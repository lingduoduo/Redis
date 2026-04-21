package com.example.redisgeodemo.model;

public record AddShopResponse(
        String message,
        String key,
        String shopId,
        double lng,
        double lat
) {}
