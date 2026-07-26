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
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;

/**
 * Deterministic, dependency-free creature draft renderer.
 *
 * <p>This is intentionally a constrained procedural illustrator rather than a generative-AI facade. A description is
 * mapped to a supported body plan, palette, proportions and visible traits. The result is coherent across every action
 * and orientation, reproducible from a seed, and suitable as an editable starting point for an artist pipeline.</p>
 */
public final class ProceduralCreatureGenerator {
  public static final int FRAME_SIZE = 112;
  public static final Point FRAME_CENTER = new Point(FRAME_SIZE / 2, 94);

  public interface ProgressListener {
    void onProgress(int completed, int total, Sequence sequence, Direction direction);
  }

  public enum Archetype {
    BIPED("Biped"),
    QUADRUPED("Quadruped"),
    ARACHNID("Arachnid"),
    SERPENT("Serpent");

    private final String label;

    Archetype(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public enum Trait {
    ARMORED,
    GLOWING,
    HORNS,
    TAIL,
    WINGS,
    UNDEAD,
    WEAPON,
    FUR,
    SPIKES
  }

  public static final class Description {
    private final String prompt;
    private final long seed;
    private final Archetype archetype;
    private final EnumSet<Trait> traits;
    private final Color bodyColor;
    private final Color accentColor;
    private final double scale;

    private Description(String prompt, long seed, Archetype archetype, EnumSet<Trait> traits, Color bodyColor,
        Color accentColor, double scale) {
      this.prompt = prompt;
      this.seed = seed;
      this.archetype = archetype;
      this.traits = traits;
      this.bodyColor = bodyColor;
      this.accentColor = accentColor;
      this.scale = scale;
    }

    public String getPrompt() {
      return prompt;
    }

    public long getSeed() {
      return seed;
    }

    public Archetype getArchetype() {
      return archetype;
    }

    public EnumSet<Trait> getTraits() {
      return EnumSet.copyOf(traits);
    }

    public Color getBodyColor() {
      return bodyColor;
    }

    public Color getAccentColor() {
      return accentColor;
    }

    public double getScale() {
      return scale;
    }
  }

  private static final Map<String, Color> NAMED_COLORS = new LinkedHashMap<>();
  private static final List<Color> DEFAULT_COLORS = Arrays.asList(new Color(104, 138, 72), new Color(117, 78, 53),
      new Color(95, 113, 145), new Color(137, 75, 102), new Color(120, 111, 93), new Color(79, 126, 124));

  static {
    NAMED_COLORS.put("black", new Color(38, 40, 43));
    NAMED_COLORS.put("white", new Color(214, 213, 203));
    NAMED_COLORS.put("gray", new Color(111, 116, 119));
    NAMED_COLORS.put("grey", new Color(111, 116, 119));
    NAMED_COLORS.put("silver", new Color(157, 165, 169));
    NAMED_COLORS.put("red", new Color(151, 57, 48));
    NAMED_COLORS.put("crimson", new Color(137, 39, 50));
    NAMED_COLORS.put("orange", new Color(184, 96, 39));
    NAMED_COLORS.put("gold", new Color(189, 146, 49));
    NAMED_COLORS.put("yellow", new Color(190, 166, 52));
    NAMED_COLORS.put("green", new Color(72, 131, 68));
    NAMED_COLORS.put("emerald", new Color(47, 136, 94));
    NAMED_COLORS.put("blue", new Color(61, 102, 157));
    NAMED_COLORS.put("cyan", new Color(48, 151, 165));
    NAMED_COLORS.put("purple", new Color(112, 69, 145));
    NAMED_COLORS.put("violet", new Color(121, 76, 155));
    NAMED_COLORS.put("brown", new Color(117, 78, 53));
    NAMED_COLORS.put("tan", new Color(169, 131, 84));
    NAMED_COLORS.put("pink", new Color(183, 100, 126));
  }

  private ProceduralCreatureGenerator() {
  }

  public static Description parseDescription(String prompt, long seed) {
    final String text = (prompt != null) ? prompt.trim() : "";
    final String normalized = " " + text.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", " ") + " ";

    final Archetype archetype;
    if (containsAny(normalized, " spider ", " arachnid ", " scorpion ", " beetle ", " insect ")) {
      archetype = Archetype.ARACHNID;
    } else if (containsAny(normalized, " snake ", " serpent ", " worm ", " eel ", " naga ", " slug ")) {
      archetype = Archetype.SERPENT;
    } else if (containsAny(normalized, " wolf ", " dog ", " bear ", " lion ", " tiger ", " cat ", " boar ",
        " horse ", " dragon ", " lizard ", " rat ", " quadruped ")) {
      archetype = Archetype.QUADRUPED;
    } else {
      archetype = Archetype.BIPED;
    }

    final EnumSet<Trait> traits = EnumSet.noneOf(Trait.class);
    addTraitIfPresent(traits, Trait.ARMORED, normalized, " armor ", " armored ", " plated ", " knight ");
    addTraitIfPresent(traits, Trait.GLOWING, normalized, " glow ", " glowing ", " luminous ", " spectral ",
        " magic ", " magical ");
    addTraitIfPresent(traits, Trait.HORNS, normalized, " horn ", " horns ", " horned ", " demon ", " dragon ");
    addTraitIfPresent(traits, Trait.TAIL, normalized, " tail ", " tailed ", " dragon ", " lizard ", " wolf ",
        " dog ", " cat ", " lion ", " tiger ");
    addTraitIfPresent(traits, Trait.WINGS, normalized, " wing ", " wings ", " winged ", " dragon ", " bat ",
        " bird ");
    addTraitIfPresent(traits, Trait.UNDEAD, normalized, " undead ", " zombie ", " skeleton ", " ghost ",
        " ghoul ", " lich ");
    addTraitIfPresent(traits, Trait.WEAPON, normalized, " sword ", " axe ", " mace ", " staff ", " spear ",
        " weapon ", " knight ", " warrior ");
    addTraitIfPresent(traits, Trait.FUR, normalized, " fur ", " furry ", " wolf ", " bear ", " cat ", " lion ",
        " tiger ");
    addTraitIfPresent(traits, Trait.SPIKES, normalized, " spike ", " spikes ", " spiked ", " thorn ",
        " scorpion ");

    double scale = 1.0;
    if (containsAny(normalized, " tiny ", " miniature ")) {
      scale = 0.68;
    } else if (containsAny(normalized, " small ", " short ")) {
      scale = 0.82;
    } else if (containsAny(normalized, " huge ", " giant ", " massive ", " enormous ")) {
      scale = 1.22;
    } else if (containsAny(normalized, " large ", " tall ")) {
      scale = 1.1;
    }

    final List<Color> colors = new ArrayList<>();
    for (final Map.Entry<String, Color> entry : NAMED_COLORS.entrySet()) {
      if (normalized.contains(" " + entry.getKey() + " ")) {
        colors.add(entry.getValue());
      }
    }

    final Random random = new Random(seed ^ text.hashCode());
    final Color body = !colors.isEmpty() ? colors.get(0) : DEFAULT_COLORS.get(random.nextInt(DEFAULT_COLORS.size()));
    final Color accent = colors.size() > 1 ? colors.get(1) : deriveAccent(body, random);
    return new Description(text, seed, archetype, traits, body, accent, scale);
  }

  public static CreatureAnimationModel generate(String prompt, long seed, ProgressListener listener) {
    final Description description = parseDescription(prompt, seed);
    final CreatureAnimationModel model = new CreatureAnimationModel();
    int completed = 0;
    int total = 0;
    for (final Sequence sequence : Sequence.values()) {
      total += sequence.getSuggestedFrameCount() * Direction.values().length;
    }

    for (final Sequence sequence : Sequence.values()) {
      final int frameCount = sequence.getSuggestedFrameCount();
      for (final Direction direction : Direction.values()) {
        final List<AnimationFrame> frames = new ArrayList<>(frameCount);
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
          final BufferedImage image = render(description, sequence, direction, frameIndex, frameCount);
          final String source = String.format(Locale.ENGLISH, "procedural:%s:%s:%d", sequence.getCode(),
              direction.getCode(), frameIndex);
          frames.add(new AnimationFrame(image, FRAME_CENTER, source));
          completed++;
          if (listener != null) {
            listener.onProgress(completed, total, sequence, direction);
          }
        }
        model.replaceFrames(sequence, direction, frames);
      }
    }
    return model;
  }

