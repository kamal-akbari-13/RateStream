package com.ratelimiter.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception → HTTP response mapping.
 *
 * All exceptions bubble up here from the service/redis layers.
 * Each handler produces a consistent JSON error envelope so clients
 * can parse errors reliably.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bad Request ────────────────────────────────────────────────────

    @ExceptionHandler(InvalidUserIdException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidUserId(InvalidUserIdException ex) {
        log.warn("Invalid userId: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    // ── 503 Service Unavailable ────────────────────────────────────────────

    @ExceptionHandler(RedisUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleRedisDown(RedisUnavailableException ex) {
        // Log at ERROR — this is an infrastructure failure requiring attention
        log.error("Redis unavailable: {}", ex.getMessage(), ex.getCause());
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Rate limiting service is temporarily unavailable. Please retry shortly.");
    }

    // ── 500 Internal Server Error (catch-all) ──────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("timestamp", Instant.now().toEpochMilli());
        return ResponseEntity.status(status).body(body);
    }
}
