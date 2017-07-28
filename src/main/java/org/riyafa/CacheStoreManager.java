package org.riyafa;

import com.google.common.cache.LoadingCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheStoreManager {
    private static Map<String, CacheStore> cacheMap = new ConcurrentHashMap<>();

    public static CacheStore get(String id) {
        CacheStore cacheStore = cacheMap.get(id);
        if (cacheStore == null) {
            cacheStore = new CacheStore();
            cacheMap.put(id, cacheStore);
        }
        return cacheStore;
    }

    public static void clean() {
        cacheMap.clear();
    }

    public static boolean hasId(String id) {
        return cacheMap.containsKey(id);
    }
}
