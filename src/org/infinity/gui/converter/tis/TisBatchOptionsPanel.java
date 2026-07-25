// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.tis;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.infinity.gui.ViewerUtil;
import org.infinity.gui.converter.AbstractConvertPanel;
import org.infinity.resource.graphics.TisDecoder;

/** Options shared by all files processed by the batch TIS converter. */
class TisBatchOptionsPanel extends AbstractConvertPanel {
  private final JComboBox<Integer> cbTileDimension =
      new JComboBox<>(new Integer[] { 64, 128, 256, TisDecoder.MAX_TILE_DIMENSION });
  private final JComboBox<String> cbTisVersion = new JComboBox<>(new String[] { "Legacy", "PVRZ-based" });
  private final JButton bTileDimensionHelp = TisOptionsPanel.createHelpButton("About tile dimensions...");
  private final JButton bTisVersionHelp = TisOptionsPanel.createHelpButton("About tileset versions...");

  public TisBatchOptionsPanel() {
    super(new GridBagLayout());
    init();
  }

  /** Returns the selected width and height of a square tile, in pixels. */
  public int getTileDimension() {
    return (Integer)cbTileDimension.getSelectedItem();
  }

  /** Returns whether the legacy palette-based TIS version is selected. */
  public boolean isLegacyVersionSelected() {
    return cbTisVersion.getSelectedIndex() == 0;
  }

  /** Resets all options to their defaults. */
  public void reset() {
    cbTileDimension.setSelectedItem(TisDecoder.DEFAULT_TILE_DIMENSION);
    cbTisVersion.setSelectedIndex(0);
  }

  private void init() {
    final JLabel lTileDimension = new JLabel("Tile dimensions:");
    final JLabel lTisVersion = new JLabel("Tileset version:");
    final GridBagConstraints c = new GridBagConstraints();
    bTileDimensionHelp.addActionListener(e -> JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(this),
        TisOptionsPanel.createTileDimensionHelpMessage(), "About tile dimensions", JOptionPane.INFORMATION_MESSAGE));
    bTisVersionHelp.addActionListener(e -> JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(this),
        TisOptionsPanel.TIS_VERSION_HELP, "About tileset versions", JOptionPane.INFORMATION_MESSAGE));

    final JPanel dimPanel = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    dimPanel.add(cbTileDimension, c);
    ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    dimPanel.add(bTileDimensionHelp, c);

    final JPanel versionPanel = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    versionPanel.add(cbTisVersion, c);
    ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    versionPanel.add(bTisVersionHelp, c);

    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    add(lTileDimension, c);
    ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 0, 0), 0, 0);
    add(dimPanel, c);
    ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(8, 0, 0, 0), 0, 0);
    add(lTisVersion, c);
    ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 8, 0, 0), 0, 0);
    add(versionPanel, c);

    reset();
  }
}
