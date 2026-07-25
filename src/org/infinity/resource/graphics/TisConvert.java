// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import org.infinity.NearInfinity;
import org.infinity.datatype.IsNumeric;
import org.infinity.gui.converter.pvrz.ConvertToPvrz;
import org.infinity.gui.converter.tis.TileEntry;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.wed.Door;
import org.infinity.resource.wed.Overlay;
import org.infinity.resource.wed.Tilemap;
import org.infinity.resource.wed.WedResource;
import org.infinity.util.BinPack2D;
import org.infinity.util.DynamicArray;
import org.infinity.util.IntegerHashMap;
import org.infinity.util.Logger;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.StreamUtils;
import org.infinity.util.tuples.Couple;

/**
 * This utility class provides methods for converting tileset (TIS) resources.
 */
public class TisConvert {
  /** A functional interface for performing operations that modify the tileset layout. */
  public interface OverlayMapUpdater {
    // TODO: redesign parameter configuration of the method
    void update(WedInfo wedInfo);
  }

  /** A functional interface for manipulating pixel data on individual tiles. */
  public interface OverlayTileConverter {
    /**
     * Performs conversion operations on the specified tile graphics.
     *
     * @param tileIndex Unique index of the tile.
     * @param tileImage Tile graphics to update as {@link BufferedImage}.
     * @param decoder   {@link TisDecoder} instance.
     * @param tileInfo {@link TileInfo} object with tile-specific information.
     * @return The updated tile graphics. May return the updated {@code tileImage} instance or a new
     *         {@link BufferedImage} object.
     * @throws Exception thrown if the operation could not be performed.
     */
    BufferedImage convert(int tileIndex, BufferedImage tileImage, TisDecoder decoder, TileInfo tileInfo) throws Exception;

    /**
     * Helper method that determines and prepares images of the primary and secondary tiles.
     *
     * @param tileIndex Index of the active tile.
     * @param tileImage {@link BufferedImage} of the active tile.
     * @param decoder   {@link TisDecoder} instance.
     * @param tileInfo  {@link TileInfo} object with tile information.
     * @return A tuple with the primary tile image in the first slot and the secondary tile image in the second slot.
     * @throws Exception If the tile images could not be prepared.
     */
    static Couple<BufferedImage, BufferedImage> init(int tileIndex, BufferedImage tileImage, TisDecoder decoder,
        TileInfo tileInfo) throws Exception {
      final int tileWidth = decoder.getTileWidth();
      final int tileHeight = decoder.getTileHeight();
      final boolean isPrimary = (tileInfo.getPrimaryTileFrame(tileIndex) >= 0);

      final BufferedImage tileImage2 = ColorConvert.createCompatibleImage(tileWidth, tileHeight, true);
      final Couple<BufferedImage, BufferedImage> retVal = new Couple<>(null, null);
      if (isPrimary) {
        decoder.getTile(tileInfo.tileSecondary, tileImage2);
        retVal.setValue0(tileImage);
        retVal.setValue1(tileImage2);
      } else {
        decoder.getTile(tileInfo.tilePrimary, tileImage2);
        retVal.setValue0(tileImage2);
        retVal.setValue1(tileImage);
      }

      return retVal;
    }
  }

  /** Status definitions used by conversion routines. */
  public enum Status {
    /** Conversion completed successfully. */
    SUCCESS,
    /** Conversion was cancelled by the user. */
    CANCELLED,
    /** Conversion failed with an error. */
    ERROR,
    /** Conversion does not meet the requirements. */
    UNSUPPORTED
  }

  /** List of available tileset overlay conversion modes. */
  public enum OverlayConversion {
    /** Do not convert tileset overlays. */
    NONE("No conversion", overlayUpdaterDefault, overlayConverterDefault, true),
    /** Convert BG1 tileset overlays using BGEE-style. */
    BG1_TO_BGEE("BG1 -> BGEE", overlayUpdaterBG1toBGEE, overlayConverterBG1toBGEE, false),
    /** Convert BG1 tileset overlays using BG2EE-style. */
    BG1_TO_BG2EE("BG1 -> BG2EE", overlayUpdaterBG1toBG2EE, overlayConverterBG1toBG2EE, false),
    /** Convert BG2 tileset overlays using BGEE-style. */
    BG2_TO_BGEE("BG2 -> BGEE", overlayUpdaterBG2toBGEE, overlayConverterBG2toBGEE, false),
    /** Convert BG2 tileset overlays using BG2EE-style. */
    BG2_TO_BG2EE("BG2 -> BG2EE", overlayUpdaterDefault, overlayConverterBG2toBG2EE, true),

    /** Convert BGEE tileset overlays using BG1-style. */
    BGEE_TO_BG1("BGEE -> BG1", overlayUpdaterBGEEtoBG1, overlayConverterBGEEtoBG1, false),
    /** Convert BGEE tileset overlays using BG2-style. */
    BGEE_TO_BG2("BGEE -> BG2", overlayUpdaterBGEEtoBG2, overlayConverterBGEEtoBG2, false),
    /** Convert BG2EE tileset overlays using BG1-style. */
    BG2EE_TO_BG1("BG2EE -> BG1", overlayUpdaterBG2EEtoBG1, overlayConverterBG2EEtoBG1, false),
    /** Convert BG2EE tileset overlays using BG2-style. */
    BG2EE_TO_BG2("BG2EE -> BG2", overlayUpdaterDefault, overlayConverterBG2EEtoBG2, true),
    ;

    private final String label;
    private final boolean implemented;
    private final OverlayMapUpdater updater;
    private final OverlayTileConverter converter;

    OverlayConversion(String label, OverlayMapUpdater updater, OverlayTileConverter converter,
        boolean implemented) {
      this.label = label;
      this.implemented = implemented;
      this.updater = Objects.requireNonNull(updater);
      this.converter = Objects.requireNonNull(converter);
    }

    /** Returns whether the conversion has been implemented. */
    public boolean isImplemented() {
      return implemented;
    }

    /** Returns a reference to the {@link OverlayMapUpdater} instance. */
    private OverlayMapUpdater getUpdater() {
      return updater;
    }

