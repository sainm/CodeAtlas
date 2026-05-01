package org.sainm.codeatlas.worker;

public record TaiEMethodCallEdge(
    String callerSignature,
    String calleeSignature,
    String evidencePath,
    int lineNumber,
    String qualifier
) {
    public TaiEMethodCallEdge {
        callerSignature = require(callerSignature, "callerSignature");
        calleeSignature = require(calleeSignature, "calleeSignature");
        evidencePath = evidencePath == null || evidencePath.isBlank() ? "tai-e-output" : evidencePath.trim();
        lineNumber = Math.max(0, lineNumber);
        qualifier = qualifier == null ? "" : qualifier.trim();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
