// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.updater;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.util.Misc;

/**
 * Provides a dialog for configuring update-relevant data.
 */
public class UpdaterSettings extends JDialog
{
  private final JComboBox<String> cbUpdateInterval = new JComboBox<>(new String[]{
      "Once per session", "Daily", "Once per week", "Once per month"});
  private final JTextField tfProxyAddress = new JTextField(12);
  private final JTextField tfProxyPort = new JTextField(6);
  private final JCheckBox cbStableOnly = new JCheckBox("Consider stable releases only");
  private final JCheckBox cbAutoUpdate = new JCheckBox("Automatically check for updates");
  private final JCheckBox cbProxyEnabled = new JCheckBox("Enable Proxy");
  private final JButton bOK = new JButton("OK");
  private final JButton bCancel = new JButton("Cancel");
  private final Listeners listeners = new Listeners();
  private final Server server = new Server(Updater.getMaxServerCount());

  private boolean retVal = false;

  public static boolean showDialog(Window owner)
  {
    UpdaterSettings dlg = new UpdaterSettings(owner);
    try {
      dlg.setVisible(true);
      return dlg.retVal;
    } finally {
      dlg = null;
    }
  }


  private UpdaterSettings(Window owner)
  {
    super(owner, "Update settings", Dialog.ModalityType.APPLICATION_MODAL);
    init();
    loadSettings();
  }

  private Server getServer() { return server; }

  private Listeners getListeners() { return listeners; }

