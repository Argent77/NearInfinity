// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.infinity.gui.StructViewer;
import org.infinity.gui.TextListPanel;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.util.Misc;

public class Bitmap extends Datatype implements Editable, IsNumeric
{
  private final String[] table;

  private TextListPanel<String> list;
  private int value;

  public Bitmap(ByteBuffer buffer, int offset, int length, String name, String[] table)
  {
    super(offset, length, name);
    this.table = table;
    read(buffer, offset);
  }

// --------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(final ActionListener container)
  {
    final List<String> values = new ArrayList<>(Math.max(table.length, value + 1));
    for (int i = 0; i < Math.max(table.length, value + 1); i++) {
      values.add(toString(i));
    }
    list = new TextListPanel<>(values, false);
    list.addMouseListener(new MouseAdapter()
    {
      @Override
      public void mouseClicked(MouseEvent event)
      {
        if (event.getClickCount() == 2) {
          container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
        }
      }
    });
    if (value >= 0 && value < list.getModel().getSize()) {
      int index = 0;
      while (!list.getModel().getElementAt(index).equals(toString(value))) {
        index++;
      }
      list.setSelectedIndex(index);
    }

    JButton bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel panel = new JPanel(gbl);

    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbl.setConstraints(list, gbc);
    panel.add(list);

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
    list.ensureIndexIsVisible(list.getSelectedIndex());
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    // updating value
    final String svalue = list.getSelectedValue();
    value = 0;
    //FIXME: Ineffective code
    while (!svalue.equals(toString(value))) {
      value++;
    }

    // notifying listeners
    fireValueUpdated(new UpdateEvent(this, struct));

    return true;
  }

// --------------------- End Interface Editable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    writeInt(os, value);
  }

// --------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    switch (getSize()) {
      case 1:
        value = buffer.get();
        break;
      case 2:
        value = buffer.getShort();
        break;
      case 4:
        value = buffer.getInt();
        break;
      default:
        throw new IllegalArgumentException();
    }

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

//--------------------- Begin Interface IsNumeric ---------------------

  @Override
  public long getLongValue()
  {
    return (long)value & 0xffffffffL;
  }

  @Override
  public int getValue()
  {
    return value;
  }

//--------------------- End Interface IsNumeric ---------------------

  @Override
  public String toString()
  {
    return toString(value);
  }

  /** Returns the unformatted description of the specified value. */
  protected String getString(int nr)
  {
    if (nr >= 0 && nr < table.length) {
      return table[nr];
    }
    return null;
  }

  // Returns a formatted description of the specified value.
  private String toString(int nr)
  {
    if (nr >= table.length) {
      return "Unknown (" + nr + ')';
    } else if (nr < 0) {
      return "Error (" + nr + ')';
    } else if (table[nr] == null || table[nr].isEmpty()) {
      return "Unknown (" + nr + ')';
    } else {
      return table[nr] + " (" + nr + ')';
    }
  }
}
