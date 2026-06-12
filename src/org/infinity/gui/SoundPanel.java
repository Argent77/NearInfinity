// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSliderUI;

import org.infinity.exceptions.ResourceNotFoundException;
import org.infinity.icon.Icons;
import org.infinity.resource.Closeable;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.sound.AudioBuffer;
import org.infinity.resource.sound.AudioFactory;
import org.infinity.resource.sound.AudioPlayback;
import org.infinity.resource.sound.AudioStateEvent;
import org.infinity.resource.sound.AudioStateListener;
import org.infinity.resource.sound.BufferedAudioPlayback;
import org.infinity.resource.sound.BufferedAudioPlayer;
import org.infinity.resource.sound.WavBuffer;
import org.infinity.util.Threading;
import org.tinylog.Logger;

/**
 * A customizable panel with controls for playing back individual sound clips.
 * <p>
 * Playback state of sounds can be tracked by {@link AudioStateEvent}s.
 * </p>
 */
public class SoundPanel extends JPanel implements Closeable {
  /** Optional UI controls for display. */
  public enum Option {
    /**
     * Specifies that the {@code Play} and {@code Pause} buttons are combined to a single button to save space.
     */
    COMPACT_CONTROLS,
    /**
     * Specifies a label that displays the current playback time. The control is shown below the playback controls and
     * progress bar if visible.
     * <p>
     * The {@link DisplayFormat} enum can be used to further customize the display.
     * </p>
     */
    TIME_LABEL,
    /**
     * Specifies a checkbox for enabling or disabling looped playback. The control is shown on the right side of the
     * playback controls.
     */
    LOOP_CHECKBOX,
    /**
     * Specifies a checkbox for enabling autoplay on load. The control is shown on the right side behind the loop
     * checkbox.
     */
    AUTOPLAY_CHECKBOX,
    /**
     * Specifies a slider that shows the playback current progress and allows the user to jump to specific positions
     * within the audio clip. The control is shown directly below the playback controls.
     */
    PROGRESS_BAR,
    /**
     * Specifies that the progress bar should display labels for every major tick (e.g. every 10 or 30 seconds).
     * <p>This option is only effective if {@link #PROGRESS_BAR} is enabled as well.</p>
     */
    PROGRESS_BAR_LABELS,
  }

  /** List of available formatted strings for displaying playback time. */
  public enum DisplayFormat {
    /** Display of elapsed time (minutes and seconds): {@code mm:ss}. */
    ELAPSED("%1$02d:%2$02d"),

    /** Display of elapsed time (minutes, seconds, deciseconds): {@code mm:ss,x}. */
    ELAPSED_PRECISE("%1$02d:%2$02d,%3$d"),

    /** Display of elapsed and total time (minutes and seconds): {@code mm:ss / mm:ss}. */
    ELAPSED_TOTAL("%1$02d:%2$02d / %4$02d:%5$02d"),

    /** Display of elapsed and total time (minutes, seconds, and deciseconds): {@code mm:ss,x / mm:ss,x}. */
    ELAPSED_TOTAL_PRECISE("%1$02d:%2$02d,%3$d / %4$02d:%5$02d,%6$d"),
    ;

    /**
     * Format string supports the following positional placeholders:
     * <ul>
     * <li>Index 1: Elapsed time, minutes portion (integer)</li>
     * <li>Index 2: Elapsed time, seconds portion (integer)</li>
     * <li>Index 3: Elapsed time, deciseconds portion (integer)</li>
     *
     * <li>Index 4: Total time, minutes portion (integer)</li>
     * <li>Index 5: Total time, seconds portion (integer)</li>
     * <li>Index 6: Total time, deciseconds portion (integer)</li>
     * </ul>
     */
    private final String fmt;

    DisplayFormat(String fmt) {
      this.fmt = fmt;
    }

    /**
     * Returns the format string associated with the enum value.
     *
     * @return A format string.
     */
    public String getFormatString() {
      return fmt;
    }

    /**
     * Returns a formatted string based on the specified parameters.
     *
     * @param elapsed The elapsed playback time, in milliseconds.
     * @param total The total playback time, in milliseconds.
     * @return Formatted string based on {@link #getFormatString()}..
     */
    public String toString(long elapsed, long total) {
      if (fmt.isEmpty()) {
        // shortcut
        return "";
      } else {
        final int elapsedMinutes = (int) (elapsed / 60_000L);
        final int elapsedSeconds = (int) ((elapsed / 1000L) % 60L);
        final int elapsedFractional = (int) ((elapsed / 100L) % 10L);
        final int totalMinutes = (int) (total / 60_000L);
        final int totalSeconds = (int) ((total / 1000L) % 60L);
        final int totalFractional = (int) ((total / 100L) % 10L);
        return String.format(fmt, elapsedMinutes, elapsedSeconds, elapsedFractional, totalMinutes, totalSeconds,
            totalFractional);
      }
    }
  }

  /**
   * Property name for {@link PropertyChangeEvent} calls.
   * Associated parameter is of type {@link Boolean} and indicates the playback state (playing/stopped).
   */
  public static final String PROPERTY_NAME_PLAYING = "soundPanelPlaying";

