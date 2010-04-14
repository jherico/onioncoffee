package net.sf.onioncoffee.app;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

public abstract class SocketProxy extends StreamProxy implements Runnable {
    static final int sleep_millis = 10;
    private final int listenPort;
    protected final ExecutorService executor;

    public SocketProxy(int port, ExecutorService executor) {
        this.listenPort = port;
        this.executor = executor;
    }
    public static void safeClose(Socket s) {
        if (s != null) {
            try {
              s.close();  
            } catch (IOException e) {
                // swallow
            }
        }
    }

    @Override
    public void run() {
        try {
            ServerSocket ss = new ServerSocket(listenPort);
            while (!executor.isShutdown()) {
                Socket client = ss.accept();
                launchClient(client);
            }
        } catch (IOException e) { }
    }

    protected abstract void launchClient(Socket client); 

}
