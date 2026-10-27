package com.ecm.order.dto.response;

import java.util.List;

/**
 * A page of a list: keyset-paginated ({@code nextCursor} is null on the last page) or, when a numbered page was asked
 * for, carrying that page's number and the totals.
 */
public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasNext, int size,
                                    Integer page, Long totalElements, Integer totalPages) {

    public CursorPageResponse(List<T> items, String nextCursor, boolean hasNext, int size) {
        this(items, nextCursor, hasNext, size, null, null, null);
    }
}
