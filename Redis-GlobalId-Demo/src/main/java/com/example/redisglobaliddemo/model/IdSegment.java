package com.example.redisglobaliddemo.model;

public record IdSegment(
        String bizTag,
        String redisKey,
        long start,
        long end,
        long step
) {
}
