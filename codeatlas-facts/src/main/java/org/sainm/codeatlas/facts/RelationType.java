package org.sainm.codeatlas.facts;

public record RelationType(String name, RelationFamily family, boolean mvp) {
    public RelationType {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("relation name is required");
        }
        if (!name.equals(name.toUpperCase())) {
            throw new IllegalArgumentException("relation name must be uppercase");
        }
        if (family == null) {
            throw new IllegalArgumentException("relation family is required");
        }
    }
}
