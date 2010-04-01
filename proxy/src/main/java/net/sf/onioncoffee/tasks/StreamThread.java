package net.sf.onioncoffee.tasks;

import net.sf.onioncoffee.Circuit;
import net.sf.onioncoffee.TCPStream;
import net.sf.onioncoffee.TCPStreamProperties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * this class is used to build a TCPStream in the background
 * 
 * @author Lexi
 */
public class StreamThread extends Thread {
    private static final Log LOG = LogFactory.getLog(StreamThread.class);

    public TCPStream stream;
    Circuit cs;
    TCPStreamProperties sp;
    boolean finished = false;

    /** copy data to local variables and start background thread */
    public StreamThread(Circuit cs, TCPStreamProperties sp) {
        this.cs = cs;
        this.sp = sp;
        this.finished = false;
        this.start();
    }

    /**
     * build stream in background and return. possibly the stream is closed
     * prematurely by another thread by having its queue closed
     */
    @Override
    public void run() {
        try {
            this.stream = new TCPStream(cs, sp);
        } catch (Exception e) {
            if ((stream != null) && (stream.queue != null) && (!stream.queue.closed)) {
                LOG.warn("Tor.StreamThread.run(): " + e.getMessage());
            }
            this.stream = null;
        }
        this.finished = true;
    }
}
