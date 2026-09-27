package com.ecm.catalog.messaging.rabbitmq.consumer;

import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.messaging.InboxGuard;
import com.ecm.catalog.messaging.event.StockReserveFailedEvent;
import com.ecm.catalog.messaging.event.StockReservedEvent;
import com.ecm.catalog.messaging.kafka.producer.StockEventProducer;
import com.ecm.catalog.messaging.rabbitmq.RabbitTopology;
import com.ecm.catalog.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.ReserveStockCommand;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single consumer of stock commands issued by order-service's saga orchestrator.
 * Both command types share one queue ({@code catalog.stock-commands}), so this uses a
 * class-level listener with one {@code @RabbitHandler} per payload type rather than two
 * separate {@code @RabbitListener} methods — two listeners on the same queue would be
 * competing consumers (RabbitMQ round-robins by consumer, not by message type).
 */
@Component
@RabbitListener(queues = RabbitTopology.STOCK_COMMANDS_QUEUE)
@RequiredArgsConstructor
public class StockCommandConsumer {

    private static final String CONSUMER_NAME = "catalog-service.stock-command-consumer";

    private final InboxGuard inboxGuard;
    private final ProductVariantRepository productVariantRepository;
    private final StockEventProducer stockEventProducer;

    @RabbitHandler
    @Transactional
    public void onReserveStock(ReserveStockCommand command) {
        if (inboxGuard.alreadyProcessed(command.commandId(), CONSUMER_NAME)) {
            return;
        }
        ProductVariant variant = productVariantRepository.findById(command.productVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", command.productVariantId()));

        // 1. Not enough stock — tell the orchestrator to cancel the order.
        if (variant.getQuantity() < command.quantity()) {
            stockEventProducer.publishStockReserveFailed(new StockReserveFailedEvent(
                    UUID.randomUUID(), command.orderId(), command.productVariantId(), "Insufficient stock"));
            return;
        }

        // 2. Reserve by decrementing on-hand quantity, then confirm to the orchestrator.
        variant.setQuantity(variant.getQuantity() - command.quantity());
        productVariantRepository.save(variant);
        stockEventProducer.publishStockReserved(new StockReservedEvent(
                UUID.randomUUID(), command.orderId(), command.productVariantId()));
    }

    @RabbitHandler
    @Transactional
    public void onReleaseStock(ReleaseStockCommand command) {
        if (inboxGuard.alreadyProcessed(command.commandId(), CONSUMER_NAME)) {
            return;
        }
        ProductVariant variant = productVariantRepository.findById(command.productVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", command.productVariantId()));

        // 1. Give the reserved quantity back to on-hand stock.
        variant.setQuantity(variant.getQuantity() + command.quantity());
        productVariantRepository.save(variant);
    }
}
