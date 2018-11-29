// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.infinity.gui.RenderCanvas;
import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewerUtil;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.util.Misc;


/** Implements a RGB color picker control. */
public class ColorPicker extends Datatype implements Editable, IsNumeric, MouseListener, FocusListener
{
  /** Supported color formats. */
  public enum Format {
    /** Byte order: {unused, red, green, blue} */
    XRGB,
    /** Byte order: {red, green, blue, unused} */
    RGBX,
    /** Byte order: {blue, green red, unused} */
    BGRX,
    /** Byte order: {unused blue, green red} */
    XBGR,
  }

  private final int shiftRed, shiftGreen, shiftBlue;

  private RenderCanvas rcMainPreview, rcSecondPreview, rcColorPreview;
  private JTextField tfHue, tfSat, tfBri, tfRed, tfGreen, tfBlue;
  private int tmpHue, tmpSat, tmpBri, tmpRed, tmpGreen, tmpBlue;
  private int value;

  /** Initializing color picker with the most commonly used color format {@link Format#XRGB}. */
  public ColorPicker(ByteBuffer buffer, int offset, String name)
  {
    this(buffer, offset, name, Format.XRGB);
  }

  public ColorPicker(ByteBuffer buffer, int offset, String name, Format fmt)
  {
    super(offset, 4, name);
    switch (fmt) {
      case RGBX: shiftRed = 0; shiftGreen = 8; shiftBlue = 16; break;
      case XRGB: shiftRed = 8; shiftGreen = 16; shiftBlue = 24; break;
      case BGRX: shiftRed = 16; shiftGreen = 8; shiftBlue = 0; break;
      case XBGR: shiftRed = 24; shiftGreen = 16; shiftBlue = 8; break;
      default: shiftRed = shiftGreen = shiftBlue = 0; break;
    }
    read(buffer, offset);
  }

//--------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(ActionListener container)
  {
    rcMainPreview = new RenderCanvas();    // use H as x and B as y
    rcMainPreview.setSize(Misc.getScaledValue(256), Misc.getScaledValue(128));
    rcMainPreview.setScalingEnabled(true);
    rcMainPreview.setPreferredSize(rcMainPreview.getSize());
    rcMainPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    rcMainPreview.addMouseListener(this);
    rcMainPreview.setImage(ColorConvert.createCompatibleImage(rcMainPreview.getWidth(),
                                                              rcMainPreview.getHeight(), false));

    rcSecondPreview = new RenderCanvas();  // use S
    rcSecondPreview.setSize(Misc.getScaledValue(32), Misc.getScaledValue(128));
    rcSecondPreview.setScalingEnabled(true);
    rcSecondPreview.setPreferredSize(rcSecondPreview.getSize());
    rcSecondPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    rcSecondPreview.addMouseListener(this);
    rcSecondPreview.setImage(ColorConvert.createCompatibleImage(rcSecondPreview.getWidth(),
                                                                rcSecondPreview.getHeight(), false));

    JLabel lPreview = new JLabel("Preview");
    rcColorPreview = new RenderCanvas();   // shows currently defined color
    rcColorPreview.setSize(Misc.getScaledValue(64), Misc.getScaledValue(32));
    rcColorPreview.setScalingEnabled(true);
    rcColorPreview.setPreferredSize(rcColorPreview.getSize());
    rcColorPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    rcColorPreview.setBackground(Color.BLACK);
    rcColorPreview.setImage(ColorConvert.createCompatibleImage(rcColorPreview.getWidth(),
                                                               rcColorPreview.getHeight(), false));

    JLabel lHue = new JLabel("H:");
    JLabel lSat = new JLabel("S:");
    JLabel lBri = new JLabel("B:");

    tfHue = new JTextField(4);    // range: [0..359]
    tfHue.addFocusListener(this);
    tfSat = new JTextField(4);    // range: [0..100]
    tfSat.addFocusListener(this);
    tfBri = new JTextField(4);    // range: [0..100]
    tfBri.addFocusListener(this);

    JLabel lHue2 = new JLabel("\u00B0");
    JLabel lSat2 = new JLabel("%");
    JLabel lBri2 = new JLabel("%");

    JLabel lR = new JLabel("R:");
    JLabel lG = new JLabel("G:");
    JLabel lB = new JLabel("B:");

    tfRed = new JTextField(4);    // range: [0..255]
    tfRed.addFocusListener(this);
    tfGreen = new JTextField(4);  // range: [0..255]
    tfGreen.addFocusListener(this);
    tfBlue = new JTextField(4);   // range: [0..255]
    tfBlue.addFocusListener(this);

    JButton bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    GridBagConstraints gbc = new GridBagConstraints();

    // Setting up HSB controls
    JPanel pHSB = new JPanel(new GridBagLayout());

    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pHSB.add(lHue, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0);
    pHSB.add(tfHue, gbc);
    gbc = ViewerUtil.setGBC(gbc, 2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0);
    pHSB.add(lHue2, gbc);

    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    pHSB.add(lSat, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 4, 0, 0), 0, 0);
    pHSB.add(tfSat, gbc);
    gbc = ViewerUtil.setGBC(gbc, 2, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 4, 0, 0), 0, 0);
    pHSB.add(lSat2, gbc);

    gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    pHSB.add(lBri, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 4, 0, 0), 0, 0);
    pHSB.add(tfBri, gbc);
    gbc = ViewerUtil.setGBC(gbc, 2, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 4, 0, 0), 0, 0);
    pHSB.add(lBri2, gbc);


    // Setting up RGB controls
    JPanel pRGB = new JPanel(new GridBagLayout());

    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pRGB.add(lR, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0);
    pRGB.add(tfRed, gbc);

    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    pRGB.add(lG, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 4, 0, 0), 0, 0);
    pRGB.add(tfGreen, gbc);

    gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0);
    pRGB.add(lB, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 4, 0, 0), 0, 0);
    pRGB.add(tfBlue, gbc);


    // Setting up color preview
    JPanel pPreview = new JPanel(new GridBagLayout());
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pPreview.add(lPreview, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pPreview.add(rcColorPreview, gbc);

    // Setting up main controls
    JPanel pControls = new JPanel(new GridBagLayout());
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    pControls.add(pHSB, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 0, 0);
    pControls.add(pRGB, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 2, 1, 1.0, 1.0, GridBagConstraints.LAST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pControls.add(new JPanel(), gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 2, 2, 1, 1.0, 0.0, GridBagConstraints.LAST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pControls.add(pPreview, gbc);

    JPanel pMain = new JPanel(new GridBagLayout());
    pMain.setBorder(BorderFactory.createEtchedBorder());
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH, new Insets(8, 8, 8, 0), 0, 0);
    pMain.add(rcMainPreview, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH, new Insets(8, 8, 8, 0), 0, 0);
    pMain.add(rcSecondPreview, gbc);
    gbc = ViewerUtil.setGBC(gbc, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH, new Insets(8, 16, 8, 8), 0, 0);
    pMain.add(pControls, gbc);

    // Setting up main panel
    JPanel panel = new JPanel(new GridBagLayout());
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(pMain, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                            GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0);
    panel.add(bUpdate, gbc);

    panel.setMinimumSize(panel.getPreferredSize());

    updateColorValues(value);
    return panel;
  }

  @Override
  public void select()
  {
    updateColorValues(value);
    updateColor();
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    value = getInputRgbValue();

    // notifying listeners
    fireValueUpdated(new UpdateEvent(this, struct));

    return true;
  }

//--------------------- End Interface Editable ---------------------

//--------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    writeInt(os, value);
  }

