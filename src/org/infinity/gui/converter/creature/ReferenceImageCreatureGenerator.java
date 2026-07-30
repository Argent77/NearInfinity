// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.CreatureEquipmentLibrary.EquipmentAsset;
import org.infinity.gui.converter.creature.CreatureEquipmentLibrary.RenderKind;
import org.infinity.gui.converter.creature.CreatureEquipmentLibrary.Slot;
import org.infinity.gui.converter.creature.CreatureTemplateLibrary.CreatureTemplate;
import org.infinity.gui.converter.creature.CreatureTemplateLibrary.MotionProfile;
import org.infinity.gui.converter.creature.CreatureTemplateLibrary.Socket;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.EquipmentSpec;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.gui.converter.creature.ProceduralCreatureGenerator.CreatureSize;

/**
 * Offline template renderer that turns one user-supplied illustration into a complete reference-free animation.
 *
 * <p>The selected template supplies motion and attachment assumptions that a static image cannot contain. The source
 * image supplies appearance only. No creature identity, palette or loadout is inferred.</p>
 */
public final class ReferenceImageCreatureGenerator {
  public static final int FRAME_SIZE = 176;
  private static final int RENDER_SCALE = 3;
  private static final int ALPHA_THRESHOLD = 12;
  private static final double BACKGROUND_TOLERANCE = 54.0;

  public enum BackgroundMode {
    AUTO("source.background.auto"),
    PRESERVE_ALPHA("source.background.preserve");

    private final String messageKey;

    BackgroundMode(String messageKey) {
      this.messageKey = messageKey;
    }

    @Override
    public String toString() {
      return CreatureAnimationMessages.get(messageKey);
    }
  }

  public interface ProgressListener {
    void onProgress(int completed, int total, Sequence sequence, Direction direction);
  }

  /** Explicit, reproducible generation request. */
  public static final class ReferenceSpec {
    private final BufferedImage sourceImage;
    private final CreatureTemplate template;
    private final Direction sourceFacing;
    private final BackgroundMode backgroundMode;
    private final CreatureSize size;
    private final EquipmentSpec equipment;
    private final EquipmentAsset armor;
    private final Color armorColor;
    private final long seed;

    public ReferenceSpec(BufferedImage sourceImage, CreatureTemplate template, Direction sourceFacing,
        BackgroundMode backgroundMode, CreatureSize size, EquipmentSpec equipment, EquipmentAsset armor,
        Color armorColor, long seed) {
      this.sourceImage = copyImage(Objects.requireNonNull(sourceImage, "sourceImage"));
      this.template = Objects.requireNonNull(template, "template");
      this.sourceFacing = Objects.requireNonNull(sourceFacing, "sourceFacing");
      this.backgroundMode = Objects.requireNonNull(backgroundMode, "backgroundMode");
      this.size = Objects.requireNonNull(size, "size");
      this.equipment = equipment;
      this.armor = armor;
      this.armorColor = Objects.requireNonNull(armorColor, "armorColor");
      this.seed = seed;
      validate();
    }

    private void validate() {
      if (sourceImage.getWidth() < 8 || sourceImage.getHeight() < 8) {
        throw new IllegalArgumentException("The reference image must be at least 8 by 8 pixels.");
      }
      if (equipment != null) {
        if (!template.supportsEquipment()) {
          throw new IllegalArgumentException(template.getLabel() + " does not define hand equipment sockets.");
        }
        if (equipment.hasOffhand() && template.getOffHandSocket() == null) {
          throw new IllegalArgumentException(template.getLabel() + " does not define an off-hand socket.");
        }
      }
      if (armor != null && armor.getSlot() != Slot.TORSO) {
        throw new IllegalArgumentException("Reference-image armor must come from the shared torso-equipment library.");
      }
    }

    public BufferedImage getSourceImage() {
      return copyImage(sourceImage);
    }

    public CreatureTemplate getTemplate() {
      return template;
    }

    public Direction getSourceFacing() {
      return sourceFacing;
    }

    public BackgroundMode getBackgroundMode() {
      return backgroundMode;
    }

    public CreatureSize getSize() {
      return size;
    }

    public EquipmentSpec getEquipment() {
      return equipment;
    }

    public EquipmentAsset getArmor() {
      return armor;
    }

    public Color getArmorColor() {
      return armorColor;
    }

    public long getSeed() {
      return seed;
    }
  }

  private ReferenceImageCreatureGenerator() {
  }

