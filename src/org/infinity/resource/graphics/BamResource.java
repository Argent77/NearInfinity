// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import tv.porst.jhexview.DataChangedEvent;
import tv.porst.jhexview.IDataChangedListener;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.IndexColorModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.NearInfinity;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.WindowBlocker;
import org.infinity.gui.converter.ConvertToBam;
import org.infinity.gui.hexview.GenericHexViewer;
import org.infinity.icon.Icons;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.Writeable;
import org.infinity.resource.chu.ChuResource;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.itm.ItmResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.spl.SplResource;
import org.infinity.search.ReferenceSearcher;
import org.infinity.util.DynamicArray;
import org.infinity.util.IntegerHashMap;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

/**
 * This resource describes animated graphics. Such files are used for animations
 * (both {@link CreResource creature} animations, {@link ItmResource item} and
 * {@link SplResource spell} animations) and {@link ChuResource interactive GUI
 * elements} (e.g. buttons) and for logical collections of images (e.g. fonts).
 * BAM files can contain multiple sequences of animations, up to a limit of 255.
 * <p>
 * While the BAM format allows the dimensions of a frame to be very large, the
 * engine will only show frames up to a certain size. This maximum size varies
 * with the {@link Profile.Engine version of the engine}:
 * <ul>
 * <li>BG1: Unknown</li>
 * <li>BG2: 256*256</li>
 * <li>PST: Unknown (greater than 256*256)</li>
 * <li>IWD1: Unknown</li>
 * <li>IWD2: Unknown</li>
 * <li>BGEE: Unknown (1024*1024 or greater)</li>
 * </ul>
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/bam_v1.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/bam_v1.htm</a>
 */
