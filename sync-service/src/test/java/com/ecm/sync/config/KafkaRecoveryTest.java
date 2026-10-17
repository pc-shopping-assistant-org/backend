package com.ecm.sync.config;

import com.ecm.sync.messaging.kafka.consumer.CatalogChangeConsumer;
import com.ecm.sync.service.ProductIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The real error handler of {@link KafkaConfig}, on an embedded broker, with delays of milliseconds instead of seconds.
 * The listener is the real {@link CatalogChangeConsumer} over a mocked index.
 */
class KafkaRecoveryTest {

    private static final String TOPIC = CatalogChangeConsumer.TOPIC;
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_PRODUCT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private final ProductIndexService indexService = mock(ProductIndexService.class);
    private EmbeddedKafkaBroker broker;
    private KafkaTemplate<String, String> template;
    private KafkaMessageListenerContainer<String, String> container;

    @BeforeEach
    void startBroker() {
        broker = new EmbeddedKafkaKraftBroker(1, 1, TOPIC, KafkaConfig.DEAD_LETTER_TOPIC);
        broker.afterPropertiesSet();
        template = new KafkaConfig().kafkaTemplate(broker.getBrokersAsString());
    }

    @AfterEach
    void stopBroker() {
        if (container != null) {
            container.stop();
        }
        template.destroy();
        broker.destroy();
    }

    private static String productChange(UUID id) {
        return "{\"before\":null,\"after\":{\"id\":\"" + id + "\"},\"source\":{\"table\":\"products\"},\"op\":\"u\"}";
    }

    private void listen(BackOff backOff) {
        var consumer = new CatalogChangeConsumer(indexService, new ObjectMapper());
        var factory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "sync-service-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        var properties = new ContainerProperties(TOPIC);
        properties.setMessageListener((MessageListener<String, String>) record -> consumer.onCatalogChanged(record.value()));
        container = new KafkaMessageListenerContainer<>(factory, properties);
        container.setCommonErrorHandler(KafkaConfig.errorHandler(template, new SimpleMeterRegistry(), backOff));
        container.start();
    }

    private List<ConsumerRecord<String, String>> parked(int expected) {
        try (var kafka = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            kafka.subscribe(List.of(KafkaConfig.DEAD_LETTER_TOPIC));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            long until = System.nanoTime() + (expected == 0 ? Duration.ofSeconds(3) : WAIT).toNanos();
            while (System.nanoTime() < until && (expected == 0 || found.size() < expected)) {
                kafka.poll(Duration.ofMillis(200)).forEach(found::add);
            }
            return found;
        }
    }

    @Test
    void aCatalogThatStaysDownLongerThanTheOldRetryBudgetStillGetsTheChangeIndexed() {
        // 15 failures is more than the old limit of 10 retries, which dropped the change
        var failures = new AtomicInteger(15);
        doAnswer(call -> {
            if (failures.getAndDecrement() > 0) {
                throw new IllegalStateException("catalog down");
            }
            return null;
        }).when(indexService).reindexProduct(PRODUCT);
        listen(new FixedBackOff(20, FixedBackOff.UNLIMITED_ATTEMPTS));

        template.send(TOPIC, productChange(PRODUCT));

        verify(indexService, timeout(WAIT.toMillis()).times(16)).reindexProduct(PRODUCT);
        assertTrue(parked(0).isEmpty(), "a change that recovered must not be parked");
    }

    @Test
    void anUnreadableChangeIsParkedAndTheOnesBehindItAreStillIndexed() {
        listen(new FixedBackOff(20, FixedBackOff.UNLIMITED_ATTEMPTS));

        template.send(TOPIC, "not json");
        template.send(TOPIC, productChange(OTHER_PRODUCT));

        verify(indexService, timeout(WAIT.toMillis())).reindexProduct(OTHER_PRODUCT);
        List<ConsumerRecord<String, String>> parked = parked(1);
        assertEquals(1, parked.size());
        assertEquals("not json", parked.getFirst().value());
    }

    @Test
    void aChangeThatKeepsFailingIsParkedWhenTheRetryTimeIsUsedUpInsteadOfDropped() {
        doThrow(new IllegalStateException("catalog down")).when(indexService).reindexProduct(PRODUCT);
        var backOff = new ExponentialBackOff(20, 1.0);
        backOff.setMaxElapsedTime(300);
        listen(backOff);

        template.send(TOPIC, productChange(PRODUCT));
        template.send(TOPIC, productChange(OTHER_PRODUCT));

        List<ConsumerRecord<String, String>> parked = parked(1);
        assertEquals(productChange(PRODUCT), parked.getFirst().value());
        verify(indexService, timeout(WAIT.toMillis())).reindexProduct(OTHER_PRODUCT);
        verify(indexService, org.mockito.Mockito.atLeast(2)).reindexProduct(PRODUCT);
    }
}
