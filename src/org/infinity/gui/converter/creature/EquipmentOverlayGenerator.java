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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  /** Weapon silhouettes supported by the offline overlay renderer. */
  public enum WeaponType {
    SICKLE("sickle", "SK", 0.88, false, "sickle", "hand sickle", "kama"),
    SCYTHE("scythe", "SY", 1.28, true, "scythe", "war scythe"),
    SWORD("sword", "S1", 1.0, false, "sword", "longsword", "long sword", "blade"),
    GREATSWORD("greatsword", "S2", 1.24, true, "greatsword", "great sword", "two handed sword",
        "two-handed sword", "zweihander"),
    DAGGER("dagger", "DD", 0.62, false, "dagger", "knife", "dirk"),
    AXE("axe", "AX", 0.96, false, "axe", "hatchet"),
    BATTLEAXE("battleaxe", "BA", 1.15, true, "battleaxe", "battle axe", "greataxe", "great axe"),
    MACE("mace", "MC", 0.9, false, "mace", "morning star", "morningstar"),
    HAMMER("war hammer", "WH", 0.94, false, "war hammer", "warhammer", "hammer", "maul"),
    SPEAR("spear", "SP", 1.28, true, "spear", "pike", "lance"),
    HALBERD("halberd", "HB", 1.28, true, "halberd", "poleaxe", "pole axe"),
    GLAIVE("glaive", "GL", 1.24, true, "glaive", "naginata", "polearm"),
    TRIDENT("trident", "TR", 1.25, true, "trident", "trishula"),
    STAFF("staff", "QS", 1.2, true, "quarterstaff", "quarter staff", "staff", "rod"),
    CLUB("club", "CL", 0.88, false, "club", "cudgel"),
    FLAIL("flail", "FL", 0.98, false, "flail"),
    BOW("bow", "BW", 1.12, true, "longbow", "long bow", "shortbow", "short bow", "bow"),
    WHIP("whip", "WP", 1.15, false, "whip", "lash");

    private final String label;
    private final String appearanceCode;
    private final double lengthScale;
    private final boolean twoHanded;
    private final List<String> aliases;

    WeaponType(String label, String appearanceCode, double lengthScale, boolean twoHanded, String... aliases) {
      this.label = label;
      this.appearanceCode = appearanceCode;
      this.lengthScale = lengthScale;
      this.twoHanded = twoHanded;
      this.aliases = Collections.unmodifiableList(Arrays.asList(aliases));
    }

    public String getLabel() {
      return label;
    }

    public String getSuggestedAppearanceCode() {
      return appearanceCode;
    }

    public boolean isTwoHanded() {
      return twoHanded;
    }

    public List<String> getAliases() {
      return aliases;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /** Parsed, deterministic subset of a free-form equipment replacement prompt. */
  public static final class PromptSpec {
    private final String prompt;
    private final WeaponType sourceWeapon;
    private final WeaponType targetWeapon;
    private final Color metalColor;
    private final Color accentColor;
    private final Color glowColor;
    private final boolean glowing;
    private final boolean ornate;
    private final double scale;

    private PromptSpec(String prompt, WeaponType sourceWeapon, WeaponType targetWeapon, Color metalColor,
        Color accentColor, Color glowColor, boolean glowing, boolean ornate, double scale) {
      this.prompt = prompt;
      this.sourceWeapon = sourceWeapon;
      this.targetWeapon = targetWeapon;
      this.metalColor = metalColor;
      this.accentColor = accentColor;
      this.glowColor = glowColor;
      this.glowing = glowing;
      this.ornate = ornate;
      this.scale = scale;
    }

    public String getPrompt() {
      return prompt;
    }

    public WeaponType getSourceWeapon() {
      return sourceWeapon;
    }

    public WeaponType getTargetWeapon() {
      return targetWeapon;
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
      return scale;
    }

    public String getSummary() {
      final String source = sourceWeapon != null ? sourceWeapon.getLabel() : "auto-detected source";
      return source + " → " + targetWeapon.getLabel() + (glowing ? " • glowing" : "")
          + (ornate ? " • ornate" : "") + " • scale " + String.format(Locale.ENGLISH, "%.2f", scale);
    }
  }

  public interface ProgressListener {
    void progress(int completed, int total, Sequence sequence, Direction direction);
  }

  private static final Map<String, Color> NAMED_COLORS = createNamedColors();

  private EquipmentOverlayGenerator() {
  }

  /** Parses the supported equipment vocabulary from a free-form replacement request. */
  public static PromptSpec parsePrompt(String prompt) {
    final String original = prompt != null ? prompt.trim() : "";
    final String normalized = normalizeWords(original);
    final List<WeaponMatch> weaponMatches = findWeaponMatches(normalized);
    if (weaponMatches.isEmpty()) {
      throw new IllegalArgumentException("The equipment prompt must name a supported weapon, such as sickle, scythe, "
          + "sword, axe, mace, hammer, spear, halberd, staff, flail, bow or whip.");
    }

    WeaponType source = null;
    WeaponType target = weaponMatches.get(weaponMatches.size() - 1).type;
    if (weaponMatches.size() > 1) {
      source = weaponMatches.get(0).type;
    }

    final List<ColorMatch> colors = findColorMatches(normalized);
    final Color metal = !colors.isEmpty() ? colors.get(0).color : new Color(202, 210, 220);
    final Color accent = colors.size() > 1 ? colors.get(1).color : new Color(91, 59, 36);
    final Color glow = !colors.isEmpty() ? colors.get(colors.size() - 1).color : new Color(105, 186, 255);
    final boolean glowing = containsAny(normalized, " glow ", " glowing ", " luminous ", " radiant ", " flaming ",
        " enchanted ", " magical ");
    final boolean ornate = containsAny(normalized, " ornate ", " runed ", " engraved ", " jeweled ", " jewelled ",
        " ceremonial ");
    double scale = 1.0;
    if (containsAny(normalized, " huge ", " massive ", " enormous ", " oversized ")) {
      scale = 1.22;
    } else if (containsAny(normalized, " large ", " long ", " heavy ")) {
      scale = 1.1;
    } else if (containsAny(normalized, " tiny ", " miniature ")) {
      scale = 0.72;
    } else if (containsAny(normalized, " small ", " short ", " light ")) {
      scale = 0.86;
    }
    return new PromptSpec(original, source, target, metal, accent, glow, glowing, ornate, scale);
  }

  /**
   * Generates a complete type {@code 0x7000} overlay model from an existing synchronized weapon overlay.
   *
   * @param sourceOverlay existing equipment layer, normally the sword named by the prompt
   * @param avatar        optional matching avatar frames, used to disambiguate which end of the source is the grip
   */
  public static CreatureAnimationModel generate(CreatureAnimationModel sourceOverlay, CreatureAnimationModel avatar,
      PromptSpec prompt, long seed, ProgressListener listener) {
    if (sourceOverlay == null || sourceOverlay.isEmpty()) {
      throw new IllegalArgumentException("An existing synchronized equipment overlay is required.");
    }
    if (prompt == null || prompt.targetWeapon == null) {
      throw new IllegalArgumentException("A parsed target weapon is required.");
    }

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
          generated.add(renderReplacement(source, body, prompt,
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
      PromptSpec prompt, long seed, ProgressListener listener) {
    final boolean explicitEastern = sourceOverlay != null && !sourceOverlay.getEasternModel().isEmpty();
    return generate(sourceOverlay, avatar, prompt, seed, explicitEastern, listener);
  }

  public static EquipmentOverlayModel generate(EquipmentOverlayModel sourceOverlay, EquipmentOverlayModel avatar,
      PromptSpec prompt, long seed, boolean explicitEastern, ProgressListener listener) {
    if (sourceOverlay == null || sourceOverlay.isEmpty()) {
      throw new IllegalArgumentException("An existing synchronized equipment overlay is required.");
    }
    if (prompt == null || prompt.targetWeapon == null) {
      throw new IllegalArgumentException("A parsed target weapon is required.");
    }

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
            generated.add(renderReplacement(source, body, prompt,
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

  private static AnimationFrame renderReplacement(AnimationFrame source, AnimationFrame avatar, PromptSpec prompt,
      long seed) {
    final Anchor anchor = analyzeAnchor(source, avatar);
    if (anchor == null) {
      final BufferedImage blank = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      return new AnimationFrame(blank, new Point(0, 0), "generated-empty-equipment-overlay");
    }

    final double length = clamp(anchor.length * prompt.targetWeapon.lengthScale * prompt.scale, 10.0, 138.0);
    final BufferedImage work = new BufferedImage(WORK_SIZE, WORK_SIZE, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = work.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      graphics.translate(WORK_ORIGIN + anchor.gripWorldX, WORK_ORIGIN + anchor.gripWorldY);
      graphics.rotate(anchor.angle);
      if (prompt.glowing) {
        drawGlow(graphics, prompt, length);
      }
      drawWeapon(graphics, prompt, length, new Random(seed));
    } finally {
      graphics.dispose();
    }

    final java.awt.Rectangle bounds = findOpaqueBounds(work);
    if (bounds == null) {
      return new AnimationFrame(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0),
          "generated-empty-equipment-overlay");
    }
    final int padding = prompt.glowing ? 7 : 3;
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
        "generated-" + prompt.targetWeapon.name().toLowerCase(Locale.ENGLISH) + "-equipment-overlay");
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

  private static void drawGlow(Graphics2D graphics, PromptSpec prompt, double length) {
    graphics.setComposite(AlphaComposite.SrcOver);
    graphics.setColor(withAlpha(prompt.glowColor, 42));
    graphics.setStroke(new BasicStroke((float) clamp(length * 0.19, 7.0, 18.0), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(-length * 0.12, 0.0, length, 0.0));
    graphics.setColor(withAlpha(prompt.glowColor, 75));
    graphics.setStroke(new BasicStroke((float) clamp(length * 0.09, 4.0, 10.0), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(-length * 0.12, 0.0, length, 0.0));
  }

  private static void drawWeapon(Graphics2D graphics, PromptSpec prompt, double length, Random random) {
    final Color outline = darken(prompt.metalColor, 0.56);
    final Color highlight = lighten(prompt.metalColor, 0.43);
    final Color handle = prompt.accentColor;
    final float handleWidth = (float) clamp(length * 0.055, 2.2, 5.4);
    switch (prompt.targetWeapon) {
      case SICKLE:
        drawHandle(graphics, handle, -length * 0.1, length * 0.58, handleWidth);
        drawCurvedBlade(graphics, outline, prompt.metalColor, highlight, length * 0.54, 0.0, length * 0.5,
            -length * 0.38, true);
        break;
      case SCYTHE:
        drawHandle(graphics, handle, -length * 0.12, length * 0.9, handleWidth);
        drawScytheBlade(graphics, outline, prompt.metalColor, highlight, length);
        drawGrip(graphics, handle, length * 0.34, -length * 0.11, length * 0.08, handleWidth * 0.72f);
        break;
      case SWORD:
      case GREATSWORD:
        drawSword(graphics, prompt, length, prompt.targetWeapon == WeaponType.GREATSWORD);
        break;
      case DAGGER:
        drawSword(graphics, prompt, length, false);
        break;
      case AXE:
      case BATTLEAXE:
        drawHandle(graphics, handle, -length * 0.12, length * 0.88, handleWidth);
        drawAxeHead(graphics, outline, prompt.metalColor, highlight, length * 0.84, length,
            prompt.targetWeapon == WeaponType.BATTLEAXE);
        break;
      case MACE:
        drawHandle(graphics, handle, -length * 0.12, length * 0.82, handleWidth);
        drawMaceHead(graphics, outline, prompt.metalColor, length * 0.88, length * 0.13);
        break;
      case HAMMER:
        drawHandle(graphics, handle, -length * 0.12, length * 0.84, handleWidth);
        drawHammerHead(graphics, outline, prompt.metalColor, highlight, length * 0.86, length * 0.14);
        break;
      case SPEAR:
      case GLAIVE:
      case HALBERD:
      case TRIDENT:
        drawHandle(graphics, handle, -length * 0.12, length * 0.87, handleWidth * 0.82f);
        drawPolearmHead(graphics, prompt, length);
        break;
      case STAFF:
        drawHandle(graphics, handle, -length * 0.18, length, handleWidth * 1.2f);
        graphics.setColor(prompt.metalColor);
        graphics.fill(new Ellipse2D.Double(length - handleWidth * 1.25, -handleWidth * 1.25,
            handleWidth * 2.5, handleWidth * 2.5));
        break;
      case CLUB:
        drawClub(graphics, outline, handle, length, handleWidth);
        break;
      case FLAIL:
        drawFlail(graphics, outline, handle, prompt.metalColor, length, handleWidth);
        break;
      case BOW:
        drawBow(graphics, outline, handle, length, handleWidth);
        break;
      case WHIP:
        drawWhip(graphics, outline, handle, length, handleWidth);
        break;
      default:
        drawSword(graphics, prompt, length, false);
        break;
    }

    if (prompt.ornate) {
      graphics.setColor(lighten(prompt.glowColor, 0.34));
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

  private static void drawSword(Graphics2D graphics, PromptSpec prompt, double length, boolean twoHanded) {
    final double handleLength = length * (twoHanded ? 0.26 : 0.18);
    final double bladeStart = length * 0.08;
    drawHandle(graphics, prompt.accentColor, -handleLength, bladeStart, (float) clamp(length * 0.055, 2.2, 5.0));
    final double halfWidth = clamp(length * (twoHanded ? 0.07 : 0.055), 2.5, 7.0);
    final Path2D blade = new Path2D.Double();
    blade.moveTo(bladeStart, -halfWidth);
    blade.lineTo(length * 0.86, -halfWidth * 0.62);
    blade.lineTo(length, 0.0);
    blade.lineTo(length * 0.86, halfWidth * 0.62);
    blade.lineTo(bladeStart, halfWidth);
    blade.closePath();
    graphics.setColor(darken(prompt.metalColor, 0.58));
    graphics.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.draw(blade);
    graphics.setColor(prompt.metalColor);
    graphics.fill(blade);
    graphics.setColor(lighten(prompt.metalColor, 0.48));
    graphics.setStroke(new BasicStroke(1.25f));
    graphics.draw(new Line2D.Double(bladeStart + 1.0, -halfWidth * 0.4, length * 0.91, -halfWidth * 0.2));
    graphics.setColor(lighten(prompt.accentColor, 0.3));
    graphics.setStroke(new BasicStroke((float) Math.max(2.1, halfWidth * 0.45), BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));
    graphics.draw(new Line2D.Double(bladeStart - halfWidth * 0.15, -halfWidth * 1.35,
        bladeStart - halfWidth * 0.15, halfWidth * 1.35));
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

  private static void drawPolearmHead(Graphics2D graphics, PromptSpec prompt, double length) {
    final double root = length * 0.84;
    final double size = length * 0.18;
    final Color outline = darken(prompt.metalColor, 0.56);
    graphics.setColor(outline);
    graphics.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    if (prompt.targetWeapon == WeaponType.TRIDENT) {
      for (int i = -1; i <= 1; i++) {
        final double y = i * size * 0.42;
        graphics.draw(new Line2D.Double(root, y, length, y));
        final Path2D point = createPoint(length, y, size * 0.32, size * 0.2);
        graphics.setColor(outline);
        graphics.draw(point);
        graphics.setColor(prompt.metalColor);
        graphics.fill(point);
      }
      graphics.setColor(prompt.metalColor);
      graphics.setStroke(new BasicStroke(2.0f));
      graphics.draw(new Line2D.Double(root, -size * 0.42, root, size * 0.42));
    } else {
      final Path2D point = createPoint(length, 0.0, size, size * 0.38);
      graphics.setColor(outline);
      graphics.draw(point);
      graphics.setColor(prompt.metalColor);
      graphics.fill(point);
      if (prompt.targetWeapon == WeaponType.HALBERD || prompt.targetWeapon == WeaponType.GLAIVE) {
        final Path2D blade = new Path2D.Double();
        blade.moveTo(root, -size * 0.14);
        blade.quadTo(root + size * 0.32, -size * 0.9, root + size * 0.68, -size * 0.76);
        blade.lineTo(root + size * 0.52, size * 0.16);
        blade.closePath();
        graphics.setColor(outline);
        graphics.draw(blade);
        graphics.setColor(prompt.metalColor);
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

  private static List<WeaponMatch> findWeaponMatches(String normalized) {
    final List<WeaponMatch> result = new ArrayList<>();
    for (final WeaponType type : WeaponType.values()) {
      for (final String alias : type.aliases) {
        final String needle = " " + normalizeWords(alias).trim() + " ";
        int index = normalized.indexOf(needle);
        while (index >= 0) {
          result.add(new WeaponMatch(index, type, needle.length()));
          index = normalized.indexOf(needle, index + 1);
        }
      }
    }
    result.sort(Comparator.comparingInt((WeaponMatch match) -> match.index)
        .thenComparing((WeaponMatch first, WeaponMatch second) -> Integer.compare(second.length, first.length)));
    final List<WeaponMatch> deduplicated = new ArrayList<>();
    int lastEnd = -1;
    for (final WeaponMatch match : result) {
      if (match.index >= lastEnd) {
        deduplicated.add(match);
        lastEnd = match.index + match.length;
      }
    }
    return deduplicated;
  }

  private static List<ColorMatch> findColorMatches(String normalized) {
    final List<ColorMatch> result = new ArrayList<>();
    for (final Map.Entry<String, Color> entry : NAMED_COLORS.entrySet()) {
      final String needle = " " + entry.getKey() + " ";
      int index = normalized.indexOf(needle);
      while (index >= 0) {
        result.add(new ColorMatch(index, entry.getValue()));
        index = normalized.indexOf(needle, index + 1);
      }
    }
    result.sort(Comparator.comparingInt(match -> match.index));
    return result;
  }

  private static Map<String, Color> createNamedColors() {
    final Map<String, Color> result = new HashMap<>();
    result.put("silver", new Color(205, 214, 226));
    result.put("steel", new Color(156, 172, 188));
    result.put("iron", new Color(126, 135, 145));
    result.put("gold", new Color(230, 177, 48));
    result.put("golden", new Color(230, 177, 48));
    result.put("bronze", new Color(177, 112, 49));
    result.put("black", new Color(35, 38, 44));
    result.put("white", new Color(236, 238, 240));
    result.put("red", new Color(190, 47, 42));
    result.put("crimson", new Color(169, 28, 47));
    result.put("blue", new Color(54, 116, 211));
    result.put("azure", new Color(55, 155, 225));
    result.put("green", new Color(55, 151, 83));
    result.put("emerald", new Color(38, 154, 101));
    result.put("purple", new Color(126, 74, 176));
    result.put("violet", new Color(117, 79, 190));
    result.put("orange", new Color(218, 113, 40));
    result.put("brown", new Color(104, 67, 39));
    return Collections.unmodifiableMap(result);
  }

  private static String normalizeWords(String value) {
    final String text = value != null ? value.toLowerCase(Locale.ENGLISH) : "";
    return " " + text.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ") + " ";
  }

  private static boolean containsAny(String value, String... candidates) {
    for (final String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
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

  private static final class WeaponMatch {
    private final int index;
    private final WeaponType type;
    private final int length;

    private WeaponMatch(int index, WeaponType type, int length) {
      this.index = index;
      this.type = type;
      this.length = length;
    }
  }

  private static final class ColorMatch {
    private final int index;
    private final Color color;

    private ColorMatch(int index, Color color) {
      this.index = index;
      this.color = color;
    }
  }
}
