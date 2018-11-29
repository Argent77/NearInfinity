// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.infinity.util.IdsMapEntry;

/**
 * Specialized IdsBitmap type for properly handling KIT.IDS in BG and BG2.
 */
public class KitIdsBitmap extends IdsBitmap
{
  public KitIdsBitmap(ByteBuffer buffer, int offset, String name)
  {
    super(buffer, offset, 4, name, "KIT.IDS");
    // adding "No Kit" value if needed
    addIdsMapEntry(new IdsMapEntry(0L, "NO_KIT"));

    // fixing word order of kit id value
    setValue(swapWords(getValue()));
  }

//--------------------- Begin Interface Writeable ---------------------

 @Override
 public void write(OutputStream os) throws IOException
 {
   writeLong(os, swapWords(getValue()));
 }

//--------------------- End Interface Writeable ---------------------

  /** Swaps position of the two lower words. */
  private static long swapWords(long value)
  {
    return ((value >>> 16) & 0xffffL) | ((value & 0xffffL) << 16);
  }
}
