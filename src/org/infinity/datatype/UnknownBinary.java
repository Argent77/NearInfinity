// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

import org.infinity.resource.AbstractStruct;

public final class UnknownBinary extends Unknown
{
  public UnknownBinary(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name);
  }

// --------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    String value = textArea.getText().trim();
    value = value.replaceAll("\r?\n", " ");
    int index = value.indexOf((int)' ');
    while (index != -1) {
      value = value.substring(0, index) + value.substring(index + 1);
      index = value.indexOf((int)' ');
    }
    if (value.length() != 8 * buffer.limit()) {
      return false;
    }
    byte newdata[] = new byte[buffer.limit()];
    for (int i = 0; i < newdata.length; i++) {
      String bytechars = value.substring(8 * i, 8 * i + 8);
      try {
        newdata[i] = (byte)Integer.parseInt(bytechars, 2);
      } catch (NumberFormatException e) {
        return false;
      }
    }
    buffer.position(0);
    buffer.put(newdata);
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
