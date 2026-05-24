// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.WindowConstants;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.infinity.AppOption;
import org.infinity.NearInfinity;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.gui.menu.LogLevel;
import org.infinity.gui.menu.OptionsMenuItem;
import org.infinity.gui.menu.OptionsMenuItem.CharsetInfo;
import org.infinity.gui.menu.OverrideMode;
import org.infinity.gui.menu.ResRefMode;
import org.infinity.gui.menu.ViewMode;
import org.infinity.gui.options.OptionBase;
import org.infinity.gui.options.OptionCategory;
import org.infinity.gui.options.OptionCheckBox;
import org.infinity.gui.options.OptionContainerBase;
import org.infinity.gui.options.OptionElementBase;
import org.infinity.gui.options.OptionGroup;
import org.infinity.gui.options.OptionGroupBox;
import org.infinity.gui.options.OptionPathBox;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.Platform;
import org.infinity.util.Weidu;
import org.infinity.util.io.FileNameFilter;

/**
 * This modal dialog provides access to application-wide options and preferences
 * that were formerly presented as individual entries in the "Options" menu.
 */
public class PreferencesDialog extends JDialog {
  /**
   * Blacklisted L&F themes: These themes should not be listed in the Preferences dialog.
   * <p>
   * List contains the fully qualified class names of L&F themes.
   * </p>
   */
  private static final List<String> LOOK_AND_FEEL_BLACKLIST = Arrays.asList("javax.swing.plaf.nimbus.NimbusLookAndFeel");

  /** Definition of category names. */
  public enum Category {
    DEFAULT(""),
    GENERAL_OPTIONS("General"),
    RESOURCES("Resources"),
    TABLE_COLUMNS("Table Columns"),
    SCRIPT_COMPILER("Script Compiler"),
    TEXT_EDITOR("Text Editor"),
    BCS_AND_BAF("BCS and BAF"),
    MISC_RESOURCE_TYPES("Misc. Resource Types"),
    DIALOG_TREE_VIEWER("Dialog Tree Viewer"),
    VISUAL_OPTIONS("GUI and Text");

    private final String label;

    Category(String label) {
      this.label = label;
    }

    /** Textual label of the category. */
    public String getLabel() {
      return label;
    }

    @Override
    public String toString() {
      return getLabel();
    }
  }

