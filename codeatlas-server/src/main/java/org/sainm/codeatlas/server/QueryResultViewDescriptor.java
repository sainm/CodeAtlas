package org.sainm.codeatlas.server;

import java.util.List;

public record QueryResultViewDescriptor(
    String name,
    String title,
    String summary,
    List<String> primaryFields,
    List<String> evidenceFields
) {
    public QueryResultViewDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        title = title == null || title.isBlank() ? name : title.trim();
        summary = summary == null ? "" : summary.trim();
        primaryFields = List.copyOf(primaryFields);
        evidenceFields = List.copyOf(evidenceFields);
    }
}
