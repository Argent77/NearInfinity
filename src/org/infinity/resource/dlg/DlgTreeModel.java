// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.infinity.datatype.ResourceRef;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;

/** Creates and manages the dialog tree structure. */
final class DlgTreeModel implements TreeModel, TableModelListener, PropertyChangeListener
{
  private final ArrayList<TreeModelListener> listeners = new ArrayList<>();
  /** Maps used dialog names to dialog resources. */
  private final HashMap<String, DlgResource> linkedDialogs = new HashMap<>();
  /** Maps dialog entries to main tree items - items from which the tree grows. */
  private final HashMap<TreeItemEntry, MainRef<? extends ItemBase>> mainItems = new HashMap<>();
  /** Maps dialog entries to tree items that represents it. Used for update tree when entry changes. */
  private final HashMap<TreeItemEntry, List<ItemBase>> allItems = new HashMap<>();

  private final RootItem root;

  public DlgTreeModel(DlgResource dlg)
  {
    linkedDialogs.put(key(dlg.getName()), dlg);

    root = new RootItem(dlg);
    for (StateItem state : root) {
      initState(state);
      putItem(state, null);
    }
    dlg.addTableModelListener(this);
    dlg.addPropertyChangeListener(this);
  }

  //<editor-fold defaultstate="collapsed" desc="TreeModel">
  @Override
  public RootItem getRoot() { return root; }

  @Override
  public ItemBase getChild(Object parent, int index)
  {
    if (parent instanceof ItemBase) {
      final ItemBase child = ((ItemBase)parent).getChildAt(index);
      initNode(child);
      return child;
    }
    return null;
  }

  @Override
  public int getChildCount(Object parent)
  {
    if (parent instanceof TreeNode) {
      return initNode((TreeNode)parent).getChildCount();
    }
    return 0;
  }

  @Override
  public boolean isLeaf(Object node)
  {
    if (node instanceof TreeNode) {
      return initNode((TreeNode)node).isLeaf();
    }
    return false;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue)
  {
    // immutable
  }

  @Override
  public int getIndexOfChild(Object parent, Object child)
  {
    if (parent instanceof TreeNode && child instanceof TreeNode) {
      final TreeNode nodeParent = initNode((TreeNode)parent);
      for (int i = 0; i < nodeParent.getChildCount(); i++) {
        TreeNode nodeChild = nodeParent.getChildAt(i);
        if (nodeChild == child) {
          return i;
        }
      }
    }
    return -1;
  }

