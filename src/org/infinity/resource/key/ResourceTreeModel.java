// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.infinity.util.Misc;

public final class ResourceTreeModel implements TreeModel
{
  private final List<TreeModelListener> treeModelListeners = new ArrayList<>();
  private final Map<String, ResourceEntry> entries = new HashMap<>(25000);
  private final Map<String, ResourceTreeFolder> folders = new TreeMap<>(Misc.getIgnoreCaseComparator());
  private final ResourceTreeFolder root = new ResourceTreeFolder(null, "");

  public ResourceTreeModel()
  {
  }

// --------------------- Begin Interface TreeModel ---------------------

  @Override
  public ResourceTreeFolder getRoot()
  {
    return root;
  }

  @Override
  public Object getChild(Object parent, int index)
  {
    if (parent instanceof ResourceTreeFolder) {
      return ((ResourceTreeFolder)parent).getChild(index);
    }
    return null;
  }

  @Override
  public int getChildCount(Object parent)
  {
    if (parent instanceof ResourceTreeFolder) {
      return ((ResourceTreeFolder)parent).getChildCount();
    }
    return 0;
  }

  @Override
  public boolean isLeaf(Object node)
  {
    return !(node instanceof ResourceTreeFolder);
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newvalue)
  {
    throw new IllegalArgumentException(); // Not allowed
  }

  @Override
  public int getIndexOfChild(Object parent, Object child)
  {
    if (parent instanceof ResourceTreeFolder) {
      return ((ResourceTreeFolder)parent).getIndexOfChild(child);
    }
    return -1;
  }

  @Override
  public void addTreeModelListener(TreeModelListener l)
  {
    treeModelListeners.add(l);
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l)
  {
    treeModelListeners.remove(l);
  }

// --------------------- End Interface TreeModel ---------------------

