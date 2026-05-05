package org.sainm.codeatlas.analyzers.source;

import java.util.List;

record JspParseAttempt(
        JspParserMode parserMode,
        List<JavaAnalysisDiagnostic> diagnostics,
        List<JasperSmapParser.JasperSmapResult> smapResults) {
    JspParseAttempt(JspParserMode parserMode, List<JavaAnalysisDiagnostic> diagnostics) {
        this(parserMode, diagnostics, List.of());
    }

    JspParseAttempt {
        if (parserMode == null) {
            throw new IllegalArgumentException("parserMode is required");
        }
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        smapResults = smapResults == null ? List.of() : List.copyOf(smapResults);
    }
}
