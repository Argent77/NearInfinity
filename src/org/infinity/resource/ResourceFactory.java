// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.swing.JComponent;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

import org.infinity.NearInfinity;
import org.infinity.datatype.PriTypeBitmap;
import org.infinity.datatype.SecTypeBitmap;
import org.infinity.datatype.Song2daBitmap;
import org.infinity.datatype.Summon2daBitmap;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.IdsBrowser;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.bcs.BafResource;
import org.infinity.resource.bcs.BcsResource;
import org.infinity.resource.chu.ChuResource;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.dlg.DlgResource;
import org.infinity.resource.gam.GamResource;
import org.infinity.resource.graphics.BamResource;
import org.infinity.resource.graphics.GraphicsResource;
import org.infinity.resource.graphics.MosResource;
import org.infinity.resource.graphics.PltResource;
import org.infinity.resource.graphics.PvrzResource;
import org.infinity.resource.graphics.TisResource;
import org.infinity.resource.itm.ItmResource;
import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.Keyfile;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.key.ResourceTreeFolder;
import org.infinity.resource.key.ResourceTreeModel;
import org.infinity.resource.maze.MazeResource;
import org.infinity.resource.mus.MusResource;
import org.infinity.resource.other.EffResource;
import org.infinity.resource.other.FntResource;
import org.infinity.resource.other.TtfResource;
import org.infinity.resource.other.UnknownResource;
import org.infinity.resource.other.VvcResource;
import org.infinity.resource.other.WfxResource;
import org.infinity.resource.pro.ProResource;
import org.infinity.resource.sav.SavResource;
import org.infinity.resource.sound.SoundResource;
import org.infinity.resource.spl.SplResource;
import org.infinity.resource.src.SrcResource;
import org.infinity.resource.sto.StoResource;
import org.infinity.resource.text.PlainTextResource;
import org.infinity.resource.to.TohResource;
import org.infinity.resource.to.TotResource;
import org.infinity.resource.var.VarResource;
import org.infinity.resource.vef.VefResource;
import org.infinity.resource.video.MveResource;
import org.infinity.resource.video.WbmResource;
import org.infinity.resource.wed.WedResource;
import org.infinity.resource.wmp.WmpResource;
import org.infinity.util.StaticSimpleXorDecryptor;
import org.infinity.util.CreMapCache;
import org.infinity.util.DynamicArray;
import org.infinity.util.IdsMapCache;
import org.infinity.util.Misc;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.FileWatcher;
import org.infinity.util.io.FileWatcher.FileWatchEvent;
import org.infinity.util.io.FileWatcher.FileWatchListener;
import org.infinity.util.io.StreamUtils;

/**
 * Handles game-specific resource access.
 */
public final class ResourceFactory implements FileWatchListener
{
  /**
   * Name of tree node that contains important game files that not stored in
   * the BIF archives or override folders.
   */
  private static final String SPECIAL_CATEGORY = "Special";
  private static ResourceFactory instance;

  private JFileChooser fc;
  private Keyfile keyfile;
  private ResourceTreeModel treeModel;
  private Path pendingSelection;

  public static Keyfile getKeyfile()
  {
    if (getInstance() != null) {
      return getInstance().keyfile;
    } else {
      return null;
    }
  }

  /**
   * Opens a save dialog and returns the file path selected by the user.
   * @param parent Parent component of the file chooser dialog.
   * @param fileName Filename shown when the dialog opens.
   * @param forceOverwrite Whether to skip asking for confirmation if file already exists.
   * @return The path to the selected file. Returns {@code null} if user cancelled operation.
   */
  public static Path getExportFileDialog(Component parent, String fileName, boolean forceOverwrite)
  {
    return getInstance().getExportFileDialogInternal(parent, fileName, forceOverwrite);
  }

  /**
   * Returns the file path of the last export operation. Returns the root folder of the game otherwise.
   * @return Directory as {@link Path} object.
   */
  public static Path getExportFilePath()
  {
    return getInstance().getExportFilePathInternal();
  }

  public static Resource getResource(ResourceEntry entry)
  {
    return getResource(entry, null);
  }

