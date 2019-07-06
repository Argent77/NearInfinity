// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.Writeable;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

public final class BIFFResourceEntry extends ResourceEntry implements Writeable
{
  /** Full path to KEY file containing BIFF entry. */
  private final Path keyFile;
  /** Resource name without extension. */
  private final String resourceName;
  /** Resource type. */
  private final int type;
  /** Cached resource extension (in upper case). */
  private final String extension;
  private boolean hasOverride = false;
  private int locator;

  public BIFFResourceEntry(BIFFEntry bifEntry, String resourceName, int offset)
  {
    final int p = resourceName.lastIndexOf('.');
    if (p <= 0) {
      throw new IllegalArgumentException("BIFF resource name '"+resourceName+"' doesn't contain extension");
    }
    this.keyFile = ResourceFactory.getKeyfile().getKeyfile();
    this.resourceName = resourceName.substring(0, p);
    this.type = ResourceFactory.getKeyfile().getExtensionType(resourceName.substring(p+1));
    this.extension = ResourceFactory.getKeyfile().getExtension(this.type);

    int bifIndex = bifEntry.getIndex();
    this.locator = bifIndex << 20;
    if (this.type == Keyfile.TYPE_TIS) { // TIS
      this.locator |= offset << 14;
    } else {
      this.locator |= offset;
    }
  }

  BIFFResourceEntry(Path keyFile, ByteBuffer buffer, int offset)
  {
    if (keyFile == null || buffer == null) {
      throw new NullPointerException("Path to KEY file and byte buffer with BIFF content must not be null");
    }
    this.keyFile = keyFile;
    this.resourceName = StreamUtils.readString(buffer, offset, 8);
    this.type = buffer.getShort() & 0xffff;

    String ext = ResourceFactory.getKeyfile().getExtension(type);
    if (ext == null) {
      ext = "Unknown (" + Integer.toHexString(type) + "h)";
    }
    this.extension = ext;

    this.locator = buffer.getInt();
  }

// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    StreamUtils.writeString(os, resourceName, 8);
    StreamUtils.writeShort(os, (short)type);
    StreamUtils.writeInt(os, locator);
  }

// --------------------- End Interface Writeable ---------------------

  @Override
  public boolean equals(Object o)
  {
    if (o == this) {
      return true;
    } else if (o instanceof BIFFResourceEntry) {
      BIFFResourceEntry other = (BIFFResourceEntry)o;
      return (locator == other.locator) &&
             (type == other.type) &&
             resourceName.equalsIgnoreCase(other.resourceName);
    }
    return false;
  }

  @Override
  public String toString()
  {
    return getResourceName();
  }

  public Path getKeyfile()
  {
    return keyFile;
  }

  public void deleteOverride() throws IOException
  {
    List<Path> overrides = Profile.getOverrideFolders(false);
    Path file = FileManager.query(overrides, getResourceName());
    if (file != null && Files.isRegularFile(file)) {
      Files.deleteIfExists(file);
    }
    file = FileManager.query(overrides, getResourceName());
    synchronized (this) {
      hasOverride = (file != null && Files.isRegularFile(file));
    }
  }

  @Override
  public Path getActualPath(boolean ignoreOverride)
  {
    if (!ignoreOverride) {
      List<Path> overrides = Profile.getOverrideFolders(false);
      Path file = FileManager.query(overrides, getResourceName());
      if (file != null && Files.isRegularFile(file)) {
        return file;
      }
    }
    try {
      return ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry()).getFile();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public long getResourceSize(boolean ignoreOverride)
  {
    long retVal = -1L;
    try {
      if (!ignoreOverride) {
        List<Path> overrides = Profile.getOverrideFolders(false);
        Path file = FileManager.query(overrides, getResourceName());
        if (file != null && Files.isRegularFile(file)) {
          retVal = Files.size(file);
          return retVal;
        }
      }
      AbstractBIFFReader biff = ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry());
      int[] info = biff.getResourceInfo(locator);
      if (info != null) {
        if (info.length == 1) {
          retVal = info[0];
        } else if (info.length == 2) {
          retVal = info[0]*info[1] + 0x18;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return retVal;
  }

  public BIFFEntry getBIFFEntry()
  {
    int sourceIndex = (locator >> 20) & 0xfff;
    return ResourceFactory.getKeyfile().getBIFFEntry(keyFile, sourceIndex);
  }

  @Override
  public String getExtension()
  {
    return extension;
  }

  public int getLocator()
  {
    return locator;
  }

  @Override
  public ByteBuffer getResourceBuffer(boolean ignoreOverride) throws Exception
  {
    if (!ignoreOverride) {
      List<Path> overrides = Profile.getOverrideFolders(false);
      Path file = FileManager.query(overrides, getResourceName());
      if (file != null && Files.isRegularFile(file)) {
        try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ)) {
          ByteBuffer bb = StreamUtils.getByteBuffer((int)ch.size());
          if (ch.read(bb) < ch.size()) {
            throw new IOException();
          }
          bb.position(0);
          return bb;
        }
      }
    }
    AbstractBIFFReader biff = ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry());
    return biff.getResourceBuffer(locator);
  }

  @Override
  public InputStream getResourceDataAsStream(boolean ignoreOverride) throws Exception
  {
    if (!ignoreOverride) {
      List<Path> overrides = Profile.getOverrideFolders(false);
      Path file = FileManager.query(overrides, getResourceName());
      if (file != null && Files.isRegularFile(file)) {
        return StreamUtils.getInputStream(file);
      }
    }
    AbstractBIFFReader biff = ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry());
    return biff.getResourceAsStream(locator);
  }

  @Override
  public int[] getResourceInfo(boolean ignoreOverride) throws Exception
  {
    if (!ignoreOverride) {
      List<Path> overrides = Profile.getOverrideFolders(false);
      Path file = FileManager.query(overrides, getResourceName());
      if (file != null && Files.isRegularFile(file)) {
        return getLocalFileInfo(file);
      }
    }
    AbstractBIFFReader biff = ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry());
    return biff.getResourceInfo(locator);
  }

  @Override
  public String getResourceName()
  {
    return resourceName + '.' + extension;
  }

  @Override
  public String getTreeFolderName()
  {
    if ((BrowserMenuBar.getInstance() != null) &&
        (BrowserMenuBar.getInstance().getOverrideMode() == BrowserMenuBar.OVERRIDE_IN_OVERRIDE) &&
        hasOverride()) {
      return Profile.getOverrideFolderName();
    }
    return getExtension();
  }

  @Override
  public ResourceTreeFolder getTreeFolder()
  {
    return ResourceFactory.getResourceTreeModel().getFolder(getTreeFolderName());
  }

  public int getType()
  {
    return type;
  }

  @Override
  public boolean hasOverride()
  {
    // TODO: update dynamically via WatchService class?
    if (!BrowserMenuBar.getInstance().cacheOverride()) {
      List<Path> overrides = Profile.getOverrideFolders(false);
      Path file = FileManager.query(overrides, getResourceName());
      synchronized (this) {
        hasOverride = (file != null && Files.isRegularFile(file));
      }
    }
    return hasOverride;
  }

  public synchronized void setOverride(boolean hasOverride)
  {
    this.hasOverride = hasOverride;
  }

  synchronized void adjustSourceIndex(int index)
  {
    int sourceindex = (locator >> 20) & 0xfff;
    if (sourceindex > index) {
      sourceindex--;
      locator = (sourceindex << 20) | (locator & 0xfffff);
    }
  }
}
