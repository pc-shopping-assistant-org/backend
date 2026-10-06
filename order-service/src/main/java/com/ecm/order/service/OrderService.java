package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderStatusResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Moves an order through its life: cancelling it (UC-ORD-003 by the customer, UC-ADM-ORD-004 by the shop) and the steps the shop takes (UC-ADM-ORD-003). */
@Service
@RequiredArgsConstructor
public class OrderService {

    /** A customer can cancel only while the shop has not confirmed the order. */
    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE = Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_CONFIRMATION);

    /** The shop can still cancel after confirming, up to delivery, for a problem with the order or its shipment. */
    private static final Set<OrderStatus> EMPLOYEE_CANCELLABLE = Set.of(
            OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED, OrderStatus.SHIPPING);

    /**
     * What an employee moves an order on to. The first step, PENDING_PAYMENT to PENDING_CONFIRMATION, belongs to the
     * payment result and cancelling has its own use case, so neither is here.
     */
    private static final Map<OrderStatus, OrderStatus> NEXT_STATUS = Map.of(
            OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED,
            OrderStatus.CONFIRMED, OrderStatus.SHIPPING,
            OrderStatus.SHIPPING, OrderStatus.COMPLETED);

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

        // 2. Cancel it
        String cleanReason = cleanReason(reason);
        List<OrderItem> items = cancel(order, CUSTOMER_CANCELLABLE, null, cleanReason);
        return orderMapper.toDetail(order, items, List.of(), cleanReason);
    }

    @Transactional
    public OrderStatusResponse cancelByEmployee(UUID orderId, UUID employeeId, String reason) {
        // 1. Lock the order so a payment result or a customer cancel cannot change it meanwhile
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 2. Cancel it, recording who did
        String cleanReason = cleanReason(reason);
        cancel(order, EMPLOYEE_CANCELLABLE, employeeId, cleanReason);
        return orderMapper.toStatusResponse(order, cleanReason);
    }

    @Transactional
    public OrderStatusResponse updateStatus(UUID orderId, OrderStatus status, UUID employeeId) {
        // 1. Lock the order so a payment result or a cancel cannot change it meanwhile
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 2. Only the next step of the order is allowed
        if (NEXT_STATUS.get(order.getStatus()) != status) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }

        // 3. Completing the order is the delivery, and its date is the invoice date
        if (status == OrderStatus.COMPLETED) {
            order.setDeliveredAt(Instant.now());
        }
        return orderMapper.toStatusResponse(orderStatusService.transition(order, status, employeeId, null), null);
    }

    private List<OrderItem> cancel(Order order, Set<OrderStatus> cancellable, UUID changedBy, String reason) {
        // 1. Only an order in one of these statuses can be cancelled
        if (!cancellable.contains(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }

        // 2. Cancel it, with the history row, and queue the follow-up: give the stock back, tell the other services
        Order cancelled = orderStatusService.transition(order, OrderStatus.CANCELLED, changedBy, reason);
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        orderOutbox.releaseStock(cancelled, items);
        orderOutbox.orderCancelled(cancelled, reason);
        return items;
    }

    private static String cleanReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
