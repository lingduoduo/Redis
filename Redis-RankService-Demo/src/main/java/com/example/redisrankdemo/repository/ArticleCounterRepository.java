package com.example.redisrankdemo.repository;

import com.example.redisrankdemo.model.ArticleCounterSnapshot;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ArticleCounterRepository {

    private final Map<Long, ArticleCounterSnapshot> table = new ConcurrentHashMap<>();

    public void save(ArticleCounterSnapshot snapshot) {
        table.put(snapshot.articleId(), snapshot);
    }

    public ArticleCounterSnapshot findByArticleId(Long articleId) {
        return table.getOrDefault(articleId, new ArticleCounterSnapshot(articleId, 0L, 0L, 0L));
    }

    public Map<Long, ArticleCounterSnapshot> findAll() {
        return Map.copyOf(table);
    }
}
