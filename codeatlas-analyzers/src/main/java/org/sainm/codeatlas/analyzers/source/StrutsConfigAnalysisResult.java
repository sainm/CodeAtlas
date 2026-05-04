package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record StrutsConfigAnalysisResult(
        List<StrutsModuleInfo> modules,
        List<StrutsFormBeanInfo> formBeans,
        List<StrutsActionInfo> actions,
        List<StrutsForwardInfo> globalForwards,
        List<StrutsExceptionInfo> globalExceptions,
        List<StrutsMessageResourceInfo> messageResources,
        List<StrutsPluginInfo> plugins,
        List<StrutsControllerInfo> controllers,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public StrutsConfigAnalysisResult {
        modules = List.copyOf(modules == null ? List.of() : modules);
        formBeans = List.copyOf(formBeans == null ? List.of() : formBeans);
        actions = List.copyOf(actions == null ? List.of() : actions);
        globalForwards = List.copyOf(globalForwards == null ? List.of() : globalForwards);
        globalExceptions = List.copyOf(globalExceptions == null ? List.of() : globalExceptions);
        messageResources = List.copyOf(messageResources == null ? List.of() : messageResources);
        plugins = List.copyOf(plugins == null ? List.of() : plugins);
        controllers = List.copyOf(controllers == null ? List.of() : controllers);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
