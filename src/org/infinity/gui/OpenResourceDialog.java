// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.ObjectString;
import org.infinity.util.SimpleListModel;

/**
 * Provides a modal dialog for selecting a single or multiple game resources of one or more
 * given resource types.
 */
public class OpenResourceDialog extends JDialog
    implements ItemListener, ListSelectionListener, DocumentListener
{
  private final List<List<ResourceEntry>> resources = new ArrayList<>();

  private ResourceEntry[] result;
  private ObjectString[] extensions;
  private JList<ResourceEntry> list;
  private SimpleListModel<ResourceEntry> listModel;
  private JComboBox<ObjectString> cbType;
  private JTextField tfSearch;
  private PlainDocument searchDoc;
  private JButton bOpen, bCancel;
  private boolean searchLock;

  /**
   * Opens a modal dialog where the user can select one or more internal game resources.
   * @param owner The parent window of this dialog.
   * @param title The dialog title.
   * @param extensions A list of file extensions which is to limit the list of internal files.
   *                   Specify {@code null} to show all available resources.
   * @param multiSelection Specify {@code true} to allow selecting more than one resource.
   * @return An array of selected ResourceEntry objects.
   *         Returns {@code null} if the user cancelled the operation.
   */
  public static ResourceEntry[] showOpenDialog(Window owner, String title, String[] extensions,
                                               boolean multiSelection)
  {
    ResourceEntry[] retVal = null;
    if (title == null) {
      title = "Select resource";
    }
    OpenResourceDialog dlg = new OpenResourceDialog(owner, title, extensions);
    dlg.setMultiSelection(multiSelection);
    dlg.setVisible(true);
    retVal = dlg.getResult();
    return retVal;
  }


  //--------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent e)
  {
    if (e.getSource() == cbType) {
      try {
        WindowBlocker.blockWindow(this, true);
        if (cbType.getSelectedIndex() >= 0 && cbType.getSelectedIndex() < resources.size()) {
          updateList(resources.get(cbType.getSelectedIndex()));
        }
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

//--------------------- End Interface ItemListener ---------------------

//--------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    if (e.getSource() == list && !isSearchLock()) {
      try {
        setSearchLock(true);
        bOpen.setEnabled(!list.isSelectionEmpty());
        updateSearchField();
      } finally {
        setSearchLock(false);
      }
    }
  }

//--------------------- End Interface ListSelectionListener ---------------------

//--------------------- Begin Interface DocumentListener ---------------------

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    if (e.getDocument() == searchDoc && !isSearchLock()) {
      try {
        setSearchLock(true);
        updateListSelection(searchDoc.getText(0, searchDoc.getLength()));
      } catch (BadLocationException ble) {
      } finally {
        setSearchLock(false);
      }
    }
  }


  @Override
  public void removeUpdate(DocumentEvent e)
  {
    if (e.getDocument() == searchDoc && !isSearchLock()) {
      try {
        setSearchLock(true);
        updateListSelection(searchDoc.getText(0, searchDoc.getLength()));
      } catch (BadLocationException ble) {
      } finally {
        setSearchLock(false);
      }
    }
  }


  @Override
  public void changedUpdate(DocumentEvent e)
  {
    if (e.getDocument() == searchDoc && !isSearchLock()) {
      try {
        setSearchLock(true);
        updateListSelection(searchDoc.getText(0, searchDoc.getLength()));
      } catch (BadLocationException ble) {
      } finally {
        setSearchLock(false);
      }
    }
  }

//--------------------- End Interface DocumentListener ---------------------

  protected OpenResourceDialog(Window owner, String title, String[] extensions)
  {
    super(owner, title, ModalityType.APPLICATION_MODAL);
    init();
    setExtensions(extensions);
  }

  /** Specifies a list of supported resource types for this dialog. */
  protected void setExtensions(String[] extList)
  {
    if (extList != null && extList.length > 0) {
      int extra = (extList.length > 1) ? 1 : 0;
      extensions = new ObjectString[extList.length + extra];
      for (int i = 0; i < extList.length; i++) {
        final String s = extList[i].trim().toUpperCase(Locale.ENGLISH);
        extensions[i + extra] = new ObjectString(s + " resources", s, ObjectString.FMT_STRING_ONLY);
      }

      // adding an extra entry which combines all listed extensions
      if (extra > 0) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < extList.length; i++) {
          if (i > 0) {
            sb.append(';');
          }
          final String s = extList[i].trim().toUpperCase(Locale.ENGLISH);
          sb.append(s);
        }
        extensions[0] = new ObjectString("Supported resources", sb.toString(), ObjectString.FMT_STRING_ONLY);
      }
    } else {
      extensions = new ObjectString[]{new ObjectString("All resources", "", ObjectString.FMT_STRING_ONLY)};
    }

    updateResources();
  }

  /** Returns a list of resource types defined for this dialog. */
  protected String[] getExtensions()
  {
    final String str = extensions[0].getObject().toString();
    if (!str.isEmpty()) {
      return str.split(";");
    }
    return new String[]{""};
  }

  /** Returns {@code true} if multiple list items can be selected. */
  protected boolean isMultiSelection()
  {
    return (list.getSelectionMode() == ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
  }

  /** Specify whether multiple list items can be selected. */
  protected void setMultiSelection(boolean multi)
  {
    if (multi) {
      list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    } else {
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }
  }

  /**
   * Returns the result of the last dialog operation.
   * Returns a list of ResourceEntry objects if dialog operation was successful.
   * Returns {@code null} if operation has been cancelled.
   */
  protected ResourceEntry[] getResult()
  {
    return result;
  }


  private void acceptDialog()
  {
    setVisible(false);
    List<ResourceEntry> entries = list.getSelectedValuesList();
    if (entries != null) {
      result = entries.toArray(new ResourceEntry[entries.size()]);
    } else {
      result = new ResourceEntry[0];
    }
  }

  private void cancel()
  {
    setVisible(false);
    result = null;
  }

  /** Generates a list of available resources for all supported types. */
  private void updateResources()
  {
    resources.clear();
    for (final ObjectString extension : extensions) {
      final String data = extension.getObject();
      final String[] types;
      if (data.isEmpty()) {
        types = Profile.getAvailableResourceTypes();
      } else {
        types = data.split(";");
      }
      final List<ResourceEntry> list = new ArrayList<>();
      for (final String type : types) {
        list.addAll(ResourceFactory.getResources(type));
      }
      Collections.sort(list);
      resources.add(list);
    }
    updateGui();
  }

  /** Initializes type combobox. */
  private void updateGui()
  {
    DefaultComboBoxModel<ObjectString> model = (DefaultComboBoxModel<ObjectString>)cbType.getModel();
    model.removeAllElements();
    if (extensions != null) {
      for (final ObjectString os: extensions) {
        model.addElement(os);
      }
    }
    if (model.getSize() > 0) {
      cbType.setSelectedIndex(0);
    }
  }

  /** Initializes resource list. */
  private void updateList(List<ResourceEntry> entries)
  {
    listModel.clear();
    if (entries != null) {
      listModel.addAll(entries);
      if (listModel.size() > 0) {
        list.setSelectedIndex(0);
        list.ensureIndexIsVisible(0);
        list.requestFocusInWindow();
      }
    }
  }

  /** Select one or more list items based on search text. */
  private void updateListSelection(String search)
  {
    if (search == null) {
      search = "";
    } else {
      search = search.trim();
    }

    // preparing search entries
    String[] entries;
    if (search.isEmpty()) {
      entries = new String[0];
    } else {
      entries = search.split("[; ]+");
      for (int i = 0; i < entries.length; i++) {
        entries[i] = entries[i].trim();
      }
    }

    // selecting entries
    if (isMultiSelection()) {
      // multi-selection mode
      final List<Integer> indexList = new ArrayList<>();
      for (final String entry : entries) {
        final int idx = getClosestIndex(entry);
        if (idx >= 0 && !indexList.contains(idx)) {
          indexList.add(idx);
        }
      }
      int[] indices;
      if (!indexList.isEmpty()) {
        indices = new int[indexList.size()];
        for (int i = 0; i < indexList.size(); i++) {
          indices[i] = indexList.get(i);
        }
      } else {
        indices = new int[0];
      }
      list.setSelectedIndices(indices);
      if (indices.length > 0) {
        list.ensureIndexIsVisible(indices[0]);
      } else {
        list.ensureIndexIsVisible(0);
      }
    } else {
      // single selection mode
      String entry = (entries.length > 0) ? entries[0] : "";
      int idx = getClosestIndex(entry);
      list.setSelectedIndex(idx);
      if (idx >= 0) {
        list.ensureIndexIsVisible(idx);
      } else {
        list.ensureIndexIsVisible(0);
      }
    }
  }

  /** Returns index of closest list item match for given string. */
  private int getClosestIndex(String text)
  {
    text = text.toUpperCase(Locale.ENGLISH);
    final int size = listModel.getSize();
    for (int selected = 0; selected < size; selected++) {
      final String s = listModel.get(selected).toString().toUpperCase(Locale.ENGLISH);
      if (s.startsWith(text)) {
        return selected;
      }
    }
    return -1;
  }

  /** Synchronizes search field with list selections. */
  private void updateSearchField()
  {
    int[] indices = list.getSelectedIndices();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < indices.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      String s = listModel.get(indices[i]).toString();
      sb.append(s);
    }
    tfSearch.setText(sb.toString());
    tfSearch.setCaretPosition(0);
  }

  private boolean isSearchLock()
  {
    return searchLock;
  }

  private synchronized void setSearchLock(boolean set)
  {
    if (searchLock != set) {
      searchLock = set;
      if (searchLock) {
        searchDoc.removeDocumentListener(this);
        list.removeListSelectionListener(this);
      } else {
        list.addListSelectionListener(this);
        searchDoc.addDocumentListener(this);
      }
    }
  }

  /** Constructs the dialog elements. */
  private void init()
  {
    AbstractAction actOpen = new AbstractAction("Open") {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        acceptDialog();
      }
    };
    AbstractAction actCancel = new AbstractAction("Cancel") {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        cancel();
      }
    };

    bOpen = new JButton(actOpen);
    bCancel = new JButton(actCancel);
    Dimension d = new Dimension(Math.max(bOpen.getPreferredSize().width, bCancel.getPreferredSize().width),
                                Math.max(bOpen.getPreferredSize().height, bCancel.getPreferredSize().height));
    bOpen.setPreferredSize(d);
    bCancel.setPreferredSize(d);
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), bOpen);
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), bCancel);
    getRootPane().getActionMap().put(bOpen, actOpen);
    getRootPane().getActionMap().put(bCancel, actCancel);

    cbType = new JComboBox<>(new DefaultComboBoxModel<>());
    cbType.setEditable(false);
    cbType.addItemListener(this);
    JLabel lType = new JLabel("Type:");
    lType.setDisplayedMnemonic('T');
    lType.setLabelFor(cbType);

    searchDoc = new PlainDocument();
    searchDoc.addDocumentListener(this);
    tfSearch = new JTextField(searchDoc, null, 0);
    JLabel lSearch = new JLabel("Search:");
    lSearch.setDisplayedMnemonic('S');
    lSearch.setLabelFor(tfSearch);

    listModel = new SimpleListModel<>();
    list = new JList<>(listModel);
    list.setLayoutOrientation(JList.VERTICAL_WRAP);
    list.setVisibleRowCount(0);   // no limit
    list.addListSelectionListener(this);
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event)
      {
        if (event.getClickCount() == 2 && !list.isSelectionEmpty()) {
          acceptDialog();
        }
      }
    });
    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(400, 200));

    GridBagConstraints c = new GridBagConstraints();

    JPanel pType = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pType.add(lType, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 0), 0, 0);
    pType.add(cbType, c);

    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(12, 0, 0, 0), 0, 0);
    pType.add(lSearch, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(12, 8, 0, 0), 0, 0);
    pType.add(tfSearch, c);

    JPanel pList = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    pList.add(scroll, c);

    JPanel pButtons = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(bOpen, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 0, 0);
    pButtons.add(bCancel, c);

    JPanel pMain = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
    pMain.add(pType, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(8, 8, 0, 8), 0, 0);
    pMain.add(pList, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(8, 8, 8, 8), 0, 0);
    pMain.add(pButtons, c);

    setLayout(new BorderLayout());
    add(pMain, BorderLayout.CENTER);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e)
      {
        if (e.getSource() == this) {
          cancel();
        }
      }
    });

    pack();
    setResizable(true);
    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    setLocationRelativeTo(getOwner());
    list.requestFocusInWindow();
  }
}
