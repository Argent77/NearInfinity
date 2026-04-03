// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.are;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;

import javax.swing.JButton;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.StructViewer;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.BoundingBox;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.Profile;
import org.infinity.resource.StructEntry;
import org.infinity.resource.vertex.Vertex;
import org.infinity.util.io.StreamUtils;

public final class ITEPoint extends AbstractStruct
    implements AddRemovable, HasVertices, HasChildStructs, ActionListener {
  // ARE/Trigger-specific field labels
  public static final String ARE_TRIGGER                            = "Trigger";
  public static final String ARE_TRIGGER_NAME                       = "Name";
  public static final String ARE_TRIGGER_TYPE                       = "Type";
  public static final String ARE_TRIGGER_BOUNDING_BOX_LEFT          = "Bounding box: Left";
  public static final String ARE_TRIGGER_BOUNDING_BOX_TOP           = "Bounding box: Top";
  public static final String ARE_TRIGGER_BOUNDING_BOX_RIGHT         = "Bounding box: Right";
  public static final String ARE_TRIGGER_BOUNDING_BOX_BOTTOM        = "Bounding box: Bottom";
  public static final String ARE_TRIGGER_NUM_VERTICES               = "# vertices";
  public static final String ARE_TRIGGER_FIRST_VERTEX_INDEX         = "First vertex index";
  public static final String ARE_TRIGGER_VALUE                      = "Trigger value";
  public static final String ARE_TRIGGER_CURSOR_INDEX               = "Cursor number";
  public static final String ARE_TRIGGER_DESTINATION_AREA           = "Destination area";
  public static final String ARE_TRIGGER_ENTRANCE_NAME              = "Entrance name";
  public static final String ARE_TRIGGER_FLAGS                      = "Flags";
  public static final String ARE_TRIGGER_INFO_POINT_TEXT            = "Info point text";
  public static final String ARE_TRIGGER_TRAP_DETECTION_DIFFICULTY  = "Trap detection difficulty";
  public static final String ARE_TRIGGER_TRAP_REMOVAL_DIFFICULTY    = "Trap removal difficulty";
  public static final String ARE_TRIGGER_TRAPPED                    = "Is trapped?";
  public static final String ARE_TRIGGER_TRAP_DETECTED              = "Is trap detected?";
  public static final String ARE_TRIGGER_LAUNCH_POINT_X             = "Launch point: X";
  public static final String ARE_TRIGGER_LAUNCH_POINT_Y             = "Launch point: Y";
  public static final String ARE_TRIGGER_KEY                        = "Key";
  public static final String ARE_TRIGGER_SCRIPT                     = "Script";
  public static final String ARE_TRIGGER_SOUND                      = "Sound";
  public static final String ARE_TRIGGER_SPEAKER_POINT_X            = "Speaker point: X";
  public static final String ARE_TRIGGER_SPEAKER_POINT_Y            = "Speaker point: Y";
  public static final String ARE_TRIGGER_SPEAKER_NAME               = "Speaker name";
  public static final String ARE_TRIGGER_DIALOG                     = "Dialogue";
  public static final String ARE_TRIGGER_OVERRIDE_POINT_X           = "Override point: X";
  public static final String ARE_TRIGGER_OVERRIDE_POINT_Y           = "Override point: Y";
  public static final String ARE_TRIGGER_ALTERNATE_POINT_X          = "Alternate point: X";
  public static final String ARE_TRIGGER_ALTERNATE_POINT_Y          = "Alternate point: Y";
  public static final String ARE_TRIGGER_ACTIVATION_POINT_X         = "Activation point: X";
  public static final String ARE_TRIGGER_ACTIVATION_POINT_Y         = "Activation point: Y";

  public static final String[] TYPE_ARRAY = { "Proximity trigger", "Info trigger", "Travel trigger" };

  public static final String[] FLAG_ARRAY = { "No flags set", "Locked", "Trap resets", "Party required",
      "Trap detectable", "Trap set off by enemy", "Tutorial trigger", "Trap set off by NPC", "Trigger silent",
      "Trigger deactivated", "Cannot be passed by NPC", "Use activation point", "Connected to door" };

  private JButton bCalcBoundingBox;

  public ITEPoint() throws Exception {
    super(null, ARE_TRIGGER, StreamUtils.getByteBuffer(196), 0);
  }

  public ITEPoint(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, ARE_TRIGGER + " " + number, buffer, offset);
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
  public void readVertices(ByteBuffer buffer, int offset) throws Exception {
    int firstVertex = ((IsNumeric) getAttribute(ARE_TRIGGER_FIRST_VERTEX_INDEX)).getValue();
    int numVertices = ((IsNumeric) getAttribute(ARE_TRIGGER_NUM_VERTICES)).getValue();
    offset += firstVertex << 2;
    for (int i = 0; i < numVertices; i++) {
      addField(new Vertex(this, buffer, offset + 4 * i, i));
    }
  }

  @Override
  public int updateVertices(int offset, int number) {
    ((DecNumber) getAttribute(ARE_TRIGGER_FIRST_VERTEX_INDEX)).setValue(number);
    int count = 0;
    for (final StructEntry entry : getFields()) {
      if (entry instanceof Vertex) {
        entry.setOffset(offset);
        ((Vertex) entry).realignStructOffsets();
        offset += 4;
        count++;
      }
    }
    ((DecNumber) getAttribute(ARE_TRIGGER_NUM_VERTICES)).setValue(count);
    return count;
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
      int index = ((IsNumeric) getAttribute(ARE_TRIGGER_FIRST_VERTEX_INDEX)).getValue();
      index += ((IsNumeric) getAttribute(ARE_TRIGGER_NUM_VERTICES)).getValue();
      final int offset = ((IsNumeric) getParent().getAttribute(AreResource.ARE_OFFSET_VERTICES)).getValue();
      datatype.setOffset(offset + 4 * index);
      ((AbstractStruct) datatype).realignStructOffsets();
    }
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new TextString(buffer, offset, 32, ARE_TRIGGER_NAME));
    addField(new Bitmap(buffer, offset + 32, 2, ARE_TRIGGER_TYPE, TYPE_ARRAY));
    addField(new DecNumber(buffer, offset + 34, 2, ARE_TRIGGER_BOUNDING_BOX_LEFT));
    addField(new DecNumber(buffer, offset + 36, 2, ARE_TRIGGER_BOUNDING_BOX_TOP));
    addField(new DecNumber(buffer, offset + 38, 2, ARE_TRIGGER_BOUNDING_BOX_RIGHT));
    addField(new DecNumber(buffer, offset + 40, 2, ARE_TRIGGER_BOUNDING_BOX_BOTTOM));
    addField(new DecNumber(buffer, offset + 42, 2, ARE_TRIGGER_NUM_VERTICES));
    addField(new DecNumber(buffer, offset + 44, 4, ARE_TRIGGER_FIRST_VERTEX_INDEX));
    addField(new DecNumber(buffer, offset + 48, 4, ARE_TRIGGER_VALUE));
    addField(new DecNumber(buffer, offset + 52, 4, ARE_TRIGGER_CURSOR_INDEX));
    addField(new ResourceRef(buffer, offset + 56, ARE_TRIGGER_DESTINATION_AREA, "ARE"));
    addField(new TextString(buffer, offset + 64, 32, ARE_TRIGGER_ENTRANCE_NAME));
    addField(new Flag(buffer, offset + 96, 4, ARE_TRIGGER_FLAGS, FLAG_ARRAY));
    addField(new StringRef(buffer, offset + 100, ARE_TRIGGER_INFO_POINT_TEXT));
    addField(new DecNumber(buffer, offset + 104, 2, ARE_TRIGGER_TRAP_DETECTION_DIFFICULTY));
    addField(new DecNumber(buffer, offset + 106, 2, ARE_TRIGGER_TRAP_REMOVAL_DIFFICULTY));
    addField(new Bitmap(buffer, offset + 108, 2, ARE_TRIGGER_TRAPPED, OPTION_NOYES));
    addField(new Bitmap(buffer, offset + 110, 2, ARE_TRIGGER_TRAP_DETECTED, OPTION_NOYES));
    addField(new DecNumber(buffer, offset + 112, 2, ARE_TRIGGER_LAUNCH_POINT_X));
    addField(new DecNumber(buffer, offset + 114, 2, ARE_TRIGGER_LAUNCH_POINT_Y));
    addField(new ResourceRef(buffer, offset + 116, ARE_TRIGGER_KEY, "ITM"));
    addField(new ResourceRef(buffer, offset + 124, ARE_TRIGGER_SCRIPT, "BCS"));
    if (Profile.getEngine() == Profile.Engine.PST) {
      addField(new Unknown(buffer, offset + 132, 40));
      addField(new ResourceRef(buffer, offset + 172, ARE_TRIGGER_SOUND, "WAV"));
      addField(new DecNumber(buffer, offset + 180, 2, ARE_TRIGGER_SPEAKER_POINT_X));
      addField(new DecNumber(buffer, offset + 182, 2, ARE_TRIGGER_SPEAKER_POINT_Y));
      addField(new StringRef(buffer, offset + 184, ARE_TRIGGER_SPEAKER_NAME));
      addField(new ResourceRef(buffer, offset + 188, ARE_TRIGGER_DIALOG, "DLG"));
    } else if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
      addField(new DecNumber(buffer, offset + 132, 2, ARE_TRIGGER_OVERRIDE_POINT_X));
      addField(new DecNumber(buffer, offset + 134, 2, ARE_TRIGGER_OVERRIDE_POINT_Y));
      addField(new DecNumber(buffer, offset + 136, 4, ARE_TRIGGER_ALTERNATE_POINT_X));
      addField(new DecNumber(buffer, offset + 140, 4, ARE_TRIGGER_ALTERNATE_POINT_Y));
      addField(new Unknown(buffer, offset + 144, 52));
    } else {
      addField(new DecNumber(buffer, offset + 132, 2, ARE_TRIGGER_ACTIVATION_POINT_X));
      addField(new DecNumber(buffer, offset + 134, 2, ARE_TRIGGER_ACTIVATION_POINT_Y));
      if (Profile.getGame() == Profile.Game.PSTEE) {
        addField(new Unknown(buffer, offset + 136, 36));
        addField(new ResourceRef(buffer, offset + 172, ARE_TRIGGER_SOUND, "WAV"));
        addField(new DecNumber(buffer, offset + 180, 2, ARE_TRIGGER_SPEAKER_POINT_X));
        addField(new DecNumber(buffer, offset + 182, 2, ARE_TRIGGER_SPEAKER_POINT_Y));
        addField(new StringRef(buffer, offset + 184, ARE_TRIGGER_SPEAKER_NAME));
        addField(new ResourceRef(buffer, offset + 188, ARE_TRIGGER_DIALOG, "DLG"));
      } else {
        addField(new Unknown(buffer, offset + 136, 60));
      }
    }
    return offset + 196;
  }

  private void updateBoundingBox() {
    final AreResource are = (AreResource)getRoot();
    if (are == null) {
      return;
    }

    final int ofsVertices = ((IsNumeric)are.getAttribute(AreResource.ARE_OFFSET_VERTICES)).getValue();
    final int idxVertices = ((IsNumeric)getAttribute(ARE_TRIGGER_FIRST_VERTEX_INDEX)).getValue();
    final int cntVertices = ((IsNumeric)getAttribute(ARE_TRIGGER_NUM_VERTICES)).getValue();
    final BoundingBox box = BoundingBox.calculateBoundingBox(this, ofsVertices, idxVertices, cntVertices);
    ((DecNumber)getAttribute(ARE_TRIGGER_BOUNDING_BOX_LEFT)).setValue(box.minX);
    ((DecNumber)getAttribute(ARE_TRIGGER_BOUNDING_BOX_TOP)).setValue(box.minY);
    ((DecNumber)getAttribute(ARE_TRIGGER_BOUNDING_BOX_RIGHT)).setValue(box.maxX);
    ((DecNumber)getAttribute(ARE_TRIGGER_BOUNDING_BOX_BOTTOM)).setValue(box.maxY);
    fireTableDataChanged();
  }
}
