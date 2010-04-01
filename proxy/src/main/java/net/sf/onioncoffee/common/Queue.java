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
package net.sf.onioncoffee.common;

import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import net.sf.onioncoffee.Cell;
import net.sf.onioncoffee.CellRelay;
import net.sf.onioncoffee.Cell.CellType;
import net.sf.onioncoffee.CellRelay.RelayType;

/**
 * a helper class for queueing data (FIFO)
 * 
 * @author Lexi Pimenidis
 * @version unstable
 */
public class Queue {
    final int WAIT = 100;
    volatile public boolean closed;
    public int timeout = 1000; // timeout internally represented in ms
    private final java.util.Queue<Cell> queue = new ConcurrentLinkedQueue<Cell>();
    private final Vector<QueueHandler> handler = new Vector<QueueHandler>();

    /**
     * init class
     * 
     * @param timeout
     *            queue timeout in seconds
     */
    public Queue(int timeout) {
        this.closed = false;
        this.timeout = timeout * 1000;
    }

    public Queue() {
        this(1000);
    }

    public synchronized void addHandler(QueueHandler qh) {
        handler.add(qh);
    }

    public synchronized boolean removeHandler(QueueHandler qh) {
        return handler.remove(qh);
    }

    /** add a cell to the queue */
    public synchronized void add(Cell cell) {
        // first check if there are handlers installed 
        for (QueueHandler qh : handler) {
            try {
                if (qh.handleCell(cell)) {
                    return;
                }
            } catch (TorException te) {
            }
        }

        // otherwise add to queue 
        queue.offer(cell);
        this.notify();
    }

    /**
     * close the queue and remove all pending messages
     * @throws IOException 
     */
    public synchronized void close() {
        closed = true;
        for (int i = 0; i < handler.size(); ++i) {
            QueueHandler qh = handler.elementAt(i);
            try {
                qh.close();
            } catch (IOException e) {
                
            }
        }
        queue.clear();
        this.notify();
    }

    /** determines whether the queue is empty */
    boolean isEmpty() {
        return closed || queue.isEmpty();
    }

    public Cell get() {
        return get(timeout);
    }

    /**
     * get the first element from out of the class. Behaviour
     * 
     * @param timeout
     *            determines what will happen, if no data is in queue.
     * @return a Cell or null
     */
    public synchronized Cell get(int timeout) {
        boolean forever = false;
        if (timeout == -1) {
            forever = true;
        }

        Cell retVal = null;
        for (int retries = timeout / WAIT; forever || (retries > 0) || !queue.isEmpty(); --retries) {
            if (closed || null != (retVal = queue.poll())) {
                break;
            }
            try {
                wait(WAIT);
            } catch (InterruptedException e) {
            }
        }
        return retVal;
    }

    /**
     * interface to receive a cell that is not a relay-cell
     * 
     * @throws TimeoutException
     */
    public Cell receiveCell(CellType type) throws IOException, TorException {
        if (closed) {
            throw new TorException("Attempted to receive cell from closed queue");
        }
        Cell cell = get();

        if (cell == null) {
            throw new TorNoAnswerException("Queue.receiveCell: no answer after " + this.timeout / 1000 + " s", this.timeout);
        }
        if (cell.command != type) {
            throw new TorException("Queue.receiveCell: expected cell of type " + type + " received type " + type);
        }
        return cell;
    }

    /**
     * interface to receive a relay-cell
     * 
     * @throws TimeoutException
     */
    public CellRelay receiveRelayCell(RelayType type) throws IOException, TorException {
        CellRelay relay = (CellRelay) receiveCell(CellType.CELL_RELAY);
        if (relay.relayCommand != type) {
            if ((relay.relayCommand == RelayType.RELAY_END) && (relay.data != null)) {
                throw new TorException("Queue.receiveRelayCell: expected relay-cell of type " + type + ", received END-CELL for reason: " + relay.payload[0]);
            } else {
                throw new TorException("Queue.receiveRelayCell: expected relay-cell, received type");
            }
        }
        return relay;
    }

}
