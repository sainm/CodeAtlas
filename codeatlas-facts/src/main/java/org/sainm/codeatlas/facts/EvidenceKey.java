package org.sainm.codeatlas.facts;

public record EvidenceKey(String value) {
    public EvidenceKey {
        requireNonBlank(value, "evidenceKey");
        if (!value.startsWith("evidence:")) {
            throw new IllegalArgumentException("evidenceKey must use evidence: prefix");
        }
    }

    public static EvidenceKey of(
            String analyzerId,
            String scopeKey,
            String sourcePath,
            String location,
            int schemaVersion) {
        requireNonBlank(analyzerId, "analyzerId");
        requireNonBlank(scopeKey, "scopeKey");
        requireNonBlank(sourcePath, "sourcePath");
        requireNonBlank(location, "location");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        return new EvidenceKey(StableKey.hash(
                "evidence",
                analyzerId,
                scopeKey,
                sourcePath,
                location,
                String.valueOf(schemaVersion)));
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
