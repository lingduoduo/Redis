package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.model.RankItem;
import com.example.redisrankdemo.model.ScoreRequest;
import com.example.redisrankdemo.service.RankService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    @Mock
    private RankService rankService;

    @InjectMocks
    private LeaderboardController controller;

    @Test
    void setScoreStoresScoreAndReturnsRank() {
        when(rankService.myScore("game:weekly", "alice")).thenReturn(1500.0);
        when(rankService.myRank("game:weekly", "alice")).thenReturn(1L);

        Map<String, Object> result = controller.setScore("game:weekly", new ScoreRequest("alice", 1500.0));

        verify(rankService).setScore("game:weekly", "alice", 1500.0);
        assertThat(result)
                .containsEntry("leaderboard", "game:weekly")
                .containsEntry("memberId", "alice")
                .containsEntry("score", 1500.0)
                .containsEntry("rank", 1L);
    }

    @Test
    void incrementScoreReturnsUpdatedScoreAndRank() {
        when(rankService.incrementScore("game:weekly", "alice", 50.0)).thenReturn(1550.0);
        when(rankService.myRank("game:weekly", "alice")).thenReturn(1L);

        Map<String, Object> result = controller.incrementScore("game:weekly", new ScoreRequest("alice", 50.0));

        assertThat(result)
                .containsEntry("leaderboard", "game:weekly")
                .containsEntry("memberId", "alice")
                .containsEntry("score", 1550.0)
                .containsEntry("rank", 1L);
    }

    @Test
    void topReturnsPaginatedLeaderboard() {
        List<RankItem> items = List.of(
                new RankItem(1, "alice", 1500.0),
                new RankItem(2, "bob", 1200.0)
        );
        when(rankService.top("game:weekly", 10)).thenReturn(items);

        Map<String, Object> result = controller.top("game:weekly", 10);

        assertThat(result)
                .containsEntry("leaderboard", "game:weekly")
                .containsEntry("count", 2);
        assertThat((List<?>) result.get("players")).hasSize(2);
    }

    @Test
    void aroundReturnsSurroundingPlayers() {
        List<RankItem> window = List.of(
                new RankItem(1, "alice", 200.0),
                new RankItem(2, "bob", 150.0),
                new RankItem(3, "carol", 100.0)
        );
        when(rankService.around("game:weekly", "bob", 1)).thenReturn(window);

        Map<String, Object> result = controller.around("game:weekly", "bob", 1);

        assertThat(result)
                .containsEntry("leaderboard", "game:weekly")
                .containsEntry("memberId", "bob")
                .containsEntry("radius", 1);
        assertThat((List<?>) result.get("players")).hasSize(3);
    }

    @Test
    void removePlayerReturnsRemovedTrue() {
        when(rankService.remove("game:weekly", "alice")).thenReturn(true);

        Map<String, Object> result = controller.removePlayer("game:weekly", "alice");

        assertThat(result)
                .containsEntry("leaderboard", "game:weekly")
                .containsEntry("memberId", "alice")
                .containsEntry("removed", true);
    }

    @Test
    void countReturnsTotalMembers() {
        when(rankService.count("game:weekly")).thenReturn(42L);

        Map<String, Object> result = controller.count("game:weekly");

        assertThat(result)
                .containsEntry("leaderboard", "game:weekly")
                .containsEntry("totalMembers", 42L);
    }
}
