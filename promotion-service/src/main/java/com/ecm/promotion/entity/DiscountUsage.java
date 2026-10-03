package com.ecm.promotion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "discount_usages")
@IdClass(DiscountUsageId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscountUsage {
    @Id
    @Column(name = "discount_id")
    private UUID discountId;
    @Id
    @Column(name = "checkout_key", length = 100)
    private String checkoutKey;
}
