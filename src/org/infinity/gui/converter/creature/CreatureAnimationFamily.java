// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.OutputSlot;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.cre.decoder.MonsterPlanescapeDecoder;
import org.infinity.resource.cre.decoder.util.AnimationInfo;

/**
 * Declarative engine resource layouts for every real Infinity Engine creature animation family.
 *
 * <p>The source editor deliberately retains one rich, neutral action model. A family plan maps that model to the exact
 * filenames, cycle indices, direction sets, quadrants and action aliases consumed by the corresponding Near Infinity
 * decoder. Eastern artwork is derived by horizontal reflection only where the target family stores it explicitly.</p>
 */
public enum CreatureAnimationFamily {
  EFFECT(AnimationInfo.Type.EFFECT, "0000", "Effect", 0x0000, SplitMode.NONE, 0, 0, true, true),
  MONSTER_QUADRANT(AnimationInfo.Type.MONSTER_QUADRANT, "1000", "Monster quadrant", 0x1000, SplitMode.NONE, 4, 0,
      true, false),
  MONSTER_MULTI(AnimationInfo.Type.MONSTER_MULTI, "1000", "Monster multi", 0x1200, SplitMode.NONE, 9, 0, false,
      false),
  MONSTER_MULTI_NEW(AnimationInfo.Type.MONSTER_MULTI_NEW, "1000", "Monster multi (new)", 0x1300,
      SplitMode.OPTIONAL, 4, 0, true, true),
  MONSTER_LAYERED_SPELL(AnimationInfo.Type.MONSTER_LAYERED_SPELL, "2000", "Monster layered spell", 0x2000,
      SplitMode.NONE, 0, 0, true, false),
  MONSTER_ANKHEG(AnimationInfo.Type.MONSTER_ANKHEG, "3000", "Monster ankheg", 0x3000, SplitMode.NONE, 0, 0, true,
      false),
  TOWN_STATIC(AnimationInfo.Type.TOWN_STATIC, "4000", "Town static", 0x4000, SplitMode.NONE, 0, 0, true, false),
  CHARACTER(AnimationInfo.Type.CHARACTER, "5000/6000", "Character", 0x5000, SplitMode.REQUIRED, 0, 1, true, false),
  CHARACTER_OLD(AnimationInfo.Type.CHARACTER_OLD, "5000/6000", "Character (old)", 0x5400, SplitMode.NONE, 0, 1,
      true, false),
  MONSTER(AnimationInfo.Type.MONSTER, "7000", "Monster", 0x7303, SplitMode.OPTIONAL, 0, 0, true, true),
  MONSTER_OLD(AnimationInfo.Type.MONSTER_OLD, "7000", "Monster (old)", 0x7000, SplitMode.NONE, 0, 0, true, true),
  MONSTER_LAYERED(AnimationInfo.Type.MONSTER_LAYERED, "8000", "Monster layered", 0x8000, SplitMode.NONE, 0, 0, true,
      false),
  MONSTER_LARGE(AnimationInfo.Type.MONSTER_LARGE, "9000", "Monster large", 0x9000, SplitMode.NONE, 0, 0, true,
      false),
  MONSTER_LARGE_16(AnimationInfo.Type.MONSTER_LARGE_16, "A000", "Monster large (16 directions)", 0xa000,
      SplitMode.NONE, 0, 0, true, false),
  AMBIENT_STATIC(AnimationInfo.Type.AMBIENT_STATIC, "B000", "Ambient static", 0xb000, SplitMode.NONE, 0, 0, true,
      false),
  AMBIENT(AnimationInfo.Type.AMBIENT, "C000", "Ambient", 0xc000, SplitMode.NONE, 0, 0, true, false),
  FLYING(AnimationInfo.Type.FLYING, "D000", "Flying", 0xd000, SplitMode.NONE, 0, 0, true, false),
  MONSTER_ICEWIND(AnimationInfo.Type.MONSTER_ICEWIND, "E000", "Monster Icewind", 0xe000, SplitMode.NONE, 0, 0,
      false, true),
  MONSTER_PLANESCAPE(AnimationInfo.Type.MONSTER_PLANESCAPE, "F000", "Monster Planescape", 0xf000, SplitMode.NONE, 0,
      0, true, false);

  /** How the family treats the {@code split_bams} definition property. */
  public enum SplitMode {
    NONE,
    OPTIONAL,
    REQUIRED
  }

  /** One target BAM cycle and the neutral source cell used to populate it. */
  public static final class CyclePlan {
    private final int cycleIndex;
    private final Sequence sequence;
    private final int directionIndex;
    private final boolean reversed;
    private final boolean blank;
    private final int quadrantIndex;
    private final int quadrantCount;

    CyclePlan(int cycleIndex, Sequence sequence, int directionIndex, boolean reversed, boolean blank,
        int quadrantIndex, int quadrantCount) {
      this.cycleIndex = cycleIndex;
      this.sequence = Objects.requireNonNull(sequence);
      this.directionIndex = directionIndex;
      this.reversed = reversed;
      this.blank = blank;
      this.quadrantIndex = quadrantIndex;
      this.quadrantCount = quadrantCount;
    }

    public int getCycleIndex() {
      return cycleIndex;
    }

    public Sequence getSequence() {
      return sequence;
    }