//--------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    value = buffer.getInt();
    tmpRed = getRed(value);
    tmpGreen = getGreen(value);
    tmpBlue = getBlue(value);
    float[] hsb = {0.0f, 0.0f, 0.0f};
    Color.RGBtoHSB(tmpRed, tmpGreen, tmpBlue, hsb);
    tmpHue = (int)Math.round(hsb[0]*360.0f);
    tmpSat = (int)Math.round(hsb[1]*100.0f);
    tmpBri = (int)Math.round(hsb[2]*100.0f);

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

//--------------------- Begin Interface MouseListener ---------------------

  @Override
  public void mouseClicked(MouseEvent e)
  {
  }

  @Override
  public void mousePressed(MouseEvent e)
  {
    if (e.getSource() == rcMainPreview && e.getButton() == MouseEvent.BUTTON1) {
      updateMainPreviewValue(e.getX(), e.getY());
      updateRgbFromHsb();
      updateColor();
    } else if (e.getSource() == rcSecondPreview && e.getButton() == MouseEvent.BUTTON1) {
      updateSecondPreviewValue(e.getY());
      updateRgbFromHsb();
      updateColor();
    }
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }

  @Override
  public void mouseEntered(MouseEvent e)
  {
  }

  @Override
  public void mouseExited(MouseEvent e)
  {
  }

