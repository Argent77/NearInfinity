// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.gui.StructViewer;
import org.infinity.gui.TextListPanel;
import org.infinity.gui.ViewFrame;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Misc;

/**
 * Datatype for selecting resource entries, constructed from a predefined list of key/value pairs.
 */
public class ResourceBitmap extends Datatype
    implements Editable, IsNumeric, IsReference, ActionListener, ListSelectionListener
{
  /** Print resource reference together with search name in parentheses and value after hyphen. */
  public static final String FMT_REF_NAME_VALUE   = "%1$s (%2$s) - %3$s";
  /** Print search name together with resource reference in parentheses and value after hyphen. */
  public static final String FMT_NAME_REF_VALUE   = "%2$s (%1$s) - %3$s";
  /** Print resource reference together with its search name after hyphen. */
  public static final String FMT_REF_HYPHEN_NAME  = "%1$s - %2$s";
  /** Print search name together with its resource reference after hyphen. */
  public static final String FMT_NAME_HYPHEN_REF  = "%2$s - %1$s";
  /** Print resource reference together with value after hyphen. */
  public static final String FMT_REF_HYPHEN_VALUE = "%1$s - %3$s";
  /** Print resource reference together with its search name. */
  public static final String FMT_REF_NAME         = "%1$s (%2$s)";
  /** Print search name together with its resource reference. */
  public static final String FMT_NAME_REF         = "%2$s (%1$s)";
  /** Print resource reference together with value in parentheses. */
  public static final String FMT_REF_VALUE        = "%1$s (%3$s)";
  /** Print resource reference only. */
  public static final String FMT_REF_ONLY         = "%1$s";
  /** Print only the search name of the resource. */
  public static final String FMT_NAME_ONLY        = "%2$s";
  /** Print resource value only. */
  public static final String FMT_VALUE_ONLY       = "%3$s";

  private final List<RefEntry> resources;
  private final String defaultLabel;
  private final String formatString;
  private JButton bView;
  private TextListPanel<RefEntry> list;
  private long value;

  public ResourceBitmap(ByteBuffer buffer, int offset, int length, String name,
      List<RefEntry> resources)
  {
    this(buffer, offset, length, name, resources, null, null);
  }

  public ResourceBitmap(ByteBuffer buffer, int offset, int length, String name,
      List<RefEntry> resources, String defLabel)
  {
    this(buffer, offset, length, name, resources, defLabel, null);
  }

  public ResourceBitmap(ByteBuffer buffer, int offset, int length, String name,
                        List<RefEntry> resources, String defLabel, String fmt)
  {
    super(offset, length, name);
    this.formatString = (fmt != null) ? fmt : FMT_REF_VALUE;
    this.defaultLabel = (defLabel != null) ? defLabel : "Unknown";

    this.resources = new ArrayList<RefEntry>((resources != null) ? resources.size() : 10);
    if (resources != null) {
      for (final RefEntry entry: resources) {
        entry.setFormatString(this.formatString);
        this.resources.add(entry);
      }
    }
    Collections.sort(this.resources);

    read(buffer, offset);
  }

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == bView) {
      final RefEntry selected = list.getSelectedValue();
      if (selected != null) {
        final ResourceEntry entry = selected.getResourceEntry();
        new ViewFrame(list.getTopLevelAncestor(), entry == null ? null : ResourceFactory.getResource(entry));
      }
    }
  }

//--------------------- End Interface ActionListener ---------------------

//--------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    final RefEntry selected = list.getSelectedValue();
    bView.setEnabled(selected != null && selected.isResource());
  }

//--------------------- End Interface ListSelectionListener ---------------------

