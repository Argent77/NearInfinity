// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.are;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.util.io.StreamUtils;

public final class Ambient extends AbstractStruct implements AddRemovable {
  // ARE/Ambient-specific field labels
  public static final String ARE_AMBIENT                    = "Ambient";
  public static final String ARE_AMBIENT_NAME               = "Name";
  public static final String ARE_AMBIENT_ORIGIN_X           = "Origin: X";
  public static final String ARE_AMBIENT_ORIGIN_Y           = "Origin: Y";
  public static final String ARE_AMBIENT_RADIUS             = "Radius";
  public static final String ARE_AMBIENT_PITCH_VARIATION    = "Pitch variation";
  public static final String ARE_AMBIENT_VOLUME_VARIATION   = "Volume variation";
  public static final String ARE_AMBIENT_VOLUME             = "Volume";
  public static final String ARE_AMBIENT_SOUND_FMT          = "Sound %d";
  public static final String ARE_AMBIENT_NUM_SOUNDS         = "# sounds";
  public static final String ARE_AMBIENT_INTERVAL_BASE      = "Base interval";
  public static final String ARE_AMBIENT_INTERVAL_VARIATION = "Interval variation";
  public static final String ARE_AMBIENT_ACTIVE_AT          = "Active at";
  public static final String ARE_AMBIENT_FLAGS              = "Flags";

  public static final String[] FLAGS_ARRAY = { "Disabled", "Enabled", "Looping", "Ignore radius",
      "Play in random order", "High memory ambient" };

  Ambient() throws Exception {
    super(null, ARE_AMBIENT, StreamUtils.getByteBuffer(212), 0);
  }

  Ambient(AbstractStruct superStruct, ByteBuffer buffer, int offset, int nr) throws Exception {
    super(superStruct, ARE_AMBIENT + " " + nr, buffer, offset);
  }

  // --------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove() {
    return true;
  }

  // --------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new TextString(buffer, offset, 32, ARE_AMBIENT_NAME));
    addField(new DecNumber(buffer, offset + 32, 2, ARE_AMBIENT_ORIGIN_X));
    addField(new DecNumber(buffer, offset + 34, 2, ARE_AMBIENT_ORIGIN_Y));
    addField(new DecNumber(buffer, offset + 36, 2, ARE_AMBIENT_RADIUS));
    addField(new Unknown(buffer, offset + 38, 2));
    addField(new DecNumber(buffer, offset + 40, 4, ARE_AMBIENT_PITCH_VARIATION));
    addField(new DecNumber(buffer, offset + 44, 2, ARE_AMBIENT_VOLUME_VARIATION));
    addField(new DecNumber(buffer, offset + 46, 2, ARE_AMBIENT_VOLUME));
    for (int i = 0; i < 10; i++) {
      addField(new ResourceRef(buffer, offset + 48 + (i * 8), String.format(ARE_AMBIENT_SOUND_FMT, i + 1), "WAV"));
    }
    addField(new DecNumber(buffer, offset + 128, 2, ARE_AMBIENT_NUM_SOUNDS));
    addField(new Unknown(buffer, offset + 130, 2));
    addField(new DecNumber(buffer, offset + 132, 4, ARE_AMBIENT_INTERVAL_BASE));
    addField(new DecNumber(buffer, offset + 136, 4, ARE_AMBIENT_INTERVAL_VARIATION));
    addField(new Flag(buffer, offset + 140, 4, ARE_AMBIENT_ACTIVE_AT, OPTION_SCHEDULE));
    addField(new Flag(buffer, offset + 144, 4, ARE_AMBIENT_FLAGS, FLAGS_ARRAY));
    addField(new Unknown(buffer, offset + 148, 64));
    return offset + 212;
  }
}

