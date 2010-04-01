package org.saintandreas.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

import net.sf.onioncoffee.ServerConnection;

import org.apache.commons.logging.LogFactory;

public class IOProcessor implements Runnable {
    private final ExecutorService executor;
    private final Selector selector;
    private Queue<SocketHandler> registrationQueue = new LinkedList<SocketHandler>();

    public Selector getSelector() {
        return selector;
    }

    public IOProcessor(ExecutorService executor) {
        this.executor = executor;
        try {
            this.selector = SelectorProvider.provider().openSelector();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void reactor(Selector selector) throws IOException {
        for (SelectionKey sk : selector.keys()) {
            SocketHandler handler = (SocketHandler) sk.attachment();
            try {
                if (handler.isMaskChanged()) {
                    sk.channel().register(selector, handler.getSelectionMask(), handler);
                }
            } catch (IOException e) {
                sk.cancel();
                handler.onException(e);
            }

        }
        if (0 <= selector.select(100)) {
            for (SelectionKey sk : selector.selectedKeys()) {
                SocketHandler handler = (SocketHandler) sk.attachment();
                try {
                    
                    if (sk.isConnectable()) {
                        handler.onConnect(sk);
                    }
                    if (sk.isAcceptable()) {
                        handler.onAccept(sk);
                    }
                    if (sk.isWritable()) {
                        handler.onWrite(sk);
                    }
                    if (sk.isReadable()) {
                        handler.onRead(sk);
                    }
                } catch (IOException e) {
                    sk.cancel();
                    handler.onException(e);
                }
            }
            LogFactory.getLog(IOProcessor.class).trace("Number of selection keys " + selector.keys().size());
            for (SelectionKey sk : selector.keys()) {
                SocketHandler handler = (SocketHandler) sk.attachment();
                try {
                    if (handler.idleAge() > handler.maxIdleAge()) {
                        handler.onTimeout();
                    }
                } catch (IOException e) {
                    sk.cancel();
                    handler.onException(e);
                }

            }

        }
    }

    public void run() {
        try {
            while (!executor.isShutdown()) {
                // handle existing connections
                if (!registrationQueue.isEmpty()) {
                    synchronized (registrationQueue) {
                        for (SocketHandler handler : registrationQueue) {
                            handler.getSocketChannel().register(selector, handler.getSelectionMask(), handler);
                        }
                        registrationQueue.clear();
                    }
                }
                reactor(selector);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addRegisteringHandler(SocketHandler hanlder) {
        synchronized (registrationQueue) {
            registrationQueue.add(hanlder);
        }
    }

}