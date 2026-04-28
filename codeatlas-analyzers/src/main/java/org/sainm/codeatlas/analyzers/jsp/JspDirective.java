package org.sainm.codeatlas.analyzers.jsp;

import java.util.Map;

public record JspDirective(
    String name,
    Map<String, String> attributes,
    int line
) {
    public JspDirective {
        name = name == null || name.isBlank() ? "unknown" : name.trim();
        attributes = Map.copyOf(attributes);
        line = Math.max(0, line);
    }
}
