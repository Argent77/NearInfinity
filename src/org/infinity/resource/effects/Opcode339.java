// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.ProRef;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;

/**
 * Implementation of opcode 339.
 */
public class Opcode339 extends BaseOpcode {
  private static final String EFFECT_PROJECTILE = "Projectile";
  private static final String EFFECT_RANGE      = "Range";

  private static final String[] MODES = { "Set value", "AND value", "OR value", "XOR value", "AND NOT value" };

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    switch (Profile.getEngine()) {
      case EE:
        return "Alter visual animation effect";
      default:
        return null;
    }
  }

  public Opcode339() {
    super(339, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new Bitmap(buffer, offset, 2, EFFECT_MODIFIER_TYPE, MODES));
    list.add(new DecNumber(buffer, offset + 2, 2, EFFECT_VALUE));
    list.add(new ProRef(buffer, offset + 4, 4, EFFECT_PROJECTILE));
    return RES_TYPE_STRING;
  }

  @Override
  protected int makeEffectSpecial(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      String resType, int param1, int param2) {
    if (Profile.isEnhancedEdition()) {
      list.add(new DecNumber(buffer, offset, 4, EFFECT_RANGE));
      return offset + 4;
    } else {
      return super.makeEffectSpecial(parent, buffer, offset, list, resType, param1, param2);
    }
  }
}
