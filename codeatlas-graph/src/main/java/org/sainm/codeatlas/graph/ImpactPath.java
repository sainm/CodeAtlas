package org.sainm.codeatlas.graph;

import java.util.List;

public record ImpactPath(List<String> identityIds) {
    public ImpactPath {
        if (identityIds == null || identityIds.isEmpty()) {
            throw new IllegalArgumentException("identityIds are required");
        }
        identityIds = List.copyOf(identityIds);
    }

    public String startIdentityId() {
        return identityIds.getFirst();
    }

    public String endIdentityId() {
        return identityIds.getLast();
    }

    public int depth() {
        return identityIds.size() - 1;
    }
}
