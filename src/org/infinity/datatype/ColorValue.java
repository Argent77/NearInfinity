// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewerUtil;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.graphics.GraphicsResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.Misc;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;

/**
 * Field that represents indexed color or color range.
 *
 * <h2>Bean property</h2>
 * When this field is child of {@link AbstractStruct}, then changes of its internal
 * value reported as {@link PropertyChangeEvent}s of the {@link #getParent() parent}
 * struct.
 * <ul>
 * <li>Property name: {@link #getName() name} of this field</li>
 * <li>Property type: {@code int}</li>
 * <li>Value meaning: index of the color in the color table</li>
 * </ul>
 */
public class ColorValue extends Datatype implements Editable, IsNumeric
{
  private static final int DEFAULT_COLOR_WIDTH  = 16;
  private static final int DEFAULT_COLOR_HEIGHT = 24;

  private int number;
  private JList<Image> colorList;
  private ResourceEntry colorEntry; // the source of color ranges
  private IdsMap colorMap;          // provides an optional symbolic color name

  public ColorValue(ByteBuffer buffer, int offset, int length, String name, String bmpFile)
  {
    this(null, buffer, offset, length, name, bmpFile);
  }

  public ColorValue(ByteBuffer buffer, int offset, int length, String name)
  {
    this(null, buffer, offset, length, name, null);
  }

  public ColorValue(StructEntry parent, ByteBuffer buffer, int offset, int length, String name)
  {
    this(parent, buffer, offset, length, name, null);
  }

  public ColorValue(StructEntry parent, ByteBuffer buffer, int offset, int length, String name, String bmpFile)
  {
    super(parent, offset, length, name);
    if (bmpFile != null && ResourceFactory.resourceExists(bmpFile)) {
      this.colorEntry = ResourceFactory.getResourceEntry(bmpFile);
    }
    if (ResourceFactory.resourceExists("CLOWNCLR.IDS")) {
      this.colorMap = IdsMapCache.get("CLOWNCLR.IDS");
    }
    read(buffer, offset);
  }

//--------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(ActionListener container)
  {
    int defaultColorWidth = (colorEntry == null) ? DEFAULT_COLOR_WIDTH : 0;
    colorList = new JList<>(new ColorListModel(defaultColorWidth, DEFAULT_COLOR_HEIGHT, colorEntry));
    colorList.setCellRenderer(new ColorCellRenderer(colorMap));
    colorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    colorList.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    colorList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event)
      {
        if (event.getClickCount() == 2) {
          container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
        }
      }
    });
    colorList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent event)
      {
        if (event.getKeyCode() == KeyEvent.VK_ENTER) {
          container.actionPerformed(new ActionEvent(this, 0, StructViewer.UPDATE_VALUE));
        }
      }
    });
    JScrollPane scroll = new JScrollPane(colorList);
    scroll.setBorder(BorderFactory.createEmptyBorder());

    int selection = Math.min(colorList.getModel().getSize(), Math.max(0, getValue()));
    colorList.setSelectedIndex(selection);

    JButton bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(scroll, gbc);

    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                            GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 0, 0);
    panel.add(bUpdate, gbc);

    Dimension dim = (colorMap != null) ? new Dimension(DIM_MEDIUM.width + 100, DIM_MEDIUM.height) : DIM_MEDIUM;
    panel.setMinimumSize(Misc.getScaledDimension(dim));
    panel.setPreferredSize(Misc.getScaledDimension(dim));

    return panel;
  }

  @Override
  public void select()
  {
    colorList.ensureIndexIsVisible(colorList.getSelectedIndex());
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    if (colorList.getSelectedIndex() >= 0) {
      if (number != colorList.getSelectedIndex()) {
        setValue(colorList.getSelectedIndex());

        // notifying listeners
        fireValueUpdated(new UpdateEvent(this, struct));
      }

      return true;
    }

    return false;
  }

//--------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    writeInt(os, number);
  }

//--------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    switch (getSize()) {
      case 1:
        number = buffer.get() & 0xff;
        break;
      case 2:
        number = buffer.getShort() & 0xffff;
        break;
      case 4:
        number = buffer.getInt() & 0x7fffffff;
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
    return (long)number & 0x7fffffffL;
  }

  @Override
  public int getValue()
  {
    return number;
  }

//--------------------- End Interface IsNumeric ---------------------

  @Override
  public String toString()
  {
    String retVal = "Color index " + Integer.toString(number);
    String name = lookupColorName(colorMap, number, true);
    if (name != null) {
      retVal += " (" + name + ")";
    }
    return retVal;
  }

  /**
   * Returns a symbolic color name based on the specified IDS lookup.
   * Returns {@code null} if no lookup table is available.
   */
  public static String lookupColorName(IdsMap colorMap, int index, boolean prettify)
  {
    String retVal = null;
    if (colorMap != null) {
      IdsMapEntry e = colorMap.get(index);
      if (e != null) {
        retVal = prettify ? Misc.prettifySymbol(e.getSymbol()) : e.getSymbol();
      } else {
        retVal = "Unknown";
      }
    }
    return retVal;
  }

  private void setValue(int newValue)
  {
    final int oldValue = number;
    number = newValue;
    if (oldValue != newValue) {
      firePropertyChange(oldValue, newValue);
    }
  }

