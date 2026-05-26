package com.example.redishashdemo.service;

import com.example.redishashdemo.model.CartItem;
import com.example.redishashdemo.model.CartSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class CartHashService {

    private static final String KEY_PREFIX = "cart:";

    private final StringRedisTemplate redisTemplate;

    public CartHashService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long increment(String userId, String productId, long delta) {
        validateUserId(userId);
        validateProductId(productId);
        if (delta == 0) {
            throw new IllegalArgumentException("delta cannot be zero");
        }

        String key = cartKey(userId);
        Long quantity = redisTemplate.opsForHash().increment(key, productId, delta);
        long safeQuantity = quantity == null ? 0L : quantity;
        if (safeQuantity <= 0) {
            redisTemplate.opsForHash().delete(key, productId);
            return 0L;
        }
        return safeQuantity;
    }

    public long addOne(String userId, String productId) {
        return increment(userId, productId, 1);
    }

    public long removeOne(String userId, String productId) {
        return increment(userId, productId, -1);
    }

    public void deleteProduct(String userId, String productId) {
        validateUserId(userId);
        validateProductId(productId);
        redisTemplate.opsForHash().delete(cartKey(userId), productId);
    }

    public Map<Object, Object> entries(String userId) {
        validateUserId(userId);
        return redisTemplate.opsForHash().entries(cartKey(userId));
    }

    public long productKinds(String userId) {
        validateUserId(userId);
        Long size = redisTemplate.opsForHash().size(cartKey(userId));
        return size == null ? 0L : size;
    }

    public CartSummary summary(String userId) {
        Map<Object, Object> entries = entries(userId);
        List<CartItem> items = entries.entrySet().stream()
                .map(entry -> new CartItem(
                        String.valueOf(entry.getKey()),
                        parseQuantity(entry.getValue())
                ))
                .filter(item -> item.quantity() > 0)
                .sorted(Comparator.comparing(CartItem::productId))
                .toList();
        return new CartSummary(userId, cartKey(userId), productKinds(userId), items);
    }

    public String cartKey(String userId) {
        validateUserId(userId);
        return KEY_PREFIX + userId;
    }

    private long parseQuantity(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId cannot be blank");
        }
    }
}
