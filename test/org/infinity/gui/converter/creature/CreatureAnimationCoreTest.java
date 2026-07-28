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
import java.util.prefs.Preferences;

import org.infinity.gui.converter.creature.CreatureAnimationExporter.Config;
import org.infinity.gui.converter.creature.CreatureAnimationExporter.ExportResult;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.CyclePlan;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.LayoutBuilder;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.ResourcePlan;
import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.EquipmentOverlayFamily.AttackKind;
import org.infinity.gui.converter.creature.EquipmentOverlayFamily.OverlaySlot;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.WeaponType;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.Profile;
import org.infinity.resource.cre.decoder.util.AnimationInfo;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamV1Decoder;
import org.infinity.resource.graphics.BamV1Decoder.BamV1Control;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamControl;
import org.infinity.resource.key.FileResourceEntry;

/** Dependency-free regression tests for the creature animation creator core. */
public final class CreatureAnimationCoreTest {
  private CreatureAnimationCoreTest() {
  }

  public static void main(String[] args) throws Exception {
    testCreatorSettingsAndPreviewControls();
    testProfileSlotCoverage();
    testAllFamilyLayouts();
    testClassicProfileExport();
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
    testExpandedEquipmentPromptAndGeneration();
    testEquipmentProfileFormats();
    testEquipmentReferenceResrefFallback();
    testEquipmentOverlayFamilyLayouts();
    testEquipmentLoadoutLayouts();
    testAllEquipmentOverlayFamilyBamV1Exports();
    testEquipmentLoadoutBamRoundTrips();
    testEquipmentExplicitEasternRoundTrip();
    testEquipmentOverlayBamRoundTrip();
    testEquipmentOverlayBamV2RoundTrip();
    testEquipmentOverlayFamilyBamV2RoundTrip();
    System.out.println("CreatureAnimationCoreTest: all checks passed");
  }

  private static void testCreatorSettingsAndPreviewControls() throws Exception {
    final Path root = createTestDirectory("ni-creature-settings-test-");
    final Preferences preferences = Preferences.userRoot().node(
        "/org/infinity/gui/converter/creature/CreatureAnimationCoreTest-" + Long.toHexString(System.nanoTime()));
    try {
      final Path source = Files.createDirectories(root.resolve("artist").resolve("source"));
      final Path missing = source.resolve("not-created").resolve("child");
      check(CreatureAnimationCreator.resolveInitialDirectory(null, root).equals(root.toAbsolutePath().normalize()),
          "A file dialog without history should start in the active install root");
      check(CreatureAnimationCreator.resolveInitialDirectory(missing, root).equals(source.toAbsolutePath().normalize()),
          "A missing remembered directory should fall back to its closest existing parent");

      final CreatureAnimationCreatorSettings.State stored =
          new CreatureAnimationCreatorSettings.State(root.resolve("override"), root);
      stored.sourceDirectory = source;
      stored.family = CreatureAnimationFamily.MONSTER_LAYERED;
      stored.bamFormat = BamFormat.BAM_V2;
      stored.compressedBam = false;
      stored.splitBams = true;
      stored.quadrants = 7;
      stored.armorLevels = 3;
      stored.canLieDown = false;
      stored.detectedByInfravision = false;
      stored.falseColor = true;
      stored.pathSmooth = false;
      stored.translucent = true;
      stored.moveScale = 17;
      stored.ellipse = 24;
      stored.personalSpace = 5;
      stored.bloodColor = 63;
      stored.chunkColor = 12;
      stored.previewSequence = Sequence.CAST;
      stored.previewDirection = 11;
      stored.previewPlaying = false;
      stored.previewPivot = false;
      stored.previewFrameRate = 15;
      stored.previewZoom = 250;
      CreatureAnimationCreatorSettings.store(preferences, stored);

      final CreatureAnimationCreatorSettings.State loaded =
          CreatureAnimationCreatorSettings.load(preferences, root.resolve("different"), root);
      check(loaded.outputDirectory.equals(root.resolve("override").toAbsolutePath().normalize()),
          "The selected output directory should survive a preferences round trip");
      check(loaded.sourceDirectory.equals(source.toAbsolutePath().normalize()),
          "The selected PNG directory should survive a preferences round trip");
      check(loaded.family == stored.family && loaded.bamFormat == stored.bamFormat,
          "Family and BAM format preferences should survive a round trip");
      check(!loaded.compressedBam && loaded.splitBams && loaded.quadrants == 7 && loaded.armorLevels == 3,
          "Layout preferences should survive a round trip");
      check(!loaded.canLieDown && !loaded.detectedByInfravision && loaded.falseColor && !loaded.pathSmooth
          && loaded.translucent, "Engine switch preferences should survive a round trip");
      check(loaded.moveScale == 17 && loaded.ellipse == 24 && loaded.personalSpace == 5
          && loaded.bloodColor == 63 && loaded.chunkColor == 12,
          "Engine numeric preferences should survive a round trip");
      check(loaded.previewSequence == Sequence.CAST && loaded.previewDirection == 11
          && !loaded.previewPlaying && !loaded.previewPivot && loaded.previewFrameRate == 15
          && loaded.previewZoom == 250, "Preview preferences should survive a round trip");

      final Path otherRoot = root.resolve("other-install");
      final CreatureAnimationCreatorSettings.State otherGame =
          CreatureAnimationCreatorSettings.load(preferences, otherRoot.resolve("override"), otherRoot);
      check(otherGame.outputDirectory.equals(otherRoot.resolve("override").toAbsolutePath().normalize())
          && otherGame.sourceDirectory.equals(otherRoot.toAbsolutePath().normalize()),
          "Directories remembered for one game must not replace another game's install defaults");

      stored.quadrants = Integer.MAX_VALUE;
      stored.armorLevels = Integer.MAX_VALUE;
      stored.previewDirection = Integer.MAX_VALUE;
      stored.previewFrameRate = Integer.MAX_VALUE;
      stored.previewZoom = Integer.MIN_VALUE;
      CreatureAnimationCreatorSettings.store(preferences, stored);
      final CreatureAnimationCreatorSettings.State bounded =
          CreatureAnimationCreatorSettings.load(preferences, root, root);
      check(bounded.quadrants == 9 && bounded.armorLevels == 9,
          "Stored family ranges should be bounded before persistence");
      check(bounded.previewDirection == AnimationPreviewPanel.PREVIEW_DIRECTIONS.length - 1,
          "Stored preview directions should be bounded before persistence");
      check(bounded.previewFrameRate == AnimationPreviewPanel.MAX_FRAME_RATE
          && bounded.previewZoom == AnimationPreviewPanel.MIN_ZOOM_PERCENT,
          "Stored preview speed and zoom should be bounded before persistence");

      final AnimationPreviewPanel preview = new AnimationPreviewPanel();
      try {
        preview.setFrameRate(15);
        preview.setZoomPercent(250);
        check(preview.getFrameRate() == 15 && AnimationPreviewPanel.getFrameDelay(15) == 1000 / 15,
            "A 15 fps selection should use Near Infinity's established 15 fps timer interval");
        check(preview.getZoomPercent() == 250, "The preview should retain its explicit zoom percentage");
      } finally {
        preview.setPlaying(false);
      }
    } finally {
      preferences.removeNode();
      Preferences.userRoot().flush();
      deleteTree(root);
    }
  }

