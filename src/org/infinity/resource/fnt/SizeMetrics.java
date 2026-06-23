// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.fnt;

import java.nio.ByteBuffer;

import org.infinity.datatype.FloatNumber;
import org.infinity.resource.AbstractStruct;
import org.infinity.util.io.StreamUtils;

public class SizeMetrics extends AbstractStruct {
  // FNT/SizeMetrics-specific labels
  public static final String FNT_SM             = "Size Metrics";
  public static final String FNT_SM_POINT_SIZE  = "Point size (em)";
  public static final String FNT_SM_LINE_HEIGHT = "Line height";
  public static final String FNT_SM_ASCENT      = "Baseline (ascent)";
  public static final String FNT_SM_DESCENT     = "Descent";

  SizeMetrics() throws Exception {
    super(null, FNT_SM, StreamUtils.getByteBuffer(16), 0);
  }

  public SizeMetrics(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, FNT_SM + " " + number, buffer, offset);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new FloatNumber(buffer, offset, 4, FNT_SM_POINT_SIZE));
    addField(new FloatNumber(buffer, offset + 4, 4, FNT_SM_LINE_HEIGHT));
    addField(new FloatNumber(buffer, offset + 8, 4, FNT_SM_ASCENT));
    addField(new FloatNumber(buffer, offset + 12, 4, FNT_SM_DESCENT));
    return offset + 16;
  }
}
