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

import java.io.IOException;
import java.util.Arrays;

import net.sf.onioncoffee.common.Encoding;

import org.apache.commons.logging.LogFactory;

/**
 * the general form of a RELAY cell in the Tor Protocol. This class also calls
 * the crypto- functions in Node.java to decode an onion, if encrypted data is
 * received.
 * 
 * @author Brad Davis
 * @author Lexi Pimenidis
 * @version unstable
 */

public class CellRelay extends Cell {
    public RelayType relayCommand;
    private int streamID = 0; 
    public int dataLength = 0; 
    public byte[] data = new byte[498];
    private byte[] digest = new byte[4];

    /**
     * set to a value from 0 to outCircuit.route_established-1 to address a
     * special router in the chain, default is the last one
     */
    private int addressedRouterInCircuit = -1;


    TCPStream s;

    public enum RelayType {
        RELAY_BEGIN((byte)1, "begin"),
        RELAY_DATA((byte)2, "data"),
        RELAY_END((byte)3, "end"),
        RELAY_CONNECTED((byte)4, "connected"),
        RELAY_SENDME((byte)5, "sendme"),
        RELAY_EXTEND((byte)6, "extend"),
        RELAY_EXTENDED((byte)7, "extended"),
        RELAY_TRUNCATE((byte)8, "truncate"),
        RELAY_TRUNCATED((byte)9, "truncated"),
        RELAY_DROP((byte)10, "drop"),
        RELAY_RESOLVE((byte)11, "resolve"),
        RELAY_RESOLVED((byte)12, "resolved"),
        RELAY_BEGIN_DIR((byte)13, "begin dir"),
        RELAY_ESTABLISH_INTRO((byte)32, null),
        RELAY_ESTABLISH_RENDEZVOUS((byte)33, null),
        RELAY_INTRODUCE1((byte)34, null),
        RELAY_INTRODUCE2((byte)35, null),
        RELAY_RENDEVOUS1((byte)36, null),
        RELAY_RENDEVOUS2((byte)37, null),
        RELAY_INTRO_ESTABLISHED((byte)38, null),
        RELAY_RENDEVOUS_ESTABLISHED((byte)39, null),
        RELAY_INTRODUCE_ACK((byte)40, null); 

        byte value;
        String name;

        RelayType(byte value, String name) {
            this.value = value; 
            this.name = name;
        }
        
        static RelayType fromByte(byte val) {
            RelayType retVal = null;
            for (RelayType type : RelayType.values()) {
                if (val == type.value) {
                    retVal = type;
                    break;
                }
            }
            if (null == retVal) {
                throw new RuntimeException("unknown relay command");
            }
            return retVal;
        }
    }

    
    static final int RELAY_COMMAND_SIZE = 1;

    static final int RELAY_RECOGNIZED_SIZE = 2;

    static final int RELAY_STREAMID_SIZE = 2;

    static final int RELAY_DIGEST_SIZE = 4;

    static final int RELAY_LENGTH_SIZE = 2;

    static final int RELAY_DATA_SIZE = 498;
    static final int RELAY_TOTAL_SIZE = CELL_PAYLOAD_SIZE;
    static final int RELAY_COMMAND_POS = 0;
    static final int RELAY_RECOGNIZED_POS = RELAY_COMMAND_POS + RELAY_COMMAND_SIZE;
    static final int RELAY_STREAMID_POS = RELAY_RECOGNIZED_POS + RELAY_RECOGNIZED_SIZE;
    static final int RELAY_DIGEST_POS = RELAY_STREAMID_POS + RELAY_STREAMID_SIZE;
    static final int RELAY_LENGTH_POS = RELAY_DIGEST_POS + RELAY_DIGEST_SIZE;
    static final int RELAY_DATA_POS = RELAY_LENGTH_POS + RELAY_LENGTH_SIZE;

    protected static CellRelay[] getRelayCells(TCPStream c, byte[] buffer) {
        return getRelayCells(c, buffer, 0, buffer.length);
    }
    
    protected static CellRelay[] getRelayCells(TCPStream  c, byte[] buffer, int length) {
        return getRelayCells(c, buffer, 0, length);
    }

    public static CellRelay[] getRelayCells(TCPStream c, byte[] buffer, int offset, int length) {
        int count = length / RELAY_DATA_SIZE;
        if (0 != length % RELAY_DATA_SIZE) {
            ++count;
        }
        CellRelay[] retVal = new CellRelay[count];
        int cellIndex =0;
        while (length != 0) {
            int curCellSize = Math.min(length, RELAY_DATA_SIZE);
            CellRelay cell = retVal[cellIndex] = new CellRelay(c, RelayType.RELAY_DATA);
            cell.appendData(buffer, offset, curCellSize);
            ++cellIndex;
            length -= curCellSize;
            offset += curCellSize;
        }
        return retVal;
    }


    protected CellRelay(Circuit c, RelayType relay_command) {
        super(c, CellType.CELL_RELAY);
        this.relayCommand = relay_command;
    }


    public CellRelay(TCPStream s, RelayType relay_command) {
        this(s.circ, relay_command);
        this.s = s;
        this.setStreamID(s.ID);
    }

