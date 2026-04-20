package com.example.redislock.service;

import com.example.redislock.exception.ServiceBusyException;
import com.example.redislock.lock.LockWatchdog;
import com.example.redislock.lock.RedisLock;
import com.example.redislock.util.RedisKeyBuilder;
import com.example.redislock.util.StockValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StringRedisTemplate redisTemplate;
    private final RedisLock redisLock;
    private final LockWatchdog lockWatchdog;

    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 30;

    /**
     * Initializes the stock for a product.
     *
     * @param productId the product identifier
     * @param quantity the initial quantity
     */
    public void initStock(String productId, int quantity) {
        if (quantity < 0) {
            log.warn("Invalid quantity for product {}: {}", productId, quantity);
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
        redisTemplate.opsForValue().set(RedisKeyBuilder.stockKey(productId), String.valueOf(quantity));
        log.info("Stock initialized: product={}, quantity={}", productId, quantity);
    }

    /**
     * Retrieves the current stock level for a product.
     *
     * @param productId the product identifier
     * @return the stock quantity, or 0 if not found
     */
    public int getStock(String productId) {
        String val = redisTemplate.opsForValue().get(RedisKeyBuilder.stockKey(productId));
        if (val == null) {
            return 0;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            log.warn("Invalid stock format for product {}: {}", productId, val, e);
            return 0;
        }
    }

    /**
     * Attempt to purchase one unit of the given product.
     * Waits up to LOCK_WAIT_SECONDS for the lock before giving up.
     *
     * @param productId the product identifier
     * @return true if purchase was successful, false if out of stock
     * @throws ServiceBusyException if lock acquisition times out or the thread is interrupted
     */
    public boolean purchase(String productId) {
        if (productId == null || productId.isBlank()) {
            log.warn("Invalid productId: {}", productId);
            throw new IllegalArgumentException("ProductId cannot be null or empty");
        }

        String lockKey = RedisKeyBuilder.lockResource(productId);
        String token;
        ScheduledFuture<?> renewalTask = null;
        try {
            token = redisLock.tryLockWithToken(lockKey, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException("Purchase interrupted", e);
        }

        if (token == null) {
            log.warn("Failed to acquire lock for product {}", productId);
            throw new ServiceBusyException("Too many requests, please try again later");
        }

        try {
            renewalTask = lockWatchdog.startRenewal(
                    redisLock.redisKey(lockKey),
                    token,
                    LOCK_LEASE_SECONDS
            );
            return deductStock(productId);
        } finally {
            lockWatchdog.stopRenewal(renewalTask);
            boolean released = redisLock.unlock(lockKey, token);
            if (!released) {
                log.warn("Lock already expired before explicit release: product={}", productId);
            }
        }
    }

    /**
     * Atomically deducts one unit of stock from a product.
     * Reads current stock, validates it, and decrements it in a single operation.
     * This method is always called while holding the lock, ensuring consistency.
     *
     * @param productId the product identifier
     * @return true if deduction was successful, false if out of stock
     */
    private boolean deductStock(String productId) {
        String stockKey = RedisKeyBuilder.stockKey(productId);
        String rawStock = redisTemplate.opsForValue().get(stockKey);
        
        // If product doesn't exist or stock is null, treat as 0
        if (rawStock == null) {
            log.warn("Stock not found for product: {}", productId);
            return false;
        }

        try {
            int stock = Integer.parseInt(rawStock);
            
            // Check if stock is sufficient
            if (!StockValidator.isSufficientStock(stock)) {
                log.info("Out of stock: product={}", productId);
                return false;
            }

            // Deduct one unit and save back to Redis
            int newStock = stock - 1;
            redisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
            log.info("Purchased: product={}, remaining={}", productId, newStock);
            return true;
        } catch (NumberFormatException e) {
            log.error("Invalid stock format for product {}: {}", productId, rawStock, e);
            return false;
        }
    }
}
