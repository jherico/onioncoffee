package net.sf.onioncoffee.tasks;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.onioncoffee.Directory;
import net.sf.onioncoffee.ServiceDescriptor;
import net.sf.onioncoffee.common.StreamsAndHTTP;

import org.apache.commons.logging.LogFactory;

public class DirectoryServerThread extends Thread {
    Map<String, Map<String, Object>> sd;

    Socket client;

    public DirectoryServerThread(Socket client, Map<String, Map<String, Object>> serviceDescriptoren) {
        this.client = client;
        this.sd = serviceDescriptoren;
        this.start();
    }

    @Override
    public void run() {
        try {
            // read request
            String query = StreamsAndHTTP.readHTTPFromStream(client.getInputStream());
            PrintStream sout = new PrintStream(client.getOutputStream());
            // parse request
            Pattern p = Pattern.compile("^(GET|POST) ([^ ]+) HTTP.*?\r?\n\r?\n(.*)", Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
            Matcher m = p.matcher(query);
            if (!m.find()) {
                return;
            }
            String method = m.group(1);
            String uri = m.group(2);
            String body = m.group(3);
            // main location to handle requests
            String answer = null;
            if (method.equals("GET")) {
                if (uri.equals("/")) { // provide a directory listing
                    answer = Directory.get().toString();
                }
                if (uri.startsWith("/tor/rendezvous/")) { // send a service
                    // descriptor
                    // - extract 'z'
                    String z = uri.substring(16);
                    // - check if stored service descriptor for 'z'
                    if (this.sd.containsKey(z)) {
                        answer = (String) this.sd.get(z).get("original");
                    }
                }
            }
            ;
            if (method.equals("POST")) {
                if (uri.equals("/tor/rendezvous/publish")) { // service
                    // descriptor is
                    // uploaded
                    try {
                        HashMap<String, Object> sd_ = new HashMap<String, Object>(); // store
                                                                                     // body
                        sd_.put("original", body);
                        // - parse body to ServiceDescriptor and store in
                        // HashMap
                        ServiceDescriptor serv_desc = new ServiceDescriptor("", body.getBytes());
                        sd_.put("parsed", serv_desc);
                        // extract z and store sd_ in this.sd
                        String z = serv_desc.getURL();
                        this.sd.put(z, sd_);
                        // success
                        answer = "";
                    } catch (Exception e) {
                        LogFactory.getLog(getClass()).warn("strange data format detected or stuff");
                        answer = null;
                    }
                }
                ;
            }
            ;
            // http-status-code
            StringBuffer http;
            if (answer != null) {
                http = new StringBuffer("HTTP/1.0 200 yoo, peace man!\r\n");
            } else {
                http = new StringBuffer("HTTP/1.0 404 no way, man!\r\n");
                answer = "";
            }
            ;
            // build answer
            http.append("Content-Length: " + answer.length() + "\r\n");
            http.append("\r\n");
            http.append(answer);
            // send answer
            sout.print(http.toString());
        } catch (IOException e) {
        } finally {
            try {
                client.close();
            } catch (IOException e) {
            }
        }
    }
}
