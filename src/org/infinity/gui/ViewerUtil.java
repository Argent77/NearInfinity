// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.infinity.NearInfinity;
import org.infinity.datatype.Flag;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.StringRef;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractAbility;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.Viewable;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.gam.PartyNPC;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamDecoder.BamControl;
import org.infinity.resource.graphics.BamDecoder.FrameEntry;
import org.infinity.resource.graphics.BamResource;
import org.infinity.resource.graphics.GraphicsResource;
import org.infinity.resource.graphics.MosResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IconCache;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.SimpleListModel;
import org.infinity.util.StringTable;

public final class ViewerUtil {
  /**
   * A collection of pastel shaded colors that can be used to colorize the background of list, table or tree items.
   */
  private static final Color[] BACKGROUND_COLORS = {
      new Color(0xceccff), new Color(0xffcce6), new Color(0xccffe9), new Color(0xfaffcc),
      new Color(0xccddff), new Color(0xffccf9), new Color(0xccffd7), new Color(0xfff2cc),
      new Color(0xccf0ff), new Color(0xf4ccff), new Color(0xd5ffcc), new Color(0xffdfcc),
      new Color(0xccfffc), new Color(0xe1ccff), new Color(0xe8ffcc), new Color(0xffcccc),
  };

  /**
   * A collection of dark shaded colors that can be used to colorize the background of list, table or tree items
   * in dark mode.
   */
  private static final Color[] BACKGROUND_COLORS_DARK = {
      new Color(0x3a3b24), new Color(0x243b2f), new Color(0x3b242e), new Color(0x27243b),
      new Color(0x3b3324), new Color(0x243b27), new Color(0x3b2436), new Color(0x242a3b),
      new Color(0x3b2b24), new Color(0x293b24), new Color(0x37243b), new Color(0x24323b),
      new Color(0x3b2426), new Color(0x313b24), new Color(0x2e243b), new Color(0x243b3b),
  };

  /** Returns a collection of background colors, based on the Look&Feel theme type (light/dark). */
  public static Color[] getBackgroundColors() {
    if (NearInfinity.getInstance().isDarkMode()) {
      return BACKGROUND_COLORS_DARK;
    }
    return BACKGROUND_COLORS;
  }

  public static void addLabelFieldPair(JPanel panel, StructEntry entry, GridBagLayout gbl, GridBagConstraints gbc,
      boolean endline) {
    addLabelFieldPair(panel, entry, gbl, gbc, endline, 0);
  }

  public static void addLabelFieldPair(JPanel panel, StructEntry entry, GridBagLayout gbl, GridBagConstraints gbc,
      boolean endline, int maxLength) {
    if (entry == null) {
      return;
    }
    JLabel label = new JLabel(entry.getName());
    JComponent text;
    if (entry instanceof ResourceRef) {
      text = new LinkButton((ResourceRef) entry, maxLength);
    } else {
      String s;
      String help = null;
      if (entry instanceof StringRef) {
        StringTable.Format fmt = BrowserMenuBar.getInstance().getOptions().showStrrefs() ? StringTable.Format.STRREF_SUFFIX
            : StringTable.Format.NONE;
        s = ((StringRef) entry).toString(fmt);
      } else {
        s = entry.toString();
      }
      if (maxLength > 0 && s.length() > maxLength) {
        help = s;
        s = s.substring(0, maxLength) + "...";
      }
      text = new JLabel(s);
      if (help != null) {
        text.setToolTipText(help);
      }
    }
    addLabelFieldPair(panel, label, text, gbl, gbc, endline);
  }

  public static void addLabelFieldPair(JPanel panel, String name, String field, GridBagLayout gbl,
      GridBagConstraints gbc, boolean endline) {
    addLabelFieldPair(panel, name, field, gbl, gbc, endline, 0);
  }

  public static void addLabelFieldPair(JPanel panel, String name, String field, GridBagLayout gbl,
      GridBagConstraints gbc, boolean endline, int maxLength) {
    if (name != null) {
      JLabel label = new JLabel(name);
      String labelText = (field != null) ? field : "";
      String help = null;
      if (maxLength > 0 && labelText.length() > maxLength) {
        help = labelText;
        labelText = labelText.substring(0, maxLength) + "...";
      }
      JComponent text = new JLabel(labelText);
      if (help != null) {
        text.setToolTipText(help);
      }
      addLabelFieldPair(panel, label, text, gbl, gbc, endline);
    }
  }

