package com.ecm.order.repository;

import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.CartItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByCartId(UUID cartId);
}
