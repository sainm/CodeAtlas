package org.sainm.codeatlas.analyzers.source;

public record ReportParameterDefinition(
        String parameterName,
        String parameterType,
        String defaultValue,
        SourceLocation location) {
    public ReportParameterDefinition {
        parameterName = parameterName == null ? "" : parameterName;
        parameterType = parameterType == null ? "" : parameterType;
        defaultValue = defaultValue == null ? "" : defaultValue;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
