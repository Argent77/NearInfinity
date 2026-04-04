// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.infinity.NearInfinity;
import org.infinity.gui.WindowBlocker;
import org.infinity.util.Logger;
import org.infinity.util.io.ByteBufferInputStream;
import org.infinity.util.io.StreamUtils;

/**
 * Provides read operations for uncompressed BIFF V1 archives.
 */
public class BIFFReader extends AbstractBIFFReader {
  private final WindowBlocker blocker;

  private int numFiles;
  private int numTilesets;

  protected BIFFReader(Path file) throws Exception {
    super(file);
    this.blocker = new WindowBlocker(NearInfinity.getInstance());
    open();
  }

  @Override
  public synchronized void open() throws Exception {
    try (FileChannel channel = FileChannel.open(getFile(), StandardOpenOption.READ)) {
      String sigver = StreamUtils.readString(channel, 8);
      if (!"BIFFV1  ".equals(sigver)) {
        throw new Exception("Invalid BIFF header");
      }
      this.numFiles = StreamUtils.readInt(channel);
      this.numTilesets = StreamUtils.readInt(channel);
      int ofsFiles = StreamUtils.readInt(channel);
      ByteBuffer bb = StreamUtils.getByteBuffer(this.numFiles * 0x10 + this.numTilesets * 0x14);
      channel.position(ofsFiles);
      channel.read(bb);
      bb.position(0);
      init(bb, numFiles, numTilesets);
    }
  }

  @Override
  public Type getType() {
    return Type.BIFF;
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
    try {
      return (int) Files.size(getFile());
    } catch (IOException e) {
      Logger.trace(e);
    }
    return -1;
  }

  @Override
  public ByteBuffer getResourceBuffer(int locator) throws IOException {
    Entry entry = getEntry(locator);
    if (entry == null) {
      throw new IOException("Resource not found");
    }

    ByteBuffer buffer;
    try (FileChannel channel = FileChannel.open(getFile(), StandardOpenOption.READ)) {
      channel.position(entry.offset);
      if (entry.isTile) {
        ByteBuffer header = getTisHeader(entry.count, entry.size);
        int remaining = entry.count * entry.size + header.limit();
        if (remaining > 1000000) {
          blocker.setBlocked(true);
        }
        try {
          buffer = StreamUtils.getByteBuffer(remaining);
          StreamUtils.copyBytes(header, buffer, header.limit());
          remaining -= header.limit();
          while (channel.read(buffer) > 0) {
          }
        } finally {
          blocker.setBlocked(false);
        }
      } else {
        buffer = StreamUtils.getByteBuffer(entry.size);
        while (channel.read(buffer) > 0) {
        }
      }

      buffer.position(0);
      return buffer;
    }
  }

  @Override
  public InputStream getResourceAsStream(int locator) throws IOException {
    Entry entry = getEntry(locator);
    if (entry == null) {
      throw new IOException("Resource not found");
    }

    try (FileChannel channel = FileChannel.open(getFile(), StandardOpenOption.READ)) {
      int size = entry.isTile ? entry.count * entry.size : entry.size;
      ByteBuffer buffer = channel.map(MapMode.READ_ONLY, entry.offset, size).order(ByteOrder.LITTLE_ENDIAN);
      InputStream is;
      if (entry.isTile) {
        ByteBuffer header = getTisHeader(entry.count, entry.size);
        is = new ByteBufferInputStream(header, buffer);
      } else {
        is = new ByteBufferInputStream(buffer);
      }
      return is;
    }
  }

  private void init(ByteBuffer buffer, int numFiles, int numTilesets) {
    // reading file entries
    for (int i = 0; i < numFiles; i++) {
      int locator = buffer.getInt() & 0xfffff;
      int offset = buffer.getInt();
      int size = buffer.getInt();
      short type = buffer.getShort();
      buffer.getShort(); // unknown data
      addEntry(new Entry(locator, offset, size, type));
    }
    // reading tileset entries
    int adjust = 0;
    for (int i = 0; i < numTilesets; i++) {
      int locator = buffer.getInt() & 0xfffff;
      if (i == 0) {
        // workaround for tileset entries with incorrect locator indices
        adjust = getTilesetIndexAdjust(locator);
        if (adjust != 0) {
          Logger.debug("{}: Tileset base index adjusted by {}", getFile().getFileName(), adjust >> 14);
        }
      }
      locator += adjust;
      int offset = buffer.getInt();
      int count = buffer.getInt();
      int size = buffer.getInt();
      short type = buffer.getShort();
      buffer.getShort(); // unknown data
      addEntry(new Entry(locator, offset, count, size, type));
    }
  }
}
