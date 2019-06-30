// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.updater;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.EventListener;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

import static org.infinity.util.Misc.toNumber;

/**
 * Generic collection of updater-related methods.
 */
public class Utils
{
  // System-specific temp folder
  private static final String TEMP_FOLDER       = System.getProperty("java.io.tmpdir");

  // Path to java executable
  private static final String JAVA_EXECUTABLE   = System.getProperty("java.home") +
                                                  File.separator + "bin" + File.separator + "java";

  // The algorithm used for calculating hash strings
  private static final String HASH_TYPE         = "md5";

  /** Returns the full path to the system temp folder. */
  public static String getTempFolder()
  {
    return TEMP_FOLDER;
  }

  /** Returns the full path to the java executable. */
  public static String getJavaExecutable()
  {
    return JAVA_EXECUTABLE;
  }

  /**
   * Attempts to determine the full filepath of the java application.
   * @return The full path of the current JAR file or an empty string on error.
   */
  public static String getJarFileName(Class<? extends Object> classType)
  {
    if (classType != null) {
      URL url = classType.getProtectionDomain().getCodeSource().getLocation();
      if (url != null) {
        try {
          Path file = Paths.get(url.toURI());
          if (Files.exists(file)) {
            return file.toString();
          }
        } catch (URISyntaxException e) {
        }
      }
    }
    return "";
  }

  /**
   * Converts a Calendar object into a timestamp string in ISO 8601 format.
   */
  public static String toTimeStamp(Calendar cal)
  {
    if (cal == null) {
      cal = Calendar.getInstance();
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%04d", cal.get(Calendar.YEAR)));
    sb.append('-').append(String.format("%02d", cal.get(Calendar.MONTH) + 1));
    sb.append('-').append(String.format("%02d", cal.get(Calendar.DAY_OF_MONTH)));
    sb.append('T').append(String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)));
    sb.append(':').append(String.format("%02d", cal.get(Calendar.MINUTE)));
    sb.append(':').append(String.format("%02d", cal.get(Calendar.SECOND)));
    int ofs = cal.get(Calendar.ZONE_OFFSET);
    if (ofs != 0) {
      char sign = (ofs < 0) ? '-' : '+';
      ofs = Math.abs(ofs);
      int ofsHour = ofs / 3600000;
      int ofsMin = (ofs / 60000) % 60;
      sb.append(sign).append(String.format("%02d", ofsHour));
      sb.append(':').append(String.format("%02d", ofsMin));
    }

