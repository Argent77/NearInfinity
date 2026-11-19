// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;

/**
 * Replaces a synchronized weapon layer with deterministic vector-like equipment artwork.
 *
 * <p>The existing overlay provides the per-frame grip, angle, length and timing. The generated result deliberately
 * remains an editable draft; it does not claim to infer hands, occlusion or unseen geometry from the avatar alone.</p>
 */
public final class EquipmentOverlayGenerator {
  private static final int WORK_SIZE = 384;
  private static final int WORK_ORIGIN = WORK_SIZE / 2;
  private static final int ALPHA_THRESHOLD = 16;

  /** Engine attack-resource classes used by procedural equipment. */
  public enum AttackStyle {
    ONE_HANDED,
    TWO_HANDED,
    BOW,
    CROSSBOW,
    SLING,
    SHIELD
  }

  /** Equipment silhouettes supported by the offline overlay renderer. */
  public enum WeaponType {
    SICKLE("sickle"),
    SCYTHE("scythe"),
    SWORD("sword"),
    SCIMITAR("scimitar"),
    WAKIZASHI("wakizashi"),
    NINJATO("ninjato"),
    KATANA("katana"),
    GREATSWORD("greatsword"),
    DAGGER("dagger"),
    AXE("axe"),
    BATTLEAXE("battleaxe"),
    MACE("mace"),
    HAMMER("hammer"),
    ONE_HANDED_SPEAR("one-handed-spear"),
    SPEAR("spear"),
    HALBERD("halberd"),
    GLAIVE("glaive"),
    TRIDENT("trident"),
    STAFF("staff"),
    CLUB("club"),
    FLAIL("flail"),
    SHORTBOW("shortbow"),
    LONGBOW("longbow"),
    BOW("bow"),
    LIGHT_CROSSBOW("light-crossbow"),
    HEAVY_CROSSBOW("heavy-crossbow"),
    CROSSBOW("crossbow"),
    SLING("sling"),
    WHIP("whip"),
    BUCKLER("buckler"),
    SMALL_SHIELD("small-shield"),
    MEDIUM_SHIELD("medium-shield"),
    LARGE_SHIELD("large-shield");

    private final String assetId;

    WeaponType(String assetId) {
      this.assetId = assetId;
    }

    public CreatureEquipmentLibrary.EquipmentAsset getAsset() {
      return CreatureEquipmentLibrary.getById(assetId);
    }

    public String getLabel() {
      return getAsset().getLabel();
    }

    public String getSuggestedAppearanceCode() {
      return getAsset().getAppearanceCode();
    }

    public double getLengthScale() {
      return getAsset().getLengthScale();
    }

    public boolean isTwoHanded() {
      final AttackStyle style = getAttackStyle();
      return style == AttackStyle.TWO_HANDED || style == AttackStyle.BOW || style == AttackStyle.CROSSBOW;
    }

    public boolean isShield() {
      return getAttackStyle() == AttackStyle.SHIELD;
    }

    public boolean isOneHandedMelee() {
      return getAttackStyle() == AttackStyle.ONE_HANDED;
    }

    public boolean allowsShield() {
      final AttackStyle style = getAttackStyle();
      return style == AttackStyle.ONE_HANDED || style == AttackStyle.SLING;
    }

    public AttackStyle getAttackStyle() {
      return AttackStyle.valueOf(getAsset().getGripStyle().name());
    }

    public List<String> getAliases() {
      return getAsset().getAliases();
    }

    @Override
    public String toString() {
      return getLabel();
    }
  }

  public enum EquipmentSize {
    TINY("equipment.size.tiny", 0.72),
    SMALL("equipment.size.small", 0.86),
    STANDARD("equipment.size.standard", 1.0),
    LARGE("equipment.size.large", 1.1),
    HUGE("equipment.size.huge", 1.22);

    private final String messageKey;
    private final double scale;

    EquipmentSize(String messageKey, double scale) {
      this.messageKey = messageKey;
      this.scale = scale;
    }

    public double getScale() {
      return scale;
    }

    @Override
    public String toString() {
      return CreatureAnimationMessages.get(messageKey);
    }
  }

  /** Complete, language-independent equipment design. */
  public static final class EquipmentSpec {
    private final WeaponType sourceWeapon;
    private final WeaponType targetWeapon;
    private final WeaponType targetOffhand;
    private final Color metalColor;
    private final Color accentColor;
    private final Color glowColor;
    private final boolean glowing;
    private final boolean ornate;
    private final EquipmentSize size;

    public EquipmentSpec(WeaponType sourceWeapon, WeaponType targetWeapon, WeaponType targetOffhand,
        Color metalColor, Color accentColor, Color glowColor, boolean glowing, boolean ornate, EquipmentSize size) {
      this(sourceWeapon, targetWeapon, targetOffhand, metalColor, accentColor, glowColor, glowing, ornate, size, true);
    }

    private EquipmentSpec(WeaponType sourceWeapon, WeaponType targetWeapon, WeaponType targetOffhand,
        Color metalColor, Color accentColor, Color glowColor, boolean glowing, boolean ornate, EquipmentSize size,
        boolean validateCombination) {
      if (validateCombination) {
        validateLoadout(targetWeapon, targetOffhand);
      }
      this.sourceWeapon = sourceWeapon;
      this.targetWeapon = Objects.requireNonNull(targetWeapon, "targetWeapon");
      this.targetOffhand = targetOffhand;
      this.metalColor = Objects.requireNonNull(metalColor, "metalColor");
      this.accentColor = Objects.requireNonNull(accentColor, "accentColor");
      this.glowColor = Objects.requireNonNull(glowColor, "glowColor");
      this.glowing = glowing;
      this.ornate = ornate;
      this.size = Objects.requireNonNull(size, "size");
    }

    public WeaponType getSourceWeapon() {
      return sourceWeapon;
    }

    public WeaponType getTargetWeapon() {
      return targetWeapon;
    }

    public WeaponType getTargetOffhand() {
      return targetOffhand;
    }

