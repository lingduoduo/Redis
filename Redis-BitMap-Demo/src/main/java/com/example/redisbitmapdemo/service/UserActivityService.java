package com.example.redisbitmapdemo.service;

import com.example.redisbitmapdemo.model.ActivityStats;
import com.example.redisbitmapdemo.model.RetentionStats;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class UserActivityService {

    private static final String KEY_PREFIX = "activity:online:";
    private static final String RESULT_PREFIX = "activity:result:";
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    public UserActivityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void markOnline(Long userId, LocalDate date) {
        validateUserId(userId);
        redisTemplate.opsForValue().setBit(dailyKey(date), userId, true);
    }

    public boolean wasOnline(Long userId, LocalDate date) {
        validateUserId(userId);
        Boolean result = redisTemplate.opsForValue().getBit(dailyKey(date), userId);
        return Boolean.TRUE.equals(result);
    }

    public long dailyActiveUsers(LocalDate date) {
        return bitCount(dailyKey(date));
    }

    public long allDaysOnlineUsers(LocalDate startDate, int days) {
        validateDays(days);
        return bitOpAnd(resultKey("and", startDate, days), dailyKeys(startDate, days));
    }

    public long anyDayOnlineUsers(LocalDate startDate, int days) {
        validateDays(days);
        return bitOpOr(resultKey("or", startDate, days), dailyKeys(startDate, days));
    }

    public ActivityStats activityStats(LocalDate startDate, int days) {
        validateDays(days);
        return new ActivityStats(
                startDate,
                startDate.plusDays(days - 1L),
                allDaysOnlineUsers(startDate, days),
                anyDayOnlineUsers(startDate, days)
        );
    }

    public RetentionStats retention(LocalDate baseDate, LocalDate retainedDate) {
        long retainedUsers = bitOpAnd(
                resultKey("retention", baseDate, retainedDate),
                List.of(dailyKey(baseDate), dailyKey(retainedDate))
        );
        return new RetentionStats(
                baseDate,
                retainedDate,
                dailyActiveUsers(baseDate),
                retainedUsers
        );
    }

    String dailyKey(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date cannot be null");
        }
        return KEY_PREFIX + date.format(DAY_FMT);
    }

    String resultKey(String operation, LocalDate startDate, int days) {
        return RESULT_PREFIX + operation + ":" + startDate.format(DAY_FMT) + ":" + days;
    }

    String resultKey(String operation, LocalDate baseDate, LocalDate retainedDate) {
        return RESULT_PREFIX + operation + ":" + baseDate.format(DAY_FMT) + ":" + retainedDate.format(DAY_FMT);
    }

    private List<String> dailyKeys(LocalDate startDate, int days) {
        return startDate.datesUntil(startDate.plusDays(days)).map(this::dailyKey).toList();
    }

    private long bitOpAnd(String destinationKey, List<String> sourceKeys) {
        return bitOp(destinationKey, sourceKeys, RedisStringCommands.BitOperation.AND);
    }

    private long bitOpOr(String destinationKey, List<String> sourceKeys) {
        return bitOp(destinationKey, sourceKeys, RedisStringCommands.BitOperation.OR);
    }

    private long bitOp(String destinationKey, List<String> sourceKeys, RedisStringCommands.BitOperation operation) {
        if (sourceKeys == null || sourceKeys.isEmpty()) {
            return 0L;
        }
        byte[] destination = keyBytes(destinationKey);
        byte[][] sources = sourceKeys.stream().map(this::keyBytes).toArray(byte[][]::new);

        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            connection.stringCommands().bitOp(operation, destination, sources);
            Long count = connection.stringCommands().bitCount(destination);
            return count == null ? 0L : count;
        });
    }

    private long bitCount(String key) {
        byte[] keyBytes = keyBytes(key);
        Long count = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(keyBytes)
        );
        return count == null ? 0L : count;
    }

    private byte[] keyBytes(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 0) {
            throw new IllegalArgumentException("userId must be non-negative");
        }
    }

    private void validateDays(int days) {
        if (days <= 0 || days > 31) {
            throw new IllegalArgumentException("days must be between 1 and 31");
        }
    }
}
