package com.ratelimiter.exception;

/**
 * Thrown when Redis is unavailable or returns an unexpected error.
 * Maps to HTTP 503 Service Unavailable.
 *
 * Design choice: fail-open vs fail-closed.
 * ─────────────────────────────────────────
 * This application uses FAIL-CLOSED: if Redis is down, requests are rejected
 * with 503 rather than silently allowed. This is the safer default for
 * APIs that need hard rate limits (billing, abuse prevention, etc.).
 *
 * For APIs where availability > strict limiting, catch this exception in
 * the controller and return 200 instead (fail-open).
 */
public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
