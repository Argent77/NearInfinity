// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.NearInfinity;
import org.infinity.datatype.IsNumeric;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.TileGrid;
import org.infinity.gui.ViewFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Referenceable;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.graphics.TisConvert.Status;
import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.wed.IndexNumber;
import org.infinity.resource.wed.Overlay;
import org.infinity.resource.wed.Tilemap;
import org.infinity.resource.wed.WedResource;
import org.infinity.search.ReferenceSearcher;
import org.infinity.util.DataString;
import org.infinity.util.DebugTimer;
import org.infinity.util.Logger;
import org.infinity.util.io.FileEx;
import org.infinity.util.tuples.Couple;

/**
 * This resource describes a tileset. There are currently two variants available:
 * <ol>
 * <li><b>Palette-based TIS</b>
 * <p>
 * TIS files are generally comprised of a large number of tiles, each of which consists of a palette and a rectangular
 * block of pixels. Each pixel is an index into the associated palette. Each tile has its own palette and a block of
 * pixels. The pixel data is not compressed.
 * <p>
 * Each tile consists of a 256 colour palette, with each entry being an RGBA value stored in BGRA order (note that the
 * Alpha value is unused), followed by 8-bit pixel values, which are indices into the palette. The pixel values are row
 * by row, from left to right and top to bottom. Index 0 is hardcoded to be the transparent index.</li>
 * <li><b>PVRZ-based TIS</b>
 * <p>
 * This variant is only supported by {@link Profile.Engine#EE Enhanced Edition} games. Each tile definition refers to a
 * block of pixels within an associated {@link PvrzResource PVRZ} file.
 * <p>
 * Each tile consists of a block of pixels that is defined in an associated PVRZ file.</li>
 * </ol>
 * TIS files contain only the graphics for an area - the location information is stored in a {@link WedResource WED}
 * file.
 * <p>
 * Engine specific notes:
 * <ul>
 * <li>PST can only load TIS files when they are stored in a {@link BIFFResourceEntry BIFF} file.</li>
 * <li>Palette-based TIS induces a noticeable performance hit and occasional visual glitches when used in
 * {@link Profile.Engine#EE Enhanced Edition} games. It is highly recommended to use PVRZ-based TIS instead.</li>
 * </ul>
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/tis_v1.htm">
 *      https://gibberlings3.github.io/iesdp/file_formats/ie_formats/tis_v1.htm</a>
 */
