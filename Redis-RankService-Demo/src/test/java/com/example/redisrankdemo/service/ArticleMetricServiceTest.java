package com.example.redisrankdemo.service;

import com.example.redisrankdemo.model.ArticleStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleMetricServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SetOperations<String, String> setOps;
    @Mock private RankService rankService;

    private ArticleMetricService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new ArticleMetricService(redisTemplate, rankService);
    }

    @Test
    void incrViewIncrementsCounterAndReturnsNewValue() {
        when(valueOps.increment("article:view:1")).thenReturn(3L);

        assertThat(service.incrView(1L)).isEqualTo(3L);
        verify(valueOps).increment("article:view:1");
        verify(setOps).add("article:dirty:view", "1");
    }

    @Test
    void incrViewReturnsZeroWhenRedisReturnsNull() {
        when(valueOps.increment("article:view:1")).thenReturn(null);

        assertThat(service.incrView(1L)).isEqualTo(0L);
    }

    @Test
    void trackUvAddsVisitorAndReturnsTotalUniqueCount() {
        String todayKey = "article:uv:1:" + LocalDate.now();
        when(setOps.add(todayKey, "v99")).thenReturn(1L);
        when(setOps.size(todayKey)).thenReturn(5L);

        long uv = service.trackUv(1L, "v99");

        assertThat(uv).isEqualTo(5L);
        verify(setOps).add(todayKey, "v99");
        verify(redisTemplate).expire(eq(todayKey), eq(Duration.ofDays(2)));
    }

    @Test
    void trackUvReturnsZeroWhenSizeIsNull() {
        String todayKey = "article:uv:1:" + LocalDate.now();
        when(setOps.size(todayKey)).thenReturn(null);

        assertThat(service.trackUv(1L, "visitor")).isEqualTo(0L);
    }

    @Test
    void recordReadIncrementsAllCountersAndAdds1PointToRank() {
        // increment() is called for view and pv; get() is called by getViews() at the end
        when(valueOps.increment(any())).thenReturn(1L);
        when(valueOps.get("article:view:1")).thenReturn("10");
        when(setOps.size(any())).thenReturn(3L);

        long views = service.recordRead(1L, "u42", "v99");

        assertThat(views).isEqualTo(10L);
        verify(valueOps).increment("article:view:1");
        verify(valueOps).increment("article:pv:1");
        verify(setOps).add("article:dirty:view", "1");
        verify(setOps).add("article:dirty:pv", "1");
        verify(rankService).addScore("article:1", 1.0);
    }

    @Test
    void recordLikeIncrementsLikeCounterAndAdds5PointsToRank() {
        when(valueOps.increment("article:like:1")).thenReturn(7L);

        long likes = service.recordLike(1L, "u42");

        assertThat(likes).isEqualTo(7L);
        verify(setOps).add("article:dirty:like", "1");
        verify(rankService).addScore("article:1", 5.0);
    }

    @Test
    void statsAggregatesAllMetricsForArticle() {
        when(valueOps.get("article:view:1")).thenReturn("10");
        when(valueOps.get("article:like:1")).thenReturn("4");
        when(valueOps.get("article:pv:1")).thenReturn("12");
        when(setOps.size("article:uv:1:" + LocalDate.now())).thenReturn(3L);
        when(rankService.myRank("article:1")).thenReturn(2L);

        ArticleStats stats = service.stats(1L);

        assertThat(stats.articleId()).isEqualTo(1L);
        assertThat(stats.views()).isEqualTo(10L);
        assertThat(stats.likes()).isEqualTo(4L);
        assertThat(stats.pv()).isEqualTo(12L);
        assertThat(stats.uv()).isEqualTo(3L);
        assertThat(stats.dailyRank()).isEqualTo(2L);
    }

    @Test
    void statsReturnsZerosForNewArticle() {
        when(valueOps.get(any())).thenReturn(null);
        when(setOps.size(any())).thenReturn(null);
        when(rankService.myRank(any())).thenReturn(-1L);

        ArticleStats stats = service.stats(99L);

        assertThat(stats.views()).isEqualTo(0L);
        assertThat(stats.likes()).isEqualTo(0L);
        assertThat(stats.uv()).isEqualTo(0L);
        assertThat(stats.dailyRank()).isEqualTo(-1L);
    }
}
