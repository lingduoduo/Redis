package com.example.redisgeodemo.service;

import com.example.redisgeodemo.model.NearbyResponse;
import com.example.redisgeodemo.model.NearbyShop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOps;

    private GeoService geoService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOps);
        geoService = new GeoService(redisTemplate);
    }

    @Test
    void addShopStoresCoordinatesAtSharedKey() {
        geoService.addShop("shop-1", -73.9857, 40.7484);

        verify(geoOps).add("geo:shop", new Point(-73.9857, 40.7484), "shop-1");
    }

    @Test
    void nearbyReturnsSortedResultsWithDistanceAndCoordinates() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                new GeoResult<>(
                        new RedisGeoCommands.GeoLocation<>("shop-1", new Point(-73.9857, 40.7484)),
                        new Distance(0.3, Metrics.KILOMETERS)
                )
        ), Metrics.KILOMETERS);
        when(geoOps.radius(eq("geo:shop"), eq(new Circle(new Point(-73.986, 40.748), new Distance(2.0, Metrics.KILOMETERS))), org.mockito.ArgumentMatchers.any()))
                .thenReturn(results);

        NearbyResponse response = geoService.nearby(-73.986, 40.748, 2.0, 5);

        assertThat(response.shops()).hasSize(1);
        NearbyShop shop = response.shops().get(0);
        assertThat(shop.shopId()).isEqualTo("shop-1");
        assertThat(shop.distanceKm()).isEqualTo(0.3);
        assertThat(shop.lng()).isEqualTo(-73.9857);
        assertThat(shop.lat()).isEqualTo(40.7484);
    }

    @Test
    void nearbyWrapsQueryMetadata() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = new GeoResults<>(List.of(
                new GeoResult<>(
                        new RedisGeoCommands.GeoLocation<>("shop-1", new Point(-73.9857, 40.7484)),
                        new Distance(0.3, Metrics.KILOMETERS)
                )
        ), Metrics.KILOMETERS);
        when(geoOps.radius(eq("geo:shop"), eq(new Circle(new Point(-73.986, 40.748), new Distance(2.0, Metrics.KILOMETERS))), org.mockito.ArgumentMatchers.any()))
                .thenReturn(results);

        NearbyResponse response = geoService.nearby(-73.986, 40.748, 2.0, 5);

        assertThat(response.queryLng()).isEqualTo(-73.986);
        assertThat(response.queryLat()).isEqualTo(40.748);
        assertThat(response.radiusKm()).isEqualTo(2.0);
        assertThat(response.limit()).isEqualTo(5);
        assertThat(response.shops()).hasSize(1);
    }
}
