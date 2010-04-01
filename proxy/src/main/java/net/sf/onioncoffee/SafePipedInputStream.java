package net.sf.onioncoffee;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;


/**
 * this class is meant to simulate the behaviour of a standard stream. It's
 * necessary because at some point the PipedInputStream returns a IOException,
 * if the connection has been closed by the remote side, where a InputStream
 * would only return a 'null'.
 * 
 * @author Lexi Pimenidis
 * @see PipedInputStream
 * @see InputStream
 */
public class SafePipedInputStream extends PipedInputStream {

    @Override
    public int read() throws IOException {
        try {
            return super.read();
        } catch (IOException e) {
            // catch only if the connected PipeOutputStream is closed. otherwise
            // rethrow the error
            String msg = e.getMessage();
            if ((msg != null) && msg.equals("Write end dead")) {
                return -1;
            } else {
                throw e;
            }
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            return super.read(b, off, len);
        } catch (IOException e) {
            // catch only if the connected PipeOutputStream is closed. otherwise
            // rethrow the error
            String msg = e.getMessage();
            if ((msg != null) && (msg.equals("Write end dead") || msg.equals("Pipe closed"))) {
                b = null;
                return 0;
            } else {
                throw e;
            }
        }
    }
}
