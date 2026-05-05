package org.sainm.codeatlas.analyzers.source;

public record MyBatisXmlStatementInfo(
        String path,
        String namespace,
        String id,
        MyBatisStatementKind kind,
        String sql,
        boolean conservativeTableAccess,
        SourceLocation location) {
    public MyBatisXmlStatementInfo(
            String path,
            String namespace,
            String id,
            MyBatisStatementKind kind,
            SourceLocation location) {
        this(path, namespace, id, kind, "", false, location);
    }

    public MyBatisXmlStatementInfo(
            String path,
            String namespace,
            String id,
            MyBatisStatementKind kind,
            String sql,
            SourceLocation location) {
        this(path, namespace, id, kind, sql, false, location);
    }

    public MyBatisXmlStatementInfo {
        JavaClassInfo.requireNonBlank(path, "path");
        JavaClassInfo.requireNonBlank(namespace, "namespace");
        JavaClassInfo.requireNonBlank(id, "id");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        sql = sql == null ? "" : sql;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
