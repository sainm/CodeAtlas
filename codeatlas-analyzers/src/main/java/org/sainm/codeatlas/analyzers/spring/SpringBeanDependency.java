package org.sainm.codeatlas.analyzers.spring;

import org.sainm.codeatlas.graph.model.Confidence;

public record SpringBeanDependency(
    String sourceClass,
    String dependencyType,
    String injectionPoint,
    String qualifier,
    Confidence confidence,
    int line
) {
    public SpringBeanDependency {
        sourceClass = require(sourceClass, "sourceClass");
        dependencyType = require(dependencyType, "dependencyType");
        injectionPoint = require(injectionPoint, "injectionPoint");
        qualifier = qualifier == null ? "" : qualifier.trim();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
