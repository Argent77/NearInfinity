// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Validated, data-driven library of reference-image animation templates.
 *
 * <p>A template defines topology, motion tuning, silhouette fitting and optional equipment sockets. It deliberately
 * contains no creature name, palette, source artwork or default loadout, so templates cannot silently become
 * appearance presets.</p>
 */
public final class CreatureTemplateLibrary {
  private static final String RESOURCE_NAME = "CreatureAnimationTemplates.json";
  private static final int SCHEMA_VERSION = 1;

  public enum Topology {
    BIPED,
    QUADRUPED,
    WINGED,
    ARACHNID,
    HEXAPOD,
    SERPENT,
    CENTAUROID,
    AMORPHOUS,
    FLOATING,
    AQUATIC,
    ROOTED
  }

  public enum MotionProfile {
    HUMANOID,
    HEAVY_HUMANOID,
    GIANT,
    WINGED_HUMANOID,
    AVIAN,
    BAT,
    CANINE,
    FELINE,
    URSINE,
    HOOFED,
    LOW_REPTILE,
    DRACONIC,
    LOW_ABERRATION,
    CENTAUROID,
    ARACHNID,
    SCORPION,
    HEXAPOD,
    BEETLE,
    SERPENT,
    MULTI_NECK,
    AMORPHOUS,
    FLOATING,
    AQUATIC,
    ROOTED
  }

  /** Normalized attachment point in source-art coordinates. */
  public static final class Socket {
    private final double x;
    private final double y;
    private final double angle;

    private Socket(double x, double y, double angle) {
      this.x = x;
      this.y = y;
      this.angle = angle;
    }

    public double getX() {
      return x;
    }

    public double getY() {
      return y;
    }

    public double getAngle() {
      return angle;
    }
  }

  /** Immutable topology and motion definition. */
  public static final class CreatureTemplate {
    private final String id;
    private final String messageKey;
    private final Topology topology;
    private final MotionProfile motionProfile;
    private final double targetWidth;
    private final double targetHeight;
    private final double ground;
    private final double depthScale;
    private final double bobAmplitude;
    private final double strideAmplitude;
    private final double attackReach;
    private final double fallAngle;
    private final double wingAmplitude;
    private final double turnBias;
    private final Socket mainHandSocket;
    private final Socket offHandSocket;

    private CreatureTemplate(String id, String messageKey, Topology topology, MotionProfile motionProfile,
        double targetWidth, double targetHeight, double ground, double depthScale, double bobAmplitude,
        double strideAmplitude, double attackReach, double fallAngle, double wingAmplitude, double turnBias,
        Socket mainHandSocket, Socket offHandSocket) {
      this.id = id;
      this.messageKey = messageKey;
      this.topology = topology;
      this.motionProfile = motionProfile;
      this.targetWidth = targetWidth;
      this.targetHeight = targetHeight;
      this.ground = ground;
      this.depthScale = depthScale;
      this.bobAmplitude = bobAmplitude;
      this.strideAmplitude = strideAmplitude;
      this.attackReach = attackReach;
      this.fallAngle = fallAngle;
      this.wingAmplitude = wingAmplitude;
      this.turnBias = turnBias;
      this.mainHandSocket = mainHandSocket;
      this.offHandSocket = offHandSocket;
    }

    public String getId() {
      return id;
    }

    public String getLabel() {
      return CreatureAnimationMessages.get(messageKey);
    }

    public Topology getTopology() {
      return topology;
    }

    public MotionProfile getMotionProfile() {
      return motionProfile;
    }

    public double getTargetWidth() {
      return targetWidth;
    }

    public double getTargetHeight() {
      return targetHeight;
    }

    public double getGround() {
      return ground;
    }

    public double getDepthScale() {
      return depthScale;
    }

    public double getBobAmplitude() {
      return bobAmplitude;
    }

    public double getStrideAmplitude() {
      return strideAmplitude;
    }

    public double getAttackReach() {
      return attackReach;
    }

    public double getFallAngle() {
      return fallAngle;
    }

    public double getWingAmplitude() {
      return wingAmplitude;
    }

    public double getTurnBias() {
      return turnBias;
    }

    public boolean supportsEquipment() {
      return mainHandSocket != null;
    }

    public Socket getMainHandSocket() {
      return mainHandSocket;
    }

    public Socket getOffHandSocket() {
      return offHandSocket;
    }

    @Override
    public String toString() {
      return getLabel();
    }
  }

  private static final class Holder {
    private static final LibraryData DATA = load();
  }

  private static final class LibraryData {
    private final List<CreatureTemplate> templates;
    private final Map<String, CreatureTemplate> byId;

    private LibraryData(List<CreatureTemplate> templates, Map<String, CreatureTemplate> byId) {
      this.templates = templates;
      this.byId = byId;
    }
  }

  private CreatureTemplateLibrary() {
  }

  public static List<CreatureTemplate> getTemplates() {
    return Holder.DATA.templates;
  }

  public static CreatureTemplate getById(String id) {
    final CreatureTemplate result = Holder.DATA.byId.get(Objects.requireNonNull(id, "id"));
    if (result == null) {
      throw new IllegalArgumentException("Unknown creature animation template: " + id);
    }
    return result;
  }

