// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.IdsBitmap;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;

/**
 * Implementation of opcode 288.
 */
public class Opcode288 extends BaseOpcode {
  private static final String EFFECT_STATE = "State";

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    switch (Profile.getEngine()) {
      case BG1:
      case PST:
        return null;
      case IWD:
      case IWD2:
        return "Set spell state";
      default:
        return "Fist THAC0 bonus";
    }
  }

  public Opcode288() {
    super(288, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_VALUE));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_MODIFIER_TYPE, INC_TYPES));
    return null;
  }

  @Override
  protected String makeEffectParamsIWD(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_STATE, SPELL_STATES));
    return null;
  }

  @Override
  protected String makeEffectParamsIWD2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
    list.add(new IdsBitmap(buffer, offset + 4, 4, EFFECT_STATE, "SPLSTATE.IDS"));
    return null;
  }
}
