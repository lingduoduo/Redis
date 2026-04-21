package com.example.redismqdemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamConsumerServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @InjectMocks
    private StreamConsumerService streamConsumerService;

    @Test
    void recentProcessedOrdersReturnsRedisListRange() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.range(StreamConsumerService.PROCESSED_ORDERS_KEY, 0, 2))
                .thenReturn(List.of("order-3", "order-2", "order-1"));

        assertThat(streamConsumerService.recentProcessedOrders(3))
                .containsExactly("order-3", "order-2", "order-1");
    }

    @Test
    void recentProcessedOrdersReturnsEmptyForNonPositiveLimit() {
        assertThat(streamConsumerService.recentProcessedOrders(0)).isEmpty();
    }
}
