package com.ecm.payment.messaging.kafka;

public final class KafkaTopics {

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    public static final String ORDER_CANCELLED = "order.cancelled";

    private KafkaTopics() {
    }
}
