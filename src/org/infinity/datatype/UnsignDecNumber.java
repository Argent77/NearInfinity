// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

public class UnsignDecNumber extends DecNumber implements InlineEditable
{
  public UnsignDecNumber(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name, false);
  }
}
