package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.order.dto.request.AdminInvoiceSearchRequest;
import com.ecm.order.dto.response.InvoiceDetailResponse;
import com.ecm.order.dto.response.InvoiceSummaryResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** The invoices of the shop: completed orders (UC-ADM-INV-001, 002). */
@Service
@RequiredArgsConstructor
public class InvoiceQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Instant NO_UPPER_BOUND = Instant.parse("9999-12-31T00:00:00Z");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;

    /** The invoices, the latest first: found by invoice number or customer name and filtered by invoice date. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryResponse> searchInvoices(AdminInvoiceSearchRequest filter, int page, int size) {
        // 1. Reject a page or a period that makes no sense
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || filter.invoiceFrom() != null && filter.invoiceTo() != null && !filter.invoiceFrom().isBefore(filter.invoiceTo())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        // 2. One page of completed orders
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
}