//--------------------- End Interface MouseListener ---------------------

//--------------------- Begin Interface FocusListener ---------------------

  @Override
  public void focusGained(FocusEvent e)
  {
    if (e.getSource() instanceof JTextField) {
      ((JTextField)e.getSource()).selectAll();
    }
  }

  @Override
  public void focusLost(FocusEvent e)
  {
    if (e.getSource() == tfRed) {
      int v = validateNumberInput(tfRed, tmpRed, 0, 255);
      if (v != tmpRed) {
        tmpRed = v;
        updateHsbFromRgb();
        updateInputHsb();
        updatePreview();
      }
    } else if (e.getSource() == tfGreen) {
      int v = validateNumberInput(tfGreen, tmpGreen, 0, 255);
      if (v != tmpGreen) {
        tmpGreen = v;
        updateHsbFromRgb();
        updateInputHsb();
        updatePreview();
      }
    } else if (e.getSource() == tfBlue) {
      int v = validateNumberInput(tfBlue, tmpBlue, 0, 255);
      if (v != tmpBlue) {
        tmpBlue = v;
        updateHsbFromRgb();
        updateInputHsb();
        updatePreview();
      }
    } else if (e.getSource() == tfHue) {
      int v = validateNumberInput(tfHue, tmpHue, 0, 360);
      if (v != tmpHue) {
        tmpHue = v;
        updateRgbFromHsb();
        updateInputRgb();
        updatePreview();
      }
    } else if (e.getSource() == tfSat) {
      int v = validateNumberInput(tfSat, tmpSat, 0, 100);
      if (v != tmpSat) {
        tmpSat = v;
        updateRgbFromHsb();
        updateInputRgb();
        updatePreview();
      }
    } else if (e.getSource() == tfBri) {
      int v = validateNumberInput(tfBri, tmpBri, 0, 100);
      if (v != tmpBri) {
        tmpBri = v;
        updateRgbFromHsb();
        updateInputRgb();
        updatePreview();
      }
    }
  }

//--------------------- End Interface FocusListener ---------------------

  @Override
  public String toString()
  {
    return String.format("Red: %d, Green: %d, Blue: %d",
                         getRed(value), getGreen(value), getBlue(value));
  }

//--------------------- Begin Interface IsNumeric ---------------------

  @Override
  public long getLongValue()
  {
    return (long)value & 0xffffffffL;
  }

  @Override
  public int getValue()
  {
    return value;
  }

