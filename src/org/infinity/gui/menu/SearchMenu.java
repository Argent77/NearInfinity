// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.menu;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.regex.Pattern;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.infinity.gui.ChildFrame;
import org.infinity.gui.StringLookup;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.search.DialogSearcher;
import org.infinity.search.SearchFrame;
import org.infinity.search.SearchResource;
import org.infinity.search.TextResourceSearcher;
import org.infinity.search.advanced.AdvancedSearch;
import org.infinity.util.StringTable;

/**
 * Handles Search menu items for the {@link BrowserMenuBar}.
 */
public class SearchMenu extends JMenu implements BrowserSubMenu, ActionListener {
  private static final String[] TEXTSEARCH = { "2DA", "BCS", "DLG", "IDS", "INI", "LUA" };

  private final BrowserMenuBar menuBar;

  private final JMenu textSearchMenu;
  private final JMenuItem searchString;
  private final JMenuItem searchFile;
  private final JMenuItem searchResource;
  private final JMenuItem advancedSearch;

  public SearchMenu(BrowserMenuBar parent) {
    super("Search");
    setMnemonic(KeyEvent.VK_S);

    menuBar = parent;

    searchString = BrowserMenuBar.makeMenuItem("StringRef...", KeyEvent.VK_S, Icons.ICON_FIND_16.getIcon(),
        KeyEvent.VK_L, this);
    add(searchString);
    searchFile = BrowserMenuBar.makeMenuItem("CRE/ITM/SPL/STO/ARE...", KeyEvent.VK_C, Icons.ICON_FIND_16.getIcon(),
        KeyEvent.VK_F, this);
    add(searchFile);

    JMenu menuAdvanced = new JMenu("Advanced Search");
    menuAdvanced.setIcon(Icons.ICON_FIND_16.getIcon());
    menuAdvanced.setMnemonic('a');
    add(menuAdvanced);

    advancedSearch = BrowserMenuBar.makeMenuItem("Advanced search...", KeyEvent.VK_A, Icons.ICON_FIND_16.getIcon(), -1,
        this);
    advancedSearch
        .setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, BrowserMenuBar.getCtrlMask() | ActionEvent.ALT_MASK));
    advancedSearch.setToolTipText("A powerful and highly flexible search for structured resources of all kinds.");
    menuAdvanced.add(advancedSearch);
    searchResource = BrowserMenuBar.makeMenuItem("Legacy extended search...", KeyEvent.VK_X,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    searchResource
        .setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, BrowserMenuBar.getCtrlMask() | ActionEvent.ALT_MASK));
    searchResource.setToolTipText("The original \"Extended Search\".");
    menuAdvanced.add(searchResource);

    textSearchMenu = new JMenu("Text Search");
    textSearchMenu.setIcon(Icons.ICON_EDIT_16.getIcon());
    for (final String type : TEXTSEARCH) {
      JMenuItem textSearch = new JMenuItem(type);
      textSearch.addActionListener(this);
      textSearch.setActionCommand(type);
      textSearchMenu.add(textSearch);
    }
    add(textSearchMenu);
  }

  public void gameLoaded() {
    // Enable INI search only if the game is supporting it
    for (int i = 0, count = textSearchMenu.getMenuComponentCount(); i < count; i++) {
      if (textSearchMenu.getMenuComponent(i) instanceof JMenuItem) {
        JMenuItem mi = (JMenuItem) textSearchMenu.getMenuComponent(i);
        if ("INI".equals(mi.getText())) {
          mi.setEnabled(Profile.getProperty(Profile.Key.IS_SUPPORTED_INI));
        } else if ("LUA".equals(mi.getText())) {
          mi.setEnabled(Profile.getProperty(Profile.Key.IS_SUPPORTED_LUA));
        }
      }
    }

    // String table may not be available
    searchString.setEnabled(StringTable.isAvailable());
  }

  @Override
  public BrowserMenuBar getMenuBar() {
    return menuBar;
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == searchString) {
      ChildFrame.show(StringLookup.class, StringLookup::new);
    } else if (event.getSource() == searchFile) {
      ChildFrame.show(SearchFrame.class, SearchFrame::new);
    } else if (event.getSource() == searchResource) {
      ChildFrame.show(SearchResource.class, SearchResource::new);
    } else if (event.getSource() == advancedSearch) {
      ChildFrame.show(AdvancedSearch.class, AdvancedSearch::new);
    } else {
      for (final String type : TEXTSEARCH) {
        if (event.getActionCommand().equals(type)) {
          if (type.equals("DLG")) {
            new DialogSearcher(ResourceFactory.getResources(type), getTopLevelAncestor());
          } else if (type.equals("BCS")) {
            // include party scripts
            final Pattern pattern = Pattern.compile("^.*\\.BC?S$", Pattern.CASE_INSENSITIVE);
            new TextResourceSearcher(ResourceFactory.getResources(pattern), getTopLevelAncestor());
          } else {
            new TextResourceSearcher(ResourceFactory.getResources(type), getTopLevelAncestor());
          }
          return;
        }
      }
    }
  }
}
