// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.pro;

import java.nio.ByteBuffer;

import javax.swing.JComponent;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.ColorPicker;
import org.infinity.datatype.Datatype;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.HashBitmap;
import org.infinity.datatype.IdsTargetType;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SpellProtType;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.UpdateEvent;
import org.infinity.datatype.UpdateListener;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasAddRemovable;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.SearchOptions;
import org.infinity.util.LongIntegerHashMap;

/**
 * This resource describes projectiles, and the files are referenced spells and
 * projectile weapons. Projectile files can control:
 * <ul>
 * <li>Projectile graphics</li>
 * <li>Projectile speed</li>
 * <li>Projectile area of effect</li>
 * <li>Projectile sound</li>
 * </ul>
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/pro_v1.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/pro_v1.htm</a>
 */
public final class ProResource extends AbstractStruct implements Resource, HasAddRemovable, HasViewerTabs, UpdateListener
{
  // PRO-specific field labels
  public static final String PRO_TYPE = "Projectile type";
  public static final String PRO_SPEED = "Speed";
  public static final String PRO_BEHAVIOR = "Behavior";
  public static final String PRO_SOUND_FIRE = "Fire sound";
  public static final String PRO_SOUND_IMPACT = "Impact sound";
  public static final String PRO_SOURCE_ANIMATION = "Source animation";
  public static final String PRO_PARTICLE_COLOR = "Particle color";
  public static final String PRO_WIDTH = "Projectile width";
  public static final String PRO_EX_FLAGS = "Extended flags";
  public static final String PRO_STRING = "String";
  public static final String PRO_COLOR = "Color";
  public static final String PRO_COLOR_SPEED = "Color speed";
  public static final String PRO_SCREEN_SHAKE_AMOUNT = "Screen shake amount";
  public static final String PRO_CREATURE_TYPE = "Creature type";
  public static final String PRO_SPELL_DEFAULT = "Default spell";
  public static final String PRO_SPELL_SUCCESS = "Success spell";
  public static final String PRO_SPELL_ANGLE_MIN = "Angle increase minimum";
  public static final String PRO_SPELL_ANGLE_MAX = "Angle increase maximum";
  public static final String PRO_SPELL_CURVE_MIN = "Curve minimum";
  public static final String PRO_SPELL_CURVE_MAX = "Curve maximum";
  public static final String PRO_SPELL_THAC0_BONUS = "THAC0 bonus";
  public static final String PRO_SPELL_THAC0_BONUS_2 = "THAC0 bonus (non-actor)";
  public static final String PRO_SPELL_RADIUS_MIN = "Radius minimum";
  public static final String PRO_SPELL_RADIUS_MAX = "Radius maximum";

  public static final String[] s_color = {"", "Black", "Blue", "Chromatic", "Gold",
                                           "Green", "Purple", "Red", "White", "Ice",
                                           "Stone", "Magenta", "Orange"};
  public static final String[] s_behave = {"No flags set", "Show sparks", "Use height",
                                            "Loop fire sound", "Loop impact sound", "Ignore center",
                                            "Draw as background",
                                            "EE: Allow saving;Allows you to save the game while the projectile is still active.",
                                            "EE: Loop spread animation"};
  public static final String[] s_flagsEx = {
    "No flags set", "Bounce from walls", "Pass target", "Draw center VVC once", "Hit immediately",
    "Face target", "Curved path", "Start random frame", "Pillar", "Semi-trans. trail puff VEF",
    "Tinted trail puff VEF", "Multiple proj.", "Default spell on missed", "Falling path", "Comet",
    "Lined up AoE", "Rectangular AoE", "Draw behind target", "Casting glow fx", "Travel door",
    "Stop/fade after hit", "Display string", "Random path", "Start random seq.", "Color pulse on hit",
    "Touch projectile", "Neg. IDS1", "Neg. IDS2", "Use either IDS", "Delayed payload",
    "Limited path count", "IWD style check", "Caster affected"};

  public static final LongIntegerHashMap<String> m_projtype = new LongIntegerHashMap<String>();
  static {
    m_projtype.put(1L, "No BAM");
    m_projtype.put(2L, "Single target");
    m_projtype.put(3L, "Area of effect");
  }

  private StructHexViewer hexViewer;

  public ProResource(ResourceEntry entry) throws Exception
  {
    super(entry);
  }

