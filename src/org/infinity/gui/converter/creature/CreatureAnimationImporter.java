// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;

/** Imports and exports the documented PNG interchange layout used by the creature animation creator. */
public final class CreatureAnimationImporter {
  public static final String CENTERS_FILE = "centers.csv";
  public static final String FORMAT_FILE = "SOURCE_FORMAT.txt";

  private static final Pattern FLAT_NAME = Pattern.compile(
      "(?i)^(WK|SC|SD|GH|DE|TW|SL|GU|A1|A2|A3|A4|A5|SP|CA)[_-]"
          + "(S|SSW|SW|WSW|W|WNW|NW|NNW|N)[_-](\\d+)\\.png$");
  private static final Pattern FRAME_NUMBER = Pattern.compile("(?i)^(?:frame[_-]?)?(\\d+)\\.png$");
  private static final Pattern COMBINED_FOLDER = Pattern.compile(
      "(?i)^(WK|SC|SD|GH|DE|TW|SL|GU|A1|A2|A3|A4|A5|SP|CA)[_-]"
          + "(S|SSW|SW|WSW|W|WNW|NW|NNW|N)$");

  public static final class ImportResult {
    private final CreatureAnimationModel model;
    private final int frameCount;
    private final int populatedCells;
    private final List<String> warnings;

    private ImportResult(CreatureAnimationModel model, int frameCount, int populatedCells, List<String> warnings) {
      this.model = model;
      this.frameCount = frameCount;
      this.populatedCells = populatedCells;
      this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public CreatureAnimationModel getModel() {
      return model;
    }

    public int getFrameCount() {
      return frameCount;
    }

    public int getPopulatedCells() {
      return populatedCells;
    }

    public List<String> getWarnings() {
      return warnings;
    }
  }

  static final class ParsedName {
    private final Sequence sequence;
    private final Direction direction;
    private final int frameIndex;

    private ParsedName(Sequence sequence, Direction direction, int frameIndex) {
      this.sequence = sequence;
      this.direction = direction;
      this.frameIndex = frameIndex;
    }
  }

  private static final class GroupKey {
    private final Sequence sequence;
    private final Direction direction;

    private GroupKey(Sequence sequence, Direction direction) {
      this.sequence = sequence;
      this.direction = direction;
    }

