// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

import org.infinity.gui.converter.creature.CreatureAnimationModel.AnimationFrame;
import org.infinity.gui.converter.creature.CreatureAnimationModel.ResolvedFrames;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Direction;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;

/** Animated, pivot-aware preview for stored and engine-mirrored creature directions. */
public final class AnimationPreviewPanel extends JPanel {
  private static final long serialVersionUID = 1L;

  static final int MIN_FRAME_RATE = 1;
  static final int MAX_FRAME_RATE = 60;
  static final int DEFAULT_FRAME_RATE = 15;
  static final int MIN_ZOOM_PERCENT = 25;
  static final int MAX_ZOOM_PERCENT = 500;
  static final int DEFAULT_ZOOM_PERCENT = 100;

  public static final String[] PREVIEW_DIRECTIONS = {
      "S - South",
      "SSW - South-southwest",
      "SW - Southwest",
      "WSW - West-southwest",
      "W - West",
      "WNW - West-northwest",
      "NW - Northwest",
      "NNW - North-northwest",
      "N - North",
      "NNE - North-northeast",
      "NE - Northeast",
      "ENE - East-northeast",
      "E - East",
      "ESE - East-southeast",
      "SE - Southeast",
      "SSE - South-southeast"
  };

  private final Timer timer;
  private CreatureAnimationModel model = new CreatureAnimationModel();
  private CreatureAnimationModel easternModel;
  private CreatureAnimationModel overlayModel;
  private CreatureAnimationModel overlayEasternModel;
  private CreatureAnimationModel offhandOverlayModel;
  private CreatureAnimationModel offhandOverlayEasternModel;
  private Sequence sequence = Sequence.WALK;
  private int directionIndex;
  private int frameIndex;
  private boolean playing = true;
  private boolean showPivot = true;
  private int frameRate = DEFAULT_FRAME_RATE;
  private int zoomPercent = DEFAULT_ZOOM_PERCENT;

  public AnimationPreviewPanel() {
    setOpaque(true);
    setBackground(new Color(45, 48, 53));
    setPreferredSize(new Dimension(500, 460));
    setMinimumSize(new Dimension(300, 260));
    timer = new Timer(getFrameDelay(frameRate), event -> advanceFrame());
    timer.start();
  }

  public void setModel(CreatureAnimationModel model) {
    this.model = (model != null) ? model : new CreatureAnimationModel();
    easternModel = null;
    frameIndex = 0;
    repaint();
  }

  /** Sets optional canonicalized frames for animation families that store explicit eastern directions. */
  public void setEasternModel(CreatureAnimationModel easternModel) {
    this.easternModel = easternModel;
    frameIndex = 0;
    repaint();
  }

  /** Sets an optional synchronized layer that is rendered over the primary creature model. */
  public void setOverlayModel(CreatureAnimationModel overlayModel) {
    this.overlayModel = overlayModel;
    overlayEasternModel = null;
    frameIndex = 0;
    repaint();
  }

  /** Sets optional canonicalized eastern frames for the synchronized layer. */
  public void setOverlayEasternModel(CreatureAnimationModel overlayEasternModel) {
    this.overlayEasternModel = overlayEasternModel;
    frameIndex = 0;
    repaint();
  }

  /** Sets an optional shield or left-handed weapon layer rendered before the main-hand overlay. */
  public void setOffhandOverlayModel(CreatureAnimationModel offhandOverlayModel) {
    this.offhandOverlayModel = offhandOverlayModel;
    offhandOverlayEasternModel = null;
    frameIndex = 0;
    repaint();
  }

  /** Sets optional canonicalized eastern frames for the off-hand layer. */
  public void setOffhandOverlayEasternModel(CreatureAnimationModel offhandOverlayEasternModel) {
    this.offhandOverlayEasternModel = offhandOverlayEasternModel;
    frameIndex = 0;
    repaint();
  }

  public void setSequence(Sequence sequence) {
    if (sequence != null && this.sequence != sequence) {
      this.sequence = sequence;
      frameIndex = 0;
      repaint();
    }
  }

  public void setDirectionIndex(int directionIndex) {
    final int normalized = Math.max(0, Math.min(PREVIEW_DIRECTIONS.length - 1, directionIndex));
    if (this.directionIndex != normalized) {
      this.directionIndex = normalized;
      frameIndex = 0;
      repaint();
    }
  }

  public void setPlaying(boolean playing) {
    this.playing = playing;
    if (playing) {
      timer.start();
    } else {
      timer.stop();
    }
  }

  public boolean isPlaying() {
    return playing;
  }

  public void setFrameRate(int frameRate) {
    if (frameRate < MIN_FRAME_RATE || frameRate > MAX_FRAME_RATE) {
      throw new IllegalArgumentException("Frame rate must be between " + MIN_FRAME_RATE + " and "
          + MAX_FRAME_RATE + " frames per second.");
    }
    this.frameRate = frameRate;
    final int delay = getFrameDelay(frameRate);
    timer.setDelay(delay);
    timer.setInitialDelay(delay);
  }

  public int getFrameRate() {
    return frameRate;
  }

