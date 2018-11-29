// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.ColorPicker;
import org.infinity.datatype.ColorValue;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.EffectType;
import org.infinity.datatype.Flag;
import org.infinity.datatype.HashBitmap;
import org.infinity.datatype.IdsBitmap;
import org.infinity.datatype.IdsFlag;
import org.infinity.datatype.IdsTargetType;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.MultiNumber;
import org.infinity.datatype.PriTypeBitmap;
import org.infinity.datatype.ProRef;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SecTypeBitmap;
import org.infinity.datatype.SpellProtType;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.Summon2daBitmap;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.UnsignDecNumber;
import org.infinity.datatype.UpdateListener;
import org.infinity.resource.are.Actor;
import org.infinity.resource.itm.ItmResource;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.LongIntegerHashMap;
import org.infinity.util.StringTable;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.ByteBufferOutputStream;
import org.infinity.util.io.StreamUtils;

public final class EffectFactory
{
  // Effect-specific field labels
  public static final String EFFECT_PARAMETER_1           = "Parameter 1";
  public static final String EFFECT_PARAMETER_2           = "Parameter 2";
  public static final String EFFECT_SPECIAL               = "Special";
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

  private static EffectFactory efactory;
  private String[] s_poricon;
  private String[] s_effname;

  /**
   * Used in conjunction with {@code getEffectStructure} to address specific fields within
   * an effect structure.
   */
  public static enum EffectEntry {
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
    IDX_RESOURCE2,            OFS_RESOURCE2,
    IDX_RESOURCE3,            OFS_RESOURCE3,
    IDX_UNKNOWN068,           OFS_UNKNOWN068,
    IDX_UNKNOWN06C,           OFS_UNKNOWN06C,
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

  // contains IDS mappings for BGEE's opcode 319 "Item Usability"
  public static final LongIntegerHashMap<String> m_duration = new LongIntegerHashMap<String>();
  public static final LongIntegerHashMap<String> m_colorloc = new LongIntegerHashMap<String>();
  public static final LongIntegerHashMap<String> m_proj_iwd = new LongIntegerHashMap<String>();
  public static final String[] s_inctype = {"Increment", "Set", "Set % of"};
  public static final String[] s_yesno = {"Yes", "No"};
  public static final String[] s_noyes = {"No", "Yes"};

  public static final String[] s_visuals = {
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
    "Darktree attack"};
  public static final String[] s_lighting = {
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
    "Shaft light", "Shaft white", "Hit door", "Hit finger of death"};
  public static final String[] s_cretype = {
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
    "Keg", "Not keg", "Outsider", "Not outsider"};
  public static final String[] s_sumanim = {"No animation", "Monster summoning circle",
                                            "Animal summoning circle", "Earth summoning circle",
                                            "Fire summoning circle", "Water summoning circle", "",
                                            "Puff of smoke"};
  public static final String[] s_sparklecolor = {"", "Black", "Blue", "Chromatic", "Gold", "Green",
                                                 "Purple", "Red", "White", "Ice", "Stone", "Magenta",
                                                 "Orange"};
  public static final String[] s_actype = {"All weapons", "Crushing weapons", "Missile weapons",
                                           "Piercing weapons", "Slashing weapons", "Set base AC to value"};
  public static final String[] s_damagetype = {"All", "Fire damage", "Cold damage",
                                               "Electricity damage", "Acid damage", "Magic damage",
                                               "Poison damage", "Slashing damage", "Piercing damage",
                                               "Crushing damage", "Missile damage"};
  public static final String[] s_button = {"Stealth", "Thieving", "Spell select", "Quick spell 1",
                                           "Quick spell 2", "Quick spell 3", "Turn undead", "Talk",
                                           "Use item", "Quick item 1", "", "Quick item 2",
                                           "Quick item 3", "Special abilities"};
  public static final String[] s_button_iwd2 = {"Stealth", "Thieving", "Cast spell", "Quick spell 0",
                                                "Quick spell 1", "Quick spell 2", "Quick spell 3",
                                                "Quick spell 4", "Quick spell 5", "Quick spell 6",
                                                "Quick spell 7", "Quick spell 8", "Bard song",
                                                "Quick song 0", "Quick song 1", "Quick song 2",
                                                "Quick song 3", "Quick song 4", "Quick song 5",
                                                "Quick song 6", "Quick song 7", "Quick song 8",
                                                "Quick skill 0", "Quick skill 1", "Quick skill 2",
                                                "Quick skill 3", "Quick skill 4", "Quick skill 5",
                                                "Quick skill 6", "Quick skill 7", "Quick skill 8"};
  public static final String[] s_school = {"None", "Abjuration", "Conjuration", "Divination",
                                           "Enchantment", "Illusion", "Evocation",
                                           "Necromancy", "Alteration", "Generalist"};
  public static final String[] s_attacks = {"0 attacks per round", "1 attack per round",
                                            "2 attacks per round", "3 attacks per round",
                                            "4 attacks per round", "5 attacks per round",
                                            "0.5 attack per round", "1.5 attacks per round",
                                            "2.5 attacks per round", "3.5 attacks per round",
                                            "4.5 attacks per round"};
  public static final String[] s_summoncontrol = {"Match target", "Match target", "From CRE file",
                                                  "Match target", "From CRE file", "Hostile",
                                                  "From CRE file", "", "From CRE file"};
  public static final String[] s_regentype = {"Amount HP per second", "Amount HP percentage per second",
                                              "Amount HP per second", "1 HP per amount seconds",
                                              "Parameter3 HP per amount seconds"};
  public static final String[] s_regentype_iwd = {"Amount HP per second", "Amount HP percentage per second",
                                                  "Amount HP per second", "1 HP per amount seconds",
                                                  "Amount HP per round"};
  public static final String[] s_savetype = {"No save", "Spell", "Breath weapon",
                                             "Paralyze/Poison/Death", "Rod/Staff/Wand",
                                             "Petrify/Polymorph", "", "", "", "", "",
                                             "EE: Ignore primary target;Line AoE projectile doesn't affect end target",
                                             "EE: Ignore secondary target;Line AoE projectile doesn't affect bystanders",
                                             "", "", "", "", "", "", "", "", "", "", "", "",
                                             "EE/Ex: Bypass mirror image", "EE: Ignore difficulty"};
  public static final String[] s_savetype_tobex = {"No save", "Spell", "Breath weapon",
                                                   "Paralyze/Poison/Death", "Rod/Staff/Wand",
                                                   "Petrify/Polymorph", "", "", "",
                                                   "", "", "", "",
                                                   "", "", "", "", "", "", "", "", "", "", "", "",
                                                   "EE/Ex: Bypass mirror image", "Ex: Limit stacking"};
  public static final String[] s_savetype2 = {"No save", "", "", "Fortitude", "Reflex", "Will"};
  public static final String[] s_spellstate = {"Chaotic Command", "Miscast Magic", "Pain",
                                               "Greater Malison", "Blood Rage", "Cat's Grace",
                                               "Mold Touch", "Shroud of Flame"};


  static {
    m_duration.put(0L, "Instant/Limited");
    m_duration.put(1L, "Instant/Permanent until death");
    m_duration.put(2L, "Instant/While equipped");
    m_duration.put(3L, "Delay/Limited");
    m_duration.put(4L, "Delay/Permanent");
    m_duration.put(5L, "Delay/While equipped");
    m_duration.put(6L, "Limited after duration");
    m_duration.put(7L, "Permanent after duration");
    m_duration.put(8L, "Equipped after duration");
    m_duration.put(9L, "Instant/Permanent");
    m_duration.put(10L, "Instant/Limited (ticks)");
    m_duration.put(4096L, "Absolute duration");

    m_colorloc.put(0x00L, "Armor (grey): Belt/Amulet");
    m_colorloc.put(0x01L, "Armor (teal): Minor color");
    m_colorloc.put(0x02L, "Armor (pink): Major color");
    m_colorloc.put(0x03L, "Armor (yellow): Skin color");
    m_colorloc.put(0x04L, "Armor (red): Strap/Leather");
    m_colorloc.put(0x05L, "Armor (blue): Armor/Trimming");
    m_colorloc.put(0x06L, "Armor (green): Hair");
    m_colorloc.put(0x10L, "Weapon (grey): Head/blade/staff major");
    m_colorloc.put(0x11L, "Weapon (teal): Staff minor");
    m_colorloc.put(0x12L, "Weapon (pink)");
    m_colorloc.put(0x13L, "Weapon (yellow)");
    m_colorloc.put(0x14L, "Weapon (red): Grip/staff minor");
    m_colorloc.put(0x15L, "Weapon (blue): Head/blade minor");
    m_colorloc.put(0x16L, "Weapon (green)");
    m_colorloc.put(0x20L, "Shield (grey): Hub");
    m_colorloc.put(0x21L, "Shield (teal): Interior");
    m_colorloc.put(0x22L, "Shield (pink): Panel");
    m_colorloc.put(0x23L, "Shield (yellow)");
    m_colorloc.put(0x24L, "Shield (red): Grip");
    m_colorloc.put(0x25L, "Shield (blue): Body/trim");
    m_colorloc.put(0x26L, "Shield (green)");
    m_colorloc.put(0x30L, "Helmet (grey): Wings");
    m_colorloc.put(0x31L, "Helmet (teal): Detail");
    m_colorloc.put(0x32L, "Helmet (pink): Plume");
    m_colorloc.put(0x33L, "Helmet (yellow)");
    m_colorloc.put(0x34L, "Helmet (red): Face");
    m_colorloc.put(0x35L, "Helmet (blue): Exterior");
    m_colorloc.put(0x36L, "Helmet (green)");
    m_colorloc.put(0xffL, "Character color");

    m_proj_iwd.put(0L, "Instant");
    m_proj_iwd.put(1L, "Arrow");
    m_proj_iwd.put(2L, "Arrow Exploding");
    m_proj_iwd.put(3L, "Arrow Flaming");
    m_proj_iwd.put(4L, "Arrow Heavy*");
    m_proj_iwd.put(5L, "Arrow (Non-Magical)");
    m_proj_iwd.put(6L, "Axe");
    m_proj_iwd.put(7L, "Axe Exploding");
    m_proj_iwd.put(8L, "Axe Flaming");
    m_proj_iwd.put(9L, "Axe Heavy*");
    m_proj_iwd.put(10L, "Axe (Non-Magical)");
    m_proj_iwd.put(11L, "Bolt");
    m_proj_iwd.put(12L, "Bolt Exploding");
    m_proj_iwd.put(13L, "Bolt Flaming");
    m_proj_iwd.put(14L, "Bolt Heavy*");
    m_proj_iwd.put(15L, "Bolt (Non-Magical)");
    m_proj_iwd.put(16L, "Bullet");
    m_proj_iwd.put(17L, "Bullet Exploding");
    m_proj_iwd.put(18L, "Bullet Flaming");
    m_proj_iwd.put(19L, "Bullet Heavy*");
    m_proj_iwd.put(20L, "Bullet (Non-Magical)");
    m_proj_iwd.put(26L, "Dagger*");
    m_proj_iwd.put(27L, "Dagger Exploding");
    m_proj_iwd.put(28L, "Dagger Flaming");
    m_proj_iwd.put(29L, "Dagger Heavy");
    m_proj_iwd.put(30L, "Dagger (Non-Magical)");
    m_proj_iwd.put(31L, "Dart");
    m_proj_iwd.put(32L, "Dart Exploding");
    m_proj_iwd.put(33L, "Dart Flaming");
    m_proj_iwd.put(34L, "Dart Heavy*");
    m_proj_iwd.put(35L, "Dart (Non-Magical)");
    m_proj_iwd.put(36L, "Magic Missile");
    m_proj_iwd.put(37L, "Fireball");
    m_proj_iwd.put(39L, "Lightning Bolt");
    m_proj_iwd.put(41L, "Sleep");
    m_proj_iwd.put(55L, "Spear");
    m_proj_iwd.put(56L, "Spear Exploding");
    m_proj_iwd.put(57L, "Spear Flaming");
    m_proj_iwd.put(58L, "Spear Heaby");
    m_proj_iwd.put(59L, "Spear (Non-Magical)");
    m_proj_iwd.put(62L, "Web Travel");
    m_proj_iwd.put(63L, "Web Ground");
    m_proj_iwd.put(64L, "Gaze");
    m_proj_iwd.put(65L, "Holy Might");
    m_proj_iwd.put(66L, "Flame Strike");
    m_proj_iwd.put(67L, "Magic Missile 1");
    m_proj_iwd.put(68L, "Magic Missile 2");
    m_proj_iwd.put(69L, "Magic Missile 3");
    m_proj_iwd.put(70L, "Magic Missile 4");
    m_proj_iwd.put(71L, "Magic Missile 5");
    m_proj_iwd.put(72L, "Magic Missile 6");
    m_proj_iwd.put(73L, "Magic Missile 7");
    m_proj_iwd.put(74L, "Magic Missile 8");
    m_proj_iwd.put(75L, "Magic Missile 9");
    m_proj_iwd.put(76L, "Magic Missile 10");
    m_proj_iwd.put(94L, "Cloud");
    m_proj_iwd.put(95L, "Skull Trap");
    m_proj_iwd.put(96L, "Colour Spray");
    m_proj_iwd.put(97L, "Ice Storm");
    m_proj_iwd.put(98L, "Fire Wall");
    m_proj_iwd.put(99L, "Glyph");
    m_proj_iwd.put(100L, "Grease");
    m_proj_iwd.put(101L, "Flame Arrow Green");
    m_proj_iwd.put(102L, "Flame Arrow Blue");
    m_proj_iwd.put(103L, "Fireball Green");
    m_proj_iwd.put(104L, "FireBall Blue");
    m_proj_iwd.put(105L, "Potion");
    m_proj_iwd.put(106L, "Potion Exploding");
    m_proj_iwd.put(107L, "Acid Blob");
    m_proj_iwd.put(108L, "Scorcher");
    m_proj_iwd.put(109L, "Travel Door");
    m_proj_iwd.put(186L, "Cloudkill");
    m_proj_iwd.put(187L, "Flame Arrow Ice");
    m_proj_iwd.put(188L, "Cow");
    m_proj_iwd.put(189L, "Hold");
    m_proj_iwd.put(190L, "Scorcher Ice");
    m_proj_iwd.put(191L, "Acid Blob Mustard");
    m_proj_iwd.put(192L, "Acid Blob Grey");
    m_proj_iwd.put(193L, "Acid Blob Ochre");
    m_proj_iwd.put(217L, "Icewind Magic Missile");
    m_proj_iwd.put(313L, "Modenkainen's Force Missiles");
    m_proj_iwd.put(345L, "Sekolah's Fire");
  }

  public static EffectFactory getFactory()
  {
    if (efactory == null)
      efactory = new EffectFactory();
    return efactory;
  }

  public static void init()
  {
    efactory = null;
  }

  /** Returns the save type flags description table depending on the current game. */
  public static String[] getSaveType()
  {
    if (Profile.getEngine() == Profile.Engine.IWD2) {
      return s_savetype2;
    } else if ((Boolean)Profile.getProperty(Profile.Key.IS_GAME_TOBEX)) {
      return s_savetype_tobex;
    } else {
      return s_savetype;
    }
  }

  /**
   * Creates and returns an index/offset map of the current effect structure which can be used
   * to address specific fields within the effect.
   * @param struct The effect structure to map.
   * @return A map containing table indices and structure offsets, starting with the opcode field.
   * @throws Exception If struct doesn't contain a valid effect structure.
   */
  public static EnumMap<EffectEntry, Integer> getEffectStructure(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      EffectType effType = (EffectType)struct.getAttribute(EffectType.EFFECT_TYPE);
      if (effType != null) {
        EnumMap<EffectEntry, Integer> map = new EnumMap<EffectFactory.EffectEntry, Integer>(EffectEntry.class);
        boolean isV1 = (effType.getSize() == 2);
        int ofsOpcode = effType.getOffset();
        int idxOpcode = struct.getIndexOf(struct.getAttribute(ofsOpcode));
        if (isV1 && struct.getSize() >= 0x30) {
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
        } else if (!isV1 && struct.getSize() >= 0x100) {
          // EFF V2.0
          map.put(EffectEntry.IDX_OPCODE, idxOpcode);
          map.put(EffectEntry.OFS_OPCODE, ofsOpcode);
          map.put(EffectEntry.IDX_TARGET, idxOpcode + 1);
          map.put(EffectEntry.OFS_TARGET, ofsOpcode + 0x04);
          map.put(EffectEntry.IDX_POWER, idxOpcode + 2);
          map.put(EffectEntry.OFS_POWER, ofsOpcode + 0x08);
          map.put(EffectEntry.IDX_PARAM1, idxOpcode + 3);
          map.put(EffectEntry.OFS_PARAM1, ofsOpcode + 0x0C);
          map.put(EffectEntry.IDX_PARAM2, idxOpcode + 4);
          map.put(EffectEntry.OFS_PARAM2, ofsOpcode + 0x10);
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
          map.put(EffectEntry.IDX_RESOURCE2, idxOpcode + 22);
          map.put(EffectEntry.OFS_RESOURCE2, ofsOpcode + 0x58);
          map.put(EffectEntry.IDX_RESOURCE3, idxOpcode + 23);
          map.put(EffectEntry.OFS_RESOURCE3, ofsOpcode + 0x60);
          map.put(EffectEntry.IDX_UNKNOWN068, idxOpcode + 24);
          map.put(EffectEntry.OFS_UNKNOWN068, ofsOpcode + 0x68);
          map.put(EffectEntry.IDX_UNKNOWN06C, idxOpcode + 25);
          map.put(EffectEntry.OFS_UNKNOWN06C, ofsOpcode + 0x6C);
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
   * Returns the StructEntry object, specified by the EffectEntry argument.
   * @param struct The structure that contains the requested entry.
   * @param id Indicates which effect field to return.
   * @return The StructEntry instance specified by the id.
   * @throws Exception If one or more arguments are invalid.
   */
  public static StructEntry getEntry(AbstractStruct struct, EffectEntry id) throws Exception
  {
    StructEntry retVal = null;
    EnumMap<EffectEntry, Integer> map = getEffectStructure(struct);
    if (map != null && map.containsKey(id)) {
      retVal = getEntryByIndex(struct, map.get(id));
    }
    return retVal;
  }

  /**
   * Returns the StructEntry object at the specified index. Use in conjunction with getEffectStructure.
   * @param struct The structure that contains the requested entry.
   * @param entryIndex The index of the requested entry.
   * @return The entry at the specified index
   * @throws Exception If one or more arguments are invalid.
   */
  public static StructEntry getEntryByIndex(AbstractStruct struct, int entryIndex) throws Exception
  {
    if (struct != null) {
      if (entryIndex >= 0 && entryIndex < struct.getList().size()) {
        return struct.getList().get(entryIndex);
      } else
        throw new Exception("Index out of bounds");
    } else
      throw new Exception("Invalid arguments specified");
  }

  /**
   * Returns the data associated with the specified structure entry.
   * @param entry The structure entry to fetch data from.
   * @return Data as ByteBuffer object.
   */
  public static ByteBuffer getEntryData(StructEntry entry)
  {
    ByteBuffer bb = StreamUtils.getByteBuffer(entry.getSize());
    try (ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb)) {
      entry.write(bbos);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    return bb;
  }

  /**
   * Convenience function to retrieve data associated with a structure entry within struct.
   * @param struct The structure that contains the structure entry
   * @param id Indicates which effect field to process.
   * @return Data as ByteBuffer object
   */
  public static ByteBuffer getEntryData(AbstractStruct struct, EffectEntry id)
  {
    if (struct != null) {
      try {
        EnumMap<EffectEntry, Integer> map = getEffectStructure(struct);
        if (map != null && map.containsKey(id)) {
          int idx = map.get(id);
          if (idx >= 0 && idx < struct.getList().size()) {
            return getEntryData(struct.getList().get(idx));
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  /**
   * Replaces a data entry in struct with the specified new entry.
   * @param struct The struct which contains the entry specified by entryIndex and entryOffset.
   * @param index The index key for the entry within struct.
   * @param offset The offset key for the data entry.
   * @param newEntry The new entry which replaces the old one.
   */
  public static void replaceEntry(AbstractStruct struct, EffectEntry index, EffectEntry offset,
                                  StructEntry newEntry) throws Exception
  {
    EnumMap<EffectEntry, Integer> map = getEffectStructure(struct);
    if (struct != null && newEntry != null &&
        map != null && map.containsKey(index) && map.containsKey(offset)) {
      int idx = map.get(index);
      int ofs = map.get(offset);
      List<StructEntry> list = struct.getList();
      if (list != null &&
          idx >= 0 && idx < list.size() &&
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
   * @param struct The effect structure to update.
   * @return true if fields within the effect structure have been updated, false otherwise.
   * @throws Exception If the argument doesn't specify a valid effect structure.
   */
  public static boolean updateOpcode(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      EffectType effType = (EffectType)getEntry(struct, EffectEntry.IDX_OPCODE);
      if (effType != null) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        switch (opcode) {
          case 1: // Modify attacks per round
            return updateOpcode1(struct);
          case 23:  // Reset morale
            return updateOpcode23(struct);
          case 78:  // Disease
              return updateOpcode78(struct);
          case 232:     // Cast spell on condition
            return updateOpcode232(struct);
          case 233:     // Modify proficiencies
            return updateOpcode233(struct);
          case 319:     // Item Usability
            return updateOpcode319(struct);
          case 328:     // Set spell state
            return updateOpcode328(struct);
          case 342:     // Override creature data
            return updateOpcode342(struct);
        }
      }
    }
    return false;
  }

  // Effect type "Modify attacks per round" (1)
  private static boolean updateOpcode1(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 1) {
          int param2 = ((Bitmap)getEntry(struct, EffectEntry.IDX_PARAM2)).getValue();
          if (param2 == 2) {  // Set % of
            replaceEntry(struct, EffectEntry.IDX_PARAM1, EffectEntry.OFS_PARAM1,
                         new DecNumber(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, "Value"));
          } else {
            replaceEntry(struct, EffectEntry.IDX_PARAM1, EffectEntry.OFS_PARAM1,
                         new Bitmap(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, "Value", s_attacks));
          }
          return true;
        }
      }
    }
    return false;
  }

  // Effect type "Reset morale" (23)
  private static boolean updateOpcode23(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 23) {
          int special = ((Bitmap)getEntry(struct, EffectEntry.IDX_SPECIAL)).getValue();
          if (special == 0 ) {
            // Activate BG2 mode
            replaceEntry(struct, EffectEntry.IDX_PARAM1, EffectEntry.OFS_PARAM1,
                new DecNumber(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, AbstractStruct.COMMON_UNUSED));
            replaceEntry(struct, EffectEntry.IDX_PARAM2, EffectEntry.OFS_PARAM2,
                new DecNumber(getEntryData(struct, EffectEntry.IDX_PARAM2), 0, 4, AbstractStruct.COMMON_UNUSED));
          } else {
            // Activate BG1 mode
            replaceEntry(struct, EffectEntry.IDX_PARAM1, EffectEntry.OFS_PARAM1,
                new DecNumber(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, "Value"));
            replaceEntry(struct, EffectEntry.IDX_PARAM2, EffectEntry.OFS_PARAM2,
                new Bitmap(getEntryData(struct, EffectEntry.IDX_PARAM2), 0, 4, "Modifier type", s_inctype));
          }
          return true;
        }
      }
    }
    return false;
  }

  // Effect type "Disease" (78)
  private static boolean updateOpcode78(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 78) {
          int param2 = ((Bitmap)getEntry(struct, EffectEntry.IDX_PARAM2)).getValue();
          switch (param2) {
            case 11:  // Mold Touch/Single
            case 12:  // Mold Touch/Decrement
              replaceEntry(struct, EffectEntry.IDX_RESOURCE, EffectEntry.OFS_RESOURCE,
                           new ResourceRef(getEntryData(struct, EffectEntry.IDX_RESOURCE), 0, 8,
                                           EFFECT_RESOURCE, "SPL"));
              break;
            default:
              replaceEntry(struct, EffectEntry.IDX_RESOURCE, EffectEntry.OFS_RESOURCE,
                           new Unknown(getEntryData(struct, EffectEntry.IDX_RESOURCE), 0, 8,
                                       AbstractStruct.COMMON_UNUSED));
              break;
          }
          return true;
        }
      }
    }
    return false;
  }

