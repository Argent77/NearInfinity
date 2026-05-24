// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.EffectType;
import org.infinity.datatype.Flag;
import org.infinity.datatype.HashBitmap;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.UnsignDecNumber;
import org.infinity.datatype.UpdateListener;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Effect2;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;
import org.infinity.util.Logger;
import org.infinity.util.StringTable;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.ByteBufferOutputStream;
import org.infinity.util.io.StreamUtils;

/**
 * Base class for opcode-handling classes. Derived classes should handle one opcode per class for all game variants.
 */
public class BaseOpcode {
  // Total number of opcode definitions. Used internally for optimization purposes.
  private static final int NUM_OPCODES = 460;

  // Effect-specific field labels (generic)
  public static final String EFFECT_PARAMETER_1           = "Parameter 1";
  public static final String EFFECT_PARAMETER_2           = "Parameter 2";
  public static final String EFFECT_SPECIAL               = "Special";
  public static final String EFFECT_IDENTIFIER            = "Identifier";
  public static final String EFFECT_PREFIX                = "Prefix";
  public static final String EFFECT_TIMING_MODE           = "Timing mode";
  public static final String EFFECT_DURATION              = "Duration";
  public static final String EFFECT_PROBABILITY_1         = "Probability 1";
  public static final String EFFECT_PROBABILITY_2         = "Probability 2";
  public static final String EFFECT_STRING                = "String";
  public static final String EFFECT_RESOURCE              = "Resource";
  public static final String EFFECT_DICE_COUNT_MAX_LEVEL  = "# dice thrown/maximum level";
  public static final String EFFECT_DICE_SIZE_MIN_LEVEL   = "Dice size/minimum level";
  public static final String EFFECT_SAVE_TYPE             = "Save type";
  public static final String EFFECT_SAVE_PENALTY          = "Save penalty";
  public static final String EFFECT_SAVE_BONUS            = "Save bonus";
  public static final String EFFECT_PARAMETER             = "Parameter?";
  public static final String EFFECT_DICE_COUNT            = "# dice thrown";
  public static final String EFFECT_DICE_SIZE             = "Dice size";

  // Effect-specific field labels (commonly used)
  protected static final String EFFECT_AMOUNT             = "Amount";
  protected static final String EFFECT_COLOR              = "Color";
  protected static final String EFFECT_ICON               = "Icon";
  protected static final String EFFECT_LOCATION           = "Location";
  protected static final String EFFECT_MODE               = "Mode";
  protected static final String EFFECT_MODIFIER_TYPE      = "Modifier type";
  protected static final String EFFECT_STAT_VALUE         = "Stat value";
  protected static final String EFFECT_TYPE               = "Type";
  protected static final String EFFECT_VALUE              = "Value";

  /** Resource type for raw strings. */
  protected static final String RES_TYPE_STRING           = "String";

  /** The string {@code "Default"} for general use. */
  protected static final String STRING_DEFAULT            = "Default";

  /**
   * Used in conjunction with {@code getEffectStructure} to address specific fields within an effect structure.
   */
  public enum EffectEntry {
    // EFF all versions
    // table index            abs. structure offset
    IDX_OPCODE,               OFS_OPCODE,
    IDX_TARGET,               OFS_TARGET,
    IDX_POWER,                OFS_POWER,
    IDX_PARAM1,               OFS_PARAM1,
    IDX_PARAM1A,              OFS_PARAM1A,
    IDX_PARAM1B,              OFS_PARAM1B,
    IDX_PARAM2,               OFS_PARAM2,
    IDX_PARAM2A,              OFS_PARAM2A,
    IDX_PARAM2B,              OFS_PARAM2B,
    IDX_TIMING,               OFS_TIMING,
    IDX_RESISTANCE,           OFS_RESISTANCE,
    IDX_DURATION,             OFS_DURATION,
    IDX_PROBABILITY1,         OFS_PROBABILITY1,
    IDX_PROBABILITY2,         OFS_PROBABILITY2,
    IDX_RESOURCE,             OFS_RESOURCE,
    IDX_DICETHROWN,           OFS_DICETHROWN,
    IDX_DICESIDES,            OFS_DICESIDES,
    IDX_SAVETYPE,             OFS_SAVETYPE,
    IDX_SAVEBONUS,            OFS_SAVEBONUS,
    IDX_SPECIAL,              OFS_SPECIAL,
    // EFF V2.0 only
    // table index            abs. structure offset
    IDX_PRIMARYTYPE,          OFS_PRIMARYTYPE,
    IDX_UNKNOWN040,           OFS_UNKNOWN040,
    IDX_PARENTLOWESTLEVEL,    OFS_PARENTLOWESTLEVEL,
    IDX_PARENTHIGHESTLEVEL,   OFS_PARENTHIGHESTLEVEL,
    IDX_PARAM3,               OFS_PARAM3,
    IDX_PARAM4,               OFS_PARAM4,
    IDX_PARAM5,               OFS_PARAM5,
    IDX_TIMEAPPLIED,          OFS_TIMEAPPLIED,
    IDX_RESOURCE2,            OFS_RESOURCE2,
    IDX_RESOURCE3,            OFS_RESOURCE3,
    IDX_CASTERX,              OFS_CASTERX,
    IDX_CASTERY,              OFS_CASTERY,
    IDX_TARGETX,              OFS_TARGETX,
    IDX_TARGETY,              OFS_TARGETY,
    IDX_PARENTRESOURCETYPE,   OFS_PARENTRESOURCETYPE,
    IDX_PARENTRESOURCE,       OFS_PARENTRESOURCE,
    IDX_PARENTRESOURCEFLAGS,  OFS_PARENTRESOURCEFLAGS,
    IDX_PROJECTILE,           OFS_PROJECTILE,
    IDX_PARENTRESOURCESLOT,   OFS_PARENTRESOURCESLOT,
    IDX_VARIABLE,             OFS_VARIABLE,
    IDX_CASTERLEVEL,          OFS_CASTERLEVEL,
    IDX_FIRSTAPPLY,           OFS_FIRSTAPPLY,
    IDX_SECONDARYTYPE,        OFS_SECONDARYTYPE,
    IDX_UNKNOWN0C4,           OFS_UNKNOWN0C4,
  }

  public static final TreeMap<Long, String> DURATIONS_V1_MAP = new TreeMap<>();
  public static final TreeMap<Long, String> DURATIONS_V2_MAP = new TreeMap<>();
  public static final TreeMap<Long, String> AC_TYPES_MAP = new TreeMap<>();
  public static final TreeMap<Long, String> COLOR_LOCATIONS_MAP = new TreeMap<>();
  public static final TreeMap<Long, String> PROJECTILES_IWD_MAP = new TreeMap<>();
  public static final TreeMap<Long, String> INC_TYPES_MAP = new TreeMap<>();
  public static final TreeMap<Long, String> ATTACKS_EE_MAP = new TreeMap<>();

  public static final String[] INC_TYPES = { "Increment", "Set", "Set % of" };

  public static final String[] BUTTON_TYPES = {
      "Unknown", "Unknown", "Bard Song", "Cast Spell", "Find Traps",
      "Talk", "Unknown", "Guard", "Attack", "Unknown",
      "Special Abilities", "Stealth", "Thieving", "Turn Undead", "Use Item",
      "Stop", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Quick Item 1", "Quick Item 2", "Quick Item 3", "Quick Spell 1",
      "Quick Spell 2", "Quick Spell 3", "Quick Weapon 1", "Quick Weapon 2", "Quick Weapon 3",
      "Quick Weapon 4", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "Unknown", "Unknown", "Unknown", "Unknown", "Unknown",
      "None"
  };