  private static void testProfileSlotCoverage() {
    check(MonsterAnimationLayout.SUPPORTED_GAMES.size() == Profile.Game.values().length - 1,
        "Every recognized Infinity Engine profile should be supported");
    check(MonsterAnimationLayout.isSupportedGame(Profile.Game.BG1)
        && MonsterAnimationLayout.isSupportedGame(Profile.Game.BG2ToB)
        && MonsterAnimationLayout.isSupportedGame(Profile.Game.PST)
        && MonsterAnimationLayout.isSupportedGame(Profile.Game.IWDHowTotLM)
        && MonsterAnimationLayout.isSupportedGame(Profile.Game.IWD2EE),
        "Classic engine profiles should be accepted");
    check(!MonsterAnimationLayout.isSupportedGame(Profile.Game.Unknown),
        "The unrecognized profile must remain unsupported");
    check(MonsterAnimationLayout.isValidSlot(Profile.Game.BG2ToB, 0x7303),
        "Classic BG2 should accept its documented modern monster slot");
    check(!MonsterAnimationLayout.isValidSlot(Profile.Game.BG2ToB, 0x7000),
        "Classic BG2 should keep monster_old and modern monster ranges distinct");
    check(AnimationInfo.Type.MONSTER_MULTI.isSupported(Profile.Game.IWD2EE)
        && AnimationInfo.Type.MONSTER.isSupported(Profile.Game.IWD2EE)
        && AnimationInfo.Type.MONSTER_ICEWIND.isSupported(Profile.Game.IWD2EE),
        "IWD2EE should use the same exact animation-family ranges as IWD2");

    check(!Profile.isBamcSupported(Profile.Game.BG1)
        && !Profile.isBamcSupported(Profile.Game.BG1TotSC)
        && !Profile.isBamcSupported(Profile.Game.PST),
        "BG1-family and PST profiles should reject BAMC");
    check(Profile.isBamcSupported(Profile.Game.BG2ToB)
        && Profile.isBamcSupported(Profile.Game.IWDHowTotLM)
        && Profile.isBamcSupported(Profile.Game.IWD2)
        && Profile.isBamcSupported(Profile.Game.IWD2EE)
        && Profile.isBamcSupported(Profile.Game.BG2EE),
        "BG2, IWD, IWD2 and Enhanced Edition profiles should accept BAMC");
    for (final Profile.Game game : Profile.Game.values()) {
      check(Profile.isBamV2Supported(game) == Profile.isEnhancedEdition(game),
          game + " has an incorrect BAM V2 capability");
    }
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
        final boolean expected = family.getAnimationInfoType().isSupported(game);
        check(family.isSupportedGame(game) == expected,
            family + " has an incorrect profile compatibility result for " + game);
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

    check(CreatureAnimationFamily.MONSTER_PLANESCAPE.isSupportedGame(Profile.Game.PST)
        && CreatureAnimationFamily.MONSTER_PLANESCAPE.isSupportedGame(Profile.Game.PSTEE)
        && !CreatureAnimationFamily.MONSTER_PLANESCAPE.isSupportedGame(Profile.Game.BG2EE),
        "Planescape animations must be restricted to PST and PSTEE");
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

  private static void testClassicProfileExport() throws Exception {
    final LayoutBuilder aliasBuilder = new LayoutBuilder();
    aliasBuilder.addCycleIfAbsent("ALIAS.BAM",
        new CyclePlan(0, Sequence.DIE, Direction.S.getCycleOffset(), false, false, -1, 0));
    aliasBuilder.addCycleIfAbsent("ALIAS.BAM",
        new CyclePlan(0, Sequence.SLEEP, Direction.S.getCycleOffset(), false, false, -1, 0));
    check(aliasBuilder.build().getResources().get("ALIAS.BAM").getCycles().get(0).getSequence() == Sequence.DIE,
        "Classic decoder aliases must retain the first canonical physical-cycle mapping");

    final Path directory = createTestDirectory("ni-creature-classic-test-");
    final Path target = directory.resolve("CLS.BAM");
    final int[] palette = new int[256];
    Arrays.fill(palette, 0xff000000);
    palette[5] = 0xffcc3322;
    palette[6] = 0xff2255cc;
    palette[7] = 0xff33aa55;
    palette[9] = 0x0000ff00;
    final IndexColorModel colorModel =
        new IndexColorModel(8, palette.length, palette, 0, true, 9, DataBuffer.TYPE_BYTE);

    final PseudoBamDecoder installedSource = new PseudoBamDecoder();
    try {
      final BufferedImage sharedImage =
          new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
      fillIndexedImage(sharedImage, 9);
      sharedImage.getRaster().setSample(1, 1, 0, 5);
      final BufferedImage replacedImage =
          new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
      fillIndexedImage(replacedImage, 9);
      replacedImage.getRaster().setSample(2, 2, 0, 6);
      final int sharedFrame = installedSource.frameAdd(sharedImage, new Point(1, 2));
      final int replacedFrame = installedSource.frameAdd(replacedImage, new Point(3, 4));
      final PseudoBamControl control = installedSource.createControl();
      control.cycleAdd(new int[] { sharedFrame });
      control.cycleAdd(new int[] { replacedFrame });
      control.cycleAdd(new int[] { sharedFrame });
      installedSource.setOption(PseudoBamDecoder.OPTION_INT_RLEINDEX, 9);
      installedSource.setOption(PseudoBamDecoder.OPTION_BOOL_COMPRESSED, false);
      check(installedSource.exportBamV1(target, null, 0),
          "The synthetic installed classic BAM should be written");
    } finally {
      installedSource.close();
    }

    final LayoutBuilder builder = new LayoutBuilder();
    builder.addCycle("CLS.BAM",
        new CyclePlan(1, Sequence.STANCE, Direction.S.getCycleOffset(), false, false, -1, 0));
    builder.preserveExistingCycles("CLS.BAM");
    final FamilyLayout layout = builder.build();
    final ClassicAnimationDefinition definition = ClassicAnimationDefinition.forTesting(
        Profile.Game.BG1, 0x0001, CreatureAnimationFamily.EFFECT, "CLS", layout, true);
    final Config config = new Config().setGame(Profile.Game.BG1)
        .setFamily(CreatureAnimationFamily.EFFECT).setAnimationId(0x0001).setResref("CLS")
        .setClassicDefinition(definition).setOutputDirectory(directory)
        .setBamFormat(BamFormat.BAM_V1).setCompressedBam(false).setFalseColor(true);
    final BufferedImage authoredImage =
        new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
    fillIndexedImage(authoredImage, 9);
    authoredImage.getRaster().setSample(8, 8, 0, 7);
    final CreatureAnimationModel model = new CreatureAnimationModel();
    model.replaceFrames(Sequence.STANCE, Direction.S,
        Collections.singletonList(new AnimationFrame(authoredImage, new Point(8, 14), "classic-palette-test")));

    try {
      final CreatureAnimationExporter.ValidationReport validation =
          CreatureAnimationExporter.validate(model, config);
      check(!validation.hasErrors(),
          "An exact complete classic profile replacement should pass validation: " + validation.getMessages());
      final ExportResult result = CreatureAnimationExporter.export(model, config, true);
      check(result.getInstalledFiles().size() == 1 && result.getInstalledFiles().get(0).equals(target),
          "Classic export should install only the exact planned BAM resource");
      check(!Files.exists(directory.resolve("0001.INI")),
          "Classic export must not generate an Enhanced Edition animation INI");

      final BamDecoder reopened = BamDecoder.loadBam(new FileResourceEntry(target));
      check(reopened instanceof BamV1Decoder && reopened.isOpen(),
          "The classic replacement should reopen as BAM V1");
      try {
        final BamV1Control control = (BamV1Control) reopened.createControl();
        check(control.cycleCount() == 3,
            "Classic export must retain the installed BAM's exact cycle count");
        check(reopened.frameCount() == 3,
            "Classic export must retain the complete installed frame table before appending generated frames");
        final BamDecoder.FrameEntry replacedOriginal = reopened.getFrameInfo(1);
        check(replacedOriginal.getCenterX() == 3 && replacedOriginal.getCenterY() == 4,
            "Frames referenced only by a replaced cycle must remain at their original absolute index");
        check(control.cycleSet(0) && control.cycleFrameCount() == 1,
            "The first untouched classic cycle should remain present");
        final int firstFrame = control.cycleGetFrameIndexAbsolute(0);
        check(control.cycleSet(2) && control.cycleFrameCount() == 1,
            "The last untouched classic cycle should remain present");
        final int lastFrame = control.cycleGetFrameIndexAbsolute(0);
        check(firstFrame == lastFrame,
            "Untouched classic cycles should preserve installed frame sharing");
        final BamDecoder.FrameEntry shared = reopened.getFrameInfo(firstFrame);
        check(shared.getCenterX() == 1 && shared.getCenterY() == 2,
            "Untouched classic frame centers should remain exact");
        final int[] reopenedPalette = control.getPalette();
        check(((BamV1Decoder) reopened).getRleIndex() == 9,
            "Classic export must preserve the installed BAM's transparency index");
        final IndexColorModel reopenedColorModel =
            new IndexColorModel(8, reopenedPalette.length, reopenedPalette, 0, true, 9,
                DataBuffer.TYPE_BYTE);
        final BufferedImage copied =
            new BufferedImage(shared.getWidth(), shared.getHeight(), BufferedImage.TYPE_BYTE_INDEXED,
                reopenedColorModel);
        reopened.frameGet(control, firstFrame, copied);
        check(copied.getRaster().getSample(1, 1, 0) == 5,
            "Untouched classic frames should preserve their exact palette indices");
        check(control.cycleSet(1) && control.cycleFrameCount() == 1,
            "The exact planned classic cycle should be replaced");
        final BamDecoder.FrameEntry generated =
            reopened.getFrameInfo(control.cycleGetFrameIndexAbsolute(0));
        check(generated.getCenterX() == 8 && generated.getCenterY() == 14,
            "Generated classic frame centers should use the authored source pivots");
        for (int index = 0; index < palette.length; index++) {
          check((reopenedPalette[index] & 0x00ffffff) == (palette[index] & 0x00ffffff),
              "Classic export must preserve installed palette index " + index);
        }
      } finally {
        reopened.close();
      }

      final Config v2 = new Config().setGame(Profile.Game.BG1)
          .setFamily(CreatureAnimationFamily.EFFECT).setAnimationId(0x0001).setResref("CLS")
          .setClassicDefinition(definition).setOutputDirectory(directory)
          .setBamFormat(BamFormat.BAM_V2).setCompressedBam(false);
      check(hasValidationMessage(CreatureAnimationExporter.validate(model, v2), "does not support BAM V2"),
          "Classic BG1 should reject BAM V2 explicitly");
      final Config bamc = new Config().setGame(Profile.Game.BG1)
          .setFamily(CreatureAnimationFamily.EFFECT).setAnimationId(0x0001).setResref("CLS")
          .setClassicDefinition(definition).setOutputDirectory(directory)
          .setBamFormat(BamFormat.BAM_V1).setCompressedBam(true);
      check(hasValidationMessage(CreatureAnimationExporter.validate(model, bamc), "does not support BAMC"),
          "Classic BG1 should reject BAMC explicitly");
      boolean iniRejected = false;
      try {
        CreatureAnimationExporter.createIniText(config);
      } catch (IllegalArgumentException e) {
        iniRejected = true;
      }
      check(iniRejected, "Classic profile configuration must reject INI generation");
    } finally {
      deleteTree(directory);
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

    final EquipmentOverlayModel mirroredFamily = EquipmentOverlayGenerator.generate(
        EquipmentOverlayModel.fromWestern(createCompleteEquipmentModel(false)),
        EquipmentOverlayModel.fromWestern(createCompleteEquipmentModel(true)), prompt, 77L, false, null);
    check(mirroredFamily.getEasternModel().isEmpty(),
        "Families that mirror east must not retain independent eastern artwork that will not be exported");
  }

  private static void testExpandedEquipmentPromptAndGeneration() {
    final EquipmentOverlayGenerator.PromptSpec dual = EquipmentOverlayGenerator.parsePrompt(
        "SOLAR wielding a longsword in the main hand and a mace in the offhand");
    check(dual.getTargetWeapon() == WeaponType.SWORD,
        "An explicitly assigned longsword must be parsed as the main-hand weapon");
    check(dual.getTargetOffhand() == WeaponType.MACE && dual.isTwoWeaponLoadout(),
        "An explicitly assigned mace must be parsed as a second weapon");

    final EquipmentOverlayGenerator.PromptSpec spearAndShield = EquipmentOverlayGenerator.parsePrompt(
        "SOLAR with a one-handed spear in the main hand and a large shield in the offhand");
    check(spearAndShield.getTargetWeapon() == WeaponType.ONE_HANDED_SPEAR,
        "The one-handed spear phrase must not collapse to the existing two-handed spear");
    check(spearAndShield.getTargetOffhand() == WeaponType.LARGE_SHIELD,
        "A qualified large shield must be parsed as off-hand equipment");

    final EquipmentOverlayGenerator.PromptSpec unicode =
        EquipmentOverlayGenerator.parsePrompt("replace the sword with a silver Ninjatō");
    check(unicode.getTargetWeapon() == WeaponType.NINJATO,
        "Unicode Ninjatō spelling must normalize to the procedural ninjato type");

    final String[] prompts = {
        "replace the sword with a light crossbow",
        "replace the sword with a heavy crossbow",
        "replace the sword with a shortbow",
        "replace the sword with a longbow",
        "replace the sword with a sling",
        "replace the sword with a scimitar",
        "replace the sword with a wakizashi",
        "replace the sword with a ninjato",
        "replace the sword with a katana",
        "replace the sword with a one-handed spear"
    };
    final WeaponType[] expected = {
        WeaponType.LIGHT_CROSSBOW,
        WeaponType.HEAVY_CROSSBOW,
        WeaponType.SHORTBOW,
        WeaponType.LONGBOW,
        WeaponType.SLING,
        WeaponType.SCIMITAR,
        WeaponType.WAKIZASHI,
        WeaponType.NINJATO,
        WeaponType.KATANA,
        WeaponType.ONE_HANDED_SPEAR
    };
    for (int index = 0; index < prompts.length; index++) {
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt(prompts[index]);
      check(prompt.getTargetWeapon() == expected[index],
          prompts[index] + " should select " + expected[index]);
      final CreatureAnimationModel generated = EquipmentOverlayGenerator.generate(
          createCompleteEquipmentModel(false), createCompleteEquipmentModel(true), prompt, 150L + index, null);
      check(hasVisiblePixel(generated.getFrames(Sequence.ATTACK_1, Direction.W).get(0).getImage()),
          expected[index] + " must render visible synchronized artwork");
    }

    final WeaponType[] shields = {
        WeaponType.BUCKLER,
        WeaponType.SMALL_SHIELD,
        WeaponType.MEDIUM_SHIELD,
        WeaponType.LARGE_SHIELD
    };
    for (final WeaponType shield : shields) {
      final CreatureAnimationModel generated = EquipmentOverlayGenerator.generate(
          createCompleteEquipmentModel(false), createCompleteEquipmentModel(true),
          spearAndShield.forTarget(shield), 211L + shield.ordinal(), null);
      check(hasVisiblePixel(generated.getFrames(Sequence.STANCE, Direction.W).get(0).getImage()),
          shield + " must render visible synchronized artwork");
    }

    boolean rejected = false;
    try {
      EquipmentOverlayGenerator.parsePrompt(
          "a greatsword in the main hand and a buckler in the offhand");
    } catch (IllegalArgumentException e) {
      rejected = e.getMessage().contains("both hands");
    }
    check(rejected, "A two-handed main weapon plus off-hand shield must be rejected before resource discovery");
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

  private static void testEquipmentOverlayFamilyLayouts() {
    final EquipmentOverlayFamily[] families = EquipmentOverlayFamily.values();
    check(families.length == 6,
        "Exactly the six Near Infinity decoders that emit weapon sprite segments must be supported");
    final List<AnimationInfo.Type> expectedTypes = Arrays.asList(
        AnimationInfo.Type.CHARACTER,
        AnimationInfo.Type.CHARACTER_OLD,
        AnimationInfo.Type.MONSTER,
        AnimationInfo.Type.MONSTER_LAYERED_SPELL,
        AnimationInfo.Type.MONSTER_LAYERED,
        AnimationInfo.Type.MONSTER_ICEWIND);
    for (final AnimationInfo.Type type : expectedTypes) {
      int matches = 0;
      for (final EquipmentOverlayFamily family : families) {
        if (family.getAnimationType() == type) {
          matches++;
        }
      }
      check(matches == 1, type + " must have exactly one equipment-overlay layout");
    }

    FamilyLayout layout =
        EquipmentOverlayFamily.MONSTER.createOverlayLayout("MSOL", "SY", WeaponType.SCYTHE);
    check(layout.getResources().size() == 2, "Type 0x7000 overlays must contain G1 and G2");
    checkResource(layout, "MSOLG1SY.BAM", 72);
    checkResource(layout, "MSOLG2SY.BAM", 63);

    layout = EquipmentOverlayFamily.CHARACTER.createOverlayLayout("WQL", "SY", WeaponType.SCYTHE);
    check(layout.getResources().size() == 5,
        "A modern two-handed character overlay must contain three attacks, casting and general movement");
    checkResource(layout, "WQLSYA2.BAM", 9);
    checkResource(layout, "WQLSYA4.BAM", 9);
    checkResource(layout, "WQLSYA6.BAM", 9);
    checkResource(layout, "WQLSYCA.BAM", 72);
    checkResource(layout, "WQLSYG1.BAM", 99);

    layout = EquipmentOverlayFamily.CHARACTER_OLD.createOverlayLayout("WPL", "SY", WeaponType.SCYTHE);
    check(layout.getResources().size() == 12,
        "A legacy two-handed character overlay must contain western/eastern attack, casting and movement resources");
    checkResource(layout, "WPLSYA2.BAM", 5);
    checkResource(layout, "WPLSYA2E.BAM", 8);
    checkResource(layout, "WPLSYCA.BAM", 61);
    checkResource(layout, "WPLSYCAE.BAM", 64);
    checkResource(layout, "WPLSYG1.BAM", 61);
    checkResource(layout, "WPLSYG1E.BAM", 64);
    checkResource(layout, "WPLSYW2.BAM", 5);
    checkResource(layout, "WPLSYW2E.BAM", 8);

    layout =
        EquipmentOverlayFamily.MONSTER_LAYERED_SPELL.createOverlayLayout("MSP", "S0", WeaponType.SWORD);
    check(layout.getResources().size() == 4,
        "Layered-spell overlays must contain western/eastern G1 and G2 resources");
    checkResource(layout, "MSPSG1.BAM", 45);
    checkResource(layout, "MSPSG1E.BAM", 48);
    checkResource(layout, "MSPSG2.BAM", 21);
    checkResource(layout, "MSPSG2E.BAM", 24);

    layout = EquipmentOverlayFamily.MONSTER_LAYERED.createOverlayLayout("MLR", "S0", WeaponType.SWORD);
    check(EquipmentOverlayFamily.MONSTER_LAYERED.isAppearanceCodeRestrictedByDefinition(),
        "Layered monster appearance prefixes must remain constrained by the animation definition");
    check(layout.getResources().size() == 4,
        "Layered monster overlays must contain western/eastern G1 and G2 resources");
    checkResource(layout, "MLRSG1.BAM", 45);
    checkResource(layout, "MLRSG1E.BAM", 48);
    checkResource(layout, "MLRSG2.BAM", 21);
    checkResource(layout, "MLRSG2E.BAM", 24);
    check(layout.getResources().keySet().equals(
        EquipmentOverlayFamily.MONSTER_LAYERED
            .createOverlayLayout("MLR", "SY", WeaponType.SWORD).getResources().keySet()),
        "Layered monster filenames must use only the Equipped appearance field's first character");

    layout = EquipmentOverlayFamily.MONSTER_ICEWIND.createOverlayLayout("MIL", "S0", WeaponType.SWORD);
    check(!EquipmentOverlayFamily.MONSTER_ICEWIND.isAppearanceCodeRestrictedByDefinition(),
        "Icewind appearance prefixes are selected directly from the equipped item");
    check(layout.getResources().size() == 28,
        "Icewind overlays must contain fourteen western action BAMs and fourteen optional eastern BAMs");
    checkResource(layout, "MILSA1.BAM", 5);
    checkResource(layout, "MILSA1E.BAM", 8);
    checkResource(layout, "MILSWK.BAM", 5);
    checkResource(layout, "MILSWKE.BAM", 8);

    boolean rejected = false;
    try {
      EquipmentOverlayFamily.CHARACTER.createOverlayLayout("ABCDE", "SY", WeaponType.SCYTHE);
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected, "Equipment layouts must reject filenames beyond the engine's eight-character resref budget");
  }

  private static void testEquipmentLoadoutLayouts() {
    final EquipmentOverlayFamily modern = EquipmentOverlayFamily.CHARACTER;
    final AttackKind twoWeapon = modern.getAttackKind(WeaponType.SWORD, WeaponType.MACE);
    check(twoWeapon == AttackKind.TWO_WEAPON,
        "Two one-handed melee weapons must select the decoder's two-weapon attack kind");

    FamilyLayout layout = modern.createOverlayLayout("WQL", "S1", WeaponType.SWORD, twoWeapon,
        OverlaySlot.MAIN_HAND);
    check(layout.getResources().size() == 4,
        "A modern two-weapon main-hand layer must contain A7, A9, casting and movement resources");
    checkResource(layout, "WQLS1A7.BAM", 9);
    checkResource(layout, "WQLS1A9.BAM", 9);
    checkResource(layout, "WQLS1CA.BAM", 72);
    checkResource(layout, "WQLS1G1.BAM", 99);

    layout = modern.createOverlayLayout("WQS", "MC", WeaponType.MACE, twoWeapon,
        OverlaySlot.OFF_HAND_WEAPON);
    check(layout.getResources().size() == 4,
        "A modern left-handed weapon layer must mirror the exact two-weapon resource set");
    checkResource(layout, "WQSMCOA7.BAM", 9);
    checkResource(layout, "WQSMCOA9.BAM", 9);
    checkResource(layout, "WQSMCOCA.BAM", 72);
    checkResource(layout, "WQSMCOG1.BAM", 99);

    final AttackKind spearShield =
        modern.getAttackKind(WeaponType.ONE_HANDED_SPEAR, WeaponType.LARGE_SHIELD);
    check(spearShield == AttackKind.ONE_HANDED,
        "A one-handed spear plus shield must retain the one-handed attack layout");
    layout = modern.createOverlayLayout("WQS", "D4", WeaponType.LARGE_SHIELD, spearShield,
        OverlaySlot.SHIELD);
    check(layout.getResources().size() == 5,
        "A modern shield must contain the three one-handed attacks, casting and movement resources");
    checkResource(layout, "WQSD4A1.BAM", 9);
    checkResource(layout, "WQSD4A3.BAM", 9);
    checkResource(layout, "WQSD4A5.BAM", 9);
    checkResource(layout, "WQSD4CA.BAM", 72);
    checkResource(layout, "WQSD4G1.BAM", 99);

    layout = modern.createOverlayLayout("WQL", "CB", WeaponType.LIGHT_CROSSBOW);
    check(layout.getResources().size() == 3,
        "Modern crossbows must use SX plus casting and movement resources");
    checkResource(layout, "WQLCBSX.BAM", 9);
    checkResource(layout, "WQLCBCA.BAM", 72);
    checkResource(layout, "WQLCBG1.BAM", 99);
    check(layout.getResources().keySet().equals(
        modern.createOverlayLayout("WQL", "CB", WeaponType.HEAVY_CROSSBOW).getResources().keySet()),
        "Light and heavy crossbows must share the decoder's exact SX pose schema");

    layout = modern.createOverlayLayout("WQL", "BS", WeaponType.SHORTBOW);
    checkResource(layout, "WQLBSSA.BAM", 9);
    check(layout.getResources().keySet().equals(
        modern.createOverlayLayout("WQL", "BS", WeaponType.LONGBOW).getResources().keySet()),
        "Shortbows and longbows must share the decoder's exact SA pose schema");

    layout = modern.createOverlayLayout("WQL", "SL", WeaponType.SLING);
    checkResource(layout, "WQLSLSS.BAM", 9);
    check(layout.getResources().size() == 3,
        "Modern slings must use SS plus casting and movement resources");

    layout = EquipmentOverlayFamily.CHARACTER_OLD.createOverlayLayout("WPL", "SL", WeaponType.SLING);
    checkResource(layout, "WPLSLA1.BAM", 5);
    checkResource(layout, "WPLSLA1E.BAM", 8);
    check(layout.getResources().size() == 8,
        "Legacy slings must follow the decoder's one-handed slash fallback plus casting and movement pairs");

    layout = EquipmentOverlayFamily.CHARACTER_OLD.createOverlayLayout("WPL", "D1",
        WeaponType.BUCKLER, AttackKind.ONE_HANDED, OverlaySlot.SHIELD);
    checkResource(layout, "WPLD1A1.BAM", 5);
    checkResource(layout, "WPLD1A1E.BAM", 8);

    boolean rejected = false;
    try {
      EquipmentOverlayFamily.CHARACTER_OLD.validateLoadout(WeaponType.SWORD, WeaponType.MACE);
    } catch (IllegalArgumentException e) {
      rejected = e.getMessage().contains("two-weapon");
    }
    check(rejected, "Legacy character definitions must reject two-weapon requests explicitly");

    rejected = false;
    try {
      EquipmentOverlayFamily.MONSTER.validateLoadout(WeaponType.ONE_HANDED_SPEAR, WeaponType.BUCKLER);
    } catch (IllegalArgumentException e) {
      rejected = e.getMessage().contains("shield");
    }
    check(rejected, "Families without shield sprite segments must reject shield loadouts");

    rejected = false;
    try {
      modern.createOverlayLayout("WQS", "D4", WeaponType.LARGE_SHIELD, AttackKind.TWO_WEAPON,
          OverlaySlot.SHIELD);
    } catch (IllegalArgumentException e) {
      rejected = e.getMessage().contains("one-handed melee or sling");
    }
    check(rejected, "A shield layer must reject a mismatched two-weapon attack schema");
  }

  private static void testEquipmentProfileFormats() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-profile-test-");
    try {
      final CreatureAnimationModel model = createCompleteEquipmentModel(false);
      final EquipmentOverlayExporter.Config v2 = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2ToB).setResref("MSOL").setAppearanceCode("SY")
          .setOutputDirectory(directory).setBamFormat(BamFormat.BAM_V2).setCompressedBam(false);
      check(hasValidationMessage(EquipmentOverlayExporter.validate(model, v2), "does not support BAM V2"),
          "Classic equipment overlay export should reject BAM V2");

      final EquipmentOverlayExporter.Config unsupportedBamc = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG1).setResref("MSOL").setAppearanceCode("SY")
          .setOutputDirectory(directory).setBamFormat(BamFormat.BAM_V1).setCompressedBam(true);
      check(hasValidationMessage(EquipmentOverlayExporter.validate(model, unsupportedBamc),
          "does not support BAMC"), "BG1 equipment overlay export should reject BAMC");

      final EquipmentOverlayExporter.Config supportedBamc = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2ToB).setResref("MSOL").setAppearanceCode("SY")
          .setOutputDirectory(directory).setBamFormat(BamFormat.BAM_V1).setCompressedBam(true);
      check(!EquipmentOverlayExporter.validate(model, supportedBamc).hasErrors(),
          "Classic BG2 equipment overlay export should accept BAMC");
    } finally {
      deleteTree(directory);
    }
  }

  private static void testAllEquipmentOverlayFamilyBamV1Exports() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-family-test-");
    try {
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt("replace the sword with an ornate silver scythe");
      final EquipmentOverlayModel source = createCompleteEquipmentOverlayModel(false);
      final EquipmentOverlayModel avatar = createCompleteEquipmentOverlayModel(true);
      final EquipmentOverlayModel generated =
          EquipmentOverlayGenerator.generate(source, avatar, prompt, 113L, null);
      check(generated.getVariantCount(Sequence.CONJURE, 0) == 4,
          "Generation must preserve every distinct synchronized casting occurrence");

      for (final EquipmentOverlayFamily family : EquipmentOverlayFamily.values()) {
        final Path familyDirectory = directory.resolve(family.name().toLowerCase(java.util.Locale.ENGLISH));
        final String prefix = getEquipmentTestPrefix(family);
        final String appearance = family.usesFullAppearanceCodeInFileName() ? "SY" : "S0";
        final EquipmentOverlayExporter.Config config = new EquipmentOverlayExporter.Config()
            .setGame(Profile.Game.BG2EE).setFamily(family).setResourcePrefix(prefix).setAppearanceCode(appearance)
            .setWeaponType(WeaponType.SCYTHE).setOutputDirectory(familyDirectory)
            .setBamFormat(BamFormat.BAM_V1).setCompressedBam(false);
        check(!EquipmentOverlayExporter.validate(generated, config).hasErrors(),
            family + " should accept a complete sixteen-direction synchronized overlay");
        final EquipmentOverlayExporter.ExportResult result =
            EquipmentOverlayExporter.export(generated, config, false);
        final FamilyLayout layout = family.createOverlayLayout(prefix, appearance, WeaponType.SCYTHE);
        check(result.getInstalledFiles().size() == layout.getResources().size(),
            family + " should install exactly its planned BAM resources");
        for (final ResourcePlan resource : layout.getResources().values()) {
          final Path bam = familyDirectory.resolve(resource.getFileName());
          check(BamDecoder.getType(new FileResourceEntry(bam)) == BamDecoder.Type.BAMV1,
              resource.getFileName() + " should reopen as BAM V1");
          final BamDecoder decoder = BamDecoder.loadBam(new FileResourceEntry(bam));
          try {
            check(decoder.createControl().cycleCount() == resource.getCycleCount(),
                resource.getFileName() + " should retain its exact planned cycle count");
          } finally {
            decoder.close();
          }
        }

        if (family == EquipmentOverlayFamily.CHARACTER
            || family == EquipmentOverlayFamily.CHARACTER_OLD) {
          final EquipmentOverlayModel imported = EquipmentOverlayBamImporter.importLayout(layout,
              resourceName -> new FileResourceEntry(familyDirectory.resolve(resourceName)), true);
          check(imported.getVariantCount(Sequence.CONJURE, 0) == 4,
              family + " must preserve all four casting occurrences");
          check(imported.getVariantCount(Sequence.STAND, 0)
              == (family == EquipmentOverlayFamily.CHARACTER ? 3 : 2),
              family + " must preserve every distinct general-idle occurrence");
        }

        if (family == EquipmentOverlayFamily.MONSTER_ICEWIND) {
          final String omittedEast = prefix + family.getFileCode(appearance) + "WKE.BAM";
          final EquipmentOverlayModel imported = EquipmentOverlayBamImporter.importLayout(layout,
              resourceName -> resourceName.equals(omittedEast)
                  ? null : new FileResourceEntry(familyDirectory.resolve(resourceName)),
              true, family);
          check(imported.getFrames(Sequence.WALK, 12).isEmpty(),
              "The intentionally omitted Icewind eastern walk resource must remain absent");
          final List<AnimationFrame> fallback = imported.resolveFrames(Sequence.WALK, 12).getFrames();
          check(!fallback.isEmpty() && fallback.get(0).getSource().startsWith(prefix + "SWK.BAM#"),
              "A missing Icewind eastern action must mirror the exact matching western action");
        }
      }
    } finally {
      deleteTree(directory);
    }
  }

  private static void testEquipmentLoadoutBamRoundTrips() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-loadout-test-");
    try {
      final EquipmentOverlayModel source = createCompleteEquipmentOverlayModel(false);
      final EquipmentOverlayModel avatar = createCompleteEquipmentOverlayModel(true);

      final EquipmentOverlayGenerator.PromptSpec dual = EquipmentOverlayGenerator.parsePrompt(
          "a longsword in the main hand and a mace in the offhand");
      final EquipmentOverlayModel mainWeapon = EquipmentOverlayGenerator.generate(
          source, avatar, dual.forTarget(dual.getTargetWeapon()), 301L, false, null);
      final EquipmentOverlayModel offhandWeapon = EquipmentOverlayGenerator.generate(
          source, avatar, dual.forTarget(dual.getTargetOffhand()), 302L, false, null);
      final Path dualDirectory = directory.resolve("dual");
      final EquipmentOverlayExporter.Config dualConfig = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2EE).setFamily(EquipmentOverlayFamily.CHARACTER)
          .setResourcePrefix("WQL").setAppearanceCode("Z1").setWeaponType(WeaponType.SWORD)
          .setOffhandResourcePrefix("WQS").setOffhandAppearanceCode("Z2").setOffhandType(WeaponType.MACE)
          .setOutputDirectory(dualDirectory).setBamFormat(BamFormat.BAM_V1).setCompressedBam(false);
      check(!EquipmentOverlayExporter.validate(mainWeapon, offhandWeapon, dualConfig).hasErrors(),
          "A complete modern two-weapon loadout must pass combined validation");
      final EquipmentOverlayExporter.ExportResult dualResult =
          EquipmentOverlayExporter.export(mainWeapon, offhandWeapon, dualConfig, false);
      check(dualResult.getInstalledFiles().size() == 8,
          "Two-weapon export must atomically install four main-hand and four off-hand BAMs");
      checkBamCycles(dualDirectory.resolve("WQLZ1A7.BAM"), 9);
      checkBamCycles(dualDirectory.resolve("WQLZ1A9.BAM"), 9);
      checkBamCycles(dualDirectory.resolve("WQSZ2OA7.BAM"), 9);
      checkBamCycles(dualDirectory.resolve("WQSZ2OA9.BAM"), 9);

      final EquipmentOverlayGenerator.PromptSpec spearShield = EquipmentOverlayGenerator.parsePrompt(
          "a one-handed spear in the main hand and a large shield in the offhand");
      final EquipmentOverlayModel spear = EquipmentOverlayGenerator.generate(
          source, avatar, spearShield.forTarget(spearShield.getTargetWeapon()), 311L, false, null);
      final EquipmentOverlayModel shield = EquipmentOverlayGenerator.generate(
          source, avatar, spearShield.forTarget(spearShield.getTargetOffhand()), 312L, false, null);
      final Path shieldDirectory = directory.resolve("shield");
      final EquipmentOverlayExporter.Config shieldConfig = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2EE).setFamily(EquipmentOverlayFamily.CHARACTER)
          .setResourcePrefix("WQL").setAppearanceCode("P1").setWeaponType(WeaponType.ONE_HANDED_SPEAR)
          .setOffhandResourcePrefix("WQS").setOffhandAppearanceCode("D4")
          .setOffhandType(WeaponType.LARGE_SHIELD)
          .setOutputDirectory(shieldDirectory).setBamFormat(BamFormat.BAM_V1).setCompressedBam(false);
      check(!EquipmentOverlayExporter.validate(spear, shield, shieldConfig).hasErrors(),
          "A complete one-handed spear and shield loadout must pass combined validation");
      final EquipmentOverlayExporter.ExportResult shieldResult =
          EquipmentOverlayExporter.export(spear, shield, shieldConfig, false);
      check(shieldResult.getInstalledFiles().size() == 10,
          "Spear-and-shield export must atomically install two complete five-resource layers");
      checkBamCycles(shieldDirectory.resolve("WQLP1A1.BAM"), 9);
      checkBamCycles(shieldDirectory.resolve("WQSD4A1.BAM"), 9);
      checkBamCycles(shieldDirectory.resolve("WQSD4G1.BAM"), 99);
    } finally {
      deleteTree(directory);
    }
  }

  private static void testEquipmentExplicitEasternRoundTrip() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-east-test-");
    try {
      final EquipmentOverlayModel source = createCompleteEquipmentOverlayModel(false);
      source.replaceFrames(Sequence.STANCE, 4,
          Collections.singletonList(createLineFrame(false, "western-horizontal")));
      source.replaceFrames(Sequence.STANCE, 12,
          Collections.singletonList(createLineFrame(true, "eastern-vertical")));
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt("replace the sword with a silver sword");
      final EquipmentOverlayModel generated =
          EquipmentOverlayGenerator.generate(source, null, prompt, 127L, null);
      final AnimationFrame generatedWest = generated.getFrames(Sequence.STANCE, 4).get(0);
      final AnimationFrame generatedEast = generated.getFrames(Sequence.STANCE, 12).get(0);
      check(generatedWest.getImage().getWidth() > generatedWest.getImage().getHeight(),
          "The generated western weapon must retain its horizontal source axis");
      check(generatedEast.getImage().getHeight() > generatedEast.getImage().getWidth(),
          "The generated eastern weapon must retain its independent vertical source axis");

      final EquipmentOverlayExporter.Config config = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2EE)
          .setFamily(EquipmentOverlayFamily.MONSTER_LAYERED).setResourcePrefix("MLR")
          .setAppearanceCode("S0").setWeaponType(WeaponType.SWORD).setOutputDirectory(directory)
          .setBamFormat(BamFormat.BAM_V1).setCompressedBam(false);
      final FamilyLayout layout =
          EquipmentOverlayFamily.MONSTER_LAYERED.createOverlayLayout("MLR", "S0", WeaponType.SWORD);
      final EquipmentOverlayExporter.ExportResult result =
          EquipmentOverlayExporter.export(generated, config, false);
      check(result.getInstalledFiles().size() == 4,
          "The explicit-direction layered export must contain four BAM resources");

      final EquipmentOverlayModel imported = EquipmentOverlayBamImporter.importLayout(layout,
          resourceName -> new FileResourceEntry(directory.resolve(resourceName)), true);
      final AnimationFrame importedWest = imported.getFrames(Sequence.STANCE, 4).get(0);
      final AnimationFrame importedEast = imported.getFrames(Sequence.STANCE, 12).get(0);
      check(importedWest.getImage().getWidth() > importedWest.getImage().getHeight(),
          "The western axis must survive BAM export and import");
      check(importedEast.getImage().getHeight() > importedEast.getImage().getWidth(),
          "Independent eastern artwork must survive BAM export and import without replacement by a mirror");
    } finally {
      deleteTree(directory);
    }
  }

  private static void testEquipmentOverlayBamRoundTrip() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-overlay-test-");
    try {
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt("replace the sword with a large silver sickle");
      final CreatureAnimationModel generated = EquipmentOverlayGenerator.generate(
          createCompleteEquipmentModel(false), createCompleteEquipmentModel(true), prompt, 91L, null);
      final EquipmentOverlayExporter.Config config = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2EE).setResref("MSOL")
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
      final EquipmentOverlayExporter.Config config = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2EE).setResref("MSOL")
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

  private static void testEquipmentOverlayFamilyBamV2RoundTrip() throws Exception {
    final Path directory = createTestDirectory("ni-equipment-family-v2-test-");
    try {
      final EquipmentOverlayGenerator.PromptSpec prompt =
          EquipmentOverlayGenerator.parsePrompt("replace the sword with a glowing silver scythe");
      final EquipmentOverlayModel generated = EquipmentOverlayGenerator.generate(
          createCompleteEquipmentOverlayModel(false), createCompleteEquipmentOverlayModel(true),
          prompt, 131L, true, null);
      final EquipmentOverlayExporter.Config config = new EquipmentOverlayExporter.Config()
          .setGame(Profile.Game.BG2EE)
          .setFamily(EquipmentOverlayFamily.MONSTER_LAYERED).setResourcePrefix("MLR")
          .setAppearanceCode("S0").setWeaponType(WeaponType.SCYTHE).setOutputDirectory(directory)
          .setBamFormat(BamFormat.BAM_V2).setCompressedBam(false);
      final EquipmentOverlayExporter.ExportResult result =
          EquipmentOverlayExporter.export(generated, config, false);
      final FamilyLayout layout =
          EquipmentOverlayFamily.MONSTER_LAYERED.createOverlayLayout("MLR", "S0", WeaponType.SCYTHE);
      check(result.getInstalledFiles().size() > layout.getResources().size(),
          "Layered BAM V2 equipment output must include its BAMs and PVRZ texture pages");
      for (final ResourcePlan resource : layout.getResources().values()) {
        final Path bam = directory.resolve(resource.getFileName());
        check(BamDecoder.getType(new FileResourceEntry(bam)) == BamDecoder.Type.BAMV2,
            resource.getFileName() + " should reopen as BAM V2");
        CreatureAnimationExporter.validatePvrzReferences(directory, bam);
      }
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

  private static void fillIndexedImage(BufferedImage image, int paletteIndex) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        image.getRaster().setSample(x, y, 0, paletteIndex);
      }
    }
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

  private static EquipmentOverlayModel createCompleteEquipmentOverlayModel(boolean avatar) {
    final EquipmentOverlayModel result = new EquipmentOverlayModel(
        createCompleteEquipmentModel(avatar), createCompleteEquipmentModel(avatar));
    for (final Sequence sequence : Sequence.values()) {
      for (int directionIndex = 0; directionIndex < 16; directionIndex++) {
        final List<AnimationFrame> base = result.getFrames(sequence, directionIndex);
        for (int occurrence = 1; occurrence < 4; occurrence++) {
          result.replaceFrames(sequence, directionIndex, occurrence, base);
        }
      }
    }
    return result;
  }

  private static AnimationFrame createLineFrame(boolean vertical, String source) {
    final BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(new Color(230, 235, 240, 255));
      graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      if (vertical) {
        graphics.drawLine(24, 38, 24, 8);
      } else {
        graphics.drawLine(8, 24, 38, 24);
      }
    } finally {
      graphics.dispose();
    }
    return new AnimationFrame(image, new Point(24, 40), source);
  }

  private static String getEquipmentTestPrefix(EquipmentOverlayFamily family) {
    switch (family) {
      case CHARACTER:
        return "WQL";
      case CHARACTER_OLD:
        return "WPL";
      case MONSTER:
        return "MSOL";
      case MONSTER_LAYERED_SPELL:
        return "MSP";
      case MONSTER_LAYERED:
        return "MLR";
      case MONSTER_ICEWIND:
        return "MIL";
      default:
        throw new AssertionError("No test prefix for " + family);
    }
  }

  private static void checkResource(FamilyLayout layout, String fileName, int cycleCount) {
    final ResourcePlan resource = layout.getResources().get(fileName);
    check(resource != null, "Missing planned equipment resource " + fileName);
    check(resource.getCycleCount() == cycleCount,
        fileName + " should contain " + cycleCount + " cycles, not " + resource.getCycleCount());
  }

  private static void checkBamCycles(Path path, int cycleCount) throws Exception {
    check(Files.isRegularFile(path), "Missing exported equipment BAM " + path.getFileName());
    final BamDecoder decoder = BamDecoder.loadBam(new FileResourceEntry(path));
    check(decoder != null && decoder.isOpen(), path.getFileName() + " should reopen as a BAM");
    try {
      check(decoder.createControl().cycleCount() == cycleCount,
          path.getFileName() + " should contain exactly " + cycleCount + " cycles");
    } finally {
      decoder.close();
    }
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

  private static boolean hasValidationMessage(CreatureAnimationExporter.ValidationReport report, String text) {
    for (final CreatureAnimationExporter.Message message : report.getMessages()) {
      if (message.getText().contains(text)) {
        return true;
      }
    }
    return false;
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
