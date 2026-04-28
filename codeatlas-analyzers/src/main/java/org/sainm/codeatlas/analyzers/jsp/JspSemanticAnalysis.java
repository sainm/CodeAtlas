package org.sainm.codeatlas.analyzers.jsp;

import java.util.List;

public record JspSemanticAnalysis(
    List<JspDirective> directives,
    List<JspAction> actions,
    List<JspExpressionFragment> expressions,
    List<String> includes,
    String encoding
) {
    public JspSemanticAnalysis {
        directives = List.copyOf(directives);
        actions = List.copyOf(actions);
        expressions = List.copyOf(expressions);
        includes = List.copyOf(includes);
        encoding = encoding == null || encoding.isBlank() ? "UTF-8" : encoding.trim();
    }
}
