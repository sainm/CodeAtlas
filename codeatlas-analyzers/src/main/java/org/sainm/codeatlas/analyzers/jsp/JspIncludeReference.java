package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;
import java.nio.file.Path;

public record JspIncludeReference(
    JspIncludeType type,
    String rawPath,
    Path resolvedPath,
    boolean exists,
    Confidence confidence,
    int line
) {
    public JspIncludeReference {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        rawPath = rawPath == null ? "" : rawPath.trim();
        if (rawPath.isBlank()) {
            throw new IllegalArgumentException("rawPath is required");
        }
        if (resolvedPath == null) {
            throw new IllegalArgumentException("resolvedPath is required");
        }
        resolvedPath = resolvedPath.toAbsolutePath().normalize();
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
        line = Math.max(0, line);
    }
}
