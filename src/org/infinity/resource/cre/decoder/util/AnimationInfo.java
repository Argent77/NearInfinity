// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre.decoder.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

import org.infinity.resource.Profile;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.tuples.Couple;

/**
 * Static class containing animation slot and type information.
 */
public class AnimationInfo {
  // Predefined sets of games with common animation slot mappings
  private static final EnumSet<Profile.Game> TYPE_GAME_BG1      = EnumSet.of(Profile.Game.BG1, Profile.Game.BG1TotSC);
  private static final EnumSet<Profile.Game> TYPE_GAME_BG2_TOB  = EnumSet.of(Profile.Game.BG2ToB, Profile.Game.BGT, Profile.Game.Tutu);
  // all BG2 game variants
  private static final EnumSet<Profile.Game> TYPE_GAME_BG2      = EnumSet.of(Profile.Game.BG2SoA, Profile.Game.BG2ToB, Profile.Game.BGT,
      Profile.Game.Tutu);
  private static final EnumSet<Profile.Game> TYPE_GAME_IWD      = EnumSet.of(Profile.Game.IWD);
  private static final EnumSet<Profile.Game> TYPE_GAME_IWD_HOW  = EnumSet.of(Profile.Game.IWDHoW, Profile.Game.IWDHowTotLM);
  private static final EnumSet<Profile.Game> TYPE_GAME_IWD2     = EnumSet.of(Profile.Game.IWD2);
  private static final EnumSet<Profile.Game> TYPE_GAME_PST      = EnumSet.of(Profile.Game.PST);
  // all EE games
  private static final EnumSet<Profile.Game> TYPE_GAME_EE       = EnumSet.of(Profile.Game.BG1EE, Profile.Game.BG1SoD, Profile.Game.BG2EE,
      Profile.Game.EET, Profile.Game.IWDEE, Profile.Game.PSTEE);
  private static final EnumSet<Profile.Game> TYPE_GAME_PSTEE    = EnumSet.of(Profile.Game.PSTEE);
  // all games except PST
  private static final EnumSet<Profile.Game> TYPE_GAME_ALL      = EnumSet.complementOf(EnumSet.of(Profile.Game.Unknown, Profile.Game.PST));

