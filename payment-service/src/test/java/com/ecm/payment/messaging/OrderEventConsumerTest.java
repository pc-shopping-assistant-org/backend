package com.ecm.payment.messaging;

import com.ecm.payment.messaging.event.OrderCancelledEvent;
import com.ecm.payment.messaging.kafka.OrderEventConsumer;
import com.ecm.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderEventConsumerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();
    private final InboxGuard inbox = mock(InboxGuard.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final OrderEventConsumer consumer = new OrderEventConsumer(inbox, paymentService, mapper);

    @Test
    void aCancelledOrderCancelsItsPendingPayments() throws Exception {
        consumer.onOrderCancelled(mapper.writeValueAsString(new OrderCancelledEvent(EVENT_ID, ORDER_ID, "reason")));

        verify(paymentService).cancelPendingPayments(ORDER_ID);
    }

    @Test
    void aRedeliveredEventIsIgnored() throws Exception {
        when(inbox.alreadyProcessed(eq(EVENT_ID), anyString())).thenReturn(true);

        consumer.onOrderCancelled(mapper.writeValueAsString(new OrderCancelledEvent(EVENT_ID, ORDER_ID, "reason")));

        verifyNoInteractions(paymentService);
    }
}
