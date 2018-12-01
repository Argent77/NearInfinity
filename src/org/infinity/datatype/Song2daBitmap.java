// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.Table2da;
import org.infinity.util.Table2daCache;
import org.infinity.util.io.FileManager;

/** Specialized ResourceBitmap type for parsing ARE song reference entries. */
public class Song2daBitmap extends ResourceBitmap
{
  /** Cached content of the song entries. */
  private static final ArrayList<RefEntry> SongList = new ArrayList<>();

  /** Source of music resource references. */
  private static String TableName;
  /** Textual representation of the song reference field data. */
  private static String FormatString = FMT_REF_NAME_VALUE;

  public Song2daBitmap(ByteBuffer buffer, int offset, int length)
  {
    this(buffer, offset, length, "Song");
  }

  public Song2daBitmap(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name, createSongList(), "Unknown", FormatString);
  }

  public static synchronized List<RefEntry> createSongList()
  {
    if (SongList.isEmpty()) {
      // search "music" subfolder as well
      List<Path> searchDirs = new ArrayList<>();
      searchDirs.add(FileManager.query(Profile.getGameRoot(), "Music"));

      if (ResourceFactory.resourceExists("SONGLIST.2DA")) {
        TableName = "SONGLIST.2DA";
        FormatString = FMT_REF_NAME_VALUE;
        createSongList_SONGLIST(searchDirs);
      } else if (ResourceFactory.resourceExists("MUSICLIS.IDS")) {
        TableName = "MUSICLIS.IDS";
        FormatString = FMT_REF_HYPHEN_VALUE;
        createSongList_MUSIC(searchDirs);
      } else if (ResourceFactory.resourceExists("MUSIC.IDS")) {
        TableName = "MUSIC.IDS";
        FormatString = FMT_REF_HYPHEN_VALUE;
        createSongList_MUSIC(searchDirs);
      } else if (ResourceFactory.resourceExists("SONGS.IDS")) {
        TableName = "SONGS.IDS";
        FormatString = FMT_REF_NAME_VALUE;
        createSongList_PST(searchDirs);
      } else {
        TableName = "";
        FormatString = FMT_REF_HYPHEN_VALUE;
        createSongList_BG1(searchDirs);
      }
    }
    return SongList;
  }

  /** Create song references for BG2 and Enhanced Edition games. */
  private static List<RefEntry> createSongList_SONGLIST(List<Path> searchDirs)
  {
    if (SongList.isEmpty()) {
      Table2da table = Table2daCache.get(TableName);
      String defValue = table.getDefaultValue();
      SongList.ensureCapacity(2 + table.getRowCount());
      SongList.add(new RefEntry(0xfffffffeL, "Special", "Continue area music"));
      SongList.add(new RefEntry(0xffffffffL, "Special", "Continue outside music"));

      for (int i = 0, size = table.getRowCount(); i < size; i++) {
        String name = table.get(i, 1);
        String ref = table.get(i, 2);
        if (defValue.equalsIgnoreCase(ref)) {
          ref = "None";
        }
        SongList.add(new RefEntry(i, ref, name, searchDirs));
      }
    }
    return SongList;
  }

  /** Create song references for IWD and IWD2. */
  private static List<RefEntry> createSongList_MUSIC(List<Path> searchDirs)
  {
    if (SongList.isEmpty()) {
      IdsMap map = IdsMapCache.get(TableName);
      SongList.ensureCapacity(2 + map.size());
      SongList.add(new RefEntry(0xfffffffeL, "Continue area music"));
      SongList.add(new RefEntry(0xffffffffL, "Continue outside music"));

      for (final IdsMapEntry e: map.getAllValues()) {
        String name = e.getSymbol();
        long key = e.getID();
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String ref = name.replaceAll("_", "").toUpperCase(Locale.ENGLISH) + ".MUS";
        if (key == 0L) {
          ref = name;
        }
        SongList.add(new RefEntry(key, ref, null, searchDirs));
      }
    }
    return SongList;
  }

  /** Create song references for PST. */
  private static List<RefEntry> createSongList_PST(List<Path> searchDirs)
  {
    if (SongList.isEmpty()) {
      // PST used static associations of SONGS.IDS with MUS resources (source: songlist.txt in game's music folder)
      final String[] map = {
        // 0-9
        null, "No music",
        "MAIN.MUS", "Planescape Main Theme",
        "CHAR_01.MUS", "Char: Nameless One",
        "CHAR_02.MUS", "Char: Transcendent One",
        "CHAR_03.MUS", "Char: The Shadows",
        "CHAR_04.MUS", "Char: Deionarra",
        "CHAR_05.MUS", "Char: Ravel",
        "CHAR_06.MUS", "Char: Fhjull Forked-Tongue",
        "CHAR_07.MUS", "Char: Trias",
        "CHAR_09.MUS", "Char: Annah",
        // 10-19
        "CHAR_10.MUS", "Char: Dak'kon",
        "CHAR_11.MUS", "Char: Morte",
        "CHAR_12.MUS", "Char: Nordom",
        "CHAR_13.MUS", "Char: Fall-from-Grace",
        "CHAR_14.MUS", "Char: Vhailor",
        "CHAR_15.MUS", "Char: Ignus",
        "MORT.MUS", "Area: Mortuary",
        "SIG.MUS", "Area: Sigil",
        "SMOL.MUS", "Area: Smoldering Corpse Bar",
        "BONE.MUS", "Area: Bones of the Night",
        // 20-29
        "CIVIC.MUS", "Area: Civic Festhall",
        "RAVE.MUS", "Area: Ravel's Maze",
        "MODR.MUS", "Area: Modron Maze",
        "CUR.MUS", "Area: Curst",
        "BAAT.MUS", "Area: Baator",
        "FORT.MUS", "Area: Fortress of Regrets",
        "CHAR13b.MUS", "Area: Fall-from-Grace",
        "BT1.MUS", "Battle: Sigil",
        "BT2.MUS", "Battle: Catacombs",
        "BT3.MUS", "Battle: Curst",
        // 30-36
        "BT4.MUS", "Battle: Modron Cube",
        "BT5.MUS", "Battle: Fortress of Regrets",
        "BT5.MUS", "Battle: Fortress of Regrets - Final)",
        "END_01G.MUS", "Ending: Good",
        "END_01B.MUS", "Ending: Bad",
        "END_02.MUS", "Ending: Indifferent",
        "SMO2.MUS", "Smoldering Corpse Alternate",
      };

      SongList.ensureCapacity(2 + map.length);
      SongList.add(new RefEntry(0xfffffffeL, "Special", "Continue area music"));
      SongList.add(new RefEntry(0xffffffffL, "Special", "Continue outside music"));

      for (int i = 0; i < map.length; i+=2) {
        final String ref  = map[i];
        final String name = map[i+1];
        if (ref != null) {
          SongList.add(new RefEntry(i/2, ref, name, searchDirs));
        } else {
          SongList.add(new RefEntry(i/2, "None", name));
        }
      }
    }
    return SongList;
  }

  /** Create song references for BG1. */
  private static List<RefEntry> createSongList_BG1(List<Path> searchDirs)
  {
    if (SongList.isEmpty()) {
    // BG1 used static list of songs (source: GemRB's unhardcoded music.2da)
      final String[] map = {
        /*0L, */"None",
        /*1L, */"CDAY1.MUS",
        /*2L, */"CDAY2.MUS",
        /*3L, */"CNITE.MUS",
        /*4L, */"CHANTS.MUS",
        /*5L, */"DREAM.MUS",
        /*6L, */"FESTI.MUS",
        /*7L, */"FDAY.MUS",
        /*8L, */"FNITE.MUS",
        /*9L, */"PDAY.MUS",
        /*10L,*/ "PNITE.MUS",
        /*11L,*/ "TEMPLE.MUS",
        /*12L,*/ "THEME.MUS",
        /*13L,*/ "TDAY1.MUS",
        /*14L,*/ "TDAY2.MUS",
        /*15L,*/ "TNITE.MUS",
        /*16L,*/ "DUNG1.MUS",
        /*17L,*/ "DUNG2.MUS",
        /*18L,*/ "DUNG3.MUS",
        /*19L,*/ "BC1.MUS",
        /*20L,*/ "BC2.MUS",
        /*21L,*/ "BD1.MUS",
        /*22L,*/ "BD2.MUS",
        /*23L,*/ "BL1.MUS",
        /*24L,*/ "BL2.MUS",
        /*25L,*/ "BF1.MUS",
        /*26L,*/ "BF2.MUS",
        /*27L,*/ "BP1.MUS",
        /*28L,*/ "BP2.MUS",
        /*29L,*/ "BW1.MUS",
        /*30L,*/ "BRIDGE.MUS",
        /*31L,*/ "FORT.MUS",
        /*32L,*/ "CHAPTER.MUS",
        /*33L,*/ "MPO900.MUS",
        /*34L,*/ "TAV1.MUS",
        /*35L,*/ "TAV2.MUS",
        /*36L,*/ "TAV3.MUS",
        /*37L,*/ "TAV4.MUS",
        /*38L,*/ "DEATH.MUS",
      };

      SongList.add(new RefEntry(0xfffffffeL, "Continue area music"));
      SongList.add(new RefEntry(0xffffffffL, "Continue outside music"));

      for (int i = 0; i < map.length; ++i) {
        SongList.add(new RefEntry(i, map[i], null, searchDirs));
      }
    }
    return SongList;
  }

  public static String getTableName()
  {
    return TableName;
  }

  public static synchronized void resetSonglist()
  {
    Table2daCache.cacheInvalid(ResourceFactory.getResourceEntry(TableName));
    SongList.clear();
    TableName = "";
    FormatString = FMT_REF_NAME_VALUE;
  }
}
