// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.LongIntegerHashMap;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;

/**
 * Specialized Bitmap type for translating SPLPROT.2DA data into human-readable descriptions.
 */
public class SpellProtType extends Bitmap
{
  public static final String DEFAULT_NAME_TYPE    = "Creature type";
  public static final String DEFAULT_NAME_VALUE   = "Creature value";
  public static final String DEFAULT_NAME_UNUSED  = "Unused";
  public static final String[] s_relation = { "<=", "=", "<", ">", ">=", "!=",
                                              "bit_l_e", "bit_g_e", "bit_eq",
                                              "bit_uneq", "bit_greater", "bit_less" };
  // TODO: remove this array after all Enhanced Editions have been updated
  public static final String[] s_cretype_ee = {
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
      "", "",
      // 70..79
      "", "", "", "", "", "", "", "", "", "",
      // 80..89
      "", "", "", "", "", "", "", "", "", "",
      // 90..99
      "", "", "", "", "", "", "", "", "", "",
      // 100..109
      "", "", "EA.IDS", "GENERAL.IDS", "RACE.IDS", "CLASS.IDS", "SPECIFIC.IDS", "GENDER.IDS",
      "ALIGN.IDS", "KIT.IDS" };

  private static final String tableName = "SPLPROT.2DA";
  private static final LongIntegerHashMap<String> statIds = new LongIntegerHashMap<>();
  private static String[] creType;

  static {
    statIds.put(Long.valueOf(152L), "KIT.IDS");
    statIds.put(Long.valueOf(0x106L), "AREATYPE.IDS");
//    statIds.put(Long.valueOf(0x107L), "TIMEODAY.IDS");
    statIds.put(Long.valueOf(0x10aL), "EA.IDS");
    statIds.put(Long.valueOf(0x10bL), "GENERAL.IDS");
    statIds.put(Long.valueOf(0x10cL), "RACE.IDS");
    statIds.put(Long.valueOf(0x10dL), "CLASS.IDS");
    statIds.put(Long.valueOf(0x10eL), "SPECIFIC.IDS");
    statIds.put(Long.valueOf(0x10fL), "GENDER.IDS");
    statIds.put(Long.valueOf(0x110L), "ALIGNMEN.IDS");
    statIds.put(Long.valueOf(0x111L), "STATE.IDS");
    statIds.put(Long.valueOf(0x112L), "SPLSTATE.IDS");
  }

  private final int index;
  private final boolean isExternalized;
  private boolean updateIdsValues;  // defines whether to update the "Creature value" field automatically

  public SpellProtType(ByteBuffer buffer, int offset, int length)
  {
    this(buffer, offset, length, null, -1);
  }

  public SpellProtType(ByteBuffer buffer, int offset, int length, String name, int idx)
  {
    super(buffer, offset, length, createFieldName(name, idx, DEFAULT_NAME_TYPE), getTypeTable());
    this.index = idx;
    this.isExternalized = isTableExternalized();
    this.updateIdsValues = true;
  }

//--------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    boolean retVal = super.updateValue(struct);
    if (updateIdsValues && retVal) {
      int valueOffset = getOffset() - getSize();
      List<StructEntry> list = struct.getList();
      for (int i = 0, size = list.size(); i < size; i++) {
        StructEntry entry = list.get(i);
        if (entry.getOffset() == valueOffset && entry instanceof Datatype) {
          final ByteBuffer buffer = entry.getDataBuffer();
          final StructEntry newEntry = createCreatureValueFromType(buffer, 0);
          newEntry.setOffset(valueOffset);
          list.set(i, newEntry);

          // notifying listeners
          struct.fireTableRowsUpdated(i, i);
        }
      }
    }
    return retVal;
  }

