package com.ecm.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSecurityProperties.class)
@ConditionalOnProperty(prefix = "security.jwt", name = "jwks-uri")
public class JwtAuthenticationAutoConfiguration {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String EXPECTED_AUDIENCE = "pc-shopping-api";

    @Bean
    @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtClaimsAuthenticationConverter());
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwksUri()).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(EXPECTED_AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token audience", null));
        OAuth2TokenValidator<Jwt> accessTokenValidator = jwt -> {
            String accountId = jwt.getClaimAsString("accountId");
            if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString("type"))
                    || !ACTIVE_STATUS.equalsIgnoreCase(jwt.getClaimAsString("status"))
                    || !isUuid(accountId)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Invalid access token claims", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator, accessTokenValidator));
        return decoder;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
    }
}
