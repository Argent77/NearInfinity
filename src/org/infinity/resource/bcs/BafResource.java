// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.bcs;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.DataMenuItem;
import org.infinity.gui.InfinityScrollPane;
import org.infinity.gui.InfinityTextArea;
import org.infinity.gui.ScriptTextArea;
import org.infinity.gui.ViewFrame;
import org.infinity.icon.Icons;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.TextResource;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.Writeable;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.TextResourceSearcher;
import org.infinity.util.Misc;
import org.infinity.util.StaticSimpleXorDecryptor;
import org.infinity.util.io.StreamUtils;

public class BafResource implements TextResource, Writeable, Closeable, ItemListener, ActionListener,
                                    DocumentListener
{
  // for source panel
  private static final ButtonPanel.Control CtrlCompile    = ButtonPanel.Control.CUSTOM_1;
  private static final ButtonPanel.Control CtrlErrors     = ButtonPanel.Control.CUSTOM_2;
  private static final ButtonPanel.Control CtrlWarnings   = ButtonPanel.Control.CUSTOM_3;
  // for code panel
  private static final ButtonPanel.Control CtrlDecompile  = ButtonPanel.Control.CUSTOM_1;
  // for button panel
  private static final ButtonPanel.Control CtrlUses       = ButtonPanel.Control.CUSTOM_1;
  private static final ButtonPanel.Control CtrlSaveScript = ButtonPanel.Control.CUSTOM_2;


  private static JFileChooser chooser;
  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();
  private final ButtonPanel bpSource = new ButtonPanel();
  private final ButtonPanel bpCode = new ButtonPanel();

  private JTabbedPane tabbedPane;
  private JMenuItem ifindall, ifindthis;
  private JPanel panel;
  private InfinityTextArea codeText;
  private ScriptTextArea sourceText;
  private String text;
  private boolean sourceChanged = false;

  public BafResource(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
    ByteBuffer buffer = entry.getResourceBuffer();
    if (buffer.limit() > 1 && buffer.getShort(0) == -1) {
      buffer = StaticSimpleXorDecryptor.decrypt(buffer, 2);
    }
    text = StreamUtils.readString(buffer, buffer.limit(),
                                  Charset.forName(BrowserMenuBar.getInstance().getSelectedCharset()));
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (bpSource.getControlByType(CtrlCompile) == event.getSource()) {
      compile();
    } else if (bpCode.getControlByType(CtrlDecompile) == event.getSource()) {
      decompile();
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.SAVE) == event.getSource()) {
      save();
    } else if (buttonPanel.getControlByType(CtrlSaveScript) == event.getSource()) {
      saveScript();
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.EXPORT_BUTTON) == event.getSource()) {
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    }
  }

// --------------------- End Interface ActionListener ---------------------


// --------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception
  {
    if (sourceChanged) {
      ResourceFactory.closeResource(this, entry, panel);
    }
  }

// --------------------- End Interface Closeable ---------------------


// --------------------- Begin Interface DocumentListener ---------------------

  @Override
  public void insertUpdate(DocumentEvent event)
  {
    if (event.getDocument() == codeText.getDocument()) {
      bpCode.getControlByType(CtrlDecompile).setEnabled(true);
    }
    else if (event.getDocument() == sourceText.getDocument()) {
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(true);
      bpSource.getControlByType(CtrlCompile).setEnabled(true);
      sourceChanged = true;
    }
  }

  @Override
  public void removeUpdate(DocumentEvent event)
  {
    if (event.getDocument() == codeText.getDocument()) {
      bpCode.getControlByType(CtrlDecompile).setEnabled(true);
    }
    else if (event.getDocument() == sourceText.getDocument()) {
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(true);
      bpSource.getControlByType(CtrlCompile).setEnabled(true);
      sourceChanged = true;
    }
  }

  @Override
  public void changedUpdate(DocumentEvent event)
  {
    if (event.getDocument() == codeText.getDocument()) {
      bpCode.getControlByType(CtrlDecompile).setEnabled(true);
    }
    else if (event.getDocument() == sourceText.getDocument()) {
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(true);
      bpSource.getControlByType(CtrlCompile).setEnabled(true);
      sourceChanged = true;
    }
  }

// --------------------- End Interface DocumentListener ---------------------


