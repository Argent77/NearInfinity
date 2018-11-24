// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

/**
 * Reference to the main item of the visual tree item. Storage of the reference
 * to a main item indirectly, in this object, allows to replace quickly the main
 * item object without the need for a traverse of all items which refer to it.
 * It is required when the main visual node is removed from a tree, but other
 * nodes, representing the same {@link TreeItemEntry}, still are available in a
 * tree. At emergence of such situation as the one of such nodes will be chosen
 * as new main item.
 *
 * @param <T> Type of reference to hold
 *
 * @author Mingun
 */
final class MainRef<T extends ItemBase>
{
  /** Actual reference to the main item. */
  T ref;

  public MainRef(T ref) { this.ref = ref; }

  @Override
  public String toString() { return ref.toString(); }
}
