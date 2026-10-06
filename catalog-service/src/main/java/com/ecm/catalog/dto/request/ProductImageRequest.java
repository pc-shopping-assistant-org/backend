package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** One gallery image: a file already stored in the Media Service, optionally the main (thumbnail) image. */
public record ProductImageRequest(@NotNull UUID fileId, boolean main) {}
