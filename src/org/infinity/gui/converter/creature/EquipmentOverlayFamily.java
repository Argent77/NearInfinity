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

/**
 * Declarative weapon-overlay layouts for every Near Infinity decoder that emits weapon sprite segments.
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

  private enum AttackKind {
    ONE_HANDED,
    TWO_HANDED,
    BOW
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
    if (decoder == null || decoder.getAnimationType() != animationType) {
      throw new IllegalArgumentException("The decoder does not belong to " + label + ".");
    }
    final String value;
    if (this == CHARACTER || this == CHARACTER_OLD) {
      value = ((CharacterBaseDecoder) decoder).getHeightCode();
    } else if (this == MONSTER && monsterResref != null && !monsterResref.trim().isEmpty()) {
      value = monsterResref;
    } else {
      value = decoder.getAnimationResref();
    }
    final String normalized = normalizePrefix(value);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " does not define a usable weapon-overlay resource prefix.");
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
    if (decoder == null || decoder.getAnimationType() != animationType) {
      throw new IllegalArgumentException("The selected reference does not use " + label + ".");
    }
    if (this == CHARACTER_OLD && ((CharacterOldDecoder) decoder).isWeaponsHidden()) {
      throw new IllegalArgumentException("The selected character_old definition suppresses weapon overlays.");
    }
    if ((this == CHARACTER || this == CHARACTER_OLD)
        && normalizeOptional(((CharacterBaseDecoder) decoder).getHeightCode()).isEmpty()) {
      throw new IllegalArgumentException("The selected " + label + " definition has no weapon height code.");
    }
    if ((this == MONSTER_LAYERED || this == MONSTER_LAYERED_SPELL)
        && getRequiredSourceLayerCode(decoder, targetWeapon).isEmpty()) {
      throw new IllegalArgumentException("The selected " + label + " definition has no "
          + (targetWeapon.isTwoHanded() ? "two-handed" : "one-handed") + " weapon-overlay prefix.");
    }
  }

  public FamilyLayout createOverlayLayout(String resourcePrefix, String appearanceCode, WeaponType weaponType) {
    final String prefix = normalizePrefix(resourcePrefix);
    final String appearance = normalizeAppearanceCode(appearanceCode);
    final String fileCode = codeMode == CodeMode.FULL ? appearance : appearance.substring(0, 1);
    final LayoutBuilder builder = new LayoutBuilder();
    switch (this) {
      case CHARACTER:
        buildCharacter(builder, prefix + fileCode, false, weaponType);
        break;
      case CHARACTER_OLD:
        buildCharacterOld(builder, prefix + fileCode, weaponType);
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
    final String prefix = normalizePrefix(resourcePrefix);
    final LayoutBuilder builder = new LayoutBuilder();
    switch (this) {
      case CHARACTER:
        buildCharacter(builder, prefix, splitBams, weaponType);
        break;
      case CHARACTER_OLD:
        buildCharacterOld(builder, prefix, weaponType);
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
      WeaponType weaponType) {
    addCharacterAttacks(builder, prefix, weaponType, false);
    addCharacterCasting(builder, prefix, false);
    final AttackKind attackKind = getAttackKind(weaponType);
    if (!splitBams) {
      addCharacterMisc(builder, prefix + "G1.BAM", attackKind);
    } else {
      builder.addBlock(prefix + (attackKind == AttackKind.TWO_HANDED ? "G13.BAM" : "G1.BAM"),
          attackKind == AttackKind.TWO_HANDED ? 27 : 9, Sequence.STANCE, FULL_W);
      builder.addBlock(prefix + "G11.BAM", 0, Sequence.WALK, FULL_W);
      builder.addBlock(prefix + "G12.BAM", 18, Sequence.STAND, FULL_W);
      builder.addBlock(prefix + "G15.BAM", 36, Sequence.GET_HIT, FULL_W);
      builder.addBlock(prefix + "G15.BAM", 45, Sequence.DIE, FULL_W);
      builder.addBlock(prefix + "G16.BAM", 54, Sequence.TWITCH, FULL_W);
      builder.addBlock(prefix + "G17.BAM", 63, Sequence.STAND, FULL_W);
      builder.addBlock(prefix + "G18.BAM", 72, Sequence.STAND, FULL_W);
      builder.addBlock(prefix + "G19.BAM", 81, Sequence.SLEEP, FULL_W);
      builder.addBlock(prefix + "G19.BAM", 90, Sequence.SLEEP, FULL_W);
    }
  }

  private static void addCharacterMisc(LayoutBuilder builder, String fileName, AttackKind attackKind) {
    builder.addBlock(fileName, 0, Sequence.WALK, FULL_W);
    builder.addBlock(fileName, attackKind == AttackKind.TWO_HANDED ? 27 : 9, Sequence.STANCE, FULL_W);
    builder.addBlock(fileName, 18, Sequence.STAND, FULL_W);
    builder.addBlock(fileName, 36, Sequence.GET_HIT, FULL_W);
    builder.addBlock(fileName, 45, Sequence.DIE, FULL_W);
    builder.addBlock(fileName, 54, Sequence.TWITCH, FULL_W);
    builder.addBlock(fileName, 63, Sequence.STAND, FULL_W);
    builder.addBlock(fileName, 72, Sequence.STAND, FULL_W);
    builder.addBlock(fileName, 81, Sequence.SLEEP, FULL_W);
    builder.addBlock(fileName, 90, Sequence.SLEEP, FULL_W);
  }

  private static void addCharacterAttacks(LayoutBuilder builder, String prefix, WeaponType weaponType,
      boolean reduced) {
    final AttackKind attackKind = getAttackKind(weaponType);
    final String[] codes;
    final Sequence[] sequences;
    if (attackKind == AttackKind.BOW) {
      codes = new String[] { "SA" };
      sequences = new Sequence[] { Sequence.ATTACK_3 };
    } else if (attackKind == AttackKind.TWO_HANDED) {
      codes = new String[] { "A2", "A4", "A6" };
      sequences = new Sequence[] { Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3 };
    } else {
      codes = new String[] { "A1", "A3", "A5" };
      sequences = new Sequence[] { Sequence.ATTACK_1, Sequence.ATTACK_2, Sequence.ATTACK_3 };
    }
    for (int i = 0; i < codes.length; i++) {
      if (reduced) {
        addReducedPair(builder, prefix + codes[i], 0, sequences[i]);
      } else {
        builder.addBlock(prefix + codes[i] + ".BAM", 0, sequences[i], FULL_W);
      }
    }
  }

  private static void addCharacterCasting(LayoutBuilder builder, String prefix, boolean reduced) {
    for (int variant = 0; variant < 4; variant++) {
      if (reduced) {
        addReducedPair(builder, prefix + "CA", variant * 16, Sequence.CONJURE);
        addReducedPair(builder, prefix + "CA", variant * 16 + 8, Sequence.CAST);
      } else {
        builder.addBlock(prefix + "CA.BAM", variant * 18, Sequence.CONJURE, FULL_W);
        builder.addBlock(prefix + "CA.BAM", variant * 18 + 9, Sequence.CAST, FULL_W);
      }
    }
  }

  private static void buildCharacterOld(LayoutBuilder builder, String prefix, WeaponType weaponType) {
    addCharacterAttacks(builder, prefix, weaponType, true);
    addCharacterCasting(builder, prefix, true);
    final AttackKind attackKind = getAttackKind(weaponType);
    addReducedPair(builder, prefix + "G1", 0, Sequence.WALK);
    addReducedPair(builder, prefix + "G1", attackKind == AttackKind.TWO_HANDED ? 24 : 8, Sequence.STANCE);
    addReducedPair(builder, prefix + "G1", 16, Sequence.STAND);
    addReducedPair(builder, prefix + "G1", 32, Sequence.STAND);
    addReducedPair(builder, prefix + "G1", 40, Sequence.GET_HIT);
    addReducedPair(builder, prefix + "G1", 48, Sequence.DIE);
    addReducedPair(builder, prefix + "G1", 56, Sequence.TWITCH);
    builder.addBlock(prefix + "W2.BAM", 0, Sequence.WALK, WALK_EXTRA_W);
    builder.addBlock(prefix + "W2E.BAM", 5, Sequence.WALK, WALK_EXTRA_E);
  }

  private static void addReducedPair(LayoutBuilder builder, String baseName, int cycleOffset, Sequence sequence) {
    builder.addBlock(baseName + ".BAM", cycleOffset, sequence, REDUCED_W);
    builder.addBlock(baseName + "E.BAM", cycleOffset + REDUCED_W.length, sequence, REDUCED_E);
  }

  private static AttackKind getAttackKind(WeaponType weaponType) {
    if (weaponType == null) {
      throw new IllegalArgumentException("A target weapon is required.");
    }
    if (weaponType == WeaponType.BOW) {
      return AttackKind.BOW;
    }
    return weaponType.isTwoHanded() ? AttackKind.TWO_HANDED : AttackKind.ONE_HANDED;
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
