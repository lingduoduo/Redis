package com.example.redismqdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StreamProducerService {

    private static final Logger log = LoggerFactory.getLogger(StreamProducerService.class);

    public static final String STREAM_KEY = "stream:order";
    public static final String FIELD_ORDER_ID = "orderId";
    public static final String FIELD_TIME = "time";
    public static final int STREAM_MAX_LEN = 1000;

    private final StringRedisTemplate redisTemplate;

    public StreamProducerService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String sendOrder(String orderId) {
        RecordId recordId = redisTemplate.opsForStream().add(STREAM_KEY, orderMessage(orderId));
        if (recordId == null) {
            log.warn("Stream add returned null for orderId={}", orderId);
            return null;
        }
        redisTemplate.opsForStream().trim(STREAM_KEY, STREAM_MAX_LEN, true);
        return recordId.getValue();
    }

    public long streamSize() {
        Long size = redisTemplate.opsForStream().size(STREAM_KEY);
        return size == null ? 0 : size;
    }

    Map<String, String> orderMessage(String orderId) {
        return Map.of(
                FIELD_ORDER_ID, orderId,
                FIELD_TIME, String.valueOf(System.currentTimeMillis())
        );
    }
}
