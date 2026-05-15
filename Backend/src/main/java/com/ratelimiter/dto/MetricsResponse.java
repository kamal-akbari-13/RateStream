package com.ratelimiter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response body for the admin metrics endpoint.
 *
 * GET /api/admin/metrics
 * → 200 OK: {
 *     "allowedRequests": 142,
 *     "blockedRequests": 18
 *   }
 *
 * Counters are in-process and reset on application restart.
 * In a multi-instance deployment, each instance exposes its own counts.
 */
@Getter
@AllArgsConstructor
public class MetricsResponse {

    /** Total requests allowed since last startup. */
    @JsonProperty("allowedRequests")
    private final int allowedRequests;

    /** Total requests rejected (rate-limited) since last startup. */
    @JsonProperty("blockedRequests")
    private final int blockedRequests;
}
