package com.example.rediscachedemo.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Product tag store backed by a Redis Set.
 *
 * key = tags:{productId}  →  Set of tag strings
 *
 * Redis commands exercised:
 *   SADD      tags:{id} tag          – add a tag (idempotent)
 *   SREM      tags:{id} tag          – remove a tag
 *   SISMEMBER tags:{id} tag          – does this product have the tag?
 *   SMEMBERS  tags:{id}              – all tags for a product
 *   SCARD     tags:{id}              – number of tags
 *   SINTER    tags:{id1} tags:{id2}  – tags shared by two products
 */
@Service
@RequiredArgsConstructor
public class ProductTagService {

    static final String KEY_PREFIX = "tags:";

    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    void seedDefaultTags() {
        // iPhone 15 Pro
        addTag(1L, "super-retina-display");
        addTag(1L, "titanium-design");
        addTag(1L, "pro-camera-system");  // shared with Galaxy S24 Ultra → SINTER demo
        addTag(1L, "5g");
        // MacBook Pro 14"
        addTag(2L, "m3-chip");
        addTag(2L, "long-battery-life");  // shared with WH-1000XM5 → SINTER demo
        addTag(2L, "pro-display");        // shared with LG Monitor → SINTER demo
        addTag(2L, "thin-and-light");
        // AirPods Pro
        addTag(3L, "active-noise-cancellation");  // shared with WH-1000XM5 → SINTER demo
        addTag(3L, "spatial-audio");
        addTag(3L, "wireless-charging");
        // Galaxy S24 Ultra
        addTag(6L, "high-resolution-display");
        addTag(6L, "built-in-s-pen");
        addTag(6L, "ai-features");
        addTag(6L, "pro-camera-system");  // shared with iPhone 15 Pro → SINTER demo
        // Sony WH-1000XM5
        addTag(9L, "active-noise-cancellation");  // shared with AirPods Pro → SINTER demo
        addTag(9L, "hi-res-audio");
        addTag(9L, "comfortable-fit");
        addTag(9L, "long-battery-life");  // shared with MacBook Pro → SINTER demo
        // LG 27" 4K Monitor
        addTag(12L, "sharp-4k-image");
        addTag(12L, "true-color-display");
        addTag(12L, "ultra-smooth");
        addTag(12L, "pro-display");       // shared with MacBook Pro → SINTER demo
    }

    /** SADD tags:{productId} tag — idempotent; returns true if the tag was newly added. */
    public boolean addTag(Long productId, String tag) {
        validate(productId, tag);
        Long added = redisTemplate.opsForSet().add(tagKey(productId), tag);
        return added != null && added > 0;
    }

    /** SREM tags:{productId} tag — returns true if the tag existed and was removed. */
    public boolean removeTag(Long productId, String tag) {
        validate(productId, tag);
        Long removed = redisTemplate.opsForSet().remove(tagKey(productId), (Object) tag);
        return removed != null && removed > 0;
    }

    /** SISMEMBER tags:{productId} tag */
    public boolean hasTag(Long productId, String tag) {
        validate(productId, tag);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(tagKey(productId), tag));
    }

    /** SMEMBERS tags:{productId} — all tags for the product. */
    public Set<String> tags(Long productId) {
        validateId(productId);
        Set<String> members = redisTemplate.opsForSet().members(tagKey(productId));
        return members == null ? Set.of() : members;
    }

    /** SCARD tags:{productId} — number of tags. */
    public long tagCount(Long productId) {
        validateId(productId);
        Long size = redisTemplate.opsForSet().size(tagKey(productId));
        return size == null ? 0L : size;
    }

    /** SINTER tags:{id1} tags:{id2} — tags shared by both products. */
    public Set<String> commonTags(Long productId1, Long productId2) {
        validateId(productId1);
        validateId(productId2);
        Set<String> common = redisTemplate.opsForSet()
                .intersect(tagKey(productId1), tagKey(productId2));
        return common == null ? Set.of() : common;
    }

    public String tagKey(Long productId) {
        validateId(productId);
        return KEY_PREFIX + productId;
    }

    private void validate(Long productId, String tag) {
        validateId(productId);
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag cannot be blank");
        }
    }

    private void validateId(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId cannot be null");
        }
    }
}
