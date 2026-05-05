package org.sainm.codeatlas.server;

import java.util.List;

public record ListResponse<T>(
        List<T> items,
        int offset,
        int limit,
        String sort) {
    public ListResponse {
        items = List.copyOf(items == null ? List.of() : items);
        sort = sort == null ? "" : sort;
    }
}
