package net.sf.onioncoffee;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.onioncoffee.common.Encoding;
import net.sf.onioncoffee.common.Encryption;
import net.sf.onioncoffee.common.RegexUtil;

import org.apache.commons.logging.LogConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A data structure for retaining information about remote Tor servers (nodes)
 * 
 * @author Lexi Pimenidis
 * @author Andriy Panchenko
 * @author Michael Koellejan
 * @version unstable
 */
public class Server  {
    public static final String ROUTER_REGEX = "r (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (\\d+) (\\d+)\\s*\ns ([a-z0-9 ]+)?";
    public final static String FINGERPRINT_REGEX = "^opt fingerprint (.*?)$";
    // FIXME ??? por que?
    private static final int MAX_EXITPOLICY_ITEMS = 300;
    private static final int HIGH_BANDWIDTH = 2097152; // see updateServerRanking()
    private static final float ALPHA = 0.6f; // see updateServerRanking()
    private static final float PUNISHMENT_FACTOR = 0.75f; // coefficient to decrease

    // private static final float rankingIndexEffect = 0.5f; see getRefinedRankingIndex

    public static enum Flag {
        RUNNING("Running", 0x8000), // 
        AUTHORITY("Authority", 0x4000), //  
        EXIT("Exit", 0x2000), // 
        GUARD("Guard", 0x1000), // 
        FAST("Fast", 0x0800), // 
        STABLE("Stable", 0x0400), // 
        VALID("Valid", 0x0200), // 
        NAMED("Named", 0x0080), // 
        HSDIR("HSDir", 0x0040), // 
        BAD_EXIT("BadExit", 0x0020), //  
        V2DIR("V2Dir", 0x0010); // 

        public final String name;
        public final int val;

        private Flag(String name, int val) {
            this.name = name;
            this.val = val;
        }
    }

    // The raw router descriptor which has been handed to us.
    private String routerDescriptor;

    // country code where it is located
    String countryCode = "?";

    private int routerPort;
    private int socksPort;

    private int flags;
    private String flagsString;
    private int bandwidthAvg;
    private int bandwidthBurst;
    private int bandwidthObserved;

    private String platform;
    private Date published;
    private int uptime;

    PublicKey onionKey;
    PublicKey signingKey;

    ExitPolicy exitpolicy = new ExitPolicy();

    byte[] routerSignature;

    private String contact;

    Set<String> family = new HashSet<String>();

    // Additional information for V2-Directories
    protected Date lastUpdate;
    protected String nickname = null;
    protected String hostname = null;
    protected String digest = null;
    protected String fingerprint = null;
    protected int dirPort;
    protected int version;
    protected InetAddress address;

    // TorJava Server-Ranking data
    float rankingIndex = -1;

    private int circuitCount = 0;

    public static String parseDescriptorFingerprint(String descriptor) {
        String fingerprint = Encoding.toHexStringNoColon(Encoding.parseHex(RegexUtil.parseStringByRE(descriptor, "^opt fingerprint (.*?)$", "")));
        return fingerprint.toLowerCase().replaceAll("\\s", "");
    }
    
    public static class ConsensusComparator implements Comparator<Server> {
        public int compare(Server o1, Server o2) {
            int retVal = 0 - Integer.valueOf(o1.flags & 0xff00).compareTo(Integer.valueOf(o2.flags & 0xff00));
            if (retVal == 0) {
                retVal = o1.published.compareTo(o2.published);
                if (retVal == 0) {
                    retVal = new Random().nextBoolean() ? -1 : 1;
                }
            }
            return retVal;
        }
    }

    protected Logger getLog() {
        return LoggerFactory.getLogger(getClass());
    }

    public Server() {
    }

    public Server(int version, String fingerprint, String name, String hostname, int dirPort) {
        this.version = version;
        this.nickname = name;
        this.hostname = hostname;
        this.dirPort = dirPort;
        this.fingerprint = fingerprint;
    }
//    <constructor-arg name="name" value="moria1"/>
//    <property name="version" value="3"/>
//    <property name="hostname" value="128.31.0.34"/>
//    <property name="dirPort" value="9031"/>
//    <property name="fingerprint" value="FFCB46DB1339DA84674C70D7CB586434C4370441"/>

    public static Server parseConfigLine(String line) {
        return null;
    }

