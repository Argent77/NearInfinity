// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.bcs;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.DataMenuItem;
import org.infinity.gui.InfinityScrollPane;
import org.infinity.gui.InfinityTextArea;
import org.infinity.gui.ScriptTextArea;
import org.infinity.gui.ViewFrame;
import org.infinity.icon.Icons;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.TextResource;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.Writeable;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.ScriptReferenceSearcher;
import org.infinity.search.TextResourceSearcher;
import org.infinity.util.IdsMap;
import org.infinity.util.StaticSimpleXorDecryptor;
import org.infinity.util.Misc;
import org.infinity.util.io.StreamUtils;

/**
 * This resource represent scripted actions. {@code .bcs} files are scripts attached
 * to anything other than the player characters. {@code .bs} files are scripts which
 * drive a characters actions automatically (AI scripts) based on criteria in the
 * environment.
 * <p>
 * Just as the files from which they are compiled, these script files make use of
 * the concept of "triggers" and "responses". A trigger is a condition which causes
 * a response with a certain probability. A response is one or more calls to functions
 * they have exposed to the script. It seems that this to be the difference between
 * {@code .bcs} and {@code .bs} files. (They are assigned different resource types
 * in the resource management code, too.)
 * <p>
 * The format will be given in a top-down sense: i.e. first will describe the formatting
 * of top-level blocks, and then will describe the contents of the blocks. The
 * process will proceed recursively until the whole format is described.
 * <p>
 * The top-level block is the script file.
 *
 * <h3>Parameters</h3>
 * First, a brief word on "function parameters". Both {@link BcsTrigger triggers}
 * and {@link BcsAction actions} are essentially calls to functions inside the
 * Infinity Engine. Triggers can take up to 7 arguments, and actions can take up
 * to 10 arguments. There are three allowable forms of arguments: strings, integers,
 * and objects. The different function calls are defined in {@code TRIGGER.IDS}
 * and {@code ACTION.IDS}.
 * <p>
 * There are also functions defined in {@code SVTRIOBJ.IDS}, which are a {sub|super}
 * set of of the function calls defined in {@code TRIGGER.IDS}. They are probably
 * used for {insert spiel here}.
 * <p>
 * String arguments are simply quoted strings (i.e. ASCII strings delimited by
 * the double quote character {@code '"'}). The format of these descriptions is
 * given below, by way of an example (from BG's {@code TRIGGER.IDS}):
 *
 * <code><pre>
 * 0x401D Specifics(O:Object*,I:Specifics*Specific)
 * </pre></code>
 *
 * The first thing on the line is the ID (in hex. IDs in scripts are typically
 * in decimal). Next is the name of the function. Inside the parentheses, similarly
 * to C/C++, is a comma-delimited list of argument type and argument name.
 * <p>
 * The argument types are:
 * <ul>
 * <li>S: string</li>
 * <li>O: object</li>
 * <li>I: integer</li>
 * <li>P: point</li>
 * <li>A: action</li>
 * </ul>
 * There is always a tag after the {@code ':'} and before the {@code '*'}. It seems
 * that the tag is used only for expository function -- i.e. simply an argument name
 * to help discern the purpose. There is, however, one minor complication. Actions
 * only have space for 2 string parameters. There are actions taking anywhere from
 * 0 to 4 strings. Some of the actions which take strings (usually either 2 or 4
 * strings) actually concatenate the strings. In this case, it is always an
 * {@code "Area"} and a {@code "Name"} parameter, (though the parameter names vary
 * somewhat).
 * <p>
 * The only surefire way to tell which is which is to hardcode the values of the
 * actions which concatenate strings. When the strings are concatenated, the
 * {@code "Area"} is always the first part of the resulting string, and always
 * takes exactly 6 characters. It works, in most respects, just like a namespace
 * qualifier in, for instance, C++. An aside: The functions which actually concatenate
 * the strings are typically the ones which access "global variables", i.e.
 * {@code Global}, {@code SetGlobal}, {@code IncrementGlobal}, et cetera.
 * <p>
 * At present there is no confidence how "action" type parameters are stored.
 * This will require some investigation.
 * <p>
 * The final detail in the above example is the bit following the {@code '*'}.
 * This occurs (it seems) only in integer arguments; the string following the
 * asterisk is the name of an {@link IdsMap IDS} file. The values in the IDS file
 * are the only allowed values for that parameter; moreover, it is extremely probable
 * that the parameter can be accessed using the symbolic names given in the IDS file,
 * though this is merely speculation. Each trigger can use up to 2 (3?) integer
 * arguments, up to 2 string arguments, and one object argument. These seven arguments
 * are always specified in each trigger, even if they are not all used. If an argument
 * is not used, it is assigned a dummy value. Finally, the arguments are used in order
 * when they are listed in the scripts. For instance, two integer parameters always
 * occupy the first two "integer parameter" slots in the trigger or action. A trigger
 * has an additional "flags" field, in which, for instance, a bit is either set or
 * cleared to indicate whether the trigger is to be negated or not. (i.e. whether the
 * success of the trigger should return {@code true} or {@code false}).
 *
 * <h3>Script file</h3>
 * <code><pre>
 * SC (newline)
 * Condition-response block
 * Condition-response block
 * ...
 * Condition-response block
 * SC (newline)
 * </pre></code>
 *
 * <h3>Condition-response block</h3>
 * This can be interpreted as "if condition, then response set". A response set
 * is a set of actions, each of which is performed with a certain probability:
 * <code><pre>
 * CR (newline)
 * Condition
 * Response set
 * CR (newline)
 * </pre></code>
 *
 * <h3>Condition</h3>
 * This should be interpreted as the AND of all the "trigger" conditions. The
 * condition is {@code true} iff all triggers inside are {@code true}:
 * <code><pre>
 * CO (newline)
 * Trigger
 * Trigger
 * ...
 * Trigger
 * CO (newline)
 * </pre></code>
 *
 * <h3>Trigger</h3>
 * This format is slightly hairier. First, it has a "trigger ID", which is an ID
 * in the {@code TRIGGER.IDS} file. Essentially, each trigger corresponds to a call
 * to one of the functions listed in there. See the section on parameters for details:
 * <code><pre>
 * TR (newline)
 * trigger ID from TRIGGER.IDS (no newline)
 * 1 integer parameter (no newline)
 * 1 flags dword (no newline):
 *    bit 0: negate condition flag (if true, this trigger is negated -- i.e. success=>false, failure=>true)
 * 1 integer parameter (no newline)
 * 1 integer. Unknown purpose.
 * 2 string parameters (no newline)
 * 1 object parameter (newline)
 * TR (newline)
 * </pre></code>
 *
 * <h3>Response set</h3>
 * Each response in a reponse set has a certain weight associated with it. Usually,
 * this is 100%, but if not, then the response is only played with a certain probability.
 * To find the chance of a particular response being chosen, sum the probabilities of
 * all the responses in the response set. Each response Rnhas a probability {@code Pn/Sum(P)}
 * of being chosen, if the response set is to be played:
 * <code><pre>
 * RS (newline)
 * Response
 * Response
 * ...
 * Response
 * </pre></code>
 *
 * <h3>Response</h3>
 * A response is simply the concatenation of a probability and an ACTION:
 * <code><pre>
 * RE (newline)
 * weight. i.e. how likely this response is to occur, given that the response
 *         set is to be run (no newline -- often no whitespace, though that may
 *         not be important).
 * action (newline)
 * RE (newline)
 * </pre></code>
 *
 * <h3>Action</h3>
 * This format is slightly hairier. First, it has a "action ID", which is an ID
 * in the {@code action.IDS} file. Essentially, each action corresponds to a call
 * to one of the functions listed in there. See the section on parameters for details:
 * <code><pre>
 * AC (newline)
 * action ID from ACTION.IDS (no newline)
 * 3 object parameters (newlines after each)
 * 1 integer parameters (no newline)
 * 1 point parameter (formatted as two integers x y) (no newline)
 * 2 integer parameters (no newline)
 * 2 string parameters (no newline)
 * AC (newline)
 * </pre></code>
 *
 * <h3>Object</h3>
 * Objects represent things (i.e. characters) in the game. An object has several
 * parameters. These parameters have enumerated values which can be looked up in
 * {@code .IDS} files. Planescape: Torment has more parameters than BG did:
 * <ul>
 * <li>{@code TEAM} (Planescape: Torment only).
 * <p>Every object in Torment can have a TEAM. Most objects do not have a team specified</li>
 * <li>{@code FACTION} (Planescape: Torment only).
 * <p>Every object in Torment can belong to a FACTION. Most creatures do, in fact,
 * belong to a faction</li>
 * <li>{@code EA} (Enemy-Ally).
 * <p>Whether the character is friendly to your party. Values include {@code "INANIMATE" (=1)},
 * for inanimate objects, {@code "PC" (=2)} for characters belonging to the player,
 * {@code "CHARMED" (=6)} for characters who have been charmed, and hence are under
 * friendly control, or {@code "ENEMY" (=255)} for characters who are hostile towards
 * the character. Two special values of EA exist: {@code "GOODCUTOFF" (=30)} and
 * {@code "EVILCUTOFF" (=200)}. Characters who are below the good cutoff are always
 * hostile towards characters over the evil cutoff, and vice versa. To this end, you
 * can use {@code GOODCUTOFF} and {@code EVILCUTOFF} as sort of "wildcards".
 * {@code EVILCUTOFF} specifies all characters who are "evil", and {@code GOODCUTOFF}
 * specifies all characters who are "good". Note that this has little to do with the
 * alignment</li>
 * <li>{@code GENERAL}
 * <p>The general type of an object. This includes {@code "HUMANOID"}, {@code "UNDEAD"},
 * {@code "ANIMAL"} et cetera, but also {@code "HELMET"}, {@code "KEY"}, {@code "POTION"},
 * {@code "GLOVES"}, et cetera</li>
 * <li>{@code RACE}
 * <p>The race of an object. Creatures obviously have a race, but items also have "race",
 * which can include a slightly more specific description of the type of item than was
 * given in the {@code GENERAL} field. For instance, for armor items, this includes the
 * "type" of the armor -- leather, chain, plate, etc.</li>
 * <li>{@code CLASS}
 * <p>The class of a creature or item. Again, the class notion makes more sense for
 * creatures, but gives some information about the specific type of an item. For a
 * "sword" or "bow", for instance, the class can be {@code "LONG_SWORD"} or {@code
 * "LONG_BOW"}. As another example, the different types of spiders (phase, wraith, etc)
 * are differentiated by the class field</li>
 * <li>{@code SPECIFIC}
 * <p>The specific type of an item. BG only defines three specific types: {@code NORMAL},
 * {@code MAGIC}, and {@code NO_MAGIC}. Dunno. Torment uses this field much more
 * extensively to differentiate precise individuals matching a description from
 * everyone else</li>
 * <li>{@code GENDER}
 * <p>Gender field. There are, mind-boggling as it may seem, five possible values for
 * this in BG, including the expected {@code MALE} and {@code FEMALE}. There's also
 * {@code "NIETHER"} (sic), which seems presume was meant as {@code NEITHER}. Finally,
 * there are {@code OTHER} and {@code BOTH}</li>
 * <li>{@code ALIGNMENT}
 * <p>This field is fairly obvious. The high nybble is the Chaotic-Lawful axis, and
 * the low nybble is the Good-Evil axis. The values for this are specified in
 * {@code ALIGNMEN.IDS}. The only nuance to this is that there are several values
 * {@code MASK_CHAOTIC}, {@code MASK_LCNEUTRAL}, [@code MASK_LAWFUL}, {@code MASK_EVIL},
 * {@code MASK_GENEUTRAL}, {@code MASK_GOOD} which are wildcards, meaning, respectively,
 * all chaotic objects, all neutral (on the lawful-chaotic axis) objects, all lawful
 * objects, all evil objects, all neutral (good-evil axis) objects, and all good objects</li>
 * <li>{@code IDENTIFIERS}
 * <p>The 5 identifiers for an object allow functional specification of an object
 * ({@code LastAttackedBy(Myself)}, etc.). These values are looked up in {@code OBJECT.IDS}.
 * Any unused bytes are set to 0.
 * <li>{@code NAME}
 * <p>This is a string parameter, and is only used for characters who have specific
 * names. Objects can be looked up by name, or referenced by name in scripts.
 * That is the purpose of this field.
 * </ul>
 * The specific format of an object is as follows:
 * <code><pre>
 * OB (newline)
 * integer: enemy-ally field (EA.IDS)
 * integer: (torment only) faction (FACTION.IDS)
 * integer: (torment only) team (TEAM.IDS)
 * integer: general (GENERAL.IDS)
 * integer: race (RACE.IDS)
 * integer: class (CLASS.IDS)
 * integer: specific (SPECIFIC.IDS)
 * integer: gender (GENDER.IDS)
 * integer: alignment (ALIGNMEN.IDS)
 * integer: identifiers (OBJECT.IDS)
 * (Not in BG1) object coordinates
 * string: name
 * OB (newline)
 * </pre></code>
 * Object coordinates must be specified as a point. Coordinate values which are -1
 * indicate that the specified part of the coordinate is not used.
 * A point is represented as:
 * <code><pre>
 * [x.y]
 * </pre></code>
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/bcs.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/bcs.htm</a>
 */