  /**
   * Recursively adds all files from directory {@code directory} as file resources
   * under {@code parentFolder}.
   *
   * @param parentFolder Navigation tree element under which new fiels will be added
   * @param directory Directory from which all files will be added (recursively)
   * @param overwrite If {@code true}, new files will replace existent resources
   *        otherwise it will be skipped and not added to the tree
   */
  public void addDirectory(ResourceTreeFolder parentFolder, Path directory, boolean overwrite)
  {
    try (DirectoryStream<Path> dstream = Files.newDirectoryStream(directory)) {
      Iterator<Path> iter = dstream.iterator();
      if (iter.hasNext()) {
        final ResourceTreeFolder folder = addFolder(parentFolder, directory.getFileName().toString());
        iter.forEachRemaining((path) -> {
          if (Files.isDirectory(path)) {
            addDirectory(folder, path, overwrite);
          } else {
            folder.addResourceEntry(new FileResourceEntry(path), overwrite);
          }
        });
        parentFolder.sortChildren(true);
      }
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
  }

  public ResourceTreeFolder addResourceEntry(ResourceEntry entry, String folderName, boolean overwrite)
  {
    if (entry.isVisible()) {
      ResourceTreeFolder folder = addFolder(folderName);
      folder.addResourceEntry(entry, overwrite);
      entries.put(entry.getResourceName().toUpperCase(Locale.ENGLISH), entry);
      folder.sortChildren(false);
      return folder;
    }
    return getFolder(folderName);
  }

  public List<BIFFResourceEntry> getBIFFResourceEntries()
  {
    return getBIFFResourceEntries(null);
  }

  public List<BIFFResourceEntry> getBIFFResourceEntries(Path keyFile)
  {
    List<BIFFResourceEntry> list = new ArrayList<BIFFResourceEntry>();
    for (int i = 0; i < root.getFolders().size(); i++) {
      List<ResourceEntry> entries = root.getFolders().get(i).getResourceEntries();
      for (int j = 0; j < entries.size(); j++) {
        ResourceEntry o = entries.get(j);
        if (o instanceof BIFFResourceEntry) {
          BIFFResourceEntry bre = (BIFFResourceEntry)o;
          if (keyFile == null || bre.getKeyfile().equals(keyFile)) {
            list.add((BIFFResourceEntry)o);
          }
        }
      }
    }
    return list;
  }

  public ResourceTreeFolder getFolder(ResourceTreeFolder parentFolder, String name)
  {
    ResourceTreeFolder folder = null;
    if (parentFolder != null) {
      for (final ResourceTreeFolder rtf: parentFolder.getFolders()) {
        if (rtf.folderName().equalsIgnoreCase(name)) {
          folder = rtf;
          break;
        }
      }
    }
    return folder;
  }

  public ResourceTreeFolder getFolder(String text)
  {
    return folders.get(text);
  }

  /**
   * Adds a folder of specified name to the root folder if not yet existing.
   * Returns the new or existing folder
   */
  public ResourceTreeFolder addFolder(String folderName)
  {
    return addFolder(root, folderName);
  }

  /**
   * Adds a folder of specified name to the parent folder if not yet existing.
   * Returns the new or existing folder
   */
  public ResourceTreeFolder addFolder(ResourceTreeFolder parent, String folderName)
  {
    if (folderName != null) {
      if (parent == null) {
        parent = root;
      }
      ResourceTreeFolder folder = getFolder(parent, folderName);
      if (folder == null) {
        if (folderName.length() > 0) {
          folderName = Character.toUpperCase(folderName.charAt(0)) + folderName.substring(1);
        }
        folder = new ResourceTreeFolder(parent, folderName);
        folders.put(folderName, folder);
        parent.addFolder(folder);
        parent.sortChildren(false);
      }
      return folder;
    }
    return null;
  }

  public TreePath getPathToNode(ResourceEntry entry)
  {
    List<Object> path = new ArrayList<>(4);
    path.add(entry);
    ResourceTreeFolder parent = entry.getTreeFolder();
    while (parent != null) {
      path.add(parent);
      parent = parent.getParentFolder();
    }
    Collections.reverse(path);
    return new TreePath(path.toArray());
  }

  public TreePath getPathToNode(ResourceTreeFolder folder)
  {
    TreePath retVal = null;
    if (folder != null) {
      List<Object> path = new ArrayList<>(4);
      while (folder != null) {
        path.add(folder);
        folder = folder.getParentFolder();
      }
      retVal = new TreePath(path.toArray());
    }
    return retVal;
  }

  public Collection<ResourceEntry> getResourceEntries()
  {
    return entries.values();
  }

  public ResourceEntry getResourceEntry(String entryname)
  {
    return getResourceEntry(entryname, false);
  }

  public ResourceEntry getResourceEntry(String entryname, boolean includeExtraFolders)
  {
    ResourceEntry retVal = null;

    if (entryname != null) {
      entryname = entryname.toUpperCase(Locale.ENGLISH);
      ResourceEntry entry = entries.get(entryname);
      if (entry != null) {
        retVal = entry;
      } else if (includeExtraFolders) {
        for (final ResourceTreeFolder folder: folders.values()) {
          List<ResourceEntry> entries = folder.getResourceEntries();
          for (final ResourceEntry curEntry: entries) {
            if (curEntry.getResourceName().equalsIgnoreCase(entryname)) {
              return curEntry;
            }
          }
        }
      }
    }

    return retVal;
  }

  public List<ResourceEntry> removeDirectory(ResourceTreeFolder parentFolder, String folderName)
  {
    List<ResourceEntry> retVal = new ArrayList<>();
    if (folderName != null) {
      if (parentFolder == null) {
        parentFolder = root;
      }

      ResourceTreeFolder folder = getFolder(parentFolder, folderName);
      if (folder != null) {
        List<ResourceEntry> entries = folder.getResourceEntries();
        for (final ResourceEntry entry: entries) {
          folder.removeResourceEntry(entry);
        }
        parentFolder.removeFolder(folder);
        folders.remove(folder.folderName());
        retVal.addAll(entries);
      }
    }
    return retVal;
  }

  public void removeResourceEntry(ResourceEntry entry)
  {
    removeResourceEntry(entry, entry.getTreeFolderName());
  }

  public void removeResourceEntry(ResourceEntry entry, String folder)
  {
    ResourceTreeFolder parent = folders.get(folder);
    if (parent == null) {
      return;
    }
    TreePath path = getPathToNode(entry).getParentPath();
    TreeModelEvent event = new TreeModelEvent(this, path, new int[]{getIndexOfChild(parent, entry)},
                                              new Object[]{entry});
    parent.removeResourceEntry(entry);
    entries.remove(entry.getResourceName().toUpperCase(Locale.ENGLISH));
    if (parent.getChildCount() == 0) {
      root.removeFolder(parent);
      folders.remove(parent.folderName());
    }
    for (int i = 0; i < treeModelListeners.size(); i++) {
      treeModelListeners.get(i).treeNodesRemoved(event);
    }
  }

  public void resourceEntryChanged(FileResourceEntry entry)
  {
    TreePath parentPath = getPathToNode(entry).getParentPath();
    ResourceTreeFolder parentFolder = (ResourceTreeFolder)parentPath.getLastPathComponent();
    TreeModelEvent event = new TreeModelEvent(this, parentPath,
        new int[]{getIndexOfChild(parentFolder, entry)}, new Object[]{entry});
    for (int i = 0; i < treeModelListeners.size(); i++) {
      treeModelListeners.get(i).treeNodesChanged(event);
    }
  }

  public int size()
  {
    int size = 0;
    for (int i = 0; i < root.getChildCount(); i++) {
      size += ((ResourceTreeFolder)root.getChild(i)).getChildCount();
    }
    return size;
  }

  public void sort()
  {
    root.sortChildren(true);
    updateFolders(root);
  }

  public void updateFolders(ResourceTreeFolder... folders)
  {
    if (folders != null && folders.length > 0) {
      fireTreeStructureChanged(new TreePath(folders));
    }
  }

  private void fireTreeStructureChanged(TreePath changed)
  {
    TreeModelEvent event = new TreeModelEvent(this, changed);
    for (int i = 0; i < treeModelListeners.size(); i++) {
      treeModelListeners.get(i).treeStructureChanged(event);
    }
  }
}
