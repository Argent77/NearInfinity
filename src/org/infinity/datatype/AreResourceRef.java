// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;

import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;

public final class AreResourceRef extends ResourceRef
{
  private final String[] legalBIFs;
  // BG1: SFXSound.BIF, CRESound.BIF
  // BG2: AMBSound.BIF 25AmbSnd.BIF
  // IDW1: SNDgen.BIF eSFXamb.BIF
  // IDW2: SNDsfx.BIF
  // PST: sound.BIF

  public AreResourceRef(ByteBuffer h_buffer, int offset, String name, AreResource are)
  {
    super(h_buffer, offset, name, "WAV");
    ResourceEntry res = ResourceFactory.getResourceEntry(((ResourceRef)are
        .getAttribute(AreResource.ARE_WED_RESOURCE)).getResourceName());
    String wedBIFF = "_dummy";
    if (res instanceof BIFFResourceEntry) {
      wedBIFF = ((BIFFResourceEntry)res).getBIFFEntry().toString();
    }
    if (Profile.getEngine() == Profile.Engine.BG1) {
      legalBIFs = new String[]{wedBIFF, "data/sfxsound.bif", "data/cresound.bif"};
    } else if (Profile.getEngine() == Profile.Engine.BG2) {
      legalBIFs = new String[]{wedBIFF, "data/ambsound.bif", "data/25ambsnd.bif", "data/sfxsound.bif"};
    } else if (Profile.getEngine() == Profile.Engine.IWD) {
      legalBIFs = new String[]{wedBIFF, "data/sndgen.bif", "data/esfxamb.bif"};
    } else if (Profile.getEngine() == Profile.Engine.IWD2) {
      legalBIFs = new String[]{wedBIFF, "data/sndsfx.bif"};
    } else if (Profile.getEngine() == Profile.Engine.PST) {
      legalBIFs = new String[]{wedBIFF, "sound.bif"};
    } else {
      legalBIFs = new String[]{wedBIFF};
    }
//    ResourceEntry entry = ResourceFactory.getInstance().getResourceEntry(getResourceName());
//    if (!isLegalEntry(entry)) {
//      System.out.println("Illegal: " + entry + " from " + entry.getActualFile());
//      System.out.println("In: " + are.getResourceEntry() + " from " + are.getResourceEntry().getActualFile());
//    }
  }

  @Override
  public boolean isLegalEntry(ResourceEntry entry)
  {
    if (entry == null)
      return false;
    if (!(entry instanceof BIFFResourceEntry))
      return true;
    if (entry.hasOverride())
      return true;
    final String bifName = ((BIFFResourceEntry)entry).getBIFFEntry().getFileName();
//    if (bifName.length() > 8 && bifName.substring(0, 9).equalsIgnoreCase("data/AREA"))
//      return true;
    for (final String legalBIF : legalBIFs) {
      if (bifName.equalsIgnoreCase(legalBIF))
        return true;
    }
    return false;
  }
}
