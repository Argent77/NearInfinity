// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.StructViewer;
import org.infinity.resource.are.Actor;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.dlg.AbstractCode;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.io.ByteBufferOutputStream;
import org.infinity.util.io.StreamUtils;

public abstract class AbstractStruct extends AbstractTableModel implements StructEntry, Viewable, Closeable,
        PropertyChangeListener
{
  // Commonly used field labels
  public static final String COMMON_SIGNATURE     = "Signature";
  public static final String COMMON_VERSION       = "Version";
  public static final String COMMON_UNKNOWN       = "Unknown";
  public static final String COMMON_UNUSED        = "Unused";
  public static final String COMMON_UNUSED_BYTES  = "Unused bytes?";

  /** Identifies the intention to removal of rows or columns. */
  public static final int WILL_BE_DELETE = -2;

  private List<StructEntry> list;
  private AbstractStruct superStruct;
  private Map<Class<? extends StructEntry>, SectionCount> countmap;
  private Map<Class<? extends StructEntry>, SectionOffset> offsetmap;
  private ResourceEntry entry;
  private String name;
  private StructViewer viewer;
  private boolean structChanged;
  private int startoffset, endoffset, extraoffset;
  private Collection<Component> viewerComponents = null;
  /**
   * If any {@link PropertyChangeListener}s have been registered,
   * the {@code changeSupport} field describes them.
   *
   * @see #addPropertyChangeListener
   * @see #removePropertyChangeListener
   */
  private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

  private static void adjustEntryOffsets(AbstractStruct superStruct, AbstractStruct modifiedStruct,
                                         AddRemovable datatype, int amount)
  {
    for (int i = 0; i < superStruct.getFieldCount(); i++) {
      StructEntry structEntry = superStruct.getField(i);
      if (structEntry.getOffset() > datatype.getOffset() ||
          structEntry.getOffset() == datatype.getOffset() && structEntry != datatype &&
          structEntry != modifiedStruct) {
        structEntry.setOffset(structEntry.getOffset() + amount);
      }
      if (structEntry instanceof AbstractStruct)
        adjustEntryOffsets((AbstractStruct)structEntry, modifiedStruct, datatype, amount);
    }
  }

  private static void adjustSectionOffsets(AbstractStruct superStruct, AddRemovable datatype, int amount)
  {
    for (int i = 0; i < superStruct.getFieldCount(); i++) {
      Object o = superStruct.getField(i);
      if (o instanceof SectionOffset) {
        SectionOffset sOffset = (SectionOffset)o;
        if (sOffset.getValue() + superStruct.getExtraOffset() > datatype.getOffset()) {
          sOffset.incValue(amount);
        }
        else if (sOffset.getValue() + superStruct.getExtraOffset() == datatype.getOffset()) {
          if (amount > 0 &&
              !(sOffset.getSection() == datatype.getClass() ||
                Profile.getEngine() == Profile.Engine.IWD2 &&
                superStruct instanceof CreResource)) {
            sOffset.incValue(amount);
          }
        }
      }
    }
  }

  protected AbstractStruct()
  {
  }

  protected AbstractStruct(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
    list = new ArrayList<>();
    name = entry.toString();
    ByteBuffer bb = entry.getResourceBuffer();
    endoffset = read(bb, 0);
    if (this instanceof HasAddRemovable && !list.isEmpty()) {// Is this enough?
      Collections.sort(list); // This way we can writeField out in the order in list - sorted by offset
      fixHoles((ByteBuffer)bb.position(0));
      initAddStructMaps();
    }
  }

  protected AbstractStruct(AbstractStruct superStruct, String name, int startoffset, int listSize)
  {
    this.superStruct = superStruct;
    this.name = name;
    this.startoffset = startoffset;
    list = new ArrayList<>(listSize);
  }

  protected AbstractStruct(AbstractStruct superStruct, String name, ByteBuffer buffer, int startoffset)
          throws Exception
  {
    this(superStruct, name, buffer, startoffset, 10);
  }

  protected AbstractStruct(AbstractStruct superStruct, String name, ByteBuffer buffer, int startoffset,
                           int listSize) throws Exception
  {
    this(superStruct, name, startoffset, listSize);
    endoffset = read(buffer, startoffset);
    if (this instanceof HasAddRemovable) {
      if (!(this instanceof Actor)) {  // Is this enough?
        Collections.sort(list); // This way we can writeField out in the order in list - sorted by offset
      }
      initAddStructMaps();
    }
  }

// --------------------- Begin Interface Closeable ---------------------

  // end - extends AbstractTableModel

  // begin - implements Closeable
  @Override
  public void close() throws Exception
  {
    if (structChanged && viewer != null && this instanceof Resource && superStruct == null) {
      ResourceFactory.closeResource((Resource)this, entry, viewer);
    }
    if (viewer != null) {
      viewer.close();
    }
  }

// --------------------- End Interface Closeable ---------------------


// --------------------- Begin Interface Comparable ---------------------

  // begin - implements StructEntry
  @Override
  public int compareTo(StructEntry o)
  {
    return getOffset() - o.getOffset();
  }

// --------------------- End Interface Comparable ---------------------


// --------------------- Begin Interface StructEntry ---------------------

  @Override
  public AbstractStruct clone() throws CloneNotSupportedException
  {
    final AbstractStruct newstruct = (AbstractStruct)super.clone();
    newstruct.superStruct = null;
    newstruct.list = new ArrayList<>(list.size());
    newstruct.viewer = null;
    for (StructEntry e : list)
      newstruct.list.add(e.clone());
//    for (Iterator i = newstruct.list.iterator(); i.hasNext();) {
//      StructEntry sentry = (StructEntry)i.next();
//      if (sentry.getOffset() <= 0)
//        break;
//      sentry.setOffset(sentry.getOffset() - newstruct.getOffset());
//    }
    newstruct.initAddStructMaps();
    return newstruct;
  }

  @Override
  public void copyNameAndOffset(StructEntry structEntry)
  {
    name = structEntry.getName();
    setOffset(structEntry.getOffset());
  }

  @Override
  public String getName()
  {
    return name;
  }

  @Override
  public void setName(String newName)
  {
    if (newName != null) {
      name = newName;
    } else {
      throw new NullPointerException();
    }
  }

  @Override
  public int getOffset()
  {
    return startoffset;
  }

  @Override
  public StructEntry getParent()
  {
    return getSuperStruct();
  }

  @Override
  public int getSize()
  {
    return endoffset - startoffset;
  }

  @Override
  public ByteBuffer getDataBuffer()
  {
    ByteBuffer bb = ByteBuffer.allocate(getSize());
    try (ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb)) {
      writeFlatList(bbos);
    } catch (IOException e) {
      e.printStackTrace();
    }
    bb.position(0);
    return bb;
  }

  @Override
  public List<StructEntry> getStructChain()
  {
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
  public void setOffset(int newoffset)
  {
    if (extraoffset != 0)
      extraoffset += newoffset - startoffset;
    int delta = getSize();
    startoffset = newoffset;
    endoffset = newoffset + delta;
  }

  @Override
  public void setParent(StructEntry parent)
  {
    if (parent instanceof AbstractStruct) {
      setSuperStruct((AbstractStruct)parent);
    } else {
      setSuperStruct(null);
    }
  }

// --------------------- End Interface StructEntry ---------------------


// --------------------- Begin Interface TableModel ---------------------

  // start - extends AbstractTableModel
  @Override
  public int getRowCount()
  {
    return getFieldCount();
  }

  @Override
  public int getColumnCount()
  {
    if (BrowserMenuBar.getInstance().showOffsets())
      return 3;
    return 2;
  }

  @Override
  public Object getValueAt(int row, int column)
  {
    if (getField(row) instanceof StructEntry) {
      StructEntry data = getField(row);
      switch (column) {
        case 0:
          return data.getName();
        case 1:
          return data;
        case 2:
          return Integer.toHexString(data.getOffset()) + " h";
      }
    }
    return "Unknown datatype";
  }

// --------------------- End Interface TableModel ---------------------


// --------------------- Begin Interface Viewable ---------------------

  // end - implements Closeable

  // begin - implements Viewable
  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    if (viewer == null) {
      viewer = new StructViewer(this, viewerComponents);
      viewerInitialized(viewer);
    }
    return viewer;
  }

