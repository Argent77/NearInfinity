// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.spl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.infinity.datatype.AbstractBitmap;
import org.infinity.datatype.Bitmap;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.EffectType;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.PriTypeBitmap;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SecTypeBitmap;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.datatype.UpdateEvent;
import org.infinity.datatype.UpdateListener;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractAbility;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.Effect;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.StructEntry;
import org.infinity.resource.effects.BaseOpcode;
import org.infinity.resource.itm.ItmResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.spl.generator.EffectConfig;
import org.infinity.resource.spl.generator.GeneratorAttribute;
import org.infinity.resource.spl.generator.GeneratorConfig;
import org.infinity.resource.spl.generator.GeneratorDialog;
import org.infinity.search.SearchOptions;
import org.infinity.util.Logger;
import org.infinity.util.StringTable;
import org.infinity.util.io.ByteBufferOutputStream;
import org.infinity.util.io.StreamUtils;

/**
 * This resource describes a "spell". Spells include mage spells, priest spells, innate abilities, special abilities and
 * effects used for game advancement (e.g. animation effects, custom spells). SPL files have a similar structure to
 * {@link ItmResource ITM} files.
 * <p>
 * SPL files consist of a main header, zero or more extended headers (each containing zero or more feature blocks) and
 * zero or more casting feature blocks. All the feature blocks are stored as a continuous data segment, with each
 * extended header containing an offset into this data, and the main header containing an offset into this data for the
 * casting feature blocks.
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/spl_v1.htm">
 *      https://gibberlings3.github.io/iesdp/file_formats/ie_formats/spl_v1.htm</a>
 */
