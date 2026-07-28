// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.infinity.gui.converter.creature.MonsterAnimationLayout.BamFormat;
import org.infinity.gui.converter.creature.MonsterAnimationLayout.Sequence;
import org.infinity.util.Logger;

/** Persistent, validated user settings for the creature animation creator. */
final class CreatureAnimationCreatorSettings {
  private static final String PREF_GAME_ROOT = "GameRoot";
  private static final String PREF_OUTPUT_DIRECTORY = "OutputDirectory";
  private static final String PREF_SOURCE_DIRECTORY = "SourceDirectory";
  private static final String PREF_FAMILY = "AnimationFamily";
  private static final String PREF_BAM_FORMAT = "BamFormat";
  private static final String PREF_COMPRESSED_BAM = "CompressedBam";
  private static final String PREF_SPLIT_BAMS = "SplitBams";
  private static final String PREF_QUADRANTS = "Quadrants";
  private static final String PREF_ARMOR_LEVELS = "ArmorLevels";
  private static final String PREF_CAN_LIE_DOWN = "CanLieDown";
  private static final String PREF_DETECTED_BY_INFRAVISION = "DetectedByInfravision";
  private static final String PREF_FALSE_COLOR = "FalseColor";
  private static final String PREF_PATH_SMOOTH = "PathSmooth";
  private static final String PREF_TRANSLUCENT = "Translucent";
  private static final String PREF_MOVE_SCALE = "MoveScale";
  private static final String PREF_ELLIPSE = "Ellipse";
  private static final String PREF_PERSONAL_SPACE = "PersonalSpace";
  private static final String PREF_BLOOD_COLOR = "BloodColor";
  private static final String PREF_CHUNK_COLOR = "ChunkColor";
  private static final String PREF_PREVIEW_SEQUENCE = "PreviewSequence";
  private static final String PREF_PREVIEW_DIRECTION = "PreviewDirection";
  private static final String PREF_PREVIEW_PLAYING = "PreviewPlaying";
  private static final String PREF_PREVIEW_PIVOT = "PreviewPivot";
  private static final String PREF_PREVIEW_FRAME_RATE = "PreviewFrameRate";
  private static final String PREF_PREVIEW_ZOOM = "PreviewZoom";

  private CreatureAnimationCreatorSettings() {
  }

  static State load(Path defaultOutputDirectory, Path defaultSourceDirectory) {
    try {
      return load(Preferences.userNodeForPackage(CreatureAnimationCreator.class), defaultOutputDirectory,
          defaultSourceDirectory);
    } catch (RuntimeException e) {
      Logger.warn(e, "Could not load Creature Animation Creator preferences.");
      return new State(defaultOutputDirectory, defaultSourceDirectory);
    }
  }

  static void store(State state) {
    try {
      store(Preferences.userNodeForPackage(CreatureAnimationCreator.class), state);
    } catch (BackingStoreException | RuntimeException e) {
      Logger.warn(e, "Could not store Creature Animation Creator preferences.");
    }
  }