  public static Resource getResource(ResourceEntry entry, String forcedExtension)
  {
    Resource res = null;
    try {
      String ext = (forcedExtension != null) ? forcedExtension : entry.getExtension();
      if (ext.equalsIgnoreCase("BAM")) {
        res = new BamResource(entry);
      } else if (ext.equalsIgnoreCase("TIS")) {
        res = new TisResource(entry);
      } else if (ext.equalsIgnoreCase("BMP") || ext.equalsIgnoreCase("PNG")) {
        res = new GraphicsResource(entry);
      } else if (ext.equalsIgnoreCase("MOS")) {
        res = new MosResource(entry);
      } else if (ext.equalsIgnoreCase("WAV") || ext.equalsIgnoreCase("ACM")) {
        res = new SoundResource(entry);
      } else if (ext.equalsIgnoreCase("MUS")) {
        res = new MusResource(entry);
      } else if (ext.equalsIgnoreCase("IDS") || ext.equalsIgnoreCase("2DA") ||
                 ext.equalsIgnoreCase("BIO") || ext.equalsIgnoreCase("RES") ||
                 ext.equalsIgnoreCase("INI") || ext.equalsIgnoreCase("TXT") ||
                 ext.equalsIgnoreCase("LOG") ||// WeiDU log files
                 (ext.equalsIgnoreCase("SRC") && Profile.getEngine() == Profile.Engine.IWD2) ||
                 (Profile.isEnhancedEdition() && (ext.equalsIgnoreCase("SQL") ||
                                                  ext.equalsIgnoreCase("GUI") ||
                                                  ext.equalsIgnoreCase("LUA") ||
                                                  ext.equalsIgnoreCase("MENU") ||
                                                  ext.equalsIgnoreCase("GLSL")))) {
        res = new PlainTextResource(entry);
      } else if (ext.equalsIgnoreCase("MVE")) {
        res = new MveResource(entry);
      } else if (ext.equalsIgnoreCase("WBM")) {
        res = new WbmResource(entry);
      } else if (ext.equalsIgnoreCase("PLT") && ext.equals(forcedExtension)) {
        res = new PltResource(entry);
      } else if (ext.equalsIgnoreCase("BCS") || ext.equalsIgnoreCase("BS")) {
        res = new BcsResource(entry);
      } else if (ext.equalsIgnoreCase("ITM")) {
        res = new ItmResource(entry);
      } else if (ext.equalsIgnoreCase("EFF")) {
        res = new EffResource(entry);
      } else if (ext.equalsIgnoreCase("VEF")) {
          res = new VefResource(entry);
      } else if (ext.equalsIgnoreCase("VVC")) {
        res = new VvcResource(entry);
      } else if (ext.equalsIgnoreCase("SRC")) {
        res = new SrcResource(entry);
      } else if (ext.equalsIgnoreCase("DLG")) {
        res = new DlgResource(entry);
      } else if (ext.equalsIgnoreCase("SPL")) {
        res = new SplResource(entry);
      } else if (ext.equalsIgnoreCase("STO")) {
        res = new StoResource(entry);
      } else if (ext.equalsIgnoreCase("WMP")) {
        res = new WmpResource(entry);
      } else if (ext.equalsIgnoreCase("CHU")) {
        res = new ChuResource(entry);
      } else if (ext.equalsIgnoreCase("CRE") || ext.equalsIgnoreCase("CHR")) {
        res = new CreResource(entry);
      } else if (ext.equalsIgnoreCase("ARE")) {
        res = new AreResource(entry);
      } else if (ext.equalsIgnoreCase("WFX")) {
        res = new WfxResource(entry);
      } else if (ext.equalsIgnoreCase("PRO")) {
        res = new ProResource(entry);
      } else if (ext.equalsIgnoreCase("WED")) {
        res = new WedResource(entry);
      } else if (ext.equalsIgnoreCase("GAM")) {
        res = new GamResource(entry);
      } else if (ext.equalsIgnoreCase("SAV")) {
        res = new SavResource(entry);
      } else if (ext.equalsIgnoreCase("VAR")) {
        res = new VarResource(entry);
      } else if (ext.equalsIgnoreCase("BAF")) {
        res = new BafResource(entry);
      } else if (ext.equalsIgnoreCase("TOH")) {
        res = new TohResource(entry);
      } else if (ext.equalsIgnoreCase("TOT")) {
        res = new TotResource(entry);
      } else if (ext.equalsIgnoreCase("PVRZ")) {
        res = Profile.isEnhancedEdition() ? new PvrzResource(entry) : new UnknownResource(entry);
      } else if (ext.equalsIgnoreCase("FNT")) {
        res = Profile.isEnhancedEdition() ? new FntResource(entry) : new UnknownResource(entry);
      } else if (ext.equalsIgnoreCase("TTF")) {
        res = Profile.isEnhancedEdition() ? new TtfResource(entry) : new UnknownResource(entry);
      } else if (ext.equalsIgnoreCase("MAZE")) {
        res = (Profile.getGame() == Profile.Game.PSTEE) ? new MazeResource(entry) : new UnknownResource(entry);
      } else {
        res = detectResource(entry);
        if (res == null) {
          res = new UnknownResource(entry);
        }
      }
    } catch (Exception e) {
      if (NearInfinity.getInstance() != null && !BrowserMenuBar.getInstance().ignoreReadErrors()) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(),
                                      "Error reading " + entry + '\n' + e.getMessage(),
                                      "Error", JOptionPane.ERROR_MESSAGE);
      } else {
        final String msg = String.format("Error reading %s @ %s - %s",
                                         entry, entry.getActualPath(), e);
        NearInfinity.getInstance().getStatusBar().setMessage(msg);
      }
      System.err.println("Error reading " + entry);
      e.printStackTrace();
    }
    return res;
  }

  /**
   * Attempts to detect the resource type from the data itself
   * and returns the respective resource class instance, or {@code null} on failure.
   */
  public static Resource detectResource(ResourceEntry entry)
  {
    Resource res = null;
    if (entry != null) {
      try {
        int[] info = entry.getResourceInfo();
        if (info.length == 2) {
          res = getResource(entry, "TIS");
        } else if (info.length == 1) {
          if (info[0] > 4) {
            byte[] data = new byte[Math.min(info[0], 24)];
            try (InputStream is = entry.getResourceDataAsStream()) {
              StreamUtils.readBytes(is, data);
            }
            String sig = DynamicArray.getString(data, 0, 4);
            if ("2DA ".equalsIgnoreCase(sig)) {
              res = getResource(entry, "2DA");
            } else if ("ARE ".equals(sig)) {
              res = getResource(entry, "ARE");
            } else if ("BAM ".equals(sig) || "BAMC".equals(sig)) {
              res = getResource(entry, "BAM");
            } else if ("CHR ".equals(sig)) {
              res = getResource(entry, "CHR");
            } else if ("CHUI".equals(sig)) {
              res = getResource(entry, "CHU");
            } else if ("CRE ".equals(sig)) {
              res = getResource(entry, "CRE");
            } else if ("DLG ".equals(sig)) {
              res = getResource(entry, "DLG");
            } else if ("EFF ".equals(sig)) {
              res = getResource(entry, "EFF");
            } else if ("GAME".equals(sig)) {
              res = getResource(entry, "GAM");
            } else if ("IDS ".equalsIgnoreCase(sig)) {
              res = getResource(entry, "IDS");
            } else if ("ITM ".equals(sig)) {
              res = getResource(entry, "ITM");
            } else if ("MAZE".equals(sig)) {
              res = getResource(entry, "MAZE");
            } else if ("MOS ".equals(sig) || "MOSC".equals(sig)) {
              res = getResource(entry, "MOS");
            } else if ("PLT ".equals(sig)) {
              res = getResource(entry, "PLT");
            } else if ("PRO ".equals(sig)) {
              res = getResource(entry, "PRO");
            } else if ("SAV ".equals(sig)) {
              res = getResource(entry, "SAV");
            } else if ("SPL ".equals(sig)) {
              res = getResource(entry, "SPL");
            } else if ("STOR".equals(sig)) {
              res = getResource(entry, "STO");
            } else if ("TIS ".equals(sig)) {
              res = getResource(entry, "TIS");
            } else if ("VEF ".equals(sig)) {
              res = getResource(entry, "VEF");
            } else if ("VVC ".equals(sig)) {
              res = getResource(entry, "VVC");
            } else if ("WAVC".equals(sig) || "RIFF".equals(sig) || "OggS".equals(sig)) {
              res = getResource(entry, "WAV");
            } else if ("WED ".equals(sig)) {
              res = getResource(entry, "WED");
            } else if ("WFX ".equals(sig)) {
              res = getResource(entry, "WFX");
            } else if ("WMAP".equals(sig)) {
              res = getResource(entry, "WMP");
            } else {
              if ((Arrays.equals(new byte[]{0x53, 0x43, 0x0a}, Arrays.copyOfRange(data, 0, 3)) ||  // == "SC\n"
                   Arrays.equals(new byte[]{0x53, 0x43, 0x0d, 0x0a}, Arrays.copyOfRange(data, 0, 4)))) { // == "SC\r\n"
                res = getResource(entry, "BCS");
              } else if (data.length > 6 && "BM".equals(new String(data, 0, 2)) &&
                         DynamicArray.getInt(data, 2) == info[0]) {
                res = getResource(entry, "BMP");
              } else if (data.length > 18 && "Interplay MVE File".equals(new String(data, 0, 18))) {
                res = getResource(entry, "MVE");
              } else if (Arrays.equals(new byte[]{(byte)0x1a, (byte)0x45, (byte)0xdf, (byte)0xa3},
                                       Arrays.copyOfRange(data, 0, 4))) {
                res = getResource(entry, "WBM");
              } else if (data.length > 6 && data[3] == 0 && data[4] == 0x78) {  // just guessing...
                res = getResource(entry, "PVRZ");
              } else if (data.length > 4 && data[0] == 0x89 &&
                         data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47) {
                res = getResource(entry, "PNG");
              } else if (DynamicArray.getInt(data, 0) == 0x00000100) {  // wild guess...
                res = getResource(entry, "TTF");
              }
            }
          }
        } else {
          throw new Exception(entry.getResourceName() + ": Unable to determine resource type");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return res;
  }

  public static void exportResource(ResourceEntry entry, Component parent)
  {
    if (getInstance() != null) {
      try {
        getInstance().exportResourceInternal(entry, parent, null);
      } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void exportResource(ResourceEntry entry, ByteBuffer buffer, String filename, Component parent)
  {
    if (getInstance() != null) {
      try {
        getInstance().exportResourceInternal(entry, buffer, filename, parent, null);
      } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /** Exports "entry" to "output" without any user interaction. */
  public static void exportResource(ResourceEntry entry, Path output) throws Exception
  {
    if (getInstance() != null && output != null) {
      getInstance().exportResourceInternal(entry, null, output);
    }
  }

  /**
   * Returns whether the specified resource exists.
   * @param resourceName The resource filename.
   * @return {@code true} if the resource exists in BIFF archives or override folders,
   *         {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName)
  {
    return (getResourceEntry(resourceName, false) != null);
  }

  /**
   * Returns whether the specified resource exists.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @return {@code true} if the resource exists in BIFF archives or override folders,
   *         {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName, boolean searchExtraDirs)
  {
    return (getResourceEntry(resourceName, searchExtraDirs) != null);
  }

  /**
   * Returns whether the specified resource exists.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @param extraDirs       A list of File entries pointing to additional folders to search, not
   *                        covered by the default override folder list (e.g. "install:/music").
   * @return {@code true} if the resource exists in BIFF archives or override folders,
   *         {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName, boolean searchExtraDirs, List<Path> extraDirs)
  {
    return (getResourceEntry(resourceName, searchExtraDirs, extraDirs) != null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   * @param resourceName The resource filename.
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not
   *         available.
   */
  public static ResourceEntry getResourceEntry(String resourceName)
  {
    return getResourceEntry(resourceName, false, null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not
   *         available.
   */
  public static ResourceEntry getResourceEntry(String resourceName, boolean searchExtraDirs)
  {
    return getResourceEntry(resourceName, searchExtraDirs, null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @param extraDirs       A list of File entries pointing to additional folders to search, not
   *                        covered by the default override folder list (e.g. "install:/music").
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not
   *         available.
   */
  public static ResourceEntry getResourceEntry(String resourceName, boolean searchExtraDirs, List<Path> extraDirs)
  {
    if (getInstance() != null) {
      ResourceEntry entry = getInstance().treeModel.getResourceEntry(resourceName);

      // checking default override folder list
      if (entry == null) {
        List<Path> extraFolders = Profile.getOverrideFolders(searchExtraDirs);
        if (extraFolders != null) {
          Path file = FileManager.query(extraFolders, resourceName);
          if (file != null && Files.isRegularFile(file)) {
            entry = new FileResourceEntry(file);
          }
        }
      }

      // checking custom folder list
      if (extraDirs != null && (entry == null)) {
        Path file = FileManager.query(extraDirs, resourceName);
        if (file != null && Files.isRegularFile(file)) {
          entry = new FileResourceEntry(file);
        }
      }

      return entry;
    } else {
      return null;
    }
  }

  /** Returns the resource tree model of the current game. */
  public static ResourceTreeModel getResourceTreeModel()
  {
    if (getInstance() != null) {
      return getInstance().treeModel;
    } else {
      return null;
    }
  }

  /**
   * Returns all resources of the specified resource type from BIFFs, extra and override directories.
   * @param type Resource extension.
   */
  public static List<ResourceEntry> getResources(String type)
  {
    return getResources(type, null);
  }

  /**
   * Returns all resources of the specified resource type from BIFFs, override and specified
   * extra directories.
   * @param type Resource extension.
   * @param extraDirs List of extra directories to search. Specify {@code null} to search default
   *                  extra directories.
   */
  public static List<ResourceEntry> getResources(String type, List<Path> extraDirs)
  {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal(type, extraDirs);
    } else {
      return null;
    }
  }

  /**
   * Returns all available resources from BIFFs, extra and override directories
   * from BIFFs, extra and override directories.
   */
  public static List<ResourceEntry> getResources()
  {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal((Pattern)null, null);
    } else {
      return null;
    }
  }

  /**
   * Returns all available resources from BIFFs, override and specified extra directories.
   * @param extraDirs List of extra directories to search. Specify {@code null} to search default
   *                  extra directories.
   */
  public static List<ResourceEntry> getResources(List<Path> extraDirs)
  {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal((Pattern)null, extraDirs);
    } else {
      return null;
    }
  }

  /**
   * Returns all available resources from BIFFs, override and extra directories matching the
   * specified regular expression pattern.
   * @param pattern RegEx pattern to filter available resources.
   */
  public static List<ResourceEntry> getResources(Pattern pattern)
  {
    return getResources(pattern, null);
  }

  /**
   * Returns all available resources from BIFFs, override and specified extra directories
   * matching the specified regular expression pattern.
   * @param pattern RegEx pattern to filter available resources.
   * @param extraDirs List of extra directories to search. Specify {@code null} to search default
   *                  extra directories.
   */
  public static List<ResourceEntry> getResources(Pattern pattern, List<Path> extraDirs)
  {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal(pattern, extraDirs);
    } else {
      return null;
    }
  }

  public static void loadResources() throws Exception
  {
    if (getInstance() != null) {
      getInstance().loadResourcesInternal();
    }
  }

  /**
   * Adds the specified resource to the resource tree if it's located in any of the supported
   * override or extra folders.
   */
  public static void registerResource(Path resource, boolean autoselect)
  {
    if (getInstance() != null) {
      getInstance().registerResourceInternal(resource, autoselect);
    }
  }

  /**
   * Removes the specified resource from the resource tree.
   */
  public static void unregisterResource(Path resource)
  {
    if (getInstance() != null) {
      getInstance().unregisterResourceInternal(resource);
    }
  }

  public static void saveCopyOfResource(ResourceEntry entry)
  {
    if (getInstance() != null) {
      getInstance().saveCopyOfResourceInternal(entry);
    }
  }

  public static boolean saveResource(Resource resource, Component parent)
  {
    if (getInstance() != null) {
      return getInstance().saveResourceInternal(resource, parent);
    } else {
      return false;
    }
  }

  /**
   * If {@code output} is not {@code null}, shows confirmation dialog for saving resource.
   * If user accepts saving then resource will be saved if it implements {@link Writable}
   *
   * @param resource Resource that must be saved
   * @param entry Entry that represents resource. Must not be {@code null}
   * @param parent Component that will be parent for dialog window. Must not be {@code null}
   *
   * @throws HeadlessException if {@link GraphicsEnvironment#isHeadless} returns {@code true}
   * @throws NullPointerException If any argument is {@code null}
   * @throws Exception If save will be cancelled
   */
  public static void closeResource(Resource resource, ResourceEntry entry, JComponent parent) throws Exception {
    final Path output;
    if (entry instanceof BIFFResourceEntry) {
      output = FileManager.query(Profile.getRootFolders(), Profile.getOverrideFolderName(), entry.getResourceName());
    } else {
      output = entry.getActualPath();
    }
    closeResource(resource, output, parent);
  }

  /**
   * If {@code output} is not {@code null}, shows confirmation dialog for saving resource.
   * If user accepts saving then resource will be saved if it implements {@link Writable}
   *
   * @param resource Resource that must be saved
   * @param output Path of the saved resource. If {@code null} method do nothing
   * @param parent Component that will be parent for dialog window. Must not be {@code null}
   *
   * @throws HeadlessException if {@link GraphicsEnvironment#isHeadless} returns {@code true}
   * @throws NullPointerException If {@code resource} or {@code parent} is {@code null}
   * @throws Exception If save will be cancelled
   */
  public static void closeResource(Resource resource, Path output, JComponent parent) throws Exception {
    if (output != null) {
      final String options[] = {"Save changes", "Discard changes", "Cancel"};
      final int result = JOptionPane.showOptionDialog(parent, "Save changes to " + output + '?',
                                                      "Resource changed", JOptionPane.YES_NO_CANCEL_OPTION,
                                                      JOptionPane.WARNING_MESSAGE, null, options, options[0]);
      if (result == JOptionPane.YES_OPTION) {
        saveResource(resource, parent.getTopLevelAncestor());
      } else
      if (result != JOptionPane.NO_OPTION) {
        throw new Exception("Save aborted");
      }
    }
  }

  /**
   * Returns a list of available game language directories for the current game in Enhanced Edition games.
   * Returns an empty list otherwise.
   */
  public static List<Path> getAvailableGameLanguages()
  {
    List<Path> list = new ArrayList<>();

    if (Profile.isEnhancedEdition()) {
      Path langPath = Profile.getProperty(Profile.Key.GET_GAME_LANG_FOLDER_BASE);
      if (langPath != null && Files.isDirectory(langPath)) {
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(langPath,
            (Path entry) -> {
              return Files.isDirectory(entry) &&
                     entry.getFileName().toString().matches("[a-z]{2}_[A-Z]{2}") &&
                     Files.isRegularFile(FileManager.query(entry, Profile.getProperty(Profile.Key.GET_GLOBAL_DIALOG_NAME)));
              })) {
          dstream.forEach((path) -> list.add(path));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return list;
  }

  /**  Return the game language specified in the given baldur.ini if found. Returns {@code en_US} otherwise. */
  public static String autodetectGameLanguage(Path iniFile)
  {
    final String langDefault = "en_US";   // using default language, if no language entry found
    if (Profile.isEnhancedEdition() && iniFile != null && Files.isRegularFile(iniFile)) {
      // Attempt to autodetect game language
      try (BufferedReader br = Files.newBufferedReader(iniFile, Misc.CHARSET_UTF8)) {
        String line;
        while ((line = br.readLine()) != null) {
          if (line.contains("'Language'")) {
            String[] entries = line.split(",");
            if (entries.length == 3) {
              // Note: replace operation is compatible with both baldur.ini and baldur.lua
              String lang = entries[2].replaceFirst("^[^']*'", "");
              lang = lang.replaceFirst("'.*$", "");
              if (lang.matches("[A-Za-z]{2}_[A-Za-z]{2}")) {
                Path path = FileManager.query(Profile.getGameRoot(), "lang", lang);
                if (path != null && Files.isDirectory(path)) {
                  try {
                    // try to fetch the actual path name to ensure correct case
                    return path.toRealPath().getFileName().toString();
                  } catch (Exception e) {
                    return lang;
                  }
                }
              }
            }
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error parsing " + iniFile.getFileName() +
                                      ". Using language defaults.", "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
    return langDefault;
  }

  /** Attempts to find the home folder of an Enhanced Edition game. */
  static Path getHomeRoot(boolean allowMissing)
  {
    if (Profile.hasProperty(Profile.Key.GET_GAME_HOME_FOLDER_NAME)) {
      final Path EE_DOC_ROOT = FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
      final String EE_DIR = Profile.getProperty(Profile.Key.GET_GAME_HOME_FOLDER_NAME);
      Path userPath = FileManager.query(EE_DOC_ROOT, EE_DIR);
      if (allowMissing || (userPath != null && Files.isDirectory(userPath))) {
        return userPath;
      } else {
        // fallback solution
        String userPrefix = System.getProperty("user.home");
        userPath = null;
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (osName.contains("windows")) {
          try {
            Process p = Runtime.getRuntime().exec("reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\" /v personal");
            p.waitFor();
            InputStream in = p.getInputStream();
            byte[] b = new byte[in.available()];
            in.read(b);
            in.close();
            String[] splitted = new String(b).split("\\s\\s+");
            userPrefix = splitted[splitted.length-1];
            userPath = FileManager.resolve(userPrefix, EE_DIR);
          } catch (Throwable t) {
            return null;
          }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
          userPath = FileManager.resolve(FileManager.resolve(userPrefix, "Documents", EE_DIR));
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("bsd")) {
          userPath = FileManager.resolve(FileManager.resolve(userPrefix, ".local", "share", EE_DIR));
        }
        if (allowMissing || (userPath != null && Files.isDirectory(userPath))) {
          return userPath;
        }
      }
    }
    return null;
  }

  /** Attempts to find folders containing BIFF archives. */
  static List<Path> getBIFFDirs()
  {
    List<Path> dirList = new ArrayList<>();

    if (Profile.isEnhancedEdition()) {
      // adding supported base biff folders
      Path langRoot = Profile.getLanguageRoot();
      if (langRoot != null) {
        dirList.add(langRoot);
      }
      List<Path> dlcList = Profile.getProperty(Profile.Key.GET_GAME_DLC_FOLDERS_AVAILABLE);
      if (dlcList != null) {
        dlcList.forEach((path) -> dirList.add(path));
      }
      dirList.add(Profile.getGameRoot());
    } else {
      // fetching the CD folders in a game installation
      Path iniFile = Profile.getProperty(Profile.Key.GET_GAME_INI_FILE);
      List<Path> rootFolders = Profile.getRootFolders();
      if (iniFile != null && Files.isRegularFile(iniFile)) {
        try (BufferedReader br = Files.newBufferedReader(iniFile)) {
          String line;
          while ((line = br.readLine()) != null) {
            if (line.contains(":=")) {
              String[] items = line.split(":=");
              if (items.length > 1) {
                int p = items[1].indexOf(';');
                if (p >= 0) {
                  line = items[1].substring(0, p).trim();
                } else {
                  line = items[1].trim();
                }
                if (line.endsWith(":")) {
                  line = line.replace(':', '/');
                }
                // Try to handle Unix paths
                Path path;
                if (line.charAt(0) == '/') {  // absolute Unix path
                  path = FileManager.resolve(line);
                  if (path == null || !Files.isDirectory(path)) { // try relative Unix path
                    path = FileManager.query(rootFolders, line);
                  }
                } else if (line.indexOf(':') < 0) { // relative Unix path
                  path = FileManager.query(rootFolders, line);
                } else {
                  path = FileManager.resolve(line);
                }
                if (Files.isDirectory(path)) {
                  dirList.add(path);
                }
              }
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
          dirList.clear();
        }
      }

      if (dirList.isEmpty()) {
        // Don't panic if an .ini-file cannot be found or contains errors
        dirList.addAll(rootFolders);
        Path path;
        for (int i = 1; i < 7; i++) {
          path = FileManager.query(rootFolders, "CD" + i);
          if (Files.isDirectory(path)) {
            dirList.add(path);
          }
        }
        // used in certain games
        path = FileManager.query(rootFolders, "CDALL");
        if (Files.isDirectory(path)) {
          dirList.add(path);
        }
      }
    }
    return dirList;
  }

  /** Returns the currently used language of an Enhanced Edition game. */
  static String fetchGameLanguage(Path iniFile)
  {
    final String langDefault = "en_US";   // using default language, if no language entry found

    if (Profile.isEnhancedEdition() && iniFile != null && Files.isRegularFile(iniFile)) {
      String lang = BrowserMenuBar.getInstance().getSelectedGameLanguage();

      if (lang == null || lang.isEmpty()) {
        return autodetectGameLanguage(iniFile);
      } else {
        // Using user-defined language
        if (lang.matches("[A-Za-z]{2}_[A-Za-z]{2}")) {
          Path path = FileManager.query(Profile.getGameRoot(), "lang", lang);
          if (path != null && Files.isDirectory(path)) {
            String retVal;
            try {
              // try to fetch the actual path name to ensure correct case
              retVal = path.toRealPath().getFileName().toString();
            } catch (Exception e) {
              retVal = lang;
            }
            return retVal;
          }
        }
      }
    }

    // falling back to default language
    return langDefault;
  }

  /** Used internally by the Profile class to open and initialize a new game. */
  static void openGame(Path keyFile)
  {
    closeGame();
    new ResourceFactory(keyFile);
  }

  /** Closes the current game configuration. */
  private static void closeGame()
  {
    if (instance != null) {
      instance.close();
      instance = null;
    }
  }

  private static ResourceFactory getInstance()
  {
    return instance;
  }


  private ResourceFactory(Path keyFile)
  {
    instance = this;
    try {
      // initializing primary key file
      this.keyfile = new Keyfile(keyFile);

      // adding DLC key files if available
      List<Path> keyList = Profile.getProperty(Profile.Key.GET_GAME_DLC_KEYS_AVAILABLE);
      if (keyList != null) {
        for (final Path key: keyList) {
          this.keyfile.addKeyfile(key);
        }
      }

      loadResourcesInternal();
      FileWatcher.getInstance().addFileWatchListener(this);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, "No Infinity Engine game found", "Error",
                                    JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
    }
  }

  /** Cleans up resources. */
  private void close()
  {
    FileWatcher.getInstance().removeFileWatchListener(this);
    // nothing to do yet...
  }

  private void exportResourceInternal(ResourceEntry entry, Component parent, Path output) throws Exception
  {
    try {
      ByteBuffer buffer = entry.getResourceBuffer();
      final String ext = entry.getExtension();
      if (ext.equalsIgnoreCase("IDS") || ext.equalsIgnoreCase("2DA") ||
          ext.equalsIgnoreCase("BIO") || ext.equalsIgnoreCase("RES") ||
          ext.equalsIgnoreCase("INI") || ext.equalsIgnoreCase("SET") ||
          ext.equalsIgnoreCase("TXT") ||
          (Profile.getEngine() == Profile.Engine.IWD2 && ext.equalsIgnoreCase("SRC")) ||
          (Profile.isEnhancedEdition() && (ext.equalsIgnoreCase("GUI") ||
                                           ext.equalsIgnoreCase("SQL") ||
                                           ext.equalsIgnoreCase("GLSL")))) {
        if (buffer.getShort(0) == -1) {
          exportResourceInternal(entry, StaticSimpleXorDecryptor.decrypt(buffer, 2), entry.getResourceName(), parent, output);
        } else {
          buffer.position(0);
          exportResourceInternal(entry, buffer, entry.getResourceName(), parent, output);
        }
      } else {
        exportResourceInternal(entry, buffer, entry.getResourceName(), parent, output);
      }
    } catch (Exception e) {
      throw new Exception("Can't read " + entry);
    }
  }

  private Path getExportFilePathInternal()
  {
    Path path = null;
    if (fc != null) {
      File file = fc.getCurrentDirectory();
      if (file != null) {
        path = file.toPath();
      }
    }
    if (path == null) {
      path = Profile.getGameRoot();
    }
    return path;
  }

  private Path getExportFileDialogInternal(Component parent, String fileName, boolean forceOverwrite)
  {
    Path path = null;
    if (fc == null) {
      fc = new JFileChooser(Profile.getGameRoot().toFile());
      fc.setDialogTitle("Export resource");
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }
    fc.setSelectedFile(new File(fc.getCurrentDirectory(), fileName));
    if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
      path = fc.getSelectedFile().toPath();
      if (!forceOverwrite && Files.exists(path)) {
        final String options[] = {"Overwrite", "Cancel"};
        if (JOptionPane.showOptionDialog(parent, path + " exists. Overwrite?", "Export resource",
                                         JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                                         null, options, options[0]) != 0) {
          path = null;
        }
      }
    }
    return path;
  }

  private void exportResourceInternal(ResourceEntry entry, ByteBuffer buffer, String fileName,
                                      Component parent, Path output) throws Exception
  {
    // ask for output file path if needed
    boolean interactive = (output == null);
    if (interactive) {
      output = getExportFileDialogInternal(parent, fileName, false);
    }

    // exporting resource
    if (output != null) {
      try {
        if (output.getFileName().toString().equalsIgnoreCase(entry.getResourceName())) {
          setPendingSelection(output);
        }
        try (OutputStream os = StreamUtils.getOutputStream(output, true)) {
          StreamUtils.writeBytes(os, buffer);
        }
        if (interactive) {
          JOptionPane.showMessageDialog(parent, "File exported to " + output, "Export complete",
                                        JOptionPane.INFORMATION_MESSAGE);
        }
      } catch (IOException e) {
        setPendingSelection(null);
        throw new Exception("Error while exporting " + entry);
      }
    }
  }

  private void unregisterResourceInternal(Path resource)
  {
    if (!BrowserMenuBar.getInstance().showUnknownResourceTypes() &&
        !Profile.isResourceTypeSupported(FileManager.getFileExtension(resource))) {
      return;
    }
    if (resource == null) {
      return;
    }
    ResourceEntry selectedEntry = NearInfinity.getInstance().getResourceTree().getSelected();

    // 1. checking extra folders <- skipped because of issues on Windows systems
//    List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
//    Path match = FileManager.getContainedPath(resource, extraPaths);
//    if (match != null) {
//      // finding correct subfolder
//      Path resPath = resource.getParent();
//      int startIdx = match.getNameCount() - 1; // include main folder
//      int endIdx = resPath.getNameCount();
//      Path subPath = resPath.subpath(startIdx, endIdx);
//
//      ResourceTreeFolder folder = (ResourceTreeFolder)treeModel.getRoot();
//      for (int idx = 0, cnt = subPath.getNameCount(); idx < cnt && folder != null; idx++) {
//        String folderName = subPath.getName(idx).toString();
//        List<ResourceTreeFolder> folders = folder.getFolders();
//        folder = null;
//        for (final ResourceTreeFolder subFolder: folders) {
//          if (folderName.equalsIgnoreCase(subFolder.folderName())) {
//            folder = subFolder;
//            break;
//          }
//        }
//      }
//
//      if (folder != null && folder.folderName().equalsIgnoreCase(resPath.getFileName().toString())) {
//        for (final ResourceEntry entry: folder.getResourceEntries()) {
//          if (entry.getResourceName().equalsIgnoreCase(resource.getFileName().toString())) {
//            folder.removeResourceEntry(entry);
//            if (folder.getChildCount() == 0) {
//              ResourceTreeFolder parentFolder = folder.getParentFolder();
//              parentFolder.removeFolder(folder);
//              treeModel.updateFolders(parentFolder);
//            } else {
//              treeModel.updateFolders(folder);
//            }
//            if (selectedEntry != null && !selectedEntry.equals(entry)) {
//              NearInfinity.getInstance().getResourceTree().select(selectedEntry, true);
//            }
//            return;
//          }
//        }
//      }
//    }

    // 2. checking override
    ResourceEntry entry = getResourceEntry(resource.getFileName().toString(), true);
    if (entry != null) {
      ResourceTreeFolder folder = entry.getTreeFolder();
      String name = entry.getTreeFolderName();
      treeModel.removeResourceEntry(entry, name);

      if (entry instanceof FileResourceEntry) {
        Path newPath = FileManager.queryExisting(Profile.getOverrideFolders(true), entry.getResourceName());
        if (newPath != null) {
          // another override file found
          treeModel.addResourceEntry(new FileResourceEntry(newPath, entry.hasOverride()), folder.folderName(), true);
          treeModel.updateFolders(folder);
        } else {
          // handle potential BIFF resource
          BIFFResourceEntry newEntry = keyfile.getResourceEntry(entry.getResourceName());
          if (newEntry != null) {
            newEntry.setOverride(false);
            treeModel.addResourceEntry(newEntry, newEntry.getTreeFolderName(), true);
            treeModel.updateFolders(newEntry.getTreeFolder());
          }
        }
      }

      if (folder.getChildCount() == 0) {
        ResourceTreeFolder parentFolder = folder.getParentFolder();
        parentFolder.removeFolder(folder);
        treeModel.updateFolders(parentFolder);
      } else {
        treeModel.updateFolders(folder);
      }

      if (selectedEntry != null) {
        if (selectedEntry.equals(entry)) {
          selectedEntry = treeModel.getResourceEntry(selectedEntry.getResourceName(), true);
        }
        NearInfinity.getInstance().getResourceTree().select(selectedEntry, true);
        if (selectedEntry == null) {
          NearInfinity.getInstance().setViewable(null);
        }
      }
    }
  }

  private void registerResourceInternal(Path resource, boolean autoselect)
  {
    if (!BrowserMenuBar.getInstance().showUnknownResourceTypes() &&
        !Profile.isResourceTypeSupported(FileManager.getFileExtension(resource))) {
      return;
    }
    if (resource == null || !Files.isRegularFile(resource)) {
      return;
    }

    // 1. checking if resource has already been added to resource tree
    ResourceEntry entry = treeModel.getResourceEntry(resource.getFileName().toString(), true);
    if (entry != null) {
      boolean match = false;
      if (entry instanceof BIFFResourceEntry) {
        boolean overrideInOverride = (BrowserMenuBar.getInstance() != null &&
                                      BrowserMenuBar.getInstance().getOverrideMode() == BrowserMenuBar.OVERRIDE_IN_OVERRIDE);
        if (overrideInOverride && entry.getTreeFolderName().equalsIgnoreCase(Profile.getOverrideFolderName())) {
          match = true;
        }
      } else if (resource.equals(entry.getActualPath())) {
        match = true;
      }
      if (match) {
        if (autoselect) {
          NearInfinity.getInstance().showResourceEntry(entry);
        }
        return;
      }
    }
    ResourceEntry selectedEntry = NearInfinity.getInstance().getResourceTree().getSelected();
    Path resPath = resource.getParent();

    // 2. checking extra folders <- skipped because of issues on Windows systems
//    List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
//    Path match = FileManager.getContainedPath(resource, extraPaths);
//    if (match != null) {
//      // finding correct subfolder
//      int startIdx = match.getNameCount() - 1; // include main folder
//      int endIdx = resPath.getNameCount();
//      Path subPath = resPath.subpath(startIdx, endIdx);
//
//      ResourceTreeFolder parentFolder = null;
//      ResourceTreeFolder folder = (ResourceTreeFolder)treeModel.getRoot();
//      for (int idx = 0, cnt = subPath.getNameCount(); idx < cnt && folder != null; idx++) {
//        String folderName = subPath.getName(idx).toString();
//        List<ResourceTreeFolder> folders = folder.getFolders();
//        parentFolder = folder;
//        folder = null;
//
//        for (final ResourceTreeFolder subFolder: folders) {
//          if (folderName.equalsIgnoreCase(subFolder.folderName())) {
//            folder = subFolder;
//            break;
//          }
//        }
//
//        if (folder == null) {
//          folder = treeModel.addFolder(parentFolder, folderName);
//        }
//      }
//
//      if (folder != null && folder.folderName().equalsIgnoreCase(resPath.getFileName().toString())) {
//        ResourceEntry newEntry = new FileResourceEntry(resource, false);
//        folder.addResourceEntry(newEntry, true);
//        folder.sortChildren(false);
//        treeModel.updateFolders(folder);
//        if (autoselect) {
//          NearInfinity.getInstance().showResourceEntry(newEntry);
//        } else if (selectedEntry != null) {
//          NearInfinity.getInstance().getResourceTree().select(selectedEntry, true);
//        }
//        return;
//      }
//    }

    // 3. checking override folders
    if (FileManager.isSamePath(resPath, Profile.getOverrideFolders(true))) {
      entry = getResourceEntry(resource.getFileName().toString());
      String folderName = null;
      if (entry instanceof BIFFResourceEntry) {
        boolean overrideInOverride = (BrowserMenuBar.getInstance() != null &&
                                      BrowserMenuBar.getInstance().getOverrideMode() == BrowserMenuBar.OVERRIDE_IN_OVERRIDE);
        if (overrideInOverride) {
          treeModel.removeResourceEntry(entry, entry.getExtension());
        }
        folderName = overrideInOverride ? Profile.getOverrideFolderName() : entry.getExtension();
        entry = new FileResourceEntry(resource, true);
      } else {
        folderName = (entry != null) ? entry.getTreeFolderName() : resPath.getFileName().toString();
        entry = new FileResourceEntry(resource, entry != null && entry.hasOverride());
      }
      treeModel.addResourceEntry(entry, folderName, true);
      treeModel.getFolder(folderName).sortChildren(false);
      treeModel.updateFolders(treeModel.getFolder(folderName));
      if (autoselect) {
        NearInfinity.getInstance().showResourceEntry(entry);
      } else if (selectedEntry != null) {
        if (entry.equals(selectedEntry)) {
          selectedEntry = entry;
        }
        NearInfinity.getInstance().getResourceTree().select(selectedEntry, true);
      }
    }
  }

  private boolean isPendingSelection(Path path, boolean autoRemove)
  {
    boolean retVal = (pendingSelection == path);

    if (pendingSelection != null && path != null) {
      retVal = path.equals(pendingSelection);
      if (retVal && autoRemove) {
        pendingSelection = null;
      }
    }

    return retVal;
  }

  private void setPendingSelection(Path path)
  {
    if (BrowserMenuBar.getInstance() != null &&
        BrowserMenuBar.getInstance().getMonitorFileChanges()) {
      pendingSelection = path;
    }
  }

  private void loadResourcesInternal() throws Exception
  {
    treeModel = new ResourceTreeModel();

    // Get resources from keyfile
    NearInfinity.advanceProgress("Loading BIFF resources...");
    keyfile.populateResourceTree(treeModel);

    // Add resources from extra folders
    NearInfinity.advanceProgress("Loading extra resources...");
    List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    extraPaths.forEach((path) -> {
      if (Files.isDirectory(path)) {
        treeModel.addDirectory(treeModel.getRoot(), path, false);
      }
    });

    NearInfinity.advanceProgress("Loading override resources...");
    final boolean overrideInOverride = (BrowserMenuBar.getInstance() != null &&
                                        BrowserMenuBar.getInstance().getOverrideMode() == BrowserMenuBar.OVERRIDE_IN_OVERRIDE);
    String overrideFolder = Profile.getOverrideFolderName();
    List<Path> overridePaths = Profile.getOverrideFolders(false);
    for (final Path overridePath: overridePaths) {
      if (Files.isDirectory(overridePath)) {
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(overridePath)) {
          dstream.forEach((path) -> {
            if (Files.isRegularFile(path)) {
              ResourceEntry entry = getResourceEntry(path.getFileName().toString());
              if (entry instanceof FileResourceEntry) {
                treeModel.addResourceEntry(entry, entry.getTreeFolderName(), true);
              } else if (entry instanceof BIFFResourceEntry) {
                ((BIFFResourceEntry)entry).setOverride(true);
                if (overrideInOverride) {
                  treeModel.removeResourceEntry(entry, entry.getExtension());
                  treeModel.addResourceEntry(new FileResourceEntry(path, true), overrideFolder, true);
                }
              }
            }
          });
        }
      }
    }
    loadSpecialResources();
    treeModel.sort();
  }

  /**
   * Registers in the resourse tree all special game resources that are not stored
   * in the override folders or BIF archives
   *
   * @param folderName Folder in the resource tree under which register files
   */
  private void loadSpecialResources()
  {
    final List<Path> roots = Profile.getRootFolders();
    final Profile.Game game = Profile.getGame();
    final Profile.Engine engine = Profile.getEngine();

    addFileResource(Profile.getProperty(Profile.Key.GET_GAME_INI_FILE));
    addFileResource(FileManager.query(roots, "WeiDU.log")); // Installed WeiDU mods

    switch (engine) {
      case EE:
        addFileResource(FileManager.query(roots, "engine.lua"));
        break;
      case BG2:
        addFileResource(FileManager.query(roots, "Autorun.ini"));
        break;
      case IWD:
        addFileResource(FileManager.query(roots, "Language.ini"));
        break;
      case IWD2:
        addFileResource(FileManager.query(roots, "Language.ini"));
        addFileResource(FileManager.query(roots, "Party.ini"));
        break;
      case PST:
        addFileResource(FileManager.query(roots, "autonote.ini"));
        addFileResource(FileManager.query(roots, "beast.ini"));// Bestiary
        addFileResource(FileManager.query(roots, "quests.ini"));
        addFileResource(FileManager.query(roots, "VAR.VAR"));
        break;
      default:
    }

    if (engine != Profile.Engine.EE) {
      addFileResource(FileManager.query(roots, "Keymap.ini"));// Key shortcuts
    }

    if (game == Profile.Game.EET) {
      addFileResource(FileManager.query(roots, "WeiDU-BGEE.log"));
    }
  }
  /**
   * Register specified path as file resource is such path points to regular file
   *
   * @param path Path to register
   */
  private void addFileResource(Path path)
  {
    if (path != null && Files.isRegularFile(path)) {
      treeModel.addResourceEntry(new FileResourceEntry(path), SPECIAL_CATEGORY, false);
    }
  }

  private List<ResourceEntry> getResourcesInternal(String type, List<Path> extraDirs)
  {
    List<ResourceEntry> list;
    ResourceTreeFolder bifNode = treeModel.getFolder(type);
    if (bifNode != null) {
      list = new ArrayList<>(bifNode.getResourceEntries());
    } else {
      list = new ArrayList<>();
    }
    int initsize = list.size();

    // include extra folders
    if (extraDirs == null) {
      extraDirs = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    }
    extraDirs.forEach(path -> {
      ResourceTreeFolder extraNode = treeModel.getFolder(path.getFileName().toString());
      if (extraNode != null) {
        list.addAll(extraNode.getResourceEntries(type));
      }
    });
    // Include special files
    final ResourceTreeFolder specialNode = treeModel.getFolder(SPECIAL_CATEGORY);
    if (specialNode != null) {
      list.addAll(specialNode.getResourceEntries(type));
    }

    // include override folders
    if (BrowserMenuBar.getInstance() != null && !BrowserMenuBar.getInstance().ignoreOverrides()) {
      ResourceTreeFolder overrideNode = treeModel.getFolder(Profile.getOverrideFolderName());
      if (overrideNode != null) {
        list.addAll(overrideNode.getResourceEntries(type));
      }
    }

    if (list.size() > initsize) {
      Collections.sort(list);
    }
    return list;
  }

  private List<ResourceEntry> getResourcesInternal(Pattern pattern, List<Path> extraDirs)
  {
    List<ResourceEntry> retList = new ArrayList<>();

    String[] resTypes = Profile.getAvailableResourceTypes();
    for (final String type: resTypes) {
      ResourceTreeFolder bifNode = treeModel.getFolder(type);
      if (bifNode != null) {
        List<ResourceEntry> list = bifNode.getResourceEntries();
        list.forEach(entry -> {
          if (pattern == null || pattern.matcher(entry.getResourceName()).matches()) {
            retList.add(entry);
          }
        });
      }
    }

    // include extra folders
    if (extraDirs == null) {
      extraDirs = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    }
    extraDirs.forEach(path -> {
      ResourceTreeFolder extraNode = treeModel.getFolder(path.getFileName().toString());
      if (extraNode != null) {
        List<ResourceEntry> list = extraNode.getResourceEntries();
        list.forEach(entry -> {
          if (pattern == null || pattern.matcher(entry.getResourceName()).matches()) {
            retList.add(entry);
          }
        });
      }
    });

    // include override folders
    if (BrowserMenuBar.getInstance() != null && !BrowserMenuBar.getInstance().ignoreOverrides()) {
      ResourceTreeFolder overrideNode = treeModel.getFolder(Profile.getOverrideFolderName());
      if (overrideNode != null) {
        List<ResourceEntry> list = overrideNode.getResourceEntries();
        list.forEach(entry -> {
          if (pattern == null || pattern.matcher(entry.getResourceName()).matches()) {
            retList.add(entry);
          }
        });
      }
    }

    if (retList.size() > 1) {
      Collections.sort(retList);
    }

    return retList;
  }

  private void saveCopyOfResourceInternal(ResourceEntry entry)
  {
    String fileName;
    do {
      fileName = (String)JOptionPane.showInputDialog(NearInfinity.getInstance(), "Enter new filename",
                                                     "Add copy of " + entry.getResourceName(),
                                                     JOptionPane.QUESTION_MESSAGE,
                                                     null, null, entry.getResourceName());
      if (fileName != null) {
        if (!fileName.contains(".")) {
          fileName += '.' + entry.getExtension();
        }
        if (fileName.lastIndexOf('.') > 8) {
          JOptionPane.showMessageDialog(NearInfinity.getInstance(),
                                        "Filenames can only be up to 8 characters long (not including the file extension).",
                                        "Error", JOptionPane.ERROR_MESSAGE);
          fileName = null;
        }
      } else {
        return;
      }
    } while (fileName == null);

    final Path outPath = FileManager.query(Profile.getGameRoot(), Profile.getOverrideFolderName().toLowerCase(Locale.ENGLISH));
    Path outFile = outPath.resolve(fileName);
    if (entry.getExtension().equalsIgnoreCase("bs")) {
      outFile = FileManager.query(Profile.getGameRoot(), "Scripts", fileName);
    }

    if (Files.exists(outFile)) {
      String options[] = {"Overwrite", "Cancel"};
      if (JOptionPane.showOptionDialog(NearInfinity.getInstance(), outFile + " exists. Overwrite?",
                                       "Confirm overwrite " + outFile, JOptionPane.YES_NO_OPTION,
                                       JOptionPane.WARNING_MESSAGE, null, options, options[0]) != 0)
        return;
    }

    // creating override folder in game directory if it doesn't exist
    if (!Files.isDirectory(outPath)) {
      try {
        Files.createDirectory(outPath);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Could not create " + outPath + ".",
                                      "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
        return;
      }
    }

    try {
      setPendingSelection(outFile);
      ByteBuffer bb = entry.getResourceBuffer();
      try (OutputStream os = StreamUtils.getOutputStream(outFile, true)) {
        WritableByteChannel wbc = Channels.newChannel(os);
        wbc.write(bb);
      }
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), entry + " copied to " + outFile,
                                    "Copy complete", JOptionPane.INFORMATION_MESSAGE);
      ResourceEntry newEntry = new FileResourceEntry(outFile, !entry.getExtension().equalsIgnoreCase("bs"));
      treeModel.addResourceEntry(newEntry, newEntry.getTreeFolderName(), true);
      treeModel.sort();
      if (BrowserMenuBar.getInstance().getKeepViewOnCopy()) {
        NearInfinity.getInstance().showResourceEntry(entry);
      } else {
        NearInfinity.getInstance().showResourceEntry(newEntry);
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error while copying " + entry,
                                    "Error", JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
    }
  }

  private boolean saveResourceInternal(Resource resource, Component parent)
  {
    if (!(resource instanceof Writeable)) {
      JOptionPane.showMessageDialog(parent, "Resource not savable", "Error", JOptionPane.ERROR_MESSAGE);
      return false;
    }
    final ResourceEntry entry = resource.getResourceEntry();
    if (entry == null) {
      return false;
    }
    Path outPath;
    if (entry instanceof BIFFResourceEntry) {
      Path overridePath = FileManager.query(Profile.getGameRoot(), Profile.getOverrideFolderName());
      if (!Files.isDirectory(overridePath)) {
        try {
          Files.createDirectory(overridePath);
        } catch (IOException e) {
          JOptionPane.showMessageDialog(parent, "Unable to create override folder.",
                                        "Error", JOptionPane.ERROR_MESSAGE);
          e.printStackTrace();
          return false;
        }
      }
      outPath = FileManager.query(overridePath, entry.getResourceName());
      ((BIFFResourceEntry)entry).setOverride(true);
    } else {
      outPath = entry.getActualPath();
      // extra step for saving resources from a read-only medium (such as DLCs)
      if (!FileManager.isDefaultFileSystem(outPath)) {
        outPath = Profile.getGameRoot().resolve(outPath.subpath(0, outPath.getNameCount()).toString());
        if (outPath != null && !Files.exists(outPath.getParent())) {
          try {
            Files.createDirectories(outPath.getParent());
          } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Unable to create folder: " + outPath.getParent(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
          }
        }
      }
    }
    if (Files.exists(outPath)) {
      outPath = outPath.toAbsolutePath();
      String options[] = {"Overwrite", "Cancel"};
      if (JOptionPane.showOptionDialog(parent, outPath + " exists. Overwrite?", "Save resource",
                                       JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                                       null, options, options[0]) == 0) {
        if (BrowserMenuBar.getInstance().backupOnSave()) {
          try {
            Path bakPath = outPath.getParent().resolve(outPath.getFileName() + ".bak");
            if (Files.isRegularFile(bakPath)) {
              Files.delete(bakPath);
            }
            if (!Files.exists(bakPath)) {
              Files.move(outPath, bakPath);
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      } else {
        return false;
      }
    }
    try (OutputStream os = StreamUtils.getOutputStream(outPath, true)) {
      ((Writeable)resource).write(os);
    } catch (IOException e) {
      JOptionPane.showMessageDialog(parent, "Error while saving " + entry,
                                    "Error", JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
      return false;
    }
    JOptionPane.showMessageDialog(parent, "File saved to \"" + outPath.toAbsolutePath() + '\"',
                                  "Save complete", JOptionPane.INFORMATION_MESSAGE);
    if ("IDS".equals(entry.getExtension())) {
      IdsMapCache.remove(entry);
      final IdsBrowser idsbrowser = ChildFrame.getFirstFrame(IdsBrowser.class);
      if (idsbrowser != null) {
        idsbrowser.refreshList();
      }
      CreMapCache.reset();
    } else if (entry.getResourceName().equalsIgnoreCase(Song2daBitmap.getTableName())) {
      Song2daBitmap.resetSonglist();
    } else if (entry.getResourceName().equalsIgnoreCase(Summon2daBitmap.getTableName())) {
      Summon2daBitmap.resetSummonTable();
    } else if (entry.getResourceName().equalsIgnoreCase(PriTypeBitmap.getTableName())) {
      PriTypeBitmap.resetTypeTable();
    } else if (entry.getResourceName().equalsIgnoreCase(SecTypeBitmap.getTableName())) {
      SecTypeBitmap.resetTypeTable();
    }
    return true;
  }

//--------------------- Begin Interface FileWatchListener ---------------------

  @Override
  public void fileChanged(FileWatchEvent e)
  {
//    System.out.println("ResourceFactory.fileChanged(): " + e.getKind().toString() + " - " + e.getPath());
    if (e.getKind() == StandardWatchEventKinds.ENTRY_CREATE) {
      registerResourceInternal(e.getPath(), isPendingSelection(e.getPath(), true));
    } else if (e.getKind() == StandardWatchEventKinds.ENTRY_DELETE) {
      unregisterResourceInternal(e.getPath());
    }
  }

//--------------------- End Interface FileWatchListener ---------------------
}
