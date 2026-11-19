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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Validated shared catalogue used by reference-free creatures and decoder-backed equipment overlays. */
public final class CreatureEquipmentLibrary {
  private static final String RESOURCE_NAME = "CreatureEquipmentLibrary.json";
  private static final int SCHEMA_VERSION = 1;

  public enum Slot {
    HAND,
    SHIELD,
    TORSO,
    HEAD,
    BACK
  }

  public enum GripStyle {
    ONE_HANDED,
    TWO_HANDED,
    BOW,
    CROSSBOW,
    SLING,
    SHIELD,
    ARMOR,
    HEADGEAR,
    BACK
  }

  public enum RenderKind {
    SICKLE,
    SCYTHE,
    STRAIGHT_BLADE,
    CURVED_BLADE,
    NINJATO,
    DAGGER,
    AXE,
    MACE,
    HAMMER,
    POLEARM,
    STAFF,
    CLUB,
    FLAIL,
    BOW,
    CROSSBOW,
    SLING,
    WHIP,
    SHIELD,
    TEXTILE_ARMOR,
    LEATHER_ARMOR,
    MAIL_ARMOR,
    SCALE_ARMOR,
    PLATE_ARMOR,
    HOOD,
    HELMET,
    CIRCLET,
    CLOAK,
    QUIVER
  }

  /** Immutable equipment asset metadata; geometry remains rendered by the shared Java2D renderer. */
  public static final class EquipmentAsset {
    private final String id;
    private final String messageKey;
    private final Slot slot;
    private final GripStyle gripStyle;
    private final RenderKind renderKind;
    private final String appearanceCode;
    private final double lengthScale;
    private final double widthScale;
    private final double curvature;
    private final double coverageStart;
    private final double coverageEnd;
    private final double metallic;
    private final double detail;
    private final List<String> aliases;

    private EquipmentAsset(String id, String messageKey, Slot slot, GripStyle gripStyle, RenderKind renderKind,
        String appearanceCode, double lengthScale, double widthScale, double curvature, double coverageStart,
        double coverageEnd, double metallic, double detail, List<String> aliases) {
      this.id = id;
      this.messageKey = messageKey;
      this.slot = slot;
      this.gripStyle = gripStyle;
      this.renderKind = renderKind;
      this.appearanceCode = appearanceCode;
      this.lengthScale = lengthScale;
      this.widthScale = widthScale;
      this.curvature = curvature;
      this.coverageStart = coverageStart;
      this.coverageEnd = coverageEnd;
      this.metallic = metallic;
      this.detail = detail;
      this.aliases = aliases;
    }

    public String getId() {
      return id;
    }

    public String getLabel() {
      return CreatureAnimationMessages.get(messageKey);
    }

    public Slot getSlot() {
      return slot;
    }

    public GripStyle getGripStyle() {
      return gripStyle;
    }

    public RenderKind getRenderKind() {
      return renderKind;
    }

    public String getAppearanceCode() {
      return appearanceCode;
    }

    public double getLengthScale() {
      return lengthScale;
    }

    public double getWidthScale() {
      return widthScale;
    }

    public double getCurvature() {
      return curvature;
    }

    public double getCoverageStart() {
      return coverageStart;
    }

    public double getCoverageEnd() {
      return coverageEnd;
    }

    public double getMetallic() {
      return metallic;
    }

    public double getDetail() {
      return detail;
    }

    public List<String> getAliases() {
      return aliases;
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
    private final List<EquipmentAsset> assets;
    private final Map<String, EquipmentAsset> byId;
    private final Map<Slot, List<EquipmentAsset>> bySlot;

    private LibraryData(List<EquipmentAsset> assets, Map<String, EquipmentAsset> byId,
        Map<Slot, List<EquipmentAsset>> bySlot) {
      this.assets = assets;
      this.byId = byId;
      this.bySlot = bySlot;
    }
  }

  private CreatureEquipmentLibrary() {
  }

  public static List<EquipmentAsset> getAssets() {
    return Holder.DATA.assets;
  }

  public static List<EquipmentAsset> getAssets(Slot slot) {
    final List<EquipmentAsset> result = Holder.DATA.bySlot.get(Objects.requireNonNull(slot, "slot"));
    return result != null ? result : Collections.<EquipmentAsset>emptyList();
  }

  public static EquipmentAsset getById(String id) {
    final EquipmentAsset result = Holder.DATA.byId.get(Objects.requireNonNull(id, "id"));
    if (result == null) {
      throw new IllegalArgumentException("Unknown shared equipment asset: " + id);
    }
    return result;
  }

