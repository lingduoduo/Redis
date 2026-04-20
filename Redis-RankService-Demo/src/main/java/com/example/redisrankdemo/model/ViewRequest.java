package com.example.redisrankdemo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ViewRequest(
        @NotBlank String userId,
        @NotBlank String visitorId,
        @NotNull Long articleId
) {
}
