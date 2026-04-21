package com.example.redisgeodemo.controller;

import com.example.redisgeodemo.model.AddShopRequest;
import com.example.redisgeodemo.model.AddShopResponse;
import com.example.redisgeodemo.model.NearbyResponse;
import com.example.redisgeodemo.service.GeoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/geo")
@Validated
public class GeoController {

    private final GeoService geoService;

    public GeoController(GeoService geoService) {
        this.geoService = geoService;
    }

    @PostMapping("/shops")
    public AddShopResponse addShop(@Valid @RequestBody AddShopRequest request) {
        geoService.addShop(request.shopId(), request.lng(), request.lat());
        return new AddShopResponse("shop added", "geo:shop", request.shopId(), request.lng(), request.lat());
    }

    @GetMapping("/nearby")
    public NearbyResponse nearby(
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam(defaultValue = "5") @DecimalMin("0.001") @DecimalMax("1000.0") double radiusKm,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return geoService.nearby(lng, lat, radiusKm, limit);
    }
}