  public static final String[] VISUALS = {
      // 0..9
      "None", "Hit abjuration", "Hit alteration", "Hit invocation", "Hit necromancy", "Hit conjuration",
      "Hit enchantment", "Hit illusion", "Hit divination", "Armor",
      // 10..19
      "Spirit armor", "Ghost armor", "Strength", "Confusion", "Shroud of flame", "Death spell",
      "Disintegrate", "Power word, silence", "Power word, stun", "Finger of death",
      // 20..29
      "Mordenkainen's sword", "Monster summoning 1", "Monster summoning 2", "Monster summoning 3",
      "Monster summoning 4", "Monster summoning 5", "Monster summoning 6", "Monster summoning 7",
      "Conjure fire elemental", "Conjure earth elemental",
      // 30..39
      "Conjure water elemental", "Bless", "Curse", "Prayer", "Recitation", "Cure light wounds",
      "Cure moderate wounds", "Cure serious wounds", "Cure critical wounds", "Heal",
      // 40..49
      "Animal summoning 1", "Animal summoning 2", "Animal summoning 3", "Slow poison",
      "Neutralize poison", "Call lightning", "Static charge", "Remove paralysis", "Free action",
      "Miscast magic",
      // 50..59
      "Strength of one", "Champion's strength", "Flame strike", "Raise dead", "Resurrection",
      "Chaotic commands", "Righteous wrath of the faithful", "Sunray", "Spike stones",
      "Dimension door departure",
      // 60..69
      "Dimension door arrival", "Cone of cold", "Sol's searing orb", "Hit fire", "Hit cold",
      "Hit electricity", "Hit acid", "Hit paralysis", "Malavon's rage",
      "Righteous wrath of the faithful ground",
      // 70..79
      "Belhifet death", "Portal", "Sunscorch", "Blade barrier (front)", "Blade barrier (back)",
      "Circle of bones (front)", "Circle of bones (back)", "Cause light wounds",
      "Cause moderate wounds", "Cause serious wounds",
      // 80..89
      "Cause critical wounds", "Cause disease", "Hit poison", "Slay living", "Harm", "Destruction",
      "Exaltation", "Cloudburst", "Mold touch", "Lower resistance",
      // 90..99
      "Cat's grace", "Soul eater", "Smashing wave", "Suffocate", "Abi-dalzim's horrid wilting",
      "Mordenkainen's force missiles", "Vitriolic sphere", "Wailing virgin death", "Undead ward",
      "Wailing virgin hit",
      // 100..109
      "Wylfden's death 1", "Wylfden's death 2", "Dragon's death 1", "Dragon's death 2",
      "Monster summoning circle", "Animal summoning circle", "Earth summoning circle",
      "Fire summoning circle", "Water summoning circle", "Gedlee's electric loop",
      // 110...
      "Darktree attack"
  };

  public static final String[] LIGHTING = {
      // 0..9
      "Necromancy air", "Necromancy earth", "Necromancy water", "", "Alteration air",
      "Alteration earth", "Alteration water", "", "Enchantment air", "Enchantment earth",
      // 10..19
      "Enchantment water", "", "Abjuration air", "Abjuration earth", "Abjuration water", "",
      "Illusion air", "Illusion earth", "Illusion water", "",
      // 20..29
      "Conjure air", "Conjure earth", "Conjure water", "", "Invocation air", "Invocation earth",
      "Invocation water", "", "Divination air", "Divination earth",
      // 30..39
      "Divination water", "", "Mushroom fire", "Mushroom gray", "Mushroom green", "Shaft fire",
      "Shaft light", "Shaft white", "Hit door", "Hit finger of death"
  };

  public static final String[] CRE_TYPES_IWD = {
      // 0..9
      "Anyone", "Undead", "Not undead", "Fire-dwelling", "Not fire-dwelling", "Humanoid",
      "Not humanoid", "Animal", "Not animal", "Elemental",
      // 10..19
      "Not elemental", "Fungus", "Not fungus", "Huge creature", "Not huge creature", "Elf", "Not elf",
      "Umber hulk", "Not umber hulk", "Half-elf",
      // 20..29
      "Not half-elf", "Humanoid or animal", "Not humanoid or animal", "Blind", "Not blind",
      "Cold-dwelling", "Not cold-dwelling", "Golem", "Not golem", "Minotaur",
      // 30..39
      "Not minotaur", "Undead or fungus", "Not undead or fungus", "Good", "Not good", "Neutral",
      "Not neutral", "Evil", "Not evil", "Paladin",
      // 40..49
      "Not paladin", "Same moral alignment as source", "Not same moral alignment as source", "Source",
      "Not source", "Water-dwelling", "Not water-dwelling", "Breathing", "Not breathing", "Allies",
      // 50..59
      "Not allies", "Enemies", "Not enemies", "Fire or cold dwelling", "Not fire or cold dwelling",
      "Unnatural", "Not unnatural", "Male", "Not male", "Lawful",
      // 60..69
      "Not lawful", "Chaotic", "Not chaotic", "Evasion check", "Orc", "Not orc", "Deaf", "Not deaf",
      "Summoned creature", "Not summoned creature",
      // 70..79
      "Mind flayer", "Not mind flayer", "Silenced", "Not silenced", "Intelligence less than",
      "Intelligence greater than", "Intelligence less than or equal to",
      "Intelligence greater than or equal to", "Skald", "Not skald",
      // 80..89
      "Near enemies", "Not near enemies", "Drow", "Not drow", "Gray dwarf", "Not gray dwarf",
      "Daytime", "Not daytime", "Outdoor", "Not outdoor",
      // 90..
      "Keg", "Not keg", "Outsider", "Not outsider"
  };

  public static final String[] CRE_TYPES_IWD2 = {
      // 0..9
      "Anyone", "Undead", "Not undead", "Fire-dwelling", "Not fire-dwelling", "Humanoid",
      "Not humanoid", "Animal", "Not animal", "Elemental",
      // 10..19
      "Not elemental", "Fungus", "Not fungus", "Huge creature", "Not huge creature", "Elf", "Not elf",
      "Umber hulk", "Not umber hulk", "Half-elf",
      // 20..29
      "Not half-elf", "Humanoid or animal", "Not humanoid or animal", "Blind", "Not blind",
      "Cold-dwelling", "Not cold-dwelling", "Golem", "Not golem", "Minotaur",
      // 30..39
      "Not minotaur", "Undead or fungus", "Not undead or fungus", "Good", "Not good", "Neutral",
      "Not neutral", "Evil", "Not evil", "Paladin",
      // 40..49
      "Not paladin", "Same moral alignment as source", "Not same moral alignment as source", "Source",
      "Not source", "Water-dwelling", "Not water-dwelling", "Breathing", "Not breathing", "Allies",
      // 50..59
      "Not allies", "Enemies", "Not enemies", "Fire or cold dwelling", "Not fire or cold dwelling",
      "Unnatural", "Not unnatural", "Male", "Not male", "Lawful",
      // 60..69
      "Not lawful", "Chaotic", "Not chaotic", "Orc", "Not orc", "Deaf", "Not deaf", "Same alignment",
      "Not same alignment", "Allied and same alignment",
      // 70..79
      "Not allied and same alignment", "HP greater than value", "HP less than value", "Drow", "Not drow",
      "Gray dwarf", "Not gray dwarf", "Summoned creature", "Not summoned creature", "Goblin",
      // 80..89
      "Not goblin", "Giant", "Not giant", "Troll", "Not troll", "Keg", "Not keg", "Human", "Not human",
      "Yuan-ti",
      // 90..
      "Not yuan-ti", "Outsider", "Not outsider"
  };

  public static final String[] SUMMON_ANIMS = {
      "No animation", "Monster summoning circle", "Animal summoning circle",
      "Earth summoning circle", "Fire summoning circle", "Water summoning circle", "", "Puff of smoke"
  };

  public static final String[] SPARKLE_COLORS = {
      "", "Black", "Blue", "Chromatic", "Gold", "Green", "Purple", "Red", "White", "Ice", "Stone", "Magenta", "Orange"
  };

  public static final String[] DAMAGE_TYPES = {
      "All", "Fire damage", "Cold damage", "Electricity damage", "Acid damage", "Magic damage", "Poison damage",
      "Slashing damage", "Piercing damage", "Crushing damage", "Missile damage"
  };

  public static final String[] BUTTONS = {
      "Stealth", "Thieving", "Spell select", "Quick spell 1", "Quick spell 2", "Quick spell 3", "Turn undead", "Talk",
      "Use item", "Quick item 1", "", "Quick item 2", "Quick item 3", "Special abilities"
  };

  public static final String[] BUTTONS_IWD2 = {
      "Stealth", "Thieving", "Cast spell", "Quick spell 0", "Quick spell 1", "Quick spell 2", "Quick spell 3",
      "Quick spell 4", "Quick spell 5", "Quick spell 6", "Quick spell 7", "Quick spell 8", "Bard song", "Quick song 0",
      "Quick song 1", "Quick song 2", "Quick song 3", "Quick song 4", "Quick song 5", "Quick song 6", "Quick song 7",
      "Quick song 8", "Quick skill 0", "Quick skill 1", "Quick skill 2", "Quick skill 3", "Quick skill 4",
      "Quick skill 5", "Quick skill 6", "Quick skill 7", "Quick skill 8"
  };

  public static final String[] ATTACKS = {
      "0 attacks per round", "1 attack per round",
      "2 attacks per round", "3 attacks per round",
      "4 attacks per round", "5 attacks per round",
      "0.5 attack per round", "1.5 attacks per round",
      "2.5 attacks per round", "3.5 attacks per round",
      "4.5 attacks per round"
  };

  public static final String[] SUMMON_CONTROLS = {
      "Match target (position=effect)", "Match target (position=effect)", "From CRE file (position=effect)",
      "Match target (position=target)", "From CRE file (position=target)", "Opposite of target (position=effect)"
  };