// --------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent event)
  {
    if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_MENU) == event.getSource()) {
      ButtonPopupMenu bpmFind = (ButtonPopupMenu)event.getSource();
      if (bpmFind.getSelectedItem() == ifindall) {
        java.util.List<ResourceEntry> files = ResourceFactory.getResources("BAF");
        new TextResourceSearcher(files, panel.getTopLevelAncestor());
      } else if (bpmFind.getSelectedItem() == ifindthis) {
        java.util.List<ResourceEntry> files = new ArrayList<ResourceEntry>(1);
        files.add(entry);
        new TextResourceSearcher(files, panel.getTopLevelAncestor());
      }
    } else if (buttonPanel.getControlByType(CtrlUses) == event.getSource()) {
      ButtonPopupMenu bpmUses = (ButtonPopupMenu)event.getSource();
      JMenuItem item = bpmUses.getSelectedItem();
      String name = item.getText();
      int index = name.indexOf(" (");
      if (index != -1) {
        name = name.substring(0, index);
      }
      ResourceEntry resEntry = ResourceFactory.getResourceEntry(name);
      new ViewFrame(panel.getTopLevelAncestor(), ResourceFactory.getResource(resEntry));
    } else if (bpSource.getControlByType(CtrlErrors) == event.getSource()) {
      ButtonPopupMenu bpmErrors = (ButtonPopupMenu)event.getSource();
      String selected = bpmErrors.getSelectedItem().getText();
      int linenr = Integer.parseInt(selected.substring(0, selected.indexOf(": ")));
      highlightText(linenr, null);
    } else if (bpSource.getControlByType(CtrlWarnings) == event.getSource()) {
      ButtonPopupMenu bpmWarnings = (ButtonPopupMenu)event.getSource();
      String selected = bpmWarnings.getSelectedItem().getText();
      int linenr = Integer.parseInt(selected.substring(0, selected.indexOf(": ")));
      highlightText(linenr, null);
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
      int startOfs = sourceText.getLineStartOffset(linenr - 1);
      int endOfs = sourceText.getLineEndOffset(linenr - 1) + 1;
      if (highlightText != null) {
        String text = sourceText.getText(startOfs, endOfs - startOfs);
        Pattern p = Pattern.compile(highlightText, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
          startOfs += m.start();
          endOfs = startOfs + m.end();
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
      sourceText.setCaretPosition(startOfs);
      sourceText.moveCaretPosition(endOfs);
      sourceText.getCaret().setSelectionVisible(true);
    } catch (IllegalArgumentException e) {
    }
  }

// --------------------- End Interface TextResource ---------------------


// --------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    sourceText = new ScriptTextArea();
    sourceText.setText(text);
    sourceText.setCaretPosition(0);
    sourceText.setAutoIndentEnabled(BrowserMenuBar.getInstance().getBcsAutoIndentEnabled());
    sourceText.addCaretListener(container.getStatusBar());
    sourceText.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
    sourceText.setMargin(new Insets(3, 3, 3, 3));
    sourceText.setLineWrap(false);
    sourceText.getDocument().addDocumentListener(this);
    InfinityScrollPane scrollSource = new InfinityScrollPane(sourceText, true);
    scrollSource.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlDkShadow")));

    JButton bCompile = new JButton("Compile", Icons.getIcon(Icons.ICON_REDO_16));
    bCompile.setMnemonic('c');
    bCompile.addActionListener(this);
    ButtonPopupMenu bpmErrors = new ButtonPopupMenu("Errors (0)...", new JMenuItem[]{});
    bpmErrors.setIcon(Icons.getIcon(Icons.ICON_UP_16));
    bpmErrors.addItemListener(this);
    ButtonPopupMenu bpmWarnings = new ButtonPopupMenu("Warnings (0)...", new JMenuItem[]{});
    bpmWarnings.setIcon(Icons.getIcon(Icons.ICON_UP_16));
    bpmWarnings.addItemListener(this);
    bpSource.addControl(bCompile, CtrlCompile);
    bpSource.addControl(bpmErrors, CtrlErrors);
    bpSource.addControl(bpmWarnings, CtrlWarnings);

    JPanel sourcePanel = new JPanel(new BorderLayout());
    sourcePanel.add(scrollSource, BorderLayout.CENTER);
    sourcePanel.add(bpSource, BorderLayout.SOUTH);

    codeText = new InfinityTextArea(true);
    codeText.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
    codeText.setMargin(new Insets(3, 3, 3, 3));
    codeText.setCaretPosition(0);
    codeText.setLineWrap(false);
    codeText.getDocument().addDocumentListener(this);
    InfinityScrollPane scrollCode = new InfinityScrollPane(codeText, true);
    scrollCode.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlDkShadow")));

    JButton bDecompile = new JButton("Decompile", Icons.getIcon(Icons.ICON_UNDO_16));
    bDecompile.setMnemonic('d');
    bDecompile.addActionListener(this);
    bpCode.addControl(bDecompile, CtrlDecompile);

    JPanel codePanel = new JPanel(new BorderLayout());
    codePanel.add(scrollCode, BorderLayout.CENTER);
    codePanel.add(bpCode, BorderLayout.SOUTH);

    ifindall = new JMenuItem("in all scripts");
    ifindthis = new JMenuItem("in this script only");
    ButtonPopupMenu bpmFind = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.FIND_MENU);
    bpmFind.setMenuItems(new JMenuItem[]{ifindall, ifindthis});
    bpmFind.addItemListener(this);
    ButtonPopupMenu bpmUses = new ButtonPopupMenu("Uses...", new JMenuItem[]{});
    bpmUses.setIcon(Icons.getIcon(Icons.ICON_FIND_16));
    bpmUses.addItemListener(this);
    buttonPanel.addControl(bpmUses, CtrlUses);
    ((JButton)buttonPanel.addControl(ButtonPanel.Control.EXPORT_BUTTON)).addActionListener(this);
    JButton bSave = (JButton)buttonPanel.addControl(ButtonPanel.Control.SAVE);
    bSave.addActionListener(this);
    JButton bSaveScript = new JButton("Save code", Icons.getIcon(Icons.ICON_SAVE_16));
    bSaveScript.addActionListener(this);
    buttonPanel.addControl(bSaveScript, CtrlSaveScript);

    tabbedPane = new JTabbedPane();
    tabbedPane.addTab("Script source", sourcePanel);
    tabbedPane.addTab("Script code (compiled)", codePanel);

    panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(tabbedPane, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);

    bCompile.setEnabled(true);
    bpmErrors.setEnabled(false);
    bpmWarnings.setEnabled(false);
    bDecompile.setEnabled(false);
    bSave.setEnabled(false);
    bpmUses.setEnabled(false);
    bSaveScript.setEnabled(false);

    return panel;
  }

