// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.infinity.NearInfinity;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.AbstractBIFFReader;
import org.infinity.resource.key.BIFFEntry;

final class ChooseBIFFrame extends ChildFrame implements ActionListener
{
  private final BIFFEditor editor;
  private final JButton bok = new JButton("OK");
  private final JButton bcancel = new JButton("Cancel");
  private final JComboBox<BIFFEntry> cbbifname;
  private final JRadioButton rbbiff = new JRadioButton("BIFF - All games");
  private final JRadioButton rbbif = new JRadioButton("BIF - Only Icewind Dale");
  private final JRadioButton rbbifc = new JRadioButton("BIFC - Only Baldur's Gate 2");
  private final JRadioButton rbedit = new JRadioButton("Edit existing");
  private final JRadioButton rbcreate = new JRadioButton("Create new");
  private final JTextField tfbifname = new JTextField(10);

  ChooseBIFFrame(BIFFEditor editor)
  {
    super("Edit BIFF: Select file", true);
    setIconImage(Icons.getIcon(Icons.ICON_EDIT_16).getImage());
    this.editor = editor;
    bok.setMnemonic('o');
    bcancel.setMnemonic('c');
    rbcreate.setMnemonic('n');
    rbedit.setMnemonic('e');
    getRootPane().setDefaultButton(bok);

    JPanel format = new JPanel();
    format.setLayout(new GridLayout(3, 1));
    format.add(rbbiff);
    format.add(rbbif);
    format.add(rbbifc);
    format.setBorder(BorderFactory.createTitledBorder("Select format:"));
    ButtonGroup bg1 = new ButtonGroup();
    bg1.add(rbbiff);
    bg1.add(rbbif);
    bg1.add(rbbifc);
    rbbiff.setSelected(true);

    JPanel bpanel = new JPanel(new GridLayout(1, 2, 6, 6));
    bpanel.add(bok);
    bpanel.add(bcancel);
    bok.addActionListener(this);
    bcancel.addActionListener(this);

    JPanel lowerpanel = new JPanel();
    lowerpanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
    lowerpanel.add(bpanel);

    tfbifname.setEnabled(false);
    rbbiff.setEnabled(false);
    rbbif.setEnabled(false);
    rbbifc.setEnabled(false);

    ButtonGroup bg2 = new ButtonGroup();
    bg2.add(rbedit);
    bg2.add(rbcreate);
    rbedit.setSelected(true);
    rbedit.addActionListener(this);
    rbcreate.addActionListener(this);

    cbbifname = new JComboBox<>(ResourceFactory.getKeyfile().getBIFFEntriesSorted());
    cbbifname.setSelectedIndex(0);
    cbbifname.setEditable(false);

    JPanel pane = (JPanel)getContentPane();
    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    pane.setLayout(gbl);

    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(6, 6, 3, 6);
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(rbedit, gbc);
    pane.add(rbedit);

    JLabel label1 = new JLabel("Name:");
    gbc.insets = new Insets(3, 6, 6, 3);
    gbc.anchor = GridBagConstraints.EAST;
    gbc.gridwidth = 1;
    gbl.setConstraints(label1, gbc);
    pane.add(label1);

    gbc.insets = new Insets(3, 3, 6, 6);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(cbbifname, gbc);
    pane.add(cbbifname);

    gbc.insets = new Insets(6, 6, 3, 6);
    gbc.anchor = GridBagConstraints.WEST;
    gbl.setConstraints(rbcreate, gbc);
    pane.add(rbcreate);

    JLabel label2 = new JLabel("Name:");
    gbc.insets = new Insets(3, 6, 3, 3);
    gbc.anchor = GridBagConstraints.EAST;
    gbc.gridwidth = 1;
    gbl.setConstraints(label2, gbc);
    pane.add(label2);

    gbc.insets = new Insets(3, 3, 3, 6);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(tfbifname, gbc);
    pane.add(tfbifname);
    tfbifname.addActionListener(this);

    JLabel label3 = new JLabel("(File will be saved as data\\<filename>)");
    gbl.setConstraints(label3, gbc);
    pane.add(label3);

//    gbc.insets = new Insets(3, 6, 6, 6);
//    gbc.fill = GridBagConstraints.NONE;
//    gbc.weighty = 1.0;
//    gbc.weightx = 0.0;
//    gbl.setConstraints(format, gbc);
//    pane.add(format);

    gbc.weighty = 0.0;
    gbl.setConstraints(lowerpanel, gbc);
    pane.add(lowerpanel);

    pack();
    Center.center(this, NearInfinity.getInstance().getBounds());
    setVisible(true);
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == bcancel) {
      close();
    }
    else if (event.getSource() == rbedit) {
      cbbifname.setEnabled(true);
      tfbifname.setEnabled(false);
      rbbiff.setEnabled(false);
      rbbif.setEnabled(false);
      rbbifc.setEnabled(false);
    }
    else if (event.getSource() == rbcreate) {
      cbbifname.setEnabled(false);
      tfbifname.setEnabled(true);
      rbbiff.setEnabled(true);

      rbbif.setEnabled(Profile.getProperty(Profile.Key.IS_SUPPORTED_BIF));
      rbbifc.setEnabled(Profile.getProperty(Profile.Key.IS_SUPPORTED_BIFC));
    }
    else if (event.getSource() == bok || event.getSource() == tfbifname) {
      if (rbcreate.isSelected()) {
        // Check if name exists
        String name = tfbifname.getText().toLowerCase(Locale.ENGLISH);
        if (name.isEmpty() || name.contains("\\") || name.contains("/")) {
          JOptionPane.showMessageDialog(this, "Illegal BIFF name", "Error", JOptionPane.ERROR_MESSAGE);
          return;
        }
        name = "data\\" + name;
        AbstractBIFFReader.Type form = AbstractBIFFReader.Type.BIFF;
        if (rbbif.isSelected()) {
          form = AbstractBIFFReader.Type.BIF;
        } else if (rbbifc.isSelected()) {
          form = AbstractBIFFReader.Type.BIFC;
        }
        if (!name.endsWith(".bif")) {
          name += ".bif";
        }
        for (int i = 0; i < cbbifname.getItemCount(); i++) {
          if (name.equalsIgnoreCase(cbbifname.getItemAt(i).getFileName())) {
            JOptionPane.showMessageDialog(this, "This BIFF already exists!", "Error",
                                          JOptionPane.ERROR_MESSAGE);
            return;
          }
        }
        close();
        editor.makeEditor(new BIFFEntry(Profile.getChitinKey(), name), form);
      }
      else {
        // Edit existing
        BIFFEntry entry = (BIFFEntry)cbbifname.getSelectedItem();
        JOptionPane.showMessageDialog(this, "Make sure you have a backup of " + entry.getPath(),
                                      "Warning", JOptionPane.WARNING_MESSAGE);
        try {
          AbstractBIFFReader file = ResourceFactory.getKeyfile().getBIFFFile(entry);
          close();
          editor.makeEditor(entry, file.getType());
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

// --------------------- End Interface ActionListener ---------------------
}
