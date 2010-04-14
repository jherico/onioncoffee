package net.sf.onioncoffee.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * this background thread closes all streams that have been build by
 * StreamThreads but are not used any more<br>
 * FIXME: cache ready streams and possibly reuse them later on
 * 
 * @author Lexi
 */
public class ClosingThread  extends Thread {

    StreamThread[] threads;
    int chosenOne;
    protected Logger getLog() {
        return LoggerFactory.getLogger(getClass());
    }

    public ClosingThread(StreamThread[] threads, int chosenOne) {
        this.threads = threads;
        this.chosenOne = chosenOne;
        this.start();
    }

    @Override
    public void run() {
        // loop and check when threads finish and then close the streams
        for (int i = 0; i < threads.length; ++i) {
            if (i != chosenOne) {
                if (threads[i].stream != null) {
                    try { // finish the queue
                        threads[i].stream.closed = true;
                        threads[i].stream.queue.close();
                    } catch (Exception e) {
                        getLog().warn("Tor.ClosingThread.run(): " + e.getMessage());
                    }
                    try {
                        threads[i].stream.close();
                    } catch (Exception e) {
                        getLog().warn("Tor.ClosingThread.run(): " + e.getMessage());
                    }
                }
                try {
                    threads[i].join();
                } catch (Exception e) {
                    getLog().warn("Tor.ClosingThread.run(): " + e.getMessage());
                }
            }
        }
    }
}

