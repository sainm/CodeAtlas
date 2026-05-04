package org.sainm.codeatlas.analyzers.source;

public record JdbcSqlStatementInfo(
        String statementId,
        String ownerQualifiedName,
        String ownerMethodName,
        String ownerSignature,
        String sql,
        SourceLocation location) {
    public JdbcSqlStatementInfo(
            String statementId,
            String sql,
            SourceLocation location) {
        this(statementId, "", "", "", sql, location);
    }

    public JdbcSqlStatementInfo {
        JavaClassInfo.requireNonBlank(statementId, "statementId");
        ownerQualifiedName = ownerQualifiedName == null ? "" : ownerQualifiedName;
        ownerMethodName = ownerMethodName == null ? "" : ownerMethodName;
        ownerSignature = ownerSignature == null ? "" : ownerSignature;
        JavaClassInfo.requireNonBlank(sql, "sql");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
