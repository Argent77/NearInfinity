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
import java.util.Map;

import org.infinity.gui.converter.creature.CreatureAnimationExporter.Config;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.ExportResult;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.CyclePlan;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.ResourcePlan;
import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.cre.decoder.util.AnimationInfo;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamV1Decoder.BamV1Control;
import org.infinity.resource.key.FileResourceEntry;

/** Dependency-free regression tests for the creature animation creator core. */
public final class CreatureAnimationCoreTest {
  private CreatureAnimationCoreTest() {
  }

  public static void main(String[] args) throws Exception {
    testEnhancedEditionSlotCoverage();
    testAllFamilyLayouts();
    testResourceNameBudgets();
    testAllFamilyBamV1Exports();
    testDescriptionAndGeneration();
    testPngRoundTrip();
    testFalseColorPaletteRoundTrip();
    testFamilyPaletteTransforms();
    testBamV1Export();
    testSplitBamV1Export();
    testBamV2Export();
    testFamilyBamV2Export();
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

  private static void testAllFamilyLayouts() {
    check(CreatureAnimationFamily.values().length == AnimationInfo.Type.values().length - 1,
        "Every real decoder family should have exactly one creator family");
    for (final AnimationInfo.Type type : AnimationInfo.Type.values()) {
      if (type == AnimationInfo.Type.PLACEHOLDER) {
        continue;
      }
      int matches = 0;
      for (final CreatureAnimationFamily family : CreatureAnimationFamily.values()) {
        if (family.getAnimationInfoType() == type) {
          matches++;
        }
      }
      check(matches == 1, type + " should have exactly one creator layout");
    }

    for (final CreatureAnimationFamily family : CreatureAnimationFamily.values()) {
      for (final Profile.Game game : MonsterAnimationLayout.SUPPORTED_GAMES) {
        final boolean expected =
            family != CreatureAnimationFamily.MONSTER_PLANESCAPE || game == Profile.Game.PSTEE;
        check(family.isSupportedGame(game) == expected,
            family + " has an incorrect Enhanced Edition compatibility result for " + game);
        check(family.isValidSlot(game, family.getDefaultSlot()) == expected,
            family + " has an incorrect default-slot result for " + game);
      }

      final String resref = family == CreatureAnimationFamily.MONSTER_PLANESCAPE ? "P01" : "TST1";
      final boolean split = family.isSplitBamsRequired();
      final int quadrants = family.hasQuadrants() ? family.getDefaultQuadrants() : 1;
      final int armorLevels = family.hasArmorLevels() ? family.getDefaultArmorLevels() : 1;
      final FamilyLayout layout = family.createLayout(resref, split, quadrants, armorLevels);
      check(layout.getResources().size() == getExpectedDefaultResourceCount(family),
          family + " should expose its complete default resource set");
      for (final ResourcePlan resource : layout.getResources().values()) {
        check(resource.getFileName().endsWith(".BAM"), "Every family resource should be a BAM");
        check(resource.getFileName().length() - 4 <= 8,
            resource.getFileName() + " exceeds the engine resref limit");
        check(resource.getCycleCount() > 0, resource.getFileName() + " should have cycles");
        int previous = -1;
        for (final CyclePlan cycle : resource.getCycles()) {
          check(cycle.getCycleIndex() > previous, resource.getFileName() + " cycles should be strictly ordered");
          check(cycle.getDirectionIndex() >= 0 && cycle.getDirectionIndex() < 16,
              resource.getFileName() + " contains an invalid direction");
          check(cycle.getSourceDirection() != null, resource.getFileName() + " should resolve a source direction");
          previous = cycle.getCycleIndex();
        }
      }
    }

    check(!CreatureAnimationFamily.MONSTER_PLANESCAPE.isSupportedGame(Profile.Game.BG2EE),
        "Planescape animations must be restricted to PSTEE");
    check(CreatureAnimationFamily.MONSTER_PLANESCAPE.createLayout("P01", false, 1, 1)
        .getActionResrefs().size() == 44, "Planescape layout should define all 44 standard and custom action slots");
    check(CreatureAnimationFamily.CHARACTER.getAnimationTypeCode(0x6500) == 0x6000,
        "Character INI type should follow the selected 0x6000 slot range");
    check(CreatureAnimationFamily.CHARACTER_OLD.getAnimationTypeCode(0x5600) == 0x5000,
        "Character-old INI type should follow the selected 0x5000 slot range");
    check(CreatureAnimationFamily.MONSTER_MULTI_NEW.createLayout("TST1", true, 4, 1)
        .getResources().size() == 52, "Split multi_new should create thirteen resources per quadrant");
    check(CreatureAnimationFamily.MONSTER_MULTI.createLayout("TST1", false, 9, 1)
        .getResources().get("TST1G21.BAM").getCycleCount() == 27,
        "monster_multi should retain all documented G2 compatibility cycles");
    final FamilyLayout splitMultiNew =
        CreatureAnimationFamily.MONSTER_MULTI_NEW.createLayout("TST1", true, 4, 1);
    check(splitMultiNew.getResources().get("TST1G11.BAM").getCycleCount() == 54,
        "Split multi_new base G1 should retain all documented compatibility cycles");
    check(splitMultiNew.getResources().get("TST1G214.BAM").getCycleCount() == 63,
        "Split multi_new G2 quadrant-4 should retain its shared A5, SP and CA cycles");
    check(CreatureAnimationFamily.MONSTER.createLayout("TST1", true, 1, 1)
        .getResources().size() == 13, "Split type 0x7000 should retain its thirteen-resource layout");
    check(CreatureAnimationFamily.CHARACTER.createLayout("TST1", true, 1, 1)
        .getResources().containsKey("TST11G14.BAM"),
        "Modern character output should include the documented split G14 damage resource");
    check(CreatureAnimationFamily.MONSTER_ICEWIND.createLayout("TST1", false, 1, 1)
        .getResources().get("TST1WKE.BAM").getCycleCount() == 8,
        "Icewind eastern resources should retain their documented cycle indices 5-7");

    for (int quadrants = 1; quadrants <= 9; quadrants++) {
      final boolean[][] covered = new boolean[13][17];
      for (int quadrant = 0; quadrant < quadrants; quadrant++) {
        final int[] bounds = CreatureAnimationExporter.getQuadrantBounds(17, 13, quadrant, quadrants);
        check(bounds[2] > 0 && bounds[3] > 0, "Quadrant bounds must retain positive dimensions");
        for (int y = bounds[1]; y < bounds[1] + bounds[3]; y++) {
          for (int x = bounds[0]; x < bounds[0] + bounds[2]; x++) {
            check(!covered[y][x], "Quadrant bounds must not overlap");
            covered[y][x] = true;
          }
        }
      }
      for (final boolean[] row : covered) {
        for (final boolean pixel : row) {
          check(pixel, "Quadrant bounds must cover every source pixel");
        }
      }
    }
  }

  private static void testResourceNameBudgets() throws Exception {
    check(CreatureAnimationFamily.EFFECT.getMaximumResrefLength(false, 1, 1) == 8,
        "Effect should permit a full eight-character BAM resref");
    check(CreatureAnimationFamily.TOWN_STATIC.getMaximumResrefLength(false, 1, 1) == 8,
        "Town static should permit a full eight-character BAM resref");
    check(CreatureAnimationFamily.FLYING.getMaximumResrefLength(false, 1, 1) == 8,
        "Flying should permit a full eight-character BAM resref");
    check(CreatureAnimationFamily.MONSTER_MULTI_NEW.getMaximumResrefLength(false, 4, 1) == 5,
        "Unsplit multi_new should derive a five-character prefix budget");
    check(CreatureAnimationFamily.MONSTER_MULTI_NEW.getMaximumResrefLength(true, 4, 1) == 4,
        "Split multi_new should derive a four-character prefix budget");
    check(CreatureAnimationFamily.CHARACTER.getMaximumResrefLength(true, 1, 1) == 4,
        "Character should retain its documented four-character base schema");
    check(CreatureAnimationFamily.MONSTER_PLANESCAPE.getMaximumResrefLength(false, 1, 1) == 3,
        "Planescape standard action prefixes should leave a three-character creature resref");

    final Path output = createTestDirectory("ni-creature-resref-budget-test-");
    try {
      Config config = new Config().setGame(Profile.Game.BG2EE).setFamily(CreatureAnimationFamily.EFFECT)
          .setAnimationId(0x0000).setResref("EFFECT01").setOutputDirectory(output);
      check(!CreatureAnimationExporter.validate(createMinimalModel(), config).hasErrors(),
          "An eight-character effect resref should pass validation");

      config = new Config().setGame(Profile.Game.BG2EE).setFamily(CreatureAnimationFamily.MONSTER_MULTI_NEW)
          .setAnimationId(0x1300).setResref("ABCDE").setOutputDirectory(output).setSplitBams(false)
          .setQuadrants(4);
      check(!CreatureAnimationExporter.validate(createMinimalModel(), config).hasErrors(),
          "A five-character unsplit multi_new resref should pass validation");
      config.setSplitBams(true);
      check(CreatureAnimationExporter.validate(createMinimalModel(), config).hasErrors(),
          "The same prefix should be rejected when the split suffix would exceed eight characters");

      config = new Config().setGame(Profile.Game.BG2EE).setFamily(CreatureAnimationFamily.CHARACTER)
          .setAnimationId(0x5000).setResref("ABC").setOutputDirectory(output).setSplitBams(true);
      check(CreatureAnimationExporter.validate(createMinimalModel(), config).hasErrors(),
          "Modern character resrefs should enforce the documented four-character schema");
    } finally {
      deleteTree(output);
    }
  }

  private static int getExpectedDefaultResourceCount(CreatureAnimationFamily family) {
    switch (family) {
      case EFFECT:
        return 1;
      case MONSTER_QUADRANT:
        return 24;
      case MONSTER_MULTI:
        return 45;
      case MONSTER_MULTI_NEW:
        return 8;
      case MONSTER_LAYERED_SPELL:
        return 4;
      case MONSTER_ANKHEG:
        return 6;
      case TOWN_STATIC:
        return 1;
      case CHARACTER:
        return 23;
      case CHARACTER_OLD:
        return 22;
      case MONSTER:
        return 2;
      case MONSTER_OLD:
      case MONSTER_LAYERED:
        return 4;
      case MONSTER_LARGE:
      case MONSTER_LARGE_16:
        return 6;
      case AMBIENT_STATIC:
      case AMBIENT:
        return 2;
      case FLYING:
        return 1;
      case MONSTER_ICEWIND:
        return 28;
      case MONSTER_PLANESCAPE:
        return 43;
      default:
        throw new AssertionError("Missing expected resource count for " + family);
    }
  }

  private static void testAllFamilyBamV1Exports() throws Exception {
    final Path root = createTestDirectory("ni-creature-families-test-");
    try {
      for (final CreatureAnimationFamily family : CreatureAnimationFamily.values()) {
        final Path output = root.resolve(family.name().toLowerCase());
        final Profile.Game game =
            family == CreatureAnimationFamily.MONSTER_PLANESCAPE ? Profile.Game.PSTEE : Profile.Game.BG2EE;
        final String resref = family == CreatureAnimationFamily.MONSTER_PLANESCAPE ? "P01" : "TST1";
        final boolean split = family.isSplitBamsRequired();
        final int quadrants = family.hasQuadrants() ? 1 : 4;
        final Config config = new Config().setGame(game).setFamily(family)
            .setAnimationId(family.getDefaultSlot()).setResref(resref).setOutputDirectory(output)
            .setBamFormat(BamFormat.BAM_V1).setCompressedBam(false).setSplitBams(split)
            .setQuadrants(quadrants).setArmorLevels(1);
        check(!CreatureAnimationExporter.validate(createMinimalModel(), config).hasErrors(),
            family + " should pass neutral-source validation");
        final FamilyLayout layout = family.createLayout(resref, split, quadrants, 1);
        final ExportResult result = CreatureAnimationExporter.export(createMinimalModel(), config, false);
        check(result.getInstalledFiles().size() == layout.getResources().size() + 1,
            family + " should install every planned BAM plus one INI");

        final Path ini = output.resolve(String.format("%04X.INI", family.getDefaultSlot()));
        final String iniText = new String(Files.readAllBytes(ini), "UTF-8");
        check(iniText.contains("[" + family.getSectionName() + "]"),
            family + " INI should contain its decoder section");
        check(iniText.contains(String.format("animation_type=%04X",
            family.getAnimationTypeCode(family.getDefaultSlot()))),
            family + " INI should contain its exact animation type");

        for (final Map.Entry<String, ResourcePlan> entry : layout.getResources().entrySet()) {
          final Path bam = output.resolve(entry.getKey());
          check(BamDecoder.getType(new FileResourceEntry(bam)) == BamDecoder.Type.BAMV1,
              entry.getKey() + " should reopen as BAM V1");
          final BamDecoder decoder = BamDecoder.loadBam(new FileResourceEntry(bam));
          try {
            check(decoder.createControl().cycleCount() == entry.getValue().getCycleCount(),
                entry.getKey() + " should preserve its planned cycle count");
          } finally {
            decoder.close();
          }
        }
      }
    } finally {
      deleteTree(root);
    }
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

  private static void testFamilyPaletteTransforms() throws Exception {
    final Path directory = createTestDirectory("ni-creature-family-palette-test-");
    try {
      final Config config = new Config().setGame(Profile.Game.BG2EE)
          .setFamily(CreatureAnimationFamily.MONSTER_QUADRANT).setAnimationId(0x1000).setResref("TST1")
          .setOutputDirectory(directory).setBamFormat(BamFormat.BAM_V1).setCompressedBam(false)
          .setQuadrants(3).setFalseColor(true);
      check(!CreatureAnimationExporter.validate(createPalettedModel(), config).hasErrors(),
          "Indexed palettes should remain valid for explicit east-direction and quadrant transforms");
      CreatureAnimationExporter.export(createPalettedModel(), config, false);

      final BamDecoder decoder =
          BamDecoder.loadBam(new FileResourceEntry(directory.resolve("TST1G21E.BAM")));
      try {
        final int[] palette = ((BamV1Control) decoder.createControl()).getPalette();
        check((palette[4] & 0x00ffffff) == 0x00336699 && (palette[16] & 0x00ffffff) == 0x00cc8844,
            "Mirroring and quadrant slicing must preserve false-color palette indices");
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

  private static void testFamilyBamV2Export() throws Exception {
    final Path directory = createTestDirectory("ni-creature-family-bam2-test-");
    try {
      final Config config = new Config().setGame(Profile.Game.BG2EE)
          .setFamily(CreatureAnimationFamily.MONSTER_QUADRANT).setAnimationId(0x1000).setResref("QDV2")
          .setOutputDirectory(directory).setBamFormat(BamFormat.BAM_V2).setCompressedBam(false)
          .setQuadrants(2);
      final ExportResult result = CreatureAnimationExporter.export(createMinimalModel(), config, false);
      final FamilyLayout layout =
          CreatureAnimationFamily.MONSTER_QUADRANT.createLayout("QDV2", false, 2, 1);
      check(result.getInstalledFiles().size() > layout.getResources().size() + 1,
          "Quadrant BAM V2 output should include every BAM, its INI and PVRZ texture pages");
      for (final String fileName : layout.getResources().keySet()) {
        final Path bam = directory.resolve(fileName);
        check(BamDecoder.getType(new FileResourceEntry(bam)) == BamDecoder.Type.BAMV2,
            fileName + " should reopen as BAM V2");
        CreatureAnimationExporter.validatePvrzReferences(directory, bam);
      }
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
