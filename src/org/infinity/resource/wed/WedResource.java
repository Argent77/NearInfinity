// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wed;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

import org.infinity.NearInfinity;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.HexNumber;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.TextString;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Resource;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.vertex.Vertex;
import org.infinity.util.ArrayUtil;
import org.infinity.util.Misc;
import org.tinylog.Logger;

/**
 * This resource maps the layout of terrain to the tiles in the tileset, and adds structure to an area by listing its
 * {@link Door doors} and {@link WallPolygon walls}.
 * <p>
 * An area is a grid, with each 64*64 cell within the grid (called a tile cell) being a location for a tile. Tile cells
 * are numbered, starting at 0, and run from top left to bottom right (i.e. a tile cell number can be calculated by
 * {@code y*width+x}). As well the tiles for the main area graphics, an area can use {@link Overlay overlays}. Overlays
 * are usually used for rivers and lakes. Each overlay layer is placed in a separate grid, which are stacked on top of
 * the base grid. Areas also contain another grid, split into 16*16 squares, for the exploration map.
 * <p>
 * The process of drawing an area is outlined below:
 * <ul>
 * <li>The cell number acts as an index into a tilemap structure</li>
 * <li>This give a "tile lookup index" which is an index into the tile indices lookup table</li>
 * <li>The tile indices lookup table gives the index into the actual tileset, at which point, the tile is drawn</li>
 * <li>The process is repeated for each required overlay (using the associated overlay tilemap / tile indices)</li>
 * </ul>
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/wed_v1.3.htm">
 *      https://gibberlings3.github.io/iesdp/file_formats/ie_formats/wed_v1.3.htm</a>
 */