// --------------------- End Interface Viewable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    if (sourceText == null) {
      StreamUtils.writeString(os, text, text.length());
    } else {
      sourceText.write(new OutputStreamWriter(os));
    }
  }

// --------------------- End Interface Writeable ---------------------

  private void compile()
  {
    JButton bCompile = (JButton)bpSource.getControlByType(CtrlCompile);
    JButton bDecompile = (JButton)bpCode.getControlByType(CtrlDecompile);
    JButton bSaveScript = (JButton)buttonPanel.getControlByType(CtrlSaveScript);
    ButtonPopupMenu bpmErrors = (ButtonPopupMenu)bpSource.getControlByType(CtrlErrors);
    ButtonPopupMenu bpmWarnings = (ButtonPopupMenu)bpSource.getControlByType(CtrlWarnings);
    ButtonPopupMenu bpmUses = (ButtonPopupMenu)buttonPanel.getControlByType(CtrlUses);
    Compiler compiler2 = new Compiler(sourceText.getText());
    codeText.setText(compiler2.getCode());
    codeText.setCaretPosition(0);
    bCompile.setEnabled(false);
    bDecompile.setEnabled(false);
    bSaveScript.setEnabled(compiler2.getErrors().isEmpty());
    SortedSet<ScriptMessage> errorMap = compiler2.getErrors();
    SortedSet<ScriptMessage> warningMap = compiler2.getWarnings();
    sourceText.clearGutterIcons();
    bpmErrors.setText("Errors (" + errorMap.size() + ")...");
    bpmWarnings.setText("Warnings (" + warningMap.size() + ")...");
    if (errorMap.size() == 0) {
      bpmErrors.setEnabled(false);
    } else {
      JMenuItem errorItems[] = new JMenuItem[errorMap.size()];
      int counter = 0;
      for (final ScriptMessage sm: errorMap) {
        sourceText.setLineError(sm.getLine(), sm.getMessage(), false);
        errorItems[counter++] = new DataMenuItem(sm.getLine() + ": " + sm.getMessage(), null, sm);
      }
      bpmErrors.setMenuItems(errorItems, false);
      bpmErrors.setEnabled(true);
    }
    if (warningMap.isEmpty()) {
      bpmWarnings.setEnabled(false);
    } else {
      JMenuItem warningItems[] = new JMenuItem[warningMap.size()];
      int counter = 0;
      for (final ScriptMessage sm: warningMap) {
        sourceText.setLineWarning(sm.getLine(), sm.getMessage(), false);
        warningItems[counter++] = new DataMenuItem(sm.getLine() + ": " + sm.getMessage(), null, sm);
      }
      bpmWarnings.setMenuItems(warningItems, false);
      bpmWarnings.setEnabled(true);
    }
    Decompiler decompiler = new Decompiler(codeText.getText(), true);
    decompiler.setGenerateComments(BrowserMenuBar.getInstance().autogenBCSComments());
    try {
      decompiler.decompile();
      Set<ResourceEntry> uses = decompiler.getResourcesUsed();
      JMenuItem usesItems[] = new JMenuItem[uses.size()];
      int usesIndex = 0;
      for (final ResourceEntry usesEntry : uses) {
        if (usesEntry.getSearchString() != null) {
          usesItems[usesIndex++] =
          new JMenuItem(usesEntry.getResourceName() + " (" + usesEntry.getSearchString() + ')');
        } else {
          usesItems[usesIndex++] = new JMenuItem(usesEntry.toString());
        }
      }
      bpmUses.setMenuItems(usesItems);
      bpmUses.setEnabled(usesItems.length > 0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void decompile()
  {
    JButton bDecompile = (JButton)bpCode.getControlByType(CtrlDecompile);
    JButton bCompile = (JButton)bpSource.getControlByType(CtrlCompile);
    ButtonPopupMenu bpmUses = (ButtonPopupMenu)buttonPanel.getControlByType(CtrlUses);
    Decompiler decompiler = new Decompiler(codeText.getText(), true);
    decompiler.setGenerateComments(BrowserMenuBar.getInstance().autogenBCSComments());
    try {
      sourceText.setText(decompiler.getSource());
    } catch (Exception e) {
      e.printStackTrace();
    }
    sourceText.setCaretPosition(0);
    Set<ResourceEntry> uses = decompiler.getResourcesUsed();
    JMenuItem usesItems[] = new JMenuItem[uses.size()];
    int usesIndex = 0;
    for (final ResourceEntry usesEntry : uses) {
      if (usesEntry.getSearchString() != null)
        usesItems[usesIndex++] =
        new JMenuItem(usesEntry.getResourceName() + " (" + usesEntry.getSearchString() + ')');
      else
        usesItems[usesIndex++] = new JMenuItem(usesEntry.toString());
    }
    bpmUses.setMenuItems(usesItems);
    bpmUses.setEnabled(usesItems.length > 0);
    bCompile.setEnabled(false);
    bDecompile.setEnabled(false);
    tabbedPane.setSelectedIndex(0);
  }

  private void save()
  {
    JButton bSave = (JButton)buttonPanel.getControlByType(ButtonPanel.Control.SAVE);
    ButtonPopupMenu bpmErrors = (ButtonPopupMenu)bpSource.getControlByType(CtrlErrors);
    if (bpmErrors.isEnabled()) {
      String options[] = {"Save", "Cancel"};
      int result = JOptionPane.showOptionDialog(panel, "Script contains errors. Save anyway?", "Errors found",
                                                JOptionPane.YES_NO_OPTION,
                                                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
      if (result != 0)
        return;
    }
    if (ResourceFactory.saveResource(this, panel.getTopLevelAncestor())) {
      bSave.setEnabled(false);
      sourceChanged = false;
    }
  }

  private void saveScript()
  {
    if (chooser == null) {
      chooser = new JFileChooser(Profile.getGameRoot().toFile());
      chooser.setDialogTitle("Save source code");
      chooser.setFileFilter(new javax.swing.filechooser.FileFilter()
      {
        @Override
        public boolean accept(File pathname)
        {
          return pathname.isDirectory() || pathname.getName().toLowerCase(Locale.ENGLISH).endsWith(".bcs");
        }

        @Override
        public String getDescription()
        {
          return "Infinity script (.BCS)";
        }
      });
    }
    chooser.setSelectedFile(new File(StreamUtils.replaceFileExtension(entry.getResourceName(), "BCS")));
    int returnval = chooser.showSaveDialog(panel.getTopLevelAncestor());
    if (returnval == JFileChooser.APPROVE_OPTION) {
      try (BufferedWriter bw =
          Files.newBufferedWriter(chooser.getSelectedFile().toPath(),
                                  Charset.forName(BrowserMenuBar.getInstance().getSelectedCharset()))) {
        bw.write(codeText.getText());
        JOptionPane.showMessageDialog(panel, "File saved to \"" + chooser.getSelectedFile().toString() +
                                             '\"', "Save completed", JOptionPane.INFORMATION_MESSAGE);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(panel, "Error saving " + chooser.getSelectedFile().toString(),
                                      "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
      }
    }
  }
}