  @Override
  public void addTreeModelListener(TreeModelListener l)
  {
    if (l != null && !listeners.contains(l)) {
      listeners.add(l);
    }
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l)
  {
    if (l != null) {
      listeners.remove(l);
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="TableModelListener">
  @Override
  public void tableChanged(TableModelEvent e)
  {
    final Object src = e.getSource();
    // TODO: Insertion or removal of nodes not yet fully supported
    switch (e.getType()) {
      case TableModelEvent.UPDATE: {
        if (src instanceof TreeItemEntry) {
          updateTreeItemEntry((TreeItemEntry)src);
        } else
        if (src instanceof DlgResource) {
          nodeChanged(root);
        }
        break;
      }
      case DlgResource.WILL_BE_DELETE: {
        final DlgResource dlg = (DlgResource)src;
        for (int i = e.getLastRow(); i >= e.getFirstRow(); --i) {
          final StructEntry field = dlg.getField(i);
          if (field instanceof TreeItemEntry) {
            //TODO: Can optimize algorithm and generate fewer events
            removeTreeItemEntry((TreeItemEntry)field);
          }
          //TODO: update nodes when trigger is deleted
        }
        break;
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="PropertyChangeListener">
  @Override
  public void propertyChange(PropertyChangeEvent e)
  {
    final Object src  = e.getSource();
    final String prop = e.getPropertyName();
    if (src instanceof TreeItemEntry) {
      final List<ItemBase> items = allItems.get(src);
      if (items == null) { return; }

      // Count of responses changed
      if (State.DLG_STATE_NUM_RESPONSES.equals(prop)) {
        // Copy list since it can change during iteration
        changeStateTransCount((State)src, new ArrayList<>(items), e.getOldValue(), e.getNewValue());
      } else
      // First response transition changed
      if (State.DLG_STATE_FIRST_RESPONSE_INDEX.equals(prop)) {
        // Copy list since it can change during iteration
        changeStateFirstTrans((State)src, new ArrayList<>(items), e.getOldValue(), e.getNewValue());
      } else
      // Next dialog or next dialog state changed
      if (Transition.DLG_TRANS_NEXT_DIALOG.equals(prop)
       || Transition.DLG_TRANS_NEXT_DIALOG_STATE.equals(prop)
      ) {
        // Copy list since it can change during iteration
        changeTransition(new ArrayList<>(items));
      } else
      // Transition flags changed
      if (Transition.DLG_TRANS_FLAGS.equals(prop)) {
        final int oldFlags = ((Number)e.getOldValue()).intValue();
        final int newFlags = ((Number)e.getNewValue()).intValue();
        final int diff = oldFlags ^ newFlags;

        // Flag 3: Terminates dialogue - if changed, rebuild tree
        if ((diff & (1 << 3)) != 0) {
          // Copy list since it can change during iteration
          changeTransition(new ArrayList<>(items));
        } else
        // Flag 0: Text associated - if changed, repaint nodes
        // No need to repaint if flag 3 changed - it already repainted
        if ((diff & (1 << 0)) != 0) {
          items.forEach(this::nodeChanged);
        }
      } else
      // Response text or Associated text changed
      if (State.DLG_STATE_RESPONSE.equals(prop) || Transition.DLG_TRANS_TEXT.equals(prop)) {
        items.forEach(this::nodeChanged);
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Events">
  /**
   * Updates tree when specified state or transition entry changed.
   *
   * @param entry Tree entry for which all visual nodes must be updated
   */
  private void updateTreeItemEntry(TreeItemEntry entry)
  {
    final List<ItemBase> items = allItems.get(entry);
    if (items != null) {
      for (ItemBase item : items) {
        nodeChanged(item);
      }
    }
  }

  /**
   * Updates tree when specified state or transition is removed.
   *
   * @param entry Tree entry for which all visual nodes must be removed from the tree
   */
  private void removeTreeItemEntry(TreeItemEntry entry)
  {
    mainItems.remove(entry);
    final List<ItemBase> items = allItems.remove(entry);
    if (items != null) {
      for (final ItemBase item : items) {
        final ItemBase parent = item.getParent();
        final int index = parent.getIndex(item);

        parent.removeChild(item);
        if (index >= 0) {
          // In break cycles mode items that begins cycle is invisible, but exists.
          // For such items index is < 0
          fireTreeNodesRemoved(parent.getPath(), new int[]{index}, new Object[]{item});
        }
      }
    }
  }

  /**
   * Removes visual sub-tree (including {@code item} itself) from tree model,
   * notifies listeners about changes. Changes includes removal of nodes and
   * changes in some non-main nodes (which a not a part of the deleted sub-tree),
   * if among the deleted nodes there are main nodes. Does not change dialog tree
   * entries - affects only GUI nodes.
   *
   * @param item Root of deleted sub-tree
   */
  private void removeItem(ItemBase item)
  {
    final TreeItemEntry entry = item.getEntry();
    final List<ItemBase> items = allItems.get(entry);
    if (items.remove(item)) {
      // If element not exists in all items list it already deleted, so skip.
      // This occurs when dialog has cycle and `item` is some item inside it
      @SuppressWarnings("unchecked")
      final MainRef<ItemBase> main = (MainRef<ItemBase>)mainItems.get(entry);
      if (main != null && main.ref == item) {
        if (items.isEmpty()) {
          mainItems.remove(entry);
        } else {
          main.ref = items.get(0);
          nodeChanged(main.ref);
        }
      }
      item.traverseChildren(this::removeItem);
    }
  }

  //<editor-fold defaultstate="collapsed" desc="State changed">
  /**
   * Changes tree structure accourding to the changes in the
   * {@link State#DLG_STATE_FIRST_RESPONSE_INDEX} property of the state.
   * <p>
   * Example:
   * <code><pre>
   * Transition indexes (start increased by 2):
   *    oldValue: 3, cnt: 4 -> [3, 4, 5, 6]
   *    newValue: 5, cnt: 4 -> [5, 6, 7, 8] -> remove [3, 4], insert [7, 8]
   * Tree item child index:     0  1  2  3             0  1           2  3
   *
   * Transition indexes (start decreased by 2):
   *    oldValue: 3, cnt: 4 -> [3, 4, 5, 6]
   *    newValue: 1, cnt: 4 -> [1, 2, 3, 4] -> remove [5, 6], insert [1, 2]
   * Tree item child index:     0  1  2  3             2  3           0  1
   * </pre></code>
   *
   * @param state Changed state entry
   * @param items List of visual items that represents state in the tree
   * @param oldValue Old value of bound bean property
   * @param newValue New value of bound bean property
   */
  private void changeStateFirstTrans(State state, List<ItemBase> items, Object oldValue, Object newValue)
  {
    final int cnt = state.getTransCount();
    final int oldStart = ((Number)oldValue).intValue();
    final int newStart = ((Number)newValue).intValue();
    final int diff = newStart - oldStart;

    if (diff > 0) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem)item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) continue;

        removeChildTransitions(s, 0, diff);
        insertChildTransitions(s, newStart, cnt - diff, cnt, false);
      }
    } else
    if (diff < 0) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem)item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) continue;

        removeChildTransitions(s, cnt + diff, cnt);
        insertChildTransitions(s, newStart, 0, -diff, true);
      }
    }
  }

