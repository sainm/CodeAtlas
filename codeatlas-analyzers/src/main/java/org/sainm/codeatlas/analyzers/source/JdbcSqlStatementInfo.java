package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JdbcSqlStatementInfo(
        String statementId,
        String ownerQualifiedName,
        String ownerMethodName,
        String ownerSignature,
        String sql,
        List<JdbcSqlParameterBindingInfo> parameters,
        SourceLocation location) {
    public JdbcSqlStatementInfo(
            String statementId,
            String sql,
            SourceLocation location) {
        this(statementId, "", "", "", sql, List.of(), location);
    }

    public JdbcSqlStatementInfo(
            String statementId,
            String ownerQualifiedName,
            String ownerMethodName,
            String ownerSignature,
            String sql,
            SourceLocation location) {
        this(statementId, ownerQualifiedName, ownerMethodName, ownerSignature, sql, List.of(), location);
    }

    public JdbcSqlStatementInfo {
        JavaClassInfo.requireNonBlank(statementId, "statementId");
        ownerQualifiedName = ownerQualifiedName == null ? "" : ownerQualifiedName;
        ownerMethodName = ownerMethodName == null ? "" : ownerMethodName;
        ownerSignature = ownerSignature == null ? "" : ownerSignature;
        JavaClassInfo.requireNonBlank(sql, "sql");
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
