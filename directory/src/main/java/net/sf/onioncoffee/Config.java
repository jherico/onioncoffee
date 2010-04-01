/**
 * OnionCoffee - Anonymous Communication through TOR Network
 * Copyright (C) 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
package net.sf.onioncoffee;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTimeZone;
import org.saintandreas.util.StringUtil;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * used for global configuration - stuff
 * 
 * @author Lexi
 * @author Michael Koellejan
 * @author Andriy Panchenko
 */
public class Config {
    static Log LOG = LogFactory.getLog(Config.class);
    // Static final/utility variables
    public static final String TORJAVA_VERSION_STRING = "TorJava 0.1a";
    public final static Map<String, Server> trustedServers = new HashMap<String, Server>();
    
    // functionality
    private static int cacheMaxAgeSeconds = 24 * 3600;

    // QoS-parameters
    static int retriesConnect = 5;

    static int reconnectCircuit = 3;
    static int retriesStreamBuildup = 5;

    static int defaultIdleCircuits = 11;

    public static int queueTimeoutCircuit = 40;
    static int queueTimeoutResolve = 20;
    public static int queueTimeoutStreamBuildup = 10;

    static int circuitClosesOnFailures = 3;
    public static int circuitsMaximumNumber = 30;

    static float rankingTransferPerServerUpdate = 0.95f; // 0..1

    static boolean veryAggressiveStreamBuilding = false; // this is a truly
                                                         // asocial way of
                                                         // building streams!!

    // directory parameters
    public static int intervalDirectoryV1Refresh = 30; // in minutes longer, since it
                                                // updates the complete
                                                // directory at once
    public static int intervalDirectoryRefresh = 2; // in minutes
    static int dirV2ReadMaxNumberOfDescriptorsFirstTime = 180; // set to <=0 to
                                                               // read all
    static int dirV2ReadMaxNumberOfDescriptorsPerUpdate = 80; // set to <=0 to
                                                              // read all
    static int dirV2ReadMaxNumberOfThreads = 50;
    static int dirV2ReloadRetries = 3; // per descriptor
    static int dirV2ReloadTimeout = 10; // in seconds
    static int dirV2DescriptorsPerBatch = 1;
    public static int dirV2NetworkStatusRequestTimeOut = 60000; // millisecond

    // QoS-parameter, see updateRanking in Circuit.java
    static final int CIRCUIT_ESTABLISHMENT_TIME_IMPACT = 5;

    // Security parameters
    public static int streamsPerCircuit = 50;
    static float rankingIndexEffect = 0.9f; // see Server.getRefinedRankingIndex

    // Path length
    public static int route_min_length = 3;
    static int route_max_length = 3;

    // Don't establish any circuits until a certain part of
    // the descriptors of running routers is present
    public static float min_percentage = 1;
    // Wait at most until this number of descriptors is known
    public static int min_descriptors = route_min_length;

    // True if there shouldn't be two class C addresses on the route
    static boolean route_uniq_class_c = true;
    // True if there should be at most one router from one country
    // (or block of countries) on the path
    static boolean route_uniq_country = false;
    // Allow a single node to be present in multiple circuits
    static int maxCircuitsPerNode = 3;

    final static Set<String> avoidedCountries = new HashSet<String>();
    final static Set<String> avoidedNodes= new HashSet<String>();

    // Time intervals of gui updates in ms
    public static int guiUpdateIntervalMilliSeconds = 3000;
    public static boolean guiDisplayNodeNames = false;
    public static String guiCountryOfUser = "EU";

    // Filenames
    private static final String TOR_CACHE_FILENAME = "cached-directory";
    static final String TOR_STABLE_DIR_FILENAME = "data/directory-stable";
    static final String TOR_GEOIPCITY_FILENAME = "data/GeoLiteCity.dat";

    // PROXY-Stuff
    // FIXME: is public (and in fact: complete class is public) only to be
    // accessed from HTTPConnection
    public final static Vector<String> setFilteredHeaders = new Vector<String>();
    public final static Vector<String[]> setReplaceHeaders = new Vector<String[]>();

    public static int portWWWProxy = 8080;
    public static int portSocksProxy = 1080;


