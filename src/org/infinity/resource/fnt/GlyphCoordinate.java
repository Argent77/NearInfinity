// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.fnt;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.resource.AbstractStruct;
import org.infinity.util.io.StreamUtils;

public class GlyphCoordinate extends AbstractStruct {
  // FNT/GlyphCoordinate-specific labels
  public static final String FNT_GC                       = "Glyph Coordinate";
  public static final String FNT_GC_VERTICAL_PLACEMENT    = "Vertical placement offset";
  public static final String FNT_GC_HORIZONTAL_PLACEMENT  = "Horizontal placement offset";
  public static final String FNT_GC_LEFT                  = "Left coordinate";
  public static final String FNT_GC_TOP                   = "Top coordinate";
  public static final String FNT_GC_WIDTH                 = "Width";
  public static final String FNT_GC_HEIGHT                = "Height";

  GlyphCoordinate() throws Exception {
    super(null, FNT_GC, StreamUtils.getByteBuffer(16), 0);
  }

  public GlyphCoordinate(AbstractStruct superStruct, ByteBuffer buffer, int offset, int sizeIndex, int glyphIndex) throws Exception {
    super(superStruct, FNT_GC + " (size " + sizeIndex + " / glyph " + glyphIndex + ")", buffer, offset);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new DecNumber(buffer, offset, 2, AbstractStruct.COMMON_UNKNOWN));
    addField(new DecNumber(buffer, offset + 2, 2, FNT_GC_VERTICAL_PLACEMENT));
    addField(new DecNumber(buffer, offset + 4, 2, FNT_GC_HORIZONTAL_PLACEMENT));
    addField(new DecNumber(buffer, offset + 6, 2, FNT_GC_LEFT));
    addField(new DecNumber(buffer, offset + 8, 2, FNT_GC_TOP));
    addField(new DecNumber(buffer, offset + 10, 2, FNT_GC_WIDTH));
    addField(new DecNumber(buffer, offset + 12, 2, FNT_GC_HEIGHT));
    return offset + 14;
  }
}
