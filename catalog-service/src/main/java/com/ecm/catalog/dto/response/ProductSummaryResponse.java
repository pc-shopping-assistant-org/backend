package com.ecm.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryResponse {
    
    private UUID id;
    private String name;
    private String seoName;
    private UUID brandId;
    private String brandName;
    private UUID categoryId;
    private String categoryName;
    private Long minPrice;
    private Long maxPrice;
    private String mainImageUrl;
    private String status;
    private Instant createdAt;
}
