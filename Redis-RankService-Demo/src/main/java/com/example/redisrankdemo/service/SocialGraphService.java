package com.example.redisrankdemo.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Social follow / fans graph backed by Redis Sets.
 *
 * keys:
 *   {userId}:follow  →  Set of user IDs this user follows
 *   {userId}:fans    →  Set of user IDs who follow this user
 *
 * Redis commands exercised:
 *   SADD    {u}:follow {v}  +  SADD {v}:fans {u}  – follow
 *   SREM    {u}:follow {v}  +  SREM {v}:fans {u}  – unfollow
 *   SISMEMBER {u}:follow {v}                       – is following?
 *   SMEMBERS  {u}:follow / {u}:fans                – list following / fans
 *   SCARD     {u}:follow / {u}:fans                – follow / fan count
 *   SINTER  {u}:follow {v}:fans                    – my followees who also follow v
 *   SDIFF   {v}:follow {u}:follow                  – v follows but u doesn't (may-know)
 *
 * Seed graph (u1001–u1005):
 *   u1001 ↔ u1002, u1001 ↔ u1003  (mutual)
 *   u1001 → u1004                  (one-way)
 *   u1002 → u1003, u1002 → u1005
 *   u1003 ↔ u1002
 *   u1004 → u1002
 *   u1005 → u1001, u1005 → u1002, u1005 → u1004
 *
 * Demo:
 *   SINTER u1001:follow u1002:fans → {u1003, u1004}   (u1001's followees who also follow u1002)
 *   SDIFF  u1002:follow u1001:follow → {u1001, u1005}  (u1001 may know u1005 via u1002)
 *   SDIFF  u1001:follow u1002:follow → {u1002, u1004}  (u1002 may know u1004 via u1001)
 */
@Service
@RequiredArgsConstructor
public class SocialGraphService {

    static final String FOLLOW_SUFFIX = ":follow";
    static final String FANS_SUFFIX   = ":fans";

    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    void seedGraph() {
        follow("u1001", "u1002");
        follow("u1001", "u1003");
        follow("u1001", "u1004");
        follow("u1002", "u1001");
        follow("u1002", "u1003");
        follow("u1002", "u1005");
        follow("u1003", "u1001");
        follow("u1003", "u1002");
        follow("u1004", "u1002");
        follow("u1005", "u1001");
        follow("u1005", "u1002");
        follow("u1005", "u1004");
    }

    /**
     * SADD {userId}:follow {targetId}  +  SADD {targetId}:fans {userId}
     * Idempotent: following the same user twice has no effect.
     * Returns true if this is a new follow relationship.
     */
    public boolean follow(String userId, String targetId) {
        validate(userId, targetId);
        Long added = redisTemplate.opsForSet().add(followKey(userId), targetId);
        redisTemplate.opsForSet().add(fansKey(targetId), userId);
        return added != null && added > 0;
    }

    /**
     * SREM {userId}:follow {targetId}  +  SREM {targetId}:fans {userId}
     * Returns true if the relationship existed and was removed.
     */
    public boolean unfollow(String userId, String targetId) {
        validate(userId, targetId);
        Long removed = redisTemplate.opsForSet().remove(followKey(userId), (Object) targetId);
        redisTemplate.opsForSet().remove(fansKey(targetId), (Object) userId);
        return removed != null && removed > 0;
    }

    /** SISMEMBER {userId}:follow {targetId} */
    public boolean isFollowing(String userId, String targetId) {
        validate(userId, targetId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(followKey(userId), targetId));
    }

    /** SMEMBERS {userId}:follow — all users this user follows. */
    public Set<String> following(String userId) {
        validateId(userId);
        Set<String> members = redisTemplate.opsForSet().members(followKey(userId));
        return members == null ? Set.of() : members;
    }

    /** SMEMBERS {userId}:fans — all users who follow this user. */
    public Set<String> fans(String userId) {
        validateId(userId);
        Set<String> members = redisTemplate.opsForSet().members(fansKey(userId));
        return members == null ? Set.of() : members;
    }

    /** SCARD {userId}:follow */
    public long followCount(String userId) {
        validateId(userId);
        Long size = redisTemplate.opsForSet().size(followKey(userId));
        return size == null ? 0L : size;
    }

    /** SCARD {userId}:fans */
    public long fanCount(String userId) {
        validateId(userId);
        Long size = redisTemplate.opsForSet().size(fansKey(userId));
        return size == null ? 0L : size;
    }

    /**
     * Mutual follow: both users follow each other.
     * SISMEMBER {userId}:follow {targetId} AND SISMEMBER {targetId}:follow {userId}
     */
    public boolean isMutual(String userId, String targetId) {
        validate(userId, targetId);
        return isFollowing(userId, targetId) && isFollowing(targetId, userId);
    }

    /**
     * SINTER {userId}:follow {targetId}:fans
     * Returns userId's followees who are also fans of targetId
     * (i.e., "people I follow who also follow targetId").
     */
    public Set<String> commonFollowing(String userId, String targetId) {
        validate(userId, targetId);
        Set<String> common = redisTemplate.opsForSet()
                .intersect(followKey(userId), fansKey(targetId));
        return common == null ? Set.of() : common;
    }

    /**
     * SDIFF {targetId}:follow {userId}:follow
     * Returns people targetId follows that userId does not —
     * userId may know these people through targetId.
     */
    public Set<String> mayKnow(String userId, String targetId) {
        validate(userId, targetId);
        Set<String> diff = redisTemplate.opsForSet()
                .difference(followKey(targetId), followKey(userId));
        return diff == null ? Set.of() : diff;
    }

    public String followKey(String userId) { return userId + FOLLOW_SUFFIX; }
    public String fansKey(String userId)   { return userId + FANS_SUFFIX; }

    private void validate(String userId, String targetId) {
        validateId(userId);
        validateId(targetId);
    }

    private void validateId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
    }
}
