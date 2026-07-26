// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.infinity.resource.Profile;
import org.infinity.resource.cre.decoder.util.AnimationInfo;

/**
 * Defines the engine-facing layout of type {@code 0x7000} ({@code monster}) creature animations.
 */
public final class MonsterAnimationLayout {
  /** Enhanced Edition games supported by the creator. */
  public static final Set<Profile.Game> SUPPORTED_GAMES = Collections.unmodifiableSet(EnumSet.of(Profile.Game.BG1EE,
      Profile.Game.BG1SoD, Profile.Game.BG2EE, Profile.Game.EET, Profile.Game.IWDEE, Profile.Game.PSTEE));

  /** The nine orientations stored by type 0x7000 BAM resources. Eastern orientations are mirrored by the engine. */
  public enum Direction {
    S("S", "South", 0),
    SSW("SSW", "South-southwest", 1),
    SW("SW", "Southwest", 2),
    WSW("WSW", "West-southwest", 3),
    W("W", "West", 4),
    WNW("WNW", "West-northwest", 5),
    NW("NW", "Northwest", 6),
    NNW("NNW", "North-northwest", 7),
    N("N", "North", 8);

    private final String code;
    private final String label;
    private final int cycleOffset;

    Direction(String code, String label, int cycleOffset) {
      this.code = code;
      this.label = label;
      this.cycleOffset = cycleOffset;
    }

    public String getCode() {
      return code;
    }

    public String getLabel() {
      return label;
    }

    public int getCycleOffset() {
      return cycleOffset;
    }

