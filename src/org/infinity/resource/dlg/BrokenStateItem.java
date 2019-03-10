// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.infinity.icon.Icons;

/**
 * Encapsulates a broken dialog state reference - this state is referenced from
 * some transition, but state is absent in dialogue file.
 *
 * @author Mingun
 */
class BrokenStateItem extends StateItem implements BrokenReference
{
  private static final ImageIcon ICON = Icons.getIcon(Icons.ICON_WARNING_16);

  /** Dialog which contains non-existent state. */
  private final DlgResource dlg;
  /** Number of non-existent state. */
  private final int number;
  public BrokenStateItem(DlgResource dlg, int number, StateOwnerItem parent)
  {
    super(parent);
    this.dlg = dlg;
    this.number = number;
  }

  @Override
  public DlgResource getDialog() { return dlg; }

  @Override
  public Icon getIcon() { return ICON; }

  @Override
  public boolean getAllowsChildren() { return false; }

  @Override
  public String getName() { return "State " + number; }

  @Override
  public String toString() { return getName() + ": <Broken reference, state not exist>"; }
}
