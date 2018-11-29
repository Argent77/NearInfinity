// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.LongIntegerHashMap;

public class IdsBitmap extends HashBitmap
{
  public IdsBitmap(ByteBuffer buffer, int offset, int length, String name, String resource)
  {
    this(buffer, offset, length, name, resource, 0, -1);
  }

  public IdsBitmap(ByteBuffer buffer, int offset, int length, String name, String resource,
                   int idsStart)
  {
    this(buffer, offset, length, name, resource, idsStart, -1);
  }

  public IdsBitmap(ByteBuffer buffer, int offset, int length, String name, String resource,
                   int idsStart, int idsSize)
  {
    super(buffer, offset, length, name, createResourceList(resource, idsStart, idsSize), true);
  }

  public int getIdsMapEntryCount()
  {
    return getHashBitmap().size();
  }

  public IdsMapEntry getIdsMapEntryByIndex(int index)
  {
    if (index >= 0 && index < getHashBitmap().size()) {
      return (IdsMapEntry)getHashBitmap().get(getHashBitmap().keys()[index]);
    } else {
      return null;
    }
  }

  public IdsMapEntry getIdsMapEntryById(long id)
  {
    return (IdsMapEntry)getHashBitmap().get(Long.valueOf(id));
  }

  public void addIdsMapEntry(IdsMapEntry entry)
  {
    if (entry != null) {
      @SuppressWarnings("unchecked")
      LongIntegerHashMap<IdsMapEntry>map = (LongIntegerHashMap<IdsMapEntry>)getHashBitmap();
      if (!map.containsKey(Long.valueOf(entry.getID()))) {
        map.put(Long.valueOf(entry.getID()), entry);
      }
    }
  }

  private static LongIntegerHashMap<IdsMapEntry> createResourceList(String resource, int idsStart, int idsSize)
  {
    LongIntegerHashMap<IdsMapEntry> retVal = null;
    IdsMap idsMap = IdsMapCache.get(resource);
    if (idsMap != null) {
      retVal = new LongIntegerHashMap<IdsMapEntry>();
      for (final IdsMapEntry e: idsMap.getAllValues()) {
        long id = e.getID();
        if (idsSize < 0 || (id >= idsStart && id < idsStart + idsSize)) {
          id -= idsStart;
          retVal.put(Long.valueOf(id), new IdsMapEntry(id, e.getSymbol()));
        }
      }

      // Add a fitting symbol for "0" to IDS list if needed
      if (!retVal.containsKey(0L)) {
        if (resource.equalsIgnoreCase("EA.IDS")) {
          retVal.put(0L, new IdsMapEntry(0L, "ANYONE"));
        } else {
          retVal.put(0L, new IdsMapEntry(0L, "NONE"));
        }
      }
    }
    return retVal;
  }
}
