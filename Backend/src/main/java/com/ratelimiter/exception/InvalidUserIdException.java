package com.ratelimiter.exception;

/**
 * Thrown when a userId is null, blank, or otherwise invalid.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidUserIdException extends RuntimeException {

    public InvalidUserIdException(String message) {
        super(message);
    }
}
