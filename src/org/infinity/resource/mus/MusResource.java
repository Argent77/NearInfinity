// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.mus;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.InfinityScrollPane;
import org.infinity.gui.InfinityTextArea;
import org.infinity.gui.WindowBlocker;
import org.infinity.resource.Closeable;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.TextResource;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.Writeable;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.SongReferenceSearcher;
import org.infinity.search.TextResourceSearcher;
import org.infinity.util.Misc;
import org.infinity.util.Table2da;
import org.infinity.util.io.StreamUtils;

/**
 * This resource acts as a playlist for ACM files, determining loops and "interrupt state"
 * effects. An "interrupt state effect" controls the music (usually a fadeout effect)
 * to play when another ACM file is interrupted by a special condition (end of combat,
 * start of romance music, etc.).
 * <p>
 * MUS files are simple ASCII files that can be edited by any text editor. The files
 * are always located in the music folder in the main game folder and any paths inside
 * MUS files are relative. The BG2 file {@code BC1.mus} will be used to describe the
 * file format:
 * <code><pre>
 * BC1
 * 10
 * A1                 @TAG ZA
 * B1                 @TAG ZA
 * C1                 @TAG ZA
 * D1                 @TAG ZD
 * E1                 @TAG ZD
 * E2                 @TAG ZD
 * F1                 @TAG ZD
 * G1                 @TAG ZG
 * H1                 @TAG ZH
 * J1        B1       @TAG ZJ
 * # B1B is loop
 * </pre></code>
 * This file will be examined line by line below.
 *
 * <h3>Line 1</h3>
 * This line indicates the subfolder (within the music directory) that files used
 * in MUS can be found.
 *
 * <h3>Line 2</h3>
 * This line reports the amount of ACM files in the main playlist. Interrupt state
 * ACMs are not included in this count.
 *
 * <h3>Lines 3-11</h3>
 * This line is the first line of the actual playlist. It consists of the characters
 * {@code A1}, 18 spaces and a string {@code "@TAG ZA"}. The first part, {@code A1},
 * means play the file {@code BC1A1.acm} under the {@code BC1} subdirectory of the
 * music. The spaces are used as a delimiter to seperate the playlist ACM and the
 * interrupt state ACM. The amount of spaces is determined as:
 * <code><pre>
 * AmountOfSpaces = 20 - AmountOfCharactersInMainPlaylistEntry
 * </pre></code>
 * The third part ({@code @TAG ZA}) determines the interrupt state ACM. This entry is
 * composed of a {@code @TAG}, a single space, and the name of the interrupt state ACM.
 * This means "Play {@code BC1A1.acm}". If the music should be stopped while this sound
 * clip is playing, play {@code "BC1ZA.acm"} after {@code "BC1A1.acm"} has finished
 * and then stop. If the music should continue, go to the next line of the play list.
 *
 * <h3>Line 12</h3>
 * The last entry in the playlist, in addition to normal ACM entry and interrupt
 * state ACM entry, also includes an "End of File Loop" entry. If no loop is
 * specified and no interupt occurs the game automatically loops to the start of
 * the MUS file. This line consists of {@code J1}, 8 spaces, {@code B1}, 8 spaces
 * and a string {@code "@TAG ZJ"}. The first entry ({@code J1}) is the usual playlist
 * entry ({@code BC1J1.ACM}). The amount of spaces following is calculated as:
 * <code><pre>
 * AmountOfSpaces = 10 - AmountOfCharsInPlaylistEntry
 * </pre></code>
 *
 * The next part ({@code BC1}) is the "End of File Loop" entry. The loop line tells
 * the engine to switch to another playlist when the current playlist is completed.
 * In the example file, the engine will move to {@code BC1B1.acm} when {@code BC1.mus}
 * is complete. The following spaces are calculated as:
 * <code><pre>
 * AmountOfSpaces = 10 - AmountOfCharsInEndOfFileLoopEntry
 * </pre></code>
 * The last part ({@code TAG @ZJ}) is a standard interrupt state, as detailed above.
 *
 * <p>
 * Each IE game includes a silent ACM file which can be used in playlists as shown
 * in {@code Tav1.mus} from BG2:
 * <code><pre>
 * TAV1
 * 6
 * SPC1
 * A
 * SPC1
 * SPC1
 * SPC1
 * SPC1    TAV1 A
 * </pre></code>
 *
 * The playlist indicates to the engine to play 62 seconds of silence, then sound
 * "A" then 4 silence files (248 seconds), then to repeat the playlist.
 * <p>
 * The name of the silent ACM file varies between games:
 * <ul>
 * <li>PST - SPC.acm /Music/</li>
 * <li>BG2, PST - SPC1.acm /Music/</li>
 * <li>IWD2 - MX0000A.acm /Music/MX0000</li>
 * <li>IWD - MX9000A.acm /Music/MX9000A</li>
 * </ul>
 *
 * <h3>Additional remarks:</h3>
 * Songs are linked to areas and scripts via a {@link Table2da 2da file}. MUS files
 * must be added to the relevant 2da file before they can be used (by their index number):
 * <ul>
 * <li>BG1: Hard-coded</li>
 * <li>BG2: songlist.2da</li>
 * <li>PST: Unknown</li>
 * <li>IWD: music.2da</li>
 * <li>IWD2: music.2da</li>
 * </ul>
 *
 * <h3>Location</h3>
 * MUS files are normally located in the music directory in the game directory.
 * The MUS files are attached/linked to areas or romances by their index number.
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/mus.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/mus.htm</a>
 */
