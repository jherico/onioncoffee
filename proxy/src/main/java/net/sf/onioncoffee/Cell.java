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
import java.io.InputStream;
import java.util.Map;

import net.sf.onioncoffee.common.Encoding;

import org.saintandreas.util.Loggable;

/**
 * most general form of the cells in the Tor protocol. Should not be used on its
 * own.
 * 
 * @author Brad Davis
 * @author Lexi Pimenidis
 * @author Vinh Pham
 * @version unstable
 */

public class Cell extends Loggable {
    public enum CellType {
        CELL_PADDING((byte)0, "padding"), 
        CELL_CREATE((byte)1, "create"), 
        CELL_CREATED((byte)2, "created"), 
        CELL_RELAY((byte)3, "relay"),
        CELL_DESTROY((byte)4, "destroy"), 
        CELL_CREATE_FAST((byte)5, "create-fast"), 
        CELL_CREATED_FAST((byte)6, "created-fast");

        public final byte value;
        public final String name;
        
        CellType(byte value, String name) {
            this.value = value; 
            this.name = name;
        }

        
        static CellType parseCommand(int command) {
            return fromByte((Encoding.intToNByteArray(command, 1))[0]);
        }

        static CellType fromByte(byte val) {
            CellType retVal = null;
            for (CellType type : CellType.values()) {
                if (val == type.value) {
                    retVal = type;
                    break;
                }
            }
            return retVal;
        }
    }

    static final int CELL_TOTAL_SIZE = 512;
    static final int CELL_CIRCID_SIZE = 2;
    static final int CELL_COMMAND_SIZE = 1;
    static final int CELL_PAYLOAD_SIZE = 509;
    static final int CELL_CIRCID_POS = 0;
    static final int CELL_COMMAND_POS = CELL_CIRCID_POS + CELL_CIRCID_SIZE;
    static final int CELL_PAYLOAD_POS = CELL_COMMAND_POS + CELL_COMMAND_SIZE;

    /* Circuit for sending data or circuit that needs to be created */
    private Circuit outCircuit;
    public CellType command;
    public final byte[] payload =  new byte[Cell.CELL_PAYLOAD_SIZE];
    public final int circuitId;


    Cell(Circuit outCircuit, int circuitId, CellType command) {
        this.command = command;
        this.outCircuit = outCircuit;
        this.circuitId = circuitId;
    }

    Cell(Circuit outCircuit, CellType command) {
        this(outCircuit, outCircuit.getId(), command);
    }

    Cell(Circuit outCircuit, int circuitId, CellType command, byte[]payload) {
        this(outCircuit, circuitId, command);
        applyPayload(payload);
    }

    Cell(Circuit outCircuit, CellType command, byte[]payload) {
        this(outCircuit, outCircuit.getId(), command, payload);
    }

    /** is this a padding cell? */
    public boolean isTypePadding() {
        return this.command == CellType.CELL_PADDING;
    }

    /** is this a relay cell? */
    public boolean isTypeRelay() {
        return this.command == CellType.CELL_RELAY;
    }
    
    /**
     * concatenate all data to a single byte-array. This function is used to finally
     * transmit the cell over a line.
     */
    public byte[] toByteArray() {
        byte[] buff = new byte[Cell.CELL_TOTAL_SIZE];
        getLog().trace("Cell.toByteArray()");
        System.arraycopy(Encoding.intToNByteArray(getCircuit().getId(), Cell.CELL_CIRCID_SIZE), 0, buff, Cell.CELL_CIRCID_POS, Cell.CELL_CIRCID_SIZE);
        buff[Cell.CELL_COMMAND_POS] = this.command.value;
        System.arraycopy(this.payload, 0, buff, CELL_PAYLOAD_POS, this.payload.length);
        return buff;
    }

    public static byte[] readNBytes(InputStream in, int bytes) throws IOException {
        byte[] retVal = new byte[bytes];
        int filled = 0;
        while (filled < bytes) {
            int n = in.read(retVal, filled, bytes - filled);
            if (n < 0) {
                throw new IOException("readNBytes reached EOF");
            }
            filled += n;
        }
        return retVal;
    }
    
    /**
     * initialize cell from stream Attention: this.outCircuit is not set!
     * 
     * @param in
     *            the input stream from a TLS-line to read the data from
     */
    public static Cell read(InputStream in, Map<Integer, Circuit> circuitMap) throws IOException {
        return read(readNBytes(in, CELL_TOTAL_SIZE), circuitMap);
    }
    
    public static Cell read(byte[] data, Map<Integer, Circuit> circuitMap) throws IOException {
        return read(data, 0, circuitMap);
    }
    
    public static Cell read(byte[] data, int offset, Map<Integer, Circuit> circuitMap) throws IOException {
        Cell retVal = null;
        CellType command = CellType.fromByte(data[Cell.CELL_COMMAND_POS  + offset]);
        int circuitId = Encoding.byteArrayToInt(data, Cell.CELL_CIRCID_POS +  offset, Cell.CELL_CIRCID_SIZE);
        Circuit circuit = circuitMap.get(circuitId);

        byte[] payload = new byte[Cell.CELL_PAYLOAD_SIZE]; 
        System.arraycopy(data, Cell.CELL_PAYLOAD_POS + offset, payload, 0, Cell.CELL_PAYLOAD_SIZE);
        if (command == CellType.CELL_RELAY) {
            try {
                CellRelay.decryptPayload(payload, circuit);
                retVal = new CellRelay(circuit, circuitId, payload);
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            }
        } else {
            retVal = new Cell(circuit, circuitId, command, payload);
        }
        return retVal;
    }

    public void setCircuit(Circuit outCircuit) {
        this.outCircuit = outCircuit;
    }

    public Circuit getCircuit() {
        return outCircuit;
    }

    public void applyPayload(byte[] newPayload) {
        applyPayload(newPayload, newPayload.length);
    }
    
    public void applyPayload(byte[] newPayload, int length) {
        System.arraycopy(newPayload, 0, payload, 0, Math.min(payload.length, length));
    }
}
