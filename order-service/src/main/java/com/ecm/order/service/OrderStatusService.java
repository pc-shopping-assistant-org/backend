package com.ecm.order.service;

import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.OrderStatusHistory;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Changes the status of an order and writes the history row in the same transaction. Whether a change is allowed is for the caller to check. */
@Component
@RequiredArgsConstructor
public class OrderStatusService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;

    /** Records the status an order starts with, which has no previous status. */
    public void recordInitial(Order order) {
        historyRepository.save(OrderStatusHistory.builder().orderId(order.getId()).toStatus(order.getStatus()).build());
    }

    /** {@code changedBy} is the employee who made the change, or null when the customer or the system did. */
    public Order transition(Order order, OrderStatus to, UUID changedBy, String reason) {
        OrderStatus from = order.getStatus();
        order.setStatus(to);
        if (changedBy != null) {
            order.setUpdatedBy(changedBy);
        }
        Order saved = orderRepository.save(order);
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId()).fromStatus(from).toStatus(to).changedBy(changedBy).reason(reason).build());
        return saved;
    }
}