  public static CreatureAnimationModel generate(ReferenceSpec specification, ProgressListener listener) {
    final ReferenceSpec spec = Objects.requireNonNull(specification, "specification");
    final BufferedImage isolated = prepareSource(spec.sourceImage, spec.backgroundMode);
    final BufferedImage normalized = normalize(isolated, spec.template, spec.size);
    final BufferedImage equipped = applyArmor(normalized, spec.armor, spec.armorColor);
    final EnumMap<Sequence, List<BufferedImage>> posedFrames = createPoseFrames(equipped, spec.template);
    final CreatureAnimationModel result = new CreatureAnimationModel();
    final int total = Sequence.values().length * Direction.values().length;
    int completed = 0;
    for (final Sequence sequence : Sequence.values()) {
      final int frameCount = sequence.getSuggestedFrameCount();
      for (final Direction direction : Direction.values()) {
        final List<AnimationFrame> frames = new ArrayList<>(frameCount);
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
          final Pose pose = Pose.forFrame(spec.template, sequence, frameIndex, frameCount);
          final BufferedImage frame =
              render(spec, posedFrames.get(sequence).get(frameIndex), pose, sequence, direction, frameIndex);
          frames.add(new AnimationFrame(frame, getFrameCenter(spec.template),
              "reference-template:" + spec.template.getId() + ':' + sequence.getCode() + ':'
                  + direction.getCode() + ':' + frameIndex));
        }
        result.replaceFrames(sequence, direction, frames);
        completed++;
        if (listener != null) {
          listener.onProgress(completed, total, sequence, direction);
        }
      }
    }
    return result;
  }

  /**
   * Returns a transparent, tightly cropped copy using the same deterministic border-connected cleanup as generation.
   */
  public static BufferedImage isolateReferenceImage(BufferedImage source) {
    return prepareSource(Objects.requireNonNull(source, "source"), BackgroundMode.AUTO);
  }

  public static Point getFrameCenter(CreatureTemplate template) {
    Objects.requireNonNull(template, "template");
    return new Point(FRAME_SIZE / 2, (int) Math.round(FRAME_SIZE * template.getGround()));
  }

  private static EnumMap<Sequence, List<BufferedImage>> createPoseFrames(BufferedImage source,
      CreatureTemplate template) {
    final EnumMap<Sequence, List<BufferedImage>> result = new EnumMap<>(Sequence.class);
    for (final Sequence sequence : Sequence.values()) {
      final int frameCount = sequence.getSuggestedFrameCount();
      final List<BufferedImage> frames = new ArrayList<>(frameCount);
      for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
        final Pose pose = Pose.forFrame(template, sequence, frameIndex, frameCount);
        frames.add(deform(source, template, pose));
      }
      result.put(sequence, frames);
    }
    return result;
  }

  private static BufferedImage render(ReferenceSpec spec, BufferedImage sprite, Pose pose, Sequence sequence,
      Direction direction, int frameIndex) {
    final int workSize = FRAME_SIZE * RENDER_SCALE;
    final Point center = getFrameCenter(spec.template);
    final View view = View.forDirection(spec, direction);
    final BufferedImage shaded = applyViewShading(sprite, view.backAmount);
    final BufferedImage work = new BufferedImage(workSize, workSize, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = work.createGraphics();
    final AffineTransform transform = createTransform(spec.template, shaded, pose, view, center);
    try {
      graphics.scale(RENDER_SCALE, RENDER_SCALE);
      setQuality(graphics);
      drawShadow(graphics, spec.template, center, pose, view);
      if (pose.magic > 0.0) {
        drawMagicAura(graphics, center, sprite, pose, spec.seed);
      }
      graphics.drawImage(shaded, transform, null);
    } finally {
      graphics.dispose();
      shaded.flush();
    }

    final BufferedImage result = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D output = result.createGraphics();
    try {
      output.setComposite(AlphaComposite.Src);
      output.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      output.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      output.drawImage(work, 0, 0, FRAME_SIZE, FRAME_SIZE, null);
      output.setComposite(AlphaComposite.SrcOver);
      if (spec.equipment != null) {
        drawEquipment(output, spec, pose, sequence, direction, frameIndex, sprite, transform, center);
      }
    } finally {
      output.dispose();
      work.flush();
    }
    return result;
  }

  private static void drawEquipment(Graphics2D graphics, ReferenceSpec spec, Pose pose, Sequence sequence,
      Direction direction, int frameIndex, BufferedImage sprite, AffineTransform transform, Point center) {
    final EquipmentSpec loadout = spec.equipment;
    if (loadout.hasOffhand()) {
      drawEquipmentItem(graphics, loadout.forTarget(loadout.getTargetOffhand()), spec.template.getOffHandSocket(),
          false, spec, pose, sequence, direction, frameIndex, sprite, transform, center);
    }
    drawEquipmentItem(graphics, loadout.forTarget(loadout.getTargetWeapon()), spec.template.getMainHandSocket(),
        true, spec, pose, sequence, direction, frameIndex, sprite, transform, center);
  }

  private static void drawEquipmentItem(Graphics2D graphics, EquipmentSpec item, Socket socket, boolean mainHand,
      ReferenceSpec spec, Pose pose, Sequence sequence, Direction direction, int frameIndex, BufferedImage sprite,
      AffineTransform transform, Point center) {
    final double baseAngle = Math.toRadians(socket.getAngle() + (mainHand ? pose.mainHandAngle : pose.offHandAngle));
    final Point2D.Double attachment = new Point2D.Double(socket.getX() * sprite.getWidth(),
        socket.getY() * sprite.getHeight());
    final Point2D.Double transformedAttachment = new Point2D.Double();
    transform.transform(attachment, transformedAttachment);
    final Point2D.Double vector = new Point2D.Double(Math.cos(baseAngle), Math.sin(baseAngle));
    final Point2D.Double transformedVector = new Point2D.Double();
    transform.deltaTransform(vector, transformedVector);
    final double angle = Math.atan2(transformedVector.y, transformedVector.x);
    final double referenceLength = Math.max(18.0, Math.min(sprite.getWidth(), sprite.getHeight()) * 0.34);
    final long itemSeed = mixSeed(spec.seed, sequence.ordinal(), direction.ordinal(), frameIndex, mainHand ? 1 : 2);
    final AnimationFrame layer = EquipmentOverlayGenerator.renderStandalone(item,
        transformedAttachment.x - center.x, transformedAttachment.y - center.y, angle, referenceLength, itemSeed);
    graphics.drawImage(layer.getImage(), center.x - layer.getCenter().x, center.y - layer.getCenter().y, null);
  }

  private static AffineTransform createTransform(CreatureTemplate template, BufferedImage sprite, Pose pose, View view,
      Point center) {
    final AffineTransform transform = new AffineTransform();
    transform.translate(center.x + pose.moveX * view.screenX, center.y + pose.moveY);
    transform.rotate(pose.rotation * view.rotationSign);
    transform.shear(view.shear, 0.0);
    transform.scale(view.mirrored ? -view.horizontalScale * pose.scaleX : view.horizontalScale * pose.scaleX,
        pose.scaleY);
    transform.translate(-sprite.getWidth() / 2.0, -sprite.getHeight());
    return transform;
  }

  private static void drawShadow(Graphics2D graphics, CreatureTemplate template, Point center, Pose pose, View view) {
    final double baseWidth = FRAME_SIZE * template.getTargetWidth() * (0.50 + 0.22 * view.horizontalScale);
    final double width = baseWidth * (1.0 - pose.groundFade * 0.34);
    final double height = Math.max(5.0, width * 0.16);
    final int alpha = clamp((int) Math.round(74.0 * (1.0 - pose.groundFade * 0.70)), 0, 92);
    graphics.setColor(new Color(10, 12, 17, alpha));
    graphics.fill(new Ellipse2D.Double(center.x - width / 2.0 + pose.moveX * view.screenX * 0.20,
        center.y - height / 2.0 + 1.0, width, height));
  }

  private static void drawMagicAura(Graphics2D graphics, Point center, BufferedImage sprite, Pose pose, long seed) {
    final double pulse = 0.82 + 0.18 * Math.sin(pose.phase * Math.PI * 2.0 + (seed & 7) * 0.23);
    final double radius = Math.max(sprite.getWidth(), sprite.getHeight()) * (0.34 + pose.magic * 0.10) * pulse;
    graphics.setColor(new Color(94, 83, 255, clamp((int) Math.round(30 + pose.magic * 42), 0, 86)));
    graphics.fill(new Ellipse2D.Double(center.x - radius, center.y - sprite.getHeight() * 0.56 - radius * 0.52,
        radius * 2.0, radius * 1.04));
    graphics.setColor(new Color(178, 136, 255, clamp((int) Math.round(pose.magic * 112), 0, 120)));
    graphics.draw(new Ellipse2D.Double(center.x - radius * 0.72,
        center.y - sprite.getHeight() * 0.56 - radius * 0.32, radius * 1.44, radius * 0.64));
  }

  private static BufferedImage prepareSource(BufferedImage source, BackgroundMode backgroundMode) {
    final BufferedImage argb = copyImage(source);
    if (backgroundMode == BackgroundMode.AUTO) {
      removeConnectedBackground(argb);
    }
    final java.awt.Rectangle bounds = findOpaqueBounds(argb);
    if (bounds == null || bounds.width < 3 || bounds.height < 3) {
      throw new IllegalArgumentException("The reference image contains no usable foreground after background cleanup.");
    }
    final int padding = 2;
    final int x = Math.max(0, bounds.x - padding);
    final int y = Math.max(0, bounds.y - padding);
    final int right = Math.min(argb.getWidth(), bounds.x + bounds.width + padding);
    final int bottom = Math.min(argb.getHeight(), bounds.y + bounds.height + padding);
    final BufferedImage result =
        new BufferedImage(Math.max(1, right - x), Math.max(1, bottom - y), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = result.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      graphics.drawImage(argb, -x, -y, null);
    } finally {
      graphics.dispose();
      argb.flush();
    }
    return result;
  }

  private static void removeConnectedBackground(BufferedImage image) {
    final int width = image.getWidth();
    final int height = image.getHeight();
    final int background = averageCorners(image);
    final boolean[] visited = new boolean[width * height];
    final int[] queue = new int[width * height];
    int head = 0;
    int tail = 0;
    for (int x = 0; x < width; x++) {
      tail = enqueueBackground(image, background, x, 0, visited, queue, tail);
      tail = enqueueBackground(image, background, x, height - 1, visited, queue, tail);
    }
    for (int y = 1; y < height - 1; y++) {
      tail = enqueueBackground(image, background, 0, y, visited, queue, tail);
      tail = enqueueBackground(image, background, width - 1, y, visited, queue, tail);
    }
    final int[] dx = { -1, 1, 0, 0 };
    final int[] dy = { 0, 0, -1, 1 };
    while (head < tail) {
      final int index = queue[head++];
      final int x = index % width;
      final int y = index / width;
      image.setRGB(x, y, 0);
      for (int direction = 0; direction < dx.length; direction++) {
        final int nx = x + dx[direction];
        final int ny = y + dy[direction];
        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
          tail = enqueueBackground(image, background, nx, ny, visited, queue, tail);
        }
      }
    }
    featherBoundary(image, visited, background);
  }

  private static int enqueueBackground(BufferedImage image, int background, int x, int y, boolean[] visited,
      int[] queue, int tail) {
    final int index = y * image.getWidth() + x;
    if (visited[index]) {
      return tail;
    }
    final int pixel = image.getRGB(x, y);
    if ((pixel >>> 24) == 0 || colorDistance(pixel, background) <= BACKGROUND_TOLERANCE) {
      visited[index] = true;
      queue[tail++] = index;
    }
    return tail;
  }

  private static void featherBoundary(BufferedImage image, boolean[] removed, int background) {
    final int width = image.getWidth();
    final int height = image.getHeight();
    for (int y = 1; y < height - 1; y++) {
      for (int x = 1; x < width - 1; x++) {
        final int index = y * width + x;
        if (removed[index] || !hasRemovedNeighbor(removed, width, x, y)) {
          continue;
        }
        final int pixel = image.getRGB(x, y);
        final double distance = colorDistance(pixel, background);
        if (distance < BACKGROUND_TOLERANCE + 54.0) {
          final int alpha = clamp((int) Math.round((distance - BACKGROUND_TOLERANCE) * 255.0 / 54.0), 0,
              pixel >>> 24);
          image.setRGB(x, y, (alpha << 24) | (pixel & 0x00ffffff));
        }
      }
    }
  }

  private static boolean hasRemovedNeighbor(boolean[] removed, int width, int x, int y) {
    return removed[y * width + x - 1] || removed[y * width + x + 1]
        || removed[(y - 1) * width + x] || removed[(y + 1) * width + x];
  }

  private static int averageCorners(BufferedImage image) {
    final int radius = Math.max(1, Math.min(4, Math.min(image.getWidth(), image.getHeight()) / 8));
    long red = 0;
    long green = 0;
    long blue = 0;
    int count = 0;
    for (int y = 0; y < radius; y++) {
      for (int x = 0; x < radius; x++) {
        final int[] xs = { x, image.getWidth() - 1 - x };
        final int[] ys = { y, image.getHeight() - 1 - y };
        for (final int sampleX : xs) {
          for (final int sampleY : ys) {
            final int pixel = image.getRGB(sampleX, sampleY);
            red += (pixel >>> 16) & 0xff;
            green += (pixel >>> 8) & 0xff;
            blue += pixel & 0xff;
            count++;
          }
        }
      }
    }
    return 0xff000000 | ((int) (red / count) << 16) | ((int) (green / count) << 8) | (int) (blue / count);
  }

  private static double colorDistance(int first, int second) {
    final int red = ((first >>> 16) & 0xff) - ((second >>> 16) & 0xff);
    final int green = ((first >>> 8) & 0xff) - ((second >>> 8) & 0xff);
    final int blue = (first & 0xff) - (second & 0xff);
    return Math.sqrt(red * red + green * green + blue * blue);
  }

  private static BufferedImage normalize(BufferedImage source, CreatureTemplate template, CreatureSize size) {
    final double sizeScale = size.getScale();
    final int maximumWidth = Math.max(8, (int) Math.round(FRAME_SIZE * template.getTargetWidth() * sizeScale));
    final int maximumHeight = Math.max(8, (int) Math.round(FRAME_SIZE * template.getTargetHeight() * sizeScale));
    final double scale = Math.min((double) maximumWidth / source.getWidth(),
        (double) maximumHeight / source.getHeight());
    final int width = Math.max(3, (int) Math.round(source.getWidth() * scale));
    final int height = Math.max(3, (int) Math.round(source.getHeight() * scale));
    final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = result.createGraphics();
    try {
      setQuality(graphics);
      graphics.setComposite(AlphaComposite.Src);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
      source.flush();
    }
    return result;
  }

  private static BufferedImage applyArmor(BufferedImage source, EquipmentAsset armor, Color color) {
    if (armor == null) {
      return source;
    }
    final BufferedImage result = copyImage(source);
    final int start = clamp((int) Math.floor(result.getHeight() * armor.getCoverageStart()), 0,
        result.getHeight() - 1);
    final int end = clamp((int) Math.ceil(result.getHeight() * armor.getCoverageEnd()), start + 1,
        result.getHeight());
    final double blend = 0.24 + armor.getMetallic() * 0.30;
    for (int y = start; y < end; y++) {
      final double verticalLight = 0.88 + 0.18 * Math.sin((double) (y - start) / Math.max(1, end - start) * Math.PI);
      for (int x = 0; x < result.getWidth(); x++) {
        final int pixel = result.getRGB(x, y);
        final int alpha = pixel >>> 24;
        if (alpha < ALPHA_THRESHOLD) {
          continue;
        }
        final int red = clamp((int) Math.round((((pixel >>> 16) & 0xff) * (1.0 - blend)
            + color.getRed() * blend) * verticalLight), 0, 255);
        final int green = clamp((int) Math.round((((pixel >>> 8) & 0xff) * (1.0 - blend)
            + color.getGreen() * blend) * verticalLight), 0, 255);
        final int blue = clamp((int) Math.round(((pixel & 0xff) * (1.0 - blend)
            + color.getBlue() * blend) * verticalLight), 0, 255);
        result.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
      }
    }
    addArmorDetail(result, armor, color, start, end);
    source.flush();
    return result;
  }

  private static void addArmorDetail(BufferedImage image, EquipmentAsset armor, Color color, int start, int end) {
    final Graphics2D graphics = image.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.SrcAtop);
      graphics.setClip(0, start, image.getWidth(), end - start);
      final Color dark = mix(color, Color.BLACK, 0.46, 118);
      final Color light = mix(color, Color.WHITE, 0.46, 104);
      final int spacing = Math.max(3, (int) Math.round(8.0 - armor.getDetail() * 4.0));
      switch (armor.getRenderKind()) {
        case MAIL_ARMOR:
          graphics.setColor(light);
          for (int y = start; y < end; y += spacing) {
            for (int x = (y / spacing % 2) * (spacing / 2); x < image.getWidth(); x += spacing) {
              graphics.drawOval(x, y, spacing, Math.max(2, spacing / 2));
            }
          }
          break;
        case SCALE_ARMOR:
          graphics.setColor(dark);
          for (int y = start; y < end; y += spacing + 1) {
            for (int x = (y / spacing % 2) * (spacing / 2); x < image.getWidth(); x += spacing) {
              graphics.drawArc(x, y, spacing + 1, spacing, 0, -180);
            }
          }
          break;
        case PLATE_ARMOR:
          graphics.setColor(dark);
          graphics.drawLine(image.getWidth() / 2, start, image.getWidth() / 2, end);
          graphics.drawLine(image.getWidth() / 5, (start + end) / 2,
              image.getWidth() * 4 / 5, (start + end) / 2);
          graphics.setColor(light);
          graphics.drawLine(image.getWidth() / 2 - 2, start + 2, image.getWidth() / 2 - 2, end - 2);
          break;
        case LEATHER_ARMOR:
          graphics.setColor(dark);
          for (int y = start + spacing; y < end; y += spacing * 2) {
            graphics.drawLine(image.getWidth() / 5, y, image.getWidth() * 4 / 5, y);
          }
          break;
        case TEXTILE_ARMOR:
        default:
          graphics.setColor(light);
          for (int y = start; y < end; y += spacing * 2) {
            graphics.drawLine(0, y, image.getWidth(), y);
          }
          break;
      }
    } finally {
      graphics.dispose();
    }
  }

  private static BufferedImage deform(BufferedImage source, CreatureTemplate template, Pose pose) {
    if (Math.abs(pose.bend) < 0.01 && Math.abs(pose.wave) < 0.01) {
      return copyImage(source);
    }
    final int width = source.getWidth();
    final int height = source.getHeight();
    final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final double profileScale = deformationScale(template.getMotionProfile());
    for (int y = 0; y < height; y++) {
      final double normalizedY = height > 1 ? (double) y / (height - 1) : 0.0;
      final double torsoCurve = Math.sin(normalizedY * Math.PI);
      final double locomotionCurve = Math.sin(normalizedY * Math.PI * 2.0 + pose.phase * Math.PI * 2.0);
      final double sourceOffset = (pose.bend * torsoCurve + pose.wave * locomotionCurve
          * (0.25 + normalizedY * 0.75)) * profileScale;
      for (int x = 0; x < width; x++) {
        result.setRGB(x, y, sampleBilinear(source, x - sourceOffset, y));
      }
    }
    return result;
  }

  private static double deformationScale(MotionProfile profile) {
    switch (profile) {
      case SERPENT:
      case AQUATIC:
        return 1.42;
      case MULTI_NECK:
      case AMORPHOUS:
        return 1.24;
      case AVIAN:
      case BAT:
        return 1.12;
      case GIANT:
      case ROOTED:
        return 0.62;
      case HEAVY_HUMANOID:
      case URSINE:
      case BEETLE:
        return 0.76;
      default:
        return 1.0;
    }
  }

  private static int sampleBilinear(BufferedImage image, double x, double y) {
    if (x < 0.0 || y < 0.0 || x > image.getWidth() - 1.0 || y > image.getHeight() - 1.0) {
      return 0;
    }
    final int x0 = (int) Math.floor(x);
    final int y0 = (int) Math.floor(y);
    final int x1 = Math.min(image.getWidth() - 1, x0 + 1);
    final int y1 = Math.min(image.getHeight() - 1, y0 + 1);
    final double fx = x - x0;
    final double fy = y - y0;
    final int first = interpolateColor(image.getRGB(x0, y0), image.getRGB(x1, y0), fx);
    final int second = interpolateColor(image.getRGB(x0, y1), image.getRGB(x1, y1), fx);
    return interpolateColor(first, second, fy);
  }

  private static int interpolateColor(int first, int second, double amount) {
    final double inverse = 1.0 - amount;
    final int alpha = clamp((int) Math.round((first >>> 24) * inverse + (second >>> 24) * amount), 0, 255);
    final int red = clamp((int) Math.round(((first >>> 16) & 0xff) * inverse
        + ((second >>> 16) & 0xff) * amount), 0, 255);
    final int green = clamp((int) Math.round(((first >>> 8) & 0xff) * inverse
        + ((second >>> 8) & 0xff) * amount), 0, 255);
    final int blue = clamp((int) Math.round((first & 0xff) * inverse + (second & 0xff) * amount), 0, 255);
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }

  private static BufferedImage applyViewShading(BufferedImage source, double amount) {
    if (amount <= 0.001) {
      return copyImage(source);
    }
    final BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    final double scale = 1.0 - amount * 0.22;
    final double cool = amount * 8.0;
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        final int pixel = source.getRGB(x, y);
        final int alpha = pixel >>> 24;
        final int red = clamp((int) Math.round(((pixel >>> 16) & 0xff) * scale), 0, 255);
        final int green = clamp((int) Math.round(((pixel >>> 8) & 0xff) * scale), 0, 255);
        final int blue = clamp((int) Math.round((pixel & 0xff) * scale + cool), 0, 255);
        result.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
      }
    }
    return result;
  }

  private static java.awt.Rectangle findOpaqueBounds(BufferedImage image) {
    int minimumX = image.getWidth();
    int minimumY = image.getHeight();
    int maximumX = -1;
    int maximumY = -1;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) >= ALPHA_THRESHOLD) {
          minimumX = Math.min(minimumX, x);
          minimumY = Math.min(minimumY, y);
          maximumX = Math.max(maximumX, x);
          maximumY = Math.max(maximumY, y);
        }
      }
    }
    return maximumX >= minimumX && maximumY >= minimumY
        ? new java.awt.Rectangle(minimumX, minimumY, maximumX - minimumX + 1, maximumY - minimumY + 1) : null;
  }

  private static BufferedImage copyImage(BufferedImage source) {
    final BufferedImage result =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = result.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      graphics.drawImage(source, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return result;
  }

  private static void setQuality(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }

  private static Color mix(Color first, Color second, double amount, int alpha) {
    final double ratio = Math.max(0.0, Math.min(1.0, amount));
    return new Color((int) Math.round(first.getRed() * (1.0 - ratio) + second.getRed() * ratio),
        (int) Math.round(first.getGreen() * (1.0 - ratio) + second.getGreen() * ratio),
        (int) Math.round(first.getBlue() * (1.0 - ratio) + second.getBlue() * ratio), clamp(alpha, 0, 255));
  }

  private static long mixSeed(long seed, int sequence, int direction, int frame, int layer) {
    long result = seed ^ 0x9e3779b97f4a7c15L;
    result ^= (sequence + 1L) * 0xbf58476d1ce4e5b9L;
    result ^= (direction + 1L) * 0x94d049bb133111ebL;
    result ^= (frame + 1L) * 0x2545f4914f6cdd1dL;
    result ^= layer * 0xd6e8feb86659fd93L;
    return result;
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static double smooth(double value) {
    final double clamped = Math.max(0.0, Math.min(1.0, value));
    return clamped * clamped * (3.0 - 2.0 * clamped);
  }

  private static final class View {
    private final double horizontalScale;
    private final double shear;
    private final double backAmount;
    private final double screenX;
    private final double rotationSign;
    private final boolean mirrored;

    private View(double horizontalScale, double shear, double backAmount, double screenX, double rotationSign,
        boolean mirrored) {
      this.horizontalScale = horizontalScale;
      this.shear = shear;
      this.backAmount = backAmount;
      this.screenX = screenX;
      this.rotationSign = rotationSign;
      this.mirrored = mirrored;
    }

    private static View forDirection(ReferenceSpec spec, Direction direction) {
      final double relative = (direction.getCycleOffset() - spec.sourceFacing.getCycleOffset()) * Math.PI / 8.0;
      final double cosine = Math.cos(relative);
      final double horizontalScale = spec.template.getDepthScale()
          + (1.0 - spec.template.getDepthScale()) * Math.abs(cosine);
      final double shear = Math.sin(relative) * spec.template.getTurnBias();
      final double back = Math.max(0.0, -cosine);
      final double world = direction.getCycleOffset() * Math.PI / 8.0;
      final double screenX = -Math.sin(world);
      final boolean mirrored = cosine < 0.0;
      return new View(horizontalScale, shear, back, screenX, mirrored ? -1.0 : 1.0, mirrored);
    }
  }

  private static final class Pose {
    private double phase;
    private double moveX;
    private double moveY;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private double rotation;
    private double bend;
    private double wave;
    private double magic;
    private double groundFade;
    private double mainHandAngle;
    private double offHandAngle;

    private static Pose forFrame(CreatureTemplate template, Sequence sequence, int frameIndex, int frameCount) {
      final Pose pose = new Pose();
      pose.phase = frameCount > 0 ? (double) frameIndex / frameCount : 0.0;
      final double progress = frameCount > 1 ? (double) frameIndex / (frameCount - 1) : 0.0;
      final double cycle = Math.sin(pose.phase * Math.PI * 2.0);
      final double pulse = Math.sin(progress * Math.PI);
      switch (sequence) {
        case WALK:
          pose.moveY = -Math.abs(cycle) * template.getBobAmplitude();
          pose.bend = cycle * template.getStrideAmplitude() * 0.34;
          pose.wave = cycle * template.getStrideAmplitude() * 0.20;
          pose.rotation = Math.toRadians(cycle * 1.8);
          pose.mainHandAngle = cycle * 8.0;
          pose.offHandAngle = -cycle * 8.0;
          break;
        case STANCE:
          pose.moveY = cycle * template.getBobAmplitude() * 0.42;
          pose.bend = cycle * 0.8;
          pose.mainHandAngle = cycle * 2.5;
          pose.offHandAngle = -cycle * 2.5;
          break;
        case STAND:
          pose.moveY = cycle * template.getBobAmplitude() * 0.24;
          pose.scaleX = 1.0 + cycle * 0.008;
          pose.scaleY = 1.0 - cycle * 0.008;
          pose.bend = cycle * 0.42;
          break;
        case GET_HIT:
          pose.moveX = -template.getAttackReach() * pulse * 0.42;
          pose.moveY = -pulse * template.getBobAmplitude();
          pose.rotation = Math.toRadians(-8.0 * pulse);
          pose.bend = -pulse * template.getStrideAmplitude() * 0.48;
          pose.mainHandAngle = -pulse * 18.0;
          pose.offHandAngle = pulse * 15.0;
          break;
        case DIE:
          final double fall = smooth(progress);
          pose.rotation = Math.toRadians(template.getFallAngle() * fall);
          pose.moveY = fall * template.getTargetHeight() * FRAME_SIZE * 0.22;
          pose.scaleY = 1.0 - fall * 0.18;
          pose.bend = -fall * template.getStrideAmplitude() * 0.65;
          pose.groundFade = fall;
          pose.mainHandAngle = fall * 28.0;
          pose.offHandAngle = -fall * 24.0;
          break;
        case TWITCH:
          pose.rotation = Math.toRadians(template.getFallAngle() * 0.96 + cycle * 2.8);
          pose.moveY = template.getTargetHeight() * FRAME_SIZE * 0.21 - Math.abs(cycle) * 1.5;
          pose.scaleY = 0.83 + Math.abs(cycle) * 0.02;
          pose.bend = cycle * 2.2;
          pose.groundFade = 0.96;
          break;
        case SLEEP:
          pose.rotation = Math.toRadians(template.getFallAngle());
          pose.moveY = template.getTargetHeight() * FRAME_SIZE * 0.22;
          pose.scaleX = 1.0 + cycle * 0.006;
          pose.scaleY = 0.82 - cycle * 0.006;
          pose.groundFade = 1.0;
          break;
        case GET_UP:
          final double down = 1.0 - smooth(progress);
          pose.rotation = Math.toRadians(template.getFallAngle() * down);
          pose.moveY = down * template.getTargetHeight() * FRAME_SIZE * 0.22;
          pose.scaleY = 1.0 - down * 0.18;
          pose.bend = down * template.getStrideAmplitude() * 0.55;
          pose.groundFade = down;
          break;
        case ATTACK_1:
        case ATTACK_2:
        case ATTACK_3:
        case ATTACK_4:
        case ATTACK_5:
          final int variant = sequence.ordinal() - Sequence.ATTACK_1.ordinal();
          final double side = (variant % 2 == 0) ? 1.0 : -1.0;
          pose.moveX = template.getAttackReach() * pulse * (0.72 + variant * 0.045);
          pose.moveY = -pulse * (1.6 + variant * 0.35);
          pose.scaleX = 1.0 + pulse * 0.035;
          pose.scaleY = 1.0 - pulse * 0.035;
          pose.rotation = Math.toRadians(side * pulse * (4.0 + variant * 1.2));
          pose.bend = side * pulse * template.getStrideAmplitude() * (0.50 + variant * 0.04);
          pose.wave = pulse * template.getWingAmplitude() * 0.20;
          pose.mainHandAngle = side * (-58.0 + variant * 7.0) * pulse;
          pose.offHandAngle = -side * (34.0 + variant * 5.0) * pulse;
          break;
        case CONJURE:
          pose.magic = Math.min(1.0, progress * 1.55);
          pose.moveY = -pulse * (template.getBobAmplitude() + template.getWingAmplitude() * 0.10);
          pose.scaleX = 1.0 + pulse * 0.025;
          pose.scaleY = 1.0 + pulse * 0.025;
          pose.bend = cycle * 1.4;
          pose.mainHandAngle = -pulse * 28.0;
          pose.offHandAngle = pulse * 28.0;
          break;
        case CAST:
          pose.magic = pulse;
          pose.moveX = template.getAttackReach() * pulse * 0.30;
          pose.moveY = -pulse * (template.getBobAmplitude() + 1.6);
          pose.scaleX = 1.0 + pulse * 0.04;
          pose.scaleY = 1.0 + pulse * 0.02;
          pose.bend = pulse * template.getStrideAmplitude() * 0.24;
          pose.mainHandAngle = -pulse * 42.0;
          pose.offHandAngle = pulse * 38.0;
          break;
        default:
          break;
      }
      if (template.getWingAmplitude() > 0.0
          && (sequence == Sequence.WALK || sequence == Sequence.STANCE || sequence == Sequence.STAND)) {
        pose.scaleY *= 1.0 + Math.abs(cycle) * template.getWingAmplitude() / 360.0;
        pose.scaleX *= 1.0 - Math.abs(cycle) * template.getWingAmplitude() / 540.0;
        pose.wave += cycle * template.getWingAmplitude() * 0.18;
      }
      return pose;
    }
  }
}
