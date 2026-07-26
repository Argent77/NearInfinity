// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
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
import java.util.EnumSet;
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
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.OutputSlot;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.DxtEncoder;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamControl;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamFrameEntry;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.util.Logger;

/** Validates, encodes and transactionally installs a type 0x7000 animation family. */
public final class CreatureAnimationExporter {
  private static final Pattern PVRZ_NAME = Pattern.compile("(?i)^MOS(\\d{4,5})\\.PVRZ$");
  private static final Pattern RESREF = Pattern.compile("(?i)^[A-Z0-9_]{1,4}$");

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
    private int moveScale = 9;
    private int ellipse = 16;
    private int personalSpace = 3;
    private int bloodColor = 47;
    private int chunkColor = 255;

    public Profile.Game getGame() {
      return game;
    }

    public Config setGame(Profile.Game game) {
      this.game = game;
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
    if (!MonsterAnimationLayout.isSupportedGame(config.game)) {
      report.add(Severity.ERROR, "Creature Animation Creator supports BG:EE, SoD, BG2:EE, EET, IWD:EE and PST:EE.");
    } else if (!MonsterAnimationLayout.isValidSlot(config.game, config.animationId)) {
      report.add(Severity.ERROR, String.format(Locale.ENGLISH,
          "Animation slot 0x%04X is not a type 0x7000 monster slot for %s.", config.animationId,
          config.game.getTitle()));
    }
    if (!RESREF.matcher(config.resref).matches()) {
      report.add(Severity.ERROR, "The BAM resref must contain 1-4 ASCII letters, digits or underscores. Type 0x7000 "
          + "requires room for its Gxx filename suffix.");
    }
    if (config.outputDirectory == null) {
      report.add(Severity.ERROR, "No output directory was selected.");
    } else if (Files.exists(config.outputDirectory) && !Files.isDirectory(config.outputDirectory)) {
      report.add(Severity.ERROR, "The selected output path is not a directory.");
    }
    if (config.bamFormat == null) {
      report.add(Severity.ERROR, "No BAM output format was selected.");
    }
    validateRange(report, "Movement scale", config.moveScale, 0, 255);
    validateRange(report, "Selection ellipse", config.ellipse, 0, 255);
    validateRange(report, "Personal space", config.personalSpace, 0, 255);
    validateRange(report, "Blood color", config.bloodColor, 0, 255);
    validateRange(report, "Chunk color", config.chunkColor, 0, 255);

    if (model == null || model.isEmpty()) {
      report.add(Severity.ERROR, "No animation source frames are loaded.");
      return report;
    }

    int missingCells = 0;
    final EnumSet<Sequence> required = EnumSet.allOf(Sequence.class);
    if (config.splitBams) {
      required.remove(Sequence.SLEEP);
      required.remove(Sequence.GET_UP);
      report.add(Severity.INFO, "Split BAM mode reuses the death animation for sleep and reversed get-up sequences.");
    }
    for (final Sequence sequence : required) {
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

    if (config.falseColor && !hasCommonIndexedPalette(model)) {
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
    return report;
  }

  public static List<Path> getExistingPrimaryTargets(Config config) {
    if (config == null || config.outputDirectory == null || config.resref == null) {
      return Collections.emptyList();
    }
    final List<Path> existing = new ArrayList<>();
    final Path ini = config.outputDirectory.resolve(getIniFileName(config.animationId));
    if (Files.exists(ini)) {
      existing.add(ini);
    }
    for (final String suffix : MonsterAnimationLayout.getOutputLayout(config.splitBams).keySet()) {
      final Path bam = config.outputDirectory.resolve(config.resref + suffix + ".BAM");
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
      final Map<String, List<OutputSlot>> layout = MonsterAnimationLayout.getOutputLayout(config.splitBams);
      int pvrzIndex = (config.bamFormat == BamFormat.BAM_V2)
          ? findPvrzStartIndex(config.outputDirectory) : 0;
      final Map<String, Integer> expectedCycles = new LinkedHashMap<>();

      for (final Map.Entry<String, List<OutputSlot>> entry : layout.entrySet()) {
        final String fileName = config.resref + entry.getKey() + ".BAM";
        final PseudoBamDecoder source = createBam(model, entry.getValue());
        final int cycleCount = getRequiredCycleCount(entry.getValue());
        expectedCycles.put(fileName, cycleCount);
        try {
          if (config.bamFormat == BamFormat.BAM_V1) {
            final PseudoBamDecoder paletted = convertToPalettedBam(source);
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
            pvrzIndex = findPvrzStartIndex(staging);
          }
        } finally {
          source.close();
        }
      }

      Files.write(staging.resolve(getIniFileName(config.animationId)),
          createIniText(config).getBytes(StandardCharsets.UTF_8));
      validateStagedOutput(staging, config, expectedCycles);
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
    final StringBuilder result = new StringBuilder(320);
    result.append("[general]\n");
    result.append("animation_type=7000\n");
    result.append("move_scale=").append(config.moveScale).append('\n');
    result.append("ellipse=").append(config.ellipse).append('\n');
    result.append("color_blood=").append(config.bloodColor).append('\n');
    result.append("color_chunks=").append(config.chunkColor).append('\n');
    result.append("personal_space=").append(config.personalSpace).append("\n\n");
    result.append("[monster]\n");
    result.append("resref=").append(config.resref).append('\n');
    result.append("can_lie_down=").append(config.canLieDown ? 1 : 0).append('\n');
    result.append("detected_by_infravision=").append(config.detectedByInfravision ? 1 : 0).append('\n');
    result.append("false_color=").append(config.falseColor ? 1 : 0).append('\n');
    result.append("path_smooth=").append(config.pathSmooth ? 1 : 0).append('\n');
    result.append("split_bams=").append(config.splitBams ? 1 : 0).append('\n');
    result.append("translucent=").append(config.translucent ? 1 : 0).append('\n');
    return result.toString();
  }

  private static PseudoBamDecoder createBam(CreatureAnimationModel model, List<OutputSlot> slots) {
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

  private static int getRequiredCycleCount(List<OutputSlot> slots) {
    int result = 0;
    for (final OutputSlot slot : slots) {
      result = Math.max(result, slot.getCycleOffset() + Direction.values().length);
    }
    return result;
  }

  private static PseudoBamDecoder convertToPalettedBam(PseudoBamDecoder source) {
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
    IndexColorModel reference = null;
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        for (final AnimationFrame frame : model.getFrames(sequence, direction)) {
          if (!(frame.getImage().getColorModel() instanceof IndexColorModel)) {
            return false;
          }
          final IndexColorModel current = (IndexColorModel) frame.getImage().getColorModel();
          if (reference == null) {
            reference = current;
          } else if (!palettesEqual(reference, current)) {
            return false;
          }
        }
      }
    }
    return reference != null && reference.getTransparentPixel() == 0;
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

  private static void validateStagedOutput(Path staging, Config config, Map<String, Integer> expectedCycles)
      throws Exception {
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

    final Path iniPath = staging.resolve(getIniFileName(config.animationId));
    final String ini = new String(Files.readAllBytes(iniPath), StandardCharsets.UTF_8);
    if (!ini.contains("[general]") || !ini.contains("animation_type=7000") || !ini.contains("[monster]")
        || !ini.contains("resref=" + config.resref)) {
      throw new IOException("Round-trip validation rejected the generated animation INI.");
    }
  }

  private static void validatePvrzReferences(Path staging, Path bamPath) throws IOException {
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

  private static List<Path> installStagedFiles(Path staging, Path output, boolean overwrite) throws IOException {
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

  private static int findPvrzStartIndex(Path directory) throws IOException {
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

  private static void deleteTree(Path root) throws IOException {
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
