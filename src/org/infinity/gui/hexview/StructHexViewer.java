// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.hexview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import org.infinity.NearInfinity;
import org.infinity.datatype.Bitmap;
import org.infinity.datatype.ColorPicker;
import org.infinity.datatype.ColorValue;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.FloatNumber;
import org.infinity.datatype.HashBitmap;
import org.infinity.datatype.MultiNumber;
import org.infinity.datatype.ProRef;
import org.infinity.datatype.ResourceBitmap;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextBitmap;
import org.infinity.datatype.TextEdit;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.StatusBar;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.dlg.AbstractCode;
import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Misc;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

import tv.porst.jhexview.DataChangedEvent;
import tv.porst.jhexview.HexViewEvent;
import tv.porst.jhexview.IColormap;
import tv.porst.jhexview.IDataChangedListener;
import tv.porst.jhexview.IDataProvider;
import tv.porst.jhexview.IHexViewListener;
import tv.porst.jhexview.IMenuCreator;
import tv.porst.jhexview.JHexView;

/**
 * A hex viewer and editor component designed to be used as separate tab in resource viewers.
 *
 * Not implemented: proper support for AbstractAction (changes in referenced text blocks are ignored)
 */
public class StructHexViewer extends JPanel implements IHexViewListener, IDataChangedListener,
                                                 ActionListener, ChangeListener, Closeable
{
  private static final ButtonPanel.Control BUTTON_FIND      = ButtonPanel.Control.FIND_BUTTON;
  private static final ButtonPanel.Control BUTTON_FINDNEXT  = ButtonPanel.Control.CUSTOM_1;
  private static final ButtonPanel.Control BUTTON_EXPORT    = ButtonPanel.Control.EXPORT_BUTTON;
  private static final ButtonPanel.Control BUTTON_SAVE      = ButtonPanel.Control.SAVE;
  private static final ButtonPanel.Control BUTTON_REFRESH   = ButtonPanel.Control.CUSTOM_2;

  private static final String FMT_OFFSET = "%1$Xh (%1$d)";

  private final AbstractStruct struct;
  private final JHexView hexView;
  private final IMenuCreator menuCreator;
  private final IDataProvider dataProvider;
  private final IColormap colorMap;
  private final InfoPanel pInfo;
  private final ButtonPanel buttonPanel;

  private FindDataDialog findData;
  private JScrollPane spInfo;
  private boolean tabSelected;
  private int cachedSize;

  /** Returns a short description of the specified structure type. */
  public static String getTypeDesc(StructEntry type)
  {
    if (type instanceof AbstractStruct) {
      return "Nested structure";
    } else if (type instanceof AbstractCode) {
      return "Script code";
    } else if (type instanceof ColorPicker) {
      return "RGB Color";
    } else if (type instanceof ColorValue) {
      return "Palette index";
    } else if (type instanceof SectionCount) {
      return "Count of a structure type";
    } else if (type instanceof SectionOffset) {
      return "Start offset of a structure type";
    } else if (type instanceof Flag) {
      return "Flags/Bitfield";
    } else if (type instanceof ProRef) {
      return "Projectile";
    } else if (type instanceof ResourceRef) {
      return "Resource reference";
    } else if (type instanceof StringRef) {
      return "String reference";
    } else if (type instanceof DecNumber || type instanceof MultiNumber ||
               type instanceof FloatNumber) {
      return "Number";
    } else if (type instanceof Bitmap || type instanceof HashBitmap ||
               type instanceof ResourceBitmap) {
      return "Numeric type or identifier";
    } else if (type instanceof TextBitmap || type instanceof TextEdit ||
               type instanceof TextString) {
      return "Text field";
    } else if (type instanceof Unknown) {
      return "Unknown or unused data";
    } else {
      return "n/a";
    }
  }


  public StructHexViewer(AbstractStruct struct)
  {
    this(struct, null, null);
  }

  public StructHexViewer(AbstractStruct struct, IColormap colorMap)
  {
    this(struct, colorMap, null);
  }

  public StructHexViewer(AbstractStruct struct, IColormap colorMap, IDataProvider dataProvider)
  {
    super();

    if (struct == null) {
      throw new NullPointerException("struct is null");
    }
    this.struct = struct;
    this.hexView = new JHexView();
    this.dataProvider = (dataProvider == null) ? new StructuredDataProvider(this.struct) : dataProvider;
    this.dataProvider.addListener(this);
    this.colorMap = colorMap;
    this.menuCreator = new ResourceMenuCreator(hexView, this.struct);
    this.buttonPanel = new ButtonPanel();

    if (this.dataProvider instanceof StructuredDataProvider) {
      this.pInfo = new InfoPanel();
    } else {
      this.pInfo = null;
    }

    initGui();
  }

  //<editor-fold defaultstate="collapsed" desc="IHexViewListener">
  @Override
  public void stateChanged(HexViewEvent event)
  {
    if (event.getSource() instanceof JHexView &&
        event.getCause() == HexViewEvent.Cause.SelectionChanged &&
        getStruct().isRawTabSelected()) {
      JHexView hv = (JHexView)event.getSource();
      int offset = (int)hv.getCurrentOffset();

      // updating info panel
      updateInfoPanel(offset);

      // updating statusbar
      updateStatusBar(offset);
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="IDataChangedListener">
  @Override
  public void dataChanged(DataChangedEvent event)
  {
    getStruct().setStructChanged(true);
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="ActionListener">
  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == buttonPanel.getControlByType(BUTTON_FIND)) {
      if (getFindData().find()) {
        boolean b;
        String s = null;
        if (getFindData().getDataType() == FindDataDialog.Type.TEXT) {
          b = !getFindData().getText().isEmpty();
          s = getFindData().getText();
        } else {
          b = (getFindData().getBytes().length > 0);
          if (getFindData().getBytes().length > 0) {
            s = byteArrayToString(getFindData().getBytes());
          }
        }

        findPattern((int)getHexView().getCurrentOffset());

        // Setting up "Find next" button
        JComponent c = buttonPanel.getControlByType(BUTTON_FINDNEXT);
        c.setEnabled(b);
        if (s == null) {
          c.setToolTipText(null);
        } else if (s.length() <= 30) {
          c.setToolTipText(String.format("Find \"%s\"", s));
        } else {
          c.setToolTipText(String.format("Find \"%s...\"", s.substring(0, 30)));
        }
      }
      getHexView().requestFocusInWindow();
    } else if (event.getSource() == buttonPanel.getControlByType(BUTTON_FINDNEXT)) {
      findPattern((int)getHexView().getCurrentOffset());
      getHexView().requestFocusInWindow();
    } else if (event.getSource() == buttonPanel.getControlByType(BUTTON_EXPORT)) {
      ResourceFactory.exportResource(getStruct().getResourceEntry(), getTopLevelAncestor());
      getHexView().requestFocusInWindow();
    } else if (event.getSource() == buttonPanel.getControlByType(BUTTON_SAVE)) {
      // XXX: Ugly hack: mimicking ResourceFactory.saveResource()
      IDataProvider dataProvider = getHexView().getData();
      ResourceEntry entry = getStruct().getResourceEntry();
      Path outPath;
      if (entry instanceof BIFFResourceEntry) {
        Path overridePath = FileManager.query(Profile.getGameRoot(), Profile.getOverrideFolderName());
        if (!Files.isDirectory(overridePath)) {
          try {
            Files.createDirectory(overridePath);
          } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to create override folder.",
                                          "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return;
          }
        }
        outPath = FileManager.query(overridePath, entry.toString());
        ((BIFFResourceEntry)entry).setOverride(true);
      } else {
        outPath = entry.getActualPath();
      }
      if (Files.exists(outPath)) {
        outPath = outPath.toAbsolutePath();
        String options[] = {"Overwrite", "Cancel"};
        if (JOptionPane.showOptionDialog(this, outPath + " exists. Overwrite?", "Save resource",
                                         JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                                         null, options, options[0]) == 0) {
          if (BrowserMenuBar.getInstance().backupOnSave()) {
            try {
              Path bakPath = outPath.getParent().resolve(outPath.getFileName() + ".bak");
              if (Files.isRegularFile(bakPath)) {
                Files.delete(bakPath);
              }
              if (!Files.exists(bakPath)) {
                Files.move(outPath, bakPath);
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        } else {
          return;
        }
      }

      try {
        byte[] buffer = dataProvider.getData(0, dataProvider.getDataLength());
        try (OutputStream os = StreamUtils.getOutputStream(outPath, true)) {
          // make sure that nothing interferes with the writing process
          getHexView().setEnabled(false);
          StreamUtils.writeBytes(os, buffer);
        } finally {
          getHexView().setEnabled(true);
        }
        buffer = null;
        getStruct().setStructChanged(false);
        getHexView().clearModified();
        JOptionPane.showMessageDialog(this, "File saved to \"" + outPath.toAbsolutePath() + '\"',
                                      "Save complete", JOptionPane.INFORMATION_MESSAGE);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(this, "Error while saving " + getStruct().getResourceEntry().toString(),
                                      "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
        return;
      }
      getHexView().requestFocusInWindow();
    } else if (event.getSource() == buttonPanel.getControlByType(BUTTON_REFRESH)) {
      dataModified(true);
      getHexView().requestFocusInWindow();
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="ChangeListener">
  @Override
  public void stateChanged(ChangeEvent e)
  {
    if (e.getSource() instanceof JTabbedPane) {
      if (!tabSelected && getStruct().isRawTabSelected()) {
        // actions when entering Raw tab
        tabSelected = true;
        getHexView().requestFocusInWindow();
        updateStatusBar((int)getHexView().getCurrentOffset());
      } else if (tabSelected) {
        // actions when leaving Raw tab
        tabSelected = false;
        getHexView().clearModified();
        getHexView().resetUndo();
        updateStatusBar(-1);
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Closeable">
  @Override
  public void close() throws Exception
  {
    hexView.setVisible(false);
    hexView.dispose();

    if (dataProvider instanceof StructuredDataProvider) {
      ((StructuredDataProvider)dataProvider).close();
    } else if (dataProvider instanceof ResourceDataProvider) {
      ((ResourceDataProvider)dataProvider).clear();
    }

    if (colorMap instanceof BasicColorMap) {
      ((BasicColorMap)colorMap).close();
    }

    if (findData != null) {
      findData.dispose();
      findData = null;
    }
  }
  //</editor-fold>

  /** Returns the associated resource structure. */
  public AbstractStruct getStruct()
  {
    return struct;
  }

  /** Returns the HexView component. */
  public JHexView getHexView()
  {
    return hexView;
  }

  /** Notify HexViewer that data has been changed. Executes a forced reset. */
  public void dataModified()
  {
    dataModified(true);
  }

  /** Notify HexViewer that data has been changed. Specify whether to force a reset. */
  public void dataModified(boolean force)
  {
    if (force || cachedSize != getDataProvider().getDataLength()) {
      WindowBlocker.blockWindow(true);
      try {
        if (getDataProvider() instanceof StructuredDataProvider) {
          // notifying data provider that data has changed
          ((StructuredDataProvider)getDataProvider()).reset();
        }
        if (hexView.isColorMapEnabled()) {
          // notifying color map that data has changed
          if (getColorMap() instanceof BasicColorMap) {
            ((BasicColorMap)getColorMap()).reset();
          }
        }

        cachedSize = getDataProvider().getDataLength();
      } finally {
        WindowBlocker.blockWindow(false);
      }
    }
  }

  /** Initialize controls. */
  private void initGui()
  {
    setLayout(new BorderLayout());

    // configuring hexview
    GenericHexViewer.configureHexView(hexView, dataProvider.isEditable());

    hexView.setColormap(colorMap);
    hexView.setMenuCreator(menuCreator);
    hexView.addHexListener(this);
    hexView.setData(dataProvider);
    hexView.setDefinitionStatus(hexView.getData().getDataLength() > 0 ?
        JHexView.DefinitionStatus.DEFINED : JHexView.DefinitionStatus.UNDEFINED);

    cachedSize = getDataProvider().getDataLength();

    // Info panel only available for structured data
    if (pInfo != null) {
      spInfo = new JScrollPane(pInfo);
      spInfo.getVerticalScrollBar().setUnitIncrement(16);

      JSplitPane splitv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, hexView, spInfo);
      splitv.setDividerLocation(2 * NearInfinity.getInstance().getContentPane().getHeight() / 3);
      add(splitv, BorderLayout.CENTER);

      pInfo.setOffset(0);
    } else {
      add(hexView, BorderLayout.CENTER);
    }

    // setting up button panel
    JButton b = (JButton)buttonPanel.addControl(BUTTON_FIND);
    b.addActionListener(this);
    b = (JButton)buttonPanel.addControl(new JButton("Find next"), BUTTON_FINDNEXT);
    b.setEnabled(false);
    b.addActionListener(this);
    b = (JButton)buttonPanel.addControl(BUTTON_EXPORT);
    b.addActionListener(this);
    b = (JButton)buttonPanel.addControl(BUTTON_SAVE);
    b.addActionListener(this);
    b = (JButton)buttonPanel.addControl(new JButton("Refresh"), BUTTON_REFRESH);
    b.setIcon(Icons.getIcon(Icons.ICON_REFRESH_16));
    b.setToolTipText("Force a refresh of the displayed data");
    b.addActionListener(this);

    add(buttonPanel, BorderLayout.SOUTH);
  }

  private FindDataDialog getFindData()
  {
    if (findData == null) {
      Window w = null;
      if (getStruct().getViewer() != null) {
        w = SwingUtilities.getWindowAncestor(getStruct().getViewer());
      }
      if (w == null) {
        w = NearInfinity.getInstance();
      }
      findData = new FindDataDialog(w);
    }

    return findData;
  }

  /** Attempts to find the next match of the search string as defined in the FindData instance, starting at offset. */
  private void findPattern(int offset)
  {
    if (getFindData().getDataType() == FindDataDialog.Type.TEXT) {
      offset = getHexView().findAscii(offset, getFindData().getText(), getFindData().isCaseSensitive());
      if (offset >= 0) {
        getHexView().setCurrentOffset(offset);
        getHexView().setSelectionLength(getFindData().getText().length()*2);
      } else {
        JOptionPane.showMessageDialog(this, "No match found.", "Find", JOptionPane.INFORMATION_MESSAGE);
      }
    } else {
      if (getFindData().getBytes().length > 0) {
        offset = getHexView().findHex(offset, getFindData().getBytes());
        if (offset >= 0) {
          getHexView().setCurrentOffset(offset);
          getHexView().setSelectionLength(getFindData().getBytes().length*2);
        } else {
          JOptionPane.showMessageDialog(this, "No match found.", "Find", JOptionPane.INFORMATION_MESSAGE);
        }
      } else {
        JOptionPane.showMessageDialog(this, "Search string does not contain valid byte values",
                                      "Find", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private String byteArrayToString(byte[] buffer)
  {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    if (buffer != null) {
      for (int i = 0; i < buffer.length; i++) {
        sb.append(String.format("%02X", buffer[i] & 0xff));
        if (i+1 < buffer.length) {
          sb.append(", ");
        }
      }
    }
    sb.append(']');
    return sb.toString();
  }

  // Update statusbar
  private void updateStatusBar(int offset)
  {
    StatusBar sb = NearInfinity.getInstance().getStatusBar();
    if (offset >= 0) {
      sb.setCursorText(String.format(FMT_OFFSET, offset));
    } else {
      sb.setCursorText("");
    }
  }

  // Update info panel
  private void updateInfoPanel(int offset)
  {
    if (pInfo != null) {
      pInfo.setOffset(offset);
    }
  }

  private IDataProvider getDataProvider()
  {
    return dataProvider;
  }

  private IColormap getColorMap()
  {
    return colorMap;
  }

//-------------------------- INNER CLASSES --------------------------

  /** Panel component showing information about the currently selected data. */
  private final class InfoPanel extends JPanel
  {
    private final List<StructEntryTableModel> listModels = new ArrayList<StructEntryTableModel>();
    private final List<Component> listComponents = new ArrayList<Component>();

    private JPanel mainPanel;
    private int offset;

    public InfoPanel()
    {
      super();

      if (struct == null) {
        throw new NullPointerException("struct is null");
      }

      init();
    }

//    /** Returns current offset. */
//    public int getOffset()
//    {
//      return offset;
//    }

    /** Sets new offset and updates info panel. */
    public void setOffset(int offset)
    {
      if (offset != this.offset) {
        this.offset = offset;
        updatePanel(this.offset);
      }
    }

    /** Initialize controls. */
    private void init()
    {
      setLayout(new GridBagLayout());
      setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      offset = -1;

      mainPanel = new JPanel();
      mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
      GridBagConstraints gbc = new GridBagConstraints();
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      add(mainPanel, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
      add(new JPanel(), gbc);
    }

    /** Updates tables and table models based on the data at the specified offset. */
    private void updatePanel(int offset)
    {
      StructuredDataProvider data = (getDataProvider() instanceof StructuredDataProvider) ?
                                    (StructuredDataProvider)getDataProvider() : null;
      if (data != null) {
        // creating list of nested StructEntry objects
        StructEntry newEntry = data.getFieldAt(offset);
        final List<StructEntry> list;
        if (newEntry != null) {
          list = newEntry.getStructChain();
          if (!list.isEmpty() && list.get(0) == getStruct()) {
            list.remove(0);
          }
        } else {
          list = new ArrayList<StructEntry>();
        }

        // removing invalid models and controls
        int lastIdx = listModels.size() - 1;
        for ( ; lastIdx >= 0; lastIdx--) {
          StructEntry curEntry = listModels.get(lastIdx).getStruct();
          if (!list.contains(curEntry)) {
            listModels.remove(lastIdx);
            Component c = listComponents.remove(lastIdx);
            removeComponentFromPanel(c);
          } else {
            break;
          }
        }
        // lastIdx contains the highest index of remaining structures

        // adding updated models and tables to the lists
        for (int i = lastIdx + 1; i < list.size(); i++) {
          StructEntryTableModel model = new StructEntryTableModel(list.get(i));
          listModels.add(model);
          Component c = createInfoTable(model, listComponents.size() + 1);
          listComponents.add(c);
          addComponentToPanel(c);
        }
      } else {
        for (int i = listModels.size() - 1; i >= 0; i--) {
          listModels.remove(i);
          Component c = listComponents.remove(i);
          removeComponentFromPanel(c);
        }
      }

      // notifying panel of the changed layout
      revalidate();
      repaint();
    }

    /** Constructs and initializes a new table panel based on the specified model and level information. */
    private Component createInfoTable(TableModel model, int level)
    {
      final String[] suffix = {"th", "st", "nd", "rd", "th"};

      JPanel retVal = new JPanel(new GridBagLayout());

      // creating title
      JLabel l = new JLabel(String.format("%d%s level structure:",
                                          level, suffix[Math.max(0, Math.min(level, 4))]));
      l.setFont(l.getFont().deriveFont(Font.BOLD));
      GridBagConstraints gbc = new GridBagConstraints();
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      retVal.add(l, gbc);

      // creating table
      JTable table = new JTable(model);
      table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      table.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
      table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 1);
      table.setBorder(BorderFactory.createLineBorder(Color.GRAY));
      table.getTableHeader().setBorder(BorderFactory.createLineBorder(Color.GRAY));
      table.getTableHeader().setReorderingAllowed(false);
      table.getTableHeader().setResizingAllowed(true);
      table.setFocusable(false);
      table.setEnabled(false);

      final String maxString = String.format("%080d", 0);
      Font f = Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont());
      FontMetrics fm = table.getFontMetrics(f);
      Rectangle2D rect = f.getStringBounds(maxString, fm.getFontRenderContext());
      Dimension d = table.getPreferredSize();
      d.width = (int)rect.getWidth();
      table.setPreferredSize(d);

      gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
          GridBagConstraints.HORIZONTAL, new Insets(2, 0, 16, 0), 0, 0);
      retVal.add(table, gbc);

      return retVal;
    }

    /** Removes the specified component from the info panel. */
    private void removeComponentFromPanel(Component c)
    {
      if (c != null) {
        mainPanel.remove(c);
      }
    }

    /** Adds the specified component to the info panel. */
    private void addComponentToPanel(Component c)
    {
      if (c != null) {
        if (!isAncestorOf(c)) {
          mainPanel.add(c);
        }
      }
    }
  }


  /** Manages the representation of a single {@link StructEntry} instance. */
  private class StructEntryTableModel extends AbstractTableModel
  {
    private final String[] names = {"Name", "Start offset", "Length", "Structure type", "Value"};

    private final StructEntry entry;

    public StructEntryTableModel(StructEntry entry)
    {
      super();
      this.entry = entry;
    }

    //--------------------- Begin Class AbstractTableModel ---------------------

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
      return String.class;
    }

    @Override
    public int getRowCount()
    {
      return names.length;
    }

    @Override
    public int getColumnCount()
    {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
      if (columnIndex == 0) {
        if (rowIndex >= 0 && rowIndex < getRowCount()) {
          return names[rowIndex];
        }
      } else if (columnIndex == 1) {
        if (getStruct() != null) {
          switch (rowIndex) {
            case 0: // Name
              return getStruct().getName();
            case 1: // Start offset
              return String.format("%1$Xh (%1$d)", getStruct().getOffset());
            case 2: // Length
              return String.format("%d byte%s",
                                   getStruct().getSize(), (getStruct().getSize() != 1) ? "s" : "");
            case 3: // Structure type
              return getTypeDesc(getStruct());
            case 4: // Field value
            {
              String s = getStruct().toString();
              return (s.length() > 30) ? s.substring(0, 30) + "..." : s;
            }
          }
        }
      }
      return "";
    }

    //--------------------- End Class AbstractTableModel ---------------------

    /** Returns the associated StructEntry instance. */
    public StructEntry getStruct()
    {
      return entry;
    }
  }
}
