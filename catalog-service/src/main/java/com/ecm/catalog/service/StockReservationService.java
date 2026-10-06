package com.ecm.catalog.service;

import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.entity.StockReservation;
import com.ecm.catalog.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.ReserveStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.StockItem;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.StockReservationRepository;
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
 * Takes the stock of an order off product_variants.quantity and gives it back. An order holds at most one
 * reservation, so a command that is delivered twice, or a release for an order that reserved nothing, changes nothing.
 */
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final ProductVariantRepository productVariantRepository;
    private final StockReservationRepository stockReservationRepository;

    /** Reserves every line or none; returns why it could not, or empty when the stock is reserved. */
    @Transactional
    public Optional<String> reserve(ReserveStockCommand command) {
        // 1. A reservation that already exists means this command was handled before
        if (stockReservationRepository.existsById(command.orderId())) {
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

        // 3. Take the stock and record the reservation
        requested.forEach((variantId, quantity) -> variants.get(variantId).setQuantity(variants.get(variantId).getQuantity() - quantity));
        productVariantRepository.saveAll(variants.values());
        stockReservationRepository.save(StockReservation.builder().orderId(command.orderId()).build());
        return Optional.empty();
    }

    @Transactional
    public void release(ReleaseStockCommand command) {
        // 1. Nothing to give back unless the order holds a reservation
        if (stockReservationRepository.deleteByOrderIdReturningCount(command.orderId()) == 0) {
            return;
        }

        // 2. Give back what the order took
        Map<UUID, Integer> returned = totalByVariant(command.items());
        List<ProductVariant> variants = productVariantRepository.findAllByIdForUpdate(returned.keySet());
        variants.forEach(variant -> variant.setQuantity(variant.getQuantity() + returned.get(variant.getId())));
        productVariantRepository.saveAll(variants);
    }

    private static Map<UUID, Integer> totalByVariant(List<StockItem> items) {
        return items.stream().collect(Collectors.toMap(StockItem::productVariantId, StockItem::quantity, Integer::sum));
    }
}
