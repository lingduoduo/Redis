package com.example.redisrankdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Tracks per-article likes with a Redis Set.
 *
 * key = like:{articleId}  →  Set of userId strings
 *
 * Redis commands exercised:
 *   SADD      like:{id} userId  – like      (idempotent: SADD returns 0 if already a member)
 *   SREM      like:{id} userId  – unlike
 *   SISMEMBER like:{id} userId  – has this user liked?
 *   SMEMBERS  like:{id}         – all users who liked
 *   SCARD     like:{id}         – unique like count
 *
 * Using a Set instead of a plain INCR counter prevents a user from liking
 * the same article more than once and supports "did I like this?" queries.
 */
@Service
public class LikeSetService {

    static final String KEY_PREFIX = "like:";

    private final StringRedisTemplate redisTemplate;

    public LikeSetService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** SADD — returns true if this is a new like, false if the user already liked it. */
    public boolean like(Long articleId, String userId) {
        validateArticleId(articleId);
        validateUserId(userId);
        Long added = redisTemplate.opsForSet().add(likeKey(articleId), userId);
        return added != null && added > 0;
    }

    /** SREM — returns true if the like was removed, false if the user had not liked it. */
    public boolean unlike(Long articleId, String userId) {
        validateArticleId(articleId);
        validateUserId(userId);
        Long removed = redisTemplate.opsForSet().remove(likeKey(articleId), (Object) userId);
        return removed != null && removed > 0;
    }

    /** SISMEMBER — true if the user has liked this article. */
    public boolean isLiked(Long articleId, String userId) {
        validateArticleId(articleId);
        validateUserId(userId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(likeKey(articleId), userId));
    }

    /** SMEMBERS — set of all user IDs who liked this article. */
    public Set<String> likedBy(Long articleId) {
        validateArticleId(articleId);
        Set<String> members = redisTemplate.opsForSet().members(likeKey(articleId));
        return members == null ? Set.of() : members;
    }

    /** SCARD — number of unique users who liked this article. */
    public long likeCount(Long articleId) {
        validateArticleId(articleId);
        Long size = redisTemplate.opsForSet().size(likeKey(articleId));
        return size == null ? 0L : size;
    }

    public String likeKey(Long articleId) {
        validateArticleId(articleId);
        return KEY_PREFIX + articleId;
    }

    private void validateArticleId(Long articleId) {
        if (articleId == null) {
            throw new IllegalArgumentException("articleId cannot be null");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
    }
}
