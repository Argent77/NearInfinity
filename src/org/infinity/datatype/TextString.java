// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.util.Misc;
import org.infinity.util.io.StreamUtils;

public final class TextString extends Datatype implements InlineEditable, IsTextual
{
  private final Charset charset;
  private final ByteBuffer buffer;
  private String text;

  public TextString(ByteBuffer buffer, int offset, int length, String name)
  {
    super(offset, length, name);
    this.buffer = StreamUtils.getByteBuffer(length);
    this.charset = Charset.forName(BrowserMenuBar.getInstance().getSelectedCharset());
    read(buffer, offset);
  }

// --------------------- Begin Interface InlineEditable ---------------------

  @Override
  public boolean update(Object value)
  {
    String newstring = (String)value;
    if (newstring.length() > getSize())
      return false;
    text = newstring;
    return true;
  }

// --------------------- End Interface InlineEditable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    if (text != null) {
      byte[] buf = text.getBytes(Misc.CHARSET_DEFAULT);
      buffer.position(0);
      buffer.put(buf, 0, Math.min(buf.length, buffer.limit()));
      while (buffer.remaining() > 0) {
        buffer.put((byte)0);
      }
    }
    buffer.position(0);
    StreamUtils.writeBytes(os, buffer);
  }

// --------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    StreamUtils.copyBytes(buffer, offset, this.buffer, 0, getSize());
    text = null;

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

//--------------------- Begin Interface IsTextual ---------------------

  @Override
  public String getText()
  {
    if (text == null) {
      buffer.position(0);
      text = StreamUtils.readString(buffer, buffer.limit(), charset);
    }
    return text;
  }

//--------------------- End Interface IsTextual ---------------------

  @Override
  public String toString()
  {
    return getText();
  }
}
