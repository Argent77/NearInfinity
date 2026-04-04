// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.InflaterInputStream;

import org.infinity.NearInfinity;
import org.infinity.gui.WindowBlocker;
import org.infinity.util.Logger;
import org.infinity.util.io.ByteBufferInputStream;
import org.infinity.util.io.StreamUtils;

/**
 * Provides read operations for file-compressed BIF V1.0 archives.
 */
public class BIFReader extends AbstractBIFFReader {
  private final WindowBlocker blocker;

  private MappedByteBuffer mappedBuffer;
  private int uncSize;
  private int compSize;
  private int compOffset;
  private int numFiles;
  private int numTilesets;

  protected BIFReader(Path file) throws Exception {
    super(file);
    this.blocker = new WindowBlocker(NearInfinity.getInstance());
    open();
  }

  @Override
  public synchronized void open() throws Exception {
    try (FileChannel channel = FileChannel.open(getFile(), StandardOpenOption.READ)) {
      String sigver = StreamUtils.readString(channel, 8);
      if (!"BIF V1.0".equals(sigver)) {
        throw new Exception("Invalid BIFF header");
      }

      int nameLength = StreamUtils.readInt(channel);
      channel.position(channel.position() + nameLength);

      this.uncSize = StreamUtils.readInt(channel);
      this.compSize = StreamUtils.readInt(channel);
      if (this.uncSize < 0 || this.compSize < 0) {
        throw new Exception("Invalid BIFF archive");
      }
      this.compOffset = (int) channel.position();

      mappedBuffer = channel.map(MapMode.READ_ONLY, compOffset, compSize);
      mappedBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }
    init();
  }

  @Override
  public Type getType() {
    return Type.BIF;
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

    ByteBuffer buffer;
    if (entry.isTile) {
      ByteBuffer header = getTisHeader(entry.count, entry.size);
      buffer = StreamUtils.getByteBuffer(entry.count * entry.size + header.limit());
      StreamUtils.copyBytes(header, buffer, header.limit());
    } else {
      buffer = StreamUtils.getByteBuffer(entry.size);
    }

    if (buffer.limit() > 1000000) {
      blocker.setBlocked(true);
    }

    try (InflaterInputStream iis = getInflaterInputStream()) {
      int remaining = entry.offset;
      while (remaining > 0) {
        long n = iis.skip(entry.offset);
        remaining -= (int) n;
      }
      StreamUtils.readBytes(iis, buffer);
    } finally {
      blocker.setBlocked(false);
    }

    buffer.position(0);
    return buffer;
  }

  @Override
  public InputStream getResourceAsStream(int locator) throws IOException {
    return new ByteBufferInputStream(getResourceBuffer(locator));
  }

  private void init() throws Exception {
    try (InflaterInputStream iis = new InflaterInputStream(new ByteBufferInputStream(mappedBuffer.duplicate()))) {
      int curOfs = 0;
      String sigver = StreamUtils.readString(iis, 8);
      if (!"BIFFV1  ".equals(sigver)) {
        throw new Exception("Invalid decompressed BIFF signature");
      }
      this.numFiles = StreamUtils.readInt(iis);
      this.numTilesets = StreamUtils.readInt(iis);
      int entryOfs = StreamUtils.readInt(iis);
      curOfs += 20;
      if (entryOfs < curOfs) {
        throw new Exception("Invalid decompressed BIFF header");
      }
      int remaining = entryOfs - curOfs;
      while (remaining > 0) {
        long n = iis.skip(entryOfs - curOfs);
        remaining -= (int) n;
      }

      // reading file entries
      for (int i = 0; i < numFiles; i++) {
        int locator = StreamUtils.readInt(iis) & 0xfffff;
        int offset = StreamUtils.readInt(iis);
        int size = StreamUtils.readInt(iis);
        short type = StreamUtils.readShort(iis);
        iis.skip(2); // unknown data
        addEntry(new Entry(locator, offset, size, type));
      }

      // reading tileset entries
      int adjust = 0;
      for (int i = 0; i < numTilesets; i++) {
        int locator = StreamUtils.readInt(iis) & 0xfffff;
        if (i == 0) {
          // workaround for tileset entries with incorrect locator indices
          adjust = getTilesetIndexAdjust(locator);
          if (adjust != 0) {
            Logger.debug("{}: Tileset base index adjusted by {}", getFile().getFileName(), adjust >> 14);
          }
        }
        locator += adjust;
        int offset = StreamUtils.readInt(iis);
        int count = StreamUtils.readInt(iis);
        int size = StreamUtils.readInt(iis);
        short type = StreamUtils.readShort(iis);
        iis.skip(2); // unknown data
        addEntry(new Entry(locator, offset, count, size, type));
      }
    }
  }

  // Returns an inflater input stream
  private InflaterInputStream getInflaterInputStream() {
    return new InflaterInputStream(new ByteBufferInputStream(mappedBuffer.duplicate()));
  }
}
