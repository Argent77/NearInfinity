// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.infinity.gui.converter.creature.CreatureAnimationExporter.Severity;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.ValidationReport;
import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.OutputSlot;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.DxtEncoder;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.key.FileResourceEntry;

/** Validates, encodes and transactionally installs a type 0x7000 G1/G2 equipment-overlay pair. */
public final class EquipmentOverlayExporter {
  private static final Pattern RESREF = Pattern.compile("(?i)^[A-Z0-9_]{1,4}$");
  private static final Pattern APPEARANCE_CODE = Pattern.compile("(?i)^[A-Z0-9_]{2}$");

  public static final class Config {
    private String resref = "";
    private String appearanceCode = "";
    private Path outputDirectory;
    private BamFormat bamFormat = BamFormat.BAM_V1;
    private boolean compressedBam = true;

    public String getResref() {
      return resref;
    }

    public Config setResref(String value) {
      resref = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
      return this;
    }

    public String getAppearanceCode() {
      return appearanceCode;
    }

    public Config setAppearanceCode(String value) {
      appearanceCode = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
      return this;
    }

    public Path getOutputDirectory() {
      return outputDirectory;
    }

    public Config setOutputDirectory(Path value) {
      outputDirectory = value;
      return this;
    }

    public BamFormat getBamFormat() {
      return bamFormat;
    }

    public Config setBamFormat(BamFormat value) {
      bamFormat = value;
      return this;
    }

    public boolean isCompressedBam() {
      return compressedBam;
    }

    public Config setCompressedBam(boolean value) {
      compressedBam = value;
      return this;
    }
  }

  public static final class ExportResult {
    private final List<Path> installedFiles;
    private final ValidationReport report;

    private ExportResult(List<Path> installedFiles, ValidationReport report) {
      this.installedFiles = Collections.unmodifiableList(new ArrayList<>(installedFiles));
      this.report = report;
    }

    public List<Path> getInstalledFiles() {
      return installedFiles;
    }

    public ValidationReport getReport() {
      return report;
    }
  }

  private EquipmentOverlayExporter() {
  }

