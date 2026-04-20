package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.model.RankItem;
import com.example.redisrankdemo.model.ScoreRequest;
import com.example.redisrankdemo.service.RankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankControllerTest {

    @Mock
    private RankService rankService;

    @InjectMocks
    private RankController rankController;

    private final String TODAY_KEY_PREFIX = "rank:article:daily:";

    @Test
    void addScoreReturnsUpdatedRankAndScore() {
        when(rankService.myScore("alice")).thenReturn(150.0);
        when(rankService.myRank("alice")).thenReturn(2L);

        Map<String, Object> result = rankController.addScore(new ScoreRequest("alice", 50.0));

        assertThat(result)
                .containsEntry("message", "score added")
                .containsEntry("memberId", "alice")
                .containsEntry("score", 150.0)
                .containsEntry("rank", 2L);
    }

    @Test
    void topDelegatesAndReturnsRankItems() {
        List<RankItem> items = List.of(
                new RankItem(1, "alice", 200.0),
                new RankItem(2, "bob", 150.0)
        );
        when(rankService.top(5)).thenReturn(items);

        List<RankItem> result = rankController.top(5);

        assertThat(result).isEqualTo(items);
    }

    @Test
    void myRankReturnsScoreAndRankForMember() {
        when(rankService.myScore("alice")).thenReturn(200.0);
        when(rankService.myRank("alice")).thenReturn(1L);

        Map<String, Object> result = rankController.myRank("alice");

        assertThat(result)
                .containsEntry("memberId", "alice")
                .containsEntry("score", 200.0)
                .containsEntry("rank", 1L);
    }
}
