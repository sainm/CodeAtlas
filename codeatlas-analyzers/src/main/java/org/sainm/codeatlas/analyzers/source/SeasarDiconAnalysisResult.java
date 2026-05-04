package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record SeasarDiconAnalysisResult(
        List<SeasarDiconComponentInfo> components,
        List<SeasarDiconIncludeInfo> includes,
        List<SeasarDiconPropertyInfo> properties,
        List<SeasarDiconAspectInfo> aspects,
        List<SeasarDiconInterceptorInfo> interceptors,
        List<SeasarDiconNamespaceInfo> namespaces,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public SeasarDiconAnalysisResult {
        components = components == null ? List.of() : List.copyOf(components);
        includes = includes == null ? List.of() : List.copyOf(includes);
        properties = properties == null ? List.of() : List.copyOf(properties);
        aspects = aspects == null ? List.of() : List.copyOf(aspects);
        interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
        namespaces = namespaces == null ? List.of() : List.copyOf(namespaces);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
