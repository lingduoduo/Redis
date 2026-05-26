package com.example.redisbitmapdemo.service;

import com.example.redisbitmapdemo.model.ActivityStats;
import com.example.redisbitmapdemo.model.RetentionStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private RedisStringCommands stringCommands;

    @Test
    void markOnlineSetsUserIdAsBitOffsetInDailyBitmap() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UserActivityService service = new UserActivityService(redisTemplate);

        service.markOnline(42L, LocalDate.of(2026, 5, 26));

        verify(valueOps).setBit("activity:online:20260526", 42L, true);
    }

    @Test
    void wasOnlineReturnsTrueOnlyWhenBitIsSet() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getBit("activity:online:20260526", 42L)).thenReturn(true);
        UserActivityService service = new UserActivityService(redisTemplate);

        assertThat(service.wasOnline(42L, LocalDate.of(2026, 5, 26))).isTrue();
    }

    @Test
    void dailyActiveUsersUsesBitCount() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<Long> callback = invocation.getArgument(0);
            return callback.doInRedis(redisConnection);
        });
        when(redisConnection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.bitCount(bytes("activity:online:20260526"))).thenReturn(8L);
        UserActivityService service = new UserActivityService(redisTemplate);

        assertThat(service.dailyActiveUsers(LocalDate.of(2026, 5, 26))).isEqualTo(8L);
    }

    @Test
    void activityStatsUsesBitOpAndForAllDaysAndBitOpOrForAnyDay() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<Long> callback = invocation.getArgument(0);
            return callback.doInRedis(redisConnection);
        });
        when(redisConnection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.bitCount(bytes("activity:result:and:20260520:7"))).thenReturn(3L);
        when(stringCommands.bitCount(bytes("activity:result:or:20260520:7"))).thenReturn(10L);
        UserActivityService service = new UserActivityService(redisTemplate);

        ActivityStats stats = service.activityStats(LocalDate.of(2026, 5, 20), 7);

        assertThat(stats).isEqualTo(new ActivityStats(
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 26),
                3L,
                10L
        ));
        verify(stringCommands).bitOp(
                eq(RedisStringCommands.BitOperation.AND),
                eq(bytes("activity:result:and:20260520:7")),
                any(byte[][].class)
        );
        verify(stringCommands).bitOp(
                eq(RedisStringCommands.BitOperation.OR),
                eq(bytes("activity:result:or:20260520:7")),
                any(byte[][].class)
        );
    }

    @Test
    void retentionUsesBitOpAndForBaseAndRetainedDates() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<Long> callback = invocation.getArgument(0);
            return callback.doInRedis(redisConnection);
        });
        when(redisConnection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.bitCount(bytes("activity:result:retention:20260520:20260526"))).thenReturn(4L);
        when(stringCommands.bitCount(bytes("activity:online:20260520"))).thenReturn(20L);
        UserActivityService service = new UserActivityService(redisTemplate);

        RetentionStats stats = service.retention(LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 26));

        assertThat(stats).isEqualTo(new RetentionStats(
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 26),
                20L,
                4L
        ));
        verify(stringCommands).bitOp(
                eq(RedisStringCommands.BitOperation.AND),
                eq(bytes("activity:result:retention:20260520:20260526")),
                any(byte[][].class)
        );
    }

    @Test
    void rejectsInvalidInput() {
        UserActivityService service = new UserActivityService(redisTemplate);

        assertThatThrownBy(() -> service.markOnline(-1L, LocalDate.of(2026, 5, 26)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be non-negative");
        assertThatThrownBy(() -> service.activityStats(LocalDate.of(2026, 5, 26), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("days must be between 1 and 31");
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
