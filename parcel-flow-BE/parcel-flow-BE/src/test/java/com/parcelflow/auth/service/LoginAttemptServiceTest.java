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
        service = new LoginAttemptService(redis, 3, 900, 86400);
    }

    @Test
    void threeConsecutiveFailures_triggerTemporaryLock() {
        when(valueOps.increment("login:fail:" + EMAIL)).thenReturn(1L, 2L, 3L);
        when(redis.hasKey("login:escalated:" + EMAIL)).thenReturn(false);

        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.JUST_FAILED);
        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.JUST_FAILED);
        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.TEMP_LOCKED);

        verify(valueOps).set(eq("login:lock:" + EMAIL), eq("1"), eq(900L), any());
        verify(valueOps).set(eq("login:escalated:" + EMAIL), eq("1"), eq(86400L), any());
    }

    @Test
    void threeFailuresWhileEscalated_triggerPermanentLock() {
        when(valueOps.increment("login:fail:" + EMAIL)).thenReturn(1L, 2L, 3L);
        when(redis.hasKey("login:escalated:" + EMAIL)).thenReturn(true);

        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.JUST_FAILED);
        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.JUST_FAILED);
        assertThat(service.onFailedLogin(EMAIL)).isEqualTo(AttemptResult.PERMANENT);
    }

    @Test
    void isTemporarilyLocked_reflectsLockKey() {
        when(redis.hasKey("login:lock:" + EMAIL)).thenReturn(true);
        assertThat(service.isTemporarilyLocked(EMAIL)).isTrue();
    }
}
