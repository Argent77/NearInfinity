// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.ProgressListener;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.PromptSpec;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.WeaponType;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.decoder.MonsterDecoder;
import org.infinity.resource.cre.decoder.SpriteDecoder;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;

/** Resolves and loads an active-game type 0x7000 reference for equipment-overlay generation. */
public final class EquipmentOverlayReference {
  private static final Pattern HEX_ID = Pattern.compile("(?i)\\b0x([0-9a-f]{1,4})\\b");
  private static final Pattern APPEARANCE_CODE = Pattern.compile("(?i)^[A-Z0-9_]{2}$");

  public static final class Result {
    private final int animationId;
    private final String symbol;
    private final String animationResref;
    private final String resref;
    private final boolean splitBams;
    private final String sourceAppearanceCode;
    private final String targetAppearanceCode;
    private final List<String> availableAppearanceCodes;
    private final PromptSpec prompt;
    private final CreatureAnimationModel avatarModel;
    private final CreatureAnimationModel overlayModel;

    private Result(int animationId, String symbol, String animationResref, String resref, boolean splitBams,
        String sourceAppearanceCode, String targetAppearanceCode, List<String> availableAppearanceCodes,
        PromptSpec prompt, CreatureAnimationModel avatarModel, CreatureAnimationModel overlayModel) {
      this.animationId = animationId;
      this.symbol = symbol;
      this.animationResref = animationResref;
      this.resref = resref;
      this.splitBams = splitBams;
      this.sourceAppearanceCode = sourceAppearanceCode;
      this.targetAppearanceCode = targetAppearanceCode;
      this.availableAppearanceCodes =
          Collections.unmodifiableList(new ArrayList<>(availableAppearanceCodes));
      this.prompt = prompt;
      this.avatarModel = avatarModel;
      this.overlayModel = overlayModel;
    }

    public int getAnimationId() {
      return animationId;
    }

    public String getSymbol() {
      return symbol;
    }

    public String getResref() {
      return resref;
    }

    public String getAnimationResref() {
      return animationResref;
    }

    public boolean isSplitBams() {
      return splitBams;
    }

    public String getSourceAppearanceCode() {
      return sourceAppearanceCode;
    }

    public String getTargetAppearanceCode() {
      return targetAppearanceCode;
    }

    public List<String> getAvailableAppearanceCodes() {
      return availableAppearanceCodes;
    }

    public PromptSpec getPrompt() {
      return prompt;
    }

    public CreatureAnimationModel getAvatarModel() {
      return avatarModel;
    }

    public CreatureAnimationModel getOverlayModel() {
      return overlayModel;
    }

    public String getSummary() {
      final String resrefSummary =
          animationResref.equals(resref) ? resref : animationResref + "\u2192" + resref;
      return symbol + " (" + String.format(Locale.ENGLISH, "0x%04X", animationId) + ", " + resrefSummary + ") • "
          + sourceAppearanceCode + " → " + targetAppearanceCode + " • " + prompt.getSummary();
    }
  }

  private EquipmentOverlayReference() {
  }

  /**
   * Resolves the reference animation named in the prompt, discovers an existing equipment layer and generates its
   * replacement.
   *
   * @param sourceCodeOverride two-character source appearance code or {@code AUTO}
   * @param targetCodeOverride two-character target appearance code or {@code AUTO}
   */
  public static Result generate(String promptText, String sourceCodeOverride, String targetCodeOverride, long seed,
      ProgressListener listener) throws Exception {
    if (!MonsterAnimationLayout.isSupportedGame(Profile.getGame())) {
      throw new IllegalArgumentException("Equipment overlays are available only for supported Enhanced Edition games.");
    }
    final PromptSpec prompt = EquipmentOverlayGenerator.parsePrompt(promptText);
    final AnimationReference reference = resolveAnimation(promptText);
    final SpriteDecoder decoder = SpriteDecoder.importSprite(reference.animationId);
    if (decoder == null) {
      throw new IllegalArgumentException("Could not load the animation definition for " + reference.symbol + " ("
          + String.format(Locale.ENGLISH, "0x%04X", reference.animationId) + ").");
    }
    if (!(decoder instanceof MonsterDecoder)) {
      throw new IllegalArgumentException(reference.symbol + " uses " + decoder.getAnimationType()
          + ", but synchronized G1/G2 equipment overlays require animation family 0x7000 (monster).");
    }
    final MonsterDecoder monster = (MonsterDecoder) decoder;
    final String animationResref = monster.getAnimationResref().trim().toUpperCase(Locale.ENGLISH);
    final EquipmentSource equipmentSource =
        resolveEquipmentSource(animationResref, prompt.getSourceWeapon(), sourceCodeOverride);
    final String resref = equipmentSource.resref;
    final List<String> availableCodes = equipmentSource.appearanceCodes;

    final String sourceCode = chooseSourceCode(availableCodes, prompt.getSourceWeapon(), sourceCodeOverride);
    final String targetCode = chooseTargetCode(resref, sourceCode, prompt.getTargetWeapon(), targetCodeOverride);
    final CreatureAnimationModel avatar = MonsterAnimationBamImporter.importAnimation(resref,
        monster.isSplittedBams(), ResourceFactory::getResourceEntry);
    final CreatureAnimationModel sourceOverlay =
        MonsterAnimationBamImporter.importEquipmentOverlay(resref, sourceCode, ResourceFactory::getResourceEntry);
    final CreatureAnimationModel generated =
        EquipmentOverlayGenerator.generate(sourceOverlay, avatar, prompt, seed, listener);
    return new Result(reference.animationId, reference.symbol, animationResref, resref, monster.isSplittedBams(),
        sourceCode, targetCode, availableCodes, prompt, avatar, generated);
  }

