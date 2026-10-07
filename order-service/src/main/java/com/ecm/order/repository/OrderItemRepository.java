package com.ecm.order.repository;

import com.ecm.order.dto.response.TopVariantResponse;
import com.ecm.order.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    List<OrderItem> findByOrderIdIn(Collection<UUID> orderIds);

    boolean existsByProductVariantId(UUID productVariantId);

    /**
     * Best-selling variants among the orders completed in the period. The name, sku and label are the
     * snapshot of the order lines; if a variant was renamed in between, the greatest value is shown.
     */
    @Query("SELECT new com.ecm.order.dto.response.TopVariantResponse(i.productVariantId, MAX(i.productName), MAX(i.sku), " +
            "MAX(i.variantLabel), SUM(i.quantity), SUM(i.quantity * i.unitPrice - i.discountAmount)) " +
            "FROM OrderItem i JOIN Order o ON o.id = i.orderId " +
            "WHERE o.status = com.ecm.order.entity.OrderStatus.COMPLETED AND o.deliveredAt >= :from AND o.deliveredAt < :to " +
            "GROUP BY i.productVariantId ORDER BY SUM(i.quantity) DESC, i.productVariantId")
    List<TopVariantResponse> findTopVariants(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
