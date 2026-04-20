package com.example.redislock.controller;

import com.example.redislock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for flash sale stock management using custom Redis locks.
 * Demonstrates distributed lock-based synchronization for high-concurrency scenarios.
 */
@RestController
@RequestMapping("/flash")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * Initializes stock for a product.
     *
     * @param productId the product identifier
     * @param quantity the initial quantity (default: 100)
     * @return initialization confirmation
     */
    @PostMapping("/init/{productId}")
    public ResponseEntity<Map<String, Object>> init(
            @PathVariable String productId,
            @RequestParam(defaultValue = "100") int quantity) {
        stockService.initStock(productId, quantity);
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "stock", quantity,
                "message", "Stock initialized successfully"
        ));
    }

    /**
     * Gets the current stock level for a product.
     *
     * @param productId the product identifier
     * @return current stock quantity
     */
    @GetMapping("/stock/{productId}")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable String productId) {
        int stock = stockService.getStock(productId);
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "stock", stock,
                "message", stock > 0 ? "In stock" : "Out of stock"
        ));
    }

    /**
     * Attempts to purchase one unit of a product.
     *
     * @param productId the product identifier
     * @return purchase success/failure status
     */
    @PostMapping("/buy/{productId}")
    public ResponseEntity<Map<String, Object>> buy(@PathVariable String productId) {
        boolean ok = stockService.purchase(productId);
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "success", ok,
                "message", ok ? "Purchase successful" : "Failed - out of stock"
        ));
    }
}
