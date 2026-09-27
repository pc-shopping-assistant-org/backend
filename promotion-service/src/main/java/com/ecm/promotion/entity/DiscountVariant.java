package com.ecm.promotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    /** ref -> Catalog Service (product_variants.id), no cross-DB FK. */
    @Id
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;
}
