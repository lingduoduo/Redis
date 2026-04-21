package com.example.redismqdemo.controller;

import com.example.redismqdemo.model.DelayOrderRequest;
import com.example.redismqdemo.model.OrderMessageRequest;
import com.example.redismqdemo.service.DelayQueueService;
import com.example.redismqdemo.service.StreamConsumerService;
import com.example.redismqdemo.service.StreamProducerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageQueueControllerTest {

    @Mock
    private StreamProducerService streamProducerService;

    @Mock
    private StreamConsumerService streamConsumerService;

    @Mock
    private DelayQueueService delayQueueService;

    @InjectMocks
    private MessageQueueController messageQueueController;

    @Test
    void sendOrderReturnsStreamMetadata() {
        when(streamProducerService.sendOrder("order-1")).thenReturn("1700000000000-0");

        Map<String, Object> result = messageQueueController.sendOrder(new OrderMessageRequest("order-1"));

        assertThat(result)
                .containsEntry("message", "stream message sent")
                .containsEntry("stream", StreamProducerService.STREAM_KEY)
                .containsEntry("messageId", "1700000000000-0")
                .containsEntry("orderId", "order-1");
    }

    @Test
    void pushDelaySchedulesOrderClose() {
        Map<String, Object> result = messageQueueController.pushDelay(new DelayOrderRequest("order-2", 3000));

        verify(delayQueueService).push("order-2", 3000);
        assertThat(result)
                .containsEntry("message", "delay job scheduled")
                .containsEntry("queue", DelayQueueService.QUEUE_KEY)
                .containsEntry("orderId", "order-2")
                .containsEntry("delayMs", 3000L);
    }

    @Test
    void processedStreamDelegatesToConsumerService() {
        when(streamConsumerService.recentProcessedOrders(5)).thenReturn(List.of("order-1"));

        assertThat(messageQueueController.processedStream(5)).containsExactly("order-1");
    }

    @Test
    void streamInfoReturnsGroupConsumerAndSize() {
        when(streamProducerService.streamSize()).thenReturn(12L);

        Map<String, Object> result = messageQueueController.streamInfo();

        assertThat(result)
                .containsEntry("stream", StreamProducerService.STREAM_KEY)
                .containsEntry("size", 12L)
                .containsEntry("group", StreamConsumerService.GROUP)
                .containsEntry("consumer", StreamConsumerService.CONSUMER);
    }
}
