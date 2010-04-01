package org.saintandreas.nio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class HttpHandler {
//implements SocketHandler {
//    public final URL requestUrl;
//    private long idle = System.currentTimeMillis();
//    private final long start = System.currentTimeMillis();;
//    public SocketChannel sc;
//    private StringBuilder request = new StringBuilder();
//    private ByteArrayOutputStream response = new ByteArrayOutputStream();
//    private ByteBuffer responseBuffer = ByteBuffer.allocate(8192);
//    public static String GET_STRING_TEMPLATE = "GET %s HTTP/1.1\r\n"
//        + "Host: %s\r\n"
////      + "Connection: close\r\n"
//        + "User-Agent: Jakarta Commons-HttpClient/3.0.1\r\n" + "\r\n";
//
//
//
//
//
//    public HttpHandler(String requestStr, Selector selector) throws IOException {
//        requestUrl = new URL(requestStr);
//        request.append(String.format(GET_STRING_TEMPLATE, requestUrl.getPath(), requestUrl.getHost()));
//
//        sc = SocketChannel.open();
//        sc.configureBlocking(false);
//        sc.register(selector, SelectionKey.OP_CONNECT, this);
//        SocketAddress address = new InetSocketAddress(requestUrl.getHost(),
//                requestUrl.getPort() != -1 ? requestUrl.getPort()
//                        : requestUrl.getDefaultPort());
//        boolean connected = sc.connect(address);
//        if (connected) {
//            sc.register(selector, SelectionKey.OP_WRITE, this);
//        }
//    }
//
//    public long age() {
//        return System.currentTimeMillis() - start;
//    }
//
//    public long idle() {
//        return System.currentTimeMillis() - idle;
//    }
//
//    public void onConnect(SelectionKey sk) throws IOException {
//        if (sc.finishConnect()) {
//            sc.register(sk.selector(), SelectionKey.OP_WRITE, this);
//        }
//    }
//
//    public void onWrite(SelectionKey sk) throws IOException {
//        byte[] requestBytes = request.toString().getBytes();
//        ByteBuffer output = ByteBuffer.wrap(requestBytes);
//        int written = sc.write(output);
//        request.replace(0, written, "");
//        if (request.length() == 0) {
//            sc.register(sk.selector(), SelectionKey.OP_READ, this);
//        }
//    }
//
//    public void onRead(SelectionKey sk) throws IOException {
//        int read = sc.read(responseBuffer);
//        if (-1 != read) {
//            response.write(responseBuffer.array(), 0, read);
//        } else {
//            System.out.println();
//            System.out.println(response);
//            System.out.println();
//            sk.cancel();
//            sc.close();
//        }
//    }
//
//    @Override
//    public void onAccept(SelectionKey sk) throws IOException {
//        // TODO Auto-generated method stub
//        
//    }

}