  static State load(Preferences preferences, Path defaultOutputDirectory, Path defaultSourceDirectory) {
    Objects.requireNonNull(preferences);
    final State state = new State(defaultOutputDirectory, defaultSourceDirectory);
    final Path storedGameRoot = getPath(preferences, PREF_GAME_ROOT, null);
    if (Objects.equals(state.gameRoot, storedGameRoot)) {
      state.outputDirectory = getPath(preferences, PREF_OUTPUT_DIRECTORY, state.outputDirectory);
      state.sourceDirectory = getPath(preferences, PREF_SOURCE_DIRECTORY, state.sourceDirectory);
    }
    state.family = getEnum(preferences, PREF_FAMILY, CreatureAnimationFamily.class, state.family);
    state.bamFormat = getEnum(preferences, PREF_BAM_FORMAT, BamFormat.class, state.bamFormat);
    state.compressedBam = preferences.getBoolean(PREF_COMPRESSED_BAM, state.compressedBam);
    state.splitBams = preferences.getBoolean(PREF_SPLIT_BAMS, state.splitBams);
    state.quadrants = getBoundedInt(preferences, PREF_QUADRANTS, state.quadrants, 1, 9);
    state.armorLevels = getBoundedInt(preferences, PREF_ARMOR_LEVELS, state.armorLevels, 1, 9);
    state.canLieDown = preferences.getBoolean(PREF_CAN_LIE_DOWN, state.canLieDown);
    state.detectedByInfravision =
        preferences.getBoolean(PREF_DETECTED_BY_INFRAVISION, state.detectedByInfravision);
    state.falseColor = preferences.getBoolean(PREF_FALSE_COLOR, state.falseColor);
    state.pathSmooth = preferences.getBoolean(PREF_PATH_SMOOTH, state.pathSmooth);
    state.translucent = preferences.getBoolean(PREF_TRANSLUCENT, state.translucent);
    state.moveScale = getBoundedInt(preferences, PREF_MOVE_SCALE, state.moveScale, 0, 255);
    state.ellipse = getBoundedInt(preferences, PREF_ELLIPSE, state.ellipse, 0, 255);
    state.personalSpace = getBoundedInt(preferences, PREF_PERSONAL_SPACE, state.personalSpace, 0, 255);
    state.bloodColor = getBoundedInt(preferences, PREF_BLOOD_COLOR, state.bloodColor, 0, 255);
    state.chunkColor = getBoundedInt(preferences, PREF_CHUNK_COLOR, state.chunkColor, 0, 255);
    state.previewSequence =
        getEnum(preferences, PREF_PREVIEW_SEQUENCE, Sequence.class, state.previewSequence);
    state.previewDirection = getBoundedInt(preferences, PREF_PREVIEW_DIRECTION, state.previewDirection, 0,
        AnimationPreviewPanel.PREVIEW_DIRECTIONS.length - 1);
    state.previewPlaying = preferences.getBoolean(PREF_PREVIEW_PLAYING, state.previewPlaying);
    state.previewPivot = preferences.getBoolean(PREF_PREVIEW_PIVOT, state.previewPivot);
    state.previewFrameRate =
        getBoundedInt(preferences, PREF_PREVIEW_FRAME_RATE, state.previewFrameRate,
            AnimationPreviewPanel.MIN_FRAME_RATE, AnimationPreviewPanel.MAX_FRAME_RATE);
    state.previewZoom = getBoundedInt(preferences, PREF_PREVIEW_ZOOM, state.previewZoom,
        AnimationPreviewPanel.MIN_ZOOM_PERCENT, AnimationPreviewPanel.MAX_ZOOM_PERCENT);
    return state;
  }

  static void store(Preferences preferences, State state) throws BackingStoreException {
    Objects.requireNonNull(preferences);
    Objects.requireNonNull(state);
    putPath(preferences, PREF_GAME_ROOT, state.gameRoot);
    putPath(preferences, PREF_OUTPUT_DIRECTORY, state.outputDirectory);
    putPath(preferences, PREF_SOURCE_DIRECTORY, state.sourceDirectory);
    preferences.put(PREF_FAMILY, valueOrDefault(state.family, CreatureAnimationFamily.MONSTER).name());
    preferences.put(PREF_BAM_FORMAT, valueOrDefault(state.bamFormat, BamFormat.BAM_V1).name());
    preferences.putBoolean(PREF_COMPRESSED_BAM, state.compressedBam);
    preferences.putBoolean(PREF_SPLIT_BAMS, state.splitBams);
    preferences.putInt(PREF_QUADRANTS, clamp(state.quadrants, 1, 9));
    preferences.putInt(PREF_ARMOR_LEVELS, clamp(state.armorLevels, 1, 9));
    preferences.putBoolean(PREF_CAN_LIE_DOWN, state.canLieDown);
    preferences.putBoolean(PREF_DETECTED_BY_INFRAVISION, state.detectedByInfravision);
    preferences.putBoolean(PREF_FALSE_COLOR, state.falseColor);
    preferences.putBoolean(PREF_PATH_SMOOTH, state.pathSmooth);
    preferences.putBoolean(PREF_TRANSLUCENT, state.translucent);
    preferences.putInt(PREF_MOVE_SCALE, clamp(state.moveScale, 0, 255));
    preferences.putInt(PREF_ELLIPSE, clamp(state.ellipse, 0, 255));
    preferences.putInt(PREF_PERSONAL_SPACE, clamp(state.personalSpace, 0, 255));
    preferences.putInt(PREF_BLOOD_COLOR, clamp(state.bloodColor, 0, 255));
    preferences.putInt(PREF_CHUNK_COLOR, clamp(state.chunkColor, 0, 255));
    preferences.put(PREF_PREVIEW_SEQUENCE, valueOrDefault(state.previewSequence, Sequence.WALK).name());
    preferences.putInt(PREF_PREVIEW_DIRECTION,
        clamp(state.previewDirection, 0, AnimationPreviewPanel.PREVIEW_DIRECTIONS.length - 1));
    preferences.putBoolean(PREF_PREVIEW_PLAYING, state.previewPlaying);
    preferences.putBoolean(PREF_PREVIEW_PIVOT, state.previewPivot);
    preferences.putInt(PREF_PREVIEW_FRAME_RATE,
        clamp(state.previewFrameRate, AnimationPreviewPanel.MIN_FRAME_RATE,
            AnimationPreviewPanel.MAX_FRAME_RATE));
    preferences.putInt(PREF_PREVIEW_ZOOM,
        clamp(state.previewZoom, AnimationPreviewPanel.MIN_ZOOM_PERCENT,
            AnimationPreviewPanel.MAX_ZOOM_PERCENT));
    preferences.flush();
  }

