// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.util.ArrayList;
import static java.util.Collections.emptyList;
import static java.util.Collections.enumeration;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.tree.TreeNode;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.icon.Icons;

/** Encapsulates a dialog state entry. */
final class StateItem extends ItemBase implements Iterable<TransitionItem>
{
  private static final ImageIcon ICON = Icons.getIcon(Icons.ICON_STOP_16);

  private final State state;

  /** Tree item that represent visual parent of this state in the tree. */
  private final StateOwnerItem parent;
  /**
   * Item to which need go to in break cycles tree view mode. This item contains
   * referense to the same state as this one (i.e. {@code this.state == main.ref.state})
   */
  private final MainRef<StateItem> main;
  /** Items that represents transition tree nodes from this state. */
  ArrayList<TransitionItem> trans;

  public StateItem(State state, StateOwnerItem parent, MainRef<StateItem> main)
  {
    this.state  = Objects.requireNonNull(state,  "State dialog entry must be not null");
    this.parent = Objects.requireNonNull(parent, "Parent tree of state item must be not null");
    this.main   = main;
  }

  @Override
  public State getEntry() { return state; }

  @Override
  public StateItem getMain() { return main == null || main.ref == this ? null : main.ref; }

  @Override
  public DlgResource getDialog() { return state.getParent(); }

  @Override
  public Icon getIcon() { return ICON; }

  @Override
  public boolean removeChild(ItemBase child) { return trans.remove(child); }

  @Override
  public void traverseChildren(Consumer<ItemBase> action)
  {
    if (trans != null) {
      trans.forEach(action);
    }
  }

  //<editor-fold defaultstate="collapsed" desc="TreeNode">
  @Override
  public TransitionItem getChildAt(int childIndex)
  {
    return getAllowsChildren() ? trans.get(childIndex) : null;
  }

  @Override
  public int getChildCount() { return getAllowsChildren() ? trans.size() : 0; }

  @Override
  public ItemBase getParent() { return parent; }

  @Override
  public int getIndex(TreeNode node)
  {
    return getAllowsChildren() ? trans.indexOf(node) : -1;
  }

  @Override
  public boolean getAllowsChildren()
  {
    return main == null || !BrowserMenuBar.getInstance().breakCyclesInDialogs();
  }

  @Override
  public boolean isLeaf() { return getAllowsChildren() ? trans.isEmpty() : true; }

  @Override
  public Enumeration<? extends TransitionItem> children()
  {
    return enumeration(getAllowsChildren() ? trans : emptyList());
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Iterable">
  @Override
  public Iterator<TransitionItem> iterator() { return trans.iterator(); }
  //</editor-fold>

  @Override
  public String toString() { return getText(state); }
}
