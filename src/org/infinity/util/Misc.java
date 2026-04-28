// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.UIManager;

import org.infinity.AppOption;
import org.infinity.NearInfinity;
import org.infinity.resource.Profile;

/**
 * A general-purpose class containing useful function not fitting elsewhere.
 */
public class Misc {
  /** The default ANSI charset (Windows-1252). */
  public static final Charset CHARSET_DEFAULT = Charset.forName("windows-1252");

  /** The UTF-8 charset. */
  public static final Charset CHARSET_UTF8 = StandardCharsets.UTF_8;

  /** The US-ASCII charset. */
  public static final Charset CHARSET_ASCII = StandardCharsets.US_ASCII;

  /** Returns the line separator string which is used by the current operating system. */
  public static final String LINE_SEPARATOR = System.lineSeparator();

  /** Can be used to slightly expand dialog message strings to force a bigger initial dialog width. */
  public static final String MSG_EXPAND_SMALL = "        \t";

  /** Can be used to expand dialog message strings to force a bigger initial dialog width. */
  public static final String MSG_EXPAND_MEDIUM = "                \t";

  /** Can be used to greatly expand dialog message strings to force a bigger initial dialog width. */
  public static final String MSG_EXPAND_LARGE = "                                \t";

  /**
   * Returns a comparator that compares the string representation of the specified objects in a case-insensitive way.
   */
  public static <T> Comparator<T> getIgnoreCaseComparator() {
    return new Comparator<T>() {
      @Override
      public int compare(T o1, T o2) {
        return (o1.toString().compareToIgnoreCase(o2.toString()));
      }

      @Override
      public int hashCode() {
        return toString().toLowerCase().hashCode();
      }

      @Override
      public boolean equals(Object obj) {
        return toString().equalsIgnoreCase(obj.toString());
      }
    };
  }

  /**
   * Returns the absolute path name of the node corresponding to the package of the specified class name.
   *
   * @param className Fully qualified class or package name in a format as provided by {@link Class#getName()}.
   * @return A node name string that can be used as path name for a {@link Preferences} instance.
   *
   * @throws IllegalArgumentException if the specified argument is not a valid class or package name.
   *
   * @implNote This is a variation of the private method {@code nodeName(Class<?> c)} in the {@link Preferences} class.
   * It should be checked and updated if needed when this project upgrades the minimum JDK requirements.
   */
  public static String prefsNodeName(String className) {
    if (className.indexOf('/') >= 0) {
      throw new IllegalArgumentException("Invalid class name specified.");
    }
    int pkgEndIndex = className.lastIndexOf('.');
    if (pkgEndIndex < 0)
      return "/<unnamed>";
    String packageName = className.substring(0, pkgEndIndex);
    return "/" + packageName.replace('.', '/');
  }

  /**
   * Returns a user {@link Preferences} instance pointing to the node that corresponds to the given class.
   *
   * @param classType the class for whose package a user preference node is desired.
   * @return {@code Preferences} instance.
   */
  public static Preferences getPrefs(Class<?> classType) {
    return getPrefs(classType.getName());
  }

  /**
   * Returns a user {@link Preferences} instance pointing to the node that corresponds to the given class path.
   *
   * @param className Class path as string.
   * @return {@code Preferences} instance.
   */
  public static Preferences getPrefs(String className) {
    return Preferences.userRoot().node(Misc.prefsNodeName(className));
  }

  /**
   * A convenience method that attempts to return the charset specified by the given name or the next best match
   * depending on the current game type.
   *
   * @param charsetName Name of the desired charset as {@code String}.
   * @return The desired charset if successful, a game-specific default charset otherwise.
   */
  public static Charset getCharsetFrom(String charsetName) {
    return getCharsetFrom(charsetName, Profile.getDefaultCharset());
  }

  /**
   * A convenience method that attempts to return the charset specified by the given name.
   *
   * @param charsetName Name of the desired charset as {@code String}.
   * @param defaultCharset Fallback solution if the desired charset doesn't exist.
   * @return the desired charset if successful, {@code defaultCharset} otherwise.
   */
  public static Charset getCharsetFrom(String charsetName, Charset defaultCharset) {
    try {
      return Charset.forName(charsetName);
    } catch (Exception e) {
      return defaultCharset;
    }
  }

  /**
   * Attempts to detect the character set of the text data in the specified byte buffer.
   *
   * @param data Text data as byte array.
   * @return The detected character set or the ANSI charset "windows-1252" if autodetection was not successful.
   */
  public static Charset detectCharset(byte[] data) {
    return detectCharset(data, CHARSET_DEFAULT);
  }

