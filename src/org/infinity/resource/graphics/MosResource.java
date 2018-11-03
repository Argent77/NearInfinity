// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ProgressMonitor;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import org.infinity.NearInfinity;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.ReferenceSearcher;
import org.infinity.util.DynamicArray;
import org.infinity.util.IntegerHashMap;
import org.infinity.util.io.StreamUtils;

/**
 * This resource describes static graphics in a tile based bitmap format.
 * Such files are used for mini-maps and GUI backgrounds.
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/mos_v1.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/mos_v1.htm</a>
 */
public class MosResource implements Resource, ActionListener, PropertyChangeListener
{
  private static final ButtonPanel.Control Properties = ButtonPanel.Control.CUSTOM_1;

  private static boolean enableTransparency = true;

  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();

  private MosDecoder.Type mosType;
  private JMenuItem miExport, miExportMOSV1, miExportMOSC, miExportPNG;
  private JCheckBox cbTransparency;
  private RenderCanvas rcImage;
  private JPanel panel;
  private RootPaneContainer rpc;
  private SwingWorker<List<byte[]>, Void> workerConvert;
  private boolean exportCompressed;
  private WindowBlocker blocker;

  public MosResource(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
  }

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == cbTransparency) {
      enableTransparency = cbTransparency.isSelected();
      if (mosType == MosDecoder.Type.MOSV1 || mosType == MosDecoder.Type.MOSC) {
        WindowBlocker.blockWindow(true);
        try {
          rcImage.setImage(loadImage());
          WindowBlocker.blockWindow(false);
        } catch (Exception e) {
        }
        WindowBlocker.blockWindow(false);
      }
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES) == event.getSource()) {
      new ReferenceSearcher(entry, panel.getTopLevelAncestor());
    } else if (buttonPanel.getControlByType(Properties) == event.getSource()) {
      showProperties();
    } else if (event.getSource() == miExport) {
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    } else if (event.getSource() == miExportMOSV1) {
      if (mosType == MosDecoder.Type.MOSV2) {
        // create new MOS V1 from scratch
        blocker = new WindowBlocker(rpc);
        blocker.setBlocked(true);
        startConversion(false);
      } else {
        if (mosType == MosDecoder.Type.MOSC) {
          // decompress existing MOSC V1 and save as MOS V1
          try {
            ByteBuffer buffer = entry.getResourceBuffer();
            buffer = Compressor.decompress(buffer);
            ResourceFactory.exportResource(entry, buffer, entry.getResourceName(), panel.getTopLevelAncestor());
          } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                          "Error while exporting " + entry, "Error",
                                          JOptionPane.ERROR_MESSAGE);
          }
        }
      }
    } else if (event.getSource() == miExportMOSC) {
      if (mosType == MosDecoder.Type.MOSV2) {
        // create new MOSC V1 from scratch
        blocker = new WindowBlocker(rpc);
        blocker.setBlocked(true);
        startConversion(true);
      } else if (mosType == MosDecoder.Type.MOSV1) {
        // compress existing MOS V1 and save as MOSC V1
        try {
          ByteBuffer buffer = entry.getResourceBuffer();
          buffer = Compressor.compress(buffer, "MOSC", "V1  ");
          ResourceFactory.exportResource(entry, buffer, entry.getResourceName(), panel.getTopLevelAncestor());
        } catch (Exception e) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                        "Error while exporting " + entry, "Error",
                                        JOptionPane.ERROR_MESSAGE);
        }
      }
    } else if (event.getSource() == miExportPNG) {
      try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        final String fileName = StreamUtils.replaceFileExtension(entry.getResourceName(), "PNG");
        boolean bRet = false;
        WindowBlocker.blockWindow(true);
        try {
          BufferedImage image = getImage();
          bRet = ImageIO.write(image, "png", os);
          image = null;
        } finally {
          WindowBlocker.blockWindow(false);
        }
        if (bRet) {
          ResourceFactory.exportResource(entry, StreamUtils.getByteBuffer(os.toByteArray()),
                                         fileName, panel.getTopLevelAncestor());
        } else {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                        "Error while exporting " + entry, "Error",
                                        JOptionPane.ERROR_MESSAGE);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

//--------------------- End Interface ActionListener ---------------------

//--------------------- Begin Interface PropertyChangeListener ---------------------

  @Override
  public void propertyChange(PropertyChangeEvent event)
  {
    if (event.getSource() == workerConvert) {
      if ("state".equals(event.getPropertyName()) &&
          SwingWorker.StateValue.DONE == event.getNewValue()) {
        if (blocker != null) {
          blocker.setBlocked(false);
          blocker = null;
        }
        byte[] mosData = null;
        try {
          List<byte[]> l = workerConvert.get();
          if (l != null && !l.isEmpty()) {
            mosData = l.get(0);
            l.clear();
            l = null;
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        if (mosData != null) {
          if (mosData.length > 0) {
            ResourceFactory.exportResource(entry, StreamUtils.getByteBuffer(mosData),
                                           entry.getResourceName(), panel.getTopLevelAncestor());
          } else {
            JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                          "Export has been cancelled." + entry, "Information",
                                          JOptionPane.INFORMATION_MESSAGE);
          }
          mosData = null;
        } else {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                        "Error while exporting " + entry, "Error",
                                        JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

//--------------------- End Interface PropertyChangeListener ---------------------

//--------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry()
  {
    return entry;
  }

//--------------------- End Interface Resource ---------------------

//--------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    if (container instanceof RootPaneContainer) {
      rpc = (RootPaneContainer)container;
    } else {
      rpc = NearInfinity.getInstance();
    }

    mosType = MosDecoder.getType(entry);

    ((JButton)buttonPanel.addControl(ButtonPanel.Control.FIND_REFERENCES)).addActionListener(this);

    miExport = new JMenuItem("original");
    miExport.addActionListener(this);
    miExportPNG = new JMenuItem("as PNG");
    miExportPNG.addActionListener(this);
    if (mosType == MosDecoder.Type.MOSV2) {
      miExportMOSV1 = new JMenuItem("as MOS V1 (uncompressed)");
      miExportMOSV1.addActionListener(this);
      miExportMOSC = new JMenuItem("as MOS V1 (compressed)");
      miExportMOSC.addActionListener(this);
    } else {
      if (mosType == MosDecoder.Type.MOSC) {
        miExportMOSV1 = new JMenuItem("decompressed");
        miExportMOSV1.addActionListener(this);
      } else {
        if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
          miExportMOSC = new JMenuItem("compressed");
          miExportMOSC.addActionListener(this);
        }
      }
    }
    List<JMenuItem> list = new ArrayList<JMenuItem>();
    if (miExport != null)
      list.add(miExport);
    if (miExportMOSV1 != null)
      list.add(miExportMOSV1);
    if (miExportMOSC != null)
      list.add(miExportMOSC);
    if (miExportPNG != null)
      list.add(miExportPNG);
    JMenuItem[] mi = new JMenuItem[list.size()];
    for (int i = 0; i < mi.length; i++) {
      mi[i] = list.get(i);
    }
    ButtonPopupMenu bpmExport = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.EXPORT_MENU);
    bpmExport.setMenuItems(mi);

    JButton bProperties = new JButton("Properties...", Icons.getIcon(Icons.ICON_EDIT_16));
    bProperties.addActionListener(this);
    buttonPanel.addControl(bProperties, Properties);

    rcImage = new RenderCanvas();
    rcImage.setHorizontalAlignment(SwingConstants.CENTER);
    rcImage.setVerticalAlignment(SwingConstants.CENTER);
    WindowBlocker.blockWindow(true);
    try {
      rcImage.setImage(loadImage());
      WindowBlocker.blockWindow(false);
    } catch (Exception e) {
      WindowBlocker.blockWindow(false);
    }
    JScrollPane scroll = new JScrollPane(rcImage);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.getHorizontalScrollBar().setUnitIncrement(16);

    cbTransparency = new JCheckBox("Enable transparency", enableTransparency);
    cbTransparency.setEnabled(mosType == MosDecoder.Type.MOSV1 || mosType == MosDecoder.Type.MOSC);
    cbTransparency.setToolTipText("Affects only legacy MOS resources (MOS v1)");
    cbTransparency.addActionListener(this);
    JPanel optionsPanel = new JPanel();
    BoxLayout bl = new BoxLayout(optionsPanel, BoxLayout.Y_AXIS);
    optionsPanel.setLayout(bl);
    optionsPanel.add(cbTransparency);
    buttonPanel.addControl(optionsPanel);

    panel = new JPanel(new BorderLayout());
    panel.add(scroll, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);
    scroll.setBorder(BorderFactory.createLoweredBevelBorder());

    return panel;
  }

//--------------------- End Interface Viewable ---------------------

  public BufferedImage getImage()
  {
    if (rcImage != null) {
      return ColorConvert.toBufferedImage(rcImage.getImage(), true);
    } else if (entry != null) {
      return loadImage();
    }
    return null;
  }

  // Shows message box about basic resource properties
  private void showProperties()
  {
    MosDecoder decoder = null;
    try {
      decoder = MosDecoder.loadMos(entry);
      String resName = entry.getResourceName().toUpperCase(Locale.ENGLISH);
      int width = decoder.getWidth();
      int height = decoder.getHeight();
      String br = "<br />";
      StringBuilder pageList = new StringBuilder();
      String type;
      switch (decoder.getType()) {
        case MOSV1:
          type = "MOS V1 (uncompressed)";
          break;
        case MOSC:
          type = "MOS V1 (compressed)";
          break;
        case MOSV2:
        {
          type = "MOS V2";
          Set<Integer> pvrzPages = ((MosV2Decoder)decoder).getReferencedPVRZPages();
          int counter = 8;
          for (Integer page: pvrzPages) {
            if (pageList.length() > 0) {
              pageList.append(", ");
              if (counter == 0) {
                pageList.append(br);
                counter = 8;
              }
            }
            pageList.append(page.toString());
            counter--;
          }
          break;
        }
        default:
          type = "Undetermined";
      }

      StringBuilder sb = new StringBuilder("<html><div style='font-family:monospace'>");
      sb.append("Type:&nbsp;&nbsp;&nbsp;").append(type).append(br);
      sb.append("Width:&nbsp;&nbsp;").append(width).append(br);
      sb.append("Height:&nbsp;").append(height).append(br);
      if (decoder.getType() == MosDecoder.Type.MOSV2) {
        sb.append(br).append("Referenced PVRZ pages:").append(br);
        sb.append(pageList.toString()).append(br);
      }
      sb.append("</code></html>");
      JOptionPane.showMessageDialog(panel, sb.toString(), "Properties of " + resName,
                                    JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (decoder != null) {
        decoder.close();
      }
    }
  }

  private BufferedImage loadImage()
  {
    BufferedImage image = null;
    mosType = MosDecoder.getType(entry);
    if (mosType != MosDecoder.Type.INVALID) {
      MosDecoder decoder = null;
      if (entry != null) {
        try {
          decoder = MosDecoder.loadMos(entry);
          if (decoder instanceof MosV1Decoder) {
            ((MosV1Decoder)decoder).setTransparencyEnabled(enableTransparency);
          }
          mosType = decoder.getType();
          image = ColorConvert.toBufferedImage(decoder.getImage(), true);
          decoder.close();
          decoder = null;
        } catch (Exception e) {
          e.printStackTrace();
          if (decoder != null) {
            decoder.close();
          }
          image = null;
        }
      }
    } else {
      image = ColorConvert.createCompatibleImage(1, 1, true);
    }
    return image;
  }

  // Creates a new MOS V1 or MOSC V1 resource from scratch. DO NOT call directly!
  private byte[] convertToMosV1(boolean compressed) throws Exception
  {
    byte[] buf = null;
    if (rcImage != null && rcImage.getImage() != null) {
      // preparing source image
      Image img = rcImage.getImage();
      BufferedImage srcImage = ColorConvert.createCompatibleImage(img.getWidth(null),
                                                                  img.getHeight(null),
                                                                  Transparency.BITMASK);
      Graphics2D g = srcImage.createGraphics();
      g.drawImage(getImage(), 0, 0, null);
      g.dispose();
      g = null;

      // preparing MOS V1 header
      int width = srcImage.getWidth();
      int height = srcImage.getHeight();
      int cols = (width + 63) / 64;
      int rows = (height + 63) / 64;
      int tileCount = cols * rows;
      int palOfs = 24;
      int tableOfs = palOfs + tileCount*1024;
      int dataOfs = tableOfs + tileCount*4;
      buf = new byte[dataOfs + width*height];
      System.arraycopy("MOS V1  ".getBytes(), 0, buf, 0, 8);
      DynamicArray.putShort(buf, 8, (short)width);
      DynamicArray.putShort(buf, 10, (short)height);
      DynamicArray.putShort(buf, 12, (short)cols);
      DynamicArray.putShort(buf, 14, (short)rows);
      DynamicArray.putInt(buf, 16, 64);
      DynamicArray.putInt(buf, 20, palOfs);

      String note = "Converting tile %d / %d";
      int progressIndex = 0, progressMax = tileCount;
      ProgressMonitor progress =
          new ProgressMonitor(panel.getTopLevelAncestor(), "Converting MOS...",
                              String.format(note, progressIndex, progressMax), 0, progressMax);
      progress.setMillisToDecideToPopup(500);
      progress.setMillisToPopup(2000);

      // creating list of tiles as int[] arrays
      List<int[]> tileList = new ArrayList<int[]>(cols*rows);
      for (int y = 0; y < rows; y++) {
        for (int x = 0; x < cols; x++) {
          int tileX = x * 64;
          int tileY = y * 64;
          int tileW = (tileX + 64 < width) ? 64 : (width - tileX);
          int tileH = (tileY + 64 < height) ? 64 : (height - tileY);
          int[] rgbArray = new int[tileW*tileH];
          srcImage.getRGB(tileX, tileY, tileW, tileH, rgbArray, 0, tileW);
          tileList.add(rgbArray);
        }
      }
      srcImage.flush(); srcImage = null;

      // applying color reduction to each tile
      int[] palette = new int[255];
      byte[] tilePalette = new byte[1024];
      byte[] tileData = new byte[64*64];
      int curPalOfs = palOfs, curTableOfs = tableOfs, curDataOfs = dataOfs;
      IntegerHashMap<Byte> colorCache = new IntegerHashMap<Byte>(1536);   // caching RGBColor -> index
      for (int tileIdx = 0; tileIdx < tileList.size(); tileIdx++) {
        colorCache.clear();
        if (progress.isCanceled()) {
          buf = new byte[0];
          break;
        }
        progressIndex++;
        if ((progressIndex % 10) == 0) {
          progress.setProgress(progressIndex);
          progress.setNote(String.format(note, progressIndex, progressMax));
        }

        int[] pixels = tileList.get(tileIdx);
        if (ColorConvert.medianCut(pixels, 255, palette, true)) {
          // filling palette
          // first palette entry denotes transparency
          tilePalette[0] = tilePalette[2] = tilePalette[3] = 0; tilePalette[1] = (byte)255;
          for (int i = 1; i < 256; i++) {
            tilePalette[(i << 2) + 0] = (byte)(palette[i - 1] & 0xff);
            tilePalette[(i << 2) + 1] = (byte)((palette[i - 1] >>> 8) & 0xff);
            tilePalette[(i << 2) + 2] = (byte)((palette[i - 1] >>> 16) & 0xff);
            tilePalette[(i << 2) + 3] = 0;
            colorCache.put(palette[i - 1], (byte)(i - 1));
          }
          // filling pixel data
          for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] & 0xff000000) == 0) {
              tileData[i] = 0;
            } else {
              Byte palIndex = colorCache.get(pixels[i]);
              if (palIndex != null) {
                tileData[i] = (byte)(palIndex + 1);
              } else {
                byte color = (byte)ColorConvert.nearestColorRGB(pixels[i], palette, true);
                tileData[i] = (byte)(color + 1);
                colorCache.put(pixels[i], color);
              }
            }
          }
        } else {
          buf = null;
          break;
        }

        System.arraycopy(tilePalette, 0, buf, curPalOfs, 1024);
        curPalOfs += 1024;
        DynamicArray.putInt(buf, curTableOfs, curDataOfs - dataOfs);
        curTableOfs += 4;
        System.arraycopy(tileData, 0, buf, curDataOfs, pixels.length);
        curDataOfs += pixels.length;
      }
      tileList.clear(); tileList = null;
      tileData = null; tilePalette = null; palette = null;

      // optionally compressing to MOSC V1
      if (compressed) {
        if (buf != null) {
          buf = Compressor.compress(buf, "MOSC", "V1  ");
        }
      }
      progress.close();
    }
    return buf;
  }

  // Starts the worker thread for MOS conversion
  private void startConversion(boolean compressed)
  {
    exportCompressed = compressed;
    workerConvert = new SwingWorker<List<byte[]>, Void>() {
      @Override
      public List<byte[]> doInBackground()
      {
        List<byte[]> list = new Vector<byte[]>(1);
        try {
          byte[] buf = convertToMosV1(exportCompressed);
          if (buf != null) {
            list.add(buf);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        return list;
      }
    };
    workerConvert.addPropertyChangeListener(this);
    workerConvert.execute();
  }

  /** Returns whether the specified PVRZ index can be found in the current MOS resource. */
  public boolean containsPvrzReference(int index)
  {
    boolean retVal = false;
    if (index >= 0 && index <= 99999) {
      try {
        InputStream is = entry.getResourceDataAsStream();
        if (is != null) {
          try {
            // parsing resource header
            byte[] sig = new byte[8];
            byte[] buf = new byte[16];
            long len;
            long curOfs = 0;
            if ((len = is.read(sig)) != sig.length) throw new Exception();
            if (!"MOS V2  ".equals(DynamicArray.getString(sig, 0, 8))) throw new Exception();
            curOfs += len;
            if ((len = is.read(buf)) != buf.length) throw new Exception();
            curOfs += len;
            int numBlocks = DynamicArray.getInt(buf, 8);
            int ofsBlocks = DynamicArray.getInt(buf, 12);
            curOfs = ofsBlocks - curOfs;
            if (curOfs > 0) {
              do {
                len = is.skip(curOfs);
                if (len <= 0) throw new Exception();
                curOfs -= len;
              } while (curOfs > 0);
            }

            // parsing blocks
            buf = new byte[28];
            for (int i = 0; i < numBlocks && !retVal; i++) {
              if (is.read(buf) != buf.length) throw new Exception();
              int curIndex = DynamicArray.getInt(buf, 0);
              retVal = (curIndex == index);
            }
          } finally {
            is.close();
            is = null;
          }
        }
      } catch (Exception e) {
      }
    }
    return retVal;
  }
}
