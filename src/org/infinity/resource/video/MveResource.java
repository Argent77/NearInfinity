// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.video;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;

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
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;

import org.infinity.NearInfinity;
import org.infinity.exceptions.UnsupportedFormatException;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPanel.Control;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Referenceable;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.ReferenceSearcher;
import org.infinity.util.Logger;
import org.infinity.util.io.FileEx;
import org.monte.media.AudioFormatKeys;
import org.monte.media.Format;
import org.monte.media.FormatKeys;
import org.monte.media.VideoFormatKeys;
import org.monte.media.avi.AVIWriter;
import org.monte.media.math.Rational;

/**
 * This resource describes the movies played during the game. Movies can only be played by the engine when they are
 * stored in a {@link BIFFResourceEntry BIFF} file.
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/mve.htm">
 *      https://gibberlings3.github.io/iesdp/file_formats/ie_formats/mve.htm</a>
 */
public class MveResource implements Resource, ActionListener, ItemListener, Closeable, Referenceable, Runnable {
  private static final int VIDEO_BUFFERS = 3;

  private static final ButtonPanel.Control CTRL_PLAY   = ButtonPanel.Control.CUSTOM_1;
  private static final ButtonPanel.Control CTRL_PAUSE  = ButtonPanel.Control.CUSTOM_2;
  private static final ButtonPanel.Control CTRL_STOP   = ButtonPanel.Control.CUSTOM_3;

  private static boolean isZoom = true;
  private static boolean isFilter = true;

  private final ButtonPanel buttonPanel = new ButtonPanel();
  private final ResourceEntry entry;
  private final MvePlayer player;

  private MveDecoder decoder;
  private ImageRenderer renderer;
  private JMenuItem miExport;
  private JMenuItem miExportAvi;
  private JPanel panel;
  private JCheckBox cbZoom;
  private JCheckBox cbFilter;

  public MveResource(ResourceEntry entry) throws Exception {
    this.entry = entry;
    player = new MvePlayer();
    try {
      decoder = new MveDecoder(entry);
      if (!decoder.isOpen()) {
        decoder.close();
        throw new Exception("");
      }
    } catch (UnsupportedFormatException e) {
      decoder = null;
      Logger.warn("{}: {}", entry, e.getMessage());
    } catch (Exception e) {
      decoder = null;
      Logger.error(e);
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error opening " + entry, "Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES)) {
      searchReferences(panel.getTopLevelAncestor());
    } else if (miExport == event.getSource()) {
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    } else if (miExportAvi == event.getSource()) {
      new Thread(() -> exportAsAvi(entry, (Window) panel.getTopLevelAncestor())).start();
    } else if (buttonPanel.getControlByType(CTRL_PLAY) == event.getSource()) {
      if (player.isStopped()) {
        new Thread(this).start();
      } else {
        if (player.isPaused()) {
          player.continuePlay();
          buttonPanel.getControlByType(CTRL_PLAY).setEnabled(player.isPaused());
          buttonPanel.getControlByType(CTRL_PAUSE).setEnabled(!player.isPaused());
        }
      }
    } else if (buttonPanel.getControlByType(CTRL_PAUSE) == event.getSource()) {
      if (!player.isStopped()) {
        if (!player.isPaused()) {
          player.pausePlay();
          buttonPanel.getControlByType(CTRL_PLAY).setEnabled(player.isPaused());
          buttonPanel.getControlByType(CTRL_PAUSE).setEnabled(!player.isPaused());
        }
      }
    } else if (buttonPanel.getControlByType(CTRL_STOP) == event.getSource()) {
      player.stopPlay();
      buttonPanel.getControlByType(CTRL_STOP).setEnabled(false);
      buttonPanel.getControlByType(CTRL_PAUSE).setEnabled(false);
      buttonPanel.getControlByType(CTRL_PLAY).setEnabled(true);
    } else if (buttonPanel.getControlByType(Control.EXPORT_BUTTON) == event.getSource()) {
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent event) {
    if (event.getSource() == cbZoom) {
      if (renderer != null) {
        isZoom = cbZoom.isSelected();
        renderer.setScalingEnabled(isZoom);
      }
    } else if (event.getSource() == cbFilter) {
      if (renderer != null) {
        isFilter = cbFilter.isSelected();
        Object filter = isFilter ? ImageRenderer.TYPE_BILINEAR : ImageRenderer.TYPE_NEAREST_NEIGHBOR;
        renderer.setInterpolationType(filter);
      }
    }
  }

  // --------------------- End Interface ItemListener ---------------------

