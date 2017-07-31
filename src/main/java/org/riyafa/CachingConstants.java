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

import org.apache.synapse.config.xml.XMLConfigConstants;

import javax.xml.namespace.QName;

/**
 * Created by riyafa on 7/13/17.
 */
public class CachingConstants {
    /**
     * Default DigestGenerator for the caching impl
     */
    public static final DigestGenerator DEFAULT_HASH_GENERATOR = new HttpRequestHashGenerator();

    /**
     * The GET method value
     */
    public static final String HTTP_METHOD_GET = "GET";

    /**
     * QName of the cache mediator which will be used by the module
     */
    public static final QName CACHE_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                  CachingConstants.CACHE_LOCAL_NAME);

    /**
     * Local name of the cache mediator which will be used by the module
     */
    public static final String CACHE_LOCAL_NAME = "eICache";

    /**
     * This holds the default timeout of the mediator cache
     */
    public static final long DEFAULT_TIMEOUT = 5000;

    /**
     * The HTTP protocol
     */
    public static final String HTTP_PROTOCOL_TYPE = "HTTP";

    /**
     * The regex for the 2xx response code
     */
    public static final String RESPONSE_CODE = "2[0-9][0-9]";

    /**
     * String key to store the the request hash in the message contetx
     */
    public static final String REQUEST_HASH = "requestHash";

    /**
     * String key to store the cached response in the message context
     */
    public static final String CACHED_OBJECT = "CachableResponse";

    /**
     * The the header that would be used to return the hashed value to invalidate this value
     */
    public static final String CACHE_KEY = "cacheKey";

    /**
     * This value can be specified for the headersToExcludeInHash property to avoid all the headers when caching
     */
    public static final String EXCLUDE_ALL_VAL = "exclude-all";

    /**
     * Default cache invalidation time
     */
    public static final Integer CACHE_INVALIDATION_TIME = 24 * 3600;
}
