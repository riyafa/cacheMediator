package org.riyafa;

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
import java.util.Iterator;
import java.util.Map;

/**
 * Created by riyafa on 7/13/17.
 */
public class HttpRequestHashGenerator implements DigestGenerator {
    private static final Log log = LogFactory.getLog(HttpRequestHashGenerator.class);
    public static final String MD5_DIGEST_ALGORITHM = "MD5";

    public String getDigest(MessageContext msgContext, boolean isGet, String... headers) throws CachingException {
        if (headers.length > 0) {
            boolean excludeAllHeaders = "exclude-all".equals(headers[0]);
            if (!excludeAllHeaders) {
                Map<String, String> transportHeaders =
                        (Map<String, String>) msgContext.getProperty(MessageContext.TRANSPORT_HEADERS);
                for (String header : headers) {
                    transportHeaders.remove(header);
                }
                if (isGet) {
                    if (msgContext.getTo() == null) {
                        return null;
                    }
                    String toAddress = msgContext.getTo().getAddress();
                    byte[] digest = getDigest(toAddress, transportHeaders, MD5_DIGEST_ALGORITHM);
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
                            digest = getDigest(body, toAddress, transportHeaders, MD5_DIGEST_ALGORITHM);
                        } else {
                            digest = getDigest(body, MD5_DIGEST_ALGORITHM);
                        }
                        return digest != null ? getStringRepresentation(digest) : null;
                    } else {
                        return null;
                    }
                }
            } else {
                if (isGet) {
                    if (msgContext.getTo() == null) {
                        return null;
                    }
                    String toAddress = msgContext.getTo().getAddress();
                    byte[] digest = getDigest(toAddress, MD5_DIGEST_ALGORITHM);
                    return digest != null ? getStringRepresentation(digest) : null;
                } else {
                    OMNode request = msgContext.getEnvelope().getBody();
                    if (request != null) {
                        byte[] digest = getDigest(request, MD5_DIGEST_ALGORITHM);
                        return digest != null ? getStringRepresentation(digest) : null;
                    } else {
                        return null;
                    }
                }
            }
        }
        return null;
    }

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
     * @return byte[] representing the calculated digest over the provided node
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
}