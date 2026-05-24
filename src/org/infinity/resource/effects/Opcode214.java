// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;

/**
 * Implementation of opcode 214.
 */
public class Opcode214 extends BaseOpcode {
  private static final String EFFECT_SHOW = "Show";

  private static final String RES_TYPE = "2DA";

  private static final String[] SHOW_TYPES    = { "From 2DA", "Known spells" };
  private static final String[] SHOW_TYPES_EE = { SHOW_TYPES[0], SHOW_TYPES[1],
      "Known spells with unset spell flags bit 15 (Ignore wild surge)" };

  private static final String[] SHOW_TYPES_EEEX = { SHOW_TYPES_EE[0], SHOW_TYPES_EE[1], SHOW_TYPES_EE[2],
      "EEex: Invoke Lua" };

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    switch (Profile.getEngine()) {
      case BG1:
      case PST:
        return null;
      case IWD:
      case IWD2:
        return AbstractStruct.COMMON_UNUSED;
      default:
        return "Select spell";
    }
  }

  public Opcode214() {
    super(214, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_SHOW, SHOW_TYPES));
    return RES_TYPE;
  }

  @Override
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
    if (isEEEx()) {
      list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_SHOW, SHOW_TYPES_EEEX));
      if (buffer.getInt(offset + 4) == 3) {
        return null;
      }
    } else {
      list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_SHOW, SHOW_TYPES_EE));
    }
    return RES_TYPE;
  }

  @Override
  protected String makeEffectParamsIWD(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
    list.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
    return null;
  }

  @Override
  protected String makeEffectParamsIWD2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return makeEffectParamsIWD(parent, buffer, offset, list, isVersion1);
  }

}
