/**
 * OnionCoffee - Anonymous Communication through TOR Network
 * Copyright (C) 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
package net.sf.onioncoffee.proxy;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import net.sf.onioncoffee.Circuit;
import net.sf.onioncoffee.Directory;
import net.sf.onioncoffee.TCPStream;
import net.sf.onioncoffee.Config;

@SuppressWarnings("serial")
public class MainWindow extends JFrame implements ActionListener {
    // frame dimension
    static final int INIT_WIDTH = 960;
    static final int INIT_HEIGHT = 500;

    private static final String aboutString = "TorJava is brought to you by Workpackage 10.1\nhttp://www.primeproject.eu.org/\n\n"
            + "This product includes GeoLite data created by MaxMind, available from http://www.maxmind.com/.";

    static final Image[] icons = { Toolkit.getDefaultToolkit().getImage("bandit.gif"), Toolkit.getDefaultToolkit().getImage("cool.gif"),
            Toolkit.getDefaultToolkit().getImage("sad.gif"), Toolkit.getDefaultToolkit().getImage("smile.gif"), Toolkit.getDefaultToolkit().getImage("confused4.gif"), };

    // SysTrayMenu menu;
    int currentIndexIcon;
    int currentIndexTooltip;

    static private JCheckBoxMenuItem useTorMenuItem = null;
    static private JCheckBoxMenuItem processHTTPHeadersMenuItem = null;
    private JTree circuitsAndStreams;
    private DefaultMutableTreeNode top;
    private WorldMap map;
    private JTextArea textLogs;
    private final TrayIcon trayIcon;

    
    public MainWindow() throws Exception {
        super("TorJava Main Window");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        ImageIcon icon = null;
        JTabbedPane tabbedPane = new JTabbedPane();
        JPanel panel1 = new JPanel(false);
        map = new WorldMap();
        panel1.setLayout(new GridLayout(1, 1));
        panel1.add(map);
        tabbedPane.addTab("The World", icon, panel1, "Displays a map of the world");
        // circuits and streams
        top = new DefaultMutableTreeNode("List of Circuits");
        circuitsAndStreams = new JTree(top);
        JScrollPane panel2 = new JScrollPane(circuitsAndStreams);

        new Timer(Config.guiUpdateIntervalMilliSeconds, circuitsAndStreamsUpdater).start();

        tabbedPane.addTab("Circuits and Streams", icon, panel2, "Shows internal information");
        textLogs = new JTextArea();
        textLogs.setEditable(false);
        JScrollPane paneLogs = new JScrollPane(textLogs, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        tabbedPane.addTab("Logs", icon, paneLogs, "Shows even more internal information");
        getContentPane().setLayout(new GridLayout(1, 1));
        getContentPane().add(tabbedPane);
        // main menu
        JMenuBar menuBar;
        JMenu menu;
        JMenuItem menuItem;
        menuBar = new JMenuBar();
        menu = new JMenu("Programm");
        menu.getAccessibleContext().setAccessibleDescription("All major operations");
        menuItem = new JMenuItem("Reload Config");
        menuItem.getAccessibleContext().setAccessibleDescription("Reload the configuration file");
        menuItem.addActionListener(this);
        menu.add(menuItem);
        menuItem = new JMenuItem("Clean Log");
        menuItem.getAccessibleContext().setAccessibleDescription("Clean the Log Window");
        menuItem.addActionListener(this);
        menu.add(menuItem);
        menuItem = new JMenuItem("Shutdown");
        menuItem.getAccessibleContext().setAccessibleDescription("Shutdown this Proxy");
        menuItem.addActionListener(this);
        menu.add(menuItem);
        menuBar.add(menu);
        menu = new JMenu("Privacy");
        menu.getAccessibleContext().setAccessibleDescription("Operations refering to the set of all TOR-Nodes");
        useTorMenuItem = new JCheckBoxMenuItem("Use Tor", true);
        useTorMenuItem.getAccessibleContext().setAccessibleDescription("(un)check to use Tor or surf without Tor (network layer privacy)");
        useTorMenuItem.addActionListener(this);
        menu.add(useTorMenuItem);
        /*
         * menuItem = new JMenuItem("Reload Directory");
         * menuItem.getAccessibleContext().setAccessibleDescription(
         * "Reload the complete directory (currently not implemented)");
         * menuItem.addActionListener(this); menuItem.setEnabled(false);
         * menu.add(menuItem);
         */
        processHTTPHeadersMenuItem = new JCheckBoxMenuItem("Process HTTP Headers", true);
        processHTTPHeadersMenuItem.getAccessibleContext().setAccessibleDescription("Filter/Process HTTP Headers (application layer privacy)");
        processHTTPHeadersMenuItem.addActionListener(this);
        menu.add(processHTTPHeadersMenuItem);
        menuBar.add(menu);
        menu = new JMenu("Support");
        menu.getAccessibleContext().setAccessibleDescription("Get help, receive information, ask for support");
        menuItem = new JMenuItem("Send Bugreport");
        menuItem.getAccessibleContext().setAccessibleDescription("Give developers more information to squash a bug in TorJava");
        menuItem.addActionListener(this);
        menu.add(menuItem);
        menuItem = new JMenuItem("Help");
        menuItem.getAccessibleContext().setAccessibleDescription("Get help using this program.");
        menuItem.addActionListener(this);
        menuItem.setEnabled(false);
        menu.add(menuItem);
        menuItem = new JMenuItem("About");
        menuItem.getAccessibleContext().setAccessibleDescription("Get information about this software");
        menuItem.addActionListener(this);
        menu.add(menuItem);
        menuBar.add(menu);
        setJMenuBar(menuBar);
        // create the menu for the systray icon

        PopupMenu popup = new PopupMenu();
        MenuItem defaultItem = new MenuItem("Exit");
        defaultItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        popup.add(defaultItem);
        trayIcon = new TrayIcon(icons[1], "Tray Demo", popup);
        trayIcon.setImage(icons[2]);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    setVisible(!isVisible());
                }
            }
        });
        SystemTray.getSystemTray().add(trayIcon);

        // pack();
        // Dimension dimMy = getSize();
        // setLocation( new Point( dimScreen.width-dimMy.width , 0 ));
        Dimension dimScreen = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds(dimScreen.width - INIT_WIDTH, 0, INIT_WIDTH, INIT_HEIGHT);
        setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("About")) {
            JOptionPane.showMessageDialog(this, aboutString, "About TorJava", JOptionPane.PLAIN_MESSAGE);
        } else if (e.getActionCommand().equals("Show/hide Window")) {
            if (this.isVisible()) {
                setVisible(false);
            } else {
                setVisible(true);
            }
            ;
        } else if (e.getActionCommand().equals("Clean Log")) {
            textLogs.setText("");
        } else if (e.getActionCommand().equals("Shutdown")) {
            int i = JOptionPane.showConfirmDialog(this, "Are you sure you want to leave?", "Shutdown Tor", JOptionPane.YES_NO_OPTION);
            if (i == JOptionPane.YES_OPTION) {
                try {
//                  tor.close();
                } catch (Exception ex) { /* silently ignore */
                }
                System.exit(0);
            }
        } else if (e.getActionCommand().equals("Send Bugreport")) {
            try {
                new BugReportWindow();
            } catch (Exception ex) {
            }
        } else if (e.getActionCommand().equals("Use Tor")) {
            // button toggles itself
            // useTorMenuItem.setState( ! useTorMenuItem.getState() );
        } else if (e.getActionCommand().equals("Process HTTP Headers")) {
            // button toggles itself
            // useTorMenuItem.setState( ! useTorMenuItem.getState() );
        } else {
            System.err.println("caught action '" + e.getActionCommand() + "'");
        }
    }

    static boolean useTor() {
        if (useTorMenuItem == null) {
            return true;
        }
        return useTorMenuItem.getState();
    }

    static boolean processHTTPHeaders() {
        if (processHTTPHeadersMenuItem == null) {
            return true;
        }
        return processHTTPHeadersMenuItem.getState();
    }

    synchronized void updateCircuitsAndStreams() {
//        map.newTemp();
//        top.removeAllChildren();
//        try {
//      for (Circuit circ : Directory.get().getCurrentCircuits()) {
//                DefaultMutableTreeNode circNode = new DefaultMutableTreeNode(circ);
//                map.addCircuit(circ);
//                top.add(circNode);
//                for (TCPStream stream : circ.streams.values()) {
//                    circNode.add(new DefaultMutableTreeNode(stream));
//                    map.addStream(circ, stream);
//                }
//            }
//        } catch (Exception e) {
//            // catches ConcurrentModificationException ...
//        }
//
//        // expanding tree to the last leaf from the root
//        DefaultMutableTreeNode root;
//        root = (DefaultMutableTreeNode) circuitsAndStreams.getModel().getRoot();
//        circuitsAndStreams.scrollPathToVisible(new TreePath(root.getLastLeaf().getPath()));
//        circuitsAndStreams.setCellRenderer(new ColourfullTreeRenderer());
//        circuitsAndStreams.updateUI();
//        // display new map
//        map.flipImage();
    }

    ActionListener circuitsAndStreamsUpdater = new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
            updateCircuitsAndStreams();
        }
    };

    class ColourfullTreeRenderer extends JLabel implements TreeCellRenderer {
        /**
     * 
     */
        private static final long serialVersionUID = -5305410067164081457L;

        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean bSelected, boolean bExpanded, boolean bLeaf, int iRow, boolean bHasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            setForeground(Color.BLACK);
            Object displayObject = node.getUserObject();
            if (displayObject instanceof Circuit) {
                Circuit c = (Circuit) displayObject;
                if (!c.established) {
                    setForeground(Color.BLUE);
                }
                if (c.closed) {
                    setForeground(Color.RED);
                }
                setText(c.toString());
            } else if (displayObject instanceof TCPStream) {
                TCPStream stream = (TCPStream) displayObject;
                if (stream.closed) {
                    setForeground(Color.RED);
                } else {
                    if (!stream.established) {
                        setForeground(Color.BLUE);
                    }
                }
                setText(stream.sp.getDestination());
            } else {
                setText((String) displayObject);
            }
            return this;
        }
    }
}
// vim: et
