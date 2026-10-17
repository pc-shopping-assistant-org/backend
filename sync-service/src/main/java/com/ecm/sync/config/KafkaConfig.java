package com.ecm.sync.config;

import com.ecm.sync.exception.InvalidChangeEventException;
import com.ecm.sync.messaging.kafka.consumer.CatalogChangeConsumer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;
import java.util.Map;

/**
 * A change that fails is never dropped. While the catalog or Elasticsearch is down it is retried with a growing delay,
 * for hours; a change that can never be processed, or is still failing after that, is parked on
 * {@code catalog.changed.DLT} and its offset is committed only once the park succeeded, so the partition keeps moving
 * and the change stays available for {@code DltReplayService} or a full sync.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    public static final String DEAD_LETTER_TOPIC = CatalogChangeConsumer.TOPIC + ".DLT";

    private static final long RETRY_INITIAL_INTERVAL_MS = 2_000L;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long RETRY_MAX_INTERVAL_MS = 30_000L;
    private static final Duration RETRY_GIVE_UP_AFTER = Duration.ofHours(6);
    private static final Duration DEAD_LETTER_RETENTION = Duration.ofDays(30);

    /** Same String-only template as order-service: Boot's own template is {@code <Object, Object>} and would not inject. */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                // A parked change must survive a broker restart, or the offset would be committed over nothing
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /** The topic is created at startup when missing; an existing one keeps its settings (see docker/README.md). */
    @Bean
    public NewTopic catalogChangedDeadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(DEAD_LETTER_RETENTION.toMillis()))
                .build();
    }

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meters) {
        var backOff = new ExponentialBackOff(RETRY_INITIAL_INTERVAL_MS, RETRY_MULTIPLIER);
        backOff.setMaxInterval(RETRY_MAX_INTERVAL_MS);
        backOff.setMaxElapsedTime(RETRY_GIVE_UP_AFTER.toMillis());
        return errorHandler(kafkaTemplate, meters, backOff);
    }

    /** The back-off is a parameter so a test can run the same handler with delays of milliseconds. */
    static DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meters, BackOff backOff) {
        // The partition is left to the producer, so the topic need not match the partition count of catalog.changed
        var publisher = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> new TopicPartition(DEAD_LETTER_TOPIC, -1));
        Counter parked = Counter.builder("sync.dlt.published").description("Catalog changes parked on the dead-letter topic").register(meters);

        var handler = new DefaultErrorHandler((record, ex) -> {
            publisher.accept(record, ex);
            parked.increment();
            log.error("Catalog change {}-{}@{} parked on {}: {}", record.topic(), record.partition(), record.offset(), DEAD_LETTER_TOPIC, ex.getMessage());
        }, backOff);
        handler.addNotRetryableExceptions(InvalidChangeEventException.class);
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception ex, int attempt) {
                log.warn("Catalog change {}-{}@{} failed (attempt {}), will retry: {}", record.topic(), record.partition(), record.offset(), attempt, ex.getMessage());
            }
        });
        return handler;
    }
}
