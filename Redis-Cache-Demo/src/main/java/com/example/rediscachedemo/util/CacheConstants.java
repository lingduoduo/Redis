package com.example.rediscachedemo.util;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class CacheConstants {

    private CacheConstants() {}

    public static final String NULL_VALUE        = "NULL";
    public static final int    PHYSICAL_TTL_MULT = 2;

    public static final int  MAX_RETRIES    = 5;
    public static final long RETRY_SLEEP_MS = 50L;
    public static final long LOCK_TTL_SEC   = 10L;
    public static final long NULL_TTL_MIN   = 2L;
    public static final long CACHE_TTL_MIN  = 10L;

    // Atomically releases the lock only when the stored value matches the caller's token.
    public static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    public static void gracefulShutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
