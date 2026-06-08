// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.Image;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;
import org.infinity.util.io.StreamUtils;

/**
 * Common base class for handling TIS resources.
 */
public abstract class TisDecoder {
  /** Recognized TIS resource types */
  public enum Type {
    INVALID, PALETTE, PVRZ
  }

  protected static final int TILE_DIMENSION = 64; // default width and height of a tile

  private final ResourceEntry tisEntry;

  private Type type;

  /**
   * Returns whether the specified resource entry points to a valid TIS resource.
   */
  public static boolean isValid(ResourceEntry tisEntry) {
    return (getType(tisEntry) != Type.INVALID);
  }

  /**
   * Returns the type of the specified resource entry.
   *
   * @return One of the TIS {@code Type}s.
   */
  public static Type getType(ResourceEntry tisEntry) {
    Type retVal = Type.INVALID;
    if (tisEntry != null) {
      try {
        final boolean ignoreOverride = tisEntry instanceof BIFFResourceEntry;
        int[] info = tisEntry.getResourceInfo(ignoreOverride);
        if (info != null && info.length > 1) {
          if (info[0] > 0 && info[1] > 0) {
            int sizeV1 = 1024 + TILE_DIMENSION * TILE_DIMENSION;
            if (sizeV1 == info[1]) {
              retVal = Type.PALETTE;
            } else if (info[1] == 12) {
              retVal = Type.PVRZ;
            }
          }
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return retVal;
  }

  /**
   * Returns information about the specified TIS resource.
   *
   * @param tisEntry The TIS resource entry.
   * @return A {@link TisInfo} structure with information about the specified TIS resource,
   *         {@code null} if information is not available.
   */
  public static TisInfo getInfo(ResourceEntry tisEntry) {
    TisInfo retVal = null;

    if (tisEntry != null) {
      final boolean ignoreOverride = tisEntry instanceof BIFFResourceEntry;
      try (InputStream is = tisEntry.getResourceDataAsStream(ignoreOverride)) {
        String signature = StreamUtils.readString(is, 4);
        String version = StreamUtils.readString(is, 4);

        if ("TIS ".equals(signature) && "V1  ".equals(version)) {
          int numTiles = StreamUtils.readInt(is);
          int tileSize = StreamUtils.readInt(is);
          is.skip(4); // tile data offset
          int tileDim = StreamUtils.readInt(is);
          retVal = new TisInfo(numTiles, tileSize, tileDim);
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }

    return retVal;
  }

  /**
   * Returns a new TisDecoder object based on the type of the specified resource entry.
   *
   * @param tisEntry The TIS resource entry.
   * @return Either {@code TisV1Decoder} or {@code TisV2Decoder} depending on the TIS resource type.
   */
  public static TisDecoder loadTis(ResourceEntry tisEntry) {
    Type type = getType(tisEntry);
    switch (type) {
      case PALETTE:
        return new TisV1Decoder(tisEntry);
      case PVRZ:
        return new TisV2Decoder(tisEntry);
      default:
        return null;
    }
  }

  /**
   * Returns the ResourceEntry object of the TIS resource.
   */
  public ResourceEntry getResourceEntry() {
    return tisEntry;
  }

  /**
   * Returns the type of the TIS resource.
   */
  public Type getType() {
    return type;
  }

  /** Removes all data from the decoder. Use this to free up memory. */
  public abstract void close();

  /** Clears existing data and reloads the current TIS resource entry. */
  public abstract void reload();

  /** Returns the raw data of the TIS resource. */
  public abstract ByteBuffer getResourceBuffer();

  /** Returns the width of a single tile (in pixels). */
  public abstract int getTileWidth();

  /** Returns the height of a single tile (in pixels). */
  public abstract int getTileHeight();

  /** Returns the total number of tiles defined by the TIS resource. */
  public abstract int getTileCount();

  /** Returns the specified tile as image object. */
  public abstract Image getTile(int tileIdx);

  /** Paints the specified tile onto the canvas. Returns the success state. */
  public abstract boolean getTile(int tileIdx, Image canvas);

  /** Returns the tile data as int array. (Format: ARGB) */
  public abstract int[] getTileData(int tileIdx);

  /** Writes the specified tile into the buffer. Returns the success state. */
  public abstract boolean getTileData(int tileIdx, int[] buffer);

  /** Does basic initializations */
  protected TisDecoder(ResourceEntry tisEntry) {
    this.tisEntry = tisEntry;
    this.type = Type.INVALID;
  }

  // Set the current TIS type
  protected void setType(Type type) {
    this.type = type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(tisEntry, type);
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
    TisDecoder other = (TisDecoder)obj;
    return Objects.equals(tisEntry, other.tisEntry) && type == other.type;
  }

  @Override
  public String toString() {
    return "TisDecoder [type=" + type + ", tisEntry=" + tisEntry + "]";
  }


  // -------------------------- INNER CLASSES --------------------------

  /** A class for providing parsed TIS header information. */
  public static class TisInfo {
    /** Type of the TIS resource. */
    public final Type type;
    /** Number of tiles stored in the TIS file. */
    public final int numTiles;
    /** Dimension of a TIS tile, in pixels (always 64). */
    public final int tileDimension;

    public TisInfo(int numTiles, int tileSize, int tileDim) {
      this.type = tileSize == 0x0c ? Type.PVRZ : Type.PALETTE;
      this.numTiles = numTiles;
      this.tileDimension = tileDim;
    }

    /** Returns the tile size, in bytes, for the current TIS file. */
    public int getTileSize() {
      return type == Type.PVRZ ? 0x0c : 0x1400;
    }
  }
}
