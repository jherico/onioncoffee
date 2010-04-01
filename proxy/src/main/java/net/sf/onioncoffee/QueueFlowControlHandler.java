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

import net.sf.onioncoffee.CellRelay.RelayType;
import net.sf.onioncoffee.common.QueueHandler;

import org.apache.commons.logging.LogFactory;

/**
 * convenient way to handle flow-control
 * 
 * @author Lexi
 */
public class QueueFlowControlHandler implements QueueHandler {
    int counter;
    int currLevel;
    int startLevel;
    int incLevel;
    Circuit circ;
    TCPStream stream;
    boolean circuitLevel;

    QueueFlowControlHandler(Circuit circ, int startLevel, int incLevel) {
        this.circ = circ;
        this.counter = 0;
        this.startLevel = startLevel;
        this.currLevel = startLevel;
        this.incLevel = incLevel;
    }

    QueueFlowControlHandler(TCPStream stream, int startLevel, int incLevel) {
        this.stream = stream;
        this.counter = 0;
        this.startLevel = startLevel;
        this.currLevel = startLevel;
        this.incLevel = incLevel;
    }

    private synchronized void count() {
        --currLevel;
        ++counter;
    }

    private synchronized void increase() {
        currLevel += incLevel;
    }

    /** return TRUE, if cell was handled */
    public boolean handleCell(Cell cell) {
        count();
        // dropped below threshold - oh no!
        // better start sending SENDMEs...
        if (currLevel <= startLevel - incLevel) {
            try {
                if (circ != null) {
                    // send to all routers in the circuit
                    LogFactory.getLog(getClass()).debug("QueueFlowControlHandler.mainAction(): (" + counter + ") " + currLevel + "<" + startLevel + " sending SENDME for circuit " + circ);
                    for (int i = 0; i < circ.route_established; ++i) {
                        CellRelay sendcell = new CellRelay(circ, RelayType.RELAY_SENDME);
                        sendcell.setAddressedRouter(i);
                        circ.sendCell(sendcell);
                    }
                }
                if (stream != null) {
                    // send to end-point
                    LogFactory.getLog(getClass()).debug("QueueFlowControlHandler.mainAction(): (" + counter + ") " + currLevel + "<" + startLevel + " sending SENDME for stream " + stream);
                    stream.sendCell(new CellRelay(stream, RelayType.RELAY_SENDME));
                }
                increase();
            } catch (IOException e) {
                if (circ != null) {
                    LogFactory.getLog(getClass()).warn("QueueFlowControlHandler.mainAction(): error sending SENDME " + e.getMessage());
                }
                if (stream != null) {
                    LogFactory.getLog(getClass()).warn("QueueFlowControlHandler.mainAction(): error sending SENDME " + e.getMessage());
                }
            }
        }
        // always return FALSE to avoid swallowing cells
        return false;
    }

    /** close these things */
    public void close() {
    }
}
