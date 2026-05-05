package org.sainm.codeatlas.analyzers.source;

import java.util.List;

/**
 * Collected results of scanning and parsing report definition files.
 */
public record ReportAnalysisResult(
        List<ReportDefinitionParseResult> parseResults) {
    public ReportAnalysisResult {
        parseResults = List.copyOf(parseResults == null ? List.of() : parseResults);
    }
}