  private static LibraryData load() {
    try (InputStream stream = CreatureEquipmentLibrary.class.getResourceAsStream(RESOURCE_NAME)) {
      if (stream == null) {
        throw new IllegalStateException("Missing bundled equipment library " + RESOURCE_NAME);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        final JSONObject root = new JSONObject(new JSONTokener(reader));
        if (root.getInt("schemaVersion") != SCHEMA_VERSION) {
          throw new IllegalStateException("Unsupported shared equipment schema version");
        }
        rejectPresetData(root);
        final JSONArray definitions = root.getJSONArray("assets");
        final List<EquipmentAsset> assets = new ArrayList<>(definitions.length());
        final Map<String, EquipmentAsset> byId = new LinkedHashMap<>();
        final Map<Slot, List<EquipmentAsset>> mutableBySlot = new EnumMap<>(Slot.class);
        for (final Slot slot : Slot.values()) {
          mutableBySlot.put(slot, new ArrayList<EquipmentAsset>());
        }
        for (int index = 0; index < definitions.length(); index++) {
          final JSONObject definition = definitions.getJSONObject(index);
          rejectPresetData(definition);
          final EquipmentAsset asset = parseAsset(definition);
          if (byId.put(asset.id, asset) != null) {
            throw new IllegalStateException("Duplicate shared equipment id: " + asset.id);
          }
          assets.add(asset);
          mutableBySlot.get(asset.slot).add(asset);
        }
        if (assets.isEmpty()) {
          throw new IllegalStateException("The shared equipment library is empty");
        }
        final Map<Slot, List<EquipmentAsset>> bySlot = new EnumMap<>(Slot.class);
        for (final Map.Entry<Slot, List<EquipmentAsset>> entry : mutableBySlot.entrySet()) {
          bySlot.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return new LibraryData(Collections.unmodifiableList(assets), Collections.unmodifiableMap(byId),
            Collections.unmodifiableMap(bySlot));
      }
    } catch (IOException | RuntimeException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static EquipmentAsset parseAsset(JSONObject definition) {
    final String id = requireIdentifier(definition.getString("id"), "equipment");
    final String messageKey = requireText(definition.getString("messageKey"), id + " message key");
    final Slot slot = parseEnum(Slot.class, definition.getString("slot"), id + " slot");
    final GripStyle gripStyle =
        parseEnum(GripStyle.class, definition.getString("gripStyle"), id + " grip style");
    final RenderKind renderKind =
        parseEnum(RenderKind.class, definition.getString("renderKind"), id + " render kind");
    final String appearanceCode = definition.optString("appearanceCode", "");
    if ((slot == Slot.HAND || slot == Slot.SHIELD) && !appearanceCode.matches("[A-Z0-9]{2}")) {
      throw new IllegalStateException(id + " requires a two-character appearance code");
    }
    if (slot != Slot.HAND && slot != Slot.SHIELD && !appearanceCode.isEmpty()) {
      throw new IllegalStateException(id + " is not an engine hand overlay and must not define an appearance code");
    }
    final JSONArray coverage = definition.optJSONArray("coverage");
    final double coverageStart;
    final double coverageEnd;
    if (coverage != null) {
      if (coverage.length() != 2) {
        throw new IllegalStateException(id + " coverage must contain start and end");
      }
      coverageStart = bounded(coverage.getDouble(0), 0.0, 1.0, id + " coverage start");
      coverageEnd = bounded(coverage.getDouble(1), 0.0, 1.0, id + " coverage end");
      if (coverageStart >= coverageEnd) {
        throw new IllegalStateException(id + " coverage start must precede its end");
      }
    } else {
      coverageStart = 0.0;
      coverageEnd = 1.0;
    }
    final JSONArray aliasValues = definition.optJSONArray("aliases");
    final List<String> aliases = new ArrayList<>();
    if (aliasValues != null) {
      for (int index = 0; index < aliasValues.length(); index++) {
        final String alias = requireText(aliasValues.getString(index), id + " alias").toLowerCase(java.util.Locale.ENGLISH);
        if (!aliases.contains(alias)) {
          aliases.add(alias);
        }
      }
    }
    return new EquipmentAsset(id, messageKey, slot, gripStyle, renderKind, appearanceCode,
        bounded(definition.optDouble("lengthScale", 1.0), 0.35, 1.8, id + " length scale"),
        bounded(definition.optDouble("widthScale", 1.0), 0.35, 2.0, id + " width scale"),
        bounded(definition.optDouble("curvature", 0.0), -0.5, 0.5, id + " curvature"),
        coverageStart, coverageEnd,
        bounded(definition.optDouble("metallic", 0.0), 0.0, 1.0, id + " metallic value"),
        bounded(definition.optDouble("detail", 0.5), 0.0, 1.0, id + " detail value"),
        Collections.unmodifiableList(aliases));
  }

  private static void rejectPresetData(JSONObject object) {
    final String[] forbidden = { "preset", "presets", "creature", "defaultColor", "defaultLoadout" };
    for (final String key : forbidden) {
      if (object.has(key)) {
        throw new IllegalStateException("Shared equipment assets must not contain preset field: " + key);
      }
    }
  }

  private static String requireIdentifier(String value, String label) {
    final String normalized = requireText(value, label);
    if (!normalized.matches("[a-z][a-z0-9-]{1,47}")) {
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
