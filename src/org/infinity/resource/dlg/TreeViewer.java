// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.infinity.NearInfinity;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.LinkButton;
import org.infinity.gui.ScriptTextArea;
import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.StructEntry;
import org.infinity.util.Misc;
import org.infinity.util.StringTable;


/** Show dialog content as tree structure. */
final class TreeViewer extends JPanel implements ActionListener, TreeSelectionListener,
                                                 PropertyChangeListener
{
  /**
   * This array contains background colors for other dialogs to which viewed dialog
   * refers. Colors are assigned to other resources from this array on rotation basis.
   */
  private static final Color[] OTHER_DIALOG_COLORS = {
    new Color(0xd8d8ff),
    new Color(0xb8ffb8),
    new Color(0xffd0d0),
    new Color(0xffc8ff),
    new Color(0xffffa0),
    new Color(0xe0e0e0),
    new Color(0x85ffc2),
    new Color(0xffd3a6),
    new Color(0x99ccff),
    new Color(0xffa3d1),
  };
  private final JPopupMenu pmTree = new JPopupMenu();

  private final JMenuItem miExpandAll   = new JMenuItem("Expand all nodes",        Icons.getIcon(Icons.ICON_EXPAND_ALL_24));
  private final JMenuItem miExpand      = new JMenuItem("Expand selected node",    Icons.getIcon(Icons.ICON_EXPAND_16));
  private final JMenuItem miCollapseAll = new JMenuItem("Collapse all nodes",      Icons.getIcon(Icons.ICON_COLLAPSE_ALL_24));
  private final JMenuItem miCollapse    = new JMenuItem("Collapse selected nodes", Icons.getIcon(Icons.ICON_COLLAPSE_16));
  private final JMenuItem miEditEntry   = new JMenuItem("Edit selected entry",     Icons.getIcon(Icons.ICON_EDIT_16));

  /** Caches ViewFrame instances used to display external dialog entries. */
  private final HashMap<DlgResource, ViewFrame> mapViewer = new HashMap<>();

  private final DlgResource dlg;
  private final JTree dlgTree;
  private final ItemInfo dlgInfo;

  private final DlgTreeModel dlgModel;
  private JScrollPane spInfo, spTree;
  private TreeWorker worker;
  private WindowBlocker blocker;

  /** Background colors for text in dialogs to that can refer main dialog. */
  private final HashMap<DlgResource, Color> dialogColors = new HashMap<>();

  TreeViewer(DlgResource dlg)
  {
    super(new BorderLayout());
    this.dlg = dlg;
    dlgModel = new DlgTreeModel(dlg);
    dlgTree = new JTree(dlgModel);
    dlgTree.addTreeSelectionListener(this);
    dlgInfo = new ItemInfo();
    initControls();
  }

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == miEditEntry) {
      final TreePath path = dlgTree.getSelectionPath();
      if (path != null) {
        final ItemBase item = (ItemBase)path.getLastPathComponent();
        final DlgResource curDlg = item.getDialog();
        if (curDlg != dlg) {
          ViewFrame vf = mapViewer.get(curDlg);
          // reuseing external dialog window if possible
          if (vf != null && vf.isVisible()) {
            vf.toFront();
          } else {
            vf = new ViewFrame(this, curDlg);
            mapViewer.put(curDlg, vf);
          }
        }

        final StructViewer viewer = curDlg.getViewer();
        if (viewer != null) {
          // selecting table entry
          final Viewer tab = (Viewer)curDlg.getViewerTab(0);
          if (item instanceof RootItem) {
            viewer.selectEntry(0);
          } else
          if (!(item instanceof BrokenReference)) {
            final TreeItemEntry s = item.getEntry();
            viewer.selectEntry(s.getName());
            tab.select(s);
          }
          curDlg.selectEditTab();
        }
      }
    } else if (e.getSource() == miExpandAll) {
      if (worker == null) {
        worker = new TreeWorker(dlgTree, new TreePath(dlgModel.getRoot()), true);
        worker.addPropertyChangeListener(this);
        blocker = new WindowBlocker(NearInfinity.getInstance());
        blocker.setBlocked(true);
        worker.execute();
      }
    } else if (e.getSource() == miCollapseAll) {
      if (worker == null) {
        worker = new TreeWorker(dlgTree, new TreePath(dlgModel.getRoot()), false);
        worker.addPropertyChangeListener(this);
        blocker = new WindowBlocker(NearInfinity.getInstance());
        blocker.setBlocked(true);
        worker.execute();
      }
    } else if (e.getSource() == miExpand) {
      if (worker == null) {
        worker = new TreeWorker(dlgTree, dlgTree.getSelectionPath(), true);
        worker.addPropertyChangeListener(this);
        blocker = new WindowBlocker(NearInfinity.getInstance());
        blocker.setBlocked(true);
        worker.execute();
      }
    } else if (e.getSource() == miCollapse) {
      if (worker == null) {
        worker = new TreeWorker(dlgTree, dlgTree.getSelectionPath(), false);
        worker.addPropertyChangeListener(this);
        blocker = new WindowBlocker(NearInfinity.getInstance());
        blocker.setBlocked(true);
        worker.execute();
      }
    }
  }

