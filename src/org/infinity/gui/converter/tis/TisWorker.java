// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.tis;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import org.infinity.gui.converter.AbstractConvertWorker;
import org.infinity.gui.converter.WorkerResult;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.util.Logger;

/** Implementation of the {@link AbstractConvertWorker} for the TIS Converter. */
class TisWorker extends AbstractConvertWorker<WorkerResult> {
  private final ConvertToTis parent;
  private final Path inputFile;
  private final Path outputFile;
  private final int tileCount;
  private final int tileDimension;
  private final boolean isLegacy;
  private final boolean closeOnExit;

  public TisWorker(ConvertToTis parent, Path inFile, Path outFile, int tileCount, int tileDimension, boolean isLegacy,
      boolean closeOnExit) {
    super(parent, true, true, 0, 5, "Converting to TIS...", 250, 1000);
    if (tileCount < 1) {
      throw new IllegalArgumentException("Tile count cannot be < 1");
    }
    this.parent = Objects.requireNonNull(parent);
    this.inputFile = Objects.requireNonNull(inFile);
    this.outputFile = Objects.requireNonNull(outFile);
    this.tileCount = tileCount;
    this.tileDimension = tileDimension;
    this.isLegacy = isLegacy;
    this.closeOnExit = closeOnExit;
    setProgressNote("Preparing tileset");
  }

  @Override
  protected WorkerResult doInBackground() throws Exception {
    final String msgSuccess = "Conversion finished successfully.";

    BufferedImage srcImage = null;
    try {
      srcImage = ColorConvert.toBufferedImage(ImageIO.read(inputFile.toFile()), true);
    } catch (Exception e) {
      Logger.trace(e);
    }

    if (srcImage == null) {
      return new WorkerResult(false, "Unable to load source image:\n" + inputFile);
    }

    boolean success = true;
    String message = null;
    try {
      if (isLegacy) {
        success = ConvertToTis.convertV1(srcImage, outputFile, tileCount, tileDimension, 1, this);
      } else {
        success = ConvertToTis.convertV2(srcImage, outputFile, tileCount, tileDimension, 1, this);
      }

      if (success) {
        message = msgSuccess;
      } else {
        // cancelled by the user
        cancel(false);
        return null;
      }
    } catch (Exception e) {
      message = e.getMessage();
      Logger.debug(e);
    }

    return new WorkerResult(success, message);
  }

  @Override
  protected boolean onStarted() {
    if (isLegacy) {
      setMaxProgress(tileCount + 1);  // include preparation stage
    } else {
      // determining number of pvrz files to generate
      final Dimension dim = ColorConvert.getImageDimension(inputFile);
      if (dim == null || dim.width == 0) {
        return false;
      }
      try {
        final int pageCount = ConvertToTis.generatePageInfo(dim.width, dim.height, tileCount, tileDimension, null,
            null);
        setMaxProgress(pageCount + 1);  // include preparation stage
      } catch (Exception e) {
        Logger.error(e);
        return false;
      }
    }
    return true;
  }

  @Override
  protected void onCompleted(WorkerResult result) {
    final String msg = (result != null) ? result.getMessage() : "Conversion finished successfully.";
    if (result.isSuccess()) {
      JOptionPane.showMessageDialog(parent, msg, "Information", JOptionPane.INFORMATION_MESSAGE);
    } else {
      JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
    parent.hideWindow(closeOnExit);
  }

  @Override
  protected void onCancelled() {
    JOptionPane.showMessageDialog(parent, "Conversion cancelled by the user.", "Error", JOptionPane.ERROR_MESSAGE);
    parent.hideWindow(closeOnExit);
  }
}
