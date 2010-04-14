package net.sf.onioncoffee.test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.onioncoffee.app.HTTPProxy;

public class HttpProxyTest {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService service = Executors.newCachedThreadPool();
        HTTPProxy proxy = new HTTPProxy(8088, service);
        service.execute(proxy);
        Thread.sleep(1000 * 60 * 60);
    }
}
