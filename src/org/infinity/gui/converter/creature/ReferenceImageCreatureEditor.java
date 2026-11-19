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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.gui.converter.creature.CreatureEquipmentLibrary.EquipmentAsset;
import org.infinity.gui.converter.creature.CreatureEquipmentLibrary.Slot;
import org.infinity.gui.converter.creature.CreatureTemplateLibrary.CreatureTemplate;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.EquipmentSize;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.EquipmentSpec;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.WeaponType;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.ProceduralCreatureGenerator.CreatureSize;
import org.infinity.gui.converter.creature.ReferenceImageCreatureGenerator.BackgroundMode;
import org.infinity.gui.converter.creature.ReferenceImageCreatureGenerator.ReferenceSpec;

/** Structured editor for the static-image plus reusable-template workflow. */
final class ReferenceImageCreatureEditor extends JPanel {
  private static final long serialVersionUID = 1L;

  private final JTextField sourceField = new JTextField(24);
  private final JButton browseButton = new JButton(CreatureAnimationMessages.get("source.referenceBrowse"));
  private final JComboBox<CreatureTemplate> templateCombo =
      new JComboBox<>(CreatureTemplateLibrary.getTemplates().toArray(new CreatureTemplate[0]));
  private final JComboBox<Direction> facingCombo = new JComboBox<>(Direction.values());
  private final JComboBox<BackgroundMode> backgroundCombo = new JComboBox<>(BackgroundMode.values());
  private final JComboBox<CreatureSize> sizeCombo = new JComboBox<>(CreatureSize.values());
  private final JComboBox<OptionalChoice<WeaponType>> mainHandCombo = new JComboBox<>();
  private final JComboBox<OptionalChoice<WeaponType>> offHandCombo = new JComboBox<>();
  private final JComboBox<OptionalChoice<EquipmentAsset>> armorCombo = new JComboBox<>();
  private final StructuredColorButton metalColorButton =
      new StructuredColorButton(new Color(205, 214, 226), "equipment.chooseMetalColor");
  private final StructuredColorButton accentColorButton =
      new StructuredColorButton(new Color(91, 58, 35), "equipment.chooseAccentColor");
  private final StructuredColorButton glowColorButton =
      new StructuredColorButton(new Color(123, 92, 255), "equipment.chooseGlowColor");
  private final StructuredColorButton armorColorButton =
      new StructuredColorButton(new Color(112, 119, 126), "equipment.chooseMetalColor");
  private final JCheckBox glowingCheck = new JCheckBox(CreatureAnimationMessages.get("equipment.glowing"));
  private final JCheckBox ornateCheck = new JCheckBox(CreatureAnimationMessages.get("equipment.ornate"));
  private final JSpinner seedSpinner =
      new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
  private final JLabel compatibilityLabel = new JLabel(" ");
  private final List<Runnable> changeListeners = new ArrayList<>();
  private Path chooserDirectory;

  ReferenceImageCreatureEditor() {
    super(new GridBagLayout());
    initializeChoices();
    initializeLayout();
    initializeListeners();
    updateEquipmentState();
  }

  ReferenceSpec getSpecification() throws IOException {
    final String sourceText = sourceField.getText().trim();
    if (sourceText.isEmpty()) {
      throw new IllegalArgumentException("Select a reference image.");
    }
    final Path sourcePath = Paths.get(sourceText).toAbsolutePath().normalize();
    if (!Files.isRegularFile(sourcePath)) {
      throw new IllegalArgumentException("Reference image does not exist: " + sourcePath);
    }
    final BufferedImage source = ImageIO.read(sourcePath.toFile());
    if (source == null) {
      throw new IOException("No installed ImageIO reader recognizes " + sourcePath.getFileName() + ".");
    }
    final WeaponType mainHand = getChoice(mainHandCombo);
    final WeaponType offHand = getChoice(offHandCombo);
    final EquipmentSpec equipment = mainHand != null
        ? new EquipmentSpec(null, mainHand, offHand, metalColorButton.getSelectedColor(),
            accentColorButton.getSelectedColor(), glowColorButton.getSelectedColor(), glowingCheck.isSelected(),
            ornateCheck.isSelected(), EquipmentSize.STANDARD)
        : null;
    if (mainHand == null && offHand != null) {
      throw new IllegalArgumentException("Select a main-hand item before selecting off-hand equipment.");
    }
    return new ReferenceSpec(source, (CreatureTemplate) templateCombo.getSelectedItem(),
        (Direction) facingCombo.getSelectedItem(), (BackgroundMode) backgroundCombo.getSelectedItem(),
        (CreatureSize) sizeCombo.getSelectedItem(), equipment, getChoice(armorCombo),
        armorColorButton.getSelectedColor(), ((Number) seedSpinner.getValue()).longValue());
  }

  void addChangeListener(Runnable listener) {
    if (listener != null) {
      changeListeners.add(listener);
    }
  }

