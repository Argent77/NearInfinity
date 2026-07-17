// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;

/**
 * Handles legacy TIS resources (using palettized tiles).
 */
public class TisV1Decoder extends TisDecoder {
  private static final int HEADER_SIZE = 24; // Size of the TIS header

  private ByteBuffer tisBuffer;
  private int tileCount;
  private int tileSize;
  private int tileDimension;
  private int[] workingPalette;
  private BufferedImage workingCanvas;

  public TisV1Decoder(ResourceEntry tisEntry) {
    super(tisEntry);
    init();
  }

  /**
   * Returns the palette of the specified tile.
   *
   * @param tileIdx The tile index
   * @param raw     Specifies whether palette entries should be returned unmodified. Otherwise, alpha components are
   *                  added to all palette entries, and "transparent" palette entry is changed to "black".
   * @return The palette as int array of 256 entries (Format: ARGB). Returns {@code null} on error.
   */
  public int[] getTilePalette(int tileIdx, boolean raw) {
    if (tileIdx >= 0 && tileIdx < getTileCount()) {
      int[] palette = new int[256];
      getTilePalette(tileIdx, palette, raw);
      return palette;
    } else {
      return null;
    }
  }

  /**
   * Writes the palette entries of the specified tile into the buffer.
   *
   * @param tileIdx The tile index
   * @param buffer  The buffer to write the palette data into.
   * @param raw     Specifies whether palette entries should be returned unmodified. Otherwise, alpha components are
   *                  added to all palette entries, and "transparent" palette entry is changed to "black".
   */
  public void getTilePalette(int tileIdx, int[] buffer, boolean raw) {
    if (buffer != null) {
      int ofs = getTileOffset(tileIdx);
      if (ofs > 0) {
        int maxLen = Math.min(buffer.length, 256);
        for (int i = 0; i < maxLen; i++) {
          buffer[i] = tisBuffer.getInt(ofs);
          if (!raw) {
            if (i == 0 && (buffer[i] & 0x00ffffff) == 0x0000ff00) {
              buffer[i] &= 0xff000000;
            } else {
              buffer[i] |= 0xff000000;
            }
          }
          ofs += 4;
        }
      }
    }
  }

  /**
   * Returns the unprocessed tile data (without palette).
   *
   * @param tileIdx The tile index
   * @return Unprocessed data of the specified tile without the palette block. Returns {@code null} on error.
   */
  public byte[] getRawTileData(int tileIdx) {
    if (tileIdx >= 0 && tileIdx < getTileCount()) {
      byte[] buffer = new byte[tileDimension * tileDimension];
      getRawTileData(tileIdx, buffer);
      return buffer;
    } else {
      return null;
    }
  }

  /**
   * Writes unprocessed tile data into the specified buffer (without palette data).
   *
   * @param tileIdx The tile index
   * @param buffer  The buffer to write the tile data into.
   */
  public void getRawTileData(int tileIdx, byte[] buffer) {
    if (buffer != null) {
      int ofs = getTileOffset(tileIdx);
      if (ofs > 0) {
        int maxSize = Math.min(buffer.length, tileDimension * tileDimension);
        ofs += 4 * 256; // skipping palette data
        tisBuffer.position(ofs);
        tisBuffer.get(buffer, 0, maxSize);
      }
    }
  }

  @Override
  public void close() {
    tisBuffer = null;
    tileCount = 0;
    tileSize = 0;
    tileDimension = 0;
    workingPalette = null;
    if (workingCanvas != null) {
      workingCanvas.flush();
      workingCanvas = null;
    }
  }

  @Override
  public void reload() {
    init();
  }

  @Override
  public ByteBuffer getResourceBuffer() {
    return tisBuffer;
  }

  @Override
  public int getTileWidth() {
    return tileDimension;
  }

  @Override
  public int getTileHeight() {
    return tileDimension;
  }

  @Override
  public int getTileCount() {
    return tileCount;
  }

  @Override
  public Image getTile(int tileIdx) {
    BufferedImage image = ColorConvert.createCompatibleImage(tileDimension, tileDimension, true);
    renderTile(tileIdx, image);
    return image;
  }

  @Override
  public boolean getTile(int tileIdx, Image canvas) {
    return renderTile(tileIdx, canvas);
  }

  @Override
  public int[] getTileData(int tileIdx) {
    int[] buffer = new int[tileDimension * tileDimension];
    renderTile(tileIdx, buffer);
    return buffer;
  }

  @Override
  public boolean getTileData(int tileIdx, int[] buffer) {
    return renderTile(tileIdx, buffer);
  }

  private void init() {
    close();

    if (getResourceEntry() != null) {
      try {
        final TisInfo info = TisDecoder.getInfo(getResourceEntry());
        if (info == null || info.type != Type.PALETTE) {
          throw new Exception("Error reading TIS header");
        }

        tileCount = info.numTiles;
        tileSize = info.tileSize;
        tileDimension = info.tileDimension;
        tisBuffer = getResourceEntry().getResourceBuffer();

        setType(Type.PALETTE);

        workingPalette = new int[256];
        workingCanvas = new BufferedImage(tileDimension, tileDimension, Transparency.BITMASK);
      } catch (Exception e) {
        Logger.error(e);
        close();
      }
    }
  }

  // Returns the start offset of the specified tile. Returns -1 on error.
  private int getTileOffset(int tileIdx) {
    if (tileIdx >= 0 && tileIdx < getTileCount()) {
      return HEADER_SIZE + tileIdx * tileSize;
    } else {
      return -1;
    }
  }

  // Paints the specified tile onto the canvas
  private boolean renderTile(int tileIdx, Image canvas) {
    if (canvas != null && canvas.getWidth(null) >= tileDimension && canvas.getHeight(null) >= tileDimension) {
      int[] buffer = ((DataBufferInt) workingCanvas.getRaster().getDataBuffer()).getData();
      if (renderTile(tileIdx, buffer)) {
        buffer = null;
        Graphics2D g = (Graphics2D) canvas.getGraphics();
        try {
          g.setComposite(AlphaComposite.Src);
          g.setColor(ColorConvert.TRANSPARENT_COLOR);
          g.fillRect(0, 0, tileDimension, tileDimension);
          g.drawImage(workingCanvas, 0, 0, null);
        } finally {
          g.dispose();
          g = null;
        }
        return true;
      }
      buffer = null;
    }
    return false;
  }

  // Writes the specified tile data into the buffer
  private boolean renderTile(int tileIdx, int[] buffer) {
    int size = tileDimension * tileDimension;
    if (buffer != null && buffer.length >= size) {
      int ofs = getTileOffset(tileIdx);
      if (ofs > 0) {
        ofs += 1024; // skipping palette data
        getTilePalette(tileIdx, workingPalette, false);
        for (int i = 0; i < size; i++, ofs++) {
          buffer[i] = workingPalette[tisBuffer.get(ofs) & 0xff];
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(tileCount, tileSize, tileDimension, tisBuffer);
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
    TisV1Decoder other = (TisV1Decoder)obj;
    return tileCount == other.tileCount && tileSize == other.tileSize && tileDimension == other.tileDimension
        && Objects.equals(tisBuffer, other.tisBuffer);
  }

  @Override
  public String toString() {
    return "TisV1Decoder [type=" + getType() + ", tisEntry=" + getResourceEntry() + ", tileCount=" + tileCount
        + ", tileSize=" + tileSize + ", tileDimension=" + tileDimension + "]";
  }
}
