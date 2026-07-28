// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.infinity.gui.converter.creature.CreatureAnimationFamily.FamilyLayout;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.ProgressListener;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.PromptSpec;
import org.infinity.gui.converter.creature.EquipmentOverlayGenerator.WeaponType;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.decoder.SpriteDecoder;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;

/** Resolves and loads an active-game reference whose decoder supports weapon overlays. */
public final class EquipmentOverlayReference {
  private static final Pattern HEX_ID = Pattern.compile("(?i)\\b0x([0-9a-f]{1,4})\\b");
  private static final Pattern APPEARANCE_CODE = Pattern.compile("(?i)^[A-Z0-9_]{2}$");
  private static final Pattern SOURCE_LAYER_CODE = Pattern.compile("(?i)^[A-Z0-9_]{1,2}$");

  public static final class Result {
    private final int animationId;
    private final String symbol;
    private final String animationResref;
    private final String resourcePrefix;
    private final boolean splitBams;
    private final EquipmentOverlayFamily family;
    private final String sourceAppearanceCode;
    private final String targetAppearanceCode;
    private final List<String> availableAppearanceCodes;
    private final PromptSpec prompt;
    private final EquipmentOverlayModel avatar;
    private final EquipmentOverlayModel overlay;

    private Result(int animationId, String symbol, String animationResref, String resourcePrefix, boolean splitBams,
        EquipmentOverlayFamily family, String sourceAppearanceCode, String targetAppearanceCode,
        List<String> availableAppearanceCodes, PromptSpec prompt, EquipmentOverlayModel avatar,
        EquipmentOverlayModel overlay) {
      this.animationId = animationId;
      this.symbol = symbol;
      this.animationResref = animationResref;
      this.resourcePrefix = resourcePrefix;
      this.splitBams = splitBams;
      this.family = family;
      this.sourceAppearanceCode = sourceAppearanceCode;
      this.targetAppearanceCode = targetAppearanceCode;
      this.availableAppearanceCodes =
          Collections.unmodifiableList(new ArrayList<>(availableAppearanceCodes));
      this.prompt = prompt;
      this.avatar = avatar;
      this.overlay = overlay;
    }

    public int getAnimationId() {
      return animationId;
    }

    public String getSymbol() {
      return symbol;
    }

    /** Returns the family-specific weapon BAM prefix. */
    public String getResref() {
      return resourcePrefix;
    }

    public String getResourcePrefix() {
      return resourcePrefix;
    }

    public String getAnimationResref() {
      return animationResref;
    }

    public boolean isSplitBams() {
      return splitBams;
    }

    public EquipmentOverlayFamily getFamily() {
      return family;
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

    public EquipmentOverlayModel getAvatarAnimation() {
      return avatar;
    }

    public EquipmentOverlayModel getOverlayAnimation() {
      return overlay;
    }

    public CreatureAnimationModel getAvatarModel() {
      return avatar.getWesternModel();
    }

    public CreatureAnimationModel getAvatarEasternModel() {
      return avatar.getEasternModel();
    }

    public CreatureAnimationModel getOverlayModel() {
      return overlay.getWesternModel();
    }

    public CreatureAnimationModel getOverlayEasternModel() {
      return overlay.getEasternModel();
    }

    public String getSummary() {
      final String resrefSummary =
          animationResref.equals(resourcePrefix) ? resourcePrefix : animationResref + "\u2192" + resourcePrefix;
      return symbol + " (" + String.format(Locale.ENGLISH, "0x%04X", animationId) + ", " + family + ", "
          + resrefSummary + ") • " + sourceAppearanceCode + " → " + targetAppearanceCode + " • "
          + prompt.getSummary();
    }
  }

  private EquipmentOverlayReference() {
  }