    /** Returns a reference to the {@link OverlayTileConverter} instance. */
    private OverlayTileConverter getConverter() {
      return converter;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /**
   * Composes an image from a list of tiles and exports it as PNG file.
   *
   * @param tiles List of tiles, ordered left-to-right, to-to-bottom.
   * @param tileCols Number of tiles per row.
   * @param pngFile {@link Path} of the PNG output file.
   * @return {@link Status} that indicates the completion state of the conversion.
   */
  public static Status exportPNG(List<Image> tiles, int tileCols, Path pngFile) {
    return exportPNG(tiles, tileCols, pngFile, false, null);
  }

  /**
   * Composes an image from a list of tiles and exports it as PNG file.
   *
   * @param tiles List of tiles, ordered left-to-right, to-to-bottom.
   * @param tileCols Number of tiles per row.
   * @param pngFile {@link Path} of the PNG output file.
   * @param showProgress Indicates whether a progress dialog is shown during the conversion process.
   * @param parent Parent component of the progress dialog.
   * @return {@link Status} that indicates the completion state of the conversion.
   */
  public static Status exportPNG(List<Image> tiles, int tileCols, Path pngFile, boolean showProgress, Component parent) {
    Status retVal = Status.ERROR;
    if (pngFile == null || tiles == null || tiles.isEmpty() || tileCols < 1) {
      return retVal;
    }

    int tileRows = (tiles.size() + tileCols - 1) / tileCols;

    if (showProgress && parent == null) {
      parent = NearInfinity.getInstance();
    }

    if (tileCols > 0 && tileRows > 0) {
      BufferedImage image;
      ProgressMonitor progress = null;
      if (showProgress) {
        progress = new ProgressMonitor(parent, "Exporting TIS to PNG...", "", 0, 2);
        progress.setMillisToDecideToPopup(0);
        progress.setMillisToPopup(0);
        progress.setProgress(0);
      }

      final Image referenceTile = tiles.stream().filter(Objects::nonNull).findFirst().orElse(null);
      if (referenceTile == null) {
        return retVal;
      }
      final int tileWidth = referenceTile.getWidth(null);
      final int tileHeight = referenceTile.getHeight(null);
      if (tileWidth <= 0 || tileHeight <= 0) {
        return retVal;
      }
      final boolean dimensionsValid = tiles.stream().filter(Objects::nonNull)
          .allMatch(tile -> tile.getWidth(null) == tileWidth && tile.getHeight(null) == tileHeight);
      if (!dimensionsValid) {
        return retVal;
      }
      image = ColorConvert.createCompatibleImage(tileCols * tileWidth, tileRows * tileHeight, Transparency.BITMASK);
      Graphics2D g = image.createGraphics();
      for (int idx = 0; idx < tiles.size(); idx++) {
        if (tiles.get(idx) != null) {
          int tx = idx % tileCols;
          int ty = idx / tileCols;
          g.drawImage(tiles.get(idx), tx * tileWidth, ty * tileHeight, null);
        }
      }
      g.dispose();

      if (showProgress) {
        progress.setProgress(1);
      }

      try (OutputStream os = StreamUtils.getOutputStream(pngFile, true)) {
        if (ImageIO.write(image, "png", os)) {
          retVal = Status.SUCCESS;
        }
      } catch (IOException e) {
        retVal = Status.ERROR;
        Logger.error(e);
      }

      if (showProgress && progress.isCanceled()) {
        retVal = Status.CANCELLED;
      }

      if (showProgress) {
        progress.close();
        progress = null;
      }
    }

    if (retVal != Status.SUCCESS && FileEx.create(pngFile).isFile()) {
      try {
        Files.delete(pngFile);
      } catch (IOException e) {
        Logger.error(e);
      }
    }

    return retVal;
  }

  /**
   * Checks whether the given TIS filename can be used to generate PVRZ filenames from.
   *
   * @param fileName TIS filename.
   * @return {@code true} if the TIS filename is valid for PVRZ file generation, {@code false} otherwise.
   */
  public static boolean isTisFileNameValid(Path fileName) {
    if (fileName != null) {
      String name = fileName.getFileName().toString();
      int extOfs = name.lastIndexOf('.');
      if (extOfs >= 0) {
        name = name.substring(0, extOfs);
      }
      return Pattern.matches(".{2,7}", name);
    }
    return false;
  }

  /**
   * Attempts to fix the given filename to make it compatible with the naming scheme of PVRZ-based TIS files.
   *
   * @param fileName TIS filename.
   * @return TIS filename that resolves to a valid PVRZ filename scheme.
   */
  public static Path makeTisFileNameValid(Path fileName) {
    if (fileName != null && !isTisFileNameValid(fileName)) {
      Path path = fileName.getParent();
      String name = fileName.getFileName().toString();
      String ext = "";
      int extOfs = name.lastIndexOf('.');
      if (extOfs >= 0) {
        ext = name.substring(extOfs);
        name = name.substring(0, extOfs);
      }

      boolean isNight = (Character.toUpperCase(name.charAt(name.length() - 1)) == 'N');
      if (name.length() > 7) {
        int numDelete = name.length() - 7;
        int ofsDelete = name.length() - numDelete - (isNight ? 1 : 0);
        name = name.substring(ofsDelete, numDelete);
        return path.resolve(name);
      } else if (name.length() < 2) {
        String fmt, newName = null;
        int maxNum;
        if (name.isEmpty()) {
          fmt = name + "%s02d";
          maxNum = 99;
        } else {
          fmt = name + "%s01d";
          maxNum = 9;
        }
        for (int i = 0; i < maxNum; i++) {
          String s = String.format(fmt, i) + (isNight ? "N" : "") + ext;
          if (!ResourceFactory.resourceExists(s)) {
            newName = s;
            break;
          }
        }
        if (newName != null) {
          return path.resolve(newName);
        }
      }
    }
    return fileName;
  }

  /**
   * Attempts to find and return a WED resource that references the given TIS resource.
   *
   * @param tisEntry   {@link ResourceEntry} instance of the TIS resource.
   * @param deepSearch Specify {@code false} to only check for a WED file of same resref as the given TIS
   *                     resource.Specify {@code true} to check all available WED files for a match.
   * @return {@link ResourceEntry} to the WED resource that references {@code tisEntry}. Returns {@code null} if not
   *         found.
   */
  public static ResourceEntry findWed(ResourceEntry tisEntry, boolean deepSearch) {
    if (tisEntry == null) {
      return null;
    }

    final Predicate<ResourceEntry> wedCheck = wedEntry -> {
      if (wedEntry != null) {
        try {
          final boolean ignoreOverride = tisEntry instanceof BIFFResourceEntry;
          final int[] resInfo = tisEntry.getResourceInfo(ignoreOverride);
          int numTiles = (resInfo != null && resInfo.length > 1) ? resInfo[0] : 0;
          if (numTiles > 0) {
            final ByteBuffer buf = wedEntry.getResourceBuffer().order(ByteOrder.LITTLE_ENDIAN);
            final String sig = StreamUtils.readString(buf, 0, 8);
            if ("WED V1.3".equals(sig) && buf.getInt(0x08) > 0) {
              final int ofsOvl = buf.getInt(0x10);
              final int w = buf.getShort(ofsOvl);
              final int h = buf.getShort(ofsOvl + 2);
              final String tisRef = StreamUtils.readString(buf, ofsOvl + 4, 8);
              return (numTiles >= w*h && tisRef != null && tisRef.equalsIgnoreCase(tisEntry.getResourceRef()));
            }
          }
        } catch (IOException e) {
          // do nothing
        } catch (Exception e) {
          Logger.warn("{}: {}", e.getClass().getName(), e.getMessage());
        }
      }
      return false;
    };

    // trying WED of same name first
    ResourceEntry wedEntry = ResourceFactory.getResourceEntry(tisEntry.getResourceRef() + ".WED");
    boolean retVal = wedCheck.test(wedEntry);

    // searching WED
    if (!retVal && deepSearch) {
      final List<ResourceEntry> wedEntries = ResourceFactory.getResources("WED");
      wedEntry = wedEntries
          .parallelStream()
          .filter(wedCheck)
          .findAny()
          .orElse(null);
    }

    return wedEntry;
  }

  /**
   * Attempts to find and load the WED resource associated with the specified TIS resource.
   *
   * @param tisEntry   The TIS resource entry.
   * @param deepSearch Specify {@code false} to only check for a WED file of same resref as the given TIS
   *                     resource.Specify {@code true} to check all available WED files for a match.
   * @return {@code WedResource} instance if successful, {@code null} otherwise.
   */
  public static WedResource loadWedForTis(ResourceEntry tisEntry, boolean deepSearch) {
    WedResource wed = null;

    final ResourceEntry wedEntry = findWed(tisEntry, deepSearch);
    if (wedEntry != null) {
      try {
        wed = new WedResource(wedEntry);
      } catch (Exception e) {
        // ignored
      }
    }

    return wed;
  }

  /**
   * Attempts to calculate the TIS width from an associated WED file.
   *
   * @param entry      {@code ResourceEntry} for a TIS or WED resource.
   * @param deepSearch Specify {@code false} to only check for a WED file of same resref as the given TIS
   *                     resource.Specify {@code true} to check all available WED files for a match.
   * @param tileCount  An optional tile count that will be used to "guess" the correct number of tiles per row if no
   *                     associated WED resource has been found.
   * @return The number of tiles per row for the specified TIS resource.
   */
  public static int calcTilesetWidth(ResourceEntry entry, boolean deepSearch, int tileCount) {
    // Try to fetch the correct width from an associated WED if available
    if (entry != null) {
      final ResourceEntry wedEntry = (entry.getExtension().equalsIgnoreCase("wed")) ? entry : findWed(entry, deepSearch);
      if (wedEntry != null) {
        try {
          final ByteBuffer buf = wedEntry.getResourceBuffer().order(ByteOrder.LITTLE_ENDIAN);
          final int ofsOvl = buf.getInt(0x10);
          final int w = buf.getShort(ofsOvl);
          if (w > 0) {
            return w;
          }
        } catch (Exception e) {
          // ignored
        }
      }
    }

    // If WED is not available: approximate the most commonly used aspect ratio found in TIS files
    // Disadvantage: does not take extra tiles into account
    return (tileCount < 9) ? tileCount : (int) (Math.sqrt(tileCount) * 1.18);
  }

  /**
   * Returns the first available PVRZ filename page index in the path of the specified {@code tisFile}.
   *
   * @param tisFile {@link Path} of the output TIS file.
   * @return PVRZ base index for the specified TIS file. Returns {@code 0} if page index could not be determined.
   */
  public static int calcPvrzBaseIndex(Path tisFile) {
    int retVal = 0;
    if (tisFile != null) {
      for (int i = 0; i < 100; i++) {
        final Path pvrzFile = generatePvrzFileName(tisFile, i);
        if (!Files.exists(pvrzFile)) {
          retVal = i;
          break;
        }
      }
    }
    return retVal;
  }

  /**
   * Attempts to determine the movement type of the tileset from the associated WED resource.
   *
   * @param entry      {@code ResourceEntry} for a TIS or WED resource.
   * @param deepSearch Specify {@code false} to only check for a WED file of same resref as the given TIS
   *                     resource.Specify {@code true} to check all available WED files for a match.
   * @return The movement type value for EE games and if a WED resource was found. Otherwise, {@code 0} is returned.
   */
  public static int getTisMovementType(ResourceEntry entry, boolean deepSearch) {
    int retVal = 0;

    if (!Profile.isEnhancedEdition()) {
      return retVal;
    }

    if (entry != null) {
      final ResourceEntry wedEntry = (entry.getExtension().equalsIgnoreCase("wed")) ? entry : findWed(entry, deepSearch);
      if (wedEntry != null) {
        try {
          final ByteBuffer buf = wedEntry.getResourceBuffer().order(ByteOrder.LITTLE_ENDIAN);
          final int ofsOvl = buf.getInt(0x10);
          retVal = buf.getShort(ofsOvl + 0x0e);
        } catch (Exception e) {
          // ignored
        }
      }
    }

    return retVal;
  }

  /**
   * Converts the given tileset into the palette-based variant.
   *
   * @param config       {@link Config} instance with global conversion parameters.
   * @param showProgress Indicates whether a progress dialog should be displayed.
   * @param parent       Parent component for the the progress dialog.
   * @return A status value that indicates success of the conversion operation.
   */
  public static Status convertToPaletteTis(Config config, boolean showProgress, Component parent) {
    Status retVal = Status.ERROR;
    if (config == null) {
      return retVal;
    }

    if (showProgress && parent == null) {
      parent = NearInfinity.getInstance();
    }

    final String fmtNote = "Converting tile %d / %d";
    final int progressMax = config.getDecoder().getTileCount();
    int progressIndex = 0;
    final ProgressMonitor progress;
    if (showProgress) {
      progress = new ProgressMonitor(parent, "Converting TIS...", String.format(fmtNote, progressIndex, progressMax),
          0, progressMax);
      progress.setMillisToDecideToPopup(250);
      progress.setMillisToPopup(1000);
    } else {
      progress = null;
    }

    final List<Image> tiles = config.getTileList();
    final TisV2Decoder decoder = (TisV2Decoder) config.getDecoder();
    final int tileWidth = decoder.getTileWidth();
    final int tileHeight = decoder.getTileHeight();
    final WedInfo wedInfo = config.getWedInfo();
    final OverlayConversion conversionMode = config.getOverlayConversion();

    if (conversionMode.isImplemented()) {
      conversionMode.getUpdater().update(wedInfo);
    }

    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(config.getTisFile()))) {
      retVal = Status.SUCCESS;

      // writing TIS header
      final byte[] header = new byte[24];
      System.arraycopy("TIS V1  ".getBytes(), 0, header, 0, 8);
      // TODO: use different tile count source when implementing overlay conversion modes that change tileset layout
      DynamicArray.putInt(header, 0x08, decoder.getTileCount());
      DynamicArray.putInt(header, 0x0c, 0x400 + tileWidth * tileHeight);
      DynamicArray.putInt(header, 0x10, 0x18);
      DynamicArray.putInt(header, 0x14, tileWidth);
      bos.write(header);

      // processing TIS data
      final int[] palette = new int[255];
      final byte[] tilePalette = new byte[256 * 4];
      final byte[] tileData = new byte[tileWidth * tileHeight];
      BufferedImage tileImageOut =
          ColorConvert.createCompatibleImage(tileWidth, tileHeight, Transparency.BITMASK);
      final IntegerHashMap<Byte> colorCache = new IntegerHashMap<>(1800); // caching RGB -> index
      for (int tileIdx = 0, tileCount = decoder.getTileCount(); tileIdx < tileCount; tileIdx++) {
        colorCache.clear();

        if (showProgress) {
          progressIndex++;
          if ((progressIndex % 100) == 0) {
            final int curProgressIndex = progressIndex;
            SwingUtilities.invokeLater(() -> {
              progress.setProgress(curProgressIndex);
              progress.setNote(String.format(fmtNote, curProgressIndex, progressMax));
            });
          }
          if (progress.isCanceled()) {
            return Status.CANCELLED;
          }
        }

        final Graphics2D g = tileImageOut.createGraphics();
        try {
          g.setComposite(AlphaComposite.Src);
          g.drawImage(tiles.get(tileIdx), 0, 0, null);
        } finally {
          g.dispose();
        }

        // overlay conversion
        if (conversionMode.isImplemented()) {
          final Point tileLocation = wedInfo.getTileLocation(tileIdx);
          if (tileLocation != null) {
            final int priTileIdx = tileLocation.y * wedInfo.getWidth() + tileLocation.x;
            final TileInfo tileInfo = wedInfo.getTile(priTileIdx);
            tileImageOut = conversionMode.getConverter().convert(tileIdx, tileImageOut, decoder, tileInfo);
          }
        }

        int[] pixels = ((DataBufferInt) tileImageOut.getRaster().getDataBuffer()).getData();
        if (ColorConvert.medianCut(pixels, 255, palette, true)) {
          // filling palette
          // first palette entry denotes transparency
          tilePalette[0] = tilePalette[2] = tilePalette[3] = 0;
          tilePalette[1] = (byte) 255;
          for (int i = 1; i < 256; i++) {
            tilePalette[(i << 2)]     = (byte) (palette[i - 1] & 0xff);
            tilePalette[(i << 2) + 1] = (byte) ((palette[i - 1] >>> 8) & 0xff);
            tilePalette[(i << 2) + 2] = (byte) ((palette[i - 1] >>> 16) & 0xff);
            tilePalette[(i << 2) + 3] = 0;
            colorCache.put(palette[i - 1], (byte) (i - 1));
          }

          // filling pixel data
          for (int i = 0; i < tileData.length; i++) {
            if ((pixels[i] & 0xff000000) == 0) {
              tileData[i] = 0;
            } else {
              final Byte palIndex = colorCache.get(pixels[i]);
              if (palIndex != null) {
                tileData[i] = (byte) (palIndex + 1);
              } else {
                byte color = (byte) ColorConvert.getNearestColor(pixels[i], palette, 0.0, null);
                tileData[i] = (byte) (color + 1);
                colorCache.put(pixels[i], color);
              }
            }
          }
        } else {
          retVal = Status.ERROR;
          break;
        }
        bos.write(tilePalette);
        bos.write(tileData);
      }
      tileImageOut.flush();
    } catch (Exception e) {
      retVal = Status.ERROR;
      Logger.error(e);
    } finally {
      if (showProgress) {
        SwingUtilities.invokeLater(progress::close);
      }
      if (retVal == Status.ERROR) {
        // deleting incomplete tis file
        try {
          Files.delete(config.getTisFile());
        } catch (IOException e) {
          Logger.error(e);
        }
      }
    }

    return retVal;
  }

  /**
   * Converts the given tileset into the pvrz-based variant.
   *
   * @param config       {@link Config} instance with global conversion parameters.
   * @param showProgress Indicates whether a progress dialog should be displayed.
   * @param parent       Parent component for the the progress dialog.
   * @return A status value that indicates success of the conversion operation.
   */
  public static Status convertToPvrzTis(Config config, boolean showProgress, Component parent) {
    Status retVal = Status.ERROR;
    if (config == null) {
      return retVal;
    }

    if (showProgress && parent == null) {
      parent = NearInfinity.getInstance();
    }

    final ProgressMonitor progress;
    if (showProgress) {
      progress = new ProgressMonitor(parent, "Converting TIS...", "Preparing TIS", 0, 6);
      progress.setMillisToDecideToPopup(0);
      progress.setMillisToPopup(0);
      SwingUtilities.invokeLater(() -> progress.setProgress(1));
    } else {
      progress = null;
    }

    try {
      final TisV1Decoder decoder = (TisV1Decoder) config.getDecoder();
      final int tileSize = decoder.getTileWidth();
      final WedInfo wedInfo = config.getWedInfo();
      final int width = wedInfo.getWidth();
      final int height = wedInfo.getHeight();
      final boolean detectBlack = config.isDetectBlack();
      final int borderSize = config.getBorderSize();
      final OverlayConversion overlayConversion = config.getOverlayConversion();
      final int segmentSize = config.getSegmentSize();

      // handling overlay conversion (layout changes)
      if (wedInfo.hasWedResource() && overlayConversion.isImplemented()) {
        overlayConversion.getUpdater().update(wedInfo);
      }

      // marks indices of tiles that have already been processed
      final BitSet markedTiles = new BitSet(decoder.getTileCount());

      // processing primary tiles
      final TileMap tmBase = new TileMap(wedInfo, tileSize);
      final int numTiles = decoder.getTileCount();
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          final int idx = y * width + x;
          if (idx < numTiles) {
            final TileInfo ti = wedInfo.getTile(idx);
            if (ti.tilePrimary >= 0 && !markedTiles.get(ti.tilePrimary)) {
              if (!detectBlack || !isTileBlack(decoder, ti.tilePrimary)) {
                tmBase.setTile(x, y, ti.tilePrimary, TileMapItem.FLAG_ALL);
                markedTiles.set(ti.tilePrimary);
              }
            }
          }
        }
      }
      final List<TileMap> primaryTilesList = createTileRegions(config, tmBase);
      final List<TileMap> regions = new ArrayList<>(primaryTilesList);

      if (showProgress) {
        SwingUtilities.invokeLater(() -> {
          progress.setProgress(2);
          progress.setNote("Processing tiles");
        });
        if (progress.isCanceled()) {
          return Status.CANCELLED;
        }
      }

      // processing animated primary tiles
      for (int i = 0, count = wedInfo.getTileCount(); i < count; i++) {
        final TileInfo ti = wedInfo.getTile(i);
        if (ti.tilesPrimary.length > 1) {
          final int x = i % wedInfo.getWidth();
          final int y = i / wedInfo.getWidth();
          // frame 0 was already covered previously
          for (int j = 1; j < ti.tilesPrimary.length; j++) {
            if (!markedTiles.get(ti.tilesPrimary[j])) {
              if (!detectBlack || !isTileBlack(decoder, ti.tilesPrimary[j])) {
                final TileMap tm = new TileMap(wedInfo, tileSize);
                tm.setTile(x, y, ti.tilesPrimary[j], TileMapItem.FLAG_ALL);
                markedTiles.set(ti.tilesPrimary[j]);
                final List<TileMap> list = createTileRegions(config, tm);
                regions.addAll(list);
              }
            }
          }
        }
      }

      // processing door tiles
      for (int i = 0, count = wedInfo.getDoorCount(); i < count; i++) {
        if (wedInfo.getDoorTileCount(i) > 0) {
          // adding door tiles
          final TileMap tm = new TileMap(wedInfo, tileSize);
          final int[] indices = wedInfo.getDoorTileIndices(i);
          for (final int idx : indices) {
            if (idx < 0 || idx >= wedInfo.getWidth() * wedInfo.getHeight()) {
              final int tileCount = wedInfo.getWidth() * wedInfo.getHeight();
              throw new IndexOutOfBoundsException(getErrorMessage(config,
                  "Door tile is out of bounds (index=" + idx + ", size=" + tileCount + ")"));
            }
            final int x = idx % wedInfo.getWidth();
            final int y = idx / wedInfo.getWidth();
            final TileInfo ti = wedInfo.getTile(idx);
            if (ti.tileSecondary >= 0 && !markedTiles.get(ti.tileSecondary)) {
              if (!detectBlack || !isTileBlack(decoder, ti.tileSecondary)) {
                tm.setTile(x, y, ti.tileSecondary, TileMapItem.FLAG_ALL);
                markedTiles.set(ti.tileSecondary);
              }
            }
          }
          // Reduce texture size hard limit to reduce the amount of wasted space on pvr textures
          final List<TileMap> list = createTileRegions(new Config(config).setTextureSize(segmentSize), tm);
          regions.addAll(list);
        }
      }

      // processing secondary tiles not covered by previous operations
      final TileMap mapAll = new TileMap(wedInfo, tileSize);
      for (int i = 0, count = wedInfo.getTileCount(); i < count; i++) {
        final TileInfo ti = wedInfo.getTile(i);
        if (ti.tileSecondary >= 0 && !markedTiles.get(ti.tileSecondary)) {
          if (!detectBlack || !isTileBlack(decoder, ti.tileSecondary)) {
            final int x = i % wedInfo.getWidth();
            final int y = i / wedInfo.getWidth();
            mapAll.setTile(x, y, ti.tileSecondary, TileMapItem.FLAG_ALL);
            markedTiles.set(ti.tileSecondary);
          }
        }
      }
      // splitting map into distinct chunks
      List<TileMap> mapList = mapAll.split();
      for (final TileMap tm : mapList) {
        if (!tm.isEmpty()) {
          // Reduce texture size hard limit to reduce the amount of wasted space on pvr textures
          final List<TileMap> list = createTileRegions(new Config(config).setTextureSize(segmentSize), tm);
          regions.addAll(list);
        }
      }

