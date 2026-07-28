// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.LayoutBuilder;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.AttackStyle;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.WeaponType;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.OutputSlot;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.cre.decoder.CharacterBaseDecoder;
import org.infinity.resource.cre.decoder.CharacterDecoder;
import org.infinity.resource.cre.decoder.CharacterOldDecoder;
import org.infinity.resource.cre.decoder.MonsterDecoder;
import org.infinity.resource.cre.decoder.MonsterLayeredDecoder;
import org.infinity.resource.cre.decoder.MonsterLayeredSpellDecoder;
import org.infinity.resource.cre.decoder.SpriteDecoder;
import org.infinity.resource.cre.decoder.util.AnimationInfo;
import org.infinity.resource.cre.decoder.util.SegmentDef;
import org.infinity.util.tuples.Couple;

/**
 * Declarative equipment-overlay layouts for every Near Infinity decoder that emits weapon sprite segments.
 */
public enum EquipmentOverlayFamily {
  CHARACTER(AnimationInfo.Type.CHARACTER, CreatureAnimationFamily.CHARACTER, "character", CodeMode.FULL),
  CHARACTER_OLD(AnimationInfo.Type.CHARACTER_OLD, CreatureAnimationFamily.CHARACTER_OLD, "character_old",
      CodeMode.FULL),
  MONSTER(AnimationInfo.Type.MONSTER, CreatureAnimationFamily.MONSTER, "monster", CodeMode.FULL),
  MONSTER_LAYERED_SPELL(AnimationInfo.Type.MONSTER_LAYERED_SPELL,
      CreatureAnimationFamily.MONSTER_LAYERED_SPELL, "monster_layered_spell", CodeMode.FIRST_CHARACTER),
  MONSTER_LAYERED(AnimationInfo.Type.MONSTER_LAYERED, CreatureAnimationFamily.MONSTER_LAYERED, "monster_layered",
      CodeMode.FIRST_CHARACTER),
  MONSTER_ICEWIND(AnimationInfo.Type.MONSTER_ICEWIND, CreatureAnimationFamily.MONSTER_ICEWIND, "monster_icewind",
      CodeMode.FIRST_CHARACTER);

  private enum CodeMode {
    FULL,
    FIRST_CHARACTER
  }

  public enum AttackKind {
    ONE_HANDED,
    TWO_HANDED,
    TWO_WEAPON,
    BOW,
    CROSSBOW,
    SLING
  }

  public enum OverlaySlot {
    MAIN_HAND,
    SHIELD,
    OFF_HAND_WEAPON
  }

  private static final int[] FULL_W = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
  private static final int[] REDUCED_W = { 0, 2, 4, 6, 8 };
  private static final int[] REDUCED_E = { 10, 12, 14 };
  private static final int[] WALK_EXTRA_W = { 1, 3, 5, 7, 9 };
  private static final int[] WALK_EXTRA_E = { 11, 13, 15 };

  private final AnimationInfo.Type animationType;
  private final CreatureAnimationFamily creatureFamily;
  private final String label;
  private final CodeMode codeMode;

  EquipmentOverlayFamily(AnimationInfo.Type animationType, CreatureAnimationFamily creatureFamily, String label,
      CodeMode codeMode) {
    this.animationType = animationType;
    this.creatureFamily = creatureFamily;
    this.label = label;
    this.codeMode = codeMode;
  }

  public AnimationInfo.Type getAnimationType() {
    return animationType;
  }

  public CreatureAnimationFamily getCreatureFamily() {
    return creatureFamily;
  }

  public boolean usesFullAppearanceCodeInFileName() {
    return codeMode == CodeMode.FULL;
  }

  public boolean hasExplicitEasternResources() {
    return this == CHARACTER_OLD || this == MONSTER_LAYERED_SPELL || this == MONSTER_LAYERED
        || this == MONSTER_ICEWIND;
  }

  /**
   * Returns whether the animation definition itself restricts the first appearance-code character.
   */
  public boolean isAppearanceCodeRestrictedByDefinition() {
    return this == MONSTER_LAYERED_SPELL || this == MONSTER_LAYERED;
  }