  private static BufferedImage render(Description description, Sequence sequence, Direction direction, int frameIndex,
      int frameCount) {
    final int renderScale = 2;
    final int size = FRAME_SIZE * renderScale;
    final BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = canvas.createGraphics();
    try {
      g.scale(renderScale, renderScale);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

      final Pose pose = Pose.forFrame(sequence, frameIndex, frameCount);
      final double angle = Math.PI / 2.0 + direction.getCycleOffset() * Math.PI / 8.0;
      final double dx = Math.cos(angle);
      final double dy = Math.sin(angle);
      final Random random = new Random(description.seed ^ (direction.ordinal() * 0x9e3779b9L));

      drawShadow(g, description, pose);
      if (description.traits.contains(Trait.GLOWING)) {
        drawAura(g, description, pose);
      }

      final AffineTransform original = g.getTransform();
      g.translate(FRAME_CENTER.x, FRAME_CENTER.y);
      g.scale(description.scale, description.scale);
      g.translate(-FRAME_CENTER.x, -FRAME_CENTER.y);
      switch (description.archetype) {
        case QUADRUPED:
          drawQuadruped(g, description, pose, dx, dy, random);
          break;
        case ARACHNID:
          drawArachnid(g, description, pose, dx, dy, random);
          break;
        case SERPENT:
          drawSerpent(g, description, pose, dx, dy, random);
          break;
        case BIPED:
        default:
          drawBiped(g, description, pose, dx, dy, random);
          break;
      }
      g.setTransform(original);
    } finally {
      g.dispose();
    }

    final BufferedImage result = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D out = result.createGraphics();
    try {
      out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      out.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      out.setComposite(AlphaComposite.Src);
      out.drawImage(canvas, 0, 0, FRAME_SIZE, FRAME_SIZE, null);
    } finally {
      out.dispose();
      canvas.flush();
    }
    return result;
  }