  public static final String[] REGEN_TYPES = {
      "Amount HP per second", "Amount HP percentage per second", "Amount HP per second", "1 HP per amount seconds",
      "Parameter3 HP per amount seconds"
  };

  public static final String[] REGEN_TYPES_IWD = {
      "Amount HP per second", "Amount HP percentage per second", "Amount HP per second", "1 HP per amount seconds",
      "Amount HP per round"
  };

  public static final String[] SAVE_TYPES = {
      "No save", "Spell", "Breath weapon", "Paralyze/Poison/Death", "Rod/Staff/Wand", "Petrify/Polymorph",
      "EE: Spell*;For use with opcode 322", "EE: Breath weapon*;For use with opcode 322", "EE: Paralyze/Poison/Death*;For use with opcode 322",
      "EE: Rod/Staff/Wand*;For use with opcode 322", "EE: Petrify/Polymorph*;For use with opcode 322",
      "EE: Ignore primary target*;Line AoE projectile doesn't affect end target",
      "EE: Ignore secondary target*;Line AoE projectile doesn't affect bystanders",
      null, null, null, null, null, null, null, null, null, null, null, "EEex: Bypass opcode 101",
      "EE/Ex: Bypass mirror image*;Affects only opcodes 12 and 25", "EE: Ignore difficulty*;Affects only opcode 12"
  };

  public static final String[] SAVE_TYPES_TOBEX = {
      "No save", "Spell", "Breath weapon", "Paralyze/Poison/Death", "Rod/Staff/Wand", "Petrify/Polymorph",
      null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
      "EE/Ex: Bypass mirror image", "Ex: Limit stacking", "Ex: Suspend effect application (internal)"
  };

  public static final String[] SAVE_TYPES_IWD2 = {"No save", null, null, "Fortitude", "Reflex", "Will"};

  public static final String[] SPELL_STATES = {
      "Chaotic Command", "Miscast Magic", "Pain", "Greater Malison", "Blood Rage", "Cat's Grace", "Mold Touch",
      "Shroud of Flame"
  };

  // Global list of available opcode classes. They are used to initialize game-specific lists of opcode instances.
  private static final HashSet<Class<? extends BaseOpcode>> OPCODE_CLASSES = new HashSet<>((int)(NUM_OPCODES / .75) + 5);

  private static TreeMap<Integer, BaseOpcode> opcodeList;
  private static String[] effectNames;
  private static String[] portraitIconNames;

