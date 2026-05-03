// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.search;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.NearInfinity;
import org.infinity.gui.Center;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.ViewFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.Viewable;
import org.infinity.resource.bcs.BcsResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;

public final class SearchFrame extends ChildFrame
    implements ActionListener, ItemListener, ListSelectionListener, Runnable {
//  private static final SearchFrame SEARCH_FRAME = null;

  private final CardLayout cards = new CardLayout();
  private final JButton bopen = new JButton("Open", Icons.ICON_OPEN_16.getIcon());
  private final JButton bopennew = new JButton("Open in new window", Icons.ICON_OPEN_16.getIcon());
  private final JButton binsert = new JButton("Insert reference", Icons.ICON_PASTE_16.getIcon());
  private final JButton bsearch = new JButton("Search", Icons.ICON_FIND_16.getIcon());
  private final JList<ResourceWrapper> list = new JList<>();
  private final JPanel bpanel;
  private final JProgressBar progress = new JProgressBar();
  private final JRadioButton rbcre = new JRadioButton("Creatures");
  private final JRadioButton rbitm = new JRadioButton("Items");
  private final JRadioButton rbspl = new JRadioButton("Spells");
  private final JRadioButton rbsto = new JRadioButton("Stores");
  private final JRadioButton rbare = new JRadioButton("Areas");
  private final JTextField tfield = new JTextField(10);
  private final JCheckBox cbCaseSensitive = new JCheckBox("Match case");
  private final JCheckBox cbRegex = new JCheckBox("Use regular expressions");
  private final JLabel lRegexHelp = ViewerUtil.createRegexpHelpLabel();
  private final JCheckBox cbInvert = new JCheckBox("Invert match");
  private final JCheckBox cbIncludeSymbols = new JCheckBox("Include symbols", true);

  public SearchFrame() {
    super("Find");
    setIconImage(Icons.ICON_FIND_16.getIcon().getImage());
    getRootPane().setDefaultButton(bsearch);
    bopen.setMnemonic('o');
    bopennew.setMnemonic('w');
    binsert.setMnemonic('r');
    rbcre.setMnemonic('c');
    rbitm.setMnemonic('i');
    rbspl.setMnemonic('s');
    rbspl.addItemListener(this);
    rbsto.setMnemonic('t');
    rbare.setMnemonic('a');
    rbare.setToolTipText("Only areas with descriptive names are considered by the search.");
    ButtonGroup bg = new ButtonGroup();
    bg.add(rbcre);
    bg.add(rbitm);
    bg.add(rbspl);
    bg.add(rbsto);
    bg.add(rbare);
    rbcre.setSelected(true);

    list.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    JScrollPane scroll = new JScrollPane(list);
    list.setEnabled(false);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.addListSelectionListener(this);
    bopen.setEnabled(false);
    bopennew.setEnabled(false);
    binsert.setEnabled(false);
    binsert.setToolTipText("Inserts reference to selected item into script displayed in main window");
    JLabel label = new JLabel("Name:");
    label.setLabelFor(tfield);
    label.setDisplayedMnemonic('n');
    JLabel resultLabel = new JLabel("Result:");
    bsearch.addActionListener(this);
    bopen.addActionListener(this);
    bopennew.addActionListener(this);
    binsert.addActionListener(this);
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
          ResourceEntry entry = list.getSelectedValue().getResourceEntry();
          NearInfinity.getInstance().showResourceEntry(entry);
        }
      }
    });
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent event) {
        tfield.requestFocus();
      }
    });

    JPanel lpanel2 = new JPanel();
    lpanel2.setLayout(new FlowLayout());
    lpanel2.add(binsert);
    lpanel2.add(bopen);
    lpanel2.add(bopennew);

    bpanel = new JPanel();
    bpanel.setLayout(cards);
    bpanel.add(lpanel2, "Button");
    bpanel.add(progress, "Progress");
    cards.show(bpanel, "Button");

    JPanel rbpanel = new JPanel();
    rbpanel.setLayout(new GridLayout(1, 4, 3, 3));
    rbpanel.add(rbcre);
    rbpanel.add(rbitm);
    rbpanel.add(rbspl);
    rbpanel.add(rbsto);
    rbpanel.add(rbare);
    rbpanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Find:"),
        BorderFactory.createEmptyBorder(3, 6, 3, 3)));

    cbCaseSensitive.setMnemonic('m');
    cbCaseSensitive.setToolTipText("Search text is matched case-sensitive. Can be used in combination with regular expressions.");
    cbRegex.setMnemonic('r');
    cbRegex.setToolTipText("Search text is treated as a regular expression. Use backslash (\\) to escape special characters.");
    cbInvert.setMnemonic('v');
    cbInvert.setToolTipText("Add to results list on mismatch.");
    cbIncludeSymbols.setMnemonic('y');
    cbIncludeSymbols.setToolTipText("Include symbolic names of SPL resources in the search.");
    cbIncludeSymbols.setEnabled(false);

    JPanel regexpPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    regexpPanel.add(cbRegex);
    regexpPanel.add(lRegexHelp);

    JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
    optionsPanel.setBorder(BorderFactory.createTitledBorder("Options:"));
    optionsPanel.add(cbCaseSensitive);
    optionsPanel.add(regexpPanel);
    optionsPanel.add(cbInvert);
    optionsPanel.add(cbIncludeSymbols);

    JPanel pane = (JPanel) getContentPane();
    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    pane.setLayout(gbl);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(6, 4, 3, 5);
    gbl.setConstraints(rbpanel, gbc);
    pane.add(rbpanel);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(6, 4, 3, 5);
    gbl.setConstraints(optionsPanel, gbc);
    pane.add(optionsPanel);

    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(3, 6, 3, 3);
    gbl.setConstraints(label, gbc);
    pane.add(label);

    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.SOUTH;
    gbc.insets.left = 3;
    tfield.setPreferredSize(
        new Dimension((int) tfield.getPreferredSize().getWidth(), (int) bsearch.getPreferredSize().getHeight()));
    gbl.setConstraints(tfield, gbc);
    pane.add(tfield);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 0.0;
    gbc.insets = new Insets(6, 3, 3, 7);
    gbl.setConstraints(bsearch, gbc);
    pane.add(bsearch);

    gbc.anchor = GridBagConstraints.CENTER;
    gbc.insets.left = 6;
    gbc.insets.bottom = 0;
    gbl.setConstraints(resultLabel, gbc);
    pane.add(resultLabel);

    gbc.insets = new Insets(3, 4, 3, 5);
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbl.setConstraints(scroll, gbc);
    pane.add(scroll);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(3, 6, 6, 7);
    gbl.setConstraints(bpanel, gbc);
    pane.add(bpanel);

    pack();
    setSize(getSize().width, getSize().height * 3 / 2);
    Center.center(this, NearInfinity.getInstance().getBounds());
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == tfield || event.getSource() == bsearch) {
      if (tfield.getText() == null || tfield.getText().isEmpty()) {
        return;
      }
      new Thread(this).start();
    } else if (event.getSource() == bopen) {
      ResourceEntry entry = list.getSelectedValue().getResourceEntry();
      NearInfinity.getInstance().showResourceEntry(entry);
    } else if (event.getSource() == bopennew) {
      ResourceEntry entry = list.getSelectedValue().getResourceEntry();
      new ViewFrame(this, ResourceFactory.getResource(entry));
    } else if (event.getSource() == binsert) {
      Viewable viewable = NearInfinity.getInstance().getViewable();
      if (!(viewable instanceof BcsResource)) {
        JOptionPane.showMessageDialog(this, "No script displayed in the main window", "Error",
            JOptionPane.ERROR_MESSAGE);
        return;
      }
      String resName = list.getSelectedValue().getResourceEntry().getResourceRef();
      ((BcsResource) viewable).insertString('\"' + resName + '\"');
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent e) {
    if (e.getSource() == rbspl) {
      cbIncludeSymbols.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
    }
  }

  // --------------------- End Interface ItemListener ---------------------

  // --------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent event) {
    bopen.setEnabled(true);
    bopennew.setEnabled(true);
    binsert.setEnabled(true);
  }

  // --------------------- End Interface ListSelectionListener ---------------------

  // --------------------- Begin Interface Runnable ---------------------

  @Override
  public void run() {
    final WindowBlocker blocker = new WindowBlocker(this);
    SwingUtilities.invokeLater(() -> {
      blocker.setBlocked(true);
      list.clearSelection();
      list.setListData(new ResourceWrapper[0]);
      bsearch.setEnabled(false);
    });

    try {
      if (tfield.getText().isEmpty()) {
        return;
      }

      String selectedtype = "";
      if (rbcre.isSelected()) {
        selectedtype = "CRE";
      } else if (rbitm.isSelected()) {
        selectedtype = "ITM";
      } else if (rbspl.isSelected()) {
        selectedtype = "SPL";
      } else if (rbsto.isSelected()) {
        selectedtype = "STO";
      } else if (rbare.isSelected()) {
        selectedtype = "ARE";
      }

      final boolean isCase = cbCaseSensitive.isSelected();
      final boolean isInverted = cbInvert.isSelected();
      final boolean includeSymbols = rbspl.isSelected() && cbIncludeSymbols.isSelected();
      Pattern regex = null;
      if (cbRegex.isSelected()) {
        try {
          String text = tfield.getText();
          regex = Pattern.compile(text, isCase ? 0 : Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
          Logger.error(e);
          JOptionPane.showMessageDialog(this, "Regular expression error:\n" + e.getMessage(), "Error",
              JOptionPane.ERROR_MESSAGE);
          return;
        }
      }

      String expr = tfield.getText();
      if (!isCase) {
        expr = expr.toLowerCase(Locale.ROOT);
      }

      List<ResourceEntry> resources = ResourceFactory.getResources(selectedtype);
      List<ResourceWrapper> found = new ArrayList<>();
      SwingUtilities.invokeLater(() -> {
        cards.show(bpanel, "Progress");
        progress.setMaximum(resources.size());
      });
      for (int i = 0, size = resources.size(); i < size; i++) {
        final ResourceEntry entry = resources.get(i);
        final String string = entry.getSearchString();
        boolean isMatch = false;
        if (string != null) {
          if (regex != null) {
            isMatch = regex.matcher(string).find();
          } else {
            String text = isCase ? string : string.toLowerCase(Locale.ROOT);
            isMatch = text.contains(expr);
          }
        }

        // check symbolic names of spell resources
        if (!isMatch && includeSymbols) {
          final String symbol = org.infinity.resource.spl.Viewer.getSymbolicName(entry, false);
          if (symbol != null) {
            if (regex != null) {
              isMatch = regex.matcher(symbol).find();
            } else {
              String text = isCase ? symbol : symbol.toLowerCase(Locale.ROOT);
              isMatch = text.contains(expr);
            }
          }
        }

        if (isInverted) {
          isMatch = !isMatch;
        }

        if (isMatch) {
          found.add(new ResourceWrapper(entry));
        }

        final int index = i + 1;
        SwingUtilities.invokeLater(() -> progress.setValue(index));
      }
      SwingUtilities.invokeLater(() -> {
        cards.show(bpanel, "Button");
        progress.setValue(0);

        list.ensureIndexIsVisible(0);
        if (!found.isEmpty()) {
          Collections.sort(found);
          list.setListData(found.toArray(new ResourceWrapper[0]));
          list.setEnabled(true);
        }
      });
    } finally {
      SwingUtilities.invokeLater(() -> {
        bsearch.setEnabled(true);
        blocker.setBlocked(false);
      });
    }
  }

  // --------------------- End Interface Runnable ---------------------

  // -------------------------- INNER CLASSES --------------------------

  private static class ResourceWrapper implements Comparable<ResourceWrapper> {
    private final ResourceEntry entry;

    public ResourceWrapper(ResourceEntry entry) {
      this.entry = entry;
    }

    public ResourceEntry getResourceEntry() {
      return entry;
    }

    // --------------------- Begin Interface Comparable ---------------------

    @Override
    public int compareTo(ResourceWrapper wrapper) {
      if (wrapper.entry == this.entry) {
        return 0;
      } else if (this.entry == null) {
        return -1;
      } else if (wrapper.entry == null) {
        return 1;
      } else {
        return this.entry.getResourceName().compareToIgnoreCase(wrapper.entry.getResourceName());
      }
    }

    // --------------------- End Interface Comparable ---------------------

    @Override
    public String toString() {
      if (entry == null) {
        return "(null)";
      }

      String resName = entry.getResourceName();
      String descName = entry.getSearchString();
      String symbolName = (entry.getExtension().equalsIgnoreCase("SPL"))
          ? org.infinity.resource.spl.Viewer.getSymbolicName(entry, false)
          : null;
      String text = resName;
      if (descName != null && !descName.isEmpty()) {
        text += " - " + descName;
      }
      if (symbolName != null) {
        text += " [" + symbolName + "]";
      }
      return text;
    }
  }
}