    public void parseConsensusLine(String line) {
        Pattern p = Pattern.compile(ROUTER_REGEX, RegexUtil.REGEX_MULTILINE_FLAGS);
        Matcher m = p.matcher(line);
        if (!m.find()) {
            throw new IllegalStateException("invalid server line format");
        }
        this.nickname = m.group(1);
        this.fingerprint = Encoding.toHexStringNoColon(Encoding.parseBase64(m.group(2)));
        this.digest = Encoding.toHexStringNoColon(Encoding.parseBase64(m.group(3)));
        try {
            this.published = new SimpleDateFormat(Constants.PUBLISHED_ITEM_SIMPLEDATE_FORMAT).parse(m.group(4) + " " + m.group(5));
        } catch (ParseException e) {
            throw new IllegalStateException("wrong date formate");
        }
        this.hostname = m.group(6);
        this.routerPort = Integer.parseInt(m.group(7));
        this.dirPort = Integer.parseInt(m.group(8));
        this.flagsString = m.group(9);
        this.flags = parseFlags(flagsString);
    }

    public static int parseFlags(String group) {
        Set<String> flags = new HashSet<String>();
        for (String flag : group.split("\\s")) {
            flags.add(flag);
        }
        int retVal = 0;
        for (Flag f : Flag.values()) {
            if (flags.contains(f.name)) {
                retVal |= f.val;
            }
        }
        return retVal;
    }

    /**
     * compound data structure for storing exit policies
     */

    public static long ALL_NETMASK = 0xFFFFFFFFL;
    public class ExitPolicy {
        public class Rule {
            public long ip = 0;
            public long netmask = ALL_NETMASK;
            public int lo_port = 0;
            public int hi_port = 65535;

            public boolean match(long ip, int port) {
                return port <= hi_port && port >= lo_port && ((ip & netmask) == this.ip);
            }
        }

        public boolean defaultAccept = true;
        public final List<Rule> rejectRules = new ArrayList<Rule>();
        public final List<Rule> acceptRules = new ArrayList<Rule>();

        public boolean accept(InetAddress addr, int port) {
            long ip;
            if (addr != null) { // set IP as given
                byte[] temp1 = addr.getAddress();
                long[] temp = new long[4];
                for (int i = 0; i < 4; ++i) {
                    temp[i] = temp1[i];
                    if (temp[i] < 0) {
                        temp[i] = 256 + temp[i];
                    }
                }
                ip = ((temp[0] << 24) | (temp[1] << 16) | (temp[2] << 8) | temp[3]);
            } else {
                // HACK: if no IP and port is given, always return true
                if (port == 0) {
                    return true;
                }
                // HACK: if no IP is given, use only exits that allow ALL ip-ranges
                // this should possibly be replaced by some other way of checking it
                ip = ALL_NETMASK;
            }
            return accept(ip, port);
        }

        public boolean accept(long ip, int port) {
            if (defaultAccept) {
                for (Rule r : rejectRules) {
                    if (r.match(ip, port)) {
                        return false;
                    }
                }
            } else {
                for (Rule r : acceptRules) {
                    if (r.match(ip, port)) {
                        return true;
                    }
                }
            }
            return defaultAccept;
        }

        public void parseRuleMatch(String ruleType, String network, String portRange) {
            if (network.equals("*") && portRange.equals("*")) {
                defaultAccept = ruleType.equals("accept");
            }
            Rule r = new Rule();
            // parse network
            if (!network.equals("*")) {
                int slash = network.indexOf("/");
                r.ip = Encoding.dottedNotationToBinary((slash >= 0) ? network.substring(0, slash) : network);
                if (slash >= 0) {
                    String netmask = network.substring(slash + 1);
                    if (netmask.contains(".")) {
                        r.netmask = Encoding.dottedNotationToBinary(netmask);
                    } else {
                        r.netmask = (((0xffffffffL << (32 - (Integer.parseInt(netmask))))) & 0xffffffffL);
                    }
                }
            }
            r.ip = r.ip & r.netmask;

            // parse port range
            if (!portRange.equals("*")) {
                int dash = portRange.indexOf("-");
                if (dash > 0) {
                    r.lo_port = Integer.parseInt(portRange.substring(0, dash));
                    r.hi_port = Integer.parseInt(portRange.substring(dash + 1));
                } else {
                    r.lo_port = Integer.parseInt(portRange);
                    r.hi_port = r.lo_port;
                }
            }
            (ruleType.equals("accept") ? acceptRules : rejectRules).add(r);
        }

        /**
         * This function parses the exit policy items from the router
         * descriptor.
         * 
         * @param routerDescriptor
         *            a router descriptor with exit policy items.
         * @return the complete exit policy
         */
        public void parseExitPolicy(String routerDescriptor) {
            this.defaultAccept = true;
            this.acceptRules.clear();
            this.rejectRules.clear();
            Pattern p = Pattern.compile("^(accept|reject) (.*?):(.*?)$", RegexUtil.REGEX_MULTILINE_FLAGS);
            Matcher m = p.matcher(routerDescriptor);
            int nr = 0;
            while (m.find() && (nr++ < MAX_EXITPOLICY_ITEMS)) {
                parseRuleMatch(m.group(1), m.group(2), m.group(3));
            }
        }

    }

