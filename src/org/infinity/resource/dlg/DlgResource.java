// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.infinity.NearInfinity;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.TextString;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasAddRemovable;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.updater.Utils;
import org.infinity.util.StringTable;
import org.infinity.util.io.StreamUtils;

/**
 * DLG resource contains the structure of conversation, in what is effectievly
 * a state machine. Dialogs contains string references into the {@link StringTable TLK}
 * file that make up the actual words of the conversation. Dialogs bear similarities
 * to scripts; each {@link State state} may have a series of {@link StateTrigger trigger}
 * conditions, and effect a series of {@link Action actions}. If the any of the
 * triggers for a state evaluate to {@code false}, the state is skipped and the
 * triggers in the next state are evaluated - this occurs when entering into
 * a dialog state, and when presenting a list of {@link Transition responses}.
 *
 * <code><pre>
 * state 0:
 *    trigger: NumTimesTalkedTo(0)
 *    Text: "Hello, sailor!"
 *
 * state 1:
 *    trigger: NumTimesTalkedToGT(5)
 *    Text: "Go away, already!"
 *
 * state 2:
 *    Text: "Hail and well met, yada yada yada."
 * </pre></code>
 *
 * Dialog always attempt to start at state 0. The first time this sample dialog is
 * entered the trigger in state 0 is {@code true}, hence the character responds
 * {@code "Hello, sailor!"}. Subsequent times the dialog is entered the trigger
 * in state 0 will be {@code false}, and state 1 is evaluated - this trigger also
 * fails and so state 2 is evaluated. This state evaluates {@code true}, and get
 * the associated message is displayed.
 * <p>
 * If the dialog is initiaed five or more times, the trigger in state 1 will evaluate
 * to {@code true} and the message associated with that state will be displayed.
 * <p>
 * In addition to the triggers outlined above, states present a list of responses
 * (aka {@link Transition transitions}). Each response may have a series of behaviours
 * associated with it; the response text, a journal entry or an {@link Action action}.
 *
 * @see <a href="https://gibberlings3.github.io/iesdp/file_formats/ie_formats/dlg_v1.htm">
 * https://gibberlings3.github.io/iesdp/file_formats/ie_formats/dlg_v1.htm</a>
 */
