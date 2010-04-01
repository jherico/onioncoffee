package net.sf.onioncoffee.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StreamProxy {
	private static Log LOG = LogFactory.getLog(StreamProxy.class);

	static boolean relay(InputStream leftIs, OutputStream leftOs, InputStream rightIs, OutputStream rightOs) throws IOException {
		byte[] buffer = new byte[8192];
		boolean retVal = false;
		{
			if (leftIs.available() > 0) {
				int cc = leftIs.read(buffer);
				if (LOG.isTraceEnabled()) {
					LOG.trace(" >> " + cc + " bytes");
				}
				rightOs.write(buffer, 0, cc);
				rightOs.flush();
				retVal = true;
			}
		}
		{
			// data from remote?
			if (rightIs.available() > 0) {
				int cc = rightIs.read(buffer);
				if (LOG.isTraceEnabled()) {
					LOG.trace(" << " + cc + " bytes");
				}
				leftOs.write(buffer, 0, cc);
				leftOs.flush();
				retVal = true;
			}
		}
		return retVal;
	}
}
