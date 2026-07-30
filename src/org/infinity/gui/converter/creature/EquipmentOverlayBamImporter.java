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
import java.util.List;
import java.util.Map;

import org.infinity.gui.converter.creature.CreatureAnimationFamily.CyclePlan;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.ResourcePlan;
import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamDecoder.BamControl;
import org.infinity.resource.graphics.BamDecoder.FrameEntry;
import org.infinity.resource.key.ResourceEntry;

/** Imports a declarative avatar or weapon-overlay resource layout into the equipment generator. */
public final class EquipmentOverlayBamImporter {
  /** Resolves a game resource by its complete filename. */
  public interface ResourceResolver {
    ResourceEntry getResourceEntry(String resourceName);
  }

  private EquipmentOverlayBamImporter() {
  }

  /**
   * Imports every planned resource and cycle.
   *
   * @param strict whether missing resources, cycles or frames must reject the layout
   */
  public static EquipmentOverlayModel importLayout(FamilyLayout layout, ResourceResolver resolver, boolean strict)
      throws Exception {
    return importLayout(layout, resolver, strict, null);
  }

  public static EquipmentOverlayModel importLayout(FamilyLayout layout, ResourceResolver resolver, boolean strict,
      EquipmentOverlayFamily family) throws Exception {
    if (layout == null || resolver == null) {
      throw new IllegalArgumentException("An animation layout and resource resolver are required.");
    }
    final EquipmentOverlayModel result = new EquipmentOverlayModel();
    final Map<CyclePlan, Integer> occurrences = EquipmentOverlayModel.getOccurrenceIndices(layout);
    int importedResources = 0;
    for (final ResourcePlan resource : layout.getResources().values()) {
      final ResourceEntry entry = resolver.getResourceEntry(resource.getFileName());
      if (entry == null) {
        if (strict && (family == null || !family.isOptionalResource(resource.getFileName()))) {
          throw new IOException("Required animation resource " + resource.getFileName() + " is unavailable.");
        }
        continue;
      }
      importResource(result, entry, resource, occurrences, strict);
      importedResources++;
    }
    if (importedResources == 0 || result.isEmpty()) {
      throw new IOException("None of the planned animation resources could be imported.");
    }
    return result;
  }

  public static boolean resourceFilesExist(FamilyLayout layout, Iterable<String> resourceNames) {
    return resourceFilesExist(layout, resourceNames, null);
  }

  public static boolean resourceFilesExist(FamilyLayout layout, Iterable<String> resourceNames,
      EquipmentOverlayFamily family) {
    if (layout == null || resourceNames == null) {
      return false;
    }
    final java.util.Set<String> names = new java.util.HashSet<>();
    for (final String name : resourceNames) {
      if (name != null) {
        names.add(name.trim().toUpperCase(java.util.Locale.ENGLISH));
      }
    }
    for (final String fileName : layout.getResources().keySet()) {
      if (!names.contains(fileName.toUpperCase(java.util.Locale.ENGLISH))
          && (family == null || !family.isOptionalResource(fileName))) {
        return false;
      }
    }
    return !layout.getResources().isEmpty();
  }

