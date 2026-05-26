package com.example.redisbitmapdemo.model;

import java.time.LocalDate;

public record ActivityStats(
        LocalDate startDate,
        LocalDate endDate,
        long allDaysOnlineUsers,
        long anyDayOnlineUsers
) {
}
