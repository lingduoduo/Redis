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

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotNewsServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOps;
    @InjectMocks private HotNewsService service;

    private static final LocalDate DATE = LocalDate.of(2019, 9, 26);

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    @Test
    void clickUsesZincrbyOnDateKey() {
        when(zSetOps.incrementScore("hotNews:20190926", "n6001", 1L)).thenReturn(121.0);

        double count = service.click("n6001", DATE, 1);

        assertThat(count).isEqualTo(121.0);
        verify(zSetOps).incrementScore("hotNews:20190926", "n6001", 1L);
    }

    @Test
    void clickReturnsZeroWhenRedisReturnsNull() {
        when(zSetOps.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(null);

        assertThat(service.click("n6001", DATE, 1)).isEqualTo(0.0);
    }

    @Test
    void topUsesZrevrangeWithScores() {
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(tuple("n6003", 200.0));
        tuples.add(tuple("n6006", 180.0));
        tuples.add(tuple("n6004", 150.0));
        when(zSetOps.reverseRangeWithScores("hotNews:20190926", 0, 14)).thenReturn(tuples);

        List<RankItem> ranking = service.top(15, DATE);

        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0)).isEqualTo(new RankItem(1, "n6003", 200.0));
        assertThat(ranking.get(1)).isEqualTo(new RankItem(2, "n6006", 180.0));
        assertThat(ranking.get(2)).isEqualTo(new RankItem(3, "n6004", 150.0));
        verify(zSetOps).reverseRangeWithScores("hotNews:20190926", 0, 14);
    }

    @Test
    void topReturnsEmptyWhenKeyMissing() {
        when(zSetOps.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(null);

        assertThat(service.top(15, DATE)).isEmpty();
    }

    @Test
    void rankUsesZrevrank() {
        when(zSetOps.reverseRank("hotNews:20190926", "n6001")).thenReturn(0L); // 0-indexed → rank 1

        assertThat(service.rank("n6001", DATE)).isEqualTo(1L);
        verify(zSetOps).reverseRank("hotNews:20190926", "n6001");
    }

    @Test
    void rankReturnsNegativeOneWhenNewsAbsent() {
        when(zSetOps.reverseRank("hotNews:20190926", "n9999")).thenReturn(null);

        assertThat(service.rank("n9999", DATE)).isEqualTo(-1L);
    }

    @Test
    void clickCountUsesZscore() {
        when(zSetOps.score("hotNews:20190926", "n6001")).thenReturn(120.0);

        assertThat(service.clickCount("n6001", DATE)).isEqualTo(120.0);
        verify(zSetOps).score("hotNews:20190926", "n6001");
    }

    @Test
    void clickCountReturnsZeroWhenNewsAbsent() {
        when(zSetOps.score("hotNews:20190926", "n9999")).thenReturn(null);

        assertThat(service.clickCount("n9999", DATE)).isEqualTo(0.0);
    }

    @Test
    void mergeUsesZunionstoreAndReturnsTop() {
        // ZUNIONSTORE hotNews:merged:3d 3 hotNews:{d0} hotNews:{d-1} hotNews:{d-2}
        when(zSetOps.unionAndStore(anyString(), anyCollection(), eq("hotNews:merged:3d"))).thenReturn(9L);
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(tuple("n6001", 1220.0));
        tuples.add(tuple("n6002", 1060.0));
        tuples.add(tuple("n6003", 780.0));
        when(zSetOps.reverseRangeWithScores("hotNews:merged:3d", 0, 14)).thenReturn(tuples);

        List<RankItem> ranking = service.merge(3, 15);

        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0)).isEqualTo(new RankItem(1, "n6001", 1220.0));
        assertThat(ranking.get(1)).isEqualTo(new RankItem(2, "n6002", 1060.0));
        verify(zSetOps).unionAndStore(anyString(), anyCollection(), eq("hotNews:merged:3d"));
    }

    @Test
    void hotNewsKeyFormatsAsYyyyMMdd() {
        assertThat(service.hotNewsKey(DATE)).isEqualTo("hotNews:20190926");
        assertThat(service.hotNewsKey("20190926")).isEqualTo("hotNews:20190926");
    }

    @Test
    void mergeRejectsDaysLessThanOne() {
        assertThatThrownBy(() -> service.merge(0, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("days must be >= 1");
    }

    @Test
    void clickRejectsBlankNewsId() {
        assertThatThrownBy(() -> service.click(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("newsId cannot be blank");
        assertThatThrownBy(() -> service.click(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("newsId cannot be blank");
    }

    private TypedTuple<String> tuple(String value, double score) {
        TypedTuple<String> t = mock(TypedTuple.class);
        when(t.getValue()).thenReturn(value);
        when(t.getScore()).thenReturn(score);
        return t;
    }
}
