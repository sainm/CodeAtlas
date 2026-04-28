package org.sainm.codeatlas.analyzers.jsp;

import java.util.List;

public record JspForm(
    String action,
    String method,
    int line,
    List<JspInput> inputs
) {
    public JspForm {
        action = require(action, "action");
        method = method == null || method.isBlank() ? "post" : method.trim().toLowerCase();
        line = Math.max(0, line);
        inputs = List.copyOf(inputs);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
