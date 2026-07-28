// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.infinity.gui.ChildFrame;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.Config;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.ExportResult;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.Message;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.Severity;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.ValidationReport;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.util.Logger;

/**
 * Integrated source-to-game creator for Enhanced Edition creature animation families.
 */
public final class CreatureAnimationCreator extends ChildFrame {
  private static final long serialVersionUID = 1L;

  private final JTextArea promptArea = new JTextArea(5, 28);
  private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
  private final JLabel descriptionLabel = new JLabel(" ");
  private final JButton generateButton = new JButton("Generate procedural draft");
  private final JButton importButton = new JButton("Import PNG folder...");
  private final JButton exportPngButton = new JButton("Export editable PNGs...");
  private final JButton clearButton = new JButton("Clear source");

  private final JTextArea equipmentPromptArea = new JTextArea(6, 28);
  private final JTextField equipmentSourceCodeField = new JTextField("AUTO", 5);
  private final JTextField equipmentTargetCodeField = new JTextField("AUTO", 5);
  private final JSpinner equipmentSeedSpinner =
      new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
  private final JLabel equipmentDescriptionLabel = new JLabel(" ");
  private final JButton equipmentGenerateButton = new JButton("Generate weapon overlay");

  private final JLabel headingLabel = new JLabel();
  private final JLabel gameLabel = new JLabel();
  private final JComboBox<CreatureAnimationFamily> familyCombo =
      new JComboBox<>(CreatureAnimationFamily.values());
  private final JTextField slotField = new JTextField(8);
  private final JLabel slotStatusLabel = new JLabel(" ");
  private final JTextField resrefField = new JTextField(8);
  private final JComboBox<BamFormat> formatCombo = new JComboBox<>(BamFormat.values());
  private final JCheckBox compressedCheck = new JCheckBox("Compress as BAMC", true);
  private final JCheckBox splitCheck = new JCheckBox("Split action groups into separate BAM files");
  private final JSpinner quadrantsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 9, 1));
  private final JSpinner armorLevelsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));
  private final JTextField outputField = new JTextField(30);
  private final JButton outputButton = new JButton("Browse...");

  private final JCheckBox lieDownCheck = new JCheckBox("Can lie down", true);
  private final JCheckBox infravisionCheck = new JCheckBox("Detected by infravision", true);
  private final JCheckBox falseColorCheck = new JCheckBox("Use false-color palette ranges");
  private final JCheckBox smoothPathCheck = new JCheckBox("Smooth 16-direction pathing", true);
  private final JCheckBox translucentCheck = new JCheckBox("Translucent rendering");
  private final JSpinner moveScaleSpinner = new JSpinner(new SpinnerNumberModel(9, 0, 255, 1));
  private final JSpinner ellipseSpinner = new JSpinner(new SpinnerNumberModel(16, 0, 255, 1));
  private final JSpinner personalSpaceSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 255, 1));
  private final JSpinner bloodSpinner = new JSpinner(new SpinnerNumberModel(47, 0, 255, 1));
  private final JSpinner chunksSpinner = new JSpinner(new SpinnerNumberModel(255, 0, 255, 1));

  private final JList<Sequence> sequenceList = new JList<>(Sequence.values());
  private final JComboBox<String> directionCombo = new JComboBox<>(AnimationPreviewPanel.PREVIEW_DIRECTIONS);
  private final AnimationPreviewPanel previewPanel = new AnimationPreviewPanel();
  private final JCheckBox playCheck = new JCheckBox("Play", true);
  private final JCheckBox pivotCheck = new JCheckBox("Show center", true);
  private final JSlider speedSlider = new JSlider(AnimationPreviewPanel.MIN_FRAME_RATE,
      AnimationPreviewPanel.MAX_FRAME_RATE, AnimationPreviewPanel.DEFAULT_FRAME_RATE);
  private final JLabel speedValueLabel = new JLabel();
  private final JSpinner zoomSpinner = new JSpinner(new SpinnerNumberModel(AnimationPreviewPanel.DEFAULT_ZOOM_PERCENT,
      AnimationPreviewPanel.MIN_ZOOM_PERCENT, AnimationPreviewPanel.MAX_ZOOM_PERCENT, 25));
  private final JLabel previewStatusLabel = new JLabel(" ");
  private final JLabel sourceStatusLabel = new JLabel("No source frames loaded");

  private final JButton validateButton = new JButton("Validate");
  private final JButton exportButton = new JButton("Export to override");
  private final JButton helpButton = new JButton("Help");
  private final JProgressBar progressBar = new JProgressBar(0, 100);
  private final JLabel operationLabel = new JLabel("Ready");

  private CreatureAnimationModel model = new CreatureAnimationModel();
  private EquipmentOverlayReference.Result equipmentResult;
  private Path lastSourceDirectory;
  private boolean busy;
  private boolean updatingEquipmentFields;

  public CreatureAnimationCreator() {
    super("Creature Animation Creator", true);
    initializeDefaults();
    initializeUi();
    initializeListeners();
    updateFamilyUi(false);
    updateSourceUi();
    updateFormatUi();
    updateSlotStatus();
    updatePreviewOptions();
    updateExportTooltip();
    setSize(new Dimension(1180, 760));
    setLocationRelativeTo(getParent());
  }

  @Override
  protected boolean windowClosing(boolean forced) throws Exception {
    CreatureAnimationCreatorSettings.store(createSettingsSnapshot());
    return super.windowClosing(forced);
  }

  private void initializeDefaults() {
    promptArea.setLineWrap(true);
    promptArea.setWrapStyleWord(true);
    promptArea.setText("armored emerald horned wolf with glowing gold eyes");
    promptArea.setToolTipText("A deterministic offline description. Body plan, colors, scale and visible traits "
        + "are parsed from the text.");

    equipmentPromptArea.setLineWrap(true);
    equipmentPromptArea.setWrapStyleWord(true);
    equipmentPromptArea.setText("I want an animation similar to the existing SOLAR, but instead of wielding a "
        + "sword, it should wield an ornate silver scythe with a blue glow.");
    equipmentPromptArea.setToolTipText("Name an ANIMATE.IDS reference, a compatible source weapon and the "
        + "replacement. The last named weapon is treated as the requested result.");

    final Path defaultOutput = getDefaultOutputDirectory();
    final CreatureAnimationCreatorSettings.State settings =
        CreatureAnimationCreatorSettings.load(defaultOutput, Profile.getGameRoot());
    final CreatureAnimationFamily family = settings.family != null
        && settings.family.isSupportedGame(Profile.getGame()) ? settings.family : CreatureAnimationFamily.MONSTER;
    familyCombo.setSelectedItem(family);
    final int slot = findSuggestedSlot(family);
    slotField.setText(String.format(Locale.ENGLISH, "0x%04X", slot));
    resrefField.setText(getSuggestedResref(family, slot));
    gameLabel.setText(Profile.getGame().getTitle());

    if (settings.outputDirectory != null) {
      outputField.setText(settings.outputDirectory.toString());
    }
    lastSourceDirectory = settings.sourceDirectory;
    formatCombo.setSelectedItem(settings.bamFormat);
    compressedCheck.setSelected(settings.compressedBam);
    splitCheck.setSelected(settings.splitBams);
    quadrantsSpinner.setValue(settings.quadrants);
    armorLevelsSpinner.setValue(settings.armorLevels);
    lieDownCheck.setSelected(settings.canLieDown);
    infravisionCheck.setSelected(settings.detectedByInfravision);
    falseColorCheck.setSelected(settings.falseColor);
    smoothPathCheck.setSelected(settings.pathSmooth);
    translucentCheck.setSelected(settings.translucent);
    moveScaleSpinner.setValue(settings.moveScale);
    ellipseSpinner.setValue(settings.ellipse);
    personalSpaceSpinner.setValue(settings.personalSpace);
    bloodSpinner.setValue(settings.bloodColor);
    chunksSpinner.setValue(settings.chunkColor);
    sequenceList.setSelectedValue(settings.previewSequence, true);
    directionCombo.setSelectedIndex(settings.previewDirection);
    playCheck.setSelected(settings.previewPlaying);
    pivotCheck.setSelected(settings.previewPivot);
    speedSlider.setValue(settings.previewFrameRate);
    zoomSpinner.setValue(settings.previewZoom);

    outputField.setToolTipText("The initial default is the active game's install override directory. "
        + "The selected directory is remembered.");
    outputButton.setToolTipText("Choose a different output directory.");
  }

  private void initializeUi() {
    final JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    setContentPane(content);

    headingLabel.setFont(headingLabel.getFont().deriveFont(Font.BOLD, headingLabel.getFont().getSize2D() + 1.0f));
    final JLabel boundary = new JLabel("<html>Generate a coherent offline procedural draft, or import artist-authored "
        + "PNG sequences. Existing synchronized weapon layers can also be redrawn from a prompt while retaining the "
        + "reference animation's timing and grip motion.</html>");
    boundary.setForeground(UIManager.getColor("Label.disabledForeground"));
    final JPanel header = new JPanel(new BorderLayout(4, 3));
    header.add(headingLabel, BorderLayout.NORTH);
    header.add(boundary, BorderLayout.CENTER);
    content.add(header, BorderLayout.NORTH);

    final JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Source", createSourcePanel());
    tabs.addTab("Equipment overlay", createEquipmentPanel());
    tabs.addTab("Definition", createDefinitionPanel());
    tabs.addTab("Engine properties", createEnginePanel());

    final JPanel left = new JPanel(new BorderLayout());
    left.add(tabs, BorderLayout.CENTER);
    left.setMinimumSize(new Dimension(390, 450));
    left.setPreferredSize(new Dimension(420, 600));

    final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, createPreviewPanel());
    splitPane.setResizeWeight(0.37);
    splitPane.setContinuousLayout(true);
    content.add(splitPane, BorderLayout.CENTER);
    content.add(createBottomPanel(), BorderLayout.SOUTH);
  }

  private JPanel createSourcePanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
    final GridBagConstraints gbc = baseConstraints();

    addWide(panel, new JLabel("Creature description"), gbc, 0);
    gbc.gridy = 1;
    gbc.weighty = 0.35;
    gbc.fill = GridBagConstraints.BOTH;
    panel.add(new JScrollPane(promptArea), gbc);

    final JPanel seedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    seedPanel.add(new JLabel("Seed: "));
    seedPanel.add(seedSpinner);
    addWide(panel, seedPanel, gbc, 2);

    descriptionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, descriptionLabel, gbc, 3);

    final JPanel generationButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    generationButtons.add(generateButton);
    generationButtons.add(clearButton);
    addWide(panel, generationButtons, gbc, 4);

    final JLabel offlineNote = new JLabel("<html><b>Offline renderer:</b> deterministic Java2D templates produce "
        + "actual action/direction artwork without a model download. This is a coherent editable draft, not "
        + "open-ended text-to-image AI.</html>");
    offlineNote.setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));
    addWide(panel, offlineNote, gbc, 5);

    final JPanel interchange = new JPanel(new GridBagLayout());
    interchange.setBorder(BorderFactory.createTitledBorder("Artist interchange"));
    final GridBagConstraints igbc = baseConstraints();
    igbc.insets = new Insets(4, 5, 4, 5);
    igbc.fill = GridBagConstraints.HORIZONTAL;
    interchange.add(importButton, igbc);
    igbc.gridy = 1;
    interchange.add(exportPngButton, igbc);
    igbc.gridy = 2;
    igbc.weighty = 1.0;
    interchange.add(new JLabel("<html>Accepts <code>WK_S_000.png</code>, "
        + "<code>WK/S/000.png</code>, and exported <code>centers.csv</code> pivots.</html>"), igbc);
    gbc.gridy = 6;
    gbc.weighty = 0.65;
    gbc.fill = GridBagConstraints.BOTH;
    panel.add(interchange, gbc);

    sourceStatusLabel.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
    addWide(panel, sourceStatusLabel, gbc, 7);
    return panel;
  }

  private JPanel createEquipmentPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
    final GridBagConstraints gbc = baseConstraints();
    int row = 0;

    addWide(panel, new JLabel("Reference-and-replace prompt"), gbc, row++);
    final GridBagConstraints promptConstraints = (GridBagConstraints) gbc.clone();
    promptConstraints.gridy = row++;
    promptConstraints.gridwidth = 2;
    promptConstraints.weighty = 0.38;
    promptConstraints.fill = GridBagConstraints.BOTH;
    panel.add(new JScrollPane(equipmentPromptArea), promptConstraints);

    final JPanel codes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    codes.add(new JLabel("Source layer:"));
    codes.add(equipmentSourceCodeField);
    codes.add(new JLabel("New appearance:"));
    codes.add(equipmentTargetCodeField);
    addWide(panel, codes, gbc, row++);

    final JLabel codeHelp = new JLabel("<html>Use <code>AUTO</code> for resource-driven source discovery and "
        + "collision-free target selection. Source layer codes are one or two characters according to the family; "
        + "the target remains a two-character ITM <b>Equipped appearance</b> value.</html>");
    codeHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, codeHelp, gbc, row++);

    final JPanel generation = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    generation.add(equipmentGenerateButton);
    generation.add(new JLabel("Seed:"));
    generation.add(equipmentSeedSpinner);
    addWide(panel, generation, gbc, row++);

    equipmentDescriptionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, equipmentDescriptionLabel, gbc, row++);

    final JLabel mechanism = new JLabel("<html><b>How it works:</b> the creator accepts every Near Infinity decoder "
        + "that defines weapon sprite segments: <code>character</code>, <code>character_old</code>, "
        + "<code>monster</code>, <code>monster_layered_spell</code>, <code>monster_layered</code>, and "
        + "<code>monster_icewind</code>. It follows that family's exact filenames, cycles, directions, height code "
        + "and Equipped appearance semantics, while leaving avatar BAMs and animation definitions unchanged.</html>");
    mechanism.setBorder(BorderFactory.createEmptyBorder(9, 0, 8, 0));
    addWide(panel, mechanism, gbc, row++);

    final JLabel limitations = new JLabel("<html>A complete family-compatible weapon layer remains required because "
        + "avatar pixels alone do not provide a reliable grip axis or occlusion order. Explicit eastern resources are "
        + "retained where the family stores them. Procedural sickles, scythes, swords, axes, maces, hammers, spears, "
        + "polearms, staves, clubs, flails, bows and whips are supported.</html>");
    limitations.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, limitations, gbc, row++);

    final GridBagConstraints filler = (GridBagConstraints) gbc.clone();
    filler.gridy = row;
    filler.weighty = 0.62;
    filler.fill = GridBagConstraints.BOTH;
    panel.add(new JPanel(), filler);
    return panel;
  }

  private JPanel createDefinitionPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
    final GridBagConstraints gbc = baseConstraints();
    int row = 0;

    addRow(panel, "Target game:", gameLabel, gbc, row++);
    addRow(panel, "Animation family:", familyCombo, gbc, row++);
    addRow(panel, "Animation slot:", slotField, gbc, row++);
    slotStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    addWide(panel, slotStatusLabel, gbc, row++);
    addRow(panel, "BAM resref:", resrefField, gbc, row++);
    final JLabel resrefHelp = new JLabel("<html>The validator derives the exact prefix budget from the selected "
        + "layout. Modern character bases use the documented four-character schema; Planescape's longest standard "
        + "action leaves three characters.</html>");
    resrefHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, resrefHelp, gbc, row++);

    addRow(panel, "Output format:", formatCombo, gbc, row++);
    addWide(panel, compressedCheck, gbc, row++);
    addWide(panel, splitCheck, gbc, row++);
    addRow(panel, "Spatial quadrants:", quadrantsSpinner, gbc, row++);
    addRow(panel, "Armor levels:", armorLevelsSpinner, gbc, row++);

    final JPanel outputPanel = new JPanel(new BorderLayout(5, 0));
    outputPanel.add(outputField, BorderLayout.CENTER);
    outputPanel.add(outputButton, BorderLayout.EAST);
    addRow(panel, "Output directory:", outputPanel, gbc, row++);

    final JLabel outputHelp = new JLabel("<html>Files are encoded in a staging directory, reopened and checked, "
        + "then moved into place as one rollback-capable transaction.</html>");
    outputHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, outputHelp, gbc, row++);

    gbc.gridy = row;
    gbc.weighty = 1.0;
    panel.add(new JPanel(), gbc);
    return panel;
  }

  private JPanel createEnginePanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
    final GridBagConstraints gbc = baseConstraints();
    int row = 0;

    addWide(panel, new JLabel("<html>These values are written to the generated slot INI. Defaults are conservative "
        + "humanoid/monster values and can be changed before every export.</html>"), gbc, row++);
    addWide(panel, lieDownCheck, gbc, row++);
    addWide(panel, infravisionCheck, gbc, row++);
    addWide(panel, falseColorCheck, gbc, row++);
    addWide(panel, smoothPathCheck, gbc, row++);
    addWide(panel, translucentCheck, gbc, row++);
    addRow(panel, "Movement scale:", moveScaleSpinner, gbc, row++);
    addRow(panel, "Selection ellipse:", ellipseSpinner, gbc, row++);
    addRow(panel, "Personal space:", personalSpaceSpinner, gbc, row++);
    addRow(panel, "Blood gradient:", bloodSpinner, gbc, row++);
    addRow(panel, "Chunk switch/color:", chunksSpinner, gbc, row++);

    final JLabel falseColorHelp = new JLabel("<html>False color is accepted only for BAM V1 sources that share one "
        + "indexed palette with transparency at index 0; the creator will not guess semantic skin, armor, hair or "
        + "equipment ranges.</html>");
    falseColorHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, falseColorHelp, gbc, row++);

    gbc.gridy = row;
    gbc.weighty = 1.0;
    panel.add(new JPanel(), gbc);
    return panel;
  }

  private JPanel createPreviewPanel() {
    final JPanel panel = new JPanel(new BorderLayout(7, 7));
    panel.setBorder(BorderFactory.createTitledBorder("Animation preview"));

    sequenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    sequenceList.setCellRenderer(new SequenceRenderer());
    final JScrollPane sequenceScroll = new JScrollPane(sequenceList);
    sequenceScroll.setPreferredSize(new Dimension(215, 450));
    panel.add(sequenceScroll, BorderLayout.WEST);

    final JPanel preview = new JPanel(new BorderLayout(5, 5));
    preview.add(directionCombo, BorderLayout.NORTH);
    preview.add(previewPanel, BorderLayout.CENTER);

    final JPanel controls = new JPanel(new GridBagLayout());
    final GridBagConstraints gbc = baseConstraints();
    gbc.insets = new Insets(2, 3, 2, 3);
    final JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    toggles.add(playCheck);
    toggles.add(pivotCheck);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    controls.add(toggles, gbc);
    gbc.gridx = 1;
    controls.add(new JLabel("Speed:"), gbc);
    gbc.gridx = 2;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    speedSlider.getAccessibleContext().setAccessibleName("Preview frame rate");
    controls.add(speedSlider, gbc);
    gbc.gridx = 3;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    speedValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    controls.add(speedValueLabel, gbc);
    gbc.gridx = 4;
    controls.add(new JLabel("Zoom:"), gbc);
    gbc.gridx = 5;
    zoomSpinner.setToolTipText("Percentage of the fitted preview size");
    zoomSpinner.getAccessibleContext().setAccessibleName("Preview zoom percentage");
    controls.add(zoomSpinner, gbc);
    gbc.gridx = 6;
    controls.add(new JLabel("%"), gbc);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 7;
    previewStatusLabel.setHorizontalAlignment(SwingConstants.LEFT);
    controls.add(previewStatusLabel, gbc);
    preview.add(controls, BorderLayout.SOUTH);

    panel.add(preview, BorderLayout.CENTER);
    return panel;
  }

  private JPanel createBottomPanel() {
    final JPanel result = new JPanel(new BorderLayout(7, 4));
    result.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

    final JPanel status = new JPanel(new BorderLayout(7, 0));
    progressBar.setPreferredSize(new Dimension(180, 18));
    progressBar.setStringPainted(false);
    status.add(operationLabel, BorderLayout.CENTER);
    status.add(progressBar, BorderLayout.EAST);
    result.add(status, BorderLayout.CENTER);

    final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    buttons.add(helpButton);
    buttons.add(validateButton);
    exportButton.setFont(exportButton.getFont().deriveFont(Font.BOLD));
    buttons.add(exportButton);
    result.add(buttons, BorderLayout.EAST);
    return result;
  }

  private void initializeListeners() {
    generateButton.addActionListener(event -> generateDraft());
    equipmentGenerateButton.addActionListener(event -> generateEquipmentOverlay());
    importButton.addActionListener(event -> importPngDirectory());
    exportPngButton.addActionListener(event -> exportPngDirectory());
    clearButton.addActionListener(event -> {
      if (getEditableModel().isEmpty() || JOptionPane.showConfirmDialog(this, "Clear all loaded source frames?",
          "Clear source",
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
        setModel(new CreatureAnimationModel());
      }
    });
    outputButton.addActionListener(event -> chooseOutputDirectory());
    validateButton.addActionListener(event -> showValidation());
    exportButton.addActionListener(event -> exportToGame());
    helpButton.addActionListener(event -> showHelp());

    sequenceList.addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting()) {
        previewPanel.setSequence(sequenceList.getSelectedValue());
        updatePreviewStatus();
      }
    });
    directionCombo.addActionListener(event -> {
      previewPanel.setDirectionIndex(directionCombo.getSelectedIndex());
      updatePreviewStatus();
    });
    playCheck.addActionListener(event -> previewPanel.setPlaying(playCheck.isSelected()));
    pivotCheck.addActionListener(event -> previewPanel.setShowPivot(pivotCheck.isSelected()));
    speedSlider.addChangeListener(event -> updatePreviewOptions());
    zoomSpinner.addChangeListener(event -> updatePreviewOptions());
    previewPanel.addPropertyChangeListener("frameStatus", event -> updatePreviewStatus());

    formatCombo.addActionListener(event -> updateFormatUi());
    familyCombo.addActionListener(event -> updateFamilyUi(true));
    splitCheck.addActionListener(event -> updateSourceUi());
    quadrantsSpinner.addChangeListener(event -> updateSlotStatus());
    armorLevelsSpinner.addChangeListener(event -> updateSlotStatus());
    slotField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateSlotStatus));
    outputField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
      updateSlotStatus();
      updateExportTooltip();
    }));
    promptArea.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateDescriptionSummary));
    seedSpinner.addChangeListener(event -> updateDescriptionSummary());
    equipmentPromptArea.getDocument()
        .addDocumentListener(new SimpleDocumentListener(this::equipmentGenerationInputChanged));
    equipmentSourceCodeField.getDocument()
        .addDocumentListener(new SimpleDocumentListener(this::equipmentGenerationInputChanged));
    equipmentTargetCodeField.getDocument()
        .addDocumentListener(new SimpleDocumentListener(this::updateEquipmentDescriptionSummary));
    equipmentSeedSpinner.addChangeListener(event -> equipmentGenerationInputChanged());
    updateDescriptionSummary();
    updateEquipmentDescriptionSummary();
  }

  private void generateDraft() {
    if (busy) {
      return;
    }
    setBusy(true, "Drawing procedural animation frames...", true);
    final String prompt = promptArea.getText();
    final long seed = ((Number) seedSpinner.getValue()).longValue();
    final SwingWorker<CreatureAnimationModel, Void> worker = new SwingWorker<CreatureAnimationModel, Void>() {
      @Override
      protected CreatureAnimationModel doInBackground() {
        return ProceduralCreatureGenerator.generate(prompt, seed,
            (completed, total, sequence, direction) -> setProgress((int) ((completed * 100L) / total)));
      }

      @Override
      protected void done() {
        try {
          setModel(get());
          operationLabel.setText("Procedural draft generated");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          showFailure("Creature generation was interrupted.", e);
        } catch (ExecutionException e) {
          showFailure("Could not generate the procedural draft.", e.getCause());
        } finally {
          setBusy(false, null, false);
        }
      }
    };
    worker.addPropertyChangeListener(event -> {
      if ("progress".equals(event.getPropertyName())) {
        progressBar.setIndeterminate(false);
        progressBar.setValue((Integer) event.getNewValue());
      }
    });
    worker.execute();
  }

  private void generateEquipmentOverlay() {
    if (busy) {
      return;
    }
    setBusy(true, "Resolving family layout and drawing synchronized equipment...", true);
    final String prompt = equipmentPromptArea.getText();
    final String sourceCode = equipmentSourceCodeField.getText();
    final String targetCode = equipmentTargetCodeField.getText();
    final long seed = ((Number) equipmentSeedSpinner.getValue()).longValue();
    final SwingWorker<EquipmentOverlayReference.Result, Void> worker =
        new SwingWorker<EquipmentOverlayReference.Result, Void>() {
          @Override
          protected EquipmentOverlayReference.Result doInBackground() throws Exception {
            return EquipmentOverlayReference.generate(prompt, sourceCode, targetCode, seed,
                (completed, total, sequence, direction) ->
                    setProgress((int) ((completed * 100L) / total)));
          }

          @Override
          protected void done() {
            try {
              final EquipmentOverlayReference.Result result = get();
              updatingEquipmentFields = true;
              try {
                equipmentSourceCodeField.setText(result.getSourceAppearanceCode());
                equipmentTargetCodeField.setText(result.getTargetAppearanceCode());
              } finally {
                updatingEquipmentFields = false;
              }
              setEquipmentResult(result);
              equipmentSourceCodeField.setToolTipText("Available complete compatible layers: "
                  + String.join(", ", result.getAvailableAppearanceCodes()));
              operationLabel.setText("Synchronized " + result.getPrompt().getTargetWeapon().getLabel()
                  + " overlay generated");
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              showFailure("Equipment overlay generation was interrupted.", e);
            } catch (ExecutionException e) {
              showFailure("Could not generate the equipment overlay.", e.getCause());
            } finally {
              setBusy(false, null, false);
            }
          }
        };
    worker.addPropertyChangeListener(event -> {
      if ("progress".equals(event.getPropertyName())) {
        progressBar.setIndeterminate(false);
        progressBar.setValue((Integer) event.getNewValue());
      }
    });
    worker.execute();
  }

  private void importPngDirectory() {
    if (busy) {
      return;
    }
    final Path directory = chooseDirectory("Import creature animation PNG folder", lastSourceDirectory);
    if (directory == null) {
      return;
    }
    lastSourceDirectory = directory;
    setBusy(true, "Importing PNG sequences...", true);
    new SwingWorker<CreatureAnimationImporter.ImportResult, Void>() {
      @Override
      protected CreatureAnimationImporter.ImportResult doInBackground() throws Exception {
        return CreatureAnimationImporter.importDirectory(directory);
      }

      @Override
      protected void done() {
        try {
          final CreatureAnimationImporter.ImportResult result = get();
          setModel(result.getModel());
          operationLabel.setText(result.getFrameCount() + " PNG frame(s) imported");
          if (!result.getWarnings().isEmpty()) {
            showTextDialog("PNG import warnings", String.join("\n", result.getWarnings()),
                JOptionPane.WARNING_MESSAGE);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          showFailure("PNG import was interrupted.", e);
        } catch (ExecutionException e) {
          showFailure("Could not import the PNG source directory.", e.getCause());
        } finally {
          setBusy(false, null, false);
        }
      }
    }.execute();
  }

  private void exportPngDirectory() {
    final CreatureAnimationModel editableModel = getEditableModel();
    if (busy || editableModel.isEmpty()) {
      return;
    }
    final Path directory = chooseDirectory("Export editable creature animation PNGs", lastSourceDirectory);
    if (directory == null) {
      return;
    }
    lastSourceDirectory = directory;
    boolean hasFiles = false;
    try (Stream<Path> stream = Files.list(directory)) {
      hasFiles = stream.findAny().isPresent();
    } catch (Exception e) {
      showFailure("Could not inspect the selected PNG directory.", e);
      return;
    }
    if (hasFiles && JOptionPane.showConfirmDialog(this,
        "The selected directory is not empty. Overwrite matching exported PNG and metadata files?",
        "Confirm PNG export", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
      return;
    }

    setBusy(true, "Exporting editable PNG source...", true);
    new SwingWorker<Integer, Void>() {
      @Override
      protected Integer doInBackground() throws Exception {
        return CreatureAnimationImporter.exportDirectory(editableModel, directory, true);
      }

      @Override
      protected void done() {
        try {
          operationLabel.setText(get() + " editable PNG frame(s) exported");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          showFailure("PNG export was interrupted.", e);
        } catch (ExecutionException e) {
          showFailure("Could not export editable PNG source.", e.getCause());
        } finally {
          setBusy(false, null, false);
        }
      }
    }.execute();
  }

  private void exportToGame() {
    if (busy) {
      return;
    }
    if (equipmentResult != null) {
      exportEquipmentToGame();
      return;
    }
    final Config config;
    try {
      config = createConfig();
    } catch (Exception e) {
      showFailure("The export definition is invalid.", e);
      return;
    }

    final ValidationReport report = CreatureAnimationExporter.validate(model, config);
    if (report.hasErrors()) {
      showReport(report, "Creature animation validation", JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (report.hasWarnings() && JOptionPane.showConfirmDialog(this, createReportComponent(report),
        "Export with validation warnings?", JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
      return;
    }

    final List<Path> existing = CreatureAnimationExporter.getExistingPrimaryTargets(config);
    final boolean overwrite;
    if (!existing.isEmpty()) {
      final StringBuilder text = new StringBuilder("The following primary resources already exist:\n\n");
      for (final Path path : existing) {
        text.append("• ").append(path.getFileName()).append('\n');
      }
      text.append("\nReplace them after the staged output passes validation?");
      overwrite = JOptionPane.showConfirmDialog(this, text.toString(), "Confirm override replacement",
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
      if (!overwrite) {
        return;
      }
    } else {
      overwrite = false;
    }

    setBusy(true, "Encoding and validating game resources...", true);
    new SwingWorker<ExportResult, Void>() {
      @Override
      protected ExportResult doInBackground() throws Exception {
        return CreatureAnimationExporter.export(model, config, overwrite);
      }

      @Override
      protected void done() {
        try {
          final ExportResult result = get();
          for (final Path path : result.getInstalledFiles()) {
            ResourceFactory.registerResource(path, false);
          }
          operationLabel.setText(result.getInstalledFiles().size() + " game resource(s) installed");
          JOptionPane.showMessageDialog(CreatureAnimationCreator.this,
              config.getFamily() + " animation " + String.format(Locale.ENGLISH, "0x%04X", config.getAnimationId())
                  + " was installed as " + result.getInstalledFiles().size() + " validated resource(s) in:\n"
                  + config.getOutputDirectory(),
              "Creature animation exported", JOptionPane.INFORMATION_MESSAGE);
          updateSlotStatus();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          showFailure("Creature animation export was interrupted.", e);
        } catch (ExecutionException e) {
          showFailure("Creature animation export failed. Existing files were preserved or restored.", e.getCause());
        } finally {
          setBusy(false, null, false);
        }
      }
    }.execute();
  }

  private void exportEquipmentToGame() {
    final EquipmentOverlayExporter.Config config;
    try {
      config = createEquipmentConfig();
    } catch (Exception e) {
      showFailure("The equipment overlay definition is invalid.", e);
      return;
    }

    final EquipmentOverlayModel overlay = equipmentResult.getOverlayAnimation();
    final ValidationReport report = EquipmentOverlayExporter.validate(overlay, config);
    if (report.hasErrors()) {
      showReport(report, "Equipment overlay validation", JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (report.hasWarnings() && JOptionPane.showConfirmDialog(this, createReportComponent(report),
        "Export with validation warnings?", JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
      return;
    }

    final List<Path> existing = EquipmentOverlayExporter.getExistingTargets(config);
    final boolean overwrite;
    if (!existing.isEmpty()) {
      final StringBuilder text = new StringBuilder("The following equipment overlays already exist:\n\n");
      for (final Path path : existing) {
        text.append("• ").append(path.getFileName()).append('\n');
      }
      text.append("\nReplace them after the staged output passes validation?");
      overwrite = JOptionPane.showConfirmDialog(this, text.toString(), "Confirm overlay replacement",
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
      if (!overwrite) {
        return;
      }
    } else {
      overwrite = false;
    }

    setBusy(true, "Encoding and validating equipment overlay resources...", true);
    new SwingWorker<EquipmentOverlayExporter.ExportResult, Void>() {
      @Override
      protected EquipmentOverlayExporter.ExportResult doInBackground() throws Exception {
        return EquipmentOverlayExporter.export(overlay, config, overwrite);
      }

      @Override
      protected void done() {
        try {
          final EquipmentOverlayExporter.ExportResult result = get();
          for (final Path path : result.getInstalledFiles()) {
            ResourceFactory.registerResource(path, false);
          }
          operationLabel.setText(result.getInstalledFiles().size() + " equipment resource(s) installed");
          JOptionPane.showMessageDialog(CreatureAnimationCreator.this,
              "The " + equipmentResult.getPrompt().getTargetWeapon().getLabel() + " overlay was installed for "
                  + equipmentResult.getSymbol() + ".\n\n"
                  + "Set the equipped test ITM to "
                  + equipmentResult.getFamily().getActivationSummary(config.getAppearanceCode())
                  + " and equip it on the creature.\n\nOutput: " + config.getOutputDirectory(),
              "Equipment overlay exported", JOptionPane.INFORMATION_MESSAGE);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          showFailure("Equipment overlay export was interrupted.", e);
        } catch (ExecutionException e) {
          showFailure("Equipment overlay export failed. Existing files were preserved or restored.", e.getCause());
        } finally {
          setBusy(false, null, false);
        }
      }
    }.execute();
  }

  private boolean showValidation() {
    if (equipmentResult != null) {
      final EquipmentOverlayExporter.Config config;
      try {
        config = createEquipmentConfig();
      } catch (Exception e) {
        showFailure("The equipment overlay definition is invalid.", e);
        return false;
      }
      final ValidationReport report =
          EquipmentOverlayExporter.validate(equipmentResult.getOverlayAnimation(), config);
      final int messageType = report.hasErrors() ? JOptionPane.ERROR_MESSAGE
          : report.hasWarnings() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
      showReport(report, "Equipment overlay validation", messageType);
      return !report.hasErrors();
    }
    final Config config;
    try {
      config = createConfig();
    } catch (Exception e) {
      showFailure("The export definition is invalid.", e);
      return false;
    }
    final ValidationReport report = CreatureAnimationExporter.validate(model, config);
    final int messageType = report.hasErrors() ? JOptionPane.ERROR_MESSAGE
        : report.hasWarnings() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
    showReport(report, "Creature animation validation", messageType);
    return !report.hasErrors();
  }

  private Config createConfig() {
    final int animationId = parseAnimationId(slotField.getText());
    final String outputText = outputField.getText().trim();
    if (outputText.isEmpty()) {
      throw new IllegalArgumentException("Select an output directory.");
    }
    final Path output = Paths.get(outputText).toAbsolutePath().normalize();
    return new Config().setGame(Profile.getGame())
        .setFamily((CreatureAnimationFamily) familyCombo.getSelectedItem())
        .setAnimationId(animationId).setResref(resrefField.getText())
        .setOutputDirectory(output).setBamFormat((BamFormat) formatCombo.getSelectedItem())
        .setCompressedBam(compressedCheck.isSelected()).setSplitBams(splitCheck.isSelected())
        .setQuadrants((Integer) quadrantsSpinner.getValue())
        .setArmorLevels((Integer) armorLevelsSpinner.getValue())
        .setCanLieDown(lieDownCheck.isSelected()).setDetectedByInfravision(infravisionCheck.isSelected())
        .setFalseColor(falseColorCheck.isSelected()).setPathSmooth(smoothPathCheck.isSelected())
        .setTranslucent(translucentCheck.isSelected()).setMoveScale((Integer) moveScaleSpinner.getValue())
        .setEllipse((Integer) ellipseSpinner.getValue()).setPersonalSpace((Integer) personalSpaceSpinner.getValue())
        .setBloodColor((Integer) bloodSpinner.getValue()).setChunkColor((Integer) chunksSpinner.getValue());
  }

  private CreatureAnimationCreatorSettings.State createSettingsSnapshot() {
    final CreatureAnimationCreatorSettings.State settings =
        new CreatureAnimationCreatorSettings.State(getDefaultOutputDirectory(), Profile.getGameRoot());
    final String outputText = outputField.getText().trim();
    if (!outputText.isEmpty()) {
      try {
        settings.outputDirectory = Paths.get(outputText).toAbsolutePath().normalize();
      } catch (RuntimeException e) {
        Logger.trace(e);
      }
    }
    if (lastSourceDirectory != null) {
      settings.sourceDirectory = lastSourceDirectory.toAbsolutePath().normalize();
    }
    final CreatureAnimationFamily family = (CreatureAnimationFamily) familyCombo.getSelectedItem();
    settings.family = family != null ? family : CreatureAnimationFamily.MONSTER;
    final BamFormat bamFormat = (BamFormat) formatCombo.getSelectedItem();
    settings.bamFormat = bamFormat != null ? bamFormat : BamFormat.BAM_V1;
    settings.compressedBam = compressedCheck.isSelected();
    settings.splitBams = splitCheck.isSelected();
    settings.quadrants = (Integer) quadrantsSpinner.getValue();
    settings.armorLevels = (Integer) armorLevelsSpinner.getValue();
    settings.canLieDown = lieDownCheck.isSelected();
    settings.detectedByInfravision = infravisionCheck.isSelected();
    settings.falseColor = falseColorCheck.isSelected();
    settings.pathSmooth = smoothPathCheck.isSelected();
    settings.translucent = translucentCheck.isSelected();
    settings.moveScale = (Integer) moveScaleSpinner.getValue();
    settings.ellipse = (Integer) ellipseSpinner.getValue();
    settings.personalSpace = (Integer) personalSpaceSpinner.getValue();
    settings.bloodColor = (Integer) bloodSpinner.getValue();
    settings.chunkColor = (Integer) chunksSpinner.getValue();
    final Sequence sequence = sequenceList.getSelectedValue();
    settings.previewSequence = sequence != null ? sequence : Sequence.WALK;
    settings.previewDirection = directionCombo.getSelectedIndex();
    settings.previewPlaying = playCheck.isSelected();
    settings.previewPivot = pivotCheck.isSelected();
    settings.previewFrameRate = speedSlider.getValue();
    settings.previewZoom = (Integer) zoomSpinner.getValue();
    return settings;
  }

  private EquipmentOverlayExporter.Config createEquipmentConfig() {
    if (equipmentResult == null) {
      throw new IllegalStateException("Generate an equipment overlay first.");
    }
    final String outputText = outputField.getText().trim();
    if (outputText.isEmpty()) {
      throw new IllegalArgumentException("Select an output directory.");
    }
    final String appearanceCode = equipmentTargetCodeField.getText().trim().toUpperCase(Locale.ENGLISH);
    if (equipmentResult.getFamily().usesFullAppearanceCodeInFileName()
        && appearanceCode.equals(equipmentResult.getSourceAppearanceCode())) {
      throw new IllegalArgumentException("The target appearance code must differ from the source layer "
          + equipmentResult.getSourceAppearanceCode() + ".");
    }
    if (equipmentResult.getFamily().isAppearanceCodeRestrictedByDefinition()
        && !equipmentResult.getFamily().getFileCode(appearanceCode)
            .equals(equipmentResult.getFamily().getFileCode(equipmentResult.getTargetAppearanceCode()))) {
      throw new IllegalArgumentException(equipmentResult.getFamily()
          + " requires the target Equipped appearance to begin with "
          + equipmentResult.getFamily().getFileCode(equipmentResult.getTargetAppearanceCode()) + ".");
    }
    return new EquipmentOverlayExporter.Config().setFamily(equipmentResult.getFamily())
        .setResourcePrefix(equipmentResult.getResourcePrefix())
        .setAppearanceCode(appearanceCode)
        .setWeaponType(equipmentResult.getPrompt().getTargetWeapon())
        .setOutputDirectory(Paths.get(outputText).toAbsolutePath().normalize())
        .setBamFormat((BamFormat) formatCombo.getSelectedItem())
        .setCompressedBam(compressedCheck.isSelected());
  }

  private void chooseOutputDirectory() {
    Path initial = null;
    try {
      if (!outputField.getText().trim().isEmpty()) {
        initial = Paths.get(outputField.getText().trim());
      }
    } catch (Exception e) {
      Logger.trace(e);
    }
    final Path selected = chooseDirectory("Select creature animation output directory", initial);
    if (selected != null) {
      outputField.setText(selected.toAbsolutePath().normalize().toString());
    }
  }

  private Path chooseDirectory(String title, Path initial) {
    final Path initialDirectory = resolveInitialDirectory(initial, Profile.getGameRoot());
    final JFileChooser chooser = initialDirectory != null
        ? new JFileChooser(initialDirectory.toFile()) : new JFileChooser();
    chooser.setDialogTitle(title);
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    return chooser.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION
        ? chooser.getSelectedFile().toPath().toAbsolutePath().normalize() : null;
  }

  static Path resolveInitialDirectory(Path preferred, Path fallback) {
    if (preferred != null) {
      Path directory = preferred.toAbsolutePath().normalize();
      if (Files.isDirectory(directory)) {
        return directory;
      }
      directory = directory.getParent();
      while (directory != null && directory.getParent() != null) {
        if (Files.isDirectory(directory)) {
          return directory;
        }
        directory = directory.getParent();
      }
    }
    Path directory = fallback != null ? fallback.toAbsolutePath().normalize() : null;
    while (directory != null && !Files.isDirectory(directory)) {
      directory = directory.getParent();
    }
    return directory;
  }

  private void setModel(CreatureAnimationModel model) {
    equipmentResult = null;
    this.model = (model != null) ? model : new CreatureAnimationModel();
    previewPanel.setModel(this.model);
    previewPanel.setOverlayModel(null);
    updateModeUi();
    updateSourceUi();
  }

  private void setEquipmentResult(EquipmentOverlayReference.Result result) {
    equipmentResult = result;
    model = result != null ? result.getAvatarModel() : new CreatureAnimationModel();
    previewPanel.setModel(model);
    previewPanel.setEasternModel(result != null ? result.getAvatarEasternModel() : null);
    previewPanel.setOverlayModel(result != null ? result.getOverlayModel() : null);
    previewPanel.setOverlayEasternModel(result != null ? result.getOverlayEasternModel() : null);
    if (result != null) {
      familyCombo.setSelectedItem(result.getFamily().getCreatureFamily());
      slotField.setText(String.format(Locale.ENGLISH, "0x%04X", result.getAnimationId()));
      resrefField.setText(result.getResref());
      splitCheck.setSelected(result.isSplitBams());
      equipmentDescriptionLabel.setText(result.getSummary());
    }
    updateModeUi();
    updateSourceUi();
  }

  private CreatureAnimationModel getEditableModel() {
    return equipmentResult != null ? equipmentResult.getOverlayModel() : model;
  }

  private void updateSourceUi() {
    final CreatureAnimationModel editableModel = getEditableModel();
    if (equipmentResult != null) {
      final EquipmentOverlayModel equipment = equipmentResult.getOverlayAnimation();
      sourceStatusLabel.setText(equipment.isEmpty() ? "No equipment frames loaded"
          : "Equipment overlay • " + equipment.getFrameCount() + " frames • "
              + equipment.getPopulatedCellCount() + " action/direction cells • "
              + equipment.getPopulatedVariantCount() + " synchronized cycle variants");
    } else {
      final int cells = editableModel.getPopulatedCellCount();
      final int requiredCells = Sequence.values().length * Direction.values().length;
      sourceStatusLabel.setText(editableModel.isEmpty() ? "No source frames loaded"
          : editableModel.getFrameCount() + " frames • " + cells + "/" + requiredCells
              + " action/direction cells");
    }
    final boolean losslessPngExport = equipmentResult == null
        || equipmentResult.getFamily() == EquipmentOverlayFamily.MONSTER;
    exportPngButton.setEnabled(!busy && !editableModel.isEmpty() && losslessPngExport);
    exportPngButton.setToolTipText(losslessPngExport ? null
        : "This family's explicit eastern or repeated cycle variants cannot be represented losslessly by the "
            + "nine-direction neutral PNG interchange format.");
    clearButton.setEnabled(!busy && !editableModel.isEmpty());
    sequenceList.repaint();
    updatePreviewStatus();
  }

  private void updatePreviewStatus() {
    previewStatusLabel.setText(previewPanel.getStatusText());
  }

  private void updatePreviewOptions() {
    final int frameRate = speedSlider.getValue();
    previewPanel.setSequence(sequenceList.getSelectedValue());
    previewPanel.setDirectionIndex(directionCombo.getSelectedIndex());
    previewPanel.setFrameRate(frameRate);
    previewPanel.setZoomPercent((Integer) zoomSpinner.getValue());
    previewPanel.setPlaying(playCheck.isSelected());
    previewPanel.setShowPivot(pivotCheck.isSelected());
    speedValueLabel.setText(frameRate + " fps");
    speedSlider.setToolTipText("Preview speed: " + frameRate + " frames per second");
    updatePreviewStatus();
  }

  private void updateExportTooltip() {
    final String pathText = outputField.getText().trim();
    if (pathText.isEmpty()) {
      exportButton.setToolTipText("Select an output directory before exporting.");
      return;
    }
    try {
      final Path path = Paths.get(pathText).toAbsolutePath().normalize();
      final String resourceType = equipmentResult != null ? "equipment overlay" : "creature animation";
      exportButton.setToolTipText("Export the validated " + resourceType + " to " + path);
    } catch (RuntimeException e) {
      exportButton.setToolTipText("The current output directory is invalid: " + pathText);
    }
  }

  private void updateDescriptionSummary() {
    final ProceduralCreatureGenerator.Description description = ProceduralCreatureGenerator.parseDescription(
        promptArea.getText(), ((Number) seedSpinner.getValue()).longValue());
    descriptionLabel.setText(description.getArchetype() + " • " + description.getTraits().toString().toLowerCase(
        Locale.ENGLISH) + " • scale " + String.format(Locale.ENGLISH, "%.2f", description.getScale()));
  }

  private void updateEquipmentDescriptionSummary() {
    try {
      if (equipmentResult != null) {
        equipmentDescriptionLabel.setText(equipmentResult.getSummary() + " • export target "
            + equipmentTargetCodeField.getText().trim().toUpperCase(Locale.ENGLISH));
        return;
      }
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt(equipmentPromptArea.getText());
      final String source = equipmentSourceCodeField.getText().trim().isEmpty()
          ? "AUTO" : equipmentSourceCodeField.getText().trim().toUpperCase(Locale.ENGLISH);
      final String targetText = equipmentTargetCodeField.getText().trim();
      final String target = targetText.isEmpty() || "AUTO".equalsIgnoreCase(targetText)
          ? prompt.getTargetWeapon().getSuggestedAppearanceCode() + " (auto)" : targetText.toUpperCase(Locale.ENGLISH);
      equipmentDescriptionLabel.setText(prompt.getSummary() + " • source " + source + " • target " + target);
    } catch (Exception e) {
      equipmentDescriptionLabel.setText(e.getMessage());
    }
  }

  private void equipmentGenerationInputChanged() {
    if (!updatingEquipmentFields && equipmentResult != null) {
      setModel(new CreatureAnimationModel());
      operationLabel.setText("Equipment request changed • generate the synchronized overlay again");
    }
    updateEquipmentDescriptionSummary();
  }

  private void updateModeUi() {
    final boolean equipmentMode = equipmentResult != null;
    familyCombo.setEnabled(!busy && !equipmentMode);
    slotField.setEnabled(!busy && !equipmentMode);
    resrefField.setEnabled(!busy && !equipmentMode);
    moveScaleSpinner.setEnabled(!busy && !equipmentMode);
    ellipseSpinner.setEnabled(!busy && !equipmentMode);
    personalSpaceSpinner.setEnabled(!busy && !equipmentMode);
    bloodSpinner.setEnabled(!busy && !equipmentMode);
    chunksSpinner.setEnabled(!busy && !equipmentMode);
    exportButton.setText(equipmentMode ? "Export overlay to override" : "Export to override");
    updateExportTooltip();
    updateFamilyUi(false);
    updateFormatUi();
    updateSlotStatus();
  }

  private void updateFamilyUi(boolean resetDefinition) {
    final CreatureAnimationFamily family = (CreatureAnimationFamily) familyCombo.getSelectedItem();
    if (family == null) {
      return;
    }
    headingLabel.setText("Enhanced Edition creature animation authoring — " + family);
    if (resetDefinition && equipmentResult == null) {
      final int slot = findSuggestedSlot(family);
      slotField.setText(String.format(Locale.ENGLISH, "0x%04X", slot));
      resrefField.setText(getSuggestedResref(family, slot));
      if (family.hasQuadrants()) {
        quadrantsSpinner.setValue(family.getDefaultQuadrants());
      }
      if (family.hasArmorLevels()) {
        armorLevelsSpinner.setValue(family.getDefaultArmorLevels());
      }
    }

    if (family.getSplitMode() == CreatureAnimationFamily.SplitMode.NONE) {
      splitCheck.setSelected(false);
    } else if (family.isSplitBamsRequired()) {
      splitCheck.setSelected(true);
    } else if (resetDefinition) {
      splitCheck.setSelected(family.isSplitBamsDefault());
    }
    if (!family.isTranslucencySupported()) {
      translucentCheck.setSelected(false);
    }

    final boolean editable = !busy && equipmentResult == null;
    splitCheck.setEnabled(editable && family.getSplitMode() == CreatureAnimationFamily.SplitMode.OPTIONAL);
    quadrantsSpinner.setEnabled(editable && family.hasQuadrants());
    armorLevelsSpinner.setEnabled(editable && family.hasArmorLevels());
    lieDownCheck.setEnabled(editable && family.isCanLieDownSupported());
    infravisionCheck.setEnabled(editable && family.isInfravisionSupported());
    smoothPathCheck.setEnabled(editable && family.isPathSmoothSupported());
    translucentCheck.setEnabled(editable && family.isTranslucencySupported());
    updateFormatUi();
    updateSlotStatus();
  }

  private void updateFormatUi() {
    final boolean bamV1 = formatCombo.getSelectedItem() == BamFormat.BAM_V1;
    final CreatureAnimationFamily family = (CreatureAnimationFamily) familyCombo.getSelectedItem();
    compressedCheck.setEnabled(!busy && bamV1);
    final boolean falseColorAvailable =
        family != null && family.isFalseColorSupported() && equipmentResult == null;
    falseColorCheck.setEnabled(!busy && bamV1 && falseColorAvailable);
    if (!bamV1 || !falseColorAvailable) {
      falseColorCheck.setSelected(false);
    }
    final BamFormat format = (BamFormat) formatCombo.getSelectedItem();
    formatCombo.setToolTipText(format != null ? format.getDescription() : null);
  }

  private void updateSlotStatus() {
    if (equipmentResult != null) {
      slotStatusLabel.setForeground(new Color(47, 139, 72));
      slotStatusLabel.setText("Reference animation • overlay export leaves its INI and avatar BAMs unchanged");
      return;
    }
    try {
      final int slot = parseAnimationId(slotField.getText());
      final CreatureAnimationFamily family = (CreatureAnimationFamily) familyCombo.getSelectedItem();
      if (family == null) {
        throw new IllegalArgumentException("Select an animation family.");
      }
      if (!family.isSupportedGame(Profile.getGame())) {
        slotStatusLabel.setForeground(new Color(190, 55, 45));
        slotStatusLabel.setText(family + " is not supported by the active game");
      } else if (!family.isValidSlot(Profile.getGame(), slot)) {
        slotStatusLabel.setForeground(new Color(190, 55, 45));
        slotStatusLabel.setText("This slot does not belong to the selected " + family + " family");
      } else {
        final String fileName = String.format(Locale.ENGLISH, "%04X.INI", slot);
        final boolean occupied = ResourceFactory.resourceExists(fileName)
            || (!outputField.getText().trim().isEmpty() && Files.exists(Paths.get(outputField.getText().trim())
                .resolve(fileName)));
        slotStatusLabel.setForeground(occupied ? new Color(190, 118, 25) : new Color(47, 139, 72));
        slotStatusLabel.setText(occupied ? "Valid slot • an existing INI definition will require confirmation"
            : "Valid, currently unoccupied " + family + " slot");
      }
    } catch (Exception e) {
      slotStatusLabel.setForeground(new Color(190, 55, 45));
      slotStatusLabel.setText("Enter a hexadecimal slot such as 0x7303");
    }
  }

  private void setBusy(boolean busy, String text, boolean indeterminate) {
    this.busy = busy;
    setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    generateButton.setEnabled(!busy);
    equipmentGenerateButton.setEnabled(!busy);
    importButton.setEnabled(!busy);
    outputButton.setEnabled(!busy);
    validateButton.setEnabled(!busy);
    exportButton.setEnabled(!busy);
    formatCombo.setEnabled(!busy);
    progressBar.setIndeterminate(busy && indeterminate);
    if (!busy) {
      progressBar.setIndeterminate(false);
      progressBar.setValue(0);
    }
    if (text != null) {
      operationLabel.setText(text);
    }
    updateModeUi();
    updateSourceUi();
  }

  private void showHelp() {
    final String text = "Professional offline scope\n\n"
        + "The built-in renderer actually draws a complete animation family from a description, but uses deterministic "
        + "parametric body plans. It cannot invent arbitrary production art like a large diffusion model. Its purpose "
        + "is coherent direction/action blocking that can be exported, painted over and imported again.\n\n"
        + "Animation families\n\n"
        + "The family selector covers every real Near Infinity Enhanced Edition decoder from effect (0000) through "
        + "monster_planescape (F000). The exporter applies the selected family's own filenames, cycle offsets, "
        + "direction set, split policy, quadrant layout, armor codes and INI section. Planescape is offered only for "
        + "PSTEE. Character uses the verified split layout; new monster_multi definitions use the engine-safe "
        + "unsplit layout. Quadrant and armor counts are explicit definition options.\n\n"
        + "PNG source naming\n\n"
        + "WK_S_000.png, WK/S/000.png and WK_S/000.png are accepted. Actions are WK, SC, SD, GH, DE, TW, SL, GU, "
        + "A1-A5, SP and CA. Store S, SSW, SW, WSW, W, WNW, NW, NNW and N. The exporter mirrors those source cells "
        + "only where a target family requires explicit eastern cycles. centers.csv preserves each frame's BAM "
        + "pivot. Family-only actions use documented deterministic aliases; PST misc1-misc20 remain replaceable "
        + "custom sequences.\n\n"
        + "Equipment replacement\n\n"
        + "Weapon replacement is available for the six decoder families that define weapon sprite segments: "
        + "character, character_old, monster, monster_layered_spell, monster_layered and monster_icewind. Enter a "
        + "prompt such as: \"similar to SOLAR, but instead of a sword wielding an ornate silver scythe with blue "
        + "glow.\" The creator resolves the ANIMATE.IDS symbol, uses the decoder's exact height code, appearance-code "
        + "width, filenames, cycles and directions, and retains explicit eastern artwork where present. The target "
        + "ITM must use the reported Equipped appearance code. Families that use only its first character will "
        + "report that shared-prefix behavior before export. Avatar BAMs and animation definitions are not modified. "
        + "A complete compatible source layer remains required; avatar-only grip inference is intentionally rejected "
        + "because it cannot preserve alignment and occlusion reliably.\n\n"
        + "Export safety\n\n"
        + "The creator validates slot ranges, source coverage, centers, dimensions, palettes and filenames. It writes "
        + "to a staging directory, reopens the BAMs, checks cycle counts and PVRZ references, and only then installs "
        + "the full family. The initial output is the active game's install override directory, and the export "
        + "button tooltip always shows the current destination. Existing primary resources are replaced only after "
        + "confirmation and are restored if the transaction fails.";
    showTextDialog("Creature Animation Creator help", text, JOptionPane.INFORMATION_MESSAGE);
  }

  private void showReport(ValidationReport report, String title, int messageType) {
    JOptionPane.showMessageDialog(this, createReportComponent(report), title, messageType);
  }

  private Component createReportComponent(ValidationReport report) {
    final StringBuilder text = new StringBuilder();
    if (report.getMessages().isEmpty()) {
      text.append("Validation passed without warnings.");
    } else {
      for (final Message message : report.getMessages()) {
        final String marker = message.getSeverity() == Severity.ERROR ? "ERROR" :
            message.getSeverity() == Severity.WARNING ? "WARNING" : "INFO";
        text.append(marker).append(": ").append(message.getText()).append("\n\n");
      }
    }
    final JTextArea area = createDialogText(text.toString());
    final JScrollPane scroll = new JScrollPane(area);
    scroll.setPreferredSize(new Dimension(620, Math.min(360, 90 + report.getMessages().size() * 58)));
    return scroll;
  }

  private void showTextDialog(String title, String text, int messageType) {
    final JScrollPane scroll = new JScrollPane(createDialogText(text));
    scroll.setPreferredSize(new Dimension(650, 390));
    JOptionPane.showMessageDialog(this, scroll, title, messageType);
  }

  private JTextArea createDialogText(String text) {
    final JTextArea area = new JTextArea(text);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setCaretPosition(0);
    area.setBackground(UIManager.getColor("Panel.background"));
    area.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    return area;
  }

  private void showFailure(String message, Throwable throwable) {
    Logger.error(throwable);
    final String detail = throwable != null && throwable.getMessage() != null ? "\n\n" + throwable.getMessage() : "";
    JOptionPane.showMessageDialog(this, message + detail, "Creature Animation Creator", JOptionPane.ERROR_MESSAGE);
    operationLabel.setText("Operation failed");
  }

  private int findSuggestedSlot(CreatureAnimationFamily family) {
    final Profile.Game game = Profile.getGame();
    if (family != null) {
      if (!family.isSupportedGame(game)) {
        return family.getDefaultSlot();
      }
      for (int pass = 0; pass < 2; pass++) {
        final int start = (pass == 0) ? family.getDefaultSlot() : 0;
        final int end = (pass == 0) ? 0xffff : family.getDefaultSlot() - 1;
        for (int slot = start; slot <= end; slot++) {
          if (family.isValidSlot(game, slot)
              && !ResourceFactory.resourceExists(String.format(Locale.ENGLISH, "%04X.INI", slot))) {
            return slot;
          }
        }
      }
      return family.getDefaultSlot();
    }
    return 0x7303;
  }

  private static String getSuggestedResref(CreatureAnimationFamily family, int slot) {
    if (family == CreatureAnimationFamily.MONSTER_PLANESCAPE) {
      return String.format(Locale.ENGLISH, "P%02X", slot & 0xff);
    }
    return String.format(Locale.ENGLISH, "M%03X", slot & 0xfff);
  }

  private Path getDefaultOutputDirectory() {
    return Profile.getGameRoot() != null
        ? ResourceFactory.getDefaultSavePath(null).toAbsolutePath().normalize() : null;
  }

  private static int parseAnimationId(String value) {
    String normalized = (value != null) ? value.trim() : "";
    if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
      normalized = normalized.substring(2);
    }
    if (!normalized.matches("(?i)[0-9a-f]{1,4}")) {
      throw new IllegalArgumentException("Animation slot must be a 1-4 digit hexadecimal value.");
    }
    return Integer.parseInt(normalized, 16);
  }

  private static GridBagConstraints baseConstraints() {
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets = new Insets(4, 3, 4, 3);
    return gbc;
  }

  private static void addWide(JPanel panel, Component component, GridBagConstraints source, int row) {
    final GridBagConstraints gbc = (GridBagConstraints) source.clone();
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    gbc.fill = row == source.gridy ? source.fill : GridBagConstraints.HORIZONTAL;
    gbc.weighty = row == source.gridy ? source.weighty : 0.0;
    panel.add(component, gbc);
  }

  private static void addRow(JPanel panel, String label, Component component, GridBagConstraints source, int row) {
    final GridBagConstraints left = (GridBagConstraints) source.clone();
    left.gridx = 0;
    left.gridy = row;
    left.gridwidth = 1;
    left.weightx = 0.0;
    left.fill = GridBagConstraints.NONE;
    left.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(label), left);

    final GridBagConstraints right = (GridBagConstraints) source.clone();
    right.gridx = 1;
    right.gridy = row;
    right.gridwidth = 1;
    right.weightx = 1.0;
    right.fill = GridBagConstraints.HORIZONTAL;
    panel.add(component, right);
  }

  private final class SequenceRenderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
        boolean focused) {
      final JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
      if (value instanceof Sequence) {
        final Sequence sequence = (Sequence) value;
        final CreatureAnimationModel displayModel = getEditableModel();
        int directions = 0;
        int frames = 0;
        for (final Direction direction : Direction.values()) {
          if (displayModel.hasFrames(sequence, direction)) {
            directions++;
            frames += displayModel.getFrames(sequence, direction).size();
          }
        }
        label.setText(sequence.getCode() + "  " + sequence.getLabel() + "   " + directions + "/9 • " + frames);
        label.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
      }
      return label;
    }
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
