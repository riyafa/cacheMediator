package org.riyafa;

import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.debug.constructs.EnclosedInlinedSequence;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.base.SequenceMediator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by riyafa on 7/10/17.
 */
public class EICacheMediator extends AbstractMediator implements ManagedLifecycle, EnclosedInlinedSequence {
    /**
     * Cache configuration ID.
     */
    private String id = null;

    /**
     * The time duration for which the cache is kept.
     */
    private long timeout = 0L;

    /**
     * This specifies whether the mediator should be in the incoming path (to check the request) or in the outgoing path
     * (to cache the response).
     */
    private boolean collector = false;

    /**
     * The maximum size of the messages to be cached. This is specified in bytes.
     */
    private int maxMessageSize = 0;

    /**
     * The SequenceMediator to the onCacheHit sequence to be executed when an incoming message is identified as an
     * equivalent to a previously received message based on the value defined for the Hash Generator field.
     */
    private SequenceMediator onCacheHitSequence = null;

    /**
     * The reference to the onCacheHit sequence to be executed when an incoming message is identified as an equivalent
     * to a previously received message based on the value defined for the Hash Generator field.
     */
    private String onCacheHitRef = null;

    /**
     * Specifies whether to continue or not continue execution after mediation
     */
    private boolean continueExecution = false;

    /**
     * The protocol type used in caching
     */
    private String protocolType = CachingConstants.HTTP_PROTOCOL_TYPE;

    /**
     * The http method type that needs to be cached
     */
    private String[] hTTPMethodsToCache = {CachingConstants.HTTP_METHOD_GET};

    /**
     * The headers to exclude when caching
     */
    private String[] headersToExcludeInHash = {""};

    /**
     * This is used to define the logic used by the mediator to evaluate the hash values of incoming messages.
     */
    private String[] responseCodes = {CachingConstants.RESPONSE_CODE};

    /**
     * This is used to define the logic used by the mediator to evaluate the hash values of incoming messages.
     */
    private DigestGenerator digestGenerator = CachingConstants.DEFAULT_HASH_GENERATOR;

    /**
     * The size of the messages to be cached in memory. If this is 0 then no disk cache, and if there is no size
     * specified in the config  factory will asign a default value to enable disk based caching.
     */
    private int inMemoryCacheSize = CachingConstants.DEFAULT_CACHE_SIZE;

    /**
     * This holds whether the global cache already initialized or not.
     */
    private static AtomicBoolean mediatorCacheInit = new AtomicBoolean(false);

    public void init(SynapseEnvironment synapseEnvironment) {

    }

    public void destroy() {

    }

    public boolean mediate(MessageContext synCtx) {
        if (synCtx.getEnvironment().isDebuggerEnabled()) {
            if (super.divertMediationRoute(synCtx)) {
                return true;
            }
        }
        SynapseLog synLog = getLog(synCtx);

        return false;
    }

    public Mediator getInlineSequence(SynapseConfiguration synapseConfiguration, int i) {
        return null;
    }

    /**
     * This methods gives the ID of the cache configuration.
     *
     * @return string cache configuration ID.
     */
    public String getId() {
        return id;
    }

    /**
     * This methods sets the ID of the cache configuration.
     *
     * @param id cache configuration ID to be set.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * This method gives the DigestGenerator to evaluate the hash values of incoming messages.
     *
     * @return DigestGenerator used evaluate hash values.
     */
    public DigestGenerator getDigestGenerator() {
        return digestGenerator;
    }

    /**
     * This method sets the DigestGenerator to evaluate the hash values of incoming messages.
     *
     * @param digestGenerator DigestGenerator to be set to evaluate hash values.
     */
    public void setDigestGenerator(DigestGenerator digestGenerator) {
        this.digestGenerator = digestGenerator;
    }

    /**
     * This method gives the timeout period in milliseconds.
     *
     * @return timeout in milliseconds
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * This method sets the timeout period as milliseconds.
     *
     * @param timeout millisecond timeout period to be set.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * This method gives whether the mediator should be in the incoming path or in the outgoing path as a boolean.
     *
     * @return boolean true if incoming path false if outgoing path.
     */
    public boolean isCollector() {
        return collector;
    }

    /**
     * This method sets whether the mediator should be in the incoming path or in the outgoing path as a boolean.
     *
     * @param collector boolean value to be set as collector.
     */
    public void setCollector(boolean collector) {
        this.collector = collector;
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

    /**
     * This method gives the HTTP method that needs to be cached
     *
     * @return the HTTP method to be cached
     */
    public String[] getHTTPMethodsToCache() {
        return hTTPMethodsToCache;
    }

    /**
     * This sets the HTTP method that needs to be cached
     *
     * @param hTTPMethodToCache the HTTP method to be cached
     */
    public void setHTTPMethodsToCache(String... hTTPMethodToCache) {
        this.hTTPMethodsToCache = hTTPMethodToCache;
    }

    /**
     * This method gives array of headers that would be excluded when hashing.
     *
     * @return array of headers to exclude from hashing
     */
    public String[] getHeadersToExcludeInHash() {
        return headersToExcludeInHash;
    }

    /**
     * This method sets the array of headers that would be excluded when hashing
     *
     * @param headersToExcludeInHash array of headers to exclude from hashing.
     */
    public void setHeadersToExcludeInHash(String... headersToExcludeInHash) {
        this.headersToExcludeInHash = headersToExcludeInHash;
    }

    /**
     * This method gives SequenceMediator to be executed.
     *
     * @return sequence mediator to be executed.
     */
    public SequenceMediator getOnCacheHitSequence() {
        return onCacheHitSequence;
    }

    /**
     * This method sets SequenceMediator to be executed.
     *
     * @param onCacheHitSequence sequence mediator to be set.
     */
    public void setOnCacheHitSequence(SequenceMediator onCacheHitSequence) {
        this.onCacheHitSequence = onCacheHitSequence;
    }

    /**
     * This method gives reference to the onCacheHit sequence to be executed.
     *
     * @return reference to the onCacheHit sequence.
     */
    public String getOnCacheHitRef() {
        return onCacheHitRef;
    }

    /**
     * This method sets reference to the onCacheHit sequence to be executed.
     *
     * @param onCacheHitRef reference to the onCacheHit sequence to be set.
     */
    public void setOnCacheHitRef(String onCacheHitRef) {
        this.onCacheHitRef = onCacheHitRef;
    }

    /**
     * This method gives whether to continue or not continue execution after mediation.
     *
     * @return true if continue execution false if not.
     */
    public boolean isContinueExecution() {
        return continueExecution;
    }

    /**
     * This method sets whether to continue or not continue execution after mediation.
     *
     * @param continueExecution boolean value of continueExecution.
     */
    public void setContinueExecution(boolean continueExecution) {
        this.continueExecution = continueExecution;
    }

    /**
     * This method gives the size of the messages to be cached in memory.
     *
     * @return memory cache size in bytes.
     */
    public int getInMemoryCacheSize() {
        return inMemoryCacheSize;
    }

    /**
     * This method sets the size of the messages to be cached in memory.
     *
     * @param inMemoryCacheSize value(number of bytes) to be set as memory cache size.
     */
    public void setInMemoryCacheSize(int inMemoryCacheSize) {
        this.inMemoryCacheSize = inMemoryCacheSize;
    }


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

    public String[] getResponseCodes() {
        return responseCodes;
    }

    /**
     * This method sets the response codes that needs to be cached.
     *
     * @param responseCodes the response codes to be cached in regex form.
     */
    public void setResponseCodes(String[] responseCodes) {
        this.responseCodes = responseCodes;
    }
}
