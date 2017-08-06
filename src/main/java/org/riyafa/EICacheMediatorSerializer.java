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

import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorSerializer;
import org.apache.synapse.config.xml.MediatorSerializer;
import org.apache.synapse.config.xml.MediatorSerializerFinder;

import java.util.List;

/**
 * Created by riyafa on 7/10/17.
 */
public class EICacheMediatorSerializer extends AbstractMediatorSerializer {

    /**
     * Stores certain parameters that are common to both Collector and Finder instances of the cache mediator
     */
    private CacheStore cacheStore;

    /**
     * {@inheritDoc}
     */
    protected OMElement serializeSpecificMediator(Mediator mediator) {
        if (!(mediator instanceof EICacheMediator)) {
            handleException("Unsupported mediator passed in for serialization : " + mediator.getType());
        }

        EICacheMediator cacheMediator = (EICacheMediator) mediator;
        OMElement cacheElem = fac.createOMElement(CachingConstants.CACHE_LOCAL_NAME, synNS);
        saveTracingState(cacheElem, mediator);

        if (cacheMediator.getId() != null) {
            cacheStore = CacheStoreManager.get(cacheMediator.getId());
            cacheElem.addAttribute(fac.createOMAttribute("id", nullNS, cacheMediator.getId()));
        } else {
            cacheStore = CacheStoreManager.get("");
        }

        if (cacheMediator.isCollector()) {
            cacheElem.addAttribute(fac.createOMAttribute("collector", nullNS, "true"));
        } else {
            cacheElem.addAttribute(fac.createOMAttribute("collector", nullNS, "false"));

            if (cacheMediator.getTimeout() != 0) {
                cacheElem.addAttribute(
                        fac.createOMAttribute("timeout", nullNS, Long.toString(cacheMediator.getTimeout())));
            }

            if (cacheStore.getMaxMessageSize() != 0) {
                cacheElem.addAttribute(
                        fac.createOMAttribute("maxMessageSize", nullNS,
                                              Integer.toString(cacheStore.getMaxMessageSize())));
            }

            OMElement onCacheHit = null;
            if (cacheMediator.getOnCacheHitRef() != null) {
                onCacheHit = fac.createOMElement("onCacheHit", synNS);
                onCacheHit.addAttribute(
                        fac.createOMAttribute("sequence", nullNS, cacheMediator.getOnCacheHitRef()));
                cacheElem.addChild(onCacheHit);
            } else if (cacheMediator.getOnCacheHitSequence() != null) {
                onCacheHit = fac.createOMElement("onCacheHit", synNS);
                serializeChildren(onCacheHit, cacheMediator.getOnCacheHitSequence().getList());
                cacheElem.addChild(onCacheHit);
            }

            if (onCacheHit != null) {
                if (cacheMediator.isContinueExecution()) {
                    onCacheHit.addAttribute(fac.createOMAttribute("continueExecution", nullNS, "true"));
                } else {
                    onCacheHit.addAttribute(fac.createOMAttribute("continueExecution", nullNS, "false"));
                }
            }

            OMElement protocolElem = fac.createOMElement("protocol", synNS);
            protocolElem.addAttribute(fac.createOMAttribute("type", nullNS, cacheStore.getProtocolType()));
            if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(cacheStore.getProtocolType())) {

                String[] methods = cacheStore.getHTTPMethodsToCache();
                if (!(methods.length == 0 && "".equals(methods[0]))) {
                    StringBuilder method = new StringBuilder();
                    for (int i = 0; i < methods.length; i++) {
                        if (i != methods.length - 1) {
                            method.append(methods[i]).append(",");
                        } else {
                            method.append(methods[i]);
                        }
                    }
                    OMElement methodElem = fac.createOMElement("methods", synNS);
                    methodElem.setText(method.toString());
                    protocolElem.addChild(methodElem);
                }

                String[] headers = cacheMediator.getHeadersToExcludeInHash();
                if (!(headers.length == 0 && "".equals(headers[0]))) {
                    StringBuilder header = new StringBuilder();
                    for (int i = 0; i < headers.length; i++) {
                        if (i != headers.length - 1) {
                            header.append(headers[i]).append(",");
                        } else {
                            header.append(headers[i]);
                        }
                    }
                    OMElement headerElem = fac.createOMElement("headersToExcludeInHash", synNS);
                    headerElem.setText(header.toString());
                    protocolElem.addChild(headerElem);
                }

                String responseCodes = cacheStore.getResponseCodes();
                OMElement responseCodesElem = fac.createOMElement("responseCodes", synNS);
                responseCodesElem.setText(responseCodes);
                protocolElem.addChild(responseCodesElem);

            }

            if (cacheMediator.getDigestGenerator() != null) {
                OMElement hashGeneratorElem = fac.createOMElement("hashGenerator", synNS);
                hashGeneratorElem.setText(cacheMediator.getDigestGenerator().getClass().getName());
                protocolElem.addChild(hashGeneratorElem);
            }

            cacheElem.addChild(protocolElem);

            if (cacheMediator.getInMemoryCacheSize() > -1) {
                OMElement implElem = fac.createOMElement("implementation", synNS);
                implElem.addAttribute(fac.createOMAttribute("maxSize", nullNS,
                                                            Integer.toString(cacheMediator.getInMemoryCacheSize())));
                cacheElem.addChild(implElem);
            }
        }
        return cacheElem;
    }

    /**
     * {@inheritDoc}
     */
    public String getMediatorClassName() {
        return EICacheMediator.class.getName();
    }

    /**
     * Creates XML representation of the child mediators
     *
     * @param parent The mediator for which the XML representation child should be attached
     * @param list   The mediators list for which the XML representation should be created
     */
    protected void serializeChildren(OMElement parent, List<Mediator> list) {
        for (Mediator child : list) {
            MediatorSerializer medSer = MediatorSerializerFinder.getInstance().getSerializer(child);
            if (medSer != null) {
                medSer.serializeMediator(parent, child);
            } else {
                handleException("Unable to find a serializer for mediator : " + child.getType());
            }
        }
    }
}
