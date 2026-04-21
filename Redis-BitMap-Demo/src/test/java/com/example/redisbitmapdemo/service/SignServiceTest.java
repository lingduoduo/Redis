package com.example.redisbitmapdemo.service;

import com.example.redisbitmapdemo.model.SignSummary;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private RedisStringCommands stringCommands;

    @Test
    void signSetsBitAtZeroBasedDayOffset() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        SignService signService = new SignService(redisTemplate);

        signService.sign(42L, LocalDate.of(2026, 4, 20));

        verify(valueOps).setBit("sign:42:202604", 19, true);
    }

    @Test
    void hasSignedReturnsTrueOnlyWhenBitIsSet() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getBit("sign:42:202604", 19)).thenReturn(true);
        SignService signService = new SignService(redisTemplate);

        assertThat(signService.hasSigned(42L, LocalDate.of(2026, 4, 20))).isTrue();
    }

    @Test
    void monthKeyUsesUserAndYearMonth() {
        SignService signService = new SignService(redisTemplate);

        assertThat(signService.monthKey(42L, LocalDate.of(2026, 4, 20))).isEqualTo("sign:42:202604");
    }

    @Test
    void keyBytesUsesUtf8Encoding() {
        SignService signService = new SignService(redisTemplate);

        assertThat(signService.keyBytes("sign:42:202604"))
                .isEqualTo("sign:42:202604".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void signDaysOfMonthUsesStringBitCountAndReturnsZeroForNull() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<Long> callback = invocation.getArgument(0);
            return callback.doInRedis(redisConnection);
        });
        when(redisConnection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.bitCount("sign:42:202604".getBytes(StandardCharsets.UTF_8))).thenReturn(null);
        SignService signService = new SignService(redisTemplate);

        long result = signService.signDaysOfMonth(42L, LocalDate.of(2026, 4, 20));

        assertThat(result).isZero();
        verify(stringCommands).bitCount("sign:42:202604".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void summaryUsesRequestedMonthAndDerivedMetrics() {
        SignService signService = new SignService(redisTemplate) {
            @Override
            public boolean hasSigned(Long userId, LocalDate date) {
                return true;
            }

            @Override
            public long signDaysOfMonth(Long userId, LocalDate date) {
                return 8;
            }

            @Override
            public long currentStreak(Long userId, LocalDate date) {
                return 4;
            }
        };

        SignSummary summary = signService.summary(42L, LocalDate.of(2026, 4, 20));

        assertThat(summary).isEqualTo(new SignSummary(42L, "202604", true, 8L, 4L));
    }
}
