/**
 * OnionCoffee - Anonymous Communication through TOR Network Copyright (C)
 * 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package net.sf.onioncoffee;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import net.sf.onioncoffee.Cell.CellType;
import net.sf.onioncoffee.CellRelay.RelayType;
import net.sf.onioncoffee.common.Encoding;
import net.sf.onioncoffee.common.Encryption;
import net.sf.onioncoffee.common.Queue;
import net.sf.onioncoffee.common.TorException;

import org.slf4j.Logger;

/**
 * handles the functionality of creating circuits, given a certain route and
 * buidling tcp-streams on top of them.
 * 
 * @author Lexi Pimenidis
 * @author Tobias Koelsch
 * @author Andriy Panchenko
 * @author Michael Koellejan
 * @version unstable
 */

public class Circuit extends CellSink {
    private static final int CIRCUT_LEVEL_FLOW_CONTROL = 1000;
    private static final int CIRCUT_LEVEL_FLOW_CONTROL_INCREMENT = 100;
    // a pointer to the TLS-layer
    private final Server entryServer;
    private final ServerConnection connection;
    //    private final long created;
    private final int setupDuration; // time in ms it took to establish the circuit
    private int sum_streams_setup_delays = 0; // duration of all streams' setup times

    // stores the route
    public CircuitNode[] route;
    // nodes in the route, where the keys have been established
    public int route_established = 0;
    // used to receive incoming data
    Queue queue = new Queue(Config.queueTimeoutCircuit);
    // a list of all tcp-streams relayed through this circuit
    public Map<Integer, TCPStream> streams = new HashMap<Integer, TCPStream>();
    // contains URLs, InetAddresse or z-part of HS URL of hosts used to make contact to (or for DNS query) with this Circuit
    final Set<Object> streamHistory = new HashSet<Object>();
    // counts the number of established streams
    public int established_streams = 0;
    private int id;

    public boolean established = false; // set to true, if route is established
    public boolean closed = false; // set to true, if no new streams are allowed
    public boolean destruct = false; // set to true, if circuit is closed and inactive and may be removed from all sets
    public int ranking = -1; // ranking index of the circuit
    public int stream_counter = 0; // overall number of streams relayed thrue the circ
    public int stream_fails = 0; // overall counter of failures in streams in this circuit
    QueueFlowControlHandler qhFC;

    /**
     * initiates a circuit. tries to rebuild the circuit for a limited number of
     * times, if first attempt fails.
     * 
     * @param dir
     *            a pointer to the directory, in case an alternative route is
     *            necessary
     * @param sp
     *            some properties for the stream that is the reason for building
     *            the circuit (needed if the circuit is needed to ask the
     *            directory for a new route)
     * 
     * @exception TorException
     * @exception IOException
     * @throws TimeoutException
     */
    public Circuit(ServerConnection connection, Server[] init, TCPStreamProperties sp) throws IOException, TorException, InterruptedException, TimeoutException {
        if (init == null || init.length == 0) {
            throw new IllegalArgumentException("cannot build null or 0 dataLength route");
        }
        this.connection = connection;
        this.entryServer = init[0];
        // FIXME: Addition to circuits-list is quite hidden here.
        connection.assignCircuitId(this);
        route = new CircuitNode[init.length];
        // stepwise route creation
        for (int i = 0; i < init.length; ++i) {
            Server s = init[i];
            getLog().debug("Circuit: " + this + " extending to " + s + " (" + s.countryCode + ")");
            extend(i, s);
            route_established += 1;
        }
        setupDuration = (int) (System.currentTimeMillis() - getCreated());
        established = true;
        getLog().info("Circuit: " + this + " established within " + setupDuration + " ms");
        qhFC = new QueueFlowControlHandler(this, CIRCUT_LEVEL_FLOW_CONTROL, CIRCUT_LEVEL_FLOW_CONTROL_INCREMENT);
        queue.addHandler(qhFC);
    }


