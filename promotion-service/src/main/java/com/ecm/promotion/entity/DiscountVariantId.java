package com.ecm.promotion.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DiscountVariantId implements Serializable {
    private UUID discountId;
    private UUID variantId;
}
