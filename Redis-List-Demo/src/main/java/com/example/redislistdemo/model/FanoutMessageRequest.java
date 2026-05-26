package com.example.redislistdemo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FanoutMessageRequest(
        @NotBlank String authorId,
        @NotBlank @Size(max = 500) String content,
        @NotEmpty List<@NotBlank String> receiverUserIds
) {
}
