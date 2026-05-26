package com.example.redisrankdemo.model;

public record ArticleCounterSnapshot(
        Long articleId,
        long views,
        long likes,
        long pv
) {
}
