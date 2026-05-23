// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import static java.util.Arrays.asList;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.infinity.datatype.ResourceRef;
import org.infinity.gui.menu.OptionsMenuItem;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;

/** Creates and manages the dialog tree structure. */
public class DlgTreeModel implements TreeModel, TreeNode, TableModelListener, PropertyChangeListener {
  private final ArrayList<TreeModelListener> listeners = new ArrayList<>();
  /**
   * List of all dialogs, that contains at tree root. Each of these dialogs also stored in the {@link #linkedDialogs}
   * map, but the opposite is incorrect
   */
  private final List<DlgItem> rootDialogs = new ArrayList<>();

  /** Maps dialog names of dialogs, presented in the tree, to dialog itself. */
  private final HashMap<String, DlgResource> linkedDialogs = new HashMap<>();

  /** Maps dialog entries to tree items that represents it. */
  private final HashMap<TreeItemEntry, DlgElement> items = new HashMap<>();

  /** States, that not accessible from any other dialogue. */
  private final OrphanStates orphanStates = new OrphanStates(this);

  /** Transitions, that not accessible from any other dialogue. */
  private final OrphanTransitions orphanTrans = new OrphanTransitions(this);

  public DlgTreeModel(DlgResource dlg) {
    addToRoot(dlg);
  }

  @Override
  public String toString() {
    return "Dialogues";
  }

  @Override
  public DlgTreeModel getRoot() {
    return this;
  }

  @Override
  public ItemBase getChild(Object parent, int index) {
    if (parent == this) {
      return getChildAt(index);
    }
    if (parent instanceof ItemBase) {
      final ItemBase child = ((ItemBase) parent).getChildAt(index);
      initNode(child);
      return child;
    }
    return null;
  }

  @Override
  public int getChildCount(Object parent) {
    if (parent instanceof TreeNode) {
      return initNode((TreeNode) parent).getChildCount();
    }
    return 0;
  }

