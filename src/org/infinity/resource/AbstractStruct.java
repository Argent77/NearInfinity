// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import org.infinity.datatype.Editable;
import org.infinity.datatype.InlineEditable;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.Unknown;
import org.infinity.gui.StructViewer;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.resource.are.Actor;
import org.infinity.resource.dlg.AbstractCode;
import org.infinity.resource.itm.ItmResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.spl.SplResource;
import org.infinity.search.ReferenceSearcher;
import org.infinity.util.Logger;
import org.infinity.util.io.ByteBufferOutputStream;
import org.infinity.util.io.StreamUtils;

public abstract class AbstractStruct extends AbstractTableModel
    implements StructEntry, Viewable, Closeable, Referenceable, PropertyChangeListener {
  // Commonly used field labels
  public static final String COMMON_SIGNATURE     = "Signature";
  public static final String COMMON_VERSION       = "Version";
  public static final String COMMON_UNKNOWN       = "Unknown";
  public static final String COMMON_UNUSED        = "Unused";
  public static final String COMMON_UNUSED_BYTES  = "Unused bytes?";
  public static final String SUFFIX_UNUSED        = " (unused)";

  // Commonly used string arrays
  public static final String[] OPTION_NOYES       = { "No", "Yes" };

  public static final String[] OPTION_YESNO       = { "Yes", "No" };

  public static final String[] OPTION_SCHEDULE    = { "Not active", "00:30-01:29", "01:30-02:29", "02:30-03:29",
      "03:30-04:29", "04:30-05:29", "05:30-06:29", "06:30-07:29", "07:30-08:29", "08:30-09:29", "09:30-10:29",
      "10:30-11:29", "11:30-12:29", "12:30-13:29", "13:30-14:29", "14:30-15:29", "15:30-16:29", "16:30-17:29",
      "17:30-18:29", "18:30-19:29", "19:30-20:29", "20:30-21:29", "21:30-22:29", "22:30-23:29", "23:30-00:29" };

  public static final String[] OPTION_ORIENTATION = { "South", "SSW", "SW", "WSW", "West", "WNW", "NW", "NNW", "North",
      "NNE", "NE", "ENE", "East", "ESE", "SE", "SSE" };

  // Table column names
  public static final String COLUMN_ATTRIBUTE = "Attribute";
  public static final String COLUMN_VALUE     = "Value";
  public static final String COLUMN_OFFSET    = "Offset";
  public static final String COLUMN_SIZE      = "Size";

  /** Identifies the intention to removal of rows or columns. */
  public static final int WILL_BE_DELETE = -2;

  private List<StructEntry> fields;
  private AbstractStruct superStruct;
  private Map<Class<? extends StructEntry>, SectionCount> countmap;
  private Map<Class<? extends StructEntry>, SectionOffset> offsetmap;

  /**
   * If this structure represents top-level structure of the resource, contains pointer to the resource, otherwize
   * {@code null}.
   */
  private final ResourceEntry entry;

  /**
   * Name of the field or resource name if this struct represents top-level structure of the resource.
   */
  private String name;

  /**
   * Arbitrary data that is associated with this structure.
   */
  private final Object extraData;

  private StructViewer viewer;
  private boolean structChanged;

  /** Offset of the first byte in serialized format of this struct. */
  private int startoffset;

  /** Offset of the last byte in serialized format of this struct. */
  private int endoffset;

  private int extraoffset;

  /**
   * If any {@link PropertyChangeListener}s have been registered, the {@code changeSupport} field describes them.
   *
   * @see #addPropertyChangeListener
   * @see #removePropertyChangeListener
   */
  private PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

  private static void adjustEntryOffsets(AbstractStruct superStruct, AbstractStruct modifiedStruct,
      AddRemovable datatype, int amount) {
    for (final StructEntry e : superStruct.fields) {
      if (e.getOffset() > datatype.getOffset()
          || e.getOffset() == datatype.getOffset() && e != datatype && e != modifiedStruct) {
        e.setOffset(e.getOffset() + amount);
      }
      if (e instanceof AbstractStruct) {
        adjustEntryOffsets((AbstractStruct) e, modifiedStruct, datatype, amount);
      }
    }
  }

  private static void adjustSectionOffsets(AbstractStruct superStruct, AddRemovable datatype, int amount) {
    for (final StructEntry e : superStruct.fields) {
      if (e instanceof SectionOffset) {
        final SectionOffset so = (SectionOffset) e;
        if (so.getValue() + superStruct.getExtraOffset() > datatype.getOffset()) {
          so.incValue(amount);
        } else if (so.getValue() + superStruct.getExtraOffset() == datatype.getOffset()) {
          if (amount > 0) {
            if (superStruct instanceof ItmResource || superStruct instanceof SplResource) {
              // ensure that effect structures are added after ability structures
              if (datatype instanceof AbstractAbility && so.getSection().equals(Effect.class)) {
                so.incValue(amount);
              }
            } else if (!so.getSection().equals(datatype.getClass())) {
              so.incValue(amount);
            }
          }
        }
      }
    }
  }

  /**
   * Creates top-level struct, that represents specified resource. Reads specified resource and creates it structured
   * representation.
   *
   * @param entry Pointer to resource for read
   * @throws Exception If resource can not be readed
   */
  protected AbstractStruct(ResourceEntry entry) throws Exception {
    this(entry, null);
  }

  /**
   * Creates top-level struct, that represents specified resource. Reads specified resource and creates it structured
   * representation.
   *
   * @param entry Pointer to resource for read
   * @param extraData Arbitrary data for use in derived classes or by explicit queries.
   * @throws Exception If resource can not be readed
   */
  protected AbstractStruct(ResourceEntry entry, Object extraData) throws Exception {
    this.entry = entry;
    this.extraData = extraData;
    fields = new ArrayList<>();
    name = entry.getResourceName();
    ByteBuffer bb = entry.getResourceBuffer();
    endoffset = read(bb, 0);
    if (this instanceof HasChildStructs && !fields.isEmpty()) {// Is this enough?
      Collections.sort(fields); // This way we can writeField out in the order in list - sorted by offset
      fixHoles((ByteBuffer) bb.position(0));
      initAddStructMaps();
    }
  }

  protected AbstractStruct(AbstractStruct superStruct, String name, int startoffset, int listSize) {
    this.entry = null;
    this.extraData = null;
    this.superStruct = superStruct;
    this.name = name;
    this.startoffset = startoffset;
    fields = new ArrayList<>(listSize);
  }

  protected AbstractStruct(AbstractStruct superStruct, String name, ByteBuffer buffer, int startoffset)
      throws Exception {
    this(superStruct, name, buffer, startoffset, 10);
  }

  protected AbstractStruct(AbstractStruct superStruct, String name, ByteBuffer buffer, int startoffset, int listSize)
      throws Exception {
    this(superStruct, name, startoffset, listSize);
    endoffset = read(buffer, startoffset);
    if (this instanceof HasChildStructs) {
      if (!(this instanceof Actor)) { // Is this enough?
        Collections.sort(fields); // This way we can writeField out in the order in list - sorted by offset
      }
      initAddStructMaps();
    }
  }

  @Override
  public void close() throws Exception {
    if (structChanged && viewer != null && this instanceof Resource && superStruct == null) {
      ResourceFactory.closeResource((Resource) this, entry, viewer);
    }
    if (viewer != null) {
      viewer.close();
    }
  }

  @Override
  public boolean isReferenceable() {
    return true;
  }

  @Override
  public void searchReferences(Component parent) {
    new ReferenceSearcher(getResourceEntry(), parent);
  }

  @Override
  public int compareTo(StructEntry o) {
    return getOffset() - o.getOffset();
  }

  @Override
  public AbstractStruct clone() throws CloneNotSupportedException {
    final AbstractStruct newstruct = (AbstractStruct) super.clone();
    newstruct.listenerList = new EventListenerList();
    newstruct.changeSupport = new PropertyChangeSupport(newstruct);
    newstruct.superStruct = null;
    newstruct.fields = new ArrayList<>(fields.size());
    newstruct.viewer = null;
    int curOfs = 0;
    for (final StructEntry e : fields) {
      final StructEntry newEntry = e.clone();
      newEntry.setParent(newstruct);
      newEntry.setOffset(curOfs);
      newstruct.fields.add(newEntry);
      curOfs += newEntry.getSize();
    }
    newstruct.initAddStructMaps();
    return newstruct;
  }

  @Override
  public void copyNameAndOffset(StructEntry structEntry) {
    name = structEntry.getName();
    setOffset(structEntry.getOffset());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String newName) {
    if (newName != null) {
      name = newName;
    } else {
      throw new NullPointerException();
    }
  }

  @Override
  public int getOffset() {
    return startoffset;
  }

  @Override
  public AbstractStruct getParent() {
    return superStruct;
  }

  @Override
  public int getSize() {
    return endoffset - startoffset;
  }

  @Override
  public ByteBuffer getDataBuffer() {
    ByteBuffer bb = ByteBuffer.allocate(getSize());
    try (ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb)) {
      writeFlatFields(bbos);
    } catch (IOException e) {
      Logger.error(e);
    }
    bb.position(0);
    return bb;
  }

  @Override
  public List<StructEntry> getStructChain() {
    final List<StructEntry> list = new ArrayList<>();
    StructEntry e = this;
    while (e != null) {
      list.add(0, e);
      e = e.getParent();
      if (list.contains(e)) {
        // avoid infinite loops
        break;
      }
    }
    return list;
  }

  @Override
  public void setOffset(int newoffset) {
    if (extraoffset != 0) {
      extraoffset += newoffset - startoffset;
    }
    int delta = getSize();
    startoffset = newoffset;
    endoffset = newoffset + delta;
  }

  @Override
  public void setParent(AbstractStruct parent) {
    if (superStruct != null) {
      removePropertyChangeListener(superStruct);
    }
    superStruct = parent;
    if (parent != null) {
      addPropertyChangeListener(parent);
    }
  }

  @Override
  public int getRowCount() {
    return fields.size();
  }

  @Override
  public int getColumnCount() {
    int retVal = 2;
    if (BrowserMenuBar.getInstance().getOptions().showTableOffsets()) {
      retVal++;
    }
    if (BrowserMenuBar.getInstance().getOptions().showTableSize()) {
      retVal++;
    }
    return retVal;
  }

  @Override
  public String getColumnName(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return COLUMN_ATTRIBUTE;
      case 1:
        return COLUMN_VALUE;
      case 2:
      case 3:
        if (columnIndex == 2 && BrowserMenuBar.getInstance().getOptions().showTableOffsets()) {
          return COLUMN_OFFSET;
        } else if (BrowserMenuBar.getInstance().getOptions().showTableSize()) {
          return COLUMN_SIZE;
        }
        break;
    }
    return "";
  }

  @Override
  public boolean isCellEditable(int row, int col) {
    if (col == 1) {
      Object o = getValueAt(row, col);
      return o instanceof InlineEditable && !(o instanceof Editable);
    }
    return false;
  }

  @Override
  public Object getValueAt(int row, int column) {
    if (row >= 0 && row < fields.size()) {
      final StructEntry data = fields.get(row);
      switch (getColumnName(column)) {
        case COLUMN_ATTRIBUTE:
          return data.getName();
        case COLUMN_VALUE:
          return data;
        case COLUMN_OFFSET: {
          String s = Integer.toHexString(data.getOffset()) + " h";
          if (BrowserMenuBar.getInstance().getOptions().showTableOffsetsRelative() && data.getParent() != null
              && data.getParent().getParent() != null) {
            final int relOfs = data.getOffset() - data.getParent().getOffset();
            final String relOfsHex = ((relOfs < 0) ? "-" : "") +  Integer.toHexString(Math.abs(relOfs));
            s += " (" + relOfsHex + " h)";
          }
          return s;
        }
        case COLUMN_SIZE:
          if (BrowserMenuBar.getInstance().getOptions().showTableSizeInHex()) {
            return Integer.toHexString(data.getSize()) + " h";
          } else {
            return Integer.toString(data.getSize());
          }
      }
    }
    return "Unknown datatype";
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    Object o = getValueAt(row, column);
    if (o instanceof InlineEditable) {
      if (!((InlineEditable) o).update(value)) {
        JOptionPane.showMessageDialog(viewer, "Error updating value", "Error", JOptionPane.ERROR_MESSAGE);
      } else {
        fireTableCellUpdated(row, column);
        setStructChanged(true);
      }
    }
  }

  @Override
  public JComponent makeViewer(ViewableContainer container) {
    if (viewer == null) {
      viewer = new StructViewer(this);
      viewerInitialized(viewer);
    }
    return viewer;
  }

  @Override
  public void write(OutputStream os) throws IOException {
    Collections.sort(fields); // This way we can writeField out in the order in list - sorted by offset
    for (final StructEntry e : fields) {
      e.write(os);
    }
  }

  @Override
  public String toString() {
    // limit text length to speed things up
    int capacity = 256;
    final StringBuilder sb = new StringBuilder(capacity);
    for (int i = 0, count = fields.size(); i < count; i++) {
      final StructEntry field = fields.get(i);
      final String text = field.getName() + ": " + field;

      if (i != 0) {
        sb.append(',');
        --capacity;
      }
      if (text.length() < capacity) {
        sb.append(text);
      } else {
        sb.append(text, 0, capacity);
      }
      capacity -= text.length();
      if (capacity < 0 || capacity == 0 && i != count) {
        break;
      }
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(endoffset, extraoffset, fields, startoffset);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AbstractStruct other = (AbstractStruct) obj;
    return endoffset == other.endoffset && extraoffset == other.extraoffset && Objects.equals(fields, other.fields)
        && startoffset == other.startoffset;
  }

  /** Returns the table row index where the specified AddRemovable structure can be inserted. */
  public int getDatatypeIndex(AddRemovable addedEntry) {
    return getDatatypeIndex(addedEntry, false);
  }

  /**
   * Returns the table row index where the specified AddRemovable structure can be inserted.
   *
   * @param addedEntry      The entry to add.
   * @param ignoreSelection Whether row index calculation should ignore currently selected table row, thus always
   *                          returning the index that points to the end of the section or parent structure.
   */
  public int getDatatypeIndex(AddRemovable addedEntry, boolean ignoreSelection) {
    int index = 0;
    if (!ignoreSelection && viewer != null && viewer.getSelectedEntry() != null
        && viewer.getSelectedEntry().getClass() == addedEntry.getClass()) {
      index = viewer.getSelectedRow();
    } else if (offsetmap.containsKey(addedEntry.getClass())) {
      int offset = offsetmap.get(addedEntry.getClass()).getValue() + extraoffset;
      final int fieldCount = fields.size();
      int extraIndex = 0;
      while (extraIndex < fieldCount && fields.get(extraIndex).getOffset() < extraoffset) {
        extraIndex++;
      }
      while (index < fieldCount && fields.get(index).getOffset() < offset) {
        index++;
      }
      while (index < fieldCount && addedEntry.getClass() == fields.get(index).getClass()) {
        index++;
      }
      if (index == extraIndex) {
        SectionOffset soffset = offsetmap.get(addedEntry.getClass());
        if (soffset.getValue() == 0) {
          index = fieldCount;
          int newOffset = getSize();
          if (extraIndex > 0) {
            newOffset -= fields.get(extraIndex).getOffset();
          }
          soffset.setValue(newOffset);
        } else {
          throw new IllegalArgumentException(
              "addDatatype: No suitable index found - " + getName() + " adding " + addedEntry.getName());
        }
      }
    } else {
      index = getInsertPosition();
    }
    return index;
  }

  /** Returns whether structure of currently selected table row is compatible with "addedEntry". */
  public boolean isCompatibleDatatypeSelection(AddRemovable addedEntry) {
    return viewer != null && viewer.getSelectedEntry() != null
        && viewer.getSelectedEntry().getClass() == addedEntry.getClass();
  }

  /**
   * Determines whether the specified range of fields can be moved by one sibling position.
   *
   * @param firstIndex The first field index in the range.
   * @param lastIndex  The last field index in the range.
   * @param moveUp     Whether to move the range toward the beginning.
   * @return {@code true} if the range can be moved.
   */
  public boolean canMoveDatatypeRange(int firstIndex, int lastIndex, boolean moveUp) {
    if (firstIndex < 0 || lastIndex < firstIndex || lastIndex >= fields.size()) {
      return false;
    }

    final int neighborIndex = moveUp ? firstIndex - 1 : lastIndex + 1;
    if (neighborIndex < 0 || neighborIndex >= fields.size()) {
      return false;
    }

    final StructEntry firstEntry = fields.get(firstIndex);
    if (!(firstEntry instanceof AbstractStruct) || !(firstEntry instanceof AddRemovable)) {
      return false;
    }

    final Class<?> entryClass = firstEntry.getClass();
    int expectedOffset = firstEntry.getOffset();
    for (int i = firstIndex; i <= lastIndex; i++) {
      final StructEntry entry = fields.get(i);
      if (!(entry instanceof AbstractStruct) || !(entry instanceof AddRemovable) || entry.getClass() != entryClass
          || entry.getParent() != this || entry.getOffset() != expectedOffset) {
        return false;
      }
      expectedOffset += entry.getSize();
    }

    final StructEntry neighbor = fields.get(neighborIndex);
    if (!(neighbor instanceof AbstractStruct) || !(neighbor instanceof AddRemovable) || neighbor.getClass() != entryClass
        || neighbor.getParent() != this) {
      return false;
    }

    return moveUp ? neighbor.getOffset() + neighbor.getSize() == firstEntry.getOffset()
        : expectedOffset == neighbor.getOffset();
  }

  /**
   * Moves a contiguous range of child structures one sibling position up or down.
   *
   * @param firstIndex The first field index in the range.
   * @param lastIndex  The last field index in the range.
   * @param moveUp     Whether to move the range toward the beginning.
   * @return {@code true} if the range was moved.
   */
  public boolean moveDatatypeRange(int firstIndex, int lastIndex, boolean moveUp) {
    if (!canMoveDatatypeRange(firstIndex, lastIndex, moveUp)) {
      return false;
    }

    final List<StructEntry> movingEntries = new ArrayList<>(fields.subList(firstIndex, lastIndex + 1));
    final int movingSize = movingEntries.stream().mapToInt(StructEntry::getSize).sum();
    final int neighborIndex = moveUp ? firstIndex - 1 : lastIndex + 1;
    final StructEntry neighbor = fields.get(neighborIndex);
    final int neighborSize = neighbor.getSize();

    if (moveUp) {
      for (final StructEntry entry : movingEntries) {
        shiftEntryOffsets(entry, -neighborSize);
      }
      shiftEntryOffsets(neighbor, movingSize);
      fields.remove(neighborIndex);
      fields.add(lastIndex, neighbor);
    } else {
      for (final StructEntry entry : movingEntries) {
        shiftEntryOffsets(entry, neighborSize);
      }
      shiftEntryOffsets(neighbor, -movingSize);
      fields.remove(neighborIndex);
      fields.add(firstIndex, neighbor);
    }

    updateRemovableIndices(((AddRemovable) movingEntries.get(0)).getClass());
    setStructChanged(true);
    fireTableDataChanged();
    return true;
  }

  private static void shiftEntryOffsets(StructEntry entry, int delta) {
    entry.setOffset(entry.getOffset() + delta);
    if (entry instanceof AbstractStruct) {
      for (final StructEntry child : ((AbstractStruct) entry).fields) {
        shiftEntryOffsets(child, delta);
      }
    }
  }

  public int addDatatype(AddRemovable addedEntry) {
    return addDatatype(addedEntry, getDatatypeIndex(addedEntry));
  }

  public int addDatatype(AddRemovable addedEntry, int index) {
    // Increase count
    if (countmap.containsKey(addedEntry.getClass())) {
      countmap.get(addedEntry.getClass()).incValue(1);
    }

    // Set addedEntry offset
    if (index > 0 && fields.get(index - 1).getClass() == addedEntry.getClass()) {
      final StructEntry prev = fields.get(index - 1);
      addedEntry.setOffset(prev.getOffset() + prev.getSize());
    } else if (offsetmap.containsKey(addedEntry.getClass())) {
      addedEntry.setOffset(offsetmap.get(addedEntry.getClass()).getValue() + extraoffset);
    } else if (index == 0 && !fields.isEmpty()) {
      final StructEntry next = fields.get(0);
      addedEntry.setOffset(next.getOffset());
    } else {
      setAddRemovableOffset(addedEntry);
      for (int i = 0; i < fields.size(); i++) {
        final StructEntry structEntry = fields.get(i);
        if (structEntry.getOffset() == addedEntry.getOffset()) {
          index = i;
          break;
        }
      }
    }
    if (addedEntry instanceof AbstractStruct) {
      AbstractStruct addedStruct = (AbstractStruct) addedEntry;
      addedStruct.realignStructOffsets();
      addedStruct.superStruct = this;
    }
    AbstractStruct topStruct = this;
    while (topStruct.superStruct != null) {
      if (topStruct instanceof Resource) {
        topStruct.endoffset += addedEntry.getSize();
        adjustSectionOffsets(topStruct, addedEntry, addedEntry.getSize());
      }
      topStruct = topStruct.superStruct;
    }
    if (topStruct instanceof Resource) {
      topStruct.endoffset += addedEntry.getSize();
    }
    adjustEntryOffsets(topStruct, this, addedEntry, addedEntry.getSize());
    adjustSectionOffsets(topStruct, addedEntry, addedEntry.getSize());

    addField(addedEntry, index);
    datatypeAdded(addedEntry);
    if (superStruct != null) {
      superStruct.datatypeAddedInChild(this, addedEntry);
    }
    setStructChanged(true);
    fireTableRowsInserted(index, index);
    updateRemovableIndices(addedEntry.getClass());
    return index;
  }

  /**
   * Adds the specified entry as a new field to the current structure.
   *
   * @param entry The new field to add.
   * @return The added field.
   */
  public <T extends StructEntry> T addField(T entry) {
    return addField(entry, fields.size());
  }

  /**
   * Inserts the specified entry as a new field at the given position to the current structure.
   *
   * @param entry The new field to add.
   * @param index The desired position of the new field.
   * @return The inserted field.
   */
  public <T extends StructEntry> T addField(T entry, int index) {
    if (entry != null) {
      if (index < 0) {
        index = 0;
      } else if (index > fields.size()) {
        index = fields.size();
      }
      entry.setParent(this);
      fields.add(index, entry);
    }
    return entry;
  }

  /** Adds list of entries to the AbstractStruct table after the specified index. */
  public void addFields(int startIndex, List<StructEntry> toBeAdded) {
    if (toBeAdded != null) {
      int i = Math.max(-1, Math.min(fields.size() - 1, startIndex));
      for (final StructEntry e : toBeAdded) {
        addField(e, ++i);
      }
    }
  }

  /** Adds list of entries to the AbstractStruct table after the specified StructEntry object. */
  public void addFields(StructEntry startFromEntry, List<StructEntry> toBeAdded) {
    if (toBeAdded != null) {
      int i = fields.indexOf(startFromEntry);
      for (final StructEntry e : toBeAdded) {
        addField(e, ++i);
      }
    }
  }

  /**
   * Removes all field entries from the list.
   */
  public void clearFields() {
    final Iterator<StructEntry> iter = fields.iterator();
    while (iter.hasNext()) {
      StructEntry e = iter.next();
      e.setParent(null);
      iter.remove();
    }
  }

  /**
   * Returns the lowest-level structure located at the specified offset.
   *
   * @param offset The offset of the structure to find.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(int offset) {
    return getAttribute(this, offset, StructEntry.class, true);
  }

  /**
   * Returns the structure located at the specified offset.
   *
   * @param offset    The offset of the structure to find.
   * @param recursive If true, returns the lowest-level structure at the specified offset. If false, returns the
   *                  first-level structure at the specified offset.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(int offset, boolean recursive) {
    return getAttribute(this, offset, StructEntry.class, recursive);
  }

  /**
   * Returns the lowest-level structure of the given type, located at the specified offset.
   *
   * @param offset The offset of the structure to find.
   * @param type   The type of structure to find.
   * @param <T>    The static type of structure to find.
   *
   * @return The matching structure, or null if not found.
   */
  public <T extends StructEntry> T getAttribute(int offset, Class<T> type) {
    return getAttribute(this, offset, type, true);
  }

  /**
   * Returns the structure of the given type, located at the specified offset.
   *
   * @param offset    The offset of the structure to find.
   * @param type      The type of structure to find.
   * @param recursive If true, returns the lowest-level structure at the specified offset. If false, returns the
   *                  first-level structure at the specified offset.
   * @param <T>       The static type of structure to find.
   *
   * @return The matching structure, or null if not found.
   */
  public <T extends StructEntry> T getAttribute(int offset, Class<T> type, boolean recursive) {
    return getAttribute(this, offset, type, recursive);
  }

  /**
   * Returns the lowest-level structure, matching the given field name.
   *
   * @param ename The field name of the structure.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(String ename) {
    return getAttribute(this, ename, true);
  }

  /**
   * Returns the lowest-level structure, matching the given field name.
   *
   * @param ename     The field name of the structure.
   * @param recursive If true, returns the lowest-level structure matching the given name. If false, returns the
   *                  first-level structure matching the given name.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(String ename, boolean recursive) {
    return getAttribute(this, ename, recursive);
  }

  private static <T extends StructEntry> T getAttribute(AbstractStruct parent, int offset, Class<T> type,
      boolean recursive) {
    for (final StructEntry field : parent.fields) {
      final int off = field.getOffset();
      T result = null;
      if (offset >= off && offset < off + field.getSize() && type.isInstance(field)) {
        // Do not return immidiatly - first try to find the same class lower on hierarchy
        result = type.cast(field);
      }

      if (recursive && field instanceof AbstractStruct) {
        final T result2 = getAttribute((AbstractStruct) field, offset, type, recursive);
        if (result2 != null) {
          return result2;
        }
      }
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static StructEntry getAttribute(AbstractStruct parent, String name, boolean recursive) {
    if (name != null && !name.isEmpty()) {
      for (StructEntry field : parent.fields) {
        StructEntry result = null;
        if (field.getName().equals(name)) {
          // Do not return immidiatly - first try to find the same class lower on hierarchy
          result = field;
        }
        if (recursive && field instanceof AbstractStruct) {
          field = getAttribute((AbstractStruct) field, name, recursive);
          if (field != null) {
            return field;
          }
        }
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  public int getEndOffset() {
    return endoffset;
  }

  public int getExtraOffset() {
    return extraoffset;
  }

  /**
   * Returns modifiable list of fields of this structure. Modification of this list do not notify listeners.
   *
   * @return Internal list of fields
   */
  public List<StructEntry> getFields() {
    return fields;
  }

  /**
   * Returns an unmodifiable list of fields of this structure matching the specified structure type.
   *
   * @param type The class type to filter. Specify {@code null} to return all fields of this structure.
   * @return Unmodifiable list of fields of {@code type}.
   */
  public List<StructEntry> getFields(Class<? extends StructEntry> type) {
    return Collections.unmodifiableList(fields.stream()
        .filter(se -> type == null || type.isAssignableFrom(se.getClass())).collect(Collectors.toList()));
  }

  /**
   * Returns the first {@code StructEntry} object of the specified class type.
   *
   * @param type   Class of the {@code StructEntry} object to return.
   * @param offset Start offset to search {@code StructEntry} instances.
   * @return First available {@code StructEntry} instance, {@code null} otherwise.
   */
  public StructEntry getField(Class<? extends StructEntry> type, int offset) {
    return fields.stream()
        .filter(se -> se.getOffset() >= offset && (type == null || type.isAssignableFrom(se.getClass()))).findFirst()
        .orElse(null);
  }

  /**
   * Returns the StructEntry object at the specified index.
   *
   * @param index The index of the desired StructEntry object.
   * @return The StructEntry object, or null if not available.
   */
  public StructEntry getField(int index) {
    try {
      return fields.get(index);
    } catch (IndexOutOfBoundsException e) {
      Logger.trace(e);
    }
    return null;
  }

  public List<StructEntry> getFlatFields() {
    final List<StructEntry> flatList = new ArrayList<>(2 * fields.size());
    fillFlatFields(flatList);
    Collections.sort(flatList);
    return flatList;
  }

  /**
   * Returns the {@link ResourceEntry} associated with this structure. May return {@code null} if the structure
   * is not directly associated with a resource.
   */
  public ResourceEntry getResourceEntry() {
    return entry;
  }

  /**
   * Returns arbitrary data that can be assigned to this structure in the constructor.
   *
   * This is a workaround to associate data for methods that are called by the {@code AbstractStruct} constructor
   * before the derived class can process the data further.
   */
  public Object getExtraData() {
    return extraData;
  }

  public AbstractStruct getSuperStruct(StructEntry structEntry) {
    for (final StructEntry e : fields) {
      if (e == structEntry) {
        return this;
      }
      if (e instanceof AbstractStruct) {
        final AbstractStruct result = ((AbstractStruct) e).getSuperStruct(structEntry);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  /**
   * Returns whether any parent of the current AbstractStruct object is an instance of the specified class type.
   */
  public boolean isChildOf(Class<? extends AbstractStruct> struct) {
    if (struct != null) {
      AbstractStruct parent = superStruct;
      while (parent != null) {
        if (struct.isInstance(parent)) {
          return true;
        }
        parent = parent.superStruct;
      }
    }
    return false;
  }

  public StructViewer getViewer() {
    return viewer;
  }

  public void realignStructOffsets() {
    int offset = startoffset;
    for (final StructEntry e : fields) {
      e.setOffset(offset);
      offset += e.getSize();
      if (e instanceof AbstractStruct) {
        ((AbstractStruct) e).realignStructOffsets();
      }
    }
  }

  public List<AddRemovable> removeAllRemoveables() {
    final List<AddRemovable> removed = new ArrayList<>();
    for (int i = 0; i < fields.size(); i++) {
      final StructEntry o = fields.get(i);
      if (o instanceof AddRemovable) {
        removeDatatype((AddRemovable) o, false);
        removed.add((AddRemovable) o);
        i--;
      }
    }
    return removed;
  }

  public void removeDatatype(AddRemovable removedEntry, boolean removeRecurse) {
    if (removeRecurse && removedEntry instanceof HasChildStructs) {
      // Recursively remove child structures first
      AbstractStruct removedStruct = (AbstractStruct) removedEntry;
      for (int i = 0; i < removedStruct.fields.size(); i++) {
        final StructEntry o = removedStruct.fields.get(i);
        if (o instanceof AddRemovable) {
          removedStruct.removeDatatype((AddRemovable) o, removeRecurse);
          i--;
        }
      }
    }
    final int index = fields.indexOf(removedEntry);
    fireTableRowsWillBeDeleted(index, index);
    fields.remove(index);
    // decrease count
    if (countmap != null && countmap.containsKey(removedEntry.getClass())) {
      countmap.get(removedEntry.getClass()).incValue(-1);
    }
    // decrease offsets
    AbstractStruct topStruct = this;
    while (topStruct.superStruct != null) {
      if (topStruct instanceof Resource) {
        topStruct.endoffset -= removedEntry.getSize();
        adjustSectionOffsets(topStruct, removedEntry, -removedEntry.getSize());
      }
      topStruct = topStruct.superStruct;
    }
    if (topStruct instanceof Resource) {
      topStruct.endoffset -= removedEntry.getSize();
    }
    adjustEntryOffsets(topStruct, this, removedEntry, -removedEntry.getSize());
    adjustSectionOffsets(topStruct, removedEntry, -removedEntry.getSize());
    datatypeRemoved(removedEntry);
    if (superStruct != null) {
      superStruct.datatypeRemovedInChild(this, removedEntry);
    }
    fireTableRowsDeleted(index, index);
    updateRemovableIndices(removedEntry.getClass());
    setStructChanged(true);
  }

  /**
   * Removes the specified entry from the current structure.
   *
   * @param entry The entry to remove.
   * @return true if the current structure contained the given entry, false otherwise.
   */
  public boolean removeField(StructEntry entry) {
    if (entry != null) {
      if (fields.remove(entry)) {
        entry.setParent(null);
        return true;
      }
    }
    return false;
  }

  /**
   * Removes the entry at the specified index.
   *
   * @param index The index of the entry to remove.
   * @return The removed entry if found, null otherwise.
   */
  public StructEntry removeField(int index) {
    if (index >= 0 && index < fields.size()) {
      StructEntry e = fields.remove(index);
      if (e != null) {
        e.setParent(null);
      }
      return e;
    }
    return null;
  }

  public ByteBuffer removeFromList(StructEntry startFromEntry, int numBytes) throws IOException {
    int startindex = fields.indexOf(startFromEntry) + 1;
    int endindex = startindex;
    int len = 0;
    // getting total size
    int maxLen = 0;
    for (int i = startindex, cnt = fields.size(); i < cnt && maxLen < numBytes; i++) {
      maxLen += fields.get(i).getSize();
    }
    // filling buffer
    ByteBuffer bb = StreamUtils.getByteBuffer(maxLen);
    try (ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb)) {
      while (len < maxLen) {
        StructEntry e = fields.get(endindex++);
        len += e.getSize();
        e.write(bbos);
      }
    }
    // discard entries
    if (endindex > startindex) {
      fields.subList(startindex, endindex).clear();
    }
    bb.position(0);
    return bb;
  }

  /** Replaces an old StructEntry instance by the specified instance if offset and size are equal. */
  public boolean replaceField(StructEntry newEntry) {
    if (newEntry != null) {
      final ListIterator<StructEntry> it = fields.listIterator();
      while (it.hasNext()) {
        final StructEntry oldEntry = it.next();
        if (oldEntry.getOffset() == newEntry.getOffset() && oldEntry.getSize() == newEntry.getSize()) {
          it.set(newEntry);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Replaces value of the specified field to specified value and notify listeners.
   *
   * @param index       Index of the field to update
   * @param structEntry New value of the field
   * @throws IndexOutOfBoundsException If the index is out of range ({@code index < 0 || index >= getList().size()})
   */
  public void setField(int index, StructEntry structEntry) {
    fields.set(index, structEntry);
    fireTableRowsUpdated(index, index);
  }

  public boolean hasStructChanged() {
    return structChanged;
  }

  public void setStructChanged(boolean changed) {
    structChanged = changed;
    if (superStruct != null) {
      superStruct.setStructChanged(changed);
    }
  }

  /**
   * Ensures that all {@link AddRemovable} of the specified type are properly indexed in sequential order.
   *
   * @param cls Class type of the AddRemovables to update.
   */
  public void updateRemovableIndices(Class<? extends AddRemovable> cls) {
    if (cls == null) {
      return;
    }

    final List<StructEntry> fieldList = new ArrayList<>(getFields(cls));
    if (fieldList.isEmpty()) {
      return;
    }
    fieldList.sort(Comparator.comparingInt(StructEntry::getOffset));

    int minIndex = Integer.MAX_VALUE;
    int maxIndex = Integer.MIN_VALUE;
    final Pattern patName = Pattern.compile("^(.+) ([0-9]+)$");
    for (int i = 0, size = fieldList.size(); i < size; i++) {
      final StructEntry entry = fieldList.get(i);
      final Matcher m = patName.matcher(entry.getName());
      final String name;
      if (m.matches()) {
        // existing or copy-pasted entry
        name = m.group(1);
//        System.out.printf("Existing/pasted: name=%s, newIndex=%d\n", entry.getName(), i);
      } else {
        // newly added entry
        name = entry.getName();
//        System.out.printf("Newly added: name=%s, newIndex=%d\n", name, i);
      }

      final String newName = name + " " + i;
      entry.setName(newName);

      final int fieldIdx = fields.indexOf(entry);
      minIndex = Math.min(minIndex, fieldIdx);
      maxIndex = Math.max(maxIndex, fieldIdx);
    }

    if (minIndex != Integer.MAX_VALUE && maxIndex != Integer.MIN_VALUE) {
      fireTableRowsUpdated(minIndex, maxIndex);
    }
  }

  public String toMultiLineString() {
    final StringBuilder sb = new StringBuilder(30 * fields.size());
    for (final StructEntry e : fields) {
      sb.append(e.getName()).append(": ").append(e).append('\n');
    }
    return sb.toString();
  }

  /** Returns the SectionOffset entry linked to the specified StructEntry object if available. */
  public SectionOffset getSectionOffset(Class<? extends StructEntry> cls) {
    return offsetmap.get(cls);
  }

  /** Returns the SectionCount entry linked to the specified StructEntry object if available. */
  public SectionCount getSectionCount(Class<? extends StructEntry> cls) {
    return countmap.get(cls);
  }

  /** Updates class mapping of the SectionOffset instance associated with the specified class type. */
  public SectionOffset updateSectionOffset(Class<? extends StructEntry> cls) {
    SectionOffset so = offsetmap.getOrDefault(cls, null);
    if (so != null && !so.getSection().equals(cls)) {
      offsetmap.remove(cls);
      offsetmap.put(so.getSection(), so);
    }
    return so;
  }

  /** Updates class mapping of the SectionCount instance associated with the specified class type. */
  public SectionCount updateSectionCount(Class<? extends StructEntry> cls) {
    SectionCount sc = countmap.getOrDefault(cls, null);
    if (sc != null && !sc.getSection().equals(cls)) {
      countmap.remove(cls);
      countmap.put(sc.getSection(), sc);
    }
    return sc;
  }

  private void fillFlatFields(List<StructEntry> flatList) {
    for (final StructEntry e : fields) {
      if (e instanceof AbstractStruct) {
        ((AbstractStruct) e).fillFlatFields(flatList);
      } else if (e instanceof AbstractCode) {
        ((AbstractCode) e).addFlatList(flatList);
      } else {
        flatList.add(e);
      }
    }
  }

  public boolean hasViewTab() {
    return (viewer != null && viewer.hasViewTab());
  }

  public boolean isViewTabSelected() {
    return (viewer != null && viewer.isViewTabSelected());
  }

  public void selectViewTab() {
    if (viewer != null) {
      viewer.selectViewTab();
    }
  }

  public boolean isEditTabSelected() {
    return (viewer != null && viewer.isEditTabSelected());
  }

  public void selectEditTab() {
    if (viewer != null) {
      viewer.selectEditTab();
    }
  }

  public boolean hasRawTab() {
    return (viewer != null && viewer.hasRawTab());
  }

  public boolean isRawTabSelected() {
    return (viewer != null && viewer.isRawTabSelected());
  }

  public void selectRawTab() {
    if (viewer != null) {
      viewer.selectRawTab();
    }
  }

  /** To be overriden by subclasses. */
  protected void viewerInitialized(StructViewer viewer) {
  }

  /** To be overriden by subclasses. */
  protected void datatypeAdded(AddRemovable datatype) {
  }

  /** To be overriden by subclasses. */
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype) {
    if (superStruct != null) {
      superStruct.datatypeAddedInChild(child, datatype);
    }
  }

  /** To be overriden by subclasses. */
  protected void datatypeRemoved(AddRemovable datatype) {
  }

  /** To be overriden by subclasses. */
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype) {
    if (superStruct != null) {
      superStruct.datatypeRemovedInChild(child, datatype);
    }
  }

  private void fixHoles(ByteBuffer buffer) {
    int offset = startoffset;
    final List<StructEntry> flatList = getFlatFields();
    for (int i = 0; i < flatList.size(); i++) {
      StructEntry se = flatList.get(i);
      int delta = se.getOffset() - offset;
      if (se.getSize() > 0 && delta > 0) {
        Unknown hole = new Unknown(buffer, offset, delta, COMMON_UNUSED_BYTES);
        fields.add(hole);
        flatList.add(i, hole);
        Logger.warn("Hole: {} off: {} h len: {}", name, Integer.toHexString(offset), delta);
        i++;
      }
      // Using max() as shared data regions may confuse the hole detection algorithm
      offset = Math.max(offset, se.getOffset() + se.getSize());
    }
    if (endoffset < buffer.limit()) { // Does this break anything?
      fields.add(new Unknown(buffer, endoffset, buffer.limit() - endoffset, COMMON_UNUSED_BYTES));
      Logger.warn("Hole: {} off: {} h len: {}", name, Integer.toHexString(endoffset), (buffer.limit() - endoffset));
      endoffset = buffer.limit();
    }
  }

  /** To be overriden by subclasses. */
  protected int getInsertPosition() {
    return fields.size(); // Default: Add at end
  }

  private void initAddStructMaps() {
    countmap = new HashMap<>();
    offsetmap = new HashMap<>();
    for (final StructEntry e : fields) {
      if (e instanceof SectionOffset) {
        final SectionOffset so = (SectionOffset) e;
        if (so.getSection() != null) {
          offsetmap.put(so.getSection(), so);
        }
      } else if (e instanceof SectionCount) {
        countmap.put(((SectionCount) e).getSection(), (SectionCount) e);
      }
    }
  }

  /** To be overriden by subclasses. */
  protected void setAddRemovableOffset(AddRemovable datatype) {
  }

  protected void setExtraOffset(int offset) {
    extraoffset = offset;
  }

  protected void setStartOffset(int offset) {
    startoffset = offset;
  }

  protected void writeFlatFields(OutputStream os) throws IOException {
    for (final StructEntry e : getFlatFields()) {
      e.write(os);
    }
  }

  /**
   * Notifies all listeners that rows in the range {@code [firstRow, lastRow]}, inclusive, will be deleted. this is the
   * last chance to get values of the deleted rows.
   *
   * @param firstRow the first row
   * @param lastRow  the last row
   *
   * @see TableModelEvent
   * @see EventListenerList
   */
  public void fireTableRowsWillBeDeleted(int firstRow, int lastRow) {
    fireTableChanged(new TableModelEvent(this, firstRow, lastRow, TableModelEvent.ALL_COLUMNS, WILL_BE_DELETE));
  }

  /**
   * Add a PropertyChangeListener to the listener list. The listener is registered for all properties. The same listener
   * object may be added more than once, and will be called as many times as it is added.
   * <p>
   * If {@code listener} is null, no exception is thrown and no action is taken.
   *
   * @param listener The PropertyChangeListener to be added
   */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.addPropertyChangeListener(listener);
  }

  /**
   * Remove a PropertyChangeListener from the listener list. This removes a PropertyChangeListener that was registered
   * for all properties. If {@code listener} was added more than once to the same event source, it will be notified one
   * less time after being removed.
   * <p>
   * If {@code listener} is null, or was never added, no exception is thrown and no action is taken.
   *
   * @param listener The PropertyChangeListener to be removed
   */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    changeSupport.firePropertyChange(evt);
  }
}
