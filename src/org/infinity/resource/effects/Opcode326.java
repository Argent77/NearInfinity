// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.SpellProtType;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;

/**
 * Implementation of opcode 326.
 */
public class Opcode326 extends BaseOpcode {
  private static final String RES_TYPE = "SPL";

  private static final String EFFECT_FLIP_MODE    = "EEex: Flip what SPLPROT.2DA considers the \"source\" and \"target\" sprites?";

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    switch (Profile.getEngine()) {
      case EE:
        return "Apply effects list";
      default:
        return null;
    }
  }

  public Opcode326() {
    super(326, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    final SpellProtType param2 = new SpellProtType(buffer, offset + 4, 4);
    list.add(param2.createCreatureValueFromType(buffer, offset));
    list.add(param2);
    return RES_TYPE;
  }

  @Override
  protected int makeEffectSpecial(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      String resType, int param1, int param2) {
    if (Profile.isEnhancedEdition() && isEEEx()) {
      list.add(new Bitmap(buffer, offset, 4, EFFECT_FLIP_MODE, AbstractStruct.OPTION_NOYES));
      return offset + 4;
    } else {
      return super.makeEffectSpecial(parent, buffer, offset, list, resType, param1, param2);
    }
  }
}
