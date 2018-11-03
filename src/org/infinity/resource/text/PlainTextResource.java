// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.text;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.InfinityScrollPane;
import org.infinity.gui.InfinityTextArea;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.TextResource;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.Writeable;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.ReferenceSearcher;
import org.infinity.search.TextResourceSearcher;
import org.infinity.util.StaticSimpleXorDecryptor;
import org.infinity.util.Misc;
import org.infinity.util.io.StreamUtils;

public final class PlainTextResource implements TextResource, Writeable, ActionListener, ItemListener,
                                                DocumentListener, Closeable
{
  private final ResourceEntry entry;
  private final String text;
  private final ButtonPanel buttonPanel = new ButtonPanel();

  private JMenuItem ifindall, ifindthis;
  private JPanel panel;
  private InfinityTextArea editor;
  private boolean resourceChanged;
  private int highlightedLine;

  public PlainTextResource(ResourceEntry entry) throws Exception
  {
    this(entry, -1);
  }

  public PlainTextResource(ResourceEntry entry, int highlightedLine) throws Exception
  {
    this.entry = entry;
    ByteBuffer buffer = entry.getResourceBuffer();
    if (buffer.limit() > 1 && buffer.getShort(0) == -1) {
      buffer = StaticSimpleXorDecryptor.decrypt(buffer, 2);
    }
    Charset cs = null;
    if (BrowserMenuBar.getInstance() != null) {
      cs = Charset.forName(BrowserMenuBar.getInstance().getSelectedCharset());
    } else {
      cs = Misc.CHARSET_DEFAULT;
    }
    text = StreamUtils.readString(buffer, buffer.limit(), cs);
    this.highlightedLine = highlightedLine;
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (buttonPanel.getControlByType(ButtonPanel.Control.SAVE) == event.getSource()) {
      if (ResourceFactory.saveResource(this, panel.getTopLevelAncestor()))
        resourceChanged = false;
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES) == event.getSource()) {
      new ReferenceSearcher(entry, panel.getTopLevelAncestor());
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.EXPORT_BUTTON) == event.getSource()) {
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.TRIM_SPACES) == event.getSource()) {
      final StringBuilder newText = new StringBuilder(editor.getText().length());
      StringTokenizer st = new StringTokenizer(editor.getText(), "\n");
      while (st.hasMoreTokens()) {
        newText.append(st.nextToken().trim()).append('\n');
      }
      editor.setText(newText.toString());
      editor.setCaretPosition(0);
    }
  }

// --------------------- End Interface ActionListener ---------------------


// --------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception
  {
    if (resourceChanged) {
      ResourceFactory.closeResource(this, entry, panel);
    }
  }

// --------------------- End Interface Closeable ---------------------


// --------------------- Begin Interface DocumentListener ---------------------

  @Override
  public void insertUpdate(DocumentEvent event)
  {
    resourceChanged = true;
  }

  @Override
  public void removeUpdate(DocumentEvent event)
  {
    resourceChanged = true;
  }

  @Override
  public void changedUpdate(DocumentEvent event)
  {
    resourceChanged = true;
  }

// --------------------- End Interface DocumentListener ---------------------


// --------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent event)
  {
    ButtonPopupMenu bpmFind = (ButtonPopupMenu)buttonPanel.getControlByType(ButtonPanel.Control.FIND_MENU);
    if (event.getSource() == bpmFind) {
      if (bpmFind.getSelectedItem() == ifindall) {
        final List<ResourceEntry> files = ResourceFactory.getResources(entry.getExtension());
        new TextResourceSearcher(files, panel.getTopLevelAncestor());
      } else if (bpmFind.getSelectedItem() == ifindthis) {
        new TextResourceSearcher(Arrays.asList(entry), panel.getTopLevelAncestor());
      }
    }
  }

// --------------------- End Interface ItemListener ---------------------


// --------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry()
  {
    return entry;
  }

// --------------------- End Interface Resource ---------------------


