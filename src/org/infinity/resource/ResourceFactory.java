// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.infinity.exceptions.AbortException;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.IdsBrowser;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.gui.menu.OverrideMode;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.are.viewer.VirtualMap;
import org.infinity.resource.bcs.BafResource;
import org.infinity.resource.bcs.BcsResource;
import org.infinity.resource.chu.ChuResource;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.dlg.DlgResource;
import org.infinity.resource.fnt.FntResource;
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
import org.infinity.resource.text.QuestsResource;
import org.infinity.resource.to.TohResource;
import org.infinity.resource.to.TotResource;
import org.infinity.resource.var.VarResource;
import org.infinity.resource.vef.VefResource;
import org.infinity.resource.video.MveResource;
import org.infinity.resource.video.WbmResource;
import org.infinity.resource.wed.WedResource;
import org.infinity.resource.wmp.WmpResource;
import org.infinity.util.CreMapCache;
import org.infinity.util.DynamicArray;
import org.infinity.util.IdsMapCache;
import org.infinity.util.Logger;
import org.infinity.util.Misc;
import org.infinity.util.Platform;
import org.infinity.util.StaticSimpleXorDecryptor;
import org.infinity.util.TriState;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

/**
 * Handles game-specific resource access.
 */
public final class ResourceFactory {
  /**
   * Name of tree node that contains important game files that not stored in the BIF archives or override folders.
   */
  public static final String SPECIAL_CATEGORY = "Special";

  private static ResourceFactory instance;

  private JFileChooser fc;
  private Keyfile keyfile;
  private ResourceTreeModel treeModel;

  public static Keyfile getKeyfile() {
    if (getInstance() != null) {
      return getInstance().keyfile;
    } else {
      return null;
    }
  }

  /**
   * Opens a save dialog and returns the file path selected by the user.
   *
   * @param parent         Parent component of the file chooser dialog.
   * @param fileName       Filename shown when the dialog opens.
   * @param forceOverwrite Whether to skip asking for confirmation if file already exists.
   * @return The path to the selected file. Returns {@code null} if user cancelled operation.
   */
  public static Path getExportFileDialog(Component parent, String fileName, boolean forceOverwrite) {
    return getInstance().getExportFileDialogInternal(parent, fileName, forceOverwrite);
  }

  /**
   * Returns the file path of the last export operation. Returns the root folder of the game otherwise.
   *
   * @return Directory as {@link Path} object.
   */
  public static Path getExportFilePath() {
    return getInstance().getExportFilePathInternal();
  }

  /**
   * Returns the default path for saving the specified resource.
   *
   * @param entry {@link ResourceEntry} instance of a resource.
   * @return Output {@link Path} for the specified resource. Returns a directory path if {@code entry} is {@code null}.
   */
  public static Path getDefaultSavePath(ResourceEntry entry) {
    Path retVal;

    if (entry instanceof FileResourceEntry) {
      retVal = entry.getActualPath();
      if (!FileManager.isDefaultFileSystem(retVal)) {
        retVal = Profile.getGameRoot().resolve(retVal.subpath(0, retVal.getNameCount()));
      }
    } else if (entry != null) {
      retVal = FileManager.query(Profile.getGameRoot(), Profile.getOverrideFolderName(), getResourceName(entry));
    } else {
      retVal = Profile.getGameRoot().resolve(Profile.getOverrideFolderName());
    }

    return retVal;
  }

  /**
   * Returns the filename of the given resource entry in the case as specified by the preferences.
   *
   * @param entry {@link ResourceEntry} to retrieve filename from. Returns {@code null} if filename is not available.
   * @return Filename in the expected case.
   */
  public static String getResourceName(ResourceEntry entry) {
    if (entry != null) {
      String name = entry.getResourceName();
      if (name != null &&
          BrowserMenuBar.getInstance().getOptions().useLowerCasedExport() &&
          entry instanceof BIFFResourceEntry) {
        name = name.toLowerCase(Locale.ROOT);
      }
      return name;
    }
    return null;
  }

  public static Class<? extends Resource> getResourceType(ResourceEntry entry) {
    return getResourceType(entry, null);
  }

  public static Class<? extends Resource> getResourceType(ResourceEntry entry, String forcedExtension) {
    Class<? extends Resource> cls = null;
    if (entry != null) {
      String ext = ((forcedExtension != null) ? forcedExtension : entry.getExtension()).toUpperCase();
      if (ext.equals("BAM")) {
        cls = BamResource.class;
      } else if (ext.equals("TIS")) {
        cls = TisResource.class;
      } else if (ext.equals("BMP") || ext.equals("PNG")) {
        cls = GraphicsResource.class;
      } else if (ext.equals("MOS")) {
        cls = MosResource.class;
      } else if (ext.equals("WAV") || ext.equals("ACM")) {
        cls = SoundResource.class;
      } else if (ext.equals("MUS")) {
        cls = MusResource.class;
      } else if (ext.equals("IDS") || ext.equals("2DA") || ext.equals("BIO") || ext.equals("RES") || ext.equals("TXT")
          || ext.equals("LOG") // WeiDU log files
          || (ext.equals("SRC") && Profile.getEngine() == Profile.Engine.IWD2)
          || (ext.equals("LUA") && (Profile.isEnhancedEdition() || Profile.getGame() == Profile.Game.IWD2EE))
          || (Profile.isEnhancedEdition()
              && (ext.equals("SQL") || ext.equals("GUI") || ext.equals("MENU") || ext.equals("GLSL")))) {
        cls = PlainTextResource.class;
      } else if (ext.equals("INI")) {
        final boolean isPST = Profile.getEngine() == Profile.Engine.PST;
        cls = isPST && QuestsResource.RESOURCE_NAME.equals(entry.getResourceName()) ? QuestsResource.class
            : PlainTextResource.class;
      } else if (ext.equals("MVE")) {
        cls = MveResource.class;
      } else if (ext.equals("WBM")) {
        cls = WbmResource.class;
      } else if (ext.equals("PLT") && ext.equals(forcedExtension)) {
        cls = PltResource.class;
      } else if (ext.equals("BCS") || ext.equals("BS")) {
        cls = BcsResource.class;
      } else if (ext.equals("ITM")) {
        cls = ItmResource.class;
      } else if (ext.equals("EFF")) {
        cls = EffResource.class;
      } else if (ext.equals("VEF")) {
        cls = VefResource.class;
      } else if (ext.equals("VVC")) {
        cls = VvcResource.class;
      } else if (ext.equals("SRC")) {
        cls = SrcResource.class;
      } else if (ext.equals("DLG")) {
        cls = DlgResource.class;
      } else if (ext.equals("SPL")) {
        cls = SplResource.class;
      } else if (ext.equals("STO")) {
        cls = StoResource.class;
      } else if (ext.equals("WMP")) {
        cls = WmpResource.class;
      } else if (ext.equals("CHU")) {
        cls = ChuResource.class;
      } else if (ext.equals("CRE") || ext.equals("CHR")) {
        cls = CreResource.class;
      } else if (ext.equals("ARE")) {
        cls = AreResource.class;
      } else if (ext.equals("WFX")) {
        cls = WfxResource.class;
      } else if (ext.equals("PRO")) {
        cls = ProResource.class;
      } else if (ext.equals("WED")) {
        cls = WedResource.class;
      } else if (ext.equals("GAM")) {
        cls = GamResource.class;
      } else if (ext.equals("SAV")) {
        cls = SavResource.class;
      } else if (ext.equals("VAR")) {
        cls = VarResource.class;
      } else if (ext.equals("BAF")) {
        cls = BafResource.class;
      } else if (ext.equals("TOH")) {
        cls = TohResource.class;
      } else if (ext.equals("TOT")) {
        cls = TotResource.class;
      } else if (ext.equals("PVRZ")) {
        cls = Profile.isEnhancedEdition() ? PvrzResource.class : UnknownResource.class;
      } else if (ext.equals("FNT")) {
        cls = Profile.isEnhancedEdition() ? FntResource.class : UnknownResource.class;
      } else if (ext.equals("TTF")) {
        cls = Profile.isEnhancedEdition() ? TtfResource.class : UnknownResource.class;
      } else if (ext.equals("MAZE")) {
        cls = (Profile.getGame() == Profile.Game.PSTEE) ? MazeResource.class : UnknownResource.class;
      } else if (ext.equals("VMAP")) {
        cls = VirtualMap.class;
      } else {
        cls = detectResourceType(entry);
        if (cls == null) {
          cls = UnknownResource.class;
        }
      }
    }
    return cls;
  }

