package com.example.redislock.exception;

import com.example.redislock.norepeat.DuplicateSubmitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        log.warn("Invalid request: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleStateConflict(IllegalStateException e) {
        log.warn("State conflict: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "State conflict", e.getMessage());
    }

    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<Map<String, Object>> handleServiceBusy(ServiceBusyException e) {
        log.warn("Service busy: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", e.getMessage());
    }

    @ExceptionHandler(DuplicateSubmitException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSubmit(DuplicateSubmitException e) {
        log.warn("Duplicate submit: {}", e.getMessage());
        return error(HttpStatus.TOO_MANY_REQUESTS, "Duplicate submit", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("Unexpected API error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "Request failed");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "success", false,
                "error", error,
                "message", message
        ));
    }
}
