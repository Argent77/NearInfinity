// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.tis;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.OutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.gui.ChildFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.converter.ConvertButtonsPanel;
import org.infinity.gui.converter.ConvertIOPanel;
import org.infinity.gui.converter.ConvertOptionsPanel;
import org.infinity.gui.converter.PanelUpdateEvent;
import org.infinity.gui.converter.PanelUpdateListener;
import org.infinity.gui.converter.pvrz.ConvertToPvrz;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.Compressor;
import org.infinity.resource.graphics.DxtEncoder;
import org.infinity.resource.graphics.TisDecoder;
import org.infinity.util.BinPack2D;
import org.infinity.util.DynamicArray;
import org.infinity.util.IntegerHashMap;
import org.infinity.util.Logger;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

/**
 * Dialog for converting graphics files into the TIS format (palette-based or pvrz-based).
 */
public class ConvertToTis extends ChildFrame implements PanelUpdateListener {
  private static final FileNameExtensionFilter OUTPUT_FILE_FILTER =
      new FileNameExtensionFilter("TIS files (*.tis)", "tis");

  private static Path currentPath = Profile.getGameRoot();

  private ConvertIOPanel ioPanel;
  private ConvertOptionsPanel optionsPanel;
  private TisOptionsPanel tisOptionsPanel;
  private ConvertButtonsPanel buttonsPanel;

  public ConvertToTis() {
    super("Convert to TIS", true);
    init();
  }

  // --------------------- Begin Class ChildFrame ---------------------

  /** Resets dialog content and optionally hides the dialog window. */
  public void hideWindow(boolean hide) {
    reset();
    if (hide) {
      setVisible(false);
    }
  }

  @Override
  protected boolean windowClosing(boolean forced) throws Exception {
    currentPath = ioPanel.getCurrentOutputDirectory();
    reset();
    return super.windowClosing(forced);
  }

  // --------------------- End Class ChildFrame ---------------------

  // --------------------- Begin Interface PanelUpdateListener ---------------------

  @Override
  public void panelUpdated(PanelUpdateEvent e) {
    if (e.getSource() == ioPanel) {
      handleIOPanel(e);
    } else if (e.getSource() == tisOptionsPanel) {
      updateInputStatus(false);
      updateStatus();
    } else if (e.getSource() == buttonsPanel) {
      handleButtonPanel(e);
    }
  }

  // --------------------- End Interface PanelUpdateListener ---------------------

  /** Handles state changes in the IO panel. */
  private void handleIOPanel(PanelUpdateEvent e) {
    if (e.getReason() == ConvertIOPanel.REASON_INPUT_CHANGED) {
      updateInputStatus(true);
    }
    updateStatus();
  }

  /** Handles state changes in the button panel. */
  private void handleButtonPanel(PanelUpdateEvent e) {
    if (e.getReason() == ConvertButtonsPanel.REASON_CONVERT) {
      convert();
    } else if (e.getReason() == ConvertButtonsPanel.REASON_CANCEL) {
      hideWindow(true);
    }
  }

  /** Resets session-specific dialog controls. */
  private void reset() {
    ioPanel.reset();
    tisOptionsPanel.reset();
  }

  /** Updates the dialog control state. */
  private void updateStatus() {
    final boolean inputSet = !ioPanel.getInputFile().isEmpty();
    final boolean outputSet = !ioPanel.getOutputFile().isEmpty();
    final boolean tileCountSet = tisOptionsPanel.getTileCount() > 0;

    buttonsPanel.setConvertButtonEnabled(inputSet && outputSet && tileCountSet);
  }

