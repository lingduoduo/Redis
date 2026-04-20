package com.example.redisrankdemo.service;

import com.example.redisrankdemo.model.RankItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @InjectMocks
    private RankService rankService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    @Test
    void setScoreCallsZAdd() {
        rankService.setScore("game:weekly", "alice", 1500.0);

        verify(zSetOps).add("game:weekly", "alice", 1500.0);
    }

    @Test
    void incrementScoreReturnsNewScore() {
        when(zSetOps.incrementScore("game:weekly", "alice", 50.0)).thenReturn(1550.0);

        double result = rankService.incrementScore("game:weekly", "alice", 50.0);

        assertThat(result).isEqualTo(1550.0);
    }

    @Test
    void incrementScoreReturnsZeroWhenRedisReturnsNull() {
        when(zSetOps.incrementScore("game:weekly", "alice", 10.0)).thenReturn(null);

        assertThat(rankService.incrementScore("game:weekly", "alice", 10.0)).isEqualTo(0.0);
    }

    @Test
    void topReturnsRankedListInOrder() {
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(mockTuple("alice", 100.0));
        tuples.add(mockTuple("bob", 80.0));
        when(zSetOps.reverseRangeWithScores("game:weekly", 0, 2)).thenReturn(tuples);

        List<RankItem> result = rankService.top("game:weekly", 3);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new RankItem(1, "alice", 100.0));
        assertThat(result.get(1)).isEqualTo(new RankItem(2, "bob", 80.0));
    }

    @Test
    void topReturnsEmptyListWhenNoMembers() {
        when(zSetOps.reverseRangeWithScores("game:weekly", 0, 9)).thenReturn(null);

        assertThat(rankService.top("game:weekly", 10)).isEmpty();
    }

    @Test
    void myRankReturnsOneIndexedRank() {
        when(zSetOps.reverseRank("game:weekly", "alice")).thenReturn(0L);

        assertThat(rankService.myRank("game:weekly", "alice")).isEqualTo(1L);
    }

    @Test
    void myRankReturnsNegativeOneWhenMemberAbsent() {
        when(zSetOps.reverseRank("game:weekly", "ghost")).thenReturn(null);

        assertThat(rankService.myRank("game:weekly", "ghost")).isEqualTo(-1L);
    }

    @Test
    void myScoreReturnsMemberScore() {
        when(zSetOps.score("game:weekly", "alice")).thenReturn(1500.0);

        assertThat(rankService.myScore("game:weekly", "alice")).isEqualTo(1500.0);
    }

    @Test
    void myScoreReturnsZeroWhenMemberAbsent() {
        when(zSetOps.score("game:weekly", "ghost")).thenReturn(null);

        assertThat(rankService.myScore("game:weekly", "ghost")).isEqualTo(0.0);
    }

    @Test
    void aroundReturnsWindowCenteredOnMember() {
        when(zSetOps.reverseRank("game:weekly", "carol")).thenReturn(2L); // rank 3
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(mockTuple("alice", 200.0));
        tuples.add(mockTuple("bob", 150.0));
        tuples.add(mockTuple("carol", 100.0));
        tuples.add(mockTuple("dave", 80.0));
        when(zSetOps.reverseRangeWithScores("game:weekly", 0L, 4L)).thenReturn(tuples);

        List<RankItem> result = rankService.around("game:weekly", "carol", 2);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).memberId()).isEqualTo("alice");
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(2).memberId()).isEqualTo("carol");
        assertThat(result.get(2).rank()).isEqualTo(3);
    }

    @Test
    void aroundReturnsEmptyWhenMemberAbsent() {
        when(zSetOps.reverseRank("game:weekly", "ghost")).thenReturn(null);

        assertThat(rankService.around("game:weekly", "ghost", 3)).isEmpty();
    }

    @Test
    void removeReturnsTrueWhenMemberDeleted() {
        when(zSetOps.remove("game:weekly", "alice")).thenReturn(1L);

        assertThat(rankService.remove("game:weekly", "alice")).isTrue();
    }

    @Test
    void removeReturnsFalseWhenMemberAbsent() {
        when(zSetOps.remove("game:weekly", "ghost")).thenReturn(0L);

        assertThat(rankService.remove("game:weekly", "ghost")).isFalse();
    }

    @Test
    void countReturnsTotalMembers() {
        when(zSetOps.size("game:weekly")).thenReturn(42L);

        assertThat(rankService.count("game:weekly")).isEqualTo(42L);
    }

    @Test
    void countReturnsZeroWhenKeyMissing() {
        when(zSetOps.size("game:weekly")).thenReturn(null);

        assertThat(rankService.count("game:weekly")).isEqualTo(0L);
    }

    private TypedTuple<String> mockTuple(String value, double score) {
        TypedTuple<String> tuple = mock(TypedTuple.class);
        when(tuple.getValue()).thenReturn(value);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}
