// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.ResourceRef;
import org.infinity.gui.ToolTipTableCellRenderer;
import org.infinity.gui.ViewFrame;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.icon.Icons;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.util.Misc;

final class ViewerSpells extends JPanel implements ActionListener {
  private final JButton bOpen = new JButton("View/Edit", Icons.ICON_ZOOM_16.getIcon());
  private final JTable table;
  private final MemSpellTableModel tableModel;

  ViewerSpells(CreResource cre) {
    super(new BorderLayout(0, 3));
    tableModel = new MemSpellTableModel(cre);
    table = new JTable(tableModel);
    table.setDefaultRenderer(Object.class,
        new ToolTipTableCellRenderer(BrowserMenuBar.getInstance().getOptions().showResourceListIcons()));
    ((DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer())
        .setHorizontalAlignment(SwingConstants.LEFT);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getColumnModel().getColumn(0).setMaxWidth(60);
    table.getColumnModel().getColumn(1).setMaxWidth(40);
    table.getColumnModel().getColumn(2).setMaxWidth(20);
    table.setRowHeight(Misc.getFontHeight(table.getGraphics(), table.getFont()));
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && table.getSelectedRowCount() == 1) {
          ResourceRef ref = (ResourceRef) tableModel.getValueAt(table.getSelectedRow(), 3);
          if (ref != null) {
            Resource res = ResourceFactory.getResource(ResourceFactory.getResourceEntry(ref.getResourceName()));
            new ViewFrame(getTopLevelAncestor(), res);
          }
        }
      }
    });
    table.getSelectionModel().addListSelectionListener(e -> {
      final boolean selected = !table.getSelectionModel().isSelectionEmpty();
      final ResourceRef ref = selected ? (ResourceRef)tableModel.getValueAt(table.getSelectedRow(), 3) : null;
      final boolean enabled = selected && ResourceFactory.resourceExists(ref.getResourceName());
      bOpen.setEnabled(enabled);
    });
    table.getSelectionModel().setSelectionInterval(0, 0);
    add(new JLabel("Memorized spells"), BorderLayout.NORTH);
    add(new JScrollPane(table), BorderLayout.CENTER);
    bOpen.addActionListener(this);
    bOpen.setEnabled(tableModel.getRowCount() > 0);
    add(bOpen, BorderLayout.SOUTH);
  }

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == bOpen) {
      ResourceRef ref = (ResourceRef) tableModel.getValueAt(table.getSelectedRow(), 3);
      if (ref != null) {
        Resource res = ResourceFactory.getResource(ResourceFactory.getResourceEntry(ref.getResourceName()));
        new ViewFrame(getTopLevelAncestor(), res);
      }
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // -------------------------- INNER CLASSES --------------------------

  private static final class MemSpellTableModel extends AbstractTableModel implements TableModelListener {
    private final List<MemSpellTableEntry> list = new ArrayList<>();
    private final CreResource cre;

    private MemSpellTableModel(CreResource cre) {
      this.cre = cre;
      updateTable();
      cre.addTableModelListener(this);
    }

    private void updateTable() {
      list.clear();
      for (final StructEntry o : cre.getFields()) {
        if (o instanceof SpellMemorization) {
          SpellMemorization inf = (SpellMemorization) o;
          int type = ((IsNumeric) inf.getAttribute(SpellMemorization.CRE_MEMORIZATION_TYPE)).getValue();
          int lvl = ((IsNumeric) inf.getAttribute(SpellMemorization.CRE_MEMORIZATION_LEVEL)).getValue();
          for (final StructEntry p : inf.getFields()) {
            if (p instanceof MemorizedSpells) {
              MemorizedSpells spell = (MemorizedSpells) p;
              addSpell(type, lvl, (ResourceRef) spell.getAttribute(MemorizedSpells.CRE_MEMORIZED_RESREF));
            }
          }
        }
      }
    }

    private void addSpell(int type, int lvl, ResourceRef spell) {
      for (MemSpellTableEntry element : list) {
        if (spell.getResourceName().equalsIgnoreCase(element.spell.getResourceName())) {
          element.count++;
          return;
        }
      }
      MemSpellTableEntry entry = new MemSpellTableEntry(type, lvl, spell);
      list.add(entry);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      updateTable();
      fireTableStructureChanged();
    }

    @Override
    public int getRowCount() {
      return list.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      MemSpellTableEntry entry = list.get(rowIndex);
      switch (columnIndex) {
        case 0:
          return entry.getTypeName();
        case 1:
          return entry.lvl + 1;
        case 2:
          return entry.count + 1;
        default:
          return entry.spell;
      }
    }

    @Override
    public int getColumnCount() {
      return 4;
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case 0:
          return "Type";
        case 1:
          return "Level";
        case 2:
          return "#";
        default:
          return "Spell";
      }
    }
  }

  private static final class MemSpellTableEntry {
    private final int type;
    private final int lvl;
    private final ResourceRef spell;

    private int count;

    private MemSpellTableEntry(int type, int lvl, ResourceRef spell) {
      this.type = type;
      this.lvl = lvl;
      this.spell = spell;
    }

    private String getTypeName() {
      switch (type) {
        case 0:
          return "Priest";
        case 1:
          return "Wizard";
        case 2:
          return "Innate";
        default:
          return "Unknown";
      }
    }
  }
}
