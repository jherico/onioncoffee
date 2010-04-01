package net.sf.onioncoffee.test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;

import net.sf.onioncoffee.Config;
import net.sf.onioncoffee.app.SwtProxyApp;
import net.sf.onioncoffee.common.TorException;

public class IntegrationTest {

    
    public static String GET_STRING = "GET /index.pl?content_type=rss HTTP/1.1\r\nHost: slashdot.org\r\nUser-Agent: Jakarta Commons-HttpClient/3.0.1\r\n\r\n";
    public static String CHARSET = "US-ASCII";
    
    
    public static void main(String[] args) throws GeneralSecurityException, TorException, IOException, InterruptedException, ParseException{
        Config.load();
        new SwtProxyApp();
    }

}
