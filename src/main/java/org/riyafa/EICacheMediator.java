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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.state.Replicator;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.OperationContext;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.continuation.ContinuationStackManager;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.debug.constructs.EnclosedInlinedSequence;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.util.FixedByteArrayOutputStream;
import org.apache.synapse.util.MessageHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamException;

/**
 * Created by riyafa on 7/10/17.
 */
public class EICacheMediator extends AbstractMediator implements ManagedLifecycle, EnclosedInlinedSequence {


    /**
     * The value of json content type as it appears in HTTP Content-Type header
     */
    private final static String JSON_CONTENT_TYPE = "application/json";
    /**
     * Cache configuration ID.
     */
    private String id = "";

    /**
     * The time duration for which the cache is kept.
     */
    private long timeout = CachingConstants.DEFAULT_TIMEOUT;

    /**
     * This specifies whether the mediator should be in the incoming path (to check the request) or in the outgoing path
     * (to cache the response).
     */
    private boolean collector = false;

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
    private DigestGenerator digestGenerator = CachingConstants.DEFAULT_HASH_GENERATOR;

    /**
     * The size of the messages to be cached in memory. If this is 0 then no disk cache, and if there is no size
     * specified in the config  factory will asign a default value to enable disk based caching.
     */
    private int inMemoryCacheSize = -1;

    /**
     * Variable to represent 'NO_ENTITY_BODY' property of synapse
     */
    private static final String NO_ENTITY_BODY = "NO_ENTITY_BODY";

    /**
     * String variable representing SOAP Header element
     */
    private static final String HEADER = "Header";


    /**
     * A store that stores values that are common to both the collector and finder
     */
    private CacheStore cacheStore;

