package net.sf.onioncoffee.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StreamProxy {

	static boolean relay(InputStream leftIs, OutputStream leftOs, InputStream rightIs, OutputStream rightOs) throws IOException {
		byte[] buffer = new byte[8192];
		boolean retVal = false, leftData, rightData;
		do {
            leftData = false;
            while (leftIs.available() > 0) {
                int cc = leftIs.read(buffer);
                rightOs.write(buffer, 0, cc);
                retVal = leftData = true;
            }
            if (leftData) { 
                rightOs.flush();
            }
            rightData = false;
            while (rightIs.available() > 0) {
                int cc = rightIs.read(buffer);
                leftOs.write(buffer, 0, cc);
                retVal = rightData = true;
            }
            if (rightData) {
                 leftOs.flush();
            }
		} while (leftData || rightData);
		
		return retVal;
	}
}
