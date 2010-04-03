package net.sf.onioncoffee;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.commons.collections15.CollectionUtils;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.StatusLine;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.codecs.HttpResponseParser;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicLineParser;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.saintandreas.nio.BufferingSocketHandler;
import org.saintandreas.nio.IOProcessor;
import org.saintandreas.util.Cache;
import org.saintandreas.util.SimpleFileCache;

public class RefreshableDirectory extends Directory {
    private static final int MAX_CONCURRENT_REQUESTS = 20;
    private static final long MAX_SERVER_LOCKOUT_TIME = 10 * 60 * 1000;
    private static final int MAX_REQUEST_TRIES = 10;
    private static final String CONSENSUS_KEY = "consensus";
    private static final String SERVER_KEY_PREFIX = "/servers";

    private final ExecutorService executor;
    private final Selector selector;
    private final Cache<String, String> dataCache = new SimpleFileCache(Config.getConfigDirFile());
    
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
        return SERVER_KEY_PREFIX + getPrefixPath(server.getFingerprint(), 4) + server.getKey();
    }

    private enum RequestType {
        Server, Consensus
    }

    private enum ServerFaultType {
        Exception, Timeout, BadResponse
    }



    public RefreshableDirectory(ExecutorService executor) {
        String consensus = dataCache.getCachedItem(CONSENSUS_KEY);
        if (consensus != null && Directory.consensusValid(consensus)) {
            parseConsensus(consensus);
        } 

        this.executor = executor;
        try {
            this.selector = SelectorProvider.provider().openSelector();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.executor.submit(new RefreshThread());
    }

    private class RefreshThread implements Runnable {
        private final LinkedList<DirectoryRequest> pendingRequests = new LinkedList<DirectoryRequest>();
        private final List<DirectoryConnection> dirConns = new LinkedList<DirectoryConnection>();
        private final Map<String, ServerFault> serverFaults = new HashMap<String, ServerFault>();
        private final Set<String> goodServers = new HashSet<String>();
        private final Set<String> invalidServers = new HashSet<String>();
        private final HttpParams params = new BasicHttpParams();

        private class ServerFault {
            public final ServerFaultType type;
            public final Object faultObject;
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
            public final Server server;
            public boolean finished = false;
            public String result;
            public int tries = 0;
            public LinkedList<DirectoryConnection> servicingConnections = new LinkedList<DirectoryConnection>();

            public DirectoryRequest(Server server) {
                this.server = server;
                this.type = RequestType.Server;
            }

            public void grabResult(SessionInputBuffer inputBuffer) {
                ByteBuffer output = ByteBuffer.allocate(inputBuffer.length());
                inputBuffer.read(output);
                result = new String(output.array());
                finished = true;
            }

            public void finish() throws IOException {
                if (type == RequestType.Consensus) {
                    dataCache.cacheItem(CONSENSUS_KEY, result);
                    parseConsensus(result);
                } else {
                    dataCache.cacheItem(getServerKey(server), result);
                    if (!parseServer(server, result)) {
                        invalidServers.add(server.getFingerprint());
                    }
                }
            }
            
            public DirectoryRequest() {
                this.server = null;
                this.type = RequestType.Consensus;
            }

            public long lastConnectionAge() {
                return servicingConnections.isEmpty() ? Long.MAX_VALUE : servicingConnections.peekLast().age();
            }

            public String url() {
                String retVal = null;
                if (type == RequestType.Consensus) {
                    retVal = "/tor/status-vote/current/consensus";
                } else if (type == RequestType.Server) {
                    retVal = "/tor/server/d/" + server.getDigest();
                }
                return retVal;
            }
        }


        private class DirectoryConnection extends BufferingSocketHandler {
            public final Server server;
            public final SocketChannel sc;
            public DirectoryRequest currentRequest;
            public final long initTime = System.currentTimeMillis();
            public final SessionInputBuffer inputBuffer = new SessionInputBufferImpl(8192, 1024, params);

            public DirectoryConnection(Server targetServer, DirectoryRequest request) throws IOException {
                this.timeoutValue = 1000 * 10;
                this.server = targetServer;
                sc = SocketChannel.open();
                sc.configureBlocking(false);
                this.desiredMask = SelectionKey.OP_CONNECT;
                sc.register(selector, SelectionKey.OP_CONNECT, this);
                sc.socket().setSoTimeout(5 * 1000);
                sc.connect(server.getDirAddress());
                currentRequest = request;
                currentRequest.servicingConnections.add(this);
            }

            public long age() {
                return System.currentTimeMillis() - initTime;
            }

            @Override
            public void onConnect(SelectionKey sk) throws IOException {
                super.onConnect(sk);
                if (currentRequest.finished) {
                    synchronized (pendingRequests) {
                        for (DirectoryRequest request : pendingRequests) {
                            if (!request.finished) {
                                currentRequest = request;
                                break;
                            }
                        }
                    }
                }
//                getLog().debug("Requesting " + server.getHostname() + ":" + server.getDirPort() + currentRequest.url() );
                write("GET " + currentRequest.url() + " HTTP/1.1\r\n\r\n");
            }

            public void onRead(SelectionKey sk) throws IOException {
                int read = inputBuffer.fill(sc);
                while (0 != read && -1 != read) {
                    lastActivityTime = System.currentTimeMillis();
                    read = inputBuffer.fill(sc);
                }
                if (-1 == read) {
                    sk.cancel();
                    onEof();
                }
            }

            @SuppressWarnings("unused")
            protected void onEof() throws IOException {
                dirConns.remove(this);
                try {
                    BasicLineParser lineParser = new BasicLineParser();
                    HttpResponseParser parser = new HttpResponseParser(inputBuffer, lineParser, new DefaultHttpResponseFactory(), params);
                    BasicHttpResponse message = (BasicHttpResponse) parser.parse();
                    StatusLine status = message.getStatusLine();
                    if (status.getStatusCode() != 200) {
                        ++currentRequest.tries;
                        serverFaults.put(server.getFingerprint(), new ServerFault(ServerFaultType.BadResponse, this, status));
                     } else {
                         goodServers.add(server.fingerprint);
                        if (currentRequest.finished) {
                            return;
                        }
                        synchronized (pendingRequests) {
                            pendingRequests.remove(currentRequest);
                        }
                        HttpMessage response = parser.parse();
                        currentRequest.grabResult(inputBuffer);
                        currentRequest.finish();
                    }
                    sc.close();
                } catch (HttpException e) {
                    throw new IOException(e);
                }
            }

            @Override
            public SocketChannel getSocketChannel() {
                return sc;
            }

            @Override
            protected Selector getSelector() {
                return selector;
            }

            @Override
            public void onException(IOException e) {
                super.onException(e);
                dirConns.remove(this);
                ++currentRequest.tries;
                serverFaults.put(server.getFingerprint(), new ServerFault(ServerFaultType.BadResponse, this, e));
            }
        }


        private Server findConnectableServer() {
            Server retVal = null;
            Set<String> currentConns = new HashSet<String>();
            for (DirectoryConnection dirConn : dirConns) {
                currentConns.add(dirConn.server.fingerprint);
            }
            
            Set<String> goodConns = new HashSet<String>(goodServers);
            goodConns.removeAll(currentConns);
            // use a previously reliable connection
            if (!goodConns.isEmpty()) {
                retVal = getServers().get(goodConns.iterator().next());
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
            if (getFreshUntil().isBeforeNow() && getConsensusAge() > 1000 * 60 * 30) {
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
                    pendingServers.remove(req.server.getFingerprint());
                }
            }

            for (Iterator<String> itr = pendingServers.iterator(); itr.hasNext(); ) {
                String s = itr.next();
                Server server = getServers().get(s);
                String descriptor = dataCache.getCachedItem(getServerKey(server));
                if (descriptor != null && parseServer(server, descriptor)) {
                    itr.remove();
                } 
            }
            pendingServers.removeAll(invalidServers);
            for (String s : pendingServers) {
                pendingRequests.add(new DirectoryRequest(getServers().get(s)));
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
                    IOProcessor.reactor(selector);
                    cleanExceptionMap();
                    Thread.sleep(100);
                } catch (Exception e) {
//                    LogFactory.getLog(RefreshThread.class).error("Failure", e);
                }
            }
        }

    }
}