  public static ValidationReport validate(CreatureAnimationModel model, Config config) {
    final ValidationReport report = new ValidationReport();
    if (config == null) {
      report.add(Severity.ERROR, "No equipment-overlay export configuration was supplied.");
      return report;
    }
    if (!RESREF.matcher(config.resref).matches()) {
      report.add(Severity.ERROR, "The reference animation BAM resref must contain 1-4 ASCII letters, digits or "
          + "underscores.");
    }
    if (!APPEARANCE_CODE.matcher(config.appearanceCode).matches()) {
      report.add(Severity.ERROR, "The new equipped appearance code must contain exactly two ASCII letters, digits or "
          + "underscores.");
    }
    if (config.outputDirectory == null) {
      report.add(Severity.ERROR, "No output directory was selected.");
    } else if (Files.exists(config.outputDirectory) && !Files.isDirectory(config.outputDirectory)) {
      report.add(Severity.ERROR, "The selected output path is not a directory.");
    }
    if (config.bamFormat == null) {
      report.add(Severity.ERROR, "No BAM output format was selected.");
    }
    if (model == null || model.isEmpty()) {
      report.add(Severity.ERROR, "No generated equipment overlay is loaded.");
      return report;
    }

    int missingCells = 0;
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        final List<AnimationFrame> frames = model.getFrames(sequence, direction);
        if (frames.isEmpty()) {
          missingCells++;
        }
        for (final AnimationFrame frame : frames) {
          final BufferedImage image = frame.getImage();
          final Point center = frame.getCenter();
          if (image.getWidth() <= 0 || image.getWidth() > 65535 || image.getHeight() <= 0
              || image.getHeight() > 65535) {
            report.add(Severity.ERROR, "An equipment frame has dimensions outside the BAM range.");
          }
          if (center.x < Short.MIN_VALUE || center.x > Short.MAX_VALUE || center.y < Short.MIN_VALUE
              || center.y > Short.MAX_VALUE) {
            report.add(Severity.ERROR, "An equipment frame center is outside the signed 16-bit BAM range.");
          }
        }
      }
    }
    if (missingCells > 0) {
      report.add(Severity.ERROR, "The source equipment layer is missing " + missingCells
          + " synchronized action/direction cell(s); generating a partial overlay would desynchronize the avatar.");
    }
    if (config.bamFormat == BamFormat.BAM_V2 && config.compressedBam) {
      report.add(Severity.INFO, "BAMC compression applies only to BAM V1 and will be ignored for BAM V2.");
    }
    report.add(Severity.INFO, "Equip an ITM whose Equipped appearance field is " + config.appearanceCode
        + " to activate the generated overlay for " + config.resref + ".");
    return report;
  }

  public static List<Path> getExistingTargets(Config config) {
    if (config == null || config.outputDirectory == null) {
      return Collections.emptyList();
    }
    final List<Path> result = new ArrayList<>();
    for (final String suffix : MonsterAnimationLayout.getOutputLayout(false).keySet()) {
      final Path target = config.outputDirectory.resolve(getFileName(config, suffix));
      if (Files.exists(target)) {
        result.add(target);
      }
    }
    return result;
  }

  public static ExportResult export(CreatureAnimationModel model, Config config, boolean overwrite) throws Exception {
    final ValidationReport report = validate(model, config);
    if (report.hasErrors()) {
      throw new IllegalArgumentException("Equipment overlay validation failed: "
          + report.getMessages(Severity.ERROR).get(0).getText());
    }
    Files.createDirectories(config.outputDirectory);
    final List<Path> existing = getExistingTargets(config);
    if (!overwrite && !existing.isEmpty()) {
      throw new IOException("Export would overwrite " + existing.get(0) + ".");
    }

    final Path staging = Files.createTempDirectory(config.outputDirectory, ".ni-equipment-overlay-");
    boolean installed = false;
    try {
      int pvrzIndex = config.bamFormat == BamFormat.BAM_V2
          ? CreatureAnimationExporter.findPvrzStartIndex(config.outputDirectory) : 0;
      final Map<String, Integer> expectedCycles = new LinkedHashMap<>();
      for (final Map.Entry<String, List<OutputSlot>> entry :
          MonsterAnimationLayout.getOutputLayout(false).entrySet()) {
        final String fileName = getFileName(config, entry.getKey());
        final PseudoBamDecoder source = CreatureAnimationExporter.createBam(model, entry.getValue());
        expectedCycles.put(fileName, CreatureAnimationExporter.getRequiredCycleCount(entry.getValue()));
        try {
          if (config.bamFormat == BamFormat.BAM_V1) {
            final PseudoBamDecoder paletted = CreatureAnimationExporter.convertToPalettedBam(source);
            paletted.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX, 0);
            paletted.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, config.compressedBam);
            try {
              if (!paletted.exportBamV1(staging.resolve(fileName), null, 0)) {
                throw new IOException("BAM V1 encoder produced no output for " + fileName + ".");
              }
            } finally {
              if (paletted != source) {
                paletted.close();
              }
            }
          } else {
            if (!source.exportBamV2(staging.resolve(fileName), DxtEncoder.DxtType.DXT5, pvrzIndex, false, null, 0)) {
              throw new IOException("BAM V2 encoder produced no output for " + fileName + ".");
            }
            pvrzIndex = CreatureAnimationExporter.findPvrzStartIndex(staging);
          }
        } finally {
          source.close();
        }
      }

      validateStagedOutput(staging, config, expectedCycles);
      final List<Path> installedFiles =
          CreatureAnimationExporter.installStagedFiles(staging, config.outputDirectory, overwrite);
      installed = true;
      return new ExportResult(installedFiles, report);
    } finally {
      if (!installed || Files.exists(staging)) {
        CreatureAnimationExporter.deleteTree(staging);
      }
    }
  }

  private static void validateStagedOutput(Path staging, Config config, Map<String, Integer> expectedCycles)
      throws Exception {
    for (final Map.Entry<String, Integer> entry : expectedCycles.entrySet()) {
      final Path path = staging.resolve(entry.getKey());
      final FileResourceEntry resource = new FileResourceEntry(path);
      final BamDecoder.Type expectedType = config.bamFormat == BamFormat.BAM_V1
          ? (config.compressedBam ? BamDecoder.Type.BAMC : BamDecoder.Type.BAMV1) : BamDecoder.Type.BAMV2;
      final BamDecoder.Type actualType = BamDecoder.getType(resource);
      if (actualType != expectedType) {
        throw new IOException("Round-trip validation found " + actualType + " instead of " + expectedType + " in "
            + entry.getKey() + ".");
      }
      final BamDecoder decoder = BamDecoder.loadBam(resource);
      if (decoder == null || !decoder.isOpen()) {
        throw new IOException("Round-trip validation could not reopen " + entry.getKey() + ".");
      }
      try {
        if (decoder.createControl().cycleCount() != entry.getValue()) {
          throw new IOException("Round-trip validation found an unexpected cycle count in " + entry.getKey() + ".");
        }
      } finally {
        decoder.close();
      }
      if (config.bamFormat == BamFormat.BAM_V2) {
        CreatureAnimationExporter.validatePvrzReferences(staging, path);
      }
    }
  }

  private static String getFileName(Config config, String groupSuffix) {
    return config.resref + groupSuffix + config.appearanceCode + ".BAM";
  }
}