//--------------------- End Interface ActionListener ---------------------

//--------------------- Begin Interface TreeSelectionListener ---------------------

  @Override
  public void valueChanged(TreeSelectionEvent e)
  {
    if (e.getSource() == dlgTree) {
      final Object data = dlgTree.getLastSelectedPathComponent();
      if (data instanceof StateItem) {
        // dialog state found
        updateStateInfo((StateItem)data);
      } else if (data instanceof TransitionItem) {
        // dialog response found
        updateTransitionInfo((TransitionItem)data);
      } else {
        // no valid type found
        dlgInfo.showPanel(ItemInfo.CARD_EMPTY);
      }
    }
  }

//--------------------- End Interface TreeSelectionListener ---------------------

//--------------------- Begin Interface PropertyChangeListener ---------------------

  @Override
  public void propertyChange(PropertyChangeEvent event)
  {
    if (event.getSource() == worker) {
      if ("state".equals(event.getPropertyName()) &&
          TreeWorker.StateValue.DONE == event.getNewValue()) {
        if (blocker != null) {
          blocker.setBlocked(false);
          blocker = null;
        }
        worker = null;
      }
    }
  }

//--------------------- End Interface PropertyChangeListener ---------------------

  /**
   * Selects specified dialog state or transition in the tree. If such entry not
   * exist in the dialog, returns {@code false}. Such situation say that specified
   * entry not unattainable from a dialogue root. Possibly, it is continuation
   * from other dialogue.
   * <p>
   * Always selects selects main state/transition.
   *
   * @param entry Child struct of the dialog for search
   * @return {@code true} If dialog item found in the tree, {@code false} otherwise
   */
  public boolean select(TreeItemEntry entry)
  {
    final ItemBase item = dlgModel.map(entry);
    if (item != null) {
      final TreePath path = item.getPath();
      dlgTree.addSelectionPath(path);
      dlgTree.scrollPathToVisible(path);
      return true;
    }
    return false;
  }

  private void updateStateInfo(StateItem si)
  {
    final DlgResource curDlg = si.getDialog();

    // updating info box title
    final StringBuilder sb = new StringBuilder(si.getName());
    if (curDlg != dlg) {
      sb.append(String.format(", Dialog: %s", curDlg.getResourceEntry().getResourceName()));
    }

    final State state = si.getEntry();
    if (state != null) {
      sb.append(String.format(", Responses: %d", state.getTransCount()));
      if (state.getTriggerIndex() >= 0) {
        sb.append(String.format(", Weight: %d", state.getTriggerIndex()));
      }
      dlgInfo.updateControlBorder(ItemInfo.Type.STATE, sb.toString());

      final int strRef = state.getAssociatedText().getValue();
      // updating state text
      dlgInfo.showControl(ItemInfo.Type.STATE_TEXT, true);
      dlgInfo.updateControlText(ItemInfo.Type.STATE_TEXT, StringTable.getStringRef(strRef));

      // updating state WAV Res
      final String responseText = StringTable.getSoundResource(strRef);
      if (!responseText.isEmpty()) {
        dlgInfo.showControl(ItemInfo.Type.STATE_WAV, true);
        dlgInfo.updateControlText(ItemInfo.Type.STATE_WAV, responseText + ".WAV");
      } else {
        dlgInfo.showControl(ItemInfo.Type.STATE_WAV, false);
      }
    } else {
      dlgInfo.showControl(ItemInfo.Type.STATE_TEXT, false);
      dlgInfo.showControl(ItemInfo.Type.STATE_WAV, false);
      dlgInfo.updateControlBorder(ItemInfo.Type.STATE, sb.toString());
    }

    // updating state triggers
    if (state != null && state.getTriggerIndex() >= 0) {
      dlgInfo.showControl(ItemInfo.Type.STATE_TRIGGER, true);
      final StructEntry entry = curDlg.getAttribute(StateTrigger.DLG_STATETRIGGER + " " + state.getTriggerIndex());
      final String text = entry instanceof StateTrigger ? ((StateTrigger)entry).getText() : "";
      dlgInfo.updateControlText(ItemInfo.Type.STATE_TRIGGER, text);
    } else {
      dlgInfo.showControl(ItemInfo.Type.STATE_TRIGGER, false);
    }

    dlgInfo.showPanel(ItemInfo.CARD_STATE);

    // jumping to top of scroll area
    SwingUtilities.invokeLater(() -> spInfo.getVerticalScrollBar().setValue(0));
  }

  private void updateTransitionInfo(TransitionItem ti)
  {
    final DlgResource curDlg = ti.getDialog();

    // updating info box title
    final StringBuilder sb = new StringBuilder(ti.getName());
    if (curDlg != dlg) {
      sb.append(String.format(", Dialog: %s", curDlg.getResourceEntry().getResourceName()));
    }
    dlgInfo.updateControlBorder(ItemInfo.Type.RESPONSE, sb.toString());

    final Transition trans = ti.getEntry();
    // updating flags
    dlgInfo.showControl(ItemInfo.Type.RESPONSE_FLAGS, trans != null);
    if (trans != null) {
      dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_FLAGS, trans.getFlag().toString());
    }

    // updating response text
    if (trans != null && trans.getFlag().isFlagSet(0)) {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_TEXT, true);
      dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_TEXT,
                                StringTable.getStringRef(trans.getAssociatedText().getValue()));
    } else {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_TEXT, false);
    }

    // updating journal entry
    if (trans != null && trans.getFlag().isFlagSet(4)) {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_JOURNAL, true);
      dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_JOURNAL,
                                StringTable.getStringRef(trans.getJournalEntry().getValue()));
    } else {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_JOURNAL, false);
    }

    // updating response trigger
    if (trans != null && trans.getFlag().isFlagSet(1)) {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_TRIGGER, true);
      final StructEntry entry = curDlg.getAttribute(ResponseTrigger.DLG_RESPONSETRIGGER + " " + trans.getTriggerIndex());
      final String text = entry instanceof ResponseTrigger ? ((ResponseTrigger)entry).getText() : "";
      dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_TRIGGER, text);
    } else {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_TRIGGER, false);
    }

    // updating action
    if (trans != null && trans.getFlag().isFlagSet(2)) {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_ACTION, true);
      final StructEntry entry = curDlg.getAttribute(Action.DLG_ACTION + " " + trans.getActionIndex());
      final String text = entry instanceof Action ? ((Action)entry).getText() : "";
      dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_ACTION, text);
    } else {
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_ACTION, false);
    }

    dlgInfo.showPanel(ItemInfo.CARD_RESPONSE);

    // jumping to top of scroll area
    SwingUtilities.invokeLater(() -> spInfo.getVerticalScrollBar().setValue(0));
  }

  private void initControls()
  {
    // initializing info component
    spInfo = new JScrollPane(dlgInfo, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                             JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    spInfo.getViewport().addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e)
      {
        // never scroll horizontally
        JViewport vp = (JViewport)e.getSource();
        if (vp != null) {
          Dimension d = vp.getExtentSize();
          if (d.width != vp.getView().getWidth()) {
            d.height = vp.getView().getHeight();
            vp.getView().setSize(d);
          }
        }
      }
    });
    spInfo.getVerticalScrollBar().setUnitIncrement(16);

    // initializing tree component
    JPanel pTree = new JPanel(new GridBagLayout());
    pTree.setBackground(dlgTree.getBackground());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    pTree.add(dlgTree, gbc);
    spTree = new JScrollPane(pTree);
    spTree.setBorder(BorderFactory.createEmptyBorder());
    spTree.getHorizontalScrollBar().setUnitIncrement(16);
    spTree.getVerticalScrollBar().setUnitIncrement(16);

    dlgTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    dlgTree.setRootVisible(true);
    dlgTree.setEditable(false);
    DefaultTreeCellRenderer tcr = (DefaultTreeCellRenderer)dlgTree.getCellRenderer();
    tcr.setLeafIcon(null);
    tcr.setOpenIcon(null);
    tcr.setClosedIcon(null);

    // drawing custom icons for each node type
    dlgTree.setCellRenderer(new DefaultTreeCellRenderer() {
      private Color getColor(DlgResource dialog)
      {
        if (dlg == dialog) {
          return null;
        }
        return dialogColors.computeIfAbsent(dialog,
            d -> OTHER_DIALOG_COLORS[dialogColors.size() % OTHER_DIALOG_COLORS.length]
        );
      }
      @Override
      public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                    boolean expanded, boolean leaf, int row,
                                                    boolean focused)
      {
        Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, focused);
        final ItemBase data = (ItemBase)value;

        final BrowserMenuBar options = BrowserMenuBar.getInstance();
        setIcon(options.showDlgTreeIcons() ? data.getIcon() : null);
        setBackgroundNonSelectionColor(options.colorizeOtherDialogs() ? getColor(data.getDialog()) : null);

        if (options.useDifferentColorForResponses() && value instanceof TransitionItem) {
          setForeground(Color.BLUE);
        }

        if (value instanceof BrokenReference) {
          setForeground(Color.RED);// Broken reference
        } else
        if (value instanceof StateItem) {
          final StateItem state = (StateItem)value;
          final State s = state.getEntry();
          if (s.getNumber() == 0 && s.getTriggerIndex() < 0 && options.alwaysShowState0()) {
            setForeground(Color.GRAY);
          }
        }
        if (data.getMain() != null) {
          setForeground(Color.GRAY);
        }
        return c;
      }
    });

    // preventing root node from collapsing
    dlgTree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException
      {
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException
      {
        if (event.getPath().getLastPathComponent() == dlgModel.getRoot()) {
          throw new ExpandVetoException(event);
        }
      }
    });

    // setting model AFTER customizing visual appearance of the tree control
