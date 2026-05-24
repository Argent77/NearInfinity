// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.folding.CurlyFoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.infinity.NearInfinity;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.TextResource;
import org.infinity.resource.key.Keyfile;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.text.modes.BCSFoldParser;
import org.infinity.resource.text.modes.BCSTokenMaker;
import org.infinity.resource.text.modes.GLSLTokenMaker;
import org.infinity.resource.text.modes.INITokenMaker;
import org.infinity.resource.text.modes.MenuTokenMaker;
import org.infinity.resource.text.modes.TLKTokenMaker;
import org.infinity.resource.text.modes.WeiDULogTokenMaker;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.StringTable;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;

/**
 * Extends {@link RSyntaxTextArea} by NearInfinity-specific features.
 */
public class InfinityTextArea extends RSyntaxTextArea
    implements ActionListener, ChangeListener, KeyListener, HierarchyListener, PopupMenuListener {
  /** Available languages for syntax highlighting. */
  public enum Language {
    /** Disables syntax highlighting */
    NONE(SyntaxConstants.SYNTAX_STYLE_NONE),
    /** Select BCS highlighting. */
    BCS(BCSTokenMaker.SYNTAX_STYLE_BCS),
    /** Select DLG tree viewer trigger/action highlighting. */
    DLG(BCSTokenMaker.SYNTAX_STYLE_BCS),
    /** Select TLK highlighting. */
    TLK(TLKTokenMaker.SYNTAX_STYLE_TLK),
    /** Select GLSL highlighting. */
    GLSL(GLSLTokenMaker.SYNTAX_STYLE_GLSL),
    /** Select INI highlighting. */
    INI(INITokenMaker.SYNTAX_STYLE_INI),
    /** Select LUA highlighting. */
    LUA(SyntaxConstants.SYNTAX_STYLE_LUA),
    /** Select MENU highlighting. */
    MENU(MenuTokenMaker.SYNTAX_STYLE_MENU),
    /** Select SQL highlighting. */
    SQL(SyntaxConstants.SYNTAX_STYLE_SQL),
    /** Select WeiDU.log highlighting. */
    WEIDU(WeiDULogTokenMaker.SYNTAX_STYLE_WEIDU),
    ;

    private final String style;

    Language(String style) {
      this.style = style;
    }

    /** Returns the syntax highlighting style definition. */
    public String getStyle() {
      return style;
    }
  }

  /** Available color schemes for use when enabling syntax highlighting. */
  public enum Scheme {
    /** Disables any color scheme. */
    NONE("None", InfinityTextArea::getNoneScheme),
    /** The default color scheme. */
    DEFAULT("Default", () -> SCHEME_DEFAULT),
    /** Color scheme based on Notepad++'s Obsidian scheme. */
    DARK("Dark", () -> SCHEME_DARK),
    /** A dark green Color scheme. */
    DRUID("Druid", () -> SCHEME_DRUID),
    /** Color scheme based on Eclipse's defaults. */
    ECLIPSE("Eclipse", () -> SCHEME_ECLIPSE),
    /** Color scheme based on IntelliJ IDEA's defaults. */
    IDEA("IntelliJ IDEA", () -> SCHEME_IDEA),
    /** A dark color scheme inspired by "Monokai". */
    MONOKAI("Monokai", () -> SCHEME_MONOKAI),
    /** Color scheme based on Microsoft Visual Studio's defaults. */
    VS("Visual Studio", () -> SCHEME_VS),
    /** Color scheme loosely based on the WeiDU Syntax Highlighter for Notepad++. */
    BCS("BCS Light", () -> SCHEME_BCS),
    /** Color scheme loosely based on the WeiDU Syntax Highlighter for Notepad++. */
    BCS_DARK("BCS Dark", () -> SCHEME_BCS_DARK),
    ;

    private final String label;

    // Stored as functional interface to react to dark/light UI theme changes
    private final Supplier<String> scheme;

    Scheme(String label, Supplier<String> scheme) {
      this.label = label;
      this.scheme = scheme;
    }

    /** Returns a descriptive label for the color scheme. */
    public String getLabel() {
      return label;
    }

    /** Returns the path to the color scheme. */
    public String getScheme() {
      return scheme.get();
    }
  }

  /** The default color of the currently highlighted text line. */
  public static final Color DEFAULT_LINE_HIGHLIGHT_COLOR = new Color(0xe8e8ff);

  /** Identifies a menu item from the "open resource" menu. */
  private static final String CMD_OPEN_RESOURCE = "OpenResource";
  /** Identifies a menu item from the "open ids" menu. */
  private static final String CMD_OPEN_IDS      = "OpenIds";
  /** Identifies a menu item from the "open strref" menu. */
  private static final String CMD_OPEN_STRREF   = "OpenStrref";

  /** Key for client property to retrieve a resource reference {@code String}. */
  private static final String PROP_RESREF = "resref";
  /** Key for client property to retrieve a string reference {@code Integer}. */
  private static final String PROP_STRREF = "strref";
  /** Key for client property to retrieve a numeric IDS {@code Integer}. */
  private static final String PROP_IDS = "ids";

  /** Format string for an "open resource" menu item label. */
  private static final String OPEN_RESOURCE_LABEL_FMT     = "Open \"%s\" as resource...";
  /** Default string for "open resource" menu item label. */
  private static final String OPEN_RESOURCE_LABEL_DEFAULT = "Open as resource...";

  /** Format string for an "open ids reference" menu item label. */
  private static final String OPEN_IDS_LABEL_FMT        = "Open \"%d\" as IDS reference...";
  /** Default string for "open ids reference" menu item label. */
  private static final String OPEN_IDS_LABEL_DEFAULT    = "Open as IDS reference...";

  /** Format string for an "open strref" menu item label. */
  private static final String OPEN_STRREF_LABEL_FMT     = "Open string reference %d ...";
  /** Default string for "open strref" menu item label. */
  private static final String OPEN_STRREF_LABEL_DEFAULT = "Open string reference...";

  /** Color scheme for unicolored text */
  private static final String SCHEME_NONE       = "org/infinity/resource/text/modes/ThemeNone.xml";
  /** Dark color scheme for unicolored text */
  private static final String SCHEME_NONE_DARK  = "org/infinity/resource/text/modes/ThemeNoneDark.xml";
  /** Default color scheme */
  private static final String SCHEME_DEFAULT    = "org/infinity/resource/text/modes/ThemeDefault.xml";
  /** Dark color scheme */
  private static final String SCHEME_DARK       = "org/infinity/resource/text/modes/ThemeDark.xml";
  /** Druid color scheme */
  private static final String SCHEME_DRUID      = "org/infinity/resource/text/modes/ThemeDruid.xml";
  /** Eclipse color scheme */
  private static final String SCHEME_ECLIPSE    = "org/infinity/resource/text/modes/ThemeEclipse.xml";
  /** IntelliJ IDEA color scheme */
  private static final String SCHEME_IDEA       = "org/infinity/resource/text/modes/ThemeIdea.xml";
  /** "Monokai" color scheme */
  private static final String SCHEME_MONOKAI    = "org/infinity/resource/text/modes/ThemeMonokai.xml";
  /** Visual Studio color scheme */
  private static final String SCHEME_VS         = "org/infinity/resource/text/modes/ThemeVs.xml";
  /** BCS color scheme based on WeiDU Highlighter for Notepad++ */
  private static final String SCHEME_BCS        = "org/infinity/resource/text/modes/ThemeBCSLight.xml";
  /** BCS color scheme based on the dark WeiDU Highlighter for Notepad++ */
  private static final String SCHEME_BCS_DARK   = "org/infinity/resource/text/modes/ThemeBCSDark.xml";

  /** IDS resources to ignore when building the IDS reference list. */
  private static final HashSet<String> IDS_BLACKLIST = new HashSet<>(Arrays.asList(
      "ACTION.IDS", "ACTSLEEP.IDS", "ANISND.IDS", "CHASE.IDS", "DLGINST.IDS", "INSTANT.IDS", "NODECODE.IDS",
      "PREFAB.IDS", "SCRINST.IDS", "SVTRIOBJ.IDS", "TRIGGER.IDS"));

  static {
    // adding custom code folding definitions
    FoldParserManager.get().addFoldParserMapping(BCSTokenMaker.SYNTAX_STYLE_BCS, new BCSFoldParser());
    FoldParserManager.get().addFoldParserMapping(GLSLTokenMaker.SYNTAX_STYLE_GLSL, new CurlyFoldParser());

    // adding custom syntax highlighting definitions
    ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance()).putMapping(BCSTokenMaker.SYNTAX_STYLE_BCS,
        BCSTokenMaker.class.getCanonicalName());
    ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance()).putMapping(TLKTokenMaker.SYNTAX_STYLE_TLK,
        TLKTokenMaker.class.getCanonicalName());
    ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance()).putMapping(WeiDULogTokenMaker.SYNTAX_STYLE_WEIDU,
        WeiDULogTokenMaker.class.getCanonicalName());
    ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance()).putMapping(GLSLTokenMaker.SYNTAX_STYLE_GLSL,
        GLSLTokenMaker.class.getCanonicalName());
    ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance()).putMapping(INITokenMaker.SYNTAX_STYLE_INI,
        INITokenMaker.class.getCanonicalName());
    ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance()).putMapping(MenuTokenMaker.SYNTAX_STYLE_MENU,
        MenuTokenMaker.class.getCanonicalName());
  }

  private final SortedMap<Integer, GutterIcon> gutterIcons = new TreeMap<>();
  private final Map<Integer, GutterIconInfo> gutterIconsActive = new HashMap<>();
  private final HashMap<KeyStroke, Action> inputActionMap = new HashMap<>();
  private final JPopupMenu.Separator menuOpenEntrySeparator = new JPopupMenu.Separator();
  private final JMenu menuOpenResource = new JMenu(OPEN_RESOURCE_LABEL_DEFAULT);
  private final JMenu menuOpenIds = new JMenu(OPEN_IDS_LABEL_DEFAULT);
  private final JMenuItem menuOpenStrref = new JMenuItem(OPEN_STRREF_LABEL_DEFAULT);

  private RTextScrollPane scrollPane;
  private List<JMenuItem> resourceTypeItems;
  private List<JMenuItem> idsFileItems;
  private JPopupMenu popupMenu;
  private boolean openResrefEnabled;
  private boolean openIdsEnabled;
  private boolean openStrrefEnabled;

  /**
   * Constructor.
   *
   * @param applySettings If {@code true}, applies global text editor settings to this component.
   */
  public InfinityTextArea(boolean applySettings) {
    super();
    if (applySettings) {
      applySettings(true);
      applyExtendedSettings(null, null);
    }
    setFont(getGlobalFont());
  }

  /**
   * Constructor.
   *
   * @param doc           The document for the editor.
   * @param applySettings If {@code true}, applies global text editor settings to this component.
   */
  public InfinityTextArea(RSyntaxDocument doc, boolean applySettings) {
    super(doc);
    if (applySettings) {
      applySettings(true);
      applyExtendedSettings(null, null);
    }
    setFont(getGlobalFont());
  }

  /**
   * Constructor.
   *
   * @param text          The initial text to display.
   * @param applySettings If {@code true}, applies global text editor settings to this component.
   */
  public InfinityTextArea(String text, boolean applySettings) {
    super(text);
    if (applySettings) {
      applySettings(true);
      applyExtendedSettings(null, null);
    }
    setFont(getGlobalFont());
  }

  /**
   * Constructor.
   *
   * @param textMode      Either {@code INSERT_MODE} or {@code OVERWRITE_MODE}.
   * @param applySettings If {@code true}, applies global text editor settings to this component.
   */
  public InfinityTextArea(int textMode, boolean applySettings) {
    super(textMode);
    if (applySettings) {
      applySettings(true);
      applyExtendedSettings(null, null);
    }
    setFont(getGlobalFont());
  }

  /**
   * Constructor.
   *
   * @param rows          The number of rows to display.
   * @param cols          The number of columns to display.
   * @param applySettings If {@code true}, applies global text editor settings to this component.
   * @throws IllegalArgumentException If either {@code rows} or {@code cols} is negative.
   */
  public InfinityTextArea(int rows, int cols, boolean applySettings) {
    super(rows, cols);
    if (applySettings) {
      applySettings(true);
      applyExtendedSettings(null, null);
    }
    setFont(getGlobalFont());
  }

  /**
   * Constructor.
   *
   * @param text          The initial text to display.
   * @param rows          The number of rows to display.
   * @param cols          The number of columns to display.
   * @param applySettings If {@code true}, applies global text editor settings to this component.
   * @throws IllegalArgumentException If either {@code rows} or {@code cols} is negative.
   */
  public InfinityTextArea(String text, int rows, int cols, boolean applySettings) {
    super(text, rows, cols);
    if (applySettings) {
      applySettings(true);
      applyExtendedSettings(null, null);
    }
    setFont(getGlobalFont());
  }

  /**
   * Constructor.
   *
   * @param doc           The document for the editor.
   * @param text          The initial text to display.
   * @param rows          The number of rows to display.
   * @param cols          The number of columns to display.
   * @param applySettings If {@code true}, applies global text editor settings to this component.
   * @throws IllegalArgumentException If either {@code rows} or {@code cols} is negative.
   */
  public InfinityTextArea(RSyntaxDocument doc, String text, int rows, int cols, boolean applySettings) {
    super(doc, text, rows, cols);
    if (applySettings) {
      applySettings(true);
      applyExtendedSettings(null, null);
    }
    setFont(getGlobalFont());
  }

  /**
   * Applies global text editor settings to the specified {@link RSyntaxTextArea} component.
   *
   * @param resetUndo Specifies whether the undo history will be discarded.
   */
  public static void applySettings(InfinityTextArea edit, boolean resetUndo) {
    if (edit != null) {
      // Allows key strokes defined by a parent components to be processed when this component has focus
      edit.addKeyListener(edit);
      edit.addHierarchyListener(edit);

      edit.setCurrentLineHighlightColor(DEFAULT_LINE_HIGHLIGHT_COLOR);
      if (BrowserMenuBar.isInstantiated()) {
        edit.setTabsEmulated(BrowserMenuBar.getInstance().getOptions().isTextTabEmulated());
        edit.setTabSize(BrowserMenuBar.getInstance().getOptions().getTextTabSize());
        edit.setWhitespaceVisible(BrowserMenuBar.getInstance().getOptions().getTextWhitespaceVisible());
        edit.setEOLMarkersVisible(BrowserMenuBar.getInstance().getOptions().getTextEOLVisible());
        edit.setHighlightCurrentLine(BrowserMenuBar.getInstance().getOptions().getTextHighlightCurrentLine());
      } else {
        // default settings
        edit.setTabsEmulated(false);
        edit.setTabSize(4);
        edit.setWhitespaceVisible(false);
        edit.setEOLMarkersVisible(false);
        edit.setHighlightCurrentLine(false);
      }
      if (resetUndo) {
        edit.discardAllEdits(); // clearing undo history
      }
    }
  }

  /**
   * Applies syntax highlighting and color schemes to the specified {@link RSyntaxTextArea} component.
   *
   * @param language The language to highlight. Specifying {@code null} uses {@code Language.NONE} for syntax
   *                 hightlighting.
   * @param scheme   A color scheme to apply to the specified {@link RSyntaxTextArea} component. Specifying {@code null}
   *                 uses the color scheme as defined for the specified language.
   */
  public static void applyExtendedSettings(RSyntaxTextArea edit, Language language, Scheme scheme) {
    if (edit != null) {
      if (language == null) {
        language = Language.NONE;
      }

      // applying syntax highlighting
      edit.setSyntaxEditingStyle(language.getStyle());

      // applying color scheme
      String schemePath;
      if (scheme != null) {
        // applying explicit color scheme
        schemePath = scheme.getScheme();
      } else {
        // applying implicit color scheme
        schemePath = getNoneScheme();
        switch (language) {
          case BCS:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getBcsColorScheme();
            }
            break;
          case DLG:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getDlgColorScheme();
            }
            break;
          case TLK:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getTlkColorScheme();
            }
            break;
          case GLSL:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getGlslColorScheme();
            }
            break;
          case INI:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getIniColorScheme();
            }
            break;
          case LUA:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getLuaColorScheme();
            }
            break;
          case MENU:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getMenuColorScheme();
            }
            break;
          case SQL:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getSqlColorScheme();
            }
            break;
          case WEIDU:
            if (BrowserMenuBar.isInstantiated()) {
              schemePath = BrowserMenuBar.getInstance().getOptions().getWeiDUColorScheme();
            }
            break;
          default:
        }
      }

      if (schemePath != null) {
        try (InputStream is = ClassLoader.getSystemResourceAsStream(schemePath)) {
          Theme theme = Theme.load(is);
          theme.apply(edit);
        } catch (NullPointerException e) {
          // ignore
        } catch (IOException e) {
          Logger.error(e);
        }
      }

      // apply code folding
      switch (language) {
        case BCS:
          if (BrowserMenuBar.isInstantiated()) {
            edit.setCodeFoldingEnabled(BrowserMenuBar.getInstance().getOptions().getBcsCodeFoldingEnabled());
          } else {
            edit.setCodeFoldingEnabled(false);
          }
          break;
        case GLSL:
          if (BrowserMenuBar.isInstantiated()) {
            edit.setCodeFoldingEnabled(BrowserMenuBar.getInstance().getOptions().getGlslCodeFoldingEnabled());
          } else {
            edit.setCodeFoldingEnabled(false);
          }
          break;
        default:
          edit.setCodeFoldingEnabled(false);
      }
    }
  }

  /** Returns the color scheme when syntax highlighting is disabled. */
  private static String getNoneScheme() {
    if (NearInfinity.getInstance() != null) {
      return NearInfinity.getInstance().isDarkMode() ? SCHEME_NONE_DARK : SCHEME_NONE;
    }
    return SCHEME_NONE;
  }

  /**
   * Applies global text editor settings to this component.
   *
   * @param resetUndo Specifies whether the undo history will be discarded.
   */
  public void applySettings(boolean resetUndo) {
    applySettings(this, resetUndo);
  }

  /**
   * Applies syntax highlighting and color schemes to this component.
   *
   * @param language The language to highlight. Specifying {@code null} uses {@code Language.NONE} for syntax
   *                 hightlighting.
   * @param scheme   A color scheme to apply to the specified {@link RSyntaxTextArea} component. Specifying {@code null}
   *                 uses the color scheme as defined for the specified language.
   */
  public void applyExtendedSettings(Language language, Scheme scheme) {
    applyExtendedSettings(this, language, scheme);
  }

  /** Returns whether the context menu should display an option to open selected text as a game resource. */
  public boolean isOpenResrefEnabled() {
    return openResrefEnabled;
  }

  /** Specifies whether the context menu should display an option to open selected text as a game resource. */
  public void setOpenResrefEnabled(boolean b) {
    if (b != openResrefEnabled) {
      openResrefEnabled = b;
      if (openResrefEnabled) {
        initOpenResource();
      }
      menuOpenEntrySeparator.setVisible(isOpenEntryEnabled());
      menuOpenResource.setVisible(openResrefEnabled);
    }
  }

  /** Returns whether the context menu should display an option to open selected text as an IDS reference. */
  public boolean isOpenIdsEnabled() {
    return openIdsEnabled;
  }

  /** Specifies whether the context menu should display an option to open selected text as an IDS reference. */
  public void setOpenIdsEnabled(boolean b) {
    if (b != openIdsEnabled) {
      openIdsEnabled = b;
      if (openIdsEnabled) {
        initOpenIds();
      }
      menuOpenEntrySeparator.setVisible(isOpenEntryEnabled());
      menuOpenIds.setVisible(openIdsEnabled);
    }
  }

  /** Returns whether the context menu should display an option to open selected text as a string reference. */
  public boolean isOpenStrrefEnabled() {
    return openStrrefEnabled;
  }

  /** Specifies whether the context menu should display an option to open selected text as a string reference. */
  public void setOpenStrrefEnabled(boolean b) {
    if (b != openStrrefEnabled) {
      openStrrefEnabled = b;
      menuOpenEntrySeparator.setVisible(isOpenEntryEnabled());
      menuOpenStrref.setVisible(openStrrefEnabled);
    }
  }

  @Override
  public void setText(String text) {
    // skips carriage return characters
    if (text != null) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c != '\r') {
          sb.append(c);
        }
      }
      super.setText(sb.toString());
    } else {
      super.setText(null);
    }
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e) {
    if (CMD_OPEN_RESOURCE.equals(e.getActionCommand())) {
      final JMenuItem item = (JMenuItem)e.getSource();
      final Object o = item.getClientProperty(PROP_RESREF);
      if (o instanceof String && !((String)o).isEmpty()) {
        final String resref = (String)o;
        final String resName = resref + '.' + item.getText();
        final ResourceEntry entry = ResourceFactory.getResourceEntry(resName);
        if (entry != null) {
          new ViewFrame(getTopLevelAncestor(), ResourceFactory.getResource(entry));
        }
      }
    } else if (CMD_OPEN_IDS.equals(e.getActionCommand())) {
      final JMenuItem item = (JMenuItem)e.getSource();
      final Object o = item.getClientProperty(PROP_IDS);
      final String idsName = item.getText();
      final ResourceEntry entry = ResourceFactory.getResourceEntry(idsName);
      if (o instanceof Number && entry != null) {
        final int ids = ((Number)o).intValue();
        final Resource res = ResourceFactory.getResource(entry);
        new ViewFrame(getTopLevelAncestor(), res);
        if (res instanceof TextResource) {
          highlightIdsEntry((TextResource) res, ids);
        }
      }
    } else if (CMD_OPEN_STRREF.equals(e.getActionCommand())) {
      final JMenuItem item = (JMenuItem)e.getSource();
      final Object o = item.getClientProperty(PROP_STRREF);
      if (o instanceof Number && ((Number)o).intValue() >= 0) {
        final int strref = ((Number)o).intValue();
        final StringLookup lookup = ChildFrame.show(StringLookup.class, StringLookup::new);
        if (lookup != null) {
          lookup.setStrref(strref);
        }
      }
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent e) {
    if (e.getSource() instanceof JViewport) {
      refreshGutterIcons();
    }
  }

  // --------------------- End Interface ChangeListener ---------------------

  // --------------------- Begin Interface KeyListener ---------------------

  @Override
  public void keyTyped(KeyEvent e) {
  }

  @Override
  public void keyPressed(KeyEvent e) {
    // Processing key strokes defined in parent components.
    // Registration of key strokes is handled by the HierarchyListener.
    final KeyStroke keyStroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    final Action action = inputActionMap.get(keyStroke);
    if (action != null) {
      action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
      e.consume();
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
  }

  // --------------------- End Interface KeyListener ---------------------

  // --------------------- Begin Interface HierarchyListener ---------------------

  @Override
  public void hierarchyChanged(HierarchyEvent e) {
    // InfinityTextArea appears to discard or override certain global key stroke definitions.
    // This method registers key strokes from parent components, so that they can still be processed.
    // Registration process is placed into a HierarchyListener to register key strokes even if
    // parent components are assigned later.
    if (!(e.getChanged() instanceof JComponent && getTopLevelAncestor() instanceof JFrame)) {
      return;
    }

    final JRootPane rootPane = ((JFrame) getTopLevelAncestor()).getRootPane();
    if (rootPane == null) {
      return;
    }

    final InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    final KeyStroke[] keyStrokes = inputMap.allKeys();
    final ActionMap actionMap = rootPane.getActionMap();
    if (inputMap == null || keyStrokes == null || keyStrokes.length == 0 || actionMap == null) {
      return;
    }

    for (final KeyStroke keyStroke : keyStrokes) {
      final Object binding = inputMap.get(keyStroke);
      if (binding != null) {
        final Action action = actionMap.get(binding);
        if (action != null) {
          inputActionMap.put(keyStroke, action);
        }
      }
    }
  }

  // --------------------- End Interface HierarchyListener ---------------------

  // --------------------- Begin Interface PopupMenuListener ---------------------

  @Override
  public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
    if (isOpenResrefEnabled()) {
      initResrefPopupMenu();
    }

    if (isOpenIdsEnabled()) {
      initIdsPopupMenu();
    }

    if (isOpenStrrefEnabled()) {
      initStrrefPopupMenu();
    }
  }

  @Override
  public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
    if (isOpenResrefEnabled()) {
      clearResrefPopupMenu();
    }

    if (isOpenIdsEnabled()) {
      clearIdsPopupMenu();
    }
  }

  @Override
  public void popupMenuCanceled(PopupMenuEvent e) {
  }

  // --------------------- End Interface PopupMenuListener ---------------------

  /**
   * Returns the underlying ScrollPane if available. Returns {@code null} otherwise.
   */
  public RTextScrollPane getScrollPane() {
    return scrollPane;
  }

  /**
   * Can be used to set the underlying ScrollPane instance.
   */
  public void setScrollPane(RTextScrollPane scrollPane) {
    if (scrollPane != this.scrollPane) {
      if (this.scrollPane != null) {
        this.scrollPane.getViewport().removeChangeListener(this);
      }

      this.scrollPane = scrollPane;
      if (this.scrollPane != null) {
        this.scrollPane.getViewport().addChangeListener(this);
        this.scrollPane.setIconRowHeaderEnabled(true);
        refreshGutterIcons();
      }
    }
  }

  /** Adds a new icon to the left vertical margin, optionally with a tooltip. */
  public void addGutterIcon(int line, Icon icon, String msg) {
    GutterIcon item = gutterIcons.get(line);
    if (item == null) {
      item = new GutterIcon(line, icon, msg);
      gutterIcons.put(line, item);
    } else {
      item.icon = icon;
      item.message = msg;
    }
    refreshGutterIcon(line);
  }

  /** Get information about the gutter icon at the specified line. */
  public GutterIcon getGutterIconInfo(int line) {
    return gutterIcons.get(line);
  }

  /** Returns whether the gutter icon at the specified line is currently applied. */
  public boolean isGutterIconActive(int line) {
    return gutterIconsActive.containsKey(line);
  }

  /** Removes the gutter icon from the specified line. */
  public void removeGutterIcon(int line) {
    if (gutterIcons.remove(line) != null) {
      refreshGutterIcon(line);
    }
  }

  /** Removes all gutter icons at once. */
  public void clearGutterIcons() {
    if (getScrollPane() != null) {
      Gutter gutter = getScrollPane().getGutter();
      Iterator<Integer> iter = gutterIconsActive.keySet().iterator();
      while (iter.hasNext()) {
        Integer key = iter.next();
        GutterIconInfo info = gutterIconsActive.get(key);
        if (info != null) {
          gutter.removeTrackingIcon(info);
        }
        iter.remove();
      }
    }
    gutterIcons.clear();
  }

  /**
   * Adds a highlight to the selected text region.
   *
   * @param start Start position of the highlighted region.
   * @param end   End position of the highlighted region.
   * @return A tag that can be used to clear the highlighted region (see {@link #clearHighlight(Object)}). Returns
   *         {@code null} if highlighting could not be applied.
   */
  public Object highlightText(int start, int end) {
    if (start < 0) {
      end += start;
      start = 0;
    }

    final int textSize = getText().length();
    end = Math.min(textSize, end);
    if (start >= textSize || end <= start) {
      return null;
    }

    final Highlighter hilite = getHighlighter();
    final Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(getSelectionColor());
    try {
      return hilite.addHighlight(start, end, painter);
    } catch (BadLocationException e) {
      Logger.error(e);
    }

    return null;
  }

  /**
   * Removes the specified highlighted region from the text component.
   *
   * @param tag A tag that identifies the highlighted region (see {@link #highlightText(int, int)}). Specify
   *              {@code null} to clear all highlighted regions in the text component.
   */
  public void clearHighlight(Object tag) {
    final Highlighter hilite = getHighlighter();
    if (tag == null) {
      hilite.removeAllHighlights();
    } else {
      hilite.removeHighlight(tag);
    }
  }

  /** Returns the currently min/max visible line numbers. */
  protected Point getVisibleLineRange(Point range) {
    if (range == null) {
      range = new Point(-1, -1);
    } else {
      range.x = range.y = -1;
    }

    RTextScrollPane pane = getScrollPane();
    if (pane != null) {
      Rectangle view = pane.getViewport().getViewRect();
      Point pt = view.getLocation();
      try {
        int startLine = getLineOfOffset(viewToModel(pt));
        pt.y += view.height;
        int endLine = getLineOfOffset(viewToModel(pt));
        range.x = startLine;
        range.y = endLine;
      } catch (BadLocationException e) {
        Logger.trace(e);
      }
    }

    return range;
  }

  @Override
  protected JPopupMenu createPopupMenu() {
    if (popupMenu == null) {
      popupMenu = super.createPopupMenu();

      // initializing "Open ... as" menu additions
      menuOpenEntrySeparator.setVisible(isOpenEntryEnabled());

      menuOpenStrref.addActionListener(this);
      menuOpenStrref.setActionCommand(CMD_OPEN_STRREF);
      menuOpenStrref.setVisible(openStrrefEnabled);

      menuOpenIds.setVisible(openIdsEnabled);

      menuOpenResource.setVisible(openResrefEnabled);

      popupMenu.add(menuOpenEntrySeparator);
      popupMenu.add(menuOpenStrref);
      popupMenu.add(menuOpenIds);
      popupMenu.add(menuOpenResource);
      popupMenu.addPopupMenuListener(this);
    }
    return popupMenu;
  }

  private void refreshGutterIcon(int line) {
    if (getScrollPane() != null) {
      Integer key = line;
      gutterIconsActive.remove(key);
      GutterIcon item = gutterIcons.get(key);
      if (item != null) {
        Point range = getVisibleLineRange(null);
        if (item.line >= range.x && item.line <= range.y) {
          try {
            GutterIconInfo info = getScrollPane().getGutter().addLineTrackingIcon(item.line, item.icon, item.message);
            gutterIconsActive.put(key, info);
          } catch (BadLocationException e) {
            Logger.trace(e);
          }
        }
      }
    }
  }

  private void refreshGutterIcons() {
    if (getScrollPane() != null && !gutterIcons.isEmpty()) {
      Gutter gutter = getScrollPane().getGutter();
      Point range = getVisibleLineRange(null);
      if (range.x >= 0) {
        // 1. remove items outside of range
        Iterator<Integer> iter = gutterIconsActive.keySet().iterator();
        while (iter.hasNext()) {
          Integer key = iter.next();
          int line = key;
          if (line < range.x || line > range.y) {
            GutterIconInfo info = gutterIconsActive.get(key);
            gutter.removeTrackingIcon(info);
            iter.remove();
          }
        }

        // 2. add items that are inside of range
        SortedMap<Integer, GutterIcon> submap = gutterIcons.subMap(range.x, range.y + 1);
        if (!submap.isEmpty()) {
          for (final Integer key : submap.keySet()) {
            if (!gutterIconsActive.containsKey(key)) {
              GutterIcon item = submap.get(key);
              try {
                GutterIconInfo info = gutter.addLineTrackingIcon(item.line, item.icon, item.message);
                gutterIconsActive.put(item.line, info);
              } catch (BadLocationException e) {
                Logger.trace(e);
              }
            }
          }
        }
      }
    }
  }

  // Returns scaled global font
  private Font getGlobalFont() {
    Font f = BrowserMenuBar.isInstantiated() ? BrowserMenuBar.getInstance().getOptions().getScriptFont() : getFont();
    if (f != null) {
      f = Misc.getScaledFont(f);
    }
    return f;
  }

  // Called lazily to initialize list of potential game resource types
  private void initOpenResource() {
    if (resourceTypeItems == null) {
      resourceTypeItems = new ArrayList<>();
    }

    if (resourceTypeItems.isEmpty() && Profile.isGameOpen()) {
      final String[] types = Profile.getAvailableResourceTypes();
      final Keyfile keyFile = ResourceFactory.getKeyfile();
      for (final String type : types) {
        final ImageIcon icon = (keyFile != null) ? keyFile.getIcon(type) : null;
        final JMenuItem item = new JMenuItem(type, icon);
        item.addActionListener(this);
        item.setActionCommand(CMD_OPEN_RESOURCE);
        resourceTypeItems.add(item);
      }
    }
  }

  // Called lazily to initialize list of potential IDS resources
  private void initOpenIds() {
    if (idsFileItems == null) {
      idsFileItems = new ArrayList<>();
    }

    if (idsFileItems.isEmpty() && Profile.isGameOpen()) {
      final List<ResourceEntry> idsFiles = ResourceFactory.getResources("IDS");
      for (final ResourceEntry entry : idsFiles) {
        if (!IDS_BLACKLIST.contains(entry.getResourceName().toUpperCase(Locale.ROOT))) {
          IdsMapCache.get(entry.getResourceName()); // pre-caching IDS resource
          final JMenuItem item = new JMenuItem(entry.getResourceName());
          item.addActionListener(this);
          item.setActionCommand(CMD_OPEN_IDS);
          idsFileItems.add(item);
        }
      }
    }
  }

  /** Returns whether any of the "open xxx" menu entries have been enabled. */
  private boolean isOpenEntryEnabled() {
    return openResrefEnabled || openIdsEnabled || openStrrefEnabled;
  }

  /**
   * Selects the line in the IDS text resource that corresponds with the specified number.
   *
   * @param text   {@link TextResource} of the IDS resource.
   * @param number IDS value.
   * @return {@code true} if a matching line was found and highlighted, {@code false} otherwise.
   */
  private boolean highlightIdsEntry(TextResource text, int number) {
    final long value = number & 0xffffffffL;
    final Pattern patDec = Pattern.compile("^[ \t]*0*" + value + "[ \t]+.+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    final Pattern patHex = Pattern.compile("^[ \t]*0x0*" + Long.toHexString(value) + "[ \t]+.+",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    final String[] lines = text.getText().split("\r?\n");
    for (int i = 0; i < lines.length; i++) {
      Matcher m = patDec.matcher(lines[i]);
      if (m.matches()) {
        text.highlightText(i + 1, null);
        return true;
      }
      m = patHex.matcher(lines[i]);
      if (m.matches()) {
        text.highlightText(i + 1, null);
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the current text selection. If no text is selected then the word under the text cursor is returned.
   *
   * @return Returns the current selection or word under the text cursor. Returns empty string if word could not be
   *         determined.
   */
  private String getTextWord() {
    // invalid characters for potential resref or strref strings
    final String invalid = "*=:.,;\"'[]()/|\\?<>`";

    String text = getSelectedText();
    if (text == null || text.isEmpty()) {
      try {
        final int startOfs = getLineStartOffsetOfCurrentLine();
        final int endOfs = getLineEndOffsetOfCurrentLine();
        final String line = getText(startOfs, endOfs - startOfs);

        int curStartOfs = getCaretPosition() - startOfs;
        while (curStartOfs >= 0 && curStartOfs < line.length()) {
          final char ch = line.charAt(curStartOfs);
          if (Character.isWhitespace(ch) || invalid.indexOf(ch) >= 0) {
            curStartOfs++;
            break;
          }
          curStartOfs--;
        }
        curStartOfs = Math.max(0, curStartOfs);

        int curEndOfs = getCaretPosition() - startOfs;
        while (curEndOfs < endOfs && curEndOfs < line.length()) {
          final char ch = line.charAt(curEndOfs);
          if (Character.isWhitespace(ch) || invalid.indexOf(ch) >= 0) {
            break;
          }
          curEndOfs++;
        }

        if (curEndOfs > curStartOfs) {
          text = line.substring(curStartOfs, curEndOfs).trim();
        }
      } catch (BadLocationException e) {
      }
    }

    if (text == null) {
      text = "";
    }

    return text;
  }

  /**
   * Captures the currently selected text or determines the word under the caret and transforms it into a valid numeric
   * value if possible.
   *
   * @param includeVirtual Whether to consider virtual strref values.
   * @return String reference if available, -1 otherwise.
   */
  private int getNumberFromText(boolean includeVirtual) {
    return getNumberFromText(getTextWord(), includeVirtual);
  }

  /**
   * Attempts to convert the given string into a valid numeric value.
   *
   * @param text           Text to convert.
   * @param includeVirtual Whether to consider virtual strref values.
   * @return String reference if available, -1 otherwise.
   */
  private int getNumberFromText(String text, boolean includeVirtual) {
    long retVal = -1L;
    if (text != null) {
      final Matcher m = Pattern.compile("-?\\b((0x[0-9a-fA-F]+)|(\\d+))\\b").matcher(text);
      if (m.find()) {
        final String group = m.group();
        try {
          if (group.contains("0x")) {
            retVal = Long.parseLong(group.replace("0x", ""), 16);
          } else {
            retVal = Long.parseLong(group);
          }
          if (includeVirtual) {
            final Table2da vtable = Table2daCache.get("ENGINEST.2DA");
            final int numVirtual = (vtable != null) ? vtable.getRowCount() : 0;
            boolean valid = (retVal >= StringTable.STRREF_VIRTUAL && retVal < StringTable.STRREF_VIRTUAL + numVirtual)
                || (retVal >= 0 && retVal < StringTable.getNumEntries());
            if (!valid) {
              retVal = -1L;
            }
          }
        } catch (NumberFormatException e) {
        }
      }
    }

    return (int)retVal;
  }

  /**
   * Captures the currently selected text or determines the word under the caret and transforms it into a valid resource
   * resref string if possible.
   *
   * @return Resource reference string if available, empty string otherwise.
   */
  private String[] getResrefFromText() {
    return getResrefFromText(getTextWord());
  }

  /**
   * Returns the processed string if it contains a valid resource resref string.
   *
   * @param text Text to process.
   * @return Array with one or more variations of the detected resource reference string if available, empty strings
   *         otherwise.
   */
  private String[] getResrefFromText(String text) {
    String resref = "";
    String resref2 = "";

    if (text != null) {
      resref = text.trim();
      final boolean hasPrefix = resref.toLowerCase().startsWith("ap_") || resref.toLowerCase().startsWith("ga_");
      final int maxLength = hasPrefix ? 8 + 3 : 8;
      if (resref.isEmpty() || resref.length() > maxLength) {
        resref = "";
      } else if (Pattern.compile("\\s+").matcher(resref).find()) {
        resref = "";
      }
      if (hasPrefix) {
        resref2 = resref.substring(3);
      }
    }

    final String[] retVal;
    if (!resref2.isEmpty()) {
      retVal = new String[] { resref2, resref };
    } else if (!resref.isEmpty()) {
      retVal = new String[] { resref };
    } else {
      retVal = new String[] {};
    }

    return retVal;
  }

  /** Clears the current state of the "open resref" popup menu. */
  private void clearResrefPopupMenu() {
    menuOpenResource.removeAll();
    menuOpenResource.setEnabled(false);
    menuOpenResource.setText(OPEN_RESOURCE_LABEL_DEFAULT);
  }

  /** Updates the "open resref" popup menu to the current state. */
  private void initResrefPopupMenu() {
    clearResrefPopupMenu();

    // populating menu with available resource types
    final String[] resref = getResrefFromText();
    if (resref.length > 0) {
      int count = 0;
      int index = 0;
      for (final JMenuItem item : resourceTypeItems) {
        for (int i = 0; i < resref.length; i++) {
          boolean exists = !resref[i].isEmpty() && ResourceFactory.resourceExists(resref[i] + '.' + item.getText());
          if (exists) {
            index = Math.max(index, i);
            item.putClientProperty(PROP_RESREF, resref[i]);
            menuOpenResource.add(item);
            count++;
            break;
          }
        }
      }

      if (count > 0) {
        menuOpenResource.setEnabled(true);
        menuOpenResource.setText(String.format(OPEN_RESOURCE_LABEL_FMT, resref[index]));
      }
    }
  }

  /** Updates the "open strref" popup menu to the current state. */
  private void initStrrefPopupMenu() {
    final int strref = getNumberFromText(true);
    if (strref >= 0) {
      menuOpenStrref.setEnabled(true);
      menuOpenStrref.putClientProperty(PROP_STRREF, strref);
      menuOpenStrref.setText(String.format(OPEN_STRREF_LABEL_FMT, strref));
    } else {
      menuOpenStrref.setEnabled(false);
      menuOpenStrref.setText(OPEN_STRREF_LABEL_DEFAULT);
    }
  }

  /** Clears the current state of the "open ids reference" popup menu. */
  private void clearIdsPopupMenu() {
    menuOpenIds.removeAll();
    menuOpenIds.setEnabled(false);
    menuOpenIds.setText(OPEN_IDS_LABEL_DEFAULT);
  }

  /** Updates the "open ids reference" popup menu to the current state. */
  private void initIdsPopupMenu() {
    clearIdsPopupMenu();

    // populating menu with available ids entries
    long ids = getNumberFromText(false) & 0xffffffffL;
    int count = 0;
    for (final JMenuItem item : idsFileItems) {
      final IdsMap idsMap = IdsMapCache.get(item.getText());
      if (idsMap != null) {
        final boolean available = idsMap.get(ids) != null;
        if (available) {
          item.putClientProperty(PROP_IDS, ids);
          menuOpenIds.add(item);
          count++;
        }
      }
    }

    if (count > 0) {
      menuOpenIds.setEnabled(true);
      menuOpenIds.setText(String.format(OPEN_IDS_LABEL_FMT, ids));
    }
  }

  // -------------------------- INNER CLASSES --------------------------

  public static class GutterIcon {
    int line;
    Icon icon;
    String message;

    public GutterIcon(int line, Icon icon, String message) {
      this.line = line;
      this.icon = icon;
      this.message = message;
    }
  }
}