  static {
    DURATIONS_V1_MAP.put(0L, "Instant/Limited");
    DURATIONS_V1_MAP.put(1L, "Instant/Permanent until death");
    DURATIONS_V1_MAP.put(2L, "Instant/While equipped");
    DURATIONS_V1_MAP.put(3L, "Delay/Limited");
    DURATIONS_V1_MAP.put(4L, "Delay/Permanent");
    DURATIONS_V1_MAP.put(5L, "Delay/While equipped");
    DURATIONS_V1_MAP.put(6L, "Limited after duration");
    DURATIONS_V1_MAP.put(7L, "Permanent after duration");
    DURATIONS_V1_MAP.put(8L, "Equipped after duration");
    DURATIONS_V1_MAP.put(9L, "Instant/Permanent");
    DURATIONS_V1_MAP.put(10L, "Instant/Limited (ticks)");

    DURATIONS_V2_MAP.put(0L, "Instant/Limited");
    DURATIONS_V2_MAP.put(1L, "Instant/Permanent until death");
    DURATIONS_V2_MAP.put(2L, "Instant/While equipped");
    DURATIONS_V2_MAP.put(3L, "Delay/Limited");
    DURATIONS_V2_MAP.put(4L, "Delay/Permanent");
    DURATIONS_V2_MAP.put(5L, "Delay/While equipped");
    DURATIONS_V2_MAP.put(6L, "Limited after duration");
    DURATIONS_V2_MAP.put(7L, "Permanent after duration");
    DURATIONS_V2_MAP.put(8L, "Equipped after duration");
    DURATIONS_V2_MAP.put(9L, "Instant/Permanent");
    DURATIONS_V2_MAP.put(10L, "Instant/Limited (ticks)");
    DURATIONS_V2_MAP.put(4096L, "Absolute duration");

    AC_TYPES_MAP.put(0x0L, "All weapons");
    AC_TYPES_MAP.put(0x1L, "Crushing weapons");
    AC_TYPES_MAP.put(0x2L, "Missile weapons");
    AC_TYPES_MAP.put(0x4L, "Piercing weapons");
    AC_TYPES_MAP.put(0x8L, "Slashing weapons");
    AC_TYPES_MAP.put(0x10L, "Set base AC to value");

    COLOR_LOCATIONS_MAP.put(0x00L, "Armor (grey): Belt/Amulet");
    COLOR_LOCATIONS_MAP.put(0x01L, "Armor (teal): Minor color");
    COLOR_LOCATIONS_MAP.put(0x02L, "Armor (pink): Major color");
    COLOR_LOCATIONS_MAP.put(0x03L, "Armor (yellow): Skin color");
    COLOR_LOCATIONS_MAP.put(0x04L, "Armor (red): Strap/Leather");
    COLOR_LOCATIONS_MAP.put(0x05L, "Armor (blue): Armor/Trimming");
    COLOR_LOCATIONS_MAP.put(0x06L, "Armor (green): Hair");
    COLOR_LOCATIONS_MAP.put(0x10L, "Weapon (grey): Head/blade/staff major");
    COLOR_LOCATIONS_MAP.put(0x11L, "Weapon (teal): Staff minor");
    COLOR_LOCATIONS_MAP.put(0x12L, "Weapon (pink)");
    COLOR_LOCATIONS_MAP.put(0x13L, "Weapon (yellow)");
    COLOR_LOCATIONS_MAP.put(0x14L, "Weapon (red): Grip/staff minor");
    COLOR_LOCATIONS_MAP.put(0x15L, "Weapon (blue): Head/blade minor");
    COLOR_LOCATIONS_MAP.put(0x16L, "Weapon (green)");
    COLOR_LOCATIONS_MAP.put(0x20L, "Shield (grey): Hub");
    COLOR_LOCATIONS_MAP.put(0x21L, "Shield (teal): Interior");
    COLOR_LOCATIONS_MAP.put(0x22L, "Shield (pink): Panel");
    COLOR_LOCATIONS_MAP.put(0x23L, "Shield (yellow)");
    COLOR_LOCATIONS_MAP.put(0x24L, "Shield (red): Grip");
    COLOR_LOCATIONS_MAP.put(0x25L, "Shield (blue): Body/trim");
    COLOR_LOCATIONS_MAP.put(0x26L, "Shield (green)");
    COLOR_LOCATIONS_MAP.put(0x30L, "Helmet (grey): Wings");
    COLOR_LOCATIONS_MAP.put(0x31L, "Helmet (teal): Detail");
    COLOR_LOCATIONS_MAP.put(0x32L, "Helmet (pink): Plume");
    COLOR_LOCATIONS_MAP.put(0x33L, "Helmet (yellow)");
    COLOR_LOCATIONS_MAP.put(0x34L, "Helmet (red): Face");
    COLOR_LOCATIONS_MAP.put(0x35L, "Helmet (blue): Exterior");
    COLOR_LOCATIONS_MAP.put(0x36L, "Helmet (green)");
    COLOR_LOCATIONS_MAP.put(0xffL, "Character color");

    PROJECTILES_IWD_MAP.put(0L, "Instant");
    PROJECTILES_IWD_MAP.put(1L, "Arrow");
    PROJECTILES_IWD_MAP.put(2L, "Arrow Exploding");
    PROJECTILES_IWD_MAP.put(3L, "Arrow Flaming");
    PROJECTILES_IWD_MAP.put(4L, "Arrow Heavy*");
    PROJECTILES_IWD_MAP.put(5L, "Arrow (Non-Magical)");
    PROJECTILES_IWD_MAP.put(6L, "Axe");
    PROJECTILES_IWD_MAP.put(7L, "Axe Exploding");
    PROJECTILES_IWD_MAP.put(8L, "Axe Flaming");
    PROJECTILES_IWD_MAP.put(9L, "Axe Heavy*");
    PROJECTILES_IWD_MAP.put(10L, "Axe (Non-Magical)");
    PROJECTILES_IWD_MAP.put(11L, "Bolt");
    PROJECTILES_IWD_MAP.put(12L, "Bolt Exploding");
    PROJECTILES_IWD_MAP.put(13L, "Bolt Flaming");
    PROJECTILES_IWD_MAP.put(14L, "Bolt Heavy*");
    PROJECTILES_IWD_MAP.put(15L, "Bolt (Non-Magical)");
    PROJECTILES_IWD_MAP.put(16L, "Bullet");
    PROJECTILES_IWD_MAP.put(17L, "Bullet Exploding");
    PROJECTILES_IWD_MAP.put(18L, "Bullet Flaming");
    PROJECTILES_IWD_MAP.put(19L, "Bullet Heavy*");
    PROJECTILES_IWD_MAP.put(20L, "Bullet (Non-Magical)");
    PROJECTILES_IWD_MAP.put(26L, "Dagger*");
    PROJECTILES_IWD_MAP.put(27L, "Dagger Exploding");
    PROJECTILES_IWD_MAP.put(28L, "Dagger Flaming");
    PROJECTILES_IWD_MAP.put(29L, "Dagger Heavy");
    PROJECTILES_IWD_MAP.put(30L, "Dagger (Non-Magical)");
    PROJECTILES_IWD_MAP.put(31L, "Dart");
    PROJECTILES_IWD_MAP.put(32L, "Dart Exploding");
    PROJECTILES_IWD_MAP.put(33L, "Dart Flaming");
    PROJECTILES_IWD_MAP.put(34L, "Dart Heavy*");
    PROJECTILES_IWD_MAP.put(35L, "Dart (Non-Magical)");
    PROJECTILES_IWD_MAP.put(36L, "Magic Missile");
    PROJECTILES_IWD_MAP.put(37L, "Fireball");
    PROJECTILES_IWD_MAP.put(39L, "Lightning Bolt");
    PROJECTILES_IWD_MAP.put(41L, "Sleep");
    PROJECTILES_IWD_MAP.put(55L, "Spear");
    PROJECTILES_IWD_MAP.put(56L, "Spear Exploding");
    PROJECTILES_IWD_MAP.put(57L, "Spear Flaming");
    PROJECTILES_IWD_MAP.put(58L, "Spear Heaby");
    PROJECTILES_IWD_MAP.put(59L, "Spear (Non-Magical)");
    PROJECTILES_IWD_MAP.put(62L, "Web Travel");
    PROJECTILES_IWD_MAP.put(63L, "Web Ground");
    PROJECTILES_IWD_MAP.put(64L, "Gaze");
    PROJECTILES_IWD_MAP.put(65L, "Holy Might");
    PROJECTILES_IWD_MAP.put(66L, "Flame Strike");
    PROJECTILES_IWD_MAP.put(67L, "Magic Missile 1");
    PROJECTILES_IWD_MAP.put(68L, "Magic Missile 2");
    PROJECTILES_IWD_MAP.put(69L, "Magic Missile 3");
    PROJECTILES_IWD_MAP.put(70L, "Magic Missile 4");
    PROJECTILES_IWD_MAP.put(71L, "Magic Missile 5");
    PROJECTILES_IWD_MAP.put(72L, "Magic Missile 6");
    PROJECTILES_IWD_MAP.put(73L, "Magic Missile 7");
    PROJECTILES_IWD_MAP.put(74L, "Magic Missile 8");
    PROJECTILES_IWD_MAP.put(75L, "Magic Missile 9");
    PROJECTILES_IWD_MAP.put(76L, "Magic Missile 10");
    PROJECTILES_IWD_MAP.put(94L, "Cloud");
    PROJECTILES_IWD_MAP.put(95L, "Skull Trap");
    PROJECTILES_IWD_MAP.put(96L, "Colour Spray");
    PROJECTILES_IWD_MAP.put(97L, "Ice Storm");
    PROJECTILES_IWD_MAP.put(98L, "Fire Wall");
    PROJECTILES_IWD_MAP.put(99L, "Glyph");
    PROJECTILES_IWD_MAP.put(100L, "Grease");
    PROJECTILES_IWD_MAP.put(101L, "Flame Arrow Green");
    PROJECTILES_IWD_MAP.put(102L, "Flame Arrow Blue");
    PROJECTILES_IWD_MAP.put(103L, "Fireball Green");
    PROJECTILES_IWD_MAP.put(104L, "FireBall Blue");
    PROJECTILES_IWD_MAP.put(105L, "Potion");
    PROJECTILES_IWD_MAP.put(106L, "Potion Exploding");
    PROJECTILES_IWD_MAP.put(107L, "Acid Blob");
    PROJECTILES_IWD_MAP.put(108L, "Scorcher");
    PROJECTILES_IWD_MAP.put(109L, "Travel Door");
    PROJECTILES_IWD_MAP.put(186L, "Cloudkill");
    PROJECTILES_IWD_MAP.put(187L, "Flame Arrow Ice");
    PROJECTILES_IWD_MAP.put(188L, "Cow");
    PROJECTILES_IWD_MAP.put(189L, "Hold");
    PROJECTILES_IWD_MAP.put(190L, "Scorcher Ice");
    PROJECTILES_IWD_MAP.put(191L, "Acid Blob Mustard");
    PROJECTILES_IWD_MAP.put(192L, "Acid Blob Grey");
    PROJECTILES_IWD_MAP.put(193L, "Acid Blob Ochre");
    PROJECTILES_IWD_MAP.put(217L, "Icewind Magic Missile");
    PROJECTILES_IWD_MAP.put(313L, "Modenkainen's Force Missiles");
    PROJECTILES_IWD_MAP.put(345L, "Sekolah's Fire");

    INC_TYPES_MAP.put(0L, "Increment");
    INC_TYPES_MAP.put(1L, "Set");
    INC_TYPES_MAP.put(2L, "Set % of");
    INC_TYPES_MAP.put(5L, "Multiply %");

    ATTACKS_EE_MAP.put(-10L, "-4.5 attack per round");
    ATTACKS_EE_MAP.put(-9L, "-3.5 attack per round");
    ATTACKS_EE_MAP.put(-8L, "-2.5 attack per round");
    ATTACKS_EE_MAP.put(-7L, "-1.5 attack per round");
    ATTACKS_EE_MAP.put(-6L, "-0.5 attack per round");
    ATTACKS_EE_MAP.put(-5L, "-5 attacks per round");
    ATTACKS_EE_MAP.put(-4L, "-4 attacks per round");
    ATTACKS_EE_MAP.put(-3L, "-3 attacks per round");
    ATTACKS_EE_MAP.put(-2L, "-2 attacks per round");
    ATTACKS_EE_MAP.put(-1L, "-1 attack per round");
    ATTACKS_EE_MAP.put(0L, "0 attacks per round");
    ATTACKS_EE_MAP.put(1L, "1 attack per round");
    ATTACKS_EE_MAP.put(2L, "2 attacks per round");
    ATTACKS_EE_MAP.put(3L, "3 attacks per round");
    ATTACKS_EE_MAP.put(4L, "4 attacks per round");
    ATTACKS_EE_MAP.put(5L, "5 attacks per round");
    ATTACKS_EE_MAP.put(6L, "0.5 attack per round");
    ATTACKS_EE_MAP.put(7L, "1.5 attacks per round");
    ATTACKS_EE_MAP.put(8L, "2.5 attacks per round");
    ATTACKS_EE_MAP.put(9L, "3.5 attacks per round");
    ATTACKS_EE_MAP.put(10L, "4.5 attacks per round");
  }

  private final int id;
  private final String name;

  /** One-time initialization of opcode classes. */
  public static void initOpcodes() {
    if (OPCODE_CLASSES.isEmpty()) {
      // Using a for-loop is not very efficient, but shouldn't produce a noticeable delay in this case.
      for (int id = 0; id < 1000; id++) {
        try {
          Class<?> cls = Class.forName(String.format("%s.Opcode%03d", BaseOpcode.class.getPackage().getName(), id));
          OPCODE_CLASSES.add(cls.asSubclass(BaseOpcode.class));
        } catch (ClassNotFoundException e) {
          // speeding up opcode detection
          if (id >= NUM_OPCODES) {
            break;
          }
        }
      }
    }
  }

  /** Clears all game-specific data. */
  public static void reset() {
    opcodeList = null;
    effectNames = null;
    portraitIconNames = null;
    DefaultOpcode.clearCache();
  }

  /**
   * Returns an array with the names of all available opcodes for the current game.
   *
   * @return String array with opcode names.
   */
  public static synchronized String[] getEffectNames() {
    if (effectNames == null) {
      final List<String> names = new ArrayList<>();

      // finding highest supported opcode id for the current game
      final BaseOpcode lastOpcode = getOpcodeMap().values().stream().max((a, b) -> {
        final int ra = a.isAvailable() ? a.getId() : -1;
        final int rb = b.isAvailable() ? b.getId() : -1;
        return ra - rb;
      }).orElse(null);
      int maxOpcodeId = (lastOpcode != null) ? lastOpcode.getId() : Integer.MAX_VALUE;

      // collecting opcode names
      for (final Integer id : getOpcodeMap().keySet()) {
        if (id > maxOpcodeId) {
          break;
        }
        final BaseOpcode opcode = getOpcodeMap().get(id);
        while (names.size() < opcode.getId()) {
          names.add(DefaultOpcode.getDefaultName());
        }
        names.add(opcode.getName());
      }

      effectNames = names.toArray(new String[0]);
    }
    return effectNames;
  }

