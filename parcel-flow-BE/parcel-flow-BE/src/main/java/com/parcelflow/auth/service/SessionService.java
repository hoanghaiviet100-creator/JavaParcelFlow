package com.parcelflow.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * One active session per user in Redis, enforcing the single-session / IP policy.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

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
        if (!s.ip().equals(ip)) {
            throw new ApiException(ErrorCode.AUTH_IP_NOT_ALLOWED, "Refresh from a different IP is not allowed");
        }
        if (!s.refreshId().equals(providedRefreshId)) {
            throw new ApiException(ErrorCode.AUTH_INVALID_TOKEN, "Refresh token is no longer valid");
        }
        write(userId, new SessionData(newAccessJti, newRefreshId, ip));
    }

    public void invalidate(Long userId) {
        redis.delete(key(userId));
    }

    private void write(Long userId, SessionData data) {
        try {
            redis.opsForValue().set(key(userId), objectMapper.writeValueAsString(data),
                    sessionTtlSeconds, TimeUnit.SECONDS);
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
