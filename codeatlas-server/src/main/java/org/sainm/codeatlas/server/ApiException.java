package org.sainm.codeatlas.server;

import org.springframework.http.HttpStatus;

final class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String details;
    private final boolean retryable;

    ApiException(HttpStatus status, String code, String message, String details, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? "" : details;
        this.retryable = retryable;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }

    String details() {
        return details;
    }

    boolean retryable() {
        return retryable;
    }
}
