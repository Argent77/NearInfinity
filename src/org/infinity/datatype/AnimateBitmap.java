// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;

import javax.swing.JButton;

import org.infinity.gui.ViewFrame;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;

/**
 * Provides a button that opens the associated INI or 2DA resource of the selected animation slot.
 */
public class AnimateBitmap extends IdsBitmap implements ActionListener
{
  private final JButton showIni;
  private IdsMap idsMap;
  private boolean useIni;

  public AnimateBitmap(ByteBuffer buffer, int offset, int length, String name)
  {
    super(buffer, offset, length, name, "ANIMATE.IDS");

    if (Profile.isEnhancedEdition() || ResourceFactory.resourceExists("ANISND.IDS")) {
      showIni = new JButton("View/Edit", Icons.getIcon(Icons.ICON_ZOOM_16));
      showIni.setEnabled(false);
      showIni.addActionListener(this);
      addButtons(showIni);

      // Don't show button for games that don't support INI/2DA files
      if (ResourceFactory.resourceExists("ANISND.IDS")) {
        showIni.setToolTipText("Open associated 2DA resource");
        idsMap = IdsMapCache.get("ANISND.IDS");
      }
      if (Profile.isEnhancedEdition()) {
        showIni.setToolTipText("Open associated INI or 2DA resource");
        useIni = true;
      }
    } else {
      showIni = null;
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == showIni) {
      final Long value = getCurrentValue();
      String animRes = value == null ? null : getAnimResource(value.longValue());
      if (animRes != null) {
        ResourceEntry entry = ResourceFactory.getResourceEntry(animRes);
        if (entry != null) {
          new ViewFrame(getListPanel().getTopLevelAncestor(), ResourceFactory.getResource(entry));
        }
      }
    }
  }

  @Override
  protected void listItemChanged()
  {
    if (showIni != null) {
      boolean b = false;
      final Long value = getCurrentValue();
      if (value != null) {
        b = (getAnimResource(value) != null);
      }
      showIni.setEnabled(b);
    }
  }

  private String getAnimResource(long value)
  {
    String animRes = null;

    if (useIni) {
      // checking for INI
      animRes = String.format("%04X.INI", value);
      if (!ResourceFactory.resourceExists(animRes)) {
        animRes = null;
      }
    }

    if (animRes == null && idsMap != null) {
      // checking for 2DA
      IdsMapEntry entry = idsMap.get(value);
      if (entry != null) {
        String[] symbols = entry.getSymbol().split("[ \t]");
        if (symbols.length > 1) {
          animRes = symbols[0].trim() + ".2DA";
          if (!ResourceFactory.resourceExists(animRes)) {
            animRes = null;
          }
        }
      }
    }

    return animRes;
  }
}
