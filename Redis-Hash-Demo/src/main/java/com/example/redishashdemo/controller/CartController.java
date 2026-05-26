package com.example.redishashdemo.controller;

import com.example.redishashdemo.model.CartSummary;
import com.example.redishashdemo.service.CartHashService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartHashService cartHashService;

    public CartController(CartHashService cartHashService) {
        this.cartHashService = cartHashService;
    }

    @PostMapping("/{userId}/items/{productId}/increment")
    public Map<String, Object> addOne(@PathVariable String userId, @PathVariable String productId) {
        long quantity = cartHashService.addOne(userId, productId);
        return itemResponse(userId, productId, quantity);
    }

    @PostMapping("/{userId}/items/{productId}/decrement")
    public Map<String, Object> removeOne(@PathVariable String userId, @PathVariable String productId) {
        long quantity = cartHashService.removeOne(userId, productId);
        return itemResponse(userId, productId, quantity);
    }

    @PostMapping("/{userId}/items/{productId}/delta")
    public Map<String, Object> changeBy(
            @PathVariable String userId,
            @PathVariable String productId,
            @RequestParam long delta) {
        long quantity = cartHashService.increment(userId, productId, delta);
        return itemResponse(userId, productId, quantity);
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public Map<String, Object> deleteProduct(@PathVariable String userId, @PathVariable String productId) {
        cartHashService.deleteProduct(userId, productId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "product removed");
        result.put("userId", userId);
        result.put("productId", productId);
        return result;
    }

    @GetMapping("/{userId}")
    public CartSummary cart(@PathVariable String userId) {
        return cartHashService.summary(userId);
    }

    @GetMapping("/{userId}/size")
    public Map<String, Object> productKinds(@PathVariable String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("productKinds", cartHashService.productKinds(userId));
        return result;
    }

    private Map<String, Object> itemResponse(String userId, String productId, long quantity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("productId", productId);
        result.put("quantity", quantity);
        return result;
    }
}