  /**
   * Attempts to detect the character set of the text data in the specified byte buffer.
   *
   * @param data           Text data as byte array.
   * @param defaultCharset The default charset to return if autodetection is not successful. (Default: windows-1252)
   * @return The detected character set or {@code defaultCharset} if autodetection was not successful.
   */
  public static Charset detectCharset(byte[] data, Charset defaultCharset) {
    if (defaultCharset == null) {
      defaultCharset = CHARSET_DEFAULT;
    }

    Charset retVal = null;
    if (data != null) {
      if (data.length >= 3 && data[0] == -17 && data[1] == -69 && data[2] == -65) { // UTF-8 BOM (0xef, 0xbb, 0xbf)
        retVal = StandardCharsets.UTF_8;
      } else if (data.length >= 2 && data[0] == -2 && data[1] == -1) { // UTF-16 BOM (0xfeff) in big-endian order
        retVal = StandardCharsets.UTF_16BE;
      } else if (data.length >= 2 && data[0] == -1 && data[1] == -2) { // UTF-16 BOM (0xfeff) in little-endian order
        retVal = StandardCharsets.UTF_16LE;
      }
    }

    if (retVal == null) {
      retVal = defaultCharset;
    }

    return retVal;
  }

  /**
   * Attempts to convert the specified string into a numeric {@code int} value. Returns defValue if value does not
   * contain a valid number.
   */
  public static int toNumber(String value, int defValue) {
    return toNumber(value, 10, defValue);
  }

  /**
   * Attempts to convert the specified string of given base "radix" into a numeric {@code int} value. Returns defValue
   * if value does not contain a valid number.
   */
  public static int toNumber(String value, int radix, int defValue) {
    if (value != null) {
      try {
        return Integer.parseInt(value, radix);
      } catch (NumberFormatException e) {
        Logger.trace(e);
      }
    }
    return defValue;
  }

  /**
   * Attempts to convert the specified string into a numeric {@code long} value. Returns defValue if value does not
   * contain a valid number.
   */
  public static long toLong(String value, long defValue) {
    return toLong(value, 10, defValue);
  }

  /**
   * Attempts to convert the specified string of given base "radix" into a numeric {@code long} value. Returns defValue
   * if value does not contain a valid number.
   */
  public static long toLong(String value, int radix, long defValue) {
    if (value != null) {
      try {
        return Long.parseLong(value, radix);
      } catch (NumberFormatException e) {
        Logger.trace(e);
      }
    }
    return defValue;
  }

  /**
   * Attempts to convert the specified string into a {@code double} value. Returns {@code defValue} if {@code value}
   * does not contain a valid number.
   */
  public static float toFloat(String value, float defValue) {
    if (value != null) {
      try {
        return Float.parseFloat(value);
      } catch (NumberFormatException e) {
        Logger.trace(e);
      }
    }
    return defValue;
  }

  /**
   * Attempts to convert the specified string into a {@code double} value. Returns {@code defValue} if {@code value}
   * does not contain a valid number.
   */
  public static double toDouble(String value, double defValue) {
    if (value != null) {
      try {
        return Double.parseDouble(value);
      } catch (NumberFormatException e) {
        Logger.trace(e);
      }
    }
    return defValue;
  }

  /**
   * Attempts to convert the specified string into a {@code boolean} value. Returns {@code defValue} if {@code value}
   * does not contain content that can be evaluated to a boolean.
   * <p>
   * Supported values are: the strings {@code "true"}/{@code "false"} and numeric values where {@code 0} indicates false.
   * </p>
   */
  public static boolean toBoolean(String value, boolean defValue) {
    if (value != null) {
      if (value.equalsIgnoreCase("true") || toNumber(value, 0) != 0) {
        return true;
      } else if (value.equalsIgnoreCase("false") || toNumber(value, 1) == 0) {
        return false;
      }
    }
    return defValue;
  }

  /** Clamps {@code int} value {@code v} between {@code min} and {@code max}. */
  public static int clamp(int v, int min, int max) {
    return (v < min) ? min : (v > max) ? max : v;
  }

  /** Clamps {@code long} value {@code v} between {@code min} and {@code max}. */
  public static long clamp(long v, long min, long max) {
    return (v < min) ? min : (v > max) ? max : v;
  }

  /** Clamps {@code float} value {@code v} between {@code min} and {@code max}. */
  public static float clamp(float v, float min, float max) {
    return (v < min) ? min : (v > max) ? max : v;
  }

  /** Clamps {@code double} value {@code v} between {@code min} and {@code max}. */
  public static double clamp(double v, double min, double max) {
    return (v < min) ? min : (v > max) ? max : v;
  }

  /** Swaps byte order of the specified short value. */
  public static short swap16(short v) {
    return (short) (((v & 0xff) << 8) | ((v >> 8) & 0xff));
  }

