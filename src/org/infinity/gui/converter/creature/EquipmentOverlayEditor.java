// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.EquipmentSize;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.EquipmentSpec;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.WeaponType;
import org.infinity.gui.converter.creature.EquipmentOverlayReference.AnimationReference;

/** Localizable, language-independent editor for synchronized equipment overlays. */
final class EquipmentOverlayEditor extends JPanel {
  private static final long serialVersionUID = 1L;

  private final JComboBox<AnimationReference> referenceCombo;
  private final JComboBox<WeaponType> sourceTypeCombo;
  private final JComboBox<WeaponType> mainTypeCombo;
  private final JComboBox<WeaponType> offhandTypeCombo = new JComboBox<>();
  private final JCheckBox offhandCheck =
      new JCheckBox(CreatureAnimationMessages.get("equipment.offhandEnabled"));
  private final JComboBox<EquipmentSize> sizeCombo = new JComboBox<>(EquipmentSize.values());
  private final StructuredColorButton metalColorButton =
      new StructuredColorButton(new Color(205, 214, 226), "equipment.chooseMetalColor");
  private final StructuredColorButton accentColorButton =
      new StructuredColorButton(new Color(91, 59, 36), "equipment.chooseAccentColor");
  private final StructuredColorButton glowColorButton =
      new StructuredColorButton(new Color(54, 116, 211), "equipment.chooseGlowColor");
  private final JCheckBox glowingCheck =
      new JCheckBox(CreatureAnimationMessages.get("equipment.glowing"), true);
  private final JCheckBox ornateCheck =
      new JCheckBox(CreatureAnimationMessages.get("equipment.ornate"), true);

  private final JCheckBox sourceAutomaticCheck =
      new JCheckBox(CreatureAnimationMessages.get("equipment.automaticCode"), true);
  private final JCheckBox targetAutomaticCheck =
      new JCheckBox(CreatureAnimationMessages.get("equipment.automaticCode"), true);
  private final JCheckBox offhandSourceAutomaticCheck =
      new JCheckBox(CreatureAnimationMessages.get("equipment.automaticCode"), true);
  private final JCheckBox offhandTargetAutomaticCheck =
      new JCheckBox(CreatureAnimationMessages.get("equipment.automaticCode"), true);
  private final JTextField sourceCodeField = new JTextField(5);
  private final JTextField targetCodeField = new JTextField(5);
  private final JTextField offhandSourceCodeField = new JTextField(5);
  private final JTextField offhandTargetCodeField = new JTextField(5);
  private final JSpinner seedSpinner =
      new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
  private final JLabel summaryLabel = new JLabel(" ");
  private final List<Runnable> generationChangeListeners = new ArrayList<>();
  private final List<Runnable> targetChangeListeners = new ArrayList<>();

  private EquipmentOverlayReference.Result result;
  private boolean editorEnabled = true;
  private boolean updating;

  EquipmentOverlayEditor() {
    super(new GridBagLayout());
    referenceCombo = new JComboBox<>(EquipmentOverlayReference.getAnimationReferences()
        .toArray(new AnimationReference[0]));
    sourceTypeCombo = createWeaponCombo(true);
    mainTypeCombo = createWeaponCombo(false);
    initializeControls();
    initializeLayout();
    initializeListeners();
    updateOffhandTypes();
    updateControlState();
    updateSummary();
  }

  AnimationReference getAnimationReference() {
    return (AnimationReference) referenceCombo.getSelectedItem();
  }

  EquipmentSpec getSpecification() {
    return new EquipmentSpec((WeaponType) sourceTypeCombo.getSelectedItem(),
        (WeaponType) mainTypeCombo.getSelectedItem(),
        offhandCheck.isSelected() ? (WeaponType) offhandTypeCombo.getSelectedItem() : null,
        metalColorButton.getSelectedColor(), accentColorButton.getSelectedColor(),
        glowColorButton.getSelectedColor(), glowingCheck.isSelected(), ornateCheck.isSelected(),
        (EquipmentSize) sizeCombo.getSelectedItem());
  }

  String getSourceCodeOverride() {
    return getCodeOverride(sourceAutomaticCheck, sourceCodeField);
  }

  String getTargetCodeOverride() {
    return getCodeOverride(targetAutomaticCheck, targetCodeField);
  }

  String getOffhandSourceCodeOverride() {
    return getCodeOverride(offhandSourceAutomaticCheck, offhandSourceCodeField);
  }

