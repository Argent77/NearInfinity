// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.itm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

import org.infinity.datatype.ColorValue;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IdsBitmap;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ItemTypeBitmap;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.StringRef;
import org.infinity.datatype.TextBitmap;
import org.infinity.datatype.TextString;
import org.infinity.datatype.Unknown;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.resource.AbstractAbility;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.Effect;
import org.infinity.resource.HasChildStructs;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.spl.SplResource;
import org.infinity.search.SearchOptions;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.StringTable;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.StreamUtils;

/**
 * This resource describes an "item". Items include weapons, armor, books, scrolls, rings and more. Items can have
 * attached {@link Ability abilties}, occuring either when a target creature it hit, or when the item is equipped. ITM
 * files have a similar structure to {@link SplResource SPL} files.
 * <p>
 * ITM files consist of a main header, zero or more extended headers (each containing zero or more feature blocks) and
 * zero or more casting feature blocks. All the feature blocks are stored as a continuous data segment, with each
 * extended header containing an offset into this data, and the main header containing an offset into this data for the
 * casting feature blocks.
 * <p>
 * A creature must meet the minimum stat requirements to be able to converse with an item.
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/itm_v1.htm">
 *      https://gibberlings3.github.io/iesdp/file_formats/ie_formats/itm_v1.htm</a>
 */
public final class ItmResource extends AbstractStruct implements Resource, HasChildStructs, HasViewerTabs {
  // ITM-specific field labels
  public static final String ITM_NAME_GENERAL           = "General name";
  public static final String ITM_NAME_IDENTIFIED        = "Identified name";
  public static final String ITM_DROP_SOUND             = "Drop sound";
  public static final String ITM_FLAGS                  = "Flags";
  public static final String ITM_CATEGORY               = "Category";
  public static final String ITM_UNUSABLE_BY            = "Unusable by";
  public static final String ITM_EQUIPPED_APPEARANCE    = "Equipped appearance";
  public static final String ITM_USED_UP_ITEM           = "Used up item";
  public static final String ITM_MIN_LEVEL              = "Minimum level";
  public static final String ITM_MIN_STRENGTH           = "Minimum strength";
  public static final String ITM_MIN_STRENGTH_BONUS     = "Minimum strength bonus";
  public static final String ITM_MIN_INTELLIGENCE       = "Minimum intelligence";
  public static final String ITM_MIN_DEXTERITY          = "Minimum dexterity";
  public static final String ITM_MIN_WISDOM             = "Minimum wisdom";
  public static final String ITM_MIN_CONSTITUTION       = "Minimum constitution";
  public static final String ITM_MIN_CHARISMA           = "Minimum charisma";
  public static final String ITM_UNUSABLE_BY_1          = "Unusable by (1/4)";
  public static final String ITM_UNUSABLE_BY_2          = "Unusable by (2/4)";
  public static final String ITM_UNUSABLE_BY_3          = "Unusable by (3/4)";
  public static final String ITM_UNUSABLE_BY_4          = "Unusable by (4/4)";
  public static final String ITM_WEAPON_PROFICIENCY     = "Weapon proficiency";
  public static final String ITM_PRICE                  = "Price";
  public static final String ITM_MAX_IN_STACK           = "Maximum in stack";
  public static final String ITM_ICON                   = "Icon";
  public static final String ITM_LORE                   = "Lore to identify";
  public static final String ITM_ICON_GROUND            = "Ground icon";
  public static final String ITM_WEIGHT                 = "Weight";
  public static final String ITM_DESCRIPTION_GENERAL    = "General description";
  public static final String ITM_DESCRIPTION_IDENTIFIED = "Identified description";
  public static final String ITM_PICK_UP_SOUND          = "Pick up sound";
  public static final String ITM_DESCRIPTION_IMAGE      = "Description image";
  public static final String ITM_ENCHANTMENT            = "Enchantment";
  public static final String ITM_OFFSET_ABILITIES       = "Abilities offset";
  public static final String ITM_NUM_ABILITIES          = "# abilities";
  public static final String ITM_OFFSET_EFFECTS         = "Effects offset";
  public static final String ITM_FIRST_EFFECT_INDEX     = "First effect index";
  public static final String ITM_NUM_GLOBAL_EFFECTS     = "# global effects";
  public static final String ITM_DIALOG                 = "Dialogue";
  public static final String ITM_SPEAKER_NAME           = "Speaker name";
  public static final String ITM_WEAPON_COLOR           = "Weapon color";

