// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.FontUIResource;

import org.infinity.cli.CommandLineTool;
import org.infinity.datatype.IdsBitmap;
import org.infinity.datatype.ProRef;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.Song2daBitmap;
import org.infinity.datatype.SpellProtType;
import org.infinity.datatype.Summon2daBitmap;
import org.infinity.gui.ButtonPopupWindow;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.DataMenuItem;
import org.infinity.gui.InfinityTextArea;
import org.infinity.gui.OpenFileFrame;
import org.infinity.gui.PopupWindowEvent;
import org.infinity.gui.PopupWindowListener;
import org.infinity.gui.QuickSearch;
import org.infinity.gui.ResourceTree;
import org.infinity.gui.SpriteAnimationPanel;
import org.infinity.gui.StatusBar;
import org.infinity.gui.StringEditor;
import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.gui.menu.Bookmark;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.Viewable;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.bcs.Signatures;
import org.infinity.resource.cre.decoder.util.ItemInfo;
import org.infinity.resource.cre.decoder.util.SpriteUtils;
import org.infinity.resource.effects.BaseOpcode;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.key.ResourceTreeModel;
import org.infinity.resource.spl.generator.GeneratorDialog;
import org.infinity.resource.text.PlainTextResource;
import org.infinity.updater.UpdateCheck;
import org.infinity.updater.UpdateInfo;
import org.infinity.updater.Updater;
import org.infinity.updater.Utils;
import org.infinity.util.CharsetDetector;
import org.infinity.util.CreMapCache;
import org.infinity.util.FileDeletionHook;
import org.infinity.util.IconCache;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IniMapCache;
import org.infinity.util.LauncherUtils;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.Operation;
import org.infinity.util.Platform;
import org.infinity.util.PortraitIconCache;
import org.infinity.util.StringTable;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.DlcManager;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;
import org.infinity.util.tuples.Couple;

public final class NearInfinity extends JFrame implements ActionListener, ViewableContainer {
  private static final String PROPERTIES_FILENAME     = "nearinfinity.properties";

  private static final String PROP_APP_VERSION        = "app_version";
  private static final String PROP_JAVA_VERSION_MIN   = "java_version_min";

  public static final String KEYFILENAME              = "chitin.key";
  public static final String WINDOW_SIZEX             = "WindowSizeX";
  public static final String WINDOW_SIZEY             = "WindowSizeY";
  public static final String WINDOW_POSX              = "WindowPosX";
  public static final String WINDOW_POSY              = "WindowPosY";
  public static final String WINDOW_STATE             = "WindowState";
  public static final String WINDOW_SPLITTER          = "WindowSplitter";
  public static final String LAST_GAMEDIR             = "LastGameDir";
  public static final String TABLE_WIDTH_ATTR         = "TableColWidthAttr";
  public static final String TABLE_WIDTH_OFS          = "TableColWidthOfs";
  public static final String TABLE_WIDTH_SIZE         = "TableColWidthSize";
  public static final String TABLE_PANEL_HEIGHT       = "TablePanelHeight";
  public static final String OPTION_GLOBAL_FONT_SIZE  = "GlobalFontSize";
  public static final String APP_UI_SCALE_ENABLED     = "AppUiScaleEnabled";
  public static final String APP_UI_SCALE_FACTOR      = "AppUiScaleFactor";
  public static final String APP_LOG_LEVEL            = "AppLogLevel";

  private static final String STATUSBAR_TEXT_FMT = "Welcome to Near Infinity! - %s @ %s - %d files available";

  // Input map key for triggering the quick search feature
  private static final String ACTIONMAP_KEY_QUICK_SEARCH = "SHORTCUT_OPEN_QUICK_SEARCH";

  private static final List<Class<? extends LookAndFeel>> CUSTOM_LOOK_AND_FEELS = new ArrayList<>();

  static {
    // Initializing custom Look&Feel themes
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.FlatLightLaf.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.FlatDarkLaf.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.FlatDarculaLaf.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.FlatIntelliJLaf.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.themes.FlatMacLightLaf.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.themes.FlatMacDarkLaf.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatArcIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatNordIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTArcDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneLightIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTDraculaIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTLightOwlIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDeepOceanIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialLighterIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialOceanicIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialPalenightIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMonokaiProIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMoonlightIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTNightOwlIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedDarkIJTheme.class);
    CUSTOM_LOOK_AND_FEELS.add(com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedLightIJTheme.class);

    // Setting Swing UI scale factor (only available for Java 9 or higher)
    if (Platform.JAVA_VERSION > 8) {
      final int uiScale = getUiScalingOption();
      if (uiScale > 0) {
        // Must be set BEFORE any Swing Library calls to be effective
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("sun.java2d.uiScale", Double.toString(uiScale / 100.0));
      }
    }

