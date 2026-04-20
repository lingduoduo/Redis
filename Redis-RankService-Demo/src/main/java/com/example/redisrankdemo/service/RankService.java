package com.example.redisrankdemo.service;

import com.example.redisrankdemo.model.RankItem;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class RankService {

    private final StringRedisTemplate redisTemplate;

    public RankService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    private static final String RANK_KEY_PREFIX = "rank:article:daily:";

    public String todayRankKey() {
        return RANK_KEY_PREFIX + LocalDate.now();
    }

    // ── article daily leaderboard (used by ArticleMetricService) ──────────

    public void addScore(String memberId, double score) {
        incrementScore(todayRankKey(), memberId, score);
    }

    public List<RankItem> top(int n) {
        return top(todayRankKey(), n);
    }

    public Long myRank(String memberId) {
        return myRank(todayRankKey(), memberId);
    }

    public Double myScore(String memberId) {
        return myScore(todayRankKey(), memberId);
    }

    // ── generic leaderboard operations ────────────────────────────────────

    public void setScore(String leaderboard, String memberId, double score) {
        redisTemplate.opsForZSet().add(leaderboard, memberId, score);
    }

    public double incrementScore(String leaderboard, String memberId, double delta) {
        Double result = redisTemplate.opsForZSet().incrementScore(leaderboard, memberId, delta);
        return result != null ? result : 0.0;
    }

    public List<RankItem> top(String leaderboard, int n) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(leaderboard, 0, n - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankItem> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            list.add(new RankItem(rank++, tuple.getValue(), tuple.getScore()));
        }
        return list;
    }

    public Long myRank(String leaderboard, String memberId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(leaderboard, memberId);
        return rank == null ? -1L : rank + 1;
    }

    public Double myScore(String leaderboard, String memberId) {
        Double score = redisTemplate.opsForZSet().score(leaderboard, memberId);
        return score == null ? 0.0 : score;
    }

    // Returns the window of members surrounding memberId, ±radius positions.
    // Uses ZREVRANK to locate the member then ZREVRANGEWITHSCORES for the window.
    public List<RankItem> around(String leaderboard, String memberId, int radius) {
        Long zeroIndexedRank = redisTemplate.opsForZSet().reverseRank(leaderboard, memberId);
        if (zeroIndexedRank == null) {
            return Collections.emptyList();
        }

        long start = Math.max(0, zeroIndexedRank - radius);
        long end = zeroIndexedRank + radius;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(leaderboard, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankItem> list = new ArrayList<>();
        int rank = (int) start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            list.add(new RankItem(rank++, tuple.getValue(), tuple.getScore()));
        }
        return list;
    }

    public boolean remove(String leaderboard, String memberId) {
        Long removed = redisTemplate.opsForZSet().remove(leaderboard, memberId);
        return removed != null && removed > 0;
    }

    public long count(String leaderboard) {
        Long size = redisTemplate.opsForZSet().size(leaderboard);
        return size != null ? size : 0L;
    }
}
