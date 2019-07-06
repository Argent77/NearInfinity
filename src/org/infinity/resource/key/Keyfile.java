// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import org.infinity.icon.Icons;
import org.infinity.resource.ResourceFactory;
import org.infinity.util.IntegerHashMap;
import org.infinity.util.Misc;
import org.infinity.util.io.StreamUtils;

public class Keyfile
{
  public static final ImageIcon ICON_STRUCT = Icons.getIcon(Icons.ICON_ROW_INSERT_AFTER_16);

  public static final int TYPE_BMP    = 0x001;
  public static final int TYPE_MVE    = 0x002;
  public static final int TYPE_WAV    = 0x004;
  public static final int TYPE_WFX    = 0x005;
  public static final int TYPE_PLT    = 0x006;
  public static final int TYPE_TGA    = 0x3b8;  // TODO: confirm!
  public static final int TYPE_BAM    = 0x3e8;
  public static final int TYPE_WED    = 0x3e9;
  public static final int TYPE_CHU    = 0x3ea;
  public static final int TYPE_TIS    = 0x3eb;
  public static final int TYPE_MOS    = 0x3ec;
  public static final int TYPE_ITM    = 0x3ed;
  public static final int TYPE_SPL    = 0x3ee;
  public static final int TYPE_BCS    = 0x3ef;
  public static final int TYPE_IDS    = 0x3f0;
  public static final int TYPE_CRE    = 0x3f1;
  public static final int TYPE_ARE    = 0x3f2;
  public static final int TYPE_DLG    = 0x3f3;
  public static final int TYPE_2DA    = 0x3f4;
  public static final int TYPE_GAM    = 0x3f5;
  public static final int TYPE_STO    = 0x3f6;
  public static final int TYPE_WMP    = 0x3f7;
  public static final int TYPE_EFF    = 0x3f8;
  public static final int TYPE_BS     = 0x3f9;
  public static final int TYPE_CHR    = 0x3fa;
  public static final int TYPE_VVC    = 0x3fb;
  public static final int TYPE_VEF    = 0x3fc;
  public static final int TYPE_PRO    = 0x3fd;
  public static final int TYPE_BIO    = 0x3fe;
  public static final int TYPE_WBM    = 0x3ff;
  public static final int TYPE_FNT    = 0x400;
  public static final int TYPE_GUI    = 0x402;
  public static final int TYPE_SQL    = 0x403;
  public static final int TYPE_PVRZ   = 0x404;
  public static final int TYPE_GLSL   = 0x405;
  public static final int TYPE_TOT    = 0x406;
  public static final int TYPE_TOH    = 0x407;
  public static final int TYPE_MENU   = 0x408;
  public static final int TYPE_LUA    = 0x409;
  public static final int TYPE_TTF    = 0x40a;
  public static final int TYPE_PNG    = 0x40b;
  public static final int TYPE_BAH    = 0x44c;
  public static final int TYPE_INI    = 0x802;
  public static final int TYPE_SRC    = 0x803;
  public static final int TYPE_MAZE   = 0x804;
  public static final int TYPE_MUS    = 0xffe;  // not in bif?
  public static final int TYPE_ACM    = 0xfff;  // not in bif?

  private static final ImageIcon ICON_TEXT = Icons.getIcon(Icons.ICON_EDIT_16);
  private static final ImageIcon ICON_UNKNOWN = Icons.getIcon(Icons.ICON_HELP_16);
  private static final ImageIcon ICON_SOUND = Icons.getIcon(Icons.ICON_VOLUME_16);
  private static final ImageIcon ICON_MOVIE = Icons.getIcon(Icons.ICON_MOVIE_16);
  private static final ImageIcon ICON_SCRIPT = Icons.getIcon(Icons.ICON_HISTORY_16);
  private static final ImageIcon ICON_IMAGE = Icons.getIcon(Icons.ICON_COLOR_16);
  private static final ImageIcon ICON_BUNDLE = Icons.getIcon(Icons.ICON_BUNDLE_16);

