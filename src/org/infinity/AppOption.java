// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity;

import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.gui.menu.LogLevel;
import org.infinity.gui.menu.OptionsMenuItem;
import org.infinity.gui.menu.OverrideMode;
import org.infinity.gui.menu.ResRefMode;
import org.infinity.gui.menu.ToolsMenu;
import org.infinity.gui.menu.ViewMode;
import org.infinity.resource.Profile;
import org.infinity.resource.are.viewer.AreaViewer;
import org.infinity.updater.UpdaterSettings;
import org.infinity.util.Misc;
import org.infinity.util.Platform;
import org.infinity.util.Weidu;

/** This enum class manages a predefined set of options. */
public class AppOption {
  /**
   * Class path for the BrowserMenuBar {@link Preferences} node. (Default)
   *
   *  @implNote Using old BrowserMenuBar class path for compatibility reasons.
   */
  public static final String PREFS_OPTIONS = "org.infinity.gui.BrowserMenuBar";

  /** Class path for the NearInfinity application {@link Preferences} node. */
  public static final String PREFS_APP = NearInfinity.class.getName();

  /** Class path for the Updater {@link Preferences} node. */
  public static final String PREFS_UPDATER = UpdaterSettings.class.getName();

  /** Class path for resource-related {@link Preferences}. */
  public static final String PREFS_RESOURCES = Profile.class.getName();

  /** Class path for the Area Viewer {@link Preferences} node. */
  public static final String PREFS_AREA_VIEWER = AreaViewer.class.getName();

  // Caches Preferences instances mapped to class path strings for quick lookup
  private static final HashMap<String, Preferences> PREFERENCES = new HashMap<>();

  // References of all defined AppOption instances for automated access
  private static final ArrayList<AppOption> ENTRIES = new ArrayList<>(64);

  // Default validator to use when no custom validator object is specified
  private static final Function<Object, Object> DEFAULT_VALIDATOR = o -> o;

  // Ensures consistency of any given Global Font Size
  private static final Function<Object, Object> GLOBAL_FONT_SIZE_VALIDATOR = scale -> {
    final int min = Arrays.stream(OptionsMenuItem.getFontSizes()).filter(i -> i > 0).min().orElse(0);
    final int max = Arrays.stream(OptionsMenuItem.getFontSizes()).max().orElse(0);
    return Math.max(min, Math.min(max, (Integer) scale));
  };

  // Ensures consistency of any given UI Scaling Factor
  private static final Function<Object, Object> UI_SCALE_FACTOR_VALIDATOR = scale -> {
    final int min = Arrays.stream(OptionsMenuItem.getUiScalingFactors()).filter(i -> i > 0).min().orElse(0);
    final int max = Arrays.stream(OptionsMenuItem.getUiScalingFactors()).max().orElse(0);
    return Math.max(min, Math.min(max, (Integer) scale));
  };

  // Constructs a fully defined default string for the Game Languages option if empty value is assigned
  private static final Function<Object, Object> GAME_LANGUAGES_VALIDATOR = lang -> {
    if (lang == null || ((String) lang).isEmpty()) {
      lang = Arrays
          .stream(Profile.Game.values())
          .filter(Profile::isEnhancedEdition)
          .map(g -> g + "=" + OptionsMenuItem.getDefaultGameLanguage())
          .collect(Collectors.joining(";"));
    }
    return lang;
  };

  // Category: General
  /** Menu Options: BackupOnSave (Boolean, Default: false) */
  public static final AppOption BACKUP_ON_SAVE = new AppOption(OptionsMenuItem.OPTION_BACKUPONSAVE, "Backup on Save",
      false);
  /** Menu Options: LaunchGameAllowed (Boolean, Default: true) */
  public static final AppOption LAUNCH_GAME_ALLOWED = new AppOption(OptionsMenuItem.OPTION_LAUNCHGAMEALLOWED,
      "Allow Launching Games", true);
  /** Menu Options: UpdateTreeOnCopy (Boolean, Default: false) */
  public static final AppOption KEEP_VIEW_ON_COPY = new AppOption(OptionsMenuItem.OPTION_KEEPVIEWONCOPY,
      "Keep View after Copy Operations", false);
  /** Menu Options: ShowStrrefs (Boolean, Default: false) */
  public static final AppOption SHOW_STRREFS = new AppOption(OptionsMenuItem.OPTION_SHOWSTRREFS,
      "Show Strrefs in View Tabs", false);
  /** Menu Options: ShowColoredStructures (Boolean, Default: true) */
  public static final AppOption SHOW_COLORED_STRUCTURES = new AppOption(OptionsMenuItem.OPTION_SHOWCOLOREDSTRUCTURES,
      "Show Colored Structures in Edit Tabs", true);
  /** Menu Options: ShowHexColored (Boolean, Default: true) */
  public static final AppOption SHOW_HEX_COLORED = new AppOption(OptionsMenuItem.OPTION_SHOWHEXCOLORED,
      "Show Colored Blocks in Raw Tabs", true);
  /** Menu Options: ShowSysInfo (Boolean, Default: true) */
  public static final AppOption SHOW_SYS_INFO = new AppOption(OptionsMenuItem.OPTION_SHOWSYSINFO,
      "Display System Information at Startup", true);
  public static final AppOption SHOW_MEM_STATUS = new AppOption(OptionsMenuItem.OPTION_SHOWMEMSTATUS,
      "Display Memory Usage in Status Bar", true);
  /** Menu Options: OpenBookmarksPrompt (Boolean, Default: true) */
  public static final AppOption OPEN_BOOKMARKS_PROMPT = new AppOption(OptionsMenuItem.OPTION_OPENBOOKMARKSPROMPT,
      "Confirm Opening Bookmarked Games", true);
  public static final AppOption OPEN_STRINGTABLE_WARNING = new AppOption(OptionsMenuItem.OPTION_OPENSTRINGTABLEWARNING,
      "Show Backup Warning When Opening String Table Editor", true);
  /** Menu Options: RememberChildFrameRect (Boolean, Default: false) */
  public static final AppOption REMEMBER_CHILD_FRAME_RECT = new AppOption(OptionsMenuItem.OPTION_REMEMBER_CHILDFRAME_RECT,
      "Remember Last Child Frame Size and Position", false);
  /** Menu Options: ShowCreaturesOnPanel (Boolean, Default: false) */
  public static final AppOption SHOW_CREATURES_ON_PANEL = new AppOption(OptionsMenuItem.OPTION_SHOW_CREATURES_ON_PANEL,
      "Show Creatures on Main Panel", false);
  /**
   * Menu Options: OptionFixedInternal (Integer, Default: 0).
   * Note: Used internally to fix incorrect default values after the public release.
   */
  public static final AppOption OPTION_FIXED_INTERNAL = new AppOption(OptionsMenuItem.OPTION_OPTION_FIXED,
      "Internal fixes", 0);

