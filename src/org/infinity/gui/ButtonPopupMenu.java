// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Provides a button component that pops up an associated menu when the button is pressed.
 */
public class ButtonPopupMenu extends JButton {
  public enum Align {
    /** Show the menu below the button. */
    TOP,
    /** Show the menu on top of the button. */
    BOTTOM,
  }

  private static final Comparator<JComponent> MENU_ITEM_COMPARATOR = (item1, item2) -> {
    if (item1 instanceof JMenuItem && item2 instanceof JMenuItem) {
      return ((JMenuItem) item1).getText().compareToIgnoreCase(((JMenuItem) item2).getText());
    } else {
      return 0;
    }
  };

  private final ScrollPopupMenu menu = new ScrollPopupMenu();
  private final PopupListener listener = new PopupListener();
  private final PopupItemListener itemListener = new PopupItemListener();
  private final List<JComponent> items = new ArrayList<>();

  private JMenuItem selected;
  private Align menuAlign;

  /**
   * Constructs a new ButtonPopupMenu control without menu items. Use this constructor in conjunction with
   * {@link #addItem(JMenuItem)} or {@link #addSeparator()}, if you want to have full control over the popup menu
   * creation.
   *
   * @param text Text label for the button.
   */
  public ButtonPopupMenu(String text) {
    this(text, (List<JMenuItem>) null, true, Align.TOP);
  }

  /**
   * Constructs a new ButtonPopupMenu control without menu items. Use this constructor in conjunction with
   * {@link #addItem(JMenuItem)} or {@link #addSeparator()}, if you want to have full control over the popup menu
   * creation.
   *
   * @param text            Text label for the button.
   * @param numVisibleItems The max. number of visible menu items at once.
   */
  public ButtonPopupMenu(String text, int numVisibleItems) {
    this(text, (List<JMenuItem>) null, true, Align.TOP, numVisibleItems);
  }

  /**
   * Constructs a new ButtonPopupMenu control without menu items. Use this constructor in conjunction with
   * {@link #addItem(JMenuItem)} or {@link #addSeparator()}, if you want to have full control over the popup menu
   * creation.
   *
   * @param text  Text label for the button.
   * @param align Indicates where to pop up the menu.
   */
  public ButtonPopupMenu(String text, Align align) {
    this(text, (List<JMenuItem>) null, true, align);
  }

