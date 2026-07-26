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
      "NNE - North-northeast (mirrored)",
      "NE - Northeast (mirrored)",
      "ENE - East-northeast (mirrored)",
      "E - East (mirrored)",
      "ESE - East-southeast (mirrored)",
      "SE - Southeast (mirrored)",
      "SSE - South-southeast (mirrored)"
  };

  private final Timer timer;
  private CreatureAnimationModel model = new CreatureAnimationModel();
  private Sequence sequence = Sequence.WALK;
  private int directionIndex;
  private int frameIndex;
  private boolean playing = true;
  private boolean showPivot = true;

  public AnimationPreviewPanel() {
    setOpaque(true);
    setBackground(new Color(45, 48, 53));
    setPreferredSize(new Dimension(500, 460));
    setMinimumSize(new Dimension(300, 260));
    timer = new Timer(110, event -> advanceFrame());
    timer.start();
  }

  public void setModel(CreatureAnimationModel model) {
    this.model = (model != null) ? model : new CreatureAnimationModel();
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

  public void setDelay(int delay) {
    timer.setDelay(Math.max(35, Math.min(1000, delay)));
  }

  public void setShowPivot(boolean showPivot) {
    this.showPivot = showPivot;
    repaint();
  }

  public String getStatusText() {
    final PreviewFrames preview = getPreviewFrames();
    if (preview.frames.isEmpty()) {
      return sequence.getCode() + " / " + PREVIEW_DIRECTIONS[directionIndex] + " - no frames";
    }
    final String fallback = preview.resolved.isFallback()
        ? " - fallback " + preview.resolved.getResolvedSequence().getCode() + "/"
            + preview.resolved.getResolvedDirection().getCode()
        : "";
    return sequence.getCode() + " / " + PREVIEW_DIRECTIONS[directionIndex] + " - "
        + (frameIndex % preview.frames.size() + 1) + "/" + preview.frames.size() + fallback;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    final Graphics2D g = (Graphics2D) graphics.create();
    try {
      drawCheckerboard(g);
      final PreviewFrames preview = getPreviewFrames();
      if (preview.frames.isEmpty()) {
        drawEmptyMessage(g);
        return;
      }

      final AnimationFrame frame = preview.frames.get(frameIndex % preview.frames.size());
      final BufferedImage image = frame.getImage();
      final double availableWidth = Math.max(1.0, getWidth() - 56.0);
      final double availableHeight = Math.max(1.0, getHeight() - 56.0);
      double scale = Math.min(availableWidth / image.getWidth(), availableHeight / image.getHeight());
      scale = Math.max(0.1, Math.min(5.0, scale));
      final int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
      final int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
      final int x = (getWidth() - width) / 2;
      final int y = (getHeight() - height) / 2;

      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          scale >= 1.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
              : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      if (preview.mirrored) {
        g.drawImage(image, x + width, y, x, y + height, 0, 0, image.getWidth(), image.getHeight(), null);
      } else {
        g.drawImage(image, x, y, x + width, y + height, 0, 0, image.getWidth(), image.getHeight(), null);
      }

      if (showPivot) {
        final Point center = frame.getCenter();
        final int centerX = preview.mirrored ? image.getWidth() - 1 - center.x : center.x;
        final int pivotX = x + (int) Math.round(centerX * scale);
        final int pivotY = y + (int) Math.round(center.y * scale);
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(new Color(255, 206, 72, 185));
        g.drawLine(12, pivotY, getWidth() - 12, pivotY);
        g.setColor(new Color(85, 218, 255, 215));
        g.drawLine(pivotX - 8, pivotY, pivotX + 8, pivotY);
        g.drawLine(pivotX, pivotY - 8, pivotX, pivotY + 8);
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
    final int count = getPreviewFrames().frames.size();
    if (count > 0) {
      frameIndex = (frameIndex + 1) % count;
      repaint();
      firePropertyChange("frameStatus", null, getStatusText());
    }
  }

  private PreviewFrames getPreviewFrames() {
    final boolean mirrored = directionIndex > Direction.N.getCycleOffset();
    final Direction storedDirection = mirrored ? Direction.values()[16 - directionIndex]
        : Direction.values()[directionIndex];
    final ResolvedFrames resolved = model.resolveFrames(sequence, storedDirection);
    final List<AnimationFrame> frames = (resolved != null) ? resolved.getFrames()
        : Collections.<AnimationFrame>emptyList();
    return new PreviewFrames(resolved, frames, mirrored);
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
    private final boolean mirrored;

    private PreviewFrames(ResolvedFrames resolved, List<AnimationFrame> frames, boolean mirrored) {
      this.resolved = resolved;
      this.frames = frames;
      this.mirrored = mirrored;
    }
  }
}
