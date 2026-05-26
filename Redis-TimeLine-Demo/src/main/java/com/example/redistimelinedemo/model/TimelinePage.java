package com.example.redistimelinedemo.model;

import java.util.List;

public record TimelinePage(
        String userId,
        String redisKey,
        Long nextCursor,
        List<TimelineMessage> messages
) {
}
