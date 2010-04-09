/**
 * OnionCoffee - Anonymous Communication through TOR Network Copyright (C)
 * 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package net.sf.onioncoffee;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.onioncoffee.common.Encoding;
import net.sf.onioncoffee.common.RegexUtil;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.saintandreas.util.Loggable;
import org.saintandreas.util.StringUtil;

/**
 * This class maintains a list of the currently known Tor routers. It is primarily concerned with the 
 * storage of information and has no knowledge of how to execute network transfers.
 * 
 * @author Brad Davis
 * @author Lexi Pimenidis
 * @author Tobias Koelsch
 * @author Andriy Panchenko
 * @author Michael Koellejan
 * @author Johannes Renner
 * 
 */
public class Directory extends Loggable {
    private static final String ROUTER_REGEX = "(^r .+?\ns .+?\nv .+?\nw .+?\np .+?\n)";
    private static final String DIR_SOURCE_REGEX = "^dir-source (\\S+) (\\S+) (\\S+) (\\S+) (\\d+) (\\d+)\\s*\ncontact (.+)\nvote-digest (\\S+)";
    private static final String DIR_SIG_REGEX = "^directory-signature (\\S+) (\\S+)\\s*\n-----BEGIN SIGNATURE-----\n(.*?)-----END SIGNATURE";
    private static final String CONSENSUS_DATE_REGEX = "^valid-after (\\S+ \\S+)\nfresh-until (\\S+ \\S+)\nvalid-until (\\S+ \\S+)";
    protected static final long MIN_CONSENSUS_AGE = 10 * 60 * 1000;

    // List of known Tor servers
    private final Map<String, Server> servers = new HashMap<String, Server>();
    private final Set<String> validServers = new HashSet<String>();
    private DateTime validUntil = new DateTime(0);
    private DateTime validAfter = new DateTime(0);
    private DateTime freshUntil = new DateTime(0);
    private long consensusLastUpdate = 0;

    public boolean isFresh() {
        return (new DateTime().isBefore(freshUntil));
    }
    
    public boolean isValid() {
        return (new DateTime().isBefore(validUntil));
    }
    
    public Set<String> getValidServers() {
        return Collections.unmodifiableSet(validServers);
    }
    
    public Set<String> getInvalidServers() {
        Set<String> retVal = new HashSet<String>(servers.keySet());
        retVal.removeAll(getValidServers());
        return retVal;
    }

    public Map<String, Server> getValidServerMap() {
        Map<String, Server> serverMap = new HashMap<String, Server>(getServers());
//        serverMap.keySet().retainAll(validServers);
        return serverMap;
    }
    
    public boolean parseServer(Server server, String serverDescriptor) {
        boolean retVal = false;
        try {
          if (server.parseDescriptor(serverDescriptor)) {
              synchronized (validServers) {
                  validServers.add(server.getFingerprint());
              }
              retVal = true;
              if (getLog().isTraceEnabled()) {
                  getLog().trace("got server " + server.getFingerprint());
              }
          }
        } catch (Exception e) {
            // FIXME
//            getLog().error("Failed to parse server descriptor", e);
        }
        return retVal;
    }
 