public class BamResource implements Resource, Closeable, Writeable, ActionListener,
                                    PropertyChangeListener, ChangeListener, IDataChangedListener
{
  private static final Color TransparentColor = new Color(0, true);
  private static final int ANIM_DELAY = 1000 / 15;    // 15 fps in milliseconds

  private static final ButtonPanel.Control CtrlNextCycle  = ButtonPanel.Control.CUSTOM_1;
  private static final ButtonPanel.Control CtrlPrevCycle  = ButtonPanel.Control.CUSTOM_2;
  private static final ButtonPanel.Control CtrlNextFrame  = ButtonPanel.Control.CUSTOM_3;
  private static final ButtonPanel.Control CtrlPrevFrame  = ButtonPanel.Control.CUSTOM_4;
  private static final ButtonPanel.Control CtrlPlay       = ButtonPanel.Control.CUSTOM_5;
  private static final ButtonPanel.Control CtrlCycleLabel = ButtonPanel.Control.CUSTOM_6;
  private static final ButtonPanel.Control CtrlFrameLabel = ButtonPanel.Control.CUSTOM_7;
  private static final ButtonPanel.Control Properties     = ButtonPanel.Control.CUSTOM_8;
  private static final ButtonPanel.Control BamEdit        = ButtonPanel.Control.CUSTOM_9;

  private static boolean transparencyEnabled = true;

  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();
  private final ButtonPanel buttonControlPanel = new ButtonPanel();

  private BamDecoder decoder;
  private BamDecoder.BamControl bamControl;
  private JTabbedPane tabbedPane;
  private GenericHexViewer hexViewer;
  private JMenuItem miExport, miExportBAM, miExportBAMC, miExportFramesPNG;
  private RenderCanvas rcDisplay;
  private JCheckBox cbTransparency;
  private JPanel panelMain, panelRaw;
  private int curCycle, curFrame;
  private Timer timer;
  private RootPaneContainer rpc;
  private SwingWorker<List<byte[]>, Void> workerConvert;
  private boolean exportCompressed;
  private WindowBlocker blocker;

  public BamResource(ResourceEntry entry)
  {
    this.entry = entry;
    WindowBlocker.blockWindow(true);
    try {
      decoder = BamDecoder.loadBam(entry);
      bamControl = decoder.createControl();
      bamControl.setMode(BamDecoder.BamControl.Mode.SHARED);
      if (bamControl instanceof BamV1Decoder.BamV1Control) {
        ((BamV1Decoder.BamV1Control)bamControl).setTransparencyEnabled(transparencyEnabled);
        ((BamV1Decoder.BamV1Control)bamControl).setTransparencyMode(BamV1Decoder.TransparencyMode.NORMAL);
      }
    } catch (Throwable t) {
      t.printStackTrace();
      decoder = null;
    }
    WindowBlocker.blockWindow(false);
  }

//--------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception
  {
    if (isRawModified()) {
      ResourceFactory.closeResource(this, entry, panelMain);
    }
 }

//--------------------- End Interface Closeable ---------------------

//--------------------- Begin Interface Writeable ---------------------

 @Override
 public void write(OutputStream os) throws IOException
 {
   StreamUtils.writeBytes(os, hexViewer.getData());
 }

//--------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (buttonControlPanel.getControlByType(CtrlPrevCycle) == event.getSource()) {
      curCycle--;
      bamControl.setSharedPerCycle(curCycle >= 0);
      bamControl.cycleSet(curCycle);
      updateCanvasSize();
      if (timer != null && timer.isRunning() && bamControl.cycleFrameCount() == 0) {
        timer.stop();
        ((JToggleButton)buttonControlPanel.getControlByType(CtrlPlay)).setSelected(false);
      }
      curFrame = 0;
      showFrame();
    } else if (buttonControlPanel.getControlByType(CtrlNextCycle) == event.getSource()) {
      curCycle++;
      bamControl.setSharedPerCycle(curCycle >= 0);
      bamControl.cycleSet(curCycle);
      updateCanvasSize();
      if (timer != null && timer.isRunning() && bamControl.cycleFrameCount() == 0) {
        timer.stop();
        ((JToggleButton)buttonControlPanel.getControlByType(CtrlPlay)).setSelected(false);
      }
      curFrame = 0;
      showFrame();
    } else if (buttonControlPanel.getControlByType(CtrlPrevFrame) == event.getSource()) {
      curFrame--;
      showFrame();
    } else if (buttonControlPanel.getControlByType(CtrlNextFrame) == event.getSource()) {
      curFrame++;
      showFrame();
    } else if (buttonControlPanel.getControlByType(CtrlPlay) == event.getSource()) {
      if (((JToggleButton)buttonControlPanel.getControlByType(CtrlPlay)).isSelected()) {
        if (timer == null) {
          timer = new Timer(ANIM_DELAY, this);
        }
        timer.restart();
      } else {
        if (timer != null) {
          timer.stop();
        }
      }
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES) == event.getSource()) {
      new ReferenceSearcher(entry, panelMain.getTopLevelAncestor());
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.SAVE) == event.getSource()) {
      if (ResourceFactory.saveResource(this, panelMain.getTopLevelAncestor())) {
        setRawModified(false);
      }
    } else if (event.getSource() == timer) {
      if (curCycle >= 0) {
        curFrame = (curFrame + 1) % bamControl.cycleFrameCount();
      } else {
        curFrame = (curFrame + 1) % decoder.frameCount();
      }
      showFrame();
    } else if (event.getSource() == cbTransparency) {
      setTransparencyEnabled(cbTransparency.isSelected());
    } else if (event.getSource() == miExport) {
      ResourceFactory.exportResource(entry, panelMain.getTopLevelAncestor());
    } else if (event.getSource() == miExportBAM) {
      if (decoder != null) {
        if (decoder.getType() == BamDecoder.Type.BAMV2) {
          // create new BAM V1 from scratch
          if (checkCompatibility(panelMain.getTopLevelAncestor())) {
            blocker = new WindowBlocker(rpc);
            blocker.setBlocked(true);
            startConversion(false);
          }
        } else {
          // decompress existing BAMC V1 and save as BAM V1
          try {
            ByteBuffer buffer = Compressor.decompress(entry.getResourceBuffer());
            ResourceFactory.exportResource(entry, buffer, entry.getResourceName(),
                                           panelMain.getTopLevelAncestor());
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    } else if (event.getSource() == miExportBAMC) {
      if (decoder != null) {
        if (decoder.getType() == BamDecoder.Type.BAMV2) {
          // create new BAMC V1 from scratch
          if (checkCompatibility(panelMain.getTopLevelAncestor())) {
            blocker = new WindowBlocker(rpc);
            blocker.setBlocked(true);
            startConversion(true);
          }
        } else {
          // compress existing BAM V1 and save as BAMC V1
          try {
            ByteBuffer buffer = Compressor.compress(entry.getResourceBuffer(), "BAMC", "V1  ");
            ResourceFactory.exportResource(entry, buffer, entry.getResourceName(), panelMain.getTopLevelAncestor());
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    } else if (event.getSource() == miExportFramesPNG) {
      JFileChooser fc = new JFileChooser(ResourceFactory.getExportFilePath().toFile());
      fc.setDialogTitle("Export BAM frames");
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setSelectedFile(new File(fc.getCurrentDirectory(), entry.getResourceName().replace(".BAM", "")));

      // Output graphics format depends on BAM type
      while (fc.getChoosableFileFilters().length > 0) {
        // removing default filter entries
        fc.removeChoosableFileFilter(fc.getChoosableFileFilters()[0]);
      }
      fc.addChoosableFileFilter(new FileNameExtensionFilter("PNG files (*.png)", "PNG"));
      if (decoder.getType() == BamDecoder.Type.BAMC || decoder.getType() == BamDecoder.Type.BAMV1) {
        fc.addChoosableFileFilter(new FileNameExtensionFilter("BMP files (*.bmp)", "BMP"));
      }
      fc.setFileFilter(fc.getChoosableFileFilters()[0]);

      if (fc.showSaveDialog(panelMain.getTopLevelAncestor()) == JFileChooser.APPROVE_OPTION) {
        Path filePath = fc.getSelectedFile().toPath().getParent();
        String fileName = fc.getSelectedFile().getName();
        String fileExt = null;
        String format = ((FileNameExtensionFilter)fc.getFileFilter()).getExtensions()[0].toLowerCase(Locale.ENGLISH);
        int extIdx = fileName.lastIndexOf('.');
        if (extIdx > 0) {
          fileExt = fileName.substring(extIdx);
          fileName = fileName.substring(0, extIdx);
        } else {
          fileExt = "." + ((FileNameExtensionFilter)fc.getFileFilter()).getExtensions()[0];
        }
        exportFrames(filePath, fileName, fileExt, format);
      }
    } else if (buttonPanel.getControlByType(Properties) == event.getSource()) {
      showProperties();
    } else if (buttonPanel.getControlByType(BamEdit) == event.getSource()) {
      final ConvertToBam converter = ChildFrame.show(ConvertToBam.class, () -> new ConvertToBam());
      converter.framesImportBam(entry);
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
        byte[] bamData = null;
        try {
          List<byte[]> l = workerConvert.get();
          if (l != null && !l.isEmpty()) {
            bamData = l.get(0);
            l.clear();
            l = null;
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        if (bamData != null) {
          if (bamData.length > 0) {
            ResourceFactory.exportResource(entry, StreamUtils.getByteBuffer(bamData),
                                           entry.getResourceName(), panelMain.getTopLevelAncestor());
          } else {
            JOptionPane.showMessageDialog(panelMain.getTopLevelAncestor(),
                                          "Export has been cancelled." + entry, "Information",
                                          JOptionPane.INFORMATION_MESSAGE);
          }
          bamData = null;
        } else {
          JOptionPane.showMessageDialog(panelMain.getTopLevelAncestor(),
                                        "Error while exporting " + entry, "Error",
                                        JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

//--------------------- End Interface PropertyChangeListener ---------------------

//--------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent event)
  {
    if (event.getSource() == tabbedPane) {
      if (tabbedPane.getSelectedComponent() == panelRaw) {
        // lazy initialization of hex viewer
        if (hexViewer == null) {
          // confirm action when opening first time
          int ret = JOptionPane.showConfirmDialog(panelMain,
                                                  "Editing BAM resources directly may result in corrupt data. " +
                                                      "Open hex editor?",
                                                  "Warning", JOptionPane.YES_NO_OPTION,
                                                  JOptionPane.WARNING_MESSAGE);
          if (ret == JOptionPane.YES_OPTION) {
            try {
              WindowBlocker.blockWindow(true);
              hexViewer = new GenericHexViewer(entry);
              hexViewer.addDataChangedListener(this);
              hexViewer.setCurrentOffset(0L);
              panelRaw.add(hexViewer, BorderLayout.CENTER);
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              WindowBlocker.blockWindow(false);
            }
          } else {
            tabbedPane.setSelectedIndex(0);
            return;
          }
        }

        // stop playback
        if (((JToggleButton)buttonControlPanel.getControlByType(CtrlPlay)).isSelected()) {
          if (timer != null) {
            timer.stop();
          }
          ((JToggleButton)buttonControlPanel.getControlByType(CtrlPlay)).setSelected(false);
        }

        hexViewer.requestFocusInWindow();
      }
    }
  }

//--------------------- End Interface ChangeListener ---------------------

//--------------------- Begin Interface IDataChangedListener ---------------------

  @Override
  public void dataChanged(DataChangedEvent event)
  {
    setRawModified(true);
  }

//--------------------- End Interface IDataChangedListener ---------------------

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

    // creating "View" tab
    Dimension dim = (decoder != null) ? bamControl.getSharedDimension() : new Dimension(1, 1);
    rcDisplay = new RenderCanvas(new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB));
    rcDisplay.setHorizontalAlignment(SwingConstants.CENTER);
    rcDisplay.setVerticalAlignment(SwingConstants.CENTER);
    JScrollPane scroll = new JScrollPane(rcDisplay);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.getHorizontalScrollBar().setUnitIncrement(16);

    JButton bFind = (JButton)ButtonPanel.createControl(ButtonPanel.Control.FIND_REFERENCES);
    bFind.addActionListener(this);

    JToggleButton bPlay = new JToggleButton("Play", Icons.getIcon(Icons.ICON_PLAY_16));
    bPlay.addActionListener(this);

    JLabel lCycle = new JLabel("", JLabel.CENTER);
    JButton bPrevCycle = new JButton(Icons.getIcon(Icons.ICON_BACK_16));
    bPrevCycle.setMargin(new Insets(bPrevCycle.getMargin().top, 2, bPrevCycle.getMargin().bottom, 2));
    bPrevCycle.addActionListener(this);
    JButton bNextCycle = new JButton(Icons.getIcon(Icons.ICON_FORWARD_16));
    bNextCycle.setMargin(bPrevCycle.getMargin());
    bNextCycle.addActionListener(this);

    JLabel lFrame = new JLabel("", JLabel.CENTER);
    JButton bPrevFrame = new JButton(Icons.getIcon(Icons.ICON_BACK_16));
    bPrevFrame.setMargin(bPrevCycle.getMargin());
    bPrevFrame.addActionListener(this);
    JButton bNextFrame = new JButton(Icons.getIcon(Icons.ICON_FORWARD_16));
    bNextFrame.setMargin(bPrevCycle.getMargin());
    bNextFrame.addActionListener(this);

    cbTransparency = new JCheckBox("Enable transparency", transparencyEnabled);
    if (decoder != null) {
      cbTransparency.setEnabled(decoder.getType() != BamDecoder.Type.BAMV2);
    }
    cbTransparency.setToolTipText("Affects only legacy BAM resources (BAM v1)");
    cbTransparency.addActionListener(this);
    JPanel optionsPanel = new JPanel();
    BoxLayout bl = new BoxLayout(optionsPanel, BoxLayout.Y_AXIS);
    optionsPanel.setLayout(bl);
    optionsPanel.add(cbTransparency);

    buttonControlPanel.addControl(lCycle, CtrlCycleLabel);
    buttonControlPanel.addControl(bPrevCycle, CtrlPrevCycle);
    buttonControlPanel.addControl(bNextCycle, CtrlNextCycle);
    buttonControlPanel.addControl(lFrame, CtrlFrameLabel);
    buttonControlPanel.addControl(bPrevFrame, CtrlPrevFrame);
    buttonControlPanel.addControl(bNextFrame, CtrlNextFrame);
    buttonControlPanel.addControl(bPlay, CtrlPlay);
    buttonControlPanel.add(optionsPanel);
    buttonControlPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

    JPanel pView = new JPanel(new BorderLayout());
    pView.add(scroll, BorderLayout.CENTER);
    pView.add(buttonControlPanel, BorderLayout.SOUTH);


    // creating "Raw" tab (stub)
    panelRaw = new JPanel(new BorderLayout());
    hexViewer = null;

    // creating main panel
    miExport = new JMenuItem("original");
    miExport.addActionListener(this);
    if (decoder != null) {
      if (decoder.getType() == BamDecoder.Type.BAMC) {
        miExportBAM = new JMenuItem("decompressed");
        miExportBAM.addActionListener(this);
      } else if (decoder.getType() == BamDecoder.Type.BAMV1 &&
                 (Boolean)Profile.getProperty(Profile.Key.IS_SUPPORTED_BAMC_V1)) {
        miExportBAMC = new JMenuItem("compressed");
        miExportBAMC.addActionListener(this);
      } else if (decoder.getType() == BamDecoder.Type.BAMV2) {
        miExportBAM = new JMenuItem("as BAM V1 (uncompressed)");
        miExportBAM.addActionListener(this);
        miExportBAM.setEnabled(decoder.frameCount() < 65536 && bamControl.cycleCount() < 256);
        miExportBAMC = new JMenuItem("as BAM V1 (compressed)");
        miExportBAMC.addActionListener(this);
        miExportBAMC.setEnabled(decoder.frameCount() < 65536 && bamControl.cycleCount() < 256);
      }
      miExportFramesPNG = new JMenuItem("all frames as graphics");
      miExportFramesPNG.addActionListener(this);
    }

    List<JMenuItem> list = new ArrayList<JMenuItem>();
    if (miExport != null) {
      list.add(miExport);
    }
    if (miExportBAM != null) {
      list.add(miExportBAM);
    }
    if (miExportBAMC != null) {
      list.add(miExportBAMC);
    }
    if (miExportFramesPNG != null) {
      list.add(miExportFramesPNG);
    }
    JMenuItem[] mi = new JMenuItem[list.size()];
    for (int i = 0; i < mi.length; i++) {
      mi[i] = list.get(i);
    }
    ButtonPopupMenu bpmExport = (ButtonPopupMenu)ButtonPanel.createControl(ButtonPanel.Control.EXPORT_MENU);
    bpmExport.setMenuItems(mi);

    JButton bProperties = new JButton("Properties...", Icons.getIcon(Icons.ICON_EDIT_16));
    bProperties.addActionListener(this);

    JButton bEdit = new JButton("Edit BAM", Icons.getIcon(Icons.ICON_APPLICATION_16));
    bEdit.setToolTipText("Opens resource in BAM Converter.");
    bEdit.addActionListener(this);

    buttonPanel.addControl(bFind, ButtonPanel.Control.FIND_REFERENCES);
    buttonPanel.addControl(bpmExport, ButtonPanel.Control.EXPORT_MENU);
    ((JButton)buttonPanel.addControl(ButtonPanel.Control.SAVE)).addActionListener(this);
    buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(false);
    buttonPanel.addControl(bProperties, Properties);
    buttonPanel.addControl(bEdit, BamEdit);
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

    tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    tabbedPane.setBorder(BorderFactory.createEmptyBorder());
    tabbedPane.addTab("View", pView);
    tabbedPane.addTab("Raw", panelRaw);
    tabbedPane.setSelectedIndex(0);
    tabbedPane.addChangeListener(this);

    panelMain = new JPanel(new BorderLayout());
    panelMain.add(tabbedPane, BorderLayout.CENTER);
    panelMain.add(buttonPanel, BorderLayout.SOUTH);
    showFrame();
    return panelMain;
  }

//--------------------- End Interface Viewable ---------------------

  private boolean viewerInitialized()
  {
    return (panelMain != null && rcDisplay != null);
  }

  public boolean isTransparencyEnabled()
  {
    return transparencyEnabled;
  }

  public void setTransparencyEnabled(boolean enable)
  {
    transparencyEnabled = enable;
    if (bamControl != null && bamControl instanceof BamV1Decoder.BamV1Control) {
      ((BamV1Decoder.BamV1Control)bamControl).setTransparencyEnabled(transparencyEnabled);
      showFrame();
    }
  }

  public int getFrameCount()
  {
    int retVal = 0;
    if (decoder != null ) {
      retVal = decoder.frameCount();
    }
    return retVal;
  }

  public int getFrameCount(int cycleIdx)
  {
    int retVal = 0;
    if (decoder != null) {
      if (cycleIdx >= 0 && cycleIdx < bamControl.cycleCount()) {
        int cycle = bamControl.cycleGet();
        int frame = bamControl.cycleGetFrameIndex();
        bamControl.cycleSet(cycleIdx);
        retVal = bamControl.cycleFrameCount();
        bamControl.cycleSet(cycle);
        bamControl.cycleSetFrameIndex(frame);
      }
    }
    return retVal;
  }

  public int getCycleCount()
  {
    int retVal = 0;
    if (decoder != null) {
      retVal = bamControl.cycleCount();
    }
    return retVal;
  }

  public Image getFrame(int frameIdx)
  {
    Image image = null;
    if (decoder != null) {
      BamDecoder.BamControl.Mode oldMode = bamControl.getMode();
      bamControl.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
      image = decoder.frameGet(bamControl, frameIdx);
      bamControl.setMode(oldMode);
    }
    // must always return a valid image!
    if (image == null) {
      image = ColorConvert.createCompatibleImage(1, 1, true);
    }
    return image;
  }

  public int getFrameIndex(int cycleIdx, int frameIdx)
  {
    if (decoder != null) {
      return bamControl.cycleGetFrameIndexAbsolute(cycleIdx, frameIdx);
    } else {
      return 0;
    }
  }

  public Point getFrameCenter(int frameIdx)
  {
    Point p = new Point();
    if (decoder != null) {
      p.x = decoder.getFrameInfo(frameIdx).getCenterX();
      p.y = decoder.getFrameInfo(frameIdx).getCenterY();
    }
    return p;
  }

  public void updateCanvasSize()
  {
    if (decoder != null && viewerInitialized()) {
      Dimension dim = bamControl.getSharedDimension();
      rcDisplay.setImage(new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB));
      updateCanvas();
    }
  }

  public void updateCanvas()
  {
    if (viewerInitialized()) {
      BufferedImage image = (BufferedImage)rcDisplay.getImage();
      Graphics2D g = image.createGraphics();
      try {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.setColor(TransparentColor);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
      } finally {
        g.dispose();
        g = null;
      }

      // rendering new frame
      if (curCycle >= 0) {
        bamControl.cycleGetFrame(image);
      } else {
        if (decoder instanceof BamV1Decoder && bamControl instanceof BamV1Decoder.BamV1Control) {
          ((BamV1Decoder)decoder).frameGet(bamControl, curFrame, image);
        } else {
          decoder.frameGet(null, curFrame, image);
        }
      }
      rcDisplay.repaint();
    }
  }

  /** Shows message box about basic resource properties. */
  private void showProperties()
  {
    BamDecoder.BamControl control = decoder.createControl();
    String resName = entry.getResourceName().toUpperCase(Locale.ENGLISH);
    int frameCount = decoder.frameCount();
    int cycleCount = control.cycleCount();
    String br = "<br />";
    StringBuilder pageList = new StringBuilder();
    String type;
    switch (decoder.getType()) {
      case BAMV1:
        type = "BAM V1 (uncompressed)";
        break;
      case BAMC:
        type = "BAM V1 (compressed)";
        break;
      case BAMV2:
      {
        type = "BAM V2";
        Set<Integer> pvrzPages = ((BamV2Decoder)decoder).getReferencedPVRZPages();
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
    sb.append("Type:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").append(type).append(br);
    sb.append("# frames:&nbsp;").append(frameCount).append(br);
    sb.append("# cycles:&nbsp;").append(cycleCount).append(br);
    if (decoder.getType() == BamDecoder.Type.BAMV2) {
      sb.append(br).append("Referenced PVRZ pages:").append(br);
      sb.append(pageList.toString()).append(br);
    }
    sb.append("</div></html>");
    JOptionPane.showMessageDialog(panelMain, sb.toString(), "Properties of " + resName,
                                  JOptionPane.INFORMATION_MESSAGE);
  }

  private void showFrame()
  {
    if (viewerInitialized()) {
      if (decoder != null) {

        if (curCycle >= 0) {
          if (!bamControl.cycleSetFrameIndex(curFrame)) {
            bamControl.cycleReset();
            curFrame = 0;
          }
        }

        updateCanvas();

        if (curCycle >= 0) {
          ((JLabel)buttonControlPanel.getControlByType(CtrlCycleLabel))
            .setText("Cycle: " + (curCycle + 1) + "/" + bamControl.cycleCount());
          ((JLabel)buttonControlPanel.getControlByType(CtrlFrameLabel))
            .setText("Frame: " + (curFrame + 1) + "/" + bamControl.cycleFrameCount());
        } else {
          ((JLabel)buttonControlPanel.getControlByType(CtrlCycleLabel)).setText("All frames");
          ((JLabel)buttonControlPanel.getControlByType(CtrlFrameLabel))
            .setText("Frame: " + (curFrame + 1) + "/" + decoder.frameCount());
        }

        buttonControlPanel.getControlByType(CtrlPrevCycle).setEnabled(curCycle > -1);
        buttonControlPanel.getControlByType(CtrlNextCycle).setEnabled(curCycle + 1 < bamControl.cycleCount());
        buttonControlPanel.getControlByType(CtrlPrevFrame).setEnabled(curFrame > 0);
        if (curCycle >= 0) {
          buttonControlPanel.getControlByType(CtrlNextFrame).setEnabled(curFrame + 1 < bamControl.cycleFrameCount());
          buttonControlPanel.getControlByType(CtrlPlay).setEnabled(bamControl.cycleFrameCount() > 1);
        } else {
          buttonControlPanel.getControlByType(CtrlNextFrame).setEnabled(curFrame + 1 < decoder.frameCount());
          buttonControlPanel.getControlByType(CtrlPlay).setEnabled(decoder.frameCount() > 1);
        }

      } else {
        buttonControlPanel.getControlByType(CtrlPlay).setEnabled(false);
        buttonControlPanel.getControlByType(CtrlPrevCycle).setEnabled(false);
        buttonControlPanel.getControlByType(CtrlNextCycle).setEnabled(false);
        buttonControlPanel.getControlByType(CtrlPrevFrame).setEnabled(false);
        buttonControlPanel.getControlByType(CtrlNextFrame).setEnabled(false);
      }
    }
  }

  /**
   * Exports each frame of the BamDecoder data.
   * @param decoder Contains the BAM graphics data to export.
   * @param filePath The target path without filename.
   * @param fileBase The filename without path and extension.
   * @param fileExt The file extension
   * @param format The format (currently supported: BMP and PNG).
   * @param enableTransparency Specifies whether to consider transparent pixels.
   * @return A status message describing the result of the operation (can be null).
   */
  public static String exportFrames(BamDecoder decoder, Path filePath, String fileBase,
                                     String fileExt, String format, boolean enableTransparency)
  {
    if (decoder == null) {
      return null;
    }

    if (filePath == null) {
      filePath = FileManager.resolve("").toAbsolutePath();
    }
    if (format == null || format.isEmpty() ||
        !("png".equalsIgnoreCase(format) || "bmp".equalsIgnoreCase(format))) {
      format = "png";
    }

    int max = 0, counter = 0, failCounter = 0;
    try {
      if (decoder != null) {
        BamDecoder.BamControl control = decoder.createControl();
        control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
        // using selected transparency mode for BAM v1 frames
        if (control instanceof BamV1Decoder.BamV1Control) {
          ((BamV1Decoder.BamV1Control)control).setTransparencyEnabled(enableTransparency);
        }
        max = decoder.frameCount();
        for (int i = 0; i < decoder.frameCount(); i++) {
          String fileIndex = String.format("%05d", i);
          BufferedImage image = null;
          try {
            image = prepareFrameImage(decoder, i);
          } catch (Exception e) {
          }
          if (image != null) {
            decoder.frameGet(control, i, image);
            try {
              Path file = filePath.resolve(fileBase + fileIndex + fileExt);
              ImageIO.write(image, format, file.toFile());
              counter++;
            } catch (IOException e) {
              failCounter++;
              System.err.println("Error writing frame #" + i);
            }
            image.flush();
            image = null;
          } else {
            failCounter++;
            System.err.println("Skipping frame #" + i);
          }
        }
      }
    } catch (Throwable t) {
    }

    // displaying results
    String msg = null;
    if (failCounter == 0 && counter == max) {
      msg = String.format("All %d frames exported successfully.", max);
    } else {
      msg = String.format("%2$d/%1$d frame(s) exported.\n%3$d/%1$d frame(s) skipped.",
                          max, counter, failCounter);
    }
    return msg;
  }

  /** Returns a BufferedImage object in the most appropriate format for the current BAM resource. */
  private static BufferedImage prepareFrameImage(BamDecoder decoder, int frameIdx)
  {
    BufferedImage image = null;

    if (decoder != null && frameIdx >= 0 && frameIdx < decoder.frameCount()) {
      if (decoder instanceof BamV1Decoder) {
        // preparing palette
        BamV1Decoder decoderV1 = (BamV1Decoder)decoder;
        BamV1Decoder.BamV1Control control = decoderV1.createControl();
        int[] palette = control.getPalette();
        int transIndex = control.getTransparencyIndex();
        boolean hasAlpha = control.isAlphaEnabled();
        IndexColorModel cm = new IndexColorModel(8, 256, palette, 0, hasAlpha, transIndex, DataBuffer.TYPE_BYTE);
        image = new BufferedImage(decoder.getFrameInfo(frameIdx).getWidth(),
                                  decoder.getFrameInfo(frameIdx).getHeight(),
                                  BufferedImage.TYPE_BYTE_INDEXED, cm);
      } else {
        image = new BufferedImage(decoder.getFrameInfo(frameIdx).getWidth(),
                                  decoder.getFrameInfo(frameIdx).getHeight(),
                                  BufferedImage.TYPE_INT_ARGB);
      }
    }

    return image;
  }

  /** Exports frames as graphics, specified by "format". */
  private void exportFrames(Path filePath, String fileBase, String fileExt, String format)
  {
    String msg = null;
    WindowBlocker blocker = new WindowBlocker(NearInfinity.getInstance());
    try {
      blocker.setBlocked(true);
      msg = exportFrames(decoder, filePath, fileBase, fileExt, format, cbTransparency.isSelected());
    } finally {
      blocker.setBlocked(false);
      blocker = null;
    }
    if (msg != null) {
      JOptionPane.showMessageDialog(panelMain.getTopLevelAncestor(), msg, "Information",
                                    JOptionPane.INFORMATION_MESSAGE);
    }
  }

  /** Checks current BAM (V2 only) for compatibility and shows an appropriate warning or error message. */
  private boolean checkCompatibility(Component parent)
  {
    if (parent == null)
      parent = NearInfinity.getInstance();

    if (decoder != null && decoder.getType() == BamDecoder.Type.BAMV2) {
      // Compatibility: 0=OK, 1=issues, but conversion is possible, 2=conversion is not possible
      int compatibility = 0;
      int numFrames = decoder.frameCount();
      int numCycles = bamControl.cycleCount();
      boolean hasSemiTrans = false;
      int maxWidth = 0, maxHeight = 0;
      List<String> issues = new ArrayList<String>(10);

      // checking for issues
      BamDecoder.BamControl control = decoder.createControl();
      control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
      for (int i = 0; i < numFrames; i++) {
        BufferedImage img = ColorConvert.toBufferedImage(decoder.frameGet(control, i), true);
        if (img != null) {
          maxWidth = Math.max(maxWidth, img.getWidth());
          maxHeight = Math.max(maxHeight, img.getHeight());
          if (hasSemiTrans == false) {
            int[] data = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
            for (int j = 0; j < data.length; j++) {
              int a = (data[j] >>> 24) & 0xff;
              if (a >= 0x20 && a <= 0xA0) {
                hasSemiTrans = true;
                break;
              }
            }
          }
        }
      }

      // number of frames > 65535
      if (numFrames > 65535) {
        issues.add("- [critical] Number of frames exceeds the supported maximum of 65535 frames.");
        compatibility = Math.max(compatibility, 2);
      }

      // number of cycles > 255
      if (numCycles > 255) {
        issues.add("- [severe] Number of cycles exceeds the supported maximum of 255 cycles. Excess cycles will be cut off.");
        compatibility = Math.max(compatibility, 1);
      }

      // frame dimensions > 256 pixels
      if (!Profile.isEnhancedEdition() && (maxWidth > 256 || maxHeight > 256)) {
        issues.add("- [severe] One or more frames are greater than 256x256 pixels. Those frames may not be visible in certain games.");
        compatibility = Math.max(compatibility, 1);
      }

      // semi-transparent pixels
      if (hasSemiTrans && !(Boolean)Profile.getProperty(Profile.Key.IS_SUPPORTED_BAM_V1_ALPHA)) {
        issues.add("- [medium] One or more frames contain semi-transparent pixels. The transparency information will be lost.");
        compatibility = Math.max(compatibility, 1);
      }

      if (compatibility == 0) {
        return true;
      } else if (compatibility == 1) {
        StringBuilder sb = new StringBuilder();
        sb.append("The BAM resource is not fully compatible with the legacy BAM V1 format:\n");
        for (final String s: issues) {
          sb.append(s);
          sb.append("\n");
        }
        sb.append("\n");
        sb.append("Do you still want to continue?");
        int retVal = JOptionPane.showConfirmDialog(parent, sb.toString(), "Warning",
                                                   JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return (retVal == JOptionPane.YES_OPTION);
      } else {
        StringBuilder sb = new StringBuilder();
        sb.append("The BAM resource is not compatible with the legacy BAM V1 format because of the following issues:\n");
        for (final String s: issues) {
          sb.append(s);
          sb.append("\n");
        }
        JOptionPane.showMessageDialog(parent, sb.toString(), "Error", JOptionPane.ERROR_MESSAGE);
        return false;
      }
    }
    return false;
  }

  /** Creates a new BAM V1 or BAMC V1 resource from scratch. DO NOT call directly!. */
  private byte[] convertToBamV1(boolean compressed) throws Exception
  {
    if (decoder != null) {
      boolean ignoreAlpha = !(Boolean)Profile.getProperty(Profile.Key.IS_SUPPORTED_BAM_V1_ALPHA);
      BamDecoder.BamControl control = decoder.createControl();
      control.setMode(BamDecoder.BamControl.Mode.INDIVIDUAL);
      // max. supported number of frames and cycles
      int frameCount = Math.min(decoder.frameCount(), 65535);
      int cycleCount = Math.min(control.cycleCount(), 255);

      // 1. calculating global palette for all frames
      final int transThreshold = ignoreAlpha ? 32 : 1;
      boolean[] frameTransparency = new boolean[frameCount];
      boolean hasTransparency = false;
      int totalWidth = 0, totalHeight = 0;
      for (int i = 0; i < frameCount; i++) {
        BufferedImage img = ColorConvert.toBufferedImage(decoder.frameGet(control, i), true);
        // getting max. dimensions
        if (img != null) {
          if (img.getHeight() > totalHeight)
            totalHeight = img.getHeight();
          totalWidth += img.getWidth();
          // frame uses transparent pixels?
          int[] data = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
          frameTransparency[i] = false;
          for (int j = 0; j < data.length; j++) {
            if (((data[j] >>> 24) & 0xff) < transThreshold) {
              frameTransparency[i] = true;
              hasTransparency = true;
              break;
            }
          }
        }
      }
      // creating global palette for all available frames
      BufferedImage composedImage = ColorConvert.createCompatibleImage(totalWidth, totalHeight, true);
      Graphics2D g = composedImage.createGraphics();
      for (int i = 0, w = 0; i < frameCount; i++) {
        BufferedImage img = ColorConvert.toBufferedImage(decoder.frameGet(control, i), true);
        if (img != null) {
          g.drawImage(img, w, 0, null);
          w += img.getWidth();
        }
      }
      g.dispose();
      int[] chainedImageData = ((DataBufferInt)composedImage.getRaster().getDataBuffer()).getData();
      int[] palette = ColorConvert.medianCut(chainedImageData, hasTransparency ? 255 : 256, ignoreAlpha);
      // initializing color cache
      IntegerHashMap<Byte> colorCache = new IntegerHashMap<Byte>(1536);
      for (int i = 0; i < palette.length; i++) {
        colorCache.put(palette[i], (byte)i);
      }
      // adding transparent color index to the palette if available
      if (hasTransparency) {
        int[] tmp = palette;
        palette = new int[tmp.length + 1];
        palette[0] = 0x00ff00;    // it's usually defined as RGB(0, 255, 0)
        System.arraycopy(tmp, 0, palette, 1, tmp.length);
        tmp = null;
      }

      // 2. encoding frames
      List<byte[]> frameList = new ArrayList<byte[]>(frameCount);
      int colorShift = hasTransparency ? 1 : 0;   // considers transparent color index
      for (int i = 0; i < frameCount; i++) {
        if (decoder.frameGet(control, i) != null) {
          BufferedImage img = ColorConvert.toBufferedImage(decoder.frameGet(control, i), true);
          int[] srcData = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
          if (frameTransparency[i] == true) {
            // do RLE encoding (on transparent pixels only)
            byte[] dstData = new byte[img.getWidth()*img.getHeight() + (img.getWidth()*img.getHeight() + 1)/2];
            int srcIdx = 0, dstIdx = 0, srcMax = img.getWidth()*img.getHeight();
            while (srcIdx < srcMax) {
              if (((srcData[srcIdx] >>> 24) & 0xff) < transThreshold) {
                // transparent pixel
                int cnt = 0;
                srcIdx++;
                while (srcIdx < srcMax && cnt < 255 && ((srcData[srcIdx] >>> 24) & 0xff) < transThreshold) {
                  cnt++;
                  srcIdx++;
                }
                dstData[dstIdx++] = 0;
                dstData[dstIdx++] = (byte)cnt;
              } else {
                // visible pixel
                Byte colIdx = colorCache.get(srcData[srcIdx]);
                if (colIdx != null) {
                  dstData[dstIdx++] = (byte)(colIdx + colorShift);
                } else {
                  int color = ColorConvert.nearestColorRGB(srcData[srcIdx], palette, ignoreAlpha);
                  dstData[dstIdx++] = (byte)(color);
                  if (color > 0) {
                    colorCache.put(srcData[srcIdx], (byte)(color - colorShift));
                  }
                }
                srcIdx++;
              }
            }
            // truncating byte array
            byte[] tmp = dstData;
            dstData = new byte[dstIdx];
            System.arraycopy(tmp, 0, dstData, 0, dstData.length);
            tmp = null;
            frameList.add(dstData);
          } else {
            // storing uncompressed pixel data
            byte[] dstData = new byte[img.getWidth()*img.getHeight()];
            int idx = 0, max = dstData.length;
            while (idx < max) {
              Byte colIdx = colorCache.get(srcData[idx]);
              if (colIdx != null) {
                dstData[idx] = (byte)(colIdx + colorShift);
              } else {
                int color = ColorConvert.nearestColorRGB(srcData[idx], palette, ignoreAlpha);
                dstData[idx] = (byte)(color);
                if (color > 0) {
                  colorCache.put(srcData[idx], (byte)(color - colorShift));
                }
              }
              idx++;
            }
            frameList.add(dstData);
          }
        } else {
          frameList.add(new byte[]{});
        }
      }

      // 3. creating header structures
      final int frameEntrySize = 0x0c;
      final int cycleEntrySize = 0x04;
      final int paletteSize = 0x400;
      final int lookupTableEntrySize = 0x02;
      // main header
      byte[] header = new byte[0x18];
      DynamicArray buf = DynamicArray.wrap(header, DynamicArray.ElementType.BYTE);
      buf.put(0x00, "BAM V1  ".getBytes());
      buf.putShort(0x08, (short)frameCount);
      buf.putByte(0x0a, (byte)cycleCount);
      buf.putByte(0x0b, (byte)0);   // compressed color index
      int frameEntryOfs = 0x18;
      int paletteOfs = frameEntryOfs + frameCount*frameEntrySize + cycleCount*cycleEntrySize;
      int lookupTableOfs = paletteOfs + paletteSize;
      buf.putInt(0x0c, frameEntryOfs);
      buf.putInt(0x10, paletteOfs);
      buf.putInt(0x14, lookupTableOfs);
      // cycle entries
      byte[] cycleEntryHeader = new byte[cycleCount*cycleEntrySize];
      buf = DynamicArray.wrap(cycleEntryHeader, DynamicArray.ElementType.BYTE);
      for (int i = 0; i < cycleCount; i++) {
        control.cycleSet(i);
        buf.putShort(0x00, (short)control.cycleFrameCount());
        buf.putShort(0x02, (short)control.cycleGetFrameIndexAbsolute(0));
        buf.addToBaseOffset(cycleEntrySize);
      }
      // frame entries and frame lookup table
      byte[] frameEntryHeader = new byte[frameCount*frameEntrySize];
      buf = DynamicArray.wrap(frameEntryHeader, DynamicArray.ElementType.BYTE);
      byte[] lookupTableHeader = new byte[frameCount*lookupTableEntrySize];
      DynamicArray buf2 = DynamicArray.wrap(lookupTableHeader, DynamicArray.ElementType.BYTE);
      int dataOfs = lookupTableOfs + frameCount*lookupTableEntrySize;
      for (int i = 0; i < frameCount; i++) {
        buf.putShort(0x00, (short)decoder.getFrameInfo(i).getWidth());
        buf.putShort(0x02, (short)decoder.getFrameInfo(i).getHeight());
        buf.putShort(0x04, (short)decoder.getFrameInfo(i).getCenterX());
        buf.putShort(0x06, (short)decoder.getFrameInfo(i).getCenterY());
        int ofs = dataOfs & 0x7fffffff;
        if (frameTransparency[i] == false)
          ofs |= 0x80000000;
        buf.putInt(0x08, ofs);
        dataOfs += frameList.get(i).length;
        buf.addToBaseOffset(frameEntrySize);

        buf2.putShort(0x00, (short)i);
        buf2.addToBaseOffset(lookupTableEntrySize);
      }

      // 4. putting it all together
      int bamSize = dataOfs;    // dataOfs should now point to the end of the data
      int bamOfs = 0;
      byte[] bamArray = new byte[bamSize];
      System.arraycopy(header, 0, bamArray, bamOfs, header.length);
      bamOfs += header.length;
      System.arraycopy(frameEntryHeader, 0, bamArray, bamOfs, frameEntryHeader.length);
      bamOfs += frameEntryHeader.length;
      System.arraycopy(cycleEntryHeader, 0, bamArray, bamOfs, cycleEntryHeader.length);
      bamOfs += cycleEntryHeader.length;
      for (int i = 0; i < palette.length; i++) {
        System.arraycopy(DynamicArray.convertInt(palette[i]), 0, bamArray, bamOfs, 4);
        bamOfs += 4;
      }
      System.arraycopy(lookupTableHeader, 0, bamArray, bamOfs, lookupTableHeader.length);
      bamOfs += lookupTableHeader.length;
      for (int i = 0; i < frameList.size(); i++) {
        byte[] frame = frameList.get(i);
        if (frame != null) {
          System.arraycopy(frame, 0, bamArray, bamOfs, frame.length);
          bamOfs += frame.length;
        }
      }
      frameList.clear(); frameList = null;
      colorCache.clear(); colorCache = null;
      palette = null;

      // optionally compressing to MOSC V1
      if (compressed) {
        if (bamArray != null) {
          bamArray = Compressor.compress(bamArray, "BAMC", "V1  ");
        }
      }

      return bamArray;
    }
    return null;
  }

  /** Starts the worker thread for BAM conversion. */
  private void startConversion(boolean compressed)
  {
    exportCompressed = compressed;
    workerConvert = new SwingWorker<List<byte[]>, Void>() {
      @Override
      public List<byte[]> doInBackground()
      {
        List<byte[]> list = new Vector<byte[]>(1);
        try {
          byte[] buf = convertToBamV1(exportCompressed);
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

  /** Returns whether the specified PVRZ index can be found in the current BAM resource. */
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
            byte[] buf = new byte[24];
            long len;
            long curOfs = 0;
            if ((len = is.read(sig)) != sig.length) throw new Exception();
            if (!"BAM V2  ".equals(DynamicArray.getString(sig, 0, 8))) throw new Exception();
            curOfs += len;
            if ((len = is.read(buf)) != buf.length) throw new Exception();
            curOfs += len;
            int numBlocks = DynamicArray.getInt(buf, 8);
            int ofsBlocks = DynamicArray.getInt(buf, 20);
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

  private boolean isRawModified()
  {
    if (hexViewer != null) {
      return hexViewer.isModified();
    }
    return false;
  }

  private void setRawModified(boolean modified)
  {
    if (hexViewer != null) {
      if (!modified) {
        hexViewer.clearModified();
      }
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(modified);
    }
  }
}
