// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.Inflater;

import org.infinity.NearInfinity;
import org.infinity.gui.WindowBlocker;
import org.infinity.util.io.ByteBufferInputStream;
import org.infinity.util.io.StreamUtils;

/**
 * Provides read operations for block-compressed BIFC V1.0 archives.
 */
public class BIFCReader extends AbstractBIFFReader {
  private final WindowBlocker blocker;

  private int uncSize;
  private int numFiles;
  private int numTilesets;

  protected BIFCReader(Path file) throws Exception {
    super(file);
    this.blocker = new WindowBlocker(NearInfinity.getInstance());
    open();
  }

  @Override
  public synchronized void open() throws Exception {
    try (FileChannel channel = FileChannel.open(getFile(), StandardOpenOption.READ)) {
      String sigver = StreamUtils.readString(channel, 8);
      if (!"BIFCV1.0".equals(sigver)) {
        throw new Exception("Invalid BIFF header");
      }

      this.uncSize = StreamUtils.readInt(channel);
      if (this.uncSize < 0) {
        throw new Exception("Invalid BIFF archive");
      }
    }
    init();
  }

  @Override
  public Type getType() {
    return Type.BIFC;
  }

  @Override
  public int getFileCount() {
    return numFiles;
  }

  @Override
  public int getTilesetCount() {
    return numTilesets;
  }

  @Override
  public int getBIFFSize() {
    return uncSize;
  }

  @Override
  public ByteBuffer getResourceBuffer(int locator) throws IOException {
    Entry entry = getEntry(locator);
    if (entry == null) {
      throw new IOException("Resource not found");
    }

    int size;
    ByteBuffer buffer;
    if (entry.isTile) {
      ByteBuffer header = getTisHeader(entry.count, entry.size);
      buffer = StreamUtils.getByteBuffer(entry.count * entry.size + header.limit());
      StreamUtils.copyBytes(header, buffer, header.limit());
      size = entry.count * entry.size;
    } else {
      buffer = StreamUtils.getByteBuffer(entry.size);
      size = entry.size;
    }

    if (buffer.limit() > 1000000) {
      blocker.setBlocked(true);
    }

    try (InputStream is = new BifcInputStream(
        new BufferedInputStream(Files.newInputStream(getFile(), StandardOpenOption.READ)), entry.offset, size)) {
      StreamUtils.readBytes(is, buffer);
    } finally {
      blocker.setBlocked(false);
    }

    buffer.position(0);
    return buffer;
  }

  @Override
  public InputStream getResourceAsStream(int locator) throws IOException {
    Entry entry = getEntry(locator);
    if (entry == null) {
      throw new IOException("Resource not found");
    }

    if (entry.isTile) {
      ByteBuffer header = getTisHeader(entry.count, entry.size);
      InputStream is1 = new ByteBufferInputStream(header);
      @SuppressWarnings("resource")
      InputStream is2 = new BifcInputStream(
          new BufferedInputStream(Files.newInputStream(getFile(), StandardOpenOption.READ)), entry.offset,
          entry.count * entry.size);
      return new SequenceInputStream(is1, is2);
    } else {
      return new BifcInputStream(new BufferedInputStream(Files.newInputStream(getFile(), StandardOpenOption.READ)),
          entry.offset, entry.size);
    }
  }

  private void init() throws Exception {
    try (InputStream is = new BifcInputStream(
        new BufferedInputStream(Files.newInputStream(getFile(), StandardOpenOption.READ)), 0, -1)) {
      int curOfs = 0;
      String sigver = StreamUtils.readString(is, 8);
      if (!"BIFFV1  ".equals(sigver)) {
        throw new Exception("Invalid decompressed BIFF signature");
      }
      this.numFiles = StreamUtils.readInt(is);
      this.numTilesets = StreamUtils.readInt(is);
      int entryOfs = StreamUtils.readInt(is);
      curOfs += 20;
      if (entryOfs < curOfs) {
        throw new Exception("Invalid decompressed BIFF header");
      }
      int remaining = entryOfs - curOfs;
      while (remaining > 0) {
        long n = is.skip(remaining);
        remaining -= (int) n;
      }

      // reading file entries
      for (int i = 0; i < numFiles; i++) {
        int locator = i;  // biff locator is ignored by the engine
        is.skip(4); // skipping locator field
        int offset = StreamUtils.readInt(is);
        int size = StreamUtils.readInt(is);
        short type = StreamUtils.readShort(is);
        is.skip(2); // unknown data
        addEntry(new Entry(locator, offset, size, type));
      }

      // reading tileset entries
      for (int i = 0; i < numTilesets; i++) {
        int locator = (i + 1) << 14;  // biff locator is ignored by the engine
        is.skip(4); // skipping locator field
        int offset = StreamUtils.readInt(is);
        int count = StreamUtils.readInt(is);
        int size = StreamUtils.readInt(is);
        short type = StreamUtils.readShort(is);
        is.skip(2); // unknown data
        addEntry(new Entry(locator, offset, count, size, type));
      }
    }
  }

  // -------------------------- INNER CLASSES --------------------------

