package com.ecm.catalog.dto.response;

import com.ecm.catalog.entity.AttributeDataType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttributeDefinitionResponse(
        UUID id,
        String key,
        String displayName,
        AttributeDataType dataType,
        String unit,
        List<String> allowedValues,
        List<String> aliases,
        boolean filterable,
        boolean comparable,
        Instant createdAt
) {}