  // Effect type "Cast spell on condition" (232)
  private static boolean updateOpcode232(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 232) {
          int param2 = ((Bitmap)getEntry(struct, EffectEntry.IDX_PARAM2)).getValue();
          switch (param2) {
            case 13: // Time of day
              replaceEntry(struct, EffectEntry.IDX_SPECIAL, EffectEntry.OFS_SPECIAL,
                           new IdsBitmap(getEntryData(struct, EffectEntry.IDX_SPECIAL), 0, 4,
                                         EFFECT_SPECIAL, "TIMEODAY.IDS"));
              break;
            case 15: // State
              replaceEntry(struct, EffectEntry.IDX_SPECIAL, EffectEntry.OFS_SPECIAL,
                           new IdsFlag(getEntryData(struct, EffectEntry.IDX_SPECIAL), 0, 4,
                                       EFFECT_SPECIAL, "STATE.IDS"));
              break;
            case 21:
              replaceEntry(struct, EffectEntry.IDX_SPECIAL, EffectEntry.OFS_SPECIAL,
                           new IdsBitmap(getEntryData(struct, EffectEntry.IDX_SPECIAL), 0, 4,
                                         EFFECT_SPECIAL, "SPLSTATE.IDS"));
              break;
            default:
              replaceEntry(struct, EffectEntry.IDX_SPECIAL, EffectEntry.OFS_SPECIAL,
                           new DecNumber(getEntryData(struct, EffectEntry.IDX_SPECIAL), 0, 4, EFFECT_SPECIAL));
          }
          return true;
        }
      }
    }
    return false;
  }

  // Effect type "Modify proficiencies" (233).
  private static boolean updateOpcode233(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 233) {
          boolean signed = ((MultiNumber)getEntry(struct, EffectEntry.IDX_PARAM1)).isSigned();
          int mode = ((IsNumeric)getEntry(struct, EffectEntry.IDX_PARAM2B)).getValue();
          if (signed ^ (mode == 1)) {
            replaceEntry(struct, EffectEntry.IDX_PARAM1, EffectEntry.OFS_PARAM1,
                         new MultiNumber(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, "# stars",
                                         3, 2, new String[]{"Active class", "Original class"}, mode == 1));
            return true;
          }
        }
      }
    }
    return false;
  }

  // Effect type "Item Usability" (319).
  private static boolean updateOpcode319(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 319) {
          // updating resource field
          StructEntry entry = getEntry(struct, EffectEntry.IDX_PARAM2);
          if (entry instanceof IdsTargetType) {
            StructEntry resourceEntry =
                ((IdsTargetType)entry).createResourceFromType(getEntryData(struct, EffectEntry.IDX_RESOURCE), 0);
            replaceEntry(struct, EffectEntry.IDX_RESOURCE, EffectEntry.OFS_RESOURCE, resourceEntry);
            return true;
          }
        }
      }
    }
    return false;
  }

  // Effect type "Set State" (328)
  private static boolean updateOpcode328(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 328) {
          int special = ((Bitmap)getEntry(struct, EffectEntry.IDX_SPECIAL)).getValue();
          if (special == 1 && ResourceFactory.resourceExists("SPLSTATE.IDS")) {
            // Activate IWD2 mode
            replaceEntry(struct, EffectEntry.IDX_PARAM2, EffectEntry.OFS_PARAM2,
                new IdsBitmap(getEntryData(struct, EffectEntry.IDX_PARAM2), 0, 4, "State", "SPLSTATE.IDS"));
          } else {
            // Activate IWD1 mode
            replaceEntry(struct, EffectEntry.IDX_PARAM2, EffectEntry.OFS_PARAM2,
                new Bitmap(getEntryData(struct, EffectEntry.IDX_PARAM2), 0, 4, "State", s_spellstate));
          }
          return true;
        }
      }
    }
    return false;
  }

  // Effect type "Override creature data" (342)
  private static boolean updateOpcode342(AbstractStruct struct) throws Exception
  {
    if (struct != null) {
      if (Profile.isEnhancedEdition()) {
        int opcode = ((EffectType)getEntry(struct, EffectEntry.IDX_OPCODE)).getValue();
        if (opcode == 342) {
          int param2 = ((IsNumeric)getEntry(struct, EffectEntry.IDX_PARAM2)).getValue();
          StructEntry newEntry = null;
          switch (param2) {
            case 1:
              newEntry = new Bitmap(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, "Enabled?", s_noyes);
              break;
            case 2:
              newEntry = new ColorValue(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, "Color");
              break;
            default:
              newEntry = new DecNumber(getEntryData(struct, EffectEntry.IDX_PARAM1), 0, 4, "Value");
          }
          replaceEntry(struct, EffectEntry.IDX_PARAM1, EffectEntry.OFS_PARAM1, newEntry);
          return true;
        }
      }
    }
    return false;
  }

  public EffectFactory()
  {
    s_effname = null;
    switch (Profile.getEngine()) {
      case BG1:
        s_effname = new String[]{
            // 0..9
            "AC bonus", "Modify attacks per round", "Cure sleep", "Berserk", "Cure berserk",
            "Charm creature", "Charisma bonus", "Set color", "Set color glow solid",
            "Set color glow pulse",
            // 10..19
            "Constitution bonus", "Cure poison", "Damage", "Kill target", "Defrost",
            "Dexterity bonus", "Haste", "Current HP bonus", "Maximum HP bonus", "Intelligence bonus",
            // 20..29
            "Invisibility", "Lore bonus", "Luck bonus", "Morale bonus", "Panic", "Poison",
            "Remove curse", "Acid resistance bonus", "Cold resistance bonus",
            "Electricity resistance bonus",
            // 30..39
            "Fire resistance bonus", "Magic damage resistance bonus", "Raise dead",
            "Save vs. death bonus", "Save vs. wand bonus",  "Save vs. polymorph bonus",
            "Save vs. breath bonus", "Save vs. spell bonus", "Silence", "Sleep",
            // 40..49
            "Slow", "Sparkle", "Bonus wizard spells", "Stone to flesh", "Strength bonus", "Stun",
            "Cure stun", "Remove invisibility", "Vocalize", "Wisdom bonus",
            // 50..59
            "Character color pulse", "Character tint solid", "Character tint bright",
            "Animation change", "Base THAC0 bonus", "Slay", "Invert alignment", "Change alignment",
            "Dispel effects", "Stealth bonus",
            // 60..69
            "Casting failure", "Unknown (61)", "Bonus priest spells", "Infravision",
            "Remove infravision", "Blur", "Translucency", "Summon creature", "Unsummon creature",
            "Nondetection",
            // 70..79
            "Remove nondetection", "Change gender", "Change AI type", "Attack damage bonus",
            "Blindness", "Cure blindness", "Feeblemindedness", "Cure feeblemindedness",
            "Disease", "Cure disease",
            // 80..89
            "Deafness", "Cure deafness", "Set AI script", "Immunity to projectile",
            "Magical fire resistance bonus", "Magical cold resistance bonus",
            "Slashing resistance bonus", "Crushing resistance bonus",
            "Piercing resistance bonus", "Missile resistance bonus",
            // 90..99
            "Open locks bonus", "Find traps bonus", "Pick pockets bonus", "Fatigue bonus",
            "Intoxication bonus", "Tracking bonus", "Change level", "Exceptional strength bonus",
            "Regeneration", "Modify duration",
            // 100..109
            "Protection from creature type", "Immunity to effect", "Immunity to spell level",
            "Change name", "XP bonus", "Remove gold", "Morale break", "Change portrait",
            "Reputation bonus", "Paralyze",
            // 110..119
            "Unknown (110)", "Create weapon", "Remove item", "Equip weapon", "Dither",
            "Detect alignment", "Detect invisible", "Clairvoyance", "Show creatures", "Mirror image",
            // 120..129
            "Immunity to weapons", "Visual animation effect", "Create inventory item",
            "Remove inventory item", "Teleport", "Unlock", "Movement rate bonus", "Summon monsters",
            "Confusion", "Aid (non-cumulative)",
            // 130..139
            "Bless (non-cumulative)", "Chant (non-cumulative)", "Draw upon holy might (non-cumulative)",
            "Luck (non-cumulative)", "Petrification", "Polymorph", "Force visible",
            "Bad chant (non-cumulative)", "Set animation sequence", "Display string",
            // 140..149
            "Casting glow", "Lighting effects", "Display portrait icon", "Create item in slot",
            "Disable button", "Disable spellcasting", "Cast spell", "Learn spell",
            "Cast spell at point", "Identify",
            // 150..159
            "Find traps", "Replace self", "Play movie",  "Sanctuary", "Entangle overlay",
            "Minor globe overlay", "Protection from normal missiles overlay", "Web effect",
            "Grease overlay", "Mirror image effect",
            // 160..169
            "Remove sanctuary", "Remove fear", "Remove paralysis", "Free action",
            "Remove intoxication", "Pause target", "Magic resistance bonus", "Missile THAC0 bonus",
            "Remove creature", "Prevent portrait icon",
            // 170..179
            "Play damage animation", "Give innate ability", "Remove spell", "Poison resistance bonus",
            "Play sound", "Hold creature", "Movement rate bonus 2", "Use EFF file",
            "THAC0 vs. type bonus", "Damage vs. type bonus",
            // 180..189
            "Disallow item", "Disallow item type", "Use EFF file (do not use)", "Use EFF file on equip type",
            "No collision detection", "Hold creature 2", "DestroySelf() on target",
            "Set local variable", "Increase spells cast per round", "Increase casting speed factor",
            // 190..
            "Increase attack speed factor", "Casting level bonus"};
        break;

      case BG2:
      case Unknown: // Default list
        s_effname = new String[]{
            // 0..9
            "AC bonus", "Modify attacks per round", "Cure sleep", "Berserk", "Cure berserk",
            "Charm creature", "Charisma bonus", "Set color", "Set color glow solid",
            "Set color glow pulse",
            // 10..19
            "Constitution bonus", "Cure poison", "Damage", "Kill target", "Defrost",
            "Dexterity bonus", "Haste", "Current HP bonus", "Maximum HP bonus", "Intelligence bonus",
            // 20..29
            "Invisibility", "Lore bonus", "Luck bonus", "Reset morale", "Panic", "Poison",
            "Remove curse", "Acid resistance bonus", "Cold resistance bonus",
            "Electricity resistance bonus",
            // 30..39
            "Fire resistance bonus", "Magic damage resistance bonus", "Raise dead",
            "Save vs. death bonus", "Save vs. wand bonus", "Save vs. polymorph bonus",
            "Save vs. breath bonus", "Save vs. spell bonus", "Silence", "Sleep",
            // 40..49
            "Slow", "Sparkle", "Bonus wizard spells", "Stone to flesh", "Strength bonus", "Stun",
            "Cure stun", "Remove invisibility", "Vocalize", "Wisdom bonus",
            // 50..59
            "Character color pulse", "Character tint solid", "Character tint bright",
            "Animation change", "Base THAC0 bonus", "Slay", "Invert alignment", "Change alignment",
            "Dispel effects", "Move silently bonus",
            // 60..69
            "Casting failure", "Unknown (61)", "Bonus priest spells", "Infravision",
            "Remove infravision", "Blur", "Translucency", "Summon creature", "Unsummon creature",
            "Nondetection",
            // 70..79
            "Remove nondetection", "Change gender", "Change AI type", "Attack damage bonus",
            "Blindness", "Cure blindness", "Feeblemindedness", "Cure feeblemindedness", "Disease",
            "Cure disease",
            // 80..89
            "Deafness", "Cure deafness", "Set AI script", "Immunity to projectile",
            "Magical fire resistance bonus", "Magical cold resistance bonus",
            "Slashing resistance bonus", "Crushing resistance bonus", "Piercing resistance bonus",
            "Missile resistance bonus",
            // 90..99
            "Open locks bonus", "Find traps bonus", "Pick pockets bonus", "Fatigue bonus",
            "Intoxication bonus", "Tracking bonus", "Change level", "Exceptional strength bonus",
            "Regeneration", "Modify duration",
            // 100..109
            "Protection from creature type", "Immunity to effect", "Immunity to spell level",
            "Change name", "XP bonus", "Remove gold", "Morale break", "Change portrait",
            "Reputation bonus", "Paralyze",
            // 110..119
            "Retreat from", "Create weapon", "Remove item", "Equip weapon", "Dither",
            "Detect alignment", "Detect invisible", "Clairvoyance",  "Show creatures", "Mirror image",
            // 120..129
            "Immunity to weapons", "Visual animation effect", "Create inventory item",
            "Remove inventory item", "Teleport", "Unlock", "Movement rate bonus", "Summon monsters",
            "Confusion", "Aid (non-cumulative)",
            // 130..139
            "Bless (non-cumulative)", "Chant (non-cumulative)", "Draw upon holy might (non-cumulative)",
            "Luck (non-cumulative)", "Petrification", "Polymorph", "Force visible",
            "Bad chant (non-cumulative)", "Set animation sequence", "Display string",
            // 140..149
            "Casting glow", "Lighting effects", "Display portrait icon", "Create item in slot",
            "Disable button", "Disable spellcasting", "Cast spell", "Learn spell",
            "Cast spell at point", "Identify",
            // 150..159
            "Find traps", "Replace self", "Play movie", "Sanctuary", "Entangle overlay",
            "Minor globe overlay", "Protection from normal missiles overlay", "Web effect",
            "Grease overlay", "Mirror image effect",
            // 160..169
            "Remove sanctuary", "Remove fear", "Remove paralysis", "Free action",
            "Remove intoxication", "Pause target", "Magic resistance bonus", "Missile THAC0 bonus",
            "Remove creature", "Prevent portrait icon",
            // 170..179
            "Play damage animation", "Give innate ability", "Remove spell", "Poison resistance bonus",
            "Play sound", "Hold creature", "Movement rate bonus 2", "Use EFF file",
            "THAC0 vs. type bonus", "Damage vs. type bonus",
            // 180..189
            "Disallow item", "Disallow item type", "Use EFF file (do not use)",
            "Use EFF file on equip type", "No collision detection", "Hold creature 2",
            "Move creature", "Set local variable", "Increase spells cast per round",
            "Increase casting speed factor",
            // 190..199
            "Increase attack speed factor", "Casting level bonus", "Find familiar",
            "Invisibility detection", "Ignore dialogue pause", "Drain CON and HP on death",
            "Disable familiar", "Physical mirror", "Reflect specified effect", "Reflect spell level",
            // 200..209
            "Spell turning", "Spell deflection", "Reflect spell school", "Reflect spell type",
            "Protection from spell school", "Protection from spell type", "Protection from spell",
            "Reflect specified spell", "Minimum HP", "Power word, kill",
            // 210..219
            "Power word, stun", "Imprisonment", "Freedom", "Maze", "Select spell",
            "Play visual effect", "Level drain", "Power word, sleep", "Stoneskin effect",
            "Attack roll penalty",
            // 220..229
            "Remove spell school protections", "Remove spell type protections", "Teleport field",
            "Spell school deflection", "Restoration", "Detect magic", "Spell type deflection",
            "Spell school turning", "Spell type turning", "Remove protection by school",
            // 230..239
            "Remove protection by type", "Time stop", "Cast spell on condition",
            "Modify proficiencies", "Create contingency", "Wing buffet", "Project image",
            "Set image type", "Disintegrate", "Farsight",
            // 240..249
            "Remove portrait icon", "Control creature", "Cure confusion", "Drain item charges",
            "Drain wizard spells", "Check for berserk", "Berserk effect", "Attack nearest creature",
            "Melee hit effect", "Ranged hit effect",
            // 250..259
            "Maximum damage each hit", "Change bard song", "Set trap", "Set automap note",
            "Remove automap note", "Create item (days)", "Spell sequencer", "Create spell sequencer",
            "Activate spell sequencer", "Spell trap",
            // 260..269
            "Activate spell sequencer at point", "Restore lost spells", "Visual range bonus",
            "Backstab bonus", "Drop item", "Set global variable", "Remove protection from spell",
            "Disable display string", "Clear fog of war", "Shake screen",
            // 270..279
            "Unpause target", "Disable creature", "Use EFF file on condition", "Zone of sweet air",
            "Phase", "Hide in shadows bonus", "Detect illusion bonus", "Set traps bonus",
            "THAC0 bonus", "Enable button",
            // 280..289
            "Wild magic", "Wild surge bonus", "Modify script state", "Use EFF file as curse",
            "Melee THAC0 bonus", "Melee weapon damage bonus", "Missile weapon damage bonus",
            "Remove feet circle", "Fist THAC0 bonus", "Fist damage bonus",
            // 290..299
            "Change title", "Disable visual effects", "Immunity to backstab", "Set persistent AI",
            "Set existence delay", "Disable permanent death", "Immunity to specific animation",
            "Immunity to turn undead", "Pocket plane", "Chaos shield effect",
            // 300..309
            "Modify collision behavior", "Critical hit bonus", "Can use any item",
            "Backstab every hit", "Mass raise dead", "Off-hand THAC0 bonus", "Main hand THAC0 bonus",
            "Tracking", "Immunity to tracking", "Set local variable",
            // 310..
            "Immunity to time stop", "Wish", "Immunity to sequester", "High-level ability",
            "Stoneskin protection", "Remove animation", "Rest", "Haste 2", "Ex: Set stat"};
        break;

      case EE:
        s_effname = new String[]{
            // 0..9
            "AC bonus", "Modify attacks per round", "Cure sleep", "Berserk", "Cure berserk",
            "Charm creature", "Charisma bonus", "Set color", "Set color glow solid",
            "Set color glow pulse",
            // 10..19
            "Constitution bonus", "Cure poison", "Damage", "Kill target", "Defrost",
            "Dexterity bonus", "Haste", "Current HP bonus", "Maximum HP bonus", "Intelligence bonus",
            // 20..29
            "Invisibility", "Lore bonus", "Luck bonus", "Reset morale", "Panic", "Poison",
            "Remove curse", "Acid resistance bonus", "Cold resistance bonus",
            "Electricity resistance bonus",
            // 30..39
            "Fire resistance bonus", "Magic damage resistance bonus", "Raise dead",
            "Save vs. death bonus", "Save vs. wand bonus", "Save vs. polymorph bonus",
            "Save vs. breath bonus", "Save vs. spell bonus", "Silence", "Sleep",
            // 40..49
            "Slow", "Sparkle", "Bonus wizard spells", "Stone to flesh", "Strength bonus", "Stun",
            "Cure stun", "Remove invisibility", "Vocalize", "Wisdom bonus",
            // 50..59
            "Character color pulse", "Character tint solid", "Character tint bright",
            "Animation change", "Base THAC0 bonus", "Slay", "Invert alignment", "Change alignment",
            "Dispel effects", "Move silently bonus",
            // 60..69
            "Casting failure", "Creature RGB color fade", "Bonus priest spells", "Infravision",
            "Remove infravision", "Blur", "Translucency", "Summon creature", "Unsummon creature",
            "Nondetection",
            // 70..79
            "Remove nondetection", "Change gender", "Change AI type", "Attack damage bonus",
            "Blindness", "Cure blindness", "Feeblemindedness", "Cure feeblemindedness", "Disease",
            "Cure disease",
            // 80..89
            "Deafness", "Cure deafness", "Set AI script", "Immunity to projectile",
            "Magical fire resistance bonus", "Magical cold resistance bonus",
            "Slashing resistance bonus", "Crushing resistance bonus", "Piercing resistance bonus",
            "Missile resistance bonus",
            // 90..99
            "Open locks bonus", "Find traps bonus", "Pick pockets bonus", "Fatigue bonus",
            "Intoxication bonus", "Tracking bonus", "Change level", "Exceptional strength bonus",
            "Regeneration", "Modify duration",
            // 100..109
            "Protection from creature type", "Immunity to effect", "Immunity to spell level",
            "Change name", "XP bonus", "Remove gold", "Morale break", "Change portrait",
            "Reputation bonus", "Paralyze",
            // 110..119
            "Retreat from", "Create weapon", "Remove item", "Equip weapon", "Dither",
            "Detect alignment", "Detect invisible", "Clairvoyance",  "Show creatures", "Mirror image",
            // 120..129
            "Immunity to weapons", "Visual animation effect", "Create inventory item",
            "Remove inventory item", "Teleport", "Unlock", "Movement rate bonus", "Summon monsters",
            "Confusion", "Aid (non-cumulative)",
            // 130..139
            "Bless (non-cumulative)", "Chant (non-cumulative)", "Draw upon holy might (non-cumulative)",
            "Luck (non-cumulative)", "Petrification", "Polymorph", "Force visible",
            "Bad chant (non-cumulative)", "Set animation sequence", "Display string",
            // 140..149
            "Casting glow", "Lighting effects", "Display portrait icon", "Create item in slot",
            "Disable button", "Disable spellcasting", "Cast spell", "Learn spell",
            "Cast spell at point", "Identify",
            // 150..159
            "Find traps", "Replace self", "Play movie", "Sanctuary", "Entangle overlay",
            "Minor globe overlay", "Protection from normal missiles overlay", "Web effect",
            "Grease overlay", "Mirror image effect",
            // 160..169
            "Remove sanctuary", "Remove fear", "Remove paralysis", "Free action",
            "Remove intoxication", "Pause target", "Magic resistance bonus", "Missile THAC0 bonus",
            "Remove creature", "Prevent portrait icon",
            // 170..179
            "Play damage animation", "Give innate ability", "Remove spell", "Poison resistance bonus",
            "Play sound", "Hold creature", "Movement rate bonus 2", "Use EFF file",
            "THAC0 vs. type bonus", "Damage vs. type bonus",
            // 180..189
            "Disallow item", "Disallow item type", "Use EFF file (do not use)",
            "Use EFF file while on type", "No collision detection", "Hold creature 2",
            "Move creature", "Set local variable", "Increase spells cast per round",
            "Increase casting speed factor",
            // 190..199
            "Increase attack speed factor", "Casting level bonus", "Find familiar",
            "Invisibility detection", "Ignore dialogue pause", "Drain CON and HP on death",
            "Disable familiar", "Physical mirror", "Reflect specified effect", "Reflect spell level",
            // 200..209
            "Spell turning", "Spell deflection", "Reflect spell school", "Reflect spell type",
            "Protection from spell school", "Protection from spell type", "Protection from spell",
            "Reflect specified spell", "Minimum HP", "Power word, kill",
            // 210..219
            "Power word, stun", "Imprisonment", "Freedom", "Maze", "Select spell",
            "Play visual effect", "Level drain", "Power word, sleep", "Stoneskin effect",
            "Attack roll penalty",
            // 220..229
            "Remove spell school protections", "Remove spell type protections", "Teleport field",
            "Spell school deflection", "Restoration", "Detect magic", "Spell type deflection",
            "Spell school turning", "Spell type turning", "Remove protection by school",
            // 230..239
            "Remove protection by type", "Time stop", "Cast spell on condition",
            "Modify proficiencies", "Create contingency", "Wing buffet", "Project image",
            "Set image type", "Disintegrate", "Farsight",
            // 240..249
            "Remove portrait icon", "Control creature", "Cure confusion", "Drain item charges",
            "Drain wizard spells", "Check for berserk", "Berserk effect", "Attack nearest creature",
            "Melee hit effect", "Ranged hit effect",
            // 250..259
            "Maximum damage each hit", "Change bard song", "Set trap", "Set automap note",
            "Remove automap note", "Create item (days)", "Spell sequencer", "Create spell sequencer",
            "Activate spell sequencer", "Spell trap",
            // 260..269
            "Activate spell sequencer at point", "Restore lost spells", "Visual range bonus",
            "Backstab bonus", "Drop item", "Set global variable", "Remove protection from spell",
            "Disable display string", "Clear fog of war", "Shake screen",
            // 270..279
            "Unpause target", "Disable creature", "Use EFF file on condition", "Zone of sweet air",
            "Phase", "Hide in shadows bonus", "Detect illusion bonus", "Set traps bonus",
            "THAC0 bonus", "Enable button",
            // 280..289
            "Wild magic", "Wild surge bonus", "Modify script state", "Use EFF file as curse",
            "Melee THAC0 bonus", "Melee weapon damage bonus", "Missile weapon damage bonus",
            "Remove feet circle", "Fist THAC0 bonus", "Fist damage bonus",
            // 290..299
            "Change title", "Disable visual effects", "Immunity to backstab", "Set persistent AI",
            "Set existence delay", "Disable permanent death", "Immunity to specific animation",
            "Immunity to turn undead", "Pocket plane", "Chaos shield effect",
            // 300..309
            "Modify collision behavior", "Critical hit bonus", "Can use any item",
            "Backstab every hit", "Mass raise dead", "Off-hand THAC0 bonus", "Main hand THAC0 bonus",
            "Tracking", "Immunity to tracking", "Set local variable",
            // 310..319
            "Immunity to time stop", "Wish", "Immunity to sequester", "High-level ability",
            "Stoneskin protection", "Remove animation", "Rest", "Haste 2", "Protection from spell",
            "Restrict item",
            // 320..329
            "Change weather", "Remove effects by resource", "AoE evade check (deprecated)",
            "Turn undead level", "Immunity to spell and message", "All saving throws bonus",
            "Apply effects list", "Show visual effect", "Set spell state", "Slow poison",
            // 330..339
            "Float text", "Summon creatures 2", "Attack damage type bonus", "Static charge",
            "Turn undead", "Seven eyes", "Seven eyes overlay", "Remove effects by opcode",
            "Disable rest or save", "Alter visual animation effect",
            // 340..349
            "Backstab hit effect", "Critical hit effect", "Override creature data",
            "HP swap", "Enchantment vs. creature type", "Enchantment bonus", "Save vs. school bonus",
            "Move view to target", "Unknown (348)", "Unknown (349)",
            // 350..359
            "Unknown (350)", "Unknown (351)", "Change Background", "Tint screen", "Flash screen",
            "Soul exodus", "Stop all actions", "Set state", "Set AI script", "Unknown (359)",
            // 360..369
            "Ignore reputation breaking point", "Cast spell on critical miss", "Critical miss bonus",
            "Movement rate check", "Unknown (364)", "Make unselectable", "Apply spell on movement",
            "Minimum base stats", "Unknown (368)", "Play BAM file (single/dual)",
            // 370..379
            "Play BAM file", "Play BAM file 2", "Play BAM file 3", "Play BAM file 4",
            "Special spell hit", "Play BAM with effects", "Detect evil", "Unknown (377)", "Prayer",
            "Curse",
            // 380..
            "Embalm", "Induce hiccups", "Fist of iron", "Hit point transfer"};
        break;

      case PST:
        s_effname = new String[]{
            // 0..9
            "AC bonus", "Modify attacks per round", "Cure sleep", "Berserk", "Cure berserk",
            "Charm creature", "Charisma bonus", "Set color", "Set color glow solid",
            "Set color glow pulse",
            // 10..19
            "Constitution bonus", "Cure poison", "Damage", "Kill target", "Defrost", "Dexterity bonus",
            "Haste", "Current HP bonus", "Maximum HP bonus", "Intelligence bonus",
            // 20..29
            "Invisibility", "Lore bonus", "Luck bonus", "Reset morale", "Panic", "Poison",
            "Remove curse", "Acid resistance bonus", "Cold resistance bonus",
            "Electricity resistance bonus",
            // 30..39
            "Fire resistance bonus", "Magic damage resistance bonus", "Raise dead",
            "Save vs. death bonus", "Save vs. wand bonus", "Save vs. polymorph bonus",
            "Save vs. breath bonus", "Save vs. spell bonus", "Silence", "Sleep",
            // 40..49
            "Slow", "Sparkle", "Bonus wizard spells", "Stone to flesh", "Strength bonus", "Stun",
            "Cure stun", "Remove invisibility", "Vocalize", "Wisdom bonus",
            // 50..59
            "Character color pulse", "Character tint solid", "Character tint bright",
            "Animation change", "Base THAC0 bonus", "Slay", "Invert alignment", "Change alignment",
            "Dispel effects", "Stealth bonus",
            // 60..69
            "Casting failure", "Unknown (61)", "Bonus priest spells", "Infravision",
            "Remove infravision", "Blur", "Translucency", "Summon creature", "Unsummon creature",
            "Nondetection",
            // 70..79
            "Remove nondetection", "Change gender", "Change AI type", "Attack damage bonus",
            "Blindness", "Cure blindness", "Feeblemindedness", "Cure feeblemindedness", "Disease",
            "Cure disease",
            // 80..89
            "Deafness", "Cure deafness", "Set AI script", "Immunity to projectile",
            "Magical fire resistance bonus", "Magical cold resistance bonus",
            "Slashing resistance bonus", "Crushing resistance bonus", "Piercing resistance bonus",
            "Missile resistance bonus",
            // 90..99
            "Open locks bonus", "Find traps bonus", "Pick pockets bonus", "Fatigue bonus",
            "Intoxication bonus", "Tracking bonus", "Change level", "Exceptional strength bonus",
            "Regeneration", "Modify duration",
            // 100..109
            "Protection from creature type", "Immunity to effect", "Immunity to spell level",
            "Change name", "XP bonus", "Remove gold", "Morale break", "Change portrait",
            "Reputation bonus", "Paralyze",
            // 110..119
            "Retreat from", "Create weapon", "Remove item", "Equip weapon", "Dither",
            "Detect alignment", "Detect invisible", "Clairvoyance", "Show creatures", "Mirror image",
            // 120..129
            "Immunity to weapons", "Visual animation effect", "Create inventory item",
            "Remove inventory item", "Teleport", "Unlock", "Movement rate bonus", "Summon monsters",
            "Confusion", "Aid (non-cumulative)",
            // 130..139
            "Bless (non-cumulative)", "Chant (non-cumulative)", "Draw upon holy might (non-cumulative)",
            "Luck (non-cumulative)", "Petrification", "Polymorph", "Force visible",
            "Bad chant (non-cumulative)", "Set animation sequence", "Display string",
            // 140..149
            "Casting glow", "Lighting effects", "Display portrait icon", "Create item in slot",
            "Disable button", "Disable spellcasting", "Cast spell", "Learn spell",
            "Cast spell at point", "Identify",
            // 150..159
            "Find traps", "Replace self", "Play movie", "Sanctuary", "Entangle overlay",
            "Minor globe overlay", "Protection from normal missiles overlay", "Web effect",
            "Grease overlay", "Mirror image effect",
            // 160..169
            "Remove sanctuary", "Remove fear", "Remove paralysis", "Free action",
            "Remove intoxication", "Pause target", "Magic resistance bonus", "Missile THAC0 bonus",
            "Remove creature", "Prevent portrait icon",
            // 170..179
            "Play damage animation", "Give innate ability", "Remove spell", "Poison resistance bonus",
            "Play sound", "Hold creature", "Unknown (176)", "Unknown (177)",
            "Unknown (178)", "Unknown (179)",
            // 180..189
            "Unknown (180)", "Unknown (181)", "Unknown (182)", "Unknown (183)", "Unknown (184)",
            "Unknown (185)", "Set state", "Play BAM file (single/dual)", "Play BAM file",
            "Play BAM file 2",
            // 190..199
            "Play BAM file 3", "Play BAM file 4", "Hit point transfer", "Shake screen",
            "Flash screen", "Tint screen", "Special spell hit", "Unknown (197)", "Unknown (198)",
            "Unknown (199)",
            // 200..209
            "Unknown (200)", "Play BAM with effects", "Unknown (202)", "Curse", "Prayer",
            "Move view to target", "Embalm", "Stop all actions", "Fist of iron", "Soul exodus",
            // 210..
            "Detect evil", "Induce hiccups", "Speak with dead"};
        break;

      case IWD:
        s_effname = new String[]{
            // 0..9
            "AC bonus", "Modify attacks per round", "Cure sleep", "Berserk", "Cure berserk",
            "Charm creature", "Charisma bonus", "Set color", "Set color glow solid",
            "Set color glow pulse",
            // 10..19
            "Constitution bonus", "Cure poison", "Damage", "Kill target", "Defrost",
            "Dexterity bonus", "Haste", "Current HP bonus", "Maximum HP bonus", "Intelligence bonus",
            // 20..29
            "Invisibility", "Lore bonus", "Luck bonus", "Morale bonus", "Panic", "Poison",
            "Remove curse", "Acid resistance bonus", "Cold resistance bonus",
            "Electricity resistance bonus",
            // 30..39
            "Fire resistance bonus", "Magic damage resistance bonus", "Raise dead",
            "Save vs. death bonus", "Save vs. wand bonus", "Save vs. polymorph bonus",
            "Save vs. breath bonus", "Save vs. spell bonus", "Silence", "Sleep",
            // 40..49
            "Slow", "Sparkle", "Bonus wizard spells", "Stone to flesh", "Strength bonus", "Stun",
            "Cure stun", "Remove invisibility", "Vocalize", "Wisdom bonus",
            // 50..59
            "Character color pulse", "Character tint solid", "Character tint bright",
            "Animation change", "Base THAC0 bonus", "Slay", "Invert alignment", "Change alignment",
            "Dispel effects", "Stealth bonus",
            // 60..69
            "Casting failure", "Unknown (61)", "Bonus priest spells", "Infravision",
            "Remove infravision", "Blur", "Translucency", "Summon creature", "Unsummon creature",
            "Nondetection",
            // 70..79
            "Remove nondetection", "Change gender", "Change AI type", "Attack damage bonus",
            "Blindness", "Cure blindness", "Feeblemindedness", "Cure feeblemindedness", "Disease",
            "Cure disease",
            // 80..89
            "Deafness", "Cure deafness", "Set AI script", "Immunity to projectile",
            "Unknown (84)", "Unknown (85)", "Slashing resistance bonus", "Crushing resistance bonus",
            "Piercing resistance bonus", "Missile resistance bonus",
            // 90..99
            "Open locks bonus", "Find traps bonus", "Pick pockets bonus", "Fatigue bonus",
            "Intoxication bonus", "Tracking bonus", "Change level", "Exceptional strength bonus",
            "Regeneration", "Modify duration",
            // 100..109
            "Protection from creature type", "Immunity to effect", "Immunity to spell level",
            "Change name", "XP bonus", "Remove gold", "Morale break", "Change portrait",
            "Reputation bonus", "Paralyze",
            // 110..119
            "Unknown (110)", "Create weapon", "Remove item", "Unknown (113)", "Unknown (114)",
            "Detect alignment", "Detect invisible", "Clairvoyance", "Unknown (118)", "Mirror image",
            // 120..129
            "Immunity to weapons", "Unknown (121)", "Create inventory item",
            "Remove inventory item", "Teleport", "Unlock", "Movement rate bonus", "Summon monsters",
            "Confusion", "Aid (non-cumulative)",
            // 130..139
            "Bless (non-cumulative)", "Chant (non-cumulative)", "Draw upon holy might (non-cumulative)",
            "Luck (non-cumulative)", "Petrification", "Polymorph", "Force visible",
            "Bad chant (non-cumulative)", "Set animation sequence", "Display string",
            // 140..149
            "Casting glow", "Lighting effects", "Display portrait icon", "Create item in slot",
            "Disable button", "Disable spellcasting", "Cast spell", "Learn spell",
            "Cast spell at point", "Identify",
            // 150..159
            "Find traps", "Replace self", "Play movie", "Sanctuary", "Entangle overlay",
            "Minor globe overlay", "Protection from normal missiles overlay", "Web effect",
            "Grease overlay", "Mirror image effect",
            // 160..169
            "Remove sanctuary", "Remove fear", "Remove paralysis", "Free action",
            "Remove intoxication", "Pause target", "Magic resistance bonus", "Missile THAC0 bonus",
            "Remove creature", "Prevent portrait icon",
            // 170..179
            "Play damage animation", "Give innate ability", "Remove spell", "Poison resistance bonus",
            "Play sound", "Hold creature", "Movement rate bonus 2", "Use EFF file",
            "THAC0 vs. type bonus", "Damage vs. type bonus",
            // 180..189
            "Disallow item", "Disallow item type", "Use EFF file (do not use)",
            "Use EFF file while on type", "No collision detection", "Hold creature 2",
            "Move creature", "Set local variable", "Increase spells cast per round",
            "Increase casting speed factor",
            // 190..199
            "Increase attack speed factor", "Casting level bonus", "Find familiar",
            "Invisibility detection", "Unknown (194)", "Unknown (195)", "Unknown (196)",
            "Unknown (197)", "Unknown (198)", "Unknown (199)",
            // 200..209
            "Unknown (200)", "Unknown (201)", "Unknown (202)", "Unknown (203)",
            "Unknown (204)", "Unknown (205)", "Protection from spell",
            "Unknown (207)", "Minimum HP", "Unknown (209)",
            // 210..219
            "Power word, stun", "Unknown (211)", "Unknown (212)", "Unknown (213)", "Unknown (214)",
            "Unknown (215)", "Unknown (216)", "Unknown (217)", "Stoneskin effect",
            "Unknown (219)",
            // 220..229
            "Unknown (220)", "Unknown (221)", "Unknown (222)", "Unknown (223)", "Unknown (224)",
            "Unknown (225)", "Unknown (226)", "Unknown (227)", "Unknown (228)", "Unknown (229)",
            // 230..239
            "Unknown (230)", "Unknown (231)", "Creature RGB color fade", "Show visual effect",
            "Snilloc's snowball swarm", "Show casting glow", "Chill touch", "Magical stone",
            "All saving throws bonus", "Slow poison",
            // 240..249
            "Summon creature 2", "Vampiric touch", "Show visual overlay", "Animate dead", "Prayer",
            "Bad prayer", "Summon creature 3", "Beltyn's burning blood", "Summon shadow",
            "Recitation",
            // 250..259
            "Bad recitation", "Lich touch", "Sol's searing orb", "Bonus AC vs. weapons",
            "Dispel specific spell", "Salamander aura", "Umber hulk gaze", "Zombie lord aura",
            "Immunity to specific resource", "Summon creatures with cloud",
            // 260..269
            "Hide creature", "Immunity to effect and string", "Pomab images", "Evil turn undead",
            "Static charge", "Cloak of fear", "Movement rate modifier", "Cure confusion",
            "Eye of the mind", "Eye of the sword",
            // 270..279
            "Eye of the mage", "Eye of venom", "Eye of the spirit", "Eye of fortitude",
            "Eye of stone", "Remove seven eyes", "Remove effect by type", "Soul eater",
            "Shroud of flame", "Animal rage",
            // 280..289
            "Turn undead", "Vitriolic sphere", "Hide hit points", "Float text", "Mace of disruption",
            "Force sleep", "Ranger tracking", "Immunity to sneak attack", "Set spell state",
            "Dragon gem cutscene",
            // 290..299
            "Display spell immunity string", "Rod of smiting", "Rest", "Beholder dispel magic",
            "Harpy wail", "Jackalwere gaze", "Set global variable"};
        break;

      case IWD2:
        s_effname = new String[]{
            // 0..9
            "AC bonus", "Modify attacks per round", "Cure sleep", "Berserk", "Cure berserk",
            "Charm creature", "Charisma bonus", "Set color", "Set color glow solid",
            "Set color glow pulse",
            // 10..19
            "Constitution bonus", "Cure poison", "Damage", "Kill target", "Defrost",
            "Dexterity bonus", "Haste", "Current HP bonus", "Maximum HP bonus", "Intelligence bonus",
            // 20..29
            "Invisibility", "Knowledge arcana", "Luck bonus", "Morale bonus", "Panic", "Poison",
            "Remove curse", "Acid resistance bonus", "Cold resistance bonus",
            "Electricity resistance bonus",
            // 30..39
            "Fire resistance bonus", "Magic damage resistance bonus", "Raise dead",
            "Fortitude save bonus", "Reflex save bonus", "Will save bonus", "Unknown (36)",
            "Unknown (37)", "Silence", "Sleep",
            // 40..49
            "Slow", "Sparkle", "Bonus wizard spells", "Stone to flesh", "Strength bonus", "Stun",
            "Cure stun", "Remove invisibility", "Vocalize", "Wisdom bonus",
            // 50..59
            "Character color pulse", "Character tint solid", "Character tint bright",
            "Animation change", "Base attack bonus", "Slay", "Invert alignment", "Change alignment",
            "Dispel effects", "Move silently bonus",
            // 60..69
            "Casting failure", "Alchemy", "Bonus priest spells", "Infravision", "Remove infravision",
            "Blur", "Translucency", "Summon creature", "Unsummon creature", "Nondetection",
            // 70..79
            "Remove nondetection", "Change gender", "Change AI type", "Attack damage bonus",
            "Blindness", "Cure blindness", "Feeblemindedness", "Cure feeblemindedness", "Disease",
            "Cure disease",
            // 80..89
            "Deafness", "Cure deafness", "Set AI script", "Immunity to projectile",
            "Unknown (84)", "Unknown (85)",
            "Slashing resistance bonus", "Crushing resistance bonus", "Piercing resistance bonus",
            "Missile resistance bonus",
            // 90..99
            "Open locks bonus", "Find traps bonus", "Pick pockets bonus", "Fatigue bonus",
            "Intoxication bonus", "Tracking bonus", "Change level", "Exceptional strength bonus",
            "Regeneration", "Modify duration",
            // 100..109
            "Protection from creature type", "Immunity to effect", "Immunity to spell level",
            "Change name", "XP bonus", "Remove gold", "Morale break", "Change portrait",
            "Reputation bonus", "Paralyze",
            // 110..119
            "Unknown (110)", "Create weapon", "Remove item", "Equip weapon", "Dither",
            "Detect alignment", "Detect invisible", "Clairvoyance", "Show creatures", "Mirror image",
            // 120..129
            "Immunity to weapons", "Visual animation effect", "Create inventory item",
            "Remove inventory item", "Teleport", "Unlock", "Movement rate bonus", "Unknown (127)",
            "Confusion", "Aid (non-cumulative)",
            // 130..139
            "Bless (non-cumulative)", "Chant (non-cumulative)", "Draw upon holy might (non-cumulative)",
            "Luck (non-cumulative)", "Petrification", "Polymorph", "Force visible",
            "Unknown (137)", "Set animation sequence", "Display string",
            // 140..149
            "Casting glow", "Lighting effects", "Display portrait icon", "Create item in slot",
            "Disable button", "Disable spellcasting", "Cast spell", "Learn spell",
            "Cast spell at point", "Identify",
            // 150..159
            "Find traps", "Replace self", "Play movie", "Sanctuary", "Entangle overlay",
            "Unknown (155)", "Unknown (156)", "Web effect",
            "Grease overlay", "Mirror image effect",
            // 160..169
            "Remove sanctuary", "Remove fear", "Remove paralysis", "Free action",
            "Remove intoxication", "Pause target", "Magic resistance bonus", "Missile attack bonus",
            "Remove creature", "Prevent portrait icon",
            // 170..179
            "Play damage animation", "Give innate ability", "Remove spell", "Poison resistance bonus",
            "Play sound", "Hold creature", "Movement rate penalty", "Use EFF file",
            "THAC0 vs. type bonus", "Damage vs. type bonus",
            // 180..189
            "Disallow item", "Disallow item type", "Use EFF file (do not use)",
            "Use EFF file while on type", "No collision detection", "Hold creature 2",
            "Move creature", "Set local variable", "Increase spells cast per round",
            "Increase casting speed factor",
            // 190..199
            "Increase attack speed factor", "Casting level bonus", "Find familiar",
            "Invisibility detection", "Unknown (194)", "Unknown (195)", "Unknown (196)",
            "Unknown (197)", "Unknown (198)", "Unknown (199)",
            // 200..209
            "Unknown (200)", "Unknown (201)", "Unknown (202)", "Unknown (203)",
            "Unknown (204)", "Unknown (205)", "Protection from spell",
            "Unknown (207)", "Minimum HP", "Unknown (209)",
            // 210..219
            "Unknown (210)", "Unknown (211)", "Unknown (212)", "Unknown (213)", "Unknown (214)",
            "Unknown (215)", "Unknown (216)", "Unknown (217)", "Stoneskin effect",
            "Unknown (219)",
            // 220..229
            "Unknown (220)", "Unknown (221)", "Unknown (222)",
            "Unknown (223)", "Unknown (224)", "Unknown (225)", "Unknown (226)",
            "Unknown (227)", "Unknown (228)", "Unknown (229)",
            // 230..239
            "Unknown (230)", "Unknown (231)", "Creature RGB color fade", "Show visual effect",
            "Unknown (234)", "Show casting glow", "Panic undead", "Unknown (237)",
            "All saving throws bonus", "Slow poison",
            // 240..249
            "Unknown (240)", "Vampiric touch", "Unknown (242)", "Unknown (243)", "Prayer",
            "Unknown (245)", "Unknown (246)", "Beltyn's burning blood", "Summon shadow",
            "Recitation",
            // 250..259
            "Unknown (250)", "Unknown (251)", "Unknown (252)", "Unknown (253)",
            "Dispel specific spell", "Salamander aura", "Umber hulk gaze", "Unknown (257)",
            "Immunity to specific resource", "Unknown (259)",
            // 260..269
            "Hide creature", "Immunity to effect and resource", "Unknown (262)", "Evil turn undead",
            "Static charge", "Cloak of fear", "Movement rate modifier", "Cure confusion",
            "Eye of the mind", "Eye of the sword",
            // 270..279
            "Eye of the mage", "Eye of venom", "Eye of the spirit", "Eye of fortitude",
            "Eye of stone", "Remove seven eyes", "Remove effect by type", "Unknown (277)",
            "Shroud of flame", "Animal rage",
            // 280..289
            "Turn undead", "Vitriolic sphere", "Hide hit points", "Float text", "Mace of disruption",
            "Force sleep", "Ranger tracking", "Immunity to sneak attack", "Set spell state",
            "Unknown (289)",
            // 290..299
            "Display spell immunity string", "Rod of smiting", "Rest", "Beholder dispel magic",
            "Harpy wail", "Jackalwere gaze", "Set global variable", "Hide in shadows bonus",
            "Use magic device bonus", "Unknown (299)",
            // 300..309
            "Unknown (300)", "Unknown (301)", "Unknown (302)", "Unknown (303)", "Unknown (304)",
            "Unknown (305)", "Unknown (306)", "Unknown (307)", "Unknown (308)", "Unknown (309)",
            // 310..319
            "Unknown (310)", "Unknown (311)", "Unknown (312)", "Unknown (313)", "Unknown (314)",
            "Unknown (315)", "Unknown (316)", "Unknown (317)", "Unknown (318)", "Unknown (319)",
            // 320..329
            "Unknown (320)", "Unknown (321)", "Unknown (322)", "Unknown (323)", "Unknown (324)",
            "Unknown (325)", "Unknown (326)", "Unknown (327)", "Unknown (328)", "Unknown (329)",
            // 330..339
            "Unknown (330)", "Unknown (331)", "Unknown (332)", "Unknown (333)", "Unknown (334)",
            "Unknown (335)", "Unknown (336)", "Unknown (337)", "Unknown (338)", "Unknown (339)",
            // 340..349
            "Unknown (340)", "Unknown (341)", "Unknown (342)", "Unknown (343)", "Unknown (344)",
            "Unknown (345)", "Unknown (346)", "Unknown (347)", "Unknown (348)", "Unknown (349)",
            // 350..359
            "Unknown (350)", "Unknown (351)", "Unknown (352)", "Unknown (353)", "Unknown (354)",
            "Unknown (355)", "Unknown (356)", "Unknown (357)", "Unknown (358)", "Unknown (359)",
            // 360..369
            "Unknown (360)", "Unknown (361)", "Unknown (362)", "Unknown (363)", "Unknown (364)",
            "Unknown (365)", "Unknown (366)", "Unknown (367)", "Unknown (368)", "Unknown (369)",
            // 370..379
            "Unknown (370)", "Unknown (371)", "Unknown (372)", "Unknown (373)", "Unknown (374)",
            "Unknown (375)", "Unknown (376)", "Unknown (377)", "Unknown (378)", "Unknown (379)",
            // 380..389
            "Unknown (380)", "Unknown (381)", "Unknown (382)", "Unknown (383)", "Unknown (384)",
            "Unknown (385)", "Unknown (386)", "Unknown (387)", "Unknown (388)", "Unknown (389)",
            // 390..399
            "Unknown (390)", "Unknown (391)", "Unknown (392)", "Unknown (393)", "Unknown (394)",
            "Unknown (395)", "Unknown (396)", "Unknown (397)", "Unknown (398)", "Unknown (399)",
            // 400..409
            "Hopelessness", "Protection from evil", "Apply effects list", "Armor of faith",
            "Nausea", "Enfeeblement", "Fire shield", "Death ward", "Holy power",
            "Righteous wrath of the faithful",
            // 410..419
            "Summon friendly creature", "Summon hostile creature", "Control creature",
            "Run visual effect", "Otiluke's resilient sphere", "Barkskin", "Bleeding wounds",
            "Area effect using effects list", "Free action", "Unconsciousness",
            // 420..429
            "Death magic", "Entropy shield", "Storm shell", "Protection from the elements",
            "Hold undead", "Control undead", "Aegis", "Executioner's eyes", "Banish",
            "Apply effects list on hit",
            // 430..439
            "Projectile type using effects list", "Energy drain", "Tortoise shell", "Blink",
            "Persistent using effects list", "Day blindness", "Damage reduction", "Disguise",
            "Heroic inspiration", "Prevent AI slowdown",
            // 440..449
            "Barbarian rage", "Force slow", "Cleave", "Protection from arrows",
            "Tenser's transformation", "Slippery mind", "Smite evil", "Restoration", "Alicorn lance",
            "Call lightning",
            // 450..459
            "Globe of invulnerability", "Lower resistance", "Bane", "Power attack", "Expertise",
            "Arterial strike", "Hamstring", "Rapid shot"};
        break;

      default:
        s_effname = new String[0];
    }
  }

  public String[] getEffectNameArray()
  {
    return s_effname;
  }

  // Returns portrait icon description array (used by opcodes 142, 149 and 240)
  public String[] getIconDescArray()
  {
    return getIconDescArray(false);
  }

  // Returns portrait icon description array.
  // Specify "setDefault" to indicate whether entry 0 should be overridden by "Default" string.
  public String[] getIconDescArray(boolean setDefault)
  {
    if (s_poricon == null) {
      Table2da table = Table2daCache.get("STATDESC.2DA");
      if (table != null) {
        // first pass: determine highest icon index
        int maxIndex = -1;
        for (int i = 0, count = table.getRowCount(); i < count; i++) {
          try {
            int idx = Integer.parseInt(table.get(i, 0));
            maxIndex = Math.max(idx, maxIndex);
          } catch (NumberFormatException e) {
          }
        }

        // second pass: collect icon descriptions
        if (maxIndex >= 0) {
          s_poricon = new String[maxIndex + 1];
          for (int i = 0, count = table.getRowCount(); i < count; i++) {
            try {
              int idx = Integer.parseInt(table.get(i, 0));
              int strref = Integer.parseInt(table.get(i, 1));
              if (idx >= 0 && strref >= 0) {
                s_poricon[idx] = StringTable.getStringRef(strref);
              }
            } catch (NumberFormatException e) {
            }
          }
        }
      }
      if (s_poricon == null) {
        s_poricon = new String[0];
      }
    }

    if (setDefault && s_poricon.length > 0) {
      s_poricon[0] = "Default";
    }

    return s_poricon;
  }

  public int makeEffectStruct(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                              int effectType, boolean isV1) throws Exception
  {
    if (buffer != null && offset >= 0 && s != null && effectType >= 0) {
      buffer.position(offset);
      int param1 = buffer.getInt();
      int param2 = buffer.getInt();

      // setting param1 & param2
      String restype = makeEffectParams(parent, buffer, offset, s, effectType, isV1);
      offset += 8;

      // setting common fields #1 ("Timing mode" ... "Probability2")
      offset = makeEffectCommon1(buffer, offset, s, isV1);

      // setting Resource field
      offset = makeEffectResource(parent, buffer, offset, s, effectType, restype, param1, param2);

      // setting common fields #2 ("Dice" ... "Save bonus")
      offset = makeEffectCommon2(buffer, offset, s, isV1);

      // setting Parameter 2.5 field
      offset = makeEffectParam25(parent, buffer, offset, s, effectType, restype, param1, param2);

      return offset;
    } else
      throw new Exception("Invalid parameters specified");
  }


  private String makeEffectParams(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                  int effectType, boolean isV1)
  {
    final int initSize = s.size();

    // Processing effects common to all supported game engines
    String restype = makeEffectParamsGeneric(parent, buffer, offset, s, effectType, isV1);

    // Processing game specific effects
    if (s.size() == initSize && restype == null) {
      if (Profile.getEngine() == Profile.Engine.BG1) {
        restype = makeEffectParamsBG1(parent, buffer, offset, s, effectType, isV1);
      } else if (Profile.getEngine() == Profile.Engine.PST) {
        restype = makeEffectParamsPST(parent, buffer, offset, s, effectType, isV1);
      } else if (Profile.getEngine() == Profile.Engine.IWD) {
        restype = makeEffectParamsIWD(parent, buffer, offset, s, effectType, isV1);
      } else if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
        restype = makeEffectParamsBG2(parent, buffer, offset, s, effectType, isV1);
      } else if (Profile.getEngine() == Profile.Engine.IWD2) {
        restype = makeEffectParamsIWD2(parent, buffer, offset, s, effectType, isV1);
      }
    }

    // failsafe initialization
    if (s.size() == initSize) {
      s.add(new Unknown(buffer, offset, 4));
      s.add(new Unknown(buffer, offset + 4, 4));
    }

    return restype;
  }

  private String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset,
                                         List<StructEntry> s, int effectType, boolean isV1)
  {
    String restype = null;
    boolean isTobEx = Profile.getProperty(Profile.Key.IS_GAME_TOBEX);

    switch (effectType) {
      case 0: // AC bonus
        s.add(new DecNumber(buffer, offset, 4, "AC value"));
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Bonus to",
                           new String[]{"Generic", "Armor", "Deflection", "Shield", "Crushing",
                                        "Piercing", "Slashing", "Missile"}));
        } else {
          s.add(new Flag(buffer, offset + 4, 4, "Bonus to", s_actype));
        }
        break;

      case 1: // Modify attacks per round
        if (Profile.getEngine() == Profile.Engine.IWD) {
          s.add(new Bitmap(buffer, offset, 4, "Value",
                           new String[]{"0 attacks per round", "1 attack per round",
                                        "2 attacks per round", "3 attacks per round",
                                        "4 attacks per round", "5 attacks per round"}));
        } else if (Profile.isEnhancedEdition()) {
          int type = buffer.getInt(offset + 4);
          if (type == 2) {
            s.add(new DecNumber(buffer, offset, 4, "Value"));
          } else {
            s.add(new Bitmap(buffer, offset, 4, "Value", s_attacks));
          }
        } else {
          s.add(new Bitmap(buffer, offset, 4, "Value", s_attacks));
        }

        if (Profile.isEnhancedEdition()) {
          Bitmap item = new Bitmap(buffer, offset + 4, 4, "Modifier type",
                                   new String[]{"Increment", "Set", "Set % of", "Set final"});
          if (parent != null && parent instanceof UpdateListener) {
            item.addUpdateListener((UpdateListener)parent);
          }
          s.add(item);
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 2: // Sleep
      case 4: // Cure berserk
      case 11: // Cure poison
      case 14: // Defrost
      case 38: // Silence
      case 40: // Slow
      case 43: // Stone to flesh
      case 46: // Cure stun
      case 47: // Remove invisibility
      case 48: // Vocalize
      case 56: // Invert alignment
      case 63: // Infravision
      case 64: // Remove infravision
      case 65: // Blur
      case 69: // Nondetection
      case 70: // Remove nondetection
      case 74: // Blindness
      case 75: // Cure blindness
      case 76: // Feeblemindedness
      case 77: // Cure feeblemindedness
      case 79: // Cure disease
      case 80: // Deafness
      case 81: // Cure deafness
      case 116: // Detect invisible
      case 117: // Clairvoyance
      case 125: // Unlock
      case 128: // Confusion
      case 134: // Petrification
      case 136: // Force visible
      case 149: // Identifiy
      case 150: // Find traps
      case 160: // Remove sanctuary
      case 161: // Remove fear
      case 162: // Remove paralysis
      case 163: // Free action
      case 164: // Remove intoxication
      case 165: // Pause target
      case 168: // Remove creature
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 3: // Berserk
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2 ||
            Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Berserk type",
                           new String[]{"Normal", "Constant", "Blood rage"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 5: // Charm creature
      {
        s.add(new IdsBitmap(buffer, offset, 4, "Creature type", "GENERAL.IDS"));
        if (Profile.getEngine() == Profile.Engine.PST || Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          final LongIntegerHashMap<String> idsmap = new LongIntegerHashMap<String>();
          idsmap.put(0L, "Charmed (neutral)");
          idsmap.put(1L, "Charmed (hostile)");
          idsmap.put(2L, "Dire charmed (neutral)");
          idsmap.put(3L, "Dire charmed (hostile)");
          idsmap.put(4L, "Controlled");
          idsmap.put(5L, "Hostile");
          if (Profile.getEngine() == Profile.Engine.BG1 || Profile.getEngine() == Profile.Engine.BG2 ||
              Profile.isEnhancedEdition()) {
            idsmap.put(1000L, "Charmed (neutral, no text)");
            idsmap.put(1001L, "Charmed (hostile, no text)");
            idsmap.put(1002L, "Dire charmed (neutral, no text)");
            idsmap.put(1003L, "Dire charmed (hostile, no text)");
            idsmap.put(1004L, "Controlled (no text)");
            idsmap.put(1005L, "Hostile (no text)");
          }
          s.add(new HashBitmap(buffer, offset + 4, 4, "Charm type", idsmap));
        }
        break;
      }

      case 6: // Charisma bonus
      case 10: // Constitution bonus
      case 19: // Intelligence bonus
      case 27: // Acid resistance bonus
      case 28: // Cold resistance bonus
      case 29: // Electricity resistance bonus
      case 30: // Fire resistance bonus
      case 31: // Magic damage resistance bonus
      case 49: // Wisdom bonus
      case 54: // Base THAC0 bonus / Base attack bonus
      case 59: // Stealth bonus / Move silently bonus
      case 86: // Slashing resistance bonus
      case 87: // Crushing resistance bonus
      case 88: // Piercing resistance bonus
      case 89: // Missile resistance bonus
      case 90: // Open locks bonus
      case 91: // Find traps bonus
      case 92: // Pick pockets bonus
      case 93: // Fatigue bonus
      case 94: // Intoxication bonus
      case 95: // Tracking bonus
      case 96: // Change level
      case 97: // Exceptional strength bonus
      case 104: // XP bonus
      case 105: // Remove gold
      case 106: // Morale break
      case 126: // Movement rate bonus
      case 167: // Missile THAC0 bonus / Missile attack bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        break;

      case 7: // Set color
        s.add(new ColorValue(buffer, offset, 4, "Color"));
        s.add(new HashBitmap(buffer, offset + 4, 4, "Location", m_colorloc, false));
        break;

      case 8: // Set color glow solid
      case 51: // Character tint solid
      case 52: // Character tint bright
        s.add(new ColorPicker(buffer, offset, "Color"));
        s.add(new HashBitmap(buffer, offset + 4, 4, "Location", m_colorloc, false));
        break;

      case 9: // Set color glow pulse
        s.add(new ColorPicker(buffer, offset, "Color"));
        s.add(new HashBitmap(buffer, offset + 4, 2, "Location", m_colorloc, false));
        s.add(new DecNumber(buffer, offset + 6, 2, "Cycle speed"));
        break;

      case 12: // Damage
      {
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        String[] s_mode;
        if (Profile.getEngine() == Profile.Engine.BG1 || Profile.getEngine() == Profile.Engine.PST) {
          s_mode = new String[]{"Normal", "Set to value", "Set to %"};
        } else if (Profile.getEngine() == Profile.Engine.IWD2) {
          s_mode = new String[]{"Normal", "Set to value", "Set to %", "Save for half"};
        } else {
          s_mode = new String[]{"Normal", "Set to value", "Set to %", "Percentage"};
        }
        s.add(new Bitmap(buffer, offset + 4, 2, "Mode", s_mode));
        s.add(new IdsBitmap(buffer, offset + 6, 2, "Damage type", "DAMAGES.IDS"));
        break;
      }

      case 13: // Kill target
      {
        if (Profile.getEngine() == Profile.Engine.PST) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          s.add(new Bitmap(buffer, offset, 4, "Display text?", s_yesno));
        }
        String[] s_type;
        if (Profile.getEngine() == Profile.Engine.BG1) {
          s_type = new String[]{"Acid", "Burning", "Crushed", "Normal", "Exploding", "Stoned",
                                "Freezing", "Exploding stoned", "Exploding freezing", "Electrified"};
        } else if (Profile.getEngine() == Profile.Engine.PST) {
          s_type = new String[]{"Normal", "", "", "", "Exploding", "", "Freezing", "Exploding stoned"};
        } else if (Profile.getEngine() == Profile.Engine.IWD) {
          s_type = new String[]{"Acid", "Burning", "Crushed", "Normal", "Exploding", "Stoned",
                                "Freezing", "", "", "", "Disintegration", "Destruction"};
        } else if (Profile.getEngine() == Profile.Engine.IWD2) {
          s_type = new String[]{"Acid", "Burning", "Crushed", "Normal", "Exploding", "Stoned",
                                "Freezing", "Exploding stoned", "Exploding freezing",
                                "Electrified", "Disintegration", "Destruction"};
        } else {
          s_type = new String[]{"Acid", "Burning", "Crushed", "Normal", "Exploding", "Stoned",
                                "Freezing", "Exploding stoned", "Exploding freezing", "Electrified",
                                "Disintegration", ""};
          if (Profile.isEnhancedEdition()) {
            s_type[11] = "Exploding (no drop);Exploding death, inventory is not dropped";
          }
        }
        s.add(new Flag(buffer, offset + 4, 4, "Death type", s_type));
        break;
      }

      case 15: // Dexterity bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2 ||
            Profile.isEnhancedEdition() || isTobEx) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                           new String[]{"Increment", "Set", "Set % of", "Cat's grace"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 16: // Haste
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Haste type",
                           new String[]{"Normal", "Improved", "Movement rate only"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 17: // Current HP bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.getEngine() == Profile.Engine.BG1 || Profile.getEngine() == Profile.Engine.PST) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        } else if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                           new String[]{"Increment", "Set", "Increment % of", "Lay on hands",
                                        "Wholeness of body", "Lathander's renewal"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 2, "Modifier type", s_inctype));
          String[] s_flags = null;
          if (Profile.getEngine() == Profile.Engine.IWD) {
            s_flags = new String[]{"No flags set", "Raise dead"};
          } else {
            s_flags = new String[]{"Heal normally", "Raise dead", "Remove limited effects"};
          }
          s.add(new Flag(buffer, offset + 6, 2, "Heal flags", s_flags));
        }
        break;

      case 18: // Maximum HP bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                         new String[]{"Increment", "Set", "Set % of",
                                      "Increment, don't update current HP",
                                      "Set, don't update current HP",
                                      "Set % of, don't update current HP"}));
        break;

      case 20: // Invisibility
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.BG1 || Profile.getEngine() == Profile.Engine.PST) {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Invisibility type",
                           new String[]{"Normal", "Improved", "Weak"}));
        }
        break;

      case 21: // Lore bonus / Knowledge arcana
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                           new String[]{"Increment", "Set", "Mastery"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 22: // Luck bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.getEngine() == Profile.Engine.BG1) {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                           new String[]{"Increment", "Lucky streak", "Fortune's favorite"}));
        } else if (isTobEx) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{
              s_inctype[0], s_inctype[1], s_inctype[2], "Instantaneous"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 23: // Reset morale
      {
        int bg1mode = (Profile.getEngine() == Profile.Engine.BG2 || Profile.getEngine() == Profile.Engine.PST) ? 0 : 1;
        if (Profile.isEnhancedEdition()) {
          int ofsSpecial = offset + (isV1 ? 0x28 : 0x2c);
          bg1mode = buffer.getInt(ofsSpecial);
        }
        if (bg1mode == 0) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;
      }

      case 24: // Panic
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Panic type", new String[]{"Normal", "Harpy wail"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 25: // Poison
      {
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        String[] s_type;
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          s_type = new String[]{"1 damage per second", "Amount damage per second",
                                "Amount damage per second", "1 damage per amount seconds",
                                "Amount damage per round", "(Crash)", "Snakebite", "Unused",
                                "Envenomed weapon"};
          if (Profile.getEngine() == Profile.Engine.IWD2) s_type[5] = "Unused";
        } else {
          s_type = new String[]{"1 damage per second", "1 damage per second",
                                "Amount damage per second", "1 damage per amount seconds",
                                "Param3 damage per amount seconds"};
          if (Profile.getEngine() == Profile.Engine.BG1) {
            s_type[3] = "1 damage per amount+1 seconds";
          }
        }
        Bitmap bmp = new Bitmap(buffer, offset + 4, 4, "Poison type", s_type);
        s.add(bmp);
        if (parent != null && parent instanceof UpdateListener) {
          bmp.addUpdateListener((UpdateListener)parent);
        }

        break;
      }

      case 26: // Remove Curse
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.PST) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Curse type",
                           new String[]{"Normal", "Jumble curse"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 32: // Raise dead
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Restore creature animation?", s_noyes));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 33: // Save vs. death bonus / Fortitude save bonus
      case 34: // Save vs. wand bonus / Reflex save bonus
      case 35: // Save vs. polymorph bonus / Will save bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (isTobEx || Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{
              s_inctype[0], s_inctype[1], s_inctype[2], "Increment instantaneously"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 36: // Save vs. breath bonus
      case 37: // Save vs. spell bonus
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          if (isTobEx || Profile.isEnhancedEdition()) {
            s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{
                s_inctype[0], s_inctype[1], s_inctype[2], "Increment instantaneously"}));
          } else {
            s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
          }
        }
        break;

      case 39: // Sleep
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2 ||
            Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Wake on damage?", s_yesno));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 41: // Sparkle
        s.add(new Bitmap(buffer, offset, 4, "Color", s_sparklecolor));
        s.add(new Bitmap(buffer, offset + 4, 4, "Particle effect",
                         new String[]{"", "Explosion", "Swirl", "Shower"}));
        break;

      case 42: // Bonus wizard spells
        s.add(new DecNumber(buffer, offset, 4, "# spells to add"));
        if (isTobEx) {
          s.add(new Flag(buffer, offset + 4, 4, "Spell levels", new String[]{
              "Double spells", "Level 1", "Level 2", "Level 3", "Level 4",
              "Level 5", "Level 6", "Level 7", "Level 8", "Level 9", "Ex: Double spells"}));
        } else {
          s.add(new Flag(buffer, offset + 4, 4, "Spell levels", new String[]{
              "Double spells", "Level 1", "Level 2", "Level 3", "Level 4",
              "Level 5", "Level 6", "Level 7", "Level 8", "Level 9"}));
        }
        break;

      case 44: // Strength bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2 ||
            Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                           new String[]{"Increment", "Set", "Set % of", "Wizard strength"}));
        } else if (isTobEx) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{
              s_inctype[0], s_inctype[1], s_inctype[2], "Strength spell"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 45: // Stun
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Stun type",
                           new String[]{"Normal", "Unstun on damage", "Power word, stun"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 50: // Character color pulse
        s.add(new ColorPicker(buffer, offset, "Color"));
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          s.add(new HashBitmap(buffer, offset + 4, 1, "Location", m_colorloc, false));
          s.add(new DecNumber(buffer, offset + 5, 1, AbstractStruct.COMMON_UNUSED));
          s.add(new UnsignDecNumber(buffer, offset + 6, 1, "Cycle speed"));
          s.add(new DecNumber(buffer, offset + 7, 1, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 53: // Animation change
        s.add(new IdsBitmap(buffer, offset, 4, "Morph into", "ANIMATE.IDS"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Morph type",
                         new String[]{"Temporary change", "Remove temporary change",
                                      "Permanent change"}));
        break;

      case 55: // Slay
      case 100: // Protection from creature type
      case 109: // Paralyze
      case 175: // Hold creature
      {
        final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
        s.add(param2.createIdsValueFromType(buffer));
        s.add(param2);
        break;
      }

      case 57: // Change alignment
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new IdsBitmap(buffer, offset + 4, 4, "Alignment",
                            Profile.getProperty(Profile.Key.GET_IDS_ALIGNMENT)));
        break;

      case 58: // Dispel effects
        s.add(new DecNumber(buffer, offset, 4, "Level"));
        if (isTobEx || Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 2, "Dispel type", new String[]{
              "Always dispel", "Use caster level", "Use specific level"}));
          s.add(new Bitmap(buffer, offset + 6, 2, "Magic weapon dispel type", new String[]{
              "Always dispel", "Do not dispel", "Chance of dispel"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Dispel type", new String[]{
              "Always dispel", "Use caster level", "Use specific level"}));
        }
        break;

      case 60: // Casting failure
      {
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        String label;
        String[] s_type;
        if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
          label = "Failure type";
          s_type = new String[]{"Wizard", "Priest", "Innate", "Wizard (dead magic)",
                                "Priest (dead magic)", "Innate (dead magic)"};
        } else if (Profile.getEngine() == Profile.Engine.IWD2) {
          label = "Spell class";
          s_type = new String[]{"Arcane", "Divine", "All spells"};
        } else {
          label = "Failure type";
          s_type = new String[]{"Wizard", "Priest", "Innate"};
        }
        s.add(new Bitmap(buffer, offset + 4, 4, label, s_type));
        break;
      }

      case 62: // Bonus priest spells
        s.add(new DecNumber(buffer, offset, 4, "# spells to add"));
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Flag(buffer, offset + 4, 4, "Spell levels",
                         new String[]{"Double spells", "Level 1", "Level 2", "Level 3", "Level 4",
                                      "Level 5", "Level 6", "Level 7", "Level 8", "Level 9"}));
        } else if (isTobEx) {
          s.add(new Flag(buffer, offset + 4, 4, "Spell levels", new String[]{
              "Double spells", "Level 1", "Level 2", "Level 3", "Level 4",
              "Level 5", "Level 6", "Level 7", null, null, "Ex: Double spells"}));
        } else {
          s.add(new Flag(buffer, offset + 4, 4, "Spell levels", new String[]{
              "Double spells", "Level 1", "Level 2", "Level 3", "Level 4",
              "Level 5", "Level 6", "Level 7"}));
        }
        break;

      case 66: // Translucency
        s.add(new DecNumber(buffer, offset, 4, "Fade amount"));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2 ||
            Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Visual effect",
                           new String[]{"Draw instantly", "Fade in", "Fade out"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 67: // Summon creature
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new DecNumber(buffer, offset, 4, "# creatures"));
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        }
        if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition() ||
            Profile.getEngine() == Profile.Engine.IWD) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Allegiance", s_summoncontrol));
        } else if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Summon animation", s_sumanim));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        restype = "CRE";
        break;

      case 68: // Unsummon creature
        s.add(new Bitmap(buffer, offset, 4, "Display text?", s_noyes));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.isEnhancedEdition()) {
          restype = "VEF:VVC:BAM";
        }
        break;

      case 71: // Change gender
        s.add(new IdsBitmap(buffer, offset, 4, "Gender", "GENDER.IDS"));
        s.add(new Bitmap(buffer, offset + 4, 4, "How?",
                         new String[]{"Reverse gender", "Set gender"}));
        break;

      case 72: // Change AI type
      {
        final String[] ids = new String[]{"EA.IDS", "GENERAL.IDS", "RACE.IDS", "CLASS.IDS",
                                          "SPECIFIC.IDS", "GENDER.IDS",
                                          Profile.getProperty(Profile.Key.GET_IDS_ALIGNMENT)};
        final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4, IdsTargetType.DEFAULT_NAME_TYPE, ids);
        s.add(param2.createIdsValueFromType(buffer));
        s.add(param2);
        break;
      }

      case 73: // Attack damage bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Damage type", s_damagetype));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 78: // Disease
        if (Profile.getEngine() == Profile.Engine.BG1 || Profile.getEngine() == Profile.Engine.PST) {
          s.add(new DecNumber(buffer, offset, 4, "Amount per second"));
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Amount"));
        }
        if (Profile.getEngine() == Profile.Engine.BG1 || Profile.getEngine() == Profile.Engine.PST) {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          String[] s_type;
          if (Profile.getEngine() == Profile.Engine.IWD) {
            s_type = new String[]{"1 damage per second", "Amount damage per round",
                                  "Amount damage per second", "1 damage per amount seconds",
                                  "Strength", "Dexterity", "Constitution", "Intelligence",
                                  "Wisdom", "Charisma", "Slow target", "Mold touch"};
          } else if (Profile.getEngine() == Profile.Engine.IWD2) {
            s_type = new String[]{"1 damage per second", "Amount damage per round",
                                  "Amount damage per second", "1 damage per amount seconds",
                                  "Strength", "Dexterity", "Constitution", "Intelligence",
                                  "Wisdom", "Charisma", "Slow target", "Mold touch", "",
                                  "Contagion", "Cloud of pestilence", "Dolorous decay"};
          } else if (Profile.isEnhancedEdition()) {
            s_type = new String[]{"1 damage per second", "Amount damage per round",
                                  "Amount damage per second", "1 damage per amount seconds",
                                  "Strength", "Dexterity", "Constitution", "Intelligence",
                                  "Wisdom", "Charisma", "Slow target", "Mold touch/Single",
                                  "Mold touch/Decrement", "Contagion"};
          } else {
            s_type = new String[]{"1 damage per second", "Amount damage per round",
                                  "Amount damage per second", "1 damage per amount seconds",
                                  "Strength", "Dexterity", "Constitution", "Intelligence",
                                  "Wisdom", "Charisma", "Slow target"};
          }
          Bitmap bmp = new Bitmap(buffer, offset + 4, 4, "Disease type", s_type);
          s.add(bmp);
          if (Profile.isEnhancedEdition() && parent != null && parent instanceof UpdateListener) {
            bmp.addUpdateListener((UpdateListener)parent);
            if (bmp.getValue() == 11 || bmp.getValue() == 12) {
              restype = "SPL";
            }
          }
        }
        break;

      case 82: // Set AI script
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new IdsBitmap(buffer, offset + 4, 4, "Script level", "SCRLEV.IDS"));
        restype = "BCS";
        break;

      case 83: // Immunity to projectile
      {
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
          IdsBitmap ids = new IdsBitmap(buffer, offset + 4, 4, "Projectile", "PROJECTL.IDS");
          ids.addIdsMapEntry(new IdsMapEntry(0L, "None"));
          s.add(ids);
        } else {
          LongIntegerHashMap<String> idsmap;
          if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
            idsmap = m_proj_iwd;
          } else {
            idsmap = new LongIntegerHashMap<String>();
            idsmap.put(0L, "None");
            idsmap.put(4L, "Arrow");
            idsmap.put(9L, "Axe");
            idsmap.put(14L, "Bolt");
            idsmap.put(19L, "Bullet");
            idsmap.put(26L, "Throwing Dagger");
            idsmap.put(34L, "Dart");
            if (Profile.getEngine() == Profile.Engine.BG1) {
              idsmap.put(64L, "Gaze");
            }
          }
          s.add(new HashBitmap(buffer, offset + 4, 4, "Projectile", idsmap));
        }
        break;
      }

      case 84: // Magical fire resistance bonus
      case 85: // Magical cold resistance bonus
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 98: // Regeneration
      {
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        String[] s_type;
        if (Profile.getEngine() == Profile.Engine.PST) {
          s_type = new String[]{"Regen all HP", "Regenerate amount percentage",
                                "Amount HP per second", "1 HP per amount seconds"};
        } else if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          s_type = s_regentype_iwd;
        } else {
          s_type = s_regentype;
        }
        Bitmap bmp = new Bitmap(buffer, offset + 4, 4, "Regeneration type", s_type);
        s.add(bmp);
        if (parent != null && parent instanceof UpdateListener) {
          bmp.addUpdateListener((UpdateListener)parent);
        }
        break;
      }

      case 99: // Modify duration
      {
        s.add(new DecNumber(buffer, offset, 4, "Percentage"));
        final String[] s_type;
        if (Profile.isEnhancedEdition()) {
          s_type = new String[]{"Wizard", "Priest", "Bard song"};
        } else {
          s_type = new String[]{"Wizard", "Priest"};
        }
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell class", s_type));
        break;
      }

      case 101: // Immunity to effect
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_effname));
        break;

      case 102: // Immunity to spell level
        s.add(new DecNumber(buffer, offset, 4, "Spell level"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 103: // Change name
        s.add(new StringRef(buffer, offset, "Name"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 107: // Change portrait
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Which portrait?", new String[]{"Small", "Large"}));
        restype = "BMP";
        break;

      case 108: // Reputation bonus
      {
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.isEnhancedEdition()) {
          final String[] incType = {s_inctype[0], s_inctype[1], s_inctype[2],
                                    "Increment (party)", "Reset (party)"};
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", incType));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;
      }

      case 111: // Create weapon
        s.add(new DecNumber(buffer, offset, 4, "# to create"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "ITM";
        break;

      case 112: // Remove item
      case 123: // Remove inventory item
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "ITM";
        break;

      case 113: // Equip weapon
      case 114: // Dither
      case 118: // Show creatures
      case 121: // Visual animation effect
        if (Profile.getEngine() == Profile.Engine.IWD) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 115: // Detect alignment
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Alignment mask",
                         new String[]{"Evil", "Neutral", "Good"}));
        break;

      case 119: // Mirror image
        s.add(new DecNumber(buffer, offset, 4, "# images"));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Image type", new String[]{"Normal", "Reflected image"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 120: // Immunity to weapons
        s.add(new DecNumber(buffer, offset, 4, "Maximum enchantment"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Weapon type",
                         new String[]{"Enchanted", "Magical", "Non-magical", "Silver",
                                      "Non-silver", "Non-silver, non-magical", "Two-handed",
                                      "One-handed", "Cursed", "Non-cursed", "Cold iron",
                                      "Non-cold-iron"}));
        break;

      case 122: // Create inventory item
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new DecNumber(buffer, offset, 4, "Location"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Type", new String[]{"Group", "Slot"}));
        } else {
          s.add(new DecNumber(buffer, offset, 4, "# to create"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        restype = "ITM";
        break;

      case 124: // Teleport
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2 ||
            Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Behavior",
                           new String[]{"Normal", "Source to target", "Return to start",
                                        "Exchange with target"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 127: // Summon monsters
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Total XP"));
          s.add(new Bitmap(buffer, offset + 4, 4, "From 2DA file",
                           new String[]{"Monsum01 (ally)", "Monsum02 (ally)", "Monsum03 (ally)",
                                        "Anisum01 (ally)", "Anisum02 (ally)", "Monsum01 (enemy)",
                                        "Monsum02 (enemy)", "Monsum03 (enemy)",
                                        "Anisum01 (enemy)", "Anisum02 (enemy)"}));
          restype = "2DA";
        }
        break;

      case 129: // Aid (non-cumulative)
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, "HP bonus"));
        break;

      case 130: // Bless (non-cumulative)
      case 132: // Draw upon holy might (non-cumulative)
      case 133: // Luck (non-cumulative)
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 137: // Bad chant (non-cumulative)
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Amount"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 131: // Chant (non-cumulative)
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Prayer type",
                           new String[]{"Beneficial", "Detrimental"}));
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Amount"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 135: // Polymorph
        s.add(new IdsBitmap(buffer, offset, 4, "Animation", "ANIMATE.IDS"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Polymorph type",
                         new String[]{"Change into", "Appearance only", "Appearance only",
                                      "Appearance only"}));
        restype = "CRE";
        break;

      case 138: // Set animation sequence
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.BG1 || Profile.getEngine() == Profile.Engine.IWD) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Sequence",
                           new String[]{"", "Lay down (short)", "Move hands (short)", "Move hands (long)",
                                        "Move shoulder (short)", "Move shoulder (long)", "Lay down (long)",
                                        "Breathe rapidly (short)", "Breath rapidly (long)"}));
        } else {
          String ids;
          if (Profile.getEngine() == Profile.Engine.PST) {
            ids = "ANIMSTAT.IDS";
          } else if (Profile.getEngine() == Profile.Engine.IWD2) {
            ids = "SEQUENCE.IDS";
          } else {
            ids = "SEQ.IDS";
          }
          s.add(new IdsBitmap(buffer, offset + 4, 4, "Sequence", ids));
        }
        break;

      case 139: // Display string
        s.add(new StringRef(buffer, offset, "String"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 140: // Casting glow
      {
        final LongIntegerHashMap<String> m_castglow = new LongIntegerHashMap<String>();
        if (Profile.isEnhancedEdition()) {
          m_castglow.put(0L, "Use projectile");
        }
        m_castglow.put(9L, "Necromancy");
        m_castglow.put(10L, "Alteration");
        m_castglow.put(11L, "Enchantment");
        m_castglow.put(12L, "Abjuration");
        m_castglow.put(13L, "Illusion");
        m_castglow.put(14L, "Conjuration");
        m_castglow.put(15L, "Invocation");
        m_castglow.put(16L, "Divination");
        if (Profile.isEnhancedEdition()) {
          s.add(new ProRef(buffer, offset, "Projectile", false));
          s.add(new DecNumber(buffer, offset + 2, 2, AbstractStruct.COMMON_UNUSED));
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        }
        s.add(new HashBitmap(buffer, offset + 4, 4, "Glow", m_castglow));
        break;
      }

      case 141: // Lighting effects / Visual spell hit
        if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset, 4, "Target", new String[]{"Spell target", "Target point"}));
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        }
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_lighting));
        break;

      case 142: // Display portrait icon
      case 169: // Prevent portrait icon
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Icon", getIconDescArray()));
        break;

      case 143: // Create item in slot
        if (Profile.getEngine() == Profile.Engine.PST) {
          s.add(new Bitmap(buffer, offset, 4, "Slot",
                           new String[]{"Hand", "Eyeball/Earring (left)", "Tattoo", "Bracelet",
                                        "Ring (right)", "Tattoo (top left)", "Ring (left)",
                                        "Earring (right)/Lens", "Armor", "Tattoo (bottomr right)",
                                        "Temporary weapon", "Ammo 1", "Ammo 2", "Ammo 3", "Ammo 4",
                                        "Ammo 5", "Ammo 6", "Quick item 1", "Quick item 2", "Quick item 3",
                                        "Quick item 4", "Quick item 5", "Inventory 1", "Inventory 2",
                                        "Inventory 3", "Inventory 4", "Inventory 5", "Inventory 6",
                                        "Inventory 7", "Inventory 8", "Inventory 9", "Inventory 10",
                                        "Inventory 11", "Inventory 12", "Inventory 13", "Inventory 14",
                                        "Inventory 15", "Inventory 16", "Inventory 17", "Inventory 18",
                                        "Inventory 19", "Inventory 20", "Magic weapon", "Weapon 1",
                                        "Weapon 2", "Weapon 3", "Weapon 4"}));
        } else {
          s.add(new IdsBitmap(buffer, offset, 4, "Slot", "SLOTS.IDS"));
        }
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "ITM";
        break;

      case 144: // Disable button
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (isTobEx || Profile.isEnhancedEdition()) {
          int size = isTobEx ? 15 : 16;
          String[] buttons = new String[size];
          System.arraycopy(s_button, 0, buttons, 0, 14);
          buttons[10] = "Bard song";
          buttons[14] = "Find traps";
          if (Profile.isEnhancedEdition()) {
            buttons[15] = "Inventory screen";
          }
          s.add(new Bitmap(buffer, offset + 4, 4, "Button", buttons));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Button",
                           Profile.getEngine() == Profile.Engine.IWD2 ? s_button_iwd2 : s_button));
        }
        break;

      case 145: // Disable spellcasting
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Spell class",
                           new String[]{"All spells", "Non-innate", "Arcane", "Divine", "Innate"}));
        } else if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Spell class",
                           new String[]{"Wizard", "Priest", "Innate", "All, magical only"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Spell class",
                           new String[]{"Wizard", "Priest", "Innate"}));
        }
        break;

      case 146: // Cast spell
      case 148: // Cast spell at point
        s.add(new DecNumber(buffer, offset, 4, "Cast at level"));
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Mode",
                           new String[]{"Cast normally", "Cast instantly (ignore level)",
                                        "Cast instantly (at level)"}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Cast instantly?", s_noyes));
        }
        restype = "SPL";
        break;

      case 147: // Learn spell
        if (isTobEx) {
          s.add(new DecNumber(buffer, offset, 2, AbstractStruct.COMMON_UNUSED));
          s.add(new Flag(buffer, offset + 2, 2, "Behavior", new String[]{
              "Default", "No XP", null, "Always successful", null, "No XP if already learned",
              "Exclude spell schools", "Exclude sorcerer", "Fail if max. spells learned"
          }));
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        }
        if (Profile.getEngine() == Profile.Engine.PST) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Spell type",
                           new String[]{"Wizard", "Priest", "Innate"}));
        } else if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Spell type",
                           new String[]{"Arcane", "Divine", "Innate"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        restype = "SPL";
        break;

      case 151: // Replace self
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.getEngine() == Profile.Engine.PST) {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Replacement method",
                           new String[]{"Remove silently", "Remove via chunked death",
                                        "Remove via normal death", "Don't remove"}));
        }
        restype = "CRE";
        break;

      case 152: // Play movie
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = Profile.isEnhancedEdition() ? "WBM" : "MVE";
        break;

      case 153: // Sanctuary
      case 154: // Entangle overlay
      case 157: // Web effect
      case 158: // Grease overlay
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Mode", new String[]{"Default overlay", "Custom overlay"}));
          restype = "VVC:BAM";
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 155: // Minor globe overlay
      case 156: // Protection from normal missiles overlay
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          if (Profile.isEnhancedEdition()) {
            s.add(new Bitmap(buffer, offset + 4, 4, "Mode", new String[]{"Default overlay", "Custom overlay"}));
            restype = "VVC:BAM";
          } else {
            s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
          }
        }
        break;


      case 159: // Mirror image effect
        s.add(new DecNumber(buffer, offset, 4, "# images"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 166: // Magic resistance bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (isTobEx) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{
              "Instantaneous", s_inctype[1], s_inctype[2], s_inctype[0]}));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{"Increment", "Set"}));
        }
        break;

      case 170: // Play damage animation
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Animation",
                         new String[]{"Blood (behind)", "Blood (front)", "Blood (left)",
                                      "Blood (right)", "Fire 1", "Fire 2", "Fire 3",
                                      "Electricity 1", "Electricity 2", "Electricity 3"}));
        break;

      case 171: // Give innate ability
      case 172: // Remove spell / Remove innate ability
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "SPL";
        break;

      case 173: // Poison resistance bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        if (Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{"Increment", "Set"}));
        } else if (isTobEx) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{
              s_inctype[1], s_inctype[0], s_inctype[2], "Instantaneous"}));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 174: // Play sound
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "WAV";
        break;

      case 176: // Movement rate bonus 2 / Movement rate penalty
        if (Profile.getEngine() == Profile.Engine.PST) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        }
        break;

      case 177: // Use EFF file
        if (Profile.getEngine() == Profile.Engine.PST) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
          s.add(param2.createIdsValueFromType(buffer));
          s.add(param2);
          restype = "EFF";
        }
        break;

      case 178: // THAC0 vs. type bonus
      case 179: // Damage vs. type bonus
        if (Profile.getEngine() == Profile.Engine.PST) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
          s.add(param2.createIdsValueFromType(buffer));
          s.add(param2);
        }
        break;

      case 180: // Disallow item
        if (Profile.getEngine() == Profile.Engine.PST) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new StringRef(buffer, offset, "String"));
          if (Profile.isEnhancedEdition()) {
            s.add(new Bitmap(buffer, offset + 4, 4, "Restriction", new String[]{"Equip", "Use"}));
          } else {
            s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
          }
          restype = "ITM";
        }
        break;

      case 181: // Disallow itemtype
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset, 4, "Item type", ItmResource.s_categories));
          s.add(new Bitmap(buffer, offset + 4, 4, "Restriction", new String[]{"Equip", "Use"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 182: // Use EFF file (do not use)
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 183: // Use EFF file on equip type
        if (Profile.getEngine() == Profile.Engine.PST) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          if (Profile.getEngine() == Profile.Engine.IWD2) {
            s.add(new StringRef(buffer, offset, "String"));
          } else {
            s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          }
          s.add(new Bitmap(buffer, offset + 4, 4, "Item type", ItmResource.s_categories));
          restype = "EFF";
        }
        break;

      case 184: // No collision detection
        if (Profile.getEngine() == Profile.Engine.PST) {
          makeEffectParamsDefault(buffer, offset, s);
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Pass walls?", s_yesno));
        }
        break;

      case 185: // Hold creature 2
        if (Profile.getEngine() == Profile.Engine.PST) {
          makeEffectParamsDefault(buffer, offset, s);
        } else if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
          s.add(param2.createIdsValueFromType(buffer));
          s.add(param2);
        }
        break;

      case 187: // Set local variable / Play BAM file (single/dual)
        if (Profile.getEngine() == Profile.Engine.PST) {
          s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
          s.add(new Flag(buffer, offset + 4, 4, "Method",
                         new String[]{"Default", "Repeat animation", "Remove stickiness"}));
          restype = "BAM";
        } else {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

    }

    return restype;
  }

  private String makeEffectParamsBG1(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                     int effectType, boolean isV1)
  {
    String restype = null;
    switch (effectType) {
      case 186: // DestroySelf() on target
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 188: // Increase spells cast per round
        s.add(new DecNumber(buffer, offset, 4, "Spells per round"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 189: // Increase casting speed factor
      case 190: // Increase attack speed factor
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 191: // Casting level bonus
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell class", new String[]{"Wizard", "Priest"}));
        break;

      default:
        makeEffectParamsDefault(buffer, offset, s);
        break;
    }

    return restype;
  }

  private String makeEffectParamsBG2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                     int effectType, boolean isV1)
  {
    String restype = null;
    boolean isTobEx = Profile.getProperty(Profile.Key.IS_GAME_TOBEX);

    switch (effectType) {
      case 61: // Creature RGB color fade
        if (Profile.isEnhancedEdition()) {
          s.add(new ColorPicker(buffer, offset, "Color"));
          s.add(new HashBitmap(buffer, offset + 4, 2, "Location", m_colorloc, false));
          s.add(new DecNumber(buffer, offset + 6, 2, "Fade speed"));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 186: // Move creature
        s.add(new DecNumber(buffer, offset, 4, "Delay"));
        if (Profile.isEnhancedEdition() && ResourceFactory.resourceExists("DIR.IDS")) {
          s.add(new IdsBitmap(buffer, offset + 4, 4, "Orientation", "DIR.IDS"));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Orientation", Actor.s_orientation));
        }
        restype = "ARE";
        break;

      case 188: // Increase spells cast per round
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Cleanse aura?", s_noyes));
        break;

      case 189: // Increase casting speed factor
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                         new String[]{"Increment", "Set", "Set if lower"}));
        break;

      case 190: // Increase attack speed factor
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 191: // Casting level bonus
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell class", new String[]{"Wizard", "Priest"}));
        break;

      case 192: // Find familiar
      case 196: // Disable familiar
      case 209: // Power word, kill
      case 210: // Power word, stun
      case 211: // Imprisonment
      case 212: // Freedom
      case 217: // Power word, sleep
      case 224: // Restoration
      case 225: // Detect magic
      case 231: // Time stop
      case 242: // Cure confusion
      case 268: // Clear fog of war
      case 271: // Disable creature
      case 274: // Phase
      case 287: // Remove feet circle
      case 304: // Mass raise dead
      case 311: // Wish
      case 316: // Rest
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 193: // Invisibility detection
      case 194: // Ignore dialogue pause
      case 245: // Check for berserk
      case 246: // Berserk effect
      case 247: // Attack nearest creature
      case 270: // Unpause target
      case 291: // Disable visual effects
      case 292: // Immunity to backstab
      case 293: // Set persistent AI
      case 295: // Disable permanent death
      case 297: // Immunity to turn undead
      case 302: // Can use any item
      case 308: // Immunity to tracking
      case 310: // Immunity to time stop
      case 312: // Immunity to sequester
      case 315: // Remove animation
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, "Stat value"));
        break;

      case 195: // Drain CON and HP on death
      case 208: // Minimum HP
        s.add(new DecNumber(buffer, offset, 4, "HP amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 197: // Physical mirror
      {
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        IdsBitmap ids = new IdsBitmap(buffer, offset + 4, 4, "Projectile", "PROJECTL.IDS");
        ids.addIdsMapEntry(new IdsMapEntry(0L, "None"));
        s.add(ids);
        break;
      }

      case 198: // Reflect specified effect
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_effname));
        break;

      case 199: // Reflect spell level
        s.add(new DecNumber(buffer, offset, 4, "Spell level"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 200: // Spell turning
      case 201: // Spell deflection
        s.add(new DecNumber(buffer, offset, 4, "# levels"));
        s.add(new DecNumber(buffer, offset + 4, 4, "Spell level"));
        if (Profile.isEnhancedEdition()) { restype = "SPL"; }
        break;

      case 202: // Reflect spell school
      case 204: // Protection from spell school
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell school", s_school));
        break;

      case 203: // Reflect spell type
      case 205: // Protection from spell type
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new SecTypeBitmap(buffer, offset + 4, 4, "Spell type"));
        break;

      case 206: // Protection from spell
        s.add(new StringRef(buffer, offset, "String"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "SPL";
        break;

      case 207: // Reflect specified spell
      case 251: // Change bard song
      case 252: // Set trap
      case 256: // Spell sequencer
      case 258: // Activate spell sequencer
      case 260: // Activate spell sequencer at point
      case 266: // Remove protection from spell
      case 313: // High-level ability
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        if (effectType == 258 || effectType == 260) {
          restype = "ITM:SPL";
        } else {
          restype = "SPL";
        }
        break;

      case 213: // Maze
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Mode",
                         new String[]{"Use INTMOD.2DA", "Use duration"}));
        break;

      case 214: // Select spell
      {
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        String[] list;
        if (Profile.isEnhancedEdition()) {
          list = new String[]{"From 2DA", "Known spells",
                              "Known spells with unset spell flags bit 15 (Ignore wild surge)"};
        } else {
          list = new String[]{"From 2DA", "Known spells"};
        }
        s.add(new Bitmap(buffer, offset + 4, 4, "Show", list));
        restype = "2DA";
        break;
      }

      case 215: // Play visual effect
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Play where?",
                         new String[]{"Over target (unattached)", "Over target (attached)",
                                      "At target point"}));
        restype = "VEF:VVC:BAM";
        break;

      case 216: // Level drain
        s.add(new DecNumber(buffer, offset, 4, "# levels"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 218: // Stoneskin effect
        s.add(new DecNumber(buffer, offset, 4, "# skins"));
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset + 4, 4, "Use dice?", s_noyes));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 219: // Attack roll penalty
      {
        final String[] ids;
        if (Profile.isEnhancedEdition()) {
          ids = new String[]{"", "", "EA.IDS", "GENERAL.IDS", "RACE.IDS", "CLASS.IDS", "", "GENDER.IDS",
                             Profile.getProperty(Profile.Key.GET_IDS_ALIGNMENT), "KIT.IDS"};
        } else {
          ids = new String[]{"", "", "EA.IDS", "GENERAL.IDS", "RACE.IDS", "CLASS.IDS", "", "GENDER.IDS",
                             Profile.getProperty(Profile.Key.GET_IDS_ALIGNMENT)};
        }
        final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4, IdsTargetType.DEFAULT_NAME_TYPE, ids);
        s.add(param2.createIdsValueFromType(buffer));
        s.add(param2);
        break;
      }

      case 220: // Remove spell school protections
      case 229: // Remove protection by school
        s.add(new DecNumber(buffer, offset, 4, "Maximum level"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell school", s_school));
        break;

      case 221: // Remove spell type protections
      case 230: // Remove protection by type
        s.add(new DecNumber(buffer, offset, 4, "Maximum level"));
        s.add(new SecTypeBitmap(buffer, offset + 4, 4, "Spell type"));
        break;

      case 222: // Teleport field
        s.add(new DecNumber(buffer, offset, 4, "Maximum range"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 223: // Spell school deflection
      case 227: // Spell school turning
        s.add(new DecNumber(buffer, offset, 4, "# levels"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell school", s_school));
        if (Profile.isEnhancedEdition()) { restype = "SPL"; }
        break;

      case 226: // Spell type deflection
      case 228: // Spell type turning
        s.add(new DecNumber(buffer, offset, 4, "# levels"));
        s.add(new SecTypeBitmap(buffer, offset + 4, 4, "Spell type"));
        if (Profile.isEnhancedEdition()) { restype = "SPL"; }
        break;

      case 232: // Cast spell on condition
      {
        s.add(new Bitmap(buffer, offset, 4, "Target",
                         new String[]{"Caster", "Last hit by", "Nearest enemy", "Anyone"}));
        if (Profile.isEnhancedEdition()) {
          Bitmap item = new Bitmap(buffer, offset + 4, 4, "Condition",
                                   new String[]{"Target hit", "Enemy sighted", "HP below 50%",
                                                "HP below 25%", "HP below 10%", "If helpless",
                                                "If poisoned", "When attacked",
                                                "Target in range 4'", "Target in range 10'",
                                                "Unknown (every round)", "Took damage", "Actor killed",
                                                "Time of day is 'Special'",
                                                "Target in 'Special' range",
                                                "Target's state is 'Special'", "Target dies",
                                                "Target died", "Target turned by",
                                                "Target HP < 'Special'", "Target HP % < 'Special'",
                                                "Target's spell state is 'Special'"});
          s.add(item);
          if (parent != null && parent instanceof UpdateListener) {
            item.addUpdateListener((UpdateListener)parent);
          }
        } else {
          String[] condition = new String[]{"Target hit", "Enemy sighted", "HP below 50%",
                                            "HP below 25%", "HP below 10%", "If helpless",
                                            "If poisoned", "When attacked",
                                            "Target in range 4'", "Target in range 10'",
                                            "Unknown (every round)", "Took damage"};
          if (isTobEx) {
            s.add(new Bitmap(buffer, offset + 4, 2, "Condition", condition));
            s.add(new DecNumber(buffer, offset + 6, 2, "Trigger check period"));
          } else {
            s.add(new Bitmap(buffer, offset + 4, 4, "Condition", condition));
          }
        }
        restype = "SPL";
        break;
      }

      case 233: // Modify proficiencies
      {
        int mode = buffer.getShort(offset + 6);
        s.add(new MultiNumber(buffer, offset, 4, "# stars", 3, 2, new String[]{"Active class", "Original class"},
                              (mode == 1) && Profile.isEnhancedEdition()));
        if (isTobEx || Profile.isEnhancedEdition()) {
          final String idsFile = (Profile.getGame() == Profile.Game.PSTEE) ? "WPROF.IDS" : "STATS.IDS";
          s.add(new IdsBitmap(buffer, offset + 4, 2, "Proficiency", idsFile));
          Bitmap param2b = new Bitmap(buffer, offset + 6, 2, "Behavior", new String[]{"Set if higher", "Increment"});
          if (Profile.isEnhancedEdition()) {
            param2b.addUpdateListener((UpdateListener)parent);
          }
          s.add(param2b);
        } else {
          s.add(new IdsBitmap(buffer, offset + 4, 4, "Proficiency", "STATS.IDS"));
        }
        break;
      }

      case 234: // Create contingency
        s.add(new DecNumber(buffer, offset, 4, "Maximum spell level"));
        s.add(new DecNumber(buffer, offset + 4, 2, "# spells"));
        s.add(new Bitmap(buffer, offset + 6, 2, "Trigger type",
                         new String[]{"Chain contingency", "Contingency", "Spell sequencer"}));
        break;

      case 235: // Wing buffet
        s.add(new DecNumber(buffer, offset, 4, "Strength"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Direction",
                         new String[]{"", "Away from target point", "Away from source",
                                      "Towards target point", "Towards source"}));
        break;

      case 236: // Project image
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Image type",
                         new String[]{"Talkative, uncontrollable", "Mislead", "Project image",
                                      "Simulacrum"}));
        break;

      case 237: // Set image type
      {
        final LongIntegerHashMap<String> map = new LongIntegerHashMap<String>();
        map.put(0L, "Player1");
        map.put(1L, "Player2");
        map.put(2L, "Player3");
        map.put(3L, "Player4");
        map.put(4L, "Player5");
        map.put(5L, "Player6");
        map.put(0xffffffffL, "Not party member");
        s.add(new HashBitmap(buffer, offset, 4, "Puppet master", map));
        s.add(new Bitmap(buffer, offset + 4, 4, "Puppet type",
                         new String[]{"Talkative, uncontrollable", "Mislead", "Project image",
                                      "Simulacrum"}));
        break;
      }

      case 238: // Disintegrate
      {
        final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
        s.add(param2.createIdsValueFromType(buffer));
        s.add(param2);
        break;
      }

      case 239: // Farsight
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Can view unexplored?", s_noyes));
        break;

      case 240: // Remove portrait icon
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Icon", getIconDescArray()));
        break;

      case 241: // Control creature
      {
        final LongIntegerHashMap<String> map = new LongIntegerHashMap<String>();
        map.put(0L, "Charmed (neutral)");
        map.put(1L, "Charmed (hostile)");
        map.put(2L, "Dire charmed (neutral)");
        map.put(3L, "Dire charmed (hostile)");
        map.put(4L, "Controlled");
        map.put(5L, "Hostile");
        map.put(1000L, "Charmed (neutral, no text)");
        map.put(1001L, "Charmed (hostile, no text)");
        map.put(1002L, "Dire charmed (neutral, no text)");
        map.put(1003L, "Dire charmed (hostile, no text)");
        map.put(1004L, "Controlled (no text)");
        map.put(1005L, "Hostile (no text)");
        s.add(new IdsBitmap(buffer, offset, 4, "Creature type", "GENERAL.IDS"));
        s.add(new HashBitmap(buffer, offset + 4, 4, "Charm type", map));
        break;
      }

      case 243: // Drain item charges
        s.add(new Bitmap(buffer, offset, 4, "Include weapons?", s_noyes));
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset + 4, 4, "# to drain"));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        }
        break;

      case 244: // Drain wizard spells
        s.add(new DecNumber(buffer, offset, 4, "# spells"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 248: // Melee hit effect
      case 249: // Ranged hit effect
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "EFF";
        break;

      case 250: // Maximum damage each hit
        s.add(new DecNumber(buffer, offset, 4, "Damage value"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 253: // Set automap note
      case 254: // Remove automap note
      case 267: // Disable display string
        s.add(new StringRef(buffer, offset, "String"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 255: // Create item (days)
        s.add(new DecNumber(buffer, offset, 4, "# items in stack"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "ITM";
        break;

      case 257: // Create spell sequencer
        s.add(new DecNumber(buffer, offset, 4, "Maximum level"));
        s.add(new DecNumber(buffer, offset + 4, 4, "# spells"));
        break;

      case 259: // Spell trap
        s.add(new DecNumber(buffer, offset, 4, "# spells"));
        s.add(new DecNumber(buffer, offset + 4, 4, "Spell level"));
        if (Profile.isEnhancedEdition()) { restype = "SPL"; }
        break;

      case 261: // Restore lost spells
        s.add(new DecNumber(buffer, offset, 4, "Spell level"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell class", new String[]{"Wizard", "Priest"}));
        break;

      case 262: // Visual range bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{"Increment", "Set"}));
        break;

      case 263: // Backstab bonus
      case 275: // Hide in shadows bonus
      case 276: // Detect illusion bonus
      case 277: // Set traps bonus
      case 278: // THAC0 bonus
      case 281: // Wild surge bonus
      case 284: // Melee THAC0 bonus
      case 285: // Melee weapon damage bonus
      case 286: // Missile weapon damage bonus
      case 288: // Fist THAC0 bonus
      case 289: // Fist damage bonus
      case 305: // Off-hand THAC0 bonus
      case 306: // Main hand THAC0 bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        break;

      case 264: // Drop item
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Only quick weapons?", s_noyes));
        break;

      case 265: // Set global variable
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{"Set", "Increment"}));
        restype = EFFECT_STRING;
        break;

      case 269: // Shake screen
        s.add(new DecNumber(buffer, offset, 4, "Strength"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 272: // Use EFF file on condition
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Condition",
                         new String[]{"Once per second", "", "Value per second",
                                      "Once per value seconds", "Parameter3 per value seconds"}));
        restype = "EFF";
        break;

      case 273: // Zone of sweet air
      {
        if (isTobEx) {
          s.add(new Bitmap(buffer, offset, 4, "Mode", new String[]{ "Remove from CLEARAIR.2DA",
                                                                    "Remove from resource"}));
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        }
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        if (isTobEx || Profile.isEnhancedEdition()) {
          restype = "2DA";
        }
        break;
      }

      case 279: // Enable button
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (isTobEx || Profile.isEnhancedEdition()) {
          int size = isTobEx ? 15 : 16;
          String[] buttons = new String[size];
          System.arraycopy(s_button, 0, buttons, 0, 14);
          buttons[10] = "Bard song";
          buttons[14] = "Find traps";
          if (Profile.isEnhancedEdition()) {
            buttons[15] = "Inventory screen";
          }
          s.add(new Bitmap(buffer, offset + 4, 4, "Button", buttons));
        } else {
          s.add(new Bitmap(buffer, offset + 4, 4, "Button", s_button));
        }
        break;

      case 280: // Wild magic
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Affect",
                         new String[]{"", "Next spell only", "Use duration"}));
        break;

      case 282: // Modify script state
      {
        final String[] list = new String[]{
            "Scripting State 1", "Scripting State 2", "Scripting State 3", "Scripting State 4", "Scripting State 5",
            "Scripting State 6", "Scripting State 7", "Scripting State 8", "Scripting State 9",
            "Scripting State 10", "Melee THAC0 Bonus", "Melee Damage Bonus", "Missile Damage Bonus",
            "Disable Circle", "Fist THAC0 Bonus", "Fist Damage Bonus", "Class String Override Mixed",
            "Class String Override Lower", "Prevent Spell Protection Effects", "Immunity to Backstab",
            "Lockpicking Bonus", "Move Silently Bonus", "Find Traps Bonus", "Pickpocket Bonus",
            "Hide in Shadows Bonus", "Detect Illusions Bonus", "Set Traps Bonus", "Prevent AI Slowdown",
            "Existance Delay Override", "Animation-only Haste", "No Permanent Death", "Immune to Turn Undead",
            "Chaos Shield", "NPC Bump", "Use Any Item", "Assassinate", "Sex Changed", "Spell Failure Innate",
            "Immune to Tracking", "Dead Magic", "Immune to Timestop", "Immune to Sequester", "Stoneskins Golem",
            "Level Drain", "Do Not Draw"};
        if (Profile.isEnhancedEdition()) {
          list[0] += " / Wing Buffet";
          list[1] += " / Death Ward";
          list[2] += " / Level Drain Immunity";
          list[3] += " / Offensive Modifier";
          list[4] += " / Defensive Modifier";
          list[5] += " / Defensive Modifier";
          list[6] += " / Wizard Spell Immunity";
          list[7] += " / Wizard Protection from Energy";
          list[8] += " / Wizard Spell Trap";
          list[9] += " / Wizard Improved Alacrity";
        }
        for (int i = 10; i < list.length; i++) {
          // only first 10 scripting states are officially supported by this opcode
          list[i] += " [undocumented]";
        }
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "State", list));
        break;
      }

      case 283: // Use EFF file as curse
      {
        final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
        s.add(param2.createIdsValueFromType(buffer));
        s.add(param2);
        restype = "EFF";
        break;
      }

      case 290: // Change title
        s.add(new StringRef(buffer, offset, "Title"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Change where?",
                         new String[]{"Records screen", "Class name"}));
        break;

      case 294: // Set existence delay
        s.add(new DecNumber(buffer, offset, 4, "Stat value"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 296: // Immunity to specific animation
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "VEF:VVC:BAM";
        break;

      case 298: // Pocket plane
        if (isTobEx) {
          s.add(new Bitmap(buffer, offset, 4, "Store party location", new String[]{
              "Use pocket plane field", "Use party location field", "Do not store"
          }));
          s.add(new Bitmap(buffer, offset + 4, 4, "Use custom script?", s_noyes));
          restype = "BCS";
        } else {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, "Stat value"));
        }
        break;

      case 299: // Chaos shield effect
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Portrait",
                         new String[]{"Chaos Shield", "Improved Chaos Shield"}));
        break;

      case 300: // Modify collision behavior
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Flag(buffer, offset + 4, 4, "Behavior",
                       new String[]{"None", "NPC bumps PCs", "NPC can't be bumped"}));
        break;

      case 301: // Critical hit bonus
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Condition",
                         new String[]{"Always", "By this weapon only"}));
        break;

      case 303: // Backstab every hit
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        if (isTobEx) {
          LongIntegerHashMap<String> idsmap = new LongIntegerHashMap<String>();
          idsmap.put(0L, "Normal conditions");
          idsmap.put(1L, "Ignore visual state and position");
          idsmap.put(2L, "Ignore visual state only");
          idsmap.put(4L, "Ignore position only");
          s.add(new HashBitmap(buffer, offset + 4, 4, "Type", idsmap));
        } else {
          s.add(new DecNumber(buffer, offset + 4, 4, "Stat value"));
        }
        break;

      case 307: // Tracking
        s.add(new DecNumber(buffer, offset, 4, "Range"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 309: // Set local variable
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{"Set", "Increment"}));
        restype = EFFECT_STRING;
        break;

      case 314: // Stoneskin protection
        s.add(new DecNumber(buffer, offset, 4, "# skins"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 317: // Haste 2
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Haste type",
                         new String[]{"Normal", "Improved", "Movement rate only"}));
        break;

      case 318: // Protection from Spell, Ex: Set stat
        if (Profile.isEnhancedEdition()) {
          SpellProtType param2 = new SpellProtType(buffer, offset + 4, 4);
          s.add(param2.createCreatureValueFromType(buffer, offset));
          s.add(param2);
          restype = "ITM:SPL";
        } else if (isTobEx) {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new IdsBitmap(buffer, offset + 4, 2, "Stat opcode", "STATS.IDS"));
          s.add(new Bitmap(buffer, offset + 6, 2, "Modifier type", new String[]{
              "Increment", "Set", "Set % of", "Multiply", "Divide", "Modulus",
              "Logical AND", "Logical OR", "Bitwise AND", "Bitwise OR", "Invert"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 324: // Immunity to spell and message
      case 326: // Apply effects list
        if (Profile.isEnhancedEdition()) {
          SpellProtType param2 = new SpellProtType(buffer, offset + 4, 4);
          s.add(param2.createCreatureValueFromType(buffer, offset));
          s.add(param2);
          restype = (effectType == 324) ? "ITM:SPL" : "SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 319: // Restrict item (BGEE)
      {
        if (Profile.isEnhancedEdition()) {
          final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4,
                                                         IdsTargetType.DEFAULT_NAME_TYPE,
                                                         IdsTargetType.DEFAULT_SECOND_IDS, true);
          param2.addUpdateListener((UpdateListener)parent);
          s.add(param2.createIdsValueFromType(buffer));
          s.add(param2);
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;
      }

      case 320: // Change weather (BGEE)
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset, 4, "Type",
                           new String[]{"Normal", "Rain", "Snow", "Nothing"}));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 321: // Remove effects by resource (BGEE)
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Type",
                           new String[]{"Default", "Equipped effects list only",
                                        "Timed effects list only"}));
          restype = "ITM:SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 322: // AoE evade check (deprecated)
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 323: // Turn undead level
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 325: // All saving throws bonus
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", new String[]{
              s_inctype[0], s_inctype[1], s_inctype[2], "Increment instantaneously"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 327: // Show visual effect
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset, 4, "Target", new String[]{"Spell target", "Target point"}));
          s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_visuals));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 328: // Set spell state
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          int ofsSpecial = offset + (isV1 ? 0x28 : 0x2c);
          int special = buffer.getInt(ofsSpecial);
          if (special == 1) {
            s.add(new IdsBitmap(buffer, offset + 4, 4, "State", "SPLSTATE.IDS"));
          } else {
            s.add(new Bitmap(buffer, offset + 4, 4, "State", s_spellstate));
          }
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 329: // Slow poison
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Amount"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 330: // Float text
        if (Profile.isEnhancedEdition()) {
          s.add(new StringRef(buffer, offset, "String"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Display type",
                           new String[]{"String reference", "Cynicism"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 331: // Summon creatures 2
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "# creatures"));
          s.add(new Summon2daBitmap(buffer, offset + 4, 4, "2DA reference"));
          restype = "2DA";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 332: // Attack damage type bonus
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Damage type", s_damagetype));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 333: // Static charge
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "# hits"));
          s.add(new DecNumber(buffer, offset + 4, 4, "Cast at level"));
          restype = "SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 334: // Turn undead
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 335: // Seven eyes
        if (Profile.isEnhancedEdition()) {
          s.add(new IdsBitmap(buffer, offset, 4, "State", "SPLSTATE.IDS"));
          s.add(new DecNumber(buffer, offset + 4, 4, "Identifier"));
          restype = "SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 336: // Seven eyes overlay
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset, 4, "Last VVC letter",
                           new String[]{"None", "A", "B", "C", "D", "E", "F", "G"}));
          s.add(new DecNumber(buffer, offset + 4, 4, "Type"));
          restype = "VVC";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 337: // Remove effects by opcode
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Match 'Parameter 2' value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_effname));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 338: // Disable rest or save
        if (Profile.isEnhancedEdition()) {
          s.add(new StringRef(buffer, offset, "Message"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Mode",
                           new String[]{"Cannot rest", "Cannot save", "Cannot rest or save"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 339: // Alter visual animation effect
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset, 2, "Modifier type",
                           new String[]{"Set value", "AND value", "OR value", "XOR value",
                                        "AND NOT value"}));
          s.add(new DecNumber(buffer, offset + 2, 2, "Value"));
          s.add(new ProRef(buffer, offset + 4, 4, "Projectile"));
          restype = EFFECT_STRING;
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 340: // Backstab hit effect
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
          restype = "SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 341: // Critical hit effect
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Condition",
                           new String[]{"Always", "By this weapon only"}));
          restype = "SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 342: // Override creature data
        if (Profile.isEnhancedEdition()) {
          Bitmap bmp = new Bitmap(buffer, offset + 4, 4, "Field",
                                  new String[]{"Unknown", "Body heat", "Blood color", "Unknown",
                                               "Personal space"});
          switch (bmp.getValue()) {
            case 1:  s.add(new Bitmap(buffer, offset, 4, "Enabled?", s_noyes)); break;
            case 2:  s.add(new ColorValue(buffer, offset, 4, "Color")); break;
            default: s.add(new DecNumber(buffer, offset, 4, "Value"));
          }
          s.add(bmp);
          if (parent != null && parent instanceof UpdateListener) {
            bmp.addUpdateListener((UpdateListener)parent);
          }
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 343: // HP swap
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Mode",
                new String[]{"Swap if caster HP > target HP", "Always swap"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 344: // Enchantment vs. creature type
        if (Profile.isEnhancedEdition()) {
          final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
          s.add(param2.createIdsValueFromType(buffer));
          s.add(param2);
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 345: // Enchantment bonus
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Enchantment"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Type",
                           new String[]{"Magical weapons (<= 'Enchantment')", "Magical weapons (all)",
                                        "Non-magical weapons", "Silver", "Non-silver", "Non-silver/non-magical",
                                        "Two-Handed", "Non-two-handed", "Cursed", "Non-cursed",
                                        "Cold iron", "Non-cold iron"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 346: // Save vs. school bonus
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 347: // Move view to target
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, "Scroll speed"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 352: // Change Background
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, "Duration modifier"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
          restype = "BMP";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 353: // Tint screen
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
          final String[] s_method = {"Quick fade light->dark->light",
                                     "Quick fade light->dark->light",
                                     "Quick fade light->dark, instant fade light",
                                     "Quick fade light->dark, instant fade light",
                                     "Fade light->dark->light (duration)",
                                     "Fade light->dark->light (duration)",
                                     "Fade light->dark->light (duration)",
                                     "Fade light->dark->light (duration)",
                                     "No effect",
                                     "Very fast light->black->light",
                                     "Instant black for duration, instant light"};
          s.add(new Bitmap(buffer, offset + 4, 4, "Method", s_method));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 354: // Flash screen
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 355: // Soul exodus
      case 376: // Detect evil
      case 381: // Induce hiccups
      case 382: // Fist of iron
        // TODO: PSTEE confirm!
        makeEffectParamsDefault(buffer, offset, s);
        break;

      case 356: // Stop all actions
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, "Stat value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Mode", new String[]{"Pause actions", "Unpause actions"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 357: // Set state
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new Bitmap(buffer, offset, 4, "Action", new String[]{"Clear", "Set"}));
          s.add(new IdsFlag(buffer, offset + 4, 4, "State", "STATE.IDS"));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 358: // Set AI script
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new IdsBitmap(buffer, offset + 4, 4, "Script level", "SCRLEV.IDS"));
          restype = "BCS";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 360: // Ignore reputation breaking point
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 361: // Cast spell on critical miss
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Current weapon only?", s_noyes));
          restype = "SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 362: // Critical miss bonus
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Current weapon only?", s_noyes));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 363: // Movement rate check
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, "Movement rate"));
          s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 365: // Make unselectable
        if (Profile.isEnhancedEdition()) {
          s.add(new Bitmap(buffer, offset, 4, "Disable dialogue?", s_yesno));
          s.add(new Bitmap(buffer, offset + 4, 4, "Disable AI?", s_yesno));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 366: // Apply spell on movement
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
          restype = "SPL";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 367: // Minimum base stats
        if (Profile.isEnhancedEdition()) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Enabled?", s_noyes));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 369: // Play BAM file (single/dual)
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
          s.add(new Flag(buffer, offset + 4, 4, "Method",
                         new String[]{"Default", "Repeat animation", "Remove stickiness"}));
          restype = "BAM";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 370: // Play BAM file
      case 371: // Play BAM file 2
      case 372: // Play BAM file 3
      case 373: // Play BAM file 4
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
          s.add(new Flag(buffer, offset + 4, 4, "Flags",
                         new String[]{"", "Random placement", "", "", "", "Undetermined", "", "", "",
                                      "", "", "", "", "Sticky", "", "", "", "Undetermined (repeat)",
                                      "Foreground (repeat)", "", "",
                                      "Fade out transparency (blended)",
                                      "Color & transparency (blended)"}));
          restype = "BAM";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 374: // Special spell hit
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Effect",
                           new String[]{"Adder's kiss", "Ball lightning", "Fizzle"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 375: // Play BAM with effects
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Effect",
                           new String[]{"Cloak of warding", "Shield", "Black-barbed shield",
                                        "Pain mirror", "Guardian mantle", "", "Enoll eva's duplication",
                                        "Armor", "Antimagic shell", "", "", "Flame walk",
                                        "Protection from evil", "Conflagration", "Infernal shield",
                                        "Submerge the will", "Balance in all things"}));
          restype = "BAM";
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 378: // Prayer
      case 379: // Curse
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, "Amount"));
          s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 380: // Embalm
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
          s.add(new Bitmap(buffer, offset + 4, 4, "Embalming type", new String[]{"Normal", "Greater"}));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      case 383: // Hit point transfer
        // TODO: PSTEE confirm!
        if (Profile.getGame() == Profile.Game.PSTEE) {
          s.add(new DecNumber(buffer, offset, 4, "Amount"));
          s.add(new Bitmap(buffer, offset + 4, 2, "Direction",
                           new String[]{"Source to target", "Target to source", "Swap HP",
                                        "Source to target even over max. HP"}));
          s.add(new IdsBitmap(buffer, offset + 6, 2, "Damage type", "DAMAGES.IDS"));
        } else {
          makeEffectParamsDefault(buffer, offset, s);
        }
        break;

      default:
        makeEffectParamsDefault(buffer, offset, s);
        break;
    }

    return restype;
  }

  private String makeEffectParamsPST(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                     int effectType, boolean isV1)
  {
    String restype = null;
    switch (effectType) {
      case 110: // Retreat from
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Type",
                         new String[]{"Run", "Run", "Run", "Run", "Run", "Run", "Run",
                                      "Weak", "Walk"}));
        break;

      case 186: // Set state
        s.add(new Bitmap(buffer, offset, 4, "Action", new String[]{"Clear", "Set"}));
        s.add(new IdsFlag(buffer, offset + 4, 4, "State", "STATE.IDS"));
        break;

      case 188: // Play BAM file
      case 189: // Play BAM file 2
      case 190: // Play BAM file 3
      case 191: // Play BAM file 4
      {
        s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
        s.add(new Flag(buffer, offset + 4, 4, "Flags",
                       new String[]{"", "Random placement", "", "", "", "Undetermined", "", "", "",
                                    "", "", "", "", "Sticky", "", "", "", "Undetermined (repeat)",
                                    "Foreground (repeat)", "", "",
                                    "Fade out transparency (blended)",
                                    "Color & transparency (blended)"}));
//        final LongIntegerHashMap<String> m_playbam = new LongIntegerHashMap<String>();
//        m_playbam.put(0L, "Non-sticky, not 3D");
//        m_playbam.put(1L, "Random placement, not 3D");
//        m_playbam.put(528384L, "Sticky, 3D");
//        m_playbam.put(1179648L, "RGB transparent");
//        m_playbam.put(1183744L, "RGB transparent, 3D, sticky");
//        m_playbam.put(3280896L, "RGB transparent, 3D");
//        s.add(new HashBitmap(buffer, offset + 4, 4, "Properties", m_playbam));
        restype = "BAM";
        break;
      }

      case 192: // Hit point transfer
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 2, "Direction",
                         new String[]{"Source to target", "Target to source", "Swap HP",
                                      "Source to target even over max. HP"}));
        s.add(new IdsBitmap(buffer, offset + 6, 2, "Damage type", "DAMAGES.IDS"));
        break;

      case 193: // Shake screen
        s.add(new DecNumber(buffer, offset, 4, "Strength"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 194: // Flash screen
        s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 195: // Tint screen
      {
        final LongIntegerHashMap<String> m_fadeType = new LongIntegerHashMap<String>();
        m_fadeType.put(0L, "Quick fade light->dark->light");
        m_fadeType.put(1L, "Quick fade light->dark->light");
        m_fadeType.put(2L, "Quick fade light->dark, instant fade light");
        m_fadeType.put(3L, "Quick fade light->dark, instant fade light");
        m_fadeType.put(4L, "Fade light->dark->light (duration)");
        m_fadeType.put(5L, "Fade light->dark->light (duration)");
        m_fadeType.put(6L, "Fade light->dark->light (duration)");
        m_fadeType.put(7L, "Fade light->dark->light (duration)");
        m_fadeType.put(8L, "No effect");
        m_fadeType.put(9L, "Very fast light->black->light");
        m_fadeType.put(10L, "Instant black for duration, instant light");
        m_fadeType.put(100L, "Unknown");
        m_fadeType.put(200L, "Unknown");
        s.add(new ColorPicker(buffer, offset, "Color", ColorPicker.Format.RGBX));
        s.add(new HashBitmap(buffer, offset + 4, 4, "Method", m_fadeType));
        break;
      }

      case 196: // Special spell hit
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect",
                         new String[]{"Adder's kiss", "Ball lightning", "Fizzle"}));
        break;

      case 201: // Play BAM with effects
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect",
                         new String[]{"Cloak of warding", "Shield", "Black-barbed shield",
                                      "Pain mirror", "Guardian mantle", "", "Enoll eva's duplication",
                                      "Armor", "Antimagic shell", "", "", "Flame walk",
                                      "Protection from evil", "Conflagration", "Infernal shield",
                                      "Submerge the will", "Balance in all things"}));
        restype = "BAM";
        break;

      case 203: // Curse
      case 204: // Prayer
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 205: // Move view to target
        s.add(new DecNumber(buffer, offset, 4, "Scroll speed"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 206: // Embalm
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Embalming type",
                         new String[]{"Normal", "Greater"}));
        break;

      case 207: // Stop all actions
        s.add(new DecNumber(buffer, offset, 4, "Stat value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Mode",
                         new String[]{"Pause actions", "Unpause actions"}));
        break;

      case 208: // Fist of iron
      case 209: // Soul exodus
      case 210: // Detect evil
      case 211: // Induce hiccups
      case 212: // Speak with dead
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      default:
        makeEffectParamsDefault(buffer, offset, s);
        break;
    }

    return restype;
  }

  private String makeEffectParamsIWD(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                     int effectType, boolean isV1)
  {
    String restype = null;
    switch (effectType) {
      case 186: // Move creature
        s.add(new DecNumber(buffer, offset, 4, "Delay"));
        s.add(new IdsBitmap(buffer, offset + 4, 4, "Orientation", "DIR.IDS"));
        restype = "ARE";
        break;

      case 188: // Increase spells cast per round
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Cleanse aura?", s_noyes));
        break;

      case 189: // Increase casting speed factor
      case 190: // Increase attack speed factor
      case 234: // Snilloc's snowball swarm
      case 236: // Chill touch
      case 237: // Magical stone
      case 239: // Slow poison
      case 244: // Prayer
      case 245: // Bad prayer
      case 249: // Recitation
      case 250: // Bad recitation
      case 252: // Sol's searing orb
      case 277: // Soul eater
      case 281: // Vitriolic sphere
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 191: // Casting level bonus
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell class", new String[]{"Wizard", "Priest"}));
        break;

      case 192: // Find familiar
      case 210: // Power word, stun
      case 257: // Zombie lord aura
      case 260: // Hide creature
      case 262: // Pomab images
      case 267: // Cure confusion
      case 268: // Eye of the mind
      case 269: // Eye of the sword
      case 270: // Eye of the mage
      case 271: // Eye of venom
      case 272: // Eye of the spirit
      case 273: // Eye of fortitude
      case 274: // Eye of stone
      case 275: // Remove seven eyes
      case 278: // Shroud of flame
      case 282: // Hide hit points
      case 284: // Mace of disruption
      case 286: // Ranger tracking
      case 287: // Immunity to sneak attack
      case 289: // Dragon gem cutscene
      case 291: // Rod of smiting
      case 292: // Rest
      case 293: // Beholder dispel magic
      case 294: // Harpy wail
      case 295: // Jackalwere gaze
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 193: // Invisibility detection
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, "Stat value"));
        break;

      case 206: // Protection from spell
      case 290: // Display spell immunity string
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Creature type", s_cretype));
        restype = "SPL";
        break;

      case 208: // Minimum HP
        s.add(new DecNumber(buffer, offset, 4, "HP amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 218: // Stoneskin effect
        s.add(new DecNumber(buffer, offset, 4, "# skins"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 232: // Creature RGB color fade
        s.add(new ColorPicker(buffer, offset, "Color"));
        s.add(new HashBitmap(buffer, offset + 4, 2, "Location", m_colorloc, false));
        s.add(new DecNumber(buffer, offset + 6, 2, "Speed"));
        break;

      case 233: // Show visual effect
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_visuals));
        break;

      case 235: // Show casting glow
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Glow",
                         new String[]{"None", "Abjuration", "Conjuration", "Divination",
                                      "Enchantment", "Illusion", "Invocation", "Necromancy",
                                      "Transmutation"}));
        break;

      case 238: // All saving throws bonus
      case 266: // Movement rate modifier
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        break;

      case 240: // Summon creature 2
        s.add(new DecNumber(buffer, offset, 4, "# creatures"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Summon type",
                         new String[]{"Monster summoning 1", "Monster summoning 2",
                                      "Monster summoning 3", "Monster summoning 4",
                                      "Monster summoning 5", "Monster summoning 6",
                                      "Monster summoning 7", "Animal summoning 1",
                                      "Animal summoning 2", "Animal summoning 3",
                                      "Summon insects", "Creeping doom", "Malavon summon"}));
        break;

      case 241: // Vampiric touch
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Direction",
                         new String[]{"Target to source", "Source to target"}));
        break;

      case 242: // Show visual overlay
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Overlay",
                         new String[]{"Globe of invulnerability", "Shroud of flame",
                                      "Antimagic shell", "Otiluke's resilient sphere",
                                      "Protection from normal missiles", "Cloak of fear",
                                      "Entropy shield", "Fire aura", "Frost aura",
                                      "Insect plague", "Storm shell", "Shield of lathander",
                                      "Greater shield of lathander", "Seven eyes"}));
        break;

      case 243: // Animate dead
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Undead type", new String[]{"Normal", "Lich"}));
        break;

      case 246: // Summon creature 3
        s.add(new DecNumber(buffer, offset, 4, "# creatures"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Creature type",
                         new String[]{"Lizard man", "Troll", "Shadow", "Invisible stalker",
                                      "Fire elemental (wizard)", "Earth elemental (wizard)",
                                      "Water elemental (wizard)", "Fire elemental (priest)",
                                      "Earth elemental (priest)", "Water elemental (priest)",
                                      "Malavon earth elemental"}));
        break;

      case 247: // Beltyn's burning blood
      case 264: // Static charge
      case 265: // Cloak of fear
        s.add(new DecNumber(buffer, offset, 4, "# hits"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 248: // Summon shadow
        s.add(new DecNumber(buffer, offset, 4, "# creatures"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Shadow type",
                         new String[]{"Shadow", "Demishadow", "Shade"}));
        break;

      case 251: // Lich touch
      case 256: // Umber hulk gaze
        s.add(new DecNumber(buffer, offset, 4, "# seconds"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 253: // Bonus AC vs. weapons
        s.add(new DecNumber(buffer, offset, 4, "AC value"));
        s.add(new Flag(buffer, offset + 4, 4, "Bonus to",
                       new String[]{"All weapons", "Blunt weapons", "Missile weapons",
                                    "Piercing weapons", "Slashing weapons",
                                    "Set base AC to value"}));
        break;

      case 254: // Dispel specific spell
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Dispel type",
                         new String[]{"All effects", "Equipped effects only",
                                      "Limited effects only"}));
        restype = "SPL";
        break;

      case 255: // Salamander aura
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Aura type", new String[]{"Fire", "Frost"}));
        break;

      case 258: // Immunity to specific resource
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Type", new String[]{"Default", "Test and set to 0"}));
        break;

      case 259: // Summon creatures with cloud
        s.add(new DecNumber(buffer, offset, 4, "# creatures"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Summon type",
                         new String[]{"Default", "Ally", "Hostile", "Forced", "Genie"}));
        restype = "CRE";
        break;

      case 261: // Immunity to effect and string
      case 276: // Remove effect by type
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_effname));
        break;

      case 263: // Evil turn undead
      {
        final LongIntegerHashMap<String> map = new LongIntegerHashMap<String>();
        map.put(0L, "Charmed (neutral)");
        map.put(1L, "Charmed (hostile)");
        map.put(2L, "Dire charmed (neutral)");
        map.put(3L, "Dire charmed (hostile)");
        map.put(4L, "Controlled");
        s.add(new IdsBitmap(buffer, offset, 4, "Creature type", "GENERAL.IDS"));
        s.add(new HashBitmap(buffer, offset + 4, 4, "Type type", map));
        break;
      }

      case 279: // Animal rage
        s.add(new DecNumber(buffer, offset, 4, "# seconds"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Type",
                         new String[]{"LOS check (# seconds)", "State"}));
        break;

      case 280: // Turn undead
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Turn type",
                         new String[]{"Command", "Rebuke", "Destroy", "Panic", "Depend on caster"}));
        break;

      case 283: // Float text
        s.add(new StringRef(buffer, offset, "String"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Display type",
                         new String[]{"String reference", "Cynicism"}));
        break;

      case 285: // Force sleep
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Wake on damage?", s_yesno));
        break;

      case 288: // Set spell state
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "State", s_spellstate));
        break;

      case 296: // Set global variable
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Variable", new String[]{"RETURN_TO_LONELYWOOD"}));
        break;

      default:
        makeEffectParamsDefault(buffer, offset, s);
        break;
    }

    return restype;
  }

  private String makeEffectParamsIWD2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                      int effectType, boolean isV1)
  {
    String restype = null;
    switch (effectType) {
      case 61: // Alchemy
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type",
                         new String[]{"Increment", "Set", "Mastery"}));
        break;

      case 186: // Move creature
        s.add(new DecNumber(buffer, offset, 4, "Delay"));
        s.add(new IdsBitmap(buffer, offset + 4, 4, "Orientation", "DIR.IDS"));
        restype = "ARE";
        break;

      case 188: // Increase spells cast per round
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Cleanse aura?", s_noyes));
        break;

      case 189: // Increase casting speed factor
      case 190: // Increase attack speed factor
      case 239: // Slow poison
      case 281: // Vitriolic sphere
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 191: // Casting level bonus
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Spell class", new String[]{"Arcane", "Divine"}));
        break;

      case 192: // Find familiar
      case 236: // Panic undead
      case 260: // Hide creature
      case 267: // Cure confusion
      case 268: // Eye of the mind
      case 269: // Eye of the sword
      case 270: // Eye of the mage
      case 271: // Eye of venom
      case 272: // Eye of the spirit
      case 273: // Eye of fortitude
      case 274: // Eye of stone
      case 275: // Remove seven eyes
      case 278: // Shroud of flame
      case 282: // Hide hit points
      case 284: // Mace of disruption
      case 286: // Ranger tracking
      case 287: // Immunity to sneak attack
      case 292: // Rest
      case 293: // Beholder dispel magic
      case 294: // Harpy wail
      case 295: // Jackalwere gaze
      case 400: // Hopelessness
      case 401: // Protection from evil
      case 403: // Armor of faith
      case 405: // Enfeeblement
      case 407: // Death ward
      case 408: // Holy power
      case 414: // Otiluke's resilient sphere
      case 415: // Barkskin
      case 418: // Free action
      case 421: // Entropy shield
      case 422: // Storm shell
      case 423: // Protection from the elements
      case 424: // Hold undead
      case 425: // Control undead
      case 426: // Aegis
      case 427: // Executioner's eyes
      case 428: // Banish
      case 435: // Day blindness
      case 438: // Heroic inspiration
      case 440: // Barbarian rage
      case 442: // Cleave
      case 444: // Tenser's transformation
      case 445: // Slippery mind
      case 446: // Smite evil
      case 447: // Restoration
      case 448: // Alicorn lance
      case 451: // Lower resistance
      case 452: // Bane
      case 453: // Power attack
      case 454: // Expertise
      case 455: // Arterial strike
      case 456: // Hamstring
      case 457: // Rapid shot
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 193: // Invisibility detection
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Ignore visibility?", s_noyes));
        break;

      case 206: // Protection from spell
      case 290: // Display spell immunity string
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Creature type", s_cretype));
        restype = "SPL";
        break;

      case 208: // Minimum HP
      case 432: // Tortoise shell
        s.add(new DecNumber(buffer, offset, 4, "HP amount"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 218: // Stoneskin effect
        s.add(new DecNumber(buffer, offset, 4, "# skins"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Skin type", new String[]{"Stoneskin", "Iron skins"}));
        break;

      case 232: // Creature RGB color fade
        s.add(new ColorPicker(buffer, offset, "Color"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 233: // Show visual effect
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_visuals));
        break;

      case 235: // Show casting glow
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Glow",
                         new String[]{"None", "Abjuration", "Conjuration", "Divination",
                                      "Enchantment", "Illusion", "Invocation", "Necromancy",
                                      "Transmutation"}));
        break;

      case 238: // All saving throws bonus
      case 266: // Movement rate modifier
      case 297: // Hide in shadows bonus
      case 298: // Use magic device bonus
      case 441: // Force slow
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Modifier type", s_inctype));
        break;

      case 241: // Vampiric touch
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Direction",
                         new String[]{"Target to source", "Source to target"}));
        break;

      case 244: // Prayer
      case 249: // Recitation
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Prayer type",
                         new String[]{"Beneficial", "Detrimental"}));
        break;

      case 247: // Beltyn's burning blood
        s.add(new DecNumber(buffer, offset, 4, "# hits"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 248: // Summon shadow
        s.add(new DecNumber(buffer, offset, 4, "# creatures"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Shadow type",
                         new String[]{"Shadow", "Demishadow", "Shade"}));
        break;

      case 256: // Umber hulk gaze
        s.add(new DecNumber(buffer, offset, 4, "# seconds"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 254: // Dispel specific spell
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Dispel type",
                         new String[]{"All effects", "Equipped effects only", "Limited effects only"}));
        restype = "SPL";
        break;

      case 255: // Salamander aura
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Aura type", new String[]{"Fire", "Frost"}));
        break;

      case 261: // Immunity to effect and resource
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_effname));
        restype = "SPL";
        break;

      case 263: // Evil turn undead
      {
        final LongIntegerHashMap<String> map = new LongIntegerHashMap<String>();
        map.put(0L, "Charmed (neutral)");
        map.put(1L, "Charmed (hostile)");
        map.put(2L, "Dire charmed (neutral)");
        map.put(3L, "Dire charmed (hostile)");
        map.put(4L, "Controlled");
        s.add(new IdsBitmap(buffer, offset, 4, "Creature type", "GENERAL.IDS"));
        s.add(new HashBitmap(buffer, offset + 4, 4, "Type type", map));
        break;
      }

      case 264: // Static charge
      case 265: // Cloak of fear
      case 449: // Call lightning
        s.add(new DecNumber(buffer, offset, 4, "# hits"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "SPL";
        break;

      case 276: // Remove effect by type
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Effect", s_effname));
        break;

      case 279: // Animal rage
        s.add(new DecNumber(buffer, offset, 4, "# seconds"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Type",
                         new String[]{"LOS check (# seconds)", "State"}));
        break;

      case 280: // Turn undead
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Turn type",
                         new String[]{"Command", "Rebuke", "Destroy", "Panic", "Depend on caster"}));
        break;

      case 283: // Float text
        s.add(new StringRef(buffer, offset, "String"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Display type",
                         new String[]{"String reference", "Cynicism"}));
        break;

      case 285: // Force sleep
      case 419: // Unconsciousness
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Wake on damage?", s_yesno));
        break;

      case 288: // Set spell state
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new IdsBitmap(buffer, offset + 4, 4, "State", "SPLSTATE.IDS"));
        break;

      case 296: // Set global variable
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Variable", new String[]{"RETURN_TO_LONELYWOOD"}));
        break;

      case 402: // Apply effects list
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Creature type", s_cretype));
        restype = "SPL";
        break;

      case 404: // Nausea
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Nausea type",
                         new String[]{"Stinking cloud", "Ghoul touch"}));
        break;

      case 406: // Fire shield
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Shield type", new String[]{"Red", "Blue"}));
        restype = "SPL";
        break;

      case 409: // Righteous wrath of the faithful
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Affect",
                         new String[]{"Allies", "Allies and same alignment"}));
        break;

      case 410: // Summon friendly creature
      case 411: // Summon hostile creature
        s.add(new DecNumber(buffer, offset, 4, "# creatures"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Summon animation", s_sumanim));
        restype = "CRE";
        break;

      case 412: // Control creature
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Control type",
                         new String[]{"", "Default", "Mental domination"}));
        break;

      case 413: // Run visual effect
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Animation",
                         new String[]{"Sanctuary", "Entangle", "Wisp", "Shield", "Grease",
                                      "Web", "Minor globe of invulnerability",
                                      "Globe of invulnerability", "Shroud of flame",
                                      "Antimagic shell", "Otiluke's resilient sphere",
                                      "Protection from normal missiles", "Cloak of fear",
                                      "Entrophy shield", "Fire aura", "Frost aura",
                                      "Insect plague", "Storm shell", "Shield of lathander",
                                      "", "Greater shield of lathander", "", "Seven eyes",
                                      "", "Blur", "Invisibility", "Fire shield (red)",
                                      "Fire shield (blue)", "", "", "Tortoise shell", "Death armor"}));
        break;

      case 416: // Bleeding wounds
        s.add(new DecNumber(buffer, offset, 4, "Amount"));
        s.add(new Bitmap(buffer, offset + 4, 4, "Damage type",
                         new String[]{"Amount HP per round", "Amount HP per second",
                                      "1 HP per amount seconds"}));
        break;

      case 417: // Area effect using effects list
        s.add(new DecNumber(buffer, offset, 4, "Radius"));
        s.add(new Flag(buffer, offset + 4, 4, "Area effect type",
                       new String[]{"Instant", "Once per round", "Ignore target"}));
        restype = "SPL";
        break;

      case 420: // Death magic
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Flag(buffer, offset + 4, 4, "Death type",
                       new String[]{"Acid", "Burning", "Crushing", "Normal", "Exploding", "Stoned",
                                    "Freezing", "", "", "", "Permanent", "Destruction"}));
        break;

      case 429: // Apply effects list on hit
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "SPL";
        break;

      case 430: // Projectile type using effects list
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, "Projectile"));
        restype = "SPL";
        break;

      case 431: // Energy drain
        s.add(new DecNumber(buffer, offset, 4, "# levels"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 433: // Blink
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Blink type", new String[]{"Normal", "Empty body"}));
        break;

      case 434: // Persistent using effects list
        s.add(new DecNumber(buffer, offset, 4, "Interval"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        restype = "SPL";
        break;

      case 436: // Damage reduction
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new DecNumber(buffer, offset + 4, 4, "Enchantment to overcome"));
        break;

      case 437: // Disguise
        s.add(new DecNumber(buffer, offset, 4, "Value"));
        s.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
        break;

      case 439: // Prevent AI slowdown
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new DecNumber(buffer, offset + 4, 4, "Stat value"));
        break;

      case 443: // Protection from arrows
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Damage reduction",
                         new String[]{"None", "10/+1", "10/+2", "10/+3", "10/+4", "10/+5"}));
        break;

      case 450: // Globe of invulnerability
        s.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
        s.add(new Bitmap(buffer, offset + 4, 4, "Globe type",
                         new String[]{"Minor globe of invulnerability", "Globe of invulnerability"}));
        break;

      default:
        makeEffectParamsDefault(buffer, offset, s);
        break;
    }

    return restype;
  }

  private void makeEffectParamsDefault(ByteBuffer buffer, int offset, List<StructEntry> s)
  {
    if (s != null) {
      s.add(new DecNumber(buffer, offset, 4, EFFECT_PARAMETER_1));
      s.add(new DecNumber(buffer, offset + 4, 4, EFFECT_PARAMETER_2));
    }
  }

  private int makeEffectCommon1(ByteBuffer buffer, int offset, List<StructEntry> s, boolean isV1)
  {
    if (isV1) {
      s.add(new HashBitmap(buffer, offset, 1, EFFECT_TIMING_MODE, m_duration, false));
//      s.add(new Flag(buffer, offset + 1, 1, Effect2.EFFECT_DISPEL_TYPE, Effect2.s_dispel));
      s.add(new Bitmap(buffer, offset + 1, 1, Effect2.EFFECT_DISPEL_TYPE, Effect2.s_dispel));
      offset += 2;
    } else {
      s.add(new HashBitmap(buffer, offset, 4, EFFECT_TIMING_MODE, m_duration, false));
      offset += 4;
    }

    s.add(new DecNumber(buffer, offset, 4, EFFECT_DURATION));
    offset += 4;

    if (isV1) {
      s.add(new DecNumber(buffer, offset, 1, EFFECT_PROBABILITY_1));
      s.add(new DecNumber(buffer, offset + 1, 1, EFFECT_PROBABILITY_2));
      offset += 2;
    } else {
      s.add(new DecNumber(buffer, offset, 2, EFFECT_PROBABILITY_1));
      s.add(new DecNumber(buffer, offset + 2, 2, EFFECT_PROBABILITY_2));
      offset += 4;
    }

    return offset;
  }

  private int makeEffectResource(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                 int effectType, String resourceType, int param1, int param2)
  {
    if (resourceType == null) {
      if ((Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) &&
          effectType == 319 && param2 == 11) {    // Restrict item (BGEE)
        s.add(new TextString(buffer, offset, 8, "Script name"));
      } else {
        s.add(new Unknown(buffer, offset, 8, AbstractStruct.COMMON_UNUSED));
      }
    } else if (resourceType.equalsIgnoreCase(EFFECT_STRING)) {
      s.add(new TextString(buffer, offset, 8, EFFECT_STRING));
    } else {
      s.add(new ResourceRef(buffer, offset, EFFECT_RESOURCE, resourceType.split(":")));
    }
    offset += 8;

    return offset;
  }

  private int makeEffectCommon2(ByteBuffer buffer, int offset, List<StructEntry> s, boolean isV1)
  {
    String[] save_type = getSaveType();
    if (isV1) {
      s.add(new DecNumber(buffer, offset, 4, EFFECT_DICE_COUNT_MAX_LEVEL));
      s.add(new DecNumber(buffer, offset + 4, 4, EFFECT_DICE_SIZE_MIN_LEVEL));
      if (Profile.getEngine() == Profile.Engine.IWD2) {
        s.add(new Flag(buffer, offset + 8, 4, EFFECT_SAVE_TYPE, save_type));
        s.add(new DecNumber(buffer, offset + 12, 4, EFFECT_SAVE_PENALTY));
      }
      else {
        s.add(new Flag(buffer, offset + 8, 4, EFFECT_SAVE_TYPE, save_type));
        s.add(new DecNumber(buffer, offset + 12, 4, EFFECT_SAVE_BONUS));
      }
    } else {
      if (Profile.getEngine() == Profile.Engine.IWD2) {
        s.add(new Flag(buffer, offset, 4, EFFECT_SAVE_TYPE, save_type));
        s.add(new DecNumber(buffer, offset + 4, 4, EFFECT_SAVE_PENALTY));
        s.add(new DecNumber(buffer, offset + 8, 4, EFFECT_PARAMETER));
        s.add(new DecNumber(buffer, offset + 12, 4, EFFECT_PARAMETER));
      }
      else {
        s.add(new DecNumber(buffer, offset, 4, EFFECT_DICE_COUNT));
        s.add(new DecNumber(buffer, offset + 4, 4, EFFECT_DICE_SIZE));
        s.add(new Flag(buffer, offset + 8 , 4, EFFECT_SAVE_TYPE, save_type));
        s.add(new DecNumber(buffer, offset + 12, 4, EFFECT_SAVE_BONUS));
      }
    }
    offset += 16;

    return offset;
  }

  private int makeEffectParam25(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> s,
                                int effectType, String resourceType, int param1, int param2)
  {
    if (Profile.isEnhancedEdition()) {
      switch (effectType) {
        case 12:    // Damage
          s.add(new Flag(buffer, offset, 4, EFFECT_SPECIAL,
                         new String[]{"Default", "Drain HP to caster", "Transfer HP to target",
                                      "Fist damage only", "Drain to max. HP of caster", "",
                                      "Suppress damage feedback", "", "", "Save for half",
                                      "Made save", "Does not wake sleepers"}));
          break;

        case 23: // Reset morale
        {
          final Bitmap bmp = new Bitmap(buffer, offset, 4, "Mode", new String[] {"BG2 mode", "BG1 mode"});
          s.add(bmp);
          if (parent != null && parent instanceof UpdateListener) {
            bmp.addUpdateListener((UpdateListener)parent);
          }
          break;
        }

//      REMEMBER: Previous functionality moved to EFF V2, Parameter4
        case 25:  // Poison
        case 78:  // Disease
        case 98:  // Regeneration
        case 272:  // Use EFF file on condition
          s.add(new Bitmap(buffer, offset, 4, "Icon", getIconDescArray(true)));
          break;

        case 39:  // Sleep
        case 213: // Maze
        case 218: // Stoneskin effect
        {
          String[] array = getIconDescArray();
          array = Arrays.copyOf(array, array.length);
          array[0] = "Default icon";
          s.add(new Bitmap(buffer, offset, 4, "Icon", array));
          break;
        }

        case 145: // Disable spellcasting
          s.add(new Bitmap(buffer, offset, 4, "Display message?", s_yesno));
          break;

        case 178: // THAC0 vs. type bonus
        case 179: // Damage vs. type bonus
          s.add(new DecNumber(buffer, offset, 4, "Value"));
          break;

        case 181: // Disallow item type
        case 318: // Protection from spell
        case 319: // Restrict item (BGEE)
          s.add(new StringRef(buffer, offset, "Description note"));
          break;

        case 232:   // Cast spell on condition
          switch (param2) {
            case 13: // Time of day
              s.add(new IdsBitmap(buffer, offset, 4, EFFECT_SPECIAL, "TIMEODAY.IDS"));
              break;
            case 15: // State check
              s.add(new IdsFlag(buffer, offset, 4, EFFECT_SPECIAL, "STATE.IDS"));
              break;
            case 21:  // Spell State check
              s.add(new IdsBitmap(buffer, offset, 4, EFFECT_SPECIAL, "SPLSTATE.IDS"));
              break;
            default:
              s.add(new DecNumber(buffer, offset, 4, EFFECT_SPECIAL));
          }
          break;

        case 290: // Change title
            s.add(new IdsBitmap(buffer, offset, 4, "Class override", "CLASS.IDS"));
          break;

        case 301: // Critical hit bonus
        case 341: // Critical hit effect
        case 361: // Cast spell on critical miss
        case 362: // Critical miss bonus
          s.add(new Bitmap(buffer, offset, 4, "Attack type",
                           new String[]{"Any attack type", "Melee attack only", "Ranged attack only",
                                        "Magical attack only"}));
          break;

        case 328: // Set spell state (BGEE/IWDEE)
        {
          final Bitmap bmp = new Bitmap(buffer, offset, 4, "Mode", new String[]{"IWD mode", "IWD2 mode"});
          s.add(bmp);
          if (parent != null && parent instanceof UpdateListener) {
            bmp.addUpdateListener((UpdateListener)parent);
          }
          break;
        }

        case 331: // Summon creatures 2
          s.add(new Bitmap(buffer, offset, 4, "Mode",
                           new String[]{"Use dice", "Use dice", "Use caster level"}));
          break;

        case 333: // Static charge
          s.add(new DecNumber(buffer, offset, 4, "Delay"));
          break;

        case 335: // Seven eyes
          s.add(new DecNumber(buffer, offset, 4, "Eye group"));
          break;

        case 339: // Alter visual animation effect
          s.add(new DecNumber(buffer, offset, 4, "Range"));
          break;

        case 344: // Enchantment vs. creature type
          s.add(new DecNumber(buffer, offset, 4, "Enchantment"));
          break;

        case 345: // Enchantment bonus
          s.add(new Bitmap(buffer, offset, 4, "Weapon slot",
                           new String[]{"Current weapon", "Main hand weapon",
                                        "Off-hand weapon", "Both weapons"}));
          break;

        case 346: // Save vs. school bonus
          s.add(new PriTypeBitmap(buffer, offset, 4, "School of magic"));
          break;

        case 363: // Movement rate check
          s.add(new IdsBitmap(buffer, offset, 4, "Modal state to check", "MODAL.IDS"));
          break;

        case 365: // Make unselectable
          s.add(new Bitmap(buffer, offset, 4, "Use purple selection color?", s_yesno));
          break;

        case 366: // Apply spell on movement
          s.add(new IdsBitmap(buffer, offset, 4, "Modal state to check", "MODAL.IDS"));
          break;

        default:
          s.add(new DecNumber(buffer, offset, 4, EFFECT_SPECIAL));
          break;
      }
    } else if (Profile.getEngine() == Profile.Engine.BG2 ||
               Profile.getEngine() == Profile.Engine.IWD2) {
      s.add(new DecNumber(buffer, offset, 4, EFFECT_SPECIAL));
    } else if (Profile.getEngine() == Profile.Engine.PST) {
      switch (effectType) {
        case 12:  // Damage
          s.add(new Flag(buffer, offset, 4, "Specific visual for",
                         new String[]{"None", "The Nameless One", "Annah", "Grace", "Nordom",
                                      "Vhailor", "Morte", "Dakkon", "Ignus"}));
          break;

        default:
          s.add(new Unknown(buffer, offset, 4));
          break;
      }
    } else {
      s.add(new Unknown(buffer, offset, 4));
    }
    offset += 4;

    return offset;
  }
}
