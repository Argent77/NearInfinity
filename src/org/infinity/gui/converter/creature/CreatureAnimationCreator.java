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
 * Integrated source-to-game creator for Enhanced Edition type 0x7000 creature animations.
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

  private final JLabel gameLabel = new JLabel();
  private final JTextField slotField = new JTextField(8);
  private final JLabel slotStatusLabel = new JLabel(" ");
  private final JTextField resrefField = new JTextField(8);
  private final JComboBox<BamFormat> formatCombo = new JComboBox<>(BamFormat.values());
  private final JCheckBox compressedCheck = new JCheckBox("Compress as BAMC", true);
  private final JCheckBox splitCheck = new JCheckBox("Split action groups into separate BAM files");
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
  private final JSlider speedSlider = new JSlider(45, 400, 110);
  private final JLabel previewStatusLabel = new JLabel(" ");
  private final JLabel sourceStatusLabel = new JLabel("No source frames loaded");

  private final JButton validateButton = new JButton("Validate");
  private final JButton exportButton = new JButton("Export to override");
  private final JButton helpButton = new JButton("Help");
  private final JProgressBar progressBar = new JProgressBar(0, 100);
  private final JLabel operationLabel = new JLabel("Ready");

  private CreatureAnimationModel model = new CreatureAnimationModel();
  private Path lastSourceDirectory;
  private boolean busy;

  public CreatureAnimationCreator() {
    super("Creature Animation Creator", true);
    initializeDefaults();
    initializeUi();
    initializeListeners();
    updateSourceUi();
    updateFormatUi();
    updateSlotStatus();
    setSize(new Dimension(1180, 760));
    setLocationRelativeTo(getParent());
  }

  private void initializeDefaults() {
    promptArea.setLineWrap(true);
    promptArea.setWrapStyleWord(true);
    promptArea.setText("armored emerald horned wolf with glowing gold eyes");
    promptArea.setToolTipText("A deterministic offline description. Body plan, colors, scale and visible traits "
        + "are parsed from the text.");

    final int slot = findSuggestedSlot();
    slotField.setText(String.format(Locale.ENGLISH, "0x%04X", slot));
    resrefField.setText(String.format(Locale.ENGLISH, "M%03X", slot & 0xfff));
    gameLabel.setText(Profile.getGame().getTitle());

    final Path output = getDefaultOutputDirectory();
    if (output != null) {
      outputField.setText(output.toAbsolutePath().normalize().toString());
    }
    outputField.setToolTipText("Defaults to the active game's highest-priority override directory.");
  }

  private void initializeUi() {
    final JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    setContentPane(content);

    final JLabel heading = new JLabel("Enhanced Edition creature animation authoring — family 0x7000");
    heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 1.0f));
    final JLabel boundary = new JLabel("<html>Generate a coherent offline procedural draft, or import artist-authored "
        + "PNG sequences. Eastern orientations are previewed exactly as the engine mirrors them.</html>");
    boundary.setForeground(UIManager.getColor("Label.disabledForeground"));
    final JPanel header = new JPanel(new BorderLayout(4, 3));
    header.add(heading, BorderLayout.NORTH);
    header.add(boundary, BorderLayout.CENTER);
    content.add(header, BorderLayout.NORTH);

    final JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Source", createSourcePanel());
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

  private JPanel createDefinitionPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
    final GridBagConstraints gbc = baseConstraints();
    int row = 0;

    addRow(panel, "Target game:", gameLabel, gbc, row++);
    addRow(panel, "Animation slot:", slotField, gbc, row++);
    slotStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    addWide(panel, slotStatusLabel, gbc, row++);
    addRow(panel, "BAM resref:", resrefField, gbc, row++);
    final JLabel resrefHelp = new JLabel("<html>1-4 ASCII characters; the remaining filename space is reserved "
        + "for the family suffix (for example, <code>G14</code>).</html>");
    resrefHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
    addWide(panel, resrefHelp, gbc, row++);

    addRow(panel, "Output format:", formatCombo, gbc, row++);
    addWide(panel, compressedCheck, gbc, row++);
    addWide(panel, splitCheck, gbc, row++);

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
    sequenceList.setSelectedValue(Sequence.WALK, true);
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
    speedSlider.setToolTipText("Preview frame delay");
    controls.add(speedSlider, gbc);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 3;
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
    importButton.addActionListener(event -> importPngDirectory());
    exportPngButton.addActionListener(event -> exportPngDirectory());
    clearButton.addActionListener(event -> {
      if (model.isEmpty() || JOptionPane.showConfirmDialog(this, "Clear all loaded source frames?", "Clear source",
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
    speedSlider.addChangeListener(event -> previewPanel.setDelay(speedSlider.getValue()));
    previewPanel.addPropertyChangeListener("frameStatus", event -> updatePreviewStatus());

    formatCombo.addActionListener(event -> updateFormatUi());
    splitCheck.addActionListener(event -> updateSourceUi());
    slotField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateSlotStatus));
    outputField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateSlotStatus));
    promptArea.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateDescriptionSummary));
    seedSpinner.addChangeListener(event -> updateDescriptionSummary());
    updateDescriptionSummary();
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
    if (busy || model.isEmpty()) {
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
        return CreatureAnimationImporter.exportDirectory(model, directory, true);
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
              "Animation " + String.format(Locale.ENGLISH, "0x%04X", config.getAnimationId()) + " was installed as "
                  + result.getInstalledFiles().size() + " validated resource(s) in:\n"
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

  private boolean showValidation() {
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
    return new Config().setGame(Profile.getGame()).setAnimationId(animationId).setResref(resrefField.getText())
        .setOutputDirectory(output).setBamFormat((BamFormat) formatCombo.getSelectedItem())
        .setCompressedBam(compressedCheck.isSelected()).setSplitBams(splitCheck.isSelected())
        .setCanLieDown(lieDownCheck.isSelected()).setDetectedByInfravision(infravisionCheck.isSelected())
        .setFalseColor(falseColorCheck.isSelected()).setPathSmooth(smoothPathCheck.isSelected())
        .setTranslucent(translucentCheck.isSelected()).setMoveScale((Integer) moveScaleSpinner.getValue())
        .setEllipse((Integer) ellipseSpinner.getValue()).setPersonalSpace((Integer) personalSpaceSpinner.getValue())
        .setBloodColor((Integer) bloodSpinner.getValue()).setChunkColor((Integer) chunksSpinner.getValue());
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
    final JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(title);
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    if (initial != null) {
      final Path directory = Files.isDirectory(initial) ? initial : initial.getParent();
      if (directory != null) {
        chooser.setCurrentDirectory(directory.toFile());
      }
    }
    return chooser.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION
        ? chooser.getSelectedFile().toPath().toAbsolutePath().normalize() : null;
  }

  private void setModel(CreatureAnimationModel model) {
    this.model = (model != null) ? model : new CreatureAnimationModel();
    previewPanel.setModel(this.model);
    updateSourceUi();
  }

  private void updateSourceUi() {
    final int cells = model.getPopulatedCellCount();
    final int requiredCells = Sequence.values().length * Direction.values().length;
    sourceStatusLabel.setText(model.isEmpty() ? "No source frames loaded"
        : model.getFrameCount() + " frames • " + cells + "/" + requiredCells + " action/direction cells");
    exportPngButton.setEnabled(!busy && !model.isEmpty());
    clearButton.setEnabled(!busy && !model.isEmpty());
    sequenceList.repaint();
    updatePreviewStatus();
  }

  private void updatePreviewStatus() {
    previewStatusLabel.setText(previewPanel.getStatusText());
  }

  private void updateDescriptionSummary() {
    final ProceduralCreatureGenerator.Description description = ProceduralCreatureGenerator.parseDescription(
        promptArea.getText(), ((Number) seedSpinner.getValue()).longValue());
    descriptionLabel.setText(description.getArchetype() + " • " + description.getTraits().toString().toLowerCase(
        Locale.ENGLISH) + " • scale " + String.format(Locale.ENGLISH, "%.2f", description.getScale()));
  }

  private void updateFormatUi() {
    final boolean bamV1 = formatCombo.getSelectedItem() == BamFormat.BAM_V1;
    compressedCheck.setEnabled(!busy && bamV1);
    falseColorCheck.setEnabled(!busy && bamV1);
    if (!bamV1) {
      falseColorCheck.setSelected(false);
    }
    final BamFormat format = (BamFormat) formatCombo.getSelectedItem();
    formatCombo.setToolTipText(format != null ? format.getDescription() : null);
  }

  private void updateSlotStatus() {
    try {
      final int slot = parseAnimationId(slotField.getText());
      if (!MonsterAnimationLayout.isValidSlot(Profile.getGame(), slot)) {
        slotStatusLabel.setForeground(new Color(190, 55, 45));
        slotStatusLabel.setText("Not a modern type 0x7000 monster slot for the active game");
      } else {
        final String fileName = String.format(Locale.ENGLISH, "%04X.INI", slot);
        final boolean occupied = ResourceFactory.resourceExists(fileName)
            || (!outputField.getText().trim().isEmpty() && Files.exists(Paths.get(outputField.getText().trim())
                .resolve(fileName)));
        slotStatusLabel.setForeground(occupied ? new Color(190, 118, 25) : new Color(47, 139, 72));
        slotStatusLabel.setText(occupied ? "Valid slot • an existing INI definition will require confirmation"
            : "Valid, currently unoccupied type 0x7000 slot");
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
    importButton.setEnabled(!busy);
    outputButton.setEnabled(!busy);
    validateButton.setEnabled(!busy);
    exportButton.setEnabled(!busy);
    formatCombo.setEnabled(!busy);
    splitCheck.setEnabled(!busy);
    progressBar.setIndeterminate(busy && indeterminate);
    if (!busy) {
      progressBar.setIndeterminate(false);
      progressBar.setValue(0);
    }
    if (text != null) {
      operationLabel.setText(text);
    }
    updateFormatUi();
    updateSourceUi();
  }

  private void showHelp() {
    final String text = "Professional offline scope\n"
        + "--------------------------\n"
        + "The built-in renderer actually draws a complete animation family from a description, but uses deterministic "
        + "parametric body plans. It cannot invent arbitrary production art like a large diffusion model. Its purpose "
        + "is coherent direction/action blocking that can be exported, painted over and imported again.\n\n"
        + "PNG source naming\n"
        + "-----------------\n"
        + "WK_S_000.png, WK/S/000.png and WK_S/000.png are accepted. Actions are WK, SC, SD, GH, DE, TW, SL, GU, "
        + "A1-A5, SP and CA. Store S, SSW, SW, WSW, W, WNW, NW, NNW and N; the engine mirrors the seven eastern "
        + "orientations. centers.csv preserves each frame's BAM pivot.\n\n"
        + "Export safety\n"
        + "-------------\n"
        + "The creator validates slot ranges, source coverage, centers, dimensions, palettes and filenames. It writes "
        + "to a staging directory, reopens the BAMs, checks cycle counts and PVRZ references, and only then installs "
        + "the full family. Existing primary resources are replaced only after confirmation and are restored if the "
        + "transaction fails.";
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

  private int findSuggestedSlot() {
    final Profile.Game game = Profile.getGame();
    for (int slot = 0x7000; slot <= 0x7fff; slot++) {
      if (MonsterAnimationLayout.isValidSlot(game, slot)
          && !ResourceFactory.resourceExists(String.format(Locale.ENGLISH, "%04X.INI", slot))) {
        return slot;
      }
    }
    return 0x7303;
  }

  private Path getDefaultOutputDirectory() {
    final List<Path> overrides = Profile.getOverrideFolders(false);
    if (!overrides.isEmpty()) {
      return overrides.get(0);
    }
    final Path root = Profile.getGameRoot();
    return (root != null) ? root.resolve(Profile.getOverrideFolderName().toLowerCase(Locale.ENGLISH)) : null;
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
        int directions = 0;
        int frames = 0;
        for (final Direction direction : Direction.values()) {
          if (model.hasFrames(sequence, direction)) {
            directions++;
            frames += model.getFrames(sequence, direction).size();
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
