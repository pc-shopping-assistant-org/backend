package com.ecm.catalog.config;

import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Translates raw Feign failures into {@link ExternalServiceException} (or {@link ResourceNotFoundException}
 * for a 404) so callers never see a Feign type directly, and forwards the caller's token so downstream
 * services can authorize on behalf of the current user.
 */
@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            String message = "HTTP " + response.status() + " calling " + response.request().url();
            if (response.status() == HttpStatus.NOT_FOUND.value()) {
                return new ResourceNotFoundException(message);
            }
            return new ExternalServiceException(methodKey, message);
        };
    }

    @Bean
    public RequestInterceptor authorizationRelayInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
        };
    }
}
