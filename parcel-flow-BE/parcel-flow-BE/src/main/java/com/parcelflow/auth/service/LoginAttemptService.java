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
 *  - {maxAttempts} consecutive failures  -> temporary lock for {tempLockSeconds},
 *    and the temp-lock counter is incremented (TTL {escalationWindowSeconds}).
 *  - The account may be temporarily locked up to {maxTempLocks} times. Once it has
 *    already been temp-locked {maxTempLocks} times within the escalation window and
 *    {maxAttempts} failures happen yet again -> PERMANENT (caller deactivates the
 *    account).
 *
 * <p>Example with the defaults (maxAttempts=3, maxTempLocks=3): three separate
 * bursts of 3 failures each produce three 15-minute locks; a fourth burst after
 * the third lock expires escalates to a permanent lock.
 */
@Slf4j
@Service
public class LoginAttemptService {

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final int maxTempLocks;
    private final long tempLockSeconds;
    private final long escalationWindowSeconds;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${app.lockout.max-attempts}") int maxAttempts,
                               @Value("${app.lockout.max-temp-locks}") int maxTempLocks,
                               @Value("${app.lockout.temp-lock-seconds}") long tempLockSeconds,
                               @Value("${app.lockout.escalation-window-seconds}") long escalationWindowSeconds) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.maxTempLocks = maxTempLocks;
        this.tempLockSeconds = tempLockSeconds;
        this.escalationWindowSeconds = escalationWindowSeconds;
    }

    private String failKey(String email) {
        return "login:fail:" + email;
    }

    private String lockKey(String email) {
        return "login:lock:" + email;
    }

    private String tempLockCountKey(String email) {
        return "login:templocks:" + email;
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
            // A burst just reached the failure threshold. Count this temp-lock
            // occurrence; the counter lives for the whole escalation window so
            // repeated lockouts accumulate towards the permanent-lock verdict.
            Long tempLocks = redis.opsForValue().increment(tempLockCountKey(email));
            if (tempLocks != null && tempLocks == 1L) {
                redis.expire(tempLockCountKey(email), escalationWindowSeconds, TimeUnit.SECONDS);
            }
            redis.delete(failKey(email));

            if (tempLocks != null && tempLocks > maxTempLocks) {
                // Already temp-locked maxTempLocks times and still failing.
                clear(email);
                return AttemptResult.PERMANENT;
            }
            redis.opsForValue().set(lockKey(email), "1", tempLockSeconds, TimeUnit.SECONDS);
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
        redis.delete(tempLockCountKey(email));
    }
}