  String getOffhandTargetCodeOverride() {
    return getCodeOverride(offhandTargetAutomaticCheck, offhandTargetCodeField);
  }

  String getTargetAppearanceCode() {
    return targetCodeField.getText();
  }

  String getOffhandTargetAppearanceCode() {
    return offhandTargetCodeField.getText();
  }

  long getSeed() {
    return ((Number) seedSpinner.getValue()).longValue();
  }

  boolean hasAnimationReferences() {
    return referenceCombo.getItemCount() > 0;
  }

  void addGenerationChangeListener(Runnable listener) {
    if (listener != null) {
      generationChangeListeners.add(listener);
    }
  }

  void addTargetChangeListener(Runnable listener) {
    if (listener != null) {
      targetChangeListeners.add(listener);
    }
  }

  void applyResult(EquipmentOverlayReference.Result generatedResult) {
    updating = true;
    try {
      result = generatedResult;
      sourceAutomaticCheck.setSelected(false);
      targetAutomaticCheck.setSelected(false);
      sourceCodeField.setText(generatedResult.getSourceAppearanceCode());
      targetCodeField.setText(generatedResult.getTargetAppearanceCode());
      if (generatedResult.hasOffhandOverlay()) {
        offhandSourceAutomaticCheck.setSelected(false);
        offhandTargetAutomaticCheck.setSelected(false);
        offhandSourceCodeField.setText(generatedResult.getOffhandSourceAppearanceCode());
        offhandTargetCodeField.setText(generatedResult.getOffhandTargetAppearanceCode());
      } else {
        offhandSourceAutomaticCheck.setSelected(true);
        offhandTargetAutomaticCheck.setSelected(true);
        offhandSourceCodeField.setText("");
        offhandTargetCodeField.setText("");
      }
      sourceCodeField.setToolTipText(CreatureAnimationMessages.format("equipment.availableSources",
          String.join(", ", generatedResult.getAvailableAppearanceCodes())));
      offhandSourceCodeField.setToolTipText(generatedResult.hasOffhandOverlay()
          ? CreatureAnimationMessages.format("equipment.availableOffhandSources",
              String.join(", ", generatedResult.getAvailableOffhandAppearanceCodes()))
          : null);
    } finally {
      updating = false;
    }
    updateControlState();
    updateSummary();
  }

  void clearResult() {
    result = null;
    updateSummary();
  }

  void setEditorEnabled(boolean enabled) {
    editorEnabled = enabled;
    updateControlState();
  }

  private void initializeControls() {
    referenceCombo.setMaximumRowCount(24);
    selectReference("SOLAR");
    sourceTypeCombo.setSelectedItem(WeaponType.SWORD);
    mainTypeCombo.setSelectedItem(WeaponType.SCYTHE);
    sizeCombo.setSelectedItem(EquipmentSize.STANDARD);
    summaryLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    if (!hasAnimationReferences()) {
      referenceCombo.setToolTipText(CreatureAnimationMessages.get("equipment.referenceUnavailable"));
    }
  }