    public static Direction fromCode(String value) {
      if (value != null) {
        final String normalized = value.trim().toUpperCase(Locale.ENGLISH);
        for (final Direction direction : values()) {
          if (direction.code.equals(normalized)) {
            return direction;
          }
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return code + " - " + label;
    }
  }

  /** All action sequences supported by the unsplit type 0x7000 layout. */
  public enum Sequence {
    WALK("WK", "Walk", 1, 0, 8, true),
    STANCE("SC", "Ready stance", 1, 9, 6, true),
    STAND("SD", "Idle / head turn", 1, 18, 6, true),
    GET_HIT("GH", "Take damage", 1, 27, 4, false),
    DIE("DE", "Die", 1, 36, 8, false),
    TWITCH("TW", "Twitch", 1, 45, 4, false),
    SLEEP("SL", "Sleep", 1, 54, 2, true),
    GET_UP("GU", "Get up", 1, 63, 8, false),
    ATTACK_1("A1", "Attack 1", 2, 0, 6, false),
    ATTACK_2("A2", "Attack 2", 2, 9, 6, false),
    ATTACK_3("A3", "Attack 3", 2, 18, 6, false),
    ATTACK_4("A4", "Attack 4", 2, 27, 6, false),
    ATTACK_5("A5", "Attack 5", 2, 36, 6, false),
    CONJURE("SP", "Conjure", 2, 45, 8, false),
    CAST("CA", "Cast", 2, 54, 8, false);

    private final String code;
    private final String label;
    private final int group;
    private final int cycleOffset;
    private final int suggestedFrameCount;
    private final boolean looped;

    Sequence(String code, String label, int group, int cycleOffset, int suggestedFrameCount, boolean looped) {
      this.code = code;
      this.label = label;
      this.group = group;
      this.cycleOffset = cycleOffset;
      this.suggestedFrameCount = suggestedFrameCount;
      this.looped = looped;
    }

    public String getCode() {
      return code;
    }

    public String getLabel() {
      return label;
    }

    public int getGroup() {
      return group;
    }

    public int getCycleOffset() {
      return cycleOffset;
    }

    public int getSuggestedFrameCount() {
      return suggestedFrameCount;
    }

    public boolean isLooped() {
      return looped;
    }

    public static Sequence fromCode(String value) {
      if (value != null) {
        final String normalized = value.trim().toUpperCase(Locale.ENGLISH);
        for (final Sequence sequence : values()) {
          if (sequence.code.equals(normalized)) {
            return sequence;
          }
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return code + " - " + label;
    }
  }

  /** BAM encodings offered by the creator. */
  public enum BamFormat {
    BAM_V1("BAM V1 / BAMC", "Palette-based BAM; most compatible for Enhanced Edition creature animations"),
    BAM_V2("BAM V2 / PVRZ", "Truecolor BAM with external PVRZ texture pages");

    private final String label;
    private final String description;

    BamFormat(String label, String description) {
      this.label = label;
      this.description = description;
    }

    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /** Location of one action sequence in an output BAM resource. */
  public static final class OutputSlot {
    private final Sequence sequence;
    private final String suffix;
    private final int cycleOffset;

    private OutputSlot(Sequence sequence, String suffix, int cycleOffset) {
      this.sequence = sequence;
      this.suffix = suffix;
      this.cycleOffset = cycleOffset;
    }

    public Sequence getSequence() {
      return sequence;
    }

    public String getSuffix() {
      return suffix;
    }

    public int getCycleOffset() {
      return cycleOffset;
    }
  }

  private MonsterAnimationLayout() {
  }

  public static boolean isSupportedGame(Profile.Game game) {
    return game != null && SUPPORTED_GAMES.contains(game);
  }

  public static boolean isValidSlot(Profile.Game game, int animationId) {
    return isSupportedGame(game) && AnimationInfo.Type.MONSTER.contains(game, animationId);
  }

  /**
   * Returns the output layout in stable filename and cycle order.
   *
   * <p>Split layouts intentionally omit independent sleep and get-up data. The engine reuses the split death resource,
   * reversing it for get-up.</p>
   */
  public static Map<String, List<OutputSlot>> getOutputLayout(boolean splitBams) {
    final Map<String, List<OutputSlot>> result = new LinkedHashMap<>();
    if (!splitBams) {
      for (final Sequence sequence : Sequence.values()) {
        addSlot(result, new OutputSlot(sequence, "G" + sequence.getGroup(), sequence.getCycleOffset()));
      }
    } else {
      addSlot(result, new OutputSlot(Sequence.WALK, "G11", 0));
      addSlot(result, new OutputSlot(Sequence.STANCE, "G1", 9));
      addSlot(result, new OutputSlot(Sequence.STAND, "G12", 18));
      addSlot(result, new OutputSlot(Sequence.GET_HIT, "G13", 27));
      addSlot(result, new OutputSlot(Sequence.DIE, "G14", 36));
      addSlot(result, new OutputSlot(Sequence.TWITCH, "G15", 45));
      addSlot(result, new OutputSlot(Sequence.ATTACK_1, "G2", 0));
      addSlot(result, new OutputSlot(Sequence.ATTACK_2, "G21", 9));
      addSlot(result, new OutputSlot(Sequence.ATTACK_3, "G22", 18));
      addSlot(result, new OutputSlot(Sequence.ATTACK_4, "G23", 27));
      addSlot(result, new OutputSlot(Sequence.ATTACK_5, "G24", 36));
      addSlot(result, new OutputSlot(Sequence.CONJURE, "G25", 45));
      addSlot(result, new OutputSlot(Sequence.CAST, "G26", 54));
    }

    for (final Map.Entry<String, List<OutputSlot>> entry : result.entrySet()) {
      entry.setValue(Collections.unmodifiableList(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  public static List<Sequence> getFallbackOrder(Sequence sequence) {
    switch (sequence) {
      case WALK:
        return Arrays.asList(Sequence.WALK, Sequence.STANCE, Sequence.STAND);
      case STANCE:
        return Arrays.asList(Sequence.STANCE, Sequence.STAND, Sequence.WALK);
      case STAND:
        return Arrays.asList(Sequence.STAND, Sequence.STANCE, Sequence.WALK);
      case GET_HIT:
        return Arrays.asList(Sequence.GET_HIT, Sequence.STANCE, Sequence.STAND);
      case DIE:
        return Arrays.asList(Sequence.DIE, Sequence.TWITCH, Sequence.STANCE);
      case TWITCH:
        return Arrays.asList(Sequence.TWITCH, Sequence.DIE, Sequence.STANCE);
      case SLEEP:
        return Arrays.asList(Sequence.SLEEP, Sequence.DIE, Sequence.STAND);
      case GET_UP:
        return Arrays.asList(Sequence.GET_UP, Sequence.DIE, Sequence.STAND);
      case ATTACK_1:
        return Arrays.asList(Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.STANCE);
      case ATTACK_2:
      case ATTACK_3:
      case ATTACK_4:
      case ATTACK_5:
        return Arrays.asList(sequence, Sequence.ATTACK_1, Sequence.STANCE);
      case CONJURE:
        return Arrays.asList(Sequence.CONJURE, Sequence.CAST, Sequence.STANCE);
      case CAST:
        return Arrays.asList(Sequence.CAST, Sequence.CONJURE, Sequence.STANCE);
      default:
        return Collections.singletonList(sequence);
    }
  }

  private static void addSlot(Map<String, List<OutputSlot>> layout, OutputSlot slot) {
    List<OutputSlot> slots = layout.get(slot.getSuffix());
    if (slots == null) {
      slots = new ArrayList<>();
      layout.put(slot.getSuffix(), slots);
    }
    slots.add(slot);
  }
}