  private static class BifcInputStream extends InputStream {
    private final Inflater inflater;

    private InputStream input; // BIFC archive as input stream
    private int endOffset; // the end-of-stream offset for this InputStream in decompressed data
    private int position; // current absolute position in decompressed data
    private byte[] inBuffer; // buffer for compressed data of current block
    private byte[] outBuffer; // buffer for decompressed data of current block
    private int bufOfs; // contains relative offset in current outBuffer
    private int bufLen; // contains actual number of bytes of data in current outBuffer

    /**
     * Constructs an InputStream over a specific section of a BIFC archive.
     *
     * @param is     The BIFC archive as input stream.
     * @param offset Start offset in decompressed BIFF data.
     * @param size   Size of decompressed BIFF data to map. Specify -1 to map until the end of decompressed data.
     */
    public BifcInputStream(InputStream is, int offset, int size) throws IOException {
      if (is == null) {
        throw new NullPointerException();
      }
      this.input = is;
      String sigver = StreamUtils.readString(this.input, 8);
      if (!"BIFCV1.0".equals(sigver)) {
        throw new IOException("Unsupported source BIFF signature");
      }
      int uncSize = StreamUtils.readInt(this.input);
      if (offset < 0 || offset > uncSize) {
        throw new IOException("Start offset is out of bounds");
      }
      if (size < 0) {
        size = uncSize - offset;
      }
      if (size < 0 || offset + size > uncSize) {
        throw new IOException("Size is out of bounds");
      }
      this.endOffset = offset + size;
      this.position = 0;
      this.inflater = new Inflater();
      this.bufOfs = 0;
      this.bufLen = 0;
      skip(offset);
    }

    @Override
    public int read() throws IOException {
      if (available() > 0) {
        final byte[] b = { 0 };
        if (getData(b, 0, 1) == 1) {
          return b[0] & 0xff;
        }
      }
      return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (available() > 0) {
        return getData(b, off, len);
      }
      return -1;
    }

    @Override
    public long skip(long n) throws IOException {
      return Math.max(0, skipData((int) n));
    }

    @Override
    public int available() throws IOException {
      return endOffset - position;
    }

    @Override
    public void close() throws IOException {
      if (isOpen()) {
        try {
          input.close();
        } finally {
          synchronized (this) {
            input = null;
          }
        }
      }
    }

    private boolean isOpen() {
      return (input != null);
    }

    // Writes decompressed data into "buf". Returns actual number of decompressed bytes.
    // Updates internal data position. Returns -1 on error or end-of-stream.
    private synchronized int getData(byte[] buf, int ofs, int len) throws IOException {
      ofs = Math.max(0, ofs);
      len = Math.max(0, Math.min(len, endOffset - position));
      if (!isOpen() || position >= endOffset || buf == null || ofs > buf.length || ofs + len > buf.length) {
        return -1;
      }

      int retVal = 0;
      while (len > 0) {
        try {
          updateBuffer(false, -1);
        } catch (Exception e) {
          throw new IOException(e.getMessage());
        }

        // copy data into output buffer
        int n = Math.min(bufLen - bufOfs, len);
        System.arraycopy(outBuffer, bufOfs, buf, ofs, n);
        retVal += n;
        bufOfs += n;
        ofs += n;
        len -= n;
      }
      position += retVal;
      return retVal;
    }

    // Skips the specified number of decompressed bytes. Returns -1 on error or end-of-stream.
    private synchronized int skipData(int len) throws IOException {
      len = Math.max(0, Math.min(len, endOffset - position));
      if (!isOpen() || position >= endOffset) {
        return -1;
      }

      int retVal = 0;
      while (len > 0) {
        try {
          updateBuffer(true, len);
        } catch (Exception e) {
          throw new IOException(e);
        }

        int n = Math.min(len, bufLen - bufOfs);
        retVal += n;
        bufOfs += n;
        len -= n;
      }
      position += retVal;
      return retVal;
    }

    // Decompress next block of data.
    // if skipOnly == true, then the next compressed block is skipped if
    // (condition < 0) || (condition >= uncompressed block size)
    private boolean updateBuffer(boolean skipOnly, int condition) throws Exception {
      if (bufLen == 0 || bufOfs >= bufLen) {
        int uncSize = StreamUtils.readInt(input);
        int compSize = StreamUtils.readInt(input);
        if (skipOnly && (condition < 0 || condition >= uncSize)) {
          int remaining = compSize;
          while (remaining > 0) {
            long n = input.skip(remaining);
            remaining -= (int) n;
          }
        } else {
          if (inBuffer == null || inBuffer.length < compSize) {
            inBuffer = new byte[compSize];
          }
          if (outBuffer == null || outBuffer.length < uncSize) {
            outBuffer = new byte[uncSize];
          }
          input.read(inBuffer, 0, compSize);
          inflater.reset();
          inflater.setInput(inBuffer, 0, compSize);
          if (inflater.inflate(outBuffer, 0, uncSize) != uncSize) {
            throw new Exception("Unexpected end of decompressed data");
          }
        }
        bufLen = uncSize;
        bufOfs = 0;
        return true;
      }
      return false;
    }
  }
}
