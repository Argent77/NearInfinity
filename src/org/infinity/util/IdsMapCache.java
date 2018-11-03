// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.infinity.resource.ResourceFactory;
import org.infinity.resource.bcs.ScriptInfo;
import org.infinity.resource.key.ResourceEntry;

public class IdsMapCache
{
  /** Maps upper-cased name of IDS resource to parsed resource. */
  private static final Map<String, IdsMap> CACHE = new HashMap<>();

  public static void remove(ResourceEntry entry)
  {
    if (entry != null) {
      CACHE.remove(entry.getResourceName().toUpperCase(Locale.ENGLISH));
    }
  }

  public static void clearCache()
  {
    CACHE.clear();
  }

  public static synchronized IdsMap get(String name)
  {
    IdsMap retVal = null;
    if (name != null) {
      name = name.trim().toUpperCase(Locale.ENGLISH);
      retVal = CACHE.get(name);
      if (retVal == null) {
        ResourceEntry entry = ResourceFactory.getResourceEntry(name);
        if (entry == null ) {
          if (name.equals("ATTSTYLE.IDS")) {
            entry = ResourceFactory.getResourceEntry("ATTSTYL.IDS");
          } else {
            System.err.println("Could not find " + name);
          }
        }
        if (entry != null) {
          retVal = new IdsMap(entry);
          CACHE.put(name, retVal);
        }
      }
    }
    return retVal;
  }

  /**
   * A convenience function that returns the numeric value of the specified symbol. Returns a
   * specified default value if symbol could not be resolved (which can be {@code null}).
   * <b>Special:</b> Returns {@code 0} if {@code symbol} is "ANYONE" and {@code idsRef} matches
   * one of the IDS resources used for IDS targeting.
   * @param idsRef  IDS resource name.
   * @param symbol  The symbolic value to resolve.
   * @param defValue Returns this value if symbol could not be resolved. Can be {@code null}.
   * @return The numeric value of the specified symbol. Returns {@code defValue} if not found.
   */
  public static Long getIdsValue(String idsRef, String symbol, Long defValue)
  {
    return getIdsValue(idsRef, symbol, isCaseSensitiveMatch(idsRef), defValue);
  }

  /**
   * A convenience function that returns the numeric value of the specified symbol. Returns a
   * specified default value if symbol could not be resolved (which can be {@code null}).
   * <b>Special:</b> Returns {@code 0} if {@code symbol} is "ANYONE" and {@code idsRef} matches
   * one of the IDS resources used for IDS targeting.
   * @param idsRef  IDS resource name.
   * @param symbol  The symbolic value to resolve.
   * @param exact   Whether symbol should be compared case-sensitive.
   * @param defValue Returns this value if symbol could not be resolved. Can be {@code null}.
   * @return The numeric value of the specified symbol. Returns {@code defValue} if not found.
   */
  public static Long getIdsValue(String idsRef, String symbol, boolean exact, Long defValue)
  {
    Long retVal = null;

    idsRef = getValidIdsRef(idsRef);

    if (idsRef != null && !idsRef.isEmpty()) {
      IdsMap map = get(idsRef);
      if (map != null) {
        IdsMapEntry entry = map.lookup(symbol, exact);
        if (entry != null) {
          retVal = Long.valueOf(entry.getID());
        }
      }
    }

    if (retVal == null) {
      if (symbol.equals("ANYONE")) {
        int p = idsRef.lastIndexOf('.');
        if (p >= 0) {
          idsRef = idsRef.substring(0, p);
        }
        idsRef = idsRef.toUpperCase(Locale.ENGLISH);
        String[] ids = ScriptInfo.getInfo().getObjectIdsList();
        for (final String s: ids) {
          if (s.equals(idsRef)) {
            retVal = Long.valueOf(0L);
            break;
          }
        }
      } else if (symbol.equals("FALSE")) {
        retVal = Long.valueOf(0L);
      } else if (symbol.equals("TRUE")) {
        retVal = Long.valueOf(1L);
      }
    }

    if (retVal == null) {
      retVal = defValue;
    }

    return retVal;
  }