    public boolean hasOffhand() {
      return targetOffhand != null;
    }

    public boolean isTwoWeaponLoadout() {
      return targetOffhand != null && !targetOffhand.isShield();
    }

    public Color getMetalColor() {
      return metalColor;
    }

    public Color getAccentColor() {
      return accentColor;
    }

    public Color getGlowColor() {
      return glowColor;
    }

    public boolean isGlowing() {
      return glowing;
    }

    public boolean isOrnate() {
      return ornate;
    }

    public double getScale() {
      return size.getScale();
    }

    public EquipmentSize getSize() {
      return size;
    }

    public String getSummary() {
      final String source = sourceWeapon != null ? sourceWeapon.getLabel()
          : CreatureAnimationMessages.get("equipment.summary.sourceAutomatic");
      final String offhand = targetOffhand != null
          ? CreatureAnimationMessages.format("equipment.summary.offhand", targetOffhand.getLabel()) : "";
      final String glow = glowing ? CreatureAnimationMessages.get("equipment.summary.glowing") : "";
      final String decoration = ornate ? CreatureAnimationMessages.get("equipment.summary.ornate") : "";
      return CreatureAnimationMessages.format("equipment.summary", source, targetWeapon.getLabel(), offhand,
          size, glow + decoration);
    }

    public EquipmentSpec forTarget(WeaponType target) {
      if (target == null) {
        throw new IllegalArgumentException("A target equipment type is required.");
      }
      return new EquipmentSpec(null, target, null, metalColor, accentColor, glowColor, glowing, ornate, size, false);
    }
  }

  public interface ProgressListener {
    void progress(int completed, int total, Sequence sequence, Direction direction);
  }

  private EquipmentOverlayGenerator() {
  }

