package org.sainm.codeatlas.analyzers.source;

public record SqlColumnAccessInfo(
        String statementId,
        String tableName,
        String columnName,
        SqlTableAccessKind kind,
        SourceLocation location) {
    public SqlColumnAccessInfo {
        JavaClassInfo.requireNonBlank(statementId, "statementId");
        JavaClassInfo.requireNonBlank(tableName, "tableName");
        JavaClassInfo.requireNonBlank(columnName, "columnName");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
