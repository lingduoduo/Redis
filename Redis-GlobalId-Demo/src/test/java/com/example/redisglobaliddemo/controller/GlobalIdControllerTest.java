package com.example.redisglobaliddemo.controller;

import com.example.redisglobaliddemo.model.IdSegment;
import com.example.redisglobaliddemo.model.NextIdResponse;
import com.example.redisglobaliddemo.service.GlobalIdService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalIdControllerTest {

    private final GlobalIdService globalIdService = mock(GlobalIdService.class);
    private final GlobalIdController controller = new GlobalIdController(globalIdService);

    @Test
    void reserveSegmentDelegatesToService() {
        IdSegment segment = new IdSegment("user", "global:id:user", 1, 1000, 1000);
        when(globalIdService.reserveSegment("user", 1000)).thenReturn(segment);

        assertThat(controller.reserveSegment("user", 1000)).isEqualTo(segment);
    }

    @Test
    void nextIdDelegatesToService() {
        NextIdResponse response = new NextIdResponse("user", 1, 1, 1000, 999);
        when(globalIdService.nextId("user", 1000)).thenReturn(response);

        assertThat(controller.nextId("user", 1000)).isEqualTo(response);
    }

    @Test
    void redisKeyDelegatesToService() {
        when(globalIdService.redisKey("user")).thenReturn("global:id:user");

        assertThat(controller.redisKey("user")).isEqualTo("global:id:user");
    }
}
