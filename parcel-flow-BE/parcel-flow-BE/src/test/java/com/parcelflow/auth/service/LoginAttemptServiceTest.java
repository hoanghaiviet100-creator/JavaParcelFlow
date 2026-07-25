package com.parcelflow.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    private static final String EMAIL = "user@example.com";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private LoginAttemptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new LoginAttemptService(redis, 3, 3, 900, 86400);
    }

    /** Drives one burst of three consecutive failures and returns the final verdict. */
    private AttemptResult failThreeTimes() {
        when(valueOps.increment("login:fail:" + EMAIL)).thenReturn(1L, 2L, 3L);
        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.JUST_FAILED);
        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.JUST_FAILED);
        return service.onFailedLogin(EMAIL);
    }

    @Test
    void threeConsecutiveFailures_triggerTemporaryLock() {
        when(valueOps.increment("login:templocks:" + EMAIL)).thenReturn(1L);

        assertThat(failThreeTimes()).isEqualTo(AttemptResult.TEMP_LOCKED);

        verify(valueOps).set(eq("login:lock:" + EMAIL), eq("1"), eq(900L), any());
        verify(redis).expire(eq("login:templocks:" + EMAIL), eq(86400L), any());
    }

    /** The 2nd and 3rd lockouts must still be temporary — not an instant permanent lock. */
    @Test
    void secondAndThirdLockouts_remainTemporary() {
        when(valueOps.increment("login:templocks:" + EMAIL)).thenReturn(2L);
        assertThat(failThreeTimes()).isEqualTo(AttemptResult.TEMP_LOCKED);

        when(valueOps.increment("login:templocks:" + EMAIL)).thenReturn(3L);
        assertThat(failThreeTimes()).isEqualTo(AttemptResult.TEMP_LOCKED);

        verify(valueOps, times(2)).set(eq("login:lock:" + EMAIL), eq("1"), eq(900L), any());
    }

    /** Only after three temporary locks does a further failed burst lock permanently. */
    @Test
    void failingAgainAfterThreeTemporaryLocks_triggersPermanentLock() {
        when(valueOps.increment("login:templocks:" + EMAIL)).thenReturn(4L);

        assertThat(failThreeTimes()).isEqualTo(AttemptResult.PERMANENT);

        verify(valueOps, never()).set(eq("login:lock:" + EMAIL), eq("1"), eq(900L), any());
    }

    @Test
    void isTemporarilyLocked_reflectsLockKey() {
        when(redis.hasKey("login:lock:" + EMAIL)).thenReturn(true);
        assertThat(service.isTemporarilyLocked(EMAIL)).isTrue();
    }
}
