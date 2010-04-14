package net.sf.onioncoffee;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;


public class DirectoryTest {

    @Test
    public void consensusParseTest() throws IOException {
        String consensus = Resources.toString(Resources.getResource("/consensus"), Charsets.UTF_8);
        Directory directory = new Directory();
        directory.parseConsensus(consensus);
        assertTrue(directory.getServers().size() == 1597);
    }
}
