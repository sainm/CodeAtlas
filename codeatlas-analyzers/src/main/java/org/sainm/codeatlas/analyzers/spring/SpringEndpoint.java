package org.sainm.codeatlas.analyzers.spring;

public record SpringEndpoint(
    String httpMethod,
    String path,
    String controllerClass,
    String handlerMethod,
    int line
) {
    public SpringEndpoint {
        httpMethod = httpMethod == null || httpMethod.isBlank() ? "ANY" : httpMethod.trim().toUpperCase();
        path = normalize(path);
        controllerClass = require(controllerClass, "controllerClass");
        handlerMethod = require(handlerMethod, "handlerMethod");
        line = Math.max(0, line);
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