public final class MusResource implements Closeable, TextResource, ActionListener, Writeable, ItemListener,
                                          DocumentListener
{
  private static int lastIndex = -1;
  private final ResourceEntry entry;
  private final String text;
  private final ButtonPanel buttonPanel = new ButtonPanel();

  private JTabbedPane tabbedPane;
  private JMenuItem ifindall, ifindthis, ifindreference;
  private JPanel panel;
  private InfinityTextArea editor;
  private Viewer viewer;
  private boolean resourceChanged;

  public MusResource(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
    ByteBuffer buffer = entry.getResourceBuffer();
    text = StreamUtils.readString(buffer, buffer.limit());
    resourceChanged = false;
  }

  //<editor-fold defaultstate="collapsed" desc="ActionListener">
  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (buttonPanel.getControlByType(ButtonPanel.Control.SAVE) == event.getSource()) {
      if (ResourceFactory.saveResource(this, panel.getTopLevelAncestor())) {
        setDocumentModified(false);
      }
      viewer.loadMusResource(this);
    }
    else if (buttonPanel.getControlByType(ButtonPanel.Control.EXPORT_BUTTON) == event.getSource()) {
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Closeable">
  @Override
  public void close() throws Exception
  {
    lastIndex = tabbedPane.getSelectedIndex();
    if (resourceChanged) {
      ResourceFactory.closeResource(this, entry, panel);
    }
    if (viewer != null) {
      viewer.close();
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="DocumentListener">
  @Override
  public void insertUpdate(DocumentEvent event)
  {
    setDocumentModified(true);
  }

  @Override
  public void removeUpdate(DocumentEvent event)
  {
    setDocumentModified(true);
  }

  @Override
  public void changedUpdate(DocumentEvent event)
  {
    setDocumentModified(true);
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="ItemListener">
  @Override
  public void itemStateChanged(ItemEvent event)
  {
    if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_MENU) == event.getSource()) {
      ButtonPopupMenu bpmFind = (ButtonPopupMenu)event.getSource();
      if (bpmFind.getSelectedItem() == ifindall) {
        final List<ResourceEntry> files = ResourceFactory.getResources(entry.getExtension());
        new TextResourceSearcher(files, panel.getTopLevelAncestor());
      } else if (bpmFind.getSelectedItem() == ifindthis) {
        new TextResourceSearcher(Arrays.asList(entry), panel.getTopLevelAncestor());
      } else if (bpmFind.getSelectedItem() == ifindreference) {
        new SongReferenceSearcher(entry, panel.getTopLevelAncestor());
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Resource">
  @Override
  public ResourceEntry getResourceEntry()
  {
    return entry;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="TextResource">
  @Override
  public String getText()
  {
    if (editor == null)
      return text;
    else
      return editor.getText();
  }

  @Override
  public void highlightText(int linenr, String highlightText)
  {
    try {
      int startOfs = editor.getLineStartOffset(linenr - 1);
      int endOfs = editor.getLineEndOffset(linenr - 1);
      if (highlightText != null) {
        String text = editor.getText(startOfs, endOfs - startOfs);
        Pattern p = Pattern.compile(highlightText, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
          startOfs += m.start();
          endOfs = startOfs + m.end() + 1;
        }
      }
      highlightText(startOfs, endOfs);
    } catch (BadLocationException ble) {
    }
  }

  @Override
  public void highlightText(int startOfs, int endOfs)
  {
    try {
      editor.setCaretPosition(startOfs);
      editor.moveCaretPosition(endOfs - 1);
      editor.getCaret().setSelectionVisible(true);
    } catch (IllegalArgumentException e) {
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Viewable">
  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    panel = new JPanel(new BorderLayout());
    try {
      WindowBlocker.blockWindow(true);
      viewer = new Viewer(this);
      tabbedPane = new JTabbedPane();
      tabbedPane.addTab("View", viewer);
      tabbedPane.addTab("Edit", getEditor(container.getStatusBar()));
      panel.add(tabbedPane, BorderLayout.CENTER);
      if (lastIndex != -1) {
        tabbedPane.setSelectedIndex(lastIndex);
      } else if (BrowserMenuBar.getInstance().getDefaultStructView() == BrowserMenuBar.DEFAULT_EDIT) {
        tabbedPane.setSelectedIndex(1);
      }
      WindowBlocker.blockWindow(false);
    } catch (Exception e) {
      WindowBlocker.blockWindow(false);
    }
    return panel;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Writable">
  @Override
  public void write(OutputStream os) throws IOException
  {
    if (editor == null) {
      StreamUtils.writeString(os, text, text.length());
    } else {
      StreamUtils.writeString(os, editor.getText(), editor.getText().length());
    }
  }
  //</editor-fold>

  public Viewer getViewer()
  {
    return viewer;
  }

  private JComponent getEditor(CaretListener caretListener)
  {
    ifindall  = new JMenuItem("in all " + entry.getExtension() + " files");
    ifindthis = new JMenuItem("in this file only");
    ifindreference = new JMenuItem("references to this file");
    ButtonPopupMenu bpmFind = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.FIND_MENU);
    bpmFind.setMenuItems(new JMenuItem[]{ifindall, ifindthis, ifindreference});
    bpmFind.addItemListener(this);
    editor = new InfinityTextArea(text, true);
    editor.discardAllEdits();
    editor.addCaretListener(caretListener);
    editor.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
    editor.setMargin(new Insets(3, 3, 3, 3));
    editor.setCaretPosition(0);
    editor.setLineWrap(false);
    editor.getDocument().addDocumentListener(this);
    ((JButton)buttonPanel.addControl(ButtonPanel.Control.EXPORT_BUTTON)).addActionListener(this);
    JButton bSave = (JButton)buttonPanel.addControl(ButtonPanel.Control.SAVE);
    bSave.addActionListener(this);
    bSave.setEnabled(getDocumentModified());

    JPanel lowerpanel = new JPanel();
    lowerpanel.setLayout(new FlowLayout(FlowLayout.CENTER));
    lowerpanel.add(buttonPanel);

    JPanel panel2 = new JPanel();
    panel2.setLayout(new BorderLayout());
    panel2.add(new InfinityScrollPane(editor, true), BorderLayout.CENTER);
    panel2.add(lowerpanel, BorderLayout.SOUTH);

    return panel2;
  }

  private boolean getDocumentModified()
  {
    return resourceChanged;
  }

  private void setDocumentModified(boolean b)
  {
    if (b != resourceChanged) {
      resourceChanged = b;
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(resourceChanged);
    }
  }
}
