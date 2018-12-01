// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.wmp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.infinity.NearInfinity;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.SectionCount;
import org.infinity.datatype.SectionOffset;
import org.infinity.datatype.StringRef;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.gui.ViewerUtil.ListValueRenderer;
import org.infinity.gui.ViewerUtil.StructListPanel;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.graphics.BamDecoder;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.MosDecoder;
import org.infinity.resource.graphics.BamDecoder.BamControl;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Misc;
import org.infinity.util.StringTable;
import org.infinity.util.io.StreamUtils;

public class ViewerMap extends JPanel
{
  /** Needed to determine map edges to travel from/to. */
  private enum Direction { NORTH, WEST, SOUTH, EAST }

  private final JPopupMenu pmOptions = new JPopupMenu("Options");
  private final JMenuItem miExportMap = new JMenuItem("Export as PNG...");
  private final JCheckBoxMenuItem miShowIcons = new JCheckBoxMenuItem("Show all map icons", true);
  private final JCheckBoxMenuItem miShowIconLabels = new JCheckBoxMenuItem("Show icons labels", true);
  private final JCheckBoxMenuItem miShowDistances = new JCheckBoxMenuItem("Show travel distances", false);
  private final JCheckBoxMenuItem miScaling =
      new JCheckBoxMenuItem("Scale map icons", Profile.getGame() == Profile.Game.PSTEE);
  private final BufferedImage iconDot;
  private final Listeners listeners = new Listeners();
  private final MapEntry mapEntry;

  private RenderCanvas rcMap;
  private BufferedImage mapOrig;
  private BamDecoder mapIcons;
  private BamControl mapIconsCtrl;
  private float mapScaleX, mapScaleY;
  private StructListPanel listPanel;
  private BufferedImage dotBackup;
  private int dotX, dotY;
  private JLabel lInfoSize, lInfoPos;

