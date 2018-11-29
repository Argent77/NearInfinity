// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;

public final class IdsFlag extends Flag
{
  public IdsFlag(ByteBuffer buffer, int offset, int length, String name, String resource)
  {
    super(buffer, offset, length, name);
    IdsMap idsMap = IdsMapCache.get(resource);
    IdsMapEntry entry = idsMap.get(0L);
    setEmptyDesc((entry != null) ? entry.getSymbol() : null);

    // fetching flag labels from IDS
    String[] stable = new String[8 * length];
    for (int i = 0; i < stable.length; i++) {
      entry = idsMap.get(Long.valueOf(1L << i));
      stable[i] = (entry != null) ? entry.getSymbol() : null;
    }
    setFlagDescriptions(length, stable, 0, ';');
  }
}
