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

/** Specialized HashBitmap type for parsing SMTABLES.2DA from IWDEE. */
public class Summon2daBitmap extends HashBitmap
{
  private static final String TableName = "SMTABLES.2DA";
  private static final LongIntegerHashMap<String> summonMap = new LongIntegerHashMap<String>();

  public Summon2daBitmap(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name, getSummonTable());
  }

  public static String getTableName()
  {
    return TableName;
  }

  private static synchronized LongIntegerHashMap<String> getSummonTable()
  {
    if (summonMap.isEmpty()) {
      if (ResourceFactory.resourceExists(TableName)) {
        Table2da table = Table2daCache.get(TableName);
        if (table != null) {
          for (int row = 0, size = table.getRowCount(); row < size; row++) {
            String[] sid = table.get(row, 0).split("_");
            if (sid.length > 0) {
              try {
                long id = Long.parseLong(sid[0]);
                String resref = table.get(row, 1).toUpperCase(Locale.ENGLISH) + ".2DA";
                summonMap.put(Long.valueOf(id), resref);
                if (!ResourceFactory.resourceExists(resref)) {
                  System.err.println("Resource does not exist: " + resref);
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    }
    return summonMap;
  }

  public static synchronized void resetSummonTable()
  {
    summonMap.clear();
    Table2daCache.cacheInvalid(ResourceFactory.getResourceEntry(TableName));
  }
}
