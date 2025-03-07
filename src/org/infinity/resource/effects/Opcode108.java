// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.resource.StructEntry;

/**
 * Implementation of opcode 108.
 */
public class Opcode108 extends BaseOpcode {
  private static final String[] INC_TYPES_EE = { INC_TYPES[0], INC_TYPES[1], INC_TYPES[2], "Increment (party)",
      "Set (party)", "Set % of (party)" };

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    return "Reputation bonus";
  }

  public Opcode108() {
    super(108, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_VALUE));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_MODIFIER_TYPE, INC_TYPES));
    return null;
  }

  @Override
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_VALUE));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_MODIFIER_TYPE, INC_TYPES_EE));
    return null;
  }
}
