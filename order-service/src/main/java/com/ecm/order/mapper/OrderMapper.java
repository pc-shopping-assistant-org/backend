package com.ecm.order.mapper;

import com.ecm.order.dto.response.AdminOrderDetailResponse;
import com.ecm.order.dto.response.AdminOrderSummaryResponse;
import com.ecm.order.dto.response.InvoiceDetailResponse;
import com.ecm.order.dto.response.InvoiceSummaryResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderItemResponse;
import com.ecm.order.dto.response.OrderStatusResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.dto.response.PaymentResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "lineTotal", expression = "java(item.getUnitPrice() * item.getQuantity() - item.getDiscountAmount())")
    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderItemResponse> toItemResponses(List<OrderItem> items);

    OrderSummaryResponse toSummary(Order order, int itemCount, String firstProductName);

    AdminOrderSummaryResponse toAdminSummary(Order order, int itemCount, String firstProductName);

    @Mapping(target = "invoiceDate", source = "deliveredAt")
    InvoiceSummaryResponse toInvoiceSummary(Order order);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "invoiceDate", source = "order.deliveredAt")
    InvoiceDetailResponse toInvoiceDetail(Order order, List<OrderItem> items);

    OrderStatusResponse toStatusResponse(Order order, String cancellationReason);

    OrderDetailResponse toDetail(Order order, List<OrderItem> items, List<PaymentResponse> payments, String cancellationReason);

    AdminOrderDetailResponse toAdminDetail(Order order, List<OrderItem> items, List<PaymentResponse> payments, List<OrderStatusHistory> statusHistory);
}
