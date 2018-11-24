// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import org.infinity.datatype.StringRef;
import org.infinity.resource.StructEntry;

/**
 * Interface for structures that represent dialog tree items.
 *
 * @author Mingun
 */
public interface TreeItemEntry extends StructEntry
{
  /**
   * Determines has this entry text or not, even if it contains some string reference.
   *
   * @return {@code true}, if {@link #getAssociatedText()} returns used reference,
   *         {@code false} otherwise
   */
  boolean hasAssociatedText();

  /**
   * Returns attribute that represents text for show in the tree.
   *
   * @return Text for show or {@code null} if case of broken structure
   */
  StringRef getAssociatedText();

  @Override
  DlgResource getParent();
}
