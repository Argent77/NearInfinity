// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.gui.ViewerUtil;
import org.infinity.resource.Profile;
import org.infinity.util.SimpleListModel;
import org.infinity.util.io.FileEx;
import org.tinylog.Logger;

/**
 * A panel that provides options for adding or removing files or folders of selected file types for conversion
 * operations.
 */
public class ConvertFileListPanel extends AbstractConvertPanel implements ActionListener, ListSelectionListener {
  /** One or more files were added to the file list. */
  public static final UpdateReason REASON_FILES_ADDED = new UpdateReason(1);
  /** One or more files were removed from the file list. */
  public static final UpdateReason REASON_FILES_REMOVED = new UpdateReason(2);

  private final List<FileNameExtensionFilter> filterList = new ArrayList<>();

  private SimpleListModel<Path> fileListModel;
  private FileListCellRenderer fileListRenderer;
  private JList<Path> fileList;
  private JButton bAdd, bAddFolder, bRemove, bRemoveAll;
  private Path currentDir;

  /**
   * Initializes a new file list panel with the specified configuration.
   *
   * @param title       Title string that is used for the border around the panel.
   * @param initialDir  Initial directory that is opened when adding new files.
   */
  public ConvertFileListPanel(String title, Path initialDir) {
    this(title, initialDir, null);
  }

  /**
   * Initializes a new file list panel with the specified configuration.
   *
   * @param title       Title string that is used for the border around the panel.
   * @param initialDir  Initial directory that is opened when adding new files.
   * @param fileFilters Collection of {@link FileNameExtensionFilter} definitions. The first definition will be
   *                      preselected in the "Add files" dialog.
   */
  public ConvertFileListPanel(String title, Path initialDir, Collection<FileNameExtensionFilter> fileFilters) {
    super(new GridBagLayout(), title);
    init();
    setCurrentDirectory(initialDir);
    setFileFilters(fileFilters);
  }

  /** Returns the number of file entries in the list. */
  public int getFileCount() {
    return fileListModel.size();
  }

  /**
   * Returns the file at the specified file list index.
   *
   * @param index Index of the file entry in the list.
   * @return {@link Path} instance of the file.
   * @throws IndexOutOfBoundsException if the index is out of bounds.
   */
  public Path getFile(int index) throws IndexOutOfBoundsException {
    if (index < 0 || index >= fileListModel.size()) {
      throw new IndexOutOfBoundsException();
    }
    return fileListModel.get(index);
  }

  /**
   * Returns an unmodifiable list of all files present in the list.
   *
   * @return List of {@link Path} instances.
   */
  public List<Path> getFiles() {
    return Collections.unmodifiableList(new ArrayList<Path>(fileListModel));
  }

  /**
   * Returns whether the specified file is in the list.
   *
   * @param file File {@link Path} to check.
   * @return {@code true} if the specified file exists in the file list, {@code false} otherwise.
   */
  public boolean containsFile(Path file) {
    boolean retVal = false;

    if (file != null) {
      return fileListModel.contains(file);
    }

    return retVal;
  }

  public void addFile(Path file) {
    if (file != null && !fileListModel.contains(file)) {
      fileListModel.add(file);
      onListChanged();
      firePanelUpdate(REASON_FILES_ADDED);
    }
  }

  /**
   * Adds the specified files to the list. Duplicate files are skipped.
   *
   * @param files Collection of {@link Path} instances to add to the list.
   */
  public void addFiles(Collection<Path> files) {
    if (files == null) {
      return;
    }

    for (final Path file : files) {
      addFile(file);
    }
  }

  /**
   * Adds files, filtered by the current filter definition, from the specified folder to the list. Duplicate files are
   * skipped.
   *
   * @param folder Directory path with the files to add.
   * @throws IOException if an I/O error occurred.
   */
  public void addFolder(Path folder) throws IOException {
    if (folder == null || !Files.isDirectory(folder)) {
      return;
    }

    // Files are collected first to cleanly terminate the operation if an error occurs
    final List<Path> fileList = new ArrayList<>();
    try (final Stream<Path> stream = Files.list(folder)) {
      for (final Iterator<Path> iter = stream.iterator(); iter.hasNext(); ) {
        final Path file = iter.next();
        if (isFileAccepted(file)) {
          fileList.add(file);
        }
      }
    }
    addFiles(fileList);
  }

  /**
   * Removes the file at the specified list item index.
   *
   * @param index Index of the file entry in the list.
   * @return the removed file {@link Path}.
   * @throws IndexOutOfBoundsException if the index is out of bounds.
   */
  public Path removeFile(int index) throws IndexOutOfBoundsException {
    if (index < 0 || index >= fileListModel.size()) {
      throw new IndexOutOfBoundsException();
    }
    final Path retVal = fileListModel.remove(index);
    onListChanged();
    firePanelUpdate(REASON_FILES_REMOVED);
    return retVal;
  }

