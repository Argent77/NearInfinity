// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
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
import org.infinity.resource.Profile;
import org.infinity.util.io.StreamUtils;

public final class Animation extends AbstractStruct implements AddRemovable
{
  public static final String ARE_ANIMATION                  = "Animation";
  public static final String ARE_ANIMATION_NAME             = "Name";
  public static final String ARE_ANIMATION_LOCATION_X       = "Location: X";
  public static final String ARE_ANIMATION_LOCATION_Y       = "Location: Y";
  public static final String ARE_ANIMATION_LOCATION_Z       = "Location: Z";
  public static final String ARE_ANIMATION_ACTIVE_AT        = "Active at";
  public static final String ARE_ANIMATION_RESREF           = "Animation";
  public static final String ARE_ANIMATION_ANIMATION_INDEX  = "Animation number";
  public static final String ARE_ANIMATION_FRAME_INDEX      = "Frame number";
  public static final String ARE_ANIMATION_APPEARANCE       = "Appearance";
  public static final String ARE_ANIMATION_TRANSLUCENCY     = "Translucency";
  public static final String ARE_ANIMATION_START_RANGE      = "Start range";
  public static final String ARE_ANIMATION_LOOP_PROBABILITY = "Loop probability";
  public static final String ARE_ANIMATION_START_DELAY      = "Start delay (frames)";
  public static final String ARE_ANIMATION_PALETTE          = "Palette";
  public static final String ARE_ANIMATION_MOVIE_WIDTH      = "Movie width";
  public static final String ARE_ANIMATION_MOVIE_HEIGHT     = "Movie height";

  public static final String[] s_flag =
    {"Not shown", "Is shown", "No shadow", "Not light source", "Partial animation",
     "Synchronized draw", "Random start","Not covered by wall", "Static animation",
     "Draw as background", "Play all frames", "Recolored by palette", "Mirror Y axis",
     "Don't remove in combat", "EE: Use WBM", "EE: Under ground", "EE: Use PVRZ"};

  Animation() throws Exception
  {
    super(null, ARE_ANIMATION, StreamUtils.getByteBuffer(76), 0);
  }

  Animation(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception
  {
    super(superStruct, ARE_ANIMATION + " " + number, buffer, offset);
  }

//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception
  {
    addField(new TextString(buffer, offset, 32, ARE_ANIMATION_NAME));
    addField(new DecNumber(buffer, offset + 32, 2, ARE_ANIMATION_LOCATION_X));
    addField(new DecNumber(buffer, offset + 34, 2, ARE_ANIMATION_LOCATION_Y));
    addField(new Flag(buffer, offset + 36, 4, ARE_ANIMATION_ACTIVE_AT, Actor.s_schedule));
    if (Profile.isEnhancedEdition()) {
      addField(new ResourceRef(buffer, offset + 40, ARE_ANIMATION_RESREF, "BAM", "WBM", "PVRZ"));
    } else {
      addField(new ResourceRef(buffer, offset + 40, ARE_ANIMATION_RESREF, "BAM"));
    }
    addField(new DecNumber(buffer, offset + 48, 2, ARE_ANIMATION_ANIMATION_INDEX));
    addField(new DecNumber(buffer, offset + 50, 2, ARE_ANIMATION_FRAME_INDEX));
    addField(new Flag(buffer, offset + 52, 4, ARE_ANIMATION_APPEARANCE, s_flag));
    addField(new DecNumber(buffer, offset + 56, 2, ARE_ANIMATION_LOCATION_Z));
    addField(new DecNumber(buffer, offset + 58, 2, ARE_ANIMATION_TRANSLUCENCY));
    addField(new DecNumber(buffer, offset + 60, 2, ARE_ANIMATION_START_RANGE));
    addField(new DecNumber(buffer, offset + 62, 1, ARE_ANIMATION_LOOP_PROBABILITY));
    addField(new DecNumber(buffer, offset + 63, 1, ARE_ANIMATION_START_DELAY));
    if (Profile.getEngine() == Profile.Engine.BG2 ||
        Profile.getEngine() == Profile.Engine.IWD2 ||
        Profile.isEnhancedEdition()) {
      addField(new ResourceRef(buffer, offset + 64, ARE_ANIMATION_PALETTE, "BMP"));
    } else {
      addField(new Unknown(buffer, offset + 64, 8));
    }
    if (Profile.isEnhancedEdition()) {
      addField(new DecNumber(buffer, offset + 72, 2, ARE_ANIMATION_MOVIE_WIDTH));
      addField(new DecNumber(buffer, offset + 74, 2, ARE_ANIMATION_MOVIE_HEIGHT));
    } else {
      addField(new Unknown(buffer, offset + 72, 4));
    }
    return offset + 76;
  }
}
