package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.ReviewResponse;
import com.ecm.catalog.entity.ProductReview;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductReviewMapper {

    ReviewResponse toResponse(ProductReview review);
}
