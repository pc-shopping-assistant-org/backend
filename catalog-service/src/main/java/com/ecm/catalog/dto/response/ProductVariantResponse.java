package com.ecm.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {
    
    private UUID id;
    private UUID productId;
    private Long listPrice;
    private Integer quantity;
    private String sku;
    private String model;
    private String description;
    private Integer warrantyMonths;
    private LocalDate releaseAt;
    private String status;
    
    @Builder.Default
    private List<ProductImageResponse> images = new ArrayList<>();
    
    @Builder.Default
    private List<OptionResponse> options = new ArrayList<>();
    
    private Instant createdAt;
}
