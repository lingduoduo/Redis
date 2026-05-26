package com.example.redismqdemo.model;

import jakarta.validation.constraints.NotBlank;

public record ListPushRequest(@NotBlank String message) {
}
