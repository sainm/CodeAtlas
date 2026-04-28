package org.sainm.codeatlas.analyzers.sql;

import java.util.List;

public record SqlTableAccess(
    String tableName,
    SqlAccessType accessType,
    List<String> columnNames
) {
    public SqlTableAccess(String tableName, SqlAccessType accessType) {
        this(tableName, accessType, List.of());
    }

    public SqlTableAccess {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName is required");
        }
        tableName = tableName.trim();
        if (accessType == null) {
            throw new IllegalArgumentException("accessType is required");
        }
        columnNames = columnNames == null ? List.of() : columnNames.stream()
            .filter(column -> column != null && !column.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }
}
