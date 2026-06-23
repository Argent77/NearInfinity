// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.fnt;

import java.nio.ByteBuffer;

import org.infinity.datatype.FloatNumber;
import org.infinity.datatype.HexNumber;
import org.infinity.resource.AbstractStruct;
import org.infinity.util.io.StreamUtils;

public class Kerning extends AbstractStruct {
  // FNT/Kerning-specific labels
  public static final String FNT_KERNING            = "Kerning";
  public static final String FNT_KERNING_NEIGHBOR   = "Code point of neighboring character";
  public static final String FNT_KERNING_ADJUSTMENT = "Horizontal kerning adjustment";

  Kerning() throws Exception {
    super(null, FNT_KERNING, StreamUtils.getByteBuffer(16), 0);
  }

  public Kerning(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, FNT_KERNING + " " + number, buffer, offset);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new HexNumber(buffer, offset, 4, FNT_KERNING_NEIGHBOR));
    addField(new FloatNumber(buffer, offset + 4, 4, FNT_KERNING_ADJUSTMENT));
    return offset + 8;
  }
}