  /**
   * Changes tree structure accourding to the changes in the
   * {@link State#DLG_STATE_NUM_RESPONSES} property of the state. Appends
   * or removes {@link TransitionItem}s based on the value of the bean property.
   *
   * @param state Changed state entry
   * @param items List of visual items that represents state in the tree
   * @param oldValue Old value of bound bean property
   * @param newValue New value of bound bean property
   */
  private void changeStateTransCount(State state, List<ItemBase> items, Object oldValue, Object newValue)
  {
    final int start  = state.getFirstTrans();
    final int oldCnt = ((Number)oldValue).intValue();
    final int newCnt = ((Number)newValue).intValue();

    if (newCnt > oldCnt) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem)item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) continue;

        insertChildTransitions(s, start, oldCnt, newCnt, false);
      }
    } else
    if (newCnt < oldCnt) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem)item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) continue;

        removeChildTransitions(s, newCnt, oldCnt);
      }
    }
  }

  /**
   * Adds continuous range of tree items that represent transitions from specified
   * state and notifies listeners. If state is not main state and option
   * {@link BrowserMenuBar#breakCyclesInDialogs()} is enabled, do nothing.
   *
   * @param parent Parent state under which tree items must be added
   * @param startTransition First transition index that state has
   * @param fromIndex Index of the first child tree item under {@code parent}
   *        state to add, inclusive
   * @param toIndex Index of the last child tree item under {@code parent}
   *        state to add, exclusive
   * @param prepend If {@code true}, then insert child transitions before existing,
   *        otherwise after existing
   */
  private void insertChildTransitions(StateItem parent, int startTransition, int fromIndex, int toIndex, boolean prepend)
  {
    final int size = parent.trans.size();
    final int from = Math.max(0, Math.min(fromIndex, size));
    final int to   = Math.max(from, toIndex);

    parent.trans.ensureCapacity(to);
    addTransitions(parent, startTransition + from, startTransition + to, prepend);

    final int[] childIndices = IntStream.range(from, to).toArray();
    final Object[] children  = parent.trans.subList(from, to).toArray();

    fireTreeNodesInserted(parent.getPath(), childIndices, children);
  }

  /**
   * Removes all visual tree items under {@code parent} state and notifies listeners.
   *
   * @param parent Parent state at which tree items must be removed
   * @param fromIndex Index of the first child tree item of the {@code state}
   *        to remove, inclusive
   * @param toIndex Index of the last child tree item of the {@code state}
   *        to remove, exclusive
   */
  private void removeChildTransitions(StateItem parent, int fromIndex, int toIndex)
  {
    final int size = parent.trans.size();
    final int from = Math.max(0, Math.min(fromIndex, size));
    final int to   = Math.max(from, Math.min(toIndex, size));

    final List<TransitionItem> items = parent.trans.subList(from, to);
    final int[] childIndices = IntStream.range(from, to).toArray();
    final Object[] children  = items.toArray();

    // Clear global registers and redirect main references
    for (TransitionItem item : items) {
      removeItem(item);
    }

    items.clear();
    fireTreeNodesRemoved(parent.getPath(), childIndices, children);
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Transition changed">
  /**
   * Changes tree structure accourding to the changes in the
   * {@link Transition#DLG_TRANS_NEXT_DIALOG},
   * {@link Transition#DLG_TRANS_NEXT_DIALOG_STATE} or
   * {@link Transition#DLG_TRANS_FLAGS} properties of the transition entry.
   *
   * Emits {@link TreeModelListener#treeStructureChanged} event for each element
   * in {@code items}.
   *
   * @param items List of visual items that represents transition in the tree
   */
  private void changeTransition(List<ItemBase> items)
  {
    for (ItemBase item : items) {
      final TransitionItem t = (TransitionItem)item;
      if (t.nextState != null) {
        removeItem(t.nextState);
        t.nextState = null;
      }
      // New node, if required, will be created on demand
      fireTreeStructureChanged(item.getPath());
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Event emitting">
  private void nodeChanged(ItemBase node)
  {
    final ItemBase parent = node.getParent();
    final Object[] children = {node};
    if (parent == null) {
      fireTreeNodesChanged(null, null, children);
    } else {
      fireTreeNodesChanged(parent.getPath(), new int[]{parent.getIndex(node)}, children);
    }
  }

  private void fireTreeNodesChanged(TreePath path, int[] childIndices, Object[] children)
  {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path, childIndices, children);
      for (final TreeModelListener tml : listeners) {
        tml.treeNodesChanged(event);
      }
    }
  }

  private void fireTreeNodesInserted(TreePath path, int[] childIndices, Object[] children)
  {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path, childIndices, children);
      for (final TreeModelListener tml : listeners) {
        tml.treeNodesInserted(event);
      }
    }
  }

  private void fireTreeNodesRemoved(TreePath path, int[] childIndices, Object[] children)
  {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path, childIndices, children);
      for (final TreeModelListener tml : listeners) {
        tml.treeNodesRemoved(event);
      }
    }
  }

  private void fireTreeStructureChanged(TreePath path)
  {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path);
      for (final TreeModelListener tml : listeners) {
        tml.treeStructureChanged(event);
      }
    }
  }
  //</editor-fold>
  //</editor-fold>

  /**
   * Translates child struct of the dialog that this tree represents, to GUI item.
   * For {@link State states} returns main state.
   *
   * @param entry Child struct of the dialog for search
   * @return GUI item or {@code null} if such item not have GUI element
   */
  public ItemBase map(TreeItemEntry entry)
  {
    final MainRef<? extends ItemBase> item = mainItems.get(entry);
    if (item == null) {
      if (entry instanceof State) {
        return slowFindState((State)entry);
      }
      return slowFindTransition((Transition)entry);
    }
    return item.ref;
  }

  /**
   * Checks that specified GUI item corresponds searched state entry. This method
   * returns {@code true} only for {@link StateItem#getMain main} GUI items.
   *
   * @param queue The queue of transitions containing references to states for check.
   *        If specified {@code state} does not correspond to searched {@code entry}
   *        it will be filled with next candidates for check
   * @param state Checked GUI item
   * @param entry Searched entry
   *
   * @return {@code true} if specified GUI item contains reference to searchd entry,
   *         {@code false} otherwise
   */
  private boolean checkState(ArrayDeque<TransitionItem> queue, StateItem state, State entry)
  {
    if (state.getMain() != null) return false;
    initState(state);

    if (state.getEntry() == entry) {
      return true;
    }
    for (TransitionItem trans : state) {
      if (trans.getMain() == null) {
        queue.add(trans);
      }
    }
    return false;
  }

  /**
   * Finds GUI item that corresponds specified state. Creates non-existent
   * tree items when necessary.
   *
   * @param entry Child struct of the dialog for search
   * @return Tree item that represents state or {@code null} if such state
   *         did not exist in the dialog
   */
  private StateItem slowFindState(State entry)
  {
    final ArrayDeque<TransitionItem> queue = new ArrayDeque<>();
    for (StateItem state : root) {
      if (checkState(queue, state, entry)) {
        return state;
      }
    }

    TransitionItem trans;
    while (true) {
      trans = queue.poll();
      if (trans == null) break;

      initTransition(trans);
      if (trans.nextState != null && checkState(queue, trans.nextState, entry)) {
        return trans.nextState;
      }
    }
    return null;
  }

  /**
   * Finds GUI item that corresponds specified transition. Creates non-existent
   * tree items when necessary.
   *
   * @param entry Child struct of the dialog for search
   * @return Tree item that represents transition or {@code null} if such transition
   *         did not exist in the dialog
   */
  private TransitionItem slowFindTransition(Transition entry)
  {
    final ArrayDeque<StateItem> queue = new ArrayDeque<>();
    for (StateItem state : root) {
      if (state.getMain() == null) {
        queue.add(state);
      }
    }

    StateItem state;
    while (true) {
      state = queue.poll();
      if (state == null) break;

      for (TransitionItem trans : state) {
        if (trans.getMain() != null) continue;
        initTransition(trans);

        if (trans.getEntry() == entry) {
          return trans;
        }
        if (trans.nextState != null && trans.nextState.getMain() == null) {
          queue.add(trans.nextState);
        }
      }
    }
    return null;
  }

  /**
   * Returns a dialog resource object based on the specified resource name.
   * Reuses exising DlgResource objects if available.
   */
  private DlgResource getDialogResource(ResourceRef dlgRef)
  {
    return dlgRef.isEmpty() ? null : linkedDialogs.computeIfAbsent(
            key(dlgRef.getResourceName()),
            name -> {
              try {
                return new DlgResource(ResourceFactory.getResourceEntry(name));
              } catch (Exception e) {
                e.printStackTrace();
              }
              return null;
            }
    );
  }

  /** Returns key for cache of {@link DlgResource}'s. */
  private static String key(String dlgName) { return dlgName.toUpperCase(Locale.ENGLISH); }

  /** Adds all available child nodes to the given parent node. */
  private void initState(StateItem state)
  {
    if (state.trans == null) {
      final int start = state.getEntry().getFirstTrans();
      final int count = state.getEntry().getTransCount();

      state.trans = new ArrayList<>(count);
      addTransitions(state, start, start + count, false);
    }
  }

  /**
   * Creates {@link TransitionItem}s for transitions at specified indexes.
   *
   * @param state State item for which need create additional transition tree items
   * @param firstTransition Index of the first transition in the {@code state}
   *        {@link StateItem#getDialog() dialog} that need to add, inclusive
   * @param lastTransition Index of the last transition in the state dialog that
   *        need to add, exclusive
   * @param prepend If {@code true}, then insert child transitions before existing,
   *        otherwise after existing
   */
  @SuppressWarnings("unchecked")
  private void addTransitions(StateItem state, int firstTransition, int lastTransition, boolean prepend)
  {
    final DlgResource dlg = state.getDialog();
    for (int i = firstTransition; i < lastTransition; ++i) {
      final Transition trans = dlg.getTransition(i);
      final MainRef<TransitionItem> main;
      final TransitionItem item;
      if (trans == null) {
        main = null;
        item = new BrokenTransitionItem(i, state);
      } else {
        main = (MainRef<TransitionItem>)mainItems.get(trans);
        item = new TransitionItem(trans, state, main);
      }
      if (prepend) {
        state.trans.add(i - firstTransition, item);
      } else {
        state.trans.add(item);
      }
      putItem(item, main);
    }
  }

  /** Adds all available child nodes to the given parent node. */
  private void initTransition(TransitionItem trans)
  {
    if (trans.nextState == null) {
      final Transition t = trans.getEntry();
      final DlgResource nextDlg = getDialogResource(t.getNextDialog());

      if (nextDlg != null) {
        final int nextIndex = t.getNextDialogState();
        final State state = nextDlg.getState(nextIndex);
        if (state == null) {
          trans.nextState = new BrokenStateItem(nextDlg, nextIndex, trans);
          putItem(trans.nextState, null);
        } else {
          @SuppressWarnings("unchecked")
          final MainRef<StateItem> main = (MainRef<StateItem>)mainItems.get(state);

          trans.nextState = new StateItem(state, trans, main);
          putItem(trans.nextState, main);
        }
      }
    }
  }

  /** Adds all available child nodes to the given parent node. */
  private TreeNode initNode(TreeNode node)
  {
    if (node.getAllowsChildren()) {
      if (node instanceof StateItem) {
        initState((StateItem)node);
      } else
      if (node instanceof TransitionItem) {
        initTransition((TransitionItem)node);
      }
    }
    return node;
  }

  /**
   * Registers visual tree node in internal maps.
   *
   * @param item The item to register
   * @param main The reference to an item which can have childrens in the break cycles mode
   */
  private <T extends ItemBase> void putItem(T item, MainRef<T> main)
  {
    final TreeItemEntry entry = item.getEntry();
    allItems.computeIfAbsent(entry, i -> new ArrayList<>()).add(item);
    if (main == null) {
      mainItems.put(entry, new MainRef<>(item));
    }
  }
}
