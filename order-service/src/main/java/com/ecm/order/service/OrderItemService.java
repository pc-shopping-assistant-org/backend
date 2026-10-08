package com.ecm.order.service;

import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.dto.response.OrderItemDetailResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderItemService {

    private static final String RESOURCE_ORDER_ITEM = "Order item";

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    /**
     * Returns the order line only to the customer who owns it; anyone else gets "not found" so
     * the existence of other customers' order lines is not revealed.
     */
    @Transactional(readOnly = true)
    public OrderItemDetailResponse getOwnedOrderItem(UUID orderItemId, Authentication authentication) {
        // 1. Load the order line and its order.
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_ORDER_ITEM, orderItemId));
        Order order = orderRepository.findById(item.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_ORDER_ITEM, orderItemId));

        // 2. Only the owner may see it.
        UUID accountId = CurrentUser.accountId(authentication);
        if (accountId == null || !accountId.equals(order.getCustomerId())) {
            throw new ResourceNotFoundException(RESOURCE_ORDER_ITEM, orderItemId);
        }

        // 3. Map to the cross-service view.
        return new OrderItemDetailResponse(item.getId(), order.getId(), order.getStatus(), item.getProductVariantId());
    }
}
