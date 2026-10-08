package com.ecm.catalog.messaging.kafka;

public final class KafkaTopics {

    public static final String STOCK_RESERVED = "stock.reserved";
    public static final String STOCK_RESERVE_FAILED = "stock.reserve-failed";
    public static final String STOCK_RELEASED = "stock.released";

    private KafkaTopics() {
    }
}
