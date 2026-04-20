package com.example.redislock.util;

/**
 * Utility class for building consistent Redis key names.
 * Centralizes key construction to avoid duplication and ensure consistency.
 */
public final class RedisKeyBuilder {

    private static final String STOCK_KEY_PREFIX = "flash:stock:";
    private static final String STOCK_LOCK_KEY_PREFIX = "lock:stock:";
    private static final String CUSTOM_LOCK_RESOURCE_PREFIX = "flash:";
    private static final String PRODUCT_ORDER_KEY_PREFIX = "flash:orders:";
    private static final String USER_ORDER_KEY_PREFIX = "orders:user:";
    private static final String USER_ORDER_LOCK_RESOURCE_PREFIX = "order:";

    private RedisKeyBuilder() {
    }

    public static String stockKey(String productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    public static String stockKey(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    public static String lockResource(String productId) {
        return CUSTOM_LOCK_RESOURCE_PREFIX + productId;
    }

    public static String stockLockKey(Long productId) {
        return STOCK_LOCK_KEY_PREFIX + productId;
    }

    public static String orderKey(Long productId) {
        return PRODUCT_ORDER_KEY_PREFIX + productId;
    }

    public static String userOrderKey(Long userId) {
        return USER_ORDER_KEY_PREFIX + userId;
    }

    public static String userOrderLockResource(Long userId) {
        return USER_ORDER_LOCK_RESOURCE_PREFIX + userId;
    }
}
