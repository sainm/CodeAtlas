package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JavaSourceAnalysisResult(
        boolean noClasspathFallbackUsed,
        List<JavaClassInfo> classes,
        List<JavaMethodInfo> methods,
        List<JavaFieldInfo> fields,
        List<JavaInvocationInfo> directInvocations,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public JavaSourceAnalysisResult {
        classes = List.copyOf(classes == null ? List.of() : classes);
        methods = List.copyOf(methods == null ? List.of() : methods);
        fields = List.copyOf(fields == null ? List.of() : fields);
        directInvocations = List.copyOf(directInvocations == null ? List.of() : directInvocations);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
