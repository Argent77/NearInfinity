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
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.gui.StructViewer;
import org.infinity.gui.TextListPanel;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.util.LongIntegerHashMap;
import org.infinity.util.Misc;
import org.infinity.util.ObjectString;

public class HashBitmap extends Datatype implements Editable, IsNumeric
{
  private final LongIntegerHashMap<? extends Object> idsmap;
  private final List<JButton> buttonList;
  private final JButton bUpdate;
  private final boolean sortByName;
  private TextListPanel<Object> list;
  private long value;

  public HashBitmap(ByteBuffer buffer, int offset, int length, String name,
                    LongIntegerHashMap<? extends Object> idsmap)
  {
    this(buffer, offset, length, name, idsmap, true);
  }

  public HashBitmap(ByteBuffer buffer, int offset, int length, String name,
                    LongIntegerHashMap<? extends Object> idsmap, boolean sortByName)
  {
    super(offset, length, name);
    this.idsmap = normalizeHashMap(idsmap);
    this.sortByName = sortByName;
    this.bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
    this.buttonList = new ArrayList<>();
    this.buttonList.add(bUpdate);

    read(buffer, offset);
  }

  //<editor-fold defaultstate="collapsed" desc="Editable">
  @Override
  public JComponent edit(final ActionListener container)
  {
    final List<Object> items = new ArrayList<>(idsmap.size());
    for (Object o : idsmap.values()) {
      if (o != null) {//TODO: It seems that map never contains nulls and this check can be removed
        items.add(o);
      }
    }
    list = new TextListPanel<>(items, sortByName);
    list.addMouseListener(new MouseAdapter()
    {
      @Override
      public void mouseClicked(MouseEvent event)
      {
        if (event.getClickCount() == 2)
          container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
      }
    });
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e)
      {
        if (!e.getValueIsAdjusting()) {
          listItemChanged();
        }
      }
    });
    Object selected = idsmap.get(value);
    if (selected != null) {
      list.setSelectedValue(selected, true);
    }

    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel panel = new JPanel(gbl);

    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridheight = buttonList.size() + 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbl.setConstraints(list, gbc);
    panel.add(list);

    gbc.weightx = 0.0;
    gbc.gridheight = 1;
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.insets.left = 6;
    gbc.insets.top = 4;
    gbc.insets.bottom = 4;
    // dummy component to center list of buttons vertically
    JPanel p = new JPanel();
    gbl.setConstraints(p, gbc);
    panel.add(p);
    ++gbc.gridy;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weighty = 0.0;
    for (final JButton btn: buttonList) {
      gbl.setConstraints(btn, gbc);
      panel.add(btn);
      ++gbc.gridy;
    }
    // dummy component to center list of buttons vertically
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    p = new JPanel();
    gbl.setConstraints(p, gbc);
    panel.add(p);

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
    Long number = getValueOfItem(list.getSelectedValue());
    if (number != null) {
      value = number.longValue();
    } else {
      return false;
    }

    // notifying listeners
    fireValueUpdated(new UpdateEvent(this, struct));

    return true;
  }

  //<editor-fold defaultstate="collapsed" desc="Writeable">
  @Override
  public void write(OutputStream os) throws IOException
  {
    writeLong(os, value);
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Readable">
  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    switch (getSize()) {
      case 1:
        value = buffer.get() & 0xffL;
        break;
      case 2:
        value = buffer.getShort() & 0xffffL;
        break;
      case 4:
        value = buffer.getInt() & 0xffffffffL;
        break;
      default:
        throw new IllegalArgumentException();
    }

    return offset + getSize();
  }
  //</editor-fold>
  //</editor-fold>

  @Override
  public String toString()
  {
    final Object o = getValueOf(value);
    return o == null ? "Unknown - " + value : o.toString();
  }

  //<editor-fold defaultstate="collapsed" desc="IsNumeric">
  @Override
  public long getLongValue()
  {
    return value;
  }

  @Override
  public int getValue()
  {
    return (int)value;
  }
  //</editor-fold>

  protected void setValue(long newValue)
  {
    this.value = newValue;
  }

  /** Called whenever the user selects a new list item. */
  protected void listItemChanged()
  {
  }

  /**
   * Can be used to register one or more custom buttons to the bitmap control.
   * Only effective if called before the UI control is created.
   */
  protected void addButtons(JButton... buttons)
  {
    if (list == null) {
      for (final JButton button: buttons) {
        if (button != null) {
          buttonList.add(button);
        }
      }
    }
  }

  /** Returns the TextListPanel control used by this datatype. */
  public TextListPanel<Object> getListPanel()
  {
    return list;
  }

  /** Returns the textual representation of the specified IDS value. */
  public Object getValueOf(long key)
  {
    return idsmap.get(Long.valueOf(key));
  }

  protected Long getCurrentValue()
  {
    return getValueOfItem(list.getSelectedValue());
  }

  /** Attempts to extract the IDS value from the specified list item. */
  private Long getValueOfItem(Object item)
  {
    Long retVal = null;
    if (item != null) {
      if (item instanceof ObjectString && ((ObjectString)item).getObject() instanceof Number) {
        retVal = ((Number)((ObjectString)item).getObject()).longValue();
      } else {
        int i = item.toString().lastIndexOf(" - ");//FIXME: Smell code
        try {
          retVal = Long.parseLong(item.toString().substring(i + 3));
        } catch (NumberFormatException e) {
          retVal = null;
        }
      }
    }
    return retVal;
  }

  protected LongIntegerHashMap<? extends Object> getHashBitmap()
  {
    return idsmap;
  }

  private static LongIntegerHashMap<? extends Object> normalizeHashMap(LongIntegerHashMap<? extends Object> map)
  {
    //TODO: The smelling code. It seems that there is a check on the fact that the map contains String's
    if (map != null && !map.isEmpty() && map.firstEntry().getValue() instanceof String) {
      final LongIntegerHashMap<ObjectString> retVal = new LongIntegerHashMap<>();
      for (Map.Entry<Long, ? extends Object> e : map.entrySet()) {
        retVal.put(e.getKey(), new ObjectString(e.getValue().toString(), e.getKey(),
                                                ObjectString.FMT_OBJECT_HYPHEN));
      }
      return retVal;
    } else if (map == null) {
      return new LongIntegerHashMap<ObjectString>();
    } else {
      return map;
    }
  }
}
