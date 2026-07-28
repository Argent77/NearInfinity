// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.event.ListDataListener;

import org.infinity.NearInfinity;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.menu.Bookmark;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.key.ResourceTreeModel;
import org.infinity.util.CharsetDetector;
import org.infinity.util.DataString;
import org.infinity.util.DebugTimer;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapCache;
import org.infinity.util.IniMapSection;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.Platform;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.DlcManager;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;

/**
 * Provides engine- and game-specific properties of the currently opened Infinity Engine game.<br>
 * <br>
 * Properties can be accessed by unique identifiers. The returned property can be of any type defined by the enum
 * {@link Profile.Type} or {@code null}.
 */
public final class Profile {
  /** Supported data types for properties. */
  public enum Type {
    /** Property data is of type {@link Boolean}. */
    BOOLEAN,
    /** Property data is of type {@link Integer}. */
    INTEGER,
    /** Property data is of type {@link String}. */
    STRING,
    /** Property data is of type {@link java.util.List}. */
    LIST,
    /** Property data is of type {@link java.util.Map}. */
    MAP,
    /** Property data is of type {@link java.nio.file.Path}. */
    PATH,
    /** Property data is of any custom data type. */
    OBJECT,
  }

  /** Supported Infinity Engine games. */
  public enum Game {
    /** Default for unrecognized or unsupported engine. */
    Unknown(Engine.Unknown, "Unknown Game"),
    /** Baldur's Gate */
    BG1(Engine.BG1, "Baldur's Gate"),
    /** Baldur's Gate: Tales of the Sword Coast */
    BG1TotSC(Engine.BG1, "Baldur's Gate: Tales of the Sword Coast"),
    /** Baldur's Gate II: Shadows of Amn */
    BG2SoA(Engine.BG2, "Baldur's Gate II: Shadows of Amn"),
    /** Baldur's Gate II: Throne of Bhaal */
    BG2ToB(Engine.BG2, "Baldur's Gate II: Throne of Bhaal"),
    /** BGTutu and EasyTutu */
    Tutu(Engine.BG2, "Baldur's Gate - Tutu"),
    /** Baldur's Gate Trilogy */
    BGT(Engine.BG2, "Baldur's Gate Trilogy"),
    /** Planescape: Torment */
    PST(Engine.PST, "Planescape: Torment"),
    /** Icewind Dale */
    IWD(Engine.IWD, "Icewind Dale"),
    /** Icewind Dale: Heart of Winter */
    IWDHoW(Engine.IWD, "Icewind Dale: Heart of Winter"),
    /** Icewind Dale: Trials of the Luremaster */
    IWDHowTotLM(Engine.IWD, "Icewind Dale: Trials of the Luremaster"),
    /** Icewind Dale II */
    IWD2(Engine.IWD2, "Icewind Dale II"),
    /** Icewind Dale II: Enhanced Edition */
    IWD2EE(Engine.IWD2, "Icewind Dale II: Enhanced Edition"),
    /** Baldur's Gate: Enhanced Edition */
    BG1EE(Engine.EE, "Baldur's Gate: Enhanced Edition"),
    /** Baldur's Gate: Siege of Dragonspear */
    BG1SoD(Engine.EE, "Baldur's Gate: Siege of Dragonspear"),
    /** Baldur's Gate II: Enhanced Edition */
    BG2EE(Engine.EE, "Baldur's Gate II: Enhanced Edition"),
    /** Icewind Dale: Enhanced Edition */
    IWDEE(Engine.EE, "Icewind Dale: Enhanced Edition"),
    /** Planescape Torment: Enhanced Edition */
    PSTEE(Engine.EE, "Planescape Torment: Enhanced Edition"),
    /** Enhanced Edition Trilogy */
    EET(Engine.EE, "Baldur's Gate - Enhanced Edition Trilogy"),
    ;

    private final Engine engine;
    private final String title;

    Game(Engine engine, String title) {
      this.engine = Objects.requireNonNull(engine);
      this.title = Objects.requireNonNull(title);
    }

    /** Returns the Infinity Engine type for the game. */
    public Engine getEngine() {
      return engine;
    }

    /** Returns whether the game is an Enhanced Edition game. */
    public boolean isEnhancedEdition() {
      return (engine == Engine.EE);
    }

    /** Returns the game title. */
    public String getTitle() {
      return title;
    }
  }

  /** Supported Infinity Engine types by feature levels. */
  public enum Engine {
    /** Default for unrecognized or unsupported games. */
    Unknown,
    /** Includes BG1 and BG1TotSC. */
    BG1,
    /** Includes BG2SoA, BG2ToB, Tutu and BGT. */
    BG2,
    /** Includes PST. */
    PST,
    /** Includes IWD, IWDHoW and IWDTotLM. */
    IWD,
    /** Includes IWD2 and IWD2EE. */
    IWD2,
    /** Includes BG1EE, BG1SoD, BG2EE, IWDEE, PSTEE and EET. */
    EE,
  }

  /**
   * Keys for querying global, game- or engine-specific properties.
   */
  public enum Key {
    // Static properties
    /** Property: ({@code String}) Current Near Infinity version. */
    GET_GLOBAL_NEARINFINITY_VERSION,
    /** Property: ({@code List<Game>}) List of supported games. */
    GET_GLOBAL_GAMES,
    /** Property: ({@code String}) The name of the override folder ("{@code Override}"). */
    GET_GLOBAL_OVERRIDE_NAME,
    /** Property: ({@code String}) The name of the parent language folder in Enhanced Edition games ("{@code Lang}"). */
    GET_GLOBAL_LANG_NAME,
    /** Property: ({@code String}) Returns "{@code dialog.tlk}". */
    GET_GLOBAL_DIALOG_NAME,
    /** Property: ({@code String}) Returns "{@code dialogf.tlk}". */
    GET_GLOBAL_DIALOG_NAME_FEMALE,

    // Static properties which require an additional parameter.
    /**
     * Property: ({@code List<String>}) Returns a list of extra folders for the specified game. Extra parameter: Desired
     * {@link Game}.
     */
    GET_GLOBAL_EXTRA_FOLDER_NAMES,
    /**
     * Property: ({@code List<String>}) Returns a list of save folders for the specified game. Extra parameter: Desired
     * {@link Game}.
     */
    GET_GLOBAL_SAVE_FOLDER_NAMES,
    /**
     * Property: ({@code String}) Returns the game's home folder name. Extra parameter: Desired <em>Enhanced
     * Edition</em> {@link Game}.
     */
    GET_GLOBAL_HOME_FOLDER_NAME,

    // Properties set at runtime
    /** Property: ({@link Game}) Game identifier. */
    GET_GAME_TYPE,
    /** Property: ({@link Game}) Game identifier of previously loaded game (if available). */
    GET_GAME_TYPE_PREVIOUS,
    /** Property: ({@code String}) Name of the game's root folder. */
    GET_GAME_ROOT_FOLDER_NAME,
    /** Property: ({@code String}) Name of the game's home folder. (Enhanced Editions only) */
    GET_GAME_HOME_FOLDER_NAME,
    /** Property: ({@code String}) Name of the currently selected game language folder. (Enhanced Editions only) */
    GET_GAME_LANG_FOLDER_NAME,
    /**
     * Property: ({@code List<String>}) List of available languages as language code for the current game. (Enhanced
     * Editions only)
     */
    GET_GAME_LANG_FOLDER_NAMES_AVAILABLE,
    /** Property: ({@code List<Path>}) List of valid root folder, sorted by priority in descending order. */
    GET_GAME_ROOT_FOLDERS_AVAILABLE,
    /** Property: ({@code Path}) Game's root folder. */
    GET_GAME_ROOT_FOLDER,
    /** Property: ({@code List<Path>}) List of available DLC root folders. (Enhanced Editions only) */
    GET_GAME_DLC_FOLDERS_AVAILABLE,
    /** Property: ({@code Path}) Game's home folder. (Enhanced Editions only) */
    GET_GAME_HOME_FOLDER,
    /** Property: ({@code Path}) Game's language folder. (Enhanced Editions only) */
    GET_GAME_LANG_FOLDER,
    /** Property: ({@code List<Path>}) List of available game language folders. (Enhanced Editions only) */
    GET_GAME_LANG_FOLDERS_AVAILABLE,
    /**
     * Property: ({@code Path}) Game's language root folder (where the actual language subfolder reside). (Enhanced
     * Editions only)
     */
    GET_GAME_LANG_FOLDER_BASE,
    /**
     * Property: ({@code List<Path>}) List of override folders to search for game resources, sorted by priority in
     * descending order.
     */
    GET_GAME_OVERRIDE_FOLDERS,
    /**
     * Property: ({@code List<String>}) List of extra folder names containing game-related resources, sorted
     * alphabetically in ascending order.
     */
    GET_GAME_EXTRA_FOLDER_NAMES,
    /**
     * Property: ({@code List<Path>}) List of extra folders containing game-related resources, sorted by root folder
     * priority (in descending order) and alphabetically in ascending order.
     */
    GET_GAME_EXTRA_FOLDERS,
    /** Property: ({@code List<String>}) List of save folder names, sorted alphabetically in ascending order. */
    GET_GAME_SAVE_FOLDER_NAMES,
    /** Property: ({@code Path}) The game's {@code chitin.key}. */
    GET_GAME_CHITIN_KEY,
    /**
     * Property: ({@code List<Path>}) List of {@code mod.key} files of available DLCs sorted by priority in ascending
     * order. (Enhanced Edition only)
     */
    GET_GAME_DLC_KEYS_AVAILABLE,
    /** Property: ({@code String}) Title of the game. */
    GET_GAME_TITLE,
    /**
     * Property: ({@code String}) A short user-defined description or name of the game. Can be used to tell specific
     * game installations apart.
     */
    GET_GAME_DESC,
    /** Property: ({@code String}) Name of the game's ini file. */
    GET_GAME_INI_NAME,
    /** Property: ({@code Path}) Path of the game's ini file. */
    GET_GAME_INI_FILE,
    /** Property: ({@code Path}) Path to the currently selected {@code dialog.tlk}. */
    GET_GAME_DIALOG_FILE,
    /**
     * Property: ({@code Path}) Path to the currently selected female {@code dialogf.tlk}. Returns {@code null} if the
     * language does not require a dialogf.tlk.
     */
    GET_GAME_DIALOGF_FILE,
    /**
     * Property: ({@code List<Path>}) List of folders containing BIFF archives. (Sorted by priority in descending order
     * for Enhanced Editions, sorted by entries found in ini file for non-enhanced games)
     */
    GET_GAME_BIFF_FOLDERS,
    /**
     * Property: ({@code Map<String, String>}) Map of "Equipped appearance" codes with associated descriptions. Map is
     * generated on first call of {@code getEquippedAppearanceMap()}.
     */
    GET_GAME_EQUIPPED_APPEARANCES,
    /**
     * Property: ({@code String}) The autodetected character set used to encode or decode strings in the game.
     * Can be overridden by NI's preferences.
     */
    GET_GAME_CHARSET,
    /** Property: ({@code Boolean}) Has current game been enhanced by TobEx? */
    IS_GAME_TOBEX,
    /** Property: ({@code Boolean}) Has current game been enhanced by EEex? */
    IS_GAME_EEEX,
    /** Property: ({@code Boolean}) Has type of current game been forcibly set? */
    IS_FORCED_GAME,
    /**
     * Property: ({@code Integer}) Returns the Infinity Animations installed version:
     *
     * <pre>
     *            0: not installed
     *            1: old IA format (v5 or earlier)
     *            2: new format (v6 or later)
     * </pre>
     */
    GET_INFINITY_ANIMATIONS,
    /** Property: ({@code String}) Returns a unique session hash code for the current game. */
    GET_GAME_SESSION_ID,

