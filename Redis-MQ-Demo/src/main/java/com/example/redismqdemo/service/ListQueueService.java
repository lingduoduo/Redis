package com.example.redismqdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis List blocking queue / stack.
 *
 * Data model:
 *   key   = queue:{queueName}
 *   value = message string
 *
 * Redis commands exercised:
 *   RPUSH  – producer pushes to the tail (O(1))
 *   BLPOP  – FIFO consumer pops from the head, blocks until message arrives or timeout
 *   BRPOP  – LIFO consumer pops from the tail, blocks until message arrives or timeout
 *   LLEN   – current queue depth
 *
 * Queue (FIFO): RPUSH + BLPOP  – right in, left out (先进先出)
 * Stack (LIFO): RPUSH + BRPOP  – right in, right out (先进后出)
 *
 * Both BLPOP and BRPOP return null when the timeout expires with no message,
 * mirroring java.util.concurrent.BlockingQueue.poll(timeout, unit).
 */
@Service
public class ListQueueService {

    static final String KEY_PREFIX = "queue:";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;

    public ListQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** RPUSH — add a message to the tail of the queue. Returns new queue length. */
    public long push(String queueName, String message) {
        validateName(queueName);
        validateMessage(message);
        Long size = redisTemplate.opsForList().rightPush(queueKey(queueName), message);
        return size == null ? 0L : size;
    }

    /** BLPOP — FIFO pop from the head; blocks up to timeout. Returns null on timeout. */
    public String popFifo(String queueName, Duration timeout) {
        validateName(queueName);
        validateTimeout(timeout);
        return redisTemplate.opsForList().leftPop(queueKey(queueName), timeout);
    }

    /** BRPOP — LIFO pop from the tail; blocks up to timeout. Returns null on timeout. */
    public String popStack(String queueName, Duration timeout) {
        validateName(queueName);
        validateTimeout(timeout);
        return redisTemplate.opsForList().rightPop(queueKey(queueName), timeout);
    }

    /** LLEN — current number of messages in the queue. */
    public long size(String queueName) {
        validateName(queueName);
        Long size = redisTemplate.opsForList().size(queueKey(queueName));
        return size == null ? 0L : size;
    }

    public String queueKey(String queueName) {
        validateName(queueName);
        return KEY_PREFIX + queueName;
    }

    private void validateName(String queueName) {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("queueName cannot be blank");
        }
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }

    private void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
