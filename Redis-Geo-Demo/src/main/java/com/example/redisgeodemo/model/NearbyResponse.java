package com.example.redisgeodemo.model;

import java.util.List;

public record NearbyResponse(
        double queryLng,
        double queryLat,
        double radiusKm,
        int limit,
        List<NearbyShop> shops
) {
}