  public static final String[] FLAGS_ARRAY = { "None", "Critical item", "Two-handed", "Droppable", "Displayable",
      "Cursed", "Not copyable", "Magical", "Left-handed", "Silver", "Cold iron", "Off-handed", "Conversable",
      "EE: Fake two-handed", "EE: Forbid off-hand weapon", "", "EE: Adamantine", null, null, "EEex: Ignore Weapon Styles", null, null, null,
      null, null, "EE/Ex: Undispellable", "EE/Ex: Toggle critical hits" };

  public static final String[] FLAGS11_ARRAY = { "None", "Unsellable", "Two-handed", "Droppable", "Displayable",
      "Cursed", "Not copyable", "Magical", "Left-handed", "Silver", "Cold iron", "Steel", "Conversable", "Pulsating" };

  public static final String[] FLAGS_PSTEE_ARRAY = { "None", "Unsellable", "Two-handed", "Droppable", "Displayable",
      "Cursed", "Not copyable", "Magical", "Left-handed", "Silver", "Cold iron", "Steel", "Conversable",
      "EE: Fake two-handed", "EE: Forbid off-hand weapon", "Usable in inventory", "EE: Adamantine", null, null, null,
      null, null, null, null, null, "EE/Ex: Undispellable", "EE/Ex: Toggle critical hits" };

  public static final String[] USABILITY_ARRAY = { "None",
      "Chaotic;Includes Chaotic Good, Chaotic Neutral and Chaotic Evil",
      "Evil;Includes Lawful Evil, Neutral Evil and Chaotic Evil",
      "Good;Includes Lawful Good, Neutral Good and Chaotic Good",
      "... Neutral;Includes Lawful Neutral, True Neutral and Chaotic Neutral",
      "Lawful;Includes Lawful Good, Lawful Neutral and Lawful Evil",
      "Neutral ...;Includes Neutral Good, True Neutral and Neutral Evil", "Bard", "Cleric", "Cleric-Mage",
      "Cleric-Thief", "Cleric-Ranger", "Fighter", "Fighter-Druid", "Fighter-Mage", "Fighter-Cleric",
      "Fighter-Mage-Cleric", "Fighter-Mage-Thief", "Fighter-Thief", "Mage", "Mage-Thief", "Paladin", "Ranger", "Thief",
      "Elf", "Dwarf", "Half-Elf", "Halfling", "Human", "Gnome", "Monk", "Druid", "Half-orc" };

  public static final String[] USABILITY11_ARRAY = { "None",
      "Chaotic;Includes Chaotic Good, Chaotic Neutral and Chaotic Evil",
      "Evil;Includes Lawful Evil, Neutral Evil and Chaotic Evil",
      "Good;Includes Lawful Good, Neutral Good and Chaotic Good",
      "... Neutral;Includes Lawful Neutral, True Neutral and Chaotic Neutral",
      "Lawful;Includes Lawful Good, Lawful Neutral and Lawful Evil",
      "Neutral ...;Includes Neutral Good, True Neutral and Neutral Evil", "Sensate", "Priest", "Godsman", "Anarchist",
      "Xaositect", "Fighter", "Non-aligned", "Fighter-Mage", "Dustman", "Mercykiller", "Indep", "Figher-Thief", "Mage",
      "Mage-Thief", "Dak'kon", "Fall-From-Grace", "Thief", "Vhailor", "Ignus", "Morte", "Nordom", "Human", "Annah",
      "EE: Monk", "Nameless One", "EE: Half-Orc" };

