package com.example.redisrankdemo.model;

import jakarta.validation.constraints.NotBlank;

public record ScoreRequest(
        @NotBlank String memberId,
        double score
) {
}