public final class BcsResource implements TextResource, Writeable, Closeable, ActionListener, ItemListener,
                                          DocumentListener
{
  // for decompile panel
  private static final ButtonPanel.Control CtrlCompile    = ButtonPanel.Control.CUSTOM_1;
  private static final ButtonPanel.Control CtrlErrors     = ButtonPanel.Control.CUSTOM_2;
  private static final ButtonPanel.Control CtrlWarnings   = ButtonPanel.Control.CUSTOM_3;
  // for compiled panel
  private static final ButtonPanel.Control CtrlDecompile  = ButtonPanel.Control.CUSTOM_1;
  // for button panel
  private static final ButtonPanel.Control CtrlUses       = ButtonPanel.Control.CUSTOM_1;

  private static JFileChooser chooser;
  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();
  private final ButtonPanel bpDecompile = new ButtonPanel();
  private final ButtonPanel bpCompiled = new ButtonPanel();

  private JMenuItem ifindall, ifindthis, ifindusage, iexportsource, iexportscript;
  private JPanel panel;
  private JTabbedPane tabbedPane;
  private InfinityTextArea codeText;
  private ScriptTextArea sourceText;
  private String text;
  private boolean sourceChanged = false, codeChanged = false;

  public BcsResource(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
    ByteBuffer buffer = entry.getResourceBuffer();
    if (buffer.limit() > 1 && buffer.getShort(0) == -1) {
      buffer = StaticSimpleXorDecryptor.decrypt(buffer, 2);
    }
    text = StreamUtils.readString(buffer, buffer.limit(),
                                  Charset.forName(BrowserMenuBar.getInstance().getSelectedCharset()));
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (bpDecompile.getControlByType(CtrlCompile) == event.getSource()) {
      compile();
    } else if (bpCompiled.getControlByType(CtrlDecompile) == event.getSource()) {
      decompile();
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.SAVE) == event.getSource()) {
      save();
    }
  }

