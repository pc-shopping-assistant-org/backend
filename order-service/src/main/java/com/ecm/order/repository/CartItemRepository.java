package com.ecm.order.repository;

import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.CartItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByCartId(UUID cartId);

    Optional<CartItem> findByCartIdAndVariantId(UUID cartId, UUID variantId);

    @Modifying
    @Query("delete from CartItem i where i.cartId = :cartId and i.variantId = :variantId")
    void deleteItem(@Param("cartId") UUID cartId, @Param("variantId") UUID variantId);

    @Modifying
    @Query("delete from CartItem i where i.cartId = :cartId")
    void deleteByCartId(@Param("cartId") UUID cartId);
}
