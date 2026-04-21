package com.example.redismqdemo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelayQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private ListOperations<String, String> listOps;

    private DelayQueueService delayQueueService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        delayQueueService = new TestDelayQueueService(redisTemplate, 1000L);
    }

    @Test
    void pushAddsOrderWithFutureTriggerScore() {
        delayQueueService.push("order-1", 5000);

        verify(zSetOps).add(DelayQueueService.QUEUE_KEY, "order-1", 6000.0);
    }

    @Test
    void pushWithoutDelayUsesDefaultThirtyMinuteTimeout() {
        delayQueueService.push("order-1");

        verify(zSetOps).add(
                DelayQueueService.QUEUE_KEY,
                "order-1",
                1000.0 + DelayQueueService.DEFAULT_ORDER_CLOSE_DELAY.toMillis()
        );
    }

    @Test
    void scanRemovesDueOrdersAndRecordsClosedOnes() {
        Set<String> dueOrders = new LinkedHashSet<>(List.of("order-1", "order-2"));
        when(zSetOps.rangeByScore(DelayQueueService.QUEUE_KEY, 0, 1000L, 0, 50))
                .thenReturn(dueOrders);
        when(zSetOps.remove(DelayQueueService.QUEUE_KEY, "order-1")).thenReturn(1L);
        when(zSetOps.remove(DelayQueueService.QUEUE_KEY, "order-2")).thenReturn(0L);
        when(redisTemplate.opsForList()).thenReturn(listOps);

        delayQueueService.scan();

        verify(listOps).leftPush(DelayQueueService.CLOSED_ORDERS_KEY, "order-1");
        verify(listOps).trim(DelayQueueService.CLOSED_ORDERS_KEY, 0, 99);
        verify(listOps, never()).leftPush(DelayQueueService.CLOSED_ORDERS_KEY, "order-2");
    }

    @Test
    void peekTopReturnsEmptyForNonPositiveLimit() {
        assertThat(delayQueueService.peekTop(0)).isEmpty();

        verifyNoInteractions(zSetOps);
    }

    @Test
    void recentClosedOrdersReturnsEmptyWhenRedisReturnsNull() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.range(DelayQueueService.CLOSED_ORDERS_KEY, 0, 4)).thenReturn(null);

        assertThat(delayQueueService.recentClosedOrders(5)).isEmpty();
    }

    private static class TestDelayQueueService extends DelayQueueService {

        private final long nowMillis;

        private TestDelayQueueService(StringRedisTemplate redisTemplate, long nowMillis) {
            super(redisTemplate);
            this.nowMillis = nowMillis;
        }

        @Override
        long nowMillis() {
            return nowMillis;
        }
    }
}
