// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui;

import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgressCellRenderer extends JProgressBar implements TableCellRenderer {

    /**
     * Creates a JProgressBar with the range 0,100.
     */
    public ProgressCellRenderer() {
        super(0, 100);
        setValue(0);
        setString("0%");
        setStringPainted(true);
    }

    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        //value is a percentage e.g. 95%
        final String sValue = value.toString();
        int index = sValue.indexOf('%');
        if (index != -1) {
            int p = 0;
            try {
                p = Integer.parseInt(sValue.substring(0, index));
            } catch (NumberFormatException e) {
                Logger.getLogger(String.valueOf(ProgressCellRenderer.class)).log(Level.WARNING, "Number Format Exception : " + e.getMessage());
            }

            this.setValue(p);
            this.setString(sValue);
        }
        return this;
    }
}
