package com.example.redisautocompletedemo.model;

public record DictionaryInfoResponse(
        String message,
        String dictionaryKey,
        long size
) {
}
