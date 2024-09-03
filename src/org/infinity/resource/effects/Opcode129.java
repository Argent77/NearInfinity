// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.resource.StructEntry;

/**
 * Implementation of opcode 129.
 */
public class Opcode129 extends BaseOpcode {
  private static final String EFFECT_HP_BONUS = "HP bonus";

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    return "Aid (non-cumulative)";
  }

  public Opcode129() {
    super(129, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_AMOUNT));
    list.add(new DecNumber(buffer, offset + 4, 4, EFFECT_HP_BONUS));
    return null;
  }
}
