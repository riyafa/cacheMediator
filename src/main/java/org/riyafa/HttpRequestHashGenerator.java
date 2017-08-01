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
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMProcessingInstruction;
import org.apache.axiom.om.OMText;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class HttpRequestHashGenerator implements DigestGenerator {
    /**
     * Log object to use when logging is required in this class.
     */
    private static final Log log = LogFactory.getLog(HttpRequestHashGenerator.class);
    /**
     * String representing the MD5 digest algorithm
     */
    public static final String MD5_DIGEST_ALGORITHM = "MD5";

    /**
     * {@inheritDoc}
     */
    public String getDigest(MessageContext msgContext, boolean isGet, String... headers) throws CachingException {
        boolean excludeAllHeaders = CachingConstants.EXCLUDE_ALL_VAL.equals(headers[0]); //put a *
        //If some or all headers need to be included in the hash
        if (!excludeAllHeaders) {
            Map<String, String> transportHeaders =
                    (Map<String, String>) msgContext.getProperty(MessageContext.TRANSPORT_HEADERS);
            for (String header : headers) {
                transportHeaders.remove(header);
            }
            //If the HTTP method is GET do not hash the payload. Hash only url and headers.
            if (isGet) {//GET, HEAD, DELETE
                if (msgContext.getTo() == null) {
                    return null;
                }
                String toAddress = msgContext.getTo().getAddress();
                byte[] digest = getDigest(toAddress, transportHeaders, MD5_DIGEST_ALGORITHM);
                return digest != null ? getStringRepresentation(digest) : null;
            } else {//If the HTTP method is POST hash the payload along with the url and the headers
                OMNode body = msgContext.getEnvelope().getBody();
                String toAddress = null;
                if (msgContext.getTo() != null) {
                    toAddress = msgContext.getTo().getAddress();
                }
                if (body != null) {
                    byte[] digest;
                    if (toAddress != null) {
                        digest = getDigest(body, toAddress, transportHeaders, MD5_DIGEST_ALGORITHM);
                    } else {
                        digest = getDigest(body, MD5_DIGEST_ALGORITHM);
                    }
                    return digest != null ? getStringRepresentation(digest) : null;
                } else {
                    return null;
                }
            }
        } else { //Do not hash the headers
            if (isGet) {
                if (msgContext.getTo() == null) {
                    return null;
                }
                String toAddress = msgContext.getTo().getAddress();
                byte[] digest = getDigest(toAddress, MD5_DIGEST_ALGORITHM);
                return digest != null ? getStringRepresentation(digest) : null;
            } else {
                OMNode body = msgContext.getEnvelope().getBody();
                String toAddress = null;
                if (msgContext.getTo() != null) {
                    toAddress = msgContext.getTo().getAddress();
                }
                if (body != null) {
                    byte[] digest;
                    if (toAddress != null) {
                        digest = getDigest(body, toAddress, null, MD5_DIGEST_ALGORITHM);
                    } else {
                        digest = getDigest(body, MD5_DIGEST_ALGORITHM);
                    }
                    return digest != null ? getStringRepresentation(digest) : null;
                } else {
                    return null;
                }
            }
        }
    }


    /**
     * For the digest generation using the to address
     *
     * @param toAddress       Request To address to be subjected to the key generation
     * @param digestAlgorithm digest algorithm as a String
     * @return byte[] representing the calculated digest over the toAddress
     * @throws CachingException if there is an error in generating the digest
     */
    public byte[] getDigest(String toAddress, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];
        try {
            MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
            md.update(toAddress.getBytes("UnicodeBigUnmarked"));
            digest = md.digest();
        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (UnsupportedEncodingException e) {
            handleException("Error in generating the digest " +
                                    "using the provided encoding : UnicodeBigUnmarked", e);
        }

        return digest;
    }

    /**
     * This is an overloaded method for the digest generation for OMNode
     *
     * @param node            - OMNode to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] c node
     * @throws CachingException if there is an error in generating the digest
     */
    public byte[] getDigest(OMNode node, String digestAlgorithm) throws CachingException {

        if (node.getType() == OMNode.ELEMENT_NODE) {
            return getDigest((OMElement) node, digestAlgorithm);
        } else if (node.getType() == OMNode.TEXT_NODE) {
            return getDigest((OMText) node, digestAlgorithm);
        } else if (node.getType() == OMNode.PI_NODE) {
            return getDigest((OMProcessingInstruction) node, digestAlgorithm);
        } else {
            return new byte[0];
        }
    }

    /**
     * This is an overloaded method for the digest generation for OMNode and request
     *
     * @param node            - OMNode to be subjected to the key generation
     * @param toAddress       - Request To address to be subjected to the key generation
     * @param headers         - Header parameters to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided node
     * @throws CachingException if there is an error in generating the digest
     */
    public byte[] getDigest(OMNode node, String toAddress, Map<String, String> headers,
                            String digestAlgorithm) throws CachingException {

        if (node.getType() == OMNode.ELEMENT_NODE) {
            return getDigest((OMElement) node, toAddress, headers, digestAlgorithm);
        } else if (node.getType() == OMNode.TEXT_NODE) {
            return getDigest((OMText) node, digestAlgorithm);
        } else if (node.getType() == OMNode.PI_NODE) {
            return getDigest((OMProcessingInstruction) node, digestAlgorithm);
        } else {
            return new byte[0];
        }
    }

    private byte[] getDigest(String toAddress, Map<String, String> transportHeaders, String digestAlgorithm)
            throws CachingException {
        byte[] digest = new byte[0];
        try {

            MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(toAddress.getBytes("UnicodeBigUnmarked"));
            Iterator itr = transportHeaders.keySet().iterator();
            while (itr.hasNext()) {
                String key = (String) itr.next();
                String value = transportHeaders.get(key);
                dos.write(getDigest(key, value, digestAlgorithm));
            }
            dos.close();
            md.update(baos.toByteArray());

            digest = md.digest();
        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (IOException e) {
            handleException("Error in calculating the " +
                                    "digest value for the headers", e);
        }
        return digest;
    }

    public byte[] getDigest(String key, String value, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];

        if (!key.equalsIgnoreCase("Date") && !key.equalsIgnoreCase("User-Agent")) {
            try {

                MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
                md.update((byte) 0);
                md.update((byte) 0);
                md.update((byte) 0);
                md.update((byte) 2);
                md.update(key.getBytes("UnicodeBigUnmarked"));

                if (value != null) {
                    md.update((byte) 0);
                    md.update((byte) 0);
                    md.update(value.getBytes("UnicodeBigUnmarked"));
                }

                digest = md.digest();

            } catch (NoSuchAlgorithmException e) {
                handleException("Can not locate the algorithm " +
                                        "provided for the digest generation : " + digestAlgorithm, e);
            } catch (UnsupportedEncodingException e) {
                handleException("Error in generating the digest " +
                                        "using the provided encoding : UnicodeBigUnmarked", e);
            }
        }

        return digest;
    }

    /**
     * Gets the String representation of the byte array
     *
     * @param array - byte[] of which the String representation is required
     * @return the String representation of the byte[]
     */
    public String getStringRepresentation(byte[] array) {

        StringBuffer strBuff = new StringBuffer(array.length);
        for (int i = 0; i < array.length; i++) {
            strBuff.append(array[i]);
        }
        return strBuff.toString();
    }

    private void handleException(String message, Throwable cause) throws CachingException {
        log.debug(message, cause);
        throw new CachingException(message, cause);
    }

    /**
     * This is an overloaded method for the digest generation for OMElement
     *
     * @param element         - OMElement to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided element
     * @throws CachingException if there is an io error or the specified algorithm is incorrect
     */
    public byte[] getDigest(OMElement element, String toAddress, Map<String, String> headers,
                            String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];

        try {

            MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(1);
            dos.write(getExpandedName(element).getBytes("UnicodeBigUnmarked"));
            dos.write((byte) 0);
            dos.write((byte) 0);

            dos.write(toAddress.getBytes("UnicodeBigUnmarked"));
            Iterator itr;
            if (headers != null) {
                itr = headers.keySet().iterator();
                while (itr.hasNext()) {
                    String key = (String) itr.next();
                    String value = headers.get(key);
                    dos.write(getDigest(key, value, digestAlgorithm));
                }
            }

            Collection attrs = getAttributesWithoutNS(element);
            dos.writeInt(attrs.size());


            itr = attrs.iterator();
            while (itr.hasNext()) {
                dos.write(getDigest((OMAttribute) itr.next(), digestAlgorithm));
            }
            OMNode node = element.getFirstOMChild();

            // adjoining Texts are merged,
            // there is  no 0-length Text, and
            // comment nodes are removed.
            int length = 0;
            itr = element.getChildElements();
            while (itr.hasNext()) {
                length++;
                itr.next();
            }
            dos.writeInt(length);

            while (node != null) {
                dos.write(getDigest(node, toAddress, headers, digestAlgorithm));
                node = node.getNextOMSibling();
            }
            dos.close();
            md.update(baos.toByteArray());

            digest = md.digest();

        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (IOException e) {
            handleException("Error in calculating the " +
                                    "digest value for the OMElement : " + element, e);
        }

        return digest;
    }

    /**
     * This method is an overloaded method for the digest generation for OMText
     *
     * @param text            - OMText to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided text
     * @throws CachingException if the specified algorithm is incorrect or the encoding is not supported by the
     *                          processor
     */
    public byte[] getDigest(OMText text, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];

        try {

            MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 3);
            md.update(text.getText().getBytes("UnicodeBigUnmarked"));

            digest = md.digest();

        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (UnsupportedEncodingException e) {
            handleException("Error in generating the digest " +
                                    "using the provided encoding : UnicodeBigUnmarked", e);
        }

        return digest;
    }

    /**
     * This method is an overloaded method for the digest generation for OMProcessingInstruction
     *
     * @param pi              - OMProcessingInstruction to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided pi
     * @throws CachingException if the specified algorithm is incorrect or the encoding is not supported by the
     *                          processor
     */
    public byte[] getDigest(OMProcessingInstruction pi, String digestAlgorithm)
            throws CachingException {

        byte[] digest = new byte[0];

        try {

            MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 7);
            md.update(pi.getTarget().getBytes("UnicodeBigUnmarked"));

            md.update((byte) 0);
            md.update((byte) 0);
            md.update(pi.getValue().getBytes("UnicodeBigUnmarked"));

            digest = md.digest();

        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (UnsupportedEncodingException e) {
            handleException("Error in generating the digest " +
                                    "using the provided encoding : UnicodeBigUnmarked", e);
        }

        return digest;
    }

    /**
     * This is an overloaded method for the digest generation for OMAttribute
     *
     * @param attribute       - OMAttribute to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided attribute
     * @throws CachingException if the specified algorithm is incorrect or the encoding is not supported by the
     *                          processor
     */
    public byte[] getDigest(OMAttribute attribute, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];

        if (!(attribute.getLocalName().equals("xmlns") ||
                attribute.getLocalName().startsWith("xmlns:"))) {

            try {

                MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
                md.update((byte) 0);
                md.update((byte) 0);
                md.update((byte) 0);
                md.update((byte) 2);
                md.update(getExpandedName(attribute).getBytes("UnicodeBigUnmarked"));

                md.update((byte) 0);
                md.update((byte) 0);
                md.update(attribute.getAttributeValue().getBytes("UnicodeBigUnmarked"));

                digest = md.digest();

            } catch (NoSuchAlgorithmException e) {
                handleException("Can not locate the algorithm " +
                                        "provided for the digest generation : " + digestAlgorithm, e);
            } catch (UnsupportedEncodingException e) {
                handleException("Error in generating the digest " +
                                        "using the provided encoding : UnicodeBigUnmarked", e);
            }
        }

        return digest;
    }

    /**
     * This is an overloaded method for getting the expanded name as namespaceURI followed by the local name for
     * OMElement
     *
     * @param element - OMElement of which the expanded name is retrieved
     * @return expanded name of OMElement as an String in the form {ns-uri:local-name}
     */
    public String getExpandedName(OMElement element) {

        if (element.getNamespace() != null) {
            return element.getNamespace().getNamespaceURI() + ":" + element.getLocalName();
        } else {
            return element.getLocalName();
        }
    }

    /**
     * This is an overloaded method for getting the expanded name as namespaceURI followed by the local name for
     * OMAttribute
     *
     * @param attribute - OMAttribute of which the expanded name is retrieved
     * @return expanded name of the OMAttribute as an String in the form {ns-uri:local-name}
     */
    public String getExpandedName(OMAttribute attribute) {

        if (attribute.getNamespace() != null) {
            return attribute.getNamespace().getNamespaceURI() + ":" + attribute.getLocalName();
        } else {
            return attribute.getLocalName();
        }
    }

    /**
     * Gets the collection of attributes which are none namespace declarations for an OMElement sorted according to the
     * expanded names of the attributes
     *
     * @param element - OMElement of which the none ns declaration attributes to be retrieved
     * @return the collection of attributes which are none namespace declarations
     */
    public Collection getAttributesWithoutNS(OMElement element) {

        SortedMap map = new TreeMap();

        Iterator itr = element.getAllAttributes();
        while (itr.hasNext()) {
            OMAttribute attribute = (OMAttribute) itr.next();

            if (!(attribute.getLocalName().equals("xmlns") ||
                    attribute.getLocalName().startsWith("xmlns:"))) {

                map.put(getExpandedName(attribute), attribute);
            }
        }

        return map.values();
    }
}