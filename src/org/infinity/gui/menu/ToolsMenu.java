// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.menu;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.infinity.AppOption;
import org.infinity.NearInfinity;
import org.infinity.check.BCSIDSChecker;
import org.infinity.check.CreInvChecker;
import org.infinity.check.DialogChecker;
import org.infinity.check.EffectValidationChecker;
import org.infinity.check.EffectsIndexChecker;
import org.infinity.check.IDSRefChecker;
import org.infinity.check.ResRefChecker;
import org.infinity.check.ResourceUseChecker;
import org.infinity.check.ScriptChecker;
import org.infinity.check.StringDuplicatesChecker;
import org.infinity.check.StringSoundsChecker;
import org.infinity.check.StringUseChecker;
import org.infinity.check.StringValidationChecker;
import org.infinity.check.StrrefIndexChecker;
import org.infinity.check.StructChecker;
import org.infinity.gui.BcsDropFrame;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.ClipboardViewer;
import org.infinity.gui.DebugConsole;
import org.infinity.gui.IdsBrowser;
import org.infinity.gui.InfinityAmpPlus;
import org.infinity.gui.MassExporter;
import org.infinity.gui.SplProtFrame;
import org.infinity.gui.converter.bam.ConvertToBam;
import org.infinity.gui.converter.creature.CreatureAnimationCreator;
import org.infinity.gui.converter.bmp.ConvertToBmp;
import org.infinity.gui.converter.mos.ConvertToMos;
import org.infinity.gui.converter.pvrz.ConvertToPvrz;
import org.infinity.gui.converter.tis.ConvertToTis;
import org.infinity.gui.converter.tis.ConvertToTisBatch;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.browser.CreatureBrowser;

/**
 * Handles Game menu items for the {@link BrowserMenuBar}.
 */
public class ToolsMenu extends JMenu implements BrowserSubMenu, ActionListener {
  public static final String TOOLS_DEBUG_EXTRA_INFO = "DebugShowExtraInfo";

  private final BrowserMenuBar menuBar;

  private final JMenuItem toolInfinityAmp;
  private final JMenuItem toolCreatureBrowser;
//  private final JMenuItem toolCleanKeyfile;
  private final JMenuItem toolCheckAllDialog;
  private final JMenuItem toolCheckOverrideDialog;

  private final JMenuItem toolCheckResRef;
  private final JMenuItem toolIDSBrowser;
  private final JMenuItem toolDropZone;
  private final JMenuItem toolCheckCREInv;
  private final JMenuItem toolSplProtEncoder;

  private final JMenuItem toolCheckIDSRef;
  private final JMenuItem toolCheckIDSBCSRef;
  private final JMenuItem toolCheckScripts;
  private final JMenuItem toolCheckStructs;

  private final JMenuItem toolCheckStringUse;
  private final JMenuItem toolCheckStringDuplicates;
  private final JMenuItem toolCheckStringSounds;
  private final JMenuItem toolCheckStringValid;
  private final JMenuItem toolCheckStringIndex;
  private final JMenuItem toolCheckFileUse;
  private final JMenuItem toolMassExport;

  private final JMenuItem toolCheckEffectsIndex;
  private final JMenuItem toolCheckEffectIsValid;

  private final JMenuItem toolConvImageToBam;
  private final JMenuItem toolCreatureAnimationCreator;
  private final JMenuItem toolConvImageToBmp;
  private final JMenuItem toolConvImageToMos;
  private final JMenuItem toolConvImageToTis;
  private final JMenuItem toolConvImagesToTis;
  private final JMenuItem toolConvImageToPvrz;

  private final JCheckBoxMenuItem toolConsole;
  private final JCheckBoxMenuItem toolClipBoard;

  private final JMenuItem dumpDebugInfo;