    public CellRelay(Circuit circuit, int circuitId, byte[] payload) throws IOException {
        super(circuit, circuitId, CellType.CELL_RELAY, payload);
        getLog().trace("init_from_data() for " + getCircuit().route_established + " layers");
        setStreamID(Encoding.byteArrayToInt(payload, RELAY_STREAMID_POS, RELAY_STREAMID_SIZE));
        dataLength = Encoding.byteArrayToInt(payload, RELAY_LENGTH_POS, RELAY_LENGTH_SIZE);
        System.arraycopy(payload, RELAY_DATA_POS, this.data, 0, RELAY_DATA_SIZE);
        relayCommand = RelayType.fromByte(payload[RELAY_COMMAND_POS]);
    }
    
    public CellRelay(Circuit circuit, byte[] payload) throws IOException {
        this(circuit, circuit.getId(), payload);
    }


    /**
     * set to a value from 0 to outCircuit.route_established-1 to address a
     * special router in the chain, default is the last one
     */
    boolean setAddressedRouter(int router) {
        if ((router > -1) && (router < getCircuit().route_established)) {
            addressedRouterInCircuit = router;
            return true;
        }
        return false;
    }

    /**
     * prepares the meta-data, such that the cell can be transmitted. encrypts
     * an onion.
     * 
     * FIXME not idempotent
     * 
     * @return the data ready for sending
     */
    @Override
    public
    byte[] toByteArray() {
        getLog().trace("toByteArray() for " + getCircuit().route_established + " layers");
        // put everything in payload
        payload[RELAY_COMMAND_POS] = relayCommand.value;
        System.arraycopy(Encoding.intToNByteArray(getStreamID(), RELAY_STREAMID_SIZE), 0, payload, RELAY_STREAMID_POS, RELAY_STREAMID_SIZE);
        System.arraycopy(Encoding.intToNByteArray(dataLength, RELAY_LENGTH_SIZE), 0, payload, RELAY_LENGTH_POS, RELAY_LENGTH_SIZE);
        System.arraycopy(data, 0, payload, RELAY_DATA_POS, RELAY_DATA_SIZE);
        // calc digest and insert it
        int i0 = addressedRouterInCircuit >= 0 ? addressedRouterInCircuit : getCircuit().route_established - 1;
        digest = getCircuit().route[i0].calcForwardDigest(payload);
        System.arraycopy(digest, 0, payload, RELAY_DIGEST_POS, RELAY_DIGEST_SIZE);
        // encrypt backwards, take keys from route
        for (int i = i0; i >= 0; --i) {
            getCircuit().route[i].encrypt(payload);
        }
        // create the byte array to be send over TLS
        return super.toByteArray();
    }


    
    public void appendData(byte [] data) {
        appendData(data, data.length);
    }


    public void appendData(byte[] data, int length) {
        appendData(data, 0, length);
    }

    public void appendData(byte[] data, int offset, int length) {
        System.arraycopy(data, offset, this.data, this.dataLength, length);
        this.dataLength += length;
    }

    
    public byte[] extractData(int offset, int length) {
        byte[] retVal = new byte[length];
        System.arraycopy(data, offset, retVal, 0, length);
        return retVal;
    }

    public byte[] extractData(int offset) {
        return extractData(offset, this.dataLength - offset);
    }

    public byte[] extractData() {
        return extractData(0);
    }


    void setStreamID(int streamID) {
        this.streamID = streamID;
    }


    int getStreamID() {
        return streamID;
    }
    
    private static byte[] ZERO_DIGEST = { 0, 0, 0, 0 };
    
    public static void decryptPayload(byte[] payload, Circuit circuit) throws IOException {
        // decrypt forwards, take keys from route
        int encrypting_router;
        boolean digest_verified = false;
        byte[] digest = new byte[RELAY_DIGEST_SIZE];

        if (circuit.route_established == 0) {
            LogFactory.getLog(CellRelay.class).warn("init_from_data() for zero layers on " + circuit);
        }
        
        for (encrypting_router = 0; encrypting_router <= circuit.route_established; ++encrypting_router) {
            // check if no decryption has lead to a recognized cell
            if (encrypting_router == circuit.route_established) {
                throw new IOException("relay cell not recognized, possibly due to decryption errors?");
            }
            CircuitNode node = circuit.route[encrypting_router];
            // decrypt payload
            node.decrypt(payload);
            // if recognized and digest is correct, then stop decrypting
            if ((payload[RELAY_RECOGNIZED_POS] == 0) && (payload[RELAY_RECOGNIZED_POS + 1] == 0)) {
                // save digest
                System.arraycopy(payload, RELAY_DIGEST_POS, digest, 0, RELAY_DIGEST_SIZE);
                System.arraycopy(ZERO_DIGEST, 0, payload, RELAY_DIGEST_POS, 4);
                byte[] digest_calc = node.calcBackwardDigest(payload); 
                // restore digest
                System.arraycopy(digest, 0, payload, RELAY_DIGEST_POS, RELAY_DIGEST_SIZE);
                // check digest
                if (Arrays.equals(Arrays.copyOf(digest, 4), Arrays.copyOf(digest_calc, 4))) {
                    LogFactory.getLog(CellRelay.class).trace("init_from_data(): backward digest from " + node.server + " is OK");
                    digest_verified = true;
                    break;
                }
            }
        }
        // check if digest verified
        if (!digest_verified) {
            LogFactory.getLog(CellRelay.class).warn("init_from_data(): Received " + Encoding.toHexString(digest) + " as backward digest but couldn't verify");
            throw new IOException("wrong digest");
        }
    }

}
