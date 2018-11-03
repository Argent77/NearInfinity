// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.util;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.NearInfinity;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.Center;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.Writeable;
import org.infinity.resource.bcs.Decompiler;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamResource;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.Compressor;
import org.infinity.resource.graphics.MosDecoder;
import org.infinity.resource.graphics.MosV1Decoder;
import org.infinity.resource.graphics.PvrDecoder;
import org.infinity.resource.graphics.TisDecoder;
import org.infinity.resource.graphics.TisResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.sound.AudioFactory;
import org.infinity.resource.video.MveResource;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

public final class MassExporter extends ChildFrame implements ActionListener, ListSelectionListener,
                                                              Runnable
{
  private static final String FMT_PROGRESS = "Processing resource %d/%d";
  private static final String TYPES[] = {"2DA", "ARE", "BAM", "BCS", "BS", "BIO", "BMP",
                                         "CHU", "CHR", "CRE", "DLG", "EFF", "FNT", "GAM",
                                         "GLSL", "GUI", "IDS", "INI", "ITM", "LUA", "MENU",
                                         "MOS", "MVE", "PLT", "PNG", "PRO", "PVRZ", "RES",
                                         "SPL", "SQL", "SRC", "STO", "TIS", "TOH", "TOT",
                                         "TTF", "VEF", "VVC", "WAV", "WBM", "WED", "WFX", "WMP"};
  private final JButton bExport = new JButton("Export", Icons.getIcon(Icons.ICON_EXPORT_16));
  private final JButton bCancel = new JButton("Cancel", Icons.getIcon(Icons.ICON_DELETE_16));
  private final JButton bDirectory = new JButton(Icons.getIcon(Icons.ICON_OPEN_16));
  private final JCheckBox cbIncludeExtraDirs = new JCheckBox("Include extra folders", true);
  private final JCheckBox cbDecompile = new JCheckBox("Decompile scripts", true);
  private final JCheckBox cbDecrypt = new JCheckBox("Decrypt text files", true);
  private final JCheckBox cbConvertWAV = new JCheckBox("Convert sounds", true);
  private final JCheckBox cbConvertCRE = new JCheckBox("Convert CHR=>CRE", false);
  private final JCheckBox cbDecompress = new JCheckBox("Decompress BAM/MOS", false);
  private final JCheckBox cbConvertToPNG = new JCheckBox("Export MOS/PVRZ/TIS as PNG", false);
  private final JCheckBox cbConvertTisVersion = new JCheckBox("Convert TIS to ", false);
  private final JComboBox<String> cbConvertTisList = new JComboBox<>(new String[]{"Palette-based", "PVRZ-based"});
  private final JCheckBox cbExtractFramesBAM = new JCheckBox("Export BAM frames as ", false);
  private final JCheckBox cbExportMVEasAVI = new JCheckBox("Export MVE as AVI", false);
  private final JCheckBox cbOverwrite = new JCheckBox("Overwrite existing files", false);
  private final JFileChooser fc = new JFileChooser(Profile.getGameRoot().toFile());
  private final JComboBox<String> cbExtractFramesBAMFormat = new JComboBox<>(new String[]{"PNG", "BMP"});
  private final JList<String> listTypes = new JList<>(TYPES);
  private final JTextField tfDirectory = new JTextField(20);
  private Path outputPath;
  private List<String> selectedTypes;
  private ProgressMonitor progress;
  private int progressIndex;
  private List<ResourceEntry> selectedFiles;

  public MassExporter()
  {
    super("Mass Exporter", true);

    bExport.addActionListener(this);
    bCancel.addActionListener(this);
    bDirectory.addActionListener(this);
    bExport.setEnabled(false);
    tfDirectory.setEditable(false);
    listTypes.addListSelectionListener(this);
    fc.setDialogTitle("Mass export: Select directory");
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    getRootPane().setDefaultButton(bExport);
    bExport.setMnemonic('e');
    bCancel.setMnemonic('d');
    cbConvertToPNG.setToolTipText("Caution: Selecting both MOS and TIS may overwrite or skip some files!");
    cbExtractFramesBAM.setToolTipText("Note: Frames of each BAM resource are exported into separate subfolders.");
    cbConvertTisVersion.setToolTipText("Caution: Conversion may take a long time. Files may be renamed to conform to naming scheme for PVRZ-based TIS files.");
    cbIncludeExtraDirs.setToolTipText("Include extra folders, such as \"Characters\" or \"Portraits\", except savegames.");

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(new JLabel("File types to export:"), BorderLayout.NORTH);
    leftPanel.add(new JScrollPane(listTypes), BorderLayout.CENTER);

    JPanel topRightPanel = new JPanel(new BorderLayout());
    topRightPanel.add(new JLabel("Output directory:"), BorderLayout.NORTH);
    topRightPanel.add(tfDirectory, BorderLayout.CENTER);
    topRightPanel.add(bDirectory, BorderLayout.EAST);

    GridBagConstraints gbc = new GridBagConstraints();
    JPanel bottomRightPanel = new JPanel(new GridBagLayout());

    JPanel pBamFrames = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    pBamFrames.add(cbExtractFramesBAM);
    pBamFrames.add(cbExtractFramesBAMFormat);

    JPanel pTisConvert = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    pTisConvert.add(cbConvertTisVersion);
    pTisConvert.add(cbConvertTisList);

    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    bottomRightPanel.add(new JLabel("Options:"), gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbIncludeExtraDirs, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbConvertWAV, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 3, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbConvertCRE, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 4, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbDecompile, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 5, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbDecrypt, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 6, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbDecompress, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 7, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbConvertToPNG, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 8, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(pTisConvert, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 9, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(pBamFrames, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 10, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbExportMVEasAVI, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 11, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    bottomRightPanel.add(cbOverwrite, gbc);

    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottomPanel.add(bExport);
    bottomPanel.add(bCancel);

    JPanel pane = (JPanel)getContentPane();
    GridBagLayout gbl = new GridBagLayout();
//    GridBagConstraints gbc = new GridBagConstraints();
    gbc = new GridBagConstraints();
    pane.setLayout(gbl);

    gbc.weightx = 0.0;
    gbc.weighty = 1.0;
    gbc.gridheight = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(6, 6, 6, 6);
    gbl.setConstraints(leftPanel, gbc);
    pane.add(leftPanel);

    gbc.gridheight = 1;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weighty = 0.0;
    gbc.weightx = 1.0;
    gbl.setConstraints(topRightPanel, gbc);
    pane.add(topRightPanel);

    gbc.weighty = 1.0;
    gbl.setConstraints(bottomRightPanel, gbc);
    pane.add(bottomRightPanel);

    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.weighty = 0.0;
    gbc.weightx = 1.0;
    gbl.setConstraints(bottomPanel, gbc);
    pane.add(bottomPanel);

    pack();
    setMinimumSize(getPreferredSize());
    Center.center(this, NearInfinity.getInstance().getBounds());
    setVisible(true);
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == bExport) {
      selectedTypes = listTypes.getSelectedValuesList();
      outputPath = FileManager.resolve(tfDirectory.getText());
      try {
        Files.createDirectories(outputPath);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(this, "Unable to create target directory.",
                                      "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
        return;
      }
      setVisible(false);
      new Thread(this).start();
    }
    else if (event.getSource() == bCancel) {
      setVisible(false);
    } else if (event.getSource() == bDirectory) {
      if (fc.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION)
        tfDirectory.setText(fc.getSelectedFile().toString());
      bExport.setEnabled(listTypes.getSelectedIndices().length > 0 && tfDirectory.getText().length() > 0);
    }
  }

// --------------------- End Interface ActionListener ---------------------


// --------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent event)
  {
    bExport.setEnabled(listTypes.getSelectedIndices().length > 0 && tfDirectory.getText().length() > 0);
  }

// --------------------- End Interface ListSelectionListener ---------------------


// --------------------- Begin Interface Runnable ---------------------

  @Override
  public void run()
  {
    try {
      List<Path> extraDirs = new ArrayList<>();
      if (cbIncludeExtraDirs.isSelected()) {
        // do not include savegame folders
        extraDirs.addAll(Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS));
        int idx = 0;
        while (idx < extraDirs.size()) {
          String s = extraDirs.get(idx).getFileName().toString().toUpperCase(Locale.ENGLISH);
          if (s.contains("SAVE")) {
            extraDirs.remove(idx);
          } else {
            idx++;
          }
        }
      }

      selectedFiles = new ArrayList<ResourceEntry>(1000);
      for (final String newVar : selectedTypes) {
        selectedFiles.addAll(ResourceFactory.getResources(newVar, extraDirs));
      }

      // executing multithreaded search
      boolean isCancelled = false;
      ThreadPoolExecutor executor = Misc.createThreadPool();
      progress = new ProgressMonitor(NearInfinity.getInstance(), "Exporting...",
                                     String.format(FMT_PROGRESS, getResourceCount(), getResourceCount()),
                                     0, selectedFiles.size());
      progress.setMillisToDecideToPopup(0);
      progress.setMillisToPopup(0);
      progress.setProgress(0);
      progress.setNote(String.format(FMT_PROGRESS, 0, getResourceCount()));
      Debugging.timerReset();
      for (int i = 0, count = getResourceCount(); i < count; i++) {
        Misc.isQueueReady(executor, true, -1);
        executor.execute(new Worker(selectedFiles.get(i)));
        if (progress.isCanceled()) {
          isCancelled = true;
          break;
        }
      }

      // enforcing thread termination if process has been cancelled
      if (isCancelled) {
        executor.shutdownNow();
      } else {
        executor.shutdown();
      }

      // waiting for pending threads to terminate
      while (!executor.isTerminated()) {
        if (!isCancelled && progress.isCanceled()) {
          executor.shutdownNow();
          isCancelled = true;
        }
        try { Thread.sleep(1); } catch (InterruptedException e) {}
      }

      if (isCancelled) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Mass export aborted",
                                      "Info", JOptionPane.INFORMATION_MESSAGE);
      } else {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Mass export completed",
                                      "Info", JOptionPane.INFORMATION_MESSAGE);
      }
    } finally {
      advanceProgress(true);
      if (selectedFiles != null) {
        selectedFiles.clear();
      }
      selectedFiles = null;
    }
    Debugging.timerShow("Mass export completed", Debugging.TimeFormat.MILLISECONDS);
  }

