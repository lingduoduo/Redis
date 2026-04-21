package com.example.redismqdemo.model;

import jakarta.validation.constraints.NotBlank;

public record OrderMessageRequest(@NotBlank String orderId) {
}
