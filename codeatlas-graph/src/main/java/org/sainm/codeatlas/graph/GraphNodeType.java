package org.sainm.codeatlas.graph;

import org.sainm.codeatlas.symbols.IdentityType;

public record GraphNodeType(
        String label,
        String kind,
        IdentityType identityType,
        String identityProperty,
        GraphNodeLayer layer,
        boolean defaultQueryFact) {
    public GraphNodeType {
        requireLabel(label);
        requireNonBlank(kind, "kind");
        if (identityType == null) {
            throw new IllegalArgumentException("identityType is required");
        }
        requireNonBlank(identityProperty, "identityProperty");
        if (layer == null) {
            throw new IllegalArgumentException("layer is required");
        }
    }

    static String identityPropertyFor(IdentityType identityType) {
        return switch (identityType) {
            case SYMBOL_ID -> "symbolId";
            case FLOW_ID -> "flowId";
            case ARTIFACT_ID -> "artifactId";
        };
    }

    static void requireLabel(String label) {
        requireNonBlank(label, "label");
        if (!Character.isUpperCase(label.charAt(0))) {
            throw new IllegalArgumentException("label must start with an uppercase letter");
        }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                throw new IllegalArgumentException("label must be alphanumeric");
            }
        }
    }

    static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
