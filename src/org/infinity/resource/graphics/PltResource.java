// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.infinity.NearInfinity;

import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.WindowBlocker;
import org.infinity.gui.WrapLayout;
import org.infinity.gui.hexview.GenericHexViewer;
import org.infinity.resource.Closeable;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.Writeable;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.StreamUtils;

import tv.porst.jhexview.DataChangedEvent;
import tv.porst.jhexview.IDataChangedListener;

/**
 * This resource describes the appearance of paperdolls displayed on the inventory
 * screen. A paperdoll can display several "materials" (e.g. skin, hair, leather,
 * metal) each of which is represented by a different colour set. The colours for
 * each material are set in the {@link CreResource CRE} file (major and minor colour
 * can be set within the game) which correspond to a colour gradient.
 * <p>
 * Each pixel is mapped to a colour by the colour byte which is then given an
 * intensity by the intensity byte. The colours are listed below:
 * <ul>
 * <li>0 - Skin</li>
 * <li>1 - Hair</li>
 * <li>2 - Metal</li>
 * <li>3 - Leather</li>
 * <li>4 - Metal</li>
 * <li>5 - Minor colour (<i>settable within the game</i>)</li>
 * <li>6 - Major colour (<i>settable within the game</i>)</li>
 * <li>7 - 127 Shadow</li>
 * </ul>
 *
 * Colour maps 128 - 255 repeat this pattern.
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/plt_v1.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/plt_v1.htm</a>
 */
