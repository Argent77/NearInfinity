// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.infinity.util.io.StreamUtils;


public class FloatNumber extends Datatype implements InlineEditable
{
  private double value;

  public FloatNumber(ByteBuffer buffer, int offset, int length, String name)
  {
    super(offset, length, name);
    read(buffer, offset);
  }

//--------------------- Begin Interface InlineEditable ---------------------

 @Override
 public boolean update(Object value)
 {
   try {
     double newValue = Double.parseDouble(value.toString());
     if (getSize() == 4) {
       newValue = Double.valueOf(newValue).floatValue();
     }
     this.value = newValue;
     return true;
   } catch (NumberFormatException e) {
     e.printStackTrace();
   }
   return false;
 }

//--------------------- End Interface InlineEditable ---------------------

//--------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    StreamUtils.writeBytes(os, toByteBuffer(value, getSize()));
  }

//--------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    switch (getSize()) {
      case 4:
      case 8:
        value = toFloatNumber(buffer, offset, getSize());
        break;
      default:
        throw new IllegalArgumentException();
    }

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

  @Override
  public String toString()
  {
    return Double.toString(value);
  }

  public double getValue()
  {
    return value;
  }


  // Converts byte array of specified length into a double value
  private static double toFloatNumber(ByteBuffer buffer, int offset, int length)
  {
    if (length == 4 || length == 8) {
      buffer.position(offset);
      switch (length) {
        case 4:
          return buffer.getFloat();
        case 8:
          return buffer.getDouble();
      }
    }
    return 0.0;
  }

  // Converts double value into byte array of specified length
  private static ByteBuffer toByteBuffer(double value, int length)
  {
    if (length == 4 || length == 8) {
      ByteBuffer buffer = StreamUtils.getByteBuffer(length);
      switch (length) {
        case 4:
          buffer.putFloat((float)value);
          break;
        case 8:
          buffer.putDouble(value);
          break;
      }
      buffer.position(0);
      return buffer;
    }
    return null;
  }
}