  /**
   * Updates dialog options that respond to the input file selection.
   *
   *  @param showFeedback Specify whether error messages should pop up.
   */
  private void updateInputStatus(boolean showFeedback) {
    try {
      final Path inputFile = FileManager.resolve(ioPanel.getInputFile());
      if (inputFile != null) {
        final Dimension dim = ColorConvert.getImageDimension(inputFile);
        if (dim.width > 0 && dim.height > 0) {
          final int tileDimension = tisOptionsPanel.getTileDimension();
          final boolean isValid = (dim.width % tileDimension) == 0 && (dim.height % tileDimension) == 0;
          if (isValid) {
            final int tileCount = (dim.width / tileDimension) * (dim.height / tileDimension);
            tisOptionsPanel.setMaxTileCount(tileCount);
            tisOptionsPanel.setTileCount(tisOptionsPanel.getMaxTileCount());
            return;
          } else if (showFeedback) {
            final String msg = "Image dimensions are not multiples of " + tileDimension
                + " pixels.\nImage: width=" + dim.width + ", height=" + dim.height;
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
          }
        }
      }
    } catch (InvalidPathException e) {
      Logger.trace(e);
    }

    tisOptionsPanel.setMaxTileCount(0);
  }

  /** Performs the file conversion. */
  private void convert() {
    final boolean isLegacy = tisOptionsPanel.isLegacyVersionSelected();
    final boolean closeOnExit = buttonsPanel.isCloseOnExit();

    // preparing input file
    final Path inFile = FileManager.resolve(ioPanel.getInputFile());
    if (!FileEx.create(inFile).isFile()) {
      JOptionPane.showMessageDialog(this, "Input file does not exist:\n" + inFile, "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    // preparing output file
    Path outFile = null;
    try {
      outFile = FileManager.resolve(ioPanel.getOutputFile());
    } catch (Exception e) {
      Logger.debug(e);
    }

    if (outFile == null) {
      JOptionPane.showMessageDialog(this, "Invalid output file path specified.", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    // validating naming scheme of filename
    final Path validatedOutFile = createValidTisPath(outFile, isLegacy);
    if (!outFile.getFileName().toString().equalsIgnoreCase(validatedOutFile.getFileName().toString())) {
      final String msg;
      if (isLegacy) {
        msg = "Filename for the palette-based TIS version must not be longer than 8 characters.";
      } else {
        msg = "Filename for the PVRZ-based TIS version must be 2 to 7 characters long.";
      }
      JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    final FileEx outFileEx = FileEx.create(outFile);
    if (outFileEx.isDirectory()) {
      JOptionPane.showMessageDialog(this, "Output file cannot be a directory.", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    } else if (outFileEx.exists()) {
      final int result = JOptionPane.showConfirmDialog(this, "TIS output file already exists. Overwrite?",
          "Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (result != JOptionPane.YES_OPTION) {
        return;
      }
    }

    // preparing tile count
    final int tileCount = tisOptionsPanel.getTileCount();
    final int tileDimension = tisOptionsPanel.getTileDimension();
    if (tileCount < 1) {
      JOptionPane.showMessageDialog(this, "No tiles available for conversion.", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    // performing conversion task
    try {
      final TisWorker worker = new TisWorker(this, inFile, outFile, tileCount, tileDimension, isLegacy, closeOnExit);
      worker.execute();
    } catch (Exception e) {
      Logger.error(e);
      final String msg;
      if (e.getMessage() == null || e.getMessage().isEmpty()) {
        msg = "Error: " + e.getMessage();
      } else {
        msg = e.getClass().getSimpleName() + " error occurred.";
      }
      JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void init() {
    setIconImage(Icons.ICON_APPLICATION_16.getIcon().getImage());

    final JLabel lInputNote =
        new JLabel("Note: Width and height of the source image have to be a multiple of the selected tile size.");

    ioPanel = new ConvertIOPanel("Input & Output", currentPath, ColorConvert.GRAPHICS_FILTERS_DEFAULT,
        OUTPUT_FILE_FILTER, "TIS", ConvertIOPanel.DEFAULT_SHORT_PATH_GENERATOR, lInputNote);
    ioPanel.addPanelUpdateListener(this);

    tisOptionsPanel = new TisOptionsPanel();
    tisOptionsPanel.addPanelUpdateListener(this);
    optionsPanel = new ConvertOptionsPanel("Options", tisOptionsPanel);

    buttonsPanel = new ConvertButtonsPanel();
    buttonsPanel.addPanelUpdateListener(this);

    final GridBagConstraints c = new GridBagConstraints();

    final JPanel panelMain = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    panelMain.add(ioPanel, c);
    ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 0, 0), 0, 0);
    panelMain.add(optionsPanel, c);
    ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 0, 0), 0, 0);
    panelMain.add(buttonsPanel, c);

    setLayout(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 8, 8, 8), 0, 0);
    add(panelMain, c);

    updateStatus();

    pack();
    setMinimumSize(getSize());
    setLocationRelativeTo(getParent());
    setVisible(true);
  }

  /**
   * Converts an image to a palette-based TIS file.
   *
   * @param srcImage    Source {@link Image} to convert.
   * @param outputFile  {@link Path} of the output TIS file.
   * @param tileCount   Number of tiles to convert.
   * @param minProgress Start position for the progress monitor.
   * @param worker      Optional {@link TisWorker} instance for tracking progress.
   * @return {@code true} if the conversion finished successfully, {@code false} if the conversion was cancelled.
   * @throws Exception thrown if an error occurs.
   */
  public static boolean convertV1(BufferedImage srcImage, Path outputFile, int tileCount, int minProgress,
      TisWorker worker) throws Exception {
    return convertV1(srcImage, outputFile, tileCount, TisDecoder.DEFAULT_TILE_DIMENSION, minProgress, worker);
  }

  /**
   * Converts an image to a palette-based TIS file using the specified square tile dimension.
   *
   * @param srcImage     Source {@link Image} to convert.
   * @param outputFile   {@link Path} of the output TIS file.
   * @param tileCount    Number of tiles to convert.
   * @param tileDimension Width and height of a square tile, in pixels.
   * @param minProgress  Start position for the progress monitor.
   * @param worker       Optional {@link TisWorker} instance for tracking progress.
   * @return {@code true} if the conversion finished successfully, {@code false} if the conversion was cancelled.
   * @throws Exception thrown if an error occurs.
   */
  public static boolean convertV1(BufferedImage srcImage, Path outputFile, int tileCount, int tileDimension,
      int minProgress, TisWorker worker) throws Exception {
    if (srcImage == null) {
      throw new NullPointerException("Source image is null.");
    }
    if (outputFile == null) {
      throw new NullPointerException("Output file is null.");
    }

    validateTileDimension(tileDimension);
    validateImageDimensions(srcImage.getWidth(), srcImage.getHeight(), tileDimension);

    if (tileCount < 1) {
      throw new IllegalArgumentException("Tile count cannot be 0 or negative.");
    }
    final int maxTileCount = (srcImage.getWidth() / tileDimension) * (srcImage.getHeight() / tileDimension);
    if (tileCount > maxTileCount) {
      final String msg = "Tile count exceeds max. number of tiles (" + tileCount + " > " + maxTileCount + ')';
      throw new IllegalArgumentException(msg);
    }

    final int[] srcBuffer = ((DataBufferInt)srcImage.getRaster().getDataBuffer()).getData();
    final int tileDataSize = Math.multiplyExact(tileDimension, tileDimension);
    final int tileRecordSize = Math.addExact(1024, tileDataSize);
    final byte[] dstBuffer = new byte[Math.addExact(24, Math.multiplyExact(tileCount, tileRecordSize))];
    int dstOfs = 0; // current start offset for write operations

    // writing header data
    System.arraycopy("TIS V1  ".getBytes(), 0, dstBuffer, 0, 8);
    DynamicArray.putInt(dstBuffer, 8, tileCount);
    DynamicArray.putInt(dstBuffer, 12, tileRecordSize);
    DynamicArray.putInt(dstBuffer, 16, 0x18);
    DynamicArray.putInt(dstBuffer, 20, tileDimension);
    dstOfs += 24;

    final int[] srcBlock = new int[tileDataSize]; // temp. storage for a single tile
    final int[] palette = new int[255]; // temp. storage for generated palette
    final byte[] tilePalette = new byte[1024]; // final palette for output
    final byte[] tileData = new byte[tileDataSize]; // final tile data for output
    int tw = srcImage.getWidth() / tileDimension; // tiles per row

    final int updateBlock = (tileCount > 300) ? 100 : 10;
    final IntegerHashMap<Byte> colorCache = new IntegerHashMap<>(2048); // caching RGBColor -> index
    for (int tileIdx = 0; tileIdx < tileCount; tileIdx++) {
      if (worker != null && worker.isProgressCancelled()) {
        return false;
      }

      if (worker != null && (tileIdx % updateBlock) == 0) {
        worker.setProgressNote("Tile " + tileIdx + " / " + tileCount);
        worker.advanceProgressTo(minProgress + tileIdx);
      }

      final int tx = tileIdx % tw;
      final int ty = tileIdx / tw;

      // resetting color cache
      colorCache.clear();

      // initializing source tile
      int inOfs = ty * tileDimension * srcImage.getWidth() + tx * tileDimension;
      for (int i = 0, outOfs = 0; i < tileDimension;
          i++, inOfs += srcImage.getWidth(), outOfs += tileDimension) {
        System.arraycopy(srcBuffer, inOfs, srcBlock, outOfs, tileDimension);
      }

      // reducing colors
      if (ColorConvert.medianCut(srcBlock, 255, palette, true)) {
        // filling palette and color cache, index 0 denotes transparency
        tilePalette[0] = tilePalette[2] = tilePalette[3] = 0;
        tilePalette[1] = (byte) 255;
        for (int i = 1; i < 256; i++) {
          tilePalette[(i << 2)]     = (byte)(palette[i - 1] & 0xff);
          tilePalette[(i << 2) + 1] = (byte)((palette[i - 1] >>> 8) & 0xff);
          tilePalette[(i << 2) + 2] = (byte)((palette[i - 1] >>> 16) & 0xff);
          tilePalette[(i << 2) + 3] = 0;
          colorCache.put(palette[i - 1], (byte)(i - 1));
        }

        // processing pixel data
        for (int i = 0; i < tileData.length; i++) {
          if ((srcBlock[i] & 0xff000000) == 0) {
            tileData[i] = 0;
          } else {
            final Byte palIndex = colorCache.get(srcBlock[i]);
            if (palIndex != null) {
              tileData[i] = (byte)(palIndex + 1);
            } else {
              final byte color = (byte)ColorConvert.getNearestColor(srcBlock[i], palette, 0.0, null);
              tileData[i] = (byte) (color + 1);
              colorCache.put(srcBlock[i], color);
            }
          }
        }
      } else {
        // error handling
        throw new Exception("Failed to generate color table for tile " + tileIdx);
      }

      // writing final palette and pixel data to output
      System.arraycopy(tilePalette, 0, dstBuffer, dstOfs, 1024);
      dstOfs += 1024;
      System.arraycopy(tileData, 0, dstBuffer, dstOfs, tileDataSize);
      dstOfs += tileDataSize;
    }

    // writing TIS file to disk
    try (OutputStream os = StreamUtils.getOutputStream(outputFile, true)) {
      os.write(dstBuffer);
    } catch (Exception e) {
      // error handling
      throw new Exception("Error writing TIS file to disk.", e);
    }

    return true;
  }

  /**
   * Converts an image to a pvrz-based TIS file.
   *
   * @param srcImage    Source {@link Image} to convert.
   * @param outputFile  {@link Path} of the output TIS file. PVRZ files are placed into the same directory as the output
   *                      TIS file.
   * @param tileCount   Number of tiles to convert.
   * @param minProgress Start position for the progress monitor.
   * @param worker      Optional {@link TisWorker} instance for tracking progress.
   * @return {@code true} if the conversion finished successfully, {@code false} if the conversion was cancelled.
   * @throws Exception thrown if an error occurs.
   */
  public static boolean convertV2(BufferedImage srcImage, Path outputFile, int tileCount, int minProgress,
      TisWorker worker) throws Exception {
    return convertV2(srcImage, outputFile, tileCount, TisDecoder.DEFAULT_TILE_DIMENSION, minProgress, worker);
  }

  /**
   * Converts an image to a PVRZ-based TIS file using the specified square tile dimension.
   *
   * @param srcImage     Source {@link Image} to convert.
   * @param outputFile   {@link Path} of the output TIS file. PVRZ files are placed into the same directory as the
   *                       output TIS file.
   * @param tileCount    Number of tiles to convert.
   * @param tileDimension Width and height of a square tile, in pixels.
   * @param minProgress  Start position for the progress monitor.
   * @param worker       Optional {@link TisWorker} instance for tracking progress.
   * @return {@code true} if the conversion finished successfully, {@code false} if the conversion was cancelled.
   * @throws Exception thrown if an error occurs.
   */
  public static boolean convertV2(BufferedImage srcImage, Path outputFile, int tileCount, int tileDimension,
      int minProgress, TisWorker worker) throws Exception {
    if (srcImage == null) {
      throw new NullPointerException("Source image is null.");
    }
    if (outputFile == null) {
      throw new NullPointerException("Output file is null.");
    }

    validateTileDimension(tileDimension);
    validateImageDimensions(srcImage.getWidth(), srcImage.getHeight(), tileDimension);

    if (tileCount < 1) {
      throw new IllegalArgumentException("Tile count cannot be 0 or negative.");
    }
    final int maxTileCount = (srcImage.getWidth() / tileDimension) * (srcImage.getHeight() / tileDimension);
    if (tileCount > maxTileCount) {
      final String msg = "Tile count exceeds max. number of tiles (" + tileCount + " > " + maxTileCount + ')';
      throw new IllegalArgumentException(msg);
    }

    final List<BinPack2D> pageList = new ArrayList<>();
    final List<TileEntry> entryList = new ArrayList<>(tileCount);

    final byte[] dstBuffer = new byte[24 + tileCount * 12]; // header + tiles
    int dstOfs = 0; // current start offset for write operations

    // writing header data
    System.arraycopy("TIS V1  ".getBytes(), 0, dstBuffer, 0, 8);
    DynamicArray.putInt(dstBuffer, 8, tileCount);
    DynamicArray.putInt(dstBuffer, 12, 0x0c);
    DynamicArray.putInt(dstBuffer, 16, 0x18);
    DynamicArray.putInt(dstBuffer, 20, tileDimension);
    dstOfs += 24;

    // processing tiles
    generatePageInfo(srcImage.getWidth(), srcImage.getHeight(), tileCount, tileDimension, pageList, entryList);

    // writing TIS entries
    entryList.sort(TileEntry.COMPARE_BY_INDEX);
    for (int i = 0; i < entryList.size(); i++, dstOfs += 12) {
      final TileEntry entry = entryList.get(i);
      DynamicArray.putInt(dstBuffer, dstOfs, entry.page);
      DynamicArray.putInt(dstBuffer, dstOfs + 4, entry.x);
      DynamicArray.putInt(dstBuffer, dstOfs + 8, entry.y);
    }

    // writing TIS file to disk
    try (OutputStream os = StreamUtils.getOutputStream(outputFile, true)) {
      os.write(dstBuffer);
    } catch (Exception e) {
      // error handling
      throw new Exception("Error writing TIS file to disk.", e);
    }

    // generating PVRZ files
    return createPvrzPages(outputFile, srcImage, pageList, DxtEncoder.DxtType.DXT1, entryList, tileDimension,
        minProgress, worker);
  }

  /**
   * Generates PVRZ page data that can be used to generate PVRZ files from the given source image.
   *
   * @param imageWidth  Source image width.
   * @param imageHeight Source image height.
   * @param tileCount   Number of tiles to convert.
   * @param pageList    Empty list where {@link BinPack2D} structures are stored by this method.
   * @param entryList   Empty list where {@link TileEntry} structures are stored by this method.
   * @return Number of generated pvrz page entries.
   * @throws Exception if an error occurs.
   */
  public static int generatePageInfo(int imageWidth, int imageHeight, int tileCount, List<BinPack2D> pageList,
      List<TileEntry> entryList) throws Exception {
    return generatePageInfo(imageWidth, imageHeight, tileCount, TisDecoder.DEFAULT_TILE_DIMENSION, pageList,
        entryList);
  }

  /**
   * Generates PVRZ page data using the specified square tile dimension.
   *
   * @param imageWidth   Source image width.
   * @param imageHeight  Source image height.
   * @param tileCount    Number of tiles to convert.
   * @param tileDimension Width and height of a square tile, in pixels.
   * @param pageList     Empty list where {@link BinPack2D} structures are stored by this method.
   * @param entryList    Empty list where {@link TileEntry} structures are stored by this method.
   * @return Number of generated PVRZ page entries.
   * @throws Exception if an error occurs.
   */
  public static int generatePageInfo(int imageWidth, int imageHeight, int tileCount, int tileDimension,
      List<BinPack2D> pageList, List<TileEntry> entryList) throws Exception {
    validateTileDimension(tileDimension);
    validateImageDimensions(imageWidth, imageHeight, tileDimension);

    if (pageList == null) {
      pageList = new ArrayList<>();
    }

    if (entryList == null) {
      entryList = new ArrayList<>();
    }

    final BinPack2D.HeuristicRules binPackRule = BinPack2D.HeuristicRules.BOTTOM_LEFT_RULE;
    final int pageDim = 1024;
    final int tilesPerDim = pageDim / tileDimension;
    final int pw = imageWidth / pageDim + (((imageWidth % pageDim) != 0) ? 1 : 0);
    final int ph = imageHeight / pageDim + (((imageHeight % pageDim) != 0) ? 1 : 0);

    for (int py = 0; py < ph; py++) {
      for (int px = 0; px < pw; px++) {
        final int x = px * pageDim, y = py * pageDim;
        final int w = Math.min(pageDim, imageWidth - x);
        final int h = Math.min(pageDim, imageHeight - y);

        final Dimension space = new Dimension(w / tileDimension, h / tileDimension);
        int pageIdx = -1;
        Rectangle rectMatch = null;
        for (int i = 0; i < pageList.size(); i++) {
          final BinPack2D packer = pageList.get(i);
          rectMatch = packer.insert(space.width, space.height, binPackRule);
          if (rectMatch.height > 0) {
            pageIdx = i;
            break;
          }
        }

        // create new page?
        if (pageIdx < 0) {
          final BinPack2D packer = new BinPack2D(tilesPerDim, tilesPerDim);
          pageList.add(packer);
          pageIdx = pageList.size() - 1;
          rectMatch = packer.insert(space.width, space.height, binPackRule);
        }

        // registering tile entries
        int tileIdx = (y * imageWidth) / (tileDimension * tileDimension) + x / tileDimension;
        for (int ty = 0; ty < space.height; ty++, tileIdx += imageWidth / tileDimension) {
          for (int tx = 0; tx < space.width; tx++) {
            // marking page index as incomplete
            if (tileIdx + tx < tileCount) {
              TileEntry entry = new TileEntry(tileIdx + tx, pageIdx, (rectMatch.x + tx) * tileDimension,
                  (rectMatch.y + ty) * tileDimension);
              entryList.add(entry);
            }
          }
        }
      }
    }

    return pageList.size();
  }

  /**
   * Creates PVRZ files according to the specified parameters.
   *
   * @param tisFile     TIS file to create the PVRZ files for.
   * @param srcImage    Source {@link BufferedImage} to convert.
   * @param pageList    List of {@link BinPack2D} structures with page layout information.
   * @param type        Compression type.
   * @param entryList   List of tile information.
   * @param minProgress Start position for the progress monitor.
   * @param worker      Optional {@link TisWorker} instance for tracking progress.
   * @return {@code true} if the conversion finished successfully, {@code false} if the conversion was cancelled.
   * @throws Exception thrown if an error occurs.
   */
  private static boolean createPvrzPages(Path tisFile, BufferedImage srcImage, List<BinPack2D> pageList,
      DxtEncoder.DxtType dxtType, List<TileEntry> entryList, int tileDimension, int minProgress, TisWorker worker)
      throws Exception {
    final int dxtCode = (dxtType == DxtEncoder.DxtType.DXT5) ? 11 : 7;
    final byte[] output = new byte[DxtEncoder.calcImageSize(1024, 1024, dxtType)];

    for (int pageIdx = 0, pageCount = pageList.size(); pageIdx < pageCount; pageIdx++) {
      if (worker != null && worker.isProgressCancelled()) {
        return false;
      }

      if (worker != null) {
        worker.setProgressNote("PVRZ file " + (pageIdx + 1) + " / " + pageCount);
        worker.advanceProgressTo(minProgress + pageIdx);
      }

      final Path pvrzFile = generatePvrzName(tisFile, pageIdx);
      final BinPack2D packer = pageList.get(pageIdx);
      packer.shrinkBin(true);

      // generating texture image
      int w = packer.getBinWidth() * tileDimension;
      int h = packer.getBinHeight() * tileDimension;
      final BufferedImage texture = ColorConvert.createCompatibleImage(w, h, true);
      final Graphics2D g = texture.createGraphics();
      g.setComposite(AlphaComposite.Src);
      g.setColor(ColorConvert.TRANSPARENT_COLOR);
      g.fillRect(0, 0, texture.getWidth(), texture.getHeight());
      int tw = srcImage.getWidth() / tileDimension;
      for (final TileEntry entry : entryList) {
        if (entry.page == pageIdx) {
          int sx = (entry.tileIndex % tw) * tileDimension;
          int sy = (entry.tileIndex / tw) * tileDimension;
          int dx = entry.x, dy = entry.y;
          g.fillRect(dx, dy, tileDimension, tileDimension);
          g.drawImage(srcImage, dx, dy, dx + tileDimension, dy + tileDimension, sx, sy, sx + tileDimension,
              sy + tileDimension, null);
        }
      }
      g.dispose();

      final int[] textureData = ((DataBufferInt)texture.getRaster().getDataBuffer()).getData();
      byte[] pvrz = null;
      try {
        // compressing PVRZ
        final int outSize = DxtEncoder.calcImageSize(texture.getWidth(), texture.getHeight(), dxtType);
        DxtEncoder.encodeImage(textureData, texture.getWidth(), texture.getHeight(), output, dxtType);
        byte[] header = ConvertToPvrz.createPVRHeader(texture.getWidth(), texture.getHeight(), dxtCode);
        pvrz = new byte[header.length + outSize];
        System.arraycopy(header, 0, pvrz, 0, header.length);
        System.arraycopy(output, 0, pvrz, header.length, outSize);
        pvrz = Compressor.compress(pvrz, 0, pvrz.length, true);
      } catch (Exception e) {
        throw new Exception("Error while generating PVRZ files:\n" + e.getMessage());
      }

      // writing PVRZ to disk
      try (OutputStream os = StreamUtils.getOutputStream(pvrzFile, true)) {
        os.write(pvrz);
      } catch (Exception e) {
        // critical error
        throw new Exception("Error writing PVRZ file to disk:\n" + pvrzFile, e);
      }
    }

    return true;
  }

  private static void validateTileDimension(int tileDimension) {
    if (tileDimension < 4 || tileDimension > TisDecoder.MAX_TILE_DIMENSION
        || (tileDimension & (tileDimension - 1)) != 0) {
      throw new IllegalArgumentException("Tile dimension must be a power of two between 4 and "
          + TisDecoder.MAX_TILE_DIMENSION + " pixels: " + tileDimension);
    }
  }

  private static void validateImageDimensions(int imageWidth, int imageHeight, int tileDimension) {
    if ((imageWidth % tileDimension) != 0 || (imageHeight % tileDimension) != 0) {
      throw new IllegalArgumentException("Source image dimensions must be a multiple of " + tileDimension
          + ".\nImage: width=" + imageWidth + ", height=" + imageHeight);
    }
  }

  /**
   * Generates a PVRZ file path for the specified TIS file.
   *
   * @param tisFile TIS file {@link Path}.
   * @param page    PVRZ page index.
   * @return PVRZ file {@link Path}.
   * @throws Exception if file path could not be generated.
   */
  private static Path generatePvrzName(Path tisFile, int page) throws Exception {
    if (tisFile == null) {
      throw new NullPointerException("tisFile is null.");
    }
    if (page < 0 || page >= 100) {
      throw new IllegalArgumentException("Page index is out of bounds: " + page);
    }

    final Path parentPath = tisFile.getParent();
    final String tisNameExt;
    String tisNameBase = tisFile.getFileName().toString();
    final int extPos = tisNameBase.lastIndexOf('.');
    if (extPos >= 0) {
      tisNameExt = tisNameBase.substring(extPos);
      tisNameBase = tisNameBase.substring(0, extPos);
    } else {
      tisNameExt = ".TIS";
    }
    if (tisNameBase.length() < 2 || tisNameBase.length() > 7) {
      throw new IllegalArgumentException("Filename for the PVRZ-based TIS version must be 2 to 7 characters long.");
    }

    final String pvrzNameExt = (Character.isLowerCase(tisNameExt.charAt(tisNameExt.length() - 1))) ? ".pvrz" : ".PVRZ";
    final String pvrzName = tisNameBase.charAt(0) + tisNameBase.substring(2) + String.format("%02d", page) + pvrzNameExt;
    final Path pvrzPath;
    if (parentPath != null) {
      pvrzPath = parentPath.resolve(pvrzName);
    } else {
      pvrzPath = FileManager.resolve(pvrzName);
    }

    return pvrzPath;
  }

  /**
   * Returns a valid TIS file path based on the parameters.
   *
   * @param tisFile  The TIS file to validate.
   * @param isLegacy Specify {@code true} to validate filename for palette-based TIS, {@code false} to validate filename
   *                   for pvrz-based TIS.
   * @return A valid TIS file {@link Path}.
   * @throws NullPointerException if {@code tisFile} is {@code null}.
   */
  static Path createValidTisPath(Path tisFile, boolean isLegacy) {
    if (tisFile == null) {
      throw new NullPointerException("tisFile is null");
    }

    // splitting path into parent path, filebase and extension
    final Path outParent = tisFile.getParent();
    String tisName = tisFile.getFileName().toString();
    final String tisExt;
    if (tisName.isEmpty() || tisName.charAt(0) == '.') {
      tisName = "OUTPUT";
    }
    final int pos = tisName.lastIndexOf('.');
    if (pos > 0) {
      tisExt = tisName.substring(pos);
      tisName = tisName.substring(0, pos);
    } else {
      tisExt = ".TIS";
    }

    if (isLegacy) {
      // palette-based TIS: filename length: 1 - 8 characters
      if (tisName.length() > 8) {
        tisName = tisName.substring(0, 8);
      }
    } else {
      // pvrz-based TIS: filename length: 2 - 7 characters
      while (tisName.length() < 2) {
        tisName += '0';
      }
      if (tisName.length() > 7) {
        tisName = tisName.substring(0, 7);
      }
    }

    // assembling output path
    final Path outPath;
    if (outParent != null) {
      outPath = outParent.resolve(tisName + tisExt);
    } else {
      outPath = Paths.get(tisName + tisExt);
    }

    return outPath;
  }


  // -------------------------- INNER CLASSES --------------------------

}