//--------------------- End Interface IsNumeric ---------------------

  // r, g, b in range [0..255]
  private int getRgbValue(int r, int g, int b)
  {
    return ((r & 0xff) << shiftRed) | ((g & 0xff) << shiftGreen) | ((b & 0xff) << shiftBlue);
  }

  // h, s, b in range [0..1]
  private int getHsbValue(float h, float s, float b)
  {
    if (h < 0.0f) h = 0.0f; else if (h > 1.0f) h = 1.0f;
    if (s < 0.0f) s = 0.0f; else if (s > 1.0f) s = 1.0f;
    if (b < 0.0f) b = 0.0f; else if (b > 1.0f) b = 1.0f;
    Color c = new Color(Color.HSBtoRGB(h, s, b));
    return getRgbValue(c.getRed(), c.getGreen(), c.getBlue());
  }

  private int getRed(int color)
  {
    return (color >>> shiftRed) & 0xff;
  }

  private int getGreen(int color)
  {
    return (color >>> shiftGreen) & 0xff;
  }

  private int getBlue(int color)
  {
    return (color >>> shiftBlue) & 0xff;
  }

  // Returns a color value based on the RGB input fields
  private int getInputRgbValue() throws NumberFormatException
  {
    return getRgbValue(getInputRed(), getInputGreen(), getInputBlue());
  }

  private int getInputRed() throws NumberFormatException
  {
    int v = Integer.parseInt(tfRed.getText());
    if (v < 0) v = 0; else if (v > 255) v = 255;
    return v;
  }

  private int getInputGreen() throws NumberFormatException
  {
    int v = Integer.parseInt(tfGreen.getText());
    if (v < 0) v = 0; else if (v > 255) v = 255;
    return v;
  }

  private int getInputBlue() throws NumberFormatException
  {
    int v = Integer.parseInt(tfBlue.getText());
    if (v < 0) v = 0; else if (v > 255) v = 255;
    return v;
  }

  // Checks and returns either the number fetched from the input field or oldVal on error
  private int validateNumberInput(JTextField tf, int oldVal, int min, int max)
  {
    if (tf != null) {
      try {
        oldVal = Integer.parseInt(tf.getText());
        if (oldVal < min) oldVal = min; else if (oldVal > max) oldVal = max;
      } catch (NumberFormatException nfe) {
      }
      tf.setText(Integer.toString(oldVal));
    }
    return oldVal;
  }

  // Returns color value based on the main preview coordinates and the saturation value from the input field
  private void updateMainPreviewValue(int x, int y)
  {
    if (x < 0) x = 0; else if (x >= rcMainPreview.getWidth()) x = rcMainPreview.getWidth() - 1;
    if (y < 0) y = 0; else if (y >= rcMainPreview.getHeight()) y = rcMainPreview.getHeight() - 1;
    tmpHue = x * 360 / rcMainPreview.getWidth();
    tmpBri = 100 - (y * 100 / rcMainPreview.getHeight());
  }

  // Returns color value based on the secondary preview coordinate and the hue/brightness values from the input fields
  private void updateSecondPreviewValue(int y)
  {
    if (y < 0) y = 0; else if (y >= rcSecondPreview.getHeight()) y = rcSecondPreview.getHeight() - 1;
    tmpSat = 100 - (y * 100 / rcSecondPreview.getHeight());
  }

  private void updateRgbFromHsb()
  {
    Color c = new Color(Color.HSBtoRGB((float)tmpHue / 360.0f, (float)tmpSat / 100.0f, (float)tmpBri / 100.0f));
    tmpRed = c.getRed();
    tmpGreen = c.getGreen();
    tmpBlue = c.getBlue();
  }

  private void updateHsbFromRgb()
  {
    float[] hsb = {0.0f, 0.0f, 0.0f};
    Color.RGBtoHSB(tmpRed, tmpGreen, tmpBlue, hsb);
    tmpHue = (int)Math.round(hsb[0]*360.0f);
    tmpSat = (int)Math.round(hsb[1]*100.0f);
    tmpBri = (int)Math.round(hsb[2]*100.0f);
  }

  // Updates all temporary values for each color component
  private void updateColorValues(int value)
  {
    tmpRed = getRed(value);
    tmpGreen = getGreen(value);
    tmpBlue = getBlue(value);
    updateHsbFromRgb();
  }

  // Update RGB input controls only
  private void updateInputRgb()
  {
    tfRed.setText(Integer.toString(tmpRed));
    if (tfRed.hasFocus()) tfRed.selectAll();
    tfGreen.setText(Integer.toString(tmpGreen));
    if (tfGreen.hasFocus()) tfGreen.selectAll();
    tfBlue.setText(Integer.toString(tmpBlue));
    if (tfBlue.hasFocus()) tfBlue.selectAll();
  }

  // Update HSB input controls only
  private void updateInputHsb()
  {
    tfHue.setText(Integer.toString(tmpHue));
    if (tfHue.hasFocus()) tfHue.selectAll();
    tfSat.setText(Integer.toString(tmpSat));
    if (tfSat.hasFocus()) tfSat.selectAll();
    tfBri.setText(Integer.toString(tmpBri));
    if (tfBri.hasFocus()) tfBri.selectAll();
  }

  // Update preview controls only
  private void updatePreview()
  {
    // update main preview
    updateMainPreview();

    // update secondary preview
    updateSecondPreview();

    // update color preview
    updateColorPreview();
  }

  // Update controls with given color value
  private void updateColor()
  {
    updateInputRgb();
    updateInputHsb();
    updatePreview();
  }

  // Update main (hue/brightness) preview
  private void updateMainPreview()
  {
    // drawing background
    initMainPreviewMap();

    // drawing marker
    BufferedImage image = (BufferedImage)rcMainPreview.getImage();
    if (image != null) {
      Graphics2D g = image.createGraphics();
      if (g != null) {
        try {
          int x = tmpHue * image.getWidth() / 360;
          int y = (100 - tmpBri) * image.getHeight() / 100;
          g.setColor(Color.WHITE);
          g.drawLine(x + 2, y, x + 6, y);
          g.drawLine(x - 2, y, x - 6, y);
          g.drawLine(x, y + 2, x, y + 6);
          g.drawLine(x, y - 2, x, y - 6);
        } finally {
          g.dispose();
          g = null;
        }
      }
    }
    rcMainPreview.repaint();
  }

  // Update secondary (saturation) preview
  private void updateSecondPreview()
  {
    BufferedImage image = (BufferedImage)rcSecondPreview.getImage();
    if (image != null) {
      int width = image.getWidth();
      int height = image.getHeight();
      int type = image.getRaster().getDataBuffer().getDataType();
      if (type == DataBuffer.TYPE_INT) {
        int[] buffer = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();

        // drawing gradient and marker
        float h = (float)tmpHue / 360.0f;
        float b = (float)tmpBri / 100.0f;
        int marker = (100 - tmpSat) * height / 100;
        for (int y = 0; y < height; y++) {
          float sat = 1.0f - ((float)y / (float)height);
          int rgb = (y == marker) ? 0xffffff : Color.HSBtoRGB(h, sat, b);
          int ofs = y*width;
          for (int x = 0; x < width; x++, ofs++) {
            buffer[ofs] = rgb;
          }
        }
      }
    }
    rcSecondPreview.repaint();
  }

  // Update color preview box
  private void updateColorPreview()
  {
    BufferedImage image = (BufferedImage)rcColorPreview.getImage();
    if (image != null) {
      int type = image.getRaster().getDataBuffer().getDataType();
      if (type == DataBuffer.TYPE_INT) {
        int[] buffer = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        int rgb = (tmpRed << 16) | (tmpGreen << 8) | tmpBlue;
        Arrays.fill(buffer, rgb);
      }
    }
    rcColorPreview.repaint();
  }

  // Update main preview background map
  private void initMainPreviewMap()
  {
    BufferedImage image = (BufferedImage)rcMainPreview.getImage();
    if (image != null) {
      int width = image.getWidth();
      int height = image.getHeight();
      int[] buffer = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
      if (buffer != null) {
        float s = (float)tmpSat / 100.0f;
        for (int y = 0; y < height; y++) {
          float b = 1.0f - ((float)y / (float)height);
          for (int x = 0; x < width; x++) {
            float h = (float)x / (float) width;
            int rgb = getHsbValue(h, s, b);
            buffer[y*width + x] = (getRed(rgb) << 16) | (getGreen(rgb) << 8) | getBlue(rgb);
          }
        }
      }
    }
  }
}