  /**
   * Returns the array with portrait icon names.
   *
   * @return String array with icon names.
   */
  public static String[] getPortraitIconNames() {
    return getPortraitIconNames(null);
  }

  /**
   * Returns the array with portrait icon names.
   *
   * @param defaultName Optional custom name for the first entry (index 0). Specify {@code null} to ignore.
   * @return String array with icon names.
   */
  public static synchronized String[] getPortraitIconNames(String defaultName) {
    if (portraitIconNames == null) {
      if (Profile.getGame() != Profile.Game.PSTEE && Profile.getGame() != Profile.Game.PST) {
        try {
          Table2da table = Table2daCache.get("STATDESC.2DA");
          if (table == null) {
            return new String[] {};
          }

          // first pass: determine highest icon index
          int maxIndex = -1;
          for (int row = 0, rowCount = table.getRowCount(); row < rowCount; row++) {
            try {
              int index = Integer.parseInt(table.get(row, 0));
              maxIndex = Math.max(maxIndex, index);
            } catch (NumberFormatException nfe) {
              Logger.trace(nfe);
            }
          }

          // second pass: collect icon descriptions
          if (maxIndex >= 0) {
            portraitIconNames = new String[maxIndex + 1];
            for (int row = 0, rowCount = table.getRowCount(); row < rowCount; row++) {
              try {
                int index = Integer.parseInt(table.get(row, 0));
                int strref = Integer.parseInt(table.get(row, 1));
                if (index >= 0 && strref >= 0) {
                  portraitIconNames[index] = StringTable.getStringRef(strref);
                }
              } catch (NumberFormatException nfe) {
                Logger.error(nfe);
              }
            }
          }
        } catch (NullPointerException npe) {
          Logger.error(npe);
        }
      }

      if (portraitIconNames == null) {
        portraitIconNames = new String[] { STRING_DEFAULT };
      }
    }

    String[] retVal;
    if (defaultName != null && portraitIconNames.length > 0) {
      retVal = Arrays.copyOf(portraitIconNames, portraitIconNames.length);
      retVal[0] = defaultName;
    } else {
      retVal = portraitIconNames;
    }
    return retVal;
  }

  /** Returns the save type flags description table depending on the current game. */
  public static String[] getSaveType() {
    if (Profile.getEngine() == Profile.Engine.IWD2) {
      return SAVE_TYPES_IWD2;
    } else if ((Boolean)Profile.getProperty(Profile.Key.IS_GAME_TOBEX)) {
      return SAVE_TYPES_TOBEX;
    } else {
      return SAVE_TYPES;
    }
  }

