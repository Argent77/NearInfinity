// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.tis;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.infinity.gui.ViewerUtil;
import org.infinity.gui.converter.AbstractConvertPanel;
import org.infinity.gui.converter.PanelUpdateEvent;
import org.infinity.resource.graphics.TisDecoder;
import org.infinity.util.Misc;

/** Subpanel with TIS-specific options. */
class TisOptionsPanel extends AbstractConvertPanel
    implements ActionListener, FocusListener, ChangeListener, DocumentListener {
  /** Used internally to identify the selected TIS version. */
  private enum TisVersion {
    LEGACY("Legacy"),
    PVRZ("PVRZ-based"),
    ;

    private final String label;
    TisVersion(String label) {
      this.label = label;
    }

    @SuppressWarnings("unused")
    public String getLabel() {
      return label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  static final String TILE_DIMENSION_HELP =
        TisDecoder.DEFAULT_TILE_DIMENSION + "x" + TisDecoder.DEFAULT_TILE_DIMENSION
      + " pixels is the standard tile dimension supported by unmodified game executables.\n\n"
      + "Nonstandard tile dimensions require:";
  private static final String TILE_DIMENSION_HELP_URL =
      "https://github.com/TheForgotten69/InfinityEngine-Enhancer";

  static final String TIS_VERSION_HELP =
        '"' + TisVersion.LEGACY.toString() + "\" is the original tileset format supported by all available\n"
      + "Infinity Engine games. Graphics data is stored in the TIS file directly.\n"
      + "Each tile is limited to a 256 color table.\n"
      + "Note: This format may produce visual artifacts in Enhanced Edition games.\n\n"
      + '"' + TisVersion.PVRZ.toString() + "\" uses a new tileset format that is only compatible with the\n"
      + "Enhanced Editions. Graphics data is stored separately in PVRZ files and is\n"
      + "not limited to 256 colors.";

  private JSlider slTileCount;
  private JTextField tfTileCount;
  private JComboBox<Integer> cbTileDimension;
  private JButton bTileDimensionHelp;
  private JComboBox<TisVersion> cbTisVersion;
  private JButton bTisVersionHelp;

  public TisOptionsPanel() {
    super(new GridBagLayout());
    init();
  }

  /** Returns the max. number of available tiles. Returns 0 if not tile count range is defined. */
  public int getMaxTileCount() {
    return slTileCount.isEnabled() ? slTileCount.getMaximum() : 0;
  }

  /**
   * Sets the max. number of available tiles.
   *
   * @param newValue Max. number of available tiles. Set to 0 to disable slider and text input controls.
   */
  public void setMaxTileCount(int newValue) {
    final boolean isEnabled = (newValue > 0);
    newValue = Math.max(1, newValue);
    if (newValue != slTileCount.getMaximum()) {
      slTileCount.setMaximum(newValue);
      if (!isEnabled) {
        slTileCount.setValue(slTileCount.getMinimum());
      }
      onSliderChanged();
    }
    slTileCount.setEnabled(isEnabled);
    tfTileCount.setEnabled(isEnabled);
  }

  /** Returns the selected number of tiles to consider in the conversion process. */
  public int getTileCount() {
    return slTileCount.isEnabled() ? slTileCount.getValue() : 0;
  }

  /** Sets the selected number of tiles to consider in the conversion process. */
  public void setTileCount(int newValue) {
    newValue = Misc.clamp(newValue, slTileCount.getMinimum(), slTileCount.getMaximum());
    slTileCount.setValue(newValue);
  }

  /** Returns the selected width and height of a square tile, in pixels. */
  public int getTileDimension() {
    return (Integer)cbTileDimension.getSelectedItem();
  }

  /** Selects the width and height of a square tile, in pixels. */
  public void setTileDimension(int tileDimension) {
    cbTileDimension.setSelectedItem(tileDimension);
  }

  /** Returns whether the legacy (palette-based) TIS version is selected. */
  public boolean isLegacyVersionSelected() {
    return cbTisVersion.getSelectedIndex() == 0;
  }

  /**
   * Specify {@code true} to select legacy (palette-based) TIS version or {@code false} to select PVRZ-based TIS
   * version.
   */
  public void setLegacyVersionSelected(boolean select) {
    cbTisVersion.setSelectedIndex(select ? 0 : 1);
  }

  /** Resets the panel to its initial state. */
  public void reset() {
    setTileDimension(TisDecoder.DEFAULT_TILE_DIMENSION);
    setLegacyVersionSelected(true);
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == bTileDimensionHelp) {
      JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(this), createTileDimensionHelpMessage(),
          "About tile dimensions", JOptionPane.INFORMATION_MESSAGE);
    } else if (e.getSource() == bTisVersionHelp) {
      JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(this), TIS_VERSION_HELP, "About tileset versions",
          JOptionPane.INFORMATION_MESSAGE);
    } else if (e.getSource() == cbTileDimension) {
      firePanelUpdate(PanelUpdateEvent.REASON_UNKNOWN);
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface FocusListener ---------------------

  @Override
  public void focusGained(FocusEvent e) {
  }

  @Override
  public void focusLost(FocusEvent e) {
    if (e.getSource() == tfTileCount) {
      onInputChanged(true);
    }
  }

  // --------------------- End Interface FocusListener ---------------------

  // --------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent e) {
    if (e.getSource() == slTileCount) {
      onSliderChanged();
    }
  }

  // --------------------- End Interface ChangeListener ---------------------

  // --------------------- Begin Interface DocumentListener ---------------------

  @Override
  public void insertUpdate(DocumentEvent e) {
    onInputChanged(false);
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    onInputChanged(false);
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    onInputChanged(false);
  }

  // --------------------- End Interface DocumentListener ---------------------

  /**
   * Validates the content of the text input control and updates the slider value if needed.
   *
   * @param autoRevert Specifies whether the content of the text input control should revert to a valid number.
   */
  private void onInputChanged(boolean autoRevert) {
    final Integer number = validateNumberString(tfTileCount.getText());
    if (number != null) {
      final int value = Misc.clamp(number, slTileCount.getMinimum(), slTileCount.getMaximum());
      if (slTileCount.getValue() != value) {
        slTileCount.setValue(value);
      }
      if (autoRevert) {
        tfTileCount.setText(Integer.toString(number));
      }
    } else if (autoRevert) {
      tfTileCount.setText(Integer.toString(slTileCount.getValue()));
    }
  }

  /** Synchronizes the text input control with the current slider value. */
  private void onSliderChanged() {
    String text = Integer.toString(slTileCount.getValue());
    if (!text.equals(tfTileCount.getText())) {
      tfTileCount.setText(text);
    }
  }

  private void init() {
    final JLabel lTileCount = new JLabel("# tiles to convert:");
    lTileCount.setToolTipText("Counting from left to right, top to bottom.");
    slTileCount = new JSlider(SwingConstants.HORIZONTAL, 1, 1, 1);
    slTileCount.addChangeListener(this);
    tfTileCount = new JTextField(6);
    tfTileCount.setText(Integer.toString(slTileCount.getValue()));
    tfTileCount.getDocument().addDocumentListener(this);
    tfTileCount.addFocusListener(this);

    final JLabel lTileDimension = new JLabel("Tile dimensions:");
    cbTileDimension = new JComboBox<>(new Integer[] { 64, 128, 256, TisDecoder.MAX_TILE_DIMENSION });
    cbTileDimension.setSelectedItem(TisDecoder.DEFAULT_TILE_DIMENSION);
    cbTileDimension.addActionListener(this);
    bTileDimensionHelp = createHelpButton("About tile dimensions...");
    bTileDimensionHelp.addActionListener(this);

    final JLabel lTisVersion = new JLabel("Tileset version:");
    cbTisVersion = new JComboBox<>(TisVersion.values());
    cbTisVersion.setSelectedIndex(0);
    bTisVersionHelp = createHelpButton("About tileset versions...");
    bTisVersionHelp.addActionListener(this);

    final GridBagConstraints c = new GridBagConstraints();

    // Subpanel for tile count
    final JPanel panelTileCount = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    panelTileCount.add(lTileCount, c);
    ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 0, 0), 0, 0);
    panelTileCount.add(slTileCount, c);
    ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    panelTileCount.add(tfTileCount, c);

    // Subpanel for tile dimensions
    final JPanel panelTileDimension = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    panelTileDimension.add(lTileDimension, c);
    ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    panelTileDimension.add(cbTileDimension, c);
    ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    panelTileDimension.add(bTileDimensionHelp, c);

    // Subpanel for tileset version
    final JPanel panelTisVersion = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    panelTisVersion.add(lTisVersion, c);
    ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    panelTisVersion.add(cbTisVersion, c);
    ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    panelTisVersion.add(bTisVersionHelp, c);

    // combining subpanels
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    add(panelTileCount, c);
    ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 0, 0), 0, 0);
    add(panelTileDimension, c);
    ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 0, 0), 0, 0);
    add(panelTisVersion, c);

    setMaxTileCount(0);
  }

  /**
   * Validates the given string for numeric content. Returns the converted number if successful, {@code false}
   * otherwise.
   */
  private static Integer validateNumberString(String text) {
    Integer retVal = null;
    if (text != null) {
      try {
        retVal = Integer.parseInt(text);
      } catch (NumberFormatException e) {
      }
    }
    return retVal;
  }

  static JButton createHelpButton(String toolTipText) {
    final JButton button = new JButton("?");
    button.setToolTipText(toolTipText);
    final Insets insets = button.getMargin();
    insets.left = insets.right = 4;
    button.setMargin(insets);
    return button;
  }

  static Object[] createTileDimensionHelpMessage() {
    return new Object[] {
        TILE_DIMENSION_HELP,
        ViewerUtil.createUrlLabel("Infinity Engine Enhancer", TILE_DIMENSION_HELP_URL),
    };
  }
}
