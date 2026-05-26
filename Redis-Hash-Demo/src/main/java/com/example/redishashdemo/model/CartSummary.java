package com.example.redishashdemo.model;

import java.util.List;

public record CartSummary(
        String userId,
        String redisKey,
        long productKinds,
        List<CartItem> items
) {
}