  public static final String[] USABILITY20_ARRAY = { "None", "Barbarian", "Bard", "Cleric", "Druid", "Fighter", "Monk",
      "Paladin", "Ranger", "Rogue", "Sorcerer", "Wizard", null,
      "Chaotic;Includes Chaotic Good, Chaotic Neutral and Chaotic Evil",
      "Evil;Includes Lawful Evil, Neutral Evil and Chaotic Evil",
      "Good;Includes Lawful Good, Neutral Good and Chaotic Good",
      "... Neutral;Includes Lawful Neutral, True Neutral and Chaotic Neutral",
      "Lawful;Includes Lawful Good, Lawful Neutral and Lawful Evil",
      "Neutral ...;Includes Neutral Good, True Neutral and Neutral Evil", null, null, null, null, null, "Elf", "Dwarf",
      "Half-elf", "Halfling", "Human", "Gnome", "Half-Orc" };

  public static final String[] KIT_USE_1_ARRAY = { "None", "Cleric of Talos", "Cleric of Helm", "Cleric of Lathander",
      "Totemic druid", "Shapeshifter", "Avenger", "Barbarian", "Wild mage" };

  public static final String[] KIT_USE_2_ARRAY = { "None", "Stalker", "Beastmaster", "Assassin", "Bounty hunter",
      "Swashbuckler", "Blade", "Jester", "Skald" };

  public static final String[] KIT_USE_3_ARRAY = { "None", "Diviner", "Enchanter", "Illusionist", "Invoker",
      "Necromancer", "Transmuter", "Generalist", "Archer" };

  public static final String[] KIT_USE_4_ARRAY = { "None", "Berserker", "Wizard slayer", "Kensai", "Cavalier",
      "Inquisitor", "Undead hunter", "Abjurer", "Conjurer" };

  public static final String[] KIT_USE_1_V2_ARRAY = { "None", "", "", "", "", "", "", "", "" };

  public static final String[] KIT_USE_2_V2_ARRAY = { "None", "Cleric of Lathander", "Cleric of Selune",
      "Cleric of Helm", "Cleric of Oghma", "Cleric of Tempus", "Cleric of Bane", "Cleric of Mask", "Cleric of Talos" };

  public static final String[] KIT_USE_3_V2_ARRAY = { "None", "Diviner", "Enchanter", "Illusionist", "Invoker",
      "Necromancer", "Transmuter", "Generalist", "Cleric of Ilmater" };

  public static final String[] KIT_USE_4_V2_ARRAY = { "None", "Paladin of Ilmater", "Paladin of Helm",
      "Paladin of Mystra", "Monk of the Old Order", "Monk of the Broken Ones", "Monk of the Dark Moon", "Abjurer",
      "Conjurer" };

  private StructHexViewer hexViewer;

  public static String getSearchString(InputStream is) throws IOException {
    is.skip(8);
    String defName = StringTable.getStringRef(StreamUtils.readInt(is)).trim();
    String name = StringTable.getStringRef(StreamUtils.readInt(is)).trim();
    if (name.isEmpty() || name.equalsIgnoreCase(StringTable.DEFAULT_STRING)) {
      return defName;
    } else {
      return name;
    }
  }

