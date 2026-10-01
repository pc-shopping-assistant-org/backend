package com.ecm.identity.controller;

import com.ecm.identity.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class JwksController {

    private static final String KEY_TYPE_RSA = "RSA";
    private static final String USE_SIGNATURE = "sig";
    private static final String ALGORITHM_RS256 = "RS256";

    private final JwtTokenProvider tokenProvider;

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey key = (RSAPublicKey) tokenProvider.publicKey();
        return Map.of("keys", List.of(Map.of(
                "kty", KEY_TYPE_RSA,
                "use", USE_SIGNATURE,
                "alg", ALGORITHM_RS256,
                "kid", tokenProvider.keyId(),
                "n", encodeUnsigned(key.getModulus().toByteArray()),
                "e", encodeUnsigned(key.getPublicExponent().toByteArray())
        )));
    }

    private String encodeUnsigned(byte[] bytes) {
        int firstByte = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes, firstByte, bytes.length));
    }
}