    /**
     * {@inheritDoc}
     */
    public void init(SynapseEnvironment se) {
        if (onCacheHitSequence != null) {
            onCacheHitSequence.init(se);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void destroy() {
        if (onCacheHitSequence != null) {
            onCacheHitSequence.destroy();
        }
        CacheManager.clean();
    }

    /**
     * {@inheritDoc}
     */
    public boolean mediate(MessageContext synCtx) {
        if (synCtx.getEnvironment().isDebuggerEnabled()) {
            if (super.divertMediationRoute(synCtx)) {
                return true;
            }
        }
        SynapseLog synLog = getLog(synCtx);
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : Cache mediator");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }

        ConfigurationContext cfgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext().getConfigurationContext();

        if (cfgCtx == null) {
            handleException("Unable to perform caching,  ConfigurationContext cannot be found", synCtx);
            return false; // never executes.. but keeps IDE happy
        }
        boolean result = true;
        try {
            if (synCtx.isResponse()) {
                processResponseMessage(synCtx, cfgCtx, synLog);
            } else {
                result = processRequestMessage(synCtx, synLog);
            }
        } catch (ClusteringFault clusteringFault) {
            synLog.traceOrDebug("Unable to replicate Cache mediator state among the cluster");
        } catch (ExecutionException e) {
            synLog.traceOrDebug("Unable to get the response");

        }
        return result;
    }

    /**
     * Caches the CachableResponse object with currently available attributes against the requestHash in
     * LoadingCache<String, CachableResponse>. Called in the load method of CachingBuilder
     *
     * @param requestHash the request hash that has already been computed
     */
    private CachableResponse cacheNewResponse(String requestHash) {
        CachableResponse response = new CachableResponse();
        response.setRequestHash(requestHash);
        response.setTimeout(timeout);
        return response;
    }

    /**
     * Processes a request message through the cache mediator. Generates the request hash and looks up for a hit, if
     * found; then the specified named or anonymous sequence is executed or marks this message as a response and sends
     * back directly to client.
     *
     * @param synCtx incoming request message
     * @param synLog the Synapse log to use
     * @return should this mediator terminate further processing?
     * @throws ClusteringFault if there is an error in replicating the cfgCtx
     */
    private boolean processRequestMessage(MessageContext synCtx, SynapseLog synLog)
            throws ExecutionException, ClusteringFault {
        if (collector) {
            handleException("Request messages cannot be handled in a collector cache", synCtx);
        }
        OperationContext opCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext().getOperationContext();
        org.apache.axis2.context.MessageContext msgCtx =
                ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        String requestHash = null;
        try {
            requestHash = digestGenerator.getDigest(((Axis2MessageContext) synCtx).getAxis2MessageContext(),
                                                    CachingConstants.HTTP_PROTOCOL_TYPE
                                                            .equals(cacheStore.getProtocolType()) &&
                                                            msgCtx.isDoingREST() &&
                                                            getHTTPMethodsToCache().length == 1 &&
                                                            getHTTPMethodsToCache()[0]
                                                                    .equals(CachingConstants.HTTP_METHOD_GET),
                                                    getHeadersToExcludeInHash());
            synCtx.setProperty(CachingConstants.REQUEST_HASH, requestHash);
        } catch (CachingException e) {
            handleException("Error in calculating the hash value of the request", e, synCtx);
        }
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Generated request hash : " + requestHash);
        }
        opCtx.setProperty(CachingConstants.REQUEST_HASH, requestHash);
        CachableResponse cachedResponse = getMediatorCache().get(requestHash);
        opCtx.setProperty(CachingConstants.CACHED_OBJECT, cachedResponse);
        Replicator.replicate(opCtx);
        Map<String, Object> headerProperties;

        if (cachedResponse.getResponsePayload() != null) {
            // get the response from the cache and attach to the context and change the
            // direction of the message
            if (!cachedResponse.isExpired()) {
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Cache-hit for message ID : " + synCtx.getMessageID());
                }
                // mark as a response and replace envelope from cache
                synCtx.setResponse(true);
                try {
                    byte[] payload = cachedResponse.getResponsePayload();
                    if (cachedResponse.isJson()) {
                        OMElement response = JsonUtil.getNewJsonPayload(msgCtx, payload, 0,
                                                                        payload.length, false, false);
                        if (msgCtx.getEnvelope().getBody().getFirstElement() != null) {
                            msgCtx.getEnvelope().getBody().getFirstElement().detach();
                        }
                        msgCtx.getEnvelope().getBody().addChild(response);

                    } else {
                        String replacementValue = new String(payload);

                        OMElement response = AXIOMUtil.stringToOM(replacementValue);

                        if (response != null) {
                            // Set the headers of the message
                            if (response.getFirstElement().getLocalName().contains(HEADER)) {
                                Iterator childElements = msgCtx.getEnvelope().getHeader().getChildElements();
                                while (childElements.hasNext()) {
                                    ((OMElement) childElements.next()).detach();
                                }
                                SOAPEnvelope env = synCtx.getEnvelope();
                                SOAPHeader header = env.getHeader();
                                SOAPFactory fac = (SOAPFactory) env.getOMFactory();

                                Iterator headers = response.getFirstElement().getChildElements();
                                while (headers.hasNext()) {
                                    OMElement soapHeader = (OMElement) headers.next();
                                    SOAPHeaderBlock hb = header.addHeaderBlock(soapHeader.getLocalName(),
                                                                               fac.createOMNamespace(
                                                                                       soapHeader.getNamespace()
                                                                                               .getNamespaceURI(),
                                                                                       soapHeader.getNamespace()
                                                                                               .getPrefix()));
                                    hb.setText(soapHeader.getText());
                                }
                                response.getFirstElement().detach();
                            }
                            // Set the body of the message
                            if (msgCtx.getEnvelope().getBody().getFirstElement() != null) {
                                msgCtx.getEnvelope().getBody().getFirstElement().detach();
                            }
                            msgCtx.getEnvelope().getBody().addChild(response.getFirstElement().getFirstElement());

                        }

                    }
                } catch (XMLStreamException | AxisFault e) {
                    handleException("Error creating response OM from cache : " + id, synCtx);
                }

                if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(cacheStore.getProtocolType())) {
                    msgCtx.setProperty(NhttpConstants.HTTP_SC, Integer.parseInt(cachedResponse.getStatusCode()));
                    msgCtx.setProperty(PassThroughConstants.HTTP_SC_DESC, cachedResponse.getStatusReason());
                }
                if (msgCtx.isDoingREST()) {

                    if ((headerProperties = cachedResponse.getHeaderProperties()) != null) {

                        msgCtx.removeProperty(NO_ENTITY_BODY);
                        msgCtx.removeProperty(Constants.Configuration.CONTENT_TYPE);
                        msgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS,
                                           headerProperties);
                        msgCtx.setProperty(Constants.Configuration.MESSAGE_TYPE,
                                           headerProperties.get(Constants.Configuration.MESSAGE_TYPE));
                    }
                }


                // take specified action on cache hit
                if (onCacheHitSequence != null) {
                    // if there is an onCacheHit use that for the mediation
                    synLog.traceOrDebug("Delegating message to the onCachingHit "
                                                + "Anonymous sequence");
                    ContinuationStackManager.addReliantContinuationState(synCtx, 0, getMediatorPosition());
                    if (onCacheHitSequence.mediate(synCtx)) {
                        ContinuationStackManager.removeReliantContinuationState(synCtx);
                    }

                } else if (onCacheHitRef != null) {
                    if (synLog.isTraceOrDebugEnabled()) {
                        synLog.traceOrDebug("Delegating message to the onCachingHit " +
                                                    "sequence : " + onCacheHitRef);
                    }
                    ContinuationStackManager.updateSeqContinuationState(synCtx, getMediatorPosition());
                    synCtx.getSequence(onCacheHitRef).mediate(synCtx);

                } else {

                    if (synLog.isTraceOrDebugEnabled()) {
                        synLog.traceOrDebug("Request message " + synCtx.getMessageID() +
                                                    " was served from the cache");
                    }
                    // send the response back if there is not onCacheHit is specified
                    synCtx.setTo(null);

                }
                //Todo if needed
                if (!continueExecution) {
                    Axis2Sender.sendBack(synCtx);
                    return false;
                } else {

                }
            } else {
                cachedResponse.reincarnate(timeout);
                getMediatorCache().put(requestHash, cachedResponse);
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Existing cached response has expired. Resetting cache element");
                }

