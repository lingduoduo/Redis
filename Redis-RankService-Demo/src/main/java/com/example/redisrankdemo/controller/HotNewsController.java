package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.model.RankItem;
import com.example.redisrankdemo.service.HotNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hot-news")
@RequiredArgsConstructor
public class HotNewsController {

    private final HotNewsService hotNewsService;

    /**
     * ZINCRBY hotNews:{today} 1 {newsId} — record one click for today.
     */
    @PostMapping("/{newsId}/click")
    public Map<String, Object> click(@PathVariable String newsId) {
        double newCount = hotNewsService.click(newsId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newsId", newsId);
        result.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        result.put("redisKey", hotNewsService.hotNewsKey(LocalDate.now()));
        result.put("clickCount", newCount);
        result.put("rank", hotNewsService.rank(newsId));
        return result;
    }

    /**
     * ZINCRBY hotNews:{date} 1 {newsId} — record one click on a specific date (for demo / backfill).
     */
    @PostMapping("/{date}/{newsId}/click")
    public Map<String, Object> clickOnDate(
            @PathVariable String date,
            @PathVariable String newsId) {
        LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        double newCount = hotNewsService.click(newsId, ld, 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newsId", newsId);
        result.put("date", date);
        result.put("redisKey", hotNewsService.hotNewsKey(date));
        result.put("clickCount", newCount);
        result.put("rank", hotNewsService.rank(newsId, ld));
        return result;
    }

    /**
     * ZREVRANGE hotNews:{today} 0 N-1 WITHSCORES — top-N most-clicked today.
     */
    @GetMapping("/top")
    public Map<String, Object> top(
            @RequestParam(defaultValue = "15") int n) {
        LocalDate today = LocalDate.now();
        List<RankItem> ranking = hotNewsService.top(n);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        result.put("redisKey", hotNewsService.hotNewsKey(today));
        result.put("redisCommand", "ZREVRANGE " + hotNewsService.hotNewsKey(today) + " 0 " + (n - 1) + " WITHSCORES");
        result.put("topN", n);
        result.put("ranking", ranking);
        return result;
    }

    /**
     * ZREVRANGE hotNews:{date} 0 N-1 WITHSCORES — top-N most-clicked on a specific date.
     */
    @GetMapping("/{date}/top")
    public Map<String, Object> topOnDate(
            @PathVariable String date,
            @RequestParam(defaultValue = "15") int n) {
        LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<RankItem> ranking = hotNewsService.top(n, ld);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("redisKey", hotNewsService.hotNewsKey(date));
        result.put("redisCommand", "ZREVRANGE " + hotNewsService.hotNewsKey(date) + " 0 " + (n - 1) + " WITHSCORES");
        result.put("topN", n);
        result.put("ranking", ranking);
        return result;
    }

    /**
     * ZREVRANK + ZSCORE hotNews:{today} {newsId} — rank and click count today.
     */
    @GetMapping("/{newsId}/rank")
    public Map<String, Object> rank(@PathVariable String newsId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newsId", newsId);
        result.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        result.put("rank", hotNewsService.rank(newsId));
        result.put("clickCount", hotNewsService.clickCount(newsId));
        return result;
    }

    /**
     * ZUNIONSTORE hotNews:merged:{days}d ... — aggregate last N days, return top-N.
     */
    @GetMapping("/merge")
    public Map<String, Object> merge(
            @RequestParam(defaultValue = "3") int days,
            @RequestParam(defaultValue = "15") int n) {
        List<RankItem> ranking = hotNewsService.merge(days, n);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("destKey", "hotNews:merged:" + days + "d");
        result.put("redisCommand", "ZUNIONSTORE hotNews:merged:" + days + "d " + days + " hotNews:{d0} ... hotNews:{d-" + (days - 1) + "}");
        result.put("topN", n);
        result.put("ranking", ranking);
        return result;
    }
}
