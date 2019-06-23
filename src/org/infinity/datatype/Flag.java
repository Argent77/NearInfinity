// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.infinity.gui.StructViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.StructEntry;
import org.infinity.util.Misc;

public class Flag extends Datatype implements Editable, IsNumeric, ActionListener
{
  public static final String DESC_NONE = "No flags set";

  /** The description of sense when any of flags is not set. */
  private String nodesc;
  /** Labels of each flag. */
  private String[] table;
  /** Tooltips of each flag. */
  private String[] toolTable;
  private ActionListener container;
  private JButton bAll, bNone;
  private JCheckBox[] checkBoxes;
  private long value;

  Flag(StructEntry parent, ByteBuffer buffer, int offset, int length, String name)
  {
    super(parent, offset, length, name);
    read(buffer, offset);
  }

  /**
   * @param stable Contains default value when no flag is selected and a list of flag descriptions.
   *               Optionally you can combine flag descriptions with tool tips, using the default
   *               separator char ';'.
   */
  public Flag(ByteBuffer buffer, int offset, int length, String name, String[] stable)
  {
    this(null, buffer, offset, length, name, stable);
  }

  /**
   * @param stable Contains default value when no flag is selected and a list of flag descriptions.
   *               Optionally you can combine flag descriptions with tool tips, using the default
   *               separator char ';'.
   */
  public Flag(StructEntry parent, ByteBuffer buffer, int offset, int length, String name, String[] stable)
  {
    this(parent, buffer, offset, length, name, stable, ';');
  }

  /**
   * @param stable Contains default value when no flag is selected and a list of flag descriptions.
   *               Optionally you can combine flag descriptions with tool tips, using the specified
   *               separator char.
   * @param separator Character that can be used to split flag description and tool tip.
   */
  public Flag(StructEntry parent, ByteBuffer buffer, int offset, int length, String name, String[] stable,
              char separator)
  {
    this(parent, buffer, offset, length, name);
    setEmptyDesc((stable == null || stable.length == 0) ? null : stable[0]);
    setFlagDescriptions(length, stable, 1, separator);
  }

  //<editor-fold defaultstate="collapsed" desc="ActionListener">
  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == bAll) {
      for (final JCheckBox checkBox : checkBoxes)
        checkBox.setSelected(true);
    }
    else if (event.getSource() == bNone) {
      for (final JCheckBox checkBox : checkBoxes)
        checkBox.setSelected(false);
    }
    container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Editable">
  @Override
  public JComponent edit(ActionListener container)
  {
    this.container = container;
    checkBoxes = new JCheckBox[table.length];
    for (int i = 0; i < table.length; i++) {
      if (table[i] == null || table[i].isEmpty()) {
        checkBoxes[i] = new JCheckBox("Unknown (" + i + ')');
      } else {
        checkBoxes[i] = new JCheckBox(table[i] + " (" + i + ')');
      }
      if (toolTable[i] != null && !toolTable[i].isEmpty()) {
        checkBoxes[i].setToolTipText(toolTable[i]);
      }
      checkBoxes[i].addActionListener(container);
      checkBoxes[i].setActionCommand(StructViewer.UPDATE_VALUE);
    }
    bAll = new JButton("Select all");
    bNone = new JButton("Select none");
    bAll.setMargin(new Insets(0, bAll.getMargin().left, 0, bAll.getMargin().right));
    bNone.setMargin(bAll.getMargin());
    bAll.addActionListener(this);
    bNone.addActionListener(this);

    JPanel bPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    bPanel.add(bAll);
    bPanel.add(bNone);
    bPanel.add(new JLabel("None = " + nodesc));

    JPanel boxPanel = new JPanel(new GridLayout(0, 4));
    int rows = checkBoxes.length >> 2;
    if (rows << 2 != checkBoxes.length) {
      for (int i = 0; i < checkBoxes.length; i++) {
        boxPanel.add(checkBoxes[i]);
        checkBoxes[i].setSelected(isFlagSet(i));
      }
    }
    else {
      for (int i = 0; i < rows; i++)
        for (int j = 0; j < 4; j++) {
          int index = i + j * rows;
          boxPanel.add(checkBoxes[index]);
          checkBoxes[index].setSelected(isFlagSet(index));
        }
    }

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(boxPanel, BorderLayout.CENTER);
    panel.add(bPanel, BorderLayout.SOUTH);

    panel.setMinimumSize(Misc.getScaledDimension(DIM_BROAD));

    return panel;
  }

  @Override
  public void select()
  {
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    // updating value
    value = 0L;
    for (int i = 0; i < checkBoxes.length; i++)
      if (checkBoxes[i].isSelected()) {
        setFlag(i);
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
        value = buffer.get() & 0xff;
        break;
      case 2:
        value = buffer.getShort() & 0xffff;
        break;
      case 4:
        value = buffer.getInt() & 0xffffffff;
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
    final StringBuilder sb = new StringBuilder("( ");
    if (value == 0)
      sb.append(nodesc).append(' ');
    else {
      for (int i = 0; i < 8 * getSize(); i++)
        if (isFlagSet(i)) {
          final String label = getString(i);
          sb.append(label == null ? "Unknown" : label)
            .append('(').append(i).append(") ");
        }
    }
    sb.append(')');
    return sb.toString();
  }

  /**
   * Returns label of flag {@code i} or {@code null}, if such flag does not exist.
   *
   * @param i Number of flag (counting from 0)
   * @return Label of flag or {@code null}, if no such flag.
   */
  public String getString(int i)
  {
    return i < 0 || i > table.length ? null : table[i];
  }

  public boolean isFlagSet(int i)
  {
    long bitnr = 1L << i;
    return (value & bitnr) == bitnr;
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

  public void setValue(long newValue)
  {
    value = newValue;
  }

  private void setFlag(int i)
  {
    long mask = 1L << i;
    value |= mask;
  }

  /**
   * Sets description for empty flags.
   *
   * @param desc If {@code null}, then {@link #DESC_NONE} will be used as description
   */
  protected final void setEmptyDesc(String desc)
  {
    nodesc = (desc != null) ? desc : DESC_NONE;
  }

  /**
   * Sets labels and optional tooltips for each flag.
   *
   * @param size Size of flag field in bytes. Count of flags equals {@code size * 8}
   * @param stable Table with labels and optional tooltips of each flag. If table
   *        size if less then count of flags, then remaining flags will be without
   *        labels and tooltips
   * @param startOfs Offset to {@code stable} from which data begins
   * @param separator Separator, used to separate flag label and tooltip
   */
  protected final void setFlagDescriptions(int size, String[] stable, int startOfs, char separator)
  {
    table = new String[8*size];
    toolTable = new String[8*size];
    if (stable != null) {
      for (int i = startOfs, j = 0; i < stable.length; ++i, ++j) {
        final String desc = stable[i];
        if (desc == null) continue;

        final int sep = desc.indexOf(separator);
        if (sep < 0) {
          table[j] = desc;
          toolTable[j] = null;
        } else {
          table[j] = desc.substring(0, sep);
          toolTable[j] = desc.substring(sep + 1);
        }
      }
    }
  }
}