  /**
   * Property name for {@link PropertyChangeEvent} calls.
   * Associated parameter is of type {@link Boolean} and indicates the paused state (paused/playing).
   */
  public static final String PROPERTY_NAME_PAUSED = "soundPanelPaused";

  private static final String CMD_PLAY      = "play";
  private static final String CMD_PAUSE     = "pause";
  private static final String CMD_STOP      = "stop";
  private static final String CMD_LOOP      = "loop";
  private static final String CMD_AUTOPLAY  = "autoplay";

  private static final ImageIcon ICON_PLAY  = Icons.ICON_PLAY_16.getIcon();
  private static final ImageIcon ICON_PAUSE = Icons.ICON_PAUSE_16.getIcon();
  private static final ImageIcon ICON_STOP  = Icons.ICON_STOP_16.getIcon();

  private static boolean looped = false;
  private static boolean autoplay = false;

  private final List<AudioStateListener> stateListeners = new ArrayList<>();

  private final Runner runner;
  private final Listeners listener = new Listeners();

  private JButton playButton;
  private JButton pauseButton;
  private JButton stopButton;
  private JLabel displayLabel;
  private JCheckBox loopCheckBox;
  private JCheckBox autoplayCheckBox;
  private FixedSlider progressSlider;

  private ResourceEntry soundEntry;
  private DisplayFormat displayFormat;
  private AudioBuffer audioBuffer;

  private boolean closed;

  private boolean progressAdjusting;
  private boolean combinedPlayPause;
  private boolean showProgressLabels;

  public static boolean isLoopEnabled() {
    return looped;
  }

  public static boolean isAutoplayEnabled() {
    return autoplay;
  }

  /**
   * Creates a new sound panel. Initializes it with {@link DisplayFormat#ELAPSED_TOTAL} to display time but does not
   * open a sound resource.
   *
   * @param options A set of {@link Option} values that controls visibility of optional control elements.
   */
  public SoundPanel(Option... options) {
    super(new GridBagLayout());
    init(options);
    setDisplayFormat(DisplayFormat.ELAPSED_TOTAL);
    runner = new Runner();
  }

  /**
   * Creates a new sound panel, opens the specified sound resource and initializes it with
   * {@link DisplayFormat#ELAPSED_TOTAL} to display time.
   *
   * @param entry   {@link ResourceEntry} of the sound resource.
   * @param options A set of {@link Option} values that controls visibility of optional control elements.
   * @throws ResourceNotFoundException if the resource referenced by the {@code ResourceEntry} parameter does not exist.
   * @throws NullPointerException      if {@code entry} is {@code null}.
   */
  public SoundPanel(ResourceEntry entry, Option... options) throws ResourceNotFoundException {
    this(entry, DisplayFormat.ELAPSED_TOTAL, false, options);
  }

  /**
   * Creates a new sound panel and opens the specified sound resource.
   *
   * @param entry   {@link ResourceEntry} of the sound resource.
   * @param format  {@link DisplayFormat} enum with the format description. {@code null} resolves to
   *                  {@link DisplayFormat#ELAPSED_TOTAL}.
   * @param options A set of {@link Option} values that controls visibility of optional control elements.
   * @throws ResourceNotFoundException if the resource referenced by the {@code ResourceEntry} parameter does not exist.
   * @throws NullPointerException      if {@code entry} is {@code null}.
   */
  public SoundPanel(ResourceEntry entry, DisplayFormat format, Option... options) throws ResourceNotFoundException {
    this(entry, format, false, options);
  }

  /**
   * Creates a new sound panel and opens the specified sound resource.
   *
   * @param entry    {@link ResourceEntry} of the sound resource.
   * @param format   {@link DisplayFormat} enum with the format description. {@code null} resolves to
   *                   {@link DisplayFormat#ELAPSED_TOTAL}.
   * @param playback Specifies whether sound playback should start automatically.
   * @param options  A set of {@link Option} values that controls visibility of optional control elements.
   * @throws ResourceNotFoundException if the resource referenced by the {@code ResourceEntry} parameter does not exist.
   * @throws NullPointerException      if {@code entry} is {@code null}.
   */
  public SoundPanel(ResourceEntry entry, DisplayFormat format, boolean playback, Option... options)
      throws ResourceNotFoundException {
    super(new GridBagLayout());
    init(options);
    setDisplayFormat(format);
    runner = new Runner();
    loadSound(entry);
    if (playback) {
      setPlaying(true);
    }
  }

  /**
   * Stops playback of the old sound resource, if any, and loads the specified sound resource.
   *
   * @param entry       {@link ResourceEntry} of the sound resource to load.
   * @throws ResourceNotFoundException if the resource referenced by the {@code ResourceEntry} parameter does not exist.
   * @throws NullPointerException      if {@code entry} is {@code null}.
   */
  public void loadSound(ResourceEntry entry) throws ResourceNotFoundException {
    loadSound(entry, null);
  }