// --------------------- End Interface ActionListener ---------------------


// --------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception
  {
    if (sourceChanged) {
      String options[] = {"Compile & save", "Discard changes", "Cancel"};
      int result = JOptionPane.showOptionDialog(panel, "Script contains uncompiled changes", "Uncompiled changes",
                                                JOptionPane.YES_NO_CANCEL_OPTION,
                                                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
      if (result == JOptionPane.YES_OPTION) {
        ((JButton)bpDecompile.getControlByType(CtrlCompile)).doClick();
        if (bpDecompile.getControlByType(CtrlErrors).isEnabled()) {
          throw new Exception("Save aborted");
        }
        ResourceFactory.saveResource(this, panel.getTopLevelAncestor());
      } else if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION)
        throw new Exception("Save aborted");
    } else if (codeChanged) {
      ResourceFactory.closeResource(this, entry, panel);
    }
  }

// --------------------- End Interface Closeable ---------------------


// --------------------- Begin Interface DocumentListener ---------------------

  @Override
  public void insertUpdate(DocumentEvent event)
  {
    if (event.getDocument() == codeText.getDocument()) {
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(true);
      bpCompiled.getControlByType(CtrlDecompile).setEnabled(true);
      sourceChanged = false;
      codeChanged = true;
    }
    else if (event.getDocument() == sourceText.getDocument()) {
      bpDecompile.getControlByType(CtrlCompile).setEnabled(true);
      sourceChanged = true;
    }
  }

  @Override
  public void removeUpdate(DocumentEvent event)
  {
    if (event.getDocument() == codeText.getDocument()) {
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(true);
      bpCompiled.getControlByType(CtrlDecompile).setEnabled(true);
      sourceChanged = false;
      codeChanged = true;
    }
    else if (event.getDocument() == sourceText.getDocument()) {
      bpDecompile.getControlByType(CtrlCompile).setEnabled(true);
      sourceChanged = true;
    }
  }

  @Override
  public void changedUpdate(DocumentEvent event)
  {
    if (event.getDocument() == codeText.getDocument()) {
      buttonPanel.getControlByType(ButtonPanel.Control.SAVE).setEnabled(true);
      bpCompiled.getControlByType(CtrlDecompile).setEnabled(true);
      sourceChanged = false;
      codeChanged = true;
    }
    else if (event.getDocument() == sourceText.getDocument()) {
      bpDecompile.getControlByType(CtrlCompile).setEnabled(true);
      sourceChanged = true;
    }
  }

