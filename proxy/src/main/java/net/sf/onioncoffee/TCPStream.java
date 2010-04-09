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
package net.sf.onioncoffee;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.concurrent.TimeoutException;

import net.sf.onioncoffee.CellRelay.RelayType;
import net.sf.onioncoffee.common.Encoding;
import net.sf.onioncoffee.common.Queue;
import net.sf.onioncoffee.common.TorException;

import org.apache.commons.logging.LogFactory;

/**
 * handles the features of single TCP streams on top of circuits through the tor
 * network. provides functionality to send and receive data by this streams and
 * is publicly visible.
 * 
 * @author Lexi Pimenidis
 * @author Tobias Koelsch
 * @author Michael Koellejan
 * @version unstable
 */

public class TCPStream extends CellSink implements Closeable {

    
    /**
     * 
     * @author Lexi Pimenidis
     */
    class TCPStreamOutputStream extends OutputStream {
        boolean stopped; // as stop() is depreacated we use this toggle variable

        @Override
        public void close() {
            this.stopped = true;
        }

        @Override
        public void write(int b) throws IOException {
            write(Encoding.intToNByteArray(b, 4), 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return;
            }
            for (CellRelay cell : CellRelay.getRelayCells(TCPStream.this, b, off, len)) {
                sendCell(cell);
            }
            
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

    }
    
    private int queue_timeout = Config.queueTimeoutStreamBuildup; // wait x seconds for answer
    
    Circuit circ;

    public int ID;

    public Queue queue; // receives incoming data

    public InetAddress resolvedAddress;

    public TCPStreamProperties sp;

    public boolean established = false;

    public boolean closed = false;

    public int closed_for_reason; // set by CellRelay. descriptive Strings are in
    // CellRelay.reason_to_string

    // TCPStreamThreadTor2Java tor2java;
    // TCPStreamThreadJava2Tor java2tor;
    QueueTor2JavaHandler qhT2J;
    QueueFlowControlHandler qhFC;
    TCPStreamOutputStream outputStream;
    int streamLevelFlowControl = 500;
    static final int streamLevelFlowControlIncrement = 50;

    /**
     * creates a stream on top of a existing circuit. users and programmers
     * should never call this function, but Tor.connect() instead.
     * 
     * @param c
     *            the circuit to build the stream through
     * @param sp
     *            the host etc. to connect to
     * @throws TimeoutException 
     * @see Tor
     * @see Circuit
     * @see TCPStreamProperties
     */
    public TCPStream(Circuit c, TCPStreamProperties sp) throws IOException, TorException{
        this.sp = sp;
        established = false;
//        last_cell = last_action = created = System.currentTimeMillis();
        int setupDuration; // stream establishment duration
        long startSetupTime;

        // attach stream to circuit
        circ = c;
        ID = circ.assignStreamID(this);
        queue = new Queue(queue_timeout);
        closed = false;
        closed_for_reason = 0;
        LogFactory.getLog(getClass()).debug("TCPStream: building new stream " + this);

        startSetupTime = System.currentTimeMillis();
        // send RELAY-BEGIN
        CellRelay cell = new CellRelay(this, RelayType.RELAY_BEGIN);
        {
            // ADDRESS | ':' | PORT | [00]
            byte[] host;
            if (sp.isResolved()) { // insert IP-adress in dotted-quad format, if
                // resolved
                StringBuffer sb = new StringBuffer();
                byte[] a = sp.addr.getAddress();
                for (int i = 0; i < 4; ++i) {
                    if (i > 0) {
                        sb.append('.');
                    }
                    sb.append((a[i]) & 0xff);
                }
                host = sb.toString().getBytes();
            } else {
                // otherwise let exit-point resolv name itself
                host = sp.hostname.getBytes(); 
            }
            cell.appendData(host);
            cell.appendData(new byte[]{':'});
            cell.appendData(new Integer(sp.port).toString().getBytes());
            cell.appendData(new byte[]{0});
        }
        sendCell(cell);

        // wait for RELAY_CONNECTED
        try {
            LogFactory.getLog(getClass()).debug("TCPStream: Waiting for Relay-Connected Cell...");
            queue.receiveRelayCell(RelayType.RELAY_CONNECTED);
            LogFactory.getLog(getClass()).debug("TCPStream: Got Relay-Connected Cell");
        } catch (TorException e) {
            if (!closed) {
                LogFactory.getLog(getClass()).warn("TCPStream: Closed:" + this + " due to TorException:" + e.getMessage());
            }
            closed = true;
            // MRK: when the circuit does not work at this point: close it
            // Lexi: please do it soft! there might be other streams
            // working on this circuit...
            // c.close(false);
            // Lexi: even better: increase only a counter for this circuit
            // otherwise circuits will close on an average after 3 or 4
            // streams. this is nothing we'd like to happen
            c.reportStreamFailure(this);
            throw e;
        } catch (IOException e) {
            closed = true;
            LogFactory.getLog(getClass()).warn("TCPStream: Closed:" + this + " due to IOException:" + e.getMessage());
            throw e;
        }

        setupDuration = (int) (System.currentTimeMillis() - startSetupTime);

        // store resolved IP in TCPStreamProperties
        byte[] ip = cell.extractData();
        try {
            sp.addr = InetAddress.getByAddress(ip);
            sp.setResolved(true);
            resolvedAddress = sp.addr;
            LogFactory.getLog(getClass()).trace("TCPStream: storing resolved IP " + sp.addr.toString());
        } catch (IOException e) {
        }
        // create reading threads to relay between user-side and tor-side
        // tor2java = new TCPStreamThreadTor2Java(this);
        // java2tor = new TCPStreamThreadJava2Tor(this);
        qhFC = new QueueFlowControlHandler(this, streamLevelFlowControl, streamLevelFlowControlIncrement);
        this.queue.addHandler(qhFC);
        qhT2J = new QueueTor2JavaHandler(this);
        this.queue.addHandler(qhT2J);
        outputStream = new TCPStreamOutputStream();

        LogFactory.getLog(getClass()).info("TCPStream: build stream " + this + " within " + setupDuration + " ms");
        // attach stream to history
        circ.registerStream(sp, setupDuration);
        established = true;
        // Tor.lastSuccessfulConnection = new Date(System.currentTimeMillis());
//        el.fireEvent(new TorEvent(TorEvent.STREAM_BUILD, this, "Stream build: " + print()));
    }