  /**
   * Stops playback of the old sound resource, if any, and loads the specified sound resource.
   *
   * @param entry       {@link ResourceEntry} of the sound resource to load.
   * @param onCompleted An optional {@link Consumer} operation that is executed when the resource has been loaded. The
   *                      boolean parameter signals success ({@code true}) or failure ({@code false}) of the load
   *                      operation.
   * @throws ResourceNotFoundException if the resource referenced by the {@code ResourceEntry} parameter does not exist.
   * @throws NullPointerException      if {@code entry} is {@code null}.
   */
  public void loadSound(ResourceEntry entry, Consumer<Boolean> onCompleted) throws ResourceNotFoundException {
    if (isClosed()) {
      return;
    }

    if (entry == null) {
      throw new NullPointerException("entry is null");
    }

    if (entry.getActualPath() != null && !Files.isRegularFile(entry.getActualPath())) {
      throw new ResourceNotFoundException("Not found: " + entry);
    }

    unload();

    // sound resource is loaded asynchronously
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    final Supplier<Throwable> operation = () -> {
      try {
        AudioBuffer.AudioOverride override = null;
        AudioBuffer buffer;

        // ignore # channels in ACM headers
        if (entry.getExtension().equalsIgnoreCase("ACM")) {
          override = AudioBuffer.AudioOverride.overrideChannels(2);
        }

        buffer = AudioFactory.getAudioBuffer(entry, override);
        if (buffer != null) {
          soundEntry = entry;
          runner.setAudio(buffer);
          runner.setLooped(isLooped());
          audioBuffer = buffer;
          initSliderRange();
        } else {
          throw new Exception("Audio format could not be determined.");
        }
      } catch (Throwable e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        return e;
      }
      return null;
    };

    final Consumer<Throwable> finalize = ex -> {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      if (onCompleted != null) {
        onCompleted.accept(ex == null);
      }
      if (ex != null) {
        // operation failed to complete
        Logger.error(ex);
        Threading.invokeInEventThread(() -> JOptionPane.showMessageDialog(SoundPanel.this.getTopLevelAncestor(),
            "Could not load sound resource: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
      }
    };

    CompletableFuture.supplyAsync(operation).thenAccept(finalize);
  }

  /** Stops playback and releases the current sound resource, if any. */
  public void unload() {
    if (isClosed()) {
      return;
    }

    setPlaying(false);
    soundEntry = null;
    audioBuffer = null;
    try {
      runner.setAudio(null);
    } catch (Exception e) {
      Logger.debug(e);
    }
  }

  /**
   * Returns the currently loaded sound resource.
   *
   * @return {@link ResourceEntry} of the current sound resource. Returns {@code null} if no sound resource is loaded.
   */
  public ResourceEntry getSoundResource() {
    return soundEntry;
  }

  /**
   * Provides access to basic properties of the currently loaded audio resource.
   *
   * @return {@link AudioBuffer} instance of the currently loaded audio resource. Returns {@code null} if no audio
   *         resource is loaded.
   */
  public AudioBuffer getAudioBuffer() {
    return audioBuffer;
  }

  /**
   * Returns the current display format of playback time.
   *
   * @return a {@link DisplayFormat} object describing the current playback time display.
   */
  public DisplayFormat getDisplayFormat() {
    return displayFormat;
  }

  /**
   * Convenience method that returns whether the currently loaded sound resource contains uncompressed WAV audio data.
   *
   * @return {@code true} if a sound resource is loaded and contains uncompressed WAV audio data, {@code false}
   *         otherwise.
   */
  public boolean isWavFile() {
    return audioBuffer instanceof WavBuffer;
  }

  /**
   * Returns {@code true} if the control designated by the specified {@link Option} enum is visible.
   *
   * @param option {@link Option} enum to check.
   * @return {@code true} if the associated control is visible, {@code false} otherwise.
   * @throws NullPointerException if {@code option} is {@code null}.
   */
  public boolean isOptionEnabled(Option option) {
    if (option == null) {
      throw new NullPointerException("option is null");
    }

    switch (option) {
      case COMPACT_CONTROLS:
        return combinedPlayPause;
      case LOOP_CHECKBOX:
        return loopCheckBox.isVisible();
      case AUTOPLAY_CHECKBOX:
        return autoplayCheckBox.isVisible();
      case PROGRESS_BAR:
        return progressSlider.isVisible();
      case PROGRESS_BAR_LABELS:
        return showProgressLabels;
      case TIME_LABEL:
        return displayLabel.isVisible();
    }

    return false;
  }

  /**
   * Specifies the display format for playback time of the sound resource.
   *
   * @param format {@link DisplayFormat} enum with the format description. {@code null} resolves to
   *                 {@link DisplayFormat#ELAPSED_TOTAL}.
   */
  public void setDisplayFormat(DisplayFormat format) {
    displayFormat = (format != null) ? format : DisplayFormat.ELAPSED_TOTAL;
  }

  /** Returns whether loop mode is enabled. */
  public boolean isLooped() {
    return loopCheckBox.isSelected();
  }

  /** Enables or disable loop mode. */
  public void setLooped(boolean loop) {
    if (isClosed()) {
      return;
    }

    if (loopCheckBox.isSelected() != loop) {
      loopCheckBox.setSelected(loop);
    }
    looped = loop;
    runner.setLooped(loop);
  }

  /** Returns whether autoplay mode is enabled. */
  public boolean isAutoplay() {
    return autoplayCheckBox.isSelected();
  }

  public void setAutoplay(boolean autoplay) {
    if (isClosed()) {
      return;
    }

    if (autoplayCheckBox.isSelected() != autoplay) {
      autoplayCheckBox.setSelected(autoplay);
    }
    SoundPanel.autoplay = autoplay;
  }

  /**
   * Returns whether playback is currently active. Pausing playback does not affect the result.
   *
   * @return {@code true} if sound is currently played back or paused, {@code false} otherwise.
   */
  public boolean isPlaying() {
    if (isClosed()) {
      return false;
    }

    return runner.isPlaying();
  }

  /**
   * Sets the playback state of the current sound resource. The paused state does not affect the playback state.
   *
   * @param play Specify {@code true} to start playback or {@code false} to stop playback.
   */
  public void setPlaying(boolean play) {
    if (isClosed()) {
      return;
    }

    if (runner.isPlaying() == play) {
      return;
    }

    runner.setPlaying(play);
  }

  /**
   * Returns whether playback is currently paused.
   *
   * @return {@code true} only if playback is active but paused, {@code false} otherwise.
   */
  public boolean isPaused() {
    if (isClosed()) {
      return false;
    }

    return runner.isPaused();
  }

  /**
   * Pauses playback. Does nothing if playback currently not active.
   *
   * @param pause Specify {@code true} to pause current playback. Specify {@code false} to continue playback.
   */
  public void setPaused(boolean pause) {
    if (isClosed()) {
      return;
    }

    if (!runner.isPlaying() || runner.isPaused() == pause) {
      return;
    }

    runner.setPaused(pause);
  }

  /**
   * Returns whether this sound panel has been closed. A closed panel does not load or play sound files anymore.
   *
   * @return {@code true} if the sound panel has been closed, {@code false} otherwise.
   */
  public boolean isClosed() {
    return closed;
  }

  /**
   * Stops playback and releases any resources. After calling this method it is not possible to load or play sound
   * resources anymore.
   */
  @Override
  public void close() throws Exception {
    unload();
    runner.terminate();
    closed = true;
  }

  @Override
  public void setEnabled(boolean enabled) {
    if (enabled) {
      updateControls();
    } else {
      playButton.setEnabled(false);
      if (!combinedPlayPause) {
        pauseButton.setEnabled(false);
      }
      stopButton.setEnabled(false);
      progressSlider.setEnabled(false);
    }
    displayLabel.setEnabled(enabled);
    loopCheckBox.setEnabled(enabled);
    autoplayCheckBox.setEnabled(enabled);
    super.setEnabled(enabled);
  }

  /** Adds a {@link AudioStateListener} to the sound panel instance. */
  public void addAudioStateListener(AudioStateListener l) {
    if (l != null) {
      listenerList.add(AudioStateListener.class, l);
    }
  }

  /** Returns all registered {@link AudioStateListener}s for this audio player instance. */
  public AudioStateListener[] getAudioStateListeners() {
    return listenerList.getListeners(AudioStateListener.class);
  }

  /** Removes a {@link AudioStateListener} from the audio player instance. */
  public void removeAudioStateListener(AudioStateListener l) {
    if (l != null) {
      listenerList.remove(AudioStateListener.class, l);
    }
  }

  /** Fires a {@link AudioStateListener} of the specified name to all registered listeners. */
  private void fireAudioStateEvent(AudioStateEvent.State state, Object value) {
    // collecting
    stateListeners.clear();
    final Object[] entries = getAudioStateListeners();
    for (int i = entries.length - 2; i >= 0; i -= 2) {
      if (entries[i] == AudioStateListener.class) {
        stateListeners.add((AudioStateListener) entries[i + 1]);
      }
    }

    // executing
    if (!stateListeners.isEmpty()) {
      final AudioStateEvent event = new AudioStateEvent(this, state, value);
      SwingUtilities.invokeLater(() -> stateListeners.forEach(l -> l.audioStateChanged(event)));
    }
  }

  /** Fires a {@link AudioStateEvent} when a sound resource has been opened and is ready for playback. */
  private void fireSoundOpened() {
    final String value = (getSoundResource() != null) ? getSoundResource().getResourceName() : null;
    fireAudioStateEvent(AudioStateEvent.State.OPEN, value);
  }

  /** Fires a {@link AudioStateEvent} when the current sound resource has been closed. */
  private void fireSoundClosed() {
    final String value = (getSoundResource() != null) ? getSoundResource().getResourceName() : null;
    fireAudioStateEvent(AudioStateEvent.State.CLOSE, value);
  }

  /** Fires a {@link AudioStateEvent} when starting playback. */
  private void fireSoundStarted() {
    fireAudioStateEvent(AudioStateEvent.State.START, null);
  }

  /** Fires a {@link AudioStateEvent} when stopping playback. */
  private void fireSoundStopped() {
    fireAudioStateEvent(AudioStateEvent.State.STOP, null);
  }

  /** Fires a {@link AudioStateEvent} when entering the paused state. */
  private void fireSoundPaused() {
    fireAudioStateEvent(AudioStateEvent.State.PAUSE, runner.getSoundPosition());
  }

  /** Fires a {@link AudioStateEvent} when resuming from the paused state. */
  private void fireSoundResumed() {
    fireAudioStateEvent(AudioStateEvent.State.RESUME, runner.getSoundPosition());
  }

  private void updateLabel() {
    final long elapsedTime = runner.getSoundPosition();
    final long totalTime = runner.getTotalLength();
    final String displayString = getDisplayFormat().toString(elapsedTime, totalTime);
    displayLabel.setText(displayString);

    final AdjustingBoundedRangeModel model = (AdjustingBoundedRangeModel)progressSlider.getModel();
    model.setDirectValue((int)elapsedTime);
  }

  /** Updates playback UI controls to reflect the current state. */
  private void updateControls() {
//    Logger.trace("SoundPanel.updateControls: isAvailable={}, isPlaying={}, isPaused={}", runner.isAvailable(),
//        runner.isPlaying(), runner.isPaused());
    if (runner.isAvailable()) {
      if (runner.isPlaying()) {
        if (combinedPlayPause) {
          playButton.setIcon(runner.isPaused() ? ICON_PLAY : ICON_PAUSE);
          playButton.setEnabled(true);
        } else {
          playButton.setEnabled(runner.isPaused());
          pauseButton.setEnabled(!runner.isPaused());
        }
        stopButton.setEnabled(true);
        progressSlider.setEnabled(true);
      } else {
        if (combinedPlayPause) {
          playButton.setIcon(ICON_PLAY);
        } else {
          pauseButton.setEnabled(false);
        }
        playButton.setEnabled(true);
        stopButton.setEnabled(false);
        progressSlider.setEnabled(false);
      }
    } else {
      playButton.setIcon(ICON_PLAY);
      playButton.setEnabled(false);
      if (!combinedPlayPause) {
        pauseButton.setEnabled(false);
      }
      stopButton.setEnabled(false);
      progressSlider.setEnabled(false);
    }
  }

  /** Resets slider settings of the progress bar. */
  private void initSliderRange() {
    if (runner.isAvailable()) {
      final int duration = (int) runner.getTotalLength();
      progressSlider.setMaximum(duration);
      if (duration < 45_000) {  // 45 seconds or less
        // major: per ten seconds, minor: per second
        progressSlider.setMajorTickSpacing(10_000);
        progressSlider.setMinorTickSpacing(1_000);
      } else if (duration < 240_000) {  // 4 minutes or less
        // major: per 30 seconds, minor: per 3 seconds
        progressSlider.setMajorTickSpacing(30_000);
        progressSlider.setMinorTickSpacing(3_000);
      } else if (duration < 900_000) {  // 15 minutes or less
        // major: per 2 minutes, minor: per 12 seconds
        progressSlider.setMajorTickSpacing(120_000);
        progressSlider.setMinorTickSpacing(12_000);
      } else {
        // major: per 5 minutes, minor: per 30 seconds
        progressSlider.setMajorTickSpacing(300_000);
        progressSlider.setMinorTickSpacing(30_000);
      }
      initSliderTickLabels();
    } else {
      progressSlider.setMaximum(1);
      progressSlider.setMajorTickSpacing(1);
      progressSlider.setMinorTickSpacing(1);
    }
  }

  /** Defines labels for the major progress bar ticks. */
  private void initSliderTickLabels() {
    if (progressSlider == null || !progressSlider.isVisible() || !showProgressLabels) {
      return;
    }

    progressSlider.setLabelTable(null);

    final Hashtable<Integer, JLabel> labels = new Hashtable<>();
    final int spacing = progressSlider.getMajorTickSpacing();
    for (int pos = progressSlider.getMinimum(); pos < progressSlider.getMaximum(); pos += spacing) {
      labels.put(pos, createSliderTickLabel(pos));
    }

    // add label for end of range if suitable
    final int minSpace;
    if (progressSlider.getMaximum() < 10_000) {
      minSpace = 0;
    } else if (progressSlider.getMaximum() < 45_000) {
      minSpace = spacing * 2 / 3;
    } else if (progressSlider.getMaximum() < 240_000) {
      minSpace = spacing / 2;
    } else {
      minSpace = spacing / 4;
    }
    final int remainingSpace = (progressSlider.getMaximum() - progressSlider.getMinimum()) % spacing;
    if (remainingSpace > minSpace) {
      labels.put(progressSlider.getMaximum(), createSliderTickLabel(progressSlider.getMaximum()));
    }

    if (!labels.isEmpty()) {
      progressSlider.setLabelTable(labels);
    }
  }

  /** Creates a label for a slider tick with the specified time value. */
  private static JLabel createSliderTickLabel(int timeMs) {
    final int min = timeMs / 60_000;
    final int sec = (timeMs / 1000) % 60;
    final JLabel label = new JLabel(String.format("%02d:%02d", min, sec));
    final Font font = label.getFont();
    label.setFont(font.deriveFont(Font.PLAIN, font.getSize2D() * 0.75f));
    return label;
  }

  /** Creates the sound panel. */
  private void init(Option[] options) {
    closed = false;

    combinedPlayPause = isOption(options, Option.COMPACT_CONTROLS);

    playButton = new JButton(ICON_PLAY);
    playButton.setActionCommand(CMD_PLAY);
    playButton.addActionListener(listener);

    if (!combinedPlayPause) {
      pauseButton = new JButton(ICON_PAUSE);
      pauseButton.setActionCommand(CMD_PAUSE);
      pauseButton.addActionListener(listener);
    } else {
      playButton.setToolTipText("Start or pause playback.");
    }

    stopButton = new JButton(ICON_STOP);
    stopButton.setActionCommand(CMD_STOP);
    stopButton.addActionListener(listener);

    loopCheckBox = new JCheckBox("Loop", looped);
    loopCheckBox.setActionCommand(CMD_LOOP);
    loopCheckBox.addActionListener(listener);
    loopCheckBox.setVisible(isOption(options, Option.LOOP_CHECKBOX));

    autoplayCheckBox = new JCheckBox("Autoplay", autoplay);
    autoplayCheckBox.setActionCommand(CMD_AUTOPLAY);
    autoplayCheckBox.addActionListener(listener);
    autoplayCheckBox.setVisible(isOption(options, Option.AUTOPLAY_CHECKBOX));

    displayLabel = new JLabel(DisplayFormat.ELAPSED_TOTAL.toString(0L, 0L), SwingConstants.LEADING);
    displayLabel.setVisible(isOption(options, Option.TIME_LABEL));

    progressSlider = new FixedSlider(new AdjustingBoundedRangeModel());
    progressSlider.setOrientation(SwingConstants.HORIZONTAL);
    progressSlider.setPaintTicks(true);
    progressSlider.addChangeListener(listener);
    progressSlider.setVisible(isOption(options, Option.PROGRESS_BAR));

    showProgressLabels = isOption(options, Option.PROGRESS_BAR_LABELS);
    progressSlider.setPaintLabels(showProgressLabels);

    // assembling panel
    final GridBagConstraints gbc = new GridBagConstraints();
    final JPanel playbackPanel = new JPanel(new GridBagLayout());
    int idx = 0;
    ViewerUtil.setGBC(gbc, idx++, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    playbackPanel.add(playButton, gbc);
    if (!combinedPlayPause) {
      ViewerUtil.setGBC(gbc, idx++, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
          new Insets(0, 8, 0, 0), 0, 0);
      playbackPanel.add(pauseButton, gbc);
    }
    ViewerUtil.setGBC(gbc, idx++, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 0, 0), 0, 0);
    playbackPanel.add(stopButton, gbc);

    final JPanel optionsPanel = new JPanel(new GridBagLayout());
    idx = 0;
    ViewerUtil.setGBC(gbc, idx++, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    optionsPanel.add(loopCheckBox, gbc);
    final int left = loopCheckBox.isVisible() ? 8 : 0;
    ViewerUtil.setGBC(gbc, idx++, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, left, 0, 0), 0, 0);
    optionsPanel.add(autoplayCheckBox, gbc);

    idx = 0;
    ViewerUtil.setGBC(gbc, 0, idx++, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    add(playbackPanel, gbc);

    ViewerUtil.setGBC(gbc, 0, idx++, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 0, 0), 0, 0);
    add(optionsPanel, gbc);

    ViewerUtil.setGBC(gbc, 0, idx++, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 0, 0), 0, 0);
    add(progressSlider, gbc);

    ViewerUtil.setGBC(gbc, 0, idx++, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 0, 0, 0), 0, 0);
    add(displayLabel, gbc);

    // cosmetic adjustment
    if (progressSlider.isVisible() && !loopCheckBox.isVisible()) {
      final int sliderWidth = playbackPanel.getPreferredSize().width;
      final int sliderHeight = progressSlider.getPreferredSize().height;
      progressSlider.setPreferredSize(new Dimension(sliderWidth, sliderHeight));
    }
    if (displayLabel.isVisible() && !loopCheckBox.isVisible()) {
      displayLabel.setHorizontalAlignment(SwingConstants.CENTER);
    }
  }

