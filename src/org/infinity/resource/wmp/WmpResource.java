// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wmp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;

import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.TextString;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Resource;
import org.infinity.resource.StructEntry;
import org.infinity.resource.graphics.BamResource;
import org.infinity.resource.graphics.MosResource;
import org.infinity.resource.key.ResourceEntry;

/**
 * This resource describes the top-level map structure of the game. It details the x/y coordinate location of areas, the
 * graphics used to represent the area on the map (both {@link MosResource MOS} and {@link BamResource BAM}) and stores
 * flag information used to decide how the map icon is displayed (visible, reachable, already visited etc.)
 * <p>
 * Engine specific notes: Areas may be also displayed on the WorldMap in ToB using 2DA files:
 * <ul>
 * <li>{@code XNEWAREA.2DA} (Area entries section of wmp)</li>
 * <li>2DA file specified in {@code XNEWAREA.2DA} (Area links section) for example {@code XL3000.2DA}</li>
 * </ul>
 * <p>
 * A WMP resource must have at least one area entry, and one area link to be considered valid.
 */
public class WmpResource extends AbstractStruct implements Resource, HasViewerTabs {
  // WMP-specific field labels
  public static final String WMP_NUM_MAPS     = "# maps";
  public static final String WMP_OFFSET_MAPS  = "Maps offset";

  private StructHexViewer hexViewer;

  public WmpResource(ResourceEntry entry) throws Exception {
    super(entry);
  }

  // --------------------- Begin Interface HasViewerTabs ---------------------

  @Override
  public int getViewerTabCount() {
    return 2;
  }

  @Override
  public String getViewerTabName(int index) {
    switch (index) {
      case 0:
        return StructViewer.TAB_VIEW;
      case 1:
        return StructViewer.TAB_RAW;
    }
    return null;
  }

  @Override
  public JComponent getViewerTab(int index) {
    switch (index) {
      case 0: {
        JTabbedPane tabbedPane = new JTabbedPane();
        int count = ((IsNumeric) getAttribute(WMP_NUM_MAPS)).getValue();
        for (int i = 0; i < count; i++) {
          MapEntry entry = (MapEntry) getAttribute(MapEntry.WMP_MAP + " " + i);
          tabbedPane.addTab(entry.getName(), entry.getViewerTab(0));
        }
        return tabbedPane;
      }
      case 1: {
        if (hexViewer == null) {
          hexViewer = new StructHexViewer(this, new BasicColorMap(this, true));
        }
        return hexViewer;
      }
    }
    return null;
  }

  @Override
  public boolean viewerTabAddedBefore(int index) {
    return (index == 0);
  }

  // --------------------- End Interface HasViewerTabs ---------------------

  // --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException {
    super.writeFlatFields(os);
  }

  // --------------------- End Interface Writeable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new TextString(buffer, offset, 4, COMMON_SIGNATURE));
    addField(new TextString(buffer, offset + 4, 4, COMMON_VERSION));
    SectionCount entryCount = new SectionCount(buffer, offset + 8, 4, WMP_NUM_MAPS, MapEntry.class);
    addField(entryCount);
    SectionOffset entryOffset = new SectionOffset(buffer, offset + 12, WMP_OFFSET_MAPS, MapEntry.class);
    addField(entryOffset);

    offset = entryOffset.getValue();
    for (int i = 0; i < entryCount.getValue(); i++) {
      MapEntry entry = new MapEntry(this, buffer, offset, i);
      offset = entry.getEndOffset();
      addField(entry);
    }

    int endoffset = offset;
    for (final StructEntry entry : getFlatFields()) {
      endoffset = Math.max(endoffset, entry.getOffset() + entry.getSize());
    }
    return endoffset;
  }

  @Override
  protected void viewerInitialized(StructViewer viewer) {
    viewer.addTabChangeListener(hexViewer);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype) {
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype) {
    super.datatypeAddedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype) {
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype) {
    super.datatypeRemovedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }
}