public class PltResource implements Resource, Closeable, Writeable, ItemListener, ActionListener,
                                     ChangeListener, IDataChangedListener
{
  /** Available random colors for initial coloring. */
  private static final int[][] CreColorRandomIndices = {
      { 8, 9, 12, 12, 12, 13, 13, 87, 90, 114 },    // Skin
      { 0, 0, 0, 1, 2, 2, 3, 4, 5, 6 },             // Hair
      { 24, 25, 26, 27, 27, 29, 30, 30, 96, 100 },  // Metal
      { 24, 25, 26, 27, 27, 29, 30, 30, 96, 100 },  // Armor
      { 21, 21, 21, 21, 22, 22, 23, 23, 23, 23 },   // Leather
      { 39, 46, 48, 50, 54, 60, 63, 64, 66, 66 },   // Minor
      { 38, 40, 41, 47, 51, 52, 57, 60, 61, 63 },   // Major
  };
  /** Color types based on CRE field names. */
  private static final String[] ColorIndexNames = {
      CreResource.CRE_COLOR_SKIN, CreResource.CRE_COLOR_HAIR, CreResource.CRE_COLOR_METAL,
      CreResource.CRE_COLOR_ARMOR, CreResource.CRE_COLOR_LEATHER, CreResource.CRE_COLOR_MINOR,
      CreResource.CRE_COLOR_MAJOR };

  private static boolean scalePreview = false;
  private static boolean randomizeColors = true;

  private final ResourceEntry entry;
  private final ByteBuffer buffer;
  private final ButtonPanel buttonPanel = new ButtonPanel();

  private JTabbedPane tabbedPane;
  private GenericHexViewer hexViewer;
  private JComboBox<ColorItem>[] cbColors;
  private JCheckBox cbScalePreview, cbRandomize;
  private RenderCanvas rcCanvas;
  private BufferedImage image;
  private JPanel panelMain, panelRaw;
  private JMenuItem miExport, miExportPNG;

  public PltResource(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
    this.buffer = (this.entry != null) ? this.entry.getResourceBuffer() : null;
    if (this.buffer != null) {
      this.buffer.order(ByteOrder.LITTLE_ENDIAN);
      if (!"PLT V1  ".equals(StreamUtils.readString(buffer, 8))) {
        throw new Exception("Invalid file signature");
      }
      int w = buffer.getInt(0x10);
      int h = buffer.getInt(0x14);
      if (w < 0 || h < 0 || buffer.limit() < 0x18 + w*h*2) {
        throw new Exception("Invalid header data");
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    // creating "View" tab
    JPanel pSelections = new JPanel(new WrapLayout(FlowLayout.CENTER, 16, 8));
    cbColors = new JComboBox[ColorIndexNames.length];
    Random rnd = new Random();
    for (int i = 0; i < ColorIndexNames.length; ++i) {
      cbColors[i] = new JComboBox<>(new ColorModel());
      cbColors[i].setRenderer(new ColorRenderer());
      if (randomizeColors) {
        cbColors[i].setSelectedIndex(CreColorRandomIndices[i][rnd.nextInt(CreColorRandomIndices[i].length)]);
      } else {
        cbColors[i].setSelectedIndex(0);
      }
      cbColors[i].addItemListener(this);
      JPanel p = new JPanel(new BorderLayout());
      JLabel l = new JLabel(ColorIndexNames[i] + ":");
      p.add(l, BorderLayout.NORTH);
      p.add(cbColors[i], BorderLayout.CENTER);
      pSelections.add(p);
    }

    JPanel pCanvas = new JPanel(new BorderLayout());
    JPanel pOptions = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
    cbScalePreview = new JCheckBox("Enable zoom", scalePreview);
    cbScalePreview.setHorizontalAlignment(SwingConstants.CENTER);
    cbScalePreview.addActionListener(this);
    pOptions.add(cbScalePreview);
    cbRandomize = new JCheckBox("Randomize colors", randomizeColors);
    cbRandomize.setToolTipText("Randomize colors when loading resource.");
    cbRandomize.setHorizontalAlignment(SwingConstants.CENTER);
    cbRandomize.addActionListener(this);
    pOptions.add(cbRandomize);
    pCanvas.add(pOptions, BorderLayout.SOUTH);

    rcCanvas = new RenderCanvas(updateImage());
    rcCanvas.setAutoScaleEnabled(false);
    rcCanvas.setScaleFactor(2.0f);
    rcCanvas.setScalingEnabled(scalePreview);
    JScrollPane scroll = new JScrollPane(rcCanvas);
    pCanvas.add(scroll, BorderLayout.CENTER);

    JPanel subPanel = new JPanel(new BorderLayout());
    subPanel.add(pCanvas, BorderLayout.CENTER);
    subPanel.add(pSelections, BorderLayout.SOUTH);
    subPanel.setBorder(BorderFactory.createLoweredBevelBorder());

    JPanel pView = new JPanel(new BorderLayout());
    pView.add(subPanel, BorderLayout.CENTER);

    // creating "Raw" tab (stub)
    panelRaw = new JPanel(new BorderLayout());
    hexViewer = null;

    // creating main panel
    ButtonPopupMenu bpmExport = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.EXPORT_MENU);
    miExport = new JMenuItem("original");
    miExport.addActionListener(this);
    miExportPNG = new JMenuItem("as PNG");
    miExportPNG.addActionListener(this);
    bpmExport.setMenuItems(new JMenuItem[]{miExport, miExportPNG}, false);

    ((JButton)buttonPanel.addControl(ButtonPanel.Control.SAVE)).addActionListener(this);
    buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(false);
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

    tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    tabbedPane.setBorder(BorderFactory.createEmptyBorder());
    tabbedPane.addTab("View", pView);
    tabbedPane.addTab("Raw", panelRaw);
    tabbedPane.setSelectedIndex(0);
    tabbedPane.addChangeListener(this);

    panelMain = new JPanel(new BorderLayout());
    panelMain.add(tabbedPane, BorderLayout.CENTER);
    panelMain.add(buttonPanel, BorderLayout.SOUTH);

    return panelMain;
  }

  @Override
  public ResourceEntry getResourceEntry()
  {
    return entry;
  }

//--------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception
  {
    if (isRawModified()) {
      ResourceFactory.closeResource(this, entry, panelMain);
    }
  }

//--------------------- End Interface Closeable ---------------------

//--------------------- Begin Interface Writeable ---------------------

 @Override
 public void write(OutputStream os) throws IOException
 {
   StreamUtils.writeBytes(os, hexViewer.getData());
 }

//--------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == cbScalePreview) {
      scalePreview = cbScalePreview.isSelected();
      rcCanvas.setScalingEnabled(scalePreview);
    } else if (e.getSource() == cbRandomize) {
      randomizeColors = cbRandomize.isSelected();
    } else if (e.getSource() == miExport) {
      ResourceFactory.exportResource(entry, panelMain.getTopLevelAncestor());
    } else if (e.getSource() == miExportPNG) {
      try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        final String fileName = StreamUtils.replaceFileExtension(entry.getResourceName(), "PNG");
        boolean bRet = false;
        WindowBlocker.blockWindow(true);
        try {
          bRet = ImageIO.write(image, "png", os);
        } finally {
          WindowBlocker.blockWindow(false);
        }
        if (bRet) {
          ResourceFactory.exportResource(entry, StreamUtils.getByteBuffer(os.toByteArray()),
                                         fileName, panelMain.getTopLevelAncestor());
        } else {
          throw new UnsupportedOperationException("PNG writing is not supported");
        }
      } catch (Exception ioe) {
        ioe.printStackTrace();
        JOptionPane.showMessageDialog(panelMain.getTopLevelAncestor(),
                                      "Error while exporting " + entry, "Error",
                                      JOptionPane.ERROR_MESSAGE);
      }
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.SAVE) == e.getSource()) {
      if (ResourceFactory.saveResource(this, panelMain.getTopLevelAncestor())) {
        setRawModified(false);
      }
    }
  }

