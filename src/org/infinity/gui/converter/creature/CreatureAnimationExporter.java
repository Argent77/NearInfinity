// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.CreatureAnimationModel.ResolvedFrames;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.CyclePlan;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.ResourcePlan;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.OutputSlot;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamV1Decoder;
import org.infinity.resource.graphics.BamV1Decoder.BamV1Control;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.DxtEncoder;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamControl;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamFrameEntry;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;

/** Validates, encodes and transactionally installs Infinity Engine creature animation families. */
public final class CreatureAnimationExporter {
  private static final Pattern PVRZ_NAME = Pattern.compile("(?i)^MOS(\\d{4,5})\\.PVRZ$");
  private static final Pattern RESREF = Pattern.compile("(?i)^[A-Z0-9_]{1,8}$");

  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  public static final class Message {
    private final Severity severity;
    private final String text;

    private Message(Severity severity, String text) {
      this.severity = severity;
      this.text = text;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getText() {
      return text;
    }

    @Override
    public String toString() {
      return severity + ": " + text;
    }
  }

  public static final class ValidationReport {
    private final List<Message> messages = new ArrayList<>();

    public void add(Severity severity, String text) {
      messages.add(new Message(severity, text));
    }

    public List<Message> getMessages() {
      return Collections.unmodifiableList(messages);
    }

    public boolean hasErrors() {
      return messages.stream().anyMatch(message -> message.severity == Severity.ERROR);
    }

    public boolean hasWarnings() {
      return messages.stream().anyMatch(message -> message.severity == Severity.WARNING);
    }

    public List<Message> getMessages(Severity severity) {
      return messages.stream().filter(message -> message.severity == severity).collect(Collectors.toList());
    }
  }

  public static final class Config {
    private Profile.Game game = Profile.getGame();
    private CreatureAnimationFamily family = CreatureAnimationFamily.MONSTER;
    private int animationId = 0x7303;
    private String resref = "MNCR";
    private Path outputDirectory;
    private BamFormat bamFormat = BamFormat.BAM_V1;
    private boolean compressedBam = true;
    private boolean splitBams;
    private boolean canLieDown = true;
    private boolean detectedByInfravision = true;
    private boolean falseColor;
    private boolean pathSmooth = true;
    private boolean translucent;
    private int quadrants = CreatureAnimationFamily.MONSTER_QUADRANT.getDefaultQuadrants();
    private int armorLevels = CreatureAnimationFamily.CHARACTER.getDefaultArmorLevels();
    private int moveScale = 9;
    private int ellipse = 16;
    private int personalSpace = 3;
    private int bloodColor = 47;
    private int chunkColor = 255;
    private ClassicAnimationDefinition classicDefinition;

    public Profile.Game getGame() {
      return game;
    }

    public Config setGame(Profile.Game game) {
      this.game = game;
      return this;
    }

    public CreatureAnimationFamily getFamily() {
      return family;
    }

    public Config setFamily(CreatureAnimationFamily family) {
      this.family = family;
      return this;
    }

    public int getAnimationId() {
      return animationId;
    }

    public Config setAnimationId(int animationId) {
      this.animationId = animationId;
      return this;
    }

    public String getResref() {
      return resref;
    }

    public Config setResref(String resref) {
      this.resref = (resref != null) ? resref.trim().toUpperCase(Locale.ENGLISH) : "";
      return this;
    }

    public Path getOutputDirectory() {
      return outputDirectory;
    }

    public Config setOutputDirectory(Path outputDirectory) {
      this.outputDirectory = outputDirectory;
      return this;
    }

    public BamFormat getBamFormat() {
      return bamFormat;
    }

    public Config setBamFormat(BamFormat bamFormat) {
      this.bamFormat = bamFormat;
      return this;
    }

    public boolean isCompressedBam() {
      return compressedBam;
    }

    public Config setCompressedBam(boolean compressedBam) {
      this.compressedBam = compressedBam;
      return this;
    }

    public boolean isSplitBams() {
      return splitBams;
    }

    public Config setSplitBams(boolean splitBams) {
      this.splitBams = splitBams;
      return this;
    }

    public boolean isCanLieDown() {
      return canLieDown;
    }

    public Config setCanLieDown(boolean canLieDown) {
      this.canLieDown = canLieDown;
      return this;
    }

    public boolean isDetectedByInfravision() {
      return detectedByInfravision;
    }

    public Config setDetectedByInfravision(boolean detectedByInfravision) {
      this.detectedByInfravision = detectedByInfravision;
      return this;
    }

    public boolean isFalseColor() {
      return falseColor;
    }

    public Config setFalseColor(boolean falseColor) {
      this.falseColor = falseColor;
      return this;
    }

    public boolean isPathSmooth() {
      return pathSmooth;
    }

    public Config setPathSmooth(boolean pathSmooth) {
      this.pathSmooth = pathSmooth;
      return this;
    }

    public boolean isTranslucent() {
      return translucent;
    }

    public Config setTranslucent(boolean translucent) {
      this.translucent = translucent;
      return this;
    }

    public int getQuadrants() {
      return quadrants;
    }

    public Config setQuadrants(int quadrants) {
      this.quadrants = quadrants;
      return this;
    }

    public int getArmorLevels() {
      return armorLevels;
    }

    public Config setArmorLevels(int armorLevels) {
      this.armorLevels = armorLevels;
      return this;
    }

    public int getMoveScale() {
      return moveScale;
    }

    public Config setMoveScale(int moveScale) {
      this.moveScale = moveScale;
      return this;
    }

    public int getEllipse() {
      return ellipse;
    }

    public Config setEllipse(int ellipse) {
      this.ellipse = ellipse;
      return this;
    }

    public int getPersonalSpace() {
      return personalSpace;
    }

    public Config setPersonalSpace(int personalSpace) {
      this.personalSpace = personalSpace;
      return this;
    }

    public int getBloodColor() {
      return bloodColor;
    }

    public Config setBloodColor(int bloodColor) {
      this.bloodColor = bloodColor;
      return this;
    }

    public int getChunkColor() {
      return chunkColor;
    }

    public Config setChunkColor(int chunkColor) {
      this.chunkColor = chunkColor;
      return this;
    }

    public ClassicAnimationDefinition getClassicDefinition() {
      return classicDefinition;
    }

    public Config setClassicDefinition(ClassicAnimationDefinition classicDefinition) {
      this.classicDefinition = classicDefinition;
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

  private CreatureAnimationExporter() {
  }

  public static ValidationReport validate(CreatureAnimationModel model, Config config) {
    final ValidationReport report = new ValidationReport();
    if (config == null) {
      report.add(Severity.ERROR, "No export configuration was supplied.");
      return report;
    }
    if (config.family == null) {
      report.add(Severity.ERROR, "No creature animation family was selected.");
    } else if (!MonsterAnimationLayout.isSupportedGame(config.game)) {
      report.add(Severity.ERROR, "Creature Animation Creator requires a recognized Infinity Engine game profile.");
    } else if (!config.family.isSupportedGame(config.game)) {
      report.add(Severity.ERROR, config.family + " is not supported by " + config.game.getTitle() + ".");
    } else if (!config.family.isValidSlot(config.game, config.animationId)) {
      report.add(Severity.ERROR, String.format(Locale.ENGLISH,
          "Animation slot 0x%04X does not belong to the %s family for %s.", config.animationId, config.family,
          config.game.getTitle()));
    }
    final boolean classic = isClassicProfile(config);
    if (classic && (config.classicDefinition == null
        || !config.classicDefinition.matches(config.game, config.animationId, config.family))) {
      report.add(Severity.ERROR, "Classic export requires the exact hardcoded or Infinity Animations definition "
          + "resolved from the active game.");
    } else if (classic && !config.classicDefinition.getResref().equals(config.resref)) {
      report.add(Severity.ERROR, "Classic export resref must match the exact profile definition.");
    } else if (classic && config.falseColor != config.classicDefinition.isFalseColor()) {
      report.add(Severity.ERROR, "Classic false-color behavior must match the exact profile definition.");
    } else if (!classic && config.classicDefinition != null) {
      report.add(Severity.ERROR, "A classic profile definition cannot be used for an Enhanced Edition export.");
    }
    if (!classic && !RESREF.matcher(config.resref).matches()) {
      report.add(Severity.ERROR, "The BAM resref must contain 1-8 ASCII letters, digits or underscores.");
    }
    if (config.outputDirectory == null) {
      report.add(Severity.ERROR, "No output directory was selected.");
    } else if (Files.exists(config.outputDirectory) && !Files.isDirectory(config.outputDirectory)) {
      report.add(Severity.ERROR, "The selected output path is not a directory.");
    }
    if (config.bamFormat == null) {
      report.add(Severity.ERROR, "No BAM output format was selected.");
    } else if (config.bamFormat == BamFormat.BAM_V2 && !Profile.isBamV2Supported(config.game)) {
      report.add(Severity.ERROR, getGameTitle(config.game) + " does not support BAM V2/PVRZ resources.");
    } else if (config.bamFormat == BamFormat.BAM_V1 && config.compressedBam
        && !Profile.isBamcSupported(config.game)) {
      report.add(Severity.ERROR,
          getGameTitle(config.game) + " does not support BAMC-compressed BAM V1 resources.");
    }
    if (!classic) {
      validateRange(report, "Movement scale", config.moveScale, 0, 255);
      validateRange(report, "Selection ellipse", config.ellipse, 0, 255);
      validateRange(report, "Personal space", config.personalSpace, 0, 255);
      validateRange(report, "Blood color", config.bloodColor, 0, 255);
      validateRange(report, "Chunk color", config.chunkColor, 0, 255);
    }

    FamilyLayout layout = null;
    if (config.family != null) {
      if (!classic && config.family.getSplitMode() == CreatureAnimationFamily.SplitMode.NONE && config.splitBams) {
        report.add(Severity.ERROR, config.family + " does not use a selectable split BAM layout.");
      } else if (!classic && config.family.isSplitBamsRequired() && !config.splitBams) {
        report.add(Severity.ERROR, config.family + " requires the verified split BAM layout.");
      }
      if (!classic && config.family.hasQuadrants()) {
        validateRange(report, "Quadrants", config.quadrants, 1, 9);
      }
      if (!classic && config.family.hasArmorLevels()) {
        validateRange(report, "Armor levels", config.armorLevels, 1, 4);
      }
      if (!classic && config.falseColor && !config.family.isFalseColorSupported()) {
        report.add(Severity.ERROR, config.family + " has no engine-supported false-color definition property.");
      }
      if (!classic && config.translucent && !config.family.isTranslucencySupported()) {
        report.add(Severity.ERROR, config.family + " has no engine-supported translucency definition property.");
      }
      final boolean splitParameterValid = classic
          || (config.family.getSplitMode() != CreatureAnimationFamily.SplitMode.NONE || !config.splitBams)
              && (!config.family.isSplitBamsRequired() || config.splitBams);
      final boolean layoutParametersValid = classic || (!config.family.hasQuadrants()
          || (config.quadrants >= 1 && config.quadrants <= 9))
          && (!config.family.hasArmorLevels()
              || (config.armorLevels >= 1 && config.armorLevels <= 4));
      if (splitParameterValid && layoutParametersValid && (classic || RESREF.matcher(config.resref).matches())) {
        try {
          layout = getLayout(config);
          boolean resourceNameErrorReported = false;
          if (!classic) {
            final int maximumResrefLength =
                config.family.getMaximumResrefLength(config.splitBams, config.quadrants, config.armorLevels);
            if (config.resref.length() > maximumResrefLength) {
              report.add(Severity.ERROR, config.family + " reserves more of the eight-character resource name in this "
                  + "layout; its base resref may contain at most " + maximumResrefLength + " characters.");
            } else if (config.family.requiresExactResrefLength()
                && config.resref.length() != maximumResrefLength) {
              report.add(Severity.ERROR, config.family
                  + " uses the documented four-character character/race/gender/class base resref.");
            }
            resourceNameErrorReported = config.resref.length() > maximumResrefLength;
          }
          for (final ResourcePlan resource : layout.getResources().values()) {
            final String baseName = resource.getFileName().substring(0, resource.getFileName().length() - 4);
            if (baseName.length() > 8 && !resourceNameErrorReported) {
              report.add(Severity.ERROR, "Generated BAM resref " + baseName + " exceeds the engine's "
                  + "eight-character resource-name limit.");
              resourceNameErrorReported = true;
            }
            if (resource.getCycleCount() <= 0) {
              report.add(Severity.ERROR, resource.getFileName() + " has no animation cycles.");
            }
            if (classic) {
              validateClassicResource(report, config, resource);
            }
          }
        } catch (RuntimeException e) {
          report.add(Severity.ERROR, "Could not construct the selected family layout: " + e.getMessage());
        }
      }
      if (!classic && config.family == CreatureAnimationFamily.MONSTER_MULTI) {
        report.add(Severity.INFO, "Custom monster_multi definitions use split_bams=0 because the Enhanced Edition "
            + "engine's split implementation is not reliable for new slots.");
      }
      if (classic && config.classicDefinition != null) {
        report.add(Severity.INFO, "Classic export replaces only the exact installed profile BAM graph; no animation "
            + "INI is generated or modified.");
      } else if (config.family.hasQuadrants()) {
        report.add(Severity.INFO, "Frames will be divided into " + config.quadrants
            + " synchronized spatial quadrant resource(s).");
      }
      if (!classic && config.family.hasArmorLevels() && config.armorLevels > 1) {
        report.add(Severity.INFO, "The neutral source is encoded independently for armor codes 1-"
            + config.armorLevels + "; exported PNGs can be refined per generated BAM afterward.");
      }
      if (!classic && config.family == CreatureAnimationFamily.MONSTER_PLANESCAPE) {
        report.add(Severity.INFO, "PST misc1-misc20 slots are custom by definition and use stable action fallbacks "
            + "until their generated BAMs are replaced with artist-specific sequences.");
      }
    }

    if (model == null || model.isEmpty()) {
      report.add(Severity.ERROR, "No animation source frames are loaded.");
      return report;
    }

    int missingCells = 0;
    final Set<String> requiredCells = new LinkedHashSet<>();
    if (layout != null) {
      for (final ResourcePlan resource : layout.getResources().values()) {
        for (final CyclePlan cycle : resource.getCycles()) {
          final Sequence sequence = cycle.getSequence();
          final Direction direction = cycle.getSourceDirection();
          final String key = sequence.name() + "/" + direction.name();
          if (!requiredCells.add(key)) {
            continue;
          }
          if (!model.hasFrames(sequence, direction)) {
            missingCells++;
          }
          final ResolvedFrames resolved = model.resolveFrames(sequence, direction);
          if (resolved.getFrames().isEmpty()) {
            report.add(Severity.ERROR, "No source fallback exists for " + sequence.getCode() + "/"
                + direction.getCode() + ".");
          }
        }
      }
    } else {
      for (final Sequence sequence : Sequence.values()) {
        for (final Direction direction : Direction.values()) {
          if (!model.hasFrames(sequence, direction)) {
            missingCells++;
          }
          final ResolvedFrames resolved = model.resolveFrames(sequence, direction);
          if (resolved.getFrames().isEmpty()) {
            report.add(Severity.ERROR, "No source fallback exists for " + sequence.getCode() + "/"
                + direction.getCode() + ".");
          }
        }
      }
    }
    if (missingCells > 0) {
      report.add(Severity.WARNING, missingCells + " action/direction cell(s) are missing and will use deterministic "
          + "nearest-action or nearest-direction fallbacks.");
    }

    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        for (final AnimationFrame frame : model.getFrames(sequence, direction)) {
          final BufferedImage image = frame.getImage();
          final Point center = frame.getCenter();
          if (image.getWidth() <= 0 || image.getWidth() > 65535 || image.getHeight() <= 0
              || image.getHeight() > 65535) {
            report.add(Severity.ERROR, "A " + sequence.getCode() + "/" + direction.getCode()
                + " frame has dimensions outside the BAM range.");
          }
          if (center.x < Short.MIN_VALUE || center.x > Short.MAX_VALUE || center.y < Short.MIN_VALUE
              || center.y > Short.MAX_VALUE) {
            report.add(Severity.ERROR, "A " + sequence.getCode() + "/" + direction.getCode()
                + " frame center is outside the signed 16-bit BAM range.");
          }
        }
      }
    }

    if (layout != null) {
      final Set<String> reportedGeometry = new LinkedHashSet<>();
      for (final ResourcePlan resource : layout.getResources().values()) {
        for (final CyclePlan cycle : resource.getCycles()) {
          final ResolvedFrames resolved = model.resolveFrames(cycle.getSequence(), cycle.getSourceDirection());
          for (final AnimationFrame frame : resolved.getFrames()) {
            int width = frame.getImage().getWidth();
            int height = frame.getImage().getHeight();
            int centerX = cycle.isMirrored() ? width - 1 - frame.getCenter().x : frame.getCenter().x;
            int centerY = frame.getCenter().y;
            if (cycle.getQuadrantCount() > 0) {
              final int[] bounds =
                  getQuadrantBounds(width, height, cycle.getQuadrantIndex(), cycle.getQuadrantCount());
              width = bounds[2];
              height = bounds[3];
              centerX -= bounds[0];
              centerY -= bounds[1];
            }
            if (width <= 0 || height <= 0) {
              final String key = resource.getFileName() + "/size";
              if (reportedGeometry.add(key)) {
                report.add(Severity.ERROR, resource.getFileName()
                    + " would contain a zero-sized quadrant. Use larger source frames or fewer quadrants.");
              }
            }
            if (centerX < Short.MIN_VALUE || centerX > Short.MAX_VALUE || centerY < Short.MIN_VALUE
                || centerY > Short.MAX_VALUE) {
              final String key = resource.getFileName() + "/center";
              if (reportedGeometry.add(key)) {
                report.add(Severity.ERROR, resource.getFileName()
                    + " would contain a transformed center outside the signed 16-bit BAM range.");
              }
            }
          }
        }
      }
    }

    if (config.falseColor && !classic && !hasCommonIndexedPalette(model)) {
      report.add(Severity.ERROR, "False-color output requires every source frame to use the same indexed palette "
          + "with transparent palette index 0. Truecolor or independently paletted frames cannot preserve Infinity "
          + "Engine false-color ranges.");
    }
    if (config.bamFormat == BamFormat.BAM_V2 && config.falseColor) {
      report.add(Severity.ERROR, "False-color replacement requires palette-based BAM V1 output.");
    }
    if (config.bamFormat == BamFormat.BAM_V2 && config.compressedBam) {
      report.add(Severity.INFO, "BAMC compression applies only to BAM V1 and will be ignored for BAM V2.");
    }
    if (classic && config.falseColor && layout != null && !classicPalettesMatch(model, config, layout)) {
      report.add(Severity.ERROR, "Classic false-color replacement requires every source frame to use the same indexed "
          + "palette, transparency index and palette-index colors as every installed target BAM.");
    }
    return report;
  }

  public static List<Path> getExistingPrimaryTargets(Config config) {
    if (config == null || config.family == null || config.outputDirectory == null || config.resref == null) {
      return Collections.emptyList();
    }
    final List<Path> existing = new ArrayList<>();
    if (!isClassicProfile(config)) {
      final Path ini = config.outputDirectory.resolve(getIniFileName(config.animationId));
      if (Files.exists(ini)) {
        existing.add(ini);
      }
    }
    final FamilyLayout layout = getLayout(config);
    for (final String fileName : layout.getResources().keySet()) {
      final Path bam = config.outputDirectory.resolve(fileName);
      if (Files.exists(bam)) {
        existing.add(bam);
      }
    }
    return existing;
  }

  public static ExportResult export(CreatureAnimationModel model, Config config, boolean overwrite) throws Exception {
    final ValidationReport report = validate(model, config);
    if (report.hasErrors()) {
      throw new IllegalArgumentException("Creature animation validation failed: "
          + report.getMessages(Severity.ERROR).get(0).getText());
    }

    Files.createDirectories(config.outputDirectory);
    final List<Path> existing = getExistingPrimaryTargets(config);
    if (!overwrite && !existing.isEmpty()) {
      throw new IOException("Export would overwrite " + existing.get(0) + ".");
    }

    final Path staging = Files.createTempDirectory(config.outputDirectory, ".ni-creature-animation-");
    boolean installed = false;
    try {
      final FamilyLayout layout = getLayout(config);
      int pvrzIndex = (config.bamFormat == BamFormat.BAM_V2)
          ? findPvrzStartIndex(config.outputDirectory) : 0;
      final Map<String, Integer> expectedCycles = new LinkedHashMap<>();

      for (final ResourcePlan resource : layout.getResources().values()) {
        final String fileName = resource.getFileName();
        final BamV1Decoder installedBam = resource.isPreserveExistingCycles()
            ? loadClassicSource(config, fileName) : null;
        final PseudoBamDecoder source = installedBam != null ? createBam(model, resource, installedBam)
            : createBam(model, resource);
        expectedCycles.put(fileName, source.createControl().cycleCount());
        try {
          if (config.bamFormat == BamFormat.BAM_V1) {
            final BamV1Control installedControl = installedBam != null ? installedBam.createControl() : null;
            final PseudoBamDecoder paletted = installedControl != null
                ? convertToPalettedBam(source, installedControl.getPalette(), installedBam.getRleIndex())
                : convertToPalettedBam(source);
            paletted.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX,
                installedBam != null ? installedBam.getRleIndex() : 0);
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
            pvrzIndex = findPvrzStartIndex(staging);
          }
        } finally {
          source.close();
          if (installedBam != null) {
            installedBam.close();
          }
        }
      }

      if (!isClassicProfile(config)) {
        Files.write(staging.resolve(getIniFileName(config.animationId)),
            createIniText(config, layout).getBytes(StandardCharsets.UTF_8));
      }
      validateStagedOutput(staging, config, layout, expectedCycles);
      final List<Path> installedFiles = installStagedFiles(staging, config.outputDirectory, overwrite);
      installed = true;
      return new ExportResult(installedFiles, report);
    } finally {
      if (!installed || Files.exists(staging)) {
        deleteTree(staging);
      }
    }
  }

