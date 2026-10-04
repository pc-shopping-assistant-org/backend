package com.ecm.catalog.dto.request;

import com.ecm.catalog.entity.CatalogStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateVariantStatusRequest(@NotNull CatalogStatus status) {}
