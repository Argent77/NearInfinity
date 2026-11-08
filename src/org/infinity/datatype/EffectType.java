// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.infinity.resource.AbstractStruct;
import org.infinity.resource.StructEntry;
import org.infinity.resource.effects.BaseOpcode;
import org.infinity.util.Logger;

public final class EffectType extends Bitmap implements UpdateListener {
  // EffectType-specific field labels
  public static final String EFFECT_TYPE        = "Type";
  public static final String EFFECT_TYPE_TARGET = "Target";
  public static final String EFFECT_TYPE_POWER  = "Power";

  public static final int TARGET_SELF = 1;

  private static final String[] TARGET_ARRAY = { "None", "Self", "Preset target", "Party", "Everyone",
      "Everyone except party", "Caster group", "Target group", "Everyone except self", "Original caster" };

  private int attrLength;

  public EffectType(ByteBuffer buffer, int offset, int length) {
    super(buffer, offset, length, EFFECT_TYPE, BaseOpcode.getEffectNames());
  }

  // --------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct) {
    super.updateValue(struct);
    try {
      final List<StructEntry> list = new ArrayList<>();
      readAttributes(struct.removeFromList(this, attrLength), 0, list);
      for (StructEntry entry : list) {
        entry.setOffset(entry.getOffset() + getOffset() + getSize());
      }
      struct.addFields(this, list);
      return true;
    } catch (IOException e) {
      Logger.error(e);
    }
    return false;
  }

  // --------------------- End Interface Editable ---------------------

  // --------------------- Begin Interface UpdateListener ---------------------

  @Override
  public boolean valueUpdated(UpdateEvent event) {
    try {
      return BaseOpcode.updateOpcode(event.getStructure());
    } catch (Exception e) {
      Logger.error(e);
    }
    return false;
  }

  // --------------------- End Interface UpdateListener ---------------------

  public int readAttributes(ByteBuffer buffer, int off, List<StructEntry> list) {
    attrLength = off;
    boolean isV1 = (getSize() == 2);
    if (isV1) {
      // EFF V1.0
      list.add(new Bitmap(buffer, off, 1, EFFECT_TYPE_TARGET, TARGET_ARRAY));
      list.add(new DecNumber(buffer, off + 1, 1, EFFECT_TYPE_POWER));
      off += 2;
    } else {
      // EFF V2.0
      list.add(new Bitmap(buffer, off, 4, EFFECT_TYPE_TARGET, TARGET_ARRAY));
      list.add(new DecNumber(buffer, off + 4, 4, EFFECT_TYPE_POWER));
      off += 8;
    }
    try {
      off = BaseOpcode.makeEffectStruct(getValue(), this, buffer, off, list, isV1);
    } catch (Exception e) {
      Logger.error(e);
    }
    attrLength = off - attrLength;
    return off;
  }
}
