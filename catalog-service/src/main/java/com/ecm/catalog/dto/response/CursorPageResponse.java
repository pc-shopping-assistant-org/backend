package com.ecm.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageResponse<T> {
    
    private List<T> items;
    private String nextCursor;
    private boolean hasNext;
    private int size;

    /** Only when a numbered page was asked for: its number and the totals. */
    private Integer page;
    private Long totalElements;
    private Integer totalPages;
}
