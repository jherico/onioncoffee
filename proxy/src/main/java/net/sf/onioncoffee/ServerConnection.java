package net.sf.onioncoffee;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import net.sf.onioncoffee.common.TorException;

import org.saintandreas.nio.BufferingSocketHandler;
import org.saintandreas.nio.IOProcessor;

public class ServerConnection extends BufferingSocketHandler {
    private static final String[] ENABLED_SUITES = { "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" };
    private static SSLSocketFactory SOCKET_FACTORY; {
        try {
            TrustManager[] TRUST_MANAGERS = { new TorX509TrustManager() };
            SSLContext context;
            context = SSLContext.getInstance("TLS", "SunJSSE");
            context.init(new KeyManager[] {}, TRUST_MANAGERS, null);
            SOCKET_FACTORY = context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    boolean stopped = false;
    boolean closed = false;
    private final Server server;

    private final Selector selector;
    Map<Integer, Circuit> circuits = new HashMap<Integer, Circuit>();
    private final SocketChannel sc;
    private SocketChannel sslsc = null;
    
    public ServerConnection(Server server, IOProcessor ioProcessor) throws IOException {
        this.server = server;
        selector = ioProcessor.getSelector();
        sc = SocketChannel.open();
        sc.configureBlocking(false);
        ioProcessor.addRegisteringHandler(this);
        desiredMask = SelectionKey.OP_CONNECT;
        if (sc.connect(server.getRouterAddress())) {
            addSSLWrapper();
        }
    }


    public void close() {
        this.stopped = true;
    }

    /**
     * converts a cell to bytes and transmits it over the line. received data
     * is dispatched by the class TLSDispatcher
     * 
     * @param c
     *            the cell to send
     * @exception IOException
     * @see TLSDispatcher
     */
    public void sendCell(Cell c) throws IOException {
        write(c.toByteArray());
    }

    synchronized public int getFreeCircuitId() {
        if (closed) {
            throw new IllegalStateException("ServerConnection.assign_circID(): Connection to " + server.getName() + " is closed for new circuits");
        }
        // find a free number (other than zero)
        int retVal = 0;
        for (int j = 0; retVal == 0; ++j) {
            if (j > 1000) {
                throw new IllegalStateException("ServerConnection.assign_circID(): no more free IDs");
            }

            // Deprecated: 16 bit unsigned Integers with MSB set
            // id = FirstNodeHandler.rnd.nextInt() & 0xffff | 0x8000;
            // XXX: Since the PrivateKeyHandler is gone, we don't need to consider
            // the MSB as long as we are in client mode (see main-tor-spec.txt, Section 5.1)
            retVal = new Random().nextInt() & 0xffff; // & 0x7fff;

            if (circuits.containsKey(new Integer(retVal))) {
                retVal = 0;
            }
        }
        return retVal;
    }

    /**
     * returns a free circID and save that it points to "c", save it to "c",
     * too. Throws an exception, if no more free IDs are available, or the TLS
     * connection is marked as closed.<br>
     * FIXME: replace this code with something more beautiful
     * 
     * @param c
     *            the circuit that is going to be build through this
     *            TLS-Connection
     * @return an identifier for this new circuit
     * @exception TorException
     */
    synchronized void assignCircuitId(Circuit c) {
        int ID = getFreeCircuitId();
        // assign id to circuit, memorize circuit
        c.setId(ID);
        circuits.put(new Integer(ID), c);
    }

    /**
     * marks as closed. closes if no more data or forced closed on real close:
     * kill dispatcher
     * 
     * @param force
     *            set to TRUE if established circuits shall be cut and
     *            terminated.
     */
    void close(boolean force) {
        getLog().debug("ServerConnection.close(): Closing TLS to " + server.getName());
        closed = true;
        // FIXME: a problem with (!force) is, that circuits, that are currently
        // still build up are not killed. their build-up should be stopped
        // close circuits, if forced
        for (Iterator<Map.Entry<Integer, Circuit>> itr = circuits.entrySet().iterator(); itr.hasNext();) {
            Map.Entry<Integer, Circuit> entry = itr.next();
            if (entry.getValue().close(force)) {
                itr.remove();
            }
        }
        if (force || circuits.isEmpty()) {
            try {
                sslsc.close();
                sc.close();
            } catch (IOException e) {
            }
        }
    }


    private void addSSLWrapper() throws IOException {
        SSLSocket anSSLSocket = (SSLSocket) SOCKET_FACTORY.createSocket(sc.socket(), server.getHostname(), server.getRouterPort(), true);
        anSSLSocket.setEnabledCipherSuites(ENABLED_SUITES);
        sslsc = anSSLSocket.getChannel();
        sslsc.configureBlocking(false);
        getSocketChannel().register(getSelector(), SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
    }

    public SocketChannel getSocketChannel() {
        return sslsc != null ? sslsc : sc;
        
    }

    protected Selector getSelector() {
        return selector;
    }

    @Override
    public void onConnect(SelectionKey sk) throws IOException {
        super.onConnect(sk);
        addSSLWrapper();
    }
    
    @Override
    public void onWrite(SelectionKey sk) throws IOException {
        super.onWrite(sk);
    }

    @Override
    public void onRead(SelectionKey sk) throws IOException {
        super.onRead(sk);
        if (inBuffer.size() >= Cell.CELL_TOTAL_SIZE) {
            byte[] data = inBuffer.toByteArray();
            int remaining = data.length;
            int offset = 0;
            while ( remaining >= Cell.CELL_TOTAL_SIZE) {
                Cell c = Cell.read(data, offset, circuits);
                onCell(c);
                offset += Cell.CELL_TOTAL_SIZE;
                remaining -= Cell.CELL_TOTAL_SIZE;
            }
            inBuffer = new ByteArrayOutputStream();
            inBuffer.write(data, offset, remaining);
        }
    }

    private void onCell(Cell cell) {
        if (getLog().isTraceEnabled()) {
            if (cell.command != Cell.CellType.CELL_RELAY) {
                getLog().trace("TLSDispatcher.run: received cell with command " + cell.command + " from " + server.getName());
            } else {
                getLog().trace("TLSDispatcher.run: received relay cell of type " + ((CellRelay)cell).relayCommand + " from " + server.getName());
            }
        }
            
        if (cell.command == Cell.CellType.CELL_PADDING) {
            return;
        }

        if (cell.getCircuit() == null) {
            getLog().warn("TLSDispatcher.run: received cell for circuit " + cell.circuitId + " from " + server.getName() + ". But no such circuit exists.");
            return;
        }

        cell.getCircuit().onCell(cell);
    }

}