  public static void addLabelFieldPair(JPanel panel, JLabel name, JComponent value, GridBagLayout gbl,
      GridBagConstraints gbc, boolean endline) {
    if (name != null) {
      if (value == null) {
        value = new JLabel();
      }

      if (!(value instanceof LinkButton)) {
        value.setFont(value.getFont().deriveFont(Font.PLAIN));
      }

      gbc.weightx = 0.0;
      gbc.fill = GridBagConstraints.NONE;
      gbc.gridwidth = 1;
      gbc.anchor = GridBagConstraints.WEST;
      gbl.setConstraints(name, gbc);
      panel.add(name);

      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      if (endline) {
        gbc.gridwidth = GridBagConstraints.REMAINDER;
      }
      gbl.setConstraints(value, gbc);
      panel.add(value);
    }
  }

  /**
   * Creates a component that lists all available BAM frames and the field name of the {@link ResourceRef} instance.
   */
  public static JComponent makeBamPanel(ResourceRef iconRef) {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gbc = new GridBagConstraints();

    final ResourceEntry iconEntry = ResourceFactory.getResourceEntry(iconRef.getResourceName());
    if (iconEntry != null) {
      try {
        final BamDecoder decoder = BamDecoder.loadBam(iconEntry);
        final BamControl ctrl = Objects.requireNonNull(decoder).createControl();

        // collecting BAM frame indices
        final List<Integer> frameIndices = new ArrayList<>();
        for (int i = 0; i < ctrl.cycleCount(); i++) {
          for (int j = 0; j < ctrl.cycleFrameCount(i); j++) {
            int index = ctrl.cycleGetFrameIndexAbsolute(i, j);
            if (!frameIndices.contains(index)) {
              frameIndices.add(index);
            }
          }
        }

        // adding frame images to panel
        final JPanel subPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        for (final int index : frameIndices) {
          final FrameEntry frameInfo = decoder.getFrameInfo(index);
          if (frameInfo.getWidth() > 1 && frameInfo.getHeight() > 1) {
            final Image image = decoder.frameGet(ctrl, index);
            subPanel.add(new JLabel(new ImageIcon(image), SwingConstants.CENTER));
          }
        }

        ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
            new Insets(0, 0, 0, 0), 0, 0);
        panel.add(subPanel, gbc);
        ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
            new Insets(4, 0, 0, 0), 0, 0);
        panel.add(new JLabel(iconRef.getName(), SwingConstants.CENTER), gbc);

        return panel;
      } catch (Exception e) {
        Logger.error(e);
      }
    }

    ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    panel.add(new JLabel("No " + iconRef.getName().toLowerCase(Locale.ROOT), SwingConstants.CENTER), gbc);
    return panel;
  }

  /**
   * Creates a component that shows the specified BAM frame and the field name of the {@link ResourceRef} instance.
   */
  public static JLabel makeBamPanel(ResourceRef iconRef, int frameNr) {
    final ResourceEntry iconEntry = ResourceFactory.getResourceEntry(iconRef.getResourceName());
    if (iconEntry != null) {
      try {
        final BamDecoder decoder = BamDecoder.loadBam(iconEntry);
        final BamControl ctrl = Objects.requireNonNull(decoder).createControl();
        final JLabel label = new JLabel(iconRef.getName(), SwingConstants.CENTER);
        frameNr = Math.min(frameNr, decoder.frameCount() - 1);
        label.setIcon(new ImageIcon(decoder.frameGet(ctrl, frameNr)));
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        return label;
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return new JLabel("No " + iconRef.getName().toLowerCase(Locale.ROOT), SwingConstants.CENTER);
  }

  /**
   * Creates a component that shows the specified BAM cycle frame and the field name of the {@link ResourceRef} instance.
   */
  public static JLabel makeBamPanel(ResourceRef iconRef, int animNr, int frameNr) {
    final ResourceEntry iconEntry = ResourceFactory.getResourceEntry(iconRef.getResourceName());
    if (iconEntry != null) {
      try {
        final BamDecoder decoder = BamDecoder.loadBam(iconEntry);
        assert decoder != null;
        final BamControl ctrl = decoder.createControl();
        final JLabel label = new JLabel(iconRef.getName(), SwingConstants.CENTER);
        int frameIdx = -1;
        for (int curAnimIdx = animNr; curAnimIdx >= 0 && frameIdx < 0; curAnimIdx--) {
          for (int curFrameIdx = frameNr; curFrameIdx >= 0 && frameIdx < 0; curFrameIdx--) {
            frameIdx = ctrl.cycleGetFrameIndexAbsolute(curAnimIdx, curFrameIdx);
          }
        }
        label.setIcon(new ImageIcon(decoder.frameGet(ctrl, frameIdx)));
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        return label;
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return new JLabel("No " + iconRef.getName().toLowerCase(Locale.ROOT), SwingConstants.CENTER);
  }

  /** Creates a panel with the biggest graphics (by area) available in the specified BAM resource. */
  public static JLabel makeMaxBamPanel(ResourceRef iconRef) {
    ResourceEntry iconEntry = ResourceFactory.getResourceEntry(iconRef.getResourceName());
    if (iconEntry != null) {
      try {
        final BamDecoder decoder = BamDecoder.loadBam(iconEntry);
        int numFrames = Objects.requireNonNull(decoder).frameCount();
        int maxSize = -1;
        int frameIdx = -1;
        for (int idx = 0; idx < numFrames; idx++) {
          final BamDecoder.FrameEntry frameInfo = decoder.getFrameInfo(idx);
          int curSize = frameInfo.getWidth() * frameInfo.getHeight();
          if (curSize > maxSize) {
            maxSize = curSize;
            frameIdx = idx;
          }
        }
        if (frameIdx >= 0) {
          final BamControl control = decoder.createControl();
          final JLabel label = new JLabel(iconRef.getName(), SwingConstants.CENTER);
          label.setIcon(new ImageIcon(decoder.frameGet(control, frameIdx)));
          label.setVerticalTextPosition(SwingConstants.BOTTOM);
          label.setHorizontalTextPosition(SwingConstants.CENTER);
          return label;
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return new JLabel("No " + iconRef.getName().toLowerCase(Locale.ROOT), SwingConstants.CENTER);
  }

  public static JLabel makeCheckLabel(StructEntry entry, String yes) {
    JLabel check = new JLabel(entry.getName());
    if (entry.toString().equalsIgnoreCase(yes)) {
      check.setIcon(Icons.ICON_CHECK_16.getIcon());
    } else {
      check.setIcon(Icons.ICON_CHECK_NOT_16.getIcon());
    }
    return check;
  }

  public static JPanel makeCheckPanel(Flag flag, int cols) {
    JPanel panel = new JPanel(new GridLayout(0, cols, 8, 4));
    for (int i = 0; i < flag.getSize() << 3; i++) {
      final String label = flag.getString(i);
      if (label != null && !label.isEmpty()) {
        final JLabel check = new JLabel(label);
        final Icons icon = flag.isFlagSet(i) ? Icons.ICON_CHECK_16 : Icons.ICON_CHECK_NOT_16;
        check.setIcon(icon.getIcon());
        panel.add(check);
      }
    }
    panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(flag.getName()),
        BorderFactory.createEmptyBorder(2, 8, 2, 8)));
    return panel;
  }

  public static JLabel makeImagePanel(ResourceRef imageRef) {
    return makeImagePanel(imageRef, false);
  }

  public static JLabel makeImagePanel(ResourceRef imageRef, boolean searchExtraDirs) {
    ResourceEntry imageEntry = ResourceFactory.getResourceEntry(imageRef.getResourceName(), searchExtraDirs);
    if (imageEntry != null) {
      Resource resource = ResourceFactory.getResource(imageEntry);
      if (resource != null) {
        JLabel label = new JLabel(imageRef.getName(), SwingConstants.CENTER);
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        if (resource instanceof GraphicsResource) {
          label.setIcon(new ImageIcon(((GraphicsResource) resource).getImage()));
        } else if (resource instanceof MosResource) {
          label.setIcon(new ImageIcon(((MosResource) resource).getImage()));
        } else if (resource instanceof BamResource) {
          BamResource br = (BamResource) resource;
          if (br.getFrameCount() > 0) {
            label.setIcon(new ImageIcon(br.getFrame(0)));
          }
        }
        return label;
      }
    }
    return new JLabel("No " + imageRef.getName().toLowerCase(Locale.ROOT), SwingConstants.CENTER);
  }

  /**
   * Creates panel with the name, list control and button for edit selected list element.
   *
   * @param title     Name of the panel
   * @param struct    Structure, which attributes must be shown in the returned editor
   * @param listClass List will contain all attributes of {@code struct} with this class
   *
   * @return Editor for show list of the specified attributes
   */
  public static StructListPanel makeListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass) {
    return new StructListPanel(title, struct, listClass, null, null, null);
  }

  /**
   * Creates panel with the name, list control and button for edit selected list element.
   *
   * @param title     Name of the panel
   * @param struct    Structure, which attributes must be shown in the returned editor
   * @param listClass List will contain all attributes of {@code struct} with this class
   * @param attrName  Name of attribute in the {@code listClass}, used to show in the list
   *
   * @return Editor for show list of the specified attributes
   */
  public static StructListPanel makeListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass,
      String attrName) {
    return new StructListPanel(title, struct, listClass, getAttributeEntry(attrName), null, null);
  }

  /**
   * Creates panel with the name, list control and button for edit selected list element.
   *
   * @param title     Name of the panel
   * @param struct    Structure, which attributes must be shown in the returned editor
   * @param listClass List will contain all attributes of {@code struct} with this class
   * @param attrName  Name of attribute in the {@code listClass}, used to show in the list
   * @param renderer  A custom renderer for displaying list entries.
   *
   * @return Editor for show list of the specified attributes
   */
  public static StructListPanel makeListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass,
      String attrName, ListCellRenderer<Object> renderer) {
    return new StructListPanel(title, struct, listClass, getAttributeEntry(attrName), renderer, null);
  }

  /**
   * Creates panel with the name, list control and button for edit selected list element.
   *
   * @param title     Name of the panel
   * @param struct    Structure, which attributes must be shown in the returned editor
   * @param listClass List will contain all attributes of {@code struct} with this class
   * @param attrName  Name of attribute in the {@code listClass}, used to show in the list
   * @param renderer  A custom renderer for displaying list entries.
   * @param listener  A custom {@link ListSelectionListener} that reacts to list selection events.
   *
   * @return Editor for show list of the specified attributes
   */
  public static StructListPanel makeListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass,
      String attrName, ListCellRenderer<Object> renderer, ListSelectionListener listener) {
    return new StructListPanel(title, struct, listClass, getAttributeEntry(attrName), renderer, listener);
  }

  /**
   * Creates panel with the name, list control and button for edit selected list element.
   *
   * @param title     Name of the panel
   * @param struct    Structure, which attributes must be shown in the returned editor
   * @param listClass List will contain all attributes of {@code struct} with this class
   * @param attrEntry A function that retrieves and returns a {@code StructEntry} to be displayed in the list.
   *
   * @return Editor for show list of the specified attributes
   */
  public static StructListPanel makeListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass,
      AttributeEntry attrEntry) {
    return new StructListPanel(title, struct, listClass, attrEntry, null, null);
  }

  /**
   * Creates panel with the name, list control and button for edit selected list element.
   *
   * @param title     Name of the panel
   * @param struct    Structure, which attributes must be shown in the returned editor
   * @param listClass List will contain all attributes of {@code struct} with this class
   * @param attrEntry A function that retrieves and returns a {@code StructEntry} to be displayed in the list.
   * @param renderer  A custom renderer for displaying list entries.
   *
   * @return Editor for show list of the specified attributes
   */
  public static StructListPanel makeListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass,
      AttributeEntry attrEntry, ListCellRenderer<Object> renderer) {
    return new StructListPanel(title, struct, listClass, attrEntry, renderer, null);
  }

  /**
   * Creates panel with the name, list control and button for edit selected list element.
   *
   * @param title     Name of the panel
   * @param struct    Structure, which attributes must be shown in the returned editor
   * @param listClass List will contain all attributes of {@code struct} with this class
   * @param attrEntry A function that retrieves and returns a {@code StructEntry} to be displayed in the list.
   * @param renderer  A custom renderer for displaying list entries.
   * @param listener  A custom {@link ListSelectionListener} that reacts to list selection events.
   *
   * @return Editor for show list of the specified attributes
   */
  public static StructListPanel makeListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass,
      AttributeEntry attrEntry, ListCellRenderer<Object> renderer, ListSelectionListener listener) {
    return new StructListPanel(title, struct, listClass, attrEntry, renderer, listener);
  }

  /**
   * Creates a panel with a text area control and a title with the {@code StructEntry} name.
   *
   * @param entry the {@code StructEntry} instance used to derive data and title from.
   * @return a {@code JPanel} instance.
   */
  public static JPanel makeTextAreaPanel(StructEntry entry) {
    return makeTextAreaPanel(entry, true);
  }

  /**
   * Creates a panel with a text area control and an optional title with the {@code StructEntry} name.
   *
   * @param entry     the {@code StructEntry} instance used to derive data and title from.
   * @param showTitle whether to show the entry title.
   * @return a {@code JPanel} instance.
   */
  public static JPanel makeTextAreaPanel(StructEntry entry, boolean showTitle) {
    String text;
    if (entry instanceof StringRef) {
      StringTable.Format fmt = BrowserMenuBar.getInstance().getOptions().showStrrefs() ? StringTable.Format.STRREF_SUFFIX
          : StringTable.Format.NONE;
      text = ((StringRef) entry).toString(fmt);
    } else {
      text = entry.toString();
    }
    InfinityTextArea ta = new InfinityTextArea(text, true);
    ta.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getOptions().getScriptFont()));
    ta.setCaretPosition(0);
    ta.setHighlightCurrentLine(false);
    ta.setEditable(false);
    ta.setLineWrap(true);
    ta.setWrapStyleWord(true);
    InfinityScrollPane scroll = new InfinityScrollPane(ta, true);
    scroll.setLineNumbersEnabled(false);
    ta.setMargin(new Insets(3, 3, 3, 3));
    JPanel panel = new JPanel(new BorderLayout());
    if (showTitle) {
      panel.add(new JLabel(entry.getName()), BorderLayout.NORTH);
    }
    panel.add(scroll, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(5, 5));
    return panel;
  }

  /**
   * Creates and initializes a button that synchronizes the current view with the associated resource tree item.
   *
   * @param container Parent container of the sync button, as {@link JComponent} object.
   * @param entry     {@link ResourceEntry} instance of the opened resource.
   * @return Fully configured {@link JButton} control that can be added to the container.
   */
  public static JButton createViewerSyncButton(JComponent container, ResourceEntry entry) {
    // XXX: Setting zero-width space as button label to enforce correct component height
    final JButton button = new JButton("\u200b", Icons.ICON_REFRESH_16.getIcon());
    button.setIconTextGap(0);
    button.setToolTipText("Synchronizes current view with resource tree.");
    if (entry != null) {
      button.addActionListener(event -> NearInfinity.getInstance().getResourceTree().select(entry, true));
    }
    if (container != null) {
      // button is only available if viewer is embedded in NI's main window
      button.addHierarchyListener(
          event -> button.setEnabled(container.getTopLevelAncestor() == NearInfinity.getInstance()));
    }
    return button;
  }

  /**
   * Initializes a {@link GridBagConstraints} instance.
   *
   * @param gbc        Specifies a reusable {@link GridBagConstraints} instance. A new instance is created if the
   *                   parameter is {@code null}.
   * @param gridX      Specifies the cell containing the leading edge of the component's display area, where the first
   *                   cell in a row has <code>gridx=0</code>. Specify either an absolute cell index or
   *                   {@link GridBagConstraints#RELATIVE}.
   * @param gridY      Specifies the cell at the top of the component's display area, where the topmost cell has
   *                   <code>gridy=0</code>. Specify either an absolute cell index or
   *                   {@link GridBagConstraints#RELATIVE}.
   * @param gridWidth  Specifies the number of cells in a row for the component's display area. Use
   *                   {@link GridBagConstraints#REMAINDER} to specify that the component's display area will be from
   *                   <code>gridx</code> to the last cell in the row.
   * @param gridHeight Specifies the number of cells in a column for the component's display area. Use
   *                   {@link GridBagConstraints#REMAINDER} to specify that the component's display area will be from
   *                   <code>gridy</code> to the last cell in the column.
   * @param weightX    Specifies how to distribute extra horizontal space.
   * @param weightY    Specifies how to distribute extra vertical space.
   * @param anchor     This field is used when the component is smaller than its display area. It determines where,
   *                   within the display area, to place the component.
   * @param fill       This field is used when the component's display area is larger than the component's requested
   *                   size. It determines whether to resize the component, and if so, how.
   * @param insets     This field specifies the external padding of the component, the minimum amount of space between
   *                   the component and the edges of its display area.
   * @param iPadX      This field specifies the internal padding of the component, how much space to add to the minimum
   *                   width of the component. The width of the component is at least its minimum width plus
   *                   <code>ipadx</code> pixels.
   * @param iPadY      This field specifies the internal padding, that is, how much space to add to the minimum height
   *                   of the component. The height of the component is at least its minimum height plus
   *                   <code>ipady</code> pixels.
   * @return A fully initialized {@code GridBagConstraints} object with the specified arguments.
   */
  public static GridBagConstraints setGBC(GridBagConstraints gbc, int gridX, int gridY, int gridWidth, int gridHeight,
      double weightX, double weightY, int anchor, int fill, Insets insets, int iPadX, int iPadY) {
    if (gbc == null) {
      gbc = new GridBagConstraints();
    }

    gbc.gridx = gridX;
    gbc.gridy = gridY;
    gbc.gridwidth = gridWidth;
    gbc.gridheight = gridHeight;
    gbc.weightx = weightX;
    gbc.weighty = weightY;
    gbc.anchor = anchor;
    gbc.fill = fill;
    gbc.insets = (insets == null) ? new Insets(0, 0, 0, 0) : insets;
    gbc.ipadx = iPadX;
    gbc.ipady = iPadY;

    return gbc;
  }

  /** Returns a JLabel control containing a clickable link. */
  public static JLabel createUrlLabel(String url) {
    return createUrlLabel(url, url, SwingConstants.LEADING);
  }

  /** Returns a JLabel control containing a clickable link. */
  public static JLabel createUrlLabel(String text, String url) {
    return createUrlLabel(text, url, SwingConstants.LEADING);
  }

  /** Returns a JLabel control containing a clickable link. */
  public static JLabel createUrlLabel(String text, String url, int horizontalAlignment) {
    JLabel l = new JLabel("<html><a href=\"" + url + "\">" + text + "</a></html>", horizontalAlignment);
    l.addMouseListener(new UrlBrowser(url));
    l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return l;
  }

  /** Returns a specialized JLabel control containing a clickable link for opening help about regular expressions. */
  public static JLabel createRegexpHelpLabel() {
    final String text = "Help";
    final String url = "https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#sum";
    JLabel l = new JLabel("<html>(<a href=\"" + url + "\">" + text + "</a>)</html>", SwingConstants.LEADING);
    l.addMouseListener(new UrlBrowser(url));
    l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    l.setToolTipText(url);
    final Font f = l.getFont();
    l.setFont(f.deriveFont(f.getSize2D() * 0.8f));
    return l;
  }

  /**
   * Returns the first {@code Window} ancestor of {@code c}, or {@code NearInfinity.getInstance()} if {@code c} is not
   * contained inside a window.
   *
   * @param c Component to get {@code Window} ancestor of.
   * @return the first {@code Window} ancestor of {@code c}, or {@code NearInfinity.getInstance()} if {@code c} is not
   *         contained inside a window.
   */
  public static Window getWindowAncestor(Component c) {
    return getWindowAncestor(c, NearInfinity.getInstance());
  }

  /**
   * Returns the first {@code Window} ancestor of {@code c}, or {@code defWindow} if {@code c} is not contained inside a
   * window.
   *
   * @param c         Component to get {@code Window} ancestor of.
   * @param defWindow This parameter is returned if a {@code Window} could not be determined from {@code c}.
   * @return the first {@code Window} ancestor of {@code c}, or {@code defWindow} if {@code c} is not contained inside a
   *         window.
   */
  public static Window getWindowAncestor(Component c, Window defWindow) {
    Window retVal = defWindow;
    if (c != null) {
      final Window w = SwingUtilities.getWindowAncestor(c);
      if (w != null) {
        retVal = w;
      }
    }
    return retVal;
  }

  /**
   * Creates a functional object that derives a {@link StructEntry} instance with the specified {@code attrName}
   * from a given {@link StructEntry} input object.
   *
   * @param attrName Name of the attribute to find and return.
   * @return A {@link AttributeEntry} object if {@code attrName} parameter is not {@code null}, {@code null} otherwise.
   */
  private static AttributeEntry getAttributeEntry(String attrName) {
    if (attrName != null) {
      return (item) -> (item instanceof AbstractStruct) ? ((AbstractStruct) item).getAttribute(attrName) : item;
    } else {
      return null;
    }
  }

  private ViewerUtil() {
  }

  // -------------------------- INNER CLASSES --------------------------

  /**
   * A functional interface that is used to find a {@link StructEntry} attribute from a given resource or substructure
   * for display in the list.
   *
   * <p>
   * <strong>Types:</strong>
   * <ul>
   * <li><strong>Parameter:</strong> {@code StructEntry} that contains the attribute for display.</li>
   * <li><strong>Return value:</strong> {@code StructEntry} that is used for display in the list.</li>
   * </ul>
   * </p>
   */
  public interface AttributeEntry extends Function<StructEntry, StructEntry> {
  }

  public static final class StructListPanel extends JPanel
      implements TableModelListener, ActionListener, ListSelectionListener {
    private final AbstractStruct struct;
    private final Class<? extends StructEntry> listClass;
    private final JList<StructEntry> list;
    private final SimpleListModel<StructEntry> listModel = new SimpleListModel<>();
    private final JButton bOpen = new JButton("View/Edit", Icons.ICON_ZOOM_16.getIcon());

    private StructListPanel(String title, AbstractStruct struct, Class<? extends StructEntry> listClass,
        AttributeEntry attrEntry, ListCellRenderer<Object> renderer, ListSelectionListener listener) {
      super(new BorderLayout(0, 3));
      this.struct = struct;
      this.listClass = listClass;
      struct.addTableModelListener(this);
      list = new JList<>(listModel);
      list.addListSelectionListener(this);
      if (listener != null) {
        list.addListSelectionListener(listener);
      }
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      if (renderer != null) {
        list.setCellRenderer(renderer);
      }
      if (attrEntry == null) {
        for (final StructEntry o : struct.getFields()) {
          if (o.getClass() == listClass) {
            listModel.addElement(o);
          }
        }
      } else {
        if (renderer == null) {
          list.setCellRenderer(new StructListRenderer(attrEntry));
        }
        final List<AbstractStruct> templist = new ArrayList<>();
        for (final StructEntry o : struct.getFields()) {
          if (o.getClass() == listClass) {
            templist.add((AbstractStruct) o);
          }
        }
        templist.sort(new StructListComparator(attrEntry));
        for (AbstractStruct s : templist) {
          listModel.addElement(s);
        }
      }

      final JPanel parent = this;
      StructListKeyListener keyListener = new StructListKeyListener();
      list.addKeyListener(keyListener);
      list.addListSelectionListener(keyListener);
      list.addListSelectionListener(listener);
      list.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2 && list.getSelectedValue() instanceof Viewable) {
            new ViewFrame(parent.getTopLevelAncestor(), (Viewable) list.getSelectedValue());
          }
        }
      });
      if (!listModel.isEmpty()) {
        list.setSelectedIndex(0);
      }
      bOpen.addActionListener(this);
      bOpen.setEnabled(!listModel.isEmpty() && listModel.get(0) instanceof Viewable);

      add(new JLabel(title), BorderLayout.NORTH);
      add(new JScrollPane(list), BorderLayout.CENTER);
      add(bOpen, BorderLayout.SOUTH);
      setPreferredSize(new Dimension(5, 5));
    }

    /** Provides access to the list component of the panel. */
    public JList<StructEntry> getList() {
      return list;
    }

    /** Provides access to the model of the list component. */
    public SimpleListModel<StructEntry> getListModel() {
      return listModel;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      new ViewFrame(getTopLevelAncestor(), (Viewable) list.getSelectedValue());
    }

    @Override
    public void tableChanged(TableModelEvent event) {
      if (event.getType() == TableModelEvent.DELETE) {

        // go through the list and find what was deleted
        listModel.retainAll(struct.getFields());
        /*
         * // Ineffective - any better solutions? if (comp == null) { listModel.clear(); for (int i = 0; i <
         * struct.getRowCount(); i++) { StructEntry o = struct.getStructEntryAt(i); if (o.getClass() == listClass)
         * listModel.addElement(o); } } else {
         *
         * List<AbstractStruct> templist = new ArrayList<AbstractStruct>(); for (int i = 0; i < struct.getRowCount();
         * i++) { StructEntry o = struct.getStructEntryAt(i); if (o.getClass() == listClass)
         * templist.add((AbstractStruct)o); } Collections.sort(templist, comp); listModel.clear(); for (int i = 0; i <
         * templist.size(); i++) { listModel.addElement(templist.get(i)); } }
         */

        if (!listModel.isEmpty()) {
          list.setSelectedIndex(0);
        }
        bOpen.setEnabled(!listModel.isEmpty() && listModel.get(0) instanceof Viewable);
      } else if (event.getType() == TableModelEvent.INSERT) {
        final List<StructEntry> fields = struct.getFields();
        for (int i = event.getFirstRow(); i <= event.getLastRow(); i++) {
          if (i >= fields.size()) {
            break;
          }
          final StructEntry o = fields.get(i);
          if (o.getClass() == listClass) {
            listModel.addElement(o); // Not sorted properly after this...
            if (!bOpen.isEnabled() && listModel.get(0) instanceof Viewable) {
              bOpen.setEnabled(true);
              list.setSelectedIndex(0);
            }
          }
        }
      }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (e.getSource() == list) {
        final boolean enabled = !list.isSelectionEmpty() && list.getSelectedValue() instanceof Viewable;
        bOpen.setEnabled(enabled);
      }
    }
  }

  /**
   * Can be used to extend ListCellRenderer interfaces by a method that returns the textual representation of the
   * specified cell value.
   */
  public interface ListValueRenderer {
    /** Returns the textual representation of the specified value. */
    String getListValue(Object value);
  }

  public static class StructListRenderer extends DefaultListCellRenderer implements ListValueRenderer {
    /** List of classes containing references to associated graphics resources. */
    private static final List<Class<? extends AbstractStruct>> SUPPORTED_STRUCTURES = Arrays.asList(
        org.infinity.resource.cre.KnownSpells.class,
        org.infinity.resource.sto.Cure.class,
        org.infinity.resource.sto.ItemSale.class,
        org.infinity.resource.sto.ItemSale11.class
    );

    private final AttributeEntry attrEntry;
    private final boolean showIcons;

    public StructListRenderer(AttributeEntry attrEntry) {
      this.attrEntry = attrEntry;
      this.showIcons = BrowserMenuBar.getInstance().getOptions().showResourceListIcons();
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
        boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(getListValue(value));

      if (showIcons && value instanceof AbstractStruct) {
        final Icon icon = loadIcon((AbstractStruct) value);
        if (icon != null) {
          setIcon(icon);
        }
      }

      return this;
    }

    @Override
    public String getListValue(Object value) {
      if (value instanceof StructEntry) {
        final StructEntry entry = attrEntry.apply((StructEntry) value);
        if (entry instanceof ResourceRef) {
          ResourceRef resRef = (ResourceRef) entry;
          return resRef.getSearchName() + " (" + resRef.getResourceName() + ')';
        } else if (entry == null || entry.toString().trim().isEmpty()) {
          return value.toString();
        } else {
          return entry.toString();
        }
      } else if (value != null) {
        return value.toString();
      }
      return "";
    }

    /**
     * Attempts to find and return a graphics associated with the specified {@link AbstractStruct} instance,
     * as {@link Icon}.
     */
    private Icon loadIcon(AbstractStruct struct) {
      Icon retVal = null;

      if (struct != null) {
        ResourceEntry entry = null;
        StructEntry se = null;
        int iconSize = IconCache.getDefaultListIconSize();
        boolean searchExtraDirs = false;

        if (struct instanceof AbstractAbility) {
          // ITM/SPL ability icon
          se = struct.getAttribute(AbstractAbility.ABILITY_ICON);
        } else if (struct instanceof PartyNPC) {
          // (Non-)player character portrait
          StructEntry cre = struct.getAttribute(PartyNPC.GAM_NPC_CRE_RESOURCE);
          if (cre instanceof CreResource) {
            se = ((CreResource) cre).getAttribute(CreResource.CRE_PORTRAIT_SMALL);
            iconSize = IconCache.getDefaultListIconSize() * 2;  // increase to thumbnail size
            searchExtraDirs = true; // bitmaps can be in "Portraits" folder
          }
        } else {
          // Resource-specific icon
          for (final Class<? extends AbstractStruct> as : SUPPORTED_STRUCTURES) {
            if (as.isAssignableFrom(struct.getClass())) {
              se = attrEntry.apply(struct);
              break;
            }
          }
        }

        if (se != null) {
          if (se instanceof ResourceRef) {
            entry = ResourceFactory.getResourceEntry(((ResourceRef) se).getResourceName(), searchExtraDirs);
          }
          retVal = IconCache.get(entry, iconSize);
        }
      }

      return retVal;
    }
  }

  private static final class StructListComparator implements Comparator<AbstractStruct> {
    private final AttributeEntry attrEntry;

    private StructListComparator(AttributeEntry attrEntry) {
      this.attrEntry = attrEntry;
    }

    @Override
    public int compare(AbstractStruct as1, AbstractStruct as2) {
      return attrEntry.apply(as1).toString().compareTo(attrEntry.apply(as2).toString());
    }
  }

  private static final class StructListKeyListener extends KeyAdapter implements ActionListener, ListSelectionListener {
    private static final int TIMER_DELAY = 1000;

    private final Timer timer;
    private final StringBuilder curKey;

    private boolean ignoreReset;

    public StructListKeyListener() {
      curKey = new StringBuilder();
      timer = new Timer(TIMER_DELAY, this);
      timer.setRepeats(false);
      ignoreReset = false;
    }

    @Override
    public void keyPressed(KeyEvent event) {
      if (curKey.length() > 0 && event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
        curKey.delete(curKey.length() - 1, curKey.length());
        if (timer.isRunning()) {
          timer.restart();
        } else {
          timer.start();
        }
        updateSelection(event.getSource());
      }
    }

    @Override
    public void keyTyped(KeyEvent event) {
      if (Character.isISOControl(event.getKeyChar())) {
        return;
      }
      curKey.append(Character.toUpperCase(event.getKeyChar()));
      if (timer.isRunning()) {
        timer.restart();
      } else {
        timer.start();
      }
      updateSelection(event.getSource());
    }

    private void updateSelection(Object source) {
      if (source instanceof JList<?>) {
        JList<?> list = (JList<?>) source;
        ListModel<?> model = list.getModel();
        if (!(list.getCellRenderer() instanceof ListValueRenderer)) {
          return;
        }
        ListValueRenderer renderer = (ListValueRenderer) list.getCellRenderer();
        int startIdx = list.getSelectedIndex();
        if (startIdx < 0) {
          startIdx = 0;
        }
        // start searching from currently selected item
        for (int idx = startIdx, max = model.getSize(); idx < max; idx++) {
          String s = renderer.getListValue(model.getElementAt(idx)).toUpperCase(Locale.ENGLISH);
          if (s.startsWith(curKey.toString())) {
            try {
              ignoreReset = true;
              list.setSelectedIndex(idx);
              list.ensureIndexIsVisible(idx);
            } finally {
              ignoreReset = false;
            }
            return;
          }
        }
        // wrap around if necessary
        for (int idx = 0; idx < startIdx; idx++) {
          String s = renderer.getListValue(model.getElementAt(idx)).toUpperCase(Locale.ENGLISH);
          if (s.startsWith(curKey.toString())) {
            try {
              ignoreReset = true;
              list.setSelectedIndex(idx);
              list.ensureIndexIsVisible(idx);
            } finally {
              ignoreReset = false;
            }
            return;
          }
        }
      }
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      if (!ignoreReset && curKey.length() > 0) {
        curKey.delete(0, curKey.length());
      }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (!ignoreReset && curKey.length() > 0) {
        curKey.delete(0, curKey.length());
      }
    }
  }
}
