package net.sf.onioncoffee.nio;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;

public class WrappingNHttpClientHandler implements NHttpClientHandler {
    protected final NHttpClientHandler handler;
    
    public WrappingNHttpClientHandler(NHttpClientHandler handler) {
        this.handler = handler;
    }

    public void connected(NHttpClientConnection conn, Object attachment) {
        handler.connected(conn, attachment);
    }

    public void closed(NHttpClientConnection conn) {
        handler.closed(conn);
    }

    public void requestReady(NHttpClientConnection conn) {
        handler.requestReady(conn);
    }

    public void outputReady(NHttpClientConnection conn, ContentEncoder encoder) {
        handler.outputReady(conn, encoder);
    }

    public void responseReceived(NHttpClientConnection conn) {
        handler.responseReceived(conn);
    }

    public void inputReady(NHttpClientConnection conn, ContentDecoder decoder) {
        handler.inputReady(conn, decoder);
    }

    public void exception(NHttpClientConnection conn, HttpException ex) {
        handler.exception(conn, ex);
    }

    public void exception(NHttpClientConnection conn, IOException ex) {
        handler.exception(conn, ex);
    }

    public void timeout(NHttpClientConnection conn) {
        handler.timeout(conn);
    }
    
}
