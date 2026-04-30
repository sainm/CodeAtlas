package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;

public record JspTaglibReference(
    String prefix,
    String uri,
    String tagdir,
    String resolvedLocation,
    Confidence confidence,
    int line
) {
    public JspTaglibReference {
        prefix = prefix == null || prefix.isBlank() ? "unknown" : prefix.trim();
        uri = trim(uri);
        tagdir = trim(tagdir);
        resolvedLocation = trim(resolvedLocation);
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
        line = Math.max(0, line);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