  private static final String KEY_SIGNATURE = "KEY ";
  private static final String KEY_VERSION   = "V1  ";

  /** Path to primary key file (usually {@code chitin.key}). */
  private final Path keyFile;
  /** List of additional DLC key files. */
  private final List<Path> keyList;

  private final IntegerHashMap<String> extMap = new IntegerHashMap<>();
  private final Map<String, ImageIcon> resourceIcons = new HashMap<>();

  /** Map of key file path => list of associated key files. */
  private final Map<Path, List<BIFFEntry>> biffEntries = new HashMap<>();

  /** Sorted map of effective BIFFResourceEntry objects. */
  private final TreeMap<String, BIFFResourceEntry> resourceEntries = new TreeMap<>(Misc.getIgnoreCaseComparator());


  public Keyfile(Path keyFile) throws FileNotFoundException
  {
    if (keyFile == null) {
      throw new NullPointerException("No keyfile specified");
    }
    if (!Files.isRegularFile(keyFile)) {
      throw new FileNotFoundException("Keyfile " + keyFile + " not found or is not regular file");
    }

    this.keyFile = keyFile;
    this.keyList = new ArrayList<>();

    // REMEMBER: Always use upper case letters for extensions strings
    resourceIcons.clear();
    resourceIcons.put("???", ICON_UNKNOWN);
    extMap.put(TYPE_BMP, "BMP");
    resourceIcons.put("BMP", ICON_IMAGE);
    extMap.put(TYPE_MVE, "MVE");
    resourceIcons.put("MVE", ICON_MOVIE);
    extMap.put(TYPE_WAV, "WAV");
    resourceIcons.put("WAV", ICON_SOUND);
    extMap.put(TYPE_WFX, "WFX");
    resourceIcons.put("WFX", ICON_STRUCT);
    extMap.put(TYPE_PLT, "PLT");
    resourceIcons.put("PLT", ICON_IMAGE);
    extMap.put(TYPE_BAM, "BAM");
    resourceIcons.put("BAM", ICON_MOVIE);
    extMap.put(TYPE_WED, "WED");
    resourceIcons.put("WED", ICON_STRUCT);
    extMap.put(TYPE_CHU, "CHU");
    resourceIcons.put("CHU", ICON_STRUCT);
    extMap.put(TYPE_TIS, "TIS");
    resourceIcons.put("TIS", ICON_IMAGE);
    extMap.put(TYPE_MOS, "MOS");
    resourceIcons.put("MOS", ICON_IMAGE);
    extMap.put(TYPE_ITM, "ITM");
    resourceIcons.put("ITM", ICON_STRUCT);
    extMap.put(TYPE_SPL, "SPL");
    resourceIcons.put("SPL", ICON_STRUCT);
    extMap.put(TYPE_BCS, "BCS");
    resourceIcons.put("BCS", ICON_SCRIPT);
    extMap.put(TYPE_IDS, "IDS");
    resourceIcons.put("IDS", ICON_TEXT);
    extMap.put(TYPE_CRE, "CRE");
    resourceIcons.put("CRE", ICON_STRUCT);
    extMap.put(TYPE_ARE, "ARE");
    resourceIcons.put("ARE", ICON_STRUCT);
    extMap.put(TYPE_DLG, "DLG");
    resourceIcons.put("DLG", ICON_STRUCT);
    extMap.put(TYPE_2DA, "2DA");
    resourceIcons.put("2DA", ICON_TEXT);
    extMap.put(TYPE_GAM, "GAM");
    resourceIcons.put("GAM", ICON_STRUCT);
    extMap.put(TYPE_STO, "STO");
    resourceIcons.put("STO", ICON_STRUCT);
    extMap.put(TYPE_WMP, "WMP");
    resourceIcons.put("WMP", ICON_STRUCT);
    extMap.put(TYPE_EFF, "EFF");
    resourceIcons.put("EFF", ICON_STRUCT);
    extMap.put(TYPE_BS, "BS");
    resourceIcons.put("BS", ICON_SCRIPT);
    extMap.put(TYPE_CHR, "CHR");
    resourceIcons.put("CHR", ICON_STRUCT);
    extMap.put(TYPE_VVC, "VVC");
    resourceIcons.put("VVC", ICON_STRUCT);
    extMap.put(TYPE_VEF, "VEF");
    resourceIcons.put("VEF", ICON_STRUCT);
    extMap.put(TYPE_PRO, "PRO");
    resourceIcons.put("PRO", ICON_STRUCT);
    extMap.put(TYPE_BIO, "BIO");
    resourceIcons.put("BIO", ICON_TEXT);
    extMap.put(TYPE_WBM, "WBM");
    resourceIcons.put("WBM", ICON_MOVIE);
    extMap.put(TYPE_BAH, "BAH"); // ???????
    extMap.put(TYPE_INI, "INI");
    resourceIcons.put("INI", ICON_TEXT);
    extMap.put(TYPE_SRC, "SRC");
    resourceIcons.put("SRC", ICON_STRUCT);
    extMap.put(TYPE_FNT, "FNT");
    resourceIcons.put("FNT", ICON_IMAGE);
    extMap.put(TYPE_GUI, "GUI");
    resourceIcons.put("GUI", ICON_TEXT);
    extMap.put(TYPE_SQL, "SQL");
    resourceIcons.put("SQL", ICON_TEXT);
    extMap.put(TYPE_PVRZ, "PVRZ");
    resourceIcons.put("PVRZ", ICON_IMAGE);
    extMap.put(TYPE_GLSL, "GLSL");
    resourceIcons.put("GLSL", ICON_TEXT);
    extMap.put(TYPE_TOT, "TOT");
    resourceIcons.put("TOT", ICON_STRUCT);
    extMap.put(TYPE_TOH, "TOH");
    resourceIcons.put("TOH", ICON_STRUCT);
    extMap.put(TYPE_MENU, "MENU");
    resourceIcons.put("MENU", ICON_SCRIPT);
    extMap.put(TYPE_LUA, "LUA");
    resourceIcons.put("LUA", ICON_SCRIPT);
    extMap.put(TYPE_TTF, "TTF");
    resourceIcons.put("TTF", ICON_IMAGE);
    extMap.put(TYPE_PNG, "PNG");
    resourceIcons.put("PNG", ICON_IMAGE);
    extMap.put(TYPE_MUS, "MUS");
    resourceIcons.put("MUS", ICON_SOUND);
    extMap.put(TYPE_MAZE, "MAZE");
    resourceIcons.put("MAZE", ICON_STRUCT);
    extMap.put(TYPE_ACM, "ACM");
    resourceIcons.put("ACM", ICON_SOUND);
    resourceIcons.put("SAV", ICON_BUNDLE);
    resourceIcons.put("TXT", ICON_TEXT);
    resourceIcons.put("RES", ICON_TEXT);
    resourceIcons.put("BAF", ICON_SCRIPT);
    resourceIcons.put("VAR", ICON_STRUCT);// PST VAR.VAR file - from Special category in the resource tree
    resourceIcons.put("LOG", ICON_TEXT);// WeiDU log files - from Special category in the resource tree
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this) {
      return true;
    }
    if (o instanceof Keyfile) {
      Keyfile other = (Keyfile)o;
      return (keyFile.equals(other.keyFile));
    }
    return false;
  }

  @Override
  public String toString()
  {
    return keyFile.toString();
  }

  /** Returns the file path to the primary key file. */
  public Path getKeyfile()
  {
    return keyFile;
  }

  /** Returns all available DLC key files as unmodifiable list. */
  public List<Path> getDlcKeyfiles()
  {
    return Collections.unmodifiableList(keyList);
  }

  /**
   * Overrides current key file mapping with data from the specified key file.
   * @param keyFile The key file containing new entries.
   */
  public void addKeyfile(Path keyFile) throws IOException
  {
    if (keyFile == null) {
      throw new NullPointerException("No DLC keyfile specified");
    }
    if (!keyList.contains(keyFile)) {
      keyList.add(keyFile);
    }
  }

  public void populateResourceTree(ResourceTreeModel treeModel) throws Exception
  {
    if (treeModel != null) {
      init();

      resourceEntries.values().forEach((entry) -> treeModel.addResourceEntry(entry, entry.getExtension(), true));

      cacheBIFFs();
    }
  }

  /**
   * Returns the resource extension string of specified type.
   *
   * @param type One of the {@code TYPE_} constants
   * @return Extension (in upper case) for that type or null, if type is unknown
   */
  public String getExtension(int type)
  {
    return extMap.get(type);
  }

  /**
   * Attempts to determine the resource type of the specified extension.
   *
   * @param extension Extension string, that can be in any case
   */
  public int getExtensionType(String extension)
  {
    if (extension != null) {
      for (final Map.Entry<Integer, String> e : extMap.entrySet()) {
        if (e.getValue().equalsIgnoreCase(extension)) {
          return e.getKey().intValue();
        }
      }
    }
    return -1;
  }

  public ImageIcon getIcon(String extension)
  {
    ImageIcon icon = resourceIcons.get(extension);
    if (icon == null) {
      icon = resourceIcons.get("???");
    }
    return icon;
  }

  public void closeBIFFFiles()
  {
    AbstractBIFFReader.resetCache();
  }

  public void addBIFFEntry(BIFFEntry entry)
  {
    if (entry != null) {
      List<BIFFEntry> biffList = getBIFFList(getKeyfile(), false);
      if(biffList != null) {
        biffList.add(entry);
        entry.setIndex(biffList.size() - 1);
      }
    }
  }

