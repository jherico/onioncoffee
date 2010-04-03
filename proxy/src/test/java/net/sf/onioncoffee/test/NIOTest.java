package net.sf.onioncoffee.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.example.echoserver.ssl.BogusSslContextFactory;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

public class NIOTest extends IoHandlerAdapter {
    NioSocketConnector connector;
    IoSession session;

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException, GeneralSecurityException {
        new NIOTest().testSslGet();
        
    }
    
    public NIOTest() {
        connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(30 * 1000L);
        connector.setHandler(this);
    }
    
    public void connect(SocketAddress addr) {
        ConnectFuture cf = connector.connect(addr);
        cf.awaitUninterruptibly();
        session = cf.getSession();
    }
    
    public void finish() {
        session.getCloseFuture().awaitUninterruptibly();
        System.out.println(new String(baos.toByteArray()));
    }

    private final static String GET_STRING = "GET %s HTTP/1.1\r\nHost: slashdot.org\r\nConnection: close\r\nUser-Agent: Jakarta Commons-HttpClient/3.0.1\r\n\r\n";

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    public void testSslGet() throws GeneralSecurityException {
        SslFilter sslFilter = new SslFilter(BogusSslContextFactory.getInstance(false));
        sslFilter.setUseClientMode(true);
        connector.getFilterChain().addLast("sslFilter", sslFilter);

        connect(new InetSocketAddress("corpmail.real.com", 443));        
        session.write(IoBuffer.wrap(String.format(GET_STRING, "/owa/auth/logon.aspx").getBytes()));
        finish();
    }

    public void testGet() {
        connect(new InetSocketAddress("slashdot.org", 80));        
        session.write(IoBuffer.wrap(String.format(GET_STRING, "/").getBytes()));
        finish();
    }
    
    public void messageReceived(IoSession session, Object message) throws Exception {
        baos.write(((IoBuffer)message).array());
    }

}
