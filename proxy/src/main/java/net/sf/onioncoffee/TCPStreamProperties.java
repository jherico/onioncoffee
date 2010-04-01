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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

import net.sf.onioncoffee.common.Encoding;

/**
 * Compound data structure.
 * 
 * @author Lexi Pimenidis
 * @author Andriy Panchenko
 * @version unstable
 */
public class TCPStreamProperties {
    public String hostname = null;
    public int port;
    public InetAddress addr = null;
    // allow exit servers to be untrusted
    public boolean allowUntrustedExit = true; 
    // allow entry node to be non Guard (dirv2)
    public boolean allowNonGuardEntry = true; 
    public boolean exitPolicyRequired = true;

    private boolean resolved = false; 

    public int route_min_length = Config.route_min_length;

    public int route_max_length = Config.route_max_length;

    int connect_retries = Config.retriesStreamBuildup;

    float p = 1; // [0..1] 0 -> select hosts completely randomly

    // 1 -> select hosts with good uptime/bandwidth with higher prob.

    Server[] route;

    /**
     * preset the data structure with all necessary attributes
     * 
     * @param host
     *            give a hostname
     * @param port
     *            connect to this port
     */
    public TCPStreamProperties(InetAddress addr, String host, int port, boolean addr_resolved) {
        this.addr = addr;
        this.hostname = host;
        this.port = port;
        this.resolved = addr_resolved;
    }

    public TCPStreamProperties(int port) {
        this(null, null, port, false);
    }

    public TCPStreamProperties(String host, int port) {
        this(null, host, port, false);
        if (Encoding.isDottedNotation(host)) {
          try {
            this.addr = InetAddress.getByName(host);
          } catch (UnknownHostException e) {}
        }
    }

    public TCPStreamProperties(InetAddress addr, int port) {
        this(addr, addr.getHostAddress(), port, true);
    }

    public TCPStreamProperties() {
        this(null, null, 0, false);
    }

    /**
     * set minimum route dataLength
     * 
     * @param min
     *            minimum route dataLength
     */
    public void setMinRouteLength(int min) {
        if (min <= 0) {
            throw new IllegalArgumentException("invalid minimum route length" + min);
        }
        route_min_length = min;
    }

    /**
     * set maximum route dataLength
     * 
     * @param max
     *            maximum route dataLength
     */
    public void setMaxRouteLength(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("invalid maximum route length" + max);
        }

        route_max_length = max;
    }

    /**
     * get minimum route dataLength
     * 
     * @return minimum route dataLength
     */
    public int getMinRouteLength() {
        return route_min_length;
    }

    /**
     * get maximum route dataLength
     * 
     * @return maximum route dataLength
     */
    public int getMaxRouteLength() {
        return route_max_length;
    }

    /**
     * sets predefined route
     * 
     * @param route
     *            custom route
     */
    public void setCustomRoute(Server[] route) {
        this.route = route;
    }

    /**
     * sets this node as a predefined exit-point
     * 
     * @param node
     */
    public void setCustomExitpoint(Server node) {
        if (route == null) {
            this.route_min_length = this.route_max_length;
            route = new Server[this.route_max_length];
        }
        route[route.length - 1] = node;
    }

    /**
     * @return predefined route
     * 
     */
    public Server[] getProposedRoute() {
        return route;
    }

    public float getRankingInfluenceIndex() {
        return p;
    }

    public void setRankingInfluenceIndex(float p) {
        this.p = p;
    }

    /**
     * returns hostname if set, in another case the IP
     * 
     */
    public String getDestination() {
        if (hostname.length() > 0) {
            return hostname;
        }
        return addr.getHostAddress();
    }

    void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    boolean isResolved() {
        return resolved;
    }

    public int getRandomRouteLength() {
        return route_min_length + new Random().nextInt((route_max_length - route_min_length) + 1) ;
    }
}
