package com.ecm.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "cart_items")
@IdClass(CartItemId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    /**
     * ref -> Catalog Service (product_variants.id), no cross-DB FK.
     */
    @Id
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
