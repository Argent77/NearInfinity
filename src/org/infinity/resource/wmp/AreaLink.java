// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wmp;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.WmpLinkBitmap;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.util.io.StreamUtils;

public abstract class AreaLink extends AbstractStruct implements AddRemovable {
  // WMP/AreaLink-specific field labels
  public static final String WMP_LINK_TARGET_AREA                   = "Target area";
  public static final String WMP_LINK_TARGET_ENTRANCE               = "Target entrance";
  public static final String WMP_LINK_DISTANCE_SCALE                = "Distance scale";
  public static final String WMP_LINK_DEFAULT_ENTRANCE              = "Default entrance";
  public static final String WMP_LINK_RANDOM_ENCOUNTER_AREA_FMT     = "Random encounter area %d";
  public static final String WMP_LINK_RANDOM_ENCOUNTER_PROBABILITY  = "Random encounter probability";

  public static final String[] ENTRANCE_ARRAY = { "No default set", "North", "East", "South", "West" };

  public AreaLink(String name) throws Exception {
    super(null, name, StreamUtils.getByteBuffer(216), 0);
  }

  public AreaLink(AbstractStruct superStruct, ByteBuffer buffer, int offset, String name) throws Exception {
    super(superStruct, name, buffer, offset);
  }

  // --------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove() {
    return true;
  }

  // --------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
//    addField(new DecNumber(buffer, offset, 4, WMP_LINK_TARGET_AREA));
    addField(new WmpLinkBitmap(buffer, offset, 4, WMP_LINK_TARGET_AREA));
    addField(new TextString(buffer, offset + 4, 32, WMP_LINK_TARGET_ENTRANCE));
    addField(new DecNumber(buffer, offset + 36, 4, WMP_LINK_DISTANCE_SCALE));
    addField(new Flag(buffer, offset + 40, 4, WMP_LINK_DEFAULT_ENTRANCE, ENTRANCE_ARRAY));
    for (int i = 0; i < 5; i++) {
      addField(new ResourceRef(buffer, offset + 44 + (i * 8), String.format(WMP_LINK_RANDOM_ENCOUNTER_AREA_FMT, i + 1),
          "ARE"));
    }
    addField(new DecNumber(buffer, offset + 84, 4, WMP_LINK_RANDOM_ENCOUNTER_PROBABILITY));
    addField(new Unknown(buffer, offset + 88, 128));
    return offset + 216;
  }
}
