package org.sainm.codeatlas.analyzers.source;

import java.util.List;

record JspParseAttempt(JspParserMode parserMode, List<JavaAnalysisDiagnostic> diagnostics) {
    JspParseAttempt {
        if (parserMode == null) {
            throw new IllegalArgumentException("parserMode is required");
        }
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