public class TisResource implements Resource, Closeable, Referenceable, ActionListener, ChangeListener, ItemListener,
    KeyListener, PropertyChangeListener {
  private static final int DEFAULT_COLUMNS = 5;

  private static final String FMT_TILEINFO_SHOW = "Tile %d: Show PVRZ information...";
  private static final String FMT_TILEINFO_PVRZ = "Tile %d: Open PVRZ resource...";
  private static final String FMT_TILEINFO_WED = "Tile %d: Open WED overlay tilemap... ";

  private static boolean showGrid = false;

  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();
  private final JPopupMenu menuTileInfo = new JPopupMenu();
  private final JMenuItem miTileInfoShow = new JMenuItem();
  private final JMenuItem miTileInfoPvrz = new JMenuItem();
  private final JMenuItem miTileInfoWed = new JMenuItem();
  private final List<SwingWorker<Status, Void>> workers = new ArrayList<>();

  private WedResource wedResource;
  private HashMap<Integer, Tilemap> wedTileMap;
  private TisDecoder decoder;
  private List<Image> tileImages; // stores one tile per image
  private TileGrid tileGrid;      // the main component for displaying the tileset
  private JSlider slCols;         // changes the tiles per row
  private JTextField tfCols;      // input/output tiles per row
  private JCheckBox cbGrid;       // show/hide frame around each tile
  private JMenuItem miExport;
  private JMenuItem miExportPaletteTis;
  private JMenuItem miExportPvrzTis;
  private JMenuItem miExportPNG;
  private JPanel panel; // top-level panel of the viewer
  private RootPaneContainer rpc;
  private WindowBlocker blocker;
  private int defaultWidth;
  private int lastTileInfoIndex = -1;

  public TisResource(ResourceEntry entry) throws Exception {
    this.entry = entry;
    initTileset();
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES)) {
      searchReferences(panel.getTopLevelAncestor());
    } else if (event.getSource() == miExport) {
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    } else if (event.getSource() == miExportPaletteTis) {
      try {
        final TisConvert.Config config = ConvertTisDialog.show(panel.getTopLevelAncestor(), this);
        if (config != null) {
          performBackgroundTask(() -> {
            DebugTimer.getInstance().timerReset();
            Status status = TisConvert.convertToPaletteTis(config, true, panel.getTopLevelAncestor());
            Logger.info(DebugTimer.getInstance().getTimerFormatted("TIS conversion completed"));
            return status;
          });
        }
      } catch (Exception e) {
        Logger.error(e);
        JOptionPane.showMessageDialog(panel.getTopLevelAncestor(), "Tileset conversion: " + e.getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    } else if (event.getSource() == miExportPvrzTis) {
      try {
        final TisConvert.Config config = ConvertTisDialog.show(panel.getTopLevelAncestor(), this);
        if (config != null) {
          performBackgroundTask(() -> {
            DebugTimer.getInstance().timerReset();
            Status status = TisConvert.convertToPvrzTis(config, true, panel.getTopLevelAncestor());
            Logger.info(DebugTimer.getInstance().getTimerFormatted("TIS conversion completed"));
            return status;
          });
        }
      } catch (Exception e) {
        Logger.error(e);
        JOptionPane.showMessageDialog(panel.getTopLevelAncestor(), "Tileset conversion: " + e.getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    } else if (event.getSource() == miExportPNG) {
      final Path pngFile = getPngFileName(panel.getTopLevelAncestor());
      if (pngFile != null) {
        performBackgroundTask(() -> TisConvert.exportPNG(tileImages, tileGrid.getTileColumns(), pngFile, true,
            panel.getTopLevelAncestor()));
      }
    } else if (event.getSource() == miTileInfoShow) {
      if (!showPvrzInfo(lastTileInfoIndex)) {
        JOptionPane.showMessageDialog(panel,
            String.format("Could not retrieve PVRZ information for tile %d.", lastTileInfoIndex), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    } else if (event.getSource() == miTileInfoPvrz) {
      if (!openPvrzResource(lastTileInfoIndex)) {
        JOptionPane.showMessageDialog(panel,
            String.format("Could not open PVRZ resource for tile %d.", lastTileInfoIndex), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    } else if (event.getSource() == miTileInfoWed) {
      final Tilemap tm = wedTileMap.get(lastTileInfoIndex);
      if (!openStructEntry(tm)) {
        JOptionPane.showMessageDialog(panel,
            String.format("Could not open overlay structure for tile %d.", lastTileInfoIndex), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent event) {
    if (event.getSource() == slCols) {
      int cols = slCols.getValue();
      tfCols.setText(Integer.toString(cols));
      tileGrid.setGridSize(calcGridSize(tileGrid.getImageCount(), cols));
    }
  }

  // --------------------- End Interface ChangeListener ---------------------

  // --------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent event) {
    if (event.getSource() == cbGrid) {
      showGrid = cbGrid.isSelected();
      tileGrid.setShowGrid(showGrid);
    }
  }

  // --------------------- End Interface ChangeListener ---------------------

  // --------------------- Begin Interface KeyListener ---------------------

  @Override
  public void keyPressed(KeyEvent event) {
    if (event.getSource() == tfCols) {
      if (event.getKeyCode() == KeyEvent.VK_ENTER) {
        int cols;
        try {
          cols = Integer.parseInt(tfCols.getText());
        } catch (NumberFormatException e) {
          cols = slCols.getValue();
          tfCols.setText(Integer.toString(slCols.getValue()));
        }
        if (cols != slCols.getValue()) {
          if (cols <= 0) {
            cols = 1;
          }
          if (cols >= decoder.getTileCount()) {
            cols = decoder.getTileCount();
          }
          slCols.setValue(cols);
          tfCols.setText(Integer.toString(slCols.getValue()));
          tileGrid.setGridSize(calcGridSize(tileGrid.getImageCount(), cols));
        }
        slCols.requestFocus(); // remove focus from textfield
      }
    }
  }

  @Override
  public void keyReleased(KeyEvent event) {
    // nothing to do
  }

  @Override
  public void keyTyped(KeyEvent event) {
    // nothing to do
  }

  // --------------------- End Interface KeyListener ---------------------

  // --------------------- Begin Interface PropertyChangeListener ---------------------

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (event.getSource() instanceof SwingWorker<?, ?>) {
      @SuppressWarnings("unchecked")
      SwingWorker<Status, Void> worker = (SwingWorker<Status, Void>) event.getSource();
      workers.remove(worker);
      if ("state".equals(event.getPropertyName()) && SwingWorker.StateValue.DONE == event.getNewValue()) {
        if (blocker != null) {
          blocker.setBlocked(false);
          blocker = null;
        }
        Status retVal = Status.ERROR;
        try {
          retVal = worker.get();
          if (retVal == null) {
            retVal = Status.ERROR;
          }
        } catch (Exception e) {
          Logger.error(e);
        }

        if (retVal == Status.SUCCESS) {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(), "File exported successfully.", "Export complete",
              JOptionPane.INFORMATION_MESSAGE);
        } else if (retVal == Status.CANCELLED) {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(), "Export has been cancelled.", "Information",
              JOptionPane.INFORMATION_MESSAGE);
        } else if (retVal == Status.UNSUPPORTED) {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(), "Operation not (yet) supported.", "Information",
              JOptionPane.INFORMATION_MESSAGE);
        } else {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(), "Error while exporting " + entry, "Error",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  // --------------------- End Interface PropertyChangeListener ---------------------

  // --------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception {
    while (!workers.isEmpty()) {
      SwingWorker<Status, Void> worker = workers.remove(0);
      if (worker != null && !worker.isDone()) {
        worker.cancel(true);
      }
      worker = null;
    }

    if (tileImages != null) {
      tileImages.clear();
      tileImages = null;
    }

    if (tileGrid != null) {
      tileGrid.clearImages();
      tileGrid = null;
    }

    if (decoder != null) {
      decoder.close();
      decoder = null;
    }

    System.gc();
  }

  // --------------------- End Interface Closeable ---------------------

  // --------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry() {
    return entry;
  }

  // --------------------- End Interface Resource ---------------------

  // --------------------- Begin Interface Referenceable ---------------------

  @Override
  public boolean isReferenceable() {
    return true;
  }

  @Override
  public void searchReferences(Component parent) {
    new ReferenceSearcher(entry, parent);
  }

  // --------------------- End Interface Referenceable ---------------------

  // --------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container) {
    if (container instanceof RootPaneContainer) {
      rpc = (RootPaneContainer) container;
    } else {
      rpc = NearInfinity.getInstance();
    }

    if (decoder == null) {
      return new JPanel(new BorderLayout());
    }

    int tileCount = decoder.getTileCount();
    int defaultColumns = Math.min(tileCount, DEFAULT_COLUMNS);

    // 1. creating top panel
    // 1.1. creating label with text field
    JLabel lblTPR = new JLabel("Tiles per row:");
    tfCols = new JTextField(Integer.toString(defaultColumns), 5);
    tfCols.addKeyListener(this);
    JPanel tPanel1 = new JPanel(new FlowLayout(FlowLayout.CENTER));
    tPanel1.add(lblTPR);
    tPanel1.add(tfCols);

    // 1.2. creating slider
    slCols = new JSlider(SwingConstants.HORIZONTAL, 1, tileCount, defaultColumns);
    if (tileCount > 1000) {
      slCols.setMinorTickSpacing(100);
      slCols.setMajorTickSpacing(1000);
    } else if (tileCount > 100) {
      slCols.setMinorTickSpacing(10);
      slCols.setMajorTickSpacing(100);
    } else {
      slCols.setMinorTickSpacing(1);
      slCols.setMajorTickSpacing(10);
    }
    slCols.setPaintTicks(true);
    slCols.addChangeListener(this);

    // 1.3. adding left side of the top panel together
    JPanel tlPanel = new JPanel(new GridLayout(2, 1));
    tlPanel.add(tPanel1);
    tlPanel.add(slCols);

    // 1.4. configuring checkbox
    cbGrid = new JCheckBox("Show Grid", showGrid);
    cbGrid.addItemListener(this);
    JPanel trPanel = new JPanel(new GridLayout());
    trPanel.add(cbGrid);

    // 1.5. putting top panel together
    BorderLayout bl = new BorderLayout();
    JPanel topPanel = new JPanel(bl);
    topPanel.add(tlPanel, BorderLayout.CENTER);
    topPanel.add(trPanel, BorderLayout.LINE_END);

    // 2. creating main panel
    // 2.1. creating tiles table and scroll pane
    tileGrid = new TileGrid(1, defaultColumns, decoder.getTileWidth(), decoder.getTileHeight());
    tileGrid.addImage(tileImages);
    tileGrid.setGridSize(calcGridSize(tileGrid.getImageCount(), getDefaultTilesPerRow()));
    tileGrid.setShowGrid(showGrid);

    menuTileInfo.add(miTileInfoShow);
    menuTileInfo.add(miTileInfoPvrz);
    menuTileInfo.add(miTileInfoWed);
    miTileInfoShow.addActionListener(this);
    miTileInfoPvrz.addActionListener(this);
    miTileInfoWed.addActionListener(this);
    tileGrid.addMouseListener(new PopupListener());

    slCols.setValue(tileGrid.getTileColumns());
    tfCols.setText(Integer.toString(tileGrid.getTileColumns()));
    JScrollPane scroll = new JScrollPane(tileGrid);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.getHorizontalScrollBar().setUnitIncrement(16);

    // 2.2. putting main panel together
    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(scroll, BorderLayout.CENTER);

    // 3. creating bottom panel
    // 3.1. creating export button
    miExport = new JMenuItem("original");
    miExport.addActionListener(this);
    if (decoder.getType() == TisDecoder.Type.PVRZ) {
      miExportPaletteTis = new JMenuItem("as palette-based TIS...");
      miExportPaletteTis.addActionListener(this);
    } else if (decoder.getType() == TisDecoder.Type.PALETTE) {
      miExportPvrzTis = new JMenuItem("as PVRZ-based TIS...");
      miExportPvrzTis.addActionListener(this);
    }
    miExportPNG = new JMenuItem("as PNG");
    miExportPNG.addActionListener(this);

    List<JMenuItem> list = new ArrayList<>();
    if (miExportPNG != null) {
      list.add(miExportPNG);
    }
    if (miExportPaletteTis != null) {
      list.add(miExportPaletteTis);
    }
    if (miExportPvrzTis != null) {
      list.add(miExportPvrzTis);
    }
    if (miExport != null) {
      list.add(miExport);
    }
    JMenuItem[] mi = new JMenuItem[list.size()];
    for (int i = 0; i < mi.length; i++) {
      mi[i] = list.get(i);
    }
    ((JButton) buttonPanel.addControl(ButtonPanel.Control.FIND_REFERENCES)).addActionListener(this);
    ButtonPopupMenu bpmExport = (ButtonPopupMenu) buttonPanel.addControl(ButtonPanel.Control.EXPORT_MENU);
    bpmExport.setMenuItems(mi, false);

    // 4. packing all together
    panel = new JPanel(new BorderLayout());
    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(centerPanel, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);
    centerPanel.setBorder(BorderFactory.createLoweredBevelBorder());

    buttonPanel.addControl(0, ViewerUtil.createViewerSyncButton(panel, getResourceEntry()), ButtonPanel.Control.SYNC_VIEW);

    return panel;
  }

  // --------------------- End Interface Viewable ---------------------

  /**
   * Returns a read-only list of decoded tiles.
   *
   * @return {@link List} of tiles as {@link Image} objects.
   */
  public List<Image> getTileList() {
    return Collections.unmodifiableList(tileImages);
  }

  /**
   * Returns the {@link TisDecoder} instance for this tileset.
   *
   * @return {@link TisDecoder} instance.
   */
  public TisDecoder getDecoder() {
    return decoder;
  }

  /** Returns whether the specified PVRZ index can be found in the current TIS resource. */
  public boolean containsPvrzReference(int index) {
    boolean retVal = false;
    if (index >= 0 && index <= 99) {
      if (decoder instanceof TisV2Decoder) {
        TisV2Decoder tisv2 = (TisV2Decoder) decoder;
        for (int i = 0, count = tisv2.getTileCount(); i < count && !retVal; i++) {
          retVal = (tisv2.getPvrzPage(i) == index);
        }
      }
    }
    return retVal;
  }

  // Returns detected or guessed number of tiles per row of the current TIS
  private int getDefaultTilesPerRow() {
    return defaultWidth;
  }

  // Returns output filename for a PNG file
  private Path getPngFileName(Component parent) {
    Path retVal = null;
    JFileChooser fc = new JFileChooser(ResourceFactory.getExportFilePath().toFile());
    fc.setDialogTitle("Export resource");
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    FileNameExtensionFilter filter = new FileNameExtensionFilter("PNG files (*.png)", "png");
    fc.addChoosableFileFilter(filter);
    fc.setFileFilter(filter);
    fc.setSelectedFile(new File(fc.getCurrentDirectory(),
        getResourceEntry().getResourceName().toUpperCase(Locale.ENGLISH).replace(".TIS", ".PNG")));
    if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
      retVal = fc.getSelectedFile().toPath();
      if (FileEx.create(retVal).exists()) {
        if (ResourceFactory.confirmOverwrite(retVal, true, parent, "Export resource") != 0) {
          retVal = null;
        }
      }
    }
    return retVal;
  }

  private void initTileset() {
    try {
      WindowBlocker.blockWindow(true);

      decoder = TisDecoder.loadTis(entry);
      if (decoder != null) {
        wedResource = TisConvert.loadWedForTis(entry, false);
        initOverlayMap(wedResource);
        int tileCount = decoder.getTileCount();
        final ResourceEntry wedEntry = (wedResource != null) ? wedResource.getResourceEntry() : null;
        defaultWidth = TisConvert.calcTilesetWidth(wedEntry, false, tileCount);
        tileImages = new ArrayList<>(tileCount);
        for (int tileIdx = 0; tileIdx < tileCount; tileIdx++) {
          BufferedImage image = ColorConvert.createCompatibleImage(decoder.getTileWidth(), decoder.getTileHeight(),
              Transparency.BITMASK);
          decoder.getTile(tileIdx, image);
          tileImages.add(image);
        }
      } else {
        throw new Exception("No TIS resource loaded");
      }
      WindowBlocker.blockWindow(false);
    } catch (Exception e) {
      Logger.error(e);
      WindowBlocker.blockWindow(false);
      if (tileImages == null) {
        tileImages = new ArrayList<>();
      }
      if (tileImages.isEmpty()) {
        tileImages.add(ColorConvert.createCompatibleImage(1, 1, Transparency.BITMASK));
      }
      JOptionPane.showMessageDialog(NearInfinity.getInstance(),
          "Error while loading TIS resource: " + entry.getResourceName(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  // Initializes the WED overlay lookup table for tile information, if WED resource is available.
  private void initOverlayMap(WedResource wed) {
    if (wed != null) {
      try {
        final Overlay ovl = (Overlay) wed.getAttribute(Overlay.WED_OVERLAY + " 0");
        int width = ((IsNumeric) ovl.getAttribute(Overlay.WED_OVERLAY_WIDTH)).getValue();
        int height = ((IsNumeric) ovl.getAttribute(Overlay.WED_OVERLAY_HEIGHT)).getValue();
        wedTileMap = new HashMap<>(width * height * 4 / 3);

        // mapping primary tiles
        final List<StructEntry> tileMapList = ovl.getFields(Tilemap.class);
        final List<StructEntry> tileIndexList = ovl.getFields(IndexNumber.class);
        for (final StructEntry e : tileMapList) {
          final Tilemap tm = (Tilemap) e;
          final int idx = ((IsNumeric) tm.getAttribute(Tilemap.WED_TILEMAP_TILE_INDEX_PRI)).getValue();
          final int tileIdx = ((IsNumeric) tileIndexList.get(idx)).getValue();
          wedTileMap.put(tileIdx, tm);
        }

        // mapping secondary tiles
        wedTileMap.entrySet().stream().filter(e -> {
          final int idx = ((IsNumeric) e.getValue().getAttribute(Tilemap.WED_TILEMAP_TILE_INDEX_SEC)).getValue();
          return (idx >= 0 && !wedTileMap.containsKey(idx));
        }).forEach((e) -> {
          final Tilemap tm = e.getValue();
          final int idx = ((IsNumeric) tm.getAttribute(Tilemap.WED_TILEMAP_TILE_INDEX_SEC)).getValue();
          wedTileMap.putIfAbsent(idx, tm);
        });
      } catch (Exception e) {
        Logger.trace(e);
      }
    }
  }

  /** Opens the specified {@code StructEntry} in a new {@code ViewFrame} window. */
  private boolean openStructEntry(StructEntry e) {
    if (e != null) {
      for (StructEntry se = e; se != null; se = se.getParent()) {
        if (se instanceof Resource) {
          final Resource viewable = (Resource) se;
          new ViewFrame(panel, viewable);
          ((AbstractStruct) viewable).getViewer().selectEntry(e.getOffset());
          return true;
        }
      }
    }
    return false;
  }

  /** Opens the PVRZ resource containing the specified tile index. */
  private boolean openPvrzResource(int tileIndex) {
    if (decoder instanceof TisV2Decoder && tileIndex >= 0 && tileIndex < decoder.getTileCount()) {
      final TisV2Decoder d = (TisV2Decoder) decoder;
      final ResourceEntry resEntry = ResourceFactory.getResourceEntry(d.getPvrzFileName(tileIndex));
      if (resEntry != null) {
        final Resource res = ResourceFactory.getResource(resEntry);
        if (res != null) {
          new ViewFrame(panel, res);
          return true;
        }
      }
    }
    return false;
  }

  /** Opens a message dialog with PVRZ-related information about the specified tile. */
  private boolean showPvrzInfo(int tileIndex) {
    if (decoder instanceof TisV2Decoder && tileIndex >= 0 && tileIndex < decoder.getTileCount()) {
      final TisV2Decoder d = (TisV2Decoder) decoder;
      final ByteBuffer buf = d.getResourceBuffer();
      final int tileOfs = buf.getInt(0x10) + tileIndex * 0x0c;
      final int tilePage = buf.getInt(tileOfs);

      final String info;
      if (tilePage < 0) {
        info = "Tile type: Solid black color<br>No PVRZ reference available.";
      } else {
        final String pvrzName = d.getPvrzFileName(tileIndex);
        final int tileX = buf.getInt(tileOfs + 4);
        final int tileY = buf.getInt(tileOfs + 8);
        info = String.format(
            "PVRZ resource: %s<br>" +
            "PVRZ page: %d<br>" +
            "PVRZ coordinates: x=%d, y=%d",
            pvrzName, tilePage, tileX, tileY);
      }
      JOptionPane.showMessageDialog(panel,
          String.format("<html><div style=\"font-family:monospace;padding-right:8px\">%s<br>&nbsp;</div></html>", info),
          "PVRZ information: tile " + tileIndex, JOptionPane.INFORMATION_MESSAGE);
      return true;
    }
    return false;
  }

  /**
   * Performs the given operation in a background task.
   *
   * @param operation Operation to perform as {@link Supplier} object.
   * @return {@link SwingWorker} instance that is used to perform the background operation.
   */
  private SwingWorker<Status, Void> performBackgroundTask(Supplier<Status> operation) {
    if (operation != null) {
      blocker = new WindowBlocker(rpc);
      blocker.setBlocked(true);
      final SwingWorker<Status, Void> worker = new SwingWorker<Status, Void>() {
        protected Status doInBackground() throws Exception {
          Status retVal = Status.ERROR;
          try {
            retVal = operation.get();
          } catch (Exception e) {
            Logger.error(e);
          }
          return retVal;
        }
      };
      workers.add(worker);
      worker.addPropertyChangeListener(this);
      worker.execute();
      return worker;
    }
    return null;
  }

  // Calculates a Dimension structure with the correct number of columns and rows from the specified arguments
  private static Dimension calcGridSize(int imageCount, int colSize) {
    if (imageCount >= 0 && colSize > 0) {
      int rowSize = imageCount / colSize;
      if (imageCount % colSize > 0) {
        rowSize++;
      }
      return new Dimension(colSize, Math.max(1, rowSize));
    }
    return null;
  }

  // -------------------------- INNER CLASSES --------------------------

  private final class PopupListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      maybeShowPopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      maybeShowPopup(e);
    }

    private void maybeShowPopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        int index = tileGrid.getTileIndexAt(new Point(e.getX(), e.getY()));
        if (index >= 0 && index < decoder.getTileCount()) {
          lastTileInfoIndex = index;

          miTileInfoShow.setText(String.format(FMT_TILEINFO_SHOW, lastTileInfoIndex));
          miTileInfoShow.setVisible(decoder.getType() == TisDecoder.Type.PVRZ);

          miTileInfoPvrz.setText(String.format(FMT_TILEINFO_PVRZ, lastTileInfoIndex));
          miTileInfoPvrz.setVisible(decoder.getType() == TisDecoder.Type.PVRZ);

          miTileInfoWed.setText(String.format(FMT_TILEINFO_WED, lastTileInfoIndex));
          miTileInfoWed.setVisible(wedTileMap != null && wedTileMap.containsKey(lastTileInfoIndex));

          if (miTileInfoShow.isVisible() || miTileInfoPvrz.isVisible() || miTileInfoWed.isVisible()) {
            menuTileInfo.show(e.getComponent(), e.getX(), e.getY());
          }
        }
      }
    }
  }

  /**
   * This class implements a customizable tileset preview.
   */
  private static class TisPreview {
    /** Default size of a preview tile, in pixels. */
    public static final int DEF_TILE_SIZE = 4;

    /** Default color of the splitter bar. */
    public static final Color DEF_SPLIT_COLOR = Color.RED;

    /** Default interpolation mode for creating preview tiles. */
    private static final Object DEF_INTERPOLATION = RenderingHints.VALUE_INTERPOLATION_BILINEAR;

    /** Max. number of cached image entries to retain. */
    private static final int CACHE_SIZE_MAX = 32;

    private final LinkedList<Couple<Dimension, BufferedImage>> imageCache = new LinkedList<>();

    private final List<int[]> tiles = new ArrayList<>();

    private final TisDecoder decoder;
    private final Color splitColor;
    private final Object renderingHints;

    private int tileSize;

    /**
     * Creates a tileset preview with the following defaults:
     * <ul>
     * <li>Tile size: {@link #DEF_TILE_SIZE} pixels</li>
     * <li>Split Color: Red</li>
     * <li>Interpolation: Bilinear</li>
     * </ul>
     *
     * @param decoder {@link TisDecoder} instance with tile information.
     * @throws Exception If preview tiles could not be created.
     */
    public TisPreview(TisDecoder decoder) throws Exception {
      this(decoder, DEF_TILE_SIZE, null, null);
    }

    /**
     * Creates a tileset preview.
     *
     * @param decoder        {@link TisDecoder} instance with tile information.
     * @param tileSize       Size of the preview tiles, in pixels.
     * @param splitColor     Color of the splitter that separates primary tiles from secondary tiles.
     * @param renderingHints Interpolation mode used for createing preview tiles.
     * @throws Exception If preview tiles could not be created.
     */
    public TisPreview(TisDecoder decoder, int tileSize, Color splitColor, Object renderingHints)
        throws Exception {
      this.decoder = Objects.requireNonNull(decoder);
      this.tileSize = Math.max(1, Math.min(decoder.getTileWidth(), tileSize));
      this.splitColor = (splitColor != null) ? splitColor : DEF_SPLIT_COLOR;
      this.renderingHints = validateRenderingHints(renderingHints);
      init();
    }

    /** Returns the associated {@link TisDecoder} instance. */
    public TisDecoder getDecoder() {
      return decoder;
    }

    /** Returns the preview tile size, in pixels. */
    public int getTileSize() {
      return tileSize;
    }

    /** Sets the preview tile size, in pixels. Forces the preview tiles to be recreated. */
    public TisPreview setTileSize(int tileSize) throws Exception {
      tileSize = Math.max(1, Math.min(getDecoder().getTileWidth(), tileSize));
      if (tileSize != this.tileSize) {
        this.tileSize = tileSize;
        init();
      }
      return this;
    }

    /** Returns the color of the splitter that separates the primary tile region from secondary tiles. */
    public Color getSplitColor() {
      return splitColor;
    }

//    /** Sets the color of the splitter that separates the primary tile region from secondary tiles. Default: RED */
//    public TisPreview setSplitColor(Color splitColor) {
//      this.splitColor = (splitColor != null) ? splitColor : DEF_SPLIT_COLOR;
//      return this;
//    }

    /** Returns the interpolation mode used for preview tile creation. */
    public Object getRenderingHints() {
      return renderingHints;
    }

//    /** Sets the interpolation mode for preview tile creation. Forces the preview tiles to be recreated. */
//    public TisPreview setRenderingHints(Object renderingHints) throws Exception {
//      renderingHints = validateRenderingHints(renderingHints);
//      if (renderingHints != this.renderingHints) {
//        this.renderingHints = renderingHints;
//        init();
//      }
//      return this;
//    }

    /**
     * Returns a {@code BufferedImage} instance with the tileset laid out with the specified dimension.
     *
     * @param width Tileset width, in # tiles.
     * @param height Tileset height, in # tiles.
     * @return A {@link BufferedImage} object with the tileset representation.
     */
    public BufferedImage get(int width, int height) {
      final int tilesPerRow = Math.max(1, Math.min(getDecoder().getTileCount(), width));
      final int numRows = (getDecoder().getTileCount() + tilesPerRow - 1) / tilesPerRow;
      final int splitPos = (height < numRows) ? Math.max(0, height) : -1; // y position of the splitter bar
      final BufferedImage image = createImage(tilesPerRow, numRows);
      final int[] buffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

      // rendering tiles according to given parameters
      final int pixelsPerRow = tilesPerRow * tileSize;
      for (int y = 0; y < numRows; y++) {
        for (int x = 0; x < tilesPerRow; x++) {
          int tileIdx = y * tilesPerRow + x;
          if (tileIdx < tiles.size()) {
            final int[] block = tiles.get(tileIdx);
            for (int srcOfs = 0, dstOfs = (y * pixelsPerRow + x) * tileSize, dy = 0;
                dy < tileSize;
                srcOfs += tileSize, dstOfs += pixelsPerRow, dy++) {
              System.arraycopy(block, srcOfs, buffer, dstOfs, tileSize);
            }
          }
        }
      }

      // rendering splitter
      if (splitPos >= 0) {
        final int ofs = splitPos * pixelsPerRow * tileSize;
        final int len = pixelsPerRow;
        final int colVal = (getSplitColor().getRGB() & 0x00ffffff) | 0xa0000000;  // reduce alpha by 25 percent
        Arrays.fill(buffer, ofs, ofs + len, colVal);
      }

      return image;
    }

    /**
     * Returns a {@code BufferedImage} object matching the given parameters.
     *
     * @param width Number of tiles per row.
     * @param height Number of tile rows.
     * @return A new {@link BufferedImage} object.
     */
    private BufferedImage createImage(int width, int height) {
      BufferedImage retVal;

      width = Math.max(1, width);
      height = Math.max(1, height);
      final int imageWidth = width * tileSize;
      final int imageHeight = height * tileSize;
      final Dimension dim = new Dimension(imageWidth, imageHeight);
      Couple<Dimension, BufferedImage> entry =
          imageCache.stream().filter(c -> c.getValue0().equals(dim)).findFirst().orElse(null);
      if (entry != null) {
        retVal = entry.getValue1();
        // update cache position
        imageCache.remove(entry);
        imageCache.addLast(entry);
      } else {
        retVal = ColorConvert.createCompatibleImage(imageWidth, imageHeight, true);
        entry = new Couple<>(dim, retVal);
        imageCache.addLast(entry);
        // trimming cache
        if (imageCache.size() > CACHE_SIZE_MAX) {
          imageCache.removeFirst();
        }
      }

      // clearing image content
      final Graphics2D g = retVal.createGraphics();
      try {
        g.setComposite(AlphaComposite.Src);
        g.setColor(ColorConvert.TRANSPARENT_COLOR);
        g.fillRect(0, 0, retVal.getWidth(), retVal.getHeight());
      } finally {
        g.dispose();
      }

      return retVal;
    }

    /** Creates preview tiles from the tileset. */
    private void init() throws Exception {
      tiles.clear();

      final int tileWidth = getDecoder().getTileWidth();
      final int tileHeight = getDecoder().getTileHeight();
      final AffineTransform xform = AffineTransform.getScaleInstance(tileSize / (double)tileWidth,
          tileSize / (double)tileHeight);
      final BufferedImage tileImg = ColorConvert.createCompatibleImage(tileWidth, tileHeight, true);

      for (int idx = 0, size = getDecoder().getTileCount(); idx < size; idx++) {
        getDecoder().getTile(idx, tileImg);

        // rendering downscaled preview tile
        final BufferedImage previewImg = ColorConvert.createCompatibleImage(tileSize, tileSize, true);
        final Graphics2D g = previewImg.createGraphics();
        try {
          g.setComposite(AlphaComposite.Src);
          g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, getRenderingHints());
          g.drawImage(tileImg, xform, null);
        } finally {
          g.dispose();
        }

        // storing preview tile data
        final int[] buf = ((DataBufferInt) previewImg.getRaster().getDataBuffer()).getData();
        tiles.add(Arrays.copyOf(buf, buf.length));
      }
    }

    /** Ensures that only interpolation hints are returned. */
    private Object validateRenderingHints(Object renderingHints) {
      if (renderingHints != RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR &&
          renderingHints != RenderingHints.VALUE_INTERPOLATION_BILINEAR &&
          renderingHints != RenderingHints.VALUE_INTERPOLATION_BICUBIC) {
        renderingHints = DEF_INTERPOLATION;
      }
      return renderingHints;
    }
  }

  /**
   * Provides a dialog with options to customize tileset conversion settings.
   */
  private static class ConvertTisDialog extends JDialog {
    private static final String BORDER_SIZE_LABEL_FMT     = "%d pixel(s)";
    private static final String SEGMENT_SIZE_LABEL_FMT    = "%d pixels";
    private static final String PVRZ_BASE_INDEX_LABEL_FMT = "%d";
    private static final String TILES_PER_ROW_LABEL_FMT   = "%d tile(s)";
    private static final String ROW_COUNT_LABEL_FMT       = "%d row(s)";

    private final Listeners listeners = new Listeners();
    private final HashMap<Component, String> helpMap = new HashMap<>();

    private final TisResource tis;
    private final ResourceEntry wedEntry;

    // set to true only if the dialog is closed by clicking on "OK"
    private boolean accepted;

    private int defaultTisWidth;
    private int defaultTisHeight;
    private int overlayMovementType;

    private JLabel lTisFileLabel;
    private JLabel lBorderSizeLabel;
    private JLabel lBorderSize;
    private JLabel lSegmentSizeLabel;
    private JLabel lSegmentSize;
    private JLabel lPvrzBaseIndexLabel;
    private JLabel lPvrzBaseIndex;
    private JLabel lOverlayModeLabel;
    private JLabel lTilesPerRowLabel;
    private JLabel lTilesPerRow;
    private JLabel lNumRowsLabel;
    private JLabel lNumRows;
    private JLabel lPreviewImageLabel;
    private JLabel lPreviewImage;
    private JLabel lPreviewImageZoomLabel;
    private JLabel lHelpLabel;
    private JButton bPreviewReset;
    private JButton bOk;
    private JButton bCancel;
    private JButton bTisFile;
    private JCheckBox cbRemoveBlack;
    private JCheckBox cbMultithreaded;
    private JRadioButton rbAuto;
    private JRadioButton rbManual;
    private JTextField tfTisFile;
    private JTextArea taHelp;
    private JSlider sBorderSize;
    private JSlider sSegmentSize;
    private JSlider sPvrzBaseIndex;
    private JSlider sTilesPerRow;
    private JSlider sNumRows;
    private JComboBox<DataString<TisConvert.OverlayConversion>> cbOverlayMode;
    private JComboBox<DataString<Integer>> cbPreviewImageZoom;
    private JPanel panelSubAuto;
    private JPanel panelSubManual;
    private TisPreview tisPreview;
    private JScrollPane spPreviewImage;
    private JScrollPane spHelp;

    /**
     * Opens a modal dialog and returns a TIS configuration.
     *
     * @param owner Parent {@link Window} for the dialog.
     * @param tis   {@link TisDecoder} of the source tileset.
     * @return An initialized {@link TisConvert.Config} object if the user accepts the conversion options. Returns
     *         {@code null} if the user cancels the operation.
     */
    public static TisConvert.Config show(Component owner, TisResource tis) {
      Window window = SwingUtilities.getWindowAncestor(owner);
      if (window == null) {
        window = NearInfinity.getInstance();
      }

      TisConvert.Config retVal = null;
      ConvertTisDialog dlg = null;
      try {
        dlg = new ConvertTisDialog(window, tis);
        dlg.setVisible(true);
        retVal = dlg.getConfig();
      } catch (Exception e) {
        Logger.error(e);
      } finally {
        if (dlg != null) {
          dlg.dispose();
          dlg = null;
        }
      }

      return retVal;
    }

    private ConvertTisDialog(Window owner, TisResource tis) {
      super(owner, Dialog.ModalityType.APPLICATION_MODAL);
      this.tis = Objects.requireNonNull(tis);
      this.wedEntry = TisConvert.findWed(tis.entry, true);
      init();
    }

    /**
     * Returns a {@link TisConvert.Config} object initialized with the current dialog settings.
     * Returns {@code null} if setting are invalid or the user cancelled the dialog.
     */
    private TisConvert.Config getConfig() {
      TisConvert.Config retVal = null;
      if (!accepted) {
        return retVal;
      }

      try {
        final TisDecoder decoder = tis.getDecoder();
        final Path tisFile = getTisPath();
        final List<Image> tileList = getTileList();
        final int tilesPerRow = getTilesPerRow();
        final int rowCount = getRowCount();
        final int textureSize = TisConvert.Config.MAX_TEXTURE_SIZE;
        final int pvrzBaseIndex = getPvrzBaseIndex();
        final boolean detectBlack = isDetectBlack();
        final boolean multithreaded = isMultithreaded();
        final int borderSize = getBorderSize();
        final int segmentSize = getSegmentSize();
        final TisConvert.OverlayConversion convert = getOverlayConversionMode();

        if (decoder instanceof TisV1Decoder) {
          retVal = TisConvert.Config.createConfigPvrz(tisFile, decoder, wedEntry, tilesPerRow, rowCount, textureSize,
              pvrzBaseIndex, borderSize, segmentSize, detectBlack, multithreaded, convert);
        } else if (decoder instanceof TisV2Decoder) {
          retVal = TisConvert.Config.createConfigPalette(tisFile, tileList, decoder, wedEntry, convert);
        } else {
          throw new Exception("Conversion not supported");
        }
      } catch (Exception e) {
        Logger.error(e);
      }

      return retVal;
    }

    private void init() {
      // initializing common components
      final boolean isTisV1 = tis.decoder instanceof TisV1Decoder;

      // dialog help
      final String helpHelp = "Hover the mouse cursor over the dialog elements for help.";
      lHelpLabel = new JLabel("Help:");
      lHelpLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lHelpLabel, helpHelp);

      final int taCols = isTisV1 ? 50 : 30;
      taHelp = new JTextArea(helpHelp, 3, taCols);
      taHelp.setEditable(false);
      taHelp.setLineWrap(true);
      taHelp.setWrapStyleWord(true);
      taHelp.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(taHelp, helpHelp);

      spHelp = new JScrollPane(taHelp, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
          JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      spHelp.setMinimumSize(spHelp.getPreferredSize());

      // TIS output file
      final String helpTisFile = "Path of the output TIS file.";
      lTisFileLabel = new JLabel("TIS Output:");
      lTisFileLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lTisFileLabel, helpTisFile);

      tfTisFile = new JTextField();
      try {
        tfTisFile.setText(Profile.getGameRoot().resolve(tis.entry.getResourceName()).toString());
      } catch (InvalidPathException ex) {
        Logger.trace(ex);
      }
      tfTisFile.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(tfTisFile, helpTisFile);

      bTisFile = new JButton("Choose...");
      bTisFile.setMnemonic(KeyEvent.VK_C);
      bTisFile.addMouseMotionListener(listeners.mouseMotion);
      bTisFile.addActionListener(listeners.actionTisFile);
      helpMap.put(bTisFile, helpTisFile);

      // overlay conversion modes
      final String helpOverlayMode = "Select an overlay conversion mode to correctly convert overlay tiles from one "
          + "game to another.";
      lOverlayModeLabel = new JLabel("Overlay conversion:");
      lOverlayModeLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lOverlayModeLabel, helpOverlayMode);

      // initializing overlay mode combobox
      cbOverlayMode = initOverlayModeComboBox(true);
      cbOverlayMode.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(cbOverlayMode, helpOverlayMode);

      bCancel = new JButton("Cancel");
      bCancel.addActionListener(listeners.actionCancel);

      bOk = new JButton("OK");
      bOk.addActionListener(listeners.actionOk);
      bOk.setPreferredSize(new Dimension(bCancel.getPreferredSize()));  // buttons should have same size

      // conversion-specific initializations
      if (isTisV1) {
        initPvrz();
        setTitle("Convert Tileset: Palette -> PVRZ");
      } else {
        initPalette();
        setTitle("Convert Tileset: PVRZ -> Palette");
      }

      // continuing common initializations
      pack();
      setMinimumSize(getPreferredSize());
      setLocationRelativeTo(getParent());
      bOk.requestFocus();

      // default action for ENTER key
      setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
      getRootPane().setDefaultButton(bOk);

      // default action for ESCAPE key
      getRootPane().registerKeyboardAction(listeners.actionCancel, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_IN_FOCUSED_WINDOW);

      addWindowListener(listeners.windowDialog);
    }

    private void initPalette() {
      // laying out components on dialog
      final GridBagConstraints c = new GridBagConstraints();

      // top panel (tis file)
      final JPanel panelTop = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelTop.add(lTisFileLabel, c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 8), 0, 0);
      panelTop.add(tfTisFile, c);
      ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelTop.add(bTisFile, c);

      // sub-panel for overlay conversion
      final JPanel panelSubOverlay = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelSubOverlay.add(lOverlayModeLabel, c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelSubOverlay.add(cbOverlayMode, c);
      ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 0), 0, 0);
      panelSubOverlay.add(new JPanel(), c);

      // help panel
      final JPanel panelHelp = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelHelp.add(lHelpLabel, c);
      ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 0, 0, 0), 0, 0);
      panelHelp.add(spHelp, c);

      // button panel
      final JPanel panelButtons = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 0), 0, 0);
      panelButtons.add(new JPanel(), c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelButtons.add(bOk, c);
      ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelButtons.add(bCancel, c);

      // putting all together
      final JPanel panelMain = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 8, 0, 8), 0, 0);
      panelMain.add(panelTop, c);
      ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(16, 8, 0, 8), 0, 0);
      panelMain.add(panelSubOverlay, c);
      ViewerUtil.setGBC(c, 0, 3, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(16, 8, 0, 8), 0, 0);
      panelMain.add(panelHelp, c);
      ViewerUtil.setGBC(c, 0, 4, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 8, 8, 8), 0, 0);
      panelMain.add(panelButtons, c);

      getContentPane().setLayout(new BorderLayout());
      getContentPane().add(panelMain, BorderLayout.CENTER);

    }

    private void initPvrz() {
      // initializing relevant WED information
      final WedResource wed = TisConvert.loadWedForTis(tis.entry, true);
      if (wed != null) {
        final Overlay ovl = (Overlay) wed.getAttribute(Overlay.WED_OVERLAY + " 0");
        if (ovl != null) {
          defaultTisWidth = ((IsNumeric) ovl.getAttribute(Overlay.WED_OVERLAY_WIDTH)).getValue();
          defaultTisHeight = ((IsNumeric) ovl.getAttribute(Overlay.WED_OVERLAY_HEIGHT)).getValue();
          final StructEntry se = ovl.getAttribute(Overlay.WED_OVERLAY_MOVEMENT_TYPE);
          if (se != null) {
            overlayMovementType = ((IsNumeric) se).getValue();
          }
        }
      }

      // border size
      final String helpBorderSize = "Number of pixels to consider from surrounding tiles on PVRZ textures to prevent "
          + "visual artifacts. Recommended: " + TisConvert.Config.DEFAULT_BORDER_SIZE + " pixels.";
      lBorderSizeLabel = new JLabel("Border Size:");
      lBorderSizeLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lBorderSizeLabel, helpBorderSize);

      final int tileSize = tis.getDecoder().getTileWidth();
      sBorderSize = new JSlider(0, tileSize, Math.min(tileSize, TisConvert.Config.DEFAULT_BORDER_SIZE));
      sBorderSize.addMouseMotionListener(listeners.mouseMotion);
      sBorderSize.addChangeListener(listeners.changeBorderSize);
      helpMap.put(sBorderSize, helpBorderSize);

      lBorderSize = new JLabel(String.format(BORDER_SIZE_LABEL_FMT, sBorderSize.getValue()));
      lBorderSize.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lBorderSize, helpBorderSize);

      // segment size
      final String helpSegmentSize = "Defines the max. size of contiguous blocks of secondary tiles that are placed on "
          + "PVRZ textures. Smaller sizes may reduce the number of generated PVRZ textures but increase fragmentation. "
          + "Recommended: 256 or 512 pixels.";
      lSegmentSizeLabel = new JLabel("Segment Size:");
      lSegmentSizeLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lSegmentSizeLabel, helpSegmentSize);

      sSegmentSize = new JSlider(6, 10, 9); // 64 (1 << 6) to 1024 (1 << 10)
      sSegmentSize.addMouseMotionListener(listeners.mouseMotion);
      sSegmentSize.addChangeListener(listeners.changeSegmentSize);
      helpMap.put(sSegmentSize, helpSegmentSize);

      lSegmentSize = new JLabel(String.format(SEGMENT_SIZE_LABEL_FMT, getSegmentSize()));
      lSegmentSize.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lSegmentSize, helpSegmentSize);

      // pvrz base index
      final String helpPvrzBaseIndex = "Defines the start value for page numbers used by the PVRZ filename scheme "
          + "(e.g. A260000.PVRZ for index=0, A260013.PVRZ for index=13.) Adjust only to avoid overlapping PVRZ "
          + "filenames from other TIS files. Default value: 0";
      lPvrzBaseIndexLabel = new JLabel("PVRZ Base Index:");
      lPvrzBaseIndexLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lPvrzBaseIndexLabel, helpPvrzBaseIndex);

      sPvrzBaseIndex = new JSlider(0, 99, 0);
      sPvrzBaseIndex.addMouseMotionListener(listeners.mouseMotion);
      sPvrzBaseIndex.addChangeListener(listeners.changePvrzBaseIndex);
      helpMap.put(sPvrzBaseIndex, helpPvrzBaseIndex);

      lPvrzBaseIndex = new JLabel(String.format(PVRZ_BASE_INDEX_LABEL_FMT, getPvrzBaseIndex()));
      lPvrzBaseIndex.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lPvrzBaseIndex, helpPvrzBaseIndex);

      // remove black tiles
      final String helpRemoveBlack = "When this option is selected then solid black tiles will be replaced by a global "
          + "default which can reduce the number of generated PVRZ textures.";
      cbRemoveBlack = new JCheckBox("Optimize black tiles", true);
      cbRemoveBlack.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(cbRemoveBlack, helpRemoveBlack);

      // multithreaded
      final String helpMultithreaded = "Specifies whether to speed up PVRZ texture encoding by using multiple threads "
          + "of execution.";
      final int numCores = Runtime.getRuntime().availableProcessors();
      cbMultithreaded = new JCheckBox("Multithreaded", numCores > 2);
      cbMultithreaded.setEnabled(numCores > 1);
      cbMultithreaded.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(cbMultithreaded, helpMultithreaded);

      // radio buttons
      final ButtonGroup buttonGroup = new ButtonGroup();
      final String helpRadioAuto = "Automatically configures tileset parameters based on WED information. "
          + "This mode is only available if an associated WED resource exists.";
      rbAuto = new JRadioButton("Automatic (recommended)");
      rbAuto.setMnemonic(KeyEvent.VK_A);
      rbAuto.setEnabled(wedEntry != null);
      rbAuto.addMouseMotionListener(listeners.mouseMotion);
      rbAuto.addItemListener(listeners.itemAuto);
      helpMap.put(rbAuto, helpRadioAuto);
      buttonGroup.add(rbAuto);

      final String helpRadioManual = "Manual customization of tileset parameters. This is the only choice if no "
          + "associated WED resource could be determined. This mode does not prevent visual artifacts around secondary "
          + "tiles.";
      rbManual = new JRadioButton("Manual");
      rbManual.setMnemonic(KeyEvent.VK_M);
      rbManual.addMouseMotionListener(listeners.mouseMotion);
      rbManual.addItemListener(listeners.itemManual);
      helpMap.put(rbManual, helpRadioManual);
      buttonGroup.add(rbManual);

      // tiles per row
      final String helpTilesPerRow = "Defines the width of the tileset, in tiles. An incorrect width will create a "
          + "distorted tileset layout which increases the likelihood of visual artifacts with PVRZ-based tilesets.";
      lTilesPerRowLabel = new JLabel("Tiles per Row:");
      lTilesPerRowLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lTilesPerRowLabel, helpTilesPerRow);

      sTilesPerRow = new JSlider(1, tis.decoder.getTileCount());
      sTilesPerRow.setValue(Math.max(1, Math.min(tis.decoder.getTileCount(), tis.slCols.getValue())));
      sTilesPerRow.addMouseMotionListener(listeners.mouseMotion);
      sTilesPerRow.addChangeListener(listeners.changeTilesPerRow);
      helpMap.put(sTilesPerRow, helpTilesPerRow);

      lTilesPerRow = new JLabel(String.format(TILES_PER_ROW_LABEL_FMT, sTilesPerRow.getValue()));
      lTilesPerRow.setPreferredSize(new Dimension(bTisFile.getPreferredSize().width, lTilesPerRow.getPreferredSize().height));
      lTilesPerRow.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lTilesPerRow, helpTilesPerRow);

      // tileset height
      final String helpNumRows = "Defines the height of the tileset, in tiles. All tiles exceeding the amount of "
          + "'Tiles Per Row' x 'Tileset Height' are treated as secondary tiles.";
      lNumRowsLabel = new JLabel("Tileset Height:");
      lNumRowsLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lNumRowsLabel, helpNumRows);

      sNumRows = new JSlider(1, tis.decoder.getTileCount() / sTilesPerRow.getValue());
      sNumRows.setValue((defaultTisHeight > 0) ? defaultTisHeight : sNumRows.getMaximum());
      sNumRows.addMouseMotionListener(listeners.mouseMotion);
      sNumRows.addChangeListener(listeners.changeNumRows);
      helpMap.put(sNumRows, helpNumRows);

      lNumRows = new JLabel(String.format(ROW_COUNT_LABEL_FMT, sNumRows.getValue()));
      lNumRows.setPreferredSize(new Dimension(bTisFile.getPreferredSize().width, lTilesPerRow.getPreferredSize().height));
      lNumRows.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lNumRows, helpNumRows);

      // tileset preview
      final String helpPreviewImage = "A visual representation of the tileset layout to help setting the right width "
          + "and height parameters.";
      lPreviewImageLabel = new JLabel("Tileset Preview:");
      lPreviewImageLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lPreviewImageLabel, helpPreviewImage);

      try {
        tisPreview = new TisPreview(tis.decoder);
      } catch (Exception e) {
        Logger.error(e);
      }

      final int previewWidth = sTilesPerRow.getValue();
      final int previewHeight = sNumRows.getValue();
      lPreviewImage = new JLabel(new ImageIcon(tisPreview.get(previewWidth, previewHeight)));
      lPreviewImage.setHorizontalAlignment(SwingConstants.LEFT);
      lPreviewImage.setVerticalAlignment(SwingConstants.TOP);
      lPreviewImage.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lPreviewImage, helpPreviewImage);

      spPreviewImage = new JScrollPane(lPreviewImage, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
          JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      spPreviewImage.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      spPreviewImage.getHorizontalScrollBar().setUnitIncrement(16);
      spPreviewImage.getVerticalScrollBar().setUnitIncrement(16);
      // setting default preview dimension
      if (defaultTisWidth > 0 && defaultTisHeight > 0) {
        final int width = defaultTisWidth * tisPreview.getTileSize();
        final int height = ((tis.decoder.getTileCount() + defaultTisWidth - 1) / defaultTisWidth) * tisPreview.getTileSize();
        spPreviewImage.setPreferredSize(new Dimension(width + 2, height + 2));
      } else {
        final int tisWidth = Math.min(80, previewWidth) * tisPreview.getTileSize();
        final int height = (tis.decoder.getTileCount() + previewWidth - 1) / previewWidth;
        final int tisHeight = Math.min(60, height) * tisPreview.getTileSize();
        spPreviewImage.setPreferredSize(new Dimension(tisWidth + 2, tisHeight + 2));
      }

      bPreviewReset = new JButton("Reset");
      bPreviewReset.setEnabled(defaultTisHeight > 0);
      bPreviewReset.setToolTipText("Click to reset tileset dimensions");
      bPreviewReset.addMouseMotionListener(listeners.mouseMotion);
      bPreviewReset.addActionListener(listeners.actionPreviewReset);
      helpMap.put(bPreviewReset, helpPreviewImage);

      lPreviewImageZoomLabel = new JLabel("Zoom:");
      lPreviewImageZoomLabel.addMouseMotionListener(listeners.mouseMotion);
      helpMap.put(lPreviewImageZoomLabel, helpPreviewImage);

      final Vector<DataString<Integer>> previewImageZoomValues = new Vector<>();
      for (final int zoom : new int[] {1, 2, 3, 4, 6, 8, 12, 16}) {
        previewImageZoomValues.add(new DataString<>(String.format("%dx", zoom), zoom));
      }
      cbPreviewImageZoom = new JComboBox<>(previewImageZoomValues);
      cbPreviewImageZoom.setSelectedIndex(getPreviewZoomIndex(tisPreview.getTileSize(), 2));
      cbPreviewImageZoom.addMouseMotionListener(listeners.mouseMotion);
      cbPreviewImageZoom.addItemListener(listeners.itemPreviewImageZoom);
      helpMap.put(cbPreviewImageZoom, helpPreviewImage);

      // laying out components on dialog
      final GridBagConstraints c = new GridBagConstraints();

      // top panel (tis file, border size)
      final JPanel panelTop = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelTop.add(lTisFileLabel, c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 8), 0, 0);
      panelTop.add(tfTisFile, c);
      ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelTop.add(bTisFile, c);

      ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 8), 0, 0);
      panelTop.add(lBorderSizeLabel, c);
      ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 0, 0, 8), 0, 0);
      panelTop.add(sBorderSize, c);
      ViewerUtil.setGBC(c, 2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelTop.add(lBorderSize, c);

      ViewerUtil.setGBC(c, 0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 8), 0, 0);
      panelTop.add(lSegmentSizeLabel, c);
      ViewerUtil.setGBC(c, 1, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 0, 0, 8), 0, 0);
      panelTop.add(sSegmentSize, c);
      ViewerUtil.setGBC(c, 2, 2, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelTop.add(lSegmentSize, c);

      ViewerUtil.setGBC(c, 0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 8), 0, 0);
      panelTop.add(lPvrzBaseIndexLabel, c);
      ViewerUtil.setGBC(c, 1, 3, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 0, 0, 8), 0, 0);
      panelTop.add(sPvrzBaseIndex, c);
      ViewerUtil.setGBC(c, 2, 3, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelTop.add(lPvrzBaseIndex, c);

      // sub-panel for checkbox options
      final JPanel panelSubOptions = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelSubOptions.add(cbRemoveBlack, c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelSubOptions.add(cbMultithreaded, c);

      ViewerUtil.setGBC(c, 0, 4, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelTop.add(new JPanel(), c);
      ViewerUtil.setGBC(c, 1, 4, 2, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelTop.add(panelSubOptions, c);

      // sub-panel for Auto mode (overlay conversion)
      panelSubAuto = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelSubAuto.add(lOverlayModeLabel, c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelSubAuto.add(cbOverlayMode, c);
      ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 0), 0, 0);
      panelSubAuto.add(new JPanel(), c);

      // Auto mode panel
      final JPanel panelAutoMode = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelAutoMode.add(rbAuto, c);
      ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(12, 24, 0, 0), 0, 0);
      panelAutoMode.add(panelSubAuto, c);

      // sub-panel for Manual mode (cols, rows, preview)
      panelSubManual = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelSubManual.add(lTilesPerRowLabel, c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 8), 0, 0);
      panelSubManual.add(sTilesPerRow, c);
      ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelSubManual.add(lTilesPerRow, c);

      ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 8), 0, 0);
      panelSubManual.add(lNumRowsLabel, c);
      ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 0, 0, 8), 0, 0);
      panelSubManual.add(sNumRows, c);
      ViewerUtil.setGBC(c, 2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelSubManual.add(lNumRows, c);

      ViewerUtil.setGBC(c, 0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 8), 0, 0);
      panelSubManual.add(lPreviewImageLabel, c);
      ViewerUtil.setGBC(c, 0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.LAST_LINE_END, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 8), 0, 0);
      panelSubManual.add(bPreviewReset, c);
      ViewerUtil.setGBC(c, 1, 2, 1, 2, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
          new Insets(8, 8, 0, 8), 0, 0);
      panelSubManual.add(spPreviewImage, c);
      ViewerUtil.setGBC(c, 2, 2, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelSubManual.add(lPreviewImageZoomLabel, c);
      ViewerUtil.setGBC(c, 2, 3, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      panelSubManual.add(cbPreviewImageZoom, c);

      // Manual mode panel
      final JPanel panelManualMode = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelManualMode.add(rbManual, c);
      ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
          new Insets(12, 24, 0, 0), 0, 0);
      panelManualMode.add(panelSubManual, c);

      // help panel
      final JPanel panelHelp = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelHelp.add(lHelpLabel, c);
      ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 0, 0, 0), 0, 0);
      panelHelp.add(spHelp, c);

      // button panel
      final JPanel panelButtons = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 0), 0, 0);
      panelButtons.add(new JPanel(), c);
      ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 8), 0, 0);
      panelButtons.add(bOk, c);
      ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(0, 0, 0, 0), 0, 0);
      panelButtons.add(bCancel, c);

      // putting all together
      final JPanel panelMain = new JPanel(new GridBagLayout());
      ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 8, 0, 8), 0, 0);
      panelMain.add(panelTop, c);
      ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(16, 8, 0, 8), 0, 0);
      panelMain.add(panelAutoMode, c);
      ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
          new Insets(16, 8, 0, 8), 0, 0);
      panelMain.add(panelManualMode, c);
      ViewerUtil.setGBC(c, 0, 3, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(16, 8, 0, 8), 0, 0);
      panelMain.add(panelHelp, c);
      ViewerUtil.setGBC(c, 0, 4, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(8, 8, 8, 8), 0, 0);
      panelMain.add(panelButtons, c);

      getContentPane().setLayout(new BorderLayout());
      getContentPane().add(panelMain, BorderLayout.CENTER);

      // preselecting mode
      if (wedEntry != null) {
        rbAuto.setSelected(true);
      } else {
        rbManual.setSelected(true);
      }
      setPanelEnabled(panelSubAuto, rbAuto.isSelected());
      setPanelEnabled(panelSubManual, !rbAuto.isSelected());
    }

    private JComboBox<DataString<TisConvert.OverlayConversion>> initOverlayModeComboBox(boolean preselect) {
      final boolean isTisV1 = tis.decoder instanceof TisV1Decoder;
      final boolean isTisV2 = tis.decoder instanceof TisV2Decoder;
      final boolean isBG1 = Profile.getEngine() == Profile.Engine.BG1;
      final boolean isBG2 = Profile.getEngine() == Profile.Engine.BG2;
      final boolean isEE = Profile.isEnhancedEdition();
      final boolean isBGEE = (Profile.getGame() == Profile.Game.BG1EE) || (Profile.getGame() == Profile.Game.BG1SoD);
      final boolean hasWed = (wedEntry != null);

      // initializing overlay mode combobox
      final Vector<DataString<TisConvert.OverlayConversion>> overlayModes = new Vector<>();
      int overlayModeIndex = 0;
      for (final TisConvert.OverlayConversion mode : TisConvert.OverlayConversion.values()) {
        boolean add;
        boolean select = false;
        switch (mode) {
          case BG1_TO_BGEE:
            add = hasWed && isTisV1;
            select = isBG1 ||
                     (overlayMovementType == 0 && isBGEE) ||
                     (overlayMovementType == 2 && isEE && !isBGEE);
            break;
          case BG1_TO_BG2EE:
            add = hasWed && isTisV1;
            break;
          case BG2_TO_BGEE:
            add = hasWed && isTisV1;
            break;
          case BG2_TO_BG2EE:
            add = hasWed && isTisV1;
            select = isBG2 ||
                     (overlayMovementType == 0 && isEE && !isBGEE) ||
                     (overlayMovementType == 2 && isBGEE);
            break;
          case BGEE_TO_BG1:
            add = hasWed && isTisV2;
            select = isBG1 ||
                     (overlayMovementType == 0 && isBGEE) ||
                     (overlayMovementType == 2 && isEE && !isBGEE);
            break;
          case BGEE_TO_BG2:
            add = hasWed && isTisV2;
            break;
          case BG2EE_TO_BG1:
            add = hasWed && isTisV2;
            break;
          case BG2EE_TO_BG2:
            add = hasWed && isTisV2;
            select = isBG2 ||
                     (overlayMovementType == 0 && isEE && !isBGEE) ||
                     (overlayMovementType == 2 && isBGEE);
            break;
          default:
            add = true;
        }

        if (mode.isImplemented() && add) {
          final String modeText = mode.toString();
          final DataString<TisConvert.OverlayConversion> entry =
              new DataString<>(modeText, mode, DataString.FMT_STRING_ONLY);
          overlayModes.add(entry);
          if (select) {
            overlayModeIndex = overlayModes.size() - 1;
          }
        }
      }

      final JComboBox<DataString<TisConvert.OverlayConversion>> retVal = new JComboBox<>(overlayModes);
      if (preselect) {
        retVal.setSelectedIndex(overlayModeIndex);
      }
      return retVal;
    }

    /** Enables or disables all child component of the given panel. */
    private void setPanelEnabled(JComponent panel, boolean enable) {
      if (panel != null) {
        for (final Component c : panel.getComponents()) {
          c.setEnabled(enable);
        }
      }
    }

    /**
     * Returns the combobox item index that defines the specified {@code zoom}. Returns {@code defIndex} if no matching
     * entry could be found.
     */
    private int getPreviewZoomIndex(int zoom, int defIndex) {
      for (int idx = 0, size = cbPreviewImageZoom.getModel().getSize(); idx < size; idx++) {
        final int zoomValue = cbPreviewImageZoom.getModel().getElementAt(idx).getData();
        if (zoomValue == zoom) {
          return idx;
        }
      }
      return defIndex;
    }

    /**
     * Checks current dialog settings.
     *
     * @param interactive Indicates whether a message dialog should notify about failed checks.
     * @return {@code true} if all checks passed successfully, {@code false} otherwise.
     */
    private boolean validateSettings(boolean interactive) {
      // is TIS path empty?
      if (tfTisFile.getText().trim().isEmpty()) {
        if (interactive) {
          JOptionPane.showMessageDialog(this, "No TIS output file specified.", "Error", JOptionPane.ERROR_MESSAGE);
          tfTisFile.requestFocus();
        }
        return false;
      }

      try {
        final Path tisPath = Paths.get(tfTisFile.getText().trim());

        // does TIS filename conform to expected naming scheme?
        if (tis.getDecoder() instanceof TisV1Decoder && !TisConvert.isTisFileNameValid(tisPath)) {
          if (interactive) {
            JOptionPane.showMessageDialog(this, "PVRZ-based TIS filenames have to be 2 up to 7 characters long.",
                "Error", JOptionPane.ERROR_MESSAGE);
          }
          return false;
        }

        // does TIS file already exist?
        if (Files.exists(tisPath)) {
          int result = 1;
          if (interactive) {
            result = ResourceFactory.confirmOverwrite(tisPath, true, this, "Question");
            if (result != 0) {
              tfTisFile.requestFocus();
            }
          }
          if (result != 0) {
            return false;
          }
        }
      } catch (InvalidPathException e) {
        // is TIS path valid?
        if (interactive) {
          JOptionPane.showMessageDialog(this, "Invalid TIS output file specified.", "Error", JOptionPane.ERROR_MESSAGE);
          tfTisFile.requestFocus();
        }
        return false;
      }

      // PVRZ base index overlaps with existing PVRZ files?
      if (tis.getDecoder() instanceof TisV1Decoder) {
        final int pvrzBaseIndex = getPvrzBaseIndex();
        final int newPvrzBaseIndex = TisConvert.calcPvrzBaseIndex(getTisPath());
        if (newPvrzBaseIndex > pvrzBaseIndex) {
          if (interactive) {
            final String[] options = { "Update", "Keep", "Cancel" };
            final int result = JOptionPane.showOptionDialog(this,
                "PVRZ base index overlaps with existing files.\n\nPlease choose:\n"
                + "Update: Update to next available free index (" + newPvrzBaseIndex + ") and continue.\n"
                + "Keep: Keep selected index (" + pvrzBaseIndex + ") and overwrite existing files.\n"
                + "Cancel: Return to the dialog and adjust manually.\n ",
                "Confirm",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
            switch (result) {
              case 0:   // Adjust
                sPvrzBaseIndex.setValue(newPvrzBaseIndex);
                break;
              case 1:   // Keep
                break;
              default:  // Cancel
                sPvrzBaseIndex.requestFocus();
                return false;
            }
          } else {
            return false;
          }
        }
      }

      // Manual mode: number of secondary tiles must be less than or equal to number of primary tiles
      if (tis.getDecoder() instanceof TisV1Decoder && isManualMode()) {
        final int numPriTiles = getRowCount() * getTilesPerRow();
        final int numSecTiles = tis.getDecoder().getTileCount() - numPriTiles;
        if (numSecTiles > numPriTiles) {
          if (interactive) {
            JOptionPane.showMessageDialog(this, "Number of secondary tiles must not be greater than number\n"
                + "of primary tiles. Please adjust tileset height accordingly.", "Error", JOptionPane.ERROR_MESSAGE);
            sNumRows.requestFocus();
          }
          return false;
        }
      }

      return true;
    }

    /**
     * Returns a valid TIS file path, based on the given input file name.
     *
     * @param fileName Filename string to validate.
     * @param strict   Specifies whether the given filename must be preserved.
     * @return Validated, and possibly corrected, filename, as {@link Path} object.
     */
    private Path validateTisFileName(String fileName, boolean strict) {
      Path retVal = null;
      try {
        retVal = Paths.get(fileName);
        if (retVal.getParent() == null || !Files.exists(retVal.getParent())) {
          retVal = null;
        }
      } catch (NullPointerException | IllegalArgumentException e) {
        Logger.trace(e);
      }

      if (strict) {
        return retVal;
      }

      if (retVal == null) {
        try {
          retVal = Profile.getGameRoot().resolve(tis.getResourceEntry().getResourceName());
          if (retVal.getParent() == null || !Files.exists(retVal.getParent())) {
            retVal = null;
          }
        } catch (IllegalArgumentException e) {
          Logger.trace(e);
        }
      }

      if (retVal == null) {
        try {
          retVal = Profile.getGameRoot().resolve("OUTPUT.TIS");
        } catch (IllegalArgumentException e) {
          Logger.trace(e);
        }
      }

      return retVal;
    }

    /** Returns the {@link Path} of the output TIS file. */
    private Path getTisPath() {
      try {
        return Paths.get(tfTisFile.getText().trim());
      } catch (InvalidPathException e) {
        Logger.error(e);
      }
      return null;
    }

    /** Returns the border size, in pixels. */
    private int getBorderSize() {
      return (sBorderSize != null) ? sBorderSize.getValue() : 0;
    }

    /** Returns the segment size, in pixels. */
    private int getSegmentSize() {
      return (sSegmentSize != null) ? 1 << sSegmentSize.getValue() : 0;
    }

    /** Returns the PVRZ base index. */
    private int getPvrzBaseIndex() {
      return (sPvrzBaseIndex != null) ? sPvrzBaseIndex.getValue() : 0;
    }

    /** Returns whether black tiles should be detected and optimized. */
    private boolean isDetectBlack() {
      return cbRemoveBlack != null && cbRemoveBlack.isSelected();
    }

    /** Returns whether multithreading is used to encode DXT1 pixel data. */
    private boolean isMultithreaded() {
      return cbMultithreaded != null && cbMultithreaded.isSelected();
    }

    /**
     * Returns whether automatic configuration mode is enabled. Always returns {@code true} for PVRZ->Palette
     * conversion.
     */
    private boolean isAutoMode() {
      return rbAuto == null || rbAuto.isSelected();
    }

    /**
     * Returns whether manual configuration mode is enabled. Always returns {@code true} for PVRZ->Palette
     * conversion.
     */
    private boolean isManualMode() {
      return rbManual == null || rbManual.isSelected();
    }

    /** Returns the selected overlay conversion mode. */
    private TisConvert.OverlayConversion getOverlayConversionMode() {
      if (isAutoMode()) {
        return cbOverlayMode.getModel().getElementAt(cbOverlayMode.getSelectedIndex()).getData();
      } else {
        return TisConvert.OverlayConversion.NONE;
      }
    }

    /** Returns the number of tiles per row. */
    private int getTilesPerRow() {
      return (sTilesPerRow != null && rbManual.isSelected()) ? sTilesPerRow.getValue() : tis.slCols.getValue();
    }

    /** Returns the number of tileset rows. */
    private int getRowCount() {
      return (sNumRows != null && rbManual.isSelected()) ? sNumRows.getValue() : -1;
    }

    /** Returns the list of tile images. Returns {@code null} for palette->PVRZ conversions. */
    private List<Image> getTileList() {
      return (tis.decoder instanceof TisV2Decoder) ? tis.getTileList() : null;
    }

    /** Child class is used to implement event-related functionality. */
    private class Listeners  {
      /** WindowListener: reset "accepted" flag when dialog is shown */
      private final WindowAdapter windowDialog = new WindowAdapter() {
        public void windowOpened(WindowEvent e) { accepted = false; }
      };

      /** MouseMotionListener: updates help text */
      private final MouseMotionAdapter mouseMotion = new MouseMotionAdapter() {
        public void mouseMoved(MouseEvent e) {
          final String msg = helpMap.getOrDefault(e.getSource(), "");
          taHelp.setText(msg);
          taHelp.setCaretPosition(0);
        }
      };

      /** ActionListener: JButton bPreviewReset */
      private final ActionListener actionPreviewReset = e -> {
        if (bPreviewReset.isEnabled()) {
          if (defaultTisWidth > 0 && defaultTisHeight > 0) {
            sTilesPerRow.setValue(defaultTisWidth);
            sNumRows.setValue(defaultTisHeight);
          }
        }
      };

      /** ActionListener: JButton bOK */
      private final ActionListener actionOk = e -> {
        if (validateSettings(true)) {
          accepted = true;
          setVisible(false);
        }
      };

      /** ActionListener: JButton bCancel */
      private final ActionListener actionCancel = e -> setVisible(false);

      /** ActionListener: JButton bTisFile */
      private final ActionListener actionTisFile = e -> {
        final Path outPath = validateTisFileName(tfTisFile.getText(), false);
        final FileNameExtensionFilter tisFilter = new FileNameExtensionFilter("TIS files (*.tis)", "tis");
        final JFileChooser chooser = new JFileChooser(outPath.toFile());
        chooser.setSelectedFile(outPath.toFile());
        chooser.setDialogType(JFileChooser.SAVE_DIALOG);
        chooser.setDialogTitle("Choose output TIS file");
        chooser.addChoosableFileFilter(tisFilter);
        chooser.setFileFilter(tisFilter);
        final int retVal = chooser.showSaveDialog(ConvertTisDialog.this);
        if (retVal == JFileChooser.APPROVE_OPTION) {
          // add extension if needed
          final String tisPath = chooser.getSelectedFile().toString().trim();
          final String tisFile = chooser.getSelectedFile().getName().trim();
          final int extIndex = tisFile.indexOf('.');
          final String ext;
          if (extIndex < 0) {
            ext = ".TIS";
          } else if (extIndex == tisFile.length() - 1) {
            ext = "TIS";
          } else {
            ext = "";
          }
          tfTisFile.setText(tisPath + ext);
        }
      };

      /** ChangeListener: JSlider sBorderSize */
      private final ChangeListener changeBorderSize = e -> {
        final int size = sBorderSize.getValue();
        lBorderSize.setText(String.format(BORDER_SIZE_LABEL_FMT, size));
      };

      /** ChangeListener: JSlider sSegmentSize */
      private final ChangeListener changeSegmentSize =
          e -> lSegmentSize.setText(String.format(SEGMENT_SIZE_LABEL_FMT, getSegmentSize()));

      /** ChangeListener: JSlider: sPvrzBaseIndex */
      private final ChangeListener changePvrzBaseIndex =
          e -> lPvrzBaseIndex.setText(String.format(PVRZ_BASE_INDEX_LABEL_FMT, getPvrzBaseIndex()));

      /** ChangeListener: JSlider sTilesPerRow */
      private final ChangeListener changeTilesPerRow = e -> {
        // updating label
        final int size = sTilesPerRow.getValue();
        lTilesPerRow.setText(String.format(TILES_PER_ROW_LABEL_FMT, size));

        // updating numRows range
        final int numTiles = tis.decoder.getTileCount();
        final int numRows = numTiles / size;
        sNumRows.setMaximum(numRows);

        // updating preview
        final int width = sTilesPerRow.getValue();
        final int height = sNumRows.getValue();
        lPreviewImage.setIcon(new ImageIcon(tisPreview.get(width, height)));
      };

      /** ChangeListener: JSlider sNumRows */
      private final ChangeListener changeNumRows = e -> {
        // updating label
        final int size = sNumRows.getValue();
        lNumRows.setText(String.format(ROW_COUNT_LABEL_FMT, size));

        // updating preview
        final int width = sTilesPerRow.getValue();
        final int height = sNumRows.getValue();
        lPreviewImage.setIcon(new ImageIcon(tisPreview.get(width, height)));
      };

      /** ItemListener: JRadioButton rbAuto */
      private final ItemListener itemAuto = e -> {
        setPanelEnabled(panelSubAuto, rbAuto.isSelected());
        setPanelEnabled(panelSubManual, !rbAuto.isSelected());
      };

      /** ItemListener: JRadioButton rbManual */
      private final ItemListener itemManual = e -> {
        setPanelEnabled(panelSubAuto, !rbManual.isSelected());
        setPanelEnabled(panelSubManual, rbManual.isSelected());
      };

      private final ItemListener itemPreviewImageZoom = e -> {
        final DataString<Integer> item = cbPreviewImageZoom.getModel().getElementAt(cbPreviewImageZoom.getSelectedIndex());
        if (item != null) {
          final int zoom = item.getData();
          try {
            tisPreview.setTileSize(zoom);
          } catch (Exception ex) {
            Logger.error(ex);
            // revert zoom change (if possible)
            for (int i = 0, size = cbPreviewImageZoom.getModel().getSize(); i < size; i++) {
              final int value = cbPreviewImageZoom.getModel().getElementAt(cbPreviewImageZoom.getSelectedIndex()).getData();
              if (value == tisPreview.getTileSize()) {
                cbPreviewImageZoom.setSelectedIndex(i);
                break;
              }
            }
          }

          final int width = sTilesPerRow.getValue();
          final int height = sNumRows.getValue();
          lPreviewImage.setIcon(new ImageIcon(tisPreview.get(width, height)));
        }
      };
    }
  }
}
