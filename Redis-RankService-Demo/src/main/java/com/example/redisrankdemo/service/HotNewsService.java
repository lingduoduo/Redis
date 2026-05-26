package com.example.redisrankdemo.service;

import com.example.redisrankdemo.model.RankItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Hot-news click ranking backed by a Redis Sorted Set per day.
 *
 * key = hotNews:{yyyyMMdd}  →  ZSet  member=newsId, score=clickCount
 *
 * Redis commands exercised:
 *   ZINCRBY  hotNews:{date} 1 {newsId}        – record a click
 *   ZREVRANGE hotNews:{date} 0 N-1 WITHSCORES – top-N most-clicked today
 *   ZREVRANK  hotNews:{date} {newsId}          – rank of a news item
 *   ZSCORE    hotNews:{date} {newsId}          – click count
 *   ZUNIONSTORE hotNews:merged:{N}d N hotNews:{d1} hotNews:{d2} ...
 *                                              – aggregate last N days
 *
 * Demo:
 *   ZINCRBY  hotNews:20190926 1 n6001   → 121
 *   ZREVRANGE hotNews:20190926 0 14 WITHSCORES  → top-15
 *   ZUNIONSTORE hotNews:merged:3d 3 hotNews:yesterday hotNews:today ...
 */
@Service
@RequiredArgsConstructor
public class HotNewsService {

    static final String KEY_PREFIX = "hotNews:";
    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    void seedClickData() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        // today's clicks
        click("n6001", today,      120);
        click("n6002", today,       80);
        click("n6003", today,      200);
        click("n6004", today,      150);
        click("n6005", today,       60);
        click("n6006", today,      180);
        click("n6007", today,       90);
        click("n6008", today,       45);

        // yesterday's clicks
        click("n6001", yesterday,  500);
        click("n6002", yesterday,  430);
        click("n6003", yesterday,  380);
        click("n6004", yesterday,  310);
        click("n6005", yesterday,  290);
        click("n6006", yesterday,  100);

        // two days ago
        click("n6001", twoDaysAgo, 600);
        click("n6002", twoDaysAgo, 550);
        click("n6003", twoDaysAgo, 200);
        click("n6005", twoDaysAgo, 400);
        click("n6008", twoDaysAgo, 300);
        click("n6009", twoDaysAgo, 250);
    }

    /**
     * ZINCRBY hotNews:{today} 1 {newsId} — record one click for today.
     * Returns the new click count.
     */
    public double click(String newsId) {
        validateNewsId(newsId);
        return click(newsId, LocalDate.now(), 1);
    }

    /**
     * ZINCRBY hotNews:{date} {delta} {newsId} — record delta clicks on a specific date.
     * Returns the new click count.
     */
    public double click(String newsId, LocalDate date, long delta) {
        validateNewsId(newsId);
        Double result = redisTemplate.opsForZSet()
                .incrementScore(hotNewsKey(date), newsId, delta);
        return result != null ? result : 0.0;
    }

    /**
     * ZREVRANGE hotNews:{today} 0 n-1 WITHSCORES — top-N most-clicked today.
     */
    public List<RankItem> top(int n) {
        return top(n, LocalDate.now());
    }

    /**
     * ZREVRANGE hotNews:{date} 0 n-1 WITHSCORES — top-N most-clicked on a given date.
     */
    public List<RankItem> top(int n, LocalDate date) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(hotNewsKey(date), 0, n - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankItem> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            list.add(new RankItem(rank++, t.getValue(), t.getScore()));
        }
        return list;
    }

    /**
     * ZREVRANK hotNews:{today} {newsId} — 1-based rank of a news item today.
     * Returns -1 if not present.
     */
    public long rank(String newsId) {
        return rank(newsId, LocalDate.now());
    }

    /**
     * ZREVRANK hotNews:{date} {newsId} — 1-based rank on a specific date.
     */
    public long rank(String newsId, LocalDate date) {
        validateNewsId(newsId);
        Long r = redisTemplate.opsForZSet().reverseRank(hotNewsKey(date), newsId);
        return r == null ? -1L : r + 1;
    }

    /**
     * ZSCORE hotNews:{today} {newsId} — total clicks today.
     */
    public double clickCount(String newsId) {
        return clickCount(newsId, LocalDate.now());
    }

    /**
     * ZSCORE hotNews:{date} {newsId} — total clicks on a specific date.
     */
    public double clickCount(String newsId, LocalDate date) {
        validateNewsId(newsId);
        Double score = redisTemplate.opsForZSet().score(hotNewsKey(date), newsId);
        return score != null ? score : 0.0;
    }

    /**
     * ZUNIONSTORE hotNews:merged:{days}d {days} hotNews:{d0} hotNews:{d-1} ...
     * Aggregates click counts across the last {@code days} days into a single key.
     * Returns top-N from the merged result.
     */
    public List<RankItem> merge(int days, int topN) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be >= 1");
        }
        LocalDate today = LocalDate.now();
        List<String> keys = IntStream.range(0, days)
                .mapToObj(i -> hotNewsKey(today.minusDays(i)))
                .toList();

        String destKey = KEY_PREFIX + "merged:" + days + "d";
        String first = keys.get(0);
        List<String> rest = keys.subList(1, keys.size());
        redisTemplate.opsForZSet().unionAndStore(first, rest, destKey);
        return top(topN, destKey);
    }

    private List<RankItem> top(int n, String key) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankItem> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            list.add(new RankItem(rank++, t.getValue(), t.getScore()));
        }
        return list;
    }

    public String hotNewsKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FMT);
    }

    public String hotNewsKey(String yyyyMMdd) {
        return KEY_PREFIX + yyyyMMdd;
    }

    private void validateNewsId(String newsId) {
        if (newsId == null || newsId.isBlank()) {
            throw new IllegalArgumentException("newsId cannot be blank");
        }
    }
}
