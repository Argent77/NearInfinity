// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;

public class ResourceChooser extends JComponent implements ActionListener
{
  /** Return value if cancel is chosen. */
  public static int CANCEL_OPTION = 0;
  /** Return value if approve (ok, yes) is chosen. */
  public static int APPROVE_OPTION = 1;
  /** Return value if an error occured. */
  public static int ERROR_OPTION = -1;

  // default component dimension
  private static Dimension DEFAULT_DIMENSION = new Dimension(200, 250);

  private final EventListenerList listeners = new EventListenerList();

  private JComboBox<String> cbType;
  private TextListPanel<ResourceEntry> lpResources;
  private int dialogResult;

  /**
   * Constructs a new Resource Chooser component.
   * The first available resource type will be preselected.
   */
  public ResourceChooser()
  {
    this(null);
  }

  /**
   * Constructs a new Resource Chooser component with a preselected resource type.
   * @param initialExtension The format extension to preselect.
   */
  public ResourceChooser(String initialExtension)
  {
    init(initialExtension);
  }

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == cbType) {
      Object o = cbType.getSelectedItem();
      resetResourceList((o != null) ? o.toString() : null);
    }
  }

//--------------------- End Interface ActionListener ---------------------

  public void addActionListener(ActionListener l)
  {
    if (l != null) {
      listeners.add(ActionListener.class, l);
    }
  }

  public ActionListener[] getActionListeners()
  {
    return listeners.getListeners(ActionListener.class);
  }

  public void removeActionListener(ActionListener l)
  {
    if (l != null) {
      listeners.remove(ActionListener.class, l);
    }
  }

  public void addListSelectionListener(ListSelectionListener l)
  {
    lpResources.addListSelectionListener(l);
  }

  public void removeListSelectionListener(ListSelectionListener l)
  {
    lpResources.removeListSelectionListener(l);
  }

  /**
   * Returns the format extension of the selected resource type.
   * @return Resource type as file extension string or {@code null} if no type is selected.
   */
  public String getSelectedType()
  {
    if (cbType.getSelectedItem() != null) {
      return cbType.getSelectedItem().toString();
    } else {
      return null;
    }
  }

  /**
   * Returns the filename of the selected resource from the list. Returns {@code null} if
   * no item has been selected.
   * @return The resource filename or {@code null} if no selection has been made.
   */
  public String getSelectedItem()
  {
    if (lpResources != null) {
      final ResourceEntry entry = lpResources.getSelectedValue();
      if (entry != null) {
        return entry.getResourceName();
      }
    }
    return null;
  }

  /**
   * Pops up a resource chooser dialog.
   * @param parent The parent component of the dialog. Can be {@code null}.
   * @return The return state of the dialog on pop down. Can be either
   */
  public int showDialog(Component parent)
  {
    dialogResult = ERROR_OPTION;
    ResourceDialog dialog = new ResourceDialog(parent);
    try {
      dialog.setVisible(true);
    } finally {
      dialog.dispose();
    }
    return dialogResult;
  }

  protected void fireActionPerformed(ActionEvent event)
  {
    ActionListener[] list = listeners.getListeners(ActionListener.class);
    for (final ActionListener l: list) {
      if (event == null) {
        event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      }
      l.actionPerformed(event);
    }
  }

  private void init(String initialExtension)
  {
    JLabel lType = new JLabel("Resource type:");
    cbType = new JComboBox<>(Profile.getAvailableResourceTypes());
    if (cbType.getModel().getSize() > 0) {
      if (initialExtension != null) {
        cbType.setSelectedItem(initialExtension);
      }
      if (cbType.getSelectedIndex() < 0) {
        cbType.setSelectedIndex(0);
      }
      cbType.addActionListener(this);
      resetResourceList(cbType.getSelectedItem().toString());
    } else {
      resetResourceList(null);
    }

    JPanel pMain = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pMain.add(lType, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 0), 0, 0);
    pMain.add(cbType, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 2, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.BOTH, new Insets(8, 0, 0, 0), 0, 0);
    pMain.add(lpResources, gbc);

    setLayout(new BorderLayout(8, 8));
    add(pMain, BorderLayout.CENTER);
    setPreferredSize(DEFAULT_DIMENSION);
  }

  private void resetResourceList(String ext)
  {
    final List<ResourceEntry> resources = (ext != null) ? ResourceFactory.getResources(ext)
                                                        : new ArrayList<ResourceEntry>();
    if (lpResources != null) {
      // switching type in existing list panel
      RootPaneContainer rpc = (RootPaneContainer)SwingUtilities.getAncestorOfClass(RootPaneContainer.class, this);
      final WindowBlocker block = (rpc != null) ? new WindowBlocker(rpc) : null;
      if (block != null) { block.setBlocked(true); }
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run()
        {
          lpResources.setValues(resources);
          if (lpResources.getModel().getSize() > 0) {
            lpResources.setSelectedIndex(0);
            lpResources.ensureIndexIsVisible(0);
          }
          if (block != null) { block.setBlocked(false); }
        }
      });
    } else {
      // initializing new list panel (no need to block controls)
      lpResources = new TextListPanel<>(resources, true);
      lpResources.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event)
        {
          if (event.getClickCount() == 2) {
            dialogResult = (lpResources.getSelectedIndex() >= 0) ? APPROVE_OPTION : CANCEL_OPTION;
            fireActionPerformed(new ActionEvent(ResourceChooser.this, ActionEvent.ACTION_PERFORMED, null));
          }
        }
      });

      if (lpResources.getModel().getSize() > 0) {
        lpResources.setSelectedIndex(0);
        lpResources.ensureIndexIsVisible(0);
      }
    }
  }