  /** Swaps byte order of the specified int value. */
  public static int swap32(int v) {
    return ((v << 24) & 0xff000000) | ((v << 8) & 0x00ff0000) | ((v >> 8) & 0x0000ff00) | ((v >> 24) & 0x000000ff);
  }

  /** Swaps byte order of the specified long value. */
  public static long swap64(long v) {
    return ((v << 56) & 0xff00000000000000L) | ((v << 40) & 0x00ff000000000000L) | ((v << 24) & 0x0000ff0000000000L)
        | ((v << 8) & 0x000000ff00000000L) | ((v >> 8) & 0x00000000ff000000L) | ((v >> 24) & 0x0000000000ff0000L)
        | ((v >> 40) & 0x000000000000ff00L) | ((v >> 56) & 0x00000000000000ffL);
  }

  /** Swaps byte order of every short value in the specified array. */
  public static void swap(short[] array) {
    if (array != null) {
      for (int i = 0, cnt = array.length; i < cnt; i++) {
        array[i] = swap16(array[i]);
      }
    }
  }

  /** Swaps byte order of every int value in the specified array. */
  public static void swap(int[] array) {
    if (array != null) {
      for (int i = 0, cnt = array.length; i < cnt; i++) {
        array[i] = swap32(array[i]);
      }
    }
  }

  /** Swaps byte order of every long value in the specified array. */
  public static void swap(long[] array) {
    if (array != null) {
      for (int i = 0, cnt = array.length; i < cnt; i++) {
        array[i] = swap64(array[i]);
      }
    }
  }

  /** Converts a short value into a byte array (little-endian). */
  public static byte[] shortToArray(short value) {
    return new byte[] { (byte) (value & 0xff), (byte) ((value >> 8) & 0xff) };
  }

  /** Converts an int value into a byte array (little-endian). */
  public static byte[] intToArray(int value) {
    return new byte[] { (byte) (value & 0xff), (byte) ((value >> 8) & 0xff), (byte) ((value >> 16) & 0xff),
        (byte) ((value >> 24) & 0xff) };
  }

  /** Converts a long value into a byte array (little-endian). */
  public static byte[] longToArray(long value) {
    return new byte[] { (byte) (value & 0xffL), (byte) ((value >> 8) & 0xffL), (byte) ((value >> 16) & 0xffL),
        (byte) ((value >> 24) & 0xffL), (byte) ((value >> 32) & 0xffL), (byte) ((value >> 40) & 0xffL),
        (byte) ((value >> 48) & 0xffL), (byte) ((value >> 56) & 0xffL) };
  }

  /**
   * Sign-extends the specified {@code int} value consisting of the specified number of significant bits.
   *
   * @param value The {@code int} value to sign-extend.
   * @param bits  Size of {@code value} in bits.
   * @return A sign-extended version of {@code value}.
   */
  public static int signExtend(int value, int bits) {
    return (value << (32 - bits)) >> (32 - bits);
  }

  /**
   * Sign-extends the specified {@code long} value consisting of the specified number of significant bits.
   *
   * @param value The {@code long} value to sign-extend.
   * @param bits  Size of {@code value} in bits.
   * @return A sign-extended version of {@code value}.
   */
  public static long signExtend(long value, int bits) {
    return (value << (64 - bits)) >> (64 - bits);
  }

  /**
   * Returns a prototype dimension object based on the height of {@code c} and the width of (@code prototype}.
   *
   * @param c         The component to derive height and properties for calculating width.
   * @param prototype The prototype string used to derive width.
   * @return The {@link Dimension} object with calculated width. Height is used unchanged from the given component.
   */
  public static Dimension getPrototypeSize(JComponent c, String prototype) {
    Dimension d = null;
    if (c != null) {
      d = new Dimension();
      d.height = c.getPreferredSize().height;
      d.width = c.getFontMetrics(c.getFont()).stringWidth(prototype);
    }
    return d;
  }

  /**
   * Returns width and height of the specified text if displayed in the given component.
   *
   * @param c             The component where the text should be displayed.
   * @param text          The text {@code String}. If string is empty then a single space is used for the calculation.
   * @param includeInsets Whether to include border's insets of the component into the calculation.
   * @return {@link Dimension} with the calculated width and height of the text. Returns an empty dimension if component
   *         is {@code null}.
   */
  public static Dimension getTextDimension(JComponent c, String text, boolean includeInsets) {
    Dimension dim = null;

    if (c != null) {
      if (text == null || text.isEmpty()) {
        text = " ";
      }
      final Font font = c.getFont();
      final FontRenderContext frc = new FontRenderContext(null, true, true);
      Rectangle2D bounds = font.getStringBounds(text, frc);
      dim = new Dimension((int)Math.ceil(bounds.getWidth()), (int)Math.ceil(bounds.getHeight()));

      if (includeInsets) {
        final Insets insets = c.getInsets();
        dim.width += insets.left + insets.right;
        dim.height += insets.top + insets.bottom;
      }
    }

    if (dim == null) {
      dim = new Dimension();
    }

    return dim;
  }

