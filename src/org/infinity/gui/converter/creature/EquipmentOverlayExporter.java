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
import org.infinity.gui.converter.creature.CreatureAnimationFamily.CyclePlan;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.ResourcePlan;
import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.EquipmentOverlayFamily.AttackKind;
import org.infinity.gui.converter.creature.EquipmentOverlayFamily.OverlaySlot;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.WeaponType;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.resource.Profile;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.DxtEncoder;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.key.FileResourceEntry;

/** Validates, encodes and transactionally installs family-specific weapon-overlay resources. */
public final class EquipmentOverlayExporter {
  private static final Pattern RESOURCE_PREFIX = Pattern.compile("(?i)^[A-Z0-9_]{1,8}$");
  private static final Pattern APPEARANCE_CODE = Pattern.compile("(?i)^[A-Z0-9_]{2}$");

  public static final class Config {
    private Profile.Game game = Profile.getGame();
    private EquipmentOverlayFamily family = EquipmentOverlayFamily.MONSTER;
    private String resourcePrefix = "";
    private String appearanceCode = "";
    private WeaponType weaponType = WeaponType.SWORD;
    private String offhandResourcePrefix = "";
    private String offhandAppearanceCode = "";
    private WeaponType offhandType;
    private Path outputDirectory;
    private BamFormat bamFormat = BamFormat.BAM_V1;
    private boolean compressedBam = true;

    public Profile.Game getGame() {
      return game;
    }

    public Config setGame(Profile.Game value) {
      game = value;
      return this;
    }

    public EquipmentOverlayFamily getFamily() {
      return family;
    }

    public Config setFamily(EquipmentOverlayFamily value) {
      family = value;
      return this;
    }

    public String getResourcePrefix() {
      return resourcePrefix;
    }

    public Config setResourcePrefix(String value) {
      resourcePrefix = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
      return this;
    }

    /** Compatibility alias for the original type {@code 0x7000} configuration API. */
    public String getResref() {
      return getResourcePrefix();
    }

    /** Compatibility alias for the original type {@code 0x7000} configuration API. */
    public Config setResref(String value) {
      return setResourcePrefix(value);
    }

    public String getAppearanceCode() {
      return appearanceCode;
    }

    public Config setAppearanceCode(String value) {
      appearanceCode = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
      return this;
    }

    public WeaponType getWeaponType() {
      return weaponType;
    }

    public Config setWeaponType(WeaponType value) {
      weaponType = value;
      return this;
    }

    public String getOffhandResourcePrefix() {
      return offhandResourcePrefix;
    }

    public Config setOffhandResourcePrefix(String value) {
      offhandResourcePrefix = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
      return this;
    }

    public String getOffhandAppearanceCode() {
      return offhandAppearanceCode;
    }

    public Config setOffhandAppearanceCode(String value) {
      offhandAppearanceCode = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
      return this;
    }

    public WeaponType getOffhandType() {
      return offhandType;
    }

    public Config setOffhandType(WeaponType value) {
      offhandType = value;
      return this;
    }

    public boolean hasOffhand() {
      return offhandType != null;
    }

    public Config clearOffhand() {
      offhandResourcePrefix = "";
      offhandAppearanceCode = "";
      offhandType = null;
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
    return validate(EquipmentOverlayModel.fromWestern(model), config);
  }

  public static ValidationReport validate(EquipmentOverlayModel model, Config config) {
    return validate(model, null, config);
  }

