package net.sf.onioncoffee.test;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class RegexTest {
    public static final Log LOG = LogFactory.getLog(RegexTest.class);
    public static final int REGEX_FLAGS = Pattern.UNIX_LINES + Pattern.CASE_INSENSITIVE + Pattern.DOTALL;
    public static final int REGEX_MULTILINE_FLAGS = Pattern.MULTILINE + REGEX_FLAGS;

    @Test
    public void testServerRegex() throws IOException {
//        String descriptor = StringUtil.readFromResource("/examples/foo.txt");
//        Pattern p = Pattern.compile(Server.REGEX, REGEX_MULTILINE_FLAGS);
//        Matcher m = p.matcher(descriptor);
//        LOG.debug("Using descriptor: \r\n" + descriptor);
//        LOG.debug("Using regex: \r\n" + Server.REGEX);
//        while (m.find()) {
//            String description = m.group(1);
//            String name = m.group(2);
//        }
    }
}