      if (showProgress) {
        SwingUtilities.invokeLater(() -> {
          progress.setProgress(3);
          progress.setNote("Generating tile mapping");
        });
        if (progress.isCanceled()) {
          return Status.CANCELLED;
        }
      }

      // mapping tile regions to textures
      final List<TileEntry> mappedTileList = new ArrayList<>(numTiles);
      final List<BinPack2D> pageList = new ArrayList<>();
      final BinPack2D.HeuristicRules binPackRule = BinPack2D.HeuristicRules.BOTTOM_LEFT_RULE;
      for (final TileMap tileMap : regions) {
        final Rectangle bounds = tileMap.getPixelBounds(borderSize);
        Rectangle binRect = null;
        int pageIndex = -1;

        // checking whether the map fits into existing bins
        for (int i = 0, size = pageList.size(); i < size; i++) {
          final BinPack2D bin = pageList.get(i);
          binRect = bin.insert(bounds.width, bounds.height, binPackRule);
          if (!binRect.isEmpty()) {
            pageIndex = i;
            break;
          }
        }

        // creating new bin?
        if (pageIndex < 0) {
          final BinPack2D bin = new BinPack2D(config.getTextureSize(), config.getTextureSize());
          pageList.add(bin);
          pageIndex = pageList.size() - 1;
          binRect = bin.insert(bounds.width, bounds.height, binPackRule);
        }

        // basic error checks
        if (pageIndex < 0 || binRect == null || binRect.isEmpty()) {
          // should never happen
          throw new Exception(getErrorMessage(config, "Could not fit tile region into texture page. Region: " + bounds));
        }
        if (config.getPvrzBaseIndex() + pageIndex >= 100) {
          throw new Exception(getErrorMessage(config, "Tileset requires too many texture pages"));
        }

        tileMap.setPageRect(config.getPvrzBaseIndex() + pageIndex, binRect, borderSize);

        // assigning TIS mapping to tiles
        final List<Point> locations = tileMap.getAllTilePositions(false);
        for (final Point p : locations) {
          final TileMapItem tmi = tileMap.getTile(p);
          if (tmi.isAllFlag()) {
            final TileEntry tileEntry =
                new TileEntry(tmi.getIndex(), tmi.getPage(), tmi.getX(), tmi.getY());
            mappedTileList.add(tileEntry);
          }
        }
      }
      mappedTileList.sort(TileEntry.COMPARE_BY_INDEX);

      // generating pvrz files
      final String fmtPvrzProgress = "Writing PVRZ (%d / %d)";
      for (int i = 0, size = pageList.size(); i < size; i++) {
        final int pageIdx = i;
        final int effectivePageIdx = config.getPvrzBaseIndex() + pageIdx;

        if (showProgress) {
          SwingUtilities.invokeLater(() -> {
            progress.setProgress(4);
            progress.setNote(String.format(fmtPvrzProgress, pageIdx + 1, pageList.size()));
          });
          if (progress.isCanceled()) {
            return Status.CANCELLED;
          }
        }

        final List<TileMap> tileMaps = regions
            .stream()
            .filter(tm -> tm.getPage() == effectivePageIdx)
            .collect(Collectors.toList());
        final Path pvrzPath = generatePvrzFileName(config.getTisFile(), effectivePageIdx);
        if (pvrzPath == null) {
          throw new Exception(getErrorMessage(config, "Could not determine pvrz file name"));
        }

        final BinPack2D bin = pageList.get(pageIdx);
        bin.shrinkBin(true);

        createPvrz(config, pvrzPath, tileMaps, bin.getBinWidth(), bin.getBinHeight());
      }

      // generating output TIS file
      if (showProgress) {
        SwingUtilities.invokeLater(() -> {
          progress.setProgress(5);
          progress.setNote("Writing TIS");
        });
        if (progress.isCanceled()) {
          return Status.CANCELLED;
        }
      }
      createPvrzTis(config, mappedTileList);

