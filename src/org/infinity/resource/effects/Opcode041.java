// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2022 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;

/**
 * Implemention of opcode 41.
 */
public class Opcode041 extends BaseOpcode {
  private static final String EFFECT_PARTICLE_EFFECT = "Particle effect";

  private static final String RES_TYPE = "BAM";

  private static final String[] PARTICLE_TYPES = { "", "Explosion", "Swirl", "Shower" };

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    return "Sparkle";
  }

  public Opcode041() {
    super(41, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    list.add(new Bitmap(buffer, offset, 4, EFFECT_COLOR, SPARKLE_COLORS));
    list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_PARTICLE_EFFECT, PARTICLE_TYPES));
    return null;
  }

  @Override
  protected String makeEffectParamsEE(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    if (Profile.getGame() == Profile.Game.PSTEE) {
      list.add(new DecNumber(buffer, offset, 4, EFFECT_AMOUNT));
      list.add(new Bitmap(buffer, offset + 4, 4, EFFECT_PARTICLE_EFFECT, PARTICLE_TYPES));
      return RES_TYPE;
    } else {
      return makeEffectParamsGeneric(parent, buffer, offset, list, isVersion1);
    }
  }
}
