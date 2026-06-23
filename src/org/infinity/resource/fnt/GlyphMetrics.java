// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.fnt;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.FloatNumber;
import org.infinity.resource.AbstractStruct;
import org.infinity.util.io.StreamUtils;

public class GlyphMetrics extends AbstractStruct {
  // FNT/GlyphMetrics-specific labels
  public static final String FNT_GM                   = "Glyph Metrics";
  public static final String FNT_GM_LEFT_SIDE_BEARING = "Left side bearing";
  public static final String FNT_GM_ADVANCE_WIDTH     = "Advance width";
  public static final String FNT_GM_TOP_SIDE_BEARING  = "Top side bearing";

  GlyphMetrics() throws Exception {
    super(null, FNT_GM, StreamUtils.getByteBuffer(16), 0);
  }

  public GlyphMetrics(AbstractStruct superStruct, ByteBuffer buffer, int offset, int sizeIndex, int glyphIndex) throws Exception {
    super(superStruct, FNT_GM + " (size " + sizeIndex + " / glyph " + glyphIndex + ")", buffer, offset);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new FloatNumber(buffer, offset, 4, FNT_GM_LEFT_SIDE_BEARING));
    addField(new FloatNumber(buffer, offset + 4, 4, FNT_GM_ADVANCE_WIDTH));
    addField(new FloatNumber(buffer, offset + 8, 4, FNT_GM_TOP_SIDE_BEARING));
    addField(new DecNumber(buffer, offset + 12, 4, AbstractStruct.COMMON_UNKNOWN));
    return offset + 16;
  }
}