      retVal = Status.SUCCESS;
    } catch (Exception e) {
      Logger.error(e);
    } finally {
      if (showProgress) {
        SwingUtilities.invokeLater(progress::close);
      }
    }

    return retVal;
  }

  /**
   * Determines whether the tile at the specified index is completely black.
   *
   * @param decoder   {@link TisDecoder} instance.
   * @param tileIndex Tile index.
   * @return {@code true} if the tile is completely black (ie. no colored or transparent pixels), {@code false}
   *         otherwise.
   */
  private static boolean isTileBlack(TisDecoder decoder, int tileIndex) {
    Objects.requireNonNull(decoder);
    boolean retVal = true;
    if (tileIndex < 0 || tileIndex >= decoder.getTileCount() || !(decoder instanceof TisV1Decoder)) {
      return retVal;
    }

    final TisV1Decoder decoder2 = (TisV1Decoder) decoder;

    // loading palette data
    final int[] pal = new int[256];
    decoder2.getTilePalette(tileIndex, pal, true);

    // loading raw tile data
    final byte[] tileData = new byte[decoder.getTileWidth() * decoder.getTileHeight()];
    decoder2.getRawTileData(tileIndex, tileData);

    // checking whether tile consists of solid color
    final int transparentColor = 0x0000ff00;
    int palIdx = -1;
    for (int idx = 0; (idx < tileData.length) && retVal; idx++) {
      if (palIdx == -1) {
        palIdx = tileData[idx] & 0xff;
        // exception: transparent palette entry doesn't count
        retVal = (pal[palIdx] != transparentColor);
      }
      retVal &= (tileData[idx] == palIdx);
    }

    if (retVal && palIdx >= 0) {
      // evaluating solid tile color
      final int threshold = 8;  // color should be (near) black
      int r = (pal[palIdx] >> 16) & 0xff;
      int g = (pal[palIdx] >> 8) & 0xff;
      int b = pal[palIdx] & 0xff;
      retVal = (r < threshold) && (g < threshold) && (b < threshold);
    }

    return retVal;
  }

  /**
   * Splits the given {@code tileMap} into regions that fit onto textures of up to {@link Config#getTextureSize()}
   * pixels. Regions are returned as a list.
   *
   * @param config  {@link Config} instance with global conversion parameters.
   * @param tileMap {@link TileMap} structure with the tiles to split.
   * @return List of {@link TileMap} objects that are small enough to fit onto a texture of {@code pageSize} pixels.
   */
  private static List<TileMap> createTileRegions(Config config, TileMap tileMap) {
    final List<TileMap> retVal = new ArrayList<>();
    if (config == null || tileMap == null || tileMap.isEmpty()) {
      return retVal;
    }

    final WedInfo wedInfo = config.getWedInfo();
    final int width = wedInfo.getWidth();
    final int height = wedInfo.getHeight();
    final int textureSize = config.getTextureSize();
    final int borderSize = config.getBorderSize();
    final int tileSize = config.getTileSize();
    final Set<Point> locations = new HashSet<>(tileMap.getAllTilePositions(false));
    final Rectangle tileBounds = tileMap.getTileBounds(true);

    int x0 = 0;
    int y0 = 0;
    final Rectangle regionBounds = new Rectangle(tileBounds.x + x0, tileBounds.y + y0, 0, 0);
    while (x0 < tileBounds.width && y0 < tileBounds.height) {
      final TileMap tm = new TileMap(wedInfo, tileSize);

      // calculating horizontal tile map size
      int borderLeft = (tileBounds.x + x0 > 0) ? borderSize : 0;
      int numTilesX = Math.min(textureSize / tileSize, tileBounds.width - x0);
      while (numTilesX * tileSize + borderLeft > textureSize) {
        numTilesX--;
      }
      int borderRight = (tileBounds.x + x0 + numTilesX < width) ? borderSize : 0;
      while (numTilesX * tileSize + borderLeft + borderRight > textureSize) {
        numTilesX--;
      }

      // calculating vertical tile map size
      int borderTop = (tileBounds.y + y0 > 0) ? borderSize : 0;
      int numTilesY = Math.min(textureSize / tileSize, tileBounds.height - y0);
      while (numTilesY * tileSize + borderTop > textureSize) {
        numTilesY--;
      }
      int borderBottom = (tileBounds.y + y0 + numTilesY < height) ? borderSize : 0;
      while (numTilesY * tileSize + borderTop + borderBottom > textureSize) {
        numTilesY--;
      }

      regionBounds.width = numTilesX;
      regionBounds.height = numTilesY;

      final Point p2 = new Point();
      for (final Point p : locations) {
        if (regionBounds.contains(p)) {
          final TileMapItem tileMapItem = tileMap.getTile(p);
          if (tileMapItem.getIndex() < 0) {
            continue;
          }

          // adding main tile
          final TileInfo tiPrimary = wedInfo.getTile(p);
          int framePrimary = tiPrimary.getPrimaryTileFrame(tileMapItem.getIndex());
          boolean isSecondary = (tileMapItem.getIndex() == tiPrimary.tileSecondary);
          tm.setTile(p, tileMapItem.getIndex(), TileMapItem.FLAG_ALL);

          // adding left border?
          p2.x = p.x - 1;
          p2.y = p.y;
          if (p2.x >= 0 && (p2.x == regionBounds.x - 1 || !locations.contains(p2))) {
            final TileInfo ti = wedInfo.getTile(p2);
            final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
            if (tileIdx >= 0) {
              final TileMapItem tmi = tm.getTile(p2);
              if (tmi != null) {
                tmi.setRightFlag(true);
              } else {
                tm.setTile(p2, tileIdx, TileMapItem.FLAG_RIGHT);
              }
            }
          }

          // adding right border?
          p2.x = p.x + 1;
          p2.y = p.y;
          if (p2.x < width && (p2.x == regionBounds.x + regionBounds.width || !locations.contains(p2))) {
            final TileInfo ti = wedInfo.getTile(p2);
            final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
            if (tileIdx >= 0) {
              final TileMapItem tmi = tm.getTile(p2);
              if (tmi != null) {
                tmi.setLeftFlag(true);
              } else {
                tm.setTile(p2, tileIdx, TileMapItem.FLAG_LEFT);
              }
            }
          }

          // adding top border?
          p2.x = p.x;
          p2.y = p.y - 1;
          if (p2.y >= 0 && (p2.y == regionBounds.y - 1 || !locations.contains(p2))) {
            final TileInfo ti = wedInfo.getTile(p2);
            final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
            if (tileIdx >= 0) {
              final TileMapItem tmi = tm.getTile(p2);
              if (tmi != null) {
                tmi.setBottomFlag(true);
              } else {
                tm.setTile(p2, tileIdx, TileMapItem.FLAG_BOTTOM);
              }
            }
          }

          // adding bottom border?
          p2.x = p.x;
          p2.y = p.y + 1;
          if (p2.y < height && (p2.y == regionBounds.y + regionBounds.height || !locations.contains(p2))) {
            final TileInfo ti = wedInfo.getTile(p2);
            final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
            if (tileIdx >= 0) {
              final TileMapItem tmi = tm.getTile(p2);
              if (tmi != null) {
                tmi.setTopFlag(true);
              } else {
                tm.setTile(p2, tileIdx, TileMapItem.FLAG_TOP);
              }
            }
          }

          // adding top-left corner?
          p2.x = p.x - 1;
          p2.y = p.y - 1;
          if (p2.x >= 0 && p2.y >= 0 &&
              ((p2.x == regionBounds.x - 1 && p2.y == regionBounds.y - 1) || !locations.contains(p2))) {
            TileMapItem tmi1 = tm.getTile(p.x, p.y - 1);
            TileMapItem tmi2 = tm.getTile(p.x - 1, p.y);
            if (tmi1 != null && tmi1.isBottomFlag() && tmi2 != null && tmi2.isRightFlag()) {
              final TileInfo ti = wedInfo.getTile(p2);
              final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
              if (tileIdx >= 0) {
                final TileMapItem tmi = tm.getTile(p2);
                if (tmi != null) {
                  tmi.setBottomRightFlag(true);
                } else {
                  tm.setTile(p2, tileIdx, TileMapItem.FLAG_BOTTOM_RIGHT);
                }
              }
            }
          }

          // adding top-right corner?
          p2.x = p.x + 1;
          p2.y = p.y - 1;
          if (p2.x < width && p2.y >= 0 &&
              ((p2.x == regionBounds.x + regionBounds.width && p2.y == regionBounds.y - 1) || !locations.contains(p2))) {
            TileMapItem tmi1 = tm.getTile(p.x, p.y - 1);
            TileMapItem tmi2 = tm.getTile(p.x + 1, p.y);
            if (tmi1 != null && tmi1.isBottomFlag() && tmi2 != null && tmi2.isLeftFlag()) {
              final TileInfo ti = wedInfo.getTile(p2);
              final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
              if (tileIdx >= 0) {
                final TileMapItem tmi = tm.getTile(p2);
                if (tmi != null) {
                  tmi.setBottomLeftFlag(true);
                } else {
                  tm.setTile(p2, tileIdx, TileMapItem.FLAG_BOTTOM_LEFT);
                }
              }
            }
          }

          // adding bottom-left corner?
          p2.x = p.x - 1;
          p2.y = p.y + 1;
          if (p2.x >= 0 && p2.y < height &&
              ((p2.x == regionBounds.x - 1 && p2.y == regionBounds.y + regionBounds.height) || !locations.contains(p2))) {
            TileMapItem tmi1 = tm.getTile(p.x, p.y + 1);
            TileMapItem tmi2 = tm.getTile(p.x - 1, p.y);
            if (tmi1 != null && tmi1.isTopFlag() && tmi2 != null && tmi2.isRightFlag()) {
              final TileInfo ti = wedInfo.getTile(p2);
              final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
              if (tileIdx >= 0) {
                final TileMapItem tmi = tm.getTile(p2);
                if (tmi != null) {
                  tmi.setTopRightFlag(true);
                } else {
                  tm.setTile(p2, tileIdx, TileMapItem.FLAG_TOP_RIGHT);
                }
              }
            }
          }

          // adding bottom-right corner?
          p2.x = p.x + 1;
          p2.y = p.y + 1;
          if (p2.x < width && p2.y < height &&
              ((p2.x == regionBounds.x + regionBounds.width && p2.y == regionBounds.y + regionBounds.height) ||
                  !locations.contains(p2))) {
            TileMapItem tmi1 = tm.getTile(p.x, p.y + 1);
            TileMapItem tmi2 = tm.getTile(p.x + 1, p.y);
            if (tmi1 != null && tmi1.isTopFlag() && tmi2 != null && tmi2.isLeftFlag()) {
              final TileInfo ti = wedInfo.getTile(p2);
              final int tileIdx = (isSecondary && ti.tileSecondary >= 0) ? ti.tileSecondary : ti.getPrimaryTileIndex(framePrimary);
              if (tileIdx >= 0) {
                final TileMapItem tmi = tm.getTile(p2);
                if (tmi != null) {
                  tmi.setTopLeftFlag(true);
                } else {
                  tm.setTile(p2, tileIdx, TileMapItem.FLAG_TOP_LEFT);
                }
              }
            }
          }
        }
      }

      // adding page map
      if (!tm.isEmpty()) {
        retVal.add(tm);
      }

      // updating x0 and y0
      x0 += numTilesX;
      if (x0 >= tileBounds.width) {
        x0 = 0;
        y0 += numTilesY;
      }
      regionBounds.x = tileBounds.x + x0;
      regionBounds.y = tileBounds.y + y0;
    }

    return retVal;
  }

  /**
   * Creates a new PVRZ files based on the given parameters.
   *
   * @param config   {@link Config} instance with global conversion parameters.
   * @param pvrzFile {@link Path} of the PVRZ file.
   * @param tileMaps List of {@link TileMap} instances with tiles for this pvrz texture.
   * @param width    Texture width, in pixels.
   * @param height   Texture height, in pixels.
   * @throws Exception if the pvrz file could not be created.
   */
  private static void createPvrz(Config config, Path pvrzFile, List<TileMap> tileMaps, int width, int height)
      throws Exception {
    Objects.requireNonNull(config, "Configuration instance is null");
    Objects.requireNonNull(pvrzFile, "PVRZ file path is null");
    Objects.requireNonNull(tileMaps, "Tile map list is null");
    if (width < config.getTileSize() || height < config.getTileSize() ||
        width > config.getMaxTextureSize() || height > config.getMaxTextureSize()) {
      throw new IllegalArgumentException("Unsupported texture size (width=" + width + ", height=" + height + ")");
    }
    if (tileMaps.isEmpty()) {
      Logger.warn("PVRZ creation: No tile maps available for {}", pvrzFile.getFileName());
    }

    // generating texture image
    final BufferedImage texture = ColorConvert.createCompatibleImage(width, height, true);
    Graphics2D g = texture.createGraphics();
    g.setComposite(AlphaComposite.Src);
    try {
      g.setBackground(Color.BLACK);
      g.setColor(Color.BLACK);
      g.fillRect(0, 0, texture.getWidth(), texture.getHeight());

      for (final TileMap tileMap : tileMaps) {
        renderTextureTiles(config, g, tileMap);
      }
    } finally {
      g.dispose();
      g = null;
    }

    // compressing to DXT1
    final DxtEncoder.DxtType dxtType = DxtEncoder.DxtType.DXT1;
    final int pvrCode = 7;  // PVR code for DXT1
    final int[] textureData = ((DataBufferInt) texture.getRaster().getDataBuffer()).getData();
    try {
      // compressing image data
      final byte[] output = new byte[DxtEncoder.calcImageSize(texture.getWidth(), texture.getHeight(), dxtType)];
      DxtEncoder.encodeImage(textureData, texture.getWidth(), texture.getHeight(), output, dxtType,
          config.isMultithreaded());
      byte[] header = ConvertToPvrz.createPVRHeader(texture.getWidth(), texture.getHeight(), pvrCode);
      byte[] pvrz = new byte[header.length + output.length];
      System.arraycopy(header, 0, pvrz, 0, header.length);
      System.arraycopy(output, 0, pvrz, header.length, output.length);
      pvrz = Compressor.compress(pvrz, 0, pvrz.length, true);

      // writing pvrz file
      try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(pvrzFile))) {
        bos.write(pvrz);
      }
    } catch (Exception e) {
      try {
        Files.delete(pvrzFile);
      } catch (IOException e2) {
        Logger.error(e2);
      }
      throw e;
    }
  }

  /**
   * Draws tiles from a tile map to a graphics object.
   *
   * @param config  {@link Config} instance with global conversion parameters.
   * @param g       {@link Graphics2D} handle of the texture image.
   * @param tileMap {@link TileMap} instance with tile layout information.
   * @throws Exception if the tile map could not be rendered.
   */
  private static void renderTextureTiles(Config config, Graphics2D g, TileMap tileMap) throws Exception {
    Objects.requireNonNull(config);
    Objects.requireNonNull(g);
    Objects.requireNonNull(tileMap);

    // evaluating overlay conversion mode
    final OverlayConversion conversionMode;
    if (config.getOverlayConversion() != OverlayConversion.NONE && tileMap.getWedInfo() != null &&
        tileMap.getWedInfo().hasWedResource()) {
      conversionMode = config.getOverlayConversion();
    } else {
      conversionMode = OverlayConversion.NONE;
    }

    final int tileSize = config.getTileSize();
    BufferedImage tileImg = ColorConvert.createCompatibleImage(tileSize, tileSize, true);
    final List<Point> locations = tileMap.getAllTilePositions(false);
    for (final Point p : locations) {
      final TileMapItem tmi = tileMap.getTile(p);
      // preparations
      if (tmi != null) {
        config.getDecoder().getTile(tmi.getIndex(), tileImg);
      } else {
        Logger.warn("No tile available at {}", p);
        continue;
      }

      // overlay conversion
      if (conversionMode.isImplemented()) {
        final Point tileLocation = config.wedInfo.getTileLocation(tmi.getIndex());
        if (tileLocation != null) {
          final int priTileIdx = tileLocation.y * config.wedInfo.getWidth() + tileLocation.x;
          final TileInfo tileInfo = config.wedInfo.getTile(priTileIdx);
          tileImg = conversionMode.getConverter().convert(tmi.getIndex(), tileImg, config.getDecoder(), tileInfo);
        }
      }

      // rendering tiles
      final int borderSize = config.getBorderSize();
      for (int flag = 0; flag <= TileMapItem.FLAG_ALL; flag++) {
        if (tmi.isFlag(flag)) {
          final int x1, y1, x2, y2;
          switch (flag) {
            case TileMapItem.FLAG_TOP:          // rendering only top rows of pixels
              x1 = 0; y1 = 0; x2 = tileSize; y2 = borderSize;
              break;
            case TileMapItem.FLAG_BOTTOM:       // rendering only bottom rows of pixels
              x1 = 0; y1 = tileSize - borderSize; x2 = tileSize; y2 = tileSize;
              break;
            case TileMapItem.FLAG_LEFT:         // rendering only left columns of pixels
              x1 = 0; y1 = 0; x2 = borderSize; y2 = tileSize;
              break;
            case TileMapItem.FLAG_RIGHT:        // rendering only right columns of pixels
              x1 = tileSize - borderSize; y1 = 0; x2 = tileSize; y2 = tileSize;
              break;
            case TileMapItem.FLAG_TOP_LEFT:     // rendering only top left rectangle of pixels
              x1 = 0; y1 = 0; x2 = borderSize; y2 = borderSize;
              break;
            case TileMapItem.FLAG_TOP_RIGHT:    // rendering only top right rectangle of pixels
              x1 = tileSize - borderSize; y1 = 0; x2 = tileSize; y2 = borderSize;
              break;
            case TileMapItem.FLAG_BOTTOM_LEFT:  // rendering only bottom left rectangle of pixels
              x1 = 0; y1 = tileSize - borderSize; x2 = borderSize; y2 = tileSize;
              break;
            case TileMapItem.FLAG_BOTTOM_RIGHT: // rendering only bottom right rectangle of pixels
              x1 = tileSize - borderSize; y1 = tileSize - borderSize;
              x2 = tileSize; y2 = tileSize;
              break;
            case TileMapItem.FLAG_ALL:          // rendering full tile
              x1 = 0; y1 = 0; x2 = tileSize; y2 = tileSize;
              break;
            default:                            // just added for completeness
              x1 = y1 = x2 = y2 = 0;
              break;
          }
          g.drawImage(tileImg, tmi.getX() + x1, tmi.getY() + y1, tmi.getX() + x2, tmi.getY() + y2, x1, y1, x2, y2, null);
        }
      }
    }
  }

  /**
   * Creates a new TIS file based on the given parameters.
   *
   * @param config   {@link Config} instance with global conversion parameters.
   * @param tileList List of {@link TileEntry} structures with tile information.
   * @throws Exception if the tis file could not be created.
   */
  private static void createPvrzTis(Config config, List<TileEntry> tileList) throws Exception {
    Objects.requireNonNull(config);
    Objects.requireNonNull(tileList);
    if (tileList.isEmpty()) {
      Logger.info(getErrorMessage(config, "Tile list is empty"));
    }

    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(config.getTisFile()))) {
      final byte[] header = new byte[24];
      System.arraycopy("TIS V1  ".getBytes(), 0, header, 0, 8);
      // TODO: use different tile count source when implementing overlay conversion modes that change tileset layout
      DynamicArray.putInt(header, 0x08, config.getDecoder().getTileCount());
      DynamicArray.putInt(header, 0x0c, 0x0c);
      DynamicArray.putInt(header, 0x10, 0x18);
      DynamicArray.putInt(header, 0x14, config.getTileSize());
      bos.write(header);

      final TileEntry defEntry = new TileEntry(-1, -1, 0, 0);
      final Map<Integer, TileEntry> tileMap =
          tileList.stream().collect(Collectors.toMap(te -> te.tileIndex, Function.identity()));
      for (int idx = 0, numTiles = config.getDecoder().getTileCount(); idx < numTiles; idx++) {
        final TileEntry tileEntry = tileMap.getOrDefault(idx, defEntry);
        bos.write(DynamicArray.convertInt(tileEntry.page));
        bos.write(DynamicArray.convertInt(tileEntry.x));
        bos.write(DynamicArray.convertInt(tileEntry.y));
      }
    } catch (Exception e) {
      try {
        Files.delete(config.getTisFile());
      } catch (IOException e2) {
        Logger.error(e2);
      }
      throw e;
    }
  }

  /**
   * Generates a PVRZ filename with full path from the given parameters.
   *
   * @param tisFile TIS file path.
   * @param page    PVRZ texture page index.
   * @return A {@link Path} object with the PVRZ texture filename.
   */
  private static Path generatePvrzFileName(Path tisFile, int page) {
    if (tisFile != null) {
      Path path = tisFile.getParent();
      String tisName = tisFile.getFileName().toString();
      int extOfs = tisName.lastIndexOf('.');
      if (extOfs > 0) {
        tisName = tisName.substring(0, extOfs);
      }
      if (Pattern.matches(".{2,7}", tisName)) {
        String pvrzName = String.format("%s%s%02d.PVRZ", tisName.charAt(0),
            tisName.substring(2), page);
        return path.resolve(pvrzName);
      }
    }
    return null;
  }

  /** A helper method that creates an error message with TIS filename and given message. */
  private static String getErrorMessage(Config config, String msg) {
    String retVal = (config != null && config.tisFile != null) ? config.tisFile.getFileName().toString() + ": " : "";
    if (msg != null) {
      retVal += msg;
    } else {
      retVal += "Error";
    }
    return retVal;
  }

  /** A default overlay tile converter routine that does nothing. */
  private static final OverlayTileConverter overlayConverterDefault = (tileIndex, tileImage, decoder, tileInfo) -> tileImage;

  /** Overlay tile conversion for {@link OverlayConversion#BG1_TO_BGEE}. */
  private static final OverlayTileConverter overlayConverterBG1toBGEE = (tileIndex, tileImage, decoder, tileInfo) -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /** Overlay tile conversion for {@link OverlayConversion#BG1_TO_BG2EE}. */
  private static final OverlayTileConverter overlayConverterBG1toBG2EE = (tileIndex, tileImage, decoder, tileInfo) -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /** Overlay tile conversion for {@link OverlayConversion#BG2_TO_BGEE}. */
  private static final OverlayTileConverter overlayConverterBG2toBGEE = (tileIndex, tileImage, decoder, tileInfo) -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /** Overlay tile conversion for {@link OverlayConversion#BG2_TO_BG2EE}. */
  private static final OverlayTileConverter overlayConverterBG2toBG2EE = (tileIndex, tileImage, decoder, tileInfo) -> {
    if (tileInfo.flags == 0 || tileInfo.tileSecondary < 0) {
      // no overlay conversion needed
      return tileImage;
    }

    // preparations
    final Couple<BufferedImage, BufferedImage> couple = OverlayTileConverter.init(tileIndex, tileImage, decoder, tileInfo);
    final int[] priData = ((DataBufferInt) couple.getValue0().getRaster().getDataBuffer()).getData();
    final int[] secData = ((DataBufferInt) couple.getValue1().getRaster().getDataBuffer()).getData();
    final boolean isPrimary = (couple.getValue0() == tileImage);

    // performing overlay conversion
    if (isPrimary) {
      // removing all pixels on primary tile that are transparent on secondary tile
      for (int idx = 0, size = decoder.getTileWidth() * decoder.getTileHeight(); idx < size; idx++) {
        if ((secData[idx] & ColorConvert.ALPHA_MASK) == 0) {
          priData[idx] = 0;
        }
      }
    } else {
      // replacing transparent pixels on secondary tile with pixels from primary tile
      // removing all opaque pixels on secondary tile
      for (int idx = 0, size = decoder.getTileWidth() * decoder.getTileHeight(); idx < size; idx++) {
        secData[idx] = ((secData[idx] & ColorConvert.ALPHA_MASK) == 0) ? priData[idx] : 0;
      }
    }
    return tileImage;
  };

  /** Overlay tile conversion for {@link OverlayConversion#BGEE_TO_BG1}. */
  private static final OverlayTileConverter overlayConverterBGEEtoBG1 = (tileIndex, tileImage, decoder, tileInfo) -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /** Overlay tile conversion for {@link OverlayConversion#BGEE_TO_BG2}. */
  private static final OverlayTileConverter overlayConverterBGEEtoBG2 = (tileIndex, tileImage, decoder, tileInfo) -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /** Overlay tile conversion for {@link OverlayConversion#BG2EE_TO_BG1}. */
  private static final OverlayTileConverter overlayConverterBG2EEtoBG1 = (tileIndex, tileImage, decoder, tileInfo) -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /** Overlay tile conversion for {@link OverlayConversion#BG2EE_TO_BG2}. */
  private static final OverlayTileConverter overlayConverterBG2EEtoBG2 = (tileIndex, tileImage, decoder, tileInfo) -> {
    if (tileInfo.flags == 0 || tileInfo.tileSecondary < 0) {
      // no overlay conversion needed
      return tileImage;
    }

    // preparations
    final Couple<BufferedImage, BufferedImage> couple = OverlayTileConverter.init(tileIndex, tileImage, decoder, tileInfo);
    final int[] priData = ((DataBufferInt) couple.getValue0().getRaster().getDataBuffer()).getData();
    final int[] secData = ((DataBufferInt) couple.getValue1().getRaster().getDataBuffer()).getData();
    final boolean isPrimary = (couple.getValue0() == tileImage);

    // performing overlay conversion
    if (isPrimary) {
      // replacing transparent pixels on primary tile with pixels from secondary tile
      for (int idx = 0, size = decoder.getTileWidth() * decoder.getTileHeight(); idx < size; idx++) {
        if ((priData[idx] & ColorConvert.ALPHA_MASK) == 0) {
          priData[idx] = secData[idx];
        }
      }
    } else {
      // replacing transparent pixels on secondary tile with pixels from primary tile
      // removing all opaque pixels on secondary tile;
      for (int idx = 0, size = decoder.getTileWidth() * decoder.getTileHeight(); idx < size; idx++) {
        secData[idx] = ((secData[idx] & ColorConvert.ALPHA_MASK) == 0) ? priData[idx] : 0;
      }
    }
    return tileImage;
  };

  /** A default overlay map updater routine that does nothing. */
  private static final OverlayMapUpdater overlayUpdaterDefault = wedInfo -> {};

  /**
   * Modifies the tileset map to account for the overlay tile changes.
   * Corresponds with {@link OverlayConversion#BG1_TO_BGEE}.
   *
   * @param wedInfo {@link WedInfo} instance with tile mappings.
   * @throws Exception If the conversion could not be performed.
   */
  private static final OverlayMapUpdater overlayUpdaterBG1toBGEE = wedInfo -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /**
   * Modifies the tileset map to account for the overlay tile changes.
   * Corresponds with {@link OverlayConversion#BG1_TO_BG2EE}.
   *
   * @param wedInfo {@link WedInfo} instance with tile mappings.
   * @throws Exception If the conversion could not be performed.
   */
  private static final OverlayMapUpdater overlayUpdaterBG1toBG2EE = wedInfo -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /**
   * Modifies the tileset map to account for the overlay tile changes.
   * Corresponds with {@link OverlayConversion#BG2_TO_BGEE}.
   *
   * @param wedInfo {@link WedInfo} instance with tile mappings.
   * @throws Exception If the conversion could not be performed.
   */
  private static final OverlayMapUpdater overlayUpdaterBG2toBGEE = wedInfo -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /**
   * Modifies the tileset map to account for the overlay tile changes.
   * Corresponds with {@link OverlayConversion#BGEE_TO_BG1}.
   *
   * @param wedInfo {@link WedInfo} instance with tile mappings.
   * @throws Exception If the conversion could not be performed.
   */
  private static final OverlayMapUpdater overlayUpdaterBGEEtoBG1 = wedInfo -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /**
   * Modifies the tileset map to account for the overlay tile changes.
   * Corresponds with {@link OverlayConversion#BGEE_TO_BG2}.
   *
   * @param wedInfo {@link WedInfo} instance with tile mappings.
   * @throws Exception If the conversion could not be performed.
   */
  private static final OverlayMapUpdater overlayUpdaterBGEEtoBG2 = wedInfo -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  /**
   * Modifies the tileset map to account for the overlay tile changes.
   * Corresponds with {@link OverlayConversion#BG2EE_TO_BG1}.
   *
   * @param wedInfo {@link WedInfo} instance with tile mappings.
   * @throws Exception If the conversion could not be performed.
   */
  private static final OverlayMapUpdater overlayUpdaterBG2EEtoBG1 = wedInfo -> {
    throw new UnsupportedOperationException("Not implemented yet");
  };

  // -------------------------- INNER CLASSES --------------------------

  /**
   * This class stores global parameters for the tileset conversion operation.
   */
  public static class Config {
    /** Max. supported texture size, in pixels. */
    public static final int MAX_TEXTURE_SIZE = 1024;

    /** Default number of pixel rows/columns used from border tiles. */
    public static final int DEFAULT_BORDER_SIZE = 2;

    /**
     * Creates a new {@code Config} instance and initializes it with the given parameters. This method is intended for
     * creating Config objects for PVRZ->Palette TIS conversions.
     *
     * @param tisFile           Path of the output TIS file. Files associated with the TIS file are created in the same
     *                            folder as the TIS file.
     * @param tileList          List of individual tiles as {@link Image} objects. (PVRZ -> Palette conversion only)
     * @param decoder           {@link TisDecoder} instance with tile information.
     * @param wedEntry          An optional {@link ResourceEntry} of the associated WED resource. Specify {@code null}
     *                            to autodetect the WED resource.
     * @param overlayConversion Specifies how to convert tile overlays. This mode is only considered if the tileset is
     *                            linked to a WED resource.
     * @return A fully configured {@link Config} instance.
     * @throws NullPointerException if {@code decoder} is {@code null}.
     * @throws Exception            if parameters could not be initialized.
     */
    public static Config createConfigPalette(Path tisFile, List<Image> tileList, TisDecoder decoder,
        ResourceEntry wedEntry, OverlayConversion overlayConversion) throws Exception {
      if (!(Objects.requireNonNull(decoder) instanceof TisV2Decoder)) {
        throw new IllegalArgumentException("Unsupported TIS decoder");
      }
      return new Config(tisFile, tileList, decoder, wedEntry, 1, 0, 0, 0, 0, 0, false, false, overlayConversion);
    }

    /**
     * Creates a new {@code Config} instance and initializes it with the given parameters. This method is intended for
     * creating Config objects for Palette->PVRZ TIS conversions.
     *
     * @param tisFile            Path of the output TIS file. Files associated with the TIS file are created in the same
     *                             folder as the TIS file.
     * @param decoder            {@link TisDecoder} instance with tile information.
     * @param wedEntry           An optional {@link ResourceEntry} of the associated WED resource. Specify {@code null}
     *                             to autodetect the WED resource.
     * @param defaultTilesPerRow Number of tiles per rows that is used to lay out tiles if WED information is
     *                             unavailable.
     * @param defaultRowCount    Number of rows to use if WED information is unavailable.
     * @param textureSize        Max. dimension of a pvr texture.
     * @param pvrzBaseIndex      Start index of PVRZ texture file names. Valid range: [0, 99]. Default: 0
     * @param borderSize         Size of border tiles, in pixels.
     * @param segmentSize        Max. size of tile segments to be placed on PVRZ textures, in pixels.
     * @param detectBlack        Indicates whether black tiles should be replaced with an implicit default.
     * @param multithreaded      Indicates whether to use multithreading to encode PVRZ textures.
     * @param overlayConversion  Specifies how to convert tile overlays. This mode is only considered if the tileset is
     *                             linked to a WED resource.
     * @return A fully configured {@link Config} instance.
     * @throws NullPointerException if {@code decoder} is {@code null}.
     * @throws Exception            if parameters could not be initialized.
     */
    public static Config createConfigPvrz(Path tisFile, TisDecoder decoder, ResourceEntry wedEntry,
        int defaultTilesPerRow, int defaultRowCount, int textureSize, int pvrzBaseIndex, int borderSize, int segmentSize,
        boolean detectBlack, boolean multithreaded, OverlayConversion overlayConversion) throws Exception {
      if (!(Objects.requireNonNull(decoder) instanceof TisV1Decoder)) {
        throw new IllegalArgumentException("Unsupported TIS decoder");
      }
      return new Config(tisFile, null, decoder, wedEntry, defaultTilesPerRow, defaultRowCount, MAX_TEXTURE_SIZE,
          pvrzBaseIndex, borderSize, segmentSize, detectBlack, multithreaded, overlayConversion);
    }

    private final List<Image> tileList;
    private final TisDecoder decoder;
    private final WedInfo wedInfo;
    private final Path tisFile;

    private int defaultTilesPerRow;
    private int defaultRowCount;
    private int textureSize;
    private int pvrzBaseIndex;
    private int borderSize;
    private int segmentSize;
    private boolean detectBlack;
    private boolean multithreaded;
    private OverlayConversion overlayConversion;

    /**
     * Creates a new {@code Config} object and initializes it with the given parameters.
     *
     * @param tisFile            Path of the output TIS file. Files associated with the TIS file are created in the same
     *                             folder.
     * @param tileList           List of individual tiles as {@link Image} objects. (PVRZ -> Palette conversion only)
     * @param decoder            {@link TisDecoder} instance with tile information.
     * @param wedEntry           An optional {@link ResourceEntry} of the associated WED resource. Specify {@code null}
     *                             to autodetect the WED resource.
     * @param defaultTilesPerRow Number of tiles per rows that is used to lay out tiles if WED information is
     *                             unavailable.
     * @param defaultRowCount    Number of rows to use if WED information is unavailable.
     * @param textureSize        Max. dimension of a pvr texture.
     * @param pvrzBaseIndex      Start index of PVRZ texture file names. Valid range: [0, 99]. Default: 0
     * @param borderSize         Size of border tiles, in pixels.
     * @param segmentSize        Max. size of tile segments to be placed on PVRZ textures, in pixels.
     * @param detectBlack        Indicates whether black tiles should be detected and replaced by a default (PVRZ only).
     * @param multithreaded      Indicates whether to use multithreading to encode PVRZ textures (PVRZ only).
     * @param overlayConversion  Specifies how to convert tile overlays. This mode is only considered if the tileset is
     *                             linked to a WED resource.
     * @throws NullPointerException if {@code decoder} is {@code null}.
     * @throws Exception            if parameters could not be initialized.
     */
    private Config(Path tisFile, List<Image> tileList, TisDecoder decoder, ResourceEntry wedEntry,
        int defaultTilesPerRow, int defaultRowCount, int textureSize, int pvrzBaseIndex, int borderSize, int segmentSize,
        boolean detectBlack, boolean multithreaded, OverlayConversion overlayConversion) throws Exception {
      this.tisFile = Objects.requireNonNull(tisFile, "Tis file path is null");
      this.decoder = Objects.requireNonNull(decoder, "Decoder is null");
      if (this.decoder instanceof TisV2Decoder) {
        this.tileList = Objects.requireNonNull(tileList, "Tile list is null");
      } else {
        this.tileList = null;
      }
      this.defaultTilesPerRow = Math.max(1, Math.min(this.decoder.getTileCount(), defaultTilesPerRow));
      setDefaultRowCount(defaultRowCount);
      this.textureSize = ensureBinarySize(Math.max(getTileSize(), Math.min(MAX_TEXTURE_SIZE, textureSize)));
      setPvrzBaseIndex(pvrzBaseIndex);
      this.borderSize = Math.max(0, Math.min(getTileSize(), borderSize));
      setSegmentSize(segmentSize);
      this.detectBlack = detectBlack;
      this.multithreaded = multithreaded;
      this.overlayConversion = validateOverlayConversion(overlayConversion, this.decoder);
      this.wedInfo = initWedInfo(wedEntry);
    }

    /**
     * Constructs a new {@code Config} with the attributes of the specified {@code Config} argument.
     *
     * @param config Source {@link Config} instance.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public Config(Config config) throws Exception {
      Objects.requireNonNull(config);
      this.tisFile = config.tisFile;
      this.tileList = config.tileList;
      this.decoder = config.decoder;
      this.wedInfo = config.wedInfo;
      this.defaultTilesPerRow = config.defaultTilesPerRow;
      this.defaultRowCount = config.defaultRowCount;
      this.textureSize = config.textureSize;
      this.pvrzBaseIndex = config.pvrzBaseIndex;
      this.borderSize = config.borderSize;
      this.segmentSize = config.segmentSize;
      this.detectBlack = config.detectBlack;
      this.multithreaded = config.multithreaded;
      this.overlayConversion = config.overlayConversion;
    }

    /** Returns the assigned {@link Path} to the output TIS file. */
    public Path getTisFile() {
      return tisFile;
    }

    /** Returns a list of individual tiles as {@link Image} objects. It is only available for PVRZ->Palette conversions. */
    public List<Image> getTileList() {
      return tileList;
    }

    /** Returns the assigned {@link TisDecoder} instance. */
    public TisDecoder getDecoder() {
      return decoder;
    }

    /** Returns the tile dimension obtained from the input TIS header. */
    public int getTileSize() {
      return decoder.getTileWidth();
    }

    /**
     * Returns an associated {@link WedInfo} object with WED details. A virtual {@code WedInfo} object with calculated
     * data is returned if no WED resource is available.
     */
    public WedInfo getWedInfo() {
      return wedInfo;
    }

    /**
     * Returns the number of tiles per row that can be used to lay out tiles if WED information is unavailable.
     * Only relevant for Palette->PVRZ conversion.
     */
    public int getDefaultTilesPerRow() {
      return defaultTilesPerRow;
    }

    /**
     * Assigns a new value for default tiles per row. Returns this {@code Config} instance. Only relevant for
     * Palette->PVRZ conversion.
     */
    public Config setDefaultTilesPerRow(int tilesPerRow) {
      this.defaultTilesPerRow = Math.max(1, Math.min(decoder.getTileCount(), tilesPerRow));
      setDefaultRowCount(getDefaultRowCount());
      return this;
    }

    /**
     * Returns the number of rows in the tilesets that are used if WED information is unavailable. Only relevant for
     * Palette->PVRZ conversion.
     */
    public int getDefaultRowCount() {
      return defaultRowCount;
    }

    /**
     * Assigns a new value to the default row count. Returns this {@code Config} instance. Only relevant for
     * Palette->PVRZ conversion.
     */
    public Config setDefaultRowCount(int rowCount) {
      if (rowCount < 1) {
        this.defaultRowCount = decoder.getTileCount() / this.defaultTilesPerRow;
      } else {
        final int maxRows = decoder.getTileCount() / this.defaultTilesPerRow;
        this.defaultRowCount = Math.max(1, Math.min(maxRows, rowCount));
      }
      return this;
    }

    /** Returns the assigned texture size, in pixels. Only relevant for Palette->PVRZ conversion. */
    public int getTextureSize() {
      return textureSize;
    }

    /**
     * Assigns a new texture size, in pixels. Returns this {@code Config} instance. Only relevant for Palette->PVRZ
     * conversion.
     */
    public Config setTextureSize(int textureSize) {
      this.textureSize = ensureBinarySize(Math.max(getTileSize(), Math.min(getMaxTextureSize(), textureSize)));
      return this;
    }

    /** Returns the max. supported texture size, in pixels. Only relevant for Palette->PVRZ conversion. */
    public int getMaxTextureSize() {
      return MAX_TEXTURE_SIZE;
    }

    /** Returns the start index for PVRZ texture files. A PVRZ file index cannot exceed 99. Default: 0 */
    public int getPvrzBaseIndex() {
      return pvrzBaseIndex;
    }

    /** Sets the start index for PVRZ texture files. A PVRZ file index cannot exceed 99. */
    public Config setPvrzBaseIndex(int pvrzBaseIndex) {
      this.pvrzBaseIndex = Math.max(0, Math.min(99, pvrzBaseIndex));
      return this;
    }

    /** Returns the assigned size of border tiles rows or columns. Only relevant for Palette->PVRZ conversion. */
    public int getBorderSize() {
      return borderSize;
    }

    /**
     * Assigns a new size of rows or columns for border tiles. Returns this {@code Config} instance. Only relevant for
     * Palette->PVRZ conversion.
     */
    public Config setBorderSize(int borderSize) {
      this.borderSize = Math.max(0, Math.min(getTileSize(), borderSize));
      setSegmentSize(getSegmentSize()); // segment size may change
      return this;
    }

    /** Returns the max. size of contiguous tile segments to be placed on PVRZ textures, in pixels. */
    public int getSegmentSize() {
      return segmentSize;
    }

    /** Sets the max. size of contiguous tile segments, in pixels. */
    public Config setSegmentSize(int segmentSize) {
      final int minSize = getTileSize() + getBorderSize() * 2;
      this.segmentSize = Math.max(minSize, Math.min(getMaxTextureSize(), segmentSize));
      return this;
    }

    /**
     * Returns whether black tiles should be detected and replaced by a default. Only relevant for Palette->PVRZ
     * conversion.
     */
    public boolean isDetectBlack() {
      return detectBlack;
    }

    /**
     * Sets whether black tiles should be detected and replaced by a default. Only relevant for Palette->PVRZ
     * conversion.
     */
    public Config setDetectBlack(boolean set) {
      this.detectBlack = set;
      return this;
    }

    /** Returns whether to use multithreading to encode PVRZ textures. Only relevant for Palette->PVRZ conversion. */
    public boolean isMultithreaded() {
      return multithreaded;
    }

    /** Sets whether to use multithreading to encode PVRZ textures. Only relevant for Palette->PVRZ conversion. */
    public Config setMultithreaded(boolean set) {
      this.multithreaded = set;
      return this;
    }

    /** Returns the assigned overlay tile conversion mode. */
    public OverlayConversion getOverlayConversion() {
      return overlayConversion;
    }

    /** Assigns a new overlay tile conversion mode. Returns this {@code Config} instance. */
    public Config setOverlayConversion(OverlayConversion conversion) {
      this.overlayConversion = validateOverlayConversion(conversion, decoder);
      return this;
    }

    /** Initializes and returns a new {@link WedInfo} object based on current data. */
    private WedInfo initWedInfo(ResourceEntry wedEntry) throws Exception {
      // getting information from associated WED if available
      if (wedEntry == null) {
        wedEntry = findWed(decoder.getResourceEntry(), true);
      }
      WedInfo wedInfo = null;
      if (wedEntry != null) {
        try {
          final WedResource wed = (WedResource) ResourceFactory.getResource(wedEntry);
          if (wed != null) {
            wedInfo = new WedInfo(wed);
          }
        } catch (Exception e) {
          Logger.error(e);
        }
      }

      // generating tileset information if WED is not available
      if (wedInfo == null) {
        final int numTiles = decoder.getTileCount();
        final int w = defaultTilesPerRow;
        int numPriTiles = w * defaultRowCount;
        final int h;
        if (numTiles - numPriTiles > numPriTiles) {
          // ensure that secondary tile count does NOT exceed primary tile count
          h = (numTiles / 2 + w - 1) / w;
          numPriTiles = w * h;
        } else {
          h = defaultRowCount;
        }

        // Laying out tiles sequentially: left>right, top>bottom
        // Secondary tiles are sequentially added to primary tiles
        final List<TileInfo> tiles = new ArrayList<>(w * h);
        for (int i = 0; i < numPriTiles; i++) {
          final int priTileIdx = i;
          final int secTileIdx = (numPriTiles + i) < numTiles ? numPriTiles + i : -1;
          tiles.add(new TileInfo(new int[] { priTileIdx }, secTileIdx, 0));
        }
        wedInfo = new WedInfo(w, h, tiles, null);
      }

      return wedInfo;
    }

    /**
     * Returns the given size rounded up to the next power-of-two value.
     * No change if given value is already a power of two.
     */
    private static int ensureBinarySize(int size) {
      for (int n = 1; n < 31; n++) {
        final int binSize = 1 << n;
        if (size <= binSize) {
          return binSize;
        }
      }
      return 1 << 31;
    }

    /**
     * Checks if the given overlay conversion mode is valid for the specified TIS decoder.
     * Returns the default overlay conversion mode {@link OverlayConversion#NONE} if the check fails.
     */
    private static OverlayConversion validateOverlayConversion(OverlayConversion convert, TisDecoder decoder) {
      OverlayConversion retVal = convert;
      if (retVal == null) {
        retVal = OverlayConversion.NONE;
      } else {
        if (decoder instanceof TisV1Decoder) {
          switch (retVal) {
            case BGEE_TO_BG1:
            case BGEE_TO_BG2:
            case BG2EE_TO_BG1:
            case BG2EE_TO_BG2:
              Logger.warn("Unsupported overlay conversion mode: {}", retVal);
              retVal = OverlayConversion.NONE;
              break;
            default:
          }
        } else {
          switch (retVal) {
            case BG1_TO_BGEE:
            case BG1_TO_BG2EE:
            case BG2_TO_BGEE:
            case BG2_TO_BG2EE:
              Logger.warn("Unsupported overlay conversion mode: {}", retVal);
              retVal = OverlayConversion.NONE;
              break;
            default:
          }
        }
      }
      return retVal;
    }

    @Override
    public int hashCode() {
      return Objects.hash(borderSize, decoder, defaultRowCount, defaultTilesPerRow, overlayConversion, textureSize,
          tisFile);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Config other = (Config)obj;
      return borderSize == other.borderSize && Objects.equals(decoder, other.decoder)
          && defaultRowCount == other.defaultRowCount && defaultTilesPerRow == other.defaultTilesPerRow
          && overlayConversion == other.overlayConversion && textureSize == other.textureSize
          && Objects.equals(tisFile, other.tisFile);
    }

    @Override
    public String toString() {
      return "Config [tisFile=" + tisFile + ", decoder=" + decoder + ", defaultTilesPerRow=" + defaultTilesPerRow
          + ", defaultRowCount=" + defaultRowCount + ", textureSize=" + textureSize + ", borderSize=" + borderSize
          + ", overlayConversion=" + overlayConversion + "]";
    }
  }

  /**
   * This class provides convenient access to WED content.
   */
  public static class WedInfo {
    /** List of tilemap entries from the primary overlay. */
    private final List<TileInfo> tiles = new ArrayList<>();

    /** Door entries consists of indices to {@link TileInfo} items in the {@code tiles} list. */
    private final List<int[]> doors = new ArrayList<>();

    private final WedResource wed;

    /** Tileset width, in tiles. */
    private final int width;
    /** Tileset height, in tiles. */
    private final int height;

    /**
     * Creates a new {@code WedInfo} structure from a WED resource.
     *
     * @param wed WED resource.
     * @throws Exception if wed data could not be retrieved.
     */
    public WedInfo(WedResource wed) throws Exception {
      Objects.requireNonNull(wed);
      this.wed = wed;
      if (((IsNumeric) wed.getAttribute(WedResource.WED_NUM_OVERLAYS)).getValue() <= 0) {
        throw new Exception(wed.getResourceEntry().getResourceName() + ": No wed overlays available");
      }

      // loading primary overlay
      final Overlay ovl = (Overlay) Objects.requireNonNull(wed.getAttribute(Overlay.WED_OVERLAY + " 0"));
      width = ((IsNumeric) ovl.getAttribute(Overlay.WED_OVERLAY_WIDTH)).getValue();
      height = ((IsNumeric) ovl.getAttribute(Overlay.WED_OVERLAY_HEIGHT)).getValue();
      final List<StructEntry> tmList = ovl.getFields(Tilemap.class);
      for (final StructEntry se : tmList) {
        if (se instanceof Tilemap) {
          tiles.add(new TileInfo((Tilemap) se));
        }
      }

      // loading door tiles
      final List<StructEntry> doorList = wed.getFields(Door.class);
      for (final StructEntry se : doorList) {
        if (se instanceof Door) {
          final Door door = (Door) se;
          int numIndices = ((IsNumeric) door.getAttribute(Door.WED_DOOR_NUM_TILEMAP_INDICES)).getValue();
          final int[] indices = new int[numIndices];
          for (int i = 0; i < numIndices; i++) {
            indices[i] = ((IsNumeric) door.getAttribute(Door.WED_DOOR_TILEMAP_INDEX + " " + i)).getValue();
          }
          doors.add(indices);
        }
      }
    }

    /**
     * Creates a new {@code WedInfo} structure from manual data.
     *
     * @param width Number of tiles in horizontal direction.
     * @param height Number of tiles in vertical direction.
     * @param tiles Array of {@link TileInfo} structures with tile information.
     * @throws Exception if parameters contain invalid data.
     */
    public WedInfo(int width, int height, List<TileInfo> tiles, List<int[]> doors) throws Exception {
      Objects.requireNonNull(tiles);
      if (width <= 0) {
        throw new Exception("Invalid width: " + width);
      }
      if (height <= 0) {
        throw new Exception("Invalid height: " + height);
      }
      if (tiles.size() < width * height) {
        throw new Exception("Invalid number of tile definitions. Expected: " + (width * height) + ", Found: " + tiles.size());
      }

      this.wed = null;
      this.width = width;
      this.height = height;
      this.tiles.addAll(tiles);
      if (doors != null) {
        this.doors.addAll(doors);
      }
    }

    /** Returns whether this {@code WedInfo} object is based on a {@link WedResource} instance. */
    public boolean hasWedResource() {
      return (wed != null);
    }

    /** Returns the linked {@link WedResource} instance. Return value may be {@code null}. */
    public WedResource getWedResource() {
      return wed;
    }

    /** Returns the horizontal tileset size, in tiles. */
    public int getWidth() {
      return width;
    }

    /** Returns the vertical tileset size, in tiles. */
    public int getHeight() {
      return height;
    }

    /** Returns the number of available tiles. */
    public int getTileCount() {
      return tiles.size();
    }

    /**
     * Returns a {@code OverlayTileInfo} structure with detailed information about the overlay tile at the given index.
     *
     * @param index Overlay tile index.
     * @return {@link TileInfo} structure with detailed information about the requested tile.
     * @throws IndexOutOfBoundsException if the tile index is out of bounds.
     */
    public TileInfo getTile(int index) throws IndexOutOfBoundsException {
      return tiles.get(index);
    }

    /**
     * Returns a {@code OverlayTileInfo} structure with detailed information about the overlay tile at the given
     * tileset location.
     *
     * @param x X coordinate of the tile in the tileset.
     * @param y Y coordinate of the tile in the tileset.
     * @return {@link TileInfo} structure with detailed information about the requested tile.
     * @throws IndexOutOfBoundsException if a coordinate points to a location outside of the tileset.
     */
    public TileInfo getTile(int x, int y) throws IndexOutOfBoundsException {
      if (x < 0 || x >= width) {
        throw new IndexOutOfBoundsException("X: " + x + ", Width: " + width);
      }
      if (y < 0 || y >= height) {
        throw new IndexOutOfBoundsException("Y: " + y + ", Height: " + height);
      }
      return tiles.get(y * width + x);
    }

    /**
     * Returns a {@code OverlayTileInfo} structure with detailed information about the overlay tile at the given
     * tileset location.
     *
     * @param p Location of the tile in the tileset.
     * @return {@link TileInfo} structure with detailed information about the requested tile.
     * @throws IndexOutOfBoundsException if a coordinate points to a location outside of the tileset.
     */
    public TileInfo getTile(Point p) throws IndexOutOfBoundsException {
      Objects.requireNonNull(p, "Point is null");

      if (p.x < 0 || p.x >= width) {
        throw new IndexOutOfBoundsException("X: " + p.x + ", Width: " + width);
      }
      if (p.y < 0 || p.y >= height) {
        throw new IndexOutOfBoundsException("Y: " + p.y + ", Height: " + height);
      }
      return tiles.get(p.y * width + p.x);
    }

    /**
     * Returns the location of the specified tile on the tile map. Both primary and secondary indices are considered.
     *
     * @param tileIndex tile index.
     * @return {@link Point} of the tile on the tile map. Returns {@code null} if the tile index is not referenced by
     *         any tile definition.
     */
    public Point getTileLocation(int tileIndex) {
      if (tileIndex < 0) {
        return null;
      }
      if (tileIndex < tiles.size()) {
        return new Point(tileIndex % width, tileIndex / width);
      } else {
        for (int idx = 0, size = tiles.size(); idx < size; idx++) {
          final TileInfo tileInfo = tiles.get(idx);
          boolean match = tileInfo.tileSecondary == tileIndex;
          if (!match) {
            for (final int frame : tileInfo.tilesPrimary) {
              match = (frame == tileIndex);
              if (match) {
                break;
              }
            }
          }
          if (match) {
            return new Point(idx % width, idx / width);
          }
        }
      }
      return null;
    }

    /** Returns the number of available doors. */
    public int getDoorCount() {
      return doors.size();
    }

    /**
     * Returns the number of tilemap indices for the specified door.
     *
     * @param doorIndex The door index.
     * @return Number of tiles referenced by the specified door.
     * @throws IndexOutOfBoundsException if the door index is out of bounds.
     */
    public int getDoorTileCount(int doorIndex) throws IndexOutOfBoundsException {
      return doors.get(doorIndex).length;
    }

    /**
     * Returns an array of tilemap indices for the specified door.
     *
     * @param doorIndex The door index.
     * @return Array of tilemap indices for the specified door. These indices can be used by {@link #getTile(int)} to
     *         get detailed tile information.
     * @throws IndexOutOfBoundsException if the door index is out of bounds.
     */
    public int[] getDoorTileIndices(int doorIndex) throws IndexOutOfBoundsException {
      final int[] ar = doors.get(doorIndex);
      return Arrays.copyOf(ar, ar.length);
    }

    @Override
    public int hashCode() {
      return Objects.hash(doors, height, tiles, width);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      WedInfo other = (WedInfo)obj;
      return Objects.equals(doors, other.doors) && height == other.height && Objects.equals(tiles, other.tiles)
          && width == other.width;
    }

    @Override
    public String toString() {
      return "WedInfo [width=" + width + ", height=" + height + ", tiles=" + tiles + ", doors=" + doors + "]";
    }
  }

  /**
   * This class stores information about a single WED overlay tile.
   */
  public static class TileInfo {
    /** The primary tile index. First primary tile index for animated tiles. */
    public int tilePrimary;

    /** Complete list of primary tile indices. */
    public int[] tilesPrimary;

    /** Secondary tile index. */
    public int tileSecondary;

    /** Overlay flags. */
    public int flags;

    /**
     * Creates a new overlay tile structure from a {@code WedResource} and indices to overlay and tilemap.
     *
     * @param wed the {@link WedResource}.
     * @param overlayIndex WED overlay index.
     * @param tileMapIndex Tilemap index in the overlay structure.
     * @throws Exception if information could not be retrieved.
     */
    public TileInfo(WedResource wed, int overlayIndex, int tileMapIndex) throws Exception {
      this((Overlay) Objects.requireNonNull(wed).getAttribute(Overlay.WED_OVERLAY + " " + overlayIndex), tileMapIndex);
    }

    /**
     * Creates a new overlay tile structure from a WED {@code Overlay} and index to tilemap.
     *
     * @param ovl the WED {@link Overlay} structure.
     * @param tileMapIndex Tilemap index.
     * @throws Exception if information could not be retrieved.
     */
    public TileInfo(Overlay ovl, int tileMapIndex) throws Exception {
      this((Tilemap) Objects.requireNonNull(ovl).getAttribute(Tilemap.WED_TILEMAP + " " + tileMapIndex));
    }

    /**
     * Creates a new overlay tile structure from a WED overlay tilemap.
     *
     * @param tileMap the WED overlay {@link Tilemap} structure. This structure must be a child of a parent
     *                  {@link Overlay} structure.
     * @throws Exception if information could not be retrieved.
     */
    public TileInfo(Tilemap tileMap) throws Exception {
      Objects.requireNonNull(tileMap);

      final Overlay ovl = (Overlay) tileMap.getParent();
      int tileMapIdx = ((IsNumeric) Objects.requireNonNull(tileMap.getAttribute(Tilemap.WED_TILEMAP_TILE_INDEX_PRI))).getValue();
      int count = ((IsNumeric) Objects.requireNonNull(tileMap.getAttribute(Tilemap.WED_TILEMAP_TILE_COUNT_PRI))).getValue();
      if (count <= 0) {
        throw new Exception("Tile count should not be zero or negative");
      }
      tilesPrimary = new int[count];
      for (int i = 0; i < count; i++) {
        tilesPrimary[i] = ((IsNumeric) Objects.requireNonNull(
            ovl.getAttribute(Overlay.WED_OVERLAY_TILEMAP_INDEX + " " + (tileMapIdx + i)))).getValue();
      }
      tilePrimary = tilesPrimary[0];

      tileSecondary = ((IsNumeric) Objects.requireNonNull(tileMap.getAttribute(Tilemap.WED_TILEMAP_TILE_INDEX_SEC))).getValue();
      flags = ((IsNumeric) Objects.requireNonNull(tileMap.getAttribute(Tilemap.WED_TILEMAP_DRAW_OVERLAYS))).getValue();
    }

    /**
     * Creates a new overlay tile structure and initialize it with manual values.
     *
     * @param tilesPrimary List of primary tile indices.
     * @param tileSecondary Secondary tile index.
     * @param flags Overlay flags.
     * @throws Exception if {@code tilesPrimary} is empty.
     */
    public TileInfo(int[] tilesPrimary, int tileSecondary, int flags) throws Exception {
      if (Objects.requireNonNull(tilesPrimary).length == 0) {
        throw new Exception("No primary tile indices specified");
      }
      this.tilesPrimary = Arrays.copyOf(tilesPrimary, tilesPrimary.length);
      this.tilePrimary = this.tilesPrimary[0];
      this.tileSecondary = tileSecondary;
      this.flags = flags;
    }

    /**
     * Returns the primary tile frame the specified tile index is assigned to.
     *
     * @param index Primary tile index.
     * @return Tile frame of the given tile {@code index}. This is {@code 0} for single frame primary tiles, a frame
     *         within the range {@code [0,tilesPrimary.length]} for animated tiles, and {@code -1} for secondary or
     *         non-existing tiles.
     */
    public int getPrimaryTileFrame(int index) {
      int retVal = -1;
      for (int i = 0; i < tilesPrimary.length; i++) {
        if (tilesPrimary[i] == index) {
          retVal = i;
          break;
        }
      }
      return retVal;
    }

    /** Returns the primary tile index. */
    public int getPrimaryTileIndex() {
      return tilePrimary;
    }

    /**
     * Returns the primary tile index that is assigned to the specified tile frame.
     *
     * @param frame Frame index of the primary tile.
     * @return Index of the primary tile at the given frame. {@code frame} is clamped to the available tile range.
     *         Always returns the index of the first primary tile if {@code frame} is negative.
     */
    public int getPrimaryTileIndex(int frame) {
      if (frame >= 0) {
        return tilesPrimary[frame % tilesPrimary.length];
      } else {
        return tilePrimary;
      }
    }

    /** Returns the secondary tile index. */
    public int getSecondaryTileIndex() {
      return tileSecondary;
    }

    /** Returns the tile overlay flags. */
    public int getFlags() {
      return flags;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + Arrays.hashCode(tilesPrimary);
      result = prime * result + Objects.hash(flags, tileSecondary);
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      TileInfo other = (TileInfo)obj;
      return flags == other.flags && tileSecondary == other.tileSecondary
          && Arrays.equals(tilesPrimary, other.tilesPrimary);
    }

    @Override
    public String toString() {
      return "OverlayTile [tilesPrimary=" + Arrays.toString(tilesPrimary) + ", tileSecondary=" + tileSecondary
          + ", flags=" + flags + "]";
    }
  }

  /**
   * This class stores the visual state of a single tile for the {@link TileMap}. It is used by child elements
   * in the {@link TileMap} class.
   */
  public static class TileMapItem {
    /**
     * Indicates that the top-most rows of pixels should be considered (<code>y >= 0</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_TOP    = 0;
    /**
     * Indicates that the bottom-most rows of pixels should be considered (<code>y <= height-1</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_BOTTOM = 1;
    /**
     * Indicates that the left-most columns of pixels should be considered (<code>x >= 0</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_LEFT   = 2;
    /**
     * Indicates that the right-most columns of pixels should be considered (<code>x <= width-1</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_RIGHT  = 3;
    /**
     * Indicates that the top-left corner should be considered (<code>x >= 0 && y >= 0</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_TOP_LEFT  = 4;
    /**
     * Indicates that the top-right corner should be considered (<code>x <= width-1 && y >= 0</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_TOP_RIGHT  = 5;
    /**
     * Indicates that the bottom-left corner should be considered (<code>x >= 0 && y <= height-1</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_BOTTOM_LEFT  = 6;
    /**
     * Indicates that the top-left corner should be considered (<code>x <= width-1 && y <= height-1</code>).
     * This flag cannot be combined with {@link #FLAG_ALL}.
     */
    public static final int FLAG_BOTTOM_RIGHT  = 7;
    /**
     * Indicates that the full tile data should be considered. This flag cannot be combined with the other
     * {@code FLAG_xxx} constants.
     */
    public static final int FLAG_ALL    = 8;

    private final BitSet flags = new BitSet(FLAG_ALL + 1);
    private final TileEntry tileEntry;

    /**
     * Creates a {@code TileInfo} structure with an empty tile index.
     */
    public TileMapItem() {
      this(-1);
    }

    public TileMapItem(int tileIndex, int... flags) {
      this.tileEntry = new TileEntry(tileIndex, -1, 0, 0);
      for (final int flag : flags) {
        setFlag(flag, true);
      }
    }

    public TileMapItem(TileMapItem item) {
      Objects.requireNonNull(item);
      this.tileEntry = new TileEntry(item.tileEntry);
      for (int i = 0, size = item.flags.size(); i < size; i++) {
        if (item.flags.get(i)) {
          this.flags.set(i);
        }
      }
    }

    /** Returns the tile index. */
    public int getIndex() {
      return this.tileEntry.tileIndex;
    }

    /** Sets the tile index value. */
    public TileMapItem setIndex(int index) {
      this.tileEntry.tileIndex = index;
      return this;
    }

    /** Returns the pvrz texture page. */
    public int getPage() {
      return this.tileEntry.page;
    }

    /** Sets the pvrz texture page. */
    public TileMapItem setPage(int page) {
      this.tileEntry.page = page;
      return this;
    }

    /** Returns the x coordinate of the tile on the pvrz texture. */
    public int getX() {
      return this.tileEntry.x;
    }

    /** Sets the x coordinate of the tile on the pvrz texture. */
    public TileMapItem setX(int x) {
      this.tileEntry.x = x;
      return this;
    }

    /** Returns the y coordinate of the tile on the pvrz texture. */
    public int getY() {
      return this.tileEntry.y;
    }

    /** Sets the y coordinate of the tile on the pvrz texture. */
    public TileMapItem setY(int y) {
      this.tileEntry.y = y;
      return this;
    }

    /**
     * General method for setting or clearing specific tile flags.
     *
     * @param flag The flag (see {@code FLAG_xxx} constants).
     * @param set Indicates whether the flag should be set or cleared.
     */
    public TileMapItem setFlag(int flag, boolean set) {
      if (flag >= 0) {
        if (set) {
          if (flag == FLAG_ALL) {
            flags.clear();
          } else {
            flags.clear(FLAG_ALL);
          }
        }
        flags.set(flag, set);
      }
      return this;
    }

    /** Unsets all tile flags. */
    public TileMapItem clearFlags() {
      flags.clear();
      return this;
    }

    /** Returns whether the specified flag is set. */
    public boolean isFlag(int flag) {
      return flags.get(flag);
    }

    /** Returns whether {@link #FLAG_TOP} is set. */
    public boolean isTopFlag() {
      return flags.get(FLAG_TOP);
    }

    /** Sets the state of {@link #FLAG_TOP}. */
    public TileMapItem setTopFlag(boolean set) {
      flags.set(FLAG_TOP, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_BOTTOM} is set. */
    public boolean isBottomFlag() {
      return flags.get(FLAG_BOTTOM);
    }

    /** Sets the state of {@link #FLAG_BOTTOM}. */
    public TileMapItem setBottomFlag(boolean set) {
      flags.set(FLAG_BOTTOM, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_LEFT} is set. */
    public boolean isLeftFlag() {
      return flags.get(FLAG_LEFT);
    }

    /** Sets the state of {@link #FLAG_LEFT}. */
    public TileMapItem setLeftFlag(boolean set) {
      flags.set(FLAG_LEFT, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_RIGHT} is set. */
    public boolean isRightFlag() {
      return flags.get(FLAG_RIGHT);
    }

    /** Sets the state of {@link #FLAG_RIGHT}. */
    public TileMapItem setRightFlag(boolean set) {
      flags.set(FLAG_RIGHT, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_TOP_LEFT} is set. */
    public boolean isTopLeftFlag() {
      return flags.get(FLAG_TOP_LEFT);
    }

    /** Sets the state of {@link #FLAG_TOP_LEFT}. */
    public TileMapItem setTopLeftFlag(boolean set) {
      flags.set(FLAG_TOP_LEFT, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_TOP_RIGHT} is set. */
    public boolean isTopRightFlag() {
      return flags.get(FLAG_TOP_RIGHT);
    }

    /** Sets the state of {@link #FLAG_TOP_RIGHT}. */
    public TileMapItem setTopRightFlag(boolean set) {
      flags.set(FLAG_TOP_RIGHT, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_BOTTOM_LEFT} is set. */
    public boolean isBottomLeftFlag() {
      return flags.get(FLAG_BOTTOM_LEFT);
    }

    /** Sets the state of {@link #FLAG_BOTTOM_LEFT}. */
    public TileMapItem setBottomLeftFlag(boolean set) {
      flags.set(FLAG_BOTTOM_LEFT, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_BOTTOM_RIGHT} is set. */
    public boolean isBottomRightFlag() {
      return flags.get(FLAG_BOTTOM_RIGHT);
    }

    /** Sets the state of {@link #FLAG_BOTTOM_RIGHT}. */
    public TileMapItem setBottomRightFlag(boolean set) {
      flags.set(FLAG_BOTTOM_RIGHT, set);
      if (set) {
        flags.clear(FLAG_ALL);
      }
      return this;
    }

    /** Returns whether {@link #FLAG_ALL} is set. */
    public boolean isAllFlag() {
      return flags.get(FLAG_ALL);
    }

    /** Sets the state of {@link #FLAG_TOP}. If {@code set} is {@code true} then all other flags are cleared. */
    public TileMapItem setAllFlag(boolean set) {
      if (set) {
        flags.clear();
      }
      flags.set(FLAG_TOP, set);
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(flags, tileEntry);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      TileMapItem other = (TileMapItem)obj;
      return Objects.equals(flags, other.flags) && Objects.equals(tileEntry, other.tileEntry);
    }

    @Override
    public String toString() {
      return "TileInfo [flags=" + flags + ", tileEntry=" + this.tileEntry + "]";
    }
  }

  /**
   * This class defines the layout of a tileset region on a virtual canvas.
   */
  public static class TileMap {
    private final HashMap<Point, TileMapItem> tiles = new HashMap<>();
//    private final TreeMap<Point, TileMapItem> tiles =
//        new TreeMap<>((p1, p2) -> (p1.y < p2.y) ? -1 : ((p1.y > p2.y) ? 1 : (p1.x - p2.x)));
    private final Rectangle pageRect = new Rectangle();
    private final WedInfo wedInfo;
    private final int tileSize;

    private int pageIndex;

    private Rectangle bounds;
    private int boundsHash;

    public TileMap(WedInfo wedInfo, int tileSize) {
      this.wedInfo = wedInfo;
      this.tileSize = Math.max(1, tileSize);
      this.pageIndex = -1;
    }

    /** Returns the associated {@link WedInfo} instance. Returns {@code null} if no {@code WedInfo} object is available. */
    public WedInfo getWedInfo() {
      return wedInfo;
    }

    /** Returns {@code true} if this tile map contains no tile definitions. */
    public boolean isEmpty() {
      return tiles.isEmpty();
    }

    /** Returns the number of defined tile definitions in this tile map. */
    public int size() {
      return tiles.size();
    }

    public TileMapItem getTile(int x, int y) {
      return getTile(new Point(x, y));
    }

    public TileMapItem getTile(Point p) {
      return tiles.get(p);
    }

    public TileMapItem setTile(int x, int y, int tileIndex, int... flags) {
      return setTile(new Point(x, y), tileIndex, flags);
    }

    public TileMapItem setTile(Point p, int tileIndex, int... flags) {
      Objects.requireNonNull(p, "Point is null");
      final TileMapItem ti = new TileMapItem(tileIndex, flags);
      return tiles.put(new Point(p), ti);
    }

    public TileMapItem setTile(Point p, TileMapItem tile) {
      Objects.requireNonNull(p, "Point is null");
      Objects.requireNonNull(tile, "Item is null");
      return tiles.put(new Point(p), new TileMapItem(tile));
    }

    /**
     * Removes the tile at the specified tileset location and returns it. Returns {@code null} if the location is
     * invalid or doesn't contain any tile definitions.
     */
    public TileMapItem removeTile(Point p) {
      return tiles.remove(p);
    }

    /**
     * Returns copies of all available tile positions as a list of {@code Point} objects.
     *
     * @param sorted Indicates whether list entries should be sorted (from top-left to bottom-right.)
     * @return {@link List} of {@link Point} objects.
     */
    public List<Point> getAllTilePositions(boolean sorted) {
      final List<Point> retVal = new ArrayList<>(tiles.size());
      tiles.keySet().forEach(p -> retVal.add(new Point(p)));
      if (sorted) {
        retVal.sort((p1, p2) -> (p1.y < p2.y) ? -1 : ((p1.y > p2.y) ? 1 : (p1.x - p2.x)));
      }
      return retVal;
    }

    /**
     * Returns the bounds of the whole tile map.
     *
     * @param ignoreBorders Specifies whether border tiles should be included in the bounding box calculation.
     * @return A {@link Rectangle} that includes all tiles of the tile map.
     */
    public Rectangle getTileBounds(boolean ignoreBorders) {
      if (boundsHash != tiles.hashCode()) {
        bounds = null;
      }
      if (bounds == null) {
        if (tiles.isEmpty()) {
          bounds = new Rectangle();
        } else {
          int minX = Integer.MAX_VALUE;
          int maxX = Integer.MIN_VALUE;
          int minY = Integer.MAX_VALUE;
          int maxY = Integer.MIN_VALUE;
          for (final Map.Entry<Point, TileMapItem> entry : tiles.entrySet()) {
            if (!ignoreBorders || entry.getValue().isAllFlag()) {
              final Point p = entry.getKey();
              minX = Math.min(minX, p.x);
              maxX = Math.max(maxX, p.x);
              minY = Math.min(minY, p.y);
              maxY = Math.max(maxY, p.y);
            }
          }
          bounds = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }
      }
      return bounds;
    }

    /**
     * Returns the pixel-exact bounding box of the whole tilemap, including border tiles.
     *
     * @param borderSize Number of pixels per row or column from border tiles to add to the bounding box calculation.
     *                     Specify a size of {@code 0} to ignore border tiles completely.
     * @return Bounding {@link Rectangle} of the tile map, in pixels.
     */
    public Rectangle getPixelBounds(int borderSize) {
      borderSize = Math.min(tileSize, Math.max(0, borderSize));

      final Rectangle retVal = new Rectangle();
      int minX = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;
      for (final Map.Entry<Point, TileMapItem> entry : tiles.entrySet()) {
        final Point p = entry.getKey(); // tile coordinate space
        final Point p2 = new Point(p.x * tileSize, p.y * tileSize); // pixel coordinate space
        final TileMapItem ti = entry.getValue();
        if (ti.isAllFlag()) {
          minX = Math.min(minX, p2.x);
          maxX = Math.max(maxX, p2.x + tileSize);
          minY = Math.min(minY, p2.y);
          maxY = Math.max(maxY, p2.y + tileSize);
        }
        if (ti.isLeftFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x);
          maxX = Math.max(maxX, p2.x + borderSize);
          minY = Math.min(minY, p2.y);
          maxY = Math.max(maxY, p2.y + tileSize);
        }
        if (ti.isRightFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x + tileSize - borderSize);
          maxX = Math.max(maxX, p2.x + tileSize);
          minY = Math.min(minY, p2.y);
          maxY = Math.max(maxY, p2.y + tileSize);
        }
        if (ti.isTopFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x);
          maxX = Math.max(maxX, p2.x + tileSize);
          minY = Math.min(minY, p2.y);
          maxY = Math.max(maxY, p2.y + borderSize);
        }
        if (ti.isBottomFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x);
          maxX = Math.max(maxX, p2.x + tileSize);
          minY = Math.min(minY, p2.y + tileSize - borderSize);
          maxY = Math.max(maxY, p2.y + tileSize);
        }
        if (ti.isTopLeftFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x);
          maxX = Math.max(maxX, p2.x + borderSize);
          minY = Math.min(minY, p2.y);
          maxY = Math.max(maxY, p2.y + borderSize);
        }
        if (ti.isTopRightFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x + tileSize - borderSize);
          maxX = Math.max(maxX, p2.x + tileSize);
          minY = Math.min(minY, p2.y);
          maxY = Math.max(maxY, p2.y + borderSize);
        }
        if (ti.isBottomLeftFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x);
          maxX = Math.max(maxX, p2.x + borderSize);
          minY = Math.min(minY, p2.y + tileSize - borderSize);
          maxY = Math.max(maxY, p2.y + tileSize);
        }
        if (ti.isBottomRightFlag() && borderSize > 0) {
          minX = Math.min(minX, p2.x + tileSize - borderSize);
          maxX = Math.max(maxX, p2.x + tileSize);
          minY = Math.min(minY, p2.y + tileSize - borderSize);
          maxY = Math.max(maxY, p2.y + tileSize);
        }
      }

      if (minX != Integer.MAX_VALUE && maxX != Integer.MIN_VALUE &&
          minY != Integer.MAX_VALUE && maxY != Integer.MIN_VALUE) {
        retVal.x = minX;
        retVal.y = minY;
        retVal.width = maxX - minX;
        retVal.height = maxY - minY;
      }

      return retVal;
    }

    /** Returns whether the tile map has border tiles on the left-most side. */
    public boolean hasLeftBorder() {
      final Rectangle bounds = getTileBounds(false);
      final Point p = new Point(bounds.x, bounds.y);
      int retVal = 0;
      for (int y = 0; y < bounds.height; y++) {
        final TileMapItem tmi = tiles.get(p);
        if (tmi != null) {
          if (tmi.isRightFlag() || tmi.isTopRightFlag() || tmi.isBottomRightFlag()) {
            retVal = Math.max(retVal, 1);
          } else if (tmi.isAllFlag()) {
            retVal = Math.max(retVal, 2);
            break;
          }
        }
        p.y++;
      }
      return (retVal == 1);
    }

    /** Returns whether the tile map has border tiles on the top-most side. */
    public boolean hasTopBorder() {
      final Rectangle bounds = getTileBounds(false);
      final Point p = new Point(bounds.x, bounds.y);
      int retVal = 0;
      for (int x = 0; x < bounds.width; x++) {
        final TileMapItem tmi = tiles.get(p);
        if (tmi != null) {
          if (tmi.isBottomFlag() || tmi.isBottomLeftFlag() || tmi.isBottomRightFlag()) {
            retVal = Math.max(retVal, 1);
          } else if (tmi.isAllFlag()) {
            retVal = Math.max(retVal, 2);
            break;
          }
        }
        p.x++;
      }
      return (retVal == 1);
    }

    /** Returns whether the tile map has border tiles on the right-most side. */
    public boolean hasRightBorder() {
      final Rectangle bounds = getTileBounds(false);
      final Point p = new Point(bounds.x + bounds.width - 1, bounds.y);
      int retVal = 0;
      for (int y = 0; y < bounds.height; y++) {
        final TileMapItem tmi = tiles.get(p);
        if (tmi != null) {
          if (tmi.isLeftFlag() || tmi.isTopLeftFlag() || tmi.isBottomLeftFlag()) {
            retVal = Math.max(retVal, 1);
          } else if (tmi.isAllFlag()) {
            retVal = Math.max(retVal, 2);
            break;
          }
        }
        p.y++;
      }
      return (retVal == 1);
    }

    /** Returns whether the tile map has border tiles on the bottom-most side. */
    public boolean hasBottomBorder() {
      final Rectangle bounds = getTileBounds(false);
      final Point p = new Point(bounds.x, bounds.y + bounds.height - 1);
      int retVal = 0;
      for (int x = 0; x < bounds.width; x++) {
        final TileMapItem tmi = tiles.get(p);
        if (tmi != null) {
          if (tmi.isTopFlag() || tmi.isTopLeftFlag() || tmi.isTopRightFlag()) {
            retVal = Math.max(retVal, 1);
          } else if (tmi.isAllFlag()) {
            retVal = Math.max(retVal, 2);
            break;
          }
        }
        p.x++;
      }
      return (retVal == 1);
    }

    /** Returns the pvrz texture page index. */
    public int getPage() {
      return pageIndex;
    }

    /** Returns the bounding rectangle of the region on the pvrz texture. */
    public Rectangle getPageRect() {
      return pageRect;
    }

    /**
     * Maps tile of this map to a pvrz texture.
     *
     * @param pageIndex The texture page index. Specify -1 to invalidate texture mapping data.
     * @param pageRect Bounding rectangle of the region on the texture page.
     * @param borderSize Size of border tiles to consider.
     */
    public void setPageRect(int pageIndex, Rectangle pageRect, int borderSize) {
      pageIndex = Math.max(-1, Math.min(99, pageIndex));
      if (pageIndex < 0 || pageRect == null) {
        pageRect = new Rectangle();
      }

      this.pageIndex = pageIndex;
      this.pageRect.x = pageRect.x;
      this.pageRect.y = pageRect.y;
      this.pageRect.width = pageRect.width;
      this.pageRect.height = pageRect.height;

      final Rectangle tileRect = getTileBounds(false);
      final int xOfs = hasLeftBorder() ? borderSize - tileSize : 0;
      final int yOfs = hasTopBorder() ? borderSize - tileSize : 0;
      for (final Map.Entry<Point, TileMapItem> entry : tiles.entrySet()) {
        final Point p = entry.getKey();
        final TileMapItem tmi = entry.getValue();
        final int x = (pageIndex >= 0) ? (pageRect.x + ((p.x - tileRect.x) * tileSize) + xOfs) : 0;
        final int y = (pageIndex >= 0) ? (pageRect.y + ((p.y - tileRect.y) * tileSize) + yOfs) : 0;
        tmi.setPage(pageIndex).setX(x).setY(y);
      }
    }

    /**
     * Cleans up invalid flags of border tiles.
     */
    public void repair() {
      // cleaning unneeded border flags
      for (final Iterator<Map.Entry<Point, TileMapItem>> iter = tiles.entrySet().iterator(); iter.hasNext();) {
        final Map.Entry<Point, TileMapItem> entry = iter.next();
        final TileMapItem tmi = entry.getValue();
        final Point p = entry.getKey();
        final Point p2 = new Point(); // reused to reduce memory allocations

        if (tmi.isBottomFlag()) {
          p2.x = p.x; p2.y = p.y + 1;
          final TileMapItem tmi2 = tiles.get(p2);
          if (tmi2 == null || !tmi2.isAllFlag()) {
            tmi.setBottomFlag(false);
          }
        }

        if (tmi.isTopFlag()) {
          p2.x = p.x; p2.y = p.y - 1;
          final TileMapItem tmi2 = tiles.get(p2);
          if (tmi2 == null || !tmi2.isAllFlag()) {
            tmi.setTopFlag(false);
          }
        }

        if (tmi.isRightFlag()) {
          p2.x = p.x + 1; p2.y = p.y;
          final TileMapItem tmi2 = tiles.get(p2);
          if (tmi2 == null || !tmi2.isAllFlag()) {
            tmi.setRightFlag(false);
          }
        }

        if (tmi.isLeftFlag()) {
          p2.x = p.x - 1; p2.y = p.y;
          final TileMapItem tmi2 = tiles.get(p2);
          if (tmi2 == null || !tmi2.isAllFlag()) {
            tmi.setLeftFlag(false);
          }
        }

        if (tmi.flags.isEmpty()) {
          iter.remove();
        }
      }
    }

    /**
     * Extracts distinct clusters of tiles into separate {@code TileMap} objects.
     *
     * @return {@link List} of {@link TileMap} objects. List is empty if the current object does not contain any tiles.
     */
    public List<TileMap> split() {
      final List<TileMap> retVal = new ArrayList<>();

      final Set<Point> locations = new HashSet<>();
      for (final Point key : tiles.keySet()) {
        if (!locations.contains(key)) {
          final TileMap tm = findContiguousTiles(null, key, locations);
          // add only TileMap objects that contain full tile definitions
          if (!tm.isEmpty()) {
            boolean isAll = false;
            for (final Point p : tm.getAllTilePositions(false)) {
              final TileMapItem tmi = tm.getTile(p);
              if (tmi != null && tmi.isAllFlag()) {
                isAll = true;
                break;
              }
            }
            if (isAll) {
              tm.repair();
              retVal.add(tm);
            }
          }
        }
      }

      return retVal;
    }

    /**
     * Recursive method that finds all contiguous tiles relative to the given {@code Point} and adds them to the
     * specified {@code TileMap} object.
     *
     * @param tileMap   {@link TileMap} object to store contiguous tiles in. Will be created if {@code null} is
     *                    specified.
     * @param p         Tile location to process.
     * @param locations Global list of already processed tiles to prevent marking a tile multiple times.
     * @return the {@code tileMap} argument.
     * @throws NullPointerException if the {@code locations} argument is null.
     */
    private TileMap findContiguousTiles(TileMap tileMap, Point p, Set<Point> locations) {
      if (locations == null) {
        throw new NullPointerException("Location set is null");
      }

      if (tileMap == null) {
        tileMap = new TileMap(getWedInfo(), tileSize);
      }

      if (locations.contains(p)) {
        return tileMap;
      }

      final TileMapItem item = getTile(p);
      if (item == null)  {
        return tileMap;
      }

      tileMap.setTile(p, item);
      if (item.isAllFlag()) {
        locations.add(p);
      }

      if (item.isAllFlag() || item.isTopFlag()) {
        // top
        final Point p2 = new Point(p.x, p.y - 1);
        findContiguousTiles(tileMap, p2, locations);
      }
      if (item.isAllFlag() || item.isLeftFlag()) {
        // left
        final Point p2 = new Point(p.x - 1, p.y);
        findContiguousTiles(tileMap, p2, locations);
      }
      if (item.isAllFlag() || item.isBottomFlag()) {
        // bottom
        final Point p2 = new Point(p.x, p.y + 1);
        findContiguousTiles(tileMap, p2, locations);
      }
      if (item.isAllFlag() || item.isRightFlag()) {
        // right
        final Point p2 = new Point(p.x + 1, p.y);
        findContiguousTiles(tileMap, p2, locations);
      }

      return tileMap;
    }

    @Override
    public int hashCode() {
      return Objects.hash(tiles);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      TileMap other = (TileMap)obj;
      return Objects.equals(tiles, other.tiles);
    }

    @Override
    public String toString() {
      return "TileMap [tiles=" + tiles + "]";
    }
  }
}
