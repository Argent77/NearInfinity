// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.OutputSlot;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamDecoder.BamControl;
import org.infinity.resource.graphics.BamDecoder.FrameEntry;
import org.infinity.resource.key.ResourceEntry;

/** Imports type 0x7000 avatar or equipment-overlay BAM resources into the creator model. */
public final class MonsterAnimationBamImporter {
  /** Resolves a game resource by its complete filename. */
  public interface ResourceResolver {
    ResourceEntry getResourceEntry(String resourceName);
  }

  private MonsterAnimationBamImporter() {
  }

  /**
   * Imports a type 0x7000 avatar family from the active game's resource resolver.
   *
   * <p>Missing split subresources are tolerated so that partially installed mod animations can still be inspected.
   * At least one source BAM must be available.</p>
   */
  public static CreatureAnimationModel importAnimation(String resref, boolean splitBams, ResourceResolver resolver)
      throws Exception {
    final String normalized = normalizeResref(resref);
    final Map<String, ResourceEntry> resources = new LinkedHashMap<>();
    for (final String suffix : MonsterAnimationLayout.getOutputLayout(splitBams).keySet()) {
      final String name = normalized + suffix + ".BAM";
      final ResourceEntry entry = resolver.getResourceEntry(name);
      if (entry != null) {
        resources.put(suffix, entry);
      }
    }
    if (resources.isEmpty()) {
      throw new IOException("No type 0x7000 BAM resources were found for " + normalized + ".");
    }

    final CreatureAnimationModel model =
        importLayout(MonsterAnimationLayout.getOutputLayout(splitBams), resources);
    if (splitBams) {
      populateSplitLieDownSequences(model);
    }
    return model;
  }

  /** Imports the always-unsplit G1/G2 equipment overlay associated with a two-character appearance code. */
  public static CreatureAnimationModel importEquipmentOverlay(String resref, String appearanceCode,
      ResourceResolver resolver) throws Exception {
    final String normalizedResref = normalizeResref(resref);
    final String normalizedCode = normalizeAppearanceCode(appearanceCode);
    final ResourceEntry group1 =
        resolver.getResourceEntry(normalizedResref + "G1" + normalizedCode + ".BAM");
    final ResourceEntry group2 =
        resolver.getResourceEntry(normalizedResref + "G2" + normalizedCode + ".BAM");
    if (group1 == null || group2 == null) {
      throw new IOException("Equipment appearance " + normalizedCode + " for " + normalizedResref
          + " requires both G1 and G2 overlay BAM resources.");
    }
    return importUnsplit(group1, group2);
  }

  /** Imports an unsplit G1/G2 family. Primarily useful for equipment overlays and round-trip tests. */
  public static CreatureAnimationModel importUnsplit(ResourceEntry group1, ResourceEntry group2) throws Exception {
    if (group1 == null || group2 == null) {
      throw new IllegalArgumentException("Both G1 and G2 BAM resources are required.");
    }
    final Map<String, ResourceEntry> resources = new LinkedHashMap<>();
    resources.put("G1", group1);
    resources.put("G2", group2);
    return importLayout(MonsterAnimationLayout.getOutputLayout(false), resources);
  }

  private static CreatureAnimationModel importLayout(Map<String, List<OutputSlot>> layout,
      Map<String, ResourceEntry> resources) throws Exception {
    final CreatureAnimationModel result = new CreatureAnimationModel();
    for (final Map.Entry<String, List<OutputSlot>> layoutEntry : layout.entrySet()) {
      final ResourceEntry resource = resources.get(layoutEntry.getKey());
      if (resource == null) {
        continue;
      }
      importResource(result, resource, layoutEntry.getValue());
    }
    return result;
  }

  private static void importResource(CreatureAnimationModel target, ResourceEntry resource, List<OutputSlot> slots)
      throws Exception {
    final BamDecoder decoder = BamDecoder.loadBam(resource);
    if (decoder == null || !decoder.isOpen()) {
      throw new IOException("Could not open " + resource.getResourceName() + " as a BAM resource.");
    }
    try {
      final BamControl control = decoder.createControl();
      for (final OutputSlot slot : slots) {
        for (final Direction direction : Direction.values()) {
          final int cycleIndex = slot.getCycleOffset() + direction.getCycleOffset();
          if (cycleIndex >= control.cycleCount() || !control.cycleSet(cycleIndex)) {
            continue;
          }
          final int frameCount = control.cycleFrameCount();
          final List<AnimationFrame> frames = new ArrayList<>(frameCount);
          for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            final int absoluteIndex = control.cycleGetFrameIndexAbsolute(frameIndex);
            if (absoluteIndex < 0) {
              continue;
            }
            final FrameEntry frameInfo = decoder.getFrameInfo(absoluteIndex);
            final Image frameImage = control.cycleGetFrame(frameIndex);
            if (frameInfo == null || frameImage == null) {
              continue;
            }
            final BufferedImage image = copyImage(frameImage, frameInfo.getWidth(), frameInfo.getHeight());
            frames.add(new AnimationFrame(image, new Point(frameInfo.getCenterX(), frameInfo.getCenterY()),
                resource.getResourceName() + "#" + cycleIndex + "/" + frameIndex));
          }
          if (!frames.isEmpty()) {
            target.replaceFrames(slot.getSequence(), direction, frames);
          }
        }
      }
    } finally {
      decoder.close();
    }
  }

  private static BufferedImage copyImage(Image source, int expectedWidth, int expectedHeight) {
    final int width = Math.max(1, source.getWidth(null) > 0 ? source.getWidth(null) : expectedWidth);
    final int height = Math.max(1, source.getHeight(null) > 0 ? source.getHeight(null) : expectedHeight);
    final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = result.createGraphics();
    try {
      graphics.drawImage(source, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return result;
  }

  private static void populateSplitLieDownSequences(CreatureAnimationModel model) {
    for (final Direction direction : Direction.values()) {
      final List<AnimationFrame> death = model.getFrames(Sequence.DIE, direction);
      if (death.isEmpty()) {
        continue;
      }
      if (!model.hasFrames(Sequence.SLEEP, direction)) {
        model.replaceFrames(Sequence.SLEEP, direction, death);
      }
      if (!model.hasFrames(Sequence.GET_UP, direction)) {
        final List<AnimationFrame> reversed = new ArrayList<>(death);
        Collections.reverse(reversed);
        model.replaceFrames(Sequence.GET_UP, direction, reversed);
      }
    }
  }

  private static String normalizeResref(String value) {
    final String normalized = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
    if (!normalized.matches("[A-Z0-9_]{1,4}")) {
      throw new IllegalArgumentException("Type 0x7000 BAM resrefs must contain 1-4 ASCII letters, digits or underscores.");
    }
    return normalized;
  }

  private static String normalizeAppearanceCode(String value) {
    final String normalized = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
    if (!normalized.matches("[A-Z0-9_]{2}")) {
      throw new IllegalArgumentException("Equipment appearance codes must contain exactly two ASCII characters.");
    }
    return normalized;
  }
}
