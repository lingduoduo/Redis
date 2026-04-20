package com.example.rediscachedemo.service;

import com.example.rediscachedemo.model.Product;
import com.example.rediscachedemo.model.RedisData;
import com.example.rediscachedemo.repository.ProductRepository;
import com.example.rediscachedemo.util.CacheConstants;
import com.google.common.hash.BloomFilter;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates two stampede-prevention strategies using RedisTemplate<String, Object>
 * (Jackson object serialization) vs. ProductService's StringRedisTemplate (JSON strings).
 *
 *   getById()                  — mutex lock    (strong consistency, waits for fresh data)
 *   getByIdWithLogicalExpire() — logical expiry (high availability, returns stale then rebuilds)
 *
 * Both sit behind a three-layer read path:
 *   1. Bloom filter  — O(1), no network; drops definitely-absent IDs instantly.
 *   2. Redis cache   — null-value sentinel absorbs missing/deleted-ID hits.
 *   3. Database      — only reached on a full miss.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private static final String CACHE_PREFIX    = "product:";
    private static final String LOGICAL_PREFIX  = "product:logical:";
    private static final String LOCK_PREFIX     = "lock:product:";
    private static final long   CACHE_TTL_MIN   = 10;
    private static final long   NULL_TTL_MIN    = 2;
    private static final long   LOGICAL_TTL_MIN = 30;
    private static final long   LOCK_TTL_SEC    = 10;
    private static final int    MAX_RETRIES     = 5;
    private static final long   RETRY_SLEEP_MS  = 50;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;
    private final BloomFilter<String> productBloomFilter;

    private final ExecutorService rebuildExecutor = Executors.newFixedThreadPool(4);

    @PreDestroy
    void shutdownExecutor() {
        rebuildExecutor.shutdown();
        try {
            if (!rebuildExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                rebuildExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            rebuildExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Strategy 1: mutex lock — strong consistency, no stale data ever returned
    // -------------------------------------------------------------------------
    public Product getById(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        if (!productBloomFilter.mightContain(String.valueOf(id))) {
            return null;
        }

        String key = CACHE_PREFIX + id;
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw != null) {
            return CacheConstants.NULL_VALUE.equals(raw) ? null : (Product) raw;
        }

        String lockKey = LOCK_PREFIX + id;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String token = tryLock(lockKey);
            if (token != null) {
                try {
                    // Double-check: a concurrent thread may have populated the cache.
                    raw = redisTemplate.opsForValue().get(key);
                    if (raw != null) {
                        return CacheConstants.NULL_VALUE.equals(raw) ? null : (Product) raw;
                    }

                    Product product = productRepository.findById(id);
                    if (product == null) {
                        redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, NULL_TTL_MIN, TimeUnit.MINUTES);
                        return null;
                    }

                    long ttl = CACHE_TTL_MIN + ThreadLocalRandom.current().nextInt(5);
                    redisTemplate.opsForValue().set(key, product, ttl, TimeUnit.MINUTES);
                    return product;
                } finally {
                    releaseLock(lockKey, token);
                }
            }

            try {
                Thread.sleep(RETRY_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            raw = redisTemplate.opsForValue().get(key);
            if (raw != null) {
                return CacheConstants.NULL_VALUE.equals(raw) ? null : (Product) raw;
            }
        }

        return productRepository.findById(id);
    }

    // -------------------------------------------------------------------------
    // Strategy 2: logical expiration — high availability, may return stale data
    //   Requires pre-loading via preloadLogicalExpire() before first read.
    // -------------------------------------------------------------------------
    public Product getByIdWithLogicalExpire(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        if (!productBloomFilter.mightContain(String.valueOf(id))) {
            return null;
        }

        String key     = LOGICAL_PREFIX + id;
        String lockKey = LOCK_PREFIX + "logical:" + id;

        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            return null; // not pre-loaded — refuse to hit DB on the hot path
        }

        @SuppressWarnings("unchecked")
        RedisData<Product> redisData = (RedisData<Product>) raw;
        Product product   = redisData.getData();
        Long    expireTime = redisData.getExpireTime();

        if (expireTime != null && expireTime > System.currentTimeMillis()) {
            return product;
        }

        // Logically expired — return stale immediately, rebuild asynchronously.
        String token = tryLock(lockKey);
        if (token != null) {
            rebuildExecutor.submit(() -> {
                try {
                    Product latest = productRepository.findById(id);
                    if (latest != null) {
                        setLogicalExpire(key, latest, Duration.ofMinutes(LOGICAL_TTL_MIN));
                    }
                } catch (Exception e) {
                    log.error("Failed to rebuild logical-expiry cache for product {}", id, e);
                } finally {
                    releaseLock(lockKey, token);
                }
            });
        }

        return product;
    }

    public void preloadLogicalExpire(Long id) {
        Objects.requireNonNull(id, "id must not be null");
        Product product = productRepository.findById(id);
        if (product != null) {
            setLogicalExpire(LOGICAL_PREFIX + id, product, Duration.ofMinutes(LOGICAL_TTL_MIN));
            productBloomFilter.put(String.valueOf(id));
        }
    }

    // -------------------------------------------------------------------------
    // Write-through operations
    // -------------------------------------------------------------------------
    public Product update(Product product) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(product.getId(), "product id must not be null");

        productRepository.save(product);

        long ttl = CACHE_TTL_MIN + ThreadLocalRandom.current().nextInt(5);
        redisTemplate.opsForValue().set(CACHE_PREFIX + product.getId(), product, ttl, TimeUnit.MINUTES);

        String logicalKey = LOGICAL_PREFIX + product.getId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(logicalKey))) {
            setLogicalExpire(logicalKey, product, Duration.ofMinutes(LOGICAL_TTL_MIN));
        }

        productBloomFilter.put(String.valueOf(product.getId()));
        return product;
    }

    public void delete(Long id) {
        Objects.requireNonNull(id, "id must not be null");
        productRepository.deleteById(id);
        redisTemplate.delete(CACHE_PREFIX + id);
        redisTemplate.delete(LOGICAL_PREFIX + id);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setLogicalExpire(String key, Product product, Duration logicalTtl) {
        RedisData<Product> redisData = new RedisData<>(
                product, System.currentTimeMillis() + logicalTtl.toMillis());
        redisTemplate.opsForValue().set(
                key, redisData, logicalTtl.multipliedBy(CacheConstants.PHYSICAL_TTL_MULT));
    }

    private String tryLock(String lockKey) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, LOCK_TTL_SEC, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    private void releaseLock(String lockKey, String token) {
        redisTemplate.execute(CacheConstants.RELEASE_LOCK, Collections.singletonList(lockKey), token);
    }
}
