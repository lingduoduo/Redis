package com.example.redistimelinedemo.model;

import java.time.Instant;

public record TimelineMessage(
        String messageId,
        String authorId,
        String content,
        Instant createdAt
) {
}