  /**
   * Resolves the reference animation named in the prompt, discovers an existing synchronized weapon layer and
   * generates its replacement.
   *
   * @param sourceCodeOverride one- or two-character source layer code, or {@code AUTO}
   * @param targetCodeOverride two-character Equipped appearance code, or {@code AUTO}
   */
  public static Result generate(String promptText, String sourceCodeOverride, String targetCodeOverride, long seed,
      ProgressListener listener) throws Exception {
    if (!MonsterAnimationLayout.isSupportedGame(Profile.getGame())) {
      throw new IllegalArgumentException("Equipment overlays require a recognized Infinity Engine game profile.");
    }
    final PromptSpec prompt = EquipmentOverlayGenerator.parsePrompt(promptText);
    final AnimationReference reference = resolveAnimation(promptText);
    final SpriteDecoder decoder = Profile.isEnhancedEdition()
        ? SpriteDecoder.importSprite(reference.animationId)
        : ClassicAnimationDefinition.resolveDecoder(Profile.getGame(), reference.animationId);
    if (decoder == null) {
      throw new IllegalArgumentException("Could not load the animation definition for " + reference.symbol + " ("
          + String.format(Locale.ENGLISH, "0x%04X", reference.animationId) + ").");
    }
    try {
      final EquipmentOverlayFamily family = EquipmentOverlayFamily.forDecoder(decoder);
      if (family == null) {
        throw new IllegalArgumentException(reference.symbol + " uses " + decoder.getAnimationType()
            + ", whose Near Infinity decoder does not define weapon sprite overlays.");
      }
      family.validateDecoder(decoder, prompt.getTargetWeapon());

      final String rawAnimationResref = decoder.getAnimationResref();
      final String animationResref =
          rawAnimationResref != null ? rawAnimationResref.trim().toUpperCase(Locale.ENGLISH) : "";
      if (animationResref.isEmpty()) {
        throw new IllegalArgumentException(reference.symbol + " does not define an animation resource prefix.");
      }
      final List<String> resourceNames = listRelevantResourceNames(family, decoder, animationResref);
      final String resourcePrefix = family == EquipmentOverlayFamily.MONSTER
          ? resolveMonsterEquipmentPrefix(animationResref, prompt.getSourceWeapon(), sourceCodeOverride, resourceNames)
          : family.getOverlayResourcePrefix(decoder, null);
      final List<String> availableCodes =
          findAvailableSourceCodes(family, decoder, resourcePrefix, prompt.getTargetWeapon(), resourceNames);
      if (availableCodes.isEmpty()) {
        throw new IOException(reference.symbol + " has no complete " + family
            + " weapon layer compatible with the requested " + prompt.getTargetWeapon().getLabel() + " pose.");
      }

      final String sourceCode = chooseSourceCode(family, availableCodes, prompt.getSourceWeapon(),
          prompt.getTargetWeapon(), sourceCodeOverride);
      final String targetCode = chooseTargetCode(family, decoder, resourcePrefix, sourceCode,
          prompt.getTargetWeapon(), targetCodeOverride);
      final String sourceLayoutCode = toLayoutAppearanceCode(sourceCode);
      final FamilyLayout sourceLayout =
          family.createOverlayLayout(resourcePrefix, sourceLayoutCode, prompt.getTargetWeapon());
      final EquipmentOverlayModel sourceOverlay = EquipmentOverlayBamImporter.importLayout(sourceLayout,
          ResourceFactory::getResourceEntry, true, family);
      final EquipmentOverlayModel avatar =
          importAvatar(family, decoder, resourcePrefix, prompt.getTargetWeapon());
      final EquipmentOverlayModel generated =
          EquipmentOverlayGenerator.generate(sourceOverlay, avatar, prompt, seed,
              family.hasExplicitEasternResources(), listener);
      return new Result(reference.animationId, reference.symbol, animationResref, resourcePrefix,
          family.isAvatarSplit(decoder), family, sourceCode, targetCode, availableCodes, prompt, avatar, generated);
    } finally {
      decoder.close();
    }
  }

