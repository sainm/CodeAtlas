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
        if (arguments == null || arguments.isEmpty()) {
            if (!descriptor.readOnly()) {
                throw new IllegalArgumentException("Write MCP tool requires explicit confirmation: " + descriptor.name().value());
            }
            return;
        }
        if (!descriptor.readOnly() && !hasWriteConfirmation(arguments)) {
            throw new IllegalArgumentException("Write MCP tool requires explicit confirmation: " + descriptor.name().value());
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

    private boolean hasWriteConfirmation(Map<String, Object> arguments) {
        Object confirmWrite = arguments.get("confirmWrite");
        Object confirmationIntent = arguments.get("confirmationIntent");
        return Boolean.TRUE.equals(confirmWrite) && "ALLOW_WRITE".equals(confirmationIntent);
    }
}
