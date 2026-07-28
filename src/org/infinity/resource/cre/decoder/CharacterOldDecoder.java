// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre.decoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.cre.decoder.tables.SpriteTables;
import org.infinity.resource.cre.decoder.util.AnimationInfo;
import org.infinity.resource.cre.decoder.util.CycleDef;
import org.infinity.resource.cre.decoder.util.DecoderAttribute;
import org.infinity.resource.cre.decoder.util.DirDef;
import org.infinity.resource.cre.decoder.util.Direction;
import org.infinity.resource.cre.decoder.util.ItemInfo;
import org.infinity.resource.cre.decoder.util.SegmentDef;
import org.infinity.resource.cre.decoder.util.SeqDef;
import org.infinity.resource.cre.decoder.util.Sequence;
import org.infinity.resource.cre.decoder.util.SpriteUtils;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapSection;
import org.infinity.util.tuples.Couple;

/**
 * Creature animation decoder for processing type 5000/6000 (character_old) animations. Available ranges: [5400,54ff],
 * [5600,5fff], [6400,64ff], [6600,6fff]
 */
public class CharacterOldDecoder extends CharacterBaseDecoder {
  /** The animation type associated with this class definition. */
  public static final AnimationInfo.Type ANIMATION_TYPE = AnimationInfo.Type.CHARACTER_OLD;

