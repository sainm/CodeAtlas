package org.sainm.codeatlas.analyzers.sql;

import java.util.List;

public record MyBatisStatement(
    String namespace,
    String id,
    String commandType,
    String sql,
    int line,
    boolean dynamic,
    List<SqlTableAccess> tableAccesses
) {
    public MyBatisStatement {
        namespace = require(namespace, "namespace");
        id = require(id, "id");
        commandType = require(commandType, "commandType").toUpperCase();
        sql = sql == null ? "" : sql.trim().replaceAll("\\s+", " ");
        line = Math.max(0, line);
        tableAccesses = List.copyOf(tableAccesses);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
