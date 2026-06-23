// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.fnt;

import java.nio.ByteBuffer;

import org.infinity.datatype.HexNumber;
import org.infinity.util.io.StreamUtils;

public class CodePoint extends HexNumber {
  // FNT/CharacterCode-specific field labels
  public static final String FNT_CODE_POINT = "Unicode code point";

  CodePoint() {
    super(StreamUtils.getByteBuffer(4), 0, 4, FNT_CODE_POINT);
  }

  public CodePoint(ByteBuffer buffer, int offset, int number) {
    super(buffer, offset, 4, FNT_CODE_POINT + " " + number);
  }
}
