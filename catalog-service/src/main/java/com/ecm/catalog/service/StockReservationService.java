package com.ecm.catalog.service;

import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.messaging.kafka.producer.StockEventProducer;
import com.ecm.catalog.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.ReserveStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.StockItem;
import com.ecm.catalog.repository.OutboxEventRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Takes the stock of an order off product_variants.quantity and gives it back. What became of the stock of an order is
 * read from the replies this service queued in its outbox: an order holds its stock from {@code StockReservedEvent}
 * until {@code StockReleasedEvent}, so a command delivered twice, or a release for an order that reserved nothing,
 * changes nothing.
 */
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final ProductVariantRepository productVariantRepository;
    private final OutboxEventRepository outboxEventRepository;

    /** Reserves every line or none; returns why it could not, or empty when the stock is reserved (the caller then queues the reply). */
    @Transactional
    public Optional<String> reserve(ReserveStockCommand command) {
        // 1. A reservation that already exists means this command was handled before
        if (outboxEventRepository.existsByAggregateIdAndEventType(command.orderId(), StockEventProducer.STOCK_RESERVED_EVENT)) {
            return Optional.empty();
        }

        // 2. Every line must exist and have enough stock
        Map<UUID, Integer> requested = totalByVariant(command.items());
        Map<UUID, ProductVariant> variants = productVariantRepository.findAllByIdForUpdate(requested.keySet()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<UUID> unavailable = requested.entrySet().stream()
                .filter(line -> !variants.containsKey(line.getKey()) || variants.get(line.getKey()).getQuantity() < line.getValue())
                .map(Map.Entry::getKey).toList();
        if (!unavailable.isEmpty()) {
            return Optional.of("Insufficient stock for variants " + unavailable);
        }

        // 3. Take the stock
        requested.forEach((variantId, quantity) -> variants.get(variantId).setQuantity(variants.get(variantId).getQuantity() - quantity));
        productVariantRepository.saveAll(variants.values());
        return Optional.empty();
    }

    /** Returns true when stock was given back, so the caller queues the reply; false when the order held none. */
    @Transactional
    public boolean release(ReleaseStockCommand command) {
        // 1. Nothing to give back unless the order holds its stock
        boolean reserved = outboxEventRepository.existsByAggregateIdAndEventType(command.orderId(), StockEventProducer.STOCK_RESERVED_EVENT);
        boolean released = outboxEventRepository.existsByAggregateIdAndEventType(command.orderId(), StockEventProducer.STOCK_RELEASED_EVENT);
        if (!reserved || released) {
            return false;
        }

        // 2. Give back what the order took
        Map<UUID, Integer> returned = totalByVariant(command.items());
        List<ProductVariant> variants = productVariantRepository.findAllByIdForUpdate(returned.keySet());
        variants.forEach(variant -> variant.setQuantity(variant.getQuantity() + returned.get(variant.getId())));
        productVariantRepository.saveAll(variants);
        return true;
    }

    private static Map<UUID, Integer> totalByVariant(List<StockItem> items) {
        return items.stream().collect(Collectors.toMap(StockItem::productVariantId, StockItem::quantity, Integer::sum));
    }
}
