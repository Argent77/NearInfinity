// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.updater;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.infinity.util.Misc;
import org.infinity.util.Pair;
import org.infinity.util.io.StreamUtils;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Provides access to update information stored in "update.xml".
 */
public class UpdateInfo
{
  /** Available release types. */
  public enum ReleaseType {
    /** Information about the latest Near Infinity release (stable or unstable). */
    LATEST,
    /** Information about the latest stable Near Infinity release. */
    STABLE,
    /** Information about the updater helper tool. */
    UPDATER
  }

  /** File type of data to be downloaded. */
  public enum FileType {
    /** Indicates an unknown or unsupported file type. */
    UNKNOWN,
    /** No further processing necessary. */
    ORIGINAL,
    /** Unpack first available file from the zip archive. */
    ZIP,
    /** Uncompress gzip file. */
    GZIP
  }

  // Supported node and attribute names
  private static final String NODE_UPDATE       = "update";
  private static final String NODE_GENERAL      = "general";
  private static final String NODE_RELEASE      = "release";
  private static final String NODE_SERVER       = "server";
  private static final String NODE_INFO         = "info";
  private static final String NODE_LINK         = "link";
  private static final String NODE_FILE         = "file";
  private static final String NODE_NAME         = "name";
  private static final String NODE_URL          = "url";
  private static final String NODE_VERSION      = "version";
  private static final String NODE_TIMESTAMP    = "timestamp";
  private static final String NODE_HASH         = "hash";
  private static final String NODE_CHANGELOG    = "changelog";
  private static final String NODE_ENTRY        = "entry";
  private static final String ATTR_VERSION      = "version";
  private static final String ATTR_TYPE         = "type";

  private final EnumMap<ReleaseType, Release> releases = new EnumMap<ReleaseType, Release>(ReleaseType.class);

  private General general;
  private int version;

  /**
   * Checks if the specified string contains valid update.xml data.
   * <b>Note:</b> This is an expensive operation.
   * @param s The string to check.
   * @param systemId Base path for relative URIs (required for Doctype reference).
   * @return {@code true} if the string conforms to the update.xml specification,
   *         {@code false} otherwise.
   */
  public static boolean isValidXml(String s, String systemId)
  {
    try {
      return isValidXml(new ByteArrayInputStream(s.getBytes("UTF-8")), systemId);
    } catch (UnsupportedEncodingException e) {
    }
    return false;
  }

  /**
   * Checks if the specified file contains valid update.xml data.
   * <b>Note:</b> This is an expensive operation.
   * @param f The file to read data from.
   * @return {@code true} if the file content conforms to the update.xml specification,
   *         {@code false} otherwise.
   */
  public static boolean isValidXml(Path f)
  {
    try (InputStream is = StreamUtils.getInputStream(f)) {
      return isValidXml(is, f.getParent().toAbsolutePath().toString());
    } catch (IOException e) {
    }
    return false;
  }

