package com.ecm.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String privateKey;
    private String publicKey;
    private String keyId = "identity-key-1";
    private String issuer = "http://identity-service";
    private boolean allowDevKeyGeneration = true;
    private long accessTokenExpirationMs;
    private long refreshTokenExpirationMs;
}
