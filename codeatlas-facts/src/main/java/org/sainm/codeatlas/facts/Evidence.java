package org.sainm.codeatlas.facts;

public record Evidence(
        String evidenceKey,
        String analyzerId,
        String scopeKey,
        String sourcePath,
        String location,
        int schemaVersion,
        SourceType sourceType) {
    public Evidence {
        requireNonBlank(evidenceKey, "evidenceKey");
        requireNonBlank(analyzerId, "analyzerId");
        requireNonBlank(scopeKey, "scopeKey");
        requireNonBlank(sourcePath, "sourcePath");
        requireNonBlank(location, "location");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        String expectedEvidenceKey = EvidenceKey.of(analyzerId, scopeKey, sourcePath, location, schemaVersion).value();
        if (!evidenceKey.equals(expectedEvidenceKey)) {
            throw new IllegalArgumentException("evidenceKey must match evidence identity fields");
        }
    }

    public static Evidence create(
            String analyzerId,
            String scopeKey,
            String sourcePath,
            String location,
            int schemaVersion,
            SourceType sourceType) {
        return new Evidence(
                EvidenceKey.of(analyzerId, scopeKey, sourcePath, location, schemaVersion).value(),
                analyzerId,
                scopeKey,
                sourcePath,
                location,
                schemaVersion,
                sourceType);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
