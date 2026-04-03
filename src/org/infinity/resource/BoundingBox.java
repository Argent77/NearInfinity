// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Objects;

import org.infinity.resource.vertex.Vertex;

/**
 * A bounding box that stores bounding coordinates for polygon structures.
 */
public class BoundingBox implements Cloneable {
  public int minX;
  public int minY;
  public int maxX;
  public int maxY;

  /**
   * Helper method that returns a {@code BoundingBox} object with the bounds of the vertices defined by the parameters.
   *
   * @param struct         Parent {@code AbstractStruct} object that contains the {@link Vertex} substructures.
   * @param verticesOffset Base offset of the vertex list.
   * @param startIndex     Start index in the vertex list.
   * @param count          Number of vertices to consider.
   * @return Fully initialized {@link BoundingBox} object. Returns an empty bounding box if no vertices are available.
   */
  public static BoundingBox calculateBoundingBox(AbstractStruct struct, int verticesOffset, int startIndex, int count) {
    final BoundingBox retVal = new BoundingBox();
    if (struct == null || count <= 0) {
      return retVal;
    }

    retVal.minX = retVal.minY = Integer.MAX_VALUE;
    retVal.maxX = retVal.maxY = Integer.MIN_VALUE;
    for (int i = 0; i < count; i++) {
      final int offset = verticesOffset + (startIndex + i) * 4;
      final StructEntry se = struct.getAttribute(offset, false);
      if (se instanceof Vertex) {
        final Vertex vertex = (Vertex)se;
        retVal.minX = Math.min(retVal.minX, vertex.getX());
        retVal.minY = Math.min(retVal.minY, vertex.getY());
        retVal.maxX = Math.max(retVal.maxX, vertex.getX());
        retVal.maxY = Math.max(retVal.maxY, vertex.getY());
      }
    }

    if (retVal.minX == Integer.MAX_VALUE) {
      retVal.minX = retVal.minY = -1;
      retVal.maxX = retVal.maxY = 0;
    }

    return retVal;
  }

  public BoundingBox() {
    this(-1, -1, 0, 0);
  }

  public BoundingBox(int minX, int minY, int maxX, int maxY) {
    this.minX = Math.min(minX, maxX);
    this.minY = Math.min(minY, maxY);
    this.maxX = Math.max(minX, maxX);
    this.maxY = Math.max(minY, maxY);
  }

  public BoundingBox(Rectangle rect) {
    Objects.requireNonNull(rect, "Rect is null");
    this.minX = Math.min(rect.x, rect.x + rect.width);
    this.minY = Math.min(rect.y, rect.y + rect.height);
    this.maxX = Math.max(rect.x, rect.x + rect.width);
    this.maxY = Math.max(rect.y, rect.y + rect.height);
  }

  /** Returns the minimum X coordinate. */
  public int getMinX() {
    return minX;
  }

  /** Returns the maximum X coordinate (inclusive). */
  public int getMaxX() {
    return maxX;
  }

  /** Returns the minimum Y coordinate. */
  public int getMinY() {
    return minY;
  }

  /** Returns the maximum Y coordinate (inclusive). */
  public int getMaxY() {
    return maxY;
  }

  /** Returns the bounding box as a {@link Rectangle} structure. */
  public Rectangle toRectangle() {
    return new Rectangle(minX, minY, maxX - minX, maxY - minY);
  }

  /** Returns the dimensions of the bounding box as {@link Dimension} structure. */
  public Dimension toDimension() {
    return new Dimension(maxX - minX, maxY - minY);
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new BoundingBox(minX, minY, maxX, maxY);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxX, maxY, minX, minY);
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
    BoundingBox other = (BoundingBox)obj;
    return maxX == other.maxX && maxY == other.maxY && minX == other.minX && minY == other.minY;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("BoundingBox [minX=").append(minX).append(", minY=").append(minY).append(", maxX=").append(maxX)
        .append(", maxY=").append(maxY).append("]");
    return builder.toString();
  }
}
