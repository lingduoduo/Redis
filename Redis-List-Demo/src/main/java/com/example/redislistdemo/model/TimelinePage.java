package com.example.redislistdemo.model;

import java.util.List;

public record TimelinePage(
        String userId,
        String redisKey,
        long start,
        long end,
        List<TimelineMessage> messages
) {
}