  /**
   * Returns whether the specified option is listed in the options array.
   *
   * @param options Array of {@link Option} enums.
   * @param option  {@link Option} to test.
   * @return {@code true} if {@code option} is found in {@code options}, {@code false} otherwise.
   */
  private boolean isOption(Option[] options, Option option) {
    boolean retVal = false;
    if (options != null) {
      for (final Option o : options) {
        if (o == option) {
          retVal = true;
          break;
        }
      }
    }
    return retVal;
  }

  // -------------------------- INNER CLASSES --------------------------

  /** Listeners for the SoundPanel class. */
  private class Listeners implements ActionListener, ChangeListener {
    public Listeners() {
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      switch (e.getActionCommand()) {
        case CMD_PLAY:
          if (SoundPanel.this.isPlaying()) {
            if (SoundPanel.this.combinedPlayPause) {
              SoundPanel.this.setPaused(!SoundPanel.this.isPaused());
            } else if (SoundPanel.this.isPaused()) {
              SoundPanel.this.setPaused(false);
            }
          } else {
            SoundPanel.this.setPlaying(true);
          }
          break;
        case CMD_PAUSE:
          if (SoundPanel.this.isPlaying()) {
            SoundPanel.this.setPaused(true);
          }
          break;
        case CMD_STOP:
          SoundPanel.this.setPlaying(false);
          break;
        case CMD_LOOP:
          setLooped(loopCheckBox.isSelected());
          break;
        case CMD_AUTOPLAY:
          setAutoplay(autoplayCheckBox.isSelected());
          break;
      }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      if (e.getSource() == progressSlider) {
        if (progressSlider.isEnabled()) {
          if (progressSlider.getValueIsAdjusting() && !progressAdjusting) {
            // starting to manually drag the slider knob
            progressAdjusting = true;
          } else if (!progressSlider.getValueIsAdjusting() && progressAdjusting) {
            // stopped dragging the slider knob
            progressAdjusting = false;
            final AdjustingBoundedRangeModel model = (AdjustingBoundedRangeModel)progressSlider.getModel();
            model.setValue(model.getAdjustedValue());
            runner.setSoundPosition(model.getAdjustedValue());
          }
        }
      }
    }
  }

