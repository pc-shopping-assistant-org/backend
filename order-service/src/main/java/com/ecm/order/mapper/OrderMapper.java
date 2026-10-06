package com.ecm.order.mapper;

import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderItemResponse;
import com.ecm.order.dto.response.OrderStatusResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.dto.response.PaymentResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "lineTotal", expression = "java(item.getUnitPrice() * item.getQuantity() - item.getDiscountAmount())")
    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderItemResponse> toItemResponses(List<OrderItem> items);

    OrderSummaryResponse toSummary(Order order, int itemCount, String firstProductName);

    OrderStatusResponse toStatusResponse(Order order);

    OrderDetailResponse toDetail(Order order, List<OrderItem> items, List<PaymentResponse> payments);
}
