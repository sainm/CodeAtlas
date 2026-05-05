package org.sainm.codeatlas.analyzers.source;

public record ReportFieldDefinition(
        String fieldName,
        String dataType,
        String sourceTable,
        String sourceColumn,
        SourceLocation location) {
    public ReportFieldDefinition {
        fieldName = fieldName == null ? "" : fieldName;
        dataType = dataType == null ? "" : dataType;
        sourceTable = sourceTable == null ? "" : sourceTable;
        sourceColumn = sourceColumn == null ? "" : sourceColumn;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
