// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.cli;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.infinity.AppOption;
import org.infinity.datatype.AbstractBitmap;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.IsTextual;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.ResourceBitmap;
import org.infinity.datatype.StringRef;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads game resources selected by regular expressions and writes their parsed fields as JSON.
 */
public final class PrintGameObjectsToXml implements CommandLineTool {
  @Override
  public void run(String fileName) throws Exception {
    JSONObject input = new JSONObject(new String(Files.readAllBytes(Paths.get(fileName)), StandardCharsets.UTF_8));
    Path workingDirectory = Paths.get(fileName).toAbsolutePath().getParent();
    if (workingDirectory == null) {
      workingDirectory = Paths.get("").toAbsolutePath();
    }

    Path gamePath = resolvePath(workingDirectory, input.optString("game", ""));
    Path weiduPath = resolvePath(workingDirectory, input.optString("weidu", ""));
    Path outputPath = resolvePath(workingDirectory, input.optString("output", ""));
    String existingFolder = input.optString("existingFolder", "create_overwrite");
    JSONArray resourcePatterns = input.optJSONArray("resourceRegexp");
    JSONArray looseFiles = input.optJSONArray("looseFiles");
    if (!Files.isRegularFile(gamePath) || !"key".equalsIgnoreCase(extension(gamePath))) {
      throw new IOException("The 'game' path must point to a chitin.key file.");
    }
    if (!Files.isRegularFile(weiduPath)) {
      throw new IOException("The 'weidu' path must point to a WeiDU executable.");
    }
    prepareOutputFolder(outputPath, existingFolder);
    if ((resourcePatterns == null || resourcePatterns.length() == 0)
        && (looseFiles == null || looseFiles.length() == 0)) {
      throw new IOException("At least one of 'resourceRegexp' or 'looseFiles' must contain an entry.");
    }

    // Initialize AppOption before BrowserMenuBar to avoid the WeiDU default-value initialization cycle.
    AppOption.WEIDU_PATH.setValue(weiduPath.toString());
    if (!BrowserMenuBar.isInstantiated()) {
      new BrowserMenuBar();
    }
    if (!Profile.openGame(gamePath)) {
      throw new IOException("Unable to load game data: " + gamePath);
    }

    int patternCount = resourcePatterns == null ? 0 : resourcePatterns.length();
    Pattern[] patterns = new Pattern[patternCount];
    List<ResourceEntry> externalEntries = new ArrayList<>();
    if (looseFiles != null) {
      for (int i = 0; i < looseFiles.length(); i++) {
        String fileNameValue = looseFiles.optString(i, "");
        if (fileNameValue.trim().isEmpty()) {
          throw new IOException("Loose file " + i + " must not be empty.");
        }
        Path externalPath = resolvePath(workingDirectory, fileNameValue);
        if (!Files.isRegularFile(externalPath)) {
          throw new IOException("Loose file does not exist: " + externalPath);
        }
        externalEntries.add(new FileResourceEntry(externalPath));
      }
    }
    for (int i = 0; i < patterns.length; i++) {
      String expression = resourcePatterns.optString(i, "");
      if (expression.trim().isEmpty()) {
        throw new IOException("Resource expression " + i + " must not be empty.");
      }
      patterns[i] = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
    }

    Collection<ResourceEntry> entries = ResourceFactory.getResourceTreeModel().getResourceEntries();
    List<ResourceEntry> resources = new ArrayList<>(externalEntries);
    for (ResourceEntry entry : entries) {
      if (matches(entry.getResourceName(), patterns)) {
        resources.add(entry);
      }
    }
    List<Path> outputFiles = getOutputFiles(outputPath, resources);
    if ("failOnOverwrite".equals(existingFolder)) {
      for (Path outputFile : outputFiles) {
        if (Files.exists(outputFile)) {
          throw new IOException("Output file already exists: " + outputFile);
        }
      }
    }
    for (ResourceEntry entry : resources) {
      Resource resource = ResourceFactory.getResource(entry);
      if (resource == null) {
        throw new IOException("Unable to parse resource: " + entry.getResourceName());
      }
      try {
        if (!(resource instanceof AbstractStruct)) {
          throw new IOException("Resource has no structured fields: " + entry.getResourceName());
        }
        Path outputFile = outputPath.resolve(entry.getResourceName() + ".xml").normalize();
        Files.write(outputFile, structToXml((AbstractStruct) resource).getBytes(StandardCharsets.UTF_8));
      } finally {
        if (resource instanceof Closeable) {
          ((Closeable) resource).close();
        }
      }
    }
  }

