package com.parcelflow.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One active session per user in Redis, enforcing the single-session / IP policy.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    /**
     * Atomically replaces the stored session, but only while it still carries the
     * refresh id the caller presented.
     *
     * <p>rotate() used to read the session, compare the refresh id, then write
     * the replacement as three separate round trips. Two refreshes arriving
     * together both read the same session, both matched, and both wrote — so a
     * refresh token could be replayed concurrently even though replaying it
     * sequentially was correctly rejected, and whichever writer lost the race
     * had already handed its caller an access token whose jti was no longer the
     * active one.
     *
     * <p>Matching on the serialized {@code "refreshId":"..."} fragment is safe:
     * refresh ids are server-generated UUIDs, so the needle cannot collide with
     * another field's value.
     *
     * <p>Returns 1 when the swap happened, 0 when the refresh id was already
     * superseded, -1 when there is no session at all.
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
              return -1
            end
            if string.find(current, ARGV[1], 1, true) == nil then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${jwt.refresh-token-ttl-seconds}")
    private long sessionTtlSeconds;

    private String key(Long userId) {
        return "session:" + userId;
    }

    /** On login: same IP overwrites (kicks) the old session; a different IP is rejected. */
    public void handleLogin(Long userId, String ip, String accessJti, String refreshId) {
        SessionData existing = read(userId);
        if (existing != null && !existing.ip().equals(ip)) {
            throw new ApiException(ErrorCode.AUTH_IP_NOT_ALLOWED,
                    "An active session exists from a different IP address");
        }
        write(userId, new SessionData(accessJti, refreshId, ip));
    }

    public boolean isActiveAccessJti(Long userId, String jti) {
        SessionData s = read(userId);
        return s != null && s.accessJti().equals(jti);
    }

    public void rotate(Long userId, String ip, String providedRefreshId,
                       String newAccessJti, String newRefreshId) {
        SessionData s = read(userId);
        if (s == null) {
            throw new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "Session expired, please login again");
        }
        // The IP check is advisory and safe to evaluate on a plain read: it is not
        // what makes a replayed refresh token fail.
        if (!s.ip().equals(ip)) {
            throw new ApiException(ErrorCode.AUTH_IP_NOT_ALLOWED, "Refresh from a different IP is not allowed");
        }

        // The consume-and-replace of the refresh id must be atomic, or two
        // concurrent refreshes both succeed. See ROTATE_SCRIPT.
        Long result = redis.execute(
                ROTATE_SCRIPT,
                List.of(key(userId)),
                "\"refreshId\":\"" + providedRefreshId + "\"",
                serialize(new SessionData(newAccessJti, newRefreshId, ip)),
                String.valueOf(sessionTtlSeconds));

        if (result == null || result == -1L) {
            throw new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "Session expired, please login again");
        }
        if (result == 0L) {
            throw new ApiException(ErrorCode.AUTH_INVALID_TOKEN, "Refresh token is no longer valid");
        }
    }

    public void invalidate(Long userId) {
        redis.delete(key(userId));
    }

    private void write(Long userId, SessionData data) {
        redis.opsForValue().set(key(userId), serialize(data), sessionTtlSeconds, TimeUnit.SECONDS);
    }

    private String serialize(SessionData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize session");
        }
    }

    private SessionData read(Long userId) {
        String json = redis.opsForValue().get(key(userId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SessionData.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private record SessionData(String accessJti, String refreshId, String ip) {
    }
}
