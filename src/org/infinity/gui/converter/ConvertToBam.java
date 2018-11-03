// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.IndexColorModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.NearInfinity;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.DataMenuItem;
import org.infinity.gui.FixedFocusTraversalPolicy;
import org.infinity.gui.OpenResourceDialog;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamV1Decoder;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.DxtEncoder;
import org.infinity.resource.graphics.GifSequenceReader;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.graphics.BamDecoder.BamControl;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamControl;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamCycleEntry;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamFrameEntry;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapEntry;
import org.infinity.util.IniMapSection;
import org.infinity.util.Misc;
import org.infinity.util.Pair;
import org.infinity.util.SimpleListModel;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

public class ConvertToBam extends ChildFrame
  implements ActionListener, PropertyChangeListener, FocusListener, ChangeListener,
             ListSelectionListener, MouseListener
{
  // Available TabPane indices
  static final int TAB_FRAMES   = 0;
  static final int TAB_CYCLES   = 1;
  static final int TAB_PREVIEW  = 2;
  static final int TAB_FILTERS  = 3;
  static final String[] TabNames = {"Frames", "Cycles", "Preview", "Post-processing"};

  // Available DXT compression types for BAM v2 export
  static final int COMPRESSION_AUTO = 0;
  static final int COMPRESSION_DXT1 = 1;
  static final int COMPRESSION_DXT5 = 2;
  static final String[] CompressionItems = {"Auto", "DXT1", "DXT5"};

  // Available BAM versions
  static final int VERSION_BAMV1 = 0;
  static final int VERSION_BAMV2 = 1;
  static final String[] BamVersionItems = {"Legacy (v1)", "PVRZ-based (v2)"};

  // Alpha component support for BAM v1
  static final int ALPHA_AUTO   = 0;
  static final int ALPHA_ALWAYS = 1;
  static final int ALPHA_NEVER  = 2;
  static final String[] UseAlphaItems = {"In EE only", "Always", "Never"};

  // Available playback modes for preview
  static final int MODE_CURRENT_CYCLE_ONCE    = 0;
  static final int MODE_CURRENT_CYCLE_LOOPED  = 1;
  static final int MODE_ALL_CYCLES_ONCE       = 2;
  static final int MODE_ALL_CYCLES_LOOPED     = 3;
  static final String[] PlaybackModeItems = {"Current cycle once", "Current cycle looped",
                                             "All cycles once", "All cycles looped"};

  // PseudoBamDecoder->setOption(): Full path to frame source
  static final String BAM_FRAME_OPTION_PATH         = "Path";
  // PseudoBamDecoder->setOption(): frame source index (usually 0, except for BAM sources)
  static final String BAM_FRAME_OPTION_SOURCE_INDEX = "FrameIndex";

  // Used as prefix if BAM source file is based on a biffed resource
  static final String BAM_FRAME_PATH_BIFF     = "BIFF:/";

  // Available lists of frame images
  static final int BAM_ORIGINAL = 0;    // the original unprocessed frames list
  static final int BAM_FINAL    = 1;    // final frames list including palette and/or post-processor

  private static Path currentPath;

  // Global BamDecoder instance for managing frames and cycles
  private final PseudoBamDecoder bamDecoder = new PseudoBamDecoder();
  // BamDecoder instance containing the final result of the current BAM structure
  private final PseudoBamDecoder bamDecoderFinal = new PseudoBamDecoder();
  // Frame image lists (use BAM_ORIGINAL/BAM_FINAL constants for access)
  private final List<List<PseudoBamFrameEntry>> listFrameEntries = new ArrayList<List<PseudoBamFrameEntry>>(2);
  // Frame entry used for preview in filter tab
  private final PseudoBamFrameEntry entryFilterPreview = new PseudoBamFrameEntry(null, 0, 0);
  // The palette dialog instance for BAM v1 export
  private final BamPaletteDialog paletteDialog = new BamPaletteDialog(this);

  private JTabbedPane tpMain, tpCyclesSection;
  private BamFramesListModel modelFrames;   // Frames == FramesAvail
  private BamCycleFramesListModel modelCurCycle;
  private BamCyclesListModel modelCycles;
  private SimpleListModel<BamFilterBase> modelFilters;
  private JList<PseudoBamFrameEntry> listFrames, listFramesAvail, listCurCycle;
  private JList<PseudoBamCycleEntry> listCycles;
  private JList<BamFilterBase> listFilters;
  private JMenuItem miFramesAddFiles, miFramesAddResources, miFramesAddFolder, miFramesImportFile,
                    miFramesImportResource, miFramesRemove, miFramesRemoveAll, miFramesDropUnused,
                    miSessionExport, miSessionImport;
  private JMenu miSessionHistory;
  private ButtonPopupMenu bpmFramesAdd, bpmFramesRemove, bpmSession;
  private JButton bOptions, bConvert, bCancel, bPalette, bVersionHelp, bCompressionHelp;
  private JButton bFramesUp, bFramesDown;
  private JButton bCyclesUp, bCyclesDown, bCyclesAdd, bCyclesRemove, bCyclesRemoveAll, bCurCycleUp,
                  bCurCycleDown, bCurCycleAdd, bCurCycleRemove;
  private JButton bMacroAssignFrames, bMacroRemoveFrames, bMacroSortFramesAsc, bMacroDuplicateCycle,
                  bMacroDuplicateFrames, bMacroReverseFrames, bMacroRemoveAll, bMacroReverseCycles;
  private JButton bPreviewCyclePrev, bPreviewCycleNext, bPreviewFramePrev, bPreviewFrameNext,
                  bPreviewPlay, bPreviewStop;
  private JButton bFiltersAdd, bFiltersRemove, bFiltersRemoveAll, bFiltersUp, bFiltersDown;
  private JTextField tfFrameWidth, tfFrameHeight, tfFrameCenterX, tfFrameCenterY;
  private JCheckBox cbCloseOnExit, cbCompressFrame, cbCompressBam, cbPreviewShowMarker, cbPreviewZoom,
                    cbFiltersShowMarker;
  private JPanel pFramesCurFrame, pCurrentCycle, pFramesOptionsVersion, pFiltersSettings;
  private JComboBox<String> cbPreviewMode;
  private JComboBox<BamFilterFactory.FilterInfo> cbFiltersAdd;
  private JComboBox<String> cbVersion, cbCompression;
  private JTextArea taFiltersDesc;
  private RenderCanvas rcFramesPreview, rcCyclesPreview, rcPreview, rcFiltersPreview;
  private JScrollPane scrollPreview, scrollFiltersPreview;
  private BufferedImage previewCanvas;
  private JLabel lPreviewCycle, lPreviewFrame;
  private JSpinner sPvrzIndex, sPreviewFps, sFiltersPreviewFrame;
  private Timer timer;
  private SwingWorker<List<String>, Void> workerConvert;
  private SwingWorker<Void, Void> workerProcess;
  private WindowBlocker blocker;
  private ProgressMonitor progress;
  private PseudoBamControl bamControlPreview;
  private boolean isPreviewModified, isPreviewPlaying;
  private double currentFps;
  private int pmCur, pmMax;
  private Path bamOutputFile;


  /** Validates numberString and modifies it to fit into the specified limits. */
  public static int numberValidator(String numberString, int min, int max, int defaultValue)
  {
    int retVal = defaultValue;
    if (numberString != null) {
      try {
        retVal = Integer.parseInt(numberString);
        if (min > max) { int tmp = min; min = max; max = tmp; }
        if (retVal < min) retVal = min; else if (retVal > max) retVal = max;
      } catch (NumberFormatException e) {
      }
    }
    return retVal;
  }

  /** Validates numberString and modifies it to fit into the specified limits. */
  public static double doubleValidator(String numberString, double min, double max, double defaultValue)
  {
    double retVal = defaultValue;
    if (numberString != null) {
      try {
        retVal = Double.parseDouble(numberString);
        if (min > max) { double tmp = min; min = max; max = tmp; }
        if (retVal < min) retVal = min; else if (retVal > max) retVal = max;
      } catch (NumberFormatException e) {
      }
    }
    return retVal;
  }

  /** Returns a list of supported input graphics file formats. */
  public static FileNameExtensionFilter[] getGraphicsFilters()
  {
    FileNameExtensionFilter[] filters = new FileNameExtensionFilter[] {
        new FileNameExtensionFilter("Graphics files (*.bam, *.bmp, *.gif, *.png, *,jpg, *.jpeg)",
                                    "bam", "bmp", "gif", "png", "jpg", "jpeg"),
        new FileNameExtensionFilter("BAM files (*.bam)", "bam"),
        new FileNameExtensionFilter("BMP files (*.bmp)", "bmp"),
        new FileNameExtensionFilter("GIF files (*.gif)", "gif"),
        new FileNameExtensionFilter("PNG files (*.png)", "png"),
        new FileNameExtensionFilter("JPEG files (*.jpg, *.jpeg)", "jpg", "jpeg")
    };
    return filters;
  }

  /** Returns a list of supported input file formats containing palettes. */
  public static FileNameExtensionFilter[] getPaletteFilters()
  {
    FileNameExtensionFilter[] filters = new FileNameExtensionFilter[] {
        new FileNameExtensionFilter("Palette from files (*.bam, *.bmp, *.png, *.act, *.pal)",
                                    "bam", "bmp", "png", "act", "pal"),
        new FileNameExtensionFilter("Palette from BAM files (*.bam)", "bam"),
        new FileNameExtensionFilter("Palette from BMP files (*.bmp)", "bmp"),
        new FileNameExtensionFilter("Palette from PNG files (*.png)", "png"),
        new FileNameExtensionFilter("Adobe Color Table files (*.act)", "act"),
        new FileNameExtensionFilter("Microsoft Palette files (*.pal)", "pal"),
    };
    return filters;
  }

  /** Returns a extension filter for BAM files. */
  public static FileNameExtensionFilter getBamFilter()
  {
    return new FileNameExtensionFilter("BAM files (*.bam)", "bam");
  }

  /** Returns a list of files that can be specified in an "Open file" dialog. */
  public static Path[] getOpenFileName(Component parent, String title, Path rootPath,
                                       boolean selectMultiple,
                                       FileNameExtensionFilter[] filters, int filterIndex)
  {
    if (rootPath == null) {
      rootPath = currentPath;
    }
    JFileChooser fc = new JFileChooser(rootPath.toFile());
    if (!Files.isDirectory(rootPath)) {
        fc.setSelectedFile(rootPath.toFile());
    }
    if (title == null) {
      title = selectMultiple ? "Select file(s)" : "Select file";
    }
    fc.setDialogTitle(title);
    fc.setDialogType(JFileChooser.OPEN_DIALOG);
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fc.setMultiSelectionEnabled(selectMultiple);
    if (filters != null) {
      for (final FileNameExtensionFilter filter: filters) {
        fc.addChoosableFileFilter(filter);
      }
      if (filterIndex >= 0 && filterIndex < filters.length) {
        fc.setFileFilter(filters[filterIndex]);
      }
    }
    if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      if (selectMultiple) {
        if (fc.getSelectedFiles().length > 0) {
          currentPath = fc.getSelectedFiles()[0].toPath().getParent();
        }
        File[] files = fc.getSelectedFiles();
        Path[] paths = new Path[files.length];
        for (int i = 0; i < files.length; i++) {
          paths[i] = files[i].toPath();
        }
        return paths;
      } else {
        currentPath = fc.getSelectedFile().toPath().getParent();
        return new Path[]{fc.getSelectedFile().toPath()};
      }
    } else {
      return null;
    }
  }

  /** Returns a path name from a "Select path" dialog. */
  public static Path getOpenPathName(Component parent, String title, Path rootPath)
  {
    if (rootPath == null) {
      rootPath = currentPath;
    }
    JFileChooser fc = new JFileChooser(rootPath.toFile());
    if (title == null) {
      title = "Select folder";
    }
    fc.setDialogTitle(title);
    fc.setDialogType(JFileChooser.OPEN_DIALOG);
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      currentPath = fc.getSelectedFile().toPath();
      return currentPath;
    } else {
      return null;
    }
  }

  /** Returns a filename that can be specified in a "Save file" dialog */
  public static Path getSaveFileName(Component parent, String title, Path rootPath,
                                     FileNameExtensionFilter[] filters, int filterIndex)
  {
    if (rootPath == null) {
      rootPath = currentPath;
    }
    JFileChooser fc = new JFileChooser(rootPath.toFile());
    if (!Files.isDirectory(rootPath)) {
        fc.setSelectedFile(rootPath.toFile());
    }
    if (title == null) {
      title = "Specify filename";
    }
    fc.setDialogTitle(title);
    fc.setDialogType(JFileChooser.SAVE_DIALOG);
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    if (filters != null) {
      for (final FileNameExtensionFilter filter: filters) {
        fc.addChoosableFileFilter(filter);
      }
      if (filterIndex >= 0 && filterIndex < filters.length) {
        fc.setFileFilter(filters[filterIndex]);
      }
    }
    if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
      currentPath = fc.getSelectedFile().toPath().getParent();
      return fc.getSelectedFile().toPath();
    } else {
      return null;
    }
  }

  public ConvertToBam()
  {
    super("Convert image sequence to BAM", true);
    init();
  }

  /**
   * Returns the BAM decoder instance that is used internally to manage the BAM structure.
   * @param bamType Either one of {@link #BAM_ORIGINAL} or {@link #BAM_FINAL}.
   */
  public PseudoBamDecoder getBamDecoder(int bamType)
  {
    return (bamType == BAM_FINAL) ? bamDecoderFinal : bamDecoder;
  }

  /** Returns the BAM decoder instance that is used to display the BAM in the preview tab. */
  public PseudoBamDecoder getPreviewBamDecoder()
  {
    return bamDecoderFinal;
  }

  /** Returns the associated BAM v1 palette dialog. */
  public BamPaletteDialog getPaletteDialog()
  {
    return paletteDialog;
  }

  /** Convenience method: Returns true if BAM v1 has been selected. */
  public boolean isBamV1Selected()
  {
    return (cbVersion.getSelectedIndex() == VERSION_BAMV1);
  }

  /** Returns the currently selected output BAM version. */
  public int getBamVersion()
  {
    return cbVersion.getSelectedIndex();
  }

  /** Returns the BAM output file. */
  public Path getBamOutput()
  {
    return bamOutputFile;
  }

  /** Predefines BAM output file or path. */
  public void setBamOutput(Path path)
  {
    bamOutputFile = path;
  }

  /** Returns whether BAM v1 output is compressed. */
  public boolean isBamV1Compressed()
  {
    return cbCompressBam.isSelected();
  }

  /** Returns the threshold used to determine transparent colors. Range: [0, 255]. */
  public static int getTransparencyThreshold()
  {
    return (255*BamOptionsDialog.getTransparencyThreshold()) / 100;
  }

  /** Returns whether alpha channel support is enabled. */
  public static boolean getUseAlpha()
  {
    return (BamOptionsDialog.getUseAlpha() == ALPHA_ALWAYS) ||
           (Profile.isEnhancedEdition() && BamOptionsDialog.getUseAlpha() == ALPHA_AUTO);
  }

  /** Returns the start index for PVRZ files used for BAM v2 output. */
  public int getPvrzIndex()
  {
    return ((Integer)sPvrzIndex.getValue()).intValue();
  }

  /** Returns the selected DXT type for BAM v2. */
  public DxtEncoder.DxtType getDxtType()
  {
    switch (cbCompression.getSelectedIndex()) {
      case COMPRESSION_DXT1:  return DxtEncoder.DxtType.DXT1;
      case COMPRESSION_DXT5:  return DxtEncoder.DxtType.DXT5;
      default: return getAutoDxtType();
    }
  }


//--------------------- Begin Class ChildFrame ---------------------

  @Override
  protected boolean windowClosing(boolean forced) throws Exception
  {
    BamOptionsDialog.saveRecentSessions();
    if (forced || confirmCloseDialog()) {
      clear();
      return true;
    } else {
      return false;
    }
  }

//--------------------- End Class ChildFrame ---------------------


//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == timer) {
      if (!previewAdvanceAnimation()) {
        previewStop();
      }
    } else if (event.getSource() == bOptions) {
      new BamOptionsDialog(this);
    } else if (event.getSource() == bConvert) {
      if (workerConvert == null) {
        final String msg = "BAM output file already exists. Overwrite?";
        Path file = null;
        do {
          file = setBamOutput();
          if (file != null) {
            if (!Files.exists(file) ||
                JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Question",
                                                                        JOptionPane.YES_NO_OPTION,
                                                                        JOptionPane.QUESTION_MESSAGE)) {
              file = null;
              workerConvert = new SwingWorker<List<String>, Void>() {
                @Override
                protected List<String> doInBackground() throws Exception {
                  return convert();
                }
              };
              initProgressMonitor(this, "Converting BAM...", " ", 3, 0, 0);
              workerConvert.addPropertyChangeListener(this);
              blocker = new WindowBlocker(this);
              blocker.setBlocked(true);
              workerConvert.execute();
            }
            file = null;
          }
        } while (file != null);
      }
    } else if (event.getSource() == bCancel) {
      hideWindow(false);
    } else if (event.getSource() == bFramesUp) {
      framesMoveUp();
    } else if (event.getSource() == bFramesDown) {
      framesMoveDown();
    } else if (event.getSource() == miFramesAddFiles) {
      framesAddFiles();
    } else if (event.getSource() == miFramesAddResources) {
      framesAddResources();
    } else if (event.getSource() == miFramesImportFile) {
      framesImportBamFile();
    } else if (event.getSource() == miFramesImportResource) {
      framesImportBamResource();
    } else if (event.getSource() == miFramesAddFolder) {
      framesAddFolder();
    } else if (event.getSource() == miFramesRemove) {
      framesRemove();
    } else if (event.getSource() == miFramesRemoveAll) {
      if (!modelFrames.isEmpty()) {
        final String msg = "Do you really want to remove all frames?";
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Question",
                                                                    JOptionPane.YES_NO_OPTION,
                                                                    JOptionPane.QUESTION_MESSAGE)) {
          framesRemoveAll();
        }
      }
    } else if (event.getSource() == miFramesDropUnused) {
      int count = framesGetUnusedFramesCount();
      if (count > 0) {
        String msg;
        if (count == 1) {
          msg = "1 unused frame can be dropped. Do you want to continue?";
        } else {
          msg = String.format("%d unused frames can be dropped. Do you want to continue?", count);
        }
        int retVal = JOptionPane.showConfirmDialog(this, msg, "Question", JOptionPane.YES_NO_OPTION,
                                                   JOptionPane.QUESTION_MESSAGE);
        if (retVal == JOptionPane.YES_OPTION) {
          framesDropUnusedFrames();
        }
      }
    } else if (event.getSource() == miSessionExport) {
      Exporter exporter = new Exporter(this);
      try {
        exporter.exportData(false);
      } finally {
        exporter.close();
      }
    } else if (event.getSource() == miSessionImport) {
      Exporter importer = new Exporter(this);
      try {
        if (importer.importData(false)) {
          if (importer.isFramesSelected() || importer.isCenterSelected()) {
            updateFramesList();
          }
          if (importer.isCyclesSelected()) {
            updateCyclesList();
          }
          if (importer.isFiltersSelected()) {
            updateFilterList();
          }
        }
      } finally {
        importer.close();
      }
    } else if (event.getSource() == cbCompressFrame) {
      framesUpdateCompressFrame();
    } else if (event.getSource() == cbCompressBam) {
      updateCompressBam();
    } else if (event.getSource() == bPalette) {
      paletteDialog.open();
      outputSetModified(true);
    } else if (event.getSource() == bCompressionHelp) {
      showCompressionHelp();
    } else if (event.getSource() == cbVersion) {
      setBamVersion(cbVersion.getSelectedIndex());
    } else if (event.getSource() == bVersionHelp) {
      showVersionHelp();
    } else if (event.getSource() == bCyclesAdd) {
      cyclesAdd();
    } else if (event.getSource() == bCyclesRemove) {
      cyclesRemove();
    } else if (event.getSource() == bCyclesRemoveAll) {
      if (!modelCycles.isEmpty()) {
        final String msg = "Do you really want to remove all cycles?";
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Question",
                                                                    JOptionPane.YES_NO_OPTION,
                                                                    JOptionPane.QUESTION_MESSAGE)) {
          cyclesRemoveAll();
        }
      }
    } else if (event.getSource() == bCyclesUp) {
      cyclesMoveUp();
    } else if (event.getSource() == bCyclesDown) {
      cyclesMoveDown();
    } else if (event.getSource() == bMacroAssignFrames) {
      macroAssignFrames();
    } else if (event.getSource() == bMacroDuplicateCycle) {
      macroDuplicateCycle();
    } else if (event.getSource() == bMacroDuplicateFrames) {
      macroDuplicateFrames();
    } else if (event.getSource() == bMacroRemoveAll) {
      macroRemoveAllFramesGlobal();
    } else if (event.getSource() == bMacroRemoveFrames) {
      macroRemoveAllFrames();
    } else if (event.getSource() == bMacroReverseCycles) {
      macroReverseCyclesOrder();
    } else if (event.getSource() == bMacroReverseFrames) {
      macroReverseFramesOrder();
    } else if (event.getSource() == bMacroSortFramesAsc) {
      macroSortFrames();
    } else if (event.getSource() == bCurCycleAdd) {
      currentCycleAdd();
    } else if (event.getSource() == bCurCycleRemove) {
      currentCycleRemove();
    } else if (event.getSource() == bCurCycleUp) {
      currentCycleMoveUp();
    } else if (event.getSource() == bCurCycleDown) {
      currentCycleMoveDown();
    } else if (event.getSource() == bPreviewCyclePrev) {
      previewCycleDown();
    } else if (event.getSource() == bPreviewCycleNext) {
      previewCycleUp();
    } else if (event.getSource() == bPreviewFramePrev) {
      previewFrameDown();
    } else if (event.getSource() == bPreviewFrameNext) {
      previewFrameUp();
    } else if (event.getSource() == bPreviewPlay) {
      previewPlay();
    } else if (event.getSource() == bPreviewStop) {
      previewStop();
    } else if (event.getSource() == cbPreviewMode) {
      previewSetMode(cbPreviewMode.getSelectedIndex());
    } else if (event.getSource() == cbPreviewZoom) {
      previewSetZoom(cbPreviewZoom.isSelected());
    } else if (event.getSource() == cbPreviewShowMarker) {
      previewSetMarkerVisible(cbPreviewShowMarker.isSelected());
    } else if (event.getSource() == bFiltersAdd) {
      filterAdd();
    } else if (event.getSource() == bFiltersRemove) {
      filterRemove();
    } else if (event.getSource() == bFiltersRemoveAll) {
      if (!modelFilters.isEmpty()) {
        final String msg = "Do you really want to remove all filters?";
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Question",
                                                                    JOptionPane.YES_NO_OPTION,
                                                                    JOptionPane.QUESTION_MESSAGE)) {
          filterRemoveAll();
        }
      }
    } else if (event.getSource() == bFiltersUp) {
      filterMoveUp();
    } else if (event.getSource() == bFiltersDown) {
      filterMoveDown();
    } else if (event.getSource() == cbFiltersShowMarker) {
      filterSetPreviewFrame(filterGetPreviewFrameIndex(), false);
    } else if (event.getSource() instanceof DataMenuItem) {
      DataMenuItem dmi = (DataMenuItem)event.getSource();
      if (dmi.getData() instanceof Path) {
        Path path = (Path)dmi.getData();
        Exporter importer = new Exporter(this);
        try {
          if (importer.importData(path, false)) {
            if (importer.isFramesSelected() || importer.isCenterSelected()) {
              updateFramesList();
            }
            if (importer.isCyclesSelected()) {
              updateCyclesList();
            }
            if (importer.isFiltersSelected()) {
              updateFilterList();
            }
          }
        } finally {
          importer.close();
        }
      }
    }
  }

//--------------------- End Interface ActionListener ---------------------