  /**
   * Background thread for the SoundPanel class that handles playback of sound clips.
   */
  private class Runner implements Runnable, AudioStateListener {
    private final ReentrantLock lock = new ReentrantLock();
    private final Thread thread;

    private AudioPlayback player;
    private boolean running;

    /** Initializes the class instance and starts a background thread. */
    public Runner() {
      thread = new Thread(this);
      running = true;
      thread.start();
    }

    /**
     * Assigns a new {@link BufferedAudioPlayer} instance to the runner. The old player instance, if any, is properly
     * closed before the new instance is assigned.
     *
     * @param newBuffer the {@link AudioBuffer} object to load.
     * @throws IOException                   if an I/O error occurs.
     * @throws UnsupportedAudioFileException if the audio data is incompatible with the player.
     * @throws LineUnavailableException      if the audio line is not available due to resource restrictions.
     * @throws IllegalArgumentException      if the audio data is invalid.
     */
    public void setAudio(AudioBuffer newBuffer) throws Exception{
      lock.lock();
      try {
        if (player != null) {
          player.setPlaying(false);
          player.close();
          player = null;
        }

        if (newBuffer != null) {
          // TODO: externalize audio player instantiation to abstract audio player backend
          player = new BufferedAudioPlayer(newBuffer, this);
          ((BufferedAudioPlayback)player).setLooped(SoundPanel.looped);
        }
      } finally {
        lock.unlock();
      }

      signal();
      updatePanel();
    }

