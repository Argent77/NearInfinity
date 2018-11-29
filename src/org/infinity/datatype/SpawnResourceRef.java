// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.List;

import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.text.PlainTextResource;

public final class SpawnResourceRef extends ResourceRef
{
  public SpawnResourceRef(ByteBuffer h_buffer, int offset, String name)
  {
    super(h_buffer, offset, name, "CRE");
  }

  @Override
  void addExtraEntries(List<ResourceRefEntry> entries)
  {
    final ResourceEntry spawnRef = ResourceFactory.getResourceEntry("SPAWNGRP.2DA");
    if (spawnRef != null) {
      final PlainTextResource spawn = (PlainTextResource)ResourceFactory.getResource(spawnRef);
      for (String header : spawn.extract2DAHeaders()) {
        entries.add(new ResourceRefEntry(header));
      }
    }
  }
}
