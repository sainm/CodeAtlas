package org.sainm.codeatlas.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class McpAuditLog {
    private static final List<Pattern> SECRET_PATTERNS = List.of(
        Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)([^\\s\"']+)"),
        Pattern.compile("(?i)(password\\s*[=:]\\s*)([^\\s\"']+)"),
        Pattern.compile("(?i)(secret\\s*[=:]\\s*)([^\\s\"']+)"),
        Pattern.compile("(?i)(token\\s*[=:]\\s*)([^\\s\"']+)")
    );
    private final List<McpAuditEvent> events = new CopyOnWriteArrayList<>();

    public void recordAllowed(McpRequestContext context, McpToolName toolName, Map<String, Object> arguments) {
        record(context, toolName, true, "allowed", arguments);
    }

    public void recordDenied(McpRequestContext context, McpToolName toolName, String reason, Map<String, Object> arguments) {
        record(context, toolName, false, reason, arguments);
    }

    public List<McpAuditEvent> events() {
        return List.copyOf(events);
    }

    private void record(
        McpRequestContext context,
        McpToolName toolName,
        boolean allowed,
        String reason,
        Map<String, Object> arguments
    ) {
        McpRequestContext safeContext = context == null ? McpRequestContext.anonymousAllowAll() : context;
        events.add(new McpAuditEvent(
            Instant.now(),
            safeContext.principal(),
            toolName == null ? "_unknown" : toolName.value(),
            allowed,
            reason,
            redactArguments(arguments)
        ));
    }

    private Map<String, Object> redactArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        return arguments.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> redactValue(entry.getValue())));
    }

    private Object redactValue(Object value) {
        if (value instanceof String text) {
            String redacted = text;
            for (Pattern pattern : SECRET_PATTERNS) {
                redacted = pattern.matcher(redacted).replaceAll("$1[REDACTED]");
            }
            return redacted;
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String)
                .collect(Collectors.toUnmodifiableMap(entry -> (String) entry.getKey(), entry -> redactValue(entry.getValue())));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::redactValue).toList();
        }
        return value;
    }
}
