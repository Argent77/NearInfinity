// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    testEquipmentPromptAndGeneration();
    testEquipmentReferenceResrefFallback();
    testEquipmentOverlayBamRoundTrip();
    testEquipmentOverlayBamV2RoundTrip();
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

  private static void testEquipmentPromptAndGeneration() {
    final EquipmentOverlayGenerator.PromptSpec prompt = EquipmentOverlayGenerator.parsePrompt(
        "I want an animation similar to the existing SOLAR, but instead of wielding a sword, it should wield "
            + "an ornate silver scythe with a blue glow.");
    check(prompt.getSourceWeapon() == EquipmentOverlayGenerator.WeaponType.SWORD,
        "The first weapon in a replacement prompt should be treated as the source");
    check(prompt.getTargetWeapon() == EquipmentOverlayGenerator.WeaponType.SCYTHE,
        "The last weapon in a replacement prompt should be treated as the target");
    check("SY".equals(prompt.getTargetWeapon().getSuggestedAppearanceCode()),
        "Scythes should receive a stable automatic appearance code");
    check(prompt.isGlowing() && prompt.isOrnate(), "Equipment prompt traits should be parsed");

    final CreatureAnimationModel generated = EquipmentOverlayGenerator.generate(
        createCompleteEquipmentModel(false), createCompleteEquipmentModel(true), prompt, 77L, null);
    check(generated.getPopulatedCellCount() == Sequence.values().length * Direction.values().length,
        "Equipment generation must preserve every synchronized action/direction cell");
    check(generated.getFrameCount() == Sequence.values().length * Direction.values().length,
        "Equipment generation must preserve source frame counts");
    final AnimationFrame frame = generated.getFrames(Sequence.ATTACK_1, Direction.W).get(0);
    check(hasVisiblePixel(frame.getImage()), "Generated scythe frames should contain visible drawing data");
    check(frame.getImage().getWidth() > 20 || frame.getImage().getHeight() > 20,
        "Generated scythe geometry should extend beyond a placeholder-sized frame");
  }

  private static void testEquipmentReferenceResrefFallback() {
    final List<String> resources = Arrays.asList(
        "MSOGG1.BAM", "MSOGG2.BAM",
        "MSOLG1.BAM", "MSOLG2.BAM", "MSOLG11.BAM", "MSOLG1S1.BAM", "MSOLG2S1.BAM",
        "MASLG1.BAM", "MASLG2.BAM", "MASLG1S1.BAM", "MASLG2S1.BAM");
    check(Collections.singletonList("MSOL")
        .equals(EquipmentOverlayReference.findCompatibleEquipmentResrefs("MSOG", resources)),
        "A SOLAR glow-layer resref should resolve to the complete MSOL solid equipment family");

    final List<String> resourcesWithPrimary = new ArrayList<>(resources);
    resourcesWithPrimary.add("MSOGG1S1.BAM");
    resourcesWithPrimary.add("MSOGG2S1.BAM");
    final List<String> compatible =
        EquipmentOverlayReference.findCompatibleEquipmentResrefs("MSOG", resourcesWithPrimary);
    check(!compatible.isEmpty() && "MSOG".equals(compatible.get(0)),
        "The animation's own resref should take precedence when it has a complete equipment pair");
  }

  private static void testEquipmentOverlayBamRoundTrip() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-overlay-test-");
    try {
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt("replace the sword with a large silver sickle");
      final CreatureAnimationModel generated = EquipmentOverlayGenerator.generate(
          createCompleteEquipmentModel(false), createCompleteEquipmentModel(true), prompt, 91L, null);
      final EquipmentOverlayExporter.Config config = new EquipmentOverlayExporter.Config().setResref("MSOL")
          .setAppearanceCode("SK").setOutputDirectory(directory).setBamFormat(BamFormat.BAM_V1)
          .setCompressedBam(true);
      check(!EquipmentOverlayExporter.validate(generated, config).hasErrors(),
          "A complete synchronized overlay should pass validation");
      final EquipmentOverlayExporter.ExportResult result =
          EquipmentOverlayExporter.export(generated, config, false);
      check(result.getInstalledFiles().size() == 2,
          "BAM V1 equipment export should install exactly the G1 and G2 overlay resources");
      final Path group1 = directory.resolve("MSOLG1SK.BAM");
      final Path group2 = directory.resolve("MSOLG2SK.BAM");
      check(BamDecoder.getType(new FileResourceEntry(group1)) == BamDecoder.Type.BAMC,
          "Compressed equipment output should be BAMC");
      final BamDecoder decoder1 = BamDecoder.loadBam(new FileResourceEntry(group1));
      final BamDecoder decoder2 = BamDecoder.loadBam(new FileResourceEntry(group2));
      try {
        check(decoder1.createControl().cycleCount() == 72, "G1 equipment output should contain 72 cycles");
        check(decoder2.createControl().cycleCount() == 63, "G2 equipment output should contain 63 cycles");
      } finally {
        decoder1.close();
        decoder2.close();
      }

      final CreatureAnimationModel imported = MonsterAnimationBamImporter.importUnsplit(
          new FileResourceEntry(group1), new FileResourceEntry(group2));
      check(imported.getPopulatedCellCount() == Sequence.values().length * Direction.values().length,
          "Exported equipment BAMs should import with complete synchronized coverage");
      check(imported.getFrameCount() == generated.getFrameCount(),
          "Equipment BAM round trips should preserve the frame count");
    } finally {
      deleteTree(directory);
    }
  }

  private static void testEquipmentOverlayBamV2RoundTrip() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-overlay-v2-test-");
    try {
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt("replace the sword with a glowing silver scythe");
      final CreatureAnimationModel generated = EquipmentOverlayGenerator.generate(
          createCompleteEquipmentModel(false), createCompleteEquipmentModel(true), prompt, 101L, null);
      final EquipmentOverlayExporter.Config config = new EquipmentOverlayExporter.Config().setResref("MSOL")
          .setAppearanceCode("SY").setOutputDirectory(directory).setBamFormat(BamFormat.BAM_V2)
          .setCompressedBam(false);
      final EquipmentOverlayExporter.ExportResult result =
          EquipmentOverlayExporter.export(generated, config, false);
      check(result.getInstalledFiles().size() > 2,
          "BAM V2 equipment output should include both BAM resources and PVRZ texture pages");
      final Path group1 = directory.resolve("MSOLG1SY.BAM");
      final Path group2 = directory.resolve("MSOLG2SY.BAM");
      check(BamDecoder.getType(new FileResourceEntry(group1)) == BamDecoder.Type.BAMV2,
          "G1 equipment output should be BAM V2");
      check(BamDecoder.getType(new FileResourceEntry(group2)) == BamDecoder.Type.BAMV2,
          "G2 equipment output should be BAM V2");
      boolean hasTexturePage = false;
      for (final Path path : result.getInstalledFiles()) {
        if (path.getFileName().toString().toUpperCase().endsWith(".PVRZ")) {
          hasTexturePage = true;
          break;
        }
      }
      check(hasTexturePage, "BAM V2 equipment output should install at least one PVRZ texture page");
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

  private static CreatureAnimationModel createCompleteEquipmentModel(boolean avatar) {
    final CreatureAnimationModel model = new CreatureAnimationModel();
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        final BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
          graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          if (avatar) {
            graphics.setColor(new Color(85, 105, 155, 255));
            graphics.fillOval(10, 12, 22, 27);
            graphics.setColor(new Color(196, 164, 119, 255));
            graphics.fillOval(17, 18, 9, 9);
          } else {
            final double angle = -1.15 + direction.ordinal() * 0.16 + sequence.ordinal() * 0.01;
            final int gripX = 22;
            final int gripY = 24;
            final int tipX = gripX + (int) Math.round(Math.cos(angle) * 22.0);
            final int tipY = gripY + (int) Math.round(Math.sin(angle) * 22.0);
            graphics.setColor(new Color(225, 230, 238, 255));
            graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawLine(gripX, gripY, tipX, tipY);
            graphics.setColor(new Color(118, 79, 45, 255));
            graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawLine(gripX - 4, gripY + 3, gripX + 3, gripY - 2);
          }
        } finally {
          graphics.dispose();
        }
        model.replaceFrames(sequence, direction,
            Collections.singletonList(new AnimationFrame(image, new Point(24, 42),
                avatar ? "test-avatar" : "test-sword-overlay")));
      }
    }
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
