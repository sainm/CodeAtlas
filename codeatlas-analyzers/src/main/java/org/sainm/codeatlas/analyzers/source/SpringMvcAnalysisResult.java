package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record SpringMvcAnalysisResult(
        boolean noClasspathFallbackUsed,
        List<SpringComponentInfo> components,
        List<SpringRouteInfo> routes,
        List<SpringInjectionInfo> injections,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public SpringMvcAnalysisResult {
        components = List.copyOf(components == null ? List.of() : components);
        routes = List.copyOf(routes == null ? List.of() : routes);
        injections = List.copyOf(injections == null ? List.of() : injections);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
