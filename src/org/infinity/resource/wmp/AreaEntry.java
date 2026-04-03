// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wmp;

import java.nio.ByteBuffer;
import java.util.List;

import javax.swing.JComponent;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.gui.StructViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.StructEntry;
import org.infinity.util.io.StreamUtils;

public class AreaEntry extends AbstractStruct implements HasViewerTabs, AddRemovable, HasChildStructs {
  // WMP/AreaEntry-specific field labels
  public static final String WMP_AREA                   = "Area";
  public static final String WMP_AREA_CURRENT           = "Current area";
  public static final String WMP_AREA_ORIGINAL          = "Original area";
  public static final String WMP_AREA_SCRIPT_NAME       = "Script name";
  public static final String WMP_AREA_FLAGS             = "Flags";
  public static final String WMP_AREA_ICON_INDEX        = "Icon number";
  public static final String WMP_AREA_COORDINATE_X      = "Coordinate: X";
  public static final String WMP_AREA_COORDINATE_Y      = "Coordinate: Y";
  public static final String WMP_AREA_NAME              = "Name";
  public static final String WMP_AREA_TOOLTIP           = "Tooltip";
  public static final String WMP_AREA_LOADING_IMAGE     = "Loading image";
  public static final String WMP_AREA_FIRST_LINK_NORTH  = "First link (north)";
  public static final String WMP_AREA_FIRST_LINK_WEST   = "First link (west)";
  public static final String WMP_AREA_FIRST_LINK_SOUTH  = "First link (south)";
  public static final String WMP_AREA_FIRST_LINK_EAST   = "First link (east)";
  public static final String WMP_AREA_NUM_LINKS_NORTH   = "# links (north)";
  public static final String WMP_AREA_NUM_LINKS_WEST    = "# links (west)";
  public static final String WMP_AREA_NUM_LINKS_SOUTH   = "# links (south)";
  public static final String WMP_AREA_NUM_LINKS_EAST    = "# links (east)";

  private static final String[] FLAGS_ARRAY = { "No flags set", "Visible", "Reveal from linked area", "Can be visited",
      "Has been visited" };

  public AreaEntry() throws Exception {
    super(null, WMP_AREA, StreamUtils.getByteBuffer(240), 0);
  }

  public AreaEntry(AbstractStruct superStruct, ByteBuffer buffer, int offset, int nr) throws Exception {
    super(superStruct, WMP_AREA + " " + nr, buffer, offset);
  }

