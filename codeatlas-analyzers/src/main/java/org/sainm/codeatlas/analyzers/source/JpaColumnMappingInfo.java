package org.sainm.codeatlas.analyzers.source;

public record JpaColumnMappingInfo(
        String fieldName,
        String fieldTypeDescriptor,
        String columnName,
        SourceLocation location) {
    public JpaColumnMappingInfo {
        JavaClassInfo.requireNonBlank(fieldName, "fieldName");
        fieldTypeDescriptor = fieldTypeDescriptor == null ? "" : fieldTypeDescriptor;
        JavaClassInfo.requireNonBlank(columnName, "columnName");
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
