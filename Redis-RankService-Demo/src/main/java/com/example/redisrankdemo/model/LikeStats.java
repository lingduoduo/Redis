package com.example.redisrankdemo.model;

import java.util.Set;

public record LikeStats(
        Long articleId,
        String likeKey,
        long likeCount,
        Set<String> likedBy
) {
}
