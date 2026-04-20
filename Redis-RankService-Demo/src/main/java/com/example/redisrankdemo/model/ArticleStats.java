package com.example.redisrankdemo.model;

public record ArticleStats(
        Long articleId,
        Long views,
        Long likes,
        Long pv,
        Long uv,
        Long dailyRank
) {
}
