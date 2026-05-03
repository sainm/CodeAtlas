package org.sainm.codeatlas.facts;

public record FactKey(String value) {
    public FactKey {
        requireNonBlank(value, "factKey");
        if (!value.startsWith("fact:")) {
            throw new IllegalArgumentException("factKey must use fact: prefix");
        }
    }

    public static FactKey of(
            String sourceIdentityId,
            String targetIdentityId,
            String relationType,
            String qualifier) {
        requireNonBlank(sourceIdentityId, "sourceIdentityId");
        requireNonBlank(targetIdentityId, "targetIdentityId");
        requireNonBlank(relationType, "relationType");
        return new FactKey(StableKey.hash("fact", sourceIdentityId, targetIdentityId, relationType, qualifier));
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
