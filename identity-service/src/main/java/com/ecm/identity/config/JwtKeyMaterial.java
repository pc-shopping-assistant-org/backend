package com.ecm.identity.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

@Component
@RequiredArgsConstructor
public class JwtKeyMaterial {

    private final JwtProperties properties;
    private final JwtKeyLoader keyLoader;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void initialize() {
        KeyPair keyPair = keyLoader.load(properties);
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    public PrivateKey privateKey() { return privateKey; }
    public PublicKey publicKey() { return publicKey; }
}
