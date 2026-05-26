package com.example.redisglobaliddemo.model;

public record NextIdResponse(
        String bizTag,
        long id,
        long segmentStart,
        long segmentEnd,
        long remainingInSegment
) {
}
