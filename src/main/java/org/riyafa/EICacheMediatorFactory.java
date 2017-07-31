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

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.SequenceMediatorFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;

import java.util.Iterator;
import java.util.Properties;
import javax.xml.namespace.QName;

public class EICacheMediatorFactory extends AbstractMediatorFactory {

    /**
     * QName of the ID of cache configuration
     */
    private static final QName ATT_ID = new QName("id");

    /**
     * QName of the timeout
     */
    private static final QName ATT_TIMEOUT = new QName("timeout");

    /**
     * QName of the collector
     */
    private static final QName ATT_COLLECTOR = new QName("collector");

    /**
     * QName of the maximum message size
     */
    private static final QName ATT_MAX_MSG_SIZE = new QName("maxMessageSize");

    /**
     * QName of the onCacheHit mediator sequence reference
     */
    private static final QName ON_CACHE_HIT_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "onCacheHit");

    /**
     * QName of the mediator sequence
     */
    private static final QName ATT_SEQUENCE = new QName("sequence");

    /**
     * QName of the continueExecution
     */
    private static final QName ATT_CONTINUE_EXECUTION = new QName("continueExecution");

    /**
     * QName of the onCacheHit mediator sequence reference
     */
    private static final QName PROTOCOL_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "protocol");

    /**
     * QName of the protocol type
     */
    private static final QName ATT_TYPE = new QName("type");

    /**
     * QName of the hTTPMethodToCache
     */
    private static final QName HTTP_METHODS_TO_CACHE_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "methods");

    /**
     * QName of the headersToExcludeInHash
     */
    private static final QName HEADERS_TO_EXCLUDE_IN_HASH_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                                        "headersToExcludeInHash");

    /**
     * QName of the response codes to include when hashing
     */
    private static final QName RESPONSE_CODES_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "responseCodes");

    /**
     * QName of the digest generator
     */
    private static final QName HASH_GENERATOR_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "hashGenerator");

    /**
     * QName of the cache implementation
     */
    private static final QName IMPLEMENTATION_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "implementation");

    /**
     * QName of the maximum message size
     */
    private static final QName ATT_SIZE = new QName("maxSize");

    /**
     * Stores certain parameters that are common to both Collector and Finder instances of the cache mediator
     */
    private CacheStore cacheStore;


    /**
     * {@inheritDoc}
     */
    protected Mediator createSpecificMediator(OMElement elem, Properties properties) {
        if (!CachingConstants.CACHE_Q.equals(elem.getQName())) {
            handleException(
                    "Unable to create the cache mediator. Unexpected element as the cache mediator configuration");
        }

        EICacheMediator cache = new EICacheMediator();
        OMAttribute idAttr = elem.getAttribute(ATT_ID);
        String id;
        if (idAttr != null && (id = idAttr.getAttributeValue()) != null) {
            cacheStore = CacheStoreManager.get(id);
            cache.setId(idAttr.getAttributeValue());
        } else {
            cacheStore = CacheStoreManager.get("");
        }

        OMAttribute collectorAttr = elem.getAttribute(ATT_COLLECTOR);
        if (collectorAttr != null && collectorAttr.getAttributeValue() != null &&
                "true".equals(collectorAttr.getAttributeValue())) {

            cache.setCollector(true);
        } else {
            cache.setCollector(false);

            OMAttribute timeoutAttr = elem.getAttribute(ATT_TIMEOUT);
            if (timeoutAttr != null && timeoutAttr.getAttributeValue() != null) {
                cache.setTimeout(Long.parseLong(timeoutAttr.getAttributeValue()));
            } else {
                cache.setTimeout(CachingConstants.DEFAULT_TIMEOUT);
            }

            OMAttribute maxMessageSizeAttr = elem.getAttribute(ATT_MAX_MSG_SIZE);
            if (maxMessageSizeAttr != null && maxMessageSizeAttr.getAttributeValue() != null) {
                cacheStore.setMaxMessageSize(Integer.parseInt(maxMessageSizeAttr.getAttributeValue()));
            }

            for (Iterator itr = elem.getChildrenWithName(PROTOCOL_Q); itr.hasNext(); ) {
                OMElement protocolElem = (OMElement) itr.next();
                OMAttribute typeAttr = protocolElem.getAttribute(ATT_TYPE);

                if (typeAttr != null &&
                        typeAttr.getAttributeValue() != null) {
                    String protocolType = typeAttr.getAttributeValue().toUpperCase();
                    cacheStore.setProtocolType(protocolType);
                    if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(protocolType)) {
                        OMElement methodElem = protocolElem.getFirstChildWithName(HTTP_METHODS_TO_CACHE_Q);
                        if (methodElem != null) {
                            String[] methods = methodElem.getText().split(",");
                            if (!"".equals(methods[0])) {
                                for (int i = 0; i < methods.length; i++) {
                                    methods[i] = methods[i].trim();
                                    if (!("POST".equals(methods[i]) || "GET".equals(methods[i]) || "HEAD".equals(
                                            methods[i]) || "PUT".equals(methods[i]) || "DELETE".equals(methods[i]) ||
                                            "OPTIONS".equals(methods[i]) || "CONNECT".equals(methods[i]))) {
                                        handleException("Unexpected method type: " + methods[i]);
                                    }
                                }
                                cache.setHTTPMethodsToCache(methods);
                            }
                        }

                        OMElement headersToExcludeInHash = protocolElem.getFirstChildWithName(
                                HEADERS_TO_EXCLUDE_IN_HASH_Q);
                        if (headersToExcludeInHash != null) {
                            String[] headers = headersToExcludeInHash.getText().split(",");
                            for (int i = 0; i < headers.length; i++) {
                                headers[i] = headers[i].trim();
                            }
                            cache.setHeadersToExcludeInHash(headers);
                        } else {
                            cache.setHeadersToExcludeInHash("");
                        }

                        OMElement responseElem = protocolElem.getFirstChildWithName(RESPONSE_CODES_Q);
                        if (responseElem != null) {
                            String responses = responseElem.getText();
                            if (!"".equals(responses) && responses != null) {
                                cacheStore.setResponseCodes(responses);
                            }
                        }

                        OMElement hashGeneratorElem = protocolElem.getFirstChildWithName(HASH_GENERATOR_Q);
                        if (hashGeneratorElem != null) {
                            try {
                                String className = hashGeneratorElem.getText();
                                if (!"".equals(className)) {
                                    Class generator = Class.forName(className);
                                    Object o = generator.newInstance();
                                    if (o instanceof DigestGenerator) {
                                        cache.setDigestGenerator((DigestGenerator) o);
                                    } else {
                                        handleException("Specified class for the hashGenerator is not a " +
                                                                "DigestGenerator. It *must* implement " +
                                                                "org.wso2.carbon.mediator.cache.digest" +
                                                                ".DigestGenerator interface");
                                    }
                                }
                            } catch (ClassNotFoundException e) {
                                handleException("Unable to load the hash generator class", e);
                            } catch (IllegalAccessException e) {
                                handleException("Unable to access the hash generator class", e);
                            } catch (InstantiationException e) {
                                handleException("Unable to instantiate the hash generator class", e);
                            }
                        }

                    }
                }
            }

            String val;
            OMElement onCacheHitElem = elem.getFirstChildWithName(ON_CACHE_HIT_Q);
            if (onCacheHitElem != null) {
                OMAttribute continueExecution = onCacheHitElem.getAttribute(ATT_CONTINUE_EXECUTION);
                if (continueExecution != null && continueExecution.getAttributeValue() != null) {
                    val = continueExecution.getAttributeValue();
                    if ("true".equals(val)) {
                        cache.setContinueExecution(true);
                    } else if ("false".equals(val)) {
                        cache.setContinueExecution(false);
                    } else {
                        handleException("Unexpected value for continueExecution: " + val);
                    }
                }
                OMAttribute sequenceAttr = onCacheHitElem.getAttribute(ATT_SEQUENCE);
                if (sequenceAttr != null && sequenceAttr.getAttributeValue() != null) {
                    cache.setOnCacheHitRef(sequenceAttr.getAttributeValue());
                } else if (onCacheHitElem.getFirstElement() != null) {
                    cache.setOnCacheHitSequence(new SequenceMediatorFactory()
                                                        .createAnonymousSequence(onCacheHitElem, properties));
                }
            }

            for (Iterator itr = elem.getChildrenWithName(IMPLEMENTATION_Q); itr.hasNext(); ) {
                OMElement implElem = (OMElement) itr.next();
                OMAttribute sizeAttr = implElem.getAttribute(ATT_SIZE);
                if (sizeAttr != null &&
                        sizeAttr.getAttributeValue() != null) {
                    cache.setInMemoryCacheSize(Integer.parseInt(sizeAttr.getAttributeValue()));
                }
            }
        }
        cache.setCacheStore(cacheStore);
        return cache;
    }

    /**
     * {@inheritDoc}
     */
    public QName getTagQName() {
        return CachingConstants.CACHE_Q;
    }
}