package org.sainm.codeatlas.analyzers.source;

public record SqlTableAccessInfo(
        String statementId,
        String tableName,
        SqlTableAccessKind kind,
        boolean conservativeFallback,
        SourceLocation location) {
    public SqlTableAccessInfo(
            String statementId,
            String tableName,
            SqlTableAccessKind kind,
            SourceLocation location) {
        this(statementId, tableName, kind, false, location);
    }

    public SqlTableAccessInfo {
        if (statementId == null || statementId.isBlank()) {
            throw new IllegalArgumentException("statementId is required");
        }
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
