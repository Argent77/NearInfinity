// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.lang.reflect.InvocationTargetException;

import javax.swing.JTree;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

//-------------------------- INNER CLASSES --------------------------

/** Applies expand or collapse operations on a set of dialog tree nodes in a background task. */
class TreeWorker extends SwingWorker<Void, Void>
{
  /** Path that must be collapsed or expanded. */
  private final TreePath path;
  /** If {@code true}, requested expanding of the tree, otherwise collapsing. */
  private final boolean expand;

  /** Tree on which operation is performed. */
  private final JTree dlgTree;
  /** Progress that shows if operation takes a long time (very deep tree). */
  private final ProgressMonitor progress;

  public TreeWorker(JTree dlgTree, TreePath path, boolean expand)
  {
    this.dlgTree = dlgTree;
    this.path = path;
    this.expand = expand;
    final String msg = expand ? "Expanding nodes" : "Collapsing nodes";
    progress = new ProgressMonitor(dlgTree.getTopLevelAncestor(), msg, "This may take a while...", 0, 1);
    progress.setMillisToDecideToPopup(250);
    progress.setMillisToPopup(1000);
    progress.setProgress(0);
  }

  @Override
  protected Void doInBackground() throws Exception
  {
    try {
      if (expand) {
        expandNode(path);
      } else {
        collapseNode(path);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  protected void done() { progress.close(); }

  /** Advances the progress bar by one unit. May display a short notice after a while. */
  private void advanceProgress()
  {
    final int current = progress.getMaximum();
    progress.setMaximum(current + 1);
    if (current % 100 == 0) {
      progress.setNote(String.format("Processing node %d", current));
    }
    progress.setProgress(current);
  }

  /** Expands all children and their children of the given path. */
  private void expandNode(final TreePath path)
  {
    if (progress.isCanceled()) return;

    final ItemBase node = (ItemBase)path.getLastPathComponent();

    // Do not try expand recursive structures
    if (node.getMain() != null) return;

    advanceProgress();
    if (!dlgTree.isExpanded(path)) {
      try {
        SwingUtilities.invokeAndWait(() -> dlgTree.expandPath(path));
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }

    // Use access via model because it properly initializes items
    final TreeModel model = dlgTree.getModel();
    for (int i = 0, count = model.getChildCount(node); i < count; ++i) {
      expandNode(path.pathByAddingChild(model.getChild(node, i)));
    }
  }

  /** Collapses all children and their children of the given path. */
  private void collapseNode(final TreePath path)
  {
    if (progress.isCanceled()) return;
    advanceProgress();

    final ItemBase node = (ItemBase)path.getLastPathComponent();

    // Do not try collapse recursive structures
    // This will collapse all main nodes (even under already collapsed nodes)
    // and will not collapse non-main nodes under collapsed nodes (but still
    // collapse non-main nodes under expanded nodes)
    if (node.getMain() != null && dlgTree.isCollapsed(path)) return;

    // Use access via model because it properly initializes items
    final TreeModel model = dlgTree.getModel();
    for (int i = 0, count = model.getChildCount(node); i < count; ++i) {
      collapseNode(path.pathByAddingChild(model.getChild(node, i)));
    }

    try {
      SwingUtilities.invokeAndWait(() -> dlgTree.collapsePath(path));
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }
}
