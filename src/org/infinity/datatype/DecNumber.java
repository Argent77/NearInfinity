// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;

import org.infinity.resource.AbstractStruct;
import org.infinity.util.Logger;

/**
 * Field that represents numerical value which is usually edited in a decimal mode.
 *
 * <h2>Bean property</h2> When this field is child of {@link AbstractStruct}, then changes of its internal value
 * reported as {@link PropertyChangeEvent}s of the {@link #getParent() parent} struct.
 * <ul>
 * <li>Property name: {@link #getName() name} of this field</li>
 * <li>Property type: {@code long}</li>
 * <li>Value meaning: numerical value of this field</li>
 * </ul>
 */
public class DecNumber extends Datatype implements InlineEditable, IsNumeric {
  private final boolean signed;

  private long number;

  public DecNumber(ByteBuffer buffer, int offset, int length, String name) {
    this(buffer, offset, length, name, true);
  }

  protected DecNumber(ByteBuffer buffer, int offset, int length, String name, boolean signed) {
    super(offset, length, name);
    this.signed = signed;
    read(buffer, offset);
  }

  // --------------------- Begin Interface InlineEditable ---------------------

  @Override
  public boolean update(Object value) {
    try {
      long oldVal = getLongValue();
      setValue(parseNumber(value, getSize(), signed, true));

      // notifying listeners
      if (getLongValue() != oldVal) {
        fireValueUpdated(new UpdateEvent(this, getParent()));
      }
      return true;
    } catch (Exception e) {
      Logger.error(e);
    }

    return false;
  }

  // --------------------- End Interface InlineEditable ---------------------

  // --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException {
    writeLong(os, number);
  }

  // --------------------- End Interface Writeable ---------------------

  // --------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) {
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
      case 3:
        number = buffer.getShort() & 0xffff;
        number |= (buffer.get() & 0xff) << 16;
        if (signed && (number & 0x800000) != 0) {
          number = ~0xffffffL | number;
        }
        break;
      case 4:
        if (signed) {
          number = buffer.getInt();
        } else {
          number = buffer.getInt() & 0xffffffffL;
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
  public long getLongValue() {
    return number;
  }

  @Override
  public int getValue() {
    return (int) number;
  }

  // --------------------- End Interface IsNumeric ---------------------

  public void incValue(long value) {
    setValue(number + value);
  }

  public void setValue(long newValue) {
    final long oldValue = number;
    number = newValue;
    if (oldValue != newValue) {
      firePropertyChange(oldValue, newValue);
    }
  }

  @Override
  public String toString() {
    return Long.toString(number);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(number);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DecNumber other = (DecNumber) obj;
    return number == other.number;
  }

  /** Attempts to parse the specified object into a decimal or, optionally, hexadecimal number. */
  public static long parseNumber(Object value, int size, boolean negativeAllowed, boolean hexAllowed) {
    if (value == null) {
      throw new NullPointerException();
    }

    long newNumber;
    if (value instanceof IsNumeric) {
      newNumber = ((IsNumeric) value).getLongValue();
    } else {
      String s;
      if (value instanceof IsTextual) {
        s = ((IsTextual) value).getText();
      } else {
        s = value.toString();
      }
      s = s.toLowerCase(Locale.ENGLISH);

      int radix = 10;
      if (hexAllowed && s.startsWith("0x")) {
        s = s.substring(2);
        radix = 16;
      } else if (hexAllowed && s.endsWith("h")) {
        s = s.substring(0, s.length() - 1).trim();
        radix = 16;
      }
      newNumber = Long.parseLong(s, radix);
    }

    long discard = negativeAllowed ? 1L : 0L;
    long maxNum = (1L << (size * 8L - discard)) - 1L;
    long minNum = negativeAllowed ? -(maxNum + 1L) : 0;
    if (newNumber > maxNum || newNumber < minNum) {
      throw new NumberFormatException("Number out of range: " + newNumber);
    }
    return newNumber;
  }
}
