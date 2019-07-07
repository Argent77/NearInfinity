// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.chu;

import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.ColorPicker;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.HexNumber;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;

final class Control extends AbstractStruct // implements AddRemovable
{
  // CHU/Control-specific field labels
  public static final String CHU_CONTROL                                = "Control";
  public static final String CHU_CONTROL_OFFSET                         = "Offset";
  public static final String CHU_CONTROL_LENGTH                         = "Length";
  public static final String CHU_CONTROL_ID                             = "Control ID";
  public static final String CHU_CONTROL_BUFFER_LENGTH                  = "Buffer length";
  public static final String CHU_CONTROL_POSITION_X                     = "Position: X";
  public static final String CHU_CONTROL_POSITION_Y                     = "Position: Y";
  public static final String CHU_CONTROL_WIDTH                          = "Width";
  public static final String CHU_CONTROL_HEIGHT                         = "Height";
  public static final String CHU_CONTROL_TYPE                           = "Type";
  public static final String CHU_CONTROL_BTN_RESREF                     = "Button";
  public static final String CHU_CONTROL_BTN_ANIMATION_INDEX            = "Animation number";
  public static final String CHU_CONTROL_BTN_FLAGS                      = "Text flags";
  public static final String CHU_CONTROL_BTN_FRAME_INDEX_UNPRESSED      = "Frame number: Unpressed";
  public static final String CHU_CONTROL_BTN_FRAME_INDEX_PRESSED        = "Frame number: Pressed";
  public static final String CHU_CONTROL_BTN_FRAME_INDEX_SELECTED       = "Frame number: Selected";
  public static final String CHU_CONTROL_BTN_FRAME_INDEX_DISABLED       = "Frame number: Disabled";
  public static final String CHU_CONTROL_BTN_TEXT_ANCHOR_LEFT           = "Text anchor: Left";
  public static final String CHU_CONTROL_BTN_TEXT_ANCHOR_RIGHT          = "Text anchor: Right";
  public static final String CHU_CONTROL_BTN_TEXT_ANCHOR_TOP            = "Text anchor: Top";
  public static final String CHU_CONTROL_BTN_TEXT_ANCHOR_BOTTOM         = "Text anchor: Bottom";
  public static final String CHU_CONTROL_SLD_BACKGROUND                 = "Background image";
  public static final String CHU_CONTROL_SLD_KNOB                       = "Slider knob";
  public static final String CHU_CONTROL_SLD_ANIMATION_INDEX            = "Animation number";
  public static final String CHU_CONTROL_SLD_FRAME_INDEX_UNGRABBED      = "Frame number: Ungrabbed";
  public static final String CHU_CONTROL_SLD_FRAME_INDEX_GRABBED        = "Frame number: Grabbed";
  public static final String CHU_CONTROL_SLD_KNOB_POSITION_X            = "Knob position: X";
  public static final String CHU_CONTROL_SLD_KNOB_POSITION_Y            = "Knob position: Y";
  public static final String CHU_CONTROL_SLD_STEP_WIDTH                 = "Step width";
  public static final String CHU_CONTROL_SLD_STEP_COUNT                 = "Step count";
  public static final String CHU_CONTROL_SLD_REGION_TOP                 = "Slider region: Top";
  public static final String CHU_CONTROL_SLD_REGION_BOTTOM              = "Slider region: Bottom";
  public static final String CHU_CONTROL_SLD_REGION_LEFT                = "Slider region: Left";
  public static final String CHU_CONTROL_SLD_REGION_RIGHT               = "Slider region: Right";
  public static final String CHU_CONTROL_TF_BACKGROUND_1                = "Background 1";
  public static final String CHU_CONTROL_TF_BACKGROUND_2                = "Background 2";
  public static final String CHU_CONTROL_TF_BACKGROUND_3                = "Background 3";
  public static final String CHU_CONTROL_TF_CARET                       = "Caret";
  public static final String CHU_CONTROL_TF_ANIMATION_INDEX             = "Animation number";
  public static final String CHU_CONTROL_TF_FRAME_INDEX                 = "Frame number";
  public static final String CHU_CONTROL_TF_CARET_POSITION_X            = "Caret position: X";
  public static final String CHU_CONTROL_TF_CARET_POSITION_Y            = "Caret position: Y";
  public static final String CHU_CONTROL_TF_ID                          = "Text field ID";
  public static final String CHU_CONTROL_TF_FONT                        = "Font";
  public static final String CHU_CONTROL_TF_TEXT                        = "Initial text";
  public static final String CHU_CONTROL_TF_FIELD_LENGTH                = "Field length";
  public static final String CHU_CONTROL_TF_ALLOWED_CSE                 = "Allowed case";
  public static final String CHU_CONTROL_TA_FONT_MAIN                   = "Font (main text)";
  public static final String CHU_CONTROL_TA_FONT_INITIALS               = "Font (initials)";
  public static final String CHU_CONTROL_TA_COLOR_1                     = "Color 1";
  public static final String CHU_CONTROL_TA_COLOR_2                     = "Color 2";
  public static final String CHU_CONTROL_TA_COLOR_3                     = "Color 3";
  public static final String CHU_CONTROL_TA_SCROLLBAR_ID                = "Scroll bar ID";
  public static final String CHU_CONTROL_LBL_TEXT                       = "Initial text";
  public static final String CHU_CONTROL_LBL_FONT                       = "Font";
  public static final String CHU_CONTROL_LBL_COLOR_1                    = "Color 1";
  public static final String CHU_CONTROL_LBL_COLOR_2                    = "Color 2";
  public static final String CHU_CONTROL_LBL_FLAGS                      = "Text flags";
  public static final String CHU_CONTROL_SB_GRAPHICS                    = "Graphics";
  public static final String CHU_CONTROL_SB_ANIMATION_INDEX             = "Animation number";
  public static final String CHU_CONTROL_SB_FRAME_INDEX_UP_UNPRESSED    = "Frame number: Up-arrow, unpressed";
  public static final String CHU_CONTROL_SB_FRAME_INDEX_UP_PRESSED      = "Frame number: Up-arrow, pressed";
  public static final String CHU_CONTROL_SB_FRAME_INDEX_DOWN_UNPRESSED  = "Frame number: Down-arrow, unpressed";
  public static final String CHU_CONTROL_SB_FRAME_INDEX_DOWN_PRESSED    = "Frame number: Down-arrow, pressed";
  public static final String CHU_CONTROL_SB_FRAME_INDEX_TROUGH          = "Frame number: Trough";
  public static final String CHU_CONTROL_SB_FRAME_INDEX_SLIDER          = "Frame number: Slider";
  public static final String CHU_CONTROL_SB_TEXT_ID                     = "Text area ID";