  public ItmResource(ResourceEntry entry) throws Exception {
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

  @Override
  protected void viewerInitialized(StructViewer viewer) {
    viewer.addTabChangeListener(hexViewer);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype) {
    if (datatype instanceof Effect) {
      for (final StructEntry o : getFields()) {
        if (o instanceof Ability) {
          ((Ability) o).incEffectsIndex(1);
        }
      }
    } else if (datatype instanceof Ability) {
      int effectCount = ((IsNumeric) getAttribute(ITM_NUM_GLOBAL_EFFECTS)).getValue();
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
        if (o instanceof Ability) {
          ((Ability) o).incEffectsIndex(-1);
        }
      }
    } else if (datatype instanceof Ability) {
      int effectCount = ((IsNumeric) getAttribute(ITM_NUM_GLOBAL_EFFECTS)).getValue();
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
    addField(new TextString(buffer, 0, 4, COMMON_SIGNATURE));
    final TextString version = new TextString(buffer, 4, 4, COMMON_VERSION);
    boolean isV10 = version.getText().equalsIgnoreCase("V1  ");
    boolean isV11 = version.getText().equalsIgnoreCase("V1.1");
    boolean isV20 = version.getText().equalsIgnoreCase("V2.0");
    addField(version);
    addField(new StringRef(buffer, 8, ITM_NAME_GENERAL));
    addField(new StringRef(buffer, 12, ITM_NAME_IDENTIFIED));
    if (isV11 || (isV10 && Profile.getGame() == Profile.Game.PSTEE)) {
      addField(new ResourceRef(buffer, 16, ITM_DROP_SOUND, "WAV"));
      if (Profile.getGame() == Profile.Game.PSTEE) {
        addField(new Flag(buffer, 24, 4, ITM_FLAGS, FLAGS_PSTEE_ARRAY));
        addField(new ItemTypeBitmap(buffer, 28, 2, ITM_CATEGORY));
      } else {
        addField(new Flag(buffer, 24, 4, ITM_FLAGS, FLAGS11_ARRAY));
        addField(new ItemTypeBitmap(buffer, 28, 2, ITM_CATEGORY));
      }
      addField(new Flag(buffer, 30, 4, ITM_UNUSABLE_BY, USABILITY11_ARRAY));
      addField(new TextBitmap(buffer, 34, 2, ITM_EQUIPPED_APPEARANCE, Profile.getEquippedAppearanceMap()));
    } else {
      addField(new ResourceRef(buffer, 16, ITM_USED_UP_ITEM, "ITM"));
      addField(
          new Flag(buffer, 24, 4, ITM_FLAGS, IdsMapCache.getUpdatedIdsFlags(FLAGS_ARRAY, "ITEMFLAG.IDS", 4, false, false)));
      addField(new ItemTypeBitmap(buffer, 28, 2, ITM_CATEGORY));
      if (isV20) {
        addField(new Flag(buffer, 30, 4, ITM_UNUSABLE_BY, USABILITY20_ARRAY));
      } else {
        addField(new Flag(buffer, 30, 4, ITM_UNUSABLE_BY, getUpdatedUsability()));
      }
      addField(new TextBitmap(buffer, 34, 2, ITM_EQUIPPED_APPEARANCE, Profile.getEquippedAppearanceMap()));
    }
    addField(new DecNumber(buffer, 36, 2, ITM_MIN_LEVEL));
    addField(new DecNumber(buffer, 38, 2, ITM_MIN_STRENGTH));
    if (ResourceFactory.resourceExists("KIT.IDS")) {
      addField(new DecNumber(buffer, 40, 1, ITM_MIN_STRENGTH_BONUS));
      if (isV20) {
        updateKitUsability(KIT_USE_1_V2_ARRAY, 24, 8, true);
      }
      addField(new Flag(buffer, 41, 1, ITM_UNUSABLE_BY_1, isV20 ? KIT_USE_1_V2_ARRAY : KIT_USE_1_ARRAY));
      addField(new DecNumber(buffer, 42, 1, ITM_MIN_INTELLIGENCE));
      addField(new Flag(buffer, 43, 1, ITM_UNUSABLE_BY_2, isV20 ? KIT_USE_2_V2_ARRAY : KIT_USE_2_ARRAY));
      addField(new DecNumber(buffer, 44, 1, ITM_MIN_DEXTERITY));
      addField(new Flag(buffer, 45, 1, ITM_UNUSABLE_BY_3, isV20 ? KIT_USE_3_V2_ARRAY : KIT_USE_3_ARRAY));
      addField(new DecNumber(buffer, 46, 1, ITM_MIN_WISDOM));
      addField(new Flag(buffer, 47, 1, ITM_UNUSABLE_BY_4, isV20 ? KIT_USE_4_V2_ARRAY : KIT_USE_4_ARRAY));
      addField(new DecNumber(buffer, 48, 1, ITM_MIN_CONSTITUTION));
      if (ResourceFactory.resourceExists("PROFTYPE.IDS")) {
        addField(new IdsBitmap(buffer, 49, 1, ITM_WEAPON_PROFICIENCY, "PROFTYPE.IDS"));
      } else {
        addField(new IdsBitmap(buffer, 49, 1, ITM_WEAPON_PROFICIENCY, "STATS.IDS"));
      }
    } else {
      addField(new DecNumber(buffer, 40, 2, ITM_MIN_STRENGTH_BONUS));
      addField(new DecNumber(buffer, 42, 2, ITM_MIN_INTELLIGENCE));
      addField(new DecNumber(buffer, 44, 2, ITM_MIN_DEXTERITY));
      addField(new DecNumber(buffer, 46, 2, ITM_MIN_WISDOM));
      addField(new DecNumber(buffer, 48, 2, ITM_MIN_CONSTITUTION));
    }
    addField(new DecNumber(buffer, 50, 2, ITM_MIN_CHARISMA));
    addField(new DecNumber(buffer, 52, 4, ITM_PRICE));
    addField(new DecNumber(buffer, 56, 2, ITM_MAX_IN_STACK));
    addField(new ResourceRef(buffer, 58, ITM_ICON, "BAM"));
    addField(new DecNumber(buffer, 66, 2, ITM_LORE));
    addField(new ResourceRef(buffer, 68, ITM_ICON_GROUND, "BAM"));
    addField(new DecNumber(buffer, 76, 4, ITM_WEIGHT));
    addField(new StringRef(buffer, 80, ITM_DESCRIPTION_GENERAL));
    addField(new StringRef(buffer, 84, ITM_DESCRIPTION_IDENTIFIED));
    if (isV11 || (isV10 && Profile.getGame() == Profile.Game.PSTEE)) {
      addField(new ResourceRef(buffer, 88, ITM_PICK_UP_SOUND, "WAV"));
    } else {
      addField(new ResourceRef(buffer, 88, ITM_DESCRIPTION_IMAGE, "BAM"));
    }
    addField(new DecNumber(buffer, 96, 4, ITM_ENCHANTMENT));
    final SectionOffset abilOffset = new SectionOffset(buffer, 100, ITM_OFFSET_ABILITIES, Ability.class);
    addField(abilOffset);
    final SectionCount abilCount = new SectionCount(buffer, 104, 2, ITM_NUM_ABILITIES, Ability.class);
    addField(abilCount);
    final SectionOffset globalOffset = new SectionOffset(buffer, 106, ITM_OFFSET_EFFECTS, Effect.class);
    addField(globalOffset);
    final DecNumber globalIndex = new DecNumber(buffer, 110, 2, ITM_FIRST_EFFECT_INDEX);
    addField(globalIndex);
    final SectionCount globalCount = new SectionCount(buffer, 112, 2, ITM_NUM_GLOBAL_EFFECTS, Effect.class);
    addField(globalCount);

    if (isV11) {
      addField(new ResourceRef(buffer, 114, ITM_DIALOG, "DLG"));
      addField(new StringRef(buffer, 122, ITM_SPEAKER_NAME));
      addField(new ColorValue(buffer, 126, 2, ITM_WEAPON_COLOR, true, "PAL32.BMP"));
      addField(new Unknown(buffer, 128, 26));
    } else if (isV20) {
      addField(new Unknown(buffer, 114, 16));
    }

    offset = abilOffset.getValue();
    Ability[] abilities = new Ability[abilCount.getValue()];
    for (int i = 0; i < abilities.length; i++) {
      abilities[i] = new Ability(this, buffer, offset, i);
      offset = abilities[i].getEndOffset();
      addField(abilities[i]);
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
   * Updates the specified string array with kit names from KIT.IDS.
   *
   * @param kits        The string array for kit names. (First slot is reserved for empty selection string.)
   * @param offset      bit position to start.
   * @param count       number of bits to update.
   * @param fillMissing whether only empty slots in the string array should be updated.
   */
  private void updateKitUsability(String[] kits, int offset, int count, boolean fillMissing) {
    if (kits != null && offset >= 0 && offset < 32 && count > 0) {
      IdsMap map = IdsMapCache.get("KIT.IDS");
      Table2da table = Table2daCache.get("KITLIST.2DA");
      if (map != null) {
        for (int i = 0; i < count; i++) {
          long value = 1L << (offset + i);
          IdsMapEntry entry = map.get(value);
          if (entry != null) {
            if (i + 1 < kits.length && (!fillMissing || kits[i + 1] == null || kits[i + 1].isEmpty())) {
              int strref = -1;
              if (table != null) {
                // try getting proper kit name from kitlist.2da
                for (int row = 3, rowCount = table.getRowCount(); row < rowCount; row++) {
                  if (entry.getSymbol().equalsIgnoreCase(table.get(row, 0))) {
                    strref = Misc.toNumber(table.get(row, 2), -1); // mixed
                    if (strref < 0) {
                      strref = Misc.toNumber(table.get(row, 1), -1); // lowercase
                    }
                  }
                  if (strref > 0) {
                    break;
                  }
                }
              }
              String desc = null;
              if (strref > 0) {
                try {
                  desc = StringTable.getStringRef(strref);
                } catch (IndexOutOfBoundsException e) {
                  Logger.trace(e);
                }
              }
              if (desc == null || desc.isEmpty()) {
                desc = entry.getSymbol();
              }
              kits[i + 1] = desc;
            }
          }
        }
      }
    }
  }

  /** Returns an updated ITM V1 usability array for the current engine version. */
  private static String[] getUpdatedUsability() {
    final String[] usability;

    if (Profile.isEnhancedEdition() || Profile.getEngine() == Profile.Engine.BG2) {
      usability = Arrays.copyOf(USABILITY_ARRAY, USABILITY_ARRAY.length);
      usability[19] = "Mage and Sorcerer";
      if (Profile.isEnhancedEdition()) {
        usability[31] = "Druid and Shaman";
      }
    } else {
      usability = USABILITY_ARRAY;
    }

    return usability;
  }

  /**
   * Called by "Extended Search" Checks whether the specified resource entry matches all available search options.
   */
  public static boolean matchSearchOptions(ResourceEntry entry, SearchOptions searchOptions) {
    if (entry != null && searchOptions != null) {
      try {
        ItmResource itm = new ItmResource(entry);
        Ability[] abilities;
        Effect[][] abilityEffects;
        Effect[] effects;
        boolean retVal = true;
        String key;
        Object o;

        // preparing substructures
        IsNumeric ofs = (IsNumeric) itm.getAttribute(ITM_OFFSET_EFFECTS, false);
        IsNumeric cnt = (IsNumeric) itm.getAttribute(ITM_NUM_GLOBAL_EFFECTS, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          effects = new Effect[cnt.getValue()];
          for (int idx = 0; idx < cnt.getValue(); idx++) {
            String label = String.format(SearchOptions.getResourceName(SearchOptions.ITM_Effect), idx);
            effects[idx] = (Effect) itm.getAttribute(label, false);
          }
        } else {
          effects = new Effect[0];
        }

        ofs = (IsNumeric) itm.getAttribute(ITM_OFFSET_ABILITIES, false);
        cnt = (IsNumeric) itm.getAttribute(ITM_NUM_ABILITIES, false);
        if (ofs != null && ofs.getValue() > 0 && cnt != null && cnt.getValue() > 0) {
          abilities = new Ability[cnt.getValue()];
          for (int idx = 0; idx < cnt.getValue(); idx++) {
            String label = String.format(SearchOptions.getResourceName(SearchOptions.ITM_Ability), idx);
            abilities[idx] = (Ability) itm.getAttribute(label, false);
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
                String label = String.format(SearchOptions.getResourceName(SearchOptions.ITM_Ability_Effect), idx2);
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
          key = SearchOptions.ITM_Name;
          o = searchOptions.getOption(key);
          StructEntry struct = itm.getAttribute(SearchOptions.getResourceName(key), false);
          retVal = SearchOptions.Utils.matchString(struct, o, false, false);
        }

        if (retVal) {
          key = SearchOptions.ITM_Appearance;
          o = searchOptions.getOption(key);
          StructEntry struct = itm.getAttribute(SearchOptions.getResourceName(key), false);
          retVal = SearchOptions.Utils.matchString(struct, o, true, true);
        }

        String[] keyList = new String[] { SearchOptions.ITM_Flags, SearchOptions.ITM_Unusable,
            SearchOptions.ITM_KitsUnusable1, SearchOptions.ITM_KitsUnusable2, SearchOptions.ITM_KitsUnusable3,
            SearchOptions.ITM_KitsUnusable4 };
        for (String element : keyList) {
          if (retVal) {
            key = element;
            o = searchOptions.getOption(key);
            StructEntry struct = itm.getAttribute(SearchOptions.getResourceName(key), false);
            retVal = SearchOptions.Utils.matchFlags(struct, o);
          } else {
            break;
          }
        }

        keyList = new String[] { SearchOptions.ITM_Category, SearchOptions.ITM_Price, SearchOptions.ITM_Enchantment,
            SearchOptions.ITM_MinLevel, SearchOptions.ITM_MinSTR, SearchOptions.ITM_MinSTRExtra,
            SearchOptions.ITM_MinCON, SearchOptions.ITM_MinDEX, SearchOptions.ITM_MinINT, SearchOptions.ITM_MinWIS,
            SearchOptions.ITM_MinCHA };
        for (String element : keyList) {
          if (retVal) {
            key = element;
            o = searchOptions.getOption(key);
            StructEntry struct = itm.getAttribute(SearchOptions.getResourceName(key), false);
            retVal = SearchOptions.Utils.matchNumber(struct, o);
          } else {
            break;
          }
        }

        keyList = new String[] { SearchOptions.ITM_Effect_Type1, SearchOptions.ITM_Effect_Type2,
            SearchOptions.ITM_Effect_Type3 };
        for (String element : keyList) {
          if (retVal) {
            boolean found = false;
            key = element;
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

        SearchOptions abilityOption = (SearchOptions) searchOptions.getOption(SearchOptions.ITM_Ability);
        if (retVal && abilityOption != null) {
          // indicates whether any ability options have been selected
          boolean hasAbilityOptions = false;
          keyList = new String[] { SearchOptions.ITM_Ability_Type, SearchOptions.ITM_Ability_Target,
              SearchOptions.ITM_Ability_Range, SearchOptions.ITM_Ability_Launcher, SearchOptions.ITM_Ability_Speed,
              SearchOptions.ITM_Ability_DiceCount, SearchOptions.ITM_Ability_DiceSize,
              SearchOptions.ITM_Ability_Charges, SearchOptions.ITM_Ability_DamageType,
              SearchOptions.ITM_Ability_Projectile, SearchOptions.ITM_Ability_Flags,
              SearchOptions.ITM_Ability_Effect_Type1, SearchOptions.ITM_Ability_Effect_Type2,
              SearchOptions.ITM_Ability_Effect_Type3 };
          for (String element : keyList) {
            hasAbilityOptions |= (abilityOption.getOption(element) != null);
          }

          // tracks matches for each option in every available ability
          final int abilityOptions = keyList.length; // number of supported spell ability options
          boolean[][] abilityMatches = new boolean[abilities.length][abilityOptions];
          for (int i = 0; i < abilities.length; i++) {
            Arrays.fill(abilityMatches[i], false);
          }

          for (int i = 0; i < abilities.length; i++) {
            if (abilities[i] != null) {
              for (int j = 0; j < 10; j++) {
                key = keyList[j];
                o = abilityOption.getOption(key);
                StructEntry struct = abilities[i].getAttribute(SearchOptions.getResourceName(key), false);
                abilityMatches[i][j] = SearchOptions.Utils.matchNumber(struct, o);
              }

              {
                key = keyList[10];
                o = abilityOption.getOption(key);
                StructEntry struct = abilities[i].getAttribute(SearchOptions.getResourceName(key), false);
                abilityMatches[i][10] = SearchOptions.Utils.matchFlags(struct, o);
              }

              for (int j = 11; j < keyList.length; j++) {
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
          for (boolean element : foundSingle) {
            resultSingle |= element;
          }
          resultSingle |= !hasAbilityOptions;

          boolean resultMulti = true;
          for (boolean element : foundMulti) {
            resultMulti &= element;
          }
          resultMulti |= !hasAbilityOptions;

          boolean isAbilitySingle;
          o = abilityOption.getOption(SearchOptions.ITM_Ability_MatchSingle);
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

        keyList = new String[] { SearchOptions.ITM_Custom1, SearchOptions.ITM_Custom2, SearchOptions.ITM_Custom3,
            SearchOptions.ITM_Custom4 };
        for (String element : keyList) {
          if (retVal) {
            key = element;
            o = searchOptions.getOption(key);
            retVal = SearchOptions.Utils.matchCustomFilter(itm, o);
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