    if (Platform.IS_MACOS) {
      // Enforce proper macOS menu bar integration
      System.setProperty("apple.laf.useScreenMenuBar", "true");
    }
  }

  // settings are applied lazily since app settings aren't available at initialization time
  private static final InfinityTextArea CONSOLE_TEXT = new InfinityTextArea(false);

  private static boolean consoleSettingsApplied = false;
  private static NearInfinity browser;

  private final SpriteAnimationPanel containerpanel;
  private final JSplitPane spSplitter;
  private final ResourceTree tree;
  private final StatusBar statusBar;
  private final WindowBlocker blocker = new WindowBlocker(this);

  /** Stores table column widths for "Attribute", "Value" and "Offset" */
  private final int[] tableColumnWidth = { -1, -1, -1, -1 };

  private final ButtonPopupWindow bpwQuickSearch;
  private final JButton btnLaunchGame;
  private final JPopupMenu launchMenu;

  private Viewable viewable;
  private int tablePanelHeight;
  private ProgressMonitor pmProgress;
  private int progressIndex;
  private SwingWorker<Void, Void> iconCacheWorker;

  private static Path findKeyfile() {
    JFileChooser chooser;
    if (Profile.getGameRoot() == null) {
      chooser = new JFileChooser(new File("."));
    } else {
      chooser = new JFileChooser(Profile.getGameRoot().toFile());
    }
    chooser.setFileHidingEnabled(false);
    chooser.setDialogTitle("Open game: Locate keyfile");
    chooser.setFileFilter(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.isDirectory() || pathname.getName().toLowerCase(Locale.ENGLISH).endsWith(".key");
      }

      @Override
      public String getDescription() {
        return "Infinity Keyfile (.KEY)";
      }
    });
    if (chooser.showOpenDialog(getInstance()) == JFileChooser.APPROVE_OPTION) {
      return chooser.getSelectedFile().toPath();
    }
    return null;
  }

  public static InfinityTextArea getConsoleText() {
    if (!consoleSettingsApplied) {
      consoleSettingsApplied = true;
      CONSOLE_TEXT.applySettings(true);
      CONSOLE_TEXT.applyExtendedSettings(null, null);
      CONSOLE_TEXT.setFont(Misc.getScaledFont(CONSOLE_TEXT.getFont()));
    }
    return CONSOLE_TEXT;
  }

  public static NearInfinity getInstance() {
    return browser;
  }

  /** Returns the current NearInfinity version. */
  public static String getVersion() {
    return getAppProperty(PROP_APP_VERSION, "v1.0-19700101");
  }

  /** Returns the minimum supported Java version. */
  public static int getMinJavaVersion() {
    try {
      return Integer.parseInt(getAppProperty(PROP_JAVA_VERSION_MIN, "0"));
    } catch (NumberFormatException e) {
      Logger.error(e);
    }
    return 0;
  }

  /**
   * Returns the value of the specified property from the app-specific properties resource.
   *
   * @param key      The property key.
   * @param defValue Default value that is returned if the property could not be read.
   * @return The value in the properties resource with the specified key value.
   */
  private static String getAppProperty(String key, String defValue) {
    try (final InputStream is = ClassLoader.getSystemResourceAsStream(PROPERTIES_FILENAME)) {
      final Properties prop = new Properties();
      prop.load(is);
      return prop.getProperty(key, defValue);
    } catch (IOException e) {
      Logger.error(e);
    }
    return defValue;
  }

  public static void printHelp(String jarFile) {
    if (jarFile == null || jarFile.isEmpty() || !jarFile.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
      jarFile = "NearInfinity.jar";
    }
    System.out.format("Usage: java -jar %s [options] [game_path]", jarFile).println();
    System.out.println("\nOptions:");
    System.out.println("  -v, -version      Display version information.");
    System.out.println("  -h, -help         Display this help.");
    System.out.println("  -no-update        Disables the update check option in the menu bar.");
    System.out.println("  -no-launch-game   Hides the \"Launch game\" button.");
    System.out.println("  --run-tool class file");
    System.out.println("                    Runs a command-line tool from the org.infinity.cli package.");
    System.out.println("  -t type           Force the current or specified game to be of");
    System.out.println("                    specific type. (Use with care!)");
    System.out.println("                    Supported game types:");
    for (final Profile.Game game : Profile.Game.values()) {
      System.out.println("                      " + game.toString());
    }
    System.out.println("\nExamples:");
    System.out.format("Specify game path: java -jar %s \"C:\\Games\\Baldurs Gate II\"", jarFile).println();
    System.out.format("Force game type:   java -jar %s -t bg2tob", jarFile).println();
    System.out.format("Display version:   java -jar %s -v", jarFile).println();
    System.out.format("Display help:      java -jar %s -help", jarFile).println();
  }

  /** Advances the progress monitor by one step with optional note. */
  public static void advanceProgress(String note) {
    if (getInstance() != null && getInstance().pmProgress != null) {
      getInstance().pmProgress.setNote((note != null) ? note : "");
      getInstance().pmProgress.setProgress(++getInstance().progressIndex);
    }
  }

  /** Returns whether the option "Override UI Scaling" is available for the current NI session. */
  public static boolean isUiScalingSupported() {
    return Platform.JAVA_VERSION >= 9;
  }

  /**
   * Returns the UI scaling factor (in percent) if overridden by the app. Returns 0 if system-wide UI scale factor
   * is used.
   *
   * @implNote This method must not, directly or indirectly, cause any Java Swing library to be loaded.
   */
  private static int getUiScalingOption() {
    int retVal = 0;
    final Preferences prefs = Preferences.userNodeForPackage(NearInfinity.class);
    if (prefs.getBoolean(APP_UI_SCALE_ENABLED, false)) {
      retVal = Math.max(50, Math.min(400, prefs.getInt(APP_UI_SCALE_FACTOR, 100)));
    }
    return retVal;
  }

  public static void main(String[] args) {
    if (args.length > 0 && "--run-tool".equalsIgnoreCase(args[0])) {
      runTool(args);
      return;
    }

    Profile.Game forcedGame = null;
    Path gameOverride = null;
    boolean enableUpdate = true;
    boolean showLaunchGame = true;

    for (int idx = 0; idx < args.length; idx++) {
      switch (args[idx].toLowerCase(Locale.ENGLISH)) {
        case "-v":
        case "-version":
          System.out.println("Near Infinity " + getVersion());
          System.exit(0);
        case "-h":
        case "-help":
          String jarFile = Utils.getJarFileName(NearInfinity.class);
          if (!jarFile.isEmpty()) {
            jarFile = FileManager.resolve(jarFile).getFileName().toString();
          }
          printHelp(jarFile);
          System.exit(0);
        case "-no-update":
          enableUpdate = false;
          break;
        case "-no-launch-game":
          showLaunchGame = false;
          break;
        case "-t":
          if (idx + 1 < args.length) {
            idx++;
            String type = args[idx];
            Profile.Game[] games = Profile.Game.values();
            for (final Profile.Game game : games) {
              if (game.toString().equalsIgnoreCase(type)) {
                forcedGame = game;
                break;
              }
            }
            break;
          } else {
            System.err.println("Missing argument for option '-t'.");
            System.exit(1);
          }
        default:
          // Override game folder via application parameter
          Path f = FileManager.resolve(args[idx]);
          if (FileEx.create(f).isFile()) {
            f = f.getParent();
          }
          if (FileEx.create(f).isDirectory()) {
            gameOverride = f;
          }
      }
    }

    // Installing custom Look&Feel themes
    for (final Class<? extends LookAndFeel> lf: CUSTOM_LOOK_AND_FEELS) {
      try {
        final LookAndFeel o = lf.getDeclaredConstructor().newInstance();
        UIManager.installLookAndFeel(o.getName(), o.getClass().getCanonicalName());
      } catch (Exception e) {
        Logger.error(e);
      }
    }

    // Checking Java version
    final int minJavaVersion = getMinJavaVersion();
    if (Platform.JAVA_VERSION < minJavaVersion) {
      JOptionPane.showMessageDialog(null, String.format("Java %d or later is required to run Near Infinity!", minJavaVersion),
          "Error", JOptionPane.ERROR_MESSAGE);
      System.exit(10);
    }

    System.setOut(new ConsoleStream(System.out, CONSOLE_TEXT));
    System.setErr(new ConsoleStream(System.err, CONSOLE_TEXT));

    final Options options = new Options(gameOverride, forcedGame, enableUpdate, showLaunchGame);
    new NearInfinity(options);
  }

  private static void runTool(String[] args) {
    if (args.length != 3) {
      System.err.println("Usage: java -jar NearInfinity.jar --run-tool <class> <file>");
      System.exit(1);
    }

    String className = args[1];
    String fileName = args[2];
    try {
      Class<?> toolClass = Class.forName("org.infinity.cli." + className);
      if (!CommandLineTool.class.isAssignableFrom(toolClass)) {
        throw new IllegalArgumentException("Class does not implement CommandLineTool: " + className);
      }
      CommandLineTool tool = (CommandLineTool) toolClass.getDeclaredConstructor().newInstance();
      tool.run(fileName);
    } catch (Exception e) {
      System.err.println("Command-line tool failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private NearInfinity(Options options) {
    super("Near Infinity");
    browser = this;
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setAppIcon();

    // setting more reasonable tooltip timings
    ToolTipManager.sharedInstance().setDismissDelay(8000);

    // FileDeletionHook provides a way to delete files when the Java Virtual Machine shuts down
    Runtime.getRuntime().addShutdownHook(FileDeletionHook.getInstance());

    // updating relative default font size globally
    resizeUIFont(AppOption.GLOBAL_FONT_SIZE.getIntValue());

    final BrowserMenuBar menu = new BrowserMenuBar();
    menu.getHelpMenu().setUpdateMenuEnabled(options.isUpdateEnabled());
    menu.getOptions().setLaunchGameMenuEnabled(options.isLaunchGameVisible());
    // Registers menu as key event dispatcher to intercept Ctrl+Shift+D from any window
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(menu);
    setJMenuBar(menu);

    final String lastDir;
    if (options.isGameOverride() && FileEx.create(options.getGameOverride()).isDirectory()) {
      lastDir = options.getGameOverride().toString();
    } else {
      lastDir = AppOption.LAST_GAME_DIR.getStringValue();
    }

    final Path keyFile;
    Path path;
    if (FileEx.create(path = FileManager.resolve(KEYFILENAME)).isFile()) {
      keyFile = path;
    } else if (lastDir != null && FileEx.create(path = FileManager.resolve(lastDir, KEYFILENAME)).isFile()) {
      keyFile = path;
    } else {
      keyFile = findKeyfile();
    }
    if (keyFile == null) {
      System.exit(10);
    }

    showProgress("Starting Near Infinity" + Misc.MSG_EXPAND_LARGE, 6);
    SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        Profile.openGame(keyFile, BrowserMenuBar.getInstance().getGameMenu().getBookmarkName(keyFile), options.getForcedGame());

        // making sure vital game resources are accessible
        Path tlkFile = Profile.getProperty(Profile.Key.GET_GAME_DIALOG_FILE);
        try {
          checkFileAccess(tlkFile);
        } catch (Exception e) {
          Logger.error(e);
          JOptionPane.showMessageDialog(NearInfinity.this,
                                        String.format("Unable to open the game \"%s\".\n" +
                                                      "The file \"%s\" is locked by another process.",
                                                      Profile.getProperty(Profile.Key.GET_GAME_TITLE), tlkFile.getFileName()),
                                        "Near Infinity Error", JOptionPane.ERROR_MESSAGE);
          System.exit(10);
        }

        advanceProgress("Initializing GUI...");
        BrowserMenuBar.getInstance().gameLoaded(Profile.Game.Unknown, null);
        CreMapCache.reset();
        BaseOpcode.initOpcodes();

        return null;
      }
    };

    try {
      try {
        worker.execute();
        worker.get();
      } catch (InterruptedException | ExecutionException e) {
        Logger.error(e);
        System.exit(10);
      }

      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent event) {
          quit();
        }
      });

      try {
        LookAndFeelInfo info = BrowserMenuBar.getInstance().getOptions().getLookAndFeel();
        UIManager.setLookAndFeel(info.getClassName());
        SwingUtilities.updateComponentTreeUI(this);
      } catch (Exception e) {
        Logger.error(e);
      }

      cacheResourceIcons(true);

      statusBar = new StatusBar();
      ResourceTreeModel treemodel = ResourceFactory.getResourceTreeModel();
      updateWindowTitle();
      final String msg = String.format(STATUSBAR_TEXT_FMT,
                                       Profile.getProperty(Profile.Key.GET_GAME_TITLE),
                                       Profile.getGameRoot(), Objects.requireNonNull(treemodel).size());
      statusBar.setMessage(msg);
      tree = new ResourceTree(treemodel);
      tree.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

      JToolBar toolBar = new JToolBar("Navigation", SwingConstants.HORIZONTAL);
      toolBar.setMargin(new Insets(4, 4, 4, 4));
      JButton b;
      toolBar.setRollover(true);
      toolBar.setFloatable(false);
      b = new JButton(Icons.ICON_EXPAND_16.getIcon());
      b.addActionListener(this);
      b.setActionCommand("Expand");
      b.setToolTipText("Expand selected node");
      b.setMargin(new Insets(4, 4, 4, 4));
      toolBar.add(b);
      b = new JButton(Icons.ICON_COLLAPSE_16.getIcon());
      b.addActionListener(this);
      b.setActionCommand("Collapse");
      b.setToolTipText("Collapse selected node");
      b.setMargin(new Insets(4, 4, 4, 4));
      toolBar.add(b);
      toolBar.addSeparator(new Dimension(8, 24));
      b = new JButton(Icons.ICON_EXPAND_ALL_24.getIcon());
      b.addActionListener(this);
      b.setActionCommand("ExpandAll");
      b.setToolTipText("Expand all");
      b.setMargin(new Insets(0, 0, 0, 0));
      toolBar.add(b);
      b = new JButton(Icons.ICON_COLLAPSE_ALL_24.getIcon());
      b.addActionListener(this);
      b.setActionCommand("CollapseAll");
      b.setToolTipText("Collapse all");
      b.setMargin(new Insets(0, 0, 0, 0));
      toolBar.add(b);
      toolBar.addSeparator(new Dimension(8, 24));

      final String shortcutModifier =
          Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() == KeyEvent.CTRL_MASK ? "Ctrl" : "⌘";
      bpwQuickSearch = new ButtonPopupWindow(Icons.ICON_MAGNIFY_16.getIcon());
      bpwQuickSearch.setToolTipText("Find resource (Shortcut: " + shortcutModifier + "-/)");
      bpwQuickSearch.setMargin(new Insets(4, 4, 4, 4));
      toolBar.add(bpwQuickSearch);
      bpwQuickSearch.addPopupWindowListener(new PopupWindowListener() {
        @Override
        public void popupWindowWillBecomeVisible(PopupWindowEvent event) {
          // XXX: Working around a visual glitch in QuickSearch's JComboBox popup list
          // by creating new QuickSearch instances on activation
          bpwQuickSearch.setContent(new QuickSearch(bpwQuickSearch, tree));

          SwingUtilities.invokeLater(() -> {
            Component c = bpwQuickSearch.getContent();
            if (c != null) {
              c.requestFocusInWindow();
            }
          });
        }

        @Override
        public void popupWindowWillBecomeInvisible(PopupWindowEvent event) {
          SwingUtilities.invokeLater(() -> {
            bpwQuickSearch.setContent(null);
            tree.requestFocusInWindow();
          });
        }
      });

      // registering window-global shortcut for the quick search option
      final int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
      getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_DIVIDE, ctrl), ACTIONMAP_KEY_QUICK_SEARCH);
      getRootPane().getActionMap().put(ACTIONMAP_KEY_QUICK_SEARCH, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          bpwQuickSearch.showPopupWindow();
        }
      });

      toolBar.add(Box.createHorizontalGlue());
      btnLaunchGame = new JButton(Icons.ICON_LAUNCH_24.getIcon());
      btnLaunchGame.setFocusable(false);
      btnLaunchGame.setEnabled(false);
      btnLaunchGame.setMargin(new Insets(0, 0, 0, 0));
      btnLaunchGame.setToolTipText("Launch game");
      btnLaunchGame.addActionListener(this);
      btnLaunchGame.setVisible(options.isLaunchGameVisible());
      toolBar.add(btnLaunchGame);
      launchMenu = new JPopupMenu();

      JPanel leftPanel = new JPanel(new BorderLayout());
      leftPanel.add(tree, BorderLayout.CENTER);
      leftPanel.add(toolBar, BorderLayout.NORTH);

      containerpanel = new SpriteAnimationPanel(new BorderLayout());
      containerpanel.add(createJavaInfoPanel(), BorderLayout.CENTER);
      spSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, containerpanel);
      spSplitter.setBorder(BorderFactory.createEmptyBorder());
      spSplitter.setDividerLocation(AppOption.APP_WINDOW_SPLITTER.getIntValue());
      Container pane = getContentPane();
      pane.setLayout(new BorderLayout());
      pane.add(spSplitter, BorderLayout.CENTER);
      pane.add(statusBar, BorderLayout.SOUTH);
    } finally {
      hideProgress();
    }

    updateLauncher();
    setSize(AppOption.APP_WINDOW_SIZE_X.getIntValue(), AppOption.APP_WINDOW_SIZE_Y.getIntValue());
    setLocation(AppOption.APP_WINDOW_POS_X.getIntValue(), AppOption.APP_WINDOW_POS_Y.getIntValue());
    setVisible(true);
    setExtendedState(AppOption.APP_WINDOW_STATE.getIntValue());

    setSpriteAnimationPanelEnabled(true);

    // XXX: Workaround to trigger standard window closing callback on OSX when using command-Q
    if (Platform.IS_MACOS) {
      enableOSXQuitStrategy();
    }

    tableColumnWidth[0] = Math.max(15, AppOption.TABLE_COLUMN_ATTRIBUTE_WIDTH.getIntValue());
    tableColumnWidth[1] = 0;
    tableColumnWidth[2] = Math.max(15, AppOption.TABLE_COLUMN_OFFSET_WIDTH.getIntValue());
    tableColumnWidth[3] = Math.max(15, AppOption.TABLE_COLUMN_SIZE_WIDTH.getIntValue());
    tablePanelHeight = Math.max(50, AppOption.TABLE_PANEL_HEIGHT.getIntValue());

    // enabling file drag and drop for whole window
    new DropTarget(getRootPane(), new FileDropTargetListener());

    // Checking for updates
    if (options.isUpdateEnabled() &&
        Updater.getInstance().isAutoUpdateCheckEnabled() &&
        Updater.getInstance().hasAutoUpdateCheckDateExpired()) {
      // storing last check date for future reference
      Updater.getInstance().setAutoUpdateCheckDate(null);
      // running check in background with as little as possible interference with user interactions
      new Thread(() -> {
        try {
          UpdateInfo info = Updater.getInstance().loadUpdateInfo();
          if (info != null) {
            if (Updater.isNewRelease(info.getRelease(), true)) {
              UpdateCheck.showDialog(NearInfinity.getInstance(), info);
            }
          }
        } catch (Exception e) {
          Logger.warn("Failed to check for updates: {}", e.getMessage());
        }
      }).start();
    }

    SwingUtilities.invokeLater(tree::requestFocusInWindow);

    // Present first-time configuration options
    SwingUtilities.invokeLater(() -> {
      if (!preferencesExist()) {
        int result = JOptionPane.showConfirmDialog(this,
            "It looks like you are running Near Infinity for the first time.\n\n"
            + "Do you want to inspect and set up the application preferences?", "Introduction",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result == JOptionPane.YES_OPTION) {
          BrowserMenuBar.getInstance().getGameMenu().showPreferencesDialog(this);
        } else {
          JOptionPane.showMessageDialog(this, "Preferences can be found in the Game menu.", "Information",
              JOptionPane.INFORMATION_MESSAGE);
        }
      }
    });
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getActionCommand().equals("Open")) {
      blocker.setBlocked(true);
      Profile.Game oldGame = Profile.getGame();
      String oldFile = Objects.requireNonNull(Profile.getChitinKey()).toString();
      if (reloadFactory(false)) {
        if (!removeViewable()) {
          blocker.setBlocked(false);
          return;
        }
        ChildFrame.closeWindows();
        ResourceTreeModel treemodel = ResourceFactory.getResourceTreeModel();
        updateWindowTitle();
        updateLauncher();
        final String msg = String.format(STATUSBAR_TEXT_FMT, Profile.getProperty(Profile.Key.GET_GAME_TITLE),
                                         Profile.getGameRoot(), Objects.requireNonNull(treemodel).size());
        statusBar.setMessage(msg);
        BrowserMenuBar.getInstance().gameLoaded(oldGame, oldFile);
        tree.setModel(treemodel);
        ChildFrame.fireGameReset(false);
        resetSpriteAnimationPanel();
        containerpanel.removeAll();
        containerpanel.add(createJavaInfoPanel(), BorderLayout.CENTER);
        containerpanel.revalidate();
        containerpanel.repaint();
      }
      blocker.setBlocked(false);
    } else if (event.getActionCommand().equals("Exit")) {
      quit();
    } else if (event.getActionCommand().equals("GameIni")) {
      editGameIni(this);
    } else if (event.getActionCommand().equals("Refresh")) {
      refreshGame();
    } else if (event.getActionCommand().equals("RefreshTree")) {
      try {
        WindowBlocker.blockWindow(this, true);
        tree.reloadRenderer();
        tree.repaint();
        tree.requestFocusInWindow();
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    } else if (event.getActionCommand().equals("RefreshView")) {
      // repaint UI controls of current view
      if (getViewable() instanceof AbstractStruct) {
        StructViewer sv = ((AbstractStruct) getViewable()).getViewer();
        if (sv != null) {
          SwingUtilities.updateComponentTreeUI(sv);
        }
      }
      // repaint UI controls of child windows
      ChildFrame.updateWindowGUIs();
    } else if (event.getActionCommand().equals("ChangeLook")) {
      try {
        LookAndFeelInfo info = BrowserMenuBar.getInstance().getOptions().getLookAndFeel();
        updateLookAndFeel(info, true);
      } catch (Exception e) {
        Logger.error(e);
      }
    } else if (event.getActionCommand().equals("Collapse")) {
      try {
        WindowBlocker.blockWindow(this, true);
        tree.collapseSelected();
        tree.requestFocusInWindow();
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    } else if (event.getActionCommand().equals("Expand")) {
      try {
        WindowBlocker.blockWindow(this, true);
        tree.expandSelected();
        tree.requestFocusInWindow();
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    } else if (event.getActionCommand().equals("CollapseAll")) {
      try {
        WindowBlocker.blockWindow(this, true);
        tree.collapseAll();
        tree.requestFocusInWindow();
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    } else if (event.getActionCommand().equals("ExpandAll")) {
      try {
        WindowBlocker.blockWindow(this, true);
        tree.expandAll();
        tree.requestFocusInWindow();
      } finally {
        WindowBlocker.blockWindow(this, false);
      }
    } else if (event.getSource() == btnLaunchGame) {
      // Path launchPath = null;
      DataMenuItem dmi = null;
      boolean ctrl = (event.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0;
      if (ctrl || launchMenu.getComponentCount() == 1) {
        // getting first available binary path
        for (int i = 0, cnt = launchMenu.getComponentCount(); i < cnt; i++) {
          if (launchMenu.getComponent(i) instanceof DataMenuItem) {
            dmi = (DataMenuItem) launchMenu.getComponent(i);
            break;
          }
        }
      }

      if ((launchMenu.getComponentCount() > 1 && ctrl) || launchMenu.getComponentCount() == 1) {
        if (dmi != null) {
          dmi.doClick();
        } else {
          JOptionPane.showMessageDialog(this, "Could not determine game executable.", "Launch game",
                                        JOptionPane.ERROR_MESSAGE);
        }
      } else if (launchMenu.getComponentCount() > 1) {
        launchMenu.show(btnLaunchGame, 0, btnLaunchGame.getHeight());
      }
    } else if (event.getSource() instanceof DataMenuItem
        && ((DataMenuItem) event.getSource()).getParent() == launchMenu) {
      DataMenuItem dmi = (DataMenuItem) event.getSource();
      if (dmi.getData() instanceof Path) {
        Path path = (Path) dmi.getData();
        if (!launchGameBinary(path)) {
          JOptionPane.showMessageDialog(this, "Game executable could not be launched.", "Launch game",
                                        JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface ViewableContainer ---------------------

  @Override
  public StatusBar getStatusBar() {
    return statusBar;
  }

  @Override
  public Viewable getViewable() {
    return viewable;
  }

  @Override
  public void setViewable(Viewable newViewable) {
    if (!(newViewable instanceof Resource)) {
      removeViewable();
    } else {
      Resource resource = (Resource) newViewable;
      if (viewable != null) {
        if (resource.getResourceEntry() == ((Resource) viewable).getResourceEntry()) {
          return;
        } else if (viewable instanceof Closeable) {
          try {
            ((Closeable) viewable).close();
          } catch (Exception e) {
            if (viewable instanceof Resource) {
              tree.select(((Resource) viewable).getResourceEntry());
            }
            return;
          }
        }
      }
      viewable = newViewable;
      tree.select(resource.getResourceEntry());
      statusBar.setMessage(resource.getResourceEntry().getActualPath().toString());
      containerpanel.removeAll();
      containerpanel.add(viewable.makeViewer(this), BorderLayout.CENTER);
      setSpriteAnimationPanelEnabled(false);
      containerpanel.revalidate();
      containerpanel.repaint();
      toFront();
    }
  }

  // --------------------- End Interface ViewableContainer ---------------------

  public ResourceTree getResourceTree() {
    return tree;
  }

  public void openGame(Path keyFile) {
    blocker.setBlocked(true);
    try {
      Profile.Game oldGame = Profile.getGame();
      Path oldKeyFile = Profile.getChitinKey();
      ChildFrame.closeWindows();
      clearCache(false);
      Profile.openGame(keyFile, BrowserMenuBar.getInstance().getGameMenu().getBookmarkName(keyFile));

      // making sure vital game resources are accessible
      Path tlkPath = Profile.getProperty(Profile.Key.GET_GAME_DIALOG_FILE);
      try {
        checkFileAccess(tlkPath);
      } catch (Exception e) {
        Logger.error(e);
        JOptionPane.showMessageDialog(NearInfinity.this,
                                      String.format("The file \"%s\" of the game \"%s\"\nis locked by another process. "
                                          + "Reverting to the previous game.", tlkPath.getFileName(),
                                                    Profile.getProperty(Profile.Key.GET_GAME_TITLE)),
                                      "Near Infinity Error", JOptionPane.ERROR_MESSAGE);
        openGame(oldKeyFile);
        return;
      }

      CreMapCache.reset();
      removeViewable();
      ResourceTreeModel treemodel = ResourceFactory.getResourceTreeModel();
      updateWindowTitle();
      updateLauncher();
      final String msg = String.format(STATUSBAR_TEXT_FMT, Profile.getProperty(Profile.Key.GET_GAME_TITLE),
                                       Profile.getGameRoot(), Objects.requireNonNull(treemodel).size());
      statusBar.setMessage(msg);
      BrowserMenuBar.getInstance().gameLoaded(oldGame, Objects.requireNonNull(oldKeyFile).toString());
      tree.setModel(treemodel);
      ChildFrame.fireGameReset(false);
      resetSpriteAnimationPanel();
      containerpanel.removeAll();
      containerpanel.add(createJavaInfoPanel(), BorderLayout.CENTER);
      containerpanel.revalidate();
      containerpanel.repaint();
    } finally {
      blocker.setBlocked(false);
    }
  }

  public boolean removeViewable() {
    if (viewable != null && viewable instanceof Closeable) {
      try {
        ((Closeable) viewable).close();
      } catch (Exception e) {
        return false;
      }
    }
    viewable = null;
    tree.select(null);
    containerpanel.removeAll();
    containerpanel.add(createJavaInfoPanel(), BorderLayout.CENTER);
    setSpriteAnimationPanelEnabled(true);
    containerpanel.revalidate();
    containerpanel.repaint();
    return true;
  }

  public void showResourceEntry(ResourceEntry resourceEntry) {
    showResourceEntry(resourceEntry, null);
  }

  public void showResourceEntry(ResourceEntry resourceEntry, Operation doneOperation) {
    tree.select(resourceEntry, doneOperation);
  }

  public void quit() {
    if (removeViewable()) {
      ChildFrame.closeWindows(true);
      try { containerpanel.close(); } catch (Exception e) {}
      storePreferences();
      clearCache(false);
      System.exit(0);
    }
  }

  // Re-initializes currently selected game
  public void refreshGame() {
    try {
      blocker.setBlocked(true);
      reloadFactory(true);
      if (removeViewable()) {
        ChildFrame.closeWindows();
        ResourceTreeModel treemodel = ResourceFactory.getResourceTreeModel();
        updateWindowTitle();
        updateLauncher();
        final String msg = String.format(STATUSBAR_TEXT_FMT, Profile.getProperty(Profile.Key.GET_GAME_TITLE),
            Profile.getGameRoot(), Objects.requireNonNull(treemodel).size());
        statusBar.setMessage(msg);
        statusBar.invalidate();
        BrowserMenuBar.getInstance().gameLoaded(null, null);
        tree.setModel(treemodel);
        containerpanel.removeAll();
        containerpanel.add(createJavaInfoPanel(), BorderLayout.CENTER);
        containerpanel.revalidate();
        containerpanel.repaint();
      }
      cacheResourceIcons(true);
      ChildFrame.fireGameReset(true);
      resetSpriteAnimationPanel();
    } finally {
      blocker.setBlocked(false);
    }
  }

  // Set/Reset main window title
  public void updateWindowTitle() {
    String title = Profile.getProperty(Profile.Key.GET_GAME_TITLE);
    String desc = Profile.getProperty(Profile.Key.GET_GAME_DESC);
    if (desc != null && !desc.isEmpty()) {
      setTitle(String.format("Near Infinity - %s (%s)", title, desc));
    } else {
      setTitle(String.format("Near Infinity - %s", title));
    }
  }

  // Opens game's ini file in text editor
  public boolean editGameIni(Component parent) {
    boolean retVal = false;
    Path iniFile = Profile.getProperty(Profile.Key.GET_GAME_INI_FILE);
    try {
      if (iniFile != null && FileEx.create(iniFile).isFile()) {
        new ViewFrame(parent, new PlainTextResource(new FileResourceEntry(iniFile)));
      } else {
        throw new Exception();
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(parent, "Cannot open INI file.", "Error", JOptionPane.ERROR_MESSAGE);
    }
    return retVal;
  }

  /**
   * Returns the current column width for tables of structured resources.
   *
   * @param index The column index (0 = Attribute, 1 = Value and, optionally, 2 = Offset, 3 = Size)
   */
  public int getTableColumnWidth(int index) {
    index = Math.min(Math.max(0, index), tableColumnWidth.length - 1);
    if (tableColumnWidth[index] < 0) {
      switch (index) {
        case 0: // "Attribute" column
          tableColumnWidth[index] = 300;
          break;
        case 1: // "Value" column (calculated dynamically)
          tableColumnWidth[index] = 0;
          break;
        case 2: // optional "Offset" column
          tableColumnWidth[index] = 100;
          break;
        case 3: // optional "Size" column
          tableColumnWidth[index] = 75;
          break;
      }
    }
    return tableColumnWidth[index];
  }

  /**
   * Updates the current default column width for tables of structured resources.
   *
   * @param index    The column index (0 = Attribute, 1 = Value and, optionally, 2 = Offset, 3 = Size)
   * @param newValue New width in pixels for the specified column
   * @return Old column width
   */
  public int updateTableColumnWidth(int index, int newValue) {
    index = Math.min(Math.max(0, index), tableColumnWidth.length - 1);
    int retVal = tableColumnWidth[index];
    tableColumnWidth[index] = Math.max(15, newValue);
    return retVal;
  }

  /** Returns the current height of the panel below the table of structured resources. */
  public int getTablePanelHeight() {
    return tablePanelHeight;
  }

  /**
   * Updates the height of the panel below the table of structured resources.
   */
  public int updateTablePanelHeight(int newValue) {
    newValue = Math.max(50, newValue);
    int retVal = tablePanelHeight;
    tablePanelHeight = newValue;
    return retVal;
  }

  /** Updates the launcher button configuration. */
  public void updateLauncher() {
    SwingUtilities.invokeLater(() -> {
      // cleaning up old configuration
      for (int idx = launchMenu.getComponentCount() - 1; idx >= 0; idx--) {
        Component c = launchMenu.getComponent(idx);
        if (c instanceof JMenuItem) {
          ((JMenuItem) c).removeActionListener(NearInfinity.this);
        }
      }
      launchMenu.removeAll();

      // setting up new configuration
      Bookmark bookmark = BrowserMenuBar.getInstance().getGameMenu().getBookmarkOf(Profile.getChitinKey());
      List<Path> binPaths = null;
      if (bookmark != null) {
        List<String> list = bookmark.getBinaryPaths(Platform.OS.getCurrentOS());
        if (list != null && !list.isEmpty()) {
          binPaths = new ArrayList<>();
          for (final String name : list) {
            Path path1 = null;
            if (name.startsWith("/")) {
              path1 = FileManager.resolveExisting(name);
              if (path1 == null) {
                path1 = FileManager.resolveExisting(Profile.getGameRoot().toString(), name);
              }
            } else {
              path1 = FileManager.resolveExisting(Profile.getGameRoot().toString(), name);
            }
            if (path1 != null) {
              binPaths.add(path1);
            }
          }
        }
      }
      if (binPaths == null || binPaths.isEmpty()) {
        binPaths = Profile.getGameBinaryPaths();
      }
      if (binPaths.isEmpty()) {
        binPaths = null;
      }

      // updating launch controls
      if (binPaths != null) {
        for (final Path path2 : binPaths) {
          DataMenuItem dmi = new DataMenuItem(path2.toString());
          dmi.setData(path2);
          dmi.addActionListener(NearInfinity.this);
          launchMenu.add(dmi);
        }
      }
      boolean isEnabled = (binPaths != null) && BrowserMenuBar.getInstance().getOptions().getLauncherEnabled();
      btnLaunchGame.setEnabled(isEnabled);
      if (binPaths == null) {
        btnLaunchGame.setIcon(Icons.ICON_LAUNCH_24.getIcon());
        btnLaunchGame.setToolTipText("Launch game");
      } else if (binPaths.size() == 1) {
        btnLaunchGame.setIcon(Icons.ICON_LAUNCH_24.getIcon());
        btnLaunchGame.setToolTipText("Launch " + binPaths.get(0).toString());
      } else {
        String ctrlName = (Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() == InputEvent.CTRL_MASK) ? "Ctrl"
            : "Command";
        btnLaunchGame.setIcon(Icons.ICON_LAUNCH_PLUS_24.getIcon());
        btnLaunchGame.setToolTipText("Launch game (launch directly with " + ctrlName + "+Click)");
      }
    });
  }

  /**
   * Updates the current Look&Feel with the specified theme.
   * {@code prompt} indicates whether to show an information dialog when the L&F change is complete.
   */
  public void updateLookAndFeel(LookAndFeelInfo info, boolean prompt)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
    UIManager.setLookAndFeel(info.getClassName());
    SwingUtilities.updateComponentTreeUI(this);
    ChildFrame.updateWindowGUIs();
    tree.reloadRenderer();
    tree.repaint();
    if (prompt) {
      JOptionPane.showMessageDialog(this,
          "It might be necessary to restart Near Infinity\n" + "to completely change look and feel.");
    }
  }

  /** Returns whether settings exist in the persistent {@code Preferences} node. */
  public boolean preferencesExist() {
    final Preferences prefs = Preferences.userNodeForPackage(NearInfinity.class);
    if (prefs != null) {
      try {
        return Arrays.asList(prefs.keys()).contains(LAST_GAMEDIR);
      } catch (BackingStoreException e) {
        Logger.warn(e);
      }
    }
    return false;
  }

  /**
   * Returns whether the current Look & Feel theme uses a dark color scheme.
   *
   * @return {@code true} if a dark color scheme is detected, {@code false} otherwise.
   */
  public boolean isDarkMode() {
    final Color bg = Misc.getDefaultColor("TextField.background", Color.WHITE);
    final double bgIntensity;
    if (bg != null) {
      bgIntensity = bg.getRed() * 0.299 + bg.getGreen() * 0.587 + bg.getBlue() * 0.114;
    } else {
      bgIntensity = 0.0;
    }

    final Color fg = Misc.getDefaultColor("TextField.foreground", Color.BLACK);
    final double fgIntensity;
    if (fg != null) {
      fgIntensity = fg.getRed() * 0.299 + fg.getGreen() * 0.587 + fg.getBlue() * 0.114;
    } else {
      fgIntensity = 255.0;
    }

    return (bgIntensity < fgIntensity);
  }

  private boolean reloadFactory(boolean refreshOnly) {
    boolean retVal = false;
    clearCache(refreshOnly);
    Path keyFile = refreshOnly ? Profile.getChitinKey() : findKeyfile();
    if (keyFile != null) {
      retVal = Profile.openGame(keyFile, BrowserMenuBar.getInstance().getGameMenu().getBookmarkName(keyFile));
      if (retVal) {
        CreMapCache.reset();
      }
    }
    return retVal;
  }

  // Central method for clearing cached data
  private void clearCache(boolean refreshOnly) {
    cancelCacheResourceIcons();
    if (ResourceFactory.getKeyfile() != null) {
      ResourceFactory.getKeyfile().closeBIFFFiles();
    }
    if (!refreshOnly) {
      CharsetDetector.clearCache();
    }
    PortraitIconCache.clearCache();
    IconCache.clearCache();
    IdsMapCache.clearCache();
    IniMapCache.clearCache();
    Table2daCache.clearCache();
    CreMapCache.clearCache();
    BaseOpcode.reset();
//    SearchFrame.clearCache();
    StringEditor.saveModified(null, true, true, NearInfinity.getInstance());
    StringTable.resetAll();
    ProRef.clearCache();
    Signatures.clearCache();
    ColorConvert.clearCache();
    SpriteUtils.clearCache();
    ItemInfo.clearCache();
    AreResource.clearCache();
    IdsBitmap.clearCache();
    Song2daBitmap.resetSonglist();
    SpellProtType.resetTypeTable();
    Summon2daBitmap.resetSummonTable();
    GeneratorDialog.clearInstance();
    DlcManager.close();
  }

  private void showProgress(String msg, int max) {
    if (pmProgress == null) {
      pmProgress = new ProgressMonitor(null, msg, "   ", 0, max);
      pmProgress.setMillisToDecideToPopup(0);
      pmProgress.setMillisToPopup(0);
      progressIndex = 0;
      pmProgress.setProgress(++getInstance().progressIndex);
    }
  }

  private void hideProgress() {
    if (pmProgress != null) {
      pmProgress.close();
      pmProgress = null;
    }
  }

  private void resizeUIFont(int percent) {
    Enumeration<Object> keys = UIManager.getDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      Object value = UIManager.get(key);
      if (value instanceof FontUIResource) {
        FontUIResource fr = (FontUIResource) value;
        Font f = Misc.getScaledFont(fr, percent);
        UIManager.put(key, new FontUIResource(f));
      }
    }
    CONSOLE_TEXT.setFont(Misc.getScaledFont(CONSOLE_TEXT.getFont()));
  }

  private void storePreferences() {
    // preserve non-maximized size and position of the window if possible
    if ((getExtendedState() & Frame.MAXIMIZED_HORIZ) == 0) {
      AppOption.APP_WINDOW_SIZE_X.setValue((int) getSize().getWidth());
      AppOption.APP_WINDOW_POS_X.setValue((int) getLocation().getX());
    }
    if ((getExtendedState() & Frame.MAXIMIZED_VERT) == 0) {
      AppOption.APP_WINDOW_SIZE_Y.setValue((int) getSize().getHeight());
      AppOption.APP_WINDOW_POS_Y.setValue((int) getLocation().getY());
    }
    AppOption.APP_WINDOW_STATE.setValue(getExtendedState());
    AppOption.APP_WINDOW_SPLITTER.setValue(spSplitter.getDividerLocation());
    AppOption.LAST_GAME_DIR.setValue(Profile.getGameRoot().toString());
    AppOption.TABLE_COLUMN_ATTRIBUTE_WIDTH.setValue(getTableColumnWidth(0));
    AppOption.TABLE_COLUMN_OFFSET_WIDTH.setValue(getTableColumnWidth(2));
    AppOption.TABLE_COLUMN_SIZE_WIDTH.setValue(getTableColumnWidth(3));
    AppOption.TABLE_PANEL_HEIGHT.setValue(getTablePanelHeight());
    AppOption.UI_SCALE_ENABLED.setValue(BrowserMenuBar.getInstance().getOptions().isUiScalingEnabled());
    AppOption.UI_SCALE_FACTOR.setValue(BrowserMenuBar.getInstance().getOptions().getUiScalingFactor());
    AppOption.GLOBAL_FONT_SIZE.setValue(BrowserMenuBar.getInstance().getOptions().getGlobalFontSize());

    BrowserMenuBar.getInstance().storePreferences();
    Updater.getInstance().saveUpdateSettings();

    // store everything in the preferences
    AppOption.storePreferences();
  }

  private void setAppIcon() {
    List<Image> list = new ArrayList<>();
    for (int i = 4; true; i++) {
      final Image icon = ColorConvert.loadAppImage(Icons.class, String.format("App%d.png", 1 << i));
      if (icon != null) {
        list.add(icon);
      } else {
        break;
      }
    }
    setIconImages(list);
  }

  // Enables command-Q on OSX to trigger the window closing callback
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void enableOSXQuitStrategy() {
    // Doesn't work anymore on JRE 9 and higher:
    // Java 9 introduced streamlined access to macOS-specific features,
    //  e.g. menu bar integration, quit strategy, about dialog, ...
    // TODO: Reimplement macOS-specific functionality if/when NI switches to Java 9+ compatibility
    try {
      Class application = Class.forName("com.apple.eawt.Application");
      Method getApplication = application.getMethod("getApplication");
      Object instance = getApplication.invoke(application);
      Class strategy = Class.forName("com.apple.eawt.QuitStrategy");
      Enum closeAllWindows = Enum.valueOf(strategy, "CLOSE_ALL_WINDOWS");
      Method method = application.getMethod("setQuitStrategy", strategy);
      method.invoke(instance, closeAllWindows);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      Logger.error(e);
    }
  }

  // Executes the specified path
  private boolean launchGameBinary(Path binPath) {
    boolean retVal = false;
    if (binPath != null && Files.exists(binPath)) {
      try {
        if (Platform.IS_MACOS && binPath.toString().toLowerCase(Locale.ENGLISH).endsWith(".app")) {
          // This method may be required for launching Mac App Bundles
          LauncherUtils.open(binPath);
        } else {
          ProcessBuilder pb = new ProcessBuilder(binPath.toString());
          pb.directory(binPath.getParent().toFile());
          pb.inheritIO();
          pb.start();
        }
        retVal = true;
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return retVal;
  }

  /**
   * Checks read access of the specified file.
   *
   * @param path The file path to check.
   * @throws IOException if specified path could not be opened for reading.
   */
  private void checkFileAccess(Path path) throws IOException {
    if (path != null && !Files.isReadable(path)) {
      throw new IOException("Readable check failed: " + path);
    }
  }

  /** Controls enabled state of the creature animation panel. */
  public void setSpriteAnimationPanelEnabled(boolean enable) {
    if (BrowserMenuBar.getInstance().getOptions().showCreaturesOnPanel()) {
      if (enable && !containerpanel.isRunning()) {
        Logger.trace("Starting creature animation panel");
        containerpanel.start();
      } else if (!enable && containerpanel.isRunning()) {
        Logger.trace("Stopping creature animation panel");
        containerpanel.stop();
      }
    } else {
      if (containerpanel.isRunning()) {
        containerpanel.stop();
      }
    }
  }

  /** Resets the animation panel in a controlled manner. */
  public void resetSpriteAnimationPanel() {
    if (BrowserMenuBar.getInstance().getOptions().showCreaturesOnPanel()) {
      Logger.trace("Resetting creature animation panel");
      containerpanel.reset();
    }
    setSpriteAnimationPanelEnabled(true);
  }

  /**
   * Shows Java Runtime information when there are no components attached to the main view.
   */
  private JPanel createJavaInfoPanel() {
    final Color fgColor = Misc.orDefault(UIManager.getColor("Label.foreground"), Color.BLACK);
    final Color warningColor = ((fgColor.getRGB() & 0x808080) == 0)
        ? new Color(128, 0, 0)    // for light UI themes
        : new Color(255, 64, 64); // for dark UI themes

    // TODO: Enable deprecation message if 32-bit JRE is detected
//    final JLabel archNote;
//    if (Platform.IS_32BIT) {
//      final String msg = "<html><p>"
//          + "Support of 32-bit Java Runtime Environments has been deprecated and will be<br/>"
//          + "removed completely in a future version.<br/><br/>"
//          + "Please upgrade your Java Runtime Environment to 64-bit if possible.<br/>"
//          + "Download options can be found on the Near Infinity Wiki page (press F1 to open<br/>"
//          + "the Wiki in your browser.)</p></html>";
//      archNote = new JLabel(msg);
//      archNote.setForeground(warningColor);
//    } else {
//      archNote = null;
//    }

    if (!BrowserMenuBar.getInstance().getOptions().showSysInfo()) {
      // show only deprecation message
      final JPanel infoPanel = new JPanel();
      // TODO: Enable deprecation message if 32-bit JRE is detected (Maybe don't show if SysInfo is disabled?)
//      if (archNote != null) {
//        final GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
//        final int prefWidth;
//        final int minWidth = 400;
//        if (gd != null && (AppOption.APP_WINDOW_STATE.getIntValue() & Frame.MAXIMIZED_HORIZ) != 0) {
//          prefWidth = Math.max(minWidth, gd.getDisplayMode().getWidth() / 3);
//        } else {
//          prefWidth = Math.max(minWidth, AppOption.APP_WINDOW_SIZE_X.getIntValue() / 2);
//        }
//        final int prefHeight;
//        final int minHeight = 400;
//        if (gd != null && (AppOption.APP_WINDOW_STATE.getIntValue() & Frame.MAXIMIZED_VERT) != 0) {
//          prefHeight = Math.max(minHeight, gd.getDisplayMode().getHeight() / 3);
//        } else {
//          prefHeight = Math.max(minHeight, AppOption.APP_WINDOW_SIZE_Y.getIntValue() / 2);
//        }
//
//        archNote.setMinimumSize(new Dimension(minWidth, minHeight));
//        archNote.setPreferredSize(new Dimension(prefWidth, prefHeight));
//        infoPanel.setLayout(new GridBagLayout());
//        final GridBagConstraints c = ViewerUtil.setGBC(null, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
//            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
//        infoPanel.add(archNote, c);
//      }
      return infoPanel;
    }

    final List<Couple<String, String>> entries = new ArrayList<>();
    entries.add(new Couple<>("Near Infinity", getVersion()));
    entries.add(new Couple<>("Java Runtime", System.getProperty("java.runtime.name")));

    String s1 = System.getProperty("java.version", "n/a");
    String s2 = System.getProperty("java.version.date", "");
    String value = s2.isEmpty() ? s1 : String.format("%s (%s)", s1, s2);
    entries.add(new Couple<>("Java Version", value));

    value = System.getProperty("java.vm.name", "n/a") + " (" + System.getProperty("java.vm.version", "n/a");
    s1 = System.getProperty("java.vm.info", "");
    if (!s1.isEmpty()) {
      value += ", " + s1;
    }
    value += ")";
    entries.add(new Couple<>("Java VM", value));

    entries.add(new Couple<>("Java VM Architecture", System.getProperty("os.arch", "n/a")));

    long memoryMax = Runtime.getRuntime().maxMemory();
    if (memoryMax != Long.MAX_VALUE) {
      memoryMax /= 1024L * 1024L;
      entries.add(new Couple<>("Available Memory", String.format("%d MB", memoryMax)));
    } else {
      entries.add(new Couple<>("Available Memory", "n/a"));
    }

    JPanel infoPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();

    final JLabel titleLabel = new JLabel("System Information");
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() * 1.25f));

    int row = 0;
    c = ViewerUtil.setGBC(c, 0, row, 2, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 4, 0), 0, 0);
    infoPanel.add(titleLabel, c);
    row++;

    for (final Couple<String, String> entry : entries) {
      final JLabel keyLabel = new JLabel(entry.getValue0());
      c = ViewerUtil.setGBC(c, 0, row, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 0, 0, 0), 0, 0);
      infoPanel.add(keyLabel, c);

      final JLabel valueLabel = new JLabel(entry.getValue1());
      c = ViewerUtil.setGBC(c, 1, row, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
          new Insets(8, 16, 0, 0), 0, 0);
      infoPanel.add(valueLabel, c);

      row++;
    }

    // TODO: Enable deprecation message if 32-bit JRE is detected
//    if (archNote != null) {
//      c = ViewerUtil.setGBC(c, 0, row, 2, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
//          new Insets(16, 0, 0, 0), 0, 0);
//      infoPanel.add(archNote, c);
//      row++;
//    }

    if (memoryMax < 480) {
      String message = "<html>Your Java memory settings may not be sufficient to use all features of Near Infinity.<br/>";
      if (Platform.IS_32BIT) {
        message += "Please consider upgrading your Java Runtime Environment to 64-bit or run<br/>"
            + "Near Infinity with improved memory settings.";
      } else {
        message += "Please consider running Near Infinity with improved memory settings.";
      }
      message += "</html>";
      final JLabel infoLabel = new JLabel(message);
      infoLabel.setForeground(warningColor);

      c = ViewerUtil.setGBC(c, 0, row, 2, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
          new Insets(16, 0, 0, 0), 0, 0);
      infoPanel.add(infoLabel, c);
      row++;
    }

    return infoPanel;
  }

  /**
   * Cancels an ongoing resource icon cache operation.
   *
   * The method returns only after the operation has been successfully cancelled.
   */
  private void cancelCacheResourceIcons() {
    if (iconCacheWorker != null) {
      iconCacheWorker.cancel(false);
      for (int i = 0; i < 100 && iconCacheWorker.getProgress() < 100; i++) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Logger.debug(e);
        }
      }
      iconCacheWorker = null;
    }
  }

  /**
   * Preloads icons for all ITM and SPL resources into the cache.
   *
   * @param threaded Whether to perform the operation in a separate thread.
   */
  private void cacheResourceIcons(boolean threaded) {
    // Operation for caching resource icons
    final Operation operation = () -> {
      try {
        IconCache.clearCache();
        final List<Integer> sizeList = new ArrayList<>();
        if (BrowserMenuBar.getInstance().getOptions().showResourceTreeIcons()) {
          sizeList.add(IconCache.getDefaultTreeIconSize());
        }
        if (BrowserMenuBar.getInstance().getOptions().showResourceListIcons()) {
          sizeList.add(IconCache.getDefaultListIconSize());
        }
        if (!sizeList.isEmpty()) {
          int[] sizes = sizeList.stream().mapToInt(Integer::intValue).toArray();
          for (final String type : ResourceRef.getIconExtensions()) {
            final List<ResourceEntry> resources = ResourceFactory.getResources(type);
            if (resources != null) {
              for (final ResourceEntry e : resources) {
                for (final int size : sizes) {
                  if (iconCacheWorker != null && iconCacheWorker.isCancelled()) {
                    return;
                  }
                  IconCache.get(e, size);
                }
              }
            }
          }
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    };

    // ensure that ongoing operations have ended before starting a new operation
    cancelCacheResourceIcons();

    if (threaded) {
      iconCacheWorker = new SwingWorker<Void, Void>() {
        @Override
        protected Void doInBackground() throws Exception {
          setProgress(0);
          try {
            operation.perform();
          } catch (Exception e) {
            Logger.error(e);
          }
          setProgress(100);
          return null;
        }
      };
      iconCacheWorker.execute();
    } else {
      operation.perform();
    }
  }

  // -------------------------- INNER CLASSES --------------------------

  private static final class Options {
    private final Path gameOverride;
    private final Profile.Game forcedGame;
    private final boolean enableUpdate;
    private final boolean showLaunchGame;

    public Options(Path gameOverride, Profile.Game forcedGame, boolean enableUpdate, boolean showLaunchGame) {
      this.gameOverride = gameOverride;
      this.forcedGame = forcedGame;
      this.enableUpdate = enableUpdate;
      this.showLaunchGame = showLaunchGame;
    }

    public boolean isGameOverride() {
      return gameOverride != null;
    }

    public Path getGameOverride() {
      return gameOverride;
    }

    @SuppressWarnings("unused")
    public boolean isForcedGame() {
      return forcedGame != null;
    }

    public Profile.Game getForcedGame() {
      return forcedGame;
    }

    public boolean isUpdateEnabled() {
      return enableUpdate;
    }

    public boolean isLaunchGameVisible() {
      return showLaunchGame;
    }
  }

  private static final class ConsoleStream extends PrintStream {
    private final JTextArea text;

    private ConsoleStream(OutputStream out, JTextArea text) {
      super(out);
      this.text = text;
    }

    @Override
    public void write(byte[] buf, int off, int len) {
      super.write(buf, off, len);
      try {
        text.append(new String(buf, off, len));
      } catch (Exception e) {
        Logger.error(e);
      }
    }
  }

  private static class FileDropTargetListener implements DropTargetListener, Runnable {
    private List<File> files;

    private FileDropTargetListener() {
    }

    @Override
    public void dragEnter(DropTargetDragEvent event) {
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
    }

    @Override
    public void dragExit(DropTargetEvent event) {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void drop(DropTargetDropEvent event) {
      if (event.isLocalTransfer() || !event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        event.rejectDrop();
        return;
      }
      try {
        event.acceptDrop(DnDConstants.ACTION_COPY);
        files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
      } catch (Exception e) {
        Logger.error(e);
        event.dropComplete(false);
      }
      event.dropComplete(true);
      if (files != null && files.size() == 1) {
        Path path = files.get(0).toPath();
        if (FileEx.create(path).isFile() && path.getFileName().toString().toUpperCase(Locale.ENGLISH).endsWith(".KEY")) {
          Path curFile = Profile.getChitinKey();
          if (!path.equals(curFile)) {
            int ret = JOptionPane.showConfirmDialog(NearInfinity.getInstance(), "Open game \"" + path + "\"?",
                "Open game", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ret == JOptionPane.YES_OPTION) {
              NearInfinity.getInstance().openGame(path);
            }
          }
          return;
        }
      }
      new Thread(this).start();
    }

    @Override
    public void run() {
      if (files != null) {
        files.forEach(file -> {
          Path path = file.toPath();
          if (FileEx.create(path).isFile()) {
            OpenFileFrame.openExternalFile(NearInfinity.getInstance(), path);
          }
        });
      }
    }
  }
}
