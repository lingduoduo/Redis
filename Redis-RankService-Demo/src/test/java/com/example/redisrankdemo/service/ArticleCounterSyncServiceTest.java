package com.example.redisrankdemo.service;

import com.example.redisrankdemo.model.ArticleCounterSnapshot;
import com.example.redisrankdemo.repository.ArticleCounterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ArticleCounterSyncServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final SetOperations<String, String> setOps = mock(SetOperations.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ArticleCounterRepository repository = new ArticleCounterRepository();
    private final ArticleCounterSyncService syncService = new ArticleCounterSyncService(redisTemplate, repository);

    @Test
    void syncDirtyArticleCountersWritesRedisSnapshotToRepository() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.pop("article:dirty:view")).thenReturn("1", (String) null);
        when(setOps.pop("article:dirty:like")).thenReturn("1", (String) null);
        when(setOps.pop("article:dirty:pv")).thenReturn((String) null);
        when(valueOps.get("article:view:1")).thenReturn("10");
        when(valueOps.get("article:like:1")).thenReturn("3");
        when(valueOps.get("article:pv:1")).thenReturn("12");

        syncService.syncDirtyArticleCounters();

        assertThat(repository.findByArticleId(1L))
                .isEqualTo(new ArticleCounterSnapshot(1L, 10L, 3L, 12L));
    }

    @Test
    void drainDirtyArticleIdsDeduplicatesIdsFromDifferentCounterTypes() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.pop("article:dirty:view")).thenReturn("1", "bad", nullString());
        when(setOps.pop("article:dirty:like")).thenReturn("1", "2", nullString());
        when(setOps.pop("article:dirty:pv")).thenReturn((String) null);

        Set<Long> articleIds = syncService.drainDirtyArticleIds();

        assertThat(articleIds).containsExactly(1L, 2L);
    }

    @Test
    void syncTreatsMissingOrInvalidRedisValuesAsZero() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.pop("article:dirty:view")).thenReturn("7", (String) null);
        when(setOps.pop("article:dirty:like")).thenReturn((String) null);
        when(setOps.pop("article:dirty:pv")).thenReturn((String) null);
        when(valueOps.get("article:view:7")).thenReturn(null);
        when(valueOps.get("article:like:7")).thenReturn("not-a-number");
        when(valueOps.get("article:pv:7")).thenReturn("4");

        syncService.syncDirtyArticleCounters();

        assertThat(repository.findByArticleId(7L))
                .isEqualTo(new ArticleCounterSnapshot(7L, 0L, 0L, 4L));
    }

    private String nullString() {
        return null;
    }
}
