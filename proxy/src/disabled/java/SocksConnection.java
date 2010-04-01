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
package net.sf.onioncoffee.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;

import net.sf.onioncoffee.TCPStream;
import net.sf.onioncoffee.TCPStreamProperties;

class SocksConnection extends Thread {
    static final int payload = 498; // maximum dataLength of Tor-Cell Payload
    static final int sleep_millis = 10;
    Socket local;

    public SocksConnection(Socket local) {
        // start connection
        this.local = local;
        this.setName("SocksConnection");
        this.start();
    }

    static void relay(Socket local, DataInputStream read_remote, DataOutputStream write_remote) {
        try {
            byte[] data = new byte[payload];
            DataInputStream read_local = new DataInputStream(local.getInputStream());
            DataOutputStream write_local = new DataOutputStream(local.getOutputStream());
            /*
             * DataInputStream read_remote = new
             * DataInputStream(remote.getInputStream()); DataOutputStream
             * write_remote = new DataOutputStream(remote.getOutputStream());
             */
            boolean action = false;
            while (true) {
                // data from local?
                if (read_local.available() > 0) {
                    int cc = read_local.read(data);
                    // System.out.println(" >> "+cc+" bytes");
                    write_remote.write(data, 0, cc);
                    write_remote.flush();
                    action = true;
                }
                // data from remote?
                if (read_remote.available() > 0) {
                    int cc = read_remote.read(data);
                    // System.out.println(" << "+cc+" bytes");
                    write_local.write(data, 0, cc);
                    write_local.flush();
                    action = true;
                }
                // rest a bit, if no action
                if (!action) {
                    Thread.sleep(sleep_millis);
                }
                action = false;
                // System.out.println("loop...");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private void socks4(DataInputStream read_local, DataOutputStream write_local) {
        TCPStream remote = null;
        Socket remoteS = null;
        byte[] command = new byte[8];
        byte[] answer = new byte[8];

        try {
            // read socks-command
            read_local.read(command, 1, 1);
            if (command[1] != 1) {
                answer[1] = 91; // failed
                write_local.write(answer);
                write_local.flush();
                throw new Exception("only support for CONNECT");
            }
            // read port and IP for Socks4
            read_local.read(command, 2, 6);
            byte[] raw_ip = new byte[4];
            System.arraycopy(command, 4, raw_ip, 0, 4);
            int port = (((command[2]) & 0xff) << 8) + ((command[3]) & 0xff);
            // read user name (and throw away)
            while (read_local.readByte() != 0) {
            }
            // check for SOCKS4a
            if ((raw_ip[0] == 0) && (raw_ip[1] == 0) && (raw_ip[2] == 0) && (raw_ip[3] != 0)) {
                StringBuffer sb = new StringBuffer(256);
                byte b;
                do {
                    b = read_local.readByte();
                    if (b != 0) {
                        sb.append((char) b);
                    }
                } while (b != 0);
                String hostname = sb.toString();
                // connect
                if (MainWindow.useTor()) {
                    remote = tor.connect(new TCPStreamProperties(hostname, port));
                } else {
                    remoteS = new Socket(hostname, port);
                }
            } else {
                // SOCKS 4
                InetAddress in_addr = InetAddress.getByAddress(raw_ip);
                if (MainWindow.useTor()) {
                    remote = tor.connect(new TCPStreamProperties(in_addr, port));
                } else {
                    remoteS = new Socket(in_addr, port);
                }
            }
            // send OK for socks4
            write_local.write(answer);
            // MAIN DATA TRANSFERCOPY LOOP
            if (MainWindow.useTor()) {
                relay(local, new DataInputStream(remote.getInputStream()), new DataOutputStream(remote.getOutputStream()));
            } else {
                relay(local, new DataInputStream(remoteS.getInputStream()), new DataOutputStream(remoteS.getOutputStream()));
            }
        } catch (Exception e) {
            System.err.println(e);
        } finally {
            if (remote != null) {
                remote.close();
            }
            if (remoteS != null) {
                try {
                    remoteS.close();
                } catch (Exception e) {
                }
                ;
            }
        }
    }

    private void socks5(DataInputStream read_local, DataOutputStream write_local) {
        TCPStream remote = null;
        Socket remoteS = null;
        byte[] methods;
        byte[] command = new byte[8];
        byte[] answer = new byte[2];

        try {
            answer[0] = 5;
            answer[1] = (byte) 0xff;
            // read methods
            read_local.read(command, 0, 1);
            if (command[0] <= 0) {
                write_local.write(answer);
                write_local.flush();
                throw new Exception("number of supported methods must be >0");
            }
            methods = new byte[command[0]];
            read_local.readFully(methods);
            // check for anonymous/unauthenticated connection
            boolean found_anonymous = false;
            for (int i = 0; i < methods.length; ++i) {
                found_anonymous = found_anonymous || (methods[i] == 0);
            }
            if (!found_anonymous) {
                write_local.write(answer);
                write_local.flush();
                throw new Exception("no accepted method listed by client");
            }
            // ok, we can tell the client to connect without username/password
            answer[1] = 0;
            write_local.write(answer);
            write_local.flush();
            // read and parse client request
            command = new byte[4];
            read_local.readFully(command);
            if (command[0] != 5) {
                throw new Exception("why the f*** does the client change its version number?");
            }
            if (command[1] != 1) {
                throw new Exception("only CONNECT supported");
            }
            if (command[2] != 0) {
                throw new Exception("do not play around with reserved fields");
            }
            if ((command[3] != 1) && (command[3] != 3)) {
                throw new Exception("only IPv4 and HOSTNAME supported");
            }
            // parse address
            InetAddress in_addr = null;
            String hostname = null;
            byte[] address;
            if (command[3] == 1) {
                address = new byte[4];
                read_local.readFully(address);
                in_addr = InetAddress.getByAddress(address);
            } else {
                read_local.read(command, 0, 1);
                address = new byte[(256 + command[0]) & 0xff];
                read_local.readFully(address);
                hostname = new String(address);
            }
            // read port
            byte[] port = new byte[2];
            read_local.readFully(port);
            int int_port = (((port[0]) & 0xff) << 8) + ((port[1]) & 0xff);
            // build connection
            if (MainWindow.useTor()) {
                if (hostname == null) {
                    remote = tor.connect(new TCPStreamProperties(in_addr, int_port));
                } else {
                    remote = tor.connect(new TCPStreamProperties(hostname, int_port));
                }
            } else {
                if (hostname == null) {
                    remoteS = new Socket(in_addr, int_port);
                } else {
                    remoteS = new Socket(hostname, int_port);
                }
            }
            // send reply to client
            answer = new byte[6 + address.length];
            answer[0] = 5; // version
            answer[1] = 0; // success
            answer[2] = 0; // reserved
            answer[3] = command[3];
            System.arraycopy(address, 0, answer, 4, address.length);
            System.arraycopy(port, 0, answer, 4 + address.length, 2);
            write_local.write(answer);
            // MAIN DATA TRANSFERCOPY LOOP
            if (MainWindow.useTor()) {
                relay(local, new DataInputStream(remote.getInputStream()), new DataOutputStream(remote.getOutputStream()));
            } else {
                relay(local, new DataInputStream(remoteS.getInputStream()), new DataOutputStream(remoteS.getOutputStream()));
            }
        } catch (Exception e) {
            System.err.println(e);
        } finally {
            if (remote != null) {
                remote.close();
            }
            if (remoteS != null) {
                try {
                    remoteS.close();
                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    public void run() {
        // String id = "none";
        try {
            DataInputStream read_local = new DataInputStream(local.getInputStream());
            DataOutputStream write_local = new DataOutputStream(local.getOutputStream());
            // read socks-version
            byte[] version = new byte[1];
            read_local.read(version, 0, 1);
            // prepare answer
            byte[] answer = new byte[2];
            answer[0] = 0;
            answer[1] = 90; // granted
            // parse command
            if (version[0] == 4) {
                socks4(read_local, write_local);
            } else if (version[0] == 5) {
                socks5(read_local, write_local);
            } else {
                answer[1] = 91; // failed
                write_local.write(answer);
                write_local.flush();
                throw new Exception("only support for Socks-4(a)");
            }
        } catch (Exception e) {
            System.err.println(e);
            // e.printStackTrace();
        } finally {
            // System.out.println(id+" closing down");
            try {
                local.close();
            } catch (Exception e) {
            }
            ;
        }
    }
}
