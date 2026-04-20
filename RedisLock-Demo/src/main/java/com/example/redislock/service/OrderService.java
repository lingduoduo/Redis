package com.example.redislock.service;

import com.example.redislock.exception.ServiceBusyException;
import com.example.redislock.lock.RedisLock;
import com.example.redislock.util.RedisKeyBuilder;
import com.example.redislock.util.StockValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 30;
    private static final long USER_ORDER_LOCK_SECONDS = 10;

    private final RedissonClient redissonClient;
    private final RedisLock redisLock;
    private final StringRedisTemplate redisTemplate;

    /**
     * Initializes stock and clears orders for a product.
     *
     * @param productId the product identifier
     * @param quantity the initial stock quantity
     */
    public void initStock(Long productId, int quantity) {
        if (productId == null || productId <= 0) {
            log.warn("Invalid productId: {}", productId);
            throw new IllegalArgumentException("ProductId must be positive");
        }
        if (quantity < 0) {
            log.warn("Invalid quantity for product {}: {}", productId, quantity);
            throw new IllegalArgumentException("Quantity must be non-negative");
        }

        redisTemplate.opsForValue().set(RedisKeyBuilder.stockKey(productId), String.valueOf(quantity));
        redisTemplate.delete(RedisKeyBuilder.orderKey(productId));
        log.info("Redisson stock initialized: product={}, quantity={}", productId, quantity);
    }

    /**
     * Retrieves the current stock level for a product.
     *
     * @param productId the product identifier
     * @return the stock quantity, or 0 if not found
     */
    public int getStock(Long productId) {
        String rawStock = redisTemplate.opsForValue().get(RedisKeyBuilder.stockKey(productId));
        if (rawStock == null) {
            return 0;
        }
        try {
            return Integer.parseInt(rawStock);
        } catch (NumberFormatException e) {
            log.warn("Invalid stock format for product {}: {}", productId, rawStock, e);
            return 0;
        }
    }

    /**
     * Creates an order for a user to purchase a product.
     * Acquires a distributed lock using Redisson, deducts stock, and records the order.
     *
     * @param userId the user identifier
     * @param productId the product identifier
     * @throws ServiceBusyException if lock acquisition times out or the thread is interrupted
     * @throws IllegalStateException if stock not initialized or out of stock
     */
    public void createOrder(Long userId, Long productId) {
        validateUserId(userId);
        if (productId == null || productId <= 0) {
            log.warn("Invalid productId: {}", productId);
            throw new IllegalArgumentException("ProductId must be positive");
        }

        RLock lock = redissonClient.getLock(RedisKeyBuilder.stockLockKey(productId));
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Failed to acquire lock for product {}", productId);
                throw new ServiceBusyException("High demand, please try again later");
            }

            deductStock(productId);
            saveOrder(userId, productId);
            log.info("Order created: userId={}, productId={}", userId, productId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Order creation interrupted for userId={}, productId={}", userId, productId, e);
            throw new ServiceBusyException("Order creation interrupted", e);
        } finally {
            // Only release the lock if we own it
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Creates a user-scoped order using the custom RedisLock.
     * This guards against duplicate concurrent order submissions from the same user.
     *
     * @param userId the user identifier
     * @return a user-facing creation message
     * @throws ServiceBusyException if another request from the same user is already creating an order
     */
    public String createUserOrder(Long userId) {
        validateUserId(userId);

        String lockKey = RedisKeyBuilder.userOrderLockResource(userId);
        String token = redisLock.tryLockWithToken(lockKey, USER_ORDER_LOCK_SECONDS);
        if (token == null) {
            log.warn("Duplicate order request blocked for user {}", userId);
            throw new ServiceBusyException("Too many requests");
        }

        try {
            simulateOrderCreation();
            String message = "Order created for user " + userId;
            redisTemplate.opsForList().rightPush(RedisKeyBuilder.userOrderKey(userId), message + ":" + Instant.now());
            log.info("User order created with custom RedisLock: userId={}", userId);
            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException("Order creation interrupted", e);
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    /**
     * Atomically deducts one unit of stock from a product.
     * This method is always called while holding the lock, ensuring consistency.
     *
     * @param productId the product identifier
     * @throws IllegalStateException if stock not initialized or out of stock
     */
    private void deductStock(Long productId) {
        String stockKey = RedisKeyBuilder.stockKey(productId);
        String rawStock = redisTemplate.opsForValue().get(stockKey);

        // Parse and validate stock, throws if null or invalid
        int stock = StockValidator.parseAndValidate(rawStock, productId);

        // Check if stock is sufficient, throws if not
        if (!StockValidator.isSufficientStock(stock)) {
            throw new IllegalStateException("Product is out of stock: " + productId);
        }

        // Deduct one unit and save back
        int newStock = stock - 1;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
        log.info("Stock deducted with Redisson lock: product={}, remaining={}", productId, newStock);
    }

    /**
     * Records an order in Redis for audit/tracking purposes.
     *
     * @param userId the user identifier
     * @param productId the product identifier
     */
    private void saveOrder(Long userId, Long productId) {
        String orderRecord = userId + ":" + productId + ":" + Instant.now();
        redisTemplate.opsForList().rightPush(RedisKeyBuilder.orderKey(productId), orderRecord);
        log.debug("Order saved: {}", orderRecord);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            log.warn("Invalid userId: {}", userId);
            throw new IllegalArgumentException("UserId must be positive");
        }
    }

    private void simulateOrderCreation() throws InterruptedException {
        Thread.sleep(200);
    }
}
