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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import net.sf.onioncoffee.tasks.DirectoryServerThread;

import org.apache.commons.logging.LogFactory;

/**
 * main class for directory server functionality
 * 
 * @author Lexi Pimenidis
 * @version unstable
 */

public class DirectoryServer extends Thread {

    Map<String, Map<String, Object>> serviceDescriptors;

    ServerSocket dir_server;

    /**
     * creates the server socket and installs a dispatcher for incoming data.
     * 
     * @param dir_port
     *            the port to open for directory services
     * @exception IOException
     */
    DirectoryServer(int dir_port) throws IOException {
        if (dir_port < 1) {
            throw new IOException("invalid port given");
        }
        if (dir_port > 0xffff) {
            throw new IOException("invalid port given");
        }
        serviceDescriptors = new HashMap<String, Map<String, Object>>();

        LogFactory.getLog(getClass()).info("DirectoryServer: starting directory server on port " + dir_port);
        dir_server = new ServerSocket(dir_port);
        this.start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                // receive new connection
                Socket client = dir_server.accept();
                String descr = client.getInetAddress().getHostAddress() + ":" + client.getPort();
                LogFactory.getLog(getClass()).debug("Incoming connection to directory from " + descr);
                // handle connection
                new DirectoryServerThread(client, serviceDescriptors);
                // close connection
            }
        } catch (IOException e) {
        }
    }

    /**
     * close down the directory server
     */
    void close() {
        LogFactory.getLog(getClass()).debug("DirectoryServer.close(): Closing directory server");

        // close connections
        try {
            dir_server.close();
        } catch (IOException e) {
        }
    }
}

