// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.infinity.gui.converter.creature.CreatureAnimationFamily.CyclePlan;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.LayoutBuilder;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.decoder.AmbientDecoder;
import org.infinity.resource.cre.decoder.CharacterBaseDecoder;
import org.infinity.resource.cre.decoder.CharacterDecoder;
import org.infinity.resource.cre.decoder.CharacterOldDecoder;
import org.infinity.resource.cre.decoder.EffectDecoder;
import org.infinity.resource.cre.decoder.MonsterDecoder;
import org.infinity.resource.cre.decoder.MonsterMultiDecoder;
import org.infinity.resource.cre.decoder.MonsterMultiNewDecoder;
import org.infinity.resource.cre.decoder.MonsterQuadrantDecoder;
import org.infinity.resource.cre.decoder.QuadrantsBaseDecoder;
import org.infinity.resource.cre.decoder.SpriteDecoder;
import org.infinity.resource.cre.decoder.TownStaticDecoder;
import org.infinity.resource.cre.decoder.tables.SpriteTables;
import org.infinity.resource.cre.decoder.util.DirDef;
import org.infinity.resource.cre.decoder.util.SegmentDef;
import org.infinity.resource.cre.decoder.util.SeqDef;
import org.infinity.resource.cre.decoder.util.SpriteUtils;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapSection;
import org.infinity.util.tuples.Couple;

/**
 * Exact, immutable binding between a classic-engine animation slot and its installed hardcoded or Infinity Animations
 * definition.
 *
 * <p>Classic engines cannot define new creature animation slots through Enhanced Edition INI files. This class
 * therefore accepts only definitions returned by {@link SpriteTables}, constructs the matching Near Infinity decoder,
 * and derives the replacement resource graph from the decoder's resolved avatar segments and installed BAMs.</p>
 */
public final class ClassicAnimationDefinition {
  private static final int[] FULL_W = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
  private static final int[] REDUCED_W = { 0, 2, 4, 6, 8 };
  private static final int[] REDUCED_E = { 10, 12, 14 };
  private static final int[] WALK_EXTRA_W = { 1, 3, 5, 7, 9 };
  private static final int[] WALK_EXTRA_E = { 11, 13, 15 };

  private final Profile.Game game;
  private final int animationId;
  private final CreatureAnimationFamily family;
  private final String resref;
  private final FamilyLayout layout;
  private final boolean splitBams;
  private final int quadrants;
  private final int armorLevels;
  private final boolean canLieDown;
  private final boolean detectedByInfravision;
  private final boolean falseColor;
  private final boolean pathSmooth;
  private final boolean translucent;
  private final double moveScale;
  private final int ellipse;
  private final int personalSpace;

  private ClassicAnimationDefinition(Profile.Game game, int animationId, CreatureAnimationFamily family,
      String resref, FamilyLayout layout, boolean splitBams, int quadrants, int armorLevels, boolean canLieDown,
      boolean detectedByInfravision, boolean falseColor, boolean pathSmooth, boolean translucent, double moveScale,
      int ellipse, int personalSpace) {
    this.game = game;
    this.animationId = animationId;
    this.family = family;
    this.resref = resref;
    this.layout = layout;
    this.splitBams = splitBams;
    this.quadrants = quadrants;
    this.armorLevels = armorLevels;
    this.canLieDown = canLieDown;
    this.detectedByInfravision = detectedByInfravision;
    this.falseColor = falseColor;
    this.pathSmooth = pathSmooth;
    this.translucent = translucent;
    this.moveScale = moveScale;
    this.ellipse = ellipse;
    this.personalSpace = personalSpace;
  }