  /**
   * Returns height of a font for the graphics context g.
   *
   * @param g    The graphics context. Specify {@code null} to use graphics context of main window.
   * @param font The font to use. Specify {@code null} to use current font of the specified graphics context.
   * @return Font height in pixels.
   */
  public static int getFontHeight(Graphics g, Font font) {
    if (g == null) {
      g = NearInfinity.getInstance().getGraphics();
    }
    if (g != null) {
      FontMetrics m = g.getFontMetrics((font != null) ? font : g.getFont());
      if (m != null) {
        return m.getHeight();
      }
    }
    return 0;
  }

  /**
   * Returns the specified font scaled to the global font scale value.
   *
   * @param font The font to scale.
   * @return The scaled font.
   */
  public static Font getScaledFont(Font font) {
    int scale = (NearInfinity.getInstance() != null) ? AppOption.GLOBAL_FONT_SIZE.getIntValue() : 100;
    return getScaledFont(font, scale);
  }

  /**
   * Returns the specified font scaled to the specified scale value.
   *
   * @param font  The font to scale.
   * @param scale The scale factor (in percent).
   * @return The scaled font.
   */
  public static Font getScaledFont(Font font, int scale) {
    Font ret = null;
    if (font != null) {
      ret = (scale != 100) ? font.deriveFont(font.getSize2D() * scale / 100.0f) : font;
    }
    return ret;
  }

  /**
   * Returns the specified Dimension structure scaled to the global font scale value.
   *
   * @param dim The Dimension structure to scale.
   * @return The scaled Dimension structure.
   */
  public static Dimension getScaledDimension(Dimension dim) {
    Dimension ret = null;
    if (dim != null) {
      int scale = 100;
      if (NearInfinity.getInstance() != null) {
        scale = AppOption.GLOBAL_FONT_SIZE.getIntValue();

      }
      ret = (scale != 100) ? new Dimension(dim.width * scale / 100, dim.height * scale / 100) : dim;
    }
    return ret;
  }

  /**
   * Returns the specified numeric value scaled to the global font scale value.
   *
   * @param value The numeric value to scale.
   * @return The scaled value.
   */
  public static float getScaledValue(float value) {
    float scale = (NearInfinity.getInstance() != null) ? AppOption.GLOBAL_FONT_SIZE.getIntValue() : 100.0f;
    return value * scale / 100.0f;
  }

  /**
   * Returns the specified numeric value scaled to the global font scale value.
   *
   * @param value The numeric value to scale.
   * @return The scaled value.
   */
  public static int getScaledValue(int value) {
    int scale = (NearInfinity.getInstance() != null) ? AppOption.GLOBAL_FONT_SIZE.getIntValue() : 100;
    return value * scale / 100;
  }

  /**
   * Returns the display size of the default sceen.
   *
   * @return {@link Dimension} containing the display width and height, in pixels.
   */
  public static Dimension getDisplaySize() {
    final DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
    return new Dimension(mode.getWidth(), mode.getHeight());
  }

  /**
   * Returns the display size of the screen that displays the specified UI component.
   *
   * @param c The {@link Component} object. Can be {@code null}.
   * @return {@link Dimension} containing the display width and height of the screen that displays the given component.
   *         Falls back to the screen of the main application window or default screen if unavailable.
   */
  public static Dimension getDisplaySize(Component c) {
    Dimension retVal = null;

    if (c != null) {
      GraphicsConfiguration gc = c.getGraphicsConfiguration();
      if (gc == null && NearInfinity.getInstance() != null) {
        gc = NearInfinity.getInstance().getGraphicsConfiguration();
      }
      if (gc != null) {
        final DisplayMode mode = gc.getDevice().getDisplayMode();
        retVal = new Dimension(mode.getWidth(), mode.getHeight());
      }
    }

    if (retVal == null) {
      retVal = getDisplaySize();
    }

    return retVal;
  }

  /**
   * Returns the L&F UI theme color of the specified {@code key} and falls back to {@code defColor} if the
   * requested color doesn't exist.
   */
  public static Color getDefaultColor(String key, Color defColor) {
    Color retVal = UIManager.getDefaults().getColor(key);
    if (retVal == null) {
      retVal = defColor;
    }
    return retVal;
  }

