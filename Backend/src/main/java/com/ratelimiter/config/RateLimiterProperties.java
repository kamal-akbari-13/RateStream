package com.ratelimiter.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Strongly-typed configuration class bound from application.yml.
 *
 * All rate limiter tunables live here — no magic strings scattered across code.
 * Change behaviour entirely by editing application.yml without touching Java.
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /**
     * Maximum tokens a bucket can hold.
     * Determines the "burst" capacity — how many simultaneous requests
     * can pass immediately when the bucket is full.
     * Default: 10
     */
    private int maxTokens = 10;

    /**
     * Tokens added per second (refill rate).
     * Controls sustained throughput — a user can make at most
     * refillRate requests per second continuously.
     * Default: 2 tokens/sec
     */
    private double refillRate = 2.0;

    /**
     * Redis key prefix. Final key pattern: "{keyPrefix}:{userId}"
     * Default: "rate_limit"
     */
    private String keyPrefix = "rate_limit";

    /**
     * TTL in seconds for Redis keys. Keys auto-expire after this period
     * of inactivity, cleaning up memory for users who stop sending requests.
     * Default: 3600 (1 hour)
     */
    private long ttlSeconds = 3600;

    @PostConstruct
    public void logConfig() {
        log.info("Rate Limiter Config → maxTokens={}, refillRate={} tok/sec, ttl={}s",
                maxTokens, refillRate, ttlSeconds);
    }
}
