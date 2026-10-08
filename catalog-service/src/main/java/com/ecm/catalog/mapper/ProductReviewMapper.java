package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.ReviewResponse;
import com.ecm.catalog.entity.ProductReview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductReviewMapper {

    @Mapping(target = "reviewerName", source = "reviewerName")
    ReviewResponse toResponse(ProductReview review, String reviewerName);
}