  private static AnimationReference resolveAnimation(String promptText) {
    final IdsMap animate = IdsMapCache.get("ANIMATE.IDS");
    if (animate == null) {
      throw new IllegalArgumentException("ANIMATE.IDS is unavailable in the active game.");
    }
    final String normalizedPrompt = normalizeWords(promptText);
    AnimationReference best = null;
    int bestLength = -1;
    for (final IdsMapEntry entry : animate.getAllValues()) {
      for (final String symbol : entry) {
        final String normalizedSymbol = normalizeWords(symbol).trim();
        if (!normalizedSymbol.isEmpty() && normalizedPrompt.contains(" " + normalizedSymbol + " ")
            && normalizedSymbol.length() > bestLength) {
          best = new AnimationReference((int) entry.getID() & 0xffff, symbol);
          bestLength = normalizedSymbol.length();
        }
      }
    }
    if (best != null) {
      return best;
    }

    final Matcher matcher = HEX_ID.matcher(promptText != null ? promptText : "");
    if (matcher.find()) {
      final int animationId = Integer.parseInt(matcher.group(1), 16);
      final IdsMapEntry entry = animate.get(animationId);
      final String symbol = entry != null ? entry.getFirstSymbol()
          : String.format(Locale.ENGLISH, "animation 0x%04X", animationId);
      return new AnimationReference(animationId, symbol);
    }
    throw new IllegalArgumentException("Name a reference from ANIMATE.IDS (for example SOLAR) or include its "
        + "hexadecimal animation id.");
  }

  private static EquipmentSource resolveEquipmentSource(String animationResref, WeaponType sourceType,
      String sourceCodeOverride) throws IOException {
    final String familyPattern = animationResref.length() == 4
        ? Pattern.quote(animationResref.substring(0, 3)) + "[A-Z0-9_]"
        : Pattern.quote(animationResref);
    final Pattern resourcePattern =
        Pattern.compile("^" + familyPattern + "G[12](?:[A-Z0-9_]{2})?\\.BAM$", Pattern.CASE_INSENSITIVE);
    final List<ResourceEntry> entries = ResourceFactory.getResources(resourcePattern);
    final List<String> resourceNames = new ArrayList<>();
    if (entries != null) {
      for (final ResourceEntry entry : entries) {
        resourceNames.add(entry.getResourceName());
      }
    }

    final List<String> compatibleResrefs =
        findCompatibleEquipmentResrefs(animationResref, resourceNames);
    if (compatibleResrefs.isEmpty()) {
      throw new IOException(animationResref + " has no complete G1/G2 equipment overlay pair to use as a pose and "
          + "grip reference. The reference also has no compatible solid layer with the same three-character prefix.");
    }

    final List<EquipmentSource> sources = new ArrayList<>();
    for (final String resref : compatibleResrefs) {
      sources.add(new EquipmentSource(resref, findCompleteAppearanceCodes(resref, resourceNames)));
    }
    if (sources.get(0).resref.equals(animationResref) || sources.size() == 1) {
      return sources.get(0);
    }

    final String requestedCode = normalizeOverride(sourceCodeOverride);
    if (requestedCode != null) {
      final List<EquipmentSource> matches = new ArrayList<>();
      for (final EquipmentSource source : sources) {
        if (source.appearanceCodes.contains(requestedCode)) {
          matches.add(source);
        }
      }
      if (matches.size() == 1) {
        return matches.get(0);
      }
    }

    if (sourceType != null) {
      final String suggestedCode = sourceType.getSuggestedAppearanceCode();
      final List<EquipmentSource> matches = new ArrayList<>();
      for (final EquipmentSource source : sources) {
        if (source.appearanceCodes.contains(suggestedCode)) {
          matches.add(source);
        }
      }
      if (matches.size() == 1) {
        return matches.get(0);
      }
    }

    if (animationResref.length() == 4 && animationResref.endsWith("G")) {
      EquipmentSource solidLayer = null;
      for (final EquipmentSource source : sources) {
        if (source.resref.endsWith("L")) {
          if (solidLayer != null) {
            solidLayer = null;
            break;
          }
          solidLayer = source;
        }
      }
      if (solidLayer != null) {
        return solidLayer;
      }
    }

    final List<String> descriptions = new ArrayList<>();
    for (final EquipmentSource source : sources) {
      descriptions.add(source.resref + " (" + String.join(", ", source.appearanceCodes) + ")");
    }
    throw new IOException("Several compatible equipment layers exist for " + animationResref + ": "
        + String.join("; ", descriptions) + ". Enter an explicit source appearance code to disambiguate them.");
  }