  private static final String[] s_type = {"Button", "", "Slider", "Text field", "",
                                          "Text area", "Label", "Scroll bar"};
  private static final String[] s_button = {"Center", "Left justify", "Right justify",
                                            "Top justify", "Bottom justify", "Anchor",
                                            "Reduce size", "Don't wrap"};
  private static final String[] s_label = {"Center", "Use color", "Truecolor", "Center justify",
                                           "Left justify", "Right justify", "Top justify",
                                           "Middle justify", "Bottom justify", "Word wrap"};
  private static final String[] s_case = {"Normal case", "Upper case only", "Lower case only"};

  private final int size;

  Control(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number, int size) throws Exception
  {
    super(superStruct, CHU_CONTROL + " " + number, buffer, offset);
    this.size = size;
  }

  //<editor-fold defaultstate="collapsed" desc="Writable">
  @Override
  public void write(OutputStream os) throws IOException
  {
    getField(0).write(os);
    getField(1).write(os);
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Readable">
  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    addField(new HexNumber(buffer, offset, 4, CHU_CONTROL_OFFSET));
    addField(new HexNumber(buffer, offset + 4, 4, CHU_CONTROL_LENGTH));
    return offset + 8;
  }
  //</editor-fold>

  /** Returns the control id. */
  public int getControlId()
  {
    return ((DecNumber)getAttribute(CHU_CONTROL_ID)).getValue();
  }

  /** Returns the x and y position of the control. */
  public Point getControlPosition()
  {
    return new Point(((DecNumber)getAttribute(CHU_CONTROL_POSITION_X)).getValue(),
                     ((DecNumber)getAttribute(CHU_CONTROL_POSITION_Y)).getValue());
  }

  /** Returns the width and height of the control. */
  public Dimension getControlDimensions()
  {
    return new Dimension(((DecNumber)getAttribute(CHU_CONTROL_WIDTH)).getValue(),
                         ((DecNumber)getAttribute(CHU_CONTROL_HEIGHT)).getValue());
  }

  /** Returns the control type. */
  public int getControlType()
  {
    return ((Bitmap)getAttribute(CHU_CONTROL_TYPE)).getValue();
  }


