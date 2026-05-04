package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record StrutsActionInfo(
        String moduleKey,
        String path,
        String type,
        String formBeanName,
        String scope,
        String parameter,
        StrutsActionDispatchKind dispatchKind,
        List<StrutsForwardInfo> forwards,
        List<StrutsExceptionInfo> exceptions,
        SourceLocation location) {
    public StrutsActionInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        JavaClassInfo.requireNonBlank(path, "path");
        type = type == null ? "" : type;
        formBeanName = formBeanName == null ? "" : formBeanName;
        scope = scope == null ? "" : scope;
        parameter = parameter == null ? "" : parameter;
        if (dispatchKind == null) {
            throw new IllegalArgumentException("dispatchKind is required");
        }
        forwards = List.copyOf(forwards == null ? List.of() : forwards);
        exceptions = List.copyOf(exceptions == null ? List.of() : exceptions);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
