package com.ratelimiter;

import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using a real Redis instance via TestContainers.
 *
 * These tests validate the full stack:
 *   HTTP → Controller → Service → Lua Script → Redis → Response
 *
 * Run with: mvn verify
 * Requires Docker to be running.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimiterIntegrationTest {

    @Container
    static RedisContainer redis = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.2")
    );

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port",  () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimiterService rateLimiterService;

    // ── Happy Path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("First request for a new user should be allowed")
    void firstRequestShouldBeAllowed() {
        ResponseEntity<RateLimitResponse> response = makeRequest("integrationUser1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Requests within token budget should all be allowed")
    void requestsWithinBudgetShouldBeAllowed() {
        String userId = "budgetUser";
        // maxTokens = 10 per config; first 10 requests must succeed
        for (int i = 0; i < 10; i++) {
            ResponseEntity<RateLimitResponse> response = makeRequest(userId);
            assertThat(response.getStatusCode())
                    .as("Request #" + (i + 1) + " should be allowed")
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("11th request should be rate limited (429)")
    void eleventhRequestShouldBeRateLimited() {
        String userId = "exhaustedUser";
        // Drain the bucket (10 tokens)
        for (int i = 0; i < 10; i++) {
            makeRequest(userId);
        }
        // 11th should fail
        ResponseEntity<RateLimitResponse> response = makeRequest(userId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().isAllowed()).isFalse();
    }

    // ── Concurrency Test ───────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrent requests: total allowed should never exceed maxTokens")
    void concurrentRequestsShouldRespectMaxTokens() throws InterruptedException {
        String userId          = "concurrentUser";
        int    concurrentCount = 30;  // Send 30 simultaneous requests
        int    maxTokens       = 10;  // From application.yml

        ExecutorService pool        = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch  startLatch  = new CountDownLatch(1);
        CountDownLatch  doneLatch   = new CountDownLatch(concurrentCount);
        AtomicInteger   allowedCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();  // All threads start simultaneously
                    ResponseEntity<RateLimitResponse> response = makeRequest(userId);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        allowedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();              // Release all threads at once
        doneLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // CRITICAL: Lua atomicity guarantees exactly maxTokens requests succeed
        assertThat(allowedCount.get())
                .as("Concurrent requests should never exceed maxTokens=%d", maxTokens)
                .isLessThanOrEqualTo(maxTokens);
    }

    // ── Admin Stats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin stats endpoint should return bucket info")
    void adminStatsShouldReturnData() {
        String userId = "statsUser";
        makeRequest(userId); // Create the bucket

        ResponseEntity<Object> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/admin/stats?userId=" + userId,
                Object.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Validation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing userId should return 400")
    void missingUserIdShouldReturn400() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/resource",
                Object.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private ResponseEntity<RateLimitResponse> makeRequest(String userId) {
        return restTemplate.getForEntity(
                "http://localhost:" + port + "/api/resource?userId=" + userId,
                RateLimitResponse.class
        );
    }
}
