package com.example.cachedemo.service;

import com.example.cachedemo.model.Product;
import com.example.cachedemo.model.RedisData;
import com.example.cachedemo.repository.ProductRepository;
import com.example.cachedemo.util.CacheConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String CACHE_PREFIX   = "cache:product:";
    private static final String LOCK_PREFIX    = "lock:product:";
    private static final int    MAX_RETRIES    = 5;
    private static final long   RETRY_SLEEP_MS = 50;

    private final ProductRepository productRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
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
    // 1) Cache penetration: Bloom filter + null-value sentinel
    // -------------------------------------------------------------------------
    public Product getProductWithPassThrough(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        // Bloom filter first — O(1), no network cost.
        if (!productBloomFilter.mightContain(String.valueOf(id))) {
            return null;
        }

        String key    = CACHE_PREFIX + id;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return CacheConstants.NULL_VALUE.equals(cached) ? null : fromJson(cached, Product.class);
        }

        Product product = productRepository.findById(id);
        if (product == null) {
            stringRedisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, Duration.ofMinutes(2));
            return null;
        }

        int ttl = 300 + ThreadLocalRandom.current().nextInt(120);
        stringRedisTemplate.opsForValue().set(key, toJson(product), Duration.ofSeconds(ttl));
        return product;
    }

    // -------------------------------------------------------------------------
    // 2) Cache stampede: mutex lock (strong consistency — no stale data returned)
    // -------------------------------------------------------------------------
    public Product getProductWithMutex(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        String key    = CACHE_PREFIX + id;
        String lockKey = LOCK_PREFIX + id;

        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return CacheConstants.NULL_VALUE.equals(cached) ? null : fromJson(cached, Product.class);
        }

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String token = tryLock(lockKey);
            if (token != null) {
                try {
                    cached = stringRedisTemplate.opsForValue().get(key);
                    if (cached != null) {
                        return CacheConstants.NULL_VALUE.equals(cached) ? null : fromJson(cached, Product.class);
                    }

                    Product product = productRepository.findById(id);
                    if (product == null) {
                        stringRedisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, Duration.ofMinutes(2));
                        return null;
                    }

                    int ttl = 300 + ThreadLocalRandom.current().nextInt(120);
                    stringRedisTemplate.opsForValue().set(key, toJson(product), Duration.ofSeconds(ttl));
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

            cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return CacheConstants.NULL_VALUE.equals(cached) ? null : fromJson(cached, Product.class);
            }
        }

        return productRepository.findById(id);
    }

    // -------------------------------------------------------------------------
    // 3) Cache stampede: logical expiration (high availability — may return stale data)
    //    Requires pre-loading via preloadLogicalExpire() before first read.
    // -------------------------------------------------------------------------
    public Product getProductWithLogicalExpire(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        String key     = CACHE_PREFIX + "logical:" + id;
        String lockKey = LOCK_PREFIX  + "logical:" + id;

        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null; // not pre-loaded — refuse to hit DB on the hot path
        }

        RedisData<Product> redisData = fromJson(json, new TypeReference<>() {});
        Product product   = redisData.getData();
        Long    expireTime = redisData.getExpireTime();

        if (expireTime != null && expireTime > System.currentTimeMillis()) {
            return product;
        }

        String token = tryLock(lockKey);
        if (token != null) {
            rebuildExecutor.submit(() -> {
                try {
                    Product latest = productRepository.findById(id);
                    if (latest != null) {
                        setLogicalExpire(key, latest, Duration.ofMinutes(5));
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
            setLogicalExpire(CACHE_PREFIX + "logical:" + id, product, Duration.ofMinutes(5));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setLogicalExpire(String key, Product product, Duration logicalTtl) {
        RedisData<Product> redisData = new RedisData<>(
                product, System.currentTimeMillis() + logicalTtl.toMillis());
        stringRedisTemplate.opsForValue().set(
                key, toJson(redisData), logicalTtl.multipliedBy(CacheConstants.PHYSICAL_TTL_MULT));
    }

    private String tryLock(String lockKey) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofSeconds(10));
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    private void releaseLock(String lockKey, String token) {
        stringRedisTemplate.execute(CacheConstants.RELEASE_LOCK, Collections.singletonList(lockKey), token);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Serialize error", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Deserialize error", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> ref) {
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            throw new RuntimeException("Deserialize error", e);
        }
    }
}