  /**
   * Resolves one exact classic profile definition from the active installation.
   *
   * @throws Exception if the slot has no complete exact definition, is ambiguous, or references an invalid BAM graph
   */
  public static ClassicAnimationDefinition resolve(Profile.Game game, int animationId) throws Exception {
    if (!MonsterAnimationLayout.isSupportedGame(game) || Profile.isEnhancedEdition(game)) {
      throw new IllegalArgumentException("An exact classic-engine game profile is required.");
    }
    if (game != Profile.getGame()) {
      throw new IllegalArgumentException("Classic animation definitions can be resolved only for the active game.");
    }
    if (animationId < 0 || animationId > 0xffff) {
      throw new IllegalArgumentException("Animation slot is outside the 16-bit engine range.");
    }

    final List<ClassicAnimationDefinition> candidates = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    for (final IniMap ini : SpriteTables.createIniMaps(game, animationId)) {
      final CreatureAnimationFamily family = findFamily(ini);
      if (family == null || !family.isValidSlot(game, animationId)) {
        continue;
      }
      SpriteDecoder decoder = null;
      try {
        decoder = createDecoder(family, animationId, ini);
        candidates.add(fromDecoder(game, animationId, family, decoder));
      } catch (Exception e) {
        failures.add(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
      } finally {
        if (decoder != null) {
          decoder.close();
        }
      }
    }

    if (candidates.size() > 1) {
      throw new IOException(String.format(Locale.ENGLISH,
          "Animation slot 0x%04X resolves to more than one complete classic profile definition.", animationId));
    }
    if (candidates.isEmpty()) {
      final String detail = failures.isEmpty() ? "" : " " + String.join("; ", failures);
      throw new IOException(String.format(Locale.ENGLISH,
          "Animation slot 0x%04X has no complete hardcoded or Infinity Animations definition for %s.%s",
          animationId, game.getTitle(), detail));
    }
    return candidates.get(0);
  }

  /** Resolves an exact classic decoder for resource-driven equipment workflows without invoking heuristic guessing. */
  static SpriteDecoder resolveDecoder(Profile.Game game, int animationId) throws Exception {
    if (!MonsterAnimationLayout.isSupportedGame(game) || Profile.isEnhancedEdition(game) || game != Profile.getGame()) {
      throw new IllegalArgumentException("An exact active classic-engine game profile is required.");
    }
    final List<SpriteDecoder> candidates = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    for (final IniMap ini : SpriteTables.createIniMaps(game, animationId)) {
      final CreatureAnimationFamily family = findFamily(ini);
      if (family == null || !family.isValidSlot(game, animationId)) {
        continue;
      }
      try {
        candidates.add(createDecoder(family, animationId, ini));
      } catch (Exception e) {
        failures.add(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
      }
    }
    if (candidates.size() > 1) {
      for (final SpriteDecoder decoder : candidates) {
        decoder.close();
      }
      throw new IOException(String.format(Locale.ENGLISH,
          "Animation slot 0x%04X resolves to more than one complete classic profile definition.", animationId));
    }
    if (candidates.isEmpty()) {
      final String detail = failures.isEmpty() ? "" : " " + String.join("; ", failures);
      throw new IOException(String.format(Locale.ENGLISH,
          "Animation slot 0x%04X has no complete hardcoded or Infinity Animations definition for %s.%s",
          animationId, game.getTitle(), detail));
    }
    return candidates.get(0);
  }

  /** Returns whether the table data for a slot identifies the requested family, without guessing a decoder. */
  static boolean hasTableDefinition(Profile.Game game, int animationId, CreatureAnimationFamily expectedFamily) {
    if (game == null || expectedFamily == null) {
      return false;
    }
    for (final IniMap ini : SpriteTables.createIniMaps(game, animationId)) {
      if (findFamily(ini) == expectedFamily) {
        return true;
      }
    }
    return false;
  }

  static ClassicAnimationDefinition forTesting(Profile.Game game, int animationId,
      CreatureAnimationFamily family, String resref, FamilyLayout layout, boolean falseColor) {
    return new ClassicAnimationDefinition(game, animationId, family, resref, layout, false, 1, 1, false, true,
        falseColor, false, false, 0.0, 16, 3);
  }

  private static CreatureAnimationFamily findFamily(IniMap ini) {
    if (ini == null) {
      return null;
    }
    CreatureAnimationFamily result = null;
    for (final CreatureAnimationFamily family : CreatureAnimationFamily.values()) {
      final IniMapSection section = ini.getSection(family.getSectionName());
      if (section != null && section.getEntryCount() > 0) {
        if (result != null) {
          return null;
        }
        result = family;
      }
    }
    return result;
  }

  private static SpriteDecoder createDecoder(CreatureAnimationFamily family, int animationId, IniMap ini)
      throws Exception {
    final Class<? extends SpriteDecoder> decoderClass =
        SpriteUtils.getSpriteDecoderClass(family.getAnimationInfoType());
    if (decoderClass == null) {
      throw new IllegalArgumentException("No Near Infinity decoder exists for " + family + ".");
    }
    try {
      final Constructor<? extends SpriteDecoder> constructor =
          decoderClass.getConstructor(int.class, IniMap.class);
      return constructor.newInstance(animationId, ini);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Exception) {
        throw (Exception) e.getCause();
      }
      throw e;
    }
  }

  private static ClassicAnimationDefinition fromDecoder(Profile.Game game, int animationId,
      CreatureAnimationFamily family, SpriteDecoder decoder) throws Exception {
    final FamilyLayout layout = createLayout(family, decoder);
    if (layout.getResources().isEmpty()) {
      throw new IOException("The exact profile definition exposes no replaceable installed avatar BAMs.");
    }
    final boolean split = getSplitBams(decoder);
    final int quadrantCount = decoder instanceof QuadrantsBaseDecoder
        ? ((QuadrantsBaseDecoder) decoder).getQuadrants() : 1;
    final int armorCount = decoder instanceof CharacterBaseDecoder
        ? ((CharacterBaseDecoder) decoder).getMaxArmorCode() : 1;
    return new ClassicAnimationDefinition(game, animationId, family,
        decoder.getAnimationResref().trim().toUpperCase(Locale.ENGLISH), layout, split, quadrantCount, armorCount,
        getCanLieDown(decoder), decoder.isDetectedByInfravision(), decoder.isFalseColor(), getPathSmooth(decoder),
        decoder.isTranslucent(), decoder.getMoveScale(), decoder.getEllipse(), decoder.getPersonalSpace());
  }

  private static FamilyLayout createLayout(CreatureAnimationFamily family, SpriteDecoder decoder) throws Exception {
    if (decoder instanceof EffectDecoder) {
      return createEffectLayout((EffectDecoder) decoder);
    }
    if (decoder instanceof CharacterDecoder) {
      return createCharacterLayout((CharacterDecoder) decoder);
    }
    if (decoder instanceof CharacterOldDecoder) {
      return createCharacterOldLayout((CharacterOldDecoder) decoder);
    }
    return createResolvedLayout(family, decoder);
  }

  private static FamilyLayout createEffectLayout(EffectDecoder decoder) throws Exception {
    final LayoutBuilder builder = new LayoutBuilder();
    final List<String> resrefs = new ArrayList<>();
    resrefs.add(decoder.getAnimationResref());
    if (decoder.isRenderRandom() && !decoder.getSecondaryResref().isEmpty()) {
      resrefs.add(decoder.getSecondaryResref());
    }
    for (final String resref : resrefs) {
      final String fileName = normalizeFileName(resref + ".BAM");
      final ResourceEntry entry = ResourceFactory.getResourceEntry(fileName);
      if (entry == null) {
        throw new IOException("Classic effect resource " + fileName + " is unavailable.");
      }
      final BamDecoder bam = BamDecoder.loadBam(entry);
      if (bam == null || !bam.isOpen()) {
        throw new IOException("Could not open classic effect resource " + fileName + ".");
      }
      try {
        final int cycleCount = bam.createControl().cycleCount();
        final int first = decoder.isRenderRandom() ? 0 : Math.max(0, decoder.getCycle());
        final int end = decoder.isRenderRandom() ? cycleCount : first + 1;
        if (first >= cycleCount) {
          throw new IOException(fileName + " does not contain profile cycle " + first + ".");
        }
        for (int cycle = first; cycle < end; cycle++) {
          builder.addCycleIfAbsent(fileName,
              new CyclePlan(cycle, Sequence.STAND, 0, false, false, -1, 0));
        }
        builder.preserveExistingCycles(fileName);
      } finally {
        bam.close();
      }
    }
    return builder.build();
  }

  private static FamilyLayout createCharacterLayout(CharacterDecoder decoder) {
    final LayoutBuilder builder = new LayoutBuilder();
    for (int armor = 1; armor <= decoder.getMaxArmorCode(); armor++) {
      for (final org.infinity.resource.cre.decoder.util.Sequence engineSequence
          : org.infinity.resource.cre.decoder.util.Sequence.values()) {
        final Couple<String, Integer> data = decoder.getAvatarSequenceMap().get(engineSequence);
        final Sequence source = mapSequence(engineSequence);
        if (data == null || source == null) {
          continue;
        }
        final String rawSuffix = data.getValue0();
        final String suffix = SegmentDef.fixBehaviorSuffix(rawSuffix);
        String prefix = armor > 1 ? decoder.getArmorSpecificResref() : decoder.getArmorBaseResref();
        if (!ResourceFactory.resourceExists(prefix + armor + suffix + ".BAM")) {
          prefix = decoder.getArmorBaseResref();
          if (!ResourceFactory.resourceExists(prefix + armor + suffix + ".BAM")) {
            prefix = decoder.getAnimationResref();
          }
        }
        final String fileName = normalizeFileName(prefix + armor + suffix + ".BAM");
        final ResourceEntry entry = ResourceFactory.getResourceEntry(fileName);
        if (!SpriteUtils.bamCyclesExist(entry, data.getValue1(), FULL_W.length)) {
          continue;
        }
        addBlockIfAbsent(builder, fileName, data.getValue1(), source, FULL_W, isReversed(rawSuffix), false, -1, 0);
        builder.preserveExistingCycles(fileName);
      }
    }
    return builder.build();
  }

  private static FamilyLayout createCharacterOldLayout(CharacterOldDecoder decoder) {
    final LayoutBuilder builder = new LayoutBuilder();
    for (int armor = 1; armor <= decoder.getMaxArmorCode(); armor++) {
      final String prefix = decoder.getAnimationResref() + armor;
      for (final org.infinity.resource.cre.decoder.util.Sequence engineSequence
          : org.infinity.resource.cre.decoder.util.Sequence.values()) {
        final Couple<String, Integer> data = decoder.getAvatarSequenceMap().get(engineSequence);
        final Sequence source = mapSequence(engineSequence);
        if (data == null || source == null) {
          continue;
        }
        final String rawSuffix = data.getValue0();
        final String suffix = SegmentDef.fixBehaviorSuffix(rawSuffix);
        final String west = normalizeFileName(prefix + suffix + ".BAM");
        final String east = normalizeFileName(prefix + suffix + "E.BAM");
        final ResourceEntry westEntry = ResourceFactory.getResourceEntry(west);
        final ResourceEntry eastEntry = ResourceFactory.getResourceEntry(east);
        if (!SpriteUtils.bamCyclesExist(westEntry, data.getValue1(), REDUCED_W.length)
            || !SpriteUtils.bamCyclesExist(eastEntry, data.getValue1() + REDUCED_W.length, REDUCED_E.length)) {
          continue;
        }
        addBlockIfAbsent(builder, west, data.getValue1(), source, REDUCED_W, isReversed(rawSuffix), false, -1, 0);
        addBlockIfAbsent(builder, east, data.getValue1() + REDUCED_W.length, source, REDUCED_E,
            isReversed(rawSuffix), false, -1, 0);
        builder.preserveExistingCycles(west);
        builder.preserveExistingCycles(east);
      }

      final String walkWest = normalizeFileName(prefix + "W2.BAM");
      final String walkEast = normalizeFileName(prefix + "W2E.BAM");
      if (SpriteUtils.bamCyclesExist(ResourceFactory.getResourceEntry(walkWest), 0, WALK_EXTRA_W.length)
          && SpriteUtils.bamCyclesExist(ResourceFactory.getResourceEntry(walkEast), WALK_EXTRA_W.length,
              WALK_EXTRA_E.length)) {
        addBlockIfAbsent(builder, walkWest, 0, Sequence.WALK, WALK_EXTRA_W, false, false, -1, 0);
        addBlockIfAbsent(builder, walkEast, WALK_EXTRA_W.length, Sequence.WALK, WALK_EXTRA_E, false, false, -1, 0);
        builder.preserveExistingCycles(walkWest);
        builder.preserveExistingCycles(walkEast);
      }
    }
    return builder.build();
  }

  private static FamilyLayout createResolvedLayout(CreatureAnimationFamily family, SpriteDecoder decoder) {
    final LayoutBuilder builder = new LayoutBuilder();
    final Set<String> allowedFiles = new HashSet<>();
    for (final String file : decoder.getAnimationFiles(false)) {
      allowedFiles.add(normalizeFileName(file));
    }
    final int quadrantCount = decoder instanceof QuadrantsBaseDecoder
        ? ((QuadrantsBaseDecoder) decoder).getQuadrants() : 0;
    final String normalizedResref = decoder.getAnimationResref().trim().toUpperCase(Locale.ENGLISH);

    for (final org.infinity.resource.cre.decoder.util.Sequence engineSequence
        : org.infinity.resource.cre.decoder.util.Sequence.values()) {
      final Sequence source = mapSequence(engineSequence);
      if (source == null) {
        continue;
      }
      final SeqDef definition = decoder.getSequenceDefinitionSnapshot(engineSequence);
      if (definition == null) {
        continue;
      }
      for (final DirDef direction : definition.getDirections()) {
        if (direction.isMirrored()) {
          continue;
        }
        int avatarIndex = 0;
        for (final SegmentDef segment : direction.getCycle().getCycles()) {
          if (segment.getSpriteType() != SegmentDef.SpriteType.AVATAR) {
            continue;
          }
          final String fileName = normalizeFileName(segment.getEntry().getResourceName());
          if (!allowedFiles.contains(fileName)) {
            continue;
          }
          final int quadrant = quadrantCount > 0 ? avatarIndex : -1;
          final boolean blank = family == CreatureAnimationFamily.MONSTER_ANKHEG
              && fileName.startsWith(normalizedResref + "D");
          builder.addCycleIfAbsent(fileName,
              new CyclePlan(segment.getCycleIndex(), source, direction.getDirection().getValue(),
                  isReversed(segment.getBehavior()), blank, quadrant, quadrantCount));
          builder.preserveExistingCycles(fileName);
          avatarIndex++;
        }
      }
    }
    return builder.build();
  }

  private static void addBlockIfAbsent(LayoutBuilder builder, String fileName, int cycleOffset, Sequence sequence,
      int[] directions, boolean reversed, boolean blank, int quadrant, int quadrants) {
    for (int i = 0; i < directions.length; i++) {
      builder.addCycleIfAbsent(fileName, new CyclePlan(cycleOffset + i, sequence, directions[i], reversed, blank,
          quadrant, quadrants));
    }
  }

  private static Sequence mapSequence(org.infinity.resource.cre.decoder.util.Sequence sequence) {
    switch (sequence) {
      case STAND:
      case STAND2:
      case STAND3:
      case STAND_EMERGED:
      case STAND_HIDDEN:
        return Sequence.STAND;
      case STANCE:
      case STANCE2:
        return Sequence.STANCE;
      case GET_HIT:
        return Sequence.GET_HIT;
      case DIE:
        return Sequence.DIE;
      case TWITCH:
        return Sequence.TWITCH;
      case SPELL:
      case SPELL1:
      case SPELL2:
      case SPELL3:
      case SPELL4:
        return Sequence.CONJURE;
      case CAST:
      case CAST1:
      case CAST2:
      case CAST3:
      case CAST4:
        return Sequence.CAST;
      case SLEEP:
      case SLEEP2:
        return Sequence.SLEEP;
      case GET_UP:
      case GET_UP2:
      case EMERGE:
        return Sequence.GET_UP;
      case HIDE:
        return Sequence.DIE;
      case WALK:
        return Sequence.WALK;
      case ATTACK:
      case ATTACK_SLASH_1H:
        return Sequence.ATTACK_1;
      case ATTACK_2:
      case ATTACK_SLASH_2H:
        return Sequence.ATTACK_2;
      case ATTACK_3:
      case ATTACK_BACKSLASH_1H:
      case ATTACK_2H:
      case ATTACK_BOW:
        return Sequence.ATTACK_3;
      case ATTACK_4:
      case ATTACK_BACKSLASH_2H:
      case ATTACK_2WEAPONS1:
      case ATTACK_OVERHEAD:
      case ATTACK_SLING:
      case SHOOT:
        return Sequence.ATTACK_4;
      case ATTACK_5:
      case ATTACK_JAB_1H:
      case ATTACK_JAB_2H:
      case ATTACK_2WEAPONS2:
      case ATTACK_CROSSBOW:
        return Sequence.ATTACK_5;
      default:
        if (sequence.name().startsWith("PST_")) {
          return mapPlanescapeSequence(sequence);
        }
        return null;
    }
  }

  private static Sequence mapPlanescapeSequence(org.infinity.resource.cre.decoder.util.Sequence sequence) {
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
      case PST_SPELL3:
        return Sequence.CAST;
      case PST_SPELL2:
        return Sequence.CONJURE;
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
          final Sequence[] fallbacks = {
              Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3, Sequence.ATTACK_4, Sequence.ATTACK_5,
              Sequence.CONJURE, Sequence.CAST, Sequence.WALK, Sequence.GET_HIT, Sequence.DIE, Sequence.TWITCH,
              Sequence.SLEEP, Sequence.GET_UP, Sequence.STAND, Sequence.STANCE
          };
          final int value = Integer.parseInt(name.substring("PST_MISC".length()));
          return fallbacks[(value - 1) % fallbacks.length];
        }
        return null;
    }
  }

  private static boolean isReversed(String suffix) {
    return suffix != null && suffix.indexOf('!') >= 0;
  }

  private static boolean isReversed(SegmentDef.Behavior behavior) {
    return behavior == SegmentDef.Behavior.REVERSE_REPEAT
        || behavior == SegmentDef.Behavior.REVERSE_SINGLE
        || behavior == SegmentDef.Behavior.REVERSE_FREEZE
        || behavior == SegmentDef.Behavior.REVERSE_CUT;
  }

  private static String normalizeFileName(String value) {
    return value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
  }

  private static boolean getSplitBams(SpriteDecoder decoder) {
    if (decoder instanceof CharacterDecoder) {
      return ((CharacterDecoder) decoder).isSplittedBams();
    } else if (decoder instanceof MonsterDecoder) {
      return ((MonsterDecoder) decoder).isSplittedBams();
    } else if (decoder instanceof MonsterMultiDecoder) {
      return ((MonsterMultiDecoder) decoder).isSplittedBams();
    } else if (decoder instanceof MonsterMultiNewDecoder) {
      return ((MonsterMultiNewDecoder) decoder).isSplittedBams();
    }
    return false;
  }

  private static boolean getCanLieDown(SpriteDecoder decoder) {
    if (decoder instanceof CharacterBaseDecoder) {
      return ((CharacterBaseDecoder) decoder).canLieDown();
    } else if (decoder instanceof MonsterDecoder) {
      return ((MonsterDecoder) decoder).canLieDown();
    } else if (decoder instanceof MonsterMultiNewDecoder) {
      return ((MonsterMultiNewDecoder) decoder).canLieDown();
    } else if (decoder instanceof TownStaticDecoder) {
      return ((TownStaticDecoder) decoder).canLieDown();
    }
    return false;
  }

  private static boolean getPathSmooth(SpriteDecoder decoder) {
    if (decoder instanceof AmbientDecoder) {
      return ((AmbientDecoder) decoder).isSmoothPath();
    } else if (decoder instanceof MonsterDecoder) {
      return ((MonsterDecoder) decoder).isSmoothPath();
    } else if (decoder instanceof MonsterMultiNewDecoder) {
      return ((MonsterMultiNewDecoder) decoder).isSmoothPath();
    } else if (decoder instanceof MonsterQuadrantDecoder) {
      return ((MonsterQuadrantDecoder) decoder).isSmoothPath();
    }
    return false;
  }

  public Profile.Game getGame() {
    return game;
  }

  public int getAnimationId() {
    return animationId;
  }

  public CreatureAnimationFamily getFamily() {
    return family;
  }

  public String getResref() {
    return resref;
  }

  public FamilyLayout getLayout() {
    return layout;
  }

  public boolean isSplitBams() {
    return splitBams;
  }

  public int getQuadrants() {
    return quadrants;
  }

  public int getArmorLevels() {
    return armorLevels;
  }

  public boolean isCanLieDown() {
    return canLieDown;
  }

  public boolean isDetectedByInfravision() {
    return detectedByInfravision;
  }

  public boolean isFalseColor() {
    return falseColor;
  }

  public boolean isPathSmooth() {
    return pathSmooth;
  }

  public boolean isTranslucent() {
    return translucent;
  }

  public double getMoveScale() {
    return moveScale;
  }

  public int getEllipse() {
    return ellipse;
  }

  public int getPersonalSpace() {
    return personalSpace;
  }

  public boolean matches(Profile.Game candidateGame, int candidateId, CreatureAnimationFamily candidateFamily) {
    return game == candidateGame && animationId == candidateId && family == candidateFamily;
  }

  public String getSummary() {
    return String.format(Locale.ENGLISH, "Exact classic profile • %d installed BAM resource(s) • move scale %s",
        layout.getResources().size(), Double.toString(moveScale));
  }
}