  @Override
  public boolean isLeaf(Object node) {
    if (node instanceof TreeNode) {
      return initNode((TreeNode) node).isLeaf();
    }
    return false;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    // immutable
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    if (parent instanceof TreeNode && child instanceof TreeNode) {
      final TreeNode nodeParent = initNode((TreeNode) parent);
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
  public void addTreeModelListener(TreeModelListener l) {
    if (l != null && !listeners.contains(l)) {
      listeners.add(l);
    }
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    if (l != null) {
      listeners.remove(l);
    }
  }

  @Override
  public ItemBase getChildAt(int childIndex) {
    if (childIndex == rootDialogs.size()) {
      return orphanStates;
    }
    if (childIndex == rootDialogs.size() + 1) {
      return orphanTrans;
    }
    return rootDialogs.get(childIndex);
  }

  @Override
  public int getChildCount() {
    return rootDialogs.size() + 2;
  }

  @Override
  public TreeNode getParent() {
    return null;
  }

  @Override
  public int getIndex(TreeNode node) {
    if (orphanStates == node) {
      return rootDialogs.size();
    }
    if (orphanTrans == node) {
      return rootDialogs.size() + 1;
    }
    return rootDialogs.indexOf(node);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  @Override
  public Enumeration<? extends ItemBase> children() {
    return new Enumeration<ItemBase>() {
      private final Iterator<? extends ItemBase> it1 = rootDialogs.iterator();
      private final Iterator<? extends ItemBase> it2 = asList(orphanStates, orphanTrans).iterator();

      @Override
      public boolean hasMoreElements() {
        return it1.hasNext() || it2.hasNext();
      }

      @Override
      public ItemBase nextElement() {
        return it1.hasNext() ? it1.next() : it2.next();
      }
    };
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    final Object src = e.getSource();
    // TODO: Insertion or removal of nodes not yet fully supported
    switch (e.getType()) {
      case TableModelEvent.UPDATE: {
        if (src instanceof TreeItemEntry) {
          updateTreeItemEntry((TreeItemEntry) src);
        } else if (src instanceof DlgResource) {
          nodeChanged(map((DlgResource) src));
        }
        break;
      }
      case AbstractStruct.WILL_BE_DELETE: {
        final DlgResource dlg = (DlgResource) src;
        for (int i = e.getLastRow(); i >= e.getFirstRow(); --i) {
          final StructEntry field = dlg.getField(i);
          if (field instanceof TreeItemEntry) {
            // TODO: Can optimize algorithm and generate fewer events
            removeTreeItemEntry((TreeItemEntry) field);
          }
          // TODO: update nodes when trigger is deleted
        }
        break;
      }
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    final Object src = e.getSource();
    final String prop = e.getPropertyName();
    if (src instanceof TreeItemEntry) {
      final DlgElement elem = items.get(src);
      if (elem == null) {
        return;
      }

      // Count of responses changed
      if (State.DLG_STATE_NUM_RESPONSES.equals(prop)) {
        // Copy list since it can change during iteration
        changeStateTransCount((State) src, new ArrayList<>(elem.all), e.getOldValue(), e.getNewValue());
      } else
      // First response transition changed
      if (State.DLG_STATE_FIRST_RESPONSE_INDEX.equals(prop)) {
        // Copy list since it can change during iteration
        changeStateFirstTrans((State) src, new ArrayList<>(elem.all), e.getOldValue(), e.getNewValue());
      } else
      // Next dialog or next dialog state changed
      if (Transition.DLG_TRANS_NEXT_DIALOG.equals(prop) || Transition.DLG_TRANS_NEXT_DIALOG_STATE.equals(prop)) {
        // Copy list since it can change during iteration
        changeTransition(new ArrayList<>(elem.all));
      } else
      // Transition flags changed
      if (Transition.DLG_TRANS_FLAGS.equals(prop)) {
        final int oldFlags = ((Number) e.getOldValue()).intValue();
        final int newFlags = ((Number) e.getNewValue()).intValue();
        final int diff = oldFlags ^ newFlags;

        // Flag 3: Terminates dialogue - if changed, rebuild tree
        if ((diff & (1 << 3)) != 0) {
          // Copy list since it can change during iteration
          changeTransition(new ArrayList<>(elem.all));
        } else
        // Flag 0: Text associated - if changed, repaint nodes
        // No need to repaint if flag 3 changed - it already repainted
        if ((diff & (1 << 0)) != 0) {
          elem.all.forEach(this::nodeChanged);
        }
      } else
      // Response text or Associated text changed
      if (State.DLG_STATE_RESPONSE.equals(prop) || Transition.DLG_TRANS_TEXT.equals(prop)) {
        elem.all.forEach(this::nodeChanged);
      }
    }
  }

  /**
   * Updates tree when specified state or transition entry changed.
   *
   * @param entry Tree entry for which all visual nodes must be updated
   */
  private void updateTreeItemEntry(TreeItemEntry entry) {
    final DlgElement elem = items.get(entry);
    if (elem != null) {
      elem.all.forEach(this::nodeChanged);
    }
  }

  /**
   * Updates tree when specified state or transition is removed.
   *
   * @param entry Tree entry for which all visual nodes must be removed from the tree
   */
  private void removeTreeItemEntry(TreeItemEntry entry) {
    final DlgElement elem = items.remove(entry);
    if (elem != null) {
      for (final ItemBase item : elem.all) {
        final TreeNode node = item.getParent();
        if (!(node instanceof ItemBase)) {
          continue;
        }

        final ItemBase parent = (ItemBase) node;
        final int index = parent.getIndex(item);

        parent.removeChild(item);
        if (index >= 0) {
          // In break cycles mode items that begins cycle is invisible, but exists.
          // For such items index is < 0
          fireTreeNodesRemoved(parent.getPath(), new int[] { index }, new Object[] { item });
        }
      }
    }
  }

  /**
   * Removes visual sub-tree (including {@code item} itself) from tree model, notifies listeners about changes. Changes
   * includes removal of nodes and changes in some non-main nodes (which a not a part of the deleted sub-tree), if among
   * the deleted nodes there are main nodes. Does not change dialog tree entries - affects only GUI nodes.
   *
   * @param item Root of deleted sub-tree
   */
  private void removeItem(ItemBase item) {
    final TreeItemEntry entry = item.getEntry();
    final DlgElement elem = items.get(entry);
    if (elem.all.remove(item)) {
      // If element not exists in all items list it already deleted, so skip.
      // This occurs when dialog has cycle and `item` is some item inside it
      if (elem.main == item) {
        // If we remove last GUI element for `entry`, then remove reference to the
        // main item itself, otherwise make first remaining node as main node
        if (elem.all.isEmpty()) {
          elem.main = null;
        } else {
          elem.main = elem.all.get(0);
          nodeChanged(elem.main);
        }
      }
      item.traverseChildren(this::removeItem);
    }
  }

  /**
   * Changes tree structure accourding to the changes in the {@link State#DLG_STATE_FIRST_RESPONSE_INDEX} property of
   * the state.
   * <p>
   * Example: <code><pre>
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
   * @param state    Changed state entry
   * @param items    List of visual items that represents state in the tree
   * @param oldValue Old value of bound bean property
   * @param newValue New value of bound bean property
   */
  private void changeStateFirstTrans(State state, List<ItemBase> items, Object oldValue, Object newValue) {
    final int cnt = state.getTransCount();
    final int oldStart = ((Number) oldValue).intValue();
    final int newStart = ((Number) newValue).intValue();
    final int diff = newStart - oldStart;

    if (diff > 0) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem) item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) {
          continue;
        }

        removeChildTransitions(s, 0, diff);
        insertChildTransitions(s, newStart, cnt - diff, cnt, false);
      }
    } else if (diff < 0) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem) item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) {
          continue;
        }

        removeChildTransitions(s, cnt + diff, cnt);
        insertChildTransitions(s, newStart, 0, -diff, true);
      }
    }
  }

  /**
   * Changes tree structure accourding to the changes in the {@link State#DLG_STATE_NUM_RESPONSES} property of the
   * state. Appends or removes {@link TransitionItem}s based on the value of the bean property.
   *
   * @param state    Changed state entry
   * @param items    List of visual items that represents state in the tree
   * @param oldValue Old value of bound bean property
   * @param newValue New value of bound bean property
   */
  private void changeStateTransCount(State state, List<ItemBase> items, Object oldValue, Object newValue) {
    final int start = state.getFirstTrans();
    final int oldCnt = ((Number) oldValue).intValue();
    final int newCnt = ((Number) newValue).intValue();

    if (newCnt > oldCnt) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem) item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) {
          continue;
        }

        insertChildTransitions(s, start, oldCnt, newCnt, false);
      }
    } else if (newCnt < oldCnt) {
      for (ItemBase item : items) {
        final StateItem s = (StateItem) item;
        // If this not main state item and non-main items do not contains childs, skip
        if (s.trans == null) {
          continue;
        }

        removeChildTransitions(s, newCnt, oldCnt);
      }
    }
  }

  /**
   * Adds continuous range of tree items that represent transitions from specified state and notifies listeners. If
   * state is not main state and option {@link OptionsMenuItem#breakCyclesInDialogs()} is enabled, do nothing.
   *
   * @param parent          Parent state under which tree items must be added
   * @param startTransition First transition index that state has
   * @param fromIndex       Index of the first child tree item under {@code parent} state to add, inclusive
   * @param toIndex         Index of the last child tree item under {@code parent} state to add, exclusive
   * @param prepend         If {@code true}, then insert child transitions before existing, otherwise after existing
   */
  private void insertChildTransitions(StateItem parent, int startTransition, int fromIndex, int toIndex,
      boolean prepend) {
    final int size = parent.trans.size();
    final int from = Math.max(0, Math.min(fromIndex, size));
    final int to = Math.max(from, toIndex);

    parent.trans.ensureCapacity(to);
    addTransitions(parent, startTransition + from, startTransition + to, prepend);

    final int[] childIndices = IntStream.range(from, to).toArray();
    final Object[] children = parent.trans.subList(from, to).toArray();

    fireTreeNodesInserted(parent.getPath(), childIndices, children);
  }

  /**
   * Removes all visual tree items under {@code parent} state and notifies listeners.
   *
   * @param parent    Parent state at which tree items must be removed
   * @param fromIndex Index of the first child tree item of the {@code state} to remove, inclusive
   * @param toIndex   Index of the last child tree item of the {@code state} to remove, exclusive
   */
  private void removeChildTransitions(StateItem parent, int fromIndex, int toIndex) {
    final int size = parent.trans.size();
    final int from = Math.max(0, Math.min(fromIndex, size));
    final int to = Math.max(from, Math.min(toIndex, size));

    final List<TransitionItem> items = parent.trans.subList(from, to);
    final int[] childIndices = IntStream.range(from, to).toArray();
    final Object[] children = items.toArray();

    // Clear global registers and redirect main references
    for (TransitionItem item : items) {
      removeItem(item);
    }

    items.clear();
    fireTreeNodesRemoved(parent.getPath(), childIndices, children);
  }

  /**
   * Changes tree structure accourding to the changes in the {@link Transition#DLG_TRANS_NEXT_DIALOG},
   * {@link Transition#DLG_TRANS_NEXT_DIALOG_STATE} or {@link Transition#DLG_TRANS_FLAGS} properties of the transition
   * entry.
   *
   * Emits {@link TreeModelListener#treeStructureChanged} event for each element in {@code items}.
   *
   * @param items List of visual items that represents transition in the tree
   */
  private void changeTransition(List<ItemBase> items) {
    for (ItemBase item : items) {
      final TransitionItem t = (TransitionItem) item;
      if (t.nextState != null) {
        removeItem(t.nextState);
        t.nextState = null;
      }
      // New node, if required, will be created on demand
      fireTreeStructureChanged(item.getPath());
    }
  }

  private void nodeChanged(ItemBase node) {
    final TreeNode parent = node.getParent();
    final int[] childIndices = { parent.getIndex(node) };
    final Object[] children = { node };
    final TreePath path = parent instanceof ItemBase ? ((ItemBase) parent).getPath() : new TreePath(this);

    fireTreeNodesChanged(path, childIndices, children);
  }

  private void fireTreeNodesChanged(TreePath path, int[] childIndices, Object[] children) {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path, childIndices, children);
      for (final TreeModelListener tml : listeners) {
        tml.treeNodesChanged(event);
      }
    }
  }

  private void fireTreeNodesInserted(TreePath path, int[] childIndices, Object[] children) {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path, childIndices, children);
      for (final TreeModelListener tml : listeners) {
        tml.treeNodesInserted(event);
      }
    }
  }

  private void fireTreeNodesRemoved(TreePath path, int[] childIndices, Object[] children) {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path, childIndices, children);
      for (final TreeModelListener tml : listeners) {
        tml.treeNodesRemoved(event);
      }
    }
  }

  private void fireTreeStructureChanged(TreePath path) {
    if (!listeners.isEmpty()) {
      final TreeModelEvent event = new TreeModelEvent(this, path);
      for (final TreeModelListener tml : listeners) {
        tml.treeStructureChanged(event);
      }
    }
  }

  /**
   * Translates child struct of the dialog that this tree represents, to GUI item. For {@link State states} returns main
   * state.
   *
   * @param entry Child struct of the dialog for search
   * @return GUI item or {@code null} if such item not have GUI element
   */
  public ItemBase map(TreeItemEntry entry) {
    final DlgElement elem = items.get(entry);
    if (elem == null) {
      if (entry instanceof State) {
        for (final DlgItem dlg : rootDialogs) {
          final StateItem state = slowFindState(dlg, (State) entry);
          if (state != null) {
            return state;
          }
        }
        return null;
      }
      for (final DlgItem dlg : rootDialogs) {
        final TransitionItem trans = slowFindTransition(dlg, (Transition) entry);
        if (trans != null) {
          return trans;
        }
      }
      return null;
    }
    return elem.main;
  }

  /**
   * @return Path the identifying first (main) dialog in model. Never {@code null}
   */
  public TreePath getMainDlgPath() {
    return new TreePath(this).pathByAddingChild(rootDialogs.get(0));
  }

  public DlgResource getDialog() {
    return rootDialogs.get(0).getDialog();
  }

  /**
   * Add specified entry to visual tree together with dialog(s), from which it accessible.
   *
   * @param entry Child struct of the dialog to add
   * @return GUI item of new added node. This is always main item
   */
  public ItemBase addToRoot(TreeItemEntry entry) {
    final DlgResource dlg = entry.getParent();
    if (entry instanceof State) {
      return addToRoot((State) entry);
    }
    if (entry instanceof Transition) {
      final Transition trans = (Transition) entry;
      final ArrayDeque<State> queue = new ArrayDeque<>();
      if (dlg.findUsages(trans, queue::add)) {
        // First add to the tree first state, that refers to this
        // transition, and then allow map do their work
        addToRoot(queue.pop());
        return map(trans);
      }
      // If transition is not accessible from any dialogue and has not parents,
      // add it under orphaned responses node
      return addOrphaned(trans);
    }
    return null;
  }

  /**
   * Add specified dialog to the tree, if it not yet present there.
   *
   * @param dlg Dialog to add. Can not be {@code null}
   */
  private void addToRoot(DlgResource dlg) {
    // If dialog already added as root, return
    if (map(dlg) != null) {
      return;
    }

    final DlgItem item = new DlgItem(this, dlg);
    final int[] childIndices = { rootDialogs.size() };
    final Object[] children = { item };

    rootDialogs.add(item);
    linkedDialogs.put(key(dlg.getName()), dlg);
    for (final StateItem state : item) {
      items.computeIfAbsent(state.getEntry(), e -> new DlgElement()).add(state);
      initState(state);
    }
    dlg.addTableModelListener(this);
    dlg.addPropertyChangeListener(this);

    fireTreeNodesInserted(new TreePath(this), childIndices, children);
  }

  /**
   * Add new state to the tree.
   *
   * @param state Reference to the state, that is not accessible from the root of any dialogue. Can not be {@code null}
   * @return GUI item representing state in the tree
   */
  private StateItem addToRoot(State state) {
    final DlgResource dlg = state.getParent();
    final StateItem item = addState(dlg, findParents(state));
    // If state is not accessible from any dialogue, item is null, so add
    // state under orphaned states node
    return item == null ? addOrphaned(state) : item;
  }

  /**
   * Add new state node under {@link #orphanStates "Orphaned states"} node.
   *
   * @param state Reference to the state, that is not accessible from the root of any dialogue. Can not be {@code null}
   * @return GUI item representing state in the tree
   */
  private StateItem addOrphaned(State state) {
    final StateItem item = new StateItem(state, orphanStates, null);
    final int[] childIndices = { orphanStates.getChildCount() };
    final Object[] children = { item };

    orphanStates.states.add(item);
    items.computeIfAbsent(state, e -> new DlgElement()).add(item);
    initState(item);

    fireTreeNodesInserted(orphanStates.getPath(), childIndices, children);
    return item;
  }

  /**
   * Add new response node under {@link #orphanTrans "Orphaned responses"} node.
   *
   * @param trans Reference to the transition, that is not accessible from the root of any dialogue. Can not be
   *              {@code null}
   * @return GUI item representing transition in the tree
   */
  private TransitionItem addOrphaned(Transition trans) {
    final TransitionItem item = new TransitionItem(trans, orphanTrans, null);
    final int[] childIndices = { orphanTrans.getChildCount() };
    final Object[] children = { item };

    orphanTrans.trans.add(item);
    items.computeIfAbsent(trans, e -> new DlgElement()).add(item);
    initTransition(item);

    fireTreeNodesInserted(orphanTrans.getPath(), childIndices, children);
    return item;
  }

  /**
   * Returns all states from the {@code state} dialog from which specified state is accessible and the state itself.
   *
   * @param state Status for which parent statuses are looked for
   * @return Collection of unique states, which forms queue of parent states
   */
  private Set<State> findParents(State state) {
    final DlgResource dlg = state.getParent();
    // Use LinkedHashSet to return states in child-parent order
    final LinkedHashSet<State> result = new LinkedHashSet<>();
    final ArrayDeque<State> queue = new ArrayDeque<>();
    result.add(state);
    queue.add(state);
    do {
      dlg.findUsages(queue.pop(), t -> dlg.findUsages(t, discoveredUpstreamState -> {
        if (result.add(discoveredUpstreamState)) {
          queue.add(discoveredUpstreamState);
        }
      }));
    } while (!queue.isEmpty());

    return result;
  }

  /**
   * Looks for all specified states in all dialogs and adds all dialogs in which the reference to a state is found to a
   * tree. The method looks for all specified state for one pass. It allows to process a situation when directly from
   * other dialogs nobody refers to a required state, but refer to one of its parents (for example, a state 32 and 33 in
   * DMorte1.dlg in PS:T).
   *
   * @param dialog Dialog, that owns of all {@code states}. Must not be {@code null}
   * @param states List of states to search. Must not be {@code null}
   * @return GUI item representing state in first dialog or {@code null}, if state is not found in any dialogue
   */
  private StateItem addState(DlgResource dialog, Set<State> states) {
    final HashSet<String> checked = new HashSet<>();
    // First look at linked dialogs, for optimization
    for (int i = 0;; ++i) {
      final Transition t = dialog.getTransition(i);
      if (t == null) {
        break;
      }

      final DlgResource dlg = getDialogResource(t.getNextDialog());
      // Transition target within this dialog
      if (dlg == null) {
        continue;
      }

      final StateItem item = map(dlg, states);
      if (item != null) {
        return item;
      }
      // This dialog not contains references to any state
      checked.add(key(dlg.getName()));
    }

    // If not found in linked dialogs, search in all dialogs
    for (final ResourceEntry e : ResourceFactory.getResources("DLG")) {
      final String name = key(e.getResourceName());
      // If this dialog in linkedDialogs, it already checked
      if (checked.contains(name)) {
        continue;
      }

      final Resource resource = ResourceFactory.getResource(e);
      // If resource has DLG extension but not DLG resource
      if (!(resource instanceof DlgResource)) {
        continue;
      }

      final StateItem item = map((DlgResource) resource, states);
      if (item != null) {
        return item;
      }
    }
    return null;
  }

  private StateItem map(DlgResource dlg, Set<State> states) {
    for (final State state : states) {
      if (dlg.findUsages(state, __ -> addToRoot(dlg))) {
        // For some reason just
        // return map(state);
        // not works, but this code work as expected
        for (final State s : states) {
          final StateItem item = (StateItem) map(s);
          if (item != null) {
            return item;
          }
        }
      }
    }
    return null;
  }

  /**
   * Translates dialog to GUI item, that presents at tree root.
   *
   * @param dlg Dialog to find in the tree
   * @return GUI item or {@code null} if dialog not exists in the tree
   */
  private DlgItem map(DlgResource dlg) {
    for (final DlgItem item : rootDialogs) {
      if (item.getDialog() == dlg) {
        return item;
      }
    }
    return null;
  }

  /**
   * Checks that specified GUI item corresponds searched state entry. This method returns {@code true} only for
   * {@link StateItem#getMain main} GUI items.
   *
   * @param queue The queue of transitions containing references to states for check. If specified {@code state} does
   *              not correspond to searched {@code entry} it will be filled with next candidates for check
   * @param state Checked GUI item
   * @param entry Searched entry
   *
   * @return {@code true} if specified GUI item contains reference to searchd entry, {@code false} otherwise
   */
  private boolean checkState(ArrayDeque<TransitionItem> queue, StateItem state, State entry) {
    if (state.getMain() != null) {
      return false;
    }
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
   * Finds GUI item that corresponds specified state. Creates non-existent tree items when necessary.
   *
   * @param dlg   Dialogue in which to perform search
   * @param entry Child struct of the dialog for search
   * @return Tree item that represents state or {@code null} if such state did not exist in the dialog
   */
  private StateItem slowFindState(DlgItem dlg, State entry) {
    final ArrayDeque<TransitionItem> queue = new ArrayDeque<>();
    for (final StateItem state : dlg) {
      if (checkState(queue, state, entry)) {
        return state;
      }
    }

    TransitionItem trans;
    while (true) {
      trans = queue.poll();
      if (trans == null) {
        break;
      }

      initTransition(trans);
      if (trans.nextState != null && checkState(queue, trans.nextState, entry)) {
        return trans.nextState;
      }
    }
    return null;
  }

  /**
   * Finds GUI item that corresponds specified transition. Creates non-existent tree items when necessary.
   *
   * @param dlg   Dialogue in which to perform search
   * @param entry Child struct of the dialog for search
   * @return Tree item that represents transition or {@code null} if such transition did not exist in the dialog
   */
  private TransitionItem slowFindTransition(DlgItem dlg, Transition entry) {
    final ArrayDeque<StateItem> queue = new ArrayDeque<>();
    for (final StateItem state : dlg) {
      if (state.getMain() == null) {
        queue.add(state);
      }
    }

    StateItem state;
    while (true) {
      state = queue.poll();
      if (state == null) {
        break;
      }

      initState(state);
      for (TransitionItem trans : state) {
        if (trans.getMain() != null) {
          continue;
        }
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
   * Returns a dialog resource object based on the specified resource name. Reuses exising DlgResource objects if
   * available.
   */
  private DlgResource getDialogResource(ResourceRef dlgRef) {
    return dlgRef.isEmpty() ? null : linkedDialogs.computeIfAbsent(key(dlgRef.getResourceName()), name -> {
      try {
        return new DlgResource(ResourceFactory.getResourceEntry(name));
      } catch (Exception e) {
        Logger.error(e);
      }
      return null;
    });
  }

  /** Returns key for cache of {@link DlgResource}'s. */
  private static String key(String dlgName) {
    return dlgName.toUpperCase(Locale.ENGLISH);
  }

  /** Adds all available child nodes to the given parent node. */
  private void initState(StateItem state) {
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
   * @param state           State item for which need create additional transition tree items
   * @param firstTransition Index of the first transition in the {@code state} {@link StateItem#getDialog() dialog} that
   *                        need to add, inclusive
   * @param lastTransition  Index of the last transition in the state dialog that need to add, exclusive
   * @param prepend         If {@code true}, then insert child transitions before existing, otherwise after existing
   */
  private void addTransitions(StateItem state, int firstTransition, int lastTransition, boolean prepend) {
    final DlgResource dlg = state.getDialog();
    for (int i = firstTransition; i < lastTransition; ++i) {
      final Transition trans = dlg.getTransition(i);
      final TransitionItem item;
      if (trans == null) {
        item = new BrokenTransitionItem(i, state);
      } else {
        final DlgElement elem = items.computeIfAbsent(trans, e -> new DlgElement());

        item = new TransitionItem(trans, state, elem);
        elem.add(item);
      }
      if (prepend) {
        state.trans.add(i - firstTransition, item);
      } else {
        state.trans.add(item);
      }
    }
  }

  /** Adds all available child nodes to the given parent node. */
  private void initTransition(TransitionItem trans) {
    if (trans.nextState == null) {
      final Transition t = trans.getEntry();
      final DlgResource nextDlg = getDialogResource(t.getNextDialog());

      if (nextDlg != null) {
        final int nextIndex = t.getNextDialogState();
        final State state = nextDlg.getState(nextIndex);
        if (state == null) {
          trans.nextState = new BrokenStateItem(nextDlg, nextIndex, trans);
        } else {
          final DlgElement elem = items.computeIfAbsent(state, e -> new DlgElement());

          trans.nextState = new StateItem(state, trans, elem);
          elem.add(trans.nextState);
        }
      }
    }
  }

  /** Adds all available child nodes to the given parent node. */
  private TreeNode initNode(TreeNode node) {
    if (node.getAllowsChildren()) {
      if (node instanceof StateItem) {
        initState((StateItem) node);
      } else if (node instanceof TransitionItem) {
        initTransition((TransitionItem) node);
      }
    }
    return node;
  }
}