  /**
   * Checks if the specified input stream points to valid update.xml data.
   * <b>Note:</b> This is an expensive operation.
   * @param is The input stream to read data from.
   * @param systemId Base path for relative URIs (required for Doctype reference).
   * @return {@code true} if the data from the stream conforms to the update.xml specification,
   *         {@code false} otherwise.
   */
  public static boolean isValidXml(InputStream is, String systemId)
  {
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      dBuilder.setErrorHandler(new XmlErrorHandler());
      Document doc = dBuilder.parse(is, systemId);
      return NODE_UPDATE.equals(doc.getDocumentElement().getNodeName());
    } catch (Exception e) {
    }
    return false;
  }

  /**
   * Read update information from the given InputStream object.
   * @param is InputStream object pointing to the update data in XML format.
   * @param systemId Base path for relative URIs (required for Doctype reference).
   */
  public UpdateInfo(InputStream is, String systemId) throws Exception
  {
    parseXml(is, systemId);
  }

  /**
   * Read update information from the specified file.
   * @param f The file containing update information in XML format.
   */
  public UpdateInfo(Path f) throws Exception
  {
    try (InputStream is = StreamUtils.getInputStream(f)) {
      parseXml(is, f.getParent().toAbsolutePath().toString());
    }
  }

  /**
   * Read update information from the specified string.
   * @param s The text string containing update information in XML format.
   * @param systemId Base path for relative URIs (required for Doctype reference).
   */
  public UpdateInfo(String s, String systemId) throws Exception
  {
    parseXml(new ByteArrayInputStream(s.getBytes("UTF-8")), systemId);
  }

  /**
   * Provides access to the General section of the update.xml.
   */
  public General getGeneral()
  {
    return general;
  }

  /**
   * Provides access to the the Release section specified by the user in the Server Settings
   * or returns {@code null} if not available.
   */
  public Release getRelease()
  {
    return releases.get(Updater.getInstance().isStableOnly() ? ReleaseType.STABLE : ReleaseType.LATEST);
  }

  /**
   * Provides access to the specified Release section or returns {@code null} if not available.
   * @param type The release type to access.
   */
  public Release getRelease(ReleaseType type)
  {
    return releases.get(type);
  }

  // Returns whether all mandatory fields have been initialized correctly.
  public boolean isValid()
  {
    boolean retVal = true;
    if (getGeneral() != null) {
      retVal &= getGeneral().isValid();
    }
    if (getRelease() == null) {
      retVal = false;
    }
    if (retVal) {
      for (Iterator<Release> iter = releases.values().iterator(); iter.hasNext();) {
        retVal &= iter.next().isValid();
      }
    }

    return retVal;
  }

  /**
   * Returns the specification version of the current update.xml. Any version > 0 is valid.
   * Newer versions are supposed to be backwards compatible.
   * @return The specification version of the update.xml.
   */
  public int getUpdateInfoVersion()
  {
    return version;
  }


  private void parseXml(InputStream is, String systemId) throws Exception
  {
    if (is != null) {
      // reading XML data
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      dBuilder.setErrorHandler(new XmlErrorHandler());
      Document doc = dBuilder.parse(is, systemId);
      doc.getDocumentElement().normalize();

      DocumentType docType = doc.getDoctype();
      if (docType == null) {
        throw new Exception("Update.xml: No DTD specified");
      }

      // initializing data
      Element elemRoot = doc.getDocumentElement();
      if (!elemRoot.getNodeName().equals(NODE_UPDATE)) {
        throw new Exception("Update.xml: Unsupported root node name: " + elemRoot.getNodeName());
      }
      version = Misc.toNumber(elemRoot.getAttribute(ATTR_VERSION), 0);
      if (getUpdateInfoVersion() < 1) {
        throw new Exception("Update.xml: Unsupported or missing specification version");
      }

      // TODO: parse elements based on update version

      // initializing general section
      NodeList generalList = elemRoot.getElementsByTagName(NODE_GENERAL);
      for (int idx = 0, size = generalList.getLength(); idx < size; idx++) {
        Node n = generalList.item(idx);
        if (n.getNodeType() == Node.ELEMENT_NODE) {
          // General section is optional
          parseGeneral((Element)n);
          break;
        }
      }

      // initializing release sections
      NodeList releaseList = elemRoot.getElementsByTagName(NODE_RELEASE);
      for (int idx = 0, size = releaseList.getLength(); idx < size; idx++) {
        Node n = releaseList.item(idx);
        if (n.getNodeType() == Node.ELEMENT_NODE) {
          parseRelease((Element)n);
        }
      }
    }
  }

  private void parseGeneral(Element elemGeneral) throws Exception
  {
    if (elemGeneral == null || !elemGeneral.getNodeName().equals(NODE_GENERAL)) {
      throw new Exception("Update.xml: Node \"" + NODE_GENERAL + "\" expected");
    }

    List<String> serverList = new ArrayList<String>();
    List<Pair<String>> infoList = new ArrayList<Pair<String>>();

    NodeList children = elemGeneral.getChildNodes();
    for (int idx = 0, size = children.getLength(); idx < size; idx++) {
      Node node = children.item(idx);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      if (node.getNodeName().equals(NODE_SERVER)) {
        NodeList list = node.getChildNodes();
        for (int j = 0, listSize = list.getLength(); j < listSize; j++) {
          node = list.item(j);
          if (node.getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }
          if (node.getNodeName().equals(NODE_LINK)) {
            try {
              URL url = new URL(node.getTextContent().trim());
              serverList.add(url.toExternalForm());
            } catch (MalformedURLException e) {
              // don't add invalid URLs
            }
          }
        }
      } else if (node.getNodeName().equals(NODE_INFO)) {
        NodeList list = node.getChildNodes();
        Node n1 = null, n2 = null;
        for (int j = 0, listSize = list.getLength(); j < listSize; j++) {
          Node n = list.item(j);
          if (n.getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }
          if (n.getNodeName().equals(NODE_NAME)) {
            n1 = n;
          } else if (n.getNodeName().equals(NODE_LINK)) {
            n2 = n;
          }
        }
        if (n1 != null && n2 != null) {
          try {
            String name = n1.getTextContent().trim();
            URL url = new URL(n2.getTextContent().trim());
            infoList.add(new Pair<String>(name, url.toExternalForm()));
          } catch (MalformedURLException e) {
            // don't add invalid URLs
          }
        }
      }
    }

    general = new General(serverList, infoList);
  }

  private void parseRelease(Element elemRelease) throws Exception
  {
    if (elemRelease == null || !elemRelease.getNodeName().equals(NODE_RELEASE)) {
      throw new Exception("Update.xml: Node \"" + NODE_RELEASE + "\" expected");
    }

    ReleaseType type = null;
    String fileName = null;
    String link = null;
    String linkType = null;
    String version = null;
    String timeStamp = null;
    String hash = null;
    String linkManual = null;
    List<String> changelog = null;

    try {
      type = Enum.valueOf(ReleaseType.class, elemRelease.getAttribute(ATTR_TYPE).toUpperCase());
    } catch (IllegalArgumentException e) {
      // skipping unsupported entries
    }
    if (type == null) {
      type = ReleaseType.LATEST;
    }

    // preprocessing available child elements in "release" section
    NodeList children = elemRelease.getChildNodes();
    Element elemFile = null, elemChangelog = null;
    for (int i = 0, size = children.getLength(); i < size; i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        if (node.getNodeName().equals(NODE_FILE)) {
          elemFile = (Element)node;
        } else if (node.getNodeName().equals(NODE_CHANGELOG)) {
          elemChangelog = (Element)node;
        }
      }
    }

    // processing required element "file"
    if (elemFile == null || !elemFile.getNodeName().equals(NODE_FILE)) {
      throw new Exception("Update.xml: Missing \"" + NODE_FILE + "\" node");
    }
    children = elemFile.getChildNodes();
    for (int idx = 0, size = children.getLength(); idx < size; idx++) {
      Element elem = null;
      if (children.item(idx).getNodeType() == Node.ELEMENT_NODE) {
        elem = (Element)children.item(idx);
      } else {
        continue;
      }
      if (elem.getNodeName().equals(NODE_NAME)) {
        fileName = elem.getTextContent().trim();
      } else if (elem.getNodeName().equals(NODE_URL)) {
        linkType = elem.getAttribute(ATTR_TYPE);
        link = elem.getTextContent().trim();
      } else if (elem.getNodeName().equals(NODE_VERSION)) {
        version = elem.getTextContent().trim();
      } else if (elem.getNodeName().equals(NODE_TIMESTAMP)) {
        timeStamp = elem.getTextContent().trim();
      } else if (elem.getNodeName().equals(NODE_HASH)) {
        hash = elem.getTextContent().trim();
      } else if (elem.getNodeName().equals(NODE_LINK)) {
        linkManual = elem.getTextContent().trim();
      }
    }

    // processing optional element "changelog"
    if (elemChangelog != null) {
      changelog = new ArrayList<String>();
      children = elemChangelog.getElementsByTagName(NODE_ENTRY);
      for (int idx = 0, size = children.getLength(); idx < size; idx++) {
        Element elem = (Element)children.item(idx);
        String s = elem.getTextContent().trim();
        if (!s.isEmpty()) {
          changelog.add(s);
        }
      }
      if (changelog.isEmpty()) {
        changelog = null;
      }
    }

    Release release = new Release(type, fileName, link, linkType, version, hash, timeStamp,
                                  linkManual, changelog);
    releases.put(type, release);
  }


