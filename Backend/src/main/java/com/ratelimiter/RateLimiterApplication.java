package com.ratelimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Entry point for the Distributed Rate Limiter application.
 *
 * Architecture Overview:
 * ─────────────────────
 *  HTTP Request
 *      │
 *      ▼
 *  RateLimiterController          ← validates input, maps HTTP
 *      │
 *      ▼
 *  RateLimiterService             ← orchestrates business logic
 *      │
 *      ▼
 *  RedisTokenBucketExecutor       ← executes Lua script atomically
 *      │
 *      ▼
 *  Redis (centralized store)      ← single source of truth across instances
 */
@Slf4j
@SpringBootApplication
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║   Distributed Rate Limiter — READY               ║");
        log.info("║   Algorithm  : Token Bucket                      ║");
        log.info("║   Store      : Redis (Lua atomic scripts)        ║");
        log.info("║   Endpoints  : GET /api/resource?userId=<id>     ║");
        log.info("║              : GET /api/admin/stats?userId=<id>  ║");
        log.info("╚══════════════════════════════════════════════════╝");
    }
}
