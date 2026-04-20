package com.example.redislock;

import com.example.redislock.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for StockService concurrency behavior.
 * Validates that distributed locking prevents race conditions and overselling.
 * <p>
 * Run with: mvn test -Dredis.integration=true
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "redis.integration", matches = "true")
@Slf4j
class StockConcurrencyTest {

    @Autowired
    private StockService stockService;

    private static final String PRODUCT_ID = "test-phone";
    private static final int INITIAL_STOCK = 10;
    private static final int THREAD_COUNT = 50;

    @BeforeEach
    void setup() {
        stockService.initStock(PRODUCT_ID, INITIAL_STOCK);
    }

    /**
     * Tests that concurrent purchases never result in overselling.
     * With 50 concurrent threads and 10 items, should only sell 10 items maximum.
     */
    @Test
    void concurrentPurchases_shouldNeverOversell() throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            pool.submit(() -> {
                try {
                    if (stockService.purchase(PRODUCT_ID)) {
                        successCount.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    // Expected when lock acquisition fails due to contention
                    // In production, these would be retried or handled gracefully
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        int remainingStock = stockService.getStock(PRODUCT_ID);
        log.info("Successful purchases: {}, remaining stock: {}", successCount.get(), remainingStock);

        // Invariant 1: Never oversell (successful purchases <= initial stock)
        assertThat(successCount.get()).isLessThanOrEqualTo(INITIAL_STOCK);
        
        // Invariant 2: Stock conservation (successful purchases + remaining = initial)
        assertThat(successCount.get() + remainingStock).isEqualTo(INITIAL_STOCK);
    }

    /**
     * Tests that initialization with invalid quantity throws IllegalArgumentException.
     */
    @Test
    void initStock_withNegativeQuantity_shouldThrow() {
        assertThatThrownBy(() -> stockService.initStock("invalid-product", -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be non-negative");
    }

    /**
     * Tests that purchase on non-existent product fails gracefully.
     */
    @Test
    void purchase_onNonExistentProduct_shouldFail() {
        String nonExistentProduct = "non-existent-" + System.nanoTime();
        assertThatThrownBy(() -> stockService.purchase(nonExistentProduct))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * Tests that getStock returns 0 for uninitialized products.
     */
    @Test
    void getStock_onUninitializedProduct_returnsZero() {
        String uninitializedProduct = "uninitialized-" + System.nanoTime();
        int stock = stockService.getStock(uninitializedProduct);
        assertThat(stock).isZero();
    }

    /**
     * Tests sequential purchases work correctly without concurrency.
     */
    @Test
    void sequentialPurchases_shouldWorkCorrectly() {
        String productId = "sequential-" + System.nanoTime();
        int initialStock = 5;
        stockService.initStock(productId, initialStock);

        // Buy all 5 items sequentially
        for (int i = 0; i < initialStock; i++) {
            boolean success = stockService.purchase(productId);
            assertThat(success).isTrue();
            assertThat(stockService.getStock(productId)).isEqualTo(initialStock - i - 1);
        }

        // Try to buy one more (should fail)
        boolean failedPurchase = stockService.purchase(productId);
        assertThat(failedPurchase).isFalse();
    }
}
