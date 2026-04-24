package com.example.redisautocompletedemo.model;

public record SuggestionItem(
        String term,
        double score
) {
}
