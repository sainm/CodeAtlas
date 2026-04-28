package org.sainm.codeatlas.ai;

public record AiPrompt(
    String task,
    String system,
    String user
) {
    public AiPrompt {
        task = require(task, "task");
        system = require(system, "system");
        user = require(user, "user");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
