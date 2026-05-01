package org.sainm.codeatlas.worker;

import java.util.List;

public record TaiESampleCompatibilityResult(
    boolean compatible,
    int classPathCount,
    int mappedSignatureCount,
    List<String> messages
) {
    public TaiESampleCompatibilityResult {
        classPathCount = Math.max(0, classPathCount);
        mappedSignatureCount = Math.max(0, mappedSignatureCount);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
