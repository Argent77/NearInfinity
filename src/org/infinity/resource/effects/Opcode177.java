// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.effects;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.datatype.Datatype;
import org.infinity.datatype.IdsTargetType;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;

/**
 * Implementation of opcode 177.
 */
public class Opcode177 extends BaseOpcode {
  private static final String RES_TYPE = "EFF";

  /** Returns the opcode name for the current game variant. */
  private static String getOpcodeName() {
    if (Profile.getEngine() == Profile.Engine.PST) {
      return null;
    } else {
      return "Use EFF file";
    }
  }

  public Opcode177() {
    super(177, getOpcodeName());
  }

  @Override
  protected String makeEffectParamsGeneric(Datatype parent, ByteBuffer buffer, int offset, List<StructEntry> list,
      boolean isVersion1) {
    final IdsTargetType param2 = new IdsTargetType(buffer, offset + 4);
    list.add(param2.createIdsValueFromType(buffer));
    list.add(param2);
    return RES_TYPE;
  }
}
