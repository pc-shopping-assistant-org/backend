package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.order.dto.response.CursorPageResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderStatusResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.OrderStatusHistory;
import com.ecm.order.helper.PaymentLookup;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.OrderStatusHistoryRepository;
import com.ecm.order.util.OrderCursor;
import com.ecm.order.util.OrderKeywords;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** What the customer reads about their own orders: history and search (UC-ORD-004, 005), status (002) and detail (006). The shop reads through {@link AdminOrderQueryService}. */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentLookup paymentLookup;
    private final OrderMapper orderMapper;

    /** The same list as {@link #getOrders}, as a numbered page (zero-based) with its number and the totals. */
    @Transactional(readOnly = true)
    public CursorPageResponse<OrderSummaryResponse> getOrdersPage(UUID customerId, OrderStatus status, String keyword, int page, Integer limit) {
        // 1. Reject a page that makes no sense
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        // 2. One page of orders, with the total
        String trimmedKeyword = trimmed(keyword);
        UUID keywordOrderId = OrderKeywords.orderId(trimmedKeyword);
        Page<Order> orders = orderRepository.findCustomerNumberedPage(customerId, status == null, status, trimmedKeyword.isEmpty(),
                keywordOrderId == null ? OrderKeywords.NO_ORDER_ID : keywordOrderId, trimmedKeyword.toUpperCase(Locale.ROOT),
                PageRequest.of(page, pageSize(limit)));
        List<OrderSummaryResponse> items = summaries(orders.getContent());
        return new CursorPageResponse<>(items, null, orders.hasNext(), items.size(),
                orders.getNumber(), orders.getTotalElements(), orders.getTotalPages());
    }

    /**
     * The orders of the customer, newest first. A keyword, which is an order id (with or without dashes) or an
     * invoice number, narrows the list to the order it names.
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<OrderSummaryResponse> getOrders(UUID customerId, OrderStatus status, String keyword, String cursor, Integer limit) {
        // 1. Read one row more than the page to learn whether another page follows
        int pageSize = pageSize(limit);
        String trimmedKeyword = trimmed(keyword);
        UUID keywordOrderId = OrderKeywords.orderId(trimmedKeyword);
        OrderCursor start = cursor == null || cursor.isBlank() ? OrderCursor.START : OrderCursor.decode(cursor);
        List<Order> rows = orderRepository.findCustomerPage(customerId, status == null, status, trimmedKeyword.isEmpty(),
                keywordOrderId == null ? OrderKeywords.NO_ORDER_ID : keywordOrderId, trimmedKeyword.toUpperCase(Locale.ROOT),
                start.createdAt(), start.id(), PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<Order> page = hasNext ? rows.subList(0, pageSize) : rows;

        // 2. The lines of the whole page
        List<OrderSummaryResponse> items = summaries(page);
        String nextCursor = hasNext ? new OrderCursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode() : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext, items.size());
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(UUID orderId, UUID customerId, String bearerToken) {
        // 1. Only the owner may read the order; for anyone else it does not exist
        Order order = findOwnedOrder(orderId, customerId);

        // 2. The snapshot lines, and the payment attempts, which live in the Payment Service
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        return orderMapper.toDetail(order, items, paymentLookup.forOrder(order.getId(), bearerToken), cancellationReason(order));
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse getStatus(UUID orderId, UUID customerId) {
        Order order = findOwnedOrder(orderId, customerId);
        return orderMapper.toStatusResponse(order, cancellationReason(order));
    }

    private static int pageSize(Integer limit) {
        return limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private static String trimmed(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    /** The orders of a page with their line count and first product, the lines read in one query. */
    private List<OrderSummaryResponse> summaries(List<Order> page) {
        Map<UUID, List<OrderItem>> itemsByOrder = orderItemRepository.findByOrderIdIn(page.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return page.stream().map(order -> {
            List<OrderItem> lines = itemsByOrder.getOrDefault(order.getId(), List.of());
            return orderMapper.toSummary(order, lines.size(), lines.isEmpty() ? null : lines.getFirst().getProductName());
        }).toList();
    }

    private Order findOwnedOrder(UUID orderId, UUID customerId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    /** Why the order was cancelled, for the customer to read; null when it is not cancelled or no reason was given. */
    private String cancellationReason(Order order) {
        if (order.getStatus() != OrderStatus.CANCELLED) {
            return null;
        }
        return historyRepository.findFirstByOrderIdAndToStatusOrderByCreatedAtDesc(order.getId(), OrderStatus.CANCELLED)
                .map(OrderStatusHistory::getReason).orElse(null);
    }
}
