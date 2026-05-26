package com.example.redislistdemo.model;

import java.time.Instant;

public record TimelineMessage(
        String messageId,
        String authorId,
        String content,
        Instant createdAt
) {
}
