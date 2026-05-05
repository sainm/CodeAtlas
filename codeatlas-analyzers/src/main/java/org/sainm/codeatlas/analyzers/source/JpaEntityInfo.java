package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JpaEntityInfo(
        String qualifiedName,
        String tableName,
        String schemaName,
        List<JpaColumnMappingInfo> columns,
        SourceLocation location) {
    public JpaEntityInfo {
        JavaClassInfo.requireNonBlank(qualifiedName, "qualifiedName");
        JavaClassInfo.requireNonBlank(tableName, "tableName");
        schemaName = schemaName == null ? "" : schemaName;
        columns = List.copyOf(columns == null ? List.of() : columns);
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
