package com.example.redisgeodemo.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record AddShopRequest(
        @NotBlank String shopId,
        @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
        @DecimalMin("-90.0") @DecimalMax("90.0") double lat
) {
}