//--------------------- End Interface ActionListener ---------------------

//--------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent e)
  {
    if (e.getSource() instanceof JComboBox<?>) {
      @SuppressWarnings("unchecked")
      JComboBox<ColorItem> cb = (JComboBox<ColorItem>)e.getSource();
      ColorItem item = cb.getModel().getElementAt(cb.getSelectedIndex());
      if (item != null && item.isRandomColor()) {
        item.randomize();
      }
      updateImage();
      rcCanvas.repaint();
    }
  }

//--------------------- End Interface ItemListener ---------------------

//--------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent event)
  {
    if (event.getSource() == tabbedPane) {
      if (tabbedPane.getSelectedComponent() == panelRaw) {
        // lazy initialization of hex viewer
        if (hexViewer == null) {
          // confirm action when opening first time
          int ret = JOptionPane.showConfirmDialog(panelMain,
                                                  "Editing PLT resources directly may result in corrupt data. " +
                                                      "Open hex editor?",
                                                      "Warning", JOptionPane.YES_NO_OPTION,
                                                      JOptionPane.WARNING_MESSAGE);
          if (ret == JOptionPane.YES_OPTION) {
            try {
              WindowBlocker.blockWindow(true);
              hexViewer = new GenericHexViewer(entry);
              hexViewer.addDataChangedListener(this);
              hexViewer.setCurrentOffset(0L);
              panelRaw.add(hexViewer, BorderLayout.CENTER);
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              WindowBlocker.blockWindow(false);
            }
          } else {
            tabbedPane.setSelectedIndex(0);
            return;
          }
        }
        hexViewer.requestFocusInWindow();
      }
    }
  }

//--------------------- End Interface ChangeListener ---------------------

//--------------------- Begin Interface IDataChangedListener ---------------------

  @Override
  public void dataChanged(DataChangedEvent event)
  {
    setRawModified(true);
  }

