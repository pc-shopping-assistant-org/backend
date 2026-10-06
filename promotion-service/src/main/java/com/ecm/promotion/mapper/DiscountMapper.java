package com.ecm.promotion.mapper;

import com.ecm.promotion.dto.response.DiscountResponse;
import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountState;
import org.mapstruct.Mapper;

import java.util.Set;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface DiscountMapper {

    DiscountResponse toResponse(Discount discount, DiscountState state, Set<UUID> categoryIds);
}
