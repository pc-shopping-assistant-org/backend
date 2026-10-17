package com.ecm.sync.service;

import com.ecm.sync.config.KafkaConfig;
import com.ecm.sync.dto.response.DltReplayResponse;
import com.ecm.sync.dto.response.DltStatusResponse;
import com.ecm.sync.exception.InvalidChangeEventException;
import com.ecm.sync.messaging.kafka.consumer.CatalogChangeConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs the changes parked on {@code catalog.changed.DLT} through the normal consumer again, once what made them fail is
 * fixed. It reads with its own consumer group, so the position is remembered between replays and each call continues
 * where the last one stopped. The offset moves only past changes that were handled (or are unreadable), never past one
 * that failed again.
 */
@Slf4j
@Service
public class DltReplayService {

    static final String GROUP_ID = "sync-service-dlt-replay";
    private static final int POLL_BATCH_SIZE = 100;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    private final CatalogChangeConsumer consumer;
    private final Supplier<Consumer<String, String>> consumers;

    @Autowired
    public DltReplayService(CatalogChangeConsumer consumer, @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this(consumer, () -> new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, POLL_BATCH_SIZE)));
    }

    DltReplayService(CatalogChangeConsumer consumer, Supplier<Consumer<String, String>> consumers) {
        this.consumer = consumer;
        this.consumers = consumers;
    }

    public DltStatusResponse status() {
        try (Consumer<String, String> kafka = consumers.get()) {
            return new DltStatusResponse(pending(kafka, partitions(kafka)));
        }
    }

    /** Replays at most {@code limit} parked changes, oldest first. */
    public DltReplayResponse replay(int limit) {
        try (Consumer<String, String> kafka = consumers.get()) {
            List<TopicPartition> partitions = partitions(kafka);
            if (partitions.isEmpty()) {
                return new DltReplayResponse(0, 0, 0, false);
            }
            kafka.assign(partitions);

            // 1. Oldest first; the offset to commit moves forward only over a change that was handled or can never be
            int replayed = 0;
            int skipped = 0;
            boolean blocked = false;
            Map<TopicPartition, OffsetAndMetadata> done = new HashMap<>();
            read:
            while (replayed + skipped < limit) {
                ConsumerRecords<String, String> records = kafka.poll(POLL_TIMEOUT);
                if (records.isEmpty()) {
                    break;
                }
                for (ConsumerRecord<String, String> record : records) {
                    if (replayed + skipped >= limit) {
                        break read;
                    }
                    try {
                        consumer.onCatalogChanged(record.value());
                        replayed++;
                    } catch (InvalidChangeEventException ex) {
                        log.warn("Parked change {}@{} is unreadable and stays on {} until it expires: {}", record.partition(), record.offset(), KafkaConfig.DEAD_LETTER_TOPIC, ex.getMessage());
                        skipped++;
                    } catch (RuntimeException ex) {
                        log.warn("Parked change {}@{} failed again, replay stops here: {}", record.partition(), record.offset(), ex.getMessage());
                        blocked = true;
                        break read;
                    }
                    done.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset() + 1));
                }
            }

            // 2. Remember how far it got, so the next replay continues after it
            if (!done.isEmpty()) {
                kafka.commitSync(done);
            }
            return new DltReplayResponse(replayed, skipped, pending(kafka, partitions), blocked);
        }
    }

    /** No partitions when the topic does not exist yet: nothing has ever been parked. */
    private List<TopicPartition> partitions(Consumer<String, String> kafka) {
        var infos = kafka.partitionsFor(KafkaConfig.DEAD_LETTER_TOPIC, Duration.ofSeconds(10));
        return infos == null ? List.of() : infos.stream().map(info -> new TopicPartition(info.topic(), info.partition())).toList();
    }

    private long pending(Consumer<String, String> kafka, List<TopicPartition> partitions) {
        if (partitions.isEmpty()) {
            return 0;
        }
        Map<TopicPartition, Long> ends = kafka.endOffsets(partitions);
        Map<TopicPartition, Long> starts = kafka.beginningOffsets(partitions);
        Map<TopicPartition, OffsetAndMetadata> committed = kafka.committed(new HashSet<>(partitions));
        long pending = 0;
        for (TopicPartition partition : partitions) {
            OffsetAndMetadata offset = committed.get(partition);
            long next = Math.max(offset == null ? 0 : offset.offset(), starts.get(partition));
            pending += Math.max(0, ends.get(partition) - next);
        }
        return pending;
    }
}
