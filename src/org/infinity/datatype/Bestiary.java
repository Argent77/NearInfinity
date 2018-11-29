// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import static java.awt.event.ActionEvent.ACTION_PERFORMED;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.infinity.NearInfinity;
import org.infinity.gui.InfinityScrollPane;
import org.infinity.gui.InfinityTextArea;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.StringEditor;
import static org.infinity.gui.StructViewer.UPDATE_VALUE;
import org.infinity.gui.ViewFrame;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.gam.GamResource;
import org.infinity.resource.gam.KillVariable;
import org.infinity.resource.graphics.GraphicsResource;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapCache;
import org.infinity.util.IniMapEntry;
import org.infinity.util.IniMapSection;
import org.infinity.util.StringTable;


/**
 * Datatype for {@link GamResource#GAM_BESTIARY Bestiary} field of the
 * {@link GamResource GAM} resource.
 *
 * Creature becomes known when any of a party sees it, however is unclear how the
 * {@link CreResource creature} is connected with record in a bestiary.
 *
 * <p>
 * The creature has the {@link CreResource#CRE_CHARACTER_TYPE Character type} field
 * value of which suspiciously remind types of creatures in a bestiary, but, first,
 * not all creatures have this field filled, but it get to a bestiary at least
 * (Lady of Pain, Morte, Nameless One, Lothar, Coaxmetal, Pillar of Skulls,
 * The Transcendent One), secondly, the made experiment showed that changeover of
 * value of this field in a CRE file and in a SAV file does not influence record
 * of a bestiary.
 *
 * @author Mingun
 */
public final class Bestiary extends Datatype implements Editable, TableModel
{
  /** Name of file with description of creatures. */
  private static final String BEAST_INI = "beast.ini";
  /**
   * This string reference used as category title when bestiary not contains any
   * NPC creatures (at start of game).
   */
  private static final int EMPTY_BESTIARY_NAME = 3594;
  /**
   * This string reference used as category description when bestiary not contains any
   * NPC creatures (at start of game).
   */
  private static final int EMPTY_BESTIARY_DESC = 3595;
  /** Color, used to highligth background of party members. */
  private static final Color PARTY_COLOR = Color.CYAN;

  //<editor-fold defaultstate="collapsed" desc="Creatures">
  /**
   * List of used entries in a bestiary field. Other bytes in the {@link #known}
   * field are unused.
   * <p>
   * String and image references are hardcoded because it seems that they are
   * hardcoded in the game.
   */
  private final List<Creature> creatures;
  //</editor-fold>
  /**
   * If byte contains non zero value, then player knowns creature from {@link #creatures}
   * array. Only first 88 bytes is used ({@code creatures.length}).
   */
  private final byte[] known;

  /** This class contains information about creature: name, description and image. */
  private static final class Creature
  {
    /** String reference to talk table with brief name of the creature. */
    final int nameStrRef;
    /** String reference to talk table with description of the creature. */
    final int descStrRef;
    /** Category of creature that determines, at which section in the Bestiary creature will appears. */
    private final Category category;
    /** Resource reference with name like {@code JR...KN.BMP} with image of the creature. */
    final String imageResRef;
    /** Variable that used to count killed creatures of this type. */
    final String killVarName;

    enum Category
    {
      /** Creature is playable character that appears in the "Playable Characters" in the game menu.*/
      PARTY("0"),
      /** Creature is Non-playable Character that appears in the "Non-playable Characters" in the game menu.*/
      NPC("1");

      /** Representation of categoty in {@code ini} file. */
      final String type;

      Category(String type) { this.type = type; }

      static Category resolve(String type)
      {
        for (Category cat : values()) {
          if (cat.type.equals(type)) return cat;
        }
        return null;
      }
    }

