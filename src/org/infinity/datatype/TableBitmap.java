// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.Locale;

import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;

/** A general-purpose Bitmap type for creating lists of 2DA table entries. */
public class TableBitmap extends Bitmap
{
  /**
   * Constructs a list control from the first column of the specified 2DA resource. List entries
   * will be normalized (i.e. underscores to spaces, each word starts with capital letter).
   *
   * @param buffer
   * @param offset
   * @param length
   * @param name
   * @param tableName 2DA resource name
   */
  public TableBitmap(ByteBuffer buffer, int offset, int length, String name,
                     String tableName)
  {
    super(buffer, offset, length, name, generateList(tableName, 0, true));
  }

  /**
   * Constructs a list control from the given column o fthe specified 2DA resource.
   *
   * @param buffer
   * @param offset
   * @param length
   * @param name
   * @param tableName 2DA resource name
   * @param column Column to use strings from
   * @param normalize Whether to normalize strings (underscores to spaces, each word starts with capital letter)
   */
  public TableBitmap(ByteBuffer buffer, int offset, int length, String name,
                     String tableName, int column, boolean normalize)
  {
    super(buffer, offset, length, name, generateList(tableName, column, normalize));
  }


  private static String[] generateList(String tableName, int column, boolean normalize)
  {
    String[] retVal = null;

    Table2da table = Table2daCache.get(tableName);
    if (table != null) {
      retVal = new String[table.getRowCount()];

      column = Math.min(table.getColCount() - 1, Math.max(0, column));
      for (int row = 0, rowMax = table.getRowCount(); row < rowMax; row++) {
        String label = table.get(row, column);
        if (normalize) {
          label = normalizeString(label);
        }
        if (label.isEmpty()) {
          label = Integer.toString(row);
        }
        retVal[row] = label;
      }
    } else {
      retVal = new String[1];
      retVal[0] = "Unknown";
    }

    return retVal;
  }

  private static String normalizeString(String s)
  {
    if (s != null && !s.isEmpty()) {
      StringBuilder sb = new StringBuilder(s.replace('_', ' ').toLowerCase(Locale.ENGLISH));

      sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));

      int len = sb.length();
      int idx = 0;
      while (idx >= 0) {
        idx = sb.indexOf(" ", idx + 1);
        if (idx > 0 && idx+1 < len) {
          sb.setCharAt(idx+1, Character.toUpperCase(sb.charAt(idx+1)));
        }
      }

      s = sb.toString();
    }
    return s;
  }
}
