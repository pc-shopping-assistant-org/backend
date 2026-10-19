package com.ecm.sync.service;

import com.ecm.sync.config.KafkaConfig;
import com.ecm.sync.dto.response.DltReplayResponse;
import com.ecm.sync.exception.InvalidChangeEventException;
import com.ecm.sync.messaging.kafka.consumer.CatalogChangeConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class DltReplayServiceTest {

    private static final TopicPartition PARTITION = new TopicPartition(KafkaConfig.DEAD_LETTER_TOPIC, 0);

    private final CatalogChangeConsumer catalogConsumer = mock(CatalogChangeConsumer.class);

    /** Stays usable after the service closed it, so a test can read what was committed. */
    private static class OpenConsumer extends MockConsumer<String, String> {
        int closes;

        OpenConsumer() {
            super("earliest");
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private OpenConsumer parked(String... payloads) {
        var kafka = new OpenConsumer();
        kafka.updatePartitions(KafkaConfig.DEAD_LETTER_TOPIC, List.of(new PartitionInfo(KafkaConfig.DEAD_LETTER_TOPIC, 0, null, new Node[0], new Node[0])));
        kafka.updateBeginningOffsets(Map.of(PARTITION, 0L));
        kafka.updateEndOffsets(Map.of(PARTITION, (long) payloads.length));
        // The mock accepts records only for a partition that is assigned, which the service does when it replays
        kafka.schedulePollTask(() -> {
            for (int offset = 0; offset < payloads.length; offset++) {
                kafka.addRecord(new ConsumerRecord<>(KafkaConfig.DEAD_LETTER_TOPIC, 0, offset, null, payloads[offset]));
            }
        });
        return kafka;
    }

    private DltReplayService service(OpenConsumer kafka) {
        return new DltReplayService(catalogConsumer, () -> kafka);
    }

    private long committed(OpenConsumer kafka) {
        var offset = kafka.committed(Set.of(PARTITION)).get(PARTITION);
        return offset == null ? 0 : offset.offset();
    }

    @Test
    void replaysTheParkedChangesOldestFirstAndRemembersHowFarItGot() {
        var kafka = parked("a", "b", "c");

        DltReplayResponse result = service(kafka).replay(100);

        assertEquals(new DltReplayResponse(3, 0, 0, false), result);
        InOrder order = inOrder(catalogConsumer);
        order.verify(catalogConsumer).onCatalogChanged("a");
        order.verify(catalogConsumer).onCatalogChanged("b");
        order.verify(catalogConsumer).onCatalogChanged("c");
        assertEquals(3, committed(kafka));
    }

    @Test
    void stopsAtAChangeThatFailsAgainAndKeepsItAndTheOnesAfterIt() {
        var kafka = parked("a", "b", "c");
        doThrow(new IllegalStateException("catalog down")).when(catalogConsumer).onCatalogChanged("b");

        DltReplayResponse result = service(kafka).replay(100);

        assertEquals(new DltReplayResponse(1, 0, 2, true), result);
        assertEquals(1, committed(kafka));
        org.mockito.Mockito.verify(catalogConsumer, org.mockito.Mockito.never()).onCatalogChanged("c");
    }

    @Test
    void passesOverAnUnreadableChangeInsteadOfBlockingOnItForever() {
        var kafka = parked("bad", "good");
        doThrow(new InvalidChangeEventException("unreadable", null)).when(catalogConsumer).onCatalogChanged("bad");

        DltReplayResponse result = service(kafka).replay(100);

        assertEquals(new DltReplayResponse(1, 1, 0, false), result);
        assertEquals(2, committed(kafka));
    }

    @Test
    void replaysNoMoreThanTheLimit() {
        var kafka = parked("a", "b", "c");

        DltReplayResponse result = service(kafka).replay(2);

        assertEquals(new DltReplayResponse(2, 0, 1, false), result);
        assertEquals(2, committed(kafka));
    }

    @Test
    void reportsHowManyChangesAreStillWaiting() {
        var kafka = parked("a", "b");

        assertEquals(2, service(kafka).status().pending());
    }

    @Test
    void doesNothingWhenNothingWasEverParked() {
        var kafka = new OpenConsumer();

        DltReplayResponse result = service(kafka).replay(100);

        assertEquals(new DltReplayResponse(0, 0, 0, false), result);
        assertEquals(0, service(kafka).status().pending());
        assertFalse(result.blocked());
        assertEquals(2, kafka.closes);
    }
}
