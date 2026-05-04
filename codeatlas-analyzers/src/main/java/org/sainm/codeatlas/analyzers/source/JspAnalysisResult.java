package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JspAnalysisResult(
        JspParserMode parserMode,
        List<JspDirectiveInfo> directives,
        List<JspTaglibInfo> taglibs,
        List<JspIncludeInfo> includes,
        List<JspForwardInfo> forwards,
        List<JspFormInfo> forms,
        List<JspInputInfo> inputs,
        List<JspRequestParameterAccessInfo> requestParameters,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public JspAnalysisResult {
        if (parserMode == null) {
            throw new IllegalArgumentException("parserMode is required");
        }
        directives = List.copyOf(directives == null ? List.of() : directives);
        taglibs = List.copyOf(taglibs == null ? List.of() : taglibs);
        includes = List.copyOf(includes == null ? List.of() : includes);
        forwards = List.copyOf(forwards == null ? List.of() : forwards);
        forms = List.copyOf(forms == null ? List.of() : forms);
        inputs = List.copyOf(inputs == null ? List.of() : inputs);
        requestParameters = List.copyOf(requestParameters == null ? List.of() : requestParameters);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
