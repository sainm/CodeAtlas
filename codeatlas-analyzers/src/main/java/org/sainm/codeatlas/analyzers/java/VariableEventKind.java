package org.sainm.codeatlas.analyzers.java;

public enum VariableEventKind {
    PARAMETER,
    LOCAL_DEFINITION,
    ASSIGNMENT,
    READ,
    WRITE,
    RETURN,
    GETTER_RETURN,
    SETTER_WRITE,
    REQUEST_PARAMETER_READ,
    REQUEST_ATTRIBUTE_READ,
    REQUEST_ATTRIBUTE_WRITE
}
