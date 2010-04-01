package net.sf.onioncoffee.test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.onioncoffee.Config;
import net.sf.onioncoffee.RefreshableDirectory;

import org.springframework.util.StopWatch;

public class NIOTest {

	public static void main(String [] args) throws InterruptedException, ExecutionException, IOException {
		ExecutorService service = Executors.newCachedThreadPool();
		Config.load();
		try {
    		RefreshableDirectory directory = new RefreshableDirectory(service);
    		StopWatch sw = new StopWatch();
    		sw.start("consensus");
    		while (!directory.isFresh()) {
                Thread.sleep(100);
    		}
    		sw.stop();
    		System.out.println(sw.prettyPrint());
    		
    		while (directory.getValidServers().size() < directory.getServers().keySet().size()) {
    	        System.out.println(directory.getValidServers().size() + " / " + directory.getServers().keySet().size());
    	        Thread.sleep(5000);
    		}
		} catch (Exception e) {
		    e.printStackTrace();
		}
		service.shutdown();
		
	}
	
}