    /** Returns the canonical 16-direction index (S=0, N=8, E=12, SSE=15). */
    public int getDirectionIndex() {
      return directionIndex;
    }

    public boolean isMirrored() {
      return directionIndex > Direction.N.getCycleOffset();
    }

    public Direction getSourceDirection() {
      return Direction.values()[isMirrored() ? 16 - directionIndex : directionIndex];
    }

    public boolean isReversed() {
      return reversed;
    }

    public boolean isBlank() {
      return blank;
    }

    public int getQuadrantIndex() {
      return quadrantIndex;
    }

    public int getQuadrantCount() {
      return quadrantCount;
    }

    private boolean sameMapping(CyclePlan other) {
      return other != null && sequence == other.sequence && directionIndex == other.directionIndex
          && reversed == other.reversed && blank == other.blank && quadrantIndex == other.quadrantIndex
          && quadrantCount == other.quadrantCount;
    }
  }

  /** Complete cycle plan for one output BAM resource. */
  public static final class ResourcePlan {
    private final String fileName;
    private final List<CyclePlan> cycles;
    private final int cycleCount;
    private final boolean preserveExistingCycles;

    private ResourcePlan(String fileName, Map<Integer, CyclePlan> cycles, boolean preserveExistingCycles) {
      this.fileName = fileName;
      this.cycles = Collections.unmodifiableList(new ArrayList<>(cycles.values()));
      this.cycleCount = cycles.isEmpty() ? 0 : Collections.max(cycles.keySet()) + 1;
      this.preserveExistingCycles = preserveExistingCycles;
    }

    public String getFileName() {
      return fileName;
    }

    public List<CyclePlan> getCycles() {
      return cycles;
    }

    public int getCycleCount() {
      return cycleCount;
    }

    /**
     * Returns whether cycles outside this plan must be copied from the installed BAM.
     *
     * <p>Classic engines commonly share BAMs or address only selected cycles in a hardcoded profile definition.</p>
     */
    public boolean isPreserveExistingCycles() {
      return preserveExistingCycles;
    }
  }

  /** Immutable resource and PST action map produced for one export definition. */
  public static final class FamilyLayout {
    private final Map<String, ResourcePlan> resources;
    private final Map<String, String> actionResrefs;

    private FamilyLayout(Map<String, MutableResourcePlan> resources, Map<String, String> actionResrefs) {
      final Map<String, ResourcePlan> immutableResources = new LinkedHashMap<>();
      for (final Map.Entry<String, MutableResourcePlan> entry : resources.entrySet()) {
        immutableResources.put(entry.getKey(), new ResourcePlan(entry.getKey(), entry.getValue().cycles,
            entry.getValue().preserveExistingCycles));
      }
      this.resources = Collections.unmodifiableMap(immutableResources);
      this.actionResrefs = Collections.unmodifiableMap(new LinkedHashMap<>(actionResrefs));
    }

    public Map<String, ResourcePlan> getResources() {
      return resources;
    }

    public Map<String, String> getActionResrefs() {
      return actionResrefs;
    }
  }

  private static final int[] FULL_W = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
  private static final int[] FULL_E = { 9, 10, 11, 12, 13, 14, 15 };
  private static final int[] FULL_16 = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
  private static final int[] FULL_16_W = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
  private static final int[] FULL_16_E = { 10, 11, 12, 13, 14, 15 };
  private static final int[] REDUCED_W = { 0, 2, 4, 6, 8 };
  private static final int[] REDUCED_E = { 10, 12, 14 };
  private static final int[] STATIC_W = { 0, 2, 4, 6 };
  private static final int[] STATIC_E = { 8, 10, 12, 14 };
  private static final int[] WALK_EXTRA_W = { 1, 3, 5, 7, 9 };
  private static final int[] WALK_EXTRA_E = { 11, 13, 15 };
  private static final int[] SOUTH_ONLY = { 0 };

  private final AnimationInfo.Type animationInfoType;
  private final String typeCode;
  private final String label;
  private final int defaultSlot;
  private final SplitMode splitMode;
  private final int defaultQuadrants;
  private final int defaultArmorLevels;
  private final boolean falseColorSupported;
  private final boolean translucencySupported;

  CreatureAnimationFamily(AnimationInfo.Type animationInfoType, String typeCode, String label, int defaultSlot,
      SplitMode splitMode, int defaultQuadrants, int defaultArmorLevels, boolean falseColorSupported,
      boolean translucencySupported) {
    this.animationInfoType = animationInfoType;
    this.typeCode = typeCode;
    this.label = label;
    this.defaultSlot = defaultSlot;
    this.splitMode = splitMode;
    this.defaultQuadrants = defaultQuadrants;
    this.defaultArmorLevels = defaultArmorLevels;
    this.falseColorSupported = falseColorSupported;
    this.translucencySupported = translucencySupported;
  }

  public AnimationInfo.Type getAnimationInfoType() {
    return animationInfoType;
  }

  public String getSectionName() {
    return animationInfoType.getSectionName();
  }

  public int getDefaultSlot() {
    return defaultSlot;
  }

  public SplitMode getSplitMode() {
    return splitMode;
  }

  public boolean isSplitBamsSupported() {
    return splitMode != SplitMode.NONE;
  }

