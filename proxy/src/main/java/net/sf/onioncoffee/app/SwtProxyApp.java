package net.sf.onioncoffee.app;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ResourceBundle;

import net.sf.onioncoffee.Circuit;
import net.sf.onioncoffee.Proxy;
import net.sf.onioncoffee.Server;
import net.sf.onioncoffee.TCPStream;
import net.sf.onioncoffee.TCPStreamProperties;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;

public class SwtProxyApp extends Proxy {
    private static ResourceBundle RESOURCES = ResourceBundle.getBundle("SwtProxyApp");
    private static String[] CIRCUIT_COLUMN_TITLES = { "ID", "Size", "State" };
    private static Integer[] CIRCUIT_COLUMN_WIDTHS = { 75, 80 };

    private final Display display = new Display();

    private Shell mainwindow = null;
    private Label serverCount;
    private Label circuitCount;
    private Label streamCount;
    private Table circuitTable;


    public SwtProxyApp() {
        SwtUtil.openSplash(display, "/images/tor_sticker.png", new SplashCallback());
        while (null == mainwindow || !mainwindow.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
        executor.shutdown();
    }

    public class SplashCallback extends SwtUtil.SplashCallback {
        public void initApp() {
            this.setStatusText("Initializing Proxy");
            this.setProgressPercent(.02f);
            // spawns idle circuits
            //executor.submit(new CircuitAndStreamManager());
            executor.submit(new DisplayUpdateThread());
            executor.submit(new HTTPProxy(8088, executor, SwtProxyApp.this));
            while (!directory.isValid()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { }
            }
            openMainWindow(display);
        }
    }
    
    protected class DisplayUpdateThread implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("Display Update Thread");
            while (!executor.isTerminated()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) { }
                Display.getDefault().syncExec(new Runnable() {
                    public void run() {
                        serverCount.setText(getServerCountString());

                        Collection<Circuit> circuits = new HashSet<Circuit>(getCurrentCircuits());

                        circuitCount.setText(Integer.toString(circuits.size()));
                        streamCount.setText(Integer.toString(getCurrentStreams().size()));

                        Map<Circuit, TableItem> map = new HashMap<Circuit, TableItem>();
                        Map<TableItem, Integer> map2 = new HashMap<TableItem, Integer>();
                        for (int i = 0; i < circuitTable.getItemCount(); ++i) {
                            map2.put(circuitTable.getItem(i), i);
                        }
                        for (TableItem item : circuitTable.getItems()) {
                            map.put((Circuit) item.getData(), item);
                        }

                        for (Circuit c : circuits) {
                            TableItem item;
                            if (map.containsKey(c)) {
                                item = map.get(c);
                            } else {
                                item = new TableItem(circuitTable, SWT.NONE);
                                item.setData(c);
                            }
                            formatItem(c, item);
                        }

                        for (int i = circuitTable.getItems().length - 1; i >= 0; --i) {
                            TableItem item = circuitTable.getItems()[i];
                            if (!circuits.contains(item.getData())) {
                                circuitTable.remove(i);
                            }
                        }
                    }

                    private void formatItem(Circuit c, TableItem item) {
                        item.setText(0, Integer.toHexString(c.getId()).toUpperCase());
                        item.setText(1, Integer.toString(c.getRoute().length));
                        String state = "Establishing";
                        if (c.destruct) {
                            state = "Destroyed";
                        } else if (c.closed) {
                            state = "Closed";
                        } else if (c.established) {
                            state = "Established";
                        }
                        item.setText(2, state);
                    }
                });

            }

        }

    }

    public Shell openMainWindow(Display display) {
        createShell(display);
        createMenuBar();
        createToolBar();
        mainwindow.setSize(500, 300);
        mainwindow.open();
        return mainwindow;
    }

    void createShell(Display display) {
        mainwindow = new Shell(display);
        mainwindow.setText(RESOURCES.getString("Window_title"));
        mainwindow.setLayout(new FillLayout(SWT.VERTICAL));

        {
            final Composite counters = new Composite(mainwindow, SWT.NONE);
            counters.setLayout(new GridLayout(2, false));
            Label label0 = new Label(counters, SWT.NONE);
            label0.setText("Servers");
            serverCount = new Label(counters, SWT.NONE);
            serverCount.setText(getServerCountString());
            serverCount.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
            Label label2 = new Label(counters, SWT.NONE);
            label2.setText("Circuits");
            circuitCount = new Label(counters, SWT.NONE);
            circuitCount.setText("0");
            circuitCount.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
            Label label4 = new Label(counters, SWT.NONE);
            label4.setText("Streams");
            streamCount = new Label(counters, SWT.NONE);
            streamCount.setText("0");
            streamCount.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            this.circuitTable = new Table(counters, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
            circuitTable.setLinesVisible(true);
            circuitTable.setHeaderVisible(true);
            GridData data = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
            data.heightHint = 200;
            circuitTable.setLayoutData(data);

            counters.addControlListener(new TableSizingControlAdapter(circuitTable));
            circuitTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseDoubleClick(MouseEvent e) {
                    TableItem item = TableUtil.getItemForEvent(circuitTable, e);
                    showCircuitDetail((Circuit) item.getData());
                }
            });

            for (int i = 0; i < CIRCUIT_COLUMN_TITLES.length; i++) {
                new TableColumn(circuitTable, SWT.NONE).setText(CIRCUIT_COLUMN_TITLES[i]);
            }
            for (int i = 0; i < CIRCUIT_COLUMN_WIDTHS.length; i++) {
                circuitTable.getColumn(i).setWidth(CIRCUIT_COLUMN_WIDTHS[i]);
            }
            counters.pack();
        }

        mainwindow.pack();
        mainwindow.open();
    }

    private void showCircuitDetail(Circuit data) {
        Shell serverWindow = new Shell(mainwindow, SWT.SHELL_TRIM);
        serverWindow.setText("Circuit " + Integer.toHexString(data.getId()).toUpperCase());
        serverWindow.setLayout(new FillLayout(SWT.VERTICAL));
        Table serverTable = new Table(serverWindow, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
        serverTable.setLinesVisible(true);
        serverTable.setHeaderVisible(true);
        String[] SERVER_COLUMN_TITLES = { "Fingerprint", "IP", "DirPort", "Flags", "BW Avg", "BW Burst", "BW Obs" };
        for (int i = 0; i < SERVER_COLUMN_TITLES.length; i++) {
            TableColumn column = new TableColumn(serverTable, SWT.NONE);
            column.setText(SERVER_COLUMN_TITLES[i]);
        }
        for (int i = 0; i < SERVER_COLUMN_TITLES.length; i++) {
            serverTable.getColumn(i).pack();
        }

        for (Server node : data.getRoute()) {
            TableItem item = new TableItem(serverTable, SWT.NONE);
            item.setText(0, node.getFingerprint());
            item.setText(1, node.getAddress().getHostAddress());
            item.setText(2, Integer.toString(node.getDirPort()));
            item.setText(3, node.getFlagsString());
            item.setText(4, Integer.toString(node.getBandwidthAvg()));
            item.setText(5, Integer.toString(node.getBandwidthBurst()));
            item.setText(6, Integer.toString(node.getBandwidthObserved()));
        }
        serverWindow.setVisible(true);
        serverWindow.pack();
        serverWindow.open();
    }

    Menu createFileMenu() {
        Menu bar = mainwindow.getMenuBar();
        Menu menu = new Menu(bar);
        {
            MenuItem item = new MenuItem(menu, SWT.PUSH);
            item.setText(RESOURCES.getString("Test_menuitem"));
            item.addSelectionListener(new SelectionAdapter() {
                public void widgetSelected(SelectionEvent event) {
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            Thread.currentThread().setName("Test Thread");
                            final String GET_STRING = "GET / HTTP/1.1\r\nHost: google.com\r\nConnection: close\r\nUser-Agent: Jakarta Commons-HttpClient/3.0.1\r\n\r\n";
                            final String GET_IP = "google.com";
                            try {
                                // TheUrbanSpaceman (?) deathcoreIII (?) noddy (?)
                                TCPStreamProperties target = new TCPStreamProperties(GET_IP, 80);
//                                target.setCustomRoute(getCustomRoute(new String[] { "TheUrbanSpaceman", "deathcoreIII", "noddy" } ));
                                TCPStream stream = proxyConnect(target);
                                stream.getOutputStream().write(GET_STRING.getBytes(Charsets.US_ASCII));
                                stream.getOutputStream().flush();
                                String result = new String(ByteStreams.toByteArray(stream.getInputStream()), Charsets.UTF_8);
                                System.out.println("SCORE: " + result.length());
                                stream.close();
                            } catch (Exception e) {
                                getLog().warn(e);
                                e.printStackTrace();
                            }
                        }

                        private Server[] getCustomRoute(String[] strings) {
                            Server[] retVal = new Server[strings.length];
                            for (int i = 0; i < strings.length; ++i) {
                                retVal[i] = findServer(strings[i]);
                            }
                            return retVal;
                        }

                        private Server findServer(String string) {
                            for (Server s : directory.getServers().values()) {
                                if (s.getName().equals(string)) {
                                    return s;
                                }
                            }
                            return null;
                        }

                        public boolean equals(byte[] a, int aoffset, byte[] b, int boffset, int length) {
                            if (a.length - aoffset < length || b.length - boffset < length) {
                                return false;
                            }
                            for (int i = 0; i < length; ++i) {
                                if (a[aoffset + i] != b[boffset + i]) {
                                    return false;
                                }
                            }

                            return true;
                        }

                        public int find(byte[] data, byte[] search) {
                            int searchLen = search.length;
                            int dataLen = data.length;
                            for (int i = 0; dataLen - i >= searchLen; ++i) {
                                if (equals(data, i, search, 0, search.length)) {
                                    return i;
                                }
                            }
                            return -1;
                        }
                    });
                }
            });
        }
        {
            MenuItem item = new MenuItem(menu, SWT.PUSH);
            item.setText(RESOURCES.getString("Exit_menuitem"));
            item.addSelectionListener(new SelectionAdapter() {
                public void widgetSelected(SelectionEvent event) {
                    mainwindow.close();
                    mainwindow.dispose();
                }
            });
        }
        return menu;
    }

    void createMenuBar() {
        Menu bar = new Menu(mainwindow, SWT.BAR);
        mainwindow.setMenuBar(bar);
        MenuItem fileItem = new MenuItem(bar, SWT.CASCADE);
        fileItem.setText(RESOURCES.getString("File_menuitem"));
        fileItem.setMenu(createFileMenu());
    }

    void createToolBar() {
        // toolBar = new ToolBar(mainwindow, SWT.NONE);
    }



    protected String getServerCountString() {
        return Integer.toString(directory.getValidServers().size()) + " valid servers of " + directory.getServers().size() + " known";
    }

}
