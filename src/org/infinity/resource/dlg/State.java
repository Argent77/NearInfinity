// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.StringRef;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.util.io.StreamUtils;

/**
 * Actor response in the {@link DlgResource dialog}. Each state contains NPC text,
 * list of {@link Transition responces} and may have associated {@link StateTrigger trigger}
 * with a condition, defining whether it is possible to use this state.
 */
public final class State extends AbstractStruct implements AddRemovable, TreeItemEntry
{
  // DLG/State-specific field labels
  public static final String DLG_STATE                      = "State";
  public static final String DLG_STATE_RESPONSE             = "Response";
  public static final String DLG_STATE_FIRST_RESPONSE_INDEX = "First response index";
  public static final String DLG_STATE_NUM_RESPONSES        = "# responses";
  public static final String DLG_STATE_TRIGGER_INDEX        = "Trigger index";

  /** State number which is unique defining it in a dialog. */
  private int nr;

  State() throws Exception
  {
    super(null, DLG_STATE, StreamUtils.getByteBuffer(16), 0);
  }

  State(DlgResource dlg, ByteBuffer buffer, int offset, int count) throws Exception
  {
    super(dlg, DLG_STATE + " " + count, buffer, offset);
    nr = count;
  }

  //<editor-fold defaultstate="collapsed" desc="TreeItemEntry">
  @Override
  public boolean hasAssociatedText() { return true; }

  @Override
  public StringRef getAssociatedText()
  {
    return (StringRef)getAttribute(DLG_STATE_RESPONSE, false);
  }

  @Override
  public DlgResource getParent() { return (DlgResource)super.getParent(); }
  //</editor-fold>

  public int getFirstTrans()
  {
    return ((DecNumber)getAttribute(DLG_STATE_FIRST_RESPONSE_INDEX, false)).getValue();
  }

  public int getNumber()
  {
    return nr;
  }

  public int getTransCount()
  {
    return ((DecNumber)getAttribute(DLG_STATE_NUM_RESPONSES, false)).getValue();
  }

  public int getTriggerIndex()
  {
    return ((DecNumber)getAttribute(DLG_STATE_TRIGGER_INDEX, false)).getValue();
  }

//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    addField(new StringRef(buffer, offset, DLG_STATE_RESPONSE));
    addField(new DecNumber(buffer, offset + 4, 4, DLG_STATE_FIRST_RESPONSE_INDEX));
    addField(new DecNumber(buffer, offset + 8, 4, DLG_STATE_NUM_RESPONSES));
    addField(new DecNumber(buffer, offset + 12, 4, DLG_STATE_TRIGGER_INDEX));
    return offset + 16;
  }
}
