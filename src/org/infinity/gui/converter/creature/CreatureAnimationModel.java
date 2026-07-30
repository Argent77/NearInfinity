// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;

/** In-memory source model used by the creature animation creator. */
public final class CreatureAnimationModel {
  /** One rendered source frame and its BAM center point. */
  public static final class AnimationFrame {
    private final BufferedImage image;
    private final Point center;
    private final String source;

    public AnimationFrame(BufferedImage image, Point center, String source) {
      this.image = Objects.requireNonNull(image, "Frame image cannot be null");
      this.center = new Point(Objects.requireNonNull(center, "Frame center cannot be null"));
      this.source = (source != null) ? source : "";
    }

    public BufferedImage getImage() {
      return image;
    }

    public Point getCenter() {
      return new Point(center);
    }

    public String getSource() {
      return source;
    }
  }

  /** Result of resolving a requested sequence and direction, potentially through a documented fallback. */
  public static final class ResolvedFrames {
    private final Sequence requestedSequence;
    private final Direction requestedDirection;
    private final Sequence resolvedSequence;
    private final Direction resolvedDirection;
    private final List<AnimationFrame> frames;

    private ResolvedFrames(Sequence requestedSequence, Direction requestedDirection, Sequence resolvedSequence,
        Direction resolvedDirection, List<AnimationFrame> frames) {
      this.requestedSequence = requestedSequence;
      this.requestedDirection = requestedDirection;
      this.resolvedSequence = resolvedSequence;
      this.resolvedDirection = resolvedDirection;
      this.frames = frames;
    }

    public Sequence getRequestedSequence() {
      return requestedSequence;
    }

    public Direction getRequestedDirection() {
      return requestedDirection;
    }

    public Sequence getResolvedSequence() {
      return resolvedSequence;
    }

    public Direction getResolvedDirection() {
      return resolvedDirection;
    }

    public List<AnimationFrame> getFrames() {
      return frames;
    }

    public boolean isFallback() {
      return requestedSequence != resolvedSequence || requestedDirection != resolvedDirection;
    }
  }

  private final EnumMap<Sequence, EnumMap<Direction, List<AnimationFrame>>> content =
      new EnumMap<>(Sequence.class);

  public CreatureAnimationModel() {
    for (final Sequence sequence : Sequence.values()) {
      final EnumMap<Direction, List<AnimationFrame>> directions = new EnumMap<>(Direction.class);
      for (final Direction direction : Direction.values()) {
        directions.put(direction, Collections.<AnimationFrame>emptyList());
      }
      content.put(sequence, directions);
    }
  }

  public void clear() {
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        content.get(sequence).put(direction, Collections.<AnimationFrame>emptyList());
      }
    }
  }

  public void replaceFrames(Sequence sequence, Direction direction, List<AnimationFrame> frames) {
    Objects.requireNonNull(sequence);
    Objects.requireNonNull(direction);
    final List<AnimationFrame> copy = new ArrayList<>();
    if (frames != null) {
      for (final AnimationFrame frame : frames) {
        copy.add(Objects.requireNonNull(frame));
      }
    }
    content.get(sequence).put(direction, Collections.unmodifiableList(copy));
  }

  public List<AnimationFrame> getFrames(Sequence sequence, Direction direction) {
    if (sequence == null || direction == null) {
      return Collections.emptyList();
    }
    return content.get(sequence).get(direction);
  }

  public boolean hasFrames(Sequence sequence, Direction direction) {
    return !getFrames(sequence, direction).isEmpty();
  }

  public boolean isEmpty() {
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        if (hasFrames(sequence, direction)) {
          return false;
        }
      }
    }
    return true;
  }

  public int getPopulatedCellCount() {
    int count = 0;
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        if (hasFrames(sequence, direction)) {
          count++;
        }
      }
    }
    return count;
  }

  public int getFrameCount() {
    int count = 0;
    for (final Sequence sequence : Sequence.values()) {
      for (final Direction direction : Direction.values()) {
        count += getFrames(sequence, direction).size();
      }
    }
    return count;
  }

  public Map<Direction, List<AnimationFrame>> getSequence(Sequence sequence) {
    return Collections.unmodifiableMap(content.get(sequence));
  }

  /**
   * Resolves missing input deterministically. Exact action/direction data is always preferred, followed by the nearest
   * available stored direction of the same action and then the documented action fallback order.
   */
  public ResolvedFrames resolveFrames(Sequence sequence, Direction direction) {
    Objects.requireNonNull(sequence);
    Objects.requireNonNull(direction);

    for (final Sequence candidate : MonsterAnimationLayout.getFallbackOrder(sequence)) {
      final List<AnimationFrame> exact = getFrames(candidate, direction);
      if (!exact.isEmpty()) {
        return new ResolvedFrames(sequence, direction, candidate, direction, exact);
      }

      Direction nearest = null;
      int nearestDistance = Integer.MAX_VALUE;
      for (final Direction possible : Direction.values()) {
        if (hasFrames(candidate, possible)) {
          final int distance = Math.abs(possible.getCycleOffset() - direction.getCycleOffset());
          if (distance < nearestDistance) {
            nearest = possible;
            nearestDistance = distance;
          }
        }
      }
      if (nearest != null) {
        return new ResolvedFrames(sequence, direction, candidate, nearest, getFrames(candidate, nearest));
      }
    }

    for (final Sequence candidate : Sequence.values()) {
      for (final Direction possible : Direction.values()) {
        final List<AnimationFrame> frames = getFrames(candidate, possible);
        if (!frames.isEmpty()) {
          return new ResolvedFrames(sequence, direction, candidate, possible, frames);
        }
      }
    }

    return new ResolvedFrames(sequence, direction, sequence, direction, Collections.<AnimationFrame>emptyList());
  }
}
