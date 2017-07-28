package org.riyafa;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This object holds the cached response and the related properties of the cache per request and will be stored in to
 * the cache. This holds the response envelope together with the request hash and the response hash. Apart from that
 * this object holds the refresh time of the cache and the timeout period. This implements the Serializable interface to
 * support the clustered caching.
 *
 * @see java.io.Serializable
 */
public class CachableResponse implements Serializable {
    /**
     * This holds the reference to the response envelope. To support clustered caching, the response envelope has to be
     * in a serializable format, but because the SOAPEnvelope or OMElement is not serializable response envelope has
     * kept as its serilaized format as a byte[]
     */
    private byte[] responsePayload = null;

    /**
     * This holds the hash value of the request payload which is calculated form the specified DigestGenerator, and is
     * used to index the cached response
     */
    private String requestHash;

    /**
     * This holds the time at which this particular cached response expires, in the standard java system time format
     * (i.e. System.currentTimeMillis())
     */
    private long expireTimeMillis;

    /**
     * This holds the timeout period of the cached response which will be used at the next refresh time in order to
     * generate the expireTimeMillis
     */
    private long timeout;

    /**
     * This holds the HTTP Header Properties of the response.
     */
    private Map<String, Object> headerProperties;

    private String statusCode;
    private String statusReason;
    private boolean json;

    /**
     * This method checks whether this cached response is expired or not
     *
     * @return boolean true if expired and false if not
     */
    public boolean isExpired() {
        return timeout <= 0 || expireTimeMillis < System.currentTimeMillis();
    }

    /**
     * This method will refresh the cached response stored in this object. If further explained this method will set the
     * response envelope and the response hash to null and set the new refresh time as timeout + current time
     * <p>
     * This is how an expired response is brought back to life
     *
     * @param timeout The period for which this object is reincarnated
     */
    public void reincarnate(long timeout) {
        if (!isExpired()) {
            throw new IllegalStateException("Unexpired Cached Responses cannot be reincarnated");
        }
        responsePayload = null;
        headerProperties = null;
        expireTimeMillis = System.currentTimeMillis() + timeout * 1000;
        setTimeout(timeout);
    }


    public void clean() {
        responsePayload = null;
        headerProperties = null;
    }

    /**
     * This method gives the cached response envelope as a String
     *
     * @return String representing the cached response payload
     */
    public byte[] getResponsePayload() {
        return responsePayload;
    }

    /**
     * This method sets the response payload to the cache as a byte array
     *
     * @param responsePayload - response payload to be stored in to the cache as a String
     */
    public void setResponsePayload(byte[] responsePayload) {
        this.responsePayload = responsePayload;
    }

    /**
     * This method gives the hash value of the request payload stored in the cache
     *
     * @return String hash of the request payload
     */
    public String getRequestHash() {
        return requestHash;
    }

    /**
     * This method sets the hash of the request to the cache
     *
     * @param requestHash - hash of the request payload to be set as an String
     */
    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    /**
     * This method gives the expireTimeMillis in the standard java system time format
     *
     * @return long refresh time in the standard java system time format
     */
    public long getExpireTimeMillis() {
        return expireTimeMillis;
    }

    /**
     * This method sets the refresh time to the cached response
     *
     * @param expireTimeMillis - refresh time in the standard java system time format
     */
    public void setExpireTimeMillis(long expireTimeMillis) {
        this.expireTimeMillis = expireTimeMillis;
    }

    /**
     * This method gives the timeout period in milliseconds
     *
     * @return timeout in milliseconds
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * This method sets the timeout period as milliseconds
     *
     * @param timeout - millisecond timeout period to be set
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * This method gives the HTTP Header Properties of the response
     *
     * @return Map<String, Object> representing the HTTP Header Properties
     */
    public Map<String, Object> getHeaderProperties() {
        return headerProperties;
    }

    /**
     * This method sets the HTTP Header Properties of the response
     *
     * @param headerProperties HTTP Header Properties to be stored in to cache as a map
     */
    public void setHeaderProperties(Map<String, Object> headerProperties) {
        this.headerProperties = headerProperties;
    }

    public boolean isJson() {
        return json;
    }

    public void setJson(boolean json) {
        this.json = json;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }
}