  public static String createIniText(Config config) {
    if (isClassicProfile(config)) {
      throw new IllegalArgumentException("Classic profile exports do not generate animation INI definitions.");
    }
    final FamilyLayout layout =
        config.family.createLayout(config.resref, config.splitBams, config.quadrants, config.armorLevels);
    return createIniText(config, layout);
  }

  private static boolean isClassicProfile(Config config) {
    return config != null && MonsterAnimationLayout.isSupportedGame(config.game)
        && !Profile.isEnhancedEdition(config.game);
  }

  private static String getGameTitle(Profile.Game game) {
    return game != null ? game.getTitle() : "The selected game profile";
  }

  private static FamilyLayout getLayout(Config config) {
    if (isClassicProfile(config)) {
      if (config.classicDefinition == null
          || !config.classicDefinition.matches(config.game, config.animationId, config.family)) {
        throw new IllegalArgumentException("No matching exact classic profile definition is available.");
      }
      return config.classicDefinition.getLayout();
    }
    return config.family.createLayout(config.resref, config.splitBams, config.quadrants, config.armorLevels);
  }

  private static ResourceEntry getClassicSourceEntry(Config config, String fileName) {
    final Path output = config.outputDirectory != null ? config.outputDirectory.resolve(fileName) : null;
    if (output != null && Files.isRegularFile(output)) {
      return new FileResourceEntry(output);
    }
    return ResourceFactory.getResourceEntry(fileName);
  }