    /** Returns whether an audio player is currently initialized. */
    public boolean isAvailable() {
      return (player != null);
    }

    /** Returns whether loop mode is enabled. */
    @SuppressWarnings("unused")
    public boolean isLooped() {
      return player instanceof BufferedAudioPlayback && ((BufferedAudioPlayback) player).isLooped();
    }

    /** Enables or disable looped playback. */
    public void setLooped(boolean loop) {
      if (player instanceof BufferedAudioPlayback) {
        ((BufferedAudioPlayback)player).setLooped(loop);
      }
    }

    /**
     * Returns whether the player is playing. Paused state does not affect the result.
     * Returns {@code false} if the player is not initialized.
     */
    public boolean isPlaying() {
      return (player != null) && player.isPlaying();
    }

    /**
     * Sets the playback state of the player. {@code true} starts playback, {@code false} stops playback. Paused
     * state does not affect the current playing state. Does nothing if the player is not initialized.
     */
    public void setPlaying(boolean play) {
      if (player != null) {
        player.setPlaying(play);
      }
    }

    /**
     * Returns whether the player is currently in the paused state. This state does not affect the {@link #isPlaying()}
     * state. Always returns {@code false} if playback is stopped or the player is not initialized.
     */
    public boolean isPaused() {
      return (player != null) && player.isPaused();
    }

