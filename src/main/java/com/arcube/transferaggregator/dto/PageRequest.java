package com.arcube.transferaggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination parameters for large result sets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    @Builder.Default
    private int page = DEFAULT_PAGE;

    @Builder.Default
    private int size = DEFAULT_SIZE;

    public int getOffset() {
        return page * size;
    }

    public int getValidatedSize() {
        if (size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    public static PageRequest of(int page, int size) {
        return PageRequest.builder()
            .page(Math.max(0, page))
            .size(Math.min(Math.max(1, size), MAX_SIZE))
            .build();
    }

    public static PageRequest first() {
        return PageRequest.of(0, DEFAULT_SIZE);
    }

    public static PageRequest unpaged() {
        return PageRequest.builder().page(0).size(Integer.MAX_VALUE).build();
    }
}