  /**
   * Removes all files that are specified by the array of list indices.
   *
   * @param indices Array of file indices to remove from the list.
   * @throws IndexOutOfBoundsException if any of the indices is out of bounds.
   */
  public void removeFiles(int[] indices) throws IndexOutOfBoundsException {
    if (indices == null || indices.length == 0) {
      return;
    }

    // Indices are removed from end to front
    final int[] array = Arrays.copyOf(indices, indices.length);
    Arrays.sort(array);
    for (int i = array.length - 1; i >= 0; i--) {
      removeFile(array[i]);
    }
  }

  /** Removes all file entries from the list. */
  public void removeAllFiles() {
    fileListModel.clear();
    onListChanged();
    firePanelUpdate(REASON_FILES_REMOVED);
  }

  /** Returns the current working directory used for open file or folder dialogs. */
  public Path getCurrentDirectory() {
    return currentDir;
  }

  /**
   * Sets a new current working directory that is used for open file or folder dialogs.
   *
   * @param dir Directory {@link Path}.
   */
  public void setCurrentDirectory(Path dir) {
    if (dir == null || !FileEx.create(dir).isDirectory()) {
      currentDir = Profile.getGameRoot();
    } else {
      currentDir = dir;
    }
  }

  /** Returns the list of currently defined file extension filters. */
  public List<FileNameExtensionFilter> getFileFilters() {
    return Collections.unmodifiableList(filterList);
  }

  /**
   * Applies the specified list of file extension filters. Old filters will be discared.
   *
   * @param fileFilters Collection of {@link FileNameExtensionFilter} definitions.
   */
  public void setFileFilters(Collection<FileNameExtensionFilter> fileFilters) {
    filterList.clear();
    if (fileFilters != null) {
      for (final FileNameExtensionFilter filter : fileFilters) {
        if (filter != null && !filterList.contains(filter)) {
          filterList.add(filter);
        }
      }
    }
  }

  /** Resets the panel to its initial state. */
  public void reset() {
    removeAllFiles();
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == bAdd) {
      onAdd();
    } else if (e.getSource() == bAddFolder) {
      onAddFolder();
    } else if (e.getSource() == bRemove) {
      onRemove();
    } else if (e.getSource() == bRemoveAll) {
      removeAllFiles();
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (e.getSource() == fileList) {
      onListChanged();
    }
  }

  // --------------------- End Interface ListSelectionListener ---------------------

  /** Updates button states based on the current list state. */
  private void onListChanged() {
    final int index = fileList.getSelectedIndex();
    bRemove.setEnabled(index >= 0);
    bRemoveAll.setEnabled(!fileListModel.isEmpty());
  }

  /** Adds new files to the list interactively. */
  private void onAdd() {
    final Path[] files = getOpenFileNames(this, "Add File(s)", currentDir, true, filterList, 0);
    if (files == null || files.length == 0) {
      return;
    }
    currentDir = files[0].getParent();
    onAdd(Arrays.asList(files));
  }

