package com.ratelimiter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response body for the admin stats endpoint.
 *
 * GET /api/admin/stats?userId=123
 * → 200 OK: {
 *     "userId":           "123",
 *     "remainingTokens":  5,
 *     "lastRefillTime":   1712345678.0,
 *     "maxTokens":        10,
 *     "refillRate":       2.0
 *   }
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {

    @JsonProperty("userId")
    private String userId;

    /** Current tokens remaining in the bucket (may be fractional internally). */
    @JsonProperty("remainingTokens")
    private double remainingTokens;

    /** Unix epoch seconds when the bucket was last refilled. */
    @JsonProperty("lastRefillTime")
    private double lastRefillTime;

    /** Configured maximum tokens (from application.yml). */
    @JsonProperty("maxTokens")
    private int maxTokens;

    /** Configured refill rate in tokens/second. */
    @JsonProperty("refillRate")
    private double refillRate;

    /** Whether an active bucket exists for this user in Redis. */
    @JsonProperty("bucketExists")
    private boolean bucketExists;
}
