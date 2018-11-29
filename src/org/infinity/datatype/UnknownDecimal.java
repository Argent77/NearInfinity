// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

import org.infinity.resource.AbstractStruct;

public final class UnknownDecimal extends Unknown
{
  public UnknownDecimal(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name);
  }

// --------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    String value = textArea.getText().trim();
    value = value.replaceAll("\r?\n", " ") + ' ';
    byte newdata[] = new byte[buffer.limit()];
    int counter = 0;
    try {
      int index = value.indexOf((int)' ');
      while (counter < newdata.length && index != -1) {
        int i = Integer.parseInt(value.substring(0, index));
        if (i > 255) {
          return false;
        }
        newdata[counter] = (byte)i;
        counter++;
        value = value.substring(index + 1).trim() + ' ';
        index = value.indexOf((int)' ');
      }
      if (counter == newdata.length) {
        buffer.position(0);
        buffer.put(newdata);
        return true;
      }
    } catch (NumberFormatException e) {
      e.printStackTrace();
    }
    return false;
  }

// --------------------- End Interface Editable ---------------------

  @Override
  public String toString()
  {
    if (buffer.limit() > 0) {
      final StringBuilder sb = new StringBuilder(4 * buffer.limit() + 1);
      buffer.position(0);
      while (buffer.remaining() > 0) {
        int v = buffer.get() & 0xff;
        String text = Integer.toString(v);
        for (int j = 0, count = 3 - text.length(); j < count; j++) {
          sb.append('0');
        }
        sb.append(text).append(' ');
      }
      sb.append('d');
      return sb.toString();
    } else
      return "";
  }
}
