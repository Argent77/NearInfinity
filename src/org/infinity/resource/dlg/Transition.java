// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.StringRef;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.util.io.StreamUtils;

/** Player {@link DlgResource dialog} option. */
public final class Transition extends AbstractStruct implements AddRemovable, TreeItemEntry
{
  // DLG/Transition-specific field labels
  public static final String DLG_TRANS = "Response";
  public static final String DLG_TRANS_FLAGS              = "Flags";
  public static final String DLG_TRANS_TEXT               = "Associated text";
  public static final String DLG_TRANS_JOURNAL_ENTRY      = "Journal entry";
  public static final String DLG_TRANS_TRIGGER_INDEX      = "Trigger index";
  public static final String DLG_TRANS_ACTION_INDEX       = "Action index";
  public static final String DLG_TRANS_NEXT_DIALOG        = "Next dialogue";
  public static final String DLG_TRANS_NEXT_DIALOG_STATE  = "Next dialogue state";

  private static final String[] s_flag = {"No flags set", "Text associated", "Trigger", "Action",
                                          "Terminates dialogue", "Journal entry", "Interrupt",
                                          "Add unsolved quest", "Add journal note", "Add solved quest",
                                          "EE: Execute immediate;Attempt to execute non-blocking script actions instantly.",
                                          "EE: Clear actions"};
  /** Transition number which is unique defining it in a dialog. */
  private int nr;

  Transition() throws Exception
  {
    super(null, DLG_TRANS, StreamUtils.getByteBuffer(32), 0);
  }

  Transition(DlgResource dlg, ByteBuffer buffer, int offset, int nr) throws Exception
  {
    super(dlg, DLG_TRANS + " " + nr, buffer, offset);
    this.nr = nr;
  }

  //<editor-fold defaultstate="collapsed" desc="TreeItemEntry">
  // Flag 0: Transition contains text
  @Override
  public boolean hasAssociatedText() { return getFlag().isFlagSet(0); }

  @Override
  public StringRef getAssociatedText()
  {
    return (StringRef)getAttribute(DLG_TRANS_TEXT, false);
  }

  @Override
  public DlgResource getParent() { return (DlgResource)super.getParent(); }
  //</editor-fold>

  public int getActionIndex()
  {
    if (getFlag().isFlagSet(2)) {
      return ((DecNumber)getAttribute(DLG_TRANS_ACTION_INDEX, false)).getValue();
    } else {
      return -1;
    }
  }

  public Flag getFlag()
  {
    return (Flag)getAttribute(DLG_TRANS_FLAGS, false);
  }

  public StringRef getJournalEntry()
  {
    return (StringRef)getAttribute(DLG_TRANS_JOURNAL_ENTRY, false);
  }

  public ResourceRef getNextDialog()
  {
    return (ResourceRef)getAttribute(DLG_TRANS_NEXT_DIALOG, false);
  }

  public int getNextDialogState()
  {
    return ((DecNumber)getAttribute(DLG_TRANS_NEXT_DIALOG_STATE, false)).getValue();
  }

  public int getNumber()
  {
    return nr;
  }

  public int getTriggerIndex()
  {
    if (getFlag().isFlagSet(1)) {
      return ((DecNumber)getAttribute(DLG_TRANS_TRIGGER_INDEX, false)).getValue();
    } else {
      return -1;
    }
  }

//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception
  {
    addField(new Flag(buffer, offset, 4, DLG_TRANS_FLAGS, s_flag));
    addField(new StringRef(buffer, offset + 4, DLG_TRANS_TEXT));
    addField(new StringRef(buffer, offset + 8, DLG_TRANS_JOURNAL_ENTRY));
    addField(new DecNumber(buffer, offset + 12, 4, DLG_TRANS_TRIGGER_INDEX));
    addField(new DecNumber(buffer, offset + 16, 4, DLG_TRANS_ACTION_INDEX));
    addField(new ResourceRef(buffer, offset + 20, DLG_TRANS_NEXT_DIALOG, "DLG"));
    addField(new DecNumber(buffer, offset + 28, 4, DLG_TRANS_NEXT_DIALOG_STATE));
    return offset + 32;
  }
}