// --------------------- End Interface Viewable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  // begin - implements Writeable
  @Override
  public void write(OutputStream os) throws IOException
  {
    Collections.sort(getList()); // This way we can writeField out in the order in list - sorted by offset
    for (int i = 0, count = getFieldCount(); i < count; i++) {
      getField(i).write(os);
    }
  }

// --------------------- End Interface Writeable ---------------------

  @Override
  public String getColumnName(int columnIndex)
  {
    if (columnIndex == 0)
      return "Attribute";
    if (columnIndex == 1)
      return "Value";
    return "Offset";
  }

  @Override
  public boolean isCellEditable(int row, int col)
  {
    if (col == 1) {
      Object o = getValueAt(row, col);
      if (o instanceof InlineEditable && !(o instanceof Editable))
        return true;
    }
    return false;
  }

  @Override
  public void setValueAt(Object value, int row, int column)
  {
    Object o = getValueAt(row, column);
    if (o instanceof InlineEditable) {
      if (!((InlineEditable)o).update(value))
        JOptionPane.showMessageDialog(viewer, "Error updating value", "Error", JOptionPane.ERROR_MESSAGE);
      else {
        fireTableCellUpdated(row, column);
        setStructChanged(true);
      }
    }
  }

  @Override
  public String toString()
  {
    // limit text length to speed things up
    int capacity = 160;
    final StringBuilder sb = new StringBuilder(capacity);
    for (int i = 0, count = getFieldCount(); i < count; i++) {
      final StructEntry field = getField(i);
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

  // Returns the table row index where the specified AddRemovable structure can be inserted
  public int getDatatypeIndex(AddRemovable addedEntry)
  {
    int index = 0;
    if (viewer != null && viewer.getSelectedEntry() != null &&
        viewer.getSelectedEntry().getClass() == addedEntry.getClass()) {
      index = viewer.getSelectedRow();
    } else if (offsetmap.containsKey(addedEntry.getClass())) {
      int offset = offsetmap.get(addedEntry.getClass()).getValue() + extraoffset;
      int fieldCount = getFieldCount();
      int extraIndex = 0;
      while (extraIndex < fieldCount && getField(extraIndex).getOffset() < extraoffset) {
        extraIndex++;
      }
      while (index < fieldCount && getField(index).getOffset() < offset) {
        index++;
      }
      while (index < fieldCount && addedEntry.getClass() == (getField(index)).getClass()) {
        index++;
      }
      if (index == extraIndex) {
        SectionOffset soffset = offsetmap.get(addedEntry.getClass());
        if (soffset.getValue() == 0) {
          index = fieldCount;
          int newOffset = getSize();
          if (extraIndex > 0) {
            newOffset -= getField(extraIndex).getOffset();
          }
          soffset.setValue(newOffset);
        }
        else
          throw new IllegalArgumentException(
                  "addDatatype: No suitable index found - " + getName() + " adding " + addedEntry.getName());
      }
    }
    else {
      index = getAddedPosition();
    }
    return index;
  }

  // Returns whether structure of currently selected table row is compatible with "addedEntry"
  public boolean isCompatibleDatatypeSelection(AddRemovable addedEntry)
  {
    return viewer != null && viewer.getSelectedEntry() != null &&
           viewer.getSelectedEntry().getClass() == addedEntry.getClass();
  }

  public int addDatatype(AddRemovable addedEntry)
  {
    return addDatatype(addedEntry, getDatatypeIndex(addedEntry));
  }

  public int addDatatype(AddRemovable addedEntry, int index)
  {
    // Increase count
    if (countmap.containsKey(addedEntry.getClass()))
      countmap.get(addedEntry.getClass()).incValue(1);

    // Set addedEntry offset
    if (index > 0 && getField(index - 1).getClass() == addedEntry.getClass()) {
      StructEntry prev = getField(index - 1);
      addedEntry.setOffset(prev.getOffset() + prev.getSize());
    }
    else if (offsetmap.containsKey(addedEntry.getClass())) {
      addedEntry.setOffset(offsetmap.get(addedEntry.getClass()).getValue() + extraoffset);
    }
    else if (index == 0 && getFieldCount() > 0) {
      StructEntry next = getField(0);
      addedEntry.setOffset(next.getOffset());
    }
    else {
      setAddRemovableOffset(addedEntry);
      for (int i = 0; i < getFieldCount(); i++) {
        StructEntry structEntry = getField(i);
        if (structEntry.getOffset() == addedEntry.getOffset()) {
          index = i;
          break;
        }
      }
    }
    if (addedEntry instanceof AbstractStruct) {
      AbstractStruct addedStruct = (AbstractStruct)addedEntry;
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
    if (topStruct instanceof Resource)
      topStruct.endoffset += addedEntry.getSize();
    adjustEntryOffsets(topStruct, this, addedEntry, addedEntry.getSize());
    adjustSectionOffsets(topStruct, addedEntry, addedEntry.getSize());

    addField(addedEntry, index);
    datatypeAdded(addedEntry);
    if (superStruct != null)
      superStruct.datatypeAddedInChild(this, addedEntry);
    setStructChanged(true);
    fireTableRowsInserted(index, index);
    return index;
  }

  /**
   * Adds the specified entry as a new field to the current structure.
   * @param entry The new field to add.
   * @return The added field.
   */
  public StructEntry addField(StructEntry entry)
  {
    return addField(entry, getFieldCount());
  }

  /**
   * Inserts the specified entry as a new field at the given position to the current structure.
   * @param entry The new field to add.
   * @param index The desired position of the new field.
   * @return The inserted field.
   */
  public StructEntry addField(StructEntry entry, int index)
  {
    if (entry != null) {
      if (index < 0) index = 0; else if (index > list.size()) index = list.size();
      entry.setParent(this);
      list.add(index, entry);
    }
    return entry;
  }

  /** Adds list of entries to the AbstractStruct table after the specified index. */
  public void addToList(int startIndex, List<StructEntry> toBeAdded)
  {
    if (toBeAdded != null) {
      startIndex = Math.max(-1, Math.min(list.size() - 1, startIndex));
      for (int i = 0; i < toBeAdded.size(); i++) {
        addField(toBeAdded.get(i), startIndex+i+1);
      }
    }
  }

  /** Adds list of entries to the AbstractStruct table after the specified StructEntry object. */
  public void addToList(StructEntry startFromEntry, List<StructEntry> toBeAdded)
  {
    if (toBeAdded != null) {
      int startIndex = list.indexOf(startFromEntry) + 1;
      for (int i = 0; i < toBeAdded.size(); i++) {
        addField(toBeAdded.get(i), startIndex+i);
      }
    }
  }

  /**
   * Removes all field entries from the list.
   */
  public void clearFields()
  {
    Iterator<StructEntry> iter = list.iterator();
    while (iter.hasNext()) {
      StructEntry e = iter.next();
      e.setParent(null);
      iter.remove();
    }
  }

  /**
   * Returns the lowest-level structure located at the specified offset.
   * @param offset The offset of the structure to find.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(int offset)
  {
    return getAttribute(this, offset, StructEntry.class, true);
  }

  /**
   * Returns the structure located at the specified offset.
   * @param offset The offset of the structure to find.
   * @param recursive If true, returns the lowest-level structure at the specified offset.
   *                  If false, returns the first-level structure at the specified offset.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(int offset, boolean recursive)
  {
    return getAttribute(this, offset, StructEntry.class, recursive);
  }

  /**
   * Returns the lowest-level structure of the given type, located at the specified offset.
   *
   * @param offset The offset of the structure to find.
   * @param type The type of structure to find.
   * @param <T> The static type of structure to find.
   *
   * @return The matching structure, or null if not found.
   */
  public <T extends StructEntry> T getAttribute(int offset, Class<T> type)
  {
    return getAttribute(this, offset, type, true);
  }

  /**
   * Returns the structure of the given type, located at the specified offset.
   *
   * @param offset The offset of the structure to find.
   * @param type The type of structure to find.
   * @param recursive If true, returns the lowest-level structure at the specified offset.
   *                  If false, returns the first-level structure at the specified offset.
   * @param <T> The static type of structure to find.
   *
   * @return The matching structure, or null if not found.
   */
  public <T extends StructEntry> T getAttribute(int offset, Class<T> type, boolean recursive)
  {
    return getAttribute(this, offset, type, recursive);
  }

  /**
   * Returns the lowest-level structure, matching the given field name.
   * @param ename The field name of the structure.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(String ename)
  {
    return getAttribute(this, ename, true);
  }

  /**
   * Returns the lowest-level structure, matching the given field name.
   * @param ename The field name of the structure.
   * @param recursive If true, returns the lowest-level structure matching the given name.
   *                  If false, returns the first-level structure matching the given name.
   * @return The matching structure, or null if not found.
   */
  public StructEntry getAttribute(String ename, boolean recursive)
  {
    return getAttribute(this, ename, recursive);
  }

  private static <T extends StructEntry> T getAttribute(AbstractStruct parent, int offset,
                                                        Class<T> type, boolean recursive)
  {
    for (final StructEntry field : parent.getList()) {
      final int off = field.getOffset();
      T result = null;
      if (offset >= off && offset < off + field.getSize() && type.isInstance(field)) {
        // Do not return immidiatly - first try to find the same class lower on hierarchy
        result = type.cast(field);
      }

      if (recursive && field instanceof AbstractStruct) {
        final T result2 = getAttribute((AbstractStruct)field, offset, type, recursive);
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

  private static StructEntry getAttribute(AbstractStruct parent, String name, boolean recursive)
  {
    if (name != null && !name.isEmpty()) {
      for (StructEntry field : parent.getList()) {
        StructEntry result = null;
        if (field.getName().equals(name)) {
          // Do not return immidiatly - first try to find the same class lower on hierarchy
          result = field;
        }
        if (recursive && field instanceof AbstractStruct) {
          field = getAttribute((AbstractStruct)field, name, recursive);
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

  public int getEndOffset()
  {
    return endoffset;
  }

  public int getExtraOffset()
  {
    return extraoffset;
  }

  public List<StructEntry> getList()
  {
    return list;
  }

  /** Returns the number of fields in the current structure. */
  public int getFieldCount()
  {
    return list.size();
  }

  /**
   * Returns the StructEntry object at the specified index.
   * @param index The index of the desired StructEntry object.
   * @return The StructEntry object, or null if not available.
   */
  public StructEntry getField(int index)
  {
    try {
      return list.get(index);
    } catch (IndexOutOfBoundsException e) {
    }
    return null;
  }

  public List<StructEntry> getFlatList()
  {
    List<StructEntry> flatList = new ArrayList<StructEntry>(2 * getFieldCount());
    addFlatList(flatList);
    Collections.sort(flatList);
    return flatList;
  }

  public int getIndexOf(StructEntry structEntry)
  {
    return list.indexOf(structEntry);
  }

  public ResourceEntry getResourceEntry()
  {
    return entry;
  }

  public AbstractStruct getSuperStruct()
  {
    return superStruct;
  }

  public AbstractStruct getSuperStruct(StructEntry structEntry)
  {
    for (int i = 0; i < list.size(); i++) {
      Object o = list.get(i);
      if (o == structEntry)
        return this;
      if (o instanceof AbstractStruct) {
        AbstractStruct result = ((AbstractStruct)o).getSuperStruct(structEntry);
        if (result != null)
          return result;
      }
    }
    return null;
  }

  /**
   * Returns whether any parent of the current AbstractStruct object is an instance of
   * the specified class type.
   */
  public boolean isChildOf(Class<? extends AbstractStruct> struct)
  {
    if (struct != null) {
      AbstractStruct parent = getSuperStruct();
      while (parent != null) {
        if (struct.isInstance(parent)) {
          return true;
        }
        parent = parent.getSuperStruct();
      }
    }
    return false;
  }

  // end - implements Viewable

  public StructViewer getViewer()
  {
    return viewer;
  }

  public void realignStructOffsets()
  {
    int offset = startoffset;
    for (int i = 0; i < list.size(); i++) {
      StructEntry structEntry = list.get(i);
      structEntry.setOffset(offset);
      offset += structEntry.getSize();
      if (structEntry instanceof AbstractStruct)
        ((AbstractStruct)structEntry).realignStructOffsets();
    }
  }

  public List<AddRemovable> removeAllRemoveables()
  {
    List<AddRemovable> removed = new ArrayList<AddRemovable>();
    for (int i = 0; i < list.size(); i++) {
      StructEntry o = list.get(i);
      if (o instanceof AddRemovable) {
        removeDatatype((AddRemovable)o, false);
        removed.add((AddRemovable)o);
        i--;
      }
    }
    return removed;
  }

  public void removeDatatype(AddRemovable removedEntry, boolean removeRecurse)
  {
    if (removeRecurse && removedEntry instanceof HasAddRemovable) { // Recusivly removeTableLine substructures first
      AbstractStruct removedStruct = (AbstractStruct)removedEntry;
      for (int i = 0; i < removedStruct.list.size(); i++) {
        Object o = removedStruct.list.get(i);
        if (o instanceof AddRemovable) {
          removedStruct.removeDatatype((AddRemovable)o, removeRecurse);
          i--;
        }
      }
    }
    int index = list.indexOf(removedEntry);
    fireTableRowsWillBeDeleted(index, index);
    list.remove(index);
    // decrease count
    if (countmap != null && countmap.containsKey(removedEntry.getClass()))
      countmap.get(removedEntry.getClass()).incValue(-1);
    // decrease offsets
    AbstractStruct topStruct = this;
    while (topStruct.superStruct != null) {
      if (topStruct instanceof Resource) {
        topStruct.endoffset -= removedEntry.getSize();
        adjustSectionOffsets(topStruct, removedEntry, -removedEntry.getSize());
      }
      topStruct = topStruct.superStruct;
    }
    if (topStruct instanceof Resource)
      topStruct.endoffset -= removedEntry.getSize();
    adjustEntryOffsets(topStruct, this, removedEntry, -removedEntry.getSize());
    adjustSectionOffsets(topStruct, removedEntry, -removedEntry.getSize());
    datatypeRemoved(removedEntry);
    if (superStruct != null)
      superStruct.datatypeRemovedInChild(this, removedEntry);
    fireTableRowsDeleted(index, index);
    setStructChanged(true);
  }

  /**
   * Removes the specified entry from the current structure.
   * @param entry The entry to remove.
   * @return true if the current structure contained the given entry, false otherwise.
   */
  public boolean removeField(StructEntry entry)
  {
    if (entry != null) {
      if (list.remove(entry)) {
        entry.setParent(null);
        return true;
      }
    }
    return false;
  }

  /**
   * Removes the entry at the specified index.
   * @param index The index of the entry to remove.
   * @return The removed entry if found, null otherwise.
   */
  public StructEntry removeField(int index)
  {
    if (index >= 0 && index < list.size()) {
      StructEntry e = list.remove(index);
      if (e != null) {
        e.setParent(null);
      }
      return e;
    }
    return null;
  }

  public ByteBuffer removeFromList(StructEntry startFromEntry, int numBytes) throws IOException
  {
    int startindex = list.indexOf(startFromEntry) + 1;
    int endindex = startindex;
    int len = 0;
    // getting total size
    int maxLen = 0;
    for (int i = startindex, cnt = list.size(); i < cnt && maxLen < numBytes; i++) {
      maxLen += list.get(i).getSize();
    }
    // filling buffer
    ByteBuffer bb = StreamUtils.getByteBuffer(maxLen);
    try (ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb)) {
      while (len < maxLen) {
        StructEntry e = list.get(endindex++);
        len += e.getSize();
        e.write(bbos);
      }
    }
    // discard entries
    for (int i = endindex - 1; i >= startindex; i--) {
      list.remove(i);
    }
    bb.position(0);
    return bb;
  }

  /** Replaces an old StructEntry instance by the specified instance if offset and size are equal. */
  public boolean replaceEntry(StructEntry newEntry)
  {
    if (newEntry != null) {
      List<StructEntry> list = getList();
      for (int i = 0, size = list.size(); i < size; i++) {
        StructEntry oldEntry = list.get(i);
        if (oldEntry.getOffset() == newEntry.getOffset() &&
            oldEntry.getSize() == newEntry.getSize()) {
          list.set(i, newEntry);
          return true;
        }
      }
    }
    return false;
  }

  public void setListEntry(int index, StructEntry structEntry)
  {
    list.set(index, structEntry);
    fireTableRowsUpdated(index, index);
  }

  public boolean hasStructChanged()
  {
    return structChanged;
  }

  public void setStructChanged(boolean changed)
  {
    structChanged = changed;
    if (superStruct != null)
      superStruct.setStructChanged(changed);
  }

  public String toMultiLineString()
  {
    StringBuilder sb = new StringBuilder(30 * list.size());
    for (int i = 0; i < list.size(); i++) {
      StructEntry datatype = list.get(i);
      sb.append(datatype.getName()).append(": ").append(datatype.toString()).append('\n');
    }
    return sb.toString();
  }

  public void setExtraComponents(Collection<Component> list)
  {
    // List of components to be added to the bottom panel of the StructView component
    viewerComponents = list;
  }

  /** Returns the SectionOffset entry linked to the specified StructEntry object if available. */
  public SectionOffset getSectionOffset(Class<? extends StructEntry> cls)
  {
    return offsetmap.get(cls);
  }

  /** Returns the SectionCount entry linked to the specified StructEntry object if available. */
  public SectionCount getSectionCount(Class<? extends StructEntry> cls)
  {
    return countmap.get(cls);
  }

  private void addFlatList(List<StructEntry> flatList)
  {
    for (int i = 0; i < list.size(); i++) {
      StructEntry o = list.get(i);
      if (o instanceof AbstractStruct)
        ((AbstractStruct)o).addFlatList(flatList);
      else if (o instanceof AbstractCode)
        ((AbstractCode)o).addFlatList(flatList);
      else
        flatList.add(o);
    }
  }

  public boolean hasViewTab()
  {
    return (viewer != null && viewer.hasViewTab());
  }

  public boolean isViewTabSelected()
  {
    return (viewer != null && viewer.isViewTabSelected());
  }

  public void selectViewTab()
  {
    if (viewer != null) {
      viewer.selectViewTab();
    }
  }

  public boolean isEditTabSelected()
  {
    return (viewer != null && viewer.isEditTabSelected());
  }

  public void selectEditTab()
  {
    if (viewer != null) {
      viewer.selectEditTab();
    }
  }

  public boolean hasRawTab()
  {
    return (viewer != null && viewer.hasRawTab());
  }

  public boolean isRawTabSelected()
  {
    return (viewer != null && viewer.isRawTabSelected());
  }

  public void selectRawTab()
  {
    if (viewer != null) {
      viewer.selectRawTab();
    }
  }

  // To be overriden by subclasses
  protected void viewerInitialized(StructViewer viewer)
  {
  }

  // To be overriden by subclasses
  protected void datatypeAdded(AddRemovable datatype)
  {
  }

  // To be overriden by subclasses
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype)
  {
    if (superStruct != null)
      superStruct.datatypeAddedInChild(child, datatype);
  }

  // To be overriden by subclasses
  protected void datatypeRemoved(AddRemovable datatype)
  {
  }

  // To be overriden by subclasses
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype)
  {
    if (superStruct != null)
      superStruct.datatypeRemovedInChild(child, datatype);
  }

  private void fixHoles(ByteBuffer buffer)
  {
    int offset = startoffset;
    List<StructEntry> flatList = getFlatList();
    for (int i = 0; i < flatList.size(); i++) {
      StructEntry se = flatList.get(i);
      int delta = se.getOffset() - offset;
      if (se.getSize() > 0 && delta > 0) {
        Unknown hole = new Unknown(buffer, offset, delta, COMMON_UNUSED_BYTES);
        list.add(hole);
        flatList.add(i, hole);
        System.out.println("Hole: " + name + " off: " + Integer.toHexString(offset) + "h len: " + delta);
        i++;
      }
      // Using max() as shared data regions may confuse the hole detection algorithm
      offset = Math.max(offset, se.getOffset() + se.getSize());
    }
    if (endoffset < buffer.limit()) { // Does this break anything?
      list.add(new Unknown(buffer, endoffset, buffer.limit() - endoffset, COMMON_UNUSED_BYTES));
      System.out.println("Hole: " + name + " off: " + Integer.toHexString(endoffset) + "h len: " +
                         (buffer.limit() - endoffset));
      endoffset = buffer.limit();
    }
  }

  // To be overriden by subclasses
  protected int getAddedPosition()
  {
    return list.size(); // Default: Add at end
  }

  private void initAddStructMaps()
  {
    countmap = new HashMap<Class<? extends StructEntry>, SectionCount>();
    offsetmap = new HashMap<Class<? extends StructEntry>, SectionOffset>();
    for (int i = 0; i < list.size(); i++) {
      Object o = list.get(i);
      if (o instanceof SectionOffset) {
        SectionOffset so = (SectionOffset)o;
        if (so.getSection() != null) {
          offsetmap.put(so.getSection(), so);
        }
      }
      else if (o instanceof SectionCount)
        countmap.put(((SectionCount)o).getSection(), (SectionCount)o);
    }
  }

  // To be overriden by subclasses
  protected void setAddRemovableOffset(AddRemovable datatype)
  {
  }

  // end - implements StructEntry

  protected void setExtraOffset(int offset)
  {
    extraoffset = offset;
  }

  protected void setStartOffset(int offset)
  {
    startoffset = offset;
  }

  // end - implements Writeable

  protected void writeFlatList(OutputStream os) throws IOException
  {
    List<StructEntry> flatList = getFlatList();
    for (int i = 0; i < flatList.size(); i++)
      flatList.get(i).write(os);
  }

  /** Assign a new list of fields. Clears current list if argument is null. */
  protected void setList(List<StructEntry> newList)
  {
    if (newList != null) {
      list = newList;
    } else {
      list.clear();
    }
  }

  protected void setSuperStruct(AbstractStruct struct)
  {
    if (superStruct != null) {
      removePropertyChangeListener(struct);
    }
    this.superStruct = struct;
    if (struct != null) {
      addPropertyChangeListener(struct);
    }
  }

  /**
   * Notifies all listeners that rows in the range {@code [firstRow, lastRow]},
   * inclusive, will be deleted. this is the last chance to get values of the
   * deleted rows.
   *
   * @param firstRow  the first row
   * @param lastRow   the last row
   *
   * @see TableModelEvent
   * @see EventListenerList
   */
  public void fireTableRowsWillBeDeleted(int firstRow, int lastRow) {
    fireTableChanged(new TableModelEvent(this, firstRow, lastRow,
                         TableModelEvent.ALL_COLUMNS, WILL_BE_DELETE));
  }

  /**
   * Add a PropertyChangeListener to the listener list. The listener is registered
   * for all properties. The same listener object may be added more than once, and
   * will be called as many times as it is added.
   * <p>
   * If {@code listener} is null, no exception is thrown and no action
   * is taken.
   *
   * @param listener  The PropertyChangeListener to be added
   */
  public void addPropertyChangeListener(PropertyChangeListener listener)
  {
    changeSupport.addPropertyChangeListener(listener);
  }

  /**
   * Remove a PropertyChangeListener from the listener list. This removes a
   * PropertyChangeListener that was registered for all properties.
   * If {@code listener} was added more than once to the same event source, it
   * will be notified one less time after being removed.
   * <p>
   * If {@code listener} is null, or was never added, no exception is thrown and
   * no action is taken.
   *
   * @param listener  The PropertyChangeListener to be removed
   */
  public void removePropertyChangeListener(PropertyChangeListener listener)
  {
    changeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    changeSupport.firePropertyChange(evt);
  }
}