  private void initializeLayout() {
    setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    final GridBagConstraints gbc = baseConstraints();
    addRow(this, CreatureAnimationMessages.get("equipment.reference"), referenceCombo, gbc, 0);

    final JPanel loadoutPanel = new JPanel(new GridBagLayout());
    loadoutPanel.setBorder(BorderFactory.createTitledBorder(
        CreatureAnimationMessages.get("equipment.loadout")));
    final GridBagConstraints lgbc = baseConstraints();
    addRow(loadoutPanel, CreatureAnimationMessages.get("equipment.sourceType"), sourceTypeCombo, lgbc, 0);
    addRow(loadoutPanel, CreatureAnimationMessages.get("equipment.mainType"), mainTypeCombo, lgbc, 1);
    addWide(loadoutPanel, offhandCheck, lgbc, 2);
    addRow(loadoutPanel, CreatureAnimationMessages.get("equipment.offhandType"), offhandTypeCombo, lgbc, 3);
    addWide(this, loadoutPanel, gbc, 1);

    final JPanel designPanel = new JPanel(new GridBagLayout());
    designPanel.setBorder(BorderFactory.createTitledBorder(
        CreatureAnimationMessages.get("equipment.design")));
    final GridBagConstraints dgbc = baseConstraints();
    addRow(designPanel, CreatureAnimationMessages.get("equipment.size"), sizeCombo, dgbc, 0);
    addRow(designPanel, CreatureAnimationMessages.get("equipment.metalColor"), metalColorButton, dgbc, 1);
    addRow(designPanel, CreatureAnimationMessages.get("equipment.accentColor"), accentColorButton, dgbc, 2);
    addRow(designPanel, CreatureAnimationMessages.get("equipment.glowColor"), glowColorButton, dgbc, 3);
    final JPanel effects = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    effects.add(glowingCheck);
    effects.add(ornateCheck);
    addWide(designPanel, effects, dgbc, 4);
    addWide(this, designPanel, gbc, 2);

    final JPanel codesPanel = new JPanel(new GridBagLayout());
    codesPanel.setBorder(BorderFactory.createTitledBorder(
        CreatureAnimationMessages.get("equipment.appearanceCodes")));
    final GridBagConstraints cgbc = baseConstraints();
    addCodeRow(codesPanel, CreatureAnimationMessages.get("equipment.mainSourceCode"),
        sourceAutomaticCheck, sourceCodeField, cgbc, 0);
    addCodeRow(codesPanel, CreatureAnimationMessages.get("equipment.mainTargetCode"),
        targetAutomaticCheck, targetCodeField, cgbc, 1);
    addCodeRow(codesPanel, CreatureAnimationMessages.get("equipment.offhandSourceCode"),
        offhandSourceAutomaticCheck, offhandSourceCodeField, cgbc, 2);
    addCodeRow(codesPanel, CreatureAnimationMessages.get("equipment.offhandTargetCode"),
        offhandTargetAutomaticCheck, offhandTargetCodeField, cgbc, 3);
    final JLabel codeHelp = new JLabel(CreatureAnimationMessages.get("equipment.codeHelp"));
    codeHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(codesPanel, codeHelp, cgbc, 4);
    addWide(this, codesPanel, gbc, 3);

    final JPanel seedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    seedPanel.add(new JLabel(CreatureAnimationMessages.get("equipment.seed") + " "));
    seedPanel.add(seedSpinner);
    addWide(this, seedPanel, gbc, 4);
    addWide(this, summaryLabel, gbc, 5);

    final JLabel structuredNote = new JLabel(
        CreatureAnimationMessages.get("equipment.structuredNote"));
    structuredNote.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(this, structuredNote, gbc, 6);
  }

  private void initializeListeners() {
    referenceCombo.addActionListener(event -> generationInputChanged());
    sourceTypeCombo.addActionListener(event -> generationInputChanged());
    mainTypeCombo.addActionListener(event -> {
      if (!updating) {
        updateOffhandTypes();
        generationInputChanged();
      }
    });
    offhandCheck.addActionListener(event -> {
      updateControlState();
      generationInputChanged();
    });
    offhandTypeCombo.addActionListener(event -> generationInputChanged());
    sizeCombo.addActionListener(event -> generationInputChanged());
    metalColorButton.addPropertyChangeListener("selectedColor", event -> generationInputChanged());
    accentColorButton.addPropertyChangeListener("selectedColor", event -> generationInputChanged());
    glowColorButton.addPropertyChangeListener("selectedColor", event -> generationInputChanged());
    glowingCheck.addActionListener(event -> generationInputChanged());
    ornateCheck.addActionListener(event -> generationInputChanged());
    seedSpinner.addChangeListener(event -> generationInputChanged());
    sourceAutomaticCheck.addActionListener(event -> {
      updateControlState();
      generationInputChanged();
    });
    targetAutomaticCheck.addActionListener(event -> {
      updateControlState();
      generationInputChanged();
    });
    offhandSourceAutomaticCheck.addActionListener(event -> {
      updateControlState();
      generationInputChanged();
    });
    offhandTargetAutomaticCheck.addActionListener(event -> {
      updateControlState();
      generationInputChanged();
    });
    sourceCodeField.getDocument().addDocumentListener(new SimpleDocumentListener(this::generationInputChanged));
    offhandSourceCodeField.getDocument()
        .addDocumentListener(new SimpleDocumentListener(this::generationInputChanged));
    targetCodeField.getDocument().addDocumentListener(new SimpleDocumentListener(this::targetInputChanged));
    offhandTargetCodeField.getDocument().addDocumentListener(new SimpleDocumentListener(this::targetInputChanged));
  }

  private void generationInputChanged() {
    if (updating) {
      return;
    }
    result = null;
    updateSummary();
    for (final Runnable listener : generationChangeListeners) {
      listener.run();
    }
  }

