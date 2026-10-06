package com.ecm.catalog.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterRequest {
    
    private String keyword;
    private UUID categoryId;
    private UUID brandId;

    /** Only honored by the admin listing; the public listing always shows ACTIVE products. */
    private com.ecm.catalog.entity.CatalogStatus status;

    private Long minPrice;
    private Long maxPrice;
    
    @Builder.Default
    private Integer limit = 20;
    
    private UUID cursor;
}
