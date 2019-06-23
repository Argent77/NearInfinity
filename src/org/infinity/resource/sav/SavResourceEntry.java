// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.sav;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

import org.infinity.resource.Writeable;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.key.ResourceTreeFolder;
import org.infinity.util.io.ByteBufferInputStream;
import org.infinity.util.io.ByteBufferOutputStream;
import org.infinity.util.io.StreamUtils;

/**
 * Specialized ResourceEntry class for compressed entries in SAV resources.
 */
public class SavResourceEntry extends ResourceEntry implements Writeable
{
  private final String fileName;
  private int offset;
  private int comprLength;
  private int uncomprLength;
  private ByteBuffer cdata;

  public SavResourceEntry(ByteBuffer buffer, int offset)
  {
    this.offset = offset;
    int fileNameLength = buffer.getInt(offset);
    fileName = StreamUtils.readString(buffer, offset + 4, fileNameLength - 1);
    offset += 4 + fileNameLength;
    uncomprLength = buffer.getInt(offset);
    comprLength = buffer.getInt(offset + 4);
    cdata = StreamUtils.getByteBuffer(comprLength);
    StreamUtils.copyBytes(buffer, offset + 8, cdata, 0, comprLength);
  }

  public SavResourceEntry(ResourceEntry entry) throws Exception
  {
    comprLength = 0;
    uncomprLength = 0;
    fileName = entry.toString();
    byte[] udata = StreamUtils.toArray(entry.getResourceBuffer(true));
    cdata = StreamUtils.getByteBuffer(udata.length * 2 + 8);
    try (DeflaterOutputStream dos = new DeflaterOutputStream(new ByteBufferOutputStream(cdata),
                                                             new Deflater(Deflater.BEST_COMPRESSION))) {
      dos.write(udata);
      dos.finish();
    }
    cdata.flip();
    uncomprLength = udata.length;
    comprLength = cdata.limit();
  }

  public int getEndOffset()
  {
    return offset + fileName.length() + 13 + cdata.limit();
  }

  @Override
  public String toString()
  {
    return fileName;
  }

  @Override
  public String getResourceName()
  {
    return fileName;
  }

  @Override
  public String getExtension()
  {
    return fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ENGLISH);
  }

  @Override
  public String getTreeFolderName()
  {
    return null;
  }

  @Override
  public ResourceTreeFolder getTreeFolder()
  {
    return null;
  }

  @Override
  public boolean hasOverride()
  {
    return false;
  }

  @Override
  public int[] getResourceInfo(boolean ignoreoverride) throws Exception
  {
    if (fileName.toUpperCase(Locale.ENGLISH).endsWith(".TIS")) {
      try {
        ByteBuffer data = decompress();
        if (!StreamUtils.readString(data, 0, 4).equalsIgnoreCase("TIS ")) {
          int tileCount= data.getInt(0);
          int tileSize = data.getInt(4);
          return new int[]{tileCount, tileSize};
        } else {
          return new int[]{data.getInt(8), data.getInt(12)};
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }
    return new int[]{uncomprLength};
  }

  @Override
  public ByteBuffer getResourceBuffer(boolean ignoreoverride) throws Exception
  {
    return decompress();
  }

  @Override
  public InputStream getResourceDataAsStream(boolean ignoreoverride) throws Exception
  {
    return new ByteBufferInputStream(decompress());
  }

  @Override
  public Path getActualPath(boolean ignoreoverride)
  {
    return null;
  }

  @Override
  public long getResourceSize(boolean ignoreOverride)
  {
    try {
      int[] info = getResourceInfo();
      if (info != null) {
        if (info.length == 1) {
          return info[0];
        } else if (info.length > 1) {
          return info[0]*info[1] + 0x18;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return -1L;
  }

  public ByteBuffer decompress() throws Exception
  {
    Inflater inflater = new Inflater();
    byte udata[] = new byte[uncomprLength];
    inflater.setInput(cdata.array());
    inflater.inflate(udata);
    return StreamUtils.getByteBuffer(udata);
  }

  @Override
  public void write(OutputStream os) throws IOException
  {
    StreamUtils.writeInt(os, fileName.length() + 1);
    StreamUtils.writeString(os, fileName, fileName.length());
    StreamUtils.writeByte(os, (byte)0);
    StreamUtils.writeInt(os, uncomprLength);
    StreamUtils.writeInt(os, comprLength);
    cdata.position(0);
    StreamUtils.writeBytes(os, cdata);
  }
}
