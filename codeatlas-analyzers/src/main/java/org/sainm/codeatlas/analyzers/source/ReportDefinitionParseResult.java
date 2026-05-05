package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record ReportDefinitionParseResult(
        String reportName,
        List<ReportFieldDefinition> fields,
        List<ReportParameterDefinition> parameters,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public ReportDefinitionParseResult {
        reportName = reportName == null ? "" : reportName;
        fields = List.copyOf(fields == null ? List.of() : fields);
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