    /**
     * Extends the existing circuit one more hop. sends an EXTEND-cell.
     * 
     * @throws TimeoutException
     */
    private void extend(int i, Server server) throws IOException, TorException, TimeoutException {
        final byte[] symmetricKey = new byte[16];
        new SecureRandom().nextBytes(symmetricKey);

        // Diffie-Hellman: generate our secret
        // Diffie-Hellman: generate g^x
        BigInteger dh_private = new BigInteger(CircuitNode.DH_P.bitLength() - 1, new SecureRandom());
        BigInteger dh_x = CircuitNode.DH_G.modPow(dh_private, CircuitNode.DH_P);
        byte[] dh_x_bytes = CircuitNode.BigIntegerTo128Bytes(dh_x);

        // create DH-exchange:
        byte[] onion_raw = new byte[144];
        // OAEP padding [42 bytes] (RSA-encrypted) (gets added automatically)
        // Symmetric key [16 bytes] FIXME: we assume that we ALWAYS need this?
        System.arraycopy(symmetricKey, 0, onion_raw, 0, 16);
        // First part of g^x [70 bytes]
        // Second part of g^x [58 bytes] (Symmetrically encrypted)
        System.arraycopy(dh_x_bytes, 0, onion_raw, 16, 128);
        // encrypt and store result in payload
        byte[] onion_skin = Circuit.asymmetricEncrypt(onion_raw, symmetricKey, server.onionKey);

        byte[] dh_response;
        if (i == 0) {
            // send create cell, set circID
            sendCell(new Cell(this, CellType.CELL_CREATE, onion_skin));
            // wait for answer
            Cell created = queue.receiveCell(CellType.CELL_CREATED);
            dh_response = created.payload;
        } else {
            // send extend cell
            CellRelay cell = new CellRelay(this, RelayType.RELAY_EXTEND);
            cell.appendData(server.getAddress().getAddress());
            cell.appendData(Encoding.intToNByteArray(server.getRouterPort(), 2));
            cell.appendData(onion_skin);
            cell.appendData(server.getFingerprintBytes());
            sendCell(cell);
            // wait for extended-cell
            CellRelay relay = queue.receiveRelayCell(RelayType.RELAY_EXTENDED);
            dh_response = relay.data;
        }
        // finish DH-exchange
        route[i] = new CircuitNode(server, dh_response, dh_private);
    }

    /** creates and send a padding-cell down the circuit */
    public void sendKeepAlive() {
        try {
            sendCell(new Cell(this, CellType.CELL_PADDING));
        } catch (IOException e) {
        }
    }


    /**
     * used to report that this stream cause some trouble (either by itself, or
     * the remote server, or what ever)
     */
    void reportStreamFailure(TCPStream stream) {
        ++stream_fails;
        // if it's just too much, 'soft'-close this circuit
        if ((stream_fails > Config.circuitClosesOnFailures) && (stream_fails > stream_counter * 3 / 2)) {
            if (!closed) {
                getLog().info("Circuit.reportStreamFailure: closing due to failures " + this);
            }
            close(false);
        }
        // include in ranking
        updateRanking();
    }

    /**
     * find a free stream id, other than zero
     */
    private synchronized int getFreeStreamID() {
        for (int nr = 1; nr < 0x10000; ++nr) {
            int id = (nr + stream_counter) & 0xffff;
            if (id != 0) {
                if (!streams.containsKey(new Integer(id))) {
                    return id;
                }
            }
        }
        throw new IllegalStateException("Circuit.getFreeStreamID: " + this + " has no free stream-IDs");
    }

    /**
     * find a free stream-id.
     * 
     * @throws TorException
     */
    int assignStreamID(TCPStream s) {
        if (closed) {
            throw new IllegalStateException("Circuit.assign_streamID: " + this + "is closed");
        }
        // assign stream id and memorize stream
        s.ID = getFreeStreamID();
        streams.put(new Integer(s.ID), s);
        return s.ID;
    }

