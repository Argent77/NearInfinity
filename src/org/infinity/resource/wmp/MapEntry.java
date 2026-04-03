// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wmp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.Unknown;
import org.infinity.gui.StructViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;
import org.infinity.resource.wmp.viewer.ViewerMap;
import org.tinylog.Logger;

public class MapEntry extends AbstractStruct implements HasViewerTabs, HasChildStructs {
  // WMP/MapEntry-specific field labels
  public static final String WMP_MAP                    = "Map";
  public static final String WMP_MAP_RESREF             = "Map";
  public static final String WMP_MAP_WIDTH              = "Width";
  public static final String WMP_MAP_HEIGHT             = "Height";
  public static final String WMP_MAP_ID                 = "Map ID";
  public static final String WMP_MAP_NAME               = "Name";
  public static final String WMP_MAP_CENTER_X           = "Center location: X";
  public static final String WMP_MAP_CENTER_Y           = "Center location: Y";
  public static final String WMP_MAP_NUM_AREAS          = "# areas";
  public static final String WMP_MAP_OFFSET_AREAS       = "Areas offset";
  public static final String WMP_MAP_OFFSET_AREA_LINKS  = "Area links offset";
  public static final String WMP_MAP_NUM_AREA_LINKS     = "# area links";
  public static final String WMP_MAP_ICONS              = "Map icons";

  private static final String[] FLAGS_ARRAY = { "No flags set", "Colored icon", "Ignore palette" };

  private List<AreaEntry> areaCache;

  public MapEntry(AbstractStruct superStruct, ByteBuffer buffer, int offset, int nr) throws Exception {
    super(superStruct, WMP_MAP + " " + nr, buffer, offset);
  }

  // --------------------- Begin Interface HasChildStructs ---------------------

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    return new AddRemovable[] { new AreaEntry() };
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception {
    return entry;
  }

  // --------------------- End Interface HasChildStructs ---------------------

  // --------------------- Begin Interface HasViewerTabs ---------------------

  @Override
  public int getViewerTabCount() {
    return 1;
  }

  @Override
  public String getViewerTabName(int index) {
    return StructViewer.TAB_VIEW;
  }

  @Override
  public JComponent getViewerTab(int index) {
    try {
      return new ViewerMap(this);
    } catch (Exception e) {
      Logger.error(e);
    }
    return null;
  }

  @Override
  public boolean viewerTabAddedBefore(int index) {
    return true;
  }

  // --------------------- End Interface HasViewerTabs ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new ResourceRef(buffer, offset, WMP_MAP_RESREF, "MOS"));
    addField(new DecNumber(buffer, offset + 8, 4, WMP_MAP_WIDTH));
    addField(new DecNumber(buffer, offset + 12, 4, WMP_MAP_HEIGHT));
    addField(new DecNumber(buffer, offset + 16, 4, WMP_MAP_ID));
    addField(new StringRef(buffer, offset + 20, WMP_MAP_NAME));
    addField(new DecNumber(buffer, offset + 24, 4, WMP_MAP_CENTER_X));
    addField(new DecNumber(buffer, offset + 28, 4, WMP_MAP_CENTER_Y));
    SectionCount areaCount = new SectionCount(buffer, offset + 32, 4, WMP_MAP_NUM_AREAS, AreaEntry.class);
    addField(areaCount);
    SectionOffset areaOffset = new SectionOffset(buffer, offset + 36, WMP_MAP_OFFSET_AREAS, AreaEntry.class);
    addField(areaOffset);
    SectionOffset linkOffset = new SectionOffset(buffer, offset + 40, WMP_MAP_OFFSET_AREA_LINKS, AreaLink.class);
    addField(linkOffset);
    SectionCount linkCount = new SectionCount(buffer, offset + 44, 4, WMP_MAP_NUM_AREA_LINKS, AreaLink.class);
    addField(linkCount);
    addField(new ResourceRef(buffer, offset + 48, WMP_MAP_ICONS, "BAM"));
    if (Profile.isEnhancedEdition()) {
      addField(new Flag(buffer, offset + 56, 4, "Flags", FLAGS_ARRAY));
      addField(new Unknown(buffer, offset + 60, 124));
    } else {
      addField(new Unknown(buffer, offset + 56, 128));
    }

    int curOfs = areaOffset.getValue();
    for (int i = 0; i < areaCount.getValue(); i++) {
      AreaEntry areaEntry = new AreaEntry(this, buffer, curOfs, i);
      curOfs = areaEntry.getEndOffset();
      addField(areaEntry);
      addCachedArea(areaEntry);
      areaEntry.readLinks(buffer, linkOffset);
    }

