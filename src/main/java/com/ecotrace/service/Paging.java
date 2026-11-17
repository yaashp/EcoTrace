package com.ecotrace.service;

import com.ecotrace.dto.Dtos.PageView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

final class Paging {

    private Paging() {
    }

    /** Clamps the requested page and size to sensible bounds. */
    static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort);
    }

    static <T, R> PageView<R> view(Page<T> page, Function<T, R> mapper) {
        return new PageView<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Builds a lower-case LIKE pattern from user input, without letting it inject wildcards. */
    static String likePattern(String q) {
        String cleaned = q == null ? "" : q.trim().toLowerCase().replace("\\", " ").replace("%", " ").replace("_", " ");
        return "%" + cleaned + "%";
    }
}
