// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;

public final class IwdRef extends ResourceBitmap
{
  private static final String NONE = "None";

  public IwdRef(ByteBuffer buffer, int offset, String name, String idsFile)
  {
    super(buffer, offset, 4, name, createIwdRefList(idsFile), NONE, FMT_REF_NAME);
  }

  public long getValue(String ref)
  {
    if (ref != null && !ref.isEmpty()) {
      ref = ref.toUpperCase(Locale.ENGLISH);
      List<RefEntry> list = getResourceList();
      for (final RefEntry entry: list) {
        if (entry.getResourceName().equals(ref)) {
          return entry.getValue();
        }
      }
    }
    return -1L;
  }

  public String getValueRef()
  {
    String retVal = getResourceName();
    if (retVal.isEmpty()) {
      retVal = NONE;
    }
    return retVal;
  }

  public String getValueRef(long id)
  {
    List<RefEntry> list = getResourceList();
    for (final RefEntry entry: list) {
      if (entry.getValue() == id) {
        return entry.getResourceName();
      }
    }
    return NONE;
  }

  private static List<RefEntry> createIwdRefList(String idsFile)
  {
    IdsMap idsMap = IdsMapCache.get(idsFile);

    final List<RefEntry> retVal = new ArrayList<>(idsMap.size());
    for (final IdsMapEntry e: idsMap.getAllValues()) {
      retVal.add(new RefEntry(e.getID(), e.getSymbol().toUpperCase(Locale.ENGLISH) + ".SPL"));
    }

    return retVal;
  }
}
