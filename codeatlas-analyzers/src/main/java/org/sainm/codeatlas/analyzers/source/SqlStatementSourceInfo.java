package org.sainm.codeatlas.analyzers.source;

public record SqlStatementSourceInfo(
        String statementId,
        String sql,
        SourceLocation location) {
    public SqlStatementSourceInfo {
        if (statementId == null || statementId.isBlank()) {
            throw new IllegalArgumentException("statementId is required");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
