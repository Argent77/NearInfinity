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
 * Implementation of opcode 409.
 */
public class Opcode409 extends BaseOpcode {
  private static final String EFFECT_AFFECT = "Affect";

  private static final String[] AFFECTED_TYPES_IWD2 = { "Allies", "Allies and same alignment" };

  private static final String EFFECT_ENABLED_STRING    = "EEex: Enable Action Listener?";

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    switch (Profile.getEngine()) {
      case IWD2:
        return "Righteous wrath of the faithful";
      case EE:
        if (isEEEx()) {
          return "EEex: Enable Action Listener";
        }
      default:
        return null;
    }
  }

  public Opcode409() {
    super(409, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsIWD2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_AFFECT, AFFECTED_TYPES_IWD2));
    return null;
  }

  @Override
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    if (isEEEx()) {
      list.add(new Bitmap(buffer, offset, 4, EFFECT_ENABLED_STRING, AbstractStruct.OPTION_NOYES));
      list.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
      return null;
    } else {
      return super.makeEffectParamsEE(parent, buffer, offset, list, isVersion1);
    }
  }
}
