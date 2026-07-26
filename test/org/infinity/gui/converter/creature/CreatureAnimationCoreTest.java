// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;

import org.infinity.gui.converter.creature.CreatureAnimationExporter.Config;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.ExportResult;
import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamV1Decoder.BamV1Control;
import org.infinity.resource.key.FileResourceEntry;

/** Dependency-free regression tests for the creature animation creator core. */
public final class CreatureAnimationCoreTest {
  private CreatureAnimationCoreTest() {
  }

  public static void main(String[] args) throws Exception {
    testEnhancedEditionSlotCoverage();
    testDescriptionAndGeneration();
    testPngRoundTrip();
    testFalseColorPaletteRoundTrip();
    testBamV1Export();
    testSplitBamV1Export();
    testBamV2Export();
    System.out.println("CreatureAnimationCoreTest: all checks passed");
  }

  private static void testEnhancedEditionSlotCoverage() {
    for (final Profile.Game game : MonsterAnimationLayout.SUPPORTED_GAMES) {
      check(MonsterAnimationLayout.isValidSlot(game, 0x7303), game + " should accept modern monster slot 0x7303");
      check(!MonsterAnimationLayout.isValidSlot(game, 0x7000), game + " should reject monster_old slot 0x7000");
    }
    check(!MonsterAnimationLayout.isSupportedGame(Profile.Game.BG2ToB), "Classic BG2 must not be accepted");
  }

  private static void testDescriptionAndGeneration() {
    final ProceduralCreatureGenerator.Description description =
        ProceduralCreatureGenerator.parseDescription("huge red and gold winged horned dragon", 42L);
    check(description.getArchetype() == ProceduralCreatureGenerator.Archetype.QUADRUPED,
        "Dragon description should select the quadruped body plan");
    check(description.getTraits().contains(ProceduralCreatureGenerator.Trait.WINGS), "Wings should be parsed");
    check(description.getTraits().contains(ProceduralCreatureGenerator.Trait.HORNS), "Horns should be parsed");
    check(description.getScale() > 1.0, "Huge should increase creature scale");

    final CreatureAnimationModel model =
        ProceduralCreatureGenerator.generate("small blue armored spider", 1234L, null);
    check(model.getPopulatedCellCount() == Sequence.values().length * Direction.values().length,
        "Procedural generator must fill every action/direction cell");
    check(model.getFrameCount() > 500, "Procedural generator should create a complete animation family");
    final BufferedImage image = model.getFrames(Sequence.WALK, Direction.S).get(0).getImage();
    check(image.getWidth() == ProceduralCreatureGenerator.FRAME_SIZE, "Generated frame width should be stable");
    check(hasVisiblePixel(image), "Generated frame should contain actual drawing data");
  }

  private static void testPngRoundTrip() throws Exception {
    final Path directory = createTestDirectory("ni-creature-png-test-");
    try {
      final CreatureAnimationModel model = createMinimalModel();
      final int exported = CreatureAnimationImporter.exportDirectory(model, directory, false);
      check(exported == 1, "One PNG frame should be exported");
      final CreatureAnimationImporter.ImportResult imported = CreatureAnimationImporter.importDirectory(directory);
      check(imported.getFrameCount() == 1, "One PNG frame should be imported");
      final Point center = imported.getModel().getFrames(Sequence.STANCE, Direction.S).get(0).getCenter();
      check(center.equals(new Point(8, 14)), "centers.csv should preserve the BAM center");
    } finally {
      deleteTree(directory);
    }
  }

  private static void testBamV1Export() throws Exception {
    final Path directory = createTestDirectory("ni-creature-bam1-test-");
    try {
      final Config config = createConfig(directory).setBamFormat(BamFormat.BAM_V1).setCompressedBam(false);
      final ExportResult result = CreatureAnimationExporter.export(createMinimalModel(), config, false);
      check(result.getInstalledFiles().size() == 3, "Unsplit BAM V1 export should install INI plus two BAM files");
      check(BamDecoder.getType(new FileResourceEntry(directory.resolve("TST1G1.BAM"))) == BamDecoder.Type.BAMV1,
          "G1 output should be BAM V1");
      check(Files.readAllLines(directory.resolve("7303.INI")).contains("animation_type=7000"),
          "Generated INI should declare animation type 7000");
    } finally {
      deleteTree(directory);
    }
  }