  public boolean isOptionalResource(String fileName) {
    return this == MONSTER_ICEWIND && fileName != null
        && fileName.toUpperCase(Locale.ENGLISH).endsWith("E.BAM");
  }

  public static EquipmentOverlayFamily forDecoder(SpriteDecoder decoder) {
    if (decoder != null) {
      for (final EquipmentOverlayFamily family : values()) {
        if (decoder.getAnimationType() == family.animationType) {
          return family;
        }
      }
    }
    return null;
  }

  /**
   * Returns the resource prefix used by weapon BAMs for the supplied decoder.
   *
   * @param monsterResref resolved solid-layer resref for type {@code 0x7000}; ignored by other families
   */
  public String getOverlayResourcePrefix(SpriteDecoder decoder, String monsterResref) {
    return getOverlayResourcePrefix(decoder, monsterResref, OverlaySlot.MAIN_HAND);
  }

  /**
   * Returns the exact BAM prefix used by the requested equipment slot.
   */
  public String getOverlayResourcePrefix(SpriteDecoder decoder, String monsterResref, OverlaySlot slot) {
    if (decoder == null || decoder.getAnimationType() != animationType) {
      throw new IllegalArgumentException("The decoder does not belong to " + label + ".");
    }
    if (slot == null) {
      throw new IllegalArgumentException("An equipment overlay slot is required.");
    }
    final String value;
    if (this == CHARACTER && slot != OverlaySlot.MAIN_HAND) {
      value = ((CharacterDecoder) decoder).getShieldHeightCode();
    } else if (this == CHARACTER || this == CHARACTER_OLD) {
      value = ((CharacterBaseDecoder) decoder).getHeightCode();
    } else if (this == MONSTER && monsterResref != null && !monsterResref.trim().isEmpty()) {
      value = monsterResref;
    } else {
      value = decoder.getAnimationResref();
    }
    final String normalized = normalizePrefix(value);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " does not define a usable " + getSlotLabel(slot)
          + " overlay resource prefix.");
    }
    return normalized;
  }

  /**
   * Returns exact avatar-resource prefix candidates in decoder fallback order.
   */
  public List<String> getAvatarResourcePrefixes(SpriteDecoder decoder, String monsterResref) {
    if (decoder == null || decoder.getAnimationType() != animationType) {
      throw new IllegalArgumentException("The decoder does not belong to " + label + ".");
    }
    final Set<String> result = new LinkedHashSet<>();
    if (this == CHARACTER) {
      final CharacterDecoder character = (CharacterDecoder) decoder;
      final int armorCode = character.getArmorCode();
      if (armorCode > 1) {
        addArmorPrefix(result, character.getArmorSpecificResref(), armorCode);
      }
      addArmorPrefix(result, character.getArmorBaseResref(), armorCode);
      addArmorPrefix(result, character.getAnimationResref(), armorCode);
    } else if (this == CHARACTER_OLD) {
      final CharacterOldDecoder character = (CharacterOldDecoder) decoder;
      addArmorPrefix(result, character.getAnimationResref(), character.getArmorCode());
    } else if (this == MONSTER && monsterResref != null && !monsterResref.trim().isEmpty()) {
      addPrefix(result, monsterResref);
      addPrefix(result, decoder.getAnimationResref());
    } else {
      addPrefix(result, decoder.getAnimationResref());
    }
    return Collections.unmodifiableList(new ArrayList<>(result));
  }

  public boolean isAvatarSplit(SpriteDecoder decoder) {
    if (this == CHARACTER) {
      return ((CharacterDecoder) decoder).isSplittedBams();
    }
    if (this == MONSTER) {
      return ((MonsterDecoder) decoder).isSplittedBams();
    }
    return false;
  }

  /**
   * Returns the one-character source prefix mandated by layered animation INI fields, or an empty string for
   * resource-discovered families.
   */
  public String getRequiredSourceLayerCode(SpriteDecoder decoder, WeaponType targetWeapon) {
    if (targetWeapon == null) {
      throw new IllegalArgumentException("A target weapon is required.");
    }
    String value = "";
    if (this == MONSTER_LAYERED) {
      final MonsterLayeredDecoder layered = (MonsterLayeredDecoder) decoder;
      value = targetWeapon.isTwoHanded() ? layered.getWeapon2Overlay() : layered.getWeapon1Overlay();
    } else if (this == MONSTER_LAYERED_SPELL) {
      final MonsterLayeredSpellDecoder layered = (MonsterLayeredSpellDecoder) decoder;
      value = targetWeapon.isTwoHanded() ? layered.getWeapon2Overlay() : layered.getWeapon1Overlay();
    }
    final String normalized = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
    return normalized.isEmpty() ? "" : normalized.substring(0, 1);
  }

  public void validateDecoder(SpriteDecoder decoder, WeaponType targetWeapon) {
    validateDecoder(decoder, targetWeapon, null);
  }

  public void validateDecoder(SpriteDecoder decoder, WeaponType mainHand, WeaponType offhand) {
    if (decoder == null || decoder.getAnimationType() != animationType) {
      throw new IllegalArgumentException("The selected reference does not use " + label + ".");
    }
    validateLoadout(mainHand, offhand);
    if (this == CHARACTER_OLD && ((CharacterOldDecoder) decoder).isWeaponsHidden()) {
      throw new IllegalArgumentException("The selected character_old definition suppresses equipment overlays.");
    }
    if ((this == CHARACTER || this == CHARACTER_OLD)
        && normalizeOptional(((CharacterBaseDecoder) decoder).getHeightCode()).isEmpty()) {
      throw new IllegalArgumentException("The selected " + label + " definition has no weapon height code.");
    }
    if ((this == MONSTER_LAYERED || this == MONSTER_LAYERED_SPELL)
        && getRequiredSourceLayerCode(decoder, mainHand).isEmpty()) {
      throw new IllegalArgumentException("The selected " + label + " definition has no "
          + (mainHand.isTwoHanded() ? "two-handed" : "one-handed") + " weapon-overlay prefix.");
    }
    if (offhand != null) {
      final OverlaySlot slot = offhand.isShield() ? OverlaySlot.SHIELD : OverlaySlot.OFF_HAND_WEAPON;
      getOverlayResourcePrefix(decoder, null, slot);
    }
  }

  public FamilyLayout createOverlayLayout(String resourcePrefix, String appearanceCode, WeaponType weaponType) {
    return createOverlayLayout(resourcePrefix, appearanceCode, weaponType, getAttackKind(weaponType, null),
        OverlaySlot.MAIN_HAND);
  }

  public FamilyLayout createOverlayLayout(String resourcePrefix, String appearanceCode, WeaponType equipmentType,
      AttackKind attackKind, OverlaySlot slot) {
    validateLayer(equipmentType, attackKind, slot);
    final String prefix = normalizePrefix(resourcePrefix);
    final String appearance = normalizeAppearanceCode(appearanceCode);
    final String fileCode = codeMode == CodeMode.FULL ? appearance : appearance.substring(0, 1);
    final String offhandSuffix = slot == OverlaySlot.OFF_HAND_WEAPON ? "O" : "";
    final LayoutBuilder builder = new LayoutBuilder();
    switch (this) {
      case CHARACTER:
        buildCharacter(builder, prefix + fileCode + offhandSuffix, false, attackKind);
        break;
      case CHARACTER_OLD:
        buildCharacterOld(builder, prefix + fileCode, attackKind);
        break;
      case MONSTER:
        buildMonsterOverlay(builder, prefix, fileCode);
        break;
      case MONSTER_LAYERED_SPELL:
      case MONSTER_LAYERED:
      case MONSTER_ICEWIND:
        return checkedLayout(creatureFamily.createLayout(prefix + fileCode, false, 1, 1));
      default:
        throw new IllegalStateException("Unsupported equipment-overlay family: " + this);
    }
    return checkedLayout(builder.build());
  }

  public FamilyLayout createAvatarLayout(String resourcePrefix, boolean splitBams, WeaponType weaponType) {
    return createAvatarLayout(resourcePrefix, splitBams, getAttackKind(weaponType, null));
  }

  public FamilyLayout createAvatarLayout(String resourcePrefix, boolean splitBams, AttackKind attackKind) {
    if (attackKind == null) {
      throw new IllegalArgumentException("An equipment attack kind is required.");
    }
    final String prefix = normalizePrefix(resourcePrefix);
    final LayoutBuilder builder = new LayoutBuilder();
    switch (this) {
      case CHARACTER:
        buildCharacter(builder, prefix, splitBams, attackKind);
        break;
      case CHARACTER_OLD:
        buildCharacterOld(builder, prefix, attackKind);
        break;
      case MONSTER:
        return checkedLayout(CreatureAnimationFamily.MONSTER.createLayout(prefix, splitBams, 1, 1));
      case MONSTER_LAYERED_SPELL:
      case MONSTER_LAYERED:
      case MONSTER_ICEWIND:
        return checkedLayout(creatureFamily.createLayout(prefix, false, 1, 1));
      default:
        throw new IllegalStateException("Unsupported equipment-overlay family: " + this);
    }
    return checkedLayout(builder.build());
  }

  public AttackKind getAttackKind(WeaponType mainHand, WeaponType offhand) {
    EquipmentOverlayGenerator.validateLoadout(mainHand, offhand);
    if (offhand != null && !offhand.isShield()) {
      return AttackKind.TWO_WEAPON;
    }
    final AttackStyle style = mainHand.getAttackStyle();
    switch (style) {
      case ONE_HANDED:
        return AttackKind.ONE_HANDED;
      case TWO_HANDED:
        return AttackKind.TWO_HANDED;
      case BOW:
        return AttackKind.BOW;
      case CROSSBOW:
        return AttackKind.CROSSBOW;
      case SLING:
        return AttackKind.SLING;
      default:
        throw new IllegalArgumentException(mainHand + " cannot be used as main-hand equipment.");
    }
  }

  public void validateLoadout(WeaponType mainHand, WeaponType offhand) {
    EquipmentOverlayGenerator.validateLoadout(mainHand, offhand);
    if (offhand != null && this != CHARACTER && this != CHARACTER_OLD) {
      throw new IllegalArgumentException(label + " does not emit shield or left-handed weapon sprite segments.");
    }
    if (offhand != null && !offhand.isShield() && this != CHARACTER) {
      throw new IllegalArgumentException(label + " does not define two-weapon attack resources.");
    }
  }

  public String getFileCode(String appearanceCode) {
    final String normalized = normalizeAppearanceCode(appearanceCode);
    return codeMode == CodeMode.FULL ? normalized : normalized.substring(0, 1);
  }

  public String getActivationSummary(String appearanceCode) {
    final String normalized = normalizeAppearanceCode(appearanceCode);
    if (codeMode == CodeMode.FULL) {
      return "Equipped appearance " + normalized;
    }
    return "Equipped appearance beginning with " + normalized.substring(0, 1);
  }

  private static void buildMonsterOverlay(LayoutBuilder builder, String resref, String appearance) {
    for (final Map.Entry<String, List<OutputSlot>> entry
        : MonsterAnimationLayout.getOutputLayout(false).entrySet()) {
      final String fileName = resref + entry.getKey() + appearance + ".BAM";
      for (final OutputSlot slot : entry.getValue()) {
        builder.addBlock(fileName, slot.getCycleOffset(), slot.getSequence(), FULL_W);
      }
    }
  }

  private static void buildCharacter(LayoutBuilder builder, String prefix, boolean splitBams,
      AttackKind attackKind) {
    addCharacterAttacks(builder, prefix, attackKind, false);
    addCharacterCasting(builder, prefix, false);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.WALK,
        Sequence.WALK);
    addModernSequence(builder, prefix, splitBams,
        attackKind == AttackKind.TWO_HANDED
            ? org.infinity.resource.cre.decoder.util.Sequence.STANCE2
            : org.infinity.resource.cre.decoder.util.Sequence.STANCE,
        Sequence.STANCE);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.STAND,
        Sequence.STAND);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.GET_HIT,
        Sequence.GET_HIT);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.DIE,
        Sequence.DIE);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.TWITCH,
        Sequence.TWITCH);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.STAND2,
        Sequence.STAND);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.STAND3,
        Sequence.STAND);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.SLEEP,
        Sequence.SLEEP);
    addModernSequence(builder, prefix, splitBams, org.infinity.resource.cre.decoder.util.Sequence.SLEEP2,
        Sequence.SLEEP);
  }

  private static void addCharacterAttacks(LayoutBuilder builder, String prefix, AttackKind attackKind,
      boolean reduced) {
    final org.infinity.resource.cre.decoder.util.Sequence[] engineSequences;
    final Sequence[] sequences;
    if (attackKind == AttackKind.BOW) {
      engineSequences = new org.infinity.resource.cre.decoder.util.Sequence[] {
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_BOW };
      sequences = new Sequence[] { Sequence.ATTACK_3 };
    } else if (attackKind == AttackKind.CROSSBOW) {
      engineSequences = new org.infinity.resource.cre.decoder.util.Sequence[] {
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_CROSSBOW };
      sequences = new Sequence[] { Sequence.ATTACK_3 };
    } else if (attackKind == AttackKind.SLING) {
      engineSequences = new org.infinity.resource.cre.decoder.util.Sequence[] {
          reduced ? org.infinity.resource.cre.decoder.util.Sequence.ATTACK_SLASH_1H
              : org.infinity.resource.cre.decoder.util.Sequence.ATTACK_SLING };
      sequences = new Sequence[] { Sequence.ATTACK_1 };
    } else if (attackKind == AttackKind.TWO_WEAPON) {
      if (reduced) {
        throw new IllegalArgumentException("character_old does not define two-weapon attack resources.");
      }
      engineSequences = new org.infinity.resource.cre.decoder.util.Sequence[] {
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_2WEAPONS1,
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_2WEAPONS2 };
      sequences = new Sequence[] { Sequence.ATTACK_1, Sequence.ATTACK_2 };
    } else if (attackKind == AttackKind.TWO_HANDED) {
      engineSequences = new org.infinity.resource.cre.decoder.util.Sequence[] {
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_SLASH_2H,
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_BACKSLASH_2H,
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_JAB_2H };
      sequences = new Sequence[] { Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3 };
    } else {
      engineSequences = new org.infinity.resource.cre.decoder.util.Sequence[] {
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_SLASH_1H,
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_BACKSLASH_1H,
          org.infinity.resource.cre.decoder.util.Sequence.ATTACK_JAB_1H };
      sequences = new Sequence[] { Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3 };
    }
    for (int i = 0; i < engineSequences.length; i++) {
      if (reduced) {
        addLegacySequence(builder, prefix, engineSequences[i], sequences[i]);
      } else {
        addModernSequence(builder, prefix, false, engineSequences[i], sequences[i]);
      }
    }
  }

  private static void addCharacterCasting(LayoutBuilder builder, String prefix, boolean reduced) {
    final org.infinity.resource.cre.decoder.util.Sequence[] conjure = {
        org.infinity.resource.cre.decoder.util.Sequence.SPELL,
        org.infinity.resource.cre.decoder.util.Sequence.SPELL2,
        org.infinity.resource.cre.decoder.util.Sequence.SPELL3,
        org.infinity.resource.cre.decoder.util.Sequence.SPELL4
    };
    final org.infinity.resource.cre.decoder.util.Sequence[] cast = {
        org.infinity.resource.cre.decoder.util.Sequence.CAST,
        org.infinity.resource.cre.decoder.util.Sequence.CAST2,
        org.infinity.resource.cre.decoder.util.Sequence.CAST3,
        org.infinity.resource.cre.decoder.util.Sequence.CAST4
    };
    for (int variant = 0; variant < conjure.length; variant++) {
      if (reduced) {
        addLegacySequence(builder, prefix, conjure[variant], Sequence.CONJURE);
        addLegacySequence(builder, prefix, cast[variant], Sequence.CAST);
      } else {
        addModernSequence(builder, prefix, false, conjure[variant], Sequence.CONJURE);
        addModernSequence(builder, prefix, false, cast[variant], Sequence.CAST);
      }
    }
  }

  private static void buildCharacterOld(LayoutBuilder builder, String prefix, AttackKind attackKind) {
    addCharacterAttacks(builder, prefix, attackKind, true);
    addCharacterCasting(builder, prefix, true);
    addLegacySequence(builder, prefix, org.infinity.resource.cre.decoder.util.Sequence.WALK, Sequence.WALK);
    addLegacySequence(builder, prefix,
        attackKind == AttackKind.TWO_HANDED
            ? org.infinity.resource.cre.decoder.util.Sequence.STANCE2
            : org.infinity.resource.cre.decoder.util.Sequence.STANCE,
        Sequence.STANCE);
    addLegacySequence(builder, prefix, org.infinity.resource.cre.decoder.util.Sequence.STAND, Sequence.STAND);
    addLegacySequence(builder, prefix, org.infinity.resource.cre.decoder.util.Sequence.STAND2, Sequence.STAND);
    addLegacySequence(builder, prefix, org.infinity.resource.cre.decoder.util.Sequence.GET_HIT, Sequence.GET_HIT);
    addLegacySequence(builder, prefix, org.infinity.resource.cre.decoder.util.Sequence.DIE, Sequence.DIE);
    addLegacySequence(builder, prefix, org.infinity.resource.cre.decoder.util.Sequence.TWITCH, Sequence.TWITCH);
    final Couple<String, Integer> walkExtra = CharacterOldDecoder.getAdditionalWalkSequence();
    final String walkSuffix = SegmentDef.fixBehaviorSuffix(walkExtra.getValue0());
    builder.addBlock(prefix + walkSuffix + ".BAM", walkExtra.getValue1(), Sequence.WALK, WALK_EXTRA_W);
    builder.addBlock(prefix + walkSuffix + "E.BAM", walkExtra.getValue1() + REDUCED_W.length,
        Sequence.WALK, WALK_EXTRA_E);
  }

  private static void addModernSequence(LayoutBuilder builder, String prefix, boolean splitBams,
      org.infinity.resource.cre.decoder.util.Sequence engineSequence, Sequence sequence) {
    final Couple<String, Integer> location = CharacterDecoder.getSequenceMap(splitBams).get(engineSequence);
    if (location == null) {
      throw new IllegalArgumentException("The character decoder has no resource mapping for " + engineSequence + ".");
    }
    final String suffix = SegmentDef.fixBehaviorSuffix(location.getValue0());
    builder.addBlock(prefix + suffix + ".BAM", location.getValue1(), sequence, FULL_W);
  }

  private static void addLegacySequence(LayoutBuilder builder, String prefix,
      org.infinity.resource.cre.decoder.util.Sequence engineSequence, Sequence sequence) {
    final Couple<String, Integer> location = CharacterOldDecoder.getSequenceMap().get(engineSequence);
    if (location == null) {
      throw new IllegalArgumentException("The character_old decoder has no resource mapping for "
          + engineSequence + ".");
    }
    final String suffix = SegmentDef.fixBehaviorSuffix(location.getValue0());
    addReducedPair(builder, prefix + suffix, location.getValue1(), sequence);
  }

  private static void addReducedPair(LayoutBuilder builder, String baseName, int cycleOffset, Sequence sequence) {
    builder.addBlock(baseName + ".BAM", cycleOffset, sequence, REDUCED_W);
    builder.addBlock(baseName + "E.BAM", cycleOffset + REDUCED_W.length, sequence, REDUCED_E);
  }

  private void validateLayer(WeaponType equipmentType, AttackKind attackKind, OverlaySlot slot) {
    if (equipmentType == null || attackKind == null || slot == null) {
      throw new IllegalArgumentException("Equipment type, attack kind and overlay slot are required.");
    }
    if (slot == OverlaySlot.MAIN_HAND && equipmentType.isShield()) {
      throw new IllegalArgumentException("A shield cannot be exported as a main-hand overlay.");
    }
    if (slot == OverlaySlot.SHIELD && !equipmentType.isShield()) {
      throw new IllegalArgumentException("The shield overlay slot requires a shield equipment type.");
    }
    if (slot == OverlaySlot.OFF_HAND_WEAPON && !equipmentType.isOneHandedMelee()) {
      throw new IllegalArgumentException("The off-hand weapon overlay requires a one-handed melee weapon.");
    }
    if (slot == OverlaySlot.MAIN_HAND) {
      final boolean validTwoWeapon =
          attackKind == AttackKind.TWO_WEAPON && equipmentType.isOneHandedMelee();
      final boolean validSingleWeapon =
          attackKind != AttackKind.TWO_WEAPON && attackKind == getAttackKind(equipmentType, null);
      if (!validTwoWeapon && !validSingleWeapon) {
        throw new IllegalArgumentException(equipmentType.getLabel() + " is incompatible with the "
            + attackKind.toString().toLowerCase(Locale.ENGLISH) + " attack layout.");
      }
    }
    if (slot == OverlaySlot.SHIELD && attackKind != AttackKind.ONE_HANDED
        && attackKind != AttackKind.SLING) {
      throw new IllegalArgumentException("Shield resources require a one-handed melee or sling attack layout.");
    }
    if (slot != OverlaySlot.MAIN_HAND && this != CHARACTER && this != CHARACTER_OLD) {
      throw new IllegalArgumentException(label + " has no decoder-backed off-hand overlay layout.");
    }
    if (slot == OverlaySlot.OFF_HAND_WEAPON && this != CHARACTER) {
      throw new IllegalArgumentException(label + " has no decoder-backed two-weapon layout.");
    }
    if (slot == OverlaySlot.OFF_HAND_WEAPON && attackKind != AttackKind.TWO_WEAPON) {
      throw new IllegalArgumentException("Off-hand weapon resources require the two-weapon attack layout.");
    }
    if (this == CHARACTER_OLD && attackKind == AttackKind.TWO_WEAPON) {
      throw new IllegalArgumentException("character_old explicitly forbids two-weapon attack sequences.");
    }
  }

  private static String getSlotLabel(OverlaySlot slot) {
    switch (slot) {
      case MAIN_HAND:
        return "weapon";
      case SHIELD:
        return "shield";
      case OFF_HAND_WEAPON:
        return "left-handed weapon";
      default:
        throw new IllegalStateException("Unsupported equipment overlay slot: " + slot);
    }
  }

  private static String normalizePrefix(String value) {
    final String normalized = normalizeOptional(value);
    if (!normalized.matches("[A-Z0-9_]{1,8}")) {
      throw new IllegalArgumentException("Overlay resource prefixes must contain 1-8 ASCII letters, digits or "
          + "underscores.");
    }
    return normalized;
  }

  private static String normalizeAppearanceCode(String value) {
    final String normalized = normalizeOptional(value);
    if (!normalized.matches("[A-Z0-9_]{2}")) {
      throw new IllegalArgumentException("Equipped appearance codes must contain exactly two ASCII characters.");
    }
    return normalized;
  }

  private static void validateResourceNames(FamilyLayout layout) {
    for (final String fileName : layout.getResources().keySet()) {
      if (!fileName.matches("(?i)^[A-Z0-9_]{1,8}\\.BAM$")) {
        throw new IllegalArgumentException(fileName + " exceeds the Infinity Engine eight-character BAM resref "
            + "limit or contains invalid characters.");
      }
    }
  }

  private static FamilyLayout checkedLayout(FamilyLayout layout) {
    validateResourceNames(layout);
    return layout;
  }

  private static void addPrefix(Set<String> prefixes, String value) {
    final String normalized = normalizeOptional(value);
    if (normalized.matches("[A-Z0-9_]{1,8}")) {
      prefixes.add(normalized);
    }
  }

  private static void addArmorPrefix(Set<String> prefixes, String baseResref, int armorCode) {
    final String normalized = normalizeOptional(baseResref);
    if (!normalized.isEmpty()) {
      addPrefix(prefixes, normalized + armorCode);
    }
  }

  private static String normalizeOptional(String value) {
    return value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
  }

  @Override
  public String toString() {
    return label;
  }
}
