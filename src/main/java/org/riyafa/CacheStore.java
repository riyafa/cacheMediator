package org.riyafa;

public class CacheStore {
    /**
     * The maximum size of the messages to be cached. This is specified in bytes.
     */
    private int maxMessageSize = -1;

    /**
     * This is used to define the logic used by the mediator to evaluate the hash values of incoming messages.
     */
    private String responseCodes = CachingConstants.RESPONSE_CODE;

    /**
     * The protocol type used in caching
     */
    private String protocolType = CachingConstants.HTTP_PROTOCOL_TYPE;

    public String getProtocolType() {
        return protocolType;
    }

    /**
     * This method sets protocolType of the messages.
     *
     * @param protocolType protocol type of the messages.
     */
    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    public String getResponseCodes() {
        return responseCodes;
    }

    /**
     * This method sets the response codes that needs to be cached.
     *
     * @param responseCodes the response codes to be cached in regex form.
     */
    public void setResponseCodes(String responseCodes) {
        this.responseCodes = responseCodes;
    }

    /**
     * This method gives the maximum size of the messages to be cached in bytes.
     *
     * @return maximum size of the messages to be cached in bytes.
     */
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * This method sets the maximum size of the messages to be cached in bytes.
     *
     * @param maxMessageSize maximum size of the messages to be set in bytes.
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }
}
