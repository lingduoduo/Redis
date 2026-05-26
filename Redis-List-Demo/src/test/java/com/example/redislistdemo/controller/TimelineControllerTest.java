package com.example.redislistdemo.controller;

import com.example.redislistdemo.model.FanoutMessageRequest;
import com.example.redislistdemo.model.PublishMessageRequest;
import com.example.redislistdemo.model.TimelineMessage;
import com.example.redislistdemo.model.TimelinePage;
import com.example.redislistdemo.service.TimelineService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TimelineControllerTest {

    private final TimelineService timelineService = mock(TimelineService.class);
    private final TimelineController controller = new TimelineController(timelineService);

    @Test
    void publishReturnsTimelineKeyAndMessage() {
        TimelineMessage message = new TimelineMessage("m1", "a1", "hello", Instant.parse("2026-05-26T12:00:00Z"));
        when(timelineService.publish("u42", "a1", "hello", 100)).thenReturn(message);
        when(timelineService.timelineKey("u42")).thenReturn("timeline:u42");

        assertThat(controller.publish("u42", new PublishMessageRequest("a1", "hello"), 100))
                .containsEntry("message", "timeline message published")
                .containsEntry("userId", "u42")
                .containsEntry("timelineKey", "timeline:u42")
                .containsEntry("item", message);
    }

    @Test
    void fanoutReturnsReceiverCountAndSharedMessage() {
        TimelineMessage message = new TimelineMessage("m1", "a1", "hello", Instant.parse("2026-05-26T12:00:00Z"));
        when(timelineService.fanout(List.of("u1", "u2"), "a1", "hello", 100)).thenReturn(List.of(message));

        assertThat(controller.fanout(new FanoutMessageRequest("a1", "hello", List.of("u1", "u2")), 100))
                .containsEntry("message", "timeline message fanned out")
                .containsEntry("receivers", 2)
                .containsEntry("item", message);
    }

    @Test
    void pageDelegatesToService() {
        TimelinePage page = new TimelinePage("u42", "timeline:u42", 0, 19, List.of());
        when(timelineService.page("u42", 0, 19)).thenReturn(page);

        assertThat(controller.page("u42", 0, 19)).isEqualTo(page);
    }

    @Test
    void sizeReturnsTimelineLength() {
        when(timelineService.timelineKey("u42")).thenReturn("timeline:u42");
        when(timelineService.size("u42")).thenReturn(5L);

        assertThat(controller.size("u42"))
                .containsEntry("userId", "u42")
                .containsEntry("timelineKey", "timeline:u42")
                .containsEntry("size", 5L);
    }

    @Test
    void clearDeletesTimeline() {
        assertThat(controller.clear("u42"))
                .containsEntry("message", "timeline cleared")
                .containsEntry("userId", "u42");

        verify(timelineService).clear("u42");
    }
}
