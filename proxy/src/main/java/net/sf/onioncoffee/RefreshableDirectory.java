package net.sf.onioncoffee;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.zip.DataFormatException;

import net.sf.onioncoffee.common.Cache;
import net.sf.onioncoffee.common.Encoding;
import net.sf.onioncoffee.common.SimpleFileCache;

import org.apache.commons.collections15.CollectionUtils;
import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;

public class RefreshableDirectory extends Directory {
    private static final int MAX_CONCURRENT_REQUESTS = 20;
    private static final long MAX_SERVER_LOCKOUT_TIME = 10 * 60 * 1000;
    private static final int MAX_REQUEST_TRIES = 10;
    private static final String CONSENSUS_KEY = "consensus";
    private static final String SERVER_KEY_PREFIX = "/servers";

    private final ExecutorService executor;
    private final Cache<String, String> dataCache = new SimpleFileCache(Config.getConfigDirFile());
    private final HttpClient client = new HttpClient();

    public static String getPrefixPath(String name, int length) {
        StringBuilder buffer = new StringBuilder();
        length = Math.min(name.length(), length);
        buffer.append("/");
        for (int i = 0; i < length; ++i) {
            buffer.append(name.charAt(i));
            buffer.append("/");
        }
        return buffer.toString();
    }

    public static String getServerKey(Server server) {
        return getServerKey(server.getKey());
    }

    public static String getServerKey(String server) {
        return SERVER_KEY_PREFIX + getPrefixPath(server, 4) + server;
    }

    private enum RequestType {
        Server, Consensus
    }

    private enum ServerFaultType {
        Exception, Timeout, BadResponse
    }