    public InetAddress getAddress() {
        return address;
    }

    /**
     * extracts all relevant information from the router discriptor and saves it
     * in the member variables.
     * 
     * @param descriptor
     *            string encoded router descriptor
     * @param flags
     * @throws LogConfigurationException
     * @throws InvalidCipherTextException
     * @throws IOException
     */
    public boolean parseDescriptor(String descriptor) throws IOException {
        this.routerDescriptor = descriptor;

        Pattern p = Pattern.compile("^router (\\w+) (\\S+) (\\d+) (\\d+) (\\d+)", Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
        Matcher m = p.matcher(descriptor);
        m.find();

        if (!m.group(1).equals(getName())) {
            return false;
        }

        if (!m.group(2).equals(getHostname())) {
            return false;
        }

        this.routerPort = Integer.valueOf(m.group(3));
        this.dirPort = Integer.valueOf(m.group(5));

        String dateStr = RegexUtil.parseStringByRE(descriptor, "^published (.*?)$", "");
        Date date = new SimpleDateFormat(Constants.PUBLISHED_ITEM_SIMPLEDATE_FORMAT).parse(dateStr, new ParsePosition(0));
        if (!date.equals(getPublished())) {
            return false;
        }

        String fingerprint = parseDescriptorFingerprint(descriptor);
        if (!fingerprint.equals(getFingerprint())) {
            throw new RuntimeException("Fingerprint match failure");
        }

        socksPort = Integer.parseInt(m.group(4));
        platform = RegexUtil.parseStringByRE(descriptor, "^platform (.*?)$", "unknown");
        uptime = Integer.parseInt(RegexUtil.parseStringByRE(descriptor, "^uptime (\\d+)", "0"));
        contact = RegexUtil.parseStringByRE(descriptor, "^contact (.*?)$", "");

        // bandwidth
        p = Pattern.compile("^bandwidth (\\d+) (\\d+) (\\d+)?", Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
        m = p.matcher(descriptor);
        if (m.find()) {
            bandwidthAvg = Integer.parseInt(m.group(1));
            bandwidthBurst = Integer.parseInt(m.group(2));
            bandwidthObserved = Integer.parseInt(m.group(3));
        }

        // make that IF description is from a trusted server, that digest is correct
        Server desc = Config.trustedServers.get(getName());
        if (null != desc) {
            if (!getDigest().equals(desc.getDigest())) {
                throw new IllegalStateException("Server " + getName() + " is trusted, but digest check failed");
            }
        }

        // onion key
        String stringOnionKey = RegexUtil.parseStringByRE(descriptor, "^onion-key\n(.*?END RSA PUBLIC KEY......)", "");
        onionKey = Encryption.extractRSAKey(stringOnionKey);

        // signing key
        String stringSigningKey = RegexUtil.parseStringByRE(descriptor, "^signing-key\n(.*?END RSA PUBLIC KEY-----\n)", "");
        signingKey = Encryption.extractRSAKey(stringSigningKey);

        // verify signing-key against digest
        byte[] pkcs = Encryption.getPKCS1EncodingFromRSAPublicKey((RSAPublicKey) signingKey);
        byte[] key_hash = Encryption.getHash(pkcs);

        if (!Arrays.equals(key_hash, getFingerprintBytes())) {
            throw new IllegalStateException("Server " + getName() + " doesn't verify signature vs digest");
        }

        // parse family
        String stringFamily = RegexUtil.parseStringByRE(descriptor, "^family (.*?)$", "");
        if ("".equals(stringFamily)) {
            stringFamily = RegexUtil.parseStringByRE(descriptor, "^opt family (.*?)$", "");
        }
        Pattern p_family = Pattern.compile("(\\S+)");
        Matcher m_family = p_family.matcher(stringFamily);
        while (m_family.find()) {
            //            String host = m_family.group(1);
            //            family.add(host);
        }

        // check the validity of the signature
        routerSignature = Encoding.parseBase64(RegexUtil.parseStringByRE(descriptor, "^router-signature\n-----BEGIN SIGNATURE-----(.*?)-----END SIGNATURE-----", ""));
        byte[] sha1_input = (RegexUtil.parseStringByRE(descriptor, "^(router .*?router-signature\n)", "")).getBytes();
        if (!Encryption.verifySignature(routerSignature, signingKey, sha1_input)) {
            LoggerFactory.getLogger(Server.class).warn("Server -> router-signature check failed for " + getName());
            throw new IllegalStateException("Server " + getName() + ": description signature verification failed");
        }

        // exit policy
        exitpolicy.parseExitPolicy(descriptor);

        // usually in directory the hostname is already set to the IP
        // so, following resolve just converts it to the InetAddress
        this.address = InetAddress.getByName(getHostname());

        /**
         * updates the server ranking index
         * 
         * Is supposed to be between 0 (undesirable) and 1 (very desirable). Two
         * variables are taken as input:
         * <ul>
         * <li>the uptime
         * <li>the bandwidth
         * <li>if available: the previous ranking
         * </ul>
         */
        float rankingFromDirectory = (Math.min(1, getUptime() / 86400) + Math.min(1, (bandwidthAvg * ALPHA + bandwidthObserved * (1 - ALPHA)) / HIGH_BANDWIDTH)) / 2; // 86400
        // build over-all ranking from old value (if available) and new
        if (rankingIndex < 0) {
            rankingIndex = rankingFromDirectory;
        } else {
            rankingIndex = rankingFromDirectory * (1 - Config.rankingTransferPerServerUpdate) + rankingIndex * Config.rankingTransferPerServerUpdate;
        }
        return true;
    }

    /**
     * returns ranking index taking into account user preference
     * 
     * @param p
     *            user preference (importance) of considering ranking index
     *            <ul>
     *            <li>0 select hosts completely randomly
     *            <li>1 select hosts with good uptime/bandwidth with higher
     *            prob.
     *            </ul>
     */
    public float getRefinedRankingIndex(float p) {
        // align all ranking values to 0.5, if the user wants to choose his
        // servers
        // from a uniform probability distribution
        return (rankingIndex * p + Config.rankingIndexEffect * (1 - p));
    }

    /**
     * decreases ranking_index by the punishment_factor
     */
    public void punishRanking() {
        rankingIndex *= PUNISHMENT_FACTOR;
    }

    /**
     * can be used to query the exit policies wether this server would allow
     * outgoing connections to the host and port as given in the parameters.
     * <b>IMPORTANT:</b> this routing must be able to work, even if <i>addr</i>
     * is not given!
     * 
     * @param addr
     *            the host that someone wants to connect to
     * @param port
     *            the port that is to be connected to
     * @return a boolean value wether the conenction would be allowed
     */
    boolean exitPolicyAccepts(InetAddress addr, int port) { // used by
        return exitpolicy.accept(addr, port);
    }

    /**
     * @return can this server be used as a directory-server?
     */
    public boolean isDirServer() {
        return (getDirPort() > 0);
    }

    public String getKey() {
        return getFingerprint();
    }

    public String getRouterDescriptor() {
        return routerDescriptor;
    }

    public String getPlatform() {
        return platform;
    }

    public int getRouterPort() {
        return routerPort;
    }

    public int getSocksPort() {
        return socksPort;
    }

    public Date getPublished() {
        return published;
    }

    public int getUptime() {
        return uptime;
    }

    public String getContact() {
        return contact;
    }

    public final String getName() {
        return nickname;
    }

    public final String getHostname() {
        return hostname;
    }

    public final int getDirPort() {
        return dirPort;
    }

    public final String getDigest() {
        return digest;
    }

    public final String getFingerprint() {
        return fingerprint;
    }

    public final byte[] getFingerprintBytes() {
        return Encoding.parseHex(getFingerprint());
    }

    public final int getVersion() {
        return version;
    }

    public String toString() {
        return getName() + ((getLog().isDebugEnabled()) ? "(" + getDigest() + ")" : "");
    }

    public String getDirUrl() {
        return "http://" + getHostname() + ":" + getDirPort() + "/tor/server/d/";
    }

    public boolean hasFlag(Flag flag) {
        return (this.flags & flag.val) == flag.val;
    }

    public int getCircuitCount() {
        return circuitCount;
    }

    public synchronized void incrementCircuitCount() {
        ++circuitCount;
    }

    public synchronized void decrementCircuitCount() {
        --circuitCount;
    }

    public int getBandwidthAvg() {
        return bandwidthAvg;
    }

    public int getBandwidthBurst() {
        return bandwidthBurst;
    }

    public int getBandwidthObserved() {
        return bandwidthObserved;
    }

    public String getFlagsString() {
        return flagsString;
    }
    
    public InetSocketAddress getDirAddress() {
        return new InetSocketAddress(getHostname(), getDirPort());
    }

    public SocketAddress getRouterAddress() {
        return new InetSocketAddress(getHostname(), getRouterPort());
    }

    public static class RunningPredicate implements org.apache.commons.collections15.Predicate<Server> {
        @Override
        public boolean evaluate(Server object) {
            return object.hasFlag(Flag.RUNNING);
        }
    }

    public static class ValidDirPortPredicate implements org.apache.commons.collections15.Predicate<Server> {
        @Override
        public boolean evaluate(Server object) {
            return object.getDirPort() != 0;
        }
    }

}
