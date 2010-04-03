package net.sf.onioncoffee;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import javax.net.SocketFactory;

import net.sf.onioncoffee.Server.Flag;
import net.sf.onioncoffee.common.TorException;
import net.sf.onioncoffee.tasks.ClosingThread;
import net.sf.onioncoffee.tasks.StreamThread;

import org.apache.commons.collections15.CollectionUtils;
import org.apache.commons.collections15.Factory;
import org.apache.commons.collections15.MapUtils;
import org.apache.commons.collections15.Predicate;
import org.apache.commons.collections15.functors.InstantiateFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.saintandreas.nio.IOProcessor;
import org.saintandreas.util.StringUtil;
import org.saintandreas.util.ThreadUtil;

public class Proxy extends SocketFactory {
    // general multiplicator for time
    private static final int MILLISEC = 1000;
    // time to wait inbetween working loads
    private static final int INTERVAL = 3;
    // interval of padding messages on circuits
    private static final int MAX_CIRCUIT_IDLE = 30 * 1000;
    // interval of padding messages on streams
    private static final int MAX_STREAM_IDLE = 30 * 1000;

    protected final ExecutorService executor = Executors.newCachedThreadPool();
    protected Directory directory = new RefreshableDirectory(executor);
    private final Set<String> excludedNodesByConfig = new HashSet<String>();
    private final IOProcessor ioProcessor;
    
    // Map of server class C addresses to servers that share that class C
    @SuppressWarnings("unchecked")
    private final Map<String, Set<String>> addressNeighbours = MapUtils.lazyMap(new HashMap<String, Set<String>>(), 
            new InstantiateFactory<Set<String>>((Class<Set<String>>) (Class<?>)HashSet.class));
    // Map of country codes to servers that report that country code
    @SuppressWarnings("unchecked")
    private final Map<String, Set<String>> countryNeighbours = MapUtils.lazyMap(new HashMap<String, Set<String>>(), 
            new InstantiateFactory<Set<String>>((Class<Set<String>>) (Class<?>)HashSet.class));
    // nicknames of currently used nodes in circuits as key, # of cirs - value
    private final Map<String, ServerConnection> connectionMap = new HashMap<String, ServerConnection>();
    
    private class CircuitAndStreamManager implements Runnable {
        private final Log LOG = LogFactory.getLog(CircuitAndStreamManager.class);

        // at least this amount of circuits should always be available
        // upper bound is (number_of_circuits+Config.circuitsMaximumNumber)
        private int minimumCircuits;

        public CircuitAndStreamManager() {
            this.minimumCircuits = Config.defaultIdleCircuits;
            spawnIdleCircuits(minimumCircuits);
        }

