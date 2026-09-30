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
public class BrandResponse {
    
    private UUID id;
    private String name;
    private String seoName;
    private String description;
    private UUID imageFileId;
    private String imageUrl;
    private String status;
    private Instant createdAt;
}