  // Predefined slot ranges for specific animation types
  private static final List<NumberRange> RANGE_EFFECT                 = Collections.singletonList(new NumberRange(0, 0xfff));
  private static final List<NumberRange> RANGE_MONSTER_QUADRANT       = Collections.singletonList(new NumberRange(0x1000, 0x1ff));
  private static final List<NumberRange> RANGE_MONSTER_MULTI          = Arrays.asList(new NumberRange(0x1200, 0xff),
      new NumberRange(0x1400, 0xbff));
  private static final List<NumberRange> RANGE_MONSTER_MULTI_NEW      = Collections.singletonList(new NumberRange(0x1300, 0xff));
  private static final List<NumberRange> RANGE_MONSTER_LAYERED        = Collections.singletonList(new NumberRange(0x8000, 0xfff));
  private static final List<NumberRange> RANGE_MONSTER_LAYERED_SPELL  = Collections.singletonList(new NumberRange(0x2000, 0xfff));
  private static final List<NumberRange> RANGE_MONSTER_ANKHEG         = Collections.singletonList(new NumberRange(0x3000, 0xfff));
  private static final List<NumberRange> RANGE_TOWN_STATIC            = Collections.singletonList(new NumberRange(0x4000, 0xfff));
  private static final List<NumberRange> RANGE_CHARACTER              = Arrays.asList(new NumberRange(0x5000, 0x3ff),
      new NumberRange(0x5500, 0xff),
      new NumberRange(0x6000, 0x3ff),
      new NumberRange(0x6500, 0xff));
  private static final List<NumberRange> RANGE_CHARACTER_IA           = Collections.singletonList(new NumberRange(0x6600, 0x4ff));
  private static final List<NumberRange> RANGE_CHARACTER_OLD          = Arrays.asList(new NumberRange(0x5400, 0xff),
      new NumberRange(0x5600, 0x9ff),
      new NumberRange(0x6400, 0xff),
      new NumberRange(0x6600, 0x9ff));
  private static final List<NumberRange> RANGE_MONSTER                = Arrays.asList(new NumberRange(0x7002, 0xd, 0x00, 0x1f, 4),
      new NumberRange(0x7004, 0xb, 0x20, 0x0f, 4),
      new NumberRange(0x7000, 0xf, 0x30, 0x0f, 4),
      new NumberRange(0x7003, 0xc, 0x40, 0x0f, 4),
      new NumberRange(0x7002, 0xd, 0x50, 0x0f, 4),
      new NumberRange(0x7003, 0xc, 0x70, 0x0f, 4),
      new NumberRange(0x7005, 0xa, 0x90, 0x1f, 4),
      new NumberRange(0x7007, 0x8, 0xb0, 0x0f, 4),
      new NumberRange(0x7002, 0xd, 0xc0, 0x0f, 4),
      new NumberRange(0x7002, 0xd, 0xe0, 0x0f, 4),
      new NumberRange(0x7000, 0xf, 0xf0, 0x0f, 4));
  private static final List<NumberRange> RANGE_MONSTER_IA             = Collections.singletonList(new NumberRange(0x5b00, 0x4ff));
  private static final List<NumberRange> RANGE_MONSTER_OLD            = Arrays.asList(new NumberRange(0x7000, 0x1, 0x00, 0x1f, 4),
      new NumberRange(0x7000, 0x3, 0x20, 0x0f, 4),
      new NumberRange(0x7000, 0x2, 0x40, 0x0f, 4),
      new NumberRange(0x7000, 0x1, 0x50, 0x0f, 4),
      new NumberRange(0x7000, 0xf, 0x60, 0x0f, 4),
      new NumberRange(0x7000, 0x2, 0x70, 0x0f, 4),
      new NumberRange(0x7000, 0xf, 0x80, 0x0f, 4),
      new NumberRange(0x7000, 0x4, 0x90, 0x1f, 4),
      new NumberRange(0x7000, 0x6, 0xb0, 0x0f, 4),
      new NumberRange(0x7000, 0x1, 0xc0, 0x0f, 4),
      new NumberRange(0x7000, 0xf, 0xd0, 0x0f, 4),
      new NumberRange(0x7000, 0x1, 0xe0, 0x0f, 4));
  private static final List<NumberRange> RANGE_MONSTER_OLD_IA         = Collections.singletonList(new NumberRange(0x547a, 0x479));
  private static final List<NumberRange> RANGE_MONSTER_LARGE          = Collections.singletonList(new NumberRange(0x9000, 0xfff));
  private static final List<NumberRange> RANGE_MONSTER_LARGE_16       = Collections.singletonList(new NumberRange(0xa000, 0xfff));
  private static final List<NumberRange> RANGE_AMBIENT_STATIC         = Collections.singletonList(new NumberRange(0xb000, 0xfff));
  private static final List<NumberRange> RANGE_AMBIENT                = Collections.singletonList(new NumberRange(0xc000, 0xfff));
  private static final List<NumberRange> RANGE_FLYING                 = Collections.singletonList(new NumberRange(0xd000, 0xfff));
  private static final List<NumberRange> RANGE_MONSTER_ICEWIND        = Collections.singletonList(new NumberRange(0xe000, 0xfff));
  private static final List<NumberRange> RANGE_MONSTER_ICEWIND_EX     = Collections.singletonList(new NumberRange(0xe000, 0x1fff));
  private static final List<NumberRange> RANGE_MONSTER_ICEWIND_IA     = Collections.singletonList(new NumberRange(0x5000, 0x478));
  private static final List<NumberRange> RANGE_MONSTER_PLANESCAPE     = Collections.singletonList(new NumberRange(0xf000, 0xfff));
  private static final List<NumberRange> RANGE_MONSTER_PLANESCAPE_EX  = Collections.singletonList(new NumberRange(0x0000, 0xffff));

