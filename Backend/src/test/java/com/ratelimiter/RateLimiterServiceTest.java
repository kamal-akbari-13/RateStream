package com.ratelimiter.service;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.dto.TokenBucketResult;
import com.ratelimiter.dto.UserStatsResponse;
import com.ratelimiter.exception.InvalidUserIdException;
import com.ratelimiter.exception.RedisUnavailableException;
import com.ratelimiter.redis.RedisTokenBucketExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimiterService.
 *
 * All Redis interactions are mocked — these tests cover
 * business logic, input validation, and response mapping.
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTokenBucketExecutor executor;

    @Mock
    private RateLimiterProperties properties;

    @InjectMocks
    private RateLimiterService service;

    @BeforeEach
    void setup() {
        when(properties.getKeyPrefix()).thenReturn("rate_limit");
        when(properties.getMaxTokens()).thenReturn(10);
        when(properties.getRefillRate()).thenReturn(2.0);
    }

    // ── Input Validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("Should throw InvalidUserIdException for null userId")
    void shouldRejectNullUserId() {
        assertThatThrownBy(() -> service.checkRateLimit(null))
                .isInstanceOf(InvalidUserIdException.class)
                .hasMessageContaining("must not be null or empty");
    }

    @Test
    @DisplayName("Should throw InvalidUserIdException for blank userId")
    void shouldRejectBlankUserId() {
        assertThatThrownBy(() -> service.checkRateLimit("   "))
                .isInstanceOf(InvalidUserIdException.class);
    }

    @Test
    @DisplayName("Should throw InvalidUserIdException for userId exceeding 256 chars")
    void shouldRejectOversizedUserId() {
        String longId = "a".repeat(257);
        assertThatThrownBy(() -> service.checkRateLimit(longId))
                .isInstanceOf(InvalidUserIdException.class)
                .hasMessageContaining("256");
    }

    // ── Allow / Reject Logic ───────────────────────────────────────────────

    @Test
    @DisplayName("Should return allowed=true when tokens available")
    void shouldAllowWhenTokensAvailable() {
        when(executor.executeTokenBucket(anyString()))
                .thenReturn(new TokenBucketResult(true, 7L));

        RateLimitResponse response = service.checkRateLimit("user1");

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getRemainingTokens()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Should return allowed=false when bucket exhausted")
    void shouldRejectWhenBucketExhausted() {
        when(executor.executeTokenBucket(anyString()))
                .thenReturn(new TokenBucketResult(false, 0L));

        RateLimitResponse response = service.checkRateLimit("user1");

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getRemainingTokens()).isEqualTo(0L);
    }

    // ── Redis Failure ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should propagate RedisUnavailableException on Redis failure")
    void shouldPropagateRedisFailure() {
        when(executor.executeTokenBucket(anyString()))
                .thenThrow(new RedisUnavailableException("Redis down", null));

        assertThatThrownBy(() -> service.checkRateLimit("user1"))
                .isInstanceOf(RedisUnavailableException.class);
    }

    // ── Key Building ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should build correct Redis key from prefix and userId")
    void shouldBuildCorrectRedisKey() {
        String key = service.buildKey("user42");
        assertThat(key).isEqualTo("rate_limit:user42");
    }

    @Test
    @DisplayName("Should trim whitespace from userId in key")
    void shouldTrimUserIdInKey() {
        String key = service.buildKey("  user42  ");
        assertThat(key).isEqualTo("rate_limit:user42");
    }

    // ── Admin Stats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return bucketExists=false when key not in Redis")
    void shouldReturnNoBucketWhenKeyMissing() {
        when(executor.getBucketState(anyString())).thenReturn(new HashMap<>());

        UserStatsResponse stats = service.getStats("newUser");

        assertThat(stats.isBucketExists()).isFalse();
        assertThat(stats.getRemainingTokens()).isEqualTo(10.0);  // defaults to maxTokens
    }

    @Test
    @DisplayName("Should return correct stats when bucket exists")
    void shouldReturnStatsForExistingBucket() {
        Map<Object, Object> mockData = new HashMap<>();
        mockData.put("tokens", "5.0");
        mockData.put("last_refill_timestamp", "1712345678.0");
        when(executor.getBucketState(anyString())).thenReturn(mockData);

        UserStatsResponse stats = service.getStats("user1");

        assertThat(stats.isBucketExists()).isTrue();
        assertThat(stats.getRemainingTokens()).isEqualTo(5.0);
        assertThat(stats.getLastRefillTime()).isEqualTo(1712345678.0);
    }
}
