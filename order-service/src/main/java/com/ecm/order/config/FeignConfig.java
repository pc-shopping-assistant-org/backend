package com.ecm.order.config;

import com.ecm.common.exception.ExternalServiceException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Translates failed Feign calls into {@link ExternalServiceException}, except a 400, which stays a Feign exception.
 */
@Configuration
public class FeignConfig {

    private static final int BAD_REQUEST = 400;

    /**
     * A 400 stays a Feign exception, because it is the other service rejecting the request (for example an invalid
     * discount code) and the caller decides what that means; every other failure is a failed call.
     */
    @Bean
    public ErrorDecoder feignErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> response.status() == BAD_REQUEST
                ? defaultDecoder.decode(methodKey, response)
                : new ExternalServiceException(methodKey, "HTTP " + response.status() + " calling " + response.request().url());
    }
}
