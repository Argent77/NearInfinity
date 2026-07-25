// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.NearInfinity;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.TextResource;
import org.infinity.resource.bcs.BcsResource;
import org.infinity.resource.bcs.Decompiler;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.dlg.DlgResource;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamResource;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.Compressor;
import org.infinity.resource.graphics.MosDecoder;
import org.infinity.resource.graphics.MosV1Decoder;
import org.infinity.resource.graphics.PvrDecoder;
import org.infinity.resource.graphics.TisConvert;
import org.infinity.resource.graphics.TisConvert.Config;
import org.infinity.resource.graphics.TisDecoder;
import org.infinity.resource.graphics.TisResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.sound.AudioFactory;
import org.infinity.resource.text.PlainTextResource;
import org.infinity.resource.ui.CheckBoxResourceCellRenderer;
import org.infinity.resource.ui.ResourceCellRenderer;
import org.infinity.resource.video.MveResource;
import org.infinity.util.DebugTimer;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.StaticSimpleXorDecryptor;
import org.infinity.util.Threading;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;
import org.infinity.util.tuples.Couple;

public final class MassExporter extends ChildFrame implements ActionListener, ListSelectionListener, DocumentListener, Runnable {
  private static final String FMT_PROGRESS = "Processing resource %d/%d";
  private static final String LABEL_EXPORT_DEFAULT  = "Export";
  private static final String LABEL_EXPORT_FILTER   = "Export...";

  private static final Set<String> TYPES_BLACKLIST = new HashSet<>(Arrays.asList("BIK", "LOG", "SAV"));

  private final JButton bPreview = new JButton("Preview", Icons.ICON_ZOOM_16.getIcon());
  private final JButton bExport = new JButton(LABEL_EXPORT_DEFAULT, Icons.ICON_EXPORT_16.getIcon());
  private final JButton bCancel = new JButton("Cancel", Icons.ICON_DELETE_16.getIcon());
  private final JButton bDirectory = new JButton(Icons.ICON_OPEN_16.getIcon());
  private final JCheckBox cbPattern = new JCheckBox("Use regular expressions", false);
  private final JLabel lPatternHelp = ViewerUtil.createRegexpHelpLabel();
  private final JCheckBox cbIncludeExtraDirs = new JCheckBox("Include extra folders", true);
  private final JCheckBox cbDecompile = new JCheckBox("Decompile scripts and dialogs", true);
  private final JCheckBox cbDecrypt = new JCheckBox("Decrypt text files", true);
  private final JCheckBox cbConvertWAV = new JCheckBox("Convert sounds", true);
  private final JCheckBox cbConvertCRE = new JCheckBox("Convert CHR=>CRE", false);
  private final JCheckBox cbDecompress = new JCheckBox("Decompress BAM/MOS", false);
  private final JCheckBox cbConvertToPNG = new JCheckBox("Export MOS/PVRZ/TIS as PNG", false);
  private final JCheckBox cbConvertTisVersion = new JCheckBox("Convert TIS to ", false);
  private final JCheckBox cbTrimText = new JCheckBox("Trim spaces in text files", false);
  private final JCheckBox cbFormatAlign = new JCheckBox("Align 2DA table data:", false);
  private final JComboBox<String> cbConvertTisList = new JComboBox<>(new String[] { "Palette-based", "PVRZ-based" });
  private final JComboBox<String> cbFormatAlignList = new JComboBox<>(new String[] { "Compact", "Uniform" });
  private final JCheckBox cbExtractFramesBAM = new JCheckBox("Export BAM frames as ", false);
  private final JCheckBox cbExportMVEasAVI = new JCheckBox("Export MVE as AVI", false);
  private final JCheckBox cbOverwrite = new JCheckBox("Overwrite existing files", false);
  private final JCheckBox cbCloseDialogOnExport = new JCheckBox("Close dialog after export", true);
  private final JCheckBox cbPreselectFilter = new JCheckBox("Preselect exported files", false);
  private final JFileChooser fc = new JFileChooser(Profile.getGameRoot().toFile());
  private final JComboBox<String> cbExtractFramesBAMFormat = new JComboBox<>(new String[] { "PNG", "BMP" });
  private final JList<String> listTypes = new JList<>(getAvailableResourceTypes());
  private final JTextField tfDirectory = new JTextField(16);
  private final JTextField tfPattern = new JTextField(16);

  private Path outputPath;
  private List<String> selectedTypes;
  private ProgressMonitor progress;
  private int progressIndex;
  private List<ResourceEntry> selectedFiles;
  private Pattern pattern;

