package com.example.redismqdemo.controller;

import com.example.redismqdemo.model.DelayOrderRequest;
import com.example.redismqdemo.model.ListPushRequest;
import com.example.redismqdemo.model.OrderMessageRequest;
import com.example.redismqdemo.service.DelayQueueService;
import com.example.redismqdemo.service.ListQueueService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private ListQueueService listQueueService;

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
    void listPushDelegatesToServiceAndReturnsQueueSize() {
        when(listQueueService.push("task", "hello")).thenReturn(3L);
        when(listQueueService.queueKey("task")).thenReturn("queue:task");

        Map<String, Object> result = messageQueueController.listPush("task", new ListPushRequest("hello"));

        assertThat(result)
                .containsEntry("message", "message pushed")
                .containsEntry("queueKey", "queue:task")
                .containsEntry("payload", "hello")
                .containsEntry("queueSize", 3L);
    }

    @Test
    void listPopFifoUsesBlpopAndReturnsPayload() {
        when(listQueueService.queueKey("task")).thenReturn("queue:task");
        when(listQueueService.popFifo(eq("task"), any())).thenReturn("msg-1");

        Map<String, Object> result = messageQueueController.listPop("task", "fifo", 5);

        assertThat(result)
                .containsEntry("mode", "fifo")
                .containsEntry("received", true)
                .containsEntry("payload", "msg-1");
    }

    @Test
    void listPopStackUsesBrpopAndReturnsPayload() {
        when(listQueueService.queueKey("task")).thenReturn("queue:task");
        when(listQueueService.popStack(eq("task"), any())).thenReturn("msg-last");

        Map<String, Object> result = messageQueueController.listPop("task", "stack", 5);

        assertThat(result)
                .containsEntry("mode", "stack")
                .containsEntry("received", true)
                .containsEntry("payload", "msg-last");
    }

    @Test
    void listPopTimeoutReturnsReceivedFalse() {
        when(listQueueService.queueKey("empty")).thenReturn("queue:empty");
        when(listQueueService.popFifo(eq("empty"), any())).thenReturn(null);

        Map<String, Object> result = messageQueueController.listPop("empty", "fifo", 1);

        assertThat(result)
                .containsEntry("received", false)
                .containsEntry("payload", null);
    }

    @Test
    void listSizeReturnsLlen() {
        when(listQueueService.queueKey("task")).thenReturn("queue:task");
        when(listQueueService.size("task")).thenReturn(5L);

        Map<String, Object> result = messageQueueController.listSize("task");

        assertThat(result)
                .containsEntry("queueKey", "queue:task")
                .containsEntry("size", 5L);
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