    return offset + 184;
  }

  /** Provides quick read access to available {@link AreaEntry} instances. */
  public List<AreaEntry> getCachedAreas() {
    ensureCachedArea();
    return Collections.unmodifiableList(areaCache);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype) {
    updateSectionOffset(datatype, datatype.getSize());
    if (datatype instanceof AreaEntry) {
      updateAreaEntryIndices(datatype);
      addCachedArea((AreaEntry)datatype);
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype) {
    super.datatypeAddedInChild(child, datatype);
    if (datatype instanceof AreaLink) {
      final DecNumber linkCount = (DecNumber)getAttribute(WMP_MAP_NUM_AREA_LINKS);
      linkCount.incValue(1);
      updateAreaLinks(datatype, 1);
    }
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype) {
    updateSectionOffset(datatype, -datatype.getSize());
    if (datatype instanceof AreaEntry) {
      removeCachedArea((AreaEntry)datatype);
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype) {
    super.datatypeRemovedInChild(child, datatype);
    if (datatype instanceof AreaLink) {
      final DecNumber linkCount = (DecNumber)getAttribute(WMP_MAP_NUM_AREA_LINKS);
      linkCount.incValue(-1);
      updateAreaLinks(datatype, -1);
    }
  }

  private void updateSectionOffset(AddRemovable datatype, int size) {
    if (!(datatype instanceof AreaLink)) {
      final SectionOffset ofsLinks = (SectionOffset)getAttribute(WMP_MAP_OFFSET_AREA_LINKS);
      if (datatype.getOffset() <= ofsLinks.getValue()) {
        ofsLinks.incValue(size);
      }
    }
  }

  private void updateAreaEntryIndices(AddRemovable datatype) {
    if (!(datatype instanceof AreaEntry)) {
      return;
    }

    final int baseOffset = getSectionOffset(AreaEntry.class).getValue();
    final int curIndex = (datatype.getOffset() - baseOffset) / 240;
    if (curIndex > 0) {
      // determine next available area link index
      final AreaEntry prevEntry = (AreaEntry)getAttribute(datatype.getOffset() - 240, false);
      if (prevEntry != null) {
        final List<Class<? extends AreaLink>> classList = Arrays.asList(AreaLinkNorth.class, AreaLinkWest.class,
            AreaLinkSouth.class, AreaLinkEast.class);
        int maxIndex = 0;
        for (final Class<? extends AreaLink> cls : classList) {
          final SectionCount cntLinks = prevEntry.getSectionCount(cls);
          if (cntLinks != null) {
            final DecNumber idxLinks = (DecNumber)getAttribute(cntLinks.getOffset() - 4);
            maxIndex = Math.max(maxIndex, idxLinks.getValue() + cntLinks.getValue());
          }
        }

        // applying area link index
        final AreaEntry curEntry = (AreaEntry)datatype;
        for (final Class<? extends AreaLink> cls : classList) {
          final SectionCount cntLinks = curEntry.getSectionCount(cls);
          if (cntLinks != null) {
            final DecNumber idxLinks = (DecNumber)curEntry.getAttribute(cntLinks.getOffset() - 4);
            idxLinks.setValue(maxIndex);
          }
        }
      }
    }
  }

  private void updateAreaLinks(AddRemovable datatype, int count) {
    if (count == 0 || !(datatype instanceof AreaLink)) {
      return;
    }

    final int ofsLinks = ((IsNumeric)getAttribute(WMP_MAP_OFFSET_AREA_LINKS)).getValue();
    final int idxLink = (datatype.getOffset() - ofsLinks) / 216;
    final List<Class<? extends AreaLink>> classList = Arrays.asList(AreaLinkNorth.class, AreaLinkWest.class,
        AreaLinkSouth.class, AreaLinkEast.class);
    for (final StructEntry se : getFields(AreaEntry.class)) {
      final AreaEntry areaEntry = (AreaEntry)se;
      for (final Class<? extends AreaLink> cls : classList) {
        final SectionCount cntLinks = areaEntry.getSectionCount(cls);
        if (cntLinks != null) {
          final DecNumber idxLinks = (DecNumber)areaEntry.getAttribute(cntLinks.getOffset() - 4);
          if (idxLink <= idxLinks.getValue() &&
              !(datatype.getClass() == cntLinks.getSection() && datatype.getParent() == areaEntry)) {
            final int newCount = Math.max(0, idxLinks.getValue() + count);
            idxLinks.setValue(newCount);
          }
        }
      }
    }
  }

  private void addCachedArea(AreaEntry areaEntry) {
    ensureCachedArea();
    if (areaEntry != null) {
      areaCache.add(areaEntry);
    }
  }

  private void removeCachedArea(AreaEntry areaEntry) {
    ensureCachedArea();
    if (areaEntry != null) {
      areaCache.remove(areaEntry);
    }
  }

  private void ensureCachedArea() {
    if (areaCache == null) {
      areaCache = new ArrayList<>();
    }
  }
}
