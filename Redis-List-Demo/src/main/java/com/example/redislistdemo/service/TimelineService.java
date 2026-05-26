package com.example.redislistdemo.service;

import com.example.redislistdemo.model.TimelineMessage;
import com.example.redislistdemo.model.TimelinePage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TimelineService {

    private static final String KEY_PREFIX = "timeline:";
    private static final long DEFAULT_MAX_LENGTH = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TimelineService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
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
        redisTemplate.opsForList().leftPush(key, serialize(message));
        redisTemplate.opsForList().trim(key, 0, maxLength - 1);
        return message;
    }

    public TimelineMessage publishExisting(String userId, TimelineMessage message, long maxLength) {
        validateUserId(userId);
        validateMessage(message);
        validateMaxLength(maxLength);

        String key = timelineKey(userId);
        redisTemplate.opsForList().leftPush(key, serialize(message));
        redisTemplate.opsForList().trim(key, 0, maxLength - 1);
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

    public TimelinePage page(String userId, long start, long end) {
        validateUserId(userId);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid range");
        }

        String key = timelineKey(userId);
        List<String> rawMessages = redisTemplate.opsForList().range(key, start, end);
        List<TimelineMessage> messages = rawMessages == null
                ? List.of()
                : rawMessages.stream().map(this::deserialize).toList();
        return new TimelinePage(userId, key, start, end, messages);
    }

    public long size(String userId) {
        validateUserId(userId);
        Long size = redisTemplate.opsForList().size(timelineKey(userId));
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
