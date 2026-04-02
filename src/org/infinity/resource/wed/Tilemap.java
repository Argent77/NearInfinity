// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wed;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.Unknown;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.util.io.StreamUtils;

public final class Tilemap extends AbstractStruct implements AddRemovable {
  // WED/Tilemap-specific field labels
  public static final String WED_TILEMAP                  = "Tilemap";
  public static final String WED_TILEMAP_TILE_INDEX_PRI   = "Tilemap index (primary)";
  public static final String WED_TILEMAP_TILE_COUNT_PRI   = "Tilemap count (primary)";
  public static final String WED_TILEMAP_TILE_INDEX_SEC   = "Tile index (secondary)";
  public static final String WED_TILEMAP_DRAW_OVERLAYS    = "Draw Overlays";

  private static final String[] FLAGS_ARRAY = { "Primary overlay only", "Unused", "Overlay 1", "Overlay 2", "Overlay 3",
      "Overlay 4", "Overlay 5", "Overlay 6", "Overlay 7" };

  protected Tilemap() throws Exception {
    super(null, WED_TILEMAP, createEmptyBuffer(true), 0);
  }

  public Tilemap(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, WED_TILEMAP + " " + number, buffer, offset, 5);
  }

  public int getTileCount() {
    return ((IsNumeric) getAttribute(WED_TILEMAP_TILE_COUNT_PRI)).getValue();
  }

  // --------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove() {
    return true;
  }

  // --------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    // Primary tile index is stored as unsigned 16-bit in WED. Read with public constructor then fix value.
    addField(new DecNumber(buffer, offset, 2, WED_TILEMAP_TILE_INDEX_PRI));
    addField(new DecNumber(buffer, offset + 2, 2, WED_TILEMAP_TILE_COUNT_PRI));
    // Read secondary tile index as unsigned 16-bit where 0xFFFF means 'no secondary index' (-1).
    addField(new DecNumber(buffer, offset + 4, 2, WED_TILEMAP_TILE_INDEX_SEC));
    // Post-process primary and secondary values to interpret as unsigned (with 0xFFFF => -1 for secondary).
    try {
      DecNumber pri = (DecNumber) getAttribute(WED_TILEMAP_TILE_INDEX_PRI);
      int rawPri = buffer.getShort(offset) & 0xffff;
      pri.setValue(rawPri);
    } catch (Exception e) {
      // ignore
    }
    try {
      DecNumber sec = (DecNumber) getAttribute(WED_TILEMAP_TILE_INDEX_SEC);
      int rawSec = buffer.getShort(offset + 4) & 0xffff;
      if (rawSec == 0xffff) {
        sec.setValue(-1);
      } else {
        sec.setValue(rawSec);
      }
    } catch (Exception e) {
      // ignore
    }
    addField(new Flag(buffer, offset + 6, 1, WED_TILEMAP_DRAW_OVERLAYS, FLAGS_ARRAY));
    addField(new Unknown(buffer, offset + 7, 3));
    return offset + 10;
  }

  /** Creates an empty {@code Tilemap} buffer, optionally initialized with sane default values. */
  private static ByteBuffer createEmptyBuffer(boolean applyDefaults) {
    final ByteBuffer bb = StreamUtils.getByteBuffer(10);
    if (applyDefaults) {
      bb.putShort(2, (short)1);
      bb.putShort(4, (short)-1);
    }
    return bb;
  }
}
