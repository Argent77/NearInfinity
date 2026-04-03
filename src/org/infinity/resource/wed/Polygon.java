// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wed;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;

import javax.swing.JButton;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.SectionCount;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.StructViewer;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.BoundingBox;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.StructEntry;
import org.infinity.resource.vertex.Vertex;

public abstract class Polygon extends AbstractStruct implements AddRemovable, HasChildStructs, ActionListener {
  // WED/Polygon-specific field labels
  public static final String WED_POLY_VERTEX_INDEX  = "Vertex index";
  public static final String WED_POLY_NUM_VERTICES  = "# vertices";
  public static final String WED_POLY_FLAGS         = "Polygon flags";
  public static final String WED_POLY_HEIGHT        = "Height";
  public static final String WED_POLY_MIN_COORD_X   = "Minimum coordinate: X";
  public static final String WED_POLY_MAX_COORD_X   = "Maximum coordinate: X";
  public static final String WED_POLY_MIN_COORD_Y   = "Minimum coordinate: Y";
  public static final String WED_POLY_MAX_COORD_Y   = "Maximum coordinate: Y";

  public static final String[] FLAGS_ARRAY = { "No flags set", "Shade wall", "Semi transparent", "Hovering wall",
      "Cover animations", null, null, null, "Is door" };

  private JButton bCalcBoundingBox;

  public Polygon(AbstractStruct superStruct, String name, ByteBuffer buffer, int offset) throws Exception {
    super(superStruct, name, buffer, offset, 8);
  }

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    return new AddRemovable[] { new Vertex() };
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception {
    return entry;
  }

  @Override
  public boolean canRemove() {
    return true;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == bCalcBoundingBox) {
      updateBoundingBox();
    }
  }

  @Override
  protected void viewerInitialized(StructViewer viewer) {
    final ButtonPanel buttonPanel = viewer.getButtonPanel();
    bCalcBoundingBox = new JButton("Calculate bounding box", Icons.ICON_REFRESH_16.getIcon());
    bCalcBoundingBox.addActionListener(this);
    buttonPanel.addControl(bCalcBoundingBox, ButtonPanel.Control.CUSTOM_1);
  }

  @Override
  protected void setAddRemovableOffset(AddRemovable datatype) {
    if (datatype instanceof Vertex) {
      int index = ((IsNumeric) getAttribute(WED_POLY_VERTEX_INDEX)).getValue();
      index += ((IsNumeric) getAttribute(WED_POLY_NUM_VERTICES)).getValue();
      final AbstractStruct superStruct = getRoot();
      int offset = ((IsNumeric) superStruct.getAttribute(WedResource.WED_OFFSET_VERTICES)).getValue();
      datatype.setOffset(offset + 4 * index);
      ((AbstractStruct) datatype).realignStructOffsets();
    }
  }

  public void readVertices(ByteBuffer buffer, int offset) throws Exception {
    IsNumeric firstVertex = (IsNumeric) getAttribute(WED_POLY_VERTEX_INDEX);
    IsNumeric numVertices = (IsNumeric) getAttribute(WED_POLY_NUM_VERTICES);
    for (int i = 0; i < numVertices.getValue(); i++) {
      addField(new Vertex(this, buffer, offset + 4 * (firstVertex.getValue() + i), i));
    }
  }

  public int updateVertices(int offset, int startIndex) {
    ((DecNumber) getAttribute(WED_POLY_VERTEX_INDEX)).setValue(startIndex);
    int count = 0;
    for (final StructEntry entry : getFields()) {
      if (entry instanceof Vertex) {
        entry.setOffset(offset);
        ((AbstractStruct) entry).realignStructOffsets();
        offset += 4;
        count++;
      }
    }
    ((DecNumber) getAttribute(WED_POLY_NUM_VERTICES)).setValue(count);
    return count;
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new DecNumber(buffer, offset, 4, WED_POLY_VERTEX_INDEX));
    addField(new SectionCount(buffer, offset + 4, 4, WED_POLY_NUM_VERTICES, Vertex.class));
    addField(new Flag(buffer, offset + 8, 1, WED_POLY_FLAGS, FLAGS_ARRAY));
    addField(new DecNumber(buffer, offset + 9, 1, WED_POLY_HEIGHT));
    addField(new DecNumber(buffer, offset + 10, 2, WED_POLY_MIN_COORD_X));
    addField(new DecNumber(buffer, offset + 12, 2, WED_POLY_MAX_COORD_X));
    addField(new DecNumber(buffer, offset + 14, 2, WED_POLY_MIN_COORD_Y));
    addField(new DecNumber(buffer, offset + 16, 2, WED_POLY_MAX_COORD_Y));
    return offset + 18;
  }

  private void updateBoundingBox() {
    final WedResource wed = (WedResource)getRoot();
    if (wed == null) {
      return;
    }

    final int ofsVertices = ((IsNumeric)wed.getAttribute(WedResource.WED_OFFSET_VERTICES)).getValue();
    final int idxVertices = ((IsNumeric)getAttribute(WED_POLY_VERTEX_INDEX)).getValue();
    final int cntVertices = ((IsNumeric)getAttribute(WED_POLY_NUM_VERTICES)).getValue();
    final BoundingBox box = BoundingBox.calculateBoundingBox(this, ofsVertices, idxVertices, cntVertices);
    ((DecNumber)getAttribute(WED_POLY_MIN_COORD_X)).setValue(box.minX);
    ((DecNumber)getAttribute(WED_POLY_MIN_COORD_Y)).setValue(box.minY);
    ((DecNumber)getAttribute(WED_POLY_MAX_COORD_X)).setValue(box.maxX);
    ((DecNumber)getAttribute(WED_POLY_MAX_COORD_Y)).setValue(box.maxY);
    fireTableDataChanged();
  }
}