  /**
   * Attempts to format the specified symbolic name, so that it becomes easier to read. E.g. by replaceing underscores
   * by spaces, or using an appropriate mix of upper/lower case characters.
   *
   * @param symbol The symbolic name to convert.
   * @return A prettified version of the symbolic name.
   */
  public static String prettifySymbol(String symbol) {
    if (symbol != null) {
      StringBuilder sb = new StringBuilder();
      boolean isUpper = false;
      boolean isDigit = false;
      boolean isPrevUpper;
      boolean isPrevDigit;
      boolean toUpper = true;
      for (int idx = 0, len = symbol.length(); idx < len; idx++) {
        char ch = symbol.charAt(idx);
        if (" ,-_".indexOf(ch) >= 0) {
          // improve spacing
          switch (ch) {
            case '_':
              sb.append(' ');
              break;
            case '-':
              sb.append(" - ");
              break;
            default:
              sb.append(ch);
          }
          toUpper = true;
        } else {
          if (toUpper) {
            ch = Character.toUpperCase(ch);
            toUpper = false;
          }
          isPrevUpper = isUpper;
          isPrevDigit = isDigit;
          isUpper = Character.isUpperCase(ch);
          isDigit = Character.isDigit(ch);
          if (idx > 0) {
            // detect word boundaries
            char chPrev = sb.charAt(sb.length() - 1);
            if (chPrev != ' ') {
              if (isUpper && !isPrevUpper && !isPrevDigit) {
                sb.append(' ');
              } else if (isDigit && !isPrevDigit) {
                sb.append(' ');
              }
            }

            chPrev = sb.charAt(sb.length() - 1);
            if (isUpper && chPrev != ' ') {
              // prevent upper case characters in the middle of words
              ch = Character.toLowerCase(ch);
            }

            if (!isUpper && chPrev == ' ') {
              // new words start with upper case character
              ch = Character.toUpperCase(ch);
            }
          }
          sb.append(ch);
        }
      }
      symbol = sb.toString();
    }
    return symbol;
  }

  /**
   * This method removes all leading occurences of whitespace from the specified string.
   *
   * @param s The string to trim.
   * @return The trimmed string. Returns {@code null} if no valid string is specified.
   */
  public static String trimStart(String s) {
    return trimStart(s, null);
  }

  /**
   * This method removes all leading occurences of whitespace or specified characters from the specified string.
   *
   * @param s         The string to trim.
   * @param trimChars Array of characters to trim in addition to whitespace.
   * @return The trimmed string. Returns {@code null} if no valid string is specified.
   */
  public static String trimStart(String s, char[] trimChars) {
    if (s != null && !s.isEmpty()) {
      int start = 0;
      int len = s.length();
      String trimS = (trimChars != null) ? new String(trimChars) : "";
      while (start < len) {
        char ch = s.charAt(start);
        if (ch > ' ' && trimS.indexOf(ch) == -1) {
          break;
        }
        start++;
      }
      return (start > 0) ? s.substring(start) : s;
    }
    return s;
  }

  /**
   * This method removes all trailing occurences of whitespace from the specified string.
   *
   * @param s The string to trim.
   * @return The trimmed string. Returns {@code null} if no valid string is specified.
   */
  public static String trimEnd(String s) {
    return trimEnd(s, null);
  }

  /**
   * This method removes all trailing occurences of whitespace or specified characters from the specified string.
   *
   * @param s         The string to trim.
   * @param trimChars Array of characters to trim in addition to whitespace.
   * @return The trimmed string. Returns {@code null} if no valid string is specified.
   */
  public static String trimEnd(String s, char[] trimChars) {
    if (s != null && !s.isEmpty()) {
      int len = s.length();
      String trimS = (trimChars != null) ? new String(trimChars) : "";
      while (len > 0) {
        char ch = s.charAt(len - 1);
        if (ch > ' ' && trimS.indexOf(ch) == -1) {
          break;
        }
        len--;
      }
      return (len < s.length()) ? s.substring(0, len) : s;
    }
    return s;
  }

  /**
   * This method removes all occurences of whitespace or specified characters from the start or end of the specified
   * string.
   *
   * @param s         The string to trim.
   * @param trimChars Array of characters to trim in addition to whitespace.
   * @return The trimmed string. Returns {@code null} if no valid string is specified.
   */
  public static String trim(String s, char[] trimChars) {
    if (s != null && !s.isEmpty()) {
      int start = 0;
      int len = s.length();
      String trimS = (trimChars != null) ? new String(trimChars) : "";
      char ch;
      while (start < len) {
        ch = s.charAt(start);
        if (ch > ' ' && trimS.indexOf(ch) == -1) {
          break;
        }
        start++;
      }
      while (len > start) {
        ch = s.charAt(len - 1);
        if (ch > ' ' && trimS.indexOf(ch) == -1) {
          break;
        }
        len--;
      }
      return (start > 0 || len < s.length()) ? s.substring(start, len) : s;
    }
    return s;
  }

