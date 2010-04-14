package net.sf.onioncoffee;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import net.sf.onioncoffee.common.TorException;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSession;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerConnection extends IoHandlerAdapter {
    private static final String[] ENABLED_SUITES = { 
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",  //
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA"  //
    };
    private static SSLContext SSL_CONTEXT; {
        try {
            TrustManager[] TRUST_MANAGERS = { new TorX509TrustManager() };
            SSL_CONTEXT = SSLContext.getInstance("TLS", "SunJSSE");
            SSL_CONTEXT.init(new KeyManager[] {}, TRUST_MANAGERS, null);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }


    boolean stopped = false;
    boolean closed = false;
    private final Server server;
    private final IoSession session;

    Map<Integer, Circuit> circuits = new HashMap<Integer, Circuit>();
    
    public ServerConnection(Server server, IoProcessor<NioSession> procssor) throws IOException {
        this.server = server;

        NioSocketConnector connector = new NioSocketConnector(procssor);
        connector.setConnectTimeoutMillis(30*1000L);
        connector.setHandler(this);
        SslFilter sslFilter = new SslFilter(SSL_CONTEXT);
        sslFilter.setUseClientMode(true);
        sslFilter.setEnabledCipherSuites(ENABLED_SUITES);
        connector.getFilterChain().addLast("sslFilter", sslFilter);
        ConnectFuture cf = connector.connect(server.getRouterAddress());
        cf.awaitUninterruptibly();
        session = cf.getSession();
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
        if (getLog().isTraceEnabled()) {
            if (c.command != Cell.CellType.CELL_RELAY) {
                getLog().trace("writing cell with command " + c.command + " to " + server.getName());
            } else {
                getLog().trace("writing relay cell of type " + ((CellRelay)c).relayCommand + " to " + server.getName());
            }
        }
        session.write(IoBuffer.wrap(c.toByteArray()));
    }
    
    private Logger getLog() {
        return LoggerFactory.getLogger(getClass());
    }

    synchronized private int getFreeCircuitId() {
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
            session.close(true);
        }
    }

    byte[] cellbuffer = new byte[512];
    int cellbufferfilled = 0;

    @Override
    public void messageReceived(IoSession session, Object message) throws IOException {
        IoBuffer iobuffer = (IoBuffer) message;
        if (iobuffer.remaining() % Cell.CELL_TOTAL_SIZE != 0) {
            getLog().warn("Partial block found " + iobuffer.remaining());
        }
        
        // complete any previous partial buffer
        if (cellbufferfilled != 0) {
            getLog().warn("previous incomplete cell found " + cellbufferfilled);
            int fillSize = Math.min(Cell.CELL_TOTAL_SIZE - cellbufferfilled, iobuffer.remaining());
            iobuffer.get(cellbuffer, cellbufferfilled, fillSize);
            cellbufferfilled += fillSize;
            if (cellbufferfilled == Cell.CELL_TOTAL_SIZE) {
                onCell(Cell.read(cellbuffer, circuits));
                cellbufferfilled = 0;
            }
        }
        
        if (iobuffer.position() != 0 && iobuffer.remaining() >= Cell.CELL_TOTAL_SIZE){
            getLog().warn("reading next cell after incomplete cell found " + iobuffer.position());
        }

        // grab any complete cells in the packet
        while ((iobuffer.remaining()) >= Cell.CELL_TOTAL_SIZE) {
            iobuffer.get(cellbuffer);
            onCell(Cell.read(cellbuffer, circuits));
        }
        
        // buffer any leftover bytes for the next incoming message
        if ((iobuffer.remaining()) > 0) {
            getLog().warn("leftover bytes found " + iobuffer.remaining());
            cellbufferfilled = iobuffer.remaining();
            iobuffer.get(cellbuffer, 0, cellbufferfilled);
            assert(0 == iobuffer.remaining());
        }
    }
    

    private void onCell(Cell cell) {
        if (getLog().isTraceEnabled()) {
            if (cell.command != Cell.CellType.CELL_RELAY) {
                getLog().trace("received cell with command " + cell.command + " from " + server.getName());
            } else {
                getLog().trace("received relay cell of type " + ((CellRelay)cell).relayCommand + " from " + server.getName());
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
