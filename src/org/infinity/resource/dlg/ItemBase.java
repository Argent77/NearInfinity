// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2018 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.util.Enumeration;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.infinity.datatype.StringRef;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.util.StringTable;
import static org.infinity.util.StringTable.Format.NONE;
import static org.infinity.util.StringTable.Format.STRREF_PREFIX;

/** Common base class for node type specific classes. */
abstract class ItemBase implements TreeNode
{
  /** Maximum string length to display. */
  private static final int MAX_LENGTH = 200;

  private final boolean showStrrefs;
  private final boolean showTechInfo;

  public ItemBase()
  {
    this.showStrrefs = BrowserMenuBar.getInstance().showStrrefs();
    this.showTechInfo = BrowserMenuBar.getInstance().showDlgTechInfo();
  }

  /** Returns the dialog resource name. */
  public String getDialogName()
  {
    final DlgResource dlg = getDialog();
    return dlg == null ? "" : dlg.getResourceEntry().getResourceName();
  }

  /**
   * Gets path that represents this node in the tree.
   *
   * @return Path from root to this node
   */
  public TreePath getPath()
  {
    final ItemBase parent = getParent();
    if (parent == null) {
      return new TreePath(this);
    }
    return parent.getPath().pathByAddingChild(this);
  }

  /** Returns the entry of the dialog which this node represents. */
  public abstract TreeItemEntry getEntry();

  /** Returns main item - item from which the tree grows in break cycles mode. */
  public abstract ItemBase getMain();

  /** Returns the dialog resource object. Dialogs can use several dialog resources in one conversation. */
  public abstract DlgResource getDialog();

  /** Returns the icon associated with the item type. */
  public abstract Icon getIcon();

  /**
   * Removes specified child item, returns {@code true} if child removed,
   * {@code false} otherwize.
   *
   * @param child Item to remove. If {@code null} method returns {@code false}
   * @return {@code true} if item is child of this node and was removed, {@code false} otherwize.
   */
  public abstract boolean removeChild(ItemBase child);

  /**
   * Performs the given action for each child of the node until all childrens have
   * been processed or the action throws an exception. Exceptions thrown by the
   * action are relayed to the caller.
   *
   * @param action The action to be performed for each children
   *
   * @throws NullPointerException If the specified action is {@code null}
   */
  public abstract void traverseChildren(Consumer<ItemBase> action);

  //<editor-fold defaultstate="collapsed" desc="TreeNode">
  @Override
  public abstract ItemBase getChildAt(int childIndex);

  @Override
  public abstract ItemBase getParent();

  @Override
  public abstract Enumeration<? extends ItemBase> children();
  //</editor-fold>

  /**
   * Returns string that can be used to display in the tree.
   *
   * @param entry Dialog entry to display
   * @return String for tree item
   */
  protected final String getText(TreeItemEntry entry)
  {
    final String text = entry.hasAssociatedText()
            ? getText(entry.getAssociatedText())
            : "(No text)";
    return showTechInfo ? entry.getName() + ": " + text : text;
  }

  /**
   * Returns string that can be used to display in the tree.
   *
   * @param value Field with reference to string in the {@link StringTable string table}
   * @return String from string table if necessary cut down up to {@link #MAX_LENGTH} characters
   */
  private String getText(StringRef value)
  {
    final String text = StringTable.getStringRef(value.getValue(), showStrrefs ? STRREF_PREFIX : NONE);
    if (text.length() > MAX_LENGTH) {
      return text.substring(0, MAX_LENGTH) + "...";
    }
    return text;
  }
}

/** Auxiliary class, being the parent for states, for a type safety. */
abstract class StateOwnerItem extends ItemBase
{
  //<editor-fold defaultstate="collapsed" desc="TreeNode">
  @Override
  public abstract StateItem getChildAt(int childIndex);

  @Override
  public abstract Enumeration<? extends StateItem> children();
  //</editor-fold>
}