package com.example.redislock.lock;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automatic lock renewal mechanism to prevent lock expiry during long-running critical sections.
 * 
 * Periodically renews locks by updating their TTL before expiration.
 * This pattern is borrowed from RedisCacheDemo and is essential for operations that may
 * exceed the initial lock lease time.
 *
 * Usage:
 * - Start renewal after acquiring a lock
 * - Cancel renewal when the critical section completes
 * - Critical for flash sales with variable operation times
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LockWatchdog {

    private final StringRedisTemplate redisTemplate;

    private static final int POOL_SIZE = 2;
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    " return redis.call('expire', KEYS[1], ARGV[2]) " +
                    "else return 0 end",
            Long.class
    );

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            POOL_SIZE,
            new WatchdogThreadFactory()
    );

    /**
     * Starts automatic lock renewal for the given key.
     * 
     * Renews the lock by updating its TTL at intervals of ttlSeconds/3.
     * This ensures the lock is renewed before it expires (with 2/3 TTL safety margin).
     *
     * @param key the lock key
     * @param value the lock token (must match for renewal to proceed)
     * @param ttlSeconds the time-to-live in seconds
     * @return a ScheduledFuture to cancel the renewal task when done
     */
    public ScheduledFuture<?> startRenewal(String key, String value, long ttlSeconds) {
        validate(key, value, ttlSeconds);
        long renewalIntervalSeconds = Math.max(1, ttlSeconds / 3);
        
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                if (renewIfOwned(key, value, ttlSeconds)) {
                    log.debug("Lock renewed: key={}, newTTL={} seconds", key, ttlSeconds);
                } else {
                    log.debug("Lock renewal skipped because owner changed or lock expired: key={}", key);
                }
            } catch (Exception e) {
                log.error("Error renewing lock: key={}", key, e);
            }
        }, renewalIntervalSeconds, renewalIntervalSeconds, TimeUnit.SECONDS);
    }

    boolean renewIfOwned(String key, String value, long ttlSeconds) {
        validate(key, value, ttlSeconds);
        Long result = redisTemplate.execute(
                RENEW_SCRIPT,
                Collections.singletonList(key),
                value,
                String.valueOf(ttlSeconds)
        );
        return result != null && result > 0;
    }

    /**
     * Stops lock renewal and cancels the scheduled task.
     *
     * @param renewalTask the ScheduledFuture returned by startRenewal()
     * @param mayInterrupt true to interrupt the renewal thread if running
     */
    public void stopRenewal(ScheduledFuture<?> renewalTask, boolean mayInterrupt) {
        if (renewalTask != null && !renewalTask.isCancelled()) {
            renewalTask.cancel(mayInterrupt);
            log.debug("Lock renewal cancelled");
        }
    }

    /**
     * Convenience method to stop renewal without interrupting.
     *
     * @param renewalTask the ScheduledFuture returned by startRenewal()
     */
    public void stopRenewal(ScheduledFuture<?> renewalTask) {
        stopRenewal(renewalTask, false);
    }

    /**
     * Checks if a renewal task is still active.
     *
     * @param renewalTask the ScheduledFuture returned by startRenewal()
     * @return true if the task is still running, false if cancelled or done
     */
    public boolean isRenewalActive(ScheduledFuture<?> renewalTask) {
        return renewalTask != null && !renewalTask.isCancelled() && !renewalTask.isDone();
    }

    /**
     * Shuts down the lock watchdog scheduler.
     * Should be called during application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("LockWatchdog scheduler did not terminate in time");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            log.warn("LockWatchdog scheduler shutdown interrupted", e);
        }
    }

    private void validate(String key, String value, long ttlSeconds) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Watchdog key cannot be blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Watchdog lock value cannot be blank");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
    }

    private static final class WatchdogThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "redis-lock-watchdog-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
