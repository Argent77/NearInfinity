// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.are;

import java.awt.Component;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.HexNumber;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.IsReference;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.UnsignDecNumber;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.are.viewer.AreaViewer;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.vertex.Vertex;
import org.infinity.resource.wmp.AreaEntry;
import org.infinity.resource.wmp.WmpResource;
import org.infinity.search.SearchOptions;
import org.infinity.util.IdsMapCache;
import org.infinity.util.Logger;
import org.infinity.util.LuaEntry;
import org.infinity.util.LuaParser;
import org.infinity.util.StringTable;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.StreamUtils;

/**
 * The ARE resource describes the content of an area (rather than its visual representation). ARE files contain the list
 * of {@link Actor actors}, {@link Item items}, {@link Entrance entrances and exits}, {@link SpawnPoint spawn points}
 * and other area-associated info.
 * <p>
 * The ARE resource may contain references to other files, e.g. the list of items in a {@link Container container} is
 * stored in the ARE file, however the files themselves are not embedded in the ARE file.
 */
public final class AreResource extends AbstractStruct implements Resource, HasChildStructs, HasViewerTabs {
  // ARE-specific field labels
  public static final String ARE_WED_RESOURCE             = "WED resource";
  public static final String ARE_LAST_SAVED               = "Last saved";
  public static final String ARE_AREA_TYPE                = "Area type";
  public static final String ARE_AREA_NORTH               = "Area north";
  public static final String ARE_AREA_EAST                = "Area east";
  public static final String ARE_AREA_SOUTH               = "Area south";
  public static final String ARE_AREA_WEST                = "Area west";
  public static final String ARE_EDGE_FLAGS_NORTH         = "Edge flags north";
  public static final String ARE_EDGE_FLAGS_EAST          = "Edge flags east";
  public static final String ARE_EDGE_FLAGS_SOUTH         = "Edge flags south";
  public static final String ARE_EDGE_FLAGS_WEST          = "Edge flags west";
  public static final String ARE_LOCATION                 = "Location";
  public static final String ARE_PROBABILITY_RAIN         = "Rain probability";
  public static final String ARE_PROBABILITY_SNOW         = "Snow probability";
  public static final String ARE_PROBABILITY_FOG          = "Fog probability";
  public static final String ARE_PROBABILITY_LIGHTNING    = "Lightning probability";
  public static final String ARE_WIND_SPEED               = "Wind speed";
  public static final String ARE_OVERLAY_TRANSPARENCY     = "Overlay transparency";
  public static final String ARE_AREA_DIFFICULTY_2        = "Area difficulty 2";
  public static final String ARE_AREA_DIFFICULTY_3        = "Area difficulty 3";
  public static final String ARE_AREA_CUR_DIFFICULTY      = "Current area difficulty";  // TODO: confirm!
  public static final String ARE_OFFSET_ACTORS            = "Actors offset";
  public static final String ARE_OFFSET_TRIGGERS          = "Triggers offset";
  public static final String ARE_OFFSET_SPAWN_POINTS      = "Spawn points offset";
  public static final String ARE_OFFSET_ENTRANCES         = "Entrances offset";
  public static final String ARE_OFFSET_CONTAINERS        = "Containers offset";
  public static final String ARE_OFFSET_ITEMS             = "Items offset";
  public static final String ARE_OFFSET_VERTICES          = "Vertices offset";
  public static final String ARE_OFFSET_AMBIENTS          = "Ambients offset";
  public static final String ARE_OFFSET_VARIABLES         = "Variables offset";
  public static final String ARE_OFFSET_OBJECT_FLAGS      = "Object flags offset";
  public static final String ARE_OFFSET_EXPLORED_BITMAP   = "Explored bitmap offset";
  public static final String ARE_OFFSET_DOORS             = "Doors offset";
  public static final String ARE_OFFSET_ANIMATIONS        = "Animations offset";
  public static final String ARE_OFFSET_TILED_OBJECTS     = "Tiled objects offset";
  public static final String ARE_OFFSET_SONGS             = "Songs offset";
  public static final String ARE_OFFSET_REST_ENCOUNTERS   = "Rest encounters offset";
  public static final String ARE_OFFSET_AUTOMAP_NOTES     = "Automap notes offset";
  public static final String ARE_OFFSET_PROJECTILE_TRAPS  = "Projectile traps offset";
  public static final String ARE_NUM_ACTORS               = "# actors";
  public static final String ARE_NUM_TRIGGERS             = "# triggers";
  public static final String ARE_NUM_SPAWN_POINTS         = "# spawn points";
  public static final String ARE_NUM_ENTRANCES            = "# entrances";
  public static final String ARE_NUM_CONTAINERS           = "# containers";
  public static final String ARE_NUM_ITEMS                = "# items";
  public static final String ARE_NUM_VERTICES             = "# vertices";
  public static final String ARE_NUM_AMBIENTS             = "# ambients";
  public static final String ARE_NUM_VARIABLES            = "# variables";
  public static final String ARE_NUM_OBJECT_FLAGS         = "# object flags";
  public static final String ARE_NUM_DOORS                = "# doors";
  public static final String ARE_NUM_ANIMATIONS           = "# animations";
  public static final String ARE_NUM_TILED_OBJECTS        = "# tiled objects";
  public static final String ARE_NUM_AUTOMAP_NOTES        = "# automap notes";
  public static final String ARE_NUM_PROJECTILE_TRAPS     = "# projectile traps";
  public static final String ARE_SIZE_EXPLORED_BITMAP     = "Explored bitmap size";
  public static final String ARE_AREA_SCRIPT              = "Area script";
  public static final String ARE_REST_MOVIE_DAY           = "Rest movie (day)";
  public static final String ARE_REST_MOVIE_NIGHT         = "Rest movie (night)";
  public static final String ARE_EXPLORED_BITMAP          = "Explored bitmap";
  public static final String ARE_AREA_INI_FILE            = "Area INI";