//  public boolean cleanUp()
//  {
//    try {
//      closeBIFFFiles();
//      List<BIFFEntry> biffList = biffEntries.get(getKeyfile());
//      Set<BIFFEntry> toRemove = new HashSet<BIFFEntry>(biffList);
//      // Determine BIFFs with no files in them
//      List<BIFFResourceEntry> resourceEntries = loadResourceEntries(getKeyfile());
//      resourceEntries.forEach((entry) -> toRemove.remove(entry.getBIFFEntry()));
//
//      // Delete these BIFFs
//      toRemove.forEach((entry) -> {
//        Path file = entry.getPath();
//        System.out.println("Deleting " + file);
//        if (file != null) {
//          try {
//            Files.deleteIfExists(file);
//          } catch (IOException e) {
//            e.printStackTrace();
//          }
//        }
//      });
//
//      // Determine non-existant BIFFs
//      biffList.forEach((entry) -> {
//        if (entry.getPath() == null) {
//          toRemove.add(entry);
//        }
//      });
//      if (toRemove.isEmpty()) {
//        return false;
//      }
//
//      // Remove bogus BIFFs from keyfile
//      toRemove.forEach((entry) -> removeBIFFEntry(getKeyfile(), entry));
//
//      return true;
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//
//    return false;
//  }

  public BIFFEntry[] getBIFFEntriesSorted()
  {
    List<BIFFEntry> biffList = new ArrayList<>();
    for (final List<BIFFEntry> list: biffEntries.values()) {
      biffList.addAll(list);
    }
    Collections.sort(biffList);
    return biffList.toArray(new BIFFEntry[biffList.size()]);
  }

  public BIFFEntry getBIFFEntry(Path keyFile, int index)
  {
    List<BIFFEntry> biffs = getBIFFList(keyFile, false);
    if (biffs != null) {
      return biffs.get(index);
    }
    return null;
  }

  public AbstractBIFFReader getBIFFFile(BIFFEntry entry) throws Exception
  {
    if (entry == null) {
      return null;
    } else if (entry.getPath() == null) {
      throw new IOException(entry + " not found");
    } else {
      return AbstractBIFFReader.open(entry.getPath());
    }
  }

  public BIFFResourceEntry getResourceEntry(String resourceName)
  {
    BIFFResourceEntry retVal = null;
    if (resourceName != null) {
      retVal = resourceEntries.get(resourceName);
    }
    return retVal;
  }

  public void write() throws IOException
  {
    List<BIFFEntry> biffs = getBIFFList(getKeyfile(), false);
    if (biffs == null) {
      throw new IOException("Error loading BIFF entry table");
    }
    try (OutputStream os = StreamUtils.getOutputStream(getKeyfile())) {
      int bifoff = 0x18;
      int offset = bifoff + 0x0c*biffs.size();
      for (final BIFFEntry biff: biffs) {
        offset += biff.updateOffset(offset);
      }
      int resoff = offset;

      List<BIFFResourceEntry> entries = ResourceFactory.getResourceTreeModel().getBIFFResourceEntries(getKeyfile());

      StreamUtils.writeString(os, KEY_SIGNATURE, 4);
      StreamUtils.writeString(os, KEY_VERSION, 4);
      StreamUtils.writeInt(os, biffs.size());
      StreamUtils.writeInt(os, entries.size());
      StreamUtils.writeInt(os, bifoff);
      StreamUtils.writeInt(os, resoff);

      for (final BIFFEntry biff: biffs) {
        biff.write(os);
      }

      for (final BIFFEntry biff: biffs) {
        biff.writeString(os);
      }

      for (final BIFFResourceEntry entry: entries) {
        entry.write(os);
      }
    }
  }

  /** Caches all BIFF files referenced in the current KEY file. */
  private void cacheBIFFs()
  {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run()
      {
        biffEntries.values().forEach((biffList) -> {
          biffList.forEach((entry) -> {
            if (entry != null) {
              Path biffPath = entry.getPath();
              if (biffPath != null && Files.isRegularFile(biffPath)) {
                try {
                  AbstractBIFFReader.open(biffPath);
                } catch (Exception e) {
                  e.printStackTrace();
                }
              }
            }
          });
        });
      }
    });
  }

  // Creates a list of ResourceEntry objects from the specified key file
