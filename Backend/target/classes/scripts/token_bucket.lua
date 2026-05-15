--[[
  Token Bucket Rate Limiter - Atomic Lua Script
  Executed atomically on Redis via EVAL command.

  Why Lua?
  --------
  Redis executes Lua scripts atomically — no other command can run
  between any two lines of this script. This eliminates all race conditions
  in a distributed environment with multiple server instances.

  KEYS[1]  = Redis key for this user (e.g., "rate_limit:user123")
  ARGV[1]  = maxTokens      (integer)
  ARGV[2]  = refillRate     (tokens per second, float)
  ARGV[3]  = currentTime    (Unix epoch in seconds, float)
  ARGV[4]  = ttlSeconds     (TTL to set on the key)

  Returns a two-element array:
    [1] = 1 if request is ALLOWED, 0 if REJECTED
    [2] = remaining tokens AFTER this request (integer)
--]]

local key            = KEYS[1]
local maxTokens      = tonumber(ARGV[1])
local refillRate     = tonumber(ARGV[2])
local currentTime    = tonumber(ARGV[3])
local ttlSeconds     = tonumber(ARGV[4])

-- ── Step 1: Read current state from Redis ──────────────────────────────────
-- HGETALL returns a flat list: { field1, val1, field2, val2, ... }
local data = redis.call('HGETALL', key)

local tokens
local lastRefillTimestamp

if #data == 0 then
    -- ── Edge Case 1: First request — bucket does not exist yet ────────────
    -- Initialize with a FULL bucket minus the one token we're about to use.
    tokens             = maxTokens
    lastRefillTimestamp = currentTime
else
    -- Parse the hash fields into variables
    local fieldMap = {}
    for i = 1, #data, 2 do
        fieldMap[data[i]] = data[i + 1]
    end
    tokens              = tonumber(fieldMap['tokens'])
    lastRefillTimestamp = tonumber(fieldMap['last_refill_timestamp'])
end

-- ── Step 2: Calculate elapsed time and refill tokens ──────────────────────
local elapsed = currentTime - lastRefillTimestamp

-- ── Edge Case 4: Handle negative elapsed time (clock skew / NTP jumps) ───
-- If system clock goes backwards, treat elapsed as 0 — do NOT remove tokens.
if elapsed < 0 then
    elapsed = 0
end

-- Compute how many tokens should be added since last refill
local tokensToAdd = elapsed * refillRate

-- ── Step 3: Add refilled tokens, then cap at maxTokens ────────────────────
-- Edge Case 5: Token overflow prevention — never exceed maxTokens
tokens = math.min(maxTokens, tokens + tokensToAdd)

-- Update the refill timestamp to NOW only if we actually refilled something.
-- This prevents timestamp drift when bucket is already full.
if tokensToAdd > 0 then
    lastRefillTimestamp = currentTime
end

-- ── Step 4: Attempt to consume one token ──────────────────────────────────
local allowed
local remaining

if tokens >= 1 then
    -- ── Normal case: token available, consume it ──────────────────────────
    tokens    = tokens - 1
    allowed   = 1
    remaining = math.floor(tokens)   -- floor for safe integer representation
else
    -- ── Edge Case 2: Bucket exhausted ─────────────────────────────────────
    allowed   = 0
    remaining = 0
end

-- ── Step 5: Persist updated state back to Redis ───────────────────────────
-- HSET is atomic within the Lua script; together with EXPIRE this is safe
-- because the entire script runs atomically.
redis.call('HSET', key,
    'tokens',                tokens,
    'last_refill_timestamp', lastRefillTimestamp
)

-- ── Step 6: Reset TTL on every access (sliding expiry) ────────────────────
-- Edge Case 9: If key expired and was recreated above, TTL is set correctly.
-- For existing keys, this extends their lifetime on activity.
redis.call('EXPIRE', key, ttlSeconds)

-- ── Return result to Java caller ──────────────────────────────────────────
return { allowed, remaining }
