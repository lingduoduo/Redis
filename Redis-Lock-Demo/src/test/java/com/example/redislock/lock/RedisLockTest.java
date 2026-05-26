package com.example.redislock.lock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class RedisLockTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisLock redisLock = new RedisLock(redisTemplate);

    @Test
    void tryLockWithTokenUsesSetIfAbsentWithTtlAndReturnsGeneratedToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("lock:stock:1001"), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);

        String token = redisLock.tryLockWithToken("stock:1001", 10);

        assertThat(token).isNotBlank();
        verify(valueOperations).setIfAbsent("lock:stock:1001", token, Duration.ofSeconds(10));
    }

    @Test
    void tryLockWithTokenReturnsNullWhenLockAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("lock:stock:1001"), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(false);

        assertThat(redisLock.tryLockWithToken("stock:1001", 10)).isNull();
    }

    @Test
    void unlockUsesLuaCompareAndDelete() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("lock:stock:1001")), eq("owner-token")))
                .thenReturn(1L);

        assertThat(redisLock.unlock("stock:1001", "owner-token")).isTrue();

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock:stock:1001")), eq("owner-token"));
    }

    @Test
    void unlockReturnsFalseWhenOwnerTokenDoesNotMatch() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("lock:stock:1001")), eq("stale-token")))
                .thenReturn(0L);

        assertThat(redisLock.unlock("stock:1001", "stale-token")).isFalse();
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> redisLock.tryLockWithToken("", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lock key cannot be blank");
        assertThatThrownBy(() -> redisLock.tryLockWithToken("stock:1001", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expireSeconds must be positive");
        assertThatThrownBy(() -> redisLock.unlock("stock:1001", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lock owner value cannot be blank");
    }
}
