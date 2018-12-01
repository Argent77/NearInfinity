// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.infinity.resource.AbstractStruct;
import org.infinity.resource.EffectFactory;
import org.infinity.resource.StructEntry;

public final class EffectType extends Bitmap implements UpdateListener
{
  // EffectType-specific field labels
  public static final String EFFECT_TYPE        = "Type";
  public static final String EFFECT_TYPE_TARGET = "Target";
  public static final String EFFECT_TYPE_POWER  = "Power";

  private static final String s_target[] = {"None", "Self", "Preset target",
                                            "Party", "Everyone", "Everyone except party",
                                            "Caster group", "Target group", "Everyone except self", "Original caster"};
  private int attr_length;

  public EffectType(ByteBuffer buffer, int offset, int length)
  {
    super(buffer, offset, length, EFFECT_TYPE, EffectFactory.getFactory().getEffectNameArray());
  }

// --------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    super.updateValue(struct);
    try {
      final List<StructEntry> list = new ArrayList<>();
      readAttributes(struct.removeFromList(this, attr_length), 0, list);
      for (int i = 0; i < list.size(); i++) {
        StructEntry entry = list.get(i);
        entry.setOffset(entry.getOffset() + getOffset() + getSize());
      }
      struct.addToList(this, list);
      return true;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }

// --------------------- End Interface Editable ---------------------

// --------------------- Begin Interface UpdateListener ---------------------

  @Override
  public boolean valueUpdated(UpdateEvent event)
  {
    try {
      return EffectFactory.updateOpcode(event.getStructure());
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

// --------------------- End Interface UpdateListener ---------------------

  public int readAttributes(ByteBuffer buffer, int off, List<StructEntry> list)
  {
    attr_length = off;
    boolean isV1 = (getSize() == 2);
    if (isV1) {
      // EFF V1.0
      list.add(new Bitmap(buffer, off, 1, EFFECT_TYPE_TARGET, s_target));
      list.add(new DecNumber(buffer, off + 1, 1, EFFECT_TYPE_POWER));
      off += 2;
    }
    else {
      // EFF V2.0
      list.add(new Bitmap(buffer, off, 4, EFFECT_TYPE_TARGET, s_target));
      list.add(new DecNumber(buffer, off + 4, 4, EFFECT_TYPE_POWER));
      off += 8;
    }
    try {
      off = EffectFactory.getFactory().makeEffectStruct(this, buffer, off, list, getValue(), isV1);
    } catch (Exception e) {
      e.printStackTrace();
    }
    attr_length = off - attr_length;
    return off;
  }
}
