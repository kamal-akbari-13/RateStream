package com.ratelimiter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response body for the protected resource endpoint.
 *
 * GET /api/resource?userId=123
 * → 200 OK  : { "allowed": true,  "remainingTokens": 7, "retryAfterSeconds": 0 }
 * → 429     : { "allowed": false, "remainingTokens": 0, "retryAfterSeconds": 1 }
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResponse {

    /** Whether the request was allowed through. */
    @JsonProperty("allowed")
    private boolean allowed;

    /** Tokens left in the bucket after this request. */
    @JsonProperty("remainingTokens")
    private long remainingTokens;

    /**
     * Seconds the client should wait before retrying.
     *
     * Formula (when rejected): ceil(1 / refillRate)
     * Example: refillRate=2 tok/sec → ceil(0.5) = 1 second
     * Always 0 for allowed requests.
     *
     * Mirrors the value sent in the HTTP Retry-After response header.
     */
    @JsonProperty("retryAfterSeconds")
    private long retryAfterSeconds;

    /** Optional human-readable message. */
    @JsonProperty("message")
    private String message;

    // ── Convenience factories ──────────────────────────────────────────────

    public static RateLimitResponse allowed(long remainingTokens) {
        return RateLimitResponse.builder()
                .allowed(true)
                .remainingTokens(remainingTokens)
                .retryAfterSeconds(0)
                .message("Request allowed")
                .build();
    }

    /**
     * Build a rejected response with the calculated retry delay.
     *
     * @param retryAfterSeconds seconds until at least 1 token is available again
     */
    public static RateLimitResponse rejected(long retryAfterSeconds) {
        return RateLimitResponse.builder()
                .allowed(false)
                .remainingTokens(0)
                .retryAfterSeconds(retryAfterSeconds)
                .message("Rate limit exceeded. Retry after " + retryAfterSeconds + " second(s).")
                .build();
    }
}