  public ToolsMenu(BrowserMenuBar parent) {
    super("Tools");
    setMnemonic(KeyEvent.VK_T);

    menuBar = parent;

    toolCreatureBrowser = BrowserMenuBar.makeMenuItem("Creature Animation Browser", KeyEvent.VK_A,
        Icons.ICON_CRE_VIEWER_24.getIcon(), -1, this);
    add(toolCreatureBrowser);

    toolInfinityAmp = BrowserMenuBar.makeMenuItem("InfinityAmp", KeyEvent.VK_I, Icons.ICON_MUSIC_16.getIcon(),
        -1, this);
    add(toolInfinityAmp);

    addSeparator();

    // TODO: reactivate when fixed
//    toolCleanKeyfile = makeMenuItem("Keyfile Cleanup", KeyEvent.VK_K, Icons.ICON_REFRESH_16.getIcon(), -1, this);
//    toolCleanKeyfile.setToolTipText("Temporarily disabled");
//    toolCleanKeyfile.setEnabled(false);
//    add(toolCleanKeyfile);

//    addSeparator();

    // *** Begin Check submenu ***
    JMenu checkMenu = new JMenu("Check");
    checkMenu.setIcon(Icons.ICON_FIND_16.getIcon());
    checkMenu.setMnemonic('c');
    add(checkMenu);

    final JMenu checkSubMenu = new JMenu("Dialogues");
    checkSubMenu.setIcon(Icons.ICON_REFRESH_16.getIcon());
    toolCheckAllDialog = new JMenuItem("All");
    toolCheckAllDialog.addActionListener(this);
    checkSubMenu.add(toolCheckAllDialog);
    toolCheckOverrideDialog = new JMenuItem("Override Only");
    toolCheckOverrideDialog.addActionListener(this);
    checkSubMenu.add(toolCheckOverrideDialog);
    checkMenu.add(checkSubMenu);

    toolCheckScripts = BrowserMenuBar.makeMenuItem("Scripts", KeyEvent.VK_S, Icons.ICON_REFRESH_16.getIcon(), -1, this);
    checkMenu.add(toolCheckScripts);

    toolCheckCREInv = BrowserMenuBar.makeMenuItem("For CRE Items Not in Inventory", KeyEvent.VK_C,
        Icons.ICON_REFRESH_16.getIcon(), -1, this);
    toolCheckCREInv.setToolTipText("Reports items present in the file but not in the inventory");
    checkMenu.add(toolCheckCREInv);

    toolCheckResRef = BrowserMenuBar.makeMenuItem("For Illegal ResourceRefs...", KeyEvent.VK_R,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    toolCheckResRef.setToolTipText("Reports resource references pointing to nonexistent files");
    checkMenu.add(toolCheckResRef);

    JMenu findMenu = new JMenu("For Unknown IDS References In");
    findMenu.setIcon(Icons.ICON_FIND_16.getIcon());
    toolCheckIDSBCSRef = new JMenuItem("BCS & BS Files");
    toolCheckIDSBCSRef.addActionListener(this);
    findMenu.add(toolCheckIDSBCSRef);
    toolCheckIDSRef = new JMenuItem("Other Files...");
    toolCheckIDSRef.addActionListener(this);
    findMenu.add(toolCheckIDSRef);
    checkMenu.add(findMenu);
    findMenu.setToolTipText("Reports IDS references to unknown IDS values");
    toolCheckIDSBCSRef.setToolTipText("Note: GTimes, Time, Scroll, ShoutIDs, and Specific are ignored");
    toolCheckIDSRef.setToolTipText("Note: \"0\" references are ignored");

    toolCheckStructs = BrowserMenuBar.makeMenuItem("For Corrupted Files...", KeyEvent.VK_F,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    toolCheckStructs.setToolTipText(
        "Reports structured files with partially overlapping subsections or resource-specific corruptions");
    checkMenu.add(toolCheckStructs);

    toolCheckStringUse = BrowserMenuBar.makeMenuItem("For Unused Strings", KeyEvent.VK_U, Icons.ICON_FIND_16.getIcon(),
        -1, this);
    checkMenu.add(toolCheckStringUse);

    toolCheckStringSounds = BrowserMenuBar.makeMenuItem("For Illegal SoundRefs in Strings", KeyEvent.VK_O,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    checkMenu.add(toolCheckStringSounds);

    toolCheckStringDuplicates = BrowserMenuBar.makeMenuItem("For Duplicate Strings", KeyEvent.VK_D,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    checkMenu.add(toolCheckStringDuplicates);

    toolCheckStringValid = BrowserMenuBar.makeMenuItem("For String Encoding Errors", KeyEvent.VK_E,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    checkMenu.add(toolCheckStringValid);

    toolCheckStringIndex = BrowserMenuBar.makeMenuItem("For Illegal Strrefs...", KeyEvent.VK_S,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    toolCheckStringIndex.setToolTipText("Reports resources with out-of-range string references");
    checkMenu.add(toolCheckStringIndex);

    toolCheckFileUse = BrowserMenuBar.makeMenuItem("For Unused Files...", -1, Icons.ICON_FIND_16.getIcon(), -1, this);
    checkMenu.add(toolCheckFileUse);

    toolCheckEffectsIndex = BrowserMenuBar.makeMenuItem("For Mis-indexed Effects...", -1, Icons.ICON_FIND_16.getIcon(),
        -1, this);
    checkMenu.add(toolCheckEffectsIndex);

    toolCheckEffectIsValid = BrowserMenuBar.makeMenuItem("For Invalid Effect Opcodes...", -1,
        Icons.ICON_FIND_16.getIcon(), -1, this);
    toolCheckEffectIsValid.setToolTipText("Reports opcodes that are unknown or not supported by this game");
    checkMenu.add(toolCheckEffectIsValid);
    // *** End Check submenu ***

    // *** Begin Convert submenu ***
    JMenu convertMenu = new JMenu("Convert");
    convertMenu.setIcon(Icons.ICON_APPLICATION_16.getIcon());
    convertMenu.setMnemonic('v');
    add(convertMenu);

    toolCreatureAnimationCreator = BrowserMenuBar.makeMenuItem("Creature Animation Creator...", KeyEvent.VK_C,
        Icons.ICON_CRE_VIEWER_24.getIcon(), -1, this);
    toolCreatureAnimationCreator.setToolTipText(
        "Creates Enhanced Edition type 0x7000 creature animation resources from descriptions or PNG frames.");
    toolCreatureAnimationCreator.setEnabled(Profile.isEnhancedEdition());
    convertMenu.add(toolCreatureAnimationCreator);
    convertMenu.addSeparator();

    toolConvImageToBam = BrowserMenuBar.makeMenuItem("BAM Converter...", KeyEvent.VK_B,
        Icons.ICON_APPLICATION_16.getIcon(), -1, this);
    convertMenu.add(toolConvImageToBam);

    toolConvImageToBmp = BrowserMenuBar.makeMenuItem("Image to BMP...", KeyEvent.VK_I,
        Icons.ICON_APPLICATION_16.getIcon(), -1, this);
    convertMenu.add(toolConvImageToBmp);

    toolConvImageToMos = BrowserMenuBar.makeMenuItem("Image to MOS...", KeyEvent.VK_M,
        Icons.ICON_APPLICATION_16.getIcon(), -1, this);
    convertMenu.add(toolConvImageToMos);

    toolConvImageToPvrz = BrowserMenuBar.makeMenuItem("Image to PVRZ...", KeyEvent.VK_P,
        Icons.ICON_APPLICATION_16.getIcon(), -1, this);
    convertMenu.add(toolConvImageToPvrz);

    toolConvImageToTis = BrowserMenuBar.makeMenuItem("Image to TIS...", KeyEvent.VK_T,
        Icons.ICON_APPLICATION_16.getIcon(), -1, this);
    convertMenu.add(toolConvImageToTis);

    toolConvImagesToTis = BrowserMenuBar.makeMenuItem("Batch images to TIS...", KeyEvent.VK_I,
        Icons.ICON_APPLICATION_16.getIcon(), -1, this);
    convertMenu.add(toolConvImagesToTis);
    // *** End Convert submenu ***

    addSeparator();

    toolIDSBrowser = BrowserMenuBar.makeMenuItem("IDS Browser", KeyEvent.VK_B, Icons.ICON_HISTORY_16.getIcon(),
        KeyEvent.VK_B, this);
    add(toolIDSBrowser);
    toolDropZone = BrowserMenuBar.makeMenuItem("Script Drop Zone", KeyEvent.VK_Z, Icons.ICON_HISTORY_16.getIcon(),
        KeyEvent.VK_Z, this);
    add(toolDropZone);
    toolSplProtEncoder = BrowserMenuBar.makeMenuItem("SPLPROT Converter", KeyEvent.VK_S,
        Icons.ICON_HISTORY_16.getIcon(), -1, this);
    toolSplProtEncoder.setToolTipText("Encodes or decodes SPLPROT.2DA filter definitions.");
    add(toolSplProtEncoder);

    addSeparator();

    toolMassExport = BrowserMenuBar.makeMenuItem("Mass Export...", KeyEvent.VK_M, Icons.ICON_EXPORT_16.getIcon(),
        KeyEvent.VK_M, this);
    add(toolMassExport);

    addSeparator();

    toolClipBoard = new JCheckBoxMenuItem("Show Clipboard", Icons.ICON_PASTE_16.getIcon());
    toolClipBoard.addActionListener(this);
    add(toolClipBoard);
    toolConsole = new JCheckBoxMenuItem("Show Debug Console", Icons.ICON_PROPERTIES_16.getIcon());
    toolConsole.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, BrowserMenuBar.getCtrlMask()));
    toolConsole.addActionListener(this);
    add(toolConsole);
    dumpDebugInfo = new JMenuItem("Print debug info to Console", Icons.ICON_PROPERTIES_16.getIcon());
    dumpDebugInfo.setToolTipText(
        "Output to console class of current top-level window, resource and selected field in the structure viewer");
    dumpDebugInfo
        .setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, BrowserMenuBar.getCtrlMask() | ActionEvent.ALT_MASK));
    dumpDebugInfo.addActionListener(this);
    dumpDebugInfo.setEnabled(AppOption.DEBUG_SHOW_EXTRA_INFO.getBoolValue());

    dumpDebugInfo.setVisible(dumpDebugInfo.isEnabled());
    add(dumpDebugInfo);
  }

//  private static void cleanKeyfile() {
//    JLabel infolabel = new JLabel("<html><center>This will delete empty BIFFs and remove<br>"
//        + "references to nonexistent BIFFs.<br><br>" + "Warning: Your existing " + ResourceFactory.getKeyfile()
//        + " will be overwritten!<br><br>Continue?</center></html>");
//    String options[] = { "Continue", "Cancel" };
//    if (JOptionPane.showOptionDialog(NearInfinity.getInstance(), infolabel, "Keyfile cleanup",
//        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]) != 0)
//      return;
//    boolean updated = ResourceFactory.getKeyfile().cleanUp();
//    if (!updated)
//      JOptionPane.showMessageDialog(NearInfinity.getInstance(), "No cleanup necessary", "Cleanup completed",
//          JOptionPane.INFORMATION_MESSAGE);
//    else {
//      try {
//        ResourceFactory.getKeyfile().write();
//        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Operation completed successfully",
//            "Cleanup completed", JOptionPane.INFORMATION_MESSAGE);
//      } catch (IOException e) {
//        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error writing keyfile", "Error",
//            JOptionPane.ERROR_MESSAGE);
//        Logger.error(e);
//      }
//    }
//  }

  public void gameLoaded() {
    toolSplProtEncoder.setEnabled(Profile.isEnhancedEdition() && ResourceFactory.resourceExists("SPLPROT.2DA"));
    toolCreatureAnimationCreator.setEnabled(Profile.isEnhancedEdition());
  }

  public JMenuItem getDumpDebugInfoItem() {
    return dumpDebugInfo;
  }

  public boolean getShowDebugExtraInfo() {
    return dumpDebugInfo.isEnabled();
  }

  public void setShowDebugExtraInfo(boolean show) {
    dumpDebugInfo.setEnabled(show);
    dumpDebugInfo.setVisible(show);
  }

  public void storePreferences() {
    AppOption.DEBUG_SHOW_EXTRA_INFO.setValue(dumpDebugInfo.isEnabled());
  }

  @Override
  public BrowserMenuBar getMenuBar() {
    return menuBar;
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == toolCreatureBrowser) {
      ChildFrame.show(CreatureBrowser.class, CreatureBrowser::new);
    } else if (event.getSource() == toolInfinityAmp) {
      ChildFrame.show(InfinityAmpPlus.class, InfinityAmpPlus::new);
    } else if (event.getSource() == toolIDSBrowser) {
      ChildFrame.show(IdsBrowser.class, IdsBrowser::new);
    } else if (event.getSource() == toolClipBoard) {
      ChildFrame.setVisible(ClipboardViewer.class, toolClipBoard.isSelected(), () -> {
        final ClipboardViewer view = new ClipboardViewer();
        view.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            toolClipBoard.setSelected(false);
          }
        });
        return view;
      });
    } else if (event.getSource() == toolConsole) {
      ChildFrame.setVisible(DebugConsole.class, toolConsole.isSelected(), () -> {
        final DebugConsole con = new DebugConsole();
        con.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            toolConsole.setSelected(false);
          }
        });
        return con;
      });
    } else if (event.getSource() == dumpDebugInfo) {
      BrowserMenuBar.dumpDebugInfo();
//    } else if (event.getSource() == toolCleanKeyfile) {
//       cleanKeyfile();
    } else if (event.getSource() == toolDropZone) {
      ChildFrame.show(BcsDropFrame.class, BcsDropFrame::new);
    } else if (event.getSource() == toolSplProtEncoder) {
      ChildFrame.show(SplProtFrame.class, SplProtFrame::new);
    } else if (event.getSource() == toolCheckAllDialog) {
      new DialogChecker(false, NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckOverrideDialog) {
      new DialogChecker(true, NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckResRef) {
      new ResRefChecker();
    } else if (event.getSource() == toolCheckCREInv) {
      new CreInvChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckIDSRef) {
      new IDSRefChecker();
    } else if (event.getSource() == toolCheckIDSBCSRef) {
      new BCSIDSChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckScripts) {
      new ScriptChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckStructs) {
      new StructChecker();
    } else if (event.getSource() == toolCheckStringUse) {
      new StringUseChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckStringSounds) {
      new StringSoundsChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckStringDuplicates) {
      new StringDuplicatesChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckStringValid) {
      new StringValidationChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolCheckStringIndex) {
      new StrrefIndexChecker();
    } else if (event.getSource() == toolCheckFileUse) {
      new ResourceUseChecker(NearInfinity.getInstance());
    } else if (event.getSource() == toolMassExport) {
      new MassExporter();
    } else if (event.getSource() == toolCheckEffectsIndex) {
      new EffectsIndexChecker();
    } else if (event.getSource() == toolCheckEffectIsValid) {
      new EffectValidationChecker();
    } else if (event.getSource() == toolConvImageToPvrz) {
      ChildFrame.show(ConvertToPvrz.class, ConvertToPvrz::new);
    } else if (event.getSource() == toolConvImageToTis) {
      ChildFrame.show(ConvertToTis.class, ConvertToTis::new);
    } else if (event.getSource() == toolConvImagesToTis) {
      ChildFrame.show(ConvertToTisBatch.class, ConvertToTisBatch::new);
    } else if (event.getSource() == toolConvImageToMos) {
      ChildFrame.show(ConvertToMos.class, ConvertToMos::new);
    } else if (event.getSource() == toolConvImageToBmp) {
      ChildFrame.show(ConvertToBmp.class, ConvertToBmp::new);
    } else if (event.getSource() == toolConvImageToBam) {
      ChildFrame.show(ConvertToBam.class, ConvertToBam::new);
    } else if (event.getSource() == toolCreatureAnimationCreator) {
      ChildFrame.show(CreatureAnimationCreator.class, CreatureAnimationCreator::new);
    }
  }
}
