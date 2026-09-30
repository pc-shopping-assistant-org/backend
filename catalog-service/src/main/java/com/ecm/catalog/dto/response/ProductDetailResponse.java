package com.ecm.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    
    private UUID id;
    private String name;
    private String seoName;
    private UUID brandId;
    private String brandName;
    private UUID categoryId;
    private String categoryName;
    
    @Builder.Default
    private Map<String, Object> specifications = new HashMap<>();
    
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    
    @Builder.Default
    private List<ProductVariantResponse> variants = new ArrayList<>();
}
