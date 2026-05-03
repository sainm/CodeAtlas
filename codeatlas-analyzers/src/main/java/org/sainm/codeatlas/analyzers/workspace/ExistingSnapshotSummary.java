package org.sainm.codeatlas.analyzers.workspace;

import java.util.List;

public record ExistingSnapshotSummary(
        String snapshotId,
        List<String> cachedScopeKeys) {
    public ExistingSnapshotSummary {
        snapshotId = snapshotId == null ? "" : snapshotId;
        if (cachedScopeKeys == null || cachedScopeKeys.isEmpty()) {
            cachedScopeKeys = List.of();
        } else {
            cachedScopeKeys = cachedScopeKeys.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    public boolean hasCachedScope(String scopeKey) {
        return cachedScopeKeys.contains(scopeKey);
    }
}