  /** Adds the specified files to the file list. */
  private void onAdd(Collection<Path> files) {
    if (files == null || files.isEmpty()) {
      return;
    }

    final List<Path> skippedFiles = new ArrayList<>(files.size());
    final List<Path> acceptedFiles = new ArrayList<>(files.size());
    for (final Path file : files) {
      if (isFileAccepted(file)) {
        acceptedFiles.add(file);
      } else {
        skippedFiles.add(file);
      }
    }
    addFiles(acceptedFiles);

    if (!skippedFiles.isEmpty()) {
      final StringBuilder sb = new StringBuilder();
      if (skippedFiles.size() == 1) {
        sb.append("1 file has been skipped:\n");
      } else {
        sb.append(skippedFiles.size()).append(" files have been skipped:\n");
      }
      final int maxItems = 5;
      for (int i = 0, count = Math.min(maxItems, skippedFiles.size()); i < count; i++) {
        sb.append("  - ").append(skippedFiles.get(i).getFileName()).append('\n');
      }
      if (skippedFiles.size() > maxItems) {
        sb.append("  - ...\n");
      }
      JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(this), sb.toString(),
          "Information", JOptionPane.WARNING_MESSAGE);
    }
  }

  /** Adds files from a selected folder to the list interactively. */
  private void onAddFolder() {
    final Path folder = getOpenDirectory(ViewerUtil.getWindowAncestor(this), "Add Folder",
        currentDir);
    if (folder == null || !FileEx.create(folder).isDirectory()) {
      return;
    }
    currentDir = folder;

    // Files are collected first to cleanly terminate the operation if an error occurs
    final List<Path> fileList = new ArrayList<>();
    try (final Stream<Path> stream = Files.list(folder)) {
      for (final Iterator<Path> iter = stream.iterator(); iter.hasNext(); ) {
        final Path file = iter.next();
        if (isFileAccepted(file)) {
          addFile(file);
        }
      }
    } catch (IOException e) {
      Logger.error(e);
      JOptionPane.showMessageDialog(ViewerUtil.getWindowAncestor(this),
          "Unable to read files from the specified folder:\n" + folder.toString(), "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    onAdd(fileList);
  }

  /** Removes all currently selected file entries from the list. */
  private void onRemove() {
    final int[] selected = fileList.getSelectedIndices();
    removeFiles(selected);
  }

  /**
   * Returns whether the specified file passes any of the defined file filters.
   *
   * @param file File {@link Path} to test.
   * @return {@code true} if the file passes any of the currently defined {@link FileNameExtensionFilter} definitions.
   *         Returns {@code false} otherwise.
   */
  private boolean isFileAccepted(Path file) {
    boolean retVal = false;
    if (file != null && Files.isRegularFile(file)) {
      final File f = file.toFile();
      for (final FileNameExtensionFilter filter : filterList) {
        retVal = filter.accept(f);
        if (retVal) {
          break;
        }
      }
    }
    return retVal;
  }

  private void init() {
    fileListModel = new SimpleListModel<>();
    fileListRenderer = new FileListCellRenderer(false);
    fileList = new JList<>(fileListModel);
    fileList.setCellRenderer(fileListRenderer);
    fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    fileList.addListSelectionListener(this);
    final JScrollPane scroll = new JScrollPane(fileList);

    bAdd = new JButton("Add...");
    bAdd.addActionListener(this);

    bAddFolder = new JButton("Add folder...");
    bAddFolder.addActionListener(this);

    bRemove = new JButton("Remove");
    bRemove.addActionListener(this);

    bRemoveAll = new JButton("Remove all");
    bRemoveAll.addActionListener(this);

    final GridBagConstraints c = new GridBagConstraints();

    final JPanel panelButtons = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    panelButtons.add(bAdd, c);
    ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    panelButtons.add(bAddFolder, c);
    ViewerUtil.setGBC(c, 1, 1, 1, 2, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 0, 8), 0, 0);
    panelButtons.add(new JPanel(), c);
    ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_END, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    panelButtons.add(bRemove, c);
    ViewerUtil.setGBC(c, 2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_END, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    panelButtons.add(bRemoveAll, c);

    final JPanel panelMain = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    panelMain.add(scroll, c);
    ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    panelMain.add(panelButtons, c);

    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 4, 4), 0, 0);
    add(panelMain, c);
  }

  // -------------------------- INNER CLASSES --------------------------

  /**
   * Specialization of the {@link DefaultListCellRenderer} that provides a customized display of {@link File} and
   * {@link Path} objects.
   */
  private static class FileListCellRenderer extends DefaultListCellRenderer {
    private boolean shortDisplay;

    /**
     * Initializes a new {@code FileListCellRenderer} object.
     *
     * @param shortDisplay Whether only the filename part of {@link File} or {@link Path} list items should be
     *                       displayed. Otherwise, the whole path is displayed.
     */
    public FileListCellRenderer(boolean shortDisplay) {
      super();
      this.shortDisplay = shortDisplay;
    }

    /**
     * Returns whether only the filename part of {@link File} or {@link Path} list items should be displayed.
     * Otherwise, the whole path is displayed.
     */
    @SuppressWarnings("unused")
    public boolean isShortDisplay() {
      return shortDisplay;
    }

    /**
     * Specify whether only the filename part of {@link File} or {@link Path} list items should be displayed. Otherwise,
     * the whole path is displayed.
     * <p>
     * <strong>Note:</strong> Changing this attribute requires a manual refresh of the list content.
     * </p>
     */
    @SuppressWarnings("unused")
    public void setShortDisplay(boolean newValue) {
      shortDisplay = newValue;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
        boolean cellHasFocus) {
      final Object newValue;
      if (shortDisplay && value instanceof File) {
        newValue = ((File)value).getName();
      } else if (shortDisplay && value instanceof Path) {
        newValue = ((Path)value).getFileName();
      } else {
        newValue = value;
      }
      return super.getListCellRendererComponent(list, newValue, index, isSelected, cellHasFocus);
    }
  }
}
