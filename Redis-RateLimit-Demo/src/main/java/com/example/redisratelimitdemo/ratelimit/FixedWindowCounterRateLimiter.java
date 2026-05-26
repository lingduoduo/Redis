package com.example.redisratelimitdemo.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class FixedWindowCounterRateLimiter {

    private static final String KEY_PREFIX = "rl:counter:";

    private final StringRedisTemplate redisTemplate;

    public FixedWindowCounterRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String key, int windowSeconds, int maxCount) {
        validate(key, windowSeconds, maxCount);

        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            return false;
        }
        if (count == 1L) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }
        return count <= maxCount;
    }

    public String buildKey(String clientIp, String subject, String operation) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("clientIp cannot be blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject cannot be blank");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation cannot be blank");
        }
        return sanitize(clientIp) + ":" + sanitize(subject) + ":" + sanitize(operation);
    }

    private void validate(String key, int windowSeconds, int maxCount) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Rate limit key cannot be blank");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("Rate limit window must be positive");
        }
        if (maxCount <= 0) {
            throw new IllegalArgumentException("Rate limit max count must be positive");
        }
    }

    private String sanitize(String value) {
        return value.trim().replaceAll("[^a-zA-Z0-9._:-]", "_");
    }
}
