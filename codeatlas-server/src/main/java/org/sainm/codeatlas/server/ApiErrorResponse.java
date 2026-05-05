package org.sainm.codeatlas.server;

public record ApiErrorResponse(
        String requestId,
        String code,
        String message,
        String details,
        boolean retryable,
        int status) {
}
