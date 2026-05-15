package com.ratelimiter.redis;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.dto.TokenBucketResult;
import com.ratelimiter.exception.RedisUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Low-level Redis executor responsible for running the Token Bucket Lua script.
 *
 * This class is the ONLY place in the application that touches Redis directly.
 * All rate limiting state mutation happens here, atomically, via Lua.
 *
 * Why this separation?
 * ────────────────────
 * - Service layer stays clean — no Redis types leak upward
 * - Easy to swap Redis implementation (e.g., Redisson, Jedis) without touching business logic
 * - Single place to handle Redis errors and wrap them in domain exceptions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBucketExecutor {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final RateLimiterProperties properties;

    /**
     * Execute the token bucket Lua script atomically for the given user key.
     *
     * Redis EVAL guarantees:
     *  - No other Redis command runs between lines of the script
     *  - Safe across any number of parallel server instances
     *
     * @param redisKey  The full Redis key (e.g., "rate_limit:user123")
     * @return          TokenBucketResult with allowed flag and remaining tokens
     * @throws RedisUnavailableException if Redis connection fails
     */
    @SuppressWarnings("unchecked")
    public TokenBucketResult executeTokenBucket(String redisKey) {
        // Current Unix time in seconds (floating point for sub-second precision)
        double currentTimeSeconds = Instant.now().toEpochMilli() / 1000.0;

        log.debug("Executing Lua script → key={}, maxTokens={}, refillRate={}, time={}",
                redisKey, properties.getMaxTokens(), properties.getRefillRate(), currentTimeSeconds);

        try {
            /*
             * RedisTemplate.execute(script, keys, args...) maps to:
             *   EVAL <sha1> 1 <redisKey> <maxTokens> <refillRate> <currentTime> <ttlSeconds>
             *
             * Spring will use EVALSHA on subsequent calls (SHA cached after first EVAL).
             */
            List<Long> result = (List<Long>) redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(redisKey),   // KEYS array
                    String.valueOf(properties.getMaxTokens()),
                    String.valueOf(properties.getRefillRate()),
                    String.valueOf(currentTimeSeconds),
                    String.valueOf(properties.getTtlSeconds())
            );

            if (result == null || result.size() < 2) {
                log.error("Lua script returned unexpected result: {}", result);
                throw new RedisUnavailableException("Lua script returned null or incomplete result", null);
            }

            boolean allowed        = result.get(0) == 1L;
            long    remainingTokens = result.get(1);

            log.debug("Lua result → allowed={}, remaining={}", allowed, remainingTokens);
            return new TokenBucketResult(allowed, remainingTokens);

        } catch (RedisConnectionFailureException ex) {
            // ── Edge Case 6: Redis connection failure ─────────────────────
            log.error("Redis connection failure for key={}: {}", redisKey, ex.getMessage());
            throw new RedisUnavailableException(
                    "Failed to connect to Redis while processing key: " + redisKey, ex);

        } catch (RedisUnavailableException ex) {
            throw ex; // re-throw domain exceptions as-is

        } catch (Exception ex) {
            log.error("Unexpected error executing Lua script for key={}", redisKey, ex);
            throw new RedisUnavailableException(
                    "Unexpected Redis error for key: " + redisKey, ex);
        }
    }

    /**
     * Read the current bucket state for inspection (admin stats endpoint).
     * Uses HGETALL — read-only, no token consumption.
     *
     * @param redisKey  The full Redis key
     * @return          Map of field → value, or empty map if key doesn't exist
     */
    public Map<Object, Object> getBucketState(String redisKey) {
        try {
            Map<Object, Object> data = redisTemplate.opsForHash().entries(redisKey);
            log.debug("Read bucket state for key={}: {}", redisKey, data);
            return data;

        } catch (RedisConnectionFailureException ex) {
            log.error("Redis connection failure reading stats for key={}", redisKey, ex.getMessage());
            throw new RedisUnavailableException("Cannot read stats from Redis", ex);
        }
    }
}