//-------------------------- INNER CLASSES --------------------------

  // Manages "General" information
  public static class General
  {
    private final List<String> servers = new ArrayList<String>();
    private final List<Pair<String>> information = new ArrayList<Pair<String>>();

    private General(List<String> servers, List<Pair<String>> information) throws Exception
    {
      if (servers != null) {
        this.servers.addAll(servers);
      }
      if (information != null) {
        this.information.addAll(information);
      }
    }

    /**  Returns number of available alternate update servers. */
    public int getServerCount() { return servers.size(); }

    /** Returns the URL of the specified alternate server or {@code null} if not available. */
    public String getServer(int index)
    {
      if (index >= 0 && index < getServerCount()) {
        return servers.get(index);
      } else {
        return null;
      }
    }

    /** Returns number of available links to related websites. */
    public int getInformationCount() { return information.size(); }

    /** Returns name of the specified related website or {@code null} if not available. */
    public String getInformationName(int index)
    {
      if (index >= 0 && index < getInformationCount()) {
        return information.get(index).getFirst();
      } else {
        return null;
      }
    }

    /** Returns URL of the specified related website or {@code null} if not available. */
    public String getInformationLink(int index)
    {
      if (index >= 0 && index < getInformationCount()) {
        return information.get(index).getSecond();
      } else {
        return null;
      }
    }

    /** Checks whether data has been initialized correctly. */
    public boolean isValid()
    {
      for (int i = 0, size = getServerCount(); i < size; i++) {
        String url = getServer(i);
        if (!Utils.isUrlValid(url)) {
          return false;
        }
      }
      for (int i = 0, size = getInformationCount(); i < size; i++) {
        String name = getInformationName(i);
        String url = getInformationLink(i);
        if (name == null || name.isEmpty() || !Utils.isUrlValid(url)) {
          return false;
        }
      }
      return true;
    }
  }


  // Manages "Release" information
  public static class Release
  {
    private final List<String> changelog = new ArrayList<String>();
    private final ReleaseType type;
    private String fileName, link, linkManual, version, hash;
    private FileType linkType;
    private Calendar timeStamp;

    private Release(ReleaseType type, String fileName, String link, String linkType, String version,
                  String hash, String timeStamp, String linkManual, List<String> changelog) throws Exception
    {
      // checking mandatory fields
      if (fileName == null) {
        throw new Exception(String.format("Update.xml: Missing \"%s\" node in %s section",
                                          NODE_NAME, NODE_RELEASE));
      }
      if (link == null) {
        throw new Exception(String.format("Update.xml: Missing \"%s\" node in %s section",
                                          NODE_LINK, NODE_RELEASE));
      }
      if (version == null) {
        throw new Exception(String.format("Update.xml: Missing \"%s\" node in %s section",
                                          NODE_VERSION, NODE_RELEASE));
      }
      if (timeStamp == null) {
        throw new Exception(String.format("Update.xml: Missing \"%s\" node in %s section",
                                          NODE_TIMESTAMP, NODE_RELEASE));
      }
      if (hash == null) {
        throw new Exception(String.format("Update.xml: Missing \"%s\" node in %s section",
                                          NODE_HASH, NODE_RELEASE));
      }

      this.type = type;
      this.fileName = fileName;
      this.link = link;
      this.linkType = validateLinkType(linkType);
      this.version = version;
      this.hash = hash;
      this.timeStamp = Utils.toCalendar(timeStamp);
      this.linkManual = linkManual;
      if (changelog != null) {
        this.changelog.addAll(changelog);
      }
    }

    /** Returns the type of this file entry. */
    public ReleaseType getReleaseType() { return type; }

    /** Returns the actual filename without path. */
    public String getFileName() { return fileName; }

    /** Returns the link to the file. Use {@link #getLinkType()} to determine archive format. */
    public String getLink() { return link; }

    /** Returns the archive format of the file to download. */
    public FileType getLinkType() { return linkType; }

    /** Returns the file version. */
    public String getVersion() { return version; }

    /** Returns the md5 hash string for the file to download. */
    public String getHash() { return hash; }

    /** Returns the date and time of the file. */
    public Calendar getTimeStamp() { return timeStamp; }

    /** Returns a String version of the timestamp in  ISO 8601 format. */
    public String getTimeStampString() { return Utils.toTimeStamp(timeStamp); }

    /** Returns a link to the file for manual download or {@code null} if not available. */
    public String getDownloadLink() { return linkManual; }

    /** Returns whether a ChangeLog is available. */
    public boolean hasChangeLog() { return !changelog.isEmpty(); }

    /** Returns a read-only list of changelog entries. */
    public List<String> getChangelog() { return Collections.unmodifiableList(changelog); }

    /** Checks whether data has been initialized correctly. */
    public boolean isValid()
    {
      if (getReleaseType() == null) {
        return false;
      }
      if (!Utils.isUrlValid(getLink())) {
        return false;
      }
      if (getDownloadLink() != null && !Utils.isUrlValid(getDownloadLink())) {
        return false;
      }
      return true;
    }

    // Returns only supported archive formats for the linked file
    private static FileType validateLinkType(String linkType) throws Exception
    {
      if ("jar".equalsIgnoreCase(linkType)) {
        return FileType.ORIGINAL;
      } else if ("zip".equalsIgnoreCase(linkType)) {
        return FileType.ZIP;
      } else if ("gzip".equalsIgnoreCase(linkType)) {
        return FileType.GZIP;
      }
      throw new Exception("Invalid link type: " + linkType);
    }
  }

  private static class XmlErrorHandler implements ErrorHandler
  {
    public XmlErrorHandler() {}

    @Override
    public void warning(SAXParseException exception) throws SAXException
    {
      throw exception;
    }

    @Override
    public void error(SAXParseException exception) throws SAXException
    {
      throw exception;
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException
    {
      throw exception;
    }
  }
}
