// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.Locale;

import org.infinity.resource.ResourceFactory;
import org.infinity.util.LongIntegerHashMap;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;

/** Specialized HashBitmap type for parsing "secondary type" entries. */
public class SecTypeBitmap extends HashBitmap
{
  private static final String TableName = ResourceFactory.resourceExists("MSECTYPE.2DA") ? "MSECTYPE.2DA" : "";
  private static final String[] s_category = {"None", "Spell protections", "Specific protections",
                                              "Illusionary protections", "Magic attack",
                                              "Divination attack", "Conjuration", "Combat protections",
                                              "Contingency", "Battleground", "Offensive damage",
                                              "Disabling", "Combination", "Non-combat"};
  private static final LongIntegerHashMap<String> typeMap = new LongIntegerHashMap<String>();

  public SecTypeBitmap(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name, getTypeTable());
  }

  public static String getTableName()
  {
    return TableName;
  }

  public static String[] getTypeArray()
  {
    final LongIntegerHashMap<String> map = getTypeTable();
    return map.values().toArray(new String[map.size()]);
  }

  private static synchronized LongIntegerHashMap<String> getTypeTable()
  {
    if (typeMap.isEmpty()) {
      if (ResourceFactory.resourceExists(TableName)) {
        // using MSECTYPE.2DA
        Table2da table = Table2daCache.get(TableName);
        if (table != null) {
          for (int row = 0, size = table.getRowCount(); row < size; row++) {
            long id = row;
            String label = table.get(row, 0).toUpperCase(Locale.ENGLISH);
            typeMap.put(Long.valueOf(id), label);
          }
        }
      } else {
        // using predefined values
        for (int i = 0; i < s_category.length; i++) {
          typeMap.put(Long.valueOf(i), s_category[i].toUpperCase(Locale.ENGLISH));
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
    }
  }
}
