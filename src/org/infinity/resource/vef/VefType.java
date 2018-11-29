// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.vef;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.TextString;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.StructEntry;

public final class VefType extends Bitmap
{
  // VEF/VefType-specific field labels
  public static final String VEF_TYPE           = "Resource type";
  public static final String VEF_TYPE_RESOURCE  = "Resource";

  public static final String[] s_restype = {"WAV", "VVC/BAM", "VEF/VVC/BAM"};
  private static int buf_size = 8;

  public VefType(ByteBuffer buffer, int offset, int length)
  {
    super(buffer, offset, length, VEF_TYPE, s_restype);
  }

  // --------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    super.updateValue(struct);
    try {
      List<StructEntry> list = new ArrayList<StructEntry>();
      readAttributes(struct.removeFromList(this, buf_size), 0, list);
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

  public int readAttributes(ByteBuffer buffer, int off, List<StructEntry> list)
  {
    switch (getValue()) {
    case 0:
      list.add(new ResourceRef(buffer, off, 8, VEF_TYPE_RESOURCE, "WAV"));
      break;
    case 1:
      list.add(new ResourceRef(buffer, off, 8, VEF_TYPE_RESOURCE, new String[]{"VVC", "BAM"}));
      break;
    case 2:
      list.add(new ResourceRef(buffer, off, 8, VEF_TYPE_RESOURCE, new String[]{"VEF", "VVC", "BAM"}));
      break;
    default:
      list.add(new TextString(buffer, off, 8, VEF_TYPE_RESOURCE));
      break;
    }
    return off + buf_size;
  }
}
