// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.Dimension;
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

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.InfinityScrollPane;
import org.infinity.gui.InfinityTextArea;
import org.infinity.gui.StringEditor;
import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewFrame;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.StringReferenceSearcher;
import org.infinity.util.Misc;
import org.infinity.util.StringTable;

/**
 * A struct field that represents reference to string in a talk table file (dialog.tlk
 * or dialogF.tlk).
 *
 * <h2>Bean property</h2>
 * When this field is child of {@link AbstractStruct}, then changes of its internal
 * value reported as {@link PropertyChangeEvent}s of the {@link #getParent() parent}
 * struct.
 * <ul>
 * <li>Property name: {@link #getName() name} of this field</li>
 * <li>Property type: {@code int}</li>
 * <li>Value meaning: number of the string in the TLK</li>
 * </ul>
 */
public final class StringRef extends Datatype implements Editable, IsNumeric, IsTextual, ActionListener, ChangeListener
{
  /**
   * Button that opens dialog with sound associated with this reference if that
   * sound exists. If no sound assotiated with this string entry, button is disabled.
   */
  private JButton bPlay;
  /** Button that opens editor of the talk table(s) of the game (dialog.tlk and dialogF.tlk). */
  private JButton bEdit;
  /**
   * Button that used to update reference in parent struct if editor of this string
   * reference opened in embedded mode. Hidden if editor opened not in embedded mode
   */
  private JButton bUpdate;
  /**
   * Button that opens dialog with settings for searching usage of this string
   * in another game files.
   */
  private JButton bSearch;
  /** Text area that contains content of string from main talk table (dialog.tlk). */
  private InfinityTextArea taRefText;
  /** Editor for numerical index in talk table for this string reference. */
  private JSpinner sRefNr;
  /** Index of this string in the talk table (TLK file). */
  private int value;

  public StringRef(String name, int value)
  {
    this(null, name, value);
  }

  /**
   * Constructs field description of {@code parent} struct, that stores it value.
   *
   * @param parent Structure that contains this field
   * @param name Name of field in parent struct that has {@code StringRef} type
   * @param value Index of the string in the talk table (TLK file)
   */
  public StringRef(StructEntry parent, String name, int value)
  {
    super(parent, 0, 4, name);
    this.value = value;
  }

  /**
   * Constructs field description and reads its value from {@code buffer} starting
   * with offset {@code offset}. Method reads 4 bytes from {@code buffer}.
   *
   * @param buffer Storage from which value of this field is readed
   * @param offset Offset of this field in the {@code buffer}
   * @param name Name of field
   */
  public StringRef(ByteBuffer buffer, int offset, String name)
  {
    this(null, buffer, offset, name);
  }

