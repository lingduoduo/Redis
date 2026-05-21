package com.example.redisrankdemo.service;

import com.example.redisrankdemo.model.ArticleStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

@Service
public class ArticleMetricService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMetricService.class);

    private final StringRedisTemplate redisTemplate;
    private final RankService rankService;

    public ArticleMetricService(StringRedisTemplate redisTemplate, RankService rankService) {
        this.redisTemplate = redisTemplate;
        this.rankService = rankService;
    }

    private static final Duration UV_EXPIRE = Duration.ofDays(2);

    public long incrView(Long articleId) {
        return valueOpsIncrement("article:view:" + articleId);
    }

    public long incrLike(Long articleId) {
        return valueOpsIncrement("article:like:" + articleId);
    }

    public long incrPv(Long articleId) {
        return valueOpsIncrement("article:pv:" + articleId);
    }

    public long trackUv(Long articleId, String visitorId) {
        String key = "article:uv:" + articleId + ":" + LocalDate.now();
        Long added = redisTemplate.opsForSet().add(key, visitorId);
        redisTemplate.expire(key, UV_EXPIRE);
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0L : size;
    }

    public long recordRead(Long articleId, String userId, String visitorId) {
        incrView(articleId);
        incrPv(articleId);
        trackUv(articleId, visitorId);
        rankService.addScore(articleMemberId(articleId), 1.0);
        return getViews(articleId);
    }

    public long recordLike(Long articleId, String userId) {
        long likes = incrLike(articleId);
        rankService.addScore(articleMemberId(articleId), 5.0);
        return likes;
    }

    public ArticleStats stats(Long articleId) {
        return new ArticleStats(
                articleId,
                getViews(articleId),
                getLikes(articleId),
                getPv(articleId),
                getUv(articleId),
                rankService.myRank(articleMemberId(articleId))
        );
    }

    public long getViews(Long articleId) {
        return getLong("article:view:" + articleId);
    }

    public long getLikes(Long articleId) {
        return getLong("article:like:" + articleId);
    }

    public long getPv(Long articleId) {
        return getLong("article:pv:" + articleId);
    }

    public long getUv(Long articleId) {
        String key = "article:uv:" + articleId + ":" + LocalDate.now();
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0L : size;
    }

    private long valueOpsIncrement(String key) {
        Long value = redisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    private long getLong(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Non-numeric value for key {}: {}", key, value);
            return 0L;
        }
    }

    private String articleMemberId(Long articleId) {
        return "article:" + articleId;
    }
}