  //<editor-fold defaultstate="collapsed" desc="HasAddRemovable">
  @Override
  public AddRemovable[] getAddRemovables() throws Exception
  {
    return null;
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception
  {
    return entry;
  }

  @Override
  public boolean confirmRemoveEntry(AddRemovable entry) throws Exception
  {
    return true;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="UpdateListener">
  @Override
  public boolean valueUpdated(UpdateEvent event)
  {
    if (event.getSource() instanceof Flag &&
        ((StructEntry)event.getSource()).getName().equals(PRO_EX_FLAGS)) {
      boolean isIwdStyle = ((Flag)event.getSource()).isFlagSet(30);
      AbstractStruct struct = event.getStructure();
      boolean bRet = false;
      if (isIwdStyle) {
        bRet |= setIwdStyleIdsType(struct, 60, 1);
        bRet |= setIwdStyleIdsType(struct, 64, 2);
      } else {
        bRet |= setOldStyleIdsType(struct, 60, 1);
        bRet |= setOldStyleIdsType(struct, 64, 2);
      }
      return bRet;
    }
    else if (event.getSource() instanceof HashBitmap &&
             ((StructEntry)event.getSource()).getName().equals(PRO_TYPE)) {
      HashBitmap proType = (HashBitmap)event.getSource();
      AbstractStruct struct = event.getStructure();
      // add/remove extended sections in the parent structure depending on the current value
      if (struct instanceof Resource && struct instanceof HasAddRemovable) {
        if (proType.getValue() == 3L) {         // area of effect
          StructEntry entry = struct.getList().get(struct.getList().size() - 1);
          try {
            if (!(entry instanceof ProSingleType) && !(entry instanceof ProAreaType))
              struct.addDatatype(new ProSingleType(), struct.getList().size());
            entry = struct.getList().get(struct.getList().size() - 1);
            if (!(entry instanceof ProAreaType))
              struct.addDatatype(new ProAreaType(), struct.getList().size());
          } catch (Exception e) {
            e.printStackTrace();
            return false;
          }
        } else if (proType.getValue() == 2L) {  // single target
          StructEntry entry = struct.getList().get(struct.getList().size() - 1);
          if (entry instanceof ProAreaType)
            struct.removeDatatype((AddRemovable)entry, false);
          entry = struct.getList().get(struct.getList().size() - 1);
          if (!(entry instanceof ProSingleType)) {
            try {
              struct.addDatatype(new ProSingleType(), struct.getList().size());
            } catch (Exception e) {
              e.printStackTrace();
              return false;
            }
          }
        } else if (proType.getValue() == 1L) {  // no bam
          if (struct.getList().size() > 2) {
            StructEntry entry = struct.getList().get(struct.getList().size() - 1);
            if (entry instanceof ProAreaType)
              struct.removeDatatype((AddRemovable)entry, false);
            entry = struct.getList().get(struct.getList().size() - 1);
            if (entry instanceof ProSingleType)
              struct.removeDatatype((AddRemovable)entry, false);
          }
        } else {
          return false;
        }
        return true;
      }
    }
    return false;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="HasViewerTabs">
  @Override
  public int getViewerTabCount()
  {
    return 1;
  }

  @Override
  public String getViewerTabName(int index)
  {
    return StructViewer.TAB_RAW;
  }

  @Override
  public JComponent getViewerTab(int index)
  {
    if (hexViewer == null) {
      BasicColorMap colorMap = new BasicColorMap(this, false);
      colorMap.setColoredEntry(BasicColorMap.Coloring.BLUE, ProSingleType.class);
      colorMap.setColoredEntry(BasicColorMap.Coloring.GREEN, ProAreaType.class);
      hexViewer = new StructHexViewer(this, colorMap);
    }
    return hexViewer;
  }

  @Override
  public boolean viewerTabAddedBefore(int index)
  {
    return false;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Readable">
  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception
  {
    final String[] s_types = new String[]{"VEF", "VVC", "BAM"};

    addField(new TextString(buffer, offset, 4, COMMON_SIGNATURE));
    addField(new TextString(buffer, offset + 4, 4, COMMON_VERSION));
    HashBitmap projtype = new HashBitmap(buffer, offset + 8, 2, PRO_TYPE, m_projtype);
    projtype.addUpdateListener(this);
    addField(projtype);
    addField(new DecNumber(buffer, offset + 10, 2, PRO_SPEED));
    addField(new Flag(buffer, offset + 12, 4, PRO_BEHAVIOR, s_behave));
    addField(new ResourceRef(buffer, offset + 16, PRO_SOUND_FIRE, "WAV"));
    addField(new ResourceRef(buffer, offset + 24, PRO_SOUND_IMPACT, "WAV"));
    addField(new ResourceRef(buffer, offset + 32, PRO_SOURCE_ANIMATION, s_types));
    addField(new Bitmap(buffer, offset + 40, 2, PRO_PARTICLE_COLOR, s_color));
    if (Profile.isEnhancedEdition()) {
      addField(new DecNumber(buffer, offset + 42, 2, PRO_WIDTH));
      Flag flag = new Flag(buffer, offset + 44, 4, PRO_EX_FLAGS, s_flagsEx);
      addField(flag);
      addField(new StringRef(buffer, offset + 48, PRO_STRING));
      addField(new ColorPicker(buffer, offset + 52, PRO_COLOR, ColorPicker.Format.BGRX));
      addField(new DecNumber(buffer, offset + 56, 2, PRO_COLOR_SPEED));
      addField(new DecNumber(buffer, offset + 58, 2, PRO_SCREEN_SHAKE_AMOUNT));
      flag.addUpdateListener(this);
      if (flag.isFlagSet(30)) {
        SpellProtType type = new SpellProtType(buffer, offset + 62, 2, PRO_CREATURE_TYPE, 1);
        addField(type.createCreatureValueFromType(buffer, offset + 60));
        addField(type);
        type = new SpellProtType(buffer, offset + 66, 2, PRO_CREATURE_TYPE, 2);
        addField(type.createCreatureValueFromType(buffer, offset + 64));
        addField(type);
      } else {
        IdsTargetType type = new IdsTargetType(buffer, offset + 62, 2, null, 1, null, false);
        addField(type.createIdsValueFromType(buffer));
        addField(type);
        type = new IdsTargetType(buffer, offset + 66, 2, null, 2, null, false);
        addField(type.createIdsValueFromType(buffer));
        addField(type);
      }
      addField(new ResourceRef(buffer, 68, PRO_SPELL_DEFAULT, "SPL"));
      addField(new ResourceRef(buffer, 76, PRO_SPELL_SUCCESS, "SPL"));
      if (Profile.getGame() == Profile.Game.PSTEE) {
        addField(new DecNumber(buffer, 84, 2, PRO_SPELL_ANGLE_MIN));
        addField(new DecNumber(buffer, 86, 2, PRO_SPELL_ANGLE_MAX));
        addField(new DecNumber(buffer, 88, 2, PRO_SPELL_CURVE_MIN));
        addField(new DecNumber(buffer, 90, 2, PRO_SPELL_CURVE_MAX));
        addField(new DecNumber(buffer, 92, 2, PRO_SPELL_THAC0_BONUS));
        addField(new DecNumber(buffer, 94, 2, PRO_SPELL_THAC0_BONUS_2));
        addField(new DecNumber(buffer, 96, 2, PRO_SPELL_RADIUS_MIN));
        addField(new DecNumber(buffer, 98, 2, PRO_SPELL_RADIUS_MAX));
        addField(new Unknown(buffer, offset + 100, 156));
      } else {
        addField(new Unknown(buffer, offset + 84, 172));
      }
    } else {
      addField(new Unknown(buffer, offset + 42, 214));
    }
    offset += 256;

    if (projtype.getValue() > 1L) {
      ProSingleType single = new ProSingleType(this, buffer, offset);
      addField(single);
      offset += single.getSize();
    }
    if (projtype.getValue() > 2L) {
      ProAreaType area = new ProAreaType(this, buffer, offset);
      addField(area);
      offset += area.getSize();
    }

    return offset;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="AbstractStruct">
  @Override
  protected void viewerInitialized(StructViewer viewer)
  {
    viewer.addTabChangeListener(hexViewer);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype)
  {
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype)
  {
    super.datatypeAddedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype)
  {
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype)
  {
    super.datatypeRemovedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }
  //</editor-fold>

  /** Updates current IDS targeting to IWD style and returns true if changes have been made. */
  private boolean setIwdStyleIdsType(AbstractStruct struct, int offset, int nr)
  {
    if (struct != null && offset >= 0) {
      StructEntry e1 = struct.getAttribute(offset, false);
      StructEntry e2 = struct.getAttribute(offset + 2, false);
      if (!(e2 instanceof SpellProtType)) {
        final ByteBuffer typeBuffer = e2.getDataBuffer();
        final SpellProtType newType = new SpellProtType(typeBuffer, 0, 2, null, nr);
        newType.setOffset(offset + 2);
        final ByteBuffer valueBuffer = e1.getDataBuffer();
        final StructEntry newValue = newType.createCreatureValueFromType(valueBuffer, 0);
        newValue.setOffset(offset);

        replaceEntry(newValue);
        replaceEntry(newType);
        return true;
      }
    }
    return false;
  }

  /** Updates current IDS targeting to old BG style and returns true if changes have been made. */
  private boolean setOldStyleIdsType(AbstractStruct struct, int offset, int nr)
  {
    if (struct != null && offset >= 0) {
      StructEntry e1 = struct.getAttribute(offset, false);
      StructEntry e2 = struct.getAttribute(offset + 2, false);
      if (!(e2 instanceof IdsTargetType)) {
        final ByteBuffer typeBuffer = e2.getDataBuffer();
        final IdsTargetType newType = new IdsTargetType(typeBuffer, 0, 2, null, nr, null, false);
        newType.setOffset(offset + 2);
        final ByteBuffer valueBuffer = e1.getDataBuffer();
        final StructEntry newValue = newType.createIdsValueFromType(valueBuffer, 0);
        newValue.setOffset(offset);

        replaceEntry(newValue);
        replaceEntry(newType);
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether the specified resource entry matches all available search options.
   * Called by "Extended Search"
   */
  public static boolean matchSearchOptions(ResourceEntry entry, SearchOptions searchOptions)
  {
    if (entry != null && searchOptions != null) {
      try {
        ProResource pro = new ProResource(entry);
        ProSingleType single = (ProSingleType)pro.getAttribute(SearchOptions.getResourceName(SearchOptions.PRO_SingleTarget), false);
        ProAreaType area = (ProAreaType)pro.getAttribute(SearchOptions.getResourceName(SearchOptions.PRO_AreaOfEffect), false);
        boolean retVal = true;
        String key;
        Object o;

        String[] keyList = new String[]{SearchOptions.PRO_Type, SearchOptions.PRO_Speed,
                                        SearchOptions.PRO_TrapSize, SearchOptions.PRO_ExplosionSize,
                                        SearchOptions.PRO_ExplosionEffect};
        AbstractStruct[] structList = new AbstractStruct[]{pro, pro, area, area, area};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            key = keyList[idx];
            o = searchOptions.getOption(key);
            if (structList[idx] != null) {
              StructEntry struct = structList[idx].getAttribute(SearchOptions.getResourceName(key), false);
              retVal &= SearchOptions.Utils.matchNumber(struct, o);
            } else {
              retVal &= (o == null);
            }
          } else {
            break;
          }
        }

        keyList = new String[]{SearchOptions.PRO_Behavior, SearchOptions.PRO_Flags,
                               SearchOptions.PRO_AreaFlags};
        structList = new AbstractStruct[]{pro, single, area};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            key = keyList[idx];
            o = searchOptions.getOption(key);
            if (structList[idx] != null) {
              StructEntry struct = structList[idx].getAttribute(SearchOptions.getResourceName(key), false);
              retVal &= SearchOptions.Utils.matchFlags(struct, o);
            } else {
              retVal &= (o == null);
            }
          } else {
            break;
          }
        }

        if (retVal) {
          key = SearchOptions.PRO_Animation;
          o = searchOptions.getOption(key);
          if (single != null) {
            StructEntry struct = single.getAttribute(SearchOptions.getResourceName(key), false);
            retVal &= SearchOptions.Utils.matchResourceRef(struct, o, false);
          } else {
            retVal &= (o == null);
          }
        }

        keyList = new String[]{SearchOptions.PRO_Custom1, SearchOptions.PRO_Custom2,
                               SearchOptions.PRO_Custom3, SearchOptions.PRO_Custom4};
        for (int idx = 0; idx < keyList.length; idx++) {
          if (retVal) {
            key = keyList[idx];
            o = searchOptions.getOption(key);
            retVal &= SearchOptions.Utils.matchCustomFilter(pro, o);
          } else {
            break;
          }
        }

        return retVal;
      } catch (Exception e) {
      }
    }
    return false;
  }
}
