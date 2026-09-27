package com.ecm.payment.config;

import com.ecm.payment.messaging.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * payment-service is the producer of these topics, so it owns their declaration.
 */
@Configuration
public class KafkaConfig {

    /**
     * Declared explicitly (not relying on autoconfiguration) so NewTopic beans below are reliably created on startup.
     */
    @Bean
    public KafkaAdmin kafkaAdmin(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

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

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED).partitions(3).replicas(1).build();
    }
}
