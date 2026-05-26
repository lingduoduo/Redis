package com.example.redisglobaliddemo.service;

import com.example.redisglobaliddemo.model.IdSegment;
import com.example.redisglobaliddemo.model.NextIdResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GlobalIdServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final GlobalIdService globalIdService = new GlobalIdService(redisTemplate);

    @Test
    void reserveSegmentUsesRedisIncrByAndReturnsAllocatedRange() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("global:id:user", 1000)).thenReturn(3000L);

        IdSegment segment = globalIdService.reserveSegment("User", 1000);

        assertThat(segment).isEqualTo(new IdSegment("user", "global:id:user", 2001, 3000, 1000));
        verify(valueOps).increment("global:id:user", 1000);
    }

    @Test
    void nextIdUsesLocalSegmentUntilItIsExhausted() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("global:id:user", 3))
                .thenReturn(3L)
                .thenReturn(6L);

        assertThat(globalIdService.nextId("user", 3))
                .isEqualTo(new NextIdResponse("user", 1, 1, 3, 2));
        assertThat(globalIdService.nextId("user", 3).id()).isEqualTo(2);
        assertThat(globalIdService.nextId("user", 3).id()).isEqualTo(3);

        NextIdResponse nextSegmentFirstId = globalIdService.nextId("user", 3);

        assertThat(nextSegmentFirstId)
                .isEqualTo(new NextIdResponse("user", 4, 4, 6, 2));
        verify(valueOps, times(2)).increment("global:id:user", 3);
    }

    @Test
    void differentBizTagsUseDifferentRedisKeysAndSegments() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("global:id:user", 2)).thenReturn(2L);
        when(valueOps.increment("global:id:order", 2)).thenReturn(1002L);

        assertThat(globalIdService.nextId("user", 2).id()).isEqualTo(1);
        assertThat(globalIdService.nextId("order", 2).id()).isEqualTo(1001);

        verify(valueOps).increment("global:id:user", 2);
        verify(valueOps).increment("global:id:order", 2);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> globalIdService.reserveSegment("", 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bizTag cannot be blank");
        assertThatThrownBy(() -> globalIdService.reserveSegment("123user", 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bizTag must start with a letter and contain only letters, numbers, ':', '_' or '-'");
        assertThatThrownBy(() -> globalIdService.reserveSegment("user", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("step must be between 1 and 1000000");
    }
}
