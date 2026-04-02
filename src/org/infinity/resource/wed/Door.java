// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wed;

import java.nio.ByteBuffer;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.TextString;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.StructEntry;
import org.infinity.util.io.StreamUtils;

public final class Door extends AbstractStruct implements AddRemovable, HasChildStructs {
  // WED/Door-specific field labels
  public static final String WED_DOOR                         = "Door";
  public static final String WED_DOOR_NAME                    = "Name";
  public static final String WED_DOOR_IS_DOOR                 = "Is door?";
  public static final String WED_DOOR_TILEMAP_LOOKUP_INDEX    = "Tilemap lookup index";
  public static final String WED_DOOR_NUM_TILEMAP_INDICES     = "# tilemap indices";
  public static final String WED_DOOR_NUM_POLYGONS_OPEN       = "# polygons open";
  public static final String WED_DOOR_NUM_POLYGONS_CLOSED     = "# polygons closed";
  public static final String WED_DOOR_OFFSET_POLYGONS_OPEN    = "Polygons open offset";
  public static final String WED_DOOR_OFFSET_POLYGONS_CLOSED  = "Polygons closed offset";
  public static final String WED_DOOR_TILEMAP_INDEX           = "Tilemap index";

  public Door() throws Exception {
    super(null, WED_DOOR, StreamUtils.getByteBuffer(26), 0);
  }

  public Door(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, WED_DOOR + " " + number, buffer, offset);
  }

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    return new AddRemovable[] { new IndexNumber(2, WED_DOOR_TILEMAP_INDEX), new OpenPolygon(), new ClosedPolygon() };
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception {
    return entry;
  }

  @Override
  public boolean canRemove() {
    return true;
  }

  @Override
  protected void setAddRemovableOffset(AddRemovable datatype) {
    if (datatype instanceof IndexNumber) {
      final int offset = ((IsNumeric) getParent().getAttribute(WedResource.WED_OFFSET_DOOR_TILEMAP_LOOKUP)).getValue();
      int index = getTilemapIndex().getValue();
      datatype.setOffset(offset + index * 2);
    }
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype) {
    if (datatype instanceof Polygon) {
      WedResource.updatePolygon(getParent(), datatype);
    }
  }

  public DecNumber getTilemapIndex() {
    return (DecNumber) getAttribute(WED_DOOR_TILEMAP_LOOKUP_INDEX);
  }

  public void readVertices(ByteBuffer buffer, int offset) throws Exception {
    for (final StructEntry o : getFields()) {
      if (o instanceof Polygon) {
        ((Polygon) o).readVertices(buffer, offset);
      }
    }
  }

  public void updatePolygonsOffset(int offset) {
    int polyOffset = Integer.MAX_VALUE;
    for (final StructEntry o : getFields()) {
      if (o instanceof Polygon) {
        polyOffset = Math.min(polyOffset, o.getOffset());
      }
    }
    if (polyOffset != Integer.MAX_VALUE) {
      offset = polyOffset;
    }
    ((SectionOffset) getAttribute(WED_DOOR_OFFSET_POLYGONS_OPEN)).setValue(offset);
    for (final StructEntry o : getFields()) {
      if (o instanceof OpenPolygon) {
        offset += 18;
      }
    }
    ((SectionOffset) getAttribute(WED_DOOR_OFFSET_POLYGONS_CLOSED)).setValue(offset);
    for (final StructEntry o : getFields()) {
      if (o instanceof ClosedPolygon) {
        offset += 18;
      }
    }
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new TextString(buffer, offset, 8, WED_DOOR_NAME));
    addField(new Bitmap(buffer, offset + 8, 2, WED_DOOR_IS_DOOR, OPTION_NOYES));
    DecNumber indexTileCell = new DecNumber(buffer, offset + 10, 2, WED_DOOR_TILEMAP_LOOKUP_INDEX);
    addField(indexTileCell);
    SectionCount countTileCell = new SectionCount(buffer, offset + 12, 2, WED_DOOR_NUM_TILEMAP_INDICES,
        IndexNumber.class);
    addField(countTileCell);
    SectionCount countOpen = new SectionCount(buffer, offset + 14, 2, WED_DOOR_NUM_POLYGONS_OPEN, OpenPolygon.class);
    addField(countOpen);
    SectionCount countClosed = new SectionCount(buffer, offset + 16, 2, WED_DOOR_NUM_POLYGONS_CLOSED,
        ClosedPolygon.class);
    addField(countClosed);
    SectionOffset offsetOpen = new SectionOffset(buffer, offset + 18, WED_DOOR_OFFSET_POLYGONS_OPEN, OpenPolygon.class);
    addField(offsetOpen);
    SectionOffset offsetClosed = new SectionOffset(buffer, offset + 22, WED_DOOR_OFFSET_POLYGONS_CLOSED,
        ClosedPolygon.class);
    addField(offsetClosed);

    for (int i = 0; i < countOpen.getValue(); i++) {
      addField(new OpenPolygon(this, buffer, offsetOpen.getValue() + 18 * i, i));
    }

    for (int i = 0; i < countClosed.getValue(); i++) {
      addField(new ClosedPolygon(this, buffer, offsetClosed.getValue() + 18 * i, i));
    }

    if (getParent() != null) {
      final IsNumeric offsetTileCell = (IsNumeric) getParent().getAttribute(WedResource.WED_OFFSET_DOOR_TILEMAP_LOOKUP);
      for (int i = 0; i < countTileCell.getValue(); i++) {
        addField(new IndexNumber(buffer, offsetTileCell.getValue() + 2 * (indexTileCell.getValue() + i), 2,
            WED_DOOR_TILEMAP_INDEX + " " + i));
      }
    }
    return offset + 26;
  }
}
