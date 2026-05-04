package org.sainm.codeatlas.analyzers.source;

public record StrutsControllerInfo(
        String moduleKey,
        String processorClass,
        String contentType,
        boolean locale,
        SourceLocation location) {
    public StrutsControllerInfo {
        moduleKey = moduleKey == null ? "" : moduleKey;
        processorClass = processorClass == null ? "" : processorClass;
        contentType = contentType == null ? "" : contentType;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
