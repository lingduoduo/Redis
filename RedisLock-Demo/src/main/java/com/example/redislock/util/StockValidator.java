package com.example.redislock.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for validating stock-related operations.
 * Centralizes validation logic to prevent code duplication.
 */
@Slf4j
public final class StockValidator {

    private StockValidator() {
    }

    /**
     * Parses a raw stock value and validates it.
     *
     * @param rawStock the raw stock value from Redis
     * @param productId the product identifier for logging
     * @return the parsed stock value
     * @throws IllegalStateException if stock is null
     */
    public static int parseAndValidate(String rawStock, Object productId) {
        if (rawStock == null) {
            log.warn("Product not found or stock not initialized: {}", productId);
            throw new IllegalStateException("Product stock was not initialized: " + productId);
        }
        try {
            return Integer.parseInt(rawStock);
        } catch (NumberFormatException e) {
            log.error("Invalid stock format for product {}: {}", productId, rawStock, e);
            throw new IllegalStateException("Invalid stock value: " + rawStock, e);
        }
    }

    /**
     * Checks if stock is sufficient for purchase.
     *
     * @param stock the current stock value
     * @return true if stock is positive, false otherwise
     */
    public static boolean isSufficientStock(int stock) {
        return stock > 0;
    }
}
