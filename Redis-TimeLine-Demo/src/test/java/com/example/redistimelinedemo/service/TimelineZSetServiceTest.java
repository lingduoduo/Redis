package com.example.redistimelinedemo.service;

import com.example.redistimelinedemo.model.TimelineMessage;
import com.example.redistimelinedemo.model.TimelinePage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TimelineZSetServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final TimelineZSetService service = new TimelineZSetService(redisTemplate, objectMapper);

    @Test
    void publishUsesZaddAndTrimToKeepTimelineOrderedAndBounded() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        TimelineMessage message = service.publish("u42", "author-1", "hello", 50);

        assertThat(message.authorId()).isEqualTo("author-1");
        assertThat(message.content()).isEqualTo("hello");
        verify(zSetOps).add(eq("timeline:u42"), anyString(), anyDouble());
        // trim keeps newest 50: ZREMRANGEBYRANK key 0 -51
        verify(zSetOps).removeRange("timeline:u42", 0, -51);
    }

    @Test
    void pageUsesZrevrangebyscoreWithCursorAndComputesNextCursor() throws Exception {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        TimelineMessage newer = new TimelineMessage("m2", "a2", "new", Instant.ofEpochMilli(2000L));
        TimelineMessage older = new TimelineMessage("m1", "a1", "old", Instant.ofEpochMilli(1000L));

        LinkedHashSet<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(new DefaultTypedTuple<>(objectMapper.writeValueAsString(newer), 2000.0));
        tuples.add(new DefaultTypedTuple<>(objectMapper.writeValueAsString(older), 1000.0));

        when(zSetOps.reverseRangeByScoreWithScores("timeline:u42", Double.NEGATIVE_INFINITY, Double.MAX_VALUE, 0, 2))
                .thenReturn(tuples);

        TimelinePage page = service.page("u42", null, 2);

        assertThat(page.userId()).isEqualTo("u42");
        assertThat(page.redisKey()).isEqualTo("timeline:u42");
        assertThat(page.messages()).containsExactly(newer, older);
        // pageSize == results.size() → nextCursor = lastScore - 1
        assertThat(page.nextCursor()).isEqualTo(999L);
    }

    @Test
    void pageReturnsNullNextCursorWhenResultsSmallerThanPageSize() throws Exception {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        TimelineMessage msg = new TimelineMessage("m1", "a1", "only", Instant.ofEpochMilli(1000L));

        LinkedHashSet<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(new DefaultTypedTuple<>(objectMapper.writeValueAsString(msg), 1000.0));

        when(zSetOps.reverseRangeByScoreWithScores("timeline:u42", Double.NEGATIVE_INFINITY, 1999.0, 0, 5))
                .thenReturn(tuples);

        TimelinePage page = service.page("u42", 1999L, 5);

        assertThat(page.messages()).containsExactly(msg);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void rangeByTimeUsesZrevrangebyscore() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRangeByScore("timeline:u42", 1000.0, 5000.0))
                .thenReturn(Set.of());

        List<TimelineMessage> messages = service.rangeByTime("u42", 1000L, 5000L);

        assertThat(messages).isEmpty();
        verify(zSetOps).reverseRangeByScore("timeline:u42", 1000.0, 5000.0);
    }

    @Test
    void fanoutWritesSameMessageToDistinctReceivers() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        List<TimelineMessage> messages = service.fanout(List.of("u1", "u2", "u1"), "author-1", "hello", 100);

        assertThat(messages).hasSize(1);
        verify(zSetOps).add(eq("timeline:u1"), anyString(), anyDouble());
        verify(zSetOps).add(eq("timeline:u2"), anyString(), anyDouble());
        verify(zSetOps).removeRange("timeline:u1", 0, -101);
        verify(zSetOps).removeRange("timeline:u2", 0, -101);
        verifyNoMoreInteractions(zSetOps);
    }

    @Test
    void sizeAndClearUseTimelineKey() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.size("timeline:u42")).thenReturn(7L);

        assertThat(service.size("u42")).isEqualTo(7L);
        service.clear("u42");

        verify(redisTemplate).delete("timeline:u42");
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> service.publish("", "author", "hello", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
        assertThatThrownBy(() -> service.publish("u42", "", "hello", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authorId cannot be blank");
        assertThatThrownBy(() -> service.publish("u42", "author", "", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content cannot be blank");
        assertThatThrownBy(() -> service.publish("u42", "author", "hello", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxLength must be positive");
        assertThatThrownBy(() -> service.page("u42", null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageSize must be positive");
        assertThatThrownBy(() -> service.rangeByTime("u42", 5000L, 1000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromEpochMillis must be <= toEpochMillis");
    }
}
