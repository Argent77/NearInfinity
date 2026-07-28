// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.infinity.gui.converter.creature.CreatureAnimationFamily.CyclePlan;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.CreatureAnimationFamily.ResourcePlan;
import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.CreatureAnimationModel.ResolvedFrames;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;

/**
 * Equipment-animation source with independent western and eastern artwork.
 *
 * <p>The creator's neutral model stores the nine western orientations used by type {@code 0x7000}. Animation
 * families with explicit eastern BAM resources need seven additional orientations. Eastern frames are stored in
 * horizontally canonicalized form so the existing renderer and BAM transformation code can mirror them back without
 * changing their palette indices or center points.</p>
 */
public final class EquipmentOverlayModel {
  private final CreatureAnimationModel western;
  private final CreatureAnimationModel eastern;
  private final VariantStore westernVariants;
  private final VariantStore easternVariants;

  public EquipmentOverlayModel() {
    this(new CreatureAnimationModel(), new CreatureAnimationModel());
  }

  public EquipmentOverlayModel(CreatureAnimationModel western, CreatureAnimationModel eastern) {
    this.western = Objects.requireNonNull(western, "Western animation model cannot be null");
    this.eastern = Objects.requireNonNull(eastern, "Eastern animation model cannot be null");
    westernVariants = new VariantStore(western);
    easternVariants = new VariantStore(eastern);
  }

  public static EquipmentOverlayModel fromWestern(CreatureAnimationModel model) {
    return new EquipmentOverlayModel(model != null ? model : new CreatureAnimationModel(),
        new CreatureAnimationModel());
  }

  public CreatureAnimationModel getWesternModel() {
    return western;
  }

  public CreatureAnimationModel getEasternModel() {
    return eastern;
  }

  public boolean isEmpty() {
    return western.isEmpty() && eastern.isEmpty();
  }

  public int getFrameCount() {
    return westernVariants.getFrameCount() + easternVariants.getFrameCount();
  }

  public int getPopulatedCellCount() {
    return western.getPopulatedCellCount() + eastern.getPopulatedCellCount();
  }

  public int getPopulatedVariantCount() {
    return westernVariants.getPopulatedVariantCount() + easternVariants.getPopulatedVariantCount();
  }

  public boolean hasFrames(Sequence sequence, int directionIndex) {
    return hasFrames(sequence, directionIndex, 0);
  }

  public boolean hasFrames(Sequence sequence, int directionIndex, int occurrence) {
    return !getFrames(sequence, directionIndex, occurrence).isEmpty();
  }

  public List<AnimationFrame> getFrames(Sequence sequence, int directionIndex) {
    return getFrames(sequence, directionIndex, 0);
  }

  public List<AnimationFrame> getFrames(Sequence sequence, int directionIndex, int occurrence) {
    final Direction direction = getCanonicalDirection(directionIndex);
    return getDirectionalStore(directionIndex).getFrames(sequence, direction, occurrence);
  }

  public int getVariantCount(Sequence sequence, int directionIndex) {
    final Direction direction = getCanonicalDirection(directionIndex);
    return getDirectionalStore(directionIndex).getVariantCount(sequence, direction);
  }

  public int getResolvedVariantCount(Sequence sequence, int directionIndex) {
    final int exact = getVariantCount(sequence, directionIndex);
    if (exact > 0 || directionIndex <= Direction.N.getCycleOffset()) {
      return exact;
    }
    return getVariantCount(sequence, 16 - directionIndex);
  }

  public List<AnimationFrame> resolveVariantFrames(Sequence sequence, int directionIndex, int occurrence) {
    final List<AnimationFrame> exact = getFrames(sequence, directionIndex, occurrence);
    if (!exact.isEmpty()) {
      return exact;
    }
    if (directionIndex > Direction.N.getCycleOffset()) {
      final List<AnimationFrame> westernFrames = getFrames(sequence, 16 - directionIndex, occurrence);
      if (!westernFrames.isEmpty()) {
        return westernFrames;
      }
    }
    return occurrence == 0 ? resolveFrames(sequence, directionIndex).getFrames()
        : Collections.<AnimationFrame>emptyList();
  }

  public ResolvedFrames resolveFrames(Sequence sequence, int directionIndex) {
    final Direction direction = getCanonicalDirection(directionIndex);
    if (directionIndex > Direction.N.getCycleOffset()) {
      // Icewind overlays may omit individual eastern BAMs. In that case the decoder mirrors the exact matching
      // western action; it must not borrow a different action or direction merely because another eastern BAM exists.
      if (eastern.hasFrames(sequence, direction)) {
        return eastern.resolveFrames(sequence, direction);
      }
      return western.resolveFrames(sequence, direction);
    }
    return western.resolveFrames(sequence, direction);
  }

  public void replaceFrames(Sequence sequence, int directionIndex, List<AnimationFrame> frames) {
    replaceFrames(sequence, directionIndex, 0, frames);
  }

  public void replaceFrames(Sequence sequence, int directionIndex, int occurrence, List<AnimationFrame> frames) {
    final Direction direction = getCanonicalDirection(directionIndex);
    getDirectionalStore(directionIndex).replaceFrames(sequence, direction, occurrence, frames);
  }

  private VariantStore getDirectionalStore(int directionIndex) {
    validateDirectionIndex(directionIndex);
    return directionIndex > Direction.N.getCycleOffset() ? easternVariants : westernVariants;
  }

