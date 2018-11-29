// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

public class DecNumber extends Datatype implements InlineEditable, IsNumeric
{
  private long number;
  private boolean signed;

  public DecNumber(ByteBuffer buffer, int offset, int length, String name)
  {
    this(buffer, offset, length, name, true);
  }

  public DecNumber(ByteBuffer buffer, int offset, int length, String name, boolean signed)
  {
    super(offset, length, name);
    this.number = 0L;
    this.signed = signed;
    read(buffer, offset);
  }

// --------------------- Begin Interface InlineEditable ---------------------

  @Override
  public boolean update(Object value)
  {
    try {
      number = parseNumber(value, getSize(), signed, true);
      return true;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

// --------------------- End Interface InlineEditable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    writeLong(os, number);
  }

// --------------------- End Interface Writeable ---------------------

// --------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    switch (getSize()) {
      case 1:
        if (signed) {
          number = buffer.get();
        } else {
          number = buffer.get() & 0xff;
        }
        break;
      case 2:
        if (signed) {
          number = buffer.getShort();
        } else {
          number = buffer.getShort() & 0xffff;
        }
        break;
      case 4:
        if (signed) {
          number = buffer.getInt();
        } else {
          number = buffer.getInt() & 0xffffffff;
        }
        break;
      default:
        throw new IllegalArgumentException();
    }

    return offset + getSize();
  }

// --------------------- End Interface Readable ---------------------

// --------------------- Begin Interface IsNumeric ---------------------

  @Override
  public long getLongValue()
  {
    return number;
  }

  @Override
  public int getValue()
  {
    return (int)number;
  }

// --------------------- End Interface IsNumeric ---------------------

  public void incValue(long value)
  {
    number += value;
  }

  public void setValue(long value)
  {
    number = value;
  }

  @Override
  public String toString()
  {
    return Long.toString(number);
  }

  /** Attempts to parse the specified string into a decimal or, optionally, hexadecimal number. */
  static long parseNumber(Object value, int size, boolean negativeAllowed, boolean hexAllowed) throws Exception
  {
    if (value == null) {
      throw new NullPointerException();
    }
    String s = value.toString().trim().toLowerCase(Locale.ENGLISH);
    int radix = 10;
    if (hexAllowed && s.startsWith("0x")) {
      s = s.substring(2);
      radix = 16;
    } else if (hexAllowed && s.endsWith("h")) {
      s = s.substring(0, s.length() - 1).trim();
      radix = 16;
    }
    long newNumber = Long.parseLong(s, radix);
    long discard = negativeAllowed ? 1L : 0L;
    long maxNum = (1L << ((long)size*8L - discard)) - 1L;
    long minNum = negativeAllowed ? -(maxNum+1L) : 0;
    if (newNumber > maxNum || newNumber < minNum) {
      throw new NumberFormatException("Number out of range: " + newNumber);
    }
    return newNumber;
  }
}