  public boolean isSplitBamsRequired() {
    return splitMode == SplitMode.REQUIRED;
  }

  public boolean isSplitBamsDefault() {
    return splitMode == SplitMode.REQUIRED;
  }

  public boolean hasQuadrants() {
    return defaultQuadrants > 0;
  }

  public int getDefaultQuadrants() {
    return defaultQuadrants;
  }

  public boolean hasArmorLevels() {
    return defaultArmorLevels > 0;
  }

  public int getDefaultArmorLevels() {
    return defaultArmorLevels;
  }

  public boolean isFalseColorSupported() {
    return falseColorSupported;
  }

  public boolean isTranslucencySupported() {
    return translucencySupported;
  }

  public boolean isCanLieDownSupported() {
    return this == MONSTER_MULTI_NEW || this == TOWN_STATIC || this == CHARACTER || this == CHARACTER_OLD
        || this == MONSTER;
  }

  public boolean isInfravisionSupported() {
    switch (this) {
      case MONSTER_MULTI_NEW:
      case MONSTER_ANKHEG:
      case CHARACTER:
      case CHARACTER_OLD:
      case MONSTER:
      case MONSTER_OLD:
      case MONSTER_LAYERED:
      case MONSTER_LARGE:
      case MONSTER_LARGE_16:
      case MONSTER_ICEWIND:
        return true;
      default:
        return false;
    }
  }

  public boolean isPathSmoothSupported() {
    return this == MONSTER_QUADRANT || this == MONSTER_MULTI_NEW || this == MONSTER || this == AMBIENT;
  }

  public boolean isSupportedGame(Profile.Game game) {
    return MonsterAnimationLayout.isSupportedGame(game) && animationInfoType.isSupported(game);
  }

  public boolean isValidSlot(Profile.Game game, int animationId) {
    return MonsterAnimationLayout.isSupportedGame(game) && animationInfoType.contains(game, animationId);
  }

  public int getAnimationTypeCode(int animationId) {
    if (this == CHARACTER || this == CHARACTER_OLD) {
      return animationId & 0xf000;
    }
    return animationInfoType.getType();
  }

  /**
   * Returns the longest base resref that keeps every resource in the selected layout within the engine's
   * eight-character resref limit.
   */
  public int getMaximumResrefLength(boolean splitBams, int quadrants, int armorLevels) {
    int longestSuffix = 0;
    final FamilyLayout layout = createLayout("", splitBams, quadrants, armorLevels);
    for (final String fileName : layout.getResources().keySet()) {
      longestSuffix = Math.max(longestSuffix, fileName.length() - ".BAM".length());
    }
    return Math.max(0, 8 - longestSuffix);
  }

  public boolean requiresExactResrefLength() {
    return this == CHARACTER;
  }

  /**
   * Builds the exact resource layout for this family.
   *
   * @param resref       normalized base resref
   * @param splitBams    requested split-resource mode
   * @param quadrants    quadrant count (1-9 where applicable)
   * @param armorLevels  armor codes (1-4 where applicable)
   * @return immutable output plan
   */
  public FamilyLayout createLayout(String resref, boolean splitBams, int quadrants, int armorLevels) {
    final String normalized = (resref != null) ? resref.trim().toUpperCase(Locale.ENGLISH) : "";
    if (splitMode == SplitMode.NONE && splitBams) {
      throw new IllegalArgumentException(this + " does not define a selectable split BAM layout.");
    }
    if (splitMode == SplitMode.REQUIRED && !splitBams) {
      throw new IllegalArgumentException(this + " requires its verified split BAM layout.");
    }
    if (hasQuadrants() && (quadrants < 1 || quadrants > 9)) {
      throw new IllegalArgumentException("Quadrant count must be in the range 1-9.");
    }
    if (hasArmorLevels() && (armorLevels < 1 || armorLevels > 4)) {
      throw new IllegalArgumentException("Armor levels must be in the range 1-4.");
    }
    final LayoutBuilder builder = new LayoutBuilder();
    switch (this) {
      case EFFECT:
        buildEffect(builder, normalized);
        break;
      case MONSTER_QUADRANT:
        buildMonsterQuadrant(builder, normalized, quadrants);
        break;
      case MONSTER_MULTI:
        buildMonsterMulti(builder, normalized, quadrants);
        break;
      case MONSTER_MULTI_NEW:
        buildMonsterMultiNew(builder, normalized, splitBams, quadrants);
        break;
      case MONSTER_LAYERED_SPELL:
        buildLayeredSpell(builder, normalized);
        break;
      case MONSTER_ANKHEG:
        buildAnkheg(builder, normalized);
        break;
      case TOWN_STATIC:
        buildTownStatic(builder, normalized);
        break;
      case CHARACTER:
        buildCharacter(builder, normalized, armorLevels);
        break;
      case CHARACTER_OLD:
        buildCharacterOld(builder, normalized, armorLevels);
        break;
      case MONSTER:
        buildMonster(builder, normalized, splitBams);
        break;
      case MONSTER_OLD:
        buildMonsterOld(builder, normalized);
        break;
      case MONSTER_LAYERED:
        buildMonsterLayered(builder, normalized);
        break;
      case MONSTER_LARGE:
        buildMonsterLarge(builder, normalized);
        break;
      case MONSTER_LARGE_16:
        buildMonsterLarge16(builder, normalized);
        break;
      case AMBIENT_STATIC:
        buildAmbientStatic(builder, normalized);
        break;
      case AMBIENT:
        buildAmbient(builder, normalized);
        break;
      case FLYING:
        buildFlying(builder, normalized);
        break;
      case MONSTER_ICEWIND:
        buildIcewind(builder, normalized);
        break;
      case MONSTER_PLANESCAPE:
        buildPlanescape(builder, normalized);
        break;
      default:
        throw new IllegalStateException("Unsupported animation family: " + this);
    }
    return builder.build();
  }

