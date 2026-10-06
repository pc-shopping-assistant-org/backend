package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order management for employees. Kept as it was while the customer side is rebuilt; the admin use cases
 * (UC-ADM-ORD-001..004) are reworked in their own phase.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final Instant NO_UPPER_BOUND = Instant.parse("9999-12-31T00:00:00Z");
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusService orderStatusService;
    private final OrderOutbox orderOutbox;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getOrders(AdminOrderSearchRequest filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || filter.createdFrom() != null && filter.createdTo() != null && !filter.createdFrom().isBefore(filter.createdTo())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = orderRepository.searchAdminOrders(filter.status(), filter.customerId(),
                filter.createdFrom() == null ? Instant.EPOCH : filter.createdFrom(),
                filter.createdTo() == null ? NO_UPPER_BOUND : filter.createdTo(), keyword, pageable);
        Map<UUID, List<OrderItem>> itemsByOrder = orderItemRepository.findByOrderIdIn(orders.getContent().stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return PageResponse.of(orders.map(order -> {
            List<OrderItem> lines = itemsByOrder.getOrDefault(order.getId(), List.of());
            return orderMapper.toSummary(order, lines.size(), lines.isEmpty() ? null : lines.getFirst().getProductName());
        }));
    }

    @Transactional
    public OrderDetailResponse updateStatus(UUID orderId, OrderStatus status, UUID employeeId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!isValidTransition(order.getStatus(), status)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        Order changed = orderStatusService.transition(order, status, employeeId, null);
        if (status == OrderStatus.COMPLETED) {
            changed.setDeliveredAt(Instant.now());
            orderRepository.save(changed);
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (status == OrderStatus.CANCELLED) {
            orderOutbox.releaseStock(changed, items);
            orderOutbox.orderCancelled(changed, null);
        }
        return orderMapper.toDetail(changed, items, List.of());
    }

    private boolean isValidTransition(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case PENDING_CONFIRMATION -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SHIPPING || next == OrderStatus.CANCELLED;
            case SHIPPING -> next == OrderStatus.COMPLETED;
            default -> false;
        };
    }
}
