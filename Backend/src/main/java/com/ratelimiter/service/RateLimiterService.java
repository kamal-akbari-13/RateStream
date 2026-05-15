package com.ratelimiter.service;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.dto.MetricsResponse;
import com.ratelimiter.dto.TokenBucketResult;
import com.ratelimiter.dto.UserStatsResponse;
import com.ratelimiter.exception.InvalidUserIdException;
import com.ratelimiter.redis.RedisTokenBucketExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Business logic layer for rate limiting.
 *
 * Responsibilities:
 *  1. Validate input (userId)
 *  2. Build the Redis key
 *  3. Delegate to RedisTokenBucketExecutor for atomic execution
 *  4. Map the low-level result to API-level DTOs
 *  5. Log meaningful events (rate limit hits, etc.)
 *
 * This layer is Redis-agnostic — it works with domain types only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisTokenBucketExecutor executor;
    private final RateLimiterProperties    properties;

    // ── In-memory request counters (process-local; reset on restart) ───────
    // Use AtomicInteger for thread-safe increment without synchronization overhead.
    // Note: in a multi-instance deployment each instance tracks its own counts.
    // For cluster-wide totals, persist these to Redis instead.
    private final AtomicInteger allowedCount = new AtomicInteger(0);
    private final AtomicInteger blockedCount = new AtomicInteger(0);

    // ── Core rate limiting ─────────────────────────────────────────────────

    /**
     * Evaluate whether the request from {@code userId} should be allowed.
     *
     * @param userId  Caller identity (must not be null/blank)
     * @return        RateLimitResponse indicating allow/reject + remaining tokens
     * @throws InvalidUserIdException    if userId is invalid
     * @throws com.ratelimiter.exception.RedisUnavailableException if Redis is down
     */
    public RateLimitResponse checkRateLimit(String userId) {
        // ── Edge Case 7: Validate userId ──────────────────────────────────
        validateUserId(userId);

        String redisKey = buildKey(userId);
        log.debug("Rate limit check → userId={}, key={}", userId, redisKey);

        TokenBucketResult result = executor.executeTokenBucket(redisKey);

        if (result.isAllowed()) {
            allowedCount.incrementAndGet();
            log.debug("ALLOWED → userId={}, remaining={}", userId, result.getRemainingTokens());
            return RateLimitResponse.allowed(result.getRemainingTokens());
        } else {
            blockedCount.incrementAndGet();
            // ── Edge Case 2: Tokens exhausted ─────────────────────────────
            // Calculate wait time: 1 token / refillRate tokens-per-second.
            // ceil() ensures we never tell a client to retry too early.
            long retryAfter = calculateRetryAfterSeconds();
            log.warn("RATE LIMITED → userId={}, retryAfter={}s", userId, retryAfter);
            return RateLimitResponse.rejected(retryAfter);
        }
    }

    // ── Admin stats ────────────────────────────────────────────────────────

    /**
     * Retrieve current bucket statistics for a user (read-only, no token consumption).
     *
     * @param userId  Target user
     * @return        UserStatsResponse with current state
     */
    public UserStatsResponse getStats(String userId) {
        validateUserId(userId);

        String              redisKey = buildKey(userId);
        Map<Object, Object> state    = executor.getBucketState(redisKey);

        if (state == null || state.isEmpty()) {
            // Bucket doesn't exist (user never made a request, or key expired)
            log.debug("No bucket state found for userId={}", userId);
            return UserStatsResponse.builder()
                    .userId(userId)
                    .remainingTokens(properties.getMaxTokens())  // conceptually full
                    .lastRefillTime(0)
                    .maxTokens(properties.getMaxTokens())
                    .refillRate(properties.getRefillRate())
                    .bucketExists(false)
                    .build();
        }

        double tokens    = parseDouble(state.get("tokens"));
        double timestamp = parseDouble(state.get("last_refill_timestamp"));

        log.debug("Stats for userId={} → tokens={}, lastRefill={}", userId, tokens, timestamp);

        return UserStatsResponse.builder()
                .userId(userId)
                .remainingTokens(tokens)
                .lastRefillTime(timestamp)
                .maxTokens(properties.getMaxTokens())
                .refillRate(properties.getRefillRate())
                .bucketExists(true)
                .build();
    }

    // ── Admin metrics ──────────────────────────────────────────────────────

    /**
     * Returns the cumulative allowed/blocked counters since last startup.
     * Counters are in-process; they reset on application restart.
     */
    public MetricsResponse getMetrics() {
        return new MetricsResponse(allowedCount.get(), blockedCount.get());
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Seconds a client must wait for at least 1 token to become available.
     *
     * Formula: ceil(1 / refillRate)
     *   refillRate = 2 tok/sec → ceil(0.5)  = 1 second
     *   refillRate = 0.5 tok/sec → ceil(2.0) = 2 seconds
     *
     * Minimum return value is 1 — never tell a client "retry immediately"
     * when the bucket is actually empty.
     */
    private long calculateRetryAfterSeconds() {
        double secondsPerToken = 1.0 / properties.getRefillRate();
        return Math.max(1L, (long) Math.ceil(secondsPerToken));
    }

    /**
     * Build the Redis key for a given userId.
     * Pattern: "{keyPrefix}:{userId}"  e.g., "rate_limit:user123"
     */
    String buildKey(String userId) {
        return properties.getKeyPrefix() + ":" + userId.trim();
    }

    /**
     * Validate that userId is non-null and non-blank.
     * Trim whitespace to catch " " edge cases.
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidUserIdException(
                    "userId must not be null or empty. Provide a valid user identifier.");
        }
        // Reject suspiciously long IDs (guard against key injection)
        if (userId.trim().length() > 256) {
            throw new InvalidUserIdException("userId exceeds maximum allowed length of 256 characters");
        }
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Redis value '{}' as double", value);
            return 0.0;
        }
    }
}
