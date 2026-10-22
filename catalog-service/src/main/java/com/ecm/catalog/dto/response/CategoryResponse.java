package com.ecm.catalog.dto.response;

import com.ecm.catalog.entity.HardwareType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    
    private UUID id;
    private String name;
    private String seoName;
    private HardwareType hardwareType;
    private String description;
    private UUID parentId;
    private String status;
    private Instant createdAt;

    /** Products a shopper can see in this category and in every category under it. */
    private long productCount;
    
    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();
}