  // Category: Resources
  /** Menu Options > Resources: IgnoreReadErrors (Boolean, Default: false) */
  public static final AppOption IGNORE_READ_ERRORS = new AppOption(OptionsMenuItem.OPTION_IGNOREREADERRORS,
      "Ignore Read Errors", false);
  /** Menu Options > Resources: ShowUnknownResources (Boolean, Default: true) */
  public static final AppOption SHOW_UNKNOWN_RESOURCES = new AppOption(OptionsMenuItem.OPTION_SHOWUNKNOWNRESOURCES,
      "Show Unknown Resource Types", true);
  /** Menu Options > Resources: ShowNonExistingResources (Boolean, Default: false) */
  public static final AppOption SHOW_NONEXISTING_RESOURCES = new AppOption(OptionsMenuItem.OPTION_SHOWNONEXISTINGRESOURCES,
      "Show Non-Existing Resources", false);
  /** Menu Options > Resources: ShowTreeSearchNames (Boolean, Default: true) */
  public static final AppOption SHOW_TREE_SEARCH_NAMES = new AppOption(OptionsMenuItem.OPTION_SHOWTREESEARCHNAMES,
      "Show Search Names in Resource Tree", true);
  /** Menu Options > Resources: Show Icons in Resource List (Boolean, Default: false) */
  public static final AppOption SHOW_RESOURCE_LIST_ICONS = new AppOption(OptionsMenuItem.OPTION_SHOW_RESOURCE_LIST_ICONS,
      "Show Icons in Resource List", false);
  /** Menu Options > Resources: Show Icons in Resource Tree (Boolean, Default: false) */
  public static final AppOption SHOW_RESOURCE_TREE_ICONS = new AppOption(OptionsMenuItem.OPTION_SHOW_RESOURCE_TREE_ICONS,
      "Show Icons in Resource Tree", false);
  /** Menu Options > Resources: HighlightOverridden (Boolean, Default: true) */
  public static final AppOption HIGHLIGHT_OVERRIDDEN = new AppOption(OptionsMenuItem.OPTION_HIGHLIGHT_OVERRIDDEN,
      "Show Overridden Files in Bold in Resource Tree", true);
  /** Menu Options > Resources: OverrideSortByType (Boolean, Default: false) */
  public static final AppOption OVERRIDE_SORT_BY_TYPE = new AppOption(OptionsMenuItem.OPTION_OVERRIDE_SORT_BY_TYPE,
      "Sort Override Files in Resource Tree by Type", false);
  /** Menu Options > Resources: CacheOverride (Boolean, Default: true) */
  public static final AppOption CACHE_OVERRIDE = new AppOption(OptionsMenuItem.OPTION_CACHEOVERRIDE,
      "Autocheck for Overrides", true);
  /** Menu Options > Resources: LowerCasedExt (Boolean, Default: {@code true} on Linux, {@code false} otherwise) */
  public static final AppOption LOWERCASED_EXPORT = new AppOption(OptionsMenuItem.OPTION_LOWERCASED_EXPORT,
      "Use Lowercased Filenames on Export", Platform.IS_UNIX);
  /** Menu Options > Resources: ShowLastIdsSymbol (Boolean, Default: true) */
  public static final AppOption IDS_SYMBOL_DISPLAY = new AppOption(OptionsMenuItem.OPTION_IDS_SYMBOL_DISPLAY,
      "Use Last Available IDS Symbol for Display", true);
  /** Menu Options > Resources: getOpenResourceMenuEnabled (Boolean, Default: true) */
  public static final AppOption OPEN_RESOURCE_TEXT_MENU = new AppOption(OptionsMenuItem.OPTION_OPEN_RESOURCE_TEXT_MENU,
      "Add \"Open resource\" option to text resource context menus", true);
  /** Menu Options > Resources: getOpenIdsMenuEnabled (Boolean, Default: true) */
  public static final AppOption OPEN_IDS_TEXT_MENU = new AppOption(OptionsMenuItem.OPTION_OPEN_IDS_TEXT_MENU,
      "Add \"Open IDS symbol\" option to text resource context menus", true);
  /** Menu Options > Resources: getOpenStrrefMenuEnabled (Boolean, Default: true) */
  public static final AppOption OPEN_STRREF_TEXT_MENU = new AppOption(OptionsMenuItem.OPTION_OPEN_STRREF_TEXT_MENU,
      "Add \"Open strref\" option to text resource context menus", true);
  /** Menu Options > Resources: getWeiduPath (Path, Default: weidu binary of the system if available) */
  public static final AppOption WEIDU_PATH = new AppOption(OptionsMenuItem.OPTION_WEIDU_PATH, "WeiDU path",
      Objects.toString(Weidu.getWeiduPath(), ""));

  // Category: Table Columns
  /** Menu Options > Show Table Columns: ShowOffsets (Boolean, Default: true) */
  public static final AppOption TABLE_SHOW_OFFSETS = new AppOption(OptionsMenuItem.OPTION_SHOWOFFSETS,
      "Show Field Offsets", true);
  /** Menu Options > Show Table Columns: ShowOffsetsRelative (Boolean, Default: false) */
  public static final AppOption TABLE_SHOW_OFFSETS_RELATIVE = new AppOption(OptionsMenuItem.OPTION_SHOWOFFSETSRELATIVE,
      "Show Relative Field Offsets", false);
  /** Menu Options > Show Table Columns: ShowSize (Boolean, Default: true) */
  public static final AppOption TABLE_SHOW_SIZE = new AppOption(OptionsMenuItem.OPTION_SHOWSIZE, "Show Field Sizes", true);
  /** Menu Options > Show Table Columns: ShowSizeHex (Boolean, Default: false) */
  public static final AppOption TABLE_SHOW_SIZE_HEX = new AppOption(OptionsMenuItem.OPTION_SHOWSIZEHEX,
      "Show Field Size as Hex Number", false);