  public void setZoomPercent(int zoomPercent) {
    if (zoomPercent < MIN_ZOOM_PERCENT || zoomPercent > MAX_ZOOM_PERCENT) {
      throw new IllegalArgumentException("Zoom must be between " + MIN_ZOOM_PERCENT + "% and "
          + MAX_ZOOM_PERCENT + "%.");
    }
    this.zoomPercent = zoomPercent;
    repaint();
  }

  public int getZoomPercent() {
    return zoomPercent;
  }

  public void setShowPivot(boolean showPivot) {
    this.showPivot = showPivot;
    repaint();
  }

  public String getStatusText() {
    final PreviewFrames preview = getPreviewFrames();
    if (preview.getFrameCount() == 0) {
      return sequence.getCode() + " / " + PREVIEW_DIRECTIONS[directionIndex] + " - no frames";
    }
    final String fallback = preview.resolved != null && preview.resolved.isFallback()
        ? " - fallback " + preview.resolved.getResolvedSequence().getCode() + "/"
            + preview.resolved.getResolvedDirection().getCode()
        : "";
    return sequence.getCode() + " / " + PREVIEW_DIRECTIONS[directionIndex] + " - "
        + (frameIndex % preview.getFrameCount() + 1) + "/" + preview.getFrameCount()
        + (!preview.overlayFrames.isEmpty() || !preview.offhandOverlayFrames.isEmpty()
            ? " + equipment overlay" : "")
        + fallback;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    final Graphics2D g = (Graphics2D) graphics.create();
    try {
      drawCheckerboard(g);
      final PreviewFrames preview = getPreviewFrames();
      if (preview.getFrameCount() == 0) {
        drawEmptyMessage(g);
        return;
      }

      final AnimationFrame frame = selectFrame(preview.frames, frameIndex, preview.getFrameCount());
      final AnimationFrame overlay = selectFrame(preview.overlayFrames, frameIndex, preview.getFrameCount());
      final AnimationFrame offhand =
          selectFrame(preview.offhandOverlayFrames, frameIndex, preview.getFrameCount());
      final java.awt.Rectangle bounds = getSharedBounds(preview.mirrored, frame, offhand, overlay);
      final double availableWidth = Math.max(1.0, getWidth() - 56.0);
      final double availableHeight = Math.max(1.0, getHeight() - 56.0);
      double fitScale = Math.min(availableWidth / Math.max(1, bounds.width),
          availableHeight / Math.max(1, bounds.height));
      fitScale = Math.max(0.1, Math.min(5.0, fitScale));
      final double scale = fitScale * zoomPercent / 100.0;
      final int renderedWidth = Math.max(1, (int) Math.round(bounds.width * scale));
      final int renderedHeight = Math.max(1, (int) Math.round(bounds.height * scale));
      final int originX = (getWidth() - renderedWidth) / 2 - (int) Math.round(bounds.x * scale);
      final int originY = (getHeight() - renderedHeight) / 2 - (int) Math.round(bounds.y * scale);

      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          scale >= 1.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
              : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      drawFrame(g, frame, originX, originY, scale, preview.mirrored);
      drawFrame(g, offhand, originX, originY, scale, preview.mirrored);
      drawFrame(g, overlay, originX, originY, scale, preview.mirrored);

      if (showPivot) {
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(new Color(255, 206, 72, 185));
        g.drawLine(12, originY, getWidth() - 12, originY);
        g.setColor(new Color(85, 218, 255, 215));
        g.drawLine(originX - 8, originY, originX + 8, originY);
        g.drawLine(originX, originY - 8, originX, originY + 8);
      }
    } finally {
      g.dispose();
    }
  }

  @Override
  public void removeNotify() {
    timer.stop();
    super.removeNotify();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (playing) {
      timer.start();
    }
  }

  private void advanceFrame() {
    final int count = getPreviewFrames().getFrameCount();
    if (count > 0) {
      frameIndex = (frameIndex + 1) % count;
      repaint();
      firePropertyChange("frameStatus", null, getStatusText());
    }
  }

  static int getFrameDelay(int frameRate) {
    if (frameRate < MIN_FRAME_RATE || frameRate > MAX_FRAME_RATE) {
      throw new IllegalArgumentException("Unsupported frame rate: " + frameRate + " fps");
    }
    return Math.max(1, 1000 / frameRate);
  }