//--------------------- End Interface IDataChangedListener ---------------------

  public int getImageWidth()
  {
    return (buffer != null) ? buffer.getInt(0x10) : 0;
  }

  public int getImageHeight()
  {
    return (buffer != null) ? buffer.getInt(0x14) : 0;
  }


  public String getColorLabel(int index)
  {
    if (index >= 0 && index < ColorIndexNames.length) {
      return ColorIndexNames[index] + " Color:";
    } else {
      return ColorIndexNames[ColorIndexNames.length - 1] + " Color:";
    }
  }

  private ColorItem getSelectedColorItem(int type)
  {
    if (type < 0) type = 0;
    if (type >= cbColors.length) type = cbColors.length - 1;
    return getColorItem(type, cbColors[type].getSelectedIndex());
  }

  private ColorItem getColorItem(int type, int index)
  {
    if (type < 0) type = 0;
    if (type >= cbColors.length) type = cbColors.length - 1;
    if (index < 0) index = 0;
    if (index >= cbColors[type].getModel().getSize()) {
      index = cbColors[type].getModel().getSize() - 1;
    }
    return cbColors[type].getModel().getElementAt(index);
  }

  private BufferedImage updateImage()
  {
    if (image == null) {
      image = new BufferedImage(getImageWidth(), getImageHeight(), BufferedImage.TYPE_INT_ARGB);
    }
    if (buffer != null) {
      int[] data = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
      buffer.position(0x18);
      for (int y = image.getHeight() - 1; y >= 0; --y) {
        int idx = y * image.getWidth();
        for (int x = 0, xmax = image.getWidth(); x < xmax; ++x, ++idx) {
          int v = buffer.getShort();
          int index = v & 0xff;
          int type = (v >> 8) & 0x7f;   // ignore bit 8
          int color = 0;
          if (type < cbColors.length) {
            // color
            color = getSelectedColorItem(type).getColorAt(index);
          } else {
            // shadow
            color = 0x80000000;
          }
          data[idx] = color;
        }
      }
    }
    return image;
  }

  private boolean isRawModified()
  {
    if (hexViewer != null) {
      return hexViewer.isModified();
    } else {
      return false;
    }
  }

  private void setRawModified(boolean modified)
  {
    if (hexViewer != null) {
      if (!modified) {
        hexViewer.clearModified();
      }
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(modified);
    }
  }