  /**
   * Returns whether every required resource, cycle and frame entry in a layout can be addressed.
   *
   * <p>This is deliberately stricter than {@link #resourceFilesExist(FamilyLayout, Iterable,
   * EquipmentOverlayFamily)}. Resource discovery uses it before selecting a source family so a filename-compatible
   * but cycle-incomplete BAM cannot mask a complete fallback family.</p>
   */
  static boolean isLayoutComplete(FamilyLayout layout, ResourceResolver resolver, EquipmentOverlayFamily family) {
    try {
      validateLayout(layout, resolver, family);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static void validateLayout(FamilyLayout layout, ResourceResolver resolver, EquipmentOverlayFamily family)
      throws Exception {
    if (layout == null || resolver == null) {
      throw new IllegalArgumentException("An animation layout and resource resolver are required.");
    }
    int validatedResources = 0;
    for (final ResourcePlan resource : layout.getResources().values()) {
      final ResourceEntry entry = resolver.getResourceEntry(resource.getFileName());
      if (entry == null) {
        if (family == null || !family.isOptionalResource(resource.getFileName())) {
          throw new IOException("Required animation resource " + resource.getFileName() + " is unavailable.");
        }
        continue;
      }
      validateResource(entry, resource);
      validatedResources++;
    }
    if (validatedResources == 0) {
      throw new IOException("None of the planned animation resources could be validated.");
    }
  }

  private static void validateResource(ResourceEntry resource, ResourcePlan plan) throws Exception {
    final BamDecoder decoder = BamDecoder.loadBam(resource);
    if (decoder == null || !decoder.isOpen()) {
      throw new IOException("Could not open " + resource.getResourceName() + " as a BAM resource.");
    }
    try {
      final BamControl control = decoder.createControl();
      for (final CyclePlan cycle : plan.getCycles()) {
        final int cycleIndex = cycle.getCycleIndex();
        if (cycleIndex >= control.cycleCount() || !control.cycleSet(cycleIndex)) {
          throw new IOException(resource.getResourceName() + " is missing required cycle " + cycleIndex + ".");
        }
        final int frameCount = control.cycleFrameCount();
        if (frameCount <= 0) {
          throw new IOException(resource.getResourceName() + " cycle " + cycleIndex + " contains no frames.");
        }
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
          final int absoluteIndex = control.cycleGetFrameIndexAbsolute(frameIndex);
          if (absoluteIndex < 0 || decoder.getFrameInfo(absoluteIndex) == null) {
            throw new IOException(resource.getResourceName() + " cycle " + cycleIndex + " frame " + frameIndex
                + " is unavailable.");
          }
        }
      }
    } finally {
      decoder.close();
    }
  }

  private static void importResource(EquipmentOverlayModel target, ResourceEntry resource, ResourcePlan plan,
      Map<CyclePlan, Integer> occurrences, boolean strict) throws Exception {
    final BamDecoder decoder = BamDecoder.loadBam(resource);
    if (decoder == null || !decoder.isOpen()) {
      throw new IOException("Could not open " + resource.getResourceName() + " as a BAM resource.");
    }
    try {
      final BamControl control = decoder.createControl();
      for (final CyclePlan cycle : plan.getCycles()) {
        final Integer occurrence = occurrences.get(cycle);
        if (occurrence == null) {
          throw new IllegalStateException("No occurrence index was planned for " + resource.getResourceName()
              + " cycle " + cycle.getCycleIndex() + ".");
        }
        final int cycleIndex = cycle.getCycleIndex();
        if (cycleIndex >= control.cycleCount() || !control.cycleSet(cycleIndex)) {
          if (strict) {
            throw new IOException(resource.getResourceName() + " is missing required cycle " + cycleIndex + ".");
          }
          continue;
        }
        final int frameCount = control.cycleFrameCount();
        if (frameCount <= 0) {
          if (strict) {
            throw new IOException(resource.getResourceName() + " cycle " + cycleIndex + " contains no frames.");
          }
          continue;
        }
        final List<AnimationFrame> frames = new ArrayList<>(frameCount);
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
          final int absoluteIndex = control.cycleGetFrameIndexAbsolute(frameIndex);
          final FrameEntry frameInfo = absoluteIndex >= 0 ? decoder.getFrameInfo(absoluteIndex) : null;
          final Image frameImage = control.cycleGetFrame(frameIndex);
          if (frameInfo == null || frameImage == null) {
            if (strict) {
              throw new IOException(resource.getResourceName() + " cycle " + cycleIndex + " frame " + frameIndex
                  + " could not be decoded.");
            }
            continue;
          }
          AnimationFrame frame = new AnimationFrame(
              copyImage(frameImage, frameInfo.getWidth(), frameInfo.getHeight()),
              new Point(frameInfo.getCenterX(), frameInfo.getCenterY()),
              resource.getResourceName() + "#" + cycleIndex + "/" + frameIndex);
          if (cycle.isMirrored()) {
            frame = EquipmentOverlayModel.canonicalizeEasternFrame(frame);
          }
          frames.add(frame);
        }
        if (frames.isEmpty()) {
          if (strict) {
            throw new IOException(resource.getResourceName() + " cycle " + cycleIndex
                + " contains no decodable frames.");
          }
          continue;
        }
        if (!target.hasFrames(cycle.getSequence(), cycle.getDirectionIndex(), occurrence)) {
          target.replaceFrames(cycle.getSequence(), cycle.getDirectionIndex(), occurrence, frames);
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
}
