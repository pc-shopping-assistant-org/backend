package com.ecm.sync.dto.response;

/** How many parked catalog changes have not been replayed yet. */
public record DltStatusResponse(long pending) {
}
