package com.ecm.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MS = 2_000L;
    private static final long MAX_RETRIES = 10L;

    /**
     * A change that fails because the catalog or Elasticsearch is briefly unavailable is retried for about twenty
     * seconds, instead of the default ten immediate attempts that would drop the change before they recover.
     */
    @Bean
    public CommonErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}