//    dlgTree.setModel(dlgModel);

    // initializing popup menu
    miEditEntry.addActionListener(this);
    miEditEntry.setEnabled(!dlgTree.isSelectionEmpty());
    miExpand.addActionListener(this);
    miExpand.setEnabled(!dlgTree.isSelectionEmpty());
    miCollapse.addActionListener(this);
    miCollapse.setEnabled(!dlgTree.isSelectionEmpty());
    miExpandAll.addActionListener(this);
    miCollapseAll.addActionListener(this);
    pmTree.add(miEditEntry);
    pmTree.addSeparator();
    pmTree.add(miExpand);
    pmTree.add(miCollapse);
    pmTree.add(miExpandAll);
    pmTree.add(miCollapseAll);
    dlgTree.addMouseListener(new MouseAdapter()
    {
      @Override
      public void mouseClicked(MouseEvent e)
      {
        if (e.getSource() == dlgTree && e.getClickCount() == 2) {
          final TreePath path = dlgTree.getPathForLocation(e.getX(), e.getY());
          if (path == null) { return; }

          final ItemBase item = (ItemBase)path.getLastPathComponent();
          if (!item.getAllowsChildren() && item.getMain() != null) {
            final TreePath target = item.getMain().getPath();
            dlgTree.setSelectionPath(target);
            dlgTree.scrollPathToVisible(target);
          }
        }
      }

      @Override
      public void mousePressed(MouseEvent e) { maybeShowPopup(e); }

      @Override
      public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }

      private void maybeShowPopup(MouseEvent e)
      {
        if (e.getSource() == dlgTree && e.isPopupTrigger()) {
          final TreePath path = dlgTree.getClosestPathForLocation(e.getX(), e.getY());
          dlgTree.setSelectionPath(path);
          final boolean isNonRoot = path != null && path.getPathCount() > 1;

          miEditEntry.setEnabled(path != null && !(path.getLastPathComponent() instanceof BrokenReference));
          miExpand.setEnabled(isNonRoot && !isNodeExpanded(path));
          miCollapse.setEnabled(isNonRoot && !isNodeCollapsed(path));

          pmTree.show(dlgTree, e.getX(), e.getY());
        }
      }
    });

    // putting components together
    JSplitPane splitv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spTree, spInfo);
    splitv.setDividerLocation(2 * NearInfinity.getInstance().getContentPane().getHeight() / 5);
    add(splitv, BorderLayout.CENTER);
  }

  /** Returns true if the given path contains expanded nodes. */
  private boolean isNodeExpanded(TreePath path)
  {
    final ItemBase node = (ItemBase)path.getLastPathComponent();
    // Use access via model because it properly initializes items
    final TreeModel model = dlgTree.getModel();
    // Treat non-main nodes as leafs - check of their childs can lead to infinity recursion
    if (node.getMain() != null || model.isLeaf(node)) return true;
    if (!dlgTree.isExpanded(path)) return false;

    for (int i = 0, count = model.getChildCount(node); i < count; ++i) {
      final TreePath childPath = path.pathByAddingChild(model.getChild(node, i));
      if (!isNodeExpanded(childPath)) {
        return false;
      }
    }
    return true;
  }

  /** Returns true if the given path contains collapsed nodes. */
  private boolean isNodeCollapsed(TreePath path)
  {
    final ItemBase node = (ItemBase)path.getLastPathComponent();
    // Use access via model because it properly initializes items
    final TreeModel model = dlgTree.getModel();
    if (model.isLeaf(node)) return true;

    final boolean retVal = dlgTree.isCollapsed(path);
    // Do not traverse child nodes of non-main items because this can lead to infinity recursion
    if (retVal && node.getMain() == null) {
      for (int i = 0, count = model.getChildCount(node); i < count; ++i) {
        final TreePath childPath = path.pathByAddingChild(model.getChild(node, i));
        if (!isNodeCollapsed(childPath)) {
          return false;
        }
      }
    }
    return retVal;
  }

