package org.example.usecase.product;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Three-layer cache-aside:
 *
 *   1. Bloom filter  — in-memory, zero network cost; rejects definitely-absent IDs instantly.
 *   2. Redis cache   — null-value sentinel for deleted/missing IDs; randomised TTL jitter.
 *   3. Database      — reached only when both layers miss; protected by a distributed lock.
 *
 * NOTE: The Bloom filter is per-JVM. For multi-instance deployments replace it with
 *       a Redis-backed filter (RedisBloom module / Redisson RBloomFilter).
 */
@Service
@RequiredArgsConstructor
public class ProductCacheService implements InitializingBean {

    private static final String CACHE_KEY_PREFIX = "product:";
    private static final String LOCK_KEY_PREFIX  = "lock:product:";
    private static final String NULL_VALUE        = "NULL";
    private static final long   CACHE_TTL_MIN     = 10;
    private static final long   NULL_TTL_MIN      = 2;
    private static final long   LOCK_TTL_SEC      = 10;
    private static final int    LOCK_MAX_RETRIES  = 5;
    private static final long   LOCK_RETRY_MS     = 50;

    private static final int    BLOOM_EXPECTED_INSERTIONS = 1_000_000;
    private static final double BLOOM_FPP                 = 0.01; // 1 % false-positive rate

    // Atomic lock release: del only when the stored value matches the caller's token.
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductMapper productMapper;

    private BloomFilter<Long> bloomFilter;

    /**
     * Pre-loads all known IDs into the Bloom filter at startup.
     * A false positive means an absent ID occasionally passes through to Redis/DB;
     * that's acceptable — the null-value cache absorbs the extra round-trip on the first miss.
     */
    @Override
    public void afterPropertiesSet() {
        bloomFilter = BloomFilter.create(Funnels.longFunnel(), BLOOM_EXPECTED_INSERTIONS, BLOOM_FPP);
        List<Long> ids = productMapper.selectAllIds();
        ids.forEach(bloomFilter::put);
    }

    public Product getById(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        // Layer 1: Bloom filter — O(1), no network call.
        if (!bloomFilter.mightContain(id)) {
            return null;
        }

        // Layer 2: Redis cache.
        String key = CACHE_KEY_PREFIX + id;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return NULL_VALUE.equals(cached) ? null : (Product) cached;
        }

        // Layer 3: DB, guarded by a distributed lock to prevent cache stampede.
        String lockKey = LOCK_KEY_PREFIX + id;
        String token   = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < LOCK_MAX_RETRIES; attempt++) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, LOCK_TTL_SEC, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(acquired)) {
                try {
                    // Double-check after acquiring lock — another thread may have populated the cache.
                    cached = redisTemplate.opsForValue().get(key);
                    if (cached != null) {
                        return NULL_VALUE.equals(cached) ? null : (Product) cached;
                    }

                    Product product = productMapper.selectById(id);
                    if (product == null) {
                        redisTemplate.opsForValue().set(key, NULL_VALUE, NULL_TTL_MIN, TimeUnit.MINUTES);
                        return null;
                    }

                    long ttl = CACHE_TTL_MIN + ThreadLocalRandom.current().nextInt(5);
                    redisTemplate.opsForValue().set(key, product, ttl, TimeUnit.MINUTES);
                    return product;
                } finally {
                    redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), token);
                }
            }

            try {
                Thread.sleep(LOCK_RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            // Re-check cache while waiting — the lock holder may have written it by now.
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return NULL_VALUE.equals(cached) ? null : (Product) cached;
            }
        }

        // All retries exhausted — fall back to a direct DB read to avoid a hard failure.
        return productMapper.selectById(id);
    }

    /**
     * Writes through: updates DB, then refreshes the cache and ensures the ID is in the filter.
     * The filter is append-only — there is no need to remove IDs on delete because
     * the null-value cache handles post-deletion lookups correctly.
     */
    public Product update(Product product) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(product.getId(), "product id must not be null");

        productMapper.updateById(product);

        long ttl = CACHE_TTL_MIN + ThreadLocalRandom.current().nextInt(5);
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + product.getId(), product, ttl, TimeUnit.MINUTES);

        bloomFilter.put(product.getId());
        return product;
    }

    public void delete(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        productMapper.deleteById(id);
        // Evict the cache key; the Bloom filter keeps the ID (by design — filters are append-only).
        // Post-deletion lookups pass the filter, miss Redis, hit DB → get null-cached for NULL_TTL_MIN.
        redisTemplate.delete(CACHE_KEY_PREFIX + id);
    }
}
