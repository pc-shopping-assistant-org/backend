package com.ecm.order.dto.response;

import java.util.List;

/** A page of a keyset-paginated list; {@code nextCursor} is null on the last page. */
public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasNext, int size) {
}
