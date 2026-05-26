package com.example.redistimelinedemo.controller;

import com.example.redistimelinedemo.model.TimelineMessage;
import com.example.redistimelinedemo.model.TimelinePage;
import com.example.redistimelinedemo.service.TimelineZSetService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TimelineControllerTest {

    private final TimelineZSetService timelineService = mock(TimelineZSetService.class);
    private final TimelineController controller = new TimelineController(timelineService);

    @Test
    void pageReturnsCursorBasedPage() {
        TimelinePage expected = new TimelinePage("u42", "timeline:u42", 999L, List.of());
        when(timelineService.page("u42", null, 20)).thenReturn(expected);

        assertThat(controller.page("u42", null, 20)).isEqualTo(expected);
    }

    @Test
    void rangeByTimeReturnsMessagesWithMetadata() {
        TimelineMessage msg = new TimelineMessage("m1", "a1", "hello", Instant.ofEpochMilli(2000L));
        when(timelineService.rangeByTime("u42", 1000L, 3000L)).thenReturn(List.of(msg));

        var result = controller.rangeByTime("u42", 1000L, 3000L);

        assertThat(result)
                .containsEntry("userId", "u42")
                .containsEntry("fromMs", 1000L)
                .containsEntry("toMs", 3000L)
                .containsEntry("count", 1);
    }

    @Test
    void clearDelegatesToService() {
        assertThat(controller.clear("u42"))
                .containsEntry("message", "timeline cleared")
                .containsEntry("userId", "u42");

        verify(timelineService).clear("u42");
    }

    @Test
    void sizeReturnsZsetCardinality() {
        when(timelineService.size("u42")).thenReturn(5L);
        when(timelineService.timelineKey("u42")).thenReturn("timeline:u42");

        assertThat(controller.size("u42"))
                .containsEntry("userId", "u42")
                .containsEntry("timelineKey", "timeline:u42")
                .containsEntry("size", 5L);
    }
}