  public static final String[] FLAGS_ARRAY = { "Indoors", "Outdoors", "Day/Night", "Weather", "City", "Forest",
      "Dungeon", "Extended night", "Can rest indoors" };

  public static final String[] FLAGS_TORMENT_ARRAY = { "Indoors", "Hive", null, "Clerk's ward", "Lower ward",
      "Ravel's maze", "Baator", "Rubikon", "Fortress of Regrets", "Curst", "Carceri", "Allow day/night" };

  public static final String[] ATYPE_ARRAY = { "Normal", "Save not allowed", "Tutorial area", "Dead magic zone",
      "Dream area" };

  public static final String[] ATYPE_EE_ARRAY = { "Normal", "Save not allowed", "Tutorial area", "Dead magic zone",
      "Dream area", "Player1 can die;Allows death of party leader without ending the game", "Rest not allowed",
      "Travel not allowed" };

  public static final String[] ATYPE_TORMENT_ARRAY = { "Normal", "Save not allowed",
      "\"You cannot rest here.\";Combined with bit 2: \"You must obtain permission to rest here.\"",
      "\"Too dangerous to rest.\";Combined with bit 1: \"You must obtain permission to rest here.\"" };

  public static final String[] ATYPE_PSTEE_ARRAY = { "Normal", "Save not allowed", "Tutorial area",
      "Dead magic zone", "Dream area", "Player1 can die;The Nameless One can die without ending the game",
      "Rest not allowed", "Travel not allowed",
      "\"You cannot rest here.\";Combined with bit 8: \"You must obtain permission to rest here.\"",
      "\"Too dangerous to rest.\";Combined with bit 7: \"You must obtain permission to rest here.\"" };

  public static final String[] ATYPE_IWD2_ARRAY = { "Normal", "Can't save game", "Cannot rest", "Lock battle music" };

  public static final String[] EDGE_ARRAY = { "No flags set", "Party required", "Party enabled" };

  private static HashMap<String, String> mapNames = null; // Map ARE resref -> description

  private StructHexViewer hexViewer;
  private AreaViewer areaViewer;

  /**
   * Returns localized name of the area. If such resource does not exists, or not contains mapping for the specified
   * area, returns {@code null}.
   *
   * @param entry Pointer to ARE resource. If {@code null}, method returns {@code null}
   * @return String with localized name of the area from male talk table
   */
  public static String getSearchString(ResourceEntry entry) {
    String retVal = null;
    if (entry != null && BrowserMenuBar.isInstantiated() && BrowserMenuBar.getInstance().getOptions().showTreeSearchNames()) {
      retVal = getMapName(entry.getResourceName());
    }
    return retVal;
  }

  /** Clears cached area names. */
  public static void clearCache() {
    if (mapNames != null) {
      mapNames.clear();
      mapNames = null;
    }
  }

  // Returns descriptive name of specified ARE resref if available, null otherwise.
  private static String getMapName(String resref) {
    String retVal = null;
    initMapNames(false);
    if (mapNames == null) {
      return retVal;
    }

    if (resref != null) {
      if (resref.lastIndexOf('.') > 0) {
        resref = resref.substring(0, resref.lastIndexOf('.'));
      }
      resref = resref.toUpperCase();
      retVal = mapNames.getOrDefault(resref, null);
    }
    return retVal;
  }

  // Initializes search names for ARE resources if available
  private static void initMapNames(boolean force) {
    if (mapNames == null || force) {
      mapNames = null;
      if (Profile.isEnhancedEdition() && ResourceFactory.resourceExists("BGEE.LUA")) {
        // Enhanced Edition 2.0+ map names
        try {
          // getting all cheatAreas* tables from BGEE.LUA and the various L_xx_YY.LUA files
          List<ResourceEntry> luaFiles = ResourceFactory.getResources(Pattern.compile("L_.*\\.LUA"));
          luaFiles.add(ResourceFactory.getResourceEntry("BGEE.LUA"));
          LuaEntry entries = LuaParser.Parse(luaFiles, "cheatAreas\\w*", false);
          mapNames = createMapNamesFromLua(entries);
        } catch (Exception e) {
          Logger.error(e);
        }
      } else if (ResourceFactory.resourceExists("MAPNAME.2DA")) {
        // PST map names
        mapNames = createMapNamesFromTable();
      } else {
        // try getting map names from worldmaps
        mapNames = createMapNamesFromWorldmap();
      }
    }
  }

  // Decodes area code -> area name entries from specified LuaEntry structure
  private static HashMap<String, String> createMapNamesFromLua(LuaEntry root) {
    if (root == null || root.children.isEmpty()) {
      return null;
    }

    HashMap<String, String> retVal = new HashMap<>();
    for (LuaEntry table : root.children) {
      for (LuaEntry area : table.children) {
        if (area.children != null && area.children.size() >= 2) {
          Object areaCode = area.children.get(0).value;
          Object areaName = area.children.get(1).value;

          // PSTEE-specific: area name is provided as strref
          if (areaName instanceof Integer) {
            areaName = StringTable.getStringRef((Integer) areaName);
          }

          if (areaCode != null && areaName != null && !areaName.toString().isEmpty()) {
            retVal.put(areaCode.toString().toUpperCase(), areaName.toString());
          }
        }
      }
    }
    return retVal;
  }