  // Category: Script Compiler
  /** Menu Options > Script Compiler: AutocheckBCS (Boolean, Default: true) */
  public static final AppOption AUTO_CHECK_BCS = new AppOption(OptionsMenuItem.OPTION_AUTOCHECK_BCS,
      "Autocheck Script Source", true);
  /** Menu Options > Script Compiler: MoreCompilerWarnings (Boolean, Default: false) */
  public static final AppOption MORE_COMPILER_WARNINGS = new AppOption(OptionsMenuItem.OPTION_MORECOMPILERWARNINGS,
      "Show More Compiler Warnings", false);
  /** Menu Options > Script Compiler: AutogenBCSComments (Boolean, Default: true) */
  public static final AppOption AUTO_GEN_BCS_COMMENTS = new AppOption(OptionsMenuItem.OPTION_AUTOGEN_BCS_COMMENTS,
      "Autogenerate Script Comments", true);

  // Category: Text Editor
  /** Menu Options > Text Editor: TextShowWhiteSpace (Boolean, Default: false) */
  public static final AppOption TEXT_SHOW_WHITE_SPACE = new AppOption(OptionsMenuItem.OPTION_TEXT_SYMBOLWHITESPACE,
      "Show Spaces and Tabs", false);
  /** Menu Options > Text Editor: TextShowEOL (Boolean, Default: false) */
  public static final AppOption TEXT_SHOW_EOL = new AppOption(OptionsMenuItem.OPTION_TEXT_SYMBOLEOL, "Show End Of Line",
      false);
  /** Menu Options > Text Editor: TextShowCurrentLine (Boolean, Default: true) */
  public static final AppOption TEXT_SHOW_CURRENT_LINE = new AppOption(OptionsMenuItem.OPTION_TEXT_SHOWCURRENTLINE,
      "Show Highlighted Current Line", true);
  /** Menu Options > Text Editor: TextShowLineNumbers (Boolean, Default: true) */
  public static final AppOption TEXT_SHOW_LINE_NUMBERS = new AppOption(OptionsMenuItem.OPTION_TEXT_SHOWLINENUMBERS,
      "Show Line Numbers", true);
  /** Menu Options > Text Editor: TextTabsEmulated (Boolean, Default: true) */
  public static final AppOption TEXT_TABS_EMULATED = new AppOption(OptionsMenuItem.OPTION_TEXT_TABSEMULATED,
      "Emulate Tabs with Spaces (Soft Tabs)", true);
  /** Menu Options > Text Editor: TextTabSize (Integer, Default: 1) */
  public static final AppOption TEXT_TAB_SIZE = new AppOption(OptionsMenuItem.OPTION_TEXT_TABSIZE, "Tab Size", 1);