    /**
     * registers a stream in the history to allow bundeling streams to the same
     * connection in one circuit
     */
    void registerStream(TCPStreamProperties sp) throws TorException {
        ++established_streams;
        if (sp.addr != null) {
            streamHistory.add(sp.addr);
        }
        if (sp.hostname != null) {
            streamHistory.add(sp.hostname);
        }
    }

    /**
     * registers a stream in the history to allow bundeling streams to the same
     * connection in one circuit, wrapped for setting stream creation time
     */
    void registerStream(TCPStreamProperties sp, long streamSetupDuration) throws TorException {

        sum_streams_setup_delays += streamSetupDuration;
        stream_counter++;
        updateRanking();
        registerStream(sp);
    }

    /**
     * updates the ranking of the circuit. takes into account: setup time of
     * circuit and streams. but also number of stream-failures on this circuit;
     * 
     */
    private void updateRanking() {
        // do a weighted average of all setups. weighten the setup-time of the
        // circuit more
        // then those of the single streams. thus streams will be rather
        // unimportant at the
        // beginning, but play a more important role afterwards.
        ranking = (Config.CIRCUIT_ESTABLISHMENT_TIME_IMPACT * setupDuration + sum_streams_setup_delays) / (stream_counter + Config.CIRCUIT_ESTABLISHMENT_TIME_IMPACT);
        // take into account number of stream-failures on this circuit
        // DEPRECATED: just scale this up linearly
        // ranking *= 1 + stream_fails;
        // NEW: be cruel! there should be something severe for 3 or 4 errors!
        ranking *= Math.exp(stream_fails);
    }

    /**
     * closes the circuit. either soft (remaining connections are kept, no new
     * one allowed) or hard (everything is closed immediatly, e.g. if a destroy
     * cell is received)
     */
    public boolean close(boolean force) {
        if (!closed) {
            getLog().info("Circuit.close(): closing " + this);
            // remove servers from list of currently used nodes
            for (int i = 0; i < route_established; ++i) {
                route[i].server.decrementCircuitCount();
            }
        }

        // mark circuit closed. do nothing more, is soft close and streams are left
        closed = true;
        established = false;
        // close all streams, removed closed streams
        Iterator<Integer> si = streams.keySet().iterator();
        while (si.hasNext()) {
            try {
                Object nick = si.next();
                TCPStream stream = streams.get(nick);
                // check if stream is still alive
                if (!stream.closed) {
                    if (force) {
                        stream.close(force);
                    } else {
                        // check if we can time-out the stream?
                        if (stream.getCellIdleTime() > 10 * Config.queueTimeoutStreamBuildup * 1000) {
                            // ok, fsck it!
                            getLog().info("Circuit.close(): forcing timeout on stream");
                            stream.close(true);
                        } else {
                            // no way...
                            getLog().debug("Circuit.close(): can't close due to " + stream);
                        }
                    }
                }
                if (stream.closed) {
                    si.remove();
                }
            } catch (Exception e) {
            }
        }
        if ((!force) && (!streams.isEmpty())) {
            return false;
        }
        // gracefully kill circuit with DESTROY-cell or so
        if (!force) {
            if (route_established > 0) {
                // send a destroy-cell to the first hop in the circuit only
                getLog().debug("Circuit.close(): destroying " + this);
                route_established = 1;
                try {
                    sendCell(new Cell(this, CellType.CELL_DESTROY));
                } catch (IOException e) {
                }
            }
        }
        // close circuit (also removes handlers)
        queue.close();
        // tls.circuits.remove(new Integer(id));
        destruct = true;
        // closed
        return true;
    }

    /**
     * returns the route of the circuit. used to display route on a map or the
     * like
     */
    public Server[] getRoute() {
        Server[] s = new Server[route_established];
        for (int i = 0; i < route_established; ++i) {
            s[i] = route[i].server;
        }
        return s;
    }

