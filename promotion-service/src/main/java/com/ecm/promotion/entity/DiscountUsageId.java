package com.ecm.promotion.entity;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DiscountUsageId implements Serializable {
    private UUID discountId;
    private String checkoutKey;
}