public final class DlgResource extends AbstractStruct
    implements Resource, HasAddRemovable, HasViewerTabs, ActionListener
{
  // DLG-specific field labels
  public static final String DLG_OFFSET_STATES            = "States offset";
  public static final String DLG_OFFSET_RESPONSES         = "Responses offset";
  public static final String DLG_OFFSET_STATE_TRIGGERS    = "State triggers offset";
  public static final String DLG_OFFSET_RESPONSE_TRIGGERS = "Response triggers offset";
  public static final String DLG_OFFSET_ACTIONS           = "Actions offset";
  public static final String DLG_NUM_STATES               = "# states";
  public static final String DLG_NUM_RESPONSES            = "# responses";
  public static final String DLG_NUM_STATE_TRIGGERS       = "# state triggers";
  public static final String DLG_NUM_RESPONSE_TRIGGERS    = "# response triggers";
  public static final String DLG_NUM_ACTIONS              = "# actions";
  public static final String DLG_THREAT_RESPONSE          = "Threat response";

  private static final String TAB_TREE  = "Tree";
  public static final String s_NonInt[] = {"Pausing dialogue", "Turn hostile",
                                           "Escape area", "Ignore attack"};
  private SectionCount countState, countTrans, countStaTri, countTranTri, countAction;
  private SectionOffset offsetState, offsetTrans, offsetStaTri, offsetTranTri, offsetAction;
  private JMenuItem miExport, miExportWeiDUDialog;
  private Viewer detailViewer;
  private TreeViewer treeViewer;
  private StructHexViewer hexViewer;

  public DlgResource(ResourceEntry entry) throws Exception
  {
    super(entry);
  }

// --------------------- Begin Interface HasAddRemovable ---------------------

  @Override
  public AddRemovable[] getAddRemovables() throws Exception
  {
    return new AddRemovable[]{new State(), new Transition(), new StateTrigger(),
                              new ResponseTrigger(), new Action()};
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception
  {
    return entry;
  }

  @Override
  public boolean confirmRemoveEntry(AddRemovable entry) throws Exception
  {
    return true;
  }

// --------------------- End Interface HasAddRemovable ---------------------


// --------------------- Begin Interface HasViewerTabs ---------------------

  @Override
  public int getViewerTabCount()
  {
    return 3;
  }

  @Override
  public String getViewerTabName(int index)
  {
    switch (index) {
      case 0: return StructViewer.TAB_VIEW;
      case 1: return TAB_TREE;
      case 2: return StructViewer.TAB_RAW;
    }
    return null;
  }

  @Override
  public JComponent getViewerTab(int index)
  {
    switch (index) {
      case 0:
        if (detailViewer == null)
          detailViewer = new Viewer(this);
        return detailViewer;
      case 1:
        if (treeViewer == null)
          treeViewer = new TreeViewer(this);
        return treeViewer;
      case 2:
        if (hexViewer == null) {
          hexViewer = new StructHexViewer(this, new BasicColorMap(this, true));
        }
        return hexViewer;
    }
    return null;
  }

  @Override
  public boolean viewerTabAddedBefore(int index)
  {
    return (index == 0);
  }

// --------------------- End Interface HasViewerTabs ---------------------

// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    offsetState.setValue(0x30);
    if (getFieldCount() >= 13 && getField(12).getName().equalsIgnoreCase(DLG_THREAT_RESPONSE))
      offsetState.setValue(0x34);
    offsetTrans.setValue(offsetState.getValue() + 0x10 * countState.getValue());
    offsetStaTri.setValue(offsetTrans.getValue() + 0x20 * countTrans.getValue());
    offsetTranTri.setValue(offsetStaTri.getValue() + 0x8 * countStaTri.getValue());
    offsetAction.setValue(offsetTranTri.getValue() + 0x8 * countTranTri.getValue());
    int stringoff = offsetAction.getValue() + 0x8 * countAction.getValue();
    for (int i = 0; i < getFieldCount(); i++) {
      Object o = getField(i);
      if (o instanceof AbstractCode) {
        stringoff += ((AbstractCode)o).updateOffset(stringoff);
      }
    }
    super.write(os);

    for (int i = 0; i < getFieldCount(); i++) {
      Object o = getField(i);
      if (o instanceof AbstractCode) {
        ((AbstractCode)o).writeString(os);
      }
    }
  }

// --------------------- End Interface Writeable ---------------------

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == miExport) {
      ResourceFactory.exportResource(getResourceEntry(), getViewer().getTopLevelAncestor());
    } else if (e.getSource() == miExportWeiDUDialog) {
      final String fileName = StreamUtils.replaceFileExtension(getResourceEntry().getResourceName(), "D");
      final Path path = ResourceFactory.getExportFileDialog(getViewer().getTopLevelAncestor(), fileName, false);
      if (path != null) {
        File file = path.toFile();
        try (PrintWriter writer = new PrintWriter(file, BrowserMenuBar.getInstance().getSelectedCharset())) {
          if (!exportDlgAsText(writer)) {
            throw new Exception();
          }
        } catch (Exception ex) {
          ex.printStackTrace();
          JOptionPane.showMessageDialog(getViewer().getTopLevelAncestor(),
                                        "Could not export resource into WeiDU dialog format.",
                                        "Error", JOptionPane.ERROR_MESSAGE);
          return;
        }
        JOptionPane.showMessageDialog(getViewer().getTopLevelAncestor(), "File exported to " + file,
                                      "Export complete", JOptionPane.INFORMATION_MESSAGE);
      }
    }
  }

