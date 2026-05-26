package com.example.redistimelinedemo.service;

import com.example.redistimelinedemo.model.TimelineMessage;
import com.example.redistimelinedemo.model.TimelinePage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Timeline backed by a Redis Sorted Set.
 *
 * key   = timeline:{userId}
 * score = epoch-millis of the message (enables time-range queries and cursor pagination)
 * member = JSON-serialized TimelineMessage (UUID messageId guarantees uniqueness)
 *
 * Key commands exercised:
 *   ZADD   – publish
 *   ZREVRANGEBYSCORE … LIMIT – cursor-based newest-first page
 *   ZRANGEBYSCORE  – time-range query
 *   ZREMRANGEBYRANK – trim to keep newest N
 *   ZCARD  – size
 */
@Service
public class TimelineZSetService {

    private static final String KEY_PREFIX = "timeline:";
    private static final long DEFAULT_MAX_LENGTH = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TimelineZSetService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public TimelineMessage publish(String userId, String authorId, String content) {
        return publish(userId, authorId, content, DEFAULT_MAX_LENGTH);
    }

    public TimelineMessage publish(String userId, String authorId, String content, long maxLength) {
        validateUserId(userId);
        validateAuthorId(authorId);
        validateContent(content);
        validateMaxLength(maxLength);

        TimelineMessage message = new TimelineMessage(
                UUID.randomUUID().toString(),
                authorId,
                content,
                Instant.now()
        );
        String key = timelineKey(userId);
        redisTemplate.opsForZSet().add(key, serialize(message), message.createdAt().toEpochMilli());
        trimToMaxLength(key, maxLength);
        return message;
    }

    public TimelineMessage publishExisting(String userId, TimelineMessage message, long maxLength) {
        validateUserId(userId);
        validateMessage(message);
        validateMaxLength(maxLength);

        String key = timelineKey(userId);
        redisTemplate.opsForZSet().add(key, serialize(message), message.createdAt().toEpochMilli());
        trimToMaxLength(key, maxLength);
        return message;
    }

    public List<TimelineMessage> fanout(List<String> receiverUserIds, String authorId, String content) {
        return fanout(receiverUserIds, authorId, content, DEFAULT_MAX_LENGTH);
    }

    public List<TimelineMessage> fanout(List<String> receiverUserIds, String authorId, String content, long maxLength) {
        if (receiverUserIds == null || receiverUserIds.isEmpty()) {
            throw new IllegalArgumentException("receiverUserIds cannot be empty");
        }
        validateAuthorId(authorId);
        validateContent(content);
        validateMaxLength(maxLength);

        TimelineMessage message = new TimelineMessage(
                UUID.randomUUID().toString(),
                authorId,
                content,
                Instant.now()
        );
        receiverUserIds.stream().distinct().forEach(userId -> publishExisting(userId, message, maxLength));
        return List.of(message);
    }

    /**
     * Returns up to pageSize messages with score <= maxScore, newest-first (ZREVRANGEBYSCORE).
     * Pass null as maxScore to start from the newest message.
     * Use TimelinePage.nextCursor() as maxScore for subsequent pages; null means end of timeline.
     */
    public TimelinePage page(String userId, Long maxScore, int pageSize) {
        validateUserId(userId);
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }

        double max = maxScore != null ? maxScore.doubleValue() : Double.MAX_VALUE;
        String key = timelineKey(userId);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, Double.NEGATIVE_INFINITY, max, 0, pageSize);

        if (tuples == null || tuples.isEmpty()) {
            return new TimelinePage(userId, key, null, List.of());
        }

        List<ZSetOperations.TypedTuple<String>> tupleList = new ArrayList<>(tuples);
        List<TimelineMessage> messages = tupleList.stream()
                .map(t -> deserialize(t.getValue()))
                .toList();

        Long nextCursor = null;
        if (tupleList.size() == pageSize) {
            Double lastScore = tupleList.get(tupleList.size() - 1).getScore();
            if (lastScore != null) {
                nextCursor = lastScore.longValue() - 1;
            }
        }

        return new TimelinePage(userId, key, nextCursor, messages);
    }

    /**
     * Returns all messages with createdAt in [fromEpochMillis, toEpochMillis], newest-first
     * (ZREVRANGEBYSCORE with explicit score bounds).
     */
    public List<TimelineMessage> rangeByTime(String userId, long fromEpochMillis, long toEpochMillis) {
        validateUserId(userId);
        if (fromEpochMillis > toEpochMillis) {
            throw new IllegalArgumentException("fromEpochMillis must be <= toEpochMillis");
        }

        String key = timelineKey(userId);
        Set<String> raw = redisTemplate.opsForZSet()
                .reverseRangeByScore(key, fromEpochMillis, toEpochMillis);
        return raw == null ? List.of() : raw.stream().map(this::deserialize).toList();
    }

    public long size(String userId) {
        validateUserId(userId);
        Long size = redisTemplate.opsForZSet().size(timelineKey(userId));
        return size == null ? 0L : size;
    }

    public void clear(String userId) {
        validateUserId(userId);
        redisTemplate.delete(timelineKey(userId));
    }

    public String timelineKey(String userId) {
        validateUserId(userId);
        return KEY_PREFIX + userId;
    }

    private void trimToMaxLength(String key, long maxLength) {
        // ZREMRANGEBYRANK key 0 -(maxLength+1) removes oldest members, keeping newest maxLength
        redisTemplate.opsForZSet().removeRange(key, 0L, -(maxLength + 1));
    }

    private String serialize(TimelineMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize timeline message", e);
        }
    }

    private TimelineMessage deserialize(String value) {
        try {
            return objectMapper.readValue(value, TimelineMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize timeline message", e);
        }
    }

    private void validateMessage(TimelineMessage message) {
        if (message == null || message.messageId() == null || message.messageId().isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
    }

    private void validateAuthorId(String authorId) {
        if (authorId == null || authorId.isBlank()) {
            throw new IllegalArgumentException("authorId cannot be blank");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content cannot be blank");
        }
    }

    private void validateMaxLength(long maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
    }
}