  // Initializes PST map names
  private static HashMap<String, String> createMapNamesFromTable() {
    HashMap<String, String> retVal = null;

    Table2da table = Table2daCache.get("MAPNAME.2DA");
    if (table != null) {
      retVal = new HashMap<>(table.getRowCount());
      for (int row = 0, cnt = table.getRowCount(); row < cnt; row++) {
        String resref = table.get(row, 0);
        String desc = null;
        try {
          int strref = Integer.parseInt(table.get(row, 1));
          desc = StringTable.getStringRef(strref);
        } catch (NumberFormatException e) {
          Logger.warn(e);
        }
        if (!Objects.equals(resref, table.getDefaultValue()) && desc != null) {
          retVal.put(resref.toUpperCase(), desc);
        }
      }
    }
    return retVal;
  }

  // Collect area names from worldmaps
  private static HashMap<String, String> createMapNamesFromWorldmap() {
    HashMap<String, String> retVal = new HashMap<>();
    List<ResourceEntry> wmpList = ResourceFactory.getResources("WMP", Collections.emptyList());
    if (wmpList != null) {
      for (ResourceEntry wmpEntry : wmpList) {
        try {
          WmpResource wmp = (WmpResource) ResourceFactory.getResource(wmpEntry);
          if (wmp != null) {
            List<StructEntry> mapList = wmp.getFields(org.infinity.resource.wmp.MapEntry.class);
            for (StructEntry mapEntry : mapList) {
              List<StructEntry> areaList = ((AbstractStruct) mapEntry).getFields(AreaEntry.class);
              for (StructEntry areaEntry : areaList) {
                AreaEntry area = (AreaEntry) areaEntry;

                String resref = ((IsReference) area.getAttribute(AreaEntry.WMP_AREA_CURRENT)).getResourceName();
                if (resref == null || resref.isEmpty()) {
                  continue;
                }
                int pos = resref.lastIndexOf('.');
                if (pos > 0) {
                  resref = resref.substring(0, pos);
                }
                resref = resref.toUpperCase();

                int strref = ((IsNumeric) area.getAttribute(AreaEntry.WMP_AREA_TOOLTIP)).getValue();
                if (!StringTable.isValidStringRef(strref)) {
                  strref = ((IsNumeric) area.getAttribute(AreaEntry.WMP_AREA_NAME)).getValue();
                }
                if (StringTable.isValidStringRef(strref)) {
                  String name = StringTable.getStringRef(strref);
                  if (!name.isEmpty()) {
                    retVal.put(resref, name);
                  }
                }
              }
            }
          }
        } catch (Exception e) {
          // no need to report anything
        }
      }
    }
    return retVal;
  }

