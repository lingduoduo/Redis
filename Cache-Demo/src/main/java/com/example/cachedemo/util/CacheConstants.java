package com.example.cachedemo.util;

import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class CacheConstants {

    private CacheConstants() {}

    public static final String NULL_VALUE            = "NULL";
    public static final int    PHYSICAL_TTL_MULT     = 2;

    // Atomically releases the lock only when the stored value matches the caller's token.
    public static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );
}
