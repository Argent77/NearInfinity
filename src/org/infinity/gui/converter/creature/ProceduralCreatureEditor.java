// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

import org.infinity.gui.converter.creature.ProceduralCreatureGenerator.Archetype;
import org.infinity.gui.converter.creature.ProceduralCreatureGenerator.CreatureSize;
import org.infinity.gui.converter.creature.ProceduralCreatureGenerator.CreatureSpec;
import org.infinity.gui.converter.creature.ProceduralCreatureGenerator.Trait;

/** Localizable, language-independent editor for procedural creature drafts. */
final class ProceduralCreatureEditor extends JPanel {
  private static final long serialVersionUID = 1L;

  private final JComboBox<Archetype> archetypeCombo = new JComboBox<>(Archetype.values());
  private final JComboBox<CreatureSize> sizeCombo = new JComboBox<>(CreatureSize.values());
  private final StructuredColorButton bodyColorButton =
      new StructuredColorButton(new Color(47, 136, 94), "source.chooseBodyColor");
  private final StructuredColorButton accentColorButton =
      new StructuredColorButton(new Color(189, 146, 49), "source.chooseAccentColor");
  private final EnumMap<Trait, JCheckBox> traitChecks = new EnumMap<>(Trait.class);
  private final JSpinner seedSpinner =
      new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
  private final JLabel summaryLabel = new JLabel(" ");
  private final List<Runnable> changeListeners = new ArrayList<>();

  ProceduralCreatureEditor() {
    super(new GridBagLayout());
    initializeControls();
    initializeLayout();
    initializeListeners();
    updateSummary();
  }

  CreatureSpec getSpecification() {
    final EnumSet<Trait> traits = EnumSet.noneOf(Trait.class);
    for (final Trait trait : Trait.values()) {
      if (traitChecks.get(trait).isSelected()) {
        traits.add(trait);
      }
    }
    return new CreatureSpec(((Number) seedSpinner.getValue()).longValue(),
        (Archetype) archetypeCombo.getSelectedItem(), traits, bodyColorButton.getSelectedColor(),
        accentColorButton.getSelectedColor(), (CreatureSize) sizeCombo.getSelectedItem());
  }

  void addChangeListener(Runnable listener) {
    if (listener != null) {
      changeListeners.add(listener);
    }
  }

  void setEditorEnabled(boolean enabled) {
    archetypeCombo.setEnabled(enabled);
    sizeCombo.setEnabled(enabled);
    bodyColorButton.setEnabled(enabled);
    accentColorButton.setEnabled(enabled);
    seedSpinner.setEnabled(enabled);
    for (final JCheckBox checkBox : traitChecks.values()) {
      checkBox.setEnabled(enabled);
    }
  }

  private void initializeControls() {
    archetypeCombo.setSelectedItem(Archetype.QUADRUPED);
    sizeCombo.setSelectedItem(CreatureSize.STANDARD);
    for (final Trait trait : Trait.values()) {
      final JCheckBox checkBox = new JCheckBox(trait.toString());
      traitChecks.put(trait, checkBox);
    }
    traitChecks.get(Trait.ARMORED).setSelected(true);
    traitChecks.get(Trait.GLOWING).setSelected(true);
    traitChecks.get(Trait.HORNS).setSelected(true);
    traitChecks.get(Trait.TAIL).setSelected(true);
    traitChecks.get(Trait.FUR).setSelected(true);
    summaryLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
  }

  private void initializeLayout() {
    setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(3, 2, 3, 6);
    add(new JLabel(CreatureAnimationMessages.get("source.bodyPlan")), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(archetypeCombo, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.weightx = 0.0;
    add(new JLabel(CreatureAnimationMessages.get("source.size")), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(sizeCombo, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.weightx = 0.0;
    add(new JLabel(CreatureAnimationMessages.get("source.bodyColor")), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(bodyColorButton, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.weightx = 0.0;
    add(new JLabel(CreatureAnimationMessages.get("source.accentColor")), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(accentColorButton, gbc);

    final JPanel traitsPanel = new JPanel(new GridBagLayout());
    traitsPanel.setBorder(BorderFactory.createTitledBorder(
        CreatureAnimationMessages.get("source.traits")));
    final GridBagConstraints traitsConstraints = new GridBagConstraints();
    traitsConstraints.anchor = GridBagConstraints.LINE_START;
    traitsConstraints.fill = GridBagConstraints.HORIZONTAL;
    traitsConstraints.weightx = 1.0;
    traitsConstraints.insets = new Insets(2, 5, 2, 5);
    int traitIndex = 0;
    for (final Trait trait : Trait.values()) {
      traitsConstraints.gridx = traitIndex % 3;
      traitsConstraints.gridy = traitIndex / 3;
      traitsPanel.add(traitChecks.get(trait), traitsConstraints);
      traitIndex++;
    }
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    add(traitsPanel, gbc);

    final JPanel seedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    seedPanel.add(new JLabel(CreatureAnimationMessages.get("source.seed") + " "));
    seedPanel.add(seedSpinner);
    gbc.gridy++;
    add(seedPanel, gbc);

    gbc.gridy++;
    add(summaryLabel, gbc);
  }

  private void initializeListeners() {
    archetypeCombo.addActionListener(event -> inputChanged());
    sizeCombo.addActionListener(event -> inputChanged());
    bodyColorButton.addPropertyChangeListener("selectedColor", event -> inputChanged());
    accentColorButton.addPropertyChangeListener("selectedColor", event -> inputChanged());
    seedSpinner.addChangeListener(event -> inputChanged());
    for (final JCheckBox checkBox : traitChecks.values()) {
      checkBox.addActionListener(event -> inputChanged());
    }
  }

  private void inputChanged() {
    updateSummary();
    for (final Runnable listener : changeListeners) {
      listener.run();
    }
  }

  private void updateSummary() {
    final List<String> traits = new ArrayList<>();
    for (final Trait trait : Trait.values()) {
      if (traitChecks.get(trait).isSelected()) {
        traits.add(trait.toString());
      }
    }
    final String traitSummary = traits.isEmpty()
        ? CreatureAnimationMessages.get("source.summary.noTraits")
        : String.join(CreatureAnimationMessages.get("source.summary.traitSeparator"), traits);
    summaryLabel.setText(CreatureAnimationMessages.format("source.summary",
        archetypeCombo.getSelectedItem(), traitSummary, sizeCombo.getSelectedItem()));
  }
}
