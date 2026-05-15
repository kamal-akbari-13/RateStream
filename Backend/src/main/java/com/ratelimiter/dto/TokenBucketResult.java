package com.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * Internal value object representing the result returned by the Lua script.
 *
 * Lua returns: { allowed (1|0), remainingTokens }
 * Java maps:   List<Long> → this DTO
 *
 * Keeping this separate from the HTTP response DTO maintains
 * a clean boundary between Redis internals and the API layer.
 */
@Getter
@ToString
@AllArgsConstructor
public class TokenBucketResult {

    /** True if the request was allowed (Lua returned 1). */
    private final boolean allowed;

    /** Tokens remaining after this request (floor'd to integer by Lua). */
    private final long remainingTokens;
}