  /**
   * Creates and returns an index/offset map of the current effect structure which can be used to address specific
   * fields within the effect.
   *
   * @param struct      The effect structure to map.
   * @return            A map containing table indices and structure offsets, starting with the opcode field.
   * @throws Exception  If struct doesn't contain a valid effect structure.
   */
  public static EnumMap<EffectEntry, Integer> getEffectStructure(AbstractStruct struct) throws Exception {
    if (struct != null) {
      EffectType effType = (EffectType)struct.getAttribute(EffectType.EFFECT_TYPE);
      if (effType != null) {
        final EnumMap<EffectEntry, Integer> map = new EnumMap<>(EffectEntry.class);
        boolean isVersion1 = (effType.getSize() == 2);
        int ofsOpcode = effType.getOffset();
        int idxOpcode = struct.getFields().indexOf(struct.getAttribute(ofsOpcode));
        if (isVersion1 && struct.getSize() >= 0x30) {
          // EFF V1.0
          map.put(EffectEntry.IDX_OPCODE, idxOpcode);
          map.put(EffectEntry.OFS_OPCODE, ofsOpcode);
          map.put(EffectEntry.IDX_TARGET, idxOpcode + 1);
          map.put(EffectEntry.OFS_TARGET, ofsOpcode + 0x02);
          map.put(EffectEntry.IDX_POWER, idxOpcode + 2);
          map.put(EffectEntry.OFS_POWER, ofsOpcode + 0x03);
          map.put(EffectEntry.IDX_PARAM1, idxOpcode + 3);
          map.put(EffectEntry.OFS_PARAM1, ofsOpcode + 0x04);
          map.put(EffectEntry.IDX_PARAM1A, idxOpcode + 3);
          map.put(EffectEntry.OFS_PARAM1A, ofsOpcode + 0x04);
          map.put(EffectEntry.IDX_PARAM1B, idxOpcode + 4);
          map.put(EffectEntry.OFS_PARAM1B, ofsOpcode + 0x06);
          map.put(EffectEntry.IDX_PARAM2, idxOpcode + 4);
          map.put(EffectEntry.OFS_PARAM2, ofsOpcode + 0x08);
          map.put(EffectEntry.IDX_PARAM2A, idxOpcode + 4);
          map.put(EffectEntry.OFS_PARAM2A, ofsOpcode + 0x08);
          map.put(EffectEntry.IDX_PARAM2B, idxOpcode + 5);
          map.put(EffectEntry.OFS_PARAM2B, ofsOpcode + 0x0a);
          map.put(EffectEntry.IDX_TIMING, idxOpcode + 5);
          map.put(EffectEntry.OFS_TIMING, ofsOpcode + 0x0C);
          map.put(EffectEntry.IDX_RESISTANCE, idxOpcode + 6);
          map.put(EffectEntry.OFS_RESISTANCE, ofsOpcode + 0x0D);
          map.put(EffectEntry.IDX_DURATION, idxOpcode + 7);
          map.put(EffectEntry.OFS_DURATION, ofsOpcode + 0x0E);
          map.put(EffectEntry.IDX_PROBABILITY1, idxOpcode + 8);
          map.put(EffectEntry.OFS_PROBABILITY1, ofsOpcode + 0x12);
          map.put(EffectEntry.IDX_PROBABILITY2, idxOpcode + 9);
          map.put(EffectEntry.OFS_PROBABILITY2, ofsOpcode + 0x13);
          map.put(EffectEntry.IDX_RESOURCE, idxOpcode + 10);
          map.put(EffectEntry.OFS_RESOURCE, ofsOpcode + 0x14);
          map.put(EffectEntry.IDX_DICETHROWN, idxOpcode + 11);
          map.put(EffectEntry.OFS_DICETHROWN, ofsOpcode + 0x1C);
          map.put(EffectEntry.IDX_DICESIDES, idxOpcode + 12);
          map.put(EffectEntry.OFS_DICESIDES, ofsOpcode + 0x20);
          map.put(EffectEntry.IDX_SAVETYPE, idxOpcode + 13);
          map.put(EffectEntry.OFS_SAVETYPE, ofsOpcode + 0x24);
          map.put(EffectEntry.IDX_SAVEBONUS, idxOpcode + 14);
          map.put(EffectEntry.OFS_SAVEBONUS, ofsOpcode + 0x28);
          map.put(EffectEntry.IDX_SPECIAL, idxOpcode + 15);
          map.put(EffectEntry.OFS_SPECIAL, ofsOpcode + 0x2C);
          return map;
        } else if (!isVersion1 && struct.getSize() >= 0x100) {
          // EFF V2.0
          map.put(EffectEntry.IDX_OPCODE, idxOpcode);
          map.put(EffectEntry.OFS_OPCODE, ofsOpcode);
          map.put(EffectEntry.IDX_TARGET, idxOpcode + 1);
          map.put(EffectEntry.OFS_TARGET, ofsOpcode + 0x04);
          map.put(EffectEntry.IDX_POWER, idxOpcode + 2);
          map.put(EffectEntry.OFS_POWER, ofsOpcode + 0x08);
          map.put(EffectEntry.IDX_PARAM1, idxOpcode + 3);
          map.put(EffectEntry.OFS_PARAM1, ofsOpcode + 0x0C);
          map.put(EffectEntry.IDX_PARAM1A, idxOpcode + 3);
          map.put(EffectEntry.OFS_PARAM1A, ofsOpcode + 0x0C);
          map.put(EffectEntry.IDX_PARAM1B, idxOpcode + 4);
          map.put(EffectEntry.OFS_PARAM1B, ofsOpcode + 0x0E);
          map.put(EffectEntry.IDX_PARAM2, idxOpcode + 4);
          map.put(EffectEntry.OFS_PARAM2, ofsOpcode + 0x10);
          map.put(EffectEntry.IDX_PARAM2A, idxOpcode + 4);
          map.put(EffectEntry.OFS_PARAM2A, ofsOpcode + 0x10);
          map.put(EffectEntry.IDX_PARAM2B, idxOpcode + 5);
          map.put(EffectEntry.OFS_PARAM2B, ofsOpcode + 0x12);
          map.put(EffectEntry.IDX_TIMING, idxOpcode + 5);
          map.put(EffectEntry.OFS_TIMING, ofsOpcode + 0x14);
          map.put(EffectEntry.IDX_DURATION, idxOpcode + 6);
          map.put(EffectEntry.OFS_DURATION, ofsOpcode + 0x18);
          map.put(EffectEntry.IDX_PROBABILITY1, idxOpcode + 7);
          map.put(EffectEntry.OFS_PROBABILITY1, ofsOpcode + 0x1C);
          map.put(EffectEntry.IDX_PROBABILITY2, idxOpcode + 8);
          map.put(EffectEntry.OFS_PROBABILITY2, ofsOpcode + 0x1E);
          map.put(EffectEntry.IDX_RESOURCE, idxOpcode + 9);
          map.put(EffectEntry.OFS_RESOURCE, ofsOpcode + 0x20);
          map.put(EffectEntry.IDX_DICETHROWN, idxOpcode + 10);
          map.put(EffectEntry.OFS_DICETHROWN, ofsOpcode + 0x28);
          map.put(EffectEntry.IDX_DICESIDES, idxOpcode + 11);
          map.put(EffectEntry.OFS_DICESIDES, ofsOpcode + 0x2C);
          map.put(EffectEntry.IDX_SAVETYPE, idxOpcode + 12);
          map.put(EffectEntry.OFS_SAVETYPE, ofsOpcode + 0x30);
          map.put(EffectEntry.IDX_SAVEBONUS, idxOpcode + 13);
          map.put(EffectEntry.OFS_SAVEBONUS, ofsOpcode + 0x34);
          map.put(EffectEntry.IDX_SPECIAL, idxOpcode + 14);
          map.put(EffectEntry.OFS_SPECIAL, ofsOpcode + 0x38);
          map.put(EffectEntry.IDX_PRIMARYTYPE, idxOpcode + 15);
          map.put(EffectEntry.OFS_PRIMARYTYPE, ofsOpcode + 0x3C);
          map.put(EffectEntry.IDX_UNKNOWN040, idxOpcode + 16);
          map.put(EffectEntry.OFS_UNKNOWN040, ofsOpcode + 0x40);
          map.put(EffectEntry.IDX_PARENTLOWESTLEVEL, idxOpcode + 17);
          map.put(EffectEntry.OFS_PARENTLOWESTLEVEL, ofsOpcode + 0x44);
          map.put(EffectEntry.IDX_PARENTHIGHESTLEVEL, idxOpcode + 18);
          map.put(EffectEntry.OFS_PARENTHIGHESTLEVEL, ofsOpcode + 0x48);
          map.put(EffectEntry.IDX_RESISTANCE, idxOpcode + 19);
          map.put(EffectEntry.OFS_RESISTANCE, ofsOpcode + 0x4C);
          map.put(EffectEntry.IDX_PARAM3, idxOpcode + 20);
          map.put(EffectEntry.OFS_PARAM3, ofsOpcode + 0x50);
          map.put(EffectEntry.IDX_PARAM4, idxOpcode + 21);
          map.put(EffectEntry.OFS_PARAM4, ofsOpcode + 0x54);
          map.put(EffectEntry.IDX_PARAM5, idxOpcode + 22);
          map.put(EffectEntry.OFS_PARAM5, ofsOpcode + 0x58);
          map.put(EffectEntry.IDX_TIMEAPPLIED, idxOpcode + 23);
          map.put(EffectEntry.OFS_TIMEAPPLIED, ofsOpcode + 0x5c);
          map.put(EffectEntry.IDX_RESOURCE2, idxOpcode + 24);
          map.put(EffectEntry.OFS_RESOURCE2, ofsOpcode + 0x60);
          map.put(EffectEntry.IDX_RESOURCE3, idxOpcode + 25);
          map.put(EffectEntry.OFS_RESOURCE3, ofsOpcode + 0x68);
          map.put(EffectEntry.IDX_CASTERX, idxOpcode + 26);
          map.put(EffectEntry.OFS_CASTERX, ofsOpcode + 0x70);
          map.put(EffectEntry.IDX_CASTERY, idxOpcode + 27);
          map.put(EffectEntry.OFS_CASTERY, ofsOpcode + 0x74);
          map.put(EffectEntry.IDX_TARGETX, idxOpcode + 28);
          map.put(EffectEntry.OFS_TARGETX, ofsOpcode + 0x78);
          map.put(EffectEntry.IDX_TARGETY, idxOpcode + 29);
          map.put(EffectEntry.OFS_TARGETY, ofsOpcode + 0x7C);
          map.put(EffectEntry.IDX_PARENTRESOURCETYPE, idxOpcode + 30);
          map.put(EffectEntry.OFS_PARENTRESOURCETYPE, ofsOpcode + 0x80);
          map.put(EffectEntry.IDX_PARENTRESOURCE, idxOpcode + 31);
          map.put(EffectEntry.OFS_PARENTRESOURCE, ofsOpcode + 0x84);
          map.put(EffectEntry.IDX_PARENTRESOURCEFLAGS, idxOpcode + 32);
          map.put(EffectEntry.OFS_PARENTRESOURCEFLAGS, ofsOpcode + 0x8C);
          map.put(EffectEntry.IDX_PROJECTILE, idxOpcode + 33);
          map.put(EffectEntry.OFS_PROJECTILE, ofsOpcode + 0x90);
          map.put(EffectEntry.IDX_PARENTRESOURCESLOT, idxOpcode + 34);
          map.put(EffectEntry.OFS_PARENTRESOURCESLOT, ofsOpcode + 0x94);
          map.put(EffectEntry.IDX_VARIABLE, idxOpcode + 35);
          map.put(EffectEntry.OFS_VARIABLE, ofsOpcode + 0x98);
          map.put(EffectEntry.IDX_CASTERLEVEL, idxOpcode + 36);
          map.put(EffectEntry.OFS_CASTERLEVEL, ofsOpcode + 0xb8);
          map.put(EffectEntry.IDX_FIRSTAPPLY, idxOpcode + 37);
          map.put(EffectEntry.OFS_FIRSTAPPLY, ofsOpcode + 0xbc);
          map.put(EffectEntry.IDX_SECONDARYTYPE, idxOpcode + 38);
          map.put(EffectEntry.OFS_SECONDARYTYPE, ofsOpcode + 0xc0);
          map.put(EffectEntry.IDX_UNKNOWN0C4, idxOpcode + 39);
          map.put(EffectEntry.OFS_UNKNOWN0C4, ofsOpcode + 0xc4);
          return map;
        }
      }
    }
    throw new Exception("Invalid effect structure specified");
  }

  /**
   * Returns the {@code StructEntry} object that is specified by the {@code EffectEntry} argument.
   *
   * @param struct      The structure that contains the requested entry.
   * @param id          Indicates which effect field to return.
   * @return            The {@code StructEntry} instance specified by the id.
   * @throws Exception  If one or more arguments are invalid.
   */
  public static StructEntry getEntry(AbstractStruct struct, EffectEntry id) throws Exception
  {
    StructEntry retVal = null;
    final EnumMap<EffectEntry, Integer> map = getEffectStructure(struct);
    if (map.containsKey(id)) {
      retVal = getEntryByIndex(struct, map.get(id));
    }
    return retVal;
  }

  /**
   * Returns the StructEntry object at the specified index. Use in conjunction with getEffectStructure.
   *
   * @param struct      The structure that contains the requested entry.
   * @param entryIndex  The index of the requested entry.
   * @return            The entry at the specified index
   * @throws Exception  If one or more arguments are invalid.
   */
  public static StructEntry getEntryByIndex(AbstractStruct struct, int entryIndex) throws Exception
  {
    if (struct != null) {
      return struct.getFields().get(entryIndex);
    } else {
      throw new Exception("Invalid arguments specified");
    }
  }

