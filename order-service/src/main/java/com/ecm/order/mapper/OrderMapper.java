package com.ecm.order.mapper;

import com.ecm.order.dto.response.OrderItemResponse;
import com.ecm.order.dto.response.OrderResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderItemResponse toItemResponse(OrderItem item);

    OrderResponse toResponse(Order order, List<OrderItem> items);
}
