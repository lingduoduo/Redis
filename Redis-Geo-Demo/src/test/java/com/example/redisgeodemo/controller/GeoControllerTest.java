package com.example.redisgeodemo.controller;

import com.example.redisgeodemo.model.AddShopRequest;
import com.example.redisgeodemo.model.AddShopResponse;
import com.example.redisgeodemo.model.NearbyResponse;
import com.example.redisgeodemo.service.GeoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoControllerTest {

    @Mock
    private GeoService geoService;

    @InjectMocks
    private GeoController geoController;

    @Test
    void addShopStoresCoordinatesAndReturnsMetadata() {
        AddShopRequest request = new AddShopRequest("shop-1", -73.9857, 40.7484);

        AddShopResponse result = geoController.addShop(request);

        verify(geoService).addShop("shop-1", -73.9857, 40.7484);
        assertThat(result).isEqualTo(new AddShopResponse("shop added", "geo:shop", "shop-1", -73.9857, 40.7484));
    }

    @Test
    void nearbyDelegatesToGeoService() {
        NearbyResponse response = new NearbyResponse(-73.9857, 40.7484, 2.0, 5, List.of());
        when(geoService.nearby(-73.9857, 40.7484, 2.0, 5)).thenReturn(response);

        assertThat(geoController.nearby(-73.9857, 40.7484, 2.0, 5)).isEqualTo(response);
    }
}