  public enum Type {
    /** Animation type: 0000 */
    EFFECT(0x0000, "effect", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_EFFECT))),                   // type=0
    /** Animation type: 1000 (slots [1000..11ff]) */
    MONSTER_QUADRANT(0x1000, "monster_quadrant", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_MONSTER_QUADRANT))),         // type=1
    /** Animation type: 1000 (slots [1200..12ff], [1400..1fff] */
    MONSTER_MULTI(0x1000, "monster_multi", Arrays.asList(
        Couple.with(TYPE_GAME_EE, RANGE_MONSTER_MULTI),
        Couple.with(TYPE_GAME_BG2, RANGE_MONSTER_MULTI),
        Couple.with(TYPE_GAME_IWD2, RANGE_MONSTER_MULTI))),           // type=2
    /** Animation type: 1000 (slots [1300..13ff]) */
    MONSTER_MULTI_NEW(0x1000, "multi_new", Arrays.asList(
        Couple.with(TYPE_GAME_EE, RANGE_MONSTER_MULTI_NEW),
        Couple.with(TYPE_GAME_BG2_TOB, RANGE_MONSTER_MULTI_NEW))),    // type=3
    /** Animation type: 8000 */
    MONSTER_LAYERED(0x8000, "monster_layered", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_MONSTER_LAYERED))),          // type=4
    /** Animation type: 2000 */
    MONSTER_LAYERED_SPELL(0x2000, "monster_layered_spell", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_MONSTER_LAYERED_SPELL))),    // type=5
    /** Animation type: 3000 */
    MONSTER_ANKHEG(0x3000, "monster_ankheg", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_MONSTER_ANKHEG))),           // type=6
    /** Animation type: 4000 */
    TOWN_STATIC(0x4000, "town_static", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_TOWN_STATIC))),              // type=7
    /** Animation types: 5000, 6000 (slots [5000..53ff], [5500..55ff], [6000..63ff], [6500..65ff]) */
    CHARACTER(new int[] {0x5000, 0x6000}, "character", Arrays.asList(
        Couple.with(TYPE_GAME_EE, RANGE_CHARACTER),
        Couple.with(TYPE_GAME_BG2, RANGE_CHARACTER),
        Couple.with(TYPE_GAME_IWD_HOW, RANGE_CHARACTER),
        Couple.with(TYPE_GAME_IWD2, RANGE_CHARACTER)),
        RANGE_CHARACTER_IA),                                          // type=8
    /** Animation types: 5000, 6000 (slots [5400..54ff], [5600..5fff], [6400..64ff], [6600..6fff]) */
    CHARACTER_OLD(new int[] {0x5000, 0x6000}, "character_old", Arrays.asList(
        Couple.with(TYPE_GAME_ALL, RANGE_CHARACTER_OLD),
        Couple.with(TYPE_GAME_BG1, RANGE_CHARACTER),
        Couple.with(TYPE_GAME_IWD, RANGE_CHARACTER))),                // type=9
    /** Animation type: 7000 (many subranges) */
    MONSTER(0x7000, "monster", Arrays.asList(
        Couple.with(TYPE_GAME_EE, RANGE_MONSTER),
        Couple.with(TYPE_GAME_BG2, RANGE_MONSTER),
        Couple.with(TYPE_GAME_IWD, RANGE_MONSTER),
        Couple.with(TYPE_GAME_IWD_HOW, RANGE_MONSTER),
        Couple.with(TYPE_GAME_IWD2, RANGE_MONSTER)),
        RANGE_MONSTER_IA),                                            // type=10
    /** Animation type: 7000 (many subranges) */
    MONSTER_OLD(0x7000, "monster_old", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_MONSTER_OLD)),
        RANGE_MONSTER_OLD_IA),                                        // type=11
    /** Animation type: 9000 */
    MONSTER_LARGE(0x9000, "monster_large", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_MONSTER_LARGE))),            // type=12
    /** Animation type: A000 */
    MONSTER_LARGE_16(0xa000, "monster_large16", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_MONSTER_LARGE_16))),         // type=13
    /** Animation type: B000 */
    AMBIENT_STATIC(0xb000, "ambient_static", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_AMBIENT_STATIC))),           // type=14
    /** Animation type: C000 */
    AMBIENT(0xc000, "ambient", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_AMBIENT))),                  // type=15
    /** Animation type: D000 */
    FLYING(0xd000, "flying", Collections.singletonList(
        Couple.with(TYPE_GAME_ALL, RANGE_FLYING))),                   // type=16
    /** Animation type: E000 (for non-EE: also slots [f000..ffff]) */
    MONSTER_ICEWIND(0xe000, "monster_icewind", Arrays.asList(
        Couple.with(TYPE_GAME_EE, RANGE_MONSTER_ICEWIND),
        Couple.with(TYPE_GAME_BG2, RANGE_MONSTER_ICEWIND),
        Couple.with(TYPE_GAME_IWD, RANGE_MONSTER_ICEWIND_EX),
        Couple.with(TYPE_GAME_IWD_HOW, RANGE_MONSTER_ICEWIND_EX),
        Couple.with(TYPE_GAME_IWD2, RANGE_MONSTER_ICEWIND_EX)),
        RANGE_MONSTER_ICEWIND_IA),                                    // type=17
    /** Animation type: F000 */
    MONSTER_PLANESCAPE(0xf000, "monster_planescape", Arrays.asList(
        Couple.with(TYPE_GAME_PSTEE, RANGE_MONSTER_PLANESCAPE),
        Couple.with(TYPE_GAME_PST, RANGE_MONSTER_PLANESCAPE_EX))),    // type=18

    /** Pseudo animation type: fallback option for non-existing animations */
    PLACEHOLDER(new int[] {0x0000, 0x1000, 0x2000, 0x3000, 0x4000, 0x5000, 0x6000, 0x7000,
        0x8000, 0x9000, 0xa000, 0xb000, 0xc000, 0xd000, 0xe000, 0xf000},
        "placeholder",
        Collections.singletonList(Couple.with(TYPE_GAME_ALL, Collections.singletonList(new NumberRange(0x0000, 0xffff)))),
        null);

    private final EnumMap<Profile.Game, List<NumberRange>> rangeMap = new EnumMap<>(Profile.Game.class);
    private final List<NumberRange> iaRanges;
    private final int[] animationTypes;
    private final String sectionName;

    /**
     * @param type        slot base range
     * @param sectionName INI section name
     * @param entries     list of games and their associated slot ranges.
     */
    Type(int type, String sectionName, List<Couple<EnumSet<Profile.Game>, List<NumberRange>>> entries)
        throws IllegalArgumentException {
      this(new int[] { type }, sectionName, entries, null);
    }

    /**
     * @param type
     * @param sectionName
     * @param entries
     * @param infinityAnimationRanges
     * @throws IllegalArgumentException
     */
    Type(int type, String sectionName, List<Couple<EnumSet<Profile.Game>, List<NumberRange>>> entries,
        List<NumberRange> infinityAnimationRanges) throws IllegalArgumentException {
      this(new int[] { type }, sectionName, entries, infinityAnimationRanges);
    }

    /**
     * @param types
     * @param sectionName
     * @param entries
     * @throws IllegalArgumentException
     */
    Type(int[] types, String sectionName, List<Couple<EnumSet<Profile.Game>, List<NumberRange>>> entries)
        throws IllegalArgumentException {
      this(types, sectionName, entries, null);
    }

    /**
     * @param types       list of slot base ranges
     * @param sectionName INI section name
     * @param entries     list of games and their associated slot ranges.
     * @throws IllegalArgumentException
     */
    Type(int[] types, String sectionName, List<Couple<EnumSet<Profile.Game>, List<NumberRange>>> entries,
        List<NumberRange> infinityAnimationRanges) throws IllegalArgumentException {
      try {
        Misc.requireCondition(types != null && types.length > 0, "Type cannot be empty",
            IllegalArgumentException.class);
        Misc.requireCondition(sectionName != null && !sectionName.isEmpty(), "Section name cannot be empty",
            IllegalArgumentException.class);
      } catch (IllegalArgumentException iae) {
        throw iae;
      } catch (Exception e) {
        Logger.trace(e);
      }
      this.animationTypes = types;
      this.sectionName = sectionName;
      for (final Couple<EnumSet<Profile.Game>, List<NumberRange>> entry : entries) {
        EnumSet<Profile.Game> games = entry.getValue0();
        List<NumberRange> ranges = entry.getValue1();
        for (final Profile.Game game : games) {
          if (!ranges.isEmpty()) {
            List<NumberRange> list = this.rangeMap.get(game);
            if (list != null) {
              list.addAll(ranges);
            } else {
              this.rangeMap.put(game, new ArrayList<>(ranges));
            }
          }
        }
      }
      this.iaRanges = infinityAnimationRanges;
    }

    /** Returns the name for the type-specific INI section. */
    public String getSectionName() {
      return sectionName;
    }

    /** Returns the first available base animation type associated with the enum instance. */
    public int getType() {
      return animationTypes[0];
    }

    /** Returns the number of defined base animation types associated with the enum instance. */
    public int getTypeCount() {
      return animationTypes.length;
    }

    /** Returns the specified base animation type associated with the enum instance. */
    public int getType(int idx) {
      return animationTypes[idx];
    }

    /**
     * Returns whether the specified value is covered by the ranges associated with the enum instance for the current
     * game.
     */
    public boolean contains(int value) {
      return contains(Profile.getGame(), value);
    }

    /**
     * Returns whether the specified value is covered by the ranges associated with the enum instance for the specified
     * game.
     */
    public boolean contains(Profile.Game game, int value) {
      if (game == null) {
        game = Profile.getGame();
      }
      boolean retVal = false;

      // Infinity Animations sprite takes precedence over regular sprite
      Type type = containsInfinityAnimations(value);
      if (type != null && type != this) {
        return retVal;
      }
      retVal = (type == this);

      if (!retVal) {
        retVal = contains(value, rangeMap.get(game));
      }
      return retVal;
    }

    // Checks whether specified value is covered by the given ranges
    private static boolean contains(int value, List<NumberRange> ranges) {
      if (ranges != null) {
        return ranges.stream().anyMatch(r -> r.contains(value));
      }
      return false;
    }

    /**
     * Determines the {@code AnimationType} enum where a defined Infinity Animations (IA) range covers the specied
     * value.
     *
     * @param value the value to check.
     * @return {@code AnimationType} enum supporting the specified IA value. Returns {@code null} if value is not
     *         covered by any IA range.
     */
    public static Type containsInfinityAnimations(int value) {
      Type retVal = null;
      final Integer infinityAnimations = Profile.getProperty(Profile.Key.GET_INFINITY_ANIMATIONS);
      if (infinityAnimations != null && infinityAnimations > 0) {
        for (Type type : Type.values()) {
          if (contains(value, type.iaRanges)) {
            retVal = type;
            break;
          }
        }
      }
      return retVal;
    }

    /**
     * Returns the {@code AnimationType} enum covering the specified animation id.
     *
     * @param animationId the animation id
     * @return {@code AnimationType} enum that covers the specified animation id. Returns {@code null} otherwise.
     */
    public static Type typeOfId(int animationId) {
      for (final Type type : values()) {
        if (type.contains(animationId)) {
          return type;
        }
      }
      return null;
    }
  }

  private AnimationInfo() {
  }
}
