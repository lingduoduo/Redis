package com.example.redishashdemo.model;

public record CartItem(
        String productId,
        long quantity
) {
}
