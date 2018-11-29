// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;

/**
 * Manages IDS target type and value fields.
 */
public class IdsTargetType extends Bitmap
{
  public static final String DEFAULT_NAME_TYPE        = "IDS target";
  public static final String DEFAULT_NAME_VALUE       = "IDS value";
  public static final String DEFAULT_NAME_UNUSED      = "Unused";
  public static final String DEFAULT_ACTOR_NAME       = "Actor's name";
  public static final String DEFAULT_ACTOR_SCRIPTNAME = "Actor's script name";
  public static final String DEFAULT_SECOND_IDS       = "EA.IDS";
  public static final String[] DEFAULT_IDS_LIST = {"", "", "", "GENERAL.IDS", "RACE.IDS",
                                                   "CLASS.IDS", "SPECIFIC.IDS", "GENDER.IDS",
                                                   "ALIGNMEN.IDS"};

  private final int index;
  private boolean updateIdsValues;  // defines whether to update the "IDS value" field automatically

  /**
   * Constructs an IDS type field with the default list of IDS entries.
   */
  public IdsTargetType(ByteBuffer buffer, int offset, int size)
  {
    this(buffer, offset, size, null, -1, null, false);
  }

  /**
   * Constructs an IDS type field with the default list of IDS entries.
   */
  public IdsTargetType(ByteBuffer buffer, int offset, int size, String name)
  {
    this(buffer, offset, size, name, -1, null, false);
  }

  /**
   * Constructs an IDS type field with the default list of IDS entries and optional modifications.
   * @param secondIds   Replace IDS resource at index = 2 by the specified resource. (Default: EA.IDS)
   * @param targetActor If {@code true}, Enhanced Editions will use index 10 for
   *                    Actor's name strrefs and index 11 for Actor's script name.
   */
  public IdsTargetType(ByteBuffer buffer, int offset, int size, String name,
                       String secondIds, boolean targetActor)
  {
    this(buffer, offset, size, name, -1, secondIds, targetActor);
  }

  /**
   * Constructs an IDS type field with the default list of IDS entries and optional modifications.
   * @param idx         An optional number added to the field name. (Default: -1 for none)
   * @param secondIds   Replace IDS resource at index = 2 by the specified resource. (Default: EA.IDS)
   * @param targetActor If {@code true}, Enhanced Editions will use index 10 for
   *                    Actor's name strrefs and index 11 for Actor's script name.
   */
  public IdsTargetType(ByteBuffer buffer, int offset, int size, String name, int idx,
                       String secondIds, boolean targetActor)
  {
    super(buffer, offset, size, createFieldName(name, idx, DEFAULT_NAME_TYPE),
          createIdsTypeTable(secondIds, targetActor));
    this.index = idx;
    this.updateIdsValues = true;
  }

  /** Constructs an IDS type field with the specified list of IDS resource names. */
  public IdsTargetType(ByteBuffer buffer, int offset, int size, String name, String[] ids)
  {
    super(buffer, offset, size, createFieldName(name, -1, DEFAULT_NAME_TYPE),
          (ids != null) ? ids : createIdsTypeTable(null, false));
    this.index = -1;
    this.updateIdsValues = true;
  }

//--------------------- Begin Interface Editable ---------------------

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    boolean retVal = super.updateValue(struct);
    if (updateIdsValues && retVal) {
      int valueOffset = getOffset() - getSize();
      List<StructEntry> list = struct.getList();
      for (int i = 0, size = list.size(); i < size; i++) {
        StructEntry entry = list.get(i);
        if (entry.getOffset() == valueOffset && entry instanceof Datatype) {
          ByteBuffer buffer = ((Datatype)entry).getDataBuffer();
          StructEntry newEntry = createIdsValueFromType(buffer, 0);
          newEntry.setOffset(valueOffset);
          list.set(i, newEntry);

          // notifying listeners
          struct.fireTableRowsUpdated(i, i);
        }
      }
    }
    return retVal;
  }

