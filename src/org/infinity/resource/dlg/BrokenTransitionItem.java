// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.infinity.icon.Icons;

/**
 * Encapsulates a broken dialog transition reference - reference to which some
 * state refers, but which is absent in dialogue file.
 *
 * @author Mingun
 */
final class BrokenTransitionItem extends TransitionItem implements BrokenReference
{
  private static final ImageIcon ICON = Icons.getIcon(Icons.ICON_WARNING_16);

  /** Number of non-existent response. */
  private final int number;
  public BrokenTransitionItem(int number, StateItem parent)
  {
    super(parent);
    this.number = number;
  }

  @Override
  public DlgResource getDialog() { return parent.getDialog(); }

  @Override
  public Icon getIcon() { return ICON; }

  @Override
  public boolean getAllowsChildren() { return false; }

  @Override
  public String getName() { return "Response " + number; }

  @Override
  public String toString() { return getName() + ": <Broken reference, response not exist>"; }
}