  public MassExporter() {
    super("Mass Exporter", true);
    init();
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == tfPattern) {
      if (tfPattern.getText().isEmpty()) {
        final Color bg = UIManager.getColor("TextField.background");
        tfPattern.setBackground(bg.darker());
      } else {
        tfPattern.setBackground(UIManager.getColor("TextField.background"));
      }
    } else if (event.getSource() == bPreview) {
      selectedTypes = listTypes.getSelectedValuesList();
      try {
        pattern = getPatternInteractive();
      } catch (IllegalArgumentException e) {
        Logger.error(e);
        return;
      }
      showPreview();
    } else if (event.getSource() == bExport) {
      selectedTypes = listTypes.getSelectedValuesList();
      outputPath = FileManager.resolve(tfDirectory.getText());
      try {
        pattern = getPatternInteractive();
      } catch (IllegalArgumentException e) {
        Logger.error(e);
        return;
      }

      try {
        Files.createDirectories(outputPath);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(this, "Unable to create target directory.", "Error", JOptionPane.ERROR_MESSAGE);
        Logger.error(e);
        return;
      }

      if (cbCloseDialogOnExport.isSelected()) {
        setVisible(false);
      }

      new Thread(this).start();
    } else if (event.getSource() == bCancel) {
      setVisible(false);
    } else if (event.getSource() == bDirectory) {
      if (fc.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION) {
        tfDirectory.setText(fc.getSelectedFile().toString());
      }
      final boolean exportEnabled = listTypes.getSelectedIndices().length > 0 && !tfDirectory.getText().isEmpty();
      bPreview.setEnabled(exportEnabled);
      bExport.setEnabled(exportEnabled);
    } else if (event.getSource() == cbPreselectFilter) {
      final String label = cbPreselectFilter.isSelected() ? LABEL_EXPORT_FILTER : LABEL_EXPORT_DEFAULT;
      bExport.setText(label);
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ListSelectionListener ---------------------

  @Override
  public void valueChanged(ListSelectionEvent event) {
    final boolean exportEnabled = listTypes.getSelectedIndices().length > 0 && !tfDirectory.getText().isEmpty();
    bPreview.setEnabled(exportEnabled);
    bExport.setEnabled(exportEnabled);
  }

  // --------------------- End Interface ListSelectionListener ---------------------

  // --------------------- Begin Interface DocumentListener ---------------------

  @Override
  public void insertUpdate(DocumentEvent e) {
    updateTextFieldColor(tfPattern);
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    updateTextFieldColor(tfPattern);
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    updateTextFieldColor(tfPattern);
  }

  // --------------------- End Interface DocumentListener ---------------------

  // --------------------- Begin Interface Runnable ---------------------

  @Override
  public void run() {
    try {
      final Component parentComponent = isVisible() ? this : NearInfinity.getInstance();

      selectedFiles = getSelectedResources(cbPreselectFilter.isSelected());

      if (selectedFiles.isEmpty()) {
        JOptionPane.showMessageDialog(parentComponent, "No files to export.", "Info", JOptionPane.INFORMATION_MESSAGE);
        return;
      }

      if (selectedFiles.get(0) == null) {
        JOptionPane.showMessageDialog(parentComponent, "Operation cancelled.", "Info", JOptionPane.INFORMATION_MESSAGE);
        return;
      }

      // executing multithreaded search
      boolean isCancelled = false;
      try (final Threading threadPool = new Threading()) {
        progress = new ProgressMonitor(parentComponent, "Exporting...",
            String.format(FMT_PROGRESS, getResourceCount(), getResourceCount()), 0, selectedFiles.size());
        progress.setMillisToDecideToPopup(0);
        progress.setMillisToPopup(0);
        progress.setProgress(0);
        progress.setNote(String.format(FMT_PROGRESS, 0, getResourceCount()));
        DebugTimer.getInstance().timerReset();
        for (int i = 0, count = getResourceCount(); i < count; i++) {
          threadPool.submit(new Worker(selectedFiles.get(i)));
          if (progress.isCanceled()) {
            isCancelled = true;
            break;
          }
        }

        // enforcing thread termination if process has been cancelled
        if (isCancelled) {
          threadPool.shutdownNow();
        } else {
          threadPool.shutdown();
        }

        // waiting for pending threads to terminate
        while (!threadPool.isTerminated()) {
          if (!isCancelled && progress.isCanceled()) {
            isCancelled = true;
            threadPool.shutdownNow();
          }
          try {
            threadPool.awaitTermination(10L, TimeUnit.MILLISECONDS);
          } catch (InterruptedException e) {
            Logger.trace(e);
          }
        }
      } catch (Exception e) {
        Logger.trace(e);
      }

      if (isCancelled) {
        JOptionPane.showMessageDialog(parentComponent, "Mass export aborted.", "Info",
            JOptionPane.INFORMATION_MESSAGE);
      } else {
        JOptionPane.showMessageDialog(parentComponent,
            String.format("Mass export completed.\n%d file(s) exported.", selectedFiles.size()), "Info",
            JOptionPane.INFORMATION_MESSAGE);
      }
    } finally {
      advanceProgress(true);
      if (selectedFiles != null) {
        selectedFiles.clear();
      }
      selectedFiles = null;
    }
    Logger.info(DebugTimer.getInstance().getTimerFormatted("Mass export completed"));
  }

  // --------------------- End Interface Runnable ---------------------

  /** Shows a dialog with a list of all game resources that would be exported with the current settings. */
  private void showPreview() {
    // calling indirectly to provide visual feedback if preparation of the preview list takes a while
    SwingUtilities.invokeLater(() -> {
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      try {
        final List<ResourceEntry> resourceList = getSelectedResources(false);
        if (resourceList == null || resourceList.isEmpty()) {
          JOptionPane.showMessageDialog(this, "No files to export found.");
          return;
        }

        final JLabel summaryLabel = new JLabel("Number of resources to export: " + resourceList.size());
        final DefaultListModel<ResourceEntry> model = new DefaultListModel<>();
        for (final ResourceEntry entry : resourceList) {
          model.addElement(entry);
        }

        final JList<ResourceEntry> resources = new JList<>(model);
        resources.setCellRenderer(new ResourceCellRenderer());
        final Font f = Misc.getScaledFont(BrowserMenuBar.getInstance().getOptions().getScriptFont());
        resources.setFont(f.deriveFont(Font.BOLD));
        resources.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        final int numRows = Math.min(16, Math.max(4, resourceList.size() + 1));
        resources.setVisibleRowCount(numRows);

        final JScrollPane scroll = new JScrollPane(resources);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        final JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.add(summaryLabel, BorderLayout.NORTH);
        mainPanel.add(scroll, BorderLayout.CENTER);

        final JOptionPane optionPane = new JOptionPane(mainPanel, JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.DEFAULT_OPTION);
        final JDialog dialog = optionPane.createDialog(this, "Resource List Preview");
        dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setResizable(true);

        // double-clicking entry opens the selected resource in a new window
        resources.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              final ResourceEntry entry = resources.getSelectedValue();
              if (entry != null) {
                new ViewFrame(NearInfinity.getInstance(), ResourceFactory.getResource(entry));
              }
            }
          }
        });

        try {
          dialog.setVisible(true);
        } finally {
          dialog.dispose();
        }
      } finally {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    });
  }

  /** Allows the user to further refine the list of resources for export. */
  private List<ResourceEntry> preselectFilterEntries(List<ResourceEntry> resources) {
    final Function<List<ResourceEntry>, List<ResourceEntry>> fnFilterEntries = resList -> {
      final List<ResourceEntry> result = new ArrayList<>(resList.size());

      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      try {
        if (resList == null || resList.isEmpty()) {
          return null;
        }

        final JLabel label = new JLabel("Resource list (" + resList.size() + " entries)");

        final CheckBoxListModel<ResourceEntry> resourceModel = new CheckBoxListModel<>(resList, true);
        final CheckBoxList<ResourceEntry> resourceList = new CheckBoxList<>(resourceModel);
        resourceList.setCellRenderer(new CheckBoxResourceCellRenderer());
        final Font f = Misc.getScaledFont(BrowserMenuBar.getInstance().getOptions().getScriptFont());
        resourceList.setFont(f.deriveFont(Font.BOLD));
        resourceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final int numRows = Math.min(16, Math.max(4, resList.size() + 1));
        resourceList.setVisibleRowCount(numRows);

        final JScrollPane scroll = new JScrollPane(resourceList);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        // unified item selection operation: b == null indicates to invert current selection state
        final Consumer<Boolean> selectOperation = b -> {
          final int[] indices = resourceList.getSelectedIndices();
          for (final int idx : indices) {
            final boolean state = (b != null) ? b : !resourceModel.isSelected(idx);
            resourceModel.setSelected(idx, state);
          }
        };

        final JButton selectButton = new JButton("Select");
        selectButton.addActionListener(event -> selectOperation.accept(true));
        selectButton.setEnabled(false);

        final JButton unselectButton = new JButton("Unselect");
        unselectButton.addActionListener(event -> selectOperation.accept(false));
        unselectButton.setEnabled(false);

        final JButton invertButton = new JButton("Invert");
        invertButton.addActionListener(event -> selectOperation.accept(null));
        invertButton.setEnabled(false);

        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttonPanel.add(selectButton);
        buttonPanel.add(unselectButton);
        buttonPanel.add(invertButton);

        // controls enabled state of selection buttons
        resourceList.addListSelectionListener(event -> {
          final boolean isSelected = (resourceList.getSelectedIndex() >= 0);
          selectButton.setEnabled(isSelected);
          unselectButton.setEnabled(isSelected);
          invertButton.setEnabled(isSelected);
        });

        final JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.add(label, BorderLayout.NORTH);
        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        final JButton exportButton = new JButton("Export", Icons.ICON_EXPORT_16.getIcon());
        final JButton cancelButton = new JButton("Cancel", Icons.ICON_DELETE_16.getIcon());
        final JOptionPane optionPane = new JOptionPane(mainPanel, JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION, null, new JButton[] { exportButton, cancelButton }, exportButton);

        // dialog button behavior has to be implemented manually
        exportButton.setEnabled(!resourceModel.isEmpty());
        exportButton.addActionListener(e -> optionPane.setValue(e.getSource()));
        cancelButton.addActionListener(e -> optionPane.setValue(e.getSource()));

        // controls enabled state of the Export button
        resourceModel.addListDataListener(new ListDataListener() {
          @Override
          public void intervalRemoved(ListDataEvent e) {}

          @Override
          public void intervalAdded(ListDataEvent e) {}

          @Override
          public void contentsChanged(ListDataEvent e) {
            for (int i = 0, size = resourceModel.size(); i < size; i++) {
              if (resourceModel.isSelected(i)) {
                exportButton.setEnabled(true);
                return;
              }
            }
            exportButton.setEnabled(false);
          }
        });

        final JDialog dialog = optionPane.createDialog(MassExporter.this, "Select resources for export");
        dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setResizable(true);

        try {
          dialog.setVisible(true);
          if (optionPane.getValue() == exportButton) {
            for (final Iterator<Couple<ResourceEntry, Boolean>> iter = resourceModel.iterator(); iter.hasNext();) {
              final Couple<ResourceEntry, Boolean> value = iter.next();
              if (value.getValue1()) {
                result.add(value.getValue0());
              }
            }
          } else {
            // Special "null" entry indicates cancelled operation
            result.add(null);
          }
        } finally {
          dialog.dispose();
        }
      } finally {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }

      return result;
    };

    return Threading.invokeInEventThread(fnFilterEntries, resources, null);
  }

  /**
   * Returns an array with all resource types available for the current game.
   */
  private static String[] getAvailableResourceTypes() {
    return Arrays
        .stream(Profile.getAvailableResourceTypes())
        .filter(s -> !TYPES_BLACKLIST.contains(s))
        .toArray(String[]::new);
  }

  /**
   * Returns the given file extension as-is or to-lowered, depending on current preferences.
   */
  private static String getExtension(String ext) {
    if (ext != null && BrowserMenuBar.getInstance().getOptions().useLowerCasedExport()) {
      return ext.toLowerCase(Locale.ROOT);
    }
    return ext;
  }

  private int getResourceCount() {
    return (selectedFiles != null) ? selectedFiles.size() : 0;
  }

  /**
   * Returns a list of resource entries to export matching the current export settings.
   *
   * @param interactive Specify {@code true} to show a selection dialog where the user can further refine the resource
   *                      selection.
   */
  private List<ResourceEntry> getSelectedResources(boolean interactive) {
    List<ResourceEntry> resourceEntries = new ArrayList<>(1000);

    final List<Path> extraDirs = new ArrayList<>();
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

    for (final String newVar : selectedTypes) {
      if (pattern != null) {
        resourceEntries.addAll(
            ResourceFactory.getResources(newVar, extraDirs)
              .stream()
              .filter(e -> pattern.matcher(e.getResourceRef()).find())
              .collect(Collectors.toList())
            );
      } else {
        resourceEntries.addAll(ResourceFactory.getResources(newVar, extraDirs));
      }
    }

    // open preselection dialog if requested
    if (interactive) {
      resourceEntries = preselectFilterEntries(resourceEntries);
    }

    return resourceEntries;
  }

  /** Returns {@link Pattern} object from the current regular expression pattern. */
  private Pattern getPattern() throws IllegalArgumentException {
    if (!tfPattern.getText().isEmpty()) {
      final String pattern = cbPattern.isSelected() ? tfPattern.getText() : Pattern.quote(tfPattern.getText());
      return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
    return null;
  }

  /**
   * Returns {@link Pattern} object from the current regular expression pattern. Returns {@code null} if no pattern
   * is defined. Throws an {@link IllegalArgumentException} if an error was handled.
   */
  private Pattern getPatternInteractive() throws IllegalArgumentException {
    try {
      return getPattern();
    } catch (IllegalArgumentException e) {
      JOptionPane.showMessageDialog(this, e.getMessage(), "Pattern syntax error", JOptionPane.ERROR_MESSAGE);
      if (e instanceof PatternSyntaxException) {
        final int index = ((PatternSyntaxException)e).getIndex();
        if (index >= 0) {
          tfPattern.setCaretPosition(index);
        } else {
          tfPattern.setCaretPosition(tfPattern.getText().length());
        }
      }
      tfPattern.requestFocusInWindow();
      throw e;
    }
  }

  /**
   * Updates the background color of the specified {@link JTextField} component based on whether it has content.
   *
   * @param tf {@link JTextField} component to update.
   */
  private void updateTextFieldColor(JTextField tf) {
    if (tf != null) {
      Color bg = UIManager.getColor("TextField.background");
      if (tf.getText().isEmpty()) {
        // shaded background if text field is empty
        int lo = 240;
        int hi = 256;
        int bright = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
        if (bright >= 64) {
          bg = new Color(bg.getRed() * lo / hi, bg.getGreen() * lo / hi, bg.getBlue() * lo / hi);
        } else {
          bg = new Color(bg.getRed() * hi / lo, bg.getGreen() * hi / lo, bg.getBlue() * hi / lo);
        }
      }
      tfPattern.setBackground(bg);
    }
  }

  private synchronized void advanceProgress(boolean finished) {
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

  private void exportText(ResourceEntry entry, Class<? extends Resource> resourceType, Path output) throws Exception {
    // preparing source data
    ByteBuffer bb = entry.getResourceBuffer();

    if (cbDecrypt.isSelected()) {
      bb = decryptText(entry, bb);
    }

    if (resourceType.isAssignableFrom(BcsResource.class) && cbDecompile.isSelected()) {
      bb = decompileScript(entry, bb);
      output = output.getParent().resolve(StreamUtils.replaceFileExtension(output.getFileName().toString(), getExtension("BAF")));
    } else {
      if (cbTrimText.isSelected()) {
        bb = trimText(entry, bb);
      }
      if (cbFormatAlign.isSelected() && entry.getExtension().equalsIgnoreCase("2DA")) {
        bb = alignTable(entry, bb);
      }
    }

    // saving data
    // Keep trying. File may be in use by another thread.
    try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
      StreamUtils.writeBytes(os, bb);
    }
  }

  private void decompileDialog(ResourceEntry entry, Path output) throws Exception {
    output = output.getParent().resolve(StreamUtils.replaceFileExtension(output.getFileName().toString(), getExtension("D")));
    final DlgResource dlg = new DlgResource(entry);
    try (PrintWriter writer = new PrintWriter(output.toFile(), BrowserMenuBar.getInstance().getOptions().getSelectedCharset())) {
      if (!dlg.exportDlgAsText(writer)) {
        Logger.error("Failed to decompile: ", entry);
      }
    }
  }

  private void decompressBamMos(ResourceEntry entry, Path output) throws Exception {
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

  private void decompressWav(ResourceEntry entry, Path output) throws Exception {
    ByteBuffer buffer = StreamUtils.getByteBuffer(AudioFactory.convertAudio(entry));
    if (buffer.limit() > 0) {
      // Keep trying. File may be in use by another thread.
      try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
        StreamUtils.writeBytes(os, buffer);
      }
    }
  }

  private void mosToPng(ResourceEntry entry, Path output) throws Exception {
    if (entry != null && entry.getExtension().equalsIgnoreCase("MOS")) {
      output = outputPath.resolve(StreamUtils.replaceFileExtension(ResourceFactory.getResourceName(entry), getExtension("PNG")));
      if (FileEx.create(output).exists() && !cbOverwrite.isSelected()) {
        return;
      }

      MosDecoder decoder = MosDecoder.loadMos(entry);
      if (decoder != null) {
        if (decoder instanceof MosV1Decoder) {
          ((MosV1Decoder) decoder).setTransparencyEnabled(true);
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

  private void pvrzToPng(ResourceEntry entry, Path output) throws Exception {
    if (entry != null && entry.getExtension().equalsIgnoreCase("PVRZ")) {
      output = outputPath.resolve(StreamUtils.replaceFileExtension(ResourceFactory.getResourceName(entry), getExtension("PNG")));
      if (FileEx.create(output).exists() && !cbOverwrite.isSelected()) {
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

  private void tisToPng(ResourceEntry entry, Path output) throws Exception {
    if (entry != null && entry.getExtension().equalsIgnoreCase("TIS")) {
      output = outputPath.resolve(StreamUtils.replaceFileExtension(ResourceFactory.getResourceName(entry), getExtension("PNG")));
      if (FileEx.create(output).exists() && !cbOverwrite.isSelected()) {
        return;
      }

      TisDecoder decoder = TisDecoder.loadTis(entry);
      if (decoder != null) {
        int tileCount = decoder.getTileCount();
        int tileWidth = decoder.getTileWidth();
        int tileHeight = decoder.getTileHeight();
        int columns = TisConvert.calcTilesetWidth(entry, true, 1);
        int rows = tileCount / columns;
        if ((tileCount % columns) != 0) {
          rows++;
        }

        BufferedImage tile = ColorConvert.createCompatibleImage(tileWidth, tileHeight, Transparency.BITMASK);
        BufferedImage image = ColorConvert.createCompatibleImage(tileWidth * columns, tileHeight * rows,
            Transparency.BITMASK);
        try {
          Graphics2D g = image.createGraphics();
          try {
            for (int i = 0; i < tileCount; i++) {
              int x = tileWidth * (i % columns);
              int y = tileHeight * (i / columns);
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

  private void extractBamFrames(ResourceEntry entry, Path output) throws Exception {
    String format = (cbExtractFramesBAMFormat.getSelectedIndex() == 0) ? "png" : "bmp";
    Path filePath = output.getParent();
    String fileName = output.getFileName().toString();
    int extIdx = fileName.lastIndexOf('.');
    String fileBase = (extIdx >= 0) ? fileName.substring(0, extIdx) : fileName;
    String fileExt = "." + format;

    // creating subfolder for frames
    Path path = filePath.resolve(fileBase);
    if (!FileEx.create(path).exists()) {
      try {
        Files.createDirectory(path);
      } catch (IOException e) {
        String msg = String.format("Error creating folder \"%s\". Skipping file \"%s\".", fileBase, fileName);
        Logger.warn(msg);
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), msg, "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
    } else if (!FileEx.create(path).isDirectory()) {
      String msg = String.format("Folder \"%s\" can not be created. Skipping file \"%s\".", fileBase, fileName);
      Logger.warn(msg);
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), msg, "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }
    filePath = path;

    BamDecoder decoder = BamDecoder.loadBam(entry);
    BamResource.exportFrames(decoder, filePath, fileBase, fileExt, format, true);
  }

  private void chrToCre(ResourceEntry entry, Path output) throws Exception {
    output = outputPath.resolve(StreamUtils.replaceFileExtension(ResourceFactory.getResourceName(entry), getExtension("CRE")));
    if (FileEx.create(output).exists() && !cbOverwrite.isSelected()) {
      return;
    }
    CreResource crefile = new CreResource(entry);
    final List<StructEntry> flatList = crefile.getFlatFields();
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

  private void exportResource(ResourceEntry entry, Path output) throws Exception {
    if (entry != null && output != null) {
      Logger.info("Converting {}", entry.toString());
      int[] info = entry.getResourceInfo();
      int size = info[0];
      if (info.length > 1) {
        size = size * info[1] + 0x18;
      }
      boolean isTis = (info.length > 1);
      boolean isTisV2 = isTis && (info[1] == 0x0c);

      if (isTis && cbConvertTisVersion.isSelected() && !isTisV2 && cbConvertTisList.getSelectedIndex() == 1) {
        final Path tisFile = TisConvert.makeTisFileNameValid(output);
        final TisResource tis = new TisResource(entry);
        final ResourceEntry wedEntry = TisConvert.findWed(entry, true);
        final int tilesPerRow = TisConvert.calcTilesetWidth(wedEntry, false, tis.getDecoder().getTileCount());
        final int pvrzBaseIndex = TisConvert.calcPvrzBaseIndex(tisFile);
        final TisConvert.OverlayConversion convert = (Profile.getEngine() == Profile.Engine.BG2)
            ? TisConvert.OverlayConversion.BG2_TO_BG2EE
            : TisConvert.OverlayConversion.NONE;
        final TisConvert.Config config = Config.createConfigPvrz(tisFile, tis.getDecoder(), wedEntry, tilesPerRow, -1,
            TisConvert.Config.MAX_TEXTURE_SIZE, pvrzBaseIndex, TisConvert.Config.DEFAULT_BORDER_SIZE,
            TisConvert.Config.MAX_TEXTURE_SIZE / 2, true, true, convert);
        TisConvert.convertToPvrzTis(config, false, null);
      } else if (isTis && cbConvertTisVersion.isSelected() && isTisV2 && cbConvertTisList.getSelectedIndex() == 0) {
        TisResource tis = new TisResource(entry);

        // overlay conversion mode depends on game and WED overlay movement type
        final ResourceEntry wedEntry = TisConvert.findWed(entry, true);
        final int movementType = TisConvert.getTisMovementType(wedEntry, false);
        final TisConvert.OverlayConversion convert;
        switch (Profile.getGame()) {
          case BG2EE:
          case IWDEE:
          case PSTEE:
          case EET:
            convert = (movementType == 0) ? TisConvert.OverlayConversion.BG2EE_TO_BG2 : TisConvert.OverlayConversion.NONE;
            break;
          case BG1EE:
            convert = (movementType == 2) ? TisConvert.OverlayConversion.BG2EE_TO_BG2 : TisConvert.OverlayConversion.NONE;
            break;
          default:
            convert = TisConvert.OverlayConversion.NONE;
        }

        final TisConvert.Config config = Config.createConfigPalette(output, tis.getTileList(), tis.getDecoder(),
            wedEntry, convert);
        TisConvert.convertToPaletteTis(config, false, null);
      } else if (size >= 0) {
        try (InputStream is = entry.getResourceDataAsStream()) {
          // Keep trying. File may be in use by another thread.
          try (OutputStream os = tryOpenOutputStream(output, 10, 100)) {
            int bytesWritten = (int) StreamUtils.writeBytes(os, is, size);
            if (bytesWritten < size) {
              throw new EOFException(entry + ": " + bytesWritten + " of " + size + " bytes written");
            }
          }
        }
      }
    }
  }

  private void export(ResourceEntry entry) {
    try {
      Path output = outputPath.resolve(ResourceFactory.getResourceName(entry));
      if (FileEx.create(output).exists() && !cbOverwrite.isSelected()) {
        return;
      }

      Class<? extends Resource> resourceType = ResourceFactory.getResourceType(entry);
      boolean isTextResource = TextResource.class.isAssignableFrom(resourceType);

      if (isTextResource) {
        exportText(entry, resourceType, output);
      } else if (entry.getExtension().equalsIgnoreCase("DLG") && cbDecompile.isSelected()) {
        decompileDialog(entry, output);
      } else if (entry.getExtension().equalsIgnoreCase("MOS") && cbConvertToPNG.isSelected()) {
        mosToPng(entry, output);
      } else if (entry.getExtension().equalsIgnoreCase("PVRZ") && cbConvertToPNG.isSelected()) {
        pvrzToPng(entry, output);
      } else if (entry.getExtension().equalsIgnoreCase("TIS") && cbConvertToPNG.isSelected()) {
        tisToPng(entry, output);
      } else if (entry.getExtension().equalsIgnoreCase("BAM") && cbExtractFramesBAM.isSelected()) {
        extractBamFrames(entry, output);
      } else if ((entry.getExtension().equalsIgnoreCase("BAM") || entry.getExtension().equalsIgnoreCase("MOS"))
          && cbDecompress.isSelected()) {
        decompressBamMos(entry, output);
      } else if (entry.getExtension().equalsIgnoreCase("CHR") && cbConvertCRE.isSelected()) {
        chrToCre(entry, output);
      } else if (entry.getExtension().equalsIgnoreCase("WAV") && cbConvertWAV.isSelected()) {
        decompressWav(entry, output);
      } else if (entry.getExtension().equalsIgnoreCase("MVE") && cbExportMVEasAVI.isSelected()) {
        output = outputPath.resolve(StreamUtils.replaceFileExtension(ResourceFactory.getResourceName(entry), getExtension("AVI")));
        if (FileEx.create(output).exists() && !cbOverwrite.isSelected()) {
          return;
        }
        MveResource.convertAvi(entry, output, null, true);
      } else {
        exportResource(entry, output);
      }
    } catch (Exception e) {
      Logger.error(e, "Error in resource: {}", entry);

    }
  }

  /**
   * Decompiles BCS data from {@code inBuffer}.
   *
   * @param entry The original {@link ResourceEntry} instance.
   * @param inBuffer Buffer containing the current state of the resource data.
   * @return {@link ByteBuffer} instance with decompiled script content.
   */
  private ByteBuffer decompileScript(ResourceEntry entry, ByteBuffer inBuffer) throws Exception {
    if (inBuffer != null) {
      final Decompiler decompiler = new Decompiler(StreamUtils.readString(inBuffer, inBuffer.limit()), false);
      decompiler.setGenerateComments(BrowserMenuBar.getInstance().getOptions().autogenBCSComments());
      String script = decompiler.getSource().replaceAll("\r?\n", Misc.LINE_SEPARATOR);
      final Charset cs = Misc.getCharsetFrom(BrowserMenuBar.getInstance().getOptions().getSelectedCharset());
      return ByteBuffer.wrap(script.getBytes(cs));
    }
    return inBuffer;
  }

  /**
   * Decrypts data from {@code inBuffer}.
   *
   * @param entry The original {@link ResourceEntry} instance.
   * @param inBuffer Buffer containing the current state of the resource data.
   * @return {@link ByteBuffer} instance with decrypted text content.
   */
  private ByteBuffer decryptText(ResourceEntry entry, ByteBuffer inBuffer) {
    if (inBuffer != null && inBuffer.limit() > 1 && inBuffer.getShort(0) == -1) {
      return StaticSimpleXorDecryptor.decrypt(inBuffer, 2);
    }
    return inBuffer;
  }

  /**
   * Removes trailing whitespace from all lines of text in {@code inBuffer}.
   *
   * @param entry The original {@link ResourceEntry} instance.
   * @param inBuffer Buffer containing the current state of the resource data.
   * @return {@link ByteBuffer} instance with trimmed text content.
   */
  private ByteBuffer trimText(ResourceEntry entry, ByteBuffer inBuffer) {
    if (inBuffer != null) {
      String text = Misc.CHARSET_DEFAULT.decode(inBuffer).toString();
      text = PlainTextResource.trimSpaces(text, true, false);
      return ByteBuffer.wrap(text.getBytes(Misc.CHARSET_DEFAULT));
    }
    return inBuffer;
  }

  /**
   * Aligns table data for improved readability.
   *
   * @param entry The original {@link ResourceEntry} instance.
   * @param inBuffer Buffer containing the current state of the resource data.
   * @return {@link ByteBuffer} instance with aligned text content.
   */
  private ByteBuffer alignTable(ResourceEntry entry, ByteBuffer inBuffer) {
    if (inBuffer != null) {
      String text = Misc.CHARSET_DEFAULT.decode(inBuffer).toString();
      if (cbFormatAlignList.getSelectedIndex() == 1) {
        // Uniform alignment
        text = PlainTextResource.alignTableColumns(text, 1, false, 1);
      } else {
        // Compact alignment
        text = PlainTextResource.alignTableColumns(text, 2, true, 4);
      }
      return ByteBuffer.wrap(text.getBytes(Misc.CHARSET_DEFAULT));
    }
    return inBuffer;
  }

//  /**
//   * Sorts IDS entries by key values.
//   *
//   * @param entry The original {@link ResourceEntry} instance.
//   * @param inBuffer Buffer containing the current state of the resource data.
//   * @return {@link ByteBuffer} instance with sorted text content.
//   */
//  private ByteBuffer sortTableText(ResourceEntry entry, ByteBuffer inBuffer) {
//    if (entry != null && inBuffer != null && entry.getExtension().equalsIgnoreCase("IDS")) {
//      String text = Misc.CHARSET_DEFAULT.decode(inBuffer).toString();
//      text = PlainTextResource.sortTable(text, true, entry.getResourceRef().equalsIgnoreCase("TRIGGER"));
//      return ByteBuffer.wrap(text.getBytes(Misc.CHARSET_DEFAULT));
//    }
//    return inBuffer;
//  }

  // Attempts to open "output" as stream to the specified file "numAttempts' time with "delayAttempts" ms delay
  // inbetween.
  private OutputStream tryOpenOutputStream(Path output, int numAttempts, int delayAttempts) throws Exception {
    if (output != null) {
      numAttempts = Math.max(1, numAttempts);
      delayAttempts = Math.max(0, delayAttempts);
      OutputStream os = null;
      while (os == null) {
        try {
          os = StreamUtils.getOutputStream(output, true);
        } catch (FileNotFoundException fnfe) {
          if (--numAttempts == 0) {
            throw fnfe;
          }
          try {
            Thread.sleep(delayAttempts);
          } catch (InterruptedException ie) {
            Logger.trace(ie);
          }
        }
      }
      return os;
    }
    return null;
  }

//  // Attempts to open "output" as writer to the specified file "numAttempts' time with "delayAttempts" ms delay
//  // inbetween.
//  private Writer tryOpenOutputWriter(Path output, int numAttempts, int delayAttempts) throws Exception {
//    if (output != null) {
//      numAttempts = Math.max(1, numAttempts);
//      delayAttempts = Math.max(0, delayAttempts);
//      Writer w = null;
//      while (w == null) {
//        try {
//          w = Files.newBufferedWriter(output);
//        } catch (FileNotFoundException fnfe) {
//          w = null;
//          if (--numAttempts == 0) {
//            throw fnfe;
//          }
//          try {
//            Thread.sleep(delayAttempts);
//          } catch (InterruptedException ie) {
//          }
//        }
//      }
//      return w;
//    }
//    return null;
//  }

  // Initializes the GUI.
  private void init() {
    bPreview.setToolTipText("Preview list of resources to export.");
    bPreview.addActionListener(this);
    bPreview.setEnabled(false);
    bPreview.setMnemonic('p');
    bExport.addActionListener(this);
    bExport.setEnabled(false);
    bExport.setMnemonic('e');
    getRootPane().setDefaultButton(bExport);
    bCancel.addActionListener(this);
    bCancel.setMnemonic('d');
    bDirectory.addActionListener(this);
    tfDirectory.setEditable(false);
    tfPattern.getDocument().addDocumentListener(this);
    updateTextFieldColor(tfPattern);
    listTypes.addListSelectionListener(this);
    fc.setDialogTitle("Mass export: Select directory");
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    cbConvertToPNG.setToolTipText("Caution: Selecting both MOS and TIS may overwrite or skip some files!");
    cbExtractFramesBAM.setToolTipText("Note: Frames of each BAM resource are exported into separate subfolders.");
    cbConvertTisVersion.setToolTipText(
        "Caution: Conversion may take a long time. Files may be renamed to conform to naming scheme for PVRZ-based TIS files.");
    cbIncludeExtraDirs
        .setToolTipText("Include extra folders, such as \"Characters\" or \"Portraits\", except savegames.");
    cbPreselectFilter.addActionListener(this);

    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(new JLabel("File types to export:"), BorderLayout.NORTH);
    leftPanel.add(new JScrollPane(listTypes), BorderLayout.CENTER);

    final JPanel topRightPanel = new JPanel(new BorderLayout());
    topRightPanel.add(new JLabel("Output directory:"), BorderLayout.NORTH);
    topRightPanel.add(tfDirectory, BorderLayout.CENTER);
    topRightPanel.add(bDirectory, BorderLayout.EAST);

    final JPanel patternSubPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    patternSubPanel.add(cbPattern);
    patternSubPanel.add(lPatternHelp);

    final JPanel patternPanel = new JPanel(new BorderLayout());
    final JLabel lPattern = new JLabel("Resource name filter:");
    patternPanel.add(lPattern, BorderLayout.NORTH);
    patternPanel.add(tfPattern, BorderLayout.CENTER);
    patternPanel.add(patternSubPanel, BorderLayout.SOUTH);

    // "Export BAM frames as..." panel
    final JPanel bamFramesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    bamFramesPanel.add(cbExtractFramesBAM);
    bamFramesPanel.add(cbExtractFramesBAMFormat);

    // "Convert TIS to..." panel
    final JPanel tisConvertPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    tisConvertPanel.add(cbConvertTisVersion);
    tisConvertPanel.add(cbConvertTisList);

    // "Align 2DA table data" panel
    final JPanel textAlignPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    textAlignPanel.add(cbFormatAlign);
    textAlignPanel.add(cbFormatAlignList);
    cbFormatAlign.setToolTipText("<html>Align table columns to improve readability:<ul>"
        + "<li>Compact: Column width is calculated individually.</li>"
        + "<li>Uniform: Column width is calculated evenly, comparable to WeiDU's PRETTY_PRINT_2DA.</li>"
        + "</ul></html>");

    final GridBagConstraints c = new GridBagConstraints();

    // Options
    int row = 0;
    final JPanel optionsSubPanel = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    optionsSubPanel.add(cbIncludeExtraDirs, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    optionsSubPanel.add(cbDecrypt, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    optionsSubPanel.add(cbTrimText, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    optionsSubPanel.add(textAlignPanel, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    optionsSubPanel.add(cbOverwrite, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    optionsSubPanel.add(cbPreselectFilter, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, GridBagConstraints.REMAINDER, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    optionsSubPanel.add(cbCloseDialogOnExport, c);

    final JPanel optionsPanel = new JPanel(new GridBagLayout());
    optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    optionsPanel.add(optionsSubPanel, c);

    // Conversions
    row = 0;
    final JPanel conversionSubPanel = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 4, 4);
    conversionSubPanel.add(cbConvertWAV, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    conversionSubPanel.add(cbConvertCRE, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    conversionSubPanel.add(cbDecompile, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    conversionSubPanel.add(cbDecompress, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    conversionSubPanel.add(cbConvertToPNG, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    conversionSubPanel.add(tisConvertPanel, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    conversionSubPanel.add(bamFramesPanel, c);
    row++;
    ViewerUtil.setGBC(c, 0, row, 1, GridBagConstraints.REMAINDER, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
    conversionSubPanel.add(cbExportMVEasAVI, c);

    final JPanel conversionPanel = new JPanel(new GridBagLayout());
    conversionPanel.setBorder(BorderFactory.createTitledBorder("Conversions"));
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    conversionPanel.add(conversionSubPanel, c);

    // combining option panels
    final JPanel bottomRightPanel = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    bottomRightPanel.add(conversionPanel, c);
    ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.BOTH, new Insets(0, 4, 0, 0), 0, 0);
    bottomRightPanel.add(optionsPanel, c);

    // button panel
    final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottomPanel.add(bPreview);
    bottomPanel.add(bExport);
    bottomPanel.add(bCancel);

    final Container contentPane = getContentPane();
    contentPane.setLayout(new GridBagLayout());

    ViewerUtil.setGBC(c, 0, 0, 1, 3, 0.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.BOTH, new Insets(6, 6, 6, 6), 0, 0);
    contentPane.add(leftPanel, c);

    ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(6, 6, 6, 6), 0, 0);
    contentPane.add(topRightPanel, c);

    ViewerUtil.setGBC(c, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(6, 6, 6, 6), 0, 0);
    contentPane.add(patternPanel, c);

    ViewerUtil.setGBC(c, 1, 2, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.BOTH, new Insets(6, 6, 6, 6), 0, 0);
    contentPane.add(bottomRightPanel, c);

    ViewerUtil.setGBC(c, 0, 3, 2, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(6, 6, 6, 6), 0, 0);
    contentPane.add(bottomPanel, c);

    pack();
    setMinimumSize(getPreferredSize());
    Center.center(this, NearInfinity.getInstance().getBounds());
    setVisible(true);
  }

  // -------------------------- INNER CLASSES --------------------------

  private class Worker implements Runnable {
    private final ResourceEntry entry;

    public Worker(ResourceEntry entry) {
      this.entry = entry;
    }

    @Override
    public void run() {
      if (entry != null) {
        export(entry);
      }
      advanceProgress(false);
    }
  }
}
