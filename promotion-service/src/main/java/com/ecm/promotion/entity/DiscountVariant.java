package com.ecm.promotion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "discount_variants")
@IdClass(DiscountVariantId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountVariant {

    @Id
    @Column(name = "discount_id", nullable = false)
    private UUID discountId;

    /**
     * ref -> Catalog Service (product_variants.id), no cross-DB FK.
     */
    @Id
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;
}
