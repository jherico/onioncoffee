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
import java.net.InetAddress;
import java.util.concurrent.TimeoutException;

import net.sf.onioncoffee.CellRelay.RelayType;
import net.sf.onioncoffee.common.Queue;
import net.sf.onioncoffee.common.TorException;

import org.apache.commons.logging.LogFactory;

/**
 * used to anonymously resolve hostnames
 * 
 * @author Lexi Pimenidis
 * @version unstable
 */

public class ResolveStream extends TCPStream {

    // wait x seconds for answer
    int queue_timeout = Config.queueTimeoutResolve;

    ResolveStream(Circuit c) {
        super(c);
    }

    /**
     * creates a new stream and does an anonymous DNS-Lookup. <br>
     * FIXME: RESOLVED-cells can transport an arbitrary amount of
     * answer-records. currently only the first is returned
     * 
     * @param hostname
     *            a hostname to be resolved, or for a reverse lookup:
     *            A.B.C.D.in-addr.arpa
     * @return either an InetAddress (normal query), or a String
     *         (reverse-DNS-lookup)
     * @throws TimeoutException
     * @throws TorException
     */
    Object resolve(String hostname) throws IOException, TorException, TimeoutException {
        ID = circ.assignStreamID(this);
        circ.streamHistory.add(hostname); // adds resolved hostname to the
        // history
        queue = new Queue(queue_timeout);
        closed = false;
        LogFactory.getLog(getClass()).debug("resolving hostname " + hostname + " on stream " + this);

        // send RELAY-RESOLV
        CellRelay cell = new CellRelay(this, RelayType.RELAY_RESOLVE);
        cell.appendData(hostname.getBytes());
        sendCell(cell);
        // wait for RELAY_RESOLVED
        CellRelay relay = queue.receiveRelayCell(RelayType.RELAY_RESOLVED);
        // read payload
        int len = ((relay.data[1]) & 0xff);
        byte[] value = new byte[len];
        System.arraycopy(relay.data, 2, value, 0, value.length);
        // check for error
        if (relay.data[0] == (byte) 0xf0) {
            throw new TorException("transient error: " + new String(value));
        }
        if (relay.data[0] == (byte) 0xf1) {
            throw new TorException("non transient error: " + new String(value));
        }
        // check return code
        if ((relay.data[0] != 0) && (relay.data[0] != 4) && (relay.data[0] != 6)) {
            throw new TorException("can't handle answers of type " + relay.data[0]);
        }
        // return payload
        if (relay.data[0] == 0) {
            return new String(value);
        } else {
            return InetAddress.getByAddress(value);
        }
    }
}