  /**
   * Finds resrefs that provide both base G1/G2 BAMs and at least one complete unsplit G1/G2 equipment pair.
   *
   * <p>The three-character-family fallback handles hardcoded layered animations such as BG2-family SOLAR, whose
   * animation table points at the MSOG glow layer while the solid avatar and equipment resources use MSOL.</p>
   */
  static List<String> findCompatibleEquipmentResrefs(String animationResref, Iterable<String> resourceNames) {
    final String normalizedResref =
        animationResref != null ? animationResref.trim().toUpperCase(Locale.ENGLISH) : "";
    if (!normalizedResref.matches("[A-Z0-9_]{1,4}")) {
      throw new IllegalArgumentException("Animation resrefs must contain 1-4 ASCII letters, digits or underscores.");
    }

    final Set<String> names = new HashSet<>();
    if (resourceNames != null) {
      for (final String name : resourceNames) {
        if (name != null) {
          names.add(name.trim().toUpperCase(Locale.ENGLISH));
        }
      }
    }

    final Pattern overlayPattern =
        Pattern.compile("^([A-Z0-9_]{1,4})G1([A-Z0-9_]{2})\\.BAM$", Pattern.CASE_INSENSITIVE);
    final Set<String> candidates = new TreeSet<>();
    for (final String name : names) {
      final Matcher matcher = overlayPattern.matcher(name);
      if (!matcher.matches()) {
        continue;
      }
      final String candidate = matcher.group(1).toUpperCase(Locale.ENGLISH);
      final boolean sameResref = candidate.equals(normalizedResref);
      final boolean sameFamily = normalizedResref.length() == 4 && candidate.length() == 4
          && candidate.regionMatches(true, 0, normalizedResref, 0, 3);
      if (sameResref || sameFamily) {
        candidates.add(candidate);
      }
    }

    final List<String> result = new ArrayList<>();
    for (final String candidate : candidates) {
      if (names.contains(candidate + "G1.BAM") && names.contains(candidate + "G2.BAM")
          && !findCompleteAppearanceCodes(candidate, names).isEmpty()) {
        result.add(candidate);
      }
    }
    if (result.remove(normalizedResref)) {
      result.add(0, normalizedResref);
    }
    return result;
  }

  private static List<String> findCompleteAppearanceCodes(String resref, Iterable<String> resourceNames) {
    final Set<String> names = new HashSet<>();
    if (resourceNames != null) {
      for (final String name : resourceNames) {
        if (name != null) {
          names.add(name.trim().toUpperCase(Locale.ENGLISH));
        }
      }
    }
    final Pattern pattern = Pattern.compile("^" + Pattern.quote(resref) + "G1([A-Z0-9_]{2})\\.BAM$",
        Pattern.CASE_INSENSITIVE);
    final Set<String> result = new TreeSet<>();
    for (final String name : names) {
      final Matcher matcher = pattern.matcher(name);
      if (matcher.matches()) {
        final String code = matcher.group(1).toUpperCase(Locale.ENGLISH);
        if (names.contains(resref + "G2" + code + ".BAM")) {
          result.add(code);
        }
      }
    }
    return new ArrayList<>(result);
  }

