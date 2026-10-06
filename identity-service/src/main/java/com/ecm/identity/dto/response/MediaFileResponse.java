package com.ecm.identity.dto.response;

import java.util.UUID;

/**
 * Mirrors the part of media-service's response this service reads; duplicated here rather than shared.
 */
public record MediaFileResponse(UUID id, String url) {
}
