// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.sound;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.nio.ByteBuffer;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.infinity.NearInfinity;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.SoundPanel;
import org.infinity.gui.ViewerUtil;
import org.infinity.icon.Icons;
import org.infinity.resource.Closeable;
import org.infinity.resource.Referenceable;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.WavReferenceSearcher;
import org.infinity.util.Logger;
import org.infinity.util.io.StreamUtils;

/**
 * Handles all kinds of supported single track audio files.
 */
public class SoundResource implements Resource, ActionListener, ItemListener, Closeable, Referenceable {
  private static final ButtonPanel.Control PROPERTIES = ButtonPanel.Control.CUSTOM_1;

  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();
  private final SoundPanel soundPanel = new SoundPanel(SoundPanel.Option.TIME_LABEL, SoundPanel.Option.PROGRESS_BAR,
      SoundPanel.Option.PROGRESS_BAR_LABELS, SoundPanel.Option.LOOP_CHECKBOX, SoundPanel.Option.AUTOPLAY_CHECKBOX);

  private JMenuItem miExport;
  private JMenuItem miConvert;
  private JPanel panel;

  public SoundResource(ResourceEntry entry) throws Exception {
    this.entry = entry;
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES) == event.getSource()) {
      searchReferences(panel.getTopLevelAncestor());
    } else if (buttonPanel.getControlByType(PROPERTIES) == event.getSource()) {
      showProperties();
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent event) {
    if (buttonPanel.getControlByType(ButtonPanel.Control.EXPORT_MENU) == event.getSource()) {
      ButtonPopupMenu bpmExport = (ButtonPopupMenu) event.getSource();
      if (bpmExport.getSelectedItem() == miExport) {
        ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
      } else if (bpmExport.getSelectedItem() == miConvert) {
        final String fileName = StreamUtils.replaceFileExtension(entry.getResourceName(), "WAV");
        ByteBuffer buffer = StreamUtils.getByteBuffer(soundPanel.getAudioBuffer().getAudioData());
        ResourceFactory.exportResource(entry, buffer, fileName, panel.getTopLevelAncestor());
      }
    }
  }

  // --------------------- End Interface ItemListener ---------------------

  // --------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception {
    soundPanel.close();
  }

  // --------------------- End Interface Closeable ---------------------

  // --------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry() {
    return entry;
  }

  // --------------------- End Interface Resource ---------------------

  // --------------------- Begin Interface Referenceable ---------------------

  @Override
  public boolean isReferenceable() {
    return entry.getExtension().equalsIgnoreCase("WAV");
  }

  @Override
  public void searchReferences(Component parent) {
    new WavReferenceSearcher(entry, parent);
  }

  // --------------------- End Interface Referenceable ---------------------

  // --------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container) {
    if (isReferenceable()) {
      // only available for WAV resource types
      ((JButton) buttonPanel.addControl(ButtonPanel.Control.FIND_REFERENCES)).addActionListener(this);
    }

    soundPanel.setDisplayFormat(SoundPanel.DisplayFormat.ELAPSED_TOTAL_PRECISE);
    miExport = new JMenuItem("original");
    miConvert = new JMenuItem("as WAV");
    miConvert.setEnabled(!soundPanel.isWavFile());
    ButtonPopupMenu bpmExport = (ButtonPopupMenu) buttonPanel.addControl(ButtonPanel.Control.EXPORT_MENU);
    bpmExport.setMenuItems(new JMenuItem[] { miExport, miConvert });
    bpmExport.addItemListener(this);

    JButton bProperties = new JButton("Properties...", Icons.ICON_EDIT_16.getIcon());
    bProperties.setEnabled(false);
    bProperties.addActionListener(this);
    buttonPanel.addControl(bProperties, PROPERTIES);

    // wrapper panel prevents the sound panel from auto-scaling
    final JPanel soundPanelWrapper = new JPanel(new GridBagLayout());
    final GridBagConstraints gbc = new GridBagConstraints();
    ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
        new Insets(16, 16, 16, 16), 0, 0);
    soundPanelWrapper.add(soundPanel, gbc);

    panel = new JPanel(new BorderLayout());
    panel.add(soundPanelWrapper, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);
    soundPanelWrapper.setBorder(BorderFactory.createLoweredBevelBorder());

    buttonPanel.addControl(0, ViewerUtil.createViewerSyncButton(panel, getResourceEntry()), ButtonPanel.Control.SYNC_VIEW);

    loadSoundResource();

    return panel;
  }

  // Returns the top level container associated with this viewer
  private Container getContainer() {
    if (panel != null) {
      return panel.getTopLevelAncestor();
    } else {
      return NearInfinity.getInstance();
    }
  }

  private void loadSoundResource() {
    setLoaded(false);
    try {
      soundPanel.loadSound(getResourceEntry(), this::setLoaded);
    } catch (Exception e) {
      Logger.error(e);
      JOptionPane.showMessageDialog(getContainer(), e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private synchronized void setLoaded(boolean success) {
    if (miConvert != null) {
      miConvert.setEnabled(success);
      buttonPanel.getControlByType(PROPERTIES).setEnabled(true);
    }

    if (success && SoundPanel.isAutoplayEnabled()) {
      soundPanel.setPlaying(true);
    }
  }

  /** Shows a message dialog with basic properties of the current sound resource. */
  private void showProperties() {
    if (soundPanel.getAudioBuffer() == null) {
      return;
    }

    final AudioBuffer audioBuffer = soundPanel.getAudioBuffer();
    final String resName = entry.getResourceName().toUpperCase(Locale.ENGLISH);
    String format;
    int rate;
    int channels;
    String channelsDesc;
    String duration = SoundPanel.DisplayFormat.ELAPSED_PRECISE.toString(audioBuffer.getDuration(), 0L);
    final String extra;
    if (audioBuffer instanceof OggBuffer) {
      format = "Ogg Vorbis";
      final OggBuffer buf = (OggBuffer) audioBuffer;
      rate = buf.getInfo().rate;
      channels = buf.getInfo().channels;
      double bitrate = entry.getResourceSize();
      bitrate = bitrate * 8.0 / 1000.0; // in kbit
      bitrate = bitrate * 1000.0 / buf.getDuration();  // per second
      extra = "Bitrate:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + ((long) bitrate) + " kbps";
    } else if (audioBuffer instanceof AcmBuffer) {
      if (audioBuffer instanceof WavcBuffer) {
        format = "WAVC/ACM";
      } else {
        format = "ACM";
      }
      final AcmBuffer buf = (AcmBuffer) audioBuffer;
      rate = buf.getSampleRate();
      channels = buf.getChannels();
      extra = "Bits/Sample:&nbsp;" + buf.getBitsPerSample();
    } else {
      format = "PCM";
      rate = audioBuffer.getSampleRate();
      channels = audioBuffer.getChannels();
      extra = "Bits/Sample:&nbsp;" + audioBuffer.getBitsPerSample();
    }

    switch (channels) {
      case 1:
        channelsDesc = " (Mono)";
        break;
      case 2:
        channelsDesc = " (Stereo)";
        break;
      default:
        channelsDesc = "";
        break;
    }

    final String br = "<br />";
    String sb = "<html><div style='font-family:monospace'>" +
        "Format:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + format + br +
        "Duration:&nbsp;&nbsp;&nbsp;&nbsp;" + duration + br +
        extra + br +
        "Sample Rate:&nbsp;" + rate + " Hz" + br +
        "Channels:&nbsp;&nbsp;&nbsp;&nbsp;" + channels + channelsDesc + br +
        "</div></html>";
    JOptionPane.showMessageDialog(panel, sb, "Properties of " + resName, JOptionPane.INFORMATION_MESSAGE);
  }

  // --------------------- End Interface Viewable ---------------------
}