  private static void testFalseColorPaletteRoundTrip() throws Exception {
    final Path directory = createTestDirectory("ni-creature-palette-test-");
    try {
      final Path source = directory.resolve("source");
      final Path game = directory.resolve("game");
      CreatureAnimationImporter.exportDirectory(createPalettedModel(), source, false);
      final CreatureAnimationModel imported = CreatureAnimationImporter.importDirectory(source).getModel();
      check(imported.getFrames(Sequence.STANCE, Direction.S).get(0).getImage().getColorModel()
          instanceof IndexColorModel, "Indexed PNG imports should retain their palette");

      final Config config = createConfig(game).setBamFormat(BamFormat.BAM_V1).setCompressedBam(false)
          .setFalseColor(true);
      check(!CreatureAnimationExporter.validate(imported, config).hasErrors(),
          "A shared indexed palette with transparent index 0 should pass false-color validation");
      CreatureAnimationExporter.export(imported, config, false);

      final BamDecoder decoder = BamDecoder.loadBam(new FileResourceEntry(game.resolve("TST1G1.BAM")));
      try {
        final int[] palette = ((BamV1Control) decoder.createControl()).getPalette();
        check((palette[0] & 0x00ffffff) == 0x0000ff00,
            "False-color BAM should normalize transparent index 0 to engine green");
        check((palette[4] & 0x00ffffff) == 0x00336699 && (palette[16] & 0x00ffffff) == 0x00cc8844,
            "False-color palette indices should survive the PNG and BAM round trip");
      } finally {
        decoder.close();
      }
    } finally {
      deleteTree(directory);
    }
  }

  private static void testBamV2Export() throws Exception {
    final Path directory = createTestDirectory("ni-creature-bam2-test-");
    try {
      final Config config = createConfig(directory).setBamFormat(BamFormat.BAM_V2).setCompressedBam(false);
      final ExportResult result = CreatureAnimationExporter.export(createMinimalModel(), config, false);
      check(result.getInstalledFiles().size() >= 5, "BAM V2 export should include INI, BAM and PVRZ files");
      check(BamDecoder.getType(new FileResourceEntry(directory.resolve("TST1G2.BAM"))) == BamDecoder.Type.BAMV2,
          "G2 output should be BAM V2");
    } finally {
      deleteTree(directory);
    }
  }

  private static void testSplitBamV1Export() throws Exception {
    final Path directory = createTestDirectory("ni-creature-split-test-");
    try {
      final Config config = createConfig(directory).setBamFormat(BamFormat.BAM_V1).setCompressedBam(true)
          .setSplitBams(true);
      final ExportResult result = CreatureAnimationExporter.export(createMinimalModel(), config, false);
      check(result.getInstalledFiles().size() == 14,
          "Split BAM V1 export should install INI plus thirteen BAM files");
      check(BamDecoder.getType(new FileResourceEntry(directory.resolve("TST1G11.BAM"))) == BamDecoder.Type.BAMC,
          "Split compressed output should be BAMC");
      check(Files.readAllLines(directory.resolve("7303.INI")).contains("split_bams=1"),
          "Generated INI should declare split BAM resources");
    } finally {
      deleteTree(directory);
    }
  }

  private static Config createConfig(Path output) {
    return new Config().setGame(Profile.Game.BG2EE).setAnimationId(0x7303).setResref("TST1")
        .setOutputDirectory(output).setSplitBams(false);
  }

  private static CreatureAnimationModel createMinimalModel() {
    final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 3; y < 14; y++) {
      for (int x = 5; x < 11; x++) {
        image.setRGB(x, y, new Color(75 + x * 5, 100 + y * 4, 145, 255).getRGB());
      }
    }
    final CreatureAnimationModel model = new CreatureAnimationModel();
    model.replaceFrames(Sequence.STANCE, Direction.S,
        Collections.singletonList(new AnimationFrame(image, new Point(8, 14), "test")));
    return model;
  }

  private static CreatureAnimationModel createPalettedModel() {
    final int[] palette = new int[256];
    Arrays.fill(palette, 0xff000000);
    palette[0] = 0x00000000;
    palette[4] = 0xff336699;
    palette[16] = 0xffcc8844;
    final IndexColorModel colorModel = new IndexColorModel(8, palette.length, palette, 0, true, 0,
        DataBuffer.TYPE_BYTE);
    final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
    for (int y = 3; y < 14; y++) {
      for (int x = 5; x < 11; x++) {
        image.getRaster().setSample(x, y, 0, ((x + y) & 1) == 0 ? 4 : 16);
      }
    }
    final CreatureAnimationModel model = new CreatureAnimationModel();
    model.replaceFrames(Sequence.STANCE, Direction.S,
        Collections.singletonList(new AnimationFrame(image, new Point(8, 14), "palette-test")));
    return model;
  }

  private static boolean hasVisiblePixel(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (((image.getRGB(x, y) >>> 24) & 0xff) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static Path createTestDirectory(String prefix) throws IOException {
    final Path root = Paths.get("build", "test", "tmp");
    Files.createDirectories(root);
    return Files.createTempDirectory(root, prefix);
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
        if (exception != null) {
          throw exception;
        }
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