//  private List<BIFFResourceEntry> loadResourceEntries(Path keyFile) throws IOException
//  {
//    if (keyFile == null) {
//      throw new NullPointerException();
//    }
//    if (!Files.isRegularFile(keyFile)) {
//      throw new IOException("Key file not found: " + keyFile);
//    }
//
//    try (SeekableByteChannel ch = Files.newByteChannel(keyFile, StandardOpenOption.READ)) {
//      ByteBuffer buffer = StreamUtils.getByteBuffer((int)ch.size());
//      if (ch.read(buffer) < ch.size()) {
//        throw new IOException("Error loading key file");
//      }
//
//      String sig = StreamUtils.readString(buffer, 0, 4);
//      String ver = StreamUtils.readString(buffer, 4, 4);
//      if (!sig.equals(KEY_SIGNATURE) || !ver.equals(KEY_VERSION)) {
//        throw new IOException("Unsupported key file: " + keyFile.toString());
//      }
//
//      int numRes = buffer.getInt(0x0c);
//      int ofsRes = buffer.getInt(0x14);
//
//      List<BIFFResourceEntry> retVal = new ArrayList<>(numRes);
//      for (int i = 0; i < numRes; i++) {
//        retVal.add(new BIFFResourceEntry(keyFile, buffer, ofsRes + i*14, 8));
//      }
//      return retVal;
//    }
//  }


  /** Creates or updates cached biff maps and entry tables. */
  private void init() throws IOException
  {
    if (getKeyfile() == null) {
      throw new NullPointerException();
    }
    if (!Files.isRegularFile(getKeyfile())) {
      throw new IOException("Key file not found: " + getKeyfile());
    }
    for (final Path file: keyList) {
      if (file != null && !Files.isRegularFile(file)) {
        throw new IOException("Key file not found: " + file);
      }
    }

    closeBIFFFiles();
    resourceEntries.clear();
    biffEntries.clear();

    List<Path> keyFiles = new ArrayList<>(1 + keyList.size());
    keyFiles.add(getKeyfile());
    keyFiles.addAll(keyList);

    for (final Path file: keyFiles) {
      try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ)) {
        ByteBuffer buffer = StreamUtils.getByteBuffer((int)ch.size());
        if (ch.read(buffer) < ch.size()) {
          throw new IOException("Error loading key file");
        }

        String sig = StreamUtils.readString(buffer, 0, 4);
        String ver = StreamUtils.readString(buffer, 4, 4);
        if (!sig.equals(KEY_SIGNATURE) || !ver.equals(KEY_VERSION)) {
          throw new IOException("Unsupported key file: " + file.toString());
        }

        int numBif = buffer.getInt(0x08);
        int numRes = buffer.getInt(0x0c);
        int ofsBif = buffer.getInt(0x10);
        int ofsRes = buffer.getInt(0x14);

        List<BIFFEntry> biffList = getBIFFList(file, true);
        if (biffList == null) {
          biffList = new ArrayList<>(numBif);
        } else {
          // discard old entries
          biffList.clear();
        }

        // processing BIFF entries
        for (int i = 0, ofs = ofsBif; i < numBif; i++, ofs += 12) {
          biffList.add(new BIFFEntry(file, i, buffer, ofs));
        }
        biffEntries.put(file, biffList);

        // processing resource entries
        for (int i = 0, ofs = ofsRes; i < numRes; i++, ofs += 14) {
          addResourceEntry(new BIFFResourceEntry(file, buffer, ofs));
        }
      }
    }
  }

  /** Returns the list of BIFFEntry objects for the specified key file, optionally removes it. */
  private List<BIFFEntry> getBIFFList(Path keyFile, boolean remove)
  {
    if (keyFile != null) {
      if (remove) {
        return biffEntries.remove(keyFile);
      } else {
        return biffEntries.get(keyFile);
      }
    }
    return null;
  }

  /** Adds the specified resource entry to the list, overwrites existing entries of same name. */
  private BIFFResourceEntry addResourceEntry(BIFFResourceEntry entry)
  {
    BIFFResourceEntry retVal = null;
    if (entry != null) {
      String key = entry.toString();
      retVal = resourceEntries.put(key, entry);
    }
    return retVal;
  }

  // Removes the specified BIFF entry and associated resource entries from cache and resource tree
//  private void removeBIFFEntry(Path keyFile, BIFFEntry entry)
//  {
//    System.out.println("Removing " + entry);
//    List<BIFFEntry> biffList = biffEntries.get(keyFile);
//    int index = biffList.indexOf(entry);
//
//    // Remove bogus BIFFResourceEntries
//    ResourceTreeModel resources = ResourceFactory.getResources();
//    resources.getBIFFResourceEntries(keyFile).forEach((resourceEntry) -> {
//      if (resourceEntry.getBIFFEntry() == entry) {
//        resources.removeResourceEntry(resourceEntry);
//        resourceEntries.remove(resourceEntry);
//      } else {
//        resourceEntry.adjustSourceIndex(index);     // Update relevant BIFFResourceEntries
//      }
//    });
//
//    // Remove BIFFEntry
//    biffList.remove(entry);
//
//    // Update relevant BIFFEntries
//    for (int i = index; i < biffList.size(); i++) {
//      BIFFEntry e = biffList.get(i);
//      e.setIndex(i);
//    }
//  }
}
