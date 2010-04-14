package net.sf.onioncoffee.app;

import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.net.SocketFactory;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import com.google.common.io.Closeables;

public class HTTPProxy extends SocketProxy {
	protected SocketFactory remoteSocketFactory = null;

	private HttpParams params = new BasicHttpParams();
	
    public HTTPProxy(int port, ExecutorService executor) {
        this(port, executor, null);
    }

    public HTTPProxy(int port, ExecutorService executor, SocketFactory remoteSocketFactory) {
        super(port, executor);
        this.remoteSocketFactory = remoteSocketFactory;
    }

    @Override
    protected void launchClient(Socket client) {
        executor.submit(new Connection(client));
    }
    
    private static final Set<String> BAD_HEADERS = new HashSet<String>(); static {
    	BAD_HEADERS.add("connection");
    	BAD_HEADERS.add("proxy-connection");
    	BAD_HEADERS.add("keep-alive");
//    	BAD_HEADERS.add("user-agent"); 
    }
    
    protected static final HttpResponse success200; 
    static {
        success200 = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 0), 200, "Connection Established"));
    }


    public Socket getRemoteSocket(String host, int port) throws UnknownHostException, IOException {
        Socket retVal = null;
        if (remoteSocketFactory != null) {
            retVal = remoteSocketFactory.createSocket(host, port);
        } else {
            retVal = new Socket(host, port);
        }
        return retVal;
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

        protected final Socket local;
        protected int timeout = 60000;


        public Connection(Socket local) {
            // start connection
            this.local = local;
        }

        private class MyDefaultHttpRequestFactory extends DefaultHttpRequestFactory {
            @Override
            public HttpRequest newHttpRequest(final RequestLine requestline) 
                throws MethodNotSupportedException {
                if (requestline == null) {
                    throw new IllegalArgumentException("Request line may not be null");
                }
                String method = requestline.getMethod();
                if ("CONNECT".equalsIgnoreCase(method)) {
                    return new BasicHttpRequest(requestline); 
                }
                return super.newHttpRequest(requestline);
            }

        }
        
        private class MyDefaultHttpServerConnection extends DefaultHttpServerConnection {
            @Override
            protected HttpRequestFactory createHttpRequestFactory() {
                return new MyDefaultHttpRequestFactory();
            }
        }
        public void run() {
            boolean https = false;
            String remoteHost = null;
            String remotePath = "/";
            int remotePort = 80;
            try {
                DefaultHttpServerConnection localConnection = new MyDefaultHttpServerConnection();
                localConnection.bind(local, params);
                HttpRequest inHttpRequest = localConnection.receiveRequestHeader();

                if ("CONNECT".equalsIgnoreCase(inHttpRequest.getRequestLine().getMethod())){
                    https = true;
                    remoteHost = inHttpRequest.getRequestLine().getUri();
                    int index = remoteHost.indexOf(':');
                    remotePort = Integer.parseInt(remoteHost.substring(index + 1));
                    remoteHost = remoteHost.substring(0, index);
                } else {
                    URL url = new URL(inHttpRequest.getRequestLine().getUri());
                    remoteHost = url.getHost();
                    remotePath = url.getFile();
                    remotePort = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
                }
                
                BasicRequestLine requestLine = new BasicRequestLine(
                        inHttpRequest.getRequestLine().getMethod(), 
                        remotePath, 
                        new ProtocolVersion("HTTP", 1, 1));
                BasicHttpRequest outHttpRequest = new BasicHttpRequest(requestLine);

                Header [] headers = inHttpRequest.getAllHeaders();
                for (Header h : headers) {
                    String headerName = h.getName().toLowerCase();
                    if (BAD_HEADERS.contains(headerName)) {
                        continue;
                    }
                    outHttpRequest.addHeader(h);
                }
                outHttpRequest.addHeader(new BasicHeader("Connection", "close"));
                Socket remote = getRemoteSocket(remoteHost, remotePort);
                try {
                    if (https) {
                        // send HTTP-success message
                        localConnection.sendResponseHeader(success200);
                        localConnection.flush();
                        long start = System.currentTimeMillis();
                        while (local.isConnected() && remote.isConnected() && System.currentTimeMillis() - start < timeout) {
                            if (relay(local.getInputStream(), local.getOutputStream(), remote.getInputStream(), remote.getOutputStream())) {
                                start = System.currentTimeMillis();
                            } else {
                                Thread.sleep(10);
                            }
                        }
                    } else {
                        // write http-request
                        DefaultHttpClientConnection clientConnection = new DefaultHttpClientConnection();
                        clientConnection.bind(remote, params);
                        clientConnection.sendRequestHeader(outHttpRequest);
                        clientConnection.flush();
                        HttpResponse response = clientConnection.receiveResponseHeader();
                        localConnection.sendResponseHeader(response);
                        clientConnection.receiveResponseEntity(response);
                        localConnection.sendResponseEntity(response);
                        localConnection.flush();
                    } 
                } finally {
                    Closeables.closeQuietly(remote);
                    safeClose(remote);
                }
            } catch (Exception e) {
            	System.out.println(e);
            	e.printStackTrace();
            	System.out.println(remoteHost + ":" + remotePort + " " + remotePath);
            } finally {
                safeClose(local);
            }
            Thread.currentThread().setName("Idle");
        }

    }

}
