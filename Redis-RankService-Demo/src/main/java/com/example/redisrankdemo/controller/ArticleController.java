package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.model.ArticleStats;
import com.example.redisrankdemo.model.LikeRequest;
import com.example.redisrankdemo.model.ViewRequest;
import com.example.redisrankdemo.service.ArticleMetricService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/articles")
public class ArticleController {

    private final ArticleMetricService articleMetricService;

    public ArticleController(ArticleMetricService articleMetricService) {
        this.articleMetricService = articleMetricService;
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

    @PostMapping("/{articleId}/view")
    public Map<String, Object> incrView(@PathVariable Long articleId) {
        long views = articleMetricService.incrView(articleId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleId", articleId);
        result.put("views", views);
        return result;
    }
}
