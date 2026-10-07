package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.order.dto.request.AdminInvoiceSearchRequest;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.response.AdminOrderDetailResponse;
import com.ecm.order.dto.response.AdminOrderSummaryResponse;
import com.ecm.order.dto.response.CursorPageResponse;
import com.ecm.order.dto.response.InvoiceDetailResponse;
import com.ecm.order.dto.response.InvoiceSummaryResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderStatusResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.OrderStatusHistory;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** What is read about orders: by the customer, their own history and search (UC-ORD-004, 005), status (002) and detail (006); by the shop, every order (UC-ADM-ORD-001, 002). */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Instant NO_UPPER_BOUND = Instant.parse("9999-12-31T00:00:00Z");
    private static final String CURSOR_SEPARATOR = "|";
    private static final Pattern UNDASHED_UUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern DASHED_UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final UUID NO_ORDER_ID = new UUID(0L, 0L);
    private static final UUID MAX_ID = new UUID(-1L, -1L);
    private static final Instant AFTER_EVERY_ORDER = Instant.parse("9999-12-31T00:00:00Z");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentLookup paymentLookup;
    private final OrderMapper orderMapper;

    /**
     * The orders of the customer, newest first. A keyword, which is an order id (with or without dashes) or an
     * invoice number, narrows the list to the order it names.
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<OrderSummaryResponse> getOrders(UUID customerId, OrderStatus status, String keyword, String cursor, Integer limit) {
        // 1. Read one row more than the page to learn whether another page follows
        int pageSize = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        UUID keywordOrderId = parseOrderId(trimmedKeyword);
        Cursor start = cursor == null || cursor.isBlank() ? new Cursor(AFTER_EVERY_ORDER, MAX_ID) : Cursor.decode(cursor);
        List<Order> rows = orderRepository.findCustomerPage(customerId, status == null, status, trimmedKeyword.isEmpty(),
                keywordOrderId == null ? NO_ORDER_ID : keywordOrderId, trimmedKeyword.toUpperCase(Locale.ROOT),
                start.createdAt(), start.id(), PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<Order> page = hasNext ? rows.subList(0, pageSize) : rows;

        // 2. The lines of the whole page in one query
        Map<UUID, List<OrderItem>> itemsByOrder = orderItemRepository.findByOrderIdIn(page.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<OrderSummaryResponse> items = page.stream().map(order -> {
            List<OrderItem> lines = itemsByOrder.getOrDefault(order.getId(), List.of());
            return orderMapper.toSummary(order, lines.size(), lines.isEmpty() ? null : lines.getFirst().getProductName());
        }).toList();
        String nextCursor = hasNext ? new Cursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode() : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext, items.size());
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse getStatus(UUID orderId, UUID customerId) {
        Order order = findOwnedOrder(orderId, customerId);
        return orderMapper.toStatusResponse(order, cancellationReason(order));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(UUID orderId, UUID customerId, String bearerToken) {
        // 1. Only the owner may read the order; for anyone else it does not exist
        Order order = findOwnedOrder(orderId, customerId);

        // 2. The snapshot lines, and the payment attempts, which live in the Payment Service
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        return orderMapper.toDetail(order, items, paymentLookup.forOrder(order.getId(), bearerToken), cancellationReason(order));
    }

    /** Why the order was cancelled, for the customer to read; null when it is not cancelled or no reason was given. */
    private String cancellationReason(Order order) {
        if (order.getStatus() != OrderStatus.CANCELLED) {
            return null;
        }
        return historyRepository.findFirstByOrderIdAndToStatusOrderByCreatedAtDesc(order.getId(), OrderStatus.CANCELLED)
                .map(OrderStatusHistory::getReason).orElse(null);
    }

    /** The orders of every customer for the shop, newest first (UC-ADM-ORD-001). */
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

    /** The invoices of the shop, the latest first (UC-ADM-INV-001, 002): completed orders, found by invoice number or customer name and filtered by invoice date. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryResponse> searchInvoices(AdminInvoiceSearchRequest filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || filter.invoiceFrom() != null && filter.invoiceTo() != null && !filter.invoiceFrom().isBefore(filter.invoiceTo())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deliveredAt", "id"));
        return PageResponse.of(orderRepository.searchInvoices(keyword,
                filter.invoiceFrom() == null ? Instant.EPOCH : filter.invoiceFrom(),
                filter.invoiceTo() == null ? NO_UPPER_BOUND : filter.invoiceTo(), pageable).map(orderMapper::toInvoiceSummary));
    }

    /** An invoice exists only for a completed order, so any other order is not found here. */
    @Transactional(readOnly = true)
    public InvoiceDetailResponse getInvoice(UUID orderId) {
        Order order = orderRepository.findById(orderId).filter(candidate -> candidate.getStatus() == OrderStatus.COMPLETED)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", orderId));
        return orderMapper.toInvoiceDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    private Order findOwnedOrder(UUID orderId, UUID customerId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private static UUID parseOrderId(String keyword) {
        if (DASHED_UUID.matcher(keyword).matches()) {
            return UUID.fromString(keyword);
        }
        if (UNDASHED_UUID.matcher(keyword).matches()) {
            return UUID.fromString(keyword.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
        }
        return null;
    }

    /** Where the previous page stopped: the creation time and id of its last order. */
    private record Cursor(Instant createdAt, UUID id) {

        String encode() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString((createdAt + CURSOR_SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\" + CURSOR_SEPARATOR);
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException | DateTimeParseException ex) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Invalid cursor");
            }
        }
    }
}