  private static void drawShadow(Graphics2D g, Description description, Pose pose) {
    final int alpha = (int) (82 * (1.0 - pose.fade * 0.45));
    g.setColor(new Color(15, 15, 18, clamp(alpha)));
    final double width = (description.archetype == Archetype.SERPENT) ? 55.0 : 44.0;
    final double height = (description.archetype == Archetype.BIPED) ? 10.0 : 13.0;
    g.fill(new Ellipse2D.Double(FRAME_CENTER.x - width / 2.0, FRAME_CENTER.y - height / 2.0, width, height));
  }

  private static void drawAura(Graphics2D g, Description description, Pose pose) {
    final int alpha = clamp((int) (32 + 24 * Math.sin(pose.phase * Math.PI * 2.0)));
    g.setColor(withAlpha(lighten(description.accentColor, 0.35), alpha));
    final double radius = 28.0 + 4.0 * Math.sin(pose.phase * Math.PI * 2.0);
    g.fill(new Ellipse2D.Double(FRAME_CENTER.x - radius, FRAME_CENTER.y - 70.0 - radius / 2.0, radius * 2.0,
        radius * 2.0));
  }

  private static void drawBiped(Graphics2D g, Description d, Pose p, double dx, double dy, Random random) {
    final double ground = FRAME_CENTER.y - p.fall * 18.0;
    final double bodyX = FRAME_CENTER.x + p.lean * dx * 7.0;
    final double bodyY = ground - 38.0 + p.bob + p.fall * 24.0;
    final double side = Math.max(0.25, Math.abs(dx));
    final Color outline = darken(d.bodyColor, 0.58);
    final Color shadow = darken(d.bodyColor, 0.25);
    final Color light = lighten(d.bodyColor, 0.22);

    if (d.traits.contains(Trait.WINGS)) {
      drawWings(g, d, bodyX, bodyY + 3.0, dx, p);
    }
    if (d.traits.contains(Trait.TAIL)) {
      drawTail(g, d, bodyX, bodyY + 9.0, -dx, -dy, p);
    }

    final double hipX = bodyX - dx * 2.0;
    final double hipY = bodyY + 17.0;
    final double stride = p.stride * 8.0;
    drawLimb(g, hipX - 4.0, hipY, hipX - 5.0 + dx * stride, ground - 1.0, outline, shadow, 6.5f);
    drawLimb(g, hipX + 4.0, hipY, hipX + 5.0 - dx * stride, ground - 1.0, outline, d.bodyColor, 7.0f);

    g.setColor(outline);
    g.fill(new Ellipse2D.Double(bodyX - 14.0 - side * 2.5, bodyY - 19.0, 28.0 + side * 5.0, 42.0));
    g.setColor(d.traits.contains(Trait.UNDEAD) ? desaturate(light, 0.65) : d.bodyColor);
    g.fill(new Ellipse2D.Double(bodyX - 12.5 - side * 2.0, bodyY - 18.0, 25.0 + side * 4.0, 39.0));

    if (d.traits.contains(Trait.ARMORED)) {
      g.setColor(darken(d.accentColor, 0.25));
      g.fillRoundRect((int) bodyX - 13, (int) bodyY - 13, 26, 24, 7, 7);
      g.setColor(lighten(d.accentColor, 0.3));
      g.setStroke(new BasicStroke(2.0f));
      g.drawLine((int) bodyX - 10, (int) bodyY - 7, (int) bodyX + 10, (int) bodyY - 7);
      g.drawLine((int) bodyX, (int) bodyY - 10, (int) bodyX, (int) bodyY + 8);
    } else if (d.traits.contains(Trait.FUR)) {
      drawFurMarks(g, bodyX, bodyY, d, random);
    }

    final double headX = bodyX + dx * (9.0 + side * 3.0);
    final double headY = bodyY - 23.0 + dy * 2.0;
    drawArm(g, d, p, bodyX, bodyY, dx, dy, false);
    drawHead(g, d, p, headX, headY, dx, dy);
    drawArm(g, d, p, bodyX, bodyY, dx, dy, true);

    if (p.magic > 0.01) {
      drawMagic(g, d, bodyX + dx * (24.0 + p.magic * 9.0), bodyY - 4.0 + dy * 8.0, p.magic, p.phase);
    }
  }