    /** used for description */
    public String toString() {
        Logger log = getLog();

        if (entryServer != null) {
            StringBuilder sb = new StringBuilder(getId());
            if (log.isDebugEnabled()) {
                sb.append(" [" + entryServer.getName() + " (" + entryServer.countryCode + ")");
                for (int i = 1; i < route_established; ++i) {
                    sb.append(" " + route[i].server.getName() + " (" + route[i].server.countryCode + ")");
                }
                sb.append("]");
            }
            if (log.isInfoEnabled()) {
                if (closed) {
                    sb.append(" (closed)");
                } else if (!established) {
                    sb.append(" (establishing)");
                }
            }
            return sb.toString();
        } else {
            return "<empty>";
        }
    }

    /**
     * Check whether the given route is compatible to the given restrictions
     * 
     * @param route
     *            a list of servers that form the route
     * @param sp
     *            the requirements to the route
     * @param forHiddenService
     *            set to TRUE to disregard exitPolicies
     * @return the boolean result
     */
    static boolean isRouteCompatible(CircuitNode[] route, TCPStreamProperties sp) {
        // check for null values
        if (route == null) {
            throw new IllegalArgumentException("received NULL-route");
        }
        if (sp == null) {
            throw new IllegalArgumentException("received NULL-sp");
        }
        if (route[route.length - 1] == null) {
            throw new IllegalArgumentException("route contains NULL at position " + (route.length - 1));
        }
        // empty route is always wrong
        if (route.length < 1) {
            return false;
        }
        // route is too short
        if (route.length < sp.getMinRouteLength()) {
            return false;
        }
        // route is too long
        if (route.length > sp.getMaxRouteLength()) {
            return false;
        }

        // check compliance with sp.route
        Server[] proposed_route = sp.getProposedRoute();
        if (proposed_route != null) {
            int len = Math.min(proposed_route.length, route.length);
            for (int i = 0; i < len; ++i) {
                if (!route[i].equals(proposed_route[i])) {
                    return false;
                }
            }
        }

        return !sp.exitPolicyRequired || route[route.length - 1].server.exitPolicyAccepts(sp.addr, sp.port); 
    }

    boolean isCompatible(TCPStreamProperties sp) throws TorException {
        return isRouteCompatible(route, sp);
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    protected void doSendCell(Cell c) throws IOException {
        connection.sendCell(c);
    }


    /**
     * encrypt data with asymmetric key. create asymmetricla encrypted data:<br>
     * <ul>
     * <li>OAEP padding [42 bytes] (RSA-encrypted)
     * <li>Symmetric key [16 bytes] FIXME: we assume that we ALWAYS need this
     * <li>First part of data [70 bytes]
     * <li>Second part of data [x-70 bytes] (Symmetrically encrypted)
     * <ul>
     * encrypt and store in result
     * 
     * @param data
     *            to be encrypted, needs currently to be at least 70 bytes long
     * @return the first half of the key exchange, ready to be send to the other
     *         partner
     */
    static byte[] asymmetricEncrypt(byte[] data, byte[] symmetric_key_for_create, PublicKey onionKey) throws TorException {
        try {
            return Encryption.asymmetricEncrypt(data, symmetric_key_for_create, onionKey);
        } catch (GeneralSecurityException e) {
            throw new TorException("InvalidCipherTextException:" + e.getMessage(), e);
        }
    }

    public void onCell(Cell cell) {
        // dispatch according to circID
        if (qhFC != null) {
            qhFC.handleCell(cell);
        }

        switch (cell.command) {
            case CELL_DESTROY:
                close(true);
                break;

            case CELL_RELAY:
                if (((CellRelay) cell).getStreamID() != 0) {
                    CellRelay relay = ((CellRelay) cell);
                    if (streams.containsKey(new Integer(relay.getStreamID()))) {
                        getLog().trace("TLSDispatcher.run: data from " + this.entryServer.getName() + " dispatched to circuit " + getId() + "/stream " + relay.getStreamID());
                        TCPStream stream = streams.get(new Integer(relay.getStreamID()));
                        stream.queue.add(relay);
                    }
                    break;
                } 
                // deliberate fallthrough

            default:
                queue.add(cell);
                break;
        }
       
    }

}
