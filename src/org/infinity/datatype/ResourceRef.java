// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.gui.SoundPanel;
import org.infinity.gui.StructViewer;
import org.infinity.gui.TextListPanel;
import org.infinity.gui.ViewFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Closeable;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.io.StreamUtils;

/**
 * Represents reference to another resource in game. This resource can be sound, item, dialog, creature, image.
 *
 * <h2>Bean property</h2> When this field is child of {@link AbstractStruct}, then changes of its internal value
 * reported as {@link PropertyChangeEvent}s of the {@link #getParent() parent} struct.
 * <ul>
 * <li>Property name: {@link #getName() name} of this field</li>
 * <li>Property type: {@link String}</li>
 * <li>Value meaning: name of the resource (without extension, 8 chars max). {code null} means that field not contains
 * any reference</li>
 * </ul>
 */
public class ResourceRef extends Datatype
    implements Editable, IsTextual, IsReference, Closeable, ActionListener, ListSelectionListener {
  private static final Comparator<ResourceRefEntry> IGNORE_CASE_EXT_COMPARATOR = new IgnoreCaseExtComparator();

  /** List of resource types that are can be used to display associated icons.  */
  private static final HashSet<String> ICON_EXTENSIONS = new HashSet<>(Arrays.asList("BMP", "ITM", "SPL"));

  /** Special constant that represents absense of resource in the field. */
  private static final ResourceRefEntry NONE = new ResourceRefEntry("None");

  /** Possible file extensions that can have this resource. */
  private final String[] types;

  /** Raw bytes of the resource reference, read from stream. */
  private final ByteBuffer buffer;

  private String type;

  /** Name of the resource, called {@code ResRef}, 8 bytes usually, as stored in the resource. */
  private String resname;
  private boolean resnameChanged; // Re-encode only if the value changes. Prevents altering the data of resref fields
                                  // on disk unless they were explicitly modified by the user.

  /** Button that used to open editor of current selected element in the list. */
  private JButton bView;

  /** Handles playback of sound resources. */
  private SoundPanel soundPanel;

  /**
   * GUI component that lists all available resources that can be set to this resource reference and have edit field for
   * ability to enter resource reference manually.
   */
  private TextListPanel<ResourceRefEntry> list;

  /** Contains the {@link Resource} of the currently selected resource reference. */
  private Resource currentResource;

  /**
   * Returns a list of resource extensions that can be used to display associated icons.
   * @return String set with file extensions (without leading dot).
   */
  public static Set<String> getIconExtensions() {
    return Collections.unmodifiableSet(ICON_EXTENSIONS);
  }

  public ResourceRef(ByteBuffer buffer, int offset, String name, String... types) {
    this(buffer, offset, 8, name, types);
  }

  private ResourceRef(ByteBuffer buffer, int offset, int length, String name, String... types) {
    super(offset, length, name);
    this.buffer = StreamUtils.getByteBuffer(length);
    if (types == null || types.length == 0) {
      this.types = new String[] { "" };
    } else {
      this.types = types;
    }
    this.type = this.types[0];
    read(buffer, offset);
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == bView) {
      final ResourceRefEntry selected = list.getSelectedValue();
      if (isEditable(selected)) {
        new ViewFrame(list.getTopLevelAncestor(), ResourceFactory.getResource(selected.entry));
      }
    }
  }

  @Override
  public JComponent edit(final ActionListener container) {
    final List<List<ResourceEntry>> resourceList = new ArrayList<>(types.length);
    int count = 0;
    for (final String type : types) {
      final List<ResourceEntry> entries = ResourceFactory.getResources(type);
      resourceList.add(entries);
      count += entries.size();
    }

    final List<ResourceRefEntry> values = new ArrayList<>(1 + count);
    values.add(NONE);
    for (List<ResourceEntry> resources : resourceList) {
      for (ResourceEntry entry : resources) {
        // FIXME: ResRefChecker check only that point is exist, so this must be
        // the same check or this check must be inside isLegalEntry(...)
        // There only 2 places where isLegalEntry is called: this and ResRefChecker
        if (isLegalEntry(entry) && entry.getResourceRef().length() <= 8) {
          values.add(new ResourceRefEntry(entry));
        }
      }
    }
    addExtraEntries(values);
    values.sort(IGNORE_CASE_EXT_COMPARATOR);
    final boolean showIcons = BrowserMenuBar.getInstance().getOptions().showResourceListIcons() &&
        Arrays.stream(types).anyMatch(s -> ICON_EXTENSIONS.contains(s.toUpperCase()));
    final TextListPanel.IconType iconType = showIcons ? TextListPanel.IconType.RESOURCE : TextListPanel.IconType.NONE;
    list = new TextListPanel<>(values, false, iconType);
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
          container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
        }
      }
    });

    ResourceEntry entry = null;
    for (int i = 0; i < types.length && entry == null; i++) {
      entry = ResourceFactory.getResourceEntry(resname + '.' + types[i], true);
    }
    if (entry != null) {
      for (ResourceRefEntry e : values) {
        if (entry.equals(e.entry)) {
          list.setSelectedValue(e, true);
          break;
        }
      }
    } else {
      list.setSelectedValue(NONE, true);
      for (ResourceRefEntry e : values) {
        if (e.name.equals(resname)) {
          list.setSelectedValue(e, true);
          break;
        }
      }
    }

    JButton bUpdate = new JButton("Update value", Icons.ICON_REFRESH_16.getIcon());
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    bView = new JButton("View/Edit", Icons.ICON_ZOOM_16.getIcon());
    bView.addActionListener(this);

    soundPanel = new SoundPanel(SoundPanel.Option.COMPACT_CONTROLS, SoundPanel.Option.TIME_LABEL,
        SoundPanel.Option.PROGRESS_BAR);
    soundPanel.setDisplayFormat(SoundPanel.DisplayFormat.ELAPSED_TOTAL_PRECISE);
    soundPanel.setVisible(ResourceEntry.isSound(types));

    list.addListSelectionListener(this);
    setResourceEntryUpdated(list.getSelectedValue());

    GridBagConstraints gbc = null;
    JPanel panel = new JPanel(new GridBagLayout());

    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 5, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    panel.add(list, gbc);

    // spacer keeps controls in the center
    final JPanel spacerTop = new JPanel();
    spacerTop.setMinimumSize(new Dimension());
    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    panel.add(spacerTop, gbc);

    gbc = ViewerUtil.setGBC(gbc, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
        new Insets(3, 6, 3, 6), 0, 0);
    panel.add(bUpdate, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
        new Insets(3, 6, 3, 6), 0, 0);
    panel.add(bView, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
            new Insets(24, 6, 3, 6), 0, 0);
    panel.add(soundPanel, gbc);

    // spacer keeps controls in the center
    final JPanel spacerBottom = new JPanel();
    spacerTop.setMinimumSize(new Dimension());
    gbc = ViewerUtil.setGBC(gbc, 1, 4, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    panel.add(spacerBottom, gbc);

    panel.setMinimumSize(Misc.getScaledDimension(DIM_MEDIUM));
    panel.setPreferredSize(panel.getMinimumSize());
    return panel;
  }

  @Override
  public void select() {
    list.ensureIndexIsVisible(list.getSelectedIndex());
  }

  @Override
  public boolean updateValue(AbstractStruct struct) {
    String oldString = getText();
    final ResourceRefEntry selected = list.getSelectedValue();
    if (selected == NONE) {
      setValue(NONE.name);// FIXME: use null instead of this

      // notifying listeners
      if (!getText().equals(oldString)) {
        fireValueUpdated(new UpdateEvent(this, struct));
      }

      return true;
    }

    final ResourceEntry entry = selected.entry;
    if (entry == null) {
      setValue(selected.name);
    } else {
      boolean found = false;
      for (final String type : types) {
        found = entry.getExtension().equalsIgnoreCase(type);
        if (found) {
          this.type = type;
          setValue(entry.getResourceRef());
          break;
        }
      }
      if (!found) {
        return false;
      }
    }

    // notifying listeners
    if (!getText().equals(oldString)) {
      fireValueUpdated(new UpdateEvent(this, struct));
    }

    return true;
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    setResourceEntryUpdated(list.getSelectedValue());
  }

  @Override
  public void write(OutputStream os) throws IOException {
    if (resnameChanged) {
      if (resname.equalsIgnoreCase(NONE.name)) {// FIXME: use null instead of NONE.name
        buffer.position(0);
        String s = StreamUtils.readString(buffer, buffer.limit());
        buffer.position(0);
        if (s.equalsIgnoreCase(NONE.name)) {// TODO: What this check do?
          StreamUtils.writeBytes(os, buffer);
        } else {
          StreamUtils.writeBytes(os, (byte) 0, buffer.limit());
        }
      } else {
        StreamUtils.writeString(os, resname, getSize());
      }
    } else {
      buffer.position(0);
      StreamUtils.writeBytes(os, buffer);
    }
  }

  @Override
  public int read(ByteBuffer buffer, int offset) {
    StreamUtils.copyBytes(buffer, offset, this.buffer, 0, getSize());
    this.buffer.position(0);
    final String s = StreamUtils.readString(this.buffer, this.buffer.limit()).toUpperCase(Locale.ENGLISH);
    resname = s.isEmpty() || s.equalsIgnoreCase(NONE.name) ? NONE.name : s;// FIXME: use null instead of NONE.name
    resnameChanged = false;

    // determine the correct file extension
    if (!resname.equals(NONE.name)) { // FIXME: use null instead of NONE.name
      for (final String type : types) {
        if (null != ResourceFactory.getResourceEntry(resname + '.' + type, true)) {
          this.type = type;
          break;
        }
      }
    }

    return offset + getSize();
  }

  @Override
  public String toString() {
    if (resname.equals(NONE.name)) {
      return resname;
    }
    String searchName = getSearchName();
    if (searchName != null) {
      return getResourceName() + " (" + searchName + ')';
    }
    return getResourceName();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(resname, type);
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
    ResourceRef other = (ResourceRef) obj;
    return Objects.equals(resname, other.resname) && Objects.equals(type, other.type);
  }

  @Override
  public String getText() {
    return resname;
  }

  @Override
  public String getResourceName() {
    if (!resname.equals(NONE.name)) {// FIXME: use null instead of NONE.name
      return resname + '.' + type;
    }
    return resname;
  }

  @Override
  public void close() throws Exception {
    if (soundPanel != null) {
      soundPanel.setPlaying(false);
    }
  }

  public boolean isEmpty() {
    return (resname.equals(NONE.name));// FIXME: use null instead of NONE.name
  }

  public String getSearchName() {
    ResourceEntry entry = ResourceFactory.getResourceEntry(getResourceName(), true);
    if (entry != null) {
      return entry.getSearchString();
    }
    return null;
  }

  public String getType() {
    return type;
  }

  /**
   * Check that this object can hold reference to the specified resource.
   *
   * @param entry Pointer to resource for check. If {@code null}, method returns {@code false}
   *
   * @return {@code false} if {@code entry} can not be target for this resource reference, {@code true} otherwise
   */
  public boolean isLegalEntry(ResourceEntry entry) {
    return entry != null && entry.getResourceName().lastIndexOf('.') != 0;
  }

  /**
   * Appends additional resources to the list of selectable resources for this reference.
   *
   * @param entries List with selectable resources. Implementers must add additional resources to it. Never {@code null}
   */
  void addExtraEntries(List<ResourceRefEntry> entries) {
  }

  private void setResourceEntryUpdated(ResourceRefEntry entry) {
    closeResource(currentResource);
    if (entry != null) {
      bView.setEnabled(isEditable(entry));
      if (soundPanel != null && soundPanel.isVisible()) {
        try {
          soundPanel.loadSound(entry.getEntry());
          soundPanel.setEnabled(isSound(entry));
        } catch (NullPointerException e) {
          // expected
          soundPanel.setEnabled(false);
        } catch (Exception e) {
          Logger.error(e);
          soundPanel.setEnabled(false);
        }
      }
    } else {
      bView.setEnabled(false);
      if (soundPanel != null && soundPanel.isVisible()) {
        soundPanel.setEnabled(false);
      }
    }
  }

  private void closeResource(Resource resource) {
    if (soundPanel != null) {
      soundPanel.unload();
    }
    if (resource instanceof Closeable) {
      try {
        ((Closeable) resource).close();
      } catch (Exception e) {
        Logger.error(e);
      }
    }
  }

  private boolean isEditable(ResourceRefEntry ref) {
    return ref != null && ref != NONE && ref.entry != null;
  }

  private boolean isSound(ResourceRefEntry ref) {
    return ref != null && ref.entry != null && ref.entry.isSound();
  }

  public void setValue(String newValue) {
    final String oldValue = NONE.name.equals(resname) ? null : resname;
    resname = newValue;

    if (NONE.name.equals(newValue)) {
      newValue = null;
    }
    if (!Objects.equals(oldValue, newValue)) {
      resnameChanged = true;
      firePropertyChange(oldValue, newValue);
    }
  }

  // -------------------------- INNER CLASSES --------------------------
  /** Class that represents resource reference in the list of choice. */
  public static final class ResourceRefEntry {
    final ResourceEntry entry;

    /**
     * If {@link #entry} is not {@code null}, contains full resource name (i.e. name and extension), otherwise contains
     * arbitrary value with reference to the resource.
     */
    final String name;

    private ResourceRefEntry(ResourceEntry entry) {
      this.entry = entry;
      this.name = entry.getResourceName();
    }

    ResourceRefEntry(String name) {
      this.entry = null;
      this.name = name;
    }

    public ResourceEntry getEntry() {
      return entry;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return entry == null ? name : BrowserMenuBar.getInstance().getOptions().getResRefMode().format(entry);
    }
  }

  private static final class IgnoreCaseExtComparator implements Comparator<ResourceRefEntry> {
    @Override
    public int compare(ResourceRefEntry o1, ResourceRefEntry o2) {
      // null always smaller any other value
      if (o1 == null) {
        return o2 == null ? 0 : -1;
      }
      if (o2 == null) {
        return 1;
      }

      // NONE always smaller any other value except null
      if (o1 == NONE) {
        return o2 == NONE ? 0 : -1;
      }
      if (o2 == NONE) {
        return 1;
      }

      final String s1 = o1.toString();
      final String s2 = o2.toString();
      // TODO: must use special method to extract only name without extension
      final int i1 = s1.lastIndexOf('.') > 0 ? s1.lastIndexOf('.') : s1.length();
      final int i2 = s2.lastIndexOf('.') > 0 ? s2.lastIndexOf('.') : s2.length();
      return s1.substring(0, i1).compareToIgnoreCase(s2.substring(0, i2));
    }
  }
}
