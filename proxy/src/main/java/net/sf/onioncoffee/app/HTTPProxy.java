package net.sf.onioncoffee.app;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.message.BasicHeader;
import org.saintandreas.util.SocketUtil;

public class HTTPProxy extends SocketProxy {
	private static Log LOG = LogFactory.getLog(HTTPProxy.class);

    public HTTPProxy(int port, ExecutorService executor) {
        super(port, executor);
    }

    @Override
    protected void launchClient(Socket client) {
        executor.submit(new Connection(client));
    }
    
    private static final Set<String> BAD_HEADERS = new HashSet<String>(); static {
    	BAD_HEADERS.add("connection");
    	BAD_HEADERS.add("proxy-connection");
    	BAD_HEADERS.add("keep-alive");
    	BAD_HEADERS.add("user-agent"); 
    }
    

    class Connection implements Runnable {

        /** http header for CONNECT-calls */
//        /** the error page including header */
//        private static final String error500a = "HTTP/1.0 500 Internal Server Error\r\n" //
//                + "Content-Type: text/html\r\n\r\n" //
//                + "<HTML><HEAD><TITLE>Internal Server Error</TITLE></HEAD>\r\n" //
//                + "<BODY><H1>Internal Server Error</H1>\r\n" //
//                + "The request failed.<br>\r\n<pre>";
//        private static final String error500b = "</pre>\r\n</BODY></HTML>\r\n";
//        /** just some $arbitrarily chosen variable ;-) **/
        protected static final String success200 = "HTTP/1.0 200 Connection Established\r\n\r\n";

        protected final Socket local;
        protected int timeout = 60000;


        public Connection(Socket local) {
            // start connection
            this.local = local;
        }

        
        public void run() {
            boolean https = false;
            try {
                // prepare parsing of http-request
                StringBuffer request = new StringBuffer();
                int body_length = 0;
                int port = 80;
                String host = null;
                
                // parsing first line
                String requestLine = null; //HttpParser.readLine(local.getInputStream(), "US-ASCII"); 
                Thread.currentThread().setName("HTTPConnection " + requestLine);
                String[] parts = requestLine.split("[ ]");
                if (parts[0].startsWith("CONNECT")) {
                	https = true;
                    String[] tt = parts[1].split("[:]");
                    host = tt[0].trim();
                    port = Integer.parseInt(tt[1].trim());
                }
                // HTTP-Protocol
                request.append(parts[0] + " ");

                if (parts[1].startsWith("http://")) {
                    String uri_without_protocol = parts[1].substring(7);
                    int pos_next_slash = uri_without_protocol.indexOf('/');
                    host = uri_without_protocol.substring(0, pos_next_slash - 1);
                    if (host.indexOf(':') >= 0) {
                        try {
                            String[] tt = requestLine.split("[:]");
                            host = tt[0].trim();
                            port = Integer.parseInt(tt[1].trim());
                        } catch (NumberFormatException e) {
                        }
                    }
                    request.append(uri_without_protocol.substring(pos_next_slash));
                } else if (!https) {
                    throw new Exception("unsupported protocol");
                }
                
                for (int i = 2; i < parts.length; ++i) {
                    request.append(" " + parts[i]);
                }
                request.append("\r\n");

                BasicHeader [] headers = null; //HttpRequestParser.parseHeaders(local.getInputStream(), "US-ASCII");
                for (BasicHeader h : headers) {
                	String headerName = h.getName().toLowerCase();
                	if ("host".equals(headerName)) {
                		host = h.getValue();
                		int colonIndex = host.indexOf(':'); 
                		if (colonIndex >= 0) {
                			port = Integer.valueOf(host.substring(colonIndex + 1));
                			host = host.substring(0, colonIndex);
                		} 
                	} else if ("content-Length".equals(headerName)) {
                        try {
                            body_length = Integer.parseInt(h.getValue().trim());
                        } catch (NumberFormatException e) {
                        	LOG.warn("Unable to parse header " + h.toString());
                            body_length = 0;
                            continue;
                        }                		
                	} else if (BAD_HEADERS.contains(headerName)) {
                		continue;
                	}
                	request.append(h.toString());	
                }
                request.append("User-Agent: Jakarta Commons-HttpClient/3.0.1\r\nConnection: close\r\n\r\n");
                // read request body
                if (body_length != 0) {
	                char[] body = new char[8192];
	                int charsRead = 0;
	                InputStreamReader reader = new InputStreamReader(local.getInputStream(), "US-ASCII");
	                while (body_length != 0) {
	                	charsRead = reader.read(body);
                		request.append(body, 0, charsRead);
                		body_length -= charsRead;
	                }
                }
                
                // remote connection
                finish(local, host,  port, https, request.toString());
            } catch (Exception e) {
            	System.out.println(e);
            } finally {
                SocketUtil.safeClose(local);
            }
            Thread.currentThread().setName("Idle");
        }

        protected void finish(Socket local, String host, int port, boolean https, String request) throws UnknownHostException, IOException, InterruptedException { 
            Socket remote = new Socket(host, port);
        	try {
                if (https) {
                    // send HTTP-success message
                    OutputStream local_write = local.getOutputStream();
                    local_write.write(success200.getBytes());
                    local_write.flush();
                } else {
                    // write http-request
                    remote.getOutputStream().write(request.toString().getBytes("US-ASCII"));
                    remote.getOutputStream().flush();
                } 
                long start = System.currentTimeMillis();
                while (local.isConnected() && remote.isConnected() && System.currentTimeMillis() - start < timeout) {
                	if (relay(local.getInputStream(), local.getOutputStream(), remote.getInputStream(), remote.getOutputStream())) {
                		start = System.currentTimeMillis();
                	} else {
                		Thread.sleep(50);
                	}
                }
        	} finally {
        		SocketUtil.safeClose(remote);
        	}
        }
    }

}