  private static void buildEffect(LayoutBuilder builder, String resref) {
    builder.addBlock(resref + ".BAM", 0, Sequence.STAND, SOUTH_ONLY);
  }

  private static void buildMonsterQuadrant(LayoutBuilder builder, String resref, int quadrants) {
    for (int quadrant = 0; quadrant < quadrants; quadrant++) {
      final String index = Integer.toString(quadrant + 1);
      addFullDirectionPair(builder, resref + "G1" + index, 0, Sequence.WALK, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G2" + index, 0, Sequence.STAND, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G2" + index, 16, Sequence.STANCE, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G2" + index, 32, Sequence.GET_HIT, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G2" + index, 48, Sequence.DIE, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G2" + index, 64, Sequence.TWITCH, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G3" + index, 0, Sequence.ATTACK_1, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G3" + index, 16, Sequence.ATTACK_2, quadrant, quadrants);
      addFullDirectionPair(builder, resref + "G3" + index, 32, Sequence.ATTACK_3, quadrant, quadrants);
    }
  }

  private static void buildMonsterMulti(LayoutBuilder builder, String resref, int quadrants) {
    for (int quadrant = 0; quadrant < quadrants; quadrant++) {
      final String index = Integer.toString(quadrant + 1);
      builder.addBlock(resref + "G1" + index + ".BAM", 0, Sequence.WALK, FULL_W, false, false, quadrant, quadrants);
      builder.addBlock(resref + "G2" + index + ".BAM", 0, Sequence.STANCE, FULL_W, false, false, quadrant, quadrants);
      builder.addBlock(resref + "G2" + index + ".BAM", 9, Sequence.STAND, FULL_W, false, false, quadrant, quadrants);
      builder.addBlock(resref + "G2" + index + ".BAM", 18, Sequence.STANCE, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G3" + index + ".BAM", 0, Sequence.ATTACK_1, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G3" + index + ".BAM", 9, Sequence.ATTACK_2, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G3" + index + ".BAM", 18, Sequence.ATTACK_3, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G4" + index + ".BAM", 0, Sequence.GET_HIT, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G4" + index + ".BAM", 9, Sequence.DIE, FULL_W, false, false, quadrant, quadrants);
      builder.addBlock(resref + "G4" + index + ".BAM", 18, Sequence.TWITCH, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G4" + index + ".BAM", 27, Sequence.SLEEP, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G4" + index + ".BAM", 36, Sequence.DIE, FULL_W, false, false, quadrant, quadrants);
      builder.addBlock(resref + "G5" + index + ".BAM", 0, Sequence.CONJURE, FULL_W, false, false, quadrant,
          quadrants);
      builder.addBlock(resref + "G5" + index + ".BAM", 9, Sequence.CAST, FULL_W, false, false, quadrant, quadrants);
    }
  }

  private static void buildMonsterMultiNew(LayoutBuilder builder, String resref, boolean splitBams, int quadrants) {
    for (int quadrant = 0; quadrant < quadrants; quadrant++) {
      final String index = Integer.toString(quadrant + 1);
      if (!splitBams) {
        addMonsterGroup1(builder, resref + "G1" + index + ".BAM", quadrant, quadrants);
        addMonsterGroup2(builder, resref + "G2" + index + ".BAM", quadrant, quadrants);
      } else {
        builder.addBlock(resref + "G1" + index + ".BAM", 9, Sequence.STANCE, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + ".BAM", 18, Sequence.STAND, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + ".BAM", 27, Sequence.GET_HIT, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + ".BAM", 36, Sequence.DIE, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + ".BAM", 45, Sequence.TWITCH, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + "1.BAM", 0, Sequence.WALK, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + "2.BAM", 18, Sequence.STAND, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + "3.BAM", 27, Sequence.GET_HIT, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + "4.BAM", 27, Sequence.GET_HIT, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + "4.BAM", 36, Sequence.DIE, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G1" + index + "5.BAM", 45, Sequence.TWITCH, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + ".BAM", 0, Sequence.ATTACK_1, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "1.BAM", 9, Sequence.ATTACK_2, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "2.BAM", 18, Sequence.ATTACK_3, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "3.BAM", 27, Sequence.ATTACK_4, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "4.BAM", 36, Sequence.ATTACK_5, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "4.BAM", 45, Sequence.CONJURE, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "4.BAM", 54, Sequence.CAST, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "5.BAM", 45, Sequence.CONJURE, FULL_W, false, false, quadrant,
            quadrants);
        builder.addBlock(resref + "G2" + index + "6.BAM", 54, Sequence.CAST, FULL_W, false, false, quadrant,
            quadrants);
      }
    }
  }

  private static void addMonsterGroup1(LayoutBuilder builder, String fileName, int quadrant, int quadrants) {
    builder.addBlock(fileName, 0, Sequence.WALK, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 9, Sequence.STANCE, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 18, Sequence.STAND, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 27, Sequence.GET_HIT, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 36, Sequence.DIE, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 45, Sequence.TWITCH, FULL_W, false, false, quadrant, quadrants);
  }

  private static void addMonsterGroup2(LayoutBuilder builder, String fileName, int quadrant, int quadrants) {
    builder.addBlock(fileName, 0, Sequence.ATTACK_1, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 9, Sequence.ATTACK_2, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 18, Sequence.ATTACK_3, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 27, Sequence.ATTACK_4, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 36, Sequence.ATTACK_5, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 45, Sequence.CONJURE, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(fileName, 54, Sequence.CAST, FULL_W, false, false, quadrant, quadrants);
  }

  private static void buildLayeredSpell(LayoutBuilder builder, String resref) {
    addReducedPair(builder, resref + "G1", 0, Sequence.WALK);
    addReducedPair(builder, resref + "G1", 8, Sequence.STANCE);
    addReducedPair(builder, resref + "G1", 16, Sequence.STAND);
    addReducedPair(builder, resref + "G1", 24, Sequence.GET_HIT);
    addReducedPair(builder, resref + "G1", 32, Sequence.DIE);
    addReducedPair(builder, resref + "G1", 40, Sequence.TWITCH);
    addReducedPair(builder, resref + "G2", 0, Sequence.ATTACK_1);
    addReducedPair(builder, resref + "G2", 8, Sequence.CONJURE);
    addReducedPair(builder, resref + "G2", 16, Sequence.ATTACK_3);
  }

  private static void buildAnkheg(LayoutBuilder builder, String resref) {
    for (final String layer : new String[] { "", "D" }) {
      final boolean blank = !layer.isEmpty();
      builder.addBlock(resref + layer + "G1.BAM", 9, Sequence.DIE, FULL_W, false, blank, -1, 0);
      builder.addBlock(resref + layer + "G1.BAM", 18, Sequence.TWITCH, FULL_W, false, blank, -1, 0);
      builder.addBlock(resref + layer + "G1.BAM", 27, Sequence.STAND, FULL_W, false, blank, -1, 0);
      builder.addBlock(resref + layer + "G2.BAM", 0, Sequence.STAND, FULL_W, false, blank, -1, 0);
      builder.addBlock(resref + layer + "G2.BAM", 9, Sequence.GET_UP, FULL_W, false, blank, -1, 0);
      builder.addBlock(resref + layer + "G2.BAM", 18, Sequence.DIE, FULL_W, false, blank, -1, 0);
      builder.addBlock(resref + layer + "G3.BAM", 0, Sequence.ATTACK_1, FULL_W, false, blank, -1, 0);
      builder.addBlock(resref + layer + "G3.BAM", 9, Sequence.CAST, FULL_W, false, blank, -1, 0);
    }
  }

  private static void buildTownStatic(LayoutBuilder builder, String resref) {
    final String fileName = resref + ".BAM";
    builder.addBlock(fileName, 0, Sequence.STANCE, FULL_16);
    builder.addBlock(fileName, 16, Sequence.STAND, FULL_16);
    builder.addBlock(fileName, 32, Sequence.GET_HIT, FULL_16);
    builder.addBlock(fileName, 48, Sequence.DIE, FULL_16);
    builder.addBlock(fileName, 64, Sequence.TWITCH, FULL_16);
  }

  private static void buildCharacter(LayoutBuilder builder, String resref, int armorLevels) {
    final Sequence[] attackSources = {
        Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3, Sequence.ATTACK_4, Sequence.ATTACK_5,
        Sequence.ATTACK_3, Sequence.ATTACK_4, Sequence.ATTACK_5, Sequence.ATTACK_4
    };
    for (int armor = 1; armor <= armorLevels; armor++) {
      final String prefix = resref + armor;
      for (int attack = 0; attack < attackSources.length; attack++) {
        builder.addBlock(prefix + "A" + (attack + 1) + ".BAM", 0, attackSources[attack], FULL_W);
      }
      builder.addBlock(prefix + "SA.BAM", 0, Sequence.ATTACK_3, FULL_W);
      builder.addBlock(prefix + "SS.BAM", 0, Sequence.ATTACK_4, FULL_W);
      builder.addBlock(prefix + "SX.BAM", 0, Sequence.ATTACK_5, FULL_W);

      final String cast = prefix + "CA.BAM";
      for (int variant = 0; variant < 4; variant++) {
        builder.addBlock(cast, variant * 18, Sequence.CONJURE, FULL_W);
        builder.addBlock(cast, variant * 18 + 9, Sequence.CAST, FULL_W);
      }

      builder.addBlock(prefix + "G1.BAM", 9, Sequence.STANCE, FULL_W);
      builder.addBlock(prefix + "G11.BAM", 0, Sequence.WALK, FULL_W);
      builder.addBlock(prefix + "G12.BAM", 18, Sequence.STAND, FULL_W);
      builder.addBlock(prefix + "G13.BAM", 27, Sequence.STANCE, FULL_W);
      builder.addBlock(prefix + "G14.BAM", 36, Sequence.GET_HIT, FULL_W);
      builder.addBlock(prefix + "G15.BAM", 36, Sequence.GET_HIT, FULL_W);
      builder.addBlock(prefix + "G15.BAM", 45, Sequence.DIE, FULL_W);
      builder.addBlock(prefix + "G16.BAM", 54, Sequence.TWITCH, FULL_W);
      builder.addBlock(prefix + "G17.BAM", 63, Sequence.STAND, FULL_W);
      builder.addBlock(prefix + "G18.BAM", 72, Sequence.STAND, FULL_W);
      builder.addBlock(prefix + "G19.BAM", 81, Sequence.SLEEP, FULL_W);
      builder.addBlock(prefix + "G19.BAM", 90, Sequence.SLEEP, FULL_W);
    }
  }

  private static void buildCharacterOld(LayoutBuilder builder, String resref, int armorLevels) {
    final String[] attacks = { "A1", "A2", "A3", "A4", "A5", "A6", "SA", "SX" };
    final Sequence[] attackSources = {
        Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3, Sequence.ATTACK_4,
        Sequence.ATTACK_5, Sequence.ATTACK_3, Sequence.ATTACK_3, Sequence.ATTACK_5
    };
    for (int armor = 1; armor <= armorLevels; armor++) {
      final String prefix = resref + armor;
      for (int i = 0; i < attacks.length; i++) {
        addReducedPair(builder, prefix + attacks[i], 0, attackSources[i]);
      }
      for (int variant = 0; variant < 4; variant++) {
        addReducedPair(builder, prefix + "CA", variant * 16, Sequence.CONJURE);
        addReducedPair(builder, prefix + "CA", variant * 16 + 8, Sequence.CAST);
      }
      addReducedPair(builder, prefix + "G1", 0, Sequence.WALK);
      addReducedPair(builder, prefix + "G1", 8, Sequence.STANCE);
      addReducedPair(builder, prefix + "G1", 16, Sequence.STAND);
      addReducedPair(builder, prefix + "G1", 24, Sequence.STANCE);
      addReducedPair(builder, prefix + "G1", 32, Sequence.STAND);
      addReducedPair(builder, prefix + "G1", 40, Sequence.GET_HIT);
      addReducedPair(builder, prefix + "G1", 48, Sequence.DIE);
      addReducedPair(builder, prefix + "G1", 56, Sequence.TWITCH);
      builder.addBlock(prefix + "W2.BAM", 0, Sequence.WALK, WALK_EXTRA_W);
      builder.addBlock(prefix + "W2E.BAM", 5, Sequence.WALK, WALK_EXTRA_E);
    }
  }

  private static void buildMonster(LayoutBuilder builder, String resref, boolean splitBams) {
    for (final Map.Entry<String, List<OutputSlot>> entry
        : MonsterAnimationLayout.getOutputLayout(splitBams).entrySet()) {
      final String fileName = resref + entry.getKey() + ".BAM";
      for (final OutputSlot slot : entry.getValue()) {
        builder.addBlock(fileName, slot.getCycleOffset(), slot.getSequence(), FULL_W);
      }
    }
  }

  private static void buildMonsterOld(LayoutBuilder builder, String resref) {
    addReducedPair(builder, resref + "G1", 0, Sequence.WALK);
    addReducedPair(builder, resref + "G1", 8, Sequence.STANCE);
    addReducedPair(builder, resref + "G1", 16, Sequence.STAND);
    addReducedPair(builder, resref + "G1", 24, Sequence.GET_HIT);
    addReducedPair(builder, resref + "G1", 32, Sequence.DIE);
    addReducedPair(builder, resref + "G1", 40, Sequence.TWITCH);
    addReducedPair(builder, resref + "G2", 0, Sequence.ATTACK_1);
    addReducedPair(builder, resref + "G2", 8, Sequence.ATTACK_2);
    addReducedPair(builder, resref + "G2", 16, Sequence.ATTACK_3);
  }

  private static void buildMonsterLayered(LayoutBuilder builder, String resref) {
    addReducedPair(builder, resref + "G1", 0, Sequence.WALK);
    addReducedPair(builder, resref + "G1", 8, Sequence.STANCE);
    addReducedPair(builder, resref + "G1", 16, Sequence.STAND);
    addReducedPair(builder, resref + "G1", 24, Sequence.GET_HIT);
    addReducedPair(builder, resref + "G1", 32, Sequence.DIE);
    addReducedPair(builder, resref + "G1", 40, Sequence.TWITCH);
    addReducedPair(builder, resref + "G2", 0, Sequence.ATTACK_1);
    addReducedPair(builder, resref + "G2", 8, Sequence.ATTACK_2);
    addReducedPair(builder, resref + "G2", 16, Sequence.ATTACK_3);
  }

  private static void buildMonsterLarge(LayoutBuilder builder, String resref) {
    addReducedPair(builder, resref + "G1", 0, Sequence.STAND);
    addReducedPair(builder, resref + "G1", 8, Sequence.STANCE);
    addReducedPair(builder, resref + "G1", 16, Sequence.WALK);
    addReducedPair(builder, resref + "G2", 0, Sequence.ATTACK_1);
    addReducedPair(builder, resref + "G2", 8, Sequence.ATTACK_2);
    addReducedPair(builder, resref + "G3", 0, Sequence.ATTACK_3);
    addReducedPair(builder, resref + "G3", 8, Sequence.GET_HIT);
    addReducedPair(builder, resref + "G3", 16, Sequence.DIE);
    addReducedPair(builder, resref + "G3", 24, Sequence.TWITCH);
  }

  private static void buildMonsterLarge16(LayoutBuilder builder, String resref) {
    addSixteenDirectionPair(builder, resref + "G1", 0, Sequence.WALK);
    addSixteenDirectionPair(builder, resref + "G2", 0, Sequence.STAND);
    addSixteenDirectionPair(builder, resref + "G2", 16, Sequence.STANCE);
    addSixteenDirectionPair(builder, resref + "G2", 32, Sequence.GET_HIT);
    addSixteenDirectionPair(builder, resref + "G2", 48, Sequence.DIE);
    addSixteenDirectionPair(builder, resref + "G2", 64, Sequence.TWITCH);
    addSixteenDirectionPair(builder, resref + "G3", 0, Sequence.ATTACK_1);
    addSixteenDirectionPair(builder, resref + "G3", 16, Sequence.ATTACK_2);
    addSixteenDirectionPair(builder, resref + "G3", 32, Sequence.ATTACK_3);
  }

  private static void buildAmbientStatic(LayoutBuilder builder, String resref) {
    addStaticPair(builder, resref + "G1", 0, Sequence.STANCE);
    addStaticPair(builder, resref + "G1", 8, Sequence.STAND);
    addStaticPair(builder, resref + "G1", 16, Sequence.GET_HIT);
    addStaticPair(builder, resref + "G1", 24, Sequence.DIE);
    addStaticPair(builder, resref + "G1", 32, Sequence.TWITCH);
  }

  private static void buildAmbient(LayoutBuilder builder, String resref) {
    addReducedPair(builder, resref + "G1", 0, Sequence.WALK);
    addReducedPair(builder, resref + "G1", 8, Sequence.STANCE);
    addReducedPair(builder, resref + "G1", 16, Sequence.STAND);
    addReducedPair(builder, resref + "G1", 24, Sequence.GET_HIT);
    addReducedPair(builder, resref + "G1", 32, Sequence.DIE);
    addReducedPair(builder, resref + "G1", 40, Sequence.TWITCH);
  }

  private static void buildFlying(LayoutBuilder builder, String resref) {
    final String fileName = resref + ".BAM";
    builder.addBlock(fileName, 0, Sequence.STAND, FULL_W);
    builder.addBlock(fileName, 9, Sequence.WALK, FULL_W);
  }

  private static void buildIcewind(LayoutBuilder builder, String resref) {
    final String[] codes = { "A1", "A2", "A3", "A4", "CA", "DE", "GH", "GU", "SC", "SD", "SL", "SP", "TW",
        "WK" };
    final Sequence[] sequences = {
        Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3, Sequence.ATTACK_4, Sequence.CAST, Sequence.DIE,
        Sequence.GET_HIT, Sequence.GET_UP, Sequence.STANCE, Sequence.STAND, Sequence.SLEEP, Sequence.CONJURE,
        Sequence.TWITCH, Sequence.WALK
    };
    for (int i = 0; i < codes.length; i++) {
      builder.addBlock(resref + codes[i] + ".BAM", 0, sequences[i], REDUCED_W);
      builder.addBlock(resref + codes[i] + "E.BAM", 5, sequences[i], REDUCED_E);
    }
  }

  private static void buildPlanescape(LayoutBuilder builder, String resref) {
    for (final org.infinity.resource.cre.decoder.util.Sequence sequence
        : org.infinity.resource.cre.decoder.util.Sequence.values()) {
      final String action = MonsterPlanescapeDecoder.getActionCommand(sequence);
      final String prefix = MonsterPlanescapeDecoder.getActionPrefix(sequence);
      if (action == null || prefix == null) {
        continue;
      }
      final String resourceResref = ("C" + prefix + resref).toUpperCase(Locale.ENGLISH);
      if (!builder.hasResource(resourceResref + ".BAM")) {
        builder.addBlock(resourceResref + ".BAM", 0, getPlanescapeSource(sequence), FULL_W);
      }
      builder.addAction(action, resourceResref);
    }
  }

  private static Sequence getPlanescapeSource(org.infinity.resource.cre.decoder.util.Sequence sequence) {
    switch (sequence) {
      case PST_ATTACK1:
        return Sequence.ATTACK_1;
      case PST_ATTACK2:
        return Sequence.ATTACK_2;
      case PST_ATTACK3:
        return Sequence.ATTACK_3;
      case PST_GET_HIT:
        return Sequence.GET_HIT;
      case PST_RUN:
      case PST_WALK:
        return Sequence.WALK;
      case PST_SPELL1:
        return Sequence.CAST;
      case PST_SPELL2:
        return Sequence.CONJURE;
      case PST_SPELL3:
        return Sequence.CAST;
      case PST_GET_UP:
        return Sequence.GET_UP;
      case PST_DIE_FORWARD:
      case PST_DIE_BACKWARD:
      case PST_DIE_COLLAPSE:
        return Sequence.DIE;
      case PST_STANCE:
      case PST_STAND_TO_STANCE:
      case PST_STANCE_FIDGET1:
      case PST_STANCE_FIDGET2:
        return Sequence.STANCE;
      case PST_STAND:
      case PST_STANCE_TO_STAND:
      case PST_STAND_FIDGET1:
      case PST_STAND_FIDGET2:
      case PST_TALK1:
      case PST_TALK2:
      case PST_TALK3:
        return Sequence.STAND;
      default:
        final String name = sequence.name();
        if (name.startsWith("PST_MISC")) {
          final int value = Integer.parseInt(name.substring("PST_MISC".length()));
          final Sequence[] fallbacks = {
              Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3, Sequence.ATTACK_4, Sequence.ATTACK_5,
              Sequence.CONJURE, Sequence.CAST, Sequence.WALK, Sequence.GET_HIT, Sequence.DIE, Sequence.TWITCH,
              Sequence.SLEEP, Sequence.GET_UP, Sequence.STAND, Sequence.STANCE
          };
          return fallbacks[(value - 1) % fallbacks.length];
        }
        return Sequence.STAND;
    }
  }

  private static void addFullDirectionPair(LayoutBuilder builder, String baseName, int cycleOffset, Sequence sequence,
      int quadrant, int quadrants) {
    builder.addBlock(baseName + ".BAM", cycleOffset, sequence, FULL_W, false, false, quadrant, quadrants);
    builder.addBlock(baseName + "E.BAM", cycleOffset + FULL_W.length, sequence, FULL_E, false, false, quadrant,
        quadrants);
  }

  private static void addReducedPair(LayoutBuilder builder, String baseName, int cycleOffset, Sequence sequence) {
    builder.addBlock(baseName + ".BAM", cycleOffset, sequence, REDUCED_W);
    builder.addBlock(baseName + "E.BAM", cycleOffset + REDUCED_W.length, sequence, REDUCED_E);
  }

  private static void addStaticPair(LayoutBuilder builder, String baseName, int cycleOffset, Sequence sequence) {
    builder.addBlock(baseName + ".BAM", cycleOffset, sequence, STATIC_W);
    builder.addBlock(baseName + "E.BAM", cycleOffset + STATIC_W.length, sequence, STATIC_E);
  }

  private static void addSixteenDirectionPair(LayoutBuilder builder, String baseName, int cycleOffset,
      Sequence sequence) {
    builder.addBlock(baseName + ".BAM", cycleOffset, sequence, FULL_16_W);
    builder.addBlock(baseName + "E.BAM", cycleOffset + FULL_16_W.length, sequence, FULL_16_E);
  }

  private static final class MutableResourcePlan {
    private final TreeMap<Integer, CyclePlan> cycles = new TreeMap<>();
    private boolean preserveExistingCycles;
  }

  static final class LayoutBuilder {
    private final LinkedHashMap<String, MutableResourcePlan> resources = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> actions = new LinkedHashMap<>();

    boolean hasResource(String fileName) {
      return resources.containsKey(fileName);
    }

    void addAction(String action, String resref) {
      actions.put(action, resref);
    }

    void addBlock(String fileName, int cycleOffset, Sequence sequence, int[] directions) {
      addBlock(fileName, cycleOffset, sequence, directions, false, false, -1, 0);
    }

    void addBlock(String fileName, int cycleOffset, Sequence sequence, int[] directions, boolean reversed,
        boolean blank, int quadrantIndex, int quadrantCount) {
      for (int i = 0; i < directions.length; i++) {
        addCycle(fileName, new CyclePlan(cycleOffset + i, sequence, directions[i], reversed, blank, quadrantIndex,
            quadrantCount));
      }
    }

    void addCycle(String fileName, CyclePlan cycle) {
      final MutableResourcePlan resource = resources.computeIfAbsent(fileName, key -> new MutableResourcePlan());
      final CyclePlan previous = resource.cycles.putIfAbsent(cycle.getCycleIndex(), cycle);
      if (previous != null && !previous.sameMapping(cycle)) {
        throw new IllegalStateException("Conflicting cycle " + cycle.getCycleIndex() + " in " + fileName + ".");
      }
    }

    /**
     * Adds the first canonical decoder mapping for a physical cycle.
     *
     * <p>Classic decoders may expose the same stored cycle through later sequence aliases (for example, death as
     * sleep or a forward sleep cycle as reverse get-up). Callers iterate the decoder's canonical sequence and
     * direction order, so aliases must not overwrite the physical cycle's first canonical mapping.</p>
     */
    void addCycleIfAbsent(String fileName, CyclePlan cycle) {
      resources.computeIfAbsent(fileName, key -> new MutableResourcePlan()).cycles
          .putIfAbsent(cycle.getCycleIndex(), cycle);
    }

    void preserveExistingCycles(String fileName) {
      resources.computeIfAbsent(fileName, key -> new MutableResourcePlan()).preserveExistingCycles = true;
    }

    FamilyLayout build() {
      return new FamilyLayout(resources, actions);
    }
  }

  @Override
  public String toString() {
    return typeCode + " — " + label;
  }
}
