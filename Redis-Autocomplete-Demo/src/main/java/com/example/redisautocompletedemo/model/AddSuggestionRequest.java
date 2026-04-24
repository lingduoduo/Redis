package com.example.redisautocompletedemo.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddSuggestionRequest(
        @NotBlank String term,
        @Min(0) double score,
        boolean incremental
) {
}
