package com.ecm.catalog.dto.request;

import com.ecm.catalog.entity.CatalogStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull CatalogStatus status) {}
