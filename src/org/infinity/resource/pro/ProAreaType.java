// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.pro;

import java.nio.ByteBuffer;

import org.infinity.datatype.ColorValue;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.HashBitmap;
import org.infinity.datatype.IdsBitmap;
import org.infinity.datatype.ProRef;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.UnsignDecNumber;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.Profile;
import org.infinity.util.LongIntegerHashMap;
import org.infinity.util.io.StreamUtils;

public final class ProAreaType extends AbstractStruct implements AddRemovable
{
  // PRO/Area-specific field labels
  public static final String PRO_AREA                               = "Area effect info";
  public static final String PRO_AREA_FLAGS                         = "Area flags";
  public static final String PRO_AREA_RAY_COUNT                     = "Ray count";
  public static final String PRO_AREA_TRAP_SIZE                     = "Trap size";
  public static final String PRO_AREA_EXPLOSION_SIZE                = "Explosion size";
  public static final String PRO_AREA_EXPLOSION_SOUND               = "Explosion sound";
  public static final String PRO_AREA_EXPLOSION_FREQUENCY           = "Explosion frequency (frames)";
  public static final String PRO_AREA_FRAGMENT_ANIMATION            = "Fragment animation";
  public static final String PRO_AREA_SECONDARY_PROJECTILE          = "Secondary projectile";
  public static final String PRO_AREA_NUM_REPETITIONS               = "# repetitions";
  public static final String PRO_AREA_EXPLOSION_EFFECT              = "Explosion effect";
  public static final String PRO_AREA_EXPLOSION_COLOR               = "Explosion color";
  public static final String PRO_AREA_EXPLOSION_PROJECTILE          = "Explosion projectile";
  public static final String PRO_AREA_EXPLOSION_ANIMATION           = "Explosion animation";
  public static final String PRO_AREA_CONE_WIDTH                    = "Cone width";
  public static final String PRO_AREA_SPREAD_ANIMATION              = "Spread animation";
  public static final String PRO_AREA_RING_ANIMATION                = "Ring animation";
  public static final String PRO_AREA_SOUND                         = "Area sound";
  public static final String PRO_AREA_EX_FLAGS                      = "Extended flags";
  public static final String PRO_AREA_DICE_COUNT                    = "# dice for multiple targets";
  public static final String PRO_AREA_DICE_SIZE                     = "Dice size for multiple targets";
  public static final String PRO_AREA_ANIMATION_GRANULARITY         = "Animation granularity";
  public static final String PRO_AREA_ANIMATION_GRANULARITY_DIVIDER = "Animation granularity divider";

  public static final LongIntegerHashMap<String> m_proj = new LongIntegerHashMap<String>();
  public static final String[] s_areaflags = {"Trap not visible", "Trap visible", "Triggered by inanimates",
                                              "Triggered by condition", "Delayed trigger", "Secondary projectile",
                                              "Fragments", "Affect only enemies", "Affect only allies*;Only in combination with \"Affect only enemies (6)\"",
                                              "Mage-level duration", "Cleric-level duration", "Draw animation",
                                              "Cone-shaped", "Ignore LOS", "Delayed explosion",
                                              "Skip first condition", "Single target"};
  public static final String[] s_areaflagsEx = {
    "No flags set", "Paletted ring", "Random speed", "Start scattered", "Paletted center",
    "Repeat scattering", "Paletted animation", "", "", "", "Oriented fireball puffs",
    "Use hit dice lookup", "", "", "Blend are/ring anim", "Glow area/ring anim", "Hit point limit",
  };

  static {
    m_proj.put(0L, "Fireball");
    m_proj.put(1L, "Stinking cloud");
    m_proj.put(2L, "Cloudkill");
    m_proj.put(3L, "Ice storm");
    m_proj.put(4L, "Grease");
    m_proj.put(5L, "Web");
    m_proj.put(6L, "Meteor");
    m_proj.put(7L, "Horrid wilting");
    m_proj.put(8L, "Teleport field");
    m_proj.put(9L, "Entangle");
    m_proj.put(10L, "Color spray");
    m_proj.put(11L, "Cone of cold");
    m_proj.put(12L, "Holy smite");
    m_proj.put(13L, "Unholy blight");
    m_proj.put(14L, "Prismatic spray");
    m_proj.put(15L, "Red dragon blast");
    m_proj.put(16L, "Storm of vengeance");
    m_proj.put(17L, "Purple fireball");
    m_proj.put(18L, "Green dragon blast");
    m_proj.put(254L, "Custom");
    m_proj.put(255L, "None");
  }


