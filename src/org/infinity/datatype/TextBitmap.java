// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import org.infinity.gui.StructViewer;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.StructEntry;
import org.infinity.util.Misc;
import org.infinity.util.io.StreamUtils;

/**
 * Field that represents a string enumeration of some values.
 *
 * <h2>Bean property</h2>
 * When this field is child of {@link AbstractStruct}, then changes of its internal
 * value reported as {@link PropertyChangeEvent}s of the {@link #getParent() parent}
 * struct.
 * <ul>
 * <li>Property name: {@link #getName() name} of this field</li>
 * <li>Property type: {@link String}</li>
 * <li>Value meaning: value from list of enumarated values</li>
 * </ul>
 */
public final class TextBitmap extends Datatype implements Editable, IsTextual
{
  private final String[] ids;
  private final String[] names;
  private JTable table;
  private String text;

  public TextBitmap(ByteBuffer buffer, int offset, int length, String name, Map<String, String> items)
  {
    super(null, offset, length, name);
    read(buffer, offset);
    if (items != null) {
      this.ids = new String[items.size()];
      this.names = new String[this.ids.length];
      int idx = 0;
      for (final String key: items.keySet()) {
        this.ids[idx] = key;
        this.names[idx] = items.get(key);
        idx++;
      }
    } else {
      this.ids = new String[0];
      this.names = this.ids;
    }
  }

  public TextBitmap(ByteBuffer buffer, int offset, int length, String name, String[] ids, String[] names)
  {
    this(null, buffer, offset, length, name, ids, names);
  }

  public TextBitmap(StructEntry parent, ByteBuffer buffer, int offset, int length, String name,
                    String[] ids, String[] names)
  {
    super(parent, offset, length, name);
    read(buffer, offset);
    this.ids = ids;
    this.names = names;
  }

// --------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(ActionListener container)
  {
    if (table == null) {
      table = new JTable(new BitmapTableModel());
      table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      table.setDragEnabled(false);
      table.setTableHeader(null);
    }
    for (int i = 0; i < ids.length; i++) {
      if (ids[i].equalsIgnoreCase(text)) {
        table.getSelectionModel().setSelectionInterval(i, i);
      }
    }

    JScrollPane scroll = new JScrollPane(table);
    JButton bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel panel = new JPanel(gbl);

    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbl.setConstraints(scroll, gbc);
    panel.add(scroll);

    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = 6;
    gbl.setConstraints(bUpdate, gbc);
    panel.add(bUpdate);

    panel.setMinimumSize(Misc.getScaledDimension(DIM_MEDIUM));
    return panel;
  }

  @Override
  public void select()
  {
    table.scrollRectToVisible(table.getCellRect(table.getSelectedRow(), 0, false));
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    int index = table.getSelectedRow();
    if (index == -1) {
      return false;
    }
    setValue(ids[index]);

    // notifying listeners
    fireValueUpdated(new UpdateEvent(this, struct));

    return true;
  }

// --------------------- End Interface Editable ---------------------

// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    StreamUtils.writeString(os, text, getSize());
  }

// --------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    text = StreamUtils.readString(buffer, getSize(), Misc.CHARSET_ASCII);

    // filling missing characters with spaces
    if (text.length() < getSize()) {
      StringBuilder sb = new StringBuilder();
      while (sb.length() < getSize() - text.length()) {
        sb.append(' ');
      }
      text = text + sb.toString();
    }

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

  @Override
  public String toString()
  {
    for (int i = 0; i < ids.length; i++)
      if (ids[i].equalsIgnoreCase(text)) {
        return text + " - " + names[i];
      }
    return text;
  }

//--------------------- Begin Interface IsTextual ---------------------

  /** Returns the unprocessed textual symbol of fixed number of characters. */
  @Override
  public String getText()
  {
    return text;
  }

//--------------------- End Interface IsTextual ---------------------

  /** Returns the textual description of the symbol. */
  public String getDescription()
  {
    for (int i = 0; i < ids.length; i++) {
      if (text.equals(ids[i])) {
        if (i < names.length) {
          return names[i];
        } else {
          break;
        }
      }
    }
    return "";
  }

  private void setValue(String newValue)
  {
    final String oldValue = text;
    text = newValue;
    if (!Objects.equals(oldValue, newValue)) {
      firePropertyChange(oldValue, newValue);
    }
  }

// -------------------------- INNER CLASSES --------------------------

  private final class BitmapTableModel extends AbstractTableModel
  {
    private BitmapTableModel()
    {
    }

    @Override
    public int getRowCount()
    {
      return ids.length;
    }

    @Override
    public int getColumnCount()
    {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
      if (columnIndex == 0)
        return ids[rowIndex];
      return names[rowIndex];
    }
  }
}
