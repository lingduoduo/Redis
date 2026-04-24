package com.example.redisautocompletedemo.model;

public record ImportResponse(
        String message,
        String dictionaryKey,
        String resource,
        int inserted,
        int skipped,
        boolean incremental,
        boolean clearedBeforeImport,
        long dictionarySize
) {
}