  private void init()
  {
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setResizable(true);
    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    // ESC closes dialog
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE");
    getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        cancel();
      }
    });

    JLabel lUpdateInterval = new JLabel("Update interval:");
    JLabel lProxyAddress = new JLabel("Address:");
    JLabel lProxyPort = new JLabel("Port:");
    cbAutoUpdate.addActionListener(getListeners());
    cbStableOnly.setToolTipText("Stable versions are released much less often and don't include " +
                                "the latest features and bugfixes.");
    cbProxyEnabled.addActionListener(getListeners());
    bOK.setPreferredSize(bCancel.getPreferredSize());
    bOK.addActionListener(getListeners());
    bCancel.addActionListener(getListeners());

    // configuring update server panel
    JPanel pServer = new JPanel(new GridBagLayout());
    pServer.setBorder(BorderFactory.createTitledBorder("Update servers"));
    for (int i = 0; i < server.getServerCount(); i++) {
      JLabel label = new JLabel(String.format("Server %d", i+1));

      gbc = ViewerUtil.setGBC(gbc, 0, i, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 0), 0, 0);
      pServer.add(label, gbc);
      gbc = ViewerUtil.setGBC(gbc, 1, i, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 16, 0, 0), 0, 0);
      pServer.add(server.getTextField(i), gbc);
      gbc = ViewerUtil.setGBC(gbc, 2, i, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pServer.add(server.getCheckButton(i), gbc);
    }
    gbc = ViewerUtil.setGBC(gbc, 0, Updater.getMaxServerCount(), 3, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 4, 4, 8), 0, 0);
    pServer.add(cbStableOnly, gbc);

    // configuring proxy server panel
    JPanel pProxy = new JPanel(new GridBagLayout());
    pProxy.setBorder(BorderFactory.createTitledBorder("Proxy server"));
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 4, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 8), 0, 0);
    pProxy.add(cbProxyEnabled, gbc);

    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 8, 8, 0), 0, 0);
    pProxy.add(lProxyAddress, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 8, 8, 0), 0, 0);
    pProxy.add(tfProxyAddress, gbc);
    gbc = ViewerUtil.setGBC(gbc, 2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 16, 8, 0), 0, 0);
    pProxy.add(lProxyPort, gbc);
    gbc = ViewerUtil.setGBC(gbc, 3, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 8, 8, 8), 0, 0);
    pProxy.add(tfProxyPort, gbc);

    // configuring auto update panel
    JPanel pUpdate = new JPanel(new GridBagLayout());
    pUpdate.setBorder(BorderFactory.createTitledBorder("Auto Update"));
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 3, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 8), 0, 0);
    pUpdate.add(cbAutoUpdate, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(12, 8, 8, 0), 0, 0);
    pUpdate.add(lUpdateInterval, gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(12, 8, 8, 0), 0, 0);
    pUpdate.add(cbUpdateInterval, gbc);
    gbc = ViewerUtil.setGBC(gbc, 2, 1, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(12, 0, 8, 0), 0, 0);
    pUpdate.add(new JPanel(), gbc);

    // configuring dialog buttons panel
    JPanel pButtons = new JPanel(new GridBagLayout());
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(new JPanel(), gbc);
    gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(bOK, gbc);
    gbc = ViewerUtil.setGBC(gbc, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 0), 0, 0);
    pButtons.add(bCancel, gbc);
    gbc = ViewerUtil.setGBC(gbc, 3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(new JPanel(), gbc);

    // putting all together
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
    add(pServer, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
    add(pProxy, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
    add(pUpdate, gbc);
    gbc = ViewerUtil.setGBC(gbc, 0, 3, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.HORIZONTAL, new Insets(8, 8, 8, 8), 0, 0);
    add(pButtons, gbc);

    pack();
    setMinimumSize(getPreferredSize());
    setSize(new Dimension(getPreferredSize().width * 3 / 2, getPreferredSize().height));
    setLocationRelativeTo(getOwner());
  }

  // Loads Updater settings into dialog fields
  private void loadSettings()
  {
    // getting server list
    List<String> serverList = Updater.getInstance().getServerList();
    for (int i = 0; i < Updater.getMaxServerCount(); i++) {
      String url = (i < serverList.size()) ? serverList.get(i) : "";
      server.getTextField(i).setText(url);
      server.getTextField(i).setCaretPosition(0);
      server.setServerValidated(i, true);
    }
    cbStableOnly.setSelected(Updater.getInstance().isStableOnly());

    // getting auto update settings
    cbAutoUpdate.setSelected(Updater.getInstance().isAutoUpdateCheckEnabled());
    cbUpdateInterval.setSelectedIndex(Updater.getInstance().getAutoUpdateCheckInterval());
    cbUpdateInterval.setEnabled(cbAutoUpdate.isSelected());

    // getting proxy settings
    Proxy proxy = Updater.getInstance().getProxy(true);
    if (proxy != null) {
      InetSocketAddress addr = (InetSocketAddress)proxy.address();
      cbProxyEnabled.setSelected(Updater.getInstance().isProxyEnabled());
      tfProxyAddress.setText(addr.getHostName());
      tfProxyPort.setText(Integer.toString(addr.getPort()));
    } else {
      cbProxyEnabled.setSelected(false);
      tfProxyAddress.setText("");
      tfProxyPort.setText("");
    }
    tfProxyAddress.setEnabled(cbProxyEnabled.isSelected());
    tfProxyPort.setEnabled(cbProxyEnabled.isSelected());
  }

  // Applies dialog settings to Updater
  private void saveSettings()
  {
    // saving server list
    List<String> serverList = Updater.getInstance().getServerList();
    serverList.clear();
    for (int i = 0; i < Updater.getMaxServerCount(); i++) {
      String url = server.getServerUrl(i);
      if (Utils.isUrlValid(url)) {
        // skip duplicate server URLs
        boolean isSame = false;
        for (Iterator<String> iter = serverList.iterator(); iter.hasNext();) {
          if (Updater.isSameServer(url, iter.next())) {
            isSame = true;
            break;
          }
        }
        if (!isSame) {
          serverList.add(url);
        }
      }
    }
    if (cbStableOnly.isSelected() != Updater.getInstance().isStableOnly()) {
      // reset cached release info if release type changed
      Updater.getInstance().setStableOnly(cbStableOnly.isSelected());
      Updater.getInstance().setCurrentHash(null);
      Updater.getInstance().setCurrentTimeStamp(null);
    }

    // saving auto update settings
    Updater.getInstance().setAutoUpdateCheckEnabled(cbAutoUpdate.isSelected());
    Updater.getInstance().setAutoUpdateCheckInterval(cbUpdateInterval.getSelectedIndex());

    // saving proxy settings
    String addr = tfProxyAddress.getText();
    int port = Misc.toNumber(tfProxyPort.getText(), -1);
    Updater.getInstance().setProxyEnabled(cbProxyEnabled.isSelected());
    Updater.getInstance().setProxy(addr, port);
  }

  // Action when clicking OK button
  private void accept()
  {
    if (validateSettings()) {
      saveSettings();
      retVal = true;
      setVisible(false);
    }
  }

  // Action when closing dialog without accepting settings
  private void cancel()
  {
    retVal = false;
    setVisible(false);
  }

  // Throws an exception with message if input data contains errors.
  private boolean validateSettings()
  {
    // checking update servers
    for (int i = 0; i < Updater.getMaxServerCount(); i++) {
      if (!server.isValidated(i)) {
        if (!Utils.isSecureUrl(server.getServerUrl(i))) {
          String msg = String.format("Server %d does not specify a secure connection (https).\n", i+1) +
                       "Do you still want to use it?";
          if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(this, msg, "Warning",
                                                                      JOptionPane.YES_NO_OPTION,
                                                                      JOptionPane.WARNING_MESSAGE)) {
            server.getTextField(i).requestFocusInWindow();
            return false;
          }
        }

        if (!validateServer(i)) {
          server.getTextField(i).requestFocusInWindow();
          return false;
        }
      }
    }

    // adding predefined default servers if no valid servers are available
    int numValidServers = 0;
    for (int i = 0; i < Updater.getMaxServerCount(); i++) {
      String s = server.getServerUrl(i).trim();
      if (!s.isEmpty()) {
        numValidServers++;
      }
    }
    if (numValidServers == 0) {
      String[] servers = Updater.getDefaultServerList();
      for (int i = 0; i < servers.length; i++) {
        server.setServerUrl(i, servers[i]);
      }
    }

    // checking proxy settings
    if (!tfProxyAddress.getText().trim().isEmpty()) {
      int port = Misc.toNumber(tfProxyPort.getText().trim(), -1);
      if (port >= 0 && port < 65536) {
        tfProxyPort.setText(Integer.toString(port));
      } else {
        JOptionPane.showMessageDialog(this, "Invalid proxy server port.", "Error",
                                      JOptionPane.ERROR_MESSAGE);
        tfProxyPort.requestFocusInWindow();
        return false;
      }

      String s = tfProxyAddress.getText().trim();
      // protocol prefixes are not needed
      final String[] prefix = {"http://", "https://"};
      for (int i = 0; i < prefix.length; i++) {
        if (s.startsWith(prefix[i])) {
          s = s.substring(prefix[i].length());
          break;
        }
      }
      if (!s.isEmpty()) {
        String url = null;
        try {
          InetSocketAddress addr = new InetSocketAddress(s, port);
          url = addr.getHostName();
        } catch (Exception e) {
        }
        if (url != null && !url.isEmpty()) {
          tfProxyAddress.setText(url);
        } else {
          JOptionPane.showMessageDialog(this, "Invalid proxy server address.", "Error",
                                        JOptionPane.ERROR_MESSAGE);
          tfProxyAddress.requestFocusInWindow();
          return false;
        }
      }
    }

    return true;
  }

  // Returns true if the specified server is valid or does not exist, false otherwise.
  private boolean validateServer(int index)
  {
    try {
      WindowBlocker.blockWindow(this, true);
      if (index >= 0 && index < Updater.getMaxServerCount()) {
        String msg = null;
        try {
          if (!server.isValidated(index)) {
            String link = server.getServerUrl(index).trim();
            String newLink = Updater.getInstance().getValidatedUpdateUrl(link);
            if (newLink != null && !newLink.isEmpty()) {
              server.setServerUrl(index, newLink);
              return true;
            } else {
              return false;
            }
          }
        } catch (MalformedURLException e) {
          msg = "Server URL is incorrect";
        } catch (IOException e) {
          msg = "Server URL does not point to a valid update server";
        } catch (Throwable t) {
          msg = "Unknown error";
        }
        if (msg != null) {
          JOptionPane.showMessageDialog(this, String.format("Server %d: %s.", index+1, msg),
                                        "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
      }
    } finally {
      WindowBlocker.blockWindow(this, false);
    }
    return true;
  }


//-------------------------- INNER CLASSES --------------------------

  // Manages all listener objects
  private class Listeners implements ActionListener, DocumentListener
  {
    public Listeners() {}

    @Override
    public void actionPerformed(ActionEvent e)
    {
      if (e.getSource() == cbAutoUpdate) {
        cbUpdateInterval.setEnabled(cbAutoUpdate.isSelected());
      } else if (e.getSource() == cbProxyEnabled) {
        boolean enabled = cbProxyEnabled.isSelected();
        tfProxyAddress.setEnabled(enabled);
        tfProxyPort.setEnabled(enabled);
      } else if (e.getSource() == bOK) {
        accept();
      } else if (e.getSource() == bCancel) {
        cancel();
      } else if (e.getSource() instanceof JButton) {
        int index = getServer().indexOf((JButton)e.getSource());
        if (index >= 0) {
          String url = getServer().getServerUrl(index);
          if (!Utils.isUrlValid(url)) {
            JOptionPane.showMessageDialog(UpdaterSettings.this, "The server URL is invalid.",
                                          "Error", JOptionPane.ERROR_MESSAGE);
            getServer().getTextField(index).requestFocusInWindow();
            return;
          }

          if (!Utils.isSecureUrl(url)) {
            String msg = "The server address does not specify a secure connection (https).\n" +
                         "Do you still want to use it?";
            if (JOptionPane.showConfirmDialog(UpdaterSettings.this, msg, "Warning", JOptionPane.YES_NO_OPTION,
                                              JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
              return;
            }
          }

          getServer().setServerValidated(index, validateServer(index));
        }
      }
    }

    @Override
    public void insertUpdate(DocumentEvent e)
    {
      getServer().setServerValidated(getServer().indexOf(e.getDocument()), false);
    }

    @Override
    public void removeUpdate(DocumentEvent e)
    {
      getServer().setServerValidated(getServer().indexOf(e.getDocument()), false);
    }

    @Override
    public void changedUpdate(DocumentEvent e)
    {
      getServer().setServerValidated(getServer().indexOf(e.getDocument()), false);
    }
  }

  // Manages server URL entries
  private class Server
  {
    private final List<JTextField> listServer = new ArrayList<JTextField>();
    private final List<JButton> listCheck = new ArrayList<JButton>();
    private final List<Boolean> listValidated = new ArrayList<Boolean>();

    private final int numServers;

    public Server(int count)
    {
      numServers = Math.max(0, count);
      for (int i = 0; i < numServers; i++) {
        JTextField tf = new JTextField();
        tf.getDocument().addDocumentListener(getListeners());
        listServer.add(tf);
        JButton b = new JButton("Check");
        b.addActionListener(getListeners());
        listCheck.add(b);
        listValidated.add(Boolean.valueOf(false));
      }
    }

    // Returns number of available servers
    public int getServerCount() { return numServers; }

    // Returns the URL of the specified server
    public String getServerUrl(int index)
    {
      if (index >= 0 && index < getServerCount()) {
        return listServer.get(index).getText();
      }
      return "";
    }

    // Replaces the URL of the specified server
    public void setServerUrl(int index, String url)
    {
      if (index >= 0 && index < getServerCount()) {
        if (url == null) url = "";
        listServer.get(index).setText(url);
      }
    }

    // Returns the text field of the specified server
    public JTextField getTextField(int index)
    {
      if (index >= 0 && index < getServerCount()) {
        return listServer.get(index);
      }
      return null;
    }

    // Returns the Check button of the specified server
    public JButton getCheckButton(int index)
    {
      if (index >= 0 && index < getServerCount()) {
        return listCheck.get(index);
      }
      return null;
    }

    // Returns whether the server URL has already been validated
    public boolean isValidated(int index)
    {
      if (index >= 0 && index < getServerCount()) {
        return listValidated.get(index).booleanValue();
      }
      return false;
    }

    // Sets whether the server URL has been validated
    public void setValidated(int index, boolean set)
    {
      if (index >= 0 && index < getServerCount()) {
        listValidated.set(index, Boolean.valueOf(set));
      }
    }

    // Marks specified server as valid or invalid and sets state of associated text field and button.
    public void setServerValidated(int index, boolean set)
    {
      if (index >= 0 && index < getServerCount()) {
        Document doc = getTextField(index).getDocument();
        setValidated(index, set || doc.getLength() == 0);
        getCheckButton(index).setEnabled(!set && doc.getLength() > 0);
      }
    }

    // Returns the server index of the specified text field document
    public int indexOf(Document doc)
    {
      if (doc != null) {
        for (int i = 0, size = listServer.size(); i < size; i++) {
          if (listServer.get(i).getDocument() == doc) {
            return i;
          }
        }
      }
      return -1;
    }

    // Returns the server index of the specified Check button
    public int indexOf(JButton b)
    {
      return listCheck.indexOf(b);
    }

//    // Returns the server index of the specified text field
//    public int indexOf(JTextField tf)
//    {
//      return listServer.indexOf(tf);
//    }
  }
}
