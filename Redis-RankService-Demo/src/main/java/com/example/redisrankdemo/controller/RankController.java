package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.model.RankItem;
import com.example.redisrankdemo.model.ScoreRequest;
import com.example.redisrankdemo.service.RankService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rank")
public class RankController {

    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }

    @PostMapping("/score")
    public Map<String, Object> addScore(@Valid @RequestBody ScoreRequest request) {
        rankService.addScore(request.memberId(), request.score());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "score added");
        result.put("memberId", request.memberId());
        result.put("score", rankService.myScore(request.memberId()));
        result.put("rank", rankService.myRank(request.memberId()));
        return result;
    }

    @GetMapping("/top")
    public List<RankItem> top(@RequestParam(defaultValue = "10") int n) {
        return rankService.top(n);
    }

    @GetMapping("/me/{memberId}")
    public Map<String, Object> myRank(@PathVariable @NotBlank String memberId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memberId", memberId);
        result.put("score", rankService.myScore(memberId));
        result.put("rank", rankService.myRank(memberId));
        return result;
    }
}