  private void targetInputChanged() {
    if (updating) {
      return;
    }
    updateSummary();
    for (final Runnable listener : targetChangeListeners) {
      listener.run();
    }
  }

  private void updateOffhandTypes() {
    final WeaponType previous = (WeaponType) offhandTypeCombo.getSelectedItem();
    final WeaponType mainHand = (WeaponType) mainTypeCombo.getSelectedItem();
    updating = true;
    try {
      offhandTypeCombo.removeAllItems();
      if (mainHand != null) {
        for (final WeaponType type : WeaponType.values()) {
          if ((type.isShield() && mainHand.allowsShield())
              || (type.isOneHandedMelee() && mainHand.isOneHandedMelee())) {
            offhandTypeCombo.addItem(type);
          }
        }
      }
      if (previous != null && containsItem(offhandTypeCombo, previous)) {
        offhandTypeCombo.setSelectedItem(previous);
      } else if (containsItem(offhandTypeCombo, WeaponType.MEDIUM_SHIELD)) {
        offhandTypeCombo.setSelectedItem(WeaponType.MEDIUM_SHIELD);
      }
      if (offhandTypeCombo.getItemCount() == 0) {
        offhandCheck.setSelected(false);
      }
    } finally {
      updating = false;
    }
    updateControlState();
  }

  private void updateControlState() {
    final boolean hasReference = hasAnimationReferences();
    referenceCombo.setEnabled(editorEnabled && hasReference);
    sourceTypeCombo.setEnabled(editorEnabled);
    mainTypeCombo.setEnabled(editorEnabled);
    sizeCombo.setEnabled(editorEnabled);
    metalColorButton.setEnabled(editorEnabled);
    accentColorButton.setEnabled(editorEnabled);
    glowColorButton.setEnabled(editorEnabled);
    glowingCheck.setEnabled(editorEnabled);
    ornateCheck.setEnabled(editorEnabled);
    seedSpinner.setEnabled(editorEnabled);

    final boolean supportsOffhandSelection = offhandTypeCombo.getItemCount() > 0;
    offhandCheck.setEnabled(editorEnabled && supportsOffhandSelection);
    final boolean offhandEnabled = editorEnabled && supportsOffhandSelection && offhandCheck.isSelected();
    offhandTypeCombo.setEnabled(offhandEnabled);

    sourceAutomaticCheck.setEnabled(editorEnabled);
    targetAutomaticCheck.setEnabled(editorEnabled);
    sourceCodeField.setEnabled(editorEnabled && !sourceAutomaticCheck.isSelected());
    targetCodeField.setEnabled(editorEnabled && !targetAutomaticCheck.isSelected());
    offhandSourceAutomaticCheck.setEnabled(offhandEnabled);
    offhandTargetAutomaticCheck.setEnabled(offhandEnabled);
    offhandSourceCodeField.setEnabled(offhandEnabled && !offhandSourceAutomaticCheck.isSelected());
    offhandTargetCodeField.setEnabled(offhandEnabled && !offhandTargetAutomaticCheck.isSelected());
  }

  private void updateSummary() {
    try {
      if (result != null) {
        final String target = normalizeCode(targetCodeField.getText());
        final String offhand = result.hasOffhandOverlay()
            ? CreatureAnimationMessages.format("equipment.resultSummary.offhand",
                normalizeCode(offhandTargetCodeField.getText())) : "";
        summaryLabel.setText(CreatureAnimationMessages.format("equipment.resultSummary",
            result.getSummary(), target, offhand));
        return;
      }
      final EquipmentSpec specification = getSpecification();
      final String source = sourceAutomaticCheck.isSelected()
          ? CreatureAnimationMessages.get("common.automatic") : normalizeCode(sourceCodeField.getText());
      final String target = targetAutomaticCheck.isSelected()
          ? specification.getTargetWeapon().getSuggestedAppearanceCode() + " ("
              + CreatureAnimationMessages.get("common.automatic") + ")"
          : normalizeCode(targetCodeField.getText());
      final String offhand = specification.hasOffhand()
          ? CreatureAnimationMessages.format("equipment.selectionSummary.offhand",
              offhandSourceAutomaticCheck.isSelected()
                  ? CreatureAnimationMessages.get("common.automatic")
                  : normalizeCode(offhandSourceCodeField.getText()),
              offhandTargetAutomaticCheck.isSelected()
                  ? specification.getTargetOffhand().getSuggestedAppearanceCode() + " ("
                      + CreatureAnimationMessages.get("common.automatic") + ")"
                  : normalizeCode(offhandTargetCodeField.getText()))
          : "";
      summaryLabel.setText(CreatureAnimationMessages.format("equipment.selectionSummary",
          specification.getSummary(), source, target, offhand));
    } catch (RuntimeException e) {
      summaryLabel.setText(e.getMessage());
    }
  }

