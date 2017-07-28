package org.riyafa;

import com.google.common.cache.LoadingCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    private static Map<String, LoadingCache<String, CachableResponse>> cacheMap = new ConcurrentHashMap<>();

    public static LoadingCache<String, CachableResponse> get(String id) {
        return cacheMap.get(id);
    }

    public static void put(String id, LoadingCache<String, CachableResponse> cache) {
        cacheMap.put(id, cache);
    }

    public static void clean() {
        cacheMap.clear();
    }

    public static boolean hasId(String id) {
        return cacheMap.containsKey(id);
    }
}
