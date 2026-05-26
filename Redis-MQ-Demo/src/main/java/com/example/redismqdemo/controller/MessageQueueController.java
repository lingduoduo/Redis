package com.example.redismqdemo.controller;

import com.example.redismqdemo.model.DelayOrderRequest;
import com.example.redismqdemo.model.ListPushRequest;
import com.example.redismqdemo.model.OrderMessageRequest;
import com.example.redismqdemo.service.DelayQueueService;
import com.example.redismqdemo.service.ListQueueService;
import com.example.redismqdemo.service.StreamConsumerService;
import com.example.redismqdemo.service.StreamProducerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/mq")
public class MessageQueueController {

    private final StreamProducerService streamProducerService;
    private final StreamConsumerService streamConsumerService;
    private final DelayQueueService delayQueueService;
    private final ListQueueService listQueueService;

    public MessageQueueController(StreamProducerService streamProducerService,
                                  StreamConsumerService streamConsumerService,
                                  DelayQueueService delayQueueService,
                                  ListQueueService listQueueService) {
        this.streamProducerService = streamProducerService;
        this.streamConsumerService = streamConsumerService;
        this.delayQueueService = delayQueueService;
        this.listQueueService = listQueueService;
    }

    @PostMapping("/stream/order")
    public Map<String, Object> sendOrder(@Valid @RequestBody OrderMessageRequest request) {
        String messageId = streamProducerService.sendOrder(request.orderId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "stream message sent");
        result.put("stream", StreamProducerService.STREAM_KEY);
        result.put("messageId", messageId);
        result.put("orderId", request.orderId());
        return result;
    }

    @PostMapping("/delay/order")
    public Map<String, Object> pushDelay(@Valid @RequestBody DelayOrderRequest request) {
        delayQueueService.push(request.orderId(), request.delayMs());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "delay job scheduled");
        result.put("queue", DelayQueueService.QUEUE_KEY);
        result.put("orderId", request.orderId());
        result.put("delayMs", request.delayMs());
        return result;
    }

    @GetMapping("/delay/peek")
    public Set<String> peekDelay(@RequestParam(defaultValue = "10") int n) {
        return delayQueueService.peekTop(n);
    }

    @GetMapping("/delay/closed")
    public List<String> closedDelay(@RequestParam(defaultValue = "10") int n) {
        return delayQueueService.recentClosedOrders(n);
    }

    @GetMapping("/stream/processed")
    public List<String> processedStream(@RequestParam(defaultValue = "10") int n) {
        return streamConsumerService.recentProcessedOrders(n);
    }

    /**
     * RPUSH — push a message onto the tail of a named list queue.
     */
    @PostMapping("/list/{queueName}/push")
    public Map<String, Object> listPush(
            @PathVariable String queueName,
            @Valid @RequestBody ListPushRequest request) {
        long size = listQueueService.push(queueName, request.message());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "message pushed");
        result.put("queueKey", listQueueService.queueKey(queueName));
        result.put("payload", request.message());
        result.put("queueSize", size);
        return result;
    }

    /**
     * BLPOP (mode=fifo, default) or BRPOP (mode=stack) — blocking pop.
     * Blocks up to timeoutSecs waiting for a message; returns received=false on timeout.
     */
    @PostMapping("/list/{queueName}/pop")
    public Map<String, Object> listPop(
            @PathVariable String queueName,
            @RequestParam(defaultValue = "fifo") String mode,
            @RequestParam(defaultValue = "5") long timeoutSecs) {
        Duration timeout = Duration.ofSeconds(timeoutSecs);
        String payload = "stack".equalsIgnoreCase(mode)
                ? listQueueService.popStack(queueName, timeout)
                : listQueueService.popFifo(queueName, timeout);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queueKey", listQueueService.queueKey(queueName));
        result.put("mode", mode);
        result.put("received", payload != null);
        result.put("payload", payload);
        return result;
    }

    /**
     * LLEN — current number of messages waiting in the queue.
     */
    @GetMapping("/list/{queueName}/size")
    public Map<String, Object> listSize(@PathVariable String queueName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queueKey", listQueueService.queueKey(queueName));
        result.put("size", listQueueService.size(queueName));
        return result;
    }

    @GetMapping("/stream/info")
    public Map<String, Object> streamInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stream", StreamProducerService.STREAM_KEY);
        result.put("size", streamProducerService.streamSize());
        result.put("group", StreamConsumerService.GROUP);
        result.put("consumer", StreamConsumerService.CONSUMER);
        return result;
    }
}