  public static void addScriptNames(Set<String> scriptNames, ByteBuffer buffer) {
    int offset = 0;
    if (StreamUtils.readString(buffer, 4, 4).equalsIgnoreCase("V9.1")) {
      offset = 16;
    }

    // Actors
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 84), buffer.getShort(offset + 88), 272,
        Profile.isEnhancedEdition() || Profile.getEngine() == Profile.Engine.BG2); // needed?

    // ITEPoints
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 92), buffer.getShort(offset + 90), 196, false);

    // Spawnpoints
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 96), buffer.getInt(offset + 100), 200, false);

    // Entrances
    // addScriptNames(scriptNames, buffer, buffer.getInt(offset + 104),
    // buffer.getInt(offset + 108), 104, false);

    // Containers
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 112), buffer.getShort(offset + 116), 192, false);

    // Ambients
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 132), buffer.getShort(offset + 130), 212, false);

    // Variables
    // addScriptNames(scriptNames, buffer, buffer.getInt(offset + 136),
    // buffer.getInt(offset + 140), 84, false);

    // Doors
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 168), buffer.getInt(offset + 164), 200, false);

    // Animations
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 176), buffer.getInt(offset + 172), 76, false);

    // Tiled objects
    addScriptNames(scriptNames, buffer, buffer.getInt(offset + 184), buffer.getInt(offset + 180), 108, false);

    // Rest spawn
    // addScriptNames(scriptNames, buffer, DynamicArray.getInt(buffer, offset + 192), 1, 228, false);
  }

  private static void addScriptNames(Set<String> scriptNames, ByteBuffer buffer, int offset, int count, int size,
      boolean checkOverride) {
    for (int i = 0; i < count; i++) {
      int curOfs = offset + i * size;
      // Bit 3 of "Flags" field determines whether to override the actor's script name
      if (!checkOverride || ((buffer.get(curOfs + 40) & 8) == 8)) {
        StringBuilder sb = new StringBuilder(32);
        for (int j = 0; j < 32; j++) {
          byte b = buffer.get(curOfs + j);
          if (b == 0x00) {
            break;
          } else if (b != 0x20) { // Space
            sb.append(Character.toLowerCase((char) b));
          }
        }
        synchronized (scriptNames) {
          scriptNames.add(sb.toString());
        }
      }
    }
  }

  public AreResource(ResourceEntry entry) throws Exception {
    super(entry);
  }

  @Override
  public void close() throws Exception {
    super.close();
    if (areaViewer != null) {
      areaViewer.close();
      areaViewer = null;
    }
  }

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    if (Profile.getEngine() == Profile.Engine.PST) {
      return new AddRemovable[] { new Actor(), new ITEPoint(), new SpawnPoint(), new Entrance(), new Container(),
          new Ambient(), new Variable(), new Door(), new Animation(), new TiledObject(), new AutomapNotePST(),
          new Song(), new RestSpawn() };
    } else if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
      return new AddRemovable[] { new Actor(), new ITEPoint(), new SpawnPoint(), new Entrance(), new Container(),
          new Ambient(), new Variable(), new Door(), new Animation(), new TiledObject(), new AutomapNote(),
          new ProTrap(), new Song(), new RestSpawn() };
    } else {
      return new AddRemovable[] { new Actor(), new ITEPoint(), new SpawnPoint(), new Entrance(), new Container(),
          new Ambient(), new Variable(), new Door(), new Animation(), new TiledObject(), new Song(), new RestSpawn() };
    }
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception {
    if (entry instanceof Song) {
      // only one instance allowed
      final IsNumeric songOffset = (IsNumeric) getAttribute(ARE_OFFSET_SONGS);
      if (songOffset != null && songOffset.getValue() > 0 && getAttribute(songOffset.getValue(), Song.class) != null) {
        throw new Exception(Song.ARE_SONGS + " entry already exists.");
      }
    } else if (entry instanceof RestSpawn) {
      // only one instance allowed
      final IsNumeric restOffset = (IsNumeric) getAttribute(ARE_OFFSET_REST_ENCOUNTERS);
      if (restOffset != null && restOffset.getValue() > 0 && getAttribute(restOffset.getValue(), RestSpawn.class) != null) {
        throw new Exception(RestSpawn.ARE_RESTSPAWN + " entry already exists.");
      }
    }
    return entry;
  }

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
      case 0: // view tab
      {
        JScrollPane scroll = new JScrollPane(new Viewer(this));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
      }
      case 1: // raw tab
      {
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

  @Override
  public void write(OutputStream os) throws IOException {
    super.writeFlatFields(os);
  }

  @Override
  protected void viewerInitialized(StructViewer viewer) {
    viewer.addTabChangeListener(hexViewer);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype) {
    HexNumber offsetVertices = (HexNumber) getAttribute(ARE_OFFSET_VERTICES);
    if (datatype.getOffset() <= offsetVertices.getValue()) {
      offsetVertices.incValue(datatype.getSize());
    }
    HexNumber offsetItems = (HexNumber) getAttribute(ARE_OFFSET_ITEMS);
    if (datatype.getOffset() <= offsetItems.getValue()) {
      offsetItems.incValue(datatype.getSize());
    }

    if (datatype instanceof HasVertices) {
      updateVertices();
    }
    if (datatype instanceof Container) {
      updateItems();
    }
    updateActorCREOffsets();
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype) {
    if (datatype instanceof Vertex) {
      updateVertices();
    } else {
      HexNumber offsetVertices = (HexNumber) getAttribute(ARE_OFFSET_VERTICES);
      if (datatype.getOffset() <= offsetVertices.getValue()) {
        offsetVertices.incValue(datatype.getSize());
        updateVertices();
      }
    }
    if (datatype instanceof Item) {
      updateItems();
    } else {
      HexNumber offsetItems = (HexNumber) getAttribute(ARE_OFFSET_ITEMS);
      if (datatype.getOffset() <= offsetItems.getValue()) {
        offsetItems.incValue(datatype.getSize());
        updateItems();
      }
    }
    updateActorCREOffsets();
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype) {
    HexNumber offsetVertices = (HexNumber) getAttribute(ARE_OFFSET_VERTICES);
    if (datatype.getOffset() < offsetVertices.getValue()) {
      offsetVertices.incValue(-datatype.getSize());
    }
    HexNumber offsetItems = (HexNumber) getAttribute(ARE_OFFSET_ITEMS);
    if (datatype.getOffset() < offsetItems.getValue()) {
      offsetItems.incValue(-datatype.getSize());
    }

    if (datatype instanceof HasVertices) {
      updateVertices();
    }
    if (datatype instanceof Container) {
      updateItems();
    }
    updateActorCREOffsets();
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype) {
    if (datatype instanceof Vertex) {
      updateVertices();
    } else {
      HexNumber offsetVertices = (HexNumber) getAttribute(ARE_OFFSET_VERTICES);
      if (datatype.getOffset() < offsetVertices.getValue()) {
        offsetVertices.incValue(-datatype.getSize());
        updateVertices();
      }
    }
    if (datatype instanceof Item) {
      updateItems();
    } else {
      HexNumber offsetItems = (HexNumber) getAttribute(ARE_OFFSET_ITEMS);
      if (datatype.getOffset() < offsetItems.getValue()) {
        offsetItems.incValue(-datatype.getSize());
        updateItems();
      }
    }
    updateActorCREOffsets();
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  public void removeDatatype(AddRemovable removedEntry, boolean removeRecurse) {
    if (removedEntry instanceof Actor) {
      final Actor actor = (Actor) removedEntry;
      if (!((Flag) actor.getAttribute(Actor.ARE_ACTOR_FLAGS)).isFlagSet(0)) {
        // remove embedded CRE resource manually
        final int creOfs = ((IsNumeric) actor.getAttribute(Actor.ARE_ACTOR_OFFSET_CRE_STRUCTURE)).getValue();
        final int creSize = ((IsNumeric) actor.getAttribute(Actor.ARE_ACTOR_SIZE_CRE_STRUCTURE)).getValue();
        final StructEntry se = actor.getAttribute(Actor.ARE_ACTOR_CRE_FILE);
        if (se instanceof CreResource && creOfs > 0 && creSize > 0) {
          actor.removeDatatype((CreResource) se, false);
        }
      }
    }
    super.removeDatatype(removedEntry, removeRecurse);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new TextString(buffer, offset, 4, COMMON_SIGNATURE));
    TextString version = new TextString(buffer, offset + 4, 4, COMMON_VERSION);
    addField(version);
    addField(new ResourceRef(buffer, offset + 8, ARE_WED_RESOURCE, "WED"));
    addField(new DecNumber(buffer, offset + 16, 4, ARE_LAST_SAVED));
    if (version.toString().equalsIgnoreCase("V9.1")) {
      addField(new Flag(buffer, offset + 20, 4, ARE_AREA_TYPE,
          IdsMapCache.getUpdatedIdsFlags(ATYPE_IWD2_ARRAY, "AREAFLAG.IDS", 4, false, false)));
    } else if (Profile.getEngine() == Profile.Engine.PST) {
      addField(new Flag(buffer, offset + 20, 4, ARE_AREA_TYPE,
          IdsMapCache.getUpdatedIdsFlags(ATYPE_TORMENT_ARRAY, null, 4, false, false)));
    } else if (Profile.getGame() == Profile.Game.PSTEE) {
      addField(new Flag(buffer, offset + 20, 4, ARE_AREA_TYPE,
          IdsMapCache.getUpdatedIdsFlags(ATYPE_PSTEE_ARRAY, null, 4, false, false)));
    } else if (Profile.isEnhancedEdition()) {
      addField(new Flag(buffer, offset + 20, 4, ARE_AREA_TYPE,
          IdsMapCache.getUpdatedIdsFlags(ATYPE_EE_ARRAY, "AREAFLAG.IDS", 4, false, false)));
    } else {
      addField(new Flag(buffer, offset + 20, 4, ARE_AREA_TYPE,
          IdsMapCache.getUpdatedIdsFlags(ATYPE_ARRAY, "AREAFLAG.IDS", 4, false, false)));
    }
    addField(new ResourceRef(buffer, offset + 24, ARE_AREA_NORTH, "ARE"));
    addField(new Flag(buffer, offset + 32, 4, ARE_EDGE_FLAGS_NORTH, EDGE_ARRAY));
    addField(new ResourceRef(buffer, offset + 36, ARE_AREA_EAST, "ARE"));
    addField(new Flag(buffer, offset + 44, 4, ARE_EDGE_FLAGS_EAST, EDGE_ARRAY));
    addField(new ResourceRef(buffer, offset + 48, ARE_AREA_SOUTH, "ARE"));
    addField(new Flag(buffer, offset + 56, 4, ARE_EDGE_FLAGS_SOUTH, EDGE_ARRAY));
    addField(new ResourceRef(buffer, offset + 60, ARE_AREA_WEST, "ARE"));
    addField(new Flag(buffer, offset + 68, 4, ARE_EDGE_FLAGS_WEST, EDGE_ARRAY));
    if (Profile.getEngine() == Profile.Engine.PST || Profile.getGame() == Profile.Game.PSTEE) {
      addField(new Flag(buffer, offset + 72, 2, ARE_LOCATION,
          IdsMapCache.getUpdatedIdsFlags(FLAGS_TORMENT_ARRAY, null, 2, false, false)));
    } else {
      addField(new Flag(buffer, offset + 72, 2, ARE_LOCATION,
          IdsMapCache.getUpdatedIdsFlags(FLAGS_ARRAY, "AREATYPE.IDS", 2, false, false)));
    }
    addField(new DecNumber(buffer, offset + 74, 2, ARE_PROBABILITY_RAIN));
    addField(new DecNumber(buffer, offset + 76, 2, ARE_PROBABILITY_SNOW));
    addField(new DecNumber(buffer, offset + 78, 2, ARE_PROBABILITY_FOG));
    addField(new DecNumber(buffer, offset + 80, 2, ARE_PROBABILITY_LIGHTNING));
    if (Profile.isEnhancedEdition()) {
      addField(new UnsignDecNumber(buffer, offset + 82, 1, ARE_OVERLAY_TRANSPARENCY));
      addField(new Unknown(buffer, offset + 83, 1));
    } else {
      addField(new DecNumber(buffer, offset + 82, 2, ARE_WIND_SPEED));
    }
    if (version.toString().equalsIgnoreCase("V9.1")) {
      addField(new DecNumber(buffer, offset + 84, 1, ARE_AREA_DIFFICULTY_2));
      addField(new DecNumber(buffer, offset + 85, 1, ARE_AREA_DIFFICULTY_3));
      addField(new DecNumber(buffer, offset + 86, 2, ARE_AREA_CUR_DIFFICULTY));
      addField(new Unknown(buffer, offset + 88, 12));
      offset += 16;
    }
    SectionOffset offsetActors = new SectionOffset(buffer, offset + 84, ARE_OFFSET_ACTORS, Actor.class);
    addField(offsetActors);
    SectionCount countActors = new SectionCount(buffer, offset + 88, 2, ARE_NUM_ACTORS, Actor.class);
    addField(countActors);
    SectionCount countItePoints = new SectionCount(buffer, offset + 90, 2, ARE_NUM_TRIGGERS, ITEPoint.class);
    addField(countItePoints);
    SectionOffset offsetItePoints = new SectionOffset(buffer, offset + 92, ARE_OFFSET_TRIGGERS, ITEPoint.class);
    addField(offsetItePoints);
    SectionOffset offsetSpawnPoints = new SectionOffset(buffer, offset + 96, ARE_OFFSET_SPAWN_POINTS, SpawnPoint.class);
    addField(offsetSpawnPoints);
    SectionCount countSpawnPoints = new SectionCount(buffer, offset + 100, 4, ARE_NUM_SPAWN_POINTS, SpawnPoint.class);
    addField(countSpawnPoints);
    SectionOffset offsetEntrances = new SectionOffset(buffer, offset + 104, ARE_OFFSET_ENTRANCES, Entrance.class);
    addField(offsetEntrances);
    SectionCount countEntrances = new SectionCount(buffer, offset + 108, 4, ARE_NUM_ENTRANCES, Entrance.class);
    addField(countEntrances);
    SectionOffset offsetContainers = new SectionOffset(buffer, offset + 112, ARE_OFFSET_CONTAINERS, Container.class);
    addField(offsetContainers);
    SectionCount countContainers = new SectionCount(buffer, offset + 116, 2, ARE_NUM_CONTAINERS, Container.class);
    addField(countContainers);
    DecNumber countItems = new DecNumber(buffer, offset + 118, 2, ARE_NUM_ITEMS);
    addField(countItems);
    HexNumber offsetItems = new HexNumber(buffer, offset + 120, 4, ARE_OFFSET_ITEMS);
    addField(offsetItems);
    HexNumber offsetVertices = new HexNumber(buffer, offset + 124, 4, ARE_OFFSET_VERTICES);
    addField(offsetVertices);
    DecNumber countVertices = new DecNumber(buffer, offset + 128, 2, ARE_NUM_VERTICES);
    addField(countVertices);
    SectionCount countAmbients = new SectionCount(buffer, offset + 130, 2, ARE_NUM_AMBIENTS, Ambient.class);
    addField(countAmbients);
    SectionOffset offsetAmbients = new SectionOffset(buffer, offset + 132, ARE_OFFSET_AMBIENTS, Ambient.class);
    addField(offsetAmbients);
    SectionOffset offsetVariables = new SectionOffset(buffer, offset + 136, ARE_OFFSET_VARIABLES, Variable.class);
    addField(offsetVariables);
    SectionCount countVariables = new SectionCount(buffer, offset + 140, 2, ARE_NUM_VARIABLES, Variable.class);
    addField(countVariables);
    addField(new DecNumber(buffer, offset + 142, 2, ARE_NUM_OBJECT_FLAGS));
    addField(new HexNumber(buffer, offset + 144, 4, ARE_OFFSET_OBJECT_FLAGS));
    addField(new ResourceRef(buffer, offset + 148, ARE_AREA_SCRIPT, "BCS"));
    SectionCount sizeExploredBitmap = new SectionCount(buffer, offset + 156, 4, ARE_SIZE_EXPLORED_BITMAP,
        Explored.class);
    addField(sizeExploredBitmap);
    SectionOffset offsetExploredBitmap = new SectionOffset(buffer, offset + 160, ARE_OFFSET_EXPLORED_BITMAP,
        Explored.class);
    addField(offsetExploredBitmap);
    SectionCount countDoors = new SectionCount(buffer, offset + 164, 4, ARE_NUM_DOORS, Door.class);
    addField(countDoors);
    SectionOffset offsetDoors = new SectionOffset(buffer, offset + 168, ARE_OFFSET_DOORS, Door.class);
    addField(offsetDoors);
    SectionCount countAnimations = new SectionCount(buffer, offset + 172, 4, ARE_NUM_ANIMATIONS, Animation.class);
    addField(countAnimations);
    SectionOffset offsetAnimations = new SectionOffset(buffer, offset + 176, ARE_OFFSET_ANIMATIONS, Animation.class);
    addField(offsetAnimations);
    SectionCount countTiledObjects = new SectionCount(buffer, offset + 180, 4, ARE_NUM_TILED_OBJECTS,
        TiledObject.class);
    addField(countTiledObjects);
    SectionOffset offsetTiledObjects = new SectionOffset(buffer, offset + 184, ARE_OFFSET_TILED_OBJECTS,
        TiledObject.class);
    addField(offsetTiledObjects);
    SectionOffset offsetSongs = new SectionOffset(buffer, offset + 188, ARE_OFFSET_SONGS, Song.class);
    addField(offsetSongs);
    SectionOffset offsetRest = new SectionOffset(buffer, offset + 192, ARE_OFFSET_REST_ENCOUNTERS, RestSpawn.class);
    addField(offsetRest);

    SectionOffset offsetAutomapNote = null, offsetProTrap = null;
    SectionCount countAutomapNote = null, countProTrap = null;
    if (Profile.getEngine() == Profile.Engine.PST) {
      addField(new Unknown(buffer, offset + 196, 4));
      offsetAutomapNote = new SectionOffset(buffer, offset + 200, ARE_OFFSET_AUTOMAP_NOTES, AutomapNotePST.class);
      addField(offsetAutomapNote);
      countAutomapNote = new SectionCount(buffer, offset + 204, 4, ARE_NUM_AUTOMAP_NOTES, AutomapNotePST.class);
      addField(countAutomapNote);
      addField(new Unknown(buffer, offset + 208, 76));
    } else if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
      offsetAutomapNote = new SectionOffset(buffer, offset + 196, ARE_OFFSET_AUTOMAP_NOTES, AutomapNote.class);
      addField(offsetAutomapNote);
      countAutomapNote = new SectionCount(buffer, offset + 200, 4, ARE_NUM_AUTOMAP_NOTES, AutomapNote.class);
      addField(countAutomapNote);
      offsetProTrap = new SectionOffset(buffer, offset + 204, ARE_OFFSET_PROJECTILE_TRAPS, ProTrap.class);
      addField(offsetProTrap);
      countProTrap = new SectionCount(buffer, offset + 208, 4, ARE_NUM_PROJECTILE_TRAPS, ProTrap.class);
      addField(countProTrap);
      final String[] movieExt = (Profile.isEnhancedEdition()) ? new String[] { "MVE", "WBM" } : new String[] { "MVE" };
      addField(new ResourceRef(buffer, offset + 212, ARE_REST_MOVIE_DAY, movieExt));
      addField(new ResourceRef(buffer, offset + 220, ARE_REST_MOVIE_NIGHT, movieExt));
      addField(new Unknown(buffer, offset + 228, 56));
    } else if (Profile.getEngine() == Profile.Engine.IWD2) {
      offsetAutomapNote = new SectionOffset(buffer, offset + 196, ARE_OFFSET_AUTOMAP_NOTES, AutomapNote.class);
      addField(offsetAutomapNote);
      countAutomapNote = new SectionCount(buffer, offset + 200, 4, ARE_NUM_AUTOMAP_NOTES, AutomapNote.class);
      addField(countAutomapNote);
      addField(new Unknown(buffer, offset + 204, 80));
    } else {
      addField(new Unknown(buffer, offset + 196, 88));
    }

    offset = offsetActors.getValue();
    for (int i = 0; i < countActors.getValue(); i++) {
      Actor actor = new Actor(this, buffer, offset, i);
      offset = actor.getEndOffset();
      addField(actor);
    }

    offset = offsetItePoints.getValue();
    for (int i = 0; i < countItePoints.getValue(); i++) {
      ITEPoint ite = new ITEPoint(this, buffer, offset, i);
      offset = ite.getEndOffset();
      addField(ite);
    }

    offset = offsetSpawnPoints.getValue();
    for (int i = 0; i < countSpawnPoints.getValue(); i++) {
      SpawnPoint sp = new SpawnPoint(this, buffer, offset, i);
      offset = sp.getEndOffset();
      addField(sp);
    }

    offset = offsetEntrances.getValue();
    for (int i = 0; i < countEntrances.getValue(); i++) {
      Entrance ent = new Entrance(this, buffer, offset, i);
      offset = ent.getEndOffset();
      addField(ent);
    }

    offset = offsetContainers.getValue();
    for (int i = 0; i < countContainers.getValue(); i++) {
      Container con = new Container(this, buffer, offset, i);
      offset = con.getEndOffset();
      addField(con);
    }

    offset = offsetAmbients.getValue();
    for (int i = 0; i < countAmbients.getValue(); i++) {
      Ambient ambi = new Ambient(this, buffer, offset, i);
      offset = ambi.getEndOffset();
      addField(ambi);
    }

    offset = offsetVariables.getValue();
    for (int i = 0; i < countVariables.getValue(); i++) {
      Variable var = new Variable(this, buffer, offset, i);
      offset = var.getEndOffset();
      addField(var);
    }

    offset = offsetExploredBitmap.getValue();
    if (sizeExploredBitmap.getValue() > 0) {
      addField(new Explored(buffer, offset, sizeExploredBitmap.getValue(), ARE_EXPLORED_BITMAP));
    }

    offset = offsetDoors.getValue();
    for (int i = 0; i < countDoors.getValue(); i++) {
      Door door = new Door(this, buffer, offset, i);
      offset = door.getEndOffset();
      addField(door);
    }

    offset = offsetAnimations.getValue();
    for (int i = 0; i < countAnimations.getValue(); i++) {
      Animation anim = new Animation(this, buffer, offset, i);
      offset = anim.getEndOffset();
      addField(anim);
    }

    offset = offsetTiledObjects.getValue();
    for (int i = 0; i < countTiledObjects.getValue(); i++) {
      TiledObject tile = new TiledObject(this, buffer, offset, i);
      offset = tile.getEndOffset();
      addField(tile);
    }

    if (offsetAutomapNote != null) { // Torment, BG2
      offset = offsetAutomapNote.getValue();
      if (Profile.getEngine() == Profile.Engine.PST) {
        for (int i = 0; i < countAutomapNote.getValue(); i++) {
          AutomapNotePST note = new AutomapNotePST(this, buffer, offset, i);
          offset = note.getEndOffset();
          addField(note);
        }
      } else {
        for (int i = 0; i < countAutomapNote.getValue(); i++) {
          AutomapNote note = new AutomapNote(this, buffer, offset, i);
          offset = note.getEndOffset();
          addField(note);
        }
      }
    }

    if (offsetProTrap != null) { // BG2
      offset = offsetProTrap.getValue();
      for (int i = 0; i < countProTrap.getValue(); i++) {
        ProTrap trap = new ProTrap(this, buffer, offset, i);
        offset = trap.getEndOffset();
        addField(trap);
      }
    }

    offset = offsetItems.getValue();
    for (final StructEntry o : getFields()) {
      if (o instanceof Container) {
        ((Container) o).readItems(buffer, offset);
      }
    }

    offset = offsetVertices.getValue();
    for (final StructEntry o : getFields()) {
      if (o instanceof HasVertices) {
        ((HasVertices) o).readVertices(buffer, offset);
      }
    }

    if (offsetSongs.getValue() > 0 && offsetSongs.getValue() < buffer.limit()) {
      addField(new Song(this, buffer, offsetSongs.getValue()));
    }
    if (offsetRest.getValue() > 0 && offsetRest.getValue() < buffer.limit()) {
      addField(new RestSpawn(this, buffer, offsetRest.getValue()));
    }

    int endoffset = offset;
    for (final StructEntry entry : getFields()) {
      if (entry instanceof HasVertices) {
        // may contain additional elements
        for (final StructEntry subEntry : ((AbstractStruct) entry).getFields()) {
          endoffset = Math.max(endoffset, subEntry.getOffset() + subEntry.getSize());
        }
      } else {
        if (entry.getOffset() + entry.getSize() > endoffset) {
          endoffset = Math.max(endoffset, entry.getOffset() + entry.getSize());
        }
      }
    }
    return endoffset;
  }

  private void updateActorCREOffsets() {
    for (final StructEntry o : getFields()) {
      if (o instanceof Actor) {
        ((Actor) o).updateCREOffset();
      }
    }
  }

  private void updateItems() {
    // Assumes items offset is correct
    int offset = ((HexNumber) getAttribute(ARE_OFFSET_ITEMS)).getValue();
    int count = 0;
    for (final StructEntry o : getFields()) {
      if (o instanceof Container) {
        Container container = (Container) o;
        int itemNum = container.updateItems(offset, count);
        offset += 20 * itemNum;
        count += itemNum;
      }
    }
    ((DecNumber) getAttribute(ARE_NUM_ITEMS)).setValue(count);
  }

  private void updateVertices() {
    // Assumes vertices offset is correct
    int offset = ((HexNumber) getAttribute(ARE_OFFSET_VERTICES)).getValue();
    int count = 0;
    for (final StructEntry o : getFields()) {
      if (o instanceof HasVertices) {
        HasVertices vert = (HasVertices) o;
        int vertNum = vert.updateVertices(offset, count);
        offset += 4 * vertNum;
        count += vertNum;
      }
    }
    ((DecNumber) getAttribute(ARE_NUM_VERTICES)).setValue(count);
  }

  /** Displays the area viewer for this ARE resource. */
  AreaViewer showAreaViewer(Component parent) {
    if (areaViewer == null) {
      try {
        areaViewer = new AreaViewer(parent, this);
      } catch (Exception e) {
        Logger.error(e);
      }
    } else if (!areaViewer.isVisible()) {
      areaViewer.setVisible(true);
      areaViewer.toFront();
    } else {
      areaViewer.toFront();
    }
    return areaViewer;
  }

  // Called by "Extended Search"
  // Checks whether the specified resource entry matches all available search options.
  public static boolean matchSearchOptions(ResourceEntry entry, SearchOptions searchOptions) {
    if (entry != null && searchOptions != null) {
      try {
        AreResource are = new AreResource(entry);
        Actor[] actors;
        Animation[] animations;
        Item[][] items;
        boolean retVal = true;
        String key;
        Object o;

        // preparing substructures
        IsNumeric ofs = (IsNumeric) are.getAttribute(ARE_OFFSET_ACTORS, false);
        IsNumeric cnt = (IsNumeric) are.getAttribute(ARE_NUM_ACTORS, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          actors = new Actor[cnt.getValue()];
          for (int idx = 0; idx < actors.length; idx++) {
            actors[idx] = (Actor) are
                .getAttribute(String.format(SearchOptions.getResourceName(SearchOptions.ARE_Actor), idx), false);
          }
        } else {
          actors = new Actor[0];
        }

        ofs = (IsNumeric) are.getAttribute(ARE_OFFSET_ANIMATIONS, false);
        cnt = (IsNumeric) are.getAttribute(ARE_NUM_ANIMATIONS, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          animations = new Animation[cnt.getValue()];
          for (int idx = 0; idx < animations.length; idx++) {
            animations[idx] = (Animation) are
                .getAttribute(String.format(SearchOptions.getResourceName(SearchOptions.ARE_Animation), idx), false);
          }
        } else {
          animations = new Animation[0];
        }

        ofs = (IsNumeric) are.getAttribute(ARE_OFFSET_CONTAINERS, false);
        cnt = (IsNumeric) are.getAttribute(ARE_NUM_CONTAINERS, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          items = new Item[cnt.getValue()][];
          for (int i = 0; i < cnt.getValue(); i++) {
            String label = String.format(SearchOptions.getResourceName(SearchOptions.ARE_Container), i);
            Container container = (Container) are.getAttribute(label, false);
            if (container != null) {
              IsNumeric cnt2 = (IsNumeric) container.getAttribute(ARE_NUM_ITEMS, false);
              if (cnt2 != null && cnt2.getValue() > 0) {
                items[i] = new Item[cnt2.getValue()];
                for (int j = 0; j < cnt2.getValue(); j++) {
                  label = String.format(SearchOptions.getResourceName(SearchOptions.ARE_Container_Item), j);
                  items[i][j] = (Item) container.getAttribute(label, false);
                }
              } else {
                items[i] = new Item[0];
              }
            } else {
              items[i] = new Item[0];
            }
          }
        } else {
          items = new Item[0][];
        }

        // checking options
        String[] keyList = new String[] { SearchOptions.ARE_AreaType, SearchOptions.ARE_Location };
        for (String element : keyList) {
          if (retVal) {
            key = element;
            o = searchOptions.getOption(key);
            StructEntry struct = are.getAttribute(SearchOptions.getResourceName(key), false);
            retVal = SearchOptions.Utils.matchFlags(struct, o);
          } else {
            break;
          }
        }

        if (retVal) {
          key = SearchOptions.ARE_AreaScript;
          o = searchOptions.getOption(key);
          StructEntry struct = are.getAttribute(SearchOptions.getResourceName(key), false);
          retVal = SearchOptions.Utils.matchResourceRef(struct, o, false);
        }

        if (retVal) {
          key = SearchOptions.ARE_Actor_Character;
          o = searchOptions.getOption(key);
          boolean found = false;
          for (Actor actor : actors) {
            if (actor != null) {
              StructEntry struct = actor.getAttribute(SearchOptions.getResourceName(key), false);
              found |= SearchOptions.Utils.matchResourceRef(struct, o, false);
            }
          }
          retVal = found || (o == null);
        }

        if (retVal) {
          key = SearchOptions.ARE_Animation_Animation;
          o = searchOptions.getOption(key);
          boolean found = false;
          for (Animation animation : animations) {
            if (animation != null) {
              StructEntry struct = animation.getAttribute(SearchOptions.getResourceName(key), false);
              found |= SearchOptions.Utils.matchResourceRef(struct, o, false);
            }
          }
          retVal = found || o == null;
        }

        if (retVal) {
          key = SearchOptions.ARE_Container_Item_Item;
          o = searchOptions.getOption(key);
          boolean found = false;
          for (final Item[] item : items) {
            for (final Item value : item) {
              if (value != null) {
                StructEntry struct = value.getAttribute(SearchOptions.getResourceName(key), false);
                found = SearchOptions.Utils.matchResourceRef(struct, o, false);
              }
              if (found) {
                break;
              }
            }
            if (found) {
              break;
            }
          }
          retVal = found || (o == null);
        }

        keyList = new String[] { SearchOptions.ARE_Custom1, SearchOptions.ARE_Custom2, SearchOptions.ARE_Custom3,
            SearchOptions.ARE_Custom4 };
        for (String element : keyList) {
          if (retVal) {
            key = element;
            o = searchOptions.getOption(key);
            retVal = SearchOptions.Utils.matchCustomFilter(are, o);
          } else {
            break;
          }
        }

        return retVal;
      } catch (Exception e) {
        Logger.warn(e);
      }
    }
    return false;
  }
}