  ViewerMap(MapEntry wmpMap)
  {
    super();
    WindowBlocker.blockWindow(true);
    try {
      mapEntry = wmpMap;

      // creating marker for selected map icon
      iconDot = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = iconDot.createGraphics();
      g.setColor(Color.RED);
      g.fillRect(0, 0, iconDot.getWidth(), iconDot.getHeight());
      g.setColor(Color.YELLOW);
      g.drawRect(0, 0, iconDot.getWidth() - 1, iconDot.getHeight() - 1);
      g.dispose();
      g = null;

      dotBackup = new BufferedImage(iconDot.getWidth(), iconDot.getHeight(), iconDot.getType());
      dotX = dotY = -1;

      miExportMap.setMnemonic('x');
      miExportMap.addActionListener(listeners);
      miShowIcons.setMnemonic('i');
      miShowIcons.addActionListener(listeners);
      miShowIconLabels.setMnemonic('l');
      miShowIconLabels.addActionListener(listeners);
      miShowDistances.setMnemonic('d');
      miShowDistances.addActionListener(listeners);
      miScaling.setMnemonic('s');
      miScaling.addActionListener(listeners);
      miScaling.setToolTipText("Scales map icon locations according to the worldmap's scaling factor. (Needed for some games)");
      pmOptions.add(miExportMap);
      pmOptions.addSeparator();
      pmOptions.add(miShowIcons);
      pmOptions.add(miShowIconLabels);
      pmOptions.add(miShowDistances);
      pmOptions.add(miScaling);

      try {
        mapIcons = null;
        ResourceRef iconRef = (ResourceRef)wmpMap.getAttribute(MapEntry.WMP_MAP_ICONS);
        if (iconRef != null) {
          ResourceEntry iconEntry = ResourceFactory.getResourceEntry(iconRef.getResourceName());
          if (iconEntry != null) {
            mapIcons = BamDecoder.loadBam(iconEntry);
            mapIconsCtrl = mapIcons.createControl();
          }
        }

        if (ResourceFactory.resourceExists(((ResourceRef)wmpMap.getAttribute(MapEntry.WMP_MAP_RESREF)).getResourceName())) {
          mapOrig = loadMap();
          rcMap = new RenderCanvas(ColorConvert.cloneImage(mapOrig));
          rcMap.addMouseListener(listeners);
          rcMap.addMouseMotionListener(listeners);

          int mapTargetWidth = ((IsNumeric)mapEntry.getAttribute(MapEntry.WMP_MAP_WIDTH)).getValue();
          if (mapTargetWidth <= 0) { mapTargetWidth = mapOrig.getWidth(); }
          int mapTargetHeight = ((IsNumeric)mapEntry.getAttribute(MapEntry.WMP_MAP_HEIGHT)).getValue();
          if (mapTargetHeight <= 0) { mapTargetHeight = mapOrig.getHeight(); }
          mapScaleX = (float)mapOrig.getWidth() / (float)mapTargetWidth;
          mapScaleY = (float)mapOrig.getHeight() / (float)mapTargetHeight;

          listPanel = (StructListPanel)ViewerUtil.makeListPanel("Areas", wmpMap, AreaEntry.class, AreaEntry.WMP_AREA_CURRENT,
                                                                new WmpAreaListRenderer(mapIcons), listeners);
          JScrollPane mapScroll = new JScrollPane(rcMap);
          mapScroll.getVerticalScrollBar().setUnitIncrement(16);
          mapScroll.getHorizontalScrollBar().setUnitIncrement(16);
          mapScroll.setBorder(BorderFactory.createEmptyBorder());

          JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapScroll, listPanel);
          split.setDividerLocation(NearInfinity.getInstance().getWidth() - 475);
          setLayout(new BorderLayout());
          add(split, BorderLayout.CENTER);

          JPanel pInfo = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
          lInfoSize = new JLabel(String.format("Worldmap size: %d x %d pixels", mapOrig.getWidth(), mapOrig.getHeight()));
          lInfoSize.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
          lInfoPos = new JLabel();
          lInfoPos.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
          pInfo.add(lInfoSize);
          pInfo.add(lInfoPos);
          add(pInfo, BorderLayout.SOUTH);

        } else {
          rcMap = null;
          mapOrig = null;
        }
      } catch (Throwable t) {
        t.printStackTrace();
      }

      // applying preselected overlays
      showOverlays(miShowIcons.isSelected(), miShowIconLabels.isSelected(), miShowDistances.isSelected());

    } finally {
      WindowBlocker.blockWindow(false);
    }
  }

  /** Returns current map entry structure. */
  private MapEntry getEntry()
  {
    return mapEntry;
  }

  /** Load and return map graphics. */
  private BufferedImage loadMap()
  {
    String mapName = ((ResourceRef)getEntry().getAttribute(MapEntry.WMP_MAP_RESREF)).getResourceName();
    if (ResourceFactory.resourceExists(mapName)) {
      MosDecoder mos = MosDecoder.loadMos(ResourceFactory.getResourceEntry(mapName));
      if (mos != null) {
        return (BufferedImage)mos.getImage();
      }
    }
    return null;
  }

  /** Show popup menu. */
  private void showPopup(Component invoker, int x, int y)
  {
    pmOptions.show(invoker, x, y);
  }

  /** Display either or both map icons and travel distances. */
  private void showOverlays(boolean showIcons, boolean showIconLabels, boolean showDistances)
  {
    resetMap();
    if (showIcons) {
      showMapIcons();
      if (showIconLabels) {
        showMapIconLabels();
      }
      if (showDistances) {
        showMapDistances(listPanel.getList().getSelectedIndex());
      }
    }
    showDot((AreaEntry)listPanel.getList().getSelectedValue(), false);
    rcMap.repaint();
  }

  /** Draws map icons onto the map. */
  private void showMapIcons()
  {
    if (mapIcons != null) {
      Graphics2D g = ((BufferedImage)rcMap.getImage()).createGraphics();
      try {
        for (int i = 0, count = listPanel.getList().getModel().getSize(); i < count; i++) {
          AreaEntry area = getAreaEntry(i, true);
          if (area != null) {
            int iconIndex = ((DecNumber)area.getAttribute(AreaEntry.WMP_AREA_ICON_INDEX)).getValue();
            int frameIndex = mapIconsCtrl.cycleGetFrameIndexAbsolute(iconIndex, 0);
            if (frameIndex >= 0) {
              final Image mapIcon = mapIcons.frameGet(mapIconsCtrl, frameIndex);
              Point p = getAreaEntryPosition(area, isScaling());
              int width = mapIcons.getFrameInfo(frameIndex).getWidth();
              int height = mapIcons.getFrameInfo(frameIndex).getHeight();
              int cx = mapIcons.getFrameInfo(frameIndex).getCenterX();
              int cy = mapIcons.getFrameInfo(frameIndex).getCenterY();
              p.x -= cx;
              p.y -= cy;
              g.drawImage(mapIcon, p.x, p.y, p.x+width, p.y+height, 0, 0, width, height, null);
            }
          }
        }
      } finally {
        g.dispose();
        g = null;
      }
    }
  }

  /** Draws map icon labels onto the map. */
  private void showMapIconLabels()
  {
    if (mapIcons != null) {
      Graphics2D g = ((BufferedImage)rcMap.getImage()).createGraphics();
      try {
        g.setFont(Misc.getScaledFont(g.getFont()));
        g.setFont(g.getFont().deriveFont(g.getFont().getSize2D()*0.9f));
        final Color WHITE_BLENDED = new Color(0xc0ffffff, true);
        for (int i = 0, count = listPanel.getList().getModel().getSize(); i < count; i++) {
          AreaEntry area = getAreaEntry(i, true);
          if (area != null) {
            int iconIndex = ((DecNumber)area.getAttribute(AreaEntry.WMP_AREA_ICON_INDEX)).getValue();
            int frameIndex = mapIconsCtrl.cycleGetFrameIndexAbsolute(iconIndex, 0);
            if (frameIndex >= 0) {
              // getting area name
              int strref = ((StringRef)area.getAttribute(AreaEntry.WMP_AREA_NAME)).getValue();
              if (strref < 0) {
                strref = ((StringRef)area.getAttribute(AreaEntry.WMP_AREA_TOOLTIP)).getValue();
              }
              String mapName = (strref >= 0) ? StringTable.getStringRef(strref) : null;
              if (mapName != null && mapName.trim().length() == 0) {
                mapName = null;
              }

              // getting area code
              String mapCode = ((ResourceRef)area.getAttribute(AreaEntry.WMP_AREA_CURRENT)).getResourceName();
              if (ResourceFactory.resourceExists(mapCode)) {
                mapCode = mapCode.replace(".ARE", "");
              } else {
                mapCode = "";
              }

              Point p = getAreaEntryPosition(area, isScaling());
              int width = mapIcons.getFrameInfo(frameIndex).getWidth();
              int height = mapIcons.getFrameInfo(frameIndex).getHeight();
              int cx = mapIcons.getFrameInfo(frameIndex).getCenterX();
              int cy = mapIcons.getFrameInfo(frameIndex).getCenterY();
              p.x -= cx;
              p.y -= cy;

              // printing label
              if (!mapCode.isEmpty()) {
                String[] labels = null;
                if (mapName != null) {
                  labels = new String[2];
                  labels[0] = mapName;
                  labels[1] = "(" + mapCode + ")";
                } else {
                  labels = new String[1];
                  labels[0] = mapCode;
                }

                int ofsY = 0;
                for (final String label: labels) {
                  Rectangle2D rectText = g.getFont().getStringBounds(label, g.getFontRenderContext());
                  int boxWidth = rectText.getBounds().width;
                  int boxHeight = rectText.getBounds().height;
                  int textX = p.x + (width - boxWidth) / 2;
                  int textY = p.y + height + ofsY;

                  g.setColor(WHITE_BLENDED);
                  g.fillRect(textX - 2, textY, boxWidth + 4, boxHeight);

                  g.setColor(Color.BLACK);
                  LineMetrics lm = g.getFont().getLineMetrics(label, g.getFontRenderContext());
                  g.drawString(label, (float)textX, (float)textY + lm.getAscent() + lm.getLeading());
                  ofsY += boxHeight;
                }
              }
            }
          }
        }
      } finally {
        g.dispose();
        g = null;
      }
    }
  }

  /** Displays all map distances from the specified area (by index). */
  private void showMapDistances(int areaIndex)
  {
    AreaEntry area = getAreaEntry(areaIndex, true);
    if (area != null) {
      final Direction[] srcDir = { Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST };
      final Color[] dirColor = { Color.GREEN, Color.RED, Color.CYAN, Color.YELLOW };
      final int[] links = new int[8];
      final int linkSize = 216;   // size of a single area link structure
      int ofsLinkBase = ((SectionOffset)getEntry().getAttribute(MapEntry.WMP_MAP_OFFSET_AREA_LINKS)).getValue();

      links[0] = ((DecNumber)area.getAttribute(AreaEntry.WMP_AREA_FIRST_LINK_NORTH)).getValue();
      links[1] = ((SectionCount)area.getAttribute(AreaEntry.WMP_AREA_NUM_LINKS_NORTH)).getValue();
      links[2] = ((DecNumber)area.getAttribute(AreaEntry.WMP_AREA_FIRST_LINK_WEST)).getValue();
      links[3] = ((SectionCount)area.getAttribute(AreaEntry.WMP_AREA_NUM_LINKS_WEST)).getValue();
      links[4] = ((DecNumber)area.getAttribute(AreaEntry.WMP_AREA_FIRST_LINK_SOUTH)).getValue();
      links[5] = ((SectionCount)area.getAttribute(AreaEntry.WMP_AREA_NUM_LINKS_SOUTH)).getValue();
      links[6] = ((DecNumber)area.getAttribute(AreaEntry.WMP_AREA_FIRST_LINK_EAST)).getValue();
      links[7] = ((SectionCount)area.getAttribute(AreaEntry.WMP_AREA_NUM_LINKS_EAST)).getValue();
      for (int dir = 0; dir < srcDir.length; dir++) {
        Direction curDir = srcDir[dir];
        Point ptOrigin = getMapIconCoordinate(areaIndex, curDir, true);
        for (int dirIndex = 0, dirCount = links[dir * 2 + 1]; dirIndex < dirCount; dirIndex++) {
          int ofsLink = ofsLinkBase + (links[dir * 2] + dirIndex)*linkSize;
          AreaLink destLink = (AreaLink)area.getAttribute(ofsLink, false);

          if (destLink != null) {
            int dstAreaIndex = ((DecNumber)destLink.getAttribute(AreaLink.WMP_LINK_TARGET_AREA)).getValue();
            Flag flag = (Flag)destLink.getAttribute(AreaLink.WMP_LINK_DEFAULT_ENTRANCE);
            Direction dstDir = Direction.NORTH;
            if (flag.isFlagSet(1)) {
              dstDir = Direction.EAST;
            } else if (flag.isFlagSet(2)) {
              dstDir = Direction.SOUTH;
            } else if (flag.isFlagSet(3)) {
              dstDir = Direction.WEST;
            }
            Point ptTarget = getMapIconCoordinate(dstAreaIndex, dstDir, false);

            // checking for random encounters during travels
            boolean hasRandomEncounters = false;
            if (((DecNumber)destLink.getAttribute(AreaLink.WMP_LINK_RANDOM_ENCOUNTER_PROBABILITY)).getValue() > 0) {
              for (int rnd = 1; rnd < 6; rnd++) {
                String rndArea = ((ResourceRef)destLink
                    .getAttribute(String.format(AreaLink.WMP_LINK_RANDOM_ENCOUNTER_AREA_FMT, rnd)))
                    .getResourceName();
                if (ResourceFactory.resourceExists(rndArea)) {
                  hasRandomEncounters = true;
                  break;
                }
              }
            }

            Graphics2D g = ((BufferedImage)rcMap.getImage()).createGraphics();
            g.setFont(g.getFont().deriveFont(g.getFont().getSize2D()*0.8f));
            try {
              // drawing line
              g.setColor(dirColor[dir]);
              if (hasRandomEncounters) {
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1.0f,
                            new float[]{6.0f, 4.0f}, 0.0f));
              } else {
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
              }
              g.drawLine(ptOrigin.x, ptOrigin.y, ptTarget.x, ptTarget.y);

              // printing travel time (in hours)
              String duration = String.format("%d h", ((DecNumber)destLink.getAttribute(AreaLink.WMP_LINK_DISTANCE_SCALE)).getValue() * 4);
              LineMetrics lm = g.getFont().getLineMetrics(duration, g.getFontRenderContext());
              Rectangle2D rectText = g.getFont().getStringBounds(duration, g.getFontRenderContext());
              int textX = ptOrigin.x + ((ptTarget.x - ptOrigin.x) - rectText.getBounds().width) / 2;
              int textY = ptOrigin.y + ((ptTarget.y - ptOrigin.y) - rectText.getBounds().height) / 2;
              int textWidth = rectText.getBounds().width;
              int textHeight = rectText.getBounds().height;
              g.setColor(Color.LIGHT_GRAY);
              g.fillRect(textX - 2, textY, textWidth + 4, textHeight);
              g.setColor(Color.BLUE);
              g.drawString(duration, (float)textX, (float)textY + lm.getAscent() + lm.getLeading());
            } finally {
              g.dispose();
              g = null;
            }
          }
        }
      }
    }
  }

  /** Returns a properly scaled map icon position. */
  private Point getAreaEntryPosition(AreaEntry areaEntry, boolean scaled)
  {
    Point p = new Point();
    if (areaEntry != null) {
      p.x = ((IsNumeric)areaEntry.getAttribute(AreaEntry.WMP_AREA_COORDINATE_X)).getValue();
      p.y = ((IsNumeric)areaEntry.getAttribute(AreaEntry.WMP_AREA_COORDINATE_Y)).getValue();
      if (scaled) {
        p.x = (int)(p.x * mapScaleX);
        p.y = (int)(p.y * mapScaleY);
      }
    }
    return p;
  }

  /** Returns a pixel coordinate for one of the edges of the specified area icon. */
  private Point getMapIconCoordinate(int areaIndex, Direction dir, boolean byPanel)
  {
    AreaEntry area = getAreaEntry(areaIndex, byPanel);
    if (area != null) {
      Point p = getAreaEntryPosition(area, isScaling());
      int iconIndex = ((DecNumber)area.getAttribute(AreaEntry.WMP_AREA_ICON_INDEX)).getValue();
      int frameIndex = mapIconsCtrl.cycleGetFrameIndexAbsolute(iconIndex, 0);
      int width, height;
      if (frameIndex >= 0) {
        width = mapIcons.getFrameInfo(frameIndex).getWidth();
        height = mapIcons.getFrameInfo(frameIndex).getHeight();
        p.x -= mapIcons.getFrameInfo(frameIndex).getCenterX();
        p.y -= mapIcons.getFrameInfo(frameIndex).getCenterY();
      } else {
        width = height = 0;
      }
      Point retVal = new Point();
      switch (dir) {
        case NORTH:
          retVal.x = p.x + (width / 2);
          retVal.y = p.y;
          break;
        case WEST:
          retVal.x = p.x;
          retVal.y = p.y + (height / 2);
          break;
        case SOUTH:
          retVal.x = p.x + (width / 2);
          retVal.y = p.y + height - 1;
          break;
        case EAST:
          retVal.x = p.x + width - 1;
          retVal.y = p.y + (height / 2);
          break;
      }
      return retVal;
    }
    return null;
  }

  /** Returns area structure of specified item index. */
  private AreaEntry getAreaEntry(int index, boolean byPanel)
  {
    AreaEntry retVal = null;
    if (byPanel) {
      if (index >= 0 && index < listPanel.getList().getModel().getSize()) {
        retVal = (AreaEntry)listPanel.getList().getModel().getElementAt(index);
      }
    } else {
      if (index >= 0 && index < ((IsNumeric)mapEntry.getAttribute(MapEntry.WMP_MAP_NUM_AREAS)).getValue()) {
        StructEntry e = mapEntry.getAttribute(AreaEntry.WMP_AREA + " " + index);
        if (e instanceof AreaEntry) {
          retVal = (AreaEntry)e;
        }
      }
    }
    return retVal;
  }

  /** Show "dot" on specified map icon, optionally restore background graphics. */
  private void showDot(AreaEntry entry, boolean restore)
  {
    if (restore) {
      restoreDot();
    }
    if (entry != null) {
      storeDot(entry);
      int x = ((DecNumber)entry.getAttribute(AreaEntry.WMP_AREA_COORDINATE_X)).getValue();
      x = (int)(x * mapScaleX);
      int y = ((DecNumber)entry.getAttribute(AreaEntry.WMP_AREA_COORDINATE_Y)).getValue();
      y = (int)(y * mapScaleY);
      int width = iconDot.getWidth();
      int height = iconDot.getHeight();
      int xofs = width / 2;
      int yofs = height / 2;
      Graphics2D g = ((BufferedImage)rcMap.getImage()).createGraphics();
      try {
        g.setComposite(AlphaComposite.Src);
        g.drawImage(iconDot, x-xofs, y-yofs, x-xofs+width, y-yofs+height, 0, 0, width, height, null);
      } finally {
        g.dispose();
        g = null;
      }
    }
  }

  /** Stores background graphics of "dot". */
  private void storeDot(AreaEntry entry)
  {
    if (entry != null) {
      int x = ((DecNumber)entry.getAttribute(AreaEntry.WMP_AREA_COORDINATE_X)).getValue();
      x = (int)(x * mapScaleX);
      int y = ((DecNumber)entry.getAttribute(AreaEntry.WMP_AREA_COORDINATE_Y)).getValue();
      y = (int)(y * mapScaleY);
      int width = dotBackup.getWidth();
      int height = dotBackup.getHeight();
      int xofs = width / 2;
      int yofs = height / 2;
      Graphics2D g = dotBackup.createGraphics();
      try {
        g.setComposite(AlphaComposite.Src);
        g.drawImage(rcMap.getImage(), 0, 0, width, height, x-xofs, y-yofs, x-xofs+width, y-yofs+height, null);
        dotX = x-xofs;
        dotY = y-yofs;
      } finally {
        g.dispose();
        g = null;
      }
    }
  }

  /** Restores background graphics of "dot". */
  private void restoreDot()
  {
    if (dotX != -1 && dotY != -1) {
      int x = dotX;
      int y = dotY;
      int width = dotBackup.getWidth();
      int height = dotBackup.getHeight();
      Graphics2D g = ((BufferedImage)rcMap.getImage()).createGraphics();
      try {
        g.setComposite(AlphaComposite.Src);
        g.drawImage(dotBackup, x, y, x+width, y+height, 0, 0, width, height, null);
        dotX = -1;
        dotY = -1;
      } finally {
        g.dispose();
        g = null;
      }
    }
  }

  /** Attempts to restore the whole map graphics. */
  private void resetMap()
  {
    Graphics2D g = ((BufferedImage)rcMap.getImage()).createGraphics();
    try {
      g.drawImage(mapOrig, 0, 0, null);
    } finally {
      g.dispose();
      g = null;
    }
  }

  /** Returns whether to apply map scaling factor. */
  private boolean isScaling()
  {
    return miScaling.isSelected();
  }

  /** Shows specified coordinates as text info. Hides display for negative coordinates. */
  private void updateCursorInfo(int x, int y)
  {
    if (lInfoPos != null) {
      if (x >= 0 && y >= 0) {
        lInfoPos.setText(String.format("Cursor at (%d, %d)", x, y));
      } else {
        lInfoPos.setText("");
      }
    }
  }

  /** Exports current map to PNG. */
  private void exportMap()
  {
    final Window wnd = SwingUtilities.getWindowAncestor(this);
    WindowBlocker.blockWindow(wnd, true);

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run()
      {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        boolean bRet = false;
        try {
          restoreDot();
          BufferedImage srcImage = (BufferedImage)rcMap.getImage();
          BufferedImage dstImage = ColorConvert.createCompatibleImage(srcImage.getWidth(),
                                                                      srcImage.getHeight(),
                                                                      srcImage.getTransparency());
          Graphics2D g = dstImage.createGraphics();
          g.drawImage(srcImage, 0, 0, null);
          g.dispose();
          srcImage = null;
          bRet = ImageIO.write(dstImage, "png", os);
          dstImage.flush();
          dstImage = null;
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          showDot((AreaEntry)listPanel.getList().getSelectedValue(), false);
          WindowBlocker.blockWindow(wnd, false);
        }
        if (bRet) {
          ResourceEntry re = ((AbstractStruct)getEntry().getParent()).getResourceEntry();
          String fileName = re.getResourceName().toUpperCase(Locale.US).replace(".WMP", ".PNG");
          ResourceFactory.exportResource(re, StreamUtils.getByteBuffer(os.toByteArray()), fileName, wnd);
        } else {
          JOptionPane.showMessageDialog(wnd, "Error while exporting map as graphics.", "Error",
                                        JOptionPane.ERROR_MESSAGE);
        }
      }
    });
  }