  private static void drawQuadruped(Graphics2D g, Description d, Pose p, double dx, double dy, Random random) {
    final double ground = FRAME_CENTER.y - p.fall * 13.0;
    final double bodyX = FRAME_CENTER.x + p.lean * dx * 7.0;
    final double bodyY = ground - 26.0 + p.bob + p.fall * 18.0;
    final double length = 34.0;
    final double projectedLength = 15.0 + Math.abs(dx) * length;
    final Color outline = darken(d.bodyColor, 0.58);

    if (d.traits.contains(Trait.WINGS)) {
      drawWings(g, d, bodyX, bodyY - 5.0, dx, p);
    }
    if (d.traits.contains(Trait.TAIL)) {
      drawTail(g, d, bodyX - dx * projectedLength / 2.0, bodyY, -dx, -dy, p);
    }

    final double stride = p.stride * 7.0;
    for (int i = 0; i < 4; i++) {
      final boolean front = i >= 2;
      final double along = front ? 0.35 : -0.35;
      final double sideOffset = (i % 2 == 0) ? -3.0 : 3.0;
      final double legX = bodyX + dx * projectedLength * along + sideOffset * -dy;
      final double legY = bodyY + 7.0 + sideOffset * dx * 0.25;
      final double step = ((i % 2 == 0) ? stride : -stride);
      drawLimb(g, legX, legY, legX + dx * step, ground - 1.0, outline,
          (i % 2 == 0) ? darken(d.bodyColor, 0.18) : d.bodyColor, (i % 2 == 0) ? 5.0f : 6.0f);
    }

    drawRotatedEllipse(g, outline, bodyX, bodyY, projectedLength + 7.0, 23.0, Math.atan2(dy, dx));
    drawRotatedEllipse(g, d.bodyColor, bodyX, bodyY - 1.0, projectedLength + 3.0, 20.0, Math.atan2(dy, dx));
    if (d.traits.contains(Trait.ARMORED)) {
      drawRotatedEllipse(g, darken(d.accentColor, 0.16), bodyX - dx * 2.0, bodyY - 5.0,
          projectedLength * 0.72, 13.0, Math.atan2(dy, dx));
    } else if (d.traits.contains(Trait.FUR)) {
      drawFurMarks(g, bodyX, bodyY, d, random);
    }

    final double reach = p.attack * 8.0;
    final double headX = bodyX + dx * (projectedLength / 2.0 + 8.0 + reach);
    final double headY = bodyY - 6.0 + dy * 5.0 + p.attack * 2.0;
    drawHead(g, d, p, headX, headY, dx, dy);
    if (p.magic > 0.01) {
      drawMagic(g, d, headX + dx * 15.0, headY + dy * 10.0, p.magic, p.phase);
    }
  }

