package org.sainm.codeatlas.analyzers.jsp;

public record JspExpressionFragment(
    String kind,
    String expression,
    int line
) {
    public JspExpressionFragment {
        kind = kind == null || kind.isBlank() ? "unknown" : kind.trim();
        expression = expression == null ? "" : expression.trim();
        line = Math.max(0, line);
    }
}