// --------------------- End Interface DocumentListener ---------------------


// --------------------- Begin Interface ItemListener ---------------------

  @Override
  public void itemStateChanged(ItemEvent event)
  {
    if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_MENU) == event.getSource()) {
      ButtonPopupMenu bpmFind = (ButtonPopupMenu)event.getSource();
      if (bpmFind.getSelectedItem() == ifindall) {
        List<ResourceEntry> files = ResourceFactory.getResources("BCS");
        files.addAll(ResourceFactory.getResources("BS"));
        new TextResourceSearcher(files, panel.getTopLevelAncestor());
      } else if (bpmFind.getSelectedItem() == ifindthis) {
        List<ResourceEntry> files = new ArrayList<ResourceEntry>(1);
        files.add(entry);
        new TextResourceSearcher(files, panel.getTopLevelAncestor());
      } else if (bpmFind.getSelectedItem() == ifindusage)
        new ScriptReferenceSearcher(entry, panel.getTopLevelAncestor());
    } else if (buttonPanel.getControlByType(CtrlUses) == event.getSource()) {
      ButtonPopupMenu bpmUses = (ButtonPopupMenu)event.getSource();
      JMenuItem item = bpmUses.getSelectedItem();
      String name = item.getText();
      int index = name.indexOf(" (");
      if (index != -1) {
        name = name.substring(0, index);
      }
      ResourceEntry resEntry = ResourceFactory.getResourceEntry(name);
      new ViewFrame(panel.getTopLevelAncestor(), ResourceFactory.getResource(resEntry));
    } else if (buttonPanel.getControlByType(ButtonPanel.Control.EXPORT_MENU) == event.getSource()) {
      ButtonPopupMenu bpmExport = (ButtonPopupMenu)event.getSource();
      if (bpmExport.getSelectedItem() == iexportsource) {
        if (chooser == null) {
          chooser = new JFileChooser(Profile.getGameRoot().toFile());
          chooser.setDialogTitle("Export source");
          chooser.setFileFilter(new FileFilter()
          {
            @Override
            public boolean accept(File pathname)
            {
              return pathname.isDirectory() || pathname.getName().toLowerCase(Locale.ENGLISH).endsWith(".baf");
            }

            @Override
            public String getDescription()
            {
              return "Infinity script (.BAF)";
            }
          });
        }
        chooser.setSelectedFile(new File(StreamUtils.replaceFileExtension(entry.getResourceName(), "BAF")));
        int returnval = chooser.showSaveDialog(panel.getTopLevelAncestor());
        if (returnval == JFileChooser.APPROVE_OPTION) {
          try (BufferedWriter bw =
              Files.newBufferedWriter(chooser.getSelectedFile().toPath(),
                                      Charset.forName(BrowserMenuBar.getInstance().getSelectedCharset()))) {
            bw.write(sourceText.getText().replaceAll("\r?\n", Misc.LINE_SEPARATOR));
            JOptionPane.showMessageDialog(panel, "File saved to \"" + chooser.getSelectedFile().toString() +
                                                 '\"', "Export complete", JOptionPane.INFORMATION_MESSAGE);
          } catch (IOException e) {
            JOptionPane.showMessageDialog(panel, "Error exporting " + chooser.getSelectedFile().toString(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
          }
        }
      } else if (bpmExport.getSelectedItem() == iexportscript) {
        ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
      }
    } else if (bpDecompile.getControlByType(CtrlErrors) == event.getSource()) {
      ButtonPopupMenu bpmErrors = (ButtonPopupMenu)event.getSource();
      String selected = bpmErrors.getSelectedItem().getText();
      int linenr = Integer.parseInt(selected.substring(0, selected.indexOf(": ")));
      highlightText(linenr, null);
    } else if (bpDecompile.getControlByType(CtrlWarnings) == event.getSource()) {
      ButtonPopupMenu bpmWarnings = (ButtonPopupMenu)event.getSource();
      String selected = bpmWarnings.getSelectedItem().getText();
      int linenr = Integer.parseInt(selected.substring(0, selected.indexOf(": ")));
      highlightText(linenr, null);
    }
  }

// --------------------- End Interface ItemListener ---------------------


// --------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry()
  {
    return entry;
  }

// --------------------- End Interface Resource ---------------------


// --------------------- Begin Interface TextResource ---------------------

  @Override
  public String getText()
  {
    if (sourceText != null) {
      return sourceText.getText();
    }
    Decompiler decompiler = new Decompiler(text, false);
    decompiler.setGenerateComments(BrowserMenuBar.getInstance().autogenBCSComments());
    try {
      return decompiler.getSource();
    } catch (Exception e) {
      e.printStackTrace();
      return "// Error: " + e.getMessage();
    }
  }

  @Override
  public void highlightText(int linenr, String highlightText)
  {
    try {
      int startOfs = sourceText.getLineStartOffset(linenr - 1);
      int endOfs = sourceText.getLineEndOffset(linenr - 1);
      if (highlightText != null) {
        String text = sourceText.getText(startOfs, endOfs - startOfs);
        Pattern p = Pattern.compile(highlightText, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
          startOfs += m.start();
          endOfs = startOfs + m.end() + 1;
        }
      }
      highlightText(startOfs, endOfs);
    } catch (BadLocationException ble) {
    }
  }

  @Override
  public void highlightText(int startOfs, int endOfs)
  {
    try {
      sourceText.setCaretPosition(startOfs);
      sourceText.moveCaretPosition(endOfs - 1);
      sourceText.getCaret().setSelectionVisible(true);
    } catch (IllegalArgumentException e) {
    }
  }

// --------------------- End Interface TextResource ---------------------


// --------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    sourceText = new ScriptTextArea();
    sourceText.setAutoIndentEnabled(BrowserMenuBar.getInstance().getBcsAutoIndentEnabled());
    sourceText.addCaretListener(container.getStatusBar());
    sourceText.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
    sourceText.setMargin(new Insets(3, 3, 3, 3));
    sourceText.setLineWrap(false);
    sourceText.getDocument().addDocumentListener(this);
    InfinityScrollPane scrollDecompiled = new InfinityScrollPane(sourceText, true);
    scrollDecompiled.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlDkShadow")));

    JButton bCompile = new JButton("Compile", Icons.getIcon(Icons.ICON_REDO_16));
    bCompile.setMnemonic('c');
    bCompile.addActionListener(this);
    ButtonPopupMenu bpmErrors = new ButtonPopupMenu("Errors (0)...", new JMenuItem[0], 20);
    bpmErrors.setIcon(Icons.getIcon(Icons.ICON_UP_16));
    bpmErrors.addItemListener(this);
    bpmErrors.setEnabled(false);
    ButtonPopupMenu bpmWarnings = new ButtonPopupMenu("Warnings (0)...", new JMenuItem[0], 20);
    bpmWarnings.setIcon(Icons.getIcon(Icons.ICON_UP_16));
    bpmWarnings.addItemListener(this);
    bpmWarnings.setEnabled(false);
    bpDecompile.addControl(bCompile, CtrlCompile);
    bpDecompile.addControl(bpmErrors, CtrlErrors);
    bpDecompile.addControl(bpmWarnings, CtrlWarnings);

    JPanel decompiledPanel = new JPanel(new BorderLayout());
    decompiledPanel.add(scrollDecompiled, BorderLayout.CENTER);
    decompiledPanel.add(bpDecompile, BorderLayout.SOUTH);

    codeText = new InfinityTextArea(text, true);
    codeText.setFont(Misc.getScaledFont(BrowserMenuBar.getInstance().getScriptFont()));
    codeText.setMargin(new Insets(3, 3, 3, 3));
    codeText.setCaretPosition(0);
    codeText.setLineWrap(false);
    codeText.getDocument().addDocumentListener(this);
    InfinityScrollPane scrollCompiled = new InfinityScrollPane(codeText, true);
    scrollCompiled.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlDkShadow")));

    JButton bDecompile = new JButton("Decompile", Icons.getIcon(Icons.ICON_UNDO_16));
    bDecompile.setMnemonic('d');
    bDecompile.addActionListener(this);
    bDecompile.setEnabled(false);
    bpCompiled.addControl(bDecompile, CtrlDecompile);

    JPanel compiledPanel = new JPanel(new BorderLayout());
    compiledPanel.add(scrollCompiled, BorderLayout.CENTER);
    compiledPanel.add(bpCompiled, BorderLayout.SOUTH);

    ifindall = new JMenuItem("in all scripts");
    ifindthis = new JMenuItem("in this script only");
    ifindusage = new JMenuItem("references to this script");
    ButtonPopupMenu bpmFind = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.FIND_MENU);
    bpmFind.setMenuItems(new JMenuItem[]{ifindall, ifindthis, ifindusage});
    bpmFind.addItemListener(this);
    ButtonPopupMenu bpmUses = new ButtonPopupMenu("Uses...", new JMenuItem[]{});
    bpmUses.setIcon(Icons.getIcon(Icons.ICON_FIND_16));
    bpmUses.addItemListener(this);
    buttonPanel.addControl(bpmUses, CtrlUses);
    iexportscript = new JMenuItem("script code");
    iexportsource = new JMenuItem("script source");
    iexportscript.setToolTipText("NB! Will export last *saved* version");
    ButtonPopupMenu bpmExport = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.EXPORT_MENU);
    bpmExport.setMenuItems(new JMenuItem[]{iexportscript, iexportsource});
    bpmExport.addItemListener(this);
    JButton bSave = (JButton)buttonPanel.addControl(ButtonPanel.Control.SAVE);
    bSave.addActionListener(this);
    bSave.setEnabled(false);

    tabbedPane = new JTabbedPane();
    tabbedPane.addTab("Script source (decompiled)", decompiledPanel);
    tabbedPane.addTab("Script code", compiledPanel);

    panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(tabbedPane, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);

    decompile();
    if (BrowserMenuBar.getInstance().autocheckBCS()) {
      compile();
      codeChanged = false;
    }
    else {
      bCompile.setEnabled(true);
      bpmErrors.setEnabled(false);
      bpmWarnings.setEnabled(false);
    }
    bDecompile.setEnabled(false);
    bSave.setEnabled(false);

    return panel;
  }

