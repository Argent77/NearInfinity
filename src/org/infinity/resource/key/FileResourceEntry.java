// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.util.io.ByteBufferInputStream;
import org.infinity.util.io.StreamUtils;

public final class FileResourceEntry extends ResourceEntry
{
  private final boolean override;
  private Path file;

  public FileResourceEntry(Path file)
  {
    this(file, false);
  }

  public FileResourceEntry(Path file, boolean override)
  {
    this.file = file;
    this.override = override;
  }

  @Override
  public String toString()
  {
    return file.getFileName().toString().toUpperCase(Locale.ENGLISH);
  }

  public void deleteFile() throws IOException
  {
    Files.delete(file);
  }

  @Override
  public Path getActualPath(boolean ignoreOverride)
  {
    return file;
  }

  @Override
  public long getResourceSize(boolean ignoreOverride)
  {
    try {
      return Files.size(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return -1L;
  }

  @Override
  public String getExtension()
  {
    String name = file.getFileName().toString();
    return name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ENGLISH);
  }

  @Override
  public ByteBuffer getResourceBuffer(boolean ignoreOverride) throws IOException
  {
    try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ)) {
      ByteBuffer bb = StreamUtils.getByteBuffer((int)ch.size());
      ch.read(bb);
      bb.position(0);
      return bb;
    }
  }

  @Override
  public InputStream getResourceDataAsStream(boolean ignoreOverride) throws IOException
  {
    return new ByteBufferInputStream(getResourceBuffer(ignoreOverride));
  }

  @Override
  public int[] getResourceInfo(boolean ignoreOverride)
  {
    return getLocalFileInfo(file);
  }

  @Override
  public String getResourceName()
  {
    return file.getFileName().toString();
  }

  @Override
  public String getTreeFolderName()
  {
    if (BrowserMenuBar.getInstance() != null) {
      int mode = BrowserMenuBar.getInstance().getOverrideMode();
      if (ResourceFactory.getKeyfile().getExtensionType(getExtension()) != -1) {
        if (mode == BrowserMenuBar.OVERRIDE_IN_THREE) {
          return getExtension();
        } else if (mode == BrowserMenuBar.OVERRIDE_SPLIT &&
                   ResourceFactory.getKeyfile().getResourceEntry(getResourceName()) != null) {
          return getExtension();
        }
      }
    }
    if (hasOverride()) {
      return Profile.getOverrideFolderName();
    } else {
      return file.getParent().getFileName().toString();
    }
  }

  @Override
  public ResourceTreeFolder getTreeFolder()
  {
    ResourceTreeFolder retVal = null;

    // check extra folders first
    List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    for (final Path path: extraPaths) {
      if (file.startsWith(path)) {
        // finding correct subfolder
        int startIndex = path.getNameCount() - 1; // include main folder
        int endIndex = file.getNameCount() - 1; // exlude filename
        Path subPath = file.subpath(startIndex, endIndex);

        retVal = (ResourceTreeFolder)ResourceFactory.getResourceTreeModel().getRoot();
        for (int idx = 0, cnt = subPath.getNameCount(); idx < cnt && retVal != null; idx++) {
          String name = subPath.getName(idx).toString();
          List<ResourceTreeFolder> folders = retVal.getFolders();
          retVal = null;
          for (final ResourceTreeFolder subFolder: folders) {
            if (name.equalsIgnoreCase(subFolder.folderName())) {
              retVal = subFolder;
              break;
            }
          }
        }
      }
    }

    // check override folders
    if (retVal == null) {
      retVal = ResourceFactory.getResourceTreeModel().getFolder(getTreeFolderName());
    }

    return retVal;
  }

  @Override
  public boolean hasOverride()
  {
    return override;
  }

  public void renameFile(String newName, boolean overwrite) throws IOException
  {
    Path basePath = file.getParent();
    CopyOption[] options = new CopyOption[overwrite ? 2 : 1];
    options[0] = StandardCopyOption.ATOMIC_MOVE;
    if (overwrite) {
      options[1] = StandardCopyOption.REPLACE_EXISTING;
    }
    file = Files.move(file, basePath.resolve(newName), options);
  }
}