  /**
   * Constructs field description of {@code parent} struct and reads its value
   * from {@code buffer} starting with offset {@code offset}. Method reads 4 bytes
   * from {@code buffer}.
   *
   * @param parent Structure that contains this field
   * @param buffer Storage from which value of this field is readed
   * @param offset Offset of this field in the {@code buffer}
   * @param name Name of field in parent struct that has {@code StringRef} type
   */
  public StringRef(StructEntry parent, ByteBuffer buffer, int offset, String name)
  {
    super(parent, offset, 4, name);
    read(buffer, offset);
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    final int newvalue = getValueFromEditor();
    if (event.getSource() == bUpdate) {
      taRefText.setText(StringTable.getStringRef(newvalue));
      enablePlay(newvalue);
    }
    else if (event.getSource() == bEdit) {
      StringEditor.edit(value);
    }
    else if (event.getSource() == bPlay) {
      final ResourceEntry entry = ResourceFactory.getResourceEntry(StringTable.getSoundResource(newvalue) + ".WAV");
      new ViewFrame(bPlay.getTopLevelAncestor(), ResourceFactory.getResource(entry));
    }
    else if (event.getSource() == bSearch) {
      new StringReferenceSearcher(newvalue, bSearch.getTopLevelAncestor());
    }
  }

// --------------------- End Interface ActionListener ---------------------

// --------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent e)
  {
    final int newvalue = getValueFromEditor();
    taRefText.setText(StringTable.getStringRef(newvalue));
    enablePlay(newvalue);
  }

// --------------------- End Interface ChangeListener ---------------------

// --------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(ActionListener container)
  {
    if (sRefNr == null) {
      sRefNr = new JSpinner(new SpinnerNumberModel((long)value, -0x80000000L, 0xFFFFFFFFL, 1L));
      sRefNr.setEditor(new JSpinner.NumberEditor(sRefNr, "#")); // no special formatting
      sRefNr.addChangeListener(this);
      taRefText = new InfinityTextArea(1, 200, true);
      if (BrowserMenuBar.getInstance().getTlkSyntaxHighlightingEnabled()) {
        taRefText.applyExtendedSettings(InfinityTextArea.Language.TLK, null);
      }
      taRefText.setFont(Misc.getScaledFont(taRefText.getFont()));
      taRefText.setHighlightCurrentLine(false);
      taRefText.setEditable(false);
      taRefText.setLineWrap(true);
      taRefText.setWrapStyleWord(true);
      taRefText.setMargin(new Insets(3, 3, 3, 3));
      bPlay = new JButton("Sound", Icons.getIcon(Icons.ICON_VOLUME_16));
      bPlay.setToolTipText("Opens associated sound");
      bPlay.addActionListener(this);
      bEdit = new JButton("Edit", Icons.getIcon(Icons.ICON_EDIT_16));
      bEdit.setToolTipText("Opens string editor");
      bEdit.setMnemonic('e');
      bEdit.addActionListener(this);
      bSearch = new JButton("Find references...", Icons.getIcon(Icons.ICON_FIND_16));
      bSearch.addActionListener(this);
      bSearch.setMnemonic('f');
    }
    enablePlay(value);
    taRefText.setText(StringTable.getStringRef(value));
    taRefText.setCaretPosition(0);
    InfinityScrollPane scroll = new InfinityScrollPane(taRefText, true);
    scroll.setLineNumbersEnabled(false);
    sRefNr.setValue(value);
    JLabel label = new JLabel("StringRef: ");
    label.setLabelFor(sRefNr);
    label.setDisplayedMnemonic('s');
    bPlay.setMargin(new Insets(1, 3, 1, 3));
    bEdit.setMargin(bPlay.getMargin());
    bSearch.setMargin(bPlay.getMargin());
    sRefNr.setMinimumSize(new Dimension(sRefNr.getPreferredSize().width, bPlay.getPreferredSize().height));

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    if (container != null) {
      bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
      bUpdate.setMargin(bPlay.getMargin());
      bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);
      bUpdate.addActionListener(this);
      bUpdate.addActionListener(container);
      buttonPanel.add(bUpdate);
    }
    buttonPanel.add(bPlay);
    buttonPanel.add(bEdit);
    buttonPanel.add(bSearch);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel panel = new JPanel(gbl);

    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(0, 0, 3, 3);
    gbc.fill = GridBagConstraints.NONE;
    gbl.setConstraints(label, gbc);
    panel.add(label);

    gbc.anchor = GridBagConstraints.WEST;
    gbl.setConstraints(sRefNr, gbc);
    panel.add(sRefNr);

    gbc.insets.right = 0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(buttonPanel, gbc);
    panel.add(buttonPanel);

    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbl.setConstraints(scroll, gbc);
    panel.add(scroll);

    panel.setMinimumSize(Misc.getScaledDimension(DIM_BROAD));
    panel.setPreferredSize(Misc.getScaledDimension(DIM_BROAD));
    return panel;
  }

  @Override
  public void select()
  {
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    setValue(getValueFromEditor());

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
    value = buffer.getInt();

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

  @Override
  public String toString()
  {
    return toString(StringTable.Format.NONE);
  }

  public String toString(StringTable.Format fmt)
  {
    if (fmt == null) {
      fmt = StringTable.Format.NONE;
    }
    return StringTable.getStringRef(value, fmt);
  }

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

//--------------------- Begin Interface IsTextual ---------------------

  @Override
  public String getText()
  {
    return StringTable.getStringRef(value);
  }

//--------------------- End Interface IsTextual ---------------------

  public void setValue(int newValue)
  {
    final int oldValue = value;
    value = newValue;
    taRefText.setText(StringTable.getStringRef(newValue));
    sRefNr.setValue(newValue);
    enablePlay(newValue);

    if (oldValue != newValue) {
      firePropertyChange(oldValue, newValue);
    }
  }

  /**
   * Enables or disables button for view associated sound for specified StringRef value.
   *
   * @param value Value of string reference
   */
  private void enablePlay(int value)
  {
    final String resname = StringTable.getSoundResource(value);
    bPlay.setEnabled(!resname.isEmpty() && ResourceFactory.resourceExists(resname + ".WAV"));
  }
  /**
   * Extracts current value of string reference from editor. This value may not
   * be saved yet in string field of {@link #getParent() owner structure}, it is
   * value of current string that editor is display.
   */
  private int getValueFromEditor()
  {
    return ((Number)sRefNr.getValue()).intValue();
  }
}
