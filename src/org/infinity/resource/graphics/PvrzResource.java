// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.ReferenceSearcher;
import org.infinity.util.io.StreamUtils;

/**
 * This resource is used to store graphics data that can be directly utilised by
 * the video hardware.
 * <p>
 * PVRZ files are basically ZLIB-compressed PVR files. The file format is primarily
 * used in conjunction with {@link BamResource BAM V2}, {@link MosResource MOS V2}
 * and PVRZ-based {@link TisResource TIS} resources. Texture compression for the
 * desktop versions of the games is limited to DXT1 (BC1) and DXT5 (BC3).
 * Compression supported by the mobile versions can vary. Width and height of
 * textures are usually a power of 2, up to a maximum of 1024 pixels.
 * <p>
 * The PVR File Format Specification is available for download from © Imagination
 * Technologies: <a href="https://community.imgtec.com/developers/powervr/documentation/">PowerVR Documentation</a>
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/pvrz.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/pvrz.htm</a>
 */
public class PvrzResource implements Resource, ActionListener, Closeable
{
  private static final ButtonPanel.Control Properties = ButtonPanel.Control.CUSTOM_1;

  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();

  private JMenuItem miExport, miPNG, miPVR;
  private RenderCanvas rcImage;
  private JPanel panel;

  public PvrzResource(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
  }

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES) == event.getSource()) {
      new ReferenceSearcher(entry, new String[]{"BAM", "MOS", "TIS"}, panel.getTopLevelAncestor());
    } else if (buttonPanel.getControlByType(Properties) == event.getSource()) {
      showProperties();
    } else if (event.getSource() == miExport) {
      // export as original PVRZ
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    } else if (event.getSource() == miPVR) {
      ByteBuffer decompressed = null;
      try {
        decompressed = Compressor.decompress(getResourceEntry().getResourceBuffer(), 0);
      } catch (Exception e) {
        decompressed = null;
        e.printStackTrace();
      }
      if (decompressed != null) {
        final String fileName = StreamUtils.replaceFileExtension(entry.getResourceName(), "PVR");
        ResourceFactory.exportResource(entry, decompressed, fileName, panel.getTopLevelAncestor());
      } else {
        JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                      "Error while exporting " + entry, "Error",
                                      JOptionPane.ERROR_MESSAGE);
      }
    } else if (event.getSource() == miPNG) {
      try {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        final String fileName = StreamUtils.replaceFileExtension(entry.getResourceName(), "PNG");
        BufferedImage image = getImage();
        if (ImageIO.write(image, "png", os)) {
          ResourceFactory.exportResource(entry, StreamUtils.getByteBuffer(os.toByteArray()),
                                         fileName, panel.getTopLevelAncestor());
        } else {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                        "Error while exporting " + entry, "Error",
                                        JOptionPane.ERROR_MESSAGE);
        }
        os.close();
        os = null;
        image = null;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

//--------------------- End Interface ActionListener ---------------------

//--------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry()
  {
    return entry;
  }

//--------------------- End Interface Resource ---------------------

//--------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception
  {
    panel.removeAll();
    rcImage.setImage(null);
    rcImage = null;
  }

//--------------------- End Interface Closeable ---------------------

//--------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    JButton btn = ((JButton)buttonPanel.addControl(ButtonPanel.Control.FIND_REFERENCES));
    btn.addActionListener(this);
    btn.setEnabled(Profile.isEnhancedEdition());

    miExport = new JMenuItem("original");
    miExport.addActionListener(this);
    miPNG = new JMenuItem("as PNG");
    miPNG.addActionListener(this);
    miPVR = new JMenuItem("as PVR (uncompressed)");
    miPVR.addActionListener(this);
    ButtonPopupMenu bpmExport = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.EXPORT_MENU);
    bpmExport.setMenuItems(new JMenuItem[]{miExport, miPVR, miPNG});

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

    panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(scroll, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);
    scroll.setBorder(BorderFactory.createLoweredBevelBorder());

    return panel;
  }

//--------------------- End Interface Viewable ---------------------

  public BufferedImage getImage()
  {
    if (rcImage != null) {
      return ColorConvert.toBufferedImage(rcImage.getImage(), false);
    } else if (entry != null) {
      return loadImage();
    }
    return null;
  }

  private void showProperties()
  {
    PvrDecoder decoder = null;
    try {
      decoder = PvrDecoder.loadPvr(entry);
      String resName = entry.getResourceName().toUpperCase(Locale.ENGLISH);
      int width = decoder.getWidth();
      int height = decoder.getHeight();
      String br = "<br />";

      String type;
      type = decoder.getPixelFormat().toString();
      StringBuilder sb = new StringBuilder("<html><div style='font-family:monospace'>");
      sb.append("Type:&nbsp;&nbsp;&nbsp;").append(type).append(br);
      sb.append("Width:&nbsp;&nbsp;").append(width).append(br);
      sb.append("Height:&nbsp;").append(height).append(br);
      sb.append("</code></html>");
      JOptionPane.showMessageDialog(panel, sb.toString(), "Properties of " + resName,
	                                      JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      decoder = null;
    }
  }

  private BufferedImage loadImage()
  {
    BufferedImage image = null;
    PvrDecoder decoder = null;
    if (entry != null) {
      try {
        decoder = PvrDecoder.loadPvr(entry);
        image = new BufferedImage(decoder.getWidth(), decoder.getHeight(), BufferedImage.TYPE_INT_ARGB);
        if (!decoder.decode(image)) {
          image = null;
        }
        decoder = null;
      } catch (Exception e) {
        image = null;
        if (decoder != null) {
          decoder = null;
        }
        e.printStackTrace();
      }
    }
    return image;
  }
}
