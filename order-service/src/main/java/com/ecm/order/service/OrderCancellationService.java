package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Cancels an order and queues what follows from it: UC-ORD-003. */
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    /** A customer can cancel only while the shop has not confirmed the order. */
    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE = Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_CONFIRMATION);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusService orderStatusService;
    private final OrderOutbox orderOutbox;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderDetailResponse cancelByCustomer(UUID orderId, UUID customerId, String reason) {
        // 1. Lock the order so a payment result or a second cancel cannot change it meanwhile; it must be the customer own
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(candidate -> candidate.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 2. Only an order the shop has not confirmed yet can be cancelled
        if (!CUSTOMER_CANCELLABLE.contains(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }

        // 3. Cancel it, with the history row, and queue the follow-up: give the stock back, tell the other services
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        Order cancelled = orderStatusService.transition(order, OrderStatus.CANCELLED, null, cleanReason);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        orderOutbox.releaseStock(cancelled, items);
        orderOutbox.orderCancelled(cancelled, cleanReason);
        return orderMapper.toDetail(cancelled, items, List.of());
    }
}