  private static LibraryData load() {
    try (InputStream stream = CreatureTemplateLibrary.class.getResourceAsStream(RESOURCE_NAME)) {
      if (stream == null) {
        throw new IllegalStateException("Missing bundled creature template library " + RESOURCE_NAME);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        final JSONObject root = new JSONObject(new JSONTokener(reader));
        if (root.getInt("schemaVersion") != SCHEMA_VERSION) {
          throw new IllegalStateException("Unsupported creature template schema version");
        }
        rejectPresetData(root);
        final JSONArray definitions = root.getJSONArray("templates");
        final List<CreatureTemplate> templates = new ArrayList<>(definitions.length());
        final Map<String, CreatureTemplate> byId = new LinkedHashMap<>();
        for (int index = 0; index < definitions.length(); index++) {
          final JSONObject definition = definitions.getJSONObject(index);
          rejectPresetData(definition);
          final CreatureTemplate template = parseTemplate(definition);
          if (byId.put(template.id, template) != null) {
            throw new IllegalStateException("Duplicate creature animation template id: " + template.id);
          }
          templates.add(template);
        }
        if (templates.isEmpty()) {
          throw new IllegalStateException("The creature animation template library is empty");
        }
        return new LibraryData(Collections.unmodifiableList(templates),
            Collections.unmodifiableMap(byId));
      }
    } catch (IOException | RuntimeException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static CreatureTemplate parseTemplate(JSONObject definition) {
    final String id = requireIdentifier(definition.getString("id"), "template");
    final String messageKey = requireText(definition.getString("messageKey"), id + " message key");
    final Topology topology = parseEnum(Topology.class, definition.getString("topology"), id + " topology");
    final MotionProfile motion =
        parseEnum(MotionProfile.class, definition.getString("motionProfile"), id + " motion profile");
    final JSONObject fit = definition.getJSONObject("fit");
    final JSONObject tuning = definition.getJSONObject("motion");
    final Socket mainHand = parseSocket(definition.optJSONArray("mainHandSocket"), id + " main-hand socket");
    final Socket offHand = parseSocket(definition.optJSONArray("offHandSocket"), id + " off-hand socket");
    if (offHand != null && mainHand == null) {
      throw new IllegalStateException(id + " defines an off-hand socket without a main-hand socket");
    }
    return new CreatureTemplate(id, messageKey, topology, motion,
        bounded(fit.getDouble("width"), 0.25, 0.96, id + " fit width"),
        bounded(fit.getDouble("height"), 0.25, 0.96, id + " fit height"),
        bounded(fit.getDouble("ground"), 0.55, 0.98, id + " ground"),
        bounded(fit.getDouble("depthScale"), 0.35, 1.0, id + " depth scale"),
        bounded(tuning.getDouble("bob"), 0.0, 12.0, id + " bob"),
        bounded(tuning.getDouble("stride"), 0.0, 16.0, id + " stride"),
        bounded(tuning.getDouble("attackReach"), 0.0, 24.0, id + " attack reach"),
        bounded(tuning.getDouble("fallAngle"), 0.0, 100.0, id + " fall angle"),
        bounded(tuning.getDouble("wing"), 0.0, 28.0, id + " wing amplitude"),
        bounded(tuning.getDouble("turnBias"), -0.5, 0.5, id + " turn bias"),
        mainHand, offHand);
  }

  private static Socket parseSocket(JSONArray values, String label) {
    if (values == null) {
      return null;
    }
    if (values.length() != 3) {
      throw new IllegalStateException(label + " must contain normalized x, normalized y and angle");
    }
    return new Socket(bounded(values.getDouble(0), 0.0, 1.0, label + " x"),
        bounded(values.getDouble(1), 0.0, 1.0, label + " y"),
        bounded(values.getDouble(2), -180.0, 180.0, label + " angle"));
  }

  private static void rejectPresetData(JSONObject object) {
    final String[] forbidden = { "preset", "presets", "creature", "palette", "sourceImage", "defaultEquipment" };
    for (final String key : forbidden) {
      if (object.has(key)) {
        throw new IllegalStateException("Creature templates must not contain preset field: " + key);
      }
    }
  }

  private static String requireIdentifier(String value, String label) {
    final String normalized = requireText(value, label);
    if (!normalized.matches("[a-z][a-z0-9-]{2,47}")) {
      throw new IllegalStateException("Invalid " + label + " id: " + normalized);
    }
    return normalized;
  }

  private static String requireText(String value, String label) {
    final String normalized = value != null ? value.trim() : "";
    if (normalized.isEmpty()) {
      throw new IllegalStateException("Missing " + label);
    }
    return normalized;
  }

  private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, String label) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid " + label + ": " + value, e);
    }
  }

  private static double bounded(double value, double minimum, double maximum, String label) {
    if (!Double.isFinite(value) || value < minimum || value > maximum) {
      throw new IllegalStateException(label + " must be between " + minimum + " and " + maximum);
    }
    return value;
  }
}
