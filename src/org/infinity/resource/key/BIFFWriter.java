// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.graphics.TisDecoder;
import org.infinity.util.Logger;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

public final class BIFFWriter {
  private static final String TIS_SIGNATURE_VERSION = "TIS V1  ";
  private static final int TIS_TILE_DIMENSION_OFFSET = 0x14;

  private final BIFFEntry bifEntry;
  private final Map<ResourceEntry, Boolean> resources = new HashMap<>();
  private final Map<ResourceEntry, Boolean> tileResources = new HashMap<>();
  private final AbstractBIFFReader.Type format;

  private static byte[] compress(byte[] data) {
    Deflater deflater = new Deflater();
    byte[] compr = new byte[data.length * 2];
    deflater.setInput(data);
    deflater.finish();
    int clength = deflater.deflate(compr);
    return Arrays.copyOfRange(compr, 0, clength);
  }

  private static void compressBIF(Path biff, Path compr, String uncrfilename) throws IOException {
    try (OutputStream os = StreamUtils.getOutputStream(compr, true)) {
      StreamUtils.writeString(os, "BIF ", 4);
      StreamUtils.writeString(os, "V1.0", 4);
      StreamUtils.writeInt(os, uncrfilename.length());
      StreamUtils.writeString(os, uncrfilename, uncrfilename.length());
      StreamUtils.writeInt(os, (int) Files.size(biff)); // Uncompressed length
      StreamUtils.writeInt(os, 0); // Compressed length
      try (OutputStream dos = new DeflaterOutputStream(os)) {
        try (InputStream is = StreamUtils.getInputStream(biff)) {
          byte[] buffer = new byte[32765];
          int bytesread = is.read(buffer, 0, buffer.length);
          while (bytesread != -1) {
            dos.write(buffer, 0, bytesread);
            bytesread = is.read(buffer, 0, buffer.length);
          }
        }
      }
    }
    int comprsize = (int) (Files.size(compr)) - (0x20 + uncrfilename.length());
    try (FileChannel ch = FileChannel.open(compr, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      ch.position(0x10 + uncrfilename.length());
      StreamUtils.writeInt(ch, comprsize);
    }
  }

  private static void compressBIFC(Path biff, Path compr) throws Exception {
    try (OutputStream os = StreamUtils.getOutputStream(compr, true)) {
      StreamUtils.writeString(os, "BIFC", 4);
      StreamUtils.writeString(os, "V1.0", 4);
      StreamUtils.writeInt(os, (int) Files.size(biff));
      try (InputStream is = StreamUtils.getInputStream(biff)) {
        byte[] block = readBytes(is, 8192);
        while (block.length != 0) {
          byte[] compressed = compress(block);
          StreamUtils.writeInt(os, block.length);
          StreamUtils.writeInt(os, compressed.length);
          StreamUtils.writeBytes(os, compressed);
          block = readBytes(is, 8192);
        }
      }
    }
  }

  private static byte[] readBytes(InputStream is, int length) throws Exception {
    byte[] buffer = new byte[length];
    int bytesread = 0;
    while (bytesread < length) {
      int newread = is.read(buffer, bytesread, length - bytesread);
      if (newread == -1) {
        break;
      }
      bytesread += newread;
    }
    return Arrays.copyOfRange(buffer, 0, bytesread);
  }

  public BIFFWriter(BIFFEntry bifEntry, AbstractBIFFReader.Type format) {
    this.bifEntry = bifEntry;
    this.format = format;
    if (bifEntry.getIndex() == -1) { // new biff-file
      ResourceFactory.getKeyfile().addBIFFEntry(bifEntry);
    }
  }

  public void addResource(ResourceEntry resourceEntry, boolean ignoreoverride) {
    if (resourceEntry.getExtension().equalsIgnoreCase("TIS")) {
      validateResource(resourceEntry, ignoreoverride);
      tileResources.put(resourceEntry, ignoreoverride);
    } else {
      resources.put(resourceEntry, ignoreoverride);
    }
  }

  public static void validateResource(ResourceEntry resourceEntry, boolean ignoreOverride) {
    if (!resourceEntry.getExtension().equalsIgnoreCase("TIS")) {
      return;
    }
    try (InputStream is = resourceEntry.getResourceDataAsStream(ignoreOverride)) {
      final int bytesToSkip = TIS_TILE_DIMENSION_OFFSET - TIS_SIGNATURE_VERSION.length();
      if (TIS_SIGNATURE_VERSION.equals(StreamUtils.readString(is, TIS_SIGNATURE_VERSION.length()))
          && is.skip(bytesToSkip) == bytesToSkip) {
        final int tileDimension = StreamUtils.readInt(is);
        if (tileDimension != TisDecoder.DEFAULT_TILE_DIMENSION) {
          throw new IllegalArgumentException("Cannot add " + resourceEntry.getResourceName()
              + " to a BIFF archive: tile dimension " + tileDimension
              + " is not supported. BIFF archives require " + TisDecoder.DEFAULT_TILE_DIMENSION + "x"
              + TisDecoder.DEFAULT_TILE_DIMENSION + " pixel TIS tiles.");
        }
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not validate " + resourceEntry.getResourceName(), e);
    }
  }

  public void write() throws Exception {
    Path biffPath = FileManager.query(Profile.getGameRoot(), "data");
    if (biffPath == null || !FileEx.create(biffPath).isDirectory()) {
      throw new Exception("No BIFF folder found.");
    }
    Path dummyFile = Files.createTempFile(biffPath, "_dummy", ".bif");
    Path compressedFile = null;
    try {
      writeBIFF(dummyFile);
      ResourceFactory.getKeyfile().closeBIFFFiles();
      bifEntry.setFileSize((int) Files.size(dummyFile)); // Uncompressed length
      if (format == AbstractBIFFReader.Type.BIFF) {
        // Delete old BIFF, rename this to real name
        Path realFile = bifEntry.getPath();
        if (realFile == null) {
          realFile = FileManager.query(Profile.getGameRoot(), bifEntry.getFileName());
        }
        if (FileEx.create(realFile).isFile()) {
          Files.delete(realFile);
        }
        Files.move(dummyFile, realFile);
      } else if (format == AbstractBIFFReader.Type.BIF) {
        compressedFile = Files.createTempFile(biffPath, "_dummy", ".cbf");
        compressBIF(dummyFile, compressedFile, bifEntry.getFileName());
        Files.delete(dummyFile);
        // Delete old BIFF, rename this to real name
        Path realFile = bifEntry.getPath();
        if (realFile == null) {
          realFile = FileManager.query(Profile.getGameRoot(), bifEntry.getFileName());
        }
        if (FileEx.create(realFile).isFile()) {
          Files.delete(realFile);
        }
        Files.move(compressedFile, realFile);
      } else if (format == AbstractBIFFReader.Type.BIFC) {
        compressedFile = Files.createTempFile(biffPath, "_dummy", ".bif");
        compressBIFC(dummyFile, compressedFile);
        Files.delete(dummyFile);
        // Delete old BIFF, rename this to real name
        Path realFile = bifEntry.getPath();
        if (realFile == null) {
          realFile = FileManager.query(Profile.getRootFolders(), bifEntry.getFileName());
        }
        if (FileEx.create(realFile).isFile()) {
          Files.delete(realFile);
        }
        Files.move(compressedFile, realFile);
      }
    } finally {
      if (dummyFile != null && FileEx.create(dummyFile).isFile()) {
        try {
          Files.delete(dummyFile);
        } catch (IOException e) {
          Logger.trace(e);
        }
      }
      if (compressedFile != null && FileEx.create(compressedFile).isFile()) {
        try {
          Files.delete(compressedFile);
        } catch (IOException e) {
          Logger.trace(e);
        }
      }
    }
  }

  private BIFFResourceEntry reloadNode(ResourceEntry entry, int newOffset) {
    ResourceFactory.getResourceTreeModel().removeResourceEntry(entry);
    final BIFFResourceEntry newEntry = new BIFFResourceEntry(bifEntry, entry.getResourceName(), newOffset);
    ResourceFactory.getResourceTreeModel().addResourceEntry(newEntry, newEntry.getTreeFolderName(), true);
    return newEntry;
  }

  private void writeBIFF(Path file) throws Exception {
    try (OutputStream os = StreamUtils.getOutputStream(file, true)) {
      StreamUtils.writeString(os, "BIFF", 4);
      StreamUtils.writeString(os, "V1  ", 4);
      StreamUtils.writeInt(os, resources.size());
      StreamUtils.writeInt(os, tileResources.size());
      StreamUtils.writeInt(os, 0x14);
      int offset = 20 + 16 * resources.size() + 20 * tileResources.size();
      int index = 0; // Non-tileset index starts at 0
      for (final Map.Entry<ResourceEntry, Boolean> entry : resources.entrySet()) {
        final ResourceEntry resourceEntry = entry.getKey();
        BIFFResourceEntry newentry = reloadNode(resourceEntry, index);
        StreamUtils.writeInt(os, newentry.getLocator());
        StreamUtils.writeInt(os, offset); // Offset
        int[] info = resourceEntry.getResourceInfo(entry.getValue());
        offset += info[0];
        StreamUtils.writeInt(os, info[0]); // Size
        StreamUtils.writeShort(os, (short) ResourceFactory.getKeyfile().getExtensionType(resourceEntry.getExtension()));
        StreamUtils.writeShort(os, (short) 0); // Unknown
        index++;
      }
      index = 1; // Tileset index starts at 1
      for (final Map.Entry<ResourceEntry, Boolean> entry : tileResources.entrySet()) {
        final ResourceEntry resourceEntry = entry.getKey();
        BIFFResourceEntry newentry = reloadNode(resourceEntry, index);
        StreamUtils.writeInt(os, newentry.getLocator());
        StreamUtils.writeInt(os, offset); // Offset
        int[] info = resourceEntry.getResourceInfo(entry.getValue());
        StreamUtils.writeInt(os, info[0]); // Number of tiles
        StreamUtils.writeInt(os, info[1]); // Size of each tile (in bytes)
        offset += info[0] * info[1];
        StreamUtils.writeShort(os, (short) ResourceFactory.getKeyfile().getExtensionType(resourceEntry.getExtension()));
        StreamUtils.writeShort(os, (short) 0); // Unknown
        index++;
      }
      for (final Map.Entry<ResourceEntry, Boolean> entry : resources.entrySet()) {
        StreamUtils.writeBytes(os, entry.getKey().getResourceBuffer(entry.getValue()));
      }
      for (final Map.Entry<ResourceEntry, Boolean> entry : tileResources.entrySet()) {
        final ResourceEntry resourceEntry = entry.getKey();
        ByteBuffer buffer = resourceEntry.getResourceBuffer(entry.getValue());
        int[] info = resourceEntry.getResourceInfo(entry.getValue());
        int size = info[0] * info[1];
        int toSkip = buffer.limit() - size;
        if (toSkip > 0) {
          buffer.position(toSkip); // skipping TIS header
        }
        StreamUtils.writeBytes(os, buffer);
      }
    }
  }
}
