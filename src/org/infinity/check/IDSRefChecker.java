// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.check;

import org.infinity.NearInfinity;
import org.infinity.datatype.IdsBitmap;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.ReferenceHitFrame;

public final class IDSRefChecker extends AbstractChecker
{
  /** Window with check results. */
  private final ReferenceHitFrame hitFrame;

  public IDSRefChecker()
  {
    super("IDSRef Checker", "IDSRefChecker",
          new String[]{"CRE", "EFF", "ITM", "PRO", "SPL"});
    hitFrame = new ReferenceHitFrame("Unknown IDS references", NearInfinity.getInstance());
  }

// --------------------- Begin Interface Runnable ---------------------

  @Override
  public void run()
  {
    if (runCheck(files)) {
      hitFrame.close();
    } else {
      hitFrame.setVisible(true);
    }
  }

// --------------------- End Interface Runnable ---------------------

  @Override
  protected Runnable newWorker(ResourceEntry entry)
  {
    return () -> {
      final Resource resource = ResourceFactory.getResource(entry);
      if (resource instanceof AbstractStruct) {
        search(entry, (AbstractStruct)resource);
      }
      advanceProgress();
    };
  }

  private void search(ResourceEntry entry, AbstractStruct struct)
  {
    for (StructEntry e : struct.getFlatList()) {
      if (e instanceof IdsBitmap) {
        final IdsBitmap ref = (IdsBitmap)e;
        final long value = ref.getLongValue();
        if (value != 0L && ref.getValueOf(value) == null) {
          synchronized (hitFrame) {
            hitFrame.addHit(entry, entry.getSearchString(), ref);
          }
        }
      }
    }
  }
}
