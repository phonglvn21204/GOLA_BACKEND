package com.gola.dto.common;

import lombok.Getter;
import org.springframework.data.domain.Page;
import java.util.List;

@Getter
public class PageResponse<T> {
    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int page;
    private final int size;
    private final boolean first;
    private final boolean last;

    public PageResponse(Page<T> page) {
        this.content = page.getContent();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.page = page.getNumber();
        this.size = page.getSize();
        this.first = page.isFirst();
        this.last = page.isLast();
    }
}