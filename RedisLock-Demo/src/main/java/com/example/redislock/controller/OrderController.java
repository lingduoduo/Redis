package com.example.redislock.controller;

import com.example.redislock.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for order management using Redisson distributed locks.
 * Demonstrates Redisson's high-level lock API for flash sale scenarios.
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Initializes stock for a product in the Redisson order system.
     *
     * @param productId the product identifier
     * @param quantity the initial quantity (default: 100)
     * @return initialization confirmation
     */
    @PostMapping("/orders/init/{productId}")
    public ResponseEntity<Map<String, Object>> initStock(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "100") int quantity) {
        orderService.initStock(productId, quantity);
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "stock", quantity,
                "message", "Stock initialized for Redisson order demo"
        ));
    }

    /**
     * Creates an order for a user to purchase a product.
     * Uses Redisson distributed lock to ensure thread-safe stock deduction.
     *
     * @param productId the product identifier
     * @param userId the user identifier
     * @return order creation confirmation
     */
    @PostMapping("/orders/{productId}")
    public ResponseEntity<Map<String, Object>> createOrder(
            @PathVariable Long productId,
            @RequestParam Long userId) {
        orderService.createOrder(userId, productId);
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "userId", userId,
                "stock", orderService.getStock(productId),
                "message", "Order created successfully"
        ));
    }

    /**
     * Creates a user-scoped order using the custom RedisLock.
     *
     * @param userId the user identifier
     * @return order creation confirmation
     */
    @PostMapping("/orders/users/{userId}")
    public ResponseEntity<Map<String, Object>> createUserOrder(@PathVariable Long userId) {
        String message = orderService.createUserOrder(userId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "message", message
        ));
    }

    /**
     * Minimal plain-text custom RedisLock demo endpoint.
     *
     * @param userId the user identifier
     * @return order creation message
     */
    @GetMapping("/order/{userId}")
    public String create(@PathVariable Long userId) {
        return orderService.createUserOrder(userId);
    }
}
