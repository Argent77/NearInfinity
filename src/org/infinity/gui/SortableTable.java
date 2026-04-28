// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;

import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.util.ArrayUtil;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.io.FileEx;

public final class SortableTable extends JTable implements MouseListener {
  private static final String WITH_DELIMITERS = "(?<=%1$s)(?!%1$s)|(?<!%1$s)(?=%1$s)";

  private static final Pattern SPLIT_BY_NUMBER = Pattern.compile(String.format(WITH_DELIMITERS, "\\d+"));

  /**
   * Comparator, that sorts strings as numbers if it looks like numbers and lexicographically with ignore case
   * otherwise.
   */
  private static final Comparator<String> SORTER = (String s1, String s2) -> {
    try {
      final int i1 = Integer.parseInt(s1);
      final int i2 = Integer.parseInt(s2);
      return Integer.compare(i1, i2);
    } catch (NumberFormatException ex) {
      return s1.compareToIgnoreCase(s2);
    }
  };

  private final SortableTableModel tableModel;

  private boolean sortAscending;
  private int sortByColumn;

  public SortableTable(String[] columnNames, Class<?>[] columnClasses, Integer[] columnWidths) {
    tableModel = new SortableTableModel(columnNames, columnClasses);
    setModel(tableModel);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setDefaultRenderer(Object.class, new ToolTipTableCellRenderer());
    getTableHeader().setDefaultRenderer(new TableHeaderRenderer());
    getTableHeader().addMouseListener(this);
    for (int i = 0; i < columnWidths.length; i++) {
      getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
    }
  }

  public void addTableItem(TableItem item) {
    tableModel.addTableItem(item);
  }

  public void clear() {
    tableModel.clear();
  }

  public TableItem getTableItemAt(int rowIndex) {
    return tableModel.getTableItemAt(rowIndex);
  }

  public void tableComplete() {
    tableModel.sort();
  }

  public void tableComplete(int sortByColumn) {
    this.sortByColumn = sortByColumn;
    tableModel.sort();
  }

  public void saveCheckResult(Component parent, String header) {
    saveResult(parent, "Save check result", header);
  }

  public void saveSearchResult(Component parent, String query) {
    saveResult(parent, "Save search result", "Searched for: " + query);
  }

