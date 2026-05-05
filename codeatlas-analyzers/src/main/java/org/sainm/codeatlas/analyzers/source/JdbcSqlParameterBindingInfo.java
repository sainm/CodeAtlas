package org.sainm.codeatlas.analyzers.source;

public record JdbcSqlParameterBindingInfo(
        int index,
        String binderMethodName,
        SourceLocation location) {
    public JdbcSqlParameterBindingInfo {
        if (index <= 0) {
            throw new IllegalArgumentException("index must be positive");
        }
        JavaClassInfo.requireNonBlank(binderMethodName, "binderMethodName");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
