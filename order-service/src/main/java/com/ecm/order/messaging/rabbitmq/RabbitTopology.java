package com.ecm.order.messaging.rabbitmq;

/**
 * Exchange/queue/routing-key names for the RabbitMQ topology shared with catalog-service (duplicated there, not shared as code).
 */
public final class RabbitTopology {

    public static final String COMMANDS_EXCHANGE = "catalog.commands.exchange";
    public static final String STOCK_COMMANDS_QUEUE = "catalog.stock-commands";
    public static final String ROUTING_KEY_RESERVE = "stock.reserve";
    public static final String ROUTING_KEY_RELEASE = "stock.release";

    public static final String DEAD_LETTER_EXCHANGE = "catalog.commands.dlx";
    public static final String STOCK_COMMANDS_DLQ = "catalog.stock-commands.dlq";

    private RabbitTopology() {
    }
}
