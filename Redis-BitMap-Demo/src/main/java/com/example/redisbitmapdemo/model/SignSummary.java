package com.example.redisbitmapdemo.model;

public record SignSummary(
        Long userId,
        String month,
        boolean signedToday,
        long signedDaysOfMonth,
        long currentStreak
) {
}