  private static BamV1Decoder loadClassicSource(Config config, String fileName) throws IOException {
    final ResourceEntry entry = getClassicSourceEntry(config, fileName);
    if (entry == null) {
      throw new IOException("The installed classic resource " + fileName + " is unavailable.");
    }
    final BamDecoder decoder = BamDecoder.loadBam(entry);
    if (!(decoder instanceof BamV1Decoder) || !decoder.isOpen()) {
      if (decoder != null) {
        decoder.close();
      }
      throw new IOException(fileName + " is not a readable BAM V1/BAMC resource.");
    }
    return (BamV1Decoder) decoder;
  }

  private static void validateClassicResource(ValidationReport report, Config config, ResourcePlan resource) {
    final ResourceEntry entry = getClassicSourceEntry(config, resource.getFileName());
    if (entry == null) {
      report.add(Severity.ERROR, "Installed classic resource " + resource.getFileName() + " is unavailable.");
      return;
    }
    final BamDecoder.Type type = BamDecoder.getType(entry);
    if (type != BamDecoder.Type.BAMV1 && type != BamDecoder.Type.BAMC) {
      report.add(Severity.ERROR, resource.getFileName() + " is not an installed BAM V1/BAMC resource.");
    }
    if (!resource.isPreserveExistingCycles()) {
      report.add(Severity.ERROR, resource.getFileName() + " is not marked for lossless classic-cycle preservation.");
    }
    final BamDecoder decoder = BamDecoder.loadBam(entry);
    if (!(decoder instanceof BamV1Decoder) || !decoder.isOpen()) {
      if (decoder != null) {
        decoder.close();
      }
      return;
    }
    try {
      final int cycleCount = decoder.createControl().cycleCount();
      for (final CyclePlan cycle : resource.getCycles()) {
        if (cycle.getCycleIndex() < 0 || cycle.getCycleIndex() >= cycleCount) {
          report.add(Severity.ERROR, resource.getFileName() + " does not contain exact profile cycle "
              + cycle.getCycleIndex() + ".");
        }
      }
    } finally {
      decoder.close();
    }
  }

