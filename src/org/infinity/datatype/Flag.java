// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewerUtil;
import org.infinity.resource.AbstractStruct;
import org.infinity.util.Misc;

/**
 * Field that represents several numerical values as flags.
 *
 * <h2>Bean property</h2> When this field is child of {@link AbstractStruct}, then changes of its internal value
 * reported as {@link PropertyChangeEvent}s of the {@link #getParent() parent} struct.
 * <ul>
 * <li>Property name: {@link #getName() name} of this field</li>
 * <li>Property type: {@code long}</li>
 * <li>Value meaning: the set flags of this field</li>
 * </ul>
 */
public class Flag extends Datatype implements Editable, IsNumeric, ActionListener {
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

  Flag(ByteBuffer buffer, int offset, int length, String name) {
    super(offset, length, name);
    read(buffer, offset);
  }

  /**
   * @param stable Contains default value when no flag is selected and a list of flag descriptions. Optionally you can
   *               combine flag descriptions with tool tips, using the separator char ';'.
   */
  public Flag(ByteBuffer buffer, int offset, int length, String name, String[] stable) {
    this(buffer, offset, length, name);
    setEmptyDesc((stable == null || stable.length == 0) ? null : stable[0]);
    setFlagDescriptions(length, stable, 1);
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == bAll) {
      for (final JCheckBox checkBox : checkBoxes) {
        checkBox.setSelected(true);
      }
    } else if (event.getSource() == bNone) {
      for (final JCheckBox checkBox : checkBoxes) {
        checkBox.setSelected(false);
      }
    }
    container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
  }

  @Override
  public JComponent edit(ActionListener container) {
    this.container = container;
    Color colBright = (Color) UIManager.get("Label.disabledForeground");
    if (colBright == null) {
      colBright = Color.GRAY;
    }
    checkBoxes = new JCheckBox[table.length];
    for (int i = 0; i < table.length; i++) {
      if (table[i] == null || table[i].isEmpty()) {
        checkBoxes[i] = new JCheckBox("Unknown (" + i + ')');
        checkBoxes[i].setForeground(colBright);
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

    // spreading flags over columns with 8 rows each
    JPanel boxPanel = new JPanel(new GridBagLayout());
    int cols = checkBoxes.length / 8;
    GridBagConstraints c = new GridBagConstraints();
    for (int col = 0; col < cols; col++) {
      JPanel colPanel = new JPanel(new GridBagLayout());
      for (int row = 0; row < 8; row++) {
        int idx = (col * 8) + row;
        c = ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
        colPanel.add(checkBoxes[idx], c);
        checkBoxes[idx].setSelected(isFlagSet(idx));
      }
      c = ViewerUtil.setGBC(c, col, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
          new Insets(0, 8, 0, 8), 0, 0);
      boxPanel.add(colPanel, c);
    }

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(boxPanel, BorderLayout.CENTER);
    panel.add(bPanel, BorderLayout.SOUTH);

    panel.setMinimumSize(Misc.getScaledDimension(DIM_BROAD));

    return panel;
  }

  @Override
  public void select() {
  }

  @Override
  public boolean updateValue(AbstractStruct struct) {
    long oldValue = getLongValue();

    // updating value
    setValue(calcValue());

    // notifying listeners
    if (getLongValue() != oldValue) {
      fireValueUpdated(new UpdateEvent(this, struct));
    }

    return true;
  }

  @Override
  public void write(OutputStream os) throws IOException {
    writeLong(os, value);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) {
    buffer.position(offset);
    switch (getSize()) {
      case 1:
        value = buffer.get() & 0xff;
        break;
      case 2:
        value = buffer.getShort() & 0xffff;
        break;
      case 3:
        value = buffer.getShort() & 0xffff;
        value |= (buffer.get() & 0xff) << 16;
        break;
      case 4:
        value = buffer.getInt() & 0xffffffffL;
        break;
      default:
        throw new IllegalArgumentException();
    }

    return offset + getSize();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("( ");
    if (value == 0) {
      sb.append(nodesc).append(' ');
    } else {
      for (int i = 0; i < 8 * getSize(); i++) {
        if (isFlagSet(i)) {
          final String label = getString(i);
          sb.append(label == null ? "Unknown" : label).append('(').append(i).append(") ");
        }
      }
    }
    sb.append(')');
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(value);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Flag other = (Flag) obj;
    return value == other.value;
  }

  /**
   * Returns label of flag {@code i} or {@code null}, if such flag does not exist.
   *
   * @param i Number of flag (counting from 0)
   * @return Label of flag or {@code null}, if no such flag.
   */
  public String getString(int i) {
    return (table != null) && (i >= 0) && (i < table.length) ? table[i] : null;
  }

  public boolean isFlagSet(int bit) {
    long bitnr = 1L << bit;
    return (value & bitnr) == bitnr;
  }

  @Override
  public long getLongValue() {
    return value;
  }

  @Override
  public int getValue() {
    return (int) value;
  }

  public void setValue(long newValue) {
    final long oldValue = value;
    value = newValue;
    if (oldValue != newValue) {
      firePropertyChange(oldValue, newValue);
    }
  }

  private long calcValue() {
    long val = 0L;
    for (int i = 0; i < checkBoxes.length; ++i) {
      if (checkBoxes[i].isSelected()) {
        val |= (1L << i);
      }
    }
    return val;
  }

  /**
   * Sets description for empty flags.
   *
   * @param desc If {@code null}, then {@link #DESC_NONE} will be used as description
   */
  public void setEmptyDesc(String desc) {
    nodesc = (desc != null) ? desc : DESC_NONE;
  }

  /**
   * Sets labels and optional tooltips for each flag. Label and tooltip separated by {@code ';'}
   *
   * @param size     Size of flag field in bytes. Count of flags equals {@code size * 8}
   * @param stable   Table with labels and optional tooltips of each flag. If table size if less then count of flags,
   *                 then remaining flags will be without labels and tooltips
   * @param startOfs Offset to {@code stable} from which data begins
   */
  public void setFlagDescriptions(int size, String[] stable, int startOfs) {
    table = new String[8 * size];
    toolTable = new String[8 * size];
    if (stable != null) {
      for (int i = startOfs, j = 0; i < stable.length; ++i, ++j) {
        final String desc = stable[i];
        if (desc == null) {
          continue;
        }

        final int sep = desc.indexOf(';');
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
