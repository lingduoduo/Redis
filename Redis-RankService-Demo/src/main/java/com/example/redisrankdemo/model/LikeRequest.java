package com.example.redisrankdemo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LikeRequest(
        @NotBlank String userId,
        @NotNull Long articleId
) {
}
