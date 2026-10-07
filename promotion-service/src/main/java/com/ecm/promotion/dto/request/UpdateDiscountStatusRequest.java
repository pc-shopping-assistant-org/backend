package com.ecm.promotion.dto.request;

import com.ecm.promotion.entity.DiscountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDiscountStatusRequest(@NotNull DiscountStatus status) {
}
