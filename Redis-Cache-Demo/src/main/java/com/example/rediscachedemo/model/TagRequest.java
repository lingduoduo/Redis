package com.example.rediscachedemo.model;

import jakarta.validation.constraints.NotBlank;

public record TagRequest(@NotBlank String tag) {
}