    public void parseConsensus(String consensus)  {
        
        // Parse the document
        Pattern p;
        Matcher m;
        // Check the version
        String version = StringUtil.parseStringByRE(consensus, "^network-status-version (\\d+)", "");
        if (!"3".equals(version)) {
            throw new IllegalStateException("wrong network status version");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.PUBLISHED_ITEM_SIMPLEDATE_FORMAT);
        // Parse valid-until
        p = Pattern.compile(CONSENSUS_DATE_REGEX, RegexUtil.REGEX_MULTILINE_FLAGS);
        m = p.matcher(consensus);
        if (m.find()) {
            try {
                validAfter = new DateTime(dateFormat.parse(m.group(1)));
                freshUntil = new DateTime(dateFormat.parse(m.group(2)));
                validUntil = new DateTime(dateFormat.parse(m.group(3)));
            } catch (ParseException e) {
                throw new IllegalStateException("wrong date formate");
            }
            DateTime now = new DateTime();
            if (now.isBefore(validUntil)) {
                Period period = new Interval(now, validUntil).toDuration().toPeriod();
                getLog().info("Consensus document is valid for "
                        + String.format("%02d:%02d:%02d", period.getHours(), period.getMinutes(), period.getSeconds()));
            } else {
                Period period = new Interval(validUntil, now).toDuration().toPeriod();
                
                getLog().warn("Consensus document is out of date by "
                        + String.format("%02d:%02d:%02d", period.getHours(), period.getMinutes(), period.getSeconds()));
            }
        }

        // TODO: Check signature here
        // Extract the signed data
        //        byte[] signedData = Parsing.parseStringByRE(consensus, "^(network-status-version.*directory-signature )", "").getBytes();
        //        byte[] hashedData = Encryption.getHash(signedData);
        // Parse signatures
        p = Pattern.compile(DIR_SOURCE_REGEX, RegexUtil.REGEX_MULTILINE_FLAGS - Pattern.DOTALL);
        //        m = p.matcher(consensus);
        //        Set<Source> sourcesx = new HashSet<Source>();
        //        while (m.find()) {
        //            Source source = new Source(m);
        //            sourcesx.add(source);
        //        }
        //
        //        for (Source s : sourcesx) {
        //
        //        }
        p = Pattern.compile(DIR_SIG_REGEX, RegexUtil.REGEX_MULTILINE_FLAGS);
        //        m = p.matcher(consensus);
        //        while (m.find()) {
        //            Signature signature = new Signature(m);
        //        }

        // Parse the single routers
        p = Pattern.compile(ROUTER_REGEX, RegexUtil.REGEX_MULTILINE_FLAGS);
        m = p.matcher(consensus);
        // Loop to extract all routers
        Map<String, Server> consensusServers = new HashMap<String, Server>();
        while (m.find()) {
            Server server = new Server();
            server.parseConsensusLine(m.group());
            consensusServers.put(server.getFingerprint(), server);
        }

        // remove out of date servers
        validServers.retainAll(consensusServers.keySet());
        servers.keySet().retainAll(consensusServers.keySet());
        // remove existing servers from list of consensus servers
        consensusServers.keySet().removeAll(servers.keySet());
        // add new new consensus servers to the primary server map 
        servers.putAll(consensusServers);
        consensusLastUpdate = System.currentTimeMillis();
    }

    long getConsensusAge() {
        return System.currentTimeMillis() - consensusLastUpdate;
    }
    
    public static class Signature {
        public Signature(Matcher m) {
            signature = Encoding.parseBase64(m.group());
            digest = Encoding.parseHex(m.group(1));
            keyDigest = Encoding.parseHex(m.group(2));
        }

        byte[] signature;
        byte[] keyDigest;
        byte[] digest;
    }

    public static class Source {
        public Source(Matcher m) throws UnknownHostException {
            nick = m.group(1);
            identity = m.group(2);
            address = m.group(3);
            ip = InetAddress.getByName(m.group(4));
            port = Short.valueOf(m.group(5));
            orport = Short.valueOf(m.group(6));
            contact = m.group(7);
            voteDigest = m.group(8);
        }

        public String nick;
        public String identity;
        public String address;
        public InetAddress ip;
        public Short port;
        public Short orport;
        public String contact;
        public String voteDigest;
    }

    public Map<String, Server> getServers() {
        return Collections.unmodifiableMap(servers);
    }

    public DateTime getValidUntil() {
        return validUntil;
    }

    public DateTime getValidAfter() {
        return validAfter;
    }

    public DateTime getFreshUntil() {
        return freshUntil;
    }

    public static boolean consensusFresh(String candidate) {
        boolean retVal = false;
        if (candidate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.PUBLISHED_ITEM_SIMPLEDATE_FORMAT);
            Pattern p = Pattern.compile(CONSENSUS_DATE_REGEX, RegexUtil.REGEX_MULTILINE_FLAGS);
            Matcher m = p.matcher(candidate);
            if (m.find()) {
                try {
                    Date freshUntil = dateFormat.parse(m.group(2));
                    if (Calendar.getInstance().getTime().before(freshUntil)) {
                        retVal = true;
                    }
                } catch (ParseException e) {

                }
            }
        }
        return retVal;
    }

}