    return sb.toString();
  }

  /**
   * Converts a timestamp string in ISO 8601 format into a Calendar object.
   */
  public static Calendar toCalendar(String timeStamp)
  {
    Calendar retVal = null;
    if (timeStamp != null && !timeStamp.isEmpty()) {
      final String regDate  = "(\\d{4})-?([0-1][0-9])-?([0-3][0-9])";
      final String regTime = "T([0-2][0-9]):?([0-5][0-9])?:?([0-5][0-9])?";
      final String regZone  = "(([-+])([0-2][0-9]):?([0-5][0-9])|(Z))";
      int year = 0, month = -1, day = 0, hour = 0, minute = 0, second = 0, ofsHour = 0, ofsMinute = 0;
      char sign = 0;
      Matcher m;
      try {
        String s = timeStamp;
        // processing date
        m = Pattern.compile(regDate).matcher(s);
        if (m.find()) {
          year = toNumber(m.group(1), 0);
          month = toNumber(m.group(2), 0) - 1;
          day = toNumber(m.group(3), 0);

          // processing time
          s = s.substring(m.end());
          m = Pattern.compile(regTime).matcher(s);
          if (m.find()) {
            hour = toNumber(m.group(1), 0);
            if (m.groupCount() >= 2) {
              minute = toNumber(m.group(2), 0);
              if (m.groupCount() >= 3) {
                second = toNumber(m.group(3), 0);
              }
            }

            // processing timezone offset
            s = s.substring(m.end());
            m = Pattern.compile(regZone).matcher(s);
            if (m.find()) {
              if (m.group(5) != null) {
                sign = '+';
              } else {
                sign = m.group(2).charAt(0);
                ofsHour = toNumber(m.group(3), 0);
                if (m.groupCount() >= 3) {
                  ofsMinute = toNumber(m.group(4), 0);
                }
              }
            }
          }
        }
      } catch (PatternSyntaxException e) {
        e.printStackTrace();
      }

      if (year > 0 && month >= 0 && day > 0) {
        retVal = Calendar.getInstance();
        retVal.set(year, month, day, hour, minute, second);

        // applying timezone offset (in ms)
        if (sign != 0) {
          int amount = (ofsHour*60 + ofsMinute)*60*1000;
          switch (sign) {
            case '+':
              retVal.set(Calendar.ZONE_OFFSET, amount);
              break;
            case '-':
              retVal.set(Calendar.ZONE_OFFSET, -amount);
              break;
          }
        }
      }
    }
    return retVal;
  }

  /**
   * Returns an number based on the specified timestamp.
   * @param timestamp The timestamp in ISO 8601 format. Either in UTC or using time offset.<br>
   *                  Syntax: YYYY-MM-DD[Thh:mm[:ss][(±hh[:mm])|Z]]<br>
   *                  (Example: 2007-11-26T14:53+06:00)
   * @return A comparable numeric value of the timestamp (higher = newer). Returns 0 on error.
   */
  public static long toTimeValue(String timestamp)
  {
    Calendar cal = toCalendar(timestamp);
    if (cal != null) {
      return cal.getTimeInMillis();
    } else {
      return 0L;
    }
  }

  /**
   * Returns a number based on the date and time of the specified Calendar object.
   * @param cal The calendar object or {@code null} to get a number based on the current time.
   * @return A comparable numeric value derived from the calendar object (higher = newer).
   */
  public static long getTimeValue(Calendar cal)
  {
    if (cal == null) {
      cal = Calendar.getInstance();
    }

    return cal.getTimeInMillis();
  }


  /**
   * Calculates the hash value from input stream data using the specified hash type.
   * @param is The input stream to read the data from.
   */
  public static String generateMD5Hash(InputStream is)
  {
    if (is != null) {
      try {
        MessageDigest m = MessageDigest.getInstance(HASH_TYPE);
        int len;
        byte[] buffer = new byte[65536];

        while ((len = is.read(buffer)) > 0) {
          m.update(buffer, 0, len);
        }

        StringBuilder sb = new StringBuilder();
        sb.append((new BigInteger(1, m.digest())).toString(16).toLowerCase(Locale.ENGLISH));
        int digits = m.getDigestLength() * 2;
        while (sb.length() < digits) {
          sb.insert(0, '0');
        }
        return sb.toString();

      } catch (NoSuchAlgorithmException nsae) {
        nsae.printStackTrace();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
    return "";
  }

  /**
   * Checks whether the specified string contains a valid URL.
   * @param url The URL string to test.
   * @return {@code true} if the string contains a valid URL, {@code false} otherwise.
   */
  public static boolean isUrlValid(String url)
  {
    if (url != null) {
      try {
        new URL(url);
        return true;
      } catch (MalformedURLException e) {
      }
    }
    return false;
  }

  /**
   * Checks whether the specified URL points to a valid resource.
   * @param url The URL to check.
   * @param proxy An optional proxy definition. Can be null.
   * @return true if the URL is available, false otherwise.
   * @throws IOException
   * @throws IllegalArgumentException
   * @throws UnsupportedOperationException
   * @throws ProtocolException
   */
  public static boolean isUrlAvailable(URL url, Proxy proxy)
      throws IOException, IllegalArgumentException, UnsupportedOperationException, ProtocolException
  {
    if (url != null) {
      if (url.getProtocol().equalsIgnoreCase("http") || url.getProtocol().equalsIgnoreCase("https")) {
        // We only need to check header for HTTP protocol
        HttpURLConnection.setFollowRedirects(true);
        HttpURLConnection conn = null;
        if (proxy != null) {
          conn = (HttpURLConnection)url.openConnection(proxy);
        } else {
          conn = (HttpURLConnection)url.openConnection();
        }
        if (conn != null) {
          int timeout = conn.getConnectTimeout();
          conn.setConnectTimeout(6000);
          conn.setRequestMethod("HEAD");
          int responseCode;
          try {
            responseCode = conn.getResponseCode();
            conn.setConnectTimeout(timeout);
            return (responseCode == HttpURLConnection.HTTP_OK);
          } catch (IOException e) {
            conn.setConnectTimeout(timeout);
          }
        }
      } else {
        // more generic method
        InputStream is = null;
        URLConnection conn = null;
        if (proxy != null) {
          conn = url.openConnection(proxy);
        } else {
          conn = url.openConnection();
        }
        if (conn != null) {
          is = url.openStream();
          is.close();
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Attempts to determine the size of the file specified by url.
   * @param url The URL pointing to a file of any kind.
   * @param proxy An optional proxy definition. Can be {@code null}.
   * @return The size of the file or -1 on error.
   * @throws IOException
   */
  public static int getFileSizeUrl(URL url, Proxy proxy)
      throws IOException
  {
    if (url != null) {
      URLConnection conn = url.openConnection();
      return conn.getContentLength();
    }
    return -1;
  }

  /**
   * Attempts to return a valid URL from either an absolute 'path' or a relative 'path'
   * combined with 'base'.
   * @param base An optional URL which is used with a relative path parameter.
   * @param path Either an absolute path or a relative path together with the base parameter.
   * @return A valid URL or {@code null} in case of an error.
   * @throws MalformedURLException
   */
  public static URL getUrl(URL base, String path) throws MalformedURLException
  {
    URL retVal = null;
    if (path != null) {
      try {
        // try absolute url first
        retVal = new URL(path);
      } catch (MalformedURLException mue) {
        retVal = null;
      }

      if (retVal == null && base != null) {
        // try relative url
        String baseUrl = base.toExternalForm();
        int idx = baseUrl.indexOf('?');
        String basePath = (idx >= 0) ? baseUrl.substring(0, idx) : baseUrl;
        String suffix = (idx >= 0) ? baseUrl.substring(idx) : "";
        if (basePath.contains(path)) {
          retVal = new URL(basePath + suffix);
        } else {
          int cnt = 0;
          if (basePath.charAt(basePath.length() - 1) == '/') {
            cnt++;
          }
          if (path.charAt(0) == '/') {
            cnt++;
          }
          if (cnt == 2) {
            path = path.substring(1);
          } else if (cnt == 0) {
            path = '/' + path;
          }
          retVal = new URL(basePath + path + suffix);
        }
      }
    }
    return retVal;
  }

  /** Returns whether the specified link is using the https protocol. */
  public static boolean isSecureUrl(String link)
  {
    if (link != null) {
      try {
        URL url = new URL(link);
        return url.getProtocol().equalsIgnoreCase("https");
      } catch (MalformedURLException e) {
      }
    }
    return false;
  }

  /**
   * Checks whether the certificates of the specified HTTPS url are valid.
   * @param url The url to test. Must be HTTPS!
   * @return {@code true} if the certificate chain of the specified URL is valid.
   *         {@code false} if the certificate chain is invalid or the connection does not use
   *         the HTTPS protocol.
   * @throws Exception
   * @throws IOException
   * @throws IllegalArgumentException
   * @throws UnsupportedOperationException
   * @throws SSLPeerUnverifiedException
   * @throws IllegalStateException
   */
  public static boolean validateSecureConnection(URL url, Proxy proxy)
      throws Exception, IOException, IllegalArgumentException, UnsupportedOperationException,
             SSLPeerUnverifiedException, IllegalStateException
  {
    if (url != null && url.getProtocol().equalsIgnoreCase("https")) {
      HttpsURLConnection conn = null;
      try {
        if (proxy != null) {
          conn = (HttpsURLConnection)url.openConnection(proxy);
        } else {
          conn = (HttpsURLConnection)url.openConnection();
        }
        if (conn != null) {
          conn.connect();
          Certificate[] certs = conn.getServerCertificates();
          for (final Certificate cert: certs) {
            if (cert instanceof X509Certificate) {
              ((X509Certificate)cert).checkValidity();
            } else {
              throw new Exception("Not a X.509 certificate");
            }
          }
          conn.disconnect();
          return true;
        }
      } finally {
        if (conn != null) {
          conn.disconnect();
        }
      }
    }
    return false;
  }

  /**
   * Attempts to open the specified URL in the default browser of the system.
   * @param url The URL to open.
   * @return {@code true} if the URL has been opened in the system's default browser.
   *         {@code false} otherwise.
   * @throws IOException
   * @throws URISyntaxException
   * @throws UnsupportedOperationException
   * @throws IllegalArgumentException
   */
  public static boolean openWebPage(URL url)
      throws IOException, URISyntaxException, UnsupportedOperationException, IllegalArgumentException
  {
    if (url != null) {
      URI uri = url.toURI();
      if (uri != null) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
          desktop.browse(uri);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * A convenience method for downloading textual data from a URL.
   * @param url The URL to download data from.
   * @param proxy An optional proxy definition. Can be {@code null}.
   * @param charset The character set of the text content (e.g. utf-8).
   * @return The text content on success or {@code null} on error.
   * @throws IOException
   * @throws FileNotFoundException
   * @throws ProtocolException
   * @throws UnknownServiceException
   * @throws ZipException
   */
  public static String downloadText(URL url, Proxy proxy, String charset)
      throws IOException, FileNotFoundException, ProtocolException, UnknownServiceException, ZipException
  {
    String retVal = null;
    if (url != null) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      if (Utils.downloadFromUrl(url, proxy, baos, UpdateInfo.FileType.ORIGINAL, null)) {
        if (charset == null  || charset.isEmpty() || !Charset.isSupported(charset)) {
          charset = Charset.defaultCharset().name();
        }
        try {
          retVal = baos.toString(charset);
        } catch (UnsupportedEncodingException e) {
        }
      }
      baos = null;
    }
    return retVal;
  }

  /**
   * Downloads data from the specified URL into the output stream. It is recommended to call this
   * method in a background task.
   * @param url The URL to download data from.
   * @param proxy An optional proxy definition. Can be {@code null}.
   * @param os The output stream to write the downloaded data into.
   * @param type The file type of the data.
   *             FileType.Original does no further preprocessing.
   *             FileType.ZIP unpacks the first available file found in the zip archive.
   *             FileType.GZIP unpacks the data and writes it into the output stream.
   * @param listeners A list of event listeners to keep track of the current download progress.
   * @return true on success, false on error or if operation has been canceled.
   * @throws IOException
   * @throws ProtocolException
   * @throws UnknownServiceException
   * @throws ZipException
   * @throws FileNotFoundException
   */
  public static boolean downloadFromUrl(URL url, Proxy proxy, OutputStream os, UpdateInfo.FileType type,
                                        List<ProgressListener> listeners)
      throws IOException, ProtocolException, UnknownServiceException, ZipException, FileNotFoundException
  {
    if (url != null && os != null) {
      URLConnection conn = null;
      if (proxy != null) {
        conn = url.openConnection(proxy);
      } else {
        conn = url.openConnection();
      }

      if (conn != null) {
        int timeout = conn.getConnectTimeout();
        conn.setConnectTimeout(6000);   // wait max. 6 seconds
        InputStream is = conn.getInputStream();
        conn.setConnectTimeout(timeout);
        try {
          switch (type) {
            case ORIGINAL:
              return downloadRaw(is, os, url, proxy, listeners);
            case ZIP:
              return downloadZip(is, os, url, proxy, listeners);
            case GZIP:
              return downloadGzip(is, os, url, proxy, listeners);
            case UNKNOWN:
              return false;
          }
        } finally {
          is.close();
          is = null;
        }
      }
    }
    return false;
  }

  /**
   * Download data from the input stream into the output stream without special processing.
   * @return {@code true} if the download has finished successfully,
   *         {@code false} on error or if the download has been cancelled.
   * @throws IOException
   * @throws ProtocolException
   * @throws UnknownServiceException
   */
  static boolean downloadRaw(InputStream is, OutputStream os, URL url, Proxy proxy,
                             List<ProgressListener> listeners)
      throws UnknownServiceException, ProtocolException, IOException
  {
    if (is != null && os != null) {
      byte[] buffer = new byte[4096];
      try {
        int totalSize = getFileSizeUrl(url, proxy);
        int curSize = 0;
        int size;
        while ((size = is.read(buffer)) > 0) {
          os.write(buffer, 0, size);
          curSize += size;
          if (fireProgressEvent(listeners, url, curSize, totalSize, false)) {
            os.flush();
            return false;
          }
        }
        os.flush();
        fireProgressEvent(listeners, url, curSize, totalSize, true);
        return true;
      } finally {
        buffer = null;
      }
    }
    return false;
  }

  /**
   * Decompresses the first available file entry in the zipped data provided by the input stream.
   * @return {@code true} if the download has finished successfully,
   *         {@code false} on error or if the download has been cancelled.
   * @throws IOException
   * @throws ZipException
   */
  static boolean downloadZip(InputStream is, OutputStream os, URL url, Proxy proxy,
                             List<ProgressListener> listeners)
      throws IOException, ZipException
  {
    if (is != null && os != null) {
      ZipInputStream zis = new ZipInputStream(is);
      byte[] buffer = new byte[4096];
      try {
        ZipEntry entry = zis.getNextEntry();
        if (entry != null) {
          int totalSize = (int)entry.getSize();
          int curSize = 0;
          int size;
          while ((size = zis.read(buffer)) != -1) {
            os.write(buffer, 0, size);
            curSize += size;
            if (fireProgressEvent(listeners, url, curSize, totalSize, false)) {
              os.flush();
              return false;
            }
          }
          os.flush();
          fireProgressEvent(listeners, url, curSize, totalSize, true);
          return true;
        }
      } finally {
        zis.close();
        zis = null;
        buffer = null;
      }
    }
    return false;
  }

  /**
   * Decompresses the GZIP compressed data provided by the input stream.
   * @return {@code true} if the download has finished successfully,
   *         {@code false} on error or if the download has been cancelled.
   * @throws IOException
   */
  static boolean downloadGzip(InputStream is, OutputStream os, URL url, Proxy proxy,
                              List<ProgressListener> listeners)
      throws IOException
  {
    if (is != null && os != null) {
      GZIPInputStream gis = null;
      byte[] buffer = new byte[4096];
      try {
        gis = new GZIPInputStream(is);
        int totalSize = -1;   // impossible to determine the uncompressed file size
        int curSize = 0;
        int size;
        while ((size = gis.read(buffer)) != -1) {
          os.write(buffer, 0, size);
          curSize += size;
          if (fireProgressEvent(listeners, url, curSize, totalSize, false)) {
            os.flush();
            return false;
          }
        }
        os.flush();
        fireProgressEvent(listeners, url, curSize, totalSize, true);
        return true;
      } finally {
        if (gis != null) {
          gis.close();
          gis = null;
        }
        buffer = null;
      }
    }
    return false;
  }

  // Informs about the progress of the current operation. Returns true if the operation can be cancelled.
  static boolean fireProgressEvent(List<ProgressListener> listeners, URL url,
                                int curBytes, int totalBytes, boolean finished)
  {
    boolean bRet = false;
    if (listeners != null) {
      ProgressEvent event = null;
      for (Iterator<ProgressListener> iter = listeners.iterator(); iter.hasNext();) {
        if (event == null) {
          event = new ProgressEvent(url, curBytes, totalBytes, finished);
        }
        ProgressListener l = iter.next();
        if (l != null) {
          l.dataProgressed(event);
        }
      }
      if (event != null) {
        bRet = event.isOperationCancelled();
      }
    }
    return bRet;
  }

// TODO: remove?
//  /**
//   * Executes the specified JAR file with optional parameters and working directory.
//   * @param jar The JAR file to execute.
//   * @param params Parameter list. Can be {@code null}.
//   * @param dir An optional working directory. Can be {@code null}.
//   * @return The Process instance of the executed JAR file or {@code null} on error.
//   */
//  public static Process executeJar(String jar, String params, String dir)
//  {
//    if (jar != null && !jar.isEmpty()) {
//      StringBuilder sb = new StringBuilder();
//      sb.append(JAVA_EXECUTABLE).append(" ");
//      sb.append("-jar ");
//      sb.append(jar).append(" ");
//      if (params != null && !params.isEmpty()) {
//        sb.append(params);
//      }
//      File file = null;
//      if (dir != null && !dir.isEmpty()) {
//        file = new File(dir);
//        if (!file.isDirectory()) {
//          file = null;
//        }
//      }
//      try {
//        return Runtime.getRuntime().exec(sb.toString(), null, file);
//      } catch (IOException e) {
//      }
//    }
//    return null;
//  }

  private Utils() {}


//-------------------------- INNER CLASSES --------------------------

  public static interface ProgressListener extends EventListener
  {
    void dataProgressed(ProgressEvent event);
  }

  /** Is used to notify interested parties to document the progress in a download or upload operation. */
  public static class ProgressEvent extends EventObject
  {
    private final boolean finished;
    private final int currentBytes, totalBytes;

    private boolean cancelOperation;

    /**
     * Constructs a new ProgressEvent object.
     * @param source should point to the URL object to download from or upload to.
     * @param currentBytes The cumulative amount of bytes processed in the current operation up until now.
     * @param totalBytes The total amount of bytes to process in the current operation.
     *                   Specify -1 if the total size of the data is unknown.
     */
    public ProgressEvent(Object source, int currentBytes, int totalBytes, boolean finished)
    {
      super(source);
      this.currentBytes = currentBytes;
      this.totalBytes = totalBytes;
      this.finished = finished;
      this.cancelOperation = false;
    }

    /** Returns the cumulative amount of bytes processed in the current operation up until now. */
    public int getCurrentBytes() { return currentBytes; }

    /**
     * Returns the total amount of bytes to procress in the current operation.
     * Can be -1 for operations where the total total size of the data is unknown.
     */
    public int getTotalBytes() { return totalBytes; }

    /** Returns true if the process has been finished, false otherwise. */
    public boolean isFinished() { return finished; }

    /**
     * Call this method to signal that the current operation can be canceled ({@code true})
     * or resumed ({@code false}).
     */
    public void cancelOperation(boolean cancel) { cancelOperation = cancel; }

    /** Returns whether the current operation can be cancelled. */
    public boolean isOperationCancelled() { return cancelOperation; }
  }

}
