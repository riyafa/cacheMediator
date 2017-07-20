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
     * The POST method value
     */
    public static final String HTTP_METHOD_POST = "POST";

    /**
     * Default cache size (in-memory)
     */
    public static final int DEFAULT_CACHE_SIZE = 1000;

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
    public static final long DEFAULT_TIMEOUT = 5000L;

    /**
     * The HTTP protocol
     */
    public static final String HTTP_PROTOCOL_TYPE = "HTTP";

    /**
     * The regex for the 2xx response code
     */
    public static final String RESPONSE_CODE = "2[0-9][0-9]";
}