  void setEditorEnabled(boolean enabled) {
    setEnabled(enabled);
    sourceField.setEnabled(enabled);
    browseButton.setEnabled(enabled);
    templateCombo.setEnabled(enabled);
    facingCombo.setEnabled(enabled);
    backgroundCombo.setEnabled(enabled);
    sizeCombo.setEnabled(enabled);
    mainHandCombo.setEnabled(enabled);
    armorCombo.setEnabled(enabled);
    armorColorButton.setEnabled(enabled);
    seedSpinner.setEnabled(enabled);
    updateEquipmentState();
  }

  private void initializeChoices() {
    final String none = CreatureAnimationMessages.get("source.templateNoEquipment");
    mainHandCombo.addItem(new OptionalChoice<WeaponType>(none, null));
    offHandCombo.addItem(new OptionalChoice<WeaponType>(none, null));
    armorCombo.addItem(new OptionalChoice<EquipmentAsset>(none, null));
    for (final WeaponType type : WeaponType.values()) {
      if (!type.isShield()) {
        mainHandCombo.addItem(new OptionalChoice<WeaponType>(type.getLabel(), type));
      }
      if (type.isShield() || type.isOneHandedMelee()) {
        offHandCombo.addItem(new OptionalChoice<WeaponType>(type.getLabel(), type));
      }
    }
    for (final EquipmentAsset armor : CreatureEquipmentLibrary.getAssets(Slot.TORSO)) {
      armorCombo.addItem(new OptionalChoice<EquipmentAsset>(armor.getLabel(), armor));
    }
    templateCombo.setSelectedItem(CreatureTemplateLibrary.getById("humanoid-balanced"));
    facingCombo.setSelectedItem(Direction.S);
    backgroundCombo.setSelectedItem(BackgroundMode.AUTO);
    sizeCombo.setSelectedItem(CreatureSize.STANDARD);
    compatibilityLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
  }

  private void initializeLayout() {
    setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(3, 2, 3, 6);

    final JPanel sourcePanel = new JPanel(new java.awt.BorderLayout(5, 0));
    sourcePanel.add(sourceField, java.awt.BorderLayout.CENTER);
    sourcePanel.add(browseButton, java.awt.BorderLayout.EAST);
    addRow(CreatureAnimationMessages.get("source.referenceImage"), sourcePanel, gbc);
    addRow(CreatureAnimationMessages.get("source.template"), templateCombo, gbc);
    addRow(CreatureAnimationMessages.get("source.sourceFacing"), facingCombo, gbc);
    addRow(CreatureAnimationMessages.get("source.background"), backgroundCombo, gbc);
    addRow(CreatureAnimationMessages.get("source.size"), sizeCombo, gbc);

    final JPanel loadout = new JPanel(new GridBagLayout());
    loadout.setBorder(BorderFactory.createTitledBorder(CreatureAnimationMessages.get("equipment.loadout")));
    final GridBagConstraints equipmentConstraints = new GridBagConstraints();
    equipmentConstraints.gridx = 0;
    equipmentConstraints.gridy = 0;
    equipmentConstraints.anchor = GridBagConstraints.LINE_START;
    equipmentConstraints.fill = GridBagConstraints.HORIZONTAL;
    equipmentConstraints.insets = new Insets(3, 5, 3, 5);
    addNestedRow(loadout, CreatureAnimationMessages.get("source.templateMainHand"), mainHandCombo,
        equipmentConstraints);
    addNestedRow(loadout, CreatureAnimationMessages.get("source.templateOffHand"), offHandCombo,
        equipmentConstraints);
    addNestedRow(loadout, CreatureAnimationMessages.get("source.templateArmor"), armorCombo, equipmentConstraints);
    addNestedRow(loadout, CreatureAnimationMessages.get("source.templateMetal"), metalColorButton,
        equipmentConstraints);
    addNestedRow(loadout, CreatureAnimationMessages.get("source.templateAccent"), accentColorButton,
        equipmentConstraints);
    addNestedRow(loadout, CreatureAnimationMessages.get("equipment.glowColor"), glowColorButton,
        equipmentConstraints);
    addNestedRow(loadout, CreatureAnimationMessages.get("source.templateArmorColor"), armorColorButton,
        equipmentConstraints);
    final JPanel effects = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    effects.add(glowingCheck);
    effects.add(ornateCheck);
    addNestedRow(loadout, CreatureAnimationMessages.get("equipment.design") + ":", effects, equipmentConstraints);

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    add(loadout, gbc);

    final JPanel seed = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    seed.add(new JLabel(CreatureAnimationMessages.get("source.templateSeed") + " "));
    seed.add(seedSpinner);
    gbc.gridy++;
    add(seed, gbc);
    gbc.gridy++;
    add(compatibilityLabel, gbc);
    gbc.gridy++;
    add(new JLabel(CreatureAnimationMessages.get("source.templateNote")), gbc);
  }