  /**
   * Generates a string with {@code count} instances of {@code ch}.
   *
   * @param count Number of character instances in the resulting string.
   * @param ch    The character to be duplicated.
   * @return {@code String} of {@code count} instances of {@code ch}. Empty string if count is negative.
   */
  public static String generate(int count, char ch) {
    final char[] buf = new char[Math.max(0, count)];
    Arrays.fill(buf, ch);
    return new String(buf);
  }

  /**
   * Generates a string with {@code count} instances of computed characters.
   *
   * @param count Number of character instances in the resulting string.
   * @param fn    A functional interface that is executed to produce characters. It takes the character position as
   *                parameter.
   * @return {@code String} of {@code count} instances. Empty string if count is negative.
   */
  public static String generate(int count, Function<Integer, Character> fn) {
    if (fn == null) {
      throw new NullPointerException("Function argument is null");
    }

    final char[] buf = new char[Math.max(0, count)];
    for (int i = 0; i < buf.length; i++) {
      buf[i] = fn.apply(i);
    }
    return new String(buf);
  }

  /**
   * Returns a string representation of the specified object. Returns an empty string if the specified object is
   * {@code null}.
   */
  public static String safeToString(Object o) {
    return (o != null) ? o.toString() : "";
  }

  /**
   * Returns {@code obj} if non-null, otherwise return {@code def}.
   * @param <T> Type of the return value.
   * @param obj The value to return if non-null.
   * @param def A default value
   * @return Returns {@code obj} if non-null, otherwise {@code def} is returned.
   */
  public static <T> T orDefault(T obj, T def) {
    if (obj != null) {
      return obj;
    } else {
      return def;
    }
  }

  // TODO: remove when upgrading code base to Java 9 or later
  /**
   * Returns an unmodifiable map containing a single mapping.
   *
   * @param <K> The map's key type.
   * @param <V> The map's value type.
   * @param k1  the mapping's key.
   * @param v1  the mapping's value.
   * @return a {@link Map} containing the specified mapping.
   * @throws NullPointerException if the key or value is {@code null}
   */
  public static <K, V> Map<K, V> mapOf(K k1, V v1) {
    return mapOfEntries(new AbstractMap.SimpleEntry<>(k1, v1));
  }