//-------------------------- INNER CLASSES --------------------------

  private class Listeners implements ActionListener, MouseListener, MouseMotionListener, ListSelectionListener
  {
    //--------------------- Begin Interface ActionListener ---------------------

    @Override
    public void actionPerformed(ActionEvent e)
    {
      if (e.getSource() == miShowIcons) {
        try {
          WindowBlocker.blockWindow(true);
          if (!miShowIcons.isSelected() && miShowDistances.isSelected()) {
            miShowDistances.setSelected(false);
          }
          showOverlays(miShowIcons.isSelected(), miShowIconLabels.isSelected(), miShowDistances.isSelected());
        } finally {
          WindowBlocker.blockWindow(false);
        }
      } else if (e.getSource() == miShowIconLabels) {
        WindowBlocker.blockWindow(true);
        try {
          if (miShowIconLabels.isSelected() && !miShowIcons.isSelected()) {
            miShowIcons.setSelected(true);
          }
          showOverlays(miShowIcons.isSelected(), miShowIconLabels.isSelected(), miShowDistances.isSelected());
        } finally {
          WindowBlocker.blockWindow(false);
        }
      } else if (e.getSource() == miShowDistances) {
        try {
          WindowBlocker.blockWindow(true);
          if (miShowDistances.isSelected() && !miShowIcons.isSelected()) {
            miShowIcons.setSelected(true);
          }
          showOverlays(miShowIcons.isSelected(), miShowIconLabels.isSelected(), miShowDistances.isSelected());
        } finally {
          WindowBlocker.blockWindow(false);
        }
      } else if (e.getSource() == miScaling) {
        try {
          WindowBlocker.blockWindow(true);
          if (miShowIcons.isSelected()) {
            showOverlays(miShowIcons.isSelected(), miShowIconLabels.isSelected(), miShowDistances.isSelected());
          }
        } finally {
          WindowBlocker.blockWindow(false);
        }
      } else if (e.getSource() == miExportMap) {
        exportMap();
      }
    }

    //--------------------- End Interface ActionListener ---------------------

    //--------------------- Begin Interface MouseListener ---------------------

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e)
    {
      if (e.isPopupTrigger() && e.getComponent() == rcMap) {
        showPopup(e.getComponent(), e.getX(), e.getY());
      }
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
      if (e.isPopupTrigger() && e.getComponent() == rcMap) {
        showPopup(e.getComponent(), e.getX(), e.getY());
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    //--------------------- End Interface MouseListener ---------------------

    //--------------------- Begin Interface MouseMotionListener ---------------------

    @Override
    public void mouseDragged(MouseEvent e)
    {
    }

    @Override
    public void mouseMoved(MouseEvent e)
    {
      if (e.getSource() == rcMap) {
        int ctrlWidth = rcMap.getWidth();
        int ctrlHeight = rcMap.getHeight();
        Image image = rcMap.getImage();
        int imgWidth = image.getWidth(null);
        int imgHeight = image.getHeight(null);
        int startX = (ctrlWidth - imgWidth) / 2;
        int startY = (ctrlHeight - imgHeight) / 2;
        int x = e.getX();
        int y = e.getY();
        if (x >= startX && x < startX + imgWidth && y >= startY && y < startY + imgHeight) {
          updateCursorInfo(x - startX, y - startY);
        } else {
          updateCursorInfo(-1, -1);
        }
      }
    }

    //--------------------- End Interface MouseMotionListener ---------------------

    // --------------------- Begin Interface ListSelectionListener ---------------------

    @Override
    public void valueChanged(ListSelectionEvent event)
    {
      if (!event.getValueIsAdjusting()) {
        JList<?> list = (JList<?>)event.getSource();
        if (miShowDistances.isSelected()) {
          showOverlays(miShowIcons.isSelected(), miShowIconLabels.isSelected(), miShowDistances.isSelected());
        } else {
          showDot((AreaEntry)list.getSelectedValue(), true);
        }
        repaint();
      }
    }

    // --------------------- End Interface ListSelectionListener ---------------------
  }


  private static final class WmpAreaListRenderer extends DefaultListCellRenderer
      implements ListValueRenderer
  {
    private final BamDecoder bam;
    private final BamControl ctrl;

    private WmpAreaListRenderer(BamDecoder decoder)
    {
      bam = decoder;
      ctrl = (bam != null) ? bam.createControl() : null;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                  boolean cellHasFocus)
    {
      JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      label.setText(getListValue(value, true));

      AbstractStruct struct = (AbstractStruct)value;
      DecNumber animNr = (DecNumber)struct.getAttribute(AreaEntry.WMP_AREA_ICON_INDEX);
      setIcon(null);
      if (ctrl != null) {
        setIcon(new ImageIcon(bam.frameGet(ctrl, ctrl.cycleGetFrameIndexAbsolute(animNr.getValue(), 0))));
      }
      return label;
    }

    @Override
    public String getListValue(Object value)
    {
      return getListValue(value, false);
    }

    private String getListValue(Object value, boolean showFull)
    {
      if (value instanceof AbstractStruct) {
        AbstractStruct struct = (AbstractStruct)value;

        StringRef areaName = (StringRef)struct.getAttribute(AreaEntry.WMP_AREA_NAME);
        ResourceRef areaRef = (ResourceRef)struct.getAttribute(AreaEntry.WMP_AREA_CURRENT);
        String text1 = null, text2 = null;
        if (areaName.getValue() >= 0) {
          StringTable.Format fmt = BrowserMenuBar.getInstance().showStrrefs() ? StringTable.Format.STRREF_SUFFIX
                                                                              : StringTable.Format.NONE;
          text1 = areaName.toString(fmt);
        } else {
          text1 = "";
        }
        text2 = areaRef.getResourceName();
        if (!text2.equalsIgnoreCase("NONE")) {
          text2 = text2.toUpperCase(Locale.ENGLISH).replace(".ARE", "");
        }

        if (showFull) {
          return '[' + text2 + "] " + text1;
        } else {
          return text2;
        }
      }
      return "";
    }
  }
}
