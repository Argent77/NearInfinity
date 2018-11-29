// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.Objects;

import org.infinity.resource.StructEntry;

public final class SectionOffset extends HexNumber
{
  private final Class<? extends StructEntry> section;

  public SectionOffset(ByteBuffer buffer, int offset, String desc,
                       Class<? extends StructEntry> section)
  {
    super(buffer, offset, 4, desc);
    this.section = Objects.requireNonNull(section, "Class for SectionOffset must not be null");
  }

//--------------------- Begin Interface InlineEditable ---------------------

 @Override
 public boolean update(Object value)
 {
   // should not be modified by the user
   return false;
 }

//--------------------- End Interface InlineEditable ---------------------

  public Class<? extends StructEntry> getSection()
  {
    return section;
  }
}
