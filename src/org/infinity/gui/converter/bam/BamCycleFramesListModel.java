// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.bam;

import javax.swing.AbstractListModel;

import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.graphics.PseudoBamDecoder.PseudoBamFrameEntry;

/** Manages frames within a cycle. */
class BamCycleFramesListModel extends AbstractListModel<PseudoBamFrameEntry> {
  private final ConvertToBam converter;
  private final PseudoBamDecoder decoder;
  private final PseudoBamDecoder.PseudoBamControl control;

  public BamCycleFramesListModel(ConvertToBam converter) {
    if (converter == null) {
      throw new NullPointerException();
    }
    this.converter = converter;
    this.decoder = getConverter().getBamDecoder(ConvertToBam.BAM_ORIGINAL);
    this.control = getDecoder().createControl();
  }

  /** Returns the parent converter object. */
  public ConvertToBam getConverter() {
    return converter;
  }

  /** Returns the associated BamDecoder object. */
  public PseudoBamDecoder getDecoder() {
    return decoder;
  }

  /** Returns the associated BamDecoder control. */
  public PseudoBamDecoder.PseudoBamControl getControl() {
    return control;
  }

  /** Returns the active cycle. */
  public int getCycle() {
    return getControl().cycleGet();
  }

  /** Sets a new active cycle. */
  public void setCycle(int cycle) {
    setCycle(cycle, false);
  }

  /** Sets a new active cycle. */
  public void setCycle(int cycle, boolean forced) {
    if (cycle < 0) {
      cycle = 0;
    } else if (cycle >= getControl().cycleCount()) {
      cycle = getControl().cycleCount() - 1;
    }
    if (forced || cycle != getControl().cycleGet()) {
      int oldCount = getControl().cycleFrameCount();
      getControl().cycleSet(cycle);
      int newCount = getControl().cycleFrameCount();

      // firing change event if needed
      int changed = Math.min(oldCount, newCount);
      if (changed > 0) {
        fireContentsChanged(this, 0, changed - 1);
      }

      // firing remove/added events if needed
      int diff = Math.max(oldCount, newCount) - changed;
      if (diff < 0) {
        fireIntervalRemoved(this, changed, changed - diff - 1);
      } else if (diff > 0) {
        fireIntervalAdded(this, changed, changed + diff - 1);
      }
    }
  }

  // /** Adds one frame index to the current cycle. */
  // public void add(int index)
  // {
  // insert(getControl().cycleFrameCount(), new int[]{index});
  // }

  /** Adds the specified array of frame indices to the current cycle. */
  public void add(int[] indices) {
    insert(getControl().cycleFrameCount(), indices);
  }

  /** Inserts one frame index into the current cycle. */
  public void insert(int pos, int index) {
    insert(pos, new int[] { index });
  }

  /** Inserts the array of frame indices into the current cycle. */
  public void insert(int pos, int[] indices) {
    if (indices != null && pos >= 0 && pos <= getControl().cycleFrameCount()) {
      int count = indices.length;
      getControl().cycleInsertFrames(getControl().cycleGet(), pos, indices);
      fireIntervalAdded(this, pos, pos + count - 1);
    }
  }

  // /** Removes one entry from the current cycle. */
  // public void remove(int pos)
  // {
  // remove(pos, 1);
  // }

  /**
   * Removes a number of entries from the current cycle.
   */
  public void remove(int pos, int count) {
    if (count > 0 && pos >= 0 && pos < getControl().cycleFrameCount()) {
      if (pos + count > getControl().cycleFrameCount()) {
        count = getControl().cycleFrameCount() - pos;
      }
      getControl().cycleRemoveFrames(getControl().cycleGet(), pos, count);
      fireIntervalRemoved(this, pos, pos + count - 1);
    }
  }

  /**
   * Removes all entries from the current cycle.
   */
  public void clear() {
    int count = getControl().cycleFrameCount();
    getControl().cycleClearFrames();
    if (count > 0) {
      fireIntervalRemoved(this, 0, count - 1);
    }
  }

  /**
   * Moves the specified entry within the list by {@code offset}.
   *
   * @param index  The index of the frame.
   * @param offset The number of positions to move.
   */
  public void move(int index, int offset) {

    // moving positions
    int retVal = getControl().cycleMoveFrame(index, offset);

    // preparing interval
    int pos1 = index, pos2 = index;
    if (retVal >= 0) {
      if (retVal > pos1) {
        pos2 = retVal;
      } else {
        pos1 = retVal;
      }
    }

    // notifying listeners
    if (pos2 > pos1) {
      fireContentsChanged(this, pos1, pos2);
    }
  }

  /** Returns {@code true} if, and only if {@code getControl().cycleFrameCount()} is 0. */
  public boolean isEmpty() {
    return (getControl().cycleFrameCount() == 0);
  }

  @Override
  public int getSize() {
    return getControl().cycleFrameCount();
  }

  @Override
  public PseudoBamFrameEntry getElementAt(int index) {
    if (index >= 0 && index < getControl().cycleFrameCount()) {
      return getDecoder().getFrameInfo(getControl().cycleGetFrameIndexAbsolute(index));
    } else {
      return null;
    }
  }
}