        /**
         * create some empty circuits to have at hand - does so in the background
         */
        private void spawnIdleCircuits(int amount) {
            LOG.info("Spawn " + amount + " new circuits");
            // Spawn new background threads
            for (int i = 0; i < amount; ++i) {
                LOG.trace("CircuitAndStreamManager.spawn_idle_circuits: Circuit-Spawning thread started.");
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // idle threads should at least allow using port 80
                            createCircuit(new TCPStreamProperties(80));
                        } catch (Exception e) {
                            LOG.debug("CircuitAndStreamManager.spawn_idle_circuits: " + e.getMessage());
                        }
                    }
                });
            }
        }

        /**
         * sends keep-alive data on circuits TODO can this ever block? perhaps
         * based on flow control?
         */
        private void sendKeepAlivePackets() {
            for (Circuit c : getCurrentCircuits()) {
                // check if this circuit needs a keep-alive-packet
                if (c.established && c.getCellIdleTime() > MAX_CIRCUIT_IDLE) {
                    LOG.trace("CircuitAndStreamManager.send_keep_alive_packets(): Circuit " + c);
                    c.sendKeepAlive();
                }
                // check streams in circuit
                for (TCPStream stream : c.streams.values()) {
                    if ((stream.established) && (!stream.closed) && (stream.getCellIdleTime() > MAX_STREAM_IDLE)) {
                        LOG.trace("CircuitAndStreamManager.send_keep_alive_packets(): Stream " + stream);
                        stream.sendKeepAlive();
                    }
                }
            }
        }

        /**
         * used to determine which (old) circuits can be torn down because there
         * are enough new circuits. or builds up new circuits, if there are not
         * enough.
         */
        private void maintainIdleCircuits() {
            // count circuits
            int circuits_total = 0; // all circuits
            int circuits_alive = 0; // circuits that are building up, or that
            // are established
            int circuits_established = 0; // established, but not already closed
            int circuits_closed = 0; // closing down
            String flag;

            List<Circuit> currentCircuits = new ArrayList<Circuit>(getCurrentCircuits()); 
            for (Circuit c : currentCircuits) {
                ++circuits_total;
                if (c.closed) {
                    flag = "C";
                    ++circuits_closed;
                } else {
                    flag = "B";
                    ++circuits_alive;
                    if (c.established) {
                        flag = "E";
                        ++circuits_established;
                    }
                }
                LOG.debug("CircuitAndStreamManager.idle_circuits(): " + flag + " rank " + c.ranking + " fails " + c.stream_fails + " of " + c.stream_counter + " TLS ");
            }
            LOG.debug("CircuitAndStreamManager.idle_circuits(): circuit counts: " + (circuits_alive - circuits_established) + " building, " + circuits_established
                    + " established + " + circuits_closed + " closed = " + circuits_total);

            // check if enough 'alive' circuits are there
            if (circuits_alive < minimumCircuits) {
                LOG.info("CircuitAndStreamManager.idle_circuits(): spawn " + (minimumCircuits - circuits_alive) + " new circuits");
                spawnIdleCircuits((minimumCircuits - circuits_alive) * 3 / 2);
            } else if (circuits_established > minimumCircuits + Config.circuitsMaximumNumber) {
                // TODO: if for some reason there are too many established circuits. close the oldest ones
                LOG.debug("CircuitAndStreamManager.idle_circuits(): kill " + (minimumCircuits + Config.circuitsMaximumNumber - circuits_alive) + "new circuits (FIXME)");
            }
        }

        /**
         * used to close circuits that are marked for closing, but are still
         * alive. They are closed, if no more streams are contained.
         */
        private void tearDownClosedCircuits() {
            for (TCPStream s : getCurrentStreams()) {
                if ((!s.established) || s.closed) {
                    if (s.getActionIdleTime() > (2000 * Config.queueTimeoutStreamBuildup)) {
                        LOG.debug("CircuitAndStreamManager.tear_down_closed_circuits(): closing stream (too long building) " + s);
                        s.close(true);
                    }
                }
            }

            for (Circuit c : getCurrentCircuits()) {
                // check if circuit is establishing but doesn't had any action
                // for a longer period of time
                if ((!c.established) && (!c.closed)) {
                    if (c.getActionIdleTime() > (2000 * Config.queueTimeoutCircuit)) {
                        LOG.debug("CircuitAndStreamManager.tear_down_closed_circuits(): closing (too long building) " + c);
                        c.close(false);
                    }
                }
                // check if this circuit should not accept more streams
                if (c.established_streams > Config.streamsPerCircuit) {
                    LOG.debug("CircuitAndStreamManager.tear_down_closed_circuits(): closing (maximum streams) " + c);
                    c.close(false);
                }
                // if closed, recall close() again and again to do garbage
                // collection and stuff
                if (c.closed) {
                    c.close(false);
                }
            }
            removeDeadCircuts();
        }

        public void run() {
            while (!executor.isShutdown()) {
                try {
                    // do work
                    maintainIdleCircuits();
                    tearDownClosedCircuits();
                    sendKeepAlivePackets();
                    // wait
                    Thread.sleep(INTERVAL * MILLISEC);
                } catch (Exception e) {
                    continue;
                }
            }
        }
    }

    private class TorSocketImpl extends SocketImpl {

        public static final int TOR_RETRIES_CONNECT_OPT = 1;
        public static final int TOR_MAXIMUM_ROUTE_LEN = 2;
        TCPStream stream;
        int retriesConnect;
        int maximumRouteLen;

        public TorSocketImpl() {
            retriesConnect = Config.retriesConnect;
            maximumRouteLen = 3;
        }

        @Override
        protected void listen(int backlog) throws java.io.IOException {
            throw new RuntimeException("Not implemented");
        }

        @Override
        protected void sendUrgentData(int data) throws java.io.IOException {
            throw new RuntimeException("Not implemented");
        }

        @Override
        protected void accept(SocketImpl s) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        protected void bind(InetAddress host, int port) throws java.io.IOException {
            throw new RuntimeException("Not implemented");
        }

        @Override
        protected int available() throws java.io.IOException {
            if (stream == null) {
                throw new java.io.IOException();
            }
            return stream.getInputStream().available();
        }

        @Override
        protected void close() throws java.io.IOException {
            stream.close();
        }

        protected void doConnect(TCPStreamProperties tcpProp) throws java.io.IOException {
            tcpProp.setMaxRouteLength(maximumRouteLen);
            this.stream = proxyConnect(tcpProp);
            this.address = tcpProp.addr;
            this.port = tcpProp.port;

        }

        @Override
        protected void connect(String host, int port) throws java.io.IOException {
            doConnect(new TCPStreamProperties(host, port));
        }

        @Override
        protected void connect(InetAddress address, int port) throws java.io.IOException {
            doConnect(new TCPStreamProperties(address, port));
        }

        @Override
        protected void connect(SocketAddress address, int timeout) throws java.io.IOException {
            InetSocketAddress inetSockAdr = ((InetSocketAddress) address);
            connect(inetSockAdr.getAddress(), inetSockAdr.getPort());
        }

        @Override
        protected void create(boolean stream) throws java.io.IOException {
            if (!stream) {
                throw new IOException();
            }
        }

        @Override
        protected InputStream getInputStream() throws java.io.IOException {
            return stream.getInputStream();
        }

        @Override
        protected OutputStream getOutputStream() throws java.io.IOException {
            return stream.getOutputStream();
        }

        public Object getOption(int optID) throws SocketException {
            if (optID == TOR_RETRIES_CONNECT_OPT) {
                return retriesConnect;
            } else if (optID == TOR_MAXIMUM_ROUTE_LEN) {
                return maximumRouteLen;
            }
            return null;
        }

        public void setOption(int optID, Object value) throws SocketException {
            if (optID == TOR_RETRIES_CONNECT_OPT) {
                retriesConnect = ((Integer) value).intValue();
            } else if (optID == TOR_MAXIMUM_ROUTE_LEN) {
                maximumRouteLen = ((Integer) value).intValue();
            }
        }
    }

    private class TorSocket extends Socket {

        public TorSocket() throws SocketException {
            super(new TorSocketImpl());
        }

        public TorSocket(InetSocketAddress dest, InetSocketAddress src) throws SocketException, IOException {
            super(new TorSocketImpl());
//            this.bind(src);
            this.connect(dest);
        }

        public TorSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            this(new InetSocketAddress(address, port), new InetSocketAddress(localAddress, localPort));
        }

        public TorSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
            this(new InetSocketAddress(host, port), new InetSocketAddress(localAddress, localPort));
        }

        public TorSocket(InetAddress address, int port) throws UnknownHostException, IOException {
            this(address, port, InetAddress.getByName("127.0.0.1"), 6543);
        }

        public TorSocket(String host, int port) throws IOException {
            this(host, port, InetAddress.getByName("127.0.0.1"), 6543);
        }

    }

    public Proxy() {
        Config.load();
        this.ioProcessor = new IOProcessor(executor);
        executor.submit(ioProcessor);
    }

    protected Log getLog() {
        return LogFactory.getLog(getClass());
    }

    /**
     * Exclude related nodes: family, class C and country (if specified in
     * Config)
     * 
     * @param s
     *            node that should be excluded with all its relations
     * @return set of excluded node names
     */
    public Set<String> getRelatedNodes(Server s) {
        Set<String> excludedServers = new HashSet<String>();
        String ipClassCString = StringUtil.parseStringByRE(s.getAddress().getHostAddress(), "(.*)\\.", "");
        if (Config.route_uniq_class_c && addressNeighbours.containsKey(ipClassCString)) {
            excludedServers.addAll(addressNeighbours.get(ipClassCString));
        } else {
            excludedServers.add(s.getKey());
        }

        // excluse all country insider, if desired
        if (Config.route_uniq_country && countryNeighbours.containsKey(s.countryCode)) {
            excludedServers.addAll(countryNeighbours.get(s.countryCode));
        }
        // exclude its family as well
        excludedServers.addAll(s.family);
        return excludedServers;
    }

    @SuppressWarnings("unchecked")
    public void init() {
        Factory<Set<String>> factory = new InstantiateFactory<Set<String>>((Class<Set<String>>) (Class<?>)HashSet.class);
        Map<String, Set<String>> lazyCountryMap = MapUtils.lazyMap(countryNeighbours, factory);
        Map<String, Set<String>> lazyAddressMap = MapUtils.lazyMap(addressNeighbours, factory);

        for (Server server : directory.getServers().values()) {
            if (Config.avoidedCountries.contains(server.countryCode)) {
                excludedNodesByConfig.add(server.getKey());
            }
            // add it to the addressNeighbours
            String key = StringUtil.parseStringByRE(server.getAddress().getHostAddress(), "(.*)\\.", "");
            lazyAddressMap.get(key).add(server.getKey());
            // add it to the country neighbours
            key = server.countryCode;
            lazyCountryMap.get(key).add(server.getKey());
            getSetForKey(server.countryCode, countryNeighbours).add(server.getKey());
        }
        // init thread to renew every now and then
        executor.submit(new CircuitAndStreamManager());
    }

    private static <T> Set<T> getSetForKey(String key, Map<String, Set<T>> map) {
        Set<T> retVal = null;
        if (!map.containsKey(key)) {
            map.put(key, retVal = new HashSet<T>());
        } else {
            retVal = map.get(key);
        }
        return retVal;
    }

    /**
     * returns a route through the network as specified in
     * 
     * @see TCPStreamProperties
     * 
     * @param sp
     *            tcp stream properties
     * @return a list of servers
     */
    public Server[] createNewRoute(TCPStreamProperties sp) {
        // are servers available?
        if (directory.getServers().size() < 1) {
            throw new IllegalStateException("directory is empty");
        }

        // random value between min and max route dataLength
        // choose random servers to form route
        Server[] route = new Server[sp.getRandomRouteLength()];
        Server[] proposedRoute = sp.getProposedRoute();

        Set<String> excludedServers = new HashSet<String>();
        // take care, that none of the specified proposed servers is selected before in route
        if (proposedRoute != null) {
            for (Server s : proposedRoute) {
                excludedServers.addAll(getRelatedNodes(s));
            }
        }

        // exclude any servers with max circuits
        for (Server s : directory.getServers().values()) {
            if (s.getCircuitCount() > Config.maxCircuitsPerNode) {
                excludedServers.add(s.getKey());
            }
        }

        for (int i = route.length - 1; i >= 0; --i) {

            if (proposedRoute != null && i < proposedRoute.length) {
                route[i] = proposedRoute[i];
            } else {
                Predicate<Server> predicate = null;
                if (i == 0) {
                    predicate = new GuardPredicate(sp);
                } else if (i == (route.length - 1)) {
                    predicate = new ExitPredicate(sp);
                }
                route[i] = getRandomServer(sp, excludedServers, predicate);
            }
        }
        return route;
    }

    private Server getRandomRankedServer(Collection<Server> servers, float rankingInfluenceIndex) {
        Server retVal = null;
        float rankingSum = 0;
        // At first, calculate sum of the rankings
        for (Server server : servers) {
            rankingSum += server.getRefinedRankingIndex(rankingInfluenceIndex);
        }

        // generate a random float between 0 and rankingSum
        float serverRandom = new Random().nextFloat() * rankingSum;
        // find the matching server
        for (Server server : servers) {
            serverRandom -= server.getRefinedRankingIndex(rankingInfluenceIndex);
            if (serverRandom <= 0) {
                retVal = server;
                break;
            }
        }
        return retVal;

    }

    private Server getRandomServer(TCPStreamProperties sp, Set<String> excludedServers, Predicate<Server> predicate) {
        Map<String, Server> serverMap = directory.getValidServerMap();
        serverMap.keySet().removeAll(excludedServers);
        serverMap.keySet().removeAll(excludedNodesByConfig);

        CollectionUtils.filter(serverMap.values(), new Server.RunningPredicate());
        if (predicate != null) {
            CollectionUtils.filter(serverMap.values(), predicate);
        }
        
        List<Server> finalList = new ArrayList<Server>(serverMap.values());
        Server retVal = getRandomRankedServer(finalList, sp.getRankingInfluenceIndex());
        if (retVal != null) {
            retVal.incrementCircuitCount();
        }
        return retVal;
    }


    private static abstract class ServerPredicate implements org.apache.commons.collections15.Predicate<Server> {
        TCPStreamProperties sp;

        public ServerPredicate(TCPStreamProperties sp) {
            this.sp = sp;
        }
    }

    public static class ExitPredicate extends ServerPredicate {

        public ExitPredicate(TCPStreamProperties sp) {
            super(sp);
        }

        public boolean evaluate(Server s) {
            return s.exitPolicyAccepts(sp.addr, sp.port) && (sp.allowUntrustedExit || s.hasFlag(Flag.EXIT));
        }
    };

    public static class GuardPredicate extends ServerPredicate {
        public GuardPredicate(TCPStreamProperties sp) {
            super(sp);
        }

        public boolean evaluate(Server s) {
            return sp.allowNonGuardEntry || s.hasFlag(Flag.GUARD);
        }

    }

    /**
     * returns a set of current established circuits (only used by
     * TorJava.Proxy.MainWindow to get a list of circuits to display)
     * 
     */
    public Collection<ServerConnection> getCurrentConnections() {
        return Collections.unmodifiableCollection(connectionMap.values());
    }

    /**
     * returns a set of current established circuits (only used by
     * TorJava.Proxy.MainWindow to get a list of circuits to display)
     * 
     */
    public Collection<Circuit> getCurrentCircuits() {
        List<Circuit> allCircs = new ArrayList<Circuit>();
        for (ServerConnection tls : this.getCurrentConnections()) {
            allCircs.addAll(tls.circuits.values());
        }
        return allCircs;
    }

    /**
     * returns a set of current established circuits (only used by
     * TorJava.Proxy.MainWindow to get a list of circuits to display)
     * 
     */
    public Collection<TCPStream> getCurrentStreams() {
        List<TCPStream> retVal = new ArrayList<TCPStream>();
        for (Circuit circuit : this.getCurrentCircuits()) {
            retVal.addAll(circuit.streams.values());
        }
        return retVal;
    }

    public void removeDeadCircuts() {
        for (ServerConnection tls : this.getCurrentConnections()) {
            for (Iterator<Map.Entry<Integer, Circuit>> itr = tls.circuits.entrySet().iterator(); itr.hasNext();) {
                Circuit c = itr.next().getValue();
                if (c.destruct) {
                    getLog().debug("CircuitAndStreamManager.tear_down_closed_circuits(): destructing circuit " + c);
                    itr.remove();
                }
            }
        }
    }

    /**
     * used to return a number of circuits to a target. established a new
     * circuit or uses an existing one
     * 
     * @param sp
     *            gives some basic restrains
     * @param forHiddenService
     *            if set to true, use circuit that is unused and don't regard
     *            exit-policies
     * @param force_new
     *            create new circuit anyway
     */
    Circuit[] provideSuitableCircuits(TCPStreamProperties sp) throws IOException {
        getLog().debug("FirstNodeHandler.provideSuitableCircuits: called for " + sp.hostname);
        // list all suiting circuits in a vector
        int numberOfExistingCircuits = 0;
        Vector<Circuit> allCircs = new Vector<Circuit>(10, 10);
        int rankingSum = 0;

        for (Circuit circuit : getCurrentCircuits()) {
            try {
                ++numberOfExistingCircuits;
                if (circuit.established && (!circuit.closed) && circuit.isCompatible(sp)) {
                    allCircs.add(circuit);
                    rankingSum += circuit.ranking;
                }
            } catch (TorException e) { /* do nothing, just try next circuit */
            }
        }

        // sort circuits (straight selection... O(n^2)) by
        // - wether they contained a stream to the specific address
        // - ranking (stochastically!)
        // - implicit: wether they haven't had a stream at all
        for (int i = 0; i < allCircs.size() - 1; ++i) {
            Circuit c1 = allCircs.get(i);
            int min = i;
            int min_ranking = c1.ranking;
            if (min_ranking == 0) {
                min_ranking = 1;
            }
            boolean min_points_to_addr = c1.streamHistory.contains(sp.hostname);
            for (int j = i + 1; j < allCircs.size(); ++j) {
                Circuit thisCirc = allCircs.get(j);
                int this_ranking = thisCirc.ranking;
                if (this_ranking == 0) {
                    this_ranking = 1;
                }
                boolean this_points_to_addr = thisCirc.streamHistory.contains(sp.hostname);
                float ranking_quota = this_ranking / min_ranking;
                if ((this_points_to_addr && !min_points_to_addr) || (new Random().nextFloat() > Math.exp(-ranking_quota))) { // sort
                    // stochastically
                    min = j;
                    min_ranking = this_ranking;
                }
            }
            if (min > i) {
                Circuit temp = allCircs.set(i, allCircs.get(min));
                allCircs.set(min, temp);
            }
        }
        // return number of circuits suiting to number of stream-connect
        // retries!
        int return_values = sp.connect_retries;
        if (allCircs.size() < return_values) {
            return_values = allCircs.size();
        }
        if ((return_values == 1) && (numberOfExistingCircuits < Config.circuitsMaximumNumber)) {
            // spawn new circuit IN BACKGROUND, unless maximum number of
            // circuits reached
            getLog().debug("FirstNodeHandler.provideSuitableCircuits: spawning circuit to " + sp.hostname + " in background");
            Thread spawnInBackground = new Thread(new CircuitLauncher(sp));
            spawnInBackground.setName("FirstNodeHandler.provideSuitableCircuits");
            spawnInBackground.start();
        } else if ((return_values == 0) && (numberOfExistingCircuits < Config.circuitsMaximumNumber)) {
            // spawn new circuit, unless maximum number of circuits reached
            getLog().debug("FirstNodeHandler.provideSuitableCircuits: spawning circuit to " + sp.hostname);
            Circuit single = createCircuit(sp);
            if (single != null) {
                return_values = 1;
                allCircs.add(single);
            }
        }
        // copy values
        Circuit[] results = new Circuit[return_values];
        for (int i = 0; i < return_values; ++i) {
            results[i] = allCircs.get(i);
            getLog().debug("FirstNodeHandler.provideSuitableCircuits: Choose Circuit ranking " + results[i].ranking + ":" + results[i]);
        }
        return results;
    }

    /**
     * anonymously resolve a hostname.
     * 
     * @param host
     *            the hostname
     * @return the resolved hostname
     * @throws TimeoutException
     * @throws TorException
     */
    public InetAddress lookup(String host) throws IOException, TorException, TimeoutException {
        Object o = internalResolve(host);
        if (o instanceof InetAddress) {
            return (InetAddress) o;
        } else {
            return null;
        }
    }

    /**
     * anonymously do a reverse look-up
     * 
     * @param addr
     *            the inet-address to be resolved
     * @return the hostname
     * @throws TimeoutException
     * @throws TorException
     */
    public String reverseLookup(InetAddress addr) throws IOException, TorException, TimeoutException {
        // build address (works only for IPv4!)
        byte[] a = addr.getAddress();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 4; ++i) {
            sb.append((a[3 - i]) & 0xff);
            sb.append('.');
        }
        sb.append("in-addr.arpa");
        // resolve address
        Object o = internalResolve(sb.toString());
        if (o instanceof String) {
            return (String) o;
        } else {
            return null;
        }
    }

    /**
     * internal function to use the tor-resolve-functionality
     * 
     * @param query
     *            a hostname to be resolved, or for a reverse lookup:
     *            A.B.C.D.in-addr.arpa
     * @return either an InetAddress (normal query), or a String
     *         (reverse-DNS-lookup)
     * @throws TimeoutException
     * @throws TorException
     */
    private Object internalResolve(String query) throws IOException, TorException, TimeoutException {
        // try to resolv query over all existing circuits
        // so iterate over all TLS-Connections
        for (Circuit circuit : getCurrentCircuits()) {
            try {
                if (circuit.established) {
                    // if an answer is given, we're satisfied
                    ResolveStream rs = new ResolveStream(circuit);
                    Object o = rs.resolve(query);
                    rs.close();
                    return o;
                }
            } catch (Exception e) {
                // in case of error, do nothing, but retry with the next
                // circuit
            }
        }
        // if no circuit could give an answer (possibly there was no
        // established circuit?)
        // build a new circuit and ask this one to resolve the query

        ResolveStream rs = new ResolveStream(createCircuit());
        Object o = rs.resolve(query);
        rs.close();
        return o;
    }

    private Circuit createCircuit() throws IOException {
        return createCircuit(new TCPStreamProperties());
    }

    private Circuit createCircuit(TCPStreamProperties tcpStreamProperties) throws IOException {
        Server[] route = createNewRoute(tcpStreamProperties);
        Server entryServer = route[0];
        Circuit retVal = null;
        try {
            if (!connectionMap.containsKey(entryServer.getFingerprint())) {
                connectionMap.put(entryServer.getFingerprint(), new ServerConnection(route[0]));
            }
            ServerConnection connection = connectionMap.get(entryServer.getFingerprint());
            retVal = new Circuit(connection, route, tcpStreamProperties);
        } catch (TorException e) {
            throw new IOException(e);
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (TimeoutException e) {
            throw new IOException(e);
        }
        return retVal;
    }

    /**
     * get a factory for TOR sockets.
     * 
     * @return default factory for TOR sockets.
     */
    public SocketFactory getSocketFactory() {
        return this;
    }

    /**
     * makes a connection to a remote service
     * 
     * @param sp
     *            hostname, port to connect to and other stuff
     * @return some socket-thing
     * @throws CryptoException
     * @throws TimeoutException
     */
    public TCPStream proxyConnect(TCPStreamProperties sp) throws IOException {
        // Tor.log.logGeneral(Logger.VERBOSE, "Tor: Trying to connect to " +
        // sp.hostname);

        if (sp.hostname == null) {
            throw new IOException("Tor: no hostname is provided");
        }


        Circuit[] cs = provideSuitableCircuits(sp);
        if (Config.veryAggressiveStreamBuilding) {

            for (int j = 0; j < cs.length; ++j) {
                // start N asynchronous stream building threads
                try {
                    StreamThread[] streamThreads = new StreamThread[cs.length];
                    for (int i = 0; i < cs.length; ++i) {
                        streamThreads[i] = new StreamThread(cs[i], sp);
                    }
                    // wait for the first stream to return
                    int chosenStream = -1;
                    int waitingCounter = Config.queueTimeoutStreamBuildup * 1000 / 10;
                    while ((chosenStream < 0) && (waitingCounter >= 0)) {
                        boolean atLeastOneAlive = false;
                        for (int i = 0; (i < cs.length) && (chosenStream < 0); ++i) {
                            if (!streamThreads[i].isAlive()) {
                                if ((streamThreads[i].stream != null) && (streamThreads[i].stream.established)) {
                                    chosenStream = i;
                                }
                            } else {
                                atLeastOneAlive = true;
                            }
                        }
                        if (!atLeastOneAlive) {
                            break;
                        }
                        ThreadUtil.safeSleep(10);
                        --waitingCounter;
                    }
                    // return one and close others
                    if (chosenStream >= 0) {
                        TCPStream returnValue = streamThreads[chosenStream].stream;
                        new ClosingThread(streamThreads, chosenStream);
                        return returnValue;
                    }
                } catch (Exception e) {
                    getLog().warn("Tor.connect(): " + e.getMessage());
                    return null;
                }
            }

        } else {
            // build serial N streams, stop if successful
            for (int i = 0; i < cs.length; ++i) {
                try {
                    return new TCPStream(cs[i], sp);
                } catch (IOException e) {
                    getLog().warn("Tor.connect: IOException " + e.getMessage());
                } catch (TorException e) {
                    getLog().warn("Tor.connect: TorException " + e.getMessage());
                }
            }
        }

        throw new IOException("Tor.connect: unable to connect to " + sp.hostname + ":" + sp.port + " after " + sp.connect_retries + " retries");
    }

    /**
     * shut down everything
     * 
     * @param force
     *            set to true, if everything shall go fast. For graceful end,
     *            set to false
     */
    public void close(boolean force) {
        getLog().info("TorJava ist closing down");
        executor.shutdown();
        // shut down connections
        for (ServerConnection t : getCurrentConnections()) {
            t.close(force);
        }
    }

    /** synonym for close(false); */
    public void close() {
        close(false);
    }

    private class CircuitLauncher implements Runnable {
        TCPStreamProperties sp;

        public CircuitLauncher(TCPStreamProperties sp) {
            this.sp = sp;
        }

        public void run() {
            try {
                createCircuit(sp);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public Socket createSocket() throws SocketException {
        return new TorSocket();
    }

    @Override
    public Socket createSocket(InetAddress address, int port) throws IOException {
        return new TorSocket(address, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return new TorSocket(address, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return new TorSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
        return new TorSocket(host, port, localAddress, localPort);
    }

    public Directory getDirectory() {
        return directory;
    }

}
