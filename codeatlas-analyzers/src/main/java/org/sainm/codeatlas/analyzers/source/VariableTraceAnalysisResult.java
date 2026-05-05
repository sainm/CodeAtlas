package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record VariableTraceAnalysisResult(
        List<MethodLocalDefUseInfo> defUses,
        List<MethodLocalUseInfo> uses,
        List<RequestValueAccessInfo> requestAccesses,
        List<FormPropertyReadInfo> formPropertyReads,
        List<BeanPropertyFlowInfo> beanPropertyFlows,
        List<RequestDerivedArgumentInfo> requestDerivedArguments,
        List<ParameterDerivedArgumentInfo> parameterDerivedArguments,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public VariableTraceAnalysisResult(
            List<MethodLocalDefUseInfo> defUses,
            List<MethodLocalUseInfo> uses,
            List<JavaAnalysisDiagnostic> diagnostics) {
        this(defUses, uses, List.of(), List.of(), List.of(), List.of(), List.of(), diagnostics);
    }

    public VariableTraceAnalysisResult {
        defUses = List.copyOf(defUses == null ? List.of() : defUses);
        uses = List.copyOf(uses == null ? List.of() : uses);
        requestAccesses = List.copyOf(requestAccesses == null ? List.of() : requestAccesses);
        formPropertyReads = List.copyOf(formPropertyReads == null ? List.of() : formPropertyReads);
        beanPropertyFlows = List.copyOf(beanPropertyFlows == null ? List.of() : beanPropertyFlows);
        requestDerivedArguments = List.copyOf(requestDerivedArguments == null ? List.of() : requestDerivedArguments);
        parameterDerivedArguments = List.copyOf(parameterDerivedArguments == null ? List.of() : parameterDerivedArguments);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
