package com.example.redismqdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class DelayQueueService {

    private static final Logger log = LoggerFactory.getLogger(DelayQueueService.class);

    public static final String QUEUE_KEY = "delay:order:close";
    public static final String CLOSED_ORDERS_KEY = "delay:order:closed";
    public static final Duration DEFAULT_ORDER_CLOSE_DELAY = Duration.ofMinutes(30);
    private static final int SCAN_BATCH_SIZE = 50;
    private static final int CLOSED_ORDERS_HISTORY_SIZE = 100;

    private final StringRedisTemplate redisTemplate;

    public DelayQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void push(String orderId) {
        push(orderId, DEFAULT_ORDER_CLOSE_DELAY.toMillis());
    }

    public void push(String orderId, long delayMs) {
        long triggerTime = nowMillis() + delayMs;
        redisTemplate.opsForZSet().add(QUEUE_KEY, orderId, triggerTime);
    }

    @Scheduled(fixedDelay = 1000)
    public void scan() {
        Set<String> dueItems = findDueOrders();

        if (dueItems == null || dueItems.isEmpty()) {
            return;
        }

        for (String orderId : dueItems) {
            if (claimDueOrder(orderId)) {
                recordClosedOrder(orderId);
                log.info("Closed expired order: {}", orderId);
            }
        }
    }

    public Set<String> peekTop(int n) {
        if (n <= 0) {
            return Collections.emptySet();
        }
        int capped = Math.min(n, 100);
        return redisTemplate.opsForZSet().range(QUEUE_KEY, 0, capped - 1);
    }

    public List<String> recentClosedOrders(int n) {
        if (n <= 0) {
            return Collections.emptyList();
        }
        int capped = Math.min(n, 100);
        List<String> orders = redisTemplate.opsForList().range(CLOSED_ORDERS_KEY, 0, capped - 1);
        return orders == null ? Collections.emptyList() : orders;
    }

    private void recordClosedOrder(String orderId) {
        redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            byte[] key = CLOSED_ORDERS_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] value = orderId.getBytes(StandardCharsets.UTF_8);
            conn.listCommands().lPush(key, value);
            conn.listCommands().lTrim(key, 0, CLOSED_ORDERS_HISTORY_SIZE - 1);
            return null;
        });
    }

    private Set<String> findDueOrders() {
        return redisTemplate.opsForZSet()
                .rangeByScore(QUEUE_KEY, 0, nowMillis(), 0, SCAN_BATCH_SIZE);
    }

    private boolean claimDueOrder(String orderId) {
        Long removed = redisTemplate.opsForZSet().remove(QUEUE_KEY, orderId);
        return removed != null && removed > 0;
    }

    long nowMillis() {
        return System.currentTimeMillis();
    }
}
