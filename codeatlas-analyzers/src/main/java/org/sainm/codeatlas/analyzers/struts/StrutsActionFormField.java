package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.graph.model.Confidence;

public record StrutsActionFormField(
    String formClass,
    String fieldName,
    String fieldType,
    Confidence confidence,
    int line
) {
    public StrutsActionFormField {
        formClass = require(formClass, "formClass");
        fieldName = require(fieldName, "fieldName");
        fieldType = require(fieldType, "fieldType");
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
        line = Math.max(0, line);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
