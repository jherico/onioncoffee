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
package net.sf.onioncoffee.app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import net.sf.onioncoffee.Proxy;
import net.sf.onioncoffee.TCPStream;
import net.sf.onioncoffee.TCPStreamProperties;

import org.saintandreas.util.SocketUtil;
import org.saintandreas.util.StreamUtil;

class SocksProxy extends SocketProxy {

	Proxy proxy;
	
    public SocksProxy(int port, Proxy proxy, ExecutorService executor) {
        super(port, executor);
        this.proxy = proxy;
    }

    @Override
    public void launchClient(Socket client) {
        executor.submit(new Connection(client));
    }
    

    class Connection implements Runnable {
        Socket local;

        public Connection(Socket local) {
            // start connection
            this.local = local;
        }

        private void socks4(DataInputStream read_local,DataOutputStream write_local)  {
            TCPStream remote=null;

            byte[] command = new byte[8];
            byte[] answer = new byte[8];

            try{
                // read socks-command
                read_local.read(command,1,1);
                if (command[1]!=1) {
                    answer[1] = 91;  // failed
                    write_local.write(answer);
                    write_local.flush();
                    throw new IOException("only support for CONNECT");
                }
                // read port and IP for Socks4
                read_local.read(command,2,6);
                byte[] raw_ip = new byte[4];
                System.arraycopy(command,4,raw_ip,0,4);
                int port = ((((int)command[2])&0xff)<<8) + (((int)command[3])&0xff);
                // read user name (and throw away)
                while(read_local.readByte()!=0) {}
                // check for SOCKS4a
                if ((raw_ip[0]==0)&&(raw_ip[1]==0)&&(raw_ip[2]==0)&&(raw_ip[3]!=0)) {
                    StringBuffer sb = new StringBuffer(256);
                    byte b;
                    do {
                        b = read_local.readByte();
                        if (b!=0) sb.append((char)b);
                    } while(b!=0);
                    String hostname = sb.toString();
                    // connect
                    remote = proxy.proxyConnect(new TCPStreamProperties(hostname,port));
                } else {
                    // SOCKS 4
                    InetAddress in_addr = InetAddress.getByAddress(raw_ip);
                    remote = proxy.proxyConnect(new TCPStreamProperties(in_addr,port));
                }
                // send OK for socks4
                write_local.write(answer);
                // MAIN DATA TRANSFERCOPY LOOP
//                relay(local,new DataInputStream(remote.getInputStream()),new DataOutputStream(remote.getOutputStream()));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally{
                SocketUtil.safeClose(local);
                StreamUtil.safeClose(remote);
            }
        }

        private void socks5(DataInputStream read_local,DataOutputStream write_local)  {
            TCPStream remote=null;
            Socket remoteS=null;
            byte[] methods; 
            byte[] command = new byte[8];
            byte[] answer = new byte[2];

            try{
                answer[0] = 5;
                answer[1] = (byte)0xff;
                // read methods
                read_local.read(command,0,1);
                if (command[0]<=0) {
                    write_local.write(answer);
                    write_local.flush();
                    throw new Exception("number of supported methods must be >0");
                }
                methods = new byte[command[0]];
                read_local.readFully(methods);
                // check for anonymous/unauthenticated connection
                boolean found_anonymous = false;
                for(int i=0;i<methods.length;++i)
                    found_anonymous = found_anonymous || (methods[i] == 0);
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
                if (command[0]!=5 ) throw new Exception("why the f*** does the client change its version number?");
                if (command[1]!=1 ) throw new Exception("only CONNECT supported");
                if (command[2]!=0 ) throw new Exception("do not play around with reserved fields");
                if ((command[3]!=1)&&(command[3] != 3)) throw new Exception("only IPv4 and HOSTNAME supported");
                // parse address
                InetAddress in_addr=null;
                String hostname=null;
                byte[] address;
                if (command[3]==1) {
                    address = new byte[4];
                    read_local.readFully(address);
                    in_addr = InetAddress.getByAddress(address);
                } else {
                    read_local.read(command,0,1);
                    address = new byte[(256+command[0]) & 0xff];
                    read_local.readFully(address);
                    hostname = new String(address);
                }
                // read port
                byte[] port = new byte[2];
                read_local.readFully(port);
                int int_port = ((((int)port[0])&0xff)<<8) + (((int)port[1])&0xff);
                // build connection
                    if (hostname==null)
                        remote = proxy.proxyConnect(new TCPStreamProperties(in_addr,int_port));
                    else
                        remote = proxy.proxyConnect(new TCPStreamProperties(hostname,int_port));
                // send reply to client
                answer = new byte[6+address.length];
                answer[0] = 5;  // version
                answer[1] = 0;  // success
                answer[2] = 0;  // reserved
                answer[3] = command[3];
                System.arraycopy(address,0,answer,4,address.length);
                System.arraycopy(port,0,answer,4+address.length,2);
                write_local.write(answer);
                // MAIN DATA TRANSFERCOPY LOOP
//                    relay(local,new DataInputStream(remote.getInputStream()),new DataOutputStream(remote.getOutputStream()));
            }
            catch(Exception e) {
                System.err.println(e);
            }
            finally{
                if(remote!=null) remote.close();
                if(remoteS!=null){ try{ remoteS.close(); } catch(Exception e){};}
            }
        }
        
        public void run() {
            //String id = "none";
            try{
                DataInputStream read_local = new DataInputStream(local.getInputStream());
                DataOutputStream write_local = new DataOutputStream(local.getOutputStream());
                // read socks-version
                byte[] version = new byte[1];
                read_local.read(version,0,1);
                // prepare answer
                byte[] answer = new byte[2];
                answer[0] = 0;
                answer[1] = 90; // granted
                // parse command
                if (version[0]==4) {
                    socks4(read_local, write_local);
                } else if (version[0] == 5) {
                    socks5(read_local, write_local);
                } else { 
                    answer[1] = 91;  // failed
                    write_local.write(answer);
                    write_local.flush();
                    throw new Exception("only support for Socks-4(a)");
                }
            }
            catch(Exception e) {
                System.err.println(e);
                //e.printStackTrace();
            }
            finally{
                //System.out.println(id+" closing down");
                try{ local.close(); }catch(Exception e){};
            }
        }
    }
    
}