  public static Resource getResource(ResourceEntry entry) {
    return getResource(entry, null);
  }

  public static Resource getResource(ResourceEntry entry, String forcedExtension) {
    Resource res = null;
    try {
      Class<? extends Resource> cls = getResourceType(entry, forcedExtension);
      if (cls != null) {
        Constructor<? extends Resource> con = cls.getConstructor(ResourceEntry.class);
        res = con.newInstance(entry);
      }
    } catch (Exception e) {
      if (NearInfinity.getInstance() != null && !BrowserMenuBar.getInstance().getOptions().ignoreReadErrors()) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error reading " + entry + '\n' + e.getMessage(),
            "Error", JOptionPane.ERROR_MESSAGE);
      } else {
        final String msg = String.format("Error reading %s @ %s - %s", entry, entry.getActualPath(), e);
        NearInfinity.getInstance().getStatusBar().setMessage(msg);
      }
      Logger.error(e, "Error reading {}", entry);
    }
    return res;
  }

  /**
   * Returns the BAM {@link ResourceEntry} of the icon associated with the specified resource.
   * <p>
   * <strong>Note:</strong> Only {@code ITM} and {@code SPL} resources will return icons if available.
   * </p>
   *
   * @param entry The {@code ResourceEntry} of the resource.
   * @return BAM {@link ResourceEntry} of the icon associated with the resource. Returns {@code null} if icon is not
   *         defined by the resource.
   * @throws FileNotFoundException if an icon resource is defined but doesn't exist in the game.
   */
  public static ResourceEntry getResourceIcon(ResourceEntry entry) throws FileNotFoundException {
    return getResourceIcon(entry, null);
  }

  /**
   * Returns the BAM {@link ResourceEntry} of the icon associated with the specified resource.
   * <p>
   * <strong>Note:</strong> Only {@code ITM} and {@code SPL} resources will return icons if available.
   * </p>
   *
   * @param entry           The {@code ResourceEntry} of the resource.
   * @param forcedExtension Optional file extension string that is used to override the original file type.
   * @return BAM {@link ResourceEntry} of the icon associated with the resource. Returns {@code null} if icon is not
   *         defined by the resource.
   * @throws FileNotFoundException if an icon resource is defined but doesn't exist in the game.
   */
  public static ResourceEntry getResourceIcon(ResourceEntry entry, String forcedExtension) throws FileNotFoundException {
    ResourceEntry retVal = null;

    Class<? extends Resource> clsResource = getResourceType(entry, forcedExtension);
    if (clsResource != null &&
      (ItmResource.class.isAssignableFrom(clsResource) || SplResource.class.isAssignableFrom(clsResource))) {
      try {
        final ByteBuffer buf = entry.getResourceBuffer();
        String iconResref = StreamUtils.readString(buf, 0x3a, 8);
        if (!iconResref.isEmpty()) {
          final ResourceEntry iconEntry = ResourceFactory.getResourceEntry(iconResref + ".BAM");
          if (iconEntry != null) {
            retVal = iconEntry;
          } else {
            // invalid resref
            throw new FileNotFoundException("Resource does not exist: " + iconResref + ".BAM");
          }
        }
      } catch (FileNotFoundException e) {
        // forward exception to caller
        throw e;
      } catch (Exception e) {
        Logger.error(e);
      }
    }

    return retVal;
  }

  /**
   * Attempts to detect the resource type from the data itself and returns the respective resource class type, or
   * {@code null} on failure.
   *
   * @param entry {@code ResourceEntry} instance of the undetermined resource.
   * @return The class type of the specified resource, or {@code null} if type could not be determined.
   */
  public static Class<? extends Resource> detectResourceType(ResourceEntry entry) {
    Class<? extends Resource> cls = null;
    if (entry != null) {
      try {
        int[] info = entry.getResourceInfo();
        if (info.length == 2) {
          cls = getResourceType(entry, "TIS");
        } else if (info.length == 1) {
          if (info[0] > 4) {
            byte[] data = new byte[Math.min(info[0], 24)];
            try (InputStream is = entry.getResourceDataAsStream()) {
              StreamUtils.readBytes(is, data);
            }
            String sig = DynamicArray.getString(data, 0, 4);
            if ("2DA ".equalsIgnoreCase(sig)) {
              cls = getResourceType(entry, "2DA");
            } else if ("ARE ".equals(sig)) {
              cls = getResourceType(entry, "ARE");
            } else if ("BAM ".equals(sig) || "BAMC".equals(sig)) {
              cls = getResourceType(entry, "BAM");
            } else if ("CHR ".equals(sig)) {
              cls = getResourceType(entry, "CHR");
            } else if ("CHUI".equals(sig)) {
              cls = getResourceType(entry, "CHU");
            } else if ("CRE ".equals(sig)) {
              cls = getResourceType(entry, "CRE");
            } else if ("DLG ".equals(sig)) {
              cls = getResourceType(entry, "DLG");
            } else if ("EFF ".equals(sig)) {
              cls = getResourceType(entry, "EFF");
            } else if ("GAME".equals(sig)) {
              cls = getResourceType(entry, "GAM");
            } else if ("IDS ".equalsIgnoreCase(sig)) {
              cls = getResourceType(entry, "IDS");
            } else if ("ITM ".equals(sig)) {
              cls = getResourceType(entry, "ITM");
            } else if ("MAZE".equals(sig)) {
              cls = getResourceType(entry, "MAZE");
            } else if ("MOS ".equals(sig) || "MOSC".equals(sig)) {
              cls = getResourceType(entry, "MOS");
            } else if ("PLT ".equals(sig)) {
              cls = getResourceType(entry, "PLT");
            } else if ("PRO ".equals(sig)) {
              cls = getResourceType(entry, "PRO");
            } else if ("SAV ".equals(sig)) {
              cls = getResourceType(entry, "SAV");
            } else if ("SPL ".equals(sig)) {
              cls = getResourceType(entry, "SPL");
            } else if ("STOR".equals(sig)) {
              cls = getResourceType(entry, "STO");
            } else if ("TIS ".equals(sig)) {
              cls = getResourceType(entry, "TIS");
            } else if ("VEF ".equals(sig)) {
              cls = getResourceType(entry, "VEF");
            } else if ("VVC ".equals(sig)) {
              cls = getResourceType(entry, "VVC");
            } else if ("WAVC".equals(sig) || "RIFF".equals(sig) || "OggS".equals(sig)) {
              cls = getResourceType(entry, "WAV");
            } else if ("WED ".equals(sig)) {
              cls = getResourceType(entry, "WED");
            } else if ("WFX ".equals(sig)) {
              cls = getResourceType(entry, "WFX");
            } else if ("WMAP".equals(sig)) {
              cls = getResourceType(entry, "WMP");
            } else if ("VMAP".equals(sig)) {
              cls = getResourceType(entry, "VMAP");
            } else {
              if ((Arrays.equals(new byte[] { 0x53, 0x43, 0x0a }, Arrays.copyOfRange(data, 0, 3)) || // == "SC\n"
                  Arrays.equals(new byte[] { 0x53, 0x43, 0x0d, 0x0a }, Arrays.copyOfRange(data, 0, 4)))) { // ==
                                                                                                           // "SC\r\n"
                cls = getResourceType(entry, "BCS");
              } else if (data.length > 6 && "BM".equals(new String(data, 0, 2))
                  && DynamicArray.getInt(data, 2) == info[0]) {
                cls = getResourceType(entry, "BMP");
              } else if (data.length > 18 && "Interplay MVE File".equals(new String(data, 0, 18))) {
                cls = getResourceType(entry, "MVE");
              } else if (Arrays.equals(new byte[] { (byte) 0x1a, (byte) 0x45, (byte) 0xdf, (byte) 0xa3 },
                  Arrays.copyOfRange(data, 0, 4))) {
                cls = getResourceType(entry, "WBM");
              } else if (data.length > 6 && data[3] == 0 && data[4] == 0x78) { // just guessing...
                cls = getResourceType(entry, "PVRZ");
              } else if ((data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47) {
                cls = getResourceType(entry, "PNG");
              } else if (DynamicArray.getInt(data, 0) == 0x00000100) { // wild guess...
                cls = getResourceType(entry, "TTF");
              }
            }
          }
        } else {
          throw new Exception(entry.getResourceName() + ": Unable to determine resource type");
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return cls;
  }

  public static void exportResource(ResourceEntry entry, Component parent) {
    if (getInstance() != null) {
      try {
        getInstance().exportResourceInternal(entry, parent, null);
      } catch (Exception e) {
        Logger.error(e);
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void exportResource(ResourceEntry entry, ByteBuffer buffer, String filename, Component parent) {
    if (getInstance() != null) {
      try {
        getInstance().exportResourceInternal(entry, buffer, filename, parent, null);
      } catch (Exception e) {
        Logger.error(e);
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /** Exports "entry" to "output" without any user interaction. */
  public static void exportResource(ResourceEntry entry, Path output) throws Exception {
    if (getInstance() != null && output != null) {
      getInstance().exportResourceInternal(entry, null, output);
    }
  }

  /**
   * Returns whether the specified resource exists.
   *
   * @param resourceName The resource filename.
   * @return {@code true} if the resource exists in BIFF archives or override folders, {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName) {
    return (getResourceEntry(resourceName, false) != null);
  }

  /**
   * Returns whether the specified resource exists.
   *
   * @param resourceName    The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched. If {@code false}, only the
   *                        default 'override' folders will be searched.
   * @return {@code true} if the resource exists in BIFF archives or override folders, {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName, boolean searchExtraDirs) {
    return (getResourceEntry(resourceName, searchExtraDirs) != null);
  }

  /**
   * Returns whether the specified resource exists.
   *
   * @param resourceName    The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched. If {@code false}, only the
   *                        default 'override' folders will be searched.
   * @param extraDirs       A list of File entries pointing to additional folders to search, not covered by the default
   *                        override folder list (e.g. "install:/music").
   * @return {@code true} if the resource exists in BIFF archives or override folders, {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName, boolean searchExtraDirs, List<Path> extraDirs) {
    return (getResourceEntry(resourceName, searchExtraDirs, extraDirs) != null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   *
   * @param resourceName The resource filename.
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not available.
   */
  public static ResourceEntry getResourceEntry(String resourceName) {
    return getResourceEntry(resourceName, false, null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   *
   * @param resourceName    The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched. If {@code false}, only the
   *                        default 'override' folders will be searched.
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not available.
   */
  public static ResourceEntry getResourceEntry(String resourceName, boolean searchExtraDirs) {
    return getResourceEntry(resourceName, searchExtraDirs, null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   *
   * @param resourceName    The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched. If {@code false}, only the
   *                        default 'override' folders will be searched.
   * @param extraDirs       A list of File entries pointing to additional folders to search, not covered by the default
   *                        override folder list (e.g. "install:/music").
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not available.
   */
  public static ResourceEntry getResourceEntry(String resourceName, boolean searchExtraDirs, List<Path> extraDirs) {
    if (getInstance() != null) {
      ResourceEntry entry = getInstance().treeModel.getResourceEntry(resourceName);

      // checking default override folder list
      if (entry == null) {
        List<Path> extraFolders = Profile.getOverrideFolders(searchExtraDirs);
        Path file = FileManager.query(extraFolders, resourceName);
        if (file != null && FileEx.create(file).isFile()) {
          entry = new FileResourceEntry(file);
        }
      }

      // checking custom folder list
      if (extraDirs != null && (entry == null)) {
        Path file = FileManager.query(extraDirs, resourceName);
        if (file != null && FileEx.create(file).isFile()) {
          entry = new FileResourceEntry(file);
        }
      }

      return entry;
    } else {
      return null;
    }
  }

  /**
   * Attempts to determine the parent {@link ResourceEntry} of the specified {@link StructEntry} instance.
   *
   * @param struct A {@link StructEntry} instance.
   * @return {@code ResourceEntry} of the resource containing {@code struct}. Returns {@code null} if resource could not
   *         be determined.
   */
  public static ResourceEntry getResourceEntry(StructEntry struct) {
    ResourceEntry entry = null;

    while (struct != null) {
      if (struct instanceof AbstractStruct) {
        final AbstractStruct as = (AbstractStruct) struct;
        if (as.getResourceEntry() != null) {
          entry = as.getResourceEntry();
          break;
        }
      }
      struct = struct.getParent();
    }

    return entry;
  }

  /** Returns the resource tree model of the current game. */
  public static ResourceTreeModel getResourceTreeModel() {
    if (getInstance() != null) {
      return getInstance().treeModel;
    } else {
      return null;
    }
  }

  /**
   * Returns all resources of the specified resource type from BIFFs, extra and override directories.
   *
   * @param type Resource extension.
   */
  public static List<ResourceEntry> getResources(String type) {
    return getResources(type, null);
  }

  /**
   * Returns all resources of the specified resource type from BIFFs, override and specified extra directories.
   *
   * @param type      Resource extension.
   * @param extraDirs List of extra directories to search. Specify {@code null} to search default extra directories.
   */
  public static List<ResourceEntry> getResources(String type, List<Path> extraDirs) {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal(type, extraDirs);
    } else {
      return null;
    }
  }

  /**
   * Returns all available resources from BIFFs, extra and override directories from BIFFs, extra and override
   * directories.
   */
  public static List<ResourceEntry> getResources() {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal((Pattern) null, null);
    } else {
      return null;
    }
  }

  /**
   * Returns all available resources from BIFFs, override and specified extra directories.
   *
   * @param extraDirs List of extra directories to search. Specify {@code null} to search default extra directories.
   */
  public static List<ResourceEntry> getResources(List<Path> extraDirs) {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal((Pattern) null, extraDirs);
    } else {
      return null;
    }
  }

  /**
   * Returns all available resources from BIFFs, override and extra directories matching the specified regular
   * expression pattern.
   *
   * @param pattern RegEx pattern to filter available resources.
   */
  public static List<ResourceEntry> getResources(Pattern pattern) {
    return getResources(pattern, null);
  }

  /**
   * Returns all available resources from BIFFs, override and specified extra directories matching the specified regular
   * expression pattern.
   *
   * @param pattern   RegEx pattern to filter available resources.
   * @param extraDirs List of extra directories to search. Specify {@code null} to search default extra directories.
   */
  public static List<ResourceEntry> getResources(Pattern pattern, List<Path> extraDirs) {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal(pattern, extraDirs);
    } else {
      return null;
    }
  }

  public static void loadResources() throws Exception {
    if (getInstance() != null) {
      getInstance().loadResourcesInternal();
    }
  }

  /**
   * Adds the specified resource to the resource tree if it's located in any of the supported override or extra folders.
   */
  public static void registerResource(Path resource, boolean autoselect) {
    if (getInstance() != null) {
      getInstance().registerResourceInternal(resource, autoselect);
    }
  }

  /**
   * Removes the specified resource from the resource tree.
   */
  public static void unregisterResource(Path resource) {
    if (getInstance() != null) {
      getInstance().unregisterResourceInternal(resource);
    }
  }

  public static void saveCopyOfResource(ResourceEntry entry) {
    if (getInstance() != null) {
      getInstance().saveCopyOfResourceInternal(entry);
    }
  }

  public static TriState saveResource(Resource resource, Component parent) {
    return saveResource(resource, parent, false);
  }

  public static TriState saveResource(Resource resource, Component parent, boolean overwrite) {
    if (getInstance() != null) {
      return getInstance().saveResourceInternal(resource, parent, overwrite);
    } else {
      return TriState.FALSE;
    }
  }

  public static TriState saveResourceAs(Resource resource, Component parent) {
    return saveResourceAs(resource, parent, false);
  }

  public static TriState saveResourceAs(Resource resource, Component parent, boolean overwrite) {
    if (getInstance() != null) {
      return getInstance().saveResourceAsInternal(resource, parent, overwrite);
    } else {
      return TriState.FALSE;
    }
  }

  /**
   * If {@code output} is not {@code null}, shows confirmation dialog for saving resource. If user accepts saving then
   * resource will be saved if it implements {@link Writeable}
   *
   * @param resource Resource that must be saved
   * @param entry    Entry that represents resource. Must not be {@code null}
   * @param parent   Component that will be parent for dialog window. Must not be {@code null}
   *
   * @throws HeadlessException    if {@link GraphicsEnvironment#isHeadless} returns {@code true}
   * @throws NullPointerException If any argument is {@code null}
   * @throws AbortException       If save will be cancelled
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
   * If {@code output} is not {@code null}, shows confirmation dialog for saving resource. If user accepts saving then
   * resource will be saved if it implements {@link Writeable}
   *
   * @param resource Resource that must be saved
   * @param output   Path of the saved resource. If {@code null} method do nothing
   * @param parent   Component that will be parent for dialog window. Must not be {@code null}
   *
   * @throws HeadlessException    if {@link GraphicsEnvironment#isHeadless} returns {@code true}
   * @throws NullPointerException If {@code resource} or {@code parent} is {@code null}
   * @throws AbortException       If save will be cancelled
   */
  public static void closeResource(Resource resource, Path output, JComponent parent) throws Exception {
    if (output != null) {
      final String[] options = { "Save changes", "Discard changes", "Cancel" };
      final int result = JOptionPane.showOptionDialog(parent, "Save changes to " + output + '?', "Resource changed",
          JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
      if (result == JOptionPane.YES_OPTION) {
        if (saveResource(resource, parent.getTopLevelAncestor()) == TriState.UNDEFINED) {
          throw new AbortException("Save aborted");
        }
      } else if (result != JOptionPane.NO_OPTION) {
        throw new AbortException("Save aborted");
      }
    }
  }

  /**
   * Convenience method that asks for user confirmation to overwrite if the specified file exists and
   * {@code interactive} mode is enabled.
   *
   * <p>Dialog title is defined as "Confirmation". Options "Overwrite" and "Cancel" are presented.</p>
   *
   * @param file        The file {@link Path} to check.
   * @param interactive Specifies whether an interactive dialog should be presented if the {@code file} already exists.
   * @param parent      determines the {@code Frame} in which the dialog is displayed; if {@code null}, or if the
   *                      {@code parent} has no {@code Frame}, a default {@code Frame} is used.
   * @return {@code 0} if {@code file} does not yet exist, or the user accepted to overwrite it. Returns a positive
   *         non-zero value if the file already exists and/or the user decided to cancel the write operation. Returns -1
   *         if {@code file} is {@code null}.
   */
  public static int confirmOverwrite(Path file, boolean interactive, Component parent) {
    return confirmOverwrite(file, interactive, parent, null, null);
  }

  /**
   * Convenience method that asks for user confirmation to overwrite if the specified file exists and
   * {@code interactive} mode is enabled.
   *
   * <p>Options "Overwrite" and "Cancel" are presented.</p>
   *
   * @param file        The file {@link Path} to check.
   * @param interactive Specifies whether an interactive dialog should be presented if the {@code file} already exists.
   * @param parent      determines the {@code Frame} in which the dialog is displayed; if {@code null}, or if the
   *                      {@code parent} has no {@code Frame}, a default {@code Frame} is used.
   * @param title       Optional confirmation dialog title. (Default: "Confirmation")
   * @return {@code 0} if {@code file} does not yet exist, or the user accepted to overwrite it. Returns a positive
   *         non-zero value if the file already exists and/or the user decided to cancel the write operation. Returns -1
   *         if {@code file} is {@code null}.
   */
  public static int confirmOverwrite(Path file, boolean interactive, Component parent, String title) {
    return confirmOverwrite(file, interactive, parent, title, null);
  }

  /**
   * Convenience method that asks for user confirmation to overwrite if the specified file exists and
   * {@code interactive} mode is enabled.
   *
   * @param file        The file {@link Path} to check.
   * @param interactive Specifies whether an interactive dialog should be presented if the {@code file} already exists.
   * @param parent      determines the {@code Frame} in which the dialog is displayed; if {@code null}, or if the
   *                      {@code parent} has no {@code Frame}, a default {@code Frame} is used.
   * @param title       Optional confirmation dialog title. (Default: "Confirmation")
   * @param options     Optional array of available confirmation options. Array must contain 2 or 3 options. The first
   *                      option indicates that the write operation can continue as planned.
   * @return {@code 0} if {@code file} does not yet exist, or the user accepted to overwrite it. Returns a positive
   *         non-zero value if the file already exists and/or the user decided to cancel the write operation. Returns -1
   *         if {@code file} is {@code null}.
   */
  public static int confirmOverwrite(Path file, boolean interactive, Component parent, String title, String[] options) {
    if (file == null) {
      return -1;
    }

    int retVal = Files.exists(file) ? 1 : 0;
    if (retVal != 0 && interactive) {
      final String dlgTitle = (title != null) ? title : "Confirmation";
      final String[] dlgOptions = (options != null && options.length > 0) ? options : new String[] { "Overwrite", "Cancel" };
      final int msgType = (dlgOptions.length == 3) ? JOptionPane.YES_NO_CANCEL_OPTION : JOptionPane.YES_NO_OPTION;
      retVal = JOptionPane.showOptionDialog(parent, file + " exists. Overwrite?", dlgTitle, msgType,
          JOptionPane.WARNING_MESSAGE, null, dlgOptions, dlgOptions[0]);
    }

    return retVal;
  }

  /**
   * Returns a list of available game language directories for the current game in Enhanced Edition games. Returns an
   * empty list otherwise.
   */
  public static List<Path> getAvailableGameLanguages() {
    List<Path> list = new ArrayList<>();

    if (Profile.isEnhancedEdition()) {
      Path langPath = Profile.getProperty(Profile.Key.GET_GAME_LANG_FOLDER_BASE);
      if (langPath != null && FileEx.create(langPath).isDirectory()) {
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(langPath,
            (Path entry) -> (FileEx.create(entry).isDirectory()
                && entry.getFileName().toString().matches("[a-z]{2}_[A-Z]{2}")
                && FileEx.create(FileManager.query(entry, Profile.getProperty(Profile.Key.GET_GLOBAL_DIALOG_NAME)))
                    .isFile()))) {
          dstream.forEach(list::add);
        } catch (IOException e) {
          Logger.error(e);
        }
      }
    }
    return list;
  }

  /** Return the game language specified in the given baldur.ini if found. Returns {@code en_US} otherwise. */
  public static String autodetectGameLanguage(Path iniFile) {
    final String langDefault = "en_US"; // using default language, if no language entry found
    if (Profile.isEnhancedEdition() && iniFile != null && FileEx.create(iniFile).isFile()) {
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
                if (path != null && FileEx.create(path).isDirectory()) {
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
        Logger.error(e);
        JOptionPane.showMessageDialog(NearInfinity.getInstance(),
            "Error parsing " + iniFile.getFileName() + ". Using language defaults.", "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    }
    return langDefault;
  }

  /** Attempts to find the home folder of an Enhanced Edition game. */
  static Path getHomeRoot(boolean allowMissing) {
    if (Profile.hasProperty(Profile.Key.GET_GAME_HOME_FOLDER_NAME)) {
      final Path EE_DOC_ROOT = FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
      final String EE_DIR = Profile.getProperty(Profile.Key.GET_GAME_HOME_FOLDER_NAME);
      Path userPath = FileManager.query(EE_DOC_ROOT, EE_DIR);
      if (allowMissing || (userPath != null && FileEx.create(userPath).isDirectory())) {
        return userPath;
      } else {
        // fallback solution
        String userPrefix = System.getProperty("user.home");
        userPath = null;
        if (Platform.IS_WINDOWS) {
          try {
            Process p = Runtime.getRuntime().exec(
                "reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\" /v personal");
            p.waitFor();
            byte[] b;
            try (InputStream in = p.getInputStream()) {
              b = new byte[in.available()];
              in.read(b);
            }
            String[] splitted = new String(b).split("\\s\\s+");
            userPrefix = splitted[splitted.length - 1];
            userPath = FileManager.resolve(userPrefix, EE_DIR);
          } catch (Throwable t) {
            Logger.error(t);
            return null;
          }
        } else if (Platform.IS_MACOS) {
          userPath = FileManager.resolve(FileManager.resolve(userPrefix, "Documents", EE_DIR));
        } else if (Platform.IS_UNIX) {
          userPath = FileManager.resolve(FileManager.resolve(userPrefix, ".local", "share", EE_DIR));
        }
        if (userPath != null && FileEx.create(userPath).isDirectory()) {
          return userPath;
        }
      }
    }
    return null;
  }

  /** Attempts to find folders containing BIFF archives. */
  static List<Path> getBIFFDirs() {
    List<Path> dirList = new ArrayList<>();

    if (Profile.isEnhancedEdition()) {
      // adding supported base biff folders
      Path langRoot = Profile.getLanguageRoot();
      if (langRoot != null) {
        dirList.add(langRoot);
      }
      List<Path> dlcList = Profile.getProperty(Profile.Key.GET_GAME_DLC_FOLDERS_AVAILABLE);
      if (dlcList != null) {
        dirList.addAll(dlcList);
      }
      dirList.add(Profile.getGameRoot());
    } else {
      // fetching the CD folders in a game installation
      Path iniFile = Profile.getProperty(Profile.Key.GET_GAME_INI_FILE);
      List<Path> rootFolders = Profile.getRootFolders();
      if (iniFile != null && FileEx.create(iniFile).isFile()) {
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

                if (!Platform.IS_WINDOWS) {
                  // Needed to parse Windows paths on non-Windows systems
                  line = line.replace('\\', '/');
                }

                // Try to handle Unix paths
                Path path;
                if (line.charAt(0) == '/') { // absolute Unix path
                  path = FileManager.resolve(line);
                  if (path == null || !FileEx.create(path).isDirectory()) { // try relative Unix path
                    path = FileManager.query(rootFolders, line);
                  }
                } else if (line.indexOf(':') < 0) { // relative Unix path
                  path = FileManager.query(rootFolders, line);
                } else {
                  path = FileManager.resolve(line);
                }

                path = fixBiffPath(rootFolders, path);
                if (FileEx.create(path).isDirectory()) {
                  dirList.add(path);
                }
              }
            }
          }
        } catch (Exception e) {
          Logger.error(e);
          dirList.clear();
        }
      }

      if (dirList.isEmpty()) {
        // Don't panic if an .ini-file cannot be found or contains errors
        dirList.addAll(rootFolders);
        Path path;
        for (int i = 1; i < 7; i++) {
          path = FileManager.query(rootFolders, "CD" + i);
          if (FileEx.create(path).isDirectory()) {
            dirList.add(path);
          }
        }
        // used in certain games
        path = FileManager.query(rootFolders, "CDALL");
        if (FileEx.create(path).isDirectory()) {
          dirList.add(path);
        }
      }
    }
    return dirList;
  }

  /** Returns the currently used language of an Enhanced Edition game. */
  static String fetchGameLanguage(Path iniFile) {
    final String langDefault = "en_US"; // using default language, if no language entry found

    if (Profile.isEnhancedEdition() && iniFile != null && FileEx.create(iniFile).isFile()) {
      String lang = BrowserMenuBar.getInstance().getOptions().getSelectedGameLanguage();

      if (lang == null || lang.isEmpty()) {
        return autodetectGameLanguage(iniFile);
      } else {
        // Using user-defined language
        if (lang.matches("[A-Za-z]{2}_[A-Za-z]{2}")) {
          Path path = FileManager.query(Profile.getGameRoot(), "lang", lang);
          if (path != null && FileEx.create(path).isDirectory()) {
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
  static void openGame(Path keyFile) {
    closeGame();
    new ResourceFactory(keyFile);
  }

  /** Closes the current game configuration. */
  private static void closeGame() {
    if (instance != null) {
      instance.close();
      instance = null;
    }
  }

  /** Attempts to return an existing biff path based on the specified parameters. */
  private static Path fixBiffPath(List<Path> rootPaths, Path path) {
    if (path == null) {
      if (rootPaths != null) {
        for (final Path rootPath : rootPaths) {
          if (rootPath != null && FileEx.create(rootPath).isDirectory()) {
            return rootPath;
          }
        }
      }
      return null;
    }

    if (FileEx.create(path).isDirectory()) {
      return path;
    }

    if (rootPaths != null) {
      for (final Path rootPath : rootPaths) {
        if (rootPath != null) {
          final String gameFolder = rootPath.getFileName().toString();
          for (int i = path.getNameCount() - 1; i >= 0; i--) {
            final String curFolder = path.getName(i).toString();
            Path newPath = null;
            if (gameFolder.equalsIgnoreCase(curFolder)) {
              if (i == path.getNameCount() - 1) {
                newPath = rootPath;
              } else {
                newPath = rootPath.resolve(path.subpath(i + 1, path.getNameCount()));
              }
              if (newPath != null && FileEx.create(newPath).isDirectory()) {
                return newPath;
              }
            }
          }
        }
      }
    }

    return path;
  }

  private static ResourceFactory getInstance() {
    return instance;
  }

  private ResourceFactory(Path keyFile) {
    instance = this;
    try {
      // initializing primary key file
      this.keyfile = new Keyfile(keyFile);

      // adding DLC key files if available
      List<Path> keyList = Profile.getProperty(Profile.Key.GET_GAME_DLC_KEYS_AVAILABLE);
      if (keyList != null) {
        for (final Path key : keyList) {
          this.keyfile.addKeyfile(key);
        }
      }

      loadResourcesInternal();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, "No Infinity Engine game found", "Error", JOptionPane.ERROR_MESSAGE);
      Logger.error(e);
    }
  }

  /** Cleans up resources. */
  private void close() {
    // nothing to do yet...
  }

  private void exportResourceInternal(ResourceEntry entry, Component parent, Path output) throws Exception {
    try {
      ByteBuffer buffer = entry.getResourceBuffer();
      final String ext = entry.getExtension();
      if (ext.equalsIgnoreCase("IDS") || ext.equalsIgnoreCase("2DA") || ext.equalsIgnoreCase("BIO")
          || ext.equalsIgnoreCase("RES") || ext.equalsIgnoreCase("INI") || ext.equalsIgnoreCase("SET")
          || ext.equalsIgnoreCase("TXT") || (Profile.getEngine() == Profile.Engine.IWD2 && ext.equalsIgnoreCase("SRC"))
          || (Profile.isEnhancedEdition()
              && (ext.equalsIgnoreCase("GUI") || ext.equalsIgnoreCase("SQL") || ext.equalsIgnoreCase("GLSL")))) {
        if (buffer.getShort(0) == -1) {
          exportResourceInternal(entry, StaticSimpleXorDecryptor.decrypt(buffer, 2), getResourceName(entry), parent,
              output);
        } else {
          buffer.position(0);
          exportResourceInternal(entry, buffer, getResourceName(entry), parent, output);
        }
      } else {
        exportResourceInternal(entry, buffer, getResourceName(entry), parent, output);
      }
    } catch (Exception e) {
      throw new Exception("Can't read " + entry);
    }
  }

  private Path getExportFilePathInternal() {
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

  private Path getExportFileDialogInternal(Component parent, String fileName, boolean forceOverwrite) {
    Path path = null;
    if (fc == null) {
      fc = new JFileChooser(Profile.getGameRoot().toFile());
      fc.setDialogTitle("Export resource");
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }
    fc.setSelectedFile(new File(fc.getCurrentDirectory(), fileName));
    if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
      path = fc.getSelectedFile().toPath();
      if (!forceOverwrite && FileEx.create(path).exists()) {
        if (confirmOverwrite(path, true, parent, "Export resource") != 0) {
          path = null;
        }
      }
    }
    return path;
  }

  private void exportResourceInternal(ResourceEntry entry, ByteBuffer buffer, String fileName, Component parent,
      Path output) throws Exception {
    // ask for output file path if needed
    boolean interactive = (output == null);
    if (interactive) {
      output = getExportFileDialogInternal(parent, fileName, false);
    }

    // exporting resource
    if (output != null) {
      try {
        try (OutputStream os = StreamUtils.getOutputStream(output, true)) {
          StreamUtils.writeBytes(os, buffer);
        }
        if (interactive) {
          JOptionPane.showMessageDialog(parent, "File exported to " + output, "Export complete",
              JOptionPane.INFORMATION_MESSAGE);
        }
      } catch (IOException e) {
        throw new Exception("Error while exporting " + entry);
      }
    }
  }

  private void unregisterResourceInternal(Path resource) {
    if ((!BrowserMenuBar.getInstance().getOptions().showUnknownResourceTypes()
        && !Profile.isResourceTypeSupported(FileManager.getFileExtension(resource))) || (resource == null)) {
      return;
    }
    ResourceEntry selectedEntry = NearInfinity.getInstance().getResourceTree().getSelected();

    // 1. checking extra folders <- skipped because of issues on Windows systems
    // List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    // Path match = FileManager.getContainedPath(resource, extraPaths);
    // if (match != null) {
    // // finding correct subfolder
    // Path resPath = resource.getParent();
    // int startIdx = match.getNameCount() - 1; // include main folder
    // int endIdx = resPath.getNameCount();
    // Path subPath = resPath.subpath(startIdx, endIdx);
    //
    // ResourceTreeFolder folder = (ResourceTreeFolder)treeModel.getRoot();
    // for (int idx = 0, cnt = subPath.getNameCount(); idx < cnt && folder != null; idx++) {
    // String folderName = subPath.getName(idx).toString();
    // List<ResourceTreeFolder> folders = folder.getFolders();
    // folder = null;
    // for (final ResourceTreeFolder subFolder: folders) {
    // if (folderName.equalsIgnoreCase(subFolder.folderName())) {
    // folder = subFolder;
    // break;
    // }
    // }
    // }
    //
    // if (folder != null && folder.folderName().equalsIgnoreCase(resPath.getFileName().toString())) {
    // for (final ResourceEntry entry: folder.getResourceEntries()) {
    // if (entry.getResourceName().equalsIgnoreCase(resource.getFileName().toString())) {
    // folder.removeResourceEntry(entry);
    // if (folder.getChildCount() == 0) {
    // ResourceTreeFolder parentFolder = folder.getParentFolder();
    // parentFolder.removeFolder(folder);
    // treeModel.updateFolders(parentFolder);
    // } else {
    // treeModel.updateFolders(folder);
    // }
    // if (selectedEntry != null && !selectedEntry.equals(entry)) {
    // NearInfinity.getInstance().getResourceTree().select(selectedEntry, true);
    // }
    // return;
    // }
    // }
    // }
    // }

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

  private void registerResourceInternal(Path resource, boolean autoselect) {
    final BrowserMenuBar options = BrowserMenuBar.getInstance();
    if ((!options.getOptions().showUnknownResourceTypes()
        && !Profile.isResourceTypeSupported(FileManager.getFileExtension(resource))) || resource == null
        || !FileEx.create(resource).isFile()) {
      return;
    }

    // 1. checking if resource has already been added to resource tree
    ResourceEntry entry = treeModel.getResourceEntry(resource.getFileName().toString(), true);
    if (entry != null) {
      boolean match = false;
      if (entry instanceof BIFFResourceEntry) {
        final boolean overrideInOverride = (options.getOptions().getOverrideMode() == OverrideMode.InOverride);
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
    // List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    // Path match = FileManager.getContainedPath(resource, extraPaths);
    // if (match != null) {
    // // finding correct subfolder
    // int startIdx = match.getNameCount() - 1; // include main folder
    // int endIdx = resPath.getNameCount();
    // Path subPath = resPath.subpath(startIdx, endIdx);
    //
    // ResourceTreeFolder parentFolder = null;
    // ResourceTreeFolder folder = (ResourceTreeFolder)treeModel.getRoot();
    // for (int idx = 0, cnt = subPath.getNameCount(); idx < cnt && folder != null; idx++) {
    // String folderName = subPath.getName(idx).toString();
    // List<ResourceTreeFolder> folders = folder.getFolders();
    // parentFolder = folder;
    // folder = null;
    //
    // for (final ResourceTreeFolder subFolder: folders) {
    // if (folderName.equalsIgnoreCase(subFolder.folderName())) {
    // folder = subFolder;
    // break;
    // }
    // }
    //
    // if (folder == null) {
    // folder = treeModel.addFolder(parentFolder, folderName);
    // }
    // }
    //
    // if (folder != null && folder.folderName().equalsIgnoreCase(resPath.getFileName().toString())) {
    // ResourceEntry newEntry = new FileResourceEntry(resource, false);
    // folder.addResourceEntry(newEntry, true);
    // folder.sortChildren(false);
    // treeModel.updateFolders(folder);
    // if (autoselect) {
    // NearInfinity.getInstance().showResourceEntry(newEntry);
    // } else if (selectedEntry != null) {
    // NearInfinity.getInstance().getResourceTree().select(selectedEntry, true);
    // }
    // return;
    // }
    // }

    // 3. checking override folders
    if (FileManager.isSamePath(resPath, Profile.getOverrideFolders(true))) {
      entry = getResourceEntry(resource.getFileName().toString());
      String folderName;
      if (entry instanceof BIFFResourceEntry) {
        final boolean overrideInOverride = (options.getOptions().getOverrideMode() == OverrideMode.InOverride);
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

  private void loadResourcesInternal() throws Exception {
    treeModel = new ResourceTreeModel();

    // Get resources from keyfile
    NearInfinity.advanceProgress("Loading BIFF resources...");
    keyfile.populateResourceTree(treeModel);

    // Add resources from extra folders
    NearInfinity.advanceProgress("Loading extra resources...");
    List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    extraPaths.forEach(path -> {
      if (FileEx.create(path).isDirectory()) {
        treeModel.addDirectory(treeModel.getRoot(), path, false);
      }
    });

    NearInfinity.advanceProgress("Loading override resources...");
    final boolean overrideInOverride = (BrowserMenuBar.isInstantiated()
        && BrowserMenuBar.getInstance().getOptions().getOverrideMode() == OverrideMode.InOverride);
    String overrideFolder = Profile.getOverrideFolderName();
    List<Path> overridePaths = Profile.getOverrideFolders(false);
    for (final Path overridePath : overridePaths) {
      if (FileEx.create(overridePath).isDirectory()) {
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(overridePath)) {
          dstream.forEach(path -> {
            if (FileEx.create(path).isFile()) {
              ResourceEntry entry = getResourceEntry(path.getFileName().toString());
              if (entry instanceof FileResourceEntry) {
                treeModel.addResourceEntry(entry, entry.getTreeFolderName(), true);
              } else if (entry instanceof BIFFResourceEntry) {
                ((BIFFResourceEntry) entry).setOverride(true);
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
   * Registers in the resourse tree all special game resources that are not stored in the override folders or BIF
   * archives
   */
  private void loadSpecialResources() {
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
        addFileResource(FileManager.query(roots, QuestsResource.RESOURCE_NAME));
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
  private void addFileResource(Path path) {
    if (path != null && FileEx.create(path).isFile()) {
      treeModel.addResourceEntry(new FileResourceEntry(path), SPECIAL_CATEGORY, false);
    }
  }

  private List<ResourceEntry> getResourcesInternal(String type, List<Path> extraDirs) {
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
    if (BrowserMenuBar.isInstantiated()) {
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

  private List<ResourceEntry> getResourcesInternal(Pattern pattern, List<Path> extraDirs) {
    final ArrayList<ResourceEntry> retList = new ArrayList<>();

    String[] resTypes = Profile.getAvailableResourceTypes();
    for (final String type : resTypes) {
      fillResources(retList, type, pattern);
    }

    // include extra folders
    if (extraDirs == null) {
      extraDirs = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    }
    extraDirs.forEach(path -> fillResources(retList, path.getFileName().toString(), pattern));

    // include override folders
    if (BrowserMenuBar.isInstantiated()) {
      fillResources(retList, Profile.getOverrideFolderName(), pattern);
    }

    retList.sort(null);

    return retList;
  }

  /**
   * Adds to {@code retList} all resources from specified {@code folderName}, that match specified {@code pattern}. If
   * such folder not exists, do nothing.
   *
   * @param retList    List to be filled. Must not be {@code null}
   * @param folderName Folder from which all direct resources must be added
   * @param pattern    Pattern to which resource name must match to be added to the list. If {@code null}, then all
   *                   folder resources will be added
   */
  private void fillResources(List<ResourceEntry> retList, String folderName, Pattern pattern) {
    final ResourceTreeFolder folder = treeModel.getFolder(folderName);
    if (folder != null) {
      final List<ResourceEntry> entries = folder.getResourceEntries();
      if (pattern == null) {
        retList.addAll(entries);
      } else {
        for (final ResourceEntry entry : entries) {
          if (pattern.matcher(entry.getResourceName()).matches()) {
            retList.add(entry);
          }
        }
      }
    }
  }

  private void saveCopyOfResourceInternal(ResourceEntry entry) {
    String fileName;
    do {
      fileName = (String) JOptionPane.showInputDialog(NearInfinity.getInstance(), "Enter new filename",
          "Add copy of " + getResourceName(entry), JOptionPane.QUESTION_MESSAGE, null, null, getResourceName(entry));
      if (fileName != null) {
        if (!fileName.contains(".")) {
          fileName += '.' + entry.getExtension();
        }
        if (fileName.lastIndexOf('.') > 8) {
          JOptionPane.showMessageDialog(NearInfinity.getInstance(),
              "Filenames can only be up to 8 characters long (not including the file extension).", "Error",
              JOptionPane.ERROR_MESSAGE);
          fileName = null;
        }
      } else {
        return;
      }
    } while (fileName == null);

    final Path outPath = FileManager.query(Profile.getGameRoot(),
        Profile.getOverrideFolderName().toLowerCase(Locale.ENGLISH));
    Path outFile = outPath.resolve(fileName);
    if (entry.getExtension().equalsIgnoreCase("bs")) {
      outFile = FileManager.query(Profile.getGameRoot(), "Scripts", fileName);
    }

    if (FileEx.create(outFile).exists()) {
      if (confirmOverwrite(outFile, true, NearInfinity.getInstance(), "Confirm overwrite " + outFile) != 0) {
        return;
      }
    }

    // creating override folder in game directory if it doesn't exist
    if (!FileEx.create(outPath).isDirectory()) {
      try {
        Files.createDirectory(outPath);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Could not create " + outPath + ".", "Error",
            JOptionPane.ERROR_MESSAGE);
        Logger.error(e);
        return;
      }
    }

    try {
      ByteBuffer bb = entry.getResourceBuffer();
      try (OutputStream os = StreamUtils.getOutputStream(outFile, true)) {
        WritableByteChannel wbc = Channels.newChannel(os);
        wbc.write(bb);
      }
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), entry + " copied to " + outFile, "Copy complete",
          JOptionPane.INFORMATION_MESSAGE);
      ResourceEntry newEntry = new FileResourceEntry(outFile, !entry.getExtension().equalsIgnoreCase("bs"));
      treeModel.addResourceEntry(newEntry, newEntry.getTreeFolderName(), true);
      treeModel.sort();
      if (BrowserMenuBar.getInstance().getOptions().getKeepViewOnCopy()) {
        NearInfinity.getInstance().showResourceEntry(entry);
      } else {
        NearInfinity.getInstance().showResourceEntry(newEntry);
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error while copying " + entry, "Error",
          JOptionPane.ERROR_MESSAGE);
      Logger.error(e);
    }
  }

  private TriState saveResourceAsInternal(Resource resource, Component parent, boolean overwrite) {
    final Path outFile = getExportFileDialogInternal(parent, getResourceName(resource.getResourceEntry()), true);
    if (outFile != null) {
      return saveResourceInternal(resource, parent, outFile, overwrite);
    } else {
      return TriState.FALSE;
    }
  }

  private TriState saveResourceInternal(Resource resource, Component parent, boolean overwrite) {
    return saveResourceInternal(resource, parent, null, overwrite);
  }

  private TriState saveResourceInternal(Resource resource, Component parent, Path outFile, boolean overwrite) {
    if (!(resource instanceof Writeable)) {
      JOptionPane.showMessageDialog(parent, "Resource not savable", "Error", JOptionPane.ERROR_MESSAGE);
      return TriState.FALSE;
    }
    final ResourceEntry entry = resource.getResourceEntry();
    if (entry == null) {
      return TriState.FALSE;
    }

    Path outPath;
    if (outFile != null) {
      outPath = outFile;
    } else {
      if (entry instanceof BIFFResourceEntry) {
        Path overridePath = FileManager.query(Profile.getGameRoot(), Profile.getOverrideFolderName());
        if (!FileEx.create(overridePath).isDirectory()) {
          try {
            Files.createDirectory(overridePath);
          } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Unable to create override folder.", "Error",
                JOptionPane.ERROR_MESSAGE);
            Logger.error(e);
            return TriState.FALSE;
          }
        }
        outPath = FileManager.query(overridePath, getResourceName(entry));
        ((BIFFResourceEntry) entry).setOverride(true);
      } else {
        outPath = entry.getActualPath();
        // extra step for saving resources from a read-only medium (such as DLCs)
        if (!FileManager.isDefaultFileSystem(outPath)) {
          outPath = Profile.getGameRoot().resolve(outPath.subpath(0, outPath.getNameCount()).toString());
          if (!FileEx.create(outPath.getParent()).exists()) {
            try {
              Files.createDirectories(outPath.getParent());
            } catch (IOException e) {
              JOptionPane.showMessageDialog(parent, "Unable to create folder: " + outPath.getParent(), "Error",
                  JOptionPane.ERROR_MESSAGE);
              Logger.error(e);
              return TriState.FALSE;
            }
          }
        }
      }
    }

    if (FileEx.create(outPath).exists()) {
      outPath = outPath.toAbsolutePath();
      final int result;
      if (overwrite) {
        result = JOptionPane.YES_OPTION;
      } else {
        result = confirmOverwrite(outPath, true, parent, "Save resource", new String[]{ "Overwrite", "Discard", "Cancel" });
      }
      switch (result) {
        case JOptionPane.YES_OPTION:  // Overwrite
          if (BrowserMenuBar.getInstance().getOptions().backupOnSave()) {
            try {
              Path bakPath = outPath.getParent().resolve(outPath.getFileName() + ".bak");
              if (FileEx.create(bakPath).isFile()) {
                Files.delete(bakPath);
              }
              if (!FileEx.create(bakPath).exists()) {
                Files.move(outPath, bakPath);
              }
            } catch (IOException e) {
              Logger.error(e);
            }
          }
          break;
        case JOptionPane.NO_OPTION: // Discard
          return TriState.FALSE;
        default:  // Cancel
          return TriState.UNDEFINED;
      }
    }

    try (OutputStream os = StreamUtils.getOutputStream(outPath, true)) {
      ((Writeable) resource).write(os);
    } catch (IOException e) {
      JOptionPane.showMessageDialog(parent, "Error while saving " + entry, "Error", JOptionPane.ERROR_MESSAGE);
      Logger.error(e);
      return TriState.FALSE;
    }

    JOptionPane.showMessageDialog(parent, "File saved to \"" + outPath.toAbsolutePath() + '\"', "Save complete",
        JOptionPane.INFORMATION_MESSAGE);

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
    return TriState.TRUE;
  }
}
