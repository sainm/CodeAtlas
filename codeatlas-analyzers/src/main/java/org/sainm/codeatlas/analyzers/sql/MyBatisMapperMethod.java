package org.sainm.codeatlas.analyzers.sql;

import org.sainm.codeatlas.graph.model.Confidence;

public record MyBatisMapperMethod(
    String mapperInterface,
    String methodName,
    String descriptor,
    boolean annotationSql,
    Confidence bridgeConfidence,
    int line
) {
    public MyBatisMapperMethod {
        mapperInterface = require(mapperInterface, "mapperInterface");
        methodName = require(methodName, "methodName");
        descriptor = require(descriptor, "descriptor");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
