// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

import org.infinity.resource.AbstractStruct;
import org.infinity.resource.StructEntry;

/**
 * Field that represents binary data in binary format in their editor.
 *
 * <h2>Bean property</h2>
 * When this field is child of {@link AbstractStruct}, then changes of its internal
 * value reported as {@link PropertyChangeEvent}s of the {@link #getParent() parent}
 * struct.
 * <ul>
 * <li>Property name: {@link #getName() name} of this field</li>
 * <li>Property type: {@code byte[]}</li>
 * <li>Value meaning: raw bytes of this field</li>
 * </ul>
 */
public final class UnknownBinary extends Unknown
{
  public UnknownBinary(ByteBuffer buffer, int offset, int length, String name)
  {
    this(null, buffer, offset, length, name);
  }

  public UnknownBinary(StructEntry parent, ByteBuffer buffer, int offset, int length, String name)
  {
    super(parent, buffer, offset, length, name);
  }

// --------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    final byte[] newData = calcValue(8, 2);
    if (newData == null) {
      return false;
    }
    setValue(newData);
    return true;
  }

// --------------------- End Interface Editable ---------------------

  @Override
  public String toString()
  {
    if (buffer.limit() > 0) {
      final StringBuilder sb = new StringBuilder(9 * buffer.limit() + 1);
      buffer.position(0);
      while (buffer.remaining() > 0) {
        int v = buffer.get() & 0xff;
        String text = Integer.toBinaryString(v);
        for (int j = 0, count = 8 - text.length(); j < count; j++) {
          sb.append('0');
        }
        sb.append(text).append(' ');
      }
      sb.append('b');
      return sb.toString();
    } else
      return "";
  }
}
