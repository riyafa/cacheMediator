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
    protected OMElement serializeSpecificMediator(Mediator mediator) {
        if (!(mediator instanceof EICacheMediator)) {
            handleException("Unsupported mediator passed in for serialization : " + mediator.getType());
        }

        EICacheMediator cacheMediator = (EICacheMediator) mediator;
        OMElement cache = fac.createOMElement(CachingConstants.CACHE_LOCAL_NAME, synNS);
        saveTracingState(cache, mediator);

        if (cacheMediator.getId() != null) {
            cache.addAttribute(fac.createOMAttribute("id", nullNS, cacheMediator.getId()));
        }

        if (cacheMediator.isCollector()) {
            cache.addAttribute(fac.createOMAttribute("collector", nullNS, "true"));
        } else {
            cache.addAttribute(fac.createOMAttribute("collector", nullNS, "false"));

            if (cacheMediator.getTimeout() != 0) {
                cache.addAttribute(
                        fac.createOMAttribute("timeout", nullNS, Long.toString(cacheMediator.getTimeout())));
            }

            if (cacheMediator.getMaxMessageSize() != 0) {
                cache.addAttribute(
                        fac.createOMAttribute("maxMessageSize", nullNS,
                                              Integer.toString(cacheMediator.getMaxMessageSize())));
            }

            OMElement onCacheHit = null;
            if (cacheMediator.getOnCacheHitRef() != null) {
                onCacheHit = fac.createOMElement("onCacheHit", synNS);
                onCacheHit.addAttribute(
                        fac.createOMAttribute("sequence", nullNS, cacheMediator.getOnCacheHitRef()));
                cache.addChild(onCacheHit);
            } else if (cacheMediator.getOnCacheHitSequence() != null) {
                onCacheHit = fac.createOMElement("onCacheHit", synNS);
                serializeChildren(onCacheHit, cacheMediator.getOnCacheHitSequence().getList());
                cache.addChild(onCacheHit);
            }

            if (onCacheHit != null) {
                if (cacheMediator.isContinueExecution()) {
                    onCacheHit.addAttribute(fac.createOMAttribute("continueExecution", nullNS, "true"));
                } else {
                    onCacheHit.addAttribute(fac.createOMAttribute("continueExecution", nullNS, "false"));
                }
            }

            if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(cacheMediator.getType())) {
                OMElement protocolElem = fac.createOMElement("protocol", synNS);
                protocolElem.addAttribute(fac.createOMAttribute("type", nullNS, cacheMediator.getType()));

                String[] methods = cacheMediator.getHTTPMethodsToCache();
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

                String[] responseCodes = cacheMediator.getResponseCodes();
                if (!(responseCodes.length == 0 && "".equals(responseCodes[0]))) {
                    StringBuilder responseCode = new StringBuilder();
                    for (int i = 0; i < responseCodes.length; i++) {
                        if (i != responseCodes.length - 1) {
                            responseCode.append(responseCodes[i]).append(",");
                        } else {
                            responseCode.append(responseCodes[i]);
                        }
                    }
                    OMElement responseCodesElem = fac.createOMElement("responseCodes", synNS);
                    responseCodesElem.setText(responseCode.toString());
                    protocolElem.addChild(responseCodesElem);
                }

                if (cacheMediator.getDigestGenerator() != null) {
                    OMElement hashGeneratorElem = fac.createOMElement("hashGenerator", synNS);
                    hashGeneratorElem.setText(cacheMediator.getDigestGenerator().getClass().getName());
                    protocolElem.addChild(hashGeneratorElem);
                }

                cache.addChild(protocolElem);
            }


            if (cacheMediator.getInMemoryCacheSize() != 0) {
                OMElement implElem = fac.createOMElement("implementation", synNS);
                implElem.addAttribute(fac.createOMAttribute("type", nullNS, "memory"));
                implElem.addAttribute(fac.createOMAttribute("maxMessagesInCache", nullNS,
                                                            Integer.toString(cacheMediator.getInMemoryCacheSize())));
                cache.addChild(implElem);
            }
        }
        return cache;
    }

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
