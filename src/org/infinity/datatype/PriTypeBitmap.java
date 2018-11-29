// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.Locale;

import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.LongIntegerHashMap;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;

/** Specialized HashBitmap type for parsing "primary type" entries. */
public class PriTypeBitmap extends HashBitmap
{
  private static final String TableName;
  private static final String[] s_school = {"None", "Abjurer", "Conjurer", "Diviner", "Enchanter",
                                            "Illusionist", "Invoker", "Necromancer", "Transmuter",
                                            "Generalist"};
  private static final LongIntegerHashMap<String> typeMap = new LongIntegerHashMap<String>();

  static {
    if (ResourceFactory.resourceExists("MSCHOOL.2DA")) {
      TableName = "MSCHOOL.2DA";
    } else if (ResourceFactory.resourceExists("SCHOOL.IDS")) {
      TableName = "SCHOOL.IDS";
    } else {
      TableName = "";
    }
  }

  public PriTypeBitmap(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name, getTypeTable());
  }

  public static String getTableName()
  {
    return ResourceFactory.resourceExists(TableName) ? TableName : "";
  }

  public static String[] getTypeArray()
  {
    final LongIntegerHashMap<String> map = getTypeTable();
    return map.values().toArray(new String[map.size()]);
  }

  private static synchronized LongIntegerHashMap<String> getTypeTable()
  {
    if (typeMap.isEmpty()) {
      if (ResourceFactory.resourceExists(TableName) && TableName.endsWith(".2DA")) {
        // using MSCHOOL.2DA
        Table2da table = Table2daCache.get(TableName);
        if (table != null) {
          for (int row = 0, size = table.getRowCount(); row < size; row++) {
            long id = row;
            String label = table.get(row, 0).toUpperCase(Locale.ENGLISH);
            typeMap.put(Long.valueOf(id), label);
          }
          if (typeMap.size() == 10) {
            // XXX: Doesn't appear to be listed in unmodded games
            typeMap.put(Long.valueOf(10L), "WILDMAGE");
          }
        }
      } else if (ResourceFactory.resourceExists(TableName) && TableName.endsWith(".IDS")) {
        // using SCHOOL.IDS
        typeMap.put(Long.valueOf(0L), "NONE");
        IdsMap map = IdsMapCache.get(TableName);
        for (final IdsMapEntry e: map.getAllValues()) {
          typeMap.put(Long.valueOf(e.getID()), e.getSymbol().toUpperCase(Locale.ENGLISH));
        }
      } else {
        // using predefined values
        for (int i = 0; i < s_school.length; i++) {
          typeMap.put(Long.valueOf(i), s_school[i].toUpperCase(Locale.ENGLISH));
        }
        if (typeMap.size() == 10 && Profile.getGame() == Profile.Game.PSTEE) {
          typeMap.put(Long.valueOf(10L), "WILDMAGE");
        }
      }
    }
    return typeMap;
  }

  public static synchronized void resetTypeTable()
  {
    typeMap.clear();
    if (TableName.endsWith(".2DA")) {
      Table2daCache.cacheInvalid(ResourceFactory.getResourceEntry(TableName));
    } else if (TableName.endsWith(".IDS")) {
      IdsMapCache.remove(ResourceFactory.getResourceEntry(TableName));
    }
  }
}