  public static ValidationReport validate(EquipmentOverlayModel model, EquipmentOverlayModel offhandModel,
      Config config) {
    final ValidationReport report = new ValidationReport();
    if (config == null) {
      report.add(Severity.ERROR, "No equipment-overlay export configuration was supplied.");
      return report;
    }
    if (config.family == null) {
      report.add(Severity.ERROR, "No equipment-overlay animation family was selected.");
    }
    if (!MonsterAnimationLayout.isSupportedGame(config.game)) {
      report.add(Severity.ERROR, "Equipment overlay export requires a recognized Infinity Engine game profile.");
    }
    if (!RESOURCE_PREFIX.matcher(config.resourcePrefix).matches()) {
      report.add(Severity.ERROR, "The main-hand overlay resource prefix must contain 1-8 ASCII letters, digits or "
          + "underscores.");
    }
    if (!APPEARANCE_CODE.matcher(config.appearanceCode).matches()) {
      report.add(Severity.ERROR, "The new equipped appearance code must contain exactly two ASCII letters, digits or "
          + "underscores.");
    }
    if (config.weaponType == null) {
      report.add(Severity.ERROR, "No target main-hand equipment type was selected.");
    }
    if (config.hasOffhand()) {
      if (!RESOURCE_PREFIX.matcher(config.offhandResourcePrefix).matches()) {
        report.add(Severity.ERROR, "The off-hand overlay resource prefix must contain 1-8 ASCII letters, digits or "
            + "underscores.");
      }
      if (!APPEARANCE_CODE.matcher(config.offhandAppearanceCode).matches()) {
        report.add(Severity.ERROR, "The off-hand equipped appearance code must contain exactly two ASCII letters, "
            + "digits or underscores.");
      }
      if (offhandModel == null || offhandModel.isEmpty()) {
        report.add(Severity.ERROR, "No generated off-hand overlay is loaded.");
      }
    } else if (offhandModel != null && !offhandModel.isEmpty()) {
      report.add(Severity.ERROR, "An off-hand overlay is loaded without an off-hand export definition.");
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
    if (model == null || model.isEmpty()) {
      report.add(Severity.ERROR, "No generated main-hand equipment overlay is loaded.");
      return report;
    }
    AttackKind attackKind = null;
    if (config.family != null && config.weaponType != null) {
      try {
        config.family.validateLoadout(config.weaponType, config.offhandType);
        attackKind = config.family.getAttackKind(config.weaponType, config.offhandType);
      } catch (IllegalArgumentException e) {
        report.add(Severity.ERROR, e.getMessage());
      }
    }
    if (report.hasErrors()) {
      return report;
    }

    final List<PlannedLayer> layers;
    try {
      layers = createLayers(model, offhandModel, config, attackKind);
    } catch (IllegalArgumentException e) {
      report.add(Severity.ERROR, e.getMessage());
      return report;
    }

    final java.util.Set<String> reportedCells = new java.util.HashSet<>();
    final java.util.Set<String> resourceNames = new java.util.HashSet<>();
    for (final PlannedLayer layer : layers) {
      final Map<CyclePlan, Integer> occurrences = EquipmentOverlayModel.getOccurrenceIndices(layer.layout);
      for (final ResourcePlan resource : layer.layout.getResources().values()) {
        if (!resourceNames.add(resource.getFileName())) {
          report.add(Severity.ERROR, "Main-hand and off-hand plans both target " + resource.getFileName() + ".");
        }
        for (final CyclePlan cycle : resource.getCycles()) {
          final Integer occurrence = occurrences.get(cycle);
          if (occurrence == null) {
            report.add(Severity.ERROR, "No cycle occurrence was planned for " + resource.getFileName() + " cycle "
                + cycle.getCycleIndex() + ".");
            continue;
          }
          final String cellKey = layer.slot + "/" + cycle.getSequence().name() + "/"
              + cycle.getDirectionIndex() + "/" + occurrence;
          final List<AnimationFrame> frames =
              layer.model.getFrames(cycle.getSequence(), cycle.getDirectionIndex(), occurrence);
          if (frames.isEmpty()) {
            if (reportedCells.add(cellKey)) {
              report.add(Severity.ERROR, "The generated " + getSlotLabel(layer.slot)
                  + " overlay is missing synchronized " + cycle.getSequence().getCode()
                  + " frames for direction index " + cycle.getDirectionIndex()
                  + ", cycle occurrence " + occurrence + ".");
            }
            continue;
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
    }
    if (config.bamFormat == BamFormat.BAM_V2 && config.compressedBam) {
      report.add(Severity.INFO, "BAMC compression applies only to BAM V1 and will be ignored for BAM V2.");
    }
    if (!config.family.usesFullAppearanceCodeInFileName()) {
      report.add(Severity.WARNING, config.family + " selects weapon BAMs from only the first character of the "
          + "Equipped appearance field. Every item code beginning with "
          + config.family.getFileCode(config.appearanceCode) + " will share this generated layer.");
    }
    report.add(Severity.INFO, "Use " + config.family.getActivationSummary(config.appearanceCode)
        + " on the main-hand ITM to activate the generated " + config.family + " weapon overlay.");
    if (config.hasOffhand()) {
      report.add(Severity.INFO, "Use " + config.family.getActivationSummary(config.offhandAppearanceCode)
          + " on the " + (config.offhandType.isShield() ? "shield" : "left-handed weapon")
          + " ITM in the shield slot to activate the generated off-hand overlay.");
    }
    return report;
  }

  public static List<Path> getExistingTargets(Config config) {
    if (config == null || config.outputDirectory == null || config.family == null
        || !RESOURCE_PREFIX.matcher(config.resourcePrefix).matches()
        || !APPEARANCE_CODE.matcher(config.appearanceCode).matches() || config.weaponType == null
        || config.hasOffhand() && (!RESOURCE_PREFIX.matcher(config.offhandResourcePrefix).matches()
            || !APPEARANCE_CODE.matcher(config.offhandAppearanceCode).matches())) {
      return Collections.emptyList();
    }
    final List<Path> result = new ArrayList<>();
    final AttackKind attackKind = config.family.getAttackKind(config.weaponType, config.offhandType);
    for (final FamilyLayout layout : createLayouts(config, attackKind)) {
      for (final String fileName : layout.getResources().keySet()) {
        final Path target = config.outputDirectory.resolve(fileName);
        if (Files.exists(target)) {
          result.add(target);
        }
      }
    }
    return result;
  }

  public static ExportResult export(CreatureAnimationModel model, Config config, boolean overwrite) throws Exception {
    return export(EquipmentOverlayModel.fromWestern(model), config, overwrite);
  }

  public static ExportResult export(EquipmentOverlayModel model, Config config, boolean overwrite) throws Exception {
    return export(model, null, config, overwrite);
  }

  public static ExportResult export(EquipmentOverlayModel model, EquipmentOverlayModel offhandModel, Config config,
      boolean overwrite) throws Exception {
    final ValidationReport report = validate(model, offhandModel, config);
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
      final AttackKind attackKind = config.family.getAttackKind(config.weaponType, config.offhandType);
      final List<PlannedLayer> layers = createLayers(model, offhandModel, config, attackKind);
      int pvrzIndex = config.bamFormat == BamFormat.BAM_V2
          ? CreatureAnimationExporter.findPvrzStartIndex(config.outputDirectory) : 0;
      final Map<String, Integer> expectedCycles = new LinkedHashMap<>();
      for (final PlannedLayer layer : layers) {
        final Map<CyclePlan, Integer> occurrences = EquipmentOverlayModel.getOccurrenceIndices(layer.layout);
        for (final ResourcePlan resource : layer.layout.getResources().values()) {
          final String fileName = resource.getFileName();
          final PseudoBamDecoder source =
              CreatureAnimationExporter.createBam(layer.model, resource, occurrences);
          if (expectedCycles.put(fileName, resource.getCycleCount()) != null) {
            throw new IOException("More than one equipment layer targets " + fileName + ".");
          }
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

  private static List<FamilyLayout> createLayouts(Config config, AttackKind attackKind) {
    final List<FamilyLayout> result = new ArrayList<>();
    result.add(config.family.createOverlayLayout(config.resourcePrefix, config.appearanceCode, config.weaponType,
        attackKind, OverlaySlot.MAIN_HAND));
    if (config.hasOffhand()) {
      final OverlaySlot slot = config.offhandType.isShield() ? OverlaySlot.SHIELD : OverlaySlot.OFF_HAND_WEAPON;
      result.add(config.family.createOverlayLayout(config.offhandResourcePrefix, config.offhandAppearanceCode,
          config.offhandType, attackKind, slot));
    }
    return result;
  }

  private static List<PlannedLayer> createLayers(EquipmentOverlayModel model, EquipmentOverlayModel offhandModel,
      Config config, AttackKind attackKind) {
    final List<FamilyLayout> layouts = createLayouts(config, attackKind);
    final List<PlannedLayer> result = new ArrayList<>();
    result.add(new PlannedLayer(OverlaySlot.MAIN_HAND, model, layouts.get(0)));
    if (config.hasOffhand()) {
      final OverlaySlot slot = config.offhandType.isShield() ? OverlaySlot.SHIELD : OverlaySlot.OFF_HAND_WEAPON;
      result.add(new PlannedLayer(slot, offhandModel, layouts.get(1)));
    }
    return result;
  }

  private static String getSlotLabel(OverlaySlot slot) {
    switch (slot) {
      case MAIN_HAND:
        return "main-hand";
      case SHIELD:
        return "shield";
      case OFF_HAND_WEAPON:
        return "left-handed weapon";
      default:
        throw new IllegalStateException("Unsupported equipment overlay slot: " + slot);
    }
  }

  private static String getGameTitle(Profile.Game game) {
    return game != null ? game.getTitle() : "The selected game profile";
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

  private static final class PlannedLayer {
    private final OverlaySlot slot;
    private final EquipmentOverlayModel model;
    private final FamilyLayout layout;

    private PlannedLayer(OverlaySlot slot, EquipmentOverlayModel model, FamilyLayout layout) {
      this.slot = slot;
      this.model = model;
      this.layout = layout;
    }
  }
}