  private static String createIniText(Config config, FamilyLayout layout) {
    final StringBuilder result = new StringBuilder(1024);
    result.append("[general]\n");
    result.append(String.format(Locale.ENGLISH, "animation_type=%04X%n",
        config.family.getAnimationTypeCode(config.animationId)));
    result.append("move_scale=").append(config.moveScale).append('\n');
    result.append("ellipse=").append(config.ellipse).append('\n');
    result.append("color_blood=").append(config.bloodColor).append('\n');
    result.append("color_chunks=").append(config.chunkColor).append('\n');
    result.append("personal_space=").append(config.personalSpace).append("\n\n");

    result.append('[').append(config.family.getSectionName()).append("]\n");
    switch (config.family) {
      case EFFECT:
        appendResref(result, config);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "translucent", config.translucent);
        appendBoolean(result, "random_render", false);
        break;
      case MONSTER_QUADRANT:
        appendResref(result, config);
        appendBoolean(result, "caster", false);
        appendBoolean(result, "extend_direction", true);
        result.append("extend_direction_test=9\n");
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "path_smooth", config.pathSmooth);
        result.append("quadrants=").append(config.quadrants).append('\n');
        break;
      case MONSTER_MULTI:
        appendResref(result, config);
        result.append("quadrants=").append(config.quadrants).append('\n');
        appendBoolean(result, "split_bams", false);
        break;
      case MONSTER_MULTI_NEW:
        appendResref(result, config);
        appendBoolean(result, "can_lie_down", config.canLieDown);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "path_smooth", config.pathSmooth);
        result.append("quadrants=").append(config.quadrants).append('\n');
        appendBoolean(result, "split_bams", config.splitBams);
        appendBoolean(result, "translucent", config.translucent);
        break;
      case MONSTER_LAYERED_SPELL:
        appendResref(result, config);
        appendBoolean(result, "dual_attack", false);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "invulnerable", false);
        break;
      case MONSTER_ANKHEG:
        appendResref(result, config);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "mirror", true);
        appendBoolean(result, "extend_direction", true);
        break;
      case TOWN_STATIC:
        appendResref(result, config);
        appendBoolean(result, "can_lie_down", config.canLieDown);
        appendBoolean(result, "false_color", config.falseColor);
        break;
      case CHARACTER:
        appendResref(result, config);
        result.append("armor_max_code=").append(config.armorLevels).append('\n');
        appendBoolean(result, "can_lie_down", config.canLieDown);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "equip_helmet", false);
        appendBoolean(result, "split_bams", true);
        appendBoolean(result, "false_color", config.falseColor);
        break;
      case CHARACTER_OLD:
        appendResref(result, config);
        result.append("armor_max_code=").append(config.armorLevels).append('\n');
        appendBoolean(result, "can_lie_down", config.canLieDown);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "equip_helmet", false);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "hide_weapons", true);
        break;
      case MONSTER:
        appendResref(result, config);
        appendBoolean(result, "can_lie_down", config.canLieDown);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "path_smooth", config.pathSmooth);
        appendBoolean(result, "split_bams", config.splitBams);
        appendBoolean(result, "translucent", config.translucent);
        break;
      case MONSTER_OLD:
        appendResref(result, config);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "translucent", config.translucent);
        break;
      case MONSTER_LAYERED:
        appendResref(result, config);
        appendBoolean(result, "caster", false);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "false_color", config.falseColor);
        break;
      case MONSTER_LARGE:
      case MONSTER_LARGE_16:
        appendResref(result, config);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        appendBoolean(result, "false_color", config.falseColor);
        break;
      case AMBIENT_STATIC:
        appendResref(result, config);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "invulnerable", false);
        break;
      case AMBIENT:
        appendResref(result, config);
        appendBoolean(result, "false_color", config.falseColor);
        appendBoolean(result, "invulnerable", false);
        result.append("list_type=0\n");
        appendBoolean(result, "path_smooth", config.pathSmooth);
        break;
      case FLYING:
        appendResref(result, config);
        appendBoolean(result, "false_color", config.falseColor);
        break;
      case MONSTER_ICEWIND:
        appendResref(result, config);
        appendBoolean(result, "weapon_left_hand", false);
        appendBoolean(result, "translucent", config.translucent);
        appendBoolean(result, "detected_by_infravision", config.detectedByInfravision);
        break;
      case MONSTER_PLANESCAPE:
        appendBoolean(result, "clown", config.falseColor);
        if (config.falseColor) {
          for (int index = 0; index < 7; index++) {
            result.append("color").append(index + 1).append('=').append(128 + index * 16).append('\n');
          }
        }
        result.append("walkscale=").append(config.moveScale).append('\n');
        result.append("runscale=").append(config.moveScale).append('\n');
        for (final Map.Entry<String, String> action : layout.getActionResrefs().entrySet()) {
          result.append(action.getKey()).append('=').append(action.getValue()).append('\n');
        }
        break;
      default:
        throw new IllegalStateException("Unsupported animation family: " + config.family);
    }
    return result.toString();
  }

  private static void appendResref(StringBuilder result, Config config) {
    result.append("resref=").append(config.resref).append('\n');
  }

  private static void appendBoolean(StringBuilder result, String name, boolean value) {
    result.append(name).append('=').append(value ? 1 : 0).append('\n');
  }

  static PseudoBamDecoder createBam(CreatureAnimationModel model, List<OutputSlot> slots) {
    final PseudoBamDecoder decoder = new PseudoBamDecoder();
    final PseudoBamControl control = decoder.createControl();
    final Map<Integer, Sequence> cycles = new HashMap<>();
    for (final OutputSlot slot : slots) {
      cycles.put(slot.getCycleOffset(), slot.getSequence());
    }

    final int cycleCount = getRequiredCycleCount(slots);
    final IdentityHashMap<AnimationFrame, Integer> frameIndices = new IdentityHashMap<>();
    for (int cycleIndex = 0; cycleIndex < cycleCount; cycleIndex++) {
      Sequence sequence = null;
      Direction direction = null;
      for (final Map.Entry<Integer, Sequence> entry : cycles.entrySet()) {
        final int relative = cycleIndex - entry.getKey();
        if (relative >= 0 && relative < Direction.values().length) {
          sequence = entry.getValue();
          direction = Direction.values()[relative];
          break;
        }
      }

      if (sequence == null) {
        control.cycleAdd();
        continue;
      }
      final ResolvedFrames resolved = model.resolveFrames(sequence, direction);
      final int[] indices = new int[resolved.getFrames().size()];
      for (int i = 0; i < resolved.getFrames().size(); i++) {
        final AnimationFrame frame = resolved.getFrames().get(i);
        Integer frameIndex = frameIndices.get(frame);
        if (frameIndex == null) {
          frameIndex = decoder.frameAdd(frame.getImage(), frame.getCenter());
          frameIndices.put(frame, frameIndex);
        }
        indices[i] = frameIndex;
      }
      control.cycleAdd(indices);
    }
    return decoder;
  }

  static PseudoBamDecoder createBam(CreatureAnimationModel model, ResourcePlan resource) {
    final PseudoBamDecoder decoder = new PseudoBamDecoder();
    final PseudoBamControl control = decoder.createControl();
    final Map<Integer, CyclePlan> cycles = new HashMap<>();
    for (final CyclePlan cycle : resource.getCycles()) {
      cycles.put(cycle.getCycleIndex(), cycle);
    }

    final IdentityHashMap<AnimationFrame, Map<Integer, Integer>> frameIndices = new IdentityHashMap<>();
    for (int cycleIndex = 0; cycleIndex < resource.getCycleCount(); cycleIndex++) {
      final CyclePlan cycle = cycles.get(cycleIndex);
      if (cycle == null) {
        control.cycleAdd();
        continue;
      }

      final ResolvedFrames resolved = model.resolveFrames(cycle.getSequence(), cycle.getSourceDirection());
      if (resolved.getFrames().isEmpty()) {
        decoder.close();
        throw new IllegalArgumentException("No source frames resolve for " + cycle.getSequence().getCode() + "/"
            + cycle.getSourceDirection().getCode() + ".");
      }

      final int[] indices = new int[resolved.getFrames().size()];
      for (int targetIndex = 0; targetIndex < resolved.getFrames().size(); targetIndex++) {
        final int sourceIndex = cycle.isReversed() ? resolved.getFrames().size() - 1 - targetIndex : targetIndex;
        final AnimationFrame frame = resolved.getFrames().get(sourceIndex);
        final int transformKey = getTransformKey(cycle);
        final Map<Integer, Integer> transformedIndices =
            frameIndices.computeIfAbsent(frame, key -> new HashMap<Integer, Integer>());
        Integer frameIndex = transformedIndices.get(transformKey);
        if (frameIndex == null) {
          final TransformedFrame transformed = transformFrame(frame, cycle);
          frameIndex = decoder.frameAdd(transformed.image, transformed.center);
          transformedIndices.put(transformKey, frameIndex);
        }
        indices[targetIndex] = frameIndex;
      }
      control.cycleAdd(indices);
    }
    return decoder;
  }

  /**
   * Builds a classic BAM replacement while retaining every installed frame and cycle outside the exact replacement
   * plan. Source palette indices, frame sharing and centers are copied before selected cycles are regenerated.
   */
  static PseudoBamDecoder createBam(CreatureAnimationModel model, ResourcePlan resource, BamV1Decoder installed) {
    if (installed == null || !installed.isOpen()) {
      throw new IllegalArgumentException("A readable installed BAM V1 resource is required.");
    }
    final PseudoBamDecoder decoder = new PseudoBamDecoder();
    final PseudoBamControl control = decoder.createControl();
    final BamV1Control installedControl = installed.createControl();
    final Map<Integer, CyclePlan> cycles = new HashMap<>();
    for (final CyclePlan cycle : resource.getCycles()) {
      cycles.put(cycle.getCycleIndex(), cycle);
    }

    if (resource.getCycleCount() > installedControl.cycleCount()) {
      decoder.close();
      throw new IllegalArgumentException(resource.getFileName()
          + " does not contain every planned classic profile cycle.");
    }
    final int cycleCount = installedControl.cycleCount();
    final IdentityHashMap<AnimationFrame, Map<Integer, Integer>> generatedFrames = new IdentityHashMap<>();
    for (int absoluteIndex = 0; absoluteIndex < installed.frameCount(); absoluteIndex++) {
      final int copiedIndex = copyInstalledFrame(decoder, installed, installedControl, absoluteIndex);
      if (copiedIndex != absoluteIndex) {
        decoder.close();
        throw new IllegalStateException("Installed classic BAM frame order could not be preserved.");
      }
    }
    for (int cycleIndex = 0; cycleIndex < cycleCount; cycleIndex++) {
      final CyclePlan cycle = cycles.get(cycleIndex);
      if (cycle == null) {
        if (cycleIndex >= installedControl.cycleCount() || !installedControl.cycleSet(cycleIndex)) {
          control.cycleAdd();
          continue;
        }
        final int[] indices = new int[installedControl.cycleFrameCount()];
        for (int frameIndex = 0; frameIndex < indices.length; frameIndex++) {
          final int absolute = installedControl.cycleGetFrameIndexAbsolute(frameIndex);
          if (absolute < 0 || absolute >= installed.frameCount()) {
            decoder.close();
            throw new IllegalArgumentException("Installed BAM contains an invalid frame lookup index.");
          }
          indices[frameIndex] = absolute;
        }
        control.cycleAdd(indices);
        continue;
      }

      final ResolvedFrames resolved = model.resolveFrames(cycle.getSequence(), cycle.getSourceDirection());
      if (resolved.getFrames().isEmpty()) {
        decoder.close();
        throw new IllegalArgumentException("No source frames resolve for " + cycle.getSequence().getCode() + "/"
            + cycle.getSourceDirection().getCode() + ".");
      }
      final int[] indices = new int[resolved.getFrames().size()];
      for (int targetIndex = 0; targetIndex < resolved.getFrames().size(); targetIndex++) {
        final int sourceIndex = cycle.isReversed() ? resolved.getFrames().size() - 1 - targetIndex : targetIndex;
        final AnimationFrame frame = resolved.getFrames().get(sourceIndex);
        final int transformKey = getTransformKey(cycle);
        final Map<Integer, Integer> transformed =
            generatedFrames.computeIfAbsent(frame, key -> new HashMap<Integer, Integer>());
        Integer frameIndex = transformed.get(transformKey);
        if (frameIndex == null) {
          final TransformedFrame generated = transformFrame(frame, cycle);
          frameIndex = decoder.frameAdd(generated.image, generated.center);
          transformed.put(transformKey, frameIndex);
        }
        indices[targetIndex] = frameIndex;
      }
      control.cycleAdd(indices);
    }
    return decoder;
  }

  private static int copyInstalledFrame(PseudoBamDecoder target, BamV1Decoder source, BamV1Control sourceControl,
      int absoluteIndex) {
    if (absoluteIndex < 0) {
      throw new IllegalArgumentException("Installed BAM contains an invalid frame lookup index.");
    }
    final BamDecoder.FrameEntry info = source.getFrameInfo(absoluteIndex);
    if (info == null || info.getWidth() <= 0 || info.getHeight() <= 0) {
      throw new IllegalArgumentException("Installed BAM contains an invalid frame entry.");
    }
    final int[] palette = sourceControl.getPalette().clone();
    final int transparencyIndex = source.getRleIndex();
    final IndexColorModel colorModel = new IndexColorModel(8, palette.length, palette, 0, true,
        transparencyIndex, DataBuffer.TYPE_BYTE);
    final BufferedImage image = new BufferedImage(info.getWidth(), info.getHeight(), BufferedImage.TYPE_BYTE_INDEXED,
        colorModel);
    source.frameGet(sourceControl, absoluteIndex, image);
    return target.frameAdd(image, new Point(info.getCenterX(), info.getCenterY()));
  }

  static PseudoBamDecoder createBam(EquipmentOverlayModel model, ResourcePlan resource,
      Map<CyclePlan, Integer> occurrences) {
    final PseudoBamDecoder decoder = new PseudoBamDecoder();
    final PseudoBamControl control = decoder.createControl();
    final Map<Integer, CyclePlan> cycles = new HashMap<>();
    for (final CyclePlan cycle : resource.getCycles()) {
      cycles.put(cycle.getCycleIndex(), cycle);
    }

    final IdentityHashMap<AnimationFrame, Map<Integer, Integer>> frameIndices = new IdentityHashMap<>();
    for (int cycleIndex = 0; cycleIndex < resource.getCycleCount(); cycleIndex++) {
      final CyclePlan cycle = cycles.get(cycleIndex);
      if (cycle == null) {
        control.cycleAdd();
        continue;
      }

      final Integer occurrence = occurrences.get(cycle);
      if (occurrence == null) {
        decoder.close();
        throw new IllegalStateException("No occurrence index was planned for " + resource.getFileName()
            + " cycle " + cycleIndex + ".");
      }
      final List<AnimationFrame> frames =
          model.resolveVariantFrames(cycle.getSequence(), cycle.getDirectionIndex(), occurrence);
      if (frames.isEmpty()) {
        decoder.close();
        throw new IllegalArgumentException("No equipment frames resolve for " + cycle.getSequence().getCode()
            + " at direction index " + cycle.getDirectionIndex() + ", occurrence " + occurrence + ".");
      }

      final int[] indices = new int[frames.size()];
      for (int targetIndex = 0; targetIndex < frames.size(); targetIndex++) {
        final int sourceIndex = cycle.isReversed() ? frames.size() - 1 - targetIndex : targetIndex;
        final AnimationFrame frame = frames.get(sourceIndex);
        final int transformKey = getTransformKey(cycle);
        final Map<Integer, Integer> transformedIndices =
            frameIndices.computeIfAbsent(frame, key -> new HashMap<Integer, Integer>());
        Integer frameIndex = transformedIndices.get(transformKey);
        if (frameIndex == null) {
          final TransformedFrame transformed = transformFrame(frame, cycle);
          frameIndex = decoder.frameAdd(transformed.image, transformed.center);
          transformedIndices.put(transformKey, frameIndex);
        }
        indices[targetIndex] = frameIndex;
      }
      control.cycleAdd(indices);
    }
    return decoder;
  }

  private static int getTransformKey(CyclePlan cycle) {
    int result = cycle.isMirrored() ? 1 : 0;
    result |= cycle.isBlank() ? 2 : 0;
    result |= (cycle.getQuadrantIndex() + 1) << 2;
    result |= cycle.getQuadrantCount() << 8;
    return result;
  }

  private static TransformedFrame transformFrame(AnimationFrame frame, CyclePlan cycle) {
    BufferedImage image = frame.getImage();
    Point center = frame.getCenter();
    if (cycle.isMirrored()) {
      image = mirrorImage(image);
      center = new Point(image.getWidth() - 1 - center.x, center.y);
    }
    if (cycle.isBlank()) {
      image = createCompatibleImage(image, image.getWidth(), image.getHeight());
    }
    if (cycle.getQuadrantCount() > 0) {
      final int[] bounds = getQuadrantBounds(image.getWidth(), image.getHeight(), cycle.getQuadrantIndex(),
          cycle.getQuadrantCount());
      image = copyRegion(image, bounds[0], bounds[1], bounds[2], bounds[3]);
      center.translate(-bounds[0], -bounds[1]);
    }
    return new TransformedFrame(image, center);
  }

  private static BufferedImage mirrorImage(BufferedImage source) {
    final int width = source.getWidth();
    final int height = source.getHeight();
    final BufferedImage target = createCompatibleImage(source, width, height);
    final WritableRaster sourceRaster = source.getRaster();
    final WritableRaster targetRaster = target.getRaster();
    Object pixel = null;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        pixel = sourceRaster.getDataElements(x, y, pixel);
        targetRaster.setDataElements(width - 1 - x, y, pixel);
      }
    }
    return target;
  }

  private static BufferedImage copyRegion(BufferedImage source, int x, int y, int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("A source frame is too small for the selected quadrant count.");
    }
    final BufferedImage target = createCompatibleImage(source, width, height);
    final Object data = source.getRaster().getDataElements(x, y, width, height, null);
    target.getRaster().setDataElements(0, 0, width, height, data);
    return target;
  }

  private static BufferedImage createCompatibleImage(BufferedImage source, int width, int height) {
    final WritableRaster raster = source.getRaster().createCompatibleWritableRaster(width, height);
    return new BufferedImage(source.getColorModel(), raster, source.isAlphaPremultiplied(), null);
  }

  static int[] getQuadrantBounds(int width, int height, int quadrantIndex, int quadrantCount) {
    final int columns = (int) Math.ceil(Math.sqrt(quadrantCount));
    final int rows = (quadrantCount + columns - 1) / columns;
    final int row = quadrantIndex / columns;
    final int firstInRow = row * columns;
    final int columnsInRow = Math.min(columns, quadrantCount - firstInRow);
    final int column = quadrantIndex - firstInRow;
    final int x = column * width / columnsInRow;
    final int y = row * height / rows;
    final int nextX = (column + 1) * width / columnsInRow;
    final int nextY = (row + 1) * height / rows;
    return new int[] { x, y, nextX - x, nextY - y };
  }

  private static final class TransformedFrame {
    private final BufferedImage image;
    private final Point center;

    private TransformedFrame(BufferedImage image, Point center) {
      this.image = image;
      this.center = center;
    }
  }

  static int getRequiredCycleCount(List<OutputSlot> slots) {
    int result = 0;
    for (final OutputSlot slot : slots) {
      result = Math.max(result, slot.getCycleOffset() + Direction.values().length);
    }
    return result;
  }

  static PseudoBamDecoder convertToPalettedBam(PseudoBamDecoder source) {
    if (hasCommonIndexedPalette(source)) {
      return normalizeIndexedPalette(source);
    }

    final Set<Integer> colors = new LinkedHashSet<>();
    for (final PseudoBamFrameEntry frame : source.getFramesList()) {
      final BufferedImage image = frame.getFrame();
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < image.getWidth(); x++) {
          final int color = image.getRGB(x, y);
          if (((color >>> 24) & 0xff) != 0) {
            colors.add(color);
          }
        }
      }
    }
    int[] paletteColors = new int[colors.size()];
    int colorIndex = 0;
    for (final Integer color : colors) {
      paletteColors[colorIndex++] = color;
    }
    if (paletteColors.length > 255) {
      paletteColors = ColorConvert.medianCut(paletteColors, 255, false);
    }

    final int[] palette = new int[256];
    palette[0] = 0x0000ff00;
    System.arraycopy(paletteColors, 0, palette, 1, Math.min(255, paletteColors.length));
    final IndexColorModel colorModel = new IndexColorModel(8, 256, palette, 0, true, 0, DataBuffer.TYPE_BYTE);
    final Map<Integer, Byte> colorCache = new HashMap<>(4096);
    for (int i = 1; i < palette.length; i++) {
      colorCache.put(palette[i], (byte) i);
    }

    final PseudoBamDecoder result = new PseudoBamDecoder();
    for (final PseudoBamFrameEntry frame : source.getFramesList()) {
      final BufferedImage sourceImage = frame.getFrame();
      final BufferedImage targetImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(),
          BufferedImage.TYPE_BYTE_INDEXED, colorModel);
      final byte[] target = ((DataBufferByte) targetImage.getRaster().getDataBuffer()).getData();
      int offset = 0;
      for (int y = 0; y < sourceImage.getHeight(); y++) {
        for (int x = 0; x < sourceImage.getWidth(); x++, offset++) {
          final int color = sourceImage.getRGB(x, y);
          if (((color >>> 24) & 0xff) == 0) {
            target[offset] = 0;
          } else {
            Byte index = colorCache.get(color);
            if (index == null) {
              index = (byte) ColorConvert.getNearestColor(color, palette, 1.0, ColorConvert.COLOR_DISTANCE_CIE94,
                  true);
              colorCache.put(color, index);
            }
            target[offset] = index;
          }
        }
      }
      final int index = result.frameAdd(targetImage, new Point(frame.getCenterX(), frame.getCenterY()));
      final PseudoBamFrameEntry targetFrame = result.getFrameInfo(index);
      targetFrame.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, true);
      targetFrame.setOption(PseudoBamDecoder.OPTION_BOOL_TRANSPARENTGREENFORCED, true);
    }
    result.getCyclesList().addAll(source.getCyclesList());
    return result;
  }

  /**
   * Converts generated frames through an installed classic BAM palette without reordering any palette index.
   */
  static PseudoBamDecoder convertToPalettedBam(PseudoBamDecoder source, int[] sourcePalette,
      int transparencyIndex) {
    if (sourcePalette == null || sourcePalette.length == 0 || transparencyIndex < 0
        || transparencyIndex >= sourcePalette.length) {
      throw new IllegalArgumentException("A valid installed BAM palette and transparency index are required.");
    }
    final int[] palette = sourcePalette.clone();
    final IndexColorModel colorModel = new IndexColorModel(8, palette.length, palette, 0, true,
        transparencyIndex, DataBuffer.TYPE_BYTE);
    final int[] searchablePalette = new int[palette.length];
    colorModel.getRGBs(searchablePalette);
    searchablePalette[transparencyIndex] = 0x0000ff00;

    final Map<Integer, Byte> colorCache = new HashMap<>(4096);
    final PseudoBamDecoder result = new PseudoBamDecoder();
    for (final PseudoBamFrameEntry frame : source.getFramesList()) {
      final BufferedImage sourceImage = frame.getFrame();
      final BufferedImage targetImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(),
          BufferedImage.TYPE_BYTE_INDEXED, colorModel);
      if (sourceImage.getColorModel() instanceof IndexColorModel
          && palettesEqual((IndexColorModel) sourceImage.getColorModel(), colorModel)) {
        targetImage.getRaster().setRect(sourceImage.getRaster());
      } else {
        final byte[] target = ((DataBufferByte) targetImage.getRaster().getDataBuffer()).getData();
        int offset = 0;
        for (int y = 0; y < sourceImage.getHeight(); y++) {
          for (int x = 0; x < sourceImage.getWidth(); x++, offset++) {
            final int color = sourceImage.getRGB(x, y);
            if (((color >>> 24) & 0xff) == 0) {
              target[offset] = (byte) transparencyIndex;
            } else {
              Byte index = colorCache.get(color);
              if (index == null) {
                final int nearest = ColorConvert.getNearestColor(color, searchablePalette, 1.0,
                    ColorConvert.COLOR_DISTANCE_CIE94, true);
                if (nearest < 0) {
                  throw new IllegalArgumentException("Could not map a generated frame color to the installed palette.");
                }
                index = (byte) nearest;
                colorCache.put(color, index);
              }
              target[offset] = index;
            }
          }
        }
      }
      final int index = result.frameAdd(targetImage, new Point(frame.getCenterX(), frame.getCenterY()));
      final PseudoBamFrameEntry targetFrame = result.getFrameInfo(index);
      targetFrame.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, true);
      targetFrame.setOption(PseudoBamDecoder.OPTION_BOOL_TRANSPARENTGREENFORCED, false);
    }
    result.getCyclesList().addAll(source.getCyclesList());
    return result;
  }

  private static PseudoBamDecoder normalizeIndexedPalette(PseudoBamDecoder source) {
    final IndexColorModel sourceModel =
        (IndexColorModel) source.getFramesList().get(0).getFrame().getColorModel();
    final int[] sourceColors = new int[sourceModel.getMapSize()];
    sourceModel.getRGBs(sourceColors);
    final int[] palette = new int[256];
    Arrays.fill(palette, 0xff000000);
    System.arraycopy(sourceColors, 0, palette, 0, Math.min(sourceColors.length, palette.length));
    palette[0] = 0x0000ff00;
    final IndexColorModel targetModel = new IndexColorModel(8, palette.length, palette, 0, true, 0,
        DataBuffer.TYPE_BYTE);

    final PseudoBamDecoder result = new PseudoBamDecoder();
    for (final PseudoBamFrameEntry frame : source.getFramesList()) {
      final BufferedImage sourceImage = frame.getFrame();
      final BufferedImage targetImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(),
          BufferedImage.TYPE_BYTE_INDEXED, targetModel);
      targetImage.getRaster().setRect(sourceImage.getRaster());
      final int index = result.frameAdd(targetImage, new Point(frame.getCenterX(), frame.getCenterY()));
      final PseudoBamFrameEntry targetFrame = result.getFrameInfo(index);
      targetFrame.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, true);
      targetFrame.setOption(PseudoBamDecoder.OPTION_BOOL_TRANSPARENTGREENFORCED, true);
    }
    result.getCyclesList().addAll(source.getCyclesList());
    return result;
  }

  private static boolean hasCommonIndexedPalette(CreatureAnimationModel model) {
    final IndexColorModel reference = getCommonIndexedPalette(model);
    return reference != null && reference.getTransparentPixel() == 0;
  }

  private static IndexColorModel getCommonIndexedPalette(CreatureAnimationModel model) {
    IndexColorModel reference = null;
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        for (final AnimationFrame frame : model.getFrames(sequence, direction)) {
          if (!(frame.getImage().getColorModel() instanceof IndexColorModel)) {
            return null;
          }
          final IndexColorModel current = (IndexColorModel) frame.getImage().getColorModel();
          if (reference == null) {
            reference = current;
          } else if (!palettesEqual(reference, current)) {
            return null;
          }
        }
      }
    }
    return reference;
  }

  private static boolean hasCommonIndexedPalette(PseudoBamDecoder decoder) {
    IndexColorModel reference = null;
    for (final PseudoBamFrameEntry frame : decoder.getFramesList()) {
      if (!(frame.getFrame().getColorModel() instanceof IndexColorModel)) {
        return false;
      }
      final IndexColorModel current = (IndexColorModel) frame.getFrame().getColorModel();
      if (reference == null) {
        reference = current;
      } else if (!palettesEqual(reference, current)) {
        return false;
      }
    }
    return reference != null && reference.getTransparentPixel() == 0;
  }

  private static boolean palettesEqual(IndexColorModel first, IndexColorModel second) {
    if (first.getMapSize() != second.getMapSize()) {
      return false;
    }
    final int[] firstColors = new int[first.getMapSize()];
    final int[] secondColors = new int[second.getMapSize()];
    first.getRGBs(firstColors);
    second.getRGBs(secondColors);
    return Arrays.equals(firstColors, secondColors);
  }

  private static boolean classicPalettesMatch(CreatureAnimationModel model, Config config, FamilyLayout layout) {
    final IndexColorModel modelPalette = getCommonIndexedPalette(model);
    if (modelPalette == null) {
      return false;
    }
    final int[] modelColors = new int[modelPalette.getMapSize()];
    modelPalette.getRGBs(modelColors);
    for (final ResourcePlan resource : layout.getResources().values()) {
      final ResourceEntry entry = getClassicSourceEntry(config, resource.getFileName());
      if (entry == null) {
        return false;
      }
      final BamDecoder decoder = BamDecoder.loadBam(entry);
      if (!(decoder instanceof BamV1Decoder) || !decoder.isOpen()) {
        if (decoder != null) {
          decoder.close();
        }
        return false;
      }
      try {
        final BamV1Decoder v1 = (BamV1Decoder) decoder;
        if (modelPalette.getTransparentPixel() != v1.getRleIndex()) {
          return false;
        }
        final int[] targetColors = v1.createControl().getPalette();
        if (modelColors.length != targetColors.length) {
          return false;
        }
        for (int index = 0; index < modelColors.length; index++) {
          if ((modelColors[index] & 0x00ffffff) != (targetColors[index] & 0x00ffffff)) {
            return false;
          }
        }
      } finally {
        decoder.close();
      }
    }
    return true;
  }

  private static void validateStagedOutput(Path staging, Config config, FamilyLayout layout,
      Map<String, Integer> expectedCycles) throws Exception {
    for (final Map.Entry<String, Integer> entry : expectedCycles.entrySet()) {
      final Path path = staging.resolve(entry.getKey());
      final FileResourceEntry resource = new FileResourceEntry(path);
      final BamDecoder.Type expectedType = (config.bamFormat == BamFormat.BAM_V1)
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
        validatePvrzReferences(staging, path);
      }
    }

    if (!isClassicProfile(config)) {
      final Path iniPath = staging.resolve(getIniFileName(config.animationId));
      final String ini = new String(Files.readAllBytes(iniPath), StandardCharsets.UTF_8);
      final String expectedType = String.format(Locale.ENGLISH, "animation_type=%04X",
          config.family.getAnimationTypeCode(config.animationId));
      boolean valid = ini.contains("[general]") && ini.contains(expectedType)
          && ini.contains("[" + config.family.getSectionName() + "]");
      if (config.family == CreatureAnimationFamily.MONSTER_PLANESCAPE) {
        for (final Map.Entry<String, String> action : layout.getActionResrefs().entrySet()) {
          if (!ini.contains(action.getKey() + "=" + action.getValue())) {
            valid = false;
            break;
          }
        }
      } else {
        valid &= ini.contains("resref=" + config.resref);
      }
      if (!valid) {
        throw new IOException("Round-trip validation rejected the generated animation INI.");
      }
    }
  }

  static void validatePvrzReferences(Path staging, Path bamPath) throws IOException {
    final byte[] data = Files.readAllBytes(bamPath);
    if (data.length < 0x20) {
      throw new IOException("BAM V2 header is truncated in " + bamPath.getFileName() + ".");
    }
    final ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    final int blockCount = buffer.getInt(0x10);
    final int blockOffset = buffer.getInt(0x1c);
    if (blockCount < 0 || blockOffset < 0 || blockOffset + (long) blockCount * 28L > data.length) {
      throw new IOException("BAM V2 frame block table is invalid in " + bamPath.getFileName() + ".");
    }
    for (int i = 0; i < blockCount; i++) {
      final int page = buffer.getInt(blockOffset + i * 28);
      final Path pvrz = staging.resolve(PseudoBamDecoder.getPvrzFileName(page));
      if (!Files.isRegularFile(pvrz) || Files.size(pvrz) == 0L) {
        throw new IOException("BAM V2 references missing texture page " + pvrz.getFileName() + ".");
      }
    }
  }

  static List<Path> installStagedFiles(Path staging, Path output, boolean overwrite) throws IOException {
    final List<Path> sources;
    try (Stream<Path> stream = Files.list(staging)) {
      sources = stream.filter(Files::isRegularFile)
          .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ENGLISH)))
          .collect(Collectors.toList());
    }
    final Path backup = Files.createDirectory(staging.resolve("backup"));
    final List<Path> movedTargets = new ArrayList<>();
    final List<Path> backedUpTargets = new ArrayList<>();
    try {
      for (final Path source : sources) {
        final Path target = output.resolve(source.getFileName());
        if (Files.exists(target)) {
          if (!overwrite) {
            throw new IOException("Export would overwrite " + target + ".");
          }
          move(target, backup.resolve(target.getFileName()), false);
          backedUpTargets.add(target);
        }
        move(source, target, false);
        movedTargets.add(target);
      }
    } catch (IOException e) {
      IOException rollbackFailure = null;
      for (int i = movedTargets.size() - 1; i >= 0; i--) {
        try {
          Files.deleteIfExists(movedTargets.get(i));
        } catch (IOException rollbackException) {
          rollbackFailure = appendFailure(rollbackFailure, rollbackException);
        }
      }
      for (int i = backedUpTargets.size() - 1; i >= 0; i--) {
        final Path target = backedUpTargets.get(i);
        final Path saved = backup.resolve(target.getFileName());
        try {
          if (Files.exists(saved)) {
            move(saved, target, true);
          }
        } catch (IOException rollbackException) {
          rollbackFailure = appendFailure(rollbackFailure, rollbackException);
        }
      }
      if (rollbackFailure != null) {
        e.addSuppressed(rollbackFailure);
      }
      throw e;
    }

    try {
      deleteTree(backup);
      Files.deleteIfExists(staging);
    } catch (IOException e) {
      Logger.warn(e, "Creature animation export succeeded, but its staging backup could not be removed.");
    }
    return movedTargets;
  }

  private static IOException appendFailure(IOException current, IOException failure) {
    if (current == null) {
      return failure;
    }
    current.addSuppressed(failure);
    return current;
  }

  private static void move(Path source, Path target, boolean replace) throws IOException {
    final StandardCopyOption[] atomicOptions = replace
        ? new StandardCopyOption[] { StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING }
        : new StandardCopyOption[] { StandardCopyOption.ATOMIC_MOVE };
    try {
      Files.move(source, target, atomicOptions);
    } catch (AtomicMoveNotSupportedException e) {
      if (replace) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
      } else {
        Files.move(source, target);
      }
    }
  }

  static int findPvrzStartIndex(Path directory) throws IOException {
    int highest = -1;
    if (directory != null && Files.isDirectory(directory)) {
      try (Stream<Path> stream = Files.list(directory)) {
        for (final Path path : stream.collect(Collectors.toList())) {
          final Matcher matcher = PVRZ_NAME.matcher(path.getFileName().toString());
          if (matcher.matches()) {
            highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
          }
        }
      }
    }
    if (highest >= 99999) {
      throw new IOException("No PVRZ index remains above the existing output pages. Use BAM V1 or free PVRZ indices.");
    }
    return highest + 1;
  }

  private static String getIniFileName(int animationId) {
    return String.format(Locale.ENGLISH, "%04X.INI", animationId & 0xffff);
  }

  private static void validateRange(ValidationReport report, String label, int value, int min, int max) {
    if (value < min || value > max) {
      report.add(Severity.ERROR, label + " must be in the range " + min + "-" + max + ".");
    }
  }

  static void deleteTree(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
        if (exception != null) {
          throw exception;
        }
        Files.deleteIfExists(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