    public RefreshableDirectory(ExecutorService executor) {
        String consensus = dataCache.getCachedItem(CONSENSUS_KEY);
        if (consensus != null && Directory.consensusFresh(consensus)) {
            parseConsensus(consensus);
        }

        client.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        client.setMaxConnectionsPerAddress(200); // max 200 concurrent
                                                 // connections to every address
        client.setThreadPool(new QueuedThreadPool(2)); // max 250 threads
        client.setTimeout(30000); // 30 seconds timeout; if no server reply, the
                                  // request expires
        try {
            client.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.executor = executor;
        this.executor.submit(new RefreshThread());
    }

    private class RefreshThread implements Runnable {
        private final LinkedList<DirectoryRequest> pendingRequests = new LinkedList<DirectoryRequest>();
        private final List<DirectoryConnection> dirConns = new LinkedList<DirectoryConnection>();
        private final Map<String, ServerFault> serverFaults = new HashMap<String, ServerFault>();
        private final Set<String> goodServers = new HashSet<String>();
        private final Set<String> invalidServers = new HashSet<String>();

        private class ServerFault {
            @SuppressWarnings("unused")
            public final ServerFaultType type;
            @SuppressWarnings("unused")
            public final Object faultObject;
            @SuppressWarnings("unused")
            public final DirectoryConnection conn;
            public final Long initTime = System.currentTimeMillis();

            public ServerFault(ServerFaultType type, DirectoryConnection conn, Object faultObject) {
                this.conn = conn;
                this.type = type;
                this.faultObject = faultObject;
            }

            public long age() {
                return System.currentTimeMillis() - initTime;
            }
        }

        private class DirectoryRequest {
            public final RequestType type;
            public final List<String> servers;
            public boolean finished = false;
            public String result;
            public int tries = 0;
            public LinkedList<DirectoryConnection> servicingConnections = new LinkedList<DirectoryConnection>();

            public DirectoryRequest(Collection<String> servers) {
                this.servers = new ArrayList<String>(servers);
                this.type = RequestType.Server;
            }

            public void finish() throws IOException {
                if (type == RequestType.Consensus) {
                    dataCache.cacheItem(CONSENSUS_KEY, result);
                    parseConsensus(result);
                } else {
                    Files.write(result, new File("foo.txt"), Charsets.UTF_8);
                    System.out.println();
                    // dataCache.cacheItem(getServerKey(server), result);
                    // if (!parseServer(server, result)) {
                    // invalidServers.add(server.getFingerprint());
                    // }
                }
            }

            public DirectoryRequest() {
                this.servers = null;
                this.type = RequestType.Consensus;
            }

            public long lastConnectionAge() {
                return servicingConnections.isEmpty() ? Long.MAX_VALUE : servicingConnections.peekLast().age();
            }

            public String url() {
                String retVal = null;
                if (type == RequestType.Consensus) {
                    retVal = "/tor/status-vote/current/consensus.z";
                } else if (type == RequestType.Server) {
                    retVal = "/tor/server/fp/" + Joiner.on("+").join(servers) + ".z";
                }

                return retVal;
            }
        }

        private class DirectoryConnection extends ContentExchange {
            public final Server server;
            public DirectoryRequest currentRequest;
            public final long initTime = System.currentTimeMillis();

            public DirectoryConnection(Server targetServer, DirectoryRequest request) throws IOException {
                this.server = targetServer;
                currentRequest = request;
                synchronized (currentRequest.servicingConnections) {
                    currentRequest.servicingConnections.add(this);
                }
                setAddress(new Address(targetServer.getHostname(), targetServer.getDirPort()));
                setURI(currentRequest.url());
                client.send(this);
            }

            public long age() {
                return System.currentTimeMillis() - initTime;
            }

            protected void onResponseComplete() throws IOException {
                int status = getResponseStatus();
                if (status == 200) {
                    dirConns.remove(this);
                    goodServers.add(server.fingerprint);
                    if (currentRequest.finished) {
                        return;
                    }
                    synchronized (pendingRequests) {
                        pendingRequests.remove(currentRequest);
                    }
                    byte[] compressedBytes = this.getResponseContentBytes();
                    byte[] decompressedBytes;
                    try {
                        decompressedBytes = Encoding.inflate(compressedBytes);
                    } catch (DataFormatException e) {
                        throw new IOException("Cannot parse response");
                    }
                    currentRequest.result = new String(decompressedBytes);
                    currentRequest.finished = true;
                    currentRequest.finish();
                } else {
                    fail(status);
                }
            }

            private byte[] decompress(byte[] compressedBytes) {
                // TODO Auto-generated method stub
                return null;
            }

            protected void fail(Object cause) {
                synchronized (currentRequest.servicingConnections) {
                    currentRequest.servicingConnections.remove(this);
                }
                dirConns.remove(this);
                ++currentRequest.tries;
                serverFaults.put(server.getFingerprint(), new ServerFault(ServerFaultType.BadResponse, this, cause));
            }

            protected void onConnectionFailed(Throwable x) {
                super.onConnectionFailed(x);
                fail(x);
            }

            protected void onException(Throwable x) {
                super.onException(x);
                fail(x);
            }

            protected void onExpire() {
                super.onExpire();
                fail("timeout");
            }
        }

        private Server findConnectableServer() {
            Server retVal = null;
            Set<String> currentConns = new HashSet<String>();
            for (DirectoryConnection dirConn : dirConns) {
                currentConns.add(dirConn.server.fingerprint);
            }

            List<String> goodConns = new ArrayList<String>(goodServers);
            goodConns.removeAll(currentConns);
            // use a previously reliable connection
            if (!goodConns.isEmpty()) {
                String selectedFingerprint = goodConns.get(new Random().nextInt(goodConns.size()));
                retVal = getServers().get(selectedFingerprint);
            }

            if (retVal == null) {
                Map<String, Server> serverMap = getValidServerMap();
                CollectionUtils.filter(serverMap.values(), new Server.RunningPredicate());
                CollectionUtils.filter(serverMap.values(), new Server.ValidDirPortPredicate());
                serverMap.keySet().removeAll(serverFaults.keySet());
                serverMap.keySet().removeAll(currentConns);
                if (!serverMap.isEmpty()) {
                    retVal = new ArrayList<Server>(serverMap.values()).get(new Random().nextInt(serverMap.keySet().size()));
                }
            }

            if (retVal == null) {
                Map<String, Server> serverMap = Config.trustedServers;
                serverMap.keySet().removeAll(currentConns);
                if (!serverMap.isEmpty()) {
                    retVal = new ArrayList<Server>(Config.trustedServers.values()).get(new Random().nextInt(Config.trustedServers.keySet().size()));
                }
            }

            return retVal;
        }

        private void openConnections() throws IOException {
            for (DirectoryRequest req : pendingRequests) {
                if (req.lastConnectionAge() > 1000 * 2 && dirConns.size() < MAX_CONCURRENT_REQUESTS && req.tries < MAX_REQUEST_TRIES) {
                    Server targetServer = findConnectableServer();
                    if (targetServer != null) {
                        getLog().debug("Adding new dir connection to server " + targetServer.getHostname() + ":" + targetServer.getDirPort());
                        dirConns.add(new DirectoryConnection(targetServer, req));
                    }
                }
            }
        }

        private void cleanExceptionMap() {
            serverFaults.keySet().retainAll(getValidServers());
            for (Iterator<Map.Entry<String, ServerFault>> itr = serverFaults.entrySet().iterator(); itr.hasNext();) {
                Map.Entry<String, ServerFault> entry = itr.next();
                if (entry.getValue().age() > MAX_SERVER_LOCKOUT_TIME) {
                    itr.remove();
                }
            }
        }

        private void refreshConsensus() {
            if (getValidUntil().isBeforeNow() || (getFreshUntil().isBeforeNow() && getConsensusAge() > 1000 * 60 * 30)) {
                boolean activeRequest = false;
                for (DirectoryRequest req : pendingRequests) {
                    if (RequestType.Consensus == req.type) {
                        activeRequest = true;
                        break;
                    }
                }
                if (!activeRequest) {
                    pendingRequests.add(new DirectoryRequest());
                }
            }
        }

        private void refreshServers() {
            Set<String> pendingServers = getInvalidServers();
            for (DirectoryRequest req : pendingRequests) {
                if (req.type == RequestType.Server) {
                    pendingServers.removeAll(req.servers);
                }
            }

            for (Iterator<String> itr = pendingServers.iterator(); itr.hasNext();) {
                String s = itr.next();
                Server server = getServers().get(s);
                String descriptor = dataCache.getCachedItem(getServerKey(server));
                if (descriptor != null && parseServer(server, descriptor)) {
                    itr.remove();
                }
            }
            pendingServers.removeAll(invalidServers);
            List<String> pendingServersList = new ArrayList<String>(pendingServers);
            while (!pendingServersList.isEmpty()) {
                int end = Math.min(pendingServersList.size(), 96);
                List<String> subList = pendingServersList.subList(0, end);
                pendingRequests.add(new DirectoryRequest(new ArrayList<String>(subList)));
                subList.clear();
            }
        }

        @Override
        public void run() {
            Thread.currentThread().setName("Directory Refresh Thread");
            while (!executor.isShutdown()) {
                try {
                    refreshConsensus();
                    refreshServers();
                    openConnections();
                    cleanExceptionMap();
                    Thread.sleep(100);
                } catch (Exception e) {
                    // LogFactory.getLog(RefreshThread.class).error("Failure",
                    // e);
                }
            }
        }

    }
}