  private void selectReference(String symbol) {
    for (int index = 0; index < referenceCombo.getItemCount(); index++) {
      final AnimationReference reference = referenceCombo.getItemAt(index);
      if (reference.getSymbol().equalsIgnoreCase(symbol)) {
        referenceCombo.setSelectedIndex(index);
        return;
      }
    }
    if (referenceCombo.getItemCount() > 0) {
      referenceCombo.setSelectedIndex(0);
    }
  }

  private static JComboBox<WeaponType> createWeaponCombo(boolean includeAutomatic) {
    final DefaultComboBoxModel<WeaponType> model = new DefaultComboBoxModel<>();
    if (includeAutomatic) {
      model.addElement(null);
    }
    for (final WeaponType type : WeaponType.values()) {
      if (!type.isShield()) {
        model.addElement(type);
      }
    }
    final JComboBox<WeaponType> comboBox = new JComboBox<>(model);
    comboBox.setRenderer(new DefaultListCellRenderer() {
      private static final long serialVersionUID = 1L;

      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
          boolean focused) {
        final String text = value != null ? value.toString()
            : CreatureAnimationMessages.get("common.automatic");
        return super.getListCellRendererComponent(list, text, index, selected, focused);
      }
    });
    return comboBox;
  }

  private static boolean containsItem(JComboBox<WeaponType> comboBox, WeaponType type) {
    for (int index = 0; index < comboBox.getItemCount(); index++) {
      if (comboBox.getItemAt(index) == type) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeCode(String value) {
    final String normalized = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
    return normalized.isEmpty() ? "\u2014" : normalized;
  }

  private static String getCodeOverride(JCheckBox automaticCheck, JTextField codeField) {
    if (automaticCheck.isSelected()) {
      return EquipmentOverlayReference.AUTOMATIC_CODE;
    }
    final String value = codeField.getText().trim();
    if (value.isEmpty() || EquipmentOverlayReference.AUTOMATIC_CODE.equalsIgnoreCase(value)) {
      throw new IllegalArgumentException(
          CreatureAnimationMessages.get("equipment.explicitCodeRequired"));
    }
    return value;
  }

  private static GridBagConstraints baseConstraints() {
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(3, 4, 3, 4);
    return gbc;
  }

  private static void addWide(JPanel panel, Component component, GridBagConstraints source, int row) {
    final GridBagConstraints gbc = (GridBagConstraints) source.clone();
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    panel.add(component, gbc);
  }

  private static void addRow(JPanel panel, String label, Component component, GridBagConstraints source, int row) {
    final GridBagConstraints left = (GridBagConstraints) source.clone();
    left.gridx = 0;
    left.gridy = row;
    left.weightx = 0.0;
    panel.add(new JLabel(label), left);
    final GridBagConstraints right = (GridBagConstraints) source.clone();
    right.gridx = 1;
    right.gridy = row;
    right.gridwidth = 2;
    right.weightx = 1.0;
    panel.add(component, right);
  }

  private static void addCodeRow(JPanel panel, String label, JCheckBox automatic, JTextField field,
      GridBagConstraints source, int row) {
    final GridBagConstraints labelConstraints = (GridBagConstraints) source.clone();
    labelConstraints.gridx = 0;
    labelConstraints.gridy = row;
    panel.add(new JLabel(label), labelConstraints);
    final GridBagConstraints automaticConstraints = (GridBagConstraints) source.clone();
    automaticConstraints.gridx = 1;
    automaticConstraints.gridy = row;
    automaticConstraints.weightx = 1.0;
    panel.add(automatic, automaticConstraints);
    final GridBagConstraints fieldConstraints = (GridBagConstraints) source.clone();
    fieldConstraints.gridx = 2;
    fieldConstraints.gridy = row;
    panel.add(field, fieldConstraints);
  }

  private static final class SimpleDocumentListener implements DocumentListener {
    private final Runnable action;

    private SimpleDocumentListener(Runnable action) {
      this.action = action;
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
      action.run();
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
      action.run();
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
      action.run();
    }
  }
}
