package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;

public record JspClientNavigation(
    String target,
    String context,
    Confidence confidence,
    int line
) {
    public JspClientNavigation {
        target = target == null ? "" : target.trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        context = context == null || context.isBlank() ? "script-url" : context.trim();
        confidence = confidence == null ? Confidence.POSSIBLE : confidence;
        line = Math.max(0, line);
    }
}
