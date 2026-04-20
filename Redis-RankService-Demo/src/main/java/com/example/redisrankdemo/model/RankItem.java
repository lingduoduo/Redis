package com.example.redisrankdemo.model;

public record RankItem(
        int rank,
        String memberId,
        Double score
) {
}
