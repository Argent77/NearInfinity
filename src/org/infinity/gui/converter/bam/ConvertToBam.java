// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.bam;

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
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.NearInfinity;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.ButtonPopupWindow;
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
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamControl;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamCycleEntry;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamFrameEntry;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.Operation;
import org.infinity.util.SimpleListModel;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;
import org.infinity.util.tuples.Couple;

public class ConvertToBam extends ChildFrame implements ActionListener, PropertyChangeListener, FocusListener,
    ChangeListener, ListSelectionListener, MouseListener, KeyListener {
  // Available TabPane indices
  protected static final int TAB_FRAMES   = 0;
  protected static final int TAB_CYCLES   = 1;
  protected static final int TAB_PREVIEW  = 2;
  protected static final int TAB_FILTERS  = 3;
  protected static final String[] TAB_NAMES = { "Frames", "Cycles", "Preview", "Post-processing" };

  // Available DXT compression types for BAM v2 export
  protected static final int COMPRESSION_AUTO = 0;
  protected static final int COMPRESSION_DXT1 = 1;
  protected static final int COMPRESSION_DXT5 = 2;
  protected static final String[] COMPRESSION_ITEMS = { "Auto", "DXT1", "DXT5" };

  // Available BAM versions
  protected static final int VERSION_BAMV1 = 0;
  protected static final int VERSION_BAMV2 = 1;
  protected static final String[] BAM_VERSION_ITEMS = { "Legacy (v1)", "PVRZ-based (v2)" };

  // Alpha component support for BAM v1
  protected static final int ALPHA_AUTO   = 0;
  protected static final int ALPHA_ALWAYS = 1;
  protected static final int ALPHA_NEVER  = 2;
  protected static final String[] USE_ALPHA_ITEMS = { "In EE only", "Always", "Never" };

  // Available playback modes for preview
  protected static final int MODE_CURRENT_CYCLE_ONCE    = 0;
  protected static final int MODE_CURRENT_CYCLE_LOOPED  = 1;
  protected static final int MODE_ALL_CYCLES_ONCE       = 2;
  protected static final int MODE_ALL_CYCLES_LOOPED     = 3;
  protected static final String[] PLAYBACK_MODE_ITEMS = {
      "Current cycle once", "Current cycle looped", "All cycles once", "All cycles looped",
  };

  // PseudoBamDecoder->setOption(): Full path to frame source
  protected static final String BAM_FRAME_OPTION_PATH = "Path";
  // PseudoBamDecoder->setOption(): frame source index (usually 0, except for BAM sources)
  protected static final String BAM_FRAME_OPTION_SOURCE_INDEX = "FrameIndex";

  // Used as prefix if BAM source file is based on a biffed resource
  protected static final String BAM_FRAME_PATH_BIFF = "BIFF:/";

  // Available lists of frame images
  protected static final int BAM_ORIGINAL = 0; // the original unprocessed frames list
  protected static final int BAM_FINAL    = 1; // final frames list including palette and/or post-processor

  // Maps activation state of BAM filters to button labels
  protected static final Map<Boolean, String> FILTER_ACTIVATE_LABELS =
      Misc.mapOf(Boolean.FALSE, "Deactivate", Boolean.TRUE, "Activate");

  private static Path currentPath;

  // Performed if a user-defined amount of cycles should be added at once
  private final Operation opCyclesAdd = () -> {
    final Object result = JOptionPane.showInputDialog(this, "Number of cycles to add:", "Add cycles",
        JOptionPane.QUESTION_MESSAGE, null, null, "1");
    if (result != null) {
      int count = Misc.toNumber(result.toString(), 0);
      if (count > 0) {
        cyclesAdd(count);
      }
    }
  };

  // Global BamDecoder instance for managing frames and cycles
  private final PseudoBamDecoder bamDecoder = new PseudoBamDecoder();
  // BamDecoder instance containing the final result of the current BAM structure
  private final PseudoBamDecoder bamDecoderFinal = new PseudoBamDecoder();
  // Frame image lists (use BAM_ORIGINAL/BAM_FINAL constants for access)
  private final List<List<PseudoBamFrameEntry>> listFrameEntries = new ArrayList<>(2);
  // Frame entry used for preview in filter tab
  private final PseudoBamFrameEntry entryFilterPreview = new PseudoBamFrameEntry(null, 0, 0);
  // The palette dialog instance for BAM v1 export
  private final BamPaletteDialog paletteDialog = new BamPaletteDialog(this);

  // Handles firing delayed action events
  private final DelayedButtonAction delayedAction = new DelayedButtonAction();

  private JTabbedPane tpMain;
  private JTabbedPane tpCyclesSection;
  private BamFramesListModel modelFrames; // Frames == FramesAvail
  private BamCycleFramesListModel modelCurCycle;
  private BamCyclesListModel modelCycles;
  private SimpleListModel<BamFilterBase> modelFilters;
  private JList<PseudoBamFrameEntry> listFrames;
  private JList<PseudoBamFrameEntry> listFramesAvail;
  private JList<PseudoBamFrameEntry> listCurCycle;
  private JList<PseudoBamCycleEntry> listCycles;
  private JList<BamFilterBase> listFilters;
  private JMenuItem miFramesAddFiles;
  private JMenuItem miFramesAddResources;
  private JMenuItem miFramesAddFolder;
  private JMenuItem miFramesImportFile;
  private JMenuItem miFramesImportResource;
  private JMenuItem miFramesRemove;
  private JMenuItem miFramesRemoveAll;
  private JMenuItem miFramesDropUnused;
  private JMenuItem miSessionExport;
  private JMenuItem miSessionImport;
  private JMenu miSessionHistory;
  private ButtonPopupMenu bpmFramesAdd;
  private ButtonPopupMenu bpmFramesRemove;
  private ButtonPopupMenu bpmSession;
  private JButton bOptions;
  private JButton bConvert;
  private JButton bCancel;
  private JButton bPalette;
  private JButton bVersionHelp;
  private JButton bCompressionHelp;
  private JButton bFramesUp;
  private JButton bFramesDown;
  private JButton bCyclesUp;
  private JButton bCyclesDown;
  private JButton bCyclesAdd;
  private JButton bCyclesRemove;
  private JButton bCyclesRemoveAll;
  private JButton bCurCycleUp;
  private JButton bCurCycleDown;
  private JButton bCurCycleAdd;
  private JButton bCurCycleRemove;
  private JButton bMacroAssignFrames;
  private JButton bMacroRemoveFrames;
  private JButton bMacroSortFramesAsc;
  private JButton bMacroDuplicateCycle;
  private JButton bMacroDuplicateFrames;
  private JButton bMacroReverseFrames;
  private JButton bMacroCompactSelection;
  private JButton bMacroInterleaveSelection;
  private JButton bMacroRemoveAll;
  private JButton bMacroReverseCycles;
  private JButton bPreviewCycleFirst;
  private JButton bPreviewCyclePrev;
  private JButton bPreviewCycleNext;
  private JButton bPreviewCycleLast;
  private ButtonPopupWindow bpwPreviewCycleGoto;
  private PreviewGotoCyclePanel previewGotoCyclePanel;
  private JButton bPreviewFrameFirst;
  private JButton bPreviewFramePrev;
  private JButton bPreviewFrameNext;
  private JButton bPreviewFrameLast;
  private JButton bPreviewPlay;
  private JButton bPreviewStop;
  private JButton bFiltersAdd;
  private JButton bFiltersActivate;
  private JButton bFiltersRemove;
  private JButton bFiltersRemoveAll;
  private JButton bFiltersUp;
  private JButton bFiltersDown;
  private JTextField tfFrameWidth;
  private JTextField tfFrameHeight;
  private JTextField tfFrameCenterX;
  private JTextField tfFrameCenterY;
  private JCheckBox cbCloseOnExit;
  private JCheckBox cbCompressFrame;
  private JCheckBox cbCompressBam;
  private JCheckBox cbPreviewShowMarker;
  private JCheckBox cbPreviewZoom;
  private JCheckBox cbFiltersShowMarker;
  private JPanel pFramesCurFrame;
  private JPanel pCurrentCycle;
  private JPanel pFramesOptionsVersion;
  private JPanel pFiltersSettings;
  private JComboBox<String> cbPreviewMode;
  private JComboBox<BamFilterFactory.FilterInfo> cbFiltersAdd;
  private JComboBox<String> cbVersion;
  private JComboBox<String> cbCompression;
  private JTextArea taFiltersDesc;
  private RenderCanvas rcFramesPreview;
  private RenderCanvas rcCyclesPreview;
  private RenderCanvas rcPreview;
  private RenderCanvas rcFiltersPreview;
  private JScrollPane scrollPreview;
  private JScrollPane scrollFiltersPreview;
  private BufferedImage previewCanvas;
  private JLabel lPreviewCycle;
  private JLabel lPreviewFrame;
  private JSpinner sPvrzIndex;
  private JSpinner sPreviewFps;
  private JSpinner sFiltersPreviewFrame;
  private Timer timer;
  private SwingWorker<List<String>, Void> workerConvert;
  private SwingWorker<Void, Void> workerProcess;
  private WindowBlocker blocker;
  private ProgressMonitor progress;
  private PseudoBamControl bamControlPreview;
  private boolean isPreviewModified;
  private boolean isPreviewPlaying;
  private double currentFps;
  private int pmCur;
  private int pmMax;
  private Path bamOutputFile;

  /** Validates numberString and modifies it to fit into the specified limits. */
  public static int numberValidator(String numberString, int min, int max, int defaultValue) {
    int retVal = defaultValue;
    if (numberString != null) {
      try {
        retVal = Integer.parseInt(numberString);
        if (min > max) {
          int tmp = min;
          min = max;
          max = tmp;
        }
        if (retVal < min) {
          retVal = min;
        } else if (retVal > max) {
          retVal = max;
        }
      } catch (NumberFormatException e) {
        Logger.trace(e);
      }
    }
    return retVal;
  }

  /** Validates numberString and modifies it to fit into the specified limits. */
  public static double doubleValidator(String numberString, double min, double max, double defaultValue) {
    double retVal = defaultValue;
    if (numberString != null) {
      try {
        retVal = Double.parseDouble(numberString);
        if (min > max) {
          double tmp = min;
          min = max;
          max = tmp;
        }
        if (retVal < min) {
          retVal = min;
        } else if (retVal > max) {
          retVal = max;
        }
      } catch (NumberFormatException e) {
        Logger.trace(e);
      }
    }
    return retVal;
  }

  /** Returns a list of supported input graphics file formats. */
  public static FileNameExtensionFilter[] getGraphicsFilters() {
    return new FileNameExtensionFilter[] {
        new FileNameExtensionFilter("Graphics files (*.bam, *.bmp, *.gif, *.png, *,jpg, *.jpeg)",
            "bam", "bmp", "gif", "png", "jpg", "jpeg"),
        new FileNameExtensionFilter("BAM files (*.bam)", "bam"),
        new FileNameExtensionFilter("BMP files (*.bmp)", "bmp"),
        new FileNameExtensionFilter("GIF files (*.gif)", "gif"),
        new FileNameExtensionFilter("PNG files (*.png)", "png"),
        new FileNameExtensionFilter("JPEG files (*.jpg, *.jpeg)", "jpg", "jpeg") };
  }

  /** Returns a list of supported input file formats containing palettes. */
  public static FileNameExtensionFilter[] getPaletteFilters() {
    return new FileNameExtensionFilter[] {
        new FileNameExtensionFilter("Palette from files (*.bam, *.bmp, *.png, *.act, *.pal)",
            "bam", "bmp", "png", "act", "pal"),
        new FileNameExtensionFilter("Palette from BAM files (*.bam)", "bam"),
        new FileNameExtensionFilter("Palette from BMP files (*.bmp)", "bmp"),
        new FileNameExtensionFilter("Palette from PNG files (*.png)", "png"),
        new FileNameExtensionFilter("Adobe Color Table files (*.act)", "act"),
        new FileNameExtensionFilter("Microsoft Palette files (*.pal)", "pal"), };
  }

  /** Returns a extension filter for BAM files. */
  public static FileNameExtensionFilter getBamFilter() {
    return new FileNameExtensionFilter("BAM files (*.bam)", "bam");
  }

  /** Returns a list of files that can be specified in an "Open file" dialog. */
  public static Path[] getOpenFileName(Component parent, String title, Path rootPath, boolean selectMultiple,
      FileNameExtensionFilter[] filters, int filterIndex) {
    if (rootPath == null) {
      rootPath = currentPath;
    }
    JFileChooser fc = new JFileChooser(rootPath.toFile());
    if (!FileEx.create(rootPath).isDirectory()) {
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
      for (final FileNameExtensionFilter filter : filters) {
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
        return new Path[] { fc.getSelectedFile().toPath() };
      }
    } else {
      return null;
    }
  }

  /** Returns a path name from a "Select path" dialog. */
  public static Path getOpenPathName(Component parent, String title, Path rootPath) {
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
  public static Path getSaveFileName(Component parent, String title, Path rootPath, FileNameExtensionFilter[] filters,
      int filterIndex) {
    if (rootPath == null) {
      rootPath = currentPath;
    }
    JFileChooser fc = new JFileChooser(rootPath.toFile());
    if (!FileEx.create(rootPath).isDirectory()) {
      fc.setSelectedFile(rootPath.toFile());
    }
    if (title == null) {
      title = "Specify filename";
    }
    fc.setDialogTitle(title);
    fc.setDialogType(JFileChooser.SAVE_DIALOG);
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    if (filters != null) {
      for (final FileNameExtensionFilter filter : filters) {
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

  /** Returns the last active working path that was used by open/save dialogs in the BAM Converter. */
  public static Path getCurrentPath() {
    return currentPath;
  }

  public ConvertToBam() {
    super("Convert image sequence to BAM", true);
    init();
  }

  /**
   * Returns the BAM decoder instance that is used internally to manage the BAM structure.
   *
   * @param bamType Either one of {@link #BAM_ORIGINAL} or {@link #BAM_FINAL}.
   */
  public PseudoBamDecoder getBamDecoder(int bamType) {
    return (bamType == BAM_FINAL) ? bamDecoderFinal : bamDecoder;
  }

  /** Returns the BAM decoder instance that is used to display the BAM in the preview tab. */
  public PseudoBamDecoder getPreviewBamDecoder() {
    return bamDecoderFinal;
  }

  /** Returns the associated BAM v1 palette dialog. */
  public BamPaletteDialog getPaletteDialog() {
    return paletteDialog;
  }

  /** Convenience method: Returns true if BAM v1 has been selected. */
  public boolean isBamV1Selected() {
    return (cbVersion.getSelectedIndex() == VERSION_BAMV1);
  }

  /** Returns the currently selected output BAM version. */
  public int getBamVersion() {
    return cbVersion.getSelectedIndex();
  }

  /** Returns the BAM output file. */
  public Path getBamOutput() {
    return bamOutputFile;
  }

  /** Predefines BAM output file or path. */
  public void setBamOutput(Path path) {
    bamOutputFile = path;
  }

  /** Returns whether BAM v1 output is compressed. */
  public boolean isBamV1Compressed() {
    return cbCompressBam.isSelected();
  }

  /** Returns the threshold used to determine transparent colors. Range: [0, 255]. */
  public static int getTransparencyThreshold() {
    return (255 * BamOptionsDialog.getTransparencyThreshold()) / 100;
  }

  /** Returns whether alpha channel support is enabled. */
  public static boolean getUseAlpha() {
    return (BamOptionsDialog.getUseAlpha() == ALPHA_ALWAYS)
        || (Profile.isEnhancedEdition() && BamOptionsDialog.getUseAlpha() == ALPHA_AUTO);
  }

  /** Returns the start index for PVRZ files used for BAM v2 output. */
  public int getPvrzIndex() {
    return ((Integer) sPvrzIndex.getValue());
  }

  /** Returns the selected DXT type for BAM v2. */
  public DxtEncoder.DxtType getDxtType() {
    switch (cbCompression.getSelectedIndex()) {
      case COMPRESSION_DXT1:
        return DxtEncoder.DxtType.DXT1;
      case COMPRESSION_DXT5:
        return DxtEncoder.DxtType.DXT5;
      default:
        return getAutoDxtType();
    }
  }

  // --------------------- Begin Class ChildFrame ---------------------

  @Override
  protected boolean windowClosing(boolean forced) throws Exception {
    BamOptionsDialog.saveRecentSessions();
    if (forced || confirmCloseDialog()) {
      clear();
      return true;
    } else {
      return false;
    }
  }

  // --------------------- End Class ChildFrame ---------------------

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == timer) {
      if (!previewAdvanceAnimation()) {
        previewStop();
      }
    } else if (event.getSource() == bOptions) {
      new BamOptionsDialog(this);
    } else if (event.getSource() == bConvert) {
      if (workerConvert == null) {
        final String msg = "BAM output file already exists. Overwrite?";
        Path file;
        do {
          file = setBamOutput();
          if (file != null) {
            if (!FileEx.create(file).exists() || JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg,
                "Question", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)) {
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
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Question", JOptionPane.YES_NO_OPTION,
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
      cyclesAdd(1);
    } else if (event.getSource() == bCyclesRemove) {
      cyclesRemove();
    } else if (event.getSource() == bCyclesRemoveAll) {
      if (!modelCycles.isEmpty()) {
        final String msg = "Do you really want to remove all cycles?";
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Question", JOptionPane.YES_NO_OPTION,
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
    } else if (event.getSource() == bMacroCompactSelection) {
      macroCompactCycles();
    } else if (event.getSource() == bMacroInterleaveSelection) {
      macroInterleaveCycles();
    } else if (event.getSource() == bCurCycleAdd) {
      currentCycleAdd();
    } else if (event.getSource() == bCurCycleRemove) {
      currentCycleRemove();
    } else if (event.getSource() == bCurCycleUp) {
      currentCycleMoveUp();
    } else if (event.getSource() == bCurCycleDown) {
      currentCycleMoveDown();
    } else if (event.getSource() == bPreviewCycleFirst) {
      previewCycleStart();
    } else if (event.getSource() == bPreviewCyclePrev) {
      previewCycleDown();
    } else if (event.getSource() == bPreviewCycleNext) {
      previewCycleUp();
    } else if (event.getSource() == bPreviewCycleLast) {
      previewCycleEnd();
    } else if (event.getSource() == previewGotoCyclePanel) {
      if (PreviewGotoCyclePanel.ACTION_ACCEPT.equals(event.getActionCommand())) {
        previewSetCycle(previewGotoCyclePanel.getValue());
      }
      bpwPreviewCycleGoto.hidePopupWindow();
    } else if (event.getSource() == bpwPreviewCycleGoto) {
      previewCycleGotoInit();
    } else if (event.getSource() == bPreviewFrameFirst) {
      previewFrameStart();
    } else if (event.getSource() == bPreviewFramePrev) {
      previewFrameDown();
    } else if (event.getSource() == bPreviewFrameNext) {
      previewFrameUp();
    } else if (event.getSource() == bPreviewFrameLast) {
      previewFrameEnd();
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
    } else if (event.getSource() == bFiltersActivate) {
      filterToggleState();
    } else if (event.getSource() == bFiltersRemove) {
      filterRemove();
    } else if (event.getSource() == bFiltersRemoveAll) {
      if (!modelFilters.isEmpty()) {
        final String msg = "Do you really want to remove all filters?";
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Question", JOptionPane.YES_NO_OPTION,
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
      DataMenuItem dmi = (DataMenuItem) event.getSource();
      if (dmi.getData() instanceof Path) {
        Path path = (Path) dmi.getData();
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

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface PropertyChangeListener ---------------------

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (event.getSource() == workerConvert) {
      if ("state".equals(event.getPropertyName()) && SwingWorker.StateValue.DONE == event.getNewValue()) {

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
          Logger.error(e);
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
      if ("state".equals(event.getPropertyName()) && SwingWorker.StateValue.DONE == event.getNewValue()) {

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

  // --------------------- End Interface PropertyChangeListener ---------------------

  // --------------------- Begin Interface FocusListener ---------------------

  @Override
  public void focusGained(FocusEvent event) {
    if (event.getSource() == tfFrameCenterX || event.getSource() == tfFrameCenterY) {
      ((JTextField) event.getSource()).selectAll();
    }
  }

  @Override
  public void focusLost(FocusEvent event) {
    if (event.getSource() == tfFrameCenterX || event.getSource() == tfFrameCenterY) {
      framesValidateCenterValue((JTextField) event.getSource());
    }
  }

  // --------------------- End Interface FocusListener ---------------------

  // --------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent event) {
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
      double v = (Double) sPreviewFps.getValue();
      previewSetFrameRate(v);
    } else if (event.getSource() == sFiltersPreviewFrame) {
      filterSetPreviewFrame(filterGetPreviewFrameIndex(), true);
    } else if (event.getSource() instanceof BamFilterBase) {
      outputSetModified(true);
      filterSetPreviewFrame(filterGetPreviewFrameIndex(), false);
    }
  }

  // --------------------- End Interface ChangeListener ---------------------

  // --------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent event) {
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

  // --------------------- End Interface ListSelectionListener ---------------------

  // --------------------- Begin Interface MouseListener ---------------------

  @Override
  public void mouseClicked(MouseEvent event) {
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
  public void mousePressed(MouseEvent event) {
    if (event.getSource() == bCyclesAdd) {
      // long-tap action: add user-defined amount of cycles to the BAM
      delayedAction.performDelayedAction(bCyclesAdd, 1000, opCyclesAdd);
    }
  }

  @Override
  public void mouseReleased(MouseEvent event) {
  }

  @Override
  public void mouseEntered(MouseEvent event) {
  }

  @Override
  public void mouseExited(MouseEvent event) {
  }

  // --------------------- End Interface MouseListener ---------------------

  // --------------------- Begin Interface KeyListener ---------------------

  @Override
  public void keyTyped(KeyEvent e) {
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (e.getSource() == listFrames) {
      if (e.getKeyCode() == KeyEvent.VK_DELETE) {
        doWithSelectedListItems(listFrames, list -> framesRemove(), true, "Remove selected frame?",
            "Remove %d selected frames?");
      }
    } else if (e.getSource() == listCurCycle) {
      if (e.getKeyCode() == KeyEvent.VK_DELETE) {
        doWithSelectedListItems(listCurCycle, list -> currentCycleRemove(), true, "Remove selected frame index?",
            "Remove %d selected frame indices?");
      }
    } else if (e.getSource() == listCycles) {
      if (e.getKeyCode() == KeyEvent.VK_DELETE) {
        doWithSelectedListItems(listCycles, list -> cyclesRemove(), true, "Remove selected cycle?",
            "Remove %d selected cycles?");
      }
    } else if (e.getSource() == listFilters) {
      if (e.getKeyCode() == KeyEvent.VK_DELETE) {
        // List is set to single selection mode
        doWithSelectedListItems(listFilters, list -> filterRemove(), true, "Remove selected filter?", null);
      }
    } else if (e.getSource() == tfFrameCenterX) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        // Accept current input value
        framesValidateCenterValue(tfFrameCenterX);
        tfFrameCenterY.requestFocusInWindow();
      }
    } else if (e.getSource() == tfFrameCenterY) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        // Accept current input value
        framesValidateCenterValue(tfFrameCenterY);
        cbCompressFrame.requestFocusInWindow();
      }
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
  }

  // --------------------- End Interface KeyListener ---------------------

  private void init() {
    setIconImage(Icons.ICON_APPLICATION_16.getIcon().getImage());
    BamOptionsDialog.loadSettings(false);

    if (BamOptionsDialog.getPath().isEmpty()) {
      currentPath = Profile.getGameRoot();
    } else {
      currentPath = FileManager.resolve(BamOptionsDialog.getPath());
    }

    // initializing frame image lists
    listFrameEntries.add(bamDecoder.getFramesList()); // original frames list
    listFrameEntries.add(bamDecoderFinal.getFramesList()); // processed frames list
    bamDecoderFinal.setCyclesList(bamDecoder.getCyclesList()); // shared cycles list

    JComponent pFrames = createFramesTab();
    JComponent pCycles = createCyclesTab();
    JComponent pPreview = createPreviewTab();
    JComponent pFilters = createFiltersTab();

    // setting up tabbed pane
    tpMain = new JTabbedPane(SwingConstants.TOP);
    tpMain.addTab(TAB_NAMES[TAB_FRAMES], pFrames);
    tpMain.setMnemonicAt(TAB_FRAMES, KeyEvent.VK_F);
    tpMain.addTab(TAB_NAMES[TAB_CYCLES], pCycles);
    tpMain.setMnemonicAt(TAB_CYCLES, KeyEvent.VK_C);
    tpMain.addTab(TAB_NAMES[TAB_PREVIEW], pPreview);
    tpMain.setMnemonicAt(TAB_PREVIEW, KeyEvent.VK_P);
    tpMain.addTab(TAB_NAMES[TAB_FILTERS], pFilters);
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
    bpmSession.setIcon(Icons.ICON_ARROW_UP_15.getIcon());
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
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(bpmSession, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 6, 0, 0), 0, 0);
    pButtons.add(bOptions, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pButtons.add(cbCloseOnExit, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(bConvert, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pButtons.add(bCancel, c);

    // putting all sections together
    setLayout(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
        new Insets(8, 8, 0, 8), 0, 0);
    add(tpMain, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 8, 8, 8), 0, 0);
    add(pButtons, c);

    // finalizing dialog initialization
    pack();
    setMinimumSize(getPreferredSize());
    setLocationRelativeTo(getParent());
    updateFramesList();
    updateFrameInfo(listFrames.getSelectedIndices());
    updateQuickPreview(rcFramesPreview, listFrames.getSelectedIndices(), true);
    updateQuickPreview(rcCyclesPreview, listFramesAvail.getSelectedIndices(), false);
    initCurrentCycle(listCycles.getSelectedIndex(), false);
    updateStatus();
    setVisible(true);
  }

  /** Creating panel "Frames". */
  private JComponent createFramesTab() {
    GridBagConstraints c = new GridBagConstraints();

    // creating "Frames List"
    JPanel pFramesListArrows = new JPanel(new GridBagLayout());
    bFramesUp = new JButton(Icons.ICON_UP_16.getIcon());
    bFramesUp.setMargin(new Insets(bFramesUp.getInsets().top, 2, bFramesUp.getInsets().bottom, 2));
    bFramesUp.addActionListener(this);
    bFramesDown = new JButton(Icons.ICON_DOWN_16.getIcon());
    bFramesDown.setMargin(new Insets(bFramesDown.getInsets().top, 2, bFramesDown.getInsets().bottom, 2));
    bFramesDown.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_END, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 16);
    pFramesListArrows.add(bFramesUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(8, 0, 0, 0), 0, 16);
    pFramesListArrows.add(bFramesDown, c);

    JPanel pFramesAdd = new JPanel(new GridBagLayout());
    miFramesAddFiles = new JMenuItem("Add file(s)...");
    miFramesAddFiles.setToolTipText("Add only frames from an external graphics file. Cycle definitions are ignored.");
    miFramesAddFiles.addActionListener(this);
    miFramesAddResources = new JMenuItem("Add resource(s)...");
    miFramesAddResources
        .setToolTipText("Add only frames from an internal graphics resource. Cycle definitions are ignored.");
    miFramesAddResources.addActionListener(this);
    miFramesAddFolder = new JMenuItem("Add folder...");
    miFramesAddFolder.addActionListener(this);
    miFramesImportFile = new JMenuItem("Import BAM file...");
    miFramesImportFile.setToolTipText("Import both frame and cycle definitions from an external BAM file.");
    miFramesImportFile.addActionListener(this);
    miFramesImportResource = new JMenuItem("Import BAM resource...");
    miFramesImportResource.setToolTipText("Import both frame and cycle definitions from an internal BAM resource.");
    miFramesImportResource.addActionListener(this);
    bpmFramesAdd = new ButtonPopupMenu("Add...", new JMenuItem[] { miFramesAddFiles, miFramesAddResources,
        miFramesAddFolder, miFramesImportFile, miFramesImportResource }, false, ButtonPopupMenu.Align.TOP);
    bpmFramesAdd.setIcon(Icons.ICON_ARROW_UP_15.getIcon());
    bpmFramesAdd.setIconTextGap(8);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pFramesAdd.add(bpmFramesAdd, c);

    JPanel pFramesRemove = new JPanel(new GridBagLayout());
    miFramesRemove = new JMenuItem("Remove");
    miFramesRemove.addActionListener(this);
    miFramesRemoveAll = new JMenuItem("Remove all");
    miFramesRemoveAll.addActionListener(this);
    miFramesDropUnused = new JMenuItem("Drop unused frames");
    miFramesDropUnused.addActionListener(this);
    miFramesDropUnused.setToolTipText("Remove frames that are not used in any cycle definitions.");
    bpmFramesRemove = new ButtonPopupMenu("Remove...",
        new JMenuItem[] { miFramesRemove, miFramesRemoveAll, miFramesDropUnused });
    bpmFramesRemove.setIcon(Icons.ICON_ARROW_UP_15.getIcon());
    bpmFramesRemove.setIconTextGap(8);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pFramesRemove.add(bpmFramesRemove, c);

    JPanel pFramesListButtons = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pFramesListButtons.add(pFramesAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pFramesListButtons.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pFramesListButtons.add(pFramesRemove, c);

    JPanel pFramesList = new JPanel(new GridBagLayout());
    JLabel lFramesTitle = new JLabel("Frames:");
    modelFrames = new BamFramesListModel(this);
    listFrames = new JList<>(modelFrames);
    listFrames.setCellRenderer(new IndexedCellRenderer());
    listFrames.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    listFrames.addListSelectionListener(this);
    listFrames.addKeyListener(this);
    listFrames.setDropMode(DropMode.INSERT);
    listFrames.setTransferHandler(new ListFileTransferHandler());
    listFrames.setDragEnabled(true);
    JScrollPane scroll = new JScrollPane(listFrames);
    c = ViewerUtil.setGBC(c, 0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 4, 0), 0, 0);
    pFramesList.add(lFramesTitle, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    pFramesList.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 8, 0, 0), 0, 0);
    pFramesList.add(pFramesListArrows, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
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
    String tip = "Tip: You can prefix the entered number by '++' for adding to or '- -' "
        + "for subtracting from the current center coordinate.";
    tfFrameCenterX = new JTextField("0", 6);
    tfFrameCenterX.setToolTipText(tip);
    tfFrameCenterX.addFocusListener(this);
    tfFrameCenterX.addKeyListener(this);
    tfFrameCenterY = new JTextField("0", 6);
    tfFrameCenterY.setToolTipText(tip);
    tfFrameCenterY.addFocusListener(this);
    tfFrameCenterY.addKeyListener(this);
    cbCompressFrame = new JCheckBox("Compress frame");
    cbCompressFrame.setToolTipText("Selecting this option activates RLE compression for the current frame(s).");
    cbCompressFrame.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesWidth, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(tfFrameWidth, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 16, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesCenterX, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 8, 0, 4), 0, 0);
    pFramesCurFrame.add(tfFrameCenterX, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesHeight, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 8, 0, 0), 0, 0);
    pFramesCurFrame.add(tfFrameHeight, c);
    c = ViewerUtil.setGBC(c, 2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 16, 0, 0), 0, 0);
    pFramesCurFrame.add(lFramesCenterY, c);
    c = ViewerUtil.setGBC(c, 3, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 8, 0, 4), 0, 0);
    pFramesCurFrame.add(tfFrameCenterY, c);
    c = ViewerUtil.setGBC(c, 0, 2, 4, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 4, 4, 4), 0, 0);
    pFramesCurFrame.add(cbCompressFrame, c);
    Component[] orderList = new Component[] { tfFrameWidth, tfFrameHeight, tfFrameCenterX, tfFrameCenterY };
    pFramesCurFrame.setFocusTraversalPolicy(new FixedFocusTraversalPolicy(orderList));
    pFramesCurFrame.setFocusTraversalPolicyProvider(true);

    // creating "Quick View" section
    JPanel pFramesQuickView = new JPanel(new GridBagLayout());
    pFramesQuickView.setBorder(BorderFactory.createTitledBorder("Quick Preview "));
    rcFramesPreview = new RenderCanvas(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 4, 4), 0, 0);
    pFramesQuickView.add(rcFramesPreview, c);
    pFramesQuickView.setMinimumSize(pFramesQuickView.getPreferredSize());

    // creating "Import" section
    JPanel pFramesImport = new JPanel(new GridBagLayout());
    pFramesImport.setBorder(BorderFactory.createTitledBorder("Import "));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 2, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 4, 16), 0, 0);
    pFramesImport.add(pFramesList, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 4), 0, 0);
    pFramesImport.add(pFramesCurFrame, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 8, 4), 0, 0);
    pFramesImport.add(pFramesQuickView, c);

    // creating "Export options" sections
    JPanel pFramesOptionsVersionV1 = new JPanel(new GridBagLayout());
    cbCompressBam = new JCheckBox("Compress BAM", BamOptionsDialog.getCompressBam());
    cbCompressBam.setToolTipText("Create a zlib compressed BAM file (BAMC)");
    cbCompressBam.addActionListener(this);
    bPalette = new JButton("Palette...");
    bPalette.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    pFramesOptionsVersionV1.add(bPalette, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    pFramesOptionsVersionV1.add(cbCompressBam, c);

    JPanel pFramesOptionsVersionV2 = new JPanel(new GridBagLayout());
    JLabel lPvrzIndex = new JLabel("PVRZ index starts at:");
    sPvrzIndex = new JSpinner(new SpinnerNumberModel(BamOptionsDialog.getPvrzIndex(), 0, 99999, 1));
    sPvrzIndex.setToolTipText("Enter a number from 0 to 99999");
    sPvrzIndex.addChangeListener(this);
    JLabel lFramesCompression = new JLabel("Compression type:");
    cbCompression = new JComboBox<>(COMPRESSION_ITEMS);
    cbCompression.setSelectedIndex(BamOptionsDialog.getCompressionType());
    bCompressionHelp = new JButton("?");
    bCompressionHelp.setMargin(new Insets(2, 4, 2, 4));
    bCompressionHelp.setToolTipText("About compression types");
    bCompressionHelp.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 0, 0);
    pFramesOptionsVersionV2.add(lPvrzIndex, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pFramesOptionsVersionV2.add(sPvrzIndex, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 16, 0, 0), 0, 0);
    pFramesOptionsVersionV2.add(lFramesCompression, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 8, 0);
    pFramesOptionsVersionV2.add(cbCompression, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 4), 0, 0);
    pFramesOptionsVersionV2.add(bCompressionHelp, c);

    pFramesOptionsVersion = new JPanel(new CardLayout());
    pFramesOptionsVersion.add(pFramesOptionsVersionV1, "V1");
    pFramesOptionsVersion.add(pFramesOptionsVersionV2, "V2");

    // creating "Output options" section
    JPanel pFramesExport = new JPanel(new GridBagLayout());
    pFramesExport.setBorder(BorderFactory.createTitledBorder("Output options "));
    JLabel lFramesVersion = new JLabel("BAM version:");
    cbVersion = new JComboBox<>(BAM_VERSION_ITEMS);
    cbVersion.addActionListener(this);
    cbVersion.setSelectedIndex(BamOptionsDialog.getBamVersion());
    bVersionHelp = new JButton("?");
    bVersionHelp.setMargin(new Insets(2, 4, 2, 4));
    bVersionHelp.setToolTipText("About BAM versions");
    bVersionHelp.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(8, 4, 8, 0), 0, 0);
    pFramesExport.add(lFramesVersion, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 8, 0, 0), 8, 0);
    pFramesExport.add(cbVersion, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pFramesExport.add(bVersionHelp, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 0, 0), 0, 0);
    pFramesExport.add(pFramesOptionsVersion, c);

    // putting all together
    JPanel pFrames = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(8, 8, 0, 8), 0, 0);
    pFrames.add(pFramesImport, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 8, 8), 0, 0);
    pFrames.add(pFramesExport, c);

    return pFrames;
  }

  private JComponent createCyclesTab() {
    GridBagConstraints c = new GridBagConstraints();

    // creating "Cycles" section
    JPanel pCyclesButtons = new JPanel(new GridBagLayout());
    bCyclesAdd = new JButton("Add cycle(s)");
    bCyclesAdd.setToolTipText("Press and hold button to specify amount of cycles to add.");
    bCyclesAdd.addActionListener(this);
    bCyclesAdd.addMouseListener(this);
    bCyclesRemove = new JButton("Remove cycle(s)");
    bCyclesRemove.addActionListener(this);
    bCyclesRemoveAll = new JButton("Remove all");
    bCyclesRemoveAll.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pCyclesButtons.add(bCyclesAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pCyclesButtons.add(bCyclesRemove, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pCyclesButtons.add(bCyclesRemoveAll, c);

    JPanel pCyclesArrows = new JPanel(new GridBagLayout());
    bCyclesUp = new JButton(Icons.ICON_UP_16.getIcon());
    bCyclesUp.setMargin(new Insets(bCyclesUp.getInsets().top, 2, bCyclesUp.getInsets().bottom, 2));
    bCyclesUp.addActionListener(this);
    bCyclesDown = new JButton(Icons.ICON_DOWN_16.getIcon());
    bCyclesDown.setMargin(new Insets(bCyclesDown.getInsets().top, 2, bCyclesDown.getInsets().bottom, 2));
    bCyclesDown.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 16);
    pCyclesArrows.add(bCyclesUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(8, 0, 0, 0), 0, 16);
    pCyclesArrows.add(bCyclesDown, c);

    JPanel pCyclesList = new JPanel(new GridBagLayout());
    JLabel lCycles = new JLabel("Cycles:");
    modelCycles = new BamCyclesListModel(this);
    listCycles = new JList<>(modelCycles);
    listCycles.setCellRenderer(new IndexedCellRenderer());
    listCycles.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    listCycles.addListSelectionListener(this);
    listCycles.addKeyListener(this);
    JScrollPane scroll = new JScrollPane(listCycles);
    c = ViewerUtil.setGBC(c, 0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 4, 0), 0, 0);
    pCyclesList.add(lCycles, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    pCyclesList.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 2, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pCyclesList.add(pCyclesArrows, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
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
    bMacroCompactSelection = new JButton("Compact cycles");
    bMacroCompactSelection.setToolTipText("Removes gaps between selected cycles.");
    bMacroCompactSelection.addActionListener(this);
    bMacroInterleaveSelection = new JButton("Interleave cycles");
    bMacroInterleaveSelection.setToolTipText("Inserts gaps between selected cycles.");
    bMacroInterleaveSelection.addActionListener(this);

    JLabel lMacroAllCycles = new JLabel("All cycles:");
    bMacroRemoveAll = new JButton("Remove all frames");
    bMacroRemoveAll.addActionListener(this);
    bMacroReverseCycles = new JButton("Reverse cycles order");
    bMacroReverseCycles.addActionListener(this);

    JPanel pMacroPanel = new JPanel(new GridBagLayout());
    int row = 0;
    c = ViewerUtil.setGBC(c, 0, row, 2, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pMacroPanel.add(lMacroCurCycle, c);
    row++;
    c = ViewerUtil.setGBC(c, 0, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroAssignFrames, c);
    c = ViewerUtil.setGBC(c, 1, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroRemoveFrames, c);
    row++;
    c = ViewerUtil.setGBC(c, 0, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroDuplicateFrames, c);
    c = ViewerUtil.setGBC(c, 1, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroDuplicateCycle, c);
    row++;
    c = ViewerUtil.setGBC(c, 0, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroSortFramesAsc, c);
    c = ViewerUtil.setGBC(c, 1, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroReverseFrames, c);
    row++;

    c = ViewerUtil.setGBC(c, 0, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroCompactSelection, c);
    c = ViewerUtil.setGBC(c, 1, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroInterleaveSelection, c);
    row++;

    c = ViewerUtil.setGBC(c, 0, row, 2, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(16, 0, 0, 0), 0, 0);
    row++;

    pMacroPanel.add(lMacroAllCycles, c);
    c = ViewerUtil.setGBC(c, 0, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 0, 0);
    pMacroPanel.add(bMacroRemoveAll, c);
    c = ViewerUtil.setGBC(c, 1, row, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 0, 0), 0, 0);
    pMacroPanel.add(bMacroReverseCycles, c);
    row++;
    c = ViewerUtil.setGBC(c, 0, row, 2, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    pMacroPanel.add(new JPanel(), c);

    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 8, 8, 8), 0, 0);
    pMacros.add(pMacroPanel, c);
    pMacros.setMinimumSize(pMacros.getPreferredSize());

    JPanel pPreview = new JPanel(new GridBagLayout());
    rcCyclesPreview = new RenderCanvas(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 4, 4), 0, 0);
    pPreview.add(rcCyclesPreview, c);
    pPreview.setMinimumSize(pPreview.getPreferredSize());

    tpCyclesSection = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    tpCyclesSection.addTab("Macros", pMacros);
    tpCyclesSection.addTab("Quick Preview", pPreview);
    tpCyclesSection.setSelectedIndex(0);

    JPanel pTopHalf = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 4, 0), 0, 0);
    pTopHalf.add(pCyclesList, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 16, 4, 0), 0, 0);
    pTopHalf.add(tpCyclesSection, c);

    // creating "Current Cycles" section
    JPanel pCycleTransfer = new JPanel(new GridBagLayout());
    bCurCycleAdd = new JButton(Icons.ICON_FORWARD_16.getIcon());
    bCurCycleAdd.setMargin(new Insets(2, bCurCycleAdd.getInsets().left, 2, bCurCycleAdd.getInsets().right));
    bCurCycleAdd.addActionListener(this);
    bCurCycleRemove = new JButton(Icons.ICON_BACK_16.getIcon());
    bCurCycleRemove.setMargin(new Insets(2, bCurCycleRemove.getInsets().left, 2, bCurCycleRemove.getInsets().right));
    bCurCycleRemove.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 8, 0);
    pCycleTransfer.add(bCurCycleAdd, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 8, 0);
    pCycleTransfer.add(bCurCycleRemove, c);

    JPanel pCurCycleArrows = new JPanel(new GridBagLayout());
    bCurCycleUp = new JButton(Icons.ICON_UP_16.getIcon());
    bCurCycleUp.setMargin(new Insets(bCurCycleUp.getInsets().top, 2, bCurCycleUp.getInsets().bottom, 2));
    bCurCycleUp.addActionListener(this);
    bCurCycleDown = new JButton(Icons.ICON_DOWN_16.getIcon());
    bCurCycleDown.setMargin(new Insets(bCurCycleDown.getInsets().top, 2, bCurCycleDown.getInsets().bottom, 2));
    bCurCycleDown.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 16);
    pCurCycleArrows.add(bCurCycleUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(8, 0, 0, 0), 0, 16);
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
    listCurCycle.addKeyListener(this);
    listCurCycle.addMouseListener(this);
    JScrollPane scroll2 = new JScrollPane(listCurCycle);

    pCurrentCycle = new JPanel(new GridBagLayout());
    pCurrentCycle.setBorder(BorderFactory.createTitledBorder("No cycle selected "));
    c = ViewerUtil.setGBC(c, 0, 0, 2, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pCurrentCycle.add(lCurCycleFrames, c);
    c = ViewerUtil.setGBC(c, 2, 0, 2, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pCurrentCycle.add(lCurCycle, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 4, 0), 0, 0);
    pCurrentCycle.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 4), 0, 0);
    pCurrentCycle.add(pCycleTransfer, c);
    c = ViewerUtil.setGBC(c, 2, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 0, 4, 0), 0, 0);
    pCurrentCycle.add(scroll2, c);
    c = ViewerUtil.setGBC(c, 3, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.VERTICAL,
        new Insets(4, 4, 0, 4), 0, 0);
    pCurrentCycle.add(pCurCycleArrows, c);

    // putting all together
    JSplitPane pCycles = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, pTopHalf, pCurrentCycle);
    pCycles.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    return pCycles;
  }

  private JComponent createPreviewTab() {
    bamControlPreview = null;

    GridBagConstraints c = new GridBagConstraints();

    // create bottom control bar
    lPreviewCycle = new JLabel("Cycle: X/Y");
    bPreviewCycleFirst = new JButton(Icons.ICON_FIRST_16.getIcon());
    bPreviewCycleFirst
        .setMargin(new Insets(bPreviewCycleFirst.getMargin().top, 8, bPreviewCycleFirst.getMargin().bottom, 8));
    bPreviewCycleFirst.addActionListener(this);
    bPreviewCyclePrev = new JButton(Icons.ICON_BACK_16.getIcon());
    bPreviewCyclePrev
        .setMargin(new Insets(bPreviewCyclePrev.getMargin().top, 8, bPreviewCyclePrev.getMargin().bottom, 8));
    bPreviewCyclePrev.addActionListener(this);
    bPreviewCycleNext = new JButton(Icons.ICON_FORWARD_16.getIcon());
    bPreviewCycleNext
        .setMargin(new Insets(bPreviewCycleNext.getMargin().top, 8, bPreviewCycleNext.getMargin().bottom, 8));
    bPreviewCycleNext.addActionListener(this);
    bPreviewCycleLast = new JButton(Icons.ICON_LAST_16.getIcon());
    bPreviewCycleLast
        .setMargin(new Insets(bPreviewCycleLast.getMargin().top, 8, bPreviewCycleLast.getMargin().bottom, 8));
    bPreviewCycleLast.addActionListener(this);

    // TODO: replace JPanel by a custom "Goto index" input mask
    previewGotoCyclePanel = new PreviewGotoCyclePanel();
    previewGotoCyclePanel.addActionListener(this);
    bpwPreviewCycleGoto = new ButtonPopupWindow("", Icons.ICON_GOTO_24.getIcon(), previewGotoCyclePanel,
        ButtonPopupWindow.Align.TOP);
    bpwPreviewCycleGoto
        .setMargin(new Insets(bpwPreviewCycleGoto.getMargin().top, 4, bpwPreviewCycleGoto.getMargin().bottom, 4));
    bpwPreviewCycleGoto.setToolTipText("Jump to a specific cycle.");
    bpwPreviewCycleGoto.addActionListener(this);

    lPreviewFrame = new JLabel("Frame: X/Y");
    bPreviewFrameFirst = new JButton(Icons.ICON_FIRST_16.getIcon());
    bPreviewFrameFirst
        .setMargin(new Insets(bPreviewFrameFirst.getMargin().top, 8, bPreviewFrameFirst.getMargin().bottom, 8));
    bPreviewFrameFirst.addActionListener(this);
    bPreviewFramePrev = new JButton(Icons.ICON_BACK_16.getIcon());
    bPreviewFramePrev
        .setMargin(new Insets(bPreviewFramePrev.getMargin().top, 8, bPreviewFramePrev.getMargin().bottom, 8));
    bPreviewFramePrev.addActionListener(this);
    bPreviewFrameNext = new JButton(Icons.ICON_FORWARD_16.getIcon());
    bPreviewFrameNext
        .setMargin(new Insets(bPreviewFrameNext.getMargin().top, 8, bPreviewFrameNext.getMargin().bottom, 8));
    bPreviewFrameNext.addActionListener(this);
    bPreviewFrameLast = new JButton(Icons.ICON_LAST_16.getIcon());
    bPreviewFrameLast
        .setMargin(new Insets(bPreviewFrameLast.getMargin().top, 8, bPreviewFrameLast.getMargin().bottom, 8));
    bPreviewFrameLast.addActionListener(this);

    bPreviewPlay = new JButton("Pause", Icons.ICON_PLAY_16.getIcon());
    bPreviewPlay.setMinimumSize(bPreviewPlay.getPreferredSize());
    bPreviewPlay.setText("Play");
    bPreviewPlay.addActionListener(this);
    bPreviewStop = new JButton("Stop", Icons.ICON_STOP_16.getIcon());
    bPreviewStop.addActionListener(this);
    JPanel pControls = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pControls.add(lPreviewCycle, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pControls.add(bPreviewCycleFirst, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewCyclePrev, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewCycleNext, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewCycleLast, c);
    c = ViewerUtil.setGBC(c, 5, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pControls.add(bpwPreviewCycleGoto, c);
    c = ViewerUtil.setGBC(c, 6, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 16, 0, 0), 0, 0);
    pControls.add(lPreviewFrame, c);
    c = ViewerUtil.setGBC(c, 7, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pControls.add(bPreviewFrameFirst, c);
    c = ViewerUtil.setGBC(c, 8, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewFramePrev, c);
    c = ViewerUtil.setGBC(c, 9, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewFrameNext, c);
    c = ViewerUtil.setGBC(c, 10, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 2, 0, 0), 0, 0);
    pControls.add(bPreviewFrameLast, c);
    c = ViewerUtil.setGBC(c, 11, 0, 1, 1, 0.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 16, 0, 0), 0, 0);
    pControls.add(bPreviewPlay, c);
    c = ViewerUtil.setGBC(c, 12, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.VERTICAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pControls.add(bPreviewStop, c);

    JLabel lFps = new JLabel("Frames/second:");
    SpinnerNumberModel spinnerModel = new SpinnerNumberModel(15.0, 1.0, 30.0, 1.0);
    sPreviewFps = new JSpinner(spinnerModel);
    sPreviewFps.addChangeListener(this);
    currentFps = (Double) spinnerModel.getValue();
    JLabel lPreviewMode = new JLabel("Playback mode:");
    cbPreviewMode = new JComboBox<>(PLAYBACK_MODE_ITEMS);
    cbPreviewMode.setSelectedIndex(MODE_CURRENT_CYCLE_LOOPED);
    cbPreviewMode.addActionListener(this);
    cbPreviewShowMarker = new JCheckBox("Show markers");
    cbPreviewShowMarker.addActionListener(this);
    cbPreviewZoom = new JCheckBox("Zoom");
    cbPreviewZoom.addActionListener(this);
    JPanel pOptions = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pOptions.add(lPreviewMode, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pOptions.add(cbPreviewMode, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 16, 0, 0), 0, 0);
    pOptions.add(lFps, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pOptions.add(sPreviewFps, c);
    c = ViewerUtil.setGBC(c, 4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 16, 0, 0), 0, 0);
    pOptions.add(cbPreviewZoom, c);
    c = ViewerUtil.setGBC(c, 5, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 16, 0, 0), 0, 0);
    pOptions.add(cbPreviewShowMarker, c);

    JPanel pCanvas = new JPanel(new GridBagLayout());
    rcPreview = new RenderCanvas();
    rcPreview.setInterpolationType(RenderCanvas.TYPE_NEAREST_NEIGHBOR);
    rcPreview.setScalingEnabled(true);
    scrollPreview = new JScrollPane(rcPreview);
    scrollPreview.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPreview.getHorizontalScrollBar().setUnitIncrement(32);
    scrollPreview.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPreview.getVerticalScrollBar().setUnitIncrement(32);
    scrollPreview.setBorder(BorderFactory.createEmptyBorder());
    previewCanvas = null;

    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pCanvas.add(scrollPreview, c);

    // putting all together
    JPanel pPreview = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 8, 0, 8), 0, 0);
    pPreview.add(pOptions, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 4, 4), 0, 0);
    pPreview.add(pCanvas, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 8, 8, 8), 0, 0);
    pPreview.add(pControls, c);

    timer = new Timer(0, this);
    isPreviewModified = true;
    isPreviewPlaying = false;
    previewSetFrameRate(currentFps);

    return pPreview;
  }

  private JComponent createFiltersTab() {
    GridBagConstraints c = new GridBagConstraints();

    // creating "Filters" section
    Vector<BamFilterFactory.FilterInfo> filters = new Vector<>();
    for (int i = 0; i < BamFilterFactory.getFilterInfoSize(); i++) {
      filters.add(BamFilterFactory.getFilterInfo(i));
    }
    Collections.sort(filters);
    cbFiltersAdd = new JComboBox<>(filters);
    bFiltersAdd = new JButton("Add");
    bFiltersAdd.addActionListener(this);
    JPanel pFiltersAdd = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pFiltersAdd.add(cbFiltersAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pFiltersAdd.add(bFiltersAdd, c);

    bFiltersActivate = new JButton(FILTER_ACTIVATE_LABELS.get(false));
    bFiltersActivate.addActionListener(this);
    bFiltersActivate.setEnabled(false);
    bFiltersRemove = new JButton("Remove");
    bFiltersRemove.addActionListener(this);
    bFiltersRemoveAll = new JButton("Remove all");
    bFiltersRemoveAll.addActionListener(this);
    JPanel pFiltersRemove = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pFiltersRemove.add(bFiltersActivate, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pFiltersRemove.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pFiltersRemove.add(bFiltersRemove, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pFiltersRemove.add(bFiltersRemoveAll, c);

    bFiltersUp = new JButton(Icons.ICON_UP_16.getIcon());
    bFiltersUp.setMargin(new Insets(16, 2, 16, 2));
    bFiltersUp.addActionListener(this);
    bFiltersDown = new JButton(Icons.ICON_DOWN_16.getIcon());
    bFiltersDown.setMargin(new Insets(16, 2, 16, 2));
    bFiltersDown.addActionListener(this);
    JPanel pFiltersMove = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pFiltersMove.add(bFiltersUp, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(8, 0, 0, 0), 0, 0);
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
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    pFiltersDesc.add(taFiltersDesc, c);

    modelFilters = new SimpleListModel<>();
    listFilters = new JList<>(modelFilters);
    listFilters.setCellRenderer(new IndexedCellRenderer());
    listFilters.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    listFilters.addListSelectionListener(this);
    listFilters.addKeyListener(this);
    JScrollPane scroll = new JScrollPane(listFilters);

    JPanel pFiltersList = new JPanel(new GridBagLayout());
    pFiltersList.setBorder(BorderFactory.createTitledBorder("Filters "));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 4, 0, 0), 0, 0);
    pFiltersList.add(pFiltersAdd, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 4), 0, 0);
    pFiltersList.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 4, 0, 0), 0, 0);
    pFiltersList.add(scroll, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.VERTICAL,
        new Insets(4, 4, 0, 4), 0, 0);
    pFiltersList.add(pFiltersMove, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 0, 0), 0, 0);
    pFiltersList.add(pFiltersRemove, c);
    c = ViewerUtil.setGBC(c, 1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 4), 0, 0);
    pFiltersList.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 0, 3, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 4, 4, 0), 0, 0);
    pFiltersList.add(pFiltersDesc, c);

    // creating "Filter settings" section
    JPanel pFiltersSettingsMain = new JPanel(new GridBagLayout());
    pFiltersSettingsMain.setBorder(BorderFactory.createTitledBorder("Filter settings "));
    pFiltersSettings = new JPanel(new BorderLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 4, 4, 4), 0, 0);
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
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 0, 4, 8), 0, 0);
    pFiltersPreviewControls.add(cbFiltersShowMarker, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 0, 4, 0), 0, 0);
    pFiltersPreviewControls.add(l, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 4, 4, 0), 0, 0);
    pFiltersPreviewControls.add(sFiltersPreviewFrame, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(4, 0, 4, 4), 0, 0);
    pFiltersPreviewControls.add(pDummy, c);

    JPanel pFiltersPreview = new JPanel(new GridBagLayout());
    pFiltersPreview.setBorder(BorderFactory.createTitledBorder("Quick Preview "));
    rcFiltersPreview = new RenderCanvas(new BufferedImage(320, 320, BufferedImage.TYPE_INT_ARGB));
    rcFiltersPreview.setMinimumSize(rcFiltersPreview.getPreferredSize());
    rcFiltersPreview.setScalingEnabled(false);
    rcFiltersPreview.setHorizontalAlignment(SwingConstants.CENTER);
    rcFiltersPreview.setVerticalAlignment(SwingConstants.CENTER);
    scrollFiltersPreview = new JScrollPane(rcFiltersPreview);
    scrollFiltersPreview.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollFiltersPreview.getHorizontalScrollBar().setUnitIncrement(32);
    scrollFiltersPreview.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollFiltersPreview.getVerticalScrollBar().setUnitIncrement(32);
    scrollFiltersPreview.setBorder(BorderFactory.createEmptyBorder());

    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 4, 0, 4), 0, 0);
    pFiltersPreview.add(scrollFiltersPreview, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 4, 4), 0, 0);
    pFiltersPreview.add(pFiltersPreviewControls, c);

    // putting all together
    JPanel pFilters = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 2, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(8, 8, 8, 0), 0, 0);
    pFilters.add(pFiltersList, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(8, 8, 0, 8), 0, 0);
    pFilters.add(pFiltersSettingsMain, c);
    c = ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(8, 8, 8, 8), 0, 0);
    pFilters.add(pFiltersPreview, c);

    updateFilterList();

    return pFilters;
  }

  /** Close the main dialog manually. 'force' overrides the confirmation dialog. */
  private void hideWindow(boolean force) {
    if (force || confirmCloseDialog()) {
      clear();
      setVisible(false);
    }
  }

  /** Presents a confirmation dialog if any frames or cycles are present. */
  private boolean confirmCloseDialog() {
    boolean isEmpty = (modelFrames.isEmpty() && modelCycles.isEmpty());
    if (!isEmpty) {
      String msg = String.format(
          "%d frame(s) and %d cycle(s) will be discarded.\n" + "Do you really want to close the dialog?",
          modelFrames.getSize(), modelCycles.getSize());
      if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, msg, "Close dialog",
          JOptionPane.YES_NO_OPTION)) {
        isEmpty = true;
      }
    }
    return isEmpty;
  }

  /** Resets the dialog state. */
  private void clear() {
    previewStop();
    outputSetModified(true);
    filterRemoveAll();
    cyclesRemoveAll();
    framesRemoveAll();
    paletteDialog.clear();
  }

  /** Updates the tab component state. */
  private void updateStatus() {
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
  protected void updateFramesList() {
    // updating button states
    Couple<Integer, Integer> bounds = getIndexBounds(listFrames.getSelectedIndices());
    bFramesUp.setEnabled(!modelFrames.isEmpty() && bounds.getValue0() > 0);
    bFramesDown.setEnabled(
        !modelFrames.isEmpty() && bounds.getValue0() >= 0 && bounds.getValue1() < modelFrames.getSize() - 1);
    miFramesRemove.setEnabled(!modelFrames.isEmpty() && !listFrames.isSelectionEmpty());
    miFramesRemoveAll.setEnabled(!modelFrames.isEmpty());
    miFramesDropUnused.setEnabled(!modelFrames.isEmpty());
    bpmFramesRemove
        .setEnabled(miFramesRemove.isEnabled() || miFramesRemoveAll.isEnabled() || miFramesDropUnused.isEnabled());

    // updating frame info box
    updateFrameInfo(listFrames.getSelectedIndices());

    // updating palette
    paletteDialog.setPaletteModified();

    updateCyclesList();
    updateStatus();
  }

  /** Updates relevant components to reflect the current state of the cycles list. */
  protected void updateCyclesList() {
    listCycles.repaint();
    Couple<Integer, Integer> bounds = getIndexBounds(listCycles.getSelectedIndices());
    int idx = (bounds.getValue0().compareTo(bounds.getValue1()) == 0) ? bounds.getValue0() : -1;
    if (idx >= 0) {
      listCycles.ensureIndexIsVisible(idx);
    }

    // updating button states
    bCyclesUp.setEnabled(!modelCycles.isEmpty() && bounds.getValue0() > 0);
    bCyclesDown.setEnabled(
        !modelCycles.isEmpty() && bounds.getValue0() >= 0 && bounds.getValue1() < modelCycles.getSize() - 1);
    bCyclesRemove.setEnabled(!listCycles.isSelectionEmpty());
    bCyclesRemoveAll.setEnabled(!modelCycles.isEmpty());

    // updating macro buttons
    bMacroAssignFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroRemoveFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroDuplicateCycle.setEnabled(!listCycles.isSelectionEmpty());
    bMacroDuplicateFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroSortFramesAsc.setEnabled(!listCycles.isSelectionEmpty());
    bMacroReverseFrames.setEnabled(!listCycles.isSelectionEmpty());
    bMacroCompactSelection.setEnabled(listCycles.getSelectedIndices().length > 1);
    bMacroInterleaveSelection.setEnabled(listCycles.getSelectedIndices().length > 1);
    bMacroRemoveAll.setEnabled(!modelCycles.isEmpty());
    bMacroReverseCycles.setEnabled(!modelCycles.isEmpty());

    initCurrentCycle(bounds, true);
    updateStatus();
  }

  /** Updates relevant components to reflect the current state of the selected cycle. */
  private void updateCurrentCycle() {
    // updating button states
    Couple<Integer, Integer> bounds = getIndexBounds(listCurCycle.getSelectedIndices());
    bCurCycleUp.setEnabled(!modelCurCycle.isEmpty() && bounds.getValue0() > 0);
    bCurCycleDown.setEnabled(
        !modelCurCycle.isEmpty() && bounds.getValue0() >= 0 && bounds.getValue1() < modelCurCycle.getSize() - 1);
    bCurCycleAdd.setEnabled(!listFramesAvail.isSelectionEmpty());
    bCurCycleRemove.setEnabled(!listCurCycle.isSelectionEmpty());
    listFramesAvail.invalidate();
    listCurCycle.invalidate();
    pCurrentCycle.validate();
  }

  private void initCurrentCycle(int cycleIdx, boolean forced) {
    initCurrentCycle(Couple.with(cycleIdx, cycleIdx), forced);
  }

  /** Initializes the "Current cycle" section of the Cycles tab. */
  private void initCurrentCycle(Couple<Integer, Integer> cycleIndices, boolean forced) {
    if (cycleIndices != null) {
      if (cycleIndices.getValue0().compareTo(cycleIndices.getValue1()) == 0 && cycleIndices.getValue0() >= 0
          && cycleIndices.getValue0() < modelCycles.getSize()) {
        int cycleIdx = cycleIndices.getValue0();

        // enabling components
        listFramesAvail.setEnabled(true);
        bCurCycleAdd.setEnabled(true);
        bCurCycleRemove.setEnabled(true);
        listCurCycle.setEnabled(true);

        // updating group box title
        pCurrentCycle.setBorder(BorderFactory.createTitledBorder(String.format("Cycle %d ", cycleIdx)));

        listFramesAvail.setSelectedIndices(new int[] {});

        // updating current cycle list view
        modelCurCycle.setCycle(cycleIdx, forced);

        listCurCycle.setSelectedIndices(new int[] {});

        updateCurrentCycle();
      } else {
        // no cycle selected
        listFramesAvail.setSelectedIndices(new int[] {});
        listFramesAvail.setEnabled(false);
        bCurCycleAdd.setEnabled(false);
        bCurCycleRemove.setEnabled(false);
        listCurCycle.setSelectedIndices(new int[] {});
        listCurCycle.setEnabled(false);

        if (cycleIndices.getValue0() < 0 || cycleIndices.getValue1() < 0) {
          pCurrentCycle.setBorder(BorderFactory.createTitledBorder("No cycle selected "));
        } else {
          pCurrentCycle.setBorder(BorderFactory.createTitledBorder("Too many cycles selected "));
        }
        modelCurCycle.setCycle(-1);
      }
    }
  }

  /** Updates relevant components to reflect the current frames list selection in the Frames tab. */
  private void updateFrameInfo(int[] indices) {
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
      boolean initialState = (fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED) != null)
          ? (Boolean) fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED)
          : false;
      boolean changedWidth = false, changedHeight = false;
      boolean changedCenterX = false, changedCenterY = false;
      boolean changedCompression = false;

      for (int i = 1; i < indices.length; i++) {
        fe = getBamDecoder(BAM_ORIGINAL).getFrameInfo(indices[i]);
        changedWidth |= (!changedWidth && initialWidth != fe.getWidth());
        changedHeight |= (!changedHeight && initialHeight != fe.getHeight());
        changedCenterX |= (!changedCenterX && initialX != fe.getCenterX());
        changedCenterY |= (!changedCenterY && initialY != fe.getCenterY());
        boolean b = (fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED) != null)
            ? (Boolean) fe.getOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED)
            : false;
        changedCompression |= (!changedCompression && initialState != b);
        if (changedWidth && changedHeight && changedCenterX && changedCenterY && changedCompression) {
          break;
        }
      }

      // setting frame info
      String title;
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
      cbCompressFrame.setSelected(!changedCompression && initialState);
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
  private void updateQuickPreview(RenderCanvas target, int[] indices, boolean showCenter) {
    if (target != null) {
      int imgWidth = target.getImage().getWidth(null);
      int imgHeight = target.getImage().getHeight(null);
      Graphics2D g = (Graphics2D) target.getImage().getGraphics();
      try {
        g.setComposite(AlphaComposite.Src);
        g.setColor(ColorConvert.TRANSPARENT_COLOR);
        g.fillRect(0, 0, imgWidth, imgHeight);

        if (indices != null && indices.length == 1 && indices[0] >= 0 && indices[0] < modelFrames.getSize()) {

          // drawing frame
          int left, top;
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
            float ratioX = (float) imgWidth / (float) image.getWidth(null);
            float ratioY = (float) imgHeight / (float) image.getHeight(null);
            Couple<Float, Float> minMaxRatio = Couple.with(Math.min(ratioX, ratioY), Math.max(ratioX, ratioY));
            if (image.getWidth(null) * minMaxRatio.getValue1() < imgWidth
                && image.getHeight(null) * minMaxRatio.getValue1() < imgHeight) {
              ratio = minMaxRatio.getValue1();
            } else {
              ratio = minMaxRatio.getValue0();
            }
            int newWidth = (int) (image.getWidth(null) * ratio);
            int newHeight = (int) (image.getHeight(null) * ratio);
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
            int cx = left + (int) (fe.getCenterX() * ratio);
            int cy = top + (int) (fe.getCenterY() * ratio);
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
  private void updatePreview() {
    previewPrepare(false);
    previewDisplay();

    // updating buttons
    bPreviewCycleFirst.setEnabled(bamControlPreview.cycleGet() != 0);
    bPreviewCyclePrev.setEnabled(bamControlPreview.cycleGet() > 0);
    bPreviewCycleNext.setEnabled(bamControlPreview.cycleGet() < bamControlPreview.cycleCount() - 1);
    bPreviewCycleLast.setEnabled(bamControlPreview.cycleGet() != bamControlPreview.cycleCount() - 1);
    bPreviewFrameFirst.setEnabled(bamControlPreview.cycleGetFrameIndex() != 0);
    bPreviewFramePrev.setEnabled(bamControlPreview.cycleGetFrameIndex() > 0);
    bPreviewFrameNext.setEnabled(bamControlPreview.cycleGetFrameIndex() < bamControlPreview.cycleFrameCount() - 1);
    bPreviewFrameLast.setEnabled(bamControlPreview.cycleGetFrameIndex() != bamControlPreview.cycleFrameCount() - 1);
    lPreviewCycle
        .setText(String.format("Cycle: %d/%d", bamControlPreview.cycleGet(), bamControlPreview.cycleCount() - 1));
    lPreviewFrame.setText(
        String.format("Frame: %d/%d", bamControlPreview.cycleGetFrameIndex(), bamControlPreview.cycleFrameCount() - 1));
  }

  /** Updates relevant components to reflect the current state of the filters list. */
  protected void updateFilterList() {
    // updating button states
    int idx = listFilters.getSelectedIndex();
    bFiltersUp.setEnabled(!modelFilters.isEmpty() && idx > 0);
    bFiltersDown.setEnabled(!modelFilters.isEmpty() && idx >= 0 && idx < modelFilters.size() - 1);
    bFiltersRemove.setEnabled(!modelFilters.isEmpty() && !listFilters.isSelectionEmpty());
    bFiltersRemoveAll.setEnabled(!modelFilters.isEmpty());

    final boolean isEnabled = !modelFilters.isEmpty() && !listFilters.isSelectionEmpty();
    final boolean activate = (idx < 0 || listFilters.getModel().getElementAt(idx).isEnabled());
    bFiltersActivate.setText(FILTER_ACTIVATE_LABELS.get(!activate));
    bFiltersActivate.setEnabled(isEnabled);

    updateFilterInfo();
    updateFilterControls();
    filterSetPreviewFrame(filterGetPreviewFrameIndex(), true);
  }

  /** Updates the filter info box. */
  private void updateFilterInfo() {
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
  private void updateFilterControls() {
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
  private void updateCyclesAddedFrames(int[] indices) {
    if (indices != null && indices.length > 0) {
      Arrays.sort(indices);
      for (final int index : indices) {
        for (int i = 0; i < modelCycles.getSize(); i++) {
          PseudoBamCycleEntry cycle = Objects.requireNonNull(modelCycles.getElementAt(i));
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
  private void updateCyclesRemovedFrames(int[] indices) {
    if (indices != null && indices.length > 0) {
      Arrays.sort(indices);
      for (int x = indices.length - 1; x >= 0; x--) {
        int index = indices[x];
        for (int i = 0; i < modelCycles.getSize(); i++) {
          PseudoBamCycleEntry cycle = Objects.requireNonNull(modelCycles.getElementAt(i));
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
  private void updateCyclesMovedFrames(int index, int shift) {
    if (index >= 0 && shift != 0) {
      for (int i = 0; i < modelCycles.getSize(); i++) {
        PseudoBamCycleEntry cycle = Objects.requireNonNull(modelCycles.getElementAt(i));
        for (int j = 0; j < cycle.size(); j++) {
          if (cycle.get(j) == index) {
            cycle.set(j, cycle.get(j) + shift);
          } else if (shift > 0 && cycle.get(j) > index && cycle.get(j) <= index + shift) {
            cycle.set(j, cycle.get(j) - 1);
          } else if (shift < 0 && cycle.get(j) < index && cycle.get(j) >= index + shift) {
            cycle.set(j, cycle.get(j) + 1);
          }
        }
      }
      updateCurrentCycle();
    }
  }

  /** Action for "Compress frame". */
  private void framesUpdateCompressFrame() {
    int[] indices = listFrames.getSelectedIndices();
    if (indices != null) {
      Boolean b = cbCompressFrame.isSelected();
      for (int index : indices) {
        getBamDecoder(BAM_ORIGINAL).getFrameInfo(index).setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, b);
      }
      outputSetModified(true);
    }
  }

  /** Evaluates the value in the specified "Center X/Y" text field. */
  private void framesValidateCenterValue(JTextField tf) {
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
          for (int index : indices) {
            if (isCenterX) {
              if (isRelative) {
                result = getBamDecoder(BAM_ORIGINAL).getFrameInfo(index).getCenterX() + value;
                result = Math.min(Math.max(result, Short.MIN_VALUE), Short.MAX_VALUE);
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(index).setCenterX(result);
              } else {
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(index).setCenterX(value);
              }
              outputSetModified(true);
            } else {
              if (isRelative) {
                result = getBamDecoder(BAM_ORIGINAL).getFrameInfo(index).getCenterY() + value;
                result = Math.min(Math.max(result, Short.MIN_VALUE), Short.MAX_VALUE);
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(index).setCenterY(result);
              } else {
                getBamDecoder(BAM_ORIGINAL).getFrameInfo(index).setCenterY(value);
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

  /** Returns the {@link BamFramesListModel} instance. */
  protected BamFramesListModel getFramesModel() {
    return modelFrames;
  }

  /** Action for "Add..."->"Add file(s)...": adds image files to the frames list. */
  public void framesAddFiles() {
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
  public void framesAddResources() {
    ResourceEntry[] entries = OpenResourceDialog.showOpenDialog(this, "Choose resource(s) to add",
        new String[] { "BAM", "BMP" }, true);
    if (entries != null) {
      try {
        WindowBlocker.blockWindow(this, true);
        framesAdd(entries, listFrames.getSelectedIndex());
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

  public void framesAdd(Path[] files) {
    framesAdd(files, listFrames.getSelectedIndex());
  }

  /** Called by framesAddLauncher. Can also be called directly. Makes use of a progress monitor if available. */
  public void framesAdd(Path[] files, int insertIndex) {
    if (files != null) {
      ResourceEntry[] entries = new ResourceEntry[files.length];
      for (int i = 0; i < files.length; i++) {
        entries[i] = new FileResourceEntry(files[i]);
      }
      framesAdd(entries, insertIndex);
    }
  }

  public void framesAdd(ResourceEntry[] entries, int insertIndex) {
    if (entries != null) {
      outputSetModified(true);
      final List<ResourceEntry> skippedFiles = new ArrayList<>();
      int idx = insertIndex;
      if (idx < -1) {
        idx = modelFrames.getSize() - 1; // includes adding frames before first list entry
      }
      try {
        for (int i = 0; i < entries.length; i++) {
          if (isProgressMonitorActive()) {
            // updating progress monitor
            if (isProgressMonitorCancelled()) {
              // adding remaining files to skip list
              for (int j = i; j < entries.length; j++) {
                if (entries[j] != null) {
                  skippedFiles.add(entries[j]);
                }
              }
              break;
            }
            advanceProgressMonitor(String.format("Adding file %d/%d", i + 1, entries.length));
          }
          // adding files to global frames list
          if (entries[i] != null) {
            if (BamDecoder.isValid(entries[i])) {
              BamDecoder decoder = framesAddBam(idx + 1, entries[i]);
              if (decoder != null) {
                idx += decoder.frameCount();
              } else {
                skippedFiles.add(entries[i]);
              }
            } else {
              if (framesAddImage(idx + 1, entries[i], -1)) {
                idx++;
              } else {
                skippedFiles.add(entries[i]);
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
      // int[] indices = new int[entries.length - skippedFiles.size()];
      int[] indices = new int[idx - insertIndex];
      // for (int i = 0; i < entries.length - skippedFiles.size(); i++) {
      for (int i = 0; i < indices.length; i++) {
        indices[i] = insertIndex + 1 + i;
      }
      updateCyclesAddedFrames(indices);

      updateFramesList();
      listFrames.requestFocusInWindow();

      // error handling
      final int size = skippedFiles.size();
      if (size != 0) {
        final StringBuilder sb = new StringBuilder();
        if (size == 1) {
          sb.append("1 file has been skipped:\n");
        } else {
          sb.append(String.format("%d files have been skipped:\n", size));
        }
        final int count = Math.min(5, size);
        for (int i = 0; i < count; i++) {
          sb.append(String.format("  - %s\n", skippedFiles.get(i)));
        }
        if (size > 5) {
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
  public BamDecoder framesAddBam(int listIndex, Path file) {
    return framesAddBam(listIndex, new FileResourceEntry(file));
  }

  /**
   * Specific: Adds the specified source BAM to the frames list.
   *
   * @return the BamDecoder object of the source BAM, or {@code null on error
   */
  public BamDecoder framesAddBam(int listIndex, ResourceEntry entry) {
    if (listIndex >= 0 && entry != null && BamDecoder.isValid(entry)) {
      BamDecoder decoder = BamDecoder.loadBam(entry);
      BamDecoder.BamControl control = Objects.requireNonNull(decoder).createControl();
      control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);

      // preparing palette-specific properties
      IndexColorModel cm = null;
      if (decoder instanceof BamV1Decoder) {
        int[] palette = ((BamV1Decoder.BamV1Control) control).getPalette();
        int transColor = ((BamV1Decoder.BamV1Control) control).getTransparencyIndex();
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
  public int framesAddBamFrame(int listIndex, BamDecoder decoder, BamDecoder.BamControl control, int frameIndex,
      IndexColorModel cm) {
    if (decoder != null && frameIndex >= 0 && frameIndex < decoder.frameCount()) {
      listIndex = Math.max(0, Math.min(modelFrames.getSize(), listIndex));

      if (control == null) {
        control = decoder.createControl();
      }

      // preparing palette-specific properties
      if (cm == null && decoder instanceof BamV1Decoder) {
        int[] palette = ((BamV1Decoder.BamV1Control) control).getPalette();
        int transColor = ((BamV1Decoder.BamV1Control) control).getTransparencyIndex();
        cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transColor, DataBuffer.TYPE_BYTE);
      }

      // adding frame
      boolean isCompressed;
      int rleIndex;
      BamDecoder.FrameEntry fe = decoder.getFrameInfo(frameIndex);
      BufferedImage image;
      if (cm != null) {
        if (fe.getWidth() > 0 && fe.getHeight() > 0) {
          image = new BufferedImage(fe.getWidth(), fe.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, cm);
        } else {
          image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED, cm);
        }
        isCompressed = ((BamV1Decoder.BamV1FrameEntry) fe).isCompressed();
        rleIndex = ((BamV1Decoder) decoder).getRleIndex();
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
      fe2.setOption(BAM_FRAME_OPTION_SOURCE_INDEX, frameIndex);
      fe2.setOption(PseudoBamDecoder.OPTION_STRING_LABEL, entry.getResourceName() + ":" + frameIndex);
      fe2.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, isCompressed);
      fe2.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX, rleIndex);
      return listIndex;
    }
    return -1;
  }

  public boolean framesAddImage(int listIndex, Path file, int frameIndex) {
    if (file != null) {
      return framesAddImage(listIndex, new FileResourceEntry(file), frameIndex);
    } else {
      return false;
    }
  }

  /**
   * Adds the specified source graphics file to the frames list.
   *
   * @param listIndex  The start position of the image or images in the frames list.
   * @param entry      The resource entry pointing to the graphics file.
   * @param frameIndex An optional index for graphics files with multiple frames. This is relevant for loading specific
   *                   frames from animated GIFs. Specify {@code -1} to load all available frames.
   * @return {@code true} if image or images have been successfully added to the frames list.
   */
  private boolean framesAddImage(int listIndex, ResourceEntry entry, int frameIndex) {
    boolean retVal = false;
    if (listIndex >= 0 && entry != null) {
      try (InputStream is = entry.getResourceDataAsStream()) {
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
          ImageReader reader = ImageIO.getImageReadersBySuffix(entry.getExtension()).next();
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
            boolean hasAlpha = image.getColorModel().hasAlpha();
            int[] cmap = new int[256];
            int transIndex = -1;
            IndexColorModel srcCm = (IndexColorModel) image.getColorModel();
            int numColors = Math.min(1 << srcCm.getPixelSize(), cmap.length);
            int i = 0;
            for (; i < numColors; i++) {
              int alpha = hasAlpha ? srcCm.getAlpha(i) : 0xff;
              cmap[i] = (alpha << 24) | (srcCm.getRed(i) << 16) | (srcCm.getGreen(i) << 8) | srcCm.getBlue(i);
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
            BufferedImage dstImage = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_BYTE_INDEXED, cm);
            byte[] srcBuffer = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            byte[] dstBuffer = ((DataBufferByte) dstImage.getRaster().getDataBuffer()).getData();
            System.arraycopy(srcBuffer, 0, dstBuffer, 0, srcBuffer.length);
            srcBuffer = null;
            dstBuffer = null;
            cmap = null;
            image = dstImage;
          }

          // Workaround for BAM V1 transparency, see PseudoBamDecoder.OPTION_BOOL_TRANSPARENTGREENFORCED
          final boolean forceTransparentGreen = image.getType() != BufferedImage.TYPE_BYTE_INDEXED;
          modelFrames.insert(listIndex + curFrameIdx, image, new Point(), forceTransparentGreen);
          // setting required extra options
          PseudoBamFrameEntry fe2 = modelFrames.getDecoder().getFrameInfo(listIndex + curFrameIdx);
          if (entry instanceof FileResourceEntry) {
            fe2.setOption(BAM_FRAME_OPTION_PATH, entry.getActualPath().toString());
          } else {
            fe2.setOption(BAM_FRAME_OPTION_PATH, BAM_FRAME_PATH_BIFF + entry.getResourceName());
          }
          fe2.setOption(BAM_FRAME_OPTION_SOURCE_INDEX, frameIdx);
          if (images.length > 1) {
            fe2.setOption(PseudoBamDecoder.OPTION_STRING_LABEL, entry.getResourceName() + ":" + frameIdx);
          } else {
            fe2.setOption(PseudoBamDecoder.OPTION_STRING_LABEL, entry.getResourceName());
          }
          curFrameIdx++;
        }
      } catch (Exception e) {
        retVal = false;
        Logger.error(e);
      }
    }
    return retVal;
  }

  /** Action for "Import BAM file...": loads a complete frames/cycles structure. */
  public void framesImportBamFile() {
    Path[] files = getOpenFileName(this, "Import BAM file", null, false,
        new FileNameExtensionFilter[] { getBamFilter() }, 0);
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
  public void framesImportBamResource() {
    ResourceEntry[] entries = OpenResourceDialog.showOpenDialog(this, "Import BAM resource", new String[] { "BAM" },
        false);
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
  public void framesImportBam(Path file) {
    framesImportBam(new FileResourceEntry(file));
  }

  /** Specific: Loads the whole frames/cycles structure from the specified BAM ResourceEntry object. */
  public void framesImportBam(ResourceEntry entry) {
    if (entry != null) {
      outputSetModified(true);
      boolean cancelled = false;
      boolean replace = true;
      if (!modelFrames.isEmpty()) {
        String[] options = { "Append", "Replace", "Cancel" };
        String msg = "What do you want to do with the selected BAM file?";
        int ret = JOptionPane.showOptionDialog(this, msg, "Question", JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        cancelled = (ret == 2 || ret == JOptionPane.CLOSED_OPTION);
        replace = (ret == 1);
      }
      if (!cancelled) {
        if (replace) {
          clear();
          setBamOutput(FileManager.query(Profile.getGameRoot(), ResourceFactory.getResourceName(entry)));
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
              indices[j] = control.cycleGetFrameIndexAbsolute(i, j) + frameBase;
            }
            cyclesAdd(i + cycleBase, indices);
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
          JOptionPane.showMessageDialog(this, "Error while importing BAM file " + entry.getResourceName(), "Error",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  /** Action for "Add..."->"Add folder...": adds all supported files from the specified folder. */
  public void framesAddFolder() {
    framesAddFolder(getOpenPathName(this, "Select folder", null));
  }

  public void framesAddFolder(Path path) {
    framesAddFolder(path, listFrames.getSelectedIndex());
  }

  public void framesAddFolder(Path path, int insertIndex) {
    if (path != null && FileEx.create(path).isDirectory()) {
      // preparing list of valid files
      FileNameExtensionFilter filters = getGraphicsFilters()[0];
      List<Path> validFiles = new ArrayList<>();
      try (DirectoryStream<Path> dstream = Files.newDirectoryStream(path)) {
        for (final Path file : dstream) {
          if (FileEx.create(file).isFile() && filters.accept(file.toFile())) {
            validFiles.add(file);
          }
        }
      } catch (IOException e) {
        Logger.error(e);
        return;
      }

      // adding entries to frames list
      try {
        WindowBlocker.blockWindow(this, true);
        framesAdd(validFiles.toArray(new Path[0]), insertIndex);
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    }
  }

  /** Action for "Remove": removes the selected frame entry/entries from the frames list, updates cycle structures. */
  public void framesRemove() {
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
  public void framesRemove(int[] indices) {
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
  public void framesRemoveAll() {
    outputSetModified(true);
    modelFrames.clear();
    updateFramesList();
    listFrames.requestFocusInWindow();
  }

  /** Action for "Up" button next to frames list. */
  private void framesMoveUp() {
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
  private void framesMoveDown() {
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
  private int framesGetUnusedFramesCount() {
    return framesGetUnusedFramesCount(framesGetUnusedFrames());
  }

  /** Action for "Drop unused frames": return number of unused frames. */
  private int framesGetUnusedFramesCount(BitSet framesUsed) {
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

  /** Action for "Drop unused frames": returns a bitset that maps the used state of all frames. */
  private BitSet framesGetUnusedFrames() {
    BitSet framesUsed = new BitSet(modelFrames.getSize());
    for (int i = 0; i < modelCycles.getSize(); i++) {
      PseudoBamCycleEntry cycle = Objects.requireNonNull(modelCycles.getElementAt(i));
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
  private void framesDropUnusedFrames() {
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

  /** Returns the {@link BamCyclesListModel} instance. */
  protected BamCyclesListModel getCyclesModel() {
    return modelCycles;
  }

  /** Action for "Add cycle": adds "count" new empty cycles at the current cycle index to the cycles list. */
  private void cyclesAdd(int count) {
    int idx = listCycles.getSelectedIndex() + 1;
    if (idx < 0) {
      idx = modelCycles.getSize();
    }

    count = Math.max(0, count);
    if (isBamV1Selected()) {
      // BAM V1 supports only up to 255 cycles
      count = Math.min(256, idx + count) - idx;
    }

    for (int i = 0; i < count; ++i) {
      cyclesAdd(idx, new int[0]);
      idx++;
    }
  }

  /** Specific: adds a new cycle with the specified frame indices at the specified position. */
  private void cyclesAdd(int index, int[] indices) {
    if (index < 0) {
      index = 0;
    } else if (index > modelCycles.getSize()) {
      index = modelCycles.getSize();
    }
    if (indices == null) {
      indices = new int[0];
    }
    modelCycles.insert(index, indices);
    listCycles.setSelectedIndex(index);
    listCycles.ensureIndexIsVisible(index);
    updateCyclesList();
  }

  /** Action for "Remove cycle": removes the selected cycles from the cycles list. */
  private void cyclesRemove() {
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
  private void cyclesRemove(int index) {
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
  protected void cyclesRemoveAll() {
    modelCycles.clear();
    updateCyclesList();
  }

  /** Action for "Up" button next to cycles list. */
  private void cyclesMoveUp() {
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
  private void cyclesMoveDown() {
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

  /**
   * Action for macro "Selected cycle"->"Assign all frames": puts all available frames into the selected cycle (sorted).
   */
  private void macroAssignFrames() {
    int[] indices = listCycles.getSelectedIndices();
    for (int index : indices) {
      if (index >= 0 && index < modelCycles.getSize()) {
        modelCurCycle.setCycle(index);
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
  private void macroRemoveAllFrames() {
    int[] indices = listCycles.getSelectedIndices();
    for (int index : indices) {
      if (index >= 0 && index < modelCycles.getSize()) {
        modelCurCycle.setCycle(index);
        modelCurCycle.clear();
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Duplicate cycle": Adds a duplicate below the selected cycle. */
  private void macroDuplicateCycle() {
    int[] indices = listCycles.getSelectedIndices();
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] >= 0 && indices[i] < modelCycles.getSize()) {
        int[] frames = new int[modelCycles.getElementAt(indices[i]).size()];
        for (int j = 0; j < frames.length; j++) {
          frames[j] = modelCycles.getElementAt(indices[i]).get(j);
        }
        modelCycles.insert(indices[i] + 1, frames);
        for (int j = i; j < indices.length; j++) {
          indices[j]++;
        }
      }
    }
    listCycles.setSelectedIndices(indices);
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Duplicate each frame": Duplicates each frame in the selected cycle. */
  private void macroDuplicateFrames() {
    int[] indices = listCycles.getSelectedIndices();
    for (int index : indices) {
      if (index >= 0 && index < modelCycles.getSize()) {
        modelCurCycle.setCycle(index);
        int frameIdx = modelCurCycle.getSize() - 1;
        while (frameIdx >= 0) {
          modelCurCycle.insert(frameIdx + 1, modelCurCycle.getControl().cycleGetFrameIndexAbsolute(frameIdx));
          frameIdx--;
        }
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Sort frames": Sorts frame indices by value. */
  private void macroSortFrames() {
    int[] indices = listCycles.getSelectedIndices();
    for (int index : indices) {
      if (index >= 0 && index < modelCycles.getSize()) {
        int[] frames = new int[modelCycles.getElementAt(index).size()];
        for (int j = 0; j < frames.length; j++) {
          frames[j] = modelCycles.getElementAt(index).get(j);
        }
        Arrays.sort(frames);
        for (int j = 0; j < frames.length; j++) {
          modelCycles.getElementAt(index).set(j, frames[j]);
        }
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycle"->"Reverse frames order": Reverses the current order of the frame indices. */
  private void macroReverseFramesOrder() {
    int[] indices = listCycles.getSelectedIndices();
    for (int index : indices) {
      if (index >= 0 && index < modelCycles.getSize()) {
        int[] frames = new int[modelCycles.getElementAt(index).size()];
        int len = modelCycles.getElementAt(index).size();
        for (int j = 0; j < len; j++) {
          frames[j] = modelCycles.getElementAt(index).get(len - j - 1);
        }
        for (int j = 0; j < len; j++) {
          modelCycles.getElementAt(index).set(j, frames[j]);
        }
      }
    }
    updateCyclesList();
  }

  /** Action for macro "Selected cycles" -> "Compact cycles": Removes gaps between selected cycle entries. */
  private void macroCompactCycles() {
    final int[] indices = listCycles.getSelectedIndices();
    if (indices.length <= 1) {
      return;
    }

    for (int i = 1; i < indices.length; i++) {
      int prevIndex = indices[i - 1];
      int index = indices[i];
      if (index > prevIndex + 1) {
        final int offset = prevIndex - index + 1;
        modelCycles.move(index, offset);
        indices[i] += offset;
      }
    }
    listCycles.setSelectedIndices(indices);
    updateCyclesList();
    listCycles.requestFocusInWindow();
  }

  /** Action for macro "Selected cycles" -> "Interleave cycles": Inserts gaps between selected cycle entries. */
  private void macroInterleaveCycles() {
    final int[] indices = listCycles.getSelectedIndices();
    if (indices.length <= 1) {
      return;
    }

    for (int i = 1; i < indices.length; i++) {
      // depending on available space, gaps are either inserted into trailing or leading selections
      if (indices[indices.length - 1] < modelCycles.getSize() - 1) {
        // expanding trailing selections
        for (int j = indices.length - 1; j >= i; j--) {
          modelCycles.move(indices[j], 1);
          indices[j]++;
        }
      } else if (indices[0] > 0) {
        // expanding leading selections
        for (int j = 0; j < i; j++) {
          modelCycles.move(indices[j], -1);
          indices[j]--;
        }
      }
    }
    listCycles.setSelectedIndices(indices);
    updateCyclesList();
    listCycles.requestFocusInWindow();
  }

  /** Action for macro "All cycles"->"Remove all frames": Removes all frame indices. */
  private void macroRemoveAllFramesGlobal() {
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
  private void macroReverseCyclesOrder() {
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

  /**
   * Action for "Right" button between available frames/current cycle: adds selected frames to the current cycle list.
   */
  private void currentCycleAdd() {
    int[] indices = listFramesAvail.getSelectedIndices();
    if (indices != null && indices.length > 0) {
      Couple<Integer, Integer> dstBounds = getIndexBounds(listCurCycle.getSelectedIndices());
      int dstIdx = dstBounds.getValue1() + 1;
      modelCurCycle.insert(dstIdx, indices);
      modelCycles.contentChanged(modelCurCycle.getCycle());
      listFramesAvail.setSelectedIndices(new int[] { getIndexBounds(indices).getValue1() });
      listCurCycle.setSelectedIndex(dstIdx + indices.length - 1);
      updateCurrentCycle();
    }
  }

  /**
   * Action for "Left" button between available frames/current cycle: removes selected frames from the current cycle
   * list.
   */
  private void currentCycleRemove() {
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
  private void currentCycleMoveUp() {
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
  private void currentCycleMoveDown() {
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
  private void previewPlay() {
    if (previewIsPlaying()) {
      timer.stop();
      isPreviewPlaying = false;
      bPreviewPlay.setText("Play");
      bPreviewPlay.setIcon(Icons.ICON_PLAY_16.getIcon());
      bPreviewStop.setEnabled(true);
      updatePreview();
    } else {
      isPreviewPlaying = true;
      bPreviewPlay.setText("Pause");
      bPreviewPlay.setIcon(Icons.ICON_PAUSE_16.getIcon());
      bPreviewStop.setEnabled(true);
      timer.start();
    }
  }

  /** Action for "Stop": stops playback and resets current frame to 1. */
  protected void previewStop() {
    timer.stop();
    isPreviewPlaying = false;
    bPreviewPlay.setText("Play");
    bPreviewPlay.setIcon(Icons.ICON_PLAY_16.getIcon());
    bPreviewStop.setEnabled(false);
    if (bamControlPreview != null) {
      bamControlPreview.cycleSetFrameIndex(0);
    }
    updatePreview();
  }

  /** Returns whether playback is active. */
  private boolean previewIsPlaying() {
    return isPreviewPlaying;
  }

  /** Action for "Next cycle" button: selects next cycle index if available. */
  private void previewCycleUp() {
    if (bamControlPreview.cycleGet() < bamControlPreview.cycleCount() - 1) {
      bamControlPreview.cycleSet(bamControlPreview.cycleGet() + 1);
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Action for "Previous cycle" button: selects previous cycle index if available. */
  private void previewCycleDown() {
    if (bamControlPreview.cycleGet() > 0) {
      bamControlPreview.cycleSet(bamControlPreview.cycleGet() - 1);
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Action for "First cycle" button: selects first cycle index if available. */
  private void previewCycleStart() {
    if (bamControlPreview.cycleGet() != 0) {
      bamControlPreview.cycleSet(0);
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Action for "Last cycle" button: selects last cycle index if available. */
  private void previewCycleEnd() {
    if (bamControlPreview.cycleGet() != bamControlPreview.cycleCount() - 1) {
      bamControlPreview.cycleSet(bamControlPreview.cycleCount() - 1);
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Initializes the "Goto cycle" panel with the current cycle index. */
  private void previewCycleGotoInit() {
    previewGotoCyclePanel.setDefaultValue(bamControlPreview.cycleGet());
    previewGotoCyclePanel.setValue(previewGotoCyclePanel.getDefaultValue());
    previewGotoCyclePanel.activate();
  }

  /** Action for "Goto cycle" button: sets the specified cycle index if available. */
  private void previewSetCycle(int cycleIdx) {
    if (cycleIdx != bamControlPreview.cycleGet() && cycleIdx >= 0 && cycleIdx < bamControlPreview.cycleCount()) {
      bamControlPreview.cycleSet(cycleIdx);
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Action for "Next frame" button: selects next frame index if available. */
  private void previewFrameUp() {
    if (bamControlPreview.cycleGetFrameIndex() < bamControlPreview.cycleFrameCount() - 1) {
      bamControlPreview.cycleSetFrameIndex(bamControlPreview.cycleGetFrameIndex() + 1);
      updatePreview();
    }
  }

  /** Action for "Previous frame" button: selects previous frame index if available. */
  private void previewFrameDown() {
    if (bamControlPreview.cycleGetFrameIndex() > 0) {
      bamControlPreview.cycleSetFrameIndex(bamControlPreview.cycleGetFrameIndex() - 1);
      updatePreview();
    }
  }

  /** Action for "First frame" button: selects first frame index if available. */
  private void previewFrameStart() {
    if (bamControlPreview.cycleGetFrameIndex() != 0) {
      bamControlPreview.cycleSetFrameIndex(0);
      updatePreview();
    }
  }

  /** Action for "Last frame" button: selects last frame index if available. */
  private void previewFrameEnd() {
    if (bamControlPreview.cycleGetFrameIndex() != bamControlPreview.cycleFrameCount() - 1) {
      bamControlPreview.cycleSetFrameIndex(bamControlPreview.cycleFrameCount() - 1);
      updatePreview();
    }
  }

  /** Returns the current playback mode. */
  private int previewGetMode() {
    return cbPreviewMode.getSelectedIndex();
  }

  /** Sets the current playback mode. */
  private void previewSetMode(int mode) {
    if (mode >= 0 && mode < cbPreviewMode.getItemCount()) {
      cbPreviewMode.setSelectedIndex(mode);
    }
  }

  /** Sets a new frame rate. */
  private void previewSetFrameRate(double fps) {
    try {
      sPreviewFps.setValue(fps);
      currentFps = (Double) sPreviewFps.getValue();
      timer.setDelay((int) (1000.0 / currentFps));
    } catch (IllegalArgumentException e) {
      Logger.trace(e);
    }
  }

  /** Returns whether the preview is in zoom mode. */
  private boolean previewIsZoomed() {
    return cbPreviewZoom.isSelected();
  }

  /** Sets the zoom mode of the preview. */
  private void previewSetZoom(boolean set) {
    cbPreviewZoom.setSelected(set);
    previewPrepare(false);
  }

  /** Returns whether markers are visible. */
  private boolean previewIsMarkerVisible() {
    return cbPreviewShowMarker.isSelected();
  }

  /** Sets the visibility state of markers. */
  private void previewSetMarkerVisible(boolean set) {
    cbPreviewShowMarker.setSelected(set);
    previewPrepare(false);
  }

  /**
   * Advances the animation by one step, depending on the current playback mode.
   *
   * @return {@code false} if no more advancements can be done.
   */
  private boolean previewAdvanceAnimation() {
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
  private void previewValidate() {
    previewPrepare(true);
    updatePreview();
  }

  /** Updates the BAM control instance for previews. */
  private void previewInitControl() {
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
  private void previewPrepare(boolean update) {
    if (update) {
      // updating finalized BAM structure
      try {
        updateFilteredBamDecoder(getBamVersion(), false);
      } catch (Exception e) {
        Logger.error(e);
      }
    }

    previewInitControl();

    int zoom = previewIsZoomed() ? 2 : 1;
    Dimension dim = bamControlPreview.getSharedDimension();
    if (previewCanvas == null || previewCanvas.getWidth() != dim.width || previewCanvas.getHeight() != dim.height) {
      previewCanvas = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
    }
    boolean sizeChanged = false;
    dim.width += 2;
    dim.height += 2; // adjusting for preview markers
    if (rcPreview.getImage() == null || rcPreview.getImage().getWidth(null) != dim.width
        || rcPreview.getImage().getHeight(null) != dim.height) {
      rcPreview.setImage(ColorConvert.createCompatibleImage(dim.width, dim.height, true));
      sizeChanged = true;
    }
    if (rcPreview.getPreferredSize().width != dim.width * zoom
        || rcPreview.getPreferredSize().height != dim.height * zoom) {
      rcPreview.setPreferredSize(new Dimension(dim.width * zoom, dim.height * zoom));
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
  private synchronized void previewDisplay() {
    if (bamControlPreview != null && previewCanvas != null && rcPreview.getImage() != null) {
      // clearing old content
      Graphics2D g = previewCanvas.createGraphics();
      try {
        g.setComposite(AlphaComposite.Src);
        g.setColor(ColorConvert.TRANSPARENT_COLOR);
        g.fillRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
      } finally {
        g.dispose();
        g = null;
      }

      // producing frame data
      bamControlPreview.cycleGetFrame(previewCanvas);

      // drawing markers
      g = (Graphics2D) rcPreview.getImage().getGraphics();
      try {
        // clearing old content
        g.setComposite(AlphaComposite.Src);
        g.setColor(ColorConvert.TRANSPARENT_COLOR);
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
          if (msize > imgWidth) {
            msize = imgWidth;
          }
          if (imgWidth > imgHeight) {
            msize = imgHeight;
          }
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

  /** Returns the BAM filter model instance. */
  protected SimpleListModel<BamFilterBase> getFilterModel() {
    return modelFilters;
  }

  /** Inserts a new filter into the filters list. */
  private BamFilterBase filterAdd() {
    BamFilterFactory.FilterInfo fi = (BamFilterFactory.FilterInfo) cbFiltersAdd.getSelectedItem();
    if (fi != null) {
      return filterAdd(fi);
    }
    return null;
  }

  /** Inserts the specified filter into the filters list. */
  protected BamFilterBase filterAdd(BamFilterFactory.FilterInfo info) {
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
        modelFilters.add(idx + 1, filter);
        listFilters.setSelectedIndex(idx + 1);
        outputSetModified(true);
      } else {
        JOptionPane.showMessageDialog(this, "Unable to create selected filter.", "Error", JOptionPane.ERROR_MESSAGE);
      }
      return filter;
    }
    return null;
  }

  /** Removes the currently selected filter. */
  private void filterRemove() {
    filterRemove(listFilters.getSelectedIndex());
  }

  /** Removes the specified filter from the filters list. */
  private void filterRemove(int index) {
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
  protected void filterRemoveAll() {
    listFilters.setSelectedIndex(-1);
    for (int i = modelFilters.size() - 1; i >= 0; i--) {
      BamFilterBase filter = modelFilters.get(i);
      filter.close();
    }
    modelFilters.clear();
    updateFilterList();
    outputSetModified(true);
  }

  /** Toggles activation state of the currently selected filter. */
  private void filterToggleState() {
    int index = listFilters.getSelectedIndex();
    if (index >= 0 && index < modelFilters.size()) {
      final BamFilterBase filter = modelFilters.get(index);
      filter.setEnabled(!filter.isEnabled());
      updateFilterList();
      outputSetModified(true);
      listFilters.repaint();
    }
  }

  /** Moves the currently selected filter up. */
  private void filterMoveUp() {
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
  private void filterMoveDown() {
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
  private boolean filterIsMarkerVisible() {
    return cbFiltersShowMarker.isSelected();
  }

  /** Returns the currently selected frame index. */
  private int filterGetPreviewFrameIndex() {
    return ((Integer) sFiltersPreviewFrame.getValue());
  }

  private void filterUpdatePreviewFrameIndex() {
    SpinnerNumberModel model = (SpinnerNumberModel) sFiltersPreviewFrame.getModel();
    int max = ((Integer) model.getMaximum());
    int cur = ((Integer) model.getValue());
    if (max != listFrameEntries.get(BAM_ORIGINAL).size()) {
      max = listFrameEntries.get(BAM_ORIGINAL).size() - 1;
      if (cur >= max) {
        cur = Math.max(max - 1, 0);
      }
      model.setMaximum(max);
      model.setValue(cur);
    }
  }

  /** Prepares the specified frame for quick preview. */
  private void filterSetPreviewFrame(int frameIdx, boolean complete) {
    PseudoBamFrameEntry entry = getFilteredBamFrame(getBamVersion(), filterGetPreviewFrameIndex(), complete);
    if (entry.getFrame().getWidth() != (rcFiltersPreview.getImage().getWidth(null) - 2)
        || entry.getFrame().getHeight() != (rcFiltersPreview.getImage().getHeight(null) - 2)) {
      rcFiltersPreview.setImage(new BufferedImage(entry.getFrame().getWidth() + 2, entry.getFrame().getHeight() + 2,
          BufferedImage.TYPE_INT_ARGB));
    }
    filterDisplay(entry);
  }

  /** Displays the specified entry in the filters quick preview. */
  private void filterDisplay(PseudoBamFrameEntry entry) {
    if (rcFiltersPreview.getImage() != null && entry != null && entry.getFrame() != null) {
      int canvasWidth = rcFiltersPreview.getImage().getWidth(null);
      int canvasHeight = rcFiltersPreview.getImage().getHeight(null);
      int centerX = entry.getCenterX();
      int centerY = entry.getCenterY();

      Graphics2D g = (Graphics2D) rcFiltersPreview.getImage().getGraphics();
      try {
        // clearing old content
        g.setComposite(AlphaComposite.Src);
        g.setColor(ColorConvert.TRANSPARENT_COLOR);
        g.fillRect(0, 0, canvasWidth, canvasHeight);

        // rendering frame
        g.drawImage(entry.getFrame(), 1, 1, null);

        if (filterIsMarkerVisible()) {
          // drawing bounding box
          g.setColor(new Color(0x00A000));
          g.drawRect(0, 0, canvasWidth - 1, canvasHeight - 1);

          // drawing center marker (if frame is big enough)
          int msize = 8;
          if (msize > canvasWidth) {
            msize = canvasWidth;
          }
          if (canvasWidth > canvasHeight) {
            msize = canvasHeight;
          }
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
   * Triggers the update function for each filter in the list, enabling them to react to the current state of the
   * converter.
   */
  private void filterUpdateControls() {
    for (int i = 0; i < modelFilters.size(); i++) {
      modelFilters.get(i).updateControls();
    }
  }

  /**
   * A helper method that performs a given operation on selected list items after optional user confirmation.
   *
   * @param <T>               List item type.
   * @param list              {@code JList} instance to perform the operation on.
   * @param operation         The operation to perform if selected list items are available.
   * @param confirm           whether to ask for confirmation before performing the operation on the list.
   * @param msgPromptSingle   Confirmation string if a single list item is selected. May contain a {@code %d}
   *                          placeholder for the number of selected list items.
   * @param msgPromptMultiple Confirmation string if multiple list items are selected. May contain a {@code %d}
   *                          placeholder for the number of selected list items.
   */
  private <T> void doWithSelectedListItems(JList<T> list, Consumer<JList<T>> operation, boolean confirm,
      String msgPromptSingle, String msgPromptMultiple) {
    if (list == null || operation == null) {
      return;
    }

    int selectedCount = list.getSelectedIndices().length;
    if (selectedCount == 0) {
      return;
    }

    boolean accepted = !confirm;
    if (confirm) {
      String message = "Remove selected item(s)?";
      String fmt;
      if (selectedCount == 1) {
        fmt = (msgPromptSingle != null) ? msgPromptSingle : msgPromptMultiple;
      } else {
        fmt = (msgPromptMultiple != null) ? msgPromptMultiple : msgPromptSingle;
      }
      if (fmt != null) {
        try {
          message = String.format(fmt, selectedCount);
        } catch (IllegalFormatException e) {
          Logger.trace(e);
        }
      }

      int retVal = JOptionPane.showConfirmDialog(this, message, "Question", JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE);
      accepted = (retVal == JOptionPane.YES_OPTION);
    }

    if (accepted) {
      operation.accept(list);
    }
  }

  /** Action for selecting BAM version in export section: 0=BAM v1, 1=BAM v2. */
  private void setBamVersion(int index) {
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
      CardLayout cl = (CardLayout) pFramesOptionsVersion.getLayout();
      cl.show(pFramesOptionsVersion, s);
      // setting enabled state of frame compression flag in info box
      cbCompressFrame.setEnabled(index == 0);
    }
    outputSetModified(true);
  }

  /** Action for BAM version help: Displays a message dialog with information. */
  private void showVersionHelp() {
    final String helpMsg = "\"Legacy (v1)\" is the old and proven BAM format supported by all available\n"
        + "Infinity Engine games. It uses a global 256 color table for all frames\n"
        + "and supports simple bitmasked transparency.\n\n"
        + "\"PVRZ-based (v2)\" uses a new BAM format introduced by BG:EE. Graphics data\n"
        + "is stored separately in PVRZ files. Each frame supports interpolated alpha\n"
        + "transitions and is not limited to a global 256 color table.\n"
        + "It is only supported by the Enhanced Editions of the Baldur's Gate series.";
    JOptionPane.showMessageDialog(this, helpMsg, "About BAM versions", JOptionPane.INFORMATION_MESSAGE);
  }

  /** Action for BAM v2 compression help: Displays a message dialog with information. */
  private void showCompressionHelp() {
    final String helpMsg = "\"DXT1\" provides the highest compression ratio. It supports only 1 bit alpha\n"
        + "(i.e. either no or full transparency) and is the preferred type for TIS or MOS resources.\n\n"
        + "\"DXT5\" provides an average compression ratio. It features interpolated\n"
        + "alpha transitions and is the preferred type for BAM resources.\n\n"
        + "\"Auto\" selects the most appropriate compression type based on the input data.";
    JOptionPane.showMessageDialog(this, helpMsg, "About compression types", JOptionPane.INFORMATION_MESSAGE);
  }

  /** Action for "Compress BAM" in BAM v1 export. */
  private void updateCompressBam() {
    Boolean b = cbCompressBam.isSelected();
    getBamDecoder(BAM_ORIGINAL).setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, b);
  }

  /** Specify a BAM output file. */
  private Path setBamOutput() {
    Path rootPath = null;
    if (bamOutputFile != null) {
      rootPath = FileManager.resolve(bamOutputFile);
    }
    Path outFile = getSaveFileName(this, "Specify output file", rootPath,
        new FileNameExtensionFilter[] { getBamFilter() }, 0);
    if (outFile != null) {
      outFile = StreamUtils.replaceFileExtension(outFile, "BAM");
      bamOutputFile = outFile;
    }

    return outFile;
  }

  /** Returns the min/max values from the specified array of indices in a Pair object. */
  private Couple<Integer, Integer> getIndexBounds(int[] indices) {
    Couple<Integer, Integer> retVal = Couple.with(-1, -1);
    if (indices != null && indices.length > 0) {
      retVal.setValue0(Integer.MAX_VALUE);
      retVal.setValue1(Integer.MIN_VALUE);
      for (int index : indices) {
        if (index < retVal.getValue0()) {
          retVal.setValue0(index);
        }
        if (index > retVal.getValue1()) {
          retVal.setValue1(index);
        }
      }
    }
    return retVal;
  }

  /** Attempts to find the most appropriate DXT compression type based on the source frames. */
  private DxtEncoder.DxtType getAutoDxtType() {
    // collecting referenced source frames
    final HashSet<Integer> framesSet = new HashSet<>();
    final PseudoBamControl control = bamDecoder.createControl();
    for (int cycleIdx = 0, cycleCount = control.cycleCount(); cycleIdx < cycleCount; cycleIdx++) {
      for (int frameIdx = 0, frameCount = control.cycleFrameCount(cycleIdx); frameIdx < frameCount; frameIdx++) {
        final int frameIdxAbs = control.cycleGetFrameIndexAbsolute(cycleIdx, frameIdx);
        if (frameIdxAbs >= 0) {
          framesSet.add(frameIdxAbs);
        }
      }
    }

    // scanning frames
    for (final int frameIdx : framesSet) {
      final PseudoBamFrameEntry frameEntry = bamDecoder.getFramesList().get(frameIdx);
      final BufferedImage image = frameEntry.getFrame();
      final WritableRaster raster = image.getRaster();
      final Object pixBuf;
      switch (raster.getTransferType()) {
        case DataBuffer.TYPE_BYTE:
          pixBuf = new byte[raster.getNumDataElements()];
          break;
        case DataBuffer.TYPE_SHORT:
        case DataBuffer.TYPE_USHORT:
          pixBuf = new short[raster.getNumDataElements()];
          break;
        case DataBuffer.TYPE_INT:
          pixBuf = new int[raster.getNumDataElements()];
          break;
        case DataBuffer.TYPE_FLOAT:
          pixBuf = new float[raster.getNumDataElements()];
          break;
        case DataBuffer.TYPE_DOUBLE:
          pixBuf = new double[raster.getNumDataElements()];
          break;
        default:
          pixBuf = null;
          Logger.warn("Could not determine color format of frame: " + frameEntry);
      }

      if (pixBuf != null) {
        final ColorModel model = image.getColorModel();
        for (int y = 0, height = image.getHeight(); y < height; y++) {
          for (int x = 0, width = image.getWidth(); x < width; x++) {
            raster.getDataElements(x, y, pixBuf);
            final int alpha = model.getAlpha(pixBuf);
            if (alpha != 0 && alpha != 255) {
              return DxtEncoder.DxtType.DXT5;
            }
          }
        }
      }
    }

    return DxtEncoder.DxtType.DXT1;
  }

  private List<String> convert() {
    List<String> result = new Vector<>(2);
    try {
      updateFilteredBamDecoder(getBamVersion(), false);
      List<BamFilterBaseOutput> outList = createOutputFilterList();
      if (!outList.isEmpty()) {
        for (BamFilterBaseOutput element : outList) {
          // processing output filter
          if (!element.process(bamDecoderFinal)) {
            throw new Exception("Conversion failed.");
          }
        }
      } else {
        throw new Exception("No output filter specified.");
      }
      result.add("Conversion finished successfully.");
    } catch (Exception e) {
      Logger.error(e);
      result.add(null);
      result.add(String.format("Error while exporting BAM files.\n(%s)", e.getMessage()));
    }
    return result;
  }

  /** Returns whether the output/preview BAM has been modified. */
  private boolean outputIsModified() {
    return isPreviewModified;
  }

  /** Defines whether the internal BAM structure has changed in any way. */
  protected void outputSetModified(boolean isModified) {
    isPreviewModified = isModified;
  }

  /** Returns a single filtered BAM frame. Set "complete" to recreate the whole filter chain. */
  private PseudoBamFrameEntry getFilteredBamFrame(int bamVersion, int frameIdx, boolean complete) {
    int curFilterIdx = listFilters.getSelectedIndex();

    // recreating filtered preview
    if (complete) {
      updateFinalBamFrame(bamVersion, frameIdx);

      // processing each filter that exists before the selected filter
      PseudoBamFrameEntry entry = entryFilterPreview;
      for (int i = 0; i < curFilterIdx; i++) {
        if (modelFilters.get(i) != null) {
          BamFilterBase filter = modelFilters.get(i);
          if (filter.isEnabled()) {
            entry = filter.updatePreview(frameIdx, entry);
          }
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
          entryFilterPreview.getCenterX(), entryFilterPreview.getCenterY());
      BamFilterBase filter = modelFilters.get(curFilterIdx);
      if (filter != null && filter.isEnabled()) {
        entry = filter.updatePreview(frameIdx, entry);
      }
    }

    return entry;
  }

  /** Updates the final BAM structure in bamDecoderFinal. */
  private void updateFilteredBamDecoder(int bamVersion, boolean force) throws Exception {
    if (outputIsModified() || force) {
      outputSetModified(false);

      updateFinalBamDecoder(bamVersion);

      // Processing each filter sequentially
      List<BamFilterBase> filters = createFilterList(false);
      for (final BamFilterBase filter : filters) {
        if (filter != null && !filter.isEnabled()) {
          continue;
        }

        if (filter instanceof BamFilterBaseColor) {
          // processing color filter
          try {
            final BamFilterBaseColor colorFilter = (BamFilterBaseColor) filter;
            for (PseudoBamFrameEntry element : listFrameEntries.get(BAM_FINAL)) {
              BufferedImage image = colorFilter.process(element.getFrame());
              if (image != null) {
                element.setFrame(image);
                image = null;
              } else {
                throw new Exception();
              }
            }
          } catch (Exception e) {
            Logger.error(e);
            throw e;
          }
        } else if (filter instanceof BamFilterBaseTransform) {
          // processing transform filter
          try {
            final BamFilterBaseTransform transformFilter = (BamFilterBaseTransform) filter;
            for (int frameIdx = 0, frameSize = listFrameEntries.get(BAM_FINAL).size(); frameIdx < frameSize; frameIdx++) {
              final PseudoBamFrameEntry srcEntry = listFrameEntries.get(BAM_FINAL).get(frameIdx);
              final PseudoBamFrameEntry dstEntry = transformFilter.process(srcEntry);
              if (dstEntry != null) {
                if (dstEntry != srcEntry) {
                  listFrameEntries.get(BAM_FINAL).set(frameIdx, dstEntry);
                }
              } else {
                throw new Exception(String.format("%s: Result is null", transformFilter));
              }
            }
          } catch (Exception e) {
            Logger.error(e);
            throw e;
          }
        } else if (filter instanceof BamFilterBaseOutput) {
          // skipping output filter
        } else {
          if (filter != null) {
            Logger.warn("Skipping unrecognized filter: {}", filter);
          } else {
            Logger.warn("Skipping null filter");
          }
        }
      }
    }
  }

  /** Creates a sorted list including all selected filters in the post-processing tab. */
  private List<BamFilterBase> createFilterList(boolean includeOutputFilters) {
    List<BamFilterBase> retVal = new ArrayList<>();
    List<BamFilterBase> outFilters = new ArrayList<>();
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
      retVal.addAll(outFilters);
    }

    return retVal;
  }

  /** Creates a list of selected output filters only. */
  private List<BamFilterBaseOutput> createOutputFilterList() {
    List<BamFilterBaseOutput> retVal = new ArrayList<>();
    for (int i = 0; i < modelFilters.size(); i++) {
      final BamFilterBase filter = modelFilters.get(i);
      if (filter instanceof BamFilterBaseOutput && filter.isEnabled()) {
        retVal.add((BamFilterBaseOutput)filter);
      }
    }
    if (retVal.isEmpty()) {
      retVal.add((BamFilterBaseOutput) BamFilterFactory.createInstance(this, BamFilterOutputDefault.class));
    }

    return retVal;
  }

  /**
   * Creates a new BAM structure from the existing structure that is compatible with the specified target BAM version.
   */
  private void updateFinalBamDecoder(int bamVersion) throws Exception {
    listFrameEntries.get(BAM_FINAL).clear();

    if (bamVersion == VERSION_BAMV1 || bamVersion == VERSION_BAMV2) {
      List<PseudoBamFrameEntry> srcListFrames = listFrameEntries.get(BAM_ORIGINAL);
      List<PseudoBamFrameEntry> dstListFrames = listFrameEntries.get(BAM_FINAL);

      // copying global options
      String[] options = bamDecoder.getOptionNames();
      for (String option : options) {
        bamDecoderFinal.setOption(option, bamDecoder.getOption(option));
      }

      if (bamVersion == VERSION_BAMV1) {
        // BAM v1: creating paletted version of each source frame
        paletteDialog.updateGeneratedPalette();

        final int Green = 0x0000ff00;
        bamDecoderFinal.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX, paletteDialog.getRleIndex());
        bamDecoderFinal.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, isBamV1Compressed());
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
        HashMap<Integer, Byte> colorCache = new HashMap<>(4096);
        for (int i = 0; i < palette.length; i++) {
          if (i != transIndex) {
            colorCache.put(palette[i], (byte) i);
          }
        }

        // processing frames
        IndexColorModel cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transIndex, DataBuffer.TYPE_BYTE);
        for (int i = 0; i < srcListFrames.size(); i++) {
          final PseudoBamFrameEntry srcEntry = srcListFrames.get(i);
          BufferedImage srcImage = ColorConvert.toBufferedImage(srcEntry.getFrame(), true, true);
          int[] srcBuf = ((DataBufferInt) srcImage.getRaster().getDataBuffer()).getData();
          BufferedImage dstImage = new BufferedImage(srcEntry.getWidth(), srcEntry.getHeight(),
              BufferedImage.TYPE_BYTE_INDEXED, cm);
          byte[] dstBuf = ((DataBufferByte) dstImage.getRaster().getDataBuffer()).getData();

          for (int ofs = 0; ofs < srcBuf.length; ofs++) {
            int c = srcBuf[ofs];
            if (PseudoBamDecoder.isTransparentColor(c, threshold)) {
              dstBuf[ofs] = (byte) transIndex;
            } else {
              Byte colIdx = colorCache.get(c);
              if (colIdx != null) {
                dstBuf[ofs] = colIdx;
              } else {
                double weight = getUseAlpha() ? 1.0 : 0.0;
                byte color = (byte) ColorConvert.getNearestColor(srcBuf[ofs], palette, weight, null, true);
                dstBuf[ofs] = color;
                colorCache.put(c, color);
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
          for (String option : options) {
            dstEntry.setOption(option, srcEntry.getOption(option));
          }
          dstListFrames.add(dstEntry);
        }
      } else {
        // BAM v2: create truecolored version of each frame
        for (PseudoBamFrameEntry srcEntry : srcListFrames) {
          BufferedImage dstImage = ColorConvert.toBufferedImage(srcEntry.getFrame(), true, true);
          PseudoBamFrameEntry dstEntry = new PseudoBamFrameEntry(dstImage, srcEntry.getCenterX(),
              srcEntry.getCenterY());
          // adding frame-specific options
          options = srcEntry.getOptionNames();
          for (String option : options) {
            dstEntry.setOption(option, srcEntry.getOption(option));
          }
          dstListFrames.add(dstEntry);
        }
      }
    } else {
      throw new Exception("Unknown target BAM format");
    }
  }

  /** Creates a new single frame that is compatible with the specified BAM version. */
  private void updateFinalBamFrame(int bamVersion, int frameIdx) {
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
        HashMap<Integer, Byte> colorCache = new HashMap<>(4096);
        for (int i = 0; i < palette.length; i++) {
          if (i != transIndex) {
            colorCache.put(palette[i], (byte) i);
          }
        }
        IndexColorModel cm = new IndexColorModel(8, 256, palette, 0, getUseAlpha(), transIndex, DataBuffer.TYPE_BYTE);

        // converting frame
        srcImage = ColorConvert.toBufferedImage(srcImage, true, true);
        int[] srcBuf = ((DataBufferInt) srcImage.getRaster().getDataBuffer()).getData();
        dstImage = new BufferedImage(srcImage.getWidth(), srcImage.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, cm);
        byte[] dstBuf = ((DataBufferByte) dstImage.getRaster().getDataBuffer()).getData();

        for (int ofs = 0; ofs < srcBuf.length; ofs++) {
          int c = srcBuf[ofs];
          if (PseudoBamDecoder.isTransparentColor(c, threshold)) {
            dstBuf[ofs] = (byte) transIndex;
          } else {
            Byte colIdx = colorCache.get(c);
            if (colIdx != null) {
              int ci = colIdx.intValue() & 0xff;
              if (ci >= transIndex) {
                ci++;
              }
              dstBuf[ofs] = colIdx;
            } else {
              double weight = getUseAlpha() ? 1.0 : 0.0;
              byte color = (byte) ColorConvert.getNearestColor(srcBuf[ofs], palette, weight, null, true);
              dstBuf[ofs] = color;// (byte)ci;
              colorCache.put(c, color);
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
  protected void updateRecentSession(Path session) {
    if (session != null) {
      BamOptionsDialog.updateRecentSession(session);
      initSessionEntries();
    }
  }

  /** (Re-)creates a list of recently accessed BAM session paths. */
  private void initSessionEntries() {
    if (miSessionHistory == null) {
      miSessionHistory = new JMenu("Load recent sessions");
    } else {
      Component[] comp = miSessionHistory.getComponents();
      for (final Component c : comp) {
        if (c instanceof DataMenuItem) {
          ((DataMenuItem) c).removeActionListener(this);
        }
      }
      miSessionHistory.removeAll();
    }

    List<Path> recentSessions = BamOptionsDialog.getRecentSessions();
    for (final Path item : recentSessions) {
      DataMenuItem dmi = new DataMenuItem(item.getFileName().toString(), -1, item);
      dmi.setToolTipText(item.toString());
      dmi.addActionListener(this);
      miSessionHistory.add(dmi);
    }
    miSessionHistory.setEnabled(!recentSessions.isEmpty());
  }

  /** Initializes a new ProgressMonitor instance. */
  protected void initProgressMonitor(Component parent, String msg, String note, int maxProgress, int msDecide, int msWait) {
    if (parent == null) {
      parent = NearInfinity.getInstance();
    }
    if (maxProgress <= 0) {
      maxProgress = 1;
    }

    releaseProgressMonitor();
    pmMax = maxProgress;
    pmCur = 0;
    progress = new ProgressMonitor(parent, msg, note, 0, pmMax);
    progress.setMillisToDecideToPopup(msDecide);
    progress.setMillisToPopup(msWait);
    progress.setProgress(pmCur);
  }

  /** Closes an active instance of the ProgressMonitor. */
  protected void releaseProgressMonitor() {
    if (progress != null) {
      progress.close();
      progress = null;
    }
  }

  /** Advances the active ProgressMonitor instance by one step. */
  protected void advanceProgressMonitor(String note) {
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
  protected int getProgressMonitorStage() {
    if (progress != null) {
      return pmCur;
    } else {
      return 0;
    }
  }

  /** Sets the current stage of the active ProgressMonitor instance. */
  protected void setProgressMonitorState(int stage) {
    if (progress != null) {
      if (stage < progress.getMinimum()) {
        stage = progress.getMinimum();
      }
      if (stage > progress.getMaximum()) {
        stage = progress.getMaximum();
      }
      progress.setProgress(stage);
    }
  }

  /** Returns the max. stage of the active ProgressMonitor instance. */
  protected int getProgressMonitorMax() {
    if (progress != null) {
      return pmMax;
    } else {
      return 0;
    }
  }

  /** Sets a new max. stage to the active ProgressMonitor instance. */
  protected void setProgressMonitorMax(int max) {
    if (progress != null) {
      if (max >= 0) {
        progress.setMaximum(max);
      }
    }
  }

  /** Sets a new note to the active ProgressMonitor instance. */
  protected void setProgressMonitorNote(String note) {
    if (progress != null) {
      progress.setNote(note);
    }
  }

  /** Returns whether the active ProgressMonitor instance has been cancelled by the user. */
  protected boolean isProgressMonitorCancelled() {
    if (isProgressMonitorActive()) {
      return progress.isCanceled();
    }
    return false;
  }

  /** Returns whether a ProgressMonitor instance is active. */
  protected boolean isProgressMonitorActive() {
    return (progress != null);
  }

  /** Returns the ProgressMonitor instance, or null if not active. */
  protected ProgressMonitor getProgressMonitor() {
    return progress;
  }

  // -------------------------- INNER CLASSES --------------------------

  /** Drag&Drop handler for dragging external files or folders to the frames list. */
  private class ListFileTransferHandler extends TransferHandler {
    @Override
    public boolean canImport(TransferSupport support) {
      return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!support.isDrop()) {
        return false;
      }

      if (support.getComponent() == listFrames) {
        try {
          List<?> fileList = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
          JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();

          Path[] filePath = new Path[1];
          boolean retVal = false;
          for (int i = fileList.size() - 1; i >= 0; i--) {
            if (fileList.get(i) instanceof File) {
              final File f = (File) fileList.get(i);
              // Mixed list of files and folders is supported
              if (f.isFile()) {
                filePath[0] = f.toPath();
                framesAdd(filePath, dl.getIndex() - 1);
                retVal = true;
              } else if (f.isDirectory()) {
                framesAddFolder(f.toPath(), dl.getIndex() - 1);
                retVal = true;
              }
            }
          }

          return retVal;
        } catch (Exception e) {
          Logger.trace(e);
        }
      }

      return false;
    }
  }
}
