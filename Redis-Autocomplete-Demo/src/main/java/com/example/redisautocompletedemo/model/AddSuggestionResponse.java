package com.example.redisautocompletedemo.model;

public record AddSuggestionResponse(
        String message,
        String dictionaryKey,
        String term,
        double score,
        boolean incremental,
        long dictionarySize
) {
}