  private static EquipmentOverlayModel importAvatar(EquipmentOverlayFamily family, SpriteDecoder decoder,
      String resourcePrefix, WeaponType targetWeapon) throws Exception {
    final List<String> prefixes = family.getAvatarResourcePrefixes(decoder, resourcePrefix);
    if (family == EquipmentOverlayFamily.CHARACTER && !prefixes.isEmpty()) {
      final String primary = prefixes.get(0);
      final FamilyLayout layout = family.createAvatarLayout(primary, family.isAvatarSplit(decoder), targetWeapon);
      try {
        return EquipmentOverlayBamImporter.importLayout(layout, resourceName -> {
          final String suffix = resourceName.substring(primary.length());
          for (final String prefix : prefixes) {
            final ResourceEntry entry = ResourceFactory.getResourceEntry(prefix + suffix);
            if (entry != null) {
              return entry;
            }
          }
          return null;
        }, true, family);
      } catch (Exception e) {
        throw new IOException("No complete avatar layer could be assembled through the selected character's "
            + "armor-specific, armor-base and generic resource fallbacks: " + e.getMessage(), e);
      }
    }

    final List<String> failures = new ArrayList<>();
    for (final String prefix : prefixes) {
      final FamilyLayout layout = family.createAvatarLayout(prefix, family.isAvatarSplit(decoder), targetWeapon);
      try {
        return EquipmentOverlayBamImporter.importLayout(layout, ResourceFactory::getResourceEntry, true, family);
      } catch (Exception e) {
        failures.add(prefix + ": " + e.getMessage());
      }
    }
    throw new IOException("No complete avatar layer could be paired with the selected weapon resources"
        + (failures.isEmpty() ? "." : ": " + String.join("; ", failures)));
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

  private static List<String> listRelevantResourceNames(EquipmentOverlayFamily family, SpriteDecoder decoder,
      String animationResref) {
    final String prefix;
    final String expression;
    if (family == EquipmentOverlayFamily.MONSTER) {
      prefix = animationResref.length() == 4
          ? Pattern.quote(animationResref.substring(0, 3)) + "[A-Z0-9_]" : Pattern.quote(animationResref);
      expression = "^" + prefix + "G[12](?:[A-Z0-9_]{2})?\\.BAM$";
    } else {
      prefix = Pattern.quote(family.getOverlayResourcePrefix(decoder, null));
      expression = "^" + prefix + "[A-Z0-9_]{0,5}\\.BAM$";
    }
    final List<ResourceEntry> entries =
        ResourceFactory.getResources(Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
    final List<String> result = new ArrayList<>();
    if (entries != null) {
      for (final ResourceEntry entry : entries) {
        result.add(entry.getResourceName().toUpperCase(Locale.ENGLISH));
      }
    }
    return result;
  }

  private static String resolveMonsterEquipmentPrefix(String animationResref, WeaponType sourceType,
      String sourceCodeOverride, List<String> resourceNames) throws IOException {
    final List<String> compatibleResrefs =
        findCompatibleEquipmentResrefs(animationResref, resourceNames);
    if (compatibleResrefs.isEmpty()) {
      throw new IOException(animationResref + " has no complete G1/G2 equipment-overlay pair and no compatible "
          + "solid layer with the same three-character prefix.");
    }

    final List<EquipmentSource> sources = new ArrayList<>();
    for (final String resref : compatibleResrefs) {
      sources.add(new EquipmentSource(resref, findCompleteMonsterAppearanceCodes(resref, resourceNames)));
    }
    if (sources.get(0).resref.equals(animationResref) || sources.size() == 1) {
      return sources.get(0).resref;
    }

    final String requestedCode = normalizeSourceOverride(sourceCodeOverride,
        EquipmentOverlayFamily.MONSTER);
    if (requestedCode != null) {
      final List<EquipmentSource> matches = new ArrayList<>();
      for (final EquipmentSource source : sources) {
        if (source.appearanceCodes.contains(requestedCode)) {
          matches.add(source);
        }
      }
      if (matches.size() == 1) {
        return matches.get(0).resref;
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
        return matches.get(0).resref;
      }
    }

    final List<String> descriptions = new ArrayList<>();
    for (final EquipmentSource source : sources) {
      descriptions.add(source.resref + " (" + String.join(", ", source.appearanceCodes) + ")");
    }
    throw new IOException("Several compatible equipment layers exist for " + animationResref + ": "
        + String.join("; ", descriptions) + ". Enter an explicit source layer code to disambiguate them.");
  }

  private static List<String> findAvailableSourceCodes(EquipmentOverlayFamily family, SpriteDecoder decoder,
      String resourcePrefix, WeaponType targetWeapon, Iterable<String> resourceNames) {
    final Set<String> candidates = new TreeSet<>();
    if (family == EquipmentOverlayFamily.MONSTER_LAYERED
        || family == EquipmentOverlayFamily.MONSTER_LAYERED_SPELL) {
      final String required = family.getRequiredSourceLayerCode(decoder, targetWeapon);
      if (!required.isEmpty()) {
        candidates.add(required);
      }
    } else {
      final Pattern pattern;
      if (family == EquipmentOverlayFamily.MONSTER) {
        pattern = Pattern.compile("^" + Pattern.quote(resourcePrefix) + "G1([A-Z0-9_]{2})\\.BAM$",
            Pattern.CASE_INSENSITIVE);
      } else if (family == EquipmentOverlayFamily.MONSTER_ICEWIND) {
        pattern = Pattern.compile("^" + Pattern.quote(resourcePrefix) + "([A-Z0-9_])WK\\.BAM$",
            Pattern.CASE_INSENSITIVE);
      } else {
        pattern = Pattern.compile("^" + Pattern.quote(resourcePrefix) + "([A-Z0-9_]{2})G1\\.BAM$",
            Pattern.CASE_INSENSITIVE);
      }
      for (final String name : resourceNames) {
        final Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
          candidates.add(matcher.group(1).toUpperCase(Locale.ENGLISH));
        }
      }
    }

    final List<String> result = new ArrayList<>();
    for (final String code : candidates) {
      final FamilyLayout layout =
          family.createOverlayLayout(resourcePrefix, toLayoutAppearanceCode(code), targetWeapon);
      if (EquipmentOverlayBamImporter.resourceFilesExist(layout, resourceNames, family)) {
        result.add(code);
      }
    }
    return result;
  }

  private static String chooseSourceCode(EquipmentOverlayFamily family, List<String> available,
      WeaponType sourceType, WeaponType targetType, String override) {
    final String requested = normalizeSourceOverride(override, family);
    if (requested != null) {
      if (!available.contains(requested)) {
        throw new IllegalArgumentException("Source layer " + requested + " is unavailable. Complete compatible "
            + "layers: " + String.join(", ", available) + ".");
      }
      return requested;
    }

    final boolean firstCharacter = !family.usesFullAppearanceCodeInFileName();
    final WeaponType preferred = sourceType != null ? sourceType : targetType;
    if (preferred != null) {
      final String suggested = preferred.getSuggestedAppearanceCode();
      final String code = firstCharacter ? suggested.substring(0, 1) : suggested;
      if (available.contains(code)
          && (family != EquipmentOverlayFamily.CHARACTER && family != EquipmentOverlayFamily.CHARACTER_OLD
              || sourceType == null || samePoseClass(sourceType, targetType))) {
        return code;
      }
    }

    if (!firstCharacter) {
      final Map<String, String> descriptions = Profile.getEquippedAppearanceMap();
      final List<CodeScore> scored = new ArrayList<>();
      for (final String code : available) {
        final String description = descriptions.get(code);
        int score = scoreDescription(description, sourceType);
        final WeaponType described = findDescribedWeapon(description);
        if (described != null && samePoseClass(described, targetType)) {
          score += 40;
        } else if (family == EquipmentOverlayFamily.CHARACTER
            || family == EquipmentOverlayFamily.CHARACTER_OLD) {
          score = 0;
        }
        if (score > 0) {
          scored.add(new CodeScore(code, score));
        }
      }
      if (!scored.isEmpty()) {
        scored.sort(Comparator.comparingInt((CodeScore value) -> value.score).reversed()
            .thenComparing(value -> value.code));
        if (scored.size() == 1 || scored.get(0).score > scored.get(1).score) {
          return scored.get(0).code;
        }
      }
    }

    if (family == EquipmentOverlayFamily.MONSTER && available.contains("S1")) {
      return "S1";
    }
    if (available.size() == 1 && family != EquipmentOverlayFamily.CHARACTER
        && family != EquipmentOverlayFamily.CHARACTER_OLD) {
      return available.get(0);
    }
    throw new IllegalArgumentException("A unique pose-compatible source could not be selected from "
        + String.join(", ", available) + ". Name the original weapon more precisely or enter its source layer code.");
  }

  private static String chooseTargetCode(EquipmentOverlayFamily family, SpriteDecoder decoder, String resourcePrefix,
      String sourceCode, WeaponType targetType, String override) {
    final String requested = normalizeTargetOverride(override);
    final String restrictedPrefix = family.getRequiredSourceLayerCode(decoder, targetType);
    if (!restrictedPrefix.isEmpty()) {
      if (requested != null && !requested.startsWith(restrictedPrefix)) {
        throw new IllegalArgumentException(family + " accepts this pose through weapon prefix " + restrictedPrefix
            + "; the target Equipped appearance must begin with that character.");
      }
      if (requested != null) {
        return requested;
      }
      final String suggested = targetType.getSuggestedAppearanceCode();
      return suggested.startsWith(restrictedPrefix) ? suggested : restrictedPrefix + suggested.substring(1);
    }
    if (requested != null) {
      if (family.usesFullAppearanceCodeInFileName() && requested.equals(sourceCode)) {
        throw new IllegalArgumentException("The target appearance code must differ from source layer " + sourceCode
            + ".");
      }
      return requested;
    }

    final String suggested = targetType.getSuggestedAppearanceCode();
    if (family.usesFullAppearanceCodeInFileName()) {
      if (!suggested.equals(sourceCode) && !overlayExists(family, resourcePrefix, suggested, targetType)) {
        return suggested;
      }
      final char prefix = Character.toUpperCase(targetType.getLabel().charAt(0));
      for (char suffix = '0'; suffix <= '9'; suffix++) {
        final String candidate = new String(new char[] { prefix, suffix });
        if (!candidate.equals(sourceCode) && !overlayExists(family, resourcePrefix, candidate, targetType)) {
          return candidate;
        }
      }
      for (char first = 'A'; first <= 'Z'; first++) {
        for (char second = 'A'; second <= 'Z'; second++) {
          final String candidate = new String(new char[] { first, second });
          if (!candidate.equals(sourceCode) && !overlayExists(family, resourcePrefix, candidate, targetType)) {
            return candidate;
          }
        }
      }
    } else {
      final LinkedHashSet<Character> firstCharacters = new LinkedHashSet<>();
      for (int index = 0; index < suggested.length(); index++) {
        firstCharacters.add(suggested.charAt(index));
      }
      final String normalizedLabel =
          targetType.getLabel().toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9]", "");
      for (int index = 0; index < normalizedLabel.length(); index++) {
        firstCharacters.add(normalizedLabel.charAt(index));
      }
      for (char value = 'A'; value <= 'Z'; value++) {
        firstCharacters.add(value);
      }
      for (char value = '0'; value <= '9'; value++) {
        firstCharacters.add(value);
      }
      for (final Character first : firstCharacters) {
        final String candidate = first + "0";
        if (!sourceCode.startsWith(first.toString())
            && !overlayExists(family, resourcePrefix, candidate, targetType)) {
          return candidate;
        }
      }
    }
    throw new IllegalArgumentException("No collision-free Equipped appearance code could be selected automatically.");
  }

