package org.example.usecase.product;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class ProductService {
    private static final String CACHE_KEY_PREFIX = "product:";
    private static final long CACHE_TTL_MINUTES = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductMapper productMapper;

    public ProductService(RedisTemplate<String, Object> redisTemplate, ProductMapper productMapper) {
        this.redisTemplate = redisTemplate;
        this.productMapper = productMapper;
    }

    public Product getById(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        String key = cacheKey(id);
        Product cached = (Product) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        Product product = productMapper.selectById(id);
        if (product != null) {
            redisTemplate.opsForValue().set(key, product, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }
        return product;
    }

    public void update(Product product) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(product.getId(), "product id must not be null");

        productMapper.updateById(product);
        redisTemplate.delete(cacheKey(product.getId()));
    }

    private String cacheKey(Long id) {
        return CACHE_KEY_PREFIX + id;
    }
}