// --------------------- Begin Interface TextResource ---------------------

  @Override
  public String getText()
  {
    return text;
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

// --------------------- End Interface TextResource ---------------------


// --------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    editor = new InfinityTextArea(text, true);
    InfinityScrollPane pane = new InfinityScrollPane(editor, true);
    setSyntaxHighlightingEnabled(editor, pane);
    editor.addCaretListener(container.getStatusBar());
    editor.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
    editor.setMargin(new Insets(3, 3, 3, 3));
    editor.setCaretPosition(0);
    editor.setLineWrap(false);
    editor.getDocument().addDocumentListener(this);

    final String ext = entry.getExtension();
    if ("BIO".equals(ext) || "RES".equals(ext)) {
      editor.setLineWrap(true);
      editor.setWrapStyleWord(true);
    }

    ifindall  = new JMenuItem("in all " + ext + " files");
    ifindthis = new JMenuItem("in this file only");
    ButtonPopupMenu bpmFind = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.FIND_MENU);
    bpmFind.setMenuItems(new JMenuItem[]{ifindall, ifindthis});
    bpmFind.addItemListener(this);
    ((JButton)buttonPanel.addControl(ButtonPanel.Control.TRIM_SPACES)).addActionListener(this);
    if ("2DA".equals(ext)) {
      ((JButton)buttonPanel.addControl(ButtonPanel.Control.FIND_REFERENCES)).addActionListener(this);
    }
    ((JButton)buttonPanel.addControl(ButtonPanel.Control.EXPORT_BUTTON)).addActionListener(this);
    ((JButton)buttonPanel.addControl(ButtonPanel.Control.SAVE)).addActionListener(this);

    panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(pane, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);

    SwingUtilities.invokeLater(() -> {
      if (highlightedLine >= 0) {
        highlightText(highlightedLine, null);
      }
    });

    return panel;
  }

// --------------------- End Interface Viewable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    if (editor == null) {
      StreamUtils.writeString(os, text, text.length());
    } else {
      editor.write(new OutputStreamWriter(os));
    }
  }

// --------------------- End Interface Writeable ---------------------

  public void setHighlightedLine(int highlightedLine)
  {
    this.highlightedLine = highlightedLine;
    if (panel != null) {
      highlightText(highlightedLine, null);
    }
  }

  public int getHighlightedLine()
  {
    return highlightedLine;
  }

  public List<String> extract2DAHeaders()
  {
    StringTokenizer st = new StringTokenizer(getText(), "\n");
    st.nextToken();
    st.nextToken();
    String header = st.nextToken();
    st = new StringTokenizer(header);
    final List<String> strings = new ArrayList<>();
    while (st.hasMoreTokens())
      strings.add(st.nextToken().toUpperCase(Locale.ENGLISH));
    return strings;
  }

  private void setSyntaxHighlightingEnabled(InfinityTextArea edit, InfinityScrollPane pane)
  {
    InfinityTextArea.Language language = InfinityTextArea.Language.NONE;
    if (entry != null) {
      if ("SQL".equalsIgnoreCase(entry.getExtension())) {
        if (BrowserMenuBar.getInstance() == null ||
            BrowserMenuBar.getInstance().getSqlSyntaxHighlightingEnabled()) {
          language = InfinityTextArea.Language.SQL;
        }
      } else if ("LUA".equalsIgnoreCase(entry.getExtension())) {
        if (BrowserMenuBar.getInstance() == null ||
            BrowserMenuBar.getInstance().getLuaSyntaxHighlightingEnabled()) {
          language = InfinityTextArea.Language.LUA;
        }
      } else if (Profile.isEnhancedEdition() && "BALDUR.INI".equalsIgnoreCase(entry.getResourceName())) {
        if (BrowserMenuBar.getInstance() == null ||
            BrowserMenuBar.getInstance().getSqlSyntaxHighlightingEnabled()) {
          language = InfinityTextArea.Language.SQL;
        }
      } else if ("GLSL".equalsIgnoreCase(entry.getExtension())) {
        if (BrowserMenuBar.getInstance() == null ||
            BrowserMenuBar.getInstance().getGlslSyntaxHighlightingEnabled()) {
          language = InfinityTextArea.Language.GLSL;
        }
      } else if ("BCS".equalsIgnoreCase(entry.getExtension()) ||
                 "BS".equalsIgnoreCase(entry.getExtension()) ||
                 "BAF".equalsIgnoreCase(entry.getExtension())) {
        if (BrowserMenuBar.getInstance() == null ||
            BrowserMenuBar.getInstance().getBcsSyntaxHighlightingEnabled()) {
          language = InfinityTextArea.Language.BCS;
        }
      } else if ("WeiDU.log".equalsIgnoreCase(entry.getResourceName()) ||
                 "WeiDU-BGEE.log".equalsIgnoreCase(entry.getResourceName())) {
        if (BrowserMenuBar.getInstance() == null ||
            BrowserMenuBar.getInstance().getWeiDUSyntaxHighlightingEnabled()) {
          language = InfinityTextArea.Language.WEIDU;
        }
      }
    }
    if (edit != null) {
      edit.applyExtendedSettings(language, null);
    }
    if (pane != null) {
      pane.applyExtendedSettings(language);
    }
  }
}