  private static String chooseSourceCode(List<String> available, WeaponType sourceType, String override) {
    final String requested = normalizeOverride(override);
    if (requested != null) {
      if (!available.contains(requested)) {
        throw new IllegalArgumentException("Source appearance " + requested + " is unavailable. Complete reference "
            + "pairs: " + String.join(", ", available) + ".");
      }
      return requested;
    }

    final Map<String, String> descriptions = Profile.getEquippedAppearanceMap();
    if (sourceType != null) {
      final List<CodeScore> scored = new ArrayList<>();
      for (final String code : available) {
        int score = code.equals(sourceType.getSuggestedAppearanceCode()) ? 100 : 0;
        final String description = descriptions.get(code);
        final String normalizedDescription = normalizeWords(description);
        for (final String alias : sourceType.getAliases()) {
          if (normalizedDescription.contains(" " + normalizeWords(alias).trim() + " ")) {
            score += 20 + alias.length();
          }
        }
        if (score > 0) {
          scored.add(new CodeScore(code, score));
        }
      }
      if (!scored.isEmpty()) {
        scored.sort(Comparator.comparingInt((CodeScore value) -> value.score).reversed()
            .thenComparing(value -> value.code));
        return scored.get(0).code;
      }
    }
    if (available.contains("S1")) {
      return "S1";
    }
    if (available.size() == 1) {
      return available.get(0);
    }
    throw new IllegalArgumentException("The reference has several equipment layers (" + String.join(", ", available)
        + "). Name the original weapon in the prompt or enter its source appearance code.");
  }

  private static String chooseTargetCode(String resref, String sourceCode, WeaponType targetType, String override) {
    final String requested = normalizeOverride(override);
    if (requested != null) {
      if (requested.equals(sourceCode)) {
        throw new IllegalArgumentException("The target appearance code must differ from the source layer "
            + sourceCode + ".");
      }
      return requested;
    }

    final String suggested = targetType.getSuggestedAppearanceCode();
    if (!suggested.equals(sourceCode) && !overlayExists(resref, suggested)) {
      return suggested;
    }
    final char prefix = Character.toUpperCase(targetType.getLabel().charAt(0));
    for (char suffix = '0'; suffix <= '9'; suffix++) {
      final String candidate = new String(new char[] { prefix, suffix });
      if (!candidate.equals(sourceCode) && !overlayExists(resref, candidate)) {
        return candidate;
      }
    }
    for (char first = 'A'; first <= 'Z'; first++) {
      for (char second = 'A'; second <= 'Z'; second++) {
        final String candidate = new String(new char[] { first, second });
        if (!candidate.equals(sourceCode) && !overlayExists(resref, candidate)) {
          return candidate;
        }
      }
    }
    throw new IllegalArgumentException("No free two-character appearance code could be selected automatically.");
  }

  private static boolean overlayExists(String resref, String code) {
    return ResourceFactory.resourceExists(resref + "G1" + code + ".BAM")
        || ResourceFactory.resourceExists(resref + "G2" + code + ".BAM");
  }

  private static String normalizeOverride(String value) {
    final String normalized = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
    if (normalized.isEmpty() || "AUTO".equals(normalized)) {
      return null;
    }
    if (!APPEARANCE_CODE.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Appearance overrides must be AUTO or exactly two ASCII characters.");
    }
    return normalized;
  }

  private static String normalizeWords(String value) {
    final String text = value != null ? value.toLowerCase(Locale.ENGLISH) : "";
    return " " + text.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ") + " ";
  }

  private static final class AnimationReference {
    private final int animationId;
    private final String symbol;

    private AnimationReference(int animationId, String symbol) {
      this.animationId = animationId;
      this.symbol = symbol;
    }
  }

  private static final class CodeScore {
    private final String code;
    private final int score;

    private CodeScore(String code, int score) {
      this.code = code;
      this.score = score;
    }
  }

  private static final class EquipmentSource {
    private final String resref;
    private final List<String> appearanceCodes;

    private EquipmentSource(String resref, List<String> appearanceCodes) {
      this.resref = resref;
      this.appearanceCodes = Collections.unmodifiableList(new ArrayList<>(appearanceCodes));
    }
  }
}