    public Creature(Integer nameStrRef, Integer descStrRef, Category category,
                    String imageResRef, String killVarName)
    {
      this.nameStrRef  = nameStrRef == null ? -1 : nameStrRef.intValue();
      this.descStrRef  = descStrRef == null ? -1 : descStrRef.intValue();
      this.category    = category;
      this.imageResRef = imageResRef;
      this.killVarName = killVarName;
    }
    public Creature(IniMapSection section)
    {
      this(section == null ? null : section.getEntry("name"),
           section == null ? null : section.getEntry("desc0"),
           section == null ? null : section.getEntry("class"),
           section == null ? null : section.getEntry("imageKnown"),
           section == null ? null : section.getEntry("killvar")
      );
    }
    private Creature(IniMapEntry nameStrRef,
                     IniMapEntry descStrRef,
                     IniMapEntry category,
                     IniMapEntry imageResRef,
                     IniMapEntry killVarName
    )
    {
      this(nameStrRef  == null ? null : nameStrRef.getIntValue(),
           descStrRef  == null ? null : descStrRef.getIntValue(),
           category    == null ? null : Category.resolve(category.getValue()),
           imageResRef == null ? null : imageResRef.getValue(),
           killVarName == null ? null : killVarName.getValue()
      );
    }
    /** Translates string reference of brief name to string with name. */
    public String getName() { return StringTable.getStringRef(nameStrRef); }
    /** Translates string reference of detail description to string with description. */
    public String getDesc() { return StringTable.getStringRef(descStrRef); }
    public boolean isParty() { return category == Category.PARTY; }
    public ResourceEntry getImageEntry()
    {
      return ResourceFactory.getResourceEntry(imageResRef + ".bmp");
    }
    public Image getImage()
    {
      final ResourceEntry entry = getImageEntry();
      if (entry == null) { return null; }

      final Resource resource = ResourceFactory.getResource(entry);
      if (resource instanceof GraphicsResource) {
        return ((GraphicsResource) resource).getImage();
      }
      return null;
    }

    /**
     * Performs search kill variable, associated with this creature. This variable
     * increments each time as player party kill such creature.
     *
     * @param game Game structure that stores kill variables
     * @return Structure that contains kill counter for this creature category
     *         or {code null}, if not found or creature not contains reference
     *         to variable
     */
    public KillVariable findVariable(GamResource game)
    {
      if (killVarName == null) return null;

      final SectionOffset offset = game.getSectionOffset(KillVariable.class);
      KillVariable var = (KillVariable)game.getAttribute(offset.getValue(), KillVariable.class, false);
      if (var == null) return null;

      final List<StructEntry> fields = game.getList();
      final ListIterator<StructEntry> it = fields.listIterator(fields.indexOf(var));
      while (it.hasNext()) {
        final StructEntry entry = it.next();
        if (!(entry instanceof KillVariable)) break;

        var = (KillVariable)entry;
        final IsTextual name = (IsTextual)var.getAttribute(KillVariable.VAR_NAME);
        if (name != null && killVarName.equalsIgnoreCase(name.getText())) {
          return var;
        }
      }
      return null;
    }

    /**
     * Mark name and description strings that describe creatures in a bestiary,
     * as used (set {@code true} at appropriate indexes in the {@code used} parameter).
     * <p>
     * If the array has the insufficient length, the appropriate strings are not marked
     *
     * @param used Array of used string indexes
     */
    public void markUsed(boolean[] used)
    {
      if (nameStrRef >= 0 && nameStrRef < used.length) {
        used[nameStrRef] = true;
      }
      if (descStrRef >= 0 && descStrRef < used.length) {
        used[descStrRef] = true;
      }
    }