  public ProAreaType() throws Exception
  {
    super(null, PRO_AREA, StreamUtils.getByteBuffer(256), 0);
    setOffset(512);
  }

  public ProAreaType(AbstractStruct superStruct, ByteBuffer buffer, int offset) throws Exception
  {
    super(superStruct, PRO_AREA, buffer, offset);
  }

  //<editor-fold defaultstate="collapsed" desc="AddRemovable">
  @Override
  public boolean canRemove()
  {
    return false;   // can not be removed manually
  }
  //</editor-fold>

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception
  {
    addField(new Flag(buffer, offset, 2, PRO_AREA_FLAGS, s_areaflags));
    addField(new DecNumber(buffer, offset + 2, 2, PRO_AREA_RAY_COUNT));
    addField(new DecNumber(buffer, offset + 4, 2, PRO_AREA_TRAP_SIZE));
    addField(new DecNumber(buffer, offset + 6, 2, PRO_AREA_EXPLOSION_SIZE));
    addField(new ResourceRef(buffer, offset + 8, PRO_AREA_EXPLOSION_SOUND, "WAV"));
    addField(new DecNumber(buffer, offset + 16, 2, PRO_AREA_EXPLOSION_FREQUENCY));
    addField(new IdsBitmap(buffer, offset + 18, 2, PRO_AREA_FRAGMENT_ANIMATION, "ANIMATE.IDS"));
    addField(new ProRef(buffer, offset + 20, PRO_AREA_SECONDARY_PROJECTILE, false));
    addField(new DecNumber(buffer, offset + 22, 1, PRO_AREA_NUM_REPETITIONS));
    addField(new HashBitmap(buffer, offset + 23, 1, PRO_AREA_EXPLOSION_EFFECT, m_proj));
    addField(new ColorValue(buffer, offset + 24, 1, PRO_AREA_EXPLOSION_COLOR));
    addField(new Unknown(buffer, offset + 25, 1, COMMON_UNUSED));
    addField(new ProRef(buffer, offset + 26, PRO_AREA_EXPLOSION_PROJECTILE));
    addField(new ResourceRef(buffer, offset + 28, PRO_AREA_EXPLOSION_ANIMATION, "VEF", "VVC", "BAM"));
    addField(new DecNumber(buffer, offset + 36, 2, PRO_AREA_CONE_WIDTH));
    if (Profile.isEnhancedEdition()) {
      addField(new Unknown(buffer, offset + 38, 2));
      addField(new ResourceRef(buffer, offset + 40, PRO_AREA_SPREAD_ANIMATION, "BAM"));
      addField(new ResourceRef(buffer, offset + 48, PRO_AREA_RING_ANIMATION, "BAM"));
      addField(new ResourceRef(buffer, offset + 56, PRO_AREA_SOUND, "WAV"));
      addField(new Flag(buffer, offset + 64, 4, PRO_AREA_EX_FLAGS, s_areaflagsEx));
      addField(new UnsignDecNumber(buffer, offset + 68, 2, PRO_AREA_DICE_COUNT));
      addField(new UnsignDecNumber(buffer, offset + 70, 2, PRO_AREA_DICE_SIZE));
      addField(new DecNumber(buffer, offset + 72, 2, PRO_AREA_ANIMATION_GRANULARITY));
      addField(new DecNumber(buffer, offset + 74, 2, PRO_AREA_ANIMATION_GRANULARITY_DIVIDER));
      addField(new Unknown(buffer, offset + 76, 180));
    } else {
      addField(new Unknown(buffer, offset + 38, 218));
    }

    return offset + 256;
  }
}