public final class WedResource extends AbstractStruct
    implements Resource, HasChildStructs, HasViewerTabs, ActionListener {
  // WED-specific field labels
  public static final String WED_NUM_OVERLAYS               = "# overlays";
  public static final String WED_NUM_DOORS                  = "# doors";
  public static final String WED_OFFSET_OVERLAYS            = "Overlays offset";
  public static final String WED_OFFSET_SECOND_HEADER       = "Second header offset";
  public static final String WED_OFFSET_DOORS               = "Doors offset";
  public static final String WED_OFFSET_DOOR_TILEMAP_LOOKUP = "Door tilemap lookup offset";
  public static final String WED_NUM_WALL_POLYGONS          = "# wall polygons";
  public static final String WED_OFFSET_WALL_POLYGONS       = "Wall polygons offset";
  public static final String WED_OFFSET_VERTICES            = "Vertices offset";
  public static final String WED_OFFSET_WALL_GROUPS         = "Wall groups offset";
  public static final String WED_OFFSET_WALL_POLYGON_LOOKUP = "Wall polygon lookup offset";
  public static final String WED_WALL_POLYGON_INDEX         = "Wall polygon index";

  private StructHexViewer hexViewer;
  private JButton bRebuildWallgroups;

  public WedResource(ResourceEntry entry) throws Exception {
    super(entry);
  }

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    return new AddRemovable[] { new Overlay(), new Door(), new WallPolygon(), new Wallgroup(),
        new IndexNumber(2, WED_WALL_POLYGON_INDEX) };
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception {
    return entry;
  }

  @Override
  public void write(OutputStream os) throws IOException {
    super.writeFlatFields(os);
  }

  @Override
  public int getViewerTabCount() {
    return 1;
  }

  @Override
  public String getViewerTabName(int index) {
    return StructViewer.TAB_RAW;
  }

  @Override
  public JComponent getViewerTab(int index) {
    if (hexViewer == null) {
      hexViewer = new StructHexViewer(this, new BasicColorMap(this, true));
    }
    return hexViewer;
  }

  @Override
  public boolean viewerTabAddedBefore(int index) {
    return false;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == bRebuildWallgroups) {
      final int result = JOptionPane.showConfirmDialog(ViewerUtil.getWindowAncestor(getViewer()),
          "Rebuild wallgroup section from scratch?\nCurrent wallgroup and wall polygon index entries will be overwritten.",
          "Rebuild Wallgroups", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (result == JOptionPane.YES_OPTION) {
        WindowBlocker block = new WindowBlocker(WindowBlocker.getRootPaneAncestor(getViewer(), NearInfinity.getInstance()));
        block.setBlocked(true);
        try {
          rebuildWallgroups();
        } catch (Exception ex) {
          Logger.debug(ex);
          block.setBlocked(false);
          JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(getViewer()), ex.getMessage(), "Error",
              JOptionPane.ERROR_MESSAGE);
          return;
        } finally {
          block.setBlocked(false);
        }
        JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(getViewer()), "Wallgroups successfully rebuilt.",
            "Rebuild Wallgroups", JOptionPane.INFORMATION_MESSAGE);
      }
    }
  }

  @Override
  protected void viewerInitialized(StructViewer viewer) {
    viewer.addTabChangeListener(hexViewer);

    final ButtonPanel buttonPanel = viewer.getButtonPanel();
    int idx = buttonPanel.getControlPosition(buttonPanel.getControlByType(ButtonPanel.Control.PRINT));
    if (idx < 0) {
      idx = 5;
    }
    bRebuildWallgroups = new JButton("Rebuild wallgroups", Icons.ICON_REFRESH_16.getIcon());
    bRebuildWallgroups.setToolTipText("Rebuilds wallgroups from scratch.");
    bRebuildWallgroups.addActionListener(this);
    buttonPanel.addControl(idx, bRebuildWallgroups, ButtonPanel.Control.CUSTOM_1);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype) {
    updateSectionOffsets(datatype, datatype.getSize());
    if (datatype instanceof IndexNumber) {
      updateWallgroups(datatype, false);
    } else if (datatype instanceof Polygon) {
      updatePolygon(this, datatype);
    }
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype) {
    updateSectionOffsets(datatype, datatype.getSize());
    if (datatype instanceof Vertex) {
      updateVertices();
    } else if (datatype instanceof IndexNumber && child instanceof Door) {
      Door childDoor = (Door) child;
      int childIndex = childDoor.getTilemapIndex().getValue();
      for (final StructEntry o : getFields()) {
        if (o instanceof Door && o != childDoor) {
          DecNumber tilemapIndex = ((Door) o).getTilemapIndex();
          if (tilemapIndex.getValue() >= childIndex) {
            tilemapIndex.incValue(1);
          }
        }
      }
    }
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype) {
    updateSectionOffsets(datatype, -datatype.getSize());
    if (datatype instanceof IndexNumber) {
      updateWallgroups(datatype, true);
    }
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype) {
    updateSectionOffsets(datatype, -datatype.getSize());
    if (datatype instanceof Vertex) {
      updateVertices();
    } else if (datatype instanceof IndexNumber && child instanceof Door) {
      Door childDoor = (Door) child;
      int childIndex = childDoor.getTilemapIndex().getValue();
      for (final StructEntry o : getFields()) {
        if (o instanceof Door && o != childDoor) {
          DecNumber tilemapIndex = ((Door) o).getTilemapIndex();
          if (tilemapIndex.getValue() > childIndex) {
            tilemapIndex.incValue(-1);
          }
        }
      }
    }
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    int startOffset = offset;

    addField(new TextString(buffer, offset, 4, COMMON_SIGNATURE));
    addField(new TextString(buffer, offset + 4, 4, COMMON_VERSION));
    SectionCount countOverlays = new SectionCount(buffer, offset + 8, 4, WED_NUM_OVERLAYS, Overlay.class);
    addField(countOverlays);
    SectionCount countDoors = new SectionCount(buffer, offset + 12, 4, WED_NUM_DOORS, Door.class);
    addField(countDoors);
    SectionOffset offsetOverlays = new SectionOffset(buffer, offset + 16, WED_OFFSET_OVERLAYS, Overlay.class);
    addField(offsetOverlays);
    SectionOffset offsetHeader2 = new SectionOffset(buffer, offset + 20, WED_OFFSET_SECOND_HEADER, HexNumber.class);
    addField(offsetHeader2);
    SectionOffset offsetDoors = new SectionOffset(buffer, offset + 24, WED_OFFSET_DOORS, Door.class);
    addField(offsetDoors);
    HexNumber offsetDoortile = new HexNumber(buffer, offset + 28, 4, WED_OFFSET_DOOR_TILEMAP_LOOKUP);
    addField(offsetDoortile);

    offset = offsetOverlays.getValue();
    for (int i = 0; i < countOverlays.getValue(); i++) {
      Overlay overlay = new Overlay(this, buffer, offset, i);
      offset = overlay.getEndOffset();
      addField(overlay);
    }

    offset = offsetHeader2.getValue();
    SectionCount countWallpolygons = new SectionCount(buffer, offset, 4, WED_NUM_WALL_POLYGONS, WallPolygon.class);
    addField(countWallpolygons);
    SectionOffset offsetPolygons = new SectionOffset(buffer, offset + 4, WED_OFFSET_WALL_POLYGONS, WallPolygon.class);
    addField(offsetPolygons);
    HexNumber offsetVertices = new HexNumber(buffer, offset + 8, 4, WED_OFFSET_VERTICES);
    addField(offsetVertices);
    SectionOffset offsetWallgroups = new SectionOffset(buffer, offset + 12, WED_OFFSET_WALL_GROUPS, Wallgroup.class);
    addField(offsetWallgroups);
    SectionOffset offsetPolytable = new SectionOffset(buffer, offset + 16, WED_OFFSET_WALL_POLYGON_LOOKUP,
        IndexNumber.class);
    addField(offsetPolytable);

    HexNumber[] offsets = new HexNumber[] { offsetOverlays, offsetHeader2, offsetDoors, offsetDoortile, offsetPolygons,
        offsetWallgroups, offsetPolytable, new HexNumber(
            ByteBuffer.wrap(Misc.intToArray(buffer.limit() - startOffset)).order(ByteOrder.LITTLE_ENDIAN), 0, 4, "") };
    Arrays.sort(offsets, Comparator.comparingInt(DecNumber::getValue));

    offset = offsetDoors.getValue();
    for (int i = 0; i < countDoors.getValue(); i++) {
      Door door = new Door(this, buffer, offset, i);
      offset = door.getEndOffset();
      door.readVertices(buffer, offsetVertices.getValue());
      addField(door);
    }

    offset = offsetWallgroups.getValue();
    int countPolytable = 0;
    int countWallgroups = (offsets[ArrayUtil.indexOf(offsets, offsetWallgroups) + 1].getValue()
        - offsetWallgroups.getValue()) / 4;
    for (int i = 0; i < countWallgroups; i++) {
      Wallgroup wall = new Wallgroup(this, buffer, offset, i);
      offset = wall.getEndOffset();
      countPolytable = Math.max(countPolytable, wall.getNextPolygonIndex());
      addField(wall);
    }

    offset = offsetPolygons.getValue();
    for (int i = 0; i < countWallpolygons.getValue(); i++) {
      Polygon poly = new WallPolygon(this, buffer, offset, i);
      offset = poly.getEndOffset();
      poly.readVertices(buffer, offsetVertices.getValue());
      addField(poly);
    }

    offset = offsetPolytable.getValue();
    for (int i = 0; i < countPolytable; i++) {
      addField(new IndexNumber(buffer, offset + i * 2, 2, WED_WALL_POLYGON_INDEX + " " + i));
    }

    int endoffset = offset;
    for (final StructEntry entry : getFlatFields()) {
      if (entry.getOffset() + entry.getSize() > endoffset) {
        endoffset = entry.getOffset() + entry.getSize();
      }
    }
    return endoffset;
  }

  /**
   * Rebuilds the wallgroups section. Old wallgroup and polygon index entries are discarded.
   *
   * @throws Exception thrown if an unrecoverable error occurs.
   */
  public void rebuildWallgroups() throws Exception {
    if (((IsNumeric)getAttribute(WED_NUM_OVERLAYS)).getValue() == 0) {
      throw new Exception("No overlay structures available.");
    }

    final int ofsOverlays = ((IsNumeric)getAttribute(WED_OFFSET_OVERLAYS)).getValue();
    final Overlay overlay = getAttribute(ofsOverlays, Overlay.class);
    if (overlay == null) {
      throw new Exception("Primary overlay structure not available.");
    }

    final int tileWidth = ((IsNumeric)overlay.getAttribute(Overlay.WED_OVERLAY_WIDTH)).getValue();
    final int tileHeight = ((IsNumeric)overlay.getAttribute(Overlay.WED_OVERLAY_HEIGHT)).getValue();
    final int mapWidth = tileWidth * 64;
    final int mapHeight = tileHeight * 64;

    // calculating number of wallgroup entries
    final int wgPerRow = (mapWidth + 639) / 640;
    final int wgRows = (mapHeight + 479) / 480;
    final int wgTotal = wgPerRow * wgRows;

    // cache for wallgroup entries
    final List<List<Integer>> wallgroups = new ArrayList<>(wgTotal);
    for (int i = 0; i < wgTotal; i++) {
      wallgroups.add(new ArrayList<>());
    }

    // processing wallpolys
    int polyIndex = 0;
    final Rectangle rect = new Rectangle();
    for (final StructEntry se : getFields(Polygon.class)) {
      final Polygon poly = (Polygon)se;
      if (calculateWallgroupIndices(mapWidth, mapHeight, poly, rect)) {
        for (int y = 0; y < rect.height; y++) {
          for (int x = 0; x < rect.width; x++) {
            final int idx = (rect.y + y) * wgPerRow + (rect.x + x);
            if (idx < wgTotal) {
              wallgroups.get(idx).add(polyIndex);
            } else {
              Logger.warn("Wallgroup index out of bounds for " + poly.getName() + ": " + idx);
            }
          }
        }
      }
      polyIndex++;
    }

    // processing doorpolys
    for (final StructEntry se : getFields(Door.class)) {
      final Door door = (Door)se;
      for (final StructEntry se2 : door.getFields(Polygon.class)) {
        final Polygon poly = (Polygon)se2;
        if (calculateWallgroupIndices(mapWidth, mapHeight, poly, rect)) {
          for (int y = 0; y < rect.height; y++) {
            for (int x = 0; x < rect.width; x++) {
              final int idx = (rect.y + y) * wgPerRow + (rect.x + x);
              if (idx < wgTotal) {
                wallgroups.get(idx).add(polyIndex);
              } else {
                Logger.warn("Wallgroup index out of bounds for " + door.getName() + " > " + poly.getName() + ": " + idx);
              }
            }
          }
        }
        polyIndex++;
      }
    }

    int totalIndexCount = 0;
    for (final List<Integer> list : wallgroups) {
      totalIndexCount += list.size();
    }

    // adjusting number of wallgroup entries
    final List<StructEntry> wallgroupList = new ArrayList<>(getFields(Wallgroup.class));
    while (wallgroupList.size() != wallgroups.size()) {
      if (wallgroupList.size() > wallgroups.size()) {
        // remove entry
        final StructEntry entry = wallgroupList.remove(wallgroupList.size() - 1);
        removeDatatype((AddRemovable)entry, false);
      } else {
        // add entry
        final AddRemovable entry = new Wallgroup();
        addDatatype(entry);
        wallgroupList.add(entry);
      }
    }

    // adjusting number of polygon index entries
    final List<StructEntry> indexList = new ArrayList<>(getFields(IndexNumber.class));
    while (indexList.size() != totalIndexCount) {
      if (indexList.size() > totalIndexCount) {
        // remove entry
        final StructEntry entry = indexList.remove(indexList.size() - 1);
        removeDatatype((AddRemovable)entry, false);
      } else {
        // add entry
        final AddRemovable entry = new IndexNumber(2, WED_WALL_POLYGON_INDEX);
        addDatatype(entry);
        indexList.add(entry);
      }
    }

    // updating polygon index entries
    int curIndex = 0;
    for (int i = 0; i < wallgroups.size(); i++) {
      final List<Integer> indices = wallgroups.get(i);
      for (int j = 0, cnt = indices.size(); j < cnt; j++) {
        final IndexNumber number = (IndexNumber)indexList.get(curIndex);
        number.setValue(indices.get(j));
        curIndex++;
      }
    }

    // updating wallgroup entries
    int startIndex = 0;
    for (int i = 0; i < wallgroups.size(); i++) {
      final Wallgroup wg = (Wallgroup)wallgroupList.get(i);
      final List<Integer> indices = wallgroups.get(i);
      ((DecNumber)wg.getAttribute(Wallgroup.WED_WALLGROUP_POLYGON_INDEX)).setValue(startIndex);
      ((DecNumber)wg.getAttribute(Wallgroup.WED_WALLGROUP_NUM_POLYGONS)).setValue(indices.size());
      startIndex += indices.size();
    }
  }

  private void updateSectionOffsets(AddRemovable datatype, int size) {
    if (!(datatype instanceof Vertex)) {
      HexNumber offsetVertices = (HexNumber) getAttribute(WED_OFFSET_VERTICES);
      if (datatype.getOffset() <= offsetVertices.getValue()) {
        offsetVertices.incValue(size);
      }
    }
    if (!(datatype instanceof IndexNumber)) {
      HexNumber offsetDoorTileMap = (HexNumber) getAttribute(WED_OFFSET_DOOR_TILEMAP_LOOKUP);
      if (datatype.getOffset() <= offsetDoorTileMap.getValue()) {
        offsetDoorTileMap.incValue(size);
      }
    }

    for (final StructEntry o : getFields()) {
      if (o instanceof Overlay) {
        ((Overlay)o).updateOffsets(datatype, size);
      }
    }

    // Assumes polygon offset is correct
    int offset = ((IsNumeric) getAttribute(WED_OFFSET_WALL_POLYGONS)).getValue();
    offset += ((IsNumeric) getAttribute(WED_NUM_WALL_POLYGONS)).getValue() * 18;
    for (final StructEntry o : getFields()) {
      if (o instanceof Door) {
        ((Door) o).updatePolygonsOffset(offset);
      }
    }

    if (datatype instanceof Overlay) {
      // determining tilemap and tilemap lookup base offsets for the Overlay structure
      final Overlay overlay = (Overlay)datatype;
      int ofsTilemap = ((IsNumeric)getAttribute(WED_OFFSET_DOORS)).getValue();
      int ofsTilemapLookup = ((IsNumeric)getAttribute(WED_OFFSET_DOOR_TILEMAP_LOOKUP)).getValue();
      int lookupIndex = 0;
      // tracking door structures
      for (final StructEntry se : getFields(Door.class)) {
        final Door door = (Door)se;
        ofsTilemap = Math.max(ofsTilemap, door.getEndOffset());
        final int idx = ((IsNumeric)door.getAttribute(Door.WED_DOOR_TILEMAP_LOOKUP_INDEX)).getValue();
        final int cnt = ((IsNumeric)door.getAttribute(Door.WED_DOOR_NUM_TILEMAP_INDICES)).getValue();
        lookupIndex = Math.max(lookupIndex, idx + cnt);
      }
      // tracking previous overlay structures
      for (final StructEntry se : getFields(Overlay.class)) {
        if (se == overlay) {
          break;
        }
        final Overlay curOvl = (Overlay)se;
        final List<StructEntry> tmList = curOvl.getFields(Tilemap.class);
        for (final StructEntry se2 : tmList) {
          final Tilemap tm = (Tilemap)se2;
          lookupIndex += ((IsNumeric)tm.getAttribute(Tilemap.WED_TILEMAP_TILE_COUNT_PRI)).getValue();
        }
        ofsTilemap += tmList.size() * 10;
      }
      ofsTilemapLookup += lookupIndex * 2;
      ((SectionOffset)overlay.getAttribute(Overlay.WED_OVERLAY_OFFSET_TILEMAP)).setValue(ofsTilemap);
      ((SectionOffset)overlay.getAttribute(Overlay.WED_OVERLAY_OFFSET_TILEMAP_LOOKUP)).setValue(ofsTilemapLookup);
    }
  }

  private void updateVertices() {
    // Assumes vertices offset is correct
    int offset = ((IsNumeric) getAttribute(WED_OFFSET_VERTICES)).getValue();
    int count = 0;
    // processing polygons in the right order: wall polys > door polys
    for (final StructEntry o : getFields(WallPolygon.class)) {
      final Polygon polygon = (Polygon) o;
      int vertNum = polygon.updateVertices(offset, count);
      offset += 4 * vertNum;
      count += vertNum;
    }
    for (final StructEntry o : getFields(Door.class)) {
      final Door door = (Door) o;
      for (final StructEntry q : door.getFields(Polygon.class)) {
        final Polygon polygon = (Polygon) q;
        int vertNum = polygon.updateVertices(offset, count);
        offset += 4 * vertNum;
        count += vertNum;
      }
    }
  }

  /** Adds or removes the specified polygon index entry from the wallgroup section. */
  private void updateWallgroups(AddRemovable datatype, boolean removed) {
    if (!(datatype instanceof DecNumber)) {
      return;
    }

    // determine index of the added/removed wallpoly index entry
    final int ofsLookups = ((IsNumeric)getAttribute(WED_OFFSET_WALL_POLYGON_LOOKUP)).getValue();
    final int lookupIndex = (datatype.getOffset() - ofsLookups) / datatype.getSize();

    // adjust wallgroup entries
    int adjust = 0;
    for (final StructEntry se : getFields(Wallgroup.class)) {
      final Wallgroup wg = (Wallgroup)se;
      final int startIndex = ((IsNumeric)wg.getAttribute(Wallgroup.WED_WALLGROUP_POLYGON_INDEX)).getValue();
      final int indexCount = ((IsNumeric)wg.getAttribute(Wallgroup.WED_WALLGROUP_NUM_POLYGONS)).getValue();
      if (adjust == 0) {
        // add or remove index to wallgroup
        if (removed) {
          if (indexCount > 0 && lookupIndex >= startIndex && lookupIndex < startIndex + indexCount) {
            adjust = -1;
          }
        } else {
          if (lookupIndex >= startIndex && lookupIndex <= startIndex + indexCount) {
            adjust = 1;
          }
        }
        ((DecNumber)wg.getAttribute(Wallgroup.WED_WALLGROUP_NUM_POLYGONS)).setValue(indexCount + adjust);
      } else {
        // adjusting start indices in subsequent wallgroup entries
        ((DecNumber)wg.getAttribute(Wallgroup.WED_WALLGROUP_POLYGON_INDEX)).setValue(startIndex + adjust);
      }
    }
  }

  /**
   * Calculates the bounding box for the vertices in the given polygon.
   *
   * @param mapWidth  Total map width, in pixels.
   * @param mapHeight Total map height, in pixels.
   * @param poly      {@link Polygon} structure to scan.
   * @param rect      {@link Rectangle} that is populated with the region of covered wallgroup indices.
   * @return {@code true} if the polygon contains a valid bounding box, {@code false} otherwise.
   */
  private static boolean calculateWallgroupIndices(int mapWidth, int mapHeight, Polygon poly, Rectangle rect) {
    if (poly == null) {
      return false;
    }

    final int vertexCount = ((IsNumeric)poly.getAttribute(Polygon.WED_POLY_NUM_VERTICES)).getValue();
    if (vertexCount <= 0) {
      return false;
    }

    int minX = mapWidth;
    int minY = mapHeight;
    int maxX = 0;
    int maxY = 0;
    for (final StructEntry se2 : poly.getFields(Vertex.class)) {
      final Vertex vertex = (Vertex)se2;
      minX = Math.min(minX, vertex.getX());
      maxX = Math.max(maxX, vertex.getX());
      minY = Math.min(minY, vertex.getY());
      maxY = Math.max(maxY, vertex.getY());
    }
    minX = Math.max(minX, 0);
    maxX = Math.min(maxX, mapWidth - 1);
    minY = Math.max(minY, 0);
    maxY = Math.min(maxY, mapHeight - 1);
    final boolean valid = (maxX - minX) * (maxY - minY) > 0;
    if (valid && rect != null) {
      final int wgMinX = minX / 640;
      final int wgMaxX = maxX / 640;
      final int wgMinY = minY / 480;
      final int wgMaxY = maxY / 480;
      rect.x = wgMinX;
      rect.y = wgMinY;
      rect.width = wgMaxX - wgMinX + 1;
      rect.height = wgMaxY - wgMinY + 1;
    }

    return valid;
  }

  protected static void updatePolygon(AbstractStruct wed, AddRemovable datatype) {
    if (wed == null || !(datatype instanceof Polygon)) {
      return;
    }

    int index = 0;
    // scanning wall polygons
    for (final StructEntry se : wed.getFields(WallPolygon.class)) {
      final WallPolygon poly = (WallPolygon)se;
      if (poly == datatype) {
        ((DecNumber)poly.getAttribute(Polygon.WED_POLY_VERTEX_INDEX)).setValue(index);
        return;
      }
      final int idx = ((IsNumeric)poly.getAttribute(Polygon.WED_POLY_VERTEX_INDEX)).getValue();
      final int cnt = ((IsNumeric)poly.getAttribute(Polygon.WED_POLY_NUM_VERTICES)).getValue();
      index = Math.max(index, idx + cnt);
    }

    // scanning door polygons
    for (final StructEntry se : wed.getFields(Door.class)) {
      final Door door = (Door)se;
      // scanning open/closed door polygons
      for (final StructEntry se2 : door.getFields(Polygon.class)) {
        final Polygon poly = (Polygon)se2;
        if (poly == datatype) {
          ((DecNumber)poly.getAttribute(Polygon.WED_POLY_VERTEX_INDEX)).setValue(index);
          return;
        }
        final int idx = ((IsNumeric)poly.getAttribute(Polygon.WED_POLY_VERTEX_INDEX)).getValue();
        final int cnt = ((IsNumeric)poly.getAttribute(Polygon.WED_POLY_NUM_VERTICES)).getValue();
        index = Math.max(index, idx + cnt);
      }
    }
  }
}
