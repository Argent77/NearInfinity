// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;
import org.infinity.util.io.StreamUtils;

/**
 * Common base class for handling BAM resources.
 */
public abstract class BamDecoder {
  /** Recognized BAM resource types */
  public enum Type {
    INVALID, BAMC, BAMV1, BAMV2, CUSTOM
  }

  private final ResourceEntry bamEntry;

  private Type type;
  private Composite composite;

  /**
   * Returns whether the specified resource entry points to a valid BAM resource.
   */
  public static boolean isValid(ResourceEntry bamEntry) {
    return getType(bamEntry) != Type.INVALID;
  }

  /**
   * Returns the type of the specified resource entry.
   *
   * @return One of the BAM {@code Type}s.
   */
  public static Type getType(ResourceEntry bamEntry) {
    Type retVal = Type.INVALID;
    if (bamEntry != null) {
      final boolean ignoreOverride = bamEntry instanceof BIFFResourceEntry;
      try (InputStream is = bamEntry.getResourceDataAsStream(ignoreOverride)) {
        String signature = StreamUtils.readString(is, 4);
        String version = StreamUtils.readString(is, 4);
        if ("BAMC".equals(signature)) {
          retVal = Type.BAMC;
        } else if ("BAM ".equals(signature)) {
          if ("V1  ".equals(version)) {
            retVal = Type.BAMV1;
          } else if ("V2  ".equals(version)) {
            retVal = Type.BAMV2;
          }
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return retVal;
  }

  /**
   * Returns a new BamDecoder object based on the specified Bam resource entry.
   *
   * @param bamEntry The BAM resource entry.
   * @return Either {@code BamV1Decoder} or {@code BamV2Decoder}, depending on the BAM resource type. Returns
   *         {@code null} if the resource doesn't contain valid BAM data.
   */
  public static BamDecoder loadBam(ResourceEntry bamEntry) {
    Type type = getType(bamEntry);
    switch (type) {
      case BAMC:
      case BAMV1:
        return new BamV1Decoder(bamEntry);
      case BAMV2:
        return new BamV2Decoder(bamEntry);
      default:
        return null;
    }
  }

  /**
   * Returns a new BamDecoder object of type PseudoBamDecoder.
   *
   * @param image The input image to create a pseudo BAM of one frame.
   * @return A PseudoBamDecoder object.
   */
  public static BamDecoder loadBam(BufferedImage image) {
    return loadBam(new BufferedImage[] { image });
  }

  /**
   * Returns a new BamDecoder object of type PseudoBamDecoder.
   *
   * @param images An array of input images to create the pseudo BAM structure.
   * @return A PseudoBamDecoder object.
   */
  public static BamDecoder loadBam(BufferedImage[] images) {
    return new PseudoBamDecoder(images);
  }

  /**
   * Returns the ResourceEntry object of the BAM resource.
   */
  public ResourceEntry getResourceEntry() {
    return bamEntry;
  }

  /**
   * Returns {@code true} if the BAM has been closed, is invalid or does not contain any frames.
   */
  public boolean isEmpty() {
    return frameCount() == 0;
  }

  /** Returns the type of the BAM resource. */
  public Type getType() {
    return type;
  }

  /** Returns an interface that provides access to cycle-specific functionality. */
  public abstract BamControl createControl();

  /** Returns a frame info object containing basic properties of the specified frame. */
  public abstract FrameEntry getFrameInfo(int frameIdx);

  /** Removes all data from the decoder. Use this to free up memory. */
  public abstract void close();

  /** Returns whether the BamDecoder instance has been initialized correctly. */
  public abstract boolean isOpen();

  /** Clears existing data and reloads the current BAM resource entry. */
  public abstract void reload();

  /** Returns the raw data of the BAM resource. */
  public abstract ByteBuffer getResourceBuffer();

  /** Returns the total number of available frames. */
  public abstract int frameCount();

  /** Returns the specified frame as Image. */
  public abstract Image frameGet(BamControl control, int frameIdx);

  /** Draws the specified frame onto the canvas. */
  public abstract void frameGet(BamControl control, int frameIdx, Image canvas);

  /**
   * Returns the {@link Composite} instance that is used to draw the BAM frame onto a canvas.
   * <p>
   * By default the {@link AlphaComposite#SrcOver} composite object is used.
   */
  public Composite getComposite() {
    return (composite != null) ? composite : AlphaComposite.SrcOver;
  }

  /**
   * Sets a {@link Composite} instance that is used to draw the BAM frame onto a canvas. Specify {@code null} to use the
   * default {@code Composite}.
   */
  public void setComposite(Composite comp) {
    this.composite = comp;
  }

  protected BamDecoder(ResourceEntry bamEntry) {
    this.bamEntry = bamEntry;
    this.type = Type.INVALID;
    this.composite = null; // use default
  }

  // Sets the current BAM type
  protected void setType(Type type) {
    this.type = type;
  }

  @Override
  public String toString() {
    String retVal = "entry=" + ((bamEntry != null) ? bamEntry.toString() : "(null)");
    retVal += ", Type=" + ((type != null) ? type.toString() : "(null)");
    return "[" + retVal + "]";
  }

  @Override
  public int hashCode() {
    return Objects.hash(bamEntry, type);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    BamDecoder other = (BamDecoder) obj;
    return Objects.equals(bamEntry, other.bamEntry) && type == other.type;
  }

  // -------------------------- INNER CLASSES --------------------------

  /**
   * Interface for basic BAM frame properties
   */
  public interface FrameEntry {
    int getWidth();

    int getHeight();

    int getCenterX();

    int getCenterY();

    void setCenterX(int x);

    void setCenterY(int y);

    void resetCenter();
  }

  /**
   * Provides access to cycle-specific functionality.
   */
  public static abstract class BamControl {
    /** Definitions of how to render BAM frames. */
    public enum Mode {
      /**
       * Each frame is drawn individually. The resulting image dimension is defined by the drawn frame. Does not take
       * frame centers into account.
       */
      INDIVIDUAL,
      /**
       * Each frame is drawn onto a canvas of fixed dimension that is big enough to hold every single frame without
       * cropping or resizing. Takes frame centers into account.
       */
      SHARED
    }

    private final BamDecoder parent;

    private Mode mode;

    // Contains the image dimension of all BAMs used in Shared mode.
    // Dimension(width, height) defines the total image dimension.
    // Point(x, y) defines the position for center (0, 0).
    private Rectangle sharedBamSize;
    private boolean sharedPerCycle;

    protected BamControl(BamDecoder parent) {
      this.parent = parent;
      this.mode = Mode.INDIVIDUAL;
      this.sharedBamSize = new Rectangle();
      this.sharedPerCycle = true;
    }

    /**
     * Returns the currently selected drawing mode.
     */
    public Mode getMode() {
      return mode;
    }

    /**
     * Specify how to draw graphical frame data. It affects all methods that draw a frame into an Image object or an int
     * array.
     *
     * @param mode The drawing mode.
     */
    public void setMode(Mode mode) {
      if (mode != null && mode != this.mode) {
        this.mode = mode;
        updateSharedBamSize();
      }
    }

    /**
     * Returns whether the calculated image dimension in shared mode is based on the current cycle.
     */
    public boolean isSharedPerCycle() {
      return sharedPerCycle;
    }

    /**
     * Sets whether the calculated image dimension in shared mode is based on the current cycle.
     */
    public void setSharedPerCycle(boolean set) {
      if (set != sharedPerCycle) {
        sharedPerCycle = set;
        updateSharedBamSize();
      }
    }

    /**
     * Calculates the rectangle of the current BAM animation. Takes {@link #isSharedPerCycle()} into account.
     *
     * @param isMirrored If {@code true}, returns a rectangle that is based on the animation mirrored along the x axis.
     * @return A rectangle containing information about the base offset (x, y) and overall dimension (width, height).
     */
    public Rectangle calculateSharedCanvas(boolean isMirrored) {
      return calculateSharedBamSize(null, isSharedPerCycle(), isMirrored);
    }

    /**
     * Returns the dimension of the image for frames to be drawn in shared mode.
     */
    public Dimension getSharedDimension() {
      if (sharedBamSize != null) {
        return new Dimension(sharedBamSize.getSize());
      } else {
        return new Dimension(1, 1);
      }
    }

    /**
     * Returns the point of origin for frames that are drawn in shared mode.<br>
     * The top-left corner of the selected frame is calculated as:<br>
     * {@code topleft = getSharedOrigin() - frameCenter()}
     */
    public Point getSharedOrigin() {
      if (sharedBamSize != null) {
        return new Point(sharedBamSize.getLocation());
      } else {
        return new Point();
      }
    }

    /** Returns the BamDecoder instance this control is associated with. */
    public BamDecoder getDecoder() {
      return parent;
    }

    /** Returns {@code true} if, and only if {@link #cycleCount()} is 0. */
    public boolean isEmpty() {
      return (cycleCount() == 0);
    }

    /** Returns the total number of available cycles. */
    public abstract int cycleCount();

    /** Returns the number of frames in the currently selected cycle. */
    public abstract int cycleFrameCount();

    /** Returns the number of frames in the specified cycle. */
    public abstract int cycleFrameCount(int cycleIdx);

    /** Returns the index of the active cycle. */
    public abstract int cycleGet();

    /** Sets the active cycle. Returns whether the specified cycle could be set. */
    public abstract boolean cycleSet(int cycleIdx);

    /** Returns whether the active cycle can be advanced by at least one more frame. */
    public abstract boolean cycleHasNextFrame();

    /**
     * Selects the next available frame in the active cycle if available. Returns whether a next frame has been
     * selected.
     */
    public abstract boolean cycleNextFrame();

    /** Selects the first frame in the active cycle. */
    public abstract void cycleReset();

    /** Returns the currently selected frame of the active cycle as Image. (Takes current mode into account.) */
    public abstract Image cycleGetFrame();

    /**
     * Draws the currently selected frame of the active cycle onto the specified canvas. (Takes current mode into
     * account.)
     */
    public abstract void cycleGetFrame(Image canvas);

    /** Returns the specified frame of the active cycle as Image. (Takes current mode into account.) */
    public abstract Image cycleGetFrame(int frameIdx);

    /** Draws the specified frame of the active cycle onto the specified canvas. (Takes current mode into account.) */
    public abstract void cycleGetFrame(int frameIdx, Image canvas);

    /** Returns the index of the currently selected frame in the active cycle. */
    public abstract int cycleGetFrameIndex();

    /** Selects the specified frame in the active cycle. Returns whether the specified frame index could be set. */
    public abstract boolean cycleSetFrameIndex(int frameIdx);

    /**
     * Translates the active cycle's frame index into an absolute frame index. Returns -1 if cycle doesn't contain
     * frames.
     */
    public abstract int cycleGetFrameIndexAbsolute();

    /**
     * Translates the specified active cycle's frame index into an absolute frame index. Returns -1 if cycle doesn't
     * contain frames.
     */
    public abstract int cycleGetFrameIndexAbsolute(int frameIdx);

    /** Translates the cycle's frame index into an absolute frame index. Returns -1 if cycle doesn't contain frames. */
    public abstract int cycleGetFrameIndexAbsolute(int cycleIdx, int frameIdx);

    @Override
    public int hashCode() {
      return Objects.hash(mode, sharedBamSize, sharedPerCycle);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      BamControl other = (BamControl) obj;
      return mode == other.mode && Objects.equals(sharedBamSize, other.sharedBamSize)
          && sharedPerCycle == other.sharedPerCycle;
    }

    // Updates the shared canvas size for the current BAM
    protected void updateSharedBamSize() {
      sharedBamSize = calculateSharedBamSize(sharedBamSize, isSharedPerCycle(), false);
    }

    /**
     * Calculates a shared canvas size for the current BAM. To get the top-left corner of the selected frame:
     * <p>
     * For unmirrored frames:
     *
     * <pre>
     * left = -sharedBamSize.x - frameCenterX()
     * </pre>
     *
     * <pre>
     * top = -sharedBamSize.y - frameCenterY()
     * </pre>
     * <p>
     * For mirrored frames:
     *
     * <pre>
     * left = -sharedBamSize.x - (frameWidth() - frameCenterX() - 1)
     * </pre>
     *
     * <pre>
     * top = -sharedBamSize.y - frameCenterY()
     * </pre>
     *
     * @param rect       Reuse this {@code Rectangle} instance if available.
     * @param cycleBased {@code true} for current cycle only, {@code false} for all available frames
     * @param isMirrored {@code true}: mirror along the x axis, {@code false}: no mirroring
     * @return the update rectangle
     */
    protected Rectangle calculateSharedBamSize(Rectangle rect, boolean cycleBased, boolean isMirrored) {
      if (rect == null) {
        rect = new Rectangle();
      }

      int x1 = Integer.MAX_VALUE;
      int x2 = Integer.MIN_VALUE;
      int y1 = Integer.MAX_VALUE;
      int y2 = Integer.MIN_VALUE;
      if (cycleBased) {
        for (int i = 0; i < cycleFrameCount(); i++) {
          int frame = cycleGetFrameIndexAbsolute(i);
          FrameEntry fe = parent.getFrameInfo(frame);
          int cx = isMirrored ? (fe.getWidth() - fe.getCenterX() - 1) : fe.getCenterX();
          x1 = Math.min(x1, -cx);
          y1 = Math.min(y1, -fe.getCenterY());
          x2 = Math.max(x2, fe.getWidth() - cx);
          y2 = Math.max(y2, fe.getHeight() - fe.getCenterY());
        }
      } else {
        for (int i = 0; i < parent.frameCount(); i++) {
          FrameEntry fe = parent.getFrameInfo(i);
          int cx = isMirrored ? (fe.getWidth() - fe.getCenterX() - 1) : fe.getCenterX();
          x1 = Math.min(x1, -cx);
          y1 = Math.min(y1, -fe.getCenterY());
          x2 = Math.max(x2, fe.getWidth() - cx);
          y2 = Math.max(y2, fe.getHeight() - fe.getCenterY());
        }
      }
      if (x1 == Integer.MAX_VALUE) {
        x1 = 0;
      }
      if (y1 == Integer.MAX_VALUE) {
        y1 = 0;
      }
      if (x2 == Integer.MIN_VALUE) {
        x2 = 0;
      }
      if (y2 == Integer.MIN_VALUE) {
        y2 = 0;
      }
      rect.x = x1;
      rect.y = y1;
      rect.width = x2 - x1 + 1;
      rect.height = y2 - y1 + 1;

      return rect;
    }

    // Returns the shared rectangle object
    protected Rectangle getSharedRectangle() {
      return sharedBamSize;
    }
  }
}