  /**
   * A convenience function that returns the symbolic name of the specified numeric value.
   * @param idsRef IDS resource name.
   * @param value The numeric value associated with a name.
   * @return symbolic name of the numeric value, or {@code null} otherwise.
   */
  public static String getIdsSymbol(String idsRef, long value)
  {
    String retVal = null;

    idsRef = getValidIdsRef(idsRef);

    if (idsRef != null && !idsRef.isEmpty()) {
      IdsMap map = get(idsRef);
      if (map != null) {
        IdsMapEntry entry = map.get(value);
        if (entry != null) {
          retVal = entry.getSymbol();
        }
      }
    }

    return retVal;
  }

  /**
   * Makes sure that the specified IDS resource name is valid by checking filename length and
   * adding extension if needed.
   * @param idsRef The IDS resource name to check.
   * @return The fixed IDS resource name. Returns {@code null} if {@code idsRef} is {@code null}.
   */
  public static String getValidIdsRef(String idsRef)
  {
    if (idsRef != null && !idsRef.isEmpty()) {
      int p = idsRef.lastIndexOf('.');
      String name = (p < 0) ? idsRef : idsRef.substring(0, p);
      String ext = (p < 0) ? ".IDS" : idsRef.substring(p);
      if (name.length() > 8) {
        name = name.substring(0, 8);
      }
      idsRef = name + ext;
    }
    return idsRef;
  }

  public static boolean isCaseSensitiveMatch(String idsRef)
  {
    idsRef = getValidIdsRef(idsRef);
    if (idsRef != null) {
      return !idsRef.equalsIgnoreCase("TRIGGER.IDS") &&
             !idsRef.equalsIgnoreCase("ACTION.IDS") &&
             !idsRef.equalsIgnoreCase("OBJECT.IDS");
    } else {
      return true;
    }
  }

  /**
   * Returns a String array for Flag datatypes, that has been updated or overwritten with entries
   * from the specified IDS resource.
   * @param flags A static String array used as basis for Flag labels.
   * @param idsFile IDS resource to take entries from.
   * @param size Size of flags field in bytes. (Range: 1..4)
   * @param overwrite If {@code true}, then static flag label will be overwritten with entries
   *                  from the IDS resource.
   *                  If {@code false}, then entries from IDS resource will be used only for
   *                  missing or empty entries in the {@code flags} array.
   * @param prettify Indicates whether to improve readability of flag names. The operation includes:
   *                  - a capitalized first letter
   *                  - lowercased remaining letters
   *                  - underscores are replaced by space
   * @return The updated list of flag names.
   */
  public static String[] getUpdatedIdsFlags(String[] flags, String idsFile, int size, boolean overwrite, boolean prettify)
  {
    ArrayList<String> list = new ArrayList<String>(32);
    size = Math.max(1, Math.min(4, size));

    // adding static labels
    if (flags != null && flags.length > 1) {
      Collections.addAll(list, flags);
    } else {
      list.add(null); // empty flags label
    }

    // updating list with labels from IDS entries
    if (ResourceFactory.resourceExists(idsFile)) {
      IdsMap map = IdsMapCache.get(idsFile);
      if (map != null) {
        int numBits = size * 8;
        for (int i = 0; i < numBits; i++) {
          IdsMapEntry entry = map.get((long)(1 << i));
          String s = (entry != null) ? entry.getSymbol() : null;
          if (s != null && !s.isEmpty()) {
            if (prettify) {
              s = prettifyName(s);
            }
            s += ";Taken from " + idsFile;
          }
          if (i < list.size() - 1) {
            if (overwrite || list.get(i+1) == null || list.get(i+1).isEmpty()) {
              list.set(i+1, s);
            }
          } else {
            list.add(s);
          }
        }
      }
    }

    // cleaning up trailing null labels
    while (list.size() > 1 && list.get(list.size() - 1) == null) {
      list.remove(list.size() - 1);
    }

    // converting list into array
    String[] retVal = new String[list.size()];
    for (int i = 0; i < retVal.length; i++) {
      retVal[i] = list.get(i);
    }

    return retVal;
  }

  // Improves readability of given string by capitalizing first letter, lowercasing remaining characters
  // and replacing underscores by space.
  private static String prettifyName(String name)
  {
    if (name == null || name.trim().isEmpty()) {
      return name;
    }
    String retVal = name.replaceAll("_", " ").toLowerCase(Locale.ENGLISH);
    retVal = Character.toUpperCase(retVal.charAt(0)) + retVal.substring(1);
    return retVal;
  }
}
