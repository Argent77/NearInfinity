// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre.decoder;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.infinity.datatype.IsNumeric;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.cre.decoder.util.AnimationInfo;
import org.infinity.resource.cre.decoder.util.CreatureInfo;
import org.infinity.resource.cre.decoder.util.CycleDef;
import org.infinity.resource.cre.decoder.util.DecoderAttribute;
import org.infinity.resource.cre.decoder.util.DirDef;
import org.infinity.resource.cre.decoder.util.Direction;
import org.infinity.resource.cre.decoder.util.EffectInfo;
import org.infinity.resource.cre.decoder.util.FrameInfo;
import org.infinity.resource.cre.decoder.util.ItemInfo;
import org.infinity.resource.cre.decoder.util.SegmentDef;
import org.infinity.resource.cre.decoder.util.SeqDef;
import org.infinity.resource.cre.decoder.util.Sequence;
import org.infinity.resource.cre.decoder.util.SpriteUtils;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.BamV1Decoder.BamV1Control;
import org.infinity.resource.graphics.BlendingComposite;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapSection;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.tuples.Couple;

/**
 * Specialized BAM decoder for creature animation sprites.
 */
public abstract class SpriteDecoder extends PseudoBamDecoder {
  // List of general creature animation attributes
  public static final DecoderAttribute KEY_ANIMATION_TYPE     = DecoderAttribute.with("animation_type", DecoderAttribute.DataType.USERDEFINED);
  public static final DecoderAttribute KEY_ANIMATION_SECTION  = DecoderAttribute.with("animation_section", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_MOVE_SCALE         = DecoderAttribute.with("move_scale", DecoderAttribute.DataType.DECIMAL);
  public static final DecoderAttribute KEY_ELLIPSE            = DecoderAttribute.with("ellipse", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_COLOR_BLOOD        = DecoderAttribute.with("color_blood", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_COLOR_CHUNKS       = DecoderAttribute.with("color_chunks", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_SOUND_FREQ         = DecoderAttribute.with("sound_freq", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_SOUND_DEATH        = DecoderAttribute.with("sound_death", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_PERSONAL_SPACE     = DecoderAttribute.with("personal_space", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_CAST_FRAME         = DecoderAttribute.with("cast_frame", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_HEIGHT_OFFSET      = DecoderAttribute.with("height_offset", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_BRIGHTEST          = DecoderAttribute.with("brightest", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_MULTIPLY_BLEND     = DecoderAttribute.with("multiply_blend", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_LIGHT_SOURCE       = DecoderAttribute.with("light_source", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_NEW_PALETTE        = DecoderAttribute.with("new_palette", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_SOUND_REF          = DecoderAttribute.with("sound_ref", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_0     = DecoderAttribute.with("combat_round_0", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_1     = DecoderAttribute.with("combat_round_1", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_2     = DecoderAttribute.with("combat_round_2", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_3     = DecoderAttribute.with("combat_round_3", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_4     = DecoderAttribute.with("combat_round_4", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_WALK_SOUND         = DecoderAttribute.with("walk_sound", DecoderAttribute.DataType.STRING);
  // List of commonly used attributes specific to creature animation types
  public static final DecoderAttribute KEY_RESREF             = DecoderAttribute.with("resref", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_DETECTED_BY_INFRAVISION  = DecoderAttribute.with("detected_by_infravision", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_FALSE_COLOR        = DecoderAttribute.with("false_color", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_TRANSLUCENT        = DecoderAttribute.with("translucent", DecoderAttribute.DataType.BOOLEAN);

  /**
   * A default operation that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It is called once per source BAM resource. Performed actions: palette replacement, shadow color fix, false
   * color replacement, translucency
   */
  protected final BeforeSourceBam FN_BEFORE_SRC_BAM = (control, sd) -> {
    if (isPaletteReplacementEnabled() && sd.getSpriteType() == SegmentDef.SpriteType.AVATAR) {
      int[] palette = getNewPaletteData(sd.getEntry());
      if (palette != null) {
        SpriteUtils.applyNewPalette(control, palette);
      }
    }

    SpriteUtils.fixShadowColor(control, isTransparentShadow());

    if (isPaletteReplacementEnabled() && isFalseColor()) {
      applyFalseColors(control, sd);
    }

    if (isTintEnabled()) {
      applyColorTint(control, sd);
    }

    if (isPaletteReplacementEnabled() && isFalseColor() && sd.getSpriteType() == SegmentDef.SpriteType.AVATAR) {
      applyColorEffects(control, sd);
    }

    if ((isTranslucencyEnabled() && isTranslucent()) || (isBlurEnabled() && isBlurred())) {
      int minVal = (isBlurEnabled() && isBlurred()) ? 64 : 255;
      applyTranslucency(control, minVal);
    }
  };

  /**
   * A default operation that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It is called for each source frame (segment) before being applied to the target frame.
   */
  protected final BeforeSourceFrame FN_BEFORE_SRC_FRAME = (sd, image, g) -> image;

  /**
   * A default operation that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It is called for each source frame (segment) after being applied to the target frame.
   */
  protected final AfterSourceFrame FN_AFTER_SRC_FRAME = (sd, g) -> {
    // nothing to do...
  };

  /**
   * A default action that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It calculates an eastern direction frame by mirroring it horizontally if needed.
   */
  protected final AfterDestFrame FN_AFTER_DST_FRAME = (dd, frameIdx) -> {
    if (dd.isMirrored()) {
      flipImageHorizontal(frameIdx);
    }
  };

  private final CreatureInfo creInfo;
  private final IniMap ini;
  /** Storage for associations between directions and cycle indices. */
  private final EnumMap<Direction, Integer> directionMap;
  /** Cache for creature animation attributes. */
  private final TreeMap<DecoderAttribute, Object> attributesMap;

  private BufferedImage imageCircle;
  private Sequence currentSequence;
  private boolean showCircle;
  private boolean selectionCircleBitmap;
  private boolean showPersonalSpace;
  private boolean showBoundingBox;
  private boolean transparentShadow;
  private boolean translucencyEnabled;
  private boolean tintEnabled;
  private boolean blurEnabled;
  private boolean paletteReplacementEnabled;
  private boolean renderSpriteAvatar;
  private boolean renderSpriteWeapon;
  private boolean renderSpriteHelmet;
  private boolean renderSpriteShield;
  private boolean animationChanged;
  private boolean autoApplyChanges;

  /**
   * Creates a new {@code SpriteDecoder} instance based on the specified animation id.
   *
   * @param animationId the creature animation id in the range [0, 0xffff].
   * @return A {@code SpriteDecoder} instance with processed animation data.
   * @throws Exception if the creature animation could not be loaded.
   */
  public static SpriteDecoder importSprite(int animationId) throws Exception {
    if (animationId < 0 || animationId > 0xffff) {
      throw new IllegalArgumentException(String.format("Animation id is out of range: 0x%04x", animationId));
    }
    CreResource cre = SpriteUtils.getPseudoCre(animationId, null, null);
    return importSprite(cre);
  }

  /**
   * Creates a new {@code SpriteDecoder} instance based on the specified CRE resource.
   *
   * @param cre The CRE resource instance.
   * @return A {@code SpriteDecoder} instance with processed animation data.
   * @throws Exception if the specified resource could not be processed.
   */
  public static SpriteDecoder importSprite(CreResource cre) throws Exception {
    Objects.requireNonNull(cre, "CRE resource cannot be null");
    int animationId = ((IsNumeric) cre.getAttribute(CreResource.CRE_ANIMATION)).getValue();
    Class<? extends SpriteDecoder> spriteClass = Objects.requireNonNull(SpriteUtils.detectAnimationType(animationId),
        String.format("Creature animation is not available: 0x%04x", animationId));
    try {
      Constructor<? extends SpriteDecoder> ctor = Objects.requireNonNull(spriteClass.getConstructor(CreResource.class),
          "No matching constructor found");
      return ctor.newInstance(cre);
    } catch (InvocationTargetException ite) {
      throw (ite.getCause() instanceof Exception) ? (Exception) ite.getCause() : ite;
    }
  }

  /**
   * Instances creates with this constructor are only suited for identification purposes.
   *
   * @param type        the animation type
   * @param animationId specific animation id
   * @param ini         the INI file with creature animation attributes
   * @throws Exception
   */
  protected SpriteDecoder(AnimationInfo.Type type, int animationId, IniMap ini) throws Exception {
    Objects.requireNonNull(type, "Animation type cannot be null");
    Objects.requireNonNull(ini, "No INI data available for animation id: " + animationId);
    this.attributesMap = new TreeMap<>();
    this.directionMap = new EnumMap<>(Direction.class);
    setAttribute(KEY_ANIMATION_TYPE, type);
    setAttribute(KEY_ANIMATION_SECTION, type.getSectionName());
    this.creInfo = new CreatureInfo(this, SpriteUtils.getPseudoCre(animationId, null, null));
    this.ini = ini;
    this.currentSequence = Sequence.NONE;
    init();
    if (!isMatchingAnimationType()) {
      throw new IllegalArgumentException("Animation id is incompatible with animation type: " + type);
    }
  }

  /**
   * This constructor creates an instance that can be used to render animation sequences.
   *
   * @param type the animation type
   * @param cre  the CRE resource instance.
   * @throws Exception
   */
  protected SpriteDecoder(AnimationInfo.Type type, CreResource cre) throws Exception {
    Objects.requireNonNull(type, "Animation type cannot be null");
    this.attributesMap = new TreeMap<>();
    this.directionMap = new EnumMap<>(Direction.class);
    setAttribute(KEY_ANIMATION_TYPE, type);
    setAttribute(KEY_ANIMATION_SECTION, type.getSectionName());
    this.creInfo = new CreatureInfo(this, cre);
    this.ini = Objects.requireNonNull(SpriteUtils.getAnimationInfo(getAnimationId()),
        String.format("No INI data available for animation id: 0x%04x", getAnimationId()));
    this.currentSequence = Sequence.NONE;
    this.showCircle = false;
    this.selectionCircleBitmap = (Profile.getGame() == Profile.Game.PST) || (Profile.getGame() == Profile.Game.PSTEE);
    this.showPersonalSpace = false;
    this.showBoundingBox = false;
    this.transparentShadow = true;
    this.translucencyEnabled = true;
    this.tintEnabled = true;
    this.blurEnabled = true;
    this.paletteReplacementEnabled = true;
    this.renderSpriteAvatar = true;
    this.renderSpriteWeapon = true;
    this.renderSpriteShield = true;
    this.renderSpriteHelmet = true;
    this.autoApplyChanges = true;
    SpriteUtils.updateRandomPool();
    init();
  }

  /**
   * Returns the data associated with the specified attribute name.
   *
   * @param att the attribute object.
   * @return attribute data in the type inferred from the method call. Returns {@code null} if data is not available for
   *         the inferred type.
   */
  @SuppressWarnings("unchecked")
  public <T> T getAttribute(DecoderAttribute att) {
    T retVal = null;
    if (att == null) {
      return retVal;
    }

    Object data = attributesMap.getOrDefault(att, att.getDefaultValue());
    if (data != null) {
      try {
        retVal = (T) data;
      } catch (ClassCastException e) {
        // Logger.error(e);
      }
    }
    return retVal;
  }

  /**
   * Stores the attribute key and value along with the autodetected data type.
   *
   * @param att   the attribute object.
   * @param value the value in one of the data types covered by {@link DecoderAttribute.DataType}.
   */
  protected void setAttribute(DecoderAttribute att, Object value) {
    if (att == null) {
      return;
    }
    attributesMap.put(att, value);
  }

  /** Returns an iterator over the attribute keys. */
  public Iterator<DecoderAttribute> getAttributeIterator() {
    return attributesMap.keySet().iterator();
  }

  /** Returns the type of the current creature animation. */
  public AnimationInfo.Type getAnimationType() {
    return getAttribute(KEY_ANIMATION_TYPE);
  }

  /**
   * Returns the INI section name for the current animation type. Returns {@code null} if the name could not be
   * determined.
   */
  public String getAnimationSectionName() {
    return getAttribute(KEY_ANIMATION_SECTION);
  }

  /**
   * Returns a list of BAM filenames associated with the current animation type.
   *
   * @param essential if set returns only essential files required for the animation.
   * @return list of BAM filenames associated with the current animation type. Returns {@code null} if files could not
   *         be determined.
   */
  public abstract List<String> getAnimationFiles(boolean essential);

  /** Recreates the creature animation based on the current creature resource. */
  public void reset() throws Exception {
    Direction[] directions = getDirectionMap().keySet().toArray(new Direction[0]);
    discard();
    // recreating current sequence
    if (getCurrentSequence() != Sequence.NONE) {
      createSequence(getCurrentSequence(), directions);
    }
  }

  /** Removes the currently loaded animation sequence. */
  protected void discard() {
    frameClear();
    directionMap.clear();
    SpriteUtils.clearBamCache();
  }

  /**
   * Loads the specified sequence if available. Discards the currently active sequence. Call {@code reset()} instead to
   * enforce reloading the same sequence with different creature attributes.
   *
   * @param seq the animation sequence to load. Specifying {@code Sequence.None} only discards the current sequence.
   * @return whether the sequence was successfully loaded.
   */
  public boolean loadSequence(Sequence seq) throws Exception {
    return loadSequence(seq, null);
  }

  /**
   * Loads selected directions of the specified sequence if available. Discards the currently active sequence. Call
   * {@code reset()} instead to enforce reloading the same sequence with different creature attributes.
   *
   * @param seq        the animation sequence to load. Specifying {@code Sequence.None} only discards the current
   *                   sequence.
   * @param directions array with directions allowed to be created. Specify {@code null} to create animations for all
   *                   directions.
   * @return whether the sequence was successfully loaded.
   */
  public boolean loadSequence(Sequence seq, Direction[] directions) throws Exception {
    boolean retVal = true;

    if (getCurrentSequence() != Objects.requireNonNull(seq, "Animation sequence cannot be null")) {
      // discarding current sequence
      discard();

      try {
        createSequence(seq, directions);
        currentSequence = seq;
      } catch (NullPointerException e) {
        retVal = (seq != Sequence.NONE);
      } catch (Exception e) {
        Logger.error(e);
        retVal = (seq != Sequence.NONE);
      }
    }

    return retVal;
  }

  /** Returns the currently active sequence. */
  public Sequence getCurrentSequence() {
    return currentSequence;
  }

  /** Returns whether the specified animation sequence is available for the current creature animation. */
  public abstract boolean isSequenceAvailable(Sequence seq);

  /**
   * Returns an independent snapshot of the resource and cycle definition used by the specified sequence.
   *
   * <p>This exposes the decoder's resolved engine layout without allowing callers to modify decoder state.</p>
   *
   * @param seq the animation sequence to inspect
   * @return a cloned sequence definition, or {@code null} if the sequence is unavailable
   */
  public final SeqDef getSequenceDefinitionSnapshot(Sequence seq) {
    final SeqDef definition = getSequenceDefinition(Objects.requireNonNull(seq));
    return (definition != null) ? definition.clone() : null;
  }

  /**
   * Returns the closest available direction to the specified direction.
   *
   * @param dir the requested direction
   * @return an available {@code Direction} that is closest to the specified direction. Returns {@code null} if no
   *         direction is available.
   */
  public Direction getExistingDirection(Direction dir) {
    Direction retVal = null;

    if (dir == null) {
      return retVal;
    }
    if (getDirectionMap().containsKey(dir)) {
      return dir;
    }
    SeqDef sd = getSequenceDefinition(getCurrentSequence());
    if (sd == null || sd.isEmpty()) {
      return retVal;
    }

    int dirIdx = dir.getValue();
    int dirLen = Direction.values().length;
    int maxRange = dirLen / 2;
    for (int range = 1; range <= maxRange; range++) {
      int dist = (dirIdx + range + dirLen) % dirLen;
      Direction distDir = Direction.from(dist);
      if (getDirectionMap().containsKey(distDir)) {
        retVal = distDir;
        break;
      }
      dist = (dirIdx - range + dirLen) % dirLen;
      distDir = Direction.from(dist);
      if (getDirectionMap().containsKey(distDir)) {
        retVal = distDir;
        break;
      }
    }

    return retVal;
  }

  /** Provides access to the {@link CreatureInfo} instance associated with the sprite decoder. */
  public CreatureInfo getCreatureInfo() {
    return creInfo;
  }

  /** Returns the {@code CreResource} instance of the current CRE resource. */
  public CreResource getCreResource() {
    return creInfo.getCreResource();
  }

  /** Returns the numeric animation id of the current CRE resource. */
  public int getAnimationId() {
    return creInfo.getAnimationId();
  }

  /** Returns a INI structure with creature animation info. */
  public IniMap getAnimationInfo() {
    return ini;
  }

  /** Returns whether the selection circle for the creature is drawn. */
  public boolean isSelectionCircleEnabled() {
    return showCircle;
  }

  /** Sets whether the selection circle for the creature is drawn. */
  public void setSelectionCircleEnabled(boolean b) {
    if (showCircle != b) {
      showCircle = b;
      selectionCircleChanged();
    }
  }

  /** Returns whether the space occupied by the creature is visualized. */
  public boolean isPersonalSpaceVisible() {
    return showPersonalSpace;
  }

  /** Sets whether the space occupied by the creature is visualized. */
  public void setPersonalSpaceVisible(boolean b) {
    if (showPersonalSpace != b) {
      showPersonalSpace = b;
      personalSpaceChanged();
    }
  }

  /** Returns whether a bounding box is drawn around sprites (or quadrants) and secondary overlays. */
  public boolean isBoundingBoxVisible() {
    return showBoundingBox;
  }

  /** Sets whether a bounding box is drawn around sprites (or quadrants) and secondary overlays. */
  public void setBoundingBoxVisible(boolean b) {
    if (showBoundingBox != b) {
      showBoundingBox = b;
      spriteChanged();
    }
  }

  /** Returns whether the avatar sprite should be rendered. */
  public boolean getRenderAvatar() {
    return renderSpriteAvatar;
  }

  /** Sets whether the avatar sprite should be rendered. */
  public void setRenderAvatar(boolean b) {
    if (renderSpriteAvatar != b) {
      renderSpriteAvatar = b;
      spriteChanged();
    }
  }

  /** Returns whether the weapon overlay should be rendered. This option affects only specific animation types. */
  public boolean getRenderWeapon() {
    return renderSpriteWeapon;
  }

  /** Sets whether the weapon overlay should be rendered. This option affects only specific animation types. */
  public void setRenderWeapon(boolean b) {
    if (renderSpriteWeapon != b) {
      renderSpriteWeapon = b;
      spriteChanged();
    }
  }

  /**
   * Returns whether the shield (or left-handed weapon) overlay should be rendered. This option affects only specific
   * animation types.
   */
  public boolean getRenderShield() {
    return renderSpriteShield;
  }

  /**
   * Sets whether the shield (or left-handed weapon) overlay should be rendered. This option affects only specific
   * animation types.
   */
  public void setRenderShield(boolean b) {
    if (renderSpriteShield != b) {
      renderSpriteShield = b;
      spriteChanged();
    }
  }

  /** Returns whether the helmet overlay should be rendered. This option affects only specific animation types. */
  public boolean getRenderHelmet() {
    return renderSpriteHelmet;
  }

  /** Sets whether the helmet overlay should be rendered. This option affects only specific animation types. */
  public void setRenderHelmet(boolean b) {
    if (renderSpriteHelmet != b) {
      renderSpriteHelmet = b;
      spriteChanged();
    }
  }

  /** Returns whether translucency effect is applied to the creature animation. */
  public boolean isTranslucencyEnabled() {
    return translucencyEnabled;
  }

  /** Sets whether translucency effect is applied to the creature animation. */
  public void setTranslucencyEnabled(boolean b) {
    if (translucencyEnabled != b) {
      translucencyEnabled = b;
      if (isTranslucent()) {
        SpriteUtils.clearBamCache();
        spriteChanged();
      }
    }
  }

  /** Returns whether tint effects are applied to the creature animation. */
  public boolean isTintEnabled() {
    return tintEnabled;
  }

  /** Sets whether tint effects are applied to the creature animation. */
  public void setTintEnabled(boolean b) {
    if (tintEnabled != b) {
      tintEnabled = b;
      SpriteUtils.clearBamCache();
      spriteChanged();
    }
  }

  /** Returns whether blur effect is applied to the creature animation. */
  public boolean isBlurEnabled() {
    return blurEnabled;
  }

  /** Sets whether blur effect is applied to the creature animation. */
  public void setBlurEnabled(boolean b) {
    if (blurEnabled != b) {
      blurEnabled = b;
      SpriteUtils.clearBamCache();
      spriteChanged();
    }
  }

  /** Returns whether any kind of palette replacement (full palette or false colors) is enabled. */
  public boolean isPaletteReplacementEnabled() {
    return paletteReplacementEnabled;
  }

  /** Sets whether palette replacement (full palette or false colors) is enabled. */
  public void setPaletteReplacementEnabled(boolean b) {
    if (paletteReplacementEnabled != b) {
      paletteReplacementEnabled = b;
      SpriteUtils.clearBamCache();
      spriteChanged();
    }
  }

  /**
   * Returns {@code true} if a bitmap is used to render the selection circle. Returns {@code false} if a colored circle
   * is drawn instead.
   */
  public boolean isSelectionCircleBitmap() {
    return selectionCircleBitmap;
  }

  /**
   * Specify {@code true} if a bitmap should be used to render the selection circle. Specify {@code false} if a colored
   * circle should be drawn instead.
   */
  public void setSelectionCircleBitmap(boolean b) {
    if (selectionCircleBitmap != b) {
      selectionCircleBitmap = b;
      selectionCircleChanged();
    }
  }

  /** Returns the moving speed of the creature animation. */
  public double getMoveScale() {
    return getAttribute(KEY_MOVE_SCALE);
  }

  /** Sets the moving speed of the creature animation. */
  protected void setMoveScale(double value) {
    setAttribute(KEY_MOVE_SCALE, value);
  }

  /** Returns the selection circle size of the creature animation. */
  public int getEllipse() {
    return getAttribute(KEY_ELLIPSE);
  }

  /** Sets the selection circle size of the creature animation. */
  public void setEllipse(int value) {
    if (getEllipse() != value) {
      setAttribute(KEY_ELLIPSE, value);
      selectionCircleChanged();
    }
  }

  /** Returns the map space (in search map units) reserved exclusively for the creature animation */
  public int getPersonalSpace() {
    return getAttribute(KEY_PERSONAL_SPACE);
  }

  /** Sets the map space (in search map units) reserved exclusively for the creature animation */
  public void setPersonalSpace(int value) {
    if (getPersonalSpace() != value) {
      setAttribute(KEY_PERSONAL_SPACE, value);
      personalSpaceChanged();
    }
  }

  /** Returns the resref (prefix) for the associated animation files. */
  public String getAnimationResref() {
    return getAttribute(KEY_RESREF);
  }

  /** Sets the resref (prefix) for the associated animation files. */
  protected void setAnimationResref(String resref) {
    setAttribute(KEY_RESREF, resref);
  }

  /**
   * Returns the replacement palette for the creature animation. Returns empty string if no replacement palette exists.
   */
  public String getNewPalette() {
    return getAttribute(KEY_NEW_PALETTE);
  }

  /** Sets the replacement palette for the creature animation. */
  public void setNewPalette(String resref) {
    resref = (resref != null) ? resref.trim() : "";
    if (!getNewPalette().equalsIgnoreCase(resref)) {
      setAttribute(KEY_NEW_PALETTE, resref);
      paletteChanged();
    }
  }

  /** Loads the replacement palette associated with the specified BAM resource. */
  protected int[] getNewPaletteData(ResourceEntry bamRes) {
    // Note: method argument is irrelevant for base implementation
    return SpriteUtils.loadReplacementPalette(getNewPalette());
  }

  /**
   * Returns whether blending mode "brightest" is enabled.
   * <p>
   * Blending modes and their effects:
   * <li>Brightest only: Use {@code GL_ONE_MINUS_DST_COLOR}
   * <li>MultiplyBlend only: Use {@code GL_DST_COLOR}
   * <li>Brightest and MultiplyBlend: Use {@code GL_SRC_COLOR}
   */
  public boolean isBrightest() {
    return getAttribute(KEY_BRIGHTEST);
  }

  /** Sets blending mode "brightest". */
  protected void setBrightest(boolean b) {
    setAttribute(KEY_BRIGHTEST, b);
  }

  /**
   * Returns whether blending mode "multiply_blend" is enabled.
   * <p>
   * Blending modes and their effects:
   * <li>Brightest only: Use {@code GL_ONE_MINUS_DST_COLOR}
   * <li>MultiplyBlend only: Use {@code GL_DST_COLOR}
   * <li>Brightest and MultiplyBlend: Use {@code GL_SRC_COLOR}
   */
  public boolean isMultiplyBlend() {
    return getAttribute(KEY_MULTIPLY_BLEND);
  }

  /** Sets blending mode "multiply_blend". */
  protected void setMultiplyBlend(boolean b) {
    setAttribute(KEY_MULTIPLY_BLEND, b);
  }

  /** Returns whether sprite is affected by environmental lighting. */
  public boolean isLightSource() {
    return getAttribute(KEY_LIGHT_SOURCE);
  }

  /** Sets whether sprite is affected by environmental lighting. */
  protected void setLightSource(boolean b) {
    setAttribute(KEY_LIGHT_SOURCE, b);
  }

  /** Returns whether a red tint is applied to the creature if detected by infravision. */
  public boolean isDetectedByInfravision() {
    return getAttribute(KEY_DETECTED_BY_INFRAVISION);
  }

  /** Sets whether a red tint is applied to the creature if detected by infravision. */
  protected void setDetectedByInfravision(boolean b) {
    setAttribute(KEY_DETECTED_BY_INFRAVISION, b);
  }

  /** Returns whether palette range replacement is enabled. */
  public boolean isFalseColor() {
    return getAttribute(KEY_FALSE_COLOR);
  }

  /** Sets whether palette range replacement is enabled. */
  protected void setFalseColor(boolean b) {
    setAttribute(KEY_FALSE_COLOR, b);
  }

  /** Returns whether creature animation is translucent. */
  public boolean isTranslucent() {
    return getCreatureInfo().getEffectiveTranslucency() > 0;
  }

  /** Sets whether creature animation is translucent. */
  protected void setTranslucent(boolean b) {
    setAttribute(KEY_TRANSLUCENT, b);
  }

  /** Returns whether the blur effect is active for the creature animation. */
  public boolean isBlurred() {
    return getCreatureInfo().isBlurEffect();
  }

  /** Call this method whenever the visibility of the selection circle has been changed. */
  public void selectionCircleChanged() {
    // force recaching
    imageCircle = null;
  }

  /** Call this method whenever the visibility of personal space has been changed. */
  public void personalSpaceChanged() {
    // nothing to do
  }

  /** Call this method whenever the visibility of any sprite types has been changed. */
  public void spriteChanged() {
    setAnimationChanged();
  }

  /** Call this method whenever the allegiance value has been changed. */
  public void allegianceChanged() {
    // force recaching
    imageCircle = null;
  }

  /**
   * Call this method whenever the creature palette has changed. False color is processed by
   * {@link #falseColorChanged()}.
   */
  public void paletteChanged() {
    // Note: PST false color palette may also contain true color regions.
    if (!isFalseColor() || Profile.getGame() == Profile.Game.PSTEE || Profile.getEngine() == Profile.Engine.PST) {
      setAnimationChanged();
    }
  }

  /**
   * Call this method whenever the false color palette of the creature has changed. Conventional palette changes are
   * processed by {@link #paletteChanged()}.
   */
  public void falseColorChanged() {
    if (isFalseColor()) {
      setAnimationChanged();
    }
  }

  /** This method reloads the creature animation if any relevant changes have been made. */
  public void applyAnimationChanges() {
    if (hasAnimationChanged()) {
      resetAnimationChanged();
      try {
        reset();
      } catch (Exception e) {
        Logger.error(e);
      }
    }
  }

  /** Returns whether changes to the creature animation should be applied immediately. */
  public boolean isAutoApplyChanges() {
    return autoApplyChanges;
  }

  /** Sets whether changes to the creature animation should be applied immediately. */
  public void setAutoApplyChanges(boolean b) {
    if (autoApplyChanges != b) {
      autoApplyChanges = b;
      if (autoApplyChanges) {
        applyAnimationChanges();
      }
    }
  }

  /**
   * Returns whether a creature animation reload has been requested. Call {@link #applyAnimationChanges()} to apply the
   * changes.
   */
  public boolean hasAnimationChanged() {
    return animationChanged;
  }

  /** Call to request a creature animation reload by the method {@link #applyAnimationChanges()}. */
  public void setAnimationChanged() {
    animationChanged = true;
    if (isAutoApplyChanges()) {
      applyAnimationChanges();
    }
  }

  /** Call to cancel the request of a creature animation reload by the method {@link #applyAnimationChanges()}. */
  public void resetAnimationChanged() {
    animationChanged = false;
  }

  @Override
  public SpriteBamControl createControl() {
    return new SpriteBamControl(this);
  }

  /**
   * Returns the preferred compositor for rendering the sprite on the target surface.
   */
  @Override
  public Composite getComposite() {
    int blending = ((isBrightest() ? 1 : 0)) | ((isMultiplyBlend() ? 1 : 0) << 1);
    switch (blending) {
      case 1: // brightest
        return BlendingComposite.Brightest;
      case 2: // multiply
        return BlendingComposite.Multiply;
      case 3: // brightest + multiply
        return BlendingComposite.BrightestMultiply;
      default:
        return AlphaComposite.SrcOver;
    }
  }

  /**
   * Returns the BAM cycle associated with the specified direction. Returns -1 if entry not found.
   */
  public int getCycleIndex(Direction dir) {
    int retVal = -1;
    Integer value = directionMap.get(dir);
    if (value != null) {
      retVal = value;
    }

    return retVal;
  }

  /**
   * Returns a copy of the map containing associations between animation directions and bam sequence numbers.
   */
  public EnumMap<Direction, Integer> getDirectionMap() {
    return directionMap.clone();
  }

  /** Creates the BAM structure for the creature animation. */
  protected abstract void init() throws Exception;

  /**
   * Initializes general data for the creature animation.
   *
   * @param ini The INI map containing creature animation data.
   */
  protected void initDefaults(IniMap ini) throws Exception {
    IniMapSection section = getGeneralIniSection(Objects.requireNonNull(ini, "INI object cannot be null"));
    Objects.requireNonNull(section.getAsString("animation_type"), "animation_type required");
    Misc.requireCondition(getAnimationType().contains(getAnimationId()),
        String.format("Animation slot (%04X) is not compatible with animation type (%s)", getAnimationId(),
            getAnimationType().toString()));

    setMoveScale(section.getAsDouble("move_scale", 0.0));
    setEllipse(section.getAsInteger("ellipse", 16));
    setPersonalSpace(section.getAsInteger("personal_space", 3));
    setBrightest(section.getAsInteger("brightest", 0) != 0);
    setMultiplyBlend(section.getAsInteger("multiply_blend", 0) != 0);
    setLightSource(section.getAsInteger("light_source", 0) != 0);

    String s = section.getAsString("new_palette", "");
    setNewPalette(s);

    // getting first available "resref" definition
    for (IniMapSection iniMapEntries : getAnimationInfo()) {
      section = iniMapEntries;
      s = section.getAsString("resref", "");
      if (!s.isEmpty()) {
        setAnimationResref(s);
        break;
      }
    }
    Misc.requireCondition(!getAnimationResref().isEmpty(), "Animation resource prefix required");
  }

  /**
   * Returns the general INI map section defined for all supported creature animation types from the specified
   * {@code IniMap} instance. Returns an empty {@code IniMapSection} instance if section could not be determined.
   */
  protected IniMapSection getGeneralIniSection(IniMap ini) {
    final String sectionName = "general";
    IniMapSection retVal = null;
    if (ini != null) {
      retVal = ini.getSection(sectionName);
    }

    if (retVal == null) {
      retVal = new IniMapSection(sectionName, 0, null);
    }

    return retVal;
  }

  /**
   * Returns the INI map section responsible for animation-type-specific attributes. Returns an empty
   * {@code IniMapSection} instance if section could not be determined.
   */
  protected IniMapSection getSpecificIniSection() {
    IniMapSection retVal = null;
    IniMap ini = getAnimationInfo();
    if (ini != null) {
      retVal = ini.getSection(getAnimationSectionName());
    }

    if (retVal == null) {
      retVal = new IniMapSection(getAnimationSectionName(), 0, null);
    }

    return retVal;
  }

  /**
   * Assigns a cycle index to the specified BAM sequence and direction.
   *
   * @param dir        the direction type
   * @param cycleIndex the cycle index associated with the specified sequence and direction.
   * @return The previous BAM cycle index if available. -1 otherwise.
   */
  protected int addDirection(Direction dir, int cycleIndex) {
    int retVal = -1;
    Objects.requireNonNull(dir, "Creature direction required");
    Integer value = directionMap.get(dir);
    if (value != null) {
      retVal = value;
    }
    directionMap.putIfAbsent(dir, cycleIndex);

    return retVal;
  }

  /**
   * Generates definitions for the specified animation sequence.
   *
   * @param seq the requested animation sequence.
   * @return a fully initialized {@code SeqDef} object if sequence is supported, {@code null} otherwise.
   */
  protected abstract SeqDef getSequenceDefinition(Sequence seq);

  /**
   * Loads the specified animation sequence into the SpriteDecoder.
   *
   * @param seq the sequence to load.
   * @throws NullPointerException if specified sequence is not available.
   */
  protected void createSequence(Sequence seq) throws Exception {
    createSequence(seq, null);
  }

  /**
   * Loads the specified animation sequence into the SpriteDecoder. Only directions listed in the given
   * {@code Direction} array will be considered.
   *
   * @param seq        the sequence to load.
   * @param directions an array of {@code Direction} values. Only directions listed in the array are considered by the
   *                   creation process. Specify {@code null} to allow all directions.
   * @throws NullPointerException if specified sequence is not available.
   */
  protected void createSequence(Sequence seq, Direction[] directions) throws Exception {
    SeqDef sd = Objects.requireNonNull(getSequenceDefinition(seq),
        "Sequence not available: " + (seq != null ? seq : "(null)"));
    if (directions == null) {
      directions = Direction.values();
    }
    createAnimation(sd, Arrays.asList(directions), FN_BEFORE_SRC_BAM, FN_BEFORE_SRC_FRAME, FN_AFTER_SRC_FRAME,
        FN_AFTER_DST_FRAME);
  }

  /** Returns the number of sprite instances to render by the current blur state of the creature animation. */
  protected int getBlurInstanceCount() {
    return isBlurred() ? 4 : 1;
  }

  protected Point getBlurInstanceShift(Point pt, Direction dir, int idx) {
    Point retVal = (pt != null) ? pt : new Point();
    if (isBlurred()) {
      int dist = Math.max(0, idx) * 9; // distance between images: 9 pixels
      // shift position depends on sprite direction and specified index
      int dirValue = ((dir != null) ? dir.getValue() : 0) & ~1; // truncate to nearest semi-cardinal direction
      switch (dirValue) {
        case 2: // SW
          retVal.x = dist / 2;
          retVal.y = -dist / 2;
          break;
        case 4: // W
          retVal.x = dist;
          retVal.y = 0;
          break;
        case 6: // NW
          retVal.x = dist / 2;
          retVal.y = dist / 2;
          break;
        case 8: // N
          retVal.x = 0;
          retVal.y = dist;
          break;
        case 10: // NE
          retVal.x = -dist / 2;
          retVal.y = dist / 2;
          break;
        case 12: // E
          retVal.x = -dist;
          retVal.y = 0;
          break;
        case 14: // SE
          retVal.x = -dist / 2;
          retVal.y = -dist / 2;
          break;
        default: // S
          retVal.x = 0;
          retVal.y = -dist;
      }
    } else {
      retVal.x = retVal.y = 0;
    }
    return retVal;
  }

  protected void createAnimation(SeqDef definition, List<Direction> directions, BeforeSourceBam beforeSrcBam,
      BeforeSourceFrame beforeSrcFrame, AfterSourceFrame afterSrcFrame, AfterDestFrame afterDstFrame) {
    PseudoBamControl dstCtrl = createControl();
    BamV1Control srcCtrl;
    ResourceEntry entry;
    Objects.requireNonNull(definition, "Sequence definition cannot be null");

    if (directions == null) {
      directions = Arrays.asList(Direction.values());
    }
    if (directions.isEmpty()) {
      return;
    }

    // Ensure that BeforeSourceBam function is applied only once per source BAM
    HashSet<BamV1Control> bamControlSet = new HashSet<>();

    // Ensures that global effects are only applied once per resource entry
    final HashSet<ResourceEntry> entrySet = new HashSet<>();

    for (final DirDef dd : definition.getDirections()) {
      if (!directions.contains(dd.getDirection())) {
        continue;
      }
      CycleDef cd = dd.getCycle();
      int cycleIndex = dstCtrl.cycleAdd();
      addDirection(dd.getDirection(), cycleIndex);

      cd.reset();
      int frameCount = cd.getMaximumFrames();
      final ArrayList<FrameInfo> frameInfo = new ArrayList<>();
      for (int frame = 0; frame < frameCount; frame++) {
        frameInfo.clear();
        int copyCount = isBlurEnabled() ? getBlurInstanceCount() : 1;
        Point centerShift = new Point();
        for (int copyIdx = copyCount - 1; copyIdx >= 0; copyIdx--) {
          if (isBlurEnabled()) {
            centerShift = getBlurInstanceShift(centerShift, dd.getDirection(), copyIdx);
          }
          for (final SegmentDef sd : cd.getCycles()) {
            // checking visibility of sprite types
            boolean skip = (sd.getSpriteType() == SegmentDef.SpriteType.AVATAR) && !getRenderAvatar();
            skip |= (sd.getSpriteType() == SegmentDef.SpriteType.WEAPON) && !getRenderWeapon();
            skip |= (sd.getSpriteType() == SegmentDef.SpriteType.SHIELD) && !getRenderShield();
            skip |= (sd.getSpriteType() == SegmentDef.SpriteType.HELMET) && !getRenderHelmet();
            if (skip) {
              continue;
            }

            entry = sd.getEntry();
            srcCtrl = Objects.requireNonNull(SpriteUtils.loadBamController(entry));
            srcCtrl.cycleSet(sd.getCycleIndex());

            if (sd.getCurrentFrame() >= 0) {
              if (beforeSrcBam != null && !bamControlSet.contains(srcCtrl)) {
                bamControlSet.add(srcCtrl);
                if (!entrySet.contains(entry)) {
                  // apply only once per resource
                  beforeSrcBam.accept(srcCtrl, sd);
                  entrySet.add(entry);
                }
              }
              frameInfo.add(new FrameInfo(srcCtrl, sd, centerShift));
            }
          }
        }

        for (final SegmentDef sd : cd.getCycles()) {
          sd.advance();
        }

        int frameIndex = createFrame(frameInfo.toArray(new FrameInfo[0]), beforeSrcFrame, afterSrcFrame);
        if (afterDstFrame != null) {
          afterDstFrame.accept(dd, frameIndex);
        }
        dstCtrl.cycleAddFrames(cycleIndex, new int[] { frameIndex });
      }
    }
  }

  /**
   * Creates a single creature animation frame from the given array of source frame segments and adds it to the BAM
   * frame list. Each source frame segment can be processed by the specified lambda function before it is drawn onto to
   * the target frame.
   *
   * @param sourceFrames   array of source frame segments to compose.
   * @param beforeSrcFrame optional function that is executed before a source frame segment is drawn onto the target
   *                       frame.
   * @param afterSrcFrame  optional method that is executed right after a source frame segment has been drawn onto the
   *                       target frame.
   * @return the absolute target BAM frame index.
   */
  protected int createFrame(FrameInfo[] sourceFrames, BeforeSourceFrame beforeSrcFrame,
      AfterSourceFrame afterSrcFrame) {
    Rectangle rect;
    if (Objects.requireNonNull(sourceFrames, "Source frame info objects required").length > 0) {
      rect = SpriteUtils.getTotalFrameDimension(sourceFrames);
    } else {
      rect = new Rectangle(0, 0, 1, 1);
    }

    // include personal space region in image size
    rect = SpriteUtils.updateFrameDimension(rect, getPersonalSpaceSize(true));

    // include selection circle in image size
    float circleStrokeSize = getSelectionCircleStrokeSize();
    Dimension dim = getSelectionCircleSize();
    rect = SpriteUtils.updateFrameDimension(rect,
        new Dimension(2 * (dim.width + (int) circleStrokeSize), 2 * (dim.height + (int) circleStrokeSize)));

    // creating target image
    BufferedImage image;
    if (rect.width > 0 && rect.height > 0) {
      image = ColorConvert.createCompatibleImage(rect.width, rect.height, Transparency.TRANSLUCENT);
      Graphics2D g = image.createGraphics();
      try {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(ColorConvert.TRANSPARENT_COLOR);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        // drawing source frames to target image
        for (final FrameInfo fi : sourceFrames) {
          BamV1Control ctrl = fi.getController();
          ctrl.cycleSet(fi.getCycle());
          int frameIdx = fi.getFrame();
          ctrl.cycleSetFrameIndex(frameIdx);
          BufferedImage srcImage = (BufferedImage) ctrl.cycleGetFrame();
          if (beforeSrcFrame != null) {
            srcImage = beforeSrcFrame.apply(fi.getSegmentDefinition(), srcImage, g);
          }
          FrameEntry entry = ctrl.getDecoder().getFrameInfo(ctrl.cycleGetFrameIndexAbsolute());
          int x = -rect.x - entry.getCenterX() + fi.getCenterShift().x;
          int y = -rect.y - entry.getCenterY() + fi.getCenterShift().y;

          if (isBoundingBoxVisible() && entry.getWidth() > 2 && entry.getHeight() > 2) {
            // drawing bounding box around sprite elements
            Stroke oldStroke = g.getStroke();
            Color oldColor = g.getColor();
            Object oldHints = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            try {
              g.setStroke(FrameInfo.STROKE_BOUNDING_BOX);
              g.setColor(FrameInfo.SPRITE_COLOR.getOrDefault(fi.getSegmentDefinition().getSpriteType(),
                  FrameInfo.SPRITE_COLOR_DEFAULT));
              g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
              g.drawRect(x, y, entry.getWidth() - 1, entry.getHeight() - 1);
            } finally {
              g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                  (oldHints != null) ? oldHints : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
              if (oldColor != null) {
                g.setColor(oldColor);
              }
              if (oldStroke != null) {
                g.setStroke(oldStroke);
              }
            }
          }

          g.drawImage(srcImage, x, y, entry.getWidth(), entry.getHeight(), null);

          if (afterSrcFrame != null) {
            afterSrcFrame.accept(fi.getSegmentDefinition(), g);
          }
          ctrl = null;
        }
      } finally {
        g.dispose();
        g = null;
      }
    } else {
      // dummy graphics
      image = ColorConvert.createCompatibleImage(1, 1, Transparency.TRANSLUCENT);
    }

    // setting center point
    int cx = -rect.x;
    int cy = -rect.y;

    return frameAdd(image, new Point(cx, cy));
  }

  /**
   * Calculates the total size of the personal space region.
   *
   * @param scaled whether dimension should be scaled according to search map unit size.
   */
  protected Dimension getPersonalSpaceSize(boolean scaled) {
    int size = Math.max(0, (getPersonalSpace() - 1) | 1);
    if (scaled) {
      return new Dimension(size * 16, size * 12);
    } else {
      return new Dimension(size, size);
    }
  }

  /**
   * Draws the personal space region onto the specified graphics object.
   *
   * @param g      the {@code Graphics2D} instance of the image.
   * @param center center position of the personal space.
   * @param color  the fill color of the drawn region. Specify {@code null} to use a default color.
   * @param alpha  alpha transparency in range [0.0, 1.0] where 0.0 is fully transparent (invisible) and 1.0 is fully
   *               opaque.
   */
  protected void drawPersonalSpace(Graphics2D g, Point center, Color color, float alpha) {
    if (g != null) {
      BufferedImage image = createPersonalSpace(color, alpha);
      g.drawImage(image, center.x - (image.getWidth() / 2), center.y - (image.getHeight() / 2), null);
    }
  }

  /** Creates a bitmap with the personal space tiles. */
  protected BufferedImage createPersonalSpace(Color color, float alpha) {
    // preparations
    if (color == null) {
      color = new Color(224, 0, 224);
    }
    alpha = Math.max(0.0f, Math.min(1.0f, alpha)); // clamping alpha to [0.0, 1.0]
    color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (255 * alpha));

    // creating personal space pattern (unscaled)
    Dimension dim = getPersonalSpaceSize(false);
    if (dim.width == 0 || dim.height == 0) {
      // personal space is not defined
      return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }
    BufferedImage image = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
    int[] bitmap = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    int cx = dim.width / 2;
    int cy = dim.height / 2;
    int c = color.getRGB();
    float maxDist = (dim.width / 2.0f) * (dim.height / 2.0f);
    for (int y = 0; y < dim.height; y++) {
      for (int x = 0; x < dim.width; x++) {
        int ofs = y * dim.width + x;
        int dx = (cx - x) * (cx - x);
        int dy = (cy - y) * (cy - y);
        if (dx + dy < maxDist) {
          bitmap[ofs] = c;
        }
      }
    }

    // scaling up to search map unit size
    dim = getPersonalSpaceSize(true);
    BufferedImage retVal = new BufferedImage(dim.width, dim.height, image.getType());
    Graphics2D g = retVal.createGraphics();
    try {
      g.setComposite(AlphaComposite.Src);
      Object oldHints = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(image, 0, 0, dim.width, dim.height, null);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          (oldHints != null) ? oldHints : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    } finally {
      g.dispose();
      g = null;
    }

    return retVal;
  }

  /** Calculates the horizontal and vertical radius of the selection circle (ellipse). */
  protected Dimension getSelectionCircleSize() {
    Dimension dim = new Dimension();
    dim.width = Math.max(0, getEllipse());
    if (dim.width == 0) {
      return dim;
    }
    if (isSelectionCircleBitmap()) {
      dim.width += 2; // compensation for missing stroke size
    }
    dim.height = dim.width * 4 / 7; // ratio 1.75
    if (dim.height % 7 > 3) {
      // rounding up
      dim.height++;
    }
    return dim;
  }

  /**
   * Determines a circle stroke size relative to the circle size. Empty circles or bitmap circles have no stroke size.
   */
  protected float getSelectionCircleStrokeSize() {
    float circleStrokeSize = 0.0f;
    if (!isSelectionCircleBitmap() && getEllipse() > 0) {
      // thickness relative to circle size
      circleStrokeSize = Math.max(1.0f, (float) (Math.floor(Math.sqrt(getEllipse()) / 2.0)));
    }

    return circleStrokeSize;
  }

  /**
   * Draws a selection circle onto the specified graphics object.
   *
   * @param g          the {@code Graphics2D} instance of the image.
   * @param center     center position of the circle.
   * @param strokeSize the thickness of the selection circle.
   */
  protected void drawSelectionCircle(Graphics2D g, Point center, float strokeSize) {
    if (g != null) {
      Dimension dim = getSelectionCircleSize();
      if (dim.width == 0 || dim.height == 0) {
        return;
      }

      Image image;
      if (isSelectionCircleBitmap()) {
        // fetching ornate selection circle image
        image = getCreatureInfo().isStatusPanic() ? SpriteUtils.getAllegianceImage(-1)
            : SpriteUtils.getAllegianceImage(getCreatureInfo().getAllegiance());
      } else {
        if (imageCircle == null) {
          // pregenerating circle graphics
          int stroke = (int) Math.ceil(strokeSize);
          imageCircle = ColorConvert.createCompatibleImage((dim.width + stroke) * 2 + 1, (dim.height + stroke) * 2 + 1,
              true);
          Graphics2D g2 = imageCircle.createGraphics();
          try {
            Color color = getCreatureInfo().isStatusPanic() ? SpriteUtils.getAllegianceColor(-1)
                : SpriteUtils.getAllegianceColor(getCreatureInfo().getAllegiance());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(strokeSize));
            g2.drawOval((imageCircle.getWidth() / 2) - dim.width, (imageCircle.getHeight() / 2) - dim.height,
                2 * dim.width, 2 * dim.height);
          } finally {
            g2.dispose();
            g2 = null;
          }
        }
        image = imageCircle;
        // adjusting drawing size
        dim.width = image.getWidth(null) / 2;
        dim.height = image.getHeight(null) / 2;
      }

      // drawing selection circle
      Object oldHints = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(image, center.x - dim.width, center.y - dim.height, 2 * dim.width, 2 * dim.height, null);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          (oldHints != null) ? oldHints : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }
  }

  /** Returns whether creature shadow is semi-transparent. */
  protected boolean isTransparentShadow() {
    return transparentShadow;
  }

  /** Sets whether creature shadow is semi-transparent. */
  protected void setTransparentShadow(boolean b) {
    if (transparentShadow != b) {
      transparentShadow = b;
      spriteChanged();
    }
  }

  /**
   * Translates the specified color location index into a palette color offset.
   *
   * @param locationIndex the location to translate.
   * @return the resulting palette color offset. Returns -1 if location is not supported.
   */
  protected int getColorOffset(int locationIndex) {
    int retVal = -1;
    if (locationIndex >= 0 && locationIndex < 7) {
      retVal = 4 + locationIndex * 12;
    }
    return retVal;
  }

  /**
   * Returns the palette data for the specified color entry.
   *
   * @param colorIndex the color entry.
   * @return palette data as int array. Returns {@code null} if palette data could not be determined.
   */
  protected int[] getColorData(int colorIndex, boolean allowRandom) {
    int[] retVal = null;
    try {
      retVal = SpriteUtils.getColorGradient(colorIndex, allowRandom);
    } catch (Exception e) {
      Logger.error(e);
    }
    return retVal;
  }

  /**
   * Replaces false colors with color ranges defined in the associated CRE resource.
   *
   * @param control the BAM controller.
   */
  protected void applyFalseColors(BamV1Control control, SegmentDef sd) {
    if (control == null || sd == null) {
      return;
    }

    // preparations
    final Map<Integer, int[]> colorRanges = new HashMap<>();
    for (int loc = 0; loc < 7; loc++) {
      int ofs = getColorOffset(loc);
      Couple<Integer, Boolean> colorInfo = getCreatureInfo().getEffectiveColorValue(sd.getSpriteType(), loc);
      int colIdx = colorInfo.getValue0();
      boolean allowRandom = colorInfo.getValue1();
      if (ofs > 0 && colIdx >= 0) {
        int[] range = getColorData(colIdx, allowRandom);
        if (range != null) {
          colorRanges.put(ofs, range);
        }
      }
    }

    // Special: Off-hand weapon uses weapon colors
    if (sd.getSpriteType() == SegmentDef.SpriteType.SHIELD) {
      ItemInfo itemInfo = getCreatureInfo().getEquippedShield();
      if (itemInfo != null && itemInfo.getSlotType() == ItemInfo.SlotType.WEAPON) {
        EffectInfo effectInfo = itemInfo.getEffectInfo();
        List<EffectInfo.Effect> fxList = effectInfo.getEffects(getCreatureInfo(), SegmentDef.SpriteType.WEAPON,
            EffectInfo.OPCODE_SET_COLOR);
        for (final EffectInfo.Effect fx : fxList) {
          int loc = fx.getParameter2() & 0xf;
          int ofs = getColorOffset(loc);
          if (ofs > 0) {
            int[] range = getColorData(fx.getParameter1(), false);
            if (range != null) {
              colorRanges.put(ofs, range);
            }
          }
        }
      }
    }

    // applying colors
    int[] palette = control.getCurrentPalette();
    for (final Integer ofs : colorRanges.keySet()) {
      // replacing base ranges
      final int[] range = colorRanges.get(ofs);
      palette = SpriteUtils.replaceColors(palette, range, ofs, range.length, false);
    }

    if (getAnimationType() != AnimationInfo.Type.MONSTER_PLANESCAPE) {
      // preparing offset array
      final int srcOfs = 4;
      final int dstOfs = 88;
      final int srcLen = 12;
      final int dstLen = 8;
      final int[] offsets = new int[colorRanges.size()];
      for (int i = 0; i < offsets.length; i++) {
        offsets[i] = srcOfs + i * srcLen;
      }

      // calculating mixed ranges
      int k = 0;
      for (int i = 0; i < offsets.length - 1; i++) {
        int ofs1 = offsets[i];
        for (int j = i + 1; j < offsets.length; j++, k++) {
          int ofs2 = offsets[j];
          int ofs3 = dstOfs + k * dstLen;
          palette = SpriteUtils.interpolateColors(palette, ofs1, ofs2, srcLen, ofs3, dstLen, false);
        }
      }

      // fixing special palette entries
      palette[2] = 0xFF000000;
      palette[3] = 0xFF000000;
    }

    control.setExternalPalette(palette, control.getTransparencyIndex(true));
  }

  /**
   * Replaces false colors with special color effects applied to the CRE resource. Currently supported:
   * stoneskin/petrification, frozen state, burned state.
   *
   * @param control the BAM controller.
   */
  protected void applyColorEffects(BamV1Control control, SegmentDef sd) {
    if (control == null || sd == null || getAnimationType() == AnimationInfo.Type.MONSTER_PLANESCAPE) {
      return;
    }

    boolean isStoneEffect = getCreatureInfo().isStoneEffect();
    boolean isFrozenEffect = getCreatureInfo().isFrozenEffect();
    boolean isBurnedEffect = getCreatureInfo().isBurnedEffect();

    if (isStoneEffect || isFrozenEffect) {
      // isStoneEffect: includes stoneskin effect, petrification/stone death status
      // isFrozenEffect: includes frozen death status
      int colorIdx = isStoneEffect ? 72 : 71;
      int[] range = getColorData(colorIdx, false);
      int[] palette = control.getCurrentPalette();

      // replacing base ranges
      for (int i = 0; i < 7; i++) {
        int ofs = 4 + (i * range.length);
        palette = SpriteUtils.replaceColors(palette, range, ofs, range.length, false);
      }

      // calculating mixed ranges
      int k = 0;
      for (int i = 0; i < 6; i++) {
        int ofs1 = 4 + (i * 12);
        for (int j = i + 1; j < 7; j++, k++) {
          int ofs2 = 4 + (j * 12);
          int ofs3 = 88 + (k * 8);
          palette = SpriteUtils.interpolateColors(palette, ofs1, ofs2, 12, ofs3, 8, false);
        }
      }

      control.setExternalPalette(palette, control.getTransparencyIndex(true));
    } else if (isBurnedEffect) {
      // isBurnedEffect: includes flame death status
      int opcode = 51;
      int color = 0x4b4b4b;
      int[] palette = control.getCurrentPalette();
      palette = SpriteUtils.tintColors(palette, 2, 254, opcode, color);
      control.setExternalPalette(palette, control.getTransparencyIndex(true));
    }

  }

  /**
   * Modifies BAM palette with tint colors from selected effect opcodes.
   *
   * @param control the BAM controller.
   */
  protected void applyColorTint(BamV1Control control, SegmentDef sd) {
    if (control == null || sd == null || getAnimationType() == AnimationInfo.Type.MONSTER_PLANESCAPE) {
      return;
    }

    int[] palette = control.getCurrentPalette();
    Couple<Integer, Integer> fullTint = Couple.with(-1, -1); // stores info for later
    // color locations >= 0: affects only false color BAMs directly; data is stored for full palette tint though
    for (int loc = 0; loc < 7; loc++) {
      int ofs = getColorOffset(loc);
      Couple<Integer, Integer> colorInfo = getCreatureInfo().getEffectiveTintValue(sd.getSpriteType(), loc);
      int opcode = colorInfo.getValue0();
      int color = colorInfo.getValue1();
      if (ofs > 0 && opcode >= 0 && color >= 0) {
        // applying tint to color range
        if (isFalseColor()) {
          palette = SpriteUtils.tintColors(palette, ofs, 12, opcode, color);
        } else {
          fullTint.setValue0(colorInfo.getValue0());
          fullTint.setValue1(colorInfo.getValue1());
        }
      }
    }

    if (isFalseColor()) {
      // preparing offset array
      final int srcOfs = 4;
      final int dstOfs = 88;
      final int srcLen = 12;
      final int dstLen = 8;
      final int[] offsets = new int[7];
      for (int i = 0; i < offsets.length; i++) {
        offsets[i] = srcOfs + i * srcLen;
      }

      // calculating mixed ranges
      int k = 0;
      for (int i = 0; i < offsets.length - 1; i++) {
        int ofs1 = offsets[i];
        for (int j = i + 1; j < offsets.length; j++, k++) {
          int ofs2 = offsets[j];
          int ofs3 = dstOfs + k * dstLen;
          palette = SpriteUtils.interpolateColors(palette, ofs1, ofs2, srcLen, ofs3, dstLen, false);
        }
      }
    }

    // color location -1: affects whole palette (except transparency and shadow color)
    Couple<Integer, Integer> colorInfo = getCreatureInfo().getEffectiveTintValue(sd.getSpriteType(), -1);
    if (colorInfo.getValue0() >= 0 && colorInfo.getValue1() >= 0) {
      fullTint.setValue0(colorInfo.getValue0());
      fullTint.setValue1(colorInfo.getValue1());
    }
    int opcode = fullTint.getValue0();
    int color = fullTint.getValue1();
    if (opcode >= 0 && color >= 0) {
      // applying tint to whole palette
      palette = SpriteUtils.tintColors(palette, 2, 254, opcode, color);
    }

    control.setExternalPalette(palette, control.getTransparencyIndex(true));
  }

  /**
   * The specified frame is mirrored horizontally. Both pixel data and center point are adjusted.
   *
   * @param frameIndex absolute frame index in the BAM frame list.
   */
  protected void flipImageHorizontal(int frameIndex) {
    PseudoBamFrameEntry frame = getFrameInfo(frameIndex);
    // flipping image horizontally
    BufferedImage image = frame.getFrame();
    AffineTransform at = AffineTransform.getScaleInstance(-1, 1);
    at.translate(-image.getWidth(), 0);
    AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
    image = op.filter(image, null);
    // updating frame data
    frame.setFrame(image);
    frame.setCenterX(frame.getWidth() - frame.getCenterX() - 1);
  }

  /**
   * Applies translucency to the specified paletted image.
   *
   * @param control         the BAM controller.
   * @param minTranslucency the minimum amount of translucency to apply.
   */
  protected void applyTranslucency(BamV1Control control, int minTranslucency) {
    if (control != null) {
      int alpha = minTranslucency;
      if (isTranslucencyEnabled()) {
        int value = getCreatureInfo().getEffectiveTranslucency();
        if (value > 0) {
          alpha = Math.min(alpha, value);
        }
      }
      int[] palette = control.getCurrentPalette();

      // shadow color (alpha relative to semi-transparency of shadow)
      int alphaShadow = 255 - (palette[1] >>> 24);
      alphaShadow = alpha * alphaShadow / 255;
      alphaShadow <<= 24; // setting alpha mask
      palette[1] = alphaShadow | (palette[1] & 0x00ffffff);

      // creature colors
      alpha <<= 24; // setting alpha mask
      for (int i = 2; i < palette.length; i++) {
        palette[i] = alpha | (palette[i] & 0x00ffffff);
      }

      control.setExternalPalette(palette, control.getTransparencyIndex(true));
    }
  }

  /**
   * Returns whether the current CRE resource provides an animation that is compatible with the {@code SpriteDecoder}
   * class.
   *
   * @return {@code true} if the animation type of the CRE is compatible with this {@code SpriteDecoder} instance.
   *         Returns {@code false} otherwise.
   */
  protected boolean isMatchingAnimationType() {
    boolean retVal = false;

    List<String> names = getAnimationFiles(true);
    if (!names.isEmpty()) {
      retVal = names.stream().allMatch(ResourceFactory::resourceExists);
    }

    return retVal;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(animationChanged, attributesMap, autoApplyChanges, blurEnabled, creInfo,
        currentSequence, directionMap, ini, paletteReplacementEnabled, renderSpriteAvatar, renderSpriteHelmet,
        renderSpriteShield, renderSpriteWeapon, selectionCircleBitmap, showBoundingBox, showCircle, showPersonalSpace,
        tintEnabled, translucencyEnabled, transparentShadow);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    SpriteDecoder other = (SpriteDecoder) obj;
    return animationChanged == other.animationChanged && Objects.equals(attributesMap, other.attributesMap)
        && autoApplyChanges == other.autoApplyChanges && blurEnabled == other.blurEnabled
        && Objects.equals(creInfo, other.creInfo) && currentSequence == other.currentSequence
        && Objects.equals(directionMap, other.directionMap) && Objects.equals(ini, other.ini)
        && paletteReplacementEnabled == other.paletteReplacementEnabled
        && renderSpriteAvatar == other.renderSpriteAvatar && renderSpriteHelmet == other.renderSpriteHelmet
        && renderSpriteShield == other.renderSpriteShield && renderSpriteWeapon == other.renderSpriteWeapon
        && selectionCircleBitmap == other.selectionCircleBitmap && showBoundingBox == other.showBoundingBox
        && showCircle == other.showCircle && showPersonalSpace == other.showPersonalSpace
        && tintEnabled == other.tintEnabled && translucencyEnabled == other.translucencyEnabled
        && transparentShadow == other.transparentShadow;
  }

  // -------------------------- INNER CLASSES --------------------------

  /**
   * Specialized controller for creature animations.
   */
  public static class SpriteBamControl extends PseudoBamControl {
    protected SpriteBamControl(SpriteDecoder decoder) {
      super(decoder);
    }

    @Override
    public SpriteDecoder getDecoder() {
      return (SpriteDecoder) super.getDecoder();
    }

    /**
     * A convenience method that draws personal space marker and/or selection circle onto the canvas specified by the
     * {@code Graphics2D} instance based on current decoder settings. Frame dimension and center position are taken into
     * account and based on the currently selected cycle frame.
     *
     * @param g      the {@code Graphics2D} instance used to render the graphics.
     * @param offset amount of pixels to move the center point by. Specify {@code null} for no change.
     */
    public void getVisualMarkers(Graphics2D g, Point offset) {
      getVisualMarkers(g, offset, cycleGetFrameIndex(), getDecoder().isPersonalSpaceVisible(),
          getDecoder().isSelectionCircleEnabled());
    }

    /**
     * A convenience method that draws personal space marker and/or selection circle onto the canvas specified by the
     * {@code Graphics2D} instance. Frame dimension and center position are taken into account and based on the
     * currently selected cycle frame.
     *
     * @param g                   the {@code Graphics2D} instance used to render the graphics.
     * @param offset              amount of pixels to move the center point by. Specify {@code null} for no change.
     * @param drawPersonalSpace   whether to draw the personal space marker.
     * @param drawSelectionCircle whether to draw the selection circle.
     */
    public void getVisualMarkers(Graphics2D g, Point offset, boolean drawPersonalSpace, boolean drawSelectionCircle) {
      getVisualMarkers(g, offset, cycleGetFrameIndex(), drawPersonalSpace, drawSelectionCircle);
    }

    /**
     * A convenience method that draws personal space marker and/or selection circle onto the canvas specified by the
     * {@code Graphics2D} instance based on current decoder settings. Frame dimension and center position are taken into
     * account by the specified relative frame index.
     *
     * @param g        the {@code Graphics2D} instance used to render the graphics.
     * @param offset   amount of pixels to move the center point by. Specify {@code null} for no change.
     * @param frameIdx the frame index relative to current cycle.
     */
    public void getVisualMarkers(Graphics2D g, Point offset, int frameIdx) {
      getVisualMarkers(g, offset, frameIdx, getDecoder().isPersonalSpaceVisible(),
          getDecoder().isSelectionCircleEnabled());
    }

    /**
     * A convenience method that draws personal space marker and/or selection circle onto the canvas specified by the
     * {@code Graphics2D} instance. Frame dimension and center position are taken into account by the specified relative
     * frame index.
     *
     * @param g                   the {@code Graphics2D} instance used to render the graphics.
     * @param offset              amount of pixels to move the center point by. Specify {@code null} for no change.
     * @param frameIdx            the frame index relative to current cycle.
     * @param drawPersonalSpace   whether to draw the personal space marker.
     * @param drawSelectionCircle whether to draw the selection circle.
     */
    public void getVisualMarkers(Graphics2D g, Point offset, int frameIdx, boolean drawPersonalSpace,
        boolean drawSelectionCircle) {
      if (g == null || (!drawPersonalSpace && !drawSelectionCircle)) {
        return;
      }

      frameIdx = cycleGetFrameIndexAbsolute(frameIdx);
      if (frameIdx < 0) {
        return;
      }

      // getting frame dimension and center
      PseudoBamFrameEntry entry = getDecoder().getFrameInfo(frameIdx);
      Point center;
      int w, h;
      if (getMode() == BamDecoder.BamControl.Mode.SHARED) {
        updateSharedBamSize();
        Dimension d = getSharedDimension();
        center = getSharedOrigin();
        center.x = -center.x;
        center.y = -center.y;
        w = d.width;
        h = d.height;
      } else {
        w = entry.getWidth();
        h = entry.getHeight();
        center = new Point(entry.getCenterX(), entry.getCenterY());
      }

      if (w <= 0 || h <= 0) {
        return;
      }

      if (offset != null) {
        center.x += offset.x;
        center.y += offset.y;
      }

      // drawing markers
      Composite comp = g.getComposite();
      g.setComposite(AlphaComposite.SrcOver);
      if (drawPersonalSpace) {
        getDecoder().drawPersonalSpace(g, center, null, 0.5f);
      }
      if (drawSelectionCircle) {
        getDecoder().drawSelectionCircle(g, center, getDecoder().getSelectionCircleStrokeSize());
      }
      if (comp != null) {
        g.setComposite(comp);
      }
    }
  }

  /**
   * Represents an operation that is called once per source BAM resource when creating a creature animation.
   */
  public interface BeforeSourceBam {
    /**
     * Performs this operation on the given arguments.
     *
     * @param control the {@code BamV1Control} instance of the source BAM
     * @param sd      the {@code SegmentDef} instance describing the source BAM.
     */
    void accept(BamV1Control control, SegmentDef sd);
  }

  /**
   * Represents a function that is called for each source frame before it is drawn onto the destination image.
   */
  public interface BeforeSourceFrame {
    /**
     * Performs this function on the given arguments.
     *
     * @param sd       the {@code SegmentDef} structure describing the given source frame.
     * @param srcImage {@code BufferedImage} object of the the source frame
     * @param g        the {@code Graphics2D} object of the destination image.
     * @return the updated source frame image
     */
    BufferedImage apply(SegmentDef sd, BufferedImage srcImage, Graphics2D g);
  }

  /**
   * Represents an operation that is called for each source frame after it has been drawn onto the destination image. It
   * can be used to clean up modifications made to the {@code Graphics2D} instance in the {@code BeforeSourceFrame}
   * function.
   */
  public interface AfterSourceFrame {
    /**
     * Performs this operation on the given arguments.
     *
     * @param sd the {@code SegmentDef} structure describing the current source frame.
     * @param g  the {@code Graphics2D} object of the destination image.
     */
    void accept(SegmentDef sd, Graphics2D g);
  }

  /**
   * Represents an operation that is called for each destination frame after it has been created.
   */
  public interface AfterDestFrame {
    /**
     * Performs this operation on the given arguments.
     *
     * @param dd       the {@code DirDef} object defining the current destination cycle
     * @param frameIdx the absolute destination BAM frame index.
     */
    void accept(DirDef dd, int frameIdx);
  }
}
