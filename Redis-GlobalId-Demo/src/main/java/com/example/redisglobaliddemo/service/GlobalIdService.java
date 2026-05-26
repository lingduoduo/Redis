package com.example.redisglobaliddemo.service;

import com.example.redisglobaliddemo.model.IdSegment;
import com.example.redisglobaliddemo.model.NextIdResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class GlobalIdService {

    static final long DEFAULT_STEP = 1000;
    static final long MAX_STEP = 1_000_000;
    private static final String KEY_PREFIX = "global:id:";
    private static final Pattern BIZ_TAG_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9:_-]{0,63}");

    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalSegment> localSegments = new ConcurrentHashMap<>();

    public GlobalIdService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public IdSegment reserveSegment(String bizTag, long step) {
        String normalizedTag = normalizeBizTag(bizTag);
        validateStep(step);

        String redisKey = redisKey(normalizedTag);
        Long newMax = redisTemplate.opsForValue().increment(redisKey, step);
        if (newMax == null) {
            throw new IllegalStateException("Redis INCRBY returned null for key " + redisKey);
        }
        long start = newMax - step + 1;
        return new IdSegment(normalizedTag, redisKey, start, newMax, step);
    }

    public synchronized NextIdResponse nextId(String bizTag, long step) {
        String normalizedTag = normalizeBizTag(bizTag);
        validateStep(step);

        LocalSegment segment = localSegments.compute(
                normalizedTag,
                (tag, current) -> current == null || current.isExhausted()
                        ? LocalSegment.from(reserveSegment(tag, step))
                        : current
        );

        long id = segment.next();
        return new NextIdResponse(
                normalizedTag,
                id,
                segment.start(),
                segment.end(),
                segment.remaining()
        );
    }

    public NextIdResponse nextId(String bizTag) {
        return nextId(bizTag, DEFAULT_STEP);
    }

    public String redisKey(String bizTag) {
        return KEY_PREFIX + normalizeBizTag(bizTag);
    }

    private String normalizeBizTag(String bizTag) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("bizTag cannot be blank");
        }
        String normalized = bizTag.trim().toLowerCase(Locale.ROOT);
        if (!BIZ_TAG_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("bizTag must start with a letter and contain only letters, numbers, ':', '_' or '-'");
        }
        return normalized;
    }

    private void validateStep(long step) {
        if (step <= 0 || step > MAX_STEP) {
            throw new IllegalArgumentException("step must be between 1 and " + MAX_STEP);
        }
    }

    private static final class LocalSegment {

        private final long start;
        private final long end;
        private final AtomicLong current;

        private LocalSegment(long start, long end) {
            this.start = start;
            this.end = end;
            this.current = new AtomicLong(start);
        }

        static LocalSegment from(IdSegment segment) {
            return new LocalSegment(segment.start(), segment.end());
        }

        boolean isExhausted() {
            return current.get() > end;
        }

        long next() {
            long id = current.getAndIncrement();
            if (id > end) {
                throw new IllegalStateException("Local ID segment is exhausted");
            }
            return id;
        }

        long start() {
            return start;
        }

        long end() {
            return end;
        }

        long remaining() {
            return Math.max(0, end - current.get() + 1);
        }
    }
}
