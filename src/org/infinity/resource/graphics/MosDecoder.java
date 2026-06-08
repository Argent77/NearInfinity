// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.Image;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;
import org.infinity.util.io.StreamUtils;

/**
 * Common base class for handling MOS resources.
 */
public abstract class MosDecoder {
  /** Recognized MOS resource types */
  public enum Type {
    INVALID, MOSC, MOSV1, MOSV2
  }

  private final ResourceEntry mosEntry;

  private Type type;

  /**
   * Returns whether the specified resource entry points to a valid MOS resource.
   */
  public static boolean isValid(ResourceEntry mosEntry) {
    return (getType(mosEntry) != Type.INVALID);
  }

  /**
   * Returns the type of the specified resource entry.
   *
   * @return One of the MOS {@code Type}s.
   */
  public static Type getType(ResourceEntry mosEntry) {
    Type retVal = Type.INVALID;
    if (mosEntry != null) {
      final boolean ignoreOverride = mosEntry instanceof BIFFResourceEntry;
      try (final InputStream is = mosEntry.getResourceDataAsStream(ignoreOverride)) {
        String signature = StreamUtils.readString(is, 4);
        String version = StreamUtils.readString(is, 4);
        if ("MOSC".equals(signature)) {
          retVal = Type.MOSC;
        } else if ("MOS ".equals(signature)) {
          if ("V1  ".equals(version)) {
            retVal = Type.MOSV1;
          } else if ("V2  ".equals(version)) {
            retVal = Type.MOSV2;
          }
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return retVal;
  }

  /**
   * Returns information about the specified MOS resource.
   *
   * @param mosEntry The MOS resource entry.
   * @return A {@link MosInfo} structure with information about the specified MOS resource,
   *         {@code null} if information is not available.
   */
  public static MosInfo getInfo(ResourceEntry mosEntry) {
    MosInfo retVal = null;

    switch (getType(mosEntry)) {
      case MOSC:
      case MOSV1:
        retVal = MosV1Decoder.getInfo(mosEntry);
        break;
      case MOSV2:
        retVal = MosV2Decoder.getInfo(mosEntry);
        break;
      default:
        break;
    }

    return retVal;
  }

  /**
   * Returns a new MosDecoder object based on the specified MOS resource entry.
   *
   * @param mosEntry The MOS resource entry.
   * @return Either {@code MosV1Decoder} or {@code MosV2Decoder}, depending on the BAM resource type. Returns
   *         {@code null} if the resource doesn't contain valid BAM data.
   */
  public static MosDecoder loadMos(ResourceEntry mosEntry) {
    Type type = getType(mosEntry);
    switch (type) {
      case MOSC:
      case MOSV1:
        return new MosV1Decoder(mosEntry);
      case MOSV2:
        return new MosV2Decoder(mosEntry);
      default:
        return null;
    }
  }

  /**
   * Returns the ResourceEntry object of the MOS resource.
   */
  public ResourceEntry getResourceEntry() {
    return mosEntry;
  }

  /**
   * Returns the type of the TIS resource.
   */
  public Type getType() {
    return type;
  }

  /** Removes all data from the decoder. Use this to free up memory. */
  public abstract void close();

  /** Clears existing data and reloads the current MOS resource entry. */
  public abstract void reload();

  /** Returns the raw data of the MOS resource. */
  public abstract ByteBuffer getResourceBuffer();

  /** Returns the width of the MOS resource. */
  public abstract int getWidth();

  /** Returns the height of the MOS resource. */
  public abstract int getHeight();

  /** Returns the MOS as image object. */
  public abstract Image getImage();

  /** Paints the MOS onto the canvas. Returns the success state. */
  public abstract boolean getImage(Image canvas);

  /** Returns an int array containing the decoded MOS data. (Format: ARGB) */
  public abstract int[] getImageData();

  /** Writes the decoded MOS data into the buffer. Returns the success state. */
  public abstract boolean getImageData(int[] buffer);

  /** Returns the number of data blocks this MOS resource consists of. */
  public abstract int getBlockCount();

  /** Returns the width of the specified MOS data block */
  public abstract int getBlockWidth(int blockIdx);

  /** Returns the height of the specified MOS data block */
  public abstract int getBlockHeight(int blockIdx);

  /** Returns the specified block as image object. */
  public abstract Image getBlock(int blockIdx);

  /** Paints the specified block onto the canvas. Returns the success state. */
  public abstract boolean getBlock(int blockIdx, Image canvas);

  /** Returns the specified blocks as int array. (Format: ARGB) */
  public abstract int[] getBlockData(int blockIdx);

  /** Writes the specified blocks into the buffer. Returns the success state. */
  public abstract boolean getBlockData(int blockIdx, int[] buffer);

  /** Does basic initializations */
  protected MosDecoder(ResourceEntry mosEntry) {
    this.mosEntry = mosEntry;
    this.type = Type.INVALID;
  }

  protected void setType(Type type) {
    this.type = type;
  }

  // -------------------------- INNER CLASSES --------------------------

  /** A class for providing parsed MOS header information. */
  public static class MosInfo {
    /** Type of the MOS resource. */
    public final Type type;
    /** MOS width, in pixels. */
    public final int width;
    /** MOS height, in pixels. */
    public final int height;
    /** Number of MOS V1 data block columns. */
    public final int columns;
    /** Number of MOS V1 data block rows. */
    public final int rows;
    /** Dimension of a MOS V1 data block, in pixels. */
    public final int blockSize;
    /** Number of MOS V2 data blocks. */
    public final int numBlocks;

    public MosInfo(boolean compressed, int width, int height, int columns, int rows, int blockSize) {
      this.type = compressed ? Type.MOSC : Type.MOSV1;
      this.width = width;
      this.height = height;
      this.columns = columns;
      this.rows = rows;
      this.blockSize= blockSize;
      this.numBlocks = 0;
    }

    public MosInfo(int width, int height, int numBlocks) {
      this.type = Type.MOSV2;
      this.width = width;
      this.height = height;
      this.numBlocks = numBlocks;
      this.columns = this.rows = this.blockSize = 0;
    }
  }
}
