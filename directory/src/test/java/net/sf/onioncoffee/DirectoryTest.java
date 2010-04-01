package net.sf.onioncoffee;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.saintandreas.util.StringUtil;


public class DirectoryTest {

    @Test
    public void consensusParseTest() throws IOException {
        String consensus = StringUtil.readFromResource("/consensus");
        Directory directory = new Directory();
        directory.parseConsensus(consensus);
        assertTrue(directory.getServers().size() == 1597);
    }
}
