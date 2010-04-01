package net.sf.onioncoffee.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;

import net.sf.onioncoffee.Proxy;
import net.sf.onioncoffee.TCPStream;
import net.sf.onioncoffee.TCPStreamProperties;

import org.saintandreas.util.StreamUtil;

public class TorHTTPProxy extends HTTPProxy {
	Proxy proxy;

    public TorHTTPProxy(int port, Proxy proxy, ExecutorService executor) {
        super(port, executor);
        this.proxy = proxy;
    }

	@Override
    protected void launchClient(Socket client) {
        executor.submit(new TorConnection(client));
    }
    
    public static String GET_STRING = "GET / HTTP/1.1\r\nHost: www.google.com\r\nConnection: close\r\nUser-Agent: Jakarta Commons-HttpClient/3.0.1\r\n\r\n";
    public static String GET_IP = "74.125.19.105";

	protected class TorConnection extends Connection {

		public TorConnection(Socket local) {
			super(local);
		}
		
	    protected void finish(Socket local, String host, int port, boolean https, String request) throws UnknownHostException, IOException, InterruptedException { 
			TCPStream remote = proxy.proxyConnect(new TCPStreamProperties(host, port));
	    	try {
	            if (https) {
	                // send HTTP-success message
	                OutputStream local_write = local.getOutputStream();
	                local_write.write(success200.getBytes());
	                local_write.flush();
	                long start = System.currentTimeMillis();
		            while (local.isConnected() && !remote.closed && System.currentTimeMillis() - start < timeout) {
		            	if (relay(local.getInputStream(), local.getOutputStream(), remote.getInputStream(), remote.getOutputStream())) {
		            		start = System.currentTimeMillis();
		            	} else {
		            		Thread.sleep(50);
		            	}
		            }
	            } else {
	                // write http-request
//	            	String requestStr = 
//	            		"GET / HTTP/1.1\r\n" +
//	            		"Host: www.google.com\r\n" +
//	            		"Connection: close\r\n" +
//	            		"Accept-Language: en-us,en;q=0.5\r\n" + 
//	            		"Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n" +
//	            		"User-Agent: Jakarta Commons-HttpClient/3.0.1\r\n\r\n";
//	            	Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
//	            	Accept-Encoding: gzip,deflate
//	            	Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7
//	            	Cookie: PREF=ID=0d09ae37911a381d:TM=1266367204:LM=1266367204:S=FN33tblBTVdTWEy7; NID=31=WAdA4yitDc8B5ftC9HQHqc5wQBBSYpVxemicKmQfGegHH_3eCINvvYuRIrWwpMrswBaXiwj3o4fL7a13GNvW_BMB_IADNOawSIovzu_94F_5y_QdyqtmeWPgbugiAaYA; SID=DQAAAI8AAADvTO-ELdPTtCJ3e82ivqGxYMgUlR26jskA8YkMVFjTrHZbuvOGd70IrewAxBU0-M2IPKQhHl3_YoRcrdlDZMSvqLfyD45xd4lEOrFWaTJ5JU886yG7ijKJ0-ERvhQ00lwUMavRyHA_Au_koSFrMXCZR2MvFozrTg4OOKagqZQTVJxzRybdDnjtYcjUMjagZ1c; HSID=AH0lSdvEgLTnWgurJ; rememberme=true; TZ=480; GMAIL_RTT=199; GMAIL_LOGIN=T1265679474060/1265679474060/1265679487329
//	            	User-Agent: Jakarta Commons-HttpClient/3.0.1
//	            	Connection: close

	            	

	            	
	                remote.getOutputStream().write(request.toString().getBytes("US-ASCII"));
	                remote.getOutputStream().flush();
                    ByteArrayOutputStream bais = new ByteArrayOutputStream();
                    InputStream is = remote.getInputStream();
                    int read = -1;
                    while (-1 != (read = is.read())) {
                        bais.write(read);
                    }
                    local.getOutputStream().write(bais.toByteArray());
                    local.getOutputStream().flush();
	            } 
	    	} finally {
	    		StreamUtil.safeClose(remote);
	    	}
	    }
	}

}