  public int readControl(ByteBuffer buffer)
  {
    int offset = ((HexNumber)getAttribute(CHU_CONTROL_OFFSET)).getValue();
    final int endOffset = offset + size;
    addField(new DecNumber(buffer, offset, 2, CHU_CONTROL_ID));
    addField(new DecNumber(buffer, offset + 2, 2, CHU_CONTROL_BUFFER_LENGTH));
    addField(new DecNumber(buffer, offset + 4, 2, CHU_CONTROL_POSITION_X));
    addField(new DecNumber(buffer, offset + 6, 2, CHU_CONTROL_POSITION_Y));
    addField(new DecNumber(buffer, offset + 8, 2, CHU_CONTROL_WIDTH));
    addField(new DecNumber(buffer, offset + 10, 2, CHU_CONTROL_HEIGHT));
    Bitmap type = new Bitmap(buffer, offset + 12, 1, CHU_CONTROL_TYPE, s_type);
    addField(type);
    addField(new Unknown(buffer, offset + 13, 1));

    switch (type.getValue()) {
      case 0: // Button
        addField(new ResourceRef(buffer, offset + 14, CHU_CONTROL_BTN_RESREF, "BAM"));
        addField(new DecNumber(buffer, offset + 22, 1, CHU_CONTROL_BTN_ANIMATION_INDEX));
        addField(new Flag(buffer, offset + 23, 1, CHU_CONTROL_BTN_FLAGS, s_button));
        addField(new DecNumber(buffer, offset + 24, 1, CHU_CONTROL_BTN_FRAME_INDEX_UNPRESSED));
        addField(new DecNumber(buffer, offset + 25, 1, CHU_CONTROL_BTN_TEXT_ANCHOR_LEFT));
        addField(new DecNumber(buffer, offset + 26, 1, CHU_CONTROL_BTN_FRAME_INDEX_PRESSED));
        addField(new DecNumber(buffer, offset + 27, 1, CHU_CONTROL_BTN_TEXT_ANCHOR_RIGHT));
        addField(new DecNumber(buffer, offset + 28, 1, CHU_CONTROL_BTN_FRAME_INDEX_SELECTED));
        addField(new DecNumber(buffer, offset + 29, 1, CHU_CONTROL_BTN_TEXT_ANCHOR_TOP));
        addField(new DecNumber(buffer, offset + 30, 1, CHU_CONTROL_BTN_FRAME_INDEX_DISABLED));
        addField(new DecNumber(buffer, offset + 31, 1, CHU_CONTROL_BTN_TEXT_ANCHOR_BOTTOM));
        offset += 32;
        break;
      case 2: // Slider
        addField(new ResourceRef(buffer, offset + 14, CHU_CONTROL_SLD_BACKGROUND, "MOS"));
        addField(new ResourceRef(buffer, offset + 22, CHU_CONTROL_SLD_KNOB, "BAM"));
        addField(new DecNumber(buffer, offset + 30, 2, CHU_CONTROL_SLD_ANIMATION_INDEX));
        addField(new DecNumber(buffer, offset + 32, 2, CHU_CONTROL_SLD_FRAME_INDEX_UNGRABBED));
        addField(new DecNumber(buffer, offset + 34, 2, CHU_CONTROL_SLD_FRAME_INDEX_GRABBED));
        addField(new DecNumber(buffer, offset + 36, 2, CHU_CONTROL_SLD_KNOB_POSITION_X));
        addField(new DecNumber(buffer, offset + 38, 2, CHU_CONTROL_SLD_KNOB_POSITION_Y));
        addField(new DecNumber(buffer, offset + 40, 2, CHU_CONTROL_SLD_STEP_WIDTH));
        addField(new DecNumber(buffer, offset + 42, 2, CHU_CONTROL_SLD_STEP_COUNT));
        addField(new DecNumber(buffer, offset + 44, 2, CHU_CONTROL_SLD_REGION_TOP));
        addField(new DecNumber(buffer, offset + 46, 2, CHU_CONTROL_SLD_REGION_BOTTOM));
        addField(new DecNumber(buffer, offset + 48, 2, CHU_CONTROL_SLD_REGION_LEFT));
        addField(new DecNumber(buffer, offset + 50, 2, CHU_CONTROL_SLD_REGION_RIGHT));
        offset += 52;
        break;
      case 3: // Text field
        addField(new ResourceRef(buffer, offset + 14, CHU_CONTROL_TF_BACKGROUND_1, "MOS"));
        addField(new ResourceRef(buffer, offset + 22, CHU_CONTROL_TF_BACKGROUND_2, "MOS"));
        addField(new ResourceRef(buffer, offset + 30, CHU_CONTROL_TF_BACKGROUND_3, "MOS"));
        addField(new ResourceRef(buffer, offset + 38, CHU_CONTROL_TF_CARET, "BAM"));
        addField(new DecNumber(buffer, offset + 46, 2, CHU_CONTROL_TF_ANIMATION_INDEX));
        addField(new DecNumber(buffer, offset + 48, 2, CHU_CONTROL_TF_FRAME_INDEX));
        addField(new DecNumber(buffer, offset + 50, 2, CHU_CONTROL_TF_CARET_POSITION_X));
        addField(new DecNumber(buffer, offset + 52, 2, CHU_CONTROL_TF_CARET_POSITION_Y));
        addField(new DecNumber(buffer, offset + 54, 4, CHU_CONTROL_TF_ID));
        if (Profile.isEnhancedEdition()) {
          addField(new ResourceRef(buffer, offset + 58, CHU_CONTROL_TF_FONT, "FNT", "BAM"));
        } else {
          addField(new ResourceRef(buffer, offset + 58, CHU_CONTROL_TF_FONT, "BAM"));
        }
        addField(new Unknown(buffer, offset + 66, 2));
        addField(new TextString(buffer, offset + 68, 32, CHU_CONTROL_TF_TEXT));
        addField(new DecNumber(buffer, offset + 100, 2, CHU_CONTROL_TF_FIELD_LENGTH));
        addField(new Bitmap(buffer, offset + 102, 4, CHU_CONTROL_TF_ALLOWED_CSE, s_case));
        offset += 106;
        break;
      case 5: // Text area
        if (Profile.isEnhancedEdition()) {
          addField(new ResourceRef(buffer, offset + 14, CHU_CONTROL_TA_FONT_MAIN, "FNT", "BAM"));
          addField(new ResourceRef(buffer, offset + 22, CHU_CONTROL_TA_FONT_INITIALS, "FNT", "BAM"));
        } else {
          addField(new ResourceRef(buffer, offset + 14, CHU_CONTROL_TA_FONT_MAIN, "BAM"));
          addField(new ResourceRef(buffer, offset + 22, CHU_CONTROL_TA_FONT_INITIALS, "BAM"));
        }
        addField(new ColorPicker(buffer, offset + 30, CHU_CONTROL_TA_COLOR_1, ColorPicker.Format.RGBX));
        addField(new ColorPicker(buffer, offset + 34, CHU_CONTROL_TA_COLOR_2, ColorPicker.Format.RGBX));
        addField(new ColorPicker(buffer, offset + 38, CHU_CONTROL_TA_COLOR_3, ColorPicker.Format.RGBX));
        addField(new DecNumber(buffer, offset + 42, 4, CHU_CONTROL_TA_SCROLLBAR_ID));
        offset += 46;
        break;
      case 6: // Label
        addField(new StringRef(buffer, offset + 14, CHU_CONTROL_LBL_TEXT));
        if (Profile.isEnhancedEdition()) {
          addField(new ResourceRef(buffer, offset + 18, CHU_CONTROL_LBL_FONT, "FNT", "BAM"));
        } else {
          addField(new ResourceRef(buffer, offset + 18, CHU_CONTROL_LBL_FONT, "BAM"));
        }
        addField(new ColorPicker(buffer, offset + 26, CHU_CONTROL_LBL_COLOR_1, ColorPicker.Format.RGBX));
        addField(new ColorPicker(buffer, offset + 30, CHU_CONTROL_LBL_COLOR_2, ColorPicker.Format.RGBX));
        addField(new Flag(buffer, offset + 34, 2, CHU_CONTROL_LBL_FLAGS, s_label));
        offset += 36;
        break;
      case 7: // Scroll bar
        addField(new ResourceRef(buffer, offset + 14, CHU_CONTROL_SB_GRAPHICS, "BAM"));
        addField(new DecNumber(buffer, offset + 22, 2, CHU_CONTROL_SB_ANIMATION_INDEX));
        addField(new DecNumber(buffer, offset + 24, 2, CHU_CONTROL_SB_FRAME_INDEX_UP_UNPRESSED));
        addField(new DecNumber(buffer, offset + 26, 2, CHU_CONTROL_SB_FRAME_INDEX_UP_PRESSED));
        addField(new DecNumber(buffer, offset + 28, 2, CHU_CONTROL_SB_FRAME_INDEX_DOWN_UNPRESSED));
        addField(new DecNumber(buffer, offset + 30, 2, CHU_CONTROL_SB_FRAME_INDEX_DOWN_PRESSED));
        addField(new DecNumber(buffer, offset + 32, 2, CHU_CONTROL_SB_FRAME_INDEX_TROUGH));
        addField(new DecNumber(buffer, offset + 34, 2, CHU_CONTROL_SB_FRAME_INDEX_SLIDER));
        addField(new DecNumber(buffer, offset + 36, 4, CHU_CONTROL_SB_TEXT_ID));
        offset += 40;
        break;
      default:
        HexNumber len = (HexNumber)getAttribute(CHU_CONTROL_LENGTH);
        addField(new Unknown(buffer, offset + 14, len.getValue() - 14));
        offset += len.getValue();
        break;
    }

    // handling optional gap between controls
    if (offset < endOffset) {
      addField(new Unknown(buffer, offset, endOffset - offset, COMMON_UNUSED));
      offset = endOffset;
    }

    return offset;
  }

  public void writeControl(OutputStream os) throws IOException
  {
    for (int i = 2; i < getFieldCount(); i++)
      getField(i).write(os);
  }
}
