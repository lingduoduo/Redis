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
        addTag(1L, "超视网膜屏幕");
        addTag(1L, "钛金属设计");
        addTag(1L, "专业相机系统");
        addTag(1L, "5G网络");
        // MacBook Pro 14"
        addTag(2L, "M3芯片");
        addTag(2L, "长效续航");
        addTag(2L, "专业显示屏");
        addTag(2L, "轻薄便携");
        // AirPods Pro
        addTag(3L, "主动降噪");
        addTag(3L, "空间音频");
        addTag(3L, "无线充电");
        // Galaxy S24 Ultra
        addTag(6L, "高分辨率屏幕");
        addTag(6L, "内置S Pen");
        addTag(6L, "AI功能");
        addTag(6L, "专业相机系统");  // shared with iPhone 15 Pro → SINTER demo
        // Sony WH-1000XM5
        addTag(9L, "主动降噪");      // shared with AirPods Pro → SINTER demo
        addTag(9L, "高音质");
        addTag(9L, "舒适佩戴");
        addTag(9L, "长效续航");
        // LG 27" 4K Monitor
        addTag(12L, "画面清晰细腻");
        addTag(12L, "真彩清晰显示屏");
        addTag(12L, "流畅至极");
        addTag(12L, "专业显示屏");    // shared with MacBook Pro → SINTER demo
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