//--------------------- End Interface Editable ---------------------

  /**
   * Creates a fully initialized StructEntry object for IDS value, based on the currently selected
   * IDS type. Offset and size values will be derived from the data of this IDS type field.
   */
  public StructEntry createIdsValueFromType(ByteBuffer buffer)
  {
    return createIdsValueFromType(buffer, getOffset() - getSize(), getSize(), null);
  }

  /**
   * Creates a fully initialized StructEntry object for IDS value, based on the currently selected
   * IDS type. Returns a {@link DecNumber} object for unsupported IDS types.
   */
  public StructEntry createIdsValueFromType(ByteBuffer buffer, int offset)
  {
    return createIdsValueFromType(buffer, offset, getSize(), null);
  }

  /**
   * Creates a fully initialized StructEntry object for IDS value, based on the currently selected
   * IDS type. Returns a {@link DecNumber} object for unsupported IDS types.
   */
  public StructEntry createIdsValueFromType(ByteBuffer buffer, int offset, int size, String name)
  {
    int value = getValue();
    String type = getString(value);
    if (type != null) {
      if (ResourceFactory.resourceExists(type)) {
        return new IdsBitmap(buffer, offset, size, createFieldName(name, index, DEFAULT_NAME_VALUE), type);
      } else if (getSize() == 4 && value == 10 && !type.isEmpty()) {
        // Actor's name as Strref
        return new StringRef(buffer, offset, createFieldName(name, index, DEFAULT_ACTOR_NAME));
      }
    }
    return new DecNumber(buffer, offset, size, createFieldName(name, index, DEFAULT_NAME_UNUSED));
  }

  public StructEntry createResourceFromType(ByteBuffer buffer, int offset)
  {
    return createResourceFromType(buffer, offset, null);
  }

  /**
   * Creates a fully initialized StructEntry object for the resource field, based on the currently
   * selected IDS type. Only index 11 (Actor's script name) is currently supported.
   * Returns an Unknown object otherwise.
   */
  public StructEntry createResourceFromType(ByteBuffer buffer, int offset, String name)
  {
    int value = getValue();
    String type = getString(value);
    if (type != null) {
      if (value == 11 &&
          !type.isEmpty() &&
          !type.toUpperCase(Locale.ENGLISH).endsWith(".IDS")) {
        // Actor's script name
        return new TextString(buffer, offset, 8, createFieldName(name, index, DEFAULT_ACTOR_SCRIPTNAME));
      }
    }
    return new Unknown(buffer, offset, 8, createFieldName(name, index, DEFAULT_NAME_UNUSED));
  }

  /** Returns whether this IdsTargetType instance automatically updates the associated IDS value field. */
  public boolean isUpdatingIdsValues()
  {
    return updateIdsValues;
  }

  /** Specify whether this IdsTargetType instance should automatically update the associated IDS value field. */
  public void setUpdateIdsValues(boolean b)
  {
    updateIdsValues = b;
  }

  /** Returns an array of available IDS targets for the current game type. */
  public static String[] createIdsTypeTable(String secondIds, boolean targetActor)
  {
    int len = DEFAULT_IDS_LIST.length;
    if (Profile.isEnhancedEdition()) {
      len++;
      if (targetActor) {
        len += 2;
      }
    }
    String[] retVal = Arrays.copyOf(DEFAULT_IDS_LIST, len);
    retVal[2] = (secondIds != null) ? secondIds : DEFAULT_SECOND_IDS;
    retVal[8] = Profile.getProperty(Profile.Key.GET_IDS_ALIGNMENT);
    if (Profile.getGame() == Profile.Game.IWD2) {
      retVal[5] = "CLASSMSK.IDS";
    }
    if (Profile.isEnhancedEdition()) {
      retVal[9] = "KIT.IDS";
      if (targetActor) {
        retVal[10] = DEFAULT_ACTOR_NAME;
        retVal[11] = DEFAULT_ACTOR_SCRIPTNAME;
      }
    }
    return retVal;
  }

  // Creates a valid field name from the specified arguments
  private static String createFieldName(String name, int index, String defName)
  {
    if (name == null) {
      name = (defName != null) ? defName : DEFAULT_NAME_TYPE;
    }
    return (index >= 0) ? (name + " " + index) : name;
  }
}
