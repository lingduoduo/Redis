package com.example.redisdemo.ratelimit;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RateLimiter {

    private final StringRedisTemplate stringRedisTemplate;
    private DefaultRedisScript<Long> rateLimitScript;

    public RateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setLocation(new ClassPathResource("lua/rate_limit.lua"));
        rateLimitScript.setResultType(Long.class);
    }

    public boolean tryAcquire(String key, int windowMillis, int maxCount) {
        return tryAcquire(List.of(new LimitRule(key, windowMillis, maxCount)));
    }

    public boolean tryAcquire(List<LimitRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        long now = System.currentTimeMillis();
        String requestMember = now + "-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong());
        List<String> keys = new ArrayList<>(rules.size());
        List<String> args = new ArrayList<>(2 + rules.size() * 2);
        args.add(String.valueOf(now));
        args.add(requestMember);

        for (LimitRule rule : rules) {
            rule.validate();
            keys.add(rule.key());
            args.add(String.valueOf(rule.windowMillis()));
            args.add(String.valueOf(rule.maxCount()));
        }

        Long result = stringRedisTemplate.execute(
                rateLimitScript,
                keys,
                args.toArray(Object[]::new)
        );

        return result != null && result == 1L;
    }

    public record LimitRule(String key, int windowMillis, int maxCount) {
        private void validate() {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Rate limit key must not be blank");
            }
            if (windowMillis <= 0) {
                throw new IllegalArgumentException("Rate limit window must be positive");
            }
            if (maxCount <= 0) {
                throw new IllegalArgumentException("Rate limit max count must be positive");
            }
        }
    }
}
