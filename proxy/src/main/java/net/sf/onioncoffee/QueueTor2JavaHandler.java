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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import net.sf.onioncoffee.CellRelay.RelayType;
import net.sf.onioncoffee.common.QueueHandler;
import net.sf.onioncoffee.common.TorException;

import org.apache.commons.logging.LogFactory;

/**
 * used to be TCPStreamThreadTor2Java
 */
public class QueueTor2JavaHandler implements QueueHandler {
    TCPStream stream;
    PipedInputStream sin; // read from tor and output to this stream
    PipedOutputStream fromtor; // private end of this pipe
    boolean stopped; // as stop() is deprecated we use this toggle variable

    QueueTor2JavaHandler(TCPStream stream) {
        this.stream = stream;
        try {
            sin = new SafePipedInputStream();
            fromtor = new PipedOutputStream(sin);
        } catch (IOException e) {
            LogFactory.getLog(getClass()).error("QueueTor2JavaHandler: caught IOException " + e.getMessage());
        }
    }

    public void close() {
        this.stopped = true;
        /* leave data around, until no more referenced by someone else */
        // try{ sin.close(); } catch(Exception e) {}
        try {
            fromtor.close();
        } catch (Exception e) {
        }
    }

    /** return TRUE, if cell was handled */
    public boolean handleCell(Cell cell) throws TorException {
        if (!stream.closed && !this.stopped && cell != null && cell.isTypeRelay()) {
            CellRelay relay = (CellRelay) cell;
            if (relay.relayCommand == RelayType.RELAY_DATA) {
                LogFactory.getLog(getClass()).trace("QueueTor2JavaHandler.handleCell(): stream " + stream.ID + " received data");
                try {
                    fromtor.write(relay.data, 0, relay.dataLength);
                } catch (IOException e) {
                    LogFactory.getLog(getClass()).error("QueueTor2JavaHandler.handleCell(): caught IOException " + e.getMessage());
                }
                return true;
            } else if (relay.relayCommand == RelayType.RELAY_END) {
                LogFactory.getLog(getClass()).trace("QueueTor2JavaHandler.handleCell(): stream " + stream.ID + " is closed: " + relay.data[0]);
                stream.closed_for_reason = (relay.payload[0]) & 0xff;
                stream.closed = true;
                stream.close(true);
                this.stopped = true;
                return true;
            }
        }
        return false;
    }
}
