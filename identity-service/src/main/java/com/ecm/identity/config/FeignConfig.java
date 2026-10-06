package com.ecm.identity.config;

import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Translates raw Feign failures into {@link ExternalServiceException} (or {@link ResourceNotFoundException}
 * for a 404) so callers never see a Feign type directly.
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
}