    /**
     * Sets the paused state of the player. {@code true} pauses playback, {@code false} resumes playback. Does nothing
     * if playback is not active or the player has not been initialized.
     */
    public void setPaused(boolean pause) {
      if (player != null) {
        player.setPaused(pause);
      }
    }

    /**
     * Returns the total play length of the sound clip, in milliseconds. Returns {@code 0} if the player is not
     * initialized.
     */
    public long getTotalLength() {
      return (player instanceof BufferedAudioPlayback) ? ((BufferedAudioPlayback)player).getTotalLength() : 0L;
    }

    /**
     * Returns the elapsed playback time of the sound clip, in milliseconds. Returns {@code 0} if the player is not
     * initialized.
     */
    public long getSoundPosition() {
      return (player != null) ? player.getSoundPosition() : 0L;
    }

    /**
     * Sets an explicit playback position.
     *
     * @param position New playback position in milliseconds. Position is clamped to the available audio clip duration.
     */
    public void setSoundPosition(int position) {
      if (player instanceof BufferedAudioPlayback) {
        ((BufferedAudioPlayback)player).setSoundPosition(position);
      }
    }

    /** Signals the runner to wake up from a waiting state. */
    public void signal() {
      thread.interrupt();
    }

    /** Returns whether the runner is active. */
    public boolean isRunning() {
      return running;
    }

    /** Signals the runner to terminate. */
    public void terminate() {
      running = false;
      signal();
      try {
        setAudio(null);
      } catch (Exception e) {
        Logger.debug(e);
      }
      updatePanel();
    }

    @Override
    public void run() {
      while (isRunning()) {
        final boolean isActive;
        lock.lock();
        try {
          isActive = (player != null) && player.isPlaying() && !player.isPaused();
        } finally {
          lock.unlock();
        }
        if (isActive) {
          try {
            SwingUtilities.invokeLater(SoundPanel.this::updateLabel);
            Thread.sleep(100L);
          } catch (InterruptedException e) {
            // waking up prematurely
          }
        } else {
          try {
            Thread.sleep(Integer.MAX_VALUE);
          } catch (InterruptedException e) {
            // waking up from sleep
          }
        }
      }
    }

