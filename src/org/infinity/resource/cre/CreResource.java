// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.infinity.NearInfinity;
import org.infinity.datatype.AnimateBitmap;
import org.infinity.datatype.Bitmap;
import org.infinity.datatype.ColorValue;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.HashBitmap;
import org.infinity.datatype.HexNumber;
import org.infinity.datatype.IdsBitmap;
import org.infinity.datatype.IdsFlag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.KitIdsBitmap;
import org.infinity.datatype.MultiNumber;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.UnsignDecNumber;
import org.infinity.datatype.UpdateEvent;
import org.infinity.datatype.UpdateListener;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.Effect;
import org.infinity.resource.Effect2;
import org.infinity.resource.HasAddRemovable;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.StructureFactory;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.gam.GamResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.SearchOptions;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapCache;
import org.infinity.util.IniMapEntry;
import org.infinity.util.IniMapSection;
import org.infinity.util.LongIntegerHashMap;
import org.infinity.util.Misc;
import org.infinity.util.StringTable;
import org.infinity.util.Table2da;
import org.infinity.util.io.StreamUtils;

/**
 * This resource describes a "creature". Creatures have several stats (some visible
 * through the game UI) which are generally mapped to {@link IdsMap IDS} or
 * {@link Table2da 2DA} files.
 * <p>
 * Planescape: Torment engine specific notes:
 * <ul>
 * <li>PST creature disguises are not stored as a field in the creature file, they
 * are held as a GLOBAL variable, named {@code 'appearance'}. A value of 1 equates
 * to zombie disguise, a value of 2 equates to dustman disguise.</li>
 * <li>Several fields for The Nameless One are generated dynamically, and as a result
 * changing fields in the relevant CRE file will have no effect.</li>
 * </ul>
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/cre_v1.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/cre_v1.htm</a>
 */