  /**
   * Generates a complete type {@code 0x7000} overlay model from an existing synchronized weapon overlay.
   *
   * @param sourceOverlay existing synchronized equipment layer
   * @param avatar        optional matching avatar frames, used to disambiguate which end of the source is the grip
   */
  public static CreatureAnimationModel generate(CreatureAnimationModel sourceOverlay, CreatureAnimationModel avatar,
      EquipmentSpec specification, long seed, ProgressListener listener) {
    if (sourceOverlay == null || sourceOverlay.isEmpty()) {
      throw new IllegalArgumentException("An existing synchronized equipment overlay is required.");
    }
    final EquipmentSpec spec = Objects.requireNonNull(specification, "specification");

    final CreatureAnimationModel result = new CreatureAnimationModel();
    final int total = Sequence.values().length * Direction.values().length;
    int completed = 0;
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        final List<AnimationFrame> sourceFrames = sourceOverlay.getFrames(sequence, direction);
        final List<AnimationFrame> avatarFrames = avatar != null
            ? avatar.resolveFrames(sequence, direction).getFrames() : Collections.<AnimationFrame>emptyList();
        final List<AnimationFrame> generated = new ArrayList<>(sourceFrames.size());
        for (int frameIndex = 0; frameIndex < sourceFrames.size(); frameIndex++) {
          final AnimationFrame source = sourceFrames.get(frameIndex);
          final AnimationFrame body = selectProportionalFrame(avatarFrames, frameIndex, sourceFrames.size());
          generated.add(renderReplacement(source, body, spec,
              mixSeed(seed, sequence.ordinal(), direction.ordinal(), frameIndex)));
        }
        if (!generated.isEmpty()) {
          result.replaceFrames(sequence, direction, generated);
        }
        completed++;
        if (listener != null) {
          listener.progress(completed, total, sequence, direction);
        }
      }
    }
    return result;
  }

  /**
   * Generates a family-aware overlay while retaining independent explicit eastern artwork where present.
   *
   * @param sourceOverlay existing synchronized weapon resources in canonical direction space
   * @param avatar        matching avatar resources used to identify the grip end of each source weapon
   */
  public static EquipmentOverlayModel generate(EquipmentOverlayModel sourceOverlay, EquipmentOverlayModel avatar,
      EquipmentSpec specification, long seed, ProgressListener listener) {
    final boolean explicitEastern = sourceOverlay != null && !sourceOverlay.getEasternModel().isEmpty();
    return generate(sourceOverlay, avatar, specification, seed, explicitEastern, listener);
  }

  public static EquipmentOverlayModel generate(EquipmentOverlayModel sourceOverlay, EquipmentOverlayModel avatar,
      EquipmentSpec specification, long seed, boolean explicitEastern, ProgressListener listener) {
    if (sourceOverlay == null || sourceOverlay.isEmpty()) {
      throw new IllegalArgumentException("An existing synchronized equipment overlay is required.");
    }
    final EquipmentSpec spec = Objects.requireNonNull(specification, "specification");

    final EquipmentOverlayModel result = new EquipmentOverlayModel();
    final int directionCount = explicitEastern ? 16 : Direction.values().length;
    final int total = Sequence.values().length * directionCount;
    int completed = 0;
    for (final Sequence sequence : Sequence.values()) {
      for (int directionIndex = 0; directionIndex < directionCount; directionIndex++) {
        final int variantCount = sourceOverlay.getResolvedVariantCount(sequence, directionIndex);
        for (int occurrence = 0; occurrence < variantCount; occurrence++) {
          final List<AnimationFrame> sourceFrames =
              sourceOverlay.resolveVariantFrames(sequence, directionIndex, occurrence);
          final List<AnimationFrame> avatarFrames = avatar != null
              ? avatar.resolveVariantFrames(sequence, directionIndex, occurrence)
              : Collections.<AnimationFrame>emptyList();
          final List<AnimationFrame> generated = new ArrayList<>(sourceFrames.size());
          for (int frameIndex = 0; frameIndex < sourceFrames.size(); frameIndex++) {
            final AnimationFrame source = sourceFrames.get(frameIndex);
            final AnimationFrame body = selectProportionalFrame(avatarFrames, frameIndex, sourceFrames.size());
            generated.add(renderReplacement(source, body, spec,
                mixSeed(seed, sequence.ordinal(), directionIndex, occurrence, frameIndex)));
          }
          if (!generated.isEmpty()) {
            result.replaceFrames(sequence, directionIndex, occurrence, generated);
          }
        }
        completed++;
        if (listener != null) {
          final int canonicalIndex = directionIndex > Direction.N.getCycleOffset() ? 16 - directionIndex
              : directionIndex;
          listener.progress(completed, total, sequence, Direction.values()[canonicalIndex]);
        }
      }
    }
    return result;
  }

  private static AnimationFrame renderReplacement(AnimationFrame source, AnimationFrame avatar, EquipmentSpec spec,
      long seed) {
    final Anchor anchor = analyzeAnchor(source, avatar);
    if (anchor == null) {
      final BufferedImage blank = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      return new AnimationFrame(blank, new Point(0, 0), "generated-empty-equipment-overlay");
    }
    return renderAtAnchor(anchor, spec, seed);
  }

  /**
   * Draws one shared-library equipment item at an explicit source-independent attachment point.
   *
   * @param gripWorldX     attachment x relative to the creature BAM center
   * @param gripWorldY     attachment y relative to the creature BAM center
   * @param angle          item direction in radians
   * @param referenceLength neutral reference length before the selected item and size scales are applied
   */
  public static AnimationFrame renderStandalone(EquipmentSpec specification, double gripWorldX, double gripWorldY,
      double angle, double referenceLength, long seed) {
    if (!Double.isFinite(gripWorldX) || !Double.isFinite(gripWorldY) || !Double.isFinite(angle)
        || !Double.isFinite(referenceLength) || referenceLength <= 0.0) {
      throw new IllegalArgumentException("Equipment attachment coordinates, angle and length must be finite.");
    }
    return renderAtAnchor(new Anchor(gripWorldX, gripWorldY, angle, referenceLength),
        Objects.requireNonNull(specification, "specification"), seed);
  }

  private static AnimationFrame renderAtAnchor(Anchor anchor, EquipmentSpec spec, long seed) {
    final double length =
        clamp(anchor.length * spec.targetWeapon.getLengthScale() * spec.size.getScale(), 10.0, 138.0);
    final BufferedImage work = new BufferedImage(WORK_SIZE, WORK_SIZE, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = work.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      graphics.translate(WORK_ORIGIN + anchor.gripWorldX, WORK_ORIGIN + anchor.gripWorldY);
      graphics.rotate(anchor.angle);
      if (spec.glowing) {
        drawGlow(graphics, spec, length);
      }
      drawWeapon(graphics, spec, length, new Random(seed));
    } finally {
      graphics.dispose();
    }

    final java.awt.Rectangle bounds = findOpaqueBounds(work);
    if (bounds == null) {
      return new AnimationFrame(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0),
          "generated-empty-equipment-overlay");
    }
    final int padding = spec.glowing ? 7 : 3;
    final int x = Math.max(0, bounds.x - padding);
    final int y = Math.max(0, bounds.y - padding);
    final int right = Math.min(work.getWidth(), bounds.x + bounds.width + padding);
    final int bottom = Math.min(work.getHeight(), bounds.y + bounds.height + padding);
    final BufferedImage cropped = new BufferedImage(Math.max(1, right - x), Math.max(1, bottom - y),
        BufferedImage.TYPE_INT_ARGB);
    final Graphics2D cropGraphics = cropped.createGraphics();
    try {
      cropGraphics.drawImage(work, -x, -y, null);
    } finally {
      cropGraphics.dispose();
    }
    return new AnimationFrame(cropped, new Point(WORK_ORIGIN - x, WORK_ORIGIN - y),
        "generated-" + spec.targetWeapon.name().toLowerCase(Locale.ENGLISH) + "-equipment-overlay");
  }

  private static Anchor analyzeAnchor(AnimationFrame source, AnimationFrame avatar) {
    final BufferedImage image = source.getImage();
    double weight = 0.0;
    double meanX = 0.0;
    double meanY = 0.0;
    int visible = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        final int alpha = image.getRGB(x, y) >>> 24;
        if (alpha >= ALPHA_THRESHOLD) {
          final double pixelWeight = alpha / 255.0;
          weight += pixelWeight;
          meanX += x * pixelWeight;
          meanY += y * pixelWeight;
          visible++;
        }
      }
    }
    if (visible < 3 || weight <= 0.0) {
      return null;
    }
    meanX /= weight;
    meanY /= weight;

    double covarianceX = 0.0;
    double covarianceY = 0.0;
    double covarianceXY = 0.0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        final int alpha = image.getRGB(x, y) >>> 24;
        if (alpha >= ALPHA_THRESHOLD) {
          final double pixelWeight = alpha / 255.0;
          final double dx = x - meanX;
          final double dy = y - meanY;
          covarianceX += dx * dx * pixelWeight;
          covarianceY += dy * dy * pixelWeight;
          covarianceXY += dx * dy * pixelWeight;
        }
      }
    }
    final double angle = 0.5 * Math.atan2(2.0 * covarianceXY, covarianceX - covarianceY);
    final double axisX = Math.cos(angle);
    final double axisY = Math.sin(angle);
    double minimum = Double.POSITIVE_INFINITY;
    double maximum = Double.NEGATIVE_INFINITY;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) >= ALPHA_THRESHOLD) {
          final double projection = (x - meanX) * axisX + (y - meanY) * axisY;
          minimum = Math.min(minimum, projection);
          maximum = Math.max(maximum, projection);
        }
      }
    }
    if (!Double.isFinite(minimum) || maximum - minimum < 4.0) {
      return null;
    }

    final double firstX = meanX + axisX * minimum;
    final double firstY = meanY + axisY * minimum;
    final double secondX = meanX + axisX * maximum;
    final double secondY = meanY + axisY * maximum;
    final Point sourceCenter = source.getCenter();
    final double firstWorldX = firstX - sourceCenter.x;
    final double firstWorldY = firstY - sourceCenter.y;
    final double secondWorldX = secondX - sourceCenter.x;
    final double secondWorldY = secondY - sourceCenter.y;
    final double firstDistance = distanceToAvatar(firstWorldX, firstWorldY, avatar);
    final double secondDistance = distanceToAvatar(secondWorldX, secondWorldY, avatar);

    final double gripX;
    final double gripY;
    final double tipX;
    final double tipY;
    if (firstDistance <= secondDistance) {
      gripX = firstWorldX;
      gripY = firstWorldY;
      tipX = secondWorldX;
      tipY = secondWorldY;
    } else {
      gripX = secondWorldX;
      gripY = secondWorldY;
      tipX = firstWorldX;
      tipY = firstWorldY;
    }
    return new Anchor(gripX, gripY, Math.atan2(tipY - gripY, tipX - gripX),
        Math.hypot(tipX - gripX, tipY - gripY));
  }

  private static double distanceToAvatar(double worldX, double worldY, AnimationFrame avatar) {
    if (avatar == null) {
      return worldX * worldX + worldY * worldY;
    }
    final BufferedImage image = avatar.getImage();
    final Point center = avatar.getCenter();
    double best = Double.POSITIVE_INFINITY;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) >= 64) {
          final double dx = (x - center.x) - worldX;
          final double dy = (y - center.y) - worldY;
          best = Math.min(best, dx * dx + dy * dy);
        }
      }
    }
    return Double.isFinite(best) ? best : worldX * worldX + worldY * worldY;
  }

  private static void drawGlow(Graphics2D graphics, EquipmentSpec spec, double length) {
    graphics.setComposite(AlphaComposite.SrcOver);
    graphics.setColor(withAlpha(spec.glowColor, 42));
    graphics.setStroke(new BasicStroke((float) clamp(length * 0.19, 7.0, 18.0), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(-length * 0.12, 0.0, length, 0.0));
    graphics.setColor(withAlpha(spec.glowColor, 75));
    graphics.setStroke(new BasicStroke((float) clamp(length * 0.09, 4.0, 10.0), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(-length * 0.12, 0.0, length, 0.0));
  }

  private static void drawWeapon(Graphics2D graphics, EquipmentSpec spec, double length, Random random) {
    final Color outline = darken(spec.metalColor, 0.56);
    final Color highlight = lighten(spec.metalColor, 0.43);
    final Color handle = spec.accentColor;
    final float handleWidth = (float) clamp(length * 0.055, 2.2, 5.4);
    switch (spec.targetWeapon) {
      case SICKLE:
        drawHandle(graphics, handle, -length * 0.1, length * 0.58, handleWidth);
        drawCurvedBlade(graphics, outline, spec.metalColor, highlight, length * 0.54, 0.0, length * 0.5,
            -length * 0.38, true);
        break;
      case SCYTHE:
        drawHandle(graphics, handle, -length * 0.12, length * 0.9, handleWidth);
        drawScytheBlade(graphics, outline, spec.metalColor, highlight, length);
        drawGrip(graphics, handle, length * 0.34, -length * 0.11, length * 0.08, handleWidth * 0.72f);
        break;
      case SWORD:
      case GREATSWORD:
        drawSword(graphics, spec, length, spec.targetWeapon == WeaponType.GREATSWORD);
        break;
      case SCIMITAR:
        drawCurvedSword(graphics, spec, length, 0.2, false);
        break;
      case WAKIZASHI:
        drawCurvedSword(graphics, spec, length, 0.12, true);
        break;
      case KATANA:
        drawCurvedSword(graphics, spec, length, 0.09, false);
        break;
      case NINJATO:
        drawNinjato(graphics, spec, length);
        break;
      case DAGGER:
        drawSword(graphics, spec, length, false);
        break;
      case AXE:
      case BATTLEAXE:
        drawHandle(graphics, handle, -length * 0.12, length * 0.88, handleWidth);
        drawAxeHead(graphics, outline, spec.metalColor, highlight, length * 0.84, length,
            spec.targetWeapon == WeaponType.BATTLEAXE);
        break;
      case MACE:
        drawHandle(graphics, handle, -length * 0.12, length * 0.82, handleWidth);
        drawMaceHead(graphics, outline, spec.metalColor, length * 0.88, length * 0.13);
        break;
      case HAMMER:
        drawHandle(graphics, handle, -length * 0.12, length * 0.84, handleWidth);
        drawHammerHead(graphics, outline, spec.metalColor, highlight, length * 0.86, length * 0.14);
        break;
      case ONE_HANDED_SPEAR:
      case SPEAR:
      case GLAIVE:
      case HALBERD:
        case TRIDENT:
        drawHandle(graphics, handle, -length * 0.12, length * 0.87, handleWidth * 0.82f);
        drawPolearmHead(graphics, spec, length);
        break;
      case STAFF:
        drawHandle(graphics, handle, -length * 0.18, length, handleWidth * 1.2f);
        graphics.setColor(spec.metalColor);
        graphics.fill(new Ellipse2D.Double(length - handleWidth * 1.25, -handleWidth * 1.25,
            handleWidth * 2.5, handleWidth * 2.5));
        break;
      case CLUB:
        drawClub(graphics, outline, handle, length, handleWidth);
        break;
      case FLAIL:
        drawFlail(graphics, outline, handle, spec.metalColor, length, handleWidth);
        break;
      case SHORTBOW:
      case LONGBOW:
      case BOW:
        drawBow(graphics, outline, handle, length, handleWidth);
        break;
      case LIGHT_CROSSBOW:
      case HEAVY_CROSSBOW:
      case CROSSBOW:
        drawCrossbow(graphics, outline, handle, spec.metalColor, length, handleWidth,
            spec.targetWeapon == WeaponType.HEAVY_CROSSBOW);
        break;
      case SLING:
        drawSling(graphics, outline, handle, length, handleWidth);
        break;
      case WHIP:
        drawWhip(graphics, outline, handle, length, handleWidth);
        break;
      case BUCKLER:
      case SMALL_SHIELD:
      case MEDIUM_SHIELD:
      case LARGE_SHIELD:
        drawShield(graphics, spec, length);
        break;
      default:
        throw new IllegalStateException("Unsupported procedural equipment type: " + spec.targetWeapon);
    }

    if (spec.ornate) {
      graphics.setColor(lighten(spec.glowColor, 0.34));
      final int ornaments = 2 + random.nextInt(2);
      for (int i = 0; i < ornaments; i++) {
        final double x = -length * (0.03 + i * 0.045);
        final double radius = 1.25 + random.nextDouble() * 0.8;
        graphics.fill(new Ellipse2D.Double(x - radius, -radius, radius * 2.0, radius * 2.0));
      }
    }
  }

  private static void drawHandle(Graphics2D graphics, Color color, double start, double end, float width) {
    graphics.setColor(darken(color, 0.42));
    graphics.setStroke(new BasicStroke(width + 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(start, 0.0, end, 0.0));
    graphics.setColor(color);
    graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(start, 0.0, end, 0.0));
    graphics.setColor(lighten(color, 0.24));
    graphics.setStroke(new BasicStroke(Math.max(1.0f, width * 0.28f), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(start, -width * 0.15, end, -width * 0.15));
  }

  private static void drawGrip(Graphics2D graphics, Color color, double x, double y, double width, float stroke) {
    graphics.setColor(darken(color, 0.35));
    graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(x - width / 2.0, y, x + width / 2.0, y));
  }

  private static void drawCurvedBlade(Graphics2D graphics, Color outline, Color metal, Color highlight, double rootX,
      double rootY, double bladeLength, double rise, boolean compact) {
    final double tipX = rootX + bladeLength;
    final Path2D blade = new Path2D.Double();
    blade.moveTo(rootX, rootY - 2.0);
    blade.curveTo(rootX + bladeLength * 0.3, rise * 0.35, tipX - bladeLength * 0.08, rise * 0.92, tipX, rise);
    blade.curveTo(tipX - bladeLength * 0.28, rise * (compact ? 0.62 : 0.48),
        rootX + bladeLength * 0.18, rise * 0.25, rootX, rootY + 2.0);
    blade.closePath();
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(blade);
    graphics.setColor(metal);
    graphics.fill(blade);
    graphics.setColor(highlight);
    graphics.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(new java.awt.geom.QuadCurve2D.Double(rootX + 2.0, rootY - 2.0,
        rootX + bladeLength * 0.58, rise * 0.55, tipX - 2.0, rise + 1.0));
  }

  private static void drawScytheBlade(Graphics2D graphics, Color outline, Color metal, Color highlight,
      double length) {
    final double rootX = length * 0.84;
    final double tipX = length * 0.27;
    final double tipY = -length * 0.55;
    final Path2D blade = new Path2D.Double();
    blade.moveTo(rootX + length * 0.035, -2.0);
    blade.curveTo(rootX + length * 0.08, -length * 0.19, length * 0.58, -length * 0.51, tipX, tipY);
    blade.curveTo(length * 0.45, -length * 0.39, length * 0.66, -length * 0.17, rootX - 2.0, 3.0);
    blade.closePath();
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(blade);
    graphics.setColor(metal);
    graphics.fill(blade);
    graphics.setColor(highlight);
    graphics.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(new java.awt.geom.QuadCurve2D.Double(rootX, -3.0, length * 0.58, -length * 0.43,
        tipX + 2.0, tipY + 1.0));
  }

  private static void drawSword(Graphics2D graphics, EquipmentSpec spec, double length, boolean twoHanded) {
    final double handleLength = length * (twoHanded ? 0.26 : 0.18);
    final double bladeStart = length * 0.08;
    drawHandle(graphics, spec.accentColor, -handleLength, bladeStart, (float) clamp(length * 0.055, 2.2, 5.0));
    final double halfWidth = clamp(length * (twoHanded ? 0.07 : 0.055), 2.5, 7.0);
    final Path2D blade = new Path2D.Double();
    blade.moveTo(bladeStart, -halfWidth);
    blade.lineTo(length * 0.86, -halfWidth * 0.62);
    blade.lineTo(length, 0.0);
    blade.lineTo(length * 0.86, halfWidth * 0.62);
    blade.lineTo(bladeStart, halfWidth);
    blade.closePath();
    graphics.setColor(darken(spec.metalColor, 0.58));
    graphics.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(blade);
    graphics.setColor(spec.metalColor);
    graphics.fill(blade);
    graphics.setColor(lighten(spec.metalColor, 0.48));
    graphics.setStroke(new BasicStroke(1.25f));
    graphics.draw(new Line2D.Double(bladeStart + 1.0, -halfWidth * 0.4, length * 0.91, -halfWidth * 0.2));
    graphics.setColor(lighten(spec.accentColor, 0.3));
    graphics.setStroke(new BasicStroke((float) Math.max(2.1, halfWidth * 0.45), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(bladeStart - halfWidth * 0.15, -halfWidth * 1.35,
        bladeStart - halfWidth * 0.15, halfWidth * 1.35));
  }

  private static void drawCurvedSword(Graphics2D graphics, EquipmentSpec spec, double length, double curvature,
      boolean compactGuard) {
    final double handleLength = length * 0.2;
    final double bladeStart = length * 0.08;
    final double halfWidth = clamp(length * 0.052, 2.4, 6.5);
    drawHandle(graphics, spec.accentColor, -handleLength, bladeStart,
        (float) clamp(length * 0.052, 2.2, 4.8));

    final Path2D blade = new Path2D.Double();
    final double bend = length * curvature;
    blade.moveTo(bladeStart, -halfWidth);
    blade.curveTo(length * 0.45, -halfWidth - bend * 0.32, length * 0.82, -halfWidth - bend * 0.82,
        length, -bend);
    blade.curveTo(length * 0.84, halfWidth * 0.42 - bend * 0.7, length * 0.45,
        halfWidth - bend * 0.22, bladeStart, halfWidth);
    blade.closePath();
    graphics.setColor(darken(spec.metalColor, 0.58));
    graphics.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(blade);
    graphics.setColor(spec.metalColor);
    graphics.fill(blade);
    graphics.setColor(lighten(spec.metalColor, 0.48));
    graphics.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(new java.awt.geom.QuadCurve2D.Double(bladeStart + 2.0, -halfWidth * 0.4,
        length * 0.62, -bend * 0.55, length * 0.94, -bend + 1.0));

    graphics.setColor(lighten(spec.accentColor, 0.3));
    if (compactGuard) {
      graphics.setStroke(new BasicStroke((float) Math.max(2.0, halfWidth * 0.5), BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      graphics.draw(new Line2D.Double(bladeStart - halfWidth * 0.12, -halfWidth,
          bladeStart - halfWidth * 0.12, halfWidth));
    } else {
      graphics.fill(new Ellipse2D.Double(bladeStart - halfWidth * 0.72, -halfWidth * 1.16,
          halfWidth * 1.12, halfWidth * 2.32));
    }
  }

  private static void drawNinjato(Graphics2D graphics, EquipmentSpec spec, double length) {
    drawSword(graphics, spec, length, false);
    final double guardX = length * 0.07;
    final double guardSize = clamp(length * 0.075, 3.4, 7.5);
    graphics.setColor(darken(spec.accentColor, 0.28));
    graphics.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
    graphics.draw(new java.awt.geom.Rectangle2D.Double(guardX - guardSize * 0.45, -guardSize,
        guardSize * 0.9, guardSize * 2.0));
  }

  private static void drawAxeHead(Graphics2D graphics, Color outline, Color metal, Color highlight, double root,
      double length, boolean doubleHeaded) {
    final double size = length * (doubleHeaded ? 0.23 : 0.19);
    final Path2D head = new Path2D.Double();
    head.moveTo(root, -size * 0.32);
    head.quadTo(root + size * 0.65, -size * 0.9, root + size, -size * 0.72);
    head.lineTo(root + size * 0.72, size * 0.72);
    head.quadTo(root + size * 0.28, size * 0.54, root, size * 0.28);
    head.closePath();
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(head);
    graphics.setColor(metal);
    graphics.fill(head);
    if (doubleHeaded) {
      final Path2D back = new Path2D.Double();
      back.moveTo(root + size * 0.1, -size * 0.25);
      back.quadTo(root - size * 0.48, -size * 0.72, root - size * 0.75, -size * 0.52);
      back.lineTo(root - size * 0.55, size * 0.56);
      back.quadTo(root - size * 0.16, size * 0.42, root + size * 0.1, size * 0.25);
      back.closePath();
      graphics.setColor(outline);
      graphics.draw(back);
      graphics.setColor(metal);
      graphics.fill(back);
    }
    graphics.setColor(highlight);
    graphics.setStroke(new BasicStroke(1.1f));
    graphics.draw(new Line2D.Double(root + size * 0.45, -size * 0.58, root + size * 0.72, size * 0.45));
  }

  private static void drawMaceHead(Graphics2D graphics, Color outline, Color metal, double x, double radius) {
    graphics.setColor(outline);
    graphics.fill(new Ellipse2D.Double(x - radius - 1.7, -radius - 1.7, radius * 2.0 + 3.4,
        radius * 2.0 + 3.4));
    graphics.setColor(metal);
    graphics.fill(new Ellipse2D.Double(x - radius, -radius, radius * 2.0, radius * 2.0));
    graphics.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    for (int i = 0; i < 8; i++) {
      final double angle = i * Math.PI / 4.0;
      graphics.setColor(lighten(metal, 0.22));
      graphics.draw(new Line2D.Double(x + Math.cos(angle) * radius * 0.7, Math.sin(angle) * radius * 0.7,
          x + Math.cos(angle) * radius * 1.35, Math.sin(angle) * radius * 1.35));
    }
  }

  private static void drawHammerHead(Graphics2D graphics, Color outline, Color metal, Color highlight, double x,
      double radius) {
    final Path2D head = new Path2D.Double();
    head.moveTo(x - radius * 0.6, -radius);
    head.lineTo(x + radius * 0.8, -radius);
    head.lineTo(x + radius * 0.8, radius);
    head.lineTo(x - radius * 0.6, radius);
    head.closePath();
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(head);
    graphics.setColor(metal);
    graphics.fill(head);
    final Path2D pick = new Path2D.Double();
    pick.moveTo(x - radius * 0.45, -radius * 0.65);
    pick.lineTo(x - radius * 1.5, 0.0);
    pick.lineTo(x - radius * 0.45, radius * 0.65);
    pick.closePath();
    graphics.setColor(outline);
    graphics.draw(pick);
    graphics.setColor(metal);
    graphics.fill(pick);
    graphics.setColor(highlight);
    graphics.draw(new Line2D.Double(x - radius * 0.15, -radius * 0.55, x + radius * 0.55, -radius * 0.55));
  }

  private static void drawPolearmHead(Graphics2D graphics, EquipmentSpec spec, double length) {
    final double root = length * 0.84;
    final double size = length * 0.18;
    final Color outline = darken(spec.metalColor, 0.56);
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    if (spec.targetWeapon == WeaponType.TRIDENT) {
      for (int i = -1; i <= 1; i++) {
        final double y = i * size * 0.42;
        graphics.draw(new Line2D.Double(root, y, length, y));
        final Path2D point = createPoint(length, y, size * 0.32, size * 0.2);
        graphics.setColor(outline);
        graphics.draw(point);
        graphics.setColor(spec.metalColor);
        graphics.fill(point);
      }
      graphics.setColor(spec.metalColor);
      graphics.setStroke(new BasicStroke(2.0f));
      graphics.draw(new Line2D.Double(root, -size * 0.42, root, size * 0.42));
    } else {
      final Path2D point = createPoint(length, 0.0, size, size * 0.38);
      graphics.setColor(outline);
      graphics.draw(point);
      graphics.setColor(spec.metalColor);
      graphics.fill(point);
      if (spec.targetWeapon == WeaponType.HALBERD || spec.targetWeapon == WeaponType.GLAIVE) {
        final Path2D blade = new Path2D.Double();
        blade.moveTo(root, -size * 0.14);
        blade.quadTo(root + size * 0.32, -size * 0.9, root + size * 0.68, -size * 0.76);
        blade.lineTo(root + size * 0.52, size * 0.16);
        blade.closePath();
        graphics.setColor(outline);
        graphics.draw(blade);
        graphics.setColor(spec.metalColor);
        graphics.fill(blade);
      }
    }
  }

  private static Path2D createPoint(double tipX, double y, double length, double halfWidth) {
    final Path2D point = new Path2D.Double();
    point.moveTo(tipX, y);
    point.lineTo(tipX - length, y - halfWidth);
    point.lineTo(tipX - length * 0.78, y);
    point.lineTo(tipX - length, y + halfWidth);
    point.closePath();
    return point;
  }

  private static void drawClub(Graphics2D graphics, Color outline, Color wood, double length, float baseWidth) {
    final Path2D club = new Path2D.Double();
    club.moveTo(-length * 0.1, -baseWidth * 0.45);
    club.lineTo(length * 0.65, -baseWidth * 0.9);
    club.quadTo(length * 0.96, -baseWidth * 2.4, length, 0.0);
    club.quadTo(length * 0.96, baseWidth * 2.4, length * 0.65, baseWidth * 0.9);
    club.lineTo(-length * 0.1, baseWidth * 0.45);
    club.closePath();
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(club);
    graphics.setColor(wood);
    graphics.fill(club);
  }

  private static void drawFlail(Graphics2D graphics, Color outline, Color handle, Color metal, double length,
      float handleWidth) {
    drawHandle(graphics, handle, -length * 0.1, length * 0.57, handleWidth);
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    final double chainEndX = length * 0.86;
    final double chainEndY = -length * 0.16;
    for (int i = 0; i < 5; i++) {
      final double ratio = i / 4.0;
      final double x = length * 0.58 + (chainEndX - length * 0.58) * ratio;
      final double y = chainEndY * ratio;
      graphics.draw(new Ellipse2D.Double(x - 1.6, y - 1.6, 3.2, 3.2));
    }
    drawMaceHead(graphics, outline, metal, chainEndX, length * 0.105);
  }

  private static void drawBow(Graphics2D graphics, Color outline, Color wood, double length, float width) {
    final Path2D bow = new Path2D.Double();
    bow.moveTo(-length * 0.08, -length * 0.36);
    bow.curveTo(length * 0.38, -length * 0.18, length * 0.38, length * 0.18,
        -length * 0.08, length * 0.36);
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(width + 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(bow);
    graphics.setColor(wood);
    graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(bow);
    graphics.setColor(new Color(218, 218, 208));
    graphics.setStroke(new BasicStroke(1.0f));
    graphics.draw(new Line2D.Double(-length * 0.08, -length * 0.36, -length * 0.08, length * 0.36));
  }

  private static void drawCrossbow(Graphics2D graphics, Color outline, Color wood, Color metal, double length,
      float width, boolean heavy) {
    final double stockEnd = length * 0.92;
    drawHandle(graphics, wood, -length * 0.16, stockEnd, width * (heavy ? 1.25f : 1.0f));
    final double limbX = length * 0.64;
    final double limbHalf = length * (heavy ? 0.34 : 0.28);
    final Path2D limb = new Path2D.Double();
    limb.moveTo(limbX, -limbHalf);
    limb.quadTo(limbX + length * 0.12, 0.0, limbX, limbHalf);
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(width + 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(limb);
    graphics.setColor(wood);
    graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(limb);
    graphics.setColor(lighten(metal, 0.35));
    graphics.setStroke(new BasicStroke(1.1f));
    graphics.draw(new Line2D.Double(limbX, -limbHalf, limbX - length * 0.08, 0.0));
    graphics.draw(new Line2D.Double(limbX - length * 0.08, 0.0, limbX, limbHalf));
    graphics.setColor(metal);
    graphics.setStroke(new BasicStroke(Math.max(1.2f, width * 0.35f), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(-length * 0.02, 0.0, length, 0.0));
  }

  private static void drawSling(Graphics2D graphics, Color outline, Color leather, double length, float width) {
    drawHandle(graphics, leather, -length * 0.12, length * 0.18, width * 0.95f);
    final Path2D cords = new Path2D.Double();
    cords.moveTo(length * 0.16, -width * 0.2);
    cords.curveTo(length * 0.48, -length * 0.18, length * 0.72, -length * 0.12, length * 0.88, 0.0);
    cords.moveTo(length * 0.16, width * 0.2);
    cords.curveTo(length * 0.48, length * 0.18, length * 0.72, length * 0.12, length * 0.88, 0.0);
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(Math.max(1.5f, width * 0.45f), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(cords);
    graphics.setColor(leather);
    graphics.setStroke(new BasicStroke(Math.max(1.0f, width * 0.22f), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(cords);
    final double pouchWidth = length * 0.18;
    final double pouchHeight = Math.max(width * 1.6, length * 0.07);
    graphics.setColor(outline);
    graphics.fill(new Ellipse2D.Double(length * 0.88 - pouchWidth / 2.0, -pouchHeight / 2.0,
        pouchWidth, pouchHeight));
    graphics.setColor(leather);
    graphics.fill(new Ellipse2D.Double(length * 0.88 - pouchWidth * 0.42, -pouchHeight * 0.36,
        pouchWidth * 0.84, pouchHeight * 0.72));
  }

  private static void drawShield(Graphics2D graphics, EquipmentSpec spec, double length) {
    final boolean buckler = spec.targetWeapon == WeaponType.BUCKLER;
    final boolean large = spec.targetWeapon == WeaponType.LARGE_SHIELD;
    final double width = length * (buckler ? 0.72 : large ? 0.9 : 0.82);
    final double height = length * (buckler ? 0.72
        : spec.targetWeapon == WeaponType.SMALL_SHIELD ? 0.84 : large ? 1.18 : 1.0);
    final double centerX = length * 0.36;
    final double halfWidth = width / 2.0;
    final double halfHeight = height / 2.0;
    final Path2D shield = new Path2D.Double();
    if (buckler) {
      shield.append(new Ellipse2D.Double(centerX - halfWidth, -halfHeight, width, height), false);
    } else {
      shield.moveTo(centerX - halfWidth, -halfHeight * 0.72);
      shield.quadTo(centerX, -halfHeight * 1.12, centerX + halfWidth, -halfHeight * 0.72);
      shield.lineTo(centerX + halfWidth * 0.82, halfHeight * 0.38);
      shield.quadTo(centerX, halfHeight * 1.12, centerX - halfWidth * 0.82, halfHeight * 0.38);
      shield.closePath();
    }
    graphics.setColor(darken(spec.metalColor, 0.62));
    graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(shield);
    graphics.setColor(spec.accentColor);
    graphics.fill(shield);
    graphics.setColor(spec.metalColor);
    graphics.setStroke(new BasicStroke(Math.max(1.5f, (float) (width * 0.055)), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(shield);
    final double bossRadius = Math.max(2.5, Math.min(width, height) * 0.14);
    graphics.setColor(darken(spec.metalColor, 0.42));
    graphics.fill(new Ellipse2D.Double(centerX - bossRadius - 1.2, -bossRadius - 1.2,
        bossRadius * 2.0 + 2.4, bossRadius * 2.0 + 2.4));
    graphics.setColor(lighten(spec.metalColor, 0.18));
    graphics.fill(new Ellipse2D.Double(centerX - bossRadius, -bossRadius, bossRadius * 2.0, bossRadius * 2.0));
  }

  private static void drawWhip(Graphics2D graphics, Color outline, Color leather, double length, float width) {
    drawHandle(graphics, leather, -length * 0.12, length * 0.24, width * 1.15f);
    final Path2D lash = new Path2D.Double();
    lash.moveTo(length * 0.22, 0.0);
    lash.curveTo(length * 0.55, -length * 0.22, length * 0.78, length * 0.28, length, -length * 0.08);
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(Math.max(2.2f, width * 0.62f), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(lash);
    graphics.setColor(leather);
    graphics.setStroke(new BasicStroke(Math.max(1.0f, width * 0.34f), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(lash);
  }

  private static AnimationFrame selectProportionalFrame(List<AnimationFrame> frames, int index, int sourceCount) {
    if (frames == null || frames.isEmpty()) {
      return null;
    }
    if (frames.size() == 1 || sourceCount <= 1) {
      return frames.get(0);
    }
    final int mapped = (int) Math.round(index * (frames.size() - 1.0) / (sourceCount - 1.0));
    return frames.get(Math.max(0, Math.min(frames.size() - 1, mapped)));
  }

  private static java.awt.Rectangle findOpaqueBounds(BufferedImage image) {
    int minX = image.getWidth();
    int minY = image.getHeight();
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) {
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
        }
      }
    }
    return maxX >= minX && maxY >= minY
        ? new java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1) : null;
  }

  public static void validateLoadout(WeaponType mainHand, WeaponType offhand) {
    if (mainHand == null) {
      throw new IllegalArgumentException("A main-hand equipment type is required.");
    }
    if (mainHand.isShield()) {
      throw new IllegalArgumentException("Shields must be assigned to the offhand together with a main-hand item.");
    }
    if (offhand == null) {
      return;
    }
    if (offhand.isShield()) {
      if (!mainHand.allowsShield()) {
        throw new IllegalArgumentException(mainHand.getLabel()
            + " cannot be combined with a shield because its decoder pose occupies both hands.");
      }
      return;
    }
    if (!mainHand.isOneHandedMelee() || !offhand.isOneHandedMelee()) {
      throw new IllegalArgumentException("Two-weapon fighting requires one one-handed melee weapon in each hand.");
    }
  }

  private static long mixSeed(long seed, int sequence, int direction, int frame) {
    return mixSeed(seed, sequence, direction, 0, frame);
  }

  private static long mixSeed(long seed, int sequence, int direction, int occurrence, int frame) {
    long result = seed ^ 0x9e3779b97f4a7c15L;
    result ^= (sequence + 1L) * 0xbf58476d1ce4e5b9L;
    result ^= (direction + 1L) * 0x94d049bb133111ebL;
    result ^= occurrence * 0xd6e8feb86659fd93L;
    result ^= (frame + 1L) * 0x2545f4914f6cdd1dL;
    return result;
  }

  private static Color lighten(Color color, double amount) {
    return mix(color, Color.WHITE, amount);
  }

  private static Color darken(Color color, double amount) {
    return mix(color, Color.BLACK, amount);
  }

  private static Color mix(Color first, Color second, double amount) {
    final double ratio = clamp(amount, 0.0, 1.0);
    return new Color((int) Math.round(first.getRed() * (1.0 - ratio) + second.getRed() * ratio),
        (int) Math.round(first.getGreen() * (1.0 - ratio) + second.getGreen() * ratio),
        (int) Math.round(first.getBlue() * (1.0 - ratio) + second.getBlue() * ratio),
        (int) Math.round(first.getAlpha() * (1.0 - ratio) + second.getAlpha() * ratio));
  }

  private static Color withAlpha(Color color, int alpha) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
  }

  private static double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static final class Anchor {
    private final double gripWorldX;
    private final double gripWorldY;
    private final double angle;
    private final double length;

    private Anchor(double gripWorldX, double gripWorldY, double angle, double length) {
      this.gripWorldX = gripWorldX;
      this.gripWorldY = gripWorldY;
      this.angle = angle;
      this.length = length;
    }
  }

}
