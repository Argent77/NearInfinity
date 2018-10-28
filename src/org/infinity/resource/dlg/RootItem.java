// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.util.ArrayList;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.infinity.datatype.Flag;
import org.infinity.datatype.SectionCount;
import org.infinity.icon.Icons;
import org.infinity.resource.StructEntry;

/** Meta class for identifying root node. */
final class RootItem extends ItemBase
{
  private static final ImageIcon ICON = Icons.getIcon(Icons.ICON_ROW_INSERT_AFTER_16);

  private final ArrayList<StateItem> states = new ArrayList<>();
  private final ImageIcon icon;

  private final int numStates;
  private final int numTransitions;
  private final int numStateTriggers;
  private final int numResponseTriggers;
  private final int numActions;
  private final String flags;

  public RootItem(DlgResource dlg)
  {
    super(dlg);

    this.icon = showIcons() ? ICON : null;

    numStates           = getAttribute(DlgResource.DLG_NUM_STATES);
    numTransitions      = getAttribute(DlgResource.DLG_NUM_RESPONSES);
    numStateTriggers    = getAttribute(DlgResource.DLG_NUM_STATE_TRIGGERS);
    numResponseTriggers = getAttribute(DlgResource.DLG_NUM_RESPONSE_TRIGGERS);
    numActions          = getAttribute(DlgResource.DLG_NUM_ACTIONS);

    final StructEntry entry = dlg.getAttribute(DlgResource.DLG_THREAT_RESPONSE);
    flags = entry instanceof Flag ? ((Flag)entry).toString() : null;

    // finding and storing initial states
    int count = 0;
    for (StructEntry e : dlg.getList()) {
      if (e instanceof State) {
        // First state always under root
        if (count == 0 || ((State)e).getTriggerIndex() >= 0) {
          states.add(new StateItem(dlg, (State)e));
        }
        if (++count >= numStates) {
          // All states readed, so break cycle
          break;
        }
      }
    }
  }

  /** Returns number of available initial states. */
  public int getInitialStateCount()
  {
    return states.size();
  }

  /** Returns the StateItem at the given index or null on error. */
  public StateItem getInitialState(int index)
  {
    if (index >= 0 && index < states.size()) {
      return states.get(index);
    }
    return null;
  }

  @Override
  public Icon getIcon()
  {
    return icon;
  }

  /**
   * Extracts specified {@link SectionCount} attribute from dialog.
   *
   * @param attrName Attribute name
   * @return value of the attribute or 0 if attribute does not exists
   */
  private int getAttribute(String attrName)
  {
    final StructEntry entry = getDialog().getAttribute(attrName);
    if (entry instanceof SectionCount) {
      return ((SectionCount)entry).getValue();
    }
    return 0;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    if (!getDialogName().isEmpty()) {
      sb.append(getDialogName());
    } else {
      sb.append("(Invalid DLG resource)");
    }
    sb.append(" (states: ").append(Integer.toString(numStates));
    sb.append(", responses: ").append(Integer.toString(numTransitions));
    sb.append(", state triggers: ").append(Integer.toString(numStateTriggers));
    sb.append(", response triggers: ").append(Integer.toString(numResponseTriggers));
    sb.append(", actions: ").append(Integer.toString(numActions));
    if (flags != null) {
      sb.append(", flags: ").append(flags);
    }
    sb.append(")");

    return sb.toString();
  }
}