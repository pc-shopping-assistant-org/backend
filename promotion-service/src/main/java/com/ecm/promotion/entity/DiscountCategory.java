package com.ecm.promotion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "discount_categories")
@IdClass(DiscountCategoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountCategory {

    @Id
    @Column(name = "discount_id", nullable = false)
    private UUID discountId;

    /**
     * ref -> Catalog Service (categories.id), no cross-DB FK.
     */
    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;
}