  private void saveResult(Component parent, String dialogTitle, String header) {
    final FileNameExtensionFilter filterTxt = new FileNameExtensionFilter("Text files (*.txt)", "txt");
    final FileNameExtensionFilter filterCsv = new FileNameExtensionFilter("CSV files (*.csv)", "csv");
    final JFileChooser chooser = new JFileChooser(Profile.getGameRoot().toFile());
    chooser.setDialogTitle(dialogTitle);
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.addChoosableFileFilter(filterTxt);
    chooser.addChoosableFileFilter(filterCsv);
    chooser.setFileFilter(filterTxt);
    chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "result"));
    if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
      final FileNameExtensionFilter curFilter = (FileNameExtensionFilter)chooser.getFileFilter();
      final Path output = Misc.ensureFileExtension(chooser.getSelectedFile(), curFilter.getExtensions()[0]).toPath();
      if (FileEx.create(output).exists()) {
        if (ResourceFactory.confirmOverwrite(output, true, parent, dialogTitle) != 0) {
          return;
        }
      }

      try {
        if (chooser.getFileFilter() == filterCsv) {
          saveResultAsCsv(output);
        } else {
          saveResultAsText(output, header, "Number of hits: " + getRowCount());
        }
        JOptionPane.showMessageDialog(parent, "Result saved to " + output, "Save complete",
            JOptionPane.INFORMATION_MESSAGE);
      } catch (Exception e) {
        JOptionPane.showMessageDialog(parent, "Error while saving " + output + " (details in the trace)", "Error",
            JOptionPane.ERROR_MESSAGE);
        Logger.error(e);
      }
    }
  }

  /**
   * Saves the result as a text file.
   *
   * @param outFile Path of the output file.
   * @param header  Custom header string
   * @param extra   Optional information to print. Specify {@code null} to omit.
   * @throws Exception thrown if an error occurred.
   */
  private void saveResultAsText(Path outFile, String header, String extra) throws Exception {
    if (outFile == null) {
      throw new NullPointerException("outFile is null");
    }

    try (final BufferedWriter bw = Files.newBufferedWriter(outFile)) {
      bw.write(header);
      bw.newLine();
      if (extra != null) {
        bw.write(extra);
        bw.newLine();
      }
      for (int i = 0; i < getRowCount(); i++) {
        bw.write(getTableItemAt(i).toString());
        bw.newLine();
      }
    }
  }

  /**
   * Saves the result in machine-readable CSV format.
   *
   * @param outFile Path of the output file.
   * @throws Exception thrown if an error occurred.
   */
  private void saveResultAsCsv(Path outFile) throws Exception {
    if (outFile == null) {
      throw new NullPointerException("outFile is null");
    }

    final String nl = "\r\n";
    try (final BufferedWriter bw = Files.newBufferedWriter(outFile)) {

      // table header
      {
        final StringBuilder sb = new StringBuilder();
        for (int col = 0, colCount = getColumnCount(); col < colCount; col++) {
          if (col > 0) {
            sb.append(',');
          }
          final String fieldValue = getCsvEntry(Objects.toString(getTableHeader().getColumnModel().getColumn(col).getHeaderValue(), ""));
          sb.append(fieldValue);
        }
        sb.append(nl);
        bw.write(sb.toString());
      }

      // table content
      for (int row = 0, rowCount = getRowCount(); row < rowCount; row++) {
        final StringBuilder sb = new StringBuilder();
        for (int col = 0, colCount = getColumnCount(); col < colCount; col++) {
          if (col > 0) {
            sb.append(',');
          }
          final String fieldValue = getCsvEntry(Objects.toString(tableModel.getValueAt(row, col), ""));
          sb.append(fieldValue);
        }
        sb.append(nl);
        bw.write(sb.toString());
      }
    }
  }

  /** Returns a field string that conforms to the CSV requirements. */
  private String getCsvEntry(String text) {
    if (text == null) {
      text = "";
    }

    final boolean quote = text.indexOf('"') >= 0 || text.indexOf(',') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0;
    if (quote) {
      text = text.replaceAll("\"", "\"\"");
    }

    if (quote) {
      return '"' + text + '"';
    } else {
      return text;
    }
  }

  /**
   * Scrolls the table within an enclosing viewport to make the specified row completely visible. This calls
   * {@code scrollRectToVisible} with the bounds of the specified row. For this method to work, the {@code JTable} must
   * be within a JViewport. If the given index is outside the table's range of rows, this method results in nothing.
   *
   * @param index the index of the row to make visible.
   */
  public void ensureIndexIsVisible(int index) {
    Rectangle rect = getCellRect(index, 0, true);
    scrollRectToVisible(rect);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    final int viewColumn = getColumnModel().getColumnIndexAtX(e.getX());
    final int column = convertColumnIndexToModel(viewColumn);
    if (column == sortByColumn) {
      sortAscending = !sortAscending;
    } else {
      sortByColumn = column;
      sortAscending = false;
    }
    getTableHeader().repaint();
    tableModel.sort();
  }

  @Override
  public void mousePressed(MouseEvent e) {
  }

  @Override
  public void mouseReleased(MouseEvent e) {
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }

  // -------------------------- INNER CLASSES --------------------------

  private final class TableHeaderRenderer extends DefaultTableCellRenderer {
    private TableHeaderRenderer() {
      setHorizontalTextPosition(SwingConstants.LEFT);
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
        int row, int column) {
      if (table != null) {
        JTableHeader header = table.getTableHeader();
        if (header != null) {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());
          setText(' ' + table.getModel().getColumnName(column));
        }
      }

      if (sortByColumn == column) {
        setIcon(sortAscending ? Icons.ICON_UP_16.getIcon() : Icons.ICON_DOWN_16.getIcon());
      } else {
        setIcon(null);
      }
      return this;
    }
  }

  private final class SortableTableModel implements TableModel, Comparator<TableItem> {
    private final List<TableModelListener> listeners = new ArrayList<>();
    private final List<TableItem> tableItems = new ArrayList<>();
    private final Class<?>[] columnClasses;
    private final String[] columnNames;

    private SortableTableModel(String[] columnNames, Class<?>[] columnClasses) {
      this.columnNames = columnNames != null ? columnNames : new String[0];
      this.columnClasses = columnClasses != null ? columnClasses : new Class<?>[0];
    }

    private void addTableItem(TableItem item) {
      tableItems.add(item);
    }

    private TableItem getTableItemAt(int rowIndex) {
      return tableItems.get(rowIndex);
    }

    private void fireTableChangedEvent() {
      final TableModelEvent event = new TableModelEvent(this);
      for (TableModelListener l : listeners) {
        l.tableChanged(event);
      }
    }

    public void sort() {
      // Sanity check
      if (!tableItems.isEmpty()) {
        final Object item = tableItems.get(0).getObjectAt(sortByColumn);
        if (item != null && !getColumnClass(sortByColumn).isAssignableFrom(item.getClass())) {
          Logger.warn("Incompatible item type at column {}: expected {}, found {}",
              sortByColumn, getColumnClass(sortByColumn).getSimpleName(), item.getClass().getSimpleName());
        }
      }

      tableItems.sort(this);
      fireTableChangedEvent();
    }

    private void clear() {
      tableItems.clear();
      fireTableChangedEvent();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnClasses[columnIndex];
    }

    @Override
    public int getColumnCount() {
      return columnClasses.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return columnNames[columnIndex];
    }

    @Override
    public int getRowCount() {
      return tableItems.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return tableItems.get(rowIndex).getObjectAt(columnIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
      listeners.add(l);
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
      listeners.remove(l);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public int compare(TableItem o1, TableItem o2) {
      Integer res = null;
      final Object item1 = o1.getObjectAt(sortByColumn);
      final Object item2 = o2.getObjectAt(sortByColumn);

      if (item1 instanceof Comparable && item2 instanceof Comparable) {
        // best case: both items can be compared directly
        try {
          res = ((Comparable) item1).compareTo(item2);
        } catch (Exception e) {
          // ignored
        }
      }
      if (res == null) {
        // fall-back solution: compare by string
        final String s1 = Objects.toString(item1, "");
        final String s2 = Objects.toString(item2, "");

        // Extract numbers from strings and compare it as numbers
        final String[] arr1 = SPLIT_BY_NUMBER.split(s1);
        final String[] arr2 = SPLIT_BY_NUMBER.split(s2);
        res = ArrayUtil.compare(arr1, arr2, SORTER);
      }
      if (sortAscending) {
        res = -res;
      }
      return res;
    }
  }
}