  // TODO: remove when upgrading code base to Java 9 or later
  /**
   * Returns an unmodifiable map containing two mappings.
   *
   * @param <K> The map's key type.
   * @param <V> The map's value type.
   * @param k1  the first mapping's key.
   * @param v1  the first mapping's value.
   * @param k2  the second mapping's key.
   * @param v2  the second mapping's value.
   * @return a {@link Map} containing the specified mappings.
   * @throws IllegalArgumentException if the keys are duplicates
   * @throws NullPointerException     if any key or value is {@code null}
   */
  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
    return mapOfEntries(new AbstractMap.SimpleEntry<>(k1, v1),
        new AbstractMap.SimpleEntry<>(k2, v2));
  }

  // TODO: remove when upgrading code base to Java 9 or later
  /**
   * Returns an unmodifiable map containing three mappings.
   *
   * @param <K> The map's key type.
   * @param <V> The map's value type.
   * @param k1  the first mapping's key.
   * @param v1  the first mapping's value.
   * @param k2  the second mapping's key.
   * @param v2  the second mapping's value.
   * @param k3  the third mapping's key.
   * @param v3  the third mapping's value.
   * @return a {@link Map} containing the specified mappings.
   * @throws IllegalArgumentException if the keys are duplicates
   * @throws NullPointerException     if any key or value is {@code null}
   */
  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
    return mapOfEntries(new AbstractMap.SimpleEntry<>(k1, v1),
        new AbstractMap.SimpleEntry<>(k2, v2),
        new AbstractMap.SimpleEntry<>(k3, v3));
  }

  // TODO: remove when upgrading code base to Java 9 or later
  /**
   * Returns an unmodifiable map containing four mappings.
   *
   * @param <K> The map's key type.
   * @param <V> The map's value type.
   * @param k1  the first mapping's key.
   * @param v1  the first mapping's value.
   * @param k2  the second mapping's key.
   * @param v2  the second mapping's value.
   * @param k3  the third mapping's key.
   * @param v3  the third mapping's value.
   * @param k4  the fourth mapping's key.
   * @param v4  the fourth mapping's value.
   * @return a {@link Map} containing the specified mappings.
   * @throws IllegalArgumentException if the keys are duplicates
   * @throws NullPointerException     if any key or value is {@code null}
   */
  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return mapOfEntries(new AbstractMap.SimpleEntry<>(k1, v1),
        new AbstractMap.SimpleEntry<>(k2, v2),
        new AbstractMap.SimpleEntry<>(k3, v3),
        new AbstractMap.SimpleEntry<>(k4, v4));
  }

  // TODO: remove when upgrading code base to Java 9 or later
  /**
   * Returns an unmodifiable map containing five mappings.
   *
   * @param <K> The map's key type.
   * @param <V> The map's value type.
   * @param k1  the first mapping's key.
   * @param v1  the first mapping's value.
   * @param k2  the second mapping's key.
   * @param v2  the second mapping's value.
   * @param k3  the third mapping's key.
   * @param v3  the third mapping's value.
   * @param k4  the fourth mapping's key.
   * @param v4  the fourth mapping's value.
   * @param k5  the fifth mapping's key.
   * @param v5  the fifth mapping's value.
   * @return a {@link Map} containing the specified mappings.
   * @throws IllegalArgumentException if the keys are duplicates
   * @throws NullPointerException     if any key or value is {@code null}
   */
  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return mapOfEntries(new AbstractMap.SimpleEntry<>(k1, v1),
        new AbstractMap.SimpleEntry<>(k2, v2),
        new AbstractMap.SimpleEntry<>(k3, v3),
        new AbstractMap.SimpleEntry<>(k4, v4),
        new AbstractMap.SimpleEntry<>(k5, v5));
  }

  // TODO: remove when upgrading code base to Java 9 or later
  /**
   * Returns an unmodifiable map containing six mappings.
   *
   * @param <K> The map's key type.
   * @param <V> The map's value type.
   * @param k1  the first mapping's key.
   * @param v1  the first mapping's value.
   * @param k2  the second mapping's key.
   * @param v2  the second mapping's value.
   * @param k3  the third mapping's key.
   * @param v3  the third mapping's value.
   * @param k4  the fourth mapping's key.
   * @param v4  the fourth mapping's value.
   * @param k5  the fifth mapping's key.
   * @param v5  the fifth mapping's value.
   * @param k6  the sixth mapping's key.
   * @param v6  the sixth mapping's value.
   * @return a {@link Map} containing the specified mappings.
   * @throws IllegalArgumentException if the keys are duplicates
   * @throws NullPointerException     if any key or value is {@code null}
   */
  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
    return mapOfEntries(new AbstractMap.SimpleEntry<>(k1, v1),
        new AbstractMap.SimpleEntry<>(k2, v2),
        new AbstractMap.SimpleEntry<>(k3, v3),
        new AbstractMap.SimpleEntry<>(k4, v4),
        new AbstractMap.SimpleEntry<>(k5, v5),
        new AbstractMap.SimpleEntry<>(k6, v6));
  }

  // TODO: remove when upgrading code base to Java 9 or later
  /**
   * Returns an unmodifiable map containing keys and values extracted from the given entries. The entries themselves are
   * not stored in the map.
   *
   * @param <K>     the {@link Map}'s key type
   * @param <V>     the {@link Map}'s value type
   * @param entries {@link Map.Entry}s containing the keys and values from which the map is populated
   * @return a {@link Map} containing the specified mappings
   * @throws IllegalArgumentException if there are any duplicate keys
   * @throws NullPointerException     if any entry key, or value is {@code null}, or if the {@code entries} array is
   *                                    {@code null}
   */
  @SafeVarargs
  public static <K, V> Map<K, V> mapOfEntries(Map.Entry<? extends K, ? extends V>... entries) {
    if (entries == null) {
      throw new NullPointerException("entries array is null");
    }

    final HashMap<K, V> map = new HashMap<>(Math.min(2, 1 + entries.length * 4 / 3));

    for (int i = 0; i < entries.length; i++) {
      final Map.Entry<? extends K, ? extends V> entry = entries[i];
      if (entry == null) {
        throw new NullPointerException("entry " + i + " is null");
      }

      if (entry.getKey() == null || entry.getValue() == null) {
        throw new NullPointerException("key or value of entry " + i + " is null");
      }

      if (!map.containsKey(entry.getKey())) {
        map.put(entry.getKey(), entry.getValue());
      } else {
        throw new IllegalArgumentException("Duplicate keys specified");
      }
    }

    return Collections.unmodifiableMap(map);
  }

  /**
   * This method throws a general {@link Exception} without message if the specified condition isn't met.
   *
   * @param cond the condition to meet.
   * @throws Exception
   */
  public static void requireCondition(boolean cond) throws Exception {
    requireCondition(cond, null, null);
  }

  /**
   * This method throws a general {@link Exception} with associated message if the specified condition isn't met.
   *
   * @param cond    the condition to meet.
   * @param message the exception message. Can be {@code null}.
   * @throws Exception
   */
  public static void requireCondition(boolean cond, String message) throws Exception {
    requireCondition(cond, message, null);
  }

  /**
   * This method throws a specialized exception without message if the specified condition isn't met.
   *
   * @param cond    the condition to meet.
   * @param classEx the exception class to throw.
   * @throws Exception
   */
  public static void requireCondition(boolean cond, Class<? extends Exception> classEx) throws Exception {
    requireCondition(cond, null, classEx);
  }

  /**
   * This method throws a specialized exception with associated message if the specified condition isn't met.
   *
   * @param cond    the condition to meet.
   * @param message the exception message. Can be {@code null}.
   * @param classEx the exception class to throw.
   * @throws Exception
   */
  public static void requireCondition(boolean cond, String message, Class<? extends Exception> classEx)
      throws Exception {
    if (!cond) {
      if (message != null && message.isEmpty()) {
        message = null;
      }

      if (classEx == null) {
        classEx = Exception.class;
      }

      for (final Class<?> cls : new Class<?>[] { classEx, Exception.class }) {
        Object ex;
        if (message != null) {
          Constructor<?> ctor = cls.getConstructor(String.class);
          ex = ctor.newInstance(message);
        } else {
          Constructor<?> ctor = cls.getConstructor();
          ex = ctor.newInstance();
        }

        if (ex instanceof Exception) {
          throw (Exception) ex;
        }
      }
    }
  }

  /**
   * Helper function that ensures that the file name in the given path ends with the specified file extension.
   *
   * @param path {@link File} path to validate.
   * @param ext  File extension to add (with or without leading dot).
   * @return {@link File} path with the specified file extension.
   * @throws NullPointerException if {@code path} is {@code null}.
   */
  public static File ensureFileExtension(File path, String ext) {
    if (path == null) {
      throw new NullPointerException("path is null");
    }

    if (ext == null || ext.isEmpty()) {
      ext = "";
    } else if (ext.charAt(0) != '.') {
      ext = '.' + ext;
    }

    final String curName = path.getName();
    final int curExtPos = curName.lastIndexOf('.');
    final String curNameBase = (curExtPos > 0) ? curName.substring(0, curExtPos) : curName;
    final String curExt = (curExtPos > 0) ? curName.substring(curExtPos) : "";
    if (curExt.equalsIgnoreCase(ext)) {
      return path;
    }

    String outPath = (path.getParent() != null) ? path.getParent() + File.separatorChar : "";
    outPath += curNameBase + ext;
    return new File(outPath);
  }

  /**
   * Returns a list of relative paths for every file that is found in the specified package of the current Java application.
   *
   * @param packageName Fully qualified package name (e.g. {@code org.infinity}).
   * @return List of files found in the current Java application as {@link Path} instances relative to the application root.
   */
  public static List<Path> getFilesInPackage(String packageName) throws Exception {
    final Package pkg = Package.getPackage(Objects.requireNonNull(packageName));
    if (pkg == null) {
      throw new IllegalArgumentException("Package not found: " + packageName);
    }

    final String pkgPath = pkg.getName().replace('.', '/');
    final URL pkgUrl = ClassLoader.getSystemClassLoader().getResource(pkgPath);
    if (pkgUrl == null) {
      throw new IOException("Resource not found: " + pkgPath);
    }

    if ("jar".equals(pkgUrl.getProtocol())) {
      return getFilesInJarPackage(pkg);
    } else if ("file".equals(pkgUrl.getProtocol())) {
      return getFilesInDefaultPackage(pkg);
    } else {
      throw new IOException("Unsupported resource location: " + pkgUrl);
    }
  }

  /** Used internally to return a list of all files in the specified package if the application is a JAR file. */
  private static List<Path> getFilesInJarPackage(Package pkg) throws Exception {
    final List<Path> retVal = new ArrayList<>();

    final URI jarLocation = Misc.class.getProtectionDomain().getCodeSource().getLocation().toURI();
    try (final JarInputStream jis = new JarInputStream(Files.newInputStream(Paths.get(jarLocation), StandardOpenOption.READ))) {
      final String rootPath = pkg.getName().replace('.', '/') + "/";
      JarEntry je;
      while ((je = jis.getNextJarEntry()) != null) {
        if (je.getName().startsWith(rootPath) && !je.getName().equals(rootPath)) {
          final Path path = Paths.get(je.getName());
          retVal.add(path);
        }
      }
    }

    return retVal;
  }

  /**
   * Used internally to return a list of all files in the specified package if the application is invoked by a regular
   * class file.
   */
  private static List<Path> getFilesInDefaultPackage(Package pkg) throws Exception {
    final List<Path> retVal = new ArrayList<>();

    final String rootPath = pkg.getName().replace('.', '/');
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(ClassLoader.getSystemClassLoader().getResourceAsStream(rootPath)))) {
      final List<String> lines = reader.lines().collect(Collectors.toList());
      for (final String line : lines) {
        final Path path = Paths.get(rootPath, line);
        retVal.add(path);
      }
    }

    return retVal;
  }

  // Contains static functions only
  private Misc() {
  }
}
