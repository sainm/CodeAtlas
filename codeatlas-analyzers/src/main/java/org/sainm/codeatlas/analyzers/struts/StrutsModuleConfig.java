package org.sainm.codeatlas.analyzers.struts;

public record StrutsModuleConfig(
    String modulePrefix,
    String configLocation
) {
    public StrutsModuleConfig {
        modulePrefix = normalizePrefix(modulePrefix);
        configLocation = require(configLocation, "configLocation");
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank() || value.equals("/")) {
            return "";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
