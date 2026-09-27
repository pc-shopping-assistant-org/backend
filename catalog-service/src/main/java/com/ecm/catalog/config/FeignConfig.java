package com.ecm.catalog.config;

import com.ecm.common.exception.ExternalServiceException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Translates raw Feign failures into {@link ExternalServiceException} so callers never see a Feign type directly. */
@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> new ExternalServiceException(
                methodKey,
                "HTTP " + response.status() + " calling " + response.request().url());
    }
}
