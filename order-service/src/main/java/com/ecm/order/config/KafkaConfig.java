package com.ecm.order.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/** Failed listener invocations are retried 3 times, then routed to "<topic>.DLT" instead of blocking the partition forever. */
@Configuration
public class KafkaConfig {

    /**
     * Spring Boot autoconfigures a {@code KafkaTemplate<Object, Object>} bean, which does not
     * satisfy an injection point declared as {@code KafkaTemplate<String, String>} (Spring
     * matches producer/template beans by exact generic type) — every publisher/consumer here
     * agrees on plain String keys/values, so this declares that exact type explicitly.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    // Reuses the KafkaTemplate<String, String> bean above — defining a custom KafkaTemplate
    // bean disables Spring Boot's autoconfigured KafkaTemplate<Object, Object>, so nothing
    // of that generic type remains to inject here.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
