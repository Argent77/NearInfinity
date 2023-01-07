// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2022 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.SecTypeBitmap;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;

/**
 * Implemention of opcode 205.
 */
public class Opcode205 extends BaseOpcode {
  private static final String EFFECT_SPELL_TYPE   = "Spell type";
  private static final String EFFECT_SCROLL_SPEED = "Scroll speed";

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    switch (Profile.getEngine()) {
      case BG1:
      case IWD:
      case IWD2:
        return null;
      case PST:
        return "Move view to target";
      default:
        return "Protection from spell type";
    }
  }

  public Opcode205() {
    super(205, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, AbstractStruct.COMMON_UNUSED));
    list.add(new SecTypeBitmap(buffer, offset + 4, 4, EFFECT_SPELL_TYPE));
    return null;
  }

  @Override
  protected String makeEffectParamsBG1(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return super.makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  @Override
  protected String makeEffectParamsIWD(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return super.makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  @Override
  protected String makeEffectParamsIWD2(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    return super.makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
  }

  @Override
  protected String makeEffectParamsPST(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_SCROLL_SPEED));
    list.add(new DecNumber(buffer, offset + 4, 4, AbstractStruct.COMMON_UNUSED));
    return null;
  }
}