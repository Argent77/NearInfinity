// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wed;

import java.nio.ByteBuffer;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.Unknown;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.Profile;
import org.infinity.util.io.StreamUtils;

public final class Overlay extends AbstractStruct implements HasChildStructs, AddRemovable {
  // WED/Overlay-specific field labels
  public static final String WED_OVERLAY                        = "Overlay";
  public static final String WED_OVERLAY_WIDTH                  = "Width";
  public static final String WED_OVERLAY_HEIGHT                 = "Height";
  public static final String WED_OVERLAY_TILESET                = "Tileset";
  public static final String WED_OVERLAY_NUM_UNIQUE_TILES       = "# unique tiles";
  public static final String WED_OVERLAY_MOVEMENT_TYPE          = "Movement type";
  public static final String WED_OVERLAY_OFFSET_TILEMAP         = "Tilemap offset";
  public static final String WED_OVERLAY_OFFSET_TILEMAP_LOOKUP  = "Tilemap lookup offset";
  public static final String WED_OVERLAY_TILEMAP_INDEX          = "Tilemap index";

  public static final String[] MOVEMENT_ARRAY = { "Default", "Disable rendering", "Alternate rendering" };

  public Overlay() throws Exception {
    super(null, WED_OVERLAY, StreamUtils.getByteBuffer(24), 0);
  }

  public Overlay(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, WED_OVERLAY + " " + number, buffer, offset);
  }

  public void updateOffsets(AddRemovable datatype, int size) {
    final SectionOffset offsetTileMap = (SectionOffset)getAttribute(WED_OVERLAY_OFFSET_TILEMAP);
    boolean skip = (datatype.getParent() == this) && offsetTileMap.getSection().isAssignableFrom(datatype.getClass());
    if (!skip && offsetTileMap.getValue() >= datatype.getOffset()) {
      offsetTileMap.incValue(size);
    }

    final SectionOffset offsetTileLookup = (SectionOffset)getAttribute(WED_OVERLAY_OFFSET_TILEMAP_LOOKUP);
    skip = (datatype.getParent() == this) && offsetTileLookup.getSection().isAssignableFrom(datatype.getClass());
    if (!skip && offsetTileLookup.getValue() >= datatype.getOffset()) {
      offsetTileLookup.incValue(size);
    }
  }

  // --------------------- Begin Interface HasChildStructs ---------------------

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    return new AddRemovable[] { new Tilemap(), new IndexNumber(2, WED_OVERLAY_TILEMAP_INDEX) };
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception {
    return entry;
  }

  // --------------------- Begin Interface HasChildStructs ---------------------

  // --------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove() {
    return true;
  }

  // --------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    DecNumber width = new DecNumber(buffer, offset, 2, WED_OVERLAY_WIDTH);
    addField(width);
    DecNumber height = new DecNumber(buffer, offset + 2, 2, WED_OVERLAY_HEIGHT);
    addField(height);
    ResourceRef tileset = new ResourceRef(buffer, offset + 4, WED_OVERLAY_TILESET, "TIS");
    addField(tileset);
    if (Profile.isEnhancedEdition()) {
      addField(new DecNumber(buffer, offset + 12, 2, WED_OVERLAY_NUM_UNIQUE_TILES));
      addField(new Bitmap(buffer, offset + 14, 2, WED_OVERLAY_MOVEMENT_TYPE, MOVEMENT_ARRAY));
    } else {
      addField(new Unknown(buffer, offset + 12, 4));
    }
    final SectionOffset offsetTileMap = new SectionOffset(buffer, offset + 16, WED_OVERLAY_OFFSET_TILEMAP,
        Tilemap.class);
    addField(offsetTileMap);
    final SectionOffset offsetTileLookup = new SectionOffset(buffer, offset + 20, WED_OVERLAY_OFFSET_TILEMAP_LOOKUP,
        IndexNumber.class);
    addField(offsetTileLookup);
    int retoff = offset + 24;

    // readTilemap
    int lookuptablesize = 0;
    if (!tileset.toString().equalsIgnoreCase(".TIS")) {
      offset = offsetTileMap.getValue();
      int mapCount = width.getValue() * height.getValue();
      for (int i = 0; i < mapCount; i++) {
        Tilemap map = new Tilemap(this, buffer, offset, i);
        offset = map.getEndOffset();
        lookuptablesize += map.getTileCount();
        addField(map);
      }
    }
    // readLookuptable
    offset = offsetTileLookup.getValue();
    for (int i = 0; i < lookuptablesize; i++) {
      addField(new IndexNumber(buffer, offset + i * 2, 2, WED_OVERLAY_TILEMAP_INDEX + " " + i));
    }
    return retoff;
  }
}