  private static Path getPath(Preferences preferences, String key, Path fallback) {
    final String value = preferences.get(key, "");
    if (!value.trim().isEmpty()) {
      try {
        return normalize(Paths.get(value));
      } catch (InvalidPathException e) {
        Logger.trace(e);
      }
    }
    return normalize(fallback);
  }

  private static void putPath(Preferences preferences, String key, Path path) {
    final Path normalized = normalize(path);
    if (normalized != null) {
      preferences.put(key, normalized.toString());
    } else {
      preferences.remove(key);
    }
  }

  private static int getBoundedInt(Preferences preferences, String key, int fallback, int minimum, int maximum) {
    return clamp(preferences.getInt(key, fallback), minimum, maximum);
  }

  private static <E extends Enum<E>> E getEnum(Preferences preferences, String key, Class<E> type, E fallback) {
    final String value = preferences.get(key, "");
    if (!value.isEmpty()) {
      try {
        return Enum.valueOf(type, value);
      } catch (IllegalArgumentException e) {
        Logger.trace(e);
      }
    }
    return fallback;
  }

  private static <E> E valueOrDefault(E value, E fallback) {
    return value != null ? value : fallback;
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static Path normalize(Path path) {
    return path != null ? path.toAbsolutePath().normalize() : null;
  }

  static final class State {
    final Path gameRoot;
    Path outputDirectory;
    Path sourceDirectory;
    CreatureAnimationFamily family = CreatureAnimationFamily.MONSTER;
    BamFormat bamFormat = BamFormat.BAM_V1;
    boolean compressedBam = true;
    boolean splitBams;
    int quadrants = 4;
    int armorLevels = 1;
    boolean canLieDown = true;
    boolean detectedByInfravision = true;
    boolean falseColor;
    boolean pathSmooth = true;
    boolean translucent;
    int moveScale = 9;
    int ellipse = 16;
    int personalSpace = 3;
    int bloodColor = 47;
    int chunkColor = 255;
    Sequence previewSequence = Sequence.WALK;
    int previewDirection;
    boolean previewPlaying = true;
    boolean previewPivot = true;
    int previewFrameRate = AnimationPreviewPanel.DEFAULT_FRAME_RATE;
    int previewZoom = AnimationPreviewPanel.DEFAULT_ZOOM_PERCENT;

    State(Path defaultOutputDirectory, Path defaultSourceDirectory) {
      gameRoot = normalize(defaultSourceDirectory);
      outputDirectory = normalize(defaultOutputDirectory);
      sourceDirectory = gameRoot;
    }
  }
}
