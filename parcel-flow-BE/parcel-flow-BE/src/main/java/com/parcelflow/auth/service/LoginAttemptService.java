package com.parcelflow.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Failed-login tracking and lock escalation, backed by Redis.
 *
 * Rule:
 *  - {maxAttempts} consecutive failures  -> temporary lock for {tempLockSeconds}
 *    and an "escalation" flag valid for {escalationWindowSeconds}.
 *  - After the temporary lock expires, if {maxAttempts} failures happen again while
 *    the escalation flag is still active -> PERMANENT (caller deactivates the account).
 */
@Slf4j
@Service
public class LoginAttemptService {

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final long tempLockSeconds;
    private final long escalationWindowSeconds;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${app.lockout.max-attempts}") int maxAttempts,
                               @Value("${app.lockout.temp-lock-seconds}") long tempLockSeconds,
                               @Value("${app.lockout.escalation-window-seconds}") long escalationWindowSeconds) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.tempLockSeconds = tempLockSeconds;
        this.escalationWindowSeconds = escalationWindowSeconds;
    }

    private String failKey(String email) {
        return "login:fail:" + email;
    }

    private String lockKey(String email) {
        return "login:lock:" + email;
    }

    private String escalatedKey(String email) {
        return "login:escalated:" + email;
    }

    public boolean isTemporarilyLocked(String email) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey(email)));
    }

    public AttemptResult onFailedLogin(String email) {
        Long count = redis.opsForValue().increment(failKey(email));
        if (count != null && count == 1L) {
            redis.expire(failKey(email), escalationWindowSeconds, TimeUnit.SECONDS);
        }

        if (count != null && count >= maxAttempts) {
            boolean escalated = Boolean.TRUE.equals(redis.hasKey(escalatedKey(email)));
            if (escalated) {
                clear(email);
                return AttemptResult.PERMANENT;
            }
            redis.opsForValue().set(lockKey(email), "1", tempLockSeconds, TimeUnit.SECONDS);
            redis.opsForValue().set(escalatedKey(email), "1", escalationWindowSeconds, TimeUnit.SECONDS);
            redis.delete(failKey(email));
            return AttemptResult.TEMP_LOCKED;
        }
        return AttemptResult.JUST_FAILED;
    }

    public void onSuccessfulLogin(String email) {
        clear(email);
    }

    public void clear(String email) {
        redis.delete(failKey(email));
        redis.delete(lockKey(email));
        redis.delete(escalatedKey(email));
    }
}