  private static boolean overlayExists(EquipmentOverlayFamily family, String resourcePrefix, String appearanceCode,
      WeaponType weaponType) {
    final FamilyLayout layout = family.createOverlayLayout(resourcePrefix, appearanceCode, weaponType);
    for (final String fileName : layout.getResources().keySet()) {
      if (ResourceFactory.resourceExists(fileName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds type {@code 0x7000} resrefs that provide both base G1/G2 BAMs and at least one complete G1/G2 weapon pair.
   */
  static List<String> findCompatibleEquipmentResrefs(String animationResref, Iterable<String> resourceNames) {
    final String normalizedResref =
        animationResref != null ? animationResref.trim().toUpperCase(Locale.ENGLISH) : "";
    if (!normalizedResref.matches("[A-Z0-9_]{1,4}")) {
      throw new IllegalArgumentException("Animation resrefs must contain 1-4 ASCII letters, digits or underscores.");
    }

    final Set<String> names = normalizeResourceNames(resourceNames);
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
          && !findCompleteMonsterAppearanceCodes(candidate, names).isEmpty()) {
        result.add(candidate);
      }
    }
    if (result.remove(normalizedResref)) {
      result.add(0, normalizedResref);
    }
    return result;
  }

  private static List<String> findCompleteMonsterAppearanceCodes(String resref, Iterable<String> resourceNames) {
    final Set<String> names = normalizeResourceNames(resourceNames);
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

  private static Set<String> normalizeResourceNames(Iterable<String> resourceNames) {
    final Set<String> result = new HashSet<>();
    if (resourceNames != null) {
      for (final String name : resourceNames) {
        if (name != null) {
          result.add(name.trim().toUpperCase(Locale.ENGLISH));
        }
      }
    }
    return result;
  }

  private static String normalizeSourceOverride(String value, EquipmentOverlayFamily family) {
    final String normalized = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
    if (normalized.isEmpty() || "AUTO".equals(normalized)) {
      return null;
    }
    if (!SOURCE_LAYER_CODE.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Source layer overrides must be AUTO or one/two ASCII characters.");
    }
    if (family.usesFullAppearanceCodeInFileName() && normalized.length() != 2) {
      throw new IllegalArgumentException(family + " source layer codes require exactly two characters.");
    }
    return family.usesFullAppearanceCodeInFileName() ? normalized : normalized.substring(0, 1);
  }

  private static String normalizeTargetOverride(String value) {
    final String normalized = value != null ? value.trim().toUpperCase(Locale.ENGLISH) : "";
    if (normalized.isEmpty() || "AUTO".equals(normalized)) {
      return null;
    }
    if (!APPEARANCE_CODE.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Target appearance overrides must be AUTO or exactly two ASCII characters.");
    }
    return normalized;
  }

  private static String toLayoutAppearanceCode(String sourceCode) {
    return sourceCode.length() == 1 ? sourceCode + "_" : sourceCode;
  }

  private static boolean samePoseClass(WeaponType first, WeaponType second) {
    if (first == null || second == null) {
      return false;
    }
    if (first == WeaponType.BOW || second == WeaponType.BOW) {
      return first == second;
    }
    return first.isTwoHanded() == second.isTwoHanded();
  }

  private static int scoreDescription(String description, WeaponType sourceType) {
    if (description == null || sourceType == null) {
      return 0;
    }
    final String normalized = normalizeWords(description);
    int result = 0;
    for (final String alias : sourceType.getAliases()) {
      if (normalized.contains(" " + normalizeWords(alias).trim() + " ")) {
        result = Math.max(result, 20 + alias.length());
      }
    }
    return result;
  }

  private static WeaponType findDescribedWeapon(String description) {
    final String normalized = normalizeWords(description);
    WeaponType result = null;
    int bestLength = -1;
    for (final WeaponType type : WeaponType.values()) {
      for (final String alias : type.getAliases()) {
        final String candidate = normalizeWords(alias).trim();
        if (normalized.contains(" " + candidate + " ") && candidate.length() > bestLength) {
          result = type;
          bestLength = candidate.length();
        }
      }
    }
    return result;
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