    @Override
    public void audioStateChanged(AudioStateEvent event) {
//      Logger.trace("{}.audioStateChanged({})", SoundPanel.class.getSimpleName(), event);
      switch (event.getAudioState()) {
        case START:
          SoundPanel.this.fireSoundStarted();
          break;
        case STOP:
          SoundPanel.this.fireSoundStopped();
          break;
        case PAUSE:
          SoundPanel.this.fireSoundPaused();
          break;
        case RESUME:
          SoundPanel.this.fireSoundResumed();
          break;
        case OPEN:
          SoundPanel.this.fireSoundOpened();
          break;
        case CLOSE:
          SoundPanel.this.fireSoundClosed();
          break;
        default:
      }
      signal();
      updatePanel();
    }

    private void updatePanel() {
      SwingUtilities.invokeLater(() -> {
        SoundPanel.this.updateControls();
        SoundPanel.this.updateLabel();
      });
    }
  }

  /**
   * Extends the {@link JSlider} class to handle issues with setting the slider position if the component was disabled
   * while the user was still dragging the slider knob.
   */
  private static class FixedSlider extends JSlider {
    @SuppressWarnings("unused")
    public FixedSlider() {
      super();
      init();
    }

    @SuppressWarnings("unused")
    public FixedSlider(int orientation) {
      super(orientation);
      init();
    }

    @SuppressWarnings("unused")
    public FixedSlider(int min, int max) {
      super(min, max);
      init();
    }

    @SuppressWarnings("unused")
    public FixedSlider(int min, int max, int value) {
      super(min, max, value);
      init();
    }

    @SuppressWarnings("unused")
    public FixedSlider(int orientation, int min, int max, int value) {
      super(orientation, min, max, value);
      init();
    }

    public FixedSlider(BoundedRangeModel brm) {
      super(brm);
      init();
    }

    @Override
    public void setEnabled(boolean enabled) {
      clearDragMode();
      super.setEnabled(enabled);
    }

    /**
     * Clears pending dragging mode that is initiated when the user clicks the slider knob and drags it around.
     * <p>
     * Dragging mode is not properly cleared if the component is disabled while dragging is still active which results
     * in an unresponsive position display when the component becomes active again, until the user intentionally clicks
     * on the component again.
     * </p>
     * <p>
     * This method simulates the mouse release by the user and should be called right before the component is disabled.
     * </p>
     */
    private void clearDragMode() {
      final MouseListener[] ml = getMouseListeners();
      if (ml.length > 0) {
        final Point pt = getMousePosition();
        final int x = (pt != null) ? pt.x : 0;
        final int y = (pt != null) ? pt.y : 0;

        final MouseEvent me = new MouseEvent(this, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1,
            false, MouseEvent.BUTTON1);
        for (final MouseListener m : ml) {
          m.mouseReleased(me);
        }
      }
    }

    private void processMousePressed(MouseEvent e) {
      if (getUI() instanceof BasicSliderUI) {
        final BasicSliderUI ui = (BasicSliderUI)getUI();
        final int value;
        if (getOrientation() == SwingConstants.VERTICAL) {
          value = ui.valueForYPosition(e.getY());
        } else {
          value = ui.valueForXPosition(e.getX());
        }
        setValue(value);
      } else {
        Logger.debug("FixedSlider.getUI() not instance of BasicSliderUI");
      }
    }

    private void init() {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          processMousePressed(e);
        }
      });
    }
  }

  /**
   * Specialization of the {@link DefaultBoundedRangeModel} class. It decouples values set during "adjusting" and direct
   * value mode. The "adjusting" value can be received from the separate getter method {@link #getAdjustedValue()}.
   */
  private static class AdjustingBoundedRangeModel extends DefaultBoundedRangeModel {
    private int adjustedValue;

    public AdjustingBoundedRangeModel() {
      super();
    }

    @SuppressWarnings("unused")
    public AdjustingBoundedRangeModel(int value, int extent, int min, int max) {
      super(value, extent, min, max);
      this.adjustedValue = getValue();
    }

    private int getValidatedValue(int n) {
      n = Math.min(n, Integer.MAX_VALUE - getExtent());
      int newValue = Math.max(n, getMinimum());
      if (newValue + getExtent() > getMaximum()) {
        newValue = getMaximum() - getExtent();
      }
      return newValue;
    }

    public void setDirectValue(int n) {
      super.setValue(n);
    }

    @Override
    public void setValue(int n) {
      if (getValueIsAdjusting()) {
        n = Math.min(n, Integer.MAX_VALUE - getExtent());
        int newValue = Math.max(n, getMinimum());
        if (newValue + getExtent() > getMaximum()) {
          newValue = getMaximum() - getExtent();
        }
        adjustedValue = getValidatedValue(newValue);
        fireStateChanged();
      } else {
        super.setValue(n);
      }
    }

    /**
     * Returns the last known value that was assigned in "adjusting" mode.
     *
     * @return the model's last known "adjusting" value.
     */
    public int getAdjustedValue() {
      return adjustedValue;
    }
  }
}