// --------------------- End Interface Runnable ---------------------

  private int getResourceCount()
  {
    return (selectedFiles != null) ? selectedFiles.size() : 0;
  }

  private synchronized void advanceProgress(boolean finished)
  {
    if (progress != null) {
      if (finished) {
        progressIndex = 0;
        progress.close();
        progress = null;
      } else {
        progressIndex++;
        if (getResourceCount() < 50 || progressIndex % 10 == 0) {
          progress.setNote(String.format(FMT_PROGRESS, progressIndex, getResourceCount()));
        }
        progress.setProgress(progressIndex);
      }
    }
  }

  private void exportText(ResourceEntry entry, Path output) throws Exception
  {
    ByteBuffer bb = entry.getResourceBuffer();
    if (bb.limit() > 0) {
      if (bb.limit() > 1 && bb.getShort(0) == -1) {
        bb = StaticSimpleXorDecryptor.decrypt(bb, 2);
      }
      // Keep trying. File may be in use by another thread.
      try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
        StreamUtils.writeBytes(os, bb);
      }
    }
  }

  private void exportDecompiledScript(ResourceEntry entry, Path output) throws Exception
  {
    output = output.getParent().resolve(StreamUtils.replaceFileExtension(output.getFileName().toString(), "BAF"));
    if (Files.exists(output) && !cbOverwrite.isSelected()) {
      return;
    }
    ByteBuffer bb = entry.getResourceBuffer();
    if (bb.limit() > 0) {
      if (bb.limit() > 1 && bb.getShort(0) == -1) {
        bb = StaticSimpleXorDecryptor.decrypt(bb, 2);
      }
      Decompiler decompiler = new Decompiler(StreamUtils.readString(bb, bb.limit()), false);
      decompiler.setGenerateComments(BrowserMenuBar.getInstance().autogenBCSComments());
      String script = decompiler.getSource();
      // Keep trying. File may be in use by another thread.
      try (BufferedWriter bw = new BufferedWriter(tryOpenOutputWriter(output, 10, 100))) {
        bw.write(script.replaceAll("\r?\n", Misc.LINE_SEPARATOR));
        bw.newLine();
      }
    }
  }

  private void decompressBamMos(ResourceEntry entry, Path output) throws Exception
  {
    ByteBuffer bb = entry.getResourceBuffer();
    if (bb.limit() > 0) {
      String sig = StreamUtils.readString(bb, 4);
      if (sig.equals("BAMC") || sig.equals("MOSC")) {
        bb = Compressor.decompress(bb);
      }
      // Keep trying. File may be in use by another thread.
      try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
        StreamUtils.writeBytes(os, bb);
      }
    }
  }

  private void decompressWav(ResourceEntry entry, Path output) throws Exception
  {
    ByteBuffer buffer = StreamUtils.getByteBuffer(AudioFactory.convertAudio(entry));
    if (buffer != null && buffer.limit() > 0) {
      // Keep trying. File may be in use by another thread.
      try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
        StreamUtils.writeBytes(os, buffer);
      }
    }
  }

  private void mosToPng(ResourceEntry entry, Path output) throws Exception
  {
    if (entry != null && entry.getExtension().equalsIgnoreCase("MOS")) {
      output = outputPath.resolve(StreamUtils.replaceFileExtension(entry.getResourceName(), "PNG"));
      if (Files.exists(output) && !cbOverwrite.isSelected()) {
        return;
      }

      MosDecoder decoder = MosDecoder.loadMos(entry);
      if (decoder != null) {
        if (decoder instanceof MosV1Decoder) {
          ((MosV1Decoder)decoder).setTransparencyEnabled(true);
        }
        RenderedImage image = ColorConvert.toBufferedImage(decoder.getImage(), true);
        try {
          ImageIO.write(image, "png", output.toFile());
        } finally {
          image = null;
        }
      } else {
        throw new Exception(String.format("Error loading resource: %s", entry.getResourceName()));
      }
    }
  }

  private void pvrzToPng(ResourceEntry entry, Path output) throws Exception
  {
    if (entry != null && entry.getExtension().equalsIgnoreCase("PVRZ")) {
      output = outputPath.resolve(StreamUtils.replaceFileExtension(entry.getResourceName(), "PNG"));
      if (Files.exists(output) && !cbOverwrite.isSelected()) {
        return;
      }

      PvrDecoder decoder = PvrDecoder.loadPvr(entry);
      if (decoder != null) {
        RenderedImage image = decoder.decode();
        try {
          ImageIO.write(image, "png", output.toFile());
        } finally {
          image = null;
        }
      } else {
        throw new Exception(String.format("Error loading resource: %s", entry.getResourceName()));
      }
    }
  }

  private void tisToPng(ResourceEntry entry, Path output) throws Exception
  {
    if (entry != null && entry.getExtension().equalsIgnoreCase("TIS")) {
      output = outputPath.resolve(StreamUtils.replaceFileExtension(entry.getResourceName(), "PNG"));
      if (Files.exists(output) && !cbOverwrite.isSelected()) {
        return;
      }

      TisDecoder decoder = TisDecoder.loadTis(entry);
      if (decoder != null) {
        int tileCount = decoder.getTileCount();
        int columns = TisResource.calcTileWidth(entry, 1);
        int rows = tileCount / columns;
        if ((tileCount % columns) != 0) {
          rows++;
        }

        BufferedImage tile = ColorConvert.createCompatibleImage(64, 64, Transparency.BITMASK);
        BufferedImage image = ColorConvert.createCompatibleImage(64*columns, 64*rows, Transparency.BITMASK);
        try {
          Graphics2D g = image.createGraphics();
          try {
            for (int i = 0; i < tileCount; i++) {
              int x = 64*(i % columns);
              int y = 64*(i / columns);
              decoder.getTile(i, tile);
              g.drawImage(tile, x, y, null);
            }
          } finally {
            g.dispose();
            g = null;
          }
          ImageIO.write(image, "png", output.toFile());
        } finally {
          tile = null;
          image = null;
        }
      } else {
        throw new Exception(String.format("Error loading resource: %s", entry.getResourceName()));
      }
    }
  }

  private void extractBamFrames(ResourceEntry entry, Path output) throws Exception
  {
    String format = (cbExtractFramesBAMFormat.getSelectedIndex() == 0) ? "png" : "bmp";
    Path filePath = output.getParent();
    String fileName = output.getFileName().toString();
    int extIdx = fileName.lastIndexOf('.');
    String fileBase = (extIdx >= 0) ? fileName.substring(0, extIdx) : fileName;
    String fileExt = "." + format;

    // creating subfolder for frames
    Path path = filePath.resolve(fileBase);
    if (!Files.exists(path)) {
      try {
        Files.createDirectory(path);
      } catch (IOException e) {
        String msg = String.format("Error creating folder \"%s\". Skipping file \"%s\".",
                                   fileBase, fileName);
        System.err.println(msg);
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), msg, "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
    } else if (!Files.isDirectory(path)) {
      String msg = String.format("Folder \"%s\" can not be created. Skipping file \"%s\".",
                                 fileBase, fileName);
      System.err.println(msg);
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), msg, "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }
    filePath = path;

    BamDecoder decoder = BamDecoder.loadBam(entry);
    BamResource.exportFrames(decoder, filePath, fileBase, fileExt, format, true);
  }

  private void chrToCre(ResourceEntry entry, Path output) throws Exception
  {
    output = outputPath.resolve(StreamUtils.replaceFileExtension(entry.getResourceName(), "CRE"));
    if (Files.exists(output) && !cbOverwrite.isSelected()) {
      return;
    }
    CreResource crefile = new CreResource(entry);
    final List<StructEntry> flatList = crefile.getFlatList();
    while (!flatList.get(0).toString().equals("CRE ")) {
      flatList.remove(0);
    }
    // Keep trying. File may be in use by another thread.
    try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
      for (final StructEntry e : flatList) {
        e.write(os);
      }
    }
  }

  private void exportResource(ResourceEntry entry, Path output) throws Exception
  {
    if (entry != null && output != null) {
      System.err.println("Converting " + entry.toString());
      int[] info = entry.getResourceInfo();
      int size = info[0];
      if (info.length > 1) {
        size = size*info[1] + 0x18;
      }
      boolean isTis = (info.length > 1);
      boolean isTisV2 = isTis && (info[1] == 0x0c);

      if (isTis && cbConvertTisVersion.isSelected() &&
          isTisV2 == false && cbConvertTisList.getSelectedIndex() == 1) {
        TisResource tis = new TisResource(entry);
        tis.convertToPvrzTis(TisResource.makeTisFileNameValid(output), false);
      } else if (isTis && cbConvertTisVersion.isSelected() &&
                 isTisV2 == true && cbConvertTisList.getSelectedIndex() == 0) {
        TisResource tis = new TisResource(entry);
        tis.convertToPaletteTis(output, false);
      } else if (size >= 0) {
        try (InputStream is = entry.getResourceDataAsStream()) {
          // Keep trying. File may be in use by another thread.
          try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
            int bytesWritten = (int)StreamUtils.writeBytes(os, is, size);
            if (bytesWritten < size) {
              throw new EOFException(entry.toString() + ": " + bytesWritten + " of " + size + " bytes written");
            }
          }
        }
      }
    }
  }


  private void export(ResourceEntry entry)
  {
    try {
      Path output = outputPath.resolve(entry.getResourceName());
      if (Files.exists(output) && !cbOverwrite.isSelected()) {
        return;
      }
      if ((entry.getExtension().equalsIgnoreCase("IDS") ||
           entry.getExtension().equalsIgnoreCase("2DA") ||
           entry.getExtension().equalsIgnoreCase("BIO") ||
           entry.getExtension().equalsIgnoreCase("RES") ||
           entry.getExtension().equalsIgnoreCase("INI") ||
           entry.getExtension().equalsIgnoreCase("TXT") ||
           (Profile.isEnhancedEdition() &&
               (entry.getExtension().equalsIgnoreCase("GLSL") ||
                entry.getExtension().equalsIgnoreCase("GUI") ||
                entry.getExtension().equalsIgnoreCase("SQL"))) ||
           (entry.getExtension().equalsIgnoreCase("SRC") &&
               Profile.getEngine() == Profile.Engine.IWD2)) &&
          cbDecrypt.isSelected()) {
        exportText(entry, output);
      }
      else if ((entry.getExtension().equalsIgnoreCase("BCS") ||
                entry.getExtension().equalsIgnoreCase("BS")) && cbDecompile.isSelected()) {
        exportDecompiledScript(entry, output);
      }
      else if (entry.getExtension().equalsIgnoreCase("MOS") && cbConvertToPNG.isSelected()) {
        mosToPng(entry, output);
      }
      else if (entry.getExtension().equalsIgnoreCase("PVRZ") && cbConvertToPNG.isSelected()) {
        pvrzToPng(entry, output);
      }
      else if (entry.getExtension().equalsIgnoreCase("TIS") && cbConvertToPNG.isSelected()) {
        tisToPng(entry, output);
      }
      else if (entry.getExtension().equalsIgnoreCase("BAM") && cbExtractFramesBAM.isSelected()) {
        extractBamFrames(entry, output);
      }
      else if ((entry.getExtension().equalsIgnoreCase("BAM") ||
                entry.getExtension().equalsIgnoreCase("MOS")) && cbDecompress.isSelected()) {
        decompressBamMos(entry, output);
      }
      else if (entry.getExtension().equalsIgnoreCase("CHR") && cbConvertCRE.isSelected()) {
        chrToCre(entry, output);
      }
      else if (entry.getExtension().equalsIgnoreCase("WAV") && cbConvertWAV.isSelected()) {
        decompressWav(entry, output);
      }
      else if (entry.getExtension().equalsIgnoreCase("MVE") && cbExportMVEasAVI.isSelected()) {
        output = outputPath.resolve(StreamUtils.replaceFileExtension(entry.getResourceName(), "avi"));
        if (Files.exists(output) && !cbOverwrite.isSelected()) {
          return;
        }
        MveResource.convertAvi(entry, output, null, true);
      }
      else {
        exportResource(entry, output);
      }
    } catch (Exception e) {
      System.err.println("Error in resource: " + entry.toString());
      e.printStackTrace();
    }
  }

  // Attempts to open "output" as stream to the specified file "numAttempts' time with "delayAttempts" ms delay inbetween.
  private OutputStream tryOpenOutputStream(Path output, int numAttempts, int delayAttempts) throws Exception
  {
    if (output != null) {
      numAttempts = Math.max(1, numAttempts);
      delayAttempts = Math.max(0, delayAttempts);
      OutputStream os = null;
      while (os == null) {
        try {
          os = StreamUtils.getOutputStream(output, true);
        } catch (FileNotFoundException fnfe) {
          os = null;
          if (--numAttempts == 0) {
            throw fnfe;
          }
          try { Thread.sleep(delayAttempts); } catch (InterruptedException ie) {}
        }
      }
      return os;
    }
    return null;
  }

  // Attempts to open "output" as writer to the specified file "numAttempts' time with "delayAttempts" ms delay inbetween.
  private Writer tryOpenOutputWriter(Path output, int numAttempts, int delayAttempts) throws Exception
  {
    if (output != null) {
      numAttempts = Math.max(1, numAttempts);
      delayAttempts = Math.max(0, delayAttempts);
      Writer w = null;
      while (w == null) {
        try {
          w = Files.newBufferedWriter(output);
        } catch (FileNotFoundException fnfe) {
          w = null;
          if (--numAttempts == 0) {
            throw fnfe;
          }
          try { Thread.sleep(delayAttempts); } catch (InterruptedException ie) {}
        }
      }
      return w;
    }
    return null;
  }


//-------------------------- INNER CLASSES --------------------------

  private class Worker implements Runnable
  {
    private final ResourceEntry entry;

    public Worker(ResourceEntry entry)
    {
      this.entry = entry;
    }

    @Override
    public void run()
    {
      if (entry != null) {
        export(entry);
      }
      advanceProgress(false);
    }
  }
}
