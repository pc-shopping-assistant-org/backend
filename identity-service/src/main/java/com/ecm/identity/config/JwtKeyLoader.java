package com.ecm.identity.config;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@Component
public class JwtKeyLoader {

    private static final String RSA_ALGORITHM = "RSA";
    private static final int KEY_SIZE_BITS = 2048;
    private final Path keyDirectory;

    public JwtKeyLoader() {
        this("./.local");
    }

    JwtKeyLoader(String keyDirectory) {
        this.keyDirectory = Path.of(keyDirectory);
    }

    public KeyPair load(JwtProperties properties) {
        if (properties.getPrivateKey() != null && !properties.getPrivateKey().isBlank()
                && properties.getPublicKey() != null && !properties.getPublicKey().isBlank()) {
            return decodeConfigured(properties);
        }
        if (!properties.isAllowDevKeyGeneration()) {
            throw new IllegalStateException("JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be configured");
        }
        return loadOrGenerateDevelopmentKeyPair();
    }

    private KeyPair decodeConfigured(JwtProperties properties) {
        try {
            var factory = java.security.KeyFactory.getInstance(RSA_ALGORITHM);
            var privateKey = factory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(properties.getPrivateKey())));
            var publicKey = factory.generatePublic(new java.security.spec.X509EncodedKeySpec(
                    Base64.getDecoder().decode(properties.getPublicKey())));
            return new KeyPair(publicKey, privateKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Configured JWT RSA keys are invalid", ex);
        }
    }

    private KeyPair loadOrGenerateDevelopmentKeyPair() {
        try {
            Path privatePath = keyDirectory.resolve("jwt-private.pk8");
            Path publicPath = keyDirectory.resolve("jwt-public.der");
            boolean privateExists = Files.exists(privatePath);
            boolean publicExists = Files.exists(publicPath);
            if (privateExists != publicExists) {
                throw new IllegalStateException("Local JWT key pair is incomplete; restore both key files or remove both to regenerate");
            }
            if (privateExists) {
                var factory = java.security.KeyFactory.getInstance(RSA_ALGORITHM);
                var privateKey = factory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Files.readAllBytes(privatePath)));
                var publicKey = factory.generatePublic(new java.security.spec.X509EncodedKeySpec(Files.readAllBytes(publicPath)));
                return new KeyPair(publicKey, privateKey);
            }
            Files.createDirectories(keyDirectory);
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(KEY_SIZE_BITS);
            KeyPair generated = generator.generateKeyPair();
            Files.write(privatePath, generated.getPrivate().getEncoded());
            Files.write(publicPath, generated.getPublic().getEncoded());
            return generated;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load or generate the local development JWT key pair", ex);
        }
    }
}
