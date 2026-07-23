package com.parcelflow.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fixed-window request counter in Redis.
 *
 * <p>The count-and-expire is a single Lua script so the increment and the TTL
 * are applied atomically. Doing it as INCR then EXPIRE from Java has a race: if
 * the process dies between the two calls the key never expires and the caller is
 * locked out permanently. It also mirrors the fix already made in
 * SessionService — compound Redis operations here go through one script.
 *
 * <p>Fixed window (not sliding) is deliberate: it needs one key and one round
 * trip, which is all a coarse abuse backstop requires. The per-account lockout
 * in LoginAttemptService remains the precise control; this only blunts volume.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    /**
     * Returns the request count in the current window after counting this call.
     * Sets the TTL only when the window opens (count == 1), so the window slides
     * forward exactly once per period rather than on every hit.
     */
    private static final RedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * @return true when the caller is within the limit, false when this request
     *         pushes them over it.
     */
    public boolean tryAcquire(String bucket, String identity, int limit, int windowSeconds) {
        String key = "ratelimit:" + bucket + ":" + identity;
        Long count = redis.execute(COUNT_SCRIPT, List.of(key), String.valueOf(windowSeconds));
        return count != null && count <= limit;
    }
}
