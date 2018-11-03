// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;

import org.infinity.NearInfinity;
import org.infinity.datatype.ResourceRef;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.updater.Utils;

/**
 * A JLabel-based control which supports either internal game resources or external URLs.
 */
final public class LinkButton extends JLabel implements MouseListener, ActionListener
{
  private static final String CMD_OPEN      = "OPEN";     // open resource in same window
  private static final String CMD_OPEN_NEW  = "OPEN_NEW"; // open resource in new window
  private static final String CMD_BROWSE    = "BROWSE";   // open URL in system-default browser

  private final List<ActionListener> listeners = new ArrayList<>();
  private ResourceEntry entry;
  private String url;
  private boolean isResource;

  /**
   * Creates a link button which points to an internal game resource as specified by the argument.
   * @param resourceRef The game resource as ResourceRef object.
   */
  public LinkButton(ResourceRef resourceRef)
  {
    super();
    setHorizontalAlignment(SwingConstants.LEFT);
    setResource(resourceRef);
  }

  /**
   * Creates a link button which points to an internal game resource as specified by the argument.
   * @param resourceName The game resource as string.
   */
  public LinkButton(String resourceName)
  {
    super();
    setHorizontalAlignment(SwingConstants.LEFT);
    setResource(resourceName);
  }

  /**
   * Creates a link button which points to an external URL.
   * @param text The display name of the link.
   * @param url The actual URL of the link.
   */
  public LinkButton(String text, String url)
  {
    super();
    setHorizontalAlignment(SwingConstants.LEFT);
    setUrl(text, url);
  }

  /** Creates a link from the specified resource reference. */
  public void setResource(ResourceRef resourceRef)
  {
    if (resourceRef != null) {
      setResource(resourceRef, resourceRef.toString());
    } else {
      setResource(null, "");
    }
  }

  /** Attempts to create a link from the specified resource name. */
  public void setResource(String resourceName)
  {
    setResource(null, resourceName);
  }

  // Uses either ResourceRef or String to create a link
  private void setResource(ResourceRef resource, String resourceName)
  {
    isResource = true;
    removeActionListener(this);
    if (resource != null) {
      entry = ResourceFactory.getResourceEntry(resource.getResourceName());
      if (resourceName == null) {
        resourceName = resource.toString();
      }
    } else {
      entry = ResourceFactory.getResourceEntry(resourceName);
      if (resourceName == null) {
        resourceName = "";
      }
    }
    if (entry != null) {
      addActionListener(this);
      setLink(resourceName, entry.getResourceName(), true);
      setEnabled(true);
      setToolTipText(null);
    } else {
      setLink(resourceName, null, false);
      setEnabled(false);
      setToolTipText("Resource not found");
    }
  }

  /** Sets link or label text, depending on arguments. */
  private void setLink(String text, String resource, boolean asLink)
  {
    removeMouseListener(this);
    setCursor(null);

    if (text == null) {
      text = resource;
    }

    if (!asLink) {
      setText(text);
    } else if (resource != null && !resource.isEmpty()) {
      setText("<html><a href=\"" + resource + "\">" + text + "</a></html");
      addMouseListener(this);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    } else {
      setText("");
    }
  }

  /** Creates a link to an external URL. */
  public void setUrl(String text, String url)
  {
    isResource = false;
    if (url == null || url.isEmpty()) {
      url = "about:blank";
    }
    if (text == null || text.isEmpty()) {
      text = url;
    }
    this.url = url;
    addActionListener(this);
    setLink(text, url, true);
    setEnabled(true);
  }

  /** Returns the external link or internal resource entry as string. */
  public String getUrl()
  {
    if (isResource) {
      return entry.toString();
    } else {
      return url;
    }
  }

  //<editor-fold defaultstate="collapsed" desc="ActionListener">
  @Override
  public void actionPerformed(ActionEvent e)
  {
    String cmd = e.getActionCommand();
    if ((cmd == null) || cmd.equals(CMD_OPEN_NEW)) {
      new ViewFrame(((LinkButton)e.getSource()).getTopLevelAncestor(),
                    ResourceFactory.getResource(entry));
    } else if (cmd.equals(CMD_OPEN)) {
      NearInfinity.getInstance().showResourceEntry(entry);
    } else if (cmd.equals(CMD_BROWSE)) {
      try {
        Utils.openWebPage(new URL(getUrl()));
      } catch (Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(((LinkButton)e.getSource()).getTopLevelAncestor(),
                                      "Error opening link in browser.", "Error",
                                      JOptionPane.ERROR_MESSAGE);
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="MouseListener">
  @Override
  public void mouseClicked(MouseEvent e)
  {
    String cmd;
    if (isResource) {
      if ((e.getButton() == MouseEvent.BUTTON2)
       || (e.getButton() == MouseEvent.BUTTON3)) {
        cmd = CMD_OPEN;
      } else {
        cmd = CMD_OPEN_NEW;
      }
    } else {
      cmd = CMD_BROWSE;
    }

    ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, cmd);
    for (final ActionListener l : listeners) {
      l.actionPerformed(event);
    }
  }

  @Override
  public void mousePressed(MouseEvent e)
  {
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }

  @Override
  public void mouseEntered(MouseEvent e)
  {
  }

  @Override
  public void mouseExited(MouseEvent e)
  {
  }
  //</editor-fold>

  public void removeActionListener(ActionListener listener)
  {
    listeners.remove(listener);
  }

  private void addActionListener(ActionListener listener)
  {
    listeners.add(listener);
  }
}