    @Override
    public String toString()
    {
      return "Creature{"
              + "nameStrRef=" + nameStrRef
              + ", descStrRef=" + descStrRef
              + ", category=" + category
              + ", imageResRef=" + imageResRef
              + ", killVarName=" + killVarName
              + '}';
    }
  }
  /**
   * Editor for Bestiary field. Contains table with creatures, HEX area for edit
   * not used bytes and information panel with image and description of the selected
   * creature in the table.
   */
  private final class Viewer extends JSplitPane implements ListSelectionListener,
                                                           TableModelListener,
                                                           MouseListener,
                                                           ActionListener
  {
    private static final String OPEN_VAR = "Edit Kill variable%s";
    private static final String GOTO_VAR = "Go to Kill variable%s";
    private static final String OPEN_IMG = "Edit Image%s";
    private static final String GOTO_IMG = "Go to Image%s";
    private static final String EDIT_NAME = "Edit creature name%s";
    private static final String EDIT_DESC = "Edit creature description%s";

    /** Canvas used to draw creature image in editor. */
    private final RenderCanvas image = new RenderCanvas();
    /** Text area that contains description of the creature. */
    private final InfinityTextArea desc = new InfinityTextArea(true);
    /** Table for editing used creature entries. */
    private final JTable table;
    /** Object which requested the editor and is interested in events about its updating. */
    private final ActionListener container;
    /** Context menu for table element or byte in HEX view tab. */
    private final JPopupMenu contextMenu = new JPopupMenu();
    /** Context menu item used to open new window with Kill Variable structure. */
    private final JMenuItem openVar = new JMenuItem();
    /** Context menu item used to select Kill Variable in the parent structure. */
    private final JMenuItem gotoVar = new JMenuItem();
    /** Context menu item used to open new window with image editor. */
    private final JMenuItem openImg = new JMenuItem();
    /** Context menu item used to select image in the resource tree. */
    private final JMenuItem gotoImg = new JMenuItem();
    /** Context menu item used to open editor for name TLK entry. */
    private final JMenuItem editName = new JMenuItem();
    /** Context menu item used to open editor for description TLK entry. */
    private final JMenuItem editDesc = new JMenuItem();
    /** Context menu item used to open {@code "beast.ini"} file. */
    private final JMenuItem beastIni = new JMenuItem(String.format("Open \"%s\"", BEAST_INI),
                                                     Icons.getIcon(Icons.ICON_EDIT_16));

    public Viewer(ActionListener container)
    {
      super();
      this.container = container;
      table = new JTable(Bestiary.this);

      final JSplitPane editor = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
      editor.add(new JScrollPane(table), JSplitPane.TOP);
      editor.add(new InfinityScrollPane(desc, true), JSplitPane.BOTTOM);
      editor.setResizeWeight(0.8);// 80% - to table, 20% - to description

      add(editor, JSplitPane.LEFT);
      JScrollPane spImage = new JScrollPane(image);
      spImage.getVerticalScrollBar().setUnitIncrement(16);
      spImage.getHorizontalScrollBar().setUnitIncrement(16);
      add(spImage, JSplitPane.RIGHT);
      // PST images for bestiary has 281 x 441 resolution. Reserve 19 pixels for scrollbar
      image.setPreferredSize(new Dimension(300, 441));
      setResizeWeight(1);// 100% - to table, image always has the same size so do not need resize
      resetToPreferredSizes();

      desc.setEditable(false);
      desc.setLineWrap(true);
      desc.setWrapStyleWord(true);

      final TableColumnModel model = table.getColumnModel();
      TableColumn col = model.getColumn(0);// checkbox
      col.setMinWidth(20);
      col.setMaxWidth(20);
      col.setResizable(false);

      col = model.getColumn(1);// number
      col.setMinWidth(25);
      col.setMaxWidth(25);
      col.setResizable(false);

      col = model.getColumn(2);// name
      col.setMinWidth(0);
      col.setPreferredWidth(50);

      col = model.getColumn(4);// kill variable
      col.setMinWidth(0);
      col.setPreferredWidth(50);

      col = model.getColumn(5);// kills
      col.setMinWidth(0);
      col.setPreferredWidth(25);

      table.getSelectionModel().addListSelectionListener(this);
      table.getModel().addTableModelListener(this);
      table.addMouseListener(this);

      final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer()
      {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column)
        {
          final boolean isPC = creatures.get(row).isParty();
          setBackground(isPC ? PARTY_COLOR : table.getBackground());

          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          setForeground(isKnown(row) ? null : Color.GRAY);
          return this;
        }
      };
      table.setDefaultRenderer(Object.class, renderer);
      table.setDefaultRenderer(Integer.class, renderer);

      openVar.addActionListener(this);
      gotoVar.addActionListener(this);
      openImg.addActionListener(this);
      gotoImg.addActionListener(this);
      editName.addActionListener(this);
      editDesc.addActionListener(this);
      beastIni.addActionListener(this);

      // Leave bold only first item - that is activated by double click on row
      final Font plain = gotoVar.getFont().deriveFont(Font.PLAIN);
      gotoVar.setFont(plain);
      openImg.setFont(plain);
      gotoImg.setFont(plain);
      editName.setFont(plain);
      editDesc.setFont(plain);
      beastIni.setFont(plain);

      contextMenu.add(openVar);
      contextMenu.add(gotoVar);
      contextMenu.add(openImg);
      contextMenu.add(gotoImg);
      contextMenu.add(editName);
      contextMenu.add(editDesc);
      contextMenu.add(beastIni);

      // Because StructViewer not stretch his components, set infinity preferred size
      setPreferredSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    //<editor-fold defaultstate="collapsed" desc="ListSelectionListener">
    @Override
    public void valueChanged(ListSelectionEvent e)
    {
      final ListSelectionModel model = (ListSelectionModel)e.getSource();
      final int index = model.getAnchorSelectionIndex();
      final boolean hasImage = image.getImage() != null;
      if (index < 0 || index >= creatures.size()) {
        image.setImage(null);
        desc.setText(null);
      } else {
        final Creature cre = creatures.get(index);
        image.setImage(cre.getImage());
        final int pos = desc.getCaretPosition();
        final String text = cre.getDesc();
        desc.setText(text);
        desc.setCaretPosition(text.length() < pos ? 0 : pos);
      }
      if (hasImage ^ (image.getImage() != null)) {
        revalidate();
      }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="TableModelListener">
    @Override
    public void tableChanged(TableModelEvent e)
    {
      container.actionPerformed(new ActionEvent(e.getSource(), ACTION_PERFORMED, UPDATE_VALUE));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="MouseListener">
    @Override
    public void mouseClicked(MouseEvent e)
    {
      if (e.getClickCount() == 2) {
        final int row = table.rowAtPoint(e.getPoint());
        final GamResource game = (GamResource)Bestiary.this.getParent();
        final KillVariable var = creatures.get(row).findVariable(game);
        if (var != null) {
          new ViewFrame(this, var);
        }
      }
    }

    @Override
    public void mousePressed(MouseEvent e) { handlePopup(e); }

    @Override
    public void mouseReleased(MouseEvent e) { handlePopup(e); }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="ActionListener">
    @Override
    public void actionPerformed(ActionEvent e)
    {
      final JMenuItem src = (JMenuItem)e.getSource();
      final int row = table.getSelectedRow();
      if (src == beastIni) {
        final Resource resource = ResourceFactory.getResource(getBeastsEntry());
        if (resource != null) {
          new ViewFrame(this, resource);
        }
      } else
      if (row >= 0 && row < creatures.size()) {
        final GamResource game = (GamResource)Bestiary.this.getParent();
        final Creature creature = creatures.get(row);
        if (src == openVar) {
          final KillVariable var = creature.findVariable(game);
          if (var != null) {
            new ViewFrame(this, var);
          }
        } else
        if (src == gotoVar) {
          final KillVariable var = creature.findVariable(game);
          if (var != null) {
            game.getViewer().selectEntry(var.getName());
          }
        } else
        if (src == openImg) {
          final ResourceEntry entry = creature.getImageEntry();
          final Resource resource = entry == null ? null : ResourceFactory.getResource(entry);
          if (resource != null) {
            new ViewFrame(this, resource);
          }
        } else
        if (src == gotoImg) {
          final ResourceEntry entry = creature.getImageEntry();
          if (entry != null) {
            NearInfinity.getInstance().showResourceEntry(entry);
          }
        } else
        if (src == editName) {
          StringEditor.edit(creature.nameStrRef);
        } else
        if (src == editDesc) {
          StringEditor.edit(creature.descStrRef);
        }
      }
    }
    //</editor-fold>

    /**
     * Setup context menu and show it for table.
     *
     * @param e Event that probably generates context menu
     */
    private void handlePopup(MouseEvent e)
    {
      if (e.isPopupTrigger()) {
        final int row = table.rowAtPoint(e.getPoint());
        if (row >= 0) {
          table.getSelectionModel().setSelectionInterval(row, row);
          setupPopup(contextMenu, row);
          contextMenu.show(table, e.getX(), e.getY());
        }
      }
    }

    /**
     * Setup availability and names of the context menu items for specified creature.
     *
     * @param menu Context menu under which place items
     * @param index Index of the creature for which menu is shown
     */
    private void setupPopup(JPopupMenu menu, int index)
    {
      final Creature cre = index >= 0 && index < creatures.size() ? creatures.get(index) : null;
      final boolean hasVar = cre != null && cre.killVarName != null;
      final boolean hasImg = cre != null && cre.imageResRef != null;
      final boolean hasName = cre != null && cre.nameStrRef >= 0;
      final boolean hasDesc = cre != null && cre.descStrRef >= 0;

      openVar.setEnabled(hasVar);
      gotoVar.setEnabled(hasVar);
      openImg.setEnabled(hasImg);
      gotoImg.setEnabled(hasImg);
      editName.setEnabled(hasName);
      editDesc.setEnabled(hasDesc);

      final String varName = hasVar ? " \"" + cre.killVarName + '"' : "";
      final String imgName = hasImg ? " \"" + cre.imageResRef + '"' : "";
      final String nameRef = hasName ? " (" + cre.nameStrRef  + ')' : "";
      final String descRef = hasDesc ? " (" + cre.descStrRef  + ')' : "";

      openVar.setText(String.format(OPEN_VAR, varName));
      gotoVar.setText(String.format(GOTO_VAR, varName));
      openImg.setText(String.format(OPEN_IMG, imgName));
      gotoImg.setText(String.format(GOTO_IMG, imgName));
      editName.setText(String.format(EDIT_NAME, nameRef));
      editDesc.setText(String.format(EDIT_DESC, descRef));
    }
  }

  public Bestiary(ByteBuffer buffer, int offset, String name)
  {
    super(offset, 260, name);
    known = new byte[getSize()];
    read(buffer, offset);
    creatures = readCreatures();
  }

  //<editor-fold defaultstate="collapsed" desc="Editable">
  @Override
  public JComponent edit(ActionListener container) { return new Viewer(container); }

  @Override
  public void select() {}

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    // notifying listeners
    fireValueUpdated(new UpdateEvent(this, struct));

    return true;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Writable">
  @Override
  public void write(OutputStream os) throws IOException
  {
    os.write(known);
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Readable">
  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    buffer.get(known);
    return offset + known.length;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="TableModel">
  @Override
  public int getRowCount() { return creatures.size(); }
  @Override
  public int getColumnCount() { return 6; }
  @Override
  public String getColumnName(int columnIndex)
  {
    switch (columnIndex) {
      case 0: return "Known?";
      case 1: return "#";
      case 2: return "Name";
      case 3: return "Description";
      case 4: return "Kill variable";
      case 5: return "Kills";
      default: return null;
    }
  }
  @Override
  public Class<?> getColumnClass(int columnIndex)
  {
    switch (columnIndex) {
      case 0: return Boolean.class;
      case 1: return Integer.class;
      case 2: return String.class;
      case 3: return String.class;
      case 4: return String.class;
      case 5: return Integer.class;
      default: return null;
    }
  }
  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) { return columnIndex == 0; }
  @Override
  public Object getValueAt(int rowIndex, int columnIndex)
  {
    final Creature cre = creatures.get(rowIndex);
    switch (columnIndex) {
      case 0: return isKnown(rowIndex);
      case 1: return rowIndex + 1;
      case 2: return cre.getName();
      case 3: return cre.getDesc();
      case 4: return cre.killVarName;
      case 5: return getKills(cre);
      default: return null;
    }
  }
  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex)
  {
    if (columnIndex == 0) {
      known[rowIndex] = (byte)(Boolean.TRUE.equals(aValue) ? 1 : 0);
      fireTableChanged(rowIndex);
    }
  }

  @Override
  public void addTableModelListener(TableModelListener l)
  {
    listenerList.add(TableModelListener.class, l);
  }

  @Override
  public void removeTableModelListener(TableModelListener l)
  {
    listenerList.remove(TableModelListener.class, l);
  }
  //</editor-fold>

  /**
   * Returns {@code true} if specified creature is included in the bestiary.
   *
   * @param creatureIndex Creature index in the bestiaty field
   * @return {@code true} if player already see this creature in a game, {@code false} otherwise
   */
  public boolean isKnown(int creatureIndex) { return known[creatureIndex] != 0; }

  private Integer getKills(Creature cre)
  {
    final GamResource parent = (GamResource)getParent();
    final KillVariable var = cre.findVariable(parent);
    if (var == null) return null;

    final StructEntry entry = var.getAttribute(KillVariable.VAR_INT);
    if (entry instanceof IsNumeric) {
      return ((IsNumeric)entry).getValue();
    }
    return null;
  }

  /**
   * Mark all strings that describe creatures in a bestiary, as used (set {@code true}
   * at appropriate indexes in the {@code used} parameter).
   * <p>
   * If the array has the insufficient length, the appropriate strings are not marked
   *
   * @param used Array of used string indexes
   */
  public static void markUsedStrings(boolean[] used)
  {
    for (Creature cre : readCreatures()) {
      cre.markUsed(used);
    }
    if (EMPTY_BESTIARY_NAME < used.length) {
      used[EMPTY_BESTIARY_NAME] = true;
    }
    if (EMPTY_BESTIARY_DESC < used.length) {
      used[EMPTY_BESTIARY_DESC] = true;
    }
  }

  private void fireTableChanged(int row)
  {
    final TableModelEvent e = new TableModelEvent(this, row, row);
    // Guaranteed to return a non-null array
    final Object[] listeners = listenerList.getListenerList();
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length-2; i >= 0; i -= 2) {
      if (listeners[i] == TableModelListener.class) {
        ((TableModelListener)listeners[i+1]).tableChanged(e);
      }
    }
  }

  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (int i = 0; i < known.length; ++i) {
      if (!isKnown(i)) { continue; }

      if (!first) { sb.append("; "); }
      first = false;

      if (i < creatures.size()) {
        sb.append(creatures.get(i).getName());
      }
      sb.append('(').append(i+1).append(')');
    }
    return first ? "No known creatures" : sb.toString();
  }

  /**
   * Returns resource entry from which creatures will be read.
   * @return Pointer to resource with creatures in the bestiary. Never {@code null}
   */
  private static ResourceEntry getBeastsEntry()
  {
    final Path beast = Profile.getGameRoot().resolve(BEAST_INI);
    return new FileResourceEntry(beast);
  }

  /**
   * Read all creatures from {@link #BEAST_INI} file in game root. File can contain
   * creatures with numbers in range {@code [0; 256]}, all creatures with other
   * numbers will be skipped and log message will be writed to the {@link System#err
   * standard error stream}.
   *
   * @return List with creatures found in the file. If file has incorrect format
   *         returns empty list
   */
  private static List<Creature> readCreatures()
  {
    final ResourceEntry entry = getBeastsEntry();
    return readCreatures(entry.getResourceName(), IniMapCache.get(entry));
  }

  /**
   * Read all creatures from specified ini file in game root. File can contain
   * creatures with numbers in range {@code [0; 256]}, all creatures with other
   * numbers will be skipped and log message will be writed to the {@link System#err
   * standard error stream}.
   *
   * @param filename Filename from which file was read, for logging
   * @param ini File to read. Must not be {@code null}
   *
   * @return List with creatures found in the file. If file has incorrect format
   *         returns empty list
   */
  private static List<Creature> readCreatures(String filename, IniMap ini)
  {
    final IniMapSection init = ini.getSection("init");
    if (init == null) {
      System.err.println(filename + ": [init] section not found in the file. Creatures not loaded");
      return Collections.emptyList();
    }
    final IniMapEntry entry = init.getEntry("beastcount");
    if (entry == null) {
      System.err.println(filename + ": \"beastcount\" key in [init] section not found. Creatures not loaded");
      return Collections.emptyList();
    }
    final Integer count = entry.getIntValue();
    if (count == null) {
      System.err.println(filename + ": \"beastcount\" key in [init] section: expected integer buf found "
              + entry.getValue() + ". Creatures not loaded");
      return Collections.emptyList();
    }

    final ArrayList<Creature> result = new ArrayList<>(count.intValue());
    for (final IniMapSection section : ini) {
      final int i;
      try {
        i = Integer.parseInt(section.getName());
      } catch (NumberFormatException ex) {
        continue;
      }
      if (i < 0 || i >= 256) {
        System.err.println(filename + ": invalid creature number "+i+", expected number in range [0; 256]. Creature skipped");
        continue;
      }
      result.ensureCapacity(i);
      result.add(new Creature(section));
    }
    return result;
  }
}
