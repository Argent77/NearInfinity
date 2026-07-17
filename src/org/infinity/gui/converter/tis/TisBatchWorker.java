// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.tis;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import org.infinity.gui.converter.AbstractConvertWorker;
import org.infinity.gui.converter.ConvertOutputPanel.Overwrite;
import org.infinity.gui.converter.WorkerResult;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.StreamUtils;
import org.tinylog.Logger;

/** Worker for converting multiple PNG files to TIS files. */
class TisBatchWorker extends AbstractConvertWorker<WorkerResult> {
  private final ConvertToTisBatch parent;
  private final List<Path> inputFiles;
  private final Path outputDir;
  private final Overwrite overwrite;
  private final int tileDimension;
  private final boolean legacy;
  private final boolean closeOnExit;

  public TisBatchWorker(ConvertToTisBatch parent, List<Path> inputFiles, Path outputDir, Overwrite overwrite,
      int tileDimension, boolean legacy, boolean closeOnExit) {
    super(parent, true, true, 0, inputFiles.size(), "Converting to TIS...", 250, 1000);
    this.parent = Objects.requireNonNull(parent);
    this.inputFiles = Objects.requireNonNull(inputFiles);
    this.outputDir = Objects.requireNonNull(outputDir);
    this.overwrite = Objects.requireNonNull(overwrite);
    this.tileDimension = tileDimension;
    this.legacy = legacy;
    this.closeOnExit = closeOnExit;
    setProgressNote("Preparing");
  }

  @Override
  protected WorkerResult doInBackground() throws Exception {
    int failed = 0;
    int skipped = 0;
    final List<String> errors = new ArrayList<>();
    final Set<Path> outputFiles = new HashSet<>();
    final Set<String> pvrzBaseNames = new HashSet<>();

    for (int i = 0, count = inputFiles.size(); i < count; i++) {
      if (isProgressCancelled()) {
        cancel(false);
        return null;
      }

      setProgressNote("File " + (i + 1) + " / " + count);
      advanceProgressTo(i);

      try {
        final Path inputFile = inputFiles.get(i);
        final Path outputFile = outputDir.resolve(
            StreamUtils.replaceFileExtension(inputFile.getFileName().toString(), "TIS"));
        final Path validOutputFile = ConvertToTis.createValidTisPath(outputFile, legacy);
        if (!outputFile.getFileName().toString().equalsIgnoreCase(validOutputFile.getFileName().toString())) {
          throw new IllegalArgumentException("Invalid TIS filename for the selected format: "
              + outputFile.getFileName());
        }
        if (!outputFiles.add(outputFile.toAbsolutePath().normalize())) {
          throw new IllegalArgumentException("Multiple input files resolve to the same output file: "
              + outputFile.getFileName());
        }
        if (!legacy) {
          final String fileName = outputFile.getFileName().toString();
          final int extensionPos = fileName.lastIndexOf('.');
          final String resourceName = (extensionPos >= 0) ? fileName.substring(0, extensionPos) : fileName;
          final String pvrzBaseName = (resourceName.charAt(0) + resourceName.substring(2)).toUpperCase(Locale.ENGLISH);
          if (!pvrzBaseNames.add(pvrzBaseName)) {
            throw new IllegalArgumentException("Multiple TIS files resolve to the same PVRZ page names: "
                + outputFile.getFileName());
          }
        }

        if (FileEx.create(outputFile).exists()) {
          switch (overwrite) {
            case ASK:
            {
              final String msg = "File " + outputFile + " already exists. Overwrite?";
              final int result = JOptionPane.showConfirmDialog(parent, msg, "Overwrite",
                  JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
              if (result == JOptionPane.NO_OPTION) {
                skipped++;
                continue;
              } else if (result == JOptionPane.CANCEL_OPTION) {
                cancel(false);
                return null;
              }
              break;
            }
            case SKIP:
              skipped++;
              continue;
            default:
          }
        }

        final BufferedImage image = ColorConvert.toBufferedImage(ImageIO.read(inputFile.toFile()), true);
        if (image == null) {
          throw new Exception("Unable to load source image: " + inputFile);
        }
        if ((image.getWidth() % tileDimension) != 0 || (image.getHeight() % tileDimension) != 0) {
          throw new IllegalArgumentException("Image dimensions are not multiples of " + tileDimension + ": "
              + inputFile.getFileName());
        }

        final int tileCount = (image.getWidth() / tileDimension) * (image.getHeight() / tileDimension);
        if (legacy) {
          ConvertToTis.convertV1(image, outputFile, tileCount, tileDimension, 0, null);
        } else {
          ConvertToTis.convertV2(image, outputFile, tileCount, tileDimension, 0, null);
        }
      } catch (Exception e) {
        failed++;
        errors.add(inputFiles.get(i).getFileName() + ": "
            + ((e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName()));
        Logger.debug(e);
      }
    }

    final StringBuilder message = new StringBuilder();
    if (failed == 0) {
      message.append("Conversion finished successfully.");
    } else {
      message.append("Conversion finished with ").append(failed).append(" error(s).");
    }
    if (skipped > 0) {
      message.append('\n').append(skipped).append(" file(s) skipped.");
    }
    if (!errors.isEmpty()) {
      message.append("\n\nErrors:");
      for (int i = 0, count = Math.min(5, errors.size()); i < count; i++) {
        message.append("\n- ").append(errors.get(i));
      }
      if (errors.size() > 5) {
        message.append("\n- ... and ").append(errors.size() - 5).append(" more");
      }
    }
    return new WorkerResult(failed == 0, message.toString());
  }

  @Override
  protected void onCompleted(WorkerResult result) {
    final String message = (result != null) ? result.getMessage() : "Conversion finished successfully.";
    final int messageType = result != null && result.isSuccess()
        ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE;
    JOptionPane.showMessageDialog(parent, message, "Batch TIS conversion", messageType);
    parent.hideWindow(closeOnExit);
  }

  @Override
  protected void onCancelled() {
    JOptionPane.showMessageDialog(parent, "Conversion cancelled by the user.", "Batch TIS conversion",
        JOptionPane.INFORMATION_MESSAGE);
    parent.hideWindow(closeOnExit);
  }
}