  // --------------------- Begin Interface HasChildStructs ---------------------

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    return new AddRemovable[] { new AreaLinkNorth(), new AreaLinkWest(), new AreaLinkSouth(), new AreaLinkEast() };
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
    return new ViewerArea(this);
  }

  @Override
  public boolean viewerTabAddedBefore(int index) {
    return true;
  }

  // --------------------- End Interface HasViewerTabs ---------------------

  // --------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove() {
    return true;
  }

  // --------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new ResourceRef(buffer, offset, WMP_AREA_CURRENT, "ARE"));
    addField(new ResourceRef(buffer, offset + 8, WMP_AREA_ORIGINAL, "ARE"));
    addField(new TextString(buffer, offset + 16, 32, WMP_AREA_SCRIPT_NAME));
    addField(new Flag(buffer, offset + 48, 4, WMP_AREA_FLAGS, FLAGS_ARRAY));
    addField(new DecNumber(buffer, offset + 52, 4, WMP_AREA_ICON_INDEX));
    addField(new DecNumber(buffer, offset + 56, 4, WMP_AREA_COORDINATE_X));
    addField(new DecNumber(buffer, offset + 60, 4, WMP_AREA_COORDINATE_Y));
    addField(new StringRef(buffer, offset + 64, WMP_AREA_NAME));
    addField(new StringRef(buffer, offset + 68, WMP_AREA_TOOLTIP));
    addField(new ResourceRef(buffer, offset + 72, WMP_AREA_LOADING_IMAGE, "MOS"));
    addField(new DecNumber(buffer, offset + 80, 4, WMP_AREA_FIRST_LINK_NORTH));
    addField(new SectionCount(buffer, offset + 84, 4, WMP_AREA_NUM_LINKS_NORTH, AreaLinkNorth.class));
    addField(new DecNumber(buffer, offset + 88, 4, WMP_AREA_FIRST_LINK_WEST));
    addField(new SectionCount(buffer, offset + 92, 4, WMP_AREA_NUM_LINKS_WEST, AreaLinkWest.class));
    addField(new DecNumber(buffer, offset + 96, 4, WMP_AREA_FIRST_LINK_SOUTH));
    addField(new SectionCount(buffer, offset + 100, 4, WMP_AREA_NUM_LINKS_SOUTH, AreaLinkSouth.class));
    addField(new DecNumber(buffer, offset + 104, 4, WMP_AREA_FIRST_LINK_EAST));
    addField(new SectionCount(buffer, offset + 108, 4, WMP_AREA_NUM_LINKS_EAST, AreaLinkEast.class));
    addField(new Unknown(buffer, offset + 112, 128));
    return offset + 240;
  }

  @Override
  public int getDatatypeIndex(AddRemovable addedEntry) {
    if (addedEntry instanceof AreaLink) {
      // add new entry to the end of the specific link section
      final SectionCount cntAreaLinks = getSectionCount(addedEntry.getClass());
      if (cntAreaLinks != null) {
        final List<StructEntry> fields = getFields();
        for (int i = 0, cnt = fields.size(); i < cnt; i++) {
          if (fields.get(i).getClass() == addedEntry.getClass()) {
            return i + cntAreaLinks.getValue();
          }
        }
      }
    }
    return super.getDatatypeIndex(addedEntry);
  }

  @Override
  protected void setAddRemovableOffset(AddRemovable datatype) {
    if (datatype instanceof AreaLink) {
      final SectionCount cntAreaLinks = getSectionCount(datatype.getClass());
      if (cntAreaLinks != null) {
        final DecNumber idxAreaLinks = (DecNumber)getAttribute(cntAreaLinks.getOffset() - 4);
        if (idxAreaLinks != null) {
          final MapEntry mapEntry = (MapEntry)getParent();
          final int offset = ((IsNumeric)mapEntry.getAttribute(MapEntry.WMP_MAP_OFFSET_AREA_LINKS)).getValue();
          datatype.setOffset(offset + (idxAreaLinks.getValue() + cntAreaLinks.getValue() - 1) * 216);
          ((AbstractStruct) datatype).realignStructOffsets();
        }
      }
    }
  }

  void readLinks(ByteBuffer buffer, DecNumber linkOffset) throws Exception {
    IsNumeric northStart = (IsNumeric) getAttribute(WMP_AREA_FIRST_LINK_NORTH);
    IsNumeric northCount = (IsNumeric) getAttribute(WMP_AREA_NUM_LINKS_NORTH);
    int offset = linkOffset.getValue() + northStart.getValue() * 216;
    for (int i = 0; i < northCount.getValue(); i++) {
      addField(new AreaLinkNorth(this, buffer, offset + i * 216, i));
    }

    IsNumeric westStart = (IsNumeric) getAttribute(WMP_AREA_FIRST_LINK_WEST);
    IsNumeric westCount = (IsNumeric) getAttribute(WMP_AREA_NUM_LINKS_WEST);
    offset = linkOffset.getValue() + westStart.getValue() * 216;
    for (int i = 0; i < westCount.getValue(); i++) {
      addField(new AreaLinkWest(this, buffer, offset + i * 216, i));
    }

    IsNumeric southStart = (IsNumeric) getAttribute(WMP_AREA_FIRST_LINK_SOUTH);
    IsNumeric southCount = (IsNumeric) getAttribute(WMP_AREA_NUM_LINKS_SOUTH);
    offset = linkOffset.getValue() + southStart.getValue() * 216;
    for (int i = 0; i < southCount.getValue(); i++) {
      addField(new AreaLinkSouth(this, buffer, offset + i * 216, i));
    }

    IsNumeric eastStart = (IsNumeric) getAttribute(WMP_AREA_FIRST_LINK_EAST);
    IsNumeric eastCount = (IsNumeric) getAttribute(WMP_AREA_NUM_LINKS_EAST);
    offset = linkOffset.getValue() + eastStart.getValue() * 216;
    for (int i = 0; i < eastCount.getValue(); i++) {
      addField(new AreaLinkEast(this, buffer, offset + i * 216, i));
    }
  }
}