    /** called from derived ResolveStream */
    TCPStream(Circuit c) {
        circ = c;
    }

    public void doSendCell(Cell c) throws IOException {
        try {
            circ.sendCell(c);
        } catch (IOException e) {
            // if there's an error in sending a cell, close this stream
            circ.reportStreamFailure(this);
            throw e;
        }
    }


    /** send a stream-layer dummy */
    public void sendKeepAlive() {
        try {
            sendCell(new CellRelay(this, RelayType.RELAY_DROP));
        } catch (IOException e) {
        }
    }

    /** for application interaction */
    public void close() {
        // gracefully close stream
        close(false);
        // remove from circuit
        LogFactory.getLog(getClass()).trace("TCPStream.close(): removing stream " + this);
        circ.streams.remove(new Integer(ID));
    }

    /**
     * for internal usage
     * 
     * @param force
     *            if set to true, just destroy the object, without sending
     *            END-CELLs and stuff
     */
    public void close(boolean force) {
        LogFactory.getLog(getClass()).debug("TCPStream.close(): closing stream " + this);
        // if stream is not closed, send a RELAY-END-CELL
        if (!(closed || force)) {
            try {
                CellRelay cell = new CellRelay(this, RelayType.RELAY_END);
                cell.appendData(new byte[]{6});
                sendCell(cell); // send cell with 'DONE'
            } catch (IOException e) {
            }
        }
        // terminate threads gracefully
        closed = true;
        /*
         * if (!force) { try { this.wait(3); } catch (Exception e) { } }
         */
        // terminate threads if they are still alive
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (Exception e) {
            }
        }
        // close queue (also removes handlers)
        queue.close();
        // remove from circuit
        circ.streams.remove(new Integer(ID));
    }

    /**
     * use this to receive data by the anonymous data stream
     * 
     * @return a standard Java-Inputstream
     */
    public InputStream getInputStream() {
        return qhT2J.sin;
    }

    /**
     * use this to transmit data through the Tor-network
     * 
     * @return a standard Java-Outputstream
     */
    public OutputStream getOutputStream() {
        return outputStream;
    }

    /** used for proxy and UI */
    public String getRoute() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < circ.route_established; ++i) {
            Server s = circ.route[i].server;
            sb.append(", ");
            sb.append(s.getName() + " (" + s.countryCode + ")");
        }
        return sb.toString();
    }

    /** for debugging */
    public String toString() {
        if (sp == null) {
            return ID + " on circuit " + circ + " to nowhere";
        } else {
            if (closed) {
                return ID + " on circuit " + circ + " to " + sp.hostname + ":" + sp.port + " (closed)";
            } else {
                return ID + " on circuit " + circ + " to " + sp.hostname + ":" + sp.port;
            }
        }
    }
    
}

// vim: et
