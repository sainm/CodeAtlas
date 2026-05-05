package org.sainm.codeatlas.server;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
final class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiErrorResponse(
                requestId(),
                exception.code(),
                exception.getMessage(),
                exception.details(),
                exception.retryable(),
                exception.status().value()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(
                requestId(),
                "INVALID_REQUEST",
                exception.getMessage(),
                "",
                false,
                HttpStatus.BAD_REQUEST.value()));
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