// --------------------- End Interface Viewable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    if (codeText == null) {
      StreamUtils.writeString(os, text, text.length());
    } else {
      StreamUtils.writeString(os, codeText.getText(), codeText.getText().length());
    }
  }

// --------------------- End Interface Writeable ---------------------

  public String getCode()
  {
    return text;
  }

  public void insertString(String s)
  {
    int pos = sourceText.getCaret().getDot();
    sourceText.insert(s, pos);
  }

  private void compile()
  {
    JButton bCompile = (JButton)bpDecompile.getControlByType(CtrlCompile);
    JButton bDecompile = (JButton)bpCompiled.getControlByType(CtrlDecompile);
    ButtonPopupMenu bpmErrors = (ButtonPopupMenu)bpDecompile.getControlByType(CtrlErrors);
    ButtonPopupMenu bpmWarnings = (ButtonPopupMenu)bpDecompile.getControlByType(CtrlWarnings);
    Compiler compiler = new Compiler(sourceText.getText());
    codeText.setText(compiler.getCode());
    codeText.setCaretPosition(0);
    bCompile.setEnabled(false);
    bDecompile.setEnabled(false);
    sourceChanged = false;
    codeChanged = true;
    iexportscript.setEnabled(compiler.getErrors().size() == 0);
    SortedSet<ScriptMessage> errorMap = compiler.getErrors();
    SortedSet<ScriptMessage> warningMap = compiler.getWarnings();
    sourceText.clearGutterIcons();
    bpmErrors.setText("Errors (" + errorMap.size() + ")...");
    bpmWarnings.setText("Warnings (" + warningMap.size() + ")...");
    if (errorMap.size() == 0) {
      bpmErrors.setEnabled(false);
    } else {
      JMenuItem errorItems[] = new JMenuItem[errorMap.size()];
      int counter = 0;
      for (final ScriptMessage sm: errorMap) {
        sourceText.setLineError(sm.getLine(), sm.getMessage(), false);
        errorItems[counter++] = new DataMenuItem(sm.getLine() + ": " + sm.getMessage(), null, sm);
      }
      bpmErrors.setMenuItems(errorItems, false);
      bpmErrors.setEnabled(true);
    }
    if (warningMap.size() == 0) {
      bpmWarnings.setEnabled(false);
    } else {
      JMenuItem warningItems[] = new JMenuItem[warningMap.size()];
      int counter = 0;
      for (final ScriptMessage sm: warningMap) {
        sourceText.setLineWarning(sm.getLine(), sm.getMessage(), false);
        warningItems[counter++] = new DataMenuItem(sm.getLine() + ": " + sm.getMessage(), null, sm);
      }
      bpmWarnings.setMenuItems(warningItems, false);
      bpmWarnings.setEnabled(true);
    }
  }

  private void decompile()
  {
    JButton bDecompile = (JButton)bpDecompile.getControlByType(CtrlDecompile);
    JButton bCompile = (JButton)bpDecompile.getControlByType(CtrlCompile);
    ButtonPopupMenu bpmUses = (ButtonPopupMenu)buttonPanel.getControlByType(CtrlUses);

    Decompiler decompiler = new Decompiler(codeText.getText(), true);
    decompiler.setGenerateComments(BrowserMenuBar.getInstance().autogenBCSComments());
    try {
      sourceText.setText(decompiler.getSource());
    } catch (Exception e) {
      e.printStackTrace();
      sourceText.setText("/*\nError: " + e.getMessage() + "\n*/");
    }
    sourceText.setCaretPosition(0);
    Set<ResourceEntry> uses = decompiler.getResourcesUsed();
    JMenuItem usesItems[] = new JMenuItem[uses.size()];
    int usesIndex = 0;
    for (final ResourceEntry usesEntry : uses) {
      if (usesEntry.getSearchString() != null) {
        usesItems[usesIndex++] =
        new JMenuItem(usesEntry.getResourceName() + " (" + usesEntry.getSearchString() + ')');
      } else {
        usesItems[usesIndex++] = new JMenuItem(usesEntry.toString());
      }
    }
    bpmUses.setMenuItems(usesItems);
    bpmUses.setEnabled(usesItems.length > 0);
    bCompile.setEnabled(false);
    bDecompile.setEnabled(false);
    sourceChanged = false;
    tabbedPane.setSelectedIndex(0);
  }

  private void save()
  {
    JButton bSave = (JButton)buttonPanel.getControlByType(ButtonPanel.Control.SAVE);
    ButtonPopupMenu bpmErrors = (ButtonPopupMenu)bpDecompile.getControlByType(CtrlErrors);
    if (bpmErrors.isEnabled()) {
      String options[] = {"Save", "Cancel"};
      int result = JOptionPane.showOptionDialog(panel, "Script contains errors. Save anyway?", "Errors found",
                                                JOptionPane.YES_NO_OPTION,
                                                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
      if (result != 0) {
        return;
      }
    }
    if (ResourceFactory.saveResource(this, panel.getTopLevelAncestor())) {
      bSave.setEnabled(false);
      sourceChanged = false;
      codeChanged = false;
    }
  }
}