  /** Root element of the options definition tree. */
  private final OptionCategory optionRoot = OptionCategory.createDefault(
      OptionCategory.create(Category.GENERAL_OPTIONS,
          OptionCategory.create(Category.RESOURCES,
              OptionGroup.createDefault(
                  OptionCheckBox.create(AppOption.IGNORE_READ_ERRORS.getName(), AppOption.IGNORE_READ_ERRORS.getLabel(),
                      "With this option enabled Near Infinity shows a message box when a resource cannot be read. "
                          + "Otherwise, a message is printed to the status bar and debug console.",
                      AppOption.IGNORE_READ_ERRORS),
                  OptionCheckBox.create(AppOption.SHOW_UNKNOWN_RESOURCES.getName(), AppOption.SHOW_UNKNOWN_RESOURCES.getLabel(),
                      "With this option enabled Near Infinity shows unknown or unsupported resources in their own type "
                          + "folder in the resource tree.",
                      AppOption.SHOW_UNKNOWN_RESOURCES),
                  OptionCheckBox.create(AppOption.SHOW_TREE_SEARCH_NAMES.getName(), AppOption.SHOW_TREE_SEARCH_NAMES.getLabel(),
                      "With this option enabled Near Infinity shows the search name of resources in the resource tree "
                          + "in parentheses if available, such as creature, item or spell names.",
                      AppOption.SHOW_TREE_SEARCH_NAMES),
                  OptionCheckBox.create(AppOption.SHOW_RESOURCE_TREE_ICONS.getName(), AppOption.SHOW_RESOURCE_TREE_ICONS.getLabel(),
                      "With this option enabled Near Infinity shows icons alongside names in the resource tree for BMP, ITM "
                          + "and SPL resources."
                          + "<p><strong>Caution:</strong> Enabling this option may result in increased memory usage and "
                          + "noticeable lags on slower systems.</p>",
                          AppOption.SHOW_RESOURCE_TREE_ICONS),
                  OptionCheckBox.create(AppOption.SHOW_RESOURCE_LIST_ICONS.getName(), AppOption.SHOW_RESOURCE_LIST_ICONS.getLabel(),
                      "With this option enabled Near Infinity shows icons alongside names in resource selection lists and "
                          + "tables for BMP, ITM and SPL resources as well as portrait thumbnails for characters in GAM resources."
                          + "<p><strong>Caution:</strong> Enabling this option may result in increased memory usage and "
                          + "noticeable lags on slower systems.</p>",
                      AppOption.SHOW_RESOURCE_LIST_ICONS),
                  OptionCheckBox.create(AppOption.HIGHLIGHT_OVERRIDDEN.getName(), AppOption.HIGHLIGHT_OVERRIDDEN.getLabel(),
                      "If checked, files that are listed in the <em>chitin.key</em> and are also available in the "
                          + "Override folder, will be shown in <strong>bold</strong> in the resource tree."
                          + "<p><strong>Note:</strong> This setting has no effect if overridden files are only shown in "
                          + "the override folder (see <code>" + Category.VISUAL_OPTIONS.getLabel() + "</code> &gt; <code>"
                          + AppOption.SHOW_OVERRIDES_IN.getLabel() + "</code>.)</p>",
                      AppOption.HIGHLIGHT_OVERRIDDEN),
                  OptionCheckBox.create(AppOption.OVERRIDE_SORT_BY_TYPE.getName(), AppOption.OVERRIDE_SORT_BY_TYPE.getLabel(),
                      "If checked, files that are listed in the Override folder of the resource tree will be sorted by file "
                          + "extension instead of file name."
                          + "<p><strong>Note:</strong> This setting has no effect if overridden files are only shown in "
                          + "the respective type folders (see <code>" + Category.VISUAL_OPTIONS.getLabel() + "</code> &gt; <code>"
                          + AppOption.SHOW_OVERRIDES_IN.getLabel() + "</code>.)</p>",
                      AppOption.OVERRIDE_SORT_BY_TYPE),
                  OptionCheckBox.create(AppOption.CACHE_OVERRIDE.getName(), AppOption.CACHE_OVERRIDE.getLabel(),
                      "With this option enabled Near Infinity checks whether resources have been overridden every time a "
                      + "resource has been requested."
                      + "<p>If this option is disabled then Near Infinity will only check for the existence of overridden "
                      + "resources after opening a new game or using the command \"Refresh Tree\" (Shortcut: <code>F5</code>).</p>",
                      AppOption.CACHE_OVERRIDE),
                  OptionCheckBox.create(AppOption.LOWERCASED_EXPORT.getName(), AppOption.LOWERCASED_EXPORT.getLabel(),
                      "With this option enabled biffed resources will use lowercased filenames for export or save operations."
                      + "<p>This option is mostly useful on Linux where filenames are generally in lower case.</p>",
                      AppOption.LOWERCASED_EXPORT),
                  OptionCheckBox.create(AppOption.IDS_SYMBOL_DISPLAY.getName(), AppOption.IDS_SYMBOL_DISPLAY.getLabel(),
                      "With this option enabled IDS entries with multiple symbolic names will display the last available "
                      + "symbol in scripts, effects, and other resources."
                      + "<p>Disable this option to show the first available symbolic name instead.</p>"
                      + "<p>You may need to refresh the game (Shortcut: <code>F5</code>) for the option to take effect.</p>",
                      AppOption.IDS_SYMBOL_DISPLAY),
                  OptionCheckBox.create(AppOption.OPEN_RESOURCE_TEXT_MENU.getName(), AppOption.OPEN_RESOURCE_TEXT_MENU.getLabel(),
                      "With this option enabled text resources will provide an \"Open resource\" entry in the context menu "
                      + "that allows you to open a game resource matching the current text selection or the word under "
                      + "the cursor position.",
                      AppOption.OPEN_RESOURCE_TEXT_MENU),
                  OptionCheckBox.create(AppOption.OPEN_IDS_TEXT_MENU.getName(), AppOption.OPEN_IDS_TEXT_MENU.getLabel(),
                      "With this option enabled text resources will provide an \"Open IDS reference\" entry in the context "
                      + "menu that allows you to open an IDS resource and highlight the entry matching the current text "
                      + "selection or the word under the cursor position.",
                      AppOption.OPEN_IDS_TEXT_MENU),
                  OptionCheckBox.create(AppOption.OPEN_STRREF_TEXT_MENU.getName(), AppOption.OPEN_STRREF_TEXT_MENU.getLabel(),
                      "With this option enabled text resources will provide an \"Open strref\" entry in the context menu "
                      + "that allows you to opens a string reference matching the current text selection or the word under "
                      + "the cursor position.",
                      AppOption.OPEN_STRREF_TEXT_MENU)
              ),
              OptionGroup.create("WeiDU",
                  OptionPathBox.fileCreate(AppOption.WEIDU_PATH.getName(), AppOption.WEIDU_PATH.getLabel(),
                      "Choose a WeiDU binary to enable WeiDU-specific operations. Near Infinity will also check the "
                      + "current game directory as well as <b><code>./weidu_external/tools/weidu</code></b> and various "
                      + "system-specific subfolders if the WeiDU binary has not been defined or does not exist.", null,
                      new FileNameFilter("WeiDU executables", Weidu.WEIDU_NAME + ".*" + Pattern.quote(Platform.EXECUTABLE_EXT)),
                      true, AppOption.WEIDU_PATH)
                  .setOnChanged(this::weiduPathOnChanged)
              )
          ),
          OptionCategory.create(Category.TABLE_COLUMNS,
              OptionGroup.createDefault(
                  OptionCheckBox.create(AppOption.TABLE_SHOW_OFFSETS.getName(), AppOption.TABLE_SHOW_OFFSETS.getLabel(),
                      "If enabled, absolute field offsets are shown in a separate \"Offset\" table column in the "
                          + "Edit view of structured resources.",
                      AppOption.TABLE_SHOW_OFFSETS)
                  .setOnCreated(this::showOffsetsOnCreated).setOnAction(this::showOffsetsOnAction),
                  OptionCheckBox.create(AppOption.TABLE_SHOW_OFFSETS_RELATIVE.getName(), AppOption.TABLE_SHOW_OFFSETS_RELATIVE.getLabel(),
                      "If enabled, offsets relative to the parent structure are additionally shown in parentheses "
                          + "in the \"Offset\" table column for entries in substructures.",
                      AppOption.TABLE_SHOW_OFFSETS_RELATIVE),
                  OptionCheckBox.create(AppOption.TABLE_SHOW_SIZE.getName(), AppOption.TABLE_SHOW_SIZE.getLabel(),
                      "If enabled, field sizes in bytes are shown in a separate \"Size\" table column in the "
                          + "Edit view of structured resources.",
                      AppOption.TABLE_SHOW_SIZE)
                      .setOnCreated(this::showSizeOnCreated).setOnAction(this::showSizeOnAction),
                  OptionCheckBox.create(AppOption.TABLE_SHOW_SIZE_HEX.getName(), AppOption.TABLE_SHOW_SIZE_HEX.getLabel(),
                      "If enabled, field sizes are shown in hexadecimal notation.",
                      AppOption.TABLE_SHOW_SIZE_HEX)
              )
          ),
          OptionGroup.createDefault(
              OptionCheckBox.create(AppOption.BACKUP_ON_SAVE.getName(), AppOption.BACKUP_ON_SAVE.getLabel(),
                  "Enable this option to automatically create a backup of the resource you are saving.",
                  AppOption.BACKUP_ON_SAVE),
              OptionCheckBox.create(AppOption.LAUNCH_GAME_ALLOWED.getName(), AppOption.LAUNCH_GAME_ALLOWED.getLabel(),
                  "Enable this option to be able to launch the game executable associated with the current game "
                      + "from within Near Infinity.",
                  AppOption.LAUNCH_GAME_ALLOWED)
              .setOnCreated(this::launchGameAllowedOnCreated),
              OptionCheckBox.create(AppOption.KEEP_VIEW_ON_COPY.getName(), AppOption.KEEP_VIEW_ON_COPY.getLabel(),
                  "With this option enabled the resource tree will keep the original resource selected after performing "
                      + "the \"Add Copy Of\" operation on the resource. Otherwise, the new resource is selected instead.",
                  AppOption.KEEP_VIEW_ON_COPY),
              OptionCheckBox.create(AppOption.SHOW_STRREFS.getName(), AppOption.SHOW_STRREFS.getLabel(),
                  "With this option enabled text strings will always be shown together with their associated string "
                      + "reference numbers.",
                  AppOption.SHOW_STRREFS),
              OptionCheckBox.create(AppOption.SHOW_COLORED_STRUCTURES.getName(), AppOption.SHOW_COLORED_STRUCTURES.getLabel(),
                  "With this option enabled structured resources will display substructure entries and their associated "
                      + "offset/size fields in different colors in the \"Edit\" tab.",
                  AppOption.SHOW_COLORED_STRUCTURES),
              OptionCheckBox.create(AppOption.SHOW_HEX_COLORED.getName(), AppOption.SHOW_HEX_COLORED.getLabel(),
                  "With this option enabled structured resources will display substructures in different colors "
                      + "in the \"Raw\" tab.",
                  AppOption.SHOW_HEX_COLORED),
              OptionCheckBox.create(AppOption.SHOW_SYS_INFO.getName(), AppOption.SHOW_SYS_INFO.getLabel(),
                  "With this option enabled Near Infinity will show detailed information about current version, "
                      + "Java Runtime, and available memory on the main panel while no game resource is opened "
                      + "in the main panel.",
                  AppOption.SHOW_SYS_INFO),
              OptionCheckBox.create(AppOption.SHOW_MEM_STATUS.getName(), AppOption.SHOW_MEM_STATUS.getLabel(),
                  "With this option enabled the current memory usage will be shown in the status bar of the Near Infinity "
                      + "main window. This value is updated in regular intervals."
                      + "<p><strong>Note:</strong> Changing this option requires a restart of Near Infinity to be effective.</p>",
                  AppOption.SHOW_MEM_STATUS),
              OptionCheckBox.create(AppOption.OPEN_BOOKMARKS_PROMPT.getName(), AppOption.OPEN_BOOKMARKS_PROMPT.getLabel(),
                  "With this option enabled a confirmation dialog is shown whenever you try to load a bookmarked game."
                      + "<p><strong>Note:</strong> This option can also be changed in the confirmation dialog of the "
                      + "bookmarked games themselves</p>",
                  AppOption.OPEN_BOOKMARKS_PROMPT),
              OptionCheckBox.create(AppOption.OPEN_STRINGTABLE_WARNING.getName(), AppOption.OPEN_STRINGTABLE_WARNING.getLabel(),
                  "With this option enabled a warning dialog about making a backup of the \"dialog.tlk\" is shown when "
                      + "you open the String Table Editor.",
                  AppOption.OPEN_STRINGTABLE_WARNING),
              OptionCheckBox.create(AppOption.REMEMBER_CHILD_FRAME_RECT.getName(), AppOption.REMEMBER_CHILD_FRAME_RECT.getLabel(),
                  "With this option enabled Near Infinity remembers current size and position of child windows on the screen.<br/>"
                      + "This information is only remembered across the current session and will be discarded whenever "
                      + "Near Infinity is closed.",
                  AppOption.REMEMBER_CHILD_FRAME_RECT),
              OptionCheckBox.create(AppOption.SHOW_CREATURES_ON_PANEL.getName(), AppOption.SHOW_CREATURES_ON_PANEL.getLabel(),
                  "With this option enabled creatures from the currently opened game will progressively populate the "
                      + "main panel and perform all kinds of random actions. This option is primarily meant as a gimmick. ;)"
                      + "<p><strong>Note:</strong> Changing this option requires to open a new game or use the command "
                      + "\"Refresh Tree\" (Shortcut: <code>F5</code>)</p>",
                  AppOption.SHOW_CREATURES_ON_PANEL)
          )
      ),
      OptionCategory.create(Category.SCRIPT_COMPILER,
          OptionGroup.createDefault(
              OptionCheckBox.create(AppOption.AUTO_CHECK_BCS.getName(), AppOption.AUTO_CHECK_BCS.getLabel(),
                  "With this option enabled Near Infinity will automatically scan scripts for syntax errors.",
                  AppOption.AUTO_CHECK_BCS),
              OptionCheckBox.create(AppOption.MORE_COMPILER_WARNINGS.getName(), AppOption.MORE_COMPILER_WARNINGS.getLabel(),
                  "With this option enabled the script compiler will generate an additional set of less severe warning messages.",
                  AppOption.MORE_COMPILER_WARNINGS),
              OptionCheckBox.create(AppOption.AUTO_GEN_BCS_COMMENTS.getName(), AppOption.AUTO_GEN_BCS_COMMENTS.getLabel(),
                  "With this option enabled the script compiler will add helpful comments to script actions and triggers "
                      + "to opened script sources.",
                  AppOption.AUTO_GEN_BCS_COMMENTS)
          )
      ),
      OptionCategory.create(Category.TEXT_EDITOR,
          OptionCategory.create(Category.BCS_AND_BAF,
              OptionGroup.createDefault(
                  OptionGroupBox.create(AppOption.BCS_INDENT.getName(), AppOption.BCS_INDENT.getLabel(),
                      "Choose what kind of indentation should be used to format BCS script sources when option "
                      + "\"Enable Automatic Indentation\" is enabled.", 0,
                      OptionsMenuItem.getIndentations().toArray(new OptionsMenuItem.IndentInfo[0]),
                      AppOption.BCS_INDENT),
                  OptionGroupBox.create(AppOption.BCS_COLOR_SCHEME.getName(), AppOption.BCS_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for BCS script sources.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.<br/>"
                          + "<strong>BCS Light:</strong> A color scheme that is based on the WeiDU Syntax Highlighter "
                          + "for Notepad++.<br/>"
                          + "<strong>BCS Dark:</strong> A dark color scheme that is based on the WeiDU Syntax Highlighter "
                          + "for Notepad++.</p>",
                      0, OptionsMenuItem.getBcsColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.BCS_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.BCS_SYNTAX_HIGHLIGHTING.getName(), AppOption.BCS_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for BCS script sources.", AppOption.BCS_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::bcsSyntaxHighlightingOnCreated).setOnAction(this::bcsSyntaxHighlightingOnAction),
                  OptionCheckBox.create(AppOption.BCS_CODE_FOLDING.getName(), AppOption.BCS_CODE_FOLDING.getLabel(),
                      "Enables code folding for BCS script sources.", AppOption.BCS_CODE_FOLDING),
                  OptionCheckBox.create(AppOption.BCS_AUTO_INDENT.getName(), AppOption.BCS_AUTO_INDENT.getLabel(),
                      "With this option enabled BCS script sources are automatically indented to improve readability.",
                      AppOption.BCS_AUTO_INDENT)
              )
          ),
          OptionCategory.create(Category.MISC_RESOURCE_TYPES,
              OptionGroup.create("2DA",
                  OptionGroupBox.create(AppOption.AUTO_ALIGN_2DA.getName(), AppOption.AUTO_ALIGN_2DA.getLabel(),
                      "Choose how to to automatically align 2DA table columns when the resource is opened.<p>"
                          + "<strong>Disabled:</strong> Table data is not modified.<br/>"
                          + "<strong>Compact:</strong> Column widths are calculated individually.<br/>"
                          + "<strong>Uniform:</strong> Column widths are calculated evenly (comparable to Weidu's PRETTY_PRINT_2DA.)"
                          + "<p><strong>Note:</strong> Formatting is discarded when the resource is closed unless the changes "
                          + "are explicitly saved.</p>",
                      0, OptionsMenuItem.AutoAlign2da.values(), AppOption.AUTO_ALIGN_2DA)
              ),
              OptionGroup.create("GLSL",
                  OptionGroupBox.create(AppOption.GLSL_COLOR_SCHEME.getName(), AppOption.GLSL_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for GLSL resources.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.</p>",
                      0, OptionsMenuItem.getColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.GLSL_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.GLSL_SYNTAX_HIGHLIGHTING.getName(), AppOption.GLSL_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for GLSL resources.", AppOption.GLSL_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::glslSyntaxHighlightingOnCreated).setOnAction(this::glslSyntaxHighlightingOnAction),
                  OptionCheckBox.create(AppOption.GLSL_CODE_FOLDING.getName(), AppOption.GLSL_CODE_FOLDING.getLabel(),
                      "Enables code folding for GLSL resources.", AppOption.GLSL_CODE_FOLDING)
              ),
              OptionGroup.create("INI",
                  OptionGroupBox.create(AppOption.INI_COLOR_SCHEME.getName(), AppOption.INI_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for INI resources.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.</p>",
                      0, OptionsMenuItem.getColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.INI_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.INI_SYNTAX_HIGHLIGHTING.getName(), AppOption.INI_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for INI resources.", AppOption.INI_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::iniSyntaxHighlightingOnCreated).setOnAction(this::iniSyntaxHighlightingOnAction)
              ),
              OptionGroup.create("LUA",
                  OptionGroupBox.create(AppOption.LUA_COLOR_SCHEME.getName(), AppOption.LUA_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for LUA resources.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.</p>",
                      0, OptionsMenuItem.getColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.LUA_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.LUA_SYNTAX_HIGHLIGHTING.getName(), AppOption.LUA_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for LUA resources.", AppOption.LUA_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::luaSyntaxHighlightingOnCreated).setOnAction(this::luaSyntaxHighlightingOnAction)
              ),
              OptionGroup.create("MENU",
                  OptionGroupBox.create(AppOption.MENU_COLOR_SCHEME.getName(), AppOption.MENU_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for MENU resources.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.</p>",
                      0, OptionsMenuItem.getColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.MENU_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.MENU_SYNTAX_HIGHLIGHTING.getName(), AppOption.MENU_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for MENU resources.", AppOption.MENU_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::menuSyntaxHighlightingOnCreated).setOnAction(this::menuSyntaxHighlightingOnAction)
              ),
              OptionGroup.create("SQL",
                  OptionGroupBox.create(AppOption.SQL_COLOR_SCHEME.getName(), AppOption.SQL_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for SQL resources.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.</p>",
                      0, OptionsMenuItem.getColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.SQL_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.SQL_SYNTAX_HIGHLIGHTING.getName(), AppOption.SQL_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for SQL resources.", AppOption.SQL_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::sqlSyntaxHighlightingOnCreated).setOnAction(this::sqlSyntaxHighlightingOnAction)
              ),
              OptionGroup.create("Dialog.tlk",
                  OptionGroupBox.create(AppOption.TLK_COLOR_SCHEME.getName(), AppOption.TLK_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for Dialog.tlk strings.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.</p>",
                      0, OptionsMenuItem.getColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.TLK_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.TLK_SYNTAX_HIGHLIGHTING.getName(), AppOption.TLK_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for Dialog.tlk strings.", AppOption.TLK_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::tlkSyntaxHighlightingOnCreated).setOnAction(this::tlkSyntaxHighlightingOnAction)
              ),
              OptionGroup.create("WeiDU.log",
                  OptionGroupBox.create(AppOption.WEIDU_COLOR_SCHEME.getName(), AppOption.WEIDU_COLOR_SCHEME.getLabel(),
                      "Select a color scheme for WeiDU.log content.<p>"
                          + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                          + "<strong>Dark:</strong> A dark scheme based off of Notepad++'s Obsidian theme.<br/>"
                          + "<strong>Druid:</strong> A dark green color scheme.<br/>"
                          + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                          + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                          + "<strong>Monokai:</strong> A dark color scheme inspired by \"Monokai\".<br/>"
                          + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.</p>",
                      0, OptionsMenuItem.getColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                      AppOption.WEIDU_COLOR_SCHEME),
                  OptionCheckBox.create(AppOption.WEIDU_SYNTAX_HIGHLIGHTING.getName(), AppOption.WEIDU_SYNTAX_HIGHLIGHTING.getLabel(),
                      "Enables syntax highlighting for WeiDU.log content.", AppOption.WEIDU_SYNTAX_HIGHLIGHTING)
                  .setOnCreated(this::weiduSyntaxHighlightingOnCreated).setOnAction(this::weiduSyntaxHighlightingOnAction)
              )
          ),
          OptionGroup.createDefault(
              OptionCheckBox.create(AppOption.TEXT_SHOW_WHITE_SPACE.getName(), AppOption.TEXT_SHOW_WHITE_SPACE.getLabel(),
                  "Enable this option to visualize spaces and tabs in the text editor.", AppOption.TEXT_SHOW_WHITE_SPACE),
              OptionCheckBox.create(AppOption.TEXT_SHOW_EOL.getName(), AppOption.TEXT_SHOW_EOL.getLabel(),
                  "Enable this option to visualize line breaks in the text editor.", AppOption.TEXT_SHOW_EOL),
              OptionCheckBox.create(AppOption.TEXT_SHOW_CURRENT_LINE.getName(), AppOption.TEXT_SHOW_CURRENT_LINE.getLabel(),
                  "Enable this option to highlight the current line with a different background color in the text editor.",
                  AppOption.TEXT_SHOW_CURRENT_LINE),
              OptionCheckBox.create(AppOption.TEXT_SHOW_LINE_NUMBERS.getName(), AppOption.TEXT_SHOW_LINE_NUMBERS.getLabel(),
                  "Enable this option to show line numbers on the left side of the text editor.",
                  AppOption.TEXT_SHOW_LINE_NUMBERS)
          ),
          OptionGroup.create("Tab Settings",
              OptionGroupBox.create(AppOption.TEXT_TAB_SIZE.getName(), AppOption.TEXT_TAB_SIZE.getLabel(),
                  "Choose the maximum number of characters that tabs will be expanded to in text resources.", 1,
                  new String[0], AppOption.TEXT_TAB_SIZE)
              .setOnInit(this::textTabSizeOnInit),
              OptionCheckBox.create(AppOption.TEXT_TABS_EMULATED.getName(), AppOption.TEXT_TABS_EMULATED.getLabel(),
                  "Enable this option to automatically convert tab characters into the required number of spaces "
                      + "in text resources.",
                  AppOption.TEXT_TABS_EMULATED)          )
      ),
      OptionCategory.create(Category.DIALOG_TREE_VIEWER,
          OptionGroup.createDefault(
              OptionGroupBox.create(AppOption.DLG_COLOR_SCHEME.getName(), AppOption.DLG_COLOR_SCHEME.getLabel(),
                  "Select a color scheme for display of dialog triggers and actions.<p>"
                      + "<strong>Default:</strong> A general-purpose default color scheme.<br/>"
                      + "<strong>Eclipse:</strong> Mimics the default color scheme of the Eclipse IDE.<br/>"
                      + "<strong>IntelliJ IDEA:</strong> Mimics the default color scheme of IntelliJ IDEA.<br/>"
                      + "<strong>Visual Studio:</strong> Mimics the default color scheme of Microsoft Visual Studio.<br/>"
                      + "<strong>BCS Light:</strong> A color scheme that is based on the WeiDU Syntax Highlighter for "
                      + "Notepad++.<br/>"
                      + "<strong>BCS Dark:</strong> A dark color scheme that is based on the WeiDU Syntax Highlighter "
                      + "for Notepad++.</p>",
                  0, OptionsMenuItem.getDlgColorSchemes().toArray(new OptionsMenuItem.ColorScheme[0]),
                  AppOption.DLG_COLOR_SCHEME),
              OptionCheckBox.create(AppOption.DLG_SYNTAX_HIGHLIGHTING.getName(), AppOption.DLG_SYNTAX_HIGHLIGHTING.getLabel(),
                  "Enables syntax highlighting for dialog triggers and actions.", AppOption.DLG_SYNTAX_HIGHLIGHTING)
              .setOnCreated(this::dlgSyntaxHighlightingOnCreated).setOnAction(this::dlgSyntaxHighlightingOnAction),
              OptionCheckBox.create(AppOption.DLG_SHOW_ICONS.getName(), AppOption.DLG_SHOW_ICONS.getLabel(),
                  "Enable this option to show small icons in front of dialog states and responses in the dialog tree view.",
                  AppOption.DLG_SHOW_ICONS),
              OptionCheckBox.create(AppOption.DLG_SORT_STATES_BY_WEIGHT.getName(), AppOption.DLG_SORT_STATES_BY_WEIGHT.getLabel(),
                  "Enable this option to sort top-level dialog states by their internal processing order.",
                  AppOption.DLG_SORT_STATES_BY_WEIGHT),
              OptionCheckBox.create(AppOption.DLG_ALWAYS_SHOW_STATE_0.getName(), AppOption.DLG_ALWAYS_SHOW_STATE_0.getLabel(),
                  "Enable this option to always show the first state of the dialog as a top-level state (not recommended.)",
                  AppOption.DLG_ALWAYS_SHOW_STATE_0),
              OptionCheckBox.create(AppOption.DLG_COLORIZE_OTHER_DIALOGS.getName(), AppOption.DLG_COLORIZE_OTHER_DIALOGS.getLabel(),
                  "Enable this option to colorize external dialog content.", AppOption.DLG_COLORIZE_OTHER_DIALOGS),
              OptionCheckBox.create(AppOption.DLG_BREAK_CYCLES.getName(), AppOption.DLG_BREAK_CYCLES.getLabel(),
                  "Enable this option to break duplicate or cyclic references and link to the first available "
                      + "reference instead.",
                  AppOption.DLG_BREAK_CYCLES),
              OptionCheckBox.create(AppOption.DLG_COLORIZE_RESPONSES.getName(), AppOption.DLG_COLORIZE_RESPONSES.getLabel(),
                  "Enable this option to use different colors for response entries in the dialog tree view.",
                  AppOption.DLG_COLORIZE_RESPONSES),
              OptionCheckBox.create(AppOption.DLG_SHOW_TECH_INFO.getName(), AppOption.DLG_SHOW_TECH_INFO.getLabel(),
                  "Enable this option to display absolute state and response indices directly in the dialog tree view.",
                  AppOption.DLG_SHOW_TECH_INFO)
          )
      ),
      OptionCategory.create(Category.VISUAL_OPTIONS,
          OptionGroup.createDefault(
              OptionGroupBox.create(AppOption.APP_LOG_LEVEL.getName(), AppOption.APP_LOG_LEVEL.getLabel(),
                  "Specify the minimum severity level for log messages to be shown in the debug console."
                      + "<p><strong>" + LogLevel.TRACE + ":</strong> <em>(Not recommended)</em> Log messages for all "
                      + "unexpected and many expected results which is only useful for developers.<br/>"
                      + "<p><strong>" + LogLevel.DEBUG + ":</strong> Log messages for diagnostic purposes which can be "
                      + "relevant for troubleshooting issues with the application.<br/>"
                      + "<p><strong>" + LogLevel.INFO + ":</strong> Log helpful information as well as warnings and "
                      + "errors.<br/>"
                      + "<p><strong>" + LogLevel.WARN + ":</strong> Log only warnings and errors. Choose this option to "
                      + "reduce the amount of messages without losing relevant information.<br/>"
                      + "<p><strong>" + LogLevel.ERROR + ":</strong> Log only error messages.<br/>"
                      + "<p><strong>" + LogLevel.OFF + ":</strong> This option disables logging completely.<br/>",
                  LogLevel.INFO.ordinal(), LogLevel.values(), AppOption.APP_LOG_LEVEL),
              OptionGroupBox.create(AppOption.SHOW_RES_REF.getName(), AppOption.SHOW_RES_REF.getLabel(),
                  "Choose how resources should be displayed in resource lists.", ResRefMode.RefName.ordinal(),
                  ResRefMode.values(), AppOption.SHOW_RES_REF),
              OptionGroupBox.create(AppOption.SHOW_OVERRIDES_IN.getName(), AppOption.SHOW_OVERRIDES_IN.getLabel(),
                  "Choose how game resources should be displayed in the resource tree."
                      + "<p><strong>" + OverrideMode.InTree + ":</strong> All resources are shown in the tree folders of "
                      + "corresponding type.<br/>"
                      + "<strong>" + OverrideMode.InOverride + ":</strong> All resources in the override folder are "
                      + "shown in the \"Override\" tree folder.<br/>"
                      + "<strong>" + OverrideMode.Split + ":</strong> All resources indexed by \"chitin.key\" are shown "
                      + "in the tree folders of corresponding type. All other resources are shown in the \"Override\" "
                      + "tree folder.</p>",
                  OverrideMode.Split.ordinal(), OverrideMode.values(), AppOption.SHOW_OVERRIDES_IN),
              OptionGroupBox.create(AppOption.VIEW_OR_EDIT_SHOWN.getName(), AppOption.VIEW_OR_EDIT_SHOWN.getLabel(),
                  "Choose the initial display mode for opening structured resources and their substructures."
                      + "<p><strong>View:</strong> Opened resources are initially shown in the \"View\" tab.<br/>"
                      + "<strong>Edit:</strong> Opened resources are initially shown in the \"Edit\" tab.</p>",
                  ViewMode.Edit.ordinal(), ViewMode.values(), AppOption.VIEW_OR_EDIT_SHOWN),
              OptionGroupBox.create(AppOption.GLOBAL_FONT_SIZE.getName(), AppOption.GLOBAL_FONT_SIZE.getLabel(),
                  "Choose a global scale factor for font sizes. This option can be used to compensate text size "
                      + "on scaled desktops."
                      + "<p><strong>Note:</strong> This option is most effective when set to the desired scale factor, "
                      + "in combination with <code>" + Category.VISUAL_OPTIONS.getLabel() + "</code> &gt; "
                      + "<code>Override UI Scaling</code> enabled, and <code>" + AppOption.UI_SCALE_FACTOR.getLabel()
                      + "</code> set to 100%.</p>",
                  0, new DataItem<?>[0], AppOption.GLOBAL_FONT_SIZE)
              .setOnInit(this::globalFontSizeOnInit).setOnAccept(this::globalFontSizeOnAccept).setOnSelect(this::globalFontSizeOnSelect),
              OptionGroupBox.create(AppOption.TEXT_FONT.getName(), AppOption.TEXT_FONT.getLabel(),
                  "Choose a default text font for the display of text in game resources.", 0, new String[0],
                  AppOption.TEXT_FONT)
              .setOnInit(this::textFontOnInit).setOnCreated(this::textFontOnCreated).setOnAccept(this::textFontOnAccept)
              .setOnSelect(this::textFontOnSelect),
              OptionGroupBox.create(AppOption.TLK_CHARSET_TYPE.getName(), AppOption.TLK_CHARSET_TYPE.getLabel(),
                  "Select a character set for decoding text strings in Dialog.tlk.<p>"
                      + "<strong>Autodetect:</strong> Choose this if you are unsure. Near Infinity will determine the "
                      + "correct character set based on game type and heuristics. Select a specific character set only "
                      + "if you notice character decoding errors.<br/>"
                      + "<strong>UTF-8:</strong> Character set for the Enhanced Editions of Baldur's Gate, Icewind Dale "
                      + "and Planescape Torment (all languages.)<br/>"
                      + "<strong>Windows-1252:</strong> Character set for English and many other Romance and Germanic "
                      + "languages, such as Spanish, Portuguese, French, and German.<br/>"
                      + "<strong>Windows-1251:</strong> Character set that covers languages using the Cyrillic script, "
                      + "such as Russian, Ukrainian, Bulgarian, Serbian Cyrillic, and other languages.<br/>"
                      + "<strong>Windows-1250:</strong> Character set for Central European and Eastern European languages, "
                      + "such as Czech, Polish, Hungarian, and Slovene.<br/>"
                      + "<strong>Windows-31J:</strong> Character set for Japanese text.<br/>"
                      + "<strong>GBK:</strong> Character set for Simplified Chinese text.<br/>"
                      + "<strong>Big5-HKSCS:</strong> Character set for Cantonese and Traditional Chinese text "
                      + "(may not be fully compatible.)<br/>"
                      + "<strong>IBM-949:</strong> Character set for Korean text.</p>"
                      + "<p>The remaining character sets are mostly listed for completeness, but are also valid choices "
                      + "if the predefined entries don't produce the desired result.</p>",
                  0, new String[0], AppOption.TLK_CHARSET_TYPE)
              .setOnInit(this::tlkCharsetTypeOnInit).setOnAccept(this::tlkCharsetTypeOnAccept).setOnSelect(this::tlkCharsetTypeOnSelect),
              OptionGroupBox.create(AppOption.GAME_LANGUAGES.getName(), AppOption.GAME_LANGUAGES.getLabel(),
                  "Select the game language for <em>dialog.tlk</em> text and other language-specific strings of the game."
                      + "<p>Choose <strong>Autodetect</strong> to use the game language that is specified in the game's "
                      + "configuration file.</p>"
                      + "<p><strong>Note:</strong> This option is only available for the Enhanced Edition games.</p>",
                  0, new String[0], AppOption.GAME_LANGUAGES)
              .setOnInit(this::gameLanguagesOnInit).setOnCreated(this::gameLanguagesOnCreated).setOnAccept(this::gameLanguagesOnAccept)
          ),
          OptionGroup.create("Look and Feel",
              OptionGroupBox.create(AppOption.LOOK_AND_FEEL_CLASS.getName(), AppOption.LOOK_AND_FEEL_CLASS.getLabel(),
                  "Choose a Look & Feel theme for the GUI."
                      + "<p><strong>Metal</strong> is the default L&F theme and provides the most consistent user experience. "
                      + "It is available on all platforms.</p>"
                      + "<p>Look & Feel themes marked with an asterisk (*) are compatible with the \"Follow system color scheme\" "
                      + "option.</p>",
                  0, new DataItem<?>[0], AppOption.LOOK_AND_FEEL_CLASS)
              .setOnInit(this::lookAndFeelClassOnInit).setOnAccept(this::lookAndFeelClassOnAccept),
              OptionCheckBox.create(AppOption.LOOK_AND_FEEL_AUTO_MODE.getName(), AppOption.LOOK_AND_FEEL_AUTO_MODE.getLabel(),
                  "With this option enabled a light or dark mode theme will be automatically chosen based on the system's "
                      + "color scheme."
                      + "<p><strong>Note:</strong> Only Look & Feel themes marked with an asterisk (*) are compatible with "
                      + "this feature.</p>",
                  AppOption.LOOK_AND_FEEL_AUTO_MODE)
          ),
          OptionGroup.create("Override UI Scaling",
              OptionCheckBox.create(AppOption.UI_SCALE_ENABLED.getName(), AppOption.UI_SCALE_ENABLED.getLabel(),
                  "Enable this option to override the default desktop scaling of UI elements with the selected scale factor."
                      + "<p>It is most effective with <code>" + AppOption.UI_SCALE_FACTOR.getLabel() + "</code> set to 100%, "
                      + "in combination with <code>" + Category.VISUAL_OPTIONS.getLabel() + "</code> &gt; <code>"
                      + AppOption.GLOBAL_FONT_SIZE.getLabel() + "</code> set to the desired scale factor.</p>"
                      + "<p><strong>Note:</strong> This option is only available if Near Infinity is running on Java 9 "
                      + "or later.</p>",
                  AppOption.UI_SCALE_ENABLED)
              .setOnCreated(this::uiScaleEnabledOnCreated).setOnAction(this::uiScaleEnabledOnAction),
              OptionGroupBox.create(AppOption.UI_SCALE_FACTOR.getName(), AppOption.UI_SCALE_FACTOR.getLabel(),
                  "Defines the scale factor to use for resizing UI elements when this option is enabled."
                      + "<p>It is most effective with <code>" + AppOption.UI_SCALE_FACTOR.getLabel() + "</code> set to 100%, "
                      + "in combination with <code>" + Category.VISUAL_OPTIONS.getLabel() + "</code> &gt; <code>"
                      + AppOption.GLOBAL_FONT_SIZE.getLabel() + "</code> set to the desired scale factor.</p>"
                      + "<p><strong>Note:</strong> This option is only available if Near Infinity is running on Java 9 "
                      + "or later.</p>",
                  0, new String[0], AppOption.UI_SCALE_FACTOR)
              .setOnInit(this::uiScaleFactorOnInit).setOnCreated(this::uiScaleFactorOnCreated)
              .setOnAccept(this::uiScaleFactorOnAccept).setOnSelect(this::uiScaleFactorOnSelect)
          )
      )
  );

  private final Listeners listeners;

  private JTree categoryTree;
  private JEditorPane infoTextPane;
  private JPanel settingsPanel;
  private JButton okButton;
  private JButton cancelButton;

  private boolean modified;
  private boolean cancelled;
  private String defaultInfoText;
  private String infoText;

  /**
   * Shows the Preferences dialog and returns it when the user closes it.
   *
   * @param owner  the {@code Window} from which the dialog is displayed or {@code null} if this dialog has no owner
   * @return {@code true} if the user accepted any changes made in the dialog. {@code false} if the user discarded
   * the changes, either by clicking the "Cancel" button or closing the dialog.
   */
  public static boolean showDialog(Window owner) {
    PreferencesDialog dlg = new PreferencesDialog(owner);
    return !dlg.isCancelled();
  }

  /**
   * Returns whether the specified Look&Feel theme is blacklisted.
   *
   * @param themeClassName Fully qualified class name of the L&F theme to check.
   * @return {@code true} if the theme has been blacklisted, {@code false} otherwise.
   */
  public static boolean IsLookAndFeelBlacklisted(String themeClassName) {
    if (themeClassName != null) {
      return LOOK_AND_FEEL_BLACKLIST.contains(themeClassName);
    }
    return false;
  }

  protected PreferencesDialog(Window owner) {
    super(owner, "Preferences", ModalityType.APPLICATION_MODAL);

    this.listeners = new Listeners();
    this.defaultInfoText = "";
    this.infoText = "";

    init();
  }

  /**
   * Returns whether changes to dialog options have been cancelled.
   * <p>
   * The return value is only meaningful when queried after the dialog has been closed.
   * </p>
   */
  private boolean isCancelled() {
    return cancelled;
  }

  /**
   * Returns whether changes were made to the options after opening the dialog.
   *
   * @return {@code true} if options have been changed, {@code false} otherwise.
   */
  private boolean isModified() {
    return modified;
  }

  private void setModified(boolean value) {
    if (value != modified) {
      modified = value;
      okButton.setEnabled(modified);
    }
  }

  /** Returns the secondary description for the info panel. */
  @SuppressWarnings("unused")
  private String getDefaultInfo() {
    return defaultInfoText;
  }

  /**
   * Sets the secondary description for the info panel.
   *
   * <p>This text is shown when there is no primary description available.</p>
   */
  private void setDefaultInfo(String text) {
    this.defaultInfoText = Objects.toString(text, "");
    updateInfoPanel();
  }

  /** Returns the primary description string for the info panel. */
  @SuppressWarnings("unused")
  private String getInfo() {
    return infoText;
  }

  /**
   * Sets the primary description for the info panel.
   *
   * <p>This text takes precedence of the secondary description if available.</p>
   */
  private void setInfo(String text) {
    this.infoText = Objects.toString(text, "");
    updateInfoPanel();
  }

  private String getEffectiveInfo() {
    return infoText.isEmpty() ? defaultInfoText : infoText;
  }

  /**
   * Ensures that it is safe to discard modifications made to the options.
   *
   * Expects user confirmation if modifications were made.
   *
   * @return {@code true} if there are no modifications or the user confirmed the request.
   *         Returns {@code false}, otherwise.
   */
  private boolean shouldDiscard() {
    int result = JOptionPane.YES_OPTION;

    if (isModified()) {
    result = JOptionPane.showConfirmDialog(
        this,
        "Options have been modified.\nDo you really want to discard the changes?",
        "Discard changes",
        JOptionPane.YES_NO_OPTION);
    }

    return (result == JOptionPane.YES_OPTION);
  }

  /** Discards all changes made after the last Apply operation or creation of the dialog. */
  private void discard() {
    forEachOption(optionRoot, o -> {
      if (o instanceof OptionElementBase) {
        final OptionElementBase element = (OptionElementBase) o;
        final AppOption option = element.getOption();
        if (option != null) {
          option.revert();
        }
        for (final AppOption secOption : element.getSecondaryOptions()) {
          if (secOption != null) {
            secOption.revert();
          }
        }
      }
    });
  }

  /** Applies all modifications made by the user. */
  private void apply() {
    forEachOption(optionRoot, o -> {
      if (o instanceof OptionElementBase) {
        ((OptionElementBase) o).fireOnAccept();
      }
    });
  }

  /** Closes the dialog and applies changed settings. */
  private void accept() {
    cancelled = false;
    apply();
    closeDialog();
  }

  /** Closes the dialog and discards changed settings. */
  private void cancel() {
    cancelled = true;
    discard();
    closeDialog();
  }

  /** Closes the dialog and releases associated resources. */
  private void closeDialog() {
    setVisible(false);
    dispose();
  }

  /**
   * Enables the settings panel associated with the specified {@link Category}.
   *
   * @param cat The associated {@code Category} value.
   */
  private void showSettingsPanel(Category cat) {
    if (settingsPanel != null && cat != null && cat != Category.DEFAULT) {
      final CardLayout cl = (CardLayout)settingsPanel.getLayout();
      cl.show(settingsPanel, cat.toString());
    }
  }

  /** Expands or collapses all nodes of the categories tree. */
  private void setTreeExpandedState(boolean expanded) {
    if (categoryTree != null) {
      setTreeNodeExpandedState((DefaultMutableTreeNode)categoryTree.getModel().getRoot(), expanded);
    }
  }

  /** Used by {@link #setTreeExpandedState(boolean)} to expand nodes of the categories tree. */
  private void setTreeNodeExpandedState(DefaultMutableTreeNode node, boolean expanded) {
    final Enumeration<?> children = node.children();
    while (children.hasMoreElements()) {
      final Object e = children.nextElement();
      if (e instanceof DefaultMutableTreeNode) {
        setTreeNodeExpandedState((DefaultMutableTreeNode) e, expanded);
      }
    }

    if (!expanded && node.isRoot()) {
      return;
    }

    TreePath path = new TreePath(node.getPath());
    if (expanded) {
      categoryTree.expandPath(path);
    } else {
      categoryTree.collapsePath(path);
    }
  }

  /** Selects the first available category in the categories tree. */
  private void selectFirstCategory() {
    if (categoryTree != null) {
      TreeNode root = (TreeNode) categoryTree.getModel().getRoot();
      if (root.getChildCount() > 0) {
        TreeNode child = ((TreeNode) categoryTree.getModel().getRoot()).getChildAt(0);
        if (child instanceof DefaultMutableTreeNode) {
          categoryTree.setSelectionPath(new TreePath(((DefaultMutableTreeNode) child).getPath()));
        }
      }
    }
  }

  /** Displays the effective info text in the info panel. */
  private void updateInfoPanel() {
    String text = getEffectiveInfo();
    if (!text.startsWith("<html>")) {
      text = "<html><body><div>" + text + "</div></body></html>";
    }
    infoTextPane.setText(text);
    infoTextPane.setCaretPosition(0);
  }

  /**
   * A helper method that calculates a size, in pixels, based on the given number of character rows and columns,
   * as well as the given font. If font is {@code null} then a default font is used.
   */
  private Dimension calculateTextDimension(int rows, int cols, Font font) {
    if (font == null) {
      font = UIManager.getDefaults().getFont("Label.font");
    }

    FontMetrics fm = getFontMetrics(font);
    final int w = fm.charWidth('m');
    final int h = fm.getHeight();

    return new Dimension(Math.max(0, cols * w), Math.max(0, rows * h));
  }

  private void init() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this);
    getRootPane().getActionMap().put(this, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Act as if the user pressed the "Cancel" button
        if (shouldDiscard()) {
          cancel();
        }
      }
    });

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        listeners.windowClosing(e);
      }
    });

    // creating static elements
    // left panel > category tree
    categoryTree = new JTree();
    categoryTree.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
    categoryTree.setRootVisible(false);
    categoryTree.setShowsRootHandles(false);
    categoryTree.setEditable(false);

    // Don't allow nodes to collapse
    categoryTree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        throw new ExpandVetoException(event);
      }
    });

    final DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) categoryTree.getCellRenderer();
    renderer.setClosedIcon(null);
    renderer.setDisabledIcon(null);
    renderer.setLeafIcon(null);
    renderer.setOpenIcon(null);

    // category tree items with (slightly) increased font size
    Font font = renderer.getFont();
    renderer.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 2.0f));
    final FontMetrics fm = renderer.getFontMetrics(renderer.getFont());
    categoryTree.setRowHeight(Math.max(categoryTree.getRowHeight() + 1, fm.getHeight()) + 2); // adjust with padding

    // main panel > settings panel
    settingsPanel = new JPanel(new CardLayout(0, 0));
    settingsPanel.setBorder(BorderFactory.createEtchedBorder());

    // main panel > description panel
    HTMLEditorKit kit = new HTMLEditorKit();
    StyleSheet css = kit.getStyleSheet();
    font = UIManager.getDefaults().getFont("Label.font");
    css.addRule(String.format(Locale.ENGLISH, "body { font-family: %s,sans-serif; font-size: %.2fpt; }", font.getName(),
        font.getSize2D()));
    Document doc = kit.createDefaultDocument();

    infoTextPane = new JEditorPane();
    infoTextPane.setEditorKit(kit);
    infoTextPane.setDocument(doc);
    infoTextPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    infoTextPane.setBackground(UIManager.getColor("Label.background"));
    infoTextPane.setFocusable(false);
    infoTextPane.setEditable(false);
    infoTextPane.setPreferredSize(calculateTextDimension(6, 40, null));

    JScrollPane infoTextScroller = new JScrollPane(infoTextPane);
    infoTextScroller.setBorder(BorderFactory.createEmptyBorder());
    infoTextScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JPanel infoPanel = new JPanel(new BorderLayout());
    infoPanel.setBorder(BorderFactory.createTitledBorder("Description"));
    infoPanel.add(infoTextScroller, BorderLayout.CENTER);

    // button panel (OK, Cancel)
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 8));

    okButton = new JButton("Apply and Close", Icons.ICON_CHECK_16.getIcon());
    okButton.setEnabled(isModified());
    okButton.addActionListener(listeners);
    buttonPanel.add(okButton);
    getRootPane().setDefaultButton(okButton);

    cancelButton = new JButton("Cancel", Icons.ICON_CHECK_NOT_16.getIcon());
    cancelButton.addActionListener(listeners);
    buttonPanel.add(cancelButton);

    // creating dynamic elements
    parse(optionRoot);

    // assembling everything
    // assembling the left panel (category tree)
    setTreeExpandedState(true);
    categoryTree.addTreeSelectionListener(listeners);

    JScrollPane leftScroller = new JScrollPane(categoryTree);
    final Dimension scrollerSize = new Dimension(categoryTree.getPreferredSize());
    scrollerSize.width += 8;
    scrollerSize.height += 8;
    leftScroller.setPreferredSize(scrollerSize);

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.setBorder(BorderFactory.createEtchedBorder());
    leftPanel.add(leftScroller, BorderLayout.CENTER);

    // optimize preferred size of settings panel
    final Dimension dim = settingsPanel.getPreferredSize();
    // avoid showing horizontal scrollbar for settings panel
    dim.width += UIManager.getInt("ScrollBar.width");
    if (getOwner().getGraphicsConfiguration() != null) {
      int screenHeight = getOwner().getGraphicsConfiguration().getDevice().getDisplayMode().getHeight();
      if (screenHeight > 0) {
        // max. 50% of active screen height
        dim.height = Math.min(screenHeight / 2, dim.height);
      }
    }

    // Further limit preferred height to "General" category panel height
    final OptionBase optionBase = optionRoot.findOption(o -> o instanceof OptionGroup &&
        ((OptionGroup) o).isDefault() && o.getParent().getId().equals(Category.GENERAL_OPTIONS));
    if (optionBase instanceof OptionGroup) {
      final Dimension panelDim = ((OptionGroup) optionBase).getUiPanel().getPreferredSize();
      if (panelDim.height > 0) {
        dim.height = Math.min(panelDim.height, dim.height);
        // compensate horizontal scrollbar width
        dim.height += UIManager.getInt("ScrollBar.width");
      }
    }

    settingsPanel.setPreferredSize(dim);

    // assembling the right panel (settings and description panels)
    JSplitPane rightPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    rightPane.setBorder(BorderFactory.createEmptyBorder());
    rightPane.setTopComponent(settingsPanel);
    rightPane.setBottomComponent(infoPanel);
    rightPane.setResizeWeight(1.0);

    // assembling the main panel (left and right panels)
    JSplitPane mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    mainPane.setLeftComponent(leftPanel);
    mainPane.setRightComponent(rightPane);
    mainPane.setResizeWeight(0.0);

    // assemble everything
    setLayout(new BorderLayout(8, 8));
    add(mainPane, BorderLayout.CENTER);
    add(buttonPanel, BorderLayout.SOUTH);

    selectFirstCategory();

    rightPane.setDividerLocation(-1); // use preferred size of child panels
    mainPane.setDividerLocation(-1);  // use preferred size of child panels

    pack();
    setMinimumSize(new Dimension(getSize().width * 2 / 3, getSize().height * 2 / 5));
