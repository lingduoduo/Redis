package com.example.redismqdemo.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DelayOrderRequest(
        @NotBlank String orderId,
        @Min(1) long delayMs
) {
}
