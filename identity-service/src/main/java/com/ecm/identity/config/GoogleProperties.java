package com.ecm.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "google")
@Getter
@Setter
public class GoogleProperties {

    /**
     * Google OAuth 2.0 client ID for verifying ID tokens from Google Identity Services.
     * Configure via {@code google.client-id} or {@code GOOGLE_CLIENT_ID} environment variable.
     */
    private String clientId;
}
