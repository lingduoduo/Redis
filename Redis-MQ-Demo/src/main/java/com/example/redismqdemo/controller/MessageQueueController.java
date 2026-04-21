package com.example.redismqdemo.controller;

import com.example.redismqdemo.model.DelayOrderRequest;
import com.example.redismqdemo.model.OrderMessageRequest;
import com.example.redismqdemo.service.DelayQueueService;
import com.example.redismqdemo.service.StreamConsumerService;
import com.example.redismqdemo.service.StreamProducerService;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

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
    private final StringRedisTemplate redisTemplate;

    public MessageQueueController(StreamProducerService streamProducerService,
                                  StreamConsumerService streamConsumerService,
                                  DelayQueueService delayQueueService,
                                  StringRedisTemplate redisTemplate) {
        this.streamProducerService = streamProducerService;
        this.streamConsumerService = streamConsumerService;
        this.delayQueueService = delayQueueService;
        this.redisTemplate = redisTemplate;
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

    @GetMapping("/stream/info")
    public Map<String, Object> streamInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        Long size = redisTemplate.opsForStream().size(StreamProducerService.STREAM_KEY);
        result.put("stream", StreamProducerService.STREAM_KEY);
        result.put("size", size == null ? 0 : size);
        result.put("group", StreamConsumerService.GROUP);
        result.put("consumer", StreamConsumerService.CONSUMER);
        return result;
    }
}
