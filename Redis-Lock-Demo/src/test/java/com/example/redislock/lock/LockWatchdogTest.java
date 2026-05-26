package com.example.redislock.lock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class LockWatchdogTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final LockWatchdog watchdog = new LockWatchdog(redisTemplate);

    @Test
    void renewIfOwnedUsesLuaCompareAndExpire() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("lock:stock:1001")), eq("owner-token"), eq("30")))
                .thenReturn(1L);

        assertThat(watchdog.renewIfOwned("lock:stock:1001", "owner-token", 30)).isTrue();

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock:stock:1001")), eq("owner-token"), eq("30"));
    }

    @Test
    void renewIfOwnedReturnsFalseWhenOwnerChangedOrLockExpired() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("lock:stock:1001")), eq("stale-token"), eq("30")))
                .thenReturn(0L);

        assertThat(watchdog.renewIfOwned("lock:stock:1001", "stale-token", 30)).isFalse();
    }

    @Test
    void renewIfOwnedRejectsInvalidInputs() {
        assertThatThrownBy(() -> watchdog.renewIfOwned("", "owner-token", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Watchdog key cannot be blank");
        assertThatThrownBy(() -> watchdog.renewIfOwned("lock:stock:1001", "", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Watchdog lock value cannot be blank");
        assertThatThrownBy(() -> watchdog.renewIfOwned("lock:stock:1001", "owner-token", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttlSeconds must be positive");
    }
}
