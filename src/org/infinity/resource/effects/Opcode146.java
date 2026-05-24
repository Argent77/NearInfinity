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
 * Implementation of opcode 146.
 */
public class Opcode146 extends BaseOpcode {
  private static final String EFFECT_CAST_AT_LEVEL = "Cast at level";

  private static final String RES_TYPE = "SPL";

  private static final String[] CASTING_MODE    = { "Cast normally", "Cast instantly (caster level)" };
  private static final String[] CASTING_MODE_EE = { CASTING_MODE[0], CASTING_MODE[1],
      "Cast instantly (specified level)" };

  private static final String EFFECT_SPELLNODEC_MODE    = "EEex: SpellNoDec() mode?";

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    return "Cast spell";
  }

  public Opcode146() {
    super(146, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_CAST_AT_LEVEL));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_MODE, CASTING_MODE));
    return RES_TYPE;
  }

  @Override
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new DecNumber(buffer, offset, 4, EFFECT_CAST_AT_LEVEL));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_MODE, CASTING_MODE_EE));
    return RES_TYPE;
  }

  @Override
  protected int makeEffectSpecial(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      String resType, int param1, int param2) {
    if (Profile.isEnhancedEdition() && isEEEx() && param2 == 0) {
      list.add(new Bitmap(buffer, offset, 4, EFFECT_SPELLNODEC_MODE, AbstractStruct.OPTION_NOYES));
      return offset + 4;
    } else {
      return super.makeEffectSpecial(parent, buffer, offset, list, resType, param1, param2);
    }
  }
}