    /** Property: ({@code Boolean}) Are {@code 2DA} resources supported? */
    IS_SUPPORTED_2DA,
    /** Property: ({@code Boolean}) Are {@code ACM} resources supported? */
    IS_SUPPORTED_ACM,
    /** Property: ({@code Boolean}) Are {@code ARE V1.0} resources supported? */
    IS_SUPPORTED_ARE_V10,
    /** Property: ({@code Boolean}) Are {@code ARE V9.1} resources supported? */
    IS_SUPPORTED_ARE_V91,
    /** Property: ({@code Boolean}) Are {@code BAM V1} resources supported? */
    IS_SUPPORTED_BAM_V1,
    /** Property: ({@code Boolean}) Are {@code BAM V1} resources supported? */
    IS_SUPPORTED_BAMC_V1,
    /** Property: ({@code Boolean}) Are {@code BAM V1} resources with alpha palette supported? */
    IS_SUPPORTED_BAM_V1_ALPHA,
    /** Property: ({@code Boolean}) Are {@code BAM V2} resources supported? */
    IS_SUPPORTED_BAM_V2,
    /** Property: ({@code Boolean}) Are {@code BCS} resources supported? */
    IS_SUPPORTED_BCS,
    /** Property: ({@code Boolean}) Are uncompressed {@code BIFF V1} resources supported? */
    IS_SUPPORTED_BIFF,
    /** Property: ({@code Boolean}) Are compressed {@code BIF V1.0} resources supported? */
    IS_SUPPORTED_BIF,
    /** Property: ({@code Boolean}) Are compressed {@code BIFC V1.0} resources supported? */
    IS_SUPPORTED_BIFC,
    /** Property: ({@code Boolean}) Are {@code BIK} resources supported? */
    IS_SUPPORTED_BIK,
    /** Property: ({@code Boolean}) Are {@code BIO} resources supported? */
    IS_SUPPORTED_BIO,
    /** Property: ({@code Boolean}) Are paletted {@code BMP} resources supported? */
    IS_SUPPORTED_BMP_PAL,
    /** Property: ({@code Boolean}) Are alpha-blended {@code BMP} resources supported? */
    IS_SUPPORTED_BMP_ALPHA,
    /** Property: ({@code Boolean}) Are {@code CHR V1.0} resources supported? */
    IS_SUPPORTED_CHR_V10,
    /** Property: ({@code Boolean}) Are {@code CHR V2.0} resources supported? */
    IS_SUPPORTED_CHR_V20,
    /** Property: ({@code Boolean}) Are {@code CHR V2.1} resources supported? */
    IS_SUPPORTED_CHR_V21,
    /** Property: ({@code Boolean}) Are {@code CHR V2.2} resources supported? */
    IS_SUPPORTED_CHR_V22,
    /** Property: ({@code Boolean}) Are {@code CHU} resources supported? */
    IS_SUPPORTED_CHU,
    /** Property: ({@code Boolean}) Are {@code CRE V1.0} resources supported? */
    IS_SUPPORTED_CRE_V10,
    /** Property: ({@code Boolean}) Are {@code CRE V1.2} resources supported? */
    IS_SUPPORTED_CRE_V12,
    /** Property: ({@code Boolean}) Are {@code CRE V2.2} resources supported? */
    IS_SUPPORTED_CRE_V22,
    /** Property: ({@code Boolean}) Are {@code CRE V9.0} resources supported? */
    IS_SUPPORTED_CRE_V90,
    /** Property: ({@code Boolean}) Are {@code DLG} resources supported? */
    IS_SUPPORTED_DLG,
    /** Property: ({@code Boolean}) Are {@code EFF} resources supported? */
    IS_SUPPORTED_EFF,
    /** Property: ({@code Boolean}) Are {@code FNT} resources supported? */
    IS_SUPPORTED_FNT,
    /** Property: ({@code Boolean}) Are {@code GAM V1.1} resources supported? */
    IS_SUPPORTED_GAM_V11,
    /** Property: ({@code Boolean}) Are {@code GAM V2.0} resources supported? */
    IS_SUPPORTED_GAM_V20,
    /** Property: ({@code Boolean}) Are {@code GAM V2.1} resources supported? */
    IS_SUPPORTED_GAM_V21,
    /** Property: ({@code Boolean}) Are {@code GAM V2.2} resources supported? */
    IS_SUPPORTED_GAM_V22,
    /** Property: ({@code Boolean}) Are {@code GLSL} resources supported? */
    IS_SUPPORTED_GLSL,
    /** Property: ({@code Boolean}) Are {@code GUI} resources supported? */
    IS_SUPPORTED_GUI,
    /** Property: ({@code Boolean}) Are {@code IDS} resources supported? */
    IS_SUPPORTED_IDS,
    /** Property: ({@code Boolean}) Are {@code INI} resources supported? */
    IS_SUPPORTED_INI,
    /** Property: ({@code Boolean}) Are {@code ITM V1.0} resources supported? */
    IS_SUPPORTED_ITM_V10,
    /** Property: ({@code Boolean}) Are {@code ITM V1.1} resources supported? */
    IS_SUPPORTED_ITM_V11,
    /** Property: ({@code Boolean}) Are {@code ITM V2.0} resources supported? */
    IS_SUPPORTED_ITM_V20,
    /** Property: ({@code Boolean}) Are {@code KEY} resources supported? */
    IS_SUPPORTED_KEY,
    /** Property: ({@code Boolean}) Are {@code LOG} resources supported? */
    IS_SUPPORTED_LOG,
    /** Property: ({@code Boolean}) Are {@code LUA} resources supported? */
    IS_SUPPORTED_LUA,
    /** Property: ({@code Boolean}) Are {@code MAZE} resources supported? */
    IS_SUPPORTED_MAZE,
    /** Property: ({@code Boolean}) Are {@code MENU} resources supported? */
    IS_SUPPORTED_MENU,
    /** Property: ({@code Boolean}) Are {@code MOS V1} resources supported? */
    IS_SUPPORTED_MOS_V1,
    /** Property: ({@code Boolean}) Are {@code MOSC V1} resources supported? */
    IS_SUPPORTED_MOSC_V1,
    /** Property: ({@code Boolean}) Are {@code MOS V2} resources supported? */
    IS_SUPPORTED_MOS_V2,
    /** Property: ({@code Boolean}) Are {@code MUS} resources supported? */
    IS_SUPPORTED_MUS,
    /** Property: ({@code Boolean}) Are {@code MVE} resources supported? */
    IS_SUPPORTED_MVE,
    /** Property: ({@code Boolean}) Are {@code OGG} resources supported? */
    IS_SUPPORTED_OGG,
    /** Property: ({@code Boolean}) Are {@code PLT} resources supported? */
    IS_SUPPORTED_PLT,
    /** Property: ({@code Boolean}) Are {@code PNG} resources supported? */
    IS_SUPPORTED_PNG,
    /** Property: ({@code Boolean}) Are {@code PRO} resources supported? */
    IS_SUPPORTED_PRO,
    /** Property: ({@code Boolean}) Are {@code PVRZ} resources supported? */
    IS_SUPPORTED_PVRZ,
    /** Property: ({@code Boolean}) Are {@code RES} resources supported? */
    IS_SUPPORTED_RES,
    /** Property: ({@code Boolean}) Are {@code SAV} resources supported? */
    IS_SUPPORTED_SAV,
    /** Property: ({@code Boolean}) Are {@code SPL V1} resources supported? */
    IS_SUPPORTED_SPL_V1,
    /** Property: ({@code Boolean}) Are {@code SPL V2} resources supported? */
    IS_SUPPORTED_SPL_V2,
    /** Property: ({@code Boolean}) Are {@code SQL} resources supported? */
    IS_SUPPORTED_SQL,
    /** Property: ({@code Boolean}) Are (PST) {@code SRC} resources supported? */
    IS_SUPPORTED_SRC_PST,
    /** Property: ({@code Boolean}) Are (IWD2) {@code SRC} resources supported? */
    IS_SUPPORTED_SRC_IWD2,
    /** Property: ({@code Boolean}) Are {@code STO V1.0} resources supported? */
    IS_SUPPORTED_STO_V10,
    /** Property: ({@code Boolean}) Are {@code STO V1.1} resources supported? */
    IS_SUPPORTED_STO_V11,
    /** Property: ({@code Boolean}) Are {@code STO V9.0} resources supported? */
    IS_SUPPORTED_STO_V90,
    /** Property: ({@code Boolean}) Are (palette-based) {@code TIS V1} resources supported? */
    IS_SUPPORTED_TIS_V1,
    /** Property: ({@code Boolean}) Are (PVRZ-based) {@code TIS V2} resources supported? */
    IS_SUPPORTED_TIS_V2,
    /** Property: ({@code Boolean}) Are {@code TLK} resources supported? */
    IS_SUPPORTED_TLK,
    /** Property: ({@code Boolean}) Are {@code TO V1} (TOH/TOT) resources supported? */
    IS_SUPPORTED_TO_V1,
    /** Property: ({@code Boolean}) Are {@code TO V2} (TOH only) resources supported? */
    IS_SUPPORTED_TO_V2,
    /** Property: ({@code Boolean}) Are {@code TTF} resources supported? */
    IS_SUPPORTED_TTF,
    /** Property: ({@code Boolean}) Are {@code VAR} resources supported? */
    IS_SUPPORTED_VAR,
    /** Property: ({@code Boolean}) Are {@code VEF} resources supported? */
    IS_SUPPORTED_VEF,
    /** Property: ({@code Boolean}) Are {@code VVC} resources supported? */
    IS_SUPPORTED_VVC,
    /** Property: ({@code Boolean}) Are {@code WAV} resources supported? */
    IS_SUPPORTED_WAV,
    /** Property: ({@code Boolean}) Are {@code WAVC} resources supported? */
    IS_SUPPORTED_WAVC,
    /** Property: ({@code Boolean}) Are {@code WBM} resources supported? */
    IS_SUPPORTED_WBM,
    /** Property: ({@code Boolean}) Are {@code WED} resources supported? */
    IS_SUPPORTED_WED,
    /** Property: ({@code Boolean}) Are {@code WFX} resources supported? */
    IS_SUPPORTED_WFX,
    /** Property: ({@code Boolean}) Are {@code WMP} resources supported? */
    IS_SUPPORTED_WMP,

    /** Property: ({@code Boolean}) Are Kits supported? */
    IS_SUPPORTED_KITS,
    /** Property: ({@code String}) The name of the ALIGNMENT IDS resource. */
    GET_IDS_ALIGNMENT,
    /** Property: ({@code String}) The name of the .GAM file that is stored in saved games. */
    GET_GAM_NAME,
    /** Property: ({@code String}) The name of the .SAV file that is stored in saved games. */
    GET_SAV_NAME,
    /** Property: ({@code Boolean}) Indices whether overlays in tilesets are stenciled. */
    IS_TILESET_STENCILED,
  }

  // Container for Property entries
  private static final EnumMap<Key, Profile.Property> PROPERTIES = new EnumMap<>(Key.class);
  // List of supported extra folders for all supported games
  private static final EnumMap<Game, List<String>> GAME_EXTRA_FOLDERS = new EnumMap<>(Game.class);
  // List of supported saved game folders for all supported games
  private static final EnumMap<Game, List<String>> GAME_SAVE_FOLDERS = new EnumMap<>(Game.class);
  // Home folder name for Enhanced Edition Games
  private static final EnumMap<Game, String> GAME_HOME_FOLDER = new EnumMap<>(Game.class);
  // Set of resource extensions supported by Infinity Engine games
  private static final HashSet<String> SUPPORTED_RESOURCE_TYPES = new HashSet<>();
  private static final HashMap<String, String> KNOWN_EQUIPPED_APPEARANCE = new HashMap<>();
  // A list of potential game executable filenames for each game
  private static final EnumMap<Game, EnumMap<Platform.OS, List<String>>> DEFAULT_GAME_BINARIES = new EnumMap<>(
      Game.class);

  // Using the singleton approach
  private static Profile instance = null;

