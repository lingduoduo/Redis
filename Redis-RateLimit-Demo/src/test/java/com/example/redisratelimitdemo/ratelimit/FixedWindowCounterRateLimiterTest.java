package com.example.redisratelimitdemo.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class FixedWindowCounterRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final FixedWindowCounterRateLimiter rateLimiter = new FixedWindowCounterRateLimiter(redisTemplate);

    @Test
    void tryAcquireIncrementsCounterAndSetsTtlForFirstRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:counter:127.0.0.1:42:order:create")).thenReturn(1L);

        boolean allowed = rateLimiter.tryAcquire("127.0.0.1:42:order:create", 60, 10);

        assertThat(allowed).isTrue();
        verify(valueOps).increment("rl:counter:127.0.0.1:42:order:create");
        verify(redisTemplate).expire("rl:counter:127.0.0.1:42:order:create", Duration.ofSeconds(60));
    }

    @Test
    void tryAcquireDoesNotResetTtlAfterFirstRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:counter:127.0.0.1:42:order:create")).thenReturn(2L);

        boolean allowed = rateLimiter.tryAcquire("127.0.0.1:42:order:create", 60, 10);

        assertThat(allowed).isTrue();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void tryAcquireReturnsFalseWhenCounterExceedsMaxCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:counter:127.0.0.1:42:order:create")).thenReturn(11L);

        assertThat(rateLimiter.tryAcquire("127.0.0.1:42:order:create", 60, 10)).isFalse();
    }

    @Test
    void tryAcquireReturnsFalseWhenRedisIncrementReturnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:counter:127.0.0.1:42:order:create")).thenReturn(null);

        assertThat(rateLimiter.tryAcquire("127.0.0.1:42:order:create", 60, 10)).isFalse();
    }

    @Test
    void buildKeyUsesIpSubjectAndOperation() {
        assertThat(rateLimiter.buildKey("203.0.113.10", "user 42", "order:create"))
                .isEqualTo("203.0.113.10:user_42:order:create");
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> rateLimiter.tryAcquire("", 60, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate limit key cannot be blank");
        assertThatThrownBy(() -> rateLimiter.tryAcquire("ip:user:op", 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate limit window must be positive");
        assertThatThrownBy(() -> rateLimiter.tryAcquire("ip:user:op", 60, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate limit max count must be positive");
    }
}
