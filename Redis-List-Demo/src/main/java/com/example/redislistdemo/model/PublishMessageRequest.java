package com.example.redislistdemo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishMessageRequest(
        @NotBlank String authorId,
        @NotBlank @Size(max = 500) String content
) {
}