public final class SplResource extends AbstractStruct
    implements Resource, HasChildStructs, HasViewerTabs, UpdateListener, ActionListener {
  // SPL-specific field labels
  public static final String SPL_NAME                             = "Spell name";
  public static final String SPL_NAME_IDENTIFIED                  = ItmResource.ITM_NAME_IDENTIFIED + SUFFIX_UNUSED;
  public static final String SPL_CASTING_SOUND                    = "Casting sound";
  public static final String SPL_FLAGS                            = "Flags";
  public static final String SPL_TYPE                             = "Spell type";
  public static final String SPL_EXCLUSION_FLAGS                  = "Exclusion flags";
  public static final String SPL_CASTING_ANIMATION                = "Casting animation";
  public static final String SPL_MINIMUM_LEVEL                    = "Minimum level (unused)";
  public static final String SPL_PRIMARY_TYPE                     = "Primary type (school)";
  public static final String SPL_SECONDARY_TYPE                   = "Secondary type";
  public static final String SPL_LEVEL                            = "Spell level";
  public static final String SPL_ICON                             = "Spell icon";
  public static final String SPL_ICON_GROUND                      = "Ground icon";
  public static final String SPL_DESCRIPTION                      = "Spell description";
  public static final String SPL_DESCRIPTION_IDENTIFIED           = ItmResource.ITM_DESCRIPTION_IDENTIFIED + SUFFIX_UNUSED;
  public static final String SPL_DESCRIPTION_IMAGE                = ItmResource.ITM_DESCRIPTION_IMAGE;
  public static final String SPL_OFFSET_ABILITIES                 = ItmResource.ITM_OFFSET_ABILITIES;
  public static final String SPL_NUM_ABILITIES                    = ItmResource.ITM_NUM_ABILITIES;
  public static final String SPL_OFFSET_EFFECTS                   = ItmResource.ITM_OFFSET_EFFECTS;
  public static final String SPL_FIRST_EFFECT_INDEX               = ItmResource.ITM_FIRST_EFFECT_INDEX;
  public static final String SPL_NUM_GLOBAL_EFFECTS               = ItmResource.ITM_NUM_GLOBAL_EFFECTS;
  public static final String SPL_SPELL_DURATION_ROUNDS_PER_LEVEL  = "Spell duration rounds/level";
  public static final String SPL_SPELL_DURATION_BASE              = "Spell duration rounds base";

  public static final String[] SPELL_TYPE_ARRAY = { "Special", "Wizard", "Priest", "Psionic", "Innate", "Bard song" };

  public static final String[] ANIM_ARRAY = { "None", "Fire aqua", "Fire blue", "Fire gold", "Fire green",
      "Fire magenta", "Fire purple", "Fire red", "Fire white", "Necromancy", "Alteration", "Enchantment", "Abjuration",
      "Illusion", "Conjuration", "Invocation", "Divination", "Fountain aqua", "Fountain black", "Fountain blue",
      "Fountain gold", "Fountain green", "Fountain magenta", "Fountain purple", "Fountain red", "Fountain white",
      "Swirl aqua", "Swirl black", "Swirl blue", "Swirl gold", "Swirl green", "Swirl magenta", "Swirl purple",
      "Swirl red", "Swirl white" };

  public static final String[] ANIM_PST_ARRAY = { "None", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
      "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "Abjuration", "Alteration",
      "Conjuration", "Enchantment", "Divination", "Illusion", "Invocation", "Necromancy", "Innate" };

  public static final String[] SPELL_FLAGS_ARRAY = { "No flags set", "", "", "", "", "", "", "", "", "",
      "EE: Break Sanctuary", "Hostile;Breaks Sanctuary and Invisibility", "No LOS required", "Allow spotting",
      "Outdoors only", "Ignore dead/wild magic", "Ignore wild surge", "Non-combat ability", "", "", "", "", "", "", "",
      "EE/Ex: Can target invisible", "EE/Ex: Castable when silenced" };

  public static final String[] SPELL_FLAGS_2_ARRAY = { "No flags set", "", "", "", "", "", "", "", "", "", "",
      "Hostile", "No LOS required", "Allow spotting", "Outdoors only", "Simplified duration", "Trigger/Contingency", "",
      "", "Non-combat ability (?)", "", "", "", "", "", "", "" };

  public static final String[] EXCLUDE_ARRAY = { "None" };

  public static final String[] EXCLUDE_WIZARD_ARRAY = { "None", "", "", "", "", "", "", "Abjurer", "Conjurer",
      "Diviner", "Enchanter", "Illusionist", "Invoker", "Necromancer", "Transmuter",
      "Generalist;Includes trueclass mages, sorcerers and bards", "", "", "", "", "", "", "", "", "", "", "", "", "",
      "", "", "", "" };

  public static final String[] EXCLUDE_PRIEST_ARRAY = { "None",
      "Chaotic;Includes Chaotic Good, Chaotic Neutral and Chaotic Evil",
      "Evil;Includes Lawful Evil, Neutral Evil and Chaotic Evil",
      "Good;Includes Lawful Good, Neutral Good and Chaotic Good",
      "... Neutral;Includes Lawful Neutral, True Neutral and Chaotic Neutral",
      "Lawful;Includes Lawful Good, Lawful Neutral and Lawful Evil",
      "Neutral ...;Includes Neutral Good, True Neutral and Neutral Evil", "", "", "", "", "", "", "", "", "", "", "",
      "", "", "", "", "", "", "", "", "", "", "", "", "", "Cleric/Paladin", "Druid/Ranger/Shaman" };

  public static final String[] EXCLUDE_COMBINED_ARRAY = { "None", "Chaotic/Berserker", "Evil/Wizard slayer",
      "Good/Kensai", "... Neutral/Cavalier", "Lawful/Inquisitor", "Neutral .../Undead hunter", "Abjurer", "Conjurer",
      "Diviner", "Enchanter", "Illusionist", "Invoker", "Necromancer", "Transmuter", "Generalist", "Archer", "Stalker",
      "Beastmaster", "Assasin", "Bounty hunter", "Swashbuckler", "Blade", "Jester", "Skald", "Cleric of Talos",
      "Cleric of Helm", "Cleric of Lathander", "Totemic druid", "Shapeshifter", "Avenger", "Cleric/Paladin/Barbarian",
      "Druid/Ranger/Wild mage" };

  private StructHexViewer hexViewer;
  private JButton bGenerateAbilities;

  public static String getSearchString(InputStream is) throws IOException {
    is.skip(8);
    return StringTable.getStringRef(StreamUtils.readInt(is)).trim();
  }

  public SplResource(ResourceEntry entry) throws Exception {
    super(entry);
  }

  @Override
  public AddRemovable[] getPrototypes() throws Exception {
    return new AddRemovable[] { new Ability(), new Effect() };
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception {
    return entry;
  }

  @Override
  public int getViewerTabCount() {
    return 2;
  }

  @Override
  public String getViewerTabName(int index) {
    switch (index) {
      case 0:
        return StructViewer.TAB_VIEW;
      case 1:
        return StructViewer.TAB_RAW;
    }
    return null;
  }

  @Override
  public JComponent getViewerTab(int index) {
    switch (index) {
      case 0: {
        JScrollPane scroll = new JScrollPane(new Viewer(this));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
      }
      case 1: {
        if (hexViewer == null) {
          hexViewer = new StructHexViewer(this, new BasicColorMap(this, true));
        }
        return hexViewer;
      }
    }
    return null;
  }

  @Override
  public boolean viewerTabAddedBefore(int index) {
    return (index == 0);
  }

  @Override
  public void write(OutputStream os) throws IOException {
    super.write(os);
    for (final StructEntry o : getFields()) {
      if (o instanceof Ability) {
        Ability a = (Ability) o;
        a.writeEffects(os);
      }
    }
  }

  // --------------------- Begin Interface UpdateListener ---------------------

  @Override
  public boolean valueUpdated(UpdateEvent event) {
    if (event.getSource() instanceof AbstractBitmap<?>
        && SPL_TYPE.equals(((AbstractBitmap<?>) event.getSource()).getName())) {
      Flag curFlags = (Flag) getAttribute(SPL_EXCLUSION_FLAGS);
      if (curFlags != null) {
        int type = ((IsNumeric) event.getSource()).getValue();
        int size = curFlags.getSize();
        int offset = curFlags.getOffset();
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).putInt(curFlags.getValue());
        Flag newFlags = new Flag(b, 0, size, SPL_EXCLUSION_FLAGS, getExclusionArray(type));
        newFlags.setOffset(offset);
        replaceField(newFlags);
        return true;
      }
    }
    return false;
  }

  // --------------------- End Interface UpdateListener ---------------------

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == bGenerateAbilities) {
      try {
        final GeneratorConfig config = GeneratorDialog.getConfiguration(this);
        if (config != null) {
          performAbilityGeneration(config);
        }
      } catch (Exception e) {
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(getViewer()), e.getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  @Override
  protected void viewerInitialized(StructViewer viewer) {
    viewer.addTabChangeListener(hexViewer);

    final ButtonPanel buttonPanel = viewer.getButtonPanel();
    int idx = buttonPanel.getControlPosition(buttonPanel.getControlByType(ButtonPanel.Control.PRINT));
    if (idx < 0) {
      idx = 5;
    }
    bGenerateAbilities = new JButton("Generate abilities...", Icons.ICON_HISTORY_16.getIcon());
    bGenerateAbilities.setToolTipText("Autogenerate level-scaled spell abilities");
    bGenerateAbilities.addActionListener(this);
    buttonPanel.addControl(idx, bGenerateAbilities, ButtonPanel.Control.CUSTOM_1);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype) {
    if (datatype instanceof Effect) {
      for (final StructEntry o : getFields()) {
        if (o instanceof Ability)
          ((Ability) o).incEffectsIndex(1);
      }
    } else if (datatype instanceof Ability) {
      int effectCount = ((IsNumeric) getAttribute(SPL_NUM_GLOBAL_EFFECTS)).getValue();
      for (final StructEntry o : getFields()) {
        if (o instanceof Ability) {
          Ability ability = (Ability) o;
          ability.setEffectsIndex(effectCount);
          effectCount += ability.getEffectsCount();
        }
      }
    }
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype) {
    super.datatypeAddedInChild(child, datatype);
    incAbilityEffects(child, datatype, 1);
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype) {
    if (datatype instanceof Effect) {
      for (final StructEntry o : getFields()) {
        if (o instanceof Ability)
          ((Ability) o).incEffectsIndex(-1);
      }
    } else if (datatype instanceof Ability) {
      int effectCount = ((IsNumeric) getAttribute(SPL_NUM_GLOBAL_EFFECTS)).getValue();
      for (final StructEntry o : getFields()) {
        if (o instanceof Ability) {
          Ability ability = (Ability) o;
          ability.setEffectsIndex(effectCount);
          effectCount += ability.getEffectsCount();
        }
      }
    }
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype) {
    super.datatypeRemovedInChild(child, datatype);
    incAbilityEffects(child, datatype, -1);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    addField(new TextString(buffer, offset, 4, COMMON_SIGNATURE));
    final TextString version = new TextString(buffer, offset + 4, 4, COMMON_VERSION);
    addField(version);
    addField(new StringRef(buffer, offset + 8, SPL_NAME));
    addField(new StringRef(buffer, offset + 12, SPL_NAME_IDENTIFIED));
    addField(new ResourceRef(buffer, offset + 16, SPL_CASTING_SOUND, "WAV"));
    if (version.getText().equalsIgnoreCase("V2.0")) {
      addField(new Flag(buffer, offset + 24, 4, SPL_FLAGS, SPELL_FLAGS_2_ARRAY));
    } else {
      addField(new Flag(buffer, offset + 24, 4, SPL_FLAGS, SPELL_FLAGS_ARRAY));
    }
    final Bitmap spellType = new Bitmap(buffer, offset + 28, 2, SPL_TYPE, SPELL_TYPE_ARRAY); // 0x1c
    spellType.addUpdateListener(this);
    addField(spellType);
    addField(new Flag(buffer, offset + 30, 4, SPL_EXCLUSION_FLAGS, getExclusionArray(spellType.getValue()))); // 0x1e
    if (Profile.getGame() == Profile.Game.PST || Profile.getGame() == Profile.Game.PSTEE) {
      addField(new Bitmap(buffer, offset + 34, 2, SPL_CASTING_ANIMATION, ANIM_PST_ARRAY)); // 0x22
    } else {
      addField(new Bitmap(buffer, offset + 34, 2, SPL_CASTING_ANIMATION, ANIM_ARRAY)); // 0x22
    }
    addField(new Unknown(buffer, offset + 36, 1, COMMON_UNUSED)); // 0x24
    addField(new PriTypeBitmap(buffer, offset + 37, 1, SPL_PRIMARY_TYPE)); // 0x25
    addField(new Unknown(buffer, offset + 38, 1, COMMON_UNUSED));
    addField(new SecTypeBitmap(buffer, offset + 39, 1, SPL_SECONDARY_TYPE)); // 0x27
    addField(new Unknown(buffer, offset + 40, 12, COMMON_UNUSED));
    addField(new DecNumber(buffer, offset + 52, 4, SPL_LEVEL));
    addField(new Unknown(buffer, offset + 56, 2, COMMON_UNUSED));
    addField(new ResourceRef(buffer, offset + 58, SPL_ICON, "BAM"));
    addField(new Unknown(buffer, offset + 66, 2, COMMON_UNUSED));
    addField(new Unknown(buffer, offset + 68, 8, COMMON_UNUSED));
    addField(new Unknown(buffer, offset + 76, 4, COMMON_UNUSED));
    addField(new StringRef(buffer, offset + 80, SPL_DESCRIPTION));
    addField(new StringRef(buffer, offset + 84, SPL_DESCRIPTION_IDENTIFIED));
    addField(new ResourceRef(buffer, offset + 88, SPL_DESCRIPTION_IMAGE, "BAM"));
    addField(new Unknown(buffer, offset + 96, 4, COMMON_UNUSED));
    final SectionOffset abilOffset = new SectionOffset(buffer, offset + 100, SPL_OFFSET_ABILITIES, Ability.class);
    addField(abilOffset);
    final SectionCount abilCount = new SectionCount(buffer, offset + 104, 2, SPL_NUM_ABILITIES, Ability.class);
    addField(abilCount);
    final SectionOffset globalOffset = new SectionOffset(buffer, offset + 106, SPL_OFFSET_EFFECTS, Effect.class);
    addField(globalOffset);
    final DecNumber globalIndex = new DecNumber(buffer, offset + 110, 2, SPL_FIRST_EFFECT_INDEX);
    addField(globalIndex);
    final SectionCount globalCount = new SectionCount(buffer, offset + 112, 2, SPL_NUM_GLOBAL_EFFECTS, Effect.class);
    addField(globalCount);

    if (version.toString().equalsIgnoreCase("V2.0")) {
      addField(new DecNumber(buffer, offset + 114, 1, SPL_SPELL_DURATION_ROUNDS_PER_LEVEL));
      addField(new DecNumber(buffer, offset + 115, 1, SPL_SPELL_DURATION_BASE));
      addField(new Unknown(buffer, offset + 116, 14));
    }

    offset = abilOffset.getValue();
    Ability[] abilities = new Ability[abilCount.getValue()];
    for (int i = 0; i < abilities.length; i++) {
      abilities[i] = new Ability(this, buffer, offset, i);
      addField(abilities[i]);
      offset = abilities[i].getEndOffset();
    }
    int endOffset = offset;

    final int effectSize = (new Effect()).getSize();
    offset = globalOffset.getValue() + effectSize * globalIndex.getValue();
    for (int i = 0; i < globalCount.getValue(); i++) {
      Effect eff = new Effect(this, buffer, offset, i);
      offset = eff.getEndOffset();
      addField(eff);
    }
    endOffset = Math.max(endOffset, offset);

    for (final Ability ability : abilities) {
      final IsNumeric abilIndex = (IsNumeric) ability.getAttribute(Ability.ABILITY_FIRST_EFFECT_INDEX);
      offset = globalOffset.getValue() + effectSize * abilIndex.getValue();
      offset = ability.readEffects(buffer, offset);
      endOffset = Math.max(endOffset, offset);
    }

    return endOffset;
  }

  private void incAbilityEffects(StructEntry child, AddRemovable datatype, int value) {
    if (child instanceof Ability && datatype instanceof Effect) {
      final List<StructEntry> fields = getFields();
      final ListIterator<StructEntry> it = fields.listIterator(fields.indexOf(child) + 1);
      while (it.hasNext()) {
        final StructEntry se = it.next();
        if (se instanceof Ability) {
          ((Ability) se).incEffectsIndex(value);
        }
      }
    }
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  /**
   * Returns the count of spell abilities that will be created or updated according to the given configuration.
   *
   * @throws Exception if the abilities count could not be determined.
   */
  public int getAbilityGenerationCount(GeneratorConfig config) throws Exception {
    if (config == null) {
      throw new Exception("Configuration not available.");
    }

    // sanity check: at least one ability must exist
    final int numAbils = ((IsNumeric)getAttribute(SPL_NUM_ABILITIES)).getValue();
    if (numAbils < 1) {
      throw new Exception("At least one spell ability must exist.");
    }

    final int spellLevel = ((IsNumeric)getAttribute(SPL_LEVEL)).getValue();
    // First effective creature level
    final int startLevel = config.isSkipUnneeded() ? Math.max(2, spellLevel * 2) : 2;
    final int maxLevel = config.getMaxLevel();
    final int levelsPerAbil = config.getLevelsPerAbility();

    int abilitiesCount = 0;
    int level = (levelsPerAbil >= startLevel) ? 0 : levelsPerAbil;
    for (; level <= maxLevel; level += levelsPerAbil) {
      if (abilitiesCount == 0 && level + levelsPerAbil < startLevel) {
        continue;
      }

      abilitiesCount++;
    }

    return abilitiesCount;
  }

  /** Autogenerates spell ability structures based on the given configuration. */
  private void performAbilityGeneration(GeneratorConfig config) throws Exception {
    if (config == null) {
      throw new NullPointerException("config is null.");
    }

    // sanity check: at least one ability must exist
    final List<StructEntry> list = getFields(Ability.class);
    if (list.isEmpty()) {
      throw new Exception("At least one spell ability must exist.");
    }

    final int spellLevel = ((IsNumeric)getAttribute(SPL_LEVEL)).getValue();
    // First effective creature level
    final int startLevel = config.isSkipUnneeded() ? Math.max(2, spellLevel * 2) : 2;
    final int maxLevel = config.getMaxLevel();
    final int levelsPerAbil = config.getLevelsPerAbility();

    // keeps track of level count
    int level;
    // effective ability index (includes skipped ability structures)
    int effectiveAbilityIndex;
    if (levelsPerAbil >= startLevel) {
      level = 0;
      effectiveAbilityIndex = -1;
    } else {
      level = levelsPerAbil;
      effectiveAbilityIndex = 0;
    }

    // keeps track of number of generated ability entries
    int abilitiesCount = 0;
    final ArrayList<Ability> abilityList = new ArrayList<>(maxLevel);

    for (; level <= maxLevel; level += levelsPerAbil) {
      effectiveAbilityIndex++;

      if (abilitiesCount == 0 && level + levelsPerAbil < startLevel) {
        continue;
      }

      // adding ability
      if (abilitiesCount < list.size()) {
        // Ability exists: just copy from source list
        abilityList.add((Ability)list.get(abilitiesCount));
      } else {
        // Ability does not exist: create as clone from previous ability and add to abilityList
        final Ability prevAbil = abilityList.get(abilitiesCount - 1);
        try {
          final Ability newAbil = (Ability)prevAbil.clone();
          abilityList.add(newAbil);
        } catch (CloneNotSupportedException e) {
          throw new Exception("Could not create spell ability #" + abilitiesCount);
        }
      }

      // adjust ability and effect attributes according to config
      final Ability curAbil = abilityList.get(abilitiesCount);
      final int effectiveLevel = (abilitiesCount == 0) ? 1 : level;

      // updating ability attributes
      // adjusting level
      ((DecNumber)curAbil.getAttribute(Ability.SPL_ABIL_MIN_LEVEL)).setValue(effectiveLevel);

      GeneratorAttribute attribute;

      // adjusting range
      if (config.isRangeEnabled()) {
        attribute = new GeneratorAttribute(config.getRangeBase(), config.getRangeIncrement());
        ((DecNumber)curAbil.getAttribute(Ability.ABILITY_RANGE)).setValue(attribute.getScaledValue(effectiveAbilityIndex));
      }

      // adjusting casting speed
      if (config.isCastingSpeedEnabled()) {
        attribute = new GeneratorAttribute(config.getCastingSpeedBase(), config.getCastingSpeedIncrement());
        final int value = Math.max(0, attribute.getScaledValue(effectiveAbilityIndex));
        ((DecNumber)curAbil.getAttribute(Ability.SPL_ABIL_CASTING_SPEED)).setValue(value);
      }

      // updating effect attributes
      final List<StructEntry> effectsList = curAbil.getFields(Effect.class);
      for (int i = 0, num = effectsList.size(); i < num; i++) {
        final Effect effect = (Effect)effectsList.get(i);
        final int opcode = ((IsNumeric)effect.getAttribute(EffectType.EFFECT_TYPE)).getValue();
        if (config.isDurationEnabled()) {
          // adjusting duration
          final int timing = ((IsNumeric)effect.getAttribute(BaseOpcode.EFFECT_TIMING_MODE)).getValue();
          final Set<Integer> setPreserve = config.getDurationBlackList();
          final boolean preserve = (timing == 0 || timing == 10) && (setPreserve != null && setPreserve.contains(opcode));
          final Set<Integer> setForce= config.getForcedDurationList();
          final boolean force = (setForce != null && setForce.contains(opcode));
          Integer duration = null;
          int durationThreshold = config.getDurationThreshold();
          switch (timing) {
            case 0:   // Instant/Limited
            case 3:   // Delay/Limited
            case 4:   // Delay/Permanent
            case 5:   // Delay/While equipped
            case 6:   // Limited after duration
            case 7:   // Permanent after duration
            case 8:   // Equipped after duration
              if (!preserve) {
                attribute = new GeneratorAttribute(config.getDurationBase(), config.getDurationIncrement());
                duration = attribute.getScaledValue(effectiveAbilityIndex);
              }
              break;
            case 10:  // Instant/Limited (in ticks)
              if (!preserve) {
                final int globalFactor = 15;
                attribute = new GeneratorAttribute(config.getDurationBase(), config.getDurationIncrement());
                duration = attribute.getScaledValue(effectiveAbilityIndex, globalFactor);
                durationThreshold *= globalFactor;
              }
              break;
            default:
              if (!force) {
                duration = 0;
              }
              break;
          }

          if (duration != null) {
            final int effectDuration = ((IsNumeric)effect.getAttribute(BaseOpcode.EFFECT_DURATION)).getValue();
            if (effectDuration >= durationThreshold) {
              ((DecNumber)effect.getAttribute(BaseOpcode.EFFECT_DURATION)).setValue(duration);
            }
          }
        }

        // adjusting dice count
        if (config.isDiceCountEnabled()) {
          attribute = new GeneratorAttribute(config.getDiceCountBase(), config.getDiceCountIncrement());
          ((DecNumber)effect.getAttribute(BaseOpcode.EFFECT_DICE_COUNT_MAX_LEVEL))
              .setValue(attribute.getScaledValue(effectiveAbilityIndex));
        }

        // adjusting dice size
        if (config.isDiceSizeEnabled()) {
          attribute = new GeneratorAttribute(config.getDiceSizeBase(), config.getDiceSizeIncrement());
          ((DecNumber)effect.getAttribute(BaseOpcode.EFFECT_DICE_SIZE_MIN_LEVEL))
              .setValue(attribute.getScaledValue(effectiveAbilityIndex));
        }

        // adjusting saving throws
        if (config.isSaveBonusEnabled()) {
          final int typeMask = (Profile.getEngine() == Profile.Engine.IWD2) ? 0x1c : 0x1f;
          final int saveType = ((IsNumeric)effect.getAttribute(BaseOpcode.EFFECT_SAVE_TYPE)).getValue() & typeMask;
          if (saveType != 0) {
            attribute = new GeneratorAttribute(config.getSaveBonusBase(), config.getSaveBonusIncrement());
            final String name = (Profile.getEngine() == Profile.Engine.IWD2) ? BaseOpcode.EFFECT_SAVE_PENALTY
                : BaseOpcode.EFFECT_SAVE_BONUS;
            ((DecNumber)effect.getAttribute(name)).setValue(attribute.getScaledValue(effectiveAbilityIndex));
          }
        }

        // adjusting opcode-specific attributes
        final Map<Integer, EffectConfig> effectConfigs = config.getEffectConfigs();
        final EffectConfig effectConfig = effectConfigs.get(opcode);
        if (effectConfig != null) {
          EffectConfig.Parameter param = new EffectConfig.Parameter(effectConfig.getParameter1Mode(),
              effectConfig.getParameter1Items(), effectiveAbilityIndex, 1);
          Byte[] bytes = param.getBytes();
          applyBytes(effect, bytes, 0x04);

          param = new EffectConfig.Parameter(effectConfig.getParameter2Mode(),
              effectConfig.getParameter2Items(), effectiveAbilityIndex, 1);
          bytes = param.getBytes();
          applyBytes(effect, bytes, 0x08);

          param = new EffectConfig.Parameter(effectConfig.getSpecialMode(),
              effectConfig.getSpecialItems(), effectiveAbilityIndex, 1);
          bytes = param.getBytes();
          applyBytes(effect, bytes, 0x2c);
        }
      }

      abilitiesCount++;
    }

    // removing excess abilities from original abilities list
    for (int i = list.size() - 1; i >= abilitiesCount; i--) {
      final StructEntry se = list.get(i);
      if (se instanceof AddRemovable) {
        removeDatatype((AddRemovable)se, true);
      }
    }

    // adding new abilities from newly generated abilitis list
    for (int i = list.size(), num = abilityList.size(); i < num; i++) {
      final Ability abil = abilityList.get(i);

      // child structures must be re-added manually to sync them with the parent structure
      final List<AddRemovable> childList = abil.removeAllRemoveables();
      addDatatype(abil, getDatatypeIndex(abil, true));

      for (final AddRemovable child : childList) {
        abil.addDatatype(child);
      }
    }
  }

  /** Returns the exclusion array for the specified spell type. */
  private static String[] getExclusionArray(int spellType) {
    switch (spellType) {
      case 1:
        return EXCLUDE_WIZARD_ARRAY;
      case 2:
        return EXCLUDE_PRIEST_ARRAY;
      default:
        return EXCLUDE_ARRAY;
    }
  }

  /**
   * Applies the given byte data to the affected fields in the specified structure.
   *
   * @param struct {@link AbstractStruct} instance of the structure where data should be applied to.
   * @param bytes  Array of {@link Byte} objects that should be applied. {@code null} values are skipped.
   * @param offset Start offset of the structure data to update.
   * @throws Exception if an error occurred
   */
  private static void applyBytes(AbstractStruct struct, Byte[] bytes, int offset) throws Exception {
    if (struct == null || bytes == null) {
      throw new NullPointerException();
    }
    if (offset < 0 || offset > struct.getSize()) {
      throw new IllegalArgumentException("Offset is out of bounds");
    }

    // checking if any bytes in the array are defined
    boolean check = false;
    for (int i = 0; i < bytes.length; i++) {
      check |= (bytes[i] != null);
    }
    if (!check) {
      return;
    }

    final int endOffset = offset + bytes.length;
    final List<StructEntry> fields = struct.getFields();
    for (int index = 0, count = fields.size(); index < count; index++) {
      final StructEntry entry = fields.get(index);
      final int entryOffset = entry.getOffset() - ((entry.getParent() != null) ? entry.getParent().getOffset() : 0);
      final int entrySize = entry.getSize();
      if (entryOffset + entrySize <= offset || entryOffset >= endOffset) {
        continue;
      }

      // checking if bytes are defined for this field
      final int relIdxStart = Math.max(0, entryOffset - offset);
      final int relIdxEnd = Math.min(bytes.length, entryOffset + entrySize - offset);
      boolean defined = false;
      for (int i = relIdxStart; i < relIdxEnd; i++) {
        defined |= (bytes[i] != null);
      }

      if (defined) {
        // collecting original data
        final ByteBuffer bb = StreamUtils.getByteBuffer(entrySize);
        try (final ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb)) {
          entry.write(bbos);
        } catch (IOException e) {
          Logger.error(e);
          throw new Exception("Reading byte data from field at offset " + entryOffset, e);
        }

        // applying defined bytes
        for (int i = relIdxStart; i < relIdxEnd; i++) {
          if (bytes[i] != null) {
            bb.put(i, bytes[i]);
          }
        }

        // recreate original datatype from new data
        bb.position(0);
        try {
          entry.read(bb, 0);
        } catch (Exception e) {
          throw new Exception("Updating byte data in field at offset " + entryOffset, e);
        }
      }
    }
  }

  /**
   * Checks whether the specified resource entry matches all available search options. Called by "Extended Search"
   */
  public static boolean matchSearchOptions(ResourceEntry entry, SearchOptions searchOptions) {
    if (entry != null && searchOptions != null) {
      try {
        SplResource spl = new SplResource(entry);
        Ability[] abilities;
        Effect[][] abilityEffects;
        Effect[] effects;
        boolean retVal = true;
        String key;
        Object o;

        // preparing substructures
        IsNumeric ofs = (IsNumeric) spl.getAttribute(SPL_OFFSET_EFFECTS, false);
        IsNumeric cnt = (IsNumeric) spl.getAttribute(SPL_NUM_GLOBAL_EFFECTS, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          effects = new Effect[cnt.getValue()];
          for (int idx = 0; idx < cnt.getValue(); idx++) {
            String label = String.format(SearchOptions.getResourceName(SearchOptions.SPL_Effect), idx);
            effects[idx] = (Effect) spl.getAttribute(label, false);
          }
        } else {
          effects = new Effect[0];
        }

        ofs = (IsNumeric) spl.getAttribute(SPL_OFFSET_ABILITIES, false);
        cnt = (IsNumeric) spl.getAttribute(SPL_NUM_ABILITIES, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          abilities = new Ability[cnt.getValue()];
          for (int idx = 0; idx < cnt.getValue(); idx++) {
            String label = String.format(SearchOptions.getResourceName(SearchOptions.SPL_Ability), idx);
            abilities[idx] = (Ability) spl.getAttribute(label, false);
          }
        } else {
          abilities = new Ability[0];
        }

        abilityEffects = new Effect[abilities.length][];
        for (int idx = 0; idx < abilities.length; idx++) {
          if (abilities[idx] != null) {
            cnt = (IsNumeric) abilities[idx].getAttribute(AbstractAbility.ABILITY_NUM_EFFECTS, false);
            if (cnt != null && cnt.getValue() > 0) {
              abilityEffects[idx] = new Effect[cnt.getValue()];
              for (int idx2 = 0; idx2 < cnt.getValue(); idx2++) {
                String label = String.format(SearchOptions.getResourceName(SearchOptions.SPL_Ability_Effect), idx2);
                abilityEffects[idx][idx2] = (Effect) abilities[idx].getAttribute(label, false);
              }
            } else {
              abilityEffects[idx] = new Effect[0];
            }
          } else {
            abilityEffects[idx] = new Effect[0];
          }
        }

        // checking options
        if (retVal) {
          key = SearchOptions.SPL_Name;
          o = searchOptions.getOption(key);
          StructEntry struct = spl.getAttribute(SearchOptions.getResourceName(key), false);
          retVal &= SearchOptions.Utils.matchString(struct, o, false, false);
        }

        String[] keyList = new String[] { SearchOptions.SPL_SpellType, SearchOptions.SPL_CastingAnimation,
            SearchOptions.SPL_PrimaryType, SearchOptions.SPL_SecondaryType, SearchOptions.SPL_Level };
        for (String s : keyList) {
          if (retVal) {
            key = s;
            o = searchOptions.getOption(key);
            StructEntry struct = spl.getAttribute(SearchOptions.getResourceName(key), false);
            retVal = SearchOptions.Utils.matchNumber(struct, o);
          } else {
            break;
          }
        }

        keyList = new String[] { SearchOptions.SPL_Flags, SearchOptions.SPL_Exclusion };
        for (String s : keyList) {
          if (retVal) {
            key = s;
            o = searchOptions.getOption(key);
            StructEntry struct = spl.getAttribute(SearchOptions.getResourceName(key), false);
            retVal = SearchOptions.Utils.matchFlags(struct, o);
          } else {
            break;
          }
        }

        keyList = new String[] { SearchOptions.SPL_Effect_Type1, SearchOptions.SPL_Effect_Type2,
            SearchOptions.SPL_Effect_Type3 };
        for (String s : keyList) {
          if (retVal) {
            boolean found = false;
            key = s;
            o = searchOptions.getOption(key);
            for (Effect effect : effects) {
              if (!found) {
                if (effect != null) {
                  StructEntry struct = effect.getAttribute(SearchOptions.getResourceName(key), false);
                  found = SearchOptions.Utils.matchNumber(struct, o);
                }
              } else {
                break;
              }
            }
            retVal = found || (o == null);
          } else {
            break;
          }
        }

        SearchOptions abilityOption = (SearchOptions) searchOptions.getOption(SearchOptions.SPL_Ability);
        if (retVal && abilityOption != null) {
          // indicates whether any ability options have been selected
          boolean hasAbilityOptions = false;
          keyList = new String[] { SearchOptions.SPL_Ability_Type, SearchOptions.SPL_Ability_Location,
              SearchOptions.SPL_Ability_Target, SearchOptions.SPL_Ability_Range, SearchOptions.SPL_Ability_Level,
              SearchOptions.SPL_Ability_Speed, SearchOptions.SPL_Ability_Projectile,
              SearchOptions.SPL_Ability_Effect_Type1, SearchOptions.SPL_Ability_Effect_Type2,
              SearchOptions.SPL_Ability_Effect_Type3 };
          for (String s : keyList) {
            hasAbilityOptions |= (abilityOption.getOption(s) != null);
          }

          // tracks matches for each option in every available ability
          final int abilityOptions = keyList.length; // number of supported spell ability options
          boolean[][] abilityMatches = new boolean[abilities.length][abilityOptions];
          for (int i = 0; i < abilities.length; i++) {
            Arrays.fill(abilityMatches[i], false);
          }

          for (int i = 0; i < abilities.length; i++) {
            if (abilities[i] != null) {
              for (int j = 0; j < 7; j++) {
                key = keyList[j];
                o = abilityOption.getOption(key);
                StructEntry struct = abilities[i].getAttribute(SearchOptions.getResourceName(key), false);
                abilityMatches[i][j] = SearchOptions.Utils.matchNumber(struct, o);
              }

              for (int j = 7; j < keyList.length; j++) {
                key = keyList[j];
                o = abilityOption.getOption(key);
                for (int k = 0; k < abilityEffects[i].length; k++) {
                  if (abilityEffects[i][k] != null) {
                    StructEntry struct = abilityEffects[i][k].getAttribute(SearchOptions.getResourceName(key), false);
                    abilityMatches[i][j] |= SearchOptions.Utils.matchNumber(struct, o);
                  }
                }
              }
            }
          }

          // evaluating collected results
          boolean[] foundSingle = new boolean[abilityMatches.length]; // for single ability option
          boolean[] foundMulti = new boolean[abilityOptions]; // for multiple abilities option
          for (int i = 0; i < foundMulti.length; i++) {
            foundMulti[i] = (abilityOption.getOption(keyList[i]) == null);
          }

          for (int i = 0; i < abilityMatches.length; i++) {
            if (abilities[i] != null) {
              foundSingle[i] = true;
              for (int j = 0; j < abilityMatches[i].length; j++) {
                foundSingle[i] &= abilityMatches[i][j];
                foundMulti[j] |= abilityMatches[i][j];
              }
            }
          }

          boolean resultSingle = false;
          for (boolean b : foundSingle) {
            resultSingle |= b;
          }
          resultSingle |= !hasAbilityOptions;

          boolean resultMulti = true;
          for (boolean b : foundMulti) {
            resultMulti &= b;
          }
          resultMulti |= !hasAbilityOptions;

          boolean isAbilitySingle;
          o = abilityOption.getOption(SearchOptions.SPL_Ability_MatchSingle);
          if (o instanceof Boolean) {
            isAbilitySingle = (Boolean) o;
          } else {
            isAbilitySingle = false;
          }

          if (isAbilitySingle) {
            retVal = resultSingle;
          } else {
            retVal = resultMulti;
          }
        }

        keyList = new String[] { SearchOptions.SPL_Custom1, SearchOptions.SPL_Custom2, SearchOptions.SPL_Custom3,
            SearchOptions.SPL_Custom4 };
        for (String s : keyList) {
          if (retVal) {
            key = s;
            o = searchOptions.getOption(key);
            retVal = SearchOptions.Utils.matchCustomFilter(spl, o);
          } else {
            break;
          }
        }

        return retVal;
      } catch (Exception e) {
        Logger.trace(e);
      }
    }
    return false;
  }
}
