package com.example.redisbitmapdemo.service;

import com.example.redisbitmapdemo.model.SignSummary;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SignService {

    private static final String KEY_PREFIX = "sign:";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final StringRedisTemplate redisTemplate;

    public SignService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void sign(Long userId) {
        sign(userId, today());
    }

    public void sign(Long userId, LocalDate date) {
        redisTemplate.opsForValue().setBit(monthKey(userId, date), dayOffset(date), true);
    }

    public boolean hasSignedToday(Long userId) {
        return hasSigned(userId, today());
    }

    public boolean hasSigned(Long userId, LocalDate date) {
        Boolean result = redisTemplate.opsForValue().getBit(monthKey(userId, date), dayOffset(date));
        return Boolean.TRUE.equals(result);
    }

    public long signDaysOfMonth(Long userId) {
        return signDaysOfMonth(userId, today());
    }

    public long signDaysOfMonth(Long userId, LocalDate date) {
        byte[] key = keyBytes(monthKey(userId, date));
        Long count = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(key)
        );
        return count == null ? 0 : count;
    }

    public long currentStreak(Long userId) {
        return currentStreak(userId, today());
    }

    public long currentStreak(Long userId, LocalDate date) {
        String key = monthKey(userId, date);
        int dayOfMonth = date.getDayOfMonth();

        List<Long> result = redisTemplate.execute((RedisCallback<List<Long>>) connection ->
                connection.stringCommands().bitField(
                        keyBytes(key),
                        BitFieldSubCommands.create()
                                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                .valueAt(0)
                )
        );

        if (result == null || result.isEmpty() || result.get(0) == null) {
            return 0;
        }

        long num = result.get(0);
        long streak = 0;
        for (int i = 0; i < dayOfMonth; i++) {
            if ((num & 1) == 0) {
                break;
            }
            streak++;
            num >>>= 1;
        }
        return streak;
    }

    public SignSummary summary(Long userId) {
        return summary(userId, today());
    }

    public SignSummary summary(Long userId, LocalDate date) {
        return new SignSummary(
                userId,
                date.format(MONTH_FMT),
                hasSigned(userId, date),
                signDaysOfMonth(userId, date),
                currentStreak(userId, date)
        );
    }

    String monthKey(Long userId, LocalDate date) {
        return KEY_PREFIX + userId + ":" + date.format(MONTH_FMT);
    }

    byte[] keyBytes(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private int dayOffset(LocalDate date) {
        return date.getDayOfMonth() - 1;
    }

    LocalDate today() {
        return LocalDate.now();
    }
}
