package org.sainm.codeatlas.mcp;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class McpToolCallSafetyGuard {
    private static final Set<String> FORBIDDEN_DATABASE_ARGUMENTS = Set.of(
        "cypher",
        "rawcypher",
        "sql",
        "rawsql",
        "statement",
        "querystatement",
        "databasequery",
        "dbquery"
    );

    public void validate(McpToolDescriptor descriptor, Map<String, Object> arguments) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Tool descriptor is required");
        }
        if (!descriptor.readOnly()) {
            throw new IllegalArgumentException("MCP tool must be read-only: " + descriptor.name().value());
        }
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        for (String key : arguments.keySet()) {
            String normalized = normalize(key);
            if (FORBIDDEN_DATABASE_ARGUMENTS.contains(normalized)) {
                throw new IllegalArgumentException("Arbitrary database query arguments are not allowed: " + key);
            }
        }
    }

    private String normalize(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("_", "")
            .replace("-", "")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}

