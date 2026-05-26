package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.model.ArticleCounterSnapshot;
import com.example.redisrankdemo.model.ArticleStats;
import com.example.redisrankdemo.model.LikeRequest;
import com.example.redisrankdemo.model.LikeStats;
import com.example.redisrankdemo.model.ViewRequest;
import com.example.redisrankdemo.repository.ArticleCounterRepository;
import com.example.redisrankdemo.service.ArticleCounterSyncService;
import com.example.redisrankdemo.service.ArticleMetricService;
import com.example.redisrankdemo.service.LikeSetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/articles")
public class ArticleController {

    private final ArticleMetricService articleMetricService;
    private final ArticleCounterRepository articleCounterRepository;
    private final ArticleCounterSyncService articleCounterSyncService;
    private final LikeSetService likeSetService;

    public ArticleController(ArticleMetricService articleMetricService,
                             ArticleCounterRepository articleCounterRepository,
                             ArticleCounterSyncService articleCounterSyncService,
                             LikeSetService likeSetService) {
        this.articleMetricService = articleMetricService;
        this.articleCounterRepository = articleCounterRepository;
        this.articleCounterSyncService = articleCounterSyncService;
        this.likeSetService = likeSetService;
    }

    @PostMapping("/read")
    public Map<String, Object> read(@Valid @RequestBody ViewRequest request) {
        long views = articleMetricService.recordRead(request.articleId(), request.userId(), request.visitorId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "read recorded");
        result.put("articleId", request.articleId());
        result.put("views", views);
        result.put("pv", articleMetricService.getPv(request.articleId()));
        result.put("uv", articleMetricService.getUv(request.articleId()));
        return result;
    }

    @PostMapping("/like")
    public Map<String, Object> like(@Valid @RequestBody LikeRequest request) {
        long likes = articleMetricService.recordLike(request.articleId(), request.userId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "like recorded");
        result.put("articleId", request.articleId());
        result.put("likes", likes);
        return result;
    }

    @GetMapping("/{articleId}/stats")
    public ArticleStats stats(@PathVariable Long articleId) {
        return articleMetricService.stats(articleId);
    }

    @GetMapping("/{articleId}/db-counters")
    public ArticleCounterSnapshot dbCounters(@PathVariable Long articleId) {
        return articleCounterRepository.findByArticleId(articleId);
    }

    @PostMapping("/counters/sync")
    public Map<String, Object> syncCounters() {
        articleCounterSyncService.syncDirtyArticleCounters();
        return Map.of("message", "counter sync triggered");
    }

    @PostMapping("/{articleId}/view")
    public Map<String, Object> incrView(@PathVariable Long articleId) {
        long views = articleMetricService.incrView(articleId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleId", articleId);
        result.put("views", views);
        return result;
    }

    /**
     * SADD like:{articleId} userId
     * Idempotent: liking twice has no effect. Returns whether this was a new like.
     */
    @PostMapping("/{articleId}/likes/{userId}")
    public Map<String, Object> likeArticle(
            @PathVariable Long articleId,
            @PathVariable String userId) {
        boolean isNew = likeSetService.like(articleId, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleId", articleId);
        result.put("userId", userId);
        result.put("likeKey", likeSetService.likeKey(articleId));
        result.put("newLike", isNew);
        result.put("likeCount", likeSetService.likeCount(articleId));
        return result;
    }

    /**
     * SREM like:{articleId} userId
     */
    @DeleteMapping("/{articleId}/likes/{userId}")
    public Map<String, Object> unlikeArticle(
            @PathVariable Long articleId,
            @PathVariable String userId) {
        boolean removed = likeSetService.unlike(articleId, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleId", articleId);
        result.put("userId", userId);
        result.put("removed", removed);
        result.put("likeCount", likeSetService.likeCount(articleId));
        return result;
    }

    /**
     * SISMEMBER like:{articleId} userId
     */
    @GetMapping("/{articleId}/likes/{userId}")
    public Map<String, Object> isLiked(
            @PathVariable Long articleId,
            @PathVariable String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleId", articleId);
        result.put("userId", userId);
        result.put("liked", likeSetService.isLiked(articleId, userId));
        return result;
    }

    /**
     * SMEMBERS + SCARD like:{articleId}
     */
    @GetMapping("/{articleId}/likes")
    public LikeStats likeStats(@PathVariable Long articleId) {
        return new LikeStats(
                articleId,
                likeSetService.likeKey(articleId),
                likeSetService.likeCount(articleId),
                likeSetService.likedBy(articleId)
        );
    }
}