// --------------------- End Interface ActionListener ---------------------

  @Override
  protected void viewerInitialized(StructViewer viewer)
  {
    // replacing original Export button with button menu
    final ButtonPanel panel = viewer.getButtonPanel();

    final JButton b = (JButton)panel.getControlByType(ButtonPanel.Control.EXPORT_BUTTON);
    if (b != null) {
      for (final ActionListener l: b.getActionListeners()) {
        b.removeActionListener(l);
      }
      int position = panel.getControlPosition(b);
      panel.removeControl(position);

      ButtonPopupMenu bpmExport = new ButtonPopupMenu(b.getText());
      bpmExport.setIcon(b.getIcon());

      miExport = new JMenuItem("as DLG file");
      miExport.addActionListener(this);
      miExportWeiDUDialog = new JMenuItem("as WeiDU dialog file");
      miExportWeiDUDialog.addActionListener(this);
      bpmExport.setMenuItems(new JMenuItem[]{miExport, miExportWeiDUDialog}, false);
      panel.addControl(position, bpmExport);
    }
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype)
  {
    updateReferences(datatype, true);
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype)
  {
    updateReferences(datatype, false);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception
  {
    addField(new TextString(buffer, offset, 4, COMMON_SIGNATURE));
    TextString version = new TextString(buffer, offset + 4, 4, COMMON_VERSION);
    addField(version);
    if (!version.toString().equalsIgnoreCase("V1.0")) {
      clearFields();
      throw new Exception("Unsupported version: " + version);
    }

    countState = new SectionCount(buffer, offset + 8, 4, DLG_NUM_STATES, State.class);
    addField(countState);
    offsetState = new SectionOffset(buffer, offset + 12, DLG_OFFSET_STATES, State.class);
    addField(offsetState);
    countTrans = new SectionCount(buffer, offset + 16, 4, DLG_NUM_RESPONSES, Transition.class);
    addField(countTrans);
    offsetTrans = new SectionOffset(buffer, offset + 20, DLG_OFFSET_RESPONSES, Transition.class);
    addField(offsetTrans);
    offsetStaTri = new SectionOffset(buffer, offset + 24, DLG_OFFSET_STATE_TRIGGERS, StateTrigger.class);
    addField(offsetStaTri);
    countStaTri = new SectionCount(buffer, offset + 28, 4, DLG_NUM_STATE_TRIGGERS, StateTrigger.class);
    addField(countStaTri);
    offsetTranTri = new SectionOffset(buffer, offset + 32, DLG_OFFSET_RESPONSE_TRIGGERS, ResponseTrigger.class);
    addField(offsetTranTri);
    countTranTri = new SectionCount(buffer, offset + 36, 4, DLG_NUM_RESPONSE_TRIGGERS, ResponseTrigger.class);
    addField(countTranTri);
    offsetAction = new SectionOffset(buffer, offset + 40, DLG_OFFSET_ACTIONS, org.infinity.resource.dlg.Action.class);
    addField(offsetAction);
    countAction = new SectionCount(buffer, offset + 44, 4, DLG_NUM_ACTIONS, org.infinity.resource.dlg.Action.class);
    addField(countAction);

    if (offsetState.getValue() > 0x30) {
      addField(new Flag(buffer, offset + 48, 4, DLG_THREAT_RESPONSE, s_NonInt));
    }

    offset = offsetState.getValue();
    for (int i = 0; i < countState.getValue(); i++) {
      State state = new State(this, buffer, offset, i);
      offset = state.getEndOffset();
      addField(state);
    }

    offset = offsetTrans.getValue();
    for (int i = 0; i < countTrans.getValue(); i++) {
      Transition transition = new Transition(this, buffer, offset, i);
      offset = transition.getEndOffset();
      addField(transition);
    }

    int textSize = 0;
    offset = offsetStaTri.getValue();
    for (int i = 0; i < countStaTri.getValue(); i++) {
      StateTrigger statri = new StateTrigger(buffer, offset, i);
      offset += statri.getSize();
      textSize += statri.getTextLength();
      addField(statri);
    }

    offset = offsetTranTri.getValue();
    for (int i = 0; i < countTranTri.getValue(); i++) {
      ResponseTrigger trantri = new ResponseTrigger(buffer, offset, i);
      offset += trantri.getSize();
      textSize += trantri.getTextLength();
      addField(trantri);
    }

    offset = offsetAction.getValue();
    for (int i = 0; i < countAction.getValue(); i++) {
      Action action = new Action(buffer, offset, i);
      offset += action.getSize();
      textSize += action.getTextLength();
      addField(action);
    }
    return offset + textSize;
  }

  /**
   * Returns state with specified number from this dialog.
   *
   * @param stateIdx State number
   * @return State with specified number or {@code null}, if such transition not exist
   */
  public State getState(int stateIdx)
  {
    return (State)getAttribute(State.DLG_STATE + " " + stateIdx);
  }

  /**
   * Returns transition with specified number from this dialog.
   *
   * @param transIdx Transition number
   * @return Transition with specified number or {@code null}, if such transition not exist
   */
  public Transition getTransition(int transIdx)
  {
    return (Transition)getAttribute(Transition.DLG_TRANS + " " + transIdx);
  }

  // sorry for this (visibility)
  public void selectInEdit(StructEntry entry) {
    final Viewer view = ((Viewer)getViewerTab(0));
    view.select(entry);
    final Container parent = view.getParent();
    if (parent instanceof JTabbedPane) {
      final JTabbedPane panned = (JTabbedPane)parent;
      panned.setSelectedIndex(panned.indexOfComponent(view));
    }
  }

  public void selectInTree(TreeItemEntry entry)
  {
    final TreeViewer view = ((TreeViewer)getViewerTab(1));
    if (view.select(entry)) {
      final Container parent = view.getParent();
      if (parent instanceof JTabbedPane) {
        final JTabbedPane panned = (JTabbedPane)parent;
        panned.setSelectedIndex(panned.indexOfComponent(view));
      }
    } else {
      JOptionPane.showMessageDialog(view,
              entry.getName() + " is unattainable from the dialogue root.\n"
                              + "It may be referenced by an external dialogue.",
              entry.getName() + " not found in dialogue tree",
              JOptionPane.INFORMATION_MESSAGE);
    }
  }

  /** Updates trigger/action references in states and responses. */
  private void updateReferences(AddRemovable datatype, boolean added)
  {
    if (datatype instanceof StateTrigger) {
      StateTrigger trigger = (StateTrigger)datatype;
      int ofsStates = ((SectionOffset)getAttribute(DLG_OFFSET_STATES)).getValue();
      int numStates = ((SectionCount)getAttribute(DLG_NUM_STATES)).getValue();
      int ofsTriggers = ((SectionOffset)getAttribute(DLG_OFFSET_STATE_TRIGGERS)).getValue();
      int idxTrigger = (trigger.getOffset() - ofsTriggers) / trigger.getSize();

      // adjusting state trigger references
      while (numStates > 0) {
        State state = (State)getAttribute(ofsStates, false);
        if (state != null) {
          DecNumber dec = (DecNumber)state.getAttribute(State.DLG_STATE_TRIGGER_INDEX);
          if (dec.getValue() == idxTrigger) {
            if (added) {
              dec.incValue(1);
            } else {
              dec.setValue(-1);
            }
          } else if (dec.getValue() > idxTrigger) {
            if (added) {
              dec.incValue(1);
            } else {
              dec.incValue(-1);
            }
          }
          ofsStates += state.getSize();
        }
        numStates--;
      }
    } else if (datatype instanceof ResponseTrigger) {
      ResponseTrigger trigger = (ResponseTrigger)datatype;
      int ofsTrans = ((SectionOffset)getAttribute(DLG_OFFSET_RESPONSES)).getValue();
      int numTrans = ((SectionCount)getAttribute(DLG_NUM_RESPONSES)).getValue();
      int ofsTriggers = ((SectionOffset)getAttribute(DLG_OFFSET_RESPONSE_TRIGGERS)).getValue();
      int idxTrigger = (trigger.getOffset() - ofsTriggers) / trigger.getSize();

      // adjusting response trigger references
      while (numTrans > 0) {
        Transition trans = (Transition)getAttribute(ofsTrans, false);
        if (trans != null) {
          Flag flags = (Flag)trans.getAttribute(Transition.DLG_TRANS_FLAGS);
          if (flags.isFlagSet(1)) {
            DecNumber dec = (DecNumber)trans.getAttribute(Transition.DLG_TRANS_TRIGGER_INDEX);
            if (dec.getValue() == idxTrigger) {
              if (added) {
                dec.incValue(1);
              } else {
                flags.setValue(flags.getValue() & ~2);
                dec.setValue(0);
              }
            } else if (dec.getValue() > idxTrigger) {
              if (added) {
                dec.incValue(1);
              } else {
                dec.incValue(-1);
              }
            }
          }
          ofsTrans += trans.getSize();
        }
        numTrans--;
      }
    } else if (datatype instanceof Action) {
      Action action = (Action)datatype;
      int ofsTrans = ((SectionOffset)getAttribute(DLG_OFFSET_RESPONSES)).getValue();
      int numTrans = ((SectionCount)getAttribute(DLG_NUM_RESPONSES)).getValue();
      int ofsActions = ((SectionOffset)getAttribute(DLG_OFFSET_ACTIONS)).getValue();
      int idxAction = (action.getOffset() - ofsActions) / action.getSize();

      // adjusting action references
      while (numTrans > 0) {
        Transition trans = (Transition)getAttribute(ofsTrans, false);
        if (trans != null) {
          Flag flags = (Flag)trans.getAttribute(Transition.DLG_TRANS_FLAGS);
          if (flags.isFlagSet(2)) {
            DecNumber dec = (DecNumber)trans.getAttribute(Transition.DLG_TRANS_ACTION_INDEX);
            if (dec.getValue() == idxAction) {
              if (added) {
                dec.incValue(1);
              } else {
                flags.setValue(flags.getValue() & ~4);
                dec.setValue(0);
              }
            } else if (dec.getValue() > idxAction) {
              if (added) {
                dec.incValue(1);
              } else {
                dec.incValue(-1);
              }
            }
          }
          ofsTrans += trans.getSize();
        }
        numTrans--;
      }
    }
  }

  /** Exports DLG resource as WeiDU D file. */
  private boolean exportDlgAsText(PrintWriter writer)
  {
    boolean retVal = false;

    if (writer != null) {
      // *** write header comment ***
      String niPath = Utils.getJarFileName(NearInfinity.class);
      if (niPath == null || niPath.isEmpty()) {
        niPath = "Near Infinity";
      }
      niPath += " (" + NearInfinity.getVersion() + ")";

      writer.println("// creator  : " + niPath);
      writer.println("// game     : " + Profile.getGameRoot().toString());
      writer.println("// resource : " + getResourceEntry().getResourceName());
      writer.println("// source   : " + Profile.getGameRoot().relativize(getResourceEntry().getActualPath()));

      Path path = Profile.getGameRoot().relativize(Profile.getProperty(Profile.Key.GET_GAME_DIALOG_FILE));
      writer.println("// dialog   : " + path.toString());

      path = Profile.getProperty(Profile.Key.GET_GAME_DIALOGF_FILE);
      if (path != null) {
        path = Profile.getGameRoot().relativize(path);
        writer.println("// dialogF  : " + path.toString());
      } else {
        writer.println("// dialogF  : (none)");
      }
      writer.println();

      // *** start of WeiDU D script ***
      String dlgResRef = getResourceEntry().getResourceName();
      int p = dlgResRef.lastIndexOf('.');
      if (p >= 0) {
        dlgResRef = dlgResRef.substring(0, p);
      }

      writer.print("BEGIN ~" + dlgResRef + "~");
      StructEntry entry = getAttribute(DLG_THREAT_RESPONSE);
      if (entry instanceof IsNumeric) {
        int flags = ((IsNumeric)getAttribute(DLG_THREAT_RESPONSE)).getValue();
        if (flags != 0) {
          writer.print(" " + flags + " // non-zero flags may indicate non-pausing dialogue");
        }
      }
      writer.println();

      // generating list of DLG states for output
      ArrayList<DlgState> statesList = new ArrayList<>();
      int numStates = ((IsNumeric)getAttribute(DLG_NUM_STATES)).getValue();
      for (int idx = 0; idx < numStates; idx++) {
        statesList.add(new DlgState(getState(idx)));
      }

      // scanning for state origins and weight information
      boolean weighted = false;
      int numStateTriggers = ((IsNumeric)getAttribute(DLG_NUM_STATE_TRIGGERS)).getValue();
      for (int idx1 = 0; idx1 < statesList.size(); idx1++) {
        DlgState curState = statesList.get(idx1);
        int[] triggers = null;
        if (curState.triggerIndex > 0) {
          triggers = new int[numStateTriggers];
          Arrays.fill(triggers, -1);
        }

        for (int idx2 = 0; idx2 < statesList.size(); idx2++) {
          DlgState state = statesList.get(idx2);
          // scanning state origins
          for (int idx3 = 0; idx3 < state.responses.size(); idx3++) {
            DlgResponse response = state.responses.get(idx3);
            if (dlgResRef.equalsIgnoreCase(response.nextStateDlg)) {
              if (response.nextStateIndex == idx1) {
                curState.addStateOrigin(idx2, idx3);
              }
            }
          }
          // fetching weight information
          if (triggers != null && idx2 > idx1 &&
              state.triggerIndex >= 0 && state.triggerIndex < curState.triggerIndex) {
            triggers[state.triggerIndex] = idx2;
            weighted = true;
          }
        }

        // finalizing weight information
        if (triggers != null) {
          for (int idx2 = triggers.length - 1; idx2 >= 0; idx2--) {
            if (triggers[idx2] > idx1) {
              curState.addWeightState(triggers[idx2]);
            }
          }
        }
      }

      if (weighted) {
        writer.println("//////////////////////////////////////////////////");
        writer.println("// WARNING: this file contains non-trivial WEIGHTs");
        writer.println("//////////////////////////////////////////////////");
      }

      // traversing through state list to generate script blocks
      for (int idx = 0; idx < statesList.size(); idx++) {
        final DlgState state = statesList.get(idx);
        state.write(writer, dlgResRef, idx, weighted);
      }

      retVal = true;
    }

    return retVal;
  }

  private static void writeStrRef(PrintWriter writer, String key, int strref)
  {
    writer.print(key + " #" + strref);
    writer.print(" /* ");
    writer.print("~" + StringTable.getStringRef(strref, StringTable.Format.NONE) + "~");
    final String wav = StringTable.getSoundResource(strref);
    if (!wav.isEmpty()) {
      writer.print(" [" + wav + "]");
    }
    writer.print(" */");
  }
//-------------------------- INNER CLASSES --------------------------

  /** Used by WeiDU D export routine. */
  private final class DlgState
  {
    /** Contains correctly ordered list of responses. */
    public final ArrayList<DlgResponse> responses = new ArrayList<>();

    /** Space-separated list of transition origins for this state. */
    private String cmtFrom;
    /** Space-separated list of states that are processed before this state. */
    private String cmtWeight;
    /** Used for weight. */
    public int triggerIndex;
    /** Strref of state. */
    private int strref;
    /** Trigger text. */
    private String trigger;

    public DlgState(State state)
    {
      if (state == null) {
        throw new NullPointerException();
      }
      cmtFrom = cmtWeight = trigger = "";

      strref = ((IsNumeric)state.getAttribute(State.DLG_STATE_RESPONSE)).getValue();
      triggerIndex = ((IsNumeric)state.getAttribute(State.DLG_STATE_TRIGGER_INDEX)).getValue();
      if (triggerIndex >= 0) {
        StructEntry e = getAttribute(StateTrigger.DLG_STATETRIGGER + " " + triggerIndex);
        if (e instanceof StateTrigger) {
          trigger = ((StateTrigger)e).getText();
        }
      }

      int responseIndex = ((IsNumeric)state.getAttribute(State.DLG_STATE_FIRST_RESPONSE_INDEX)).getValue();
      int numResponses = ((IsNumeric)state.getAttribute(State.DLG_STATE_NUM_RESPONSES)).getValue();
      if (numResponses > 0) {
        for (int idx = 0; idx < numResponses; idx++) {
          responses.add(new DlgResponse(getTransition(responseIndex + idx)));
        }
      }
    }

    public void addStateOrigin(int stateIndex, int triggerIndex)
    {
      if (stateIndex >= 0 && triggerIndex >= 0) {
        cmtFrom += " " + stateIndex + "." + triggerIndex;
      }
    }

    /** Add subsequent state indices with trigger indices less than current index. */
    public void addWeightState(int stateIndex)
    {
      if (stateIndex > 0) {
        cmtWeight += " " + stateIndex;
      }
    }

    public void write(PrintWriter writer, String dlgResRef, int idx, boolean weighted)
    {
      writer.println();
      writer.print("IF ");

      // optional weight information
      if (triggerIndex >= 0 && weighted) {
        writer.print("WEIGHT #" + triggerIndex + " ");

        if (!cmtWeight.isEmpty()) {
          writer.print("/* Triggers after states #:");
          writer.print(cmtWeight);
          writer.println(" even though they appear after this state */");
        }
      }

      // state trigger
      writer.print("~" + trigger + "~");
      writer.print(" THEN BEGIN " + idx);

      // state origins
      writer.print(" // from:");
      writer.print(cmtFrom);
      writer.println();

      final String indent = "  ";

      // state text
      writeStrRef(writer, indent + "SAY", strref);
      writer.println();

      // responses
      for (DlgResponse response : responses) {
        response.write(writer, dlgResRef, indent);
      }

      writer.println("END");
    }
  }

  /** Used by WeiDU D export routine. */
  private final class DlgResponse
  {
    /** Response flags. */
    private final int flags;
    /** Response text. */
    private int strref;
    /** Journal text. */
    private int strrefJournal;
    /** The trigger code. */
    private String trigger;
    /** The action code. */
    private String action;
    /** Resref to DLG (or null if dialog terminates). */
    public String nextStateDlg;
    /** State index in external DLG (or -1 if dialog terminates). */
    public int nextStateIndex;

    public DlgResponse(Transition trans)
    {
      strref = strrefJournal = nextStateIndex = -1;
      trigger = action = "";
      nextStateDlg = null;

      flags = ((IsNumeric)trans.getAttribute(Transition.DLG_TRANS_FLAGS)).getValue();
      if ((flags & 0x01) != 0) {
        strref = ((IsNumeric)trans.getAttribute(Transition.DLG_TRANS_TEXT)).getValue();
      }
      if ((flags & 0x10) != 0) {
        strrefJournal = ((IsNumeric)trans.getAttribute(Transition.DLG_TRANS_JOURNAL_ENTRY)).getValue();
      }
      if ((flags & 0x02) != 0) {
        int index = ((IsNumeric)trans.getAttribute(Transition.DLG_TRANS_TRIGGER_INDEX)).getValue();
        StructEntry e = getAttribute(ResponseTrigger.DLG_RESPONSETRIGGER + " " + index);
        if (e instanceof AbstractCode) {
          trigger = ((AbstractCode)e).getText();
        }
      }
      if ((flags & 0x04) != 0) {
        int index = ((IsNumeric)trans.getAttribute(Transition.DLG_TRANS_ACTION_INDEX)).getValue();
        StructEntry e = getAttribute(Action.DLG_ACTION + " " + index);
        if (e instanceof AbstractCode) {
          action = ((AbstractCode)e).getText();
        }
      }
      if ((flags & 0x08) == 0) {
        nextStateDlg = ((ResourceRef)trans.getAttribute(Transition.DLG_TRANS_NEXT_DIALOG)).getText();
        nextStateIndex = ((IsNumeric)trans.getAttribute(Transition.DLG_TRANS_NEXT_DIALOG_STATE)).getValue();
      }
    }

    public void write(PrintWriter writer, String dlgResRef, String indent)
    {
      writer.print(indent + "IF ");
      // response trigger
      writer.print("~" + trigger + "~");
      writer.print(" THEN");

      // reply
      if ((flags & 0x01) != 0) {
        writeStrRef(writer, " REPLY", strref);
      }

      // response action
      if ((flags & 0x04) != 0) {
        writer.print(" DO ");
        writer.print("~" + action + "~");
      }

      // journal entry
      if ((flags & 0x10) != 0) {
        final String keyJournal;
        if ((flags & 0x40) != 0) {
          keyJournal = " UNSOLVED_JOURNAL";
        } else if ((flags & 0x100) != 0) {
          keyJournal = " SOLVED_JOURNAL";
        } else {
          keyJournal = " JOURNAL";
        }

        writeStrRef(writer, keyJournal, strrefJournal);
      }

      // transition
      if ((flags & 0x08) != 0) {
        // terminating
        writer.print(" EXIT");
      } else {
        if (dlgResRef.equalsIgnoreCase(nextStateDlg)) {
          // internal transition
          writer.print(" GOTO ");
        } else {
          // external transition
          writer.print(" EXTERN ~" + nextStateDlg + "~ ");
        }
        writer.print(nextStateIndex);
      }
      writer.println();
    }
  }
}