//--------------------------- INNER CLASSES ---------------------------

  /** Stores color index and value. */
  private static class ColorItem
  {
    /** Color index. */
    private final int index;
    private final Random rand = new Random();

    /** Full range of color entries as ARGB values ({@code 0xaarrggbb}). */
    private int[] range;
    /** Lookup for squared distances. */
    private int[] squareDist;
    /** Initialized if item refers to a random color. */
    private ColorItem[] randItems;
    private int randIndex;

    public ColorItem(int index, int[] range)
    {
      this(index, range, null);
    }

    public ColorItem(int index, int[] range, ColorItem[] randItems)
    {
      this.index = index;
      init(range, randItems);
      initDistance();
    }

    public int getIndex()
    {
      return index;
    }

    public boolean isRandomColor()
    {
      return (randItems[randIndex] != this);
    }

    public void randomize()
    {
      if (randItems[randIndex] != this) {
        randIndex = rand.nextInt(randItems.length);
      }
    }

    public int getColorValue()
    {
      if (randItems[randIndex] != this) {
        return randItems[randIndex].getColorValue();
      } else {
        return range[range.length / 2];
      }
    }

    public int[] getColorRange()
    {
      if (randItems[randIndex] != this) {
        return randItems[randIndex].range;
      } else {
        return range;
      }
    }

    public int getColorAt(int index)
    {
      int[] colorRange = getColorRange();
      if (index < 0) index = 0;
      if (index >= colorRange.length) index = colorRange.length - 1;
      return colorRange[index];
    }

//    public int setColorAt(int index, int color)
//    {
//      int[] colorRange = getColorRange();
//      if (index < 0) index = 0;
//      if (index >= colorRange.length) index = colorRange.length - 1;
//      int retVal = colorRange[index];
//      colorRange[index] = color;
//      return retVal;
//    }

    public int getDistance(int squared)
    {
      // handling special case first
      if (squared <= squareDist[0]) {
        return 0;
      }

      // apply binary search
      int min = 0, max = squareDist.length - 1;
      while (min < max) {
        int mid = (min + max) / 2;
        if (squareDist[mid] > squared) {
          max = mid;
        } else {
          min = mid + 1;
        }
      }

      // final adjustments
      if (squareDist[min] != squared) {
        int v1 = squared - squareDist[min];
        if (min > 0) {
          int v2 = squared - squareDist[min-1];
          if (v2*v2 < v1*v1) {
            --min;
          }
        }
        if (min < squareDist.length - 1) {
          int v2 = squared - squareDist[min+1];
          if (v2*v2 < v1*v1) {
            ++min;
          }
        }
      }

      return min;
    }

    @Override
    public String toString()
    {
      int value = getColorValue();
      String retVal = "Index=" + index + ", ";
      if (value != 0) {
        retVal += "RGB(" + (value & 0xff) + "," + ((value >> 8) & 0xff) + "," + ((value >> 16) & 0xff) + ")";
      } else {
        retVal += "transparent";
      }
      return retVal;
    }

    private void init(int[] range, ColorItem[] randItems)
    {
      this.range = range;
      if (randItems != null) {
        this.randItems = randItems;
      } else {
        this.randItems = new ColorItem[]{this};
      }
    }

    private void initDistance()
    {
      if (this.range != null) {
        squareDist = new int[range.length];
        for (int i = 0; i < squareDist.length; ++i) {
          squareDist[i] = i*i;
        }
      }
    }
  }


  private static class ColorModel extends AbstractListModel<ColorItem> implements ComboBoxModel<ColorItem>
  {
    public static final String PAL_RESOURCE       = "MPAL256.BMP";
    public static final String RANDOM_COLOR_TABLE = "RANDCOLR.2DA";

    private ColorItem[] items;
    private ColorItem selectedItem;

    public ColorModel()
    {
      this.selectedItem = null;
      init();
    }

    @Override
    public int getSize()
    {
      return items.length;
    }

    @Override
    public ColorItem getElementAt(int index)
    {
      return (index >= 0 && index < items.length) ? items[index] : null;
    }

    @Override
    public void setSelectedItem(Object anItem)
    {
      if (anItem instanceof ColorItem) {
        this.selectedItem = (ColorItem)anItem;
      }
    }

    @Override
    public Object getSelectedItem()
    {
      return selectedItem;
    }

    private void init()
    {
      ResourceEntry entry = ResourceFactory.getResourceEntry(PAL_RESOURCE);
      if (entry != null) {
        try {
          BufferedImage image = ImageIO.read(entry.getResourceDataAsStream());

          // preparing fixed color ranges
          int[] buffer = null;
          if (image.getRaster().getDataBuffer() instanceof DataBufferByte) {
            int bytesPerPixel = image.getColorModel().getPixelSize() / 8;
            byte[] srcBuffer = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
            buffer = new int[srcBuffer.length / bytesPerPixel];
            int srcOfs = 0, dstOfs = 0;
            while (srcOfs < srcBuffer.length) {
              int pixel = 0;
              for (int idx = 0; idx < bytesPerPixel; ++idx) {
                pixel <<= 8;
                pixel |= srcBuffer[srcOfs + bytesPerPixel - idx - 1] & 0xff;
              }
              srcOfs += bytesPerPixel;
              buffer[dstOfs] = pixel;
              ++dstOfs;
            }
          } else if (image.getRaster().getDataBuffer() instanceof DataBufferInt) {
            buffer = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
          }

          int numItems = image.getHeight();

          // preparing random color entries
          HashSet<Integer> randomIndices = new HashSet<>();
          Table2da table = Table2daCache.get(RANDOM_COLOR_TABLE);
          if (table != null) {
            int numCols = table.getColCount();
            int numRows = table.getRowCount();
            if (numRows > 1) {
              // don't count indices that are already reserved by fixed color ranges
              for (int c = 1; c < numCols; ++c) {
                String s = table.get(0, c);
                try {
                  int v = Integer.parseInt(s);
                  if (v >= numItems) {
                    randomIndices.add(Integer.valueOf(v));
                  }
                } catch (NumberFormatException nfe) {
                }
              }
            }
          }

          items = new ColorItem[numItems+randomIndices.size()];

          // adding fixed color ranges
          for (int itemIdx = 0; itemIdx < numItems; ++itemIdx) {
            int startOfs = itemIdx * image.getWidth();
            int[] range = new int[image.getWidth()];
            for (int idx = 0; idx < range.length; ++idx) {
              range[idx] = buffer[startOfs + idx] | 0xff000000;  // alpha component required
              if ((range[idx] & 0xffffff) == 0x00ff00) {         // RGB(0,255,0) = transparent
                range[idx] = 0;
              }
            }
            items[itemIdx] = new ColorItem(itemIdx, range);
          }

          // adding random color entries
          if (!randomIndices.isEmpty()) {
            int numCols = table.getColCount();
            int numRows = table.getRowCount();
            int idx = 0;
            for (int col = 1; col < numCols; ++col) {
              String s = table.get(0, col);
              int v = -1;
              try { v = Integer.parseInt(s); } catch (NumberFormatException nfe) {}
              if (randomIndices.contains(Integer.valueOf(v))) {
                int index = v;
                ColorItem[] randItems = new ColorItem[numRows - 1];
                for (int row = 1; row < numRows; ++row) {
                  s = table.get(row, col);
                  v = -1;
                  try { v = Integer.parseInt(s); } catch (NumberFormatException nfe) {}
                  if (v < 0 || v >= numItems) v = 0;
                  randItems[row - 1] = items[v];
                }
                items[numItems+idx] = new ColorItem(index, null, randItems);
                idx++;
              }
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      if (items == null) {
        items = new ColorItem[1];
        items[0] = new ColorItem(0, new int[]{0});
      }
    }
  }


  private static class ColorRenderer extends JLabel implements ListCellRenderer<ColorItem>
  {
    private static final int ICON_WIDTH = 33;
    private static final int ICON_HEIGHT = 33;

    private final ImageIcon icon;
    private final HashMap<Integer, int[]> iconIndexMap = new HashMap<>(350);

    public ColorRenderer()
    {
      super();
      setOpaque(true);
      this.icon = new ImageIcon(new BufferedImage(ICON_WIDTH, ICON_HEIGHT, BufferedImage.TYPE_INT_ARGB));
      setText("Index " + 255);
      setIcon(icon);
      setHorizontalAlignment(SwingConstants.LEADING);
      setHorizontalTextPosition(SwingConstants.TRAILING);
      setVerticalAlignment(SwingConstants.CENTER);
      setVerticalTextPosition(SwingConstants.CENTER);
      setIconTextGap(8);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ColorItem> list, ColorItem value,
                                                  int index, boolean isSelected, boolean cellHasFocus)
    {
      if (value != null) {
        setText("Index " + value.getIndex());
        icon.setImage(getColorCircle(value));
      }
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      } else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }
      return this;
    }

    private BufferedImage getColorCircle(ColorItem item)
    {
      BufferedImage image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT, BufferedImage.TYPE_INT_ARGB);
      if (item.isRandomColor()) {
        // random color entry
        final String msg = "?";
        Graphics2D g2 = (Graphics2D)image.getGraphics();
        JLabel l = new JLabel();
        g2.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 3.0f));
        g2.setColor(Color.BLACK);
        int x = (image.getWidth() - g2.getFontMetrics().stringWidth(msg)) / 2;
        int y = ((image.getHeight() - g2.getFontMetrics().getHeight()) / 2) + g2.getFontMetrics().getAscent();
        g2.drawString(msg, x, y);
      } else {
        // fixed color range
        int[] indices = iconIndexMap.get(Integer.valueOf(item.getIndex()));
        if (indices == null) {
          indices = new int[ICON_WIDTH*ICON_HEIGHT];
          int maxDist = (ICON_WIDTH - 1) / 2;
          int scale = (item.getColorRange().length - 1) / maxDist;
          scale = scale * 4 / 3; // scale to 75%
          final int cx = 16, cy = 16;
          int[] range = item.getColorRange();
          for (int y = 0; y < ICON_HEIGHT; ++y) {
            for (int x = 0; x < ICON_WIDTH; ++x) {
              int dx = (cx - x) * (cx - x);
              int dy = (cy - y) * (cy - y);
              int index = item.getDistance(dx + dy) * scale;
              if (index > 0) {
                index = (index < range.length - 1) ? range.length - index - 1 : range.length - 1;
              } else {
                index = range.length - 2;
              }
              indices[y*ICON_WIDTH + x] = index;
            }
          }
          iconIndexMap.put(Integer.valueOf(item.getIndex()), indices);
        }

        int[] buffer = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        int[] range = item.getColorRange();
        for (int ofs = 0; ofs <indices.length; ++ofs) {
          buffer[ofs] = range[indices[ofs]];
        }
      }

      return image;
    }
  }
}
