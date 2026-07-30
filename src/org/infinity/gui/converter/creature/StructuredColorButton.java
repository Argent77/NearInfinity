// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Color;
import java.awt.Component;
import java.util.Locale;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JColorChooser;

/** Compact color-picker control used by the structured creator editors. */
final class StructuredColorButton extends JButton {
  private static final long serialVersionUID = 1L;

  private final String dialogTitleKey;
  private Color selectedColor;

  StructuredColorButton(Color initialColor, String dialogTitleKey) {
    this.dialogTitleKey = Objects.requireNonNull(dialogTitleKey, "dialogTitleKey");
    setSelectedColor(initialColor);
    addActionListener(event -> chooseColor(this));
  }

  Color getSelectedColor() {
    return selectedColor;
  }

  void setSelectedColor(Color color) {
    final Color previous = selectedColor;
    selectedColor = Objects.requireNonNull(color, "color");
    setText(String.format(Locale.ENGLISH, "#%02X%02X%02X",
        color.getRed(), color.getGreen(), color.getBlue()));
    setBackground(color);
    final int luminance = color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114;
    setForeground(luminance >= 140000 ? Color.BLACK : Color.WHITE);
    setOpaque(true);
    setToolTipText(CreatureAnimationMessages.get(dialogTitleKey));
    firePropertyChange("selectedColor", previous, selectedColor);
  }

  private void chooseColor(Component parent) {
    final Color choice = JColorChooser.showDialog(parent,
        CreatureAnimationMessages.get(dialogTitleKey), selectedColor);
    if (choice != null && !choice.equals(selectedColor)) {
      setSelectedColor(choice);
    }
  }
}
