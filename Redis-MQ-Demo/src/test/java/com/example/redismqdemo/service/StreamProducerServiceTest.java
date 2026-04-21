package com.example.redismqdemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamProducerServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Test
    void sendOrderAddsOrderMessageToStreamAndReturnsRecordId() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(StreamProducerService.STREAM_KEY, Map.of(
                StreamProducerService.FIELD_ORDER_ID, "order-1",
                StreamProducerService.FIELD_TIME, "123"
        ))).thenReturn(RecordId.of("1700000000000-0"));

        StreamProducerService producerService = new StreamProducerService(redisTemplate) {
            @Override
            Map<String, String> orderMessage(String orderId) {
                return Map.of(
                        FIELD_ORDER_ID, orderId,
                        FIELD_TIME, "123"
                );
            }
        };

        String result = producerService.sendOrder("order-1");

        assertThat(result).isEqualTo("1700000000000-0");
    }

    @Test
    void orderMessageIncludesOrderIdAndCurrentTime() {
        StreamProducerService producerService = new StreamProducerService(redisTemplate);

        Map<String, String> message = producerService.orderMessage("order-2");

        assertThat(message)
                .containsEntry(StreamProducerService.FIELD_ORDER_ID, "order-2")
                .containsKey(StreamProducerService.FIELD_TIME);
        assertThat(message.get(StreamProducerService.FIELD_TIME)).containsOnlyDigits();
    }

    @Test
    void sendOrderUsesStableStreamKeyAndPayloadFields() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        StreamProducerService producerService = new StreamProducerService(redisTemplate);

        producerService.sendOrder("order-3");

        verify(streamOps).add(eq(StreamProducerService.STREAM_KEY), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry(StreamProducerService.FIELD_ORDER_ID, "order-3")
                .containsKey(StreamProducerService.FIELD_TIME);
    }
}
