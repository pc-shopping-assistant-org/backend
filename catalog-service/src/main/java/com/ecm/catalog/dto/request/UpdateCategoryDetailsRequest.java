package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCategoryDetailsRequest(@NotBlank @Size(max = 255) String name,
                                           @Size(max = 255) String seoName,
                                           UUID parentId) {}