                opCtx.setProperty(CachingConstants.CACHED_OBJECT, cachedResponse);
                Replicator.replicate(opCtx);
            }
        }
        return true;
    }

    /**
     * Process a response message through this cache mediator. This finds the Cache used, and updates it for the
     * corresponding request hash
     *
     * @param synLog the Synapse log to use
     * @param synCtx the current message (response)
     * @param cfgCtx the abstract context in which the cache will be kept
     * @throws ClusteringFault is there is an error in replicating the cfgCtx
     */
    @SuppressWarnings("unchecked")
    private void processResponseMessage(MessageContext synCtx, ConfigurationContext cfgCtx, SynapseLog synLog)
            throws ClusteringFault {
        if (!collector) {
            handleException("Response messages cannot be handled in a non collector cache", synCtx);
        }
        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        OperationContext operationContext = msgCtx.getOperationContext();
        CachableResponse response = (CachableResponse) operationContext.getProperty(CachingConstants.CACHED_OBJECT);

        boolean toCache = false;
        if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(cacheStore.getProtocolType())) {
            String statusCode = msgCtx.getProperty(NhttpConstants.HTTP_SC).toString();
            // Create a Pattern object
            Pattern r = Pattern.compile(cacheStore.getResponseCodes());
            // Now create matcher object.
            Matcher m = r.matcher(statusCode);
            if (m.matches()) {
                toCache = true;
                if (response != null) {
                    response.setStatusCode(statusCode);
                    response.setStatusReason(msgCtx.getProperty(PassThroughConstants.HTTP_SC_DESC).toString());
                }
            } else {
                toCache = false;
            }
        } else {
            toCache = true;
        }

        if (toCache) {
            if (response != null) {
                String contentType = ((String) msgCtx.getProperty(Constants.Configuration.CONTENT_TYPE)).split(";")[0];

                if (contentType.equals(JSON_CONTENT_TYPE)) {
                    byte[] responsePayload = JsonUtil.jsonPayloadToByteArray(msgCtx);
                    if (cacheStore.getMaxMessageSize() > -1) {
                        if (responsePayload.length > cacheStore.getMaxMessageSize()) {
                            synLog.traceOrDebug(
                                    "Message size exceeds the upper bound for caching, request will not be cached");
                            return;
                        }
                    }

                    response.setResponsePayload(responsePayload);
                    response.setJson(true);
                } else {
                    if (cacheStore.getMaxMessageSize() > -1) {
                        FixedByteArrayOutputStream fbaos = new FixedByteArrayOutputStream(
                                cacheStore.getMaxMessageSize());
                        try {
                            MessageHelper.cloneSOAPEnvelope(synCtx.getEnvelope()).serialize(fbaos);
                        } catch (XMLStreamException e) {
                            handleException("Error in checking the message size", e, synCtx);
                        } catch (SynapseException syne) {
                            synLog.traceOrDebug(
                                    "Message size exceeds the upper bound for caching, request will not be cached");
                            return;
                        } finally {
                            try {
                                fbaos.close();
                            } catch (IOException e) {
                                handleException("Error occurred while closing the FixedByteArrayOutputStream ", e,
                                                synCtx);
                            }
                        }
                    }
                    try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
                        synCtx.getEnvelope().serialize(outStream);
                        response.setResponsePayload(outStream.toByteArray());
                        response.setJson(false);
                    } catch (XMLStreamException e) {
                        handleException("Unable to set the response to the Cache", e, synCtx);
                    } catch (IOException e) {
                        handleException("Error occurred while closing the FixedByteArrayOutputStream ", e, synCtx);
                    }
                }

                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Storing the response message into the cache with ID : "
                                                + id + " for request hash : " + response.getRequestHash());
                }
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug(
                            "Storing the response for the message with ID : " + synCtx.getMessageID() + " " +
                                    "with request hash ID : " + response.getRequestHash() + " in the cache");
                }
                if (response.getTimeout() > 0) {
                    response.setExpireTimeMillis(System.currentTimeMillis() + response.getTimeout() * 1000);
                }

                if (msgCtx.isDoingREST()) {
                    Map<String, String> headers =
                            (Map<String, String>) msgCtx.getProperty(
                                    org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                    String messageType = (String) msgCtx.getProperty(Constants.Configuration.MESSAGE_TYPE);
                    Map<String, Object> headerProperties = new HashMap<String, Object>();
                    //Individually copying All TRANSPORT_HEADERS to headerProperties Map instead putting whole
                    //TRANSPORT_HEADERS map as single Key/Value pair to fix hazelcast serialization issue.
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        headerProperties.put(entry.getKey(), entry.getValue());
                    }
                    headerProperties.put(Constants.Configuration.MESSAGE_TYPE, messageType);
                    headerProperties.put(CachingConstants.CACHE_KEY, response.getRequestHash());
                    response.setHeaderProperties(headerProperties);
                    msgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headerProperties);
                }
                getMediatorCache().put(response.getRequestHash(), response);
                // Finally, we may need to replicate the changes in the cache
                Replicator.replicate(cfgCtx);
            } else {
                synLog.auditWarn("A response message without a valid mapping to the " +
                                         "request hash found. Unable to store the response in cache");
            }
        } else {
            response.clean();
            getMediatorCache().put(response.getRequestHash(), response);
            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug(
                        "Received a response status that could not be cached. Hence resetting the cache for this " +
                                "request");
            }
            operationContext.setProperty(CachingConstants.CACHED_OBJECT, response);
            Replicator.replicate(operationContext);
        }

    }

    /**
     * Creates default cache to keep mediator cache
     *
     * @return global cache
     */
    public LoadingCache<String, CachableResponse> getMediatorCache() {
        LoadingCache<String, CachableResponse> cache = CacheManager.get(id);
        if (cache == null) {
            if (inMemoryCacheSize > -1) {
                cache = CacheBuilder.newBuilder().expireAfterWrite(CachingConstants.CACHE_INVALIDATION_TIME,
                                                                   TimeUnit.SECONDS).maximumSize(inMemoryCacheSize)
                        .build(new CacheLoader<String, CachableResponse>() {
                            @Override
                            public CachableResponse load(String requestHash) throws Exception {
                                return cacheNewResponse(requestHash);
                            }
                        });
            } else {
                cache = CacheBuilder.newBuilder().expireAfterWrite(CachingConstants.CACHE_INVALIDATION_TIME,
                                                                   TimeUnit.SECONDS).build(
                        new CacheLoader<String, CachableResponse>() {
                            @Override
                            public CachableResponse load(String requestHash) throws Exception {
                                return cacheNewResponse(requestHash);
                            }
                        });
            }
            CacheManager.put(id, cache);
        }
        return cache;
    }

    /**
     * {@inheritDoc}
     */
    public Mediator getInlineSequence(SynapseConfiguration synCfg, int inlinedSeqIdentifier) {
        if (inlinedSeqIdentifier == 0) {
            if (onCacheHitSequence != null) {
                return onCacheHitSequence;
            } else if (onCacheHitRef != null) {
                return synCfg.getSequence(onCacheHitRef);
            }
        }
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

    /**
     * Sets the store that stores values that are common to both the collector and finder
     */
    public void setCacheStore(CacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }
}