//--------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(final ActionListener container)
  {
    list = new TextListPanel<>(resources, false);
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event)
      {
        if (event.getClickCount() == 2)
          container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
      }
    });

    RefEntry curEntry = getRefEntry(value);
    if (curEntry == null) {
      curEntry = getRefEntry(0L);
    }
    if (curEntry == null && resources.size() > 0) {
      curEntry = resources.get(0);
    }
    if (curEntry != null) {
      list.setSelectedValue(curEntry, true);
    }

    JButton bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);
    bView = new JButton("View/Edit", Icons.getIcon(Icons.ICON_ZOOM_16));
    bView.addActionListener(this);
    bView.setEnabled(curEntry != null && curEntry.isResource());
    list.addListSelectionListener(this);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel panel = new JPanel(gbl);

    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridheight = 2;
    gbl.setConstraints(list, gbc);
    panel.add(list);

    gbc.gridheight = 1;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(3, 6, 3, 0);
    gbc.anchor = GridBagConstraints.SOUTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(bUpdate, gbc);
    panel.add(bUpdate);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.NORTH;
    gbl.setConstraints(bView, gbc);
    panel.add(bView);

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
    final RefEntry selected = list.getSelectedValue();
    if (selected == null) {
      return false;
    }

    value = selected.getValue();
    // notifying listeners
    fireValueUpdated(new UpdateEvent(this, struct));

    return true;
  }

//--------------------- End Interface Editable ---------------------

//--------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    writeLong(os, value);
  }

//--------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

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

//--------------------- End Interface Readable ---------------------

  @Override
  public String toString()
  {
    String resName, searchString;
    RefEntry ref = getRefEntry(value);
    if (ref != null) {
      resName = ref.getResourceName();
      searchString = ref.getSearchString();
    } else {
      resName = defaultLabel;
      searchString = "";
    }

    return String.format(formatString, resName, searchString, Long.toString(value));
  }

  //--------------------- Begin Interface IsNumeric ---------------------

  @Override
  public int getValue()
  {
    return (int)value;
  }

  @Override
  public long getLongValue()
  {
    return value;
  }

//--------------------- End Interface IsNumeric ---------------------

//--------------------- Begin Interface IsReference ---------------------

  @Override
  public String getResourceName()
  {
    RefEntry entry = getRefEntry(value);
    if (entry != null) {
      return entry.getResourceName();
    } else {
      return "";
    }
  }

//--------------------- End Interface IsReference ---------------------

  public List<RefEntry> getResourceList()
  {
    return resources;
  }

  public String getFormatString()
  {
    return formatString;
  }

  private RefEntry getRefEntry(long value)
  {
    for (int i = 0, size = resources.size(); i < size; i++) {
      RefEntry entry = resources.get(i);
      if (entry.getValue() == value) {
        return entry;
      }
    }
    return null;
  }

//-------------------------- INNER CLASSES --------------------------

  public static class RefEntry implements Comparable<RefEntry>
  {
    private final long value;           // associated ID
    private final String name;          // alternate label if ResourceEntry is empty
    private final ResourceEntry entry;  // contains resource if available
    private final String searchString;  // resource-dependent search string
    private String fmt;                 // format string for textual representation
    private String desc;                // cached textual output for toString() method

    public RefEntry(long value, String ref)
    {
      this(value, ref, null, null);
    }

    public RefEntry(long value, String ref, String search)
    {
      this(value, ref, search, null);
    }

    public RefEntry(long value, String ref, String search, List<Path> searchDirs)
    {
      this.value = value;
      this.entry = (ref.lastIndexOf('.') > 0) ? ResourceFactory.getResourceEntry(ref, true, searchDirs) : null;
      if (this.entry != null) {
        this.searchString = (search != null) ? search : entry.getSearchString();
        this.name = null;
      } else {
        this.searchString = (search != null) ? search : "";
        this.name = ref;
      }
      this.fmt = FMT_REF_VALUE;
      this.desc = String.format(fmt, getResourceName(), getSearchString(), Long.toString(value));
    }

    @Override
    public String toString()
    {
      return desc;
    }

    @Override
    public boolean equals(Object o)
    {
      return toString().equalsIgnoreCase(o.toString());
    }

    @Override
    public int compareTo(RefEntry o)
    {
      return toString().compareToIgnoreCase(o.toString());
    }

    public boolean isResource() { return (entry != null); }

    public long getValue() { return value; }

    public ResourceEntry getResourceEntry() { return entry; }

    public String getResourceName()
    {
      if (entry != null) {
        return entry.getResourceName();
      } else {
        return name;
      }
    }

    public String getSearchString()
    {
      return searchString;
    }

    public String getFormatString() { return fmt; }

    public void setFormatString(String fmt)
    {
      if (fmt != null) {
        this.fmt = fmt;
      } else {
        this.fmt = FMT_REF_VALUE;
      }
      this.desc = String.format(fmt, getResourceName(), getSearchString(), Long.toString(value));
    }
  }
}
