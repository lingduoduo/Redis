package com.example.redisrankdemo.service;

import com.example.redisrankdemo.model.ArticleCounterSnapshot;
import com.example.redisrankdemo.repository.ArticleCounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ArticleCounterSyncService {

    private static final Logger log = LoggerFactory.getLogger(ArticleCounterSyncService.class);

    private final StringRedisTemplate redisTemplate;
    private final ArticleCounterRepository repository;

    public ArticleCounterSyncService(StringRedisTemplate redisTemplate, ArticleCounterRepository repository) {
        this.redisTemplate = redisTemplate;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${counter-sync.article.fixed-delay-ms:5000}")
    public void syncDirtyArticleCounters() {
        Set<Long> articleIds = drainDirtyArticleIds();
        for (Long articleId : articleIds) {
            ArticleCounterSnapshot snapshot = new ArticleCounterSnapshot(
                    articleId,
                    getLong(ArticleCounterType.VIEW.counterKey(articleId)),
                    getLong(ArticleCounterType.LIKE.counterKey(articleId)),
                    getLong(ArticleCounterType.PV.counterKey(articleId))
            );
            repository.save(snapshot);
            log.debug("Synced article counters to repository: {}", snapshot);
        }
    }

    Set<Long> drainDirtyArticleIds() {
        Set<Long> articleIds = new LinkedHashSet<>();
        for (ArticleCounterType counterType : ArticleCounterType.values()) {
            drainDirtyKey(counterType.dirtyKey(), articleIds);
        }
        return articleIds;
    }

    private void drainDirtyKey(String dirtyKey, Set<Long> articleIds) {
        while (true) {
            String rawArticleId = redisTemplate.opsForSet().pop(dirtyKey);
            if (rawArticleId == null) {
                return;
            }
            try {
                articleIds.add(Long.parseLong(rawArticleId));
            } catch (NumberFormatException e) {
                log.warn("Ignore non-numeric article id in dirty key {}: {}", dirtyKey, rawArticleId);
            }
        }
    }

    private long getLong(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Non-numeric counter value for key {}: {}", key, value);
            return 0L;
        }
    }
}
