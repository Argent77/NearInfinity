// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class IntegerHashMap<V> extends HashMap<Integer, V>
{
  public IntegerHashMap()
  {
    super();
  }

  public IntegerHashMap(int initialCapacity)
  {
    super(initialCapacity);
  }

  public IntegerHashMap(int initialCapacity, float loadFactor)
  {
    super(initialCapacity, loadFactor);
  }

  public IntegerHashMap(Map<Integer, ? extends V> m)
  {
    super(m);
  }

  @Override
  public String toString()
  {
    final StringBuilder buf = new StringBuilder();
    buf.append('{');
    Set<Map.Entry<Integer, V>> set = entrySet();
    Iterator<Map.Entry<Integer, V>> i = set.iterator();
    boolean hasNext = i.hasNext();
    while (hasNext) {
      Map.Entry<Integer, V> e = i.next();
      int key = e.getKey();
      V value = e.getValue();
      buf.append(key);
      buf.append('=');
      if (value == this)
        buf.append("(this Map)");
      else
        buf.append(value);
      hasNext = i.hasNext();
      if (hasNext)
        buf.append(", ");
    }
    buf.append('}');
    return buf.toString();
  }
}
