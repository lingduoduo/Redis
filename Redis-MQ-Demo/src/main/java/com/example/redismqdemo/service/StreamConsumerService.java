package com.example.redismqdemo.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class StreamConsumerService {

    private static final Logger log = LoggerFactory.getLogger(StreamConsumerService.class);

    public static final String GROUP = "order-group";
    public static final String CONSUMER = "consumer-a";
    public static final String PROCESSED_ORDERS_KEY = "stream:order:processed";

    private final StringRedisTemplate redisTemplate;

    public StreamConsumerService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void initGroup() {
        try {
            RecordId bootstrapId = null;
            if (Boolean.FALSE.equals(redisTemplate.hasKey(StreamProducerService.STREAM_KEY))) {
                bootstrapId = redisTemplate.opsForStream().add(StreamProducerService.STREAM_KEY, Map.of("init", "1"));
            }
            redisTemplate.opsForStream().createGroup(StreamProducerService.STREAM_KEY, ReadOffset.latest(), GROUP);
            if (bootstrapId != null) {
                redisTemplate.opsForStream().delete(StreamProducerService.STREAM_KEY, bootstrapId);
            }
            log.info("Created consumer group {} for {}", GROUP, StreamProducerService.STREAM_KEY);
        } catch (RedisSystemException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("BUSYGROUP")) {
                log.info("Consumer group already exists: {}", GROUP);
            } else {
                throw e;
            }
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void consume() {
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                Consumer.from(GROUP, CONSUMER),
                StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                StreamOffset.create(StreamProducerService.STREAM_KEY, ReadOffset.lastConsumed())
        );

        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<String> processedIds = new ArrayList<>();
        List<RecordId> toAck = new ArrayList<>();

        for (MapRecord<String, Object, Object> message : messages) {
            try {
                Object orderId = message.getValue().get(StreamProducerService.FIELD_ORDER_ID);
                Object time = message.getValue().get(StreamProducerService.FIELD_TIME);
                log.info("Consumed stream message id={}, orderId={}, time={}",
                        message.getId().getValue(), orderId, time);

                if (orderId != null) {
                    processedIds.add(orderId.toString());
                }
                toAck.add(message.getId());
            } catch (Exception ex) {
                log.error("Failed to process message {}", message.getId().getValue(), ex);
            }
        }

        if (!processedIds.isEmpty()) {
            recordProcessedOrders(processedIds);
        }
        if (!toAck.isEmpty()) {
            redisTemplate.opsForStream().acknowledge(
                    StreamProducerService.STREAM_KEY, GROUP,
                    toAck.toArray(RecordId[]::new));
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void checkPendingSummary() {
        PendingMessagesSummary summary = redisTemplate.opsForStream()
                .pending(StreamProducerService.STREAM_KEY, GROUP);

        if (summary != null && summary.getTotalPendingMessages() > 0) {
            log.info("Pending messages in group {}: {}", GROUP, summary.getTotalPendingMessages());
        }
    }

    public List<String> recentProcessedOrders(int n) {
        if (n <= 0) {
            return Collections.emptyList();
        }
        int capped = Math.min(n, 100);
        List<String> orders = redisTemplate.opsForList().range(PROCESSED_ORDERS_KEY, 0, capped - 1);
        return orders == null ? Collections.emptyList() : orders;
    }

    private void recordProcessedOrders(List<String> orderIds) {
        redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            byte[] key = PROCESSED_ORDERS_KEY.getBytes(StandardCharsets.UTF_8);
            for (String orderId : orderIds) {
                conn.listCommands().lPush(key, orderId.getBytes(StandardCharsets.UTF_8));
            }
            conn.listCommands().lTrim(key, 0, 99);
            return null;
        });
    }
}
