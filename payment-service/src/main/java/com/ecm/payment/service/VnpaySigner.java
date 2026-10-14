package com.ecm.payment.service;

import com.ecm.payment.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Signs and checks the parameters exchanged with VNPAY: HMAC-SHA512 over the sorted, URL-encoded {@code vnp_*} fields. */
@Component
@RequiredArgsConstructor
public class VnpaySigner {

    public static final String SECURE_HASH = "vnp_SecureHash";
    private static final String SECURE_HASH_TYPE = "vnp_SecureHashType";
    private static final String HMAC_SHA512 = "HmacSHA512";

    private final VnpayProperties properties;

    /** The signed query string for the given fields, ready to append to the VNPAY pay URL. */
    public String signedQuery(Map<String, String> fields) {
        String data = hashData(fields);
        return data + "&" + SECURE_HASH + "=" + hmac(data);
    }

    /** True when the {@code vnp_SecureHash} of a callback matches the other fields. */
    public boolean verify(Map<String, String> callback) {
        String received = callback.get(SECURE_HASH);
        if (received == null || received.isBlank()) {
            return false;
        }
        byte[] expected = hmac(hashData(callback)).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, received.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private String hashData(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .filter(entry -> !SECURE_HASH.equals(entry.getKey()) && !SECURE_HASH_TYPE.equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign the VNPAY request", ex);
        }
    }
}
