package com.ratelimiter.controller;

import com.ratelimiter.dto.MetricsResponse;
import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.dto.UserStatsResponse;
import com.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
/**
 * HTTP entry point for rate-limited resource access and admin statistics.
 *
 * The controller is intentionally thin — it only handles:
 *  1. HTTP parameter extraction
 *  2. Delegating to the service
 *  3. Mapping service responses to HTTP status codes
 *
 * All business logic lives in RateLimiterService.
 */
@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    // ── Protected Resource Endpoint ────────────────────────────────────────

    /**
     * GET /api/resource?userId=123
     *
     * Checks the token bucket for the given user:
     *   - 200 OK             → request allowed, bucket had a token
     *   - 429 Too Many Reqs  → bucket exhausted, retry later
     *   - 400 Bad Request    → invalid/missing userId
     *   - 503 Unavailable    → Redis is down
     *
     * In a real system, this endpoint would proxy to an actual downstream
     * service after passing the rate limit check.
     *
     * Sample:
     *   curl "http://localhost:8080/api/resource?userId=user123"
     */
    @GetMapping("/resource")
    public ResponseEntity<RateLimitResponse> accessResource(
            @RequestParam(value = "userId") String userId) {

        log.info("Incoming request → GET /api/resource, userId={}", userId);

        RateLimitResponse response = rateLimiterService.checkRateLimit(userId);

        if (response.isAllowed()) {
            // 200 OK — include the response body with remaining token count
            return ResponseEntity.ok(response);
        } else {
            // 429 Too Many Requests — standard status for rate limiting.
            // The Retry-After header (RFC 6585 §4) tells HTTP clients and
            // intermediaries (proxies, CDNs) exactly how long to wait.
            // Value mirrors retryAfterSeconds in the response body so clients
            // only need to read one place.
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(response.getRetryAfterSeconds()))
                    .body(response);
        }
    }

    // ── Admin Stats Endpoint ───────────────────────────────────────────────

    /**
     * GET /api/admin/stats?userId=123
     *
     * Returns the current bucket state for inspection without consuming a token.
     * In production, this endpoint should be secured (e.g., Spring Security + role).
     *
     * Sample:
     *   curl "http://localhost:8080/api/admin/stats?userId=user123"
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<UserStatsResponse> getUserStats(
            @RequestParam(value = "userId") String userId) {

        log.info("Admin stats request → GET /api/admin/stats, userId={}", userId);

        UserStatsResponse stats = rateLimiterService.getStats(userId);
        return ResponseEntity.ok(stats);
    }

    // ── Admin Metrics Endpoint ─────────────────────────────────────────────

    /**
     * GET /api/admin/metrics
     *
     * Returns cumulative allowed/blocked request counts for this instance
     * since last startup. Counters are in-memory — they reset on restart.
     *
     * Sample:
     *   curl "http://localhost:8080/api/admin/metrics"
     */
    @GetMapping("/admin/metrics")
    public ResponseEntity<MetricsResponse> getMetrics() {
        log.info("Admin metrics request → GET /api/admin/metrics");
        return ResponseEntity.ok(rateLimiterService.getMetrics());
    }
}
