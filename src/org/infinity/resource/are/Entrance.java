// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.are;

import java.nio.ByteBuffer;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.util.io.StreamUtils;

public final class Entrance extends AbstractStruct implements AddRemovable {
  // ARE/Entrance-specific field labels
  public static final String ARE_ENTRANCE             = "Entrance";
  public static final String ARE_ENTRANCE_NAME        = "Name";
  public static final String ARE_ENTRANCE_LOCATION_X  = "Location: X";
  public static final String ARE_ENTRANCE_LOCATION_Y  = "Location: Y";
  public static final String ARE_ENTRANCE_ORIENTATION = "Orientation";

  Entrance() throws Exception {
    super(null, ARE_ENTRANCE, StreamUtils.getByteBuffer(104), 0);
  }

  Entrance(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, ARE_ENTRANCE + " " + number, buffer, offset);
  }

  // --------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove() {
    return true;
  }

  // --------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new TextString(buffer, offset, 32, ARE_ENTRANCE_NAME));
    addField(new DecNumber(buffer, offset + 32, 2, ARE_ENTRANCE_LOCATION_X));
    addField(new DecNumber(buffer, offset + 34, 2, ARE_ENTRANCE_LOCATION_Y));
    addField(new Bitmap(buffer, offset + 36, 4, ARE_ENTRANCE_ORIENTATION, OPTION_ORIENTATION));
    addField(new Unknown(buffer, offset + 40, 64));
    return offset + 104;
  }
}