//-------------------------- INNER CLASSES --------------------------

  private class ResourceDialog extends JDialog implements ActionListener
  {
    private Action acceptAction, cancelAction;
    private JButton bAccept, bCancel;

    public ResourceDialog(Component parent)
    {
      super((parent instanceof Frame) ? (Frame)parent : (Frame)SwingUtilities.getAncestorOfClass(Frame.class, parent),
            "Choose resource", true);
      init();
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
      if (e.getSource() == ResourceChooser.this) {
        dialogResult = APPROVE_OPTION;
        setVisible(false);
      }
    }

    @Override
    public void dispose()
    {
      ResourceChooser.this.removeActionListener(this);
      super.dispose();
    }

    private void init()
    {
      acceptAction = new DialogOkAction(this);
      addListSelectionListener((DialogOkAction)acceptAction);
      cancelAction = new DialogCancelAction(this);

      bAccept = new JButton(acceptAction);
      bCancel = new JButton(cancelAction);
      Dimension d = new Dimension(Math.max(bAccept.getPreferredSize().width, bCancel.getPreferredSize().width),
                                  Math.max(bAccept.getPreferredSize().height, bCancel.getPreferredSize().height));
      bAccept.setPreferredSize(d);
      bCancel.setPreferredSize(d);


      JPanel panelButtons = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      panelButtons.add(new JPanel(), gbc);
      gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.NONE, new Insets(8, 4, 8, 0), 0, 0);
      panelButtons.add(bAccept, gbc);
      gbc = ViewerUtil.setGBC(gbc, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.NONE, new Insets(8, 8, 8, 4), 0, 0);
      panelButtons.add(bCancel, gbc);
      gbc = ViewerUtil.setGBC(gbc, 3, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      panelButtons.add(new JPanel(), gbc);

      JPanel panelMain = new JPanel(new GridBagLayout());
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.BOTH, new Insets(8, 8, 8, 8), 0, 0);
      panelMain.add(ResourceChooser.this, gbc);

      ActionMap actionMap = panelButtons.getActionMap();
      actionMap.put(cancelAction.getValue(Action.DEFAULT), cancelAction);
      actionMap.put(acceptAction.getValue(Action.DEFAULT), acceptAction);
      InputMap inputMap = panelButtons.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelAction.getValue(Action.DEFAULT));
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), acceptAction.getValue(Action.DEFAULT));

      getContentPane().add(panelMain, BorderLayout.CENTER);
      getContentPane().add(panelButtons, BorderLayout.SOUTH);
      pack();
      setLocationRelativeTo(getParent());

      ResourceChooser.this.addActionListener(this);
      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e)
        {
          ResourceChooser.this.dialogResult = CANCEL_OPTION;
        }
      });
    }
  }


  private class DialogOkAction extends AbstractAction implements ListSelectionListener
  {
    public static final String ACTION_NAME = "OK";

    private JDialog dialog;

    public DialogOkAction(JDialog dialog)
    {
      this.dialog = dialog;
      putValue(Action.DEFAULT, ACTION_NAME);
      putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
      putValue(Action.NAME, ACTION_NAME);
      setEnabled(getSelectedItem() != null);
    }

    @Override
    public void actionPerformed(ActionEvent event)
    {
      dialogResult = APPROVE_OPTION;
      dialog.setVisible(false);
      fireActionPerformed(event);
    }

    @Override
    public void valueChanged(ListSelectionEvent e)
    {
      setEnabled(getSelectedItem() != null);
    }
  }


  private class DialogCancelAction extends AbstractAction
  {
    public static final String ACTION_NAME = "Cancel";

    private JDialog dialog;

    public DialogCancelAction(JDialog dialog)
    {
      this.dialog = dialog;
      putValue(Action.DEFAULT, ACTION_NAME);
      putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
      putValue(Action.NAME, ACTION_NAME);
    }

    @Override
    public void actionPerformed(ActionEvent event)
    {
      dialogResult = CANCEL_OPTION;
      dialog.setVisible(false);
      fireActionPerformed(event);
    }
  }
}
