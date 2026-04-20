package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.model.RankItem;
import com.example.redisrankdemo.model.ScoreRequest;
import com.example.redisrankdemo.service.RankService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/leaderboard/{name}")
public class LeaderboardController {

    private final RankService rankService;

    public LeaderboardController(RankService rankService) {
        this.rankService = rankService;
    }

    @PostMapping("/score")
    public Map<String, Object> setScore(
            @PathVariable String name,
            @Valid @RequestBody ScoreRequest request) {
        rankService.setScore(name, request.memberId(), request.score());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaderboard", name);
        result.put("memberId", request.memberId());
        result.put("score", rankService.myScore(name, request.memberId()));
        result.put("rank", rankService.myRank(name, request.memberId()));
        return result;
    }

    @PostMapping("/increment")
    public Map<String, Object> incrementScore(
            @PathVariable String name,
            @Valid @RequestBody ScoreRequest request) {
        double newScore = rankService.incrementScore(name, request.memberId(), request.score());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaderboard", name);
        result.put("memberId", request.memberId());
        result.put("score", newScore);
        result.put("rank", rankService.myRank(name, request.memberId()));
        return result;
    }

    @GetMapping("/top")
    public Map<String, Object> top(
            @PathVariable String name,
            @RequestParam(defaultValue = "10") @Positive int n) {
        List<RankItem> players = rankService.top(name, n);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaderboard", name);
        result.put("players", players);
        result.put("count", players.size());
        return result;
    }

    @GetMapping("/me/{memberId}")
    public Map<String, Object> myRank(
            @PathVariable String name,
            @PathVariable @NotBlank String memberId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaderboard", name);
        result.put("memberId", memberId);
        result.put("score", rankService.myScore(name, memberId));
        result.put("rank", rankService.myRank(name, memberId));
        return result;
    }

    @GetMapping("/around/{memberId}")
    public Map<String, Object> around(
            @PathVariable String name,
            @PathVariable @NotBlank String memberId,
            @RequestParam(defaultValue = "3") @Positive int radius) {
        List<RankItem> players = rankService.around(name, memberId, radius);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaderboard", name);
        result.put("memberId", memberId);
        result.put("radius", radius);
        result.put("players", players);
        return result;
    }

    @DeleteMapping("/player/{memberId}")
    public Map<String, Object> removePlayer(
            @PathVariable String name,
            @PathVariable @NotBlank String memberId) {
        boolean removed = rankService.remove(name, memberId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaderboard", name);
        result.put("memberId", memberId);
        result.put("removed", removed);
        return result;
    }

    @GetMapping("/count")
    public Map<String, Object> count(@PathVariable String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaderboard", name);
        result.put("totalMembers", rankService.count(name));
        return result;
    }
}
