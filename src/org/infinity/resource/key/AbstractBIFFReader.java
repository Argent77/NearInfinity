// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.infinity.util.io.StreamUtils;

/**
 * Abstract base class for specifialized BIFF readers.
 */
public abstract class AbstractBIFFReader //implements AutoCloseable
{
  /** Supported BIFF archive types. */
  public enum Type {
    /** Uncompressed BIFF V1 */
    BIFF,
    /** File-compressed BIF V1.0 */
    BIF,
    /** Block-compressed BIFC V1.0 */
    BIFC,
  }

  // A cache for AbstractBIFFReader instances
  private static final LinkedHashMap<Path, AbstractBIFFReader> BIFF_CACHE = new LinkedHashMap<>();

  // Maps resource locators to BIFF entry structures
  private final HashMap<Integer, Entry> mapEntries = new HashMap<>();

  protected final Path file;

  /**
   * Opens the specified BIFF file (of any supported type) and returns it fully initialized and
   * ready for read operations as a BIFFReader object.
   * @param file Path to the BIFF file.
   * @return A BIFFReader object for accessing the BIFF archive.
   * @throws IOException On error.
   */
  public static synchronized AbstractBIFFReader open(Path file) throws Exception
  {
    return queryBIFFReader(file);
  }

  /** Returns a fully initialized TIS header as {@link ByteBuffer} object. */
  public static ByteBuffer getTisHeader(int tileCount, int tileSize)
  {
    ByteBuffer bb = StreamUtils.getByteBuffer(24);
    bb.put("TIS V1  ".getBytes());
    bb.putInt(tileCount);
    bb.putInt(tileSize);
    bb.putInt(0x18);  // data offset
    bb.putInt(0x40);  // tile dimension
    bb.position(0);
    return bb;
  }

  /** Removes all {@code AbstractBIFFReader} entries from the cache. */
  public static void resetCache()
  {
    BIFF_CACHE.clear();
  }

  // Fetches a cached AbstractBIFFReader associated of the specified path or creates a new one
  private static AbstractBIFFReader queryBIFFReader(Path file) throws Exception
  {
    AbstractBIFFReader retVal = null;
    if (file != null) {
      // get and remove an available cached entry
      retVal = BIFF_CACHE.get(file);
      if (retVal == null) {
        Type type = detectBiffType(file);
        switch (type) {
          case BIFF:
            retVal = new BIFFReader(file);
            break;
          case BIF:
            retVal = new BIFReader(file);
            break;
          case BIFC:
            retVal = new BIFCReader(file);
            break;
          default:
            throw new IOException("Unsupported BIFF type");
        }
        BIFF_CACHE.put(file, retVal);
      }
    }
    return retVal;
  }


  /** Returns whether the BIFF file uses any kind of compression. */
  public boolean isCompressed()
  {
    return (getType() == Type.BIF || getType() == Type.BIFC);
  }

  /** Returns the {@link Path} to the BIFF file. */
  public Path getFile()
  {
    return file;
  }

  /**
   * Returns a ResourceInfo array containing size for regular resources
   * and tile count and size for TIS resources.
   * @param locator The unmodified locator of the desired resource as found in the KEY file.
   */
  public int[] getResourceInfo(int locator) throws IOException
  {
    Entry entry = getEntry(locator);
    if (entry != null) {
      int[] retVal = null;
      if (entry.isTile) {
        retVal = new int[]{entry.count, entry.size};
      } else {
        retVal = new int[]{entry.size};
      }
      return retVal;
    } else {
      throw new IOException("Resource not found");
    }
  }

  /** Returns whether the BIFF file is open and ready for read operations. */
//  public abstract boolean isOpen();

  /** Re-opens the BIFF file if it had been {@code close}d before. Does nothing if the BIFF file is open. */
  public abstract void open() throws Exception;

  /** Returns the BIFF resource type. */
  public abstract Type getType();

  /** Returns the number of regular resources in the BIFF file. */
  public abstract int getFileCount();

  /** Returns the number of tileset (TIS) resources in the BIFF file. */
  public abstract int getTilesetCount();

  /** Returns the uncompressed size of the BIFF archive in bytes. Returns -1 on error. */
  public abstract int getBIFFSize();

  /**
   * Returns a {@link ByteBuffer} object of the requested (TIS or regular) resource.
   * @param locator The unmodified locator of the desired resource as found in the KEY file.
   */
  public abstract ByteBuffer getResourceBuffer(int locator) throws IOException;

  /**
   * Returns an {@link InputStream} object of the requested (TIS or regular) resource.
   * @param locator The unmodified locator of the desired resource as found in the KEY file.
   */
  public abstract InputStream getResourceAsStream(int locator) throws IOException;

  protected AbstractBIFFReader(Path file) throws Exception
  {
    if (file == null) {
      throw new NullPointerException();
    }

    this.file = file;
  }

  // Internally used to store BIFF entry information
  protected void addEntry(Entry entry)
  {
    if (entry != null) {
      mapEntries.put(Integer.valueOf(entry.locator & 0xfffff), entry);
    }
  }

  // Internally used to retrieve stored BIFF entry information
  protected Entry getEntry(int locator)
  {
    return mapEntries.get(Integer.valueOf(locator & 0xfffff));
  }

  // Internally used to remove all entries from the map
  protected void resetEntries()
  {
    mapEntries.clear();
  }

  private static Type detectBiffType(Path file) throws Exception
  {
    if (file == null) {
      throw new NullPointerException();
    }

    try (InputStream is = StreamUtils.getInputStream(file)) {
      String sigver = StreamUtils.readString(is, 8);
      if ("BIFFV1  ".equals(sigver)) {
        return Type.BIFF;
      } else if ("BIF V1.0".equals(sigver)) {
        return Type.BIF;
      } else if ("BIFCV1.0".equals(sigver)) {
        return Type.BIFC;
      } else {
        throw new IOException("Unsupported BIFF file: " + file);
      }
    }
  }


//-------------------------- INNER CLASSES --------------------------

  /** File or tileset entry definition. */
  protected static class Entry
  {
    /** Resource locator. */
    public final int locator;
    /** Offset to resource data. */
    public final int offset;
    /** File size or size of each tile. */
    public final int size;
    /** Number of tiles in the resource (if {@code isTile == true}). */
    public final int count;
    /** Resource type. */
    public final short type;
    /** Indicates whether this is a file or tileset. */
    public final boolean isTile;

    public Entry(int locator, int offset, int size, short type)
    {
      this.locator = locator;
      this.offset = offset;
      this.size = size;
      this.count = 0;
      this.type = type;
      this.isTile = (type == Keyfile.TYPE_TIS);
    }

    public Entry(int locator, int offset, int count, int size, short type)
    {
      this.locator = locator;
      this.offset = offset;
      this.size = size;
      this.count = count;
      this.type = type;
      this.isTile = (type == Keyfile.TYPE_TIS);
    }
  }
}
