// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.fnt;

import java.nio.ByteBuffer;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import org.infinity.datatype.DecNumber;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.graphics.GraphicsResource;
import org.infinity.resource.key.ResourceEntry;
import org.tinylog.Logger;

/**
 *
 */
public class FntResource extends AbstractStruct implements Resource, HasViewerTabs {
  // FNT-specific field labels
  public static final String FNT_NUM_GLYPHS         = "# glyphs";
  public static final String FNT_NUM_POINT_SIZES    = "# sizes";
  public static final String FNT_NUM_KERNING        = "# kerning entries";

  private StructHexViewer hexViewer;

  public FntResource(ResourceEntry entry) throws Exception {
    super(entry);
  }

  // --------------------- Begin Interface HasViewerTabs ---------------------

  @Override
  public int getViewerTabCount() {
    return 2;
  }

  @Override
  public String getViewerTabName(int index) {
    switch (index) {
      case 0:
        return StructViewer.TAB_VIEW;
      case 1:
        return StructViewer.TAB_RAW;
    }
    return null;
  }

  @Override
  public JComponent getViewerTab(int index) {
    switch (index) {
      case 0:
        JComponent comp = null;
        final String bmpFileName = getResourceEntry().getResourceRef() + ".BMP";
        final ResourceEntry entry = ResourceFactory.getResourceEntry(bmpFileName);
        if (entry != null) {
          try {
            GraphicsResource graphics = new GraphicsResource(entry);
            comp = new RenderCanvas(graphics.getImage());
          } catch (Exception e) {
            Logger.error(e);
          }
        }
        if (comp == null) {
          comp = new JLabel("Resource not found: " + bmpFileName, SwingConstants.CENTER);
        }
        final JScrollPane scroll = new JScrollPane(comp);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
      case 1:
        if (hexViewer == null) {
          hexViewer = new StructHexViewer(this, new BasicColorMap(this, true));
        }
        return hexViewer;
    }
    return null;
  }

  @Override
  public boolean viewerTabAddedBefore(int index) {
    return (index == 0);
  }

  // --------------------- End Interface HasViewerTabs ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    final DecNumber glyphsCount = new DecNumber(buffer, offset, 4, FNT_NUM_GLYPHS);
    addField(glyphsCount);
    final DecNumber pointSizeCount = new DecNumber(buffer, offset + 4, 2, FNT_NUM_POINT_SIZES);
    addField(pointSizeCount);
    addField(new DecNumber(buffer, offset + 6, 2, AbstractStruct.COMMON_UNKNOWN));
    addField(new DecNumber(buffer, offset + 8, 4, AbstractStruct.COMMON_UNKNOWN));
    final DecNumber kerningCount = new DecNumber(buffer, offset + 12, 4, FNT_NUM_KERNING);
    addField(kerningCount);

    offset = 16;
    for (int i = 0, count = glyphsCount.getValue(); i < count; i++) {
      final CodePoint characterCode = new CodePoint(buffer, offset, i);
      addField(characterCode);
      offset += characterCode.getSize();
    }

    for (int i = 0, count = pointSizeCount.getValue(); i < count; i++) {
      final SizeMetrics sizeMetrics = new SizeMetrics(this, buffer, offset, i);
      addField(sizeMetrics);
      offset = sizeMetrics.getEndOffset();
    }

    for (int i = 0, count = pointSizeCount.getValue(); i < count; i++) {
      for (int j = 0, count2 = glyphsCount.getValue(); j < count2; j++) {
        final GlyphMetrics glyphMetrics = new GlyphMetrics(this, buffer, offset, i, j);
        addField(glyphMetrics);
        offset = glyphMetrics.getEndOffset();
      }
    }

    for (int i = 0, count = kerningCount.getValue(); i < count; i++) {
      final Kerning kerning = new Kerning(this, buffer, offset, i);
      addField(kerning);
      offset = kerning.getEndOffset();
    }

    for (int i = 0, count = pointSizeCount.getValue(); i < count; i++) {
      for (int j = 0, count2 = glyphsCount.getValue(); j < count2; j++) {
        final GlyphCoordinate glyphCoordinate = new GlyphCoordinate(this, buffer, offset, i, j);
        addField(glyphCoordinate);
        offset = glyphCoordinate.getEndOffset();
      }
    }

    return offset;
  }

}