//-------------------------- INNER CLASSES --------------------------

  private static final class ColorCellRenderer extends DefaultListCellRenderer
  {
    private static final Border DEFAULT_NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);

    private final IdsMap colorMap;

    public ColorCellRenderer(IdsMap colorMap)
    {
      super();
      this.colorMap = colorMap;
      setVerticalAlignment(SwingConstants.CENTER);
      setHorizontalTextPosition(SwingConstants.RIGHT);
      setVerticalTextPosition(SwingConstants.CENTER);
      setIconTextGap(8);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus)
    {
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      } else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }

      String label = "Index " + index;
      String name = lookupColorName(colorMap, index, true);
      if (name != null) {
        label += " (" + name + ")";
      }
      setText(label);
      if (value instanceof Image) {
        setIcon(new ImageIcon((Image)value));
      } else {
        setIcon(null);
      }

      setEnabled(list.isEnabled());
      setFont(list.getFont());

      Border border = null;
      if (cellHasFocus) {
        if (isSelected) {
          border = UIManager.getBorder("List.focusSelectedCellHighlightBorder");
        }
        if (border == null) {
          border = UIManager.getBorder("List.focusCellHighlightBorder");
        }
      } else {
        border = UIManager.getBorder("List.cellNoFocusBorder");
        if (border == null) {
          border = DEFAULT_NO_FOCUS_BORDER;
        }
      }
      setBorder(border);

      return this;
    }
  }

  private static final class ColorListModel extends AbstractListModel<Image>
  {
    private final HashSet<Integer> randomColors = new HashSet<>();
    private final List<Image> colors = new ArrayList<>(256);

    public ColorListModel(int defaultWidth, int defaultHeight, ResourceEntry colorsEntry)
    {
      initEntries(defaultWidth, defaultHeight, colorsEntry);
    }

    @Override
    public int getSize()
    {
      return colors.size();
    }

    @Override
    public Image getElementAt(int index)
    {
      if (index >= 0 && index < getSize()) {
        return colors.get(index);
      }
      return null;
    }

    private void initEntries(int defaultWidth, int defaultHeight, ResourceEntry colorsEntry)
    {
      if (colorsEntry == null) {
        if (ResourceFactory.resourceExists("RANGES12.BMP")) {
          colorsEntry = ResourceFactory.getResourceEntry("RANGES12.BMP");
        } else if (ResourceFactory.resourceExists("MPALETTE.BMP")) {
          colorsEntry = ResourceFactory.getResourceEntry("MPALETTE.BMP");
        }
      }

      ResourceEntry randomEntry = null;
      if (ResourceFactory.resourceExists("RANDCOLR.2DA")) {
        randomEntry = ResourceFactory.getResourceEntry("RANDCOLR.2DA");
      }

      // adding color gradients
      if (colorsEntry != null) {
        BufferedImage image = null;
        try {
          image = new GraphicsResource(colorsEntry).getImage();
          if (defaultWidth <= 0) {
            // auto-calculate color width
            defaultWidth = 192 / image.getWidth();
          }
          for (int y = 0; y < image.getHeight(); y++) {
            BufferedImage range = new BufferedImage(image.getWidth() * defaultWidth, defaultHeight, image.getType());
            Graphics2D g = range.createGraphics();
            try {
              g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
//              g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
              g.drawImage(image, 0, 0, range.getWidth(), range.getHeight(), 0, y, image.getWidth(), y+1, null);
              g.setColor(Color.BLACK);
              g.setStroke(new BasicStroke(1.0f));
              g.drawRect(0, 0, range.getWidth() - 1, range.getHeight() - 1);
            } finally {
              g.dispose();
            }
            colors.add(range);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      // collecting valid random color indices
      int maxValue = colors.size() - 1;
      if (randomEntry != null) {
        Table2da table = Table2daCache.get(randomEntry);
        for (int col = 1; col < table.getColCount(); col++) {
          String s = table.get(0, col);
          if (s != null) {
            try {
              Integer v = Integer.valueOf(Integer.parseInt(s));
              if (v >= colors.size() && v < 256 && !randomColors.contains(v)) {
                randomColors.add(v);
                maxValue = Math.max(maxValue, v.intValue());
              }
            } catch (NumberFormatException e) {
            }
          }
        }
      }

      // adding random/invalid color placeholder
      maxValue = Math.max(maxValue, 255) + 1;
      Color invalidColor = new Color(0xe0e0e0);
      for (int i = colors.size(); i < maxValue; i++) {
        boolean isRandom = randomColors.contains(Integer.valueOf(i));
        BufferedImage range = new BufferedImage(12 * defaultWidth, defaultHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = range.createGraphics();
        try {
          g.setColor(isRandom ? Color.LIGHT_GRAY : invalidColor);
          g.fillRect(0, 0, range.getWidth(), range.getHeight());
          g.setFont(new JLabel().getFont());
          g.setColor(Color.BLACK);
          g.setStroke(new BasicStroke(1.0f));
          g.drawRect(0, 0, range.getWidth() - 1, range.getHeight() - 1);
          g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
          String msg = isRandom ? "(Random)" : "(Invalid)";
          FontMetrics fm = g.getFontMetrics();
          Rectangle2D rect = fm.getStringBounds(msg, g);
          g.drawString(msg,
                      (float)(range.getWidth() - rect.getWidth()) / 2.0f,
                      (float)(range.getHeight() - rect.getY()) / 2.0f);
        } finally {
          g.dispose();
        }
        colors.add(range);
      }
    }
  }
}
