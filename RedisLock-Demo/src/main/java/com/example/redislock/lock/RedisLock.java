package com.example.redislock.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight Redis distributed lock for short critical sections.
 *
 * Acquire uses Redis SET NX EX semantics through setIfAbsent with TTL.
 * Release uses Lua compare-and-delete so a caller can only delete a lock
 * when its UUID token still matches the Redis value.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:";
    private static final long RETRY_INTERVAL_MS = 50;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    " return redis.call('del', KEYS[1]) " +
                    "else return 0 end",
            Long.class
    );

    private static final AtomicLong lockAcquisitions = new AtomicLong();
    private static final AtomicLong lockFailures = new AtomicLong();
    private static final AtomicLong lockTimeouts = new AtomicLong();

    /**
     * Single-attempt lock acquisition with a generated UUID token.
     *
     * @param key the business lock key
     * @param expireSeconds lock lease time in seconds
     * @return the lock token when acquired, or null when the lock is already held
     */
    public String tryLockWithToken(String key, long expireSeconds) {
        validateKey(key);
        validatePositive(expireSeconds, "expireSeconds");

        String token = UUID.randomUUID().toString();
        return tryLock(key, token, expireSeconds) ? token : null;
    }

    private boolean tryLock(String key, String token, long timeoutSeconds) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                redisKey(key),
                token,
                Duration.ofSeconds(timeoutSeconds)
        );

        if (Boolean.TRUE.equals(success)) {
            lockAcquisitions.incrementAndGet();
            log.debug("Lock acquired: key={}", key);
            return true;
        }

        lockFailures.incrementAndGet();
        log.debug("Lock acquisition failed: key={}", key);
        return false;
    }

    /**
     * Retry token-based lock acquisition until the wait time expires.
     *
     * @param key the business lock key
     * @param waitSeconds maximum time to wait
     * @param expireSeconds lock lease time in seconds
     * @return UUID token when acquired, or null when the wait time expires
     * @throws InterruptedException if interrupted while waiting
     */
    public String tryLockWithToken(String key, long waitSeconds, long expireSeconds) throws InterruptedException {
        validateKey(key);
        validateNonNegative(waitSeconds, "waitSeconds");
        validatePositive(expireSeconds, "expireSeconds");

        long waitNanos = TimeUnit.SECONDS.toNanos(waitSeconds);
        long deadline = System.nanoTime() + waitNanos;

        while (true) {
            String token = tryLockWithToken(key, expireSeconds);
            if (token != null) {
                return token;
            }

            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                lockTimeouts.incrementAndGet();
                log.debug("Token lock acquisition timeout after {} seconds: key={}", waitSeconds, key);
                return null;
            }

            long sleepMillis = Math.min(RETRY_INTERVAL_MS, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
            Thread.sleep(Math.max(1, sleepMillis));
        }
    }

    /**
     * Release a token-owned lock, but only if Redis still stores this token.
     *
     * @param key the business lock key
     * @param token token returned by tryLockWithToken
     * @return true when Redis deleted the lock
     */
    public boolean unlock(String key, String token) {
        validateKey(key);
        validateOwnerValue(token);

        Long result = redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(redisKey(key)),
                token
        );

        boolean released = result != null && result > 0;
        if (released) {
            log.debug("Lock released: key={}", key);
        } else {
            log.debug("Lock release skipped because owner did not match or lock expired: key={}", key);
        }
        return released;
    }

    /**
     * Returns lock acquisition metrics for monitoring.
     *
     * @return array of [acquisitions, failures, timeouts]
     */
    public long[] getMetrics() {
        return new long[]{
                lockAcquisitions.get(),
                lockFailures.get(),
                lockTimeouts.get()
        };
    }

    /**
     * Resets all metrics. Useful for integration tests and demos.
     */
    public void resetMetrics() {
        lockAcquisitions.set(0);
        lockFailures.set(0);
        lockTimeouts.set(0);
    }

    /**
     * Returns the physical Redis key used for a business lock key.
     */
    public String redisKey(String key) {
        validateKey(key);
        return LOCK_PREFIX + key;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Lock key cannot be blank");
        }
    }

    private void validateOwnerValue(String ownerValue) {
        if (ownerValue == null || ownerValue.isBlank()) {
            throw new IllegalArgumentException("Lock owner value cannot be blank");
        }
    }

    private void validatePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }
}