  private static Direction getCanonicalDirection(int directionIndex) {
    validateDirectionIndex(directionIndex);
    final int canonicalIndex = directionIndex > Direction.N.getCycleOffset() ? 16 - directionIndex : directionIndex;
    return Direction.values()[canonicalIndex];
  }

  static AnimationFrame canonicalizeEasternFrame(AnimationFrame frame) {
    Objects.requireNonNull(frame, "Eastern animation frame cannot be null");
    final BufferedImage source = frame.getImage();
    final int width = source.getWidth();
    final int height = source.getHeight();
    final WritableRaster raster = source.getRaster().createCompatibleWritableRaster(width, height);
    final BufferedImage target =
        new BufferedImage(source.getColorModel(), raster, source.isAlphaPremultiplied(), null);
    Object pixel = null;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        pixel = source.getRaster().getDataElements(x, y, pixel);
        target.getRaster().setDataElements(width - 1 - x, y, pixel);
      }
    }
    final Point center = frame.getCenter();
    center.x = width - 1 - center.x;
    return new AnimationFrame(target, center, frame.getSource());
  }

  private static void validateDirectionIndex(int directionIndex) {
    if (directionIndex < 0 || directionIndex > 15) {
      throw new IllegalArgumentException("Direction index must be in the range 0-15.");
    }
  }

  static Map<CyclePlan, Integer> getOccurrenceIndices(FamilyLayout layout) {
    Objects.requireNonNull(layout, "Animation layout cannot be null");
    final EnumMap<Sequence, int[]> counts = new EnumMap<>(Sequence.class);
    for (final Sequence sequence : Sequence.values()) {
      counts.put(sequence, new int[16]);
    }
    final Map<CyclePlan, Integer> result = new IdentityHashMap<>();
    for (final ResourcePlan resource : layout.getResources().values()) {
      for (final CyclePlan cycle : resource.getCycles()) {
        final int[] directions = counts.get(cycle.getSequence());
        final int occurrence = directions[cycle.getDirectionIndex()]++;
        result.put(cycle, occurrence);
      }
    }
    return result;
  }

  private static final class VariantStore {
    private final CreatureAnimationModel primary;
    private final EnumMap<Sequence, EnumMap<Direction, List<List<AnimationFrame>>>> variants =
        new EnumMap<>(Sequence.class);

    private VariantStore(CreatureAnimationModel primary) {
      this.primary = primary;
      for (final Sequence sequence : Sequence.values()) {
        final EnumMap<Direction, List<List<AnimationFrame>>> directions = new EnumMap<>(Direction.class);
        for (final Direction direction : Direction.values()) {
          final List<List<AnimationFrame>> values = new ArrayList<>();
          final List<AnimationFrame> frames = primary.getFrames(sequence, direction);
          if (!frames.isEmpty()) {
            values.add(frames);
          }
          directions.put(direction, values);
        }
        variants.put(sequence, directions);
      }
    }

    private List<AnimationFrame> getFrames(Sequence sequence, Direction direction, int occurrence) {
      if (sequence == null || direction == null || occurrence < 0) {
        return Collections.emptyList();
      }
      final List<List<AnimationFrame>> values = variants.get(sequence).get(direction);
      return occurrence < values.size() ? values.get(occurrence) : Collections.<AnimationFrame>emptyList();
    }

    private int getVariantCount(Sequence sequence, Direction direction) {
      if (sequence == null || direction == null) {
        return 0;
      }
      return variants.get(sequence).get(direction).size();
    }

    private void replaceFrames(Sequence sequence, Direction direction, int occurrence, List<AnimationFrame> frames) {
      Objects.requireNonNull(sequence, "Animation sequence cannot be null");
      Objects.requireNonNull(direction, "Animation direction cannot be null");
      if (occurrence < 0) {
        throw new IllegalArgumentException("Cycle occurrence cannot be negative.");
      }
      final List<AnimationFrame> copy = new ArrayList<>();
      if (frames != null) {
        for (final AnimationFrame frame : frames) {
          copy.add(Objects.requireNonNull(frame, "Animation frame cannot be null"));
        }
      }
      final List<List<AnimationFrame>> values = variants.get(sequence).get(direction);
      while (values.size() <= occurrence) {
        values.add(Collections.<AnimationFrame>emptyList());
      }
      final List<AnimationFrame> immutable = Collections.unmodifiableList(copy);
      values.set(occurrence, immutable);
      if (occurrence == 0) {
        primary.replaceFrames(sequence, direction, immutable);
      }
    }

    private int getFrameCount() {
      int result = 0;
      for (final Map<Direction, List<List<AnimationFrame>>> directions : variants.values()) {
        for (final List<List<AnimationFrame>> values : directions.values()) {
          for (final List<AnimationFrame> frames : values) {
            result += frames.size();
          }
        }
      }
      return result;
    }

    private int getPopulatedVariantCount() {
      int result = 0;
      for (final Map<Direction, List<List<AnimationFrame>>> directions : variants.values()) {
        for (final List<List<AnimationFrame>> values : directions.values()) {
          for (final List<AnimationFrame> frames : values) {
            if (!frames.isEmpty()) {
              result++;
            }
          }
        }
      }
      return result;
    }
  }
}
