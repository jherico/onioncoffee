package net.sf.onioncoffee.common;
import java.io.File;
import java.io.IOException;

import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Files;


public class SimpleFileCache implements Cache<String, String>{
    private final File rootFolder;
    
    public SimpleFileCache(File root ) {
        this.rootFolder = root;
    }

    protected File getTarget(String key, boolean create) {
        File retVal = new File(rootFolder, key);
        if (create) {
            retVal.getParentFile().mkdirs();
        }
        return retVal; 
    }

    public void cacheItem(String key, String item) throws IOException {
        Files.write(key, getTarget(key, true), Charsets.UTF_8);
    }

    public String getCachedItem(String key) {
        File target = getTarget(key,false);
        if (target.exists()) {
            try {
                return Files.toString(target, Charsets.UTF_8);
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).warn("unable to read cache file " + target, e);
            }
        }
        return null;
    }

}
