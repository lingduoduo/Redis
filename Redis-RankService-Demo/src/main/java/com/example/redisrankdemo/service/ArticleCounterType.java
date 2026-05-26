package com.example.redisrankdemo.service;

enum ArticleCounterType {
    VIEW("article:view:", "article:dirty:view"),
    LIKE("article:like:", "article:dirty:like"),
    PV("article:pv:", "article:dirty:pv");

    private final String keyPrefix;
    private final String dirtyKey;

    ArticleCounterType(String keyPrefix, String dirtyKey) {
        this.keyPrefix = keyPrefix;
        this.dirtyKey = dirtyKey;
    }

    String counterKey(Long articleId) {
        return keyPrefix + articleId;
    }

    String dirtyKey() {
        return dirtyKey;
    }
}