  private static void drawArachnid(Graphics2D g, Description d, Pose p, double dx, double dy, Random random) {
    final double ground = FRAME_CENTER.y - 4.0;
    final double bodyX = FRAME_CENTER.x + p.lean * dx * 6.0;
    final double bodyY = ground - 18.0 + p.bob + p.fall * 12.0;
    final Color outline = darken(d.bodyColor, 0.62);
    final double stride = p.stride * 5.0;

    g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    for (int i = 0; i < 8; i++) {
      final double side = (i < 4) ? -1.0 : 1.0;
      final int row = i % 4;
      final double along = (row - 1.5) * 7.0;
      final double rootX = bodyX + dx * along;
      final double rootY = bodyY + dy * along * 0.25;
      final double middleX = rootX + side * -dy * (14.0 + row);
      final double middleY = rootY + side * dx * 3.0 + 4.0;
      final double footX = middleX + side * -dy * 9.0 + dx * stride * ((row % 2 == 0) ? 1.0 : -1.0);
      g.setColor(outline);
      final Path2D leg = new Path2D.Double();
      leg.moveTo(rootX, rootY);
      leg.lineTo(middleX, middleY);
      leg.lineTo(footX, ground);
      g.draw(leg);
      g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(d.bodyColor);
      g.draw(leg);
      g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    drawRotatedEllipse(g, outline, bodyX - dx * 5.0, bodyY, 31.0, 24.0, Math.atan2(dy, dx));
    drawRotatedEllipse(g, d.bodyColor, bodyX - dx * 5.0, bodyY - 1.0, 28.0, 21.0, Math.atan2(dy, dx));
    final double headX = bodyX + dx * (15.0 + p.attack * 7.0);
    final double headY = bodyY - 2.0 + dy * 3.0;
    drawRotatedEllipse(g, outline, headX, headY, 19.0, 17.0, Math.atan2(dy, dx));
    drawRotatedEllipse(g, lighten(d.bodyColor, 0.1), headX, headY - 1.0, 16.0, 14.0,
        Math.atan2(dy, dx));
    drawEyes(g, d, headX, headY, dx, dy, 3);

    if (d.traits.contains(Trait.SPIKES)) {
      drawSpikes(g, d, bodyX, bodyY - 10.0, dx, 5);
    }
    if (p.magic > 0.01) {
      drawMagic(g, d, headX + dx * 15.0, headY + dy * 10.0, p.magic, p.phase);
    }
  }

  private static void drawSerpent(Graphics2D g, Description d, Pose p, double dx, double dy, Random random) {
    final double ground = FRAME_CENTER.y - 5.0;
    final double bodyX = FRAME_CENTER.x + p.lean * dx * 8.0;
    final double bodyY = ground - 9.0 + p.fall * 5.0;
    final Color outline = darken(d.bodyColor, 0.62);
    final Path2D body = new Path2D.Double();
    for (int i = 0; i < 9; i++) {
      final double t = i / 8.0;
      final double wave = Math.sin(t * Math.PI * 2.5 + p.phase * Math.PI * 2.0) * (8.0 * (1.0 - t));
      final double x = bodyX - dx * t * 47.0 + -dy * wave;
      final double y = bodyY - dy * t * 10.0 + dx * wave * 0.35 - Math.sin(t * Math.PI) * 9.0;
      if (i == 0) {
        body.moveTo(x, y);
      } else {
        body.lineTo(x, y);
      }
    }
    g.setStroke(new BasicStroke(16.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(outline);
    g.draw(body);
    g.setStroke(new BasicStroke(12.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(d.bodyColor);
    g.draw(body);
    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(lighten(d.bodyColor, 0.24));
    g.draw(body);

    final double reach = p.attack * 10.0;
    final double headX = bodyX + dx * reach;
    final double headY = bodyY - 10.0 + dy * reach * 0.2;
    drawHead(g, d, p, headX, headY, dx, dy);
    if (d.traits.contains(Trait.SPIKES)) {
      drawSpikes(g, d, bodyX - dx * 15.0, bodyY - 12.0, dx, 4);
    }
    if (p.magic > 0.01) {
      drawMagic(g, d, headX + dx * 16.0, headY + dy * 9.0, p.magic, p.phase);
    }
  }

  private static void drawHead(Graphics2D g, Description d, Pose p, double x, double y, double dx, double dy) {
    final Color outline = darken(d.bodyColor, 0.62);
    final double width = (d.archetype == Archetype.BIPED) ? 22.0 : 20.0;
    final double height = (d.archetype == Archetype.SERPENT) ? 16.0 : 20.0;
    drawRotatedEllipse(g, outline, x, y, width + 4.0, height + 4.0, Math.atan2(dy, dx));
    drawRotatedEllipse(g, d.traits.contains(Trait.UNDEAD) ? desaturate(lighten(d.bodyColor, 0.18), 0.75)
        : lighten(d.bodyColor, 0.08), x, y - 1.0, width, height, Math.atan2(dy, dx));

    if (d.traits.contains(Trait.HORNS)) {
      final Path2D left = new Path2D.Double();
      left.moveTo(x - 6.0, y - 7.0);
      left.lineTo(x - 11.0 - dx * 2.0, y - 18.0);
      left.lineTo(x - 2.0, y - 8.0);
      left.closePath();
      final Path2D right = new Path2D.Double();
      right.moveTo(x + 6.0, y - 7.0);
      right.lineTo(x + 11.0 - dx * 2.0, y - 18.0);
      right.lineTo(x + 2.0, y - 8.0);
      right.closePath();
      g.setColor(lighten(d.accentColor, 0.28));
      g.fill(left);
      g.fill(right);
    }
    if (d.traits.contains(Trait.ARMORED)) {
      g.setColor(darken(d.accentColor, 0.22));
      g.fillArc((int) x - 10, (int) y - 12, 20, 17, 0, 180);
    }
    drawEyes(g, d, x, y, dx, dy, 2);
  }

  private static void drawEyes(Graphics2D g, Description d, double x, double y, double dx, double dy, int count) {
    final Color eye = d.traits.contains(Trait.GLOWING) || d.traits.contains(Trait.UNDEAD)
        ? lighten(d.accentColor, 0.48) : new Color(235, 188, 68);
    g.setColor(eye);
    final double sideX = -dy;
    final double sideY = dx;
    for (int i = 0; i < count; i++) {
      final double offset = (i - (count - 1) / 2.0) * 3.0;
      final double eyeX = x + dx * 7.0 + sideX * offset;
      final double eyeY = y + dy * 4.0 + sideY * offset - 2.0;
      g.fill(new Ellipse2D.Double(eyeX - 1.35, eyeY - 1.35, 2.7, 2.7));
    }
  }

  private static void drawArm(Graphics2D g, Description d, Pose p, double bodyX, double bodyY, double dx, double dy,
      boolean foreground) {
    final double side = foreground ? 1.0 : -1.0;
    final double shoulderX = bodyX + side * -dy * 8.0 + dx * 2.0;
    final double shoulderY = bodyY - 8.0 + side * dx * 2.0;
    final double attackVariant = p.attackVariant * side;
    final double handX = shoulderX + dx * (11.0 + p.attack * 17.0) + -dy * (5.0 + attackVariant * 5.0);
    final double handY = shoulderY + 14.0 + dy * (6.0 + p.attack * 8.0) - p.magic * 14.0
        + attackVariant * 2.0;
    final Color limb = foreground ? d.bodyColor : darken(d.bodyColor, 0.18);
    drawLimb(g, shoulderX, shoulderY, handX, handY, darken(d.bodyColor, 0.62), limb,
        foreground ? 6.0f : 5.0f);

    if (foreground && d.traits.contains(Trait.WEAPON)) {
      final double weaponLength = 25.0;
      final double wx = dx * weaponLength + -dy * p.attackVariant * 10.0;
      final double wy = dy * weaponLength + dx * p.attackVariant * 10.0 - p.attack * 8.0;
      g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(darken(d.accentColor, 0.38));
      g.drawLine((int) handX, (int) handY, (int) (handX + wx), (int) (handY + wy));
      g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(lighten(d.accentColor, 0.46));
      g.drawLine((int) (handX + dx * 5.0), (int) (handY + dy * 5.0), (int) (handX + wx),
          (int) (handY + wy));
    }
  }

  private static void drawLimb(Graphics2D g, double x1, double y1, double x2, double y2, Color outline, Color fill,
      float width) {
    final double midX = (x1 + x2) / 2.0 + (y2 - y1) * 0.08;
    final double midY = (y1 + y2) / 2.0 - (x2 - x1) * 0.08;
    final Path2D limb = new Path2D.Double();
    limb.moveTo(x1, y1);
    limb.quadTo(midX, midY, x2, y2);
    g.setStroke(new BasicStroke(width + 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(outline);
    g.draw(limb);
    g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(fill);
    g.draw(limb);
  }

  private static void drawTail(Graphics2D g, Description d, double x, double y, double dx, double dy, Pose p) {
    final Path2D tail = new Path2D.Double();
    tail.moveTo(x, y);
    tail.curveTo(x + dx * 12.0 - dy * 5.0, y + dy * 5.0, x + dx * 22.0 + dy * 7.0,
        y + 7.0 + Math.sin(p.phase * Math.PI * 2.0) * 4.0, x + dx * 31.0,
        y + 2.0 + Math.sin(p.phase * Math.PI * 2.0) * 7.0);
    g.setStroke(new BasicStroke(8.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(darken(d.bodyColor, 0.62));
    g.draw(tail);
    g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(d.bodyColor);
    g.draw(tail);
  }

  private static void drawWings(Graphics2D g, Description d, double x, double y, double dx, Pose p) {
    final double flap = 9.0 + Math.sin(p.phase * Math.PI * 2.0) * 7.0 + p.magic * 7.0;
    final Path2D left = new Path2D.Double();
    left.moveTo(x - 5.0, y);
    left.quadTo(x - 24.0, y - 22.0 - flap, x - 34.0, y - 5.0);
    left.quadTo(x - 18.0, y - 8.0, x - 4.0, y + 7.0);
    left.closePath();
    final Path2D right = new Path2D.Double();
    right.moveTo(x + 5.0, y);
    right.quadTo(x + 24.0, y - 22.0 - flap, x + 34.0, y - 5.0);
    right.quadTo(x + 18.0, y - 8.0, x + 4.0, y + 7.0);
    right.closePath();
    g.setColor(withAlpha(darken(d.accentColor, 0.22), 190));
    g.fill(left);
    g.fill(right);
    g.setColor(withAlpha(lighten(d.accentColor, 0.22), 190));
    g.setStroke(new BasicStroke(1.7f));
    g.draw(left);
    g.draw(right);
  }

  private static void drawSpikes(Graphics2D g, Description d, double x, double y, double dx, int count) {
    g.setColor(lighten(d.accentColor, 0.25));
    for (int i = 0; i < count; i++) {
      final double px = x - dx * (i - count / 2.0) * 5.0;
      final Path2D spike = new Path2D.Double();
      spike.moveTo(px - 3.0, y + 2.0);
      spike.lineTo(px, y - 9.0 - (i % 2) * 3.0);
      spike.lineTo(px + 3.0, y + 2.0);
      spike.closePath();
      g.fill(spike);
    }
  }

  private static void drawFurMarks(Graphics2D g, double x, double y, Description d, Random random) {
    g.setColor(withAlpha(lighten(d.bodyColor, 0.25), 150));
    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    for (int i = 0; i < 7; i++) {
      final double px = x - 9.0 + random.nextDouble() * 18.0;
      final double py = y - 11.0 + random.nextDouble() * 24.0;
      g.drawLine((int) px, (int) py, (int) (px + 2.0), (int) (py + 3.0));
    }
  }

  private static void drawMagic(Graphics2D g, Description d, double x, double y, double intensity, double phase) {
    final double radius = 4.0 + intensity * 6.0;
    g.setColor(withAlpha(lighten(d.accentColor, 0.45), clamp((int) (60 + intensity * 80))));
    g.fill(new Ellipse2D.Double(x - radius * 1.8, y - radius * 1.8, radius * 3.6, radius * 3.6));
    g.setColor(withAlpha(lighten(d.accentColor, 0.7), clamp((int) (120 + intensity * 120))));
    g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2.0, radius * 2.0));
    g.setColor(Color.WHITE);
    g.fill(new Ellipse2D.Double(x - radius * 0.33, y - radius * 0.33, radius * 0.66, radius * 0.66));
    g.setStroke(new BasicStroke(1.4f));
    for (int i = 0; i < 3; i++) {
      final double a = phase * Math.PI * 2.0 + i * Math.PI * 2.0 / 3.0;
      final double sx = x + Math.cos(a) * radius * 2.0;
      final double sy = y + Math.sin(a) * radius * 1.4;
      g.draw(new Ellipse2D.Double(sx - 1.0, sy - 1.0, 2.0, 2.0));
    }
  }

  private static void drawRotatedEllipse(Graphics2D g, Color color, double x, double y, double width, double height,
      double angle) {
    final AffineTransform old = g.getTransform();
    g.translate(x, y);
    g.rotate(angle);
    g.setColor(color);
    g.fill(new Ellipse2D.Double(-width / 2.0, -height / 2.0, width, height));
    g.setTransform(old);
  }

  private static final class Pose {
    private double phase;
    private double bob;
    private double stride;
    private double lean;
    private double fall;
    private double fade;
    private double attack;
    private double attackVariant;
    private double magic;

    private static Pose forFrame(Sequence sequence, int frameIndex, int frameCount) {
      final Pose pose = new Pose();
      pose.phase = frameCount > 0 ? (double) frameIndex / frameCount : 0.0;
      final double wave = Math.sin(pose.phase * Math.PI * 2.0);
      final double pulse = Math.sin(pose.phase * Math.PI);
      switch (sequence) {
        case WALK:
          pose.stride = wave;
          pose.bob = -Math.abs(wave) * 2.4;
          break;
        case STANCE:
          pose.stride = wave * 0.18;
          pose.bob = wave * 1.2;
          pose.lean = Math.sin(pose.phase * Math.PI * 4.0) * 0.08;
          break;
        case STAND:
          pose.bob = wave * 0.7;
          pose.lean = wave * 0.06;
          break;
        case GET_HIT:
          pose.lean = -pulse;
          pose.bob = pulse * 2.5;
          break;
        case DIE:
          pose.fall = smooth(pose.phase);
          pose.lean = -pose.fall * 1.2;
          pose.fade = Math.max(0.0, pose.fall - 0.72) / 0.28;
          break;
        case TWITCH:
          pose.fall = 0.9;
          pose.lean = Math.sin(pose.phase * Math.PI * 4.0) * 0.35;
          pose.bob = -Math.abs(wave) * 2.0;
          break;
        case SLEEP:
          pose.fall = 1.0;
          pose.bob = wave * 0.45;
          break;
        case GET_UP:
          pose.fall = 1.0 - smooth(pose.phase);
          pose.lean = -pose.fall;
          break;
        case ATTACK_1:
        case ATTACK_2:
        case ATTACK_3:
        case ATTACK_4:
        case ATTACK_5:
          pose.attack = pulse;
          pose.lean = pulse * 0.75;
          pose.bob = -pulse * 1.5;
          pose.attackVariant = sequence.ordinal() - Sequence.ATTACK_3.ordinal();
          pose.attackVariant /= 2.0;
          break;
        case CONJURE:
          pose.magic = Math.min(1.0, pose.phase * 1.7);
          pose.bob = -pulse * 2.0;
          pose.lean = pulse * 0.18;
          break;
        case CAST:
          pose.magic = pulse;
          pose.lean = pulse * 0.45;
          pose.bob = -pulse * 2.8;
          break;
        default:
          break;
      }
      return pose;
    }
  }

  private static double smooth(double value) {
    final double clamped = Math.max(0.0, Math.min(1.0, value));
    return clamped * clamped * (3.0 - 2.0 * clamped);
  }

  private static void addTraitIfPresent(EnumSet<Trait> traits, Trait trait, String text, String... values) {
    if (containsAny(text, values)) {
      traits.add(trait);
    }
  }

  private static boolean containsAny(String value, String... candidates) {
    for (final String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private static Color deriveAccent(Color body, Random random) {
    final float[] hsb = Color.RGBtoHSB(body.getRed(), body.getGreen(), body.getBlue(), null);
    final float hue = (hsb[0] + 0.34f + random.nextFloat() * 0.24f) % 1.0f;
    return Color.getHSBColor(hue, Math.max(0.42f, hsb[1]), Math.min(0.92f, Math.max(0.58f, hsb[2] + 0.18f)));
  }

  private static Color lighten(Color color, double amount) {
    return mix(color, Color.WHITE, amount);
  }

  private static Color darken(Color color, double amount) {
    return mix(color, Color.BLACK, amount);
  }

  private static Color desaturate(Color color, double amount) {
    final int gray = (color.getRed() * 30 + color.getGreen() * 59 + color.getBlue() * 11) / 100;
    return mix(color, new Color(gray, gray, gray), amount);
  }

  private static Color mix(Color first, Color second, double amount) {
    final double ratio = Math.max(0.0, Math.min(1.0, amount));
    final int red = clamp((int) Math.round(first.getRed() * (1.0 - ratio) + second.getRed() * ratio));
    final int green = clamp((int) Math.round(first.getGreen() * (1.0 - ratio) + second.getGreen() * ratio));
    final int blue = clamp((int) Math.round(first.getBlue() * (1.0 - ratio) + second.getBlue() * ratio));
    final int alpha = clamp((int) Math.round(first.getAlpha() * (1.0 - ratio) + second.getAlpha() * ratio));
    return new Color(red, green, blue, alpha);
  }

  private static Color withAlpha(Color color, int alpha) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(alpha));
  }

  private static int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }
}