//--------------------- End Interface Editable ---------------------

  public StructEntry createCreatureValueFromType(ByteBuffer buffer)
  {
    return createCreatureValueFromType(buffer, getOffset() - getSize(), getSize(), null);
  }

  public StructEntry createCreatureValueFromType(ByteBuffer buffer, int offset)
  {
    return createCreatureValueFromType(buffer, offset, getSize(), null);
  }

  public StructEntry createCreatureValueFromType(ByteBuffer buffer, int offset, int size, String name)
  {
    if (useCustomValue()) {
      String idsFile = getIdsFile();
      if (!idsFile.isEmpty()) {
        return new IdsBitmap(buffer, offset, size, createFieldName(name, index, DEFAULT_NAME_VALUE), idsFile);
      } else {
        return new DecNumber(buffer, offset, size, createFieldName(name, index, DEFAULT_NAME_VALUE));
      }
    }
    return new DecNumber(buffer, offset, size, createFieldName(name, index, DEFAULT_NAME_UNUSED));
  }

  /** Returns whether this SpellProtType instance automatically updates the associated creature value field. */
  public boolean isUpdatingCreatureValues()
  {
    return updateIdsValues;
  }

  /** Specify whether this SpellProtType instance should automatically update the associated creature value field. */
  public void setUpdateCreatureValues(boolean b)
  {
    updateIdsValues = b;
  }

  /** Returns whether this datatype makes use of the externalized table for creature types. */
  public boolean isExternalized()
  {
    return isExternalized;
  }

  /** Returns true if the specified creature type value depends on a user-defined value. */
  public boolean useCustomValue()
  {
    int value = getValue();
    if (isExternalized()) {
      Table2da table = Table2daCache.get(tableName);
      if (table != null) {
        return (-1 == toNumber(table.get(value, 2), 0));
      }
    } else {
      if (value >= 0 && value < s_cretype_ee.length) {
        return s_cretype_ee[value].endsWith(".IDS");
      }
    }
    return false;
  }

  /** Returns the IDS resource name use by the specified creature type. Returns an empty string if unused. */
  public String getIdsFile()
  {
    int value = getValue();
    if (isExternalized()) {
      Table2da table = Table2daCache.get(tableName);
      if (table != null) {
        int id = toNumber(table.get(value, 1), -1);
        String retVal = statIds.get(Long.valueOf((long)id));
        if (retVal != null) {
          return retVal;
        }
      }
    } else {
      if (value >= 0 && value < s_cretype_ee.length) {
        if (s_cretype_ee[value].endsWith(".IDS")) {
          return s_cretype_ee[value];
        }
      }
    }
    return "";
  }

  /** Returns whether creature table has been externalized into a 2DA file. */
  public static boolean isTableExternalized()
  {
    if (Profile.isEnhancedEdition()) {
      return ResourceFactory.resourceExists(tableName);
    } else {
      return false;
    }
  }

  /** Returns name of the 2DA resource used as reference for the list. */
  public static String getTableName()
  {
    return tableName;
  }

  /** Returns true if the specified creature type value depends on a user-defined value. */
  public static boolean useCustomValue(int value)
  {
    if (isTableExternalized()) {
      Table2da table = Table2daCache.get(tableName);
      if (table != null) {
        return (-1 == toNumber(table.get(value, 2), 0));
      }
    } else {
      if (value >= 0 && value < s_cretype_ee.length) {
        return s_cretype_ee[value].endsWith(".IDS");
      }
    }
    return false;
  }

  /** Returns the IDS resource name use by the specified creature type. Returns an empty string if unused. */
  public static String getIdsFile(int value)
  {
    if (isTableExternalized()) {
      Table2da table = Table2daCache.get(tableName);
      if (table != null) {
        boolean isCustom = (-1 == toNumber(table.get(value, 2), 0));
        if (isCustom) {
          int id = toNumber(table.get(value, 1), -1);
          String retVal = statIds.get(Long.valueOf((long)id));
          if (retVal != null) {
            return retVal;
          }
        }
      }
    } else if (value >= 0 && value < s_cretype_ee.length) {
      if (s_cretype_ee[value].endsWith(".IDS")) {
        return s_cretype_ee[value];
      }
    }
    return "";
  }

  public static String[] getTypeTable()
  {
    if (creType == null) {
      if (isTableExternalized()) {
        creType = getExternalizedTypeTable();
      } else {
        creType = Arrays.copyOf(s_cretype_ee, s_cretype_ee.length);
      }
    }
    return creType;
  }

  public static void resetTypeTable()
  {
    creType = null;
    Table2daCache.cacheInvalid(ResourceFactory.getResourceEntry(tableName));
  }

  /** Returns an array of descriptions based on the entries in the 2DA resource. */
  private static String[] getExternalizedTypeTable()
  {
    String[] retVal = null;
    if (ResourceFactory.resourceExists(tableName)) {
      Table2da table = Table2daCache.get(tableName);
      if (table != null) {
        retVal = new String[table.getRowCount()];
        for (int row = 0, size = table.getRowCount(); row < size; row++) {
          String label;
          int stat = toNumber(table.get(row, 1), -1);
          int value = toNumber(table.get(row, 2), -1);
          int rel = toNumber(table.get(row, 3), -1);
          switch (stat) {
            case 0x100: // source equals target
              label = "Source";
              break;
            case 0x101: // source is not target
              label = "Not source";
              break;
            case 0x102: // circle size
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("Circle size %s %d [0x%x]", getRelation(rel), value, value);
              } else {
                label = String.format("Circle size %s %d", getRelation(rel), value);
              }
              break;
            case 0x103: // use two rows of splprot.2da
              label = String.format("Match entries %d or %d", value, rel);
              break;
            case 0x104: // negate 0x103
              label = String.format("Not match entries %d and %d", value, rel);
              break;
            case 0x105: // source and target morale match
              switch (rel) {
                case 1: label = "Same morale alignment"; break;
                case 5: label = "Not same morale alignment"; break;
                default: label = "Morale alignment relation: " + getRelation(rel); break;
              }
              break;
            case 0x106: // areatype (like outdoors, forest, etc)
              if (isBitwiseRelation(rel) && value != -1) {
              } else {
                label = String.format("AREATYPE %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("AREATYPE.IDS", value, isBitwiseRelation(rel)),
                                      value);
              }
                label = String.format("AREATYPE %s %s",
                                      getRelation(rel),
                                      getIdsValue("AREATYPE.IDS", value, isBitwiseRelation(rel)));
              break;
            case 0x107: // daytime
              label = String.format("Time of day is %d to %d", value, rel);
              break;
            case 0x108: // source and target ethical match
              switch (rel) {
                case 1: label = "Same ethical alignment"; break;
                case 5: label = "Not same ethical alignment"; break;
                default: label = "Ethical alignment: " + getRelation(rel); break;
              }
              break;
            case 0x109: // evasion
              label = "Evasion check";
              break;
            case 0x10a: // EA
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("EA %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("EA.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("EA %s %s",
                                      getRelation(rel),
                                      getIdsValue("EA.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x10b: // GENERAL
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("GENERAL %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("GENERAL.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("GENERAL %s %s",
                                      getRelation(rel),
                                      getIdsValue("GENERAL.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x10c: // RACE
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("RACE %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("RACE.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("RACE %s %s",
                                      getRelation(rel),
                                      getIdsValue("RACE.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x10d: // CLASS
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("CLASS %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("CLASS.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("CLASS %s %s",
                                      getRelation(rel),
                                      getIdsValue("CLASS.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x10e: // SPECIFIC
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("SPECIFIC %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("SPECIFIC.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("SPECIFIC %s %s",
                                      getRelation(rel),
                                      getIdsValue("SPECIFIC.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x10f: // GENDER
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("GENDER %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("GENDER.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("GENDER %s %s",
                                      getRelation(rel),
                                      getIdsValue("GENDER.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x110: // ALIGNMENT
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("ALIGNMENT %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("ALIGNMEN.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("ALIGNMENT %s %s",
                                      getRelation(rel),
                                      getIdsValue("ALIGNMEN.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x111: // STATE
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("STATE %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("STATE.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("STATE %s %s",
                                      getRelation(rel),
                                      getIdsValue("STATE.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x112: // SPELL STATE
              if (isBitwiseRelation(rel) && value != -1) {
                label = String.format("SPLSTATE %s %s [0x%x]",
                                      getRelation(rel),
                                      getIdsValue("SPLSTATE.IDS", value, isBitwiseRelation(rel)),
                                      value);
              } else {
                label = String.format("SPLSTATE %s %s",
                                      getRelation(rel),
                                      getIdsValue("SPLSTATE.IDS", value, isBitwiseRelation(rel)));
              }
              break;
            case 0x113: // source and target allies
              switch (rel) {
                case 1: label = "Allies"; break;
                case 5: label = "Not allies"; break;
                default: label = "Allies match: " + getRelation(rel); break;
              }
              break;
            case 0x114: // source and target enemies
              switch (rel) {
                case 1: label = "Enemies"; break;
                case 5: label = "Not enemies"; break;
                default: label = "Enemies match: " + getRelation(rel); break;
              }
              break;
            case 0x115: // summon creature limit
              label = String.format("# summoned creatures %s specified value", getRelation(rel));
              break;
            case 0x116: // chapter check
              label = String.format("Chapter %s specified value", getRelation(rel));
              break;
            default:    // use values from STATS.IDS
              if (stat >= 0 && stat < 0x100) {
                // valid stat
                if (value == -1) {
                  label = String.format("STAT %s %s specified value",
                                        getIdsValue("STATS.IDS", stat, isBitwiseRelation(rel)),
                                        getRelation(rel));
                } else {
                  if (isBitwiseRelation(rel)) {
                    label = String.format("STAT %s %s %x",
                                          getIdsValue("STATS.IDS", stat, isBitwiseRelation(rel)),
                                          getRelation(rel), value);
                  } else {
                    label = String.format("STAT %s %s %d",
                                          getIdsValue("STATS.IDS", stat, isBitwiseRelation(rel)),
                                          getRelation(rel), value);
                  }
                }
              } else {
                label = "Undefined";
              }
              break;
          }
          retVal[row] = label;
        }
      }
    }
    return retVal;
  }

  // Attempts to convert the specified string into a number. Returns the default value on error.
  private static int toNumber(String value, int defValue)
  {
    int retVal = defValue;
    if (value != null && !value.isEmpty()) {
      try {
        if (value.toLowerCase().startsWith("0x")) {
          retVal = Integer.parseInt(value.substring(2), 16);
        } else {
          retVal = Integer.parseInt(value);
        }
      } catch (NumberFormatException e) {
      }
    }
    return retVal;
  }

  // Returns the IDS symbol based on the specified arguments.
  // Returns a decimal or hexadecimal number as string if symbol not found.
  private static String getIdsValue(String idsFile, int value, boolean asHex)
  {
    if (value == -1) {
      return "specified value";
    } else {
      IdsMap map = IdsMapCache.get(idsFile);
      if (map != null) {
        IdsMapEntry entry = map.get((long)value);
        if (entry != null) {
          if (entry.getSymbol() != null && !entry.getSymbol().isEmpty()) {
            return entry.getSymbol();
          }
        } else if (value == 0 && "STATS.IDS".equalsIgnoreCase(idsFile)) {
          // XXX: Workaround for EE since patch 2.5. Remove if symbol has been added to STATS.IDS.
          return "CURHITPOINTS";
        }
      }
      return asHex ? "0x" + Integer.toHexString(value) : Integer.toString(value);
    }
  }

  // Returns a textual representation of the specified relation code
  private static String getRelation(int rel)
  {
    return (rel >= 0 && rel < s_relation.length) ? s_relation[rel] : "";
  }

  // Returns whether specified code indicates a binary operator
  private static boolean isBitwiseRelation(int rel)
  {
    return (rel >= 6 && rel <= 11);
  }

  // Creates a valid field name from the specified arguments
  private static String createFieldName(String name, int index, String defName)
  {
    if (name == null) {
      name = (defName != null) ? defName : DEFAULT_NAME_TYPE;
    }
    return (index >= 0) ? (name + " " + index) : name;
  }
}