  private static void prepareOutputFolder(Path outputPath, String existingFolder) throws IOException {
    Set<String> modes = new HashSet<>();
    Collections.addAll(modes, "failIfNotEmpty", "failOnOverwrite", "overwrite", "clear", "create_overwrite",
        "craete_clear");
    if (!modes.contains(existingFolder)) {
      throw new IOException("Unknown existingFolder option: " + existingFolder);
    }
    if (Files.exists(outputPath) && !Files.isDirectory(outputPath)) {
      throw new IOException("The 'output' path exists but is not a folder: " + outputPath);
    }
    boolean create = existingFolder.startsWith("create_") || "craete_clear".equals(existingFolder);
    if (!Files.exists(outputPath)) {
      if (create) {
        Files.createDirectories(outputPath);
        return;
      }
      throw new IOException("The 'output' folder does not exist: " + outputPath);
    }
    if ("failIfNotEmpty".equals(existingFolder) && hasChildren(outputPath)) {
      throw new IOException("The 'output' folder must be empty: " + outputPath);
    }
    if ("clear".equals(existingFolder) || "craete_clear".equals(existingFolder)) {
      clearFolder(outputPath);
    }
  }

  private static boolean hasChildren(Path folder) throws IOException {
    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
      return stream.iterator().hasNext();
    }
  }

  private static void clearFolder(Path folder) throws IOException {
    List<Path> paths;
    try (java.util.stream.Stream<Path> stream = Files.walk(folder)) {
      paths = stream.filter(path -> !path.equals(folder))
          .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }
    for (Path path : paths) {
      Files.delete(path);
    }
  }

  private static List<Path> getOutputFiles(Path outputPath, List<ResourceEntry> resources) throws IOException {
    List<Path> outputFiles = new ArrayList<>(resources.size());
    Path normalizedOutput = outputPath.toAbsolutePath().normalize();
    for (ResourceEntry entry : resources) {
      Path outputFile = outputPath.resolve(entry.getResourceName() + ".xml").normalize();
      if (!normalizedOutput.equals(outputFile.getParent())) {
        throw new IOException("Invalid resource filename: " + entry.getResourceName());
      }
      outputFiles.add(outputFile);
    }
    return outputFiles;
  }

  private static boolean matches(String name, Pattern[] patterns) {
    for (Pattern pattern : patterns) {
      if (pattern != null && pattern.matcher(name).matches()) {
        return true;
      }
    }
    return false;
  }

  private static String structToXml(AbstractStruct struct) throws Exception {
    StringBuilder output = new StringBuilder();
    XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(new StringBuilderWriter(output));
    writer.writeStartDocument("UTF-8", "1.0");
    writeStruct(writer, struct, xmlName(struct.getName()), false, -1);
    writer.writeEndDocument();
    writer.close();
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    StringWriter pretty = new StringWriter();
    transformer.transform(new StreamSource(new StringReader(output.toString())), new StreamResult(pretty));
    return pretty.toString();
  }

  private static void writeStruct(XMLStreamWriter writer, AbstractStruct struct, String elementName,
      boolean repeated, int index) throws Exception {
    writer.writeStartElement(elementName);
    if (repeated) {
      writer.writeAttribute("id", Integer.toString(index));
    }
    List<StructEntry> fields = new ArrayList<>(struct.getFields());
    Collections.sort(fields, Comparator.comparingInt(StructEntry::getOffset));
    Map<String, Integer> counts = new HashMap<>();
    for (StructEntry field : fields) {
      String name = fieldName(field);
      counts.put(name, counts.containsKey(name) ? counts.get(name) + 1 : 1);
    }
    Map<String, Integer> indices = new HashMap<>();
    for (StructEntry field : fields) {
      String name = fieldName(field);
      int fieldIndex = indices.containsKey(name) ? indices.get(name) : 0;
      indices.put(name, fieldIndex + 1);
      writeField(writer, field, counts.get(name) > 1, fieldIndex);
    }
    writer.writeEndElement();
  }

  private static void writeField(XMLStreamWriter writer, StructEntry field, boolean repeated, int index)
      throws Exception {
    String name = xmlName(fieldName(field));
    writer.writeStartElement(name);
    writer.writeAttribute("offset", hex(field.getOffset(), 2));
    writer.writeAttribute("size", Integer.toString(field.getSize()));
    if (repeated) {
      writer.writeAttribute("id", Integer.toString(index));
    }
    if (field instanceof AbstractStruct) {
      writeStructContents(writer, (AbstractStruct) field);
    } else if (field instanceof Flag) {
      Flag flag = (Flag) field;
      writer.writeAttribute("value", hex(flag.getLongValue(), field.getSize() * 2));
      for (int bit = 0; bit < field.getSize() * 8; bit++) {
        if (flag.isFlagSet(bit)) {
          writer.writeStartElement(xmlName(flag.getString(bit) == null ? "Bit" + bit : flag.getString(bit)));
          writer.writeAttribute("bit", Integer.toString(bit));
          writer.writeCharacters("1");
          writer.writeEndElement();
        }
      }
    } else {
      writeScalar(writer, field);
    }
    writer.writeEndElement();
  }

  private static void writeStructContents(XMLStreamWriter writer, AbstractStruct struct) throws Exception {
    List<StructEntry> fields = new ArrayList<>(struct.getFields());
    Collections.sort(fields, Comparator.comparingInt(StructEntry::getOffset));
    Map<String, Integer> counts = new HashMap<>();
    for (StructEntry field : fields) {
      String name = fieldName(field);
      counts.put(name, counts.containsKey(name) ? counts.get(name) + 1 : 1);
    }
    Map<String, Integer> indices = new HashMap<>();
    for (StructEntry field : fields) {
      String name = fieldName(field);
      int index = indices.containsKey(name) ? indices.get(name) : 0;
      indices.put(name, index + 1);
      writeField(writer, field, counts.get(name) > 1, index);
    }
  }

  private static void writeScalar(XMLStreamWriter writer, StructEntry field) throws Exception {
    String text = field.toString();
    if (field instanceof ResourceRef) {
      writer.writeCharacters(((ResourceRef) field).getResourceName());
    } else if (field instanceof ResourceBitmap) {
      ResourceBitmap bitmap = (ResourceBitmap) field;
      writer.writeAttribute("value", hex(bitmap.getLongValue(), field.getSize() * 2));
      ResourceBitmap.RefEntry data = bitmap.getDataOf(bitmap.getLongValue());
      writer.writeCharacters(data == null ? text : data.getResourceName());
    } else if (field instanceof StringRef) {
      StringRef stringRef = (StringRef) field;
      writer.writeAttribute("value", hex(stringRef.getLongValue(), field.getSize() * 2));
      writer.writeCharacters(stringRef.getText());
    } else if (field instanceof AbstractBitmap) {
      AbstractBitmap<?> bitmap = (AbstractBitmap<?>) field;
      writer.writeAttribute("value", hex(bitmap.getLongValue(), field.getSize() * 2));
      Object data = bitmap.getDataOf(bitmap.getLongValue());
      writer.writeCharacters(data == null ? text : data.toString());
    } else if (field instanceof IsNumeric) {
      writer.writeAttribute("value", hex(((IsNumeric) field).getLongValue(), field.getSize() * 2));
      writer.writeCharacters(text);
    } else if (field instanceof IsTextual) {
      writer.writeCharacters(((IsTextual) field).getText());
    } else {
      writer.writeCharacters(text);
    }
  }

  private static String hex(long value, int digits) {
    return String.format(Locale.ROOT, "0x%0" + Math.max(2, digits) + "X", value);
  }

  private static String xmlName(String name) {
    if (name == null || name.isEmpty()) {
      return "Field";
    }
    StringBuilder result = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      result.append((i == 0 && !Character.isJavaIdentifierStart(c))
          || (i > 0 && !Character.isJavaIdentifierPart(c)) ? '_' : c);
    }
    return result.toString();
  }

  private static String fieldName(StructEntry field) {
    String name = field.getName();
    return name == null ? "Field" : name.replaceFirst("[_ ]+[0-9]+$", "");
  }

  private static final class StringBuilderWriter extends java.io.Writer {
    private final StringBuilder builder;

    StringBuilderWriter(StringBuilder builder) {
      this.builder = builder;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
      builder.append(cbuf, off, len);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  }


  private static Path resolvePath(Path workingDirectory, String value) throws IOException {
    if (value == null || value.trim().isEmpty()) {
      throw new IOException("A file path must not be empty.");
    }
    Path path = Paths.get(value);
    return path.isAbsolute() ? path : workingDirectory.resolve(path).normalize();
  }

  private static String extension(Path path) {
    String name = path.getFileName().toString();
    int index = name.lastIndexOf('.');
    return index < 0 ? "" : name.substring(index + 1);
  }
}