  // --------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry() {
    return entry;
  }

  // --------------------- End Interface Resource ---------------------

  // --------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception {
    if (player != null) {
      player.stopPlay();
    }
    if (decoder != null) {
      decoder.close();
      decoder = null;
    }
  }

  // --------------------- End Interface Closeable ---------------------

  // --------------------- Begin Interface Referenceable ---------------------

  @Override
  public boolean isReferenceable() {
    return true;
  }

  @Override
  public void searchReferences(Component parent) {
    new ReferenceSearcher(entry, parent);
  }

  // --------------------- End Interface Referenceable ---------------------

  // --------------------- Begin Interface Runnable ---------------------

  @Override
  public void run() {
    if (!decoder.isOpen()) {
      try {
        decoder.open(entry);
      } catch (Exception e) {
        Logger.error(e);
        JOptionPane.showMessageDialog(panel, "Error starting video playback", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
    }

    buttonPanel.getControlByType(CTRL_PLAY).setEnabled(false);
    buttonPanel.getControlByType(CTRL_PAUSE).setEnabled(true);
    buttonPanel.getControlByType(CTRL_STOP).setEnabled(true);
    try {
      renderer.clearBuffers();
      player.play(renderer, decoder);
    } catch (Exception e) {
      player.stopPlay();
      Logger.error(e);
      JOptionPane.showMessageDialog(panel, "Error during playback", "Error", JOptionPane.ERROR_MESSAGE);
    }
    decoder.close();
    buttonPanel.getControlByType(CTRL_PLAY).setEnabled(true);
    buttonPanel.getControlByType(CTRL_PAUSE).setEnabled(false);
    buttonPanel.getControlByType(CTRL_STOP).setEnabled(false);
  }

  // --------------------- End Interface Runnable ---------------------

  // --------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container) {
    if (decoder != null) {
      renderer = new ImageRenderer(VIDEO_BUFFERS, decoder.getVideoWidth(), decoder.getVideoHeight());
      renderer.setHorizontalAlignment(SwingConstants.CENTER);
      renderer.setVerticalAlignment(SwingConstants.CENTER);
      if (isFilter) {
        renderer.setInterpolationType(ImageRenderer.TYPE_BILINEAR);
      } else {
        renderer.setInterpolationType(ImageRenderer.TYPE_NEAREST_NEIGHBOR);
      }
      renderer.setAspectRatioEnabled(true);
      renderer.setScalingEnabled(isZoom);
      decoder.setVideoOutput(renderer);
    } else {
      renderer = new ImageRenderer();
    }
    JScrollPane scroll = new JScrollPane(renderer);
    scroll.setPreferredSize(new Dimension(renderer.getBufferWidth(), renderer.getBufferHeight()));
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.getHorizontalScrollBar().setUnitIncrement(16);
    scroll.setBorder(BorderFactory.createLoweredBevelBorder());

    cbZoom = new JCheckBox("Zoom video", isZoom);
    cbZoom.addItemListener(this);
    cbFilter = new JCheckBox("Enable video filtering", isFilter);
    cbFilter.addItemListener(this);
    cbFilter.setToolTipText("Uncheck for better video performance");
    JPanel optionsPanel = new JPanel();
    BoxLayout bl = new BoxLayout(optionsPanel, BoxLayout.Y_AXIS);
    optionsPanel.setLayout(bl);
    optionsPanel.add(cbZoom);
    optionsPanel.add(cbFilter);

    JButton bPlay = new JButton("Play", Icons.ICON_PLAY_16.getIcon());
    bPlay.addActionListener(this);
    bPlay.setEnabled(decoder != null);
    JButton bPause = new JButton("Pause", Icons.ICON_PAUSE_16.getIcon());
    bPause.addActionListener(this);
    bPause.setEnabled(false);
    JButton bStop = new JButton("Stop", Icons.ICON_STOP_16.getIcon());
    bStop.addActionListener(this);
    bStop.setEnabled(false);

    final JComponent viewControl;
    final ButtonPanel.Control exportType;
    final JComponent exportControl;
    if (decoder != null) {
      miExport = new JMenuItem("as MVE");
      miExport.addActionListener(this);
      miExportAvi = new JMenuItem("as AVI");
      miExportAvi.addActionListener(this);
      exportType = ButtonPanel.Control.EXPORT_MENU;
      ButtonPopupMenu bpmExport = (ButtonPopupMenu) ButtonPanel.createControl(exportType);
      bpmExport.setMenuItems(new JMenuItem[] { miExport, miExportAvi });
      exportControl = bpmExport;
      viewControl = scroll;
    } else {
      exportType = ButtonPanel.Control.EXPORT_BUTTON;
      JButton bExport = (JButton) ButtonPanel.createControl(exportType);
      bExport.addActionListener(this);
      exportControl = bExport;
      viewControl = new JLabel("Invalid or unsupported video format.", Icons.ICON_WARNING_16.getIcon(),
          SwingConstants.CENTER);
    }

    buttonPanel.addControl(bPlay, CTRL_PLAY);
    buttonPanel.addControl(bPause, CTRL_PAUSE);
    buttonPanel.addControl(bStop, CTRL_STOP);
    ((JButton) buttonPanel.addControl(ButtonPanel.Control.FIND_REFERENCES)).addActionListener(this);
    buttonPanel.addControl(exportControl, exportType);
    buttonPanel.addControl(optionsPanel);

    panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(viewControl, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);

    buttonPanel.addControl(0, ViewerUtil.createViewerSyncButton(panel, getResourceEntry()), ButtonPanel.Control.SYNC_VIEW);

    return panel;
  }

  // --------------------- End Interface Viewable ---------------------

  private static void exportAsAvi(ResourceEntry inEntry, Window parent) {
    if (inEntry != null) {
      JFileChooser fc = new JFileChooser(Profile.getGameRoot().toFile());
      fc.setDialogTitle("Export MVE as AVI");
      String name = inEntry.getResourceRef() + ".avi";
      fc.setSelectedFile(new File(fc.getCurrentDirectory(), name));
      fc.setDialogType(JFileChooser.SAVE_DIALOG);
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
        boolean cancelled = false;
        if (fc.getSelectedFile().isFile()) {
          int ret = ResourceFactory.confirmOverwrite(fc.getSelectedFile().toPath(), true, parent, "Export MVE to AVI");
          cancelled = (ret != JOptionPane.YES_OPTION);
        }
        if (!cancelled) {
          try {
            WindowBlocker.blockWindow(parent, true);
            convertAvi(inEntry, fc.getSelectedFile().toPath(), parent, false);
          } finally {
            WindowBlocker.blockWindow(parent, false);
          }
        }
      }
      fc = null;
    }
  }

  public static boolean convertAvi(ResourceEntry inEntry, Path outFile, Window parent, boolean silent) {
    if (inEntry == null || outFile == null) {
      if (!silent) {
        JOptionPane.showMessageDialog(parent, "No input or output file specified.", "Error", JOptionPane.ERROR_MESSAGE);
      }
      return false;
    }
    Format videoFormat = new Format(FormatKeys.EncodingKey, VideoFormatKeys.ENCODING_AVI_MJPG, VideoFormatKeys.DepthKey,
        24, VideoFormatKeys.QualityKey, 1.0f);
    try {
      MveDecoder decoder = null;
      ProgressMonitor pm = null;
      AVIWriter writer = null;
      try {
        if (!silent) {
          pm = new ProgressMonitor(parent, "Converting MVE to AVI...", "Initializing", 0, 2);
          pm.setMillisToDecideToPopup(0);
          pm.setMillisToPopup(0);
        }

        decoder = new MveDecoder(inEntry);
        decoder.setDefaultAudioOutput(new AudioQueue());

        // prebuffering audio and searching for first video frame
        LinkedList<byte[]> audioQueue = new LinkedList<>();
        while (decoder.hasNextFrame()) {
          decoder.processNextFrame();
          if (!decoder.frameHasVideo()) {
            if (decoder.frameHasAudio()) {
              byte[] buffer = decoder.getAudioOutput(0).getNextData();
              if (buffer != null) {
                audioQueue.add(buffer);
              }
            }
          } else {
            break;
          }
        }

        writer = new AVIWriter(outFile.toFile());

        // initializing video track
        int rate = 1000000;
        int scale = decoder.getFrameDelay();
        if (scale == 0) {
          scale = 66728;
        } // assuming default frame rate
        final int[] prim = { 29, 23, 19, 17, 13, 11, 7, 5, 3, 2 };
        boolean divisible;
        do {
          divisible = false;
          for (int element : prim) {
            if (rate % element == 0 && scale % element == 0) {
              divisible = true;
              rate /= element;
              scale /= element;
            }
          }
        } while (divisible);

        int width = decoder.getVideoWidth();
        int height = decoder.getVideoHeight();
        decoder.setVideoOutput(new BasicVideoBuffer(1, width, height, false));
        videoFormat = videoFormat.prepend(FormatKeys.MediaTypeKey, FormatKeys.MediaType.VIDEO, FormatKeys.FrameRateKey,
            new Rational(rate, scale), VideoFormatKeys.WidthKey, width, VideoFormatKeys.HeightKey, height);
        int trackVideo = writer.addTrack(videoFormat);

        // initializing audio track
        Format audioFormat;
        int channels = decoder.getAudioFormat().getChannels();
        int sampleRate = (int) decoder.getAudioFormat().getSampleRate();
        int sampleBits = decoder.getAudioFormat().getSampleSizeInBits();
        int frameSize = decoder.getAudioFormat().getFrameSize();
        audioFormat = new Format(FormatKeys.EncodingKey, AudioFormatKeys.ENCODING_PCM_SIGNED,
            AudioFormatKeys.ByteOrderKey, ByteOrder.LITTLE_ENDIAN, AudioFormatKeys.ChannelsKey, channels,
            AudioFormatKeys.SampleRateKey, new Rational(sampleRate), AudioFormatKeys.SampleSizeInBitsKey, sampleBits,
            AudioFormatKeys.FrameSizeKey, frameSize, AudioFormatKeys.SignedKey, true);
        int trackAudio = writer.addTrack(audioFormat);

        // default audio buffer for one frame
        int bufferSize = (int) Math.ceil((double) (sampleRate) * (double) scale / rate) * frameSize;
        byte[] defaultBuffer = new byte[bufferSize];

        if (!silent) {
          pm.setProgress(1);
        }
        int frameIdx = 0;

        // writing prebuffered audio data first
        while (!audioQueue.isEmpty()) {
          byte[] buffer = audioQueue.pollFirst();
          writer.writeSample(trackAudio, buffer, 0, buffer.length, true);
        }

        // writing regular frame data
        do {
          if (!silent && frameIdx % 10 == 0) {
            pm.setNote(String.format("Processing frame %d", frameIdx));
          }

          if (decoder.frameHasVideo()) {
            BufferedImage image = (BufferedImage) decoder.getVideoOutput().frontBuffer();
            adjustColorSpace(image);
            writer.write(trackVideo, image, 1);
            image = null;
          }

          byte[] buffer = decoder.getAudioOutput(0).getNextData();
          if (buffer == null) {
            buffer = defaultBuffer;
          }
          writer.writeSample(trackAudio, buffer, 0, buffer.length, true);

          frameIdx++;

          if (!silent && pm.isCanceled()) {
            writer.close();
            writer = null;
            if (FileEx.create(outFile).isFile()) {
              try {
                Files.delete(outFile);
              } catch (IOException e) {
                Logger.error(e);
              }
            }
            JOptionPane.showMessageDialog(parent, "Conversion has been cancelled.", "Information",
                JOptionPane.INFORMATION_MESSAGE);
            return true;
          }
        } while (decoder.processNextFrame());

        if (!silent) {
          pm.setProgress(2);
        }
      } finally {
        if (decoder != null) {
          decoder.close();
          decoder = null;
        }
        if (writer != null) {
          writer.close();
          writer = null;
        }
        if (pm != null) {
          pm.close();
          pm = null;
        }
      }
      if (!silent) {
        JOptionPane.showMessageDialog(parent, "Resource has been converted successfully: " + inEntry, "Information",
            JOptionPane.INFORMATION_MESSAGE);
      }
      return true;
    } catch (Exception e) {
      Logger.error(e);
    }
    if (!silent) {
      JOptionPane.showMessageDialog(parent, "Error while exporting " + inEntry + " as AVI file.", "Error",
          JOptionPane.ERROR_MESSAGE);
    }
    return false;
  }

  /** Reduces color range from [0, 255] to [16, 235] to conform to CCIR-601 standard. */
  private static void adjustColorSpace(BufferedImage image) {
    if (image != null) {
      if (image.getRaster().getDataBuffer().getDataType() == DataBuffer.TYPE_INT) {
        // true color image
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < data.length; i++) {
          int b = data[i] & 0xff;
          b = (16 + ((b * 220) >>> 8)) & 0xff;
          int g = (data[i] >>> 8) & 0xff;
          g = (16 + ((g * 220) >>> 8)) & 0xff;
          int r = (data[i] >>> 16) & 0xff;
          r = (16 + ((r * 220) >>> 8)) & 0xff;
          data[i] = (data[i] & 0xff000000) | (r << 16) | (g << 8) | b;
        }
      }
    }
  }
}
