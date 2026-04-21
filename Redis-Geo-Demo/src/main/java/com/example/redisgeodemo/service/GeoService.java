package com.example.redisgeodemo.service;

import com.example.redisgeodemo.model.NearbyResponse;
import com.example.redisgeodemo.model.NearbyShop;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeoService {

    private static final String KEY = "geo:shop";

    private final StringRedisTemplate redisTemplate;

    public GeoService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addShop(String shopId, double lng, double lat) {
        redisTemplate.opsForGeo().add(KEY, new Point(lng, lat), shopId);
    }

    public NearbyResponse nearby(double lng, double lat, double radiusKm, int limit) {
        Circle circle = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                redisTemplate.opsForGeo().radius(KEY, circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeCoordinates()
                                .includeDistance()
                                .sortAscending()
                                .limit(limit));

        List<NearbyShop> shops = results == null ? List.of() :
                results.getContent().stream()
                        .map(r -> {
                            RedisGeoCommands.GeoLocation<String> loc = r.getContent();
                            Point p = loc.getPoint();
                            Double distKm = r.getDistance() == null ? null : r.getDistance().getValue();
                            return new NearbyShop(loc.getName(), distKm, p.getX(), p.getY());
                        })
                        .toList();

        return new NearbyResponse(lng, lat, radiusKm, limit, shops);
    }
}