  public static final DecoderAttribute KEY_HIDE_WEAPONS = DecoderAttribute.with("hide_weapons",
      DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_SHADOW = DecoderAttribute.with("shadow", DecoderAttribute.DataType.STRING);

  /** Assigns BAM suffix and cycle index to a specific animation sequence. */
  private static final HashMap<Sequence, Couple<String, Integer>> SUFFIX_MAP = new HashMap<>();

  /** BAM suffix and cycle index for extended walk directions. */
  private static final Couple<String, Integer> WALK_EXTRA = Couple.with("W2", 0);

  /** Set of invalid attack type / animation sequence combinations. */
  private static final EnumMap<AttackType, EnumSet<Sequence>> FORBIDDEN_SEQUENCES_MAP = new EnumMap<>(
      AttackType.class);

  // the default shadow of the animation
  private static final String SHADOW_RESREF = "CSHD";
  // special shadow for (armored) Sarevok
  private static final String SHADOW_RESREF_SAREVOK = "SSHD";

  static {
    SUFFIX_MAP.put(Sequence.ATTACK_SLASH_1H, Couple.with("A1", 0));
    SUFFIX_MAP.put(Sequence.ATTACK_SLASH_2H, Couple.with("A2", 0));
    SUFFIX_MAP.put(Sequence.ATTACK_BACKSLASH_1H, Couple.with("A3", 0));
    SUFFIX_MAP.put(Sequence.ATTACK_BACKSLASH_2H, Couple.with("A4", 0));
    SUFFIX_MAP.put(Sequence.ATTACK_JAB_1H, Couple.with("A5", 0));
    SUFFIX_MAP.put(Sequence.ATTACK_JAB_2H, Couple.with("A6", 0));
    SUFFIX_MAP.put(Sequence.ATTACK_BOW, Couple.with("SA", 0));
    SUFFIX_MAP.put(Sequence.ATTACK_CROSSBOW, Couple.with("SX", 0));
    SUFFIX_MAP.put(Sequence.SPELL, Couple.with("CA", 0));
    SUFFIX_MAP.put(Sequence.SPELL1, SUFFIX_MAP.get(Sequence.SPELL));
    SUFFIX_MAP.put(Sequence.SPELL2, Couple.with("CA", 16));
    SUFFIX_MAP.put(Sequence.SPELL3, Couple.with("CA", 32));
    SUFFIX_MAP.put(Sequence.SPELL4, Couple.with("CA", 48));
    SUFFIX_MAP.put(Sequence.CAST, Couple.with("CA", 8));
    SUFFIX_MAP.put(Sequence.CAST1, SUFFIX_MAP.get(Sequence.CAST));
    SUFFIX_MAP.put(Sequence.CAST2, Couple.with("CA", 24));
    SUFFIX_MAP.put(Sequence.CAST3, Couple.with("CA", 40));
    SUFFIX_MAP.put(Sequence.CAST4, Couple.with("CA", 56));
    SUFFIX_MAP.put(Sequence.WALK, Couple.with("G1", 0));
    SUFFIX_MAP.put(Sequence.STANCE, Couple.with("G1", 8));
    SUFFIX_MAP.put(Sequence.STANCE2, Couple.with("G1", 24));
    SUFFIX_MAP.put(Sequence.STAND, Couple.with("G1", 16));
    SUFFIX_MAP.put(Sequence.STAND2, Couple.with("G1", 32));
    SUFFIX_MAP.put(Sequence.GET_HIT, Couple.with("G1", 40));
    SUFFIX_MAP.put(Sequence.DIE, Couple.with("G1", 48));
    SUFFIX_MAP.put(Sequence.SLEEP, SUFFIX_MAP.get(Sequence.DIE));
    SUFFIX_MAP.put(Sequence.GET_UP, Couple.with("!G1", 48));
    SUFFIX_MAP.put(Sequence.TWITCH, Couple.with("G1", 56));

    FORBIDDEN_SEQUENCES_MAP.put(AttackType.ONE_HANDED,
        EnumSet.of(Sequence.ATTACK_SLASH_2H, Sequence.ATTACK_BACKSLASH_2H, Sequence.ATTACK_JAB_2H, Sequence.ATTACK_BOW,
            Sequence.ATTACK_CROSSBOW, Sequence.STANCE2));
    FORBIDDEN_SEQUENCES_MAP.put(AttackType.TWO_HANDED,
        EnumSet.of(Sequence.ATTACK_SLASH_1H, Sequence.ATTACK_BACKSLASH_1H, Sequence.ATTACK_JAB_1H, Sequence.ATTACK_BOW,
            Sequence.ATTACK_CROSSBOW, Sequence.STANCE));
    FORBIDDEN_SEQUENCES_MAP.put(AttackType.TWO_WEAPON, EnumSet.allOf(Sequence.class));
    FORBIDDEN_SEQUENCES_MAP.put(AttackType.THROWING,
        EnumSet.of(Sequence.ATTACK_BACKSLASH_1H, Sequence.ATTACK_JAB_1H, Sequence.ATTACK_SLASH_2H,
            Sequence.ATTACK_BACKSLASH_2H, Sequence.ATTACK_JAB_2H, Sequence.ATTACK_BOW, Sequence.ATTACK_CROSSBOW,
            Sequence.STANCE2));
    FORBIDDEN_SEQUENCES_MAP.put(AttackType.BOW,
        EnumSet.of(Sequence.ATTACK_SLASH_1H, Sequence.ATTACK_BACKSLASH_1H, Sequence.ATTACK_JAB_1H,
            Sequence.ATTACK_SLASH_2H, Sequence.ATTACK_BACKSLASH_2H, Sequence.ATTACK_JAB_2H, Sequence.ATTACK_CROSSBOW,
            Sequence.STANCE2));
    FORBIDDEN_SEQUENCES_MAP.put(AttackType.SLING, FORBIDDEN_SEQUENCES_MAP.get(AttackType.THROWING));
    FORBIDDEN_SEQUENCES_MAP.put(AttackType.CROSSBOW,
        EnumSet.of(Sequence.ATTACK_SLASH_1H, Sequence.ATTACK_BACKSLASH_1H, Sequence.ATTACK_JAB_1H,
            Sequence.ATTACK_SLASH_2H, Sequence.ATTACK_BACKSLASH_2H, Sequence.ATTACK_JAB_2H, Sequence.ATTACK_BOW,
            Sequence.STANCE2));
  }

  /**
   * A helper method that parses the specified data array and generates a {@link IniMap} instance out of it.
   *
   * @param data a String array containing table values for a specific table entry.
   * @return a {@code IniMap} instance with the value derived from the specified data array. Returns {@code null} if no
   *         data could be derived.
   */
  public static IniMap processTableData(String[] data) {
    IniMap retVal = null;
    if (data == null || data.length < 16) {
      return retVal;
    }

    String resref = SpriteTables.valueToString(data, SpriteTables.COLUMN_RESREF, "");
    if (resref.isEmpty()) {
      return retVal;
    }
    int equipHelmet = SpriteTables.valueToInt(data, SpriteTables.COLUMN_HELMET, 0);
    int falseColor = SpriteTables.valueToInt(data, SpriteTables.COLUMN_CLOWN, 0);
    int hideWeapons = SpriteTables.valueToInt(data, SpriteTables.COLUMN_WEAPON, 1) != 0 ? 0 : 1;
    String heightCode = SpriteTables.valueToString(data, SpriteTables.COLUMN_HEIGHT, "");
    String heightCodeHelmet = heightCode;
    String shadow = SpriteTables.valueToString(data, SpriteTables.COLUMN_RESREF2, "");

    List<String> lines = SpriteUtils.processTableDataGeneral(data, ANIMATION_TYPE);
    lines.add("[character_old]");
    lines.add("equip_helmet=" + equipHelmet);
    lines.add("false_color=" + falseColor);
    lines.add("hide_weapons=" + hideWeapons);
    lines.add("resref=" + resref);
    if (!heightCode.isEmpty()) {
      lines.add("height_code=" + heightCode);
    }
    if (!heightCodeHelmet.isEmpty()) {
      lines.add("height_code_helmet=" + heightCodeHelmet);
    }
    if (!shadow.isEmpty()) {
      lines.add("shadow=" + shadow);
    }

    retVal = IniMap.from(lines);

    return retVal;
  }

  public CharacterOldDecoder(int animationId, IniMap ini) throws Exception {
    super(ANIMATION_TYPE, animationId, ini);
  }

  public CharacterOldDecoder(CreResource cre) throws Exception {
    super(ANIMATION_TYPE, cre);
  }

  /** Returns the decoder's exact avatar suffix and cycle schema. */
  public Map<Sequence, Couple<String, Integer>> getAvatarSequenceMap() {
    return Collections.unmodifiableMap(SUFFIX_MAP);
  }

  /** Returns whether weapon animation overlays are suppressed. */
  public boolean isWeaponsHidden() {
    return getAttribute(KEY_HIDE_WEAPONS);
  }

  protected void setWeaponsHidden(boolean b) {
    setAttribute(KEY_HIDE_WEAPONS, b);
  }

  /** Returns a separate shadow sprite resref. */
  public String getShadowResref() {
    return getAttribute(KEY_SHADOW);
  }

  protected void setShadowResref(String s) {
    String shadow;
    // taking care of harcoded shadows
    switch (getAnimationId()) {
      case 0x6400: // Drizzt
        shadow = SHADOW_RESREF;
        break;
      case 0x6404: // Sarevok
        shadow = SHADOW_RESREF_SAREVOK;
        break;
      default:
        shadow = !s.isEmpty() ? s : SHADOW_RESREF;
    }
    setAttribute(KEY_SHADOW, shadow);
  }

  /**
   * Sets the maximum armor code value uses as suffix in animation filenames. Specify -1 to detect value automatically.
   */
  @Override
  protected void setMaxArmorCode(int v) {
    if (v < 0) {
      // autodetection
      for (int i = 1; i < 10 && v < 0; i++) {
        String resref = getAnimationResref();
        if (!resref.isEmpty() && !ResourceFactory.resourceExists(resref + i + "G1.BAM")) {
          v = i - 1;
        }
      }
    }
    super.setMaxArmorCode(v);
  }

  @Override
  public List<String> getAnimationFiles(boolean essential) {
    ArrayList<String> retVal;
    String resref = getAnimationResref();

    if (essential) {
      HashSet<String> files = new HashSet<>();
      for (final HashMap.Entry<Sequence, Couple<String, Integer>> entry : SUFFIX_MAP.entrySet()) {
        String suffix = SegmentDef.fixBehaviorSuffix(entry.getValue().getValue0());
        if (suffix.startsWith("G")) {
          for (int i = 1; i <= getMaxArmorCode(); i++) {
            files.add(resref + i + suffix + ".BAM");
          }
        }
      }
      retVal = new ArrayList<>(Arrays.asList(files.toArray(new String[0])));
    } else {
      // collecting suffixes
      HashSet<String> actionSet = new HashSet<>();
      for (final HashMap.Entry<Sequence, Couple<String, Integer>> entry : SUFFIX_MAP.entrySet()) {
        String suffix = SegmentDef.fixBehaviorSuffix(entry.getValue().getValue0());
        actionSet.add(suffix);
      }
      actionSet.add(WALK_EXTRA.getValue0());

      // generating file list
      retVal = new ArrayList<String>() {
        {
          for (int i = 1; i <= getMaxArmorCode(); i++) {
            for (final String a : actionSet) {
              add(resref + i + a + ".BAM");
              add(resref + i + a + "E.BAM");
            }
          }
        }
      };
    }

    return retVal;
  }

  @Override
  public boolean isSequenceAvailable(Sequence seq) {
    return (getSequenceDefinition(seq) != null);
  }

  @Override
  protected void init() throws Exception {
    super.init();
    IniMapSection section = getSpecificIniSection();
    setWeaponsHidden(section.getAsInteger(KEY_HIDE_WEAPONS.getName(), 0) != 0);
    setShadowResref(section.getAsString(KEY_SHADOW.getName(), ""));
    if (getMaxArmorCode() == 0) {
      setMaxArmorCode(-1);
    }
  }

  @Override
  protected SeqDef getSequenceDefinition(Sequence seq) {
    SeqDef retVal = null;

    if (!SUFFIX_MAP.containsKey(seq)) {
      return retVal;
    }

    // getting armor level
    int armorCode = getArmorCode();
    if (armorCode > getMaxArmorCode()) {
      return retVal;
    }

    // getting attack type
    ItemInfo itmWeapon = getCreatureInfo().getEquippedWeapon();
    int itmAbility = getCreatureInfo().getSelectedWeaponAbility();
    AttackType attackType = getAttackType(itmWeapon, itmAbility, false);

    EnumSet<Sequence> sequences = FORBIDDEN_SEQUENCES_MAP.get(attackType);
    if (sequences != null && sequences.contains(seq)) {
      // sequence not allowed for selected weapon
      return retVal;
    }

    ArrayList<Couple<String, SegmentDef.SpriteType>> resrefList = new ArrayList<>();

    // getting BAM suffix and cycle index
    String suffix = SUFFIX_MAP.get(seq).getValue0();
    SegmentDef.Behavior behavior = SegmentDef.getBehaviorOf(suffix);
    suffix = SegmentDef.fixBehaviorSuffix(suffix);
    int cycleIdx = SUFFIX_MAP.get(seq).getValue1();

    // adding creature shadow
    if (!getShadowResref().isEmpty()) {
      resrefList.add(Couple.with(getShadowResref(), SegmentDef.SpriteType.AVATAR));
    }

    // adding creature sprite
    resrefList.add(Couple.with(getAnimationResref() + armorCode, SegmentDef.SpriteType.AVATAR));

    // adding helmet overlay
    if (isHelmetEquipped()) {
      String prefix = getHelmetHeightCode();
      if (!prefix.isEmpty()) {
        ItemInfo itmHelmet = getCreatureInfo().getEquippedHelmet();
        if (itmHelmet != null) {
          String code = itmHelmet.getAppearance().trim();
          if (code.length() == 2) {
            resrefList.add(Couple.with(prefix + code, SegmentDef.SpriteType.HELMET));
          }
        }
      }
    }

    if (!isWeaponsHidden()) {
      // adding shield overlay
      String prefix = getHeightCode();
      if (!prefix.isEmpty()) {
        ItemInfo itmShield = getCreatureInfo().getEquippedShield();
        if (itmShield != null) {
          String code = itmShield.getAppearance().trim();
          if (!code.isEmpty()) {
            resrefList.add(Couple.with(prefix + code, SegmentDef.SpriteType.SHIELD));
          }
        }
      }

      // adding weapon overlay
      prefix = getHeightCode();
      if (itmWeapon != null && !prefix.isEmpty()) {
        String code = itmWeapon.getAppearance().trim();
        if (code.length() == 2) {
          if (ResourceFactory.resourceExists(prefix + code + suffix + ".BAM")) {
            resrefList.add(Couple.with(prefix + code, SegmentDef.SpriteType.WEAPON));
          } else {
            // weapon animation is crucial
            return retVal;
          }
        }
      }
    }

    retVal = new SeqDef(seq);
    for (final Couple<String, SegmentDef.SpriteType> data : resrefList) {
      String prefix = data.getValue0();
      SegmentDef.SpriteType spriteType = data.getValue1();
      // defining sequences
      ResourceEntry entry = ResourceFactory.getResourceEntry(prefix + suffix + ".BAM");
      ResourceEntry entryE = ResourceFactory.getResourceEntry(prefix + suffix + "E.BAM");
      if (entry != null) {
        if (seq == Sequence.WALK) {
          // special: uses full set of directions spread over two BAM files
          String suffix2 = WALK_EXTRA.getValue0();
          int cycleIdx2 = WALK_EXTRA.getValue1();
          ResourceEntry entry2 = ResourceFactory.getResourceEntry(prefix + suffix2 + ".BAM");
          ResourceEntry entry2E = ResourceFactory.getResourceEntry(prefix + suffix2 + "E.BAM");
          if (SpriteUtils.bamCyclesExist(entry, cycleIdx, SeqDef.DIR_REDUCED_W.length)
              && SpriteUtils.bamCyclesExist(entry2, cycleIdx2, SeqDef.DIR_REDUCED_W.length)
              && SpriteUtils.bamCyclesExist(entryE, cycleIdx + SeqDef.DIR_REDUCED_W.length, SeqDef.DIR_REDUCED_E.length)
              && SpriteUtils.bamCyclesExist(entry2E, cycleIdx2 + SeqDef.DIR_REDUCED_W.length,
                  SeqDef.DIR_REDUCED_E.length)) {
            // defining western directions
            Direction[] dirX = new Direction[] { Direction.SSW, Direction.WSW, Direction.WNW, Direction.NNW,
                Direction.NNE };
            for (int i = 0; i < SeqDef.DIR_REDUCED_W.length; i++) {
              retVal.addDirections(
                  new DirDef(SeqDef.DIR_REDUCED_W[i], false, new CycleDef(entry, cycleIdx + i, spriteType, behavior)));
              retVal
                  .addDirections(new DirDef(dirX[i], false, new CycleDef(entry2, cycleIdx2 + i, spriteType, behavior)));
            }
            // defining eastern directions
            dirX = new Direction[] { Direction.ENE, Direction.ESE, Direction.SSE, };
            for (int i = 0; i < SeqDef.DIR_REDUCED_E.length; i++) {
              retVal.addDirections(new DirDef(SeqDef.DIR_REDUCED_E[i], false,
                  new CycleDef(entryE, cycleIdx + SeqDef.DIR_REDUCED_W.length + i, spriteType, behavior)));
              retVal.addDirections(new DirDef(dirX[i], false,
                  new CycleDef(entry2E, cycleIdx2 + SeqDef.DIR_REDUCED_W.length + i, spriteType, behavior)));
            }
          }
        } else {
          if (SpriteUtils.bamCyclesExist(entry, cycleIdx, SeqDef.DIR_REDUCED_W.length) && SpriteUtils
              .bamCyclesExist(entryE, cycleIdx + SeqDef.DIR_REDUCED_W.length, SeqDef.DIR_REDUCED_E.length)) {
            SeqDef tmp = SeqDef.createSequence(seq, SeqDef.DIR_REDUCED_W, false, entry, cycleIdx, spriteType, behavior);
            retVal.addDirections(tmp.getDirections().toArray(new DirDef[0]));
            tmp = SeqDef.createSequence(seq, SeqDef.DIR_REDUCED_E, false, entryE,
                cycleIdx + SeqDef.DIR_REDUCED_W.length, spriteType, behavior);
            retVal.addDirections(tmp.getDirections().toArray(new DirDef[0]));
          }
        }
      }
    }

    if (retVal.isEmpty()) {
      retVal = null;
    }

    return retVal;
  }
}