//--------------------- Begin Interface PropertyChangeListener ---------------------

  @Override
  public void propertyChange(PropertyChangeEvent event)
  {
    if (event.getSource() == workerConvert) {
      if ("state".equals(event.getPropertyName()) &&
          SwingWorker.StateValue.DONE == event.getNewValue()) {

        if (blocker != null) {
          blocker.setBlocked(false);
          blocker = null;
        }

        if (isProgressMonitorActive()) {
          releaseProgressMonitor();
        }

        List<String> result = null;
        try {
          result = workerConvert.get();
        } catch (Exception e) {
          e.printStackTrace();
        }
        workerConvert = null;

        boolean isError = false;
        String s = null;
        if (result != null && !result.isEmpty()) {
          if (result.get(0) != null) {
            s = result.get(0);
          } else if (result.size() > 1 && result.get(1) != null) {
            s = result.get(1);
            isError = true;
          }
        }

        if (s != null) {
          if (isError) {
            JOptionPane.showMessageDialog(this, s, "Error", JOptionPane.ERROR_MESSAGE);
          } else {
            JOptionPane.showMessageDialog(this, s, "Information", JOptionPane.INFORMATION_MESSAGE);
            if (cbCloseOnExit.isSelected()) {
              hideWindow(true);
            } else if (BamOptionsDialog.getAutoClear()) {
              clear();
            }
          }
        } else {
          JOptionPane.showMessageDialog(this, "Unexpected error!", "Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    } else if (event.getSource() == workerProcess) {
      if ("state".equals(event.getPropertyName()) &&
          SwingWorker.StateValue.DONE == event.getNewValue()) {

        if (blocker != null) {
          blocker.setBlocked(false);
          blocker = null;
        }

        if (isProgressMonitorActive()) {
          releaseProgressMonitor();
        }

        workerProcess = null;
      }
    }
  }

//--------------------- End Interface PropertyChangeListener ---------------------

//--------------------- Begin Interface FocusListener ---------------------

  @Override
  public void focusGained(FocusEvent event)
  {
    if (event.getSource() == tfFrameCenterX || event.getSource() == tfFrameCenterY) {
      ((JTextField)event.getSource()).selectAll();
    }
  }

  @Override
  public void focusLost(FocusEvent event)
  {
    if (event.getSource() == tfFrameCenterX || event.getSource() == tfFrameCenterY) {
      framesValidateCenterValue((JTextField)event.getSource());
    }
  }

//--------------------- End Interface FocusListener ---------------------

//--------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent event)
  {
    if (event.getSource() == tpMain) {
      bamControlPreview = null;
      if (tpMain.getSelectedIndex() == TAB_PREVIEW) {
        if (workerProcess == null) {
          workerProcess = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
              previewValidate();
              return null;
            }
          };
          initProgressMonitor(this, "Preparing BAM...", null, 1, 0, 0);
          workerProcess.addPropertyChangeListener(this);
          blocker = new WindowBlocker(this);
          blocker.setBlocked(true);
          workerProcess.execute();
        }
      } else {
        previewStop();
        if (tpMain.getSelectedIndex() == TAB_FILTERS) {
          WindowBlocker.blockWindow(this, true);
          try {
            filterUpdateControls();
            filterUpdatePreviewFrameIndex();
            filterSetPreviewFrame(filterGetPreviewFrameIndex(), true);
          } finally {
            WindowBlocker.blockWindow(this, false);
          }
        }
      }
    } else if (event.getSource() == sPreviewFps) {
      double v = (Double)sPreviewFps.getValue();
      previewSetFrameRate(v);
    } else if (event.getSource() == sFiltersPreviewFrame) {
      filterSetPreviewFrame(filterGetPreviewFrameIndex(), true);
    } else if (event.getSource() instanceof BamFilterBase) {
      outputSetModified(true);
      filterSetPreviewFrame(filterGetPreviewFrameIndex(), false);
    }
  }

//--------------------- End Interface ChangeListener ---------------------

//--------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent event)
  {
    if (event.getSource() == listFrames) {
      int[] indices = listFrames.getSelectedIndices();
      updateFramesList();
      updateFrameInfo(indices);
      updateQuickPreview(rcFramesPreview, indices, true);
    } else if (event.getSource() == listCycles) {
      updateCyclesList();
    } else if (event.getSource() == listCurCycle) {
      updateCurrentCycle();
    } else if (event.getSource() == listFramesAvail) {
      int[] indices = listFramesAvail.getSelectedIndices();
      updateCurrentCycle();
      updateQuickPreview(rcCyclesPreview, indices, false);
    } else if (event.getSource() == listFilters) {
      updateFilterList();
    }
  }

//--------------------- End Interface ListSelectionListener ---------------------

//--------------------- Begin Interface MouseListener ---------------------

  @Override
  public void mouseClicked(MouseEvent event)
  {
    if (event.getSource() == listFramesAvail && (event.getClickCount() & 1) == 0) {
      // double click on list element
      int idx = listFramesAvail.getSelectedIndex();
      if (idx >= 0) {
        Rectangle rect = listFramesAvail.getCellBounds(idx, idx);
        if (rect != null && rect.contains(event.getX(), event.getY())) {
          currentCycleAdd();
        }
      }
    } else if (event.getSource() == listCurCycle && (event.getClickCount() & 1) == 0) {
      // double click on list element
      int idx = listCurCycle.getSelectedIndex();
      if (idx >= 0) {
        Rectangle rect = listCurCycle.getCellBounds(idx, idx);
        if (rect != null && rect.contains(event.getX(), event.getY())) {
          currentCycleRemove();
        }
      }
    }
  }

  @Override
  public void mousePressed(MouseEvent event)
  {
  }

  @Override
  public void mouseReleased(MouseEvent event)
  {
  }

  @Override
  public void mouseEntered(MouseEvent event)
  {
  }

  @Override
  public void mouseExited(MouseEvent event)
  {
  }

//--------------------- End Interface MouseListener ---------------------


  private void init()
  {
    setIconImage(Icons.getImage(Icons.ICON_APPLICATION_16));
    BamOptionsDialog.loadSettings(false);

    if (BamOptionsDialog.getPath().isEmpty()) {
      currentPath = Profile.getGameRoot();
    } else {
      currentPath = FileManager.resolve(BamOptionsDialog.getPath());
    }

    // initializing frame image lists
    listFrameEntries.add(bamDecoder.getFramesList());         // original frames list
    listFrameEntries.add(bamDecoderFinal.getFramesList());    // processed frames list
    bamDecoderFinal.setCyclesList(bamDecoder.getCyclesList());  // shared cycles list

    JPanel pFrames = createFramesTab();
    JPanel pCycles = createCyclesTab();
    JPanel pPreview = createPreviewTab();
    JPanel pFilters = createFiltersTab();

    // setting up tabbed pane
    tpMain = new JTabbedPane(JTabbedPane.TOP);
    tpMain.addTab(TabNames[TAB_FRAMES], pFrames);
    tpMain.setMnemonicAt(TAB_FRAMES, KeyEvent.VK_F);
    tpMain.addTab(TabNames[TAB_CYCLES], pCycles);
    tpMain.setMnemonicAt(TAB_CYCLES, KeyEvent.VK_C);
    tpMain.addTab(TabNames[TAB_PREVIEW], pPreview);
    tpMain.setMnemonicAt(TAB_PREVIEW, KeyEvent.VK_P);
    tpMain.addTab(TabNames[TAB_FILTERS], pFilters);
    tpMain.setMnemonicAt(TAB_FILTERS, KeyEvent.VK_O);
    tpMain.addChangeListener(this);

    // setting up bottom button bar
    GridBagConstraints c = new GridBagConstraints();
    initSessionEntries();
    miSessionExport = new JMenuItem("Export session...");
    miSessionExport.addActionListener(this);
    miSessionImport = new JMenuItem("Import session...");
    miSessionImport.addActionListener(this);
    bpmSession = new ButtonPopupMenu("BAM session");
    bpmSession.addItem(miSessionHistory);
    bpmSession.getPopupMenu().addSeparator();
    bpmSession.addItem(miSessionExport);
    bpmSession.addItem(miSessionImport);
    bpmSession.setToolTipText("Export or import BAM frame, cycle or filter definitions.");
    bpmSession.setIcon(Icons.getIcon(Icons.ICON_ARROW_UP_15));
    bpmSession.setIconTextGap(8);
    Insets i = bpmSession.getInsets();
    bpmSession.setMargin(new Insets(i.top + 1, i.left, i.bottom + 1, i.right));
    bOptions = new JButton("Options...");
    bOptions.addActionListener(this);
    i = bOptions.getInsets();
    bOptions.setMargin(new Insets(i.top + 1, i.left, i.bottom + 1, i.right));
    cbCloseOnExit = new JCheckBox("Close dialog after conversion", BamOptionsDialog.getCloseOnExit());
    bamOutputFile = null;
    bConvert = new JButton("Save output file...");
    bConvert.addActionListener(this);
    i = bConvert.getInsets();
    bConvert.setMargin(new Insets(i.top + 1, i.left, i.bottom + 1, i.right));
    bCancel = new JButton("Cancel");
    bCancel.addActionListener(this);
    i = bCancel.getInsets();
    bCancel.setMargin(new Insets(i.top + 1, i.left, i.bottom + 1, i.right));
    JPanel pButtons = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(bpmSession, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 6, 0, 0), 0, 0);
    pButtons.add(bOptions, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pButtons.add(cbCloseOnExit, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(bConvert, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pButtons.add(bCancel, c);

    // putting all sections together
    setLayout(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.BOTH, new Insets(8, 8, 0, 8), 0, 0);
    add(tpMain, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(8, 8, 8, 8), 0, 0);
    add(pButtons, c);

    // finalizing dialog initialization
    pack();
    setMinimumSize(getPreferredSize());
    setLocationRelativeTo(getParent());
    updateFramesList();
    updateFrameInfo(listFrames.getSelectedIndices());
    updateQuickPreview(rcFramesPreview, listFrames.getSelectedIndices(), true);
    updateQuickPreview(rcCyclesPreview, listFramesAvail.getSelectedIndices(), false);
    initCurrentCycle(listCycles.getSelectedIndex());
    updateStatus();
    setVisible(true);
  }

  /** Creating panel "Frames". */
  private JPanel createFramesTab()
  {
    GridBagConstraints c = new GridBagConstraints();

    // creating "Frames List"
    JPanel pFramesListArrows = new JPanel(new GridBagLayout());
    bFramesUp = new JButton(Icons.getIcon(Icons.ICON_UP_16));
    bFramesUp.setMargin(new Insets(bFramesUp.getInsets().top, 2, bFramesUp.getInsets().bottom, 2));
    bFramesUp.addActionListener(this);
    bFramesDown = new JButton(Icons.getIcon(Icons.ICON_DOWN_16));
    bFramesDown.setMargin(new Insets(bFramesDown.getInsets().top, 2, bFramesDown.getInsets().bottom, 2));
    bFramesDown.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_END,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 16);
    pFramesListArrows.add(bFramesUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0, 16);
    pFramesListArrows.add(bFramesDown, c);

    JPanel pFramesAdd = new JPanel(new GridBagLayout());
    miFramesAddFiles = new JMenuItem("Add file(s)...");
    miFramesAddFiles.setToolTipText("Add only frames from an external graphics file. Cycle definitions are ignored.");
    miFramesAddFiles.addActionListener(this);
    miFramesAddResources = new JMenuItem("Add resource(s)...");
    miFramesAddResources.setToolTipText("Add only frames from an internal graphics resource. Cycle definitions are ignored.");
    miFramesAddResources.addActionListener(this);
    miFramesAddFolder = new JMenuItem("Add folder...");
    miFramesAddFolder.addActionListener(this);
    miFramesImportFile = new JMenuItem("Import BAM file...");
    miFramesImportFile.setToolTipText("Import both frame and cycle definitions from an external BAM file.");
    miFramesImportFile.addActionListener(this);
    miFramesImportResource = new JMenuItem("Import BAM resource...");
    miFramesImportResource.setToolTipText("Import both frame and cycle definitions from an internal BAM resource.");
    miFramesImportResource.addActionListener(this);
    bpmFramesAdd = new ButtonPopupMenu("Add...", new JMenuItem[]{miFramesAddFiles, miFramesAddResources,
                                                                 miFramesAddFolder,
                                                                 miFramesImportFile, miFramesImportResource},
                                       false, ButtonPopupMenu.Align.TOP);
    bpmFramesAdd.setIcon(Icons.getIcon(Icons.ICON_ARROW_UP_15));
    bpmFramesAdd.setIconTextGap(8);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pFramesAdd.add(bpmFramesAdd, c);

    JPanel pFramesRemove = new JPanel(new GridBagLayout());
    miFramesRemove = new JMenuItem("Remove");
    miFramesRemove.addActionListener(this);
    miFramesRemoveAll = new JMenuItem("Remove all");
    miFramesRemoveAll.addActionListener(this);
    miFramesDropUnused = new JMenuItem("Drop unused frames");
    miFramesDropUnused.addActionListener(this);
    miFramesDropUnused.setToolTipText("Remove frames that are not used in any cycle definitions.");
    bpmFramesRemove = new ButtonPopupMenu("Remove...", new JMenuItem[]{miFramesRemove, miFramesRemoveAll,
                                                                       miFramesDropUnused});
    bpmFramesRemove.setIcon(Icons.getIcon(Icons.ICON_ARROW_UP_15));
    bpmFramesRemove.setIconTextGap(8);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pFramesRemove.add(bpmFramesRemove, c);

    JPanel pFramesListButtons = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pFramesListButtons.add(pFramesAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pFramesListButtons.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pFramesListButtons.add(pFramesRemove, c);

    JPanel pFramesList = new JPanel(new GridBagLayout());
    JLabel lFramesTitle = new JLabel("Frames:");
    modelFrames = new BamFramesListModel(this);
    listFrames = new JList<>(modelFrames);
    listFrames.setCellRenderer(new IndexedCellRenderer());
    listFrames.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    listFrames.addListSelectionListener(this);
    JScrollPane scroll = new JScrollPane(listFrames);
    c = ViewerUtil.setGBC(c, 0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0);
    pFramesList.add(lFramesTitle, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    pFramesList.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.VERTICAL, new Insets(0, 8, 0, 0), 0, 0);
    pFramesList.add(pFramesListArrows, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    pFramesList.add(pFramesListButtons, c);

    // creating "Current Frame" section
    pFramesCurFrame = new JPanel(new GridBagLayout());
    pFramesCurFrame.setBorder(BorderFactory.createTitledBorder("No frame selected"));
    JLabel lFramesWidth = new JLabel("Width:");
    JLabel lFramesHeight = new JLabel("Height:");
    JLabel lFramesCenterX = new JLabel("Center X:");
    JLabel lFramesCenterY = new JLabel("Center Y:");
    tfFrameWidth = new JTextField("0", 6);
    tfFrameWidth.setEditable(false);
    tfFrameHeight = new JTextField("0", 6);
    tfFrameHeight.setEditable(false);
    String tip = "Tip: You can prefix the entered number by '++' for adding to or '- -' " +
                 "for subtracting from the current center coordinate.";
    tfFrameCenterX = new JTextField("0", 6);
    tfFrameCenterX.setToolTipText(tip);
    tfFrameCenterX.addFocusListener(this);
    tfFrameCenterY = new JTextField("0", 6);
    tfFrameCenterY.setToolTipText(tip);
    tfFrameCenterY.addFocusListener(this);
    cbCompressFrame = new JCheckBox("Compress frame");
    cbCompressFrame.setToolTipText("Selecting this option activates RLE compression for the current frame(s).");
    cbCompressFrame.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesWidth, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(tfFrameWidth, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 16, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesCenterX, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 8, 0, 4), 0, 0);
    pFramesCurFrame.add(tfFrameCenterX, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesHeight, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(tfFrameHeight, c);
    c = ViewerUtil.setGBC(c, 2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 16, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesCenterY, c);
    c = ViewerUtil.setGBC(c, 3, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 8, 0, 4), 0, 0);
    pFramesCurFrame.add(tfFrameCenterY, c);
    c = ViewerUtil.setGBC(c, 0, 2, 4, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 4, 4, 4), 0, 0);
    pFramesCurFrame.add(cbCompressFrame, c);
    Component[] orderList = new Component[]{tfFrameWidth, tfFrameHeight, tfFrameCenterX, tfFrameCenterY};
    pFramesCurFrame.setFocusTraversalPolicy(new FixedFocusTraversalPolicy(orderList));
    pFramesCurFrame.setFocusTraversalPolicyProvider(true);

    // creating "Quick View" section
    JPanel pFramesQuickView = new JPanel(new GridBagLayout());
    pFramesQuickView.setBorder(BorderFactory.createTitledBorder("Quick Preview "));
    rcFramesPreview = new RenderCanvas(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    pFramesQuickView.add(rcFramesPreview, c);
    pFramesQuickView.setMinimumSize(pFramesQuickView.getPreferredSize());

    // creating "Import" section
    JPanel pFramesImport = new JPanel(new GridBagLayout());
    pFramesImport.setBorder(BorderFactory.createTitledBorder("Import "));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 2, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 4, 16), 0, 0);
    pFramesImport.add(pFramesList, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 4), 0, 0);
    pFramesImport.add(pFramesCurFrame, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(8, 0, 8, 4), 0, 0);
    pFramesImport.add(pFramesQuickView, c);

    // creating "Export options" sections
    JPanel pFramesOptionsVersionV1 = new JPanel(new GridBagLayout());
    cbCompressBam = new JCheckBox("Compress BAM", BamOptionsDialog.getCompressBam());
    cbCompressBam.setToolTipText("Create a zlib compressed BAM file (BAMC)");
    cbCompressBam.addActionListener(this);
    bPalette = new JButton("Palette...");
    bPalette.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 0, 0);
    pFramesOptionsVersionV1.add(bPalette, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 0, 0);
    pFramesOptionsVersionV1.add(cbCompressBam, c);

    JPanel pFramesOptionsVersionV2 = new JPanel(new GridBagLayout());
    JLabel lPvrzIndex = new JLabel("PVRZ index starts at:");
    sPvrzIndex = new JSpinner(new SpinnerNumberModel(BamOptionsDialog.getPvrzIndex(), 0, 99999, 1));
    sPvrzIndex.setToolTipText("Enter a number from 0 to 99999");
    sPvrzIndex.addChangeListener(this);
    JLabel lFramesCompression = new JLabel("Compression type:");
    cbCompression = new JComboBox<>(CompressionItems);
    cbCompression.setSelectedIndex(BamOptionsDialog.getCompressionType());
    bCompressionHelp = new JButton("?");
    bCompressionHelp.setMargin(new Insets(2, 4, 2, 4));
    bCompressionHelp.setToolTipText("About compression types");
    bCompressionHelp.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 0, 0);
    pFramesOptionsVersionV2.add(lPvrzIndex, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pFramesOptionsVersionV2.add(sPvrzIndex, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 0, 0);
    pFramesOptionsVersionV2.add(lFramesCompression, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 8, 0);
    pFramesOptionsVersionV2.add(cbCompression, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 4), 0, 0);
    pFramesOptionsVersionV2.add(bCompressionHelp, c);

    pFramesOptionsVersion = new JPanel(new CardLayout());
    pFramesOptionsVersion.add(pFramesOptionsVersionV1, "V1");
    pFramesOptionsVersion.add(pFramesOptionsVersionV2, "V2");

    // creating "Output options" section
    JPanel pFramesExport = new JPanel(new GridBagLayout());
    pFramesExport.setBorder(BorderFactory.createTitledBorder("Output options "));
    JLabel lFramesVersion = new JLabel("BAM version:");
    cbVersion = new JComboBox<>(BamVersionItems);
    cbVersion.addActionListener(this);
    cbVersion.setSelectedIndex(BamOptionsDialog.getBamVersion());
    bVersionHelp = new JButton("?");
    bVersionHelp.setMargin(new Insets(2, 4, 2, 4));
    bVersionHelp.setToolTipText("About BAM versions");
    bVersionHelp.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(8, 4, 8, 0), 0, 0);
    pFramesExport.add(lFramesVersion, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 8, 0);
    pFramesExport.add(cbVersion, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pFramesExport.add(bVersionHelp, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 0), 0, 0);
    pFramesExport.add(pFramesOptionsVersion, c);

    // putting all together
    JPanel pFrames = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(8, 8, 0, 8), 0, 0);
    pFrames.add(pFramesImport, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 8, 8, 8), 0, 0);
    pFrames.add(pFramesExport, c);

    return pFrames;
  }

  private JPanel createCyclesTab()
  {
    GridBagConstraints c = new GridBagConstraints();

    // creating "Cycles" section
    JPanel pCyclesButtons = new JPanel(new GridBagLayout());
    bCyclesAdd = new JButton("Add cycle");
    bCyclesAdd.addActionListener(this);
    bCyclesRemove = new JButton("Remove cycle(s)");
    bCyclesRemove.addActionListener(this);
    bCyclesRemoveAll = new JButton("Remove all");
    bCyclesRemoveAll.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pCyclesButtons.add(bCyclesAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0);
    pCyclesButtons.add(bCyclesRemove, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0);
    pCyclesButtons.add(bCyclesRemoveAll, c);

    JPanel pCyclesArrows = new JPanel(new GridBagLayout());
    bCyclesUp = new JButton(Icons.getIcon(Icons.ICON_UP_16));
    bCyclesUp.setMargin(new Insets(bCyclesUp.getInsets().top, 2, bCyclesUp.getInsets().bottom, 2));
    bCyclesUp.addActionListener(this);
    bCyclesDown = new JButton(Icons.getIcon(Icons.ICON_DOWN_16));
    bCyclesDown.setMargin(new Insets(bCyclesDown.getInsets().top, 2, bCyclesDown.getInsets().bottom, 2));
    bCyclesDown.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 16);
    pCyclesArrows.add(bCyclesUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0, 16);
    pCyclesArrows.add(bCyclesDown, c);

    JPanel pCyclesList = new JPanel(new GridBagLayout());
    JLabel lCycles = new JLabel("Cycles:");
    modelCycles = new BamCyclesListModel(this);
    listCycles = new JList<>(modelCycles);
    listCycles.setCellRenderer(new IndexedCellRenderer());
    listCycles.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    listCycles.addListSelectionListener(this);
    JScrollPane scroll = new JScrollPane(listCycles);
    c = ViewerUtil.setGBC(c, 0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0);
    pCyclesList.add(lCycles, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    pCyclesList.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 2, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.VERTICAL, new Insets(0, 4, 0, 0), 0, 0);
    pCyclesList.add(pCyclesArrows, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    pCyclesList.add(pCyclesButtons, c);

    // creating Macros/Preview section
    JPanel pMacros = new JPanel(new GridBagLayout());
    JLabel lMacroCurCycle = new JLabel("Selected cycle(s):");
    bMacroAssignFrames = new JButton("Assign all frames");
    bMacroAssignFrames.addActionListener(this);
    bMacroRemoveFrames = new JButton("Remove all frames");
    bMacroRemoveFrames.addActionListener(this);
    bMacroSortFramesAsc = new JButton("Sort frames");
    bMacroSortFramesAsc.addActionListener(this);
    bMacroDuplicateFrames = new JButton("Duplicate each frame");
    bMacroDuplicateFrames.addActionListener(this);
    bMacroDuplicateCycle = new JButton("Duplicate cycle(s)");
    bMacroDuplicateCycle.addActionListener(this);
    bMacroReverseFrames = new JButton("Reverse frames order");
    bMacroReverseFrames.addActionListener(this);
    JLabel lMacroAllCycles = new JLabel("All cycles:");
    bMacroRemoveAll = new JButton("Remove all frames");
    bMacroRemoveAll.addActionListener(this);
    bMacroReverseCycles = new JButton("Reverse cycles order");
    bMacroReverseCycles.addActionListener(this);
    JPanel pMacroPanel = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pMacroPanel.add(lMacroCurCycle, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroAssignFrames, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroRemoveFrames, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroDuplicateFrames, c);
    c = ViewerUtil.setGBC(c, 1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroDuplicateCycle, c);
    c = ViewerUtil.setGBC(c, 0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroSortFramesAsc, c);
    c = ViewerUtil.setGBC(c, 1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroReverseFrames, c);
    c = ViewerUtil.setGBC(c, 0, 4, 2, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(16, 0, 0, 0), 0, 0);
    pMacroPanel.add(lMacroAllCycles, c);
    c = ViewerUtil.setGBC(c, 0, 5, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroRemoveAll, c);
    c = ViewerUtil.setGBC(c, 1, 5, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroReverseCycles, c);
    c = ViewerUtil.setGBC(c, 0, 6, 2, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    pMacroPanel.add(new JPanel(), c);

    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 8, 8, 8), 0, 0);
    pMacros.add(pMacroPanel, c);
    pMacros.setMinimumSize(pMacros.getPreferredSize());

    JPanel pPreview = new JPanel(new GridBagLayout());
    rcCyclesPreview = new RenderCanvas(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    pPreview.add(rcCyclesPreview, c);
    pPreview.setMinimumSize(pPreview.getPreferredSize());

    tpCyclesSection = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    tpCyclesSection.addTab("Macros", pMacros);
    tpCyclesSection.addTab("Quick Preview", pPreview);
    tpCyclesSection.setSelectedIndex(0);

    JPanel pTopHalf = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 8, 0, 0), 0, 0);
    pTopHalf.add(pCyclesList, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(8, 16, 0, 0), 0, 0);
    pTopHalf.add(tpCyclesSection, c);

    // creating "Current Cycles" section
    JPanel pCycleTransfer = new JPanel(new GridBagLayout());
    bCurCycleAdd = new JButton(Icons.getIcon(Icons.ICON_FORWARD_16));
    bCurCycleAdd.setMargin(new Insets(2, bCurCycleAdd.getInsets().left,
                                      2, bCurCycleAdd.getInsets().right));
    bCurCycleAdd.addActionListener(this);
    bCurCycleRemove = new JButton(Icons.getIcon(Icons.ICON_BACK_16));
    bCurCycleRemove.setMargin(new Insets(2, bCurCycleRemove.getInsets().left,
                                         2, bCurCycleRemove.getInsets().right));
    bCurCycleRemove.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 8, 0);
    pCycleTransfer.add(bCurCycleAdd, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 8, 0);
    pCycleTransfer.add(bCurCycleRemove, c);

    JPanel pCurCycleArrows = new JPanel(new GridBagLayout());
    bCurCycleUp = new JButton(Icons.getIcon(Icons.ICON_UP_16));
    bCurCycleUp.setMargin(new Insets(bCurCycleUp.getInsets().top, 2, bCurCycleUp.getInsets().bottom, 2));
    bCurCycleUp.addActionListener(this);
    bCurCycleDown = new JButton(Icons.getIcon(Icons.ICON_DOWN_16));
    bCurCycleDown.setMargin(new Insets(bCurCycleDown.getInsets().top, 2, bCurCycleDown.getInsets().bottom, 2));
    bCurCycleDown.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 16);
    pCurCycleArrows.add(bCurCycleUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0, 16);
    pCurCycleArrows.add(bCurCycleDown, c);

    JLabel lCurCycleFrames = new JLabel("Available frames:");
    listFramesAvail = new JList<>(modelFrames);
    listFramesAvail.setCellRenderer(new IndexedCellRenderer());
    listFramesAvail.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    listFramesAvail.addListSelectionListener(this);
    listFramesAvail.addMouseListener(this);
    scroll = new JScrollPane(listFramesAvail);
    JLabel lCurCycle = new JLabel("Current cycle:");
    modelCurCycle = new BamCycleFramesListModel(this);
    listCurCycle = new JList<>(modelCurCycle);
    listCurCycle.setCellRenderer(new IndexedCellRenderer());
    listCurCycle.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    listCurCycle.addListSelectionListener(this);
    listCurCycle.addMouseListener(this);
    JScrollPane scroll2 = new JScrollPane(listCurCycle);

    pCurrentCycle = new JPanel(new GridBagLayout());
    pCurrentCycle.setBorder(BorderFactory.createTitledBorder("No cycle selected "));
    c = ViewerUtil.setGBC(c, 0, 0, 2, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pCurrentCycle.add(lCurCycleFrames, c);
    c = ViewerUtil.setGBC(c, 2, 0, 2, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pCurrentCycle.add(lCurCycle, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 4, 0), 0, 0);
    pCurrentCycle.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 4), 0, 0);
    pCurrentCycle.add(pCycleTransfer, c);
    c = ViewerUtil.setGBC(c, 2, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 0, 4, 0), 0, 0);
    pCurrentCycle.add(scroll2, c);
    c = ViewerUtil.setGBC(c, 3, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.VERTICAL, new Insets(4, 4, 0, 4), 0, 0);
    pCurrentCycle.add(pCurCycleArrows, c);

    // putting all together
    JPanel pCycles = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    pCycles.add(pTopHalf, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 2.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    pCycles.add(pCurrentCycle, c);

    return pCycles;
  }

  private JPanel createPreviewTab()
  {
    bamControlPreview = null;

    GridBagConstraints c = new GridBagConstraints();

    // create bottom control bar
    lPreviewCycle = new JLabel("Cycle: X/Y");
    bPreviewCyclePrev = new JButton(Icons.getIcon(Icons.ICON_BACK_16));
    bPreviewCyclePrev.setMargin(new Insets(bPreviewCyclePrev.getMargin().top, 2,
                                           bPreviewCyclePrev.getMargin().bottom, 2));
    bPreviewCyclePrev.addActionListener(this);
    bPreviewCycleNext = new JButton(Icons.getIcon(Icons.ICON_FORWARD_16));
    bPreviewCycleNext.setMargin(new Insets(bPreviewCycleNext.getMargin().top, 2,
                                           bPreviewCycleNext.getMargin().bottom, 2));
    bPreviewCycleNext.addActionListener(this);

    lPreviewFrame = new JLabel("Frame: X/Y");
    bPreviewFramePrev = new JButton(Icons.getIcon(Icons.ICON_BACK_16));
    bPreviewFramePrev.setMargin(new Insets(bPreviewFramePrev.getMargin().top, 2,
                                           bPreviewFramePrev.getMargin().bottom, 2));
    bPreviewFramePrev.addActionListener(this);
    bPreviewFrameNext = new JButton(Icons.getIcon(Icons.ICON_FORWARD_16));
    bPreviewFrameNext.setMargin(new Insets(bPreviewFrameNext.getMargin().top, 2,
                                           bPreviewFrameNext.getMargin().bottom, 2));
    bPreviewFrameNext.addActionListener(this);

    bPreviewPlay = new JButton("Pause", Icons.getIcon(Icons.ICON_PLAY_16));
    bPreviewPlay.setMinimumSize(bPreviewPlay.getPreferredSize());
    bPreviewPlay.setText("Play");
    bPreviewPlay.addActionListener(this);
    bPreviewStop = new JButton("Stop", Icons.getIcon(Icons.ICON_STOP_16));
    bPreviewStop.addActionListener(this);
    JPanel pControls = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pControls.add(lPreviewCycle, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pControls.add(bPreviewCyclePrev, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewCycleNext, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 0, 0);
    pControls.add(lPreviewFrame, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pControls.add(bPreviewFramePrev, c);
    c = ViewerUtil.setGBC(c, 5, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewFrameNext, c);
    c = ViewerUtil.setGBC(c, 6, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 0, 0);
    pControls.add(bPreviewPlay, c);
    c = ViewerUtil.setGBC(c, 7, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pControls.add(bPreviewStop, c);

    JLabel lFps = new JLabel("Frames/second:");
    SpinnerNumberModel spinnerModel = new SpinnerNumberModel(15.0, 1.0, 30.0, 1.0);
    sPreviewFps = new JSpinner(spinnerModel);
    sPreviewFps.addChangeListener(this);
    currentFps = (Double)spinnerModel.getValue();
    JLabel lPreviewMode = new JLabel("Playback mode:");
    cbPreviewMode = new JComboBox<>(PlaybackModeItems);
    cbPreviewMode.setSelectedIndex(MODE_CURRENT_CYCLE_LOOPED);
    cbPreviewMode.addActionListener(this);
    cbPreviewShowMarker = new JCheckBox("Show markers");
    cbPreviewShowMarker.addActionListener(this);
    cbPreviewZoom = new JCheckBox("Zoom");
    cbPreviewZoom.addActionListener(this);
    JPanel pOptions = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pOptions.add(lPreviewMode, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pOptions.add(cbPreviewMode, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 0, 0);
    pOptions.add(lFps, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pOptions.add(sPreviewFps, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 0, 0);
    pOptions.add(cbPreviewZoom, c);
    c = ViewerUtil.setGBC(c, 5, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 0, 0);
    pOptions.add(cbPreviewShowMarker, c);

    JPanel pCanvas = new JPanel(new GridBagLayout());
    rcPreview = new RenderCanvas();
    rcPreview.setInterpolationType(RenderCanvas.TYPE_NEAREST_NEIGHBOR);
    rcPreview.setScalingEnabled(true);
    scrollPreview = new JScrollPane(rcPreview);
    scrollPreview.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPreview.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollPreview.setBorder(BorderFactory.createEmptyBorder());
    previewCanvas = null;

    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pCanvas.add(scrollPreview, c);

    // putting all together
    JPanel pPreview = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 8, 0, 8), 0, 0);
    pPreview.add(pOptions, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    pPreview.add(pCanvas, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 8, 8, 8), 0, 0);
    pPreview.add(pControls, c);

    timer = new Timer(0, this);
    isPreviewModified = true;
    isPreviewPlaying = false;
    previewSetFrameRate(currentFps);

    return pPreview;
  }

  private JPanel createFiltersTab()
  {
    GridBagConstraints c = new GridBagConstraints();

    // creating "Filters" section
    Vector<BamFilterFactory.FilterInfo> filters = new Vector<BamFilterFactory.FilterInfo>();
    for (int i = 0; i < BamFilterFactory.getFilterInfoSize(); i++) {
      filters.add(BamFilterFactory.getFilterInfo(i));
    }
    Collections.sort(filters);
    cbFiltersAdd = new JComboBox<>(filters);
    bFiltersAdd = new JButton("Add");
    bFiltersAdd.addActionListener(this);
    JPanel pFiltersAdd = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pFiltersAdd.add(cbFiltersAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pFiltersAdd.add(bFiltersAdd, c);

    bFiltersRemove = new JButton("Remove");
    bFiltersRemove.addActionListener(this);
    bFiltersRemoveAll = new JButton("Remove all");
    bFiltersRemoveAll.addActionListener(this);
    JPanel pFiltersRemove = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pFiltersRemove.add(bFiltersRemove, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pFiltersRemove.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0);
    pFiltersRemove.add(bFiltersRemoveAll, c);

    bFiltersUp = new JButton(Icons.getIcon(Icons.ICON_UP_16));
    bFiltersUp.setMargin(new Insets(16, 2, 16, 2));
    bFiltersUp.addActionListener(this);
    bFiltersDown = new JButton(Icons.getIcon(Icons.ICON_DOWN_16));
    bFiltersDown.setMargin(new Insets(16, 2, 16, 2));
    bFiltersDown.addActionListener(this);
    JPanel pFiltersMove = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pFiltersMove.add(bFiltersUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0, 0);
    pFiltersMove.add(bFiltersDown, c);

    JPanel pFiltersDesc = new JPanel(new GridBagLayout());
    pFiltersDesc.setBorder(BorderFactory.createTitledBorder("Filter Description "));
    taFiltersDesc = new JTextArea(8, 0);
    taFiltersDesc.setEditable(false);
    taFiltersDesc.setFont(UIManager.getFont("Label.font"));
    Color bg = UIManager.getColor("Label.background");
    taFiltersDesc.setBackground(bg);
    taFiltersDesc.setSelectionColor(bg);
    taFiltersDesc.setSelectedTextColor(bg);
    taFiltersDesc.setWrapStyleWord(true);
    taFiltersDesc.setLineWrap(true);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    pFiltersDesc.add(taFiltersDesc, c);

    modelFilters = new SimpleListModel<BamFilterBase>();
    listFilters = new JList<>(modelFilters);
    listFilters.setCellRenderer(new IndexedCellRenderer());
    listFilters.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    listFilters.addListSelectionListener(this);
    JScrollPane scroll = new JScrollPane(listFilters);

    JPanel pFiltersList = new JPanel(new GridBagLayout());
    pFiltersList.setBorder(BorderFactory.createTitledBorder("Filters "));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0);
    pFiltersList.add(pFiltersAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 4), 0, 0);
    pFiltersList.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(4, 4, 0, 0), 0, 0);
    pFiltersList.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.VERTICAL, new Insets(4, 4, 0, 4), 0, 0);
    pFiltersList.add(pFiltersMove, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 4, 0, 0), 0, 0);
    pFiltersList.add(pFiltersRemove, c);
    c = ViewerUtil.setGBC(c, 1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.NONE, new Insets(0, 0, 0, 4), 0, 0);
    pFiltersList.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 0, 3, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(8, 4, 4, 0), 0, 0);
    pFiltersList.add(pFiltersDesc, c);


    // creating "Filter settings" section
    JPanel pFiltersSettingsMain = new JPanel(new GridBagLayout());
    pFiltersSettingsMain.setBorder(BorderFactory.createTitledBorder("Filter settings "));
    pFiltersSettings = new JPanel(new BorderLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(0, 4, 4, 4), 0, 0);
    pFiltersSettingsMain.add(pFiltersSettings, c);

    // creating "Quick Preview" section
    JPanel pFiltersPreviewControls = new JPanel(new GridBagLayout());
    JLabel l = new JLabel("Frame:");
    cbFiltersShowMarker = new JCheckBox("Show markers", false);
    cbFiltersShowMarker.addActionListener(this);
    sFiltersPreviewFrame = new JSpinner(new SpinnerNumberModel(0, 0, 99999, 1));
    sFiltersPreviewFrame.addChangeListener(this);
    JPanel pDummy = new JPanel();
    pDummy.setPreferredSize(cbFiltersShowMarker.getPreferredSize());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 0, 4, 8), 0, 0);
    pFiltersPreviewControls.add(cbFiltersShowMarker, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 0, 4, 0), 0, 0);
    pFiltersPreviewControls.add(l, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 4, 4, 0), 0, 0);
    pFiltersPreviewControls.add(sFiltersPreviewFrame, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                          GridBagConstraints.NONE, new Insets(4, 0, 4, 4), 0, 0);
    pFiltersPreviewControls.add(pDummy, c);

    JPanel pFiltersPreview = new JPanel(new GridBagLayout());
    pFiltersPreview.setBorder(BorderFactory.createTitledBorder("Quick Preview "));
    rcFiltersPreview = new RenderCanvas(new BufferedImage(320, 320, BufferedImage.TYPE_INT_ARGB));
    rcFiltersPreview.setMinimumSize(rcFiltersPreview.getPreferredSize());
    rcFiltersPreview.setScalingEnabled(false);
    rcFiltersPreview.setHorizontalAlignment(SwingConstants.CENTER);
    rcFiltersPreview.setVerticalAlignment(SwingConstants.CENTER);
    scrollFiltersPreview = new JScrollPane(rcFiltersPreview);
    scrollFiltersPreview.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollFiltersPreview.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollFiltersPreview.setBorder(BorderFactory.createEmptyBorder());

    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(0, 4, 0, 4), 0, 0);
    pFiltersPreview.add(scrollFiltersPreview, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.HORIZONTAL, new Insets(4, 4, 4, 4), 0, 0);
    pFiltersPreview.add(pFiltersPreviewControls, c);


    // putting all together
    JPanel pFilters = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 2, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(8, 8, 8, 0), 0, 0);
    pFilters.add(pFiltersList, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.5, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(8, 8, 0, 8), 0, 0);
    pFilters.add(pFiltersSettingsMain, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                          GridBagConstraints.BOTH, new Insets(8, 8, 8, 8), 0, 0);
    pFilters.add(pFiltersPreview, c);

    updateFilterList();

    return pFilters;
  }


  /** Close the main dialog manually. 'force' overrides the confirmation dialog. */
  private void hideWindow(boolean force)
  {
    if (force || confirmCloseDialog()) {
      clear();
      setVisible(false);
    }
  }

  /** Presents a confirmation dialog if any frames or cycles are present. */
  private boolean confirmCloseDialog()
  {
    boolean isEmpty = (modelFrames.isEmpty() && modelCycles.isEmpty());
    if (!isEmpty) {
      String msg = String.format("%d frame(s) and %d cycle(s) will be discarded.\n" +
                                 "Do you really want to close the dialog?",
                                 modelFrames.getSize(), modelCycles.getSize());
      if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Close dialog",
                                                                  JOptionPane.YES_NO_OPTION)) {
        isEmpty = true;
      }
    }
    return isEmpty;
  }

  /** Resets the dialog state. */
  private void clear()
  {
    previewStop();
    outputSetModified(true);
    filterRemoveAll();
    cyclesRemoveAll();
    framesRemoveAll();
    paletteDialog.clear();
  }


  /** Updates the tab component state. */
  private void updateStatus()
  {
    boolean isReady = (!modelFrames.isEmpty() && !modelCycles.isEmpty());
    boolean showTabs = !modelFrames.isEmpty();

    bPalette.setEnabled(showTabs);
    miSessionExport.setEnabled(showTabs);
    bConvert.setEnabled(isReady);
    if (!showTabs) {
      tpMain.setSelectedIndex(TAB_FRAMES);
    }
    tpMain.setEnabledAt(TAB_CYCLES, showTabs);
    tpMain.setEnabledAt(TAB_PREVIEW, isReady);
    tpMain.setEnabledAt(TAB_FILTERS, showTabs);
  }

  /** Updates relevant components to reflect the current state of the global frames list. */
  private void updateFramesList()
  {
    // updating button states
    Pair<Integer> bounds = getIndexBounds(listFrames.getSelectedIndices());
    bFramesUp.setEnabled(!modelFrames.isEmpty() && bounds.getFirst() > 0);
    bFramesDown.setEnabled(!modelFrames.isEmpty() && bounds.getFirst() >= 0 &&
                           bounds.getSecond() < modelFrames.getSize() - 1);
    miFramesRemove.setEnabled(!modelFrames.isEmpty() && !listFrames.isSelectionEmpty());
    miFramesRemoveAll.setEnabled(!modelFrames.isEmpty());
    miFramesDropUnused.setEnabled(!modelFrames.isEmpty());
    bpmFramesRemove.setEnabled(miFramesRemove.isEnabled() || miFramesRemoveAll.isEnabled() ||
                               miFramesDropUnused.isEnabled());

    // updating palette
    paletteDialog.setPaletteModified();

    updateCyclesList();
    updateStatus();
  }

  /** Updates relevant components to reflect the current state of the cycles list. */
  private void updateCyclesList()
  {
    listCycles.repaint();
    Pair<Integer> bounds = getIndexBounds(listCycles.getSelectedIndices());
    int idx = (bounds.getFirst().compareTo(bounds.getSecond()) == 0) ? bounds.getFirst() : -1;
    if (idx >= 0) {
      listCycles.ensureIndexIsVisible(idx);
    }

    // updating button states
    bCyclesUp.setEnabled(!modelCycles.isEmpty() && bounds.getFirst() > 0);
    bCyclesDown.setEnabled(!modelCycles.isEmpty() && bounds.getFirst() >= 0 &&
                           bounds.getSecond() < modelCycles.getSize() - 1);
    bCyclesRemove.setEnabled(!listCycles.isSelectionEmpty());
    bCyclesRemoveAll.setEnabled(!modelCycles.isEmpty());

    // updating macro buttons
    bMacroAssignFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroRemoveFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroDuplicateCycle.setEnabled(!listCycles.isSelectionEmpty());
    bMacroDuplicateFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroSortFramesAsc.setEnabled(!listCycles.isSelectionEmpty());
    bMacroReverseFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroRemoveAll.setEnabled(!modelCycles.isEmpty());
    bMacroReverseCycles.setEnabled(!modelCycles.isEmpty());

    initCurrentCycle(bounds);
    updateStatus();
  }

  /** Updates relevant components to reflect the current state of the selected cycle. */
  private void updateCurrentCycle()
  {
    // updating button states
    Pair<Integer> bounds = getIndexBounds(listCurCycle.getSelectedIndices());
    bCurCycleUp.setEnabled(!modelCurCycle.isEmpty() && bounds.getFirst() > 0);
    bCurCycleDown.setEnabled(!modelCurCycle.isEmpty() && bounds.getFirst() >= 0 &&
                             bounds.getSecond() < modelCurCycle.getSize() - 1);
    bCurCycleAdd.setEnabled(!listFramesAvail.isSelectionEmpty());
    bCurCycleRemove.setEnabled(!listCurCycle.isSelectionEmpty());
    listFramesAvail.invalidate();
    listCurCycle.invalidate();
    pCurrentCycle.validate();
  }

  private void initCurrentCycle(int cycleIdx)
  {
    initCurrentCycle(new Pair<Integer>(cycleIdx, cycleIdx));
  }

  /** Initializes the "Current cycle" section of the Cycles tab. */
  private void initCurrentCycle(Pair<Integer> cycleIndices)
  {
    if (cycleIndices != null) {
      if (cycleIndices.getFirst().compareTo(cycleIndices.getSecond()) == 0 &&
          cycleIndices.getFirst() >= 0 && cycleIndices.getFirst() < modelCycles.getSize()) {
        int cycleIdx = cycleIndices.getFirst();

        // enabling components
        listFramesAvail.setEnabled(true);
        bCurCycleAdd.setEnabled(true);
        bCurCycleRemove.setEnabled(true);
        listCurCycle.setEnabled(true);

        // updating group box title
        pCurrentCycle.setBorder(BorderFactory.createTitledBorder(String.format("Cycle %d ", cycleIdx)));

        listFramesAvail.setSelectedIndices(new int[]{});

        // updating current cycle list view
        modelCurCycle.setCycle(cycleIdx);

        listCurCycle.setSelectedIndices(new int[]{});

        updateCurrentCycle();
      } else {
        // no cycle selected
        listFramesAvail.setSelectedIndices(new int[]{});
        listFramesAvail.setEnabled(false);
        bCurCycleAdd.setEnabled(false);
        bCurCycleRemove.setEnabled(false);
        listCurCycle.setSelectedIndices(new int[]{});
        listCurCycle.setEnabled(false);

        if (cycleIndices.getFirst() < 0 || cycleIndices.getSecond() < 0) {
          pCurrentCycle.setBorder(BorderFactory.createTitledBorder("No cycle selected "));
        } else {
          pCurrentCycle.setBorder(BorderFactory.createTitledBorder("Too many cycles selected "));
        }
        modelCurCycle.setCycle(-1);
      }
    }
  }

  /** Updates relevant components to reflect the current frames list selection in the Frames tab. */
  private void updateFrameInfo(int[] indices)
  {
    if (indices != null && indices.length > 0) {
      // enabling components
      tfFrameWidth.setEnabled(true);
      tfFrameHeight.setEnabled(true);
      tfFrameCenterX.setEnabled(true);
      tfFrameCenterY.setEnabled(true);
      cbCompressFrame.setEnabled(isBamV1Selected());

      // evaluating data
      PseudoBamFrameEntry fe = getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[0]);
      int initialWidth = fe.getWidth();
      int initialHeight = fe.getHeight();
      int initialX = fe.getCenterX();
      int initialY = fe.getCenterY();
      boolean initialState = (fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED) != null) ?
                             (Boolean)fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED) : false;
      boolean changedWidth = false, changedHeight = false;
      boolean changedCenterX = false, changedCenterY = false;
      boolean changedCompression = false;

      for (int i = 1; i < indices.length; i++) {
        fe = getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]);
        changedWidth |= (!changedWidth && initialWidth != fe.getWidth());
        changedHeight |= (!changedHeight && initialHeight != fe.getHeight());
        changedCenterX |= (!changedCenterX && initialX != fe.getCenterX());
        changedCenterY |= (!changedCenterY && initialY != fe.getCenterY());
        boolean b = (fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED) != null) ?
                    (Boolean)fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED) : false;
        changedCompression |= (!changedCompression && initialState != b);
        if (changedWidth && changedHeight && changedCenterX && changedCenterY && changedCompression) {
          break;
        }
      }

      // setting frame info
      String title = null;
      if (indices.length > 1) {
        title = String.format("%d frames selected ", indices.length);
      } else {
        title = String.format("Frame %d ", indices[0]);
      }
      pFramesCurFrame.setBorder(BorderFactory.createTitledBorder(title));
      tfFrameWidth.setText(changedWidth ? "" : Integer.toString(initialWidth));
      tfFrameHeight.setText(changedHeight ? "" : Integer.toString(initialHeight));
      tfFrameCenterX.setText(changedCenterX ? "" : Integer.toString(initialX));
      tfFrameCenterY.setText(changedCenterY ? "" : Integer.toString(initialY));
      cbCompressFrame.setSelected(changedCompression ? false : initialState);
    } else {
      // no frame selected
      final String zero = "0";
      tfFrameWidth.setEnabled(false);
      tfFrameWidth.setText(zero);
      tfFrameHeight.setEnabled(false);
      tfFrameHeight.setText(zero);
      tfFrameCenterX.setEnabled(false);
      tfFrameCenterX.setText(zero);
      tfFrameCenterY.setEnabled(false);
      tfFrameCenterY.setText(zero);
      cbCompressFrame.setEnabled(false);
      cbCompressFrame.setSelected(false);
      pFramesCurFrame.setBorder(BorderFactory.createTitledBorder("No frame selected "));
    }
  }

  /** Updates the specified quick preview section. */
  private void updateQuickPreview(RenderCanvas target, int[] indices, boolean showCenter)
  {
    if (target != null) {
      int imgWidth = target.getImage().getWidth(null);
      int imgHeight = target.getImage().getHeight(null);
      Graphics2D g = (Graphics2D)target.getImage().getGraphics();
      try {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, imgWidth, imgHeight);

        if (indices != null && indices.length == 1 &&
            indices[0] >= 0 && indices[0] < modelFrames.getSize()) {

          // drawing frame
          int left = 0, top = 0;
          float ratio = 1.0f;

          int frameIdx = indices[0];
          PseudoBamFrameEntry fe = getBamDecoder(BAM_ORIGINAL).getFrameInfo(frameIdx);
          PseudoBamControl control = getBamDecoder(BAM_ORIGINAL).createControl();
          control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
          Image image = getBamDecoder(BAM_ORIGINAL).frameGet(control, frameIdx);
          control = null;
          boolean zoom = ((fe.getWidth() > imgWidth || fe.getHeight() > imgHeight));
          if (zoom) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            float ratioX = (float)imgWidth / (float)image.getWidth(null);
            float ratioY = (float)imgHeight / (float)image.getHeight(null);
            Pair<Float> minMaxRatio = new Pair<Float>(Math.min(ratioX, ratioY), Math.max(ratioX, ratioY));
            if ((float)image.getWidth(null)*minMaxRatio.getSecond() < (float)imgWidth &&
                (float)image.getHeight(null)*minMaxRatio.getSecond() < (float)imgHeight) {
              ratio = minMaxRatio.getSecond();
            } else {
              ratio = minMaxRatio.getFirst();
            }
            int newWidth = (int)((float)image.getWidth(null)*ratio);
            int newHeight = (int)((float)image.getHeight(null)*ratio);
            left = (imgWidth - newWidth) >>> 1;
            top = (imgHeight - newHeight) >>> 1;
            g.drawImage(image, left, top, newWidth, newHeight, null);
          } else {
            left = (imgWidth - image.getWidth(null)) >>> 1;
            top = (imgHeight - image.getHeight(null)) >>> 1;
            g.drawImage(image, left, top, null);
          }

          if (showCenter) {
            // drawing center location
            int cx = left + (int)((float)fe.getCenterX()*ratio);
            int cy = top + (int)((float)fe.getCenterY()*ratio);
            if (cx >= 0 && cx < imgWidth && cy >= 0 && cy < imgHeight) {
              g.setStroke(new BasicStroke(3.0f));
              g.setColor(Color.BLACK);
              g.drawLine(cx - 4, cy, cx + 4, cy);
              g.drawLine(cx, cy - 4, cx, cy + 4);
              g.setStroke(new BasicStroke(1.0f));
              g.setColor(Color.RED);
              g.drawLine(cx - 4, cy, cx + 4, cy);
              g.drawLine(cx, cy - 4, cx, cy + 4);
            }
          }
        }
      } finally {
        g.dispose();
        g = null;
        target.repaint();
      }
    }
  }

  /** Updates the preview tab. */
  private void updatePreview()
  {
    previewPrepare(false);
    previewDisplay();

    // updating buttons
    bPreviewCyclePrev.setEnabled(bamControlPreview.cycleGet() > 0);
    bPreviewCycleNext.setEnabled(bamControlPreview.cycleGet() < bamControlPreview.cycleCount() - 1);
    bPreviewFramePrev.setEnabled(bamControlPreview.cycleGetFrameIndex() > 0);
    bPreviewFrameNext.setEnabled(bamControlPreview.cycleGetFrameIndex() < bamControlPreview.cycleFrameCount() - 1);
    lPreviewCycle.setText(String.format("Cycle: %d/%d",
                                        bamControlPreview.cycleGet(), bamControlPreview.cycleCount() - 1));
    lPreviewFrame.setText(String.format("Frame: %d/%d",
                                        bamControlPreview.cycleGetFrameIndex(),
                                        bamControlPreview.cycleFrameCount() - 1));
  }


  /** Updates relevant components to reflect the current state of the filters list. */
  private void updateFilterList()
  {
    // updating button states
    int idx = listFilters.getSelectedIndex();
    bFiltersUp.setEnabled(!modelFilters.isEmpty() && idx > 0);
    bFiltersDown.setEnabled(!modelFilters.isEmpty() && idx >= 0 && idx < modelFilters.size() - 1);
    bFiltersRemove.setEnabled(!modelFilters.isEmpty() && !listFilters.isSelectionEmpty());
    bFiltersRemoveAll.setEnabled(!modelFilters.isEmpty());

    updateFilterInfo();
    updateFilterControls();
    filterSetPreviewFrame(filterGetPreviewFrameIndex(), true);
  }

  /** Updates the filter info box. */
  private void updateFilterInfo()
  {
    final String fmt = "Name: %s\n\nDescription:\n%s";
    int idx = listFilters.getSelectedIndex();
    if (idx >= 0) {
      BamFilterBase filter = modelFilters.get(idx);
      taFiltersDesc.setText(String.format(fmt, filter.getName(), filter.getDescription()));
    } else {
      taFiltersDesc.setText(String.format(fmt, "", ""));
    }
  }

  /** Updates the filter settings section with the controls of the currently selected filter. */
  private void updateFilterControls()
  {
    pFiltersSettings.removeAll();
    int idx = listFilters.getSelectedIndex();
    if (idx >= 0) {
      BamFilterBase filter = modelFilters.get(idx);
      pFiltersSettings.add(filter.getControls(), BorderLayout.CENTER);
    } else {
      // insert empty dummy control
      pFiltersSettings.add(new JPanel(), BorderLayout.CENTER);
    }
    pFiltersSettings.validate();
    pFiltersSettings.repaint();
  }

  /** Adjusts cycle indices after adding frames to the global frames list. */
  private void updateCyclesAddedFrames(int[] indices)
  {
    if (indices != null && indices.length > 0) {
      Arrays.sort(indices);
      for (final int index: indices) {
        for (int i = 0; i < modelCycles.getSize(); i++) {
          PseudoBamCycleEntry cycle = modelCycles.getElementAt(i);
          for (int j = 0; j < cycle.size(); j++) {
            int idx = cycle.get(j);
            if (idx >= index) {
              cycle.set(j, idx + 1);
            }
          }
        }
      }
      updateCurrentCycle();
    }
  }

  /** Adjusts cycle indices after removing frames from the global frames list. */
  private void updateCyclesRemovedFrames(int[] indices)
  {
    if (indices != null && indices.length > 0) {
      Arrays.sort(indices);
      for (int x = indices.length - 1; x >= 0; x--) {
        int index = indices[x];
        for (int i = 0; i < modelCycles.getSize(); i++) {
          PseudoBamCycleEntry cycle = modelCycles.getElementAt(i);
          int j = 0;
          while (j < cycle.size()) {
            int idx = cycle.get(j);
            if (idx == index) {
              cycle.remove(j, 1);
              continue;
            } else if (idx > index) {
              cycle.set(j, idx - 1);
            }
            j++;
          }
        }
      }
      updateCurrentCycle();
    }
  }

  /** Adjusts cycle indices after moving frames within the global frames list. */
  private void updateCyclesMovedFrames(int index, int shift)
  {
    if (index >= 0 && shift != 0) {
      for (int i = 0; i < modelCycles.getSize(); i++) {
        PseudoBamCycleEntry cycle = modelCycles.getElementAt(i);
        for (int j = 0; j < cycle.size(); j++) {
          if (cycle.get(j) == index) {
            cycle.set(j, cycle.get(j) + shift);
          } else if (shift > 0 && cycle.get(j) > index && cycle.get(j) <= index+shift) {
            cycle.set(j, cycle.get(j) - 1);
          } else if (shift < 0 && cycle.get(j) < index && cycle.get(j) >= index+shift) {
            cycle.set(j, cycle.get(j) + 1);
          }
        }
      }
      updateCurrentCycle();
    }
  }

  /** Action for "Compress frame". */
  private void framesUpdateCompressFrame()
  {
    int[] indices = listFrames.getSelectedIndices();
    if (indices != null) {
      Boolean b = Boolean.valueOf(cbCompressFrame.isSelected());
      for (int i = 0; i < indices.length; i++) {
        getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]).setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, b);
      }
      outputSetModified(true);
    }
  }

  /** Evaluates the value in the specified "Center X/Y" text field. */
  private void framesValidateCenterValue(JTextField tf)
  {
    if (tf != null) {
      boolean isCenterX = (tf == tfFrameCenterX);
      int[] indices = listFrames.getSelectedIndices();
      if (indices != null && indices.length > 0) {
        String s = tf.getText();
        boolean isRelative;
        if (s.startsWith("++")) {
          s = s.substring(2);
          isRelative = true;
        } else if (s.startsWith("--")) {
          s = s.substring(1);
          isRelative = true;
        } else {
          isRelative = false;
        }
        int value = numberValidator(s, Short.MIN_VALUE, Short.MAX_VALUE, Integer.MAX_VALUE);
        if (value != Integer.MAX_VALUE) {
          int result = 0;
          for(int i = 0; i < indices.length; i++) {
            if (isCenterX) {
              if (isRelative) {
                result = getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]).getCenterX() + value;
                result = Math.min(Math.max(result, Short.MIN_VALUE), Short.MAX_VALUE);
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]).setCenterX(result);
              } else {
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]).setCenterX(value);
              }
              outputSetModified(true);
            } else {
              if (isRelative) {
                result = getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]).getCenterY() + value;
                result = Math.min(Math.max(result, Short.MIN_VALUE), Short.MAX_VALUE);
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]).setCenterY(result);
              } else {
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]).setCenterY(value);
              }
              outputSetModified(true);
            }
          }
          if (isRelative) {
            if (indices.length == 1) {
              tf.setText(Integer.toString(result));
            } else {
              tf.setText("");
            }
          }
        } else {
          updateFrameInfo(indices);
        }
        updateQuickPreview(rcFramesPreview, indices, true);
      }
    }
  }

  /** Action for "Add..."->"Add file(s)...": adds image files to the frames list. */
  public void framesAddFiles()
  {
    Path[] files = getOpenFileName(this, "Choose file(s) to add", null, true, getGraphicsFilters(), 0);
    if (files != null) {
      try {
        WindowBlocker.blockWindow(this, true);
        framesAdd(files);
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

  /** Action for "Add..."->"Add resource(s)...": adds image resources to the frames list. */
  public void framesAddResources()
  {
    ResourceEntry[] entries =
        OpenResourceDialog.showOpenDialog(this, "Choose resource(s) to add", new String[]{"BAM", "BMP"}, true);
    if (entries != null) {
      try {
        WindowBlocker.blockWindow(this, true);
        framesAdd(entries);
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

  /** Called by framesAddLauncher. Can also be called directly. Makes use of a progress monitor if available. */
  public void framesAdd(Path[] files)
  {
    if (files != null) {
      ResourceEntry[] entries = new ResourceEntry[files.length];
      for (int i = 0; i < files.length; i++) {
        entries[i] = new FileResourceEntry(files[i]);
      }
      framesAdd(entries);
    }
  }

  public void framesAdd(ResourceEntry[] entries)
  {
    if (entries != null) {
      outputSetModified(true);
      List<String> skippedFiles = new ArrayList<String>();
      int insertIndex = listFrames.getSelectedIndex();
      int idx = insertIndex;
      if (idx < 0) idx = modelFrames.getSize() - 1;
      try {
        for (int i = 0; i < entries.length; i++) {
          if (isProgressMonitorActive()) {
            // updating progress monitor
            if (isProgressMonitorCancelled()) {
              // adding remaining files to skip list
              for (int j = i; j < entries.length; j++) {
                if (entries[j] != null) {
                  skippedFiles.add(entries[j].toString());
                }
              }
              break;
            }
            advanceProgressMonitor(String.format("Adding file %d/%d", i+1, entries.length));
          }
          // adding files to global frames list
          if (entries[i] != null) {
            if (BamDecoder.isValid(entries[i])) {
              BamDecoder decoder = framesAddBam(idx + 1, entries[i]);
              if (decoder != null) {
                idx += decoder.frameCount();
              } else {
                skippedFiles.add(entries[i].toString());
              }
            } else {
              if (framesAddImage(idx + 1, entries[i], -1)) {
                idx++;
              } else {
                skippedFiles.add(entries[i].toString());
              }
            }
          }
        }
      } finally {
        releaseProgressMonitor();
      }
      listFrames.setSelectedIndex(idx);
      listFrames.ensureIndexIsVisible(idx);

      // adjusting cycle indices
//      int[] indices = new int[entries.length - skippedFiles.size()];
      int[] indices = new int[idx - insertIndex];
//      for (int i = 0; i < entries.length - skippedFiles.size(); i++) {
      for (int i = 0; i < indices.length; i++) {
        indices[i] = insertIndex + 1 + i;
      }
      updateCyclesAddedFrames(indices);

      updateFramesList();
      listFrames.requestFocusInWindow();

      // error handling
      if (!skippedFiles.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        if (skippedFiles.size() == 1) {
          sb.append(String.format("%d file has been skipped:\n", skippedFiles.size()));
        } else {
          sb.append(String.format("%d files have been skipped:\n", skippedFiles.size()));
        }
        int count = Math.min(5, skippedFiles.size());
        for (int i = 0; i < count; i++) {
          sb.append(String.format("  - %s\n", skippedFiles.get(i)));
        }
        if (skippedFiles.size() > 5) {
          sb.append("  - ...\n");
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /**
   * Specific: Adds the specified source BAM to the frames list.
   *
   * @return the BamDecoder object of the source BAM, or {@code null} on error
   */
  public BamDecoder framesAddBam(int listIndex, Path file)
  {
    return framesAddBam(listIndex, new FileResourceEntry(file));
  }

  /**
   * Specific: Adds the specified source BAM to the frames list.
   *
   * @return the BamDecoder object of the source BAM, or {@code null on error
   */
  public BamDecoder framesAddBam(int listIndex, ResourceEntry entry)
  {
    if (listIndex >= 0 && entry != null && BamDecoder.isValid(entry)) {
      BamDecoder decoder = BamDecoder.loadBam(entry);
      BamDecoder.BamControl control = decoder.createControl();
      control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);

      // preparing palette-specific properties
      IndexColorModel cm = null;
      if (decoder instanceof BamV1Decoder) {
        int[] palette = ((BamV1Decoder.BamV1Control)control).getPalette();
        int transColor = ((BamV1Decoder.BamV1Control)control).getTransparencyIndex();
        cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transColor, DataBuffer.TYPE_BYTE);
      }

      for (int j = 0; j < decoder.frameCount(); j++) {
        if (framesAddBamFrame(listIndex + j, decoder, control, j, cm) < 0) {
          return null;
        }
      }
      return decoder;
    }
    return null;
  }

  /** Adds a single BAM frame to the frame list. */
  private int framesAddBamFrame(int listIndex, BamDecoder decoder, BamDecoder.BamControl control,
                                int frameIndex, IndexColorModel cm)
  {
    if (decoder != null && frameIndex >= 0 && frameIndex < decoder.frameCount()) {
      listIndex = Math.max(0, Math.min(modelFrames.getSize(), listIndex));

      if (control == null) {
        control = decoder.createControl();
      }

      // preparing palette-specific properties
      if (cm == null && decoder instanceof BamV1Decoder) {
        int[] palette = ((BamV1Decoder.BamV1Control)control).getPalette();
        int transColor = ((BamV1Decoder.BamV1Control)control).getTransparencyIndex();
        cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transColor, DataBuffer.TYPE_BYTE);
      }

      // adding frame
      boolean isCompressed;
      int rleIndex;
      BamDecoder.FrameEntry fe = decoder.getFrameInfo(frameIndex);
      BufferedImage image = null;
      if (cm != null) {
        if (fe.getWidth() > 0 && fe.getHeight() > 0) {
          image = new BufferedImage(fe.getWidth(), fe.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, cm);
        } else {
          image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED, cm);
        }
        isCompressed = ((BamV1Decoder.BamV1FrameEntry)fe).isCompressed();
        rleIndex = ((BamV1Decoder)decoder).getRleIndex();
      } else {
        if (fe.getWidth() > 0 && fe.getHeight() > 0) {
          image = new BufferedImage(fe.getWidth(), fe.getHeight(), BufferedImage.TYPE_INT_ARGB);
        } else {
          image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        isCompressed = false;
        rleIndex = 0;
      }
      if (fe.getWidth() > 0 && fe.getHeight() > 0) {
        decoder.frameGet(control, frameIndex, image);
      }
      modelFrames.insert(listIndex, image, new Point(fe.getCenterX(), fe.getCenterY()));

      // setting required extra options
      PseudoBamFrameEntry fe2 = getBamDecoder(BAM_ORIGINAL).getFrameInfo(listIndex);
      ResourceEntry entry = decoder.getResourceEntry();
      if (entry instanceof FileResourceEntry) {
        fe2.setOption(BAM_FRAME_OPTION_PATH, entry.getActualPath().toString());
      } else {
        fe2.setOption(BAM_FRAME_OPTION_PATH, BAM_FRAME_PATH_BIFF + entry.getResourceName());
      }
      fe2.setOption(BAM_FRAME_OPTION_SOURCE_INDEX, Integer.valueOf(frameIndex));
      fe2.setOption(PseudoBamDecoder.OPTION_STRING_LABEL, entry.getResourceName() + ":" + frameIndex);
      fe2.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, Boolean.valueOf(isCompressed));
      fe2.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX, Integer.valueOf(rleIndex));
      return listIndex;
    }
    return -1;
  }

  private boolean framesAddImage(int listIndex, Path file, int frameIndex)
  {
    if (file != null) {
      return framesAddImage(listIndex, new FileResourceEntry(file), frameIndex);
    } else {
      return false;
    }
  }

  /**
   * Adds the specified source graphics file to the frames list.
   * @param listIndex The start position of the image or images in the frames list.
   * @param entry The resource entry pointing to the graphics file.
   * @param frameIndex An optional index for graphics files with multiple frames.
   *                   This is relevant for loading specific frames from animated GIFs.
   *                   Specify {@code -1} to load all available frames.
   * @return {@code true} if image or images have been successfully added to the frames list.
   */
  private boolean framesAddImage(int listIndex, ResourceEntry entry, int frameIndex)
  {
    boolean retVal = false;
    if (listIndex >= 0 && entry != null) {
      try {
        InputStream is = entry.getResourceDataAsStream();
        BufferedImage[] images;
        if (entry.getExtension().equalsIgnoreCase("gif")) {
          // Potential GIF animation
          GifSequenceReader reader = new GifSequenceReader(ImageIO.createImageInputStream(is));
          reader.decodeAll();
          images = new BufferedImage[reader.getFrameCount()];
          for (int i = 0; i < images.length; i++) {
            images[i] = reader.getFrame(i).getRenderedImage();
          }
        } else {
          // Everything else
          ImageReader reader = (ImageReader)ImageIO.getImageReadersBySuffix(entry.getExtension()).next();
          reader.setInput(ImageIO.createImageInputStream(is), false);
          int numFrames = reader.getNumImages(true);
          images = new BufferedImage[numFrames];
          for (int i = 0; i < images.length; i++) {
            images[i] = reader.read(i);
          }
        }
        retVal = (images.length > 0);

        for (int frameIdx = 0, curFrameIdx = 0; frameIdx < images.length; frameIdx++) {
          BufferedImage image = images[frameIdx];
          if (image == null) {
            retVal = false;
            break;
          }
          if (frameIndex >= 0 && frameIdx != frameIndex) {
            // skip if frame has not been requested
            continue;
          }

          // transparency detection for paletted images
          if (image.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
            boolean hasAlpha = ((IndexColorModel)image.getColorModel()).hasAlpha();
            int[] cmap = new int[256];
            int transIndex = -1;
            IndexColorModel srcCm = (IndexColorModel)image.getColorModel();
            int numColors = Math.min(1 << srcCm.getPixelSize(), cmap.length);
            int i = 0;
            for (; i < numColors; i++) {
              int alpha = hasAlpha ? srcCm.getAlpha(i) : 0xff;
              cmap[i] = (alpha << 24) |
                        (srcCm.getRed(i) << 16) |
                        (srcCm.getGreen(i) << 8) |
                        srcCm.getBlue(i);
              // marking first occurence of "Green" as transparent
              if (transIndex < 0) {
                if ((cmap[i] & 0xff000000) == 0) {
                  transIndex = i;
                  cmap[i] = 0xff00ff00;
                } else if ((cmap[i] & 0x00ffffff) == 0x0000ff00) {
                  transIndex = i;
                }
              }
            }
            for (; i < cmap.length; i++) {
              cmap[i] = 0xff000000;
            }

            // fallback to index 0 as transparent color
            if (transIndex < 0) {
              transIndex = 0;
            }

            // Adding transparency to image
            IndexColorModel cm = new IndexColorModel(8, 256, cmap, 0, getUseAlpha(), transIndex, DataBuffer.TYPE_BYTE);
            BufferedImage dstImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, cm);
            byte[] srcBuffer = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
            byte[] dstBuffer = ((DataBufferByte)dstImage.getRaster().getDataBuffer()).getData();
            System.arraycopy(srcBuffer, 0, dstBuffer, 0, srcBuffer.length);
            srcBuffer = null; dstBuffer = null;
            cmap = null;
            image = dstImage;
          }

          modelFrames.insert(listIndex + curFrameIdx, image, new Point());
          // setting required extra options
          PseudoBamFrameEntry fe2 = modelFrames.getDecoder().getFrameInfo(listIndex + curFrameIdx);
          if (entry instanceof FileResourceEntry) {
            fe2.setOption(BAM_FRAME_OPTION_PATH, entry.getActualPath().toString());
          } else {
            fe2.setOption(BAM_FRAME_OPTION_PATH, BAM_FRAME_PATH_BIFF + entry.getResourceName());
          }
          fe2.setOption(BAM_FRAME_OPTION_SOURCE_INDEX, Integer.valueOf(frameIdx));
          if (images.length > 1) {
            fe2.setOption(PseudoBamDecoder.OPTION_STRING_LABEL, entry.getResourceName() + ":" + frameIdx);
          } else {
            fe2.setOption(PseudoBamDecoder.OPTION_STRING_LABEL, entry.getResourceName());
          }
          curFrameIdx++;
        }
      } catch (Exception e) {
        retVal = false;
        e.printStackTrace();
      }
    }
    return retVal;
  }

  /** Action for "Import BAM file...": loads a complete frames/cycles structure. */
  public void framesImportBamFile()
  {
    Path[] files = getOpenFileName(this, "Import BAM file", null, false,
                                   new FileNameExtensionFilter[]{getBamFilter()}, 0);
    if (files != null && files.length > 0) {
      try {
        WindowBlocker.blockWindow(this, true);
        framesImportBam(StreamUtils.replaceFileExtension(files[0], "BAM"));
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

  /** Action for "Import BAM resource...": loads a complete frames/cycles structure. */
  public void framesImportBamResource()
  {
    ResourceEntry[] entries =
        OpenResourceDialog.showOpenDialog(this, "Import BAM resource", new String[]{"BAM"}, false);
    if (entries != null && entries.length > 0) {
      try {
        WindowBlocker.blockWindow(this, true);
        framesImportBam(entries[0]);
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

  /** Specific: Loads the whole frames/cycles structure from the specified BAM file. */
  public void framesImportBam(Path file)
  {
    framesImportBam(new FileResourceEntry(file));
  }

  /** Specific: Loads the whole frames/cycles structure from the specified BAM ResourceEntry object. */
  public void framesImportBam(ResourceEntry entry)
  {
    if (entry != null) {
      outputSetModified(true);
      boolean cancelled = false;
      boolean replace = true;
      if (!modelFrames.isEmpty()) {
        String[] options = {"Append", "Replace", "Cancel"};
        String msg = "What do you want to do with the selected BAM file?";
        int ret = JOptionPane.showOptionDialog(this, msg, "Question",
                                               JOptionPane.YES_NO_CANCEL_OPTION,
                                               JOptionPane.QUESTION_MESSAGE, null,
                                               options, options[0]);
        cancelled = (ret == 2 || ret == JOptionPane.CLOSED_OPTION);
        replace = (ret == 1);
      }
      if (!cancelled) {
        if (replace) {
          clear();
          setBamOutput(FileManager.query(Profile.getGameRoot(), entry.getResourceName()));
        }
        int frameBase = modelFrames.getSize();
        BamDecoder decoder = framesAddBam(frameBase, entry);
        if (decoder != null) {
          // initializing cycles section
          BamDecoder.BamControl control = decoder.createControl();
          int cycleBase = modelCycles.getSize();
          for (int i = 0; i < control.cycleCount(); i++) {
            int[] indices = new int[control.cycleFrameCount(i)];
            for (int j = 0; j < control.cycleFrameCount(i); j++) {
              indices[j] = control.cycleGetFrameIndexAbsolute(i, j)+frameBase;
            }
            cyclesAdd(i+cycleBase, indices);
          }
          // updating GUI controls
          updateFramesList();
          listFrames.setSelectedIndex(0);
          listFrames.ensureIndexIsVisible(listFrames.getSelectedIndex());
          listFrames.requestFocusInWindow();
          if (!modelCycles.isEmpty()) {
            listCycles.setSelectedIndex(0);
            updateCyclesList();
          }
        } else {
          JOptionPane.showMessageDialog(this, "Error while importing BAM file " + entry.getResourceName(),
                                        "Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  /** Action for "Add..."->"Add folder...": adds all supported files from the specified folder. */
  public void framesAddFolder()
  {
    framesAddFolder(getOpenPathName(this, "Select folder", null));
  }

  public void framesAddFolder(Path path)
  {
    if (path != null && Files.isDirectory(path)) {
      // preparing list of valid files
      FileNameExtensionFilter filters = getGraphicsFilters()[0];
      List<Path> validFiles = new ArrayList<>();
      try (DirectoryStream<Path> dstream = Files.newDirectoryStream(path)) {
        for (final Path file: dstream) {
          if (Files.isRegularFile(file) && filters.accept(file.toFile())) {
            validFiles.add(file);
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
        return;
      }

      // adding entries to frames list
      try {
        WindowBlocker.blockWindow(this, true);
        framesAdd(validFiles.toArray(new Path[validFiles.size()]));
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

  /** Action for "Remove": removes the selected frame entry/entries from the frames list, updates cycle structures. */
  public void framesRemove()
  {
    try {
      WindowBlocker.blockWindow(this, true);
      int[] indices = listFrames.getSelectedIndices();
      if (indices.length == modelFrames.getSize()) {
        framesRemoveAll();
      } else {
        framesRemove(indices);
      }
    } finally {
      WindowBlocker.blockWindow(this, false);
    }
  }

  /** Specific: Removes the selected frames from the frames list. */
  public void framesRemove(int[] indices)
  {
    if (indices != null && indices.length > 0) {
      outputSetModified(true);
      Arrays.sort(indices);
      int curIdx = indices[indices.length - 1] - indices.length + 1;
      for (int i = indices.length - 1; i >= 0; i--) {
        if (indices[i] >= 0 && indices[i] < modelFrames.getSize()) {
          modelFrames.remove(indices[i]);
        }
      }
      updateCyclesRemovedFrames(indices);
      updateFramesList();
      curIdx = Math.min(modelFrames.getSize() - 1, curIdx);
      listFrames.setSelectedIndex(curIdx);
      listFrames.ensureIndexIsVisible(curIdx);
      listFrames.requestFocusInWindow();
    }
  }

  /** Action for "Remove all": removes all frame entries from frames list, updatees cycle structures. */
  public void framesRemoveAll()
  {
    outputSetModified(true);
    modelFrames.clear();
    updateFramesList();
    listFrames.requestFocusInWindow();
  }

  /** Action for "Up" button next to frames list. */
  private void framesMoveUp()
  {
    outputSetModified(true);
    int[] indices = listFrames.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] > 0 && indices[i] < modelFrames.getSize()) {
        modelFrames.move(indices[i], -1);
        updateCyclesMovedFrames(indices[i], -1);
        indices[i]--;
      }
    }
    listFrames.setSelectedIndices(indices);
    updateFramesList();
    listFrames.requestFocusInWindow();
  }

  /** Action for "Down" button next to frames list. */
  private void framesMoveDown()
  {
    outputSetModified(true);
    int[] indices = listFrames.getSelectedIndices();
    for (int i = indices.length - 1; i >= 0; i--) {
      if (indices[i] >= 0 && indices[i] < modelFrames.getSize() - 1) {
        modelFrames.move(indices[i], 1);
        updateCyclesMovedFrames(indices[i], 1);
        indices[i]++;
      }
    }
    listFrames.setSelectedIndices(indices);
    updateFramesList();
    listFrames.requestFocusInWindow();
  }

  /** Action for "Drop unused frames": return number of unused frames. */
  private int framesGetUnusedFramesCount()
  {
    return framesGetUnusedFramesCount(framesGetUnusedFrames());
  }

  /** Action for "Drop unused frames": return number of unused frames. */
  private int framesGetUnusedFramesCount(BitSet framesUsed)
  {
    int retVal = 0;
    if (framesUsed != null) {
      for (int i = 0; i < modelFrames.getSize(); i++) {
        if (!framesUsed.get(i)) {
          retVal++;
        }
      }
    }
    return retVal;
  }

  /** Action  for "Drop unused frames": returns a bitset that maps the used state of all frames. */
  private BitSet framesGetUnusedFrames()
  {
    BitSet framesUsed = new BitSet(modelFrames.getSize());
    for (int i = 0; i < modelCycles.getSize(); i++) {
      PseudoBamCycleEntry cycle = modelCycles.getElementAt(i);
      for (int j = 0; j < cycle.size(); j++) {
        int idx = cycle.get(j);
        if (idx >= 0) {
          framesUsed.set(idx);
        }
      }
    }
    return framesUsed;
  }

  /** Action for "Drop unused frames": removes unused frames and adjusts cycle indices respectively. */
  private void framesDropUnusedFrames()
  {
    BitSet framesUsed = framesGetUnusedFrames();
    int count = framesGetUnusedFramesCount(framesUsed);
    if (count > 0) {
      int[] indices = new int[count];
      for (int i = 0, idx = 0; i < modelFrames.getSize(); i++) {
        if (!framesUsed.get(i)) {
          indices[idx] = i;
          idx++;
        }
      }
      framesRemove(indices);
    }
  }


  /** Action for "Add cycle": adds a new empty cycle at the current cycle index to the cycles list. */
  private void cyclesAdd()
  {
    int idx = listCycles.getSelectedIndex() + 1;
    if (idx < 0) idx = modelCycles.getSize();
    cyclesAdd(idx, new int[0]);
  }

  /** Specific: adds a new cycle with the specified frame indices at the specified position. */
  private void cyclesAdd(int index, int[] indices)
  {
    if (index < 0) index = 0; else if (index > modelCycles.getSize()) index = modelCycles.getSize();
    if (indices == null) indices = new int[0];
    modelCycles.insert(index, indices);
    listCycles.setSelectedIndex(index);
    listCycles.ensureIndexIsVisible(index);
    updateCyclesList();
  }

  /** Action for "Remove cycle": removes the selected cycles from the cycles list. */
  private void cyclesRemove()
  {
    int[] indices = listCycles.getSelectedIndices();
    if (indices.length > 0) {
      for (int i = indices.length - 1; i >= 0; i--) {
        if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
          cyclesRemove(indices[i]);
        }
      }
    }
  }

  /** Specific: removes the specified cycle from the cycles list. */
  private void cyclesRemove(int index)
  {
    if (index >= 0 && index < modelCycles.getSize()) {
      modelCycles.remove(index, 1);
      if (index > 0) {
        index--;
      }
      if (!modelCycles.isEmpty()) {
        listCycles.setSelectedIndex(index);
      }
      updateCyclesList();
    }
  }

  /** Action for "Remove all": removes all cycles from the cycles list. */
  private void cyclesRemoveAll()
  {
    modelCycles.clear();
    updateCyclesList();
  }

  /** Action for "Up" button next to cycles list. */
  private void cyclesMoveUp()
  {
    int[] indices = listCycles.getSelectedIndices();
    if (indices.length > 0) {
      for (int i = 0; i < indices.length; i++) {
        if (indices[i] > 0 && indices[i] < modelCycles.getSize()) {
          modelCycles.move(indices[i], -1);
          indices[i]--;
        }
      }
      listCycles.setSelectedIndices(indices);
      updateCyclesList();
      listCycles.requestFocusInWindow();
    }
  }

  /** Action for "Down" button next to cycles list. */
  private void cyclesMoveDown()
  {
    int[] indices = listCycles.getSelectedIndices();
    if (indices.length > 0) {
      for (int i = indices.length - 1; i >= 0; i--) {
        if (indices[i] >= 0 && indices[i] < modelCycles.getSize() - 1) {
          modelCycles.move(indices[i], 1);
          indices[i]++;
        }
      }
      listCycles.setSelectedIndices(indices);
      updateCyclesList();
      listCycles.requestFocusInWindow();
    }
  }


  /** Action for macro "Selected cycle"->"Assign all frames": puts all available frames into the selected cycle (sorted). */
  private void macroAssignFrames()
  {
    int[] indices = listCycles.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
        modelCurCycle.setCycle(indices[i]);
        modelCurCycle.clear();
        int[] frames = new int[modelFrames.getSize()];
        for (int j = 0; j < frames.length; j++) {
          frames[j] = j;
        }
        modelCurCycle.add(frames);
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Remove all frames": Removes all frame indices. */
  private void macroRemoveAllFrames()
  {
    int[] indices = listCycles.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
        modelCurCycle.setCycle(indices[i]);
        modelCurCycle.clear();
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Duplicate cycle": Adds a duplicate below the selected cycle. */
  private void macroDuplicateCycle()
  {
    int[] indices = listCycles.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
        int[] frames = new int[modelCycles.getElementAt(indices[i]).size()];
        for (int j = 0; j < frames.length; j++) {
          frames[j] = modelCycles.getElementAt(indices[i]).get(j);
        }
        modelCycles.insert(indices[i]+1, frames);
        for (int j = i; j < indices.length; j++) {
          indices[j]++;
        }
      }
    }
    listCycles.setSelectedIndices(indices);
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Duplicate each frame": Duplicates each frame in the selected cycle. */
  private void macroDuplicateFrames()
  {
    int[] indices = listCycles.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
        modelCurCycle.setCycle(indices[i]);
        int frameIdx = modelCurCycle.getSize() - 1;
        while (frameIdx >= 0) {
          modelCurCycle.insert(frameIdx+1, modelCurCycle.getControl().cycleGetFrameIndexAbsolute(frameIdx));
          frameIdx--;
        }
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Sort frames": Sorts frame indices by value. */
  private void macroSortFrames()
  {
    int[] indices = listCycles.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
        int[] frames = new int[modelCycles.getElementAt(indices[i]).size()];
        for (int j = 0; j < frames.length; j++) {
          frames[j] = modelCycles.getElementAt(indices[i]).get(j);
        }
        Arrays.sort(frames);
        for (int j = 0; j < frames.length; j++) {
          modelCycles.getElementAt(indices[i]).set(j, frames[j]);
        }
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Reverse frames order": Reverses the current order of the frame indices. */
  private void macroReverseFramesOrder()
  {
    int[] indices = listCycles.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
        int[] frames = new int[modelCycles.getElementAt(indices[i]).size()];
        int len = modelCycles.getElementAt(indices[i]).size();
        for (int j = 0; j < len; j++) {
          frames[j] = modelCycles.getElementAt(indices[i]).get(len - j - 1);
        }
        for (int j = 0; j < len; j++) {
          modelCycles.getElementAt(indices[i]).set(j, frames[j]);
        }
      }
    }
    updateCyclesList();
  }

  /** Action for macro "All cycles"->"Remove all frames": Removes all frame indices. */
  private void macroRemoveAllFramesGlobal()
  {
    if (!modelCycles.isEmpty()) {
      String msg = "Do you really want to remove all frame indices from each cycle?";
      int ret = JOptionPane.showConfirmDialog(this, msg, "Question", JOptionPane.YES_NO_OPTION,
                                              JOptionPane.QUESTION_MESSAGE);
      if (ret == JOptionPane.YES_OPTION) {
        for (int i = 0; i < modelCycles.getSize(); i++) {
          modelCycles.getElementAt(i).clear();
        }
        updateCyclesList();
      }
    }
  }

  /** Action for macro "All cycles"->"Reverse cycles order": Reverses the order of all cycle entries. */
  private void macroReverseCyclesOrder()
  {
    if (!modelCycles.isEmpty()) {
      int idx = listCycles.getSelectedIndex();
      int[][] entries = new int[modelCycles.getSize()][];
      for (int i = 0; i < entries.length; i++) {
        entries[i] = new int[modelCycles.getElementAt(i).size()];
        for (int j = 0; j < entries[i].length; j++) {
          entries[i][j] = modelCycles.getElementAt(i).get(j);
        }
      }
      modelCycles.clear();
      for (int i = 0; i < entries.length; i++) {
        modelCycles.add(entries[entries.length - i - 1]);
      }
      if (idx >= 0) {
        listCycles.setSelectedIndex(modelCycles.getSize() - idx - 1);
        listCycles.ensureIndexIsVisible(listCycles.getSelectedIndex());
      }
      updateCyclesList();
    }
  }


  /** Action for "Right" button between available frames/current cycle: adds selected frames to the current cycle list. */
  private void currentCycleAdd()
  {
    int[] indices = listFramesAvail.getSelectedIndices();
    if (indices != null && indices.length > 0) {
      Pair<Integer> dstBounds = getIndexBounds(listCurCycle.getSelectedIndices());
      int dstIdx = dstBounds.getSecond() + 1;
      modelCurCycle.insert(dstIdx, indices);
      modelCycles.contentChanged(modelCurCycle.getCycle());
      listFramesAvail.setSelectedIndices(new int[]{getIndexBounds(indices).getSecond()});
      listCurCycle.setSelectedIndex(dstIdx + indices.length - 1);
      updateCurrentCycle();
    }
  }

  /** Action for "Left" button between available frames/current cycle: removes selected frames from the current cycle list. */
  private void currentCycleRemove()
  {
    int[] indices = listCurCycle.getSelectedIndices();
    if (indices != null && indices.length > 0) {
      for (int i = indices.length - 1; i >= 0; i--) {
        modelCurCycle.remove(indices[i], 1);
      }
      modelCycles.contentChanged(listCycles.getSelectedIndex());
      listCurCycle.setSelectedIndices(new int[0]);
      updateCurrentCycle();
    }
  }

  /** Action for "Up" button of current cycle list. */
  private void currentCycleMoveUp()
  {
    int[] indices = listCurCycle.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] > 0 && indices[i] < modelCurCycle.getSize()) {
        modelCurCycle.move(indices[i], -1);
        indices[i]--;
      }
    }
    modelCycles.contentChanged(listCycles.getSelectedIndex());
    listCurCycle.setSelectedIndices(indices);
    listCurCycle.requestFocusInWindow();
    updateCurrentCycle();
  }

  /** Action for "Down" button of current cycle list. */
  private void currentCycleMoveDown()
  {
    int[] indices = listCurCycle.getSelectedIndices();
    for (int i = indices.length - 1; i >= 0; i--) {
      if (indices[i] >= 0 && indices[i] < modelCurCycle.getSize() - 1) {
        modelCurCycle.move(indices[i], 1);
        indices[i]++;
      }
    }
    modelCycles.contentChanged(listCycles.getSelectedIndex());
    listCurCycle.setSelectedIndices(indices);
    listCurCycle.requestFocusInWindow();
    updateCurrentCycle();
  }


  /** Action for "Play/Pause": start/pause playback without resetting current frame. */
  private void previewPlay()
  {
    if (previewIsPlaying()) {
      timer.stop();
      isPreviewPlaying = false;
      bPreviewPlay.setText("Play");
      bPreviewPlay.setIcon(Icons.getIcon(Icons.ICON_PLAY_16));
      bPreviewStop.setEnabled(true);
      updatePreview();
    } else {
      isPreviewPlaying = true;
      bPreviewPlay.setText("Pause");
      bPreviewPlay.setIcon(Icons.getIcon(Icons.ICON_PAUSE_16));
      bPreviewStop.setEnabled(true);
      timer.start();
    }
  }

  /** Action for "Stop": stops playback and resets current frame to 1. */
  private void previewStop()
  {
    timer.stop();
    isPreviewPlaying = false;
    bPreviewPlay.setText("Play");
    bPreviewPlay.setIcon(Icons.getIcon(Icons.ICON_PLAY_16));
    bPreviewStop.setEnabled(false);
    if (bamControlPreview != null) {
      bamControlPreview.cycleSetFrameIndex(0);
    }
    updatePreview();
  }

  /** Returns whether playback is active. */
  private boolean previewIsPlaying()
  {
    return isPreviewPlaying;
  }

  /** Action for "Next cycle" button: selects next cycle index if available. */
  private void previewCycleUp()
  {
    if (bamControlPreview.cycleGet() < bamControlPreview.cycleCount() - 1) {
      bamControlPreview.cycleSet(bamControlPreview.cycleGet() + 1);
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Action for "Previous cycle" button: selects previous cycle index if available. */
  private void previewCycleDown()
  {
    if (bamControlPreview.cycleGet() > 0) {
      bamControlPreview.cycleSet(bamControlPreview.cycleGet() - 1);
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Action for "Next frame" button: selects next frame index if available. */
  private void previewFrameUp()
  {
    if (bamControlPreview.cycleGetFrameIndex() < bamControlPreview.cycleFrameCount() - 1) {
      bamControlPreview.cycleSetFrameIndex(bamControlPreview.cycleGetFrameIndex() + 1);
      updatePreview();
    }
  }

  /** Action for "Previous frame" button: selects previous frame index if available. */
  private void previewFrameDown()
  {
    if (bamControlPreview.cycleGetFrameIndex() > 0) {
      bamControlPreview.cycleSetFrameIndex(bamControlPreview.cycleGetFrameIndex() - 1);
      updatePreview();
    }
  }

  /** Returns the current playback mode. */
  private int previewGetMode()
  {
    return cbPreviewMode.getSelectedIndex();
  }

  /** Sets the current playback mode. */
  private void previewSetMode(int mode)
  {
    if (mode >= 0 && mode < cbPreviewMode.getItemCount()) {
      cbPreviewMode.setSelectedIndex(mode);
    }
  }

  /** Sets a new frame rate. */
  private void previewSetFrameRate(double fps)
  {
    try {
      sPreviewFps.setValue(Double.valueOf(fps));
      currentFps = (Double)sPreviewFps.getValue();
      timer.setDelay((int)(1000.0 / currentFps));
    } catch (IllegalArgumentException e) {
    }
  }

  /** Returns whether the preview is in zoom mode. */
  private boolean previewIsZoomed()
  {
    return cbPreviewZoom.isSelected();
  }

  /** Sets the zoom mode of the preview. */
  private void previewSetZoom(boolean set)
  {
    cbPreviewZoom.setSelected(set);
    previewPrepare(false);
  }

  /** Returns whether markers are visible. */
  private boolean previewIsMarkerVisible()
  {
    return cbPreviewShowMarker.isSelected();
  }

  /** Sets the visibility state of markers. */
  private void previewSetMarkerVisible(boolean set)
  {
    cbPreviewShowMarker.setSelected(set);
    previewPrepare(false);
  }

  /**
   * Advances the animation by one step, depending on the current playback mode.
   *
   * @return {@code false} if no more advancements can be done.
   */
  private boolean previewAdvanceAnimation()
  {
    boolean retVal = true;
    int retries = bamControlPreview.cycleCount();
    // advancing frame depends on current playback mode
    do {
      if (bamControlPreview.cycleGetFrameIndex() >= bamControlPreview.cycleFrameCount() - 1) {
        switch (previewGetMode()) {
          case MODE_CURRENT_CYCLE_ONCE:
            bamControlPreview.cycleSetFrameIndex(0);
            retVal = false;
            break;
          case MODE_CURRENT_CYCLE_LOOPED:
            bamControlPreview.cycleSetFrameIndex(0);
            break;
          case MODE_ALL_CYCLES_ONCE:
            if (bamControlPreview.cycleGet() == bamControlPreview.cycleCount() - 1) {
              bamControlPreview.cycleSet(0);
              retVal = false;
            } else {
              bamControlPreview.cycleSet(bamControlPreview.cycleGet() + 1);
            }
            bamControlPreview.cycleSetFrameIndex(0);
            break;
          case MODE_ALL_CYCLES_LOOPED:
            if (bamControlPreview.cycleGet() == bamControlPreview.cycleCount() - 1) {
              bamControlPreview.cycleSet(0);
            } else {
              bamControlPreview.cycleSet(bamControlPreview.cycleGet() + 1);
            }
            bamControlPreview.cycleSetFrameIndex(0);
            break;
          default:
            retVal = false;
        }
      } else {
        bamControlPreview.cycleSetFrameIndex(bamControlPreview.cycleGetFrameIndex() + 1);
      }
    } while (retVal && retries-- > 0 && bamControlPreview.cycleGetFrameIndex() >= bamControlPreview.cycleFrameCount());

    updatePreview();
    return retVal;
  }

  /** Resets the current animation (can used as a reaction to the IsModified flag). */
  private void previewValidate()
  {
    previewPrepare(true);
    updatePreview();
  }

  /** Updates the BAM control instance for previews. */
  private void previewInitControl()
  {
    if (bamControlPreview == null) {
      bamControlPreview = bamDecoderFinal.createControl();
      bamControlPreview.setMode(BamDecoder.BamControl.Mode.SHARED);
      bamControlPreview.setSharedPerCycle(true);
    } else {
      int idx = bamControlPreview.cycleGet();
      if (idx < 0) {
        bamControlPreview.cycleSet(0);
        bamControlPreview.cycleReset();
      } else if (idx >= bamControlPreview.cycleCount()) {
        bamControlPreview.cycleSet(bamControlPreview.cycleCount() - 1);
        bamControlPreview.cycleReset();
      }
    }
  }

  /** Initializes the preview. */
  private void previewPrepare(boolean update)
  {
    if (update) {
      // updating finalized BAM structure
      try {
        updateFilteredBamDecoder(getBamVersion(), false);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    previewInitControl();

    int zoom = previewIsZoomed() ? 2 : 1;
    Dimension dim = bamControlPreview.getSharedDimension();
    if (previewCanvas == null ||
        previewCanvas.getWidth() != dim.width||
        previewCanvas.getHeight() != dim.height) {
      previewCanvas = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
    }
    boolean sizeChanged = false;
    dim.width += 2; dim.height += 2;    // adjusting for preview markers
    if (rcPreview.getImage() == null ||
        rcPreview.getImage().getWidth(null) != dim.width ||
        rcPreview.getImage().getHeight(null) != dim.height) {
      rcPreview.setImage(ColorConvert.createCompatibleImage(dim.width, dim.height, true));
      sizeChanged = true;
    }
    if (rcPreview.getPreferredSize().width != dim.width*zoom ||
        rcPreview.getPreferredSize().height != dim.height*zoom) {
      rcPreview.setPreferredSize(new Dimension(dim.width*zoom, dim.height*zoom));
      rcPreview.setMinimumSize(rcPreview.getPreferredSize());
      rcPreview.setSize(rcPreview.getPreferredSize());
      sizeChanged = true;
    }
    if (sizeChanged) {
      rcPreview.invalidate();
      scrollPreview.setMinimumSize(rcPreview.getPreferredSize());
      scrollPreview.invalidate();
      scrollPreview.getParent().validate();
    }
    previewDisplay();
  }

  /** Displays the current frame on screen. */
  private synchronized void previewDisplay()
  {
    if (bamControlPreview != null && previewCanvas != null && rcPreview.getImage() != null) {
      // clearing old content
      Graphics2D g = previewCanvas.createGraphics();
      try {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
      } finally {
        g.dispose();
        g = null;
      }

      // producing frame data
      bamControlPreview.cycleGetFrame(previewCanvas);

      // drawing markers
      g = (Graphics2D)rcPreview.getImage().getGraphics();
      try {
        // clearing old content
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, rcPreview.getImage().getWidth(null), rcPreview.getImage().getHeight(null));

        // rendering frame
        g.drawImage(previewCanvas, 1, 1, null);

        if (previewIsMarkerVisible()) {
          Point origin = bamControlPreview.getSharedOrigin();
          int frameIdx = bamControlPreview.cycleGetFrameIndexAbsolute();
          int centerX = bamDecoderFinal.getFrameInfo(frameIdx).getCenterX();
          int centerY = bamDecoderFinal.getFrameInfo(frameIdx).getCenterY();
          int imgWidth = bamDecoderFinal.getFrameInfo(frameIdx).getWidth();
          int imgHeight = bamDecoderFinal.getFrameInfo(frameIdx).getHeight();

          // drawing bounding box
          g.setColor(new Color(0x00A000));
          int x = -origin.x - centerX;
          int y = -origin.y - centerY;
          g.drawRect(x, y, imgWidth + 1, imgHeight + 1);

          // drawing center marker (if frame is big enough)
          int msize = 8;
          if (msize > imgWidth) msize = imgWidth;
          if (imgWidth > imgHeight) msize = imgHeight;
          if (imgWidth > 3 && imgHeight > 3) {
            x = -origin.x + 1;
            y = -origin.y + 1;
            g.setStroke(new BasicStroke(3.0f));
            g.setColor(Color.BLACK);
            g.drawLine(x - 4, y, x + 4, y);
            g.drawLine(x, y - 4, x, y + 4);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(Color.RED);
            g.drawLine(x - 4, y, x + 4, y);
            g.drawLine(x, y - 4, x, y + 4);
          }
        }
      } finally {
        g.dispose();
        g = null;
      }
    }

    rcPreview.repaint();
  }


  /** Inserts a new filter into the filters list. */
  private BamFilterBase filterAdd()
  {
    BamFilterFactory.FilterInfo fi = (BamFilterFactory.FilterInfo)cbFiltersAdd.getSelectedItem();
    if (fi != null) {
      return filterAdd(fi);
    }
    return null;
  }

  /** Inserts the specified filter into the filters list. */
  private BamFilterBase filterAdd(BamFilterFactory.FilterInfo info)
  {
    if (info != null) {
      int idx = listFilters.getSelectedIndex();

      // check if the filter can be inserted
      Class<? extends BamFilterBase> filterClass = info.getFilterClass();
      if (BamFilterBaseOutput.class.isAssignableFrom(filterClass)) {
        for (int i = 0; i < modelFilters.getSize(); i++) {
          BamFilterBase filter = modelFilters.get(i);
          if (filter.getClass().equals(filterClass)) {
            listFilters.setSelectedIndex(i);
            return null;
          }
        }
      }

      // insert new filter
      BamFilterBase filter = BamFilterFactory.createInstance(this, filterClass);
      if (filter != null) {
        filter.addChangeListener(this);
        modelFilters.add(idx+1, filter);
        listFilters.setSelectedIndex(idx+1);
        outputSetModified(true);
      } else {
        JOptionPane.showMessageDialog(this, "Unable to create selected filter.", "Error", JOptionPane.ERROR_MESSAGE);
      }
      return filter;
    }
    return null;
  }

  /** Removes the currently selected filter. */
  private void filterRemove()
  {
    filterRemove(listFilters.getSelectedIndex());
  }

  /** Removes the specified filter from the filters list. */
  private void filterRemove(int index)
  {
    if (index >= 0 && index < modelFilters.size()) {
      BamFilterBase filter = modelFilters.get(index);
      filter.close();
      modelFilters.remove(index);
      if (index < modelFilters.size()) {
        listFilters.setSelectedIndex(index);
      } else {
        listFilters.setSelectedIndex(index - 1);
      }
      updateFilterList();
      outputSetModified(true);
    }
  }

  /** Removes all filters from the filters list. */
  private void filterRemoveAll()
  {
    listFilters.setSelectedIndex(-1);
    for (int i = modelFilters.size() - 1; i >= 0; i--) {
      BamFilterBase filter = modelFilters.get(i);
      filter.close();
    }
    modelFilters.clear();
    updateFilterList();
    outputSetModified(true);
  }

  /** Moves the currently selected filter up. */
  private void filterMoveUp()
  {
    int index = listFilters.getSelectedIndex();
    if (index > 0 && index < modelFilters.size()) {
      BamFilterBase filter = modelFilters.get(index - 1);
      modelFilters.set(index - 1, modelFilters.get(index));
      modelFilters.set(index, filter);
    }
    listFilters.setSelectedIndex(index - 1);
    updateFilterList();
    listFilters.requestFocusInWindow();
    outputSetModified(true);
  }

  /** Moves the currently selected filter down. */
  private void filterMoveDown()
  {
    int index = listFilters.getSelectedIndex();
    if (index >= 0 && index < modelFilters.size() - 1) {
      BamFilterBase filter = modelFilters.get(index + 1);
      modelFilters.set(index + 1, modelFilters.get(index));
      modelFilters.set(index, filter);
    }
    listFilters.setSelectedIndex(index + 1);
    updateFilterList();
    listFilters.requestFocusInWindow();
    outputSetModified(true);
  }

  /** Returns whether preview markers have been enabled. */
  private boolean filterIsMarkerVisible()
  {
    return cbFiltersShowMarker.isSelected();
  }

  /** Returns the currently selected frame index. */
  private int filterGetPreviewFrameIndex()
  {
    return ((Integer)sFiltersPreviewFrame.getValue()).intValue();
  }

  private void filterUpdatePreviewFrameIndex()
  {
    SpinnerNumberModel model = (SpinnerNumberModel)sFiltersPreviewFrame.getModel();
    int max = ((Integer)model.getMaximum()).intValue();
    int cur = ((Integer)model.getValue()).intValue();
    if (max != listFrameEntries.get(BAM_ORIGINAL).size()) {
      max = listFrameEntries.get(BAM_ORIGINAL).size();
      if (cur >= max) {
        cur = Math.max(max - 1, 0);
      }
      model.setMaximum(Integer.valueOf(max));
      model.setValue(Integer.valueOf(cur));
    }
  }

  /** Prepares the specified frame for quick preview. */
  private void filterSetPreviewFrame(int frameIdx, boolean complete)
  {
    PseudoBamFrameEntry entry = getFilteredBamFrame(getBamVersion(), filterGetPreviewFrameIndex(), complete);
    if (entry.getFrame().getWidth() != (rcFiltersPreview.getImage().getWidth(null) - 2) ||
        entry.getFrame().getHeight() != (rcFiltersPreview.getImage().getHeight(null) - 2)) {
      rcFiltersPreview.setImage(new BufferedImage(entry.getFrame().getWidth() + 2,
                                                  entry.getFrame().getHeight() + 2,
                                                  BufferedImage.TYPE_INT_ARGB));
    }
    filterDisplay(entry);
  }

  /** Displays the specified entry in the filters quick preview. */
  private void filterDisplay(PseudoBamFrameEntry entry)
  {
    if (rcFiltersPreview.getImage() != null && entry != null && entry.getFrame() != null) {
      int canvasWidth = rcFiltersPreview.getImage().getWidth(null);
      int canvasHeight = rcFiltersPreview.getImage().getHeight(null);
      int centerX = entry.getCenterX();
      int centerY = entry.getCenterY();

      Graphics2D g = (Graphics2D)rcFiltersPreview.getImage().getGraphics();
      try {
        // clearing old content
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, canvasWidth, canvasHeight);

        // rendering frame
        g.drawImage(entry.getFrame(), 1, 1, null);

        if (filterIsMarkerVisible()) {
          // drawing bounding box
          g.setColor(new Color(0x00A000));
          g.drawRect(0, 0, canvasWidth - 1, canvasHeight - 1);

          // drawing center marker (if frame is big enough)
          int msize = 8;
          if (msize > canvasWidth) msize = canvasWidth;
          if (canvasWidth > canvasHeight) msize = canvasHeight;
          if (canvasWidth > 3 && canvasHeight > 3) {
            g.setStroke(new BasicStroke(3.0f));
            g.setColor(Color.BLACK);
            g.drawLine(centerX - 4, centerY, centerX + 4, centerY);
            g.drawLine(centerX, centerY - 4, centerX, centerY + 4);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(Color.RED);
            g.drawLine(centerX - 4, centerY, centerX + 4, centerY);
            g.drawLine(centerX, centerY - 4, centerX, centerY + 4);
          }
        }
      } finally {
        g.dispose();
        g = null;
      }
      rcFiltersPreview.repaint();
    }
  }

  /**
   * Triggers the update function for each filter in the list, enabling them to react to the
   * current state of the converter.
   */
  private void filterUpdateControls()
  {
    for (int i = 0; i < modelFilters.size(); i++) {
      modelFilters.get(i).updateControls();
    }
  }


  /** Action for selecting BAM version in export section: 0=BAM v1, 1=BAM v2. */
  private void setBamVersion(int index)
  {
    String s;
    switch (index) {
      case VERSION_BAMV1:
        s = "V1";
        break;
      case VERSION_BAMV2:
        s = "V2";
        break;
      default:
        s = null;
    }
    if (s != null) {
      cbVersion.setSelectedIndex(index);
      // setting BAM version-specific export controls
      CardLayout cl = (CardLayout)pFramesOptionsVersion.getLayout();
      cl.show(pFramesOptionsVersion, s);
      // setting enabled state of frame compression flag in info box
      cbCompressFrame.setEnabled(index == 0);
    }
    outputSetModified(true);
  }

  /** Action for BAM version help: Displays a message dialog with information. */
  private void showVersionHelp()
  {
    final String helpMsg =
        "\"Legacy (v1)\" is the old and proven BAM format supported by all available\n" +
        "Infinity Engine games. It uses a global 256 color table for all frames\n" +
        "and supports simple bitmasked transparency.\n\n" +
        "\"PVRZ-based (v2)\" uses a new BAM format introduced by BG:EE. Graphics data\n" +
        "is stored separately in PVRZ files. Each frame supports interpolated alpha\n" +
        "transitions and is not limited to a global 256 color table.\n" +
        "It is only supported by the Enhanced Editions of the Baldur's Gate series.";
    JOptionPane.showMessageDialog(this, helpMsg, "About BAM versions", JOptionPane.INFORMATION_MESSAGE);
  }

  /** Action for BAM v2 compression help: Displays a message dialog with information. */
  private void showCompressionHelp()
  {
    final String helpMsg =
        "\"DXT1\" provides the highest compression ratio. It supports only 1 bit alpha\n" +
        "(i.e. either no or full transparency) and is the preferred type for TIS or MOS resources.\n\n" +
        "\"DXT5\" provides an average compression ratio. It features interpolated\n" +
        "alpha transitions and is the preferred type for BAM resources.\n\n" +
        "\"Auto\" selects the most appropriate compression type based on the input data.";
    JOptionPane.showMessageDialog(this, helpMsg, "About compression types", JOptionPane.INFORMATION_MESSAGE);
  }

  /** Action for "Compress BAM" in BAM v1 export. */
  private void updateCompressBam()
  {
    Boolean b = Boolean.valueOf(cbCompressBam.isSelected());
    getBamDecoder(BAM_ORIGINAL).setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, b);
  }

  /** Specify a BAM output file. */
  private Path setBamOutput()
  {
    Path rootPath = null;
    if (bamOutputFile != null) {
      rootPath = FileManager.resolve(bamOutputFile);
    }
    Path outFile = getSaveFileName(this, "Specify output file", rootPath,
                                   new FileNameExtensionFilter[]{getBamFilter()}, 0);
    if (outFile != null) {
      outFile = StreamUtils.replaceFileExtension(outFile, "BAM");
      bamOutputFile = outFile;
    }

    return outFile;
  }


  /** Returns the min/max values from the specified array of indices in a Pair object. */
  private Pair<Integer> getIndexBounds(int[] indices)
  {
    Pair<Integer> retVal = new Pair<Integer>(Integer.valueOf(-1), Integer.valueOf(-1));
    if (indices != null && indices.length > 0) {
      retVal.setFirst(Integer.valueOf(Integer.MAX_VALUE));
      retVal.setSecond(Integer.valueOf(Integer.MIN_VALUE));
      for (int i = 0; i < indices.length; i++) {
        if (indices[i] < retVal.getFirst()) {
          retVal.setFirst(Integer.valueOf(indices[i]));
        }
        if (indices[i] > retVal.getSecond()) {
          retVal.setSecond(Integer.valueOf(indices[i]));
        }
      }
    }
    return retVal;
  }

  /** Attempts to find the most appropriate DXT compression type based on the source frames. */
  private DxtEncoder.DxtType getAutoDxtType()
  {
    DxtEncoder.DxtType dxtType = DxtEncoder.DxtType.DXT1;

    PseudoBamControl control = bamDecoder.createControl();
    control.setMode(BamControl.Mode.SHARED);
    control.setSharedPerCycle(false);
    Dimension dim = control.getSharedDimension();
    control.setMode(BamControl.Mode.INDIVIDUAL);
    BufferedImage canvas = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
    boolean typeFound = false;
    for (int i = 0; i < bamDecoder.frameCount(); i++) {
      Graphics2D g = canvas.createGraphics();
      try {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
      } finally {
        g.dispose();
        g = null;
      }
      bamDecoder.frameGet(control, i, canvas);
      dim.width = bamDecoder.getFrameInfo(i).getWidth();
      dim.height = bamDecoder.getFrameInfo(i).getHeight();
      int[] buffer = ((DataBufferInt)canvas.getRaster().getDataBuffer()).getData();
      if (buffer != null) {
        for (int y = 0; y < dim.height; y++) {
          int ofs = y*canvas.getWidth();
          for (int x = 0; x < dim.width; x++, ofs++) {
            if ((buffer[ofs] & 0xff000000) != 0xff000000 && (buffer[ofs] & 0xff000000) != 0) {
              dxtType = DxtEncoder.DxtType.DXT5;
              typeFound = true;
              break;
            }
          }
          if (typeFound) break;
        }
      }
      buffer = null;
      if (typeFound) break;
    }
    canvas.flush();
    canvas = null;
    control = null;

    return dxtType;
  }


  private List<String> convert()
  {
    List<String> result = new Vector<String>(2);
    try {
      updateFilteredBamDecoder(getBamVersion(), false);
      List<BamFilterBaseOutput> outList = createOutputFilterList();
      if (outList != null && !outList.isEmpty()) {
        for (int idx = 0; idx < outList.size(); idx++) {
          // processing output filter
          if (!outList.get(idx).process(bamDecoderFinal)) {
            throw new Exception("Conversion failed.");
          }
        }
      } else {
        throw new Exception("No output filter specified.");
      }
      result.add("Conversion finished successfully.");
    } catch (Exception e) {
      e.printStackTrace();
      result.add(null);
      result.add(String.format("Error while exporting BAM files.\n(%s)", e.getMessage()));
    }
    return result;
  }


  /** Returns whether the output/preview BAM has been modified. */
  private boolean outputIsModified()
  {
    return isPreviewModified;
  }

  /** Defines whether the internal BAM structure has changed in any way. */
  private void outputSetModified(boolean isModified)
  {
    isPreviewModified = isModified;
  }


  /** Returns a single filtered BAM frame. Set "complete" to recreate the whole filter chain. */
  private PseudoBamFrameEntry getFilteredBamFrame(int bamVersion, int frameIdx, boolean complete)
  {
    int curFilterIdx = listFilters.getSelectedIndex();

    // recreating filtered preview
    if (complete) {
      updateFinalBamFrame(bamVersion, frameIdx);

      // processing each filter that exists before the selected filter
      PseudoBamFrameEntry entry = entryFilterPreview;
      for (int i = 0; i < curFilterIdx; i++) {
        if (modelFilters.get(i) instanceof BamFilterBase) {
          BamFilterBase filter = modelFilters.get(i);
          entry = filter.updatePreview(entry);
        }
      }
      entryFilterPreview.setFrame(entry.getFrame());
      entryFilterPreview.setCenterX(entry.getCenterX());
      entryFilterPreview.setCenterY(entry.getCenterY());
    }

    // updating currently selected filter
    PseudoBamFrameEntry entry = entryFilterPreview;
    if (curFilterIdx >= 0 && curFilterIdx < modelFilters.size()) {
      entry = new PseudoBamFrameEntry(ColorConvert.cloneImage(entryFilterPreview.getFrame()),
                                      entryFilterPreview.getCenterX(),
                                      entryFilterPreview.getCenterY());
      BamFilterBase filter = modelFilters.get(curFilterIdx);
      if (filter != null) {
        entry = filter.updatePreview(entry);
      }
    }

    return entry;
  }


  /** Updates the final BAM structure in bamDecoderFinal. */
  private void updateFilteredBamDecoder(int bamVersion, boolean force) throws Exception
  {
    if (outputIsModified() || force) {
      outputSetModified(false);

      updateFinalBamDecoder(bamVersion);

      // Processing each filter sequentially
      List<BamFilterBase> filters = createFilterList(false);
      if (filters != null) {
        for (int idx = 0; idx < filters.size(); idx++) {
          if (filters.get(idx) instanceof BamFilterBaseColor) {
            // processing color filter
            try {
              BamFilterBaseColor filter = (BamFilterBaseColor)filters.get(idx);
              for (int frameIdx = 0; frameIdx < listFrameEntries.get(BAM_FINAL).size(); frameIdx++) {
                BufferedImage image = filter.process(listFrameEntries.get(BAM_FINAL).get(frameIdx).getFrame());
                if (image != null) {
                  listFrameEntries.get(BAM_FINAL).get(frameIdx).setFrame(image);
                  image = null;
                } else {
                  throw new Exception();
                }
              }
            } catch (Exception e) {
              e.printStackTrace();
              throw e;
            }
          } else if (filters.get(idx) instanceof BamFilterBaseTransform) {
            // processing transform filter
            try {
              BamFilterBaseTransform filter = (BamFilterBaseTransform)filters.get(idx);
              for (int frameIdx = 0; frameIdx < listFrameEntries.get(BAM_FINAL).size(); frameIdx++) {
                PseudoBamFrameEntry entry = filter.process(listFrameEntries.get(BAM_FINAL).get(frameIdx));
                if (entry != null) {
                  if (entry != listFrameEntries.get(BAM_FINAL).get(frameIdx)) {
                    listFrameEntries.get(BAM_FINAL).set(frameIdx, entry);
                  }
                } else {
                  throw new Exception();
                }
              }
            } catch (Exception e) {
              e.printStackTrace();
              throw e;
            }
          } else if (filters.get(idx) instanceof BamFilterBaseOutput) {
            // skipping output filter
          } else {
            if (filters.get(idx) != null) {
              System.err.println(String.format("Unrecognized filter at index %d: %s", idx, filters.get(idx)));
            } else {
              System.err.println(String.format("null filter at index %d", idx));
            }
          }
        }
      }
    }
  }


  /** Creates a sorted list including all selected filters in the post-processing tab. */
  private List<BamFilterBase> createFilterList(boolean includeOutputFilters)
  {
    List<BamFilterBase> retVal = new ArrayList<BamFilterBase>();
    List<BamFilterBase> outFilters = new ArrayList<BamFilterBase>();
    for (int i = 0; i < modelFilters.size(); i++) {
      BamFilterBase filter = modelFilters.get(i);
      if (filter instanceof BamFilterBaseOutput) {
        outFilters.add(filter);
      } else {
        retVal.add(filter);
      }
    }

    if (includeOutputFilters) {
      if (outFilters.isEmpty()) {
        outFilters.add(BamFilterFactory.createInstance(this, BamFilterOutputDefault.class));
      }
      for (int i = 0; i < outFilters.size(); i++) {
        retVal.add(outFilters.get(i));
      }
    }

    return retVal;
  }

  /** Creates a list of selected output filters only. */
  private List<BamFilterBaseOutput> createOutputFilterList()
  {
    List<BamFilterBaseOutput> retVal = new ArrayList<BamFilterBaseOutput>();
    for (int i = 0; i < modelFilters.size(); i++) {
      if (modelFilters.get(i) instanceof BamFilterBaseOutput) {
        retVal.add((BamFilterBaseOutput)modelFilters.get(i));
      }
    }
    if (retVal.isEmpty()) {
      retVal.add((BamFilterBaseOutput)BamFilterFactory.createInstance(this, BamFilterOutputDefault.class));
    }

    return retVal;
  }

  /**
   * Creates a new BAM structure from the existing structure that is compatible with the
   * specified target BAM version.
   */
  private void updateFinalBamDecoder(int bamVersion) throws Exception
  {
    listFrameEntries.get(BAM_FINAL).clear();

    if (bamVersion == VERSION_BAMV1 || bamVersion == VERSION_BAMV2) {
      List<PseudoBamFrameEntry> srcListFrames = listFrameEntries.get(BAM_ORIGINAL);
      List<PseudoBamFrameEntry> dstListFrames = listFrameEntries.get(BAM_FINAL);

      // copying global options
      String[] options = bamDecoder.getOptionNames();
      for (int i = 0; i < options.length; i++) {
        bamDecoderFinal.setOption(options[i], bamDecoder.getOption(options[i]));
      }

      if (bamVersion == VERSION_BAMV1) {
        // BAM v1: creating paletted version of each source frame
        paletteDialog.updateGeneratedPalette();

        final int Green = 0x0000ff00;
        bamDecoderFinal.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX,
                                  Integer.valueOf(paletteDialog.getRleIndex()));
        bamDecoderFinal.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED,
                                  Boolean.valueOf(isBamV1Compressed()));
        // preparing palette
        int[] palette = paletteDialog.getPalette(paletteDialog.getPaletteType());
        int threshold = getUseAlpha() ? -1 : getTransparencyThreshold();
        int transIndex = -1;
        for (int i = 0; i < palette.length; i++) {
          int c = palette[i] & 0x00ffffff;
          if (transIndex < 0 && c == Green) {
            transIndex = i;
            break;
          }
        }
        if (transIndex < 0) {
          transIndex = 0;
        }
        HashMap<Integer, Byte> colorCache = new HashMap<Integer, Byte>(4096);
        for (int i = 0; i < palette.length; i++) {
          if (i != transIndex) {
            colorCache.put(Integer.valueOf(palette[i]), Byte.valueOf((byte)i));
          }
        }

        // processing frames
        IndexColorModel cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transIndex, DataBuffer.TYPE_BYTE);
        for (int i = 0; i < srcListFrames.size(); i++) {
          PseudoBamFrameEntry srcEntry = srcListFrames.get(i);
          BufferedImage srcImage = ColorConvert.toBufferedImage(srcEntry.getFrame(), true, true);
          int[] srcBuf = ((DataBufferInt)srcImage.getRaster().getDataBuffer()).getData();
          BufferedImage dstImage = new BufferedImage(srcEntry.getWidth(), srcEntry.getHeight(),
                                                     BufferedImage.TYPE_BYTE_INDEXED, cm);
          byte[] dstBuf = ((DataBufferByte)dstImage.getRaster().getDataBuffer()).getData();

          for (int ofs = 0; ofs < srcBuf.length; ofs++) {
            int c = srcBuf[ofs];
            if (PseudoBamDecoder.isTransparentColor(c, threshold)) {
              dstBuf[ofs] = (byte)transIndex;
            } else {
              Byte colIdx = colorCache.get(Integer.valueOf(c));
              if (colIdx != null) {
                int ci = colIdx.intValue() & 0xff;
                if (ci >= transIndex) ci++;
                dstBuf[ofs] = colIdx.byteValue();//(byte)ci;
              } else {
                byte color = (byte)ColorConvert.nearestColorRGB(srcBuf[ofs], palette, !getUseAlpha());
                dstBuf[ofs] = color;//(byte)ci;
                colorCache.put(Integer.valueOf(c), Byte.valueOf(color));
              }
            }
          }
          srcBuf = null;
          srcImage.flush();
          srcImage = null;
          dstBuf = null;

          PseudoBamFrameEntry dstEntry = new PseudoBamFrameEntry(dstImage, srcEntry.getCenterX(),
                                                                 srcEntry.getCenterY());
          // adding frame-specific options
          options = srcEntry.getOptionNames();
          for (int j = 0; j < options.length; j++) {
            dstEntry.setOption(options[j], srcEntry.getOption(options[j]));
          }
          dstListFrames.add(dstEntry);
        }
      } else {
        // BAM v2: create truecolored version of each frame
        for (int i = 0; i < srcListFrames.size(); i++) {
          PseudoBamFrameEntry srcEntry = srcListFrames.get(i);
          BufferedImage dstImage = ColorConvert.toBufferedImage(srcEntry.getFrame(), true, true);
          PseudoBamFrameEntry dstEntry = new PseudoBamFrameEntry(dstImage, srcEntry.getCenterX(),
                                                                 srcEntry.getCenterY());
          // adding frame-specific options
          options = srcEntry.getOptionNames();
          for (int j = 0; j < options.length; j++) {
            dstEntry.setOption(options[j], srcEntry.getOption(options[j]));
          }
          dstListFrames.add(dstEntry);
        }
      }
    } else {
      throw new Exception("Unknown target BAM format");
    }
  }

  /** Creates a new single frame that is compatible with the specified BAM version. */
  private void updateFinalBamFrame(int bamVersion, int frameIdx)
  {
    if (frameIdx >= 0 && frameIdx < listFrameEntries.get(BAM_ORIGINAL).size()) {
      PseudoBamFrameEntry srcEntry = listFrameEntries.get(BAM_ORIGINAL).get(frameIdx);
      BufferedImage srcImage = srcEntry.getFrame();
      BufferedImage dstImage = null;

      if (bamVersion == VERSION_BAMV1) {
        // BAM v1: creating paletted version of the source frame
        paletteDialog.updateGeneratedPalette();
        // preparing palette
        final int Green = 0x0000ff00;
        int[] palette = paletteDialog.getPalette(paletteDialog.getPaletteType());
        int threshold = getUseAlpha() ? -1 : getTransparencyThreshold();
        int transIndex = -1;
        for (int i = 0; i < palette.length; i++) {
          int c = palette[i] & 0x00ffffff;
          if (transIndex < 0 && c == Green) {
            transIndex = i;
            break;
          }
        }
        if (transIndex < 0) {
          transIndex = 0;
        }
        HashMap<Integer, Byte> colorCache = new HashMap<Integer, Byte>(4096);
        for (int i = 0; i < palette.length; i++) {
          if (i != transIndex) {
            colorCache.put(Integer.valueOf(palette[i]), Byte.valueOf((byte)i));
          }
        }
        IndexColorModel cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transIndex, DataBuffer.TYPE_BYTE);

        // converting frame
        srcImage = ColorConvert.toBufferedImage(srcImage, true, true);
        int[] srcBuf = ((DataBufferInt)srcImage.getRaster().getDataBuffer()).getData();
        dstImage = new BufferedImage(srcImage.getWidth(), srcImage.getHeight(),
                                     BufferedImage.TYPE_BYTE_INDEXED, cm);
        byte[] dstBuf = ((DataBufferByte)dstImage.getRaster().getDataBuffer()).getData();

        for (int ofs = 0; ofs < srcBuf.length; ofs++) {
          int c = srcBuf[ofs];
          if (PseudoBamDecoder.isTransparentColor(c, threshold)) {
            dstBuf[ofs] = (byte)transIndex;
          } else {
            Byte colIdx = colorCache.get(Integer.valueOf(c));
            if (colIdx != null) {
              int ci = colIdx.intValue() & 0xff;
              if (ci >= transIndex) ci++;
              dstBuf[ofs] = colIdx.byteValue();
            } else {
              byte color = (byte)ColorConvert.nearestColorRGB(srcBuf[ofs], palette, !getUseAlpha());
              dstBuf[ofs] = color;//(byte)ci;
              colorCache.put(Integer.valueOf(c), Byte.valueOf(color));
            }
          }
        }
        srcBuf = null;
        srcImage.flush();
        srcImage = null;
        dstBuf = null;

      } else if (bamVersion == VERSION_BAMV2) {
        // BAM v2: creating truecolored version of the source frame
        dstImage = ColorConvert.toBufferedImage(srcImage, true, true);
      }

      entryFilterPreview.setFrame(dstImage);
      entryFilterPreview.setCenterX(srcEntry.getCenterX());
      entryFilterPreview.setCenterY(srcEntry.getCenterY());
      dstImage = null;
    }
  }

  /** Updates current BAM session path list. */
  private void updateRecentSession(Path session)
  {
    if (session != null) {
      BamOptionsDialog.updateRecentSession(session);
      initSessionEntries();
    }
  }

  /** (Re-)creates a list of recently accessed BAM session paths. */
  private void initSessionEntries()
  {
    if (miSessionHistory == null) {
      miSessionHistory = new JMenu("Load recent sessions");
    } else {
      Component[] comp = miSessionHistory.getComponents();
      for (final Component c: comp) {
        if (c instanceof DataMenuItem) {
          ((DataMenuItem)c).removeActionListener(this);
        }
      }
      miSessionHistory.removeAll();
    }

    List<Path> recentSessions = BamOptionsDialog.getRecentSessions();
    for (final Path item: recentSessions) {
      DataMenuItem dmi = new DataMenuItem(item.getFileName().toString(), -1, item);
      dmi.setToolTipText(item.toString());
      dmi.addActionListener(this);
      miSessionHistory.add(dmi);
    }
    miSessionHistory.setEnabled(!recentSessions.isEmpty());
  }

  /** Initializes a new ProgressMonitor instance. */
  void initProgressMonitor(Component parent, String msg, String note, int maxProgress,
                                   int msDecide, int msWait)
  {
    if (parent == null) parent = NearInfinity.getInstance();
    if (maxProgress <= 0) maxProgress = 1;

    releaseProgressMonitor();
    pmMax = maxProgress;
    pmCur = 0;
    progress = new ProgressMonitor(parent, msg, note, 0, pmMax);
    progress.setMillisToDecideToPopup(msDecide);
    progress.setMillisToPopup(msWait);
    progress.setProgress(pmCur);
  }

  /** Closes an active instance of the ProgressMonitor. */
  void releaseProgressMonitor()
  {
    if (progress != null) {
      progress.close();
      progress = null;
    }
  }

  /** Advances the active ProgressMonitor instance by one step. */
  void advanceProgressMonitor(String note)
  {
    if (progress != null) {
      if (pmCur < pmMax) {
        pmCur++;
        if (note != null) {
          progress.setNote(note);
        }
        progress.setProgress(pmCur);
      }
    }
  }

  /** Returns the current progress of the active ProgressMonitor instance. */
  int getProgressMonitorStage()
  {
    if (progress != null) {
      return pmCur;
    } else {
      return 0;
    }
  }

  /** Sets the current stage of the active ProgressMonitor instance. */
  void setProgressMonitorState(int stage)
  {
    if (progress != null) {
      if (stage < progress.getMinimum()) stage = progress.getMinimum();
      if (stage > progress.getMaximum()) stage = progress.getMaximum();
      progress.setProgress(stage);
    }
  }

  /** Returns the max. stage of the active ProgressMonitor instance. */
  int getProgressMonitorMax()
  {
    if (progress != null) {
      return pmMax;
    } else {
      return 0;
    }
  }

  /** Sets a new max. stage to the active ProgressMonitor instance. */
  void setProgressMonitorMax(int max)
  {
    if (progress != null) {
      if (max >= 0) {
        progress.setMaximum(max);
      }
    }
  }

  /** Sets a new note to the active ProgressMonitor instance. */
  void setProgressMonitorNote(String note)
  {
    if (progress != null) {
      progress.setNote(note);
    }
  }

  /** Returns whether the active ProgressMonitor instance has been cancelled by the user. */
  boolean isProgressMonitorCancelled()
  {
    if (isProgressMonitorActive()) {
      return progress.isCanceled();
    }
    return false;
  }

  /** Returns whether a ProgressMonitor instance is active. */
  boolean isProgressMonitorActive()
  {
    return (progress != null);
  }

  /** Returns the ProgressMonitor instance, or null if not active. */
  ProgressMonitor getProgressMonitor()
  {
    return progress;
  }


//-------------------------- INNER CLASSES --------------------------

  /** Manages the frames aspect of BAM resources. */
  private static class BamFramesListModel extends AbstractListModel<PseudoBamFrameEntry>
  {
    private final ConvertToBam converter;
    private final PseudoBamDecoder decoder;

    public BamFramesListModel(ConvertToBam converter)
    {
      if (converter == null) {
        throw new NullPointerException();
      }
      this.converter = converter;
      this.decoder = this.converter.getBamDecoder(BAM_ORIGINAL);
    }

    /** Returns the parent converter object. */
    public ConvertToBam getConverter()
    {
      return converter;
    }

    /** Returns the associated BamDecoder object. */
    public PseudoBamDecoder getDecoder()
    {
      return decoder;
    }

//    /** Adds a new frame to the global frames list. */
//    public void add(BufferedImage image, Point center)
//    {
//      insert(getDecoder().frameCount(), new BufferedImage[]{image}, new Point[]{center});
//    }

//    /** Adds an array of images to the global frames list. */
//    public void add(BufferedImage[] images, Point[] centers)
//    {
//      insert(getDecoder().frameCount(), images, centers);
//    }

    /** Inserts the image into the global frames list. */
    public void insert(int pos, BufferedImage image, Point center)
    {
      insert(pos, new BufferedImage[]{image}, new Point[]{center});
    }

    /** Inserts the array of images into the global frames list. */
    public void insert(int pos, BufferedImage[] images, Point[] centers)
    {
      if (images != null && pos >= 0 && pos <= getDecoder().frameCount()) {
        int count = 0;
        PseudoBamControl control = getDecoder().createControl();
        control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
        for (int i = 0; i < images.length; i++) {
          if (images[i] != null) {
            // adding frame to global list
            Point center = (centers.length > i && centers[i] != null) ? centers[i] : null;
            getDecoder().frameInsert(pos+i, images[i], center);
            // registering colors values in global HashMap
            BufferedImage image = ColorConvert.toBufferedImage(images[i], true, false);
            PseudoBamDecoder.registerColors(getConverter().paletteDialog.getColorMap(), image);
            getConverter().paletteDialog.setPaletteModified();
            count++;
          }
        }
        if (count > 0) {
          fireIntervalAdded(this, pos, pos+count-1);
        }
      }
    }

    /**
     * Removes a single entry from the global frames list.
     */
    public void remove(int pos)
    {
      remove(pos, 1);
    }

    /**
     * Removes a number of entries from the global frames list.
     */
    public void remove(int pos, int count)
    {
      if (count > 0) {
        if (pos < 0) pos = 0;
        if (pos >= getDecoder().frameCount()) pos = getDecoder().frameCount() - 1;
        if (pos + count > getDecoder().frameCount()) {
          count = getDecoder().frameCount() - pos;
        }
        PseudoBamControl control = getDecoder().createControl();
        control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
        // unregistering color values in global color map
        for (int i = 0; i < count; i++) {
          BufferedImage image = ColorConvert.toBufferedImage(getDecoder().frameGet(control, pos+i), true, false);
          PseudoBamDecoder.unregisterColors(getConverter().paletteDialog.getColorMap(), image);
          getConverter().paletteDialog.setPaletteModified();
        }
        getDecoder().frameRemove(pos, count);
        fireIntervalRemoved(this, pos, pos+count-1);
      }
    }

    /**
     * Removes all entries from the global frame list.
     */
    public void clear()
    {
      int count;
      count = getDecoder().frameCount();
      PseudoBamControl control = getDecoder().createControl();
      control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
      // clearing global color map
      getConverter().paletteDialog.getColorMap().clear();
      getConverter().paletteDialog.setPaletteModified();
      getDecoder().frameClear();
      if (count > 0) {
        fireIntervalRemoved(this, 0, count-1);
      }
    }

    /**
     * Moves the specified entry within the list by {@code offset}.
     * @param index The index of the frame.
     * @param offset The number of positions to move.
     */
    public void move(int index, int offset)
    {
      int retVal, pos1 = index, pos2 = index;

      // moving positions
      retVal = getDecoder().frameMove(index, offset);

      // preparing interval
      if (retVal >= 0) {
        if (retVal > pos1) {
          pos2 = retVal;
        } else {
          pos1 = retVal;
        }
      }

      // notifying listeners
      if (pos2 > pos1) {
        fireContentsChanged(this, pos1, pos2);
      }
    }

    /** Returns {@code true} if, and only if {@code getDecoder().getFrameCount()} is 0. */
    public boolean isEmpty()
    {
      return getDecoder().isEmpty();
    }

    @Override
    public int getSize()
    {
      return getDecoder().frameCount();
    }

    @Override
    public PseudoBamFrameEntry getElementAt(int index)
    {
      if (index >= 0 && index < getDecoder().frameCount()) {
        return getDecoder().getFrameInfo(index);
      } else {
        return null;
      }
    }
  }


  /** Manages frames within a cycle. */
  private static class BamCycleFramesListModel extends AbstractListModel<PseudoBamFrameEntry>
  {
    private final ConvertToBam converter;
    private final PseudoBamDecoder decoder;
    private final PseudoBamDecoder.PseudoBamControl control;

    public BamCycleFramesListModel(ConvertToBam converter)
    {
      if (converter == null) {
        throw new NullPointerException();
      }
      this.converter = converter;
      this.decoder = getConverter().getBamDecoder(BAM_ORIGINAL);
      this.control = getDecoder().createControl();
    }

    /** Returns the parent converter object. */
    public ConvertToBam getConverter()
    {
      return converter;
    }

    /** Returns the associated BamDecoder object. */
    public PseudoBamDecoder getDecoder()
    {
      return decoder;
    }

    /** Returns the associated BamDecoder control. */
    public PseudoBamDecoder.PseudoBamControl getControl()
    {
      return control;
    }

    /** Returns the active cycle. */
    public int getCycle()
    {
      return getControl().cycleGet();
    }

    /** Sets a new active cycle. */
    public void setCycle(int cycle)
    {
      if (cycle < 0) cycle = 0; else if (cycle >= getControl().cycleCount()) cycle = getControl().cycleCount() - 1;
      if (cycle != getControl().cycleGet()) {
        int oldCount = getControl().cycleFrameCount();
        getControl().cycleSet(cycle);
        int newCount = getControl().cycleFrameCount();

        // firing change event if needed
        int changed = Math.min(oldCount, newCount);
        if (changed > 0) {
          fireContentsChanged(this, 0, changed - 1);
        }

        // firing remove/added events if needed
        int diff = Math.max(oldCount, newCount) - changed;
        if (diff < 0) {
          fireIntervalRemoved(this, changed, changed - diff - 1);
        } else if (diff > 0) {
          fireIntervalAdded(this, changed, changed + diff - 1);
        }
      }
    }

//    /** Adds one frame index to the current cycle. */
//    public void add(int index)
//    {
//      insert(getControl().cycleFrameCount(), new int[]{index});
//    }

    /** Adds the specified array of frame indices to the current cycle. */
    public void add(int[] indices)
    {
      insert(getControl().cycleFrameCount(), indices);
    }

    /** Inserts one frame index into the current cycle. */
    public void insert(int pos, int index)
    {
      insert(pos, new int[]{index});
    }

    /** Inserts the array of frame indices into the current cycle. */
    public void insert(int pos, int[] indices)
    {
      if (indices != null && pos >= 0 && pos <= getControl().cycleFrameCount()) {
        int count = indices.length;
        getControl().cycleInsertFrames(getControl().cycleGet(), pos, indices);
        fireIntervalAdded(this, pos, pos+count-1);
      }
    }

//    /** Removes one entry from the current cycle. */
//    public void remove(int pos)
//    {
//      remove(pos, 1);
//    }

    /**
     * Removes a number of entries from the current cycle.
     */
    public void remove(int pos, int count)
    {
      if (count > 0 && pos >= 0 && pos < getControl().cycleFrameCount()) {
        if (pos + count > getControl().cycleFrameCount()) {
          count = getControl().cycleFrameCount() - pos;
        }
        getControl().cycleRemoveFrames(getControl().cycleGet(), pos, count);
        fireIntervalRemoved(this, pos, pos+count-1);
      }
    }

    /**
     * Removes all entries from the current cycle.
     */
    public void clear()
    {
      int count = getControl().cycleFrameCount();
      getControl().cycleClearFrames();
      if (count > 0) {
        fireIntervalRemoved(this, 0, count-1);
      }
    }

    /**
     * Moves the specified entry within the list by {@code offset}.
     * @param index The index of the frame.
     * @param offset The number of positions to move.
     */
    public void move(int index, int offset)
    {

      // moving positions
      int retVal = getControl().cycleMoveFrame(index, offset);

      // preparing interval
      int pos1 = index, pos2 = index;
      if (retVal >= 0) {
        if (retVal > pos1) {
          pos2 = retVal;
        } else {
          pos1 = retVal;
        }
      }

      // notifying listeners
      if (pos2 > pos1) {
        fireContentsChanged(this, pos1, pos2);
      }
    }

    /** Returns {@code true} if, and only if {@code getControl().cycleFrameCount()} is 0. */
    public boolean isEmpty()
    {
      return (getControl().cycleFrameCount() == 0);
    }


    @Override
    public int getSize()
    {
      return getControl().cycleFrameCount();
    }

    @Override
    public PseudoBamFrameEntry getElementAt(int index)
    {
      if (index >= 0 && index < getControl().cycleFrameCount()) {
        return getDecoder().getFrameInfo(getControl().cycleGetFrameIndexAbsolute(index));
      } else {
        return null;
      }
    }
  }



  /** Manages the cycles aspect of BAM resources. */
  private static class BamCyclesListModel extends AbstractListModel<PseudoBamCycleEntry>
  {
    private final ConvertToBam converter;
    private final PseudoBamDecoder decoder;
    private final PseudoBamDecoder.PseudoBamControl control;

    public BamCyclesListModel(ConvertToBam converter)
    {
      if (converter == null) {
        throw new NullPointerException();
      }
      this.converter = converter;
      this.decoder = getConverter().getBamDecoder(BAM_ORIGINAL);
      this.control = getDecoder().createControl();
    }

    /** Returns the parent converter object. */
    public ConvertToBam getConverter()
    {
      return converter;
    }

    /** Returns the associated BamDecoder object. */
    public PseudoBamDecoder getDecoder()
    {
      return decoder;
    }

    /** Returns the associated BamDecoder control. */
    public PseudoBamDecoder.PseudoBamControl getControl()
    {
      return control;
    }


//    /** Adds a new empty cycle to the cycles list. */
//    public void add()
//    {
//      insert(getControl().cycleCount(), new int[0]);
//    }

//    /** Adds a new cycle with the specified frame index to the cycles list. */
//    public void add(int index)
//    {
//      insert(getControl().cycleCount(), new int[]{index});
//    }

    /** Adds a new cycle with the specified frame indices to the cycles list. */
    public void add(int[] indices)
    {
      insert(getControl().cycleCount(), indices);
    }

//    /** Insert an empty cycle at the specified cycle position. */
//    public void insert(int pos)
//    {
//      insert(pos, new int[0]);
//    }

//    /** Inserts a cycle with one frame index at the specified cycle position. */
//    public void insert(int pos, int index)
//    {
//      insert(pos, new int[]{index});
//    }

    /** Inserts a cycle with the specified frame indices at the specified cycle position. */
    public void insert(int pos, int[] indices)
    {
      if (pos >= 0 && pos <= getControl().cycleCount() && indices != null) {
        getControl().cycleInsert(pos, indices);
        fireIntervalAdded(this, pos, pos);
      }
    }

//    /** Removes one cycle at the specified cycle position. */
//    public void remove(int pos)
//    {
//      remove(pos, 1);
//    }

    /** Removes a number of cycles at the specified cycle position. */
    public void remove(int pos, int count)
    {
      if (pos >= 0 && pos < getControl().cycleCount() && count > 0) {
        if (pos + count > getControl().cycleCount()) {
          count = getControl().cycleCount() - pos;
        }
        getControl().cycleRemove(pos, count);
        if (count > 0) {
          fireIntervalRemoved(this, pos, pos+count-1);
        }
      }
    }

    /** Removes all cycles from the cycles list. */
    public void clear()
    {
      int count = getControl().cycleCount();
      getControl().cycleClear();
      if (count > 0) {
        fireIntervalRemoved(this, 0, count-1);
      }
    }

    /**
     * Moves the specified cycle entry within the cycles list by {@code offset}.
     * @param cycleIdx The index of the cycle.
     * @param offset The number of positions to move.
     */
    public void move(int cycleIdx, int offset)
    {
      if (cycleIdx >= 0 && cycleIdx < getControl().cycleCount()) {
        int pos1 = cycleIdx, pos2 = cycleIdx;
        int retVal = getControl().cycleMove(cycleIdx, offset);
        if (retVal >= 0) {
          if (retVal > pos1) {
            pos2 = retVal;
          } else {
            pos1 = retVal;
          }
        }
        if (pos2 > pos1) {
          fireContentsChanged(this, pos1, pos2);
        }
      }
    }

    /** Fires a change event for the cycle at the specified index. */
    public void contentChanged(int index)
    {
      contentsChanged(index, index);
    }

    /** Fires a change event for the cycle range defined by the specified indices. */
    public void contentsChanged(int index0, int index1)
    {
      if (index0 >= 0 && index0 < getControl().cycleCount() &&
          index1 >= 0 && index1 < getControl().cycleCount()) {
        if (index0 > index1) {
          int tmp = index0;
          index0 = index1;
          index1 = tmp;
        }
        fireContentsChanged(this, index0, index1);
      }
    }

    /** Returns {@code true} if, and only if {@code getControl().cycleCount()} is 0. */
    public boolean isEmpty()
    {
      return getControl().isEmpty();
    }

    @Override
    public int getSize()
    {
      return getControl().cycleCount();
    }

    @Override
    public PseudoBamDecoder.PseudoBamCycleEntry getElementAt(int index)
    {
      if (index >= 0 && index < getControl().cycleCount()) {
        return getControl().getCycleInfo(index);
      } else {
        return null;
      }
    }
  }


  /** Adds a prefix to the cell's visual output. */
  private static class IndexedCellRenderer extends DefaultListCellRenderer
  {
    public IndexedCellRenderer()
    {
      super();
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
        boolean isSelected, boolean cellHasFocus)
    {
      String template = "%0" +
                        String.format("%d", Integer.toString(list.getModel().getSize()).length()) +
                        "d - %s";
      return super.getListCellRendererComponent(list, String.format(template, index, value),
                                                index, isSelected, cellHasFocus);
    }
  }


  /**
   * Provides methods for importing or exporting BAM configuration data via INI file, such as
   * frame sources, center position data or cycle definitions.
   */
  private static class Exporter extends JDialog implements ActionListener
  {
    /*
     * INI format:
     * 1. Section "[Global]" (mandatory)
     *  - contains a single entry "version" with a version number
     * 2. Section "[Frames]" (optional)
     *  - contains any number of frame source definitions in the format
     *    - key: zero-based frame index
     *    - value: full path to graphics file,
     *             optionally separated by colon ':' followed by a frame index
     *             (only for input files containing multiple frames, default: 0)
     *      Example: 0=c:/myfolder/myfile.bam:12 <- to load frame 12 of myfile.bam
     * 3. Section "[Center]" (optional)
     *  - contains any number of center position entries for individual frames in the format
     *    - key: zero-based frame index
     *    - value: a sequence of two numbers for x and y, separated by comma ','
     *      Example: 0=12,-55 <- for position [12.-55]
     * 4. Section "[Cycles]" (optional)
     *  - contains cycle definitions for the BAM in the format
     *    - key: zero-based cycle index
     *    - value: a sequence of numbers specifying frame indices, separated by comma ','
     *      Example: 0=0,1,2,3,4,5,90,91,92,93
     * 5. Section "[Filters]" (optional)
     *  - contains a list of filters to apply, including filter configurations
     *  - uses name and config entries
     *  - name key: name_n (where n is a positive number)
     *  - name value: the filter name
     *  - config key: config_n (where n is a positive number)
     *  - config value: a configuration string (can be empty)
     *  - Example:
     *      name_0=Brightness/Contrast/Gamma
     *      config_0=25;100;128;[0,18,19,20,192,193,194,195]
     */
    private static final String SECTION_GLOBAL    = "Global";   // global section name
    private static final String SECTION_FRAMES    = "Frames";   // frames section name
    private static final String SECTION_CENTER    = "Center";   // center point section name
    private static final String SECTION_CYCLES    = "Cycles";   // cycles section name
    private static final String SECTION_FILTERS   = "Filters";  // filters section name
    private static final String KEY_VERSION       = "version";  // key in global section
    private static final String KEY_FILTER_NAME   = "name_";  // key in global section
    private static final String KEY_FILTER_CONFIG = "config_";  // key in global section
    private static final char SEPARATOR_FRAME     = ':';        // used in frame source definition to separate frame name from index
    private static final char SEPARATOR_NUMBER    = ',';        // number separator for cycle definitions or center point data
    private static final int VERSION              = 1;          // supported file version
    private static final String QUESTION_EXPORT   = "What do you want to export?";
    private static final String QUESTION_IMPORT   = "What do you want to import?";

    private final JLabel lSelect = new JLabel();
    private final JCheckBox cbFrames = new JCheckBox("Frame source files", true);
    private final JCheckBox cbCenter = new JCheckBox("Frame center coordinates", true);
    private final JCheckBox cbCycles = new JCheckBox("Cycle definitions", true);
    private final JCheckBox cbFilters = new JCheckBox("Filter configurations", true);
    private final JButton bAccept = new JButton("Accept");
    private final JButton bCancel = new JButton("Cancel");
    private final ConvertToBam bam;

    private IniMapSection sectionFrames, sectionCenter, sectionCycles, sectionFilters;
    private boolean accepted;

    /** Returns a extension filter for INI files. */
    private static FileNameExtensionFilter getIniFilter()
    {
      return new FileNameExtensionFilter("INI files (*.ini)", "ini");
    }

    public Exporter(ConvertToBam bam)
    {
      super(bam, true);
      this.bam = bam;
      init();
    }

    //--------------------- Begin Interface ActionListener ---------------------

    @Override
    public void actionPerformed(ActionEvent event)
    {
      if (event.getSource() == bAccept) {
        acceptDialog();
      } else if (event.getSource() == bCancel) {
        cancel();
      } else if (event.getSource() instanceof JCheckBox) {
        bAccept.setEnabled(cbFrames.isSelected() || cbCenter.isSelected() ||
                           cbCycles.isSelected() || cbFilters.isSelected());
      }
    }

    //--------------------- End Interface ActionListener ---------------------

    /** Must be called at the end to clean up dialog resources. */
    public void close()
    {
      dispose();
    }

    /**
     * Opens dialog to choose what to export and exports selected data.
     * Returns whether export was successful.
     */
    public boolean exportData(boolean silent)
    {
      resetData();

      // trying to determine default output filename
      Path root = getDefaultIniName("data.ini");
      Path outFile = getSaveFileName(bam, "Export BAM session", root,
                                     new FileNameExtensionFilter[]{getIniFilter()}, 0);
      if (outFile != null) {
        outFile = StreamUtils.replaceFileExtension(outFile, "ini");
        bam.updateRecentSession(outFile);
        if (getSelection(true)) {
          try {
            WindowBlocker.blockWindow(bam, true);
            return saveData(outFile, silent);
          } finally {
            WindowBlocker.blockWindow(bam, false);
          }
        }
      }
      return false;
    }

    /**
     * Opens dialog to choose what to import and imports selected data.
     * Returns whether import was successful.
     */
    public boolean importData(boolean silent)
    {
      resetData();

      Path[] files = getOpenFileName(bam, "Import BAM session", null, false,
                                     new FileNameExtensionFilter[]{getIniFilter()}, 0);
      if (files != null && files.length > 0) {
        if (!Files.isRegularFile(files[0])) {
          files[0] = StreamUtils.replaceFileExtension(files[0], "ini");
        }
        if (loadData(files[0], silent)) {
          bam.updateRecentSession(files[0]);
          if (getSelection(false)) {
            try {
              WindowBlocker.blockWindow(bam, true);
              return applyData(silent);
            } finally {
              WindowBlocker.blockWindow(bam, false);
            }
          }
        }
      }
      return false;
    }

    /**
     * Imports selected data from the specified session file.
     * Returns whether import was successful.
     */
    public boolean importData(Path session, boolean silent)
    {
      if (session != null) {
        resetData();
        if (loadData(session, silent)) {
          bam.updateRecentSession(session);
          if (getSelection(false)) {
            try {
              WindowBlocker.blockWindow(bam, true);
              return applyData(silent);
            } finally {
              WindowBlocker.blockWindow(bam, false);
            }
          }
        }
      }
      return false;
    }

    /** Loads data from the specified file without user-interaction and optionally without feedback. */
    private boolean loadData(Path inFile, boolean silent)
    {
      if (inFile != null) {
        IniMap ini = new IniMap(new FileResourceEntry(inFile));

        try {
          // checking integrity
          if (ini.getSection(SECTION_GLOBAL) == null ||
              ini.getSection(SECTION_GLOBAL).getEntry(KEY_VERSION) == null) {
            throw new Exception("Invalid BAM session file.");
          }
          if (Misc.toNumber(ini.getSection(SECTION_GLOBAL).getEntry(KEY_VERSION).getValue(), -1) != VERSION) {
            throw new Exception("Invalid or unsupported file version.");
          }

          if (ini.getSection(SECTION_FRAMES) != null) {
            if (!loadFrameData(ini.getSection(SECTION_FRAMES))) {
              throw new Exception("Error loading frame source files.");
            }
          }

          if (ini.getSection(SECTION_CENTER) != null) {
            if (!loadCenterData(ini.getSection(SECTION_CENTER))) {
              throw new Exception("Error loading frame center coordinates.");
            }
          }

          if (ini.getSection(SECTION_CYCLES) != null) {
            if (!loadCycleData(ini.getSection(SECTION_CYCLES))) {
              throw new Exception("Error loading cycle definitions.");
            }
          }

          if (ini.getSection(SECTION_FILTERS) != null) {
            if (!loadFilterData(ini.getSection(SECTION_FILTERS))) {
              throw new Exception("Error loading filters.");
            }
          }

          return true;
        } catch (Exception e) {
          // parsing failed
          resetData();
          if (!silent && e.getMessage() != null && !e.getMessage().isEmpty()) {
            JOptionPane.showMessageDialog(bam, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          }
        }
      }
      return false;
    }

    private boolean loadFrameData(IniMapSection frames) throws Exception
    {
      if (frames != null && frames.getName().equalsIgnoreCase(SECTION_FRAMES)) {
        for (final IniMapEntry entry : frames) {
          if (Misc.toNumber(entry.getKey(), -1) < 0) {
            throw new Exception("Invalid key value found at line " + (entry.getLine() + 1));
          }
          String value = entry.getValue().trim();
          if (value.isEmpty()) {
            throw new Exception("Empty frame source path found at line " + (entry.getLine() + 1));
          }
          int sepIdx = value.lastIndexOf(SEPARATOR_FRAME);
          int frameIdx = Integer.MIN_VALUE;
          if (sepIdx >= 0) {
            frameIdx = Misc.toNumber(value.substring(sepIdx + 1), Integer.MIN_VALUE);
            value = value.substring(0, sepIdx);
          }
          if (frameIdx == Integer.MIN_VALUE) {
            throw new Exception("Frame source path does not contain frame index at line " + (entry.getLine() + 1));
          }

          if (value.startsWith(BAM_FRAME_PATH_BIFF)) {
            String resName = value.substring(BAM_FRAME_PATH_BIFF.length(), value.length());
            if (!ResourceFactory.resourceExists(resName)) {
              throw new Exception("Frame source path not found at line " + (entry.getLine() + 1));
            }
          } else {
            Path file = FileManager.resolve(value);
            if (!Files.isRegularFile(file)) {
              throw new Exception("Frame source path not found at line " + (entry.getLine() + 1));
            }
          }
        }
        sectionFrames = frames;
        return true;
      }
      return true;
    }

    private boolean loadCenterData(IniMapSection centers) throws Exception
    {
      if (centers != null && centers.getName().equalsIgnoreCase(SECTION_CENTER)) {
        for (final IniMapEntry entry : centers) {
          if (Misc.toNumber(entry.getKey(), -1) < 0) {
            throw new Exception("Invalid key value found at line " + (entry.getLine() + 1));
          }
          if (!entry.getValue().trim().matches("-?\\d+\\s*,\\s*-?\\d+")) {
            throw new Exception("Invalid value found at line " + (entry.getLine() + 1));
          }
        }
        sectionCenter = centers;
        return true;
      }
      return false;
    }

    private boolean loadCycleData(IniMapSection cycles) throws Exception
    {
      if (cycles != null && cycles.getName().equalsIgnoreCase(SECTION_CYCLES)) {
        for (final IniMapEntry entry : cycles) {
          if (Misc.toNumber(entry.getKey(), -1) < 0) {
            throw new Exception("Invalid key value found at line " + (entry.getLine() + 1));
          }
          if (!entry.getValue().trim().matches("(\\d+\\s*(,\\s*\\d+\\s*)*)?")) {
            throw new Exception("Invalid value found at line " + (entry.getLine() + 1));
          }
        }
        sectionCycles = cycles;
        return true;
      }
      return false;
    }

    private boolean loadFilterData(IniMapSection filters) throws Exception
    {
      if (filters != null && filters.getName().equalsIgnoreCase(SECTION_FILTERS)) {
        for (final IniMapEntry entry : filters) {
          String key = entry.getKey().trim();
          String value = entry.getValue().trim();
          if (key.matches(KEY_FILTER_NAME + "\\d+")) {
            if (BamFilterFactory.getFilterInfo(value) == null) {
              throw new Exception("BAM filter \"" +
                                  value.substring(0, Math.min(value.length(), 256)) +
                                  "\" does not exist.");
            }
          } else if (!key.matches(KEY_FILTER_CONFIG + "\\d+")) {
            throw new Exception("Invalid key value found at line " + (entry.getLine() + 1));
          }
        }
        sectionFilters = filters;
        return true;
      }
      return false;
    }


    /** Applies available data to the converter without user-interaction and optionally without feedback. */
    private boolean applyData(boolean silent)
    {
      bam.previewStop();
      bam.outputSetModified(true);

      try {
        if (sectionFrames != null) {
          if (!applyFramesData(silent)) {
            throw new Exception("Error adding frame entries.");
          }
        }
        if (sectionCenter != null) {
          if (!applyCenterData(silent)) {
            throw new Exception("Error applying frame center coordinates.");
          }
        }
        if (sectionCycles != null) {
          if (!applyCycleData(silent)) {
            throw new Exception("Error adding cycle definitions.");
          }
        }
        if (sectionFilters != null) {
          if (!applyFilterData(silent)) {
            throw new Exception("Error adding filters.");
          }
        }
        return true;
      } catch (Exception e) {
        resetData();
        if (!silent && e.getMessage() != null && !e.getMessage().isEmpty()) {
          WindowBlocker.blockWindow(bam, false);
          JOptionPane.showMessageDialog(bam, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
      }
      return false;
    }

    private boolean applyFramesData(boolean silent) throws Exception
    {
      /** Storage for ResourceEntry and frame index for convenience. */
      class SourceFrame {
        public final ResourceEntry entry;
        public final int index;

        public SourceFrame(ResourceEntry entry, int index)
        {
          this.entry = entry;
          this.index = index;
        }
      }

      /** Primarily used for caching BAM decoder instances. */
      class SourceData {
        public final boolean isBam;
        // bam-specific
        public final BamDecoder decoder;
        public final BamDecoder.BamControl control;
        public final IndexColorModel cm;
        // image-specific
        public final Path file;

        public SourceData(BamDecoder decoder)
        {
          this.isBam = true;
          this.decoder = decoder;
          this.control = this.decoder.createControl();
          if (this.decoder instanceof BamV1Decoder) {
            int[] palette = ((BamV1Decoder.BamV1Control)control).getPalette();
            int transColor = ((BamV1Decoder.BamV1Control)control).getTransparencyIndex();
            this.cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transColor, DataBuffer.TYPE_BYTE);
          } else {
            this.cm = null;
          }
          this.file = null;
        }

        public SourceData(Path image)
        {
          this.isBam = false;
          this.decoder = null;
          this.control = null;
          this.cm = null;
          this.file = image;
        }
      }

      if (sectionFrames != null) {
        // preparing frames
        int entryCount = sectionFrames.getEntryCount();
        SourceFrame[] frames = new SourceFrame[entryCount];
        for (final IniMapEntry entry : sectionFrames) {
          // checking list indices
          int listIndex = Misc.toNumber(entry.getKey(), -1);
          if (listIndex < 0 || listIndex >= entryCount) {
            throw new Exception("Target frame index out of range [: " + listIndex + "] at line " + (entry.getLine() + 1));
          }

          // checking frame source paths and indices
          String value = entry.getValue().trim();
          int frameIndex = -1;
          int sepIdx = value.lastIndexOf(SEPARATOR_FRAME);
          if (sepIdx >= 0) {
            frameIndex = Misc.toNumber(value.substring(sepIdx + 1), -1);
            value = value.substring(0, sepIdx);
          }
          if (frameIndex < 0 || value.isEmpty()) {
            throw new Exception("Source frame index out of range [: " + listIndex + "] at line " + (entry.getLine() + 1));
          }

          ResourceEntry resource = null;
          if (value.startsWith(BAM_FRAME_PATH_BIFF)) {
            value = value.substring(BAM_FRAME_PATH_BIFF.length());
            if (ResourceFactory.resourceExists(value)) {
              resource = ResourceFactory.getResourceEntry(value);
            }
          } else {
            Path file = FileManager.resolve(value);
            if (Files.isRegularFile(file)) {
              resource = new FileResourceEntry(file);
            }
          }
          if (resource == null) {
            throw new Exception("Resource does not exist at line " + (entry.getLine() + 1));
          }

          frames[listIndex] = new SourceFrame(resource, frameIndex);
        }
        for (int i = 0; i < frames.length; i++) {
          if (frames[i] == null) {
            throw new Exception("Undefined target frame index " + i);
          }
        }

        bam.filterRemoveAll();
        bam.cyclesRemoveAll();
        bam.framesRemoveAll();
        bam.getPaletteDialog().clear();

        // applying frames
        HashMap<ResourceEntry, SourceData> sourceMap = new HashMap<ResourceEntry, SourceData>();
        for (int i = 0; i < frames.length; i++) {
          SourceFrame frame = frames[i];
          SourceData data = sourceMap.get(frame.entry);
          if (data == null) {
            if (BamDecoder.isValid(frame.entry)) {
              data = new SourceData(BamDecoder.loadBam(frame.entry));
            } else {
              data = new SourceData(frame.entry.getActualPath());
            }
            sourceMap.put(frame.entry, data);
          }
          if (data.isBam) {
            bam.framesAddBamFrame(i, data.decoder, data.control, frame.index, data.cm);
          } else {
            bam.framesAddImage(i, data.file, frame.index);
          }
        }
        bam.updateFramesList();
        return true;
      }
      return false;
    }

    private boolean applyCenterData(boolean silent) throws Exception
    {
      if (sectionCenter != null) {
        for (final IniMapEntry entry : sectionCenter) {
          int listIndex = Misc.toNumber(entry.getKey(), -1);
          if (listIndex >= 0 && listIndex < bam.modelFrames.getSize()) {
            String[] numbers = entry.getValue().trim().split(Character.toString(SEPARATOR_NUMBER));
            if (numbers.length >= 2) {
              int x = Misc.toNumber(numbers[0].trim(), Integer.MIN_VALUE);
              int y = Misc.toNumber(numbers[1].trim(), Integer.MIN_VALUE);
              if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
                PseudoBamFrameEntry bfe = bam.modelFrames.getElementAt(listIndex);
                bfe.setCenterX(x);
                bfe.setCenterY(y);
              }
            }
          }
        }
        bam.updateFramesList();
        return true;
      }
      return false;
    }

    private boolean applyCycleData(boolean silent) throws Exception
    {
      if (sectionCycles != null) {
        if (bam.modelFrames.getSize() == 0) {
          throw new Exception("Unable to add cycle definitions. No frames available.");
        }

        // preparing cycle definitions
        final HashMap<Integer, int[]> cycles = new HashMap<>();
        int maxCycle = -1;
        for (final IniMapEntry entry : sectionCenter) {
          int cycleIndex = Misc.toNumber(entry.getKey(), -1);
          if (cycleIndex >= 0) {
            String value = entry.getValue().trim();
            String[] values = (value.isEmpty()) ? new String[0] : value.split(Character.toString(SEPARATOR_NUMBER));
            int[] cycleList = new int[values.length];
            for (int j = 0; j < cycleList.length; j++) {
              int n = Misc.toNumber(values[j].trim(), -1);
              n = Math.max(0, Math.min(bam.modelFrames.getSize() - 1, n));
              cycleList[j] = n;
            }
            cycles.put(Integer.valueOf(cycleIndex), cycleList);
            maxCycle = Math.max(maxCycle, cycleIndex);
          }
        }
        if (maxCycle < 0) {
          // no cycles defined -> return successfully
          return true;
        }

        // post-processing
        int[][] cycleArray = new int[maxCycle + 1][];
        for (Integer idx : cycles.keySet()) {
          cycleArray[idx] = cycles.get(idx);
        }

        bam.filterRemoveAll();
        bam.cyclesRemoveAll();

        // applying cycle definitions
        final int[] emptyCycle = new int[0];
        for (int[] cycle : cycleArray) {
          int[] curCycle = (cycle != null) ? cycle : emptyCycle;
          bam.modelCycles.add(curCycle);
        }

        bam.updateCyclesList();
        return true;
      }
      return false;
    }

    private boolean applyFilterData(boolean silent) throws Exception
    {
      class Config {
        public String name;
        public String param;

        public Config() {}
      }

      if (sectionFilters != null) {
        if (bam.modelFrames.getSize() == 0) {
          throw new Exception("Unable to add filters. No frames available.");
        }

        // preparing filter list
        final HashMap<Integer, Config> filterMap = new HashMap<>();
        int maxIndex = -1;
        for (final IniMapEntry entry : sectionFilters) {
          String key = entry.getKey();
          if (key.startsWith(KEY_FILTER_NAME)) {
            final int idx = Misc.toNumber(key.substring(KEY_FILTER_NAME.length()), -1);
            if (idx >= 0) {
              String name = entry.getValue().trim();
              Config config = filterMap.get(idx);
              if (config == null) {
                config = new Config();
                filterMap.put(idx, config);
              }
              config.name = name;
              maxIndex = Math.max(maxIndex, idx);
            }
          } else if (key.startsWith(KEY_FILTER_CONFIG)) {
            final Integer idx = Misc.toNumber(key.substring(KEY_FILTER_CONFIG.length()), -1);
            if (idx >= 0) {
              String param = entry.getValue().trim();
              Config config = filterMap.get(idx);
              if (config == null) {
                config = new Config();
                filterMap.put(idx, config);
              }
              config.param = param;
              maxIndex = Math.max(maxIndex, idx);
            }
          }
        }
        if (maxIndex < 0) {
          // no filters defined -> return successfully
          return true;
        }

        // post-processing data
        Config[] configArray = new Config[maxIndex + 1];
        for (Integer idx : filterMap.keySet()) {
          Config config = filterMap.get(idx);
          if (config.name != null) {
            if (config.param == null) {
              config.param = "";
            }
            configArray[idx] = config;
          }
        }

        // applying filter list
        bam.filterRemoveAll();
        for (Config config : configArray) {
          if (config != null) {
            BamFilterFactory.FilterInfo info = BamFilterFactory.getFilterInfo(config.name);
            if (info != null) {
              BamFilterBase filter = bam.filterAdd(info);
              if (filter != null) {
                filter.setConfiguration(config.param);
              }
            }
          }
        }

        bam.updateFilterList();
        return true;
      }
      return false;
    }

    /** Saves data to specified INI file without user-interaction and optionally without feedback. */
    private boolean saveData(Path outFile, boolean silent)
    {
      boolean retVal = false;
      if (outFile != null) {
        StringBuilder sb = new StringBuilder();

        // creating global section
        sb.append('[').append(SECTION_GLOBAL).append(']').append(Misc.LINE_SEPARATOR);
        sb.append(KEY_VERSION).append('=').append(VERSION).append(Misc.LINE_SEPARATOR);
        sb.append(Misc.LINE_SEPARATOR);

        // creating frames section
        if (isFramesSelected()) {
          sb.append('[').append(SECTION_FRAMES).append(']').append(Misc.LINE_SEPARATOR);
          for (int i = 0; i < bam.modelFrames.getSize(); i++) {
            PseudoBamFrameEntry entry = bam.modelFrames.getElementAt(i);
            String path = entry.getOption(BAM_FRAME_OPTION_PATH).toString();
            int index = ((Number)entry.getOption(BAM_FRAME_OPTION_SOURCE_INDEX)).intValue();
            sb.append(Integer.toString(i)).append('=').append(path);
            sb.append(SEPARATOR_FRAME).append(Integer.toString(index));
            sb.append(Misc.LINE_SEPARATOR);
          }
          sb.append(Misc.LINE_SEPARATOR);
        }

        // creating center section
        if (isCenterSelected()) {
          sb.append('[').append(SECTION_CENTER).append(']').append(Misc.LINE_SEPARATOR);
          for (int i = 0; i < bam.modelFrames.getSize(); i++) {
            PseudoBamFrameEntry entry = bam.modelFrames.getElementAt(i);
            sb.append(Integer.toString(i)).append('=');
            sb.append(entry.getCenterX()).append(SEPARATOR_NUMBER).append(entry.getCenterY());
            sb.append(Misc.LINE_SEPARATOR);
          }
          sb.append(Misc.LINE_SEPARATOR);
        }

        // creating cycles section
        if (isCyclesSelected()) {
          sb.append('[').append(SECTION_CYCLES).append(']').append(Misc.LINE_SEPARATOR);
          for (int i = 0; i < bam.modelCycles.getSize(); i++) {
            PseudoBamCycleEntry entry = bam.modelCycles.getElementAt(i);
            sb.append(Integer.toString(i)).append('=');
            for (int j = 0; j < entry.size(); j++) {
              if (j > 0) {
                sb.append(SEPARATOR_NUMBER);
              }
              sb.append(entry.get(j));
            }
            sb.append(Misc.LINE_SEPARATOR);
          }
          sb.append(Misc.LINE_SEPARATOR);
        }

        // creating filters section
        if (isFiltersSelected()) {
          sb.append('[').append(SECTION_FILTERS).append(']').append(Misc.LINE_SEPARATOR);
          for (int i = 0; i < bam.modelFilters.getSize(); i++) {
            BamFilterBase filter = bam.modelFilters.getElementAt(i);
            sb.append(KEY_FILTER_NAME).append(i).append('=').append(filter.getName()).append(Misc.LINE_SEPARATOR);
            sb.append(KEY_FILTER_CONFIG).append(i).append('=').append(filter.getConfiguration()).append(Misc.LINE_SEPARATOR);
          }
          sb.append(Misc.LINE_SEPARATOR);
        }

        // writing data to disk
        try (BufferedWriter bw = Files.newBufferedWriter(outFile)) {
          bw.write(sb.toString());
          if (!silent) {
            JOptionPane.showMessageDialog(bam, "Export completed.", "Message",
                                          JOptionPane.INFORMATION_MESSAGE);
          }
          retVal = true;
        } catch (IOException e) {
          e.printStackTrace();
          if (!silent) {
            JOptionPane.showMessageDialog(bam, "Error exporting BAM session.", "Error",
                                          JOptionPane.ERROR_MESSAGE);
          }
        }
      }
      return retVal;
    }

    /** Clears all BAM session data. */
    private void resetData()
    {
      sectionFrames = null;
      sectionCenter = null;
      sectionCycles = null;
      sectionFilters = null;
    }

    /** Shows options dialog and returns whether user selected "Accept" or "Cancel". */
    private boolean getSelection(boolean isExport)
    {
      if (isExport) {
        setTitle("Export BAM session");
        lSelect.setText(QUESTION_EXPORT);
        cbFrames.setEnabled(bam.modelFrames.getSize() > 0);
        cbCenter.setEnabled(bam.modelFrames.getSize() > 0);
        cbCycles.setEnabled(bam.modelCycles.getSize() > 0);
        cbFilters.setEnabled(bam.modelFilters.getSize() > 0);
      } else {
        setTitle("Import BAM session");
        lSelect.setText(QUESTION_IMPORT);
        cbFrames.setEnabled(sectionFrames != null);
        cbCenter.setEnabled(sectionCenter != null);
        cbCycles.setEnabled(sectionCycles != null);
        cbFilters.setEnabled(sectionFilters != null);
      }
      cbFrames.setSelected(cbFrames.isEnabled());
      cbCenter.setSelected(cbCenter.isEnabled());
      cbCycles.setSelected(cbCycles.isEnabled());
      cbFilters.setSelected(cbFilters.isEnabled());
      pack();
      setLocationRelativeTo(bam);
      bAccept.requestFocusInWindow();
      setVisible(true);

      return isAccepted();
    }

    /** Attempts to determine a fitting default name for the ini file. */
    private Path getDefaultIniName(String defaultName)
    {
      Path retVal = null;
      if (bam.modelFrames.getSize() > 0) {
        String name = bam.modelFrames.getElementAt(0).getOption(PseudoBamDecoder.OPTION_STRING_LABEL).toString();
        if (name != null) {
          if (name.indexOf(':') > 0) {
            name = name.substring(0, name.indexOf(':'));
          }
          if (!name.isEmpty()) {
            Path file = ConvertToBam.currentPath.resolve(name);
            retVal = StreamUtils.replaceFileExtension(file, "ini");
          }
        }
      }
      if (retVal == null) {
        Path file = ConvertToBam.currentPath.resolve(defaultName);
        retVal = StreamUtils.replaceFileExtension(file, "ini");
      }

      return retVal;
    }

    /** Returns whether the dialog options have been accepted. */
    private boolean isAccepted()
    {
      return accepted;
    }

    /** Returns whether the frames option has been selected. */
    private boolean isFramesSelected()
    {
      return (cbFrames.isEnabled() && cbFrames.isSelected());
    }

    /** Returns whether the center position option has been selected. */
    private boolean isCenterSelected()
    {
      return (cbCenter.isEnabled() && cbCenter.isSelected());
    }

    /** Returns whether the cycle definition option has been selected. */
    private boolean isCyclesSelected()
    {
      return (cbCycles.isEnabled() && cbCycles.isSelected());
    }

    /** Returns whether the filter configuration option has been selected. */
    private boolean isFiltersSelected()
    {
      return (cbFilters.isEnabled() && cbFilters.isSelected());
    }

    /** Disposes the dialog and marks it as accepted. */
    private void acceptDialog()
    {
      setVisible(false);
      accepted = true;
    }

    /** Disposes the dialog and marks it as cancelled. */
    private void cancel()
    {
      setVisible(false);
      accepted = false;
    }

    /** Initializes the basic dialog layout. */
    private void init()
    {
      setLayout(new BorderLayout());
      GridBagConstraints c = new GridBagConstraints();

      bAccept.addActionListener(this);
      bCancel.addActionListener(this);

      getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), bCancel);
      getRootPane().getActionMap().put(bCancel, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e)
        {
          cancel();
        }
      });
      getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), bAccept);
      getRootPane().getActionMap().put(bAccept, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e)
        {
          acceptDialog();
        }
      });

      JPanel pList = new JPanel(new GridBagLayout());
      lSelect.setText(QUESTION_EXPORT);
      c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
      pList.add(lSelect, c);
      c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 0, 0, 0), 0, 0);
      pList.add(cbFrames, c);
      c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
      pList.add(cbCenter, c);
      c = ViewerUtil.setGBC(c, 0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
      pList.add(cbCycles, c);
      c = ViewerUtil.setGBC(c, 0, 4, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
      pList.add(cbFilters, c);

      JPanel pBottom = new JPanel(new GridBagLayout());
      c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      pBottom.add(new JPanel(), c);
      c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      pBottom.add(bAccept, c);
      c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 0), 0, 0);
      pBottom.add(bCancel, c);
      c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      pBottom.add(new JPanel(), c);

      JPanel pMain = new JPanel(new GridBagLayout());
      c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.BOTH, new Insets(8, 8, 16, 8), 0, 0);
      pMain.add(pList, c);
      c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.BOTH, new Insets(8, 8, 8, 8), 0, 0);
      pMain.add(pBottom, c);

      add(pMain, BorderLayout.CENTER);
      pack();
      setMinimumSize(getPreferredSize());
      setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    }
  }
}
