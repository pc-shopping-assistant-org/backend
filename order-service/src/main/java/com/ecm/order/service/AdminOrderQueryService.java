package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.response.AdminOrderDetailResponse;
import com.ecm.order.dto.response.AdminOrderSummaryResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.helper.PaymentLookup;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.OrderStatusHistoryRepository;
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

/** What the shop reads about the orders of every customer (UC-ADM-ORD-001, 002). */
@Service
@RequiredArgsConstructor
public class AdminOrderQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Instant NO_UPPER_BOUND = Instant.parse("9999-12-31T00:00:00Z");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentLookup paymentLookup;
    private final OrderMapper orderMapper;

    /** The orders of every customer, newest first (UC-ADM-ORD-001). */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> searchOrders(AdminOrderSearchRequest filter, int page, int size) {
        // 1. Reject a page or a period that makes no sense
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || filter.createdFrom() != null && filter.createdTo() != null && !filter.createdFrom().isBefore(filter.createdTo())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        // 2. One page of orders
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Order> orders = orderRepository.searchAdminOrders(filter.status(), filter.customerId(),
                filter.createdFrom() == null ? Instant.EPOCH : filter.createdFrom(),
                filter.createdTo() == null ? NO_UPPER_BOUND : filter.createdTo(), keyword, pageable);

        // 3. The lines of the whole page in one query
        Map<UUID, List<OrderItem>> itemsByOrder = orderItemRepository.findByOrderIdIn(orders.getContent().stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return PageResponse.of(orders.map(order -> {
            List<OrderItem> lines = itemsByOrder.getOrDefault(order.getId(), List.of());
            return orderMapper.toAdminSummary(order, lines.size(), lines.isEmpty() ? null : lines.getFirst().getProductName());
        }));
    }

    /** Any order as it was placed, with its status history (UC-ADM-ORD-002); every amount comes from the order own snapshot. */
    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrderDetail(UUID orderId, String bearerToken) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return orderMapper.toAdminDetail(order, orderItemRepository.findByOrderId(orderId),
                paymentLookup.forOrder(orderId, bearerToken), historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
    }
}