public final class CreResource extends AbstractStruct
  implements Resource, HasAddRemovable, AddRemovable, HasViewerTabs, ItemListener, UpdateListener
{
  // CHR-specific field labels
  public static final String CHR_NAME                         = "Character name";
  public static final String CHR_OFFSET_CRE                   = "CRE structure offset";
  public static final String CHR_CRE_SIZE                     = "CRE structure size";
  public static final String CHR_QUICK_WEAPON_SLOT_FMT        = "Quick weapon slot %d";
  public static final String CHR_QUICK_SHIELD_SLOT_FMT        = "Quick shield slot %d";
  public static final String CHR_QUICK_WEAPON_ABILITY_FMT     = "Quick weapon %d ability";
  public static final String CHR_QUICK_SHIELD_ABILITY_FMT     = "Quick shield %d ability";
  public static final String CHR_QUICK_SPELL_FMT              = "Quick spell %d";
  public static final String CHR_QUICK_SPELL_CLASS_FMT        = "Quick spell %d class";
  public static final String CHR_QUICK_ITEM_SLOT_FMT          = "Quick item slot %d";
  public static final String CHR_QUICK_ITEM_ABILITY_FMT       = "Quick item %d ability";
  public static final String CHR_QUICK_ABILITY_FMT            = "Quick ability %d";
  public static final String CHR_QUICK_SONG_FMT               = "Quick song %d";
  public static final String CHR_QUICK_BUTTON_FMT             = "Quick button %d";
  public static final String CHR_VOICE_SET_PREFIX             = "Voice set prefix";
  public static final String CHR_VOICE_SET                    = "Voice set";
  public static final String CHR_SIGNATURE_2                  = "Signature 2";
  public static final String CHR_VERSION_2                    = "Version 2";
  // CRE-specific field labels
  public static final String CRE_NAME                         = "Name";
  public static final String CRE_TOOLTIP                      = "Tooltip";
  public static final String CRE_FLAGS                        = "Flags";
  public static final String CRE_XP_VALUE                     = "XP value";
  public static final String CRE_XP                           = "XP/Power level";
  public static final String CRE_GOLD                         = "Gold";
  public static final String CRE_STATUS                       = "Status";
  public static final String CRE_HP_CURRENT                   = "Current HP";
  public static final String CRE_HP_MAX                       = "Maximum HP";
  public static final String CRE_ANIMATION                    = "Animation";
  public static final String CRE_COLOR_METAL                  = "Metal color";
  public static final String CRE_COLOR_MINOR                  = "Minor color";
  public static final String CRE_COLOR_MAJOR                  = "Major color";
  public static final String CRE_COLOR_SKIN                   = "Skin color";
  public static final String CRE_COLOR_LEATHER                = "Leather color";
  public static final String CRE_COLOR_ARMOR                  = "Armor color";
  public static final String CRE_COLOR_HAIR                   = "Hair color";
  public static final String CRE_EFFECT_VERSION               = "Effect version";
  public static final String CRE_PORTRAIT_SMALL               = "Small portrait";
  public static final String CRE_PORTRAIT_LARGE               = "Large portrait";
  public static final String CRE_REPUTATION                   = "Reputation";
  public static final String CRE_ARMOR_CLASS                  = "Armor class";
  public static final String CRE_AC_MOD_BLUDGEONING           = "Bludgeoning AC modifier";
  public static final String CRE_AC_MOD_CRUSHING              = "Crushing AC modifier";
  public static final String CRE_AC_MOD_MISSILE               = "Missile AC modifier";
  public static final String CRE_AC_MOD_PIERCING              = "Piercing AC modifier";
  public static final String CRE_AC_MOD_SLASHING              = "Slashing AC modifier";
  public static final String CRE_AC_NATURAL                   = "Natural AC";
  public static final String CRE_AC_EFFECTIVE                 = "Effective AC";
  public static final String CRE_THAC0                        = "THAC0";
  public static final String CRE_BASE_ATTACK_BONUS            = "Base attack bonus";
  public static final String CRE_ATTACKS_PER_ROUND            = "# attacks/round";
  public static final String CRE_SAVE_FORTITUDE               = "Fortitude save";
  public static final String CRE_SAVE_REFLEX                  = "Reflex save";
  public static final String CRE_SAVE_WILL                    = "Will save";
  public static final String CRE_SAVE_DEATH                   = "Save vs. death";
  public static final String CRE_SAVE_WAND                    = "Save vs. wand";
  public static final String CRE_SAVE_POLYMORPH               = "Save vs. polymorph";
  public static final String CRE_SAVE_BREATH                  = "Save vs. breath";
  public static final String CRE_SAVE_SPELL                   = "Save vs. spell";
  public static final String CRE_RESISTANCE_FIRE              = "Fire resistance";
  public static final String CRE_RESISTANCE_COLD              = "Cold resistance";
  public static final String CRE_RESISTANCE_ELECTRICITY       = "Electricity resistance";
  public static final String CRE_RESISTANCE_ACID              = "Acid resistance";
  public static final String CRE_RESISTANCE_SPELL             = "Spell resistance";
  public static final String CRE_RESISTANCE_MAGIC             = "Magic resistance";
  public static final String CRE_RESISTANCE_MAGIC_FIRE        = "Magic fire resistance";
  public static final String CRE_RESISTANCE_MAGIC_COLD        = "Magic cold resistance";
  public static final String CRE_RESISTANCE_SLASHING          = "Slashing resistance";
  public static final String CRE_RESISTANCE_BLUDGEONING       = "Bludgeoning resistance";
  public static final String CRE_RESISTANCE_CRUSHING          = "Crushing resistance";
  public static final String CRE_RESISTANCE_PIERCING          = "Piercing resistance";
  public static final String CRE_RESISTANCE_MISSILE           = "Missile resistance";
  public static final String CRE_RESISTANCE_MAGIC_DAMAGE      = "Magic damage resistance";
  public static final String CRE_FATIGUE                      = "Fatigue";
  public static final String CRE_INTOXICATION                 = "Intoxication";
  public static final String CRE_LUCK                         = "Luck";
  public static final String CRE_TURN_UNDEAD_LEVEL            = "Turn undead level";
  public static final String CRE_LEVELS_TOTAL                 = "Total level";
  public static final String CRE_LEVEL_BARBARIAN              = "Barbarian level";
  public static final String CRE_LEVEL_BARD                   = "Bard level";
  public static final String CRE_LEVEL_CLERIC                 = "Cleric level";
  public static final String CRE_LEVEL_DRUID                  = "Druid level";
  public static final String CRE_LEVEL_FIGHTER                = "Fighter level";
  public static final String CRE_LEVEL_MONK                   = "Monk level";
  public static final String CRE_LEVEL_PALADIN                = "Paladin level";
  public static final String CRE_LEVEL_RANGER                 = "Ranger level";
  public static final String CRE_LEVEL_ROGUE                  = "Rogue level";
  public static final String CRE_LEVEL_SORCERER               = "Sorcerer level";
  public static final String CRE_LEVEL_WIZARD                 = "Wizard level";
  public static final String CRE_SOUND_SLOT_FMT               = "Sound: %s";
  public static final String CRE_SOUND_SLOT_GENERIC           = "Soundset string";
  public static final String CRE_ENCHANTMENT_LEVEL            = "Enchantment level";
  public static final String CRE_FEATS_1                      = "Feats (1/3)";
  public static final String CRE_FEATS_2                      = "Feats (2/3)";
  public static final String CRE_FEATS_3                      = "Feats (3/3)";
  public static final String CRE_PROFICIENCY_LARGE_SWORD      = "Large sword proficiency";
  public static final String CRE_PROFICIENCY_SMALL_SWORD      = "Small sword proficiency";
  public static final String CRE_PROFICIENCY_BOW              = "Bow proficiency";
  public static final String CRE_PROFICIENCY_SPEAR            = "Spear proficiency";
  public static final String CRE_PROFICIENCY_BLUNT            = "Blunt proficiency";
  public static final String CRE_PROFICIENCY_SPIKED           = "Spiked proficiency";
  public static final String CRE_PROFICIENCY_AXE              = "Axe proficiency";
  public static final String CRE_PROFICIENCY_MISSILE          = "Missile proficiency";
  public static final String CRE_PROFICIENCY_FIST             = "Fist proficiency";
  public static final String CRE_PROFICIENCY_EDGED_WEAPON     = "Edged-weapon proficiency";
  public static final String CRE_PROFICIENCY_HAMMER           = "Hammer proficiency";
  public static final String CRE_PROFICIENCY_CLUB             = "Club proficiency";
  public static final String CRE_PROFICIENCY_GREATSWORD       = "Greatsword proficiency";
  public static final String CRE_PROFICIENCY_DAGGER           = "Dagger proficiency";
  public static final String CRE_PROFICIENCY_HALBERD          = "Halberd proficiency";
  public static final String CRE_PROFICIENCY_MACE             = "Mace proficiency";
  public static final String CRE_PROFICIENCY_FLAIL            = "Flail proficiency";
  public static final String CRE_PROFICIENCY_QUARTERSTAFF     = "Quarterstaff proficiency";
  public static final String CRE_PROFICIENCY_CROSSBOW         = "Crossbow proficiency";
  public static final String CRE_MW_BOW                       = "MW: Bow";
  public static final String CRE_SW_CROSSBOW                  = "SW: Crossbow";
  public static final String CRE_SW_MISSILE                   = "SW: Missile";
  public static final String CRE_MW_AXE                       = "MW: Axe";
  public static final String CRE_SW_MACE                      = "SW: Mace";
  public static final String CRE_MW_FLAIL                     = "MW: Flail";
  public static final String CRE_MW_POLEARM                   = "MW: Polearm";
  public static final String CRE_MW_HAMMER                    = "MW: Hammer";
  public static final String CRE_SW_QUARTERSTAFF              = "SW: Quarterstaff";
  public static final String CRE_MW_GREATSWORD                = "MW: Greatsword";
  public static final String CRE_MW_LARGE_SWORD               = "MW: Large sword";
  public static final String CRE_SW_SMALL_BLADE               = "SW: Small blade";
  public static final String CRE_TOUGHNESS                    = "Toughness";
  public static final String CRE_ARMORED_ARCANA               = "Armored arcana";
  public static final String CRE_CLEAVE                       = "Cleave";
  public static final String CRE_ARMOR_PROFICIENCY            = "Armor proficiency";
  public static final String CRE_SF_ENCHANTMENT               = "SF: Enchantment";
  public static final String CRE_SF_EVOCATION                 = "SF: Evocation";
  public static final String CRE_SF_NECROMANCY                = "SF: Necromancy";
  public static final String CRE_SF_TRANSMUTATION             = "SF: Transmutation";
  public static final String CRE_SPELL_PENETRATION            = "Spell penetration";
  public static final String CRE_EXTRA_RAGE                   = "Extra rage";
  public static final String CRE_EXTRA_WILD_SHAPE             = "Extra wild shape";
  public static final String CRE_EXTRA_SMITING                = "Extra smiting";
  public static final String CRE_EXTRA_TURNING                = "Extra turning";
  public static final String CRE_EW_BASTARD_SWORD             = "EW: Bastard sword";
  public static final String CRE_NIGHTMARE_MODE               = "Nightmare mode";
  public static final String CRE_TRANSLUCENCY                 = "Translucency";
  public static final String CRE_REPUTATION_MOD_KILLED        = "Reputation gain/loss when killed";
  public static final String CRE_REPUTATION_MOD_JOIN          = "Reputation gain/loss when joining party";
  public static final String CRE_REPUTATION_MOD_LEAVE         = "Reputation gain/loss when leaving party";
  public static final String CRE_ALCHEMY                      = "Alchemy";
  public static final String CRE_ANIMAL_EMPATHY               = "Animal empathy";
  public static final String CRE_BLUFF                        = "Bluff";
  public static final String CRE_CONCENTRATION                = "Concentration";
  public static final String CRE_DIPLOMACY                    = "Diplomacy";
  public static final String CRE_DISABLE_DEVICE               = "Disable device";
  public static final String CRE_HIDE                         = "Hide";
  public static final String CRE_HIDE_IN_SHADOWS              = "Hide in shadows";
  public static final String CRE_INTIMIDATE                   = "Intimidate";
  public static final String CRE_KNOWLEDGE_ARCANA             = "Knowledge (arcana)";
  public static final String CRE_DETECT_ILLUSION              = "Detect illusion";
  public static final String CRE_SET_TRAPS                    = "Set traps";
  public static final String CRE_MOVE_SILENTLY                = "Move silently";
  public static final String CRE_OPEN_LOCKS                   = "Open locks";
  public static final String CRE_PICK_POCKETS                 = "Pick pockets";
  public static final String CRE_FIND_TRAPS                   = "Find traps";
  public static final String CRE_SEARCH                       = "Search";
  public static final String CRE_LORE                         = "Lore";
  public static final String CRE_SPELLCRAFT                   = "Spellcraft";
  public static final String CRE_USE_MAGIC_DEVICE             = "Use magic device";
  public static final String CRE_WILDERNESS_LORE              = "Wilderness lore";
  public static final String CRE_CHALLENGE_RATING             = "Challenge rating";
  public static final String CRE_FAVORED_ENEMY_FMT            = "Favored enemy %d";
  public static final String CRE_RACIAL_ENEMY                 = "Racial enemy";
  public static final String CRE_SUBRACE                      = "Subrace";
  public static final String CRE_TRACKING                     = "Tracking";
  public static final String CRE_TARGET                       = "Target";
  public static final String CRE_LEVEL_FIRST_CLASS            = "Level first class";
  public static final String CRE_LEVEL_SECOND_CLASS           = "Level second class";
  public static final String CRE_LEVEL_THIRD_CLASS            = "Level third class";
  public static final String CRE_SEX                          = "Sex";
  public static final String CRE_STRENGTH                     = "Strength";
  public static final String CRE_STRENGTH_BONUS               = "Strength bonus";
  public static final String CRE_INTELLIGENCE                 = "Intelligence";
  public static final String CRE_WISDOM                       = "Wisdom";
  public static final String CRE_DEXTERITY                    = "Dexterity";
  public static final String CRE_CONSTITUTION                 = "Constitution";
  public static final String CRE_CHARISMA                     = "Charisma";
  public static final String CRE_MORALE                       = "Morale";
  public static final String CRE_MORALE_BREAK                 = "Morale break";
  public static final String CRE_MORALE_RECOVERY              = "Morale recovery";
  public static final String CRE_KIT                          = "Kit";
  public static final String CRE_DEITY                        = "Deity";
  public static final String CRE_MAGE_TYPE                    = "Mage type";
  public static final String CRE_SCRIPT_TEAM                  = "Team script";
  public static final String CRE_SCRIPT_SPECIAL_1             = "Special script 1";
  public static final String CRE_SCRIPT_OVERRIDE              = "Override script";
  public static final String CRE_SCRIPT_CLASS                 = "Class script";
  public static final String CRE_SCRIPT_RACE                  = "Race script";
  public static final String CRE_SCRIPT_GENERAL               = "General script";
  public static final String CRE_SCRIPT_DEFAULT               = "Default script";
  public static final String CRE_SCRIPT_SPECIAL_2             = "Special script 2";
  public static final String CRE_SCRIPT_COMBAT                = "Combat script";
  public static final String CRE_SCRIPT_SPECIAL_3             = "Special script 3";
  public static final String CRE_SCRIPT_MOVEMENT              = "Movement script";
  public static final String CRE_DEFAULT_VISIBILITY           = "Default visibility";
  public static final String CRE_OFFSET_OVERLAYS              = "Overlays offset";
  public static final String CRE_OVERLAYS_SIZE                = "Overlays size";
  public static final String CRE_XP_SECOND_CLASS              = "Thief XP";
  public static final String CRE_XP_THIRD_CLASS               = "Mage XP";
  public static final String CRE_GOOD_INC                     = "Good increment by";
  public static final String CRE_LAW_INC                      = "Law increment by";
  public static final String CRE_LADY_INC                     = "Lady increment by";
  public static final String CRE_MURDER_INC                   = "Murder increment by";
  public static final String CRE_CHARACTER_TYPE               = "Character type";
  public static final String CRE_DIALOG_ACTIVATION_RADIUS     = "Dialogue activation radius";
  public static final String CRE_COLLISION_RADIUS             = "Collision radius";
  public static final String CRE_SHIELD_FLAGS                 = "Shield flags";
  public static final String CRE_FIELD_OF_VISION              = "Field of vision";
  public static final String CRE_NUM_COLORS                   = "# colors";
  public static final String CRE_COLOR_FMT                    = "Color %d";
  public static final String CRE_COLOR_PLACEMENT_FMT          = "Color %d placement";
  public static final String CRE_SPECIES                      = "Species";
  public static final String CRE_TEAM                         = "Team";
  public static final String CRE_FACTION                      = "Faction";
  public static final String CRE_SET_EXTRA_DEATH_VAR          = "Set extra death variable?";
  public static final String CRE_INCREMENT_KILL_COUNT         = "Increment kill count?";
  public static final String CRE_INTERNAL_FMT                 = "Internal %d";
  public static final String CRE_DEATH_VAR_SET                = "Death variable (set)";
  public static final String CRE_DEATH_VAR_INC                = "Death variable (increment)";
  public static final String CRE_LOCATION_SAVED               = "Location saved?";
  public static final String CRE_SAVED_LOCATION_X             = "Saved location: X";
  public static final String CRE_SAVED_LOCATION_Y             = "Saved location: Y";
  public static final String CRE_SAVED_ORIENTATION            = "Saved orientation";
  public static final String CRE_FADE_AMOUNT                  = "Fade amount";
  public static final String CRE_FADE_SPEED                   = "Fade speed";
  public static final String CRE_ATTRIBUTES                   = "Attributes";
  public static final String CRE_VISIBILITY                   = "Visibility";
  public static final String CRE_SKILL_POINTS_UNUSED          = "Unused skill points";
  public static final String CRE_PROFICIENCIES_UNSPENT        = "Unspent proficiencies";
  public static final String CRE_NUM_ITEM_SLOTS               = "# item slots";
  public static final String CRE_ALLEGIANCE                   = "Allegiance";
  public static final String CRE_GENERAL                      = "General";
  public static final String CRE_RACE                         = "Race";
  public static final String CRE_CLASS                        = "Class";
  public static final String CRE_SPECIFICS                    = "Specifics";
  public static final String CRE_GENDER                       = "Gender";
  public static final String CRE_OBJECT_FMT                   = "Object spec %d";
  public static final String CRE_ALIGNMENT                    = "Alignment";
  public static final String CRE_IDENTIFIER_GLOBAL            = "Global identifier";
  public static final String CRE_IDENTIFIER_LOCAL             = "Local identifier";
  public static final String CRE_SCRIPT_NAME                  = "Script name";
  public static final String CRE_CLASS_2                      = "Class 2";
  public static final String CRE_CLASS_MASK                   = "Class mask";
  public static final String CRE_OFFSET_SPELLS_BARD_FMT       = "Bard spells %d offset";
  public static final String CRE_OFFSET_SPELLS_CLERIC_FMT     = "Cleric spells %d offset";
  public static final String CRE_OFFSET_SPELLS_DRUID_FMT      = "Druid spells %d offset";
  public static final String CRE_OFFSET_SPELLS_PALADIN_FMT    = "Paladin spells %d offset";
  public static final String CRE_OFFSET_SPELLS_RANGER_FMT     = "Ranger spells %d offset";
  public static final String CRE_OFFSET_SPELLS_SORCERER_FMT   = "Sorcerer spells %d offset";
  public static final String CRE_OFFSET_SPELLS_WIZARD_FMT     = "Wizard spells %d offset";
  public static final String CRE_OFFSET_SPELLS_DOMAIN_FMT     = "Domain spells %d offset";
  public static final String CRE_OFFSET_ABILITIES             = "Abilities offset";
  public static final String CRE_NUM_SPELLS_BARD_FMT          = "Bard spells %d count";
  public static final String CRE_NUM_SPELLS_CLERIC_FMT        = "Cleric spells %d count";
  public static final String CRE_NUM_SPELLS_DRUID_FMT         = "Druid spells %d count";
  public static final String CRE_NUM_SPELLS_PALADIN_FMT       = "Paladin spells %d count";
  public static final String CRE_NUM_SPELLS_RANGER_FMT        = "Ranger spells %d count";
  public static final String CRE_NUM_SPELLS_SORCERER_FMT      = "Sorcerer spells %d count";
  public static final String CRE_NUM_SPELLS_WIZARD_FMT        = "Wizard spells %d count";
  public static final String CRE_NUM_SPELLS_DOMAIN_FMT        = "Domain spells %d count";
  public static final String CRE_NUM_ABILITIES                = "Abilities count";
  public static final String CRE_SPELLS_BARD_FMT              = "Bard spells %d";
  public static final String CRE_SPELLS_CLERIC_FMT            = "Cleric spells %d";
  public static final String CRE_SPELLS_DRUID_FMT             = "Druid spells %d";
  public static final String CRE_SPELLS_PALADIN_FMT           = "Paladin spells %d";
  public static final String CRE_SPELLS_RANGER_FMT            = "Ranger spells %d";
  public static final String CRE_SPELLS_SORCERER_FMT          = "Sorcerer spells %d";
  public static final String CRE_SPELLS_WIZARD_FMT            = "Wizard spells %d";
  public static final String CRE_SPELLS_DOMAIN_FMT            = "Domain spells %d";
  public static final String CRE_ABILITIES                    = "Abilities";
  public static final String CRE_OFFSET_KNOWN_SPELLS          = "Known spells offset";
  public static final String CRE_OFFSET_MEMORIZATION_INFO     = "Memorization info offset";
  public static final String CRE_OFFSET_MEMORIZED_SPELLS      = "Memorized spells offset";
  public static final String CRE_OFFSET_SONGS                 = "Songs offset";
  public static final String CRE_OFFSET_SHAPES                = "Shapes offset";
  public static final String CRE_OFFSET_ITEM_SLOTS            = "Item slots offset";
  public static final String CRE_OFFSET_ITEMS                 = "Items offset";
  public static final String CRE_OFFSET_EFFECTS               = "Effects offset";
  public static final String CRE_NUM_KNOWN_SPELLS             = "# known spells";
  public static final String CRE_NUM_MEMORIZATION_INFO        = "# memorization info";
  public static final String CRE_NUM_MEMORIZED_SPELLS         = "# memorized spells";
  public static final String CRE_NUM_SONGS                    = "Songs count";
  public static final String CRE_NUM_SHAPES                   = "Shapes count";
  public static final String CRE_NUM_ITEMS                    = "# items";
  public static final String CRE_NUM_EFFECTS                  = "# effects";
  public static final String CRE_SONGS                        = "Songs";
  public static final String CRE_SHAPES                       = "Shapes";
  public static final String CRE_DIALOG                       = "Dialogue";
  public static final String CRE_ITEM_SLOT_HELMET             = "Helmet";
  public static final String CRE_ITEM_SLOT_ARMOR              = "Armor";
  public static final String CRE_ITEM_SLOT_SHIELD             = "Shield";
  public static final String CRE_ITEM_SLOT_GAUNTLETS          = "Gauntlets";
  public static final String CRE_ITEM_SLOT_GLOVES             = "Gloves";
  public static final String CRE_ITEM_SLOT_LEFT_RING          = "Left ring";
  public static final String CRE_ITEM_SLOT_RIGHT_RING         = "Right ring";
  public static final String CRE_ITEM_SLOT_AMULET             = "Amulet";
  public static final String CRE_ITEM_SLOT_BELT               = "Belt";
  public static final String CRE_ITEM_SLOT_BOOTS              = "Boots";
  public static final String CRE_ITEM_SLOT_WEAPON_FMT         = "Weapon %d";
  public static final String CRE_ITEM_SLOT_SHIELD_FMT         = "Shield %d";
  public static final String CRE_ITEM_SLOT_QUIVER_FMT         = "Quiver %d";
  public static final String CRE_ITEM_SLOT_CLOAK              = "Cloak";
  public static final String CRE_ITEM_SLOT_RIGHT_EARRING      = "Right earring/Eye";
  public static final String CRE_ITEM_SLOT_CHEST              = "Chest";
  public static final String CRE_ITEM_SLOT_LEFT_TATTOO        = "Left tattoo";
  public static final String CRE_ITEM_SLOT_HAND               = "Hand";
  public static final String CRE_ITEM_SLOT_LEFT_EARRING       = "Left earring";
  public static final String CRE_ITEM_SLOT_RIGHT_TATTOO_LOWER = "Right tattoo (lower)";
  public static final String CRE_ITEM_SLOT_WRIST              = "Wrist";
  public static final String CRE_ITEM_SLOT_RIGHT_TATTOO_UPPER = "Right tattoo (upper)";
  public static final String CRE_ITEM_SLOT_QUICK_FMT          = "Quick item %d";
  public static final String CRE_ITEM_SLOT_INVENTORY_FMT      = "Inventory %d";
  public static final String CRE_ITEM_SLOT_UNUSED_FMT         = "Unused %d";
  public static final String CRE_ITEM_SLOT_MAGIC_WEAPON       = "Magically created weapon";
  public static final String CRE_SELECTED_WEAPON_SLOT         = "Weapon slot selected";
  public static final String CRE_SELECTED_WEAPON_ABILITY      = "Weapon ability selected";

  private static final LongIntegerHashMap<String> m_magetype = new LongIntegerHashMap<String>();
  private static final LongIntegerHashMap<String> m_colorPlacement = new LongIntegerHashMap<String>();
  public static final String[] s_flag = {
    "No flags set", "Identified", "No corpse", "Permanent corpse",
    "Original class: Fighter", "Original class: Mage", "Original class: Cleric", "Original class: Thief",
    "Original class: Druid", "Original class: Ranger", "Fallen paladin", "Fallen ranger",
    "Export allowed", "Hide status", "Large creature", "Moving between areas", "Been in party",
    "Holding item", "Reset bit 16", "", "", "EE: No exploding death", "", "EE: Ignore nightmare mode",
    "EE: No tooltip", "Allegiance tracking", "General tracking", "Race tracking", "Class tracking",
    "Specifics tracking", "Gender tracking", "Alignment tracking", "Uninterruptible"};
  public static final String[] s_feats1 = {
    "No feats selected", "Aegis of rime", "Ambidexterity", "Aqua mortis", "Armor proficiency", "Armored arcana",
    "Arterial strike", "Blind fight", "Bullheaded", "Cleave", "Combat casting", "Courteous magocracy", "Crippling strike",
    "Dash", "Deflect arrows", "Dirty fighting", "Discipline", "Dodge", "Envenom weapon", "Exotic bastard",
    "Expertise", "Extra rage", "Extra shapeshifting", "Extra smiting", "Extra turning", "Fiendslayer",
    "Forester", "Great fortitude", "Hamstring", "Heretic's bane", "Heroic inspiration", "Improved critical",
    "Improved evasion"};
  public static final String[] s_feats2 = {
    "No feats selected", "Improved initiative", "Improved turning", "Iron will", "Lightning reflexes",
    "Lingering song", "Luck of heroes", "Martial axe", "Martial bow", "Martial flail", "Martial greatsword",
    "Martial hammer", "Martial large sword", "Martial polearm", "Maximized attacks", "Mercantile background",
    "Power attack", "Precise shot", "Rapid shot", "Resist poison", "Scion of storms", "Shield proficiency",
    "Simple crossbow", "Simple mace", "Simple missile", "Simple quarterstaff", "Simple small blade",
    "Slippery mind", "Snake blood", "Spell focus enchantment", "Spell focus evocation", "Spell focus necromancy",
    "Spell focus transmutation"};
  public static final String[] s_feats3 = {
    "No feats selected", "Spell penetration", "Spirit of flame", "Strong back", "Stunning fist",
    "Subvocal casting",
    "Toughness", "Two-weapon fighting", "Weapon finesse", "Wild shape boar", "Wild shape panther",
    "Wild shape shambler"};
  public static final String[] s_subraces = {
      "Pureblood",
      "Aamimar/Drow/Gold dwarf/Strongheart halfling/Deep gnome",
      "Tiefling/Wild elf/Gray dwarf/Ghostwise halfling"};
  public static final String[] s_attributes_pst = {
    "No flags set", "Auto-calc marker rect", "Translucent", "", "", "Track script name", "Increment kill count",
    "Script name only", "Track faction death", "Track team death", "Invulnerable",
    "Modify good on death", "Modify law on death", "Modify lady on death", "Modify murder on death",
    "No dialogue turn", "Call for help", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "Death globals set (internal)", "Area supplemental"};
  public static final String[] s_attributes_iwd2 = {"No flags set", "Mental fortitude", "Critical hit immunity",
                                                    "Cannot be paladin", "Cannot be monk"};
  public static final String[] s_attacks = {"0", "1", "2", "3", "4", "5", "1/2", "3/2", "5/2", "7/2", "9/2"};
  public static final String[] s_noyes = {"No", "Yes"};
  public static final String[] s_visible = {"Shown", "Hidden"};
  public static final String[] s_profLabels = {"Active class", "Original class"};
  public static final String[] s_effversion = {"Version 1", "Version 2"};

  static
  {
    m_magetype.put((long)0x0000, "None");
    m_magetype.put((long)0x0040, "Abjurer");
    m_magetype.put((long)0x0080, "Conjurer");
    m_magetype.put((long)0x0100, "Diviner");
    m_magetype.put((long)0x0200, "Enchanter");
    m_magetype.put((long)0x0400, "Illusionist");
    m_magetype.put((long)0x0800, "Invoker");
    m_magetype.put((long)0x1000, "Necromancer");
    m_magetype.put((long)0x2000, "Transmuter");
    m_magetype.put((long)0x4000, "Generalist");

    m_colorPlacement.put((long)0x80, "Metal");
    m_colorPlacement.put((long)0x81, "Metal (hologram)");
    m_colorPlacement.put((long)0x82, "Metal (pulsate)");
    m_colorPlacement.put((long)0x83, "Metal (hologram/pulsate)");
    m_colorPlacement.put((long)0x90, "Minor cloth");
    m_colorPlacement.put((long)0x91, "Minor cloth (hologram)");
    m_colorPlacement.put((long)0x92, "Minor cloth (pulsate)");
    m_colorPlacement.put((long)0x93, "Minor cloth (hologram/pulsate)");
    m_colorPlacement.put((long)0xA0, "Main cloth");
    m_colorPlacement.put((long)0xA1, "Main cloth (hologram)");
    m_colorPlacement.put((long)0xA2, "Main cloth (pulsate)");
    m_colorPlacement.put((long)0xA3, "Main cloth (hologram/pulsate)");
    m_colorPlacement.put((long)0xB0, "Skin");
    m_colorPlacement.put((long)0xB1, "Skin (hologram)");
    m_colorPlacement.put((long)0xB2, "Skin (pulsate)");
    m_colorPlacement.put((long)0xB3, "Skin (hologram/pulsate)");
    m_colorPlacement.put((long)0xC0, "Leather");
    m_colorPlacement.put((long)0xC1, "Leather (hologram)");
    m_colorPlacement.put((long)0xC2, "Leather (pulsate)");
    m_colorPlacement.put((long)0xC3, "Leather (hologram/pulsate)");
    m_colorPlacement.put((long)0xD0, "Armor");
    m_colorPlacement.put((long)0xD1, "Armor (hologram)");
    m_colorPlacement.put((long)0xD2, "Armor (pulsate)");
    m_colorPlacement.put((long)0xD3, "Armor (hologram/pulsate)");
    m_colorPlacement.put((long)0xE0, "Hair");
    m_colorPlacement.put((long)0xE1, "Hair (hologram)");
    m_colorPlacement.put((long)0xE2, "Hair (pulsate)");
    m_colorPlacement.put((long)0xE3, "Hair (hologram/pulsate)");
    m_colorPlacement.put((long)0x00, "Not used");
  }

  private boolean isChr;
  private JMenuItem miExport, miConvert;
  private ButtonPopupMenu bExport;
  private StructHexViewer hexViewer;
  private Boolean hasRawTab;

  public static void addScriptName(Map<String, Set<ResourceEntry>> scriptNames,
                                   ResourceEntry entry)
  {
    try {
      ByteBuffer buffer = entry.getResourceBuffer();
      String signature = StreamUtils.readString(buffer, 0, 4);
      String scriptName = "";
      int offset = 0;

      // CHR header is ignored
      if (signature.equalsIgnoreCase("CHR ")) {
        String version = StreamUtils.readString(buffer, 4, 4);
        if (version.equalsIgnoreCase("V2.2")) {
          offset += 0x224;
        } else {
          offset += 0x64;
        }
        signature = StreamUtils.readString(buffer, offset + 0, 4);
      }

      if (signature.equalsIgnoreCase("CRE ")) {
        String version = StreamUtils.readString(buffer, offset + 4, 4);
        if (version.equalsIgnoreCase("V1.0")) {
          scriptName = StreamUtils.readString(buffer, offset + 640, 32);
        } else if (version.equalsIgnoreCase("V1.1") || version.equalsIgnoreCase("V1.2")) {
          scriptName = StreamUtils.readString(buffer, offset + 804, 32);
        } else if (version.equalsIgnoreCase("V2.2")) {
          scriptName = StreamUtils.readString(buffer, offset + 916, 32);
        } else if (version.equalsIgnoreCase("V9.0")) {
          scriptName = StreamUtils.readString(buffer, offset + 744, 32);
        }
        if (scriptName.equals("") || scriptName.equalsIgnoreCase("None")) {
          return;
        // Apparently script name is the only thing that matters
  //        scriptName = entry.toString().substring(0, entry.toString().length() - 4);
        } else {
          scriptName = scriptName.toLowerCase(Locale.ENGLISH).replaceAll(" ", "");
          if (scriptNames.containsKey(scriptName)) {
            synchronized (scriptNames) {
              Set<ResourceEntry> entries = scriptNames.get(scriptName);
              entries.add(entry);
            }
          } else {
            Set<ResourceEntry> entries = new HashSet<ResourceEntry>();
            entries.add(entry);
            synchronized (scriptNames) {
              scriptNames.put(scriptName, entries);
            }
          }
        }
      }
    } catch (Exception e) {}
  }

  private static void adjustEntryOffsets(AbstractStruct struct, int amount)
  {
    for (int i = 0; i < struct.getFieldCount(); i++) {
      StructEntry structEntry = struct.getField(i);
      structEntry.setOffset(structEntry.getOffset() + amount);
      if (structEntry instanceof AbstractStruct)
        adjustEntryOffsets((AbstractStruct)structEntry, amount);
    }
  }

  public static void convertCHRtoCRE(ResourceEntry resourceEntry)
  {
    if (!resourceEntry.getExtension().equalsIgnoreCase("CHR")) {
      return;
    }
    Path path = ResourceFactory.getExportFileDialog(NearInfinity.getInstance(),
                                                    resourceEntry.toString().replace(".CHR", ".CRE"),
                                                    false);
    if (path != null) {
      try {
        CreResource crefile = (CreResource)ResourceFactory.getResource(resourceEntry);
        while (!crefile.getField(0).toString().equals("CRE ")) {
          crefile.removeField(0);
        }
        convertToSemiStandard(crefile);
        try (OutputStream os = StreamUtils.getOutputStream(path, true)) {
          crefile.write(os);
        }
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "File saved to " + path,
                                      "Conversion complete", JOptionPane.INFORMATION_MESSAGE);
      } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Errors during conversion",
                                      "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private static void convertToSemiStandard(CreResource crefile)
  {
    if (!crefile.getField(1).toString().equals("V1.0")) {
      System.err.println("Conversion to semi-standard aborted: Unsupported CRE version");
      return;
    }

    // Order:
    //  KnownSpell
    //  SpellMemorizationInfo
    //  MemorizedSpell
    //  Effects
    //  Items
    //  ItemSlots

    // Adjust offsets first - Size of CHR header = 0x64
    adjustEntryOffsets(crefile, -0x64);

    SectionOffset knownspells_offset = (SectionOffset)crefile.getAttribute(CRE_OFFSET_KNOWN_SPELLS);
    SectionOffset memspellinfo_offset = (SectionOffset)crefile.getAttribute(CRE_OFFSET_MEMORIZATION_INFO);
    SectionOffset memspells_offset = (SectionOffset)crefile.getAttribute(CRE_OFFSET_MEMORIZED_SPELLS);
    SectionOffset itemslots_offset = (SectionOffset)crefile.getAttribute(CRE_OFFSET_ITEM_SLOTS);
    SectionOffset items_offset = (SectionOffset)crefile.getAttribute(CRE_OFFSET_ITEMS);
    SectionOffset effects_offset = (SectionOffset)crefile.getAttribute(CRE_OFFSET_EFFECTS);

    int indexStructs = crefile.getIndexOf(effects_offset) + 3; // Start of non-permanent section
    List<StructEntry> newlist = new ArrayList<StructEntry>(crefile.getFieldCount());
    for (int i = 0; i < indexStructs; i++)
      newlist.add(crefile.getField(i));

    int offsetStructs = 0x2d4;
    knownspells_offset.setValue(offsetStructs);
    offsetStructs = copyStruct(crefile.getList(), newlist, indexStructs, offsetStructs, KnownSpells.class);

    memspellinfo_offset.setValue(offsetStructs);
    offsetStructs = copyStruct(crefile.getList(), newlist, indexStructs, offsetStructs, SpellMemorization.class);

    memspells_offset.setValue(offsetStructs);
    // XXX: mem spells are not directly stored in crefile.list
    // and added by addFlatList on the Spell Memorization entries
    // (but the offsets are wrong, so we need to realign them with copyStruct)
    List<StructEntry> trashlist = new ArrayList<StructEntry>();
    for (int i = indexStructs; i < crefile.getFieldCount(); i++) {
      StructEntry entry = crefile.getField(i);
      if (entry instanceof SpellMemorization) {
        offsetStructs = copyStruct(((SpellMemorization)entry).getList(), trashlist, 0, offsetStructs, MemorizedSpells.class);
      }
    }

    effects_offset.setValue(offsetStructs);
    offsetStructs =
    copyStruct(crefile.getList(), newlist, indexStructs, offsetStructs, effects_offset.getSection());

    items_offset.setValue(offsetStructs);
    offsetStructs = copyStruct(crefile.getList(), newlist, indexStructs, offsetStructs, Item.class);

    itemslots_offset.setValue(offsetStructs);
    offsetStructs = copyStruct(crefile.getList(), newlist, indexStructs, offsetStructs, DecNumber.class);
    copyStruct(crefile.getList(), newlist, indexStructs, offsetStructs, Unknown.class);

    crefile.setList(newlist);
  }

  private static int copyStruct(List<StructEntry> oldlist, List<StructEntry> newlist,
                                int indexStructs, int offsetStructs,
                                Class<? extends StructEntry> copyClass)
  {
    for (int i = indexStructs; i < oldlist.size(); i++) {
      StructEntry structEntry = oldlist.get(i);
      if (structEntry.getClass() == copyClass) {
        structEntry.setOffset(offsetStructs);
        if (structEntry instanceof AbstractStruct)
          ((AbstractStruct)structEntry).realignStructOffsets();
        offsetStructs += structEntry.getSize();
        newlist.add(structEntry);
      }
    }
    return offsetStructs;
  }

  public static String getSearchString(InputStream is) throws IOException
  {
    String retVal = "";
    String sig = StreamUtils.readString(is, 4);
    is.skip(4);
    if (sig.equals("CHR ")) {
      retVal = StreamUtils.readString(is, 32);
    } else {
      String name = StringTable.getStringRef(StreamUtils.readInt(is));
      String shortName = StringTable.getStringRef(StreamUtils.readInt(is));
      if (name.equals(shortName)) {
        retVal = name;
      } else {
        retVal = name + " - " + shortName;
      }
    }
    return retVal.trim();
  }

  public CreResource(String name) throws Exception
  {
    super(null, name,
          StructureFactory.getInstance().createStructure(StructureFactory.ResType.RES_CRE,
                                                         null, null).getBuffer(),
          0);
  }

  public CreResource(ResourceEntry entry) throws Exception
  {
    super(entry);
    isChr = entry.getExtension().equalsIgnoreCase("CHR");
  }

  public CreResource(AbstractStruct superStruct, String name, ByteBuffer data, int startoffset) throws Exception
  {
    super(superStruct, name, data, startoffset);
    isChr = StreamUtils.readString(data, startoffset, 4).equalsIgnoreCase("CHR ");
  }

// --------------------- Begin Interface HasAddRemovable ---------------------

  @Override
  public AddRemovable[] getAddRemovables() throws Exception
  {
    IsNumeric effectVersion = (IsNumeric)getAttribute(CRE_EFFECT_VERSION);
    if (Profile.getEngine() == Profile.Engine.IWD2) {
      if (effectVersion.getValue() == 1) {
        return new AddRemovable[]{new Item(), new Effect2()};
      } else {
        return new AddRemovable[]{new Item(), new Effect()};
      }
    } else {
      if (effectVersion.getValue() == 1) {
        return new AddRemovable[]{new Item(), new Effect2(), new KnownSpells(), new SpellMemorization()};
      } else {
        return new AddRemovable[]{new Item(), new Effect(), new KnownSpells(), new SpellMemorization()};
      }
    }
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception
  {
    return entry;
  }

  @Override
  public boolean confirmRemoveEntry(AddRemovable entry) throws Exception
  {
    return true;
  }

// --------------------- End Interface HasAddRemovable ---------------------


//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------


// --------------------- Begin Interface HasViewerTabs ---------------------

  @Override
  public int getViewerTabCount()
  {
    return showRawTab() ? 2 : 1;
  }

  @Override
  public String getViewerTabName(int index)
  {
    switch (index) {
      case 0:
        return StructViewer.TAB_VIEW;
      case 1:
        return showRawTab() ? StructViewer.TAB_RAW : null;
    }
    return null;
  }

  @Override
  public JComponent getViewerTab(int index)
  {
    switch (index) {
      case 0:
        return new Viewer(this);
      case 1:
        if (showRawTab() && hexViewer == null) {
          hexViewer = new StructHexViewer(this, new BasicColorMap(this, true));
        }
        return hexViewer;
    }
    return null;
  }

  @Override
  public boolean viewerTabAddedBefore(int index)
  {
    return (index == 0);
  }

  // Needed for embedded CRE resources
  private boolean showRawTab()
  {
    if (hasRawTab == null) {
      hasRawTab = !(Boolean.valueOf(this.isChildOf(GamResource.class)) ||
                    Boolean.valueOf(this.isChildOf(AreResource.class)));
    }
    return hasRawTab.booleanValue();
  }

// --------------------- End Interface HasViewerTabs ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    super.writeFlatList(os);
  }


// --------------------- End Interface Writeable ---------------------

  @Override
  protected void viewerInitialized(StructViewer viewer)
  {
    viewer.addTabChangeListener(hexViewer);
    if (isChr) {
      ButtonPanel panel = viewer.getButtonPanel();
      JButton b = (JButton)panel.getControlByType(ButtonPanel.Control.EXPORT_BUTTON);
      int idx = panel.getControlPosition(b);
      if (b != null && idx >= 0) {
        // replacing button with menu
        b.removeActionListener(viewer);
        panel.removeControl(idx);
        miExport = new JMenuItem("original");
        miExport.setToolTipText(b.getToolTipText());
        miConvert = new JMenuItem("as CRE");
        bExport = (ButtonPopupMenu)panel.addControl(idx, ButtonPanel.Control.EXPORT_MENU);
        bExport.setMenuItems(new JMenuItem[]{miExport, miConvert});
        bExport.addItemListener(this);
      }
    }
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype)
  {
    updateOffsets(datatype, datatype.getSize());
    if (datatype instanceof SpellMemorization)
      updateMemorizedSpells();
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype)
  {
    updateOffsets(datatype, datatype.getSize());
    if (datatype instanceof MemorizedSpells)
      updateMemorizedSpells();
    super.datatypeAddedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype)
  {
    updateOffsets(datatype, -datatype.getSize());
    if (datatype instanceof SpellMemorization)
      updateMemorizedSpells();
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype)
  {
    updateOffsets(datatype, -datatype.getSize());
    if (datatype instanceof MemorizedSpells)
      updateMemorizedSpells();
    super.datatypeRemovedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception
  {
    setExtraOffset(getExtraOffset() + offset);
    TextString signature = new TextString(buffer, offset, 4, COMMON_SIGNATURE);
    addField(signature);
    TextString version = new TextString(buffer, offset + 4, 4, COMMON_VERSION);
    addField(version);
    if (signature.toString().equalsIgnoreCase("CHR ")) {
      addField(new TextString(buffer, offset + 8, 32, CHR_NAME));
      HexNumber structOffset = new HexNumber(buffer, offset + 40, 4, CHR_OFFSET_CRE);
      addField(structOffset);
      addField(new HexNumber(buffer, offset + 44, 4, CHR_CRE_SIZE));
      if (version.toString().equalsIgnoreCase("V2.2")) {
        for (int i = 0; i < 4; i++) {
          addField(new IdsBitmap(buffer, offset + 48 + (i * 4), 2,
                                 String.format(CHR_QUICK_WEAPON_SLOT_FMT, i+1), "SLOTS.IDS"));
          addField(new IdsBitmap(buffer, offset + 50 + (i * 4), 2,
                                 String.format(CHR_QUICK_SHIELD_SLOT_FMT, i+1), "SLOTS.IDS"));
        }
        for (int i = 0; i < 4; i++) {
          addField(new DecNumber(buffer, offset + 64 + (i * 4), 2,
                                 String.format(CHR_QUICK_WEAPON_ABILITY_FMT, i+1)));
          addField(new DecNumber(buffer, offset + 66 + (i * 4), 2,
                                 String.format(CHR_QUICK_SHIELD_ABILITY_FMT, i+1)));
        }
        for (int i = 0; i < 9; i++) {
          addField(new ResourceRef(buffer, offset + 80 + (i * 8),
                                   String.format(CHR_QUICK_SPELL_FMT, i+1), "SPL"));
        }
        for (int i = 0; i < 9; i++) {
          addField(new IdsBitmap(buffer, offset + 152 + i, 1,
                                 String.format(CHR_QUICK_SPELL_CLASS_FMT, i+1), "CLASS.IDS"));
        }
        addField(new Unknown(buffer, offset + 161, 1));
        for (int i = 0; i < 3; i++) {
          addField(new IdsBitmap(buffer, offset + 162 + (i * 2), 2,
                                 String.format(CHR_QUICK_ITEM_SLOT_FMT, i+1), "SLOTS.IDS"));
        }
        for (int i = 0; i < 3; i++) {
          addField(new DecNumber(buffer, offset + 168 + (i * 2), 2,
                                 String.format(CHR_QUICK_ITEM_ABILITY_FMT, i+1)));
        }
        for (int i = 0; i < 9; i++) {
          addField(new ResourceRef(buffer, offset + 174 + (i * 8),
                                   String.format(CHR_QUICK_ABILITY_FMT, i+1), "SPL"));
        }
        for (int i = 0; i < 9; i++) {
          addField(new ResourceRef(buffer, offset + 246 + (i * 8),
                                   String.format(CHR_QUICK_SONG_FMT, i+1), "SPL"));
        }
        for (int i = 0; i < 9; i++) {
          addField(new DecNumber(buffer, offset + 318 + (i * 4), 4,
                                 String.format(CHR_QUICK_BUTTON_FMT, i+1)));
        }
        addField(new Unknown(buffer, offset + 354, 26));
        addField(new TextString(buffer, offset + 380, 8, CHR_VOICE_SET_PREFIX));
        addField(new TextString(buffer, offset + 388, 32, CHR_VOICE_SET));
        addField(new Unknown(buffer, offset + 420, 128));
      }
      else if (version.toString().equalsIgnoreCase("V1.0") ||
               version.toString().equalsIgnoreCase("V2.0") ||
               version.toString().equalsIgnoreCase("V2.1")) {
        for (int i = 0; i < 4; i++) {
          addField(new IdsBitmap(buffer, offset + 48 + (i * 2), 2,
                                 String.format(CHR_QUICK_WEAPON_SLOT_FMT, i+1), "SLOTS.IDS"));
        }
        for (int i = 0; i < 4; i++) {
          addField(new DecNumber(buffer, offset + 56 + (i * 2), 2,
                                 String.format(CHR_QUICK_WEAPON_ABILITY_FMT, i+1)));
        }
        for (int i = 0; i < 3; i++) {
          addField(new ResourceRef(buffer, offset + 64 + (i * 8),
                                   String.format(CHR_QUICK_SPELL_FMT, i+1), "SPL"));
        }
        for (int i = 0; i < 3; i++) {
          addField(new IdsBitmap(buffer, offset + 88 + (i * 2), 2,
                                 String.format(CHR_QUICK_ITEM_SLOT_FMT, i+1), "SLOTS.IDS"));
        }
        for (int i = 0; i < 3; i++) {
          addField(new DecNumber(buffer, offset + 94 + (i * 2), 2,
                                 String.format(CHR_QUICK_ITEM_ABILITY_FMT, i+1)));
        }
      }
      else {
        addField(new Unknown(buffer, offset + 48, structOffset.getValue() - 48));
      }
      offset = structOffset.getValue();
      addField(new TextString(buffer, offset, 4, CHR_SIGNATURE_2));
      version = new TextString(buffer, offset + 4, 4, CHR_VERSION_2);
      addField(version);
      setExtraOffset(getExtraOffset() + structOffset.getValue());
    }
    offset += 8;
    if (version.toString().equalsIgnoreCase("V2.2")) {
      return readIWD2(buffer, offset);
    }
    return readOther(version.toString(), buffer, offset);
  }

  ////////////////////////
  // Icewind Dale 2
  ////////////////////////

  private int readIWD2(ByteBuffer buffer, int offset) throws Exception
  {
    addField(new StringRef(buffer, offset, CRE_NAME));
    addField(new StringRef(buffer, offset + 4, CRE_TOOLTIP));
    addField(new Flag(buffer, offset + 8, 4, CRE_FLAGS, s_flag)); // ToDo: figure these out whenever
    addField(new DecNumber(buffer, offset + 12, 4, CRE_XP_VALUE));
    addField(new DecNumber(buffer, offset + 16, 4, CRE_XP));
    addField(new DecNumber(buffer, offset + 20, 4, CRE_GOLD));
    addField(new IdsFlag(buffer, offset + 24, 4, CRE_STATUS, "STATE.IDS"));
    addField(new DecNumber(buffer, offset + 28, 2, CRE_HP_CURRENT));
    addField(new DecNumber(buffer, offset + 30, 2, CRE_HP_MAX));
    addField(new IdsBitmap(buffer, offset + 32, 4, CRE_ANIMATION, "ANIMATE.IDS"));
    addField(new ColorValue(buffer, offset + 36, 1, CRE_COLOR_METAL));
    addField(new ColorValue(buffer, offset + 37, 1, CRE_COLOR_MINOR));
    addField(new ColorValue(buffer, offset + 38, 1, CRE_COLOR_MAJOR));
    addField(new ColorValue(buffer, offset + 39, 1, CRE_COLOR_SKIN));
    addField(new ColorValue(buffer, offset + 40, 1, CRE_COLOR_LEATHER));
    addField(new ColorValue(buffer, offset + 41, 1, CRE_COLOR_ARMOR));
    addField(new ColorValue(buffer, offset + 42, 1, CRE_COLOR_HAIR));
    Bitmap effect_version = (Bitmap)addField(new Bitmap(buffer, offset + 43, 1, CRE_EFFECT_VERSION, s_effversion));
    addField(new ResourceRef(buffer, offset + 44, CRE_PORTRAIT_SMALL, "BMP"));
    addField(new ResourceRef(buffer, offset + 52, CRE_PORTRAIT_LARGE, "BMP"));
    addField(new DecNumber(buffer, offset + 60, 1, CRE_REPUTATION));
    addField(new DecNumber(buffer, offset + 61, 1, CRE_HIDE_IN_SHADOWS));
    addField(new DecNumber(buffer, offset + 62, 2, CRE_ARMOR_CLASS));
    addField(new DecNumber(buffer, offset + 64, 2, CRE_AC_MOD_BLUDGEONING));
    addField(new DecNumber(buffer, offset + 66, 2, CRE_AC_MOD_MISSILE));
    addField(new DecNumber(buffer, offset + 68, 2, CRE_AC_MOD_PIERCING));
    addField(new DecNumber(buffer, offset + 70, 2, CRE_AC_MOD_SLASHING));
    addField(new DecNumber(buffer, offset + 72, 1, CRE_BASE_ATTACK_BONUS));
    addField(new DecNumber(buffer, offset + 73, 1, CRE_ATTACKS_PER_ROUND));
    addField(new DecNumber(buffer, offset + 74, 1, CRE_SAVE_FORTITUDE));
    addField(new DecNumber(buffer, offset + 75, 1, CRE_SAVE_REFLEX));
    addField(new DecNumber(buffer, offset + 76, 1, CRE_SAVE_WILL));
    addField(new DecNumber(buffer, offset + 77, 1, CRE_RESISTANCE_FIRE));
    addField(new DecNumber(buffer, offset + 78, 1, CRE_RESISTANCE_COLD));
    addField(new DecNumber(buffer, offset + 79, 1, CRE_RESISTANCE_ELECTRICITY));
    addField(new DecNumber(buffer, offset + 80, 1, CRE_RESISTANCE_ACID));
    addField(new DecNumber(buffer, offset + 81, 1, CRE_RESISTANCE_SPELL));
    addField(new DecNumber(buffer, offset + 82, 1, CRE_RESISTANCE_MAGIC_FIRE));
    addField(new DecNumber(buffer, offset + 83, 1, CRE_RESISTANCE_MAGIC_COLD));
    addField(new DecNumber(buffer, offset + 84, 1, CRE_RESISTANCE_SLASHING));
    addField(new DecNumber(buffer, offset + 85, 1, CRE_RESISTANCE_BLUDGEONING));
    addField(new DecNumber(buffer, offset + 86, 1, CRE_RESISTANCE_PIERCING));
    addField(new DecNumber(buffer, offset + 87, 1, CRE_RESISTANCE_MISSILE));
    addField(new DecNumber(buffer, offset + 88, 1, CRE_RESISTANCE_MAGIC_DAMAGE));

    addField(new Unknown(buffer, offset + 89, 4));
    addField(new DecNumber(buffer, offset + 93, 1, CRE_FATIGUE));
    addField(new DecNumber(buffer, offset + 94, 1, CRE_INTOXICATION));
    addField(new DecNumber(buffer, offset + 95, 1, CRE_LUCK));
    addField(new DecNumber(buffer, offset + 96, 1, CRE_TURN_UNDEAD_LEVEL));
    addField(new Unknown(buffer, offset + 97, 33));

    addField(new DecNumber(buffer, offset + 130, 1, CRE_LEVELS_TOTAL));
    addField(new DecNumber(buffer, offset + 131, 1, CRE_LEVEL_BARBARIAN));
    addField(new DecNumber(buffer, offset + 132, 1, CRE_LEVEL_BARD));
    addField(new DecNumber(buffer, offset + 133, 1, CRE_LEVEL_CLERIC));
    addField(new DecNumber(buffer, offset + 134, 1, CRE_LEVEL_DRUID));
    addField(new DecNumber(buffer, offset + 135, 1, CRE_LEVEL_FIGHTER));
    addField(new DecNumber(buffer, offset + 136, 1, CRE_LEVEL_MONK));
    addField(new DecNumber(buffer, offset + 137, 1, CRE_LEVEL_PALADIN));
    addField(new DecNumber(buffer, offset + 138, 1, CRE_LEVEL_RANGER));
    addField(new DecNumber(buffer, offset + 139, 1, CRE_LEVEL_ROGUE));
    addField(new DecNumber(buffer, offset + 140, 1, CRE_LEVEL_SORCERER));
    addField(new DecNumber(buffer, offset + 141, 1, CRE_LEVEL_WIZARD));
    addField(new Unknown(buffer, offset + 142, 22));

    IdsMap sndMap = null;
    if (ResourceFactory.resourceExists("SOUNDOFF.IDS")) {
      sndMap = IdsMapCache.get("SOUNDOFF.IDS");
    }
    if (sndMap != null) {
      for (int i = 0; i < 64; i++) {
        IdsMapEntry e = sndMap.get((long)i);
        String label = (e != null) ? e.getSymbol() : "Unknown";
        addField(new StringRef(buffer, offset + 164 + (i * 4), String.format(CRE_SOUND_SLOT_FMT, label)));
      }
    }
    else {
      for (int i = 0; i < 64; i++) {
        addField(new StringRef(buffer, offset + 164 + (i * 4), CRE_SOUND_SLOT_GENERIC));
      }
    }

    addField(new ResourceRef(buffer, offset + 420, CRE_SCRIPT_TEAM, "BCS"));
    addField(new ResourceRef(buffer, offset + 428, CRE_SCRIPT_SPECIAL_1, "BCS"));
    addField(new DecNumber(buffer, offset + 436, 2, CRE_ENCHANTMENT_LEVEL));
    addField(new Unknown(buffer, offset + 438, 2));
    addField(new Flag(buffer, offset + 440, 4, CRE_FEATS_1, s_feats1));
    addField(new Flag(buffer, offset + 444, 4, CRE_FEATS_2, s_feats2));
    addField(new Flag(buffer, offset + 448, 4, CRE_FEATS_3, s_feats3));
    addField(new Unknown(buffer, offset + 452, 12));
    addField(new DecNumber(buffer, offset + 464, 1, CRE_MW_BOW));
    addField(new DecNumber(buffer, offset + 465, 1, CRE_SW_CROSSBOW));
    addField(new DecNumber(buffer, offset + 466, 1, CRE_SW_MISSILE));
    addField(new DecNumber(buffer, offset + 467, 1, CRE_MW_AXE));
    addField(new DecNumber(buffer, offset + 468, 1, CRE_SW_MACE));
    addField(new DecNumber(buffer, offset + 469, 1, CRE_MW_FLAIL));
    addField(new DecNumber(buffer, offset + 470, 1, CRE_MW_POLEARM));
    addField(new DecNumber(buffer, offset + 471, 1, CRE_MW_HAMMER));
    addField(new DecNumber(buffer, offset + 472, 1, CRE_SW_QUARTERSTAFF));
    addField(new DecNumber(buffer, offset + 473, 1, CRE_MW_GREATSWORD));
    addField(new DecNumber(buffer, offset + 474, 1, CRE_MW_LARGE_SWORD));
    addField(new DecNumber(buffer, offset + 475, 1, CRE_SW_SMALL_BLADE));
    addField(new DecNumber(buffer, offset + 476, 1, CRE_TOUGHNESS));
    addField(new DecNumber(buffer, offset + 477, 1, CRE_ARMORED_ARCANA));
    addField(new DecNumber(buffer, offset + 478, 1, CRE_CLEAVE));
    addField(new DecNumber(buffer, offset + 479, 1, CRE_ARMOR_PROFICIENCY));
    addField(new DecNumber(buffer, offset + 480, 1, CRE_SF_ENCHANTMENT));
    addField(new DecNumber(buffer, offset + 481, 1, CRE_SF_EVOCATION));
    addField(new DecNumber(buffer, offset + 482, 1, CRE_SF_NECROMANCY));
    addField(new DecNumber(buffer, offset + 483, 1, CRE_SF_TRANSMUTATION));
    addField(new DecNumber(buffer, offset + 484, 1, CRE_SPELL_PENETRATION));
    addField(new DecNumber(buffer, offset + 485, 1, CRE_EXTRA_RAGE));
    addField(new DecNumber(buffer, offset + 486, 1, CRE_EXTRA_WILD_SHAPE));
    addField(new DecNumber(buffer, offset + 487, 1, CRE_EXTRA_SMITING));
    addField(new DecNumber(buffer, offset + 488, 1, CRE_EXTRA_TURNING));
    addField(new DecNumber(buffer, offset + 489, 1, CRE_EW_BASTARD_SWORD));
    addField(new Unknown(buffer, offset + 490, 38));
    addField(new DecNumber(buffer, offset + 528, 1, CRE_ALCHEMY));
    addField(new DecNumber(buffer, offset + 529, 1, CRE_ANIMAL_EMPATHY));
    addField(new DecNumber(buffer, offset + 530, 1, CRE_BLUFF));
    addField(new DecNumber(buffer, offset + 531, 1, CRE_CONCENTRATION));
    addField(new DecNumber(buffer, offset + 532, 1, CRE_DIPLOMACY));
    addField(new DecNumber(buffer, offset + 533, 1, CRE_DISABLE_DEVICE));
    addField(new DecNumber(buffer, offset + 534, 1, CRE_HIDE));
    addField(new DecNumber(buffer, offset + 535, 1, CRE_INTIMIDATE));
    addField(new DecNumber(buffer, offset + 536, 1, CRE_KNOWLEDGE_ARCANA));
    addField(new DecNumber(buffer, offset + 537, 1, CRE_MOVE_SILENTLY));
    addField(new DecNumber(buffer, offset + 538, 1, CRE_OPEN_LOCKS));
    addField(new DecNumber(buffer, offset + 539, 1, CRE_PICK_POCKETS));
    addField(new DecNumber(buffer, offset + 540, 1, CRE_SEARCH));
    addField(new DecNumber(buffer, offset + 541, 1, CRE_SPELLCRAFT));
    addField(new DecNumber(buffer, offset + 542, 1, CRE_USE_MAGIC_DEVICE));
    addField(new DecNumber(buffer, offset + 543, 1, CRE_WILDERNESS_LORE));
    addField(new Unknown(buffer, offset + 544, 50));
    addField(new DecNumber(buffer, offset + 594, 1, CRE_CHALLENGE_RATING));
    for (int i = 0; i < 8; i++) {
      addField(new IdsBitmap(buffer, offset + 595 + i, 1,
                             String.format(CRE_FAVORED_ENEMY_FMT, i+1), "RACE.IDS"));
    }
    addField(new Bitmap(buffer, offset + 603, 1, CRE_SUBRACE, s_subraces));
    addField(new Unknown(buffer, offset + 604, 1));
    addField(new IdsBitmap(buffer, offset + 605, 1, CRE_SEX, "GENDER.IDS"));
    addField(new DecNumber(buffer, offset + 606, 1, CRE_STRENGTH));
    addField(new DecNumber(buffer, offset + 607, 1, CRE_INTELLIGENCE));
    addField(new DecNumber(buffer, offset + 608, 1, CRE_WISDOM));
    addField(new DecNumber(buffer, offset + 609, 1, CRE_DEXTERITY));
    addField(new DecNumber(buffer, offset + 610, 1, CRE_CONSTITUTION));
    addField(new DecNumber(buffer, offset + 611, 1, CRE_CHARISMA));
    addField(new DecNumber(buffer, offset + 612, 1, CRE_MORALE));
    addField(new DecNumber(buffer, offset + 613, 1, CRE_MORALE_BREAK));
    addField(new DecNumber(buffer, offset + 614, 2, CRE_MORALE_RECOVERY));
    addField(new KitIdsBitmap(buffer, offset + 616, CRE_KIT));
    addField(new ResourceRef(buffer, offset + 620, CRE_SCRIPT_OVERRIDE, "BCS"));
    addField(new ResourceRef(buffer, offset + 628, CRE_SCRIPT_SPECIAL_2, new String[]{"BCS", "BS"}));
    addField(new ResourceRef(buffer, offset + 636, CRE_SCRIPT_COMBAT, "BCS"));
    addField(new ResourceRef(buffer, offset + 644, CRE_SCRIPT_SPECIAL_3, "BCS"));
    addField(new ResourceRef(buffer, offset + 652, CRE_SCRIPT_MOVEMENT, "BCS"));
    addField(new Bitmap(buffer, offset + 660, 1, CRE_DEFAULT_VISIBILITY, s_visible));
    addField(new Bitmap(buffer, offset + 661, 1, CRE_SET_EXTRA_DEATH_VAR, s_noyes));
    addField(new Bitmap(buffer, offset + 662, 1, CRE_INCREMENT_KILL_COUNT, s_noyes));
    addField(new Unknown(buffer, offset + 663, 1));
    for (int i = 0; i < 5; i++) {
      addField(new DecNumber(buffer, offset + 664 + (i * 2), 2, String.format(CRE_INTERNAL_FMT, i+1)));
    }
    addField(new TextString(buffer, offset + 674, 32, CRE_DEATH_VAR_SET));
    addField(new TextString(buffer, offset + 706, 32, CRE_DEATH_VAR_INC));
    addField(new Bitmap(buffer, offset + 738, 2, CRE_LOCATION_SAVED, s_noyes));
    addField(new DecNumber(buffer, offset + 740, 2, CRE_SAVED_LOCATION_X));
    addField(new DecNumber(buffer, offset + 742, 2, CRE_SAVED_LOCATION_Y));
    addField(new DecNumber(buffer, offset + 744, 2, CRE_SAVED_ORIENTATION));
    addField(new Unknown(buffer, offset + 746, 15));
    addField(new DecNumber(buffer, offset + 761, 1, CRE_FADE_AMOUNT));
    addField(new DecNumber(buffer, offset + 762, 1, CRE_FADE_SPEED));
    addField(new Flag(buffer, offset + 763, 1, CRE_ATTRIBUTES, s_attributes_iwd2));
    addField(new DecNumber(buffer, offset + 764, 1, CRE_VISIBILITY));
    addField(new Unknown(buffer, offset + 765, 2));
    addField(new DecNumber(buffer, offset + 767, 1, CRE_SKILL_POINTS_UNUSED));
    addField(new Unknown(buffer, offset + 768, 124));
    addField(new IdsBitmap(buffer, offset + 892, 1, CRE_ALLEGIANCE, "EA.IDS"));
    addField(new IdsBitmap(buffer, offset + 893, 1, CRE_GENERAL, "GENERAL.IDS"));
    addField(new IdsBitmap(buffer, offset + 894, 1, CRE_RACE, "RACE.IDS"));
    addField(new IdsBitmap(buffer, offset + 895, 1, CRE_CLASS, "CLASS.IDS"));
    addField(new IdsBitmap(buffer, offset + 896, 1, CRE_SPECIFICS, "SPECIFIC.IDS"));
    addField(new IdsBitmap(buffer, offset + 897, 1, CRE_GENDER, "GENDER.IDS"));
    for (int i = 0; i < 5; i++) {
      addField(new IdsBitmap(buffer, offset + 898 + i, 1, String.format(CRE_OBJECT_FMT, i+1), "OBJECT.IDS"));
    }
    addField(new IdsBitmap(buffer, offset + 903, 1, CRE_ALIGNMENT, "ALIGNMNT.IDS"));
    addField(new DecNumber(buffer, offset + 904, 2, CRE_IDENTIFIER_GLOBAL));
    addField(new DecNumber(buffer, offset + 906, 2, CRE_IDENTIFIER_LOCAL));
    addField(new TextString(buffer, offset + 908, 32, CRE_SCRIPT_NAME));
    addField(new IdsBitmap(buffer, offset + 940, 2, CRE_CLASS_2, "CLASS.IDS"));
    addField(new IdsBitmap(buffer, offset + 942, 4, CRE_CLASS_MASK, "CLASSMSK.IDS"));

    // Bard spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 946 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_BARD_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1198 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_BARD_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_BARD_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Cleric spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 982 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_CLERIC_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1234 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_CLERIC_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_CLERIC_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Druid spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 1018 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_DRUID_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1270 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_DRUID_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_DRUID_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Paladin spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 1054 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_PALADIN_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1306 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_PALADIN_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_PALADIN_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Ranger spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 1090 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_RANGER_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1342 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_RANGER_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_RANGER_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Sorcerer spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 1126 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_SORCERER_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1378 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_SORCERER_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_SORCERER_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Wizard spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 1162 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_WIZARD_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1414 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_WIZARD_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_WIZARD_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Domain spells
    for (int i = 0; i < 9; i++) {
      SectionOffset s_off = new SectionOffset(buffer, offset + 1450 + (i * 4),
                                              String.format(CRE_OFFSET_SPELLS_DOMAIN_FMT, i+1), Iwd2Spell.class);
      DecNumber s_count = new DecNumber(buffer, offset + 1486 + (i * 4), 4,
                                        String.format(CRE_NUM_SPELLS_DOMAIN_FMT, i+1));
      addField(s_off);
      addField(s_count);
      AbstractStruct s = new Iwd2Struct(this, buffer, getExtraOffset() + s_off.getValue(), s_count,
                                        String.format(CRE_SPELLS_DOMAIN_FMT, i+1), Iwd2Struct.TYPE_SPELL);
      addField(s);
    }

    // Innate abilities
    SectionOffset inn_off = new SectionOffset(buffer, offset + 1522, CRE_OFFSET_ABILITIES, Iwd2Ability.class);
    DecNumber inn_num = new DecNumber(buffer, offset + 1526, 4, CRE_NUM_ABILITIES);
    addField(inn_off);
    addField(inn_num);
    AbstractStruct inn_str = new Iwd2Struct(this, buffer, getExtraOffset() + inn_off.getValue(),
                                            inn_num, CRE_ABILITIES, Iwd2Struct.TYPE_ABILITY);
    addField(inn_str);

    // Songs
    SectionOffset song_off = new SectionOffset(buffer, offset + 1530, CRE_OFFSET_SONGS, Iwd2Song.class);
    DecNumber song_num = new DecNumber(buffer, offset + 1534, 4, CRE_NUM_SONGS);
    addField(song_off);
    addField(song_num);
    AbstractStruct song_str = new Iwd2Struct(this, buffer, getExtraOffset() + song_off.getValue(),
                                             song_num, CRE_SONGS, Iwd2Struct.TYPE_SONG);
    addField(song_str);

    // Shapes
    SectionOffset shape_off = new SectionOffset(buffer, offset + 1538, CRE_OFFSET_SHAPES, Iwd2Shape.class);
    DecNumber shape_num = new DecNumber(buffer, offset + 1542, 4, CRE_NUM_SHAPES);
    addField(shape_off);
    addField(shape_num);
    AbstractStruct shape_str = new Iwd2Struct(this, buffer, getExtraOffset() + shape_off.getValue(),
                                              shape_num, CRE_SHAPES, Iwd2Struct.TYPE_SHAPE);
    addField(shape_str);

    SectionOffset itemslots_offset = new SectionOffset(buffer, offset + 1546, CRE_OFFSET_ITEM_SLOTS, DecNumber.class);
    addField(itemslots_offset);
    SectionOffset items_offset = new SectionOffset(buffer, offset + 1550, CRE_OFFSET_ITEMS,
                                                   Item.class);
    addField(items_offset);
    SectionCount items_count = new SectionCount(buffer, offset + 1554, 4, CRE_NUM_ITEMS,
                                                Item.class);
    addField(items_count);

    SectionOffset effects_offset;
    SectionCount effects_count;
    if (effect_version.getValue() == 1) {
      effects_offset = new SectionOffset(buffer, offset + 1558, CRE_OFFSET_EFFECTS, Effect2.class);
      effects_count = new SectionCount(buffer, offset + 1562, 4, CRE_NUM_EFFECTS, Effect2.class);
    }
    else {
      effects_offset = new SectionOffset(buffer, offset + 1558, CRE_OFFSET_EFFECTS, Effect.class);
      effects_count = new SectionCount(buffer, offset + 1562, 4, CRE_NUM_EFFECTS, Effect.class);
    }
    addField(effects_offset);
    addField(effects_count);
    addField(new ResourceRef(buffer, offset + 1566, CRE_DIALOG, "DLG"));

    offset = getExtraOffset() + effects_offset.getValue();
    if (effect_version.getValue() == 1)
      for (int i = 0; i < effects_count.getValue(); i++) {
        Effect2 eff = new Effect2(this, buffer, offset, i);
        offset = eff.getEndOffset();
        addField(eff);
      }
    else
      for (int i = 0; i < effects_count.getValue(); i++) {
        Effect eff = new Effect(this, buffer, offset, i);
        offset = eff.getEndOffset();
        addField(eff);
      }

    offset = getExtraOffset() + items_offset.getValue();
    for (int i = 0; i < items_count.getValue(); i++) {
      Item item = new Item(this, buffer, offset, i);
      offset = item.getEndOffset();
      addField(item);
    }

    offset = getExtraOffset() + itemslots_offset.getValue();
    addField(new DecNumber(buffer, offset, 2, CRE_ITEM_SLOT_HELMET));
    addField(new DecNumber(buffer, offset + 2, 2, CRE_ITEM_SLOT_ARMOR));
    addField(new DecNumber(buffer, offset + 4, 2, CRE_ITEM_SLOT_SHIELD));
    addField(new DecNumber(buffer, offset + 6, 2, CRE_ITEM_SLOT_GAUNTLETS));
    addField(new DecNumber(buffer, offset + 8, 2, CRE_ITEM_SLOT_LEFT_RING));
    addField(new DecNumber(buffer, offset + 10, 2, CRE_ITEM_SLOT_RIGHT_RING));
    addField(new DecNumber(buffer, offset + 12, 2, CRE_ITEM_SLOT_AMULET));
    addField(new DecNumber(buffer, offset + 14, 2, CRE_ITEM_SLOT_BELT));
    addField(new DecNumber(buffer, offset + 16, 2, CRE_ITEM_SLOT_BOOTS));
    for (int i = 0; i < 4; i++) {
      addField(new DecNumber(buffer, offset + 18 + (i * 4), 2, String.format(CRE_ITEM_SLOT_WEAPON_FMT, i+1)));
      addField(new DecNumber(buffer, offset + 20 + (i * 4), 2, String.format(CRE_ITEM_SLOT_SHIELD_FMT, i+1)));
    }
    for (int i = 0; i < 4; i++) {
      addField(new DecNumber(buffer, offset + 34 + (i * 2), 2, String.format(CRE_ITEM_SLOT_QUIVER_FMT, i+1)));
    }
    addField(new DecNumber(buffer, offset + 42, 2, CRE_ITEM_SLOT_CLOAK));
    for (int i = 0; i < 3; i++) {
      addField(new DecNumber(buffer, offset + 44 + (i * 2), 2, String.format(CRE_ITEM_SLOT_QUICK_FMT, i+1)));
    }
    for (int i = 0; i < 24; i++) {
      addField(new DecNumber(buffer, offset + 50 + (i * 2), 2,
                             String.format(CRE_ITEM_SLOT_INVENTORY_FMT, i+1)));
    }
    addField(new DecNumber(buffer, offset + 98, 2, CRE_ITEM_SLOT_MAGIC_WEAPON));
    addField(new DecNumber(buffer, offset + 100, 2, CRE_SELECTED_WEAPON_SLOT));
    addField(new DecNumber(buffer, offset + 102, 2, CRE_SELECTED_WEAPON_ABILITY));

    int endoffset = offset;
    for (int i = 0; i < getFieldCount(); i++) {
      StructEntry entry = getField(i);
      if (entry.getOffset() + entry.getSize() > endoffset) {
        endoffset = entry.getOffset() + entry.getSize();
      }
    }
    return endoffset;
  }

  ////////////////////////
  // Other IE games
  ////////////////////////

  private int readOther(String version, ByteBuffer buffer, int offset) throws Exception
  {
    addField(new StringRef(buffer, offset, CRE_NAME));
    addField(new StringRef(buffer, offset + 4, CRE_TOOLTIP));
    addField(new Flag(buffer, offset + 8, 4, CRE_FLAGS, s_flag));
    addField(new DecNumber(buffer, offset + 12, 4, CRE_XP_VALUE));
    addField(new DecNumber(buffer, offset + 16, 4, CRE_XP));
    addField(new DecNumber(buffer, offset + 20, 4, CRE_GOLD));
    addField(new IdsFlag(buffer, offset + 24, 4, CRE_STATUS, "STATE.IDS"));
    addField(new DecNumber(buffer, offset + 28, 2, CRE_HP_CURRENT));
    addField(new DecNumber(buffer, offset + 30, 2, CRE_HP_MAX));
    final AnimateBitmap animate = new AnimateBitmap(buffer, offset + 32, 4, CRE_ANIMATION);
    if (Profile.getGame() == Profile.Game.PSTEE && version.equals("V1.0")) {
      // TODO: resolve issues with Listener queue filled with duplicate entries on each "Update" button click
//      animate.addUpdateListener(this);
    }
    addField(animate);
    if (Profile.getGame() == Profile.Game.PSTEE && version.equals("V1.0")) {
      setColorFieldsPSTEE(animate.getValue(), buffer, offset + 36, false);
    } else {
      addField(new ColorValue(buffer, offset + 36, 1, CRE_COLOR_METAL));
      addField(new ColorValue(buffer, offset + 37, 1, CRE_COLOR_MINOR));
      addField(new ColorValue(buffer, offset + 38, 1, CRE_COLOR_MAJOR));
      addField(new ColorValue(buffer, offset + 39, 1, CRE_COLOR_SKIN));
      addField(new ColorValue(buffer, offset + 40, 1, CRE_COLOR_LEATHER));
      addField(new ColorValue(buffer, offset + 41, 1, CRE_COLOR_ARMOR));
      addField(new ColorValue(buffer, offset + 42, 1, CRE_COLOR_HAIR));
    }
    Bitmap effect_version = (Bitmap)addField(new Bitmap(buffer, offset + 43, 1, CRE_EFFECT_VERSION, s_effversion));
    addField(new ResourceRef(buffer, offset + 44, CRE_PORTRAIT_SMALL, "BMP"));
    if (version.equalsIgnoreCase("V1.2") || version.equalsIgnoreCase("V1.1")) {
      addField(new ResourceRef(buffer, offset + 52, CRE_PORTRAIT_LARGE, "BAM"));
    } else if (version.equalsIgnoreCase("V1.0") && Profile.getGame() == Profile.Game.PSTEE) {
      addField(new ResourceRef(buffer, offset + 52, CRE_PORTRAIT_LARGE, "BAM"));
    } else {
      addField(new ResourceRef(buffer, offset + 52, CRE_PORTRAIT_LARGE, "BMP"));
    }
    addField(new UnsignDecNumber(buffer, offset + 60, 1, CRE_REPUTATION));
    addField(new UnsignDecNumber(buffer, offset + 61, 1, CRE_HIDE_IN_SHADOWS));
    addField(new DecNumber(buffer, offset + 62, 2, CRE_AC_NATURAL));
    addField(new DecNumber(buffer, offset + 64, 2, CRE_AC_EFFECTIVE));
    addField(new DecNumber(buffer, offset + 66, 2, CRE_AC_MOD_CRUSHING));
    addField(new DecNumber(buffer, offset + 68, 2, CRE_AC_MOD_MISSILE));
    addField(new DecNumber(buffer, offset + 70, 2, CRE_AC_MOD_PIERCING));
    addField(new DecNumber(buffer, offset + 72, 2, CRE_AC_MOD_SLASHING));
    addField(new DecNumber(buffer, offset + 74, 1, CRE_THAC0));
    addField(new Bitmap(buffer, offset + 75, 1, CRE_ATTACKS_PER_ROUND, s_attacks));
    addField(new DecNumber(buffer, offset + 76, 1, CRE_SAVE_DEATH));
    addField(new DecNumber(buffer, offset + 77, 1, CRE_SAVE_WAND));
    addField(new DecNumber(buffer, offset + 78, 1, CRE_SAVE_POLYMORPH));
    addField(new DecNumber(buffer, offset + 79, 1, CRE_SAVE_BREATH));
    addField(new DecNumber(buffer, offset + 80, 1, CRE_SAVE_SPELL));
    addField(new DecNumber(buffer, offset + 81, 1, CRE_RESISTANCE_FIRE));
    addField(new DecNumber(buffer, offset + 82, 1, CRE_RESISTANCE_COLD));
    addField(new DecNumber(buffer, offset + 83, 1, CRE_RESISTANCE_ELECTRICITY));
    addField(new DecNumber(buffer, offset + 84, 1, CRE_RESISTANCE_ACID));
    addField(new DecNumber(buffer, offset + 85, 1, CRE_RESISTANCE_MAGIC));
    addField(new DecNumber(buffer, offset + 86, 1, CRE_RESISTANCE_MAGIC_FIRE));
    addField(new DecNumber(buffer, offset + 87, 1, CRE_RESISTANCE_MAGIC_COLD));
    addField(new DecNumber(buffer, offset + 88, 1, CRE_RESISTANCE_SLASHING));
    addField(new DecNumber(buffer, offset + 89, 1, CRE_RESISTANCE_CRUSHING));
    addField(new DecNumber(buffer, offset + 90, 1, CRE_RESISTANCE_PIERCING));
    addField(new DecNumber(buffer, offset + 91, 1, CRE_RESISTANCE_MISSILE));
    if (version.equalsIgnoreCase("V1.2") || version.equalsIgnoreCase("V1.1")) {
      addField(new DecNumber(buffer, offset + 92, 1, CRE_PROFICIENCIES_UNSPENT));
    } else {
      addField(new UnsignDecNumber(buffer, offset + 92, 1, CRE_DETECT_ILLUSION));
    }
    addField(new UnsignDecNumber(buffer, offset + 93, 1, CRE_SET_TRAPS));
    addField(new DecNumber(buffer, offset + 94, 1, CRE_LORE));
    addField(new UnsignDecNumber(buffer, offset + 95, 1, CRE_OPEN_LOCKS));
    addField(new UnsignDecNumber(buffer, offset + 96, 1, CRE_MOVE_SILENTLY));
    addField(new UnsignDecNumber(buffer, offset + 97, 1, CRE_FIND_TRAPS));
    addField(new UnsignDecNumber(buffer, offset + 98, 1, CRE_PICK_POCKETS));
    addField(new DecNumber(buffer, offset + 99, 1, CRE_FATIGUE));
    addField(new DecNumber(buffer, offset + 100, 1, CRE_INTOXICATION));
    addField(new DecNumber(buffer, offset + 101, 1, CRE_LUCK));
    if (version.equals("V1.0")) {
      addField(new MultiNumber(buffer, offset + 102, 1, CRE_PROFICIENCY_LARGE_SWORD, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 103, 1, CRE_PROFICIENCY_SMALL_SWORD, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 104, 1, CRE_PROFICIENCY_BOW, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 105, 1, CRE_PROFICIENCY_SPEAR, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 106, 1, CRE_PROFICIENCY_BLUNT, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 107, 1, CRE_PROFICIENCY_SPIKED, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 108, 1, CRE_PROFICIENCY_AXE, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 109, 1, CRE_PROFICIENCY_MISSILE, 3, 2, s_profLabels));
      if (Profile.isEnhancedEdition()) {
        if (Profile.getGame() == Profile.Game.PSTEE) {
          addField(new Unknown(buffer, offset + 110, 5));
          addField(new UnsignDecNumber(buffer, offset + 115, 1, CRE_PROFICIENCIES_UNSPENT));
          addField(new SectionCount(buffer, offset + 116, 1, CRE_NUM_ITEM_SLOTS, Unknown.class));
        } else {
          addField(new Unknown(buffer, offset + 110, 7));
        }
        addField(new Bitmap(buffer, offset + 117, 1, CRE_NIGHTMARE_MODE, s_noyes));
        addField(new UnsignDecNumber(buffer, offset + 118, 1, CRE_TRANSLUCENCY));
        if (Profile.getGame() == Profile.Game.PSTEE) {
          addField(new DecNumber(buffer, offset + 119, 1, CRE_MURDER_INC));
          addField(new Unknown(buffer, offset + 120, 2));
        } else {
          addField(new DecNumber(buffer, offset + 119, 1, CRE_REPUTATION_MOD_KILLED));
          addField(new DecNumber(buffer, offset + 120, 1, CRE_REPUTATION_MOD_JOIN));
          addField(new DecNumber(buffer, offset + 121, 1, CRE_REPUTATION_MOD_LEAVE));
        }
      } else {
        addField(new Unknown(buffer, offset + 110, 12));
      }
    }
    else if (version.equalsIgnoreCase("V1.2") || version.equalsIgnoreCase("V1.1")) {
      addField(new MultiNumber(buffer, offset + 102, 1, CRE_PROFICIENCY_FIST, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 103, 1, CRE_PROFICIENCY_EDGED_WEAPON, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 104, 1, CRE_PROFICIENCY_HAMMER, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 105, 1, CRE_PROFICIENCY_AXE, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 106, 1, CRE_PROFICIENCY_CLUB, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 107, 1, CRE_PROFICIENCY_BOW, 3, 2, s_profLabels));
      addField(new Unknown(buffer, offset + 108, 14));
    }
    else if (version.equalsIgnoreCase("V9.0")) {
      addField(new MultiNumber(buffer, offset + 102, 1, CRE_PROFICIENCY_LARGE_SWORD, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 103, 1, CRE_PROFICIENCY_SMALL_SWORD, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 104, 1, CRE_PROFICIENCY_BOW, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 105, 1, CRE_PROFICIENCY_SPEAR, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 106, 1, CRE_PROFICIENCY_AXE, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 107, 1, CRE_PROFICIENCY_MISSILE, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 108, 1, CRE_PROFICIENCY_GREATSWORD, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 109, 1, CRE_PROFICIENCY_DAGGER, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 110, 1, CRE_PROFICIENCY_HALBERD, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 111, 1, CRE_PROFICIENCY_MACE, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 112, 1, CRE_PROFICIENCY_FLAIL, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 113, 1, CRE_PROFICIENCY_HAMMER, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 114, 1, CRE_PROFICIENCY_CLUB, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 115, 1, CRE_PROFICIENCY_QUARTERSTAFF, 3, 2, s_profLabels));
      addField(new MultiNumber(buffer, offset + 116, 1, CRE_PROFICIENCY_CROSSBOW, 3, 2, s_profLabels));
      addField(new Unknown(buffer, offset + 117, 5));
    }
    else {
      clearFields();
      throw new Exception("Unsupported version: " + version);
    }
    addField(new DecNumber(buffer, offset + 122, 1, CRE_TURN_UNDEAD_LEVEL));
    addField(new DecNumber(buffer, offset + 123, 1, CRE_TRACKING));
    if (Profile.getGame() == Profile.Game.PSTEE) {
      addField(new DecNumber(buffer, offset + 124, 4, CRE_XP_SECOND_CLASS));
      addField(new DecNumber(buffer, offset + 128, 4, CRE_XP_THIRD_CLASS));
      addField(new DecNumber(buffer, offset + 132, 1, CRE_GOOD_INC));
      addField(new DecNumber(buffer, offset + 133, 1, CRE_LAW_INC));
      addField(new DecNumber(buffer, offset + 134, 1, CRE_LADY_INC));
      addField(new IdsBitmap(buffer, offset + 135, 1, CRE_FACTION, "FACTION.IDS"));
      addField(new IdsBitmap(buffer, offset + 136, 1, CRE_TEAM, "TEAM.IDS"));
      addField(new IdsBitmap(buffer, offset + 137, 1, CRE_SPECIES, "RACE.IDS"));
      addField(new DecNumber(buffer, offset + 138, 1, CRE_DIALOG_ACTIVATION_RADIUS));
      addField(new DecNumber(buffer, offset + 139, 1, CRE_COLLISION_RADIUS));
      addField(new Flag(buffer, offset + 140, 1, CRE_SHIELD_FLAGS,
                        new String[]{"No flags set", "Normal shield", "Black barbed shield"}));
      addField(new DecNumber(buffer, offset + 141, 1, CRE_FIELD_OF_VISION));
      addField(new Flag(buffer, offset + 142, 4, CRE_ATTRIBUTES, s_attributes_pst));
      addField(new Unknown(buffer, offset + 146, 10));
    } else {
      addField(new TextString(buffer, offset + 124, 32, CRE_TARGET));
    }
    IdsMap sndMap = null;
    if (ResourceFactory.resourceExists("SNDSLOT.IDS")) {
      sndMap = IdsMapCache.get("SNDSLOT.IDS");
    } else if (ResourceFactory.resourceExists("SOUNDOFF.IDS")) {
      sndMap = IdsMapCache.get("SOUNDOFF.IDS");
    }
    if (sndMap != null) {
      for (int i = 0; i < 100; i++) {
        IdsMapEntry e = sndMap.get((long)i);
        String label = (e != null) ? e.getSymbol() : COMMON_UNKNOWN;
        addField(new StringRef(buffer, offset + 156 + (i * 4), String.format(CRE_SOUND_SLOT_FMT, label)));
      }
    } else {
      for (int i = 0; i < 100; i++) {
        addField(new StringRef(buffer, offset + 156 + i * 4, CRE_SOUND_SLOT_GENERIC));
      }
    }
    addField(new DecNumber(buffer, offset + 556, 1, CRE_LEVEL_FIRST_CLASS));
    addField(new DecNumber(buffer, offset + 557, 1, CRE_LEVEL_SECOND_CLASS));
    addField(new DecNumber(buffer, offset + 558, 1, CRE_LEVEL_THIRD_CLASS));
    addField(new IdsBitmap(buffer, offset + 559, 1, CRE_SEX, "GENDER.IDS"));
    addField(new DecNumber(buffer, offset + 560, 1, CRE_STRENGTH));
    addField(new DecNumber(buffer, offset + 561, 1, CRE_STRENGTH_BONUS));
    addField(new DecNumber(buffer, offset + 562, 1, CRE_INTELLIGENCE));
    addField(new DecNumber(buffer, offset + 563, 1, CRE_WISDOM));
    addField(new DecNumber(buffer, offset + 564, 1, CRE_DEXTERITY));
    addField(new DecNumber(buffer, offset + 565, 1, CRE_CONSTITUTION));
    addField(new DecNumber(buffer, offset + 566, 1, CRE_CHARISMA));
    addField(new DecNumber(buffer, offset + 567, 1, CRE_MORALE));
    addField(new DecNumber(buffer, offset + 568, 1, CRE_MORALE_BREAK));
    addField(new IdsBitmap(buffer, offset + 569, 1, CRE_RACIAL_ENEMY, "RACE.IDS"));
    addField(new DecNumber(buffer, offset + 570, 2, CRE_MORALE_RECOVERY));
    if (Profile.getGame() != Profile.Game.PSTEE &&
        ResourceFactory.resourceExists("KIT.IDS")) {
      addField(new KitIdsBitmap(buffer, offset + 572, CRE_KIT));
    }
    else {
      if (ResourceFactory.resourceExists("DEITY.IDS")) {
        addField(new IdsBitmap(buffer, offset + 572, 2, CRE_DEITY, "DEITY.IDS"));
      } else if (ResourceFactory.resourceExists("DIETY.IDS")) {
        addField(new IdsBitmap(buffer, offset + 572, 2, CRE_DEITY, "DIETY.IDS"));
      } else {
        addField(new Unknown(buffer, offset + 572, 2));
      }
      if (ResourceFactory.resourceExists("MAGESPEC.IDS")) {
        addField(new IdsBitmap(buffer, offset + 574, 2, CRE_MAGE_TYPE, "MAGESPEC.IDS"));
      } else {
        addField(new HashBitmap(buffer, offset + 574, 2, CRE_MAGE_TYPE, m_magetype));
      }
    }
    addField(new ResourceRef(buffer, offset + 576, CRE_SCRIPT_OVERRIDE, "BCS"));
    addField(new ResourceRef(buffer, offset + 584, CRE_SCRIPT_CLASS, new String[]{"BCS", "BS"}));
    addField(new ResourceRef(buffer, offset + 592, CRE_SCRIPT_RACE, "BCS"));
    addField(new ResourceRef(buffer, offset + 600, CRE_SCRIPT_GENERAL, "BCS"));
    addField(new ResourceRef(buffer, offset + 608, CRE_SCRIPT_DEFAULT, "BCS"));
    if (version.equalsIgnoreCase("V1.2") || version.equalsIgnoreCase("V1.1")) {
      addField(new Unknown(buffer, offset + 616, 24));
      addField(new Unknown(buffer, offset + 640, 4));
      addField(new Unknown(buffer, offset + 644, 8));
      addField(new Unknown(buffer, offset + 652, 4, CRE_OFFSET_OVERLAYS));
      addField(new Unknown(buffer, offset + 656, 4, CRE_OVERLAYS_SIZE));
      addField(new DecNumber(buffer, offset + 660, 4, CRE_XP_SECOND_CLASS));
      addField(new DecNumber(buffer, offset + 664, 4, CRE_XP_THIRD_CLASS));
      IdsMap intMap = IdsMapCache.get("INTERNAL.IDS");
      for (int i = 0; i < 10; i++) {
        IdsMapEntry e = intMap.get((long)i);
        if (e != null) {
          addField(new DecNumber(buffer, offset + 668 + i * 2, 2, e.getSymbol()));
        } else {
          addField(new DecNumber(buffer, offset + 668 + i * 2, 2, String.format(CRE_INTERNAL_FMT, i)));
        }
      }
      addField(new DecNumber(buffer, offset + 688, 1, CRE_GOOD_INC));
      addField(new DecNumber(buffer, offset + 689, 1, CRE_LAW_INC));
      addField(new DecNumber(buffer, offset + 690, 1, CRE_LADY_INC));
      addField(new DecNumber(buffer, offset + 691, 1, CRE_MURDER_INC));
      addField(new TextString(buffer, offset + 692, 32, CRE_CHARACTER_TYPE));
      addField(new DecNumber(buffer, offset + 724, 1, CRE_DIALOG_ACTIVATION_RADIUS));
      addField(new DecNumber(buffer, offset + 725, 1, CRE_COLLISION_RADIUS)); // 0x2dd
      addField(new Unknown(buffer, offset + 726, 1));
      addField(new DecNumber(buffer, offset + 727, 1, CRE_NUM_COLORS));
      addField(new Flag(buffer, offset + 728, 4, CRE_ATTRIBUTES, s_attributes_pst));
      for (int i = 0; i < 7; i++) {
        addField(new ColorValue(buffer, offset + 732 + (i * 2), 2, String.format(CRE_COLOR_FMT, i+1),
                                "PAL32.BMP"));
//        addField(new IdsBitmap(buffer, offset + 732 + (i * 2), 2,
//                               String.format(CRE_COLOR_FMT, i+1), "CLOWNCLR.IDS"));
      }
      addField(new Unknown(buffer, offset + 746, 3));
      for (int i = 0; i < 7; i++) {
        addField(new HashBitmap(buffer, offset + 749 + i, 1,
                                String.format(CRE_COLOR_PLACEMENT_FMT, i+1), m_colorPlacement));
      }
      addField(new Unknown(buffer, offset + 756, 21));
      addField(new IdsBitmap(buffer, offset + 777, 1, CRE_SPECIES, "RACE.IDS"));
      addField(new IdsBitmap(buffer, offset + 778, 1, CRE_TEAM, "TEAM.IDS"));
      addField(new IdsBitmap(buffer, offset + 779, 1, CRE_FACTION, "FACTION.IDS"));
      offset += 164;
    }
    else if (version.equalsIgnoreCase("V9.0")) {
      addField(new Bitmap(buffer, offset + 616, 1, CRE_DEFAULT_VISIBILITY, s_visible));
      addField(new Bitmap(buffer, offset + 617, 1, CRE_SET_EXTRA_DEATH_VAR, s_noyes));
      addField(new Bitmap(buffer, offset + 618, 1, CRE_INCREMENT_KILL_COUNT, s_noyes));
      addField(new Unknown(buffer, offset + 619, 1));
      for (int i = 0; i < 5; i++) {
        addField(new DecNumber(buffer, offset + 620 + (i * 2), 2, String.format(CRE_INTERNAL_FMT, i+1)));
      }
      addField(new TextString(buffer, offset + 630, 32, CRE_DEATH_VAR_SET));
      addField(new TextString(buffer, offset + 662, 32, CRE_DEATH_VAR_INC));
      addField(new Bitmap(buffer, offset + 694, 2, CRE_LOCATION_SAVED, s_noyes));
      addField(new DecNumber(buffer, offset + 696, 2, CRE_SAVED_LOCATION_X));
      addField(new DecNumber(buffer, offset + 698, 2, CRE_SAVED_LOCATION_Y));
      addField(new DecNumber(buffer, offset + 700, 2, CRE_SAVED_ORIENTATION));
      addField(new Unknown(buffer, offset + 702, 18));
      offset += 104;
    }
    addField(new IdsBitmap(buffer, offset + 616, 1, CRE_ALLEGIANCE, "EA.IDS"));
    addField(new IdsBitmap(buffer, offset + 617, 1, CRE_GENERAL, "GENERAL.IDS"));
    addField(new IdsBitmap(buffer, offset + 618, 1, CRE_RACE, "RACE.IDS"));
    addField(new IdsBitmap(buffer, offset + 619, 1, CRE_CLASS, "CLASS.IDS"));
    addField(new IdsBitmap(buffer, offset + 620, 1, CRE_SPECIFICS, "SPECIFIC.IDS"));
    addField(new IdsBitmap(buffer, offset + 621, 1, CRE_GENDER, "GENDER.IDS"));
    for (int i = 0; i < 5; i++) {
      addField(new IdsBitmap(buffer, offset + 622 + i, 1, String.format(CRE_OBJECT_FMT, i+1), "OBJECT.IDS"));
    }
    addField(new IdsBitmap(buffer, offset + 627, 1, CRE_ALIGNMENT, "ALIGNMEN.IDS"));
    addField(new DecNumber(buffer, offset + 628, 2, CRE_IDENTIFIER_GLOBAL));
    addField(new DecNumber(buffer, offset + 630, 2, CRE_IDENTIFIER_LOCAL));
    addField(new TextString(buffer, offset + 632, 32, CRE_SCRIPT_NAME));

    SectionOffset offsetKnownSpells = new SectionOffset(buffer, offset + 664, CRE_OFFSET_KNOWN_SPELLS,
                                                        KnownSpells.class);
    addField(offsetKnownSpells);
    SectionCount countKnownSpells = new SectionCount(buffer, offset + 668, 4, CRE_NUM_KNOWN_SPELLS,
                                                     KnownSpells.class);
    addField(countKnownSpells);
    SectionOffset offsetMemSpellInfo = new SectionOffset(buffer, offset + 672, CRE_OFFSET_MEMORIZATION_INFO,
                                                         SpellMemorization.class);
    addField(offsetMemSpellInfo);
    SectionCount countMemSpellInfo = new SectionCount(buffer, offset + 676, 4, CRE_NUM_MEMORIZATION_INFO,
                                                      SpellMemorization.class);
    addField(countMemSpellInfo);
    SectionOffset offsetMemSpells = new SectionOffset(buffer, offset + 680, CRE_OFFSET_MEMORIZED_SPELLS,
                                                      MemorizedSpells.class);
    addField(offsetMemSpells);
    SectionCount countMemSpells = new SectionCount(buffer, offset + 684, 4, CRE_NUM_MEMORIZED_SPELLS,
                                                   MemorizedSpells.class);
    addField(countMemSpells);
    SectionOffset offsetItemslots = new SectionOffset(buffer, offset + 688, CRE_OFFSET_ITEM_SLOTS, DecNumber.class);
    addField(offsetItemslots);
    SectionOffset offsetItems = new SectionOffset(buffer, offset + 692, CRE_OFFSET_ITEMS, Item.class);
    addField(offsetItems);
    SectionCount countItems = new SectionCount(buffer, offset + 696, 4, CRE_NUM_ITEMS, Item.class);
    addField(countItems);
    SectionOffset offsetEffects;
    SectionCount countEffects;
    if (effect_version.getValue() == 1) {
      offsetEffects = new SectionOffset(buffer, offset + 700, CRE_OFFSET_EFFECTS, Effect2.class);
      countEffects = new SectionCount(buffer, offset + 704, 4, CRE_NUM_EFFECTS, Effect2.class);
    }
    else {
      offsetEffects = new SectionOffset(buffer, offset + 700, CRE_OFFSET_EFFECTS, Effect.class);
      countEffects = new SectionCount(buffer, offset + 704, 4, CRE_NUM_EFFECTS, Effect.class);
    }
    addField(offsetEffects);
    addField(countEffects);
    addField(new ResourceRef(buffer, offset + 708, CRE_DIALOG, "DLG"));

    offset = getExtraOffset() + offsetKnownSpells.getValue();
    for (int i = 0; i < countKnownSpells.getValue(); i++) {
      KnownSpells known = new KnownSpells(this, buffer, offset, i);
      offset = known.getEndOffset();
      addField(known);
    }

    offset = getExtraOffset() + offsetMemSpellInfo.getValue();
    for (int i = 0; i < countMemSpellInfo.getValue(); i++) {
      SpellMemorization mem = new SpellMemorization(this, buffer, offset, i);
      offset = mem.getEndOffset();
      mem.readMemorizedSpells(buffer, offsetMemSpells.getValue() + getExtraOffset());
      addField(mem);
    }

    offset = getExtraOffset() + offsetEffects.getValue();
    if (effect_version.getValue() == 1) {
      for (int i = 0; i < countEffects.getValue(); i++) {
        Effect2 eff = new Effect2(this, buffer, offset, i);
        offset = eff.getEndOffset();
        addField(eff);
      }
    } else {
      for (int i = 0; i < countEffects.getValue(); i++) {
        Effect eff = new Effect(this, buffer, offset, i);
        offset = eff.getEndOffset();
        addField(eff);
      }
    }

    offset = getExtraOffset() + offsetItems.getValue();
    for (int i = 0; i < countItems.getValue(); i++) {
      Item item = new Item(this, buffer, offset, i);
      offset = item.getEndOffset();
      addField(item);
    }

    offset = getExtraOffset() + offsetItemslots.getValue();
    int slotCount = 0;
    if (version.equalsIgnoreCase("V1.2")) {
      addField(new DecNumber(buffer, offset, 2, CRE_ITEM_SLOT_RIGHT_EARRING));
      addField(new DecNumber(buffer, offset + 2, 2, CRE_ITEM_SLOT_CHEST));
      addField(new DecNumber(buffer, offset + 4, 2, CRE_ITEM_SLOT_LEFT_TATTOO));
      addField(new DecNumber(buffer, offset + 6, 2, CRE_ITEM_SLOT_HAND));
      addField(new DecNumber(buffer, offset + 8, 2, CRE_ITEM_SLOT_LEFT_RING));
      addField(new DecNumber(buffer, offset + 10, 2, CRE_ITEM_SLOT_RIGHT_RING));
      addField(new DecNumber(buffer, offset + 12, 2, CRE_ITEM_SLOT_LEFT_EARRING));
      addField(new DecNumber(buffer, offset + 14, 2, CRE_ITEM_SLOT_RIGHT_TATTOO_LOWER));
      addField(new DecNumber(buffer, offset + 16, 2, CRE_ITEM_SLOT_WRIST));
      slotCount += 9;
      for (int i = 0; i < 4; i++) {
        addField(new DecNumber(buffer, offset + 18 + (i * 2), 2,
                               String.format(CRE_ITEM_SLOT_WEAPON_FMT, i+1)));
      }
      slotCount += 4;
      for (int i = 0; i < 6; i++) {
        addField(new DecNumber(buffer, offset + 26 + (i * 2), 2, String.format(CRE_ITEM_SLOT_QUIVER_FMT, i+1)));
      }
      addField(new DecNumber(buffer, offset + 38, 2, CRE_ITEM_SLOT_RIGHT_TATTOO_UPPER));
      slotCount += 7;
      for (int i = 0; i < 5; i++) {
        addField(new DecNumber(buffer, offset + 40 + (i * 2), 2, String.format(CRE_ITEM_SLOT_QUICK_FMT, i+1)));
      }
      slotCount += 5;
      for (int i = 0; i < 20; i++) {
        addField(new DecNumber(buffer, offset + 50 + (i * 2), 2, String.format(CRE_ITEM_SLOT_INVENTORY_FMT, i+1)));
      }
      slotCount += 20;
      addField(new DecNumber(buffer, offset + 90, 2, CRE_ITEM_SLOT_MAGIC_WEAPON));
      addField(new DecNumber(buffer, offset + 92, 2, CRE_SELECTED_WEAPON_SLOT));
      addField(new DecNumber(buffer, offset + 94, 2, CRE_SELECTED_WEAPON_ABILITY));
      slotCount += 3;
    }
    else {
      if (Profile.getGame() == Profile.Game.PSTEE) {
        // REMEMBER: ITMSLOTS.2DA can be used as reference for item slot layout
        addField(new DecNumber(buffer, offset, 2, CRE_ITEM_SLOT_LEFT_EARRING));
        addField(new DecNumber(buffer, offset + 2, 2, CRE_ITEM_SLOT_CHEST));
        addField(new DecNumber(buffer, offset + 4, 2, CRE_ITEM_SLOT_RIGHT_TATTOO_LOWER));
        addField(new DecNumber(buffer, offset + 6, 2, CRE_ITEM_SLOT_HAND));
        addField(new DecNumber(buffer, offset + 8, 2, CRE_ITEM_SLOT_RIGHT_RING));
        addField(new DecNumber(buffer, offset + 10, 2, CRE_ITEM_SLOT_LEFT_RING));
        addField(new DecNumber(buffer, offset + 12, 2, CRE_ITEM_SLOT_RIGHT_EARRING));
        addField(new DecNumber(buffer, offset + 14, 2, CRE_ITEM_SLOT_LEFT_TATTOO));
        addField(new DecNumber(buffer, offset + 16, 2, CRE_ITEM_SLOT_WRIST));
        slotCount += 9;
      } else {
        addField(new DecNumber(buffer, offset, 2, CRE_ITEM_SLOT_HELMET));
        addField(new DecNumber(buffer, offset + 2, 2, CRE_ITEM_SLOT_ARMOR));
        addField(new DecNumber(buffer, offset + 4, 2, CRE_ITEM_SLOT_SHIELD));
        addField(new DecNumber(buffer, offset + 6, 2, CRE_ITEM_SLOT_GLOVES));
        addField(new DecNumber(buffer, offset + 8, 2, CRE_ITEM_SLOT_LEFT_RING));
        addField(new DecNumber(buffer, offset + 10, 2, CRE_ITEM_SLOT_RIGHT_RING));
        addField(new DecNumber(buffer, offset + 12, 2, CRE_ITEM_SLOT_AMULET));
        addField(new DecNumber(buffer, offset + 14, 2, CRE_ITEM_SLOT_BELT));
        addField(new DecNumber(buffer, offset + 16, 2, CRE_ITEM_SLOT_BOOTS));
        slotCount += 9;
      }
      for (int i = 0; i < 4; i++) {
        addField(new DecNumber(buffer, offset + 18 + (i * 2), 2, String.format(CRE_ITEM_SLOT_WEAPON_FMT, i+1)));
        slotCount++;
      }
      for (int i = 0; i < 4; i++) {
        addField(new DecNumber(buffer, offset + 26 + (i * 2), 2, String.format(CRE_ITEM_SLOT_QUIVER_FMT, i+1)));
        slotCount++;
      }
      if (Profile.getGame() == Profile.Game.PSTEE) {
        addField(new DecNumber(buffer, offset + 34, 2, CRE_ITEM_SLOT_RIGHT_TATTOO_UPPER));
        slotCount++;
      } else {
        addField(new DecNumber(buffer, offset + 34, 2, CRE_ITEM_SLOT_CLOAK));
        slotCount++;
      }
      for (int i = 0; i < 3; i++) {
        addField(new DecNumber(buffer, offset + 36 + (i * 2), 2, String.format(CRE_ITEM_SLOT_QUICK_FMT, i+1)));
        slotCount++;
      }
      for (int i = 0; i < 16; i++) {
        addField(new DecNumber(buffer, offset + 42 + (i * 2), 2, String.format(CRE_ITEM_SLOT_INVENTORY_FMT, i+1)));
        slotCount++;
      }
      addField(new DecNumber(buffer, offset + 74, 2, CRE_ITEM_SLOT_MAGIC_WEAPON));
      slotCount++;
      StructEntry se = getAttribute(CRE_NUM_ITEM_SLOTS);
      int maxSlotCount = 0;
      if (se instanceof IsNumeric) {
        maxSlotCount = ((IsNumeric)getAttribute(CRE_NUM_ITEM_SLOTS)).getValue();
      }
      if (Profile.getGame() == Profile.Game.PSTEE && slotCount + 8 < maxSlotCount) {
        // registered characters gain additional item slots
        int idxUnused = 1;
        addField(new DecNumber(buffer, offset + 76, 2, String.format(CRE_ITEM_SLOT_QUIVER_FMT, 5)));
        addField(new DecNumber(buffer, offset + 78, 2, String.format(CRE_ITEM_SLOT_UNUSED_FMT, idxUnused++)));
        addField(new DecNumber(buffer, offset + 80, 2, String.format(CRE_ITEM_SLOT_QUICK_FMT, 4)));
        addField(new DecNumber(buffer, offset + 82, 2, String.format(CRE_ITEM_SLOT_QUICK_FMT, 5)));
        slotCount += 4;
        offset += 84;
        for (int i = 0; i < 4; i++) {
          addField(new DecNumber(buffer, offset, 2, String.format(CRE_ITEM_SLOT_INVENTORY_FMT, i+17)));
          slotCount++;
          offset += 2;
        }
        // filling the gap with placeholder slots
        for (int i = 0, imax = maxSlotCount - slotCount - 1; i < imax; i++) {
          addField(new DecNumber(buffer, offset, 2, String.format(CRE_ITEM_SLOT_UNUSED_FMT, idxUnused++)));
          slotCount++;
          offset += 2;
        }
        addField(new DecNumber(buffer, offset, 2, CRE_SELECTED_WEAPON_SLOT));
        offset += 2;
        addField(new DecNumber(buffer, offset, 2, CRE_SELECTED_WEAPON_ABILITY));
        offset += 2;
      } else {
        addField(new DecNumber(buffer, offset + 76, 2, CRE_SELECTED_WEAPON_SLOT));
        addField(new DecNumber(buffer, offset + 78, 2, CRE_SELECTED_WEAPON_ABILITY));
        offset += 80;
      }
    }
    int endoffset = offset;
    for (int i = 0; i < getFieldCount(); i++) {
      StructEntry entry = getField(i);
      if (entry.getOffset() + entry.getSize() > endoffset) {
        endoffset = entry.getOffset() + entry.getSize();
      }
    }
    return endoffset;
  }

  private void updateMemorizedSpells()
  {
    // Assumes memorized spells offset is correct
    int offset = ((HexNumber)getAttribute(CRE_OFFSET_MEMORIZED_SPELLS)).getValue() + getExtraOffset();
    int count = 0;
    for (int i = 0; i < getFieldCount(); i++) {
      Object o = getField(i);
      if (o instanceof SpellMemorization) {
        SpellMemorization info = (SpellMemorization)o;
        int numSpells = info.updateSpells(offset, count);
        offset += 12 * numSpells;
        count += numSpells;
      }
    }
    ((DecNumber)getAttribute(CRE_NUM_MEMORIZED_SPELLS)).setValue(count);
  }

  private void updateOffsets(AddRemovable datatype, int size)
  {
    if (getField(0).toString().equalsIgnoreCase("CHR "))
      ((HexNumber)getAttribute(CHR_CRE_SIZE)).incValue(size);
//    if (!(datatype instanceof MemorizedSpells)) {
//      HexNumber offsetMemSpells = (HexNumber)getAttribute("Memorized spells offset");
//      if (datatype.getOffset() < offsetMemSpells.getValue() + getExtraOffset() ||
//          datatype.getOffset() == offsetMemSpells.getValue() + getExtraOffset() && size > 0)
//        offsetMemSpells.incValue(size);
//    }
  }


  // Adds or updates CRE color fields, returns offset behind last color field
  private int setColorFieldsPSTEE(int animId, ByteBuffer buffer, int startOffset, boolean update)
  {
    int[] colorTypes = new int[7];
    if (animId >= 0xf000 && animId < 0x10000) {
      // determine color types
      IniMap iniMap = IniMapCache.get(Integer.toHexString(animId) + ".INI");
      if (iniMap != null) {
        IniMapSection iniSection = iniMap.getSection("monster_planescape");
        if (iniSection != null) {
          IniMapEntry iniEntry = iniSection.getEntry("clown");
          boolean isClown = (iniEntry != null && Misc.toNumber(iniEntry.getValue(), 0) > 0);
          iniEntry = null;
          for (int i = 0; i < colorTypes.length; i++) {
            if (isClown) {
              iniEntry = iniSection.getEntry("color" + (i+1));
            }
            if (iniEntry != null) {
              colorTypes[i] = Misc.toNumber(iniEntry.getValue(), -1) >> 4;
            } else {
              colorTypes[i] = -1;
            }
          }
        }
      }
    }

    // generate color type names
    String[] colorNames = new String[colorTypes.length];
    IdsMap idsMap = IdsMapCache.get("CLOWNRGE.IDS");
    if (idsMap != null) {
      for (int i = 0; i < colorTypes.length; i++) {
        if (colorTypes[i] > 0) {
          IdsMapEntry idsEntry = idsMap.get(colorTypes[i]);
          if (idsEntry != null) {
            colorNames[i] = Misc.prettifySymbol(idsEntry.getSymbol()) + " color";
          } else {
            colorNames[i] = "Type " + Integer.toHexString(colorTypes[i]).toUpperCase() + " color";
          }
        } else {
          colorNames[i] = "Unused color";
        }
      }
    }

    // add or update color fields
    for (int i = 0; i < colorTypes.length; i++) {
      if (update) {
        StructEntry field = getAttribute(startOffset);
        if (field instanceof ColorValue) {
          field.setName(colorNames[i]);
        }
        startOffset += field.getSize();
      } else {
        addField(new ColorValue(buffer, startOffset, 1, colorNames[i], "PAL32.BMP"));
        startOffset++;
      }
    }

    return startOffset;
  }

  //--------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent event)
  {
    if (event.getSource() == bExport) {
      JMenuItem item = bExport.getSelectedItem();
      if (item == miExport) {
        ResourceFactory.exportResource(getResourceEntry(), NearInfinity.getInstance());
      } else if (item == miConvert) {
        convertCHRtoCRE(getResourceEntry());
      }
    }
  }

//--------------------- End Interface ItemListener ---------------------

//--------------------- Begin Interface UpdateListener ---------------------

  @Override
  public boolean valueUpdated(UpdateEvent event)
  {
    // TODO: Listener queue fills with duplicate entries with each click on AnimateBitmap's "Update" button
//    boolean retVal = false;
//    if (event.getSource() instanceof AnimateBitmap) {
//      AnimateBitmap animate = (AnimateBitmap)event.getSource();
//      retVal = setColorFieldsPSTEE(animate.getValue(), null, 0x2c, true) > 0;
//    }
//    return retVal;
    return false;
  }

//--------------------- End Interface UpdateListener ---------------------


  // Called by "Extended Search"
  // Checks whether the specified resource entry matches all available search options.
  public static boolean matchSearchOptions(ResourceEntry entry, SearchOptions searchOptions)
  {
    if (entry != null && searchOptions != null) {
      try {
        CreResource cre = new CreResource(entry);
        AbstractStruct[] effects;
        AbstractStruct[] items;
        Datatype[] spells;
        boolean retVal = true;
        String key;
        Object o;

        // preparing substructures
        DecNumber ofs = (DecNumber)cre.getAttribute(CRE_OFFSET_EFFECTS, false);
        DecNumber cnt = (DecNumber)cre.getAttribute(CRE_NUM_EFFECTS, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          effects = new AbstractStruct[cnt.getValue()];
          for (int idx = 0; idx < cnt.getValue(); idx++) {
            String label = String.format(SearchOptions.getResourceName(SearchOptions.CRE_Effect), idx);
            effects[idx] = (AbstractStruct)cre.getAttribute(label, false);
          }
        } else {
          effects = new AbstractStruct[0];
        }

        ofs = (DecNumber)cre.getAttribute(CRE_OFFSET_ITEMS, false);
        cnt = (DecNumber)cre.getAttribute(CRE_NUM_ITEMS, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          items = new AbstractStruct[cnt.getValue()];
          for (int idx = 0; idx < cnt.getValue(); idx++) {
            String label = String.format(SearchOptions.getResourceName(SearchOptions.CRE_Item), idx);
            items[idx] = (AbstractStruct)cre.getAttribute(label, false);
          }
        } else {
          items = new AbstractStruct[0];
        }

        if (Profile.getEngine() == Profile.Engine.IWD2) {
          final String[] spellTypes = new String[]{
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellBard),
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellCleric),
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellDruid),
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellPaladin),
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellRanger),
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellSorcerer),
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellWizard),
              SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellDomain)};
          final String spellTypesStruct = SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellBard_Spell);
          final String spellTypesRef = SearchOptions.getResourceName(SearchOptions.CRE_IWD2SpellBard_Spell_ResRef);
          List<Datatype> listSpells = new ArrayList<Datatype>(64);
          for (int i = 0; i < spellTypes.length; i++) {
            for (int j = 1; j < 10; j++) {
              String label = String.format(spellTypes[i], j);
              AbstractStruct struct1 = (AbstractStruct)cre.getAttribute(label, false);
              if (struct1 != null) {
                AbstractStruct struct2 = (AbstractStruct)struct1.getAttribute(spellTypesStruct, false);
                if (struct2 != null) {
                  Datatype struct3 = (Datatype)struct2.getAttribute(spellTypesRef, false);
                  if (struct3 != null) {
                    listSpells.add(struct3);
                  }
                }
              }
            }
          }
          spells = new Datatype[listSpells.size()];
          for (int i = 0; i < spells.length; i++) {
            spells[i] = listSpells.get(i);
          }
        } else {
          ofs = (DecNumber)cre.getAttribute(CRE_OFFSET_KNOWN_SPELLS, false);
          cnt = (DecNumber)cre.getAttribute(CRE_NUM_KNOWN_SPELLS, false);
          if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
            spells = new Datatype[cnt.getValue()];
            final String spellLabel = SearchOptions.getResourceName(SearchOptions.CRE_Spell_Spell1);
            for (int idx = 0; idx < cnt.getValue(); idx++) {
              String label = String.format(SearchOptions.getResourceName(SearchOptions.CRE_Spell), idx);
              AbstractStruct struct = (AbstractStruct)cre.getAttribute(label, false);
              spells[idx] = (Datatype)struct.getAttribute(spellLabel, false);
            }
          } else {
            spells = new Datatype[0];
          }
        }

        // checking options
        String[] keyList = new String[]{SearchOptions.CRE_Name, SearchOptions.CRE_ScriptName};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            key = keyList[idx];
            o = searchOptions.getOption(key);
            StructEntry struct = cre.getAttribute(SearchOptions.getResourceName(key), false);
            retVal &= SearchOptions.Utils.matchString(struct, o, false, false);
          } else {
            break;
          }
        }

        keyList = new String[]{SearchOptions.CRE_Script1, SearchOptions.CRE_Script2};
        String[] scriptFields;
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          scriptFields = new String[]{CRE_SCRIPT_TEAM, CRE_SCRIPT_SPECIAL_1, CRE_SCRIPT_OVERRIDE,
                                      CRE_SCRIPT_SPECIAL_2, CRE_SCRIPT_COMBAT, CRE_SCRIPT_SPECIAL_3,
                                      CRE_SCRIPT_MOVEMENT};
        } else {
          scriptFields = new String[]{CRE_SCRIPT_OVERRIDE, CRE_SCRIPT_CLASS, CRE_SCRIPT_RACE,
                                      CRE_SCRIPT_GENERAL, CRE_SCRIPT_DEFAULT};
        }
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            boolean found = false;
            key = keyList[idx];
            o = searchOptions.getOption(key);
            for (int idx2 = 0; idx2 < scriptFields.length; idx2++) {
              StructEntry struct = cre.getAttribute(scriptFields[idx2], false);
              found |= SearchOptions.Utils.matchResourceRef(struct, o, false);
            }
            retVal &= found;
          }
        }

        keyList = new String[]{SearchOptions.CRE_Flags, SearchOptions.CRE_Feats1,
                               SearchOptions.CRE_Feats2, SearchOptions.CRE_Feats3,
                               SearchOptions.CRE_Attributes};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            key = keyList[idx];
            o = searchOptions.getOption(key);
            StructEntry struct = cre.getAttribute(SearchOptions.getResourceName(key), false);
            retVal &= SearchOptions.Utils.matchFlags(struct, o);
          } else {
            break;
          }
        }

        keyList = new String[]{SearchOptions.CRE_Animation, SearchOptions.CRE_General,
                               SearchOptions.CRE_Class, SearchOptions.CRE_Specifics,
                               SearchOptions.CRE_Alignment, SearchOptions.CRE_Gender,
                               SearchOptions.CRE_Sex, SearchOptions.CRE_Race,
                               SearchOptions.CRE_Allegiance, SearchOptions.CRE_Kit,
                               SearchOptions.CRE_Level1, SearchOptions.CRE_Level2, SearchOptions.CRE_Level3,
                               SearchOptions.CRE_IWD2LevelTotal, SearchOptions.CRE_IWD2LevelBarbarian,
                               SearchOptions.CRE_IWD2LevelBard, SearchOptions.CRE_IWD2LevelCleric,
                               SearchOptions.CRE_IWD2LevelDruid, SearchOptions.CRE_IWD2LevelFighter,
                               SearchOptions.CRE_IWD2LevelMonk, SearchOptions.CRE_IWD2LevelPaladin,
                               SearchOptions.CRE_IWD2LevelRanger, SearchOptions.CRE_IWD2LevelRogue,
                               SearchOptions.CRE_IWD2LevelSorcerer, SearchOptions.CRE_IWD2LevelWizard};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            key = keyList[idx];
            o = searchOptions.getOption(key);
            StructEntry struct = cre.getAttribute(SearchOptions.getResourceName(key), false);
            retVal &= SearchOptions.Utils.matchNumber(struct, o);
          } else {
            break;
          }
        }

        keyList = new String[]{SearchOptions.CRE_Effect_Type1, SearchOptions.CRE_Effect_Type2,
                               SearchOptions.CRE_Effect_Type3, SearchOptions.CRE_Effect_Type4};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            boolean found = false;
            key = keyList[idx];
            o = searchOptions.getOption(key);
            for (int idx2 = 0; idx2 < effects.length; idx2++) {
              if (!found) {
                if (effects[idx2] != null) {
                  StructEntry struct = effects[idx2].getAttribute(SearchOptions.getResourceName(key), false);
                  found |= SearchOptions.Utils.matchNumber(struct, o);
                }
              } else {
                break;
              }
            }
            retVal &= found || (o == null);
          } else {
            break;
          }
        }

        keyList = new String[]{SearchOptions.CRE_Item_Item1, SearchOptions.CRE_Item_Item2,
                               SearchOptions.CRE_Item_Item3, SearchOptions.CRE_Item_Item4};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            boolean found = false;
            key = keyList[idx];
            o = searchOptions.getOption(key);
            for (int idx2 = 0; idx2 < items.length; idx2++) {
              if (!found) {
                if (items[idx2] != null) {
                  StructEntry struct = items[idx2].getAttribute(SearchOptions.getResourceName(key), false);
                  found |= SearchOptions.Utils.matchResourceRef(struct, o, false);
                }
              } else {
                break;
              }
            }
            retVal &= found || (o == null);
          } else {
            break;
          }
        }

        keyList = new String[]{SearchOptions.CRE_Spell_Spell1, SearchOptions.CRE_Spell_Spell2,
                               SearchOptions.CRE_Spell_Spell3, SearchOptions.CRE_Spell_Spell4};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            boolean found = false;
            key = keyList[idx];
            o = searchOptions.getOption(key);
            for (int idx2 = 0; idx2 < spells.length; idx2++) {
              if (!found) {
                if (spells[idx2] != null) {
                  found |= SearchOptions.Utils.matchResourceRef(spells[idx2], o, false);
                }
              } else {
                break;
              }
            }
            retVal &= found || (o == null);
          } else {
            break;
          }
        }

        keyList = new String[]{SearchOptions.CRE_Custom1, SearchOptions.CRE_Custom2,
                               SearchOptions.CRE_Custom3, SearchOptions.CRE_Custom4};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            key = keyList[idx];
            o = searchOptions.getOption(key);
            retVal &= SearchOptions.Utils.matchCustomFilter(cre, o);
          } else {
            break;
          }
        }

        return retVal;
      } catch (Exception e) {
      }
    }
    return false;
  }
}
