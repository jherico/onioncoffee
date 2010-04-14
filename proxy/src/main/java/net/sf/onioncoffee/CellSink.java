package net.sf.onioncoffee;

import java.io.IOException;

import net.sf.onioncoffee.Cell.CellType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Brad Davis
 *
 */
public abstract class CellSink {
    private long lastActionTime; // last time, a cell was send that was not a padding cell
    private long lastCellTime; // last time, a cell was send
    private long created;

    CellSink() {
        lastCellTime = lastActionTime = created = System.currentTimeMillis();
    }

    public void sendCell(Cell c) throws IOException {
        // update 'action'-timestamp, if not padding cell
        lastCellTime = System.currentTimeMillis();
        if (CellType.CELL_PADDING != c.command) {
            lastActionTime = lastCellTime;
        }
        doSendCell(c);
    }

    protected Logger getLog() {
        return LoggerFactory.getLogger(getClass());
    }

    protected abstract void doSendCell(Cell c) throws IOException;
    public abstract void sendKeepAlive();

    
    public final long getLastActionTime() {
        return lastActionTime;
    }

    public final long getLastCellTime() {
        return lastCellTime;
    }

    public final long getCreated() {
        return created;
    }

    public final long getCellIdleTime() {
        return System.currentTimeMillis() - getLastCellTime();
    }

    public final long getActionIdleTime() {
        return System.currentTimeMillis() - getLastActionTime();
    }

 
}
