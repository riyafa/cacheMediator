/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.riyafa;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * There are two instances of the Cache mediator that is Collector and Finder. Since we get all the parameters in the
 * Finder instance we need to pass some of these parameters to the Collector instance since these parameters are used in
 * the Collector instance.
 */
public class CacheStoreManager {
    /**
     * Maps the id with the relevant CacheStore
     */
    private static Map<String, CacheStore> cacheMap = new ConcurrentHashMap<>();

    /**
     * @param id the id of the mediator
     * @return the relevant CacheStore of the mediator
     */
    public static CacheStore get(String id) {
        CacheStore cacheStore = cacheMap.get(id);
        if (cacheStore == null) {
            cacheStore = new CacheStore();
            cacheMap.put(id, cacheStore);
        }
        return cacheStore;
    }

    /**
     * Clears the CacheStoreManager
     */
    static void clean() {
        cacheMap.clear();
    }//MAKE  package private
}
