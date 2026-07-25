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
import java.nio.file.Path;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.infinity.gui.ViewerUtil;
import org.infinity.resource.Profile;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;

/**
 * A panel that handles output files and related options for conversion operations.
 */
public class ConvertOutputPanel extends AbstractConvertPanel implements ActionListener, DocumentListener {
  /** Available file "Overwrite" modes. */
  public enum Overwrite {
    /** User is asked to confirm an overwrite. */
    ASK("Ask"),
    /** Existing files are overwritten without further confirmation. */
    REPLACE("Replace"),
    /** Existing files are skipped without further confirmation. */
    SKIP("Skip"),
    ;

    private final String label;

    Overwrite(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /** Output directory path string has changed. */
  public static final UpdateReason REASON_OUTPUT_CHANGED = new UpdateReason(1);

  private final Component optionsPanel;

  private JTextField tfOutput;
  private JButton bOutput;
  private JComboBox<Overwrite> cbOverwrite;
  private Path currentDir;

  /**
   * Initializes a new conversion output panel.
   *
   * @param title        Title string that is used for the border around the panel.
   * @param initialDir   Initial directory that is used by the open folder dialog when specifying a new output
   *                       directory.
   * @param optionsPanel A custom options panel that is added in the bottom-right section of the panel. Specify
   *                       {@code null} if no further customizations are needed.
   */
  public ConvertOutputPanel(String title, Path initialDir, Component optionsPanel) {
    this(title, initialDir, optionsPanel, null);
  }

  /**
   * Initializes a new conversion output panel.
   *
   * @param title        Title string that is used for the border around the panel.
   * @param initialDir   Initial directory that is used by the open folder dialog when specifying a new output
   *                       directory.
   * @param optionsPanel A custom options panel that is added in the bottom-right section of the panel. Specify
   *                       {@code null} if no further customizations are needed.
   * @param overwrite    Specifies which overwrite mode should be initially selected.
   */
  public ConvertOutputPanel(String title, Path initialDir, Component optionsPanel, Overwrite overwrite) {
    super(new GridBagLayout(), title);
    this.optionsPanel = optionsPanel;
    init();
    setOverwriteMode(overwrite);
    setCurrentDirectory(initialDir);
  }

  /** Returns the currently selected {@link Overwrite} mode. */
  public Overwrite getOverwriteMode() {
    return cbOverwrite.getModel().getElementAt(cbOverwrite.getSelectedIndex());
  }

  /** Sets a new {@link Overwrite} mode. Specify {@code null} to set the default overwrite mode. */
  public void setOverwriteMode(Overwrite overwrite) {
    if (overwrite == null) {
      overwrite = Overwrite.REPLACE;
    }
    cbOverwrite.setSelectedItem(overwrite);
  }

  /** Returns the current output folder string. */
  public String getOutputFolder() {
    return tfOutput.getText();
  }

  /**
   * Sets a new output folder string. Invalid directory paths are not assigned.
   *
   * @param path Directory path string that should be assigned to the output folder field.
   */
  public void setOutputFolder(String path) {
    if (path == null || path.isEmpty()) {
      if (!tfOutput.getText().isEmpty()) {
        tfOutput.setText("");
      }
      return;
    }

    Path dir = FileManager.resolve(path);
    if (dir == null) {
      return;
    }

    if (!FileEx.create(dir).isDirectory()) {
      dir = dir.getParent();
      if (!FileEx.create(dir).isDirectory()) {
        return;
      }
    }

    final String text = dir.toString();
    if (!text.equals(tfOutput.getText())) {
      tfOutput.setText(text);
      currentDir = dir;
    }
  }

  /**
   * Returns the options panel that was passed to the constructor. Returns {@code null} if no custom options panel was
   * specified.
   */
  public Component getOptionsPanel() {
    return optionsPanel;
  }

  /** Returns the initial working directory used for open file or folder dialogs. */
  public Path getCurrentDirectory() {
    return currentDir;
  }

  /**
   * Sets a new initial working directory that is used for open file or folder dialogs. It is overridden by paths
   * defined in the output directory field.
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

  /** Resets the panel to its initial state. */
  public void reset() {
    setOutputFolder("");
    setOverwriteMode(null);
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == bOutput) {
      onOutput();
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface DocumentListener ---------------------

  @Override
  public void insertUpdate(DocumentEvent e) {
    firePanelUpdate(REASON_OUTPUT_CHANGED);
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    firePanelUpdate(REASON_OUTPUT_CHANGED);
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    firePanelUpdate(REASON_OUTPUT_CHANGED);
  }

  // --------------------- End Interface DocumentListener ---------------------

  /** Assigns a new output directory path interactively. */
  private void onOutput() {
    Path initialDir = null;

    final String text = tfOutput.getText();
    if (!text.isEmpty()) {
      initialDir = FileManager.resolve(text);
    }

    if (initialDir == null) {
      initialDir = currentDir;
    }

    final Path folder = getOpenDirectory(this, "Choose Output Directory", initialDir);

    if (folder != null) {
      setOutputFolder(folder.toString());
    }
  }

  private void init() {
    final JLabel lOutput = new JLabel("Directory:");
    tfOutput = new JTextField();
    tfOutput.getDocument().addDocumentListener(this);
    bOutput = new JButton("...");
    bOutput.addActionListener(this);
    JLabel lOverwrite = new JLabel("Overwrite:");

    cbOverwrite = new JComboBox<>(Overwrite.values());

    final Component options = (optionsPanel != null) ? optionsPanel : new JPanel();

    final GridBagConstraints c = new GridBagConstraints();

    final JPanel panelOutput = new JPanel(new GridBagLayout());
    ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    panelOutput.add(lOutput, c);
    ViewerUtil.setGBC(c, 1, 0, 2, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 0, 0), 0, 0);
    panelOutput.add(tfOutput, c);
    ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    panelOutput.add(bOutput, c);

    ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(8, 0, 0, 0), 0, 0);
    panelOutput.add(lOverwrite, c);
    ViewerUtil.setGBC(c, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NONE,
        new Insets(8, 8, 0, 0), 0, 0);
    panelOutput.add(cbOverwrite, c);
    ViewerUtil.setGBC(c, 2, 1, 2, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 16, 0, 0), 0, 0);
    panelOutput.add(options, c);

    ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 4, 4, 4), 0, 0);
    add(panelOutput, c);

    setOverwriteMode(null);
  }
}
