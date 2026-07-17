// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.tis;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.gui.ChildFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.converter.ConvertButtonsPanel;
import org.infinity.gui.converter.ConvertFileListPanel;
import org.infinity.gui.converter.ConvertOutputPanel;
import org.infinity.gui.converter.ConvertOutputPanel.Overwrite;
import org.infinity.gui.converter.PanelUpdateEvent;
import org.infinity.gui.converter.PanelUpdateListener;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.util.io.FileManager;
import org.tinylog.Logger;

/** Dialog for converting multiple PNG files to palette-based or PVRZ-based TIS files. */
public class ConvertToTisBatch extends ChildFrame implements PanelUpdateListener {
  private static final List<FileNameExtensionFilter> INPUT_FILTERS = Arrays.asList(
      new FileNameExtensionFilter("PNG files (*.png)", "png"));

  private static Path currentPath = Profile.getGameRoot();

  private ConvertFileListPanel fileListPanel;
  private ConvertOutputPanel outputPanel;
  private TisBatchOptionsPanel optionsPanel;
  private ConvertButtonsPanel buttonsPanel;

  public ConvertToTisBatch() {
    super("Batch convert to TIS", true);
    init();
  }

  /** Resets dialog content and optionally hides the dialog window. */
  public void hideWindow(boolean hide) {
    reset();
    if (hide) {
      setVisible(false);
    }
  }

  @Override
  protected boolean windowClosing(boolean forced) throws Exception {
    currentPath = outputPanel.getCurrentDirectory();
    reset();
    return super.windowClosing(forced);
  }

  @Override
  public void panelUpdated(PanelUpdateEvent e) {
    if (e.getSource() == buttonsPanel) {
      if (e.getReason() == ConvertButtonsPanel.REASON_CONVERT) {
        convert();
      } else if (e.getReason() == ConvertButtonsPanel.REASON_CANCEL) {
        hideWindow(true);
      }
    }
    updateStatus();
  }

  private void reset() {
    fileListPanel.reset();
    outputPanel.reset();
    optionsPanel.reset();
  }

  private void updateStatus() {
    final boolean hasInput = fileListPanel.getFileCount() > 0;
    final boolean hasOutput = !outputPanel.getOutputFolder().isEmpty();
    buttonsPanel.setConvertButtonEnabled(hasInput && hasOutput);
  }

  private void convert() {
    final List<Path> inputFiles = fileListPanel.getFiles();
    if (inputFiles.isEmpty()) {
      JOptionPane.showMessageDialog(this, "No input files defined.", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    Path outputDir = null;
    try {
      outputDir = FileManager.resolve(outputPanel.getOutputFolder());
    } catch (Exception e) {
      Logger.debug(e);
    }
    if (outputDir == null) {
      JOptionPane.showMessageDialog(this, "Invalid output directory specified.", "Error",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    final Overwrite overwrite = outputPanel.getOverwriteMode();
    final int tileDimension = optionsPanel.getTileDimension();
    final boolean legacy = optionsPanel.isLegacyVersionSelected();
    final boolean closeOnExit = buttonsPanel.isCloseOnExit();

    try {
      new TisBatchWorker(this, inputFiles, outputDir, overwrite, tileDimension, legacy, closeOnExit).execute();
    } catch (Exception e) {
      Logger.error(e);
      JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void init() {
    setIconImage(Icons.ICON_APPLICATION_16.getIcon().getImage());

    fileListPanel = new ConvertFileListPanel("Input", currentPath, INPUT_FILTERS);
    fileListPanel.addPanelUpdateListener(this);

    optionsPanel = new TisBatchOptionsPanel();

    outputPanel = new ConvertOutputPanel("Output", currentPath, optionsPanel);
    outputPanel.addPanelUpdateListener(this);

    buttonsPanel = new ConvertButtonsPanel();
    buttonsPanel.addPanelUpdateListener(this);

    final GridBagConstraints c = new GridBagConstraints();
    final JPanel panelMain = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 0, 0, 0), 0, 0);
    panelMain.add(fileListPanel, c);
    ViewerUtil.setGBC(c, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(8, 0, 0, 0), 0, 0);
    panelMain.add(outputPanel, c);
    ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(8, 0, 0, 0), 0, 0);
    panelMain.add(buttonsPanel, c);

    setLayout(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
        new Insets(8, 8, 8, 8), 0, 0);
    add(panelMain, c);

    updateStatus();
    setPreferredSize(new Dimension(getPreferredSize().width + 50, getPreferredSize().height + 50));
    setMinimumSize(getPreferredSize());
    pack();
    setLocationRelativeTo(getParent());
    setVisible(true);
  }
}