  private PreviewFrames getPreviewFrames() {
    final boolean mirrored = directionIndex > Direction.N.getCycleOffset();
    final Direction storedDirection = mirrored ? Direction.values()[16 - directionIndex]
        : Direction.values()[directionIndex];
    final CreatureAnimationModel primary = mirrored && easternModel != null
        && easternModel.hasFrames(sequence, storedDirection) ? easternModel : model;
    final ResolvedFrames resolved = primary.resolveFrames(sequence, storedDirection);
    final List<AnimationFrame> frames = (resolved != null) ? resolved.getFrames()
        : Collections.<AnimationFrame>emptyList();
    final CreatureAnimationModel overlay = mirrored && overlayEasternModel != null
        && overlayEasternModel.hasFrames(sequence, storedDirection) ? overlayEasternModel : overlayModel;
    final ResolvedFrames overlayResolved =
        overlay != null ? overlay.resolveFrames(sequence, storedDirection) : null;
    final List<AnimationFrame> overlayFrames = overlayResolved != null ? overlayResolved.getFrames()
        : Collections.<AnimationFrame>emptyList();
    final CreatureAnimationModel offhandOverlay = mirrored && offhandOverlayEasternModel != null
        && offhandOverlayEasternModel.hasFrames(sequence, storedDirection)
            ? offhandOverlayEasternModel : offhandOverlayModel;
    final ResolvedFrames offhandResolved =
        offhandOverlay != null ? offhandOverlay.resolveFrames(sequence, storedDirection) : null;
    final List<AnimationFrame> offhandOverlayFrames = offhandResolved != null ? offhandResolved.getFrames()
        : Collections.<AnimationFrame>emptyList();
    return new PreviewFrames(resolved, frames, overlayFrames, offhandOverlayFrames, mirrored);
  }

  private static AnimationFrame selectFrame(List<AnimationFrame> frames, int index, int timelineCount) {
    if (frames == null || frames.isEmpty()) {
      return null;
    }
    if (frames.size() == timelineCount || timelineCount <= 1) {
      return frames.get(index % frames.size());
    }
    final int timelineIndex = index % timelineCount;
    final int mapped = (int) Math.round(timelineIndex * (frames.size() - 1.0) / (timelineCount - 1.0));
    return frames.get(Math.max(0, Math.min(frames.size() - 1, mapped)));
  }

  private static java.awt.Rectangle getSharedBounds(boolean mirrored, AnimationFrame... frames) {
    int minimumX = Integer.MAX_VALUE;
    int minimumY = Integer.MAX_VALUE;
    int maximumX = Integer.MIN_VALUE;
    int maximumY = Integer.MIN_VALUE;
    for (final AnimationFrame frame : frames) {
      if (frame == null) {
        continue;
      }
      final BufferedImage image = frame.getImage();
      final Point center = frame.getCenter();
      final int centerX = mirrored ? image.getWidth() - 1 - center.x : center.x;
      minimumX = Math.min(minimumX, -centerX);
      minimumY = Math.min(minimumY, -center.y);
      maximumX = Math.max(maximumX, image.getWidth() - centerX);
      maximumY = Math.max(maximumY, image.getHeight() - center.y);
    }
    if (minimumX == Integer.MAX_VALUE) {
      return new java.awt.Rectangle(0, 0, 1, 1);
    }
    return new java.awt.Rectangle(minimumX, minimumY, Math.max(1, maximumX - minimumX),
        Math.max(1, maximumY - minimumY));
  }

  private static void drawFrame(Graphics2D graphics, AnimationFrame frame, int originX, int originY, double scale,
      boolean mirrored) {
    if (frame == null) {
      return;
    }
    final BufferedImage image = frame.getImage();
    final Point center = frame.getCenter();
    final int centerX = mirrored ? image.getWidth() - 1 - center.x : center.x;
    final int x = originX - (int) Math.round(centerX * scale);
    final int y = originY - (int) Math.round(center.y * scale);
    final int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
    final int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
    if (mirrored) {
      graphics.drawImage(image, x + width, y, x, y + height, 0, 0, image.getWidth(), image.getHeight(), null);
    } else {
      graphics.drawImage(image, x, y, x + width, y + height, 0, 0, image.getWidth(), image.getHeight(), null);
    }
  }

  private void drawCheckerboard(Graphics2D g) {
    final int cell = 18;
    final Color first = new Color(55, 58, 63);
    final Color second = new Color(65, 68, 73);
    for (int y = 0; y < getHeight(); y += cell) {
      for (int x = 0; x < getWidth(); x += cell) {
        g.setColor((((x / cell) + (y / cell)) & 1) == 0 ? first : second);
        g.fillRect(x, y, cell, cell);
      }
    }
  }

  private void drawEmptyMessage(Graphics2D g) {
    final String text = "Generate a draft or import PNG frames to begin";
    g.setColor(new Color(225, 226, 228));
    final int width = g.getFontMetrics().stringWidth(text);
    g.drawString(text, Math.max(12, (getWidth() - width) / 2), getHeight() / 2);
  }

  private static final class PreviewFrames {
    private final ResolvedFrames resolved;
    private final List<AnimationFrame> frames;
    private final List<AnimationFrame> overlayFrames;
    private final List<AnimationFrame> offhandOverlayFrames;
    private final boolean mirrored;

    private PreviewFrames(ResolvedFrames resolved, List<AnimationFrame> frames, List<AnimationFrame> overlayFrames,
        List<AnimationFrame> offhandOverlayFrames, boolean mirrored) {
      this.resolved = resolved;
      this.frames = frames;
      this.overlayFrames = overlayFrames;
      this.offhandOverlayFrames = offhandOverlayFrames;
      this.mirrored = mirrored;
    }

    private int getFrameCount() {
      return Math.max(Math.max(frames.size(), overlayFrames.size()), offhandOverlayFrames.size());
    }
  }
}