    @Override
    public int hashCode() {
      return sequence.hashCode() * 31 + direction.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof GroupKey)) {
        return false;
      }
      final GroupKey other = (GroupKey) obj;
      return sequence == other.sequence && direction == other.direction;
    }
  }

  private CreatureAnimationImporter() {
  }

  public static ImportResult importDirectory(Path root) throws IOException {
    if (root == null || !Files.isDirectory(root)) {
      throw new IOException("The selected PNG source directory does not exist.");
    }

    final List<String> warnings = new ArrayList<>();
    final Map<String, Point> centers = readCenters(root.resolve(CENTERS_FILE), warnings);
    final Map<GroupKey, TreeMap<Integer, Path>> grouped = new HashMap<>();
    final List<Path> pngFiles;
    try (Stream<Path> stream = Files.walk(root)) {
      pngFiles = stream.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".png"))
          .sorted(Comparator.comparing(path -> root.relativize(path).toString().toLowerCase(Locale.ENGLISH)))
          .collect(Collectors.toList());
    }

    int ignored = 0;
    for (final Path path : pngFiles) {
      final ParsedName parsed = parseName(root, path);
      if (parsed == null) {
        ignored++;
        continue;
      }
      final GroupKey key = new GroupKey(parsed.sequence, parsed.direction);
      TreeMap<Integer, Path> frames = grouped.get(key);
      if (frames == null) {
        frames = new TreeMap<>();
        grouped.put(key, frames);
      }
      final Path previous = frames.put(parsed.frameIndex, path);
      if (previous != null) {
        warnings.add("Duplicate frame index " + parsed.frameIndex + " for " + parsed.sequence.getCode() + "/"
            + parsed.direction.getCode() + "; using " + root.relativize(path) + ".");
      }
    }

    if (grouped.isEmpty()) {
      throw new IOException("No named creature-animation PNG frames were found. Expected names such as "
          + "WK_S_000.png or folders such as WK/S/000.png.");
    }
    if (ignored > 0) {
      warnings.add(ignored + " PNG file(s) did not match the source naming convention and were ignored.");
    }

    final CreatureAnimationModel model = new CreatureAnimationModel();
    int imported = 0;
    for (final Map.Entry<GroupKey, TreeMap<Integer, Path>> entry : grouped.entrySet()) {
      final List<BufferedImage> images = new ArrayList<>();
      final List<Path> paths = new ArrayList<>();
      for (final Path path : entry.getValue().values()) {
        final BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) {
          warnings.add("Could not decode " + root.relativize(path) + "; the file was skipped.");
          continue;
        }
        images.add(toArgb(source));
        paths.add(path);
      }
      if (images.isEmpty()) {
        continue;
      }

      final Point sharedCenter = inferSharedCenter(images);
      final List<AnimationFrame> frames = new ArrayList<>(images.size());
      for (int i = 0; i < images.size(); i++) {
        final Path relative = root.relativize(paths.get(i));
        Point center = centers.get(normalize(relative));
        if (center == null) {
          center = centers.get(normalize(paths.get(i).getFileName()));
        }
        if (center == null) {
          center = sharedCenter;
        }
        frames.add(new AnimationFrame(images.get(i), center, paths.get(i).toAbsolutePath().normalize().toString()));
        imported++;
      }
      model.replaceFrames(entry.getKey().sequence, entry.getKey().direction, frames);
    }

    if (model.isEmpty()) {
      throw new IOException("None of the matching PNG files could be decoded.");
    }
    return new ImportResult(model, imported, model.getPopulatedCellCount(), warnings);
  }

  public static int exportDirectory(CreatureAnimationModel model, Path output, boolean overwrite) throws IOException {
    if (model == null || model.isEmpty()) {
      throw new IOException("There are no source frames to export.");
    }
    if (output == null) {
      throw new IOException("No PNG output directory was selected.");
    }
    Files.createDirectories(output);

    final StringBuilder centers = new StringBuilder("file,center_x,center_y\n");
    int count = 0;
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        final List<AnimationFrame> frames = model.getFrames(sequence, direction);
        for (int index = 0; index < frames.size(); index++) {
          final String name = String.format(Locale.ENGLISH, "%s_%s_%03d.png", sequence.getCode(),
              direction.getCode(), index);
          final Path target = output.resolve(name);
          if (!overwrite && Files.exists(target)) {
            throw new IOException("Source export would overwrite " + target + ".");
          }
          if (!ImageIO.write(frames.get(index).getImage(), "png", target.toFile())) {
            throw new IOException("No PNG writer is available for " + target + ".");
          }
          final Point center = frames.get(index).getCenter();
          centers.append(name).append(',').append(center.x).append(',').append(center.y).append('\n');
          count++;
        }
      }
    }

    final Path centersPath = output.resolve(CENTERS_FILE);
    final Path formatPath = output.resolve(FORMAT_FILE);
    if (!overwrite && (Files.exists(centersPath) || Files.exists(formatPath))) {
      throw new IOException("Source export would overwrite its metadata files in " + output + ".");
    }
    Files.write(centersPath, centers.toString().getBytes(StandardCharsets.UTF_8));
    Files.write(formatPath, getFormatDocumentation().getBytes(StandardCharsets.UTF_8));
    return count;
  }

  static ParsedName parseName(Path root, Path path) {
    final String fileName = path.getFileName().toString();
    Matcher matcher = FLAT_NAME.matcher(fileName);
    if (matcher.matches()) {
      return new ParsedName(Sequence.fromCode(matcher.group(1)), Direction.fromCode(matcher.group(2)),
          Integer.parseInt(matcher.group(3)));
    }

    matcher = FRAME_NUMBER.matcher(fileName);
    if (!matcher.matches()) {
      return null;
    }
    final int frameIndex = Integer.parseInt(matcher.group(1));
    final Path relative = root.relativize(path);
    if (relative.getNameCount() >= 3) {
      final String sequenceName = relative.getName(relative.getNameCount() - 3).toString();
      final String directionName = relative.getName(relative.getNameCount() - 2).toString();
      final Sequence sequence = Sequence.fromCode(sequenceName);
      final Direction direction = Direction.fromCode(directionName);
      if (sequence != null && direction != null) {
        return new ParsedName(sequence, direction, frameIndex);
      }
    }
    if (relative.getNameCount() >= 2) {
      final String folder = relative.getName(relative.getNameCount() - 2).toString();
      matcher = COMBINED_FOLDER.matcher(folder);
      if (matcher.matches()) {
        return new ParsedName(Sequence.fromCode(matcher.group(1)), Direction.fromCode(matcher.group(2)), frameIndex);
      }
    }
    return null;
  }

  private static Map<String, Point> readCenters(Path file, List<String> warnings) throws IOException {
    final Map<String, Point> result = new HashMap<>();
    if (!Files.isRegularFile(file)) {
      return result;
    }
    final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
      final String line = lines.get(lineNumber).trim();
      if (line.isEmpty() || line.startsWith("#") || line.toLowerCase(Locale.ENGLISH).startsWith("file,")) {
        continue;
      }
      final String[] values = line.split(",", -1);
      if (values.length != 3) {
        warnings.add(CENTERS_FILE + " line " + (lineNumber + 1) + " is malformed and was ignored.");
        continue;
      }
      try {
        result.put(normalize(values[0]), new Point(Integer.parseInt(values[1].trim()),
            Integer.parseInt(values[2].trim())));
      } catch (NumberFormatException e) {
        warnings.add(CENTERS_FILE + " line " + (lineNumber + 1) + " has an invalid center and was ignored.");
      }
    }
    return result;
  }

  private static Point inferSharedCenter(List<BufferedImage> images) {
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int widest = 0;
    int tallest = 0;
    for (final BufferedImage image : images) {
      widest = Math.max(widest, image.getWidth());
      tallest = Math.max(tallest, image.getHeight());
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < image.getWidth(); x++) {
          if (((image.getRGB(x, y) >>> 24) & 0xff) != 0) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
          }
        }
      }
    }
    if (minX == Integer.MAX_VALUE) {
      return new Point(widest / 2, Math.max(0, tallest - 1));
    }
    return new Point((minX + maxX + 1) / 2, Math.min(tallest - 1, maxY + 1));
  }

  private static BufferedImage toArgb(BufferedImage source) {
    if (source.getType() == BufferedImage.TYPE_INT_ARGB || source.getColorModel() instanceof IndexColorModel) {
      return source;
    }
    final BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = result.createGraphics();
    try {
      g.setComposite(AlphaComposite.Src);
      g.drawImage(source, 0, 0, null);
    } finally {
      g.dispose();
    }
    return result;
  }

  private static String normalize(Path path) {
    return normalize(path.toString());
  }

  private static String normalize(String path) {
    return path.trim().replace('\\', '/').toLowerCase(Locale.ENGLISH);
  }

  private static String getFormatDocumentation() {
    return "Near Infinity Creature Animation Creator - PNG source format\n"
        + "==============================================================\n\n"
        + "Flat filename form:\n"
        + "  <ACTION>_<DIRECTION>_<FRAME>.png\n"
        + "  Example: WK_S_000.png\n\n"
        + "Nested forms are also accepted:\n"
        + "  <ACTION>/<DIRECTION>/<FRAME>.png\n"
        + "  <ACTION>_<DIRECTION>/<FRAME>.png\n\n"
        + "Actions: WK SC SD GH DE TW SL GU A1 A2 A3 A4 A5 SP CA\n"
        + "Stored directions: S SSW SW WSW W WNW NW NNW N\n"
        + "Eastern directions are mirrored by the Infinity Engine.\n\n"
        + "centers.csv stores the BAM center point for each frame. If it is absent, Near Infinity infers a shared "
        + "ground center from the opaque bounds of each action/direction group.\n";
  }
}
