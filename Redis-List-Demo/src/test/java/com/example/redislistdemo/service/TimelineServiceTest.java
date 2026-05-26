package com.example.redislistdemo.service;

import com.example.redislistdemo.model.TimelineMessage;
import com.example.redislistdemo.model.TimelinePage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TimelineServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ListOperations<String, String> listOps = mock(ListOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final TimelineService service = new TimelineService(redisTemplate, objectMapper);

    @Test
    void publishUsesLeftPushAndTrimToKeepTimelineOrderedAndBounded() {
        when(redisTemplate.opsForList()).thenReturn(listOps);

        TimelineMessage message = service.publish("u42", "author-1", "hello", 50);

        assertThat(message.authorId()).isEqualTo("author-1");
        assertThat(message.content()).isEqualTo("hello");
        verify(listOps).leftPush(eq("timeline:u42"), anyString());
        verify(listOps).trim("timeline:u42", 0, 49);
    }

    @Test
    void pageUsesLrangeAndDeserializesMessages() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        TimelineMessage newest = new TimelineMessage("m2", "a2", "new", Instant.parse("2026-05-26T12:00:00Z"));
        TimelineMessage older = new TimelineMessage("m1", "a1", "old", Instant.parse("2026-05-26T11:00:00Z"));
        when(listOps.range("timeline:u42", 0, 1)).thenReturn(List.of(
                objectMapper.writeValueAsString(newest),
                objectMapper.writeValueAsString(older)
        ));

        TimelinePage page = service.page("u42", 0, 1);

        assertThat(page.userId()).isEqualTo("u42");
        assertThat(page.redisKey()).isEqualTo("timeline:u42");
        assertThat(page.messages()).containsExactly(newest, older);
    }

    @Test
    void fanoutWritesSameMessageToDistinctReceivers() {
        when(redisTemplate.opsForList()).thenReturn(listOps);

        List<TimelineMessage> messages = service.fanout(List.of("u1", "u2", "u1"), "author-1", "hello", 100);

        assertThat(messages).hasSize(1);
        verify(listOps).leftPush(eq("timeline:u1"), anyString());
        verify(listOps).leftPush(eq("timeline:u2"), anyString());
        verify(listOps).trim("timeline:u1", 0, 99);
        verify(listOps).trim("timeline:u2", 0, 99);
        verifyNoMoreInteractions(listOps);
    }

    @Test
    void sizeAndClearUseTimelineKey() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.size("timeline:u42")).thenReturn(7L);

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
    }
}