  /**
   * Returns the data associated with the specified structure entry.
   *
   * @param entry The structure entry to fetch data from.
   * @return      Data as {@code ByteBuffer} object.
   */
  public static ByteBuffer getEntryData(StructEntry entry)
  {
    ByteBuffer bb = StreamUtils.getByteBuffer(entry.getSize());
    try (ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb)) {
      entry.write(bbos);
    } catch (IOException e) {
      Logger.error(e);
      return null;
    }
    return bb;
  }

  /**
   * Convenience function to retrieve data associated with a structure entry within struct.
   *
   * @param struct  The structure that contains the structure entry
   * @param id      Indicates which effect field to process.
   * @return        Data as {@code ByteBuffer} object.
   */
  public static ByteBuffer getEntryData(AbstractStruct struct, EffectEntry id)
  {
    if (struct != null) {
      try {
        EnumMap<EffectEntry, Integer> map = getEffectStructure(struct);
        if (map.containsKey(id)) {
          int idx = map.get(id);
          if (idx >= 0 && idx < struct.getFields().size()) {
            return getEntryData(struct.getFields().get(idx));
          }
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return null;
  }

  /**
   * Replaces a data entry in struct with the specified new entry.
   *
   * @param struct    The struct which contains the entry specified by entryIndex and entryOffset.
   * @param index     The index key for the entry within struct.
   * @param offset    The offset key for the data entry.
   * @param newEntry  The new entry which replaces the old one.
   */
  public static void replaceEntry(AbstractStruct struct, EffectEntry index, EffectEntry offset,
      StructEntry newEntry) throws Exception
  {
    EnumMap<EffectEntry, Integer> map = getEffectStructure(struct);
    if (newEntry != null && map.containsKey(index) && map.containsKey(offset)) {
      int idx = map.get(index);
      int ofs = map.get(offset);
      final List<StructEntry> list = struct.getFields();
      if (idx >= 0 && idx < list.size() &&
          ofs >= struct.getOffset() && ofs < struct.getOffset() + struct.getSize()) {
        newEntry.setOffset(ofs);
        list.set(idx, newEntry);
      } else{
        throw new Exception("Index or offset are out of bounds");
      }
    } else {
      throw new Exception("Invalid arguments specified");
    }
  }

  /**
   * Central hub for dynamic opcode specific modifications of effect structures.
   *
   * @param struct The effect structure to update.
   * @return {@code true} if fields within the effect structure have been updated, {@code false} otherwise.
   * @throws Exception If the argument doesn't specify a valid effect structure.
   */
  public static boolean updateOpcode(AbstractStruct struct) throws Exception {
    if (struct != null) {
      EffectType effType = (EffectType)getEntry(struct, EffectEntry.IDX_OPCODE);
      if (effType != null) {
        int opcode = ((IsNumeric)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        return getOpcode(opcode).update(struct);
      }
    }
    return false;
  }

  /**
   * Public method that creates all the required effect fields based on the given parameters for the given opcode.
   *
   * @param id          Number of the opcode to create an effect structure for.
   * @param parent      The parent structure.
   * @param buffer      Buffer containing raw effects data.
   * @param offset      Buffer offset pointing to the first field after the opcode type field.
   * @param list        List of field data for the current effect.
   * @param isVersion1  {@code true} if the buffer contains data for a version 1 effect, otherwise a version 2 effect is
   *                    assumed.
   * @return Offset pointing behind the last initialized effect field.
   */
  public static int makeEffectStruct(int id, Datatype parent, ByteBuffer buffer, int offset,
      List<StructEntry> list, boolean isVersion1) throws Exception {
    return getOpcode(id).makeEffectStruct(parent, buffer, offset, list, isVersion1);
  }

  /**
   * Returns the specified opcode instance. Returns a default instance if the requested opcode doesn't exist.
   *
   * @param id  The opcode number.
   * @return {@code BaseOpcode} instance of the requested opcode. Returns a default opcode instance for unknown or
   *         unsupported opcodes.
   */
  public static BaseOpcode getOpcode(int id) {
    final BaseOpcode opcode = getOpcodeMap().get(id);
    if (opcode != null) {
      return opcode;
    } else {
      return DefaultOpcode.get(id);
    }
  }

  /** Returns whether the current game is enhanced by TobEx. */
  protected static boolean isTobEx() {
    return Profile.getProperty(Profile.Key.IS_GAME_TOBEX);
  }

  /** Returns whether the current game is enhanced by EEEx. */
  protected static boolean isEEEx() {
    return Profile.getProperty(Profile.Key.IS_GAME_EEEX);
  }

  /** Used internally to return a valid opcode list for the current game. */
  private static synchronized TreeMap<Integer, BaseOpcode> getOpcodeMap() {
    if (opcodeList == null) {
      initOpcodes();
      opcodeList = new TreeMap<>();
      // initializing opcode list
      for (final Class<? extends BaseOpcode> cls : OPCODE_CLASSES) {
        try {
          Constructor<? extends BaseOpcode> ctor = cls.getConstructor();
          BaseOpcode opcode = ctor.newInstance();
          if (opcode.isAvailable()) {
            opcodeList.put(opcode.getId(), opcode);
          }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException
            | InstantiationException e) {
          Logger.error(e);
        }
      }
    }
    return opcodeList;
  }


  /** Returns the opcode index. */
  public int getId() {
    return id;
  }

  /** Returns the name of the opcode for the current game. */
  public String getName() {
    return (name != null) ? name : DefaultOpcode.getDefaultName();
  }

  /** Returns whether the opcode is available in the current game. */
  public boolean isAvailable() {
    return name != null;
  }

  /**
   * Initializes common properties of the opcode.
   *
   * @param id   The opcode index.
   * @param name The opcode name.
   */
  protected BaseOpcode(int id, String name) {
    this.id = id;
    this.name = name;
  }

  /**
   * Creates all the required effect fields based on the given parameters.
   *
   * @param parent     The parent structure.
   * @param buffer     Buffer containing raw effects data.
   * @param offset     Buffer offset pointing to the first field after the opcode type field.
   * @param list       List of field data for the current effect.
   * @param isVersion1 {@code true} if the buffer contains data for a version 1 effect, otherwise a version 2 effect is
   *                   assumed.
   * @return Offset pointing behind the last initialized effect field.
   */
  protected int makeEffectStruct(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) throws Exception {
    if (buffer != null && offset >= 0 && list != null) {
      buffer.position(offset);
      int param1 = buffer.getInt();
      int param2 = buffer.getInt();

      // setting parameter 1 and 2
      String resType = makeEffectParams(parent, buffer, offset, list, isVersion1);
      offset += 8;

      // setting common fields #1 ("Timing mode" ... "Probability2")
      offset = makeEffectCommon1(parent, buffer, offset, list, isVersion1);

      // setting Resource field
      offset = makeEffectResource(parent, buffer, offset, list, resType, param1, param2);

      // setting common fields #2 ("Dice" ... "Save bonus")
      offset = makeEffectCommon2(parent, buffer, offset, list, isVersion1);

      // setting 'Special' parameter
      offset = makeEffectSpecial(parent, buffer, offset, list, resType, param1, param2);

      return offset;
    } else {
      throw new Exception("Invalid effect parameters");
    }
  }

  /**
   * Handles initialization of parameter 1 and 2 of the effect structure.
   *
   * @param parent     The parent structure.
   * @param buffer     Buffer containing raw effects data.
   * @param offset     Buffer offset pointing to the first field after the opcode type field.
   * @param list       List of field data for the current effect.
   * @param isVersion1 {@code true} if the buffer contains data for a version 1 effect, otherwise a version 2 effect is
   *                   assumed.
   * @return Expected resource type(s) for the resource field. Multiple types are separated by colons.
   *         Returns {@link #EFFECT_STRING} if resource field specifies a literal string.
   *         Returns {@code null} if resource field is unused.
   */
  protected String makeEffectParams(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    String resType;
    switch (Profile.getEngine()) {
      case BG1:
        resType = makeEffectParamsBG1(parent, buffer, offset, list, isVersion1);
        break;
      case BG2:
        resType = makeEffectParamsBG2(parent, buffer, offset, list, isVersion1);
        break;
      case EE:
        resType = makeEffectParamsEE(parent, buffer, offset, list, isVersion1);
        break;
      case PST:
        resType = makeEffectParamsPST(parent, buffer, offset, list, isVersion1);
        break;
      case IWD:
        resType = makeEffectParamsIWD(parent, buffer, offset, list, isVersion1);
        break;
      case IWD2:
        resType = makeEffectParamsIWD2(parent, buffer, offset, list, isVersion1);
        break;
      default:
        resType = makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
    }

    return resType;
  }

  /**
   * Generic implementation for handling parameter 1 and 2 of the effect structure.
   * See {@link #makeEffectParams(Datatype, ByteBuffer, int, List, boolean)} for more information.
   * <p>Preferred method to override in derived classes for opcode definitions that cover multiple engines.</p>
   */
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_PARAMETER_1));
    list.add(new DecNumber(buffer, offset + 4, 4, EFFECT_PARAMETER_2));
    return null;
  }

  /**
   * BG1 implementation for handling parameter 1 and 2 of the effect structure.
   * See {@link #makeEffectParams(Datatype, ByteBuffer, int, List, boolean)} for more information.
   * <p>Preferred method to override in derived classes for BG1-specific opcode definitions.</p>
   */
  protected String makeEffectParamsBG1(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  /**
   * BG2 implementation for handling parameter 1 and 2 of the effect structure.
   * See {@link #makeEffectParams(Datatype, ByteBuffer, int, List, boolean)} for more information.
   * <p>Preferred method to override in derived classes for BG2-specific opcode definitions.</p>
   */
  protected String makeEffectParamsBG2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  /**
   * Enhanced Edition implementation for handling parameter 1 and 2 of the effect structure.
   * See {@link #makeEffectParams(Datatype, ByteBuffer, int, List, boolean)} for more information.
   * <p>Preferred method to override in derived classes for EE-specific opcode definitions (all games).</p>
   */
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  /**
   * PST implementation for handling parameter 1 and 2 of the effect structure.
   * See {@link #makeEffectParams(Datatype, ByteBuffer, int, List, boolean)} for more information.
   * <p>Preferred method to override in derived classes for PST-specific opcode definitions.</p>
   */
  protected String makeEffectParamsPST(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  /**
   * IWD implementation for handling parameter 1 and 2 of the effect structure.
   * See {@link #makeEffectParams(Datatype, ByteBuffer, int, List, boolean)} for more information.
   * <p>Preferred method to override in derived classes for IWD-specific opcode definitions.</p>
   */
  protected String makeEffectParamsIWD(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  /**
   * IWD2 implementation for handling parameter 1 and 2 of the effect structure.
   * See {@link #makeEffectParams(Datatype, ByteBuffer, int, List, boolean)} for more information.
   * <p>Preferred method to override in derived classes for IWD2-specific opcode definitions.</p>
   */
  protected String makeEffectParamsIWD2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  /**
   * Handles parameter 1 and 2 of the effect structure for unknown opcodes.
   *
   * @param buffer     Buffer containing raw effects data.
   * @param offset     Buffer offset pointing to the first field after the opcode type field.
   * @param list       List of field data for the current effect.
   * @return Always returns {@code null}.
   */
  protected String makeEffectParamsUnknown(ByteBuffer buffer, int offset, List<StructEntry> list) {
    list.add(new Unknown(buffer, offset, 4));
    list.add(new Unknown(buffer, offset + 4, 4));
    return null;
  }

  /**
   * Handles initialization of the resource field.
   *
   * @param parent     The parent structure.
   * @param buffer     Buffer containing raw effects data.
   * @param offset     Buffer offset pointing to the first field after the opcode type field.
   * @param list       List of field data for the current effect.
   * @param resType    Resource type(s) for the resource field. Multiple types are separated by colons.
   *                   Specify {@code #EFFECT_STRING} if resource field specifies a literal string.
   *                   Specify {@code null} if resource field is unused.
   * @param param1     Value of the "Parameter 1" field.
   * @param param2     Value of the "Parameter 2" field
   * @return Offset pointing behind the last initialized effect field.
   */
  protected int makeEffectResource(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      String resType, int param1, int param2) {
    if (resType != null) {
      if (resType.equalsIgnoreCase(EFFECT_STRING)) {
        list.add(new TextString(buffer, offset, 8, EFFECT_STRING));
      } else {
        list.add(new ResourceRef(buffer, offset, EFFECT_RESOURCE, resType.split(":")));
      }
    } else {
      list.add(new Unknown(buffer, offset, 8, AbstractStruct.COMMON_UNUSED));
    }
    return offset + 8;
  }

  /**
   * Handles initialization of the first set of common fields ("Timing mode" ... "Probability2").
   *
   * @param parent     The parent structure.
   * @param buffer     Buffer containing raw effects data.
   * @param offset     Buffer offset pointing to the first field after the opcode type field.
   * @param list       List of field data for the current effect.
   * @param isVersion1 {@code true} if the buffer contains data for a version 1 effect, otherwise a version 2 effect is
   *                   assumed.
   * @return Offset pointing behind the last initialized effect field.
   */
  protected int makeEffectCommon1(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    if (isVersion1) {
      list.add(new HashBitmap(buffer, offset, 1, EFFECT_TIMING_MODE, DURATIONS_V1_MAP, false));
      list.add(new Bitmap(buffer, offset + 1, 1, Effect2.EFFECT_DISPEL_TYPE, Effect2.DISPEL_ARRAY));
      offset += 2;
    } else {
      list.add(new HashBitmap(buffer, offset, 4, EFFECT_TIMING_MODE, DURATIONS_V2_MAP, false));
      offset += 4;
    }

    list.add(new DecNumber(buffer, offset, 4, EFFECT_DURATION));
    offset += 4;

    if (isVersion1) {
      list.add(new DecNumber(buffer, offset, 1, EFFECT_PROBABILITY_1));
      list.add(new DecNumber(buffer, offset + 1, 1, EFFECT_PROBABILITY_2));
      offset += 2;
    } else {
      list.add(new DecNumber(buffer, offset, 2, EFFECT_PROBABILITY_1));
      list.add(new DecNumber(buffer, offset + 2, 2, EFFECT_PROBABILITY_2));
      offset += 4;
    }

    return offset;
  }

  /**
   * Handles initialization of the second set of common fields ("Dice" ... "Save bonus").
   *
   * @param parent     The parent structure.
   * @param buffer     Buffer containing raw effects data.
   * @param offset     Buffer offset pointing to the first field after the opcode type field.
   * @param list       List of field data for the current effect.
   * @param isVersion1 {@code true} if the buffer contains data for a version 1 effect, otherwise a version 2 effect is
   *                   assumed.
   * @return Offset pointing behind the last initialized effect field.
   */
  protected int makeEffectCommon2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    final String[] saveType = getSaveType();
    if (isVersion1) {
      list.add(new UnsignDecNumber(buffer, offset, 4, EFFECT_DICE_COUNT_MAX_LEVEL));
      list.add(new UnsignDecNumber(buffer, offset + 4, 4, EFFECT_DICE_SIZE_MIN_LEVEL));
      if (Profile.getEngine() == Profile.Engine.IWD2) {
        list.add(new Flag(buffer, offset + 8, 4, EFFECT_SAVE_TYPE, saveType));
        list.add(new DecNumber(buffer, offset + 12, 4, EFFECT_SAVE_PENALTY));
      }
      else {
        list.add(new Flag(buffer, offset + 8, 4, EFFECT_SAVE_TYPE, saveType));
        list.add(new DecNumber(buffer, offset + 12, 4, EFFECT_SAVE_BONUS));
      }
    } else {
      if (Profile.getEngine() == Profile.Engine.IWD2) {
        list.add(new Flag(buffer, offset, 4, EFFECT_SAVE_TYPE, saveType));
        list.add(new DecNumber(buffer, offset + 4, 4, EFFECT_SAVE_PENALTY));
        list.add(new DecNumber(buffer, offset + 8, 4, EFFECT_PARAMETER));
        list.add(new DecNumber(buffer, offset + 12, 4, EFFECT_PARAMETER));
      }
      else {
        list.add(new UnsignDecNumber(buffer, offset, 4, EFFECT_DICE_COUNT));
        list.add(new UnsignDecNumber(buffer, offset + 4, 4, EFFECT_DICE_SIZE));
        list.add(new Flag(buffer, offset + 8 , 4, EFFECT_SAVE_TYPE, saveType));
        list.add(new DecNumber(buffer, offset + 12, 4, EFFECT_SAVE_BONUS));
      }
    }
    offset += 16;

    return offset;
  }

  /**
   * Handles initialization of the "Special" (or "Parameter 2.5") field.
   *
   * @param parent     The parent structure.
   * @param buffer     Buffer containing raw effects data.
   * @param offset     Buffer offset pointing to the first field after the opcode type field.
   * @param list       List of field data for the current effect.
   * @param resType    Resource type(s) for the resource field. Multiple types are separated by colons.
   *                   Specify {@code #EFFECT_STRING} if resource field specifies a literal string.
   *                   Specify {@code null} if resource field is unused.
   * @param param1     Value of the "Parameter 1" field.
   * @param param2     Value of the "Parameter 2" field
   * @return Offset pointing behind the last initialized effect field.
   */
  protected int makeEffectSpecial(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      String resType, int param1, int param2) {
    if (Profile.getEngine() == Profile.Engine.BG2 && isTobEx()) {
      // related to effect stacking behavior
      list.add(new DecNumber(buffer, offset, 2, EFFECT_IDENTIFIER));
      list.add(new TextString(buffer, offset + 2, 2, EFFECT_PREFIX));
    } else {
      list.add(new DecNumber(buffer, offset, 4, EFFECT_SPECIAL));
    }
    return offset + 4;
  }

  /**
   * This method is called when effect-related fields have enabled their {@link UpdateListener} for content changes.
   * It should be overridden by subclasses that provide content-aware changes to the effect structure.
   *
   * @param struct The effect structure to update.
   * @return {@code true} if fields within the effect structure have been updated, {@code false} otherwise.
   * @throws Exception If the argument doesn't specify a valid effect structure.
   */
  protected boolean update(AbstractStruct struct) throws Exception {
    return false;
  }
}