//-------------------------- INNER CLASSES --------------------------
  /** Panel for displaying information about the current dialog state or trigger. */
  private static final class ItemInfo extends JPanel
  {
    /** Identifies the respective controls for displaying information. */
    private enum Type {
      STATE, STATE_TEXT, STATE_WAV, STATE_TRIGGER,
      RESPONSE, RESPONSE_FLAGS, RESPONSE_TEXT, RESPONSE_JOURNAL, RESPONSE_TRIGGER, RESPONSE_ACTION
    }

    private static final String CARD_EMPTY    = "Empty";
    private static final String CARD_STATE    = "State";
    private static final String CARD_RESPONSE = "Response";

    private static final Color  COLOR_BACKGROUND = UIManager.getColor("Panel.background");
    private static final Font   FONT_DEFAULT = UIManager.getFont("Label.font").deriveFont(0);

    private final CardLayout cardLayout;
    private final JPanel pMainPanel, pState, pResponse, pStateText, pStateWAV, pStateTrigger,
                         pResponseFlags, pResponseText, pResponseJournal, pResponseTrigger,
                         pResponseAction;
    private final ScriptTextArea taStateTrigger, taResponseTrigger, taResponseAction;
    private final LinkButton lbStateWAV;
    private final JTextArea taStateText, taResponseText, taResponseJournal;
    private final JTextField tfResponseFlags;


    public ItemInfo()
    {
      setLayout(new GridBagLayout());

      GridBagConstraints gbc = new GridBagConstraints();

      cardLayout = new CardLayout(0, 0);
      pMainPanel = new JPanel(cardLayout);
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(8, 8, 8, 8), 0, 0);
      add(pMainPanel, gbc);

      // shown when no item has been selected
      pMainPanel.add(new JPanel(), CARD_EMPTY);

      // initializing state item info
      pState = new JPanel(new GridBagLayout());
      pState.setBorder(createTitledBorder("State", Font.BOLD, true));
      pMainPanel.add(pState, CARD_STATE);

      taStateText = createReadOnlyTextArea();
      taStateText.setMargin(new Insets(0, 4, 0, 4));
      pStateText = new JPanel(new BorderLayout());
      pStateText.setBorder(createTitledBorder("Associated text", Font.BOLD, false));
      pStateText.add(taStateText, BorderLayout.CENTER);

      lbStateWAV = new LinkButton("");
      pStateWAV = new JPanel(new GridBagLayout());
      pStateWAV.setBorder(createTitledBorder("Sound Resource", Font.BOLD, false));
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(0, 4, 0, 4), 0, 0);
      pStateWAV.add(lbStateWAV, gbc);

      taStateTrigger = createScriptTextArea(true);
      taStateTrigger.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
      taStateTrigger.setMargin(new Insets(0, 4, 0, 4));
      pStateTrigger = new JPanel(new BorderLayout());
      pStateTrigger.setBorder(createTitledBorder("State trigger", Font.BOLD, false));
      pStateTrigger.add(taStateTrigger, BorderLayout.CENTER);

      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pState.add(pStateText, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pState.add(pStateWAV, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pState.add(pStateTrigger, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 3, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
      pState.add(new JPanel(), gbc);


      // initializing response item info
      pResponse = new JPanel(new GridBagLayout());
      pResponse.setBorder(createTitledBorder("Response", Font.BOLD, true));
      pMainPanel.add(pResponse, CARD_RESPONSE);

      tfResponseFlags = createReadOnlyTextField();
      tfResponseFlags.setMargin(new Insets(0, 4, 0, 4));
      pResponseFlags = new JPanel(new BorderLayout());
      pResponseFlags.setBorder(createTitledBorder("Flags", Font.BOLD, false));
      pResponseFlags.add(tfResponseFlags, BorderLayout.CENTER);

      taResponseText = createReadOnlyTextArea();
      taResponseText.setMargin(new Insets(0, 4, 0, 4));
      pResponseText = new JPanel(new BorderLayout());
      pResponseText.setBorder(createTitledBorder("Associated text", Font.BOLD, false));
      pResponseText.add(taResponseText, BorderLayout.CENTER);

      taResponseJournal = createReadOnlyTextArea();
      taResponseJournal.setMargin(new Insets(0, 4, 0, 4));
      pResponseJournal = new JPanel(new BorderLayout());
      pResponseJournal.setBorder(createTitledBorder("Journal entry", Font.BOLD, false));
      pResponseJournal.add(taResponseJournal, BorderLayout.CENTER);

      taResponseTrigger = createScriptTextArea(true);
      taResponseTrigger.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
      taResponseTrigger.setMargin(new Insets(0, 4, 0, 4));
      pResponseTrigger = new JPanel(new BorderLayout());
      pResponseTrigger.setBorder(createTitledBorder("Response trigger", Font.BOLD, false));
      pResponseTrigger.add(taResponseTrigger, BorderLayout.CENTER);

      taResponseAction = createScriptTextArea(true);
      taResponseAction.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
      taResponseAction.setMargin(new Insets(0, 4, 0, 4));
      pResponseAction = new JPanel(new BorderLayout());
      pResponseAction.setBorder(createTitledBorder("Action", Font.BOLD, false));
      pResponseAction.add(taResponseAction, BorderLayout.CENTER);

      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseFlags, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseText, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseJournal, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 3, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseTrigger, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 4, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseAction, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 5, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
      pResponse.add(new JPanel(), gbc);

      showPanel(CARD_EMPTY);
    }

    /** Shows the panel of given name. */
    public void showPanel(String cardName)
    {
      if (cardName != null) {
        if (cardName.equals(CARD_STATE)) {
          clearCard(CARD_RESPONSE);
        } else if (cardName.equals(CARD_RESPONSE)) {
          clearCard(CARD_STATE);
        } else {
          cardName = CARD_EMPTY;
          clearCard(CARD_STATE);
          clearCard(CARD_RESPONSE);
        }
        cardLayout.show(pMainPanel, cardName);
      }
    }

    /** Enable/disable visibility of the given control. */
    public void showControl(Type type, boolean b)
    {
      getControl(type).setVisible(b);
    }

    /** Update border title of the given control. */
    public void updateControlBorder(Type type, String title)
    {
      if (title == null) title = "";
      if (getControl(type).getBorder() instanceof TitledBorder) {
        TitledBorder b = (TitledBorder)getControl(type).getBorder();
        b.setTitle(" " + title + " ");
        getControl(type).repaint();
      }
    }

    /** Update content of the given control. */
    public void updateControlText(Type type, String text)
    {
      if (text == null) text = "";
      switch (type) {
        case STATE_TEXT:        taStateText.setText(text); break;
        case STATE_WAV:         lbStateWAV.setResource(text); break;
        case STATE_TRIGGER:     taStateTrigger.setText(text); break;
        case RESPONSE_FLAGS:    tfResponseFlags.setText(text); break;
        case RESPONSE_TEXT:     taResponseText.setText(text); break;
        case RESPONSE_JOURNAL:  taResponseJournal.setText(text); break;
        case RESPONSE_TRIGGER:  taResponseTrigger.setText(text); break;
        case RESPONSE_ACTION:   taResponseAction.setText(text); break;
        default:
      }
    }


    /** Returns the given control. */
    private JPanel getControl(Type type)
    {
      switch (type) {
        case STATE:             return pState;
        case RESPONSE:          return pResponse;
        case STATE_TEXT:        return pStateText;
        case STATE_WAV:         return pStateWAV;
        case STATE_TRIGGER:     return pStateTrigger;
        case RESPONSE_FLAGS:    return pResponseFlags;
        case RESPONSE_TEXT:     return pResponseText;
        case RESPONSE_JOURNAL:  return pResponseJournal;
        case RESPONSE_TRIGGER:  return pResponseTrigger;
        case RESPONSE_ACTION:   return pResponseAction;
      }
      return new JPanel();
    }

    /** Clears and disables controls in the specified panel. */
    private void clearCard(String cardName)
    {
      if (cardName != null) {
        if (cardName.equals(CARD_STATE)) {
          updateControlText(Type.STATE_TEXT, "");
          showControl(Type.STATE_TEXT, false);
          updateControlText(Type.STATE_WAV, "");
          showControl(Type.STATE_WAV, false);
          updateControlText(Type.STATE_TRIGGER, "");
          showControl(Type.STATE_TRIGGER, false);
        } else if (cardName.equals(CARD_RESPONSE)) {
          updateControlText(Type.RESPONSE_FLAGS, "");
          showControl(Type.RESPONSE_FLAGS, false);
          updateControlText(Type.RESPONSE_TEXT, "");
          showControl(Type.RESPONSE_TEXT, false);
          updateControlText(Type.RESPONSE_JOURNAL, "");
          showControl(Type.RESPONSE_JOURNAL, false);
          updateControlText(Type.RESPONSE_TRIGGER, "");
          showControl(Type.RESPONSE_TRIGGER, false);
          updateControlText(Type.RESPONSE_ACTION, "");
          showControl(Type.RESPONSE_ACTION, false);
        }
      }
    }

    /** Helper method for creating a read-only textarea component. */
    private JTextArea createReadOnlyTextArea()
    {
      JTextArea ta = new JTextArea();
      ta.setEditable(false);
      ta.setFont(FONT_DEFAULT);
      ta.setBackground(COLOR_BACKGROUND);
      ta.setWrapStyleWord(true);
      ta.setLineWrap(true);

      return ta;
    }

    /** Helper method for creating a ScriptTextArea component. */
    private ScriptTextArea createScriptTextArea(boolean readOnly)
    {
      ScriptTextArea ta = new ScriptTextArea();
      if (readOnly) {
        ta.setBackground(COLOR_BACKGROUND);
        ta.setHighlightCurrentLine(false);
      }
      ta.setEditable(!readOnly);
      ta.setWrapStyleWord(true);
      ta.setLineWrap(true);

      return ta;
    }

    /** Helper method for creating a read-only textfield component. */
    private JTextField createReadOnlyTextField()
    {
      JTextField tf = new JTextField();
      tf.setBorder(BorderFactory.createEmptyBorder());
      tf.setEditable(false);
      tf.setFont(FONT_DEFAULT);
      tf.setBackground(COLOR_BACKGROUND);

      return tf;
    }

    /** Returns a modified TitledBorder object. */
    private TitledBorder createTitledBorder(String title, int fontStyle, boolean isTitle)
    {
      if(title == null) title = "";
      TitledBorder tb = BorderFactory.createTitledBorder(title);
      Font f = tb.getTitleFont();
      if (f == null) {
        f = FONT_DEFAULT;
      }
      if (f != null) {
        tb.setTitleFont(new Font(f.getFamily(), fontStyle, isTitle ? (f.getSize() + 1) : f.getSize()));
      }
      return tb;
    }
  }
}