  static {
    // initializing extra folders for each supported game
    final String[] PST_EXTRA_FOLDERS = { "Music", "Save", "Temp" };
    final String[] BG_EXTRA_FOLDERS = { "Characters", "MPSave", "Music", "Portraits", "Save", "Scripts", "ScrnShot",
        "Sounds", "Temp", "TempSave" };
    final String[] EE_EXTRA_FOLDERS = { "BPSave", "Characters", "Fonts", "ImportSave", "ImportBPSave", "Movies",
        "MPBPSave", "MPSave", "Music", "Portraits", "Save", "Sounds", "ScrnShot", "Scripts", "Temp", "TempSave" };
    GAME_EXTRA_FOLDERS.put(Game.Unknown, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BG1, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BG1TotSC, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BG2SoA, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BG2ToB, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.Tutu, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BGT, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.PST, new ArrayList<>(Arrays.asList(PST_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.IWD, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.IWDHoW, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.IWDHowTotLM, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.IWD2, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.IWD2EE, new ArrayList<>(Arrays.asList(BG_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BG1EE, new ArrayList<>(Arrays.asList(EE_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BG1SoD, new ArrayList<>(Arrays.asList(EE_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.BG2EE, new ArrayList<>(Arrays.asList(EE_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.IWDEE, new ArrayList<>(Arrays.asList(EE_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.PSTEE, new ArrayList<>(Arrays.asList(EE_EXTRA_FOLDERS)));
    GAME_EXTRA_FOLDERS.put(Game.EET, new ArrayList<>(Arrays.asList(EE_EXTRA_FOLDERS)));

    final String[] BG_SAVE_FOLDERS = { "MPSave", "Save" };
    final String[] EE_SAVE_FOLDERS = { "BPSave", "CloudSave", "ImportBPSave", "ImportSave", "MPBPSave", "MPSave", "Save" };
    GAME_SAVE_FOLDERS.put(Game.Unknown, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BG1, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BG1TotSC, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BG2SoA, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BG2ToB, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.Tutu, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BGT, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.PST, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.IWD, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.IWDHoW, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.IWDHowTotLM, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.IWD2, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.IWD2EE, new ArrayList<>(Arrays.asList(BG_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BG1EE, new ArrayList<>(Arrays.asList(EE_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BG1SoD, new ArrayList<>(Arrays.asList(EE_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.BG2EE, new ArrayList<>(Arrays.asList(EE_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.IWDEE, new ArrayList<>(Arrays.asList(EE_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.PSTEE, new ArrayList<>(Arrays.asList(EE_SAVE_FOLDERS)));
    GAME_SAVE_FOLDERS.put(Game.EET, new ArrayList<>(Arrays.asList(EE_SAVE_FOLDERS)));

    // initializing home folder names for Enhanced Edition games
    GAME_HOME_FOLDER.put(Game.BG1EE, "Baldur's Gate - Enhanced Edition");
    GAME_HOME_FOLDER.put(Game.BG1SoD, GAME_HOME_FOLDER.get(Game.BG1EE));
    GAME_HOME_FOLDER.put(Game.BG2EE, "Baldur's Gate II - Enhanced Edition");
    GAME_HOME_FOLDER.put(Game.EET, GAME_HOME_FOLDER.get(Game.BG2EE));
    GAME_HOME_FOLDER.put(Game.IWDEE, "Icewind Dale - Enhanced Edition");
    GAME_HOME_FOLDER.put(Game.PSTEE, "Planescape Torment - Enhanced Edition");

    // initializing known equipped appearance codes for items
    KNOWN_EQUIPPED_APPEARANCE.put("  ", "None");
    KNOWN_EQUIPPED_APPEARANCE.put("2A", "Leather armor");
    KNOWN_EQUIPPED_APPEARANCE.put("3A", "Chain mail");
    KNOWN_EQUIPPED_APPEARANCE.put("4A", "Plate mail");
    KNOWN_EQUIPPED_APPEARANCE.put("2W", "Mage robe 1");
    KNOWN_EQUIPPED_APPEARANCE.put("3W", "Mage robe 2");
    KNOWN_EQUIPPED_APPEARANCE.put("4W", "Mage robe 3");
    KNOWN_EQUIPPED_APPEARANCE.put("AX", "Battle axe");
    KNOWN_EQUIPPED_APPEARANCE.put("BS", "Shortbow");
    KNOWN_EQUIPPED_APPEARANCE.put("BW", "Longbow");
    KNOWN_EQUIPPED_APPEARANCE.put("C0", "Small shield (alternate 1)");
    KNOWN_EQUIPPED_APPEARANCE.put("C1", "Medium shield (alternate 1)");
    KNOWN_EQUIPPED_APPEARANCE.put("C2", "Large shield (alternate 1)");
    KNOWN_EQUIPPED_APPEARANCE.put("C3", "Medium shield (alternate 2)");
    KNOWN_EQUIPPED_APPEARANCE.put("C4", "Small shield (alternate 2)");
    KNOWN_EQUIPPED_APPEARANCE.put("C5", "Large shield (alternate 2)");
    KNOWN_EQUIPPED_APPEARANCE.put("C6", "Large shield (alternate 3)");
    KNOWN_EQUIPPED_APPEARANCE.put("C7", "Medium shield (alternate 3)");
    KNOWN_EQUIPPED_APPEARANCE.put("CB", "Crossbow");
    KNOWN_EQUIPPED_APPEARANCE.put("CL", "Club");
    KNOWN_EQUIPPED_APPEARANCE.put("D0", "Small shield (alternate 3)");
    KNOWN_EQUIPPED_APPEARANCE.put("D1", "Buckler");
    KNOWN_EQUIPPED_APPEARANCE.put("D2", "Small shield");
    KNOWN_EQUIPPED_APPEARANCE.put("D3", "Medium shield");
    KNOWN_EQUIPPED_APPEARANCE.put("D4", "Large shield");
    KNOWN_EQUIPPED_APPEARANCE.put("DD", "Dagger");
    KNOWN_EQUIPPED_APPEARANCE.put("F0", "Flail (alternate 1)");
    KNOWN_EQUIPPED_APPEARANCE.put("F1", "Flail (alternate 2)");
    KNOWN_EQUIPPED_APPEARANCE.put("F2", "Flaming sword (blue)");
    KNOWN_EQUIPPED_APPEARANCE.put("F3", "Flail (alternate 3");
    KNOWN_EQUIPPED_APPEARANCE.put("FL", "Flail");
    KNOWN_EQUIPPED_APPEARANCE.put("FS", "Flaming sword");
    KNOWN_EQUIPPED_APPEARANCE.put("GS", "Glowing staff");
    KNOWN_EQUIPPED_APPEARANCE.put("H0", "Helmet 1");
    KNOWN_EQUIPPED_APPEARANCE.put("H1", "Helmet 2");
    KNOWN_EQUIPPED_APPEARANCE.put("H2", "Helmet 3");
    KNOWN_EQUIPPED_APPEARANCE.put("H3", "Helmet 4");
    KNOWN_EQUIPPED_APPEARANCE.put("H4", "Helmet 5");
    KNOWN_EQUIPPED_APPEARANCE.put("H5", "Helmet 6");
    KNOWN_EQUIPPED_APPEARANCE.put("H6", "Helmet 7");
    KNOWN_EQUIPPED_APPEARANCE.put("H7", "Helmet 8");
    KNOWN_EQUIPPED_APPEARANCE.put("HB", "Halberd");
    KNOWN_EQUIPPED_APPEARANCE.put("J0", "Helmet 9");
    KNOWN_EQUIPPED_APPEARANCE.put("J1", "Helmet 10");
    KNOWN_EQUIPPED_APPEARANCE.put("J2", "Helmet 11");
    KNOWN_EQUIPPED_APPEARANCE.put("J3", "Helmet 12");
    KNOWN_EQUIPPED_APPEARANCE.put("J4", "Helmet 13");
    KNOWN_EQUIPPED_APPEARANCE.put("J5", "Helmet 14");
    KNOWN_EQUIPPED_APPEARANCE.put("J6", "Helmet 15");
    KNOWN_EQUIPPED_APPEARANCE.put("J7", "Helmet 16");
    KNOWN_EQUIPPED_APPEARANCE.put("J8", "Helmet 17");
    KNOWN_EQUIPPED_APPEARANCE.put("J9", "Helmet 18");
    KNOWN_EQUIPPED_APPEARANCE.put("JA", "Helmet 19");
    KNOWN_EQUIPPED_APPEARANCE.put("JB", "Circlet");
    KNOWN_EQUIPPED_APPEARANCE.put("JC", "Helmet 20");
    KNOWN_EQUIPPED_APPEARANCE.put("M2", "Mace (alternate)");
    KNOWN_EQUIPPED_APPEARANCE.put("MC", "Mace");
    KNOWN_EQUIPPED_APPEARANCE.put("MS", "Morning star");
    KNOWN_EQUIPPED_APPEARANCE.put("Q2", "Quarterstaff (alternate 1)");
    KNOWN_EQUIPPED_APPEARANCE.put("Q3", "Quarterstaff (alternate 2)");
    KNOWN_EQUIPPED_APPEARANCE.put("Q4", "Quarterstaff (alternate 3)");
    KNOWN_EQUIPPED_APPEARANCE.put("QS", "Quarterstaff");
    KNOWN_EQUIPPED_APPEARANCE.put("S0", "Bastard sword");
    KNOWN_EQUIPPED_APPEARANCE.put("S1", "Long sword");
    KNOWN_EQUIPPED_APPEARANCE.put("S2", "Two-handed sword");
    KNOWN_EQUIPPED_APPEARANCE.put("S3", "Katana");
    KNOWN_EQUIPPED_APPEARANCE.put("SC", "Scimitar");
    KNOWN_EQUIPPED_APPEARANCE.put("SL", "Sling");
    KNOWN_EQUIPPED_APPEARANCE.put("SP", "Spear");
    KNOWN_EQUIPPED_APPEARANCE.put("SS", "Short sword");
    KNOWN_EQUIPPED_APPEARANCE.put("WH", "War hammer");
    KNOWN_EQUIPPED_APPEARANCE.put("YW", "Wings (male)");
    KNOWN_EQUIPPED_APPEARANCE.put("ZW", "Wings (female)");

    // initializing potential game executable filenames
    initDefaultGameBinaries();

    // static properties are always available
    initStaticProperties();
  }

  /**
   * Converts the string representation of a game type into a {@link Game} enum type. Falls back to {@code Game.Unknown}
   * on error.
   */
  public static Game gameFromString(String gameName) {
    if (gameName != null && !gameName.isEmpty()) {
      try {
        return Enum.valueOf(Game.class, gameName);
      } catch (IllegalArgumentException e) {
        Logger.warn("Unknown game type \"{}\" specified. Falling back to \"Unknown\".", gameName);
      }
    }
    return Game.Unknown;
  }

  /**
   * Initializes properties of a new game.
   *
   * @param keyFile Full path to the chitin.key of the opened game.
   * @return {@code true} if the game has been initialized successfully, {@code false} otherwise.
   */
  public static boolean openGame(Path keyFile) {
    return openGame(keyFile, null, null);
  }

  /**
   * Initializes properties of a new game.
   *
   * @param keyFile Full path to the chitin.key of the opened game.
   * @param desc    An optional description associated with the game.
   * @return {@code true} if the game has been initialized successfully, {@code false} otherwise.
   */
  public static boolean openGame(Path keyFile, String desc) {
    return openGame(keyFile, desc, null);
  }

  /**
   * Initializes properties of a new game.
   *
   * @param keyFile    Full path to the chitin.key of the opened game.
   * @param desc       An optional description associated with the game.
   * @param forcedGame Set to non-{@code null} to enforce a specific game type.
   * @return {@code true} if the game has been initialized successfully, {@code false} otherwise.
   */
  public static boolean openGame(Path keyFile, String desc, Game forcedGame) {
    try {
      closeGame();
      final DebugTimer timer = new DebugTimer();
      instance = new Profile(keyFile, desc, forcedGame);
      Logger.info(timer.getTimerFormatted("Initializing game resources"));
      return true;
    } catch (Exception e) {
      Logger.error(e);
    }
    closeGame();
    return false;
  }

  /**
   * Returns whether a game has been initialized.
   *
   * @return {@code true} if a game has been initialized, {@code false} if not.
   */
  public static boolean isGameOpen() {
    return (instance != null);
  }

  /**
   * Returns whether a property of the specified key is available.
   *
   * @param key A key to identify the property.
   * @return {@code true} if the key is pointing to an existing property, {@code false} otherwise.
   */
  public static boolean hasProperty(Key key) {
    return PROPERTIES.containsKey(key);
  }

  /**
   * Returns the data type of the specified property.
   *
   * @param key The unique key identifying the property.
   * @return The data type of the property, or {@code null} if not available.
   */
  public static Type getPropertyType(Key key) {
    Property prop = getEntry(key);
    if (prop != null) {
      return prop.getType();
    } else {
      return null;
    }
  }

  /**
   * Returns the actual data of the specified property.
   *
   * @param key The unique key identifying the property.
   * @return The data of the property, or {@code null} if not available.
   */
  public static <T> T getProperty(Key key) {
    return getProperty(key, null);
  }

  /**
   * Returns the actual data of the specified property.
   *
   * @param key   The unique key identifying the property.
   * @param param An additional parameter required by a small number of properties. Specify {@code null} to always
   *              return the parent structure of the property.
   * @return The data of the property, or {@code null} if not available.
   */
  public static <T> T getProperty(Key key, Object param) {
    Property prop = getEntry(key);
    if (prop != null) {
      if (param == null) {
        return prop.getData();
      } else {
        // handling properties which require an additional parameter
        switch (key) {
          case GET_GLOBAL_EXTRA_FOLDER_NAMES:
          case GET_GLOBAL_SAVE_FOLDER_NAMES:
          case GET_GLOBAL_HOME_FOLDER_NAME:
            if (param instanceof Game) {
              final EnumMap<?, T> map = prop.getData();
              return map.get(param);
            }
            break;
          default:
        }
      }
    }
    return null;
  }

  /**
   * Updates the value of the Property entry specified by {@code key} and returns the previous Property value.
   *
   * @param key  The Property key.
   * @param data The new value of the Property instance.
   * @return the previous value of the specified Property, or {@code null} if not available or applicable.
   */
  public static Object updateProperty(Key key, Object data) {
    // Properties requiring extra parameters cannot be updated
    if (key != null) {
      Property prop = PROPERTIES.get(key);
      if (prop != null) {
        return prop.setData(data);
      }
    }
    return null;
  }

  /**
   * Adds or updates a Property entry.
   *
   * @param key  The Property key.
   * @param type The data type.
   * @param data The data of the Property instance.
   * @return {@code true} if Property has been added or updated, {@code false} otherwise.
   */
  public static boolean addProperty(Key key, Type type, Object data) {
    if (key != null) {
      if (PROPERTIES.containsKey(key)) {
        updateProperty(key, data);
        return true;
      } else {
        addEntry(key, type, data);
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the name of the default override folder (without path). Does not need an opened game to return a valid
   * value.
   *
   * @return The name of the override folder.
   */
  public static String getOverrideFolderName() {
    return getProperty(Key.GET_GLOBAL_OVERRIDE_NAME);
  }

  /**
   * Returns the game type of the current game.
   *
   * @return The {@link Game} type of the current game or {@code Game.Unknown} otherwise.
   */
  public static Game getGame() {
    Object ret = getProperty(Key.GET_GAME_TYPE);
    return (ret instanceof Game) ? (Game) ret : Game.Unknown;
  }

  /**
   * Returns the engine type of the current game.
   *
   * @return The {@link Engine} type of the current game or {@code Engine.Unknown} otherwise.
   */
  public static Engine getEngine() {
    Engine engine = getGame().getEngine();
    if (engine == null) {
      engine = Engine.Unknown;
    }
    return engine;
  }

  /**
   * Returns whether the current game is an Enhanced Edition game.
   *
   * @return {@code true} if the current game is an Enhanced Edition game, {@code false} otherwise.
   */
  public static boolean isEnhancedEdition() {
    if (getGame() != null) {
      return getGame().isEnhancedEdition();
    }
    return false;
  }

  /**
   * Returns whether the specified {@link Game} is an Enhanced Edition game.
   *
   * @param game The game to check.
   * @return {@code true} if the game is an Enhanced Edition game, {@code false} otherwise.
   */
  public static boolean isEnhancedEdition(Game game) {
    if (game != null) {
      return game.isEnhancedEdition();
    }
    return false;
  }

  /**
   * Returns whether the specified game engine supports compressed BAM V1 (BAMC) resources.
   *
   * @param game The game to check.
   * @return {@code true} if BAMC resources are supported, {@code false} otherwise.
   */
  public static boolean isBamcSupported(Game game) {
    final Engine engine = (game != null) ? game.getEngine() : Engine.Unknown;
    return engine == Engine.BG2 || engine == Engine.IWD || engine == Engine.IWD2 || engine == Engine.EE;
  }

  /**
   * Returns whether the specified game engine supports BAM V2/PVRZ resources.
   *
   * @param game The game to check.
   * @return {@code true} if BAM V2 resources are supported, {@code false} otherwise.
   */
  public static boolean isBamV2Supported(Game game) {
    return isEnhancedEdition(game);
  }

  /**
   * Returns the game's root folder.
   *
   * @return The game's root folder as {@link Path} object.
   */
  public static Path getGameRoot() {
    return getProperty(Key.GET_GAME_ROOT_FOLDER);
  }

  /**
   * Returns the game's home folder. A Non-enhanced Edition game will always return the game's root folder instead.
   *
   * @return The game's home folder as {@link Path} object.
   */
  public static Path getHomeRoot() {
    Object ret = getProperty(Key.GET_GAME_HOME_FOLDER);
    return (ret instanceof Path) ? (Path) ret : getGameRoot();
  }

  /**
   * Returns the game's language folder (where the effective dialog.tlk is located). A Non-enhanced Edition game will
   * always return the game's root folder instead.
   *
   * @return The game's effective language folder as @{link Path} object.
   */
  public static Path getLanguageRoot() {
    Object ret = getProperty(Key.GET_GAME_LANG_FOLDER);
    return (ret instanceof Path) ? (Path) ret : getGameRoot();
  }

  /**
   * Returns all available root folders of the game as a list of {@link Path} objects, sorted by priority in descending
   * order. (which includes the home, language and optional DLC roots for Enhanced Edition games).
   *
   * @return A list of {@code Path} objects specifying the game's root folders.
   */
  public static List<Path> getRootFolders() {
    return getProperty(Key.GET_GAME_ROOT_FOLDERS_AVAILABLE);
  }

  /**
   * Returns a list of override folders supported by the current game, sorted by priority in descending order.
   *
   * @param includeExtraFolders Whether to include extra folders that are treated as override folders by the current
   *                            game.
   * @return List of {@link Path} objects for all available override folders.
   */
  public static List<Path> getOverrideFolders(boolean includeExtraFolders) {
    List<Path> ret = getProperty(Key.GET_GAME_OVERRIDE_FOLDERS);
    if (ret != null && !includeExtraFolders) {
      String overrideName = getProperty(Key.GET_GLOBAL_OVERRIDE_NAME);
      List<Path> overrides = new ArrayList<>();
      ret.forEach(path -> {
        if (path.getFileName().toString().equalsIgnoreCase(overrideName)) {
          overrides.add(path);
        }
      });
      ret = overrides;
    }
    if (ret == null) {
      ret = new ArrayList<>();
    }
    return ret;
  }

  /**
   * Returns the full path to the chitin.key of the currently open game.
   *
   * @return The full path of the chitin.key as {@link Path} object.
   */
  public static Path getChitinKey() {
    Object ret = getProperty(Key.GET_GAME_CHITIN_KEY);
    return (ret instanceof Path) ? (Path) ret : null;
  }

  /**
   * Returns the default character set used by the currently open game to encode or decode game strings.
   *
   * <p>
   * <b>Note:</b> Charset detection uses heuristics for the original games and may not be fully accurate.
   * It falls back to {@code windows-1252} if the charset could not be autodetected.
   * </p>
   *
   * @return Character set as {@link Charset} object.
   */
  public static Charset getDefaultCharset() {
    final String retVal = getProperty(Key.GET_GAME_CHARSET);
    return Charset.forName((retVal != null) ? retVal : Misc.CHARSET_DEFAULT.name());
  }

  /**
   * Updates language-related Properties with the specified game language. (Enhanced Editions only)
   *
   * @param language The name of the language subfolder.
   * @return {@code true} if the game language has been updated successfully, {@code false} otherwise.
   */
  public static boolean updateGameLanguage(String language) {
    if (isEnhancedEdition() && language != null) {
      List<String> languages = getProperty(Key.GET_GAME_LANG_FOLDER_NAMES_AVAILABLE);
      for (final String curLang : languages) {
        if (curLang.equalsIgnoreCase(language)) {
          instance.initRootDirs();
          instance.initOverrides();
          // updating language names and folders
          updateProperty(Key.GET_GAME_LANG_FOLDER_NAME, curLang);
          Path langPath = FileManager.query((Path) getProperty(Key.GET_GAME_LANG_FOLDER_BASE), curLang);
          updateProperty(Key.GET_GAME_LANG_FOLDER, langPath);
          // updating dialog.tlks
          updateProperty(Key.GET_GAME_DIALOG_FILE,
              FileManager.query(langPath, getProperty(Key.GET_GLOBAL_DIALOG_NAME)));
          Path femaleTlkFile = FileManager.query(langPath, getProperty(Key.GET_GLOBAL_DIALOG_NAME_FEMALE));
          if (FileEx.create(femaleTlkFile).isFile()) {
            addProperty(Key.GET_GAME_DIALOGF_FILE, Type.PATH, femaleTlkFile);
          } else {
            updateProperty(Key.GET_GAME_DIALOGF_FILE, null);
          }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns whether the specified resource type is supported by Infinity Engine games.
   *
   * @param extension The file extension to check.
   * @return {@code true} if the specified resource type is supported, {@code false} otherwise.
   */
  public static boolean isResourceTypeSupported(String extension) {
    if (SUPPORTED_RESOURCE_TYPES.isEmpty()) {
      String[] types = getAvailableResourceTypes(true);
      Collections.addAll(SUPPORTED_RESOURCE_TYPES, types);
    }

    if (extension != null) {
      extension = extension.trim().toUpperCase(Locale.ENGLISH);
      if (!extension.isEmpty()) {
        if (extension.charAt(0) == '.') {
          extension = extension.substring(1);
        }
        return SUPPORTED_RESOURCE_TYPES.contains(extension);
      }
    }
    return false;
  }

  /**
   * Returns a list of supported resource types by the current game.
   *
   * @return String array containing format extensions.
   */
  public static String[] getAvailableResourceTypes() {
    return getAvailableResourceTypes(false);
  }

  /**
   * Returns a list of known or supported resource types by the current game.
   *
   * @param ignoreGame If {@code true}, returns all known resource types. If {@code false}, returns resource types
   *                   supported by the current game.
   * @return String array containing format extensions.
   */
  public static String[] getAvailableResourceTypes(boolean ignoreGame) {
    ArrayList<String> list = new ArrayList<>();
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_2DA)) {
      list.add("2DA");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_ACM)) {
      list.add("ACM");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_ARE_V10)
        || (Boolean) getProperty(Key.IS_SUPPORTED_ARE_V91)) {
      list.add("ARE");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_BAM_V1) || (Boolean) getProperty(Key.IS_SUPPORTED_BAM_V2)
        || (Boolean) getProperty(Key.IS_SUPPORTED_BAMC_V1)) {
      list.add("BAM");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_BCS)) {
      list.add("BCS");
      list.add("BS");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_BIK)) {
      list.add("BIK");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_BIO)) {
      list.add("BIO");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_BMP_ALPHA)
        || (Boolean) getProperty(Key.IS_SUPPORTED_BMP_PAL)) {
      list.add("BMP");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_CHR_V10) || (Boolean) getProperty(Key.IS_SUPPORTED_CHR_V20)
        || (Boolean) getProperty(Key.IS_SUPPORTED_CHR_V21) || (Boolean) getProperty(Key.IS_SUPPORTED_CHR_V22)) {
      list.add("CHR");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_CHU)) {
      list.add("CHU");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_CRE_V10) || (Boolean) getProperty(Key.IS_SUPPORTED_CRE_V12)
        || (Boolean) getProperty(Key.IS_SUPPORTED_CRE_V22) || (Boolean) getProperty(Key.IS_SUPPORTED_CRE_V90)) {
      list.add("CRE");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_DLG)) {
      list.add("DLG");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_EFF)) {
      list.add("EFF");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_FNT)) {
      list.add("FNT");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_GAM_V11) || (Boolean) getProperty(Key.IS_SUPPORTED_GAM_V20)
        || (Boolean) getProperty(Key.IS_SUPPORTED_GAM_V21) || (Boolean) getProperty(Key.IS_SUPPORTED_GAM_V22)) {
      list.add("GAM");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_GLSL)) {
      list.add("GLSL");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_GUI)) {
      list.add("GUI");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_IDS)) {
      list.add("IDS");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_INI)) {
      list.add("INI");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_ITM_V10) || (Boolean) getProperty(Key.IS_SUPPORTED_ITM_V11)
        || (Boolean) getProperty(Key.IS_SUPPORTED_ITM_V20)) {
      list.add("ITM");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_LOG)) {
      list.add("LOG");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_LUA)) {
      list.add("LUA");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_MAZE)) {
      list.add("MAZE");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_MENU)) {
      list.add("MENU");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_MOS_V1) || (Boolean) getProperty(Key.IS_SUPPORTED_MOS_V2)
        || (Boolean) getProperty(Key.IS_SUPPORTED_MOSC_V1)) {
      list.add("MOS");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_MUS)) {
      list.add("MUS");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_MVE)) {
      list.add("MVE");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_PLT)) {
      list.add("PLT");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_PNG)) {
      list.add("PNG");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_PRO)) {
      list.add("PRO");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_PVRZ)) {
      list.add("PVRZ");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_RES)) {
      list.add("RES");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_SAV)) {
      list.add("SAV");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_SPL_V1)
        || (Boolean) getProperty(Key.IS_SUPPORTED_SPL_V2)) {
      list.add("SPL");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_SQL)) {
      list.add("SQL");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_SRC_IWD2)
        || (Boolean) getProperty(Key.IS_SUPPORTED_SRC_PST)) {
      list.add("SRC");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_STO_V10) || (Boolean) getProperty(Key.IS_SUPPORTED_STO_V11)
        || (Boolean) getProperty(Key.IS_SUPPORTED_STO_V90)) {
      list.add("STO");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_TIS_V1)
        || (Boolean) getProperty(Key.IS_SUPPORTED_TIS_V2)) {
      list.add("TIS");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_TO_V1)) {
      list.add("TOH");
      list.add("TOT");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_TO_V2)) {
      list.add("TOH");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_TTF)) {
      list.add("TTF");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_VAR)) {
      list.add("VAR");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_VEF)) {
      list.add("VEF");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_VVC)) {
      list.add("VVC");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_WAV) || (Boolean) getProperty(Key.IS_SUPPORTED_WAVC)
        || (Boolean) getProperty(Key.IS_SUPPORTED_OGG)) {
      list.add("WAV");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_WBM)) {
      list.add("WBM");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_WED)) {
      list.add("WED");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_WFX)) {
      list.add("WFX");
    }
    if (ignoreGame || (Boolean) getProperty(Key.IS_SUPPORTED_WMP)) {
      list.add("WMP");
    }

    String[] retVal = new String[list.size()];
    for (int i = 0; i < retVal.length; i++) {
      retVal[i] = list.get(i);
    }

    return retVal;
  }

  /**
   * Returns a map of equipped appearance codes with associated descriptions.
   */
  public static Map<String, String> getEquippedAppearanceMap() {
    Map<String, String> retVal = getProperty(Key.GET_GAME_EQUIPPED_APPEARANCES);
    if (retVal == null) {
      Set<String> codes = new HashSet<>();
      // determine armor types
      List<ResourceEntry> entries = ResourceFactory
          .getResources(Pattern.compile(".[DHEIO][FM][BCFTW][^1]G1\\.BAM", Pattern.CASE_INSENSITIVE));
      for (final ResourceEntry entry : entries) {
        String code = entry.getResourceName().substring(4, 5).toUpperCase(Locale.ENGLISH);
        codes.add(code + "A");
        codes.add(code + "W");
      }
      // determine weapon types
      entries = ResourceFactory.getResources(Pattern.compile("W[PQ][LMS]..G1\\.BAM", Pattern.CASE_INSENSITIVE));
      for (final ResourceEntry entry : entries) {
        codes.add(entry.getResourceName().substring(3, 5).toUpperCase(Locale.ENGLISH));
      }

      // fall back to fixed list if needed
      if (codes.isEmpty()) {
        codes.add("AX");
        codes.add("CB");
        codes.add("CL");
        codes.add("DD");
        codes.add("S1");
        codes.add("WH");
      }

      // space for "no type"
      codes.add("  ");

      retVal = new TreeMap<>();
      for (final String code : codes) {
        String desc = KNOWN_EQUIPPED_APPEARANCE.get(code);
        if (desc != null) {
          retVal.put(code, desc);
        } else {
          retVal.put(code, "Unknown (" + code + ")");
        }
      }

      addEntry(Key.GET_GAME_EQUIPPED_APPEARANCES, Type.MAP, retVal);
    }
    return retVal;
  }

  /**
   * Returns whether the specified path is a saved game folder or a file belonging to a saved game.
   *
   * @param relPath Relative path of file or folder to check.
   * @return {@code true} if the path is a saved game folder or file.
   */
  public static boolean isSaveGame(Path relPath) {
    boolean retVal = false;
    if (relPath != null) {
      List<Path> roots = getRootFolders();
      for (final Path root : roots) {
        try {
          Path savePath = root.resolve(relPath);
          List<String> folderNames = getProperty(Key.GET_GAME_SAVE_FOLDER_NAMES);
          for (final String saveFolder : folderNames) {
            Path saveRootPath = FileManager.queryExisting(root, saveFolder);
            if (saveRootPath != null && savePath.startsWith(saveRootPath)) {
              retVal = true;
              break;
            }
          }
          if (retVal) {
            break;
          }
        } catch (ProviderMismatchException e) {
          // incompatible filesystem roots: ignore
        }
      }
    }
    return retVal;
  }

  /**
   * Returns a list of potential game binary filenames for the current game and platform. Returned list can be empty but
   * is never {@code null}.
   */
  public static List<String> getGameBinaries() {
    return getGameBinaries(null, null);
  }

  /**
   * Returns a list of potential game binary filenames for the specified game and platform. Returned list can be empty
   * but is never {@code null}.
   */
  public static List<String> getGameBinaries(Game game, Platform.OS os) {
    List<String> list = null;

    if (game == null) {
      game = getGame();
    }
    if (os == null) {
      os = Platform.OS.getCurrentOS();
    }

    EnumMap<Platform.OS, List<String>> osMap = DEFAULT_GAME_BINARIES.get(game);
    if (osMap != null) {
      list = osMap.get(os);
    }

    return Collections.unmodifiableList((list != null) ? list : new ArrayList<>(1));
  }

  /** Returns a list of paths for existing game binaries associated with the current game and platform. */
  public static List<Path> getGameBinaryPaths() {
    return getGameBinaryPaths(null, null);
  }

  /** Returns a list of existing game binary paths associated with the specified game and platform. */
  public static List<Path> getGameBinaryPaths(Game game, Platform.OS os) {
    List<Path> list = new ArrayList<>();

    if (game == null) {
      game = getGame();
    }
    if (os == null) {
      os = Platform.OS.getCurrentOS();
    }

    List<String> listNames = getGameBinaries(game, os);
    Path root = getGameRoot();
    for (String name : listNames) {
      Path path = FileManager.queryExisting(root, name);
      if (path != null) {
        if (os == Platform.OS.MAC_OS && path.toString().toLowerCase(Locale.ENGLISH).endsWith(".app")
            && Files.isDirectory(path)) {
          if (!list.contains(path)) {
            list.add(path);
          }
        } else if (Files.isRegularFile(path)) {
          if (!list.contains(path)) {
            list.add(path);
          }
        }
      }
    }

    return list;
  }

  /**
   * Brings up a dialog where the user can select a game from a list.
   *
   * @param title       an optional title string for the dialog.
   * @param prompt      an optional string associated with the list of games.
   * @param defaultGame the game which is initially selected.
   * @return the selected game. Returns {@code null} if no game was selected or the user cancelled the game selection.
   */
  public static Game showGameSelectionDialog(String title, String prompt, Game defaultGame) {
    Game game = null;
    if (title == null) {
      title = UIManager.getString("OptionPane.titleText");
    }
    if (prompt == null) {
      prompt = "Please select:";
    }

    JList<String> list = new JList<>(new ListModel<String>() {
      @Override
      public int getSize() {
        return Game.values().length;
      }

      @Override
      public String getElementAt(int index) {
        try {
          return Game.values()[index].getTitle();
        } catch (Exception e) {
          throw new ArrayIndexOutOfBoundsException(index);
        }
      }

      @Override
      public void addListDataListener(ListDataListener l) {
      }

      @Override
      public void removeListDataListener(ListDataListener l) {
      }
    });
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    if (defaultGame != null) {
      int idx = Arrays.asList(Game.values()).indexOf(defaultGame);
      list.setSelectedIndex(idx);
    }

    JScrollPane scroll = new JScrollPane(list);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    list.ensureIndexIsVisible(list.getSelectedIndex());

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(new JLabel(prompt), gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(4, 0, 0, 0), 0, 0);
    panel.add(scroll, gbc);

    int retVal = JOptionPane.showConfirmDialog(NearInfinity.getInstance(), panel, title, JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (retVal == JOptionPane.OK_OPTION && list.getSelectedValue() != null) {
      try {
        game = Game.values()[list.getSelectedIndex()];
      } catch (Exception e) {
        Logger.trace(e);
      }
    }

    return game;
  }

  /**
   * Returns the unique session id for this game.
   *
   * @return Session id as {@code String}.
   */
  public static String getSessionId() {
    return getProperty(Key.GET_GAME_SESSION_ID);
  }

  // Returns the Property object assigned to the given key.
  private static Property getEntry(Key key) {
    return PROPERTIES.get(key);
  }

  // Adds a new Property entry to the list.
  private static void addEntry(Key key, Type type, Object data) {
    Property prop = new Property(key, type, data);
    PROPERTIES.put(prop.getKey(), prop);
  }

  // Cleans up data when closing a game profile
  private static void closeGame() {
    Game oldGame = getProperty(Key.GET_GAME_TYPE);
    PROPERTIES.clear();
    addEntry(Key.GET_GAME_TYPE_PREVIOUS, Type.OBJECT, oldGame);
    initStaticProperties();
    instance = null;
  }

  // Initializes properties not related to a specific game
  private static void initStaticProperties() {
    // setting current NI version
    addEntry(Key.GET_GLOBAL_NEARINFINITY_VERSION, Type.STRING, NearInfinity.getVersion());

    // setting list of supported games and associated data
    List<Game> gameList = new ArrayList<>();
    Collections.addAll(gameList, Game.values());

    addEntry(Key.GET_GLOBAL_GAMES, Type.LIST, gameList);
    addEntry(Key.GET_GLOBAL_EXTRA_FOLDER_NAMES, Type.LIST, GAME_EXTRA_FOLDERS);
    addEntry(Key.GET_GLOBAL_SAVE_FOLDER_NAMES, Type.LIST, GAME_SAVE_FOLDERS);
    addEntry(Key.GET_GLOBAL_HOME_FOLDER_NAME, Type.STRING, GAME_HOME_FOLDER);

    // setting default override folder name
    addEntry(Key.GET_GLOBAL_OVERRIDE_NAME, Type.STRING, "override");

    // Language root folder for Enhanced Edition games
    addEntry(Key.GET_GLOBAL_LANG_NAME, Type.STRING, "lang");

    // setting dialog.tlk file names
    addEntry(Key.GET_GLOBAL_DIALOG_NAME, Type.STRING, "dialog.tlk");
    addEntry(Key.GET_GLOBAL_DIALOG_NAME_FEMALE, Type.STRING, "dialogf.tlk");
  }

  // Initializes a list of potential executable filenames for each game and platform
  private static void initDefaultGameBinaries() {
    DEFAULT_GAME_BINARIES.clear();
    EnumMap<Platform.OS, List<String>> osMap;
    List<String> emptyList = new ArrayList<>();
    List<String> list;

    // BG1 & BG1TotSC (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("bgmain2.exe");
    list.add("bgmain.exe");
    list.add("baldur.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.BG1, osMap);
    DEFAULT_GAME_BINARIES.put(Game.BG1TotSC, osMap);

    // Tutu (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("bgmain.exe");
    list.add("baldur.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.Tutu, osMap);

    // BG2SoA & BG2ToB (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("bgmain.exe");
    list.add("baldur.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.BG2SoA, osMap);
    DEFAULT_GAME_BINARIES.put(Game.BG2ToB, osMap);

    // BGT (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("bgmain.exe");
    list.add("baldur.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.BGT, osMap);

    // PST (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("torment.exe");
    list.add("pst.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.PST, osMap);

    // IWD & IWDHoW & IWDHowTotLM (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("idmain.exe");
    list.add("icewind.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.IWD, osMap);
    DEFAULT_GAME_BINARIES.put(Game.IWDHoW, osMap);
    DEFAULT_GAME_BINARIES.put(Game.IWDHowTotLM, osMap);

    // IWD2 (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("iwd2.exe");
    list.add("icewind2.exe");
    list.add("icewind.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.IWD2, osMap);

    // IWD2EE (Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    osMap.put(Platform.OS.UNIX, emptyList);
    osMap.put(Platform.OS.MAC_OS, emptyList);
    list = new ArrayList<>();
    list.add("iwd2ee.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.IWD2EE, osMap);

    // BG1EE (Linux, macOS, Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    list = new ArrayList<>();
    list.add("baldursgate64");
    list.add("baldursgate");
    list.add("baldur");
    osMap.put(Platform.OS.UNIX, list);
    list = new ArrayList<>();
    list.add("Baldur's Gate - Enhanced Edition.app");
    list.add("Baldur's Gate - Enhanced Edition.app/Contents/MacOS/Baldur's Gate - Enhanced Edition");
    list.add("Baldur's Gate - Enhanced Edition");
    osMap.put(Platform.OS.MAC_OS, list);
    list = new ArrayList<>();
    list.add("InfinityLoader.exe");
    list.add("Baldur.exe");
    list.add("BGEE.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.BG1EE, osMap);

    // BG1BG1SoD (Linux, macOS, Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    list = new ArrayList<>();
    list.add("SiegeOfDragonspear64");
    list.add("SiegeOfDragonspear");
    list.add("BaldursGate64");
    list.add("BaldursGate");
    list.add("Baldur");
    list.add("siegeofdragonspear64");
    list.add("siegeofdragonspear");
    list.add("baldursgate64");
    list.add("baldursgate");
    list.add("baldur");
    osMap.put(Platform.OS.UNIX, list);
    list = new ArrayList<>();
    list.add("Baldur's Gate - Enhanced Edition.app");
    list.add("Baldur's Gate - Enhanced Edition.app/Contents/MacOS/Baldur's Gate - Enhanced Edition");
    list.add("Baldur's Gate - Enhanced Edition");
    osMap.put(Platform.OS.MAC_OS, list);
    list = new ArrayList<>();
    list.add("InfinityLoader.exe");
    list.add("SiegeOfDragonspear.exe");
    list.add("SOD.exe");
    list.add("Baldur.exe");
    list.add("BGEE.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.BG1SoD, osMap);

    // BG2EE & EET (Linux, macOS, Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    list = new ArrayList<>();
    list.add("BaldursGateII64");
    list.add("BaldursGateII");
    list.add("Baldur");
    list.add("baldursgateii64");
    list.add("baldursgateii");
    list.add("baldur");
    osMap.put(Platform.OS.UNIX, list);
    list = new ArrayList<>();
    list.add("BaldursGateIIEnhancedEdition.app");
    list.add("BaldursGateIIEnhancedEdition.app/Contents/MacOS/BaldursGateIIEnhancedEdition");
    list.add("BaldursGateIIEnhancedEdition");
    list.add("Baldur's Gate II - Enhanced Edition.app");
    list.add("Baldur's Gate II - Enhanced Edition.app/Contents/MacOS/Baldur's Gate II - Enhanced Edition");
    list.add("Baldur's Gate II - Enhanced Edition");
    osMap.put(Platform.OS.MAC_OS, list);
    list = new ArrayList<>();
    list.add("InfinityLoader.exe");
    list.add("Baldur.exe");
    list.add("BaldurII.exe");
    list.add("BG2EE.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.BG2EE, osMap);
    DEFAULT_GAME_BINARIES.put(Game.EET, osMap);

    // IWDEE (Linux, macOS, Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    list = new ArrayList<>();
    list.add("IcewindDale64");
    list.add("IcewindDale");
    list.add("Icewind");
    list.add("icewinddale64");
    list.add("icewinddale");
    list.add("icewind");
    osMap.put(Platform.OS.UNIX, list);
    list = new ArrayList<>();
    list.add("IcewindDale.app");
    list.add("IcewindDale.app/Contents/MacOS/IcewindDale");
    list.add("Icewind Dale - Enhanced Edition.app");
    list.add("Icewind Dale - Enhanced Edition.app/Contents/MacOS/Icewind Dale - Enhanced Edition");
    osMap.put(Platform.OS.MAC_OS, list);
    list = new ArrayList<>();
    list.add("InfinityLoader.exe");
    list.add("Icewind.exe");
    list.add("IWD.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.IWDEE, osMap);

    // PSTEE (Linux, macOS, Windows)
    osMap = new EnumMap<>(Platform.OS.class);
    list = new ArrayList<>();
    list.add("Torment64");
    list.add("Torment");
    list.add("torment64");
    list.add("torment");
    osMap.put(Platform.OS.UNIX, list);
    list = new ArrayList<>();
    list.add("Planescape Torment - Enhanced Edition.app");
    list.add("Planescape Torment - Enhanced Edition.app/Contents/MacOS/Planescape Torment - Enhanced Edition");
    list.add("Planescape Torment - Enhanced Edition");
    osMap.put(Platform.OS.MAC_OS, list);
    list = new ArrayList<>();
    list.add("Torment.exe");
    list.add("PST.exe");
    osMap.put(Platform.OS.WINDOWS, list);
    DEFAULT_GAME_BINARIES.put(Game.PSTEE, osMap);
  }

  // Attempts to determine home folder name from the game's "engine.lua" file if available
  private static String getLuaHomeFolderName(Game game) {
    Path gameRoot = getGameRoot();
    if (gameRoot != null) {
      Path lua = FileManager.query(gameRoot, "engine.lua");
      String name = getLuaValue(lua, "engine_name", "Infinity Engine - Enhanced Edition", true);
      if (name != null) {
        return name.replace('"', ' ').trim();
      }
    }

    if (game != null) {
      return GAME_HOME_FOLDER.get(game);
    } else {
      return null;
    }
  }

  // Returns the value of the Lua script entry specified by key
  private static String getLuaValue(Path file, String key, String defaultValue, boolean ifLuaExists) {
    String retVal = ifLuaExists ? null : defaultValue;
    if (file != null && FileEx.create(file).isFile() && key != null && !key.trim().isEmpty()) {
      retVal = defaultValue;
      try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
        for (Iterator<String> iter = lines.iterator(); iter.hasNext();) {
          String line = iter.next();
          int sep = line.indexOf('=');
          if (sep > 0) {
            String name = line.substring(0, sep).trim();
            if (name.equals(key)) {
              String value = line.substring(sep + 1).trim();
              if (!value.isEmpty()) {
                boolean quote = (value.charAt(0) == '"');
                boolean cmt = false;
                int pos = 0;
                for (int i = 1, len = value.length(); i < len; i++) {
                  char ch = value.charAt(i);
                  if (ch == '"') {
                    quote = !quote;
                    if (!quote) {
                      pos = i + 1;
                      break;
                    }
                  } else if (ch == '-' && !quote) {
                    cmt = !cmt;
                    if (!cmt) {
                      pos = i - 1;
                      break;
                    }
                  } else {
                    cmt = false;
                  }
                }
                if (pos > 0) {
                  value = value.substring(0, pos);
                }
                retVal = value;
              }
              break;
            }
          }
        }
      } catch (IOException e) {
        Logger.error(e);
      }
    }
    return retVal;
  }

  private Profile(Path keyFile, String desc, Game forcedGame) throws Exception {
    init(keyFile, desc, forcedGame);
  }

  // Initializes profile
  private void init(Path keyFile, String desc, Game forcedGame) throws Exception {
    if (keyFile == null) {
      throw new Exception("No chitin.key specified");
    } else if (!FileEx.create(keyFile).isFile()) {
      throw new Exception(keyFile + " does not exist");
    }

    if (desc != null) {
      addEntry(Key.GET_GAME_DESC, Type.STRING, desc);
    }

    addEntry(Key.IS_FORCED_GAME, Type.BOOLEAN, forcedGame != null);
    if (forcedGame != null) {
      addEntry(Key.GET_GAME_TYPE, Type.OBJECT, forcedGame);
    }

    // adding chitin.key path
    addEntry(Key.GET_GAME_CHITIN_KEY, Type.PATH, keyFile);

    // adding game's root folder and name
    Path rootDir = keyFile.toAbsolutePath().getParent();
    addEntry(Key.GET_GAME_ROOT_FOLDER, Type.PATH, rootDir);
    addEntry(Key.GET_GAME_ROOT_FOLDER_NAME, Type.STRING, rootDir.getFileName().toString());

    // first attempt to determine home directory for current game
    String home = getLuaHomeFolderName(null);
    Path homeDir = null;
    if (home != null) {
      addEntry(Key.GET_GAME_HOME_FOLDER_NAME, Type.STRING, home);
      homeDir = ResourceFactory.getHomeRoot(true);
      if (homeDir != null && FileEx.create(homeDir).isDirectory()) {
        addEntry(Key.GET_GAME_HOME_FOLDER, Type.PATH, homeDir);
      }
    }

    // first attempt to initialize DLC content
    addEntry(Key.GET_GAME_DLC_FOLDERS_AVAILABLE, Type.LIST, initDlc(rootDir, homeDir));

    initGame();
  }

  private void initGame() throws Exception {
    // Main game detection
    Game game = null;

    // Preparing available root paths
    List<Path> gameRoots = new ArrayList<>();
    if (Profile.getGameRoot() != null) {
      gameRoots.add(Profile.getGameRoot());
    }
    if (Profile.getProperty(Key.GET_GAME_DLC_FOLDERS_AVAILABLE) != null) {
      gameRoots.addAll(Profile.getProperty(Key.GET_GAME_DLC_FOLDERS_AVAILABLE));
    }

    boolean isForced = getProperty(Key.IS_FORCED_GAME);
    if (isForced) {
      game = getGame();
    }

    if (game == Game.IWDEE || FileEx.create(FileManager.query(gameRoots, "movies/howseer.wbm")).isFile()) {
      if (game == null) {
        game = Game.IWDEE;
        // Note: baldur.ini is initialized later
      }
    } else if (game == Game.PSTEE || (FileEx.create(FileManager.query(gameRoots, "data/MrtGhost.bif")).isFile()
        && FileEx.create(FileManager.query(gameRoots, "data/shaders.bif")).isFile()
        && getLuaValue(FileManager.query(gameRoots, "engine.lua"), "engine_mode", "0", false).equals("3"))) {
      if (game == null) {
        game = Game.PSTEE;
        // Note: baldur.ini is initialized later
      }
    } else if (game == Game.EET || game == Game.BG2EE
        || FileEx.create(FileManager.query(gameRoots, "movies/pocketzz.wbm")).isFile()) {
      if ((FileEx.create(FileManager.query(gameRoots, "override/EET.flag")).isFile())
          || (FileEx.create(FileManager.query(gameRoots, "data/eetTU00.bif")).isFile())) {
        if (game == null) {
          game = Game.EET;
        }
      } else {
        if (game == null) {
          game = Game.BG2EE;
        }
      }
      // Note: baldur.ini is initialized later
    } else if (game == Game.BG1SoD || FileEx.create(FileManager.query(gameRoots, "movies/sodcin01.wbm")).isFile()) {
      if (game == null) {
        game = Game.BG1SoD;
        // Note: baldur.ini is initialized later
      }
    } else if (game == Game.BG1EE || FileEx.create(FileManager.query(gameRoots, "movies/bgenter.wbm")).isFile()) {
      if (game == null) {
        game = Game.BG1EE;
        // Note: baldur.ini is initialized later
      }
    } else if ((game == Game.PST || FileEx.create(FileManager.query(gameRoots, "torment.exe")).isFile())
        && (!FileEx.create(FileManager.query(gameRoots, "movies/sigil.wbm")).isFile())) {
      if (game == null) {
        game = Game.PST;
      }
      addEntry(Key.GET_GAME_INI_NAME, Type.STRING, "torment.ini");
      Path ini = FileManager.query(gameRoots, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
    } else if (game == Game.IWD || game == Game.IWDHoW || game == Game.IWDHowTotLM
        || (FileEx.create(FileManager.query(gameRoots, "idmain.exe")).isFile())
            && (!FileEx.create(FileManager.query(gameRoots, "movies/howseer.wbm")).isFile())) {
      if (game == null) {
        game = Game.IWD;
      }
      addEntry(Key.GET_GAME_INI_NAME, Type.STRING, "icewind.ini");
      Path ini = FileManager.query(gameRoots, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
    } else if (game == Game.IWD2 || game == Game.IWD2EE
        || (FileEx.create(FileManager.query(gameRoots, "iwd2.exe")).isFile()
            || FileEx.create(FileManager.query(gameRoots, "iwd2ee.exe")).isFile())
        && (FileEx.create(FileManager.query(gameRoots, "Data/Credits.mve")).isFile())) {
      if (game == null) {
        game = Game.IWD2;
      }
      addEntry(Key.GET_GAME_INI_NAME, Type.STRING, "icewind2.ini");
      Path ini = FileManager.query(gameRoots, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
      // special: movies are found in biff folders
      initIwd2ExtraFolders(game);
    } else if (game == Game.Tutu || FileEx.create(FileManager.query(gameRoots, "bg1tutu.exe")).isFile()
        || FileEx.create(FileManager.query(gameRoots, "bg1mov/MovieCD1.bif")).isFile()) {
      if (game == null) {
        game = Game.Tutu;
      }
      addEntry(Key.GET_GAME_INI_NAME, Type.STRING, "baldur.ini");
      Path ini = FileManager.query(gameRoots, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
    } else if (game == Game.BG2SoA || game == Game.BG2ToB || game == Game.BGT
        || (FileEx.create(FileManager.query(gameRoots, "baldur.exe")).isFile())
            && (FileEx.create(FileManager.query(gameRoots, "BGConfig.exe")).isFile())) {
      if (game == null) {
        game = Game.BG2SoA;
      }
      addEntry(Key.GET_GAME_INI_NAME, Type.STRING, "baldur.ini");
      Path ini = FileManager.query(gameRoots, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
    } else if (game == Game.BG1 || game == Game.BG1TotSC
        || (FileEx.create(FileManager.query(gameRoots, "movies/graphsim.mov")).isFile()) || // Mac BG1 detection hack
        ((FileEx.create(FileManager.query(gameRoots, "baldur.exe")).isFile())
            && (FileEx.create(FileManager.query(gameRoots, "Config.exe")).isFile()))) {
      if (game == null) {
        game = Game.BG1;
      }
      addEntry(Key.GET_GAME_INI_NAME, Type.STRING, "baldur.ini");
      Path ini = FileManager.query(gameRoots, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
    } else {
      // game == Game.Unknown
      if (game == null) {
        // present list of available game types to choose from
        Game oldGame = getProperty(Key.GET_GAME_TYPE_PREVIOUS);
        if (oldGame == null) {
          if (FileEx.create(FileManager.query(gameRoots, "baldur.exe")).isFile()) {
            // Could be BG1 Demo
            oldGame = Profile.Game.BG1;
          } else {
            oldGame = Profile.Game.Unknown;
          }
        }
        game = Profile.showGameSelectionDialog("Unknown game", "Please select a game:", oldGame);
        if (game != null) {
          openGame(getChitinKey(), BrowserMenuBar.getInstance().getGameMenu().getBookmarkName(getChitinKey()), game);
          return;
        }
      }
      if (game == null) {
        game = Game.Unknown;
      }
      addEntry(Key.GET_GAME_INI_NAME, Type.STRING, "baldur.ini");
      Path ini = FileManager.query(gameRoots, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
    }
    // adding priliminary game type into storage
    addEntry(Key.GET_GAME_TYPE, Type.OBJECT, game);
    addEntry(Key.GET_GAME_EXTRA_FOLDER_NAMES, Type.LIST, GAME_EXTRA_FOLDERS.get(game));
    addEntry(Key.GET_GAME_SAVE_FOLDER_NAMES, Type.LIST, GAME_SAVE_FOLDERS.get(game));

    if (isEnhancedEdition()) {
      Path langDir = FileManager.query(gameRoots, "lang");
      if (langDir != null && FileEx.create(langDir).isDirectory()) {
        addEntry(Key.GET_GAME_LANG_FOLDER_BASE, Type.PATH, langDir);
      }
    }

    if (!hasProperty(Key.GET_GAME_HOME_FOLDER_NAME) && GAME_HOME_FOLDER.containsKey(game)) {
      addEntry(Key.GET_GAME_HOME_FOLDER_NAME, Type.STRING, getLuaHomeFolderName(game));
    }

    // delayed initialization of ini files (EE only)
    if (isEnhancedEdition() && getProperty(Key.GET_GAME_INI_FILE) == null) {
      initIniFile("baldur.lua", "baldur.ini");
    }

    // initializing available root directories
    initRootDirs();

    // initializing extra folders containing resources
    initExtraFolders();

    // initializing list of available override folders
    initOverrides();

    // initializing dialog.tlk and dialogf.tlk
    Path tlk = FileManager.query(getRootFolders(), getProperty(Key.GET_GLOBAL_DIALOG_NAME));
    if (tlk != null && FileEx.create(tlk).isFile()) {
      addEntry(Key.GET_GAME_DIALOG_FILE, Type.PATH, tlk);
    }
    Path tlkf = FileManager.query(getRootFolders(), getProperty(Key.GET_GLOBAL_DIALOG_NAME_FEMALE));
    if (tlkf != null && FileEx.create(tlkf).isFile()) {
      addEntry(Key.GET_GAME_DIALOGF_FILE, Type.PATH, tlkf);
    }

    // initializing list of folders containing BIFF archives
    List<Path> biffDirs = ResourceFactory.getBIFFDirs();
    if (!biffDirs.isEmpty()) {
      addEntry(Key.GET_GAME_BIFF_FOLDERS, Type.LIST, biffDirs);
    }

    // Initializing resource structure
    ResourceFactory.openGame(getChitinKey());

    // Expansion pack detection
    if (!isForced && game == Game.IWD && ResourceFactory.resourceExists("HOWDRAG.MVE")) {
      // detect Trials of the Luremaster
      if (ResourceFactory.resourceExists("AR9715.ARE")) {
        game = Game.IWDHowTotLM;
      } else {
        game = Game.IWDHoW;
      }
    } else if (!isForced && game == Game.BG2SoA && ResourceFactory.resourceExists("SARADUSH.MVE")) {
      // detect BG Trilogy
      if (ResourceFactory.resourceExists("ARU000.ARE")) {
        game = Game.BGT;
      } else {
        game = Game.BG2ToB;
      }
    } else if (!isForced && game == Game.BG1 && ResourceFactory.resourceExists("DURLAG.MVE")) {
      game = Game.BG1TotSC;
    } else if (!isForced && game == Game.IWD2 && FileEx.create(FileManager.query(gameRoots, "IEex.dll")).isFile()) {
      game = Game.IWD2EE;
    }

    // updating game type
    addEntry(Key.GET_GAME_TYPE, Type.OBJECT, game);
    addEntry(Key.GET_GAME_TITLE, Type.STRING, game.getTitle());

    // initializing supported resource types
    initResourceTypes();

    // initializing engine-specific traits
    initFeatures();

    // generating a unique id for the current game session
    initSession();
  }

  // Initializes the first available of the specified ini files
  private void initIniFile(String... iniFiles) {
    if (iniFiles != null) {
      Path homeRoot = ResourceFactory.getHomeRoot(false);
      for (String iniFile : iniFiles) {
        Path ini = FileManager.query(homeRoot, iniFile);
        if (ini != null && FileEx.create(ini).isFile()) {
          addEntry(Key.GET_GAME_INI_NAME, Type.STRING, iniFile);
          break;
        }
      }
    }
  }

  // Initializes available root folders of the game
  private void initRootDirs() {
    // Considering three (or four) different root folders to locate game resources
    // Note: Order of the root directories is important. FileNI will take the first one available.
    Path homeRoot = null;
    Bookmark bookmark = BrowserMenuBar.getInstance().getGameMenu().getBookmarkOf(getChitinKey());
    if (bookmark != null && bookmark.getHomePath() != null) {
      final Path path = FileManager.resolve(bookmark.getHomePath());
      if (path != null && Files.isDirectory(path)) {
        homeRoot = path;
      }
    }
    if (homeRoot == null) {
      homeRoot = ResourceFactory.getHomeRoot(false);
    }
    String language = ResourceFactory
        .fetchGameLanguage(FileManager.query(homeRoot, getProperty(Key.GET_GAME_INI_NAME)));
    String languageDef = ResourceFactory.fetchGameLanguage(null);

    // adding available roots in order of priority (highest first)
    List<Path> listRoots = new ArrayList<>();
    if (homeRoot != null) {
      addEntry(Key.GET_GAME_HOME_FOLDER, Type.PATH, homeRoot);
      Path ini = FileManager.query(homeRoot, getProperty(Key.GET_GAME_INI_NAME));
      if (ini != null && FileEx.create(ini).isFile()) {
        addEntry(Key.GET_GAME_INI_FILE, Type.PATH, ini);
      }
      listRoots.add(homeRoot);
    }

    // initializing available DLC
    Path gameRoot = getGameRoot();
    List<Path> dlcRoots = initDlc(gameRoot, homeRoot);
    addEntry(Key.GET_GAME_DLC_FOLDERS_AVAILABLE, Type.LIST, dlcRoots);

    // preparing available game root paths
    List<Path> roots = new ArrayList<>(dlcRoots);
    roots.add(gameRoot);

    // process each root separately
    roots.forEach(root -> {
      // adding root of active language
      Path langRoot = FileManager.query(root, getProperty(Key.GET_GLOBAL_LANG_NAME), language);
      if (langRoot != null && FileEx.create(langRoot).isDirectory()) {
        addEntry(Key.GET_GAME_LANG_FOLDER_NAME, Type.STRING, language);
        addEntry(Key.GET_GAME_LANG_FOLDER, Type.PATH, langRoot);
        List<Path> langPaths = ResourceFactory.getAvailableGameLanguages();
        addEntry(Key.GET_GAME_LANG_FOLDERS_AVAILABLE, Type.LIST, langPaths);
        List<String> languages = new ArrayList<>(langPaths.size());
        langPaths.forEach((path) -> languages.add(path.getFileName().toString()));
        addEntry(Key.GET_GAME_LANG_FOLDER_NAMES_AVAILABLE, Type.LIST, languages);
        listRoots.add(langRoot);
      }

      // adding fallback language added if selected language is non-english
      Path langRootDef = FileManager.query((Path) getProperty(Key.GET_GAME_LANG_FOLDER_BASE), languageDef);
      if (!languageDef.equals(language) && langRootDef != null && FileEx.create(langRootDef).isDirectory()) {
        listRoots.add(langRootDef);
      }

      // adding game root
      listRoots.add(root);
    });

    addEntry(Key.GET_GAME_ROOT_FOLDERS_AVAILABLE, Type.PATH, listRoots);
  }

  // Initializes extra folders containing resources
  private void initExtraFolders() {
    List<Path> pathList = new ArrayList<>();
    List<Path> rootPaths = getRootFolders();
    List<String> extraFolders = getProperty(Key.GET_GAME_EXTRA_FOLDER_NAMES);
    rootPaths.forEach(root -> {
      List<Path> list = new ArrayList<>(extraFolders.size());
      extraFolders.forEach((folder) -> {
        Path path = FileManager.query(root, folder);
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
      });
      Collections.sort(list);
      pathList.addAll(list);
    });

    if (getProperty(Key.GET_GAME_EXTRA_FOLDERS) != null) {
      updateProperty(Key.GET_GAME_EXTRA_FOLDERS, pathList);
    } else {
      addEntry(Key.GET_GAME_EXTRA_FOLDERS, Type.LIST, pathList);
    }
  }

  // Initializes supported override folders used by specific games
  private void initOverrides() {
    List<Path> list = new ArrayList<>();
    if (isEnhancedEdition()) {
      // preparations
      Path gameRoot = getGameRoot();
      // relative language paths based on game root
      String langFolder = gameRoot.relativize(getLanguageRoot()).toString();
      String langFolderDef = gameRoot.relativize(getLanguageRoot().getParent().resolve("en_US")).toString();
      if (langFolder.equalsIgnoreCase(langFolderDef)) {
        langFolderDef = null;
      }
      Path homeRoot = getHomeRoot();
      List<Path> dlcRoots = getProperty(Key.GET_GAME_DLC_FOLDERS_AVAILABLE);

      // create default override folder if it doesn't exist yet
      if (FileManager.queryExisting(gameRoot, getOverrideFolderName().toLowerCase(Locale.ENGLISH)) == null) {
        try {
          Files.createDirectory(FileManager.query(gameRoot, getOverrideFolderName().toLowerCase(Locale.ENGLISH)));
        } catch (Throwable t) {
          Logger.trace(t);
        }
      }

      // putting all root folders into a list ordered by priority (highest first)
      List<Path> gameRoots = new ArrayList<>();
      gameRoots.add(homeRoot);
      gameRoots.addAll(dlcRoots);
      gameRoots.add(gameRoot);

      // registering override paths
      for (final Path root : gameRoots) {
        Path path = FileManager.query(root, langFolder, "Movies");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        if (langFolderDef != null) {
          path = FileManager.query(root, langFolderDef, "Movies");
          if (path != null && FileEx.create(path).isDirectory()) {
            list.add(path);
          }
        }
        path = FileManager.query(root, "Movies");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        path = FileManager.query(root, "Characters");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        path = FileManager.query(root, "Portraits");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        path = FileManager.query(root, langFolder, "Sounds");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        if (langFolderDef != null) {
          path = FileManager.query(root, langFolderDef, "Sounds");
          if (path != null && FileEx.create(path).isDirectory()) {
            list.add(path);
          }
        }
        path = FileManager.query(root, langFolder, "Fonts");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        path = FileManager.query(root, "Sounds");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        path = FileManager.query(root, "Scripts");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        path = FileManager.query(root, langFolder, "Override");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
        path = FileManager.query(root, "Override");
        if (path != null && FileEx.create(path).isDirectory()) {
          list.add(path);
        }
      }
    } else {
      Path root = getGameRoot();
      Path path = FileManager.query(root, "Movies");
      if (path != null && FileEx.create(path).isDirectory()) {
        list.add(path);
      }
      path = FileManager.query(root, "Characters");
      if (path != null && FileEx.create(path).isDirectory()) {
        list.add(path);
      }
      path = FileManager.query(root, "Portraits");
      if (path != null && FileEx.create(path).isDirectory()) {
        list.add(path);
      }
      path = FileManager.query(root, "Sounds");
      if (path != null && FileEx.create(path).isDirectory()) {
        list.add(path);
      }
      path = FileManager.query(root, "Scripts");
      if (path != null && FileEx.create(path).isDirectory()) {
        list.add(path);
      }
      path = FileManager.query(root, "Override");
      if (path != null && FileEx.create(path).isDirectory()) {
        list.add(path);
      }
    }

    addEntry(Key.GET_GAME_OVERRIDE_FOLDERS, Type.LIST, list);
  }

  // Initializes supported resource types
  private void initResourceTypes() {
    Game game = getGame();
    Engine engine = getEngine();

    addEntry(Key.IS_SUPPORTED_2DA, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_ACM, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_ARE_V10, Type.BOOLEAN, (engine == Engine.BG1 || engine == Engine.BG2
        || engine == Engine.IWD || engine == Engine.PST || engine == Engine.EE || engine == Engine.Unknown));
    addEntry(Key.IS_SUPPORTED_ARE_V91, Type.BOOLEAN, (engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_BAM_V1, Type.BOOLEAN, true);
    addEntry(Key.IS_SUPPORTED_BAMC_V1, Type.BOOLEAN, isBamcSupported(game));
    addEntry(Key.IS_SUPPORTED_BAM_V1_ALPHA, Type.BOOLEAN, isEnhancedEdition());
    addEntry(Key.IS_SUPPORTED_BAM_V2, Type.BOOLEAN, isBamV2Supported(game));

    addEntry(Key.IS_SUPPORTED_BCS, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_BIFF, Type.BOOLEAN, true);
    addEntry(Key.IS_SUPPORTED_BIF, Type.BOOLEAN, (engine == Engine.IWD));
    addEntry(Key.IS_SUPPORTED_BIFC, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_BIK, Type.BOOLEAN, (engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_BIO, Type.BOOLEAN,
        (engine == Engine.BG1 || engine == Engine.BG2 || engine == Engine.EE || engine == Engine.Unknown));

    addEntry(Key.IS_SUPPORTED_BMP_PAL, Type.BOOLEAN, true);
    addEntry(Key.IS_SUPPORTED_BMP_ALPHA, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_CHR_V10, Type.BOOLEAN, (engine == Engine.BG1 || engine == Engine.Unknown));
    addEntry(Key.IS_SUPPORTED_CHR_V20, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.EE));
    addEntry(Key.IS_SUPPORTED_CHR_V21, Type.BOOLEAN, (game == Game.BG2ToB || engine == Engine.EE));
    addEntry(Key.IS_SUPPORTED_CHR_V22, Type.BOOLEAN, (engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_CHU, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_CRE_V10, Type.BOOLEAN,
        (engine == Engine.BG1 || engine == Engine.BG2 || engine == Engine.EE || engine == Engine.Unknown));
    addEntry(Key.IS_SUPPORTED_CRE_V12, Type.BOOLEAN, (engine == Engine.PST));
    addEntry(Key.IS_SUPPORTED_CRE_V22, Type.BOOLEAN, (engine == Engine.IWD2));
    addEntry(Key.IS_SUPPORTED_CRE_V90, Type.BOOLEAN, (engine == Engine.IWD));

    addEntry(Key.IS_SUPPORTED_DLG, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_EFF, Type.BOOLEAN, (engine == Engine.BG1 || engine == Engine.BG2 || engine == Engine.IWD
        || engine == Engine.EE || engine == Engine.IWD2 || engine == Engine.Unknown));

    addEntry(Key.IS_SUPPORTED_FNT, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_GAM_V11, Type.BOOLEAN,
        (engine == Engine.BG1 || engine == Engine.IWD || engine == Engine.PST || engine == Engine.Unknown));
    addEntry(Key.IS_SUPPORTED_GAM_V20, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.EE));
    addEntry(Key.IS_SUPPORTED_GAM_V21, Type.BOOLEAN, (game == Game.BG2ToB || engine == Engine.EE));
    addEntry(Key.IS_SUPPORTED_GAM_V22, Type.BOOLEAN, (engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_GLSL, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_GUI, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_IDS, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_INI, Type.BOOLEAN,
        (engine == Engine.IWD || engine == Engine.IWD2 || engine == Engine.PST || engine == Engine.EE));

    addEntry(Key.IS_SUPPORTED_ITM_V10, Type.BOOLEAN, (engine == Engine.BG1 || engine == Engine.BG2
        || engine == Engine.IWD || engine == Engine.EE || engine == Engine.Unknown));
    addEntry(Key.IS_SUPPORTED_ITM_V11, Type.BOOLEAN, (engine == Engine.PST));
    addEntry(Key.IS_SUPPORTED_ITM_V20, Type.BOOLEAN, (engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_KEY, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_LOG, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_LUA, Type.BOOLEAN, isEnhancedEdition() || game == Game.IWD2EE);

    addEntry(Key.IS_SUPPORTED_MAZE, Type.BOOLEAN, game == Game.PSTEE);

    addEntry(Key.IS_SUPPORTED_MENU, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_MOS_V1, Type.BOOLEAN, true);
    addEntry(Key.IS_SUPPORTED_MOSC_V1, Type.BOOLEAN,
        (engine == Engine.BG2 || engine == Engine.IWD || engine == Engine.IWD2 || engine == Engine.EE));
    addEntry(Key.IS_SUPPORTED_MOS_V2, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_MUS, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_MVE, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_OGG, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_PLT, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_PNG, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_PRO, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.EE));

    addEntry(Key.IS_SUPPORTED_PVRZ, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_RES, Type.BOOLEAN, (engine == Engine.IWD || engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_SAV, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_SPL_V1, Type.BOOLEAN, (engine == Engine.BG1 || engine == Engine.BG2
        || engine == Engine.IWD || engine == Engine.PST || engine == Engine.EE || engine == Engine.Unknown));
    addEntry(Key.IS_SUPPORTED_SPL_V2, Type.BOOLEAN, (engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_SQL, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_SRC_PST, Type.BOOLEAN, (engine == Engine.PST || game == Game.PSTEE));

    addEntry(Key.IS_SUPPORTED_SRC_IWD2, Type.BOOLEAN, (engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_STO_V10, Type.BOOLEAN,
        ((engine == Engine.BG1 || engine == Engine.BG2 || engine == Engine.EE || engine == Engine.Unknown)
            && game != Game.PSTEE));
    addEntry(Key.IS_SUPPORTED_STO_V11, Type.BOOLEAN, (engine == Engine.PST || game == Game.PSTEE));
    addEntry(Key.IS_SUPPORTED_STO_V90, Type.BOOLEAN, (engine == Engine.IWD || engine == Engine.IWD2));

    addEntry(Key.IS_SUPPORTED_TIS_V1, Type.BOOLEAN, true);
    addEntry(Key.IS_SUPPORTED_TIS_V2, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_TLK, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_TO_V1, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.IWD));
    addEntry(Key.IS_SUPPORTED_TO_V2, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_TTF, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_VAR, Type.BOOLEAN, (engine == Engine.PST));

    addEntry(Key.IS_SUPPORTED_VEF, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.EE));

    addEntry(Key.IS_SUPPORTED_VVC, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.EE));

    addEntry(Key.IS_SUPPORTED_WAV, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_WAVC, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_WBM, Type.BOOLEAN, isEnhancedEdition());

    addEntry(Key.IS_SUPPORTED_WED, Type.BOOLEAN, true);

    addEntry(Key.IS_SUPPORTED_WFX, Type.BOOLEAN, (engine == Engine.BG2 || engine == Engine.EE));

    addEntry(Key.IS_SUPPORTED_WMP, Type.BOOLEAN, true);
  }

  // Initializes game-specific features
  private void initFeatures() {
    Game game = getGame();
    Engine engine = getEngine();

    // Autodetect default charset used by game strings
    addEntry(Key.GET_GAME_CHARSET, Type.STRING, CharsetDetector.guessCharset(true));

    // Are Kits supported?
    addEntry(Key.IS_SUPPORTED_KITS, Type.BOOLEAN,
        (engine == Engine.BG2 || engine == Engine.IWD2 || engine == Engine.EE));

    // the actual name of the "Alignment" IDS resource
    addEntry(Key.GET_IDS_ALIGNMENT, Type.STRING, (engine == Engine.IWD2) ? "ALIGNMNT.IDS" : "ALIGNMEN.IDS");

    // the GAM filename used in saved games
    switch (engine) {
      case IWD:
        addEntry(Key.GET_GAM_NAME, Type.STRING, "ICEWIND.GAM");
        addEntry(Key.GET_SAV_NAME, Type.STRING, "ICEWIND.SAV");
        break;
      case IWD2:
        addEntry(Key.GET_GAM_NAME, Type.STRING, "ICEWIND2.GAM");
        addEntry(Key.GET_SAV_NAME, Type.STRING, "ICEWIND2.SAV");
        break;
      case PST:
        addEntry(Key.GET_GAM_NAME, Type.STRING, "TORMENT.GAM");
        addEntry(Key.GET_SAV_NAME, Type.STRING, "TORMENT.SAV");
        break;
      default:
        addEntry(Key.GET_GAM_NAME, Type.STRING, "BALDUR.GAM");
        addEntry(Key.GET_SAV_NAME, Type.STRING, "BALDUR.SAV");
    }

    // display mode of overlays in tilesets
    addEntry(Key.IS_TILESET_STENCILED, Type.BOOLEAN, (engine == Engine.BG2 || game == Game.BG2EE));

    // Has TobEx been installed?
    if (engine == Engine.BG2) {
      Path tobexIni = FileManager.query(getGameRoot(), "TobEx_ini/TobExCore.ini");
      addEntry(Key.IS_GAME_TOBEX, Type.BOOLEAN, FileEx.create(tobexIni).isFile());
    } else {
      addEntry(Key.IS_GAME_TOBEX, Type.BOOLEAN, Boolean.FALSE);
    }

    // Has EEex been installed?
    if (engine == Engine.EE) {
      Path eeexDb = FileManager.query(getGameRoot(), "EEex.db");  // older EEex versions
      Path eeexDb2 = FileManager.query(getGameRoot(), "InfinityLoader.db");
      addEntry(Key.IS_GAME_EEEX, Type.BOOLEAN, FileEx.create(eeexDb).isFile() || FileEx.create(eeexDb2).isFile());
    } else {
      addEntry(Key.IS_GAME_EEEX, Type.BOOLEAN, Boolean.FALSE);
    }

    // Is Infinity Animations installed?
    boolean isIAv1 = false;
    boolean isIAv2 = false;
    if (engine == Engine.BG2) {
      Path exe = FileManager.queryExisting(getGameRoot(), "bgmain.exe");
      if (exe != null) {
        File exeFile = exe.toFile();
        if (exeFile.length() == 7839790L) {
          try (RandomAccessFile raf = new RandomAccessFile(exeFile, "r")) {
            // checking key signatures
            final int[] sigCheckV1 = { 0x3db6d84, 0xc6004c48, 0x54464958, 0x004141de, 0xf9 };
            final int[] sigCheckV2 = { 0x3db6d84, 0x34004c48, 0x54464958, 0x0041412d, 0xf9 };
            long[] ofs = { 0x40742cL, 0x40a8daL, 0x7536e7L, 0x407713L };
            int[] sig = new int[ofs.length + 1];
            for (int i = 0; i < ofs.length; i++) {
              // reading int signatures
              raf.seek(ofs[i]);
              int b1 = raf.read();
              int b2 = raf.read();
              int b3 = raf.read();
              int b4 = raf.read();
              if ((b1 | b2 | b3 | b4) < 0) {
                throw new EOFException();
              }
              sig[i] = b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
            }

            // reading byte signature
            raf.seek(0x4595c9L);
            sig[ofs.length] = raf.read();

            isIAv1 = Arrays.equals(sig, sigCheckV1);
            isIAv2 = Arrays.equals(sig, sigCheckV2);
          } catch (IOException e) {
            Logger.trace(e);
          }
        }
      }
    }
    if (isIAv1) {
      addEntry(Key.GET_INFINITY_ANIMATIONS, Type.INTEGER, 1); // v5 or earlier
    } else if (isIAv2) {
      addEntry(Key.GET_INFINITY_ANIMATIONS, Type.INTEGER, 2); // v6 or later
    } else {
      addEntry(Key.GET_INFINITY_ANIMATIONS, Type.INTEGER, 0); // not installed
    }

    // Add campaign-specific extra folders
    initCampaigns();
  }

  // Generates a unique session id for this game
  private void initSession() {
    final String id = Long.toHexString(System.currentTimeMillis());
    addEntry(Key.GET_GAME_SESSION_ID, Type.STRING, id);
  }

  // Adds any campaign-specific save folders to the resource tree (EE only)
  private void initCampaigns() {
    final String campaign = "CAMPAIGN.2DA";
    if (isEnhancedEdition() && ResourceFactory.resourceExists(campaign)) {
      Table2da table = Table2daCache.get(campaign);
      if (table == null || table.getRowCount() == 0) {
        return;
      }

      // getting correct column
      final String saveColName = "SAVE_DIR"; // default column name
      int col = 12; // default column index
      for (int i = 0; i < table.getColCount(); i++) {
        if (saveColName.equalsIgnoreCase(table.getHeader(i))) {
          col = i;
          break;
        }
      }
      if (col >= table.getColCount()) {
        return;
      }

      // getting save folder names
      List<String> extraNames = getProperty(Key.GET_GAME_EXTRA_FOLDER_NAMES);
      List<String> saveNames = getProperty(Key.GET_GAME_SAVE_FOLDER_NAMES);
      boolean available = false;
      for (int row = 0; row < table.getRowCount(); row++) {
        String save = table.get(row, col);
        if (save != null && !save.isEmpty()) {
          save = Character.toUpperCase(save.charAt(0)) + save.substring(1).toLowerCase(Locale.ENGLISH);
          String mpsave = "MP" + save;
          boolean checkSave = false, checkMPSave = false;
          for (final String s : extraNames) {
            checkSave |= save.equalsIgnoreCase(s);
            checkMPSave |= mpsave.equalsIgnoreCase(s);
            if (checkSave && checkMPSave) {
              break;
            }
          }
          if (!checkSave) {
            available = true;
            extraNames.add(save);
            saveNames.add(save);
          }
          if (!checkMPSave) {
            available = true;
            extraNames.add(mpsave);
            saveNames.add(mpsave);
          }
        }
      }

      if (available) {
        // updating extra folder name and path list
        Collections.sort(extraNames);
        updateProperty(Key.GET_GAME_EXTRA_FOLDER_NAMES, extraNames);
        Collections.sort(saveNames);
        updateProperty(Key.GET_GAME_SAVE_FOLDER_NAMES, saveNames);
        initExtraFolders();

        // adding new paths to resource tree
        ResourceTreeModel model = ResourceFactory.getResourceTreeModel();
        if (model != null) {
          List<Path> extraDirs = getProperty(Key.GET_GAME_EXTRA_FOLDERS);
          for (final Path path : extraDirs) {
            if (FileEx.create(path).isDirectory()) {
              String folderName = path.getFileName().toString();
              if (model.getFolder(folderName) == null) {
                model.addDirectory(model.getRoot(), path, false);
              }
            }
          }
          model.sort();
        }
      }
    }
  }

  // Registers available DLCs and returns them as list of root paths
  private List<Path> initDlc(Path rootDir, Path homeDir) {
    List<Path> retVal = new ArrayList<>();

    if (homeDir == null) { // assume original IE game or EE game without DLC support
      return retVal;
    }

    List<DataString<Path>> gameFolders = new ArrayList<>();
    // Getting potential DLC folders (search order is important)
    if (rootDir != null && FileEx.create(rootDir).isDirectory()) {
      gameFolders.add(DataString.with("mod", rootDir.resolve("workshop")));
      gameFolders.add(DataString.with("zip", rootDir.resolve("dlc")));
      gameFolders.add(DataString.with("zip", rootDir));
    }
    if (FileEx.create(homeDir).isDirectory()) {
      gameFolders.add(DataString.with("zip", homeDir));
    }

    for (final DataString<Path> root : gameFolders) {
      String ext = root.getString();
      Path dir = root.getData();
      if (dir != null && FileEx.create(dir).isDirectory()) {
        List<Path> list = new ArrayList<>();
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(dir)) {
          for (final Path file : dstream) {
            try {
              Path dlcRoot = validateDlc(file, ext);
              if (dlcRoot != null) {
                list.add(dlcRoot);
              }
            } catch (IOException e) {
              Logger.error(e);
            }
          }
        } catch (IOException e) {
          Logger.error(e);
        }
        // DLCs of the same root are sorted alphabetically (in reverse order)
        if (!list.isEmpty()) {
          Collections.sort(list);
          Collections.reverse(list);
          retVal.addAll(list);
        }
      }
    }

    // registering paths to mod.key files
    List<Path> keyList = new ArrayList<>();
    retVal.forEach(path -> {
      Path keyFile = DlcManager.queryKey(path);
      if (keyFile != null) {
        keyList.add(keyFile);
      }
    });
    if (getProperty(Key.GET_GAME_DLC_KEYS_AVAILABLE) != null) {
      updateProperty(Key.GET_GAME_DLC_KEYS_AVAILABLE, keyList);
    } else {
      addEntry(Key.GET_GAME_DLC_KEYS_AVAILABLE, Type.LIST, keyList);
    }

    return retVal;
  }

  // Checks whether specified file meets the requirement of a DLC archive, which includes
  // is regular file, has correct file extension, is of type zip and contains a valid KEY file in
  // the archive's root folder.
  // Returns the root path of the Dlc archive if available or null.
  private Path validateDlc(Path file, String ext) throws IOException {
    // is regular file?
    if (file == null && !FileEx.create(file).isFile()) {
      return null;
    }

    // has correct file extension?
    String fileName = file.getFileName().toString().toLowerCase(Locale.ENGLISH);
    if (!fileName.endsWith('.' + ext.toLowerCase(Locale.ENGLISH))) {
      return null;
    }

    // is already registered?
    FileSystem fs = DlcManager.getDlc(file);
    if (fs == null) {
      // try to register potential DLC
      fs = DlcManager.register(file);
    }

    if (fs != null) {
      return fs.getPath("/");
    } else {
      return null;
    }
  }

  // Movies in IWD2 can be found in the biff folders
  private void initIwd2ExtraFolders(Game game) {
    if (game != Game.IWD2 && game != Game.IWD2EE) {
      return;
    }

    final Path rootPath = getGameRoot();
    final IniMap iniMap = IniMapCache.get(new FileResourceEntry(rootPath.resolve("icewind2.ini")), true);
    if (iniMap != null) {
      final IniMapSection iniSection = iniMap.getSection("Alias");
      if (iniSection != null) {
        final List<String> folderList = new ArrayList<>(GAME_EXTRA_FOLDERS.get(game));

        // getting reference path
        String refPath = iniSection.getAsString("HD0:", "");
        if (refPath.isEmpty()) {
          refPath = rootPath.toString();
        }
        refPath = refPath.toLowerCase().replace('\\', '/');
        if (refPath.charAt(refPath.length() - 1) != '/') {
          refPath += '/';
        }

        for (final String key : new String[] {"CD0:", "CD1:", "CD2:", "CD3:"}) {
          String pathString = iniSection.getAsString(key, "");
          if (!pathString.isEmpty()) {
            pathString = pathString.replace('\\', '/');
            if (pathString.toLowerCase().startsWith(refPath)) {
              pathString = pathString.substring(refPath.length());
            }
            Path path;
            try {
              path = Paths.get(pathString);
            } catch (InvalidPathException e) {
              Logger.warn("Not a valid path: {}", pathString);
              continue;
            }
            if (!path.isAbsolute()) {
              path = FileManager.resolveExisting(rootPath.resolve(pathString));
            }
            if (path != null && Files.isDirectory(path)) {
              final Path relPath = rootPath.relativize(path);
              folderList.add(relPath.toString());
            }
          }
        }
        GAME_EXTRA_FOLDERS.put(game, folderList);
      }
    }
  }

  // -------------------------- INNER CLASSES --------------------------

  // Internal definition of a property entry
  private static class Property {
    private final Key key;
    private final Type type;

    private Object data;

    /**
     * Initialize a new property.
     *
     * @param key  A unique identifier for the property. Cannot be modified afterwards.
     * @param type The data type of the property. Cannot be modified afterwards.
     * @param data The actual data of the property. Can be modified afterwards.
     */
    public Property(Key key, Type type, Object data) {
      this.key = key;
      this.type = type;
      this.data = data;
    }

    /** Returns a unique key which identifies this property. */
    public Key getKey() {
      return key;
    }

    /** Returns the data type of this property. */
    public Type getType() {
      return type;
    }

    /** Returns the actual data of this property. */
    @SuppressWarnings("unchecked")
    public <T> T getData() {
      return (T) data;
    }

    /** Sets new data value of this property. Returns the previous data. */
    public Object setData(Object newValue) {
      Object retVal = data;
      data = newValue;
      return retVal;
    }

    @Override
    public String toString() {
      return String.format("%s:[%s] = %s", key, type, data);
    }
  }
}
