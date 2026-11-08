// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.cli;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.GifSequenceReader;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamControl;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Converts GIF source images and cycle definitions from JSON into a compressed BAM v1 file.
 */
public final class ImageSequenceToBam implements CommandLineTool {
  private static final int PALETTE_SIZE = 256;
  private static final int TRANSPARENT_GREEN = 0xff00ff00;

  public ImageSequenceToBam() {
  }

  public void run(String fileName) throws Exception {
    JSONObject input = new JSONObject(new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8"));
    convert(input, Paths.get("").toAbsolutePath());
  }

  private static void convert(JSONObject input, Path workingDirectory) throws Exception {
    JSONArray sources = input.optJSONArray("sources");
    JSONArray cycles = input.optJSONArray("cycles");
    String outputName = input.optString("output", "");
    if (sources == null || sources.length() == 0) {
      throw new IOException("The 'sources' array must contain at least one GIF.");
    }
    if (cycles == null || cycles.length() == 0) {
      throw new IOException("The 'cycles' array must contain at least one cycle.");
    }
    if (outputName.trim().isEmpty()) {
      throw new IOException("The 'output' path is required.");
    }

    List<BufferedImage> images = new ArrayList<>(sources.length());
    List<Point> centers = new ArrayList<>(sources.length());
    for (int i = 0; i < sources.length(); i++) {
      JSONObject source = sources.optJSONObject(i);
      if (source == null) {
        throw new IOException("Source " + i + " must be a JSON object.");
      }
      Path file = resolvePath(workingDirectory, source.optString("file", ""));
      if (!Files.isRegularFile(file)) {
        throw new IOException("Source file does not exist: " + file);
      }
      images.add(readGifFrame(file, source.optInt("frame", 0)));
      centers.add(new Point(source.optInt("centerX", 0), source.optInt("centerY", 0)));
    }

    int[] palette = createPalette(images);
    PseudoBamDecoder decoder = new PseudoBamDecoder();
    for (int i = 0; i < images.size(); i++) {
      decoder.frameAdd(toIndexed(images.get(i), palette), centers.get(i));
    }

    PseudoBamControl control = decoder.createControl();
    for (int i = 0; i < cycles.length(); i++) {
      JSONArray cycle = cycles.optJSONArray(i);
      if (cycle == null || cycle.length() == 0) {
        throw new IOException("Cycle " + i + " must contain at least one frame index.");
      }
      int[] indices = new int[cycle.length()];
      for (int j = 0; j < cycle.length(); j++) {
        indices[j] = cycle.getInt(j);
        if (indices[j] < 0 || indices[j] >= images.size()) {
          throw new IOException("Cycle " + i + " references invalid frame index " + indices[j] + ".");
        }
      }
      control.cycleAdd(indices);
    }

    decoder.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, Boolean.TRUE);
    decoder.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX, Integer.valueOf(0));
    Path output = resolvePath(workingDirectory, outputName);
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (!decoder.exportBamV1(output, null, 0)) {
      throw new IOException("No BAM data was produced.");
    }
  }

  private static BufferedImage readGifFrame(Path file, int frameIndex) throws Exception {
    if (frameIndex < 0) {
      throw new IOException("GIF frame index must not be negative: " + file);
    }
    try (InputStream input = Files.newInputStream(file);
        ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
      if (imageInput == null) {
        throw new IOException("Unable to open GIF: " + file);
      }
      GifSequenceReader reader = new GifSequenceReader(imageInput);
      reader.decodeAll();
      if (frameIndex >= reader.getFrameCount()) {
        throw new IOException("GIF frame index " + frameIndex + " is out of range: " + file);
      }
      return reader.getFrame(frameIndex).getRenderedImage();
    }
  }

  private static int[] createPalette(List<BufferedImage> images) {
    int pixelCount = 0;
    for (BufferedImage image : images) {
      pixelCount += image.getWidth() * image.getHeight();
    }
    int[] pixels = new int[pixelCount];
    int offset = 0;
    for (BufferedImage image : images) {
      image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, offset, image.getWidth());
      offset += image.getWidth() * image.getHeight();
    }
    int[] palette = ColorConvert.medianCut(pixels, PALETTE_SIZE - 1, false);
    if (palette == null) {
      throw new IllegalArgumentException("Unable to create a BAM palette.");
    }
    int[] result = new int[PALETTE_SIZE];
    result[0] = TRANSPARENT_GREEN;
    System.arraycopy(palette, 0, result, 1, palette.length);
    return result;
  }

  private static BufferedImage toIndexed(BufferedImage source, int[] palette) {
    IndexColorModel colorModel = new IndexColorModel(8, PALETTE_SIZE, palette, 0, false, 0,
        DataBuffer.TYPE_BYTE);
    BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_INDEXED,
        colorModel);
    byte[] pixels = ((DataBufferByte) result.getRaster().getDataBuffer()).getData();
    int[] sourcePixels = source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth());
    for (int i = 0; i < sourcePixels.length; i++) {
      if ((sourcePixels[i] >>> 24) < 128) {
        pixels[i] = 0;
      } else {
        pixels[i] = (byte) (ColorConvert.getNearestColor(sourcePixels[i], palette, 0.0, null, true) & 0xff);
      }
    }
    return result;
  }

  private static Path resolvePath(Path workingDirectory, String value) throws IOException {
    if (value == null || value.trim().isEmpty()) {
      throw new IOException("A file path must not be empty.");
    }
    Path path = Paths.get(value);
    return path.isAbsolute() ? path : workingDirectory.resolve(path).normalize();
  }
}