  /**
   * Constructs a new ButtonPopupMenu control without menu items. Use this constructor in conjunction with
   * {@link #addItem(JMenuItem)} or {@link #addSeparator()}, if you want to have full control over the popup menu
   * creation.
   *
   * @param text            Text label for the button.
   * @param align           Indicates where to pop up the menu.
   * @param numVisibleItems The max. number of visible menu items at once.
   */
  public ButtonPopupMenu(String text, Align align, int numVisibleItems) {
    this(text, (List<JMenuItem>) null, true, align, numVisibleItems);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text      Text label for the button.
   * @param menuItems List of menu items. Items will be sorted alphabetically before adding to the button.
   */
  public ButtonPopupMenu(String text, JMenuItem[] menuItems) {
    this(text, menuItems, true, Align.TOP);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text            Text label for the button.
   * @param menuItems       List of menu items. Items will be sorted alphabetically before adding to the button.
   * @param numVisibleItems The max. number of visible menu items at once.
   */
  public ButtonPopupMenu(String text, JMenuItem[] menuItems, int numVisibleItems) {
    this(text, menuItems, true, Align.TOP, numVisibleItems);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text      Text label for the button.
   * @param menuItems List of menu items.
   * @param sorted    Indicates whether to sort items alphabetically before adding to the button.
   * @param align     Indicates where to pop up the menu.
   */
  public ButtonPopupMenu(String text, JMenuItem[] menuItems, boolean sorted, Align align) {
    this(text, menuItems, sorted, align, 0);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text            Text label for the button.
   * @param menuItems       List of menu items.
   * @param sorted          Indicates whether to sort items alphabetically before adding to the button.
   * @param align           Indicates where to pop up the menu.
   * @param numVisibleItems The max. number of visible menu items at once.
   */
  public ButtonPopupMenu(String text, JMenuItem[] menuItems, boolean sorted, Align align, int numVisibleItems) {
    super(text);
    this.menuAlign = align;
    setMenuItems(menuItems, sorted);
    addMouseListener(listener);
    setMaximumVisibleRows(numVisibleItems);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text      Text label for the button.
   * @param menuItems List of menu items. Items will be sorted alphabetically before adding to the button.
   */
  public ButtonPopupMenu(String text, List<JMenuItem> menuItems) {
    this(text, menuItems, true, Align.TOP);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text            Text label for the button.
   * @param menuItems       List of menu items. Items will be sorted alphabetically before adding to the button.
   * @param numVisibleItems The max. number of visible menu items at once.
   */
  public ButtonPopupMenu(String text, List<JMenuItem> menuItems, int numVisibleItems) {
    this(text, menuItems, true, Align.TOP, numVisibleItems);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text      Text label for the button.
   * @param menuItems List of menu items.
   * @param sorted    Indicates whether to sort items alphabetically before adding to the button.
   * @param align     Indicates where to pop up the menu.
   */
  public ButtonPopupMenu(String text, List<JMenuItem> menuItems, boolean sorted, Align align) {
    this(text, menuItems, sorted, align, 0);
  }

  /**
   * Constructs a new ButtonPopupMenu control with the given menu items.
   *
   * @param text            Text label for the button.
   * @param menuItems       List of menu items.
   * @param sorted          Indicates whether to sort items alphabetically before adding to the button.
   * @param align           Indicates where to pop up the menu.
   * @param numVisibleItems The max. number of visible menu items at once.
   */
  public ButtonPopupMenu(String text, List<JMenuItem> menuItems, boolean sorted, Align align, int numVisibleItems) {
    super(text);
    this.menuAlign = align;
    setMenuItems(menuItems, sorted);
    addMouseListener(listener);
    setMaximumVisibleRows(numVisibleItems);
  }

  public JMenuItem getSelectedItem() {
    return selected;
  }

  /**
   * Replaces current list of menu items with the given list.
   *
   * @param menuItems List of menu items. Original item order will be retained.
   */
  public void setMenuItems(JMenuItem[] menuItems) {
    setMenuItems(menuItems, false);
  }

  /**
   * Replaces current list of menu items with the given list.
   *
   * @param menuItems List of menu items.
   * @param sorted    Indicates whether to sort items alphabetically before adding to the button.
   */
  public void setMenuItems(JMenuItem[] menuItems, boolean sorted) {
    List<JMenuItem> list = new ArrayList<>();
    if (menuItems != null) {
      Collections.addAll(list, menuItems);
    }
    setMenuItems(list, sorted);
  }

  /**
   * Replaces current list of menu items with the given list.
   *
   * @param menuItems List of menu items. Items will be sorted alphabetically before adding to the button.
   */
  public void setMenuItems(List<JMenuItem> menuItems) {
    setMenuItems(menuItems, true);
  }

  /**
   * Replaces current list of menu items with the given list.
   *
   * @param menuItems List of menu items.
   * @param sorted    Indicates whether to sort items alphabetically before adding to the button.
   */
  public void setMenuItems(List<JMenuItem> menuItems, boolean sorted) {
    removeAll();
    if (menuItems != null) {
      List<JMenuItem> preparedList;
      if (sorted) {
        preparedList = new ArrayList<>(menuItems);
        preparedList.sort(MENU_ITEM_COMPARATOR);
      } else {
        preparedList = menuItems;
      }
      for (final JMenuItem mi : preparedList) {
        addItem(mi);
      }
    }
  }

  /**
   * Returns an unfiltered read-only list of menu items of any class type.
   */
  public List<? extends JComponent> getItems() {
    return Collections.unmodifiableList(items);
  }

  /**
   * Returns a filtered list of JMenuItem instances. Instances of JSeparator or other class types will be filtered out.
   */
  public List<JMenuItem> getMenuItems() {
    List<JMenuItem> list = new ArrayList<>(items.size());
    for (final JComponent c : items) {
      if (c instanceof JMenuItem) {
        list.add((JMenuItem) c);
      }
    }
    return list;
  }

  /** Returns the alignment of the menu relative to the button. */
  public Align getMenuAlignment() {
    return menuAlign;
  }

  /** Sets how the menu should be aligned relative to the button. */
  public void setMenuAlignment(Align align) {
    this.menuAlign = align;
  }

  /**
   * Returns the JPopupMenu associated with this button.
   */
  public JPopupMenu getPopupMenu() {
    return menu;
  }

  /**
   * Appends the specified menu item to the end of the popup menu.
   *
   * @param item The JMenuItem to add.
   * @return The JMenuItem added.
   */
  public JMenuItem addItem(JMenuItem item) {
    if (item != null) {
      items.add(item);
      menu.add(item);
      item.addMouseListener(itemListener);
      return item;
    }
    return null;
  }

  /**
   * Appends a new separator to the end of the popup menu.
   */
  public void addSeparator() {
    JPopupMenu.Separator sep = new JPopupMenu.Separator();
    items.add(sep);
    menu.add(sep);
  }

  /**
   * Removes the menu item at the specified index from the popup menu.
   */
  @Override
  public void remove(int pos) {
    if (pos < 0 || pos >= items.size()) {
      throw new IllegalArgumentException();
    }
    JComponent c = items.remove(pos);
    c.removeMouseListener(itemListener);
    menu.remove(pos);
  }

  /**
   * Removes all items from the popup menu.
   */
  @Override
  public void removeAll() {
    for (int i = items.size() - 1; i >= 0; i--) {
      remove(i);
    }
  }

  /**
   * Returns the menu item at the specified index.
   *
   * @param pos Index of the menu item
   * @return The menu item as JComponent.
   */
  public JComponent getItemAt(int pos) {
    if (pos < 0 || pos >= items.size()) {
      throw new IllegalArgumentException();
    }
    return items.get(pos);
  }

  /**
   * Returns the position of the item in the popup menu. Returns -1 if not found.
   *
   * @param item The menu item.
   * @return The position of the item in the popup menu, or -1 if not found.
   */
  public int getItemIndex(JMenuItem item) {
    if (item != null) {
      return items.indexOf(item);
    }
    return -1;
  }

  /** Returns the max. number of visible rows before scrollbars are enabled. */
  public int getMaximumVisibleRows() {
    return menu.getMaximumVisibleRows();
  }

  /** Sets the max. number of visible rows before scrollbars are enabled. */
  public void setMaximumVisibleRows(int maximumVisibleRows) {
    menu.setMaximumVisibleRows(maximumVisibleRows);
  }

  private void menuItemSelected(JMenuItem item) {
    if (item.isEnabled()) {
      selected = item;
      // Why won't the following line work?
      fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, selected, ItemEvent.SELECTED));
    }
  }

  // -------------------------- INNER CLASSES --------------------------

  private final class PopupItemListener extends MouseAdapter {
    private PopupItemListener() {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (!e.isPopupTrigger() && ((JMenuItem) e.getSource()).contains(e.getX(), e.getY())) {
        menuItemSelected((JMenuItem) e.getSource());
      }
      menu.setVisible(false);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      JMenuItem item = (JMenuItem) e.getSource();
      item.setArmed(false);
      item.repaint();
    }
  }

  private final class PopupListener extends MouseAdapter {
    private PopupListener() {
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (!e.isPopupTrigger() && e.getComponent().isEnabled()) {
        if (getMenuAlignment() == Align.BOTTOM) {
          menu.show(e.getComponent(), 0, e.getComponent().getSize().height);
        } else {
          menu.show(e.getComponent(), 0, -menu.getPreferredSize().height);
        }
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (!e.isPopupTrigger() && ((JButton) e.getSource()).contains(e.getX(), e.getY())
          && e.getComponent().isEnabled()) {
        if (getMenuAlignment() == Align.BOTTOM) {
          menu.show(e.getComponent(), 0, e.getComponent().getSize().height);
        } else {
          menu.show(e.getComponent(), 0, -menu.getPreferredSize().height);
        }
      } else {
        menu.setVisible(false);
        Component[] components = menu.getComponents();
        for (final Component component : components) {
          if (component instanceof JMenuItem) {
            JMenuItem item = (JMenuItem) component;
            if (item.isArmed()) {
              menuItemSelected(item);
              break;
            }
          }
        }
      }
    }
  }
}