  private void initializeListeners() {
    browseButton.addActionListener(event -> browseForImage());
    sourceField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override
      public void insertUpdate(javax.swing.event.DocumentEvent event) {
        inputChanged();
      }

      @Override
      public void removeUpdate(javax.swing.event.DocumentEvent event) {
        inputChanged();
      }

      @Override
      public void changedUpdate(javax.swing.event.DocumentEvent event) {
        inputChanged();
      }
    });
    templateCombo.addActionListener(event -> {
      updateEquipmentState();
      inputChanged();
    });
    facingCombo.addActionListener(event -> inputChanged());
    backgroundCombo.addActionListener(event -> inputChanged());
    sizeCombo.addActionListener(event -> inputChanged());
    mainHandCombo.addActionListener(event -> {
      updateEquipmentState();
      inputChanged();
    });
    offHandCombo.addActionListener(event -> inputChanged());
    armorCombo.addActionListener(event -> {
      updateEquipmentState();
      inputChanged();
    });
    metalColorButton.addPropertyChangeListener("selectedColor", event -> inputChanged());
    accentColorButton.addPropertyChangeListener("selectedColor", event -> inputChanged());
    glowColorButton.addPropertyChangeListener("selectedColor", event -> inputChanged());
    armorColorButton.addPropertyChangeListener("selectedColor", event -> inputChanged());
    glowingCheck.addActionListener(event -> {
      updateEquipmentState();
      inputChanged();
    });
    ornateCheck.addActionListener(event -> inputChanged());
    seedSpinner.addChangeListener(event -> inputChanged());
  }

  private void browseForImage() {
    final JFileChooser chooser = new JFileChooser(chooserDirectory != null ? chooserDirectory.toFile() : null);
    chooser.setDialogTitle("Select creature reference image");
    chooser.setFileFilter(new FileNameExtensionFilter("Images supported by Java ImageIO",
        "png", "gif", "jpg", "jpeg", "bmp", "wbmp"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      final Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
      sourceField.setText(selected.toString());
      chooserDirectory = selected.getParent();
    }
  }

  private void updateEquipmentState() {
    final CreatureTemplate template = (CreatureTemplate) templateCombo.getSelectedItem();
    final WeaponType mainHand = getChoice(mainHandCombo);
    final boolean sockets = template != null && template.supportsEquipment();
    if (!sockets) {
      selectNone(mainHandCombo);
      selectNone(offHandCombo);
    }
    final boolean mainEnabled = isEnabled() && sockets;
    mainHandCombo.setEnabled(mainEnabled);
    final boolean offHandAllowed = mainEnabled && mainHand != null
        && (mainHand.isOneHandedMelee() || mainHand.allowsShield())
        && template.getOffHandSocket() != null;
    if (!offHandAllowed) {
      selectNone(offHandCombo);
    }
    offHandCombo.setEnabled(offHandAllowed);
    final boolean hasHandEquipment = mainEnabled && mainHand != null;
    metalColorButton.setEnabled(hasHandEquipment);
    accentColorButton.setEnabled(hasHandEquipment);
    glowingCheck.setEnabled(hasHandEquipment);
    ornateCheck.setEnabled(hasHandEquipment);
    glowColorButton.setEnabled(hasHandEquipment && glowingCheck.isSelected());
    armorColorButton.setEnabled(isEnabled() && getChoice(armorCombo) != null);
    compatibilityLabel.setText(sockets
        ? "Template sockets: main hand" + (template.getOffHandSocket() != null ? " + off hand" : "")
        : "This topology intentionally has no hand-equipment sockets.");
  }

  private void inputChanged() {
    for (final Runnable listener : changeListeners) {
      listener.run();
    }
  }

  private void addRow(String label, Component component, GridBagConstraints source) {
    final GridBagConstraints left = (GridBagConstraints) source.clone();
    left.gridx = 0;
    left.gridwidth = 1;
    left.weightx = 0.0;
    left.fill = GridBagConstraints.NONE;
    add(new JLabel(label), left);
    final GridBagConstraints right = (GridBagConstraints) source.clone();
    right.gridx = 1;
    right.gridwidth = 1;
    right.weightx = 1.0;
    right.fill = GridBagConstraints.HORIZONTAL;
    add(component, right);
    source.gridy++;
  }

  private static void addNestedRow(JPanel panel, String label, Component component, GridBagConstraints source) {
    final GridBagConstraints left = (GridBagConstraints) source.clone();
    left.gridx = 0;
    left.gridwidth = 1;
    left.weightx = 0.0;
    left.fill = GridBagConstraints.NONE;
    panel.add(new JLabel(label), left);
    final GridBagConstraints right = (GridBagConstraints) source.clone();
    right.gridx = 1;
    right.gridwidth = 1;
    right.weightx = 1.0;
    right.fill = GridBagConstraints.HORIZONTAL;
    panel.add(component, right);
    source.gridy++;
  }

  private static <T> T getChoice(JComboBox<OptionalChoice<T>> combo) {
    @SuppressWarnings("unchecked")
    final OptionalChoice<T> choice = (OptionalChoice<T>) combo.getSelectedItem();
    return choice != null ? choice.value : null;
  }

  private static <T> void selectNone(JComboBox<OptionalChoice<T>> combo) {
    if (combo.getSelectedIndex() != 0) {
      combo.setSelectedIndex(0);
    }
  }

  private static final class OptionalChoice<T> {
    private final String label;
    private final T value;

    private OptionalChoice(String label, T value) {
      this.label = label;
      this.value = value;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
