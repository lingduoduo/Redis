package com.example.redisgeodemo.model;

public record NearbyShop(
        String shopId,
        Double distanceKm,
        Double lng,
        Double lat
) {
}