    public static void load() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        DateTimeZone.setDefault(DateTimeZone.UTC);
        addDefaultTrustedServers();
    }

    /**
     * should be called in case there are no trusted servers in the config-file
     * given
     */
    @SuppressWarnings("unchecked")
    private static void addDefaultTrustedServers() {
      ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:net/sf/onioncoffee/default-trusted-servers.xml");
      List<Server> server = context.getBean( "default-trusted-servers", List.class);
      for (Server s : server) {
          trustedServers.put(s.getKey(), s);
      }
    }

    private static String replaceSpaceWithSpaceRegExp(String regexp) {
        return regexp.replaceAll(" ", "\\\\s+");
    }

    private static int parseInt(String config, String name, int myDefault) {
        int x = Integer.parseInt(StringUtil.parseStringByRE(config, "^\\s*" + replaceSpaceWithSpaceRegExp(name) + "\\s+(\\d+)", Integer.toString(myDefault)));
        LOG.trace("Config.parseInt: Parsed '" + name + "' as '" + x + "'");
        return x;
    }

    private static String writeInt(String name, int value) {
        return name + " " + value + "\n";
    }

    private static String writeFloat(String name, float value) {
        return name + " " + value + "\n";
    }

    private static float parseFloat(String config, String name, float myDefault, float lower, float upper) {
        float x = Float.parseFloat(StringUtil.parseStringByRE(config, "^\\s*" + replaceSpaceWithSpaceRegExp(name) + "\\s+([0-9.]+)", Float.toString(myDefault)));
        if (x < lower) {
            x = lower;
        }
        if (x > upper) {
            x = upper;
        }
        LOG.trace("Config.parseFloat: Parsed '" + name + "' as '" + x + "'");
        return x;
    }

    private static String parseString(String config, String name, String myDefault) {
        String x = StringUtil.parseStringByRE(config, "^\\s*" + replaceSpaceWithSpaceRegExp(name) + "\\s+(\\S.*?)$", myDefault);
        LOG.trace("Config.parseString: Parsed '" + name + "' as '" + x + "'");
        return x;
    }

    private static String writeString(String name, String value) {
        return name + " " + value + "\n";
    }

    private static boolean parseBoolean(String config, String name, boolean myDefault) {
        String mydef = "false";
        if (myDefault) {
            mydef = "true";
        }
        String x = StringUtil.parseStringByRE(config, "^\\s*" + replaceSpaceWithSpaceRegExp(name) + "\\s+(\\S.*?)$", mydef).trim();
        boolean ret = false;
        if (x.equals("1") || x.equalsIgnoreCase("true") || x.equalsIgnoreCase("yes")) {
            ret = true;
        }
        LOG.trace("Config.parseBoolean: Parsed '" + name + "' as '" + ret + "'");
        return ret;
    }

    private static String writeBoolean(String name, boolean value) {
        if (value == true) {
            return name + " " + "true" + "\n";
        } else {
            return name + " " + "false" + "\n";
        }
    }

    
    @SuppressWarnings("unused")
    private static void readFromConfig(String filename) {
        try {
            String config = "";
            if (filename != null) {
                DataInputStream sin = new DataInputStream(new FileInputStream(new File(filename)));
                // DataInputStream sin = new
                // DataInputStream(ClassLoader.getSystemResourceAsStream(filename));
                config = StringUtil.read(sin);
                LOG.trace("Config.readFromConfig(): " + config);
            }
            // security parameters
            streamsPerCircuit = parseInt(config, "StreamsPerCircuit", streamsPerCircuit);
            rankingIndexEffect = parseFloat(config, "RankingIndexEffect", rankingIndexEffect, 0, 1);
            route_min_length = parseInt(config, "RouteMinLength", route_min_length);
            route_max_length = parseInt(config, "RouteMaxLength", route_max_length);
            min_percentage = parseFloat(config, "MinPercentage", min_percentage, 0, 1);
            min_descriptors = parseInt(config, "MinDescriptors", min_descriptors);
            route_uniq_class_c = parseBoolean(config, "RouteUniqClassC", route_uniq_class_c);
            route_uniq_country = parseBoolean(config, "RouteUniqCountry", route_uniq_country);
            maxCircuitsPerNode = parseInt(config, "AllowNodeMultipleCircuits", maxCircuitsPerNode);
            // Avoid Countries
            Pattern p = Pattern.compile("^\\s*AvoidCountry\\s+(.*?)$", Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
            Matcher m = p.matcher(config);
            while (m.find()) {
                LOG.debug("Config.readConfig: will avoid country: " + m.group(1));
                avoidedCountries.add(m.group(1));
            }
            // Avoid Nodes
            p = Pattern.compile("^\\s*AvoidNode\\s+(.*?)$", Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
            m = p.matcher(config);
            while (m.find()) {
                LOG.debug("Config.readConfig: will avoid node: " + m.group(1));
                avoidedNodes.add(m.group(1));
            }
            // functionality
            cacheMaxAgeSeconds = parseInt(config, "cacheMaxAgeSeconds", cacheMaxAgeSeconds);
            guiUpdateIntervalMilliSeconds = parseInt(config, "guiUpdateIntervalMilliSeconds", guiUpdateIntervalMilliSeconds);
            guiDisplayNodeNames = parseBoolean(config, "guiDisplayNodeNames", guiDisplayNodeNames);
            guiCountryOfUser = parseString(config, "guiCountryOfUser", guiCountryOfUser);
            portWWWProxy = parseInt(config, "portwwwproxy", portWWWProxy);
            portSocksProxy = parseInt(config, "portsocksproxy", portSocksProxy);
            // QoS parameters
            retriesConnect = parseInt(config, "RetriesConnect", retriesConnect);
            retriesStreamBuildup = parseInt(config, "RetriesStreamBuildup", retriesStreamBuildup);
            reconnectCircuit = parseInt(config, "ReconnectCircuit", reconnectCircuit);
            defaultIdleCircuits = parseInt(config, "DefaultIdleCircuits", defaultIdleCircuits);

            queueTimeoutCircuit = parseInt(config, "QueueTimeoutCircuit", queueTimeoutCircuit);
            queueTimeoutResolve = parseInt(config, "QueueTimeoutResolve", queueTimeoutResolve);
            queueTimeoutStreamBuildup = parseInt(config, "QueueTimeoutStreamBuildup", queueTimeoutStreamBuildup);

            rankingTransferPerServerUpdate = parseFloat(config, "RankingTransferPerServerUpdate", rankingTransferPerServerUpdate, 0, 1);

            circuitClosesOnFailures = parseInt(config, "CircuitClosesOnFailures", circuitClosesOnFailures);
            circuitsMaximumNumber = parseInt(config, "circuitsMaximumNumber", circuitsMaximumNumber);

            veryAggressiveStreamBuilding = parseBoolean(config, "veryAggressiveStreamBuilding", veryAggressiveStreamBuilding);
            // directory parameters
            intervalDirectoryV1Refresh = parseInt(config, "DirectoryV1Refresh", intervalDirectoryV1Refresh);
            intervalDirectoryRefresh = parseInt(config, "DirectoryRefresh", intervalDirectoryRefresh);
            dirV2ReadMaxNumberOfDescriptorsFirstTime = parseInt(config, "MaxNumberOfDescriptorsFirstTime", dirV2ReadMaxNumberOfDescriptorsFirstTime);
            dirV2ReadMaxNumberOfDescriptorsPerUpdate = parseInt(config, "MaxNumberOfDescriptorsPerUpdate", dirV2ReadMaxNumberOfDescriptorsPerUpdate);
            dirV2ReloadRetries = parseInt(config, "dirV2ReloadRetries", dirV2ReloadRetries);
            dirV2ReloadTimeout = parseInt(config, "dirV2ReloadTimeout", dirV2ReloadTimeout);
            dirV2DescriptorsPerBatch = parseInt(config, "dirV2DescriptorsPerBatch", dirV2DescriptorsPerBatch);
            // Filtering HTTP-headers
            p = Pattern.compile("^\\s*FilterHeader\\s+(.*?)$", Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
            m = p.matcher(config);
            while (m.find()) {
                LOG.trace("Config.readConfig: will filter '" + m.group(1) + "' HTTP-headers");
                setFilteredHeaders.add(m.group(1));
            }
            // Filtering HTTP-headers
            p = Pattern.compile("^\\s*ReplaceHeader\\s+(\\S+)\\s+(.*?)$", Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
            m = p.matcher(config);
            while (m.find()) {
                LOG.trace("Config.readConfig: will replace '" + m.group(1) + "' HTTP-headers with " + m.group(2));
                String[] set = new String[2];
                set[0] = m.group(1);
                set[1] = m.group(2);
                setReplaceHeaders.add(set);
            }
        } catch (IOException e) {
            LOG.warn("Config.readFromConfig(): Warning: " + e.getMessage());
        }
    }

    /** used to store some new values to a file */
    @SuppressWarnings("unused")
    private static void writeToFile(String filename) {
        if (filename == null) {
            return;
        }
        try {
            StringBuffer config = new StringBuffer();

            LOG.debug("Config.writeToFile(): " + config);
            // Write variable config information here

            // security parameters
            config.append(writeInt("StreamsPerCircuit", streamsPerCircuit));
            config.append(writeFloat("RankingIndexEffect", rankingIndexEffect));
            config.append(writeInt("RouteMinLength", route_min_length));
            config.append(writeInt("RouteMaxLength", route_max_length));
            config.append(writeFloat("MinPercentage", min_percentage));
            config.append(writeInt("MinDescriptors", min_descriptors));
            config.append(writeBoolean("RouteUniqClassC", route_uniq_class_c));
            config.append(writeBoolean("RouteUniqCountry", route_uniq_country));
            config.append(writeInt("AllowNodeMultipleCircuits", maxCircuitsPerNode));

            // Avoided countries
            Iterator<String> it = avoidedCountries.iterator();
            while (it.hasNext()) {
                String countryName = (String) it.next();
                config.append(writeString("AvoidCountry", countryName));
                LOG.debug("Config.writeToFile: will avoid country " + countryName);
            }
            // Avoided nodes
            it = avoidedNodes.iterator();
            while (it.hasNext()) {
                String nodeName = (String) it.next();
                config.append(writeString("AvoidNode", nodeName));
                LOG.debug("Config.writeToFile: will avoid node " + nodeName);
            }
            // Functionality
            config.append(writeInt("cacheMaxAgeSeconds", cacheMaxAgeSeconds));
            config.append(writeInt("guiUpdateIntervalMilliSeconds", guiUpdateIntervalMilliSeconds));
            config.append(writeBoolean("guiDisplayNodeNames", guiDisplayNodeNames));
            config.append(writeString("guiCountryOfUser", guiCountryOfUser));
            config.append(writeInt("portwwwproxy", portWWWProxy));
            config.append(writeInt("portsocksproxy", portSocksProxy));

            // QoS parameters
            config.append(writeInt("RetriesConnect", retriesConnect));
            config.append(writeInt("RetriesStreamBuildup", retriesStreamBuildup));
            config.append(writeInt("ReconnectCircuit", reconnectCircuit));
            config.append(writeInt("DefaultIdleCircuits", defaultIdleCircuits));

            config.append(writeInt("QueueTimeoutCircuit", queueTimeoutCircuit));
            config.append(writeInt("QueueTimeoutResolve", queueTimeoutResolve));
            config.append(writeInt("QueueTimeoutStreamBuildup", queueTimeoutStreamBuildup));

            config.append(writeInt("CircuitClosesOnFailures", circuitClosesOnFailures));
            config.append(writeInt("circuitsMaximumNumber", circuitsMaximumNumber));

            config.append(writeBoolean("veryAggressiveStreamBuilding", veryAggressiveStreamBuilding));

            // FIXME: Check if this really works
            config.append(writeFloat("RankingTransferPerServerUpdate", rankingTransferPerServerUpdate));
            // directory parameters
            config.append(writeInt("DirectoryV1Refresh", intervalDirectoryV1Refresh));
            config.append(writeInt("DirectoryRefresh", intervalDirectoryRefresh));
            config.append(writeInt("MaxNumberOfDescriptorsFirstTime", dirV2ReadMaxNumberOfDescriptorsFirstTime));
            config.append(writeInt("MaxNumberOfDescriptorsPerUpdate", dirV2ReadMaxNumberOfDescriptorsPerUpdate));
            config.append(writeInt("dirV2ReloadRetries", dirV2ReloadRetries));
            config.append(writeInt("dirV2ReloadTimeout", dirV2ReloadTimeout));
            config.append(writeInt("dirV2DescriptorsPerBatch", dirV2DescriptorsPerBatch));

            // Filtering HTTP-headers
            for (String headerName : setFilteredHeaders) {
                config.append(writeString("FilterHeader", headerName));
                LOG.trace("Config.writeToFile: will filter '" + headerName + "' HTTP-headers");
            }

            // Replace HTTP-headers
            for (String[] set : setReplaceHeaders) {
                config.append(writeString("ReplaceHeader", set[0] + " " + set[1]));
                LOG.trace("Config.writeToFile: will filter '" + set[0] + "' HTTP-headers");
            }

            FileWriter writer = new FileWriter(new File(filename));
            writer.write(config.toString());
            writer.close();

        } catch (IOException e) {
            LOG.warn("Config.writeToFile(): Warning: " + e.getMessage());
        }

    }

    public static String getConfigDir() {
        return System.getProperty("user.home") + System.getProperty("file.separator") + ".jtor" + System.getProperty("file.separator");
    }

    public static File getConfigDirFile() {
        return new File(getConfigDir());
    }

    public static File getConfigDirFile(String subdirectory) {
        return new File(getConfigDirFile(), subdirectory);
    }

    /** removed, since it is no more used */
    /*
     * static String getTempDir() { String os = operatingSystem(); if
     * (os.equals("Linux")) return "/tmp/"; return getConfigDir(); }
     */

    public static String getCacheFilename() {
        return getConfigDir() + TOR_CACHE_FILENAME;
    }

    static String operatingSystem() {
        return System.getProperty("os.name");
    }
}

// vim: et