  // Category: BCS and BAF
  /** Menu Options > BCS and BAF: BcsColorScheme (Integer, Default: last item in list) */
  public static final AppOption BCS_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_BCS_COLORSCHEME,
      "BCS Color Scheme", OptionsMenuItem.getBcsColorSchemes().size() - 1);
  /** Menu Options > BCS and BAF: BcsSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption BCS_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_BCS_SYNTAXHIGHLIGHTING,
      "Enable BCS Syntax Highlighting", true);
  /** Menu Options > BCS and BAF: BcsCodeFolding (Boolean, Default: false) */
  public static final AppOption BCS_CODE_FOLDING = new AppOption(OptionsMenuItem.OPTION_BCS_CODEFOLDING,
      "Enable BCS Code Folding", false);
  /** Menu Options > BCS and BAF: BcsAutoIndent (Boolean, Default: false) */
  public static final AppOption BCS_AUTO_INDENT = new AppOption(OptionsMenuItem.OPTION_BCS_AUTO_INDENT,
      "Enable Automatic Indentation", true);
  /** Menu Options > BCS and BAF: BcsIndent (Integer, Default: 1) */
  public static final AppOption BCS_INDENT = new AppOption(OptionsMenuItem.OPTION_BCS_INDENT, "Indentation", 1);

  // Category: Misc. Types
  /** Menu Options > Misc. Types: AutoAlign2da (Integer, Default: 0) */
  public static final AppOption AUTO_ALIGN_2DA = new AppOption(OptionsMenuItem.OPTION_2DA_AUTOALIGN,
      "Auto-Align 2DA Columns", 0);
  /** Menu Options > Misc. Types: GlslColorScheme (Integer, Default: 0) */
  public static final AppOption GLSL_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_GLSL_COLORSCHEME,
      "GLSL Color Scheme", 0);
  /** Menu Options > Misc. Types: GlslSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption GLSL_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_GLSL_SYNTAXHIGHLIGHTING,
      "Enable GLSL Syntax Highlighting", true);
  /** Menu Options > Misc. Types: GlslCodeFolding (Boolean, Default: false) */
  public static final AppOption GLSL_CODE_FOLDING = new AppOption(OptionsMenuItem.OPTION_GLSL_CODEFOLDING,
      "Enable GLSL Code Folding", false);
  /** Menu Options > Misc. Types: IniColorScheme (Integer, Default: 0) */
  public static final AppOption INI_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_INI_COLORSCHEME,
      "INI Color Scheme", 0);
  /** Menu Options > Misc. Types: IniSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption INI_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_INI_SYNTAXHIGHLIGHTING,
      "Enable INI Syntax Highlighting", true);
  /** Menu Options > Misc. Types: LuaColorScheme (Integer, Default: 0) */
  public static final AppOption LUA_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_LUA_COLORSCHEME,
      "LUA Color Scheme", 0);
  /** Menu Options > Misc. Types: LuaSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption LUA_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_LUA_SYNTAXHIGHLIGHTING,
      "Enable LUA Syntax Highlighting", true);
  /** Menu Options > Misc. Types: MenuColorScheme (Integer, Default: 0) */
  public static final AppOption MENU_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_MENU_COLORSCHEME,
      "MENU Color Scheme", 0);
  /** Menu Options > Misc. Types: MenuSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption MENU_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_MENU_SYNTAXHIGHLIGHTING,
      "Enable MENU Syntax Highlighting", true);
  /** Menu Options > Misc. Types: SqlColorScheme (Integer, Default: 0) */
  public static final AppOption SQL_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_SQL_COLORSCHEME,
      "SQL Color Scheme", 0);
  /** Menu Options > Misc. Types: SqlSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption SQL_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_SQL_SYNTAXHIGHLIGHTING,
      "Enable SQL Syntax Highlighting", true);
  /** Menu Options > Misc. Types: TlkColorScheme (Integer, Default: 0) */
  public static final AppOption TLK_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_TLK_COLORSCHEME,
      "TLK Color Scheme", 0);
  /** Menu Options > Misc. Types: TlkSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption TLK_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_TLK_SYNTAXHIGHLIGHTING,
      "Enable TLK Syntax Highlighting", true);
  /** Menu Options > Misc. Types: WeiDUColorScheme (Integer, Default: 0) */
  public static final AppOption WEIDU_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_WEIDU_COLORSCHEME,
      "WeiDU Color Scheme", 0);
  /** Menu Options > Misc. Types: WeiDUSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption WEIDU_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_WEIDU_SYNTAXHIGHLIGHTING,
      "Enable WeiDU Syntax Highlighting", true);

  // Category: Dialog Tree Viewer
  /** Menu Options > Dialog Tree Viewer: DlgColorScheme (Integer, Default: last item in list) */
  public static final AppOption DLG_COLOR_SCHEME = new AppOption(OptionsMenuItem.OPTION_DLG_COLORSCHEME,
      "Color Scheme", OptionsMenuItem.getDlgColorSchemes().size() - 1);
  /** Menu Options > Dialog Tree Viewer: DlgSyntaxHighlighting (Boolean, Default: true) */
  public static final AppOption DLG_SYNTAX_HIGHLIGHTING = new AppOption(OptionsMenuItem.OPTION_DLG_SYNTAXHIGHLIGHTING,
      "Enable Syntax Highlighting", true);
  /** Menu Options > Dialog Tree Viewer: DlgShowIcons (Boolean, Default: true) */
  public static final AppOption DLG_SHOW_ICONS = new AppOption(OptionsMenuItem.OPTION_SHOWICONS, "Show Icons", true);
  /** Menu Options > Dialog Tree Viewer: DlgSortStatesByWeight (Boolean, Default: true) */
  public static final AppOption DLG_SORT_STATES_BY_WEIGHT = new AppOption(OptionsMenuItem.OPTION_SORT_STATES_BY_WEIGHT,
      "Sort States by Weight", true);
  /** Menu Options > Dialog Tree Viewer: DlgAlwaysShowState0 (Boolean, Default: false) */
  public static final AppOption DLG_ALWAYS_SHOW_STATE_0 = new AppOption(OptionsMenuItem.OPTION_ALWAYS_SHOW_STATE_0,
      "Always Show State 0", false);
  /** Menu Options > Dialog Tree Viewer: DlgColorizeOtherDialogs (Boolean, Default: true) */
  public static final AppOption DLG_COLORIZE_OTHER_DIALOGS = new AppOption(OptionsMenuItem.OPTION_COLORIZE_OTHER_DIALOGS,
      "Show Colored Entries from Other Dialogs", true);
  /** Menu Options > Dialog Tree Viewer: DlgBreakCycles (Boolean, Default: true) */
  public static final AppOption DLG_BREAK_CYCLES = new AppOption(OptionsMenuItem.OPTION_BREAK_CYCLES,
      "Break Cycles (NWN-like Tree)", true);
  /** Menu Options > Dialog Tree Viewer: DlgColorizeResponses (Boolean, Default: false) */
  public static final AppOption DLG_COLORIZE_RESPONSES = new AppOption(OptionsMenuItem.OPTION_COLORIZE_RESPONSES,
      "Use Different Color for Responses (PC Replies)", false);
  /** Menu Options > Dialog Tree Viewer: DlgShowTechInfo (Boolean, Default: true) */
  public static final AppOption DLG_SHOW_TECH_INFO = new AppOption(OptionsMenuItem.OPTION_SHOW_TECH_INFO,
      "Show State/Response Numbers", true);

  // Category: Visual Options
  /** Menu Options > Visual Options: AppLogLevel (Integer, Default: Level.INFO) */
  public static final AppOption APP_LOG_LEVEL = new AppOption(NearInfinity.APP_LOG_LEVEL, "Application Log Level",
      LogLevel.INFO.ordinal());
  /** Menu Options > Visual Options: ShowResRef (Integer, Default: ResRefMode.RefName) */
  public static final AppOption SHOW_RES_REF = new AppOption(OptionsMenuItem.OPTION_SHOWRESREF, "Show ResourceRef As",
      ResRefMode.RefName.ordinal());
  /** Menu Options > Visual Options: ShowOverridesIn (Integer, Default: OverrideMode.Split) */
  public static final AppOption SHOW_OVERRIDES_IN = new AppOption(OptionsMenuItem.OPTION_SHOWOVERRIDES,
      "Show Override Files", OverrideMode.Split.ordinal());
  /** Menu Options > Visual Options: ViewOrEditShown (Integer, Default: ViewMode.Edit) */
  public static final AppOption VIEW_OR_EDIT_SHOWN = new AppOption(OptionsMenuItem.OPTION_VIEWOREDITSHOWN,
      "Default Structure Display", ViewMode.Edit.ordinal());
  /** Menu Options > Visual Options: AppUiScaleEnabled (Boolean, Default: false) */
  public static final AppOption UI_SCALE_ENABLED = new AppOption(NearInfinity.APP_UI_SCALE_ENABLED, "Enabled", false,
      PREFS_APP);
  /** Menu Options > Visual Options: AppUiScaleFactor (Integer, Default: 100) */
  public static final AppOption UI_SCALE_FACTOR = new AppOption(NearInfinity.APP_UI_SCALE_FACTOR, "Scale Factor", 100,
      PREFS_APP, UI_SCALE_FACTOR_VALIDATOR);
  /** Menu Options > Visual Options: GlobalFontSize (Integer, Default: 100) */
  public static final AppOption GLOBAL_FONT_SIZE = new AppOption(NearInfinity.OPTION_GLOBAL_FONT_SIZE,
      "Scale Global Font Size", 100, PREFS_APP, GLOBAL_FONT_SIZE_VALIDATOR);
  /** Menu Options > Visual Options: LookAndFeelClass (String, Default: "javax.swing.plaf.metal.MetalLookAndFeel") */
  public static final AppOption LOOK_AND_FEEL_CLASS = new AppOption(OptionsMenuItem.OPTION_LOOKANDFEELCLASS,
      "Look and Feel UI", BrowserMenuBar.getDefaultLookAndFeel().getClassName());
  /** Menu Options > Visual Options: LookAndFeelAutoMode (Boolean, Default: false) */
  public static final AppOption LOOK_AND_FEEL_AUTO_MODE = new AppOption(OptionsMenuItem.OPTION_LOOKANDFEEL_AUTOMODE,
      "Follow system color scheme", false);
  /** Menu Options > Visual Options: Font (Integer, Default: 0) */
  public static final AppOption TEXT_FONT = new AppOption(OptionsMenuItem.OPTION_FONT, "Text Font", 0);
  /** Menu Options > Visual Options: FontName (String, Default: "") */
  public static final AppOption TEXT_FONT_NAME = new AppOption(OptionsMenuItem.OPTION_FONT_NAME, "Text Font Name", "");
  /** Menu Options > Visual Options: FontStyle (Integer, Default: Font.PLAIN) */
  public static final AppOption TEXT_FONT_STYLE = new AppOption(OptionsMenuItem.OPTION_FONT_STYLE, "Text Font Style",
      Font.PLAIN);
  /** Menu Options > Visual Options: FontSize (Integer, Default: 12) */
  public static final AppOption TEXT_FONT_SIZE = new AppOption(OptionsMenuItem.OPTION_FONT_SIZE, "Text Font Size", 12);
  /** Menu Options > Visual Options: TLKCharsetType (String, Default: "Auto") */
  public static final AppOption TLK_CHARSET_TYPE = new AppOption(OptionsMenuItem.OPTION_TLKCHARSET,
      "TLK Character Encoding", OptionsMenuItem.getDefaultCharset());
  /** Menu Options > Visual Options: GameLanguages (String, Default: "") */
  public static final AppOption GAME_LANGUAGES = new AppOption(OptionsMenuItem.OPTION_LANGUAGE_GAMES,
      "TLK Language (EE only)", "", GAME_LANGUAGES_VALIDATOR);

  // Tools Menu
  /** Menu Tools: DebugShowExtraInfo (Boolean, Default: false) */
  public static final AppOption DEBUG_SHOW_EXTRA_INFO = new AppOption(ToolsMenu.TOOLS_DEBUG_EXTRA_INFO,
      "Print debug info to Console", false);

  // Application settings
  /** Application: WindowSizeX (Integer, Default: 67% of screen width) */
  public static final AppOption APP_WINDOW_SIZE_X = new AppOption(NearInfinity.WINDOW_SIZEX, "Application Window Width",
      Math.max(800, (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 2 / 3), PREFS_APP);
  /** Application: WindowSizeY (Integer, Default: 67% of screen height) */
  public static final AppOption APP_WINDOW_SIZE_Y = new AppOption(NearInfinity.WINDOW_SIZEY, "Application Window Height",
      Math.max(600, (int) Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 2 / 3), PREFS_APP);
  /** Application: WindowPosX (Integer, Default: screen center) */
  public static final AppOption APP_WINDOW_POS_X = new AppOption(NearInfinity.WINDOW_POSX, "Application Window X",
      ((int) Toolkit.getDefaultToolkit().getScreenSize().getWidth() - (Integer) APP_WINDOW_SIZE_X.getValue()) / 2,
      PREFS_APP);
  /** Application: WindowPosY (Integer, Default: screen center) */
  public static final AppOption APP_WINDOW_POS_Y = new AppOption(NearInfinity.WINDOW_POSY, "Application Window Y",
      ((int) Toolkit.getDefaultToolkit().getScreenSize().getHeight() - (Integer) APP_WINDOW_SIZE_Y.getValue()) / 2,
      PREFS_APP);
  /** Application: WindowState (Integer, Default: Frame.NORMAL) */
  public static final AppOption APP_WINDOW_STATE = new AppOption(NearInfinity.WINDOW_STATE, "Application Window State",
      Frame.NORMAL, PREFS_APP);
  /** Application: WindowSplitter (Integer, Default: 200) */
  public static final AppOption APP_WINDOW_SPLITTER = new AppOption(NearInfinity.WINDOW_SPLITTER,
      "Application Window Splitter", 200, PREFS_APP);
  /** Application: LastGameDir (Integer(nullable), Default: null) */
  public static final AppOption LAST_GAME_DIR = new AppOption(NearInfinity.LAST_GAMEDIR, "Last Game Directory", null,
      PREFS_APP, String.class);
  /** Application: TableColWidthAttr (Integer, Default: 300) */
  public static final AppOption TABLE_COLUMN_ATTRIBUTE_WIDTH = new AppOption(NearInfinity.TABLE_WIDTH_ATTR,
      "Table Column Width (Attribute)", 300, PREFS_APP);
  /** Application: TableColWidthOfs (Integer, Default: 100) */
  public static final AppOption TABLE_COLUMN_OFFSET_WIDTH = new AppOption(NearInfinity.TABLE_WIDTH_OFS,
      "Table Column Width (Offset)", 100, PREFS_APP);
  /** Application: TableColWidthSize (Integer, Default: 75) */
  public static final AppOption TABLE_COLUMN_SIZE_WIDTH = new AppOption(NearInfinity.TABLE_WIDTH_SIZE,
      "Table Column Width (Size)", 75, PREFS_APP);
  /** Application: TablePanelHeight (Integer, Default: 250) */
  public static final AppOption TABLE_PANEL_HEIGHT = new AppOption(NearInfinity.TABLE_PANEL_HEIGHT,
      "Table Details Panel Height", 250, PREFS_APP);

  // Name of the Preferences node where this option is stored
  private final String prefsNode;
  // Option name
  private final String name;
  // A short descriptive label for display
  private final String label;
  // A generic default value that is used if no actual value is provided
  private final Object defValue;
  // Class type of the value; required if default value is null
  private final Class<?> valueType;
  // Called whenever a value (default, initial, current) is assigned as a means to ensure intended consistency
  private final Function<Object, Object> validator;

  // Value as assigned by setInitialValue(E) or loaded via AppOption(String, E)
  private Object initialValue;
  // Value assigned by setValue(E)
  private Object value;

  /**
   * Returns the first available {@link AppOption} instance matching the specified option name.
   *
   * @param name Name of the option to find.
   * @return An {@code AppOption} instance if successful, {@code null} otherwise.
   */
  public static AppOption find(String name) {
    return AppOption
        .getInstances().stream()
        .filter(o -> o.getName().equalsIgnoreCase(name))
        .findAny()
        .orElse(null);
  }

  /**
   * Returns whether there are one or more options with modified values.
   *
   * @return {@code true} if one or more options have been modified, {@code false} otherwise.
   */
  public static boolean isAnyModified() {
    return AppOption.getInstances().stream().anyMatch(AppOption::isModified);
  }

  /** Returns a set of all {@code AppOption} that have been modified. */
  public static List<AppOption> getModifiedOptions() {
    return AppOption.getInstances().stream().filter(AppOption::isModified).collect(Collectors.toList());
  }

  /**
   * Returns a set of all {@code AppOption} matching the specified {@code Preferences} node.
   * {@code null} argument is allowed.
   */
  public static List<AppOption> getOptionsByPrefs(String prefsNode) {
    return AppOption
        .getInstances()
        .stream()
        .filter(o -> (prefsNode == null && o.getPrefsNode() == null) ||
            (prefsNode != null && prefsNode.equals(o.getPrefsNode())))
        .collect(Collectors.toList());
  }

  /** Discards any changes made to the options and resets them to their initial values. */
  public static void revertAll() {
    AppOption.getInstances().forEach(AppOption::revert);
  }

  /** Writes all options of this enum back to the persistent Preferences storage. */
  public static void storePreferences() {
    storePreferences(null);
  }

  /**
   * Writes the specified options back to the specified persistent Preferences storage.
   *
   * @param collection Collection of {@code AppOptions} to store. Specify {@code null} to store all
   * options defined by this {@code enum}.
   */
  public static void storePreferences(Collection<AppOption> collection) {
    if (collection == null) {
      collection = AppOption.getInstances();
    }
    collection.forEach(AppOption::storeValue);
  }

  // /** Used internally to allow only supported types for the generic argument. */
  /** Used internally to check if the given class type is a supported value type. */
  private static<E> Class<E> validateType(Class<E> classType) throws ClassCastException {
    if (Boolean.class.isAssignableFrom(classType) ||
        Integer.class.isAssignableFrom(classType) ||
        Long.class.isAssignableFrom(classType) ||
        Float.class.isAssignableFrom(classType) ||
        Double.class.isAssignableFrom(classType) ||
        String.class.isAssignableFrom(classType)) {
      // pass
      return classType;
    } else {
      // fail
      final String t = classType.getSimpleName();
      throw new ClassCastException("Unsupported value type: " + t);
    }
  }

  /** Returns a {@link Preferences} instance for the specified node. */
  private static Preferences getPrefs(String prefsNode) {
    if (prefsNode != null) {
      return PREFERENCES.computeIfAbsent(prefsNode, Misc::getPrefs);
    } else {
      return null;
    }
  }

  /** Ensures that no cached {@code AppOption} instances exist with the specified option name. */
  private static String isValidInstance(String optionName) throws IllegalArgumentException {
    Objects.requireNonNull(optionName);
    if (ENTRIES.stream().anyMatch(o -> o.getName().equals(optionName))) {
      throw new IllegalArgumentException("Duplicate option names are prohibited");
    }
    return optionName;
  }

  /** Adds the specified {@code AppOption} instance to the cache. Returns the added option. */
  private static AppOption addInstance(AppOption option) {
    if (option != null) {
      ENTRIES.add(option);
    }
    return option;
  }

  /** Removes the specified {@code AppOption} from the cache. Returns the success state. */
  @SuppressWarnings("unused")
  private static boolean removeInstance(AppOption option) {
    if (option != null) {
      return ENTRIES.remove(option);
    } else {
      return false;
    }
  }

  /** Returns an unmodifiable list of all cached {@code AppOption} instances. */
  public static List<AppOption> getInstances() {
    return Collections.unmodifiableList(ENTRIES);
  }

  /**
   * Initializes the option with a name and a default value.
   *
   * <p>Option is automatically associated with the {@link #PREFS_OPTIONS} preferences instance.</p>
   *
   * @param name     Unique name of the option.
   * @param defValue Default value that can be used if the actual value is not available.
   * @throws NullPointerException if either {@code name} or {@code defValue} are {@code null}.
   */
  public AppOption(String name, String label, Object defValue) {
    this(name, label, defValue, PREFS_OPTIONS, null, null);
  }

  /**
   * Initializes the option with a name and a default value.
   *
   * <p>Option is automatically associated with the {@link #PREFS_OPTIONS} preferences instance.</p>
   *
   * @param name      Unique name of the option.
   * @param defValue  Default value that can be used if the actual value is not available.
   * @param validator A function object that is called whenever a new value is assigned to the default, initial or
   *                  current value. It is intended as a means to validate consistency of the value.
   * @throws NullPointerException if either {@code name} or {@code defValue} are {@code null}.
   */
  public AppOption(String name, String label, Object defValue, Function<Object, Object> validator) {
    this(name, label, defValue, PREFS_OPTIONS, null, validator);
  }

  /**
   * Initializes the option with a name and loads the actual value from the specified {@link Preferences}.
   *
   * @param name      Unique name of the option.
   * @param defValue  A default value that can be used if the actual value is not available.
   * @param prefsNode Class path as string that is used as {@link Preferences} node for loading and storing the option
   *                  value. Specify {@code null} if the value is not associated with persistent storage.
   * @throws NullPointerException if either {@code name} or {@code defValue} are {@code null}.
   */
  public AppOption(String name, String label, Object defValue, String prefsNode) {
    this(name, label, defValue, prefsNode, null, null);
  }

  /**
   * Initializes the option with a name and loads the actual value from the specified {@link Preferences}.
   *
   * @param name      Unique name of the option.
   * @param defValue  A default value that can be used if the actual value is not available.
   * @param prefsNode Class path as string that is used as {@link Preferences} node for loading and storing the option
   *                  value. Specify {@code null} if the value is not associated with persistent storage.
   * @param validator A function object that is called whenever a new value is assigned to the default, initial or
   *                  current value. It is intended as a means to validate consistency of the value.
   * @throws NullPointerException if either {@code name} or {@code defValue} are {@code null}.
   */
  public AppOption(String name, String label, Object defValue, String prefsNode, Function<Object, Object> validator) {
    this(name, label, defValue, prefsNode, null, validator);
  }

  /**
   * Initializes the option with a name and loads the actual value from the specified {@link Preferences}. Class type of
   * the value is determined by the specified {@code classType}.
   *
   * @param name      Unique name of the option.
   * @param defValue  A default value that can be used if the actual value is not available.
   * @param prefsNode Class path as string that is used as {@link Preferences} node for loading and storing the option
   *                  value. Specify {@code null} if the value is not associated with persistent storage.
   * @param classType The class type of value that is managed by this {@code AppOption} instance. Specify {@code null}
   *                  to determine class type from the {@code defValue} parameter.
   * @throws NullPointerException if either {@code name} or {@code defValue} are {@code null}.
   * @throws ClassCastException   if the class type could not be determined from either {@code defValue} or
   *                              {@code classType}, or the value type is not supported by the {@code AppOption} class.
   */
  public AppOption(String name, String label, Object defValue, String prefsNode, Class<?> classType) {
    this(name, label, defValue, prefsNode, classType, null);
  }

  /**
   * Initializes the option with a name and loads the actual value from the specified {@link Preferences}. Class type of
   * the value is determined by the specified {@code classType}.
   *
   * @param name      Unique name of the option.
   * @param defValue  A default value that can be used if the actual value is not available.
   * @param prefsNode Class path as string that is used as {@link Preferences} node for loading and storing the option
   *                  value. Specify {@code null} if the value is not associated with persistent storage.
   * @param classType The class type of value that is managed by this {@code AppOption} instance. Specify {@code null}
   *                  to determine class type from the {@code defValue} parameter.
   * @param validator A function object that is called whenever a new value is assigned to the default, initial or
   *                  current value. It is intended as a means to validate consistency of the value.
   * @throws NullPointerException if either {@code name} or {@code defValue} are {@code null}.
   * @throws ClassCastException   if the class type could not be determined from either {@code defValue} or
   *                              {@code classType}, or the value type is not supported by the {@code AppOption} class.
   */
  public AppOption(String name, String label, Object defValue, String prefsNode, Class<?> classType,
      Function<Object, Object> validator) {
    this.validator = (validator != null) ? validator : DEFAULT_VALIDATOR;
    this.name = isValidInstance(name);
    this.label = (label != null) ? label : this.name;
    if (classType != null) {
      this.valueType = validateType(classType);
    } else {
      this.valueType = validateType(Objects.requireNonNull(defValue).getClass());
    }
    this.defValue = validate(defValue);
    this.initialValue = this.defValue;
    this.value = this.defValue;
    this.prefsNode = prefsNode;

    loadValue();

    addInstance(this);
  }

  /** Returns the {@code Preferences} node where this option is stored. May be {@code null}. */
  public String getPrefsNode() {
    return prefsNode;
  }

  /** Returns the {@code Preferences} instance where this option can be read or written to. */
  public Preferences getPrefs() {
    return getPrefs(prefsNode);
  }

  /** Returns the name of the option. */
  public String getName() {
    return name;
  }

  /** Returns the descriptive label associated with this option. */
  public String getLabel() {
    return label;
  }

  /** Returns the generic default value provided for the option. */
  public Object getDefault() {
    return defValue;
  }

  /** Returns the class type of the value used by this {@code AppOption} instance. */
  public Class<?> getValueType() {
    return valueType;
  }

  /**
   * Returns the initial value of the option as set by {@link #setInitialValue(Object)} or {@link #loadValue()}.
   */
  public Object getInitialValue() {
    return initialValue;
  }

  /**
   * Like {@link #setValue(Object)} it sets the specified value, but also resets the "modified" state.
   * @throws NullPointerException if {@code newValue} is {@code null}.
   */
  public void setInitialValue(Object newValue) {
    setValue(newValue);
    this.initialValue = this.value;
  }

  /** Returns the current value of the option. */
  public Object getValue() {
    return value;
  }

  /**
   * Convenience method for returning the current value of the option as {@code boolean}.
   *
   * @throws ClassCastException if the value is not compatible with the {@code boolean} type.
   */
  public boolean getBoolValue() {
    if (getValue() instanceof Number) {
      return ((Number) getValue()).intValue() != 0;
    } else {
      return (Boolean) getValue();
    }
  }

  /**
   * Convenience method for returning the current value of the option as {@code int}.
   *
   * @throws ClassCastException if the value is not compatible with the {@code int} type.
   */
  public int getIntValue() {
    if (getValue() instanceof Boolean) {
      return getBoolValue() ? 1 : 0;
    }
    return ((Number) getValue()).intValue();
  }

  /**
   * Convenience method for returning the current value of the option as {@code long}.
   *
   * @throws ClassCastException if the value is not compatible with the {@code long} type.
   */
  public long getLongValue() {
    if (getValue() instanceof Boolean) {
      return getBoolValue() ? 1L : 0L;
    }
    return ((Number) getValue()).longValue();
  }

  /**
   * Convenience method for returning the current value of the option as {@code float}.
   *
   * @throws ClassCastException if the value is not compatible with the {@code float} type.
   */
  public float getFloatValue() {
    if (getValue() instanceof Boolean) {
      return getBoolValue() ? 1.0f : 0.0f;
    }
    return ((Number) getValue()).floatValue();
  }

  /**
   * Convenience method for returning the current value of the option as {@code double}.
   *
   * @throws ClassCastException if the value is not compatible with the {@code double} type.
   */
  public double getDoubleValue() {
    if (getValue() instanceof Boolean) {
      return getBoolValue() ? 1.0 : 0.0;
    }
    return ((Number) getValue()).doubleValue();
  }

  /** Convenience method for returning the current value of the option as {@code String}. */
  public String getStringValue() {
    return (getValue() != null) ? getValue().toString() : null;
  }

  /**
   * Returns {@code true} if the option value can be returned as {@code boolean}, which includes all numeric types
   * and {@code boolean} itself.
   */
  public boolean isBoolValue() {
    return (getValue() instanceof Boolean) || isNumericValue();
  }

  /** Returns whether the option value can be returned as a number. */
  public boolean isNumericValue() {
    return getValue() instanceof Number;
  }

  /**
   * Sets the option to the specified value.
   * If the new value differs from the previous value then the "modified" flag is set as well.
   * @throws ClassCastException if {@code newValue} is not of a type assignable from {@link #getValueType()}.
   */
  public void setValue(Object newValue) {
    this.value = validate(newValue);
  }

  /**
   * Reverts to the initial value as set by {@link #setInitialValue(Object)} or {@link #loadValue()}.
   */
  public void revert() {
    this.value = this.initialValue;
  }

  /**
   * Returns whether the value has been modified after initialization.
   */
  public boolean isModified() {
    return (this.initialValue == null && this.value != null) ||
        (this.initialValue != null && !this.initialValue.equals(this.value));
  }

  /**
   * Loads option value from the associated {@code Preferences} instance, falls back to the default value if needed.
   *
   * @return The value fetched from the {@code Preferences}.
   * @throws ClassCastException If the value type of this instance is not supported by the {@code Preferences} instance.
   */
  public Object loadValue() throws ClassCastException {
    final Preferences prefs = getPrefs();
    if (Boolean.class.isAssignableFrom(valueType)) {
      if (prefs != null) {
        initialValue = prefs.getBoolean(getName(), (Boolean) getDefault());
      } else {
        initialValue = getDefault();
      }
    } else if (Integer.class.isAssignableFrom(valueType)) {
      if (prefs != null) {
        initialValue = prefs.getInt(getName(), (Integer) getDefault());
      } else {
        initialValue = getDefault();
      }
    } else if (Long.class.isAssignableFrom(valueType)) {
      if (prefs != null) {
        initialValue = prefs.getLong(getName(), (Long) getDefault());
      } else {
        initialValue = getDefault();
      }
    } else if (Float.class.isAssignableFrom(valueType)) {
      if (prefs != null) {
        initialValue = prefs.getFloat(getName(), (Float) getDefault());
      } else {
        initialValue = getDefault();
      }
    } else if (Double.class.isAssignableFrom(valueType)) {
      if (prefs != null) {
        initialValue = prefs.getDouble(getName(), (Double) getDefault());
      } else {
        initialValue = getDefault();
      }
    } else if (String.class.isAssignableFrom(valueType)) {
      final String def = (getDefault() != null) ? getDefault().toString() : null;
      if (prefs != null) {
        initialValue = prefs.get(getName(), def);
      } else {
        initialValue = def;
      }
    } else {
      throw new ClassCastException("Unsupported value type: " + initialValue.getClass().getSimpleName());
    }
    this.value = this.initialValue;

    return initialValue;
  }

  /**
   * Saves current option value to the associated {@code Preferences} instance and resets the "modified" flag.
   *
   * <p>
   * Note: If the current value is {@code null} then the associated option is removed from the {@code Preferences}.
   * </p>
   *
   * @throws ClassCastException If the value type of this instance is not supported by the {@code Preferences} instance.
   */
  public void storeValue() throws ClassCastException {
    if (getPrefs() != null) {
      if (value == null) {
        getPrefs().remove(getName());
      } else if (Boolean.class.isAssignableFrom(valueType)) {
        Objects.requireNonNull(getPrefs()).putBoolean(getName(), (Boolean) value);
      } else if (Integer.class.isAssignableFrom(valueType)) {
        Objects.requireNonNull(getPrefs()).putInt(getName(), (Integer) value);
      } else if (Long.class.isAssignableFrom(valueType)) {
        Objects.requireNonNull(getPrefs()).putLong(getName(), (Long) value);
      } else if (Float.class.isAssignableFrom(valueType)) {
        Objects.requireNonNull(getPrefs()).putFloat(getName(), (Float) value);
      } else if (Double.class.isAssignableFrom(valueType)) {
        Objects.requireNonNull(getPrefs()).putDouble(getName(), (Double) value);
      } else if (String.class.isAssignableFrom(valueType)) {
        Objects.requireNonNull(getPrefs()).put(getName(), value.toString());
      } else {
        throw new ClassCastException("Unsupported value type: " + value.getClass().getSimpleName());
      }
    }
    this.initialValue = this.value;
  }

  @Override
  public String toString() {
    return String.format("%s: %s (%s)", name, value, initialValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defValue, initialValue, label, name, prefsNode, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AppOption other = (AppOption) obj;
    return Objects.equals(defValue, other.defValue) && Objects.equals(initialValue, other.initialValue)
        && Objects.equals(label, other.label) && Objects.equals(name, other.name)
        && Objects.equals(prefsNode, other.prefsNode) && Objects.equals(value, other.value);
  }

  /** Used internally to ensure that the specified value is of the same type as {@link #getDefault()}. */
  private Object validate(Object value) throws ClassCastException {
    value = validator.apply(value);
    if (value == null && Objects.equals(validateType(valueType), valueType) ||
        valueType.isAssignableFrom(validateType(value.getClass()))) {
      // pass
      return value;
    }

    // fail
    final String t = (value != null) ? value.getClass().getSimpleName() : "(null)";
    throw new ClassCastException("Unsupported value type: " + t);
  }
}
