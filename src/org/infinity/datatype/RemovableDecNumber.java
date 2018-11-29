// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

import org.infinity.resource.AddRemovable;

public final class RemovableDecNumber extends DecNumber implements AddRemovable
{
  public RemovableDecNumber(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name);
  }

//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------
}