//    setSize(new Dimension(getSize().width, getSize().height * 3 / 4));

    setLocationRelativeTo(getOwner());
    setVisible(true);
  }

  /** Initiates the parse operation to create the dynamic interface. */
  private void parse(OptionCategory cat) {
    final OptionCategory rootCategory = parseCategory(Objects.requireNonNull(cat));
    categoryTree.setModel(new DefaultTreeModel(rootCategory.getUiTreeNode()));
    forEachOption(cat, o -> {
      if (o instanceof OptionElementBase) {
        ((OptionElementBase) o).fireOnCreated();
      }
    });
  }

  private OptionCategory parseCategory(OptionCategory cat) {
    DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(cat.getId());
    cat.setUiTreeNode(treeNode);

    JPanel panel = null;
    if (!cat.isDefault()) {
      panel = new JPanel(new GridBagLayout());
      JScrollPane scroll = new JScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scroll.getVerticalScrollBar().setUnitIncrement(16);
      cat.setUiScrollPane(scroll);
      settingsPanel.add(scroll, treeNode.getUserObject().toString());
    }

    GridBagConstraints gbc = ViewerUtil.setGBC(null, 0, 0, 1, 1, 1.0, 0.0,
        GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);

    for (final OptionBase child : cat.getChildren()) {
      if (child instanceof OptionCategory) {
        final OptionCategory childCategory = (OptionCategory) child;
        parseCategory(childCategory);
        treeNode.add(childCategory.getUiTreeNode());
      } else if (child instanceof OptionGroup) {
        if (panel != null) {
          final OptionGroup childGroup = (OptionGroup) child;
          parseGroup(childGroup);
          panel.add(childGroup.getUiPanel(), gbc);
          gbc.gridy++;
        } else {
          throw new IllegalArgumentException(
              "Cannot be assigned to a default category: " + child.getClass().getSimpleName());
        }
      } else {
        throw new IllegalArgumentException("Not an option container: " + child.getClass().getSimpleName());
      }
    }

    // XXX: Forces group panel placement to the top of the parent panel
    if (panel != null) {
      gbc.weighty = 1.0;
      gbc.insets = new Insets(0, 0, 0, 0);
      final JPanel p = new JPanel();
      p.setMinimumSize(new Dimension());
      p.setPreferredSize(p.getMinimumSize());
      panel.add(p, gbc);
    }

    return cat;
  }

  private OptionGroup parseGroup(OptionGroup group) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder(group.isDefault() ? "General" : group.getLabel()));
    group.setUiPanel(panel);

    GridBagConstraints gbc = ViewerUtil.setGBC(null, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
        GridBagConstraints.HORIZONTAL, new Insets(4, 4, 4, 4), 0, 0);

    for (final OptionBase child : group.getChildren()) {
      if (child instanceof OptionCheckBox) {
        final OptionCheckBox childCheckBox = (OptionCheckBox) child;
        parseCheckBox(childCheckBox);
        // Adding element
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        panel.add(childCheckBox.getUiCheckBox(), gbc);
        gbc.gridy++;
      } else if (child instanceof OptionGroupBox) {
        final OptionGroupBox childGroupBox = (OptionGroupBox) child;
        parseGroupBox(childGroupBox);
        // adding elements
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        panel.add(childGroupBox.getUiLabel(), gbc);
        gbc.weightx = 1.0;
        gbc.gridx = 1;
        panel.add(childGroupBox.getUiComboBox(), gbc);
        gbc.gridy++;
      } else if (child instanceof OptionPathBox) {
        final OptionPathBox childPathBox = (OptionPathBox) child;
        parsePathBox(childPathBox);

        // subpanel is needed for multi-components panel
        final JPanel subPanel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc2 = ViewerUtil.setGBC(null, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
        subPanel.add(childPathBox.getUiTextField(), gbc2);
        ViewerUtil.setGBC(gbc2, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
            new Insets(0, 4, 0, 0), 0, 0);
        subPanel.add(childPathBox.getUiSelectButton(), gbc2);
        if (childPathBox.getUiClearButton() != null) {
          ViewerUtil.setGBC(gbc2, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
              new Insets(0, 4, 0, 0), 0, 0);
          subPanel.add(childPathBox.getUiClearButton(), gbc2);
        }

        // adding elements
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        panel.add(childPathBox.getUiLabel(), gbc);
        gbc.weightx = 1.0;
        gbc.gridx = 1;
        panel.add(subPanel, gbc);
        gbc.gridy++;
      } else {
        throw new IllegalArgumentException("Not an option element: " + child.getClass().getSimpleName());
      }
    }

    return group;
  }

  private OptionCheckBox parseCheckBox(OptionCheckBox checkBox) {
    checkBox.fireOnInit();

    checkBox.updateUi();

    JCheckBox cb = checkBox.getUiCheckBox();
    cb.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        setDefaultInfo(checkBox.getDescription());
      }

      @Override
      public void focusLost(FocusEvent e) {
        setDefaultInfo(null);
      }
    });

    cb.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        setInfo(checkBox.getDescription());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setInfo(null);
      }
    });

    cb.addActionListener(e -> {
      checkBox.setValue(cb.isSelected());
      checkBox.fireOnAction();
      setModified(true);
    });

    return checkBox;
  }

  private OptionGroupBox parseGroupBox(OptionGroupBox groupBox) {
    groupBox.fireOnInit();

    groupBox.updateUi();

    JComboBox<Object> comboBox = groupBox.getUiComboBox();
    comboBox.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        setDefaultInfo(groupBox.getDescription());
      }

      @Override
      public void focusLost(FocusEvent e) {
        setDefaultInfo(null);
      }
    });

    final MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        setInfo(groupBox.getDescription());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setInfo(null);
      }
    };

    JLabel label = groupBox.getUiLabel();
    label.addMouseListener(adapter);
    comboBox.addMouseListener(adapter);

    comboBox.addActionListener(e -> {
      groupBox.setSelectedIndex(comboBox.getSelectedIndex());
      groupBox.fireOnSelect();
      setModified(true);
    });

    return groupBox;
  }

  private OptionPathBox parsePathBox(OptionPathBox pathBox) {
    pathBox.fireOnInit();

    pathBox.updateUi();

    final FocusListener focus = new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        setDefaultInfo(pathBox.getDescription());
      }

      @Override
      public void focusLost(FocusEvent e) {
        setDefaultInfo(null);
      }
    };

    final MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        setInfo(pathBox.getDescription());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setInfo(null);
      }
    };

    pathBox.getUiLabel().addMouseListener(adapter);
    pathBox.getUiTextField().addMouseListener(adapter);
    pathBox.getUiTextField().addFocusListener(focus);
    pathBox.getUiSelectButton().addMouseListener(adapter);
    pathBox.getUiSelectButton().addFocusListener(focus);
    if (pathBox.getUiClearButton() != null) {
      pathBox.getUiClearButton().addMouseListener(adapter);
      pathBox.getUiClearButton().addFocusListener(focus);
    }

    return pathBox;
  }

  /**
   * Scans the option tree recursively and performs the given operation for each node.
   *
   * @param option Option to scan and perform the given operation.
   * @param proc Operation to perform on each option node.
   */
  private void forEachOption(OptionBase option, Consumer<OptionBase> proc) {
    proc.accept(option);

    if (option instanceof OptionContainerBase) {
      final OptionContainerBase container = (OptionContainerBase) option;
      for (final OptionBase child : container.getChildren()) {
        forEachOption(child, proc);
      }
    }
  }

  /** onChanged() function for {@link AppOption#WEIDU_PATH}. */
  private boolean weiduPathOnChanged(OptionPathBox pb) {
    setModified(true);
    return true;
  }

  /** onCreated() function for {@link AppOption#TABLE_SHOW_OFFSETS}. */
  private void showOffsetsOnCreated(OptionCheckBox cb) {
    final OptionBase optionOffsetsRelative = optionRoot.findOption(OptionsMenuItem.OPTION_SHOWOFFSETSRELATIVE);
    if (optionOffsetsRelative instanceof OptionCheckBox) {
      ((OptionCheckBox) optionOffsetsRelative).setEnabled(cb.getValue());
    }
  }

  /** onAction() function for {@link AppOption#TABLE_SHOW_OFFSETS}. */
  private boolean showOffsetsOnAction(OptionCheckBox cb) {
    final OptionBase optionOffsetsRelative = optionRoot.findOption(OptionsMenuItem.OPTION_SHOWOFFSETSRELATIVE);
    if (optionOffsetsRelative instanceof OptionCheckBox) {
      ((OptionCheckBox) optionOffsetsRelative).setEnabled(cb.getValue());
    }
    return true;
  }

  /** onCreated() function for {@link AppOption#TABLE_SHOW_SIZE}. */
  private void showSizeOnCreated(OptionCheckBox cb) {
    final OptionBase optionShowSizeHex = optionRoot.findOption(OptionsMenuItem.OPTION_SHOWSIZEHEX);
    if (optionShowSizeHex instanceof OptionCheckBox) {
      ((OptionCheckBox) optionShowSizeHex).setEnabled(cb.getValue());
    }
  }

  /** onAction() function for {@link AppOption#TABLE_SHOW_SIZE}. */
  private boolean showSizeOnAction(OptionCheckBox cb) {
    final OptionBase optionShowSizeHex = optionRoot.findOption(OptionsMenuItem.OPTION_SHOWSIZEHEX);
    if (optionShowSizeHex instanceof OptionCheckBox) {
      ((OptionCheckBox) optionShowSizeHex).setEnabled(cb.getValue());
    }
    return true;
  }

  /** onCreated() function for {@link AppOption#LAUNCH_GAME_ALLOWED}. */
  private void launchGameAllowedOnCreated(OptionCheckBox cb) {
    cb.setEnabled(BrowserMenuBar.getInstance().getOptions().isLaunchGameMenuEnabled());
  }

  /** onCreated() function for {@link AppOption#BCS_SYNTAX_HIGHLIGHTING}. */
  private void bcsSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.BCS_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#BCS_SYNTAX_HIGHLIGHTING}. */
  private boolean bcsSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.BCS_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#GLSL_SYNTAX_HIGHLIGHTING}. */
  private void glslSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.GLSL_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#GLSL_SYNTAX_HIGHLIGHTING}. */
  private boolean glslSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.GLSL_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#INI_SYNTAX_HIGHLIGHTING}. */
  private void iniSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.INI_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#INI_SYNTAX_HIGHLIGHTING}. */
  private boolean iniSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.INI_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#LUA_SYNTAX_HIGHLIGHTING}. */
  private void luaSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.LUA_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#LUA_SYNTAX_HIGHLIGHTING}. */
  private boolean luaSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.LUA_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#MENU_SYNTAX_HIGHLIGHTING}. */
  private void menuSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.MENU_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#MENU_SYNTAX_HIGHLIGHTING}. */
  private boolean menuSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.MENU_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#SQL_SYNTAX_HIGHLIGHTING}. */
  private void sqlSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.SQL_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#SQL_SYNTAX_HIGHLIGHTING}. */
  private boolean sqlSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.SQL_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#TLK_SYNTAX_HIGHLIGHTING}. */
  private void tlkSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.TLK_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#TLK_SYNTAX_HIGHLIGHTING}. */
  private boolean tlkSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.TLK_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#WEIDU_SYNTAX_HIGHLIGHTING}. */
  private void weiduSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.WEIDU_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#WEIDU_SYNTAX_HIGHLIGHTING}. */
  private boolean weiduSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.WEIDU_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** onCreated() function for {@link AppOption#DLG_SYNTAX_HIGHLIGHTING}. */
  private void dlgSyntaxHighlightingOnCreated(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.DLG_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
  }

  /** onAction() function for {@link AppOption#DLG_SYNTAX_HIGHLIGHTING}. */
  private boolean dlgSyntaxHighlightingOnAction(OptionCheckBox cb) {
    setOptionUiEnabled(AppOption.DLG_COLOR_SCHEME.getName(), cb.getUiCheckBox().isSelected());
    return true;
  }

  /** Helper method for setting the enabled state of a given option component. */
  private void setOptionUiEnabled(String optionName, boolean enable) {
    OptionBase option = optionRoot.findOption(optionName);
    if (option instanceof OptionElementBase) {
      ((OptionElementBase) option).setEnabled(enable);
    }
  }

  /** onInit() function for {@link AppOption#TEXT_TAB_SIZE}. */
  private void textTabSizeOnInit(OptionGroupBox gb) {
    final String[] items = { "Expand by 2 Spaces", "Expand by 4 Spaces", "Expand by 8 Spaces" };
    gb.clearItems();
    for (final String item : items) {
      gb.addItem(item);
    }
    gb.setSelectedIndex(gb.getOption().getIntValue());
  }

  /** onInit() function for {@link AppOption#GLOBAL_FONT_SIZE}. */
  private void globalFontSizeOnInit(OptionGroupBox gb) {
    gb.clearItems();

    final int[] fontSizes = OptionsMenuItem.getFontSizes();
    int minFontSize = Arrays.stream(fontSizes).filter(i -> i > 0).min().orElse(0);
    int maxFontSize = Arrays.stream(fontSizes).max().orElse(0);
    int selectedSize = gb.getOption().getIntValue();
    selectedSize = Math.max(minFontSize, Math.min(maxFontSize, selectedSize));
    boolean isCustom = true;
    for (int i = 0, len = OptionsMenuItem.getFontSizes().length; i < len; i++) {
      int size = OptionsMenuItem.getFontSizes()[i];
      if (size > 0) {
        String msg = OptionsMenuItem.getFontSizes()[i] + " %" + (size == 100 ? " (Default)" : "");
        gb.addItem(new DataItem<>(msg, size));
        if (size == selectedSize) {
          isCustom = false;
          gb.setSelectedIndex(i);
        }
      } else {
        String msg = isCustom ? "Custom (" + selectedSize + " %)..." : "Custom...";
        gb.addItem(new DataItem<>(msg, isCustom ? selectedSize : size));
        if (isCustom) {
          gb.setSelectedIndex(i);
        }
      }
    }
  }

  /** onAccept() function for {@link AppOption#GLOBAL_FONT_SIZE}. */
  private void globalFontSizeOnAccept(OptionGroupBox gb) {
    try {
      DataItem<?> item = (DataItem<?>) gb.getItem(gb.getSelectedIndex());
      int size = (Integer) item.getData();
      if (size > 0) {
        gb.getOption().setValue(size);
      } else if (size < -1) {
        gb.getOption().setValue(-size);
      }
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }
  }

  /** onSelect() function for {@link AppOption#GLOBAL_FONT_SIZE}. */
  private boolean globalFontSizeOnSelect(OptionGroupBox gb) {
    final int[] fontSizes = OptionsMenuItem.getFontSizes();
    if (gb.getSelectedIndex() != fontSizes.length - 1) {
      // predefined font size: no further action
      return true;
    }

    try {
      @SuppressWarnings("unchecked")
      DataItem<Integer> item = (DataItem<Integer>) gb.getItem(gb.getSelectedIndex());
      int size = item.getData();
      if (size < 0) {
        size = gb.getOption().getIntValue();
      }

      int minFontSize = Arrays.stream(fontSizes).filter(i -> i > 0).min().orElse(0);
      int maxFontSize = Arrays.stream(fontSizes).max().orElse(0);
      String ret = JOptionPane.showInputDialog(NearInfinity.getInstance(),
          String.format("Enter font size in percent (%d - %d):", minFontSize, maxFontSize), size);
      if (ret == null) {
        selectMatchingGlobalFontSize(gb, size);
        return true;
      }

      int value = size;
      try {
        int radix = 10;
        if (ret.toLowerCase().startsWith("0x")) {
          ret = ret.substring(2);
          radix = 16;
        }
        value = Integer.parseInt(ret, radix);
        if (value < minFontSize || value > maxFontSize) {
          JOptionPane.showMessageDialog(NearInfinity.getInstance(),
              "Number out of range. Using current value '" + size + "'.");
          value = gb.getOption().getIntValue();
        }
      } catch (NumberFormatException e) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(),
            "Invalid number entered. Using current value '" + size + "'.");
      }

      selectMatchingGlobalFontSize(gb, value);

      return true;
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }

    return true;
  }

  /** onInit() function for {@link AppOption#LOOK_AND_FEEL_CLASS}. */
  private void lookAndFeelClassOnInit(OptionGroupBox gb) {
    gb.clearItems();

    int selectedIndex = 0;
    final String selectedLF = gb.getOption().getStringValue();
    LookAndFeelInfo[] info = UIManager.getInstalledLookAndFeels();
    for (int i = 0, curIdx = 0; i < info.length; i++) {
      final LookAndFeelInfo lf = info[i];

      // check if theme is black-listed
      if (lf != null && IsLookAndFeelBlacklisted(lf.getClassName())) {
        continue;
      }

      try {
        // L&F description is only available from class instance
        final Class<?> cls = Class.forName(lf.getClassName());
        String label = null;
        try {
          // may fail on Java >= 9
          Object o = cls.getDeclaredConstructor().newInstance();
          if (o instanceof LookAndFeel) {
            label = ((LookAndFeel) o).getName();
          }
        } catch (Exception e) {
//          Logger.error(e);
        }

        if (label == null) {
          // constructing (beautified) L&F name from class name
          label = cls.getSimpleName();
          int idx = label.indexOf("LookAndFeel");
          if (idx > 0) {
            label = label.substring(0, idx);
            label = label.replaceAll("([A-Z][^A-Z])", " $1").trim();
          }
        }

        // Marking themes that are compatible with automatic light/dark mode selection
        if (OptionsMenuItem.isLookAndFeelAutoModeReady(lf, info)) {
          label = label + " (*)";
        }

        gb.addItem(new DataItem<>(label, lf));
        if (lf.getClassName().equals(selectedLF)) {
          selectedIndex = curIdx;
        }

        // need to track item index separately in case that a L&F class is not accessible
        curIdx++;
      } catch (Exception e) {
//        Logger.error(e);
      }
    }

    if (gb.getItemCount() == 0) {
      gb.addItem(
          new DataItem<>(BrowserMenuBar.getDefaultLookAndFeel().getName(), BrowserMenuBar.getDefaultLookAndFeel()));
      gb.setSelectedIndex(0);
    } else {
      gb.setSelectedIndex(selectedIndex);
    }
  }

  /** onAccept() function for {@link AppOption#LOOK_AND_FEEL_CLASS}. */
  private void lookAndFeelClassOnAccept(OptionGroupBox gb) {
    try {
      @SuppressWarnings("unchecked")
      final DataItem<LookAndFeelInfo> item = (DataItem<LookAndFeelInfo>) gb.getItem(gb.getSelectedIndex());
      gb.getOption().setValue(item.getData().getClassName());
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }
  }

  /** onInit() function for {@link AppOption#TEXT_FONT}. */
  private void textFontOnInit(OptionGroupBox gb) {
    gb.clearItems();

    final List<Font> fonts = OptionsMenuItem.getFonts();
    for (int i = 0, size = fonts.size(); i < size; i++) {
      Font font = fonts.get(i);
      String label;
      if (i < 4) {
        // predefined font
        label = String.format("%s %d", font.getName(), font.getSize());
      } else {
        // custom font
        String fontName = AppOption.TEXT_FONT_NAME.getStringValue();
        label = "Select font...";
        if (font == null && !fontName.isEmpty()) {
          int fontSize = AppOption.TEXT_FONT_SIZE.getIntValue();
          int fontStyle = AppOption.TEXT_FONT_STYLE.getIntValue();
          font = new Font(fontName, fontStyle, fontSize);
        }
        if (font != null) {
          label += String.format(" (%s %d)", font.getName(), font.getSize());
        }
      }
      gb.addItem(new DataItem<>(label, font));
    }
    gb.setSelectedIndex(gb.getOption().getIntValue());
  }

  /** onCreated() function for {@link AppOption#TEXT_FONT}. */
  private void textFontOnCreated(OptionGroupBox gb) {
    gb.getUiComboBox().setRenderer(new FontListCellRenderer());
  }

  /** onAccept() function for {@link AppOption#TEXT_FONT}. */
  private void textFontOnAccept(OptionGroupBox gb) {
    try {
      int index = gb.getSelectedIndex();
      @SuppressWarnings("unchecked")
      final DataItem<Font> item = (DataItem<Font>) gb.getItem(index);
      if (index == 4) {
        // custom font
        final Font font = item.getData();
        AppOption.TEXT_FONT_NAME.setValue(font.getName());
        AppOption.TEXT_FONT_SIZE.setValue(font.getSize());
        AppOption.TEXT_FONT_STYLE.setValue(font.getStyle());
      }
      gb.getOption().setValue(index);
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }
  }

  /** onSelect() function for {@link AppOption#TEXT_FONT}. */
  private boolean textFontOnSelect(OptionGroupBox gb) {
    try {
      int index = gb.getSelectedIndex();
      if (index == 4) {
        // custom font
        @SuppressWarnings("unchecked")
        final DataItem<Font> item = (DataItem<Font>) gb.getItem(index);
        Font font = item.getData();
        FontChooser fc = new FontChooser();
        if (font != null) {
          fc.setSelectedFont(font);
        }
        if (fc.showDialog(NearInfinity.getInstance()) == FontChooser.OK_OPTION) {
          font = fc.getSelectedFont();
          item.setData(font);
          String label = String.format("Select font... (%s %d)", font.getName(), font.getSize());
          item.setLabel(label);
          gb.updateUi();
        }
      }
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }

    return true;
  }

  /** onInit() function for {@link AppOption#TLK_CHARSET_TYPE}. */
  private void tlkCharsetTypeOnInit(OptionGroupBox gb) {
    gb.clearItems();

    final String selectedCharset = gb.getOption().getStringValue();
    int selectedIndex = 0;

    final Set<String> cache = new HashSet<>();
    int index = 0;

    gb.addItem(new DataItem<>("Autodetect", OptionsMenuItem.getDefaultCharset()));
    index++;

    // Adding default charsets entries
    for (final CharsetInfo ci : OptionsMenuItem.getDefaultCharsets()) {
      if (ci != null) {
        gb.addItem(new DataItem<>(ci.getLabel(), ci.getId()));
        cache.add(ci.getId());
        if (ci.getId().equalsIgnoreCase(selectedCharset)) {
          selectedIndex = index;
        }
        index++;
      }
    }

    // Adding dynamically queried charsets
    gb.addItem(new DataItem<>("------ More Charsets ------", null));

    for (final Entry<String, Charset> entry : Charset.availableCharsets().entrySet()) {
      final String name = entry.getKey();
      if (!cache.contains(name)) {
        boolean official = !name.toLowerCase().startsWith("x-");
        String desc = official ? name : name.substring(2) + " (unofficial)";
        gb.addItem(new DataItem<>(desc, name));
        cache.add(name);
        if (name.equalsIgnoreCase(selectedCharset)) {
          selectedIndex = index;
        }
      }
    }

    gb.setSelectedIndex(selectedIndex);
  }

  /** onAccept() function for {@link AppOption#TLK_CHARSET_TYPE}. */
  private void tlkCharsetTypeOnAccept(OptionGroupBox gb) {
    try {
      @SuppressWarnings("unchecked")
      final DataItem<String> item = (DataItem<String>) gb.getItem(gb.getSelectedIndex());
      if (item.getData() != null) {
        gb.getOption().setValue(item.getData());
      }
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }
  }

  /** onSelect() function for {@link AppOption#TLK_CHARSET_TYPE}. */
  private boolean tlkCharsetTypeOnSelect(OptionGroupBox gb) {
    try {
      final DataItem<?> item = (DataItem<?>) gb.getItem(gb.getSelectedIndex());
      return item.getData() != null;
    } catch (IndexOutOfBoundsException e) {
      Logger.trace(e);
    }
    return false;
  }

  /** onInit() function for {@link AppOption#GAME_LANGUAGES}. */
  private void gameLanguagesOnInit(OptionGroupBox gb) {
    gb.clearItems();

    final String selectedCode = OptionsMenuItem.getGameLanguage(gb.getOption().getStringValue(), Profile.getGame());
    int selectedIndex = 0;

    gb.addItem(new DataItem<>("Autodetect", OptionsMenuItem.getDefaultGameLanguage()));

    if (Profile.isEnhancedEdition()) {
      List<String> languages = Profile.getProperty(Profile.Key.GET_GAME_LANG_FOLDER_NAMES_AVAILABLE);
      for (int i = 0, size = languages.size(); i < size; i++) {
        final String langCode = languages.get(i);
        final String langName = OptionsMenuItem.getDisplayLanguage(langCode);
        final String label;
        if (!langName.equalsIgnoreCase(langCode)) {
          label = String.format("%s (%s)", langName, langCode);
        } else {
          label = langCode;
        }
        gb.addItem(new DataItem<>(label, langCode));
        if (selectedCode.equalsIgnoreCase(langCode)) {
          selectedIndex = i + 1; // adjust for "Autodetect"
        }
      }
    }

    gb.setSelectedIndex(selectedIndex);
  }

  /** onCreated() function for {@link AppOption#GAME_LANGUAGES}. */
  private void gameLanguagesOnCreated(OptionGroupBox gb) {
    gb.getUiComboBox().setEnabled(Profile.isEnhancedEdition());
    gb.getUiLabel().setEnabled(Profile.isEnhancedEdition());
  }

  /** onAccept() function for {@link AppOption#GAME_LANGUAGES}. */
  private void gameLanguagesOnAccept(OptionGroupBox gb) {
    if (Profile.isEnhancedEdition()) {
      try {
        @SuppressWarnings("unchecked")
        final DataItem<String> item = (DataItem<String>) gb.getItem(gb.getSelectedIndex());
        final String langCode = item.getData();

        String languageDefinitions = gb.getOption().getStringValue();
        languageDefinitions = OptionsMenuItem.updateGameLanguages(languageDefinitions, Profile.getGame(), langCode);
        gb.getOption().setValue(languageDefinitions);
      } catch (IndexOutOfBoundsException e) {
        Logger.error(e);
      }
    }
  }

  /** onCreated() function for {@link AppOption#UI_SCALE_ENABLED}. */
  private void uiScaleEnabledOnCreated(OptionCheckBox cb) {
    cb.getUiCheckBox().setEnabled(NearInfinity.isUiScalingSupported());
    if (cb.getUiCheckBox().isEnabled()) {
      setUiScaleFactorEnabled(cb.getValue());
    }
  }

  /** onAction() function for {@link AppOption#UI_SCALE_ENABLED}. */
  private boolean uiScaleEnabledOnAction(OptionCheckBox cb) {
    if (cb.getUiCheckBox().isEnabled()) {
      setUiScaleFactorEnabled(cb.getValue());
    }
    return true;
  }

  /** onInit() function for {@link AppOption#UI_SCALE_FACTOR}. */
  private void uiScaleFactorOnInit(OptionGroupBox gb) {
    gb.clearItems();

    int selectedScale = gb.getOption().getIntValue();
    int[] scaleFactors = OptionsMenuItem.getUiScalingFactors();
    int minUiScale = Arrays.stream(scaleFactors).filter(i -> i > 0).min().orElse(0) / 2;
    int maxUiScale = Arrays.stream(scaleFactors).max().orElse(0);
    selectedScale = Math.max(minUiScale, Math.min(maxUiScale, selectedScale));
    boolean isCustomScale = true;

    for (int i = 0; i < scaleFactors.length; i++) {
      int factor = scaleFactors[i];
      if (factor > 0) {
        String msg = factor + " %" + (factor == 100 ? " (Default)" : "");
        gb.addItem(new DataItem<>(msg, factor));
        if (factor == selectedScale) {
          gb.setSelectedIndex(i);
          isCustomScale = false;
        }
      } else {
        String msg = isCustomScale ? "Custom (" + selectedScale + " %)..." : "Custom...";
        gb.addItem(new DataItem<>(msg, isCustomScale ? selectedScale : factor));
        if (isCustomScale) {
          gb.setSelectedIndex(i);
        }
      }
    }
  }

  /** onCreated() function for {@link AppOption#UI_SCALE_FACTOR}. */
  private void uiScaleFactorOnCreated(OptionGroupBox gb) {
    gb.getUiComboBox().setEnabled(NearInfinity.isUiScalingSupported());
    gb.getUiLabel().setEnabled(NearInfinity.isUiScalingSupported());
  }

  /** onAccept() function for {@link AppOption#UI_SCALE_FACTOR}. */
  private void uiScaleFactorOnAccept(OptionGroupBox gb) {
    try {
      @SuppressWarnings("unchecked")
      final DataItem<Integer> item = (DataItem<Integer>) gb.getItem(gb.getSelectedIndex());
      gb.getOption().setValue(item.getData());
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }
  }

  /** onSelect() function for {@link AppOption#UI_SCALE_FACTOR}. */
  private boolean uiScaleFactorOnSelect(OptionGroupBox gb) {
    final int[] scaleFactors = OptionsMenuItem.getUiScalingFactors();
    if (gb.getSelectedIndex() != scaleFactors.length - 1) {
      return true;
    }

    try {
      @SuppressWarnings("unchecked")
      final DataItem<Integer> item = (DataItem<Integer>) gb.getItem(gb.getSelectedIndex());
      int factor = item.getData();
      if (factor < 0) {
        factor = gb.getOption().getIntValue();
      }

      int minUiScale = Arrays.stream(scaleFactors).filter(i -> i > 0).min().orElse(0) / 2;
      int maxUiScale = Arrays.stream(scaleFactors).max().orElse(0);
      String ret = JOptionPane.showInputDialog(NearInfinity.getInstance(),
          String.format("Enter UI scaling factor in percent (%d - %d):", minUiScale, maxUiScale), factor);
      if (ret == null) {
        selectMatchingUiScaleFactor(gb, factor);
        return true;
      }

      int value = factor;
      try {
        int radix = 10;
        if (ret.toLowerCase().startsWith("0x")) {
          ret = ret.substring(2);
          radix = 16;
        }
        value = Integer.parseInt(ret, radix);
        if (value < minUiScale || value > maxUiScale) {
          JOptionPane.showMessageDialog(NearInfinity.getInstance(),
              "Number out of range. Using current value '" + factor + "'.");
          value = gb.getOption().getIntValue();
        }
      } catch (NumberFormatException e) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(),
            "Invalid number entered. Using current value '" + factor + "'.");
      }

      selectMatchingUiScaleFactor(gb, value);

      return true;
    } catch (IndexOutOfBoundsException e) {
      Logger.error(e);
    }
    return false;
  }

  /** Selects the combobox item that matches the given Global Font Size and updates item label if needed. */
  private void selectMatchingGlobalFontSize(OptionGroupBox gb, int percent) {
    int selectedIndex = -1;
    for (int i = 0, size = gb.getItemCount(); i < size; i++) {
      @SuppressWarnings("unchecked")
      final DataItem<Integer> item = (DataItem<Integer>) gb.getItem(i);
      if (percent > 0 && item.getData() == percent) {
        selectedIndex = i;
        break;
      }
    }

    if (selectedIndex < 0) {
      selectedIndex = OptionsMenuItem.getFontSizes().length - 1;
    }

    if (selectedIndex == OptionsMenuItem.getFontSizes().length - 1) {
      // custom scale factor
      @SuppressWarnings("unchecked")
      final DataItem<Integer> item = (DataItem<Integer>) gb.getItem(selectedIndex);
      if (percent < 0) {
        percent = gb.getOption().getIntValue();
      }
      item.setData(percent);
      item.setLabel("Custom (" + percent + " %)...");
      gb.updateUi();
    }

    gb.setSelectedIndex(selectedIndex);
    gb.getUiComboBox().setSelectedIndex(selectedIndex);
  }

  /** Sets the specified enabled state for all UI component related to the UI Scale Factor. */
  private void setUiScaleFactorEnabled(boolean enabled) {
    OptionBase o = optionRoot.findOption(NearInfinity.APP_UI_SCALE_FACTOR);
    if (o instanceof OptionGroupBox) {
      final OptionGroupBox uiScaleFactor = (OptionGroupBox) o;
      uiScaleFactor.getUiComboBox().setEnabled(enabled);
      uiScaleFactor.getUiLabel().setEnabled(enabled);
    }
  }

  /** Selects the combobox item that matches the given UI Scale Factor and updates item label if needed. */
  private void selectMatchingUiScaleFactor(OptionGroupBox gb, int factor) {
    int selectedIndex = -1;
    for (int i = 0, size = gb.getItemCount(); i < size; i++) {
      @SuppressWarnings("unchecked")
      final DataItem<Integer> item = (DataItem<Integer>) gb.getItem(i);
      if (factor > 0 && item.getData() == factor) {
        selectedIndex = i;
        break;
      }
    }

    if (selectedIndex < 0) {
      selectedIndex = OptionsMenuItem.getUiScalingFactors().length - 1;
    }

    if (selectedIndex == OptionsMenuItem.getUiScalingFactors().length - 1) {
      // custom scale factor
      @SuppressWarnings("unchecked")
      final DataItem<Integer> item = (DataItem<Integer>) gb.getItem(selectedIndex);
      if (factor < 0) {
        factor = gb.getOption().getIntValue();
      }
      item.setData(factor);
      item.setLabel("Custom (" + factor + " %)...");
      gb.updateUi();
    }

    gb.setSelectedIndex(selectedIndex);
    gb.getUiComboBox().setSelectedIndex(selectedIndex);
  }

  // -------------------------- INNER CLASSES --------------------------

  private class Listeners implements ActionListener, TreeSelectionListener {

    // Called by WindowAdapter > windowClosing(WindowEvent) in the main dialog class.
    public void windowClosing(WindowEvent e) {
      // Act as if the user pressed the "Cancel" button
      if (shouldDiscard()) {
        cancel();
      }
    }

    // --------------------- Begin Interface ActionListener ---------------------

    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getSource() == cancelButton) {
        if (shouldDiscard()) {
          cancel();
        }
      } else if (e.getSource() == okButton) {
        accept();
      }
    }

    // --------------------- End Interface ActionListener ---------------------

    // --------------------- Begin Interface TreeSelectionListener ---------------------

    @Override
    public void valueChanged(TreeSelectionEvent e) {
      if (e.getSource() == categoryTree) {
        // Shows the settings panel associated with the tree node
        if (e.getNewLeadSelectionPath() != null &&
            e.getNewLeadSelectionPath().getLastPathComponent() instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.getNewLeadSelectionPath().getLastPathComponent();
          if (node.getUserObject() instanceof Category) {
            showSettingsPanel((Category)node.getUserObject());
          }
        }
      }
    }

    // --------------------- End Interface TreeSelectionListener ---------------------
  }

  /**
   * A general-purpose class for associating a text string with custom data.
   *
   * @param <E> Type of the data element.
   */
  private static class DataItem<E> {
    private String label;
    private E data;

    public DataItem(String label, E data) {
      this.label = label;
      this.data = data;
    }

    public String getLabel() {
      if (label != null) {
        return label;
      } else if (data != null) {
        return data.toString();
      } else {
        return "(null)";
      }
    }

    public DataItem<E> setLabel(String label) {
      this.label = label;
      return this;
    }

    public E getData() {
      return data;
    }

    public DataItem<E> setData(E data) {
      this.data = data;
      return this;
    }

    @Override
    public String toString() {
      return getLabel();
    }

    @Override
    public int hashCode() {
      return Objects.hash(data, label);
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
      DataItem<?> other = (DataItem<?>) obj;
      return Objects.equals(data, other.data) && Objects.equals(label, other.label);
    }
  }

  /**
   * Specialized version of the {@link DefaultListCellRenderer} for the "Global Text Font" option list.
   */
  public static class FontListCellRenderer extends DefaultListCellRenderer {
    public FontListCellRenderer() {
      super();
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
        boolean cellHasFocus) {
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      DataItem<?> fontInfo = (DataItem<?>) value;
      if (fontInfo != null && fontInfo.getData() instanceof Font) {
        final Font oldFont = label.getFont();
        final Font newFont = (Font) fontInfo.getData();
        label.setFont(Misc.getScaledFont(newFont.deriveFont(oldFont.getSize2D())));
        label.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      }

      return label;
    }
  }
}